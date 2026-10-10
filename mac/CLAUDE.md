# mac/
macOS dictation client. Plan: `docs/dictation-plan-c-mac.md`. Talk to the mock backend until A1 is done.
- Build: `mac/build.sh` (quiet; makes `mac/build/Dictation.app`, signs with an Apple Development identity if present, else ad-hoc).
- Test: `cd mac && swift test -q`
- Swift package, no Xcode project. Config: `~/Library/Application Support/Dictation/config.json` (`baseURL`, `token`; created with mock values on first run).
