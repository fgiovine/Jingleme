package it.ingenia.badumtss.core

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/**
 * Legge il microfono in frame da 32 ms e li consegna al chiamante.
 *
 * VOICE_COMMUNICATION attiva la catena di pre-elaborazione vocale del dispositivo,
 * inclusa la cancellazione dell'eco: senza, il jingle che esce dallo speaker rientra
 * dal microfono e fa ripartire il rilevatore.
 */
class AudioEngine(private val onFrame: (ShortArray) -> Unit) {

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = 512          // 32 ms
        const val FRAME_MS = FRAME_SAMPLES * 1000 / SAMPLE_RATE
        private const val TAG = "AudioEngine"
    }

    private var record: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false

        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf * 4, FRAME_SAMPLES * 8)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }

        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }
        }
        // Il guadagno automatico va spento: comprimendo la dinamica appiattisce
        // proprio il salto di volume su cui si basa il rilevamento della battuta.
        if (AutomaticGainControl.isAvailable()) {
            agc = AutomaticGainControl.create(rec.audioSessionId)?.apply { enabled = false }
        }

        record = rec
        running = true
        rec.startRecording()

        thread = Thread({
            val buf = ShortArray(FRAME_SAMPLES)
            while (running) {
                val n = rec.read(buf, 0, FRAME_SAMPLES)
                if (n == FRAME_SAMPLES) {
                    onFrame(buf)
                } else if (n < 0) {
                    Log.w(TAG, "read error $n")
                    break
                }
            }
        }, "badumtss-capture").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        aec?.release(); aec = null
        ns?.release(); ns = null
        agc?.release(); agc = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
    }
}
