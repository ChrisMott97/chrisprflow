package app.dictation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors

/**
 * Microphone foreground service. START records, STOP (or the 10 minute cap) ends the recording,
 * trims silence, uploads, puts the text on the clipboard and posts a notification. After a
 * successful upload it retries recordings saved by earlier failures.
 */
class RecordingService : Service() {
    private val recorder = Recorder()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val capRunnable = Runnable { finishRecording() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finishRecording()
            else -> startRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (DictationState.phase != Phase.IDLE) return
        ensureChannels()
        startForeground(
            ONGOING_ID, ongoing("Recording…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        try {
            recorder.start()
        } catch (e: Exception) {
            DictationState.update(Phase.IDLE, "Cannot record: ${e.message}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        DictationState.update(Phase.RECORDING, "Recording…")
        main.postDelayed(capRunnable, MAX_RECORDING_MS)
    }

    private fun finishRecording() {
        if (DictationState.phase != Phase.RECORDING) return
        main.removeCallbacks(capRunnable)
        val pcm = recorder.stop()
        if (Wav.durationSeconds(pcm) < MIN_SECONDS) {
            DictationState.update(Phase.IDLE, "Too short, discarded")
            endService()
            return
        }
        DictationState.update(Phase.PROCESSING, "Transcribing…")
        notify(ONGOING_ID, ongoing("Transcribing…"))
        worker.execute {
            process(pcm)
            main.post { endService() }
        }
    }

    private fun process(pcm: ByteArray) {
        val flags = try {
            SileroVad.classify(this, pcm)
        } catch (e: Throwable) {
            null // VAD failed: send the audio untrimmed rather than lose it.
        }
        if (flags != null && flags.none { it }) {
            DictationState.update(Phase.IDLE, "No speech found")
            postResult("No speech found")
            return
        }
        val trimmed = if (flags != null) SilenceTrimmer.trim(pcm, flags) else pcm
        val wav = Wav.wrap(trimmed)
        when (val outcome = DictationApi.dictate(this, wav)) {
            is DictateResult.Ok -> {
                if (outcome.empty) {
                    DictationState.update(Phase.IDLE, "No speech found")
                    postResult("No speech found")
                } else {
                    copy(outcome.text)
                    DictationState.update(
                        Phase.IDLE,
                        if (outcome.cleaned) "Copied to clipboard" else "Copied (cleanup skipped)",
                        outcome.text,
                    )
                    postResult(outcome.text)
                }
                retryPending()
            }
            is DictateResult.Fatal -> {
                DictationState.update(Phase.IDLE, "Error: ${outcome.message}")
                postResult("Error: ${outcome.message}")
            }
            is DictateResult.Retryable -> {
                saveForRetry(wav)
                DictationState.update(Phase.IDLE, "Error: ${outcome.message}. Audio saved.")
                postResult("Error: ${outcome.message}. Audio saved for retry.")
            }
        }
    }

    /** Sends saved recordings, oldest first. Stops at the first one that still cannot be sent. */
    private fun retryPending() {
        val files = pendingDir().listFiles { f -> f.extension == "wav" }?.sortedBy { it.name } ?: return
        for (f in files) {
            when (val outcome = DictationApi.dictate(this, f.readBytes())) {
                is DictateResult.Ok -> {
                    f.delete()
                    if (!outcome.empty) {
                        // Not the clipboard: that holds the newer dictation.
                        postResult("Recovered: ${outcome.text}", id = nextId())
                    }
                }
                is DictateResult.Fatal -> f.delete()
                is DictateResult.Retryable -> return
            }
        }
    }

    private fun saveForRetry(wav: ByteArray) {
        File(pendingDir().apply { mkdirs() }, "${System.currentTimeMillis()}.wav").writeBytes(wav)
    }

    private fun pendingDir() = File(filesDir, "pending")

    private fun endService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        main.removeCallbacks(capRunnable)
        if (DictationState.phase == Phase.RECORDING) recorder.stop()
        if (DictationState.phase != Phase.PROCESSING) DictationState.update(Phase.IDLE)
        worker.shutdown()
        super.onDestroy()
    }

    private fun copy(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dictation", text))
    }

    private fun ensureChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, "Recording", NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "Results", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun open() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ongoing(text: String): Notification {
        val b = Notification.Builder(this, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Dictation")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open())
        if (DictationState.phase == Phase.RECORDING || text.startsWith("Recording")) {
            val stop = PendingIntent.getService(
                this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(Notification.Action.Builder(null, "Stop", stop).build())
        }
        return b.build()
    }

    private fun postResult(text: String, id: Int = RESULT_ID) {
        notify(
            id,
            Notification.Builder(this, CH_RESULT)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle("Dictation")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(open())
                .build(),
        )
    }

    private fun notify(id: Int, n: Notification) {
        getSystemService(NotificationManager::class.java).notify(id, n)
    }

    private fun nextId() = (System.currentTimeMillis() % 100_000).toInt() + 100

    companion object {
        const val ACTION_START = "app.dictation.START"
        const val ACTION_STOP = "app.dictation.STOP"
        private const val CH_ONGOING = "ongoing"
        private const val CH_RESULT = "result"
        private const val ONGOING_ID = 1
        private const val RESULT_ID = 2
        private const val MIN_SECONDS = 0.5
        const val MAX_RECORDING_MS = 10 * 60 * 1000L

        fun start(ctx: Context) {
            ctx.startForegroundService(
                Intent(ctx, RecordingService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}
