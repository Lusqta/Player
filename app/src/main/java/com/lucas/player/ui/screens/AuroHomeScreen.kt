package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.data.LibraryRepository
import com.lucas.player.model.Track
import com.lucas.player.model.formatArtistsDisplay
import com.lucas.player.model.isJunkAudio
import com.lucas.player.model.isPrimaryArtistTrack
import com.lucas.player.model.splitArtists
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class HomeComputedData(
    val artistEntries: List<Pair<String, Track?>> = emptyList(),
    val topAlbums: List<Track> = emptyList()
)

data class HomePlaylist(
    val id: Long = 0L,
    val name: String,
    val countText: String,
    val coverTrack: Track?,
    val coverPath: String = ""
)

/**
 * ABA INÍCIO (Editorial Dark OLED - Minimalista & Atmosférico)
 * - Top greeting 2 linhas: "Recomendado\nPara Você Hoje"
 * - Carrossel de Mixes rápidos (108dp × 68dp, raio 18dp) com gradientes atmosféricos
 * - Carrossel de Álbuns mais ouvidos (140dp × 140dp, raio 20dp)
 * - Linha de Artistas (72dp × 72dp, raio 18dp)
 * - Coleções como carrossel de playlists com card tracejado para criar nova playlist
 */
@Composable
fun AuroHomeScreen(
    currentTrack: Track?,
    isPlaying: Boolean,
    recentlyPlayed: List<Track>,
    mostPlayed: List<Track>,
    allTracks: List<Track>,
    isLoading: Boolean = false,
    loadingCount: Int = 0,
    onTrackClick: (Track) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onResumePlayback: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit = {},
    onOpenMostPlayed: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    playlists: List<HomePlaylist> = emptyList(),
    onAlbumClick: (String) -> Unit = {},
    onAlbumPlay: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onCreatePlaylist: () -> Unit = {},
    artistMediaMap: Map<String, Pair<String, Boolean>> = emptyMap(),
    libraryRepository: LibraryRepository = LibraryRepository.getInstance(),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val bottomInset = LocalAuroBottomInset.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AuroLoadingBar(modifier = Modifier.fillMaxWidth(0.7f))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.library_loading_count, loadingCount),
                    style = AuroType.Subtitle.copy(color = AuroColor.TextSecondary)
                )
            }
        } else if (allTracks.isEmpty()) {
            AuroEmptyState(
                icon = Icons.Rounded.MusicOff,
                title = stringResource(R.string.empty_library_title),
                message = stringResource(R.string.empty_library_message),
                actionLabel = stringResource(R.string.empty_library_action),
                onAction = onOpenSettings,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val cleanTracks = remember(allTracks) { allTracks.filterNot { isJunkAudio(it) } }

            val computedData by produceState(
                initialValue = HomeComputedData(),
                key1 = cleanTracks,
                key2 = mostPlayed
            ) {
                value = withContext(Dispatchers.IO) {
                    val map = mutableMapOf<String, MutableList<Track>>()
                    val casingMap = mutableMapOf<String, String>()

                    for (track in cleanTracks) {
                        val artistsList = splitArtists(track.artist)
                        for (artist in artistsList) {
                            val key = artist.lowercase().trim()
                            if (key.isEmpty() || !isValidHighlightName(artist)) continue
                            map.getOrPut(key) { mutableListOf() }.add(track)
                            if (!casingMap.containsKey(key)) {
                                casingMap[key] = artist.trim()
                            }
                        }
                    }

                    val artists = map.map { (key, tracks) ->
                        val displayName = casingMap[key] ?: key
                        val totalPlays = tracks.sumOf { it.playCount }
                        val hasPrimaryTrack = tracks.any { isPrimaryArtistTrack(it, displayName) }
                        val coverTrack = if (hasPrimaryTrack) {
                            libraryRepository.resolveArtistFallbackCoverTrack(displayName)
                                ?: tracks.maxByOrNull { it.playCount }
                                ?: tracks.firstOrNull()
                        } else {
                            null
                        }
                        Triple(displayName, coverTrack, totalPlays)
                    }
                        .sortedByDescending { it.third }
                        .take(12)
                        .map { Pair(it.first, it.second) }

                    val sourceTracks = if (mostPlayed.isNotEmpty()) {
                        val cleanMostPlayed = mostPlayed.filterNot { isJunkAudio(it) }
                        if (cleanMostPlayed.isNotEmpty()) cleanMostPlayed else cleanTracks
                    } else {
                        cleanTracks
                    }
                    val albums = sourceTracks.filter { isValidHighlightName(it.album) }
                        .groupBy { it.album.trim() }
                        .values
                        .sortedByDescending { group -> group.sumOf { it.playCount } }
                        .map { group -> group.maxByOrNull { it.playCount } ?: group.first() }
                        .take(10)

                    HomeComputedData(artistEntries = artists, topAlbums = albums)
                }
            }
            val artistEntries = computedData.artistEntries
            val topAlbums = computedData.topAlbums

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = bottomInset + 80.dp
                )
            ) {
                // Top Greeting ("Início" / "Home")
                item(key = "home_greeting") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = AuroSpace.ScreenMargin)
                            .padding(top = 20.dp, bottom = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_greeting_recommended),
                            style = AuroType.GreetingTitle,
                            color = AuroColor.TextPrimary
                        )
                    }
                }

                // Grade 2×2 de Mixes rápidos ocupando largura útil entre margens
                item(key = "mixes_carousel") {
                    val shuffleSource = cleanTracks.ifEmpty { allTracks }
                    val favoritesCount = remember(cleanTracks) { cleanTracks.count { it.rating > 0 } }
                    val cleanRecent = remember(recentlyPlayed) { recentlyPlayed.filterNot { isJunkAudio(it) } }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuroSpace.ScreenMargin),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_shuffle),
                                subtitle = formatTracksCount(shuffleSource.size),
                                icon = Icons.Rounded.Shuffle,
                                modifier = Modifier.weight(1f),
                                onClick = { onShuffleAll(shuffleSource) }
                            )
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_most_played),
                                subtitle = stringResource(R.string.home_mix_most_played_subtitle),
                                icon = Icons.Rounded.Whatshot,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenMostPlayed
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_favorites),
                                subtitle = formatTracksCount(favoritesCount),
                                icon = Icons.Rounded.Favorite,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenFavorites
                            )
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_recent),
                                subtitle = formatTracksCount(cleanRecent.size),
                                icon = Icons.Rounded.History,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (cleanRecent.isNotEmpty()) onPlayAll(cleanRecent)
                                    else if (cleanTracks.isNotEmpty()) onPlayAll(cleanTracks)
                                    else onPlayAll(allTracks)
                                }
                            )
                        }
                    }
                }

                // Seção: Álbuns mais ouvidos (Card largo 296 × 128dp)
                if (topAlbums.isNotEmpty()) {
                    item(key = "top_albums") {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 28.dp)) {
                            EditorialSectionHeader(
                                title = stringResource(R.string.home_top_albums),
                                onAction = onOpenLibrary
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = AuroSpace.ScreenMargin),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(topAlbums, key = { it.album }) { t ->
                                    WideAlbumCard(
                                        coverTrack = t,
                                        album = t.album,
                                        artist = formatArtistsDisplay(t.artist, stringResource(R.string.unknown_artist)),
                                        onClick = { onAlbumClick(t.album) },
                                        onPlay = { onAlbumPlay(t.album) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Seção: Artistas (Tile 72dp com AuroSquircle)
                if (artistEntries.isNotEmpty()) {
                    item(key = "artists_row") {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 28.dp)) {
                            EditorialSectionHeader(
                                title = stringResource(R.string.category_artists),
                                onAction = onOpenLibrary
                            )

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = AuroSpace.ScreenMargin),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(artistEntries, key = { it.first }) { (artistName, artistTrack) ->
                                    val media = artistMediaMap[artistName.trim().lowercase()]

                                    Column(
                                        modifier = Modifier
                                            .width(72.dp)
                                            .clickable {
                                                AuroHaptics.click(view)
                                                onArtistClick(artistName)
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(AuroSquircle)
                                                .background(AuroColor.SurfaceCard)
                                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), AuroSquircle),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ArtistAvatarImage(
                                                artistName = artistName,
                                                customMediaPath = media?.first,
                                                isGif = media?.second ?: false,
                                                fallbackTrack = artistTrack,
                                                shape = AuroSquircle,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = artistName,
                                            style = TextStyle(
                                                fontFamily = AuroFontFamily,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White.copy(alpha = 0.90f)
                                            ),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Seção: Coleções (Playlists - Tiles 112dp AuroSquircle)
                item(key = "collections") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 28.dp)) {
                        EditorialSectionHeader(
                            title = stringResource(R.string.home_banner_collections),
                            actionLabel = null
                        )
                        if (playlists.isEmpty()) {
                            val cardShape = RoundedCornerShape(22.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(78.dp)
                                    .padding(horizontal = AuroSpace.ScreenMargin)
                                    .clip(cardShape)
                                    .background(AuroColor.SurfaceCard.copy(alpha = 0.5f))
                                    .drawBehind {
                                        drawRoundRect(
                                            color = Color.White.copy(alpha = 0.22f),
                                            cornerRadius = CornerRadius(22.dp.toPx()),
                                            style = Stroke(
                                                width = 1.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                                            )
                                        )
                                    }
                                    .clickable {
                                        AuroHaptics.click(view)
                                        onCreatePlaylist()
                                    }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.empty_collections_title),
                                            style = AuroType.CardTitle,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.empty_collections_subtitle),
                                            style = AuroType.Subtitle.copy(
                                                fontSize = 12.sp,
                                                color = Color.White.copy(alpha = 0.55f)
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = AuroSpace.ScreenMargin),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item(key = "new_playlist") { NewPlaylistCard(onClick = onCreatePlaylist) }
                                items(playlists, key = { it.id }) { pl ->
                                    PlaylistCard(
                                        coverTrack = pl.coverTrack,
                                        coverPath = pl.coverPath,
                                        title = pl.name,
                                        subtitle = pl.countText,
                                        onClick = { onPlaylistClick(pl.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card de Mix Rápido Editorial Dark OLED (flat fosco #141416, borda uniforme #242428)
 */
@Composable
private fun QuickMixCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val mixShape = RoundedCornerShape(AuroRadius.mix)
    Box(
        modifier = modifier
            .height(106.dp)
            .clip(mixShape)
            .background(Color(0xFF141416))
            .border(0.75.dp, Color(0xFF242428), mixShape)
            .clickable {
                AuroHaptics.click(view)
                onClick()
            }
            .padding(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(20.dp)
        )

        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = AuroFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    fontFamily = AuroFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.56f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Card horizontal largo de Álbum (296 × 128dp, capa quadrada 0dp)
 */
@Composable
private fun WideAlbumCard(
    coverTrack: Track?,
    album: String,
    artist: String,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    val view = LocalView.current
    val bitmap = rememberTrackCover(coverTrack, 320)
    val cardShape = RoundedCornerShape(24.dp)
    val coverShape = RectangleShape

    Box(
        modifier = Modifier
            .size(width = 296.dp, height = 128.dp)
            .clip(cardShape)
            .background(AuroColor.SurfaceCard)
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), cardShape)
            .clickable {
                AuroHaptics.click(view)
                onClick()
            }
            .padding(13.dp)
    ) {
        // Capa à esquerda (102dp)
        Box(
            modifier = Modifier
                .size(102.dp)
                .clip(coverShape)
                .background(AuroColor.SurfaceElevated)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = album,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AuroCoverPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    glass = false,
                    shape = coverShape
                )
            }
        }

        // TranslucentPlayButton (44dp) centralizado na borda direita da capa
        TranslucentPlayButton(
            isPlaying = false,
            size = 44.dp,
            minTouchTarget = 48.dp,
            onClick = onPlay,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 80.dp)
        )

        // Textos centralizados na vertical, 12dp após o botão
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 136.dp, end = 6.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = album,
                style = TextStyle(
                    fontFamily = AuroFontFamily,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AuroColor.TextPrimary
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = artist,
                style = TextStyle(
                    fontFamily = AuroFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.55f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Tile de Playlist (112dp × 112dp, AuroSquircle)
 */
@Composable
private fun PlaylistCard(
    coverTrack: Track?,
    coverPath: String = "",
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val bitmap = rememberPlaylistCover(coverPath, coverTrack, 240)
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable { AuroHaptics.click(view); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(AuroSquircle)
                .background(AuroColor.SurfaceCard)
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), AuroSquircle)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ArtistMonogram(
                    name = title,
                    modifier = Modifier.fillMaxSize(),
                    shape = AuroSquircle
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = TextStyle(
                fontFamily = AuroFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = TextStyle(
                fontFamily = AuroFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.50f)
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Card "Nova Playlist" tracejado (112dp × 112dp, quadrado 0dp)
 */
@Composable
private fun NewPlaylistCard(onClick: () -> Unit) {
    val view = LocalView.current
    val strokeWidth = 1.dp
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable { AuroHaptics.click(view); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RectangleShape)
                .background(AuroColor.SurfaceCard, RectangleShape)
                .drawBehind {
                    val strokePx = strokeWidth.toPx()
                    val halfStroke = strokePx / 2f
                    drawRect(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(halfStroke, halfStroke),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - strokePx,
                            size.height - strokePx
                        ),
                        style = Stroke(
                            width = strokePx,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.70f),
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.create_playlist),
            style = TextStyle(
                fontFamily = AuroFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
