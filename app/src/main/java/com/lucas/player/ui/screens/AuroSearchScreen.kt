package com.lucas.player.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.lucas.player.model.formatArtistsDisplay
import com.lucas.player.model.splitArtists
import com.lucas.player.ui.components.AuroEmptyState
import com.lucas.player.ui.components.AuroHaptics
import com.lucas.player.ui.components.AuroTopFade
import com.lucas.player.ui.components.AuroTrackRow
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroGlass
import com.lucas.player.ui.theme.AuroGlassKind
import com.lucas.player.ui.theme.AuroHeight
import com.lucas.player.ui.theme.AuroSpace
import com.lucas.player.ui.theme.AuroType
import com.lucas.player.ui.theme.LiquidGlassSurface
import com.lucas.player.ui.theme.LocalAuroBottomInset
import kotlinx.coroutines.delay
import java.util.Locale

enum class SearchFilter {
    ALL, TRACKS, ALBUMS, ARTISTS
}

/**
 * AURŌ DESIGN SYSTEM v2 - TELA DE BUSCA (AURŌ SEARCH - FASE 7.4)
 * - Campo: BasicTextField dentro de AuroGlass(Card) de 52dp (raio 26), sem TextField do Material.
 *   Ícone de lupa 24dp, dica em TextTertiary, X de limpar com toque 48dp, cursor e alças em accent A.
 *   Em foco, o reflexo sobe x1.4 (uSpec) com mola.
 * - Chips (32dp, contorno 1dp branco 18%. Selecionado: fundo 12%, contorno accent 70%, texto branco).
 * - Cabeçalho de seção: Caption em maiúsculas, TextTertiary, +1sp.
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

    val matchedTracks = remember(trimmed, allTracks) {
        if (trimmed.isEmpty()) emptyList()
        else allTracks.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
            it.artist.contains(trimmed, ignoreCase = true) ||
            it.album.contains(trimmed, ignoreCase = true)
        }
    }

    val matchedAlbums = remember(trimmed, allTracks) {
        if (trimmed.isEmpty()) emptyList()
        else {
            allTracks
                .filter { it.album.contains(trimmed, ignoreCase = true) }
                .groupBy { it.album }
                .map { (albumName, tracks) ->
                    val firstTrack = tracks.first()
                    Album(
                        name = albumName,
                        artist = firstTrack.artist,
                        year = firstTrack.year,
                        trackCount = tracks.size
                    )
                }
        }
    }

    val matchedArtists = remember(trimmed, allTracks) {
        if (trimmed.isEmpty()) emptyList()
        else {
            val map = mutableMapOf<String, Pair<String, MutableSet<Long>>>()
            for (track in allTracks) {
                val artists = splitArtists(track.artist)
                for (artist in artists) {
                    if (artist.contains(trimmed, ignoreCase = true)) {
                        val key = artist.lowercase().trim()
                        val entry = map.getOrPut(key) { artist.trim() to mutableSetOf() }
                        entry.second.add(track.id)
                    }
                }
            }
            map.values
                .map { (displayName, trackIds) ->
                    Artist(
                        name = displayName,
                        trackCount = trackIds.size
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
    }

    val hasAnyResults = matchedTracks.isNotEmpty() || matchedAlbums.isNotEmpty() || matchedArtists.isNotEmpty()
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
            .statusBarsPadding()
            .imePadding()
            .padding(top = 12.dp)
    ) {
        // Large Title estilo Apple Music
        Text(
            text = stringResource(R.string.tab_search),
            style = AuroType.LargeTitle,
            color = AuroColor.TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuroSpace.ScreenMargin, vertical = 6.dp)
        )

        // Campo de busca em pílula (48dp, CircleShape)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = AuroSpace.ScreenMargin)
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
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = Color.White,
                        backgroundColor = Color.White.copy(alpha = 0.25f)
                    )
                ) {
                    BasicTextField(
                        value = queryInput,
                        onValueChange = { queryInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = AuroType.Body.copy(color = AuroColor.TextPrimary),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
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
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (queryInput.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_placeholder),
                                        style = AuroType.Body.copy(color = AuroColor.TextTertiary)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                if (queryInput.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            queryInput = ""
                            debouncedQuery = ""
                            onQueryChange("")
                        },
                        modifier = Modifier.size(AuroHeight.TouchTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_clear_search),
                            tint = AuroColor.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Chips de Filtro (32dp, 1dp contorno branco 18%. Selecionado: fundo 12%, contorno accent 70%, texto branco)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AuroSpace.ScreenMargin, vertical = 6.dp),
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
                        .padding(horizontal = AuroSpace.ScreenMargin, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_recent_title),
                        style = AuroType.Subtitle.copy(
                            color = AuroColor.TextTertiary,
                            fontWeight = FontWeight.SemiBold
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

                Box(modifier = Modifier.weight(1f)) {
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
                                    .padding(horizontal = AuroSpace.ScreenMargin, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = AuroColor.TextTertiary,
                                    modifier = Modifier.size(20.dp)
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
                                    modifier = Modifier.size(AuroHeight.TouchTarget)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = AuroColor.TextTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    AuroTopFade(modifier = Modifier.align(Alignment.TopCenter))
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
            Box(modifier = Modifier.weight(1f)) {
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
                            }
                        }
                    }
                }
                AuroTopFade(modifier = Modifier.align(Alignment.TopCenter))
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
    val view = LocalView.current
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = AuroHeight.TouchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                AuroHaptics.tick(view)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(AuroHeight.Chip)
                .clip(shape)
                .background(if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (isSelected) Color.White.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.18f),
                    shape = shape
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = AuroType.Label.copy(
                    color = if (isSelected) AuroColor.TextPrimary else AuroColor.TextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            )
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    canViewAll: Boolean = false,
    onViewAll: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = AuroType.Subtitle.copy(
                color = AuroColor.TextTertiary,
                fontWeight = FontWeight.SemiBold
            )
        )
        if (canViewAll) {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .clip(CircleShape)
                    .background(AuroColor.PillMuted)
                    .clickable(onClick = onViewAll)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_view_all),
                    style = AuroType.MicroLabel.copy(
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                )
            }
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
            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Album,
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
            val artistDisplay = formatArtistsDisplay(album.artist, stringResource(R.string.unknown_artist))
            Text(
                text = "$artistDisplay • ${pluralStringResource(R.plurals.tracks_count, album.trackCount, album.trackCount)}",
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
            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
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
