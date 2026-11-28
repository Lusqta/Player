package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.data.CoverArtLoader
import com.lucas.player.data.MusicDatabase
import com.lucas.player.model.LibraryCategory
import com.lucas.player.model.Track
import com.lucas.player.model.getTitleRes
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.*
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * TELA DE DRILLDOWN (Editorial Dark OLED - Hero Portrait & Zero Divisores)
 * - Hero Portrait de 360dp com raio 32dp e TranslucentPlayButton (52dp) flutuando no canto inferior direito
 * - Título em HeroName (30sp), subtítulo em TextSecondary
 * - EditorialSectionHeader com ação Aleatório
 * - Lista de faixas sem nenhum divisor
 */
@Composable
fun AuroDrilldownScreen(
    category: LibraryCategory,
    groupTitle: String,
    tracks: List<Track>,
    currentPlayingId: Long,
    onBackToCategoryList: () -> Unit,
    onBackToLibraryRoot: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onTrackMenuClick: (Track) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    errorTrackIds: Set<Long> = emptySet(),
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedTrackIds: Set<Long> = emptySet(),
    onToggleSelectTrack: (Track) -> Unit = {},
    showFileFormat: Boolean = true,
    playlistCoverPath: String? = null,
    albumMeta: MusicDatabase.AlbumMeta? = null,
    albumMediaVersion: Int = 0,
    customMedia: MusicDatabase.CustomMedia? = null,
    customMediaVersion: Int = 0,
    onCustomizeMedia: (() -> Unit)? = null,
    onAddTracksToPlaylist: (() -> Unit)? = null,
    onEditPlaylist: (() -> Unit)? = null,
    onDeletePlaylist: (() -> Unit)? = null,
    onChangeAlbumCover: (() -> Unit)? = null,
    onRemoveAlbumCover: (() -> Unit)? = null,
    onSaveAlbumNotes: ((String) -> Unit)? = null,
    onAddAlbumToCollection: (() -> Unit)? = null
) {
    val view = LocalView.current
    val bottomInset = LocalAuroBottomInset.current
    val coroutineScope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }
    val mosaicTracks = remember(tracks, category) {
        if (category == LibraryCategory.PLAYLISTS) {
            com.lucas.player.model.selectPlaylistMosaicTracks(tracks)
        } else emptyList()
    }

    val firstTrack = tracks.firstOrNull()
    val customAlbumPath = if (category == LibraryCategory.ALBUMS) {
        albumMeta?.mediaPath?.takeIf { it.isNotBlank() }
    } else null
    val isAlbumGif = category == LibraryCategory.ALBUMS && (albumMeta?.isGif ?: false)
    val effectiveAccentMediaPath = remember(customMedia, customAlbumPath, playlistCoverPath, category) {
        val cmPath = customMedia?.staticImagePath?.ifBlank {
            customMedia.gifFrozenFramePath.ifBlank { customMedia.gifPath }
        }?.ifBlank { null }
        when (category) {
            LibraryCategory.ALBUMS -> cmPath ?: customAlbumPath
            LibraryCategory.PLAYLISTS -> cmPath ?: playlistCoverPath?.ifBlank { null }
            else -> cmPath
        }
    }
    val albumMediaStamp = remember(customAlbumPath, effectiveAccentMediaPath, albumMediaVersion, customMediaVersion) {
        val fileMod = effectiveAccentMediaPath?.let { File(it).takeIf(File::exists)?.lastModified() } ?: 0L
        fileMod xor ((albumMediaVersion + customMediaVersion).toLong() shl 16)
    }
    var customAlbumBitmap by remember(effectiveAccentMediaPath, albumMediaStamp) {
        mutableStateOf(
            if (effectiveAccentMediaPath != null) CoverArtLoader.getCachedFileBitmap(effectiveAccentMediaPath) else null
        )
    }
    LaunchedEffect(effectiveAccentMediaPath, albumMediaStamp) {
        if (effectiveAccentMediaPath == null) {
            customAlbumBitmap = null
        } else {
            CoverArtLoader.loadFileBitmap(effectiveAccentMediaPath, 400) { loaded ->
                customAlbumBitmap = loaded
            }
        }
    }

    val heroCover = if (category == LibraryCategory.PLAYLISTS) {
        rememberPlaylistCover(playlistCoverPath, firstTrack, 400)
    } else {
        rememberTrackCover(firstTrack, 400)
    }
    var groupAccent by remember { mutableStateOf(AuroAccent.Neutral) }
    LaunchedEffect(customAlbumBitmap, heroCover, category) {
        val effectiveCover = customAlbumBitmap ?: heroCover
        groupAccent = if (effectiveCover != null) {
            CoverGlow.from(effectiveCover)
        } else if (category == LibraryCategory.TOP_RATED) {
            AuroAccent(mesh1 = Color(0xFFD04565), mesh2 = Color(0xFF6A1A34), a = Color(0xFFD04565), b = Color(0xFF6A1A34))
        } else if (category == LibraryCategory.MOST_PLAYED) {
            AuroAccent(mesh1 = Color(0xFF6F4BF0), mesh2 = Color(0xFF3A2A90), a = Color(0xFF6F4BF0), b = Color(0xFF3A2A90))
        } else {
            AuroAccent.Neutral
        }
    }

    val canFlip = category == LibraryCategory.ALBUMS
    var isFlipped by remember(groupTitle, category) { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    val flipRotation by animateFloatAsState(
        targetValue = if (canFlip && isFlipped) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.82f, stiffness = 320f),
        label = "albumFlipRotation"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        val density = LocalDensity.current
        val minTopPx = with(density) { 96.dp.roundToPx() }
        val fastScrollerTopDp by remember(listState, minTopPx) {
            derivedStateOf {
                val heroInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero_header" }
                if (heroInfo != null) {
                    val heroBottom = heroInfo.offset + heroInfo.size
                    with(density) { maxOf(heroBottom, minTopPx).toDp() + 8.dp }
                } else {
                    96.dp
                }
            }
        }

        // Fundo animado de 2 cores borradas da capa do próprio grupo atrás do Hero (520dp fixos)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
        ) {
            AnimatedDualColorBackground(
                color1 = groupAccent.mesh1,
                color2 = groupAccent.mesh2,
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

        if (tracks.isEmpty() && category != LibraryCategory.PLAYLISTS) {
            AuroEmptyState(
                icon = Icons.Rounded.MusicOff,
                title = stringResource(R.string.empty_library_title),
                message = stringResource(R.string.empty_library_message),
                actionLabel = stringResource(R.string.cd_back),
                onAction = onBackToCategoryList,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset + 80.dp)
            ) {
                // Hero Header Centralizado com Ambient Bleed
                item(key = "hero_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 52.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Hero quadrado (aspectRatio 1f, padding lateral AuroSpace.ScreenMargin, flip 3D em álbuns)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AuroSpace.ScreenMargin)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .then(
                                        if (canFlip) {
                                            Modifier.pointerInput(groupTitle) {
                                                var totalDragX = 0f
                                                detectHorizontalDragGestures(
                                                    onDragStart = { totalDragX = 0f },
                                                    onHorizontalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        totalDragX += dragAmount
                                                    },
                                                    onDragEnd = {
                                                        if (abs(totalDragX) > 36f) {
                                                            AuroHaptics.click(view)
                                                            isFlipped = !isFlipped
                                                        }
                                                    }
                                                )
                                            }
                                        } else Modifier
                                    )
                                    .graphicsLayer {
                                        rotationY = flipRotation
                                        cameraDistance = 12f * density.density
                                    }
                                    .clip(RectangleShape)
                                    .background(AuroColor.SurfaceCard)
                            ) {
                                if (canFlip && flipRotation > 90f) {
                                    // Verso do Álbum com Anotações
                                    val albumYear = remember(tracks) {
                                        tracks.mapNotNull { t -> t.year.takeIf { it > 0 } }.minOrNull() ?: 0
                                    }
                                    val albumFormat = remember(tracks) {
                                        tracks.firstOrNull { it.formatTag.isNotBlank() }?.formatTag ?: ""
                                    }
                                    val footerInfo = remember(albumYear, albumFormat, totalDurationMs) {
                                        listOfNotNull(
                                            albumYear.takeIf { it > 0 }?.toString(),
                                            albumFormat.takeIf { it.isNotBlank() },
                                            totalDurationMs.takeIf { it > 0 }?.let { formatTimeMs(it) }
                                        ).joinToString("  ·  ")
                                    }
                                    val savedNotes = albumMeta?.notes?.trim().orEmpty()

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { rotationY = 180f }
                                            .background(Color(0xFF141416))
                                            .border(0.75.dp, Color(0xFF242428), RectangleShape)
                                            .padding(20.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.album_notes_title),
                                                style = AuroType.MicroLabel.copy(
                                                    color = AuroColor.TextSecondary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    letterSpacing = 0.6.sp
                                                )
                                            )
                                            IconButton(
                                                onClick = {
                                                    AuroHaptics.click(view)
                                                    showNotesDialog = true
                                                },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = stringResource(R.string.album_notes_action),
                                                    tint = Color.White.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .clickable {
                                                    AuroHaptics.click(view)
                                                    showNotesDialog = true
                                                }
                                                .padding(vertical = 8.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = if (savedNotes.isNotEmpty()) {
                                                    savedNotes
                                                } else {
                                                    stringResource(R.string.album_notes_placeholder)
                                                },
                                                style = AuroType.Body.copy(
                                                    fontSize = 14.sp,
                                                    lineHeight = 20.sp,
                                                    color = if (savedNotes.isNotEmpty()) AuroColor.TextPrimary else AuroColor.TextSecondary
                                                )
                                            )
                                        }

                                        if (footerInfo.isNotEmpty()) {
                                            Text(
                                                text = footerInfo,
                                                style = AuroType.MicroLabel.copy(
                                                    color = AuroColor.TextSecondary,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontFeatureSettings = "tnum"
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                } else {
                                    // Frente da Capa
                                    if (category == LibraryCategory.ALBUMS) {
                                        AlbumArtworkImage(
                                            albumName = groupTitle,
                                            customMediaPath = customAlbumPath,
                                            isGif = isAlbumGif,
                                            fallbackTrack = firstTrack,
                                            shape = RectangleShape,
                                            mediaStamp = albumMediaStamp,
                                            targetSizeDp = 600,
                                            preloadedFallbackBitmap = heroCover,
                                            customMedia = customMedia,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (category == LibraryCategory.PLAYLISTS) {
                                        PlaylistArtworkImage(
                                            playlistName = groupTitle,
                                            customMedia = customMedia,
                                            legacyCoverPath = playlistCoverPath.orEmpty(),
                                            mosaicTracks = mosaicTracks,
                                            fallbackTrack = firstTrack,
                                            shape = RectangleShape,
                                            mediaStamp = (customMediaVersion.toLong() + albumMediaStamp),
                                            targetSizeDp = 600,
                                            useMonogramFallback = true,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (heroCover != null) {
                                        Image(
                                            bitmap = heroCover.asImageBitmap(),
                                            contentDescription = groupTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (category == LibraryCategory.TOP_RATED) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.radialGradient(
                                                        colors = listOf(Color(0xFFD04565), Color(0xFF2A0E18))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Favorite,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(64.dp)
                                            )
                                        }
                                    } else if (category == LibraryCategory.MOST_PLAYED) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.radialGradient(
                                                        colors = listOf(Color(0xFF6F4BF0), Color(0xFF1A1340))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Whatshot,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(64.dp)
                                            )
                                        }
                                    } else if (category == LibraryCategory.ARTISTS || category == LibraryCategory.ALBUM_ARTISTS || category == LibraryCategory.PLAYLISTS) {
                                        ArtistMonogram(
                                            name = groupTitle,
                                            modifier = Modifier.fillMaxSize(),
                                            shape = AuroSquircle
                                        )
                                    } else {
                                        AuroCoverPlaceholder(
                                            modifier = Modifier.fillMaxSize(),
                                            glass = false,
                                            shape = AuroSquircle
                                        )
                                    }

                                    if (tracks.isNotEmpty()) {
                                        TranslucentPlayButton(
                                            isPlaying = false,
                                            size = 56.dp,
                                            containerColor = Color(0xBD121215),
                                            onClick = onPlayAll,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Nome Hero (HeroName = 32sp Bold) centralizado
                        val displayGroupTitle = if (category == LibraryCategory.ARTISTS || category == LibraryCategory.ALBUM_ARTISTS) {
                            formatArtistsDisplay(groupTitle, stringResource(R.string.unknown_artist))
                        } else {
                            groupTitle
                        }
                        Text(
                            text = displayGroupTitle,
                            style = AuroType.HeroName,
                            color = AuroColor.TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = AuroSpace.ScreenMargin)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Subtítulo e contagem centralizado, 13sp branco 55%
                        val singularLabel = when (category) {
                            LibraryCategory.ALBUMS -> stringResource(R.string.group_subtitle_album)
                            LibraryCategory.ARTISTS -> stringResource(R.string.group_subtitle_artist)
                            LibraryCategory.ALBUM_ARTISTS -> stringResource(R.string.group_subtitle_album_artist)
                            LibraryCategory.GENRES -> stringResource(R.string.group_subtitle_genre)
                            LibraryCategory.YEARS -> stringResource(R.string.group_subtitle_year)
                            LibraryCategory.COMPOSERS -> stringResource(R.string.group_subtitle_composer)
                            LibraryCategory.PLAYLISTS -> stringResource(R.string.group_subtitle_playlist)
                            LibraryCategory.MOST_PLAYED, LibraryCategory.TOP_RATED -> stringResource(R.string.group_subtitle_smart_playlist)
                            else -> stringResource(category.getTitleRes())
                        }
                        val countDurationText = if (tracks.isEmpty()) {
                            formatTracksCount(0)
                        } else {
                            "${formatTracksCount(tracks.size)}  ·  ${formatTimeMs(totalDurationMs)}"
                        }
                        Text(
                            text = "$singularLabel  ·  $countDurationText",
                            style = AuroType.Subtitle.copy(
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.55f),
                                fontFeatureSettings = "tnum"
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = AuroSpace.ScreenMargin)
                        )

                        if (tracks.isEmpty()) {
                            Spacer(modifier = Modifier.height(28.dp))
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable {
                                        AuroHaptics.click(view)
                                        onAddTracksToPlaylist?.invoke()
                                    }
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.playlist_add_tracks),
                                    style = AuroType.Body.copy(
                                        color = Color.Black,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(20.dp))

                            // Ações primárias explícitas: Reproduzir & Aleatório
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AuroSpace.ScreenMargin),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable {
                                            AuroHaptics.click(view)
                                            onPlayAll()
                                        },
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.play_all),
                                        style = AuroType.Label.copy(
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .border(0.75.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                                        .clickable {
                                            AuroHaptics.click(view)
                                            onShuffleAll()
                                        },
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
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
                                        style = AuroType.Label.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            // Cabeçalho da seção com ação rápida de Adicionar (se playlist)
                            EditorialSectionHeader(
                                title = if (category == LibraryCategory.ALBUMS) {
                                    stringResource(R.string.album_tracks_section)
                                } else {
                                    stringResource(R.string.top_songs)
                                },
                                actionLabel = if (category == LibraryCategory.PLAYLISTS && onAddTracksToPlaylist != null) {
                                    stringResource(R.string.playlist_add_short)
                                } else null,
                                onAction = if (category == LibraryCategory.PLAYLISTS && onAddTracksToPlaylist != null) {
                                    {
                                        AuroHaptics.click(view)
                                        onAddTracksToPlaylist.invoke()
                                    }
                                } else ({})
                            )
                        }
                    }
                }

                // Lista de Faixas sem NENHUM divisor
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    AuroTrackRow(
                        track = track,
                        trackIndex = index,
                        isPlayingThis = track.id == currentPlayingId,
                        hasError = errorTrackIds.contains(track.id),
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedTrackIds.contains(track.id),
                        showFormatTag = showFileFormat,
                        showPlayCount = category == LibraryCategory.MOST_PLAYED,
                        showAlbumInSubtitle = category != LibraryCategory.ALBUMS,
                        trackNumber = if (category == LibraryCategory.ALBUMS) track.trackNumber else null,
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelectTrack(track)
                            } else {
                                onTrackClick(track, index)
                            }
                        },
                        onLongClick = {
                            if (isSelectionMode) {
                                onToggleSelectTrack(track)
                            } else {
                                onTrackLongClick(track)
                            }
                        },
                        onMenuClick = { onTrackMenuClick(track) }
                    )
                }
            }
        }

        // Top Bar Limpa com proteção sutil sobre capas claras
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.36f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .clickable {
                        AuroHaptics.click(view)
                        onBackToCategoryList()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPinned) Color.White.copy(alpha = 0.22f)
                            else Color.Black.copy(alpha = 0.36f)
                        )
                        .border(
                            0.5.dp,
                            if (isPinned) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.14f),
                            CircleShape
                        )
                        .clickable {
                            AuroHaptics.click(view)
                            onTogglePin()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(if (isPinned) R.string.action_unpin else R.string.action_pin),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (category == LibraryCategory.PLAYLISTS || category == LibraryCategory.ALBUMS) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.36f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                .clickable {
                                    AuroHaptics.click(view)
                                    showMenu = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.cd_more_options),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(AuroColor.SurfaceGrouped)
                        ) {
                            if (category == LibraryCategory.PLAYLISTS) {
                                if (onCustomizeMedia != null) {
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
                                            onCustomizeMedia()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.playlist_add_tracks),
                                            style = AuroType.Body,
                                            color = AuroColor.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onAddTracksToPlaylist?.invoke()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.playlist_edit_action),
                                            style = AuroType.Body,
                                            color = AuroColor.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onEditPlaylist?.invoke()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.playlist_delete_action),
                                            style = AuroType.Body,
                                            color = AuroColor.Danger
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDeletePlaylist?.invoke()
                                    }
                                )
                            } else if (category == LibraryCategory.ALBUMS) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.album_change_cover),
                                            style = AuroType.Body,
                                            color = AuroColor.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        (onCustomizeMedia ?: onChangeAlbumCover)?.invoke()
                                    }
                                )
                                if (customAlbumPath != null || customMedia != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.album_remove_cover),
                                                style = AuroType.Body,
                                                color = AuroColor.Danger
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            onRemoveAlbumCover?.invoke()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.album_notes_action),
                                            style = AuroType.Body,
                                            color = AuroColor.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        isFlipped = true
                                        showNotesDialog = true
                                    }
                                )
                                if (onAddAlbumToCollection != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.album_add_to_collection),
                                                style = AuroType.Body,
                                                color = AuroColor.TextPrimary
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            onAddAlbumToCollection()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showNotesDialog && category == LibraryCategory.ALBUMS) {
            AuroAlbumNotesDialog(
                initialNotes = albumMeta?.notes ?: "",
                onSave = { trimmedNotes ->
                    showNotesDialog = false
                    onSaveAlbumNotes?.invoke(trimmedNotes)
                },
                onDismiss = { showNotesDialog = false }
            )
        }

        // FastScroller translúcido na margem direita (dinâmico abaixo do Hero, apenas se houver 20+ faixas)
        if (tracks.size >= 20 && (maxHeight - fastScrollerTopDp - bottomInset > 180.dp)) {
            AuroAlphabetFastScroller(
                onLetterSelected = { letter ->
                    val targetIndex = tracks.indexOfFirst { matchesFirstLetter(it.title, letter) }
                    if (targetIndex >= 0) {
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex + 1)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = fastScrollerTopDp, bottom = bottomInset + 30.dp, end = 2.dp)
            )
        }
    }
}

@Composable
internal fun AuroAlbumNotesDialog(
    initialNotes: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draftNotes by remember(initialNotes) { mutableStateOf(initialNotes) }
    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = stringResource(R.string.album_notes_action),
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                AuroDialogTextButton(
                    text = stringResource(R.string.save),
                    onClick = { onSave(draftNotes.trim()) }
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                if (draftNotes.isEmpty()) {
                    Text(
                        text = stringResource(R.string.album_notes_hint),
                        style = AuroType.Body.copy(color = AuroColor.TextTertiary)
                    )
                }
                BasicTextField(
                    value = draftNotes,
                    onValueChange = { draftNotes = it },
                    textStyle = AuroType.Body.copy(
                        color = AuroColor.TextPrimary,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
