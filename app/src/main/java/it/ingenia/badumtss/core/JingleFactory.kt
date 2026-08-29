package it.ingenia.badumtss.core

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Genera i jingle in modo sintetico, così l'app suona subito senza dipendere da campioni
 * di terze parti (le risate delle sitcom americane sono materiale protetto).
 *
 * Se in app/src/main/assets/jingles/ trovi un file con lo stesso nome dello slot
 * (rimshot.wav, applause.wav, laugh.wav) quello ha la precedenza sulla sintesi.
 */
object JingleFactory {

    const val SR = 44100

    /** Restituisce i file pronti da caricare nel SoundPool, con la loro durata in ms. */
    fun ensureJingles(context: Context): List<JingleFile> {
        val dir = File(context.cacheDir, "jingles").apply { mkdirs() }
        return Jingle.entries.map { jingle ->
            val fromAssets = copyFromAssetsIfPresent(context, jingle, dir)
            if (fromAssets != null) return@map fromAssets

            val file = File(dir, "${jingle.slug}.wav")
            if (!file.exists() || file.length() < 1024) {
                val pcm = when (jingle) {
                    Jingle.RIMSHOT -> synthRimshot()
                    Jingle.APPLAUSE -> synthApplause()
                    Jingle.LAUGH -> synthCrowdLaugh()
                }
                writeWav(file, pcm)
            }
            JingleFile(jingle, file, durationMs(file))
        }
    }

    private fun copyFromAssetsIfPresent(context: Context, jingle: Jingle, dir: File): JingleFile? {
        val names = listOf("${jingle.slug}.wav", "${jingle.slug}.ogg", "${jingle.slug}.mp3")
        val available = runCatching { context.assets.list("jingles")?.toSet() ?: emptySet() }
            .getOrDefault(emptySet())
        val match = names.firstOrNull { it in available } ?: return null
        val out = File(dir, match)
        if (!out.exists() || out.length() == 0L) {
            context.assets.open("jingles/$match").use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
        }
        // Per i formati compressi non conosciamo la durata esatta: stima prudente.
        val ms = if (match.endsWith(".wav")) durationMs(out) else 3000
        return JingleFile(jingle, out, ms)
    }

    private fun durationMs(wav: File): Int {
        val frames = max(0L, wav.length() - 44) / 2
        return ((frames * 1000) / SR).toInt()
    }

    // ---------------------------------------------------------------- sintesi

    /** Il classico "ba-dum-tss": due colpi di rullante e un piatto. */
    private fun synthRimshot(): FloatArray {
        val out = FloatArray((SR * 1.9f).toInt())
        snare(out, atSec = 0.00f, gain = 0.90f)
        snare(out, atSec = 0.17f, gain = 0.85f)
        cymbal(out, atSec = 0.34f, gain = 0.75f, decay = 1.15f)
        return normalize(out, 0.89f)
    }

    /** Applauso: tanti battimani sparsi con densità che sale e poi scende. */
    private fun synthApplause(): FloatArray {
        val dur = 3.4f
        val out = FloatArray((SR * dur).toInt())
        val rnd = Random(4242)
        val claps = 900
        repeat(claps) {
            val t = rnd.nextFloat() * dur
            // densità: salita rapida, coda lunga
            val density = if (t < 0.35f) t / 0.35f else exp(-(t - 0.35f) / 1.6f)
            if (rnd.nextFloat() > density) return@repeat
            clap(out, t, gain = 0.10f + rnd.nextFloat() * 0.14f, seed = rnd.nextInt())
        }
        return normalize(out, 0.75f)
    }

    /**
     * Risata di gruppo: dodici "voci" di rumore filtrato, ognuna modulata in ampiezza
     * a 4-7 Hz. È un segnaposto credibile, non una laugh track da studio.
     */
    private fun synthCrowdLaugh(): FloatArray {
        val dur = 2.8f
        val out = FloatArray((SR * dur).toInt())
        val rnd = Random(1312)
        repeat(12) {
            val start = rnd.nextFloat() * 0.25f
            val rate = 4.2f + rnd.nextFloat() * 2.8f          // sillabe "ah-ah-ah" al secondo
            val centre = 380f + rnd.nextFloat() * 620f        // colore della voce
            val gain = 0.16f + rnd.nextFloat() * 0.10f
            val phase = rnd.nextFloat() * 6.283f
            val voice = BandNoise(centre * 0.5f, centre * 2.2f, rnd.nextInt())
            for (i in out.indices) {
                val t = i / SR.toFloat() - start
                val v = voice.next()
                if (t < 0f) continue
                val env = exp(-t / 1.5f) * min(1f, t / 0.08f)
                if (env < 0.002f) continue
                // modulazione sillabica, mai completamente a zero
                val syl = 0.25f + 0.75f * max(0f, sin(2f * PI.toFloat() * rate * t + phase))
                out[i] += v * 3f * env * syl * syl * gain
            }
        }
        return normalize(out, 0.72f)
    }

