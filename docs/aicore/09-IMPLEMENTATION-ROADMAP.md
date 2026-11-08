# 09 — Implementation Roadmap

Do not start Phase 1 until the owner leaves planning mode. This is the queue.

## Phase 0 — Handoff (DONE)

- [x] Clone AiCode + Sketchware-Pro
- [x] Read `chatgptconvo.md` end to end
- [x] Verify Sketchware pipeline from `DesignActivity` / `ProjectBuilder` / `ResourceCompiler` / `DexCompiler`
- [x] Verify AiCode container, PATH, existing `aicode` CLI, tools, InstaBox, workspace binds
- [x] Write `docs/aicore/*`

## Phase 1 — First debug APK (LANDED)

- [x] `compiler/` POSIX `aic` orchestrator, stages 0–8, JSONL, native-xml template, testdata/hello
- [x] Ship via `app/src/main/assets/aicode/bin/aic` + `compiler/`; `ContainerInstaller.extractEnvTool` copies them to `/root/.aicode/`
- [x] Backend JSON registry + `PUT /api/toolchain/upload` (R2) so `aic setup android` can curl components
- [ ] First on-device APK still needs JDK + aapt2/d8/android.jar (via `aicode` Android scenario or uploaded R2 artifacts)

## Phase 1 leftover (the only phase that matters)

Acceptance: in a Debian or Alpine aarch64 container with javac + aapt2 + d8 + android.jar,

```
aic init HelloAi --package com.example.hello
cd HelloAi && aic build
ls build/outputs/apk/debug/app-debug.apk
```

and the APK installs on a minSdk-24 device and shows "Hello".

Work items:

1. Create `compiler/` tree (POSIX `aic` script is OK).
2. Ship minimal toolchain **or** document `aic doctor` against an existing `$ANDROID_HOME` from `android.sh`.
3. Implement stages 0–8 without cache.
4. `native-xml` template.
5. `debug.keystore`.
6. JSONL + pretty output.
7. Golden test: `compiler/testdata/hello/` expected to build.

**First files to create:**

```
compiler/README.md                 # how to run aic in the container
compiler/aic                       # script
compiler/lib/stages.sh
compiler/lib/json.sh
compiler/templates/native-xml/
compiler/testdata/hello/
app/src/main/assets/aicode/bin/aic # copy or wrapper
```

Do **not** touch Kotlin app code in Phase 1 except dropping the script into assets so Terminal can run it.

## Phase 2 — Packaging robustness

- Native libs, extra dex
- Curated AppCompat/Material exploded AARs (optional template `material`)
- Better aapt2/javac error parsing
- `aic clean`, `aic info`

## Phase 3 — CLI completeness + optional daemon

- `aic daemon --stdio`
- Shared cache directory layout (even if unused)
- linux/amd64 binary for InstaBox

## Phase 4 — AiCode agent + light UI

- Kotlin tools in `feature/agent/domain/tool/android/`
- Register + prompts + docs-site + strings.xml
- Optional: chat card "Install APK" using existing send/install paths
- No full project-wizard UI required if `android_create_project` exists

## Phase 5 — Speed and deps

- Incremental hashes
- Maven-lite: POM + AAR explode + cache under `/root/.aicode/compiler/cache/maven`
- Still no Compose, no KSP

## Phase 6 — Remote release

- `engine: gradle` wrapper
- `linux_machine` recipe: sync tree, `./gradlew assembleRelease` or `aic build --release`
- GitHub Actions template
- Signing via credentials store (never in `aicode.json`)

## Explicitly out of order (do not sneak in)

- Porting `Jx`/`Ox`/`Ix` generators
- In-app ECJ
- Replacing `aicode` env command
- Kotlin local compiler
- Raising `targetSdk`

## Suggested first PR (when building starts)

`feat(compiler): add aic POSIX orchestrator and native-xml Hello APK`

Scope: Phase 1 only. No agent tools. No Go rewrite. No docs-site until the APK is real (internal `docs/aicore` already exists).
