package app.dictation

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Backend URL and API token. The token is stored encrypted and never shown again. */
class SettingsActivity : Activity() {
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
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
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
        root.addView(save, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)
    }
}
