# Toolchain resolution, doctor, setup. POSIX sh. Sourced.

resolve_java_home() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
        printf '%s' "$JAVA_HOME"
        return 0
    fi
    for v in 17 21 11; do
        for d in /usr/lib/jvm/java-$v-openjdk-* /usr/lib/jvm/java-$v-* /usr/lib/jvm/openjdk-$v*; do
            [ -x "$d/bin/javac" ] && { printf '%s' "$d"; return 0; }
        done
    done
    if have_cmd javac; then
        jp=$(command -v javac)
        jp=$(readlink -f "$jp" 2>/dev/null || printf '%s' "$jp")
        case "$jp" in
            */bin/javac) printf '%s' "${jp%/bin/javac}"; return 0 ;;
        esac
    fi
    return 1
}

best_build_tools_dir() {
    sdk="$1"
    best=""
    best_ver=""
    if [ ! -d "$sdk/build-tools" ]; then
        return 1
    fi
    for d in "$sdk"/build-tools/*/; do
        [ -d "$d" ] || continue
        ver=$(basename "$d")
        if [ -z "$best_ver" ]; then
            best="$d"
            best_ver="$ver"
            continue
        fi
        # string compare is good enough for 33.0.1 vs 35.0.0
        awk -v a="$ver" -v b="$best_ver" 'BEGIN { exit !(a > b) }' && {
            best="$d"
            best_ver="$ver"
        }
    done
    [ -n "$best" ] || return 1
    printf '%s' "${best%/}"
}

find_tool_in() {
    name="$1"
    shift
    for d in "$@"; do
        [ -n "$d" ] || continue
        if [ -x "$d/$name" ]; then
            printf '%s' "$d/$name"
            return 0
        fi
        if [ -x "$d/$name.jar" ]; then
            printf '%s' "$d/$name.jar"
            return 0
        fi
    done
    if have_cmd "$name"; then
        command -v "$name"
        return 0
    fi
    return 1
}

# d8 often ships as a script next to lib/d8.jar or as d8.jar itself.
resolve_d8() {
    for candidate in "$@"; do
        [ -n "$candidate" ] || continue
        if [ -x "$candidate/d8" ]; then
            printf '%s' "$candidate/d8"
            return 0
        fi
        if [ -f "$candidate/lib/d8.jar" ]; then
            printf '%s' "$candidate/lib/d8.jar"
            return 0
        fi
        if [ -f "$candidate/d8.jar" ]; then
            printf '%s' "$candidate/d8.jar"
            return 0
        fi
    done
    if have_cmd d8; then
        command -v d8
        return 0
    fi
    return 1
}

resolve_android_jar() {
    compile_sdk="${1:-34}"
    if [ -n "${ANDROID_HOME:-}" ] && [ -f "$ANDROID_HOME/platforms/android-$compile_sdk/android.jar" ]; then
        printf '%s' "$ANDROID_HOME/platforms/android-$compile_sdk/android.jar"
        return 0
    fi
    if [ -f "$AICORE_HOME/sdk/platforms/android-$compile_sdk/android.jar" ]; then
        printf '%s' "$AICORE_HOME/sdk/platforms/android-$compile_sdk/android.jar"
        return 0
    fi
    # any android.jar we can find
    for f in \
        "$AICORE_HOME/sdk/android.jar" \
        ${ANDROID_HOME:-}/platforms/android-*/android.jar \
        "$AICORE_HOME"/sdk/platforms/android-*/android.jar
    do
        [ -f "$f" ] && { printf '%s' "$f"; return 0; }
    done
    return 1
}

