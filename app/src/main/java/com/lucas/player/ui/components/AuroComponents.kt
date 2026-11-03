package com.lucas.player.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.lucas.player.ui.theme.*
import java.util.Locale

/**
 * AURŌ DESIGN SYSTEM v2 - COMPONENTES
 * Todos os objetos de vidro leem a luz global e são fundidos sobre o preto absoluto (#000000).
 */

fun formatTimeMs(ms: Long): String {
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
 * 1. BARRA DE PROGRESSO OFICIAL DO AURŌ DESIGN SYSTEM
 * Spec: "trilho 3dp #2D2D2D, preenchimento Accent A, bolinha branca de 10dp. Tempos em Label/TextSecondary."
 */
@Composable
fun AuroProgressBar(
    progressFraction: Float,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    onSeekJumpBackward: () -> Unit = {},
    onSeekJumpForward: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAuroAccent.current.a.value
    val haptics = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var showRemainingTime by remember { mutableStateOf(true) }

    val activeFraction = (if (isDragging) dragFraction else progressFraction).coerceIn(0f, 1f)
    val displayedMs = (activeFraction * durationMs).toLong()
    val remainingMs = (durationMs - displayedMs).coerceAtLeast(0)

    Column(modifier = modifier.fillMaxWidth()) {
        // Trilho de 3dp com thumb de 10dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp) // Aumentado de 30.dp para 48.dp (trilho interno continua 3.dp)
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
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Trilho #2D2D2D
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(AuroColor.Track, RoundedCornerShape(1.5.dp))
            )

            // Preenchimento com Accent A
            Box(
                modifier = Modifier
                    .fillMaxWidth(activeFraction)
                    .height(3.dp)
                    .background(accent, RoundedCornerShape(1.5.dp))
            )

            // Bolinha branca de 10dp
            Box(
                modifier = Modifier
                    .fillMaxWidth(activeFraction)
                    .wrapContentWidth(Alignment.End)
                    .size(10.dp)
                    .background(Color.White, CircleShape)
            )
        }

        // Tempos (ex: 1:24 e -3:41 conforme o espécime)
        val tabularStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTimeMs(displayedMs),
                color = AuroColor.TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                style = tabularStyle
            )
            Text(
                text = if (showRemainingTime) "-${formatTimeMs(remainingMs)}" else formatTimeMs(durationMs),
                color = AuroColor.TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                style = tabularStyle,
                modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
            )
        }
    }
}

/**
 * 2. MINI PLAYER EM VIDRO PRETO (AURŌ GLASS CARD)
 * Posicionado embaixo na tela (próximo à luz global em y=0.88), captura os reflexos na borda inferior.
 */
@Composable
fun AuroMiniPlayer(
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

    val accent = LocalAuroAccent.current.a.value
    val progressFraction = if (durationMs > 0) {
        (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    AuroGlass(
        kind = AuroGlassKind.Card,
        onClick = onOpenNowPlaying,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Linha fina de progresso de 2dp no topo do vidro
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(AuroColor.Track)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(accent)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Capa quadrada (40x40) com cantos arredondados
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AuroColor.Track),
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
                            tint = AuroColor.TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Título e Artista
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = AuroColor.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
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

                // Ações de reprodução
                IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Próxima",
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * 3. NAVBAR INFERIOR PERSISTENTE (AURŌ NAVBAR)
 * Senta diretamente sobre o fundo preto #000000, com linha de 1dp Hairline no topo.
 */
@Composable
fun AuroNavbar(
    currentScreen: NavScreen,
    onEqClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AuroColor.Black,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = AuroColor.Hairline)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AuroNavbarTab(
                icon = Icons.Rounded.GraphicEq,
                label = "Equalizador",
                isActive = currentScreen is NavScreen.Equalizer,
                onClick = onEqClick
            )

            AuroNavbarTab(
                icon = Icons.Rounded.LibraryMusic,
                label = "Biblioteca",
                isActive = currentScreen !is NavScreen.Equalizer && currentScreen !is NavScreen.Search && currentScreen !is NavScreen.NowPlaying,
                onClick = onLibraryClick
            )

            AuroNavbarTab(
                icon = Icons.Rounded.Search,
                label = "Buscar",
                isActive = currentScreen is NavScreen.Search,
                onClick = onSearchClick
            )

            AuroNavbarTab(
                icon = Icons.Rounded.Menu,
                label = "Menu",
                isActive = false,
                onClick = onMenuClick
            )
        }
    }
}

