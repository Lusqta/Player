package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.data.CoverArtLoader
import com.lucas.player.data.LibraryRepository
import com.lucas.player.model.*
import com.lucas.player.model.formatArtistsDisplay
import com.lucas.player.model.splitArtists
import com.lucas.player.model.isValidHighlightName
import com.lucas.player.model.isJunkAudio
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.launch

/**
 * TELA PRINCIPAL DE ARTISTA (Editorial Dark OLED - Estilo Streaming)
 * - Fundo Bleed Atmosférico de 520dp via CoverGlow (da mídia salva ou fallbackTrack)
 * - Top Bar transparente com Glass Actions (Voltar, Fixar, Mais Opções -> Alterar / Remover foto)
 * - Hero Avatar 160×160dp com TranslucentPlayButton flutuante de 56dp
 * - Seção 1: Principais músicas (Top 5) clicável com atalho Aleatório
 * - Seção 2: Álbuns / Discografia com carrossel horizontal de até 3 lançamentos
 */
@Composable
fun AuroArtistScreen(
    artistName: String,
    onBack: () -> Unit,
    onNavigateToTracks: () -> Unit,
    onNavigateToDiscography: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onTrackMenuClick: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    currentPlayingId: Long,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onChangeMedia: () -> Unit = {},
    onRemoveMedia: () -> Unit = {},
    customMediaPath: String? = null,
    isGif: Boolean = false,
    artistMediaVersion: Int = 0,
    dataVersion: Int = 0,
    errorTrackIds: Set<Long> = emptySet(),
    showFileFormat: Boolean = true,
    libraryRepository: LibraryRepository = LibraryRepository.getInstance(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val bottomInset = LocalAuroBottomInset.current
    val listState = rememberLazyListState()

    val mediaStamp = remember(customMediaPath, artistMediaVersion) {
        customMediaPath?.let { java.io.File(it).takeIf(java.io.File::exists)?.lastModified() } ?: 0L
    }

    val top5Tracks = remember(artistName, dataVersion) { libraryRepository.getTop5TracksForArtist(artistName) }
    val releases = remember(artistName, dataVersion) { libraryRepository.getArtistReleases(artistName) }
    val carouselReleases = remember(artistName, dataVersion) { libraryRepository.getTop3CarouselReleases(artistName) }
    val primaryTracks = remember(artistName, dataVersion) { libraryRepository.getPrimaryTracksForArtist(artistName) }
    val featTracks = remember(artistName, dataVersion) { libraryRepository.getFeatTracksForArtist(artistName) }
    val fallbackTrack = remember(artistName, dataVersion) { libraryRepository.resolveArtistFallbackCoverTrack(artistName) }

    val fallbackCover = rememberTrackCover(fallbackTrack, 400)
    var customBitmap by remember(customMediaPath, mediaStamp) {
        mutableStateOf(CoverArtLoader.getCachedArtistStaticBitmap(customMediaPath))
    }
    LaunchedEffect(customMediaPath, mediaStamp) {
        if (customMediaPath.isNullOrBlank()) {
            customBitmap = null
        } else {
            CoverArtLoader.loadArtistStaticBitmap(customMediaPath, 400) { bmp ->
                customBitmap = bmp
            }
        }
    }

    var artistAccent by remember { mutableStateOf(AuroAccent.Neutral) }
    LaunchedEffect(customBitmap, fallbackCover) {
        artistAccent = CoverGlow.from(customBitmap ?: fallbackCover)
    }

    val totalTracksCount = primaryTracks.size + featTracks.size
    val albumCount = releases.count { it.isAlbum }
    val singleCount = releases.count { !it.isAlbum }
    val subtitleText = buildString {
        append(pluralStringResource(R.plurals.tracks_count, totalTracksCount, totalTracksCount))
        if (albumCount > 0) {
            append(" • ")
            append(pluralStringResource(R.plurals.albums_count, albumCount, albumCount))
        } else if (singleCount > 0) {
            append(" • ")
            append(pluralStringResource(R.plurals.singles_count, singleCount, singleCount))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        // Fundo Bleed Atmosférico (520dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
        ) {
            AnimatedDualColorBackground(
                color1 = artistAccent.mesh1,
                color2 = artistAccent.mesh2,
                isPlaying = false,
                modifier = Modifier.fillMaxSize()
            )

            // Gradiente vertical para dissolver suavemente no preto absoluto OLED
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                AuroColor.Canvas.copy(alpha = 0.78f),
                                AuroColor.Canvas
                            )
                        )
                    )
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 80.dp)
        ) {
            // Hero Avatar e Identificação
            item(key = "artist_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 56.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ArtistAvatarImage(
                            artistName = artistName,
                            customMediaPath = customMediaPath,
                            isGif = isGif,
                            fallbackTrack = fallbackTrack,
                            shape = AuroSquircle,
                            mediaStamp = mediaStamp,
                            modifier = Modifier.size(160.dp)
                        )

                        TranslucentPlayButton(
                            isPlaying = false,
                            size = 56.dp,
                            containerColor = Color(0xBD121215),
                            onClick = {
                                AuroHaptics.click(view)
                                onPlayAll()
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 6.dp, y = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = formatArtistsDisplay(artistName, artistName),
                        style = AuroType.HeroName.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                        color = AuroColor.TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = AuroSpace.ScreenMargin)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = subtitleText,
                        style = AuroType.Caption,
                        color = AuroColor.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = AuroSpace.ScreenMargin)
                    )
                }
            }

            // Seção 1: Principais músicas (Top 5)
            if (top5Tracks.isNotEmpty()) {
                item(key = "top_tracks_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(AuroSquircle)
                                .clickable {
                                    AuroHaptics.click(view)
                                    onNavigateToTracks()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.artist_top_tracks),
                                style = AuroType.SectionTitle,
                                color = AuroColor.TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = AuroColor.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(CircleShape)
                                .background(AuroColor.PillMuted)
                                .clickable {
                                    AuroHaptics.click(view)
                                    onShuffleAll()
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = stringResource(R.string.shuffle_all),
                                    style = AuroType.MicroLabel.copy(
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                )
                            }
                        }
                    }
                }

                itemsIndexed(top5Tracks, key = { _, track -> "top_${track.id}" }) { index, track ->
                    AuroTrackRow(
                        track = track,
                        trackIndex = index,
                        isPlayingThis = track.id == currentPlayingId,
                        hasError = errorTrackIds.contains(track.id),
                        showFormatTag = showFileFormat,
                        onClick = { onTrackClick(track, index) },
                        onLongClick = { onTrackLongClick(track) },
                        onMenuClick = { onTrackMenuClick(track) }
                    )
                }
            }

            // Seção 2: Álbuns / Discografia (Carrossel Horizontal de até 3)
            if (carouselReleases.isNotEmpty()) {
                item(key = "albums_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AuroSpace.ScreenMargin, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(AuroSquircle)
                                    .clickable {
                                        AuroHaptics.click(view)
                                        onNavigateToDiscography()
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.artist_albums),
                                    style = AuroType.SectionTitle,
                                    color = AuroColor.TextPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = AuroColor.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .clip(CircleShape)
                                    .background(AuroColor.PillMuted)
                                    .clickable {
                                        AuroHaptics.click(view)
                                        onNavigateToDiscography()
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.artist_see_all),
                                    style = AuroType.MicroLabel.copy(
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                )
                            }
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = AuroSpace.ScreenMargin),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                        ) {
                            items(carouselReleases, key = { it.albumName }) { release ->
                                val cover = rememberTrackCover(release.coverTrack, 300)
                                Column(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clip(AuroSquircle)
                                        .clickable {
                                            AuroHaptics.click(view)
                                            onAlbumClick(release.albumName)
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(140.dp)
                                            .clip(AuroSquircle)
                                            .background(AuroColor.SurfaceCard)
                                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), AuroSquircle)
                                    ) {
                                        if (cover != null) {
                                            Image(
                                                bitmap = cover.asImageBitmap(),
                                                contentDescription = release.albumName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            AuroCoverPlaceholder(modifier = Modifier.fillMaxSize(), glass = false)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = release.albumName,
                                        style = AuroType.CardTitle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                        color = AuroColor.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val typeLabel = if (release.isAlbum) {
                                        stringResource(R.string.group_subtitle_album)
                                    } else {
                                        stringResource(R.string.artist_singles_eps)
                                    }
                                    val desc = if (release.year > 0) "${release.year} • $typeLabel" else typeLabel
                                    Text(
                                        text = desc,
                                        style = AuroType.Caption,
                                        color = AuroColor.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top Bar transparente com Glass Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    AuroHaptics.click(view)
                    onBack()
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = AuroColor.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        onTogglePin()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(if (isPinned) R.string.action_unpin else R.string.action_pin),
                        tint = if (isPinned) Color.White else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = {
                            AuroHaptics.click(view)
                            showMenu = true
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.cd_more_options),
                            tint = AuroColor.TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(AuroColor.SurfaceGrouped)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.artist_change_media),
                                    style = AuroType.Body,
                                    color = AuroColor.TextPrimary
                                )
                            },
                            onClick = {
                                showMenu = false
                                onChangeMedia()
                            }
                        )
                        if (customMediaPath != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.artist_remove_media),
                                        style = AuroType.Body,
                                        color = AuroColor.Danger
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onRemoveMedia()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * TELA COMPLETA DE MÚSICAS DO ARTISTA
 * - Top Bar com nome do artista, contagem, Tocar tudo e Aleatório
 * - Filtro opcional: "Mostrar feats"
 * - Fast scroller alfabético quando > 15 faixas
 */
@Composable
fun AuroArtistTracksScreen(
    artistName: String,
    onBack: () -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onTrackMenuClick: (Track) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    currentPlayingId: Long,
    dataVersion: Int = 0,
    errorTrackIds: Set<Long> = emptySet(),
    showFileFormat: Boolean = true,
    libraryRepository: LibraryRepository = LibraryRepository.getInstance(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val bottomInset = LocalAuroBottomInset.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val primaryTracks = remember(artistName, dataVersion) { libraryRepository.getPrimaryTracksForArtist(artistName, SortMode.TITLE_ASC) }
    val featTracks = remember(artistName, dataVersion) { libraryRepository.getFeatTracksForArtist(artistName, SortMode.TITLE_ASC) }

    var showFeats by remember(artistName, dataVersion) { mutableStateOf(primaryTracks.isEmpty() && featTracks.isNotEmpty()) }

    val displayedTracks = remember(primaryTracks, featTracks, showFeats) {
        if (showFeats && featTracks.isNotEmpty()) {
            (primaryTracks + featTracks).distinctBy { it.id }.sortedBy { it.title.lowercase() }
        } else {
            primaryTracks
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        onBack()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = formatArtistsDisplay(artistName, artistName),
                        style = AuroType.SectionTitle.copy(fontSize = 18.sp),
                        color = AuroColor.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = pluralStringResource(R.plurals.tracks_count, displayedTracks.size, displayedTracks.size),
                        style = AuroType.Caption,
                        color = AuroColor.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        onPlayAll(displayedTracks)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.play_all),
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        onShuffleAll(displayedTracks)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.shuffle_all),
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Filtro de participações (Feats)
            if (featTracks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuroSpace.ScreenMargin, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(if (showFeats) Color.White.copy(alpha = 0.18f) else AuroColor.PillMuted)
                            .clickable {
                                AuroHaptics.click(view)
                                showFeats = !showFeats
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${stringResource(R.string.artist_show_feats)} (${featTracks.size})",
                            style = AuroType.MicroLabel.copy(
                                fontSize = 12.sp,
                                fontWeight = if (showFeats) FontWeight.Bold else FontWeight.Normal,
                                color = if (showFeats) Color.White else Color.White.copy(alpha = 0.70f)
                            )
                        )
                    }
                }
            }

            // Lista de Faixas
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset + 80.dp)
            ) {
                itemsIndexed(displayedTracks, key = { _, track -> track.id }) { index, track ->
                    AuroTrackRow(
                        track = track,
                        trackIndex = index,
                        isPlayingThis = track.id == currentPlayingId,
                        hasError = errorTrackIds.contains(track.id),
                        showFormatTag = showFileFormat,
                        onClick = { onTrackClick(track, index) },
                        onLongClick = { onTrackLongClick(track) },
                        onMenuClick = { onTrackMenuClick(track) }
                    )
                }
            }
        }

        // FastScroller translúcido na margem direita
        if (displayedTracks.size > 15) {
            AuroAlphabetFastScroller(
                onLetterSelected = { letter ->
                    val targetIndex = displayedTracks.indexOfFirst { matchesFirstLetter(it.title, letter) }
                    if (targetIndex >= 0) {
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, bottom = bottomInset + 30.dp, end = 2.dp)
            )
        }
    }
}

