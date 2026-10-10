package app.dictation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The bubble face. Idle: a mic. Recording: five bars that dance with the live mic level.
 * Processing: the mic dimmed inside a spinning arc. All three share one blue-teal family.
 * Animates only while recording or processing.
 */
class BubbleView(ctx: Context) : View(ctx) {
    private val mic: Drawable = ctx.getDrawable(R.drawable.ic_mic)!!
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val arc = RectF()
    private var smoothed = 0f
    private var phase: Phase = Phase.IDLE
    private var started = 0L

    fun setPhase(p: Phase) {
        if (p == phase) return
        phase = p
        started = System.currentTimeMillis()
        fill.color = when (p) {
            Phase.IDLE -> Color.parseColor("#455A64")
            Phase.RECORDING -> Color.parseColor("#00796B")
            Phase.PROCESSING -> Color.parseColor("#3F6E8C")
        }
        alpha = if (p == Phase.IDLE) 0.75f else 1f
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = min(w, h) / 2f
        c.drawCircle(w / 2, h / 2, r, fill)
        val t = (System.currentTimeMillis() - started) / 1000f
        when (phase) {
            Phase.IDLE -> drawMic(c, 255)
            Phase.RECORDING -> drawBars(c, t, r)
            Phase.PROCESSING -> drawSpinner(c, t, r)
        }
        if (phase != Phase.IDLE) postInvalidateOnAnimation()
    }

    private fun drawMic(c: Canvas, a: Int) {
        val pad = (width * 0.25f).toInt()
        mic.setBounds(pad, pad, width - pad, height - pad)
        mic.alpha = a
        mic.draw(c)
    }

    private fun drawBars(c: Canvas, t: Float, r: Float) {
        // Rise fast, fall slowly, so the bars read as a voice and not as noise.
        val target = (DictationState.level * 4f).coerceIn(0f, 1f)
        smoothed += (target - smoothed) * (if (target > smoothed) 0.5f else 0.12f)
        ink.strokeWidth = r * 0.17f
        val cx = width / 2f
        val cy = height / 2f
        val gap = r * 0.3f
        val maxHalf = r * 0.5f
        for (i in -2..2) {
            val wave = 0.5f + 0.5f * sin(t * 7f + i * 1.1f)
            val centre = 1f - 0.2f * kotlin.math.abs(i)
            val half = max(r * 0.08f, maxHalf * centre * (0.25f + 0.75f * smoothed * wave))
            c.drawLine(cx + i * gap, cy - half, cx + i * gap, cy + half, ink)
        }
    }

    private fun drawSpinner(c: Canvas, t: Float, r: Float) {
        drawMic(c, 130)
        ink.strokeWidth = r * 0.1f
        val inset = r * 0.2f
        arc.set(inset, inset, width - inset, height - inset)
        val start = (t * 360f * 0.9f) % 360f
        val sweep = 100f + 60f * sin(t * 2f * PI.toFloat())
        c.drawArc(arc, start, sweep, false, ink)
    }
}
