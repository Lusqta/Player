package com.lucas.player.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.data.CoverArtLoader
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

@Composable
fun rememberTrackCover(track: Track?, size: Int = 300): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(track?.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(track?.id) {
        if (track != null) {
            CoverArtLoader.loadCover(context, track, size) { bmp ->
                bitmap = bmp
            }
        } else {
            bitmap = null
        }
    }
    return bitmap
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
    val view = LocalView.current
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
                            AuroHaptics.click(view)
                        },
                        onDragEnd = {
                            onSeek(dragFraction)
                            isDragging = false
                            AuroHaptics.confirm(view)
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    ) { change, _ ->
                        change.consume()
                        val newFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                        if ((newFrac * 20).toInt() != (dragFraction * 20).toInt()) {
                            AuroHaptics.sliderTick(view)
                        }
                        dragFraction = newFrac
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        AuroHaptics.click(view)
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
                val view = LocalView.current

                // Capa quadrada (40x40) com cantos arredondados
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = "Capa",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    AuroCoverPlaceholder(
                        modifier = Modifier.size(40.dp),
                        glass = true,
                        shape = RoundedCornerShape(8.dp)
                    )
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
                IconButton(onClick = { AuroHaptics.click(view); onPrevious() }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = { AuroHaptics.confirm(view); onPlayPause() }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(onClick = { AuroHaptics.click(view); onNext() }, modifier = Modifier.size(44.dp)) {
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
    val view = LocalView.current

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
                onClick = {
                    AuroHaptics.click(view)
                    onClick()
                }
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
    modifier: Modifier = Modifier,
    hasError: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    showFormatTag: Boolean = true
) {
    val accent = LocalAuroAccent.current.a.value
    val view = LocalView.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    AuroHaptics.click(view)
                    onClick()
                },
                onLongClick = {
                    if (!isSelectionMode) {
                        AuroHaptics.longPress(view)
                        onLongClick()
                    }
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicador de Seleção Múltipla (Fase 4.3): 24dp à esquerda
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accent else Color.Transparent)
                    .border(
                        width = if (isSelected) 0.dp else 1.5.dp,
                        color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.30f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        val coverBitmap = rememberTrackCover(track, 100)

        if (coverBitmap != null) {
            // Com capa: miniatura de 42dp
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
                if (isPlayingThis) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = "Tocando",
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            // Sem capa: mantém o número da faixa exatamente como antes
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
        }

        // Título e Artista
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasError) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = "Erro de reprodução",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = track.title,
                    color = if (hasError) Color(0xFFFF5252) else if (isPlayingThis) accent else AuroColor.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

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

        // Tag de formato de áudio (FLAC, MP3, etc.)
        if (showFormatTag && track.formatTag.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = track.formatTag,
                    color = AuroColor.TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Duração da faixa
        Text(
            text = formatTimeMs(track.durationMs),
            color = AuroColor.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
        )

        if (!isSelectionMode) {
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
 * AURŌ SWITCH (Fase 4.2)
 * Pílula 48×28dp preta com contorno de 1dp em branco 15% e bolinha branca de 20dp.
 * Ligado: bolinha à direita e contorno em Accent A a 60%.
 */
@Composable
fun AuroSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val accent = LocalAuroAccent.current.a.value
    val view = LocalView.current
    val reduceMotion = com.lucas.player.ui.theme.rememberReduceMotion()

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = if (reduceMotion) tween(0) else com.lucas.player.ui.theme.AuroMotion.liquid(),
        label = "switchThumb"
    )

    val borderColor by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.15f),
        animationSpec = if (reduceMotion) tween(0) else com.lucas.player.ui.theme.AuroMotion.liquid(),
        label = "switchBorder"
    )

    val toggleModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = {
                AuroHaptics.click(view)
                onCheckedChange(it)
            }
        )
    } else Modifier

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AuroColor.Black)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .then(toggleModifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * AURŌ SLIDER (Fase 4.2)
 * Slider minimalista alinhado à barra de progresso, com suporte completo a acessibilidade (semantics)
 * e resposta tátil com tick a cada passagem de degrau.
 */
@Composable
fun AuroSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val accent = LocalAuroAccent.current.a.value
    val view = LocalView.current
    var lastTickValue by remember { mutableFloatStateOf(value) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = valueRange,
                    steps = steps
                )
                setProgress { target ->
                    val clamped = target.coerceIn(valueRange.start, valueRange.endInclusive)
                    onValueChange(clamped)
                    true
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val rangeSpan = valueRange.endInclusive - valueRange.start
                    if (rangeSpan > 0f && size.width > 0) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * rangeSpan
                        AuroHaptics.sliderTick(view)
                        onValueChange(newValue)
                        onValueChangeFinished?.invoke()
                    }
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val rangeSpan = valueRange.endInclusive - valueRange.start
                        if (rangeSpan > 0f && size.width > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val newValue = valueRange.start + fraction * rangeSpan
                            AuroHaptics.sliderTick(view)
                            onValueChange(newValue)
                        }
                    },
                    onDragEnd = {
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        onValueChangeFinished?.invoke()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val rangeSpan = valueRange.endInclusive - valueRange.start
                        if (rangeSpan > 0f && size.width > 0) {
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            val newValue = valueRange.start + fraction * rangeSpan
                            val tickThreshold = if (steps > 0) rangeSpan / (steps + 1) else (rangeSpan / 20f).coerceAtLeast(0.05f)
                            if (kotlin.math.abs(newValue - lastTickValue) >= tickThreshold) {
                                AuroHaptics.sliderTick(view)
                                lastTickValue = newValue
                            }
                            onValueChange(newValue)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
        val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

        // Linha de fundo (trilho inativo)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.15f))
        )

        // Trilho ativo com preenchimento Accent A
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )

        // Bolinha / Indicador (Thumb)
        val thumbRadius = 8.dp
        val thumbOffset = ((totalWidth - thumbRadius * 2) * fraction).coerceAtLeast(0.dp)
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbRadius * 2)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, accent.copy(alpha = 0.8f), CircleShape)
        )
    }
}


