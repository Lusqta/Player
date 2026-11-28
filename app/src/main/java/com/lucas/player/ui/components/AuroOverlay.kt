package com.lucas.player.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lucas.player.ui.theme.AuroMotion
import com.lucas.player.ui.theme.AuroRadius
import com.lucas.player.ui.theme.rememberReduceMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

enum class AuroOverlayType {
    Dialog,
    BottomSheet
}

/**
 * Portal central de overlays no root da Activity para que sheets/diálogos lançados de telas internas
 * sempre renderizem acima do MiniPlayer e da EditorialFloatingNavbar com o mesmo scrim.
 */
class AuroOverlayPortalState {
    private val entries = mutableStateListOf<Pair<Any, @Composable () -> Unit>>()

    val activeEntries: List<Pair<Any, @Composable () -> Unit>>
        get() = entries

    fun set(key: Any, content: @Composable () -> Unit) {
        val idx = entries.indexOfFirst { it.first === key }
        if (idx >= 0) {
            entries[idx] = key to content
        } else {
            entries.add(key to content)
        }
    }

    fun remove(key: Any) {
        entries.removeAll { it.first === key }
    }
}

val LocalAuroOverlayPortal = staticCompositionLocalOf<AuroOverlayPortalState?> { null }

@Composable
fun AuroOverlayPortalHost(
    portal: AuroOverlayPortalState,
    modifier: Modifier = Modifier
) {
    if (portal.activeEntries.isNotEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            portal.activeEntries.forEach { (entryKey, entryContent) ->
                key(entryKey) {
                    entryContent()
                }
            }
        }
    }
}

/**
 * AURŌ DESIGN SYSTEM v3 - SISTEMA CENTRAL DE OVERLAYS (Fase 2.3 & 2.4)
 * - Scrim preto 72% com fade de 200ms na entrada e 140ms na saída.
 * - Diálogos entram de escala 0.94 para 1.0 com mola líquida e fade, saem com fade de 140ms.
 * - BottomSheets sobem com spring(0.85f, 300f), fecham com arrasto vertical ou mola.
 * - PredictiveBackHandler nativo: escala (1 - 0.08p), raio (+28dp) e alpha (1 - 0.3p).
 * - Respeita rememberReduceMotion() e anima apenas na fase de graphicsLayer / desenho.
 */
@Composable
fun AuroOverlay(
    visible: Boolean = true,
    onDismiss: () -> Unit,
    type: AuroOverlayType = AuroOverlayType.Dialog,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val portal = LocalAuroOverlayPortal.current
    if (portal != null) {
        val portalKey = remember { Any() }
        val latestVisible by rememberUpdatedState(visible)
        val latestOnDismiss by rememberUpdatedState(onDismiss)
        val latestType by rememberUpdatedState(type)
        val latestModifier by rememberUpdatedState(modifier)
        val latestContent by rememberUpdatedState(content)

        DisposableEffect(portal) {
            onDispose { portal.remove(portalKey) }
        }
        SideEffect {
            portal.set(portalKey) {
                AuroOverlayInternal(
                    visible = latestVisible,
                    onDismiss = latestOnDismiss,
                    type = latestType,
                    modifier = latestModifier,
                    content = latestContent
                )
            }
        }
    } else {
        AuroOverlayInternal(
            visible = visible,
            onDismiss = onDismiss,
            type = type,
            modifier = modifier,
            content = content
        )
    }
}

