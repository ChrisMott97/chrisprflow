package app.dictation

import android.content.Context
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate

/** Runs Silero VAD over a finished recording and returns one speech flag per 512-sample frame. */
object SileroVad {
    fun classify(ctx: Context, pcm: ByteArray): BooleanArray {
        val frames = pcm.size / 2 / SilenceTrimmer.FRAME
        val flags = BooleanArray(frames)
        // No smoothing: silenceDurationMs and speechDurationMs of 0 give the raw per-frame decision.
        VadSilero(
            ctx,
            sampleRate = SampleRate.SAMPLE_RATE_16K,
            frameSize = FrameSize.FRAME_SIZE_512,
            mode = Mode.NORMAL,
            silenceDurationMs = 0,
            speechDurationMs = 0,
        ).use { vad ->
            val frame = ByteArray(SilenceTrimmer.FRAME * 2)
            for (f in 0 until frames) {
                System.arraycopy(pcm, f * frame.size, frame, 0, frame.size)
                flags[f] = vad.isSpeech(frame)
            }
        }
        return flags
    }
}
