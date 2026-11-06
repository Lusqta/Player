package com.lucas.player.ui.screens

import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lucas.player.R
import com.lucas.player.model.Track
import com.lucas.player.queue.QueueSection
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroType
import com.lucas.player.ui.theme.AuroMotion
import com.lucas.player.ui.theme.LocalAuroAccent
import com.lucas.player.ui.theme.LocalAuroBottomInset
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * AURŌ DESIGN SYSTEM v3 - TELA DEDICADA DE FILA DE REPRODUÇÃO (AURŌ QUEUE SCREEN)
 * Fase 4.1: Suporte completo a reordenação por arrasto (drag & drop) com toque longo,
 * alça de 24dp, abertura de espaço com animateItem(), rolagem automática nas bordas,
 * resposta tátil sliderTick a cada linha cruzada, snackbar "Faixa movida" com "Desfazer",
 * e ações de acessibilidade no leitor de tela ("Mover para cima" / "Mover para baixo").
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
    onMoveItem: (section: QueueSection, from: Int, to: Int) -> Unit = { _, _, _ -> },
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    errorTrackIds: Set<Long> = emptySet(),
    showSnackbar: ((message: String, actionLabel: String?, onAction: (() -> Unit)?) -> Unit)? = null
) {
    val totalCount = priorityTracks.size + regularTracks.size
    val totalDuration = priorityTracks.sumOf { it.durationMs } + regularTracks.sumOf { it.durationMs }
    val view = LocalView.current
    val density = LocalDensity.current

    // Listas locais para renderização instantânea e reativa durante o gesto de arrasto
    var localPriorityTracks by remember(priorityTracks) { mutableStateOf(priorityTracks) }
    var localRegularTracks by remember(regularTracks) { mutableStateOf(regularTracks) }

    // Estados do Item Arrastado
    var draggingSection by remember { mutableStateOf<QueueSection?>(null) }
    var draggingInitialIndex by remember { mutableStateOf<Int?>(null) }
    var draggingCurrentIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }

    // Sincroniza as listas locais quando a reprodução ou serviço atualiza externamente
    LaunchedEffect(priorityTracks) {
        if (draggingSection != QueueSection.PRIORITY) {
            localPriorityTracks = priorityTracks
        }
    }
    LaunchedEffect(regularTracks) {
        if (draggingSection != QueueSection.QUEUE) {
            localRegularTracks = regularTracks
        }
    }

    // Rolagem automática suave de 60fps perto das bordas superior e inferior
    LaunchedEffect(draggingSection) {
        if (draggingSection == null) return@LaunchedEffect
        while (isActive && draggingSection != null) {
            val viewportH = listState.layoutInfo.viewportSize.height
            if (viewportH > 0) {
                val edgeThreshold = with(density) { 70.dp.toPx() }
                if (dragPointerY > 0 && dragPointerY < edgeThreshold) {
                    val factor = ((edgeThreshold - dragPointerY) / edgeThreshold).coerceIn(0.2f, 1f)
                    listState.scrollBy(-18f * factor)
                } else if (dragPointerY > viewportH - edgeThreshold) {
                    val factor = ((dragPointerY - (viewportH - edgeThreshold)) / edgeThreshold).coerceIn(0.2f, 1f)
                    listState.scrollBy(18f * factor)
                }
            }
            delay(16)
        }
    }

    val itemHeightPx = with(density) { 68.dp.toPx() }
    val context = LocalContext.current
    val movedString = stringResource(R.string.queue_track_moved)
    val undoString = stringResource(R.string.undo)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroBreadcrumbHeader(
            items = listOf(
                AuroBreadcrumbItem(stringResource(R.string.library_breadcrumb)),
                AuroBreadcrumbItem(stringResource(R.string.queue_breadcrumb))
            ),
            onItemClick = { onBackClick() },
            canGoBack = true,
            onBackClick = onBackClick,
            rightActions = {
                if (totalCount > 0) {
                    IconButton(onClick = onShuffleQueue, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = stringResource(R.string.cd_shuffle),
                            tint = AuroColor.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onClearQueue, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_clear_queue),
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
                modifier = Modifier.padding(bottom = 0.dp)
            )
        } else {
            // Banner informativo da fila
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.queue_total_header),
                        style = AuroType.Caption.copy(
                            color = AuroColor.TextTertiary,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${pluralStringResource(R.plurals.tracks_count, totalCount, totalCount)} • ${formatTimeMs(totalDuration)}",
                        style = AuroType.Body.copy(fontWeight = FontWeight.Medium)
                    )
                }

                if (localPriorityTracks.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(R.plurals.priority_tracks_count, localPriorityTracks.size, localPriorityTracks.size),
                        style = AuroType.Label.copy(
                            color = LocalAuroAccent.current.a.value,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalAuroBottomInset.current + 24.dp)
            ) {
                // Seção Prioritária (A SEGUIR)
                if (localPriorityTracks.isNotEmpty()) {
                    item(key = "header_priority") {
                        Text(
                            text = stringResource(R.string.queue_section_priority),
                            style = AuroType.SectionHeader.copy(
                                color = LocalAuroAccent.current.a.value,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    itemsIndexed(localPriorityTracks, key = { _, t -> "prio_${t.id}" }) { index, track ->
                        val isThisDragging = draggingSection == QueueSection.PRIORITY && draggingCurrentIndex == index

                        Box(
                            modifier = Modifier
                                .animateItem()
                                .zIndex(if (isThisDragging) 2f else 0f)
                                .graphicsLayer {
                                    if (isThisDragging) {
                                        translationY = dragOffsetY
                                        scaleX = 1.03f
                                        scaleY = 1.03f
                                    }
                                }
                                .background(if (isThisDragging) Color.White.copy(alpha = 0.06f) else Color.Transparent)
                        ) {
                            AuroQueueTrackRow(
                                track = track,
                                index = index,
                                totalCount = localPriorityTracks.size,
                                section = QueueSection.PRIORITY,
                                isPlaying = track.id == currentTrackId,
                                isPriority = true,
                                isDragging = isThisDragging,
                                hasError = errorTrackIds.contains(track.id),
                                onClick = { onTrackClick(track, index, true) },
                                onRemove = { onRemoveTrack(track) },
                                onAccessibilityMove = { from, to ->
                                    onMoveItem(QueueSection.PRIORITY, from, to)
                                    showSnackbar?.invoke(movedString, undoString) {
                                        onMoveItem(QueueSection.PRIORITY, to, from)
                                    }
                                },
                                dragHandleModifier = Modifier.pointerInput(localPriorityTracks.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            AuroHaptics.longPress(view)
                                            draggingSection = QueueSection.PRIORITY
                                            draggingInitialIndex = index
                                            draggingCurrentIndex = index
                                            dragOffsetY = 0f
                                            dragPointerY = with(density) { (index * 68).dp.toPx() }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            dragPointerY += dragAmount.y

                                            val slotsShifted = (dragOffsetY / itemHeightPx).roundToInt()
                                            val currentIdx = draggingCurrentIndex ?: index
                                            val targetIdx = (currentIdx + slotsShifted).coerceIn(0, localPriorityTracks.size - 1)

                                            if (targetIdx != currentIdx) {
                                                AuroHaptics.sliderTick(view)
                                                val list = localPriorityTracks.toMutableList()
                                                val item = list.removeAt(currentIdx)
                                                list.add(targetIdx, item)
                                                localPriorityTracks = list
                                                dragOffsetY -= (targetIdx - currentIdx) * itemHeightPx
                                                draggingCurrentIndex = targetIdx
                                            }
                                        },
                                        onDragEnd = {
                                            val initial = draggingInitialIndex
                                            val target = draggingCurrentIndex
                                            if (initial != null && target != null && initial != target) {
                                                onMoveItem(QueueSection.PRIORITY, initial, target)
                                                showSnackbar?.invoke(movedString, undoString) {
                                                    onMoveItem(QueueSection.PRIORITY, target, initial)
                                                }
                                            }
                                            draggingSection = null
                                            draggingInitialIndex = null
                                            draggingCurrentIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingSection = null
                                            draggingInitialIndex = null
                                            draggingCurrentIndex = null
                                            dragOffsetY = 0f
                                            localPriorityTracks = priorityTracks
                                        }
                                    )
                                }
                            )
                        }
                        HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                    }
                }

                // Seção Regular
                if (localRegularTracks.isNotEmpty()) {
                    item(key = "header_regular") {
                        Text(
                            text = stringResource(R.string.queue_section_normal),
                            style = AuroType.SectionHeader.copy(
                                color = AuroColor.TextTertiary,
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    itemsIndexed(localRegularTracks, key = { _, t -> "reg_${t.id}" }) { index, track ->
                        val isThisDragging = draggingSection == QueueSection.QUEUE && draggingCurrentIndex == index

                        Box(
                            modifier = Modifier
                                .animateItem()
                                .zIndex(if (isThisDragging) 2f else 0f)
                                .graphicsLayer {
                                    if (isThisDragging) {
                                        translationY = dragOffsetY
                                        scaleX = 1.03f
                                        scaleY = 1.03f
                                    }
                                }
                                .background(if (isThisDragging) Color.White.copy(alpha = 0.06f) else Color.Transparent)
                        ) {
                            AuroQueueTrackRow(
                                track = track,
                                index = index,
                                totalCount = localRegularTracks.size,
                                section = QueueSection.QUEUE,
                                isPlaying = track.id == currentTrackId,
                                isPriority = false,
                                isDragging = isThisDragging,
                                hasError = errorTrackIds.contains(track.id),
                                onClick = { onTrackClick(track, index, false) },
                                onRemove = { onRemoveTrack(track) },
                                onAccessibilityMove = { from, to ->
                                    onMoveItem(QueueSection.QUEUE, from, to)
                                    showSnackbar?.invoke(movedString, undoString) {
                                        onMoveItem(QueueSection.QUEUE, to, from)
                                    }
                                },
                                dragHandleModifier = Modifier.pointerInput(localRegularTracks.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            AuroHaptics.longPress(view)
                                            draggingSection = QueueSection.QUEUE
                                            draggingInitialIndex = index
                                            draggingCurrentIndex = index
                                            dragOffsetY = 0f
                                            dragPointerY = with(density) { (index * 68).dp.toPx() }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            dragPointerY += dragAmount.y

                                            val slotsShifted = (dragOffsetY / itemHeightPx).roundToInt()
                                            val currentIdx = draggingCurrentIndex ?: index
                                            val targetIdx = (currentIdx + slotsShifted).coerceIn(0, localRegularTracks.size - 1)

                                            if (targetIdx != currentIdx) {
                                                AuroHaptics.sliderTick(view)
                                                val list = localRegularTracks.toMutableList()
                                                val item = list.removeAt(currentIdx)
                                                list.add(targetIdx, item)
                                                localRegularTracks = list
                                                dragOffsetY -= (targetIdx - currentIdx) * itemHeightPx
                                                draggingCurrentIndex = targetIdx
                                            }
                                        },
                                        onDragEnd = {
                                            val initial = draggingInitialIndex
                                            val target = draggingCurrentIndex
                                            if (initial != null && target != null && initial != target) {
                                                onMoveItem(QueueSection.QUEUE, initial, target)
                                                showSnackbar?.invoke(movedString, undoString) {
                                                    onMoveItem(QueueSection.QUEUE, target, initial)
                                                }
                                            }
                                            draggingSection = null
                                            draggingInitialIndex = null
                                            draggingCurrentIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingSection = null
                                            draggingInitialIndex = null
                                            draggingCurrentIndex = null
                                            dragOffsetY = 0f
                                            localRegularTracks = regularTracks
                                        }
                                    )
                                }
                            )
                        }
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
    totalCount: Int,
    section: QueueSection,
    isPlaying: Boolean,
    isPriority: Boolean,
    isDragging: Boolean,
    hasError: Boolean = false,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onAccessibilityMove: (from: Int, to: Int) -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    val accent = LocalAuroAccent.current.a.value
    val context = LocalContext.current
    val moveUpText = stringResource(R.string.queue_move_up)
    val moveDownText = stringResource(R.string.queue_move_down)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics {
                if (index > 0) {
                    customActions = customActions + CustomAccessibilityAction(moveUpText) {
                        onAccessibilityMove(index, index - 1)
                        true
                    }
                }
                if (index < totalCount - 1) {
                    customActions = customActions + CustomAccessibilityAction(moveDownText) {
                        onAccessibilityMove(index, index + 1)
                        true
                    }
                }
            },
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
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = stringResource(R.string.cd_playing_indicator),
                            tint = accent,
                            modifier = Modifier.size(18.dp)
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
                    Icon(
                        imageVector = Icons.Rounded.Equalizer,
                        contentDescription = stringResource(R.string.cd_playing_indicator),
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = if (isPriority) "★${index + 1}" else "${index + 1}",
                        style = AuroType.Caption.copy(
                            color = if (isPriority) accent else AuroColor.TextTertiary,
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum"
                        )
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasError) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = stringResource(R.string.cd_error_indicator),
                        tint = AuroColor.Danger,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = track.title,
                    style = AuroType.Body.copy(
                        color = if (hasError) AuroColor.Danger else if (isPlaying) accent else AuroColor.TextPrimary,
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                style = AuroType.Caption.copy(color = AuroColor.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatTimeMs(track.durationMs),
            style = AuroType.Caption.copy(
                color = AuroColor.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            )
        )

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_remove_item),
                tint = AuroColor.TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        // Alça de 24dp para reordenação por arrasto (Fase 4.1)
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(dragHandleModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = stringResource(R.string.queue_reorder_handle),
                tint = if (isDragging) accent else AuroColor.TextTertiary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
