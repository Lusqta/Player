package com.lucas.player.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.model.Track
import com.lucas.player.ui.navigation.NavScreen
import com.lucas.player.ui.theme.DryColors
import java.util.Locale

/**
 * Formata milissegundos no padrão mm:ss ou hh:mm:ss
 */
private fun dryFormatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val hours = totalSec / 3600
    val min = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, min, sec)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }
}

/**
 * 1. NAVBAR INFERIOR TOTALMENTE SECA
 * Sem efeitos, cores ou decorações inventadas.
 */
@Composable
fun DryNavbar(
    currentScreen: NavScreen,
    onEqClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DryColors.Background,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 0.5.dp, color = DryColors.BorderSubtle)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavbarTab(
                icon = Icons.Rounded.GraphicEq,
                label = "Equalizador",
                isActive = currentScreen is NavScreen.Equalizer,
                onClick = onEqClick
            )

            NavbarTab(
                icon = Icons.Rounded.LibraryMusic,
                label = "Biblioteca",
                isActive = currentScreen !is NavScreen.Equalizer && currentScreen !is NavScreen.Search && currentScreen !is NavScreen.NowPlaying,
                onClick = onLibraryClick
            )

            NavbarTab(
                icon = Icons.Rounded.Search,
                label = "Buscar",
                isActive = currentScreen is NavScreen.Search,
                onClick = onSearchClick
            )

            NavbarTab(
                icon = Icons.Rounded.Menu,
                label = "Menu",
                isActive = false,
                onClick = onMenuClick
            )
        }
    }
}

