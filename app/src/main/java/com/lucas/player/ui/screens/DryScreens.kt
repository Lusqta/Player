package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.lucas.player.ui.theme.DryColors
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 1. TELA RAIZ DA BIBLIOTECA (Totalmente Seca)
 * Lista simples de categorias com divisores finos.
 */
@Composable
fun DryLibraryRootScreen(
    totalTracksCount: Int,
    onSelectCategory: (LibraryCategory) -> Unit,
    onSortClick: () -> Unit,
    onRescanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = remember {
        listOf(
            CategoryCardData(LibraryCategory.ALL_SONGS, "Todas as Músicas", "$totalTracksCount faixas", Icons.Rounded.MusicNote),
            CategoryCardData(LibraryCategory.FOLDERS, "Pastas", "Diretórios locais", Icons.Rounded.Folder),
            CategoryCardData(LibraryCategory.ALBUMS, "Álbuns", "Coleções", Icons.Rounded.Album),
            CategoryCardData(LibraryCategory.ARTISTS, "Artistas", "Intérpretes", Icons.Rounded.Person),
            CategoryCardData(LibraryCategory.GENRES, "Gêneros", "Estilos", Icons.Rounded.Category),
            CategoryCardData(LibraryCategory.YEARS, "Anos", "Por lançamento", Icons.Rounded.DateRange),
            CategoryCardData(LibraryCategory.COMPOSERS, "Compositores", "Autores", Icons.Rounded.HistoryEdu),
            CategoryCardData(LibraryCategory.PLAYLISTS, "Listas de Reprodução", "Playlists", Icons.AutoMirrored.Rounded.PlaylistPlay),
            CategoryCardData(LibraryCategory.QUEUE, "Fila de Reprodução", "Fila prioritária", Icons.AutoMirrored.Rounded.QueueMusic),
            CategoryCardData(LibraryCategory.RECENTLY_ADDED, "Adicionadas Recentemente", "Últimas adicionadas", Icons.Rounded.Schedule),
            CategoryCardData(LibraryCategory.MOST_PLAYED, "Mais Tocadas", "Mais ouvidas", Icons.Rounded.Whatshot),
            CategoryCardData(LibraryCategory.TOP_RATED, "Mais Votadas", "Favoritas", Icons.Rounded.Star)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DryColors.Background)
    ) {
        DryBreadcrumbHeader(
            items = listOf(BreadcrumbItem("Biblioteca")),
            onItemClick = {},
            canGoBack = false,
            onBackClick = {},
            rightActions = {
                IconButton(onClick = onSortClick, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Ordenar", tint = DryColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRescanClick, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar", tint = DryColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(categories) { cat ->
                DryCategoryCard(
                    title = cat.title,
                    subtitle = cat.subtitle,
                    icon = cat.icon,
                    onClick = { onSelectCategory(cat.category) }
                )
                HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp)
            }
        }
    }
}

data class CategoryCardData(
    val category: LibraryCategory,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

/**
 * 2. TELA DE LISTA DE CATEGORIA (Totalmente Seca)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DryCategoryListScreen(
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
            .background(DryColors.Background)
    ) {
        DryBreadcrumbHeader(
            items = listOf(
                BreadcrumbItem("Biblioteca"),
                BreadcrumbItem(category.title)
            ),
            onItemClick = { index ->
                if (index == 0) onBackClick()
            },
            canGoBack = true,
            onBackClick = onBackClick,
            rightActions = {
                IconButton(onClick = onSortClick, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Ordenar", tint = DryColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        )

        // Banner de Ações Rápidas do Poweramp em Todas as Músicas
        if (isAllSongs && displayedTracks.isNotEmpty()) {
            Surface(
                color = DryColors.Surface,
                modifier = Modifier.fillMaxWidth().border(0.5.dp, DryColors.BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayAll,
                        colors = ButtonDefaults.buttonColors(containerColor = DryColors.SurfaceElevated),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = DryColors.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tocar Todas", color = DryColors.TextPrimary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onShuffleAll,
                        colors = ButtonDefaults.buttonColors(containerColor = DryColors.SurfaceElevated),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = DryColors.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aleatório", color = DryColors.TextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }

        // Ação de Nova Lista de Reprodução estilo Poweramp
        if (isPlaylists) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCreatePlaylist() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = DryColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("+ Nova Lista de Reprodução", color = DryColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp)
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
                        DryTrackRow(
                            track = track,
                            trackIndex = index,
                            isPlayingThis = track.id == currentPlayingId,
                            onClick = { onTrackClick(track, index) },
                            onLongClick = { onTrackLongClick(track) },
                            onMenuClick = { onTrackMenuClick(track) }
                        )
                        HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp)
                    }
                }

                if (displayedTracks.size > 15) {
                    DryAlphabetFastScroller(
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
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.title,
                                    color = DryColors.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = group.subtitle,
                                    color = DryColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = DryColors.TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp)
                    }
                }

                if (displayedGroups.size > 15) {
                    DryAlphabetFastScroller(
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

/**
 * 3. TELA DE DRILLDOWN (Músicas de um Álbum/Artista/Pasta)
 */
@Composable
fun DryDrilldownScreen(
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
            .background(DryColors.Background)
    ) {
        DryBreadcrumbHeader(
            items = listOf(
                BreadcrumbItem("Biblioteca"),
                BreadcrumbItem(category.title),
                BreadcrumbItem(groupTitle)
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

        // Banner de Ações
        Surface(
            color = DryColors.Surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = groupTitle,
                    color = DryColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tracks.size} faixas • ${formatTimeMs(totalDurationMs)}",
                    color = DryColors.TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPlayAll,
                        colors = ButtonDefaults.buttonColors(containerColor = DryColors.SurfaceElevated),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = DryColors.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tocar Todas", color = DryColors.TextPrimary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onShuffleAll,
                        colors = ButtonDefaults.buttonColors(containerColor = DryColors.SurfaceElevated),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = DryColors.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aleatório", color = DryColors.TextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }
        HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp)

        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp, end = if (tracks.size > 15) 28.dp else 0.dp)
            ) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    DryTrackRow(
                        track = track,
                        trackIndex = index,
                        isPlayingThis = track.id == currentPlayingId,
                        onClick = { onTrackClick(track, index) },
                        onLongClick = { onTrackLongClick(track) },
                        onMenuClick = { onTrackMenuClick(track) }
                    )
                    HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp)
                }
            }

            if (tracks.size > 15) {
                DryAlphabetFastScroller(
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

/**
 * 4. TELA NOW PLAYING TOTALMENTE SECA
 * Sem botões coloridos, sem auras, sem decorações inventadas.
 */
@Composable
fun DryNowPlayingScreen(
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
    onOpenEqualizer: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenTrackInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DryColors.Background),
            contentAlignment = Alignment.Center
        ) {
            Text("Nenhuma faixa selecionada", color = DryColors.TextSecondary)
        }
        return
    }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragProgressFraction by remember { mutableFloatStateOf(0f) }

    val currentProgressFraction = if (durationMs > 0) {
        (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val displayedProgress = if (isDraggingSlider) dragProgressFraction else currentProgressFraction
    val displayedMs = (displayedProgress * durationMs).toLong()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DryColors.Background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Cabeçalho Superior Seco
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onCollapse)
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Voltar",
                    tint = DryColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = categoryTitle,
                    color = DryColors.TextPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp)
                )
            }

            if (queueSize > 0) {
                Text(
                    text = "${currentTrackIndex + 1} / $queueSize",
                    color = DryColors.TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCollapse
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenLyrics, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = "Letras",
                        tint = DryColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onOpenQueue, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Fila",
                        tint = DryColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onOpenTrackInfo, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Info",
                        tint = DryColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.3f))

        // Capa Quadrada Seca com Gestos Unificados (Poweramp-style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(DryColors.Surface)
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
                                if (totalDragY > 35f) {
                                    onCollapse()
                                } else if (totalDragY < -35f) {
                                    onOpenQueue()
                                }
                            } else {
                                if (totalDragX < -45f) {
                                    onNext()
                                } else if (totalDragX > 45f) {
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
                    contentDescription = "Capa",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = DryColors.TextTertiary,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.3f))

        // Metadados
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = track.title,
                color = DryColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${track.artist.ifEmpty { "Desconhecido" }} • ${track.album.ifEmpty { "Desconhecido" }}",
                color = DryColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Detalhes de Áudio e Classificação 5 Estrelas
            val rateKhz = if (track.sampleRateHz > 0) "${track.sampleRateHz / 1000}kHz" else ""
            val bitrateStr = if (track.bitrateKbps > 0) "${track.bitrateKbps}kbps" else ""
            val codecInfo = listOf(track.formatTag.uppercase(), rateKhz, bitrateStr).filter { it.isNotEmpty() }.joinToString(" • ")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (codecInfo.isNotEmpty()) {
                    Text(
                        text = "[$codecInfo]",
                        color = DryColors.TextTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable(onClick = onOpenTrackInfo)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                DryRatingBar(
                    rating = track.rating,
                    onRatingSelected = onRatingChanged
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Barra de Progresso em Onda (Poweramp Waveseek) com Salto de 10s
        Column(modifier = Modifier.fillMaxWidth()) {
            DryWaveformSeekbar(
                progressFraction = displayedProgress,
                durationMs = durationMs,
                onSeek = onSeek,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            )

            var showRemainingTime by remember { mutableStateOf(true) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatTimeMs(displayedMs),
                        color = DryColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onSeekJumpBackward, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = "-10s",
                            tint = DryColors.TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSeekJumpForward, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = "+10s",
                            tint = DryColors.TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    val remainingMs = (durationMs - displayedMs).coerceAtLeast(0)
                    Text(
                        text = if (showRemainingTime) "-${formatTimeMs(remainingMs)}" else formatTimeMs(durationMs),
                        color = DryColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Controles de Reprodução Neutros com Avanço/Retrocesso Contínuo por Toque Longo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Aleatório",
                    tint = if (shuffleMode != ShuffleMode.OFF) DryColors.TextPrimary else DryColors.TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onBeginRewind()
                                tryAwaitRelease()
                                onEndRewind()
                            },
                            onTap = { onPrevious() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = DryColors.TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onPlayPause, modifier = Modifier.size(60.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                    tint = DryColors.TextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onBeginFastForward()
                                tryAwaitRelease()
                                onEndFastForward()
                            },
                            onTap = { onNext() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Próxima",
                    tint = DryColors.TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onToggleRepeat) {
                Icon(
                    imageVector = when (repeatMode) {
                        RepeatMode.ONE -> Icons.Rounded.RepeatOne
                        RepeatMode.ALL, RepeatMode.CATEGORY, RepeatMode.ADVANCE_CATEGORY -> Icons.Rounded.Repeat
                        else -> Icons.Rounded.Repeat
                    },
                    contentDescription = "Repetir",
                    tint = if (repeatMode != RepeatMode.OFF) DryColors.TextPrimary else DryColors.TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

/**
 * 5. TELA DE EQUALIZADOR TOTALMENTE SECA
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DryEqualizerScreen(
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
    val bandFreqs = remember {
        listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DryColors.Background)
    ) {
        DryBreadcrumbHeader(
            items = listOf(
                BreadcrumbItem("Biblioteca"),
                BreadcrumbItem("Equalizador")
            ),
            onItemClick = { onClose() },
            canGoBack = true,
            onBackClick = onClose,
            rightActions = {
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = onToggleEq,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DryColors.TextPrimary,
                        checkedTrackColor = DryColors.SurfaceElevated
                    )
                )
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Presets
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PRESETS", color = DryColors.TextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Redefinir",
                            color = DryColors.TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { onResetEq() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "+ Salvar",
                            color = DryColors.Accent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onSavePreset() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets, key = { it.id }) { p ->
                        val isSelected = p.name.equals(activePresetName, ignoreCase = true)
                        Surface(
                            color = if (isSelected) DryColors.SurfaceActive else DryColors.Surface,
                            modifier = Modifier
                                .border(0.5.dp, DryColors.BorderSubtle)
                                .combinedClickable(
                                    onClick = { onSelectPreset(p) },
                                    onLongClick = {
                                        if (!p.isBuiltin) onDeletePreset(p)
                                    }
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = p.name,
                                    color = if (isSelected) DryColors.TextPrimary else DryColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (!p.isBuiltin) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Excluir preset",
                                        tint = DryColors.TextTertiary,
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
                Surface(
                    color = DryColors.Surface,
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, DryColors.BorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("TOM & PRÉ-AMP", color = DryColors.TextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = toneEnabled,
                                    onCheckedChange = onToggleTone,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = DryColors.TextPrimary,
                                        checkedTrackColor = DryColors.SurfaceElevated
                                    ),
                                    modifier = Modifier.height(20.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Mono", color = DryColors.TextSecondary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Checkbox(
                                    checked = isMono,
                                    onCheckedChange = onToggleMono,
                                    colors = CheckboxDefaults.colors(checkedColor = DryColors.TextPrimary)
                                )
                            }
                        }

                        ToneRow(label = "Pré-Amp", value = preampDb, range = -12f..12f, enabled = eqEnabled, onValueChange = onPreampChange)
                        ToneRow(label = "Graves", value = bassDb, range = -12f..12f, enabled = eqEnabled && toneEnabled, onValueChange = onBassChange)
                        ToneRow(label = "Agudos", value = trebleDb, range = -12f..12f, enabled = eqEnabled && toneEnabled, onValueChange = onTrebleChange)
                        ToneRow(label = "Balanço", value = balance, range = -1f..1f, isBalance = true, enabled = true, onValueChange = onBalanceChange)
                    }
                }
            }

            // 10 Bandas
            item {
                Surface(
                    color = DryColors.Surface,
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, DryColors.BorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("10 BANDAS", color = DryColors.TextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(6.dp))

                        bandFreqs.forEachIndexed { bandIndex, freqLabel ->
                            val gain = bandGains.getOrElse(bandIndex) { 0f }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = freqLabel,
                                    color = DryColors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(48.dp)
                                )

                                Slider(
                                    value = gain,
                                    onValueChange = { onBandGainChange(bandIndex, it) },
                                    valueRange = -12f..12f,
                                    enabled = eqEnabled,
                                    colors = SliderDefaults.colors(
                                        thumbColor = DryColors.TextPrimary,
                                        activeTrackColor = DryColors.TextPrimary,
                                        inactiveTrackColor = DryColors.BorderSubtle
                                    ),
                                    modifier = Modifier.weight(1f).height(18.dp)
                                )

                                Text(
                                    text = String.format(Locale.getDefault(), "%+.1fdB", gain),
                                    color = DryColors.TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(48.dp)
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
private fun ToneRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isBalance: Boolean = false,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = if (enabled) DryColors.TextSecondary else DryColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.width(90.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = DryColors.TextPrimary,
                activeTrackColor = DryColors.TextPrimary,
                inactiveTrackColor = DryColors.BorderSubtle,
                disabledThumbColor = DryColors.TextTertiary,
                disabledActiveTrackColor = DryColors.BorderSubtle,
                disabledInactiveTrackColor = DryColors.BorderSubtle
            ),
            modifier = Modifier.weight(1f).height(18.dp)
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
            color = if (enabled) DryColors.TextPrimary else DryColors.TextTertiary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
    }
}

/**
 * 6. TELA DE BUSCA TOTALMENTE SECA
 */
@Composable
fun DrySearchScreen(
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
            .background(DryColors.Background)
            .statusBarsPadding()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar", tint = DryColors.TextPrimary, modifier = Modifier.size(20.dp))
            }

            Surface(
                color = DryColors.Surface,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .border(0.5.dp, DryColors.BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = DryColors.TextTertiary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Buscar faixas, artistas...", color = DryColors.TextTertiary, fontSize = 13.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = DryColors.TextPrimary,
                            unfocusedTextColor = DryColors.TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Limpar", tint = DryColors.TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Text(
            text = if (searchQuery.isEmpty()) "Digite para buscar" else "${results.size} resultados",
            color = DryColors.TextTertiary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            itemsIndexed(results, key = { _, t -> t.id }) { index, track ->
                DryTrackRow(
                    track = track,
                    trackIndex = index,
                    isPlayingThis = track.id == currentPlayingId,
                    onClick = { onTrackClick(track, index) },
                    onLongClick = {},
                    onMenuClick = { onTrackMenuClick(track) }
                )
                HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp)
            }
        }
    }
}

/**
 * 7. TELA DEDICADA DE FILA DE REPRODUÇÃO (Poweramp Queue View)
 */
@Composable
fun DryQueueScreen(
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
            .background(DryColors.Background)
    ) {
        DryBreadcrumbHeader(
            items = listOf(
                BreadcrumbItem("Biblioteca"),
                BreadcrumbItem("Fila de Reprodução")
            ),
            onItemClick = { onBackClick() },
            canGoBack = true,
            onBackClick = onBackClick,
            rightActions = {
                if (totalCount > 0) {
                    IconButton(onClick = onShuffleQueue, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Aleatório", tint = DryColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onClearQueue, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Limpar Fila", tint = DryColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        // Banner informativo da fila
        Surface(
            color = DryColors.Surface,
            modifier = Modifier.fillMaxWidth().border(0.5.dp, DryColors.BorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$totalCount faixas • ${formatTimeMs(totalDuration)}",
                    color = DryColors.TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (priorityTracks.isNotEmpty()) {
                    Text(
                        text = "[${priorityTracks.size} prioritárias]",
                        color = DryColors.Accent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (totalCount == 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = DryColors.TextTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Fila de reprodução vazia",
                        color = DryColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Toque em 'Tocar a seguir' no menu de qualquer faixa.",
                        color = DryColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                if (priorityTracks.isNotEmpty()) {
                    item {
                        Surface(
                            color = DryColors.SurfaceElevated,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "A SEGUIR (PRIORITÁRIA)",
                                color = DryColors.Accent,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }

                    itemsIndexed(priorityTracks, key = { _, t -> "priority_${t.id}" }) { index, track ->
                        QueueTrackRow(
                            track = track,
                            trackIndex = index,
                            isPlayingThis = track.id == currentTrackId,
                            isPriority = true,
                            onClick = { onTrackClick(track, index, true) },
                            onRemove = { onRemoveTrack(track) }
                        )
                        HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp)
                    }
                }

                if (regularTracks.isNotEmpty()) {
                    item {
                        Surface(
                            color = DryColors.SurfaceElevated,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "FILA ATIVA",
                                color = DryColors.TextTertiary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }

                    itemsIndexed(regularTracks, key = { idx, t -> "regular_${t.id}_$idx" }) { index, track ->
                        QueueTrackRow(
                            track = track,
                            trackIndex = index,
                            isPlayingThis = track.id == currentTrackId,
                            isPriority = false,
                            onClick = { onTrackClick(track, index, false) },
                            onRemove = { onRemoveTrack(track) }
                        )
                        HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: Track,
    trackIndex: Int,
    isPlayingThis: Boolean,
    isPriority: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isPlayingThis) DryColors.SurfaceActive else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isPlayingThis) {
                Icon(
                    imageVector = Icons.Rounded.Equalizer,
                    contentDescription = "Tocando",
                    tint = DryColors.TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = "${trackIndex + 1}",
                    color = DryColors.TextTertiary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPriority) {
                    Text(
                        text = "[A SEGUIR] ",
                        color = DryColors.Accent,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = track.title,
                    color = DryColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                text = "${track.artist.ifEmpty { "Desconhecido" }} • ${track.album.ifEmpty { "Desconhecido" }}",
                color = DryColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = formatTimeMs(track.durationMs),
            color = DryColors.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remover da fila",
                tint = DryColors.TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
