package com.lucas.player.ui.screens

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
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
import java.util.Locale

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
 * 1. TELA DA BIBLIOTECA (Biblioteca, Carrossel Mixes, Categorias, Busca e Grupos)
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
        Spacer(modifier = Modifier.height(6.dp))

        // Cabeçalho: Título + Botão de Busca + Ordenação
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Biblioteca",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${displayedTracks.size} faixas indexadas",
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FigmaGlassCircleButton(
                    size = 40.dp,
                    onClick = { isSearchActive = !isSearchActive }
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = "Buscar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                FigmaGlassCircleButton(
                    size = 40.dp,
                    onClick = onSortClick
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = "Ordenar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Campo de Busca Expansível
        AnimatedVisibility(
            visible = isSearchActive,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161238).copy(alpha = 0.75f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text("Filtrar músicas, artistas ou álbuns...", color = Color.White.copy(alpha = 0.40f), fontSize = 13.sp)
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pílulas de Categorias
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                val isSelected = category == currentCategory
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color(0xFF6368F2).copy(alpha = 0.55f)
                            else Color(0xFF140F2D).copy(alpha = 0.55f)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = if (isSelected) {
                                    listOf(Color.White.copy(alpha = 0.45f), Color(0xFFA8AEFF).copy(alpha = 0.35f))
                                } else {
                                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f))
                                }
                            ),
                            shape = CircleShape
                        )
                        .clickable { onSelectCategory(category) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Carrossel Mixes / Álbuns no topo (Estilo Figma)
        if (!isInsideGroupDrilldown && displayedTracks.isNotEmpty()) {
            val previewTracks = remember(displayedTracks) { displayedTracks.take(8) }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                itemsIndexed(previewTracks) { index, track ->
                    AlbumMixCard(
                        track = track,
                        onClick = { onTrackClick(track, index) }
                    )
                }
            }
        }

        // Sub-header de Navegação dentro de Grupos
        if (isInsideGroupDrilldown) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A143A).copy(alpha = 0.70f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onBackFromDrilldown)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Voltar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = drilldownTitle,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Lista de Faixas ou Grupos
        if (!isInsideGroupDrilldown && displayedGroups.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 140.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedGroups) { group ->
                    GroupCardItem(group = group, onClick = { onGroupClick(group) })
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 140.dp),
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

/**
 * Card Mix / Álbum do Carrossel Topo (Figma UI)
 */
@Composable
fun AlbumMixCard(
    track: Track,
    onClick: () -> Unit
) {
    val coverBitmap = rememberTrackCover(track, 256)

    Box(
        modifier = Modifier
            .width(165.dp)
            .height(195.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0E0A26).copy(alpha = 0.68f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Capa no topo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFF1E173E)),
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF70A6B8), Color(0xFFE56B1F), Color(0xFFFFE8BE))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(36.dp))
                    }
                }
            }

            // Rodapé translúcido lavanda
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF5E58B8).copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(modifier = Modifier.padding(end = 36.dp)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Botão circular de Play sobreposto na divisa direita
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 18.dp)
        ) {
            FigmaGlassCircleButton(
                size = 36.dp,
                isPrimaryPlay = true,
                onClick = onClick
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun GroupCardItem(
    group: GroupItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0E0A26).copy(alpha = 0.68f))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF3B2D8A).copy(alpha = 0.55f))
        ) {
            Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color(0xFFA8AEFF), modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = group.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (group.subtitle.isNotEmpty()) {
                Text(text = group.subtitle, color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Text(text = group.countText, color = Color(0xFFA8AEFF).copy(alpha = 0.70f), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackCardItem(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val coverBitmap = rememberTrackCover(track, 128)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isPlaying) Color(0xFF382F72).copy(alpha = 0.75f)
                else Color(0xFF0E0A26).copy(alpha = 0.68f)
            )
            .border(
                width = if (isPlaying) 1.dp else 0.5.dp,
                brush = Brush.linearGradient(
                    if (isPlaying) listOf(Color.White.copy(alpha = 0.50f), Color(0xFFA8AEFF).copy(alpha = 0.35f))
                    else listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Capa
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E173E)),
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
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(22.dp))
            }
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF6368F2).copy(alpha = 0.40f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Detalhes
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isPlaying) Color(0xFFA8AEFF) else Color.White,
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
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatTimeMs(track.durationMs),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp
        )
    }
}

