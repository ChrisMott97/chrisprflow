import Foundation

/// Read from ~/Library/Application Support/Dictation/config.json, outside the repo.
/// The token is not in the file: it lives in the Keychain (menu item "Set API Token...").
struct Config: Codable {
    var baseURL: String
    /// Saves the audio before and after trimming to Support/Debug/ so the cut points can be heard.
    var debugAudio: Bool?

    static let mockURL = "https://dictation-mock.chrisamott97.workers.dev"
    static let realURL = "https://dictation.chrisamott97.workers.dev"

    var isMock: Bool { baseURL == Self.mockURL }

    /// Keychain token; the mock backend needs no secret, so it falls back to `mock-token` there.
    var token: String { Keychain.token() ?? (isMock ? "mock-token" : "") }

    static var supportDir: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Dictation", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func load() -> Config {
        let url = supportDir.appendingPathComponent("config.json")
        // A C1 file also holds `baseURL: mock` and `token: mock-token`; extra keys are ignored, and the
        // mock URL is switched to the real one once (C2).
        if let data = try? Data(contentsOf: url), var c = try? JSONDecoder().decode(Config.self, from: data) {
            if c.isMock { c.baseURL = realURL; c.save() }
            return c
        }
        let c = Config(baseURL: realURL, debugAudio: false)
        c.save()
        return c
    }

    private func save() {
        let enc = JSONEncoder()
        enc.outputFormatting = [.prettyPrinted]
        try? enc.encode(self).write(to: Config.supportDir.appendingPathComponent("config.json"))
    }
}
