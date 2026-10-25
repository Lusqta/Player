package com.lucas.player.ui.screens

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.data.CoverArtLoader
import com.lucas.player.data.LyricsManager
import com.lucas.player.data.MusicDatabase
import com.lucas.player.model.LibraryCategory
import com.lucas.player.model.SortMode
import com.lucas.player.model.Track
import com.lucas.player.queue.RepeatMode
import com.lucas.player.queue.ShuffleMode
import com.lucas.player.ui.GroupItem
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.AuraPalette
import com.lucas.player.ui.theme.AuraPaletteExtractor
import java.util.Locale
import kotlin.math.roundToInt

// Helper para carregamento reativo de capa via CoverArtLoader existente
@Composable
fun rememberTrackCover(track: Track?, size: Int = 300): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(track?.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(track?.id) {
        if (track != null) {
            CoverArtLoader.loadCover(context, track, size) { bmp ->
                bitmap = bmp
            }
        } else {
            bitmap = null
        }
    }
    return bitmap
}

fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
}

/**
 * 1. TELA DA BIBLIOTECA (Biblioteca, Categorias, Busca e Grupos)
 */
@Composable
fun LibraryScreen(
    currentCategory: LibraryCategory,
    categories: List<LibraryCategory>,
    onSelectCategory: (LibraryCategory) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    displayedTracks: List<Track>,
    displayedGroups: List<GroupItem>,
    isInsideGroupDrilldown: Boolean,
    drilldownTitle: String,
    onBackFromDrilldown: () -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onGroupClick: (GroupItem) -> Unit,
    currentPlayingId: Long,
    sortMode: SortMode,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearchActive by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Header: "Biblioteca" + Botão de Busca + Ordenação
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Biblioteca",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "${displayedTracks.size} faixas indexadas",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCircleButton(
                    icon = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                    size = 42.dp,
                    onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) onSearchQueryChange("")
                    }
                )
                GlassCircleButton(
                    icon = Icons.Rounded.Tune,
                    size = 42.dp,
                    onClick = onSortClick
                )
            }
        }

        // Barra de Busca Expansível em Vidro
        AnimatedVisibility(
            visible = isSearchActive,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            GlassSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                cornerRadius = 16.dp
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Buscar músicas, álbuns, artistas...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Categorias Pills (Carrossel Horizontal)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            items(categories) { category ->
                PillFilterChip(
                    text = category.title,
                    isSelected = category == currentCategory && !isInsideGroupDrilldown,
                    onClick = { onSelectCategory(category) }
                )
            }
        }

        // Header de Drilldown (Se estiver dentro de um grupo específico)
        if (isInsideGroupDrilldown) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                GlassSurfaceCard(
                    cornerRadius = 14.dp,
                    onClick = onBackFromDrilldown,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Voltar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    text = drilldownTitle,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Conteúdo da Lista: Grupos ou Faixas
        if (!isInsideGroupDrilldown && displayedGroups.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedGroups) { group ->
                    GroupCardItem(
                        group = group,
                        onClick = { onGroupClick(group) }
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(displayedTracks) { index, track ->
                    TrackCardItem(
                        track = track,
                        isPlaying = track.id == currentPlayingId,
                        onClick = { onTrackClick(track, index) },
                        onLongClick = { onTrackLongClick(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun GroupCardItem(
    group: GroupItem,
    onClick: () -> Unit
) {
    GlassSurfaceCard(
        cornerRadius = 18.dp,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF8D72E1).copy(alpha = 0.5f), Color(0xFF1B172B))
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = group.subtitle,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TrackCardItem(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val coverBitmap = rememberTrackCover(track, 128)

    GlassSurfaceCard(
        cornerRadius = 16.dp,
        onClick = onClick,
        onLongClick = onLongClick,
        backgroundColor = if (isPlaying) Color(0xFF261D4E).copy(alpha = 0.70f) else Color(0xFF141026).copy(alpha = 0.50f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Capa quadrada em miniatura
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF241E38)),
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
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF00E5FF).copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Detalhes da Faixa
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isPlaying) Color(0xFF00E5FF) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${track.artist} • ${track.album}",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.formatTag.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = track.formatTag,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Duração da faixa
            Text(
                text = formatTimeMs(track.durationMs),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 2. FLOATING MINI PLAYER & BOTTOM NAVIGATION BAR
 */
@Composable
fun FloatingMiniPlayerAndNav(
    track: Track?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    onMiniPlayerClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverBitmap = rememberTrackCover(track, 128)
    val progressFraction = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Mini Player Flutuante (Card estilo Shivers)
        if (track != null) {
            GlassSurfaceCard(
                cornerRadius = 24.dp,
                onClick = onMiniPlayerClick,
                backgroundColor = Color(0xFF1B1433).copy(alpha = 0.88f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Capa
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2B2248)),
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
                                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.4f))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Título e Artista
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Controles do Mini-Player
                        GlassCircleButton(
                            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            size = 40.dp,
                            isPrimary = true,
                            onClick = onPlayPauseClick
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        GlassCircleButton(
                            icon = Icons.Rounded.SkipNext,
                            size = 38.dp,
                            onClick = onNextClick
                        )
                    }

                    // Linha fina de progresso no rodapé do mini-player
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF8D72E1), Color(0xFF00E5FF))
                                    )
                                )
                        )
                    }
                }
            }
        }

        // Cápsula de Navegação Inferior (Glass Bottom Nav)
        GlassSurfaceCard(
            cornerRadius = 28.dp,
            backgroundColor = Color(0xFF100B22).copy(alpha = 0.90f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEqualizerClick) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Equalizador", tint = Color(0xFF00E5FF))
                }
                IconButton(onClick = onLyricsClick) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Letras", tint = Color(0xFF8D72E1))
                }
                IconButton(onClick = onQueueClick) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Fila", tint = Color.White.copy(alpha = 0.85f))
                }
                IconButton(onClick = onSleepTimerClick) {
                    Icon(Icons.Rounded.Timer, contentDescription = "Sleep Timer", tint = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

/**
 * 3. NOW PLAYING SHEET (Tela Cheia Imersiva)
 */
@Composable
fun NowPlayingScreen(
    track: Track?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    waveformPeaks: FloatArray?,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCollapse: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenTrackInfo: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val coverBitmap = rememberTrackCover(track, 512)
    val auraPalette = remember(coverBitmap) { AuraPaletteExtractor.extract(coverBitmap) }

    AuraBackground(palette = auraPalette, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCircleButton(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    size = 44.dp,
                    onClick = onCollapse
                )

                Text(
                    text = "Tocando Agora",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassCircleButton(
                        icon = Icons.Rounded.Timer,
                        size = 44.dp,
                        onClick = onOpenSleepTimer
                    )
                    GlassCircleButton(
                        icon = Icons.Rounded.MoreVert,
                        size = 44.dp,
                        onClick = { track?.let { onOpenTrackInfo(it) } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Capa do Álbum Grande com Cantos Contínuos e Sombra Difusa Iluminada
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 32.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = auraPalette.primary,
                        ambientColor = auraPalette.accent
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF1E1736))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.08f), auraPalette.accent.copy(alpha = 0.4f))
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = track?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(96.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Título, Artista e Formato
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = track?.title ?: "Nenhuma faixa selecionada",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${track?.artist ?: "Artista"} • ${track?.album ?: "Álbum"}",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track != null && track.formatTag.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Engine C++ Nativa (Oboe) • ${track.formatTag}",
                        color = auraPalette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Refractive Waveform Seek Bar (Slider 52)
            val seekProgress = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
            RefractiveWaveformSeekBar(
                progress = seekProgress,
                onSeek = onSeek,
                waveformPeaks = waveformPeaks,
                currentTimeText = formatTimeMs(progressMs),
                totalTimeText = formatTimeMs(durationMs),
                accentColor = auraPalette.accent
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Controles de Reprodução Principais
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botão Shuffle
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleMode != ShuffleMode.OFF) auraPalette.accent else Color.White.copy(alpha = 0.5f)
                    )
                }

                // Previous
                GlassCircleButton(
                    icon = Icons.Rounded.SkipPrevious,
                    size = 54.dp,
                    onClick = onPrevious
                )

                // Play / Pause (Botão Principal 72dp)
                GlassCircleButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    size = 74.dp,
                    isPrimary = true,
                    onClick = onPlayPause
                )

                // Next
                GlassCircleButton(
                    icon = Icons.Rounded.SkipNext,
                    size = 54.dp,
                    onClick = onNext
                )

                // Repeat
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != RepeatMode.OFF) auraPalette.accent else Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Barra de Ações Rápidas no Rodapé (Letras, Equalizador, Fila)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillFilterChip(
                    text = "🎙 Letras",
                    isSelected = false,
                    onClick = onOpenLyrics
                )
                PillFilterChip(
                    text = "🎛 Equalizador",
                    isSelected = false,
                    onClick = onOpenEqualizer
                )
                PillFilterChip(
                    text = "📋 Fila",
                    isSelected = false,
                    onClick = onOpenQueue
                )
            }
        }
    }
}

