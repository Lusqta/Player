package com.lucas.player.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lucas.player.ui.components.AuroHaptics
import android.view.View
import androidx.palette.graphics.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * APPLE MUSIC ANDROID 7.0.2 LIQUID GLASS DESIGN SYSTEM
 * - Fundo absoluto OLED (#000000)
 * - Cores de acento adaptativas extraídas da capa tocando (ou Apple Blue #0A84FF)
 * - Liquid Glass Neutro: grafite neutro (#121214) com refração esférica sutil de 10dp nas bordas,
 *   borda fina de 1dp com especular, miolo 100% nítido, blur curto (2..10dp) e zero saturação neon.
 * - Fundo Mesh Animado (Now Playing) misturando 4 cores profundas da capa com rotação lenta.
 */

// ───────────────────────────── Tokens Editorial Dark OLED ─────────────────────────────

object AuroColor {
    val Canvas = Color(0xFF050505)                      // Fundo OLED profundo
    val Black = Canvas                                  // Alias
    val SurfaceCard = Color(0xFF141416)                 // Cards horizontais e agrupados
    val SurfaceGrouped = Color(0xFF1C1C1E)              // Platters secundários e sheets
    val SurfaceElevated = Color(0xFF262629)             // Superfícies elevadas
    val NavBarBg = Color(0xE0161618)                    // Fundo da barra flutuante (88% opacidade)
    val NavBarBorder = Color.White.copy(alpha = 0.08f)  // Borda sutil da navbar
    val ControlGlass = Color.White.copy(alpha = 0.22f)  // Botões de controle translúcidos
    val PillMuted = Color.White.copy(alpha = 0.12f)     // Pílulas secundárias ("Ver tudo")
    val TextPrimary = Color(0xFFFFFFFF)                 // Texto principal 100% branco
    val TextSecondary = Color.White.copy(alpha = 0.55f) // Rótulos secundários 55% branco
    val TextTertiary = Color.White.copy(alpha = 0.28f)  // Rótulos terciários 28% branco
    val ScrubberTrack = Color.White.copy(alpha = 0.20f) // Trilho inativo do scrubber
    val ScrubberActive = Color(0xFFFFFFFF)              // Trilho ativo do scrubber branco puro
    val Separator = Color.White.copy(alpha = 0.08f)     // Divisores sutis
    val Hairline = Separator                            // Alias
    val IconMono = Color(0xFFEBEBF5).copy(alpha = 0.85f)// Ícones monocromáticos
    val FavoriteYellow = Color(0xFFFFD60A)              // Estrela/coração ativo
    val ToggleGreen = Color(0xFF34C759)                 // Chave ativa padrão
    val Danger = Color(0xFFFF453A)                      // Ações destrutivas
    val AppleBlueFallback = Color(0xFF0A84FF)           // Fallback de azul
    val Track = ScrubberTrack                           // Alias
}

object AuroType {
    val GreetingTitle = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Medium,
        color = AuroColor.TextPrimary
    )
    val HeroName = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        color = AuroColor.TextPrimary
    )
    val NowPlayingTitle = TextStyle(
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Medium,
        color = AuroColor.TextPrimary
    )
    val SectionTitle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        color = AuroColor.TextPrimary
    )
    val LyricsActive = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        color = AuroColor.TextPrimary
    )
    val LyricsInactive = TextStyle(
        fontSize = 17.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        color = Color.White.copy(alpha = 0.28f)
    )
    val CardTitle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        color = AuroColor.TextPrimary
    )
    val TrackTitle = TextStyle(
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        color = AuroColor.TextPrimary
    )
    val Subtitle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal,
        color = AuroColor.TextSecondary
    )
    val MicroLabel = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = 0.75f)
    )

    // Aliases para manter total compatibilidade com o restante do app
    val LargeTitle = GreetingTitle
    val Body = TrackTitle
    val Caption = Subtitle
    val Label = Subtitle
    val Display = HeroName
    val Title = SectionTitle
    val NavLabel = TextStyle(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Normal,
        color = AuroColor.TextSecondary
    )
    val SectionHeader = MicroLabel.copy(letterSpacing = 1.sp)
    val FastScroller = TextStyle(
        fontSize = 8.5.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Normal,
        color = Color.White.copy(alpha = 0.40f)
    )
}

