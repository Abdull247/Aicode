# 08 — Project Format

## Layout

```
MyFirstApp/
  aicode.json                 # required, format versioned
  app/
    src/main/
      AndroidManifest.xml
      java/<package>/...
      res/layout|values|drawable|mipmap/...
      assets/                 # optional
      jniLibs/<abi>/*.so      # optional
    libs/*.jar                # optional local jars
  build/                      # generated, gitignore
```

This is a **normal Android-ish tree** without Gradle files. A later `aic export-gradle` (not v1) can emit `build.gradle.kts` for Android Studio.

## `aicode.json` format 1

```json
{
  "format": 1,
  "name": "MyFirstApp",
  "package": "com.example.app",
  "version": { "code": 1, "name": "1.0.0" },
  "platform": {
    "minSdk": 24,
    "targetSdk": 34,
    "compileSdk": 34
  },
  "compiler": {
    "engine": "aicore",
    "language": "java",
    "java": 17,
    "template": "native-xml"
  },
  "dependencies": [],
  "build": { "type": "debug" }
}
```

Rules:

- Unknown fields ignored (forward compat).
- `format` > supported → hard error `AIC_VALIDATE_FORMAT`.
- `engine: "gradle"` reserved.
- `language: "kotlin"` → `AIC_KOTLIN_UNSUPPORTED` in v1.
- `dependencies` v1: only `{ "name": "appcompat" }` from the curated registry, or `{ "jar": "app/libs/foo.jar" }`. No Maven coordinates until Phase 5.

## Templates

Shipped in `/root/.aicode/compiler/templates/<id>/`:

```
template.json       # id, title, language, files to copy, placeholders
project/            # skeleton
```

Placeholders: `{{PACKAGE}}`, `{{PACKAGE_PATH}}`, `{{APP_NAME}}`, `{{MIN_SDK}}`, `{{TARGET_SDK}}`, `{{COMPILE_SDK}}`.

### v1 template: `native-xml`

- `MainActivity.java` extends `Activity` (not AppCompat, so no AndroidX required)
- `res/layout/activity_main.xml` with a `TextView`
- `res/values/strings.xml`, `themes.xml` (framework themes)
- `AndroidManifest.xml` with that activity as launcher
- `res/mipmap-mdpi/ic_launcher.png` (one density is enough for debug)

### Later templates (Phase 5)

| id | Needs |
| --- | --- |
| `material` | appcompat + material AARs |
| `empty` | no activity, library-like |
| `gradle-kotlin` | remote engine only |

Do **not** add a Sketchware-blocks template. The agent writes Java.

## Gitignore snippet (write on `aic init`)

```
build/
.aicode/
local.properties
```

## Mapping vs Sketchware `yq`

| Sketchware `yq` | AICore |
| --- | --- |
| `projectMyscPath` | project root |
| `javaFilesPath` | `app/src/main/java` |
| `resDirectoryPath` | `app/src/main/res` |
| `androidManifestPath` | `app/src/main/AndroidManifest.xml` |
| `assetsPath` | `app/src/main/assets` |
| `rJavaDirectoryPath` | `build/gen` |
| `compiledClassesPath` | `build/intermediates/classes` |
| `binDirectoryPath` | `build/intermediates` |
| `resourcesApkPath` | `build/intermediates/res/resources.apk` |
| `classesDexPath` | `build/intermediates/dex/classes.dex` |
| `finalToInstallApkPath` | `build/outputs/apk/debug/app-debug.apk` |
| `sc_id` | **none** — directory name is the id |
