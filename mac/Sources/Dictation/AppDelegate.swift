import Cocoa
import AVFoundation

final class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private let hotkey = HotkeyMonitor()
    private let recorder = Recorder()
    private var recording = false
    private let config = Config.load()

    func applicationDidFinishLaunching(_ notification: Notification) {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        statusItem.button?.image = NSImage(systemSymbolName: "mic", accessibilityDescription: "Dictation")
        let menu = NSMenu()
        menu.addItem(withTitle: "Quit Dictation", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q")
        statusItem.menu = menu

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

    private func stopAndSend() {
        guard recording else { return }
        recording = false
        guard let samples = recorder.stop() else { return }   // under 0.5 s: do nothing
        let wav = WavEncoder.wav(fromPCM16: samples)
        Task { await send(wav) }
    }

    private func send(_ wav: Data) async {
        do {
            let r = try await APIClient(config: config).dictate(wav: wav)
            if r.empty || r.text.isEmpty { return }
            await MainActor.run { Paster.paste(r.text) }
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
}
