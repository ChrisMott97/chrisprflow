package app.dictation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Lets a test run the insertion paths without speaking:
 * `adb shell am broadcast -n app.dictation/.DebugInsertReceiver --es text "hello"`.
 * The result is the `insert app=... path=...` line in logcat (tag Dictation).
 */
class DebugInsertReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: return
        val pending = goAsync()
        Thread {
            InsertionService.insert(ctx.applicationContext, text)
            pending.finish()
        }.start()
    }
}
