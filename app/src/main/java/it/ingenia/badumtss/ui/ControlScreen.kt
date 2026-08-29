package it.ingenia.badumtss.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ingenia.badumtss.core.AppState
import it.ingenia.badumtss.core.Jingle
import it.ingenia.badumtss.core.JingleChoice
import it.ingenia.badumtss.core.TriggerMode
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ControlScreen(
    onToggleListening: (Boolean) -> Unit,
    onFire: (Jingle) -> Unit
) {
    val listening by AppState.listening.collectAsState()
    val settings by AppState.settings.collectAsState()
    val levelDb by AppState.levelDb.collectAsState()
    val fireAt by AppState.lastFireAt.collectAsState()
    val reason by AppState.lastReason.collectAsState()
    val modelLoaded by AppState.modelLoaded.collectAsState()
    val floorDb by AppState.floorDb.collectAsState()
    val armed by AppState.armed.collectAsState()
    val ctx = LocalContext.current

    var chase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(fireAt) {
        if (fireAt == 0L) return@LaunchedEffect
        animate(0f, 1f, animationSpec = tween(950)) { v, _ -> chase = v }
        chase = 0f
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Club.Velvet)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MarqueeSign(chase = chase, breathing = listening)

        Spacer(Modifier.height(28.dp))

        StageButton(listening = listening, onClick = { onToggleListening(!listening) })

        Spacer(Modifier.height(10.dp))
        Text(
            if (listening) "Microfono aperto" else "Tocca per iniziare",
            style = Stencil.copy(color = if (listening) Club.Bulb else Club.Dim)
        )

        Spacer(Modifier.height(24.dp))
        VuMeter(levelDb = levelDb, active = listening)

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (listening)
                "voce ${levelDb.roundToInt()}  fondo ${floorDb.roundToInt()}  " +
                    (if (armed) "PRONTO" else "-")
            else "fermo",
            style = Readout.copy(fontSize = 12.sp, color = if (armed) Club.Bulb else Club.Dim)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (reason.isNotEmpty()) "ultimo: $reason" else "in attesa della prima battuta",
            style = Readout.copy(fontSize = 11.sp, color = Club.Dim)
        )

        Spacer(Modifier.height(30.dp))
        Panel(title = "Cosa fa partire il jingle") {
            Choices(
                options = TriggerMode.entries.map { it.label },
                selectedIndex = TriggerMode.entries.indexOf(settings.mode),
                onSelect = { i -> AppState.update(ctx) { it.copy(mode = TriggerMode.entries[i]) } }
            )
            Spacer(Modifier.height(8.dp))
            Text(settings.mode.hint, style = Stencil.copy(letterSpacing = 0.sp, fontSize = 12.sp))
            if (!modelLoaded && (settings.mode == TriggerMode.AUTO || settings.mode == TriggerMode.LAUGH)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Rilevamento pause attivo. Il riconoscimento delle risate si accende " +
                        "quando aggiungi yamnet.tflite in assets.",
                    style = Stencil.copy(letterSpacing = 0.sp, fontSize = 12.sp, color = Club.Dim)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Panel(title = "Quale jingle") {
            Choices(
                options = JingleChoice.entries.map { it.label },
                selectedIndex = JingleChoice.entries.indexOf(settings.choice),
                onSelect = { i -> AppState.update(ctx) { it.copy(choice = JingleChoice.entries[i]) } }
            )
        }

        Spacer(Modifier.height(16.dp))
        Panel(title = "Regolazioni") {
            Knob(
                label = "Sensibilità",
                readout = "${(settings.sensitivity * 100).roundToInt()}%",
                value = settings.sensitivity,
                range = 0f..1f,
                onChange = { v -> AppState.update(ctx) { it.copy(sensitivity = v) } }
            )
            Knob(
                label = "Attesa dopo la battuta",
                readout = "${settings.holdMs} ms",
                value = settings.holdMs.toFloat(),
                range = 250f..1400f,
                onChange = { v -> AppState.update(ctx) { it.copy(holdMs = v.roundToInt()) } }
            )
            Knob(
                label = "Pausa tra due jingle",
                readout = "${settings.cooldownMs / 1000f} s",
                value = settings.cooldownMs.toFloat(),
                range = 2000f..20000f,
                onChange = { v -> AppState.update(ctx) { it.copy(cooldownMs = v.roundToInt()) } }
            )
            Knob(
                label = "Volume",
                readout = "${(settings.volume * 100).roundToInt()}%",
                value = settings.volume,
                range = 0.1f..1f,
                onChange = { v -> AppState.update(ctx) { it.copy(volume = v) } }
            )
        }

        Spacer(Modifier.height(16.dp))
        Panel(title = "Fai partire tu") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Jingle.entries.forEach { j ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Club.Riser)
                            .clickable { onFire(j) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(j.label, style = Stencil.copy(color = Club.Marquee, letterSpacing = 1.sp))
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "L'audio non lascia mai il telefono: viene analizzato sul posto e scartato.",
            style = Stencil.copy(letterSpacing = 0.sp, fontSize = 11.sp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
    }
}

/* ------------------------------------------------------------------ insegna */

/**
 * L'elemento firma: le lampadine dell'insegna sopra il palco. A riposo respirano
 * piano, quando parte un jingle si accendono a rincorsa da sinistra a destra.
 */
@Composable
private fun MarqueeSign(chase: Float, breathing: Boolean) {
    val bulbs = 13
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "glow"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
        ) {
            val gap = size.width / (bulbs - 1)
            val head = chase * (bulbs + 3f) - 2f
            for (i in 0 until bulbs) {
                val nearness = if (chase == 0f) 0f else (1f - abs(i - head) / 2.4f).coerceIn(0f, 1f)
                val base = if (breathing) pulse * 0.45f else 0.18f
                val heat = (base + nearness).coerceIn(0f, 1f)
                val color = lerpColor(Club.BulbOff, Club.Bulb, heat)
                val r = 4.dp.toPx() + 2.dp.toPx() * nearness
                val cx = gap * i
                val cy = size.height / 2
                if (heat > 0.5f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Club.Bulb.copy(alpha = 0.35f * heat), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = r * 4f
                        ),
                        radius = r * 4f,
                        center = Offset(cx, cy)
                    )
                }
                drawCircle(color, radius = r, center = Offset(cx, cy))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("BA·DUM·TSS", style = Marquee)
        Spacer(Modifier.height(6.dp))
        Text("LA BATTERIA CHE NON HAI", style = Stencil)
    }
}

