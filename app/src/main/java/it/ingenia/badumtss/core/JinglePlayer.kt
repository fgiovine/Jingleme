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
 * Ogni slot può avere più file. Quando ce ne sono, la scelta è casuale ma evita di
 * ripetere l'ultimo suonato: tre risate identiche di fila si notano subito.
 *
 * Espone [mutedUntil]: finché il jingle suona il rilevatore deve ignorare il
 * microfono, altrimenti l'audio rientra e si innesca un loop.
 */
class JinglePlayer(context: Context) {

    private data class Sample(val soundId: Int, val durationMs: Int)

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val samples = HashMap<Jingle, List<Sample>>()
    private val lastPlayed = HashMap<Jingle, Int>()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile var mutedUntil = 0L
        private set

    /** Millisecondi di coda dopo il jingle prima di riaprire il microfono. */
    var tailMs = 350

    init {
        JingleFactory.ensureJingles(context).forEach { (jingle, files) ->
            samples[jingle] = files.map { Sample(pool.load(it.file.absolutePath, 1), it.durationMs) }
        }
    }

    fun play(jingle: Jingle, volume: Float = 1f, attempt: Int = 0) {
        val pool0 = samples[jingle] ?: return
        if (pool0.isEmpty()) return

        val index = if (pool0.size == 1) 0 else {
            var i: Int
            do { i = Random.nextInt(pool0.size) } while (i == lastPlayed[jingle])
            i
        }
        val sample = pool0[index]

        val stream = pool.play(sample.soundId, volume, volume, 1, 0, 1f)
        if (stream == 0) {
            // Il campione non ha ancora finito di caricarsi: riprova a breve.
            if (attempt < 8) handler.postDelayed({ play(jingle, volume, attempt + 1) }, 200)
            return
        }
        lastPlayed[jingle] = index
        mutedUntil = SystemClock.elapsedRealtime() + sample.durationMs + tailMs
    }

    fun playRandom(volume: Float = 1f) {
        play(Jingle.entries[Random.nextInt(Jingle.entries.size)], volume)
    }

    fun isMuted(): Boolean = SystemClock.elapsedRealtime() < mutedUntil

    fun release() {
        handler.removeCallbacksAndMessages(null)
        pool.release()
    }
}
