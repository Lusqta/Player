package com.lucas.player.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.data.CoverArtLoader
import com.lucas.player.model.Track
import com.lucas.player.ui.navigation.NavScreen
import com.lucas.player.ui.theme.*
import java.util.Locale

/**
 * AURŌ DESIGN SYSTEM v2 - COMPONENTES
 * Todos os objetos de vidro leem a luz global e são fundidos sobre o preto absoluto (#000000).
 */

fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val hours = totalSec / 3600
    val min = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, min, sec)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }
}

@Composable
fun rememberTrackCover(track: Track?, size: Int = 300): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(track?.id, size) {
        mutableStateOf(track?.let { CoverArtLoader.getCachedCover(it, size) })
    }
    LaunchedEffect(track?.id, size) {
        if (track != null) {
            if (bitmap == null) {
                CoverArtLoader.loadCover(context, track, size) { bmp ->
                    bitmap = bmp
                }
            }
        } else {
            bitmap = null
        }
    }
    return bitmap
}

/**
 * 1. BARRA DE PROGRESSO OFICIAL DO AURŌ DESIGN SYSTEM
 * Spec: "trilho 3dp #2D2D2D, preenchimento Accent A, bolinha branca de 10dp. Tempos em Label/TextSecondary."
 */
@Composable
fun AuroProgressBar(
    progressFraction: Float,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    onSeekJumpBackward: () -> Unit = {},
    onSeekJumpForward: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var showRemainingTime by remember { mutableStateOf(true) }

    val activeFraction = (if (isDragging) dragFraction else progressFraction).coerceIn(0f, 1f)
    val displayedMs = (activeFraction * durationMs).toLong()
    val remainingMs = (durationMs - displayedMs).coerceAtLeast(0)

    val seekBackLabel = stringResource(R.string.cd_seek_back_10)
    val seekForwardLabel = stringResource(R.string.cd_seek_forward_10)
    val cdProgress = stringResource(R.string.cd_track_progress)
    val stateDesc = stringResource(R.string.progress_state_desc, formatTimeMs(displayedMs), formatTimeMs(durationMs))

    Column(modifier = modifier.fillMaxWidth()) {
        // Trilho de 3dp com thumb de 10dp
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp) // Aumentado de 30.dp para 48.dp (trilho interno continua 3.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = cdProgress
                    stateDescription = stateDesc
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = displayedMs.toFloat(),
                        range = 0f..(durationMs.toFloat().coerceAtLeast(1f)),
                        steps = 0
                    )
                    setProgress { targetValue ->
                        if (durationMs > 0) {
                            val targetFrac = (targetValue / durationMs.toFloat()).coerceIn(0f, 1f)
                            onSeek(targetFrac)
                            true
                        } else false
                    }
                    customActions = listOf(
                        CustomAccessibilityAction(seekBackLabel) {
                            if (durationMs > 0) {
                                val currentMs = (activeFraction * durationMs).toLong()
                                val newMs = (currentMs - 10_000L).coerceAtLeast(0L)
                                onSeek(newMs.toFloat() / durationMs.toFloat())
                                onSeekJumpBackward()
                                true
                            } else false
                        },
                        CustomAccessibilityAction(seekForwardLabel) {
                            if (durationMs > 0) {
                                val currentMs = (activeFraction * durationMs).toLong()
                                val newMs = (currentMs + 10_000L).coerceAtMost(durationMs)
                                onSeek(newMs.toFloat() / durationMs.toFloat())
                                onSeekJumpForward()
                                true
                            } else false
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            AuroHaptics.click(view)
                        },
                        onDragEnd = {
                            onSeek(dragFraction)
                            isDragging = false
                            AuroHaptics.confirm(view)
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    ) { change, _ ->
                        change.consume()
                        val newFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                        if ((newFrac * 20).toInt() != (dragFraction * 20).toInt()) {
                            AuroHaptics.sliderTick(view)
                        }
                        dragFraction = newFrac
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        AuroHaptics.click(view)
                        onSeek(frac)
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidth = maxWidth
            val thumbOffset = ((totalWidth - 10.dp) * activeFraction).coerceAtLeast(0.dp)

            // Trilho #2D2D2D
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(AuroColor.Track, RoundedCornerShape(1.5.dp))
            )

            // Preenchimento com Branco Puro
            Box(
                modifier = Modifier
                    .fillMaxWidth(activeFraction)
                    .height(3.dp)
                    .background(Color.White, RoundedCornerShape(1.5.dp))
            )

            // Bolinha branca de 10dp
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(10.dp)
                    .background(Color.White, CircleShape)
            )
        }

        // Tempos (ex: 1:24 e -3:41 conforme o espécime)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTimeMs(displayedMs),
                style = AuroType.Caption.copy(
                    color = AuroColor.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = if (showRemainingTime) "-${formatTimeMs(remainingMs)}" else formatTimeMs(durationMs),
                style = AuroType.Caption.copy(
                    color = AuroColor.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum"
                ),
                modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
            )
        }
    }
}

