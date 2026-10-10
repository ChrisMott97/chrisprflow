---
type: topic
subject: Dictation tool build plan - workstream C, the Mac client
author: claude
reviewed:
sources:
  - "[[topics/personal-voice-dictation-tool]]"
  - "conversation: 'dictation tool implementation plan', Oct 2026"
last_updated: 2026-10-10
---

# Workstream C: Mac client

**What this is.** The plan for the Mac menu bar app that records, uploads and pastes. Three
sessions in a chain: C1, C2, C3.
**Read first.** `dictation-plan-contract.md`. Nothing else is needed.

- C1 gives a working dictation loop against the mock backend.
- C2 adds silence trimming and switches to the real backend. The app is usable daily from here.
- C3 adds tap-to-toggle, cancel, the indicator and the length cap.
- The folder is `mac/`. The app is Swift, a menu bar app with no dock icon and no main window.

These sessions must run in Claude Code on the Mac itself. They need the microphone and the
macOS permission prompts.

## Before C1, Chris does

- Install Xcode and sign in with an Apple ID, so the app can be signed with a personal
  development certificate. **The signing matters:** macOS ties the Microphone and Accessibility
  permissions to the app's signature. An unsigned build loses its permissions on every rebuild.
- Choose the hotkey. The default in this plan is the right Option key.
- When the app first runs, grant Microphone and Accessibility in System Settings.

## Session C1: record, upload, paste

**Build**

1. A menu bar app with an icon and a Quit item.
2. **Hotkey.** A global listener for the chosen key. Key down starts recording, key up stops.
3. **Recording.** Capture the microphone and convert to 16 kHz mono 16-bit PCM in memory. Wrap
   it as WAV.
4. **Length check.** Discard a recording under 0.5 seconds and do nothing.
5. **Upload.** `POST /v1/dictate` as in the contract, with `X-Client: mac`. The base URL and
   token come from a small config file outside the repo. Use the mock URL and `mock-token`.
6. **Paste.** If `empty` is true, do nothing. Otherwise save the current clipboard, write `text`
   to the clipboard marked as transient and concealed (the `org.nspasteboard` types, which tell
   clipboard managers not to record it), send Command-V, then restore the old clipboard after a
   short delay.
7. **Errors.** On a `401`, `413` or `415`, show a notification. On anything retryable, save the
   WAV to the app's support folder and show a notification. Retrying comes in C3.

**Done when** holding the key in any text field, speaking, and releasing pastes "This is a mock
dictation." and the previous clipboard content is still there afterwards. Chris confirms this by
hand, because an agent cannot press a global hotkey and speak.

## Session C2: silence trimming and the real backend

**Waits on:** C1, and A1 deployed.

**Build**

1. **VAD** (voice activity detection, a small model that marks which parts of the audio are
   speech), using Silero VAD on the device. Check what the current supported way to run it from
   Swift is before choosing a package. Run it on the finished recording, not live.
2. **Trim.** Cut every silence longer than 1 second down to 0.3 seconds. Leave shorter pauses
   alone. Join the result into one WAV. Keep both numbers as named constants.
3. If the trimmed audio is under 0.5 seconds, discard it.
4. A debug switch that saves the audio before and after trimming, so the cut points can be
   listened to.
5. Switch the config to the real URL and the Mac token. Move the token to the Keychain.
6. Encode as FLAC before upload if that is simple with the system frameworks. Otherwise leave it
   as WAV.

**Done when** a recording with a ten second pause in the middle produces one clean transcript
with no invented words, through the real backend. Report the time from key release to paste for
a short and a long dictation.

## Session C3: controls

**Waits on:** C2.

**Build**

1. **Two modes on one key.** Hold to talk, as now. A quick tap starts a recording that runs
   until the next tap.
2. **Cancel.** Escape discards the recording in progress.
3. **Indicator.** The menu bar icon shows three states: idle, recording, processing.
4. **Cap.** A recording stops itself at 10 minutes and is sent.
5. **Retry.** Saved recordings from failed uploads are retried on the next successful request and
   at launch. A menu item shows how many are waiting.
6. **Last result.** A menu item copies the last result, read from `GET /v1/history`.
7. Launch at login.

**Done when** Chris has used it for a day without touching Wispr Flow.

## Status

Not started. Each session writes here: what was built, how to build and run, the package chosen
for the VAD and why, measured latency, and anything the next session must know.
