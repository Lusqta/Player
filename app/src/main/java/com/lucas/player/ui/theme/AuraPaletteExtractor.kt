package com.lucas.player.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AuraPalette(
    val deepBase: Color = Color(0xFF04040A),      // Obsidian Deep Black (#04040A)
    val primaryAura: Color = Color(0xFFF5881F),   // Vibrant Inner Glow (default Walk Amber)
    val secondaryAura: Color = Color(0xFF9E4708), // Secondary Transition
    val accentGlow: Color = Color(0xFFFFD875)     // Highlight Bloom
)

object AuraPaletteExtractor {

    // Paleta padrão Amber Obsidian (Estilo Walk 4,151 steps)
    val DARK_OBSIDIAN = AuraPalette(
        deepBase = Color(0xFF04040A),
        primaryAura = Color(0xFFF5881F),
        secondaryAura = Color(0xFF9E4708),
        accentGlow = Color(0xFFFFD875)
    )

    // Paleta padrão Rosa Magenta (Estilo Last test 3.3L)
    val MAGENTA_PULSE = AuraPalette(
        deepBase = Color(0xFF04040A),
        primaryAura = Color(0xFFE655A5),
        secondaryAura = Color(0xFF7D2258),
        accentGlow = Color(0xFFFFB0DC)
    )

    // Paleta padrão Laranja Coral (Estilo Breath Volume 5.5L)
    val CORAL_BREATH = AuraPalette(
        deepBase = Color(0xFF04040A),
        primaryAura = Color(0xFFE84C27),
        secondaryAura = Color(0xFF8A220C),
        accentGlow = Color(0xFFFFA878)
    )

    // Paleta padrão Verde Sálvia (Estilo Sensor Sheet)
    val SAGE_SENSOR = AuraPalette(
        deepBase = Color(0xFF04040A),
        primaryAura = Color(0xFF88AD9E),
        secondaryAura = Color(0xFF496B5E),
        accentGlow = Color(0xFFC8E6DA)
    )

    // Paleta padrão Azul Cobalto (Estilo Training)
    val COBALT_TRAINING = AuraPalette(
        deepBase = Color(0xFF04040A),
        primaryAura = Color(0xFF2848B8),
        secondaryAura = Color(0xFF102060),
        accentGlow = Color(0xFF7898FF)
    )

    suspend fun extractFromBitmapAsync(bitmap: Bitmap?): AuraPalette = withContext(Dispatchers.Default) {
        extractFromBitmap(bitmap)
    }

    fun extractFromBitmap(bitmap: Bitmap?): AuraPalette {
        if (bitmap == null || bitmap.isRecycled) return DARK_OBSIDIAN

        // Downscale obrigatório para 64x64 em background para evitar qualquer ANR na Main Thread
        val scaled = try {
            Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        } catch (_: Exception) {
            return DARK_OBSIDIAN
        }

        val palette = Palette.from(scaled).maximumColorCount(16).generate()
        val vibrant = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch
        val dominant = palette.dominantSwatch

        val candidateRgb = vibrant?.rgb ?: dominant?.rgb ?: return DARK_OBSIDIAN
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(candidateRgb, hsl)

        // Se a capa for em tons de cinza ou monocromática, mantém Dark Obsidian
        if (hsl[1] < 0.20f) {
            return DARK_OBSIDIAN
        }

        // Garante saturação rica para aura luminosa
        val primaryHsl = floatArrayOf(hsl[0], hsl[1].coerceIn(0.65f, 0.95f), hsl[2].coerceIn(0.45f, 0.62f))
        val secondaryHsl = floatArrayOf((hsl[0] + 25f) % 360f, (hsl[1] * 0.85f).coerceIn(0.50f, 0.85f), 0.30f)
        val glowHsl = floatArrayOf((hsl[0] + 345f) % 360f, 0.88f, 0.78f)

        return AuraPalette(
            deepBase = Color(0xFF04040A),
            primaryAura = Color(ColorUtils.HSLToColor(primaryHsl)),
            secondaryAura = Color(ColorUtils.HSLToColor(secondaryHsl)),
            accentGlow = Color(ColorUtils.HSLToColor(glowHsl))
        )
    }
}
