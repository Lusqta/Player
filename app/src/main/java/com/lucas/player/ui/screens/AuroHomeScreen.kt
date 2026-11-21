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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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

/**
 * ABA INÍCIO (Editorial Dark OLED - Minimalista & Atmosférico)
 * - Top greeting 2 linhas: "Recomendado\nPara Você Hoje"
 * - Carrossel de Mixes rápidos (112dp × 68dp, raio 18dp) com gradientes atmosféricos
 * - Adicionadas Recentemente em cartões horizontais (270dp × 112dp, raio 24dp)
 * - Linha de Artistas (72dp × 72dp, raio 18dp)
 * - Banner Coleções & Pastas (110dp, raio 24dp)
 */
@Composable
fun AuroHomeScreen(
    currentTrack: Track?,
    isPlaying: Boolean,
    recentlyPlayed: List<Track>,
    mostPlayed: List<Track>,
    recentlyAdded: List<Track>,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = stringResource(R.string.home_greeting_recommended),
                            style = AuroType.GreetingTitle,
                            color = AuroColor.TextPrimary
                        )

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AuroColor.ControlGlass)
                                .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                .clickable {
                                    AuroHaptics.click(view)
                                    onOpenSettings()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.cd_more_options),
                                tint = AuroColor.TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
                                gradientColors = listOf(Color(0xFF2A203F), Color(0xFF141416)),
                                onClick = { onShuffleAll(allTracks) }
                            )
                        }
                        item {
                            QuickMixCard(
                                title = stringResource(R.string.home_mix_most_played),
                                icon = Icons.Rounded.Whatshot,
                                gradientColors = listOf(Color(0xFF1B3533), Color(0xFF141416)),
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
                                gradientColors = listOf(Color(0xFF3F1A24), Color(0xFF141416)),
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
                                gradientColors = listOf(Color(0xFF382C17), Color(0xFF141416)),
                                onClick = {
                                    if (recentlyPlayed.isNotEmpty()) onPlayAll(recentlyPlayed)
                                    else onPlayAll(allTracks)
                                }
                            )
                        }
                    }
                }

                // Seção: Adicionadas Recentemente (Cartões Horizontais 270dp × 112dp)
                val displayAdded = if (recentlyAdded.isNotEmpty()) recentlyAdded else allTracks.take(10)
                if (displayAdded.isNotEmpty()) {
                    item(key = "featured_recent") {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            EditorialSectionHeader(
                                title = stringResource(R.string.home_recently_added),
                                actionLabel = stringResource(R.string.view_all),
                                onAction = onOpenLibrary
                            )

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = AuroSpace.ScreenMargin),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(displayAdded.take(8), key = { it.id }) { track ->
                                    FeaturedTrackCard(
                                        track = track,
                                        onPlay = {
                                            onTrackClick(track)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Seção: Artistas (72dp × 72dp, raio 18dp)
                if (artists.isNotEmpty()) {
                    item(key = "artists_row") {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            EditorialSectionHeader(
                                title = stringResource(R.string.category_artists),
                                actionLabel = null,
                                onAction = null
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
                                                if (artistTrack != null) onTrackClick(artistTrack)
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

                // Banner: Coleções & Pastas (110dp, raio 24dp)
                item(key = "collections_banner") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 20.dp)
                            .height(110.dp)
                            .clip(RoundedCornerShape(AuroRadius.card))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF242428), Color(0xFF141416))
                                )
                            )
                            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(AuroRadius.card))
                            .clickable {
                                AuroHaptics.click(view)
                                onOpenLibrary()
                            }
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AuroColor.ControlGlass),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LibraryMusic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.home_banner_collections),
                                    style = AuroType.SectionTitle,
                                    color = AuroColor.TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.tab_library),
                                    style = AuroType.Subtitle,
                                    color = AuroColor.TextSecondary
                                )
                            }

                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.40f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card de Mix Rápido com gradiente atmosférico (112dp × 68dp, raio 18dp)
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
            .size(width = 112.dp, height = 68.dp)
            .clip(RoundedCornerShape(AuroRadius.mix))
            .background(Brush.linearGradient(gradientColors))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(AuroRadius.mix))
            .clickable {
                AuroHaptics.click(view)
                onClick()
            }
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(16.dp)
                )
                TranslucentPlayButton(
                    isPlaying = false,
                    size = 22.dp,
                    onClick = onClick
                )
            }
            Text(
                text = title,
                style = AuroType.MicroLabel.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Cartão de Faixa em Destaque (270dp × 112dp, raio 24dp)
 */
@Composable
private fun FeaturedTrackCard(
    track: Track,
    onPlay: () -> Unit
) {
    val coverBitmap = rememberTrackCover(track, 180)
    val view = LocalView.current

    Box(
        modifier = Modifier
            .size(width = 270.dp, height = 112.dp)
            .clip(RoundedCornerShape(AuroRadius.card))
            .background(AuroColor.SurfaceCard)
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(AuroRadius.card))
            .clickable {
                AuroHaptics.click(view)
                onPlay()
            }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Capa 84dp com raio 18dp + TranslucentPlayButton
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(AuroRadius.artist))
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
                        shape = RoundedCornerShape(AuroRadius.artist)
                    )
                }

                // Play Button sobre a capa
                TranslucentPlayButton(
                    isPlaying = false,
                    size = 32.dp,
                    onClick = onPlay
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    style = AuroType.CardTitle,
                    color = AuroColor.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                    style = AuroType.Subtitle,
                    color = AuroColor.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