resolve_toolchain() {
    compile_sdk="${1:-34}"
    ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME:-/root}/android/sdk}}"
    export ANDROID_HOME

    JAVA_HOME=$(resolve_java_home || true)
    if [ -n "$JAVA_HOME" ]; then
        TOOL_JAVAC="$JAVA_HOME/bin/javac"
        export JAVA_HOME
    else
        TOOL_JAVAC=$(command -v javac 2>/dev/null || true)
    fi

    bt=$(best_build_tools_dir "$ANDROID_HOME" 2>/dev/null || true)
    mini_bt="$AICORE_HOME/sdk/build-tools"
    [ -d "$mini_bt" ] || mini_bt=""

    TOOL_AAPT2=$(find_tool_in aapt2 "$bt" "$mini_bt" "$AICORE_HOME/sdk/build-tools" || true)
    TOOL_ZIPALIGN=$(find_tool_in zipalign "$bt" "$mini_bt" "$AICORE_HOME/sdk/build-tools" || true)
    TOOL_APKSIGNER=$(find_tool_in apksigner "$bt" "$mini_bt" "$AICORE_HOME/sdk/build-tools" || true)
    TOOL_D8=$(resolve_d8 "$bt" "$mini_bt" "$AICORE_HOME/sdk/build-tools" || true)
    TOOL_ANDROID_JAR=$(resolve_android_jar "$compile_sdk" || true)
    TOOL_KEYSTORE="$AICORE_HOME/debug.keystore"
    TOOL_KS_TYPE="JKS"
    [ -f "$TOOL_KEYSTORE" ] || TOOL_KEYSTORE=""
}

tool_version() {
    bin="$1"
    [ -n "$bin" ] && [ -e "$bin" ] || { printf 'missing'; return 0; }
    case "$bin" in
        *javac)
            "$bin" -version 2>&1 | awk '{print $NF; exit}'
            ;;
        *aapt2)
            "$bin" version 2>&1 | awk '{print $NF; exit}'
            ;;
        *)
            printf 'ok'
            ;;
    esac
}

AIC_DOCTOR_MISSING=0

doctor_row() {
    name="$1"
    path="$2"
    if [ -n "$path" ] && [ -e "$path" ]; then
        ver=$(tool_version "$path")
        if [ "$AIC_JSON" = "1" ]; then
            printf '{"event":"doctor_ok","name":"%s","path":"%s","version":"%s"}\n' "$name" "$(json_escape "$path")" "$(json_escape "$ver")"
        else
            printf '%-12s %s%s%s  %s\n' "$name" "$C_GREEN" "$path" "$C_RESET" "$ver"
        fi
    else
        AIC_DOCTOR_MISSING=1
        if [ "$AIC_JSON" = "1" ]; then
            printf '{"event":"doctor_missing","name":"%s"}\n' "$name"
        else
            printf '%-12s %sMISSING%s\n' "$name" "$C_RED" "$C_RESET"
        fi
    fi
}

print_doctor() {
    compile_sdk="${1:-34}"
    resolve_toolchain "$compile_sdk"
    AIC_DOCTOR_MISSING=0
    if [ "$AIC_JSON" != "1" ]; then
        printf '%sAICore doctor%s  v%s\n' "$C_BOLD" "$C_RESET" "$AICORE_VERSION"
        printf 'AICORE_HOME  %s\n' "$AICORE_HOME"
        printf 'ANDROID_HOME %s\n\n' "${ANDROID_HOME:-unset}"
    fi
    doctor_row javac "$TOOL_JAVAC"
    doctor_row aapt2 "$TOOL_AAPT2"
    doctor_row d8 "$TOOL_D8"
    doctor_row zipalign "$TOOL_ZIPALIGN"
    doctor_row apksigner "$TOOL_APKSIGNER"
    doctor_row android.jar "$TOOL_ANDROID_JAR"
    doctor_row keystore "$TOOL_KEYSTORE"
    if [ "$AIC_DOCTOR_MISSING" -ne 0 ]; then
        if [ "$AIC_JSON" != "1" ]; then
            printf '\n%sMissing tools.%s Install JDK 17 and run:\n' "$C_YELLOW" "$C_RESET" >&2
            printf '  aic setup android\n' >&2
            printf '  # or the full SDK via the existing env installer: aicode\n' >&2
        fi
        return 2
    fi
    return 0
}

