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

Not started. Each session writes here: what was built, how to build and install, which library
runs the VAD and why, the per-app results from D4, and anything the next session must know.
