# 04 — Target Architecture

## Layers

```
AiCode App (Kotlin/Compose)
  ├── Agent  ── android_* tools ──┐
  ├── Terminal (human) ── aic CLI ─┼──► AICore engine (in Linux)
  └── (later) Build UI ────────────┘          │
                                              ▼
                                   Android toolchain
                                   AAPT2 javac D8 zipalign apksigner android.jar
                                              ▼
                                            APK
```

The Android app **never** compiles. It starts processes in the Linux box (already true for `Bash` / terminal) and optionally parses JSON.

## Process shapes

### A. CLI (Phase 1)

```
aic <command> [--json] [--project DIR]
```

- Human: pretty progress on stderr, summary on stdout.
- `--json`: one JSON object per line (JSONL events) on stdout; exit 0/1.

The agent can already use this via `Bash`. Good enough for first APK.

### B. Library + CLI wrapper (Phase 1 internal)

Even if we only ship a CLI, structure the code as:

```
AICore (library)
  ProjectLoader      read aicode.json + tree
  TemplateEngine     aic init
  Toolchain          locate aapt2/javac/d8/...
  Stages             validate, aapt2 compile, aapt2 link, javac, d8, pack, align, sign
  Cache              (Phase 5) hash → skip
  Reporter           events

cmd/aic              cobra/urfave/posix case
```

### C. Daemon (Phase 3, optional)

```
aic daemon --stdio     JSON-RPC 2.0 over stdin/stdout
```

Agent tool holds one process, sends `build` / `cancel`, gets `progress` notifications. Only worth it when CLI spawn + log scraping hurts.

## Where bits live on disk (Linux view)

```
/root/.aicode/                          # bind: host filesDir/aicode  (persistent)
  bin/aicode                            # EXISTING env installer — do not replace
  bin/aic                               # NEW compiler CLI
  lib/                                  # EXISTING provision libraries
  compiler/                             # NEW shared compiler assets
    sdk/
      android.jar                       # one compileSdk (e.g. 34)
      build-tools/                      # aapt2, d8, zipalign, apksigner (or wrappers)
    libs/                               # curated exploded AARs (Phase 2)
    templates/
      native-xml/
      material/                         # Phase 5
    debug.keystore
    cache/                              # shared intermediate cache (Phase 5)

/root/workspace/                        # bind: current AiCode workspace
  MyFirstApp/
    aicode.json
    app/src/main/java/...
    app/src/main/res/...
    app/src/main/AndroidManifest.xml
    app/libs/                           # optional local jars
    build/                              # gitignored outputs
      intermediates/
      outputs/apk/debug/app-debug.apk
```

Host equivalents: replace `/root/.aicode` with `$filesDir/aicode`, `/root/workspace` with `$filesDir/projects/<ws>`.

## Data flow for a local debug build

1. `aic build` (cwd has `aicode.json` or `--project` given).
2. Load config, resolve toolchain (`aic doctor` logic).
3. Run stages; each stage emits `stage_start` / `stage_ok` / `stage_error`.
4. Write APK; emit `build_ok` with path, size, duration.
5. Exit 0.

Failure: emit `build_error` with `stage`, `file`, `line`, `column`, `message`, `code`; exit 1. Leave intermediates for debugging.

## Remote / release flow (Phase 6)

```
Local aic                  InstaBox linux_machine / GitHub Actions
─────────                  ──────────────────────────────────────
aic build --release   →    if engine=aicore: same binary on cloud
                      →    if engine=gradle: ./gradlew assembleRelease
                      ←    APK bytes / URL
```

Signing: keys stay in AiCode credentials store or GH secrets. Agent never sees key material. Local `--release` without keys must refuse.

## Dual engine flag in aicode.json

```json
"compiler": { "engine": "aicore" | "gradle" }
```

- `aicore` (default): this pipeline.
- `gradle`: `aic build` becomes a thin wrapper around `./gradlew assembleDebug` so the CLI/agent tools still work on advanced projects. Do not implement until Phase 6.

## Why not compile in the Android app

Sketchware does, because it has no Linux. AiCode has PRoot. In-app ECJ + extracting aapt2 to cacheDir would:

- Fight `targetSdk 28` forever
- Duplicate the container toolchain
- Be unreachable from the terminal (`aic` would not exist)
- Break remote SSH (no app JVM there)

The Linux box is the only place that works for local + SSH + cloud with one binary.

## Language of the engine (recommendation, not locked)

See `10-DECISIONS-AND-OPEN-QUESTIONS.md`. Default plan:

**Phase 0 prototype:** POSIX `sh` orchestrator (`aic` as a script) so we can produce an APK this week without a Go CI story. Keep stages in separate functions.

**Phase 1:** rewrite orchestrator in **Go** (`CGO_ENABLED=0`, linux/arm64 + linux/amd64), ship two binaries in assets, `extractEnvTool` picks by `uname -m`.

Do not use Node. Do not use the app's Kotlin.

## Module boundary

```
compiler/                 standalone; no Android imports
app/ ... tool/android/    Kotlin wrappers only
assets/aicode/bin/aic     shipped binary/script
```

The Kotlin wrappers must not reimplement stages. They exec `aic --json` or talk JSON-RPC.