/**
 * EDITORIAL FLOATING NAVBAR (Editorial Dark OLED)
 * Pílula flutuante (altura 56dp, CircleShape, margem lateral 24dp).
 * 4 abas minimalistas com apenas ícones: Início, Buscar, Biblioteca, Ajustes/Áudio.
 * Fundo #161618 @ 88% + borda 0.5dp White 8%.
 */
@Composable
fun EditorialFloatingNavbar(
    currentScreen: NavScreen,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onTuneClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF2121214))
            .navigationBarsPadding()
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = Color.White.copy(alpha = 0.08f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorialNavIcon(
                activeIcon = Icons.Rounded.Home,
                inactiveIcon = Icons.Outlined.Home,
                contentDescription = stringResource(R.string.tab_home),
                isActive = currentScreen is NavScreen.Home,
                onClick = onHomeClick
            )
            EditorialNavIcon(
                activeIcon = Icons.Rounded.Search,
                inactiveIcon = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.tab_search),
                isActive = currentScreen is NavScreen.Search,
                onClick = onSearchClick
            )
            EditorialNavIcon(
                activeIcon = Icons.Rounded.Favorite,
                inactiveIcon = Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(R.string.tab_library),
                isActive = currentScreen is NavScreen.LibraryRoot ||
                    currentScreen is NavScreen.CategoryList ||
                    currentScreen is NavScreen.Drilldown,
                onClick = onLibraryClick
            )
            EditorialNavIcon(
                activeIcon = Icons.Rounded.Tune,
                inactiveIcon = Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.tab_tune),
                isActive = currentScreen is NavScreen.Equalizer ||
                    currentScreen is NavScreen.Settings,
                onClick = onTuneClick
            )
        }
    }
}

@Composable
private fun EditorialNavIcon(
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else if (isActive) 1.05f else 1f,
        animationSpec = AuroMotion.press(),
        label = "nav_icon_scale"
    )

    val tint by animateColorAsState(
        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.45f),
        animationSpec = tween(200),
        label = "nav_icon_tint"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    AuroHaptics.tick(view)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isActive) activeIcon else inactiveIcon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * APPLE FLOATING TAB BAR - Retrocompatibilidade com EditorialFloatingNavbar
 */
@Composable
fun AppleFloatingTabBar(
    currentScreen: NavScreen,
    onHomeClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onQueueClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EditorialFloatingNavbar(
        currentScreen = currentScreen,
        onHomeClick = onHomeClick,
        onSearchClick = onSearchClick,
        onLibraryClick = onLibraryClick,
        onTuneClick = onQueueClick,
        modifier = modifier
    )
}

/**
 * TRANSLUCENT PLAY BUTTON (Editorial Dark OLED)
 * Botão circular translúcido (#FFFFFF @ 22%) com ícone branco e escala interativa.
 */
