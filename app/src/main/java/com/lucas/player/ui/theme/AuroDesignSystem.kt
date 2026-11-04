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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.lucas.player.ui.components.AuroHaptics
import android.view.View
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * AURŌ DESIGN SYSTEM v3: o mundo é vazio (#000000), os objetos são de vidro preto, e a luz é o OUVINTE.
 * Spec completa em DESIGN_SYSTEM.md.
 *
 * A luz é paralela (a MESMA direção em todos os objetos), vem de baixo e acompanha a inclinação do celular.
 * Ela não é desenhada: aparece só como reflexo nas bordas dos objetos. O resto de cada objeto é #000000.
 */

// ───────────────────────────── Tokens ─────────────────────────────

object AuroColor {
    val Black = Color(0xFF000000)         // fundo de TODAS as telas. Nunca #121212 nem cinza de "superfície".
    val TextPrimary = Color(0xFFFFFFFF)   // 21:1 sobre o preto
    val TextSecondary = Color(0xFF9E9E9E) // branco 62%, 7,8:1
    val TextTertiary = Color(0xFF616161)  // branco 38%, 3,4:1: só dica/desabilitado, nunca texto essencial
    val Hairline = Color(0x14FFFFFF)      // branco 8%: fio de 1dp entre linhas
    val Track = Color(0xFF2D2D2D)         // trilho da barra de progresso
}

object AuroSpace { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp }

object AuroRadius { val card = 24.dp; val cover = 22.dp; val chip = 16.dp }

object AuroMotion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)   // sai rápido e pousa devagar: o "líquido"
    const val ColorMs = 900                                 // troca de capa: as cores se misturam em Oklab
    const val DriftMs = 16000                               // respiração da luz (só enquanto toca)
    fun <T> accent() = tween<T>(ColorMs, easing = Emphasized)
    fun <T> liquid() = spring<T>(dampingRatio = 0.82f, stiffness = 260f)   // movimento de UI
    fun <T> press() = spring<T>(dampingRatio = 0.75f, stiffness = 500f)    // toque
}

// ─────────────────────── Cor da capa -> glow ───────────────────────

@Immutable
data class AuroAccent(val a: Color, val b: Color) {
    companion object { val Neutral = AuroAccent(Color(0xFFD0CDC8), Color(0xFFA69F96)) }
}