require_toolchain() {
    compile_sdk="${1:-34}"
    resolve_toolchain "$compile_sdk"
    [ -n "$TOOL_JAVAC" ] && [ -x "$TOOL_JAVAC" ] || die AIC_TOOLCHAIN_JAVAC "javac not found (need JDK 17). Run: aic setup android"
    [ -n "$TOOL_AAPT2" ] && [ -x "$TOOL_AAPT2" ] || die AIC_TOOLCHAIN_AAPT2 "aapt2 not found. Run: aic setup android"
    [ -n "$TOOL_D8" ] || die AIC_TOOLCHAIN_D8 "d8 not found. Run: aic setup android"
    [ -n "$TOOL_ZIPALIGN" ] && [ -x "$TOOL_ZIPALIGN" ] || die AIC_TOOLCHAIN_ZIPALIGN "zipalign not found. Run: aic setup android"
    [ -n "$TOOL_APKSIGNER" ] || die AIC_TOOLCHAIN_APKSIGNER "apksigner not found. Run: aic setup android"
    [ -n "$TOOL_ANDROID_JAR" ] && [ -f "$TOOL_ANDROID_JAR" ] || die AIC_TOOLCHAIN_ANDROID_JAR "android.jar not found for compileSdk $compile_sdk. Run: aic setup android"
}

run_d8() {
    min_api="$1"
    android_jar="$2"
    out_dir="$3"
    shift 3
    java_bin="java"
    [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && java_bin="$JAVA_HOME/bin/java"
    case "$TOOL_D8" in
        *.jar)
            "$java_bin" -cp "$TOOL_D8" com.android.tools.r8.D8 --min-api "$min_api" --lib "$android_jar" --output "$out_dir" "$@"
            return $?
            ;;
        *)
            "$TOOL_D8" --min-api "$min_api" --lib "$android_jar" --output "$out_dir" "$@"
            return $?
            ;;
    esac
}

run_apksigner() {
    aligned="$1"
    out="$2"
    ks="$3"
    ks_type="${4:-JKS}"
    extra=""
    [ "$ks_type" = "PKCS12" ] && extra="--ks-type PKCS12"
    case "$TOOL_APKSIGNER" in
        *.jar)
            java_bin="java"
            [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && java_bin="$JAVA_HOME/bin/java"
            # shellcheck disable=SC2086
            "$java_bin" -jar "$TOOL_APKSIGNER" sign $extra \
                --ks "$ks" --ks-pass pass:android --key-pass pass:android \
                --ks-key-alias androiddebugkey --out "$out" "$aligned"
            ;;
        *)
            # shellcheck disable=SC2086
            "$TOOL_APKSIGNER" sign $extra \
                --ks "$ks" --ks-pass pass:android --key-pass pass:android \
                --ks-key-alias androiddebugkey --out "$out" "$aligned"
            ;;
    esac
}

verify_apk() {
    apk="$1"
    case "$TOOL_APKSIGNER" in
        *.jar)
            java_bin="java"
            [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && java_bin="$JAVA_HOME/bin/java"
            "$java_bin" -jar "$TOOL_APKSIGNER" verify --verbose "$apk"
            ;;
        *)
            "$TOOL_APKSIGNER" verify --verbose "$apk"
            ;;
    esac
}