@Composable
fun TranslucentPlayButton(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = AuroMotion.press(),
        label = "translucent_play_scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(AuroColor.ControlGlass)
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    AuroHaptics.click(view)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
            tint = Color.White,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

/**
 * EDITORIAL MINI PLAYER (Editorial Dark OLED)
 * Pílula minimalista (altura 52dp, CircleShape, margem lateral 20dp).
 * Capa 36dp (raio 8dp), título 13.5sp, artista 12sp, TranslucentPlayButton (34dp) e SkipNext (20dp).
 */
@Composable
fun EditorialMiniPlayer(
    track: Track?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    coverBitmap: Bitmap?,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null) return

    val view = LocalView.current
    val progressFraction = if (durationMs > 0) {
        (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 20.dp)
            .clip(CircleShape)
            .background(AuroColor.SurfaceCard)
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .clickable(onClick = onOpenNowPlaying)
    ) {
        // Linha de progresso discreta de 2dp na base
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .fillMaxHeight()
                    .background(Color.White, CircleShape)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Capa 36dp com raio 8dp
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(AuroRadius.thumbnail)),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_album_cover),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AuroCoverPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        glass = false,
                        shape = RoundedCornerShape(AuroRadius.thumbnail)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Metadados com suporte a gestos
            Column(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                totalDragX = 0f
                                totalDragY = 0f
                            },
                            onDragEnd = {
                                if (abs(totalDragY) > abs(totalDragX) && totalDragY < -20f) {
                                    AuroHaptics.click(view)
                                    onOpenNowPlaying()
                                } else if (abs(totalDragX) > 30f) {
                                    if (totalDragX < 0) {
                                        AuroHaptics.click(view)
                                        onNext()
                                    } else {
                                        AuroHaptics.click(view)
                                        onPrevious()
                                    }
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y
                        }
                    }
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    style = AuroType.TrackTitle,
                    color = AuroColor.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                    style = AuroType.Subtitle,
                    color = AuroColor.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Botão Play Translúcido (34dp)
            TranslucentPlayButton(
                isPlaying = isPlaying,
                size = 34.dp,
                onClick = onPlayPause
            )

            Spacer(modifier = Modifier.width(4.dp))

            // SkipNext (20dp)
            IconButton(
                onClick = {
                    AuroHaptics.click(view)
                    onNext()
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.cd_next),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * APPLE MINI PLAYER - Retrocompatibilidade
 */
@Composable
fun AppleMiniPlayer(
    track: Track?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    coverBitmap: Bitmap?,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    EditorialMiniPlayer(
        track = track,
        isPlaying = isPlaying,
        progressMs = progressMs,
        durationMs = durationMs,
        coverBitmap = coverBitmap,
        onOpenNowPlaying = onOpenNowPlaying,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onPrevious = onPrevious,
        modifier = modifier
    )
}

/**
 * EDITORIAL SECTION HEADER (Editorial Dark OLED)
 * Título de seção 17sp SemiBold com ação opcional "Ver tudo".
 */
@Composable
fun EditorialSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = stringResource(R.string.view_all),
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = AuroType.SectionTitle,
            color = AuroColor.TextPrimary
        )
        if (actionLabel != null && onAction != null) {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .clip(CircleShape)
                    .background(AuroColor.PillMuted)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    style = AuroType.MicroLabel.copy(
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                )
            }
        }
    }
}

/**
 * EDITORIAL SCRUBBER (Editorial Dark OLED)
 * Barra de progresso com trilho de 2.5dp, thumb de 10dp e números monoespaçados.
 */
