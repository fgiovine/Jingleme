package it.ingenia.badumtss.core

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import it.ingenia.badumtss.App
import it.ingenia.badumtss.MainActivity

/**
 * L'ascolto continuo deve stare in un foreground service: da Android 14 il microfono
 * in background senza notifica visibile non è concesso.
 */
class ListenerService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_FIRE = "fire"
        const val EXTRA_JINGLE = "jingle"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            val i = Intent(context, ListenerService::class.java).setAction(ACTION_START)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ListenerService::class.java).setAction(ACTION_STOP))
        }

        fun fire(context: Context, jingle: Jingle) {
            val i = Intent(context, ListenerService::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_JINGLE, jingle.name)
            context.startService(i)
        }
    }

    private var engine: AudioEngine? = null
    private var player: JinglePlayer? = null
    private var laugh: LaughDetector? = null
    private val punchline = PunchlineDetector()

    private val window = FloatArray(LaughDetector.WINDOW)
    private var windowFilled = 0
    private var framesSinceInference = 0
    private var framesSinceLevel = 0
    private var lastFire = 0L
    private val idleStop = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_FIRE -> {
                ensurePlayer()
                val j = runCatching { Jingle.valueOf(intent.getStringExtra(EXTRA_JINGLE)!!) }
                    .getOrDefault(Jingle.RIMSHOT)
                player?.play(j, AppState.settings.value.volume)
                AppState.lastReason.value = "manuale"
                AppState.lastFireAt.value = SystemClock.elapsedRealtime()
                if (!AppState.listening.value) idleStop.postDelayed(::stopSelf, 8_000)
                return START_NOT_STICKY
            }
            else -> {
                idleStop.removeCallbacksAndMessages(null)
                startListening()
            }
        }
        return START_STICKY
    }

    private fun ensurePlayer() {
        if (player == null) player = JinglePlayer(this)
    }

    private fun startListening() {
        if (engine != null) return
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification("In ascolto"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        ensurePlayer()

        laugh = LaughDetector.create(this)
        AppState.modelLoaded.value = laugh != null

        val e = AudioEngine(::onFrame)
        if (!e.start()) {
            AppState.lastReason.value = "microfono non disponibile"
            stopSelf()
            return
        }
        engine = e
        AppState.listening.value = true
    }

    private fun stopListening() {
        engine?.stop(); engine = null
        laugh?.close(); laugh = null
        player?.release(); player = null
        AppState.listening.value = false
        AppState.levelDb.value = -90f
        AppState.armed.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // ------------------------------------------------------------- rilevamento

    private fun onFrame(frame: ShortArray) {
        val s = AppState.settings.value
        val p = player ?: return
        val now = SystemClock.elapsedRealtime()

        // Il jingle sta suonando: microfono chiuso, altrimenti si autoinnesca.
        if (p.isMuted()) {
            punchline.reset()
            windowFilled = 0
            return
        }

        punchline.sensitivity = s.sensitivity
        punchline.holdMs = s.holdMs

        val pauseHit = punchline.process(frame)

        if (++framesSinceLevel >= 3) {
            framesSinceLevel = 0
            AppState.levelDb.value = punchline.lastLevelDb
            AppState.floorDb.value = punchline.floorDb
            AppState.armed.value = punchline.isArmed
        }

        val inCooldown = now - lastFire < s.cooldownMs
        if (s.mode == TriggerMode.MANUAL || inCooldown) {
            if (inCooldown) appendWindow(frame)
            return
        }

        if (pauseHit && (s.mode == TriggerMode.AUTO || s.mode == TriggerMode.PAUSE)) {
            fire("pausa dopo la battuta", now)
            return
        }

        appendWindow(frame)

        val detector = laugh ?: return
        if (s.mode != TriggerMode.AUTO && s.mode != TriggerMode.LAUGH) return
        if (windowFilled < LaughDetector.WINDOW) return
        if (++framesSinceInference < 8) return          // inferenza ogni ~256 ms
        framesSinceInference = 0

        // Soglia inversa rispetto alla sensibilità: più sensibile, meno prove servono.
        val threshold = 0.62f - 0.30f * s.sensitivity
        if (detector.isLaughter(window, threshold)) {
            AppState.laughScore.value = detector.lastScore
            fire("risata", now)
        } else {
            AppState.laughScore.value = detector.lastScore
        }
    }

    private fun appendWindow(frame: ShortArray) {
        val n = frame.size
        System.arraycopy(window, n, window, 0, window.size - n)
        val off = window.size - n
        for (i in 0 until n) window[off + i] = frame[i] / 32768f
        if (windowFilled < window.size) windowFilled += n
    }

    private fun fire(reason: String, now: Long) {
        val s = AppState.settings.value
        val p = player ?: return
        when (s.choice) {
            JingleChoice.RIMSHOT -> p.play(Jingle.RIMSHOT, s.volume)
            JingleChoice.LAUGH -> p.play(Jingle.LAUGH, s.volume)
            JingleChoice.APPLAUSE -> p.play(Jingle.APPLAUSE, s.volume)
            JingleChoice.RANDOM -> p.playRandom(s.volume)
        }
        lastFire = now
        AppState.lastFireAt.value = now
        AppState.lastReason.value = reason
        punchline.reset()
        windowFilled = 0
    }

    // ----------------------------------------------------------- notifica

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ListenerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Ba-dum-tss")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Ferma", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        idleStop.removeCallbacksAndMessages(null)
        stopListening()
        super.onDestroy()
    }
}
