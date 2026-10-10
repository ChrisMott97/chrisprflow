package app.dictation

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream

/** Records 16 kHz mono 16-bit PCM until [stop] is called. Needs RECORD_AUDIO granted. */
class Recorder {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()

    @Volatile
    private var running = false

    @SuppressLint("MissingPermission")
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(
            Wav.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, Wav.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 4,
        )
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "microphone unavailable" }
        record = rec
        pcm.reset()
        running = true
        rec.startRecording()
        thread = Thread {
            val buf = ByteArray(minBuf)
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    synchronized(pcm) { pcm.write(buf, 0, n) }
                    DictationState.level = rms(buf, n)
                }
            }
            DictationState.level = 0f
        }.also { it.start() }
    }

    private fun rms(buf: ByteArray, n: Int): Float {
        var sum = 0.0
        val samples = n / 2
        for (i in 0 until samples) {
            val s = ((buf[2 * i + 1].toInt() shl 8) or (buf[2 * i].toInt() and 0xFF)).toShort()
            sum += s * s.toDouble()
        }
        return if (samples == 0) 0f else (Math.sqrt(sum / samples) / Short.MAX_VALUE).toFloat()
    }

    /** Stops recording and returns the raw PCM bytes. */
    fun stop(): ByteArray {
        running = false
        thread?.join()
        record?.run { stop(); release() }
        record = null
        thread = null
        return synchronized(pcm) { pcm.toByteArray() }
    }
}
