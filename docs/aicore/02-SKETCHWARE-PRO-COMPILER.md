# 02 — Sketchware Pro Compiler (verified from source)

Clone used: `/workspace/Sketchware-Pro` (github.com/Sketchware-Pro/Sketchware-Pro, shallow).
Do not re-read the whole tree. This file is the map.

## Mental model

Sketchware Pro is **not** a compiler. It is:

1. A **code generator** (blocks → Java/XML/Manifest).
2. A **path manager** (`yq`) that lays out a fake Gradle-like tree under `.sketchware/mysc/<sc_id>/`.
3. A **build orchestrator** (`ProjectBuilder`) that shells/invokes AAPT2, ECJ, D8/R8, ApkBuilder, zipalign, apksigner.

AiCode already has (1) via the agent writing files, and does not want (2)'s storage model. We only want (3), rewritten as a Linux CLI.

## Source map (from their README + code)

| Class | Role | Path |
| --- | --- | --- |
| `a.a.a.ProjectBuilder` | Orchestrator | `app/src/main/java/a/a/a/ProjectBuilder.java` (~1101 lines) |
| `a.a.a.yq` | All project paths + metadata | `app/src/main/java/a/a/a/yq.java` (~1039 lines) |
| `a.a.a.wq` | Global `.sketchware` / `sketchware` path constants on external storage | `app/src/main/java/a/a/a/wq.java` |
| `a.a.a.Ix` | AndroidManifest.xml generator | `app/src/main/java/a/a/a/Ix.java` |
| `a.a.a.Jx` | Activity source generator | `app/src/main/java/a/a/a/Jx.java` |
| `a.a.a.Lx` | Component/listener source generator | `app/src/main/java/a/a/a/Lx.java` |
| `a.a.a.Ox` | Layout XML generator | `app/src/main/java/a/a/a/Ox.java` |
| `a.a.a.qq` | Built-in library dependency registry | (legacy) |
| `mod.jbk.build.compiler.resource.ResourceCompiler` | AAPT2 compile + link | `app/src/main/java/mod/jbk/build/compiler/resource/ResourceCompiler.java` |
| `mod.jbk.build.compiler.dex.DexCompiler` | D8 API | `app/src/main/java/mod/jbk/build/compiler/dex/DexCompiler.java` |
| `mod.jbk.build.BuiltInLibraries` | Bundled AARs/JARs/DEX extracted to `filesDir/libs` | `app/src/main/java/mod/jbk/build/BuiltInLibraries.java` |
| `mod.hey.studios.compiler.kotlin.KotlinCompilerBridge` | Optional kotlinc before javac | `.../kotlin/KotlinCompilerBridge.java` |
| `mod.pranav.build.R8Compiler` | Optional shrinking | `.../build/R8Compiler.kt` |
| `mod.jbk.util.TestkeySignBridge` | Debug sign via apksigner + testkey | `.../util/TestkeySignBridge.java` |
| `mod.alucard.tn.apksigner.ApkSigner` | Actual signer | `.../apksigner/ApkSigner.java` |
| `com.android.sdklib.build.ApkBuilder` | Pack resources APK + DEX + libs into unsigned APK | used in `ProjectBuilder.buildApk` |
| `com.iyxan23.zipalignjava.ZipAlign` | Java zipalign | `ProjectBuilder` |
| `DesignActivity` | **Canonical pipeline order** | `app/src/main/java/com/besome/sketch/design/DesignActivity.java` ~1161–1237 |

Generators (`Ix/Jx/Lx/Ox`) are Sketchware-specific. **Do not port them.** AiCode's agent writes source. Port only orchestration + resource/dex/apk stages.

## Storage model we are abandoning

From `wq.java` + `yq.java`:

```
/storage/emulated/0/.sketchware/mysc/<sc_id>/          # generated Android tree
    app/src/main/java|res|assets|AndroidManifest.xml
    bin/                                               # classes, dex, unsigned apk
    gen/                                               # R.java
/storage/emulated/0/.sketchware/data/<sc_id>/          # editor project data (blocks)
/storage/emulated/0/sketchware/signed_apk/             # release APKs
/storage/emulated/0/sketchware/keystore/               # release keystore
```

`sc_id` is a numeric project id. Everything is on **shared external storage**. AiCode must use the Linux workspace instead (`/root/workspace/<Project>`).

## Canonical build order (DesignActivity)

