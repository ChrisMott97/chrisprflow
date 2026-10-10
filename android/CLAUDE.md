# android/
Android dictation client. Plan: `docs/dictation-plan-d-android.md`. Real backend is the default; the token is entered in the app (Settings), not built in.
- JDK: none on PATH. Prefix Gradle with `JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Build: `./gradlew assembleDebug -q` (prints errors only)
- Test: `./gradlew testDebugUnitTest -q` (SilenceTrimmer, Splice)
- No `local.properties` in a fresh worktree: prefix Gradle with `ANDROID_HOME=$HOME/Library/Android/sdk` instead
- Test insertion without speaking (debug builds): focus a field on the phone, then
  `adb shell am broadcast -n app.dictation/.DebugInsertReceiver --es text "hello"` and read `adb logcat -d -s Dictation:I`
- `install -r` keeps the accessibility service enabled; it rebinds by itself after a few seconds
- Install: `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`
- Config: `android/local.properties` (git-ignored, never read it) holds `sdk.dir` and optionally `dictation.baseUrl`
