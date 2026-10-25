package com.lucas.player.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.ui.shaders.LiquidAgslShaders
import com.lucas.player.ui.theme.AuraPalette

val LocalLiquidBackdrop = compositionLocalOf<GraphicsLayer?> { null }

/**
 * 1. ROOT CONTAINER QUE RENDERIZA A AURA E GRAVA O BACKDROP EM TEMPO REAL PARA OS MODAIS E BARRAS DE VIDRO
 */
@Composable
fun AuraLiquidRootBox(
    palette: AuraPalette,
    backgroundContent: @Composable BoxScope.() -> Unit,
    glassOverlays: @Composable BoxScope.() -> Unit
) {
    val backdropLayer = rememberGraphicsLayer()
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                time = (now - start) / 1_000_000_000f
            }
        }
    }

    val cDeep by animateColorAsState(palette.deepBase, tween(900), label = "deep")
    val cPrim by animateColorAsState(palette.primaryAura, tween(900), label = "prim")
    val cSec by animateColorAsState(palette.secondaryAura, tween(900), label = "sec")
    val cGlow by animateColorAsState(palette.accentGlow, tween(900), label = "glow")

    val auraShader = remember { RuntimeShader(LiquidAgslShaders.AURA_BACKGROUND_SHADER) }

    CompositionLocalProvider(LocalLiquidBackdrop provides backdropLayer) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Tudo aqui dentro é gravado no backdropLayer para ser desfocado e refratado pelos vidros acima
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        backdropLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(backdropLayer)
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    auraShader.setFloatUniform("uResolution", size.width, size.height)
                    auraShader.setFloatUniform("uTime", time)
                    auraShader.setFloatUniform("uColorDeep", cDeep.red, cDeep.green, cDeep.blue, 1f)
                    auraShader.setFloatUniform("uColorPrimary", cPrim.red, cPrim.green, cPrim.blue, 1f)
                    auraShader.setFloatUniform("uColorSecondary", cSec.red, cSec.green, cSec.blue, 1f)
                    auraShader.setFloatUniform("uColorGlow", cGlow.red, cGlow.green, cGlow.blue, 1f)
                    drawRect(ShaderBrush(auraShader))
                }
                backgroundContent()
            }

            // Modais, Equalizador, Letras e Floating Bars que refratam o backdropLayer
            glassOverlays()
        }
    }
}

/**
 * 2. PAINEL DE VIDRO REAL COM BLUR PROFUNDO + REFRAÇÃO DE LENTE SDF + DISPERSÃO CROMÁTICA
 * Usado em: Floating Bottom Nav, Mini-Player, Equalizador, Letras, Fila, Sleep Timer e Info Técnica.
 */
@Composable
fun RealLiquidGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    blurRadius: Dp = 34.dp,
    refractionPx: Float = 24f,
    tint: Color = Color(0xFF1A1642).copy(alpha = 0.56f),
    content: @Composable BoxScope.() -> Unit
) {
    val backdropLayer = LocalLiquidBackdrop.current
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val blurPx = with(density) { blurRadius.toPx() }

    var bounds by remember { mutableStateOf(Rect.Zero) }
    val lensShader = remember { RuntimeShader(LiquidAgslShaders.REAL_GLASS_LENS_SHADER) }
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clip(shape)
    ) {
        // Camada 1: Fundo capturado, desfocado (Blur) e refratado pela lente AGSL
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    if (size.width > 0f && size.height > 0f) {
                        lensShader.setFloatUniform("uResolution", size.width, size.height)
                        lensShader.setFloatUniform("uCornerRadius", radiusPx)
                        lensShader.setFloatUniform("uRefractionPx", refractionPx)
                        lensShader.setFloatUniform("uDispersion", 0.34f)
                        lensShader.setFloatUniform("uTint", tint.red, tint.green, tint.blue, tint.alpha)

                        val blurEff = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                        val lensEff = RenderEffect.createRuntimeShaderEffect(lensShader, "content")
                        // Primeiro aplica Blur nos pixels do fundo, depois aplica a lente refrativa!
                        renderEffect = RenderEffect.createChainEffect(lensEff, blurEff).asComposeRenderEffect()
                    }
                }
                .drawWithContent {
                    if (backdropLayer != null && bounds != Rect.Zero) {
                        drawContext.canvas.save()
                        drawContext.canvas.translate(-bounds.left, -bounds.top)
                        drawLayer(backdropLayer)
                        drawContext.canvas.restore()
                    } else {
                        drawRect(tint)
                    }
                }
        )

        // Camada 2: Conteúdo nítido + Borda especular diagonal
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.48f),
                            Color.White.copy(alpha = 0.10f),
                            Color(0xFF9E96FF).copy(alpha = 0.32f)
                        ),
                        start = Offset.Zero,
                        end = Offset(800f, 800f)
                    ),
                    shape = shape
                ),
            content = content
        )
    }
}

