# Build stages 0-8. POSIX sh. Sourced.

stage_banner() {
    index="$1"
    total="$2"
    name="$3"
    if [ "$AIC_JSON" = "1" ]; then
        aic_emit stage_start stage "$name" index "$index" total "$total"
    else
        printf '[%s/%s] %-18s ' "$index" "$total" "$name" >&2
    fi
}

stage_ok() {
    name="$1"
    ms="$2"
    if [ "$AIC_JSON" = "1" ]; then
        aic_emit stage_ok stage "$name" ms "$ms"
    else
        printf '%sok%s  %sms\n' "$C_GREEN" "$C_RESET" "$ms" >&2
    fi
}

stage_fail() {
    name="$1"
    code="$2"
    msg="$3"
    if [ "$AIC_JSON" != "1" ]; then
        printf '%sFAIL%s\n' "$C_RED" "$C_RESET" >&2
    fi
    aic_emit_error "$name" "" "" "" "$msg" "$code"
    case "$code" in
        AIC_TOOLCHAIN_*) exit 2 ;;
        *) exit 1 ;;
    esac
}

parse_javac_errors() {
    # stdin: javac output. Emit structured errors; return 1 if any error: line.
    had=0
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            *": error:"*)
                had=1
                file=$(printf '%s' "$line" | awk -F: '{print $1}')
                ln=$(printf '%s' "$line" | awk -F: '{print $2}')
                col=$(printf '%s' "$line" | awk -F: '{print $3}')
                msg=$(printf '%s' "$line" | sed 's/.*: error:[[:space:]]*//')
                case "$ln" in
                    ''|*[!0-9]*) ln="" ;;
                esac
                case "$col" in
                    ''|*[!0-9]*) col="" ;;
                esac
                aic_emit_error javac "$file" "$ln" "$col" "$msg" AIC_JAVAC_001
                ;;
            *": warning:"*)
                :
                ;;
            *)
                [ "$AIC_VERBOSE" = "1" ] && [ "$AIC_JSON" != "1" ] && printf '%s\n' "$line" >&2
                ;;
        esac
    done
    [ "$had" -eq 0 ]
}

stage_validate() {
    stage_banner 1 8 validate
    t0=$(aic_now_ms)

    case "$PROJECT_FORMAT" in
        ''|*[!0-9]*) die AIC_VALIDATE_FORMAT "aicode.json format is not a number" ;;
    esac
    if [ "$PROJECT_FORMAT" -gt 1 ]; then
        die AIC_VALIDATE_FORMAT "aicode.json format $PROJECT_FORMAT is newer than this engine (supports 1)"
    fi
    [ -n "$PROJECT_PACKAGE" ] || die AIC_VALIDATE_PACKAGE "missing package in aicode.json"
    valid_java_package "$PROJECT_PACKAGE" || die AIC_VALIDATE_PACKAGE "invalid Java package '$PROJECT_PACKAGE'"
    [ -f "$SRC_MANIFEST" ] || die AIC_VALIDATE_MANIFEST "missing $SRC_MANIFEST"
    [ -d "$SRC_JAVA" ] || die AIC_VALIDATE_JAVA "missing $SRC_JAVA"
    njava=$(count_java_files "$SRC_JAVA")
    [ "$njava" -gt 0 ] || die AIC_VALIDATE_JAVA "no .java files under app/src/main/java"
    nkt=$(count_kt_files "$PROJECT_DIR")
    if [ "$nkt" -gt 0 ] || [ "$PROJECT_LANGUAGE" = "kotlin" ]; then
        die AIC_KOTLIN_UNSUPPORTED "Kotlin is not supported in AICore v1; use engine gradle remotely"
    fi
    case "$PROJECT_ENGINE" in
        aicore|"") ;;
        gradle) die AIC_ENGINE_GRADLE "engine=gradle is reserved for Phase 6" ;;
        *) die AIC_VALIDATE_ENGINE "unknown compiler.engine '$PROJECT_ENGINE'" ;;
    esac
    case "$PROJECT_MIN_SDK" in
        ''|*[!0-9]*) die AIC_VALIDATE_SDK "minSdk must be an integer" ;;
    esac
    if [ "$PROJECT_MIN_SDK" -lt 21 ]; then
        die AIC_VALIDATE_SDK "v1 requires minSdk >= 21 (got $PROJECT_MIN_SDK)"
    fi
    require_toolchain "$PROJECT_COMPILE_SDK"
    ensure_debug_keystore
    stage_ok validate "$(aic_elapsed_ms "$t0")"
}

