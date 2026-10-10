import XCTest
import FluidAudio
@testable import Dictation

final class SilenceTrimmerTests: XCTestCase {
    private let rate = WavEncoder.sampleRate

    private func audio(seconds: Double) -> [Int16] { [Int16](repeating: 100, count: Int(seconds * Double(rate))) }

    func testLongGapCutToKeptSilence() {
        // speech 1-3 s, 10 s gap, speech 13-15 s, 1 s trailing silence
        let out = SilenceTrimmer.trim(audio(seconds: 16), spans: [(1, 3), (13, 15)])!
        // leading 1 s (not longer than 1 s: kept whole) + 2 s + 0.3 s + 2 s + 1 s trailing
        XCTAssertEqual(Double(out.count) / Double(rate), 1 + 2 + SilenceTrimmer.keptSilence + 2 + 1, accuracy: 0.01)
    }

    func testShortGapUntouched() {
        let out = SilenceTrimmer.trim(audio(seconds: 6), spans: [(0, 2), (2.8, 6)])!
        XCTAssertEqual(out.count, audio(seconds: 6).count)
    }

    func testLeadingAndTrailingSilenceShortened() {
        let out = SilenceTrimmer.trim(audio(seconds: 20), spans: [(8, 12)])!
        XCTAssertEqual(Double(out.count) / Double(rate), 0.3 + 4 + 0.3, accuracy: 0.01)
    }

    func testNoSpeechOrTooShortIsNil() {
        XCTAssertNil(SilenceTrimmer.trim(audio(seconds: 5), spans: []))
        XCTAssertNil(SilenceTrimmer.trim(audio(seconds: 0.4), spans: [(0.1, 0.3)]))
    }

    func testFlacEncodes() throws {
        let flac = try XCTUnwrap(FlacEncoder.flac(fromPCM16: audio(seconds: 1)))
        XCTAssertEqual(Array(flac.prefix(4)), Array("fLaC".utf8))
    }

    /// Real VAD on sample.wav, then the same clip twice around 10 s of low noise.
    func testVadOnSampleWithTenSecondPause() async throws {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("docs/sample.wav")
        let data = try Data(contentsOf: url)
        let clip: [Int16] = data.dropFirst(44).withUnsafeBytes { Array($0.bindMemory(to: Int16.self)) }
        let noise = (0..<(10 * rate)).map { _ in Int16.random(in: -20...20) }
        let long = clip + noise + clip

        let vad = try await VadManager()
        let spans = try await SilenceTrimmer.speechSpans(in: long, vad: vad)
        let trimmed = try XCTUnwrap(SilenceTrimmer.trim(long, spans: spans))
        let before = Double(long.count) / Double(rate), after = Double(trimmed.count) / Double(rate)
        print("VADTEST spans=\(spans) before=\(before)s after=\(after)s")
        XCTAssertGreaterThanOrEqual(spans.count, 2)
        XCTAssertLessThan(after, before - 8)
    }
}
