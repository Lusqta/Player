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
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
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
    showFileFormat: Boolean = true
) {
    val view = LocalView.current
    val accent = LocalAuroAccent.current.a.value
    val bottomInset = LocalAuroBottomInset.current
    val coroutineScope = rememberCoroutineScope()
    val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }

    val firstTrack = tracks.firstOrNull()
    val heroCover = rememberTrackCover(firstTrack, 400)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        if (tracks.isEmpty()) {
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
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        accent.copy(alpha = 0.20f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .statusBarsPadding()
                            .padding(top = 52.dp, bottom = 16.dp, start = AuroSpace.ScreenMargin, end = AuroSpace.ScreenMargin),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Hero Portrait de até 300dp (raio 32dp) com PlayButton flutuando
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .aspectRatio(1f)
                                .shadow(elevation = 24.dp, shape = RoundedCornerShape(AuroRadius.hero), spotColor = accent.copy(alpha = 0.45f))
                                .clip(RoundedCornerShape(AuroRadius.hero))
                                .background(AuroColor.SurfaceCard)
                        ) {
                            if (heroCover != null) {
                                Image(
                                    bitmap = heroCover.asImageBitmap(),
                                    contentDescription = groupTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AuroCoverPlaceholder(
                                    modifier = Modifier.fillMaxSize(),
                                    glass = false,
                                    shape = RoundedCornerShape(AuroRadius.hero)
                                )
                            }

                            // TranslucentPlayButton (52dp) no canto inferior direito
                            TranslucentPlayButton(
                                isPlaying = false,
                                size = 52.dp,
                                onClick = onPlayAll,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Nome Hero (30sp Bold)
                        Text(
                            text = groupTitle,
                            style = AuroType.HeroName,
                            color = AuroColor.TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Subtítulo e contagem
                        Text(
                            text = "${stringResource(category.getTitleRes())} • ${pluralStringResource(R.plurals.tracks_count, tracks.size, tracks.size)} • ${formatTimeMs(totalDurationMs)}",
                            style = AuroType.Subtitle.copy(
                                color = AuroColor.TextSecondary,
                                fontFeatureSettings = "tnum"
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Cabeçalho da seção com ação de Aleatório
                        EditorialSectionHeader(
                            title = stringResource(R.string.top_songs),
                            actionLabel = stringResource(R.string.shuffle_all),
                            onAction = onShuffleAll
                        )
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

            // Top Bar Transparente com botões circulares flutuantes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = AuroSpace.ScreenMargin, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AuroColor.ControlGlass)
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .clickable {
                            AuroHaptics.click(view)
                            onBackToCategoryList()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AuroColor.ControlGlass)
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .clickable {
                            AuroHaptics.click(view)
                            onTogglePin()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(if (isPinned) R.string.action_unpin else R.string.action_pin),
                        tint = if (isPinned) Color.White else AuroColor.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // FastScroller translúcido na margem direita
            if (tracks.size >= 12) {
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
                        .align(Alignment.CenterEnd)
                        .padding(top = 100.dp, bottom = bottomInset + 30.dp, end = 2.dp)
                )
            }
        }
    }
}
