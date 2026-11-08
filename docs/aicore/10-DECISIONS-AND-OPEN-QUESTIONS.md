# 10 — Decisions and Open Questions

## Locked (do not relitigate without the owner)

| Decision | Choice |
| --- | --- |
| Compiler location | Linux box, not app JVM |
| Project location | Workspace directory, not `.sketchware` / not user storage |
| CLI name | `aic` |
| Existing `aicode` command | Untouched (env installer) |
| Install path | `/root/.aicode/bin/aic` via assets extract |
| v1 language | Java 17 |
| v1 UI toolkit | XML + framework `Activity` |
| v1 minSdk | >= 21 |
| v1 shrinking | none (no R8/ProGuard) |
| Local signing | debug.keystore only |
| Gradle | escape hatch / remote only, not default |
| Sketchware generators | not ported |
| Agent can use CLI first | yes; dedicated tools Phase 4 |
| Cloud | existing InstaBox `linux_machine`, no new API in v1 |
| Planning docs | `docs/aicore/` in the AiCode repo |

## Recommendations (default if owner silent)

| Topic | Recommendation |
| --- | --- |
| Engine language | POSIX sh for Phase 1 APK; Go rewrite once pipeline is proven |
| compileSdk | 34 (widely cached; 35/36 need matching android.jar) |
| Minimal SDK tarball | Ship `android.jar` + aarch64 aapt2/d8/zipalign/apksigner in assets if size allows; else require `aicode` Android scenario |
| AppCompat in v1 | No — Hello World on framework to prove the pipeline |
| Daemon | Not in first PR |
| Branch | `feat/aicore-compiler` when implementation starts (`CLAUDE.md` branch rules) |

## Open questions for the owner

1. **Engine language:** accept POSIX prototype first, or Go from day one?
2. **Minimal toolchain in APK assets:** ok to grow AiCode APK by ~30–80 MB (android.jar + static tools), or always download on first `aic doctor`?
3. **Default compileSdk / targetSdk** for templates (24/34 vs 26/35)?
4. **Should `aic init` also `git init`?** (workspace already has Git UI)
5. **Install on device:** v1 only produce APK (user installs via Files / `sendFile`), or hook Shizuku/`pm install` immediately?
6. **Alpine vs Debian:** is shipping musl-static tools a requirement so builtin Alpine works, or is "use Debian image" acceptable like current Android tutorial?
7. **Go/no-go on curated AndroidX in Phase 2** (appcompat/material) vs staying framework-only longer?

When answering, edit this file — do not leave decisions only in chat.

## ChatGPT convo vs this pack

`chatgptconvo.md` remains the original brainstorm. If it conflicts with `docs/aicore/`, **this folder wins** because it was checked against both codebases.
