package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
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
 * TELA DE DRILLDOWN (Apple Music Android 7.0.2 - Hero Header)
 * - Top bar transparente com botões circulares flutuantes de vidro (< e Fixar)
 * - Hero Header com capa centralizada 210dp (raio 12dp + sombra), título 22sp Bold,
 *   subtítulo na cor adaptativa, contagem/duração e 2 botões pílula lado a lado (48dp).
 * - Linhas de faixas estilo Apple Music + FastScroller na margem direita.
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
    val heroCover = rememberTrackCover(firstTrack, 350)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
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
                contentPadding = PaddingValues(bottom = bottomInset + 24.dp)
            ) {
                // Hero Header Centralizado estilo Apple Music
                item(key = "hero_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        accent.copy(alpha = 0.16f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .statusBarsPadding()
                            .padding(top = 56.dp, bottom = 20.dp, start = AuroSpace.ScreenMargin, end = AuroSpace.ScreenMargin),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Capa Centralizada 210dp (12dp radius + sombra elegante)
                        Box(
                            modifier = Modifier
                                .size(210.dp)
                                .shadow(elevation = 18.dp, shape = RoundedCornerShape(AuroRadius.card), spotColor = accent.copy(alpha = 0.5f))
                                .clip(RoundedCornerShape(AuroRadius.card))
                                .background(AuroColor.SurfaceGrouped),
                            contentAlignment = Alignment.Center
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
                                    shape = RoundedCornerShape(AuroRadius.card)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Título 22sp Bold
                        Text(
                            text = groupTitle,
                            style = AuroType.SectionTitle,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Subtítulo na cor de acento adaptativa
                        Text(
                            text = stringResource(category.getTitleRes()),
                            style = AuroType.Subtitle.copy(
                                color = accent,
                                fontWeight = FontWeight.SemiBold
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Contagem e duração total
                        Text(
                            text = "${pluralStringResource(R.plurals.tracks_count, tracks.size, tracks.size)} • ${formatTimeMs(totalDurationMs)}",
                            style = AuroType.Caption.copy(
                                color = AuroColor.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontFeatureSettings = "tnum"
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // 2 Botões pílula lado a lado (48dp, #1C1C1E, ícone + texto na cor adaptativa)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(CircleShape)
                                    .background(AuroColor.SurfaceGrouped)
                                    .clickable {
                                        AuroHaptics.click(view)
                                        onPlayAll()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.play_all),
                                        style = AuroType.Body.copy(
                                            color = accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(CircleShape)
                                    .background(AuroColor.SurfaceGrouped)
                                    .clickable {
                                        AuroHaptics.click(view)
                                        onShuffleAll()
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
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.shuffle_all),
                                        style = AuroType.Body.copy(
                                            color = accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Lista de Faixas com AuroTrackRow
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
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = AuroColor.Separator,
                        thickness = 0.5.dp
                    )
                }
            }

            // Top Bar Transparente com botões circulares flutuantes de vidro
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = AuroSpace.ScreenMargin, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuroGlass(
                    kind = AuroGlassKind.Button,
                    cornerRadius = 20.dp,
                    modifier = Modifier.size(AuroHeight.GlassButton),
                    onClick = {
                        AuroHaptics.click(view)
                        onBackToCategoryList()
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = AuroColor.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AuroGlass(
                    kind = AuroGlassKind.Button,
                    cornerRadius = 20.dp,
                    modifier = Modifier.size(AuroHeight.GlassButton),
                    onClick = {
                        AuroHaptics.click(view)
                        onTogglePin()
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                            contentDescription = stringResource(if (isPinned) R.string.action_unpin else R.string.action_pin),
                            tint = if (isPinned) accent else AuroColor.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
