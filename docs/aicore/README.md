# AICore — Custom Android Compiler for AiCode

**Status:** Phase 1 implemented (POSIX `aic` orchestrator + backend toolchain registry).
**Last updated:** 2026-11-08
**Audience:** any human or agent picking this up cold.

This folder is the handoff package for building a **lightweight, Sketchware-Pro-inspired Android compiler** that lives in AiCode's **Linux box** (PRoot container / remote SSH / InstaBox cloud), not on the user's phone storage and not inside the Android app JVM.

## Read in this order

| # | File | What it is |
| --- | --- | --- |
| 0 | [README.md](README.md) | This index |
| 1 | [01-VISION-AND-CONSTRAINTS.md](01-VISION-AND-CONSTRAINTS.md) | Goal, non-goals, hard constraints, what ChatGPT got right/wrong |
| 2 | [02-SKETCHWARE-PRO-COMPILER.md](02-SKETCHWARE-PRO-COMPILER.md) | Verified Sketchware Pro compiler pipeline (from source) |
| 3 | [03-AICODE-INTEGRATION-SURFACE.md](03-AICODE-INTEGRATION-SURFACE.md) | Verified AiCode Linux box, tools, workspace, existing `aicode` CLI |
| 4 | [04-ARCHITECTURE.md](04-ARCHITECTURE.md) | Target architecture: AICore engine + `aic` CLI + agent tools |
| 5 | [05-BUILD-PIPELINE.md](05-BUILD-PIPELINE.md) | Exact compile stages, tools, inputs/outputs, cache keys |
| 6 | [06-CLI-AND-DAEMON.md](06-CLI-AND-DAEMON.md) | `aic` command surface + JSON-RPC daemon protocol |
| 7 | [07-AGENT-TOOLS.md](07-AGENT-TOOLS.md) | New agent tools, registration, prompts, permissions |
| 8 | [08-PROJECT-FORMAT.md](08-PROJECT-FORMAT.md) | `aicode.json`, templates, layout on disk |
| 9 | [09-IMPLEMENTATION-ROADMAP.md](09-IMPLEMENTATION-ROADMAP.md) | Phased build plan, first files to create, acceptance tests |
| 10 | [10-DECISIONS-AND-OPEN-QUESTIONS.md](10-DECISIONS-AND-OPEN-QUESTIONS.md) | Locked decisions vs questions for the owner |

## Companion sources (do not rediscover)

- AiCode conversation that started this: `chatgptconvo.md` (repo root). ChatGPT had **no codebase access**. Treat it as a design sketch, not ground truth. Corrections are in `01-VISION-AND-CONSTRAINTS.md`.
- Sketchware Pro clone (sibling of this repo on the planning machine): `/workspace/Sketchware-Pro`. Key files listed in `02-SKETCHWARE-PRO-COMPILER.md`.
- AiCode architecture bible: `CLAUDE.md` (repo root). Feature map, tool registry, container, `targetSdk = 28`.

## One-sentence product

A user (or the agent) says "build me a weather app"; AiCode creates a self-contained Java/XML Android project **inside the Linux workspace**, the custom compiler (`aic`) produces a debug APK without Gradle, structured errors go back to the agent, and the APK is installable. Release / Kotlin / heavy deps go to cloud Linux or GitHub Actions with Gradle as a fallback — not as the default local path.
