# JSONL events + aicode.json reader. POSIX sh. Sourced.

aic_emit() {
    event="$1"
    shift
    if [ "$AIC_JSON" = "1" ]; then
        printf '{"event":"%s"' "$event"
        while [ "$#" -ge 2 ]; do
            k="$1"
            v="$2"
            shift 2
            case "$k" in
                index|total|ms|bytes|line|column)
                    printf ',"%s":%s' "$k" "$v"
                    ;;
                *)
                    printf ',"%s":"%s"' "$k" "$(json_escape "$v")"
                    ;;
            esac
        done
        printf '}\n'
    fi
}

aic_emit_error() {
    stage="$1"
    file="$2"
    line="$3"
    column="$4"
    message="$5"
    code="$6"
    if [ "$AIC_JSON" = "1" ]; then
        printf '{"event":"build_error","stage":"%s"' "$(json_escape "$stage")"
        [ -n "$file" ] && printf ',"file":"%s"' "$(json_escape "$file")"
        [ -n "$line" ] && printf ',"line":%s' "$line"
        [ -n "$column" ] && printf ',"column":%s' "$column"
        printf ',"message":"%s","code":"%s"}\n' "$(json_escape "$message")" "$(json_escape "$code")"
    else
        printf '%serror:%s [%s] %s' "$C_RED" "$C_RESET" "$code" "$message" >&2
        if [ -n "$file" ]; then
            printf ' (%s' "$file" >&2
            [ -n "$line" ] && printf ':%s' "$line" >&2
            [ -n "$column" ] && printf ':%s' "$column" >&2
            printf ')' >&2
        fi
        printf '\n' >&2
    fi
}

# Pull a scalar (string or number) for a known key from a simple JSON file.
# Nested objects are flattened by unique key names in aicode.json format 1.
json_scalar() {
    file="$1"
    key="$2"
    default="$3"
    [ -f "$file" ] || { printf '%s' "$default"; return 0; }
    val=$(awk -v key="$key" '
        {
            line = $0
            # "key": "value"
            pat = "\"" key "\"[[:space:]]*:[[:space:]]*\""
            if (match(line, pat)) {
                rest = substr(line, RSTART + RLENGTH)
                if (match(rest, /^([^"\\]|\\.)*"/)) {
                    val = substr(rest, 1, RLENGTH - 1)
                    gsub(/\\"/, "\"", val)
                    gsub(/\\\\/, "\\", val)
                    print val
                    found = 1
                    exit
                }
            }
            # "key": 123
            patn = "\"" key "\"[[:space:]]*:[[:space:]]*"
            if (match(line, patn)) {
                rest = substr(line, RSTART + RLENGTH)
                if (match(rest, /^-?[0-9]+(\.[0-9]+)?/)) {
                    print substr(rest, RSTART, RLENGTH)
                    found = 1
                    exit
                }
            }
        }
        END { if (!found) exit 2 }
    ' "$file" 2>/dev/null) || true
    if [ -n "$val" ]; then
        printf '%s' "$val"
    else
        printf '%s' "$default"
    fi
}

load_project() {
    PROJECT_DIR="$1"
    cfg="$PROJECT_DIR/aicode.json"
    [ -f "$cfg" ] || die AIC_VALIDATE_MISSING "aicode.json not found in $PROJECT_DIR"

    PROJECT_FORMAT=$(json_scalar "$cfg" format 1)
    PROJECT_NAME=$(json_scalar "$cfg" name "$(basename "$PROJECT_DIR")")
    PROJECT_PACKAGE=$(json_scalar "$cfg" package "")
    PROJECT_VERSION_CODE=$(json_scalar "$cfg" code 1)
    PROJECT_VERSION_NAME=$(json_scalar "$cfg" name "1.0.0")
    # The first "name" wins (project name). Re-read version.name from a tighter window.
    PROJECT_VERSION_NAME=$(awk '
        /"version"[[:space:]]*:/ { in_v=1 }
        in_v && /"name"[[:space:]]*:/ {
            if (match($0, /"name"[[:space:]]*:[[:space:]]*"/)) {
                rest = substr($0, RSTART + RLENGTH)
                if (match(rest, /^([^"\\]|\\.)*"/)) {
                    print substr(rest, 1, RLENGTH - 1)
                    exit
                }
            }
        }
    ' "$cfg")
    [ -n "$PROJECT_VERSION_NAME" ] || PROJECT_VERSION_NAME="1.0.0"
    PROJECT_VERSION_CODE=$(awk '
        /"version"[[:space:]]*:/ { in_v=1 }
        in_v && /"code"[[:space:]]*:/ {
            if (match($0, /"code"[[:space:]]*:[[:space:]]*/)) {
                rest = substr($0, RSTART + RLENGTH)
                if (match(rest, /^-?[0-9]+/)) { print substr(rest, RSTART, RLENGTH); exit }
            }
        }
    ' "$cfg")
    [ -n "$PROJECT_VERSION_CODE" ] || PROJECT_VERSION_CODE=1

    PROJECT_MIN_SDK=$(json_scalar "$cfg" minSdk 24)
    PROJECT_TARGET_SDK=$(json_scalar "$cfg" targetSdk 34)
    PROJECT_COMPILE_SDK=$(json_scalar "$cfg" compileSdk 34)
    PROJECT_ENGINE=$(json_scalar "$cfg" engine aicore)
    PROJECT_LANGUAGE=$(json_scalar "$cfg" language java)
    PROJECT_JAVA=$(json_scalar "$cfg" java 17)
    PROJECT_TEMPLATE=$(json_scalar "$cfg" template native-xml)
    PROJECT_BUILD_TYPE=$(json_scalar "$cfg" type debug)

    SRC_JAVA="$PROJECT_DIR/app/src/main/java"
    SRC_RES="$PROJECT_DIR/app/src/main/res"
    SRC_ASSETS="$PROJECT_DIR/app/src/main/assets"
    SRC_JNI="$PROJECT_DIR/app/src/main/jniLibs"
    SRC_MANIFEST="$PROJECT_DIR/app/src/main/AndroidManifest.xml"
    SRC_LIBS="$PROJECT_DIR/app/libs"
    BUILD_DIR="$PROJECT_DIR/build"
    BUILD_GEN="$BUILD_DIR/gen"
    BUILD_RES="$BUILD_DIR/intermediates/res"
    BUILD_COMPILED="$BUILD_RES/compiled"
    BUILD_CLASSES="$BUILD_DIR/intermediates/classes"
    BUILD_DEX="$BUILD_DIR/intermediates/dex"
    BUILD_APK_DIR="$BUILD_DIR/intermediates/apk"
    BUILD_OUT="$BUILD_DIR/outputs/apk/debug"
    BUILD_RESOURCES_APK="$BUILD_RES/resources.apk"
    BUILD_UNSIGNED="$BUILD_APK_DIR/unsigned.apk"
    BUILD_ALIGNED="$BUILD_APK_DIR/aligned.apk"
    BUILD_SIGNED="$BUILD_OUT/app-debug.apk"
}
