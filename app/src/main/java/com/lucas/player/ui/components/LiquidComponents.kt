package com.lucas.player.ui.components

import android.graphics.RuntimeShader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.ui.shaders.LiquidAgslShaders
import com.lucas.player.ui.theme.AuraPalette
import kotlin.math.cos
import kotlin.math.sin

enum class AuraPreset(
    val auraColor: Color,
    val secondaryColor: Color,
    val glowHighlight: Color,
    val topBias: Float,
    val bottomBias: Float
) {
    // 1. Âmbar Dourado (Idêntico ao cartão 'Walk 4,151 steps' - 1000020365.jpg)
    WALK_AMBER(
        auraColor = Color(0xFFF5881F),
        secondaryColor = Color(0xFF9E4708),
        glowHighlight = Color(0xFFFFD875),
        topBias = 0.25f,
        bottomBias = 1.0f
    ),
    // 2. Rosa Magenta (Idêntico ao cartão 'Last test 3.3 Liter' - 1000020366.jpg)
    MAGENTA_PULSE(
        auraColor = Color(0xFFE655A5),
        secondaryColor = Color(0xFF7D2258),
        glowHighlight = Color(0xFFFFB0DC),
        topBias = 1.0f,
        bottomBias = 0.45f
    ),
    // 3. Laranja Coral (Idêntico ao cartão 'Breath Volume 5.5 L' - 1000020366.jpg)
    CORAL_BREATH(
        auraColor = Color(0xFFE84C27),
        secondaryColor = Color(0xFF8A220C),
        glowHighlight = Color(0xFFFFA878),
        topBias = 0.22f,
        bottomBias = 1.0f
    ),
    // 4. Verde Sálvia / Celadon (Idêntico ao painel 'Move camera closer to a sensor' - 1000020364.jpg)
    SAGE_SENSOR(
        auraColor = Color(0xFF88AD9E),
        secondaryColor = Color(0xFF496B5E),
        glowHighlight = Color(0xFFC8E6DA),
        topBias = 0.85f,
        bottomBias = 0.85f
    ),
    // 5. Azul Cobalto Profundo (Idêntico ao cartão 'Training' - 1000020366.jpg)
    COBALT_TRAINING(
        auraColor = Color(0xFF2848B8),
        secondaryColor = Color(0xFF102060),
        glowHighlight = Color(0xFF7898FF),
        topBias = 0.30f,
        bottomBias = 0.90f
    )
}

/**
 * 1. CARTÃO / PAINEL SQUIRCLE COM NÚCLEO PRETO OBSIDIANA (#050508) E AURA INTERNA NAS BORDAS
 * Sem recursão de GraphicsLayer, sem backdrop capture. Todo o bloom é gerado no próprio SDF!
 */
@Composable
fun InnerAuraCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 32.dp,
    glowSpreadDp: Dp = 54.dp,
    auraColor: Color = AuraPreset.WALK_AMBER.auraColor,
    secondaryColor: Color = AuraPreset.WALK_AMBER.secondaryColor,
    topBias: Float = 0.35f,
    bottomBias: Float = 1.0f,
    coreColor: Color = Color(0xFF050508),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val spreadPx = with(density) { glowSpreadDp.toPx() }

    val animAura by animateColorAsState(auraColor, tween(600), label = "aura")
    val animSec by animateColorAsState(secondaryColor, tween(600), label = "sec")

    val shader = remember { RuntimeShader(LiquidAgslShaders.INNER_AURA_CARD_SHADER) }
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .drawBehind {
                shader.setFloatUniform("uResolution", size.width, size.height)
                shader.setFloatUniform("uCornerRadius", radiusPx)
                shader.setFloatUniform("uGlowSpread", spreadPx)
                shader.setFloatUniform("uTopBias", topBias)
                shader.setFloatUniform("uBottomBias", bottomBias)
                shader.setFloatUniform("uAuraColor", animAura.red, animAura.green, animAura.blue, 1f)
                shader.setFloatUniform("uSecondaryColor", animSec.red, animSec.green, animSec.blue, 1f)
                shader.setFloatUniform("uCoreColor", coreColor.red, coreColor.green, coreColor.blue, 1f)
                drawRect(ShaderBrush(shader))
            },
        content = content
    )
}

/**
 * 2. BARRA DE PROGRESSO / SLIDER EM CÁPSULA LUMINOSA ESTRIADA (Estilo 'Walk' 1000020365.jpg)
 */
