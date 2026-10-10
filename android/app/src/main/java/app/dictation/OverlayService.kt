package app.dictation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Draws the floating bubble over other apps while an editable field has focus, as reported by
 * [InsertionService]. The bubble mirrors [DictationState]: idle, recording, processing. Tap starts or stops [RecordingService]; drag moves it.
 * Having a visible overlay window is what lets this app start the microphone service from the background.
 */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: BubbleView? = null
    private lateinit var params: WindowManager.LayoutParams
    private val listener: () -> Unit = { bubble?.post { render() } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubble == null) {
            if (!canShow(this)) {
                stopSelf()
                return START_NOT_STICKY
            }
            ensureChannel()
            startForeground(
                NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            show()
        }
        return START_STICKY
    }

    private fun show() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        val size = (BUBBLE_DP * d).toInt()
        val view = BubbleView(this).apply {
            elevation = 6 * d
            contentDescription = "Dictate"
        }
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(K_X, 0)
            y = prefs.getInt(K_Y, (200 * d).toInt())
        }
        view.setOnTouchListener(
            DragTapListener(
                onTap = ::onTap,
                onMove = { dx, dy -> move(dx, dy) },
                onDone = { prefs.edit().putInt(K_X, params.x).putInt(K_Y, params.y).apply() },
                slop = 8 * d,
            ),
        )
        bubble = view
        wm.addView(view, params)
        DictationState.add(listener)
        render()
    }

    private fun move(dx: Float, dy: Float) {
        val v = bubble ?: return
        val dm = resources.displayMetrics
        params.x = (params.x + dx).toInt().coerceIn(0, dm.widthPixels - params.width)
        params.y = (params.y + dy).toInt().coerceIn(0, dm.heightPixels - params.height)
        wm.updateViewLayout(v, params)
    }

    private fun onTap() {
        when (DictationState.phase) {
            Phase.RECORDING -> RecordingService.stop(this)
            Phase.PROCESSING -> Unit
            Phase.IDLE ->
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    RecordingService.start(this)
                } else {
                    startActivity(
                        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
        }
    }

    private fun render() {
        val view = bubble ?: return
        val phase = DictationState.phase
        view.setPhase(phase)
        // Shown only while an editable field has focus, and until a dictation in progress is finished.
        // With the accessibility service off, focus is unknown and the bubble stays up.
        val show = phase != Phase.IDLE || InsertionService.editableFocused != false
        view.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        DictationState.remove(listener)
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH, "Bubble", NotificationManager.IMPORTANCE_MIN),
        )
    }

    private fun notification(): Notification = Notification.Builder(this, CH)
        .setSmallIcon(R.drawable.ic_mic)
        .setContentTitle("Dictation bubble is on")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 2, Intent(this, SettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    /** Distinguishes a tap from a drag by movement past [slop] pixels. */
    private class DragTapListener(
        val onTap: () -> Unit,
        val onMove: (Float, Float) -> Unit,
        val onDone: () -> Unit,
        val slop: Float,
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var dragging = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; lastX = e.rawX; lastY = e.rawY; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && Math.hypot((e.rawX - downX).toDouble(), (e.rawY - downY).toDouble()) > slop) {
                        dragging = true
                    }
                    if (dragging) onMove(e.rawX - lastX, e.rawY - lastY)
                    lastX = e.rawX; lastY = e.rawY
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) onDone() else { v.performClick(); onTap() }
                }
            }
            return true
        }
    }

    companion object {
        private const val PREFS = "bubble"
        private const val K_X = "x"
        private const val K_Y = "y"
        private const val CH = "bubble"
        private const val NOTIF_ID = 3
        private const val BUBBLE_DP = 56

        fun canShow(ctx: Context) =
            Settings.bubbleEnabled(ctx) && android.provider.Settings.canDrawOverlays(ctx)

        /** Starts or stops the bubble to match the setting and the overlay permission. */
        fun sync(ctx: Context) {
            val i = Intent(ctx, OverlayService::class.java)
            if (canShow(ctx)) ctx.startForegroundService(i) else ctx.stopService(i)
        }
    }
}
