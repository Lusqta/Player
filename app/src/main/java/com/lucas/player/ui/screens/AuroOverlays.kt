package com.lucas.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.lucas.player.model.SortMode
import com.lucas.player.model.Track
import com.lucas.player.model.UserPlaylist
import com.lucas.player.ui.components.AuroOverlay
import com.lucas.player.ui.components.AuroOverlayType
import com.lucas.player.ui.components.AuroRatingBar
import com.lucas.player.ui.components.formatTimeMs
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.delay

/**
 * AURŌ DESIGN SYSTEM v3 - DIÁLOGOS E SOBREPOSIÇÕES (Fase 2.3 & 2.4)
 * Fundo #000000 absoluto, fios Hairline (0x14FFFFFF), sem cinzas de superfície.
 * Todos os modais e sheets utilizam AuroOverlay com PredictiveBackHandler e molas de movimento.
 */

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
    onDismiss: () -> Unit
) {
    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        Surface(
            color = AuroColor.Black,
            shape = RoundedCornerShape(AuroRadius.card),
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .border(1.dp, AuroColor.Hairline, RoundedCornerShape(AuroRadius.card))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = AuroColor.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist.ifEmpty { "Desconhecido" },
                            color = AuroColor.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fechar",
                            tint = AuroColor.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                // Classificação por estrelas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Classificação:",
                        color = AuroColor.TextSecondary,
                        fontSize = 13.sp
                    )
                    AuroRatingBar(
                        rating = track.rating,
                        onRatingSelected = onSetRating
                    )
                }

                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                AuroDialogOption(
                    icon = Icons.Rounded.Checklist,
                    title = stringResource(R.string.selection_select),
                    onClick = {
                        onDismiss()
                        onStartSelection()
                    }
                )

                AuroDialogOption(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = "Tocar a seguir (Fila prioritária)",
                    onClick = onPlayNext
                )

                AuroDialogOption(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = "Adicionar ao fim da fila",
                    onClick = onEnqueueEnd
                )

                AuroDialogOption(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    title = "Adicionar à lista de reprodução...",
                    onClick = onAddToPlaylist
                )

                AuroDialogOption(
                    icon = Icons.Rounded.Info,
                    title = "Informações da faixa",
                    onClick = onShowInfo
                )
            }
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
        Surface(
            color = AuroColor.Black,
            shape = RoundedCornerShape(AuroRadius.card),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.70f)
                .border(1.dp, AuroColor.Hairline, RoundedCornerShape(AuroRadius.card))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ORDENAR POR",
                        color = AuroColor.TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = AuroColor.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(SortMode.values()) { mode ->
                        val isSelected = mode == currentSortMode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectSort(mode)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = LocalAuroAccent.current.a.value,
                                    unselectedColor = AuroColor.TextTertiary
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = mode.displayName,
                                color = if (isSelected) LocalAuroAccent.current.a.value else AuroColor.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
        Surface(
            color = AuroColor.Black,
            shape = RoundedCornerShape(AuroRadius.card),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f)
                .border(1.dp, AuroColor.Hairline, RoundedCornerShape(AuroRadius.card))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETALHES DA FAIXA",
                        color = AuroColor.TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = AuroColor.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { AuroInfoRow("Título", track.title) }
                    item { AuroInfoRow("Artista", track.artist) }
                    item { AuroInfoRow("Álbum", track.album) }
                    item { AuroInfoRow("Duração", formatTimeMs(track.durationMs)) }
                    item { AuroInfoRow("Formato", track.formatTag.uppercase()) }
                    item { AuroInfoRow("Taxa de Amostragem", if (track.sampleRateHz > 0) "${track.sampleRateHz} Hz" else "N/A") }
                    item { AuroInfoRow("Bitrate", if (track.bitrateKbps > 0) "${track.bitrateKbps} kbps" else "N/A") }
                    item { AuroInfoRow("Caminho", track.path) }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Fechar", color = AuroColor.TextPrimary, fontSize = 14.sp)
                    }
                }
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
    val accent = LocalAuroAccent.current.a.value

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        Surface(
            color = AuroColor.Black,
            shape = RoundedCornerShape(AuroRadius.card),
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .fillMaxHeight(0.70f)
                .border(1.dp, AuroColor.Hairline, RoundedCornerShape(AuroRadius.card))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ADICIONAR À LISTA",
                            color = AuroColor.TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = track.title,
                            color = AuroColor.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = AuroColor.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

                if (isCreatingNew) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AuroColor.Hairline, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text("NOVA LISTA DE REPRODUÇÃO", color = AuroColor.TextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("Nome da lista...", color = AuroColor.TextTertiary, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = AuroColor.Hairline,
                                focusedTextColor = AuroColor.TextPrimary,
                                unfocusedTextColor = AuroColor.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { isCreatingNew = false }) {
                                Text("Cancelar", color = AuroColor.TextSecondary, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            AuroGlass(
                                kind = AuroGlassKind.Card,
                                onClick = {
                                    if (newPlaylistName.isNotBlank()) {
                                        onCreatePlaylist(newPlaylistName.trim())
                                        isCreatingNew = false
                                        newPlaylistName = ""
                                    }
                                },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                    Text("Criar & Adicionar", color = AuroColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCreatingNew = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("+ Nova Lista de Reprodução", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (playlists.isEmpty() && !isCreatingNew) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Nenhuma lista criada ainda", color = AuroColor.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    } else {
                        items(playlists) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPlaylist(pl) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = AuroColor.TextSecondary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = pl.name, color = AuroColor.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(text = "${pl.trackCount} faixas", color = AuroColor.TextSecondary, fontSize = 11.5.sp)
                                }
                            }
                            HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. DIÁLOGO SLEEP TIMER (AURŌ SLEEP TIMER)
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
    val accent = LocalAuroAccent.current.a.value

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        Surface(
            color = AuroColor.Black,
            shape = RoundedCornerShape(AuroRadius.card),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .border(1.dp, AuroColor.Hairline, RoundedCornerShape(AuroRadius.card))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TEMPORIZADOR",
                        color = AuroColor.TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = AuroColor.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                if (remainingSeconds > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ATIVO: ${remainingSeconds / 60}m ${remainingSeconds % 60}s restantes",
                        color = accent,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                            checkedColor = accent,
                            uncheckedColor = AuroColor.TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tocar até o fim da faixa",
                        color = AuroColor.TextPrimary,
                        fontSize = 13.sp
                    )
                }

                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                options.forEach { minutes ->
                    AuroDialogOption(
                        icon = Icons.Rounded.Schedule,
                        title = "$minutes minutos",
                        onClick = {
                            onSetTimerMinutes(minutes, stopAtTrackEnd)
                            onDismiss()
                        }
                    )
                }

                if (remainingSeconds > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AuroDialogOption(
                        icon = Icons.Rounded.Cancel,
                        title = "Desativar Temporizador",
                        onClick = {
                            onCancelTimer()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. MENU SHEET PRINCIPAL (AURŌ MENU SHEET)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuroPowerampMenuSheet(
    onOpenSettings: () -> Unit = {},
    onOpenEqualizer: () -> Unit,
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
        Surface(
            color = AuroColor.Black,
            shape = RoundedCornerShape(topStart = AuroRadius.card, topEnd = AuroRadius.card),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AuroColor.Hairline, RoundedCornerShape(topStart = AuroRadius.card, topEnd = AuroRadius.card))
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp)
                        .height(4.dp)
                        .background(AuroColor.Hairline, RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MENU",
                        color = AuroColor.TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = AuroColor.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AuroDialogOption(
                    icon = Icons.Rounded.Settings,
                    title = stringResource(R.string.settings_title),
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    }
                )

                AuroDialogOption(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Equalizador",
                    onClick = {
                        onDismiss()
                        onOpenEqualizer()
                    }
                )

                AuroDialogOption(
                    icon = Icons.Rounded.Schedule,
                    title = "Temporizador para Dormir",
                    onClick = {
                        onDismiss()
                        onOpenSleepTimer()
                    }
                )

                AuroDialogOption(
                    icon = Icons.Rounded.Speed,
                    title = "Informações do Motor de Áudio",
                    onClick = {
                        onDismiss()
                        onOpenAudioInfo()
                    }
                )

                AuroDialogOption(
                    icon = Icons.Rounded.Refresh,
                    title = "Reavaliar / Atualizar Biblioteca",
                    onClick = {
                        onDismiss()
                        onRescanLibrary()
                    }
                )

                AuroDialogOption(
                    icon = Icons.Rounded.Info,
                    title = "Sobre o Player",
                    onClick = {
                        onDismiss()
                        onOpenAbout()
                    }
                )
            }
        }
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
        Surface(
            color = AuroColor.Black,
            shape = RoundedCornerShape(AuroRadius.card),
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .border(1.dp, AuroColor.Hairline, RoundedCornerShape(AuroRadius.card))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MOTOR DE ÁUDIO NATIVO",
                        color = AuroColor.TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = AuroColor.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuroInfoRow("Pipeline", "Google Oboe (C++ High-Performance)")
                    AuroInfoRow("Driver Nativo", "AAudio / OpenSL ES")
                    AuroInfoRow("Modo de Latência", "PerformanceMode::LowLatency")
                    AuroInfoRow("Modo de Compartilhamento", "SharingMode::Shared")
                    AuroInfoRow("Taxa de Amostragem", "$sampleRate Hz")
                    AuroInfoRow("Buffer", "$bufferSizeFrames quadros (Burst)")
                    AuroInfoRow("Ponto Flutuante", "32-bit Float PCM Engine")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("OK", color = AuroColor.TextPrimary, fontSize = 14.sp)
                    }
                }
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
    val accent = LocalAuroAccent.current.a.value

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
        Surface(
            color = AuroColor.Black,
            shape = RoundedCornerShape(topStart = AuroRadius.card, topEnd = AuroRadius.card),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .border(1.dp, AuroColor.Hairline, RoundedCornerShape(topStart = AuroRadius.card, topEnd = AuroRadius.card))
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp)
                        .height(4.dp)
                        .background(AuroColor.Hairline, RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LETRAS",
                            color = AuroColor.TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${track.title} • ${lyrics?.source ?: "Sem letras locais"}",
                            color = AuroColor.TextTertiary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = AuroColor.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                if (lyrics == null || lyrics.lines.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Nenhuma letra encontrada",
                                color = AuroColor.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Coloque um arquivo .lrc na mesma pasta com o mesmo nome da música.",
                                color = AuroColor.TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            val isCurrent = index == activeLineIndex
                            Text(
                                text = line.text,
                                color = if (isCurrent) accent else AuroColor.TextTertiary,
                                fontSize = if (isCurrent) 18.sp else 14.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
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
    val accent = LocalAuroAccent.current.a.value

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        Surface(
            color = AuroColor.Black,
            shape = RoundedCornerShape(AuroRadius.card),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .border(1.dp, AuroColor.Hairline, RoundedCornerShape(AuroRadius.card))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "SALVAR PRESET",
                    color = AuroColor.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    placeholder = { Text("Nome do preset...", color = AuroColor.TextTertiary, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = AuroColor.Hairline,
                        focusedTextColor = AuroColor.TextPrimary,
                        unfocusedTextColor = AuroColor.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = AuroColor.TextSecondary, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    AuroGlass(
                        kind = AuroGlassKind.Card,
                        onClick = {
                            if (presetName.isNotBlank()) {
                                onSave(presetName.trim())
                                onDismiss()
                            }
                        },
                        modifier = Modifier.height(38.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text("Salvar", color = AuroColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
    val accent = LocalAuroAccent.current.a.value

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.Dialog
    ) {
        Surface(
            color = AuroColor.Black,
            shape = RoundedCornerShape(AuroRadius.card),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .border(1.dp, AuroColor.Hairline, RoundedCornerShape(AuroRadius.card))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "NOVA LISTA DE REPRODUÇÃO",
                    color = AuroColor.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = { Text("Nome da lista...", color = AuroColor.TextTertiary, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = AuroColor.Hairline,
                        focusedTextColor = AuroColor.TextPrimary,
                        unfocusedTextColor = AuroColor.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCELAR", color = AuroColor.TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (playlistName.isNotBlank()) {
                                onCreate(playlistName.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(AuroRadius.chip)
                    ) {
                        Text("CRIAR", color = AuroColor.Black, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
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
        Surface(
            color = AuroColor.Black,
            shape = RoundedCornerShape(AuroRadius.card),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .border(1.dp, AuroColor.Hairline, RoundedCornerShape(AuroRadius.card))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title.uppercase(),
                    color = AuroColor.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    color = AuroColor.TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = AuroColor.TextSecondary, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    AuroGlass(
                        kind = AuroGlassKind.Card,
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        modifier = Modifier.height(38.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text("Excluir", color = AuroColor.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
        Surface(
            color = AuroColor.Black,
            shape = RoundedCornerShape(AuroRadius.card),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .border(1.dp, AuroColor.Hairline, RoundedCornerShape(AuroRadius.card))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "AURŌ PLAYER",
                    color = AuroColor.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • Aurō DS v3",
                    color = AuroColor.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "• Motor de áudio nativo Google Oboe C++\n" +
                            "• Processamento de luz global única com AGSL RuntimeShader\n" +
                            "• Vidro preto líquido com dispersão cromática por canal\n" +
                            "• Extração harmônica de cores de capa via AndroidX Palette\n" +
                            "• Letras sincronizadas (.LRC) e equalizador de 10 bandas",
                    color = AuroColor.TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Fechar", color = AuroColor.TextPrimary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AuroDialogOption(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = AuroColor.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = title, color = AuroColor.TextPrimary, fontSize = 14.sp)
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
        Text(text = label, color = AuroColor.TextSecondary, fontSize = 12.sp, modifier = Modifier.width(130.dp))
        Text(
            text = value,
            color = AuroColor.TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
