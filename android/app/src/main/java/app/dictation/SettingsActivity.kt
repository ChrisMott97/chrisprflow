package app.dictation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Backend URL and API token. The token is stored encrypted and never shown again. */
class SettingsActivity : Activity() {
    private lateinit var bubble: Switch
    private lateinit var insertion: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(pad, bars.top + pad, pad, bars.bottom + pad)
                insets
            }
        }
        val url = EditText(this).apply {
            setText(Settings.baseUrl(this@SettingsActivity))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        val hasToken = Settings.token(this) != null
        val token = EditText(this).apply {
            hint = if (hasToken) "Token saved. Type to replace" else "Android token"
            // The password type is already single line. setSingleLine() after it would remove the masking.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        bubble = Switch(this).apply {
            text = "Floating bubble"
            isChecked = Settings.bubbleEnabled(this@SettingsActivity)
            setPadding(0, pad, 0, pad)
            setOnCheckedChangeListener { _, on ->
                Settings.setBubbleEnabled(this@SettingsActivity, on)
                if (on && !android.provider.Settings.canDrawOverlays(this@SettingsActivity)) {
                    // Chris grants "Appear on top" here; onResume starts the bubble when he returns.
                    startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                } else {
                    OverlayService.sync(this@SettingsActivity)
                }
            }
        }
        insertion = Button(this).apply {
            setOnClickListener {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val save = Button(this).apply {
            text = "Save"
            setOnClickListener {
                Settings.setBaseUrl(this@SettingsActivity, url.text.toString())
                if (token.text.isNotBlank()) Settings.setToken(this@SettingsActivity, token.text.toString())
                finish()
            }
        }
        root.addView(TextView(this).apply { text = "Backend URL" })
        root.addView(url, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(TextView(this).apply { text = "Token"; setPadding(0, pad, 0, 0) })
        root.addView(token, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(bubble, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(insertion, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(save, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // Back from the permission screen without granting: the switch must not claim the bubble is on.
        if (bubble.isChecked && !android.provider.Settings.canDrawOverlays(this)) {
            bubble.isChecked = false
        }
        OverlayService.sync(this)
        insertion.text =
            if (InsertionService.enabled) "Insert into fields: on" else "Insert into fields: off (open Accessibility)"
    }
}