/**
 * 4. GLASS EQUALIZER SHEET (Equalizador Studio com 10 bandas ISO biquad e controles de tom)
 */
@Composable
fun GlassEqualizerSheet(
    eqEnabled: Boolean,
    onToggleEq: (Boolean) -> Unit,
    toneEnabled: Boolean,
    onToggleTone: (Boolean) -> Unit,
    preampDb: Float,
    onPreampChange: (Float) -> Unit,
    bassDb: Float,
    onBassChange: (Float) -> Unit,
    trebleDb: Float,
    onTrebleChange: (Float) -> Unit,
    balance: Float,
    onBalanceChange: (Float) -> Unit,
    crossfadeMs: Int,
    onCrossfadeChange: (Int) -> Unit,
    isMono: Boolean,
    onToggleMono: (Boolean) -> Unit,
    replayGainMode: Int,
    onReplayGainModeChange: (Int) -> Unit,
    bandGains: List<Float>, // 10 bandas
    onBandGainChange: (Int, Float) -> Unit,
    activePresetName: String,
    presets: List<MusicDatabase.EqPreset>,
    onSelectPreset: (MusicDatabase.EqPreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onClose: () -> Unit
) {
    val frequencies = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    var showPresetDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    GlassSurfaceCard(
        cornerRadius = 28.dp,
        backgroundColor = Color(0xFF0F0B1E).copy(alpha = 0.94f),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Equalizador Studio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Biquad IIR 32-bit • Anti-Clipping Ativo", color = Color(0xFF00E5FF), fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = eqEnabled,
                        onCheckedChange = onToggleEq,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF6C4AB6))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassCircleButton(icon = Icons.Rounded.Close, size = 36.dp, onClick = onClose)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets Selector & Save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassSurfaceCard(
                    cornerRadius = 14.dp,
                    onClick = { showPresetDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "📋 Preset: $activePresetName",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                GlassSurfaceCard(
                    cornerRadius = 14.dp,
                    onClick = { showSaveDialog = true },
                    backgroundColor = Color(0xFF00E5FF).copy(alpha = 0.25f)
                ) {
                    Text(
                        text = "💾 Salvar",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 10 Bandas ISO Verticais
            Text("Bandas de Frequência (ISO 10-Band)", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in 0 until 10) {
                    val gain = bandGains.getOrElse(i) { 0f }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${if (gain > 0) "+" else ""}${gain.toInt()}",
                            color = if (gain != 0f) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.4f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Slider vertical com touch drag
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFF1E1736))
                                .pointerInput(i) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        val fraction = 1f - (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                        val newGain = (fraction * 24f) - 12f
                                        onBandGainChange(i, newGain)
                                    }
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val norm = ((gain + 12f) / 24f).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(norm)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF00E5FF), Color(0xFF6C4AB6))
                                        )
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = frequencies[i],
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Controles de Tom (Tone) e Preamp
            Text("Controles de Tonalidade (Tone)", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            // Preamp Slider
            SliderControlRow(
                label = "Preamp",
                valueText = "${if (preampDb > 0) "+" else ""}${preampDb.toInt()} dB",
                value = (preampDb + 12f) / 24f,
                onValueChange = { onPreampChange((it * 24f) - 12f) },
                color = Color(0xFFFFD54F)
            )

            // Bass Slider
            SliderControlRow(
                label = "Graves (Bass)",
                valueText = "${if (bassDb > 0) "+" else ""}${bassDb.toInt()} dB",
                value = (bassDb + 12f) / 24f,
                onValueChange = { onBassChange((it * 24f) - 12f) },
                color = Color(0xFF00E676)
            )

            // Treble Slider
            SliderControlRow(
                label = "Agudos (Treble)",
                valueText = "${if (trebleDb > 0) "+" else ""}${trebleDb.toInt()} dB",
                value = (trebleDb + 12f) / 24f,
                onValueChange = { onTrebleChange((it * 24f) - 12f) },
                color = Color(0xFF00E5FF)
            )

            // Balanço Estéreo L/R
            val balText = when {
                balance < -0.05f -> "E ${( -balance * 100).toInt()}%"
                balance > 0.05f -> "D ${( balance * 100).toInt()}%"
                else -> "Centro"
            }
            SliderControlRow(
                label = "Balanço L/R",
                valueText = balText,
                value = (balance + 1f) / 2f,
                onValueChange = { onBalanceChange((it * 2f) - 1f) },
                color = Color(0xFFBA68C8)
            )

            // Crossfade
            val cfSec = crossfadeMs / 1000
            SliderControlRow(
                label = "Crossfade",
                valueText = if (cfSec == 0) "Desativado" else "${cfSec}s",
                value = cfSec / 10f,
                onValueChange = { onCrossfadeChange((it * 10f).roundToInt() * 1000) },
                color = Color(0xFF8D72E1)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Switch Mono & Seletor ReplayGain
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Áudio Mono (Downmixing)", color = Color.White, fontSize = 13.sp)
                    Text("Combina canais esquerdo e direito", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                }
                Switch(
                    checked = isMono,
                    onCheckedChange = onToggleMono,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Normalização ReplayGain", color = Color.White, fontSize = 13.sp)
                    Text("Equaliza o volume padrão das gravações", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                }
                val rgLabel = when (replayGainMode) {
                    1 -> "🔊 Faixa"
                    2 -> "🔊 Álbum"
                    else -> "Desligado"
                }
                GlassSurfaceCard(
                    cornerRadius = 10.dp,
                    onClick = {
                        val nextMode = (replayGainMode + 1) % 3
                        onReplayGainModeChange(nextMode)
                    }
                ) {
                    Text(
                        text = rgLabel,
                        color = if (replayGainMode != 0) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // Diálogo de Seleção de Presets
    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("Predefinições de Equalizador", color = Color.White) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                    items(presets) { preset ->
                        Text(
                            text = if (preset.isBuiltin) "🔹 ${preset.name}" else "⭐ ${preset.name} (Custom)",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectPreset(preset)
                                    showPresetDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresetDialog = false }) {
                    Text("Fechar", color = Color(0xFF00E5FF))
                }
            },
            containerColor = Color(0xFF1E1736)
        )
    }

    // Diálogo de Salvar Preset
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Salvar Preset Personalizado", color = Color.White) },
            text = {
                TextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    placeholder = { Text("Nome do Preset (ex: Meus Fones)", color = Color.White.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPresetName.isNotBlank()) {
                        onSavePreset(newPresetName.trim())
                        showSaveDialog = false
                        newPresetName = ""
                    }
                }) {
                    Text("Salvar", color = Color(0xFF00E5FF))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E1736)
        )
    }
}

