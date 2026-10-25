package com.lucas.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.model.SortMode
import com.lucas.player.model.Track
import com.lucas.player.ui.components.GlassCircleButton
import com.lucas.player.ui.components.GlassSurfaceCard

/**
 * Menu Contextual da Faixa (Ações Rápidas Poweramp)
 */
@Composable
fun TrackContextMenuSheet(
    track: Track,
    onPlayNext: () -> Unit,
    onEnqueueEnd: () -> Unit,
    onFavorite: () -> Unit,
    onShowInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ContextMenuItem(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Tocar a Seguir (Play Next)",
                    onClick = onPlayNext
                )
                ContextMenuItem(
                    icon = Icons.Rounded.QueueMusic,
                    label = "Enfileirar no Fim (Enqueue)",
                    onClick = onEnqueueEnd
                )
                ContextMenuItem(
                    icon = Icons.Rounded.Star,
                    label = "Favoritar (5 Estrelas)",
                    onClick = onFavorite,
                    tint = Color(0xFFFFD54F)
                )
                ContextMenuItem(
                    icon = Icons.Rounded.Info,
                    label = "Detalhes Técnicos e ReplayGain",
                    onClick = onShowInfo,
                    tint = Color(0xFF00E5FF)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF16112C)
    )
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Diálogo de Detalhes Técnicos do Áudio e Tags ReplayGain
 */
@Composable
fun TrackInfoDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Informações da Faixa", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow("Título", track.title)
                InfoRow("Artista", track.artist)
                InfoRow("Álbum", track.album)
                InfoRow("Gênero", if (track.genre.isNotEmpty()) track.genre else "N/A")
                InfoRow("Ano", if (track.year > 0) track.year.toString() else "N/A")
                InfoRow("Formato / Codec", track.formatTag)
                InfoRow("ReplayGain Faixa", if (track.replayGainTrackGain != 0f) "${track.replayGainTrackGain} dB (Pico: ${track.replayGainTrackPeak})" else "Sem tag RG")
                InfoRow("ReplayGain Álbum", if (track.replayGainAlbumGain != 0f) "${track.replayGainAlbumGain} dB" else "Sem tag RG")
                InfoRow("Duração", formatTimeMs(track.durationMs))
                InfoRow("Tamanho", "${track.fileSize / (1024 * 1024)} MB")
                InfoRow("Execuções", "${track.playCount} vezes")
                InfoRow("Classificação", if (track.rating > 0) "${track.rating} estrelas" else "Sem voto")
                InfoRow("Caminho", track.path)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF16112C)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Fila Prioritária (Priority Queue do Poweramp)
 */
@Composable
fun PriorityQueueDialog(
    queueTracks: List<Track>,
    currentTrackId: Long,
    onTrackClick: (Track, Int) -> Unit,
    onClearQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fila de Reprodução", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (queueTracks.isNotEmpty()) {
                    TextButton(onClick = onClearQueue) {
                        Text("Limpar", color = Color(0xFFFF5252), fontSize = 12.sp)
                    }
                }
            }
        },
        text = {
            if (queueTracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("A fila prioritária está vazia.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(queueTracks) { track ->
                        val isPlaying = track.id == currentTrackId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTrackClick(track, queueTracks.indexOf(track)) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = if (isPlaying) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = if (isPlaying) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = formatTimeMs(track.durationMs),
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF16112C)
    )
}

/**
 * Diálogo de Sleep Timer
 */
@Composable
fun SleepTimerDialog(
    remainingSeconds: Int,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val intervals = listOf(5, 10, 15, 30, 45, 60, 90)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Temporizador de Desligamento", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (remainingSeconds > 0) {
                    Text(
                        text = "Ativo: ${remainingSeconds / 60}m ${remainingSeconds % 60}s restantes",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (remainingSeconds > 0) {
                    TextButton(
                        onClick = {
                            onCancelTimer()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("❌ Desativar Temporizador", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                }
                intervals.forEach { min ->
                    Text(
                        text = "⏱ $min minutos",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onStartTimer(min)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF16112C)
    )
}

/**
 * Diálogo de Ordenação da Lista
 */
@Composable
fun SortOrderDialog(
    currentSortMode: SortMode,
    onSelectSortMode: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ordenar Lista", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(SortMode.values()) { mode ->
                    val isSelected = mode == currentSortMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectSortMode(mode)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = mode.displayName,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF16112C)
    )
}
