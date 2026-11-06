package com.lucas.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.model.LibraryCategory
import com.lucas.player.model.Track
import com.lucas.player.model.getTitleRes
import com.lucas.player.ui.GroupItem
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroType
import com.lucas.player.ui.theme.AuroGlass
import com.lucas.player.ui.theme.AuroGlassKind
import com.lucas.player.ui.theme.AuroSpace
import com.lucas.player.ui.theme.LocalAuroAccent
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
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    onSortClick: () -> Unit,
    errorTrackIds: Set<Long> = emptySet(),
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedTrackIds: Set<Long> = emptySet(),
    onToggleSelectTrack: (Track) -> Unit = {},
    showFileFormat: Boolean = true
) {
    val isTrackListCategory = category == LibraryCategory.ALL_SONGS ||
            category == LibraryCategory.MOST_PLAYED ||
            category == LibraryCategory.RECENTLY_ADDED ||
            category == LibraryCategory.RECENTLY_PLAYED ||
            category == LibraryCategory.TOP_RATED
    val isPlaylists = category == LibraryCategory.PLAYLISTS

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroTopBar(
            title = stringResource(category.getTitleRes()),
            subtitle = if (isTrackListCategory) "${displayedTracks.size} faixas" else "${displayedGroups.size} itens",
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

        // Banner de Ações Rápidas em categorias baseadas em faixas
        if (isTrackListCategory && displayedTracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuroSpace.ScreenMargin, vertical = 8.dp),
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
                            text = stringResource(R.string.play_all),
                            style = AuroType.Label
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
                            text = stringResource(R.string.shuffle_all),
                            style = AuroType.Label
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
                    .padding(horizontal = AuroSpace.ScreenMargin, vertical = 12.dp),
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
                    text = stringResource(R.string.new_playlist_button),
                    style = AuroType.Label.copy(
                        color = LocalAuroAccent.current.a.value,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
        }

        val coroutineScope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            if (isTrackListCategory) {
                if (displayedTracks.isEmpty()) {
                    AuroEmptyState(
                        icon = Icons.Rounded.MusicOff,
                        title = stringResource(R.string.empty_library_title),
                        message = stringResource(R.string.empty_library_message),
                        actionLabel = stringResource(R.string.empty_library_action),
                        onAction = onBackClick,
                        modifier = Modifier.padding(bottom = 0.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp, end = if (displayedTracks.size > 15) 28.dp else 0.dp)
                    ) {
                        itemsIndexed(displayedTracks, key = { _, t -> t.id }) { index, track ->
                            AuroTrackRow(
                                track = track,
                                trackIndex = index,
                                isPlayingThis = track.id == currentPlayingId,
                                hasError = errorTrackIds.contains(track.id),
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedTrackIds.contains(track.id),
                                showFormatTag = showFileFormat,
                                onClick = {
                                    if (isSelectionMode) {
                                        onToggleSelectTrack(track)
                                    } else {
                                        onTrackClick(track, index)
                                    }
                                },
                                onLongClick = { onTrackLongClick(track) },
                                onMenuClick = { onTrackMenuClick(track) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 80.dp),
                                color = AuroColor.Hairline,
                                thickness = 1.dp
                            )
                        }
                    }

                    if (displayedTracks.size > 15) {
                        AuroAlphabetFastScroller(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(bottom = 0.dp),
                            onLetterSelected = { letter ->
                                val targetIndex = if (letter == '#') {
                                    displayedTracks.indexOfFirst { it.title.firstOrNull()?.isLetter() == false }
                                } else {
                                    displayedTracks.indexOfFirst { matchesFirstLetter(it.title, letter) }
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
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp, end = if (displayedGroups.size > 15) 28.dp else 0.dp)
                    ) {
                        items(displayedGroups, key = { it.title }) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
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
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 66.dp),
                                color = AuroColor.Hairline,
                                thickness = 1.dp
                            )
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