This is the pipeline to reimplement. Line numbers from `DesignActivity.java`:

1. **Generate sources** (Sketchware-only — skip). `q.a(...)`, `q.b(...)`, `q.f()`, `q.e()` write Java/XML/Manifest into `yq` paths.
2. `builder.buildBuiltInLibraryInformation()` — decide which bundled libs are on (AppCompat, Material, Firebase, Gson, Glide, OkHttp, Kotlin stdlib if `.kt` present).
3. `builder.maybeExtractAapt2()` — copy `assets/aapt/aapt2-$ABI` → `context.cacheDir/aapt2`, `chmod +x`. ABI = `Build.SUPPORTED_ABIS[0]`.
4. `BuiltInLibraries.extractCompileAssets(...)` — extract `android.jar`, library JARs/res/dex into `filesDir/libs`.
5. `builder.compileResources()` → `ResourceCompiler` → AAPT2.
6. `builder.generateViewBinding()` — optional, ProjectSettings flag.
7. `KotlinCompilerBridge.compileKotlinCodeIfPossible` — if any `.kt`.
8. `builder.compileJavaCode()` — Eclipse ECJ batch compiler.
9. Optional StringFog, then ProGuard/R8 (`ProguardHandler`).
10. `builder.createDexFilesFromClasses()` — D8 (skipped if R8 already emitted DEX).
11. `builder.getDexFilesReady()` — merge DEX (dx DexMerger) if minSdk < 21 or release; else keep multi-dex list.
12. `builder.buildApk()` — `ApkBuilder(resources.apk, classes.dex)` + extra dex + native libs + jar resources.
13. `builder.signDebugApk()` — testkey via apksigner.
14. Install.

Export path (`ExportProjectActivity`) is the same minus install, plus optional zipalign + release sign.

## Stage details we must reproduce

### AAPT2 (ResourceCompiler.Aapt2Compiler)

**compile** (per res dir → zip of compiled resources):

```
aapt2 compile --dir <resDir> -o <out>/<name>.zip
```

They compile, in order: built-in library res (cached in `cacheDir/compiledLibs/<lib>.zip`), local library res, project res (`project.zip`), imported res (`project-imported.zip`).

**link**:

```
aapt2 link
  [--proto-format]          # only for AAB
  --allow-reserved-package-id
  --auto-add-overlay
  --no-version-vectors
  --no-version-transitions
  --min-sdk-version N
  --target-sdk-version N
  --version-code N
  --version-name S
  -I <android.jar>
  -A <assetsDir>            # repeatable
  -R <compiled.zip>         # repeatable, libraries then project
  --java <genDir>           # emits R.java
  --proguard <aapt_rules.pro>
  --manifest AndroidManifest.xml
  --extra-packages <lib.packages>   # if libs have resources
  -o <resources.apk>
```

This is the exact flag set to start from. `--proto-format` is AAB-only; skip in v1.

### Java (ECJ)

`org.eclipse.jdt.internal.compiler.batch.Main`:

```
-1.7 (or project Java version)
-nowarn
[-deprecation]
-d <bin/classes>
-cp <classpath>
-proc:none
<javaFilesPath>
<rJavaDirectoryPath>
[+ extra java/broadcast/service dirs]
```

Classpath composition (`getClasspath()`):

1. `bin/classes` if Kotlin already compiled there
2. `android.jar` (default extracted compile asset, overridable)
3. `org.apache.http.legacy` unless disabled
4. MultiDex if minSdk < 21
5. `core-lambda-stubs.jar` if Java > 1.7
6. Each enabled built-in library `classes.jar`
7. Local library JARs
8. User extra classpath
9. Project `files/classpath/*.jar`

**For AiCode v1:** android.jar + generated R.java + project sources + a curated lib set. No HTTP legacy, no MultiDex if we lock minSdk >= 21.

ECJ is used because Sketchware runs **on Android** (no full JDK). **In the Linux box we have a real JDK.** Prefer **javac** from OpenJDK 17. Keep ECJ as a fallback only if we ever compile inside the app process (we should not).

### D8

`DexCompiler.compileDexFiles` uses R8's D8 API in-process:

```
D8Command.builder()
  .setMode(RELEASE)
  .setIntermediate(true)
  .setMinApiLevel(minSdk)
  .addLibraryFiles(classpath jars)
  .setOutput(bin/dex, DexIndexed)
  .addProgramFiles(all .class files or proguarded jar)
```

