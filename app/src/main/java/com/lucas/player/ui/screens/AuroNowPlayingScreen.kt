package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
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
import androidx.compose.ui.unit.lerp
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
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

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
                    expandProgress.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = 320f))
                }
            } else {
                onCollapse()
            }
        } catch (_: CancellationException) {
            if (!reduceMotion) backProgress.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 300f))
        } finally {
            if (!reduceMotion) backProgress.snapTo(0f)
        }
    }

    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.96f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 280f),
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
            // Recolhida (0f): pílula flutuante (padding 16dp, altura 50dp, raio 25dp) igual à referência
            // Expandida (1f): largura total 100% da tela (0dp lateral/inferior), deixando só o topo da faixa
            val collapsedPillHeight = 50.dp
            val collapsedBottomGap = navBarInset + 10.dp
            val collapsedTop = screenHeight - collapsedPillHeight - collapsedBottomGap
            val expandedTop = statusBarInset + 114.dp
            val currentTop = lerp(collapsedTop, expandedTop, progress)
            val sheetHorizontalPadding = lerp(16.dp, 0.dp, progress)
            val sheetBottomPadding = lerp(collapsedBottomGap, 0.dp, progress)
            val sheetTopRadius = lerp(25.dp, 28.dp, progress)
            val sheetBottomRadius = lerp(25.dp, 0.dp, progress)

            // ─────────── CAMADA 1: PLAYER (Capa 100% largura + Metadados + Transporte + Scrubber) ───────────
            if (playerAlpha > 0f) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = playerAlpha
                            scaleX = 1f - 0.04f * progress
                            scaleY = 1f - 0.04f * progress
                        },
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Capa ocupando 100% da largura da tela sem deformar/cortar as laterais
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .heightIn(max = screenHeight * 0.56f)
                            .graphicsLayer {
                                scaleX = coverScale
                                scaleY = coverScale
                            }
                            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                            .pointerInput(Unit) {
                                var dx = 0f
                                var dy = 0f
                                detectDragGestures(
                                    onDragStart = { dx = 0f; dy = 0f },
                                    onDragEnd = {
                                        if (dy < -45f && abs(dy) > abs(dx)) {
                                            AuroHaptics.click(view)
                                            coroutineScope.launch {
                                                expandProgress.animateTo(1f, spring(0.84f, 320f))
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
                                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                            )
                        }

                        // Fade sutil no topo para o chevron e status bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent)
                                    )
                                )
                        )

                        // Fade inferior fundindo a capa no fundo borrado de 2 cores
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.28f)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                                    )
                                )
                        )

                        // Chevron v limpo no canto superior esquerdo (fiel à referência)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(start = 12.dp, top = 6.dp)
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

                    // Bloco inferior de controles (acima da pílula Letras ^)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 22.dp)
                            .padding(bottom = collapsedPillHeight + collapsedBottomGap + 14.dp),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Título / Artista + ♡ e •••
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                    text = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                                    style = AuroType.Subtitle.copy(fontSize = 14.sp),
                                    color = Color.White.copy(alpha = 0.58f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val isFavorite = track.rating > 0
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
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
                                        .size(40.dp)
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

                        // Transporte: Shuffle · Prev · Play (58dp) · Next · Repeat
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isShuffleActive = shuffleMode != ShuffleMode.OFF
                            IconButton(
                                onClick = { AuroHaptics.click(view); onToggleShuffle() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = stringResource(R.string.cd_shuffle),
                                    tint = if (isShuffleActive) Color.White else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
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
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            TranslucentPlayButton(
                                isPlaying = isPlaying,
                                size = 58.dp,
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
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            val isRepeatActive = repeatMode != RepeatMode.OFF
                            IconButton(
                                onClick = { AuroHaptics.click(view); onToggleRepeat() },
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

                        // Scrubber fino branco
                        EditorialScrubber(
                            progressMs = progressMs,
                            durationMs = durationMs,
                            onSeek = { ms ->
                                onSeek(if (durationMs > 0) ms.toFloat() / durationMs.toFloat() else 0f)
                            },
                            showTimestampsOnlyWhenDragging = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ─────────── CAMADA 2: TOPO COMPACTO DA FAIXA (Arrastar para baixo volta ao player) ───────────
            if (compactAlpha > 0f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 10.dp)
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
                                        expandProgress.animateTo(target, spring(0.84f, 320f))
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
                                text = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                                style = AuroType.Subtitle.copy(fontSize = 13.sp),
                                color = Color.White.copy(alpha = 0.58f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        TranslucentPlayButton(
                            isPlaying = isPlaying,
                            size = 38.dp,
                            onClick = onPlayPause
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    EditorialScrubber(
                        progressMs = progressMs,
                        durationMs = durationMs,
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
                    .background(Color.White.copy(alpha = 0.08f + 0.04f * progress))
                    .background(Color(0xFF0E0E10).copy(alpha = 0.55f + 0.10f * progress))
                    .border(0.5.dp, Color.White.copy(alpha = 0.10f), sheetShape)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cabeçalho da aba (toque ou arrasto contínuo expande/recolhe)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(collapsedPillHeight)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                AuroHaptics.click(view)
                                val target = if (progress < 0.5f) 1f else 0f
                                coroutineScope.launch {
                                    expandProgress.animateTo(target, spring(0.84f, 320f))
                                }
                            }
                            .pointerInput(screenHeight) {
                                val totalDist = (collapsedTop - expandedTop).toPx().coerceAtLeast(1f)
                                detectDragGestures(
                                    onDragEnd = {
                                        val target = if (expandProgress.value > 0.22f) 1f else 0f
                                        coroutineScope.launch {
                                            expandProgress.animateTo(target, spring(0.84f, 320f))
                                        }
                                    }
                                ) { change, drag ->
                                    change.consume()
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
                            Text(
                                text = stringResource(R.string.lyrics_label),
                                style = AuroType.MicroLabel.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White.copy(alpha = 0.80f)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = if (progress >= 0.5f) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (progress > 0.08f) {
                        LyricsContentView(
                            lyrics = lyricsData,
                            progressMs = progressMs,
                            durationMs = durationMs,
                            onSeek = onSeek,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp)
                                .graphicsLayer { alpha = ((progress - 0.1f) * 1.2f).coerceIn(0f, 1f) }
                        )
                    }
                }
            }
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
                    showOptionsMenu = false; showQueueSheet = true
                }
                QuickMenuItem(Icons.Rounded.GraphicEq, stringResource(R.string.menu_equalizer_spatial)) {
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

    // ─────────── Bottom Sheet: Fila ───────────
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
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
