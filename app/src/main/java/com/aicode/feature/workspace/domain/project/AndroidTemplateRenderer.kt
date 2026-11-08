package com.aicode.feature.workspace.domain.project

/**
 * 把 [AndroidProjectSpec] 填进 native-xml 骨架，返回「相对工作区根的路径 → 文件全文」。
 * 纯函数，不碰磁盘，便于单测。
 */
object AndroidTemplateRenderer {

    private val PACKAGE_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

    fun isValidPackage(packageName: String): Boolean = PACKAGE_REGEX.matches(packageName)

    fun packagePath(packageName: String): String = packageName.replace('.', '/')

    fun suggestPackage(appName: String): String {
        val seg = appName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "")
            .ifBlank { "app" }
            .let { if (it.first().isDigit()) "app$it" else it }
            .take(24)
        return "com.example.$seg"
    }

    fun toColorHex(color: Int): String {
        val alpha = (color ushr 24) and 0xFF
        val rgb = color and 0x00FFFFFF
        return if (alpha == 0xFF) {
            String.format("#%06X", rgb)
        } else {
            String.format("#%08X", color)
        }
    }

    fun parseColorHex(raw: String): Int? {
        val t = raw.trim().removePrefix("#")
        val value = t.toLongOrNull(16) ?: return null
        return when (t.length) {
            6 -> (0xFF000000L or value).toInt()
            8 -> value.toInt()
            else -> null
        }
    }

    fun render(spec: AndroidProjectSpec): Map<String, String> {
        require(isValidPackage(spec.packageName)) { "invalid package: ${spec.packageName}" }
        val pkgPath = packagePath(spec.packageName)
        val vars = mapOf(
            "{{PACKAGE}}" to spec.packageName,
            "{{PACKAGE_PATH}}" to pkgPath,
            "{{APP_NAME}}" to spec.appName,
            "{{PROJECT_NAME}}" to spec.projectName,
            "{{MIN_SDK}}" to spec.minSdk.toString(),
            "{{TARGET_SDK}}" to spec.targetSdk.toString(),
            "{{COMPILE_SDK}}" to spec.compileSdk.toString(),
            "{{VERSION_CODE}}" to spec.versionCode.toString(),
            "{{VERSION_NAME}}" to spec.versionName,
            "{{COLOR_ACCENT}}" to toColorHex(spec.colorAccent),
            "{{COLOR_PRIMARY}}" to toColorHex(spec.colorPrimary),
            "{{COLOR_PRIMARY_DARK}}" to toColorHex(spec.colorPrimaryDark),
            "{{COLOR_HIGHLIGHT}}" to toColorHex(spec.colorControlHighlight),
            "{{COLOR_NORMAL}}" to toColorHex(spec.colorControlNormal)
        )
        return NativeXmlTemplate.files.map { (path, content) ->
            var outPath = path
            var outContent = content
            for ((key, value) in vars) {
                outPath = outPath.replace(key, value)
                outContent = outContent.replace(key, value)
            }
            outPath to outContent
        }.toMap()
    }
}

private object NativeXmlTemplate {
    val files: Map<String, String> = mapOf(
        "aicode.json" to """
            {
              "format": 1,
              "name": "{{PROJECT_NAME}}",
              "package": "{{PACKAGE}}",
              "version": { "code": {{VERSION_CODE}}, "name": "{{VERSION_NAME}}" },
              "platform": {
                "minSdk": {{MIN_SDK}},
                "targetSdk": {{TARGET_SDK}},
                "compileSdk": {{COMPILE_SDK}}
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
        """.trimIndent() + "\n",
        ".gitignore" to """
            build/
            .aicode/
            local.properties
        """.trimIndent() + "\n",
        "app/src/main/AndroidManifest.xml" to """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="{{PACKAGE}}">

                <uses-sdk
                    android:minSdkVersion="{{MIN_SDK}}"
                    android:targetSdkVersion="{{TARGET_SDK}}" />

                <application
                    android:allowBackup="true"
                    android:label="@string/app_name"
                    android:theme="@style/AppTheme">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent() + "\n",
        "app/src/main/java/{{PACKAGE_PATH}}/MainActivity.java" to """
            package {{PACKAGE}};

            import android.app.Activity;
            import android.os.Bundle;

            public class MainActivity extends Activity {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setContentView(R.layout.activity_main);
                }
            }
        """.trimIndent() + "\n",
        "app/src/main/res/layout/activity_main.xml" to """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:background="@color/colorPrimary"
                android:gravity="center"
                android:orientation="vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/hello_message"
                    android:textColor="@android:color/white"
                    android:textSize="20sp" />
            </LinearLayout>
        """.trimIndent() + "\n",
        "app/src/main/res/values/strings.xml" to """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">{{APP_NAME}}</string>
                <string name="hello_message">Hello, {{APP_NAME}}</string>
            </resources>
        """.trimIndent() + "\n",
        "app/src/main/res/values/colors.xml" to """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="colorAccent">{{COLOR_ACCENT}}</color>
                <color name="colorPrimary">{{COLOR_PRIMARY}}</color>
                <color name="colorPrimaryDark">{{COLOR_PRIMARY_DARK}}</color>
                <color name="colorControlHighlight">{{COLOR_HIGHLIGHT}}</color>
                <color name="colorControlNormal">{{COLOR_NORMAL}}</color>
            </resources>
        """.trimIndent() + "\n",
        "app/src/main/res/values/themes.xml" to """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <style name="AppTheme" parent="@android:style/Theme.DeviceDefault.Light.DarkActionBar">
                    <item name="android:colorAccent">@color/colorAccent</item>
                    <item name="android:colorPrimary">@color/colorPrimary</item>
                    <item name="android:colorPrimaryDark">@color/colorPrimaryDark</item>
                    <item name="android:statusBarColor">@color/colorPrimaryDark</item>
                    <item name="android:navigationBarColor">@color/colorPrimaryDark</item>
                </style>
            </resources>
        """.trimIndent() + "\n"
    )
}
