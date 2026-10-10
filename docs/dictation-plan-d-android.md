---
type: topic
subject: Dictation tool build plan - workstream D, the Android client
author: claude
reviewed:
sources:
  - "[[topics/personal-voice-dictation-tool]]"
  - "conversation: 'dictation tool implementation plan', Oct 2026"
last_updated: 2026-10-10
---

# Workstream D: Android client

**What this is.** The plan for the Android app, built in four stages where each is a superset of
the last. Four sessions in a chain: D1 to D4.
**Read first.** `dictation-plan-contract.md`. Nothing else is needed.

- D1 is a plain app: record, upload, copy to clipboard.
- D2 adds a Quick Settings tile and silence trimming, and switches to the real backend.
- D3 adds a floating bubble. D4 inserts the text directly into the focused field.
- The folder is `android/`. Kotlin, one module. The only target is Chris's phone: a Samsung
  Galaxy S24+ on Android 16. Set `minSdk` to 34 and do not spend effort on older versions.

These sessions must run in Claude Code on the Mac with the phone attached by USB.

## Before D1, Chris does

- Install Android Studio. Switch on developer options and USB debugging on the phone.
- Confirm `adb devices` lists the phone.

## Session D1: standalone app

**Build**

1. One screen: a record button, a status line, the last result, a Copy button.
2. Record 16 kHz mono 16-bit PCM and wrap it as WAV. Discard anything under 0.5 seconds.
3. `POST /v1/dictate` as in the contract, with `X-Client: android`. The base URL and token come
   from `local.properties`, which git ignores. Use the mock URL and `mock-token`.
4. If `empty` is true, say so. Otherwise show `text` and copy it to the clipboard.
5. On a retryable error, keep the WAV in app storage and show the error. Retrying comes in D2.

**Done when** the app is installed on the phone through `adb`, and tapping record, speaking and
tapping stop puts "This is a mock dictation." on the clipboard.

## Session D2: tile, trimming, real backend

**Waits on:** D1, and A1 deployed.

**Build**

1. **Recording moves into a foreground service** of the microphone type, with its notification.
2. **A Quick Settings tile** that starts and stops that service. The result goes to the clipboard
   and a notification.
3. **VAD** using Silero VAD on the device, run on the finished recording. Same rule as the Mac:
   cut every silence over 1 second to 0.3 seconds, join into one WAV. Check the current supported
   way to run Silero on Android before choosing a library.
4. A 10 minute cap on a recording.
5. Retry of saved recordings on the next successful request.
6. Switch to the real URL and the Android token. Store the token encrypted on the device, entered
   on a settings screen.

**Watch for.** Android restricts starting a microphone foreground service when the app is not
visible. Starting it from a tile tap is expected to be allowed, but confirm it on the phone on
Android 16 first, before building the rest. If it is refused, report it and stop.

**Done when** the tile works from the home screen with the app closed, and a recording
with a ten second pause comes back clean through the real backend.

## Session D3: overlay bubble

**Waits on:** D2. Chris grants "Appear on top" for the app when asked.

**Build**

1. A small draggable bubble drawn over other apps, shown while the service is on.
2. Tap starts recording, tap again stops. The bubble shows idle, recording and processing.
3. The result goes to the clipboard, as before.
4. A switch on the settings screen to turn the bubble on and off.

**Done when** Chris can dictate into the clipboard from inside another app without leaving it.

## Session D4: accessibility insertion

**Waits on:** D3. Use the strongest model for this session. It is the hardest one in the plan.

**Before this session, Chris does**

- Enable the app's accessibility service in Settings. If Android says the setting is restricted,
  open the app's App info page and choose "Allow restricted settings" first.
- Set the app's battery use to Unrestricted, so Samsung does not stop the service.

**Build**

1. An `AccessibilityService` that tracks which field has focus.
2. Show the bubble only while an editable field has focus.
3. **Insert at the cursor.** Read the field's current text and selection. Splice the new text in
   at the selection. Write it back with `ACTION_SET_TEXT`, then put the cursor after the inserted
   text with `ACTION_SET_SELECTION`. `ACTION_SET_TEXT` replaces the whole field, which is why the
   splice is needed.
