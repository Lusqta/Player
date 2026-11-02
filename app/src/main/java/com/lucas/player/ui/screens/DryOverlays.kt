package com.lucas.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.data.LyricsManager
import com.lucas.player.model.SortMode
import com.lucas.player.model.Track
import com.lucas.player.model.UserPlaylist
import com.lucas.player.ui.components.DryRatingBar
import com.lucas.player.ui.components.formatTimeMs
import com.lucas.player.ui.theme.DryColors
import java.util.Locale

/**
 * 1. MENU CONTEXTUAL DA FAIXA "SECO"
 */
@Composable
fun DryTrackContextMenuDialog(
    track: Track,
    onPlayNext: () -> Unit,
    onEnqueueEnd: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSetRating: (Int) -> Unit,
    onShowInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = DryColors.SurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .border(1.dp, DryColors.Border, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = DryColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist.ifEmpty { "Desconhecido" },
                            color = DryColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = DryColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Classificação 5 estrelas rápida estilo Poweramp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Classificação:", color = DryColors.TextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    DryRatingBar(rating = track.rating, onRatingSelected = onSetRating)
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(6.dp))

                DryDialogOption(icon = Icons.AutoMirrored.Rounded.PlaylistPlay, title = "Tocar a seguir (Fila prioritária)", onClick = onPlayNext)
                DryDialogOption(icon = Icons.AutoMirrored.Rounded.QueueMusic, title = "Adicionar ao fim da fila", onClick = onEnqueueEnd)
                DryDialogOption(icon = Icons.AutoMirrored.Rounded.PlaylistAdd, title = "Adicionar à lista de reprodução...", onClick = onAddToPlaylist)
                DryDialogOption(icon = Icons.Rounded.Info, title = "Informações técnicas do arquivo", onClick = onShowInfo)
            }
        }
    }
}

/**
 * 2. DIÁLOGO DE INFORMAÇÕES TÉCNICAS DO ARQUIVO (Estilo Poweramp AudioInfoActivity)
 */
