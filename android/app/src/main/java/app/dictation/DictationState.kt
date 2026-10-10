package app.dictation

enum class Phase { IDLE, RECORDING, PROCESSING }

/** Process-wide view of what the recording service is doing, for the activity and the tile. */
object DictationState {
    @Volatile var phase = Phase.IDLE
        private set
    @Volatile var status = "Ready"
        private set
    @Volatile var lastResult = ""
        private set

    /** Live mic level, 0..1 (RMS). Read by the bubble each frame; changing it does not notify listeners. */
    @Volatile var level = 0f

    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    fun add(l: () -> Unit) { listeners.add(l) }
    fun remove(l: () -> Unit) { listeners.remove(l) }

    fun update(phase: Phase? = null, status: String? = null, result: String? = null) {
        if (phase != null) this.phase = phase
        if (status != null) this.status = status
        if (result != null) lastResult = result
        listeners.forEach { it() }
    }
}