4. **Fallbacks, in order:** if the field's text cannot be read or set, put the text on the
   clipboard and try `ACTION_PASTE`. If that also fails, leave it on the clipboard and show a
   notification.
5. Never read or store field contents beyond the one splice. Never act on password fields.

**Test in:** Samsung Messages, WhatsApp, Gmail, Chrome on a web form, Obsidian, Slack. Record in
Status which path each app took: direct, paste or clipboard.

**Watch for.** Android's Advanced Protection mode blocks accessibility services that are not
declared as accessibility tools. If Chris has that mode on, this stage cannot work as designed.

## Status

Each session writes here: what was built, how to build and install, which library
runs the VAD and why, the per-app results from D4, and anything the next session must know.

### D1: done (2026-10-10)

**Built.** `android/`, Kotlin, one module, no third-party dependencies (plain Views,
`HttpURLConnection`, `org.json`). Package `app.dictation`, minSdk 34, targetSdk 36.
- `MainActivity`: Record/Stop button, status line, last result, Copy button. Asks for the mic
  permission on first tap. Discards recordings under 0.5 s.
- `Recorder`: `AudioRecord`, 16 kHz mono 16-bit PCM. `Wav`: wraps PCM in a 44-byte header.
- `DictationApi`: `POST /v1/dictate` with `audio/wav`, `Authorization: Bearer`, `X-Client: android`.
  401/413/415 (any 4xx) are fatal: error shown, audio dropped. 5xx, timeout and no network are
  retryable: the WAV is saved in `filesDir/pending/<millis>.wav` and the error shown. Retrying is D2.
- `empty: true` shows "No speech found". Otherwise `text` is shown and copied to the clipboard.

**Build and install.** No JDK is on PATH; use Android Studio's:
```
cd android
JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug -q
adb -s R3CX80C28AE install -r app/build/outputs/apk/debug/app-debug.apk
```
AGP 8.13.2, Kotlin 2.0.21, Gradle 8.13 (wrapper checked in). `android/local.properties`
(git-ignored) holds `sdk.dir`, `dictation.baseUrl`, `dictation.token`; D1 uses the mock URL and
`mock-token`. A fresh worktree needs its own copy of that file. Missing keys fall back to the mock.

**Verified on the phone** (Galaxy, serial `R3CX80C28AE`): installed over USB, launched, and drove
Record then Stop with `adb shell input tap`. Screen then showed "Copied to clipboard" and
"This is a mock dictation.". Not verified: a human speaking, and reading the clipboard back
(Android blocks background clipboard reads from adb). Chris should do one real tap-speak-paste check.

**VAD.** Not in D1. D2 must check the current supported way to run Silero on Android.

**For D2.**
- Targeting SDK 36 forces edge-to-edge; `MainActivity` pads for system bar insets itself.
- The phone first showed `unauthorized` in `adb devices` until the USB debugging prompt was accepted.
- Recording currently lives in the Activity; D2 moves it to a foreground service. `Recorder` and
  `DictationApi` are free of Activity references and can be reused as is.
- No tests exist yet.

### D2: built, tile fixed (2026-10-10). Needs Chris's real-speech check through the real backend

**Tile fix (later the same day).** Tapping the tile used to do nothing and then crash the app. Cause, from logcat:
`SecurityException: Starting FGS with type microphone ... requires permissions ... and the app must be in the eligible
state`. On Android 16 a tile tap alone does not make the app eligible to start a microphone service (RECORD_AUDIO is a
while-in-use permission). Fix: the tile launches `RecordStartActivity`, a transparent activity, through
`startActivityAndCollapse`; its `onResume` starts the service and finishes. Stopping from the tile needs no activity.
Verified on the phone: tile tap from the home screen gave `isForeground=true types=0x80`, a second tap stopped it, no
crash, "No speech found" for a silent recording (VAD works, nothing uploaded). Chris's tile appears at the very bottom
of the One UI panel, under the sliders, as its own section.

**Built** (worktree branch `worktree-d2-android`, `android/`, package `app.dictation`, versionName `d2`).
- `RecordingService`: microphone-type foreground service, with a Stop action in its notification. START records,
  STOP or the 10 minute cap (`MAX_RECORDING_MS`) ends it. Then: VAD, trim, upload, clipboard, result notification.
