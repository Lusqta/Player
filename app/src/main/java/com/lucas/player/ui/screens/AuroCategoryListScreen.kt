package com.lucas.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lucas.player.ui.theme.AuroMotion
import com.lucas.player.ui.theme.AuroSquircle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.data.MusicDatabase
import com.lucas.player.model.LibraryCategory
import com.lucas.player.model.Track
import com.lucas.player.model.formatArtistsDisplay
import com.lucas.player.model.getTitleRes
import com.lucas.player.ui.GroupItem
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroType
import com.lucas.player.ui.theme.AuroGlass
import com.lucas.player.ui.theme.AuroGlassKind
import com.lucas.player.ui.theme.AuroSpace
import com.lucas.player.ui.theme.LocalAuroBottomInset
import kotlinx.coroutines.launch

/**
 * AURŌ DESIGN SYSTEM v2 - TELA DE LISTA DE CATEGORIA (AURŌ CATEGORY LIST)
 */
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
    coverTrackFinder: (GroupItem) -> Track? = { null },
    onPlayTracksList: ((List<Track>, Int) -> Unit)? = null,
    onShuffleTracksList: ((List<Track>) -> Unit)? = null,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    onSortClick: () -> Unit,
    errorTrackIds: Set<Long> = emptySet(),
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedTrackIds: Set<Long> = emptySet(),
    onToggleSelectTrack: (Track) -> Unit = {},
    showFileFormat: Boolean = true,
    albumMetaMap: Map<String, MusicDatabase.AlbumMeta> = emptyMap(),
    albumMediaVersion: Int = 0
) {
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isTrackListCategory = category == LibraryCategory.ALL_SONGS ||
            category == LibraryCategory.MOST_PLAYED ||
            category == LibraryCategory.RECENTLY_ADDED ||
            category == LibraryCategory.RECENTLY_PLAYED ||
            category == LibraryCategory.TOP_RATED
    val isPlaylists = category == LibraryCategory.PLAYLISTS
    val isAlbumsGrid = category == LibraryCategory.ALBUMS

    var searchQuery by remember(category) { mutableStateOf("") }
    val trimmedQuery = if (category == LibraryCategory.ALL_SONGS) searchQuery.trim() else ""

    val filteredTracks = remember(displayedTracks, trimmedQuery) {
        if (trimmedQuery.isEmpty()) displayedTracks
        else displayedTracks.filter { track ->
            track.title.contains(trimmedQuery, ignoreCase = true) ||
                track.artist.contains(trimmedQuery, ignoreCase = true) ||
                track.album.contains(trimmedQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(listState.isScrollInProgress, category) {
        if (category == LibraryCategory.ALL_SONGS && listState.isScrollInProgress) {
            keyboardController?.hide()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        AuroTopBar(
            title = stringResource(category.getTitleRes()),
            subtitle = when {
                category == LibraryCategory.ALL_SONGS && trimmedQuery.isNotEmpty() ->
                    stringResource(R.string.tracks_filtered_count, filteredTracks.size, displayedTracks.size)
                isTrackListCategory ->
                    formatTracksCount(displayedTracks.size)
                else ->
                    pluralStringResource(R.plurals.items_count, displayedGroups.size, displayedGroups.size)
            },
            canGoBack = true,
            onBackClick = onBackClick,
            rightActions = {
                IconButton(onClick = onSortClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = stringResource(R.string.cd_sort),
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )

        // Campo de busca interna em "Todas as músicas"
        if (category == LibraryCategory.ALL_SONGS) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuroSpace.ScreenMargin, vertical = 6.dp)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(AuroColor.SurfaceCard)
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    CompositionLocalProvider(
                        LocalTextSelectionColors provides TextSelectionColors(
                            handleColor = Color.White,
                            backgroundColor = Color.White.copy(alpha = 0.25f)
                        )
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = AuroType.Body.copy(color = AuroColor.TextPrimary),
                            cursorBrush = SolidColor(Color.White),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { keyboardController?.hide() }
                            ),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_songs_placeholder),
                                            style = AuroType.Body.copy(color = AuroColor.TextTertiary),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_clear_search),
                                tint = AuroColor.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Banner de Ações Rápidas em categorias baseadas em faixas
        if (isTrackListCategory && filteredTracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuroSpace.ScreenMargin, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(AuroColor.SurfaceGrouped)
                        .clickable {
                            if (onPlayTracksList != null) {
                                onPlayTracksList(filteredTracks, 0)
                            } else {
                                onPlayAll()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.play_all),
                            style = AuroType.Body.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(AuroColor.SurfaceGrouped)
                        .clickable {
                            if (onShuffleTracksList != null) {
                                onShuffleTracksList(filteredTracks)
                            } else {
                                onShuffleAll()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.shuffle_all),
                            style = AuroType.Body.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }

        // Ação de Nova Lista de Reprodução
        if (isPlaylists) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCreatePlaylist() }
                    .padding(horizontal = AuroSpace.ScreenMargin, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.new_playlist_button),
                    style = AuroType.Label.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        val coroutineScope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            if (isTrackListCategory) {
                if (filteredTracks.isEmpty()) {
                    if (displayedTracks.isNotEmpty() && trimmedQuery.isNotEmpty()) {
                        AuroEmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = stringResource(R.string.empty_search_title, trimmedQuery),
                            message = stringResource(R.string.empty_search_message),
                            actionLabel = stringResource(R.string.cd_clear_search),
                            onAction = { searchQuery = "" },
                            modifier = Modifier.padding(bottom = 0.dp)
                        )
                    } else {
                        AuroEmptyState(
                            icon = Icons.Rounded.MusicOff,
                            title = stringResource(R.string.empty_library_title),
                            message = stringResource(R.string.empty_library_message),
                            actionLabel = stringResource(R.string.empty_library_action),
                            onAction = onBackClick,
                            modifier = Modifier.padding(bottom = 0.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp, end = if (filteredTracks.size > 15) 28.dp else 0.dp)
                    ) {
                        itemsIndexed(filteredTracks, key = { _, t -> t.id }) { index, track ->
                            AuroTrackRow(
                                modifier = Modifier.animateItem(),
                                track = track,
                                trackIndex = index,
                                isPlayingThis = track.id == currentPlayingId,
                                hasError = errorTrackIds.contains(track.id),
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedTrackIds.contains(track.id),
                                showFormatTag = showFileFormat,
                                showPlayCount = category == LibraryCategory.MOST_PLAYED,
                                onClick = {
                                    if (isSelectionMode) {
                                        onToggleSelectTrack(track)
                                    } else if (onPlayTracksList != null) {
                                        onPlayTracksList(filteredTracks, index)
                                    } else {
                                        onTrackClick(track, index)
                                    }
                                },
                                onLongClick = { onTrackLongClick(track) },
                                onMenuClick = { onTrackMenuClick(track) }
                            )
                        }
                    }

                    if (filteredTracks.size > 15) {
                        AuroAlphabetFastScroller(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(bottom = 0.dp),
                            onLetterSelected = { letter ->
                                val targetIndex = if (letter == '#') {
                                    filteredTracks.indexOfFirst { it.title.firstOrNull()?.isLetter() == false }
                                } else {
                                    filteredTracks.indexOfFirst { matchesFirstLetter(it.title, letter) }
                                }
                                if (targetIndex >= 0) {
                                    coroutineScope.launch { listState.scrollToItem(targetIndex) }
                                }
                            }
                        )
                    }
                }
            } else {
                if (displayedGroups.isEmpty()) {
                    if (isPlaylists) {
                        AuroEmptyState(
                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                            title = stringResource(R.string.empty_playlist_title),
                            message = stringResource(R.string.empty_playlist_message),
                            actionLabel = stringResource(R.string.create_playlist),
                            onAction = onCreatePlaylist,
                            modifier = Modifier.padding(bottom = 0.dp)
                        )
                    } else {
                        AuroEmptyState(
                            icon = Icons.Rounded.FolderOff,
                            title = stringResource(R.string.empty_library_title),
                            message = stringResource(R.string.empty_library_message),
                            modifier = Modifier.padding(bottom = 0.dp)
                        )
                    }
                } else if (isAlbumsGrid) {
                    val albumPairs = remember(displayedGroups) { displayedGroups.chunked(2) }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = AuroSpace.ScreenMargin,
                            end = if (displayedGroups.size > 15) 32.dp else AuroSpace.ScreenMargin,
                            top = 8.dp,
                            bottom = LocalAuroBottomInset.current + 24.dp
                        )
                    ) {
                        items(albumPairs, key = { pair -> pair.joinToString("_") { it.title } }) { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                for (group in pair) {
                                    val coverTrack = remember(group.title) { coverTrackFinder(group) }
                                    val albumMeta = albumMetaMap[group.title.trim().lowercase()]
                                    val groupInteraction = remember { MutableInteractionSource() }
                                    val isGroupPressed by groupInteraction.collectIsPressedAsState()
                                    val groupScale by animateFloatAsState(
                                        targetValue = if (isGroupPressed) 0.98f else 1f,
                                        animationSpec = AuroMotion.press(),
                                        label = "album_grid_press_scale"
                                    )

                                    Column(
                                        modifier = Modifier
                                            .animateItem()
                                            .weight(1f)
                                            .graphicsLayer {
                                                scaleX = groupScale
                                                scaleY = groupScale
                                            }
                                            .combinedClickable(
                                                interactionSource = groupInteraction,
                                                indication = null,
                                                onClick = {
                                                    AuroHaptics.click(view)
                                                    onGroupClick(group)
                                                },
                                                onLongClick = {
                                                    AuroHaptics.longPress(view)
                                                    onGroupLongClick(group)
                                                }
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(AuroSquircle)
                                                .background(AuroColor.SurfaceCard)
                                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), AuroSquircle),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AlbumArtworkImage(
                                                albumName = group.title,
                                                customMediaPath = albumMeta?.mediaPath?.takeIf { it.isNotBlank() },
                                                isGif = albumMeta?.isGif ?: false,
                                                fallbackTrack = coverTrack,
                                                shape = AuroSquircle,
                                                mediaStamp = albumMediaVersion.toLong(),
                                                targetSizeDp = 220,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = group.title,
                                            style = AuroType.CardTitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        val artistDisplay = formatArtistsDisplay(
                                            coverTrack?.artist ?: group.subtitle,
                                            stringResource(R.string.unknown_artist)
                                        )
                                        Text(
                                            text = artistDisplay,
                                            style = AuroType.Subtitle,
                                            color = AuroColor.TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (displayedGroups.size > 15) {
                        AuroAlphabetFastScroller(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(bottom = 0.dp),
                            onLetterSelected = { letter ->
                                val targetIndex = if (letter == '#') {
                                    displayedGroups.indexOfFirst { it.title.firstOrNull()?.isLetter() == false }
                                } else {
                                    displayedGroups.indexOfFirst { matchesFirstLetter(it.title, letter) }
                                }
                                if (targetIndex >= 0) {
                                    coroutineScope.launch { listState.scrollToItem(targetIndex / 2) }
                                }
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp, end = if (displayedGroups.size > 15) 28.dp else 0.dp)
                    ) {
                        items(displayedGroups, key = { it.title }) { group ->
                            val groupInteraction = remember { MutableInteractionSource() }
                            val isGroupPressed by groupInteraction.collectIsPressedAsState()
                            val groupScale by animateFloatAsState(
                                targetValue = if (isGroupPressed) 0.98f else 1f,
                                animationSpec = AuroMotion.press(),
                                label = "group_press_scale"
                            )

                            Row(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = groupScale
                                        scaleY = groupScale
                                    }
                                    .combinedClickable(
                                        interactionSource = groupInteraction,
                                        indication = null,
                                        onClick = { onGroupClick(group) },
                                        onLongClick = { onGroupLongClick(group) }
                                    )
                                    .padding(horizontal = AuroSpace.ScreenMargin, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = group.title,
                                        style = AuroType.Body.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = group.subtitle,
                                        style = AuroType.Label.copy(color = AuroColor.TextSecondary)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = AuroColor.TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (displayedGroups.size > 15) {
                        AuroAlphabetFastScroller(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(bottom = 0.dp),
                            onLetterSelected = { letter ->
                                val targetIndex = if (letter == '#') {
                                    displayedGroups.indexOfFirst { it.title.firstOrNull()?.isLetter() == false }
                                } else {
                                    displayedGroups.indexOfFirst { matchesFirstLetter(it.title, letter) }
                                }
                                if (targetIndex >= 0) {
                                    coroutineScope.launch { listState.scrollToItem(targetIndex) }
                                }
                            }
                        )
                    }
                }
            }

            // Fade sutil de 16dp no topo das listas
            AuroTopFade(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}
