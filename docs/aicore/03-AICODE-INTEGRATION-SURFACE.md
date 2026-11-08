# 03 — AiCode Integration Surface (verified)

This is what already exists. New compiler work must plug in here, not reinvent it.

## App shape

- Kotlin + Compose + Hilt + Coroutines/Flow
- Modules: `:app` / `:terminal-emulator` / `:terminal-view`
- Feature packages under `app/src/main/java/com/aicode/feature/`
- Agent tools: `feature/agent/domain/tool/`
- Container: `feature/agent/domain/container/`
- Workspace: `feature/workspace/`

Entry: `AIEditorApp` inits `FileLogger`, `TerminalKeepaliveService`, `McpManager`.

## Three execution backends (CommandEngine)

```
CommandEngine (interface)
 ├── LinuxContainerEngine     local PRoot
 ├── RemoteSshEngine          remote SSH
 └── DeviceRootEngine         real-device su (not for compiling)
DelegatingCommandEngine       picks by ExecutionMode
InstaboxTool (linux_machine)  separate cloud VM API, not CommandEngine
```

**Compiler runs in whichever Linux the user selected** (local PRoot or remote SSH). Heavy/release builds may also target InstaBox via `linux_machine`.

### Local PRoot (default)

`LinuxContainerEngine.buildBaseProotArgv`:

| Bind | Container path | Host path |
| --- | --- | --- |
| workspace | `/root/workspace` | `filesDir/projects/<currentWorkspace>` |
| AI config | `/root/.aicode` | `filesDir/aicode` (survives rootfs reset) |
| rootfs `/` | `/` | `filesDir/rootfs` or `filesDir/rootfs_<profileId>` |

Env (`buildContainerEnv`):

```
PATH=/root/.aicode/bin:/usr/bin:/bin:/usr/sbin:/sbin
HOME=/root
TMPDIR=/tmp
GIT_CONFIG_GLOBAL=/root/.aicode/.gitconfig
```

Working directory of commands: `/root/workspace`.

**Implication:** drop the `aic` binary, toolchain, templates, debug keystore into **`filesDir/aicode/`** so they appear at `/root/.aicode/` and sit on PATH. Use `ContainerInstaller.extractEnvTool` (copies `assets/aicode/bin` + `assets/aicode/lib` every launch, overwrite).

### Existing command named `aicode` — DO NOT TOUCH

`app/src/main/assets/aicode/bin/aicode` is a 7-line wrapper:

```sh
exec sh "$SELF_DIR/../provision.sh" --env
```

It opens the **environment installer** (JDK, Node, Android SDK, Flutter, …). Users and docs (`docs-site/docs/guide/container.md`) already teach `aicode` for that.

New compiler CLI = **`aic`**. Install as `assets/aicode/bin/aic`.

### Android SDK scenario already exists

- Script: `app/src/main/assets/aicode/lib/scenarios/android.sh`
- Shared: `app/src/main/assets/aicode/lib/android-sdk.sh`
- Docs: `docs-site/docs/advanced/build-android-app.md`

It installs **full** JDK 17 + SDK 36 + build-tools 35.0.0 + ARM64 aapt2 overlay, then expects **Gradle**.

AICore should:

- **Reuse** `JAVA_HOME`, `ANDROID_HOME`, and the ARM64 overlay when present (`aic doctor` detects them).
- **Not require** Gradle or the full SDK. Ship a minimal toolchain under `/root/.aicode/compiler/` for the "I just want to compile a small app" case.
- Keep the Gradle path as the documented escape hatch.

Alpine musl warning is already in `android.sh`. For AICore binaries: prefer **static aarch64** (like lzhiyong tools) so they run on Alpine **and** Debian. The Go orchestrator, if used, must be `CGO_ENABLED=0 GOARCH=arm64` (and amd64 for cloud).

## Workspace

`WorkspaceRepository`:

- Local projects: `context.filesDir/projects/<name>` (ext4, no storage permission, symlink-ok).
- Default workspace name: `"default"`.
- Container view: `~/workspace` (`WorkspacePathMapper.CONTAINER_ROOT`).
- File tools (`readFile` / `writeFile` / `editFile`) map container paths → host files **without entering PRoot**.
- Shell tool `Bash` (`ExecuteCommandTool`) **does** enter PRoot / SSH and cwd = current workspace.

