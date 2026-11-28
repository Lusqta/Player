package com.lucas.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.BuildConfig
import com.lucas.player.R
import com.lucas.player.data.LyricsManager
import com.lucas.player.model.AlbumCollection
import com.lucas.player.model.SortMode
import com.lucas.player.model.Track
import com.lucas.player.model.UserPlaylist
import com.lucas.player.model.formatArtistsDisplay
import com.lucas.player.model.getDisplayNameRes
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.delay

/**
 * AURŌ DESIGN SYSTEM v3 - SOBREPOSIÇÕES (FASE 7.5)
 * - Scrim preto a 72% via AuroOverlay.
 * - AuroDialogContainer: card único de AuroGlass(Card) raio 28, largura da tela menos 56dp (máx. 360), padding 24.
 *   Título Subtitle; opções de 48dp (ícone outlined 20dp e Body); ações como botões de texto Label em negrito,
 *   accent A, alinhados à direita, altura mínima 48dp; ação destrutiva em AuroColor.Danger.
 * - AuroSheetContainer: cantos superiores de 28dp, alça de 36x4dp em branco a 24%, itens de 56dp, altura máxima 80% da tela.
 * - Um único vidro por overlay (sem vidro dentro de vidro).
 * - Campos de texto com BasicTextField e borda fina 1dp (0x2EFFFFFF).
 */

