import Cocoa
import AVFoundation
import FluidAudio

final class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private let hotkey = HotkeyMonitor()
    private let recorder = Recorder()
    private var recording = false
    private let config = Config.load()
    private var vad: VadManager?

    func applicationDidFinishLaunching(_ notification: Notification) {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        statusItem.button?.image = NSImage(systemSymbolName: "mic", accessibilityDescription: "Dictation")
        let menu = NSMenu()
        menu.addItem(withTitle: "Quit Dictation", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        menu.insertItem(withTitle: "Set API Token...", action: #selector(setToken), keyEquivalent: "", at: 0).target = self
        statusItem.menu = menu
        // Loads (and on first run downloads) the Silero model. Until it is ready, audio is sent untrimmed.
        Task { [weak self] in
            do { self?.vad = try await VadManager() }
            catch { Notifier.show("Silence trimming unavailable: \(error.localizedDescription)") }
        }

        Notifier.requestAuthorization()
        AVCaptureDevice.requestAccess(for: .audio) { _ in }
        hotkey.onDown = { [weak self] in self?.startRecording() }
        hotkey.onUp = { [weak self] in self?.stopAndSend() }
        if !hotkey.start() {
            // Prompts for Accessibility / Input Monitoring; the user must relaunch after granting.
            let opts = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
            _ = AXIsProcessTrustedWithOptions(opts)
            Notifier.show("Grant Accessibility and Input Monitoring in System Settings, then relaunch.")
        }
    }

    private func startRecording() {
        guard !recording else { return }
        do { try recorder.start(); recording = true }
        catch { Notifier.show("Could not start recording: \(error.localizedDescription)") }
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

    private func stopAndSend() {
        guard recording else { return }
        recording = false
        let released = Date()
        guard let samples = recorder.stop() else { return }   // under 0.5 s: do nothing
        Task { await process(samples, released: released) }
    }

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
            if r.empty || r.text.isEmpty { return }
            await MainActor.run { Paster.paste(r.text) }
            logLatency(released: released, vadMs: vadMs, before: originalCount, after: samples.count, flac: flac != nil)
        } catch DictateError.fatal(let m) {
            Notifier.show(m)
        } catch DictateError.retryable(let m) {
            let name = "\(Int(Date().timeIntervalSince1970)).wav"
            let dir = Config.supportDir.appendingPathComponent("Pending", isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try? wav.write(to: dir.appendingPathComponent(name))
            Notifier.show("Upload failed (\(m)). Recording saved for retry.")
        } catch {
            Notifier.show(error.localizedDescription)
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
