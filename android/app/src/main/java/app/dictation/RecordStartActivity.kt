package app.dictation

import android.app.Activity

/**
 * Invisible stepping stone for the tile. Android 16 refuses to start a microphone foreground service
 * straight from a tile tap, because RECORD_AUDIO is a while-in-use permission and the app is not
 * visible. A tile may launch an activity, and a resumed activity makes the app eligible, so this
 * one starts the service and closes. Recording carries on in the service after it is gone.
 */
class RecordStartActivity : Activity() {
    override fun onResume() {
        super.onResume()
        RecordingService.start(this)
        finish()
    }
}
