# android/
Android dictation client. Plan: `docs/dictation-plan-d-android.md`. Talk to the mock backend until A1 is done.
- JDK: none on PATH. Prefix Gradle with `JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Build: `./gradlew assembleDebug -q` (prints errors only)
- Test: no tests yet (D1)
- Install: `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`
- Config: `android/local.properties` (git-ignored, never read it) holds `sdk.dir`, `dictation.baseUrl`, `dictation.token`
