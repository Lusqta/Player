package com.lucas.player.ui.shaders

import org.intellij.lang.annotations.Language

object LiquidAgslShaders {

    /**
     * 1. INNER-RIM AURA SQUIRCLE SHADER (Estética exata dos cartões Walk, Last Test, Breath Volume e Sensor Sheet)
     * - Centro preto profundo (#050508) para contraste absoluto.
     * - Luz intensa sangrando das bordas para o interior (Inner Edge Glow) com assimetria vertical.
     * - Textura micro-jateada (frosted grain) + anel especular fino de 1px na borda extrema.
     */
    @Language("AGSL")
    const val INNER_AURA_CARD_SHADER = """
        uniform float2 uResolution;
        uniform float uCornerRadius;
        uniform float uGlowSpread;     // Espessura da aura interna em px (ex: 68.0 para cards, 115.0 para sheets)
        uniform float uTopBias;        // 1.0 = brilho forte no topo (estilo card rosa), 0.25 = suave no topo
        uniform float uBottomBias;     // 1.0 = brilho forte na base (estilo card Walk âmbar / Breath laranja)
        uniform float4 uAuraColor;     // Cor principal da borda (Âmbar, Rosa-Magenta, Laranja-Coral, Verde-Sálvia)
        uniform float4 uSecondaryColor;// Cor secundária de transição
        uniform float4 uCoreColor;     // Cor do núcleo central (#050509)

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        float hash(float2 p) {
            p = fract(p * float2(123.34, 456.21));
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = uResolution * 0.5;
            float2 p = fragCoord - halfSize;
            float d = sdRoundedBox(p, halfSize, uCornerRadius);
            if (d > 0.0) return half4(0.0);

            float2 uv = fragCoord / max(uResolution, float2(1.0));

            // Distância normalizada da borda para o centro (1.0 na borda exata, 0.0 no centro escuro)
            float edgeDist = clamp(1.0 - (-d / max(uGlowSpread, 1.0)), 0.0, 1.0);
            float auraCurve = pow(edgeDist, 1.75);

            // Peso direcional (permite brilho intenso na base como no card 'Walk' ou no topo como em 'Last test')
            float verticalWeight = mix(uTopBias, uBottomBias, smoothstep(0.0, 1.0, uv.y));
            float sideWeight = pow(abs(uv.x - 0.5) * 2.0, 1.6) * 0.65;
            float totalGlow = clamp(auraCurve * (verticalWeight + sideWeight * 0.5), 0.0, 1.0);

            // Bloom secundário na base/topo
            float bottomPool = pow(uv.y, 2.4) * smoothstep(-uGlowSpread * 1.3, 0.0, d) * uBottomBias * 0.45;
            float topPool = pow(1.0 - uv.y, 2.4) * smoothstep(-uGlowSpread * 1.3, 0.0, d) * uTopBias * 0.45;

            float3 glowTint = mix(uSecondaryColor.rgb, uAuraColor.rgb, clamp(edgeDist * 1.2, 0.0, 1.0));
            float3 col = mix(uCoreColor.rgb, glowTint, clamp(totalGlow + bottomPool + topPool, 0.0, 0.96));

            // Micro-granulação jateada (frosted noise) na zona de transição da luz
            float grain = (hash(fragCoord) - 0.5) * 0.032 * totalGlow;
            col += grain;

            // Borda especular vítrea fina de 1.2px
            float rim = smoothstep(-1.8, -0.2, d);
            float3 rimLight = mix(uAuraColor.rgb, float3(1.0), 0.55);
            col = mix(col, rimLight, rim * 0.72);

            return half4(clamp(col, 0.0, 1.0), 1.0);
        }
    """

