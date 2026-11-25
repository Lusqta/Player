package com.lucas.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.data.LibraryRepository
import com.lucas.player.model.SortMode
import com.lucas.player.model.Track
import com.lucas.player.ui.components.AuroEmptyState
import com.lucas.player.ui.components.AuroHaptics
import com.lucas.player.ui.components.AuroTopBar
import com.lucas.player.ui.components.AuroTopFade
import com.lucas.player.ui.components.AuroTrackRow
import com.lucas.player.ui.components.formatTracksCount
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroFontFamily
import com.lucas.player.ui.theme.AuroSpace
import com.lucas.player.ui.theme.AuroType
import com.lucas.player.ui.theme.LocalAuroBottomInset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class YearsChipsState(
    val chips: List<Int> = emptyList(),
    val loaded: Boolean = false
)

/**
 * TELA DE ANOS COM CARROSSEL NO TOPO (Editorial Dark OLED)
 * - Top Bar: "Anos" + contagem de faixas do ano selecionado + Voltar + Ordenar
 * - Carrossel horizontal com chips de anos em ordem decrescente e "Sem ano" ao final se aplicável
 * - Botões "Tocar Todas" e "Aleatório" para as faixas do ano selecionado
 * - Lista direta de faixas com AuroTrackRow
 */
@Composable
fun AuroYearsScreen(
    currentPlayingId: Long,
    sortMode: SortMode = SortMode.TITLE_ASC,
    dataVersion: Int = 0,
    onBackClick: () -> Unit,
    onSortClick: () -> Unit,
    onTrackClick: (List<Track>, Int) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onTrackMenuClick: (Track) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    errorTrackIds: Set<Long> = emptySet(),
    isSelectionMode: Boolean = false,
    selectedTrackIds: Set<Long> = emptySet(),
    onToggleSelectTrack: (Track) -> Unit = {},
    showFileFormat: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    libraryRepository: LibraryRepository = LibraryRepository.getInstance(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    val chipsState by produceState(
        initialValue = YearsChipsState(),
        key1 = dataVersion
    ) {
        value = withContext(Dispatchers.IO) {
            val knownYears = libraryRepository.getYears().map { it.year }
            val hasUnknownYear = libraryRepository.getTracksForYear(0).isNotEmpty()
            YearsChipsState(
                chips = if (hasUnknownYear) knownYears + 0 else knownYears,
                loaded = true
            )
        }
    }
    val yearChips = chipsState.chips

    var userSelectedYear by remember { mutableStateOf<Int?>(null) }
    val selectedYear = remember(userSelectedYear, yearChips) {
        val current = userSelectedYear
        if (current != null && yearChips.contains(current)) current
        else yearChips.firstOrNull() ?: 0
    }

    val yearTracks by produceState<List<Track>>(
        emptyList(),
        selectedYear,
        sortMode,
        dataVersion,
        yearChips
    ) {
        value = withContext(Dispatchers.IO) {
            if (yearChips.isEmpty()) emptyList()
            else libraryRepository.getTracksForYear(selectedYear, sortMode)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        AuroTopBar(
            title = stringResource(R.string.category_years),
            subtitle = formatTracksCount(yearTracks.size),
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

        if (chipsState.loaded && yearChips.isEmpty()) {
            AuroEmptyState(
                icon = Icons.Rounded.CalendarToday,
                title = stringResource(R.string.empty_library_title),
                message = stringResource(R.string.empty_library_message),
                actionLabel = stringResource(R.string.cd_back),
                onAction = onBackClick,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Carrossel de Anos (pílulas)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp),
                contentPadding = PaddingValues(horizontal = AuroSpace.ScreenMargin),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(yearChips, key = { it }) { year ->
                    val isSelected = year == selectedYear
                    val label = if (year > 0) year.toString() else stringResource(R.string.year_unknown)
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White else AuroColor.SurfaceCard)
                            .then(
                                if (!isSelected) {
                                    Modifier.border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                                } else Modifier
                            )
                            .clickable {
                                AuroHaptics.click(view)
                                userSelectedYear = year
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = AuroType.Body.copy(
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontFamily = if (year > 0) FontFamily.Monospace else AuroFontFamily,
                                fontFeatureSettings = "tnum",
                                color = if (isSelected) Color.Black else AuroColor.TextSecondary
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            // Botões de ação (Tocar tudo e Aleatório)
            if (yearTracks.isNotEmpty()) {
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
                                AuroHaptics.click(view)
                                onPlayAll(yearTracks)
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
                                AuroHaptics.click(view)
                                onShuffleAll(yearTracks)
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

            // Lista de faixas do ano selecionado
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp)
                ) {
                    itemsIndexed(yearTracks, key = { _, t -> t.id }) { index, track ->
                        AuroTrackRow(
                            modifier = Modifier.animateItem(),
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
                                    onTrackClick(yearTracks, index)
                                }
                            },
                            onLongClick = { onTrackLongClick(track) },
                            onMenuClick = { onTrackMenuClick(track) }
                        )
                    }
                }

                AuroTopFade(modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}
