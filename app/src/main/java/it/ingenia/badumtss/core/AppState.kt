package it.ingenia.badumtss.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

enum class TriggerMode(val label: String, val hint: String) {
    AUTO("Tutto", "Risate e pause"),
    LAUGH("Risate", "Solo quando si ride"),
    PAUSE("Pause", "Solo dopo la battuta"),
    MANUAL("Manuale", "Solo i tasti qui sotto")
}

enum class JingleChoice(val label: String) {
    RIMSHOT("Ba-dum-tss"),
    LAUGH("Risate"),
    APPLAUSE("Applausi"),
    RANDOM("A caso")
}

data class Settings(
    val mode: TriggerMode = TriggerMode.AUTO,
    val choice: JingleChoice = JingleChoice.RIMSHOT,
    val sensitivity: Float = 0.5f,
    val holdMs: Int = 550,
    val cooldownMs: Int = 5000,
    val volume: Float = 0.9f
)

/** Un solo punto di verità, letto dalla UI e scritto dal servizio (e viceversa). */
object AppState {
    val listening = MutableStateFlow(false)
    val levelDb = MutableStateFlow(-90f)
    val laughScore = MutableStateFlow(0f)
    val modelLoaded = MutableStateFlow(false)
    val lastFireAt = MutableStateFlow(0L)
    val lastReason = MutableStateFlow("")
    val settings = MutableStateFlow(Settings())

    private const val PREFS = "badumtss"

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        settings.value = Settings(
            mode = runCatching { TriggerMode.valueOf(p.getString("mode", "AUTO")!!) }
                .getOrDefault(TriggerMode.AUTO),
            choice = runCatching { JingleChoice.valueOf(p.getString("choice", "RIMSHOT")!!) }
                .getOrDefault(JingleChoice.RIMSHOT),
            sensitivity = p.getFloat("sensitivity", 0.5f),
            holdMs = p.getInt("holdMs", 550),
            cooldownMs = p.getInt("cooldownMs", 5000),
            volume = p.getFloat("volume", 0.9f)
        )
    }

    fun save(context: Context) {
        val s = settings.value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", s.mode.name)
            .putString("choice", s.choice.name)
            .putFloat("sensitivity", s.sensitivity)
            .putInt("holdMs", s.holdMs)
            .putInt("cooldownMs", s.cooldownMs)
            .putFloat("volume", s.volume)
            .apply()
    }

    fun update(context: Context, block: (Settings) -> Settings) {
        settings.value = block(settings.value)
        save(context)
    }
}