ensure_debug_keystore() {
    mkdir_p "$AICORE_HOME"
    ks="$AICORE_HOME/debug.keystore"
    if [ -f "$ks" ]; then
        TOOL_KEYSTORE="$ks"
        if command -v file >/dev/null 2>&1; then
            :
        fi
        # Prefer PKCS12 if the file was generated with openssl.
        if have_cmd openssl && openssl pkcs12 -in "$ks" -passin pass:android -nokeys >/dev/null 2>&1; then
            TOOL_KS_TYPE="PKCS12"
        else
            TOOL_KS_TYPE="JKS"
        fi
        return 0
    fi
    if have_cmd keytool; then
        keytool -genkeypair -keystore "$ks" -alias androiddebugkey \
            -keyalg RSA -keysize 2048 -validity 10000 \
            -storepass android -keypass android \
            -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1 || \
            die AIC_SIGN_KEYSTORE "keytool failed to create debug.keystore"
        TOOL_KEYSTORE="$ks"
        TOOL_KS_TYPE="JKS"
        return 0
    fi
    if have_cmd openssl; then
        tmp="$AICORE_HOME/.ks-tmp"
        mkdir_p "$tmp"
        openssl req -newkey rsa:2048 -nodes -keyout "$tmp/key.pem" -x509 -days 10000 \
            -out "$tmp/cert.pem" -subj "/CN=Android Debug/O=Android/C=US" >/dev/null 2>&1 || \
            die AIC_SIGN_KEYSTORE "openssl failed to create debug cert"
        openssl pkcs12 -export -in "$tmp/cert.pem" -inkey "$tmp/key.pem" \
            -out "$ks" -name androiddebugkey -passout pass:android >/dev/null 2>&1 || \
            die AIC_SIGN_KEYSTORE "openssl pkcs12 export failed"
        TOOL_KEYSTORE="$ks"
        TOOL_KS_TYPE="PKCS12"
        return 0
    fi
    die AIC_SIGN_KEYSTORE "neither keytool nor openssl is available to create debug.keystore"
}

host_arch() {
    m=$(uname -m 2>/dev/null || echo unknown)
    case "$m" in
        aarch64|arm64) printf 'linux-arm64' ;;
        x86_64|amd64) printf 'linux-x64' ;;
        *) printf 'linux-%s' "$m" ;;
    esac
}

cmd_setup() {
    target="${1:-android}"
    profile="${AIC_SETUP_PROFILE:-minimal}"
    arch=$(host_arch)
    mkdir_p "$AICORE_HOME"
    case "$target" in
        android|compiler|toolchain) ;;
        *) die AIC_VALIDATE_SETUP "unknown setup target '$target' (try: android)" ;;
    esac
    url="$AICORE_REGISTRY_URL/manifest?channel=stable&arch=$arch&profile=$profile"
    log "Fetching toolchain manifest..."
    log_dim "  $url"
    manifest="$AICORE_HOME/manifest.json"
    if ! have_cmd curl; then
        die AIC_TOOLCHAIN_CURL "curl is required for aic setup"
    fi
    if ! curl -fsSL "$url" -o "$manifest"; then
        die AIC_TOOLCHAIN_MANIFEST "failed to fetch $url"
    fi
    # Download each component that has a non-empty url + sha256.
    python_ok=0
    have_cmd python3 && python_ok=1
    if [ "$python_ok" -eq 1 ]; then
        python3 - "$manifest" "$AICORE_HOME" "$arch" <<'PY'
import json, os, subprocess, sys, hashlib, urllib.request, shutil

manifest_path, home, arch = sys.argv[1], sys.argv[2], sys.argv[3]
with open(manifest_path, encoding="utf-8") as f:
    data = json.load(f)

# Some hosts (e.g. Cloudflare r2.dev) reject the default Python-urllib UA.
user_agent = "AICore/%s" % (data.get("compiler", {}).get("version", "0.1.0"))

components = data.get("components") or []
if isinstance(components, dict):
    components = [{"id": k, **v} for k, v in components.items()]

