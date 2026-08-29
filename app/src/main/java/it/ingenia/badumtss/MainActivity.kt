package it.ingenia.badumtss

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import it.ingenia.badumtss.core.AppState
import it.ingenia.badumtss.core.Jingle
import it.ingenia.badumtss.core.ListenerService
import it.ingenia.badumtss.ui.BaDumTssTheme
import it.ingenia.badumtss.ui.ControlScreen

class MainActivity : ComponentActivity() {

    private var pendingStart = false

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok && pendingStart) {
            pendingStart = false
            askNotificationsThenStart()
        } else {
            pendingStart = false
            AppState.lastReason.value = "serve il permesso microfono"
        }
    }

    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // La notifica può essere rifiutata: il servizio parte comunque, resta solo meno visibile.
        ListenerService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BaDumTssTheme {
                ControlScreen(
                    onToggleListening = { start -> if (start) requestStart() else stopListening() },
                    onFire = ::fireNow
                )
            }
        }
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestStart() {
        if (!hasMic()) {
            pendingStart = true
            askMic.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        askNotificationsThenStart()
    }

    private fun askNotificationsThenStart() {
        val needsNotif = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsNotif) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ListenerService.start(this)
        }
    }

    private fun stopListening() = ListenerService.stop(this)

    private fun fireNow(jingle: Jingle) = ListenerService.fire(this, jingle)
}
