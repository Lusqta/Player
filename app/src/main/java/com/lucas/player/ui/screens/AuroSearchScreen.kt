package com.lucas.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.data.PreferencesManager
import com.lucas.player.model.Album
import com.lucas.player.model.Artist
import com.lucas.player.model.Track
import com.lucas.player.ui.components.AuroEmptyState
import com.lucas.player.ui.components.AuroHaptics
import com.lucas.player.ui.components.AuroTrackRow
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroType
import com.lucas.player.ui.theme.AuroGlass
import com.lucas.player.ui.theme.AuroGlassKind
import com.lucas.player.ui.theme.LocalAuroAccent
import com.lucas.player.ui.theme.LocalAuroBottomInset
import kotlinx.coroutines.delay

enum class SearchFilter {
    ALL, TRACKS, ALBUMS, ARTISTS
}

/**
 * AURŌ DESIGN SYSTEM v2 - TELA DE BUSCA (AURŌ SEARCH - FASE 3.3)
 */
@Composable
fun AuroSearchScreen(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    allTracks: List<Track>,
    currentPlayingId: Long,
    preferencesManager: PreferencesManager,
    onTrackClick: (Track, Int) -> Unit,
    onTrackMenuClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit = {},
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onClose: () -> Unit,
    errorTrackIds: Set<Long> = emptySet(),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    var queryInput by remember { mutableStateOf(searchQuery) }
    var debouncedQuery by remember { mutableStateOf(searchQuery) }
    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }
    val recentSearches = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        recentSearches.clear()
        recentSearches.addAll(preferencesManager.getRecentSearches())
    }

    // Debounce de 250ms ao digitar
    LaunchedEffect(queryInput) {
        delay(250)
        debouncedQuery = queryInput
        onQueryChange(queryInput)
    }

    // Auto-ocultar teclado ao rolar
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            keyboardController?.hide()
        }
    }

    val trimmed = debouncedQuery.trim()
    val hasQuery = trimmed.isNotEmpty()

    // Resultados filtrados
    val matchedTracks = remember(trimmed, allTracks) {
        if (!hasQuery) emptyList()
        else allTracks.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
            it.artist.contains(trimmed, ignoreCase = true) ||
            it.album.contains(trimmed, ignoreCase = true)
        }
    }

    val matchedAlbums = remember(trimmed, allTracks) {
        if (!hasQuery) emptyList()
        else allTracks.filter { it.album.contains(trimmed, ignoreCase = true) }
            .groupBy { it.album }
            .map { (albumName, tList) ->
                Album(
                    name = albumName,
                    artist = tList.firstOrNull()?.artist ?: "",
                    trackCount = tList.size,
                    year = tList.map { it.year }.filter { it > 0 }.minOrNull() ?: 0
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    val matchedArtists = remember(trimmed, allTracks) {
        if (!hasQuery) emptyList()
        else allTracks.filter { it.artist.contains(trimmed, ignoreCase = true) || it.composer.contains(trimmed, ignoreCase = true) }
            .groupBy { it.artist }
            .map { (artistName, tList) ->
                Artist(
                    name = artistName,
                    trackCount = tList.size
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    val hasAnyResults = matchedTracks.isNotEmpty() || matchedAlbums.isNotEmpty() || matchedArtists.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
            .statusBarsPadding()
            .imePadding()
            .padding(top = 12.dp)
    ) {
        // Barra de Busca
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
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
                        value = queryInput,
                        onValueChange = { queryInput = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                style = AuroType.Body.copy(color = AuroColor.TextTertiary)
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                if (queryInput.isNotBlank()) {
                                    preferencesManager.addRecentSearch(queryInput)
                                    recentSearches.clear()
                                    recentSearches.addAll(preferencesManager.getRecentSearches())
                                }
                            }
                        ),
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
                    if (queryInput.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                queryInput = ""
                                debouncedQuery = ""
                                onQueryChange("")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_clear_search),
                                tint = AuroColor.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Chips de Filtro (sem vidro: borda Hairline no preto puro)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchFilterChip(
                label = stringResource(R.string.search_filter_all),
                isSelected = selectedFilter == SearchFilter.ALL,
                onClick = { selectedFilter = SearchFilter.ALL }
            )
            SearchFilterChip(
                label = stringResource(R.string.search_filter_tracks),
                isSelected = selectedFilter == SearchFilter.TRACKS,
                onClick = { selectedFilter = SearchFilter.TRACKS }
            )
            SearchFilterChip(
                label = stringResource(R.string.search_filter_albums),
                isSelected = selectedFilter == SearchFilter.ALBUMS,
                onClick = { selectedFilter = SearchFilter.ALBUMS }
            )
            SearchFilterChip(
                label = stringResource(R.string.search_filter_artists),
                isSelected = selectedFilter == SearchFilter.ARTISTS,
                onClick = { selectedFilter = SearchFilter.ARTISTS }
            )
        }

        // Conteúdo: Buscas Recentes OU Resultados
        if (!hasQuery) {
            // Buscas Recentes
            if (recentSearches.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_recent_title),
                        style = AuroType.SectionHeader.copy(
                            color = AuroColor.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Text(
                        text = stringResource(R.string.search_recent_clear),
                        style = AuroType.Label.copy(color = AuroColor.TextTertiary),
                        modifier = Modifier
                            .clickable {
                                preferencesManager.clearRecentSearches()
                                recentSearches.clear()
                            }
                            .padding(4.dp)
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp)
                ) {
                    itemsIndexed(recentSearches, key = { _, item -> item }) { _, queryItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    queryInput = queryItem
                                    debouncedQuery = queryItem
                                    keyboardController?.hide()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = AuroColor.TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = queryItem,
                                style = AuroType.Body,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    preferencesManager.removeRecentSearch(queryItem)
                                    recentSearches.remove(queryItem)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = AuroColor.TextTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                    }
                }
            }
        } else if (!hasAnyResults) {
            // Estado Vazio de Busca
            AuroEmptyState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.empty_search_title, trimmed),
                message = stringResource(R.string.empty_search_message),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 0.dp)
            )
        } else {
            // Lista de Resultados
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp)
            ) {
                when (selectedFilter) {
                    SearchFilter.ALL -> {
                        // Grupo: Faixas (máximo 5 + Ver todos)
                        if (matchedTracks.isNotEmpty()) {
                            item {
                                SearchSectionHeader(
                                    title = stringResource(R.string.search_section_tracks),
                                    canViewAll = matchedTracks.size > 5,
                                    onViewAll = { selectedFilter = SearchFilter.TRACKS }
                                )
                            }
                            itemsIndexed(matchedTracks.take(5), key = { _, t -> "all_t_${t.id}" }) { index, track ->
                                AuroTrackRow(
                                    track = track,
                                    trackIndex = index,
                                    isPlayingThis = track.id == currentPlayingId,
                                    hasError = errorTrackIds.contains(track.id),
                                    onClick = {
                                        preferencesManager.addRecentSearch(trimmed)
                                        keyboardController?.hide()
                                        onTrackClick(track, index)
                                    },
                                    onLongClick = { onTrackLongClick(track) },
                                    onMenuClick = { onTrackMenuClick(track) }
                                )
                                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                            }
                        }

                        // Grupo: Álbuns (máximo 5 + Ver todos)
                        if (matchedAlbums.isNotEmpty()) {
                            item {
                                SearchSectionHeader(
                                    title = stringResource(R.string.search_section_albums),
                                    canViewAll = matchedAlbums.size > 5,
                                    onViewAll = { selectedFilter = SearchFilter.ALBUMS }
                                )
                            }
                            itemsIndexed(matchedAlbums.take(5), key = { _, a -> "all_a_${a.name}" }) { _, album ->
                                SearchAlbumRow(
                                    album = album,
                                    onClick = {
                                        preferencesManager.addRecentSearch(trimmed)
                                        keyboardController?.hide()
                                        onAlbumClick(album.name)
                                    }
                                )
                                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                            }
                        }

                        // Grupo: Artistas (máximo 5 + Ver todos)
                        if (matchedArtists.isNotEmpty()) {
                            item {
                                SearchSectionHeader(
                                    title = stringResource(R.string.search_section_artists),
                                    canViewAll = matchedArtists.size > 5,
                                    onViewAll = { selectedFilter = SearchFilter.ARTISTS }
                                )
                            }
                            itemsIndexed(matchedArtists.take(5), key = { _, ar -> "all_ar_${ar.name}" }) { _, artist ->
                                SearchArtistRow(
                                    artist = artist,
                                    onClick = {
                                        preferencesManager.addRecentSearch(trimmed)
                                        keyboardController?.hide()
                                        onArtistClick(artist.name)
                                    }
                                )
                                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                            }
                        }
                    }

                    SearchFilter.TRACKS -> {
                        itemsIndexed(matchedTracks, key = { _, t -> "tracks_${t.id}" }) { index, track ->
                            AuroTrackRow(
                                track = track,
                                trackIndex = index,
                                isPlayingThis = track.id == currentPlayingId,
                                hasError = errorTrackIds.contains(track.id),
                                onClick = {
                                    preferencesManager.addRecentSearch(trimmed)
                                    keyboardController?.hide()
                                    onTrackClick(track, index)
                                },
                                onLongClick = { onTrackLongClick(track) },
                                onMenuClick = { onTrackMenuClick(track) }
                            )
                            HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                        }
                    }

                    SearchFilter.ALBUMS -> {
                        itemsIndexed(matchedAlbums, key = { _, a -> "albums_${a.name}" }) { _, album ->
                            SearchAlbumRow(
                                album = album,
                                onClick = {
                                    preferencesManager.addRecentSearch(trimmed)
                                    keyboardController?.hide()
                                    onAlbumClick(album.name)
                                }
                            )
                            HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                        }
                    }

                    SearchFilter.ARTISTS -> {
                        itemsIndexed(matchedArtists, key = { _, ar -> "artists_${ar.name}" }) { _, artist ->
                            SearchArtistRow(
                                artist = artist,
                                onClick = {
                                    preferencesManager.addRecentSearch(trimmed)
                                    keyboardController?.hide()
                                    onArtistClick(artist.name)
                                }
                            )
                            HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalAuroAccent.current.a.value
    val view = LocalView.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = if (isSelected) accent else AuroColor.Hairline,
                shape = RoundedCornerShape(16.dp)
            )
            .background(if (isSelected) accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable {
                AuroHaptics.click(view)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = AuroType.Label.copy(
                color = if (isSelected) accent else AuroColor.TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    canViewAll: Boolean,
    onViewAll: () -> Unit
) {
    val accent = LocalAuroAccent.current.a.value
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = AuroType.SectionHeader.copy(
                color = AuroColor.TextSecondary,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        )
        if (canViewAll) {
            Text(
                text = stringResource(R.string.search_view_all),
                style = AuroType.Label.copy(color = accent, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clickable(onClick = onViewAll)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun SearchAlbumRow(
    album: Album,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Album,
            contentDescription = null,
            tint = AuroColor.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = AuroType.Body.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${album.artist} • ${pluralStringResource(R.plurals.tracks_count, album.trackCount, album.trackCount)}",
                style = AuroType.Caption.copy(color = AuroColor.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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

@Composable
private fun SearchArtistRow(
    artist: Artist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            tint = AuroColor.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = AuroType.Body.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = pluralStringResource(R.plurals.tracks_count, artist.trackCount, artist.trackCount),
                style = AuroType.Caption.copy(color = AuroColor.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
