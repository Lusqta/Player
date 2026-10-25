package com.lucas.player.ui.shaders

import org.intellij.lang.annotations.Language

object LiquidAgslShaders {

    /**
     * 1. FIGMA ROYAL INDIGO & AURŌ FROSTED RELIEF BACKGROUND SHADER
     * Cria um ambiente luminoso profundo (nunca preto chapado) com micro-relevo jateado
     * inspirado na referência 'aurō' e iluminação espacial da referência do Figma.
     */
    @Language("AGSL")
    const val AURA_BACKGROUND_SHADER = """
        uniform float2 uResolution;
        uniform float uTime;
        uniform float4 uColorDeep;      // Base noturna rica (#0E0929)
        uniform float4 uColorPrimary;   // Luz principal (#6468F0 ou #E56B1F)
        uniform float4 uColorSecondary; // Luz média (#3E2F96 ou #6CA0B0)
        uniform float4 uColorGlow;      // Aura luminosa alta (#A8AEFF ou #FFE8BE)

        float hash(float2 p) {
            p = fract(p * float2(123.34, 456.21));
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }

        float frostedGrain(float2 fragCoord) {
            float n1 = hash(floor(fragCoord * 0.5));
            float n2 = hash(floor(fragCoord * 0.25 + 19.1));
            return (n1 * 0.6 + n2 * 0.4) - 0.5;
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0));
            float aspect = uResolution.x / max(uResolution.y, 1.0);
            float2 uvA = float2(uv.x * aspect, uv.y);

            float grain = frostedGrain(fragCoord);
            float2 warpedUv = uvA + grain * 0.008;

            // 3 focos amplos de luz difusa para preencher toda a tela (sem buracos pretos)
            float2 p1 = float2(0.28 * aspect + 0.12 * sin(uTime * 0.35), 0.16 + 0.08 * cos(uTime * 0.28));
            float2 p2 = float2(0.76 * aspect + 0.10 * cos(uTime * 0.25), 0.46 + 0.10 * sin(uTime * 0.32));
            float2 p3 = float2(0.48 * aspect + 0.14 * sin(uTime * 0.22), 0.82 + 0.06 * cos(uTime * 0.38));

            float g1 = exp(-length(warpedUv - p1) * 1.45);
            float g2 = exp(-length(warpedUv - p2) * 1.65);
            float g3 = exp(-length(warpedUv - p3) * 1.75);

            // Gradiente vertical base iluminado (estilo Figma)
            float3 baseGrad = mix(uColorSecondary.rgb * 0.55, uColorDeep.rgb, smoothstep(0.0, 0.95, uv.y));

            float3 col = baseGrad;
            col = mix(col, uColorPrimary.rgb, clamp(g1 * 0.88, 0.0, 1.0));
            col = mix(col, uColorSecondary.rgb, clamp(g2 * 0.76, 0.0, 1.0));
            col = mix(col, uColorGlow.rgb, clamp(pow(g3, 1.5) * 0.48, 0.0, 1.0));

            // Micro-relevo fosco iluminado (efeito Aurō)
            col += grain * 0.026 * (0.5 + g1 + g3);

            return half4(clamp(col, 0.0, 1.0), 1.0);
        }
    """

