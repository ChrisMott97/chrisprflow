package app.dictation

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: tap to start recording, tap again to stop. */
class DictationTileService : TileService() {
    private val listener: () -> Unit = { refresh() }

    override fun onStartListening() {
        DictationState.add(listener)
        refresh()
    }

    override fun onStopListening() {
        DictationState.remove(listener)
    }

    override fun onClick() {
        when (DictationState.phase) {
            Phase.RECORDING -> RecordingService.stop(this)
            Phase.PROCESSING -> Unit
            Phase.IDLE ->
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    RecordingService.start(this)
                } else {
                    // Permission is asked for in the app; open it.
                    val pi = PendingIntent.getActivity(
                        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                    startActivityAndCollapse(pi)
                }
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        when (DictationState.phase) {
            Phase.IDLE -> { tile.state = Tile.STATE_INACTIVE; tile.subtitle = "Tap to dictate" }
            Phase.RECORDING -> { tile.state = Tile.STATE_ACTIVE; tile.subtitle = "Recording…" }
            Phase.PROCESSING -> { tile.state = Tile.STATE_UNAVAILABLE; tile.subtitle = "Transcribing…" }
        }
        tile.updateTile()
    }
}
