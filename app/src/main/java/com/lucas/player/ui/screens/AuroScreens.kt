package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.data.MusicDatabase
import com.lucas.player.model.LibraryCategory
import com.lucas.player.model.SortMode
import com.lucas.player.model.Track
import com.lucas.player.queue.RepeatMode
import com.lucas.player.queue.ShuffleMode
import com.lucas.player.ui.GroupItem
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * AURŌ DESIGN SYSTEM v2 - TELAS PRINCIPAIS
 * O mundo é vazio (#000000), uma luz global (y=0.88), e os objetos interativos são de vidro preto.
 */

// ─────────────────────────────────────────────────────────────────────────────
// 1. TELA RAIZ DA BIBLIOTECA (AURŌ LIBRARY ROOT)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroLibraryRootScreen(
    totalTracksCount: Int,
    onSelectCategory: (LibraryCategory) -> Unit,
    onSortClick: () -> Unit,
    onRescanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = remember {
        listOf(
            LibraryCategory.ALL_SONGS to ("Todas as Músicas" to Icons.AutoMirrored.Rounded.PlaylistPlay),
            LibraryCategory.FOLDERS to ("Pastas" to Icons.Rounded.Folder),
            LibraryCategory.ALBUMS to ("Álbuns" to Icons.Rounded.Album),
            LibraryCategory.ARTISTS to ("Artistas" to Icons.Rounded.Person),
            LibraryCategory.ALBUM_ARTISTS to ("Artistas do Álbum" to Icons.Rounded.Group),
            LibraryCategory.GENRES to ("Gêneros" to Icons.Rounded.Category),
            LibraryCategory.PLAYLISTS to ("Listas de Reprodução" to Icons.Rounded.PlaylistAddCheck),
            LibraryCategory.QUEUE to ("Fila de Reprodução" to Icons.AutoMirrored.Rounded.QueueMusic),
            LibraryCategory.YEARS to ("Anos" to Icons.Rounded.DateRange),
            LibraryCategory.COMPOSERS to ("Compositores" to Icons.Rounded.Piano),
            LibraryCategory.MOST_PLAYED to ("Mais Tocadas" to Icons.Rounded.TrendingUp),
            LibraryCategory.RECENTLY_ADDED to ("Adicionadas Recentemente" to Icons.Rounded.History),
            LibraryCategory.TOP_RATED to ("Melhor Classificadas" to Icons.Rounded.Star)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroBreadcrumbHeader(
            items = listOf(AuroBreadcrumbItem("Biblioteca")),
            onItemClick = {},
            canGoBack = false,
            rightActions = {
                IconButton(onClick = onSortClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Ordenar",
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onRescanClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Atualizar",
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        // Resumo
        Text(
            text = "$totalTracksCount faixas indexadas",
            color = AuroColor.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Linhas de categoria (sem vidro, texto e fio Hairline direto no preto)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(categories, key = { it.first.name }) { (cat, info) ->
                val (title, icon) = info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCategory(cat) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = AuroColor.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = AuroColor.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. TELA DE LISTA DE CATEGORIA (AURŌ CATEGORY LIST)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuroCategoryListScreen(
    category: LibraryCategory,
    displayedTracks: List<Track>,
    displayedGroups: List<GroupItem>,
    currentPlayingId: Long,
    onBackClick: () -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onTrackMenuClick: (Track) -> Unit,
    onGroupClick: (GroupItem) -> Unit,
    onGroupLongClick: (GroupItem) -> Unit = {},
    onPlayAll: () -> Unit = {},
    onShuffleAll: () -> Unit = {},
    onCreatePlaylist: () -> Unit = {},
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAllSongs = category == LibraryCategory.ALL_SONGS
    val isPlaylists = category == LibraryCategory.PLAYLISTS

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroBreadcrumbHeader(
            items = listOf(
                AuroBreadcrumbItem("Biblioteca"),
                AuroBreadcrumbItem(category.title)
            ),
            onItemClick = { index ->
                if (index == 0) onBackClick()
            },
            canGoBack = true,
            onBackClick = onBackClick,
            rightActions = {
                IconButton(onClick = onSortClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Ordenar",
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        // Banner de Ações Rápidas em "Todas as Músicas"
        if (isAllSongs && displayedTracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuroGlass(
                    kind = AuroGlassKind.Card,
                    onClick = onPlayAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = AuroColor.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tocar Todas",
                            color = AuroColor.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                AuroGlass(
                    kind = AuroGlassKind.Card,
                    onClick = onShuffleAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = AuroColor.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Aleatório",
                            color = AuroColor.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
        }

        // Ação de Nova Lista de Reprodução
        if (isPlaylists) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCreatePlaylist() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = LocalAuroAccent.current.a.value,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "+ Nova Lista de Reprodução",
                    color = LocalAuroAccent.current.a.value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
        }

        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            if (isAllSongs) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp, end = if (displayedTracks.size > 15) 28.dp else 0.dp)
                ) {
                    itemsIndexed(displayedTracks, key = { _, t -> t.id }) { index, track ->
                        AuroTrackRow(
                            track = track,
                            trackIndex = index,
                            isPlayingThis = track.id == currentPlayingId,
                            onClick = { onTrackClick(track, index) },
                            onLongClick = { onTrackLongClick(track) },
                            onMenuClick = { onTrackMenuClick(track) }
                        )
                        HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                    }
                }

                if (displayedTracks.size > 15) {
                    AuroAlphabetFastScroller(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(bottom = 120.dp),
                        onLetterSelected = { letter ->
                            val targetIndex = if (letter == '#') {
                                displayedTracks.indexOfFirst { it.title.firstOrNull()?.isLetter() == false }
                            } else {
                                displayedTracks.indexOfFirst { it.title.startsWith(letter, ignoreCase = true) }
                            }
                            if (targetIndex >= 0) {
                                coroutineScope.launch { listState.scrollToItem(targetIndex) }
                            }
                        }
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp, end = if (displayedGroups.size > 15) 28.dp else 0.dp)
                ) {
                    items(displayedGroups, key = { it.title }) { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onGroupClick(group) },
                                    onLongClick = { onGroupLongClick(group) }
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.title,
                                    color = AuroColor.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = group.subtitle,
                                    color = AuroColor.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = AuroColor.TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                    }
                }

                if (displayedGroups.size > 15) {
                    AuroAlphabetFastScroller(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(bottom = 120.dp),
                        onLetterSelected = { letter ->
                            val targetIndex = if (letter == '#') {
                                displayedGroups.indexOfFirst { it.title.firstOrNull()?.isLetter() == false }
                            } else {
                                displayedGroups.indexOfFirst { it.title.startsWith(letter, ignoreCase = true) }
                            }
                            if (targetIndex >= 0) {
                                coroutineScope.launch { listState.scrollToItem(targetIndex) }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. TELA DE DRILLDOWN (AURŌ DRILLDOWN)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroDrilldownScreen(
    category: LibraryCategory,
    groupTitle: String,
    tracks: List<Track>,
    currentPlayingId: Long,
    onBackToCategoryList: () -> Unit,
    onBackToLibraryRoot: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onTrackMenuClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroBreadcrumbHeader(
            items = listOf(
                AuroBreadcrumbItem("Biblioteca"),
                AuroBreadcrumbItem(category.title),
                AuroBreadcrumbItem(groupTitle)
            ),
            onItemClick = { index ->
                when (index) {
                    0 -> onBackToLibraryRoot()
                    1 -> onBackToCategoryList()
                }
            },
            canGoBack = true,
            onBackClick = onBackToCategoryList
        )

        // Banner do grupo e ações
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = groupTitle,
                color = AuroColor.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${tracks.size} faixas • ${formatTimeMs(totalDurationMs)}",
                color = AuroColor.TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AuroGlass(
                    kind = AuroGlassKind.Card,
                    onClick = onPlayAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = AuroColor.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tocar Todas",
                            color = AuroColor.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                AuroGlass(
                    kind = AuroGlassKind.Card,
                    onClick = onShuffleAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = AuroColor.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Aleatório",
                            color = AuroColor.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)

        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp, end = if (tracks.size > 15) 28.dp else 0.dp)
            ) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    AuroTrackRow(
                        track = track,
                        trackIndex = index,
                        isPlayingThis = track.id == currentPlayingId,
                        onClick = { onTrackClick(track, index) },
                        onLongClick = { onTrackLongClick(track) },
                        onMenuClick = { onTrackMenuClick(track) }
                    )
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                }
            }

            if (tracks.size > 15) {
                AuroAlphabetFastScroller(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(bottom = 120.dp),
                    onLetterSelected = { letter ->
                        val targetIndex = if (letter == '#') {
                            tracks.indexOfFirst { it.title.firstOrNull()?.isLetter() == false }
                        } else {
                            tracks.indexOfFirst { it.title.startsWith(letter, ignoreCase = true) }
                        }
                        if (targetIndex >= 0) {
                            coroutineScope.launch { listState.scrollToItem(targetIndex) }
                        }
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. TELA NOW PLAYING OFICIAL DO AURŌ DESIGN SYSTEM
// Reproduz fielmente a spec e o espécime visual (especimen_design_system.png).
// ─────────────────────────────────────────────────────────────────────────────
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
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenTrackInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(AuroColor.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Nenhuma faixa selecionada", color = AuroColor.TextSecondary)
        }
        return
    }

    val currentProgressFraction = if (durationMs > 0) {
        (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        // Barra superior discreta para recolher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Recolher",
                    tint = AuroColor.TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (queueSize > 0) {
                Text(
                    text = "${currentTrackIndex + 1} / $queueSize",
                    color = AuroColor.TextTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable(onClick = onOpenQueue)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(36.dp))
            }

            Spacer(modifier = Modifier.size(36.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Capa com cantos arredondados de 22dp (sem vidro: fonte da cor)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(AuroRadius.cover))
                .background(AuroColor.Track)
                .pointerInput(Unit) {
                    var totalDragX = 0f
                    var totalDragY = 0f
                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDragEnd = {
                            val absX = kotlin.math.abs(totalDragX)
                            val absY = kotlin.math.abs(totalDragY)
                            if (absY > absX) {
                                if (totalDragY > 40f) onCollapse()
                                else if (totalDragY < -40f) onOpenQueue()
                            } else {
                                if (totalDragX < -45f) onNext()
                                else if (totalDragX > 45f) onPrevious()
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
                    contentDescription = "Capa",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = AuroColor.TextTertiary,
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Título e Artista à esquerda com botões de vidro ★ e ⋮ à direita (Fiel ao espécime)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = track.title,
                    color = AuroColor.TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist.ifEmpty { "Desconhecido" },
                    color = AuroColor.TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ★ Círculo de vidro de 40dp
                val isFavorite = track.rating > 0
                AuroGlass(
                    kind = AuroGlassKind.Button,
                    onClick = { onRatingChanged(if (isFavorite) 0 else 5) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Favoritar",
                            tint = if (isFavorite) LocalAuroAccent.current.a.value else AuroColor.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // ⋮ Círculo de vidro de 40dp
                AuroGlass(
                    kind = AuroGlassKind.Button,
                    onClick = onOpenTrackInfo,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Mais opções",
                            tint = AuroColor.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Barra de Progresso Oficial (Trilho 3dp #2D2D2D, Accent A, bolinha branca 10dp)
        AuroProgressBar(
            progressFraction = currentProgressFraction,
            durationMs = durationMs,
            onSeek = onSeek,
            onSeekJumpBackward = onSeekJumpBackward,
            onSeekJumpForward = onSeekJumpForward
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Controles de Reprodução (3 Círculos de Vidro Preto + Shuffle/Repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(onClick = onToggleShuffle, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Aleatório",
                    tint = if (shuffleMode != ShuffleMode.OFF) LocalAuroAccent.current.a.value else AuroColor.TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Anterior: Círculo de vidro de 56dp
            AuroGlass(
                kind = AuroGlassKind.Button,
                modifier = Modifier
                    .size(56.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onBeginRewind()
                                tryAwaitRelease()
                                onEndRewind()
                            },
                            onTap = { onPrevious() }
                        )
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Play/Pause: Círculo de vidro de 76dp
            AuroGlass(
                kind = AuroGlassKind.Button,
                onClick = onPlayPause,
                modifier = Modifier.size(76.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            // Próximo: Círculo de vidro de 56dp
            AuroGlass(
                kind = AuroGlassKind.Button,
                modifier = Modifier
                    .size(56.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onBeginFastForward()
                                tryAwaitRelease()
                                onEndFastForward()
                            },
                            onTap = { onNext() }
                        )
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Próxima",
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Repeat
            IconButton(onClick = onToggleRepeat, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = when (repeatMode) {
                        RepeatMode.ONE -> Icons.Rounded.RepeatOne
                        RepeatMode.ALL, RepeatMode.CATEGORY, RepeatMode.ADVANCE_CATEGORY -> Icons.Rounded.Repeat
                        else -> Icons.Rounded.Repeat
                    },
                    contentDescription = "Repetir",
                    tint = if (repeatMode != RepeatMode.OFF) LocalAuroAccent.current.a.value else AuroColor.TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Cards de Fila e Letras em Vidro Preto (Exatamente como no espécime)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AuroGlass(
                kind = AuroGlassKind.Card,
                onClick = onOpenQueue,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Fila",
                        color = AuroColor.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            AuroGlass(
                kind = AuroGlassKind.Card,
                onClick = onOpenLyrics,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Letras",
                        color = AuroColor.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Título da Seção (ex: VOX (7) como no espécime)
        val headerTitle = if (categoryTitle.isNotBlank()) {
            if (queueSize > 0) "${categoryTitle.uppercase()} ($queueSize)" else categoryTitle.uppercase()
        } else if (queueSize > 0) "FILA ($queueSize)" else "FILA"

        Text(
            text = headerTitle,
            color = AuroColor.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable(onClick = onOpenQueue)
        )

        // Pré-visualização da Fila / Próxima Faixa (Direto sobre o preto, como no espécime)
        if (upcomingTracks.isNotEmpty()) {
            val nextTrack = upcomingTracks.firstOrNull()
            val afterNextTrack = upcomingTracks.getOrNull(1)

            if (nextTrack != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenQueue)
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = nextTrack.title,
                        color = AuroColor.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = nextTrack.artist.ifEmpty { "Desconhecido" },
                        color = AuroColor.TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            if (afterNextTrack != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenQueue)
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = afterNextTrack.title,
                        color = AuroColor.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = afterNextTrack.artist.ifEmpty { "Desconhecido" },
                        color = AuroColor.TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }
        } else {
            Text(
                text = "Fim da fila",
                color = AuroColor.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. TELA DE EQUALIZADOR (AURŌ EQUALIZER)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuroEqualizerScreen(
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
    isMono: Boolean,
    onToggleMono: (Boolean) -> Unit,
    bandGains: List<Float>,
    onBandGainChange: (Int, Float) -> Unit,
    activePresetName: String,
    presets: List<MusicDatabase.EqPreset>,
    onSelectPreset: (MusicDatabase.EqPreset) -> Unit,
    onResetEq: () -> Unit = {},
    onSavePreset: () -> Unit = {},
    onDeletePreset: (MusicDatabase.EqPreset) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAuroAccent.current.a.value
    val bandFreqs = remember {
        listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroBreadcrumbHeader(
            items = listOf(
                AuroBreadcrumbItem("Biblioteca"),
                AuroBreadcrumbItem("Equalizador")
            ),
            onItemClick = { onClose() },
            canGoBack = true,
            onBackClick = onClose,
            rightActions = {
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = onToggleEq,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent
                    )
                )
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Presets
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRESETS",
                        color = AuroColor.TextTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Redefinir",
                            color = AuroColor.TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { onResetEq() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "+ Salvar",
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onSavePreset() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets, key = { it.id }) { p ->
                        val isSelected = p.name.equals(activePresetName, ignoreCase = true)
                        AuroGlass(
                            kind = AuroGlassKind.Card,
                            onClick = { onSelectPreset(p) },
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(horizontal = 14.dp)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    text = p.name,
                                    color = if (isSelected) accent else AuroColor.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (!p.isBuiltin) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Excluir preset",
                                        tint = AuroColor.TextTertiary,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onDeletePreset(p) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tom & Pré-amp
            item {
                AuroGlass(
                    kind = AuroGlassKind.Card,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "TOM & PRÉ-AMP",
                                    color = AuroColor.TextTertiary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Switch(
                                    checked = toneEnabled,
                                    onCheckedChange = onToggleTone,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = accent
                                    ),
                                    modifier = Modifier.height(20.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Mono", color = AuroColor.TextSecondary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Checkbox(
                                    checked = isMono,
                                    onCheckedChange = onToggleMono,
                                    colors = CheckboxDefaults.colors(checkedColor = accent)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        AuroToneRow(label = "Pré-Amp", value = preampDb, range = -12f..12f, enabled = eqEnabled, onValueChange = onPreampChange)
                        AuroToneRow(label = "Graves", value = bassDb, range = -12f..12f, enabled = eqEnabled && toneEnabled, onValueChange = onBassChange)
                        AuroToneRow(label = "Agudos", value = trebleDb, range = -12f..12f, enabled = eqEnabled && toneEnabled, onValueChange = onTrebleChange)
                        AuroToneRow(label = "Balanço", value = balance, range = -1f..1f, isBalance = true, enabled = true, onValueChange = onBalanceChange)
                    }
                }
            }

            // 10 Bandas
            item {
                AuroGlass(
                    kind = AuroGlassKind.Card,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "10 BANDAS",
                            color = AuroColor.TextTertiary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        bandFreqs.forEachIndexed { bandIndex, freqLabel ->
                            val gain = bandGains.getOrElse(bandIndex) { 0f }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = freqLabel,
                                    color = AuroColor.TextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(50.dp)
                                )

                                Slider(
                                    value = gain,
                                    onValueChange = { onBandGainChange(bandIndex, it) },
                                    valueRange = -12f..12f,
                                    enabled = eqEnabled,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = accent,
                                        inactiveTrackColor = AuroColor.Track,
                                        disabledThumbColor = AuroColor.TextTertiary,
                                        disabledActiveTrackColor = AuroColor.Track,
                                        disabledInactiveTrackColor = AuroColor.Track
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(20.dp)
                                )

                                Text(
                                    text = String.format(Locale.getDefault(), "%+.1fdB", gain),
                                    color = AuroColor.TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(50.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuroToneRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isBalance: Boolean = false,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    val accent = LocalAuroAccent.current.a.value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (enabled) AuroColor.TextSecondary else AuroColor.TextTertiary,
            fontSize = 12.sp,
            modifier = Modifier.width(86.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent,
                inactiveTrackColor = AuroColor.Track,
                disabledThumbColor = AuroColor.TextTertiary,
                disabledActiveTrackColor = AuroColor.Track,
                disabledInactiveTrackColor = AuroColor.Track
            ),
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
        )
        val textVal = if (isBalance) {
            when {
                value < -0.05f -> "L ${(value * -100).roundToInt()}%"
                value > 0.05f -> "R ${(value * 100).roundToInt()}%"
                else -> "Centro"
            }
        } else {
            String.format(Locale.getDefault(), "%+.1fdB", value)
        }
        Text(
            text = textVal,
            color = if (enabled) AuroColor.TextPrimary else AuroColor.TextTertiary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.width(50.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. TELA DE BUSCA (AURŌ SEARCH)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroSearchScreen(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    results: List<Track>,
    currentPlayingId: Long,
    onTrackClick: (Track, Int) -> Unit,
    onTrackMenuClick: (Track) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
            .statusBarsPadding()
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Voltar",
                    tint = AuroColor.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Campo de busca com vidro preto
            AuroGlass(
                kind = AuroGlassKind.Card,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = AuroColor.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Buscar faixas, artistas...", color = AuroColor.TextTertiary, fontSize = 13.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = AuroColor.TextPrimary,
                            unfocusedTextColor = AuroColor.TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Limpar",
                                tint = AuroColor.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = if (searchQuery.isEmpty()) "Digite para buscar" else "${results.size} resultados encontrados",
            color = AuroColor.TextTertiary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            itemsIndexed(results, key = { _, t -> t.id }) { index, track ->
                AuroTrackRow(
                    track = track,
                    trackIndex = index,
                    isPlayingThis = track.id == currentPlayingId,
                    onClick = { onTrackClick(track, index) },
                    onLongClick = {},
                    onMenuClick = { onTrackMenuClick(track) }
                )
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. TELA DEDICADA DE FILA DE REPRODUÇÃO (AURŌ QUEUE SCREEN)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroQueueScreen(
    priorityTracks: List<Track>,
    regularTracks: List<Track>,
    currentTrackId: Long,
    onBackClick: () -> Unit,
    onTrackClick: (Track, Int, Boolean) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onClearQueue: () -> Unit,
    onShuffleQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCount = priorityTracks.size + regularTracks.size
    val totalDuration = priorityTracks.sumOf { it.durationMs } + regularTracks.sumOf { it.durationMs }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroBreadcrumbHeader(
            items = listOf(
                AuroBreadcrumbItem("Biblioteca"),
                AuroBreadcrumbItem("Fila de Reprodução")
            ),
            onItemClick = { onBackClick() },
            canGoBack = true,
            onBackClick = onBackClick,
            rightActions = {
                if (totalCount > 0) {
                    IconButton(onClick = onShuffleQueue, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Aleatório",
                            tint = AuroColor.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onClearQueue, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Limpar Fila",
                            tint = AuroColor.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )

        // Banner informativo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FILA TOTAL",
                    color = AuroColor.TextTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$totalCount faixas • ${formatTimeMs(totalDuration)}",
                    color = AuroColor.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (priorityTracks.isNotEmpty()) {
                Text(
                    text = "${priorityTracks.size} prioritárias",
                    color = LocalAuroAccent.current.a.value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Seção Prioritária (A SEGUIR)
            if (priorityTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "A SEGUIR (PRIORITÁRIA)",
                        color = LocalAuroAccent.current.a.value,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                itemsIndexed(priorityTracks, key = { idx, t -> "prio_${t.id}_$idx" }) { index, track ->
                    AuroQueueTrackRow(
                        track = track,
                        index = index,
                        isPlaying = track.id == currentTrackId,
                        isPriority = true,
                        onClick = { onTrackClick(track, index, true) },
                        onRemove = { onRemoveTrack(track) }
                    )
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                }
            }

            // Seção Regular
            if (regularTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "REPRODUÇÃO NORMAL",
                        color = AuroColor.TextTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                itemsIndexed(regularTracks, key = { idx, t -> "reg_${t.id}_$idx" }) { index, track ->
                    AuroQueueTrackRow(
                        track = track,
                        index = index,
                        isPlaying = track.id == currentTrackId,
                        isPriority = false,
                        onClick = { onTrackClick(track, index, false) },
                        onRemove = { onRemoveTrack(track) }
                    )
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun AuroQueueTrackRow(
    track: Track,
    index: Int,
    isPlaying: Boolean,
    isPriority: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val accent = LocalAuroAccent.current.a.value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isPlaying) {
                Text(
                    text = "ılı",
                    color = accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "${index + 1}",
                    color = AuroColor.TextTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isPlaying) accent else AuroColor.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist.ifEmpty { "Desconhecido" },
                color = AuroColor.TextSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatTimeMs(track.durationMs),
            color = AuroColor.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remover",
                tint = AuroColor.TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
