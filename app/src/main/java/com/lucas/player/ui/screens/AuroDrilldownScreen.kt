package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.lucas.player.model.LibraryCategory
import com.lucas.player.model.Track
import com.lucas.player.model.getTitleRes
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.*
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
    onAddTracksToPlaylist: (() -> Unit)? = null,
    onEditPlaylist: (() -> Unit)? = null,
    onDeletePlaylist: (() -> Unit)? = null
) {
    val view = LocalView.current
    val bottomInset = LocalAuroBottomInset.current
    val coroutineScope = rememberCoroutineScope()
    val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }

    val firstTrack = tracks.firstOrNull()
    val heroCover = if (category == LibraryCategory.PLAYLISTS) {
        rememberPlaylistCover(playlistCoverPath, firstTrack, 400)
    } else {
        rememberTrackCover(firstTrack, 400)
    }
    var groupAccent by remember { mutableStateOf(AuroAccent.Neutral) }
    LaunchedEffect(heroCover) {
        groupAccent = CoverGlow.from(heroCover)
    }

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
                        // Hero quadrado (aspectRatio 1f, padding lateral AuroSpace.ScreenMargin)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AuroSpace.ScreenMargin)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(AuroSquircle)
                                    .background(AuroColor.SurfaceCard)
                            ) {
                                if (heroCover != null) {
                                    Image(
                                        bitmap = heroCover.asImageBitmap(),
                                        contentDescription = groupTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
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
                            else -> stringResource(category.getTitleRes())
                        }
                        val countDurationText = if (tracks.isEmpty()) {
                            "0 faixas"
                        } else {
                            "${pluralStringResource(R.plurals.tracks_count, tracks.size, tracks.size)}  ·  ${formatTimeMs(totalDurationMs)}"
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
                            Spacer(modifier = Modifier.height(28.dp))

                            // Cabeçalho da seção com ação de Aleatório e Adicionar (se playlist)
                            EditorialSectionHeader(
                                title = stringResource(R.string.top_songs),
                                actionLabel = stringResource(R.string.shuffle_all),
                                onAction = onShuffleAll,
                                secondaryActionLabel = if (category == LibraryCategory.PLAYLISTS && onAddTracksToPlaylist != null) {
                                    stringResource(R.string.playlist_add_short)
                                } else null,
                                onSecondaryAction = if (category == LibraryCategory.PLAYLISTS) {
                                    {
                                        AuroHaptics.click(view)
                                        onAddTracksToPlaylist?.invoke()
                                    }
                                } else null
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

            // Top Bar Limpa: Botão voltar (<) e Fixar (Pin) sem círculos
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
                        onBackToCategoryList()
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

                    if (category == LibraryCategory.PLAYLISTS) {
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
                            }
                        }
                    }
                }
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
}
