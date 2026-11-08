# 用 AICore (`aic`) 在容器里编译小应用

AiCode 自带一套轻量 Android 编译器 **AICore**，命令名是 `aic`（不要和容器环境安装器 `aicode` 搞混）。它不跑 Gradle，只编排 AAPT2、javac、D8、zipalign、apksigner，适合手机上做的小型 Java + XML 应用。

复杂项目（Kotlin、Compose、一堆 Maven 依赖、正式签名）仍然走原来的 [Gradle 教程](/advanced/build-android-app)。

## 它在哪

终端打开后，`aic` 已经在 PATH 上：

```
/root/.aicode/bin/aic          # 命令
/root/.aicode/compiler/        # 引擎、模板、可选 SDK 缓存
```

工作区里的项目是普通目录，例如 `~/workspace/HelloAi/`，带一份 `aicode.json`。不要写到 `/storage/emulated/0/.sketchware`。

## 第一次出 debug APK

需要 JDK 17。编译工具（aapt2 / d8 / android.jar）有两条来源：

1. **推荐**：先用 `aicode` 环境菜单装 Android 场景（JDK + SDK + ARM64 overlay），`aic doctor` 会自动找到 `$ANDROID_HOME`。
2. 或者跑 `aic setup android`，从 AiCode 后端清单下载精简工具链（需要你已经把组件上传到 R2）。

然后：

```
aic doctor
aic init HelloAi --package com.example.hello
cd HelloAi
aic build
ls build/outputs/apk/debug/app-debug.apk
```

加 `--json` 会打 JSONL 事件，方便 Agent 解析：

```
aic build --json
```

## 常用命令

| 命令 | 作用 |
| --- | --- |
| `aic doctor` | 检查 javac / aapt2 / d8 / zipalign / apksigner / android.jar |
| `aic setup android` | 按后端清单下载缺失组件 |
| `aic init <name>` | 用 `native-xml` 模板建项目 |
| `aic build` | 打 debug APK |
| `aic clean` | 删 `build/` |
| `aic info` | 打印解析后的工程配置 |
| `aic template list` | 列出模板 |

v1 限制：Java 17、XML、框架 `Activity`、minSdk >= 21、只用 debug 证书。出现 `.kt` 会直接报 `AIC_KOTLIN_UNSUPPORTED`。

## 工程格式

`aicode.json` 是唯一配置。模板生成的树和普通 Android 源码一样，Agent 用 `writeFile` / `editFile` 改 Java 和 XML 即可。