/* ------------------------------------------------------------------ pulsante */

@Composable
private fun StageButton(listening: Boolean, onClick: () -> Unit) {
    val glow by rememberInfiniteTransition(label = "btn").animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "btnGlow"
    )
    val scale = if (listening) glow else 1f

    Box(
        Modifier
            .fillMaxWidth(0.52f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (listening)
                        listOf(Club.Bulb.copy(alpha = scale), Color(0xFFC97A12))
                    else
                        listOf(Club.Riser, Club.Curtain)
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (listening) "STOP" else "VIA",
            style = Marquee.copy(
                fontSize = 26.sp,
                color = if (listening) Club.Velvet else Club.Marquee
            )
        )
    }
}

/* ---------------------------------------------------------------- vu meter */

@Composable
private fun VuMeter(levelDb: Float, active: Boolean) {
    val segments = 22
    val frac = ((levelDb + 60f) / 60f).coerceIn(0f, 1f)
    val lit = if (active) (frac * segments).roundToInt() else 0

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        for (i in 0 until segments) {
            val t = i / (segments - 1f)
            val on = i < lit
            val hue = when {
                t > 0.86f -> Club.Hot
                t > 0.66f -> Club.Bulb
                else -> Club.Cool
            }
            drawRect(
                color = if (on) hue else hue.copy(alpha = 0.10f),
                topLeft = Offset(i * (w + gap), 0f),
                size = androidx.compose.ui.geometry.Size(w, size.height)
            )
        }
    }
}

/* ---------------------------------------------------------------- pannelli */

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Club.Curtain)
            .padding(16.dp)
    ) {
        Text(title.uppercase(), style = Stencil)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Choices(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Club.Bulb else Club.Riser)
                    .clickable { onSelect(i) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = Stencil.copy(
                        letterSpacing = 0.5.sp,
                        fontSize = 12.sp,
                        color = if (on) Club.Velvet else Club.Marquee
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun Knob(
    label: String,
    readout: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = Stencil.copy(letterSpacing = 0.5.sp, fontSize = 12.sp))
            Text(readout, style = Readout)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Club.Bulb,
                activeTrackColor = Club.Bulb,
                inactiveTrackColor = Club.Riser
            )
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f
)