@Composable
fun WalkAuraProgressBar(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
    striped: Boolean = true,
    barColor: Color = AuraPreset.WALK_AMBER.auraColor,
    glowColor: Color = AuraPreset.WALK_AMBER.glowHighlight,
    overlayContent: (@Composable RowScope.() -> Unit)? = null
) {
    val shader = remember { RuntimeShader(LiquidAgslShaders.AURA_CAPSULE_SLIDER_SHADER) }
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onProgressChange((pos.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                ) { change, _ ->
                    change.consume()
                    onProgressChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            shader.setFloatUniform("uResolution", size.width, size.height)
            shader.setFloatUniform("uProgress", progress.coerceIn(0f, 1f))
            shader.setFloatUniform("uStripeMode", if (striped) 1f else 0f)
            shader.setFloatUniform("uBarColor", barColor.red, barColor.green, barColor.blue, 1f)
            shader.setFloatUniform("uGlowColor", glowColor.red, glowColor.green, glowColor.blue, 1f)
            drawRect(ShaderBrush(shader))
        }
        if (overlayContent != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                content = overlayContent
            )
        }
    }
}

/**
 * 3. MOSTRADOR RADIAL DE PRECISÃO (Idêntico ao anel de traços de 'Move camera closer to a sensor' - 1000020364.jpg)
 */
@Composable
fun SensorRadialTickDial(
    progress: Float,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFD4FF4A), // Verde-lima/amarelo técnico
    totalTicks: Int = 72,
    centerContent: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            val outerR = size.minDimension * 0.46f
            val baseInnerR = outerR - 10.dp.toPx()
            val activeTickIndex = (progress.coerceIn(0f, 1f) * totalTicks).toInt()

            // Linhas tracejadas de eixo técnico
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = outerR * 0.62f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(cx, cy - outerR),
                end = Offset(cx, cy + outerR),
                pathEffect = dashEffect
            )

            for (i in 0 until totalTicks) {
                val angleDeg = (i * 360f / totalTicks) - 90f
                val rad = Math.toRadians(angleDeg.toDouble())
                val distFromActive = kotlin.math.abs(i - activeTickIndex)
                val isPeak = distFromActive <= 3

                val tickLen = when {
                    distFromActive == 0 -> 28.dp.toPx()
                    distFromActive == 1 -> 21.dp.toPx()
                    distFromActive == 2 -> 15.dp.toPx()
                    else -> 8.dp.toPx()
                }
                val startR = baseInnerR
                val endR = startR + tickLen

                val x1 = cx + (startR * cos(rad)).toFloat()
                val y1 = cy + (startR * sin(rad)).toFloat()
                val x2 = cx + (endR * cos(rad)).toFloat()
                val y2 = cy + (endR * sin(rad)).toFloat()

                val tickColor = when {
                    distFromActive == 0 -> accentColor
                    isPeak -> Color.White.copy(alpha = 0.92f)
                    i <= activeTickIndex -> Color.White.copy(alpha = 0.65f)
                    else -> Color.White.copy(alpha = 0.28f)
                }

                drawLine(
                    color = tickColor,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = if (distFromActive == 0) 2.4.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        centerContent()
    }
}

/**
 * 4. ARCOS PONTILHADOS E ONDAS DE PRECISÃO (Idêntico à base dos cartões 'Last test' e 'Breath Volume' - 1000020366.jpg)
 */
@Composable
fun DottedArcWaveDecoration(
    modifier: Modifier = Modifier,
    dotColor: Color = Color.White.copy(alpha = 0.85f),
    arcColor: Color = Color.White.copy(alpha = 0.18f)
) {
    Canvas(modifier = modifier.fillMaxWidth().height(54.dp)) {
        val w = size.width
        val h = size.height
        val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)

        // 3 arcos pontilhados entrelaçados (igual ao cartão rosa 'Last test')
        for (idx in 0..2) {
            val centerX = w * (0.28f + idx * 0.22f)
            val radius = w * 0.28f
            drawCircle(
                color = arcColor,
                radius = radius,
                center = Offset(centerX, h * 1.15f),
                style = Stroke(width = 1.dp.toPx(), pathEffect = dash)
            )
        }

        // Pontos luminosos nos vértices (igual a 1000020366.jpg)
        val nodes = listOf(
            Offset(w * 0.22f, h * 0.28f),
            Offset(w * 0.34f, h * 0.72f),
            Offset(w * 0.56f, h * 0.45f),
            Offset(w * 0.82f, h * 0.78f)
        )
        nodes.forEach { pt ->
            drawCircle(color = dotColor, radius = 2.8.dp.toPx(), center = pt)
        }
    }
}

/**
 * 5. PÍLULA DE TOGGLE (Idêntico ao switch do cartão 'Training' - 1000020366.jpg)
 */
