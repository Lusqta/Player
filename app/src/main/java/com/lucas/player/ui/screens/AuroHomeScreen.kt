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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
 * ABA INÍCIO (Apple Music Android 7.0.2 - Hub Offline)
 * - Large Title "Início" + botão flutuante de vidro superior direito (Menu/Configurações)
 * - Card Continuar Ouvindo com ações rápidas Reproduzir e Aleatório
 * - Carrosséis horizontais: Tocadas Recentemente, Mais Tocadas, Adicionadas Recentemente
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
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val accent = LocalAuroAccent.current.a.value
    val bottomInset = LocalAuroBottomInset.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = bottomInset + 24.dp
                )
            ) {
                // Header com Large Title "Início" e botão circular flutuante de vidro no topo direito
                item(key = "home_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = AuroType.LargeTitle,
                            maxLines = 1
                        )

                        AuroGlass(
                            kind = AuroGlassKind.Button,
                            cornerRadius = 20.dp,
                            modifier = Modifier.size(AuroHeight.GlassButton),
                            onClick = {
                                AuroHaptics.click(view)
                                onOpenSettings()
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
                    }
                }

                // Card Agrupado: Continuar Ouvindo
                item(key = "continue_listening") {
                    val activeTrack = currentTrack ?: recentlyPlayed.firstOrNull() ?: allTracks.firstOrNull()
                    if (activeTrack != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AuroSpace.ScreenMargin, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_continue_listening),
                                style = AuroType.SectionTitle,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            val coverBitmap = rememberTrackCover(activeTrack, 150)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AuroRadius.card))
                                    .background(AuroColor.SurfaceGrouped)
                                    .clickable {
                                        AuroHaptics.click(view)
                                        if (currentTrack != null) {
                                            onOpenNowPlaying()
                                        } else {
                                            onTrackClick(activeTrack)
                                        }
                                    }
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Capa da faixa
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (coverBitmap != null) {
                                                Image(
                                                    bitmap = coverBitmap.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                AuroCoverPlaceholder(
                                                    modifier = Modifier.fillMaxSize(),
                                                    glass = false,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = activeTrack.title,
                                                style = AuroType.CardTitle,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = activeTrack.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                                                style = AuroType.Subtitle,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Ações Rápidas: Reproduzir e Aleatório
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .clip(CircleShape)
                                                .background(AuroColor.SurfaceElevated)
                                                .clickable {
                                                    AuroHaptics.click(view)
                                                    onResumePlayback()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.PlayArrow,
                                                    contentDescription = null,
                                                    tint = accent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(R.string.play),
                                                    style = AuroType.Body.copy(
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = accent
                                                    )
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .clip(CircleShape)
                                                .background(AuroColor.SurfaceElevated)
                                                .clickable {
                                                    AuroHaptics.click(view)
                                                    onShuffleAll(allTracks)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Shuffle,
                                                    contentDescription = null,
                                                    tint = accent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(R.string.shuffle_all),
                                                    style = AuroType.Body.copy(
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = accent
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Seção: Tocadas Recentemente
                if (recentlyPlayed.isNotEmpty()) {
                    item(key = "section_recently_played") {
                        HomeMediaCarousel(
                            title = stringResource(R.string.home_recently_played),
                            tracks = recentlyPlayed,
                            onTrackClick = onTrackClick
                        )
                    }
                }

                // Seção: Mais Tocadas
                if (mostPlayed.isNotEmpty()) {
                    item(key = "section_most_played") {
                        HomeMediaCarousel(
                            title = stringResource(R.string.home_most_played),
                            tracks = mostPlayed,
                            onTrackClick = onTrackClick
                        )
                    }
                }

                // Seção: Adicionadas Recentemente
                if (recentlyAdded.isNotEmpty()) {
                    item(key = "section_recently_added") {
                        HomeMediaCarousel(
                            title = stringResource(R.string.home_recently_added),
                            tracks = recentlyAdded,
                            onTrackClick = onTrackClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMediaCarousel(
    title: String,
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit
) {
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = AuroType.SectionTitle,
            modifier = Modifier.padding(horizontal = AuroSpace.ScreenMargin, vertical = 6.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = AuroSpace.ScreenMargin),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(tracks.take(15), key = { it.id }) { track ->
                val coverBitmap = rememberTrackCover(track, 180)

                Column(
                    modifier = Modifier
                        .width(140.dp)
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
                            .size(140.dp)
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

                    Spacer(modifier = Modifier.height(8.dp))

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

                    Spacer(modifier = Modifier.height(2.dp))

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
        }
    }
}
