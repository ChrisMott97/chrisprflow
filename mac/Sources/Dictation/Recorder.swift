import AVFoundation

/// Captures the microphone and converts to 16 kHz mono 16-bit PCM in memory.
final class Recorder {
    static let minSamples = WavEncoder.sampleRate / 2   // 0.5 s
    static let maxSeconds: TimeInterval = 600           // a recording stops itself here and is sent
    static let maxSamples = Int(maxSeconds) * WavEncoder.sampleRate

    private let engine = AVAudioEngine()
    private let lock = NSLock()
    private var samples: [Int16] = []
    private var converter: AVAudioConverter?
    private let target = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: Double(WavEncoder.sampleRate),
                                       channels: 1, interleaved: true)!

    func start() throws {
        lock.lock(); samples.removeAll(keepingCapacity: true); lock.unlock()
        let input = engine.inputNode
        let inFormat = input.outputFormat(forBus: 0)
        converter = AVAudioConverter(from: inFormat, to: target)
        input.installTap(onBus: 0, bufferSize: 4096, format: inFormat) { [weak self] buf, _ in
            self?.append(buf)
        }
        engine.prepare()
        try engine.start()
    }

    /// Stops and returns the recording, or nil if it is shorter than 0.5 s.
    func stop() -> [Int16]? {
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        lock.lock(); defer { lock.unlock() }
        if samples.count > Self.maxSamples { samples.removeSubrange(Self.maxSamples...) }   // contract limit is 10 min
        return samples.count >= Self.minSamples ? samples : nil
    }

    /// Stops and throws the audio away.
    func cancel() {
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        lock.lock(); samples.removeAll(); lock.unlock()
    }

    private func append(_ buf: AVAudioPCMBuffer) {
        guard let converter else { return }
        let ratio = target.sampleRate / buf.format.sampleRate
        let cap = AVAudioFrameCount(Double(buf.frameLength) * ratio) + 16
        guard let out = AVAudioPCMBuffer(pcmFormat: target, frameCapacity: cap) else { return }
        var fed = false
        var err: NSError?
        converter.convert(to: out, error: &err) { _, status in
            if fed { status.pointee = .noDataNow; return nil }
            fed = true; status.pointee = .haveData; return buf
        }
        guard err == nil, let p = out.int16ChannelData?[0] else { return }
        lock.lock()
        samples.append(contentsOf: UnsafeBufferPointer(start: p, count: Int(out.frameLength)))
        lock.unlock()
    }
}
