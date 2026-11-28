package com.lucas.player.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistStyleColorTest {

    @Test
    fun fitVibrantPageColor_achromaticProducesRichGraphite() {
        val pureWhite = GlowTone.fitVibrantPageColor(0xFFFFFFFF.toInt())
        val hslWhite = GlowTone.rgbToHsl(pureWhite.red, pureWhite.green, pureWhite.blue)
        assertEquals(0f, hslWhite[1], 0.01f)
        assertTrue("Expected lightness in 0.16..0.24, got ${hslWhite[2]}", hslWhite[2] in 0.155f..0.245f)

        val neutralGray = GlowTone.fitVibrantPageColor(0xFF707070.toInt())
        val hslGray = GlowTone.rgbToHsl(neutralGray.red, neutralGray.green, neutralGray.blue)
        assertEquals(0f, hslGray[1], 0.01f)
        assertTrue("Expected lightness in 0.16..0.24, got ${hslGray[2]}", hslGray[2] in 0.155f..0.245f)
    }

    @Test
    fun fitVibrantPageColor_chromaticClampsSaturationAndLightness() {
        // Pastel red (high lightness, lower saturation)
        val pastelRed = GlowTone.fitVibrantPageColor(0xFFFFB3BA.toInt())
        val hslPastel = GlowTone.rgbToHsl(pastelRed.red, pastelRed.green, pastelRed.blue)
        assertTrue("Expected saturation in 0.60..0.82, got ${hslPastel[1]}", hslPastel[1] in 0.59f..0.83f)
        assertTrue("Expected lightness in 0.22..0.34, got ${hslPastel[2]}", hslPastel[2] in 0.21f..0.35f)

        // Deep saturated blue
        val deepBlue = GlowTone.fitVibrantPageColor(0xFF0014FF.toInt())
        val hslBlue = GlowTone.rgbToHsl(deepBlue.red, deepBlue.green, deepBlue.blue)
        assertTrue("Expected saturation in 0.60..0.82, got ${hslBlue[1]}", hslBlue[1] in 0.59f..0.83f)
        assertTrue("Expected lightness in 0.22..0.34, got ${hslBlue[2]}", hslBlue[2] in 0.21f..0.35f)
    }

    @Test
    fun clampManualPageColor_capsBrightLightnessAt042AndPreservesHueAndSaturation() {
        // Neon yellow (very bright: L=0.50, S=1.0)
        val neonYellow = GlowTone.clampManualPageColor(0xFFFFFF00.toInt())
        val hslYellow = GlowTone.rgbToHsl(neonYellow.red, neonYellow.green, neonYellow.blue)
        assertTrue("Expected lightness <= 0.425, got ${hslYellow[2]}", hslYellow[2] <= 0.425f)
        assertTrue("Expected saturation preserved near 1.0, got ${hslYellow[1]}", hslYellow[1] > 0.95f)

        // Pure white -> clamped to 0.42 lightness
        val white = GlowTone.clampManualPageColor(0xFFFFFFFF.toInt())
        val hslWhite = GlowTone.rgbToHsl(white.red, white.green, white.blue)
        assertTrue("Expected white clamped <= 0.425, got ${hslWhite[2]}", hslWhite[2] <= 0.425f)

        // Already dark crimson -> stays unchanged
        val darkCrimson = 0xFF8B1A2B.toInt()
        val inputHsl = GlowTone.rgbToHsl(
            ((darkCrimson shr 16) and 0xFF) / 255f,
            ((darkCrimson shr 8) and 0xFF) / 255f,
            (darkCrimson and 0xFF) / 255f
        )
        val clampedCrimson = GlowTone.clampManualPageColor(darkCrimson)
        val outHsl = GlowTone.rgbToHsl(clampedCrimson.red, clampedCrimson.green, clampedCrimson.blue)
        assertEquals(inputHsl[0], outHsl[0], 0.02f)
        assertEquals(inputHsl[1], outHsl[1], 0.02f)
        assertEquals(inputHsl[2], outHsl[2], 0.02f)
    }
}
