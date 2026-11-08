# 05 — Build Pipeline

v1 target: **debug APK, Java, XML resources, minSdk >= 21, no R8, no Kotlin.**

## Stages

| # | ID | Tool | Inputs | Outputs | Skip if |
| --- | --- | --- | --- | --- | --- |
| 0 | `validate` | AICore | `aicode.json`, tree | resolved `BuildPlan` | never |
| 1 | `aapt2_compile` | `aapt2 compile --dir` | `app/src/main/res`, lib res | `build/intermediates/res/compiled/*.zip` | res+libs unchanged |
| 2 | `aapt2_link` | `aapt2 link` | compiled zips, manifest, assets, android.jar | `build/intermediates/res/resources.apk`, `build/gen/**/R.java` | compile+manifest+assets unchanged |
| 3 | `javac` | `javac` | java src + R.java + android.jar + lib jars | `build/intermediates/classes/` | java+R+libs unchanged |
| 4 | `d8` | `d8` | classes + lib jars (or lib dex) | `build/intermediates/dex/classes.dex` | classes unchanged |
| 5 | `pack` | zip merge | resources.apk + dex + `app/libs` native | `build/intermediates/apk/unsigned.apk` | always if 2–4 ran |
| 6 | `align` | `zipalign -p -f 4` | unsigned | `build/intermediates/apk/aligned.apk` | |
| 7 | `sign` | `apksigner sign` | aligned + debug.keystore | `build/outputs/apk/debug/app-debug.apk` | |
| 8 | `verify` | `apksigner verify` + size | signed apk | event `build_ok` | |

Phase 1 may run all stages every time. Cache keys below are for Phase 5.

## Stage 0 — validate

Must fail fast with structured errors:

- `aicode.json` present, `format` supported
- `package` is valid Java package
- `app/src/main/AndroidManifest.xml` exists
- `app/src/main/java` exists and contains at least one `.java`
- no `.kt` (v1)
- toolchain: `javac`, `aapt2`, `d8`, `zipalign`, `apksigner`, `android.jar` all executable/readable (`aic doctor` shares this)
- `minSdk >= 21` (v1 lock; lower needs MultiDex — out of scope)

## AAPT2 compile

Per resource directory:

```
aapt2 compile --dir <res> -o build/intermediates/res/compiled/<name>.zip
```

Order: libraries first, project last (so project overlays win at link with `--auto-add-overlay`).

## AAPT2 link

Copy Sketchware flags (verified):

```
aapt2 link
  --auto-add-overlay
  --no-version-vectors
  --no-version-transitions
  --min-sdk-version ${minSdk}
  --target-sdk-version ${targetSdk}
  --version-code ${versionCode}
  --version-name ${versionName}
  -I ${androidJar}
  -A app/src/main/assets          # if dir exists
  -R build/intermediates/res/compiled/*.zip
  --java build/gen
  --manifest app/src/main/AndroidManifest.xml
  --extra-packages ${libPackages} # if any lib has res
  -o build/intermediates/res/resources.apk
```

Optional later: `--proguard build/intermediates/aapt_rules.pro` (needed when R8 arrives).

`--allow-reserved-package-id` is Sketchware-specific for old support libs. Only add if a curated lib needs it.

## javac

```
javac -source 17 -target 17 -encoding UTF-8
  -bootclasspath ${androidJar}     # or -classpath android.jar and --release? 
  -cp ${androidJar}:${libJars}
  -d build/intermediates/classes
  $(find app/src/main/java build/gen -name '*.java')
```

Notes:

- Android `android.jar` is **not** a full JDK. Use it as classpath/bootclasspath, never as a runtime.
- `-proc:none` unless we add annotation processors (we will not in v1).
- Prefer generating a `sources.list` file to avoid argv limits.

Java version: lock **17** to match AiCode's documented JDK. Do not offer 1.7/1.8 knobs in v1 (Sketchware did; we don't need to).

