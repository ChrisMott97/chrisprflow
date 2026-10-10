import Foundation

/// Decides what a press of the dictation key means. Hold to talk: recording runs while the key is down.
/// A quick tap (shorter than `tapThreshold`) starts a recording that runs until the next press.
struct KeyGesture {
    enum Phase { case idle, holding, toggled }
    enum Action { case none, start, stopAndSend }

    static let tapThreshold: TimeInterval = 0.3

    private(set) var phase = Phase.idle
    private var pressedAt = Date.distantPast
    private var ignoreNextUp = false

    var isRecording: Bool { phase != .idle }

    mutating func down(at now: Date) -> Action {
        switch phase {
        case .idle:
            phase = .holding
            pressedAt = now
            return .start
        case .toggled:
            phase = .idle
            ignoreNextUp = true   // the release of the stopping tap is not a gesture
            return .stopAndSend
        case .holding:
            return .none
        }
    }

    mutating func up(at now: Date) -> Action {
        if ignoreNextUp { ignoreNextUp = false; return .none }
        guard phase == .holding else { return .none }
        if now.timeIntervalSince(pressedAt) < Self.tapThreshold {
            phase = .toggled
            return .none
        }
        phase = .idle
        return .stopAndSend
    }

    /// Ends a recording from outside the key: Escape, the length cap, or a failed start.
    /// Returns false when nothing was recording.
    @discardableResult
    mutating func end() -> Bool {
        guard phase != .idle else { return false }
        if phase == .holding { ignoreNextUp = true }   // the key is still down; its release must not act
        phase = .idle
        return true
    }
}