object AuroSpace {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    // Grid 4/8dp Apple Music
    val Micro = 4.dp
    val Compact = 8.dp
    val Standard = 12.dp
    val ScreenGutter = 16.dp
    val Section = 24.dp
    val Large = 32.dp
    val TouchTarget = 48.dp

    val ScreenMargin = 16.dp
    val EdgeMargin = 16.dp
}

object AuroHeight {
    val MiniPlayer = 56.dp
    val TabBar = 62.dp
    val CategoryRow = 48.dp
    val TrackRow = 54.dp
    val GlassButton = 40.dp
    val TouchTarget = 48.dp
    val Dock = 122.dp
    val DockCollapsed = 62.dp
    val SearchField = 48.dp
    val DialogOption = 48.dp
    val DialogInputField = 44.dp
    val Chip = 32.dp
}

object AuroRadius {
    val hero = 32.dp
    val cover = 28.dp
    val sheet = 28.dp
    val card = 24.dp
    val mix = 18.dp
    val artist = 18.dp
    val thumbnail = 8.dp
    val pill = 999.dp

    val chip = pill
    val dock = pill
    val button = pill
}

object AuroMotion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)   // Suave e responsivo
    const val ColorMs = 700                                 // Transição de cores da capa
    const val DriftMs = 18000                               // Onda de fundo do Now Playing
    fun <T> accent() = tween<T>(ColorMs, easing = Emphasized)
    fun <T> liquid() = spring<T>(dampingRatio = 0.82f, stiffness = 280f)
    fun <T> press() = spring<T>(dampingRatio = 0.75f, stiffness = 500f)
}

// ─────────────────────── Cor da capa -> Glow & Mesh ───────────────────────

@Immutable
data class AuroAccent(
    val a: Color,
    val b: Color,
    val mesh1: Color = Color(0xFF3D1214),
    val mesh2: Color = Color(0xFF160D18),
    val mesh3: Color = Color(0xFF2A1015),
    val mesh4: Color = Color(0xFF120A14)
) {
    companion object {
        val WarmRed = Color(0xFF9A1D1D)
        val AppleBlue = Color(0xFF0A84FF)
        val Neutral = AuroAccent(
            a = AppleBlue,
            b = Color(0xFF0071E3),
            mesh1 = Color(0xFF6B181A),
            mesh2 = Color(0xFF241126),
            mesh3 = Color(0xFF2A1015),
            mesh4 = Color(0xFF120A14)
        )
    }
}

/**
 * Normalização e cálculo de cores adaptativas baseadas na capa do álbum (HSL).
 * Garante contraste ótimo com #000000 e #1C1C1E.
 */
object GlowTone {
    fun fit(argb: Int): Pair<Int, Int> {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val hsl = rgbToHsl(r, g, b)
        val h = hsl[0]; val s = hsl[1]; val l = hsl[2]

        return if (s < 0.10f) {
            // Capa monocromática/acromática: Fallback para Azul Apple Music
            0xFF0A84FF.toInt() to 0xFF0071E3.toInt()
        } else {
            // Normaliza S em [0.55, 0.90] e L em [0.56, 0.68] para alto contraste sobre OLED
            val s1 = s.coerceIn(0.55f, 0.90f)
            val l1 = l.coerceIn(0.56f, 0.68f)
            val h2 = (h + 18f / 360f) % 1f
            hslToArgb(h, s1, l1) to hslToArgb(h2, (s1 - 0.05f).coerceIn(0f, 1f), (l1 + 0.04f).coerceIn(0f, 0.72f))
        }
    }

    fun fitMeshColor(argb: Int): Color {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val hsl = rgbToHsl(r, g, b)
        val h = hsl[0]
        val s = hsl[1].coerceIn(0.45f, 0.85f)
        val l = hsl[2].coerceIn(0.18f, 0.34f)
        return Color(hslToArgb(h, s, l))
    }

    fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        val l = (mx + mn) / 2f
        if (mx == mn) return floatArrayOf(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
            g -> ((b - r) / d + 2f) / 6f
            else -> ((r - g) / d + 4f) / 6f
        }
        return floatArrayOf(h, s, l)
    }

    private fun hue(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    fun hslToArgb(h: Float, s: Float, l: Float): Int {
        val r: Float; val g: Float; val b: Float
        if (s == 0f) { r = l; g = l; b = l } else {
            val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
            val p = 2f * l - q
            r = hue(p, q, h + 1f / 3f); g = hue(p, q, h); b = hue(p, q, h - 1f / 3f)
        }
        fun c(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }
}

object CoverGlow {
    private val memoryCache = mutableMapOf<Int, AuroAccent>()

    suspend fun from(cover: Bitmap?, accentColorMode: Int = 0): AuroAccent = withContext(Dispatchers.Default) {
        if (cover == null) return@withContext AuroAccent.Neutral

        val cacheKey = cover.hashCode() * 31 + accentColorMode
        memoryCache[cacheKey]?.let { return@withContext it }

        try {
            val swBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && cover.config == Bitmap.Config.HARDWARE) {
                cover.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext AuroAccent.Neutral
            } else {
                cover
            }

            val p = Palette.from(swBitmap).maximumColorCount(24).resizeBitmapArea(96 * 96).generate()
            val swatches = listOfNotNull(p.vibrantSwatch, p.lightVibrantSwatch, p.darkVibrantSwatch, p.dominantSwatch, p.mutedSwatch)
            val seed = swatches.maxByOrNull { it.population * (0.25f + it.hsl[1]) }

            val (aInt, bInt) = if (seed != null) {
                GlowTone.fit(seed.rgb)
            } else {
                0xFF9A1D1D.toInt() to 0xFF3D1214.toInt()
            }

            // 4 cores para o mesh animado do Now Playing (fallback neutro quente escuro #6B181A e #241126)
            val m1 = GlowTone.fitMeshColor(p.vibrantSwatch?.rgb ?: seed?.rgb ?: 0xFF6B181A.toInt())
            var m2 = GlowTone.fitMeshColor(p.darkVibrantSwatch?.rgb ?: p.dominantSwatch?.rgb ?: 0xFF241126.toInt())
            if (m2 == m1) {
                val hsl = GlowTone.rgbToHsl(m1.red, m1.green, m1.blue)
                val hShifted = (hsl[0] + 35f / 360f) % 1f
                m2 = Color(GlowTone.hslToArgb(hShifted, hsl[1], hsl[2]))
            }
            val m3 = GlowTone.fitMeshColor(p.lightVibrantSwatch?.rgb ?: p.mutedSwatch?.rgb ?: 0xFF2A1015.toInt())
            val m4 = GlowTone.fitMeshColor(seed?.rgb ?: 0xFF120A14.toInt())

            val finalAccent = if (accentColorMode == 1) {
                // Modo fixo Azul Apple
                AuroAccent(
                    a = AuroAccent.AppleBlue,
                    b = Color(0xFF0071E3),
                    mesh1 = m1,
                    mesh2 = m2,
                    mesh3 = m3,
                    mesh4 = m4
                )
            } else {
                AuroAccent(
                    a = Color(aInt),
                    b = Color(bInt),
                    mesh1 = m1,
                    mesh2 = m2,
                    mesh3 = m3,
                    mesh4 = m4
                )
            }

            if (memoryCache.size > 100) memoryCache.clear()
            memoryCache[cacheKey] = finalAccent
            finalAccent
        } catch (_: Exception) {
            AuroAccent.Neutral
        }
    }
}

// ─────────────────── Luz do ouvinte: inclinação do celular ───────────────────

// <AuroTilt>
/** Inclinação do celular -> direção da luz do ouvinte. Pura (sem Android), testável na JVM. */
object AuroTilt {
    fun light(gx: Float, gy: Float, sideGain: Float = 1.6f, bias: Float = 0.25f): FloatArray {
        val x = (gx / 9.81f) * sideGain
        val y = -(gy / 9.81f) - bias
        val len = sqrt(x * x + y * y)
        val inPlane = sqrt(gx * gx + gy * gy) / 9.81f
        val gain = 0.6f + 0.4f * inPlane.coerceIn(0f, 1f)
        return if (len < 1e-4f) floatArrayOf(0f, -1f, gain) else floatArrayOf(x / len, y / len, gain)
    }
}

