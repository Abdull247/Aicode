# 01 — Vision and Constraints

## What we are building

A **small, opinionated Android build engine** (working name **AICore**, CLI name **`aic`**) that:

1. Lives **inside the Linux environment** AiCode already owns (PRoot Alpine/Debian container, remote SSH, or InstaBox cloud VM).
2. Orchestrates real Android tools (AAPT2, javac/ECJ, D8, zipalign, apksigner) **without Gradle**.
3. Is callable three ways with the **same engine**:
   - Human in the terminal: `aic build`
   - Agent via structured tools: `android_build`
   - Optional JSON-RPC daemon: `aic daemon --stdio`
4. Stores projects as **portable directories in the workspace**, never in a hidden `.sketchware` tree on shared storage.

This is **not** a second Gradle. It is a controlled compiler for the apps people actually build on a phone: Java + XML + a small library set.

## Why this exists

Today AiCode can already compile Android apps **only if** the user installs JDK + full Android SDK + Gradle in a Debian/Ubuntu container (`docs-site/docs/advanced/build-android-app.md`). That path is heavy, slow, ARM64-fragile (Google's aapt2 is x86_64), and hostile to the agent (Gradle logs are unstructured).

Sketchware Pro already proved a Gradle-less pipeline on-device. We steal the **pipeline**, not the product: no block editor, no `.sketchware` paths, no obfuscated `a.a.a.*` classes, no user-storage project IDs.

## Non-goals (v1)

- Full Gradle / AGP compatibility.
- Arbitrary Maven/AAR universes (RecyclerView 1.4.0 + full transitive graph on day one).
- Local Kotlin compilation (Kotlin compiler distribution is huge). Kotlin = remote Gradle path until Phase 5+.
- Jetpack Compose local builds.
- Replacing AiCode's existing Gradle tutorial. That remains the escape hatch.
- Running the compiler inside the Android app process (no ECJ-in-app, no extracting aapt2 to `cacheDir` like Sketchware).
- Using `/storage/emulated/0` or any user-visible storage for compiler state.

## Hard constraints from AiCode (verified)

| Constraint | Why | Source |
| --- | --- | --- |
| `targetSdk = 28`, `minSdk = 26` | PRoot must execute binaries from app-writable dirs; Android 10+ W^X / SELinux forbids it | `CLAUDE.md` |
| Compiler must not live in the app JVM | App is the controller; Linux box owns toolchains | owner request + ChatGPT (correct) |
| Projects live in the Linux workspace | Host: `filesDir/projects/<name>`; container: `/root/workspace` via PRoot `-b` | `WorkspaceRepository`, `LinuxContainerEngine` |
| Do not collide with existing `aicode` command | That binary is already `provision.sh --env` (dev-env installer) | `app/src/main/assets/aicode/bin/aicode` |
| Agent tools register in `AgentModule` + `ToolRegistry` | Order = schema order; prompts in `assets/prompts/` must stay in sync | `CLAUDE.md` asset sync |
| Bilingual strings.xml for any UI | No hardcoded Chinese in `.kt` | `CLAUDE.md` |
| PLAN mode must reject writes | New build tools are write/execute; policy engine already handles this if capabilities are set | `ToolPermissionPolicyEngine` |
| Alpine musl is a bad Android compile host | Official docs already warn this; Debian/Ubuntu custom image is recommended | `docs-site/docs/advanced/build-android-app.md`, `scenarios/android.sh` |

## What ChatGPT got right (keep)

From `chatgptconvo.md` — these survive contact with the codebases:

- Gradle is orchestration, not the compiler. Replace orchestration; keep AAPT2 / javac / D8 / android.jar / zipalign / apksigner.
- Compiler is a **standalone Linux executable**, not an Android library.
- Project format is a self-contained directory with `aicode.json`. Version the format.
- Staged pipeline with incremental cache (hash inputs per stage).
- CLI for humans + structured JSON for the agent. Daemon/JSON-RPC is a good Phase 3+, not Phase 1.
- Do not recreate Maven in v1. Curated dependency registry first.
- Dual build: local lightweight debug vs remote Gradle release.
- Don't tie the engine to Sketchware-style generated sources. Compile ordinary Java/XML Android trees.

## What ChatGPT got wrong or incomplete (fix)

| ChatGPT claim | Reality |
| --- | --- |
| Invent `/usr/local/bin/aic` in a vacuum | Container PATH is `/root/.aicode/bin:/usr/bin:/bin:...`. Install `aic` at `/root/.aicode/bin/aic` via `ContainerInstaller.extractEnvTool` (same mechanism as the existing `aicode` env tool). |
| Name the CLI `aic` vs existing `aicode` | **Correct luck.** Existing command is `aicode` (env installer). New compiler CLI **must** be `aic` (or `aicore`). Never overwrite `aicode`. |
| "4 GB RAM cloud Linux" as a vague idea | Already exists as **InstaBox** tool `linux_machine` (`InstaboxTool.kt`): Firecracker VM, `/data/home`, Debian, `run_command` / `files_*`. Use it for release/heavy builds. Do not invent a second cloud API in v1. |
| Go is the default language | Reasonable, but **not locked**. Engine can be Go, or a POSIX shell orchestrator wrapping binaries, or a small Java/ECJ runner **inside the container**. See open questions. Recommendation: **Go orchestrator** if we can ship a static aarch64 binary; otherwise a **Python or POSIX orchestrator** is faster to land because the container already has those after provision. |
| Templates under `workspace/.aicode/templates` | Shared compiler assets should live in **`/root/.aicode/compiler/`** (host `filesDir/aicode/compiler/`, already bind-mounted, survives rootfs reset). Per-project config stays in the project dir. |
| Agent tools named `android_*` | Fine. Register in `AgentModule.provideToolRegistry`. Also add prompt files. Do **not** make the agent shell out to `aic` as its only interface long-term — wrap JSON. v1 may shell out. |
| Sketchware stores under `.sketchware` | Confirmed. `wq.java` uses `Environment.getExternalStorageDirectory() + "/.sketchware/mysc/<sc_id>"`. We **must not** copy this. |
| Incremental compilation "from the beginning" | Correct as a design, but Phase 1 can be always-full-build. Don't block first APK on a cache manager. |

## Product principles (do not violate)

1. **Opinionated, not universal.** If a project needs Compose, KSP, or 40 AARs, send it to Gradle remotely.
2. **One engine, three faces.** CLI, agent tools, later UI all call AICore. Never fork the pipeline.
3. **Workspace is the source of truth.** Project files are normal files the agent already knows how to edit.
4. **Structured diagnostics.** Every compiler error is JSON the agent can act on. Human CLI pretty-prints the same events.
5. **Debug locally, release remotely.** Local = debug keystore, no R8 required. Release signing keys never enter agent context.
6. **Do not build a second Gradle.**
