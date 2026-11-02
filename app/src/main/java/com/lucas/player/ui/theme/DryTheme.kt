package com.lucas.player.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tema Totalmente "Seco" (Neutro, Preto e Branco, Sem Estilização Inventada)
 * Serve de esqueleto puro e funcional para receber qualquer estilização posterior.
 */
object DryColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF101010)
    val SurfaceElevated = Color(0xFF181818)
    val SurfaceActive = Color(0xFF242424)
    val Border = Color(0xFF2A2A2A)
    val BorderSubtle = Color(0xFF1E1E1E)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9E9E9E)
    val TextTertiary = Color(0xFF616161)

    // Neutro puro: sem azul, ciano, neon ou gradientes inventados
    val Accent = Color(0xFFFFFFFF)
    val AccentAmber = Color(0xFFE0E0E0)
    val AccentGlow = Color.Transparent

    val BadgeBg = Color(0xFF202020)
    val BadgeText = Color(0xFFD0D0D0)

    val TrackProgress = Color(0xFFFFFFFF)
    val TrackBuffered = Color(0xFF333333)
}