class TiltSmoother(private val omega: Float = 7f, private val zeta: Float = 0.8f) {
    var x = 0f
        private set
    var y = 1f
        private set
    var gain = 1f
        private set
    private var vx = 0f
    private var vy = 0f
    private var vg = 0f
    private var last = 0L

    fun step(tx: Float, ty: Float, tg: Float, nanos: Long) {
        val dt = if (last == 0L) 0.02f else ((nanos - last) / 1e9f).coerceIn(0.001f, 0.05f)
        last = nanos
        vx += (omega * omega * (tx - x) - 2f * zeta * omega * vx) * dt
        x += vx * dt
        vy += (omega * omega * (ty - y) - 2f * zeta * omega * vy) * dt
        y += vy * dt
        vg += (omega * omega * (tg - gain) - 2f * zeta * omega * vg) * dt
        gain += vg * dt
    }
}
// </AuroTilt>

// ─────────────────── CompositionLocals & Tema ───────────────────

@Immutable
data class AuroLight(val dx: Float = 0f, val dy: Float = 1f, val gain: Float = 1f)

class AuroAccentState(
    val a: State<Color>,
    val b: State<Color>,
    val mesh1: State<Color>,
    val mesh2: State<Color>,
    val mesh3: State<Color>,
    val mesh4: State<Color>
)

val LocalAuroLight = staticCompositionLocalOf<() -> AuroLight> { { AuroLight() } }
val LocalAuroTime = staticCompositionLocalOf<() -> Float> { { 0f } }
val LocalAuroAccent = staticCompositionLocalOf<AuroAccentState> {
    AuroAccentState(
        object : State<Color> { override val value = AuroAccent.Neutral.a },
        object : State<Color> { override val value = AuroAccent.Neutral.b },
        object : State<Color> { override val value = AuroAccent.Neutral.mesh1 },
        object : State<Color> { override val value = AuroAccent.Neutral.mesh2 },
        object : State<Color> { override val value = AuroAccent.Neutral.mesh3 },
        object : State<Color> { override val value = AuroAccent.Neutral.mesh4 }
    )
}

val LocalAuroSoft = staticCompositionLocalOf { 0.6f }
val LocalReduceMotionOverride = staticCompositionLocalOf<Boolean?> { null }
val LocalReduceTransparency = staticCompositionLocalOf { false }
val LocalAccentColorMode = staticCompositionLocalOf { 0 }
val LocalAuroBottomInset = compositionLocalOf { 0.dp }
val LocalAuroIsPlaying = compositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val override = LocalReduceMotionOverride.current
    if (override != null) return override
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
fun rememberListenerLight(playing: Boolean, tilt: Boolean = true): () -> AuroLight {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val rawTarget = remember { FloatArray(3) { if (it == 1) -1f else if (it == 2) 1f else 0f } }
    val currentLightRef = remember { arrayOf(AuroLight()) }
    var drawTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(tilt, lifecycleOwner) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!tilt || sensor == null) {
            currentLightRef[0] = AuroLight()
            return@DisposableEffect onDispose {}
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val l = AuroTilt.light(event.values[0], event.values[1])
                rawTarget[0] = l[0]
                rawTarget[1] = l[1]
                rawTarget[2] = l[2]
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }

        var isRegistered = false
        fun register() {
            if (!isRegistered) {
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                isRegistered = true
            }
        }
        fun unregister() {
            if (isRegistered) {
                sm.unregisterListener(listener)
                isRegistered = false
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            register()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> register()
                Lifecycle.Event.ON_STOP -> unregister()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }

    LaunchedEffect(tilt, lifecycleOwner) {
        if (!tilt) return@LaunchedEffect
        val smoother = TiltSmoother()
        while (isActive) {
            withFrameNanos { nanos ->
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    smoother.step(rawTarget[0], rawTarget[1], rawTarget[2], nanos)
                    val len = sqrt(smoother.x * smoother.x + smoother.y * smoother.y).coerceAtLeast(1e-4f)
                    val next = AuroLight(smoother.x / len, smoother.y / len, smoother.gain)
                    val cur = currentLightRef[0]
                    if (abs(next.dx - cur.dx) + abs(next.dy - cur.dy) + abs(next.gain - cur.gain) > 0.0005f) {
                        currentLightRef[0] = next
                        drawTrigger++
                    }
                }
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "light")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(AuroMotion.DriftMs, easing = LinearEasing)),
        label = "phase",
    )
    val amount = animateFloatAsState(if (playing && tilt) 1f else 0f, AuroMotion.liquid(), label = "breath")
    return {
        @Suppress("UNUSED_VARIABLE")
        val v = drawTrigger
        val s = currentLightRef[0]
        val th = 0.07f * amount.value * sin((phase.value * 2f * PI).toFloat())
        val c = cos(th)
        val sn = sin(th)
        AuroLight(dx = s.dx * c - s.dy * sn, dy = s.dx * sn + s.dy * c, gain = s.gain)
    }
}

