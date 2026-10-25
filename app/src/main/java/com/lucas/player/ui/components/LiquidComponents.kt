package com.lucas.player.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.ui.shaders.LiquidAgslShaders
import com.lucas.player.ui.theme.AuraPalette
import kotlin.math.max

/**
 * 1. AURA MESH BACKGROUND
 * Utiliza o RuntimeShader AGSL quando Android >= 13 (Tiramisu), com fallback
 * gracioso para malha de gradientes radiais animados no Android legado.
 */
@Composable
fun AuraBackground(
    palette: AuraPalette,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val animBase by animateColorAsState(palette.base, tween(900), label = "base")
    val animPrimary by animateColorAsState(palette.primary, tween(900), label = "primary")
    val animSecondary by animateColorAsState(palette.secondary, tween(900), label = "secondary")
    val animAccent by animateColorAsState(palette.accent, tween(900), label = "accent")

    var timeSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                timeSeconds = (frameMs - startTime) / 1000f
            }
        }
    }

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                RuntimeShader(LiquidAgslShaders.AURA_BACKGROUND_SHADER)
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                    try {
                        shader.setFloatUniform("uResolution", size.width, size.height)
                        shader.setFloatUniform("uTime", timeSeconds)
                        shader.setColorUniform(
                            "uColorBase",
                            android.graphics.Color.valueOf(animBase.red, animBase.green, animBase.blue, animBase.alpha)
                        )
                        shader.setColorUniform(
                            "uColorPrimary",
                            android.graphics.Color.valueOf(animPrimary.red, animPrimary.green, animPrimary.blue, animPrimary.alpha)
                        )
                        shader.setColorUniform(
                            "uColorSecondary",
                            android.graphics.Color.valueOf(animSecondary.red, animSecondary.green, animSecondary.blue, animSecondary.alpha)
                        )
                        shader.setColorUniform(
                            "uColorAccent",
                            android.graphics.Color.valueOf(animAccent.red, animAccent.green, animAccent.blue, animAccent.alpha)
                        )
                        drawRect(brush = ShaderBrush(shader))
                    } catch (_: Throwable) {
                        drawFallbackMesh(size, animBase, animPrimary, animSecondary, animAccent)
                    }
                } else {
                    drawFallbackMesh(size, animBase, animPrimary, animSecondary, animAccent)
                }
            }
    ) {
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFallbackMesh(
    size: Size,
    base: Color,
    primary: Color,
    secondary: Color,
    accent: Color
) {
    drawRect(color = base)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.55f), Color.Transparent),
            center = Offset(size.width * 0.35f, size.height * 0.25f),
            radius = size.width * 0.85f
        )
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(secondary.copy(alpha = 0.45f), Color.Transparent),
            center = Offset(size.width * 0.8f, size.height * 0.65f),
            radius = size.width * 0.9f
        )
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.85f),
            radius = size.width * 0.7f
        )
    )
}

/**
 * 2. GLASS SURFACE CARD (Figma Dark Indigo Glass)
 * Superfície translúcida com borda chanfrada especular e mola elástica de toque.
 */
@Composable
fun GlassSurfaceCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    backgroundColor: Color = Color(0xFF141026).copy(alpha = 0.58f),
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1.0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "glassScale"
    )

    val shape = RoundedCornerShape(cornerRadius)

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.42f),
            Color.White.copy(alpha = 0.08f),
            Color(0xFF8D72E1).copy(alpha = 0.25f),
            Color(0xFF00E5FF).copy(alpha = 0.35f)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(backgroundColor)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onClick?.invoke() },
                            onLongPress = { onLongClick?.invoke() }
                        )
                    }
                } else Modifier
            ),
        content = content
    )
}

/**
 * 3. GLASS CIRCLE BUTTON (Controles de Reprodução)
 */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    isPrimary: Boolean = false,
    contentDescription: String? = null,
    tint: Color = Color.White
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1.0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 450f),
        label = "btnScale"
    )

    val bgBrush = if (isPrimary) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF8D72E1),
                Color(0xFF6C4AB6),
                Color(0xFF281E5D)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF262042).copy(alpha = 0.85f),
                Color(0xFF141026).copy(alpha = 0.70f)
            )
        )
    }

    val borderBrush = Brush.linearGradient(
        colors = if (isPrimary) {
            listOf(Color.White.copy(alpha = 0.85f), Color(0xFF00E5FF).copy(alpha = 0.7f))
        } else {
            listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))
        }
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isPrimary) {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = Color(0xFF8D72E1),
                        ambientColor = Color(0xFF6C4AB6)
                    )
                } else Modifier
            )
            .clip(CircleShape)
            .background(bgBrush)
            .border(width = if (isPrimary) 1.5.dp else 1.dp, brush = borderBrush, shape = CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(if (isPrimary) size * 0.52f else size * 0.44f)
        )
    }
}