@Composable
private fun NavbarTab(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val tint = if (isActive) DryColors.TextPrimary else DryColors.TextSecondary

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "tabScale"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * 2. MINI PLAYER TOTALMENTE SECO
 * Docado acima da barra inferior. Sem sombras, sem bordas coloridas.
 */
@Composable
fun DryMiniPlayer(
    track: Track?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    coverBitmap: Bitmap?,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null) return

    val progressFraction = if (durationMs > 0) {
        (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        color = DryColors.Surface,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 0.5.dp, color = DryColors.BorderSubtle)
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
                        if (absY > absX && totalDragY < -30f) {
                            onOpenNowPlaying()
                        } else if (absX > absY) {
                            if (totalDragX < -40f) onNext()
                            else if (totalDragX > 40f) onPrevious()
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    totalDragX += dragAmount.x
                    totalDragY += dragAmount.y
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenNowPlaying
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Linha de progresso fina de 1.5dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(DryColors.BorderSubtle)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(DryColors.TextPrimary)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Capa simples quadrada (38x38)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(DryColors.SurfaceElevated),
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
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = DryColors.TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Título e Artista
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = DryColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist.ifEmpty { "Desconhecido" },
                        color = DryColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Anterior
                IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = DryColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play/Pause
                IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                        tint = DryColors.TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Próxima
                IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Próxima",
                        tint = DryColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 3. CABEÇALHO SECO COM BREADCRUMB
 */
@Composable
fun DryBreadcrumbHeader(
    items: List<BreadcrumbItem>,
    onItemClick: (Int) -> Unit,
    canGoBack: Boolean,
    onBackClick: () -> Unit,
    rightActions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = DryColors.Background,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .border(width = 0.5.dp, color = DryColors.BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Voltar",
                        tint = DryColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Breadcrumb
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isLast = index == items.size - 1
                    Text(
                        text = item.label,
                        color = if (isLast) DryColors.TextPrimary else DryColors.TextSecondary,
                        fontSize = if (items.size == 1) 17.sp else 14.sp,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable(
                                enabled = !isLast,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onItemClick(index) }
                            )
                            .padding(vertical = 4.dp)
                    )

                    if (!isLast) {
                        Text(
                            text = " > ",
                            color = DryColors.TextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = rightActions
            )
        }
    }
}

data class BreadcrumbItem(val label: String)

/**
 * 4. LINHA DE FAIXA TOTALMENTE SECA
 * Alta densidade, neutra, pura informação funcional.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DryTrackRow(
    track: Track,
    trackIndex: Int,
    isPlayingThis: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isPlayingThis) DryColors.SurfaceActive else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicador de posição / tocando
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isPlayingThis) {
                Icon(
                    imageVector = Icons.Rounded.Equalizer,
                    contentDescription = "Tocando",
                    tint = DryColors.TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = "${trackIndex + 1}",
                    color = DryColors.TextTertiary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                )
            }
        }

        // Título e Detalhes
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = DryColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${track.artist.ifEmpty { "Desconhecido" }} • ${track.album.ifEmpty { "Desconhecido" }}",
                color = DryColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Indicador de classificação por estrelas se houver
        if (track.rating > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = DryColors.Accent,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "${track.rating}",
                    color = DryColors.TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Tag do formato em texto simples
        if (track.formatTag.isNotEmpty()) {
            Text(
                text = track.formatTag.uppercase(),
                color = DryColors.TextTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Duração
        Text(
            text = dryFormatTimeMs(track.durationMs),
            color = DryColors.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
        )

        // Menu 3 pontinhos
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Opções",
                tint = DryColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 5. LINHA DE CATEGORIA TOTALMENTE SECA
 * Sem cartões arredondados ou caixas com cor. Lista simples e limpa.
 */
@Composable
fun DryCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DryColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = DryColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = DryColors.TextSecondary,
                fontSize = 12.sp
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = DryColors.TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 6. BADGE SECO (Apenas texto discreto)
 */
@Composable
fun DryBadge(
    text: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = "[$text]",
        color = DryColors.TextSecondary,
        fontSize = if (compact) 9.sp else 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        modifier = modifier
    )
}

/**
 * 7. INDEXADOR ALFABÉTICO VERTICAL (Estilo Poweramp ListIndexerPopupView)
 * Permite rolagem rápida e exibe bolha flutuante com a letra ativa.
 */
@Composable
fun DryAlphabetFastScroller(
    availableLetters: List<Char> = remember { listOf('#') + ('A'..'Z').toList() },
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp)
                .pointerInput(availableLetters) {
                    detectTapGestures { offset ->
                        val letterIndex = (offset.y / size.height * availableLetters.size).toInt()
                            .coerceIn(0, availableLetters.size - 1)
                        val letter = availableLetters[letterIndex]
                        if (letter != activeLetter) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeLetter = letter
                            onLetterSelected(letter)
                        } else {
                            onLetterSelected(letter)
                        }
                    }
                }
                .pointerInput(availableLetters) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val letterIndex = (offset.y / size.height * availableLetters.size).toInt()
                                .coerceIn(0, availableLetters.size - 1)
                            val letter = availableLetters[letterIndex]
                            if (letter != activeLetter) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeLetter = letter
                                onLetterSelected(letter)
                            }
                        },
                        onDragEnd = {
                            activeLetter = null
                        },
                        onDragCancel = {
                            activeLetter = null
                        }
                    ) { change, _ ->
                        change.consume()
                        val letterIndex = (change.position.y / size.height * availableLetters.size).toInt()
                            .coerceIn(0, availableLetters.size - 1)
                        val letter = availableLetters[letterIndex]
                        if (letter != activeLetter) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeLetter = letter
                            onLetterSelected(letter)
                        }
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            availableLetters.forEach { char ->
                Text(
                    text = char.toString(),
                    color = if (char == activeLetter) DryColors.Accent else DryColors.TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (char == activeLetter) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Popup flutuante da letra ativa (Poweramp ListIndexerPopupView)
        activeLetter?.let { letter ->
            Box(
                modifier = Modifier
                    .padding(end = 40.dp)
                    .size(54.dp)
                    .background(DryColors.SurfaceElevated, RoundedCornerShape(4.dp))
                    .border(1.dp, DryColors.Border, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    color = DryColors.TextPrimary,
                    fontSize = 26.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 8. BARRA DE CLASSIFICAÇÃO DE 5 ESTRELAS SECA (Poweramp SET_RATING / TOGGLE_RATING)
 */
@Composable
fun DryRatingBar(
    rating: Int,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) {
            val isFilled = i <= rating
            IconButton(
                onClick = {
                    if (rating == i) {
                        onRatingSelected(0)
                    } else {
                        onRatingSelected(i)
                    }
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isFilled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "$i estrelas",
                    tint = if (isFilled) DryColors.Accent else DryColors.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (rating > 0) {
            Text(
                text = "$rating/5",
                color = DryColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * 9. BARRA DE BUSCA EM FORMA DE ONDA (Poweramp Waveseek)
 * Substitui o slider genérico pela assinatura visual e tátil icônica do Poweramp.
 */
@Composable
fun DryWaveformSeekbar(
    progressFraction: Float,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    peaks: FloatArray? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val barCount = 70
    val wavePeaks = remember(peaks) {
        if (peaks != null && peaks.isNotEmpty()) {
            peaks
        } else {
            FloatArray(barCount) { i ->
                val base = 0.22f + 0.52f * kotlin.math.abs(kotlin.math.sin(i * 0.18)).toFloat()
                val variation = 0.18f * kotlin.math.abs(kotlin.math.cos(i * 0.42)).toFloat()
                (base + variation).coerceIn(0.12f, 0.95f)
            }
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val activeFraction = if (isDragging) dragFraction else progressFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDragEnd = {
                        onSeek(dragFraction)
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                ) { change, _ ->
                    change.consume()
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSeek(frac)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalWidth = size.width
            val totalHeight = size.height
            val centerY = totalHeight / 2f
            val maxHalfHeight = totalHeight * 0.44f

            val slotWidth = totalWidth / barCount
            val barWidth = (slotWidth * 0.65f).coerceAtLeast(2.5f)

            for (i in 0 until barCount) {
                val x = i * slotWidth + slotWidth / 2f
                val frac = x / totalWidth
                val isPlayed = frac <= activeFraction

                val peak = wavePeaks.getOrElse(i) { 0.3f }
                val halfH = (peak * maxHalfHeight).coerceAtLeast(3f)

                drawLine(
                    color = if (isPlayed) DryColors.TextPrimary else DryColors.BorderSubtle,
                    start = androidx.compose.ui.geometry.Offset(x, centerY - halfH),
                    end = androidx.compose.ui.geometry.Offset(x, centerY + halfH),
                    strokeWidth = barWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }

            // Indicador da agulha de reprodução / scrubber
            val needleX = (activeFraction * totalWidth).coerceIn(0f, totalWidth)
            drawLine(
                color = DryColors.Accent,
                start = androidx.compose.ui.geometry.Offset(needleX, 2f),
                end = androidx.compose.ui.geometry.Offset(needleX, totalHeight - 2f),
                strokeWidth = 2f
            )
        }
    }
}