@Composable
fun AuroDialogContainer(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val dialogWidth = (screenWidth - 56.dp).coerceAtMost(360.dp)

    Box(
        modifier = modifier
            .width(dialogWidth)
            .padding(vertical = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(AuroColor.SurfaceGrouped)
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = title,
                style = AuroType.Subtitle.copy(fontWeight = FontWeight.Bold),
                color = AuroColor.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = AuroType.Label.copy(color = AuroColor.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            content()

            if (actions != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

@Composable
fun AuroDialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = AuroHeight.TouchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                AuroHaptics.click(view)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AuroType.Label.copy(
                color = if (isDestructive) AuroColor.Danger else Color.White,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun AuroSheetContainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.80f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(AuroColor.SurfaceGrouped)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Alça de 36x4dp em branco a 24%
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.24f))
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!title.isNullOrEmpty()) {
                Text(
                    text = title,
                    style = AuroType.Subtitle.copy(fontWeight = FontWeight.Bold),
                    color = AuroColor.TextPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = AuroType.Caption.copy(color = AuroColor.TextTertiary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. MENU CONTEXTUAL DA FAIXA (AURŌ TRACK CONTEXT MENU)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroTrackContextMenuDialog(
    track: Track,
    onPlayNext: () -> Unit,
    onEnqueueEnd: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSetRating: (Int) -> Unit,
    onShowInfo: () -> Unit,
    onStartSelection: () -> Unit = {},
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = track.title,
            subtitle = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss
                )
            }
        ) {
            // Classificação por estrelas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.context_rating),
                    style = AuroType.Body.copy(color = AuroColor.TextSecondary)
                )
                AuroRatingBar(
                    rating = track.rating,
                    onRatingSelected = onSetRating
                )
            }

            HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            AuroDialogOption(
                icon = Icons.Outlined.Checklist,
                title = stringResource(R.string.selection_select),
                onClick = {
                    onDismiss()
                    onStartSelection()
                }
            )
            AuroDialogOption(
                icon = Icons.AutoMirrored.Outlined.QueueMusic,
                title = stringResource(R.string.context_play_next),
                onClick = onPlayNext
            )
            AuroDialogOption(
                icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                title = stringResource(R.string.context_add_queue),
                onClick = onEnqueueEnd
            )
            AuroDialogOption(
                icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                title = stringResource(R.string.context_add_playlist),
                onClick = onAddToPlaylist
            )
            onRemoveFromPlaylist?.let { removeAction ->
                AuroDialogOption(
                    icon = Icons.Outlined.Delete,
                    title = stringResource(R.string.playlist_remove_track),
                    onClick = removeAction
                )
            }
            AuroDialogOption(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.context_track_info),
                onClick = onShowInfo
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. DIÁLOGO DE ORDENAÇÃO (AURŌ SORT DIALOG)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroSortDialog(
    currentSortMode: SortMode,
    onSelectSort: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = stringResource(R.string.sort_title),
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss
                )
            }
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                items(SortMode.entries.toTypedArray()) { mode ->
                    val isSelected = mode == currentSortMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AuroHeight.DialogOption)
                            .clickable {
                                onSelectSort(mode)
                                onDismiss()
                            }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color.White,
                                unselectedColor = AuroColor.TextTertiary
                            )
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(mode.getDisplayNameRes()),
                            style = AuroType.Body.copy(
                                color = if (isSelected) Color.White else AuroColor.TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. DIÁLOGO DE DETALHES DA FAIXA (AURŌ TRACK INFO)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroTrackInfoDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = stringResource(R.string.track_info_title),
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { AuroInfoRow(stringResource(R.string.track_info_track_title), track.title) }
                item { AuroInfoRow(stringResource(R.string.track_info_artist), track.artist.ifEmpty { stringResource(R.string.unknown_artist) }) }
                item { AuroInfoRow(stringResource(R.string.track_info_album), track.album.ifEmpty { stringResource(R.string.unknown_album) }) }
                item { AuroInfoRow(stringResource(R.string.track_info_duration), formatTimeMs(track.durationMs)) }
                item { AuroInfoRow(stringResource(R.string.track_info_format), track.formatTag.uppercase()) }
                item { AuroInfoRow(stringResource(R.string.track_info_sample_rate), if (track.sampleRateHz > 0) "${track.sampleRateHz} Hz" else "N/A") }
                item { AuroInfoRow(stringResource(R.string.track_info_bitrate), if (track.bitrateKbps > 0) "${track.bitrateKbps} kbps" else "N/A") }
                item { AuroInfoRow(stringResource(R.string.track_info_path), track.path) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. DIÁLOGO ADICIONAR À PLAYLIST (AURŌ ADD TO PLAYLIST)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroAddToPlaylistDialog(
    track: Track,
    playlists: List<UserPlaylist>,
    onCreatePlaylist: (String) -> Unit,
    onSelectPlaylist: (UserPlaylist) -> Unit,
    onDismiss: () -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val view = LocalView.current

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = stringResource(R.string.context_add_playlist),
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss
                )
            }
        ) {
            if (isCreatingNew) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AuroHeight.DialogInputField)
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (newPlaylistName.isEmpty()) {
                            Text(
                                text = stringResource(R.string.new_playlist_hint),
                                style = AuroType.Body.copy(color = AuroColor.TextTertiary)
                            )
                        }
                        BasicTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            singleLine = true,
                            textStyle = AuroType.Body.copy(color = AuroColor.TextPrimary),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AuroDialogTextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { isCreatingNew = false }
                        )
                        AuroDialogTextButton(
                            text = stringResource(R.string.create),
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    onCreatePlaylist(newPlaylistName.trim())
                                    isCreatingNew = false
                                }
                            }
                        )
                    }
                }
            } else {
                AuroDialogOption(
                    icon = Icons.Outlined.Add,
                    title = stringResource(R.string.create_playlist),
                    onClick = {
                        AuroHaptics.click(view)
                        isCreatingNew = true
                    }
                )

                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    items(playlists, key = { it.id }) { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(AuroHeight.DialogOption)
                                .clickable {
                                    AuroHaptics.click(view)
                                    onSelectPlaylist(pl)
                                    onDismiss()
                                }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                                contentDescription = null,
                                tint = AuroColor.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = pl.name,
                                style = AuroType.Body,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatTracksCount(pl.trackCount),
                                style = AuroType.Caption.copy(
                                    color = AuroColor.TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontFeatureSettings = "tnum"
                                )
                            )
                        }
                        HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. DIÁLOGO DO TIMER DE SONO (AURŌ SLEEP TIMER)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroSleepTimerDialog(
    remainingSeconds: Int = 0,
    onSetTimerMinutes: (Int, Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(15, 30, 45, 60, 90, 120)
    var stopAtTrackEnd by remember { mutableStateOf(false) }

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = stringResource(R.string.sleep_timer_title),
            subtitle = if (remainingSeconds > 0) {
                stringResource(R.string.sleep_timer_active_desc, remainingSeconds / 60, remainingSeconds % 60)
            } else null,
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss
                )
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { stopAtTrackEnd = !stopAtTrackEnd }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = stopAtTrackEnd,
                    onCheckedChange = { stopAtTrackEnd = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.White,
                        checkmarkColor = Color.Black,
                        uncheckedColor = AuroColor.TextSecondary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.sleep_timer_stop_at_end),
                    style = AuroType.Body
                )
            }

            HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            options.forEach { minutes ->
                AuroDialogOption(
                    icon = Icons.Outlined.Schedule,
                    title = "$minutes min",
                    onClick = {
                        onSetTimerMinutes(minutes, stopAtTrackEnd)
                        onDismiss()
                    }
                )
            }

            if (remainingSeconds > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                AuroDialogOption(
                    icon = Icons.Outlined.Cancel,
                    title = stringResource(R.string.snackbar_timer_off),
                    isDestructive = true,
                    onClick = {
                        onCancelTimer()
                        onDismiss()
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. MENU SHEET PRINCIPAL (AURŌ MENU SHEET)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroPowerampMenuSheet(
    onOpenQueue: () -> Unit = {},
    onOpenEqualizer: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenSleepTimer: () -> Unit,
    onOpenAudioInfo: () -> Unit,
    onRescanLibrary: () -> Unit,
    onOpenAbout: () -> Unit,
    onDismiss: () -> Unit
) {
    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.BottomSheet
    ) {
        AuroSheetContainer(
            title = "Menu",
            onDismiss = onDismiss
        ) {
            AuroSheetItem(
                icon = Icons.Outlined.QueueMusic,
                title = stringResource(R.string.menu_queue),
                onClick = {
                    onDismiss()
                    onOpenQueue()
                }
            )
            AuroSheetItem(
                icon = Icons.Outlined.GraphicEq,
                title = stringResource(R.string.tab_equalizer),
                onClick = {
                    onDismiss()
                    onOpenEqualizer()
                }
            )
            AuroSheetItem(
                icon = Icons.Outlined.Settings,
                title = stringResource(R.string.settings_title),
                onClick = {
                    onDismiss()
                    onOpenSettings()
                }
            )
            AuroSheetItem(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.menu_sleep_timer),
                onClick = {
                    onDismiss()
                    onOpenSleepTimer()
                }
            )
            AuroSheetItem(
                icon = Icons.Outlined.Speed,
                title = stringResource(R.string.audio_engine_title),
                onClick = {
                    onDismiss()
                    onOpenAudioInfo()
                }
            )
            HorizontalDivider(
                color = AuroColor.Hairline,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            AuroSheetItem(
                icon = Icons.Outlined.Refresh,
                title = stringResource(R.string.settings_rescan_library),
                onClick = {
                    onDismiss()
                    onRescanLibrary()
                }
            )
            AuroSheetItem(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.menu_about),
                onClick = {
                    onDismiss()
                    onOpenAbout()
                }
            )
        }
    }
}

@Composable
private fun AuroSheetItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                AuroHaptics.click(view)
                onClick()
            }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AuroColor.TextPrimary.copy(alpha = 0.80f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            style = AuroType.Body.copy(fontWeight = FontWeight.Medium),
            color = AuroColor.TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. DIÁLOGO DO MOTOR DE ÁUDIO (AURŌ AUDIO ENGINE)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroAudioEngineDialog(
    sampleRate: Int = 48000,
    bufferSizeFrames: Int = 192,
    onDismiss: () -> Unit
) {
    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = stringResource(R.string.audio_engine_title),
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.ok),
                    onClick = onDismiss
                )
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AuroInfoRow(stringResource(R.string.audio_engine_pipeline), stringResource(R.string.audio_engine_pipeline_val))
                AuroInfoRow(stringResource(R.string.audio_engine_driver), stringResource(R.string.audio_engine_driver_val))
                AuroInfoRow(stringResource(R.string.audio_engine_latency), stringResource(R.string.audio_engine_latency_val))
                AuroInfoRow(stringResource(R.string.audio_engine_sharing), stringResource(R.string.audio_engine_sharing_val))
                AuroInfoRow(stringResource(R.string.audio_engine_sample_rate), "$sampleRate Hz")
                AuroInfoRow(stringResource(R.string.audio_engine_buffer), stringResource(R.string.audio_engine_buffer_val, bufferSizeFrames))
                AuroInfoRow(stringResource(R.string.audio_engine_fp), stringResource(R.string.audio_engine_fp_val))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. TELA DE LETRAS (AURŌ LYRICS SHEET)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroLyricsSheet(
    track: Track,
    lyrics: LyricsManager.Lyrics?,
    currentProgressMs: Long,
    onSeekToMs: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    var isUserScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isUserScrolling = true
        } else if (isUserScrolling) {
            delay(3000L)
            isUserScrolling = false
        }
    }

    val activeLineIndex = remember(lyrics, currentProgressMs) {
        if (lyrics != null && lyrics.isSynced && lyrics.lines.isNotEmpty()) {
            val idx = lyrics.lines.indexOfLast { it.timeMs <= currentProgressMs }
            if (idx >= 0) idx else 0
        } else -1
    }

    LaunchedEffect(activeLineIndex, isUserScrolling) {
        if (!isUserScrolling && activeLineIndex >= 0) {
            val targetScroll = (activeLineIndex - 3).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.BottomSheet
    ) {
        AuroSheetContainer(
            title = stringResource(R.string.lyrics_sheet_title),
            subtitle = "${track.title} • ${lyrics?.source ?: stringResource(R.string.lyrics_no_local)}",
            onDismiss = onDismiss
        ) {
            if (lyrics == null || lyrics.lines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.lyrics_none_found),
                            style = AuroType.Subtitle.copy(fontWeight = FontWeight.Medium)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.lyrics_lrc_hint),
                            style = AuroType.Label.copy(
                                color = AuroColor.TextSecondary,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val isCurrent = index == activeLineIndex
                        Text(
                            text = line.text,
                            style = (if (isCurrent) AuroType.Subtitle else AuroType.Body).copy(
                                color = if (isCurrent) Color.White else AuroColor.TextTertiary,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (lyrics.isSynced) {
                                        onSeekToMs(line.timeMs)
                                    }
                                }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 9. DIÁLOGO PARA SALVAR PRESET (AURŌ SAVE PRESET)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroSavePresetDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var presetName by remember { mutableStateOf("") }

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = stringResource(R.string.save_preset_title),
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                AuroDialogTextButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        if (presetName.isNotBlank()) {
                            onSave(presetName.trim())
                        }
                    }
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AuroHeight.DialogInputField)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (presetName.isEmpty()) {
                    Text(
                        text = stringResource(R.string.preset_name_hint),
                        style = AuroType.Body.copy(color = AuroColor.TextTertiary)
                    )
                }
                BasicTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    singleLine = true,
                    textStyle = AuroType.Body.copy(color = AuroColor.TextPrimary),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 9.1 DIÁLOGO DE CRIAÇÃO DE PLAYLIST VAZIA
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroCreatePlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var playlistName by remember { mutableStateOf("") }

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = stringResource(R.string.create_playlist_title),
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                AuroDialogTextButton(
                    text = stringResource(R.string.create),
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            onCreate(playlistName.trim())
                        }
                    }
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AuroHeight.DialogInputField)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (playlistName.isEmpty()) {
                    Text(
                        text = stringResource(R.string.new_playlist_hint),
                        style = AuroType.Body.copy(color = AuroColor.TextTertiary)
                    )
                }
                BasicTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    singleLine = true,
                    textStyle = AuroType.Body.copy(color = AuroColor.TextPrimary),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 9.2 DIÁLOGO DE EDIÇÃO DE PLAYLIST (NOME E CAPA)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroEditPlaylistDialog(
    playlist: UserPlaylist,
    fallbackTrack: Track? = null,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onSave: (newName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentName by remember(playlist.name) { mutableStateOf(playlist.name) }
    val view = LocalView.current
    val coverBitmap = rememberPlaylistCover(playlist.coverPath, fallbackTrack, 240)

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = stringResource(R.string.playlist_edit_title),
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                AuroDialogTextButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        if (currentName.isNotBlank()) {
                            onSave(currentName.trim())
                        }
                    }
                )
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Prévia da capa clicável (120.dp, AuroSquircle)
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(AuroSquircle)
                        .background(AuroColor.SurfaceCard)
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), AuroSquircle)
                        .clickable {
                            AuroHaptics.click(view)
                            onPickCover()
                        }
                ) {
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap.asImageBitmap(),
                            contentDescription = currentName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ArtistMonogram(
                            name = currentName,
                            modifier = Modifier.fillMaxSize(),
                            shape = AuroSquircle
                        )
                    }

                    // Overlay discreto de ícone Edit no canto inferior direito
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xBD121215))
                            .border(0.5.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.playlist_change_cover),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Botão "Remover capa" se houver capa customizada
                if (playlist.coverPath.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clickable {
                                AuroHaptics.click(view)
                                onRemoveCover()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.playlist_remove_cover),
                            style = AuroType.Caption.copy(
                                fontSize = 12.sp,
                                color = AuroColor.Danger,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Campo de nome
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AuroHeight.DialogInputField)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (currentName.isEmpty()) {
                        Text(
                            text = stringResource(R.string.new_playlist_hint),
                            style = AuroType.Body.copy(color = AuroColor.TextTertiary)
                        )
                    }
                    BasicTextField(
                        value = currentName,
                        onValueChange = { currentName = it },
                        singleLine = true,
                        textStyle = AuroType.Body.copy(color = AuroColor.TextPrimary),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 9.3 BOTTOM SHEET SELETOR DE MÚSICAS EM LOTE (TRACK PICKER)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroPlaylistTrackPickerSheet(
    allTracks: List<Track>,
    existingTrackIds: Set<Long>,
    onAddTracks: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }
    val view = LocalView.current

    val filteredTracks = remember(allTracks, searchQuery) {
        if (searchQuery.isBlank()) {
            allTracks
        } else {
            val q = searchQuery.trim().lowercase()
            allTracks.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
    }

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.BottomSheet
    ) {
        AuroSheetContainer(
            title = stringResource(R.string.playlist_add_tracks_title),
            onDismiss = onDismiss
        ) {
            // Campo de busca interno (pílula SurfaceCard, 44dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(AuroColor.SurfaceCard)
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                style = AuroType.Body.copy(color = AuroColor.TextTertiary)
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = AuroType.Body.copy(color = AuroColor.TextPrimary),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = AuroColor.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // LazyColumn com faixas selecionáveis
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(filteredTracks, key = { it.id }) { track ->
                    val isAlreadyIn = existingTrackIds.contains(track.id)
                    val isSelected = selectedIds.contains(track.id)
                    val coverBitmap = rememberTrackCover(track, 80)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable(enabled = !isAlreadyIn) {
                                AuroHaptics.click(view)
                                if (isSelected) {
                                    selectedIds.remove(track.id)
                                } else {
                                    selectedIds.add(track.id)
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Checkbox circular 22.dp à esquerda
                        if (isAlreadyIn) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else Color.Transparent)
                                    .border(
                                        1.5.dp,
                                        if (isSelected) Color.White else Color.White.copy(alpha = 0.35f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Miniatura 36.dp
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(AuroSquircle)
                                .background(AuroColor.SurfaceCard)
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
                                    shape = AuroSquircle
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Título e artista
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = AuroType.Body.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = if (isAlreadyIn) AuroColor.TextTertiary else AuroColor.TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatArtistsDisplay(track.artist, stringResource(R.string.unknown_artist)),
                                style = AuroType.Caption.copy(
                                    color = if (isAlreadyIn) AuroColor.TextTertiary.copy(alpha = 0.6f) else AuroColor.TextSecondary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Selo "Na playlist"
                        if (isAlreadyIn) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.playlist_already_added),
                                    style = AuroType.MicroLabel.copy(
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.45f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Rodapé fixo com botão pílula branco (altura 48dp, CircleShape)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(if (selectedIds.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.20f))
                    .clickable(enabled = selectedIds.isNotEmpty()) {
                        AuroHaptics.click(view)
                        onAddTracks(selectedIds.toList())
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedIds.isNotEmpty()) {
                        "${stringResource(R.string.playlist_add_short)} (${selectedIds.size})"
                    } else {
                        stringResource(R.string.playlist_add_short)
                    },
                    style = AuroType.Body.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedIds.isNotEmpty()) Color.Black else Color.White.copy(alpha = 0.40f)
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 10. DIÁLOGO DE CONFIRMAÇÃO DE EXCLUSÃO (AURŌ CONFIRM DELETE)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = title,
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                AuroDialogTextButton(
                    text = stringResource(R.string.delete),
                    isDestructive = true,
                    onClick = {
                        onConfirm()
                        onDismiss()
                    }
                )
            }
        ) {
            Text(
                text = message,
                style = AuroType.Body.copy(color = AuroColor.TextSecondary)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 11. DIÁLOGO SOBRE O PLAYER (AURŌ ABOUT)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroAboutDialog(
    onDismiss: () -> Unit
) {
    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = stringResource(R.string.about_app_title),
            subtitle = stringResource(R.string.about_app_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss
                )
            }
        ) {
            Text(
                text = stringResource(R.string.about_app_specs),
                style = AuroType.Body.copy(color = AuroColor.TextSecondary)
            )
        }
    }
}

@Composable
fun AuroDialogOption(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    trailingText: String? = null,
    isDestructive: Boolean = false
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AuroHeight.DialogOption)
            .clickable {
                AuroHaptics.click(view)
                onClick()
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = AuroType.Body.copy(
                color = if (isDestructive) AuroColor.Danger else AuroColor.TextPrimary
            ),
            modifier = Modifier.weight(1f)
        )
        if (!trailingText.isNullOrEmpty()) {
            Text(
                text = trailingText,
                style = AuroType.Caption.copy(
                    color = AuroColor.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum"
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) AuroColor.Danger else AuroColor.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AuroInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, style = AuroType.Label.copy(color = AuroColor.TextSecondary), modifier = Modifier.width(130.dp))
    Text(
            text = value,
            style = AuroType.Body.copy(color = AuroColor.TextPrimary),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 11. DIÁLOGOS DE COLEÇÕES DE ÁLBUNS (Fase 4B)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AuroCreateAlbumCollectionDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = stringResource(R.string.album_collection_create_title),
            onDismiss = onDismiss,
            actions = {
                AuroDialogTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                AuroDialogTextButton(
                    text = stringResource(R.string.create),
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreate(name.trim())
                        }
                    }
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AuroHeight.DialogInputField)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (name.isEmpty()) {
                    Text(
                        text = stringResource(R.string.album_collection_name_hint),
                        style = AuroType.Body.copy(color = AuroColor.TextTertiary)
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = AuroType.Body.copy(color = AuroColor.TextPrimary),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun AuroAddAlbumToCollectionDialog(
    albumName: String,
    collections: List<AlbumCollection>,
    onCreateCollection: (String) -> Unit,
    onSelectCollection: (AlbumCollection) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    var isCreatingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        AuroDialogContainer(
            title = stringResource(R.string.album_add_to_collection),
            subtitle = albumName,
            onDismiss = onDismiss
        ) {
            if (isCreatingNew) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AuroHeight.DialogInputField)
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (newName.isEmpty()) {
                            Text(
                                text = stringResource(R.string.album_collection_name_hint),
                                style = AuroType.Body.copy(color = AuroColor.TextTertiary)
                            )
                        }
                        BasicTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            textStyle = AuroType.Body.copy(color = AuroColor.TextPrimary),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AuroDialogTextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { isCreatingNew = false }
                        )
                        AuroDialogTextButton(
                            text = stringResource(R.string.create),
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onCreateCollection(newName.trim())
                                    isCreatingNew = false
                                }
                            }
                        )
                    }
                }
            } else {
                AuroDialogOption(
                    icon = Icons.Outlined.Add,
                    title = stringResource(R.string.album_collection_new),
                    onClick = {
                        AuroHaptics.click(view)
                        isCreatingNew = true
                    }
                )

                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    items(collections, key = { it.id }) { col ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(AuroHeight.DialogOption)
                                .clickable {
                                    AuroHaptics.click(view)
                                    onSelectCollection(col)
                                    onDismiss()
                                }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Album,
                                contentDescription = null,
                                tint = AuroColor.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = col.name,
                                style = AuroType.Body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatAlbumsCount(col.albumCount),
                                style = AuroType.Caption.copy(
                                    color = AuroColor.TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontFeatureSettings = "tnum"
                                )
                            )
                        }
                        HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                    }
                }
            }
        }
    }
}
