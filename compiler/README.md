# AICore (`aic`)

Lightweight Android build engine for the AiCode Linux box. Orchestrates AAPT2, javac, D8, zipalign and apksigner without Gradle.

The existing `aicode` command is the env installer. This CLI is **`aic`**. Never overwrite `aicode`.

## Layout

```
compiler/
  aic                 CLI entry
  lib/                common.sh json.sh toolchain.sh stages.sh init.sh
  templates/native-xml/
  testdata/hello/     golden Java/XML tree
```

Installed into the container as `/root/.aicode/bin/aic` (PATH already includes that dir). Shared assets live in `/root/.aicode/compiler/`.

## First APK

In a Debian/Ubuntu aarch64 container with JDK 17:

```
aic setup android
aic doctor
aic init HelloAi --package com.example.hello
cd HelloAi && aic build
ls build/outputs/apk/debug/app-debug.apk
```

`aic setup` reads `GET /api/toolchain/manifest` from AiCodeProBackend and downloads missing components from R2. Until those artifacts are uploaded, install the Android scenario via `aicode` (JDK + SDK + ARM64 overlay) and `aic doctor` will pick up `$ANDROID_HOME`.

## Commands

| Command | Purpose |
| --- | --- |
| `aic doctor` | Toolchain report (exit 2 if anything required is missing) |
| `aic setup android` | Fetch registry + install components |
| `aic init <name>` | Create a `native-xml` project |
| `aic build` | Debug APK |
| `aic build --json` | JSONL events for the agent |
| `aic clean` | Delete `build/` |
| `aic info` | Resolved plan |
| `aic template list` | Shipped templates |

v1 is Java 17 + XML + framework `Activity`, minSdk >= 21, debug keystore only. Kotlin / Compose / R8 / local release signing are out of scope.