    /**
     * 2. GLOWING RIBBED & MENISCUS CAPSULE SLIDER SHADER (Fusão do Slider Âmbar 'Walk' + Slider 52)
     * Trilho escuro cavado + preenchimento líquido iluminado com estrias diagonais subtis
     * e menisco brilhante na extremidade direita.
     */
    @Language("AGSL")
    const val AURA_CAPSULE_SLIDER_SHADER = """
        uniform float2 uResolution;
        uniform float uProgress;      // 0.0 a 1.0
        uniform float uStripeMode;    // 1.0 = estrias diagonais luminosas (estilo Walk), 0.0 = vidro liso com menisco
        uniform float4 uBarColor;     // Cor principal da barra (ex: Âmbar #FF9824, Coral #F05228, Rosa #E84A9C)
        uniform float4 uGlowColor;    // Cor de luz alta (ex: Creme #FFE6A3)

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0));
            float radius = min(uResolution.y * 0.34, 18.0);
            float2 halfSize = uResolution * 0.5;
            float dTrack = sdRoundedBox(fragCoord - halfSize, halfSize, radius);
            if (dTrack > 0.0) return half4(0.0);

            // Trilho de fundo escuro translúcido com brilho interno da cor nas bordas
            float trackEdge = smoothstep(-22.0, 0.0, dTrack);
            float3 trackCol = mix(float3(0.05, 0.05, 0.07), uBarColor.rgb * 0.42, pow(trackEdge, 1.8));
            float trackRim = smoothstep(-1.5, -0.2, dTrack);
            trackCol += uBarColor.rgb * trackRim * 0.35;

            float3 finalCol = trackCol;

            // Cápsula preenchida
            float fillW = max(radius * 2.0, uResolution.x * clamp(uProgress, 0.0, 1.0));
            float2 fillHalf = float2(fillW * 0.5, halfSize.y);
            float2 fillCenter = float2(fillW * 0.5, halfSize.y);
            float dFill = sdRoundedBox(fragCoord - fillCenter, fillHalf, radius);

            // Halo externo difuso emitido pela ponta da barra sobre o trilho escuro
            if (dFill > 0.0) {
                float outerBloom = exp(-dFill * 0.12) * 0.45;
                finalCol += uBarColor.rgb * outerBloom;
            } else {
                float fillUvX = fragCoord.x / max(fillW, 1.0);
                float3 fillCol = mix(uBarColor.rgb * 0.85, uGlowColor.rgb, pow(fillUvX, 1.3) * 0.75);

                // Estrias diagonais luminosas (exatamente como na barra 'Walk' 1000020365.jpg)
                if (uStripeMode > 0.5) {
                    float diag = (fragCoord.x + fragCoord.y * 0.42) * 0.18;
                    float stripe = smoothstep(-0.2, 0.4, sin(diag));
                    fillCol = mix(fillCol * 0.86, fillCol * 1.12, stripe * 0.45);
                }

                // Menisco luminoso na ponta direita
                float tipDist = length(fragCoord - float2(fillW - radius * 0.6, halfSize.y));
                float tipGlow = exp(-tipDist / max(radius * 0.9, 1.0));
                fillCol = mix(fillCol, float3(1.0, 0.98, 0.92), tipGlow * 0.55);

                float fillRim = smoothstep(-1.6, -0.2, dFill);
                fillCol = mix(fillCol, float3(1.0, 0.96, 0.88), fillRim * 0.55);
                finalCol = fillCol;
            }

            return half4(clamp(finalCol, 0.0, 1.0), 1.0);
        }
    """

    /**
     * 3. AMBIENT DEEP BACKGROUND SHADER (Fundo atmosférico escuro azul-cobalto/obsidiana da Imagem 1)
     */
    @Language("AGSL")
    const val DEEP_AMBIENT_BG_SHADER = """
        uniform float2 uResolution;
        uniform float4 uTopGlowColor; // Azul cobalto profundo (#142266) ou cor escura da capa

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0));
            float2 centerTop = float2(0.52, 0.22);
            float d = length((uv - centerTop) * float2(1.0, 1.35));
            float glow = exp(-d * 2.8) * 0.55;
            float3 col = mix(float3(0.015, 0.015, 0.03), uTopGlowColor.rgb, glow);
            return half4(clamp(col, 0.0, 1.0), 1.0);
        }
    """

    /**
     * 4. VERTICAL CAPSULE SLIDER SHADER (Para as bandas verticais do equalizador)
     */
    @Language("AGSL")
    const val AURA_CAPSULE_VERTICAL_SLIDER_SHADER = """
        uniform float2 uResolution;
        uniform float uProgress;      // 0.0 (base) a 1.0 (topo)
        uniform float4 uBarColor;
        uniform float4 uGlowColor;

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0));
            float radius = min(uResolution.x * 0.42, 16.0);
            float2 halfSize = uResolution * 0.5;
            float dTrack = sdRoundedBox(fragCoord - halfSize, halfSize, radius);
            if (dTrack > 0.0) return half4(0.0);

            float trackEdge = smoothstep(-16.0, 0.0, dTrack);
            float3 trackCol = mix(float3(0.06, 0.06, 0.08), uBarColor.rgb * 0.35, pow(trackEdge, 1.6));
            float trackRim = smoothstep(-1.5, -0.2, dTrack);
            trackCol += uBarColor.rgb * trackRim * 0.3;

            float fillH = max(radius * 2.0, uResolution.y * clamp(uProgress, 0.0, 1.0));
            float topY = uResolution.y - fillH;
            float2 fillHalf = float2(halfSize.x, fillH * 0.5);
            float2 fillCenter = float2(halfSize.x, topY + fillH * 0.5);
            float dFill = sdRoundedBox(fragCoord - fillCenter, fillHalf, radius);

            float3 finalCol = trackCol;
            if (dFill <= 0.0) {
                float fillUvY = 1.0 - (fragCoord.y - topY) / max(fillH, 1.0);
                float3 fillCol = mix(uBarColor.rgb * 0.85, uGlowColor.rgb, pow(fillUvY, 1.3) * 0.7);
                float topTipDist = length(fragCoord - float2(halfSize.x, topY + radius * 0.6));
                float tipGlow = exp(-topTipDist / max(radius * 0.9, 1.0));
                fillCol = mix(fillCol, float3(1.0, 0.98, 0.92), tipGlow * 0.55);
                float fillRim = smoothstep(-1.5, -0.2, dFill);
                fillCol = mix(fillCol, float3(1.0, 0.96, 0.88), fillRim * 0.55);
                finalCol = fillCol;
            } else {
                float outerBloom = exp(-dFill * 0.14) * 0.4;
                finalCol += uBarColor.rgb * outerBloom;
            }
            return half4(clamp(finalCol, 0.0, 1.0), 1.0);
        }
    """
}
