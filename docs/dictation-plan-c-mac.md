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
5. Switch the config to the real URL. Read the token from the Keychain, and add a menu item
   where Chris pastes it in. The agent never sees the production token, so Chris runs the final
   check himself.
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
6. **Last result.** A menu item copies the last result. Keep it in memory from the last
   response. Do not call `GET /v1/history`, which does not exist until session A2.
7. Launch at login.

**Done when** Chris has used it for a day without touching Wispr Flow.

## Status

**C1 done 2026-10-10. Chris confirmed the hotkey test by hand once the permissions were granted.**
- Built: menu bar app (mic icon, Quit), right Option hold-to-talk via a listen-only CGEvent tap
  (keycode 61, device mask 0x40 so left Option is ignored), AVAudioEngine capture converted to 16 kHz
  mono Int16 and wrapped as WAV, under 0.5 s discarded, `POST /v1/dictate` with `X-Client: mac`,
  paste via clipboard (transient + concealed types, Cmd-V, old clipboard restored after 0.5 s),
  notifications for 401/413/415, retryable failures saved to `~/Library/Application Support/Dictation/Pending/`.
- Layout: Swift package in `mac/` (no Xcode project, xcodegen is not installed). `mac/build.sh` assembles
  and signs `mac/build/Dictation.app`. `swift test -q` passes (WAV header test).
- Checked: build clean; the exact request shape (headers, WAV body) against the mock returns the mock text,
  bad token returns 401. Not checkable by an agent: hotkey, microphone, paste, clipboard restore.
- **Signing: no codesigning identity exists on this Mac** (`security find-identity` found 0), so the build is
  ad-hoc signed and macOS will drop Microphone/Accessibility/Input Monitoring grants on every rebuild.
  Fix: Xcode, Settings, Accounts, sign in, Manage Certificates, add Apple Development. `build.sh` picks it up.
- To run: `mac/build.sh && open mac/build/Dictation.app`. Grant Microphone, Accessibility and Input
  Monitoring when asked, then relaunch once (the hotkey tap only starts after the grant).
- Config: `config.json` is created with the mock URL and `mock-token`. C2 moves the token to the Keychain.
- VAD package and latency: not applicable until C2.
- For C2/C3: `AppDelegate.send` holds the upload and paste logic; `Recorder.stop()` returns `[Int16]?`
  (nil means under 0.5 s), which is the place to run VAD. Pending WAVs have no retry yet (C3).
- Done-when check: passed (Chris, by hand, against the mock).

**C2 built 2026-10-10. Needs Chris's final check with the real token (the agent never sees it).**
- Built: Silero VAD v6.2.1 via FluidAudio 0.17.7 (Core ML, the maintained Swift route; pinned in `mac/Package.swift`,
  platform raised to macOS 14). Runs on the finished recording in `SilenceTrimmer`. Gaps over 1.0 s (`maxSilence`)
  are cut to 0.3 s (`keptSilence`), taken from the original audio split around the speech; leading and trailing
  silence follow the same rule. No speech, or trimmed audio under 0.5 s, is discarded. If the model is not loaded
  yet or VAD throws, the full recording is sent.
- Debug switch: `"debugAudio": true` in `config.json` saves `<ts>-before.wav` and `<ts>-after.wav` to
  `~/Library/Application Support/Dictation/Debug/`.
- Config: defaults to the real URL; a C1 file with the mock URL is switched to the real one on first load. The
  token is no longer in the file: Keychain, set via the new menu item "Set API Token..." (secure field). The mock
  URL still falls back to `mock-token`. Missing token gives a notification, no request.
- FLAC: `FlacEncoder` uses AVAudioFile (system, simple). Sent as `audio/flac`; falls back to WAV on any failure.
  Accepted by the mock (checked). Failed uploads are still saved as WAV in `Pending/`.
- Tests: `swift test` 7 pass. Includes real VAD on `docs/sample.wav` + 10 s noise gap + the clip again: two
  spans found, 14.3 s trimmed to 5.1 s. Build signed with the Apple Development identity now present.
- First launch downloads the Silero model (HuggingFace, cached in `~/Library/Application Support/FluidAudio/`);
  the first dictation before it finishes goes untrimmed. VAD run time on a 14 s clip was a few hundred ms in the test.
- Latency: each dictation appends `release_to_paste_ms`, `vad_ms`, audio and trimmed seconds to
  `~/Library/Application Support/Dictation/latency.log`. **Not measured**: it needs the hotkey, mic and real token.
- Chris to do: relaunch, paste the Mac token via the menu, dictate with a 10 s pause in the middle (set
  `debugAudio` to listen to the cuts), then read `latency.log` for a short and a long dictation.
- **Latency measured by Chris, real backend, FLAC (key release to paste):** 2.3 s dictation 1.68 s (first, cold);
  4.4 s dictation 1.12 s; 13.5 s recording with a long pause, trimmed to 4.3 s, 1.36 s. VAD cost 13 to 30 ms.
  Trimming removed about 9 s of pause and the upload stayed small, so a long pause costs nothing extra.
  Whether the 13.5 s transcript was clean (no invented words) is for Chris to confirm; the log cannot show it.
