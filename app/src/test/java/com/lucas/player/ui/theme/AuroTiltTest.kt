package com.lucas.player.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Testes unitários para AuroTilt (Fase 6 - Qualidade).
 * Valida a direção da luz calculada com base na gravidade do dispositivo.
 */
class AuroTiltTest {

    @Test
    fun testTiltRight_movesLightToRight() {
        // Celular inclinado para a direita (gx > 0)
        // Convenção Android: ao inclinar o lado direito para baixo, gx é positivo.
        // O reflexo da luz deve se mover para a direita (dx > 0).
        val result = AuroTilt.light(gx = 5.0f, gy = 8.5f)
        val dx = result[0]
        val dy = result[1]
        val gain = result[2]

        assertTrue("dx deve ser positivo quando gx > 0 (inclinado para a direita)", dx > 0f)
        assertTrue("dy deve ser positivo quando celular em pé (gy > 0)", dy > 0f)
        assertTrue("gain deve estar entre 0.6 e 1.0", gain in 0.6f..1.0f)
    }

    @Test
    fun testTiltLeft_movesLightToLeft() {
        // Celular inclinado para a esquerda (gx < 0)
        // O reflexo da luz deve se mover para a esquerda (dx < 0).
        val result = AuroTilt.light(gx = -5.0f, gy = 8.5f)
        val dx = result[0]
        val dy = result[1]
        val gain = result[2]

        assertTrue("dx deve ser negativo quando gx < 0 (inclinado para a esquerda)", dx < 0f)
        assertTrue("dy deve ser positivo quando celular em pé (gy > 0)", dy > 0f)
        assertTrue("gain deve estar entre 0.6 e 1.0", gain in 0.6f..1.0f)
    }

    @Test
    fun testUprightPhone_lightPointsDownwards() {
        // Celular em pé perfeitamente reto: gx = 0, gy = 9.81
        val result = AuroTilt.light(gx = 0f, gy = 9.81f)
        val dx = result[0]
        val dy = result[1]
        val gain = result[2]

        assertEquals(0f, dx, 1e-4f)
        assertTrue("dy deve apontar para baixo (> 0)", dy > 0f)
        assertEquals(1.0f, gain, 0.05f)
    }

    @Test
    fun testNormalizedVectorLength() {
        // O vetor retornado (dx, dy) deve ter norma unitária (aproximadamente 1.0)
        val result = AuroTilt.light(gx = 4.2f, gy = 7.1f)
        val len = sqrt(result[0] * result[0] + result[1] * result[1])
        assertEquals(1.0f, len, 1e-4f)
    }
}
