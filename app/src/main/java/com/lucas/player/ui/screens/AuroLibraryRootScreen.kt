package com.lucas.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.model.LibraryCategory
import com.lucas.player.model.getTitleRes
import com.lucas.player.ui.components.AuroBreadcrumbHeader
import com.lucas.player.ui.components.AuroBreadcrumbItem
import com.lucas.player.ui.components.AuroEmptyState
import com.lucas.player.ui.components.AuroLoadingBar
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroType
import com.lucas.player.ui.theme.LocalAuroAccent
import com.lucas.player.ui.theme.LocalAuroBottomInset
import kotlinx.coroutines.delay

/**
 * AURŌ DESIGN SYSTEM v2 - TELA RAIZ DA BIBLIOTECA (AURŌ LIBRARY ROOT)
 */
@Composable
fun AuroLibraryRootScreen(
    totalTracksCount: Int,
    isLoading: Boolean = false,
    loadingCount: Int = 0,
    onSelectCategory: (LibraryCategory) -> Unit,
    onSortClick: () -> Unit,
    onRescanClick: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    var showLoadingBar by remember { mutableStateOf(false) }
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
            LibraryCategory.ALL_SONGS to Icons.AutoMirrored.Rounded.PlaylistPlay,
            LibraryCategory.FOLDERS to Icons.Rounded.Folder,
            LibraryCategory.ALBUMS to Icons.Rounded.Album,
            LibraryCategory.ARTISTS to Icons.Rounded.Person,
            LibraryCategory.ALBUM_ARTISTS to Icons.Rounded.Group,
            LibraryCategory.GENRES to Icons.Rounded.Category,
            LibraryCategory.PLAYLISTS to Icons.Rounded.PlaylistAddCheck,
            LibraryCategory.QUEUE to Icons.AutoMirrored.Rounded.QueueMusic,
            LibraryCategory.YEARS to Icons.Rounded.DateRange,
            LibraryCategory.COMPOSERS to Icons.Rounded.Piano,
            LibraryCategory.MOST_PLAYED to Icons.Rounded.TrendingUp,
            LibraryCategory.RECENTLY_ADDED to Icons.Rounded.Schedule,
            LibraryCategory.RECENTLY_PLAYED to Icons.Rounded.History,
            LibraryCategory.TOP_RATED to Icons.Rounded.Star
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroBreadcrumbHeader(
            items = listOf(AuroBreadcrumbItem(stringResource(R.string.library_breadcrumb))),
            onItemClick = {},
            canGoBack = false,
            rightActions = {
                IconButton(onClick = onSortClick, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = stringResource(R.string.cd_sort),
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onRescanClick, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh),
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        } else {
            // Resumo
            Text(
                text = pluralStringResource(R.plurals.tracks_indexed_count, totalTracksCount, totalTracksCount),
                style = AuroType.Label.copy(color = AuroColor.TextSecondary),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
            // Linhas de categoria (sem vidro, texto e fio Hairline direto no preto)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp)
            ) {
                items(categories, key = { it.first.name }) { (cat, icon) ->
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
                                text = stringResource(cat.getTitleRes()),
                                style = AuroType.Body.copy(fontWeight = FontWeight.Medium)
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
}
