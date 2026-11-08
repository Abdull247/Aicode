# 06 — CLI and Daemon

Binary name: **`aic`** (never `aicode`).

## Global flags

```
aic [--project DIR] [--json] [--verbose] <command>
```

- `--project` defaults to cwd; walk up looking for `aicode.json`.
- `--json` JSONL on stdout; pretty text otherwise.
- Exit: 0 ok, 1 user/project error, 2 toolchain error, 130 cancel.

## Commands (v1 minimum marked *)

| Command | Purpose | Phase |
| --- | --- | --- |
| `aic doctor` * | Toolchain report | 1 |
| `aic init <name>` * | Create project from template | 1 |
| `aic build` * | Debug APK | 1 |
| `aic clean` * | Delete `build/` | 1 |
| `aic info` | Dump resolved plan | 1 |
| `aic template list` | List templates | 1 |
| `aic sdk list` | Platforms / build-tools found | 3 |
| `aic build --release` | Refuse locally unless keys; later remote | 6 |
| `aic install` | `adb install -r` if adb works | 4 |
| `aic daemon --stdio` | JSON-RPC | 3 |

### `aic init`

```
aic init WeatherApp --package com.example.weather --template native-xml
```

Creates `./WeatherApp/` (or `--dir`) with `aicode.json` + template tree. Refuse if directory exists unless `--force`.

### `aic build`

```
aic build [--variant debug] [--clean]
```

v1 ignores `--variant` other than `debug`. `--release` → `AIC_SIGN_NO_KEY` until Phase 6.

### `aic doctor`

Print:

```
javac        /usr/lib/jvm/.../bin/javac     17.0.x
aapt2        /root/android/sdk/build-tools/35.0.0/aapt2   35.0.2
d8           ...
zipalign     ...
apksigner    ...
android.jar  .../platforms/android-34/android.jar
keystore     /root/.aicode/compiler/debug.keystore
```

Exit 2 if any required missing.

## Daemon (Phase 3)

JSON-RPC 2.0 over stdio, one object per line (LSP-style).

Methods:

- `initialize` → engine version, toolchain
- `project/load` `{path}`
- `build/start` `{variant}` → `{id}`
- `build/cancel` `{id}`
- `build/diagnostics` `{id}`

Notifications:

- `build/progress` `{id,stage,index,total,message}`
- `build/finished` `{id,ok,apk?,errors[]}`

Kotlin side: spawn like `StdioTransport` already does for MCP (`LinuxContainerEngine.startStdioProcess`). Reuse that pattern; do not invent a second stdio host.

## Human output example

```
AICore v0.1
project: MyFirstApp  (com.example.app)
toolchain: javac17 aapt2-35.0.2 d8

[1/8] validate           ok    12ms
[2/8] aapt2 compile      ok    80ms
[3/8] aapt2 link         ok   210ms
[4/8] javac              ok   400ms
[5/8] d8                 ok   350ms
[6/8] pack               ok    40ms
[7/8] zipalign           ok    20ms
[8/8] sign               ok    90ms

BUILD OK  1.2 MB  1.2s
build/outputs/apk/debug/app-debug.apk
```
