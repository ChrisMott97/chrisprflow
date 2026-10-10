package app.dictation

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val recorder = Recorder()
    private val worker = Executors.newSingleThreadExecutor()
    private var recording = false
    private var busy = false

    private lateinit var recordButton: Button
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var copyButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Edge-to-edge is forced at targetSdk 35+, so keep clear of the system bars.
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(pad, bars.top + pad, pad, bars.bottom + pad)
                insets
            }
        }
        recordButton = Button(this).apply {
            text = "Record"
            textSize = 22f
            setOnClickListener { onRecordTapped() }
        }
        status = TextView(this).apply {
            text = "Ready"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }
        result = TextView(this).apply {
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextIsSelectable(true)
        }
        copyButton = Button(this).apply {
            text = "Copy"
            isEnabled = false
            setOnClickListener { copy(result.text.toString()) }
        }
        root.addView(recordButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(result, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(copyButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)
    }

    override fun onDestroy() {
        if (recording) recorder.stop()
        worker.shutdown()
        super.onDestroy()
    }

    private fun onRecordTapped() {
        if (busy) return
        if (recording) {
            stopAndUpload()
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        } else {
            startRecording()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        if (code == REQ_MIC) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startRecording()
            else status.text = "Microphone permission denied"
        }
    }

    private fun startRecording() {
        try {
            recorder.start()
        } catch (e: Exception) {
            status.text = "Cannot record: ${e.message}"
            return
        }
        recording = true
        recordButton.text = "Stop"
        status.text = "Recording…"
    }

    private fun stopAndUpload() {
        val pcm = recorder.stop()
        recording = false
        recordButton.text = "Record"
        if (Wav.durationSeconds(pcm) < MIN_SECONDS) {
            status.text = "Too short, discarded"
            return
        }
        busy = true
        recordButton.isEnabled = false
        status.text = "Transcribing…"
        val wav = Wav.wrap(pcm)
        worker.execute {
            val outcome = DictationApi.dictate(wav)
            if (outcome is DictateResult.Retryable) saveForRetry(wav)
            runOnUiThread { show(outcome) }
        }
    }

    private fun show(outcome: DictateResult) {
        busy = false
        recordButton.isEnabled = true
        when (outcome) {
            is DictateResult.Ok -> if (outcome.empty) {
                status.text = "No speech found"
            } else {
                result.text = outcome.text
                copyButton.isEnabled = true
                copy(outcome.text)
                status.text = if (outcome.cleaned) "Copied to clipboard" else "Copied (cleanup skipped)"
            }
            is DictateResult.Fatal -> status.text = "Error: ${outcome.message}"
            is DictateResult.Retryable -> status.text = "Error: ${outcome.message}. Audio saved."
        }
    }

    /** Keeps the WAV in app storage. Retrying comes in D2. */
    private fun saveForRetry(wav: ByteArray) {
        val dir = File(filesDir, "pending").apply { mkdirs() }
        File(dir, "${System.currentTimeMillis()}.wav").writeBytes(wav)
    }

    private fun copy(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dictation", text))
    }

    private companion object {
        const val REQ_MIC = 1
        const val MIN_SECONDS = 0.5
    }
}