/**
 * 3. SLIDER 52 HORIZONTAL (Para SeekBar com Waveform sutil e Controles de Tonalidade do EQ)
 */
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
    var isDragging by remember { mutableStateOf(false) }
    val dragAnim by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "drag"
    )
    val scaleY by animateFloatAsState(
        targetValue = if (isDragging) 1.10f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "scaleY"
    )

    val shader = remember { RuntimeShader(LiquidAgslShaders.SLIDER_52_HORIZONTAL_SHADER) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(1f, scaleY)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pos ->
                        isDragging = true
                        onProgressChange((pos.x / size.width).coerceIn(0f, 1f))
                        tryAwaitRelease()
                        isDragging = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onProgressChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            shader.setFloatUniform("uResolution", size.width, size.height)
            shader.setFloatUniform("uProgress", progress.coerceIn(0f, 1f))
            shader.setFloatUniform("uDragHighlight", dragAnim)
            drawRect(ShaderBrush(shader))

            // Se houver picos de onda, desenha de forma sutil e elegante dentro da cápsula
            if (waveformPeaks != null && waveformPeaks.isNotEmpty()) {
                val count = waveformPeaks.size
                val startX = 76.dp.toPx()
                val endX = size.width - 64.dp.toPx()
                val availW = (endX - startX).coerceAtLeast(1f)
                val step = availW / count
                val barW = (step * 0.48f).coerceIn(1.5f, 4f)
                val maxBarH = size.height * 0.42f
                val fillLimitX = size.width * progress.coerceIn(0f, 1f)

                for (i in 0 until count) {
                    val x = startX + i * step
                    val amp = waveformPeaks[i].coerceIn(0.12f, 1f)
                    val h = maxBarH * amp
                    val isPlayed = x <= fillLimitX
                    drawRoundRect(
                        color = if (isPlayed) Color(0xFFDCE6FF).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.16f),
                        topLeft = Offset(x, (size.height - h) * 0.5f),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW * 0.5f, barW * 0.5f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (rightText != null) {
                Text(
                    text = rightText,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 4. SLIDER 52 VERTICAL (Para as 10 bandas do Equalizador Biquad)
 */
@Composable
fun Slider52Vertical(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val shader = remember { RuntimeShader(LiquidAgslShaders.SLIDER_52_VERTICAL_SHADER) }

    Canvas(
        modifier = modifier
            .width(26.dp)
            .height(155.dp)
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val newProg = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                    onProgressChange(newProg)
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    val newProg = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onProgressChange(newProg)
                }
            }
    ) {
        shader.setFloatUniform("uResolution", size.width, size.height)
        shader.setFloatUniform("uProgress", progress.coerceIn(0f, 1f))
        drawRect(ShaderBrush(shader))
    }
}

/**
 * 5. LIQUID GLASS TOGGLE (Substitui o Switch cinza do Material Design)
 */
@Composable
fun LiquidGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "toggleOffset"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF6368F2).copy(alpha = 0.65f) else Color(0xFF1F222D).copy(alpha = 0.75f),
        animationSpec = tween(250),
        label = "toggleColor"
    )

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(trackColor)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.12f))
                ),
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFFD0E4FF))
                    )
                )
        )
    }
}

/**
 * 6. BOTÃO CIRCULAR DE VIDRO FIGMA (Sem anel ciano neon!)
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
                            Color(0xFF7B7EFF).copy(alpha = 0.78f),
                            Color(0xFF493FA8).copy(alpha = 0.68f),
                            Color(0xFF281F68).copy(alpha = 0.85f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF5A549E).copy(alpha = 0.45f),
                            Color(0xFF262052).copy(alpha = 0.45f)
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color(0xFFA59FFF).copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.30f)
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
