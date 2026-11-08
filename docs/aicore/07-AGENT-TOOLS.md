# 07 — Agent Tools

v1 can ship **zero** new tools and still work: the agent uses `writeFile` + `Bash("aic build --json")`. Dedicated tools exist so the model gets **schema + structured errors** instead of log scraping.

## Tools to add (Phase 4, design frozen now)

Register in `AgentModule.provideToolRegistry` **immediately after file tools / loadSkill**, before `Bash`, so they get schema-front real estate.

| Name | Capabilities | Policy | Maps to |
| --- | --- | --- | --- |
| `android_create_project` | WRITE_WORKSPACE | ASK | `aic init` |
| `android_build` | EXECUTE_COMMANDS | ASK | `aic build --json` |
| `android_inspect_project` | READ_WORKSPACE | AUTO | `aic info` + tree |
| `android_diagnostics` | READ_WORKSPACE | AUTO | last build JSON |
| `android_add_dependency` | WRITE_WORKSPACE | ASK | edit `aicode.json` + later resolver |
| `android_install_apk` | EXECUTE_COMMANDS | ASK | `aic install` / `pm install` via Shizuku later |

### `android_create_project`

```json
{
  "name": "WeatherApp",
  "package": "com.example.weather",
  "template": "native-xml",
  "language": "java"
}
```

Returns container path `~/workspace/WeatherApp`.

### `android_build`

```json
{ "project": "WeatherApp", "variant": "debug" }
```

`project` optional: current workspace root or unique child with `aicode.json`.

Success:

```json
{
  "ok": true,
  "apk": "~/workspace/WeatherApp/build/outputs/apk/debug/app-debug.apk",
  "bytes": 123456,
  "ms": 8421
}
```

Failure: `ToolResult.Error` with message = human summary, and `data` still containing `errors[]` if we use Partial/Success with ok=false. Prefer **Success with `ok:false` + errors array** so the model keeps structured fields (Error type is just a string). Implementers: use `ToolResult.Success` for both outcomes; reserve `Error` for toolchain/crash.

### `android_inspect_project`

Returns package, sdk, sources list, manifest package, last apk if any.

## Prompt updates (mandatory with tools)

Under `app/src/main/assets/prompts/`:

- Add a short "Android projects in this workspace use `aicode.json` and `aic`. Prefer `android_build` over Gradle. If `aic` missing, tell the user to wait / run doctor. Do not invent `.sketchware` paths."
- Document tool names and params in the tools section of the system prompt (follow existing tool doc style).

Also update `docs-site/docs/guide/available-tools.md`.

## Implementation sketch (do not write production code yet)

```
AndroidBuildTool : AgentTool, StreamingAgentTool
  exec: commandEngine.run("aic build --json", cwd = projectDir)
  parse JSONL → stream ToolStreamEvent
  last object → ToolResult
```

Use `DelegatingCommandEngine` so SSH workspaces work. Never call host `Runtime.exec` for this.

Timeout: builds can exceed default 120s. Set 600s like InstaBox max, or match `ExecuteCommandTool.MAX_TIMEOUT_SECONDS` (1800). Document in the tool param.

## Permissions

Build can be surprising (CPU, heat). ASK in BUILD mode is correct. AUTO mode already bypasses ASK.

Do not add `MODIFY_CONTAINER_ENV`. Installing JDK/SDK is the existing `aicode` env menu / `Bash`.

## What the agent must not do

- `./gradlew` on an `engine: aicore` project unless the user asked or `aic` is missing.
- Write to `/storage/emulated/0/.sketchware`.
- Request release signing keys.
- Compile on `device` env (host Android). Always `container` (or InstaBox for release).