ok = 0
skipped = 0
failed = 0
for c in components:
    cid = c.get("id") or c.get("name") or "component"
    url = c.get("url") or ""
    sha = (c.get("sha256") or "").lower()
    dest_rel = c.get("installPath") or c.get("path") or ""
    required = c.get("required", False)
    if not url:
        print(f"skip {cid}: not uploaded yet", file=sys.stderr)
        skipped += 1
        continue
    filename = os.path.basename(url.split("?")[0]) or (cid + ".bin")
    dl_dir = os.path.join(home, "downloads")
    os.makedirs(dl_dir, exist_ok=True)
    dl = os.path.join(dl_dir, filename)
    print(f"download {cid}: {url}", file=sys.stderr)
    downloaded = False
    if shutil.which("curl"):
        downloaded = subprocess.call(["curl", "-fSL", "--retry", "3", "-A", user_agent, "-o", dl, url]) == 0
    if not downloaded:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": user_agent})
            with urllib.request.urlopen(req, timeout=600) as resp, open(dl, "wb") as out:
                shutil.copyfileobj(resp, out)
            downloaded = True
        except Exception as e:
            print(f"fail {cid}: {e}", file=sys.stderr)
    if not downloaded:
        failed += 1
        if required:
            sys.exit(2)
        continue
    if sha:
        h = hashlib.sha256()
        with open(dl, "rb") as fh:
            for chunk in iter(lambda: fh.read(1024 * 1024), b""):
                h.update(chunk)
        got = h.hexdigest()
        if got != sha:
            print(f"fail {cid}: sha256 mismatch (got {got})", file=sys.stderr)
            failed += 1
            if required:
                sys.exit(2)
            continue
    # Extract archives into AICORE_HOME/sdk or the given installPath.
    install_root = os.path.join(home, dest_rel) if dest_rel else os.path.join(home, "sdk")
    os.makedirs(install_root, exist_ok=True)
    lower = filename.lower()
    try:
        if lower.endswith(".zip"):
            subprocess.check_call(["unzip", "-qo", dl, "-d", install_root])
        elif lower.endswith(".tar.zst") or lower.endswith(".tzst"):
            subprocess.check_call(["tar", "-C", install_root, "--zstd", "-xf", dl])
        elif lower.endswith(".tar.gz") or lower.endswith(".tgz"):
            subprocess.check_call(["tar", "-C", install_root, "-xzf", dl])
        elif lower.endswith(".tar"):
            subprocess.check_call(["tar", "-C", install_root, "-xf", dl])
        else:
            # raw binary (e.g. aic itself)
            target = os.path.join(home, "bin", filename) if "compiler" in cid else os.path.join(install_root, filename)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            os.replace(dl, target)
            os.chmod(target, 0o755)
        ok += 1
    except Exception as e:
        print(f"fail {cid}: extract {e}", file=sys.stderr)
        failed += 1
        if required:
            sys.exit(2)

print(f"setup: installed={ok} skipped={skipped} failed={failed}", file=sys.stderr)
if failed and ok == 0:
    sys.exit(2)
PY
    else
        log "python3 not found; cannot parse manifest. Install python3 or the Android scenario via \`aicode\`."
        die AIC_TOOLCHAIN_PYTHON "python3 required for aic setup"
    fi
    ensure_debug_keystore
    log "Toolchain setup finished. Run: aic doctor"
}

cmd_sdk_list() {
    resolve_toolchain 34
    if [ "$AIC_JSON" = "1" ]; then
        aic_emit info path "${ANDROID_HOME:-}"
    else
        printf 'ANDROID_HOME %s\n' "${ANDROID_HOME:-unset}"
        printf 'AICORE_HOME  %s\n' "$AICORE_HOME"
        printf '\nplatforms:\n'
        for d in ${ANDROID_HOME:-}/platforms/android-* "$AICORE_HOME"/sdk/platforms/android-*; do
            [ -d "$d" ] && printf '  %s\n' "$d"
        done
        printf '\nbuild-tools:\n'
        for d in ${ANDROID_HOME:-}/build-tools/* "$AICORE_HOME"/sdk/build-tools; do
            [ -d "$d" ] && printf '  %s\n' "$d"
        done
    fi
}