@Composable
fun SliderControlRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            modifier = Modifier.width(96.dp)
        )
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueText,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(54.dp)
        )
    }
}

/**
 * 5. GLASS LYRICS VIEW (Letras Sincronizadas .LRC com Click-to-Seek e Glow)
 */
@Composable
fun GlassLyricsScreen(
    track: Track?,
    lyrics: LyricsManager.Lyrics?,
    currentMs: Long,
    onSeekToTime: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeIndex = remember(lyrics, currentMs) {
        if (lyrics != null && lyrics.isSynced) {
            LyricsManager.findActiveLineIndex(lyrics, currentMs)
        } else -1
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val target = (activeIndex - 3).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    GlassSurfaceCard(
        cornerRadius = 28.dp,
        backgroundColor = Color(0xFF0B071A).copy(alpha = 0.96f),
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = track?.title ?: "Letras",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = lyrics?.source ?: "Sem fonte de letras",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp
                    )
                }
                GlassCircleButton(icon = Icons.Rounded.Close, size = 38.dp, onClick = onClose)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (lyrics == null || lyrics.lines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma letra sincronizada (.lrc) ou embutida encontrada para esta faixa.\n\nColoque um arquivo .lrc com o mesmo nome na pasta da música.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val isActive = index == activeIndex
                        Text(
                            text = line.text,
                            color = if (isActive) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.45f),
                            fontSize = if (isActive) 22.sp else 16.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (line.timeMs > 0) onSeekToTime(line.timeMs)
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
