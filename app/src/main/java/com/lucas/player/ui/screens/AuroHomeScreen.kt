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
import com.lucas.player.model.splitArtists
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.*

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

            val artistEntries = remember(cleanTracks) {
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

                map.map { (key, tracks) ->
                    val displayName = casingMap[key] ?: key
                    val totalPlays = tracks.sumOf { it.playCount }
                    val coverTrack = libraryRepository.resolveArtistFallbackCoverTrack(displayName)
                        ?: tracks.maxByOrNull { it.playCount }
                        ?: tracks.firstOrNull()
                    Triple(displayName, coverTrack, totalPlays)
                }
                .sortedByDescending { it.third }
                .take(12)
                .map { Pair(it.first, it.second) }
            }

            val topAlbums = remember(cleanTracks, mostPlayed) {
                val sourceTracks = if (mostPlayed.isNotEmpty()) {
                    val cleanMostPlayed = mostPlayed.filterNot { isJunkAudio(it) }
                    if (cleanMostPlayed.isNotEmpty()) cleanMostPlayed else cleanTracks
                } else {
                    cleanTracks
                }
                sourceTracks.filter { isValidHighlightName(it.album) }
                    .groupBy { it.album.trim() }
                    .values
                    .sortedByDescending { group -> group.sumOf { it.playCount } }
                    .map { group -> group.maxByOrNull { it.playCount } ?: group.first() }
                    .take(10)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = bottomInset + 80.dp
                )
            ) {
                // Top Greeting
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

                // Carrossel de Mixes rápidos (122dp × 70dp, raio 22dp)
                item(key = "mixes_carousel") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(start = AuroSpace.ScreenMargin, end = AuroSpace.ScreenMargin, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_shuffle),
                                icon = Icons.Rounded.Shuffle,
                                baseColor = Color(0xFF2A1210),
                                spotAColor = Color(0xFFD2703A), spotACx = 0.30f, spotACy = 0.80f, spotARadius = 0.75f, spotAAlpha = 0.95f,
                                spotBColor = Color(0xFF7A2A22), spotBCx = 0.95f, spotBCy = 0.10f, spotBRadius = 0.60f, spotBAlpha = 0.80f,
                                onClick = { onShuffleAll(cleanTracks.ifEmpty { allTracks }) }
                            )
                        }
                        item {
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_most_played),
                                icon = Icons.Rounded.Whatshot,
                                baseColor = Color(0xFF1A1340),
                                spotAColor = Color(0xFF6F4BF0), spotACx = 0.50f, spotACy = 0.45f, spotARadius = 0.70f, spotAAlpha = 0.95f,
                                spotBColor = Color(0xFF3A2A90), spotBCx = 0.10f, spotBCy = 0.95f, spotBRadius = 0.55f, spotBAlpha = 0.80f,
                                onClick = onOpenMostPlayed,
                                onPlayClick = {
                                    val cleanMost = mostPlayed.filterNot { isJunkAudio(it) }
                                    if (cleanMost.isNotEmpty()) onPlayAll(cleanMost)
                                    else if (cleanTracks.isNotEmpty()) onPlayAll(cleanTracks)
                                    else onPlayAll(allTracks)
                                }
                            )
                        }
                        item {
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_favorites),
                                icon = Icons.Rounded.Favorite,
                                baseColor = Color(0xFF2A0E18),
                                spotAColor = Color(0xFFD04565), spotACx = 0.25f, spotACy = 0.85f, spotARadius = 0.75f, spotAAlpha = 0.95f,
                                spotBColor = Color(0xFF6A1A34), spotBCx = 0.90f, spotBCy = 0.15f, spotBRadius = 0.55f, spotBAlpha = 0.80f,
                                onClick = onOpenFavorites,
                                onPlayClick = {
                                    val favs = cleanTracks.filter { it.rating > 0 }
                                    if (favs.isNotEmpty()) onPlayAll(favs)
                                    else onShuffleAll(cleanTracks.ifEmpty { allTracks })
                                }
                            )
                        }
                        item {
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_recent),
                                icon = Icons.Rounded.History,
                                baseColor = Color(0xFF0E1B28),
                                spotAColor = Color(0xFF2F7FA0), spotACx = 0.80f, spotACy = 0.85f, spotARadius = 0.75f, spotAAlpha = 0.95f,
                                spotBColor = Color(0xFF1B3F5C), spotBCx = 0.10f, spotBCy = 0.15f, spotBRadius = 0.55f, spotBAlpha = 0.80f,
                                onClick = {
                                    val cleanRecent = recentlyPlayed.filterNot { isJunkAudio(it) }
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
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                            EditorialSectionHeader(
                                title = stringResource(R.string.home_top_albums),
                                actionLabel = stringResource(R.string.view_all),
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

                // Seção: Artistas (Tile 72dp com AuroSquircle, item 80dp largura)
                if (artistEntries.isNotEmpty()) {
                    item(key = "artists_row") {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                            EditorialSectionHeader(
                                title = stringResource(R.string.category_artists),
                                actionLabel = stringResource(R.string.view_all),
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
                                            .width(80.dp)
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
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
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
 * Card de Mix Rápido (122dp × 70dp, raio 22dp, play pendurado para fora)
 */
@Composable
private fun QuickMixCard(
    title: String,
    icon: ImageVector,
    baseColor: Color,
    spotAColor: Color,
    spotACx: Float,
    spotACy: Float,
    spotARadius: Float,
    spotAAlpha: Float,
    spotBColor: Color,
    spotBCx: Float,
    spotBCy: Float,
    spotBRadius: Float,
    spotBAlpha: Float,
    onClick: () -> Unit,
    onPlayClick: () -> Unit = onClick
) {
    val view = LocalView.current
    val mixShape = RoundedCornerShape(AuroRadius.mix)
    Box(
        modifier = Modifier
            .size(width = 122.dp, height = 70.dp)
    ) {
        // Camada de fundo clipada com as 2 manchas radiais borradas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(mixShape)
                .drawBehind {
                    drawRect(baseColor)
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(spotAColor.copy(alpha = spotAAlpha), spotAColor.copy(alpha = 0f)),
                            center = Offset(size.width * spotACx, size.height * spotACy),
                            radius = size.width * spotARadius
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(spotBColor.copy(alpha = spotBAlpha), spotBColor.copy(alpha = 0f)),
                            center = Offset(size.width * spotBCx, size.height * spotBCy),
                            radius = size.width * spotBRadius
                        )
                    )
                }
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), mixShape)
                .clickable {
                    AuroHaptics.click(view)
                    onClick()
                }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.90f),
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp)
                    .size(14.dp)
                    .align(Alignment.TopStart)
            )
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = AuroFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Botão de play 28dp pendurado no canto inferior direito
        TranslucentPlayButton(
            isPlaying = false,
            size = 28.dp,
            minTouchTarget = 48.dp,
            onClick = onPlayClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 7.dp)
        )
    }
}

/**
 * Card horizontal largo de Álbum (296 × 128dp, raio 36dp)
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
    val cardShape = RoundedCornerShape(36.dp)
    val coverShape = RoundedCornerShape(28.dp)

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
 * Card "Nova Playlist" tracejado (112dp × 112dp, AuroSquircle)
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
                .clip(AuroSquircle)
                .background(AuroColor.SurfaceCard, AuroSquircle)
                .drawBehind {
                    val strokePx = strokeWidth.toPx()
                    val halfStroke = strokePx / 2f
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(halfStroke, halfStroke),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - strokePx,
                            size.height - strokePx
                        ),
                        cornerRadius = CornerRadius(size.width * 0.26f),
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