    /**
     * 2. REAL SDF LENS REFRACTION & CHROMATIC ABERRATION SHADER (iOS 26 / PrismalAGSL)
     * Usa 'uniform shader content;' para dobrar a imagem real de fundo (já desfocada)
     * nas bordas curvas do vidro, adicionando dispersão RGB e luz especular diagonal.
     */
    @Language("AGSL")
    const val REAL_GLASS_LENS_SHADER = """
        uniform shader content;
        uniform float2 uResolution;
        uniform float uCornerRadius;
        uniform float uRefractionPx;   // Força da lente na borda (ex: 24.0)
        uniform float uDispersion;     // Aberração cromática (ex: 0.32)
        uniform float4 uTint;          // Cor translúcida do vidro

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = uResolution * 0.5;
            float2 p = fragCoord - halfSize;
            float d = sdRoundedBox(p, halfSize, uCornerRadius);

            // Normal analítica da borda do SDF
            float e = 1.5;
            float nx = sdRoundedBox(p + float2(e, 0.0), halfSize, uCornerRadius) -
                       sdRoundedBox(p - float2(e, 0.0), halfSize, uCornerRadius);
            float ny = sdRoundedBox(p + float2(0.0, e), halfSize, uCornerRadius) -
                       sdRoundedBox(p - float2(0.0, e), halfSize, uCornerRadius);
            float2 normal = normalize(float2(nx, ny) + 0.0001);

            // Anel de curvatura óptica nos 32px próximos à borda
            float bevel = smoothstep(-32.0, 0.0, d);
            float lensCurve = bevel * bevel;
            float2 offset = normal * lensCurve * uRefractionPx;

            float2 minB = float2(1.0);
            float2 maxB = max(uResolution - 1.0, float2(2.0));

            half r = content.eval(clamp(fragCoord - offset * (1.0 + uDispersion), minB, maxB)).r;
            half g = content.eval(clamp(fragCoord - offset, minB, maxB)).g;
            half b = content.eval(clamp(fragCoord - offset * (1.0 - uDispersion), minB, maxB)).b;
            float3 refracted = float3(r, g, b);

            // Mistura com o tint vítreo
            float3 col = mix(refracted, uTint.rgb, uTint.a);

            // Luz especular direcional (topo-esquerda e base-direita)
            float2 uv = fragCoord / max(uResolution, float2(1.0));
            float topLight = pow(max(0.0, 1.0 - (uv.x * 0.6 + uv.y * 0.7)), 2.2);
            float botLight = pow(max(0.0, (uv.x * 0.5 + uv.y * 0.5) - 0.35), 2.6);

            // Brilho interno de refração + borda especular nítida
            col += float3(0.78, 0.84, 1.0) * (bevel * 0.16 * (topLight + 0.4));
            col += float3(1.0) * topLight * 0.09;

            float rim = smoothstep(-2.2, -0.2, d);
            float rimLight = 0.28 + 0.55 * topLight + 0.30 * botLight;
            col = mix(col, float3(0.92, 0.95, 1.0), clamp(rim * rimLight, 0.0, 0.85));

            return half4(clamp(col, 0.0, 1.0), 1.0);
        }
    """

    /**
     * 3. SLIDER '52' HORIZONTAL MENISCUS SHADER (Reprodução Fiel da Imagem 3)
     * Cápsula externa em vidro fumê metálico + cápsula interna escura com o
     * menisco luminoso branco e franja cromática azul-gelo na ponta direita.
     */
    @Language("AGSL")
    const val SLIDER_52_HORIZONTAL_SHADER = """
        uniform float2 uResolution;
        uniform float uProgress;      // 0.0 a 1.0
        uniform float uDragHighlight; // 0.0 a 1.0

        float sdCapsuleBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0));
            float radius = uResolution.y * 0.5;
            float2 outerHalf = uResolution * 0.5;
            float dOuter = sdCapsuleBox(fragCoord - outerHalf, outerHalf, radius);
            if (dOuter > 0.0) return half4(0.0);

            // Trilha externa: vidro fumê com reflexo diagonal acetinado (igual à Imagem 3)
            float diagonalSheen = exp(-pow((uv.x - 0.56) * 2.8 + (uv.y - 0.45) * 1.3, 2.0));
            float3 trackCol = mix(float3(0.16, 0.17, 0.22), float3(0.38, 0.40, 0.46), diagonalSheen * 0.72 + uv.x * 0.22);
            float outerRim = smoothstep(-1.8, -0.2, dOuter);
            trackCol = mix(trackCol, float3(0.82, 0.85, 0.90), outerRim * 0.55);

            float3 finalCol = trackCol;
            float finalAlpha = 0.86;

            // Cápsula interna (Progresso líquido)
            float pad = 3.5;
            float innerR = max(radius - pad, 4.0);
            float minW = uResolution.y;
            float fillW = mix(minW, max(uResolution.x - pad * 2.0, minW), clamp(uProgress, 0.0, 1.0));
            float2 innerHalf = float2(fillW * 0.5, innerR);
            float2 innerCenter = float2(pad + fillW * 0.5, radius);
            float dInner = sdCapsuleBox(fragCoord - innerCenter, innerHalf, innerR);

            if (dInner <= 0.0) {
                // Vidro fumê escuro interno
                float3 innerCol = mix(float3(0.10, 0.11, 0.15), float3(0.20, 0.21, 0.27), (1.0 - uv.y) * 0.55);

                // Menisco luminoso na curva direita (a meia-lua branca com halo azul da Imagem 3)
                float2 rightCapCenter = float2(pad + fillW - innerR, radius);
                float2 pCap = fragCoord - rightCapCenter;

                float rNorm = length(pCap) / innerR;
                float rCut = length(pCap - float2(-innerR * 0.34, innerR * 0.20)) / (innerR * 0.80);

                float meniscusShape = smoothstep(1.02, 0.86, rNorm) *
                                      smoothstep(0.52, 0.96, rCut) *
                                      smoothstep(-innerR * 0.15, innerR * 0.25, pCap.x) *
                                      smoothstep(innerR * 0.55, -innerR * 0.40, pCap.y);

                float chromaticHalo = smoothstep(1.02, 0.68, rNorm) *
                                      smoothstep(0.42, 0.88, rCut) *
                                      smoothstep(-innerR * 0.28, innerR * 0.30, pCap.x);

                // Halo azul-gelo de dispersão cromática + núcleo branco translúcido do menisco
                innerCol += float3(0.35, 0.68, 1.0) * chromaticHalo * (0.65 + 0.25 * uDragHighlight);
                innerCol = mix(innerCol, float3(0.93, 0.96, 1.0), clamp(meniscusShape * (0.92 + 0.08 * uDragHighlight), 0.0, 1.0));

                // Anel fino especular da cápsula interna
                float innerRim = smoothstep(-1.8, -0.2, dInner);
                float rightTipFactor = smoothstep(-innerR * 0.5, innerR, pCap.x);
                float3 rimColor = mix(float3(0.62, 0.66, 0.74), float3(0.70, 0.88, 1.0), rightTipFactor);
                innerCol = mix(innerCol, rimColor, innerRim * 0.70);

                finalCol = innerCol;
                finalAlpha = 0.95;
            }

            return half4(clamp(finalCol, 0.0, 1.0), finalAlpha);
        }
    """