// <GlowTone>
/** Cor-semente da capa -> duas cores de glow que brilham sobre o preto. Pura (sem Android), testável na JVM. */
object GlowTone {
    fun fit(argb: Int): Pair<Int, Int> {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val hsl = rgbToHsl(r, g, b)
        val h = hsl[0]; val s = hsl[1]; val l = hsl[2]
        return if (s < 0.10f) {
            // capa sem cor (cinza/preto/branco): glow prata quente
            hslToArgb(35f / 360f, 0.08f, 0.80f) to hslToArgb(35f / 360f, 0.08f, 0.62f)
        } else {
            val s1 = (s * 1.15f).coerceIn(0.62f, 0.95f)
            val l1 = l.coerceIn(0.50f, 0.62f)
            val h2 = (h + 28f / 360f) % 1f // vizinha análoga, um pouco mais clara
            hslToArgb(h, s1, l1) to hslToArgb(h2, (s1 - 0.05f).coerceIn(0f, 1f), (l1 + 0.06f).coerceIn(0f, 0.7f))
        }
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
// </GlowTone>

object CoverGlow {
    private val memoryCache = mutableMapOf<Int, AuroAccent>()

    /** Capa -> cores de glow com cache. Quantiza a imagem: chame fora da main thread. */
    suspend fun from(cover: Bitmap?): AuroAccent = withContext(Dispatchers.Default) {
        if (cover == null) return@withContext AuroAccent.Neutral

        val cacheKey = cover.hashCode()
        memoryCache[cacheKey]?.let { return@withContext it }

        try {
            // Conversão segura caso o bitmap seja Hardware
            val swBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && cover.config == Bitmap.Config.HARDWARE) {
                cover.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext AuroAccent.Neutral
            } else {
                cover
            }

            val p = Palette.from(swBitmap).maximumColorCount(16).resizeBitmapArea(96 * 96).generate()
            val swatches = listOfNotNull(p.vibrantSwatch, p.lightVibrantSwatch, p.darkVibrantSwatch, p.dominantSwatch)
            val seed = swatches.maxByOrNull { it.population * (0.25f + it.hsl[1]) }
                ?: return@withContext AuroAccent.Neutral
            val (a, b) = GlowTone.fit(seed.rgb)
            val result = AuroAccent(Color(a), Color(b))
            if (memoryCache.size > 100) memoryCache.clear()
            memoryCache[cacheKey] = result
            result
        } catch (_: Exception) {
            AuroAccent.Neutral
        }
    }
}

// ─────────────────── Luz do ouvinte: inclinação do celular ───────────────────

// <AuroTilt>
/** Inclinação do celular -> direção da luz do ouvinte. Pura (sem Android), testável na JVM. */
object AuroTilt {
    /**
     * gx, gy: gravidade nos eixos do aparelho, em m/s² (convenção do Android: x pra direita, y pra cima na tela;
     * com o celular em pé, gy ≈ +9,81). A luz vem do "pra baixo do mundo" projetado na tela, com um viés pro pé
     * da tela (o ouvinte) quando o celular está deitado. Retorna (dx, dy, ganho): direção PARA a luz
     * (x pra direita, y pra baixo) e intensidade 0,6..1 (celular deitado = reflexo um pouco mais fraco).
     */
    fun light(gx: Float, gy: Float, sideGain: Float = 1.6f, bias: Float = 0.25f): FloatArray {
        val x = -gx / 9.81f * sideGain
        val y = gy / 9.81f + bias
        val len = sqrt(x * x + y * y)
        val inPlane = sqrt(gx * gx + gy * gy) / 9.81f
        val gain = 0.6f + 0.4f * inPlane.coerceIn(0f, 1f)
        return if (len < 1e-4f) floatArrayOf(0f, 1f, gain) else floatArrayOf(x / len, y / len, gain)
    }
}

/** Mola amortecida (ω = 7 rad/s, ζ = 0,8): a luz acompanha o celular com um pouco de atraso, como líquido. */
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

// ─────────────────── Luz do ouvinte + tema (fundo preto) ───────────────────

/**
 * Direção PARA a luz no plano da tela (x pra direita, y pra baixo): (0, 1) = vem de baixo. gain = 0,6..1.
 * Só existe UMA luz, igual para todos os objetos.
 */
@Immutable
data class AuroLight(val dx: Float = 0f, val dy: Float = 1f, val gain: Float = 1f)

class AuroAccentState(val a: State<Color>, val b: State<Color>)

// Leitores só em fase de desenho (lambda / State): a animação NÃO recompõe a tela a cada frame.
val LocalAuroLight = staticCompositionLocalOf<() -> AuroLight> { { AuroLight() } }
val LocalAuroTime = staticCompositionLocalOf<() -> Float> { { 0f } }
val LocalAuroAccent = staticCompositionLocalOf<AuroAccentState> {
    AuroAccentState(
        object : State<Color> { override val value = AuroAccent.Neutral.a },
        object : State<Color> { override val value = AuroAccent.Neutral.b },
    )
}

val LocalAuroSoft = staticCompositionLocalOf { 0.6f }
val LocalReduceMotionOverride = staticCompositionLocalOf<Boolean?> { null }

/** true se o usuário desligou as animações do sistema ou configurou no app. */
@Composable
fun rememberReduceMotion(): Boolean {
    val override = LocalReduceMotionOverride.current
    if (override != null) return override
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * Luz do ouvinte: vem de baixo e acompanha o celular (sensor de gravidade, suavizado por mola). Enquanto a
 * música toca, a direção respira ±4° devagar. Sem sensor ou com "remover animações", fica fixa embaixo.
 */
@Composable
fun rememberListenerLight(playing: Boolean, tilt: Boolean = true): () -> AuroLight {
    val context = LocalContext.current
    val sensed = remember { mutableStateOf(AuroLight()) } // lido só na fase de desenho: não recompõe
    DisposableEffect(tilt) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!tilt || sensor == null) {
            sensed.value = AuroLight()
            return@DisposableEffect onDispose {}
        }
        val smoother = TiltSmoother()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val l = AuroTilt.light(event.values[0], event.values[1])
                smoother.step(l[0], l[1], l[2], event.timestamp)
                val len = sqrt(smoother.x * smoother.x + smoother.y * smoother.y).coerceAtLeast(1e-4f)
                val next = AuroLight(smoother.x / len, smoother.y / len, smoother.gain)
                val cur = sensed.value
                if (abs(next.dx - cur.dx) + abs(next.dy - cur.dy) + abs(next.gain - cur.gain) > 0.002f) sensed.value = next
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
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
        val s = sensed.value
        val th = 0.07f * amount.value * sin((phase.value * 2f * PI).toFloat()) // ±4°
        val c = cos(th)
        val sn = sin(th)
        AuroLight(dx = s.dx * c - s.dy * sn, dy = s.dx * sn + s.dy * c, gain = s.gain)
    }
}

