package it.ingenia.badumtss

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import it.ingenia.badumtss.core.AppState

class App : Application() {

    companion object {
        const val CHANNEL_ID = "listening"
    }

    override fun onCreate() {
        super.onCreate()
        AppState.load(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Ascolto attivo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mostra quando il microfono è aperto."
                setShowBadge(false)
            }
        )
    }
}
