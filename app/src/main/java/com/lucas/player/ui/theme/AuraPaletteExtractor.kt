package com.lucas.player.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

data class AuraPalette(
    val base: Color = Color(0xFF0B081B),       // Indigo/Obsidian profundo (#0B081B)
    val primary: Color = Color(0xFF6C4AB6),    // Cor dominante vibrante da capa
    val secondary: Color = Color(0xFF8D72E1),  // Cor secundária iluminada
    val accent: Color = Color(0xFF00E5FF)      // Luz alta quente/fria (Aura glow)
)

object AuraPaletteExtractor {
    fun extract(bitmap: Bitmap?): AuraPalette {
        if (bitmap == null) return AuraPalette()
        return try {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val vibrant = palette.vibrantSwatch
            val dominant = palette.dominantSwatch
            val muted = palette.mutedSwatch
            val lightVibrant = palette.lightVibrantSwatch
            val darkVibrant = palette.darkVibrantSwatch

            val primInt = vibrant?.rgb ?: dominant?.rgb ?: 0xFF6C4AB6.toInt()
            val secInt = lightVibrant?.rgb ?: muted?.rgb ?: 0xFF8D72E1.toInt()
            val accInt = palette.lightMutedSwatch?.rgb ?: lightVibrant?.rgb ?: 0xFF00E5FF.toInt()
            val basInt = darkVibrant?.rgb ?: 0xFF0B081B.toInt()

            AuraPalette(
                base = Color(basInt).copy(alpha = 1f),
                primary = Color(primInt).copy(alpha = 1f),
                secondary = Color(secInt).copy(alpha = 1f),
                accent = Color(accInt).copy(alpha = 1f)
            )
        } catch (_: Exception) {
            AuraPalette()
        }
    }
}
