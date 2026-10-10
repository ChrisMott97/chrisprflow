import Cocoa

/// Global listener for the right Option key. Key down fires `onDown`, key up fires `onUp`.
final class HotkeyMonitor {
    static let rightOptionKeyCode: Int64 = 61
    private static let rightOptionDeviceMask: UInt64 = 0x40   // NX_DEVICERALTKEYMASK

    var onDown: (() -> Void)?
    var onUp: (() -> Void)?
    var onEscape: (() -> Void)?
    static let escapeKeyCode: Int64 = 53
    private var tap: CFMachPort?
    private var isDown = false

    /// Returns false if the tap could not be created (permission missing).
    func start() -> Bool {
        let mask = CGEventMask(1 << CGEventType.flagsChanged.rawValue) | CGEventMask(1 << CGEventType.keyDown.rawValue)
        let callback: CGEventTapCallBack = { _, type, event, refcon in
            let me = Unmanaged<HotkeyMonitor>.fromOpaque(refcon!).takeUnretainedValue()
            if type == .tapDisabledByTimeout || type == .tapDisabledByUserInput {
                if let t = me.tap { CGEvent.tapEnable(tap: t, enable: true) }
            } else if type == .keyDown {
                if event.getIntegerValueField(.keyboardEventKeycode) == HotkeyMonitor.escapeKeyCode {
                    DispatchQueue.main.async { me.onEscape?() }
                }
            } else if event.getIntegerValueField(.keyboardEventKeycode) == HotkeyMonitor.rightOptionKeyCode {
                let down = event.flags.rawValue & HotkeyMonitor.rightOptionDeviceMask != 0
                if down != me.isDown {
                    me.isDown = down
                    DispatchQueue.main.async { down ? me.onDown?() : me.onUp?() }
                }
            }
            return Unmanaged.passUnretained(event)
        }
        guard let t = CGEvent.tapCreate(tap: .cgSessionEventTap, place: .headInsertEventTap, options: .listenOnly,
                                        eventsOfInterest: mask, callback: callback,
                                        userInfo: Unmanaged.passUnretained(self).toOpaque()) else { return false }
        tap = t
        let src = CFMachPortCreateRunLoopSource(nil, t, 0)
        CFRunLoopAddSource(CFRunLoopGetMain(), src, .commonModes)
        CGEvent.tapEnable(tap: t, enable: true)
        return true
    }
}
