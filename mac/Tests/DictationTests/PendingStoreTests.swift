import XCTest
@testable import Dictation

final class PendingStoreTests: XCTestCase {
    func testSaveListsOldestFirstAndIgnoresOtherFiles() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("pending-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: dir) }
        let a = try XCTUnwrap(PendingStore.save(Data([1]), in: dir))
        Thread.sleep(forTimeInterval: 0.01)
        let b = try XCTUnwrap(PendingStore.save(Data([2]), in: dir))
        try Data().write(to: dir.appendingPathComponent("note.txt"))
        XCTAssertEqual(PendingStore.files(in: dir).map(\.lastPathComponent), [a.lastPathComponent, b.lastPathComponent])
    }
}
