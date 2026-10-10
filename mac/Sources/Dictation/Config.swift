import Foundation

/// Read from ~/Library/Application Support/Dictation/config.json, outside the repo.
struct Config: Codable {
    var baseURL: String
    var token: String

    static let mock = Config(baseURL: "https://dictation-mock.chrisamott97.workers.dev", token: "mock-token")

    static var supportDir: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Dictation", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func load() -> Config {
        let url = supportDir.appendingPathComponent("config.json")
        if let data = try? Data(contentsOf: url), let c = try? JSONDecoder().decode(Config.self, from: data) {
            return c
        }
        let enc = JSONEncoder()
        enc.outputFormatting = [.prettyPrinted]
        try? enc.encode(Config.mock).write(to: url)
        return .mock
    }
}
