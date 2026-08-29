package it.ingenia.badumtss.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.random.Random

/**
 * Tiene i campioni già decodificati in memoria: caricarli a runtime aggiungerebbe
 * ~200 ms, che è esattamente quello che rovina la battuta.
 *
 * Espone anche [mutedUntil]: finché il jingle suona il rilevatore deve ignorare il
 * microfono, altrimenti l'audio rientra, viene classificato come risata e si innesca
 * un loop infinito.
 */
class JinglePlayer(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<Jingle, Int>()
    private val durations = HashMap<Jingle, Int>()
    @Volatile private var loaded = 0
    @Volatile var mutedUntil = 0L
        private set

    /** Millisecondi di coda dopo il jingle prima di riaprire il microfono. */
    var tailMs = 350

    init {
        val files = JingleFactory.ensureJingles(context)
        pool.setOnLoadCompleteListener { _, _, status -> if (status == 0) loaded++ }
        files.forEach { jf ->
            ids[jf.jingle] = pool.load(jf.file.absolutePath, 1)
            durations[jf.jingle] = jf.durationMs
        }
    }

    val isReady: Boolean get() = loaded >= ids.size

    private val handler = Handler(Looper.getMainLooper())

    fun play(jingle: Jingle, volume: Float = 1f, attempt: Int = 0) {
        val id = ids[jingle] ?: return
        val stream = pool.play(id, volume, volume, 1, 0, 1f)
        if (stream == 0) {
            // Il campione non ha ancora finito di caricarsi: riprova a breve.
            if (attempt < 6) handler.postDelayed({ play(jingle, volume, attempt + 1) }, 200)
            return
        }
        val dur = durations[jingle] ?: 2000
        mutedUntil = SystemClock.elapsedRealtime() + dur + tailMs
    }

    fun playRandom(volume: Float = 1f) {
        val pick = Jingle.entries[Random.nextInt(Jingle.entries.size)]
        play(pick, volume)
    }

    fun isMuted(): Boolean = SystemClock.elapsedRealtime() < mutedUntil

    fun release() {
        handler.removeCallbacksAndMessages(null)
        pool.release()
    }
}