@Composable
fun rememberAuroClock(running: Boolean): () -> Float {
    val t = produceState(0f, running) {
        if (!running) return@produceState
        val start = withFrameNanos { it }
        val offset = value
        while (true) withFrameNanos { value = offset + (it - start) / 1_000_000_000f }
    }
    return { t.value }
}

@Composable
fun AuroTheme(
    accent: AuroAccent,
    playing: Boolean = true,
    tilt: Boolean = true,
    soft: Float = 0.6f,
    reduceTransparency: Boolean = false,
    accentColorMode: Int = 0,
    reduceMotionOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val reduce = if (reduceMotionOverride != null) reduceMotionOverride else rememberReduceMotion()
    val a = animateColorAsState(accent.a, if (reduce) tween(0) else AuroMotion.accent(), label = "accentA")
    val b = animateColorAsState(accent.b, if (reduce) tween(0) else AuroMotion.accent(), label = "accentB")
    val m1 = animateColorAsState(accent.mesh1, if (reduce) tween(0) else AuroMotion.accent(), label = "mesh1")
    val m2 = animateColorAsState(accent.mesh2, if (reduce) tween(0) else AuroMotion.accent(), label = "mesh2")
    val m3 = animateColorAsState(accent.mesh3, if (reduce) tween(0) else AuroMotion.accent(), label = "mesh3")
    val m4 = animateColorAsState(accent.mesh4, if (reduce) tween(0) else AuroMotion.accent(), label = "mesh4")

    val state = remember(a, b, m1, m2, m3, m4) { AuroAccentState(a, b, m1, m2, m3, m4) }
    val light = rememberListenerLight(playing && !reduce, tilt && !reduce)
    val clock = rememberAuroClock(playing && !reduce)

    CompositionLocalProvider(
        LocalAuroAccent provides state,
        LocalAuroLight provides light,
        LocalAuroTime provides clock,
        LocalAuroSoft provides soft,
        LocalReduceTransparency provides reduceTransparency,
        LocalAccentColorMode provides accentColorMode,
        LocalReduceMotionOverride provides reduceMotionOverride
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AuroColor.Black)
        ) {
            content()
        }
    }
}

// ─────────────────────────── Superfícies Editorial OLED ───────────────────────────

enum class AuroGlassKind { Button, Card }

/**
 * Superfície limpa do design Editorial Dark OLED:
 * - Fundo fosco translúcido (#18181B @ 82%) ou branco translúcido (#FFFFFF @ 22% para Button)
 * - Borda fina e precisa de 0.5.dp a 10% branco
 * - Zero blur/refração pesada, garantindo 120Hz constantes e leveza total
 * - Animação de toque com escala 0.95f e mola spring(0.75f, 500f)
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    soft: Float = LocalAuroSoft.current,
    onClick: (() -> Unit)? = null,
    haptic: ((View) -> Unit)? = { AuroHaptics.click(it) },
    content: @Composable BoxScope.() -> Unit
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "glassPressScale"
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = interaction, indication = null) {
            haptic?.invoke(view)
            onClick()
        }
    } else Modifier

    val isButton = shape == CircleShape
    val surfaceColor = if (isButton) AuroColor.ControlGlass else Color(0xD118181B)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .background(surfaceColor, shape)
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Componente unificado AuroGlass: mapeia diretamente para LiquidGlassSurface
 * mantendo compatibilidade total com as chamadas existentes.
 */
