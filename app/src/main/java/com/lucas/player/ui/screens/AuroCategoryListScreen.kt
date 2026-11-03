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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
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
import com.lucas.player.ui.GroupItem
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroGlass
import com.lucas.player.ui.theme.AuroGlassKind
import com.lucas.player.ui.theme.LocalAuroAccent
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
                if (displayedTracks.isEmpty()) {
                    AuroEmptyState(
                        icon = Icons.Rounded.MusicOff,
                        title = stringResource(R.string.empty_library_title),
                        message = stringResource(R.string.empty_library_message),
                        actionLabel = stringResource(R.string.empty_library_action),
                        onAction = onBackClick,
                        modifier = Modifier.padding(bottom = 120.dp)
                    )
                } else {
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
                            modifier = Modifier.padding(bottom = 120.dp)
                        )
                    } else {
                        AuroEmptyState(
                            icon = Icons.Rounded.FolderOff,
                            title = stringResource(R.string.empty_library_title),
                            message = stringResource(R.string.empty_library_message),
                            modifier = Modifier.padding(bottom = 120.dp)
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
}
