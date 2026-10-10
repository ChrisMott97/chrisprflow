import Foundation
import FluidAudio

/// Cuts long silences out of a finished recording.
enum SilenceTrimmer {
    /// A silence longer than this is shortened.
    static let maxSilence: Double = 1.0
    /// Length a long silence is cut down to.
    static let keptSilence: Double = 0.3
    /// Speech shorter than this after trimming is discarded.
    static let minTrimmedSamples = WavEncoder.sampleRate / 2   // 0.5 s

    /// Runs Silero VAD on the recording, returns speech spans in seconds.
    static func speechSpans(in samples: [Int16], vad: VadManager) async throws -> [(start: Double, end: Double)] {
        let floats = samples.map { Float($0) / 32768 }
        var cfg = VadSegmentationConfig.default
        cfg.minSilenceDuration = 0.5   // finer than maxSilence so gaps near 1 s are still seen
        let segs = try await vad.segmentSpeech(floats, config: cfg)
        return segs.map { (Double($0.startTime), Double($0.endTime)) }
    }

    /// Pure trimming: every gap (including leading and trailing) longer than `maxSilence` keeps
    /// `keptSilence` of the original audio, split around the speech. Returns nil when no speech
    /// or the result is under 0.5 s.
    static func trim(_ samples: [Int16], spans: [(start: Double, end: Double)]) -> [Int16]? {
        let rate = Double(WavEncoder.sampleRate)
        let n = samples.count
        func idx(_ t: Double) -> Int { min(n, max(0, Int((t * rate).rounded()))) }
        let sorted = spans.sorted { $0.start < $1.start }
        guard !sorted.isEmpty else { return nil }
        let total = Double(n) / rate
        var out: [Int16] = []
        var cursor = 0.0   // everything before `cursor` is already handled
        for (i, s) in sorted.enumerated() {
            let start = max(s.start, cursor)
            let gap = start - cursor
            if gap > maxSilence {
                if i == 0 {
                    out += samples[idx(start - keptSilence)..<idx(start)]
                } else {
                    // Half of the kept silence after the previous speech, half before the next.
                    out += samples[idx(cursor)..<idx(cursor + keptSilence / 2)]
                    out += samples[idx(start - keptSilence / 2)..<idx(start)]
                }
            } else {
                out += samples[idx(cursor)..<idx(start)]
            }
            out += samples[idx(start)..<idx(max(s.end, start))]
            cursor = max(s.end, start)
        }
        let tail = total - cursor
        out += samples[idx(cursor)..<idx(tail > maxSilence ? cursor + keptSilence : total)]
        return out.count >= minTrimmedSamples ? out : nil
    }
}
