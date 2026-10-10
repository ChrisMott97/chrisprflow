package app.dictation

/**
 * Cuts every silence longer than [MAX_SILENCE_SAMPLES] down to [KEEP_SILENCE_SAMPLES].
 * Same rule as the Mac client: over 1 s becomes 0.3 s.
 * Silence at the very start and end is cut harder, to [EDGE_SILENCE_SAMPLES] whatever its length:
 * Whisper tends to invent a closing "Thank you" when a recording ends in silence.
 */
object SilenceTrimmer {
    const val FRAME = 512
    const val MAX_SILENCE_SAMPLES = Wav.SAMPLE_RATE // 1 s
    const val KEEP_SILENCE_SAMPLES = Wav.SAMPLE_RATE * 3 / 10 // 0.3 s
    const val EDGE_SILENCE_SAMPLES = Wav.SAMPLE_RATE / 5 // 0.2 s

    /** [speech] has one flag per [FRAME] samples; samples past the last full frame take the last flag. */
    fun trim(pcm: ByteArray, speech: BooleanArray): ByteArray {
        val total = pcm.size / 2
        if (total == 0 || speech.isEmpty()) return pcm
        fun isSpeech(sample: Int) = speech[minOf(sample / FRAME, speech.size - 1)]

        val out = java.io.ByteArrayOutputStream(pcm.size)
        var i = 0
        while (i < total) {
            val s = isSpeech(i)
            var j = i
            while (j < total && isSpeech(j) == s) j++
            val len = j - i
            val leading = i == 0
            val trailing = j == total
            if (!s && (leading || trailing) && len > EDGE_SILENCE_SAMPLES) {
                // Keep the part next to the speech. A recording that is all silence keeps its start.
                val from = if (leading && !trailing) j - EDGE_SILENCE_SAMPLES else i
                out.write(pcm, from * 2, EDGE_SILENCE_SAMPLES * 2)
            } else if (!s && len > MAX_SILENCE_SAMPLES) {
                val half = KEEP_SILENCE_SAMPLES / 2
                out.write(pcm, i * 2, half * 2)
                out.write(pcm, (j - half) * 2, half * 2)
            } else {
                out.write(pcm, i * 2, len * 2)
            }
            i = j
        }
        return out.toByteArray()
    }
}
