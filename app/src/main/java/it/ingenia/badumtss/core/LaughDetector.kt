package it.ingenia.badumtss.core

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Classifica il suono ambientale con YAMNet e si accende quando qualcuno ride.
 * È il segnale più affidabile che abbiamo: la risata è acusticamente distintiva,
 * la battuta no.
 *
 * Il modello è opzionale. Se assets/yamnet.tflite non c'è, [create] restituisce null
 * e l'app resta in modalità prosodica.
 */
class LaughDetector private constructor(
    private val interpreter: Interpreter,
    private val laughIndices: IntArray,
    private val numClasses: Int
) {

    companion object {
        private const val TAG = "LaughDetector"
        /** YAMNet vuole 0,975 s di forma d'onda a 16 kHz. */
        const val WINDOW = 15_600
        private val KEYWORDS = listOf("laugh", "giggle", "chuckle", "chortle", "snicker", "cackle")

        fun create(context: Context): LaughDetector? = runCatching {
            val model = loadModel(context, "yamnet.tflite") ?: return null
            val opts = Interpreter.Options().apply { numThreads = 2 }
            val interpreter = Interpreter(model, opts)

            val outShape = interpreter.getOutputTensor(0).shape()
            val numClasses = outShape.last()

            // Gli indici delle classi si leggono dalla class map, non si scrivono a mano:
            // cambiano tra le versioni del modello.
            val indices = readLaughIndices(context, numClasses)
            if (indices.isEmpty()) {
                Log.w(TAG, "nessuna classe di risata trovata nella class map")
                interpreter.close()
                return null
            }
            LaughDetector(interpreter, indices, numClasses)
        }.onFailure { Log.w(TAG, "YAMNet non disponibile: ${it.message}") }.getOrNull()

        private fun loadModel(context: Context, name: String): ByteBuffer? {
            val available = runCatching { context.assets.list("")?.toSet() ?: emptySet() }
                .getOrDefault(emptySet())
            if (name !in available) return null
            val afd: AssetFileDescriptor = context.assets.openFd(name)
            FileInputStream(afd.fileDescriptor).use { input ->
                return input.channel.map(
                    FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength
                )
            }
        }

        private fun readLaughIndices(context: Context, numClasses: Int): IntArray {
            val available = runCatching { context.assets.list("")?.toSet() ?: emptySet() }
                .getOrDefault(emptySet())
            val csv = available.firstOrNull { it.contains("class_map") } ?: return intArrayOf()
            val out = ArrayList<Int>()
            context.assets.open(csv).bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = line.split(",")
                    if (cols.size >= 3) {
                        val idx = cols[0].trim().toIntOrNull() ?: return@forEach
                        val name = cols.drop(2).joinToString(",").lowercase()
                        if (idx < numClasses && KEYWORDS.any { it in name }) out.add(idx)
                    }
                }
            }
            return out.toIntArray()
        }
    }

    // Le due varianti di YAMNet in circolazione differiscono nella forma dell'ingresso:
    // una vuole [15600], l'altra [1, 15600]. Ci adattiamo a quella che troviamo.
    private val inputRank = interpreter.getInputTensor(0).shape().size
    private val flat = FloatArray(WINDOW)
    private val batched = Array(1) { FloatArray(WINDOW) }
    private val output = Array(1) { FloatArray(numClasses) }

    init {
        val shape = if (inputRank == 1) intArrayOf(WINDOW) else intArrayOf(1, WINDOW)
        runCatching {
            interpreter.resizeInput(0, shape)
            interpreter.allocateTensors()
        }
    }

    var lastScore = 0f
        private set

    /** @param window 15600 campioni float in [-1, 1]. @return true se è una risata. */
    fun isLaughter(window: FloatArray, threshold: Float): Boolean {
        if (window.size < WINDOW) return false
        return runCatching {
            val input: Any = if (inputRank == 1) {
                System.arraycopy(window, 0, flat, 0, WINDOW); flat
            } else {
                System.arraycopy(window, 0, batched[0], 0, WINDOW); batched
            }
            interpreter.runForMultipleInputsOutputs(arrayOf(input), mapOf(0 to output))
            var best = 0f
            for (i in laughIndices) best = maxOf(best, output[0][i])
            lastScore = best
            best >= threshold
        }.getOrDefault(false)
    }

    fun close() = interpreter.close()
}
