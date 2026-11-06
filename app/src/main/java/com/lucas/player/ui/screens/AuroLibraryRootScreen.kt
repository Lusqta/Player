package com.lucas.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucas.player.R
import com.lucas.player.model.LibraryCategory
import com.lucas.player.model.getTitleRes
import com.lucas.player.ui.components.AuroEmptyState
import com.lucas.player.ui.components.AuroHaptics
import com.lucas.player.ui.components.AuroLoadingBar
import com.lucas.player.ui.components.AuroTopBar
import com.lucas.player.ui.components.AuroTopFade
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroGlass
import com.lucas.player.ui.theme.AuroGlassKind
import com.lucas.player.ui.theme.AuroHeight
import com.lucas.player.ui.theme.AuroSpace
import com.lucas.player.ui.theme.AuroType
import com.lucas.player.ui.theme.LocalAuroAccent
import com.lucas.player.ui.theme.LocalAuroBottomInset
import kotlinx.coroutines.delay

/**
 * AURŌ DESIGN SYSTEM v2 - TELA RAIZ DA BIBLIOTECA (AURŌ LIBRARY ROOT)
 * 7.4: Dois atalhos de vidro (Aleatório e Continuar) e linhas de categoria de 50dp com contagens.
 */
@Composable
fun AuroLibraryRootScreen(
    totalTracksCount: Int,
    isLoading: Boolean = false,
    loadingCount: Int = 0,
    categoryCounts: Map<LibraryCategory, Int> = emptyMap(),
    onShuffleAll: () -> Unit = {},
    onResumePlayback: () -> Unit = {},
    onSelectCategory: (LibraryCategory) -> Unit,
    onSortClick: () -> Unit,
    onRescanClick: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var showLoadingBar by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(150)
            showLoadingBar = true
        } else {
            showLoadingBar = false
        }
    }

    val categories = remember {
        listOf(
            LibraryCategory.ALL_SONGS to Icons.Outlined.MusicNote,
            LibraryCategory.FOLDERS to Icons.Outlined.Folder,
            LibraryCategory.ALBUMS to Icons.Outlined.Album,
            LibraryCategory.ARTISTS to Icons.Outlined.Person,
            LibraryCategory.ALBUM_ARTISTS to Icons.Outlined.Group,
            LibraryCategory.GENRES to Icons.AutoMirrored.Outlined.Label,
            LibraryCategory.PLAYLISTS to Icons.AutoMirrored.Outlined.QueueMusic,
            LibraryCategory.QUEUE to Icons.AutoMirrored.Outlined.FormatListBulleted,
            LibraryCategory.YEARS to Icons.Outlined.CalendarToday,
            LibraryCategory.COMPOSERS to Icons.Outlined.Piano,
            LibraryCategory.MOST_PLAYED to Icons.AutoMirrored.Outlined.TrendingUp,
            LibraryCategory.RECENTLY_ADDED to Icons.Outlined.Schedule,
            LibraryCategory.RECENTLY_PLAYED to Icons.Outlined.History,
            LibraryCategory.TOP_RATED to Icons.Outlined.StarOutline
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroTopBar(
            title = stringResource(R.string.library_breadcrumb),
            subtitle = pluralStringResource(R.plurals.tracks_indexed_count, totalTracksCount, totalTracksCount),
            isRoot = true,
            rightActions = {
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
                        modifier = Modifier.background(AuroColor.Black)
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
                    }
                }
            }
        )

        // Indicador de Carregamento com debounce de 150ms
        if (showLoadingBar) {
            AuroLoadingBar()
            Text(
                text = stringResource(R.string.library_loading_count, if (loadingCount > 0) loadingCount else totalTracksCount),
                style = AuroType.Label.copy(
                    color = LocalAuroAccent.current.a.value,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.padding(horizontal = AuroSpace.ScreenMargin, vertical = 6.dp)
            )
        }

        if (totalTracksCount == 0 && !isLoading) {
            AuroEmptyState(
                icon = Icons.Rounded.MusicOff,
                title = stringResource(R.string.empty_library_title),
                message = stringResource(R.string.empty_library_message),
                actionLabel = stringResource(R.string.empty_library_action),
                onAction = onRescanClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 0.dp)
            )
        } else {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp)
                ) {
                    // Dois cards de vidro lado a lado: Aleatório e Continuar (154x64dp, raio 24)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AuroSpace.ScreenMargin, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AuroGlass(
                                kind = AuroGlassKind.Card,
                                cornerRadius = 24.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp),
                                onClick = {
                                    AuroHaptics.click(view)
                                    onShuffleAll()
                                }
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
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.shuffle_all),
                                        style = AuroType.Body.copy(fontWeight = FontWeight.Medium)
                                    )
                                }
                            }

                            AuroGlass(
                                kind = AuroGlassKind.Card,
                                cornerRadius = 24.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp),
                                onClick = {
                                    AuroHaptics.click(view)
                                    onResumePlayback()
                                }
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
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.action_resume),
                                        style = AuroType.Body.copy(fontWeight = FontWeight.Medium)
                                    )
                                }
                            }
                        }
                    }

                    // Linhas de categoria (sem vidro, texto, contagem à direita e fio Hairline direto no preto)
                    items(categories, key = { it.first.name }) { (cat, icon) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(AuroHeight.CategoryRow)
                                .clickable {
                                    AuroHaptics.click(view)
                                    onSelectCategory(cat)
                                }
                                .padding(horizontal = AuroSpace.ScreenMargin),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = AuroColor.TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(cat.getTitleRes()),
                                style = AuroType.Body.copy(fontWeight = FontWeight.Medium),
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
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 66.dp),
                            color = AuroColor.Hairline,
                            thickness = 1.dp
                        )
                    }
                }

                // Fade de 16dp no topo da lista
                AuroTopFade(modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}
