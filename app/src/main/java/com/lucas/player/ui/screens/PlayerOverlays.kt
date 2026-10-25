package com.lucas.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.model.SortMode
import com.lucas.player.model.Track
import com.lucas.player.ui.components.FigmaGlassCircleButton
import com.lucas.player.ui.components.RealLiquidGlassPanel

/**
 * 1. MENU CONTEXTUAL DA FAIXA (Sem AlertDialog do Material, Vidro Real + Refração)
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        RealLiquidGlassPanel(
            cornerRadius = 32.dp,
            blurRadius = 38.dp,
            refractionPx = 26f,
            tint = Color(0xFF161238).copy(alpha = 0.72f),
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FigmaGlassCircleButton(
                        size = 36.dp,
                        onClick = onDismiss
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                        tint = Color(0xFFA8AEFF)
                    )
                }
            }
        }
    }
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
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 2. DIÁLOGO DE INFORMAÇÕES TÉCNICAS E REPLAYGAIN (Vidro Real)
 */
@Composable
fun TrackInfoDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        RealLiquidGlassPanel(
            cornerRadius = 32.dp,
            blurRadius = 38.dp,
            refractionPx = 26f,
            tint = Color(0xFF161238).copy(alpha = 0.72f),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Informações da Faixa", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    FigmaGlassCircleButton(size = 36.dp, onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoRow("Título", track.title)
                    InfoRow("Artista", track.artist)
                    InfoRow("Álbum", track.album)
                    InfoRow("Gênero", if (track.genre.isNotEmpty()) track.genre else "N/A")
                    InfoRow("Ano", if (track.year > 0) track.year.toString() else "N/A")
                    InfoRow("Formato / Codec", track.formatTag)
                    InfoRow("Engine de Decodificação", "C++ Nativa Oboe / AAudio 32-bit")
                    InfoRow("ReplayGain Faixa", if (track.replayGainTrackGain != 0f) "${track.replayGainTrackGain} dB (Pico: ${track.replayGainTrackPeak})" else "Sem tag RG")
                    InfoRow("ReplayGain Álbum", if (track.replayGainAlbumGain != 0f) "${track.replayGainAlbumGain} dB" else "Sem tag RG")
                    InfoRow("Duração", formatTimeMs(track.durationMs))
                    InfoRow("Tamanho", "${track.fileSize / (1024 * 1024)} MB")
                    InfoRow("Execuções", "${track.playCount} vezes")
                    InfoRow("Classificação", if (track.rating > 0) "${track.rating} estrelas" else "Sem voto")
                    InfoRow("Caminho", track.path)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text = label, color = Color(0xFFA8AEFF).copy(alpha = 0.70f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 3. FILA DE REPRODUÇÃO PRIORITÁRIA (Vidro Real)
 */
@Composable
fun PriorityQueueDialog(
    queueTracks: List<Track>,
    currentTrackId: Long,
    onTrackClick: (Track, Int) -> Unit,
    onClearQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        RealLiquidGlassPanel(
            cornerRadius = 32.dp,
            blurRadius = 38.dp,
            refractionPx = 26f,
            tint = Color(0xFF161238).copy(alpha = 0.72f),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fila de Reprodução", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (queueTracks.isNotEmpty()) {
                            FigmaGlassCircleButton(size = 36.dp, onClick = onClearQueue) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Limpar", tint = Color(0xFFFF6E6E), modifier = Modifier.size(18.dp))
                            }
                        }
                        FigmaGlassCircleButton(size = 36.dp, onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (queueTracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("A fila prioritária está vazia.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(queueTracks) { track ->
                            val isPlaying = track.id == currentTrackId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onTrackClick(track, queueTracks.indexOf(track)) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = if (isPlaying) Color(0xFFA8AEFF) else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = if (isPlaying) Color(0xFFA8AEFF) else Color.White,
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
            }
        }
    }
}

/**
 * 4. TEMPORIZADOR DE DESLIGAMENTO (Sleep Timer, Vidro Real)
 */
@Composable
fun SleepTimerDialog(
    remainingSeconds: Int,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val intervals = listOf(5, 10, 15, 30, 45, 60, 90)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        RealLiquidGlassPanel(
            cornerRadius = 32.dp,
            blurRadius = 38.dp,
            refractionPx = 26f,
            tint = Color(0xFF161238).copy(alpha = 0.72f),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Temporizador", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (remainingSeconds > 0) {
                            Text(
                                text = "Ativo: ${remainingSeconds / 60}m ${remainingSeconds % 60}s restantes",
                                color = Color(0xFFA8AEFF),
                                fontSize = 12.sp
                            )
                        }
                    }
                    FigmaGlassCircleButton(size = 36.dp, onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (remainingSeconds > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onCancelTimer()
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.TimerOff, contentDescription = null, tint = Color(0xFFFF6E6E), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Desativar Temporizador", color = Color(0xFFFF6E6E), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    intervals.forEach { min ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onStartTimer(min)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Timer, contentDescription = null, tint = Color(0xFFA8AEFF), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("$min minutos", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. DIÁLOGO DE ORDENAÇÃO DA LISTA (Vidro Real)
 */
@Composable
fun SortOrderDialog(
    currentSortMode: SortMode,
    onSelectSortMode: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        RealLiquidGlassPanel(
            cornerRadius = 32.dp,
            blurRadius = 38.dp,
            refractionPx = 26f,
            tint = Color(0xFF161238).copy(alpha = 0.72f),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ordenar Lista", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    FigmaGlassCircleButton(size = 36.dp, onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(SortMode.values()) { mode ->
                        val isSelected = mode == currentSortMode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectSortMode(mode)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFFA8AEFF) else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = mode.displayName,
                                color = if (isSelected) Color(0xFFA8AEFF) else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