- `DictationTileService`: Quick Settings tile "Dictate". Tap starts or stops the service; shows idle, recording,
  transcribing. If the mic permission is missing it opens the app instead.
- Silence trimming: `SilenceTrimmer` (pure, 4 unit tests, `./gradlew testDebugUnitTest -q`) cuts every silence over
  1 s to 0.3 s, including leading and trailing. `SileroVad` gives one speech flag per 512-sample frame.
  If VAD throws, the untrimmed audio is sent. If no frame is speech, nothing is uploaded ("No speech found").
- Retry: after any successful upload, saved WAVs in `filesDir/pending` are sent oldest first. Recovered text goes to
  a notification only, not the clipboard, because the clipboard holds the newer dictation. 4xx drops the file;
  a retryable error stops the loop.
- `Settings`: base URL (default real URL) and token, token encrypted with an Android Keystore AES-GCM key. `SettingsActivity`
  edits both. The token is no longer in `BuildConfig`; `local.properties` may still override `dictation.baseUrl`.
- `MainActivity` now only drives the service and mirrors `DictationState`. It also asks for the notification permission.

**VAD library: `com.github.gkonovalov.android-vad:silero:2.0.10`** (JitPack, added in `settings.gradle.kts`). It bundles the
Silero ONNX model and onnxruntime-android, targets API 24+, and takes 16 kHz 16-bit mono frames of 512 samples. It was the
ready-made option; running the model through onnxruntime directly would only add code. It pulls in Kotlin stdlib 2.2.0, so
the Kotlin Gradle plugin is now 2.2.0 (was 2.0.21). Smoothing is off (`silenceDurationMs = 0`).

**Verified on the phone (Galaxy, `R3CX80C28AE`, Android 16):** installed, mic and notification permissions granted, Record in
the app started the service with `isForeground=true types=0x80` (microphone), Stop ran VAD, uploaded to the mock and gave
"Copied to clipboard / This is a mock dictation.", and the service ended cleanly. Unit tests and `assembleDebug` pass.

**Not verified, so D2's "done when" is not yet met:**
1. **Real speech through the real backend.** Chris has entered the production token. Still to do: tap the tile, speak with a ten
   second pause, tap again, and check the text comes back clean. I cannot speak into the phone.
2. The retry path and the 10 minute cap were not exercised.

**For D3.** `DictationState` (phase, status, last result, listeners) is what a bubble should observe;
`RecordingService.start/stop(ctx)` are the entry points. A fresh worktree needs a `local.properties` with at least `sdk.dir`;
agent shells cannot copy the main one (blocked), so write one by hand.

### D3: built (2026-10-10). Needs Chris's real-speech check from inside another app

**Built** (worktree branch `worktree-d3-android`, `android/`, package `app.dictation`).
- `OverlayService`: foreground service (type `specialUse`, min-importance notification "Dictation bubble is on") that draws a
  56dp round bubble with `TYPE_APPLICATION_OVERLAY`. It observes `DictationState`: grey idle, red recording, amber processing.
  Tap starts or stops `RecordingService`; tap is ignored while processing; dragging moves it (clamped to the screen) and
  the position is remembered. Without the mic permission a tap opens the app. The result goes to the clipboard as before.
- `Settings.bubbleEnabled` and a "Floating bubble" switch on `SettingsActivity`. Turning it on without "Appear on top" opens
  that permission screen; coming back without granting flips the switch off. `OverlayService.sync(ctx)` starts or stops the
  bubble to match the switch and the permission; `MainActivity` and `SettingsActivity` call it in `onResume`.