/**
 * 2. FLOATING MINI PLAYER & BOTTOM NAVIGATION BAR (Vidro Real com Blur e Refração)
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
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Mini Player Flutuante
        if (track != null) {
            RealLiquidGlassPanel(
                cornerRadius = 24.dp,
                blurRadius = 30.dp,
                tint = Color(0xFF161238).copy(alpha = 0.70f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable(onClick = onMiniPlayerClick)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF261D4C)),
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

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                color = Color.White.copy(alpha = 0.60f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FigmaGlassCircleButton(
                            size = 38.dp,
                            isPrimaryPlay = true,
                            onClick = onPlayPauseClick
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        FigmaGlassCircleButton(
                            size = 36.dp,
                            onClick = onNextClick
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Linha fina de progresso no rodapé do mini-player
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(Color.White.copy(alpha = 0.10f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF6368F2), Color(0xFFA8AEFF))
                                    )
                                )
                        )
                    }
                }
            }
        }

        // Barra de Navegação Inferior Flutuante (Vidro Claro Figma UI)
        RealLiquidGlassPanel(
            cornerRadius = 28.dp,
            blurRadius = 30.dp,
            tint = Color(0xFF8C92F8).copy(alpha = 0.22f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEqualizerClick) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Equalizador", tint = Color.White)
                }
                IconButton(onClick = onLyricsClick) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = "Letras", tint = Color(0xFFA8AEFF))
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
 * 3. NOW PLAYING SCREEN (Tela Cheia Imersiva Figma UI + Slider 52 Meniscus)
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

    Column(
        modifier = modifier
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
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FigmaGlassCircleButton(
                size = 42.dp,
                onClick = onCollapse
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Recolher", tint = Color.White)
            }

            Text(
                text = "Tocando Agora",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FigmaGlassCircleButton(
                    size = 42.dp,
                    onClick = onOpenSleepTimer
                ) {
                    Icon(Icons.Rounded.Timer, contentDescription = "Sleep Timer", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                FigmaGlassCircleButton(
                    size = 42.dp,
                    onClick = { track?.let { onOpenTrackInfo(it) } }
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Mais", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Capa do Álbum (Sem círculo escuro por trás!)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(28.dp),
                    spotColor = Color(0xFF6368F2),
                    ambientColor = Color(0xFF382C83)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1E173E))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f), Color(0xFFA8AEFF).copy(alpha = 0.35f))
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF70A6B8), Color(0xFFE56B1F), Color(0xFFFFE8BE))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Título e Artista (Alinhados à esquerda estilo Figma + Botão MoreHoriz à direita)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "Nenhuma faixa selecionada",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${track?.artist ?: "Artista"} • ${track?.album ?: "Álbum"}",
                    color = Color(0xFFB4B8DC),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track != null && track.formatTag.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Engine C++ Nativa (Oboe) • ${track.formatTag}",
                        color = Color(0xFFA8AEFF).copy(alpha = 0.80f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            FigmaGlassCircleButton(
                size = 40.dp,
                onClick = { track?.let { onOpenTrackInfo(it) } }
            ) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "Mais opções", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Slider 52 Horizontal Meniscus Fiel à Imagem 3
        val seekProgress = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        Slider52Horizontal(
            progress = seekProgress,
            onProgressChange = onSeek,
            waveformPeaks = waveformPeaks,
            leftText = formatTimeMs(progressMs),
            rightText = formatTimeMs(durationMs),
            leadingIcon = {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Controles de Reprodução Principais
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleMode != ShuffleMode.OFF) Color(0xFFA8AEFF) else Color.White.copy(alpha = 0.45f)
                )
            }

            FigmaGlassCircleButton(
                size = 54.dp,
                onClick = onPrevious
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior", tint = Color.White, modifier = Modifier.size(26.dp))
            }

            FigmaGlassCircleButton(
                size = 72.dp,
                isPrimaryPlay = true,
                onClick = onPlayPause
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            FigmaGlassCircleButton(
                size = 54.dp,
                onClick = onNext
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Próxima", tint = Color.White, modifier = Modifier.size(26.dp))
            }

            IconButton(onClick = onToggleRepeat) {
                Icon(
                    imageVector = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeatMode != RepeatMode.OFF) Color(0xFFA8AEFF) else Color.White.copy(alpha = 0.45f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Rodapé com 3 botões em cápsulas translúcidas (Sem emojis!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RealLiquidGlassPanel(
                cornerRadius = 20.dp,
                blurRadius = 24.dp,
                tint = Color(0xFF1E1948).copy(alpha = 0.55f),
                modifier = Modifier.clickable(onClick = onOpenLyrics)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = null, tint = Color(0xFFA8AEFF), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Letras", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            RealLiquidGlassPanel(
                cornerRadius = 20.dp,
                blurRadius = 24.dp,
                tint = Color(0xFF1E1948).copy(alpha = 0.55f),
                modifier = Modifier.clickable(onClick = onOpenEqualizer)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color(0xFFA8AEFF), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Equalizador", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            RealLiquidGlassPanel(
                cornerRadius = 20.dp,
                blurRadius = 24.dp,
                tint = Color(0xFF1E1948).copy(alpha = 0.55f),
                modifier = Modifier.clickable(onClick = onOpenQueue)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Color(0xFFA8AEFF), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fila", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * 4. GLASS EQUALIZER SHEET (10 Bandas Slider52Vertical + 5 Controles Slider52Horizontal + Toggles)
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
    bandGains: List<Float>, // 10 bandas (-12 a +12 dB)
    onBandGainChange: (Int, Float) -> Unit,
    activePresetName: String,
    presets: List<MusicDatabase.EqPreset>,
    onSelectPreset: (MusicDatabase.EqPreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onClose: () -> Unit
) {
    val frequencies = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    var showPresetMenu by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(12.dp)
    ) {
        RealLiquidGlassPanel(
            cornerRadius = 32.dp,
            blurRadius = 38.dp,
            refractionPx = 26f,
            tint = Color(0xFF161238).copy(alpha = 0.68f),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Equalizador Studio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Biquad IIR 32-bit • Anti-Clipping Ativo", color = Color(0xFFA8AEFF), fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiquidGlassToggle(checked = eqEnabled, onCheckedChange = onToggleEq)
                        FigmaGlassCircleButton(size = 36.dp, onClick = onClose) {
                            Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Seletor de Preset & Botão Salvar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF262052).copy(alpha = 0.50f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .clickable { showPresetMenu = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, tint = Color(0xFFA8AEFF), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Preset: $activePresetName",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF6368F2).copy(alpha = 0.65f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .clickable { showSaveDialog = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("Salvar", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 10 Bandas Verticais Slider 52
                Text("Bandas de Frequência (ISO 10-Band)", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (i in 0 until 10) {
                        val gainDb = bandGains.getOrElse(i) { 0f }
                        val prog = ((gainDb + 12f) / 24f).coerceIn(0f, 1f)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            val prefix = if (gainDb > 0) "+" else ""
                            Text(
                                text = "${prefix}${gainDb.toInt()}",
                                color = if (gainDb != 0f) Color(0xFFA8AEFF) else Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Slider52Vertical(
                                progress = prog,
                                onProgressChange = { newProg ->
                                    val newGain = (newProg * 24f) - 12f
                                    onBandGainChange(i, newGain)
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = frequencies[i],
                                color = Color.White.copy(alpha = 0.60f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Controles de Tom (Tone)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Controles de Tonalidade (Tone)", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    LiquidGlassToggle(checked = toneEnabled, onCheckedChange = onToggleTone)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Preamp
                val preampProg = ((preampDb + 12f) / 24f).coerceIn(0f, 1f)
                val preampPrefix = if (preampDb > 0) "+" else ""
                Slider52Horizontal(
                    progress = preampProg,
                    onProgressChange = { onPreampChange((it * 24f) - 12f) },
                    leftText = "Preamp",
                    rightText = "$preampPrefix${preampDb.toInt()} dB"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Graves
                val bassProg = ((bassDb + 12f) / 24f).coerceIn(0f, 1f)
                val bassPrefix = if (bassDb > 0) "+" else ""
                Slider52Horizontal(
                    progress = bassProg,
                    onProgressChange = { onBassChange((it * 24f) - 12f) },
                    leftText = "Graves (Bass)",
                    rightText = "$bassPrefix${bassDb.toInt()} dB"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Agudos
                val trebleProg = ((trebleDb + 12f) / 24f).coerceIn(0f, 1f)
                val treblePrefix = if (trebleDb > 0) "+" else ""
                Slider52Horizontal(
                    progress = trebleProg,
                    onProgressChange = { onTrebleChange((it * 24f) - 12f) },
                    leftText = "Agudos (Treble)",
                    rightText = "$treblePrefix${trebleDb.toInt()} dB"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Balanço L/R
                val balProg = ((balance + 1f) / 2f).coerceIn(0f, 1f)
                val balText = when {
                    balance < -0.05f -> "E ${(-balance * 100).toInt()}%"
                    balance > 0.05f -> "D ${(balance * 100).toInt()}%"
                    else -> "Centro"
                }
                Slider52Horizontal(
                    progress = balProg,
                    onProgressChange = { onBalanceChange((it * 2f) - 1f) },
                    leftText = "Balanço L/R",
                    rightText = balText
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Crossfade
                val crossfadeProg = (crossfadeMs / 10000f).coerceIn(0f, 1f)
                val crossfadeSec = crossfadeMs / 1000
                Slider52Horizontal(
                    progress = crossfadeProg,
                    onProgressChange = { onCrossfadeChange((it * 10).toInt() * 1000) },
                    leftText = "Crossfade",
                    rightText = if (crossfadeSec == 0) "Desativado" else "${crossfadeSec}s"
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Áudio Mono (Downmixing)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Áudio Mono (Downmixing)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Combina canais esquerdo e direito", color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp)
                    }
                    LiquidGlassToggle(checked = isMono, onCheckedChange = onToggleMono)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Normalização ReplayGain
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Normalização ReplayGain", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Equaliza o volume padrão das gravações", color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF262052).copy(alpha = 0.65f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                            .clickable {
                                val nextMode = (replayGainMode + 1) % 3
                                onReplayGainModeChange(nextMode)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = when (replayGainMode) {
                                1 -> "Faixa"
                                2 -> "Álbum"
                                else -> "Desativado"
                            },
                            color = if (replayGainMode != 0) Color(0xFFA8AEFF) else Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Sub-Modal de Lista de Presets (Vidro Real)
        if (showPresetMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showPresetMenu = false },
                contentAlignment = Alignment.Center
            ) {
                RealLiquidGlassPanel(
                    cornerRadius = 28.dp,
                    blurRadius = 38.dp,
                    tint = Color(0xFF140F2D).copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Predefinições", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            FigmaGlassCircleButton(size = 32.dp, onClick = { showPresetMenu = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(presets) { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onSelectPreset(p)
                                            showPresetMenu = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = Color(0xFFA8AEFF), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(text = p.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sub-Modal de Salvar Preset (Vidro Real)
        if (showSaveDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showSaveDialog = false },
                contentAlignment = Alignment.Center
            ) {
                RealLiquidGlassPanel(
                    cornerRadius = 28.dp,
                    blurRadius = 38.dp,
                    tint = Color(0xFF140F2D).copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Salvar Preset", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = newPresetName,
                            onValueChange = { newPresetName = it },
                            placeholder = { Text("Nome do Preset (ex: Meus Fones)", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF262052).copy(alpha = 0.5f),
                                unfocusedContainerColor = Color(0xFF262052).copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showSaveDialog = false }) {
                                Text("Cancelar", color = Color.White.copy(alpha = 0.6f))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newPresetName.isNotBlank()) {
                                        onSavePreset(newPresetName.trim())
                                        showSaveDialog = false
                                        newPresetName = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6368F2))
                            ) {
                                Text("Salvar", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. GLASS LYRICS SCREEN (Letras Sincronizadas .LRC com Vidro Real e Refração)
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(12.dp)
    ) {
        RealLiquidGlassPanel(
            cornerRadius = 32.dp,
            blurRadius = 38.dp,
            refractionPx = 26f,
            tint = Color(0xFF100C2A).copy(alpha = 0.75f),
            modifier = Modifier.fillMaxSize()
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
                    Column(modifier = Modifier.weight(1f)) {
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
                            color = Color(0xFFA8AEFF),
                            fontSize = 11.sp
                        )
                    }
                    FigmaGlassCircleButton(size = 36.dp, onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (lyrics == null || lyrics.lines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma letra sincronizada (.lrc) ou embutida encontrada para esta faixa.\n\nColoque um arquivo .lrc com o mesmo nome na pasta da música.",
                            color = Color.White.copy(alpha = 0.50f),
                            fontSize = 13.sp,
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
                                color = if (isActive) Color(0xFFA8AEFF) else Color.White.copy(alpha = 0.40f),
                                fontSize = if (isActive) 21.sp else 15.sp,
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
}
