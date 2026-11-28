package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lucas.player.R
import com.lucas.player.data.LyricsManager
import com.lucas.player.model.Track
import com.lucas.player.queue.QueueSection
import com.lucas.player.queue.RepeatMode
import com.lucas.player.queue.ShuffleMode
import com.lucas.player.ui.components.*
import androidx.compose.foundation.gestures.scrollBy
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PlayerSheetTab {
    LYRICS,
    QUEUE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuroNowPlayingScreen(
    track: Track?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    coverBitmap: Bitmap?,
    customAlbumMediaPath: String? = null,
    isAlbumGif: Boolean = false,
    albumMediaVersion: Int = 0,
    customAlbumMedia: com.lucas.player.data.MusicDatabase.CustomMedia? = null,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    priorityTracks: List<Track> = emptyList(),
    regularTracks: List<Track> = emptyList(),
    playbackHistory: List<Pair<Long, Track>> = emptyList(),
    currentTrackId: Long = track?.id ?: -1L,
    onQueueTrackClick: (Track, Int, Boolean) -> Unit = { _, _, _ -> },
    onRemoveQueueTrack: (Track) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onClearManualQueue: () -> Unit = onClearQueue,
    onClearHistory: () -> Unit = {},
    onMoveUpcomingItem: (section: QueueSection, from: Int, to: Int) -> Unit = { _, _, _ -> },
    onRemoveUpcomingItem: (section: QueueSection, index: Int) -> Track? = { _, _ -> null },
    onInsertUpcomingItem: (section: QueueSection, index: Int, track: Track) -> Unit = { _, _, _ -> },
    onPlayTrack: (Track) -> Unit = { _ -> },
    onEnqueueNext: (Track) -> Unit = {},
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onBeginFastForward: () -> Unit = {},
    onEndFastForward: () -> Unit = {},
    onBeginRewind: () -> Unit = {},
    onEndRewind: () -> Unit = {},
    onRatingChanged: (Int) -> Unit = {},
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenTrackInfo: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
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
    val accentState = LocalAuroAccent.current
    val color1 = accentState.mesh1.value
    val color2 = accentState.mesh2.value
    val reduceMotion = rememberReduceMotion()
    val coroutineScope = rememberCoroutineScope()

    val expandProgress = remember { Animatable(0f) }
    var activeSheetTab by remember { mutableStateOf(PlayerSheetTab.LYRICS) }
    val snackbarState = rememberAuroSnackbarState()
    var showOptionsMenu by remember { mutableStateOf(false) }

    var lyricsData by remember(track.id) { mutableStateOf<LyricsManager.Lyrics?>(null) }
    LaunchedEffect(track.id) {
        lyricsData = LyricsManager.loadLyrics(context, track)
    }

    val backProgress = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = true) { progressFlow: Flow<BackEventCompat> ->
        try {
            progressFlow.collect { event ->
                if (!reduceMotion) backProgress.snapTo(event.progress)
            }
            if (expandProgress.value > 0.05f) {
                coroutineScope.launch {
                    expandProgress.animateTo(0f, AuroMotion.spatialDefault())
                }
            } else {
                onCollapse()
            }
        } catch (_: CancellationException) {
            if (!reduceMotion) backProgress.animateTo(0f, AuroMotion.spatialDefault())
        } finally {
            if (!reduceMotion) backProgress.snapTo(0f)
        }
    }

    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.96f,
        animationSpec = AuroMotion.spec(reduceMotion, AuroMotion.spatialDefault()),
        label = "coverScale"
    )

    AnimatedDualColorBackground(
        color1 = color1,
        color2 = color2,
        isPlaying = isPlaying,
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            val progress = expandProgress.value.coerceIn(0f, 1f)
            val playerAlpha = (1f - progress * 2.2f).coerceIn(0f, 1f)
            val compactAlpha = ((progress - 0.30f) * 1.43f).coerceIn(0f, 1f)

            // Geometria interpolada da Aba Expansível de Letras:
            // Recolhida (0f): pílula flutuante (padding lateral 12dp, altura 60dp, raio 28dp) igual à referência
            // Expandida (1f): largura total 100% da tela (0dp lateral/inferior), deixando só o topo da faixa
            val collapsedPillHeight = 60.dp
            val collapsedBottomGap = navBarInset + 10.dp
            val collapsedTop = screenHeight - collapsedPillHeight - collapsedBottomGap
            val expandedTop = statusBarInset + 114.dp
            val currentTop = lerp(collapsedTop, expandedTop, progress)
            val sheetHorizontalPadding = lerp(12.dp, 0.dp, progress)
            val sheetBottomPadding = lerp(collapsedBottomGap, 0.dp, progress)
            val sheetTopRadius = 28.dp
            val sheetBottomRadius = lerp(28.dp, 0.dp, progress)

            // ─────────── CAMADA 1: PLAYER (Capa 100% largura + Metadados + Transporte + Scrubber) ───────────
            if (playerAlpha > 0f) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = collapsedPillHeight + collapsedBottomGap + 12.dp)
                        .graphicsLayer {
                            alpha = playerAlpha
                            scaleX = 1f - 0.04f * progress
                            scaleY = 1f - 0.04f * progress
                        }
                ) {
                    // Top bar do chevron
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(44.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    AuroHaptics.click(view)
                                    onCollapse()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.cd_collapse),
                                tint = Color.White.copy(alpha = 0.90f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Capa quadrada centralizada
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuroSpace.ScreenMargin)
                            .aspectRatio(1f)
                            .align(Alignment.CenterHorizontally)
                            .graphicsLayer {
                                scaleX = coverScale
                                scaleY = coverScale
                            }
                            .clip(RectangleShape)
                            .pointerInput(Unit) {
                                var dx = 0f
                                var dy = 0f
                                detectDragGestures(
                                    onDragStart = { dx = 0f; dy = 0f },
                                    onDragEnd = {
                                        if (dy < -45f && abs(dy) > abs(dx)) {
                                            AuroHaptics.click(view)
                                            coroutineScope.launch {
                                                expandProgress.animateTo(1f, AuroMotion.spatialDefault())
                                            }
                                        } else if (dy > 55f && abs(dy) > abs(dx)) {
                                            AuroHaptics.click(view)
                                            onCollapse()
                                        } else if (abs(dx) > 45f) {
                                            AuroHaptics.click(view)
                                            if (dx < 0) onNext() else onPrevious()
                                        }
                                    }
                                ) { change, drag ->
                                    change.consume()
                                    dx += drag.x
                                    dy += drag.y
                                }
                            }
                    ) {
                        AlbumArtworkImage(
                            albumName = track.album,
                            customMediaPath = customAlbumMediaPath,
                            isGif = isAlbumGif,
                            fallbackTrack = track,
                            shape = RectangleShape,
                            mediaStamp = albumMediaVersion.toLong(),
                            targetSizeDp = 600,
                            preloadedFallbackBitmap = coverBitmap,
                            customMedia = customAlbumMedia,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.heightIn(min = 12.dp).weight(0.7f))

                    // Título / Artista + ♡ e •••
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuroSpace.ScreenMargin),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = AuroType.NowPlayingTitle,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatArtistsDisplay(track.artist, stringResource(R.string.unknown_artist)),
                                style = AuroType.Subtitle.copy(fontSize = 16.sp),
                                color = Color.White.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.offset(x = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val isFavorite = track.rating > 0
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        AuroHaptics.click(view)
                                        onRatingChanged(if (isFavorite) 0 else 5)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = stringResource(if (isFavorite) R.string.cd_unfavorite else R.string.cd_favorite),
                                    tint = if (isFavorite) Color.White else Color.White.copy(alpha = 0.72f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        AuroHaptics.click(view)
                                        showOptionsMenu = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = stringResource(R.string.cd_more_options),
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.heightIn(min = 12.dp).weight(1f))

                    // Controles de Transporte: Shuffle · Prev · Play · Next · Repeat
                    val playCornerRadius by animateDpAsState(
                        targetValue = if (isPlaying) 20.dp else 32.dp,
                        animationSpec = AuroMotion.spec(reduceMotion, AuroMotion.spatialFast()),
                        label = "playMorphRadius"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuroSpace.ScreenMargin),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isShuffleActive = shuffleMode != ShuffleMode.OFF
                        IconButton(
                            onClick = { AuroHaptics.click(view); onToggleShuffle() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = stringResource(R.string.cd_shuffle),
                                    tint = if (isShuffleActive) Color.White else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .graphicsLayer { alpha = if (isShuffleActive) 1f else 0f }
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }

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
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        TranslucentPlayButton(
                            isPlaying = isPlaying,
                            size = 64.dp,
                            cornerRadius = playCornerRadius,
                            containerColor = Color.White,
                            iconTint = Color(0xFF050505),
                            onClick = onPlayPause
                        )

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
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        val isRepeatActive = repeatMode != RepeatMode.OFF
                        IconButton(
                            onClick = { AuroHaptics.click(view); onToggleRepeat() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = stringResource(R.string.cd_repeat),
                                    tint = if (isRepeatActive) Color.White else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .graphicsLayer { alpha = if (isRepeatActive) 1f else 0f }
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.heightIn(min = 12.dp).weight(0.8f))

                    // Scrubber fino branco
                    EditorialScrubber(
                        progressMs = progressMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        onSeek = { ms ->
                            onSeek(if (durationMs > 0) ms.toFloat() / durationMs.toFloat() else 0f)
                        },
                        showTimestampsOnlyWhenDragging = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuroSpace.ScreenMargin)
                    )
                }
            }

            // ─────────── CAMADA 2: TOPO COMPACTO DA FAIXA (Arrastar para baixo volta ao player) ───────────
            if (compactAlpha > 0f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = AuroSpace.ScreenMargin, vertical = 10.dp)
                        .graphicsLayer {
                            alpha = compactAlpha
                            translationY = (-12.dp * (1f - progress)).toPx()
                        }
                        .pointerInput(screenHeight) {
                            val totalDist = (collapsedTop - expandedTop).toPx().coerceAtLeast(1f)
                            detectDragGestures(
                                onDragEnd = {
                                    val target = if (expandProgress.value < 0.78f) 0f else 1f
                                    coroutineScope.launch {
                                        expandProgress.animateTo(target, AuroMotion.spatialDefault())
                                    }
                                }
                            ) { change, drag ->
                                change.consume()
                                val delta = -drag.y / totalDist
                                coroutineScope.launch {
                                    expandProgress.snapTo((expandProgress.value + delta).coerceIn(0f, 1f))
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = AuroType.NowPlayingTitle.copy(fontSize = 18.sp),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatArtistsDisplay(track.artist, stringResource(R.string.unknown_artist)),
                                style = AuroType.Subtitle.copy(fontSize = 13.sp),
                                color = Color.White.copy(alpha = 0.58f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        val playCornerRadiusSmall by animateDpAsState(
                            targetValue = if (isPlaying) 12.dp else 19.dp,
                            animationSpec = AuroMotion.spec(reduceMotion, AuroMotion.spatialFast()),
                            label = "playMorphRadiusSmall"
                        )
                        TranslucentPlayButton(
                            isPlaying = isPlaying,
                            size = 38.dp,
                            cornerRadius = playCornerRadiusSmall,
                            onClick = onPlayPause
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    EditorialScrubber(
                        progressMs = progressMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        onSeek = { ms ->
                            onSeek(if (durationMs > 0) ms.toFloat() / durationMs.toFloat() else 0f)
                        },
                        showTimestampsOnlyWhenDragging = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ─────────── CAMADA 3: ABA EXPANSÍVEL DE LETRAS (Pílula -> Largura Total) ───────────
            val sheetShape = RoundedCornerShape(
                topStart = sheetTopRadius,
                topEnd = sheetTopRadius,
                bottomStart = sheetBottomRadius,
                bottomEnd = sheetBottomRadius
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sheetHorizontalPadding)
                    .offset(y = currentTop)
                    .height((screenHeight - currentTop - sheetBottomPadding).coerceAtLeast(collapsedPillHeight))
                    .clip(sheetShape)
                    .background(Color(0xFF0E0E10).copy(alpha = 0.65f + 0.15f * progress))
                    .background(color1.copy(alpha = 0.22f))
                    .border(
                        width = 0.75.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        ),
                        shape = sheetShape
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .size(width = 34.dp, height = 3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f))
                )
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cabeçalho da aba dividida (60dp, 2 metades: Letras | Fila)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(collapsedPillHeight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Metade Esquerda: Letras
                        val isLyricsActive = activeSheetTab == PlayerSheetTab.LYRICS
                        val lyricsTextColor = if (progress >= 0.5f) {
                            if (isLyricsActive) Color.White else Color.White.copy(alpha = 0.45f)
                        } else {
                            Color.White.copy(alpha = 0.75f)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    AuroHaptics.click(view)
                                    if (progress < 0.5f) {
                                        activeSheetTab = PlayerSheetTab.LYRICS
                                        coroutineScope.launch {
                                            expandProgress.animateTo(1f, AuroMotion.spatialDefault())
                                        }
                                    } else {
                                        if (activeSheetTab == PlayerSheetTab.LYRICS) {
                                            coroutineScope.launch {
                                                expandProgress.animateTo(0f, AuroMotion.spatialDefault())
                                            }
                                        } else {
                                            activeSheetTab = PlayerSheetTab.LYRICS
                                        }
                                    }
                                }
                                .pointerInput(screenHeight) {
                                    val totalDist = (collapsedTop - expandedTop).toPx().coerceAtLeast(1f)
                                    detectDragGestures(
                                        onDragEnd = {
                                            val target = if (expandProgress.value > 0.22f) 1f else 0f
                                            coroutineScope.launch {
                                                expandProgress.animateTo(target, AuroMotion.spatialDefault())
                                            }
                                        }
                                    ) { change, drag ->
                                        change.consume()
                                        if (progress < 0.3f && activeSheetTab != PlayerSheetTab.LYRICS) {
                                            activeSheetTab = PlayerSheetTab.LYRICS
                                        }
                                        val delta = -drag.y / totalDist
                                        coroutineScope.launch {
                                            expandProgress.snapTo((expandProgress.value + delta).coerceIn(0f, 1f))
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MicNone,
                                    contentDescription = null,
                                    tint = lyricsTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.lyrics_tab_label),
                                    style = TextStyle(
                                        fontFamily = AuroFontFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = lyricsTextColor
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Icon(
                                    imageVector = if (progress >= 0.5f && isLyricsActive) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = lyricsTextColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Divisor Vertical Central (0.5dp, 24dp)
                        Box(
                            modifier = Modifier
                                .width(0.5.dp)
                                .height(24.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                        )

                        // Metade Direita: Fila
                        val isQueueActive = activeSheetTab == PlayerSheetTab.QUEUE
                        val queueTextColor = if (progress >= 0.5f) {
                            if (isQueueActive) Color.White else Color.White.copy(alpha = 0.45f)
                        } else {
                            Color.White.copy(alpha = 0.75f)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    AuroHaptics.click(view)
                                    if (progress < 0.5f) {
                                        activeSheetTab = PlayerSheetTab.QUEUE
                                        coroutineScope.launch {
                                            expandProgress.animateTo(1f, AuroMotion.spatialDefault())
                                        }
                                    } else {
                                        if (activeSheetTab == PlayerSheetTab.QUEUE) {
                                            coroutineScope.launch {
                                                expandProgress.animateTo(0f, AuroMotion.spatialDefault())
                                            }
                                        } else {
                                            activeSheetTab = PlayerSheetTab.QUEUE
                                        }
                                    }
                                }
                                .pointerInput(screenHeight) {
                                    val totalDist = (collapsedTop - expandedTop).toPx().coerceAtLeast(1f)
                                    detectDragGestures(
                                        onDragEnd = {
                                            val target = if (expandProgress.value > 0.22f) 1f else 0f
                                            coroutineScope.launch {
                                                expandProgress.animateTo(target, AuroMotion.spatialDefault())
                                            }
                                        }
                                    ) { change, drag ->
                                        change.consume()
                                        if (progress < 0.3f && activeSheetTab != PlayerSheetTab.QUEUE) {
                                            activeSheetTab = PlayerSheetTab.QUEUE
                                        }
                                        val delta = -drag.y / totalDist
                                        coroutineScope.launch {
                                            expandProgress.snapTo((expandProgress.value + delta).coerceIn(0f, 1f))
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = queueTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.queue_tab_label),
                                    style = TextStyle(
                                        fontFamily = AuroFontFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = queueTextColor
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Icon(
                                    imageVector = if (progress >= 0.5f && isQueueActive) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = queueTextColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (progress > 0.08f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .navigationBarsPadding()
                                .graphicsLayer { alpha = ((progress - 0.1f) * 1.2f).coerceIn(0f, 1f) }
                        ) {
                            if (activeSheetTab == PlayerSheetTab.LYRICS) {
                                LyricsContentView(
                                    lyrics = lyricsData,
                                    progressMs = progressMs,
                                    durationMs = durationMs,
                                    onSeek = onSeek,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp)
                                )
                            } else {
                                ExpandedQueueSheetView(
                                    currentTrack = track,
                                    isPlaying = isPlaying,
                                    shouldScrollToAnchor = progress > 0.25f,
                                    priorityTracks = priorityTracks,
                                    regularTracks = regularTracks,
                                    historyList = playbackHistory,
                                    currentTrackId = currentTrackId,
                                    shuffleMode = shuffleMode,
                                    repeatMode = repeatMode,
                                    onToggleShuffle = onToggleShuffle,
                                    onToggleRepeat = onToggleRepeat,
                                    onClearManualQueue = onClearManualQueue,
                                    onClearHistory = onClearHistory,
                                    onMoveUpcomingItem = onMoveUpcomingItem,
                                    onRemoveUpcomingItem = onRemoveUpcomingItem,
                                    onInsertUpcomingItem = onInsertUpcomingItem,
                                    onTrackClick = onQueueTrackClick,
                                    onPlayTrack = onPlayTrack,
                                    onEnqueueNext = onEnqueueNext,
                                    showSnackbar = { msg, act, onAct ->
                                        snackbarState.show(msg, act, onAct)
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Snackbar do Player
            AuroSnackbarHost(
                state = snackbarState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 72.dp, start = 16.dp, end = 16.dp)
            )
        }
    }

    // ─────────── Bottom Sheet: Menu ••• ───────────
    if (showOptionsMenu) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsMenu = false },
            containerColor = AuroColor.SurfaceGrouped,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                QuickMenuItem(Icons.AutoMirrored.Outlined.QueueMusic, stringResource(R.string.menu_queue)) {
                    showOptionsMenu = false
                    activeSheetTab = PlayerSheetTab.QUEUE
                    coroutineScope.launch {
                        expandProgress.animateTo(1f, AuroMotion.spatialDefault())
                    }
                }
                QuickMenuItem(Icons.Rounded.GraphicEq, stringResource(R.string.tab_equalizer)) {
                    showOptionsMenu = false; onOpenEqualizer()
                }
                QuickMenuItem(Icons.AutoMirrored.Outlined.PlaylistAdd, stringResource(R.string.selection_add_playlist)) {
                    showOptionsMenu = false; onAddToPlaylist()
                }
                QuickMenuItem(Icons.Rounded.Timer, stringResource(R.string.menu_sleep_timer)) {
                    showOptionsMenu = false; onOpenSleepTimer()
                }
                QuickMenuItem(Icons.Rounded.Info, stringResource(R.string.context_track_info)) {
                    showOptionsMenu = false; onOpenTrackInfo()
                }
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
                            fontSize = 18.sp,
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

// ─────────────────────── Componente de Fila Expandida (ExpandedQueueSheetView) ───────────────────────

@Composable
private fun ExpandedQueueSheetView(
    currentTrack: Track,
    isPlaying: Boolean,
    shouldScrollToAnchor: Boolean,
    priorityTracks: List<Track>,
    regularTracks: List<Track>,
    historyList: List<Pair<Long, Track>>,
    currentTrackId: Long,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onClearManualQueue: () -> Unit,
    onClearHistory: () -> Unit,
    onMoveUpcomingItem: (section: QueueSection, from: Int, to: Int) -> Unit,
    onRemoveUpcomingItem: (section: QueueSection, index: Int) -> Track?,
    onInsertUpcomingItem: (section: QueueSection, index: Int, track: Track) -> Unit,
    onTrackClick: (Track, Int, Boolean) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onEnqueueNext: (Track) -> Unit,
    showSnackbar: (message: String, actionLabel: String?, onAction: (() -> Unit)?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    var localPriorityTracks by remember(priorityTracks) { mutableStateOf(priorityTracks) }
    var localRegularTracks by remember(regularTracks) { mutableStateOf(regularTracks) }

    var draggingSection by remember { mutableStateOf<QueueSection?>(null) }
    var draggingInitialIndex by remember { mutableStateOf<Int?>(null) }
    var draggingCurrentIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    val queueListState = rememberLazyListState()

    val chronologicalHistory = remember(historyList) { historyList.reversed() }
    val anchorIndex = if (chronologicalHistory.isNotEmpty()) 1 + chronologicalHistory.size else 0

    LaunchedEffect(shouldScrollToAnchor) {
        if (shouldScrollToAnchor) {
            queueListState.scrollToItem(anchorIndex)
        }
    }

    // Rolagem automática suave de 60fps perto das bordas superior e inferior
    LaunchedEffect(draggingSection) {
        if (draggingSection == null) return@LaunchedEffect
        while (isActive && draggingSection != null) {
            val viewportH = queueListState.layoutInfo.viewportSize.height
            if (viewportH > 0) {
                val edgeThreshold = with(density) { 70.dp.toPx() }
                if (dragPointerY > 0 && dragPointerY < edgeThreshold) {
                    val factor = ((edgeThreshold - dragPointerY) / edgeThreshold).coerceIn(0.2f, 1f)
                    queueListState.scrollBy(-18f * factor)
                } else if (dragPointerY > viewportH - edgeThreshold) {
                    val factor = ((dragPointerY - (viewportH - edgeThreshold)) / edgeThreshold).coerceIn(0.2f, 1f)
                    queueListState.scrollBy(18f * factor)
                }
            }
            delay(16)
        }
    }

    val priorityKeys = remember(localPriorityTracks) {
        val counts = mutableMapOf<Long, Int>()
        localPriorityTracks.map { track ->
            val occ = counts.getOrDefault(track.id, 0)
            counts[track.id] = occ + 1
            "p_${track.id}_$occ"
        }
    }

    val regularKeys = remember(localRegularTracks) {
        val counts = mutableMapOf<Long, Int>()
        localRegularTracks.map { track ->
            val occ = counts.getOrDefault(track.id, 0)
            counts[track.id] = occ + 1
            "r_${track.id}_$occ"
        }
    }

    val itemHeightPx = with(density) { 62.dp.toPx() }

    Column(modifier = modifier) {
        // 1. Linha Superior: 3 Pílulas Rápidas (Aleatório, Repetir, Limpar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Aleatório
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

            // Repetir
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

            // Limpar Fila Prioritária/Manual
            val isClearEnabled = localPriorityTracks.isNotEmpty()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isClearEnabled) 0.08f else 0.04f))
                    .border(1.dp, Color.White.copy(alpha = if (isClearEnabled) 0.12f else 0.06f), CircleShape)
                    .clickable(enabled = isClearEnabled) {
                        AuroHaptics.click(view)
                        onClearManualQueue()
                        showSnackbar(context.getString(R.string.queue_manual_cleared), null, null)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = AuroColor.TextSecondary.copy(alpha = if (isClearEnabled) 1f else 0.40f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.search_recent_clear),
                    style = AuroType.Caption.copy(
                        color = AuroColor.TextSecondary.copy(alpha = if (isClearEnabled) 1f else 0.40f),
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Lista Contínua Unificada: Histórico (acima) -> Faixa Atual (âncora) -> A Seguir -> Continuando
        LazyColumn(
            state = queueListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Histórico (acima da âncora em ordem cronológica)
            if (chronologicalHistory.isNotEmpty()) {
                item(key = "header_history") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.queue_mode_history),
                            style = AuroType.Caption.copy(
                                color = AuroColor.TextTertiary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text(
                            text = stringResource(R.string.history_clear),
                            style = AuroType.Caption.copy(
                                color = AuroColor.TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    AuroHaptics.click(view)
                                    onClearHistory()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                itemsIndexed(chronologicalHistory, key = { idx, pair -> "h_${pair.first}_$idx" }) { _, (_, hTrack) ->
                    HistoryTrackRow(
                        track = hTrack,
                        isPlaying = hTrack.id == currentTrackId,
                        onClick = {
                            AuroHaptics.click(view)
                            onPlayTrack(hTrack)
                        },
                        onAddToNext = {
                            AuroHaptics.click(view)
                            onEnqueueNext(hTrack)
                            showSnackbar(
                                context.getString(R.string.history_added_next),
                                null,
                                null
                            )
                        }
                    )
                }
            }

            // Âncora da Faixa Atual (meio)
            item(key = "current_track_anchor") {
                val anchorCover = rememberTrackCover(currentTrack, 120)
                val anchorShape = RoundedCornerShape(16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(anchorShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.18f), anchorShape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RectangleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (anchorCover != null) {
                            Image(
                                bitmap = anchorCover.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AuroCoverPlaceholder(
                                modifier = Modifier.fillMaxSize(),
                                glass = false,
                                shape = RectangleShape
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack.title,
                            style = AuroType.Body.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatArtistsDisplay(currentTrack.artist, stringResource(R.string.unknown_artist)),
                            style = AuroType.Subtitle.copy(
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.72f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    AuroEqualizerBars(
                        isPlaying = isPlaying,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = formatTimeMs(currentTrack.durationMs),
                        style = AuroType.Caption.copy(
                            color = Color.White.copy(alpha = 0.75f),
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum"
                        )
                    )
                }
            }

            // Seção A Seguir (Fila Prioritária)
            if (localPriorityTracks.isNotEmpty()) {
                item(key = "header_priority") {
                    Text(
                        text = stringResource(R.string.queue_mode_up_next),
                        style = AuroType.Caption.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }

                itemsIndexed(localPriorityTracks, key = { index, _ -> priorityKeys.getOrElse(index) { "p_fallback_$index" } }) { index, track ->
                    val itemKey = priorityKeys.getOrElse(index) { "p_fallback_$index" }
                    val isThisDragging = draggingSection == QueueSection.PRIORITY && draggingCurrentIndex == index
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .zIndex(if (isThisDragging) 2f else 0f)
                            .graphicsLayer {
                                if (isThisDragging) {
                                    translationY = dragOffsetY
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                }
                            }
                    ) {
                        UpcomingTrackRow(
                            track = track,
                            index = index,
                            section = QueueSection.PRIORITY,
                            isPlaying = track.id == currentTrackId,
                            isDragging = isThisDragging,
                            onClick = { onTrackClick(track, index, true) },
                            onSwipeDelete = {
                                val removed = onRemoveUpcomingItem(QueueSection.PRIORITY, index)
                                if (removed != null) {
                                    showSnackbar(
                                        context.getString(R.string.queue_item_removed),
                                        context.getString(R.string.undo)
                                    ) {
                                        onInsertUpcomingItem(QueueSection.PRIORITY, index, removed)
                                    }
                                }
                            },
                            dragHandleModifier = Modifier.pointerInput(localPriorityTracks.size) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        AuroHaptics.sliderTick(view)
                                        draggingSection = QueueSection.PRIORITY
                                        draggingInitialIndex = index
                                        draggingCurrentIndex = index
                                        dragOffsetY = 0f
                                        dragPointerY = queueListState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == itemKey }
                                            ?.offset?.toFloat() ?: 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount
                                        dragPointerY += dragAmount

                                        val slotsShifted = (dragOffsetY / itemHeightPx).roundToInt()
                                        val currentIdx = draggingCurrentIndex ?: index
                                        val targetIdx = (currentIdx + slotsShifted).coerceIn(0, localPriorityTracks.size - 1)

                                        if (targetIdx != currentIdx) {
                                            AuroHaptics.sliderTick(view)
                                            val list = localPriorityTracks.toMutableList()
                                            val item = list.removeAt(currentIdx)
                                            list.add(targetIdx, item)
                                            localPriorityTracks = list
                                            dragOffsetY -= (targetIdx - currentIdx) * itemHeightPx
                                            draggingCurrentIndex = targetIdx
                                        }
                                    },
                                    onDragEnd = {
                                        val initial = draggingInitialIndex
                                        val target = draggingCurrentIndex
                                        if (initial != null && target != null && initial != target) {
                                            onMoveUpcomingItem(QueueSection.PRIORITY, initial, target)
                                        }
                                        draggingSection = null
                                        draggingInitialIndex = null
                                        draggingCurrentIndex = null
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggingSection = null
                                        draggingInitialIndex = null
                                        draggingCurrentIndex = null
                                        dragOffsetY = 0f
                                        localPriorityTracks = priorityTracks
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Seção Continuando (Fila Regular)
            if (localRegularTracks.isNotEmpty()) {
                item(key = "header_regular") {
                    Text(
                        text = stringResource(R.string.queue_section_continuing),
                        style = AuroType.Caption.copy(
                            color = AuroColor.TextTertiary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }

                itemsIndexed(localRegularTracks, key = { index, _ -> regularKeys.getOrElse(index) { "r_fallback_$index" } }) { index, track ->
                    val itemKey = regularKeys.getOrElse(index) { "r_fallback_$index" }
                    val isThisDragging = draggingSection == QueueSection.QUEUE && draggingCurrentIndex == index
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .zIndex(if (isThisDragging) 2f else 0f)
                            .graphicsLayer {
                                if (isThisDragging) {
                                    translationY = dragOffsetY
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                }
                            }
                    ) {
                        UpcomingTrackRow(
                            track = track,
                            index = index,
                            section = QueueSection.QUEUE,
                            isPlaying = track.id == currentTrackId,
                            isDragging = isThisDragging,
                            onClick = { onTrackClick(track, index, false) },
                            onSwipeDelete = {
                                val removed = onRemoveUpcomingItem(QueueSection.QUEUE, index)
                                if (removed != null) {
                                    showSnackbar(
                                        context.getString(R.string.queue_item_removed),
                                        context.getString(R.string.undo)
                                    ) {
                                        onInsertUpcomingItem(QueueSection.QUEUE, index, removed)
                                    }
                                }
                            },
                            dragHandleModifier = Modifier.pointerInput(localRegularTracks.size) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        AuroHaptics.sliderTick(view)
                                        draggingSection = QueueSection.QUEUE
                                        draggingInitialIndex = index
                                        draggingCurrentIndex = index
                                        dragOffsetY = 0f
                                        dragPointerY = queueListState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == itemKey }
                                            ?.offset?.toFloat() ?: 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount
                                        dragPointerY += dragAmount

                                        val slotsShifted = (dragOffsetY / itemHeightPx).roundToInt()
                                        val currentIdx = draggingCurrentIndex ?: index
                                        val targetIdx = (currentIdx + slotsShifted).coerceIn(0, localRegularTracks.size - 1)

                                        if (targetIdx != currentIdx) {
                                            AuroHaptics.sliderTick(view)
                                            val list = localRegularTracks.toMutableList()
                                            val item = list.removeAt(currentIdx)
                                            list.add(targetIdx, item)
                                            localRegularTracks = list
                                            dragOffsetY -= (targetIdx - currentIdx) * itemHeightPx
                                            draggingCurrentIndex = targetIdx
                                        }
                                    },
                                    onDragEnd = {
                                        val initial = draggingInitialIndex
                                        val target = draggingCurrentIndex
                                        if (initial != null && target != null && initial != target) {
                                            onMoveUpcomingItem(QueueSection.QUEUE, initial, target)
                                        }
                                        draggingSection = null
                                        draggingInitialIndex = null
                                        draggingCurrentIndex = null
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggingSection = null
                                        draggingInitialIndex = null
                                        draggingCurrentIndex = null
                                        dragOffsetY = 0f
                                        localRegularTracks = regularTracks
                                    }
                                )
                            }
                        )
                    }
                }
            }

            if (localPriorityTracks.isEmpty() && localRegularTracks.isEmpty()) {
                item(key = "empty_upcoming") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.end_of_queue),
                            style = AuroType.Subtitle.copy(color = AuroColor.TextSecondary),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────── Componente de Linha de Faixa Futura com Swipe e Drag ───────────────────────

@Composable
private fun UpcomingTrackRow(
    track: Track,
    index: Int,
    section: QueueSection,
    isPlaying: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onSwipeDelete: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val coverBitmap = rememberTrackCover(track, 100)

    val swipeOffsetX = remember(track.id, index) { Animatable(0f) }
    val swipeThreshold = with(density) { 90.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuroRadius.card))
    ) {
        // Fundo vermelho revelado no swipe para esquerda
        if (swipeOffsetX.value < -2f) {
            val progress = ((-swipeOffsetX.value) / swipeThreshold).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFE53935).copy(alpha = progress * 0.9f))
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_remove_item),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Conteúdo da Linha com Deslocamento Horizontal
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(AuroRadius.card))
                .background(if (isDragging) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                .clickable { onClick() }
                .pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (swipeOffsetX.value < -swipeThreshold) {
                                    AuroHaptics.confirm(view)
                                    swipeOffsetX.animateTo(-with(density) { 400.dp.toPx() }, AuroMotion.spatialDefault())
                                    onSwipeDelete()
                                    swipeOffsetX.snapTo(0f)
                                } else {
                                    swipeOffsetX.animateTo(0f, AuroMotion.spatialDefault())
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch { swipeOffsetX.animateTo(0f, AuroMotion.spatialDefault()) }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            val newOffset = (swipeOffsetX.value + dragAmount).coerceAtMost(0f)
                            swipeOffsetX.snapTo(newOffset)
                        }
                    }
                }
                .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RectangleShape),
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
                        shape = RectangleShape
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
                    text = formatArtistsDisplay(track.artist, stringResource(R.string.unknown_artist)),
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

            // Alça de 3 Barras para Arrasto Imediato (Touch Target de 44x44dp)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .then(dragHandleModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Reorder,
                    contentDescription = stringResource(R.string.queue_reorder_handle),
                    tint = AuroColor.TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────── Componente de Linha de Histórico ───────────────────────

@Composable
private fun HistoryTrackRow(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToNext: () -> Unit
) {
    val coverBitmap = rememberTrackCover(track, 100)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = 0.55f }
            .clip(RoundedCornerShape(AuroRadius.card))
            .background(Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RectangleShape),
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
                    shape = RectangleShape
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
                text = formatArtistsDisplay(track.artist, stringResource(R.string.unknown_artist)),
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

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onAddToNext,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                contentDescription = stringResource(R.string.selection_play_next),
                tint = AuroColor.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
