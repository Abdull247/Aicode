# aic init + templates. POSIX sh. Sourced.

template_root() {
    # Prefer compiler home (downloaded / extracted), then the tree next to this script.
    if [ -d "$AICORE_HOME/templates" ]; then
        printf '%s' "$AICORE_HOME/templates"
        return 0
    fi
    if [ -d "$AICORE_LIB_DIR/../templates" ]; then
        printf '%s' "$AICORE_LIB_DIR/../templates"
        return 0
    fi
    printf '%s' "$AICORE_HOME/templates"
}

list_templates() {
    root=$(template_root)
    if [ "$AIC_JSON" = "1" ]; then
        printf '{"event":"templates"'
        printf ',"items":['
        first=1
        for d in "$root"/*; do
            [ -d "$d" ] || continue
            id=$(basename "$d")
            [ "$first" = 1 ] || printf ','
            first=0
            printf '"%s"' "$(json_escape "$id")"
        done
        printf ']}\n'
        return 0
    fi
    printf 'templates in %s:\n' "$root"
    found=0
    for d in "$root"/*; do
        [ -d "$d" ] || continue
        found=1
        id=$(basename "$d")
        title="$id"
        [ -f "$d/template.json" ] && title=$(json_scalar "$d/template.json" title "$id")
        printf '  %-16s %s\n' "$id" "$title"
    done
    [ "$found" = 1 ] || printf '  (none — native-xml is bundled next to aic)\n'
}

replace_placeholders() {
    file="$1"
    pkg="$2"
    pkg_path="$3"
    app_name="$4"
    min_sdk="$5"
    target_sdk="$6"
    compile_sdk="$7"
    # Portable in-place replace without depending on GNU sed -i.
    tmp="$file.__aic"
    awk -v pkg="$pkg" -v pkg_path="$pkg_path" -v app="$app_name" \
        -v min="$min_sdk" -v tgt="$target_sdk" -v cmp="$compile_sdk" '
        {
            gsub(/\{\{PACKAGE\}\}/, pkg)
            gsub(/\{\{PACKAGE_PATH\}\}/, pkg_path)
            gsub(/\{\{APP_NAME\}\}/, app)
            gsub(/\{\{MIN_SDK\}\}/, min)
            gsub(/\{\{TARGET_SDK\}\}/, tgt)
            gsub(/\{\{COMPILE_SDK\}\}/, cmp)
            print
        }
    ' "$file" > "$tmp" && mv "$tmp" "$file"
}

copy_template_tree() {
    src="$1"
    dest="$2"
    pkg="$3"
    pkg_path="$4"
    app_name="$5"
    min_sdk="$6"
    target_sdk="$7"
    compile_sdk="$8"
    (cd "$src" && find . -type d) | while IFS= read -r d; do
        d=${d#./}
        [ -z "$d" ] && continue
        mapped=$(printf '%s' "$d" | sed "s|{{PACKAGE_PATH}}|$pkg_path|g; s|__PACKAGE_PATH__|$pkg_path|g")
        mkdir_p "$dest/$mapped"
    done
    (cd "$src" && find . -type f) | while IFS= read -r f; do
        f=${f#./}
        [ -z "$f" ] && continue
        mapped=$(printf '%s' "$f" | sed "s|{{PACKAGE_PATH}}|$pkg_path|g; s|__PACKAGE_PATH__|$pkg_path|g")
        mkdir_p "$(dirname "$dest/$mapped")"
        cp -f "$src/$f" "$dest/$mapped"
        case "$mapped" in
            *.png|*.jpg|*.webp|*.jar|*.so|*.keystore) ;;
            *) replace_placeholders "$dest/$mapped" "$pkg" "$pkg_path" "$app_name" "$min_sdk" "$target_sdk" "$compile_sdk" ;;
        esac
    done
}

write_aicode_json() {
    dest="$1"
    name="$2"
    pkg="$3"
    min_sdk="$4"
    target_sdk="$5"
    compile_sdk="$6"
    template="$7"
    cat > "$dest/aicode.json" <<EOF
{
  "format": 1,
  "name": "$name",
  "package": "$pkg",
  "version": { "code": 1, "name": "1.0.0" },
  "platform": {
    "minSdk": $min_sdk,
    "targetSdk": $target_sdk,
    "compileSdk": $compile_sdk
  },
  "compiler": {
    "engine": "aicore",
    "language": "java",
    "java": 17,
    "template": "$template"
  },
  "dependencies": [],
  "build": { "type": "debug" }
}
EOF
}

write_gitignore() {
    cat > "$1/.gitignore" <<'EOF'
build/
.aicode/
local.properties
EOF
}

cmd_init() {
    name="$1"
    pkg="$AIC_INIT_PACKAGE"
    template="${AIC_INIT_TEMPLATE:-native-xml}"
    dest="$AIC_INIT_DIR"
    force="$AIC_INIT_FORCE"
    min_sdk="${AIC_INIT_MIN_SDK:-24}"
    target_sdk="${AIC_INIT_TARGET_SDK:-34}"
    compile_sdk="${AIC_INIT_COMPILE_SDK:-34}"

    [ -n "$name" ] || die AIC_VALIDATE_INIT "usage: aic init <name> [--package com.example.app] [--template native-xml]"
    [ -n "$pkg" ] || pkg="com.example.$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9_')"
    valid_java_package "$pkg" || die AIC_VALIDATE_PACKAGE "invalid Java package '$pkg'"
    pkg_path=$(package_to_path "$pkg")
    [ -n "$dest" ] || dest="$(pwd)/$name"

    if [ -e "$dest" ] && [ "$force" != "1" ]; then
        die AIC_VALIDATE_EXISTS "directory already exists: $dest (pass --force)"
    fi
    mkdir_p "$dest"

    root=$(template_root)
    tdir="$root/$template"
    if [ ! -d "$tdir/project" ]; then
        # Bundled fallback lives next to the compiler tree even before setup.
        bundled="$AICORE_LIB_DIR/../templates/$template"
        if [ -d "$bundled/project" ]; then
            tdir="$bundled"
        else
            die AIC_VALIDATE_TEMPLATE "unknown template '$template'"
        fi
    fi

    copy_template_tree "$tdir/project" "$dest" "$pkg" "$pkg_path" "$name" "$min_sdk" "$target_sdk" "$compile_sdk"
    write_aicode_json "$dest" "$name" "$pkg" "$min_sdk" "$target_sdk" "$compile_sdk" "$template"
    write_gitignore "$dest"

    if [ "$AIC_JSON" = "1" ]; then
        printf '{"event":"init_ok","path":"%s","package":"%s","template":"%s"}\n' \
            "$(json_escape "$dest")" "$(json_escape "$pkg")" "$(json_escape "$template")"
    else
        printf 'created %s\n' "$dest"
        printf '  package  %s\n' "$pkg"
        printf '  template %s\n' "$template"
        printf 'next: cd %s && aic build\n' "$dest"
    fi
}