/** Relógio em segundos para as ondas lentas da luz. Só corre enquanto toca (economiza bateria). */
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
    reduceMotionOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val reduce = if (reduceMotionOverride != null) reduceMotionOverride else rememberReduceMotion()
    val a = animateColorAsState(accent.a, if (reduce) tween(0) else AuroMotion.accent(), label = "accentA")
    val b = animateColorAsState(accent.b, if (reduce) tween(0) else AuroMotion.accent(), label = "accentB")
    val state = remember(a, b) { AuroAccentState(a, b) }
    val light = rememberListenerLight(playing && !reduce, tilt && !reduce)
    val clock = rememberAuroClock(playing && !reduce)
    CompositionLocalProvider(
        LocalAuroAccent provides state,
        LocalAuroLight provides light,
        LocalAuroTime provides clock,
        LocalAuroSoft provides soft,
        LocalReduceMotionOverride provides reduceMotionOverride
    ) {
        Box(Modifier.fillMaxSize().background(AuroColor.Black)) { content() }
    }
}

// ─────────────────────────── Objeto de vidro ───────────────────────────

/** Button = círculo (anterior, play, próximo, ★, ⋮). Card = retângulo arredondado (Fila, Letras, mini player). */
enum class AuroGlassKind { Button, Card }

@Composable
fun AuroGlass(
    modifier: Modifier = Modifier,
    kind: AuroGlassKind = AuroGlassKind.Card,
    onClick: (() -> Unit)? = null,
    haptic: ((View) -> Unit)? = { AuroHaptics.click(it) },
    soft: Float = LocalAuroSoft.current, // 0 = reflexo agudo, 1 = bem borrado
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current.density
    val cfg = LocalConfiguration.current
    val view = LocalView.current
    val screenH = cfg.screenHeightDp * density
    var center by remember { mutableStateOf(Offset.Zero) } // centro do objeto na janela, px
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, AuroMotion.press(), label = "press")
    val isButton = kind == AuroGlassKind.Button
    val shape = if (isButton) CircleShape else RoundedCornerShape(AuroRadius.card)

    val clickModifier = if (onClick != null) {
        Modifier.clickable(interaction, indication = null) {
            haptic?.invoke(view)
            onClick()
        }
    } else Modifier

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(AURO_GLASS_AGSL) }
        val brush = remember(shader) { ShaderBrush(shader) }
        val light = LocalAuroLight.current
        val clock = LocalAuroTime.current
        val accent = LocalAuroAccent.current

        Box(
            modifier
                .onGloballyPositioned { center = it.boundsInWindow().center }
                .graphicsLayer { val s = 1f - 0.04f * press; scaleX = s; scaleY = s }
                .drawBehind {
                    val l = light()
                    val radius = if (isButton) min(size.width, size.height) / 2f else AuroRadius.card.toPx()
                    val bevel = if (isButton) 0.34f * radius else 13.dp.toPx()
                    val a = accent.a.value
                    val b = accent.b.value
                    shader.setFloatUniform("uSize", size.width, size.height)
                    shader.setFloatUniform("uRadius", radius)
                    shader.setFloatUniform("uCenter", center.x, center.y)
                    shader.setFloatUniform("uScreenH", screenH)
                    shader.setFloatUniform("uLightDir", l.dx, l.dy)
                    shader.setFloatUniform("uAccent", a.red, a.green, a.blue)
                    shader.setFloatUniform("uAccent2", b.red, b.green, b.blue)
                    shader.setFloatUniform("uBevel", bevel)
                    shader.setFloatUniform("uSoft", soft)
                    shader.setFloatUniform("uSpec", (if (isButton) 1.5f else 1.3f) * l.gain)
                    shader.setFloatUniform("uBody", 0f) // 0 = corpo de vidro preto total
                    shader.setFloatUniform("uTime", clock())
                    shader.setFloatUniform("uPx", density)
                    shader.setFloatUniform("uPress", press)
                    drawRect(brush)
                }
                .clip(shape)
                .then(clickModifier),
            content = content,
        )
    } else {
        // Fallback robusto para Android < 13: Objeto preto com aro branco sutil de 10%
        Box(
            modifier = modifier
                .graphicsLayer { val s = 1f - 0.04f * press; scaleX = s; scaleY = s }
                .background(AuroColor.Black, shape)
                .border(1.dp, Color(0x1AFFFFFF), shape)
                .clip(shape)
                .then(clickModifier),
            content = content,
        )
    }
}

