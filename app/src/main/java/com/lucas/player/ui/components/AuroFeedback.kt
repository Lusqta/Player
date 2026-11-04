package com.lucas.player.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// 1. HÁPTICOS OFICIAIS (AURŌ HAPTICS - ULTRA RESPONSIVO PARA MOTOROLA EDGE)
// ─────────────────────────────────────────────────────────────────────────────

object AuroHaptics {
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun getVibrator(view: View?): Vibrator? {
        if (vibrator == null && view != null) {
            init(view.context)
        }
        return vibrator
    }

    // Flags com bypass de configurações globais e de view (garante resposta tátil no Moto Edge)
    private const val HAPTIC_FLAGS = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING

    fun click(view: View? = null) {
        val vib = getVibrator(view)
        if (vib != null && vib.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(20L)
                }
                return
            } catch (_: Exception) {}
        }
        view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HAPTIC_FLAGS)
    }

    fun confirm(view: View? = null) {
        val vib = getVibrator(view)
        if (vib != null && vib.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(35L)
                }
                return
            } catch (_: Exception) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM, HAPTIC_FLAGS)
        } else {
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HAPTIC_FLAGS)
        }
    }

    fun reject(view: View? = null) {
        val vib = getVibrator(view)
        if (vib != null && vib.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 25, 40, 30), intArrayOf(0, 255, 0, 255), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(longArrayOf(0, 25, 40, 30), -1)
                }
                return
            } catch (_: Exception) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.REJECT, HAPTIC_FLAGS)
        } else {
            view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HAPTIC_FLAGS)
        }
    }

    fun longPress(view: View? = null) {
        val vib = getVibrator(view)
        if (vib != null && vib.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(45L)
                }
                return
            } catch (_: Exception) {}
        }
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HAPTIC_FLAGS)
    }

    fun sliderTick(view: View? = null) {
        val vib = getVibrator(view)
        if (vib != null && vib.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(10L, 100))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(10L)
                }
                return
            } catch (_: Exception) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view?.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, HAPTIC_FLAGS)
        } else {
            view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HAPTIC_FLAGS)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. AVISO DE VIDRO (AURŌ SNACKBAR)
// ─────────────────────────────────────────────────────────────────────────────

@Stable
class AuroSnackbarState {
    data class SnackbarData(
        val text: String,
        val action: String? = null,
        val onAction: (() -> Unit)? = null,
        val durationMs: Long = if (action != null) 4000L else 3000L,
        val id: Long = System.currentTimeMillis()
    )

    var currentData by mutableStateOf<SnackbarData?>(null)
        private set

    private var dismissJob: Job? = null
    private var scope: CoroutineScope? = null

    fun bindScope(scope: CoroutineScope) {
        this.scope = scope
    }

    fun show(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        val data = SnackbarData(text, action, onAction)
        currentData = data
        dismissJob?.cancel()
        val s = scope ?: return
        dismissJob = s.launch {
            delay(data.durationMs)
            if (currentData?.id == data.id) {
                currentData = null
            }
        }
    }

    fun dismiss() {
        dismissJob?.cancel()
        currentData = null
    }
}

@Composable
fun rememberAuroSnackbarState(): AuroSnackbarState {
    val scope = rememberCoroutineScope()
    val state = remember { AuroSnackbarState() }
    DisposableEffect(scope) {
        state.bindScope(scope)
        onDispose { }
    }
    return state
}

@Composable
fun AuroSnackbarHost(
    state: AuroSnackbarState,
    modifier: Modifier = Modifier
) {
    val current = state.currentData
    val density = LocalDensity.current
    val view = LocalView.current

    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically(
            animationSpec = AuroMotion.liquid()
        ) { with(density) { 24.dp.roundToPx() } } + fadeIn(animationSpec = AuroMotion.liquid()),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 160, easing = LinearEasing)
        ),
        modifier = modifier
    ) {
        if (current != null) {
            AuroGlass(
                kind = AuroGlassKind.Card,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = current.text,
                        color = AuroColor.TextPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!current.action.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = current.action,
                            color = LocalAuroAccent.current.a.value,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    AuroHaptics.click(view)
                                    current.onAction?.invoke()
                                    state.dismiss()
                                }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. ESTADOS VAZIO E CARREGANDO (AURŌ EMPTY & LOADING)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AuroEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = AuroColor.TextPrimary,
            fontSize = 21.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = AuroColor.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            AuroGlass(
                kind = AuroGlassKind.Card,
                onClick = onAction,
                modifier = Modifier
                    .height(48.dp)
                    .wrapContentWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = actionLabel,
                        color = LocalAuroAccent.current.a.value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun AuroLoadingBar(
    modifier: Modifier = Modifier
) {
    val accent = LocalAuroAccent.current.a.value
    val transition = rememberInfiniteTransition(label = "AuroLoadingTransition")
    val progress by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AuroLoadingProgress"
    )

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                val barWidth = size.width * 0.3f
                val startX = (progress * size.width) - (barWidth / 2f)
                val endX = startX + barWidth

                // Track sutil de fundo em Accent A 15%
                drawLine(
                    color = accent.copy(alpha = 0.15f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = size.height
                )

                // Trecho deslizante de luz (30% da largura)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.7f),
                            Color.White,
                            accent.copy(alpha = 0.7f),
                            Color.Transparent
                        ),
                        startX = startX.coerceAtLeast(0f),
                        endX = endX.coerceAtMost(size.width)
                    )
                )
            }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. PLACEHOLDER DE CAPA (AURŌ COVER PLACEHOLDER)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AuroCoverPlaceholder(
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    shape: Shape = RoundedCornerShape(AuroRadius.cover)
) {
    val accent = LocalAuroAccent.current.a.value
    val noteColor = accent.copy(alpha = 0.6f)

    if (glass) {
        AuroGlass(
            kind = AuroGlassKind.Card,
            modifier = modifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AuroColor.Black),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val side = min(maxWidth, maxHeight)
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = noteColor,
                        modifier = Modifier.size(side * 0.4f)
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .background(AuroColor.Black, shape)
                .border(1.dp, Color(0x1AFFFFFF), shape), // 10% branco contorno
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val side = min(maxWidth, maxHeight)
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = noteColor,
                    modifier = Modifier.size(side * 0.4f)
                )
            }
        }
    }
}
