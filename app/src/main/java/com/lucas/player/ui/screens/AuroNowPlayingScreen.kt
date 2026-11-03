package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.model.Track
import com.lucas.player.queue.RepeatMode
import com.lucas.player.queue.ShuffleMode
import com.lucas.player.ui.components.AuroCoverPlaceholder
import com.lucas.player.ui.components.AuroEmptyState
import com.lucas.player.ui.components.AuroHaptics
import com.lucas.player.ui.components.AuroProgressBar
import com.lucas.player.ui.theme.*

/**
 * AURŌ DESIGN SYSTEM v2 - TELA NOW PLAYING OFICIAL DO AURŌ DESIGN SYSTEM
 * Reproduz fielmente a spec e o espécime visual (especimen_design_system.png).
 */
@Composable
fun AuroNowPlayingScreen(
    track: Track?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    coverBitmap: Bitmap?,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    currentTrackIndex: Int = 0,
    queueSize: Int = 0,
    categoryTitle: String = "Biblioteca",
    upcomingTracks: List<Track> = emptyList(),
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekJumpForward: () -> Unit = {},
    onSeekJumpBackward: () -> Unit = {},
    onBeginFastForward: () -> Unit = {},
    onEndFastForward: () -> Unit = {},
    onBeginRewind: () -> Unit = {},
    onEndRewind: () -> Unit = {},
    onRatingChanged: (Int) -> Unit = {},
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenTrackInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null) {
        AuroEmptyState(
            icon = Icons.Rounded.MusicOff,
            title = stringResource(R.string.no_file_loaded),
            message = stringResource(R.string.empty_queue_message),
            actionLabel = stringResource(R.string.empty_queue_action),
            onAction = onCollapse,
            modifier = modifier
                .fillMaxSize()
                .background(AuroColor.Black)
        )
        return
    }

    val currentProgressFraction = if (durationMs > 0) {
        (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        // Barra superior discreta para recolher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Recolher",
                    tint = AuroColor.TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (queueSize > 0) {
                Text(
                    text = "${currentTrackIndex + 1} / $queueSize",
                    color = AuroColor.TextTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable(onClick = onOpenQueue)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(36.dp))
            }

            Spacer(modifier = Modifier.size(36.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Capa com cantos arredondados de 22dp (sem vidro: fonte da cor)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(AuroRadius.cover))
                .pointerInput(Unit) {
                    var totalDragX = 0f
                    var totalDragY = 0f
                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDragEnd = {
                            val absX = kotlin.math.abs(totalDragX)
                            val absY = kotlin.math.abs(totalDragY)
                            if (absY > absX) {
                                if (totalDragY > 40f) onCollapse()
                                else if (totalDragY < -40f) onOpenQueue()
                            } else {
                                if (totalDragX < -45f) onNext()
                                else if (totalDragX > 45f) onPrevious()
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = "Capa",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AuroCoverPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    glass = true
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Título e Artista à esquerda com botões de vidro ★ e ⋮ à direita (Fiel ao espécime)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = track.title,
                    color = AuroColor.TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist.ifEmpty { "Desconhecido" },
                    color = AuroColor.TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ★ Círculo de vidro de 40dp
                val isFavorite = track.rating > 0
                AuroGlass(
                    kind = AuroGlassKind.Button,
                    onClick = { onRatingChanged(if (isFavorite) 0 else 5) },
                    haptic = { AuroHaptics.confirm(it) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Favoritar",
                            tint = if (isFavorite) LocalAuroAccent.current.a.value else AuroColor.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // ⋮ Círculo de vidro de 40dp
                AuroGlass(
                    kind = AuroGlassKind.Button,
                    onClick = onOpenTrackInfo,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Mais opções",
                            tint = AuroColor.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Barra de Progresso Oficial (Trilho 3dp #2D2D2D, Accent A, bolinha branca 10dp)
        AuroProgressBar(
            progressFraction = currentProgressFraction,
            durationMs = durationMs,
            onSeek = onSeek,
            onSeekJumpBackward = onSeekJumpBackward,
            onSeekJumpForward = onSeekJumpForward
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Controles de Reprodução (3 Círculos de Vidro Preto + Shuffle/Repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(onClick = onToggleShuffle, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Aleatório",
                    tint = if (shuffleMode != ShuffleMode.OFF) LocalAuroAccent.current.a.value else AuroColor.TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Anterior: Círculo de vidro de 56dp
            AuroGlass(
                kind = AuroGlassKind.Button,
                modifier = Modifier
                    .size(56.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onBeginRewind()
                                tryAwaitRelease()
                                onEndRewind()
                            },
                            onTap = { onPrevious() }
                        )
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Play/Pause: Círculo de vidro de 76dp
            AuroGlass(
                kind = AuroGlassKind.Button,
                onClick = onPlayPause,
                haptic = { AuroHaptics.confirm(it) },
                modifier = Modifier.size(76.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            // Próximo: Círculo de vidro de 56dp
            AuroGlass(
                kind = AuroGlassKind.Button,
                modifier = Modifier
                    .size(56.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onBeginFastForward()
                                tryAwaitRelease()
                                onEndFastForward()
                            },
                            onTap = { onNext() }
                        )
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Próxima",
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Repeat
            IconButton(onClick = onToggleRepeat, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = when (repeatMode) {
                        RepeatMode.ONE -> Icons.Rounded.RepeatOne
                        RepeatMode.ALL, RepeatMode.CATEGORY, RepeatMode.ADVANCE_CATEGORY -> Icons.Rounded.Repeat
                        else -> Icons.Rounded.Repeat
                    },
                    contentDescription = "Repetir",
                    tint = if (repeatMode != RepeatMode.OFF) LocalAuroAccent.current.a.value else AuroColor.TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Cards de Fila e Letras em Vidro Preto (Exatamente como no espécime)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AuroGlass(
                kind = AuroGlassKind.Card,
                onClick = onOpenQueue,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Fila",
                        color = AuroColor.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            AuroGlass(
                kind = AuroGlassKind.Card,
                onClick = onOpenLyrics,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Letras",
                        color = AuroColor.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Título da Seção (ex: VOX (7) como no espécime)
        val headerTitle = if (categoryTitle.isNotBlank()) {
            if (queueSize > 0) "${categoryTitle.uppercase()} ($queueSize)" else categoryTitle.uppercase()
        } else if (queueSize > 0) "FILA ($queueSize)" else "FILA"

        Text(
            text = headerTitle,
            color = AuroColor.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable(onClick = onOpenQueue)
        )

        // Pré-visualização da Fila / Próxima Faixa (Direto sobre o preto, como no espécime)
        if (upcomingTracks.isNotEmpty()) {
            val nextTrack = upcomingTracks.firstOrNull()
            val afterNextTrack = upcomingTracks.getOrNull(1)

            if (nextTrack != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenQueue)
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = nextTrack.title,
                        color = AuroColor.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = nextTrack.artist.ifEmpty { "Desconhecido" },
                        color = AuroColor.TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }

            if (afterNextTrack != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenQueue)
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = afterNextTrack.title,
                        color = AuroColor.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = afterNextTrack.artist.ifEmpty { "Desconhecido" },
                        color = AuroColor.TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }
        } else {
            Text(
                text = "Fim da fila",
                color = AuroColor.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