    // ------------------------------------------------------------- primitive

    private fun snare(out: FloatArray, atSec: Float, gain: Float) {
        val start = (atSec * SR).toInt()
        val len = (0.30f * SR).toInt()
        val noise = BandNoise(300f, 3800f, start + 7)
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= out.size) break
            val t = i / SR.toFloat()
            val skin = noise.next() * 4.0f * exp(-t / 0.075f)
            val body = sin(2f * PI.toFloat() * 185f * t) * exp(-t / 0.045f) * 0.95f
            val crack = sin(2f * PI.toFloat() * 330f * t) * exp(-t / 0.012f) * 0.35f
            out[idx] += (skin + body + crack) * gain
        }
    }

    private fun cymbal(out: FloatArray, atSec: Float, gain: Float, decay: Float) {
        val start = (atSec * SR).toInt()
        val len = ((decay + 0.2f) * SR).toInt()
        val noise = BandNoise(3000f, 11000f, start + 99)
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= out.size) break
            val t = i / SR.toFloat()
            // leggero shimmer inarmonico, come le campane di un piatto
            val shimmer = 1f + 0.12f * sin(2f * PI.toFloat() * 3.1f * t)
            out[idx] += noise.next() * 2.0f * exp(-t / (decay / 3f)) * shimmer * gain
        }
    }

    private fun clap(out: FloatArray, atSec: Float, gain: Float, seed: Int) {
        val start = (atSec * SR).toInt()
        val len = (0.05f * SR).toInt()
        val noise = BandNoise(800f, 5000f, seed)
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= out.size) break
            val t = i / SR.toFloat()
            out[idx] += noise.next() * 3f * exp(-t / 0.010f) * gain
        }
    }

    private fun normalize(buf: FloatArray, peak: Float): FloatArray {
        var maxAbs = 0f
        for (v in buf) maxAbs = max(maxAbs, kotlin.math.abs(v))
        if (maxAbs < 1e-6f) return buf
        val k = peak / maxAbs
        for (i in buf.indices) buf[i] *= k
        return buf
    }

    // ------------------------------------------------------------------ WAV

    private fun writeWav(file: File, pcm: FloatArray) {
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in pcm) {
            val s = (max(-1f, min(1f, v)) * 32767f).toInt().toShort()
            bytes.putShort(s)
        }
        val data = bytes.array()
        FileOutputStream(file).use { fos ->
            fos.write(wavHeader(data.size))
            fos.write(data)
        }
    }

    private fun wavHeader(dataSize: Int): ByteArray {
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray())
        b.putInt(36 + dataSize)
        b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray())
        b.putInt(16)
        b.putShort(1)              // PCM
        b.putShort(1)              // mono
        b.putInt(SR)
        b.putInt(SR * 2)           // byte rate
        b.putShort(2)              // block align
        b.putShort(16)             // bit depth
        b.put("data".toByteArray())
        b.putInt(dataSize)
        return b.array()
    }
}

/** Coefficiente di un passa-basso a un polo: lp += a * (x - lp). */
private fun lpCoef(cutoffHz: Float): Float =
    1f - exp(-2f * PI.toFloat() * cutoffHz / JingleFactory.SR)

/** Coefficiente di un passa-alto a un polo: y = a * (yPrev + x - xPrev). */
private fun hpCoef(cutoffHz: Float): Float =
    exp(-2f * PI.toFloat() * cutoffHz / JingleFactory.SR)

/**
 * Rumore in banda: un passa-alto a un polo seguito da due passa-basso in cascata.
 * Con un solo polo in alto il rumore resterebbe pieno fino a Nyquist e ogni colpo
 * suonerebbe come un "ssss" invece che come una pelle.
 */
private class BandNoise(hpHz: Float, lpHz: Float, seed: Int) {
    private val aHp = hpCoef(hpHz)
    private val aLp = lpCoef(lpHz)
    private val rnd = Random(seed)
    private var hp = 0f
    private var prev = 0f
    private var lp1 = 0f
    private var lp2 = 0f

    fun next(): Float {
        val n = rnd.nextFloat() * 2f - 1f
        hp = aHp * (hp + n - prev)
        prev = n
        lp1 += aLp * (hp - lp1)
        lp2 += aLp * (lp1 - lp2)
        return lp2
    }
}

enum class Jingle(val slug: String, val label: String) {
    RIMSHOT("rimshot", "Ba-dum-tss"),
    LAUGH("laugh", "Risate"),
    APPLAUSE("applause", "Applausi")
}

data class JingleFile(val jingle: Jingle, val file: File, val durationMs: Int)