/**
 * TELA DE DISCOGRAFIA DO ARTISTA
 * - Top Bar: "Discografia" • nome do artista
 * - Seção 1: Álbuns (Grid de 2 colunas, cronológico decrescente)
 * - Seção 2: Singles e EPs (Grid de 2 colunas, cronológico decrescente)
 */
@Composable
fun AuroArtistDiscographyScreen(
    artistName: String,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    dataVersion: Int = 0,
    libraryRepository: LibraryRepository = LibraryRepository.getInstance(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val bottomInset = LocalAuroBottomInset.current
    val listState = rememberLazyListState()

    val releases = remember(artistName, dataVersion) { libraryRepository.getArtistReleases(artistName) }
    val albums = remember(releases) { releases.filter { it.isAlbum } }
    val singlesAndEps = remember(releases) { releases.filterNot { it.isAlbum } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    AuroHaptics.click(view)
                    onBack()
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = AuroColor.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.artist_discography),
                    style = AuroType.SectionTitle.copy(fontSize = 18.sp),
                    color = AuroColor.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatArtistsDisplay(artistName, artistName),
                    style = AuroType.Caption,
                    color = AuroColor.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AuroSpace.ScreenMargin,
                end = AuroSpace.ScreenMargin,
                top = 8.dp,
                bottom = bottomInset + 80.dp
            )
        ) {
            // Seção: Álbuns
            if (albums.isNotEmpty()) {
                item(key = "section_albums_header") {
                    Text(
                        text = "${stringResource(R.string.artist_albums)} (${albums.size})",
                        style = AuroType.SectionTitle,
                        color = AuroColor.TextPrimary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
                    )
                }

                val albumPairs = albums.chunked(2)
                items(albumPairs, key = { it.first().albumName }) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (album in pair) {
                            Box(modifier = Modifier.weight(1f)) {
                                DiscographyCard(
                                    release = album,
                                    onClick = {
                                        AuroHaptics.click(view)
                                        onAlbumClick(album.albumName)
                                    }
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Seção: Singles e EPs
            if (singlesAndEps.isNotEmpty()) {
                item(key = "section_singles_header") {
                    Text(
                        text = "${stringResource(R.string.artist_singles_eps)} (${singlesAndEps.size})",
                        style = AuroType.SectionTitle,
                        color = AuroColor.TextPrimary,
                        modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
                    )
                }

                val singlePairs = singlesAndEps.chunked(2)
                items(singlePairs, key = { it.first().albumName }) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (release in pair) {
                            Box(modifier = Modifier.weight(1f)) {
                                DiscographyCard(
                                    release = release,
                                    onClick = {
                                        AuroHaptics.click(view)
                                        onAlbumClick(release.albumName)
                                    }
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscographyCard(
    release: ArtistRelease,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cover = rememberTrackCover(release.coverTrack, 300)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AuroSquircle)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(AuroSquircle)
                .background(AuroColor.SurfaceCard)
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), AuroSquircle)
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = release.albumName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AuroCoverPlaceholder(modifier = Modifier.fillMaxSize(), glass = false)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = release.albumName,
            style = AuroType.CardTitle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = AuroColor.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        val desc = buildString {
            if (release.year > 0) append("${release.year} • ")
            append(pluralStringResource(R.plurals.tracks_count, release.trackCount, release.trackCount))
        }

        Text(
            text = desc,
            style = AuroType.Caption,
            color = AuroColor.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