@Composable
fun EditorialScrubber(
    progressMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = AuroColor.ScrubberActive,
    trackColor: Color = AuroColor.ScrubberTrack,
    showTimestampsOnlyWhenDragging: Boolean = true
) {
    var isDragging by remember { mutableStateOf(false) }
    var draggingFraction by remember { mutableFloatStateOf(-1f) }
    val view = LocalView.current
    val currentFraction = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val displayFraction = if (draggingFraction >= 0f) draggingFraction else currentFraction

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(durationMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val f = (offset.x / size.width).coerceIn(0f, 1f)
                            draggingFraction = f
                            AuroHaptics.sliderTick(view)
                        },
                        onDragEnd = {
                            if (draggingFraction >= 0f) {
                                onSeek((draggingFraction * durationMs).toLong())
                            }
                            draggingFraction = -1f
                            isDragging = false
                            AuroHaptics.click(view)
                        },
                        onDragCancel = {
                            draggingFraction = -1f
                            isDragging = false
                        }
                    ) { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        draggingFraction = f
                    }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek((f * durationMs).toLong())
                        AuroHaptics.sliderTick(view)
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track base
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(CircleShape)
                    .background(trackColor)
            )
            // Active progress
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayFraction)
                    .height(2.5.dp)
                    .clip(CircleShape)
                    .background(activeColor)
            )
            // Thumb
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayFraction),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }

        // Time labels com AnimatedVisibility
        AnimatedVisibility(
            visible = !showTimestampsOnlyWhenDragging || isDragging,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 2.dp, top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val elapsed = if (draggingFraction >= 0f) (draggingFraction * durationMs).toLong() else progressMs
                val remaining = (durationMs - elapsed).coerceAtLeast(0)

                Text(
                    text = formatTimeMs(elapsed),
                    style = AuroType.MicroLabel.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum"
                    ),
                    color = AuroColor.TextSecondary
                )
                Text(
                    text = "-${formatTimeMs(remaining)}",
                    style = AuroType.MicroLabel.copy(
                        fontFamily = FontFamily.Monospace,
                        fontFeatureSettings = "tnum"
                    ),
                    color = AuroColor.TextSecondary
                )
            }
        }
    }
}

/**
 * Transição suave de fade no scroll para que o conteúdo passe por trás das barras flutuantes.
 */
@Composable
fun ScrollEdgeFade(
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    top: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colors = if (top) {
                        listOf(AuroColor.Black, Color.Transparent)
                    } else {
                        listOf(Color.Transparent, AuroColor.Black)
                    }
                )
            )
    )
}

/**
 * Controle de Volume estilo Apple Music com ícones nas pontas e trilho de 6dp.
 */
@Composable
fun AppleVolumeSlider(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVol = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }
    var currentVol by remember {
        mutableIntStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
    }
    val view = LocalView.current

    // Sincronizar com botões físicos de volume em tempo real
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: android.content.Intent?) {
                currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            }
        }
        val filter = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    val fraction = if (maxVol > 0) (currentVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f) else 0f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.VolumeDown,
            contentDescription = null,
            tint = AuroColor.TextSecondary,
            modifier = Modifier.size(18.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(maxVol) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        val targetVol = (f * maxVol).toInt().coerceIn(0, maxVol)
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
                        currentVol = targetVol
                        AuroHaptics.sliderTick(view)
                    }
                }
                .pointerInput(maxVol) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        val targetVol = (f * maxVol).toInt().coerceIn(0, maxVol)
                        if (targetVol != currentVol) {
                            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
                            currentVol = targetVol
                            AuroHaptics.sliderTick(view)
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.16f))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
            )
        }

        Icon(
            imageVector = Icons.Rounded.VolumeUp,
            contentDescription = null,
            tint = AuroColor.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * AURO DOCK (Mantido para compatibilidade, encapsulando as duas barras flutuantes se necessário)
 */
@Composable
fun AuroDock(
    currentScreen: NavScreen,
    currentTrack: Track?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    coverBitmap: Bitmap?,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onEqClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (currentTrack != null) {
            AppleMiniPlayer(
                track = currentTrack,
                isPlaying = isPlaying,
                progressMs = progressMs,
                durationMs = durationMs,
                coverBitmap = coverBitmap,
                onOpenNowPlaying = onOpenNowPlaying,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious
            )
        }

        AppleFloatingTabBar(
            currentScreen = currentScreen,
            onHomeClick = { /* Home */ },
            onLibraryClick = onLibraryClick,
            onQueueClick = { /* Queue */ },
            onSearchClick = onSearchClick
        )
    }
}

/**
 * 2. MINI PLAYER EM VIDRO PRETO (AURŌ GLASS CARD)
 * Posicionado embaixo na tela (próximo à luz global em y=0.88), captura os reflexos na borda inferior.
 */