// Shader AGSL do vidro preto: luz paralela do ouvinte refletida nas bordas.
private const val AURO_GLASS_AGSL = """
// Aurō Glass v3: vidro preto sobre o vazio (#000000), iluminado pela luz do OUVINTE:
// uma luz paralela vinda de baixo (igual em todos os objetos) que acompanha a inclinação do celular.
// A luz não é desenhada: aparece só como reflexo nas bordas. AGSL, Android 13+.

uniform float2 uSize;      // px do objeto
uniform float  uRadius;    // px do raio do canto (círculo: metade do lado)
uniform float2 uCenter;    // centro do objeto na janela, px (ondas lentas e gradiente vertical)
uniform float  uScreenH;   // altura da tela, px
uniform float2 uLightDir;  // direção PARA a luz no plano da tela (y pra baixo): (0,1) = vem de baixo
uniform float3 uAccent;    // cor da luz (vem da capa)
uniform float3 uAccent2;   // cor secundária (vem da capa)
uniform float  uBevel;     // px: largura do chanfro (usada só no reflexo interno fraco)
uniform float  uSoft;      // 0 = reflexo agudo, 1 = bem borrado
uniform float  uSpec;      // intensidade do reflexo
uniform float  uBody;      // luz no miolo (0 = preto total)
uniform float  uTime;      // s
uniform float  uPx;        // 1dp em px
uniform float  uPress;     // 0..1 (toque)

float sdRound(float2 p) {
    float2 q = abs(p) - 0.5 * uSize + uRadius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - uRadius;
}

float hash2(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Intensidade da luz neste ponto: um pouco mais forte embaixo (perto do ouvinte), com ondas lentas.
float lightI(float2 g) {
    float yN = clamp(g.y / uScreenH, 0.0, 1.0);
    float base = mix(0.6, 1.0, yN);
    float flux = 1.0 + 0.05 * sin(1.3 * uTime) + 0.03 * sin(2.9 * uTime + 1.7);
    float2 gd = g / uPx;                                   // em dp
    float wave = sin(gd.x * 0.055 + 1.4 * sin(gd.y * 0.04 + uTime * 0.45) + uTime * 0.3);
    return base * flux * (1.0 + 0.12 * wave);
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord - 0.5 * uSize;
    float d = sdRound(p);
    float a = 1.0 - smoothstep(-0.7, 0.7, d);
    if (a <= 0.0) return half4(0.0);
    float depth = max(-d, 0.0);
    float dd = depth / uPx;                                 // profundidade em dp: a espessura das linhas é igual em todos os objetos
    float2 n = normalize(float2(sdRound(p + float2(1.0, 0.0)) - sdRound(p - float2(1.0, 0.0)),
                                sdRound(p + float2(0.0, 1.0)) - sdRound(p - float2(0.0, 1.0))) + float2(1e-5));
    float2 g = uCenter + p;
    float I = lightI(g);
    float press = 1.0 + 0.6 * uPress;

    // luz paralela: a MESMA direção em todos os objetos. Dispersão por canal: a direção gira ±2°.
    float2 L = normalize(uLightDir);
    float2 Lp = float2(-L.y, L.x);
    float3 ndl = float3(max(dot(n, L * 0.99939 + Lp * 0.03490), 0.0),
                        max(dot(n, L), 0.0),
                        max(dot(n, L * 0.99939 - Lp * 0.03490), 0.0));

    // reflexo: um lóbulo estreito e um largo; uSoft controla o quão borrado
    float eN = mix(10.0, 4.0, uSoft);
    float eW = mix(3.0, 1.4, uSoft);
    float3 lN = pow(ndl, float3(eN));
    float3 lW = pow(ndl, float3(eW));
    float core = exp(-dd / 1.0);                            // linha de ~1dp
    float mid = exp(-dd / (2.2 + 2.6 * uSoft));             // faixa de poucos dp
    float bloom = exp(-dd / (5.0 + 6.0 * uSoft));           // brilho suave que entra no vidro
    float3 hot = mix(uAccent, float3(1.0), 0.55);
    float3 refl = hot * (lN * (0.55 * core + 0.55 * mid) + lW * (0.30 * mid + 0.10 * bloom)) * I;
    float3 sparkCol = mix(hot, float3(1.0), 0.6) * lN.g * core * mix(0.9, 0.5, uSoft) * I;

    // linha de contorno com presença: contínua em volta do objeto, mais forte do lado da luz
    float3 rimCol = mix(float3(1.0), uAccent, 0.25) * exp(-dd / 1.0) * (0.06 + 0.20 * pow(ndl.g, 1.5)) * I;

    // a luz que atravessa sai pelo lado oposto: fio interno fraco
    float opp = pow(max(-dot(n, L), 0.0), 3.0);
    float band = exp(-pow((depth - 0.5 * uBevel) / (0.3 * uBevel), 2.0));
    float3 inner = mix(uAccent2, hot, 0.4) * opp * band * 0.07 * I;

    float3 body = uAccent * uBody * I * exp(-dd / 6.0);     // 0 = corpo preto

    float3 c = body + (refl + sparkCol) * uSpec * press + rimCol * press + inner;
    float mx = max(c.r, max(c.g, c.b));
    c = max(c + (hash2(fragCoord) - 0.5) * (1.6 / 255.0) * smoothstep(0.0, 0.03, mx), 0.0);   // dither só onde há luz
    return half4(half3(clamp(c, 0.0, 1.0) * a), half(a));
}
"""
