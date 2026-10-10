package app.dictation

import org.junit.Assert.assertEquals
import org.junit.Test

class SilenceTrimmerTest {
    private val frame = SilenceTrimmer.FRAME

    private fun pcm(frames: Int) = ByteArray(frames * frame * 2) { (it % 100).toByte() }
    private fun flags(vararg runs: Pair<Boolean, Int>) =
        runs.flatMap { (s, n) -> List(n) { s } }.toBooleanArray()

    @Test
    fun longSilenceBecomesThreeTenthsOfASecond() {
        // 1 s of speech, 2 s of silence, 1 s of speech.
        val speechFrames = 32
        val silenceFrames = 64
        val f = flags(true to speechFrames, false to silenceFrames, true to speechFrames)
        val out = SilenceTrimmer.trim(pcm(f.size), f)
        val expected = 2 * speechFrames * frame + SilenceTrimmer.KEEP_SILENCE_SAMPLES
        assertEquals(expected * 2, out.size)
    }

    @Test
    fun shortSilenceIsKept() {
        val f = flags(true to 10, false to 20, true to 10) // 20 frames is 0.64 s
        val p = pcm(f.size)
        assertEquals(p.size, SilenceTrimmer.trim(p, f).size)
    }

    @Test
    fun silenceOfExactlyOneSecondIsKept() {
        val f = flags(true to 4, false to 31, true to 4) // 31 frames is 15872 samples, under 16000
        val p = pcm(f.size)
        assertEquals(p.size, SilenceTrimmer.trim(p, f).size)
    }

    @Test
    fun leadingAndTrailingSilenceAreCutToTwoTenths() {
        val f = flags(false to 64, true to 10, false to 64)
        val out = SilenceTrimmer.trim(pcm(f.size), f)
        assertEquals((2 * SilenceTrimmer.EDGE_SILENCE_SAMPLES + 10 * frame) * 2, out.size)
    }

    @Test
    fun shortTrailingSilenceIsCutToo() {
        val f = flags(true to 10, false to 20) // 0.64 s of silence at the end
        val out = SilenceTrimmer.trim(pcm(f.size), f)
        assertEquals((10 * frame + SilenceTrimmer.EDGE_SILENCE_SAMPLES) * 2, out.size)
    }

    @Test
    fun trailingSilenceKeepsThePartNextToTheSpeech() {
        val f = flags(false to 20, true to 10, false to 20)
        val p = ByteArray(f.size * frame * 2) { (it / (frame * 2)).toByte() } // each frame holds its index
        val out = SilenceTrimmer.trim(p, f)
        val edge = SilenceTrimmer.EDGE_SILENCE_SAMPLES * 2
        assertEquals(19.toByte(), out[edge - 1]) // leading: the last silent frame before the speech
        assertEquals(30.toByte(), out[out.size - edge]) // trailing: the first silent frame after it
    }
}