@Composable
private fun AuroNavbarTab(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val tint = if (isActive) AuroColor.TextPrimary else AuroColor.TextTertiary

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
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * 4. LINHA DE FAIXA NA LISTA (AURŌ TRACK ROW)
 * Regra: "Linha de lista: título 14sp TextPrimary, artista 11,5sp TextSecondary, fio Hairline. Sem vidro."
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuroTrackRow(
    track: Track,
    trackIndex: Int,
    isPlayingThis: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAuroAccent.current.a.value

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicador de número / tocando
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isPlayingThis) {
                Icon(
                    imageVector = Icons.Rounded.Equalizer,
                    contentDescription = "Tocando",
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = "${trackIndex + 1}",
                    color = AuroColor.TextTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                )
            }
        }

        // Título e Artista
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isPlayingThis) accent else AuroColor.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${track.artist} • ${track.album}",
                    color = AuroColor.TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (track.rating > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "★ ${track.rating}",
                        color = accent,
                        fontSize = 10.sp,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Duração da faixa
        Text(
            text = formatTimeMs(track.durationMs),
            color = AuroColor.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Menu da faixa
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Mais opções",
                tint = AuroColor.TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 5. CABEÇALHO BREADCRUMB (AURŌ BREADCRUMB HEADER)
 */
data class AuroBreadcrumbItem(val title: String)

@Composable
fun AuroBreadcrumbHeader(
    items: List<AuroBreadcrumbItem>,
    onItemClick: (Int) -> Unit,
    canGoBack: Boolean = false,
    onBackClick: () -> Unit = {},
    rightActions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = AuroColor.Black,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .border(width = 1.dp, color = AuroColor.Hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isLast = index == items.size - 1
                    Text(
                        text = item.title,
                        color = if (isLast) AuroColor.TextPrimary else AuroColor.TextSecondary,
                        fontSize = if (isLast) 16.sp else 13.sp,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onItemClick(index) }
                    )
                    if (!isLast) {
                        Text(
                            text = " / ",
                            color = AuroColor.TextTertiary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = rightActions
            )
        }
    }
}

/**
 * 6. INDEXADOR ALFABÉTICO RÁPIDO (A-Z SCROLLER)
 */
@Composable
fun AuroAlphabetFastScroller(
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val alphabet = remember { listOf('#') + ('A'..'Z').toList() }
    var selectedLetter by remember { mutableStateOf<Char?>(null) }

    Box(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .pointerInput(alphabet) {
                    detectTapGestures { offset ->
                        val itemHeight = size.height / alphabet.size.toFloat()
                        val index = (offset.y / itemHeight).toInt().coerceIn(0, alphabet.size - 1)
                        val letter = alphabet[index]
                        if (letter != selectedLetter) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedLetter = letter
                            onLetterSelected(letter)
                        } else {
                            onLetterSelected(letter)
                        }
                    }
                }
                .pointerInput(alphabet) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val itemHeight = size.height / alphabet.size.toFloat()
                            val index = (offset.y / itemHeight).toInt().coerceIn(0, alphabet.size - 1)
                            val letter = alphabet[index]
                            if (letter != selectedLetter) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedLetter = letter
                                onLetterSelected(letter)
                            }
                        },
                        onDragEnd = { selectedLetter = null },
                        onDragCancel = { selectedLetter = null }
                    ) { change, _ ->
                        change.consume()
                        val itemHeight = size.height / alphabet.size.toFloat()
                        val index = (change.position.y / itemHeight).toInt().coerceIn(0, alphabet.size - 1)
                        val letter = alphabet[index]
                        if (letter != selectedLetter) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedLetter = letter
                            onLetterSelected(letter)
                        }
                    }
                },
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            alphabet.forEach { letter ->
                Text(
                    text = letter.toString(),
                    color = if (letter == selectedLetter) AuroColor.TextPrimary else AuroColor.TextTertiary,
                    fontSize = 9.sp,
                    fontWeight = if (letter == selectedLetter) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // Balão popup flutuante em vidro preto
        selectedLetter?.let { letter ->
            Box(
                modifier = Modifier
                    .padding(end = 36.dp)
                    .align(Alignment.Center)
            ) {
                AuroGlass(
                    modifier = Modifier.size(48.dp),
                    kind = AuroGlassKind.Card
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = letter.toString(),
                            color = AuroColor.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 7. CLASSIFICAÇÃO DE 5 ESTRELAS
 */
@Composable
fun AuroRatingBar(
    rating: Int,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAuroAccent.current.a.value

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) {
            val isFilled = i <= rating
            IconButton(
                onClick = {
                    val newRating = if (rating == i) 0 else i
                    onRatingSelected(newRating)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isFilled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "$i estrelas",
                    tint = if (isFilled) accent else AuroColor.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (rating > 0) {
            Text(
                text = "$rating/5",
                color = AuroColor.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * 8. WAVESEEK ADAPTADO AO AURŌ DESIGN SYSTEM
 */
@Composable
fun AuroWaveformSeekbar(
    progressFraction: Float,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val accent = LocalAuroAccent.current.a.value
    val barCount = 70
    val wavePeaks = remember {
        FloatArray(barCount) { i ->
            val base = 0.22f + 0.52f * kotlin.math.abs(kotlin.math.sin(i * 0.18)).toFloat()
            val variation = 0.18f * kotlin.math.abs(kotlin.math.cos(i * 0.42)).toFloat()
            (base + variation).coerceIn(0.12f, 0.95f)
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
                    color = if (isPlayed) accent else AuroColor.Track,
                    start = androidx.compose.ui.geometry.Offset(x, centerY - halfH),
                    end = androidx.compose.ui.geometry.Offset(x, centerY + halfH),
                    strokeWidth = barWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }

            // Agulha de reprodução branca (specular spark)
            val needleX = (activeFraction * totalWidth).coerceIn(0f, totalWidth)
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(needleX, 2f),
                end = androidx.compose.ui.geometry.Offset(needleX, totalHeight - 2f),
                strokeWidth = 2.5f
            )
        }
    }
}