@Composable
fun AuroMiniPlayer(
    track: Track?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    coverBitmap: Bitmap?,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null) return

    val progressFraction = if (durationMs > 0) {
        (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    AuroGlass(
        kind = AuroGlassKind.Card,
        onClick = onOpenNowPlaying,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Linha fina de progresso de 2dp no topo do vidro
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(AuroColor.Track)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(Color.White)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val view = LocalView.current

                // Capa quadrada (40x40) com cantos arredondados
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_album_cover),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    AuroCoverPlaceholder(
                        modifier = Modifier.size(40.dp),
                        glass = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Título e Artista
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = AuroType.Body.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                        style = AuroType.Caption.copy(color = AuroColor.TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Ações de reprodução
                IconButton(onClick = { AuroHaptics.click(view); onPrevious() }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_previous),
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = { AuroHaptics.confirm(view); onPlayPause() }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(onClick = { AuroHaptics.click(view); onNext() }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.cd_next),
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * 3. NAVBAR INFERIOR PERSISTENTE (AURŌ NAVBAR)
 * Senta diretamente sobre o fundo preto #000000, com linha de 1dp Hairline no topo.
 */
@Composable
fun AuroNavbar(
    currentScreen: NavScreen,
    onEqClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AuroColor.Black,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = AuroColor.Hairline)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AuroNavbarTab(
                icon = Icons.Rounded.GraphicEq,
                label = stringResource(R.string.tab_equalizer),
                isActive = currentScreen is NavScreen.Equalizer,
                onClick = onEqClick
            )

            AuroNavbarTab(
                icon = Icons.Rounded.LibraryMusic,
                label = stringResource(R.string.tab_library),
                isActive = currentScreen is NavScreen.LibraryRoot || currentScreen is NavScreen.CategoryList || currentScreen is NavScreen.Drilldown,
                onClick = onLibraryClick
            )

            AuroNavbarTab(
                icon = Icons.Rounded.Search,
                label = stringResource(R.string.tab_search),
                isActive = currentScreen is NavScreen.Search,
                onClick = onSearchClick
            )

            AuroNavbarTab(
                icon = Icons.Rounded.Menu,
                label = stringResource(R.string.tab_menu),
                isActive = false,
                onClick = onMenuClick
            )
        }
    }
}

@Composable
private fun AuroNavbarTab(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val tint = if (isActive) AuroColor.TextPrimary else AuroColor.TextTertiary
    val view = LocalView.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "tabScale"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    AuroHaptics.click(view)
                    onClick()
                }
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = AuroType.NavLabel.copy(
                color = tint,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
            )
        )
    }
}

/**
 * Três barras animadas de equalizador sobre a miniatura da faixa tocando.
 * Param quando pausado ou quando reduce motion estiver ativo.
 */
