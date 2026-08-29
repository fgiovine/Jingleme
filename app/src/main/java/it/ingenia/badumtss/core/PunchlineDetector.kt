package it.ingenia.badumtss.core

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Riconosce la forma di una battuta senza capire le parole: qualcuno alza la voce,
 * poi si ferma. È la pausa dopo la punchline, quella in cui in TV parte il rullo.
 *
 * Funziona interamente sull'inviluppo di energia, quindi costa quasi nulla e non
 * richiede modelli. Non sa distinguere una battuta da una frase enfatica qualunque:
 * per quello serve il riconoscimento del parlato.
 */
class PunchlineDetector {

    /** 0 = solo battute urlate, 1 = si accende quasi sempre. */
    @Volatile var sensitivity = 0.5f

    /** Quanto silenzio serve dopo la frase perché scatti il jingle. */
    @Volatile var holdMs = 550

    private var noiseFloorDb = -55f
    private var speechMs = 0
    private var silenceMs = 0
    private var peakDb = -120f
    private var armed = false

    var lastLevelDb = -90f
        private set

    /** Esposti per il pannello diagnostico: senza numeri la taratura è alla cieca. */
    var floorDb = -55f
        private set
    var isArmed = false
        private set

    fun reset() {
        speechMs = 0; silenceMs = 0; peakDb = -120f; armed = false; isArmed = false
    }

    /** @return true se questo frame chiude una battuta. */
    fun process(frame: ShortArray): Boolean {
        val db = levelDb(frame)
        lastLevelDb = db

        // Il fondo di rumore scende in fretta e risale piano: si adatta alla stanza
        // senza farsi trascinare in alto da chi parla.
        noiseFloorDb += if (db < noiseFloorDb) (db - noiseFloorDb) * 0.20f
        else (db - noiseFloorDb) * 0.0006f

        floorDb = noiseFloorDb

        // Soglie basse di proposito: la catena vocale di Android comprime la dinamica,
        // quindi tra silenzio e voce parlata restano spesso solo 10-12 dB, non 20.
        val speechThresh = noiseFloorDb + (10f - 5f * sensitivity)
        val peakThresh = noiseFloorDb + (15f - 8f * sensitivity)
        val minBurstMs = 200

        // Sotto questo livello assoluto non c'è voce, c'è una stanza vuota.
        val floorGuard = -52f

        if (db > speechThresh) {
            speechMs += AudioEngine.FRAME_MS
            silenceMs = 0
            peakDb = max(peakDb, db)
            if (speechMs >= minBurstMs && peakDb >= peakThresh && peakDb > floorGuard) {
                armed = true
                isArmed = true
            }
        } else {
            silenceMs += AudioEngine.FRAME_MS
            if (silenceMs > 150) speechMs = 0
            if (armed && silenceMs >= holdMs) {
                reset()
                return true
            }
            // Se il silenzio si prolunga troppo la battuta è passata: nessuno ride più.
            if (silenceMs > holdMs + 2000) reset()
        }
        return false
    }

    private fun levelDb(frame: ShortArray): Float {
        var sum = 0.0
        for (s in frame) {
            val v = s / 32768.0
            sum += v * v
        }
        val rms = sqrt(sum / frame.size)
        return (20.0 * log10(max(rms, 1e-7))).toFloat()
    }
}