- Manifest: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE`.

**Key finding.** A visible overlay window makes the app eligible to start the microphone service from the background on
Android 16 (`getFgsAllowStart=UID_VISIBLE`). No transparent activity is needed for the bubble.

**Verified on the phone (`R3CX80C28AE`):** with the overlay permission granted via `appops` and the switch turned on in the UI,
the bubble appeared over the home screen with the app closed. Tapping it gave `RecordingService isForeground=true types=0x80`;
tapping again stopped it and the service ended. Build and unit tests pass.

**Not verified:** speaking and pasting the result in another app (I cannot speak into the phone); dragging; bubble colours
while recording and processing (only seen idle in a screenshot); the bubble coming back after a reboot.

**Bubble animation (follow-up).** `BubbleView` replaces the plain icon. Idle: mic on slate. Recording: teal with five bars driven by the
live mic RMS (`Recorder` publishes it as `DictationState.level`, no listener notification). Processing: dimmed mic inside a spinning
arc on a blue-grey. Seen on the phone: idle and recording. The spinner is drawn but not yet seen, because a silent test recording
never reaches the upload step.

**For D4.** The bubble does not survive a reboot until the app is opened once (no boot receiver). D4 should show the bubble
only while an editable field has focus; `OverlayService` is where the visibility switch belongs.

### D4: built (2026-10-10). Needs Chris's checks in WhatsApp, Slack, Obsidian and Samsung Messages, and one spoken run

**Built** (worktree branch `worktree-d4-android`, `android/`, versionName `d4`).
- `InsertionService`: the accessibility service. It asks the system which field has input focus after focus, click and window
  events (the event content is not used), and publishes "an editable, non-password field has focus".
- `OverlayService` shows the bubble only while that is true, or while a recording is being made or processed, so it can always be
  stopped. With the accessibility service off, the bubble stays visible everywhere as in D3 and the text goes to the clipboard.
- `RecordingService` hands the result to `InsertionService.insert`, which tries three paths in order:
  1. **Direct.** Read text and selection, splice (`Splice`, 9 unit tests), `ACTION_SET_TEXT`, then `ACTION_SET_SELECTION`.
     It checks that the field really changed; if not, it moves on.
  2. **Paste.** Text to the clipboard, then `ACTION_PASTE`.
  3. **Clipboard.** Text left on the clipboard and a notification posted. The notification now appears only on this path.
- The status line says "Inserted", "Pasted" or "Copied to clipboard". Logcat (tag `Dictation`) gets one line per dictation with the
  app, the path and the field's type. Never the text.
- Settings has a button showing whether insertion is on; it opens the Accessibility settings.
- The accessibility service starts the bubble service when it connects, which the system does at boot. Not tested with a reboot.

**Three things that differ from the plan as written. Chris should know about each.**
1. **The direct path is used only where it cannot lose anything.** `ACTION_SET_TEXT` writes plain text over the whole field. In a
   rich-text web editor this was seen to strip the formatting: `<b>Bold</b> and <i>italic</i> text` came back as plain text. Gmail's
   body and Obsidian are editors of that kind. So: in web content (Chrome and any WebView) only a real `<input>` gets the direct path;
   a `<textarea>` cannot be told apart from a rich editor, so both are pasted. In native fields, text carrying formatting spans
   (bold, links, mentions) is pasted. Fields over 10,000 characters are pasted. Paste inserts at the cursor and keeps everything.
2. **The service is declared `isAccessibilityTool="true"`.** Without it Gmail's compose screen hides its fields from the service
   (Android's "accessibility data sensitive" protection): no bubble and no insertion there. With it, both work. It also means the
   service can see screens that other apps mark as sensitive. It still reads only the focused field, only at the moment of inserting.
   This is the same declaration the plan's Advanced Protection note refers to. Fine for a personal sideloaded app; it would not pass
   Play review. To undo, delete the one line in `res/xml/insertion_service.xml`.
3. **A space is added in front of the text** when it would otherwise run into the previous word ("Done." + "Next" gives
   "Done. Next"). Not in the plan; without it two dictations in a row join up.

**Per-app results** (phone `R3CX80C28AE`, Android 16, driven over adb with the debug hook below, not by voice):

| App | Field | Path | Result |
|---|---|---|---|
| Chrome, web form | `<input>` | direct | Text and cursor correct, twice in a row |
| Chrome, web form | `<textarea>` | paste | Inserted at a mid-text cursor |
| Chrome, web form | rich editor (`contenteditable`) | paste | Formatting kept, cursor after the text |
| Chrome, web form | password | none | Bubble hidden, field untouched, text on clipboard |
| Gmail | Subject | direct | Correct |
| Gmail | Body | paste | Two dictations in a row, spaced correctly |
| Google Messages | Message box | direct | Two dictations in a row |
| This app | URL field | direct | Start, middle and end of text |
| This app | Token field | none | Bubble hidden, field untouched |
| Samsung Messages | | not tested | It is not the default SMS app (Google Messages is) and asks to become it |
| WhatsApp, Slack, Obsidian | | not tested | They open onto real chats and notes; left for Chris |

Also verified: the bubble is hidden on the home screen and appears when a field takes focus; tapping it inside Chrome starts the
microphone service (`isForeground=true types=0x80`), a second tap stops it, and Chrome keeps focus throughout.

**For Chris to check.**
1. One spoken dictation into any app. Every insertion above used test text, not speech.
2. WhatsApp, Slack, Obsidian (and Samsung Messages if wanted): dictate once in each, then run
   `adb logcat -d -s Dictation:I` and copy the `path=` of each line into the table. Obsidian should say `path=PASTE`.
3. Battery set to Unrestricted for the app. I did not change it.
4. Reboot once and check the bubble returns without opening the app.

**Done to the phone during this session.**
- The accessibility service was switched on through adb, by adding it to the existing list. ScreenZen and Bitwarden stay on. Chris
  did not see Android's consent screen for it. Switch it off in Settings > Accessibility > Installed apps if that is not wanted.
- The clipboard, and Gboard's clipboard history, hold test strings ("pw test", "Body sentence two." and similar).
- Chrome has a leftover tab on `localhost:8765`. Google Messages may show an empty conversation with "000". A Gmail test draft was
  discarded.

**Fixed on the way.** The token field in Settings was never masked: `setSingleLine()` after the password input type removes the
masking. It is now a real password field, which is also what keeps the insertion service away from it.

**Debug hook.** Debug builds have `DebugInsertReceiver`: focus a field, then
`adb shell am broadcast -n app.dictation/.DebugInsertReceiver --es text "hello"` inserts as a dictation would. It requires the
`DUMP` permission, which the adb shell has and ordinary apps cannot get.

**Known limits.**
- The paste path leaves the dictation on the clipboard, and so in Gboard's clipboard history.
- If an app accepts `ACTION_SET_TEXT`, changes nothing for half a second, then applies it late, the text would be inserted twice
  (once directly, once pasted). Not seen.
- Samsung may still stop the service if battery use is not Unrestricted.

### D4 follow-up (2026-10-10): paste is now the first path, after a crash in Google Keep

**The bug.** Chris dictated into a Google Keep note. The text went in, then pressing Enter walked the cursor through the dictated
line letter by letter, and Keep crashed: `IndexOutOfBoundsException: setSpan (271 ... 271) ends beyond length 270` in Keep's
`changelogeditor.EditTextView`. The insertion had taken the direct path. Keep keeps its own copy of the note's text, and
`ACTION_SET_TEXT` changed the field without that copy knowing. Keep's field is an ordinary native text box with no formatting, so
nothing can tell it apart in advance from a field where the direct path is safe.

**The fix. The order of the paths is now paste, then direct, then clipboard.** This replaces the order given in the plan and in
the D4 section above. Paste is what the user would do by hand, so every app keeps its own state right. A paste counts only if the
field is then seen to change (when its text can be read). The direct splice is kept as the fallback for a field that refuses a
paste, with the same guards as before. Every row of the D4 table that says "direct" now takes "paste"; re-run after the change:
Keep and this app's URL field, both `path=PASTE`.

**Verified on the phone** in a new Keep note: two dictations with Enter presses and typing between and after them. Both
`path=PASTE`, the text and the cursor were right, and Keep did not crash. The note was then deleted.

**Cost.** Every dictation now lands on the clipboard, and in Gboard's clipboard history, replacing whatever was there.

**"Thank you" added to the end of a dictation.** Whisper tends to invent a closing phrase when a recording ends in silence. The
trimmer used to pass a pause at the end of under 1 s untouched and leave 0.3 s of a longer one. `SilenceTrimmer` now cuts silence at
the very start and end to 0.2 s whatever its length (7 unit tests). Silence between words is unchanged. Not verified by voice.

**To do, outside this workstream.**
- If "Thank you" still appears: the sure fix is in the backend, dropping a closing phrase from the transcript when it is one of
  Whisper's known inventions ("Thank you.", "Thanks for watching."). That belongs to workstream A or B.
- The Mac client has the same trimming rule and probably the same problem. That belongs to workstream C.
