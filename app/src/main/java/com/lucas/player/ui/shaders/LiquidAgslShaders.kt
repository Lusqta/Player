package com.lucas.player.ui.shaders

import org.intellij.lang.annotations.Language

object LiquidAgslShaders {

    /**
     * 1. AURA MESH + FROSTED GRAIN SHADER (Inspirado na referência Aurō e Figma Dark Indigo)
     * Gera um gradiente fluido orgânico iluminado com textura granulada (dither noise)
     * para evitar color banding e simular difusão luminosa física.
     */
    @Language("AGSL")
    const val AURA_BACKGROUND_SHADER = """
        uniform float2 uResolution;
        uniform float uTime;
        uniform float4 uColorBase;     // Indigo/Obsidian profundo (#0B081B)
        uniform float4 uColorPrimary;  // Cor dominante vibrante da capa
        uniform float4 uColorSecondary;// Cor secundária iluminada
        uniform float4 uColorAccent;   // Luz alta quente/fria (Aura glow)

        float hash(float2 p) {
            return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / uResolution;
            
            // Movimento líquido lento dos centros de luz
            float2 p1 = float2(0.3 + 0.2 * sin(uTime * 0.4), 0.25 + 0.15 * cos(uTime * 0.3));
            float2 p2 = float2(0.75 + 0.18 * cos(uTime * 0.35), 0.65 + 0.2 * sin(uTime * 0.45));
            float2 p3 = float2(0.5 + 0.25 * sin(uTime * 0.25), 0.85 + 0.1 * cos(uTime * 0.5));

            float d1 = exp(-length(uv - p1) * 2.6);
            float d2 = exp(-length(uv - p2) * 2.8);
            float d3 = exp(-length(uv - p3) * 3.2);

            float3 col = uColorBase.rgb;
            col = mix(col, uColorPrimary.rgb, clamp(d1 * 0.85, 0.0, 1.0));
            col = mix(col, uColorSecondary.rgb, clamp(d2 * 0.75, 0.0, 1.0));
            col += uColorAccent.rgb * (d3 * 0.55);

            // Frosted Noise (Textura granulada estilo Aurō)
            float grain = (hash(fragCoord + fract(uTime)) - 0.5) * 0.032;
            col += grain;

            return half4(clamp(col, 0.0, 1.0), 1.0);
        }
    """

    /**
     * 2. REFRACTIVE CAPSULE SLIDER SHADER (Inspirado no Slider '52' + PrismalAGSL)
     * Cria a cápsula interna líquida com refração de lente na crista direita,
     * dispersão cromática (halo azulado/branco) e luz especular na borda.
     */
    @Language("AGSL")
    const val REFRACTIVE_SLIDER_SHADER = """
        uniform float2 uResolution;
        uniform float uProgress;       // 0.0 a 1.0
        uniform float uDragIntensity;  // 0.0 (repouso) a 1.0 (arrastando)
        uniform float4 uGlowColor;

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / uResolution;
            float radius = uResolution.y * 0.5;
            
            // Cápsula externa
            float2 centerOuter = uResolution * 0.5;
            float distOuter = sdRoundedBox(fragCoord - centerOuter, centerOuter, radius);
            if (distOuter > 0.0) return half4(0.0);

            // Fundo escuro translúcido da trilha com sombra interna (Depth Inset)
            float insetGlow = smoothstep(0.0, -12.0, distOuter);
            float3 trackCol = mix(float3(0.28, 0.28, 0.34), float3(0.08, 0.08, 0.12), insetGlow);
            float trackAlpha = 0.65;

            // Cápsula interna (Progresso líquido)
            float fillWidth = max(uResolution.y, uResolution.x * clamp(uProgress, 0.0, 1.0));
            float2 centerFill = float2(fillWidth * 0.5, uResolution.y * 0.5);
            float distFill = sdRoundedBox(fragCoord - centerFill, centerFill - 2.0, radius - 2.0);

            float3 finalCol = trackCol;
            float finalAlpha = trackAlpha;

            if (distFill <= 0.0) {
                // Gradiente vítreo interno
                float edgeFactor = smoothstep(-radius, 0.0, distFill);
                
                // Menisco luminoso na ponta direita (efeito lente refrativa da Imagem 3)
                float distToRightTip = length(fragCoord - float2(fillWidth - radius * 0.55, uResolution.y * 0.38));
                float lensHighlight = exp(-distToRightTip / (radius * (0.55 + 0.2 * uDragIntensity)));

                // Dispersão cromática sutil na borda do menisco
                float3 chromaticRim = float3(0.55, 0.78, 1.0) * pow(edgeFactor, 3.0) * 0.9;
                float3 innerGlass = mix(float3(0.14, 0.15, 0.20), uGlowColor.rgb * 0.45, 0.35);
                
                finalCol = innerGlass + chromaticRim + float3(0.85, 0.92, 1.0) * (lensHighlight * 0.75);
                
                // Borda especular fina de 1.5px ao redor da cápsula interna
                float rim = smoothstep(-2.5, -0.5, distFill);
                finalCol += float3(0.9, 0.95, 1.0) * rim * 0.45;
                finalAlpha = 0.88;
            }

            // Borda especular externa da cápsula inteira
            float outerRim = smoothstep(-2.0, 0.0, distOuter) * (1.0 - uv.y * 0.6);
            finalCol += float3(1.0) * outerRim * 0.35;

            return half4(clamp(finalCol, 0.0, 1.0), finalAlpha);
        }
    """

    /**
     * 3. GLASS CARD & BUTTON SPECULAR SHADER (Inspirado em PrismalAGSL + Figma UI)
     * Aplica iluminação especular diagonal + brilho de toque reativo.
     */
    @Language("AGSL")
    const val SPECULAR_GLASS_SURFACE_SHADER = """
        uniform float2 uResolution;
        uniform float2 uTouchPoint;
        uniform float uTouchProgress;
        uniform float4 uTintColor;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / uResolution;
            
            // Iluminação diagonal de topo-esquerda para base-direita
            float diagLight = pow(1.0 - (uv.x * 0.5 + uv.y * 0.5), 2.2);
            
            // Onda de luz reativa ao toque (press_ripple)
            float distTouch = length(fragCoord - uTouchPoint) / max(uResolution.x, uResolution.y);
            float ripple = exp(-distTouch * 6.0) * (1.0 - uTouchProgress) * sin(uTouchProgress * 3.14159);

            float3 col = uTintColor.rgb + float3(0.18, 0.16, 0.28) * diagLight + float3(0.5, 0.6, 1.0) * ripple * 0.4;
            float alpha = clamp(uTintColor.a + diagLight * 0.12 + ripple * 0.15, 0.12, 0.85);

            return half4(clamp(col, 0.0, 1.0), alpha);
        }
    """
}
