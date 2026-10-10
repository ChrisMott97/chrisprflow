import XCTest
@testable import Dictation

final class KeyGestureTests: XCTestCase {
    private let t0 = Date(timeIntervalSince1970: 1000)
    private func at(_ s: TimeInterval) -> Date { t0.addingTimeInterval(s) }

    func testHoldStartsOnDownAndStopsOnUp() {
        var g = KeyGesture()
        XCTAssertEqual(g.down(at: at(0)), .start)
        XCTAssertEqual(g.up(at: at(2)), .stopAndSend)
        XCTAssertFalse(g.isRecording)
    }

    func testTapKeepsRecordingUntilNextTap() {
        var g = KeyGesture()
        XCTAssertEqual(g.down(at: at(0)), .start)
        XCTAssertEqual(g.up(at: at(0.1)), .none)
        XCTAssertTrue(g.isRecording)
        XCTAssertEqual(g.down(at: at(5)), .stopAndSend)
        XCTAssertEqual(g.up(at: at(5.1)), .none)     // release of the stopping tap
        XCTAssertFalse(g.isRecording)
        XCTAssertEqual(g.down(at: at(6)), .start)    // and the next press works normally
    }

    func testCancelWhileHoldingIgnoresRelease() {
        var g = KeyGesture()
        _ = g.down(at: at(0))
        XCTAssertTrue(g.end())
        XCTAssertEqual(g.up(at: at(3)), .none)
        XCTAssertFalse(g.isRecording)
    }

    func testCancelWhileToggledNeedsNoRelease() {
        var g = KeyGesture()
        _ = g.down(at: at(0)); _ = g.up(at: at(0.1))
        XCTAssertTrue(g.end())
        XCTAssertEqual(g.down(at: at(4)), .start)    // not swallowed by a stale ignore flag
        XCTAssertEqual(g.up(at: at(6)), .stopAndSend)
    }

    func testEndWhenIdleDoesNothing() {
        var g = KeyGesture()
        XCTAssertFalse(g.end())
        XCTAssertEqual(g.down(at: at(0)), .start)
    }

    func testRecorderCapMatchesContractLimit() {
        XCTAssertEqual(Recorder.maxSeconds, 600)
        XCTAssertEqual(Recorder.maxSamples, 600 * 16000)
    }
}
