# android/
Android dictation client. Plan: `docs/dictation-plan-d-android.md`. Real backend is the default; the token is entered in the app (Settings), not built in.
- JDK: none on PATH. Prefix Gradle with `JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Build: `./gradlew assembleDebug -q` (prints errors only)
- Test: `./gradlew testDebugUnitTest -q` (SilenceTrimmer)
- Install: `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`
- Config: `android/local.properties` (git-ignored, never read it) holds `sdk.dir` and optionally `dictation.baseUrl`
