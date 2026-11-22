package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.delay

/**
 * ABA BIBLIOTECA (Apple Music Android 7.0.2)
 * - Large Title "Biblioteca" + botão circular flutuante ••• (Ordenar, Atualizar, Configurações)
 * - Carrossel de Fixados (Pins) no topo
 * - Linhas de Categoria (48dp, ícone monocromático 22dp, título 17sp, contagem tnum, chevron >, divisor inset)
 * - Grade 2 colunas de Adicionadas Recentemente logo abaixo
 */
@Composable
fun AuroLibraryRootScreen(
    totalTracksCount: Int,
    isLoading: Boolean = false,
    loadingCount: Int = 0,
    categoryCounts: Map<LibraryCategory, Int> = emptyMap(),
    pinnedGroups: Set<String> = emptySet(),
    pinnedItems: List<GroupItem> = emptyList(),
    recentlyAddedTracks: List<Track> = emptyList(),
    coverTrackFinder: (GroupItem) -> Track? = { null },
    onShuffleAll: () -> Unit = {},
    onResumePlayback: () -> Unit = {},
    onSelectCategory: (LibraryCategory) -> Unit,
    onGroupClick: (GroupItem) -> Unit = {},
    onTrackClick: (Track) -> Unit = {},
    onSortClick: () -> Unit,
    onRescanClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    selectedFolderName: String? = null,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var showMenu by remember { mutableStateOf(false) }
    val bottomInset = LocalAuroBottomInset.current

    val categories = remember {
        listOf(
            LibraryCategory.ALL_SONGS to Icons.Outlined.MusicNote,
            LibraryCategory.FOLDERS to Icons.Outlined.Folder,
            LibraryCategory.ALBUMS to Icons.Outlined.Album,
            LibraryCategory.ARTISTS to Icons.Outlined.Person,
            LibraryCategory.ALBUM_ARTISTS to Icons.Outlined.Group,
            LibraryCategory.GENRES to Icons.AutoMirrored.Outlined.Label,
            LibraryCategory.PLAYLISTS to Icons.AutoMirrored.Outlined.QueueMusic,
            LibraryCategory.YEARS to Icons.Outlined.CalendarToday,
            LibraryCategory.COMPOSERS to Icons.Outlined.Piano,
            LibraryCategory.MOST_PLAYED to Icons.AutoMirrored.Outlined.TrendingUp,
            LibraryCategory.RECENTLY_ADDED to Icons.Outlined.Schedule,
            LibraryCategory.RECENTLY_PLAYED to Icons.Outlined.History,
            LibraryCategory.TOP_RATED to Icons.Outlined.StarOutline
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        if (totalTracksCount == 0 && !isLoading) {
            AuroEmptyState(
                icon = Icons.Rounded.MusicOff,
                title = stringResource(R.string.empty_library_title),
                message = if (!selectedFolderName.isNullOrEmpty()) {
                    stringResource(R.string.empty_folder_message, selectedFolderName)
                } else {
                    stringResource(R.string.empty_library_message)
                },
                actionLabel = stringResource(R.string.empty_library_action),
                onAction = onSettingsClick,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset + 24.dp)
            ) {
                // Large Title "Biblioteca" + botão circular flutuante •••
                item(key = "library_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.library_breadcrumb),
                                style = AuroType.LargeTitle,
                                maxLines = 1
                            )
                            if (totalTracksCount > 0) {
                                val countStr = pluralStringResource(R.plurals.tracks_indexed_count, totalTracksCount, totalTracksCount)
                                val subtitleText = if (!selectedFolderName.isNullOrEmpty()) {
                                    "$countStr • $selectedFolderName"
                                } else {
                                    countStr
                                }
                                Text(
                                    text = subtitleText,
                                    style = AuroType.Caption.copy(color = AuroColor.TextSecondary)
                                )
                            }
                        }

                        Box {
                            AuroGlass(
                                kind = AuroGlassKind.Button,
                                cornerRadius = 20.dp,
                                modifier = Modifier.size(AuroHeight.GlassButton),
                                onClick = {
                                    AuroHaptics.click(view)
                                    showMenu = true
                                }
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = stringResource(R.string.cd_more_options),
                                        tint = AuroColor.TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(AuroColor.SurfaceGrouped)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cd_sort), style = AuroType.Body) },
                                    onClick = {
                                        showMenu = false
                                        onSortClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cd_refresh), style = AuroType.Body) },
                                    onClick = {
                                        showMenu = false
                                        onRescanClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_title), style = AuroType.Body) },
                                    onClick = {
                                        showMenu = false
                                        onSettingsClick()
                                    }
                                )
                            }
                        }
                    }
                }

                // Carrossel de Fixados (Pins) caso o usuário tenha fixado álbuns ou playlists
                if (pinnedItems.isNotEmpty()) {
                    item(key = "pinned_section") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.library_pins_title),
                                style = AuroType.SectionTitle,
                                modifier = Modifier.padding(horizontal = AuroSpace.ScreenMargin, vertical = 6.dp)
                            )

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = AuroSpace.ScreenMargin),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(pinnedItems, key = { it.title }) { item ->
                                    val coverBitmap = rememberTrackCover(coverTrackFinder(item), 150)

                                    Column(
                                        modifier = Modifier
                                            .width(120.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    AuroHaptics.click(view)
                                                    onGroupClick(item)
                                                }
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(120.dp)
                                                .clip(RoundedCornerShape(AuroRadius.card))
                                                .background(AuroColor.SurfaceGrouped),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (coverBitmap != null) {
                                                Image(
                                                    bitmap = coverBitmap.asImageBitmap(),
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                AuroCoverPlaceholder(
                                                    modifier = Modifier.fillMaxSize(),
                                                    glass = false,
                                                    shape = RoundedCornerShape(AuroRadius.card)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = item.title,
                                            style = AuroType.Body.copy(
                                                fontSize = 14.sp,
                                                lineHeight = 18.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.subtitle,
                                            style = AuroType.Caption,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Linhas de Categoria (48dp, ícone monocromático 22dp, título 17sp, contagem tnum, chevron >)
                items(categories, key = { it.first.name }) { (cat, icon) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AuroHaptics.click(view)
                                onSelectCategory(cat)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = AuroSpace.ScreenMargin),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = AuroColor.IconMono,
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = stringResource(cat.getTitleRes()),
                                style = AuroType.Body.copy(
                                    fontSize = 17.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = AuroColor.TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            val count = categoryCounts[cat] ?: if (cat == LibraryCategory.ALL_SONGS) totalTracksCount else null
                            if (count != null && count > 0) {
                                Text(
                                    text = count.toString(),
                                    style = AuroType.Caption.copy(
                                        color = AuroColor.TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontFeatureSettings = "tnum"
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = AuroColor.TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Grade 2 colunas: Adicionadas Recentemente
                if (recentlyAddedTracks.isNotEmpty()) {
                    item(key = "recently_added_header") {
                        Text(
                            text = stringResource(R.string.home_recently_added),
                            style = AuroType.SectionTitle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = AuroSpace.ScreenMargin,
                                    end = AuroSpace.ScreenMargin,
                                    top = 24.dp,
                                    bottom = 12.dp
                                )
                        )
                    }

                    val pairs = recentlyAddedTracks.take(10).chunked(2)
                    items(pairs, key = { "${it.first().id}_${it.getOrNull(1)?.id}" }) { rowPair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AuroSpace.ScreenMargin, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            for (track in rowPair) {
                                val coverBitmap = rememberTrackCover(track, 200)

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                AuroHaptics.click(view)
                                                onTrackClick(track)
                                            }
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(AuroRadius.card))
                                            .background(AuroColor.SurfaceGrouped),
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
                                            AuroCoverPlaceholder(
                                                modifier = Modifier.fillMaxSize(),
                                                glass = false,
                                                shape = RoundedCornerShape(AuroRadius.card)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = track.title,
                                        style = AuroType.Body.copy(
                                            fontSize = 14.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                                        style = AuroType.Subtitle.copy(
                                            fontSize = 13.sp,
                                            lineHeight = 16.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (rowPair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
