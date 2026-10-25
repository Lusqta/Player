package com.lucas.player.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

data class AuraPalette(
    val deepBase: Color = Color(0xFF0E0929),      // Velvet Midnight Indigo (Nunca preto #000000)
    val primaryAura: Color = Color(0xFF5E63F0),   // Figma Royal Periwinkle Indigo
    val secondaryAura: Color = Color(0xFF3B2D8A), // Deep Royal Violet
    val accentGlow: Color = Color(0xFFA6ACFF)     // Luminous Lavender Glow
)

object AuraPaletteExtractor {

    // Paleta padrão oficial da Imagem 1 (Figma UI)
    val FIGMA_ROYAL_INDIGO = AuraPalette(
        deepBase = Color(0xFF0E0929),
        primaryAura = Color(0xFF6368F2),
        secondaryAura = Color(0xFF392B85),
        accentGlow = Color(0xFFA8AEFF)
    )

    // Paleta alternativa oficial da Imagem 2 (Aurō Warm Amber & Glacier)
    val AURO_WARM_AMBER = AuraPalette(
        deepBase = Color(0xFF1A0D0A),
        primaryAura = Color(0xFFE06A1E),
        secondaryAura = Color(0xFF5D93A6),
        accentGlow = Color(0xFFFFE5B8)
    )

    fun extractFromBitmap(bitmap: Bitmap?): AuraPalette {
        if (bitmap == null) return FIGMA_ROYAL_INDIGO

        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val vibrant = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch
        val dominant = palette.dominantSwatch

        // Se a imagem for um documento branco/cinza/preto (baixa saturação), usa a paleta Figma Royal Indigo!
        val candidateRgb = vibrant?.rgb ?: dominant?.rgb ?: return FIGMA_ROYAL_INDIGO
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(candidateRgb, hsl)

        if (hsl[1] < 0.28f) {
            return FIGMA_ROYAL_INDIGO
        }

        // Força saturação rica e luminosidade controlada de Aura
        val primaryHsl = floatArrayOf(hsl[0], hsl[1].coerceIn(0.62f, 0.90f), hsl[2].coerceIn(0.48f, 0.62f))
        val secondaryHsl = floatArrayOf((hsl[0] + 28f) % 360f, (hsl[1] * 0.85f).coerceIn(0.50f, 0.82f), 0.34f)
        val deepHsl = floatArrayOf(hsl[0], 0.58f, 0.10f)
        val glowHsl = floatArrayOf((hsl[0] + 345f) % 360f, 0.85f, 0.76f)

        return AuraPalette(
            deepBase = Color(ColorUtils.HSLToColor(deepHsl)),
            primaryAura = Color(ColorUtils.HSLToColor(primaryHsl)),
            secondaryAura = Color(ColorUtils.HSLToColor(secondaryHsl)),
            accentGlow = Color(ColorUtils.HSLToColor(glowHsl))
        )
    }
}