@Composable
fun AuroGlass(
    modifier: Modifier = Modifier,
    kind: AuroGlassKind = AuroGlassKind.Card,
    onClick: (() -> Unit)? = null,
    haptic: ((View) -> Unit)? = { AuroHaptics.click(it) },
    soft: Float = LocalAuroSoft.current,
    cornerRadius: Dp? = null,
    specMultiplier: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val isButton = kind == AuroGlassKind.Button
    val shape = if (isButton) CircleShape else RoundedCornerShape(cornerRadius ?: AuroRadius.card)

    LiquidGlassSurface(
        modifier = modifier,
        shape = shape,
        soft = soft,
        onClick = onClick,
        haptic = haptic,
        content = content
    )
}

/**
 * Fundo Borrado Animado de 2 Cores do Álbum (Now Playing + Letras):
 * - Extrai 2 cores reais da capa atual (mesh1 e mesh2 do CoverGlow)
 * - 2 manchas radiais amplas e super difusas em órbita orgânica (16s)
 * - Base #050505, 120Hz constante, zero BlurEffect recursivo
 */
@Composable
fun AnimatedDualColorBackground(
    color1: Color,
    color2: Color,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val reduceMotion = rememberReduceMotion()
    val animC1 by animateColorAsState(
        targetValue = color1,
        animationSpec = tween(AuroMotion.ColorMs, easing = AuroMotion.Emphasized),
        label = "dualBgColor1"
    )
    val animC2 by animateColorAsState(
        targetValue = color2,
        animationSpec = tween(AuroMotion.ColorMs, easing = AuroMotion.Emphasized),
        label = "dualBgColor2"
    )
    val transition = rememberInfiniteTransition(label = "dualColorOrbit")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitPhase"
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(DUAL_COLOR_BLUR_AGSL) }
        val brush = remember(shader) { ShaderBrush(shader) }
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawBehind {
                    val t = if (isPlaying && !reduceMotion) (phase * 2f * PI).toFloat() else 0f
                    shader.setFloatUniform("uSize", size.width, size.height)
                    shader.setFloatUniform("uTime", t)
                    shader.setFloatUniform("uColor1", animC1.red, animC1.green, animC1.blue)
                    shader.setFloatUniform("uColor2", animC2.red, animC2.green, animC2.blue)
                    drawRect(brush)
                },
            content = content
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF050505))
                .drawBehind {
                    val t = if (isPlaying && !reduceMotion) (phase * 2f * PI).toFloat() else 0f
                    val w = size.width
                    val h = size.height
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(animC1.copy(alpha = 0.72f), animC1.copy(alpha = 0.28f), Color.Transparent),
                            center = Offset(w * (0.35f + 0.28f * cos(t)), h * (0.28f + 0.16f * sin(t))),
                            radius = max(w, h) * 0.85f
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(animC2.copy(alpha = 0.65f), animC2.copy(alpha = 0.24f), Color.Transparent),
                            center = Offset(w * (0.65f + 0.26f * cos(-t + 2.1f)), h * (0.62f + 0.18f * sin(-t + 2.1f))),
                            radius = max(w, h) * 0.90f
                        )
                    )
                },
            content = content
        )
    }
}

