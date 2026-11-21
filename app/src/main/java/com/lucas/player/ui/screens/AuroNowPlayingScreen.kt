package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.data.LyricsManager
import com.lucas.player.model.Track
import com.lucas.player.queue.RepeatMode
import com.lucas.player.queue.ShuffleMode
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * NOW PLAYING (Editorial Dark OLED - Minimalista & Atmosférico)
 * - Fiel às Telas 2 (Capa) e 4 (Letras Expandidas) do Design System
 * - Controles 100% Branco Puro / Acromático
 * - Ambient Bleed sutil da capa no fundo
 * - Transição fluida entre Capa e Letras Expandidas
 * - Menu ••• e Fila acessíveis via BottomSheet limpo
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuroNowPlayingScreen(
    track: Track?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    coverBitmap: Bitmap?,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    currentTrackIndex: Int = 0,
    queueSize: Int = 0,
    categoryTitle: String = "Biblioteca",
    upcomingTracks: List<Track> = emptyList(),
    priorityTracks: List<Track> = emptyList(),
    regularTracks: List<Track> = emptyList(),
    currentTrackId: Long = track?.id ?: -1L,
    spatialMode: Int = 0,
    onQueueTrackClick: (Track, Int, Boolean) -> Unit = { _, _, _ -> },
    onRemoveQueueTrack: (Track) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekJumpForward: () -> Unit = {},
    onSeekJumpBackward: () -> Unit = {},
    onBeginFastForward: () -> Unit = {},
    onEndFastForward: () -> Unit = {},
    onBeginRewind: () -> Unit = {},
    onEndRewind: () -> Unit = {},
    onRatingChanged: (Int) -> Unit = {},
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenLyrics: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenTrackInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null) {
        AuroEmptyState(
            icon = Icons.Rounded.MusicOff,
            title = stringResource(R.string.no_file_loaded),
            message = stringResource(R.string.empty_queue_message),
            actionLabel = stringResource(R.string.empty_queue_action),
            onAction = onCollapse,
            modifier = modifier
                .fillMaxSize()
                .background(AuroColor.Black)
        )
        return
    }

    val context = LocalContext.current
    val view = LocalView.current
    val accent = LocalAuroAccent.current.a.value
    val reduceMotion = rememberReduceMotion()
    val coroutineScope = rememberCoroutineScope()

    var isLyricsExpanded by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // Carregamento de Letras Locais (.LRC / ID3)
    var lyricsData by remember(track.id) { mutableStateOf<LyricsManager.Lyrics?>(null) }
    LaunchedEffect(track.id) {
        lyricsData = LyricsManager.loadLyrics(context, track)
    }

    // Predictive Back Gesture
    val backProgress = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = true) { progressFlow: Flow<BackEventCompat> ->
        try {
            progressFlow.collect { event ->
                if (!reduceMotion) {
                    backProgress.snapTo(event.progress)
                }
            }
            if (isLyricsExpanded) {
                isLyricsExpanded = false
            } else {
                onCollapse()
            }
        } catch (_: CancellationException) {
            if (!reduceMotion) {
                backProgress.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 300f))
            }
        } finally {
            if (!reduceMotion) {
                backProgress.snapTo(0f)
            }
        }
    }

    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.94f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 280f),
        label = "nowPlayingCoverScale"
    )

    // Viewport com Fundo Ambient Bleed Suave
    AmbientBleedBackground(
        color = accent,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = backProgress.value
                scaleX = 1f - 0.08f * p
                scaleY = 1f - 0.08f * p
                alpha = 1f - 0.3f * p
                shape = RoundedCornerShape((28f * p).dp)
                clip = p > 0.001f
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Barra Superior Minimalista: Chevron recolher à esquerda + Ícone Fila à direita
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        if (isLyricsExpanded) isLyricsExpanded = false
                        else onCollapse()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_collapse),
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        showQueueSheet = true
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                        contentDescription = stringResource(R.string.now_playing_mode_queue),
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            AnimatedContent(
                targetState = isLyricsExpanded,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(tween(300)) + slideInVertically(animationSpec = tween(300)) { it / 4 })
                            .togetherWith(fadeOut(tween(220)) + slideOutVertically(animationSpec = tween(220)) { -it / 4 })
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "nowPlayingScreenContent"
            ) { expanded ->
                if (!expanded) {
                    // ─────────────────────── TELA 2: MODO CAPA (Cover Mode) ───────────────────────
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1. Capa Vertical Grande (~56% da tela) com fade inferior de 20%
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .graphicsLayer {
                                    scaleX = coverScale
                                    scaleY = coverScale
                                }
                                .clip(RoundedCornerShape(28.dp))
                                .background(AuroColor.SurfaceGrouped)
                                .pointerInput(Unit) {
                                    var totalDragX = 0f
                                    var totalDragY = 0f
                                    detectDragGestures(
                                        onDragStart = {
                                            totalDragX = 0f
                                            totalDragY = 0f
                                        },
                                        onDragEnd = {
                                            if (totalDragY < -50f && abs(totalDragY) > abs(totalDragX)) {
                                                AuroHaptics.click(view)
                                                isLyricsExpanded = true
                                            } else if (totalDragY > 50f && abs(totalDragY) > abs(totalDragX)) {
                                                AuroHaptics.click(view)
                                                onCollapse()
                                            } else if (abs(totalDragX) > 40f) {
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
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (coverBitmap != null) {
                                Image(
                                    bitmap = coverBitmap.asImageBitmap(),
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AuroCoverPlaceholder(
                                    modifier = Modifier.fillMaxSize(),
                                    glass = false,
                                    shape = RoundedCornerShape(28.dp)
                                )
                            }

                            // Gradiente escuro sutil nos 20% inferiores fundindo suavemente no fundo
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.22f)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color(0xFF050505).copy(alpha = 0.40f),
                                                Color(0xFF050505).copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                            )
                        }

                        // 2. Linha Título/Artista + Ações (Coração ♡ e •••)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = AuroType.NowPlayingTitle.copy(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                                    style = AuroType.Subtitle.copy(
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.55f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val isFavorite = track.rating > 0
                                Icon(
                                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = stringResource(if (isFavorite) R.string.cd_unfavorite else R.string.cd_favorite),
                                    tint = if (isFavorite) Color.White else Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            AuroHaptics.click(view)
                                            onRatingChanged(if (isFavorite) 0 else 5)
                                        }
                                )

                                Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = stringResource(R.string.cd_more_options),
                                    tint = Color.White.copy(alpha = 0.60f),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            AuroHaptics.click(view)
                                            showOptionsMenu = true
                                        }
                                )
                            }
                        }

                        // 3. Controles Principais de Transporte (ACIMA do Scrubber, conforme Tela 2)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Shuffle
                            val isShuffleActive = shuffleMode != ShuffleMode.OFF
                            IconButton(
                                onClick = {
                                    AuroHaptics.click(view)
                                    onToggleShuffle()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = stringResource(R.string.cd_shuffle),
                                    tint = if (isShuffleActive) Color.White else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // SkipPrevious com toque curto e hold rewind
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                var isHold = false
                                                val job = coroutineScope.launch {
                                                    delay(350L)
                                                    isHold = true
                                                    AuroHaptics.sliderTick(view)
                                                    onBeginRewind()
                                                }
                                                try {
                                                    val released = tryAwaitRelease()
                                                    job.cancel()
                                                    if (!isHold && released) {
                                                        AuroHaptics.click(view)
                                                        onPrevious()
                                                    }
                                                } finally {
                                                    job.cancel()
                                                    if (isHold) onEndRewind()
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = stringResource(R.string.cd_previous),
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Play / Pause (TranslucentPlayButton 56dp)
                            TranslucentPlayButton(
                                isPlaying = isPlaying,
                                size = 56.dp,
                                onClick = onPlayPause
                            )

                            // SkipNext com toque curto e hold fast-forward
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                var isHold = false
                                                val job = coroutineScope.launch {
                                                    delay(350L)
                                                    isHold = true
                                                    AuroHaptics.sliderTick(view)
                                                    onBeginFastForward()
                                                }
                                                try {
                                                    val released = tryAwaitRelease()
                                                    job.cancel()
                                                    if (!isHold && released) {
                                                        AuroHaptics.click(view)
                                                        onNext()
                                                    }
                                                } finally {
                                                    job.cancel()
                                                    if (isHold) onEndFastForward()
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = stringResource(R.string.cd_next),
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Repeat
                            val isRepeatActive = repeatMode != RepeatMode.OFF
                            IconButton(
                                onClick = {
                                    AuroHaptics.click(view)
                                    onToggleRepeat()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = stringResource(R.string.cd_repeat),
                                    tint = if (isRepeatActive) Color.White else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 4. Scrubber Editorial (Linha fina 2.5dp + thumb 10dp)
                        EditorialScrubber(
                            progressMs = progressMs,
                            durationMs = durationMs,
                            onSeek = { ms ->
                                val frac = if (durationMs > 0) ms.toFloat() / durationMs.toFloat() else 0f
                                onSeek(frac)
                            },
                            showTimestampsOnlyWhenDragging = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 5. Barra de Rodapé: Letras ^
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF1C1C1E).copy(alpha = 0.85f))
                                .background(accent.copy(alpha = 0.10f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                                .clickable {
                                    AuroHaptics.click(view)
                                    isLyricsExpanded = true
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.lyrics_label),
                                    style = AuroType.MicroLabel.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.70f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                } else {
                    // ─────────────────────── TELA 4: LETRAS EXPANDIDAS (Lyrics Mode) ───────────────────────
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1. Linha Compacta Superior: Título/Artista à esquerda, Play 40dp à direita
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = AuroType.CardTitle.copy(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                                    style = AuroType.Subtitle.copy(
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.55f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            TranslucentPlayButton(
                                isPlaying = isPlaying,
                                size = 40.dp,
                                onClick = onPlayPause
                            )
                        }

                        // 2. Scrubber compacto abaixo da linha
                        EditorialScrubber(
                            progressMs = progressMs,
                            durationMs = durationMs,
                            onSeek = { ms ->
                                val frac = if (durationMs > 0) ms.toFloat() / durationMs.toFloat() else 0f
                                onSeek(frac)
                            },
                            showTimestampsOnlyWhenDragging = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. Sheet Elevada de Letras (Fundo #1C1C1E + 12% tint da capa)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                                .background(Color(0xFF1C1C1E))
                                .background(accent.copy(alpha = 0.12f))
                                .border(
                                    0.5.dp,
                                    Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Pílula/Handle "Letras v" no topo
                                Box(
                                    modifier = Modifier
                                        .padding(top = 12.dp, bottom = 8.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .clickable {
                                            AuroHaptics.click(view)
                                            isLyricsExpanded = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.lyrics_label),
                                            style = AuroType.MicroLabel.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.70f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                // Letras centralizadas
                                LyricsContentView(
                                    lyrics = lyricsData,
                                    progressMs = progressMs,
                                    durationMs = durationMs,
                                    onSeek = onSeek,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────── Bottom Sheet: Menu ••• (Opções Rápidas) ───────────────────────
    if (showOptionsMenu) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsMenu = false },
            containerColor = AuroColor.SurfaceGrouped,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Fila de Reprodução
                QuickMenuItem(
                    icon = Icons.AutoMirrored.Outlined.QueueMusic,
                    title = stringResource(R.string.menu_queue),
                    onClick = {
                        showOptionsMenu = false
                        showQueueSheet = true
                    }
                )

                // Equalizador & Áudio Espacial
                QuickMenuItem(
                    icon = Icons.Rounded.GraphicEq,
                    title = stringResource(R.string.menu_equalizer_spatial),
                    onClick = {
                        showOptionsMenu = false
                        onOpenEqualizer()
                    }
                )

                // Adicionar à Playlist
                QuickMenuItem(
                    icon = Icons.Rounded.PlaylistAdd,
                    title = stringResource(R.string.selection_add_playlist),
                    onClick = {
                        showOptionsMenu = false
                        onOpenTrackInfo()
                    }
                )

                // Temporizador
                QuickMenuItem(
                    icon = Icons.Rounded.Timer,
                    title = stringResource(R.string.menu_sleep_timer),
                    onClick = {
                        showOptionsMenu = false
                        onOpenTrackInfo()
                    }
                )

                // Informações da Faixa
                QuickMenuItem(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.context_track_info),
                    onClick = {
                        showOptionsMenu = false
                        onOpenTrackInfo()
                    }
                )
            }
        }
    }

    // ─────────────────────── Bottom Sheet: Fila de Reprodução ───────────────────────
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = AuroColor.SurfaceGrouped,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                InlineQueueView(
                    priorityTracks = priorityTracks,
                    regularTracks = regularTracks,
                    currentTrackId = currentTrackId,
                    shuffleMode = shuffleMode,
                    repeatMode = repeatMode,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeat = onToggleRepeat,
                    onClearQueue = onClearQueue,
                    onTrackClick = onQueueTrackClick,
                    onRemoveTrack = onRemoveQueueTrack
                )
            }
        }
    }
}

@Composable
private fun QuickMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                AuroHaptics.click(view)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = AuroType.Body.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        )
    }
}

// ─────────────────────── Componente de Letras Centralizadas (LyricsContentView) ───────────────────────

@Composable
private fun LyricsContentView(
    lyrics: LyricsManager.Lyrics?,
    progressMs: Long,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    if (lyrics == null || lyrics.lines.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.lyrics_none_found),
                    style = AuroType.SectionTitle.copy(color = AuroColor.TextSecondary),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.lyrics_lrc_hint),
                    style = AuroType.Caption.copy(color = AuroColor.TextTertiary),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        val activeLineIndex = remember(progressMs, lyrics) {
            val idx = lyrics.lines.indexOfLast { it.timeMs <= progressMs }
            if (idx >= 0) idx else 0
        }

        LaunchedEffect(activeLineIndex) {
            if (lyrics.isSynced && activeLineIndex >= 0) {
                listState.animateScrollToItem(activeLineIndex.coerceAtLeast(0))
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isLineActive = index == activeLineIndex && lyrics.isSynced

                Text(
                    text = line.text,
                    style = if (isLineActive) {
                        AuroType.LyricsActive.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        AuroType.LyricsInactive.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.28f),
                            textAlign = TextAlign.Center
                        )
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (durationMs > 0) {
                                val targetFrac = (line.timeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                onSeek(targetFrac)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ─────────────────────── Componente de Fila Inline (InlineQueueView - Acromático) ───────────────────────

@Composable
private fun InlineQueueView(
    priorityTracks: List<Track>,
    regularTracks: List<Track>,
    currentTrackId: Long,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onClearQueue: () -> Unit,
    onTrackClick: (Track, Int, Boolean) -> Unit,
    onRemoveTrack: (Track) -> Unit
) {
    val view = LocalView.current
    val totalTracks = priorityTracks.size + regularTracks.size

    Column(modifier = Modifier.fillMaxSize()) {
        // 3 Pílulas no topo: Aleatório, Repetir, Limpar (100% Branco Acromático)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Aleatório
            val isShuffleActive = shuffleMode != ShuffleMode.OFF
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (isShuffleActive) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (isShuffleActive) Color.White.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.12f),
                        CircleShape
                    )
                    .clickable {
                        AuroHaptics.click(view)
                        onToggleShuffle()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = null,
                    tint = if (isShuffleActive) Color.White else AuroColor.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.cd_shuffle),
                    style = AuroType.Caption.copy(
                        color = if (isShuffleActive) Color.White else AuroColor.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // 2. Repetir
            val isRepeatActive = repeatMode != RepeatMode.OFF
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (isRepeatActive) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (isRepeatActive) Color.White.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.12f),
                        CircleShape
                    )
                    .clickable {
                        AuroHaptics.click(view)
                        onToggleRepeat()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = null,
                    tint = if (isRepeatActive) Color.White else AuroColor.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.cd_repeat),
                    style = AuroType.Caption.copy(
                        color = if (isRepeatActive) Color.White else AuroColor.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // 3. Limpar
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable {
                        AuroHaptics.click(view)
                        onClearQueue()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = AuroColor.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.search_recent_clear),
                    style = AuroType.Caption.copy(
                        color = AuroColor.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        if (totalTracks == 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.end_of_queue),
                    style = AuroType.Subtitle.copy(color = AuroColor.TextSecondary)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (priorityTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.queue_section_priority),
                            style = AuroType.Caption.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    itemsIndexed(priorityTracks, key = { index, t -> "priority_${t.id}_$index" }) { index, track ->
                        InlineTrackRow(
                            track = track,
                            isPlaying = track.id == currentTrackId,
                            onClick = { onTrackClick(track, index, true) },
                            onRemove = { onRemoveTrack(track) }
                        )
                    }
                }

                if (regularTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.queue_section_normal),
                            style = AuroType.Caption.copy(
                                color = AuroColor.TextTertiary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    itemsIndexed(regularTracks, key = { index, t -> "regular_${t.id}_$index" }) { index, track ->
                        InlineTrackRow(
                            track = track,
                            isPlaying = track.id == currentTrackId,
                            onClick = { onTrackClick(track, index, false) },
                            onRemove = { onRemoveTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineTrackRow(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val coverBitmap = rememberTrackCover(track, 100)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuroRadius.card))
            .background(if (isPlaying) Color.White.copy(alpha = 0.12f) else AuroColor.SurfaceGrouped.copy(alpha = 0.70f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AuroCoverPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    glass = false,
                    shape = RoundedCornerShape(6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = AuroType.Body.copy(
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isPlaying) Color.White else AuroColor.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                style = AuroType.Subtitle.copy(fontSize = 12.sp, color = AuroColor.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatTimeMs(track.durationMs),
            style = AuroType.Caption.copy(
                color = AuroColor.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            )
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cd_delete_preset),
                tint = AuroColor.TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