**In the Linux box:** call the `d8` binary from build-tools (already part of Android SDK / lzhiyong aarch64 static tools), not the Java API. Example:

```
d8 --min-api 21 --lib android.jar --output build/dex/ [--intermediate] <classes or jar>
```

If R8 shrinking is on, Sketchware skips D8 and uses `R8Compiler`. **v1: no R8.** Debug APKs only.

### APK pack

`com.android.sdklib.build.ApkBuilder`:

- Inputs: `resources.apk` (from aapt2 link), first `classes.dex`
- Adds: library jar resources, native libs (`lib/<abi>/*.so`), extra `classesN.dex`
- Output: unsigned unaligned APK

**In Linux:** `aapt2 link` already produced a resources APK. Then either:

- `aapt add` / custom zip merge of dex + libs, or
- Python/Go zip writer that copies resources.apk and injects `classes.dex`, `lib/**`, extra dex.

Do not depend on `sdklib` Java. A zip merge is enough.

### Align + sign

- Sketchware debug path: **sign without zipalign first** (`signDebugApk` reads `unsignedUnalignedApkPath`). Export path zipaligns.
- Signer: `mod.alucard.tn.apksigner.ApkSigner.signWithTestKey` (embedded testkey, same as AOSP testkey; repo also has `testkey.keystore` at Sketchware root).
- Fallback: kellinwood zipsigner.

**In Linux:** always `zipalign -p -f 4 in.apk aligned.apk` then `apksigner sign --ks debug.keystore --ks-pass pass:android aligned.apk`. Ship a generated debug keystore in `/root/.aicode/compiler/debug.keystore` (standard Android debug cert). Never use a release key locally in v1.

## Built-in libraries (do not copy the whole set)

`BuiltInLibraries.java` lists dozens of AndroidX + Material + Firebase + Play Services + Glide + Gson + OkHttp, extracted from app assets to `filesDir/libs`.

This is Sketchware's substitute for Gradle deps: **pre-extracted AAR contents** (res + classes.jar + classes.dex + assets + proguard.txt).

For AiCode v1:

- Start with **no AndroidX** (framework-only Hello World).
- Phase 2: a **curated registry** of a handful of AARs we vendor ourselves under `/root/.aicode/compiler/libs/` (e.g. `androidx.appcompat`, `material`, `recyclerview`, `constraintlayout`). Store already-exploded AAR layout like Sketchware, or explode at first use.
- Do not download Maven at compile time in v1.

## Kotlin in Sketchware

`KotlinCompilerBridge`: if any `.kt` exists, run their `KotlinCompiler`, add `kotlin-stdlib` to built-in libs, put `bin/classes` on javac classpath so Java can see Kotlin.

**AiCode v1: Java only.** If `.kt` files are present, `aic build` should fail with a clear error: use remote Gradle. Do not silently skip.

## What is worth copying vs rewriting

| Copy the idea | Rewrite | Discard |
| --- | --- | --- |
| Stage order | Path layout (`yq` / `wq`) | Block-to-Java generators |
| AAPT2 compile+link flags | In-process ECJ/D8 Java APIs → CLI binaries | `.sketchware` on external storage |
| android.jar as -I / -cp | Built-in lib extraction from APK assets | StringFog, quizzes (`tq`) |
| Multi-stage progress events | Java ApkBuilder → zip merge | `sc_id` project IDs |
| Debug testkey signing | | Firebase/Crashlytics in the builder |
| Curated exploded AARs | | Running aapt2 from Android cacheDir |

## ABI / binary note

Sketchware ships `assets/aapt/aapt2-<abi>` and extracts per device ABI. AiCode's Linux box is typically **aarch64** (phone) or **x86_64** (cloud VM). Google build-tools binaries are **x86_64**. AiCode already solves this in `android-sdk.sh` by overlaying [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools) static aarch64 builds. **AICore should reuse that overlay**, not ship a third aapt2.

Recommended toolchain source for the compiler:

- Prefer tools already at `$ANDROID_HOME/build-tools/<ver>/` if `aic doctor` finds them.
- Else a **minimal AICore toolchain tarball** (aapt2, d8/lib, zipalign, apksigner, android.jar for one compileSdk) unpacked to `/root/.aicode/compiler/sdk/`. Much smaller than full SDK.
