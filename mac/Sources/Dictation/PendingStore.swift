import Foundation

/// WAV recordings whose upload failed, kept in Support/Pending/ until they can be sent.
enum PendingStore {
    static var dir: URL { Config.supportDir.appendingPathComponent("Pending", isDirectory: true) }

    /// Oldest first.
    static func files(in dir: URL = PendingStore.dir) -> [URL] {
        let all = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        return all.filter { $0.pathExtension == "wav" }.sorted { $0.lastPathComponent < $1.lastPathComponent }
    }

    static var count: Int { files().count }

    @discardableResult
    static func save(_ wav: Data, in dir: URL = PendingStore.dir) -> URL? {
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        // Millisecond stamp so two failures in one second do not overwrite each other.
        let url = dir.appendingPathComponent("\(Int(Date().timeIntervalSince1970 * 1000)).wav")
        return (try? wav.write(to: url)) != nil ? url : nil
    }
}
