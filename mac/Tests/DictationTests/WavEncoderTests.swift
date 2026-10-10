import XCTest
@testable import Dictation

final class WavEncoderTests: XCTestCase {
    func testHeaderAndSize() {
        let wav = WavEncoder.wav(fromPCM16: [0, 1, -1, 32767])
        XCTAssertEqual(wav.count, 44 + 8)
        XCTAssertEqual(String(decoding: wav[0..<4], as: UTF8.self), "RIFF")
        XCTAssertEqual(String(decoding: wav[8..<12], as: UTF8.self), "WAVE")
        XCTAssertEqual(wav.subdata(in: 24..<28), Data([0x80, 0x3E, 0, 0]))   // 16000 Hz
        XCTAssertEqual(wav.subdata(in: 40..<44), Data([8, 0, 0, 0]))
    }
}
