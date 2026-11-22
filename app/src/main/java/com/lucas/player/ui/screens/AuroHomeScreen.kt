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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.model.Track
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.*

data class HomePlaylist(val name: String, val countText: String, val coverTrack: Track?)

private val HomeCardSize = 140.dp
private val HomeCardRadius = 20.dp

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
    onArtistClick: (String) -> Unit = {},
    playlists: List<HomePlaylist> = emptyList(),
    onAlbumClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onCreatePlaylist: () -> Unit = {},
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
            val artists = remember(allTracks) {
                allTracks.asSequence().map { it.artist }.filter { it.isNotEmpty() }.distinct().take(12).toList()
            }

            val topAlbums = remember(mostPlayed) {
                mostPlayed.filter { it.album.isNotBlank() }
                    .groupBy { it.album }
                    .values
                    .sortedByDescending { it.size }
                    .map { it.first() }
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
                // Top Greeting & Settings Button
                item(key = "home_greeting") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_greeting_recommended),
                            style = AuroType.GreetingTitle,
                            color = AuroColor.TextPrimary
                        )
                    }
                }

                // Carrossel de Mixes rápidos (112dp × 68dp, raio 18dp)
                item(key = "mixes_carousel") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentPadding = PaddingValues(horizontal = AuroSpace.ScreenMargin),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_shuffle),
                                icon = Icons.Rounded.Shuffle,
                                gradientColors = listOf(Color(0xFF9E472A), Color(0xFF2A1618)),
                                onClick = { onShuffleAll(allTracks) }
                            )
                        }
                        item {
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_most_played),
                                icon = Icons.Rounded.Whatshot,
                                gradientColors = listOf(Color(0xFF5B46C6), Color(0xFF1D1936)),
                                onClick = {
                                    if (mostPlayed.isNotEmpty()) onPlayAll(mostPlayed)
                                    else onPlayAll(allTracks)
                                }
                            )
                        }
                        item {
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_favorites),
                                icon = Icons.Rounded.Favorite,
                                gradientColors = listOf(Color(0xFF7A2438), Color(0xFF1F1216)),
                                onClick = {
                                    val favs = allTracks.filter { it.rating > 0 }
                                    if (favs.isNotEmpty()) onPlayAll(favs)
                                    else onShuffleAll(allTracks)
                                }
                            )
                        }
                        item {
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_recent),
                                icon = Icons.Rounded.History,
                                gradientColors = listOf(Color(0xFF23536B), Color(0xFF111C24)),
                                onClick = {
                                    if (recentlyPlayed.isNotEmpty()) onPlayAll(recentlyPlayed)
                                    else onPlayAll(allTracks)
                                }
                            )
                        }
                    }
                }

                // Seção: Álbuns mais ouvidos
                if (topAlbums.isNotEmpty()) {
                    item(key = "top_albums") {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
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
                                    CoverCard(
                                        coverTrack = t,
                                        title = t.album,
                                        subtitle = t.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                                        onClick = { onAlbumClick(t.album) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Seção: Artistas (72dp × 72dp, raio 18dp)
                if (artists.isNotEmpty()) {
                    item(key = "artists_row") {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
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
                                items(artists) { artistName ->
                                    val artistTrack = remember(artistName) {
                                        allTracks.firstOrNull { it.artist == artistName }
                                    }
                                    val coverBitmap = rememberTrackCover(artistTrack, 120)

                                    Column(
                                        modifier = Modifier
                                            .width(76.dp)
                                            .clickable {
                                                AuroHaptics.click(view)
                                                onArtistClick(artistName)
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(AuroRadius.artist))
                                                .background(AuroColor.SurfaceCard)
                                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(AuroRadius.artist)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (coverBitmap != null) {
                                                Image(
                                                    bitmap = coverBitmap.asImageBitmap(),
                                                    contentDescription = artistName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.Person,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.40f),
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = artistName,
                                            style = AuroType.MicroLabel,
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

                item(key = "collections") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        EditorialSectionHeader(
                            title = stringResource(R.string.home_banner_collections),
                            actionLabel = null
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = AuroSpace.ScreenMargin),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item(key = "new_playlist") { NewPlaylistCard(onClick = onCreatePlaylist) }
                            items(playlists, key = { it.name }) { pl ->
                                CoverCard(
                                    coverTrack = pl.coverTrack,
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

/**
 * Card de Mix Rápido com gradiente atmosférico (108dp × 68dp, raio 18dp)
 */
@Composable
private fun QuickMixCard(
    title: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    val view = LocalView.current
    Box(
        modifier = Modifier
            .size(width = 108.dp, height = 68.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .clickable {
                AuroHaptics.click(view)
                onClick()
            }
            .padding(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.70f),
            modifier = Modifier
                .size(14.dp)
                .align(Alignment.TopStart)
        )
        Text(
            text = title,
            style = AuroType.MicroLabel.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        TranslucentPlayButton(
            isPlaying = false,
            size = 20.dp,
            onClick = onClick,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun CoverCard(
    coverTrack: Track?,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val bitmap = rememberTrackCover(coverTrack, 320)
    val shape = RoundedCornerShape(HomeCardRadius)
    Column(
        modifier = Modifier
            .width(HomeCardSize)
            .clip(shape)
            .clickable { AuroHaptics.click(view); onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(HomeCardSize)
                .clip(shape)
                .background(AuroColor.SurfaceCard)
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), shape)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AuroCoverPlaceholder(modifier = Modifier.fillMaxSize(), glass = false, shape = shape)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = AuroType.CardTitle.copy(fontSize = 14.sp),
            color = AuroColor.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = AuroType.Subtitle.copy(fontSize = 12.sp),
            color = AuroColor.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NewPlaylistCard(onClick: () -> Unit) {
    val view = LocalView.current
    val shape = RoundedCornerShape(HomeCardRadius)
    Column(
        modifier = Modifier
            .width(HomeCardSize)
            .clip(shape)
            .clickable { AuroHaptics.click(view); onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(HomeCardSize)
                .drawBehind {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f),
                        cornerRadius = CornerRadius(HomeCardRadius.toPx()),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.create_playlist),
            style = AuroType.CardTitle.copy(fontSize = 14.sp),
            color = AuroColor.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