    /**
     * 4. SLIDER '52' VERTICAL MENISCUS SHADER (Para as 10 bandas do Equalizador)
     * Versão vertical da cápsula líquida com menisco luminoso no topo.
     */
    @Language("AGSL")
    const val SLIDER_52_VERTICAL_SHADER = """
        uniform float2 uResolution;
        uniform float uProgress; // 0.0 (base) a 1.0 (topo)

        float sdCapsuleBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0));
            float radius = uResolution.x * 0.5;
            float2 outerHalf = uResolution * 0.5;
            float dOuter = sdCapsuleBox(fragCoord - outerHalf, outerHalf, radius);
            if (dOuter > 0.0) return half4(0.0);

            float3 trackCol = mix(float3(0.12, 0.13, 0.18), float3(0.24, 0.26, 0.33), 1.0 - uv.y * 0.6);
            float outerRim = smoothstep(-1.5, -0.2, dOuter);
            trackCol = mix(trackCol, float3(0.70, 0.75, 0.85), outerRim * 0.40);

            float pad = 2.0;
            float innerR = max(radius - pad, 3.0);
            float minH = uResolution.x;
            float fillH = mix(minH, max(uResolution.y - pad * 2.0, minH), clamp(uProgress, 0.0, 1.0));
            float topY = uResolution.y - pad - fillH;
            float2 innerHalf = float2(innerR, fillH * 0.5);
            float2 innerCenter = float2(radius, topY + fillH * 0.5);
            float dInner = sdCapsuleBox(fragCoord - innerCenter, innerHalf, innerR);

            float3 finalCol = trackCol;
            float finalAlpha = 0.78;

            if (dInner <= 0.0) {
                float3 innerCol = mix(float3(0.18, 0.20, 0.30), float3(0.32, 0.36, 0.52), 1.0 - uv.y);
                float2 topCapCenter = float2(radius, topY + innerR);
                float2 pCap = fragCoord - topCapCenter;

                float rNorm = length(pCap) / innerR;
                float rCut = length(pCap - float2(innerR * 0.15, innerR * 0.35)) / (innerR * 0.78);
                float meniscus = smoothstep(1.0, 0.82, rNorm) * smoothstep(0.50, 0.95, rCut) * smoothstep(innerR * 0.3, -innerR * 0.3, pCap.y);
                float blueHalo = smoothstep(1.0, 0.60, rNorm) * smoothstep(0.40, 0.85, rCut);

                innerCol += float3(0.38, 0.70, 1.0) * blueHalo * 0.65;
                innerCol = mix(innerCol, float3(0.95, 0.98, 1.0), clamp(meniscus * 0.92, 0.0, 1.0));

                float innerRim = smoothstep(-1.5, -0.2, dInner);
                innerCol = mix(innerCol, float3(0.78, 0.88, 1.0), innerRim * 0.65);

                finalCol = innerCol;
                finalAlpha = 0.95;
            }

            return half4(clamp(finalCol, 0.0, 1.0), finalAlpha);
        }
    """
}