private const val DUAL_COLOR_BLUR_AGSL = """
uniform float2 uSize;
uniform float  uTime;
uniform float3 uColor1;
uniform float3 uColor2;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float2 aspect = float2(uSize.x / max(uSize.y, 1.0), 1.0);

    // 2 núcleos borrados em órbita orgânica suave
    float2 p1 = float2(0.38 + 0.28 * cos(uTime), 0.28 + 0.15 * sin(uTime * 0.9));
    float2 p2 = float2(0.62 + 0.28 * cos(-uTime + 2.2), 0.62 + 0.18 * sin(-uTime * 0.8 + 1.4));
    float2 p3 = float2(0.50 + 0.22 * sin(uTime * 0.7 + 1.0), 0.85 + 0.10 * cos(uTime));

    float d1 = length((uv - p1) * aspect);
    float d2 = length((uv - p2) * aspect);
    float d3 = length((uv - p3) * aspect);

    // Queda gaussiana ultra suave (efeito blur real sem bandas)
    float w1 = exp(-d1 * d1 * 2.2);
    float w2 = exp(-d2 * d2 * 2.0);
    float w3 = exp(-d3 * d3 * 2.8) * 0.45;

    float3 base = float3(0.025, 0.025, 0.030);
    float3 col = base + uColor1 * (w1 * 0.95 + w3 * 0.5) + uColor2 * (w2 * 0.90);

    // Vinheta discreta nas bordas para contraste dos controles brancos
    float vig = 1.0 - smoothstep(0.55, 1.35, length((uv - float2(0.5, 0.48)) * float2(1.0, 1.15))) * 0.45;
    col *= vig;

    return half4(half3(col), 1.0);
}
"""

/**
 * Fundo com Ambient Bleed suave extraído da capa:
 * Base #050505 com gradiente vertical suave da cor da capa desvanecendo para transparente.
 * Zero shader pesado, garantindo 120Hz perfeitos.
 */
@Composable
fun AmbientBleedBackground(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(AuroMotion.ColorMs, easing = AuroMotion.Emphasized),
        label = "ambientBleedColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.50f),
                            animatedColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.70f
                    )
                )
            },
        content = content
    )
}

// ─────────────────────── Fundo Mesh Animado (Now Playing) ───────────────────────

/**
 * Fundo animado de gradientes fluidos estilo Apple Music Android 7.0.2 (LyricsBackgroundLayerView).
 * Mistura 4 cores escuras/profundas extraídas da capa com rotação lenta, controlado por LocalAuroTime.
 * Pausa em ON_STOP ou quando reduceMotion estiver ativo (0 custo de GPU quando parado).
 */
@Composable
fun LiquidMeshBackground(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val clock = LocalAuroTime.current
    val accent = LocalAuroAccent.current
    val reduceMotion = rememberReduceMotion()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !reduceMotion) {
        val shader = remember { RuntimeShader(LIQUID_MESH_AGSL) }
        val brush = remember(shader) { ShaderBrush(shader) }

        Box(
            modifier = modifier
                .fillMaxSize()
                .drawBehind {
                    val time = if (isPlaying) clock() else 0f
                    val c1 = accent.mesh1.value
                    val c2 = accent.mesh2.value
                    val c3 = accent.mesh3.value
                    val c4 = accent.mesh4.value
                    shader.setFloatUniform("uSize", size.width, size.height)
                    shader.setFloatUniform("uTime", time)
                    shader.setFloatUniform("uColor1", c1.red, c1.green, c1.blue, 1f)
                    shader.setFloatUniform("uColor2", c2.red, c2.green, c2.blue, 1f)
                    shader.setFloatUniform("uColor3", c3.red, c3.green, c3.blue, 1f)
                    shader.setFloatUniform("uColor4", c4.red, c4.green, c4.blue, 1f)
                    drawRect(brush)
                },
            content = content
        )
    } else {
        // Fallback: gradiente estático profundo
        val c1 = accent.mesh1.value
        val c2 = accent.mesh2.value
        val c4 = accent.mesh4.value

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            c1.copy(alpha = 0.85f),
                            c2.copy(alpha = 0.90f),
                            c4.copy(alpha = 0.95f),
                            AuroColor.Black
                        )
                    )
                ),
            content = content
        )
    }
}

// ─────────────────────── AGSL Shaders Nativos (API 33+) ───────────────────────

/**
 * Shader AGSL do Liquid Glass: grafite neutro (#121214) com refração nas bordas externas
 * de ~10dp, especular sutil na borda superior e centro nítido.
 */