Parse javac output into diagnostics:

```
file:line:column: error: message
```

Map to `AIC_JAVAC_*` codes.

## D8

```
d8 --min-api ${minSdk}
   --lib ${androidJar}
   --output build/intermediates/dex
   build/intermediates/classes
   ${maybeLibJars}
```

If a curated lib already has `classes.dex`, we can skip dexing that jar and add the dex at pack time (Sketchware does this). v1 with no libs: just project classes.

Output: `classes.dex` (and `classes2.dex` if we ever exceed 64K — then pack extra dex. minSdk>=21 allows this without MultiDex library).

## Pack

`resources.apk` is already a zip (manifest, `resources.arsc`, `res/`, assets). Create unsigned APK:

1. Copy `resources.apk` → `unsigned.apk`
2. Add `classes.dex` (and `classesN.dex`) at zip root
3. Add `lib/<abi>/*.so` from `app/src/main/jniLibs` if present
4. Do **not** compress `resources.arsc` or already-compressed res (aapt2 handled this). When injecting dex, use stored or deflate consistently; `zipalign` expects uncompressed `resources.arsc`.

Implementation: Python `zipfile`, Go `archive/zip`, or `zip -u`. Test that `aapt dump badging` still works after merge.

## Align + sign

```
zipalign -p -f 4 unsigned.apk aligned.apk
apksigner sign \
  --ks /root/.aicode/compiler/debug.keystore \
  --ks-pass pass:android \
  --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out app-debug.apk \
  aligned.apk
apksigner verify --verbose app-debug.apk
```

Generate debug.keystore once (standard Android debug DN) and ship it. Document that it is **not secure** and only for local install.

## Incremental cache (Phase 5, design now)

Per stage, hash:

```
configHash = hash(aicode.json relevant fields, tool versions)
inputHash  = hash of listed input files (content)
```

Store under `/root/.aicode/compiler/cache/<projectId>/<stage>/<hash>/`. If hit, copy artifacts. Invalidate downstream stages when an upstream hash changes.

Do not implement until a full build is green.

## Progress events (JSONL)

```json
{"event":"stage_start","stage":"javac","index":3,"total":8}
{"event":"stage_ok","stage":"javac","ms":412}
{"event":"build_error","stage":"javac","file":"app/src/main/java/com/example/app/MainActivity.java","line":24,"column":12,"message":"cannot find symbol","code":"AIC_JAVAC_001"}
{"event":"build_ok","apk":"build/outputs/apk/debug/app-debug.apk","bytes":123456,"ms":8421}
```

Human mode pretty-prints the same stream.

## Error codes (reserve now)

| Code | Meaning |
| --- | --- |
| `AIC_VALIDATE_*` | config/tree |
| `AIC_TOOLCHAIN_*` | missing binary / android.jar |
| `AIC_AAPT2_*` | resource compile/link |
| `AIC_JAVAC_*` | Java |
| `AIC_D8_*` | dex |
| `AIC_PACK_*` | zip |
| `AIC_SIGN_*` | align/sign/verify |
| `AIC_KOTLIN_UNSUPPORTED` | `.kt` present in v1 |
| `AIC_ENGINE_GRADLE` | project asked for gradle engine before implemented |

## Toolchain resolution order

`aic doctor` / build:

1. `$AICORE_HOME` if set, else `/root/.aicode/compiler`
2. `$ANDROID_HOME/build-tools/<best>` for aapt2, d8, zipalign, apksigner
3. `$ANDROID_HOME/platforms/android-<compileSdk>/android.jar`
4. `$JAVA_HOME/bin/javac` or `javac` on PATH
5. Fallback: `$AICORE_HOME/sdk/...` (minimal tarball we ship)

Print a table. Missing required tool → `AIC_TOOLCHAIN_MISSING` with install hint (`aicode` env menu Android scenario, or "AICore will unpack its minimal SDK").