@Composable
fun AuroEqualizerBars(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val shouldAnimate = isPlaying && !reduceMotion

    val infiniteTransition = rememberInfiniteTransition(label = "auro_eq_bars")

    val height1 by if (shouldAnimate) {
        infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.90f,
            animationSpec = infiniteRepeatable(
                animation = tween(460, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }

    val height2 by if (shouldAnimate) {
        infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(620, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        )
    } else {
        remember { mutableFloatStateOf(0.70f) }
    }

    val height3 by if (shouldAnimate) {
        infiniteTransition.animateFloat(
            initialValue = 0.20f,
            targetValue = 0.80f,
            animationSpec = infiniteRepeatable(
                animation = tween(520, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        )
    } else {
        remember { mutableFloatStateOf(0.45f) }
    }

    Row(
        modifier = modifier.size(width = 18.dp, height = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(height1)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(height2)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(height3)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
    }
}

/**
 * 4. LINHA DE FAIXA NA LISTA (AURŌ / EDITORIAL TRACK ROW)
 * Specs: Altura 52dp, miniatura 36dp (raio 8dp), título TrackTitle (13.5sp), artista Subtitle (12sp),
 * sem divisores na lista, menu com três pontos horizontais (•••).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuroTrackRow(
    track: Track,
    trackIndex: Int,
    isPlayingThis: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasError: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    showFormatTag: Boolean = true,
    isAudioPlaying: Boolean = LocalAuroIsPlaying.current
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = AuroMotion.press(),
        label = "track_press_scale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    AuroHaptics.click(view)
                    onClick()
                },
                onLongClick = {
                    if (!isSelectionMode) {
                        AuroHaptics.longPress(view)
                        onLongClick()
                    }
                }
            )
            .padding(horizontal = AuroSpace.ScreenMargin),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicador de Seleção Múltipla: 22dp à esquerda
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .border(
                        width = if (isSelected) 0.dp else 1.5.dp,
                        color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.30f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        val coverBitmap = rememberTrackCover(track, 100)

        if (coverBitmap != null) {
            // Com capa: miniatura de 36dp com raio 8dp
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(AuroRadius.thumbnail)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isPlayingThis) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AuroEqualizerBars(
                            isPlaying = isAudioPlaying,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            // Sem capa
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPlayingThis) {
                    AuroEqualizerBars(
                        isPlaying = isAudioPlaying,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Título e Artista
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasError) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = stringResource(R.string.cd_error_indicator),
                        tint = AuroColor.Danger,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = track.title,
                    style = AuroType.TrackTitle.copy(
                        color = if (hasError) AuroColor.Danger else AuroColor.TextPrimary,
                        fontWeight = if (isPlayingThis) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(1.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = AuroType.Subtitle,
                    color = AuroColor.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (track.rating > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "★ ${track.rating}",
                        style = AuroType.MicroLabel.copy(
                            color = Color.White.copy(alpha = 0.65f),
                            fontFeatureSettings = "tnum"
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Tag de formato de áudio (FLAC, MP3, etc.)
        if (showFormatTag && track.formatTag.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = track.formatTag,
                    style = AuroType.FastScroller.copy(
                        color = AuroColor.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Duração da faixa
        Text(
            text = formatTimeMs(track.durationMs),
            style = AuroType.MicroLabel.copy(
                color = AuroColor.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            )
        )

        if (!isSelectionMode) {
            Spacer(modifier = Modifier.width(2.dp))

            // Menu da faixa: três pontos horizontais (•••)
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = stringResource(R.string.cd_more_options),
                    tint = AuroColor.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * EDITORIAL TRACK ROW - Alias de AuroTrackRow
 */
@Composable
fun EditorialTrackRow(
    track: Track,
    trackIndex: Int,
    isPlayingThis: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasError: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    showFormatTag: Boolean = true,
    isAudioPlaying: Boolean = LocalAuroIsPlaying.current
) {
    AuroTrackRow(
        track = track,
        trackIndex = trackIndex,
        isPlayingThis = isPlayingThis,
        onClick = onClick,
        onLongClick = onLongClick,
        onMenuClick = onMenuClick,
        modifier = modifier,
        hasError = hasError,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected,
        showFormatTag = showFormatTag,
        isAudioPlaying = isAudioPlaying
    )
}

/**
 * Gradiente sutil de 16dp de preto para transparente no topo das listas roláveis,
 * garantindo transição suave sob a AuroTopBar sem necessidade de borda inferior.
 */
@Composable
fun AuroTopFade(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(AuroColor.Black, Color.Transparent)
                )
            )
    )
}

/**
 * 5. CABEÇALHO DO AURŌ PLAYER (AURŌ TOP BAR)
 * Substitui AuroBreadcrumbHeader:
 * - Raiz: título Display, subtítulo Label (contagens), à direita botão de vidro ⋮ de 40dp
 * - Telas internas: botão voltar de vidro 40dp, título Subtitle, subtítulo Label. Com profundidade >= 2, breadcrumb acima.
 * - Sem borda inferior.
 */
@Composable
fun AuroTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    breadcrumb: String? = null,
    isRoot: Boolean = false,
    canGoBack: Boolean = false,
    onBackClick: () -> Unit = {},
    rightActions: @Composable RowScope.() -> Unit = {}
) {
    val view = LocalView.current
    Surface(
        color = AuroColor.Black,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuroSpace.ScreenMargin, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                AuroGlass(
                    kind = AuroGlassKind.Button,
                    cornerRadius = 20.dp,
                    modifier = Modifier.size(AuroHeight.GlassButton), // 40.dp
                    onClick = {
                        AuroHaptics.click(view)
                        onBackClick()
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = AuroColor.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                if (!breadcrumb.isNullOrEmpty()) {
                    Text(
                        text = breadcrumb,
                        style = AuroType.Label.copy(color = AuroColor.TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = title,
                    style = if (isRoot) AuroType.Display else AuroType.Subtitle,
                    color = AuroColor.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = AuroType.Label.copy(color = AuroColor.TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = rightActions
            )
        }
    }
}

data class AuroBreadcrumbItem(val title: String)

@Composable
fun AuroBreadcrumbHeader(
    items: List<AuroBreadcrumbItem>,
    onItemClick: (Int) -> Unit = {},
    canGoBack: Boolean = false,
    onBackClick: () -> Unit = {},
    rightActions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    val title = items.lastOrNull()?.title ?: ""
    val breadcrumb = if (items.size > 2) {
        items.dropLast(1).joinToString(" › ") { it.title }
    } else null
    val isRoot = items.size <= 1 && !canGoBack

    AuroTopBar(
        title = title,
        breadcrumb = breadcrumb,
        isRoot = isRoot,
        canGoBack = canGoBack,
        onBackClick = onBackClick,
        rightActions = rightActions,
        modifier = modifier
    )
}

/**
 * 6. INDEXADOR ALFABÉTICO RÁPIDO (A-Z SCROLLER)
 */
@Composable
fun AuroAlphabetFastScroller(
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val alphabet = remember { listOf('#') + ('A'..'Z').toList() }
    var selectedLetter by remember { mutableStateOf<Char?>(null) }

    Box(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .pointerInput(alphabet) {
                    detectTapGestures { offset ->
                        val itemHeight = size.height / alphabet.size.toFloat()
                        val index = (offset.y / itemHeight).toInt().coerceIn(0, alphabet.size - 1)
                        val letter = alphabet[index]
                        if (letter != selectedLetter) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedLetter = letter
                            onLetterSelected(letter)
                        } else {
                            onLetterSelected(letter)
                        }
                    }
                }
                .pointerInput(alphabet) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val itemHeight = size.height / alphabet.size.toFloat()
                            val index = (offset.y / itemHeight).toInt().coerceIn(0, alphabet.size - 1)
                            val letter = alphabet[index]
                            if (letter != selectedLetter) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedLetter = letter
                                onLetterSelected(letter)
                            }
                        },
                        onDragEnd = { selectedLetter = null },
                        onDragCancel = { selectedLetter = null }
                    ) { change, _ ->
                        change.consume()
                        val itemHeight = size.height / alphabet.size.toFloat()
                        val index = (change.position.y / itemHeight).toInt().coerceIn(0, alphabet.size - 1)
                        val letter = alphabet[index]
                        if (letter != selectedLetter) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedLetter = letter
                            onLetterSelected(letter)
                        }
                    }
                },
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            alphabet.forEach { letter ->
                Text(
                    text = letter.toString(),
                    style = AuroType.FastScroller.copy(
                        color = if (letter == selectedLetter) AuroColor.TextPrimary else AuroColor.TextTertiary,
                        fontWeight = if (letter == selectedLetter) FontWeight.Bold else FontWeight.Normal
                    )
                )
            }
        }

        // Balão popup flutuante em vidro preto
        selectedLetter?.let { letter ->
            Box(
                modifier = Modifier
                    .padding(end = 36.dp)
                    .align(Alignment.Center)
            ) {
                AuroGlass(
                    modifier = Modifier.size(48.dp),
                    kind = AuroGlassKind.Card
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = letter.toString(),
                            style = AuroType.Title.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Utilitário de normalização para o AuroAlphabetFastScroller.
 * Trata caracteres acentuados (Á, É, Í, Ó, Ú, etc.) mapeando para a letra base sem acento.
 */
fun normalizeBaseLetter(c: Char): Char {
    val normalized = java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD)
    val base = normalized.firstOrNull { it in 'A'..'Z' || it in 'a'..'z' } ?: c
    return base.uppercaseChar()
}

fun matchesFirstLetter(text: String, targetLetter: Char): Boolean {
    val firstChar = text.trimStart().firstOrNull() ?: return false
    if (targetLetter == '#') {
        return !firstChar.isLetter()
    }
    return normalizeBaseLetter(firstChar) == targetLetter.uppercaseChar()
}

/**
 * 7. CLASSIFICAÇÃO DE 5 ESTRELAS
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuroRatingBar(
    rating: Int,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) {
            val isFilled = i <= rating
            IconButton(
                onClick = {
                    val newRating = if (rating == i) 0 else i
                    onRatingSelected(newRating)
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = if (isFilled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = pluralStringResource(R.plurals.star_rating_desc, i, i),
                    tint = if (isFilled) Color.White else AuroColor.TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (rating > 0) {
            Text(
                text = "$rating/5",
                style = AuroType.Caption.copy(
                    color = AuroColor.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum"
                ),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * AURŌ SWITCH (Fase 4.2)
 * Pílula 48×28dp preta com contorno de 1dp em branco 15% e bolinha branca de 20dp.
 * Ligado: bolinha à direita e contorno em Accent A a 60%.
 */
@Composable
fun AuroSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val view = LocalView.current
    val reduceMotion = com.lucas.player.ui.theme.rememberReduceMotion()

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = if (reduceMotion) tween(0) else com.lucas.player.ui.theme.AuroMotion.liquid(),
        label = "switchThumb"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) AuroColor.ToggleGreen else Color.White.copy(alpha = 0.16f),
        animationSpec = if (reduceMotion) tween(0) else com.lucas.player.ui.theme.AuroMotion.liquid(),
        label = "switchTrack"
    )

    val toggleModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = {
                AuroHaptics.click(view)
                onCheckedChange(it)
            }
        )
    } else Modifier

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .then(toggleModifier),
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
 * AURŌ SLIDER (Fase 4.2)
 * Slider minimalista alinhado à barra de progresso, com suporte completo a acessibilidade (semantics)
 * e resposta tátil com tick a cada passagem de degrau.
 */