@Composable
fun DryTrackInfoDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = DryColors.SurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .border(1.dp, DryColors.Border, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETALHES DO ÁUDIO",
                        color = DryColors.TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = DryColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                InfoRow(label = "Título", value = track.title)
                InfoRow(label = "Artista", value = track.artist.ifEmpty { "Desconhecido" })
                InfoRow(label = "Álbum", value = track.album.ifEmpty { "Desconhecido" })
                InfoRow(label = "Formato", value = track.formatTag.uppercase().ifEmpty { "DESCONHECIDO" })
                InfoRow(label = "Taxa de Amostragem", value = "${track.sampleRateHz} Hz")
                if (track.bitrateKbps > 0) {
                    InfoRow(label = "Taxa de Bits", value = "${track.bitrateKbps} kbps")
                }
                InfoRow(label = "Duração", value = formatTimeMs(track.durationMs))
                val sizeMb = track.fileSize / (1024f * 1024f)
                InfoRow(label = "Tamanho", value = String.format(Locale.getDefault(), "%.2f MB", sizeMb))
                InfoRow(label = "Caminho", value = track.path.ifEmpty { track.uri.toString() })
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, color = DryColors.TextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = DryColors.TextPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 3. DIÁLOGO DE ORDENAÇÃO
 */
@Composable
fun DrySortDialog(
    currentSortMode: SortMode,
    onSelectSort: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = DryColors.SurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .border(1.dp, DryColors.Border, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "ORDENAR POR",
                    color = DryColors.TextPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                SortMode.values().forEach { mode ->
                    val isSelected = mode == currentSortMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectSort(mode)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelectSort(mode)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = DryColors.Accent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = mode.displayName,
                            color = if (isSelected) DryColors.Accent else DryColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4. DIÁLOGO PARA ADICIONAR À PLAYLIST (Poweramp PlaylistSelectAndAddToListLayout)
 */
@Composable
fun DryAddToPlaylistDialog(
    track: Track,
    playlists: List<UserPlaylist>,
    onCreatePlaylist: (String) -> Unit,
    onSelectPlaylist: (UserPlaylist) -> Unit,
    onDismiss: () -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = DryColors.SurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .fillMaxHeight(0.65f)
                .border(1.dp, DryColors.Border, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ADICIONAR À LISTA",
                        color = DryColors.TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = DryColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.title,
                    color = DryColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isCreatingNew) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DryColors.Surface, RoundedCornerShape(4.dp))
                            .border(0.5.dp, DryColors.BorderSubtle, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Text("NOVA LISTA DE REPRODUÇÃO", color = DryColors.TextTertiary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("Nome da lista...", color = DryColors.TextTertiary, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DryColors.Accent,
                                unfocusedBorderColor = DryColors.BorderSubtle,
                                focusedTextColor = DryColors.TextPrimary,
                                unfocusedTextColor = DryColors.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { isCreatingNew = false }) {
                                Text("Cancelar", color = DryColors.TextSecondary, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    if (newPlaylistName.isNotBlank()) {
                                        onCreatePlaylist(newPlaylistName.trim())
                                        isCreatingNew = false
                                        newPlaylistName = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DryColors.Accent),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Text("Criar & Adicionar", color = DryColors.Background, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCreatingNew = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = DryColors.Accent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("+ Nova Lista de Reprodução", color = DryColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp)
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
                                Text("Nenhuma lista existente", color = DryColors.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    } else {
                        items(playlists) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPlaylist(pl) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = DryColors.TextSecondary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = pl.name, color = DryColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(text = "${pl.trackCount} faixas", color = DryColors.TextSecondary, fontSize = 11.sp)
                                }
                            }
                            HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. DIÁLOGO DO SLEEP TIMER (Poweramp SleepTimerBehavior)
 */
@Composable
fun DrySleepTimerDialog(
    remainingSeconds: Int = 0,
    onSetTimerMinutes: (Int, Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(15, 30, 45, 60, 90, 120)
    var stopAtTrackEnd by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = DryColors.SurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .border(1.dp, DryColors.Border, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TEMPORIZADOR PARA DORMIR",
                        color = DryColors.TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = DryColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                if (remainingSeconds > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "TEMPORIZADOR ATIVO: ${remainingSeconds / 60}m ${remainingSeconds % 60}s restantes",
                        color = DryColors.Accent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Opção estilo Poweramp: Tocar até o fim da faixa
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
                            checkedColor = DryColors.Accent,
                            uncheckedColor = DryColors.TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tocar até o fim da faixa",
                        color = DryColors.TextPrimary,
                        fontSize = 13.sp
                    )
                }

                HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                options.forEach { minutes ->
                    DryDialogOption(
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
                    DryDialogOption(
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

@Composable
fun DryPowerampMenuSheet(
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenAudioInfo: () -> Unit,
    onRescanLibrary: () -> Unit,
    onOpenAbout: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = DryColors.SurfaceElevated,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, DryColors.Border, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                // Barra de arraste
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .background(DryColors.Border, RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MENU",
                        color = DryColors.TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = DryColors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                DryDialogOption(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Equalizador",
                    onClick = {
                        onDismiss()
                        onOpenEqualizer()
                    }
                )

                DryDialogOption(
                    icon = Icons.Rounded.Schedule,
                    title = "Temporizador para Dormir",
                    onClick = {
                        onDismiss()
                        onOpenSleepTimer()
                    }
                )

                DryDialogOption(
                    icon = Icons.Rounded.Speed,
                    title = "Informações do Motor de Áudio",
                    onClick = {
                        onDismiss()
                        onOpenAudioInfo()
                    }
                )

                DryDialogOption(
                    icon = Icons.Rounded.Refresh,
                    title = "Reavaliar / Atualizar Biblioteca",
                    onClick = {
                        onDismiss()
                        onRescanLibrary()
                    }
                )

                DryDialogOption(
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

/**
 * 6. DIÁLOGO DE INFORMAÇÕES DO MOTOR DE ÁUDIO (NATIVE OBOE / AAUDIO)
 */
@Composable
fun DryAudioEngineDialog(
    sampleRate: Int = 48000,
    bufferSizeFrames: Int = 192,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = DryColors.SurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .border(1.dp, DryColors.Border, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MOTOR DE ÁUDIO NATIVO",
                        color = DryColors.TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = DryColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                InfoRow(label = "Engine", value = "C++ Oboe / AAudio Native Direct Output")
                InfoRow(label = "Latência", value = "Ultra-Low Latency (Modo Exclusivo)")
                InfoRow(label = "Frequência Nativa", value = "$sampleRate Hz")
                InfoRow(label = "Tamanho do Buffer", value = "$bufferSizeFrames quadros (burst)")
                InfoRow(label = "Canais", value = "2 canais (Estéreo 32-bit Float)")
                InfoRow(label = "RingBuffer", value = "Lock-free SPSC RingBuffer (Zero GC)")
                InfoRow(label = "Underruns", value = "0")
            }
        }
    }
}

/**
 * 7. DIÁLOGO SOBRE
 */
@Composable
fun DryAboutDialog(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = DryColors.SurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .border(1.dp, DryColors.Border, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SOBRE O PLAYER",
                        color = DryColors.TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = DryColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Player Audiófilo v1.0",
                    color = DryColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Arquitetura de Navegação Poweramp com Engine de Áudio Nativa C++ Oboe / AAudio de 120 FPS.",
                    color = DryColors.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "• Processamento DSP em C++ nativo\n• Buffer SPSC sem travamentos\n• Decodificação direta MediaCodec / FFmpeg\n• Equalizador gráfico paramétrico de 10 bandas",
                    color = DryColors.TextTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun DryDialogOption(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DryColors.Accent, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = title, color = DryColors.TextPrimary, fontSize = 14.sp)
    }
}

/**
 * 8. DIÁLOGO / SHEET DE LETRAS SINCRONIZADAS (Poweramp Lyrics Engine)
 */
@Composable
fun DryLyricsSheet(
    track: Track,
    lyrics: LyricsManager.Lyrics?,
    currentProgressMs: Long,
    onSeekToMs: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    val activeLineIndex = remember(lyrics, currentProgressMs) {
        if (lyrics != null && lyrics.isSynced && lyrics.lines.isNotEmpty()) {
            val idx = lyrics.lines.indexOfLast { it.timeMs <= currentProgressMs }
            if (idx >= 0) idx else 0
        } else -1
    }

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0) {
            val targetScroll = (activeLineIndex - 3).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = DryColors.SurfaceElevated,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.80f)
                .border(0.5.dp, DryColors.Border, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .background(DryColors.Border, RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LETRAS",
                            color = DryColors.TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${track.title} • ${lyrics?.source ?: "Sem letras locais"}",
                            color = DryColors.TextTertiary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = DryColors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider(color = DryColors.BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

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
                                color = DryColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Coloque um arquivo .lrc na mesma pasta com o mesmo nome da faixa.",
                                color = DryColors.TextSecondary,
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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            val isCurrent = index == activeLineIndex
                            Text(
                                text = line.text,
                                color = if (isCurrent) DryColors.TextPrimary else DryColors.TextTertiary,
                                fontSize = if (isCurrent) 16.sp else 14.sp,
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

/**
 * 9. DIÁLOGO PARA SALVAR PRESET PERSONALIZADO DO EQUALIZADOR
 */
@Composable
fun DrySavePresetDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var presetName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = DryColors.SurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .border(1.dp, DryColors.Border, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "NOVO PRESET DE EQUALIZADOR",
                    color = DryColors.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    placeholder = { Text("Nome do preset...", color = DryColors.TextTertiary, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DryColors.Accent,
                        unfocusedBorderColor = DryColors.BorderSubtle,
                        focusedTextColor = DryColors.TextPrimary,
                        unfocusedTextColor = DryColors.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = DryColors.TextSecondary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (presetName.isNotBlank()) {
                                onSave(presetName.trim())
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DryColors.Accent),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text("Salvar", color = DryColors.Background, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 10. DIÁLOGO GENÉRICO DE CONFIRMAÇÃO DE EXCLUSÃO
 */
@Composable
fun DryConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = DryColors.SurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .border(1.dp, DryColors.Border, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = title.uppercase(),
                    color = DryColors.TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = message,
                    color = DryColors.TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = DryColors.TextSecondary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DryColors.TextPrimary),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text("Excluir", color = DryColors.Background, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
