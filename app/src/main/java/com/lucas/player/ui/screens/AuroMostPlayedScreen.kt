package com.lucas.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.data.LibraryRepository
import com.lucas.player.model.*
import com.lucas.player.model.formatArtistsDisplay
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class MostPlayedContentState(
    val period: MostPlayedPeriod? = null,
    val isLoaded: Boolean = false,
    val tracks: List<RankedTrack> = emptyList(),
    val albums: List<RankedAlbum> = emptyList(),
    val artists: List<RankedArtist> = emptyList()
)

@Composable
fun AuroMostPlayedScreen(
    onBack: () -> Unit,
    onTrackClick: (List<Track>, Int) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onTrackMenuClick: (Track) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    currentPlayingId: Long,
    libraryVersion: Int = 0,
    artistMediaMap: Map<String, Pair<String, Boolean>> = emptyMap(),
    showFileFormat: Boolean = true,
    libraryRepository: LibraryRepository = LibraryRepository.getInstance(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val bottomInset = LocalAuroBottomInset.current
    val listState = rememberLazyListState()

    var userSelectedPeriod by remember { mutableStateOf<MostPlayedPeriod?>(null) }
    var defaultPeriod by remember { mutableStateOf<MostPlayedPeriod?>(null) }
    var selectedType by remember { mutableStateOf(MostPlayedType.TRACKS) }

    val contentState by produceState(
        initialValue = MostPlayedContentState(),
        key1 = userSelectedPeriod,
        key2 = selectedType,
        key3 = libraryVersion
    ) {
        value = withContext(Dispatchers.IO) {
            val resolvedPeriod = userSelectedPeriod ?: (defaultPeriod ?: run {
                val detected = if (libraryRepository.hasPlayEvents(MostPlayedPeriod.MONTH)) {
                    MostPlayedPeriod.MONTH
                } else {
                    MostPlayedPeriod.ALL
                }
                defaultPeriod = detected
                detected
            })
            when (selectedType) {
                MostPlayedType.TRACKS -> MostPlayedContentState(
                    period = resolvedPeriod,
                    isLoaded = true,
                    tracks = libraryRepository.getMostPlayedTracks(resolvedPeriod)
                )
                MostPlayedType.ALBUMS -> MostPlayedContentState(
                    period = resolvedPeriod,
                    isLoaded = true,
                    albums = libraryRepository.getMostPlayedAlbums(resolvedPeriod)
                )
                MostPlayedType.ARTISTS -> MostPlayedContentState(
                    period = resolvedPeriod,
                    isLoaded = true,
                    artists = libraryRepository.getMostPlayedArtists(resolvedPeriod)
                )
            }
        }
    }

    val activePeriod = userSelectedPeriod ?: contentState.period ?: MostPlayedPeriod.ALL

    val periodItems = listOf(
        MostPlayedPeriod.WEEK to R.string.most_played_period_week,
        MostPlayedPeriod.MONTH to R.string.most_played_period_month,
        MostPlayedPeriod.YEAR to R.string.most_played_period_year,
        MostPlayedPeriod.ALL to R.string.most_played_period_all
    )

    val typeItems = listOf(
        MostPlayedType.TRACKS to R.string.most_played_type_tracks,
        MostPlayedType.ALBUMS to R.string.most_played_type_albums,
        MostPlayedType.ARTISTS to R.string.most_played_type_artists
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        AuroTopBar(
            title = stringResource(R.string.home_mix_most_played),
            isRoot = true,
            canGoBack = true,
            onBackClick = onBack
        )

        // Filtro 1 — Período (chips pílula 34.dp)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp),
            contentPadding = PaddingValues(horizontal = AuroSpace.ScreenMargin),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(periodItems, key = { it.first.name }) { (period, labelRes) ->
                val isSelected = activePeriod == period
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else AuroColor.SurfaceCard)
                        .then(
                            if (isSelected) Modifier
                            else Modifier.border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                        )
                        .clickable {
                            AuroHaptics.click(view)
                            userSelectedPeriod = period
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = AuroType.Caption.copy(
                            color = if (isSelected) Color(0xFF050505) else AuroColor.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // Filtro 2 — Tipo (Segmented Control 40.dp CircleShape)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuroSpace.ScreenMargin)
                .padding(bottom = 10.dp)
                .height(40.dp)
                .clip(CircleShape)
                .background(AuroColor.SurfaceCard)
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for ((type, labelRes) in typeItems) {
                val isSelected = selectedType == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else Color.Transparent)
                        .clickable {
                            AuroHaptics.click(view)
                            selectedType = type
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = AuroType.Caption.copy(
                            color = if (isSelected) Color(0xFF050505) else AuroColor.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        when (selectedType) {
            MostPlayedType.TRACKS -> {
                val tracks = contentState.tracks
                if (contentState.isLoaded && tracks.isEmpty()) {
                    AuroEmptyState(
                        icon = Icons.AutoMirrored.Outlined.TrendingUp,
                        title = stringResource(R.string.most_played_empty_period),
                        message = "",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = bottomInset + 64.dp)
                    )
                } else {
                    val queueTracks = remember(tracks) { tracks.map { it.track } }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomInset + 80.dp)
                    ) {
                        if (queueTracks.isNotEmpty()) {
                            item(key = "tracks_actions") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = AuroSpace.ScreenMargin, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .clickable {
                                                AuroHaptics.click(view)
                                                onPlayAll(queueTracks)
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = Color(0xFF050505),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.play_all),
                                            style = AuroType.Caption.copy(
                                                color = Color(0xFF050505),
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            maxLines = 1
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(CircleShape)
                                            .background(AuroColor.SurfaceCard)
                                            .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                            .clickable {
                                                AuroHaptics.click(view)
                                                onShuffleAll(queueTracks)
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shuffle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.shuffle_all),
                                            style = AuroType.Caption.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(
                            items = tracks,
                            key = { _, item -> "mp_track_${item.track.id}" }
                        ) { index, item ->
                            RankedTrackRow(
                                rank = index + 1,
                                item = item,
                                isPlayingThis = item.track.id == currentPlayingId,
                                showFormatTag = showFileFormat,
                                onClick = { onTrackClick(queueTracks, index) },
                                onLongClick = { onTrackLongClick(item.track) },
                                onMenuClick = { onTrackMenuClick(item.track) }
                            )
                        }
                    }
                }
            }

            MostPlayedType.ALBUMS -> {
                val albums = contentState.albums
                if (contentState.isLoaded && albums.isEmpty()) {
                    AuroEmptyState(
                        icon = Icons.AutoMirrored.Outlined.TrendingUp,
                        title = stringResource(R.string.most_played_empty_period),
                        message = "",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = bottomInset + 64.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomInset + 80.dp)
                    ) {
                        itemsIndexed(
                            items = albums,
                            key = { _, item -> "mp_album_${item.albumName.lowercase()}" }
                        ) { index, item ->
                            RankedAlbumRow(
                                rank = index + 1,
                                item = item,
                                onClick = {
                                    AuroHaptics.click(view)
                                    onAlbumClick(item.albumName)
                                }
                            )
                        }
                    }
                }
            }

            MostPlayedType.ARTISTS -> {
                val artists = contentState.artists
                if (contentState.isLoaded && artists.isEmpty()) {
                    AuroEmptyState(
                        icon = Icons.AutoMirrored.Outlined.TrendingUp,
                        title = stringResource(R.string.most_played_empty_period),
                        message = "",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = bottomInset + 64.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomInset + 80.dp)
                    ) {
                        itemsIndexed(
                            items = artists,
                            key = { _, item -> "mp_artist_${item.artistName.lowercase()}" }
                        ) { index, item ->
                            val media = artistMediaMap[item.artistName.trim().lowercase()]
                            RankedArtistRow(
                                rank = index + 1,
                                item = item,
                                customMediaPath = media?.first,
                                isGif = media?.second ?: false,
                                onClick = {
                                    AuroHaptics.click(view)
                                    onArtistClick(item.artistName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RankedTrackRow(
    rank: Int,
    item: RankedTrack,
    isPlayingThis: Boolean,
    showFormatTag: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val view = LocalView.current
    val track = item.track
    val thumbShape = RoundedCornerShape(AuroRadius.thumbnail)
    val coverBitmap = rememberTrackCover(track, 120)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    AuroHaptics.click(view)
                    onClick()
                },
                onLongClick = {
                    AuroHaptics.confirm(view)
                    onLongClick()
                }
            )
            .padding(start = AuroSpace.ScreenMargin, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = AuroType.Caption.copy(
                color = if (rank <= 3) Color.White else AuroColor.TextSecondary,
                fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(thumbShape),
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
                    shape = thumbShape
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = AuroType.TrackTitle.copy(
                    color = if (isPlayingThis) Color.White else AuroColor.TextPrimary,
                    fontWeight = if (isPlayingThis) FontWeight.SemiBold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = formatArtistsDisplay(track.artist, stringResource(R.string.unknown_artist)),
                style = AuroType.Subtitle,
                color = AuroColor.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${item.plays}×",
            style = AuroType.MicroLabel.copy(
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            )
        )

        if (showFormatTag && track.formatTag.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = track.formatTag,
                    style = AuroType.FastScroller.copy(
                        color = AuroColor.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatTimeMs(track.durationMs),
            style = AuroType.MicroLabel.copy(
                color = AuroColor.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            )
        )

        Spacer(modifier = Modifier.width(2.dp))

        IconButton(
            onClick = {
                AuroHaptics.click(view)
                onMenuClick()
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(R.string.cd_more_options),
                tint = AuroColor.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RankedAlbumRow(
    rank: Int,
    item: RankedAlbum,
    onClick: () -> Unit
) {
    val coverBitmap = rememberTrackCover(item.coverTrack, 160)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            style = AuroType.Caption.copy(
                color = if (rank <= 3) Color.White else AuroColor.TextSecondary,
                fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            ),
            modifier = Modifier.widthIn(min = 32.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(AuroSquircle),
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
                    shape = AuroSquircle
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.albumName,
                style = AuroType.TrackTitle.copy(color = AuroColor.TextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = formatArtistsDisplay(item.artist, stringResource(R.string.unknown_artist)),
                style = AuroType.Subtitle,
                color = AuroColor.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = "${item.plays}×",
            style = AuroType.Caption.copy(
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            )
        )
    }
}

@Composable
private fun RankedArtistRow(
    rank: Int,
    item: RankedArtist,
    customMediaPath: String?,
    isGif: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            style = AuroType.Caption.copy(
                color = if (rank <= 3) Color.White else AuroColor.TextSecondary,
                fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            ),
            modifier = Modifier.widthIn(min = 32.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        ArtistAvatarImage(
            artistName = item.artistName,
            customMediaPath = customMediaPath,
            isGif = isGif,
            fallbackTrack = item.fallbackTrack,
            shape = AuroSquircle,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = item.artistName,
            style = AuroType.TrackTitle.copy(
                fontSize = 15.sp,
                color = AuroColor.TextPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = "${item.plays}×",
            style = AuroType.Caption.copy(
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            )
        )
    }
}
