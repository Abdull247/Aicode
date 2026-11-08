#!/bin/sh
# 场景:Kotlin / Android(aicode 自定义编译器)。
# 与 android 场景一样装 JDK 17 + Android SDK + build-tools,并在装完后校验 AICore(`aic`)工具链。
# 编译器本体(`aic` 引擎 + native-xml 模板)已随 App 提取到 ~/.aicode/compiler,`aic` 位于 PATH 的
# ~/.aicode/bin;这里只备好它依赖的 JDK / SDK,可选地让用户据 doctor 结果补装缺失组件。
# 依赖 lib/android-sdk.sh 提供的共享安装逻辑。
scenario_name_android_compiler() { echo "Kotlin / Android development (aicode custom compiler)"; }

install_scenario_android_compiler() {
    if [ "$PMGR" != "apt" ]; then
        echo "${C_YELLOW}Note${C_RESET}: the AICore compiler works best on Debian / Ubuntu images. Built-in Alpine (musl) needs extra work."
        printf "Current package manager is %s. Continue anyway? [y/N]: " "${PMGR:-unknown}"
        read a
        case "$a" in y|Y) ;; *) return 2 ;; esac
    fi
    echo ""
    echo "Installing JDK and base tools..."
    pkg_add "$(jdk_pkg)" curl wget unzip openssl || return 1
    install_android_sdk "36" "35.0.0" || return 1
    install_android_tools_arm64 || true
    write_android_profile
    echo ""
    echo "${C_GREEN}Android / Kotlin SDK ready: $HOME/android/sdk${C_RESET}"
    echo "  New terminals pick up JAVA_HOME / ANDROID_HOME automatically."
    echo ""
    echo "${C_CYAN}AICore compiler setup${C_RESET}"
    if command -v aic >/dev/null 2>&1; then
        echo "  Verifying the aic toolchain (javac, aapt2, d8, zipalign, apksigner)..."
        aic doctor || echo "${C_YELLOW}Some components are missing. Re-run this scenario or install the Android SDK.${C_RESET}"
        echo ""
        echo "${C_GREEN}Create and build a small app: aic init MyApp && cd MyApp && aic build${C_RESET}"
    else
        echo "${C_YELLOW}aic is not on PATH yet.${C_RESET}"
        echo "  Reopen the terminal so AiCode extracts the compiler, then run: aic doctor"
    fi
    return 0
}