So the agent can write `MainActivity.java` via `writeFile` and then `aic build` via `Bash` **today**, with no new tools. Dedicated `android_*` tools are for structured errors and not forcing the model to parse CLI output.

## Agent tool system (how to add compiler tools)

1. Subclass `AgentTool` in `feature/agent/domain/tool/...`
2. Set `name`, `description`, `parameters`, `capabilities`, `permissionPolicy`
3. Return `ToolResult.Success(json)` / `Error(message, code)`
4. Inject + `register("name", tool)` in `di/AgentModule.kt` `provideToolRegistry`
5. **Sync** `app/src/main/assets/prompts/` (asset sync hard rule)
6. If user-facing: `docs-site/docs/` + strings.xml both languages

Current registrations (order = schema order, models prefer the front):

```
readFile, sendFile, viewImage, writeFile, editFile,
loadSkill, generateImage,
Bash, Shizuku, terminal, list, search,
askUserQuestion, manageMcp, websearch, webfetch,
planMode, todo, memory, task, messageParent, browser,
github, githubWrite, linux_machine, renderLayout
```

Put new android tools **after file tools, before or near Bash** so the model actually calls them. Do not dump them at the end next to `renderLayout`.

### Capabilities to use

```
WRITE_WORKSPACE     create project, add dep
EXECUTE_COMMANDS    build, clean, install
READ_WORKSPACE      inspect, diagnostics
```

`permissionPolicy = ASK` for build/install/create. PLAN mode will auto-reject writes.

### Streaming

`ExecuteCommandTool` implements `StreamingAgentTool` so the chat sees live output. `aic build` should too — either:

- v1: agent calls `Bash` with `aic build --json` (streaming already works), or
- v1.5: `android_build` implements `StreamingAgentTool` and consumes daemon events.

## Cloud Linux (InstaBox)

Tool name: `linux_machine` (`InstaboxTool.kt`).

- Persistent Firecracker VM, Debian, home `/data/home`
- Actions: `create`, `run_command`, `files_read/write`, `download`, …
- Template `ci` exists — good for release builds
- Permission: ASK; needs API key in settings

**Phase 6 use:** upload project (or git push), `run_command` Gradle or `aic build --release` on the VM, `download` the APK. Do not design a new cloud API.

## Layout preview already exists

`renderLayout` turns `res/layout/*.xml` into PNG under `~/workspace/.aicode/previews/`. Compiler work should not duplicate this. Agent flow: write layout → `renderLayout` for UI check → `android_build` for APK.

## What "Linux local terminal box" means in practice

When the owner says "Linux local terminal box":

1. User opens Terminal tab → PRoot Alpine (or custom Debian image).
2. cwd is `/root/workspace`.
3. `aic` is on PATH because it lives in `/root/.aicode/bin`.
4. User types `aic init MyApp && cd MyApp && aic build`.
5. APK lands in `MyApp/build/outputs/apk/debug/app-debug.apk`, which is also `~/workspace/MyApp/...` for the agent and file picker.

No Android Service, no in-app compiler dialog required for v1. A later UI can call the same binary.

## Files an implementer will touch (AiCode side, later phases)

| Area | Files |
| --- | --- |
| Ship `aic` into container | `app/src/main/assets/aicode/bin/aic` (or a real binary next to it), `ContainerInstaller.extractEnvTool` |
| Toolchain assets | `app/src/main/assets/aicode/compiler/` → extracted to `filesDir/aicode/compiler` (extend extractEnvTool or add `extractCompiler`) |
| Agent tools | `feature/agent/domain/tool/android/*.kt`, `di/AgentModule.kt` |
| Prompts | `app/src/main/assets/prompts/` |
| Docs | `docs-site/docs/guide/` + `advanced/` |
| Optional UI | new feature `feature/androidbuild/` — **Phase 4 only** |

## Engine source location (recommendation)

Do **not** bury the compiler in `:app`. Keep it as a sibling tree the container runs:

```
Aicode/
  docs/aicore/          # this planning pack
  compiler/             # NEW — AICore sources (Go or sh)
    cmd/aic/
    internal/...
    testdata/hello/
    README.md
  app/src/main/assets/aicode/bin/aic   # built artifact copied here for shipping
```

CI (later): build `aic` for linux-arm64 and linux-amd64, drop into assets.

Until CI exists, commit a prebuilt or a POSIX prototype.