@Composable
fun TrainingPillToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "thumb"
    )
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(if (checked) Color(0xFF2848B8).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * 6. SLIDER VERTICAL EM CÁPSULA LUMINOSA (Para as bandas do equalizador)
 */
@Composable
fun VerticalAuraSlider(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barColor: Color = AuraPreset.WALK_AMBER.auraColor,
    glowColor: Color = AuraPreset.WALK_AMBER.glowHighlight
) {
    val shader = remember { RuntimeShader(LiquidAgslShaders.AURA_CAPSULE_VERTICAL_SLIDER_SHADER) }
    val haptics = LocalHapticFeedback.current
    var lastProg by remember { mutableFloatStateOf(progress) }

    Box(
        modifier = modifier
            .width(48.dp)
            .height(155.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onProgressChange(0.5f)
                    },
                    onTap = { pos ->
                        val newProg = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onProgressChange(newProg)
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    val newProg = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    if ((lastProg < 0.5f && newProg >= 0.5f) || (lastProg > 0.5f && newProg <= 0.5f)) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    lastProg = newProg
                    onProgressChange(newProg)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .width(28.dp)
                .height(155.dp)
        ) {
            shader.setFloatUniform("uResolution", size.width, size.height)
            shader.setFloatUniform("uProgress", progress.coerceIn(0f, 1f))
            shader.setFloatUniform("uBarColor", barColor.red, barColor.green, barColor.blue, 1f)
            shader.setFloatUniform("uGlowColor", glowColor.red, glowColor.green, glowColor.blue, 1f)
            drawRect(ShaderBrush(shader))
        }
    }
}

/**
 * 7. FUNDO ATMOSFÉRICO ESCURO OBSIDIANA / AZUL-COBALTO
 */
@Composable
fun AmbientDeepBackground(
    modifier: Modifier = Modifier,
    topGlowColor: Color = Color(0xFF142266)
) {
    val shader = remember { RuntimeShader(LiquidAgslShaders.DEEP_AMBIENT_BG_SHADER) }
    Canvas(modifier = modifier.fillMaxSize()) {
        shader.setFloatUniform("uResolution", size.width, size.height)
        shader.setFloatUniform("uTopGlowColor", topGlowColor.red, topGlowColor.green, topGlowColor.blue, 1f)
        drawRect(ShaderBrush(shader))
    }
}

/**
 * 8. BOTÃO CIRCULAR VÍTREO DE ALTO CONTRASTE (Para Controles de Mídia e Ações)
 */
@Composable
fun FigmaGlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    isPrimaryPlay: Boolean = false,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "btnScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .background(
                brush = if (isPrimaryPlay) {
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFA738),
                            Color(0xFFE85B12),
                            Color(0xFF6B1D00)
                        )
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A1C29).copy(alpha = 0.85f),
                            Color(0xFF090A10).copy(alpha = 0.95f)
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (isPrimaryPlay) {
                        listOf(Color.White.copy(alpha = 0.75f), Color(0xFFFFCC7A).copy(alpha = 0.40f))
                    } else {
                        listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))
                    }
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// -------------------------------------------------------------
// COMPATIBILITY ADAPTERS (Mantém compatibilidade com chamadas existentes sem quebras)
// -------------------------------------------------------------

@Composable
fun RealLiquidGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    blurRadius: Dp = 34.dp,
    refractionPx: Float = 24f,
    tint: Color = Color(0xFF050508),
    content: @Composable BoxScope.() -> Unit
) {
    InnerAuraCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        glowSpreadDp = 64.dp,
        auraColor = AuraPreset.SAGE_SENSOR.auraColor,
        secondaryColor = AuraPreset.SAGE_SENSOR.secondaryColor,
        coreColor = Color(0xFF050508),
        content = content
    )
}

@Composable
fun Slider52Horizontal(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    waveformPeaks: FloatArray? = null,
    leftText: String? = null,
    rightText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val tabularStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
    WalkAuraProgressBar(
        progress = progress,
        onProgressChange = onProgressChange,
        modifier = modifier,
        height = 48.dp,
        striped = true,
        barColor = AuraPreset.WALK_AMBER.auraColor,
        glowColor = AuraPreset.WALK_AMBER.glowHighlight,
        overlayContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(6.dp))
                }
                if (leftText != null) {
                    Text(
                        text = leftText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = tabularStyle
                    )
                }
            }
            if (rightText != null) {
                Text(
                    text = rightText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    style = tabularStyle
                )
            }
        }
    )
}

@Composable
fun Slider52Vertical(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    VerticalAuraSlider(
        progress = progress,
        onProgressChange = onProgressChange,
        modifier = modifier
    )
}

@Composable
fun LiquidGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    TrainingPillToggle(
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
fun AuraLiquidRootBox(
    palette: AuraPalette,
    backgroundContent: @Composable BoxScope.() -> Unit,
    glassOverlays: @Composable BoxScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AmbientDeepBackground(topGlowColor = palette.secondaryAura.copy(alpha = 0.45f))
        backgroundContent()
        glassOverlays()
    }
}
