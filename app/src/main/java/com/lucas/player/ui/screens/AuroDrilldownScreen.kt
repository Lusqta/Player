package com.lucas.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.model.LibraryCategory
import com.lucas.player.model.Track
import com.lucas.player.model.getTitleRes
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroType
import com.lucas.player.ui.theme.AuroGlass
import com.lucas.player.ui.theme.AuroGlassKind
import com.lucas.player.ui.theme.AuroSpace
import com.lucas.player.ui.theme.LocalAuroBottomInset
import kotlinx.coroutines.launch

/**
 * AURŌ DESIGN SYSTEM v2 - TELA DE DRILLDOWN (AURŌ DRILLDOWN)
 */
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
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    errorTrackIds: Set<Long> = emptySet(),
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedTrackIds: Set<Long> = emptySet(),
    onToggleSelectTrack: (Track) -> Unit = {},
    showFileFormat: Boolean = true
) {
    val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroTopBar(
            title = groupTitle,
            breadcrumb = stringResource(category.getTitleRes()),
            subtitle = "${pluralStringResource(R.plurals.tracks_count, tracks.size, tracks.size)} • ${formatTimeMs(totalDurationMs)}",
            canGoBack = true,
            onBackClick = onBackToCategoryList
        )

        // Banner de Ações Rápidas (Tocar tudo / Aleatório)
        if (tracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuroSpace.ScreenMargin, vertical = 8.dp),
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

        val coroutineScope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            if (tracks.isEmpty()) {
                AuroEmptyState(
                    icon = Icons.Rounded.MusicOff,
                    title = stringResource(R.string.empty_library_title),
                    message = stringResource(R.string.empty_library_message),
                    actionLabel = stringResource(R.string.empty_queue_action),
                    onAction = onBackToCategoryList,
                    modifier = Modifier.padding(bottom = 0.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp, end = if (tracks.size > 15) 28.dp else 0.dp)
                ) {
                    itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
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

                if (tracks.size > 15) {
                    AuroAlphabetFastScroller(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(bottom = 0.dp),
                        onLetterSelected = { letter ->
                            val targetIndex = if (letter == '#') {
                                tracks.indexOfFirst { it.title.firstOrNull()?.isLetter() == false }
                            } else {
                                tracks.indexOfFirst { matchesFirstLetter(it.title, letter) }
                            }
                            if (targetIndex >= 0) {
                                coroutineScope.launch { listState.scrollToItem(targetIndex) }
                            }
                        }
                    )
                }

                // Fade de 16dp no topo da lista
                AuroTopFade(modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}
