package com.lucas.player.ui.screens

import android.content.Context
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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

enum class NowPlayingMode {
    ARTWORK,
    LYRICS,
    QUEUE
}

/**
 * NOW PLAYING (Apple Music Android 7.0.2 - Viewport Fixo sobre LiquidMeshBackground)
 * - 3 modos alternáveis com transição fluida: ARTWORK, LYRICS (tempo real sincronizado) e QUEUE
 * - Capa 18dp radius com sombra difusa elegante
 * - Scrubber Apple Music (4dp -> 8dp ao toque, tempos em tnum mono)
 * - Controles de transporte limpos sem círculo branco no Play/Pause
 * - Slider de Volume Apple conectado a STREAM_MUSIC
 * - Rodapé fixo com 3 ações: Letras, Equalizador, Fila
 */
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

    var activeMode by remember { mutableStateOf(NowPlayingMode.ARTWORK) }

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
            onCollapse()
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
            // Barra Superior: Top Drag Handle + Menu ••• flutuante
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicador de posição na fila à esquerda
                if (queueSize > 0) {
                    Text(
                        text = "${currentTrackIndex + 1} / $queueSize",
                        style = AuroType.Caption.copy(
                            color = AuroColor.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum"
                        ),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                activeMode = if (activeMode == NowPlayingMode.QUEUE) NowPlayingMode.ARTWORK else NowPlayingMode.QUEUE
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }

                // Drag Handle central de recolher
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f))
                        .clickable { onCollapse() }
                )

                // Botão de Opções •••
                AuroGlass(
                    kind = AuroGlassKind.Button,
                    cornerRadius = 20.dp,
                    modifier = Modifier.size(36.dp),
                    onClick = onOpenTrackInfo
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.cd_more_options),
                            tint = AuroColor.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Cabeçalho compacto quando em modo Letras ou Fila
            if (activeMode != NowPlayingMode.ARTWORK) {
                val isFavorite = track.rating > 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(AuroRadius.card))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AuroColor.SurfaceGrouped),
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

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = AuroType.Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                            style = AuroType.Subtitle.copy(fontSize = 13.sp, color = AuroColor.TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            val nextRating = if (isFavorite) 0 else 5
                            onRatingChanged(nextRating)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = null,
                            tint = if (isFavorite) AuroColor.FavoriteYellow else AuroColor.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Corpo Central (Alternável entre ARTWORK, LYRICS e QUEUE)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = activeMode,
                    transitionSpec = {
                        if (reduceMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (fadeIn(tween(280)) + scaleIn(initialScale = 0.94f, animationSpec = tween(280)))
                                .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.94f, animationSpec = tween(200)))
                        }
                    },
                    label = "nowPlayingModeTransition"
                ) { mode ->
                    when (mode) {
                        NowPlayingMode.ARTWORK -> {
                            ArtworkView(
                                track = track,
                                coverBitmap = coverBitmap,
                                isPlaying = isPlaying,
                                onCollapse = onCollapse,
                                onNext = onNext,
                                onPrevious = onPrevious,
                                onRatingChanged = onRatingChanged
                            )
                        }
                        NowPlayingMode.LYRICS -> {
                            LyricsView(
                                lyrics = lyricsData,
                                progressMs = progressMs,
                                durationMs = durationMs,
                                onSeek = onSeek
                            )
                        }
                        NowPlayingMode.QUEUE -> {
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

            Spacer(modifier = Modifier.height(8.dp))

            // Scrubber Apple Music com selo de formato/sample rate/espacial no centro
            AppleScrubberBar(
                progressMs = progressMs,
                durationMs = durationMs,
                track = track,
                spatialMode = spatialMode,
                onOpenEqualizer = onOpenEqualizer,
                onSeek = onSeek,
                onSeekJumpBackward = onSeekJumpBackward,
                onSeekJumpForward = onSeekJumpForward
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Controles Principais de Transporte (Apple Clean Buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Aleatório
                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        onToggleShuffle()
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.cd_shuffle),
                        tint = if (shuffleMode != ShuffleMode.OFF) accent else AuroColor.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Faixa Anterior com toque curto (onPrevious) e hold (onBeginRewind / onEndRewind)
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
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play / Pause (TranslucentPlayButton no padrão Editorial Dark OLED)
                TranslucentPlayButton(
                    isPlaying = isPlaying,
                    size = 56.dp,
                    onClick = onPlayPause
                )

                // Próxima Faixa com toque curto (onNext) e hold (onBeginFastForward / onEndFastForward)
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
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Repetir
                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        onToggleRepeat()
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = stringResource(R.string.cd_repeat),
                        tint = if (repeatMode != RepeatMode.OFF) accent else AuroColor.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Controle de Volume Apple
            AppleVolumeSlider(
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Rodapé Fixo com 3 Ações de Vidro: Letras, Equalizador, Fila
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Botão Letras
                val isLyricsActive = activeMode == NowPlayingMode.LYRICS
                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        activeMode = if (isLyricsActive) NowPlayingMode.ARTWORK else NowPlayingMode.LYRICS
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .then(
                            if (isLyricsActive) Modifier.background(Color.White.copy(alpha = 0.18f))
                            else Modifier
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = stringResource(R.string.now_playing_mode_lyrics),
                        tint = if (isLyricsActive) Color.White else AuroColor.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 2. Botão Equalizador
                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        onOpenEqualizer()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = stringResource(R.string.tab_equalizer),
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 3. Botão Fila
                val isQueueActive = activeMode == NowPlayingMode.QUEUE
                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        activeMode = if (isQueueActive) NowPlayingMode.ARTWORK else NowPlayingMode.QUEUE
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .then(
                            if (isQueueActive) Modifier.background(Color.White.copy(alpha = 0.18f))
                            else Modifier
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = stringResource(R.string.now_playing_mode_queue),
                        tint = if (isQueueActive) Color.White else AuroColor.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────── Componente de Capa (Artwork) ───────────────────────

@Composable
private fun ArtworkView(
    track: Track,
    coverBitmap: Bitmap?,
    isPlaying: Boolean,
    onCollapse: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRatingChanged: (Int) -> Unit
) {
    val accent = LocalAuroAccent.current.a.value
    val view = LocalView.current

    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.92f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "nowPlayingCoverScale"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Capa com cantos arredondados de 18dp e sombra difusa
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = coverScale
                    scaleY = coverScale
                }
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(AuroRadius.cover),
                    spotColor = accent.copy(alpha = 0.6f)
                )
                .clip(RoundedCornerShape(AuroRadius.cover))
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
                            if (abs(totalDragY) > abs(totalDragX) && totalDragY > 40f) {
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
                    contentDescription = stringResource(R.string.cd_album_cover),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AuroCoverPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    glass = false,
                    shape = RoundedCornerShape(AuroRadius.cover)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Título e Artista com botão de Coração de Favorito
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = AuroType.NowPlayingTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                    style = AuroType.Subtitle.copy(color = AuroColor.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Botão de Favorito (Coração)
            val isFavorite = track.rating > 0
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AuroColor.ControlGlass)
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable {
                        val nextRating = if (isFavorite) 0 else 5
                        onRatingChanged(nextRating)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(if (isFavorite) R.string.cd_unfavorite else R.string.cd_favorite),
                    tint = if (isFavorite) Color.White else AuroColor.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────── Componente de Letras (LyricsView) ───────────────────────

@Composable
private fun LyricsView(
    lyrics: LyricsManager.Lyrics?,
    progressMs: Long,
    durationMs: Long,
    onSeek: (Float) -> Unit
) {
    val listState = rememberLazyListState()

    if (lyrics == null || lyrics.lines.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
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
        // Encontra a linha ativa de acordo com o progresso atual
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isLineActive = index == activeLineIndex && lyrics.isSynced

                Text(
                    text = line.text,
                    style = if (isLineActive) AuroType.LyricsActive else AuroType.LyricsInactive,
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

// ─────────────────────── Componente de Fila Inline (InlineQueueView) ───────────────────────

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
    val accent = LocalAuroAccent.current.a.value
    val view = LocalView.current
    val totalTracks = priorityTracks.size + regularTracks.size

    Column(modifier = Modifier.fillMaxSize()) {
        // 3 Pílulas translúcidas no topo (Aleatório, Repetir, Limpar)
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
                    .background(if (isShuffleActive) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (isShuffleActive) accent.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.12f),
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
                    tint = if (isShuffleActive) accent else AuroColor.TextSecondary,
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
                    .background(if (isRepeatActive) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (isRepeatActive) accent.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.12f),
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
                    tint = if (isRepeatActive) accent else AuroColor.TextSecondary,
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
                                color = accent,
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
    val accent = LocalAuroAccent.current.a.value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuroRadius.card))
            .background(if (isPlaying) accent.copy(alpha = 0.15f) else AuroColor.SurfaceGrouped.copy(alpha = 0.70f))
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
                    color = if (isPlaying) accent else AuroColor.TextPrimary
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

// ─────────────────────── Scrubber Apple Music (Barra de Progresso) ───────────────────────

@Composable
fun AppleScrubberBar(
    progressMs: Long,
    durationMs: Long,
    track: Track? = null,
    spatialMode: Int = 0,
    onOpenEqualizer: () -> Unit = {},
    onSeek: (Float) -> Unit,
    onSeekJumpBackward: () -> Unit = {},
    onSeekJumpForward: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAuroAccent.current.a.value
    val view = LocalView.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var showRemainingTime by remember { mutableStateOf(true) }

    val activeFraction = (if (isDragging) dragFraction else (if (durationMs > 0) progressMs.toFloat() / durationMs.toFloat() else 0f)).coerceIn(0f, 1f)
    val displayedMs = (activeFraction * durationMs).toLong()
    val remainingMs = (durationMs - displayedMs).coerceAtLeast(0)

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "scrubberHeight"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
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
                        if ((newFrac * 25).toInt() != (dragFraction * 25).toInt()) {
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
            // Trilho inativo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(Color.White.copy(alpha = 0.18f))
            )

            // Trilho ativo com cor adaptativa ou branco translúcido
            Box(
                modifier = Modifier
                    .fillMaxWidth(activeFraction)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(Color.White.copy(alpha = 0.85f))
            )

            // Thumb sutil visível ao arrastar
            if (isDragging) {
                val thumbOffset = ((maxWidth - 12.dp) * activeFraction).coerceAtLeast(0.dp)
                Box(
                    modifier = Modifier
                        .offset(x = thumbOffset)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }

        // Tempos em monospace nas extremidades com selo de áudio central
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTimeMs(displayedMs),
                style = AuroType.Caption.copy(
                    fontSize = 12.sp,
                    color = AuroColor.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum"
                )
            )

            // Selo de áudio central (ex.: FLAC • 48 kHz • + Espacial)
            if (track != null) {
                val fmt = if (track.formatTag.isNotBlank()) track.formatTag.uppercase()
                else track.path.substringAfterLast('.', "").uppercase()
                val khz = if (track.sampleRateHz > 0) "${track.sampleRateHz / 1000} kHz" else ""
                val badgeText = buildString {
                    if (fmt.isNotBlank()) append(fmt)
                    if (khz.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(khz)
                    }
                    if (spatialMode != 0) {
                        if (isNotEmpty()) append(" • ")
                        append("+ Espacial")
                    }
                }

                if (badgeText.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable { onOpenEqualizer() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = badgeText,
                            style = AuroType.Caption.copy(
                                fontSize = 10.sp,
                                color = if (spatialMode != 0) accent else AuroColor.TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                fontFeatureSettings = "tnum"
                            )
                        )
                    }
                }
            }

            Text(
                text = if (showRemainingTime) "-${formatTimeMs(remainingMs)}" else formatTimeMs(durationMs),
                style = AuroType.Caption.copy(
                    fontSize = 12.sp,
                    color = AuroColor.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum"
                ),
                modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
            )
        }
    }
}
