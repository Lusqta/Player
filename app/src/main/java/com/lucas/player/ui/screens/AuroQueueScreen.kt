package com.lucas.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.model.Track
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.LocalAuroAccent

/**
 * AURŌ DESIGN SYSTEM v2 - TELA DEDICADA DE FILA DE REPRODUÇÃO (AURŌ QUEUE SCREEN)
 */
@Composable
fun AuroQueueScreen(
    priorityTracks: List<Track>,
    regularTracks: List<Track>,
    currentTrackId: Long,
    onBackClick: () -> Unit,
    onTrackClick: (Track, Int, Boolean) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onClearQueue: () -> Unit,
    onShuffleQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCount = priorityTracks.size + regularTracks.size
    val totalDuration = priorityTracks.sumOf { it.durationMs } + regularTracks.sumOf { it.durationMs }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroBreadcrumbHeader(
            items = listOf(
                AuroBreadcrumbItem("Biblioteca"),
                AuroBreadcrumbItem("Fila de Reprodução")
            ),
            onItemClick = { onBackClick() },
            canGoBack = true,
            onBackClick = onBackClick,
            rightActions = {
                if (totalCount > 0) {
                    IconButton(onClick = onShuffleQueue, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Aleatório",
                            tint = AuroColor.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onClearQueue, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Limpar Fila",
                            tint = AuroColor.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )

        if (totalCount == 0) {
            AuroEmptyState(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = stringResource(R.string.empty_queue_title),
                message = stringResource(R.string.empty_queue_message),
                actionLabel = stringResource(R.string.empty_queue_action),
                onAction = onBackClick,
                modifier = Modifier.padding(bottom = 120.dp)
            )
        } else {
            // Banner informativo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FILA TOTAL",
                        color = AuroColor.TextTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$totalCount faixas • ${formatTimeMs(totalDuration)}",
                        color = AuroColor.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (priorityTracks.isNotEmpty()) {
                    Text(
                        text = "${priorityTracks.size} prioritárias",
                        color = LocalAuroAccent.current.a.value,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Seção Prioritária (A SEGUIR)
                if (priorityTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "A SEGUIR (PRIORITÁRIA)",
                            color = LocalAuroAccent.current.a.value,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    itemsIndexed(priorityTracks, key = { idx, t -> "prio_${t.id}_$idx" }) { index, track ->
                        AuroQueueTrackRow(
                            track = track,
                            index = index,
                            isPlaying = track.id == currentTrackId,
                            isPriority = true,
                            onClick = { onTrackClick(track, index, true) },
                            onRemove = { onRemoveTrack(track) }
                        )
                        HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                    }
                }

                // Seção Regular
                if (regularTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "REPRODUÇÃO NORMAL",
                            color = AuroColor.TextTertiary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    itemsIndexed(regularTracks, key = { idx, t -> "reg_${t.id}_$idx" }) { index, track ->
                        AuroQueueTrackRow(
                            track = track,
                            index = index,
                            isPlaying = track.id == currentTrackId,
                            isPriority = false,
                            onClick = { onTrackClick(track, index, false) },
                            onRemove = { onRemoveTrack(track) }
                        )
                        HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuroQueueTrackRow(
    track: Track,
    index: Int,
    isPlaying: Boolean,
    isPriority: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val accent = LocalAuroAccent.current.a.value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val coverBitmap = rememberTrackCover(track, 100)

        if (coverBitmap != null) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ılı",
                            color = accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isPlaying) {
                    Text(
                        text = "ılı",
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        color = AuroColor.TextTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isPlaying) accent else AuroColor.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist.ifEmpty { "Desconhecido" },
                color = AuroColor.TextSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatTimeMs(track.durationMs),
            color = AuroColor.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remover",
                tint = AuroColor.TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