stage_aapt2_compile() {
    stage_banner 2 8 "aapt2 compile"
    t0=$(aic_now_ms)
    mkdir_p "$BUILD_COMPILED"
    [ -d "$SRC_RES" ] || die AIC_AAPT2_RES "missing resource directory $SRC_RES"
    out="$BUILD_COMPILED/project.zip"
    errf="$BUILD_DIR/intermediates/aapt2-compile.log"
    if ! "$TOOL_AAPT2" compile --dir "$SRC_RES" -o "$out" >"$errf" 2>&1; then
        msg=$(cat "$errf" 2>/dev/null | tr '\n' ' ')
        stage_fail aapt2_compile AIC_AAPT2_COMPILE "aapt2 compile failed: $msg"
    fi
    stage_ok aapt2_compile "$(aic_elapsed_ms "$t0")"
}

stage_aapt2_link() {
    stage_banner 3 8 "aapt2 link"
    t0=$(aic_now_ms)
    mkdir_p "$BUILD_GEN"
    mkdir_p "$BUILD_RES"
    errf="$BUILD_DIR/intermediates/aapt2-link.log"
    set -- \
        "$TOOL_AAPT2" link \
        --auto-add-overlay \
        --no-version-vectors \
        --no-version-transitions \
        --min-sdk-version "$PROJECT_MIN_SDK" \
        --target-sdk-version "$PROJECT_TARGET_SDK" \
        --version-code "$PROJECT_VERSION_CODE" \
        --version-name "$PROJECT_VERSION_NAME" \
        -I "$TOOL_ANDROID_JAR" \
        --java "$BUILD_GEN" \
        --manifest "$SRC_MANIFEST" \
        -o "$BUILD_RESOURCES_APK"
    if [ -d "$SRC_ASSETS" ] && [ "$(find "$SRC_ASSETS" -type f 2>/dev/null | wc -l | awk '{print $1}')" -gt 0 ]; then
        set -- "$@" -A "$SRC_ASSETS"
    fi
    for z in "$BUILD_COMPILED"/*.zip; do
        [ -f "$z" ] || continue
        set -- "$@" -R "$z"
    done
    if ! "$@" >"$errf" 2>&1; then
        msg=$(cat "$errf" 2>/dev/null | tr '\n' ' ')
        stage_fail aapt2_link AIC_AAPT2_LINK "aapt2 link failed: $msg"
    fi
    stage_ok aapt2_link "$(aic_elapsed_ms "$t0")"
}

stage_javac() {
    stage_banner 4 8 javac
    t0=$(aic_now_ms)
    mkdir_p "$BUILD_CLASSES"
    slist="$BUILD_DIR/intermediates/sources.list"
    : > "$slist"
    find "$SRC_JAVA" -type f -name '*.java' >> "$slist" 2>/dev/null || true
    if [ -d "$BUILD_GEN" ]; then
        find "$BUILD_GEN" -type f -name '*.java' >> "$slist" 2>/dev/null || true
    fi
    [ -s "$slist" ] || stage_fail javac AIC_JAVAC_EMPTY "no Java sources to compile"

    cp="$TOOL_ANDROID_JAR"
    if [ -d "$SRC_LIBS" ]; then
        for j in "$SRC_LIBS"/*.jar; do
            [ -f "$j" ] && cp="$cp:$j"
        done
    fi
    errf="$BUILD_DIR/intermediates/javac.log"
    src_ver="$PROJECT_JAVA"
    tgt_ver="$PROJECT_JAVA"
    if ! "$TOOL_JAVAC" \
        -source "$src_ver" -target "$tgt_ver" \
        -encoding UTF-8 \
        -bootclasspath "$TOOL_ANDROID_JAR" \
        -cp "$cp" \
        -d "$BUILD_CLASSES" \
        -proc:none \
        @"$slist" >"$errf" 2>&1
    then
        if [ "$AIC_JSON" != "1" ]; then
            printf '%sFAIL%s\n' "$C_RED" "$C_RESET" >&2
        fi
        parse_javac_errors < "$errf" || true
        exit 1
    fi
    stage_ok javac "$(aic_elapsed_ms "$t0")"
}

stage_d8() {
    stage_banner 5 8 d8
    t0=$(aic_now_ms)
    mkdir_p "$BUILD_DEX"
    errf="$BUILD_DIR/intermediates/d8.log"
    set -- "$BUILD_CLASSES"
    if [ -d "$SRC_LIBS" ]; then
        for j in "$SRC_LIBS"/*.jar; do
            [ -f "$j" ] && set -- "$@" "$j"
        done
    fi
    if ! run_d8 "$PROJECT_MIN_SDK" "$TOOL_ANDROID_JAR" "$BUILD_DEX" "$@" >"$errf" 2>&1; then
        msg=$(cat "$errf" 2>/dev/null | tr '\n' ' ')
        stage_fail d8 AIC_D8_FAIL "d8 failed: $msg"
    fi
    [ -f "$BUILD_DEX/classes.dex" ] || stage_fail d8 AIC_D8_EMPTY "d8 did not produce classes.dex"
    stage_ok d8 "$(aic_elapsed_ms "$t0")"
}

stage_pack() {
    stage_banner 6 8 pack
    t0=$(aic_now_ms)
    mkdir_p "$BUILD_APK_DIR"
    errf="$BUILD_DIR/intermediates/pack.log"
    if have_cmd python3; then
        if ! python3 - "$BUILD_RESOURCES_APK" "$BUILD_DEX" "$SRC_JNI" "$BUILD_UNSIGNED" <<'PY' >"$errf" 2>&1
import os, shutil, sys, zipfile

res_apk, dex_dir, jni_dir, out_apk = sys.argv[1:5]
shutil.copyfile(res_apk, out_apk)
with zipfile.ZipFile(out_apk, "a") as z:
    existing = set(z.namelist())
    for name in sorted(os.listdir(dex_dir)):
        if not name.endswith(".dex"):
            continue
        src = os.path.join(dex_dir, name)
        if name in existing:
            continue
        # DEX compresses well; stored vs deflated both work after zipalign.
        z.write(src, name, compress_type=zipfile.ZIP_DEFLATED)
    if os.path.isdir(jni_dir):
        for root, _dirs, files in os.walk(jni_dir):
            for fn in files:
                if not fn.endswith(".so"):
                    continue
                full = os.path.join(root, fn)
                rel = os.path.relpath(full, jni_dir)
                arc = "lib/" + rel.replace(os.sep, "/")
                if arc in existing:
                    continue
                z.write(full, arc, compress_type=zipfile.ZIP_DEFLATED)
PY
        then
            msg=$(cat "$errf" 2>/dev/null | tr '\n' ' ')
            stage_fail pack AIC_PACK_ZIP "pack failed: $msg"
        fi
    else
        cp -f "$BUILD_RESOURCES_APK" "$BUILD_UNSIGNED" || stage_fail pack AIC_PACK_COPY "cannot copy resources.apk"
        (
            cd "$BUILD_DEX" || exit 1
            zip -q -u "$BUILD_UNSIGNED" classes.dex classes*.dex 2>/dev/null || zip -q -u "$BUILD_UNSIGNED" classes.dex
        ) || stage_fail pack AIC_PACK_ZIP "zip merge of classes.dex failed"
        if [ -d "$SRC_JNI" ]; then
            (
                cd "$SRC_JNI" || exit 0
                find . -name '*.so' | while IFS= read -r so; do
                    so=${so#./}
                    zip -q -u "$BUILD_UNSIGNED" "$so" || true
                done
            )
        fi
    fi
    stage_ok pack "$(aic_elapsed_ms "$t0")"
}

stage_align() {
    stage_banner 7 8 zipalign
    t0=$(aic_now_ms)
    errf="$BUILD_DIR/intermediates/zipalign.log"
    if ! "$TOOL_ZIPALIGN" -p -f 4 "$BUILD_UNSIGNED" "$BUILD_ALIGNED" >"$errf" 2>&1; then
        msg=$(cat "$errf" 2>/dev/null | tr '\n' ' ')
        stage_fail align AIC_SIGN_ALIGN "zipalign failed: $msg"
    fi
    stage_ok align "$(aic_elapsed_ms "$t0")"
}

stage_sign() {
    stage_banner 8 8 sign
    t0=$(aic_now_ms)
    mkdir_p "$BUILD_OUT"
    errf="$BUILD_DIR/intermediates/sign.log"
    if ! run_apksigner "$BUILD_ALIGNED" "$BUILD_SIGNED" "$TOOL_KEYSTORE" "$TOOL_KS_TYPE" >"$errf" 2>&1; then
        msg=$(cat "$errf" 2>/dev/null | tr '\n' ' ')
        stage_fail sign AIC_SIGN_FAIL "apksigner failed: $msg"
    fi
    if ! verify_apk "$BUILD_SIGNED" >>"$errf" 2>&1; then
        msg=$(cat "$errf" 2>/dev/null | tr '\n' ' ')
        stage_fail sign AIC_SIGN_VERIFY "apksigner verify failed: $msg"
    fi
    stage_ok sign "$(aic_elapsed_ms "$t0")"
}

run_build() {
    variant="${1:-debug}"
    clean_first="${2:-0}"
    if [ "$variant" = "release" ]; then
        die AIC_SIGN_NO_KEY "local --release is not supported in v1 (no release keys). Use debug, or remote Gradle later."
    fi
    [ "$variant" = "debug" ] || die AIC_VALIDATE_VARIANT "unknown variant '$variant' (v1 supports debug)"
    [ "$clean_first" = "1" ] && cmd_clean_quiet

    t_all=$(aic_now_ms)
    if [ "$AIC_JSON" != "1" ]; then
        printf '%sAICore%s v%s\n' "$C_BOLD" "$C_RESET" "$AICORE_VERSION"
        printf 'project: %s  (%s)\n' "$PROJECT_NAME" "$PROJECT_PACKAGE"
        printf 'engine:  aicore  java%s  minSdk %s / targetSdk %s\n\n' "$PROJECT_JAVA" "$PROJECT_MIN_SDK" "$PROJECT_TARGET_SDK"
    fi
    stage_validate
    stage_aapt2_compile
    stage_aapt2_link
    stage_javac
    stage_d8
    stage_pack
    stage_align
    stage_sign
    bytes=$(wc -c < "$BUILD_SIGNED" | awk '{print $1}')
    ms=$(aic_elapsed_ms "$t_all")
    if [ "$AIC_JSON" = "1" ]; then
        aic_emit build_ok apk "$BUILD_SIGNED" bytes "$bytes" ms "$ms"
    else
        printf '\n%sBUILD OK%s  %s  %sms\n' "$C_GREEN" "$C_RESET" "$(human_bytes "$bytes")" "$ms"
        printf '%s\n' "$BUILD_SIGNED"
    fi
}

cmd_clean_quiet() {
    [ -n "$BUILD_DIR" ] && [ -d "$BUILD_DIR" ] && rm -rf "$BUILD_DIR"
}

cmd_clean() {
    if [ -d "$BUILD_DIR" ]; then
        rm -rf "$BUILD_DIR"
        log "removed $BUILD_DIR"
    else
        log "nothing to clean"
    fi
}

cmd_info() {
    if [ "$AIC_JSON" = "1" ]; then
        printf '{"event":"info","name":"%s","package":"%s","minSdk":%s,"targetSdk":%s,"compileSdk":%s,"engine":"%s","language":"%s","apk":"%s"}\n' \
            "$(json_escape "$PROJECT_NAME")" \
            "$(json_escape "$PROJECT_PACKAGE")" \
            "$PROJECT_MIN_SDK" "$PROJECT_TARGET_SDK" "$PROJECT_COMPILE_SDK" \
            "$(json_escape "$PROJECT_ENGINE")" \
            "$(json_escape "$PROJECT_LANGUAGE")" \
            "$(json_escape "$BUILD_SIGNED")"
    else
        printf 'name         %s\n' "$PROJECT_NAME"
        printf 'package      %s\n' "$PROJECT_PACKAGE"
        printf 'version      %s (%s)\n' "$PROJECT_VERSION_NAME" "$PROJECT_VERSION_CODE"
        printf 'minSdk       %s\n' "$PROJECT_MIN_SDK"
        printf 'targetSdk    %s\n' "$PROJECT_TARGET_SDK"
        printf 'compileSdk   %s\n' "$PROJECT_COMPILE_SDK"
        printf 'engine       %s\n' "$PROJECT_ENGINE"
        printf 'language     %s\n' "$PROJECT_LANGUAGE"
        printf 'java         %s\n' "$PROJECT_JAVA"
        printf 'template     %s\n' "$PROJECT_TEMPLATE"
        printf 'manifest     %s\n' "$SRC_MANIFEST"
        if [ -f "$BUILD_SIGNED" ]; then
            printf 'last apk     %s (%s)\n' "$BUILD_SIGNED" "$(human_bytes "$(wc -c < "$BUILD_SIGNED" | awk '{print $1}')")"
        else
            printf 'last apk     (none)\n'
        fi
    fi
}