/**
 * 4. REFRACTIVE WAVEFORM SEEK BAR (Slider 52 + Waveform)
 * Cápsula vítrea líquida com crista refrativa e forma de onda dentro.
 */
@Composable
fun RefractiveWaveformSeekBar(
    progress: Float, // 0.0 a 1.0
    onSeek: (Float) -> Unit,
    waveformPeaks: FloatArray?,
    currentTimeText: String,
    totalTimeText: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF00E5FF)
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val activeProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)

    val heightDp by animateDpAsState(
        targetValue = if (isDragging) 54.dp else 46.dp,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 380f),
        label = "sliderHeight"
    )

    val sliderShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                RuntimeShader(LiquidAgslShaders.REFRACTIVE_SLIDER_SHADER)
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeek(dragProgress)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val p = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(p)
                }
            }
            .drawBehind {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && sliderShader != null) {
                    try {
                        sliderShader.setFloatUniform("uResolution", size.width, size.height)
                        sliderShader.setFloatUniform("uProgress", activeProgress)
                        sliderShader.setFloatUniform("uDragIntensity", if (isDragging) 1.0f else 0.0f)
                        sliderShader.setColorUniform(
                            "uGlowColor",
                            android.graphics.Color.valueOf(accentColor.red, accentColor.green, accentColor.blue, accentColor.alpha)
                        )
                        drawRoundRect(
                            brush = ShaderBrush(sliderShader),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.5f)
                        )
                    } catch (_: Throwable) {
                        drawFallbackSlider(size, activeProgress, accentColor)
                    }
                } else {
                    drawFallbackSlider(size, activeProgress, accentColor)
                }

                // Render subtle waveform bars inside the capsule
                if (waveformPeaks != null && waveformPeaks.isNotEmpty()) {
                    val count = waveformPeaks.size
                    val spacing = size.width / count.toFloat()
                    val barWidth = max(2f, spacing * 0.55f)
                    val centerY = size.height * 0.5f
                    val fillWidth = size.width * activeProgress

                    for (i in 0 until count) {
                        val x = i * spacing + spacing * 0.5f
                        val peak = waveformPeaks[i].coerceIn(0.08f, 1.0f)
                        val barHeight = size.height * 0.65f * peak
                        val isPlayed = x <= fillWidth

                        drawLine(
                            color = if (isPlayed) accentColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f),
                            start = Offset(x, centerY - barHeight * 0.5f),
                            end = Offset(x, centerY + barHeight * 0.5f),
                            strokeWidth = barWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Inner Labels (Current time at left, Total time at right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentTimeText,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = totalTimeText,
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFallbackSlider(
    size: Size,
    progress: Float,
    accentColor: Color
) {
    val radius = size.height * 0.5f
    // Background track
    drawRoundRect(
        color = Color(0xFF1B172B).copy(alpha = 0.75f),
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
    )
    // Progress fill
    val fillWidth = max(size.height, size.width * progress)
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0xFF6C4AB6).copy(alpha = 0.7f), accentColor.copy(alpha = 0.85f))
        ),
        size = Size(fillWidth, size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
    )
    // Outer specular rim
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent, Color(0xFF8D72E1).copy(alpha = 0.3f))
        ),
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
    )
}

/**
 * 5. PILL FILTER CHIP (Categorias de Navegação)
 */
@Composable
fun PillFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgBrush = if (isSelected) {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF8D72E1), Color(0xFF00E5FF))
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF1E1A34).copy(alpha = 0.6f), Color(0xFF141026).copy(alpha = 0.6f))
        )
    }

    val textColor = if (isSelected) Color(0xFF0A0718) else Color.White.copy(alpha = 0.80f)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgBrush)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (isSelected) {
                        listOf(Color.White.copy(alpha = 0.8f), Color(0xFF00E5FF))
                    } else {
                        listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                    }
                ),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
