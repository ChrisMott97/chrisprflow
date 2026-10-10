import Cocoa
import AVFoundation
import ServiceManagement
import FluidAudio

final class AppDelegate: NSObject, NSApplicationDelegate, NSMenuDelegate {
    private var statusItem: NSStatusItem!
    private let hotkey = HotkeyMonitor()
    private let recorder = Recorder()
    private var gesture = KeyGesture()
    private var capTimer: Timer?
    private var processing = 0          // dictations between key release and paste
    private var retrying = false
    private var lastResult: String?     // in memory only; GET /v1/history is not used
    private let config = Config.load()
    private var vad: VadManager?

    private let copyLastItem = NSMenuItem(title: "Copy Last Result", action: #selector(copyLastResult), keyEquivalent: "")
    private let waitingItem = NSMenuItem(title: "", action: nil, keyEquivalent: "")
    private let loginItem = NSMenuItem(title: "Launch at Login", action: #selector(toggleLaunchAtLogin), keyEquivalent: "")

    func applicationDidFinishLaunching(_ notification: Notification) {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        let menu = NSMenu()
        menu.delegate = self
        copyLastItem.target = self
        loginItem.target = self
        waitingItem.isEnabled = false
        menu.addItem(copyLastItem)
        menu.addItem(waitingItem)
        menu.addItem(.separator())
        menu.addItem(withTitle: "Set API Token...", action: #selector(setToken), keyEquivalent: "").target = self
        menu.addItem(loginItem)
        menu.addItem(withTitle: "Quit Dictation", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        statusItem.menu = menu
        refreshIcon()
        enableLaunchAtLoginOnFirstRun()

        // Loads (and on first run downloads) the Silero model. Until it is ready, audio is sent untrimmed.
        Task { [weak self] in
            do { self?.vad = try await VadManager() }
            catch { Notifier.show("Silence trimming unavailable: \(error.localizedDescription)") }
        }

        Notifier.requestAuthorization()
        AVCaptureDevice.requestAccess(for: .audio) { _ in }
        hotkey.onDown = { [weak self] in self?.keyDown() }
        hotkey.onUp = { [weak self] in self?.keyUp() }
        hotkey.onEscape = { [weak self] in self?.cancelRecording() }
        if !hotkey.start() {
            // Prompts for Accessibility / Input Monitoring; the user must relaunch after granting.
            let opts = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
            _ = AXIsProcessTrustedWithOptions(opts)
            Notifier.show("Grant Accessibility and Input Monitoring in System Settings, then relaunch.")
        }
        Task { await retryPending() }
    }

    // MARK: Menu

    func menuWillOpen(_ menu: NSMenu) {
        copyLastItem.isEnabled = lastResult != nil
        let n = PendingStore.count
        waitingItem.title = n == 0 ? "No recordings waiting" : "\(n) recording\(n == 1 ? "" : "s") waiting to upload"
        loginItem.state = SMAppService.mainApp.status == .enabled ? .on : .off
    }

    @objc private func copyLastResult() {
        guard let text = lastResult else { return }
        let pb = NSPasteboard.general
        pb.clearContents()
        pb.setString(text, forType: .string)
    }

    @objc private func toggleLaunchAtLogin() {
        do {
            if SMAppService.mainApp.status == .enabled { try SMAppService.mainApp.unregister() }
            else { try SMAppService.mainApp.register() }
        } catch {
            Notifier.show("Could not change Launch at Login: \(error.localizedDescription)")
        }
    }

    /// Registers once, so a later "off" in the menu is respected.
    private func enableLaunchAtLoginOnFirstRun() {
        let key = "didSetLaunchAtLogin"
        guard !UserDefaults.standard.bool(forKey: key) else { return }
        UserDefaults.standard.set(true, forKey: key)
        try? SMAppService.mainApp.register()
    }

    @objc private func setToken() {
        let alert = NSAlert()
        alert.messageText = "API token"
        alert.informativeText = "Paste the Mac token. It is stored in the Keychain."
        let field = NSSecureTextField(frame: NSRect(x: 0, y: 0, width: 320, height: 24))
        alert.accessoryView = field
        alert.addButton(withTitle: "Save")
        alert.addButton(withTitle: "Cancel")
        NSApp.activate(ignoringOtherApps: true)
        alert.window.initialFirstResponder = field
        guard alert.runModal() == .alertFirstButtonReturn else { return }
        let t = field.stringValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        Notifier.show(Keychain.setToken(t) ? "Token saved." : "Could not save the token to the Keychain.")
    }

    // MARK: Indicator

    /// Recording wins over processing, processing over idle.
    private func refreshIcon() {
        let (symbol, tint): (String, NSColor?) =
            gesture.isRecording ? ("mic.fill", .systemRed) :
            processing > 0 ? ("ellipsis.circle", .systemOrange) : ("mic", nil)
        guard let button = statusItem.button else { return }
        button.image = NSImage(systemSymbolName: symbol, accessibilityDescription: "Dictation")
        button.contentTintColor = tint
    }

    // MARK: Recording

    private func keyDown() {
        switch gesture.down(at: Date()) {
        case .start: startRecording()
        case .stopAndSend: stopAndSend()
        case .none: break
        }
    }

    private func keyUp() {
        if gesture.up(at: Date()) == .stopAndSend { stopAndSend() }
    }

    private func startRecording() {
        do {
            try recorder.start()
        } catch {
            gesture.end()
            Notifier.show("Could not start recording: \(error.localizedDescription)")
            return
        }
        capTimer = Timer.scheduledTimer(withTimeInterval: Recorder.maxSeconds, repeats: false) { [weak self] _ in
            self?.capReached()
        }
        refreshIcon()
    }

    /// Escape: throw the recording away.
    private func cancelRecording() {
        guard gesture.end() else { return }
        capTimer?.invalidate()
        recorder.cancel()
        refreshIcon()
    }

    /// Ten minutes: stop and send what there is.
    private func capReached() {
        guard gesture.end() else { return }
        Notifier.show("Recording stopped at the 10 minute limit and sent.")
        stopAndSend()
    }

    /// The gesture has already left the recording state; this stops the recorder and starts the pipeline.
    private func stopAndSend() {
        capTimer?.invalidate()
        let released = Date()
        guard let samples = recorder.stop() else { refreshIcon(); return }   // under 0.5 s: do nothing
        processing += 1
        refreshIcon()
        Task {
            await process(samples, released: released)
            await MainActor.run { processing -= 1; refreshIcon() }
        }
    }

    // MARK: Pipeline

    /// Trim silences, encode, upload, paste.
    private func process(_ original: [Int16], released: Date) async {
        var samples = original
        var vadMs = 0
        if let vad {
            let t0 = Date()
            do {
                let spans = try await SilenceTrimmer.speechSpans(in: original, vad: vad)
                guard let trimmed = SilenceTrimmer.trim(original, spans: spans) else { return }   // no speech or under 0.5 s
                samples = trimmed
            } catch {
                Notifier.show("Trimming failed, sending the full recording: \(error.localizedDescription)")
            }
            vadMs = Int(Date().timeIntervalSince(t0) * 1000)
        }
        if config.debugAudio == true { saveDebug(original, samples) }
        await send(samples, released: released, vadMs: vadMs, originalCount: original.count)
    }

    private func saveDebug(_ before: [Int16], _ after: [Int16]) {
        let dir = Config.supportDir.appendingPathComponent("Debug", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let stamp = Int(Date().timeIntervalSince1970)
        try? WavEncoder.wav(fromPCM16: before).write(to: dir.appendingPathComponent("\(stamp)-before.wav"))
        try? WavEncoder.wav(fromPCM16: after).write(to: dir.appendingPathComponent("\(stamp)-after.wav"))
    }

    private func send(_ samples: [Int16], released: Date, vadMs: Int, originalCount: Int) async {
        let wav = WavEncoder.wav(fromPCM16: samples)
        // FLAC when the system encoder works, else WAV.
        let flac = FlacEncoder.flac(fromPCM16: samples)
        do {
            let r = try await APIClient(config: config).dictate(audio: flac ?? wav, contentType: flac != nil ? "audio/flac" : "audio/wav")
            if !r.empty && !r.text.isEmpty {
                await MainActor.run { lastResult = r.text; Paster.paste(r.text) }
                logLatency(released: released, vadMs: vadMs, before: originalCount, after: samples.count, flac: flac != nil)
            }
            await retryPending()   // the network works, so send anything that was waiting
        } catch DictateError.fatal(let m) {
            Notifier.show(m)
        } catch DictateError.retryable(let m) {
            PendingStore.save(wav)
            Notifier.show("Upload failed (\(m)). Recording saved for retry.")
        } catch {
            Notifier.show(error.localizedDescription)
        }
    }

    /// Sends saved recordings, oldest first. Stops at the first failure. A recovered transcript is not
    /// pasted, because the cursor has moved on; it goes to Copy Last Result.
    private func retryPending() async {
        let alreadyRunning = await MainActor.run { () -> Bool in
            if retrying { return true }
            retrying = true
            return false
        }
        if alreadyRunning { return }
        defer { Task { @MainActor in retrying = false } }

        var recovered = 0
        for file in PendingStore.files() {
            guard let wav = try? Data(contentsOf: file) else { continue }
            do {
                let r = try await APIClient(config: config).dictate(audio: wav, contentType: "audio/wav")
                try? FileManager.default.removeItem(at: file)
                if !r.empty && !r.text.isEmpty {
                    recovered += 1
                    await MainActor.run { lastResult = r.text }
                }
            } catch DictateError.fatal(let m) {
                Notifier.show("Saved recordings could not be sent: \(m)")
                break
            } catch {
                break   // still offline or the server is failing; try again at the next success or launch
            }
        }
        if recovered > 0 {
            Notifier.show("\(recovered) saved dictation\(recovered == 1 ? "" : "s") sent. Use Copy Last Result.")
        }
    }

    /// One line per dictation in Support/latency.log: key release to paste, and the audio lengths.
    private func logLatency(released: Date, vadMs: Int, before: Int, after: Int, flac: Bool) {
        let rate = Double(WavEncoder.sampleRate)
        let line = String(format: "%@ release_to_paste_ms=%d vad_ms=%d audio_s=%.1f trimmed_s=%.1f format=%@\n",
                          ISO8601DateFormatter().string(from: Date()), Int(Date().timeIntervalSince(released) * 1000), vadMs,
                          Double(before) / rate, Double(after) / rate, flac ? "flac" : "wav")
        let url = Config.supportDir.appendingPathComponent("latency.log")
        if let h = try? FileHandle(forWritingTo: url) { h.seekToEndOfFile(); h.write(Data(line.utf8)); try? h.close() }
        else { try? Data(line.utf8).write(to: url) }
    }
}