@Composable
private fun AuroOverlayInternal(
    visible: Boolean = true,
    onDismiss: () -> Unit,
    type: AuroOverlayType = AuroOverlayType.Dialog,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(visible) }
    val coroutineScope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()

    // Sincroniza se o estado externo mudar
    LaunchedEffect(visible) {
        isVisible = visible
    }

    fun triggerDismiss() {
        if (reduceMotion) {
            onDismiss()
        } else {
            isVisible = false
            coroutineScope.launch {
                val dismissDelay = if (type == AuroOverlayType.BottomSheet) 220L else 140L
                delay(dismissDelay)
                onDismiss()
            }
        }
    }

    // Voltar preditivo (Fase 2.4)
    val backProgress = remember { Animatable(0f) }

    PredictiveBackHandler(enabled = isVisible) { progressFlow: Flow<BackEventCompat> ->
        try {
            progressFlow.collect { event ->
                if (!reduceMotion) {
                    backProgress.snapTo(event.progress)
                }
            }
            triggerDismiss()
        } catch (_: CancellationException) {
            if (!reduceMotion) {
                backProgress.animateTo(0f, AuroMotion.spatialDefault())
            }
        } finally {
            if (!reduceMotion) {
                backProgress.snapTo(0f)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (type == AuroOverlayType.Dialog) Alignment.Center else Alignment.BottomCenter
    ) {
        // Scrim preto a 72%
        AnimatedVisibility(
            visible = isVisible,
            enter = if (reduceMotion) EnterTransition.None else fadeIn(AuroMotion.effectsFast()),
            exit = if (reduceMotion) ExitTransition.None else fadeOut(AuroMotion.effectsFast())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { triggerDismiss() }
                    )
            )
        }

        // Painel do Overlay de acordo com o tipo
        if (type == AuroOverlayType.Dialog) {
            AnimatedVisibility(
                visible = isVisible,
                enter = if (reduceMotion) EnterTransition.None else {
                    scaleIn(
                        animationSpec = AuroMotion.spatialDefault(),
                        initialScale = 0.94f
                    ) + fadeIn(AuroMotion.effectsFast())
                },
                exit = if (reduceMotion) ExitTransition.None else {
                    scaleOut(
                        animationSpec = AuroMotion.effectsFast(),
                        targetScale = 0.94f
                    ) + fadeOut(AuroMotion.effectsFast())
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        val p = backProgress.value
                        scaleX = 1f - 0.08f * p
                        scaleY = 1f - 0.08f * p
                        alpha = 1f - 0.3f * p
                        shape = RoundedCornerShape((AuroRadius.card.value + 28f * p).dp)
                        clip = p > 0f
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* Bloqueia clique para não fechar o scrim */ }
                    )
            ) {
                content()
            }
        } else {
            val sheetDragOffset = remember { Animatable(0f) }

            AnimatedVisibility(
                visible = isVisible,
                enter = if (reduceMotion) EnterTransition.None else {
                    slideInVertically(
                        animationSpec = AuroMotion.spatialDefault()
                    ) { it } + fadeIn(AuroMotion.effectsFast())
                },
                exit = if (reduceMotion) ExitTransition.None else {
                    slideOutVertically(
                        animationSpec = AuroMotion.spatialDefault()
                    ) { it } + fadeOut(AuroMotion.effectsFast())
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        val p = backProgress.value
                        scaleX = 1f - 0.08f * p
                        scaleY = 1f - 0.08f * p
                        alpha = 1f - 0.3f * p
                        translationY = sheetDragOffset.value
                        shape = RoundedCornerShape(
                            topStart = (AuroRadius.card.value + 28f * p).dp,
                            topEnd = (AuroRadius.card.value + 28f * p).dp
                        )
                        clip = true
                    }
                    .pointerInput(reduceMotion) {
                        if (reduceMotion) return@pointerInput
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (sheetDragOffset.value > 140f) {
                                    triggerDismiss()
                                } else {
                                    coroutineScope.launch {
                                        sheetDragOffset.animateTo(0f, AuroMotion.spatialDefault())
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    sheetDragOffset.animateTo(0f, AuroMotion.spatialDefault())
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount > 0 || sheetDragOffset.value > 0) {
                                    change.consume()
                                    val newOffset = (sheetDragOffset.value + dragAmount).coerceAtLeast(0f)
                                    coroutineScope.launch {
                                        sheetDragOffset.snapTo(newOffset)
                                    }
                                }
                            }
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* Bloqueia clique para não fechar o scrim */ }
                    )
            ) {
                content()
            }
        }
    }
}
