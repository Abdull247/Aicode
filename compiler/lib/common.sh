# Shared helpers for AICore (`aic`). POSIX sh. Sourced, not executed.

AICORE_VERSION="0.1.0"
AICORE_HOME="${AICORE_HOME:-${HOME:-/root}/.aicode/compiler}"
AICORE_BIN_DIR="${AICORE_BIN_DIR:-${HOME:-/root}/.aicode/bin}"
AICORE_REGISTRY_URL="${AICORE_REGISTRY_URL:-https://aicodepro.onrender.com/api/toolchain}"

C_BOLD=$(printf '\033[1m')
C_CYAN=$(printf '\033[36m')
C_GREEN=$(printf '\033[32m')
C_YELLOW=$(printf '\033[33m')
C_RED=$(printf '\033[31m')
C_DIM=$(printf '\033[2m')
C_RESET=$(printf '\033[0m')

aic_now_ms() {
    if command -v date >/dev/null 2>&1; then
        d=$(date +%s%3N 2>/dev/null || true)
        case "$d" in
            *N|"") date +%s | awk '{printf "%s000", $1}' ;;
            *) printf '%s' "$d" ;;
        esac
    else
        printf '0'
    fi
}

aic_elapsed_ms() {
    start="$1"
    now=$(aic_now_ms)
    awk -v a="$start" -v b="$now" 'BEGIN { d=b-a; if (d<0) d=0; printf "%d", d }'
}

die() {
    code="$1"
    shift
    msg="$*"
    if [ "$AIC_JSON" = "1" ]; then
        aic_emit_error "fatal" "" "" "" "$msg" "$code"
    else
        printf '%serror:%s %s (%s)\n' "$C_RED" "$C_RESET" "$msg" "$code" >&2
    fi
    case "$code" in
        AIC_TOOLCHAIN_*) exit 2 ;;
        *) exit 1 ;;
    esac
}

log() {
    if [ "$AIC_JSON" != "1" ]; then
        printf '%s\n' "$*" >&2
    fi
}

log_dim() {
    if [ "$AIC_JSON" != "1" ]; then
        printf '%s%s%s\n' "$C_DIM" "$*" "$C_RESET" >&2
    fi
}

is_abs() {
    case "$1" in /*) return 0 ;; *) return 1 ;; esac
}

abs_path() {
    p="$1"
    if is_abs "$p"; then
        printf '%s' "$p"
        return 0
    fi
    printf '%s/%s' "$(pwd)" "$p"
}

mkdir_p() {
    mkdir -p "$1" || die AIC_PACK_001 "cannot create directory $1"
}

have_cmd() {
    command -v "$1" >/dev/null 2>&1
}

file_sha256() {
    f="$1"
    if have_cmd sha256sum; then
        sha256sum "$f" | awk '{print $1}'
    elif have_cmd shasum; then
        shasum -a 256 "$f" | awk '{print $1}'
    elif have_cmd openssl; then
        openssl dgst -sha256 "$f" | awk '{print $NF}'
    else
        printf ''
    fi
}

json_escape() {
    printf '%s' "$1" | awk '
    BEGIN { ORS="" }
    {
        gsub(/\\/, "\\\\")
        gsub(/"/, "\\\"")
        gsub(/\t/, "\\t")
        gsub(/\r/, "\\r")
        n = split($0, a, "\n")
        for (i = 1; i <= n; i++) {
            printf "%s", a[i]
            if (i < n) printf "\\n"
        }
    }'
}

# Walk up from DIR (default cwd) looking for aicode.json.
find_project_root() {
    start="${1:-$(pwd)}"
    dir=$(abs_path "$start")
    while [ -n "$dir" ] && [ "$dir" != "/" ]; do
        if [ -f "$dir/aicode.json" ]; then
            printf '%s' "$dir"
            return 0
        fi
        parent=$(dirname "$dir")
        [ "$parent" = "$dir" ] && break
        dir="$parent"
    done
    return 1
}

valid_java_package() {
    printf '%s' "$1" | grep -Eq '^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*$'
}

package_to_path() {
    printf '%s' "$1" | tr '.' '/'
}

count_java_files() {
    root="$1"
    n=0
    if [ -d "$root" ]; then
        n=$(find "$root" -type f -name '*.java' 2>/dev/null | wc -l | awk '{print $1}')
    fi
    printf '%s' "$n"
}

count_kt_files() {
    root="$1"
    n=0
    if [ -d "$root" ]; then
        n=$(find "$root" -type f -name '*.kt' 2>/dev/null | wc -l | awk '{print $1}')
    fi
    printf '%s' "$n"
}

human_bytes() {
    awk -v b="$1" 'BEGIN {
        if (b < 1024) printf "%d B", b
        else if (b < 1048576) printf "%.1f KB", b/1024
        else printf "%.2f MB", b/1048576
    }'
}