private const val IOS_LIQUID_GLASS_AGSL = """
uniform float2 uSize;
uniform float  uRadius;
uniform float  uSoft;
uniform float2 uLightDir;
uniform float3 uAccent;
uniform float  uPx;
uniform float  uTintAlpha;

float sdRound(float2 p, float2 halfSize, float radius) {
    float2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

float2 getNormal(float2 p, float2 halfSize, float radius) {
    float eps = 1.0;
    float dx = sdRound(p + float2(eps, 0.0), halfSize, radius) - sdRound(p - float2(eps, 0.0), halfSize, radius);
    float dy = sdRound(p + float2(0.0, eps), halfSize, radius) - sdRound(p - float2(0.0, eps), halfSize, radius);
    float2 n = float2(dx, dy);
    float l = length(n);
    return (l > 0.0001) ? (n / l) : float2(0.0, 1.0);
}

half4 main(float2 fragCoord) {
    float2 halfSize = 0.5 * uSize;
    float2 p = fragCoord - halfSize;
    float radius = clamp(uRadius, 0.0, min(halfSize.x, halfSize.y));
    
    float d = sdRound(p, halfSize, radius);
    
    // Borda externa de ~10dp para refração e anel especular
    float rimWidth = 10.0 * uPx;
    // Dentro da forma, d <= 0.0, a distância até a borda externa é -d
    float distToRim = -d;
    float rimFactor = 1.0 - clamp(distToRim / rimWidth, 0.0, 1.0);

    // Reflexo especular sutil na direção da luz do ouvinte ao longo da normal da borda
    float2 n = getNormal(p, halfSize, radius);
    float spec = max(dot(n, -uLightDir), 0.0);
    spec = pow(spec, 4.0) * rimFactor * 0.35;

    // Grafite neutro #121214
    float3 graphite = float3(0.070, 0.070, 0.078);
    float3 col = graphite + spec * (float3(1.0) * 0.8 + uAccent * 0.2);

    float alpha = clamp(uTintAlpha + rimFactor * 0.15, 0.0, 0.95);
    return half4(half3(col * alpha), half(alpha));
}
"""

/**
 * Shader AGSL do Mesh Animado de Fundo (Now Playing):
 * Três vórtices lentos inspirados no algoritmo LyricsBackgroundLayerView do Apple Music.
 */
private const val LIQUID_MESH_AGSL = """
uniform float2 uSize;
uniform float  uTime;
uniform float4 uColor1;
uniform float4 uColor2;
uniform float4 uColor3;
uniform float4 uColor4;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float t = uTime * 0.08;

    // Três centros orgânicos em rotação lenta
    float2 c1 = float2(0.50 + 0.35 * cos(t * 0.8), 0.35 + 0.25 * sin(t * 0.8));
    float2 c2 = float2(0.50 + 0.38 * cos(-t * 0.6 + 2.09), 0.65 + 0.25 * sin(-t * 0.6 + 2.09));
    float2 c3 = float2(0.50 + 0.30 * sin(t * 0.5 + 4.18), 0.50 + 0.28 * cos(t * 0.5 + 4.18));

    float d1 = smoothstep(0.95, 0.05, length(uv - c1));
    float d2 = smoothstep(0.95, 0.05, length(uv - c2));
    float d3 = smoothstep(0.95, 0.05, length(uv - c3));

    // Mistura aditiva/interpolada
    float3 col = uColor1.rgb * (1.0 - uv.y * 0.25);
    col = mix(col, uColor2.rgb, clamp(d1, 0.0, 1.0));
    col = mix(col, uColor3.rgb, clamp(d2 * 0.85, 0.0, 1.0));
    col = mix(col, uColor4.rgb, clamp(d3 * 0.75, 0.0, 1.0));

    // Scrims Apple Music: vinheta radial e escurecimento do rodapé para contraste total
    float vig = smoothstep(1.3, 0.3, length((uv - float2(0.5, 0.5)) * float2(1.0, 1.3)));
    col *= (0.60 + 0.40 * vig);

    float bottomScrim = smoothstep(1.0, 0.70, uv.y);
    col *= (0.70 + 0.30 * bottomScrim);

    return half4(half3(col), 1.0);
}
"""
