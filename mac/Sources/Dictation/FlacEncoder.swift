import AVFoundation

enum FlacEncoder {
    /// Encodes 16 kHz mono PCM as FLAC through AVAudioFile. Returns nil on any failure; the caller sends WAV.
    static func flac(fromPCM16 samples: [Int16]) -> Data? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("dict-\(UUID().uuidString).flac")
        defer { try? FileManager.default.removeItem(at: url) }
        guard !samples.isEmpty,
              let fmt = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: Double(WavEncoder.sampleRate), channels: 1, interleaved: true),
              let buf = AVAudioPCMBuffer(pcmFormat: fmt, frameCapacity: AVAudioFrameCount(samples.count)) else { return nil }
        buf.frameLength = AVAudioFrameCount(samples.count)
        samples.withUnsafeBufferPointer { buf.int16ChannelData![0].update(from: $0.baseAddress!, count: samples.count) }
        do {
            do {
                let file = try AVAudioFile(forWriting: url, settings: [
                    AVFormatIDKey: kAudioFormatFLAC, AVSampleRateKey: Double(WavEncoder.sampleRate),
                    AVNumberOfChannelsKey: 1, AVLinearPCMBitDepthKey: 16,
                ], commonFormat: .pcmFormatInt16, interleaved: true)
                try file.write(from: buf)
            }   // the file closes here, which finalizes the header
            let data = try Data(contentsOf: url)
            return data.starts(with: Array("fLaC".utf8)) ? data : nil
        } catch { return nil }
    }
}