@Composable
fun AuroSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    contentDescription: String? = null,
    stateDescription: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val view = LocalView.current
    var lastTickValue by remember { mutableFloatStateOf(value) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                if (stateDescription != null) {
                    this.stateDescription = stateDescription
                }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = valueRange,
                    steps = steps
                )
                setProgress { target ->
                    val clamped = target.coerceIn(valueRange.start, valueRange.endInclusive)
                    onValueChange(clamped)
                    true
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val rangeSpan = valueRange.endInclusive - valueRange.start
                    if (rangeSpan > 0f && size.width > 0) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * rangeSpan
                        AuroHaptics.sliderTick(view)
                        onValueChange(newValue)
                        onValueChangeFinished?.invoke()
                    }
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val rangeSpan = valueRange.endInclusive - valueRange.start
                        if (rangeSpan > 0f && size.width > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val newValue = valueRange.start + fraction * rangeSpan
                            AuroHaptics.sliderTick(view)
                            onValueChange(newValue)
                        }
                    },
                    onDragEnd = {
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        onValueChangeFinished?.invoke()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val rangeSpan = valueRange.endInclusive - valueRange.start
                        if (rangeSpan > 0f && size.width > 0) {
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            val newValue = valueRange.start + fraction * rangeSpan
                            val tickThreshold = if (steps > 0) rangeSpan / (steps + 1) else (rangeSpan / 20f).coerceAtLeast(0.05f)
                            if (kotlin.math.abs(newValue - lastTickValue) >= tickThreshold) {
                                AuroHaptics.sliderTick(view)
                                lastTickValue = newValue
                            }
                            onValueChange(newValue)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
        val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

        // Linha de fundo (trilho inativo)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.15f))
        )

        // Trilho ativo em branco puro
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
        )

        // Bolinha / Indicador (Thumb)
        val thumbRadius = 8.dp
        val thumbOffset = ((totalWidth - thumbRadius * 2) * fraction).coerceAtLeast(0.dp)
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbRadius * 2)
                .clip(CircleShape)
                .background(Color.White)
                .border(0.5.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
        )
    }
}


