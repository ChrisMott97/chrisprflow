package app.dictation

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Wav {
    const val SAMPLE_RATE = 16_000

    /** Wraps 16 kHz mono 16-bit PCM in a 44-byte WAV header. */
    fun wrap(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // mono
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2) // byte rate
        header.putShort(2) // block align
        header.putShort(16) // bits per sample
        header.put("data".toByteArray())
        header.putInt(pcm.size)
        return header.array() + pcm
    }

    fun durationSeconds(pcm: ByteArray): Double = pcm.size / 2.0 / SAMPLE_RATE
}
