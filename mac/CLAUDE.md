# mac/
macOS dictation client. Plan: `docs/dictation-plan-c-mac.md`. Real backend since C2; token in Keychain (menu: Set API Token...). C3: `KeyGesture` (tap/hold logic), `PendingStore` (retry queue).
- Build: `mac/build.sh` (quiet; makes `mac/build/Dictation.app`, signs with an Apple Development identity if present, else ad-hoc).
- Test: `cd mac && swift test -q`
- Swift package, no Xcode project. Config: `~/Library/Application Support/Dictation/config.json` (`baseURL`, `debugAudio`; real URL by default). VAD is FluidAudio (Silero, Core ML); tests download the model..
