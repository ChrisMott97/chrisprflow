package app.dictation

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shows the state of [RecordingService] and starts or stops it. Also asks for permissions. */
class MainActivity : Activity() {
    private lateinit var recordButton: Button
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var copyButton: Button
    private val listener: () -> Unit = { runOnUiThread { render() } }

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
            setOnClickListener { copy(result.text.toString()) }
        }
        val settings = Button(this).apply {
            text = "Settings"
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        root.addView(recordButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(result, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(copyButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(settings, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        DictationState.add(listener)
        render()
    }

    override fun onResume() {
        super.onResume()
        OverlayService.sync(this)
    }

    override fun onStop() {
        DictationState.remove(listener)
        super.onStop()
    }

    private fun render() {
        val phase = DictationState.phase
        recordButton.text = if (phase == Phase.RECORDING) "Stop" else "Record"
        recordButton.isEnabled = phase != Phase.PROCESSING
        status.text = DictationState.status
        result.text = DictationState.lastResult
        copyButton.isEnabled = DictationState.lastResult.isNotEmpty()
    }

    private fun onRecordTapped() {
        if (DictationState.phase == Phase.RECORDING) {
            RecordingService.stop(this)
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), REQ_MIC,
            )
        } else {
            RecordingService.start(this)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        if (code == REQ_MIC) {
            // Notifications are optional; the microphone is not.
            val mic = perms.indexOf(Manifest.permission.RECORD_AUDIO)
            if (mic >= 0 && results[mic] == PackageManager.PERMISSION_GRANTED) RecordingService.start(this)
            else status.text = "Microphone permission denied"
        }
    }

    private fun copy(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dictation", text))
    }

    private companion object {
        const val REQ_MIC = 1
    }
}
