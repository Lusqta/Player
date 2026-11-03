# Plano de UI/UX para o agente: Aurō Player

Cole este arquivo no início da sessão. Trabalhe **uma fase por vez**: cada fase termina compilando (`./gradlew assembleDebug`), com um commit e um resumo do que mudou e do que **não** deu pra testar. Base: leitura do repositório (compileSdk 35, minSdk 26, Compose BOM 2024.09.00). Referência de design: `DESIGN_SYSTEM.md` v3.

**Prompt inicial sugerido:** "Leia `AI_CONTEXT.md`, `DESIGN_SYSTEM.md` e `PLANO_UIUX_AGENTE.md`. Execute a Fase 0 e pare. Mostre o resumo e espere eu mandar a próxima."

## Regras para tudo

1. Fundo #000000. Nada de cinza de superfície (`#121212`, e `AuroColor.Track` só na barra de progresso). Cores só via `AuroColor` e o accent da capa.
2. Vidro (`AuroGlass`) só em objetos interativos, no máximo 8 por tela (o Now Playing já usa 7). Chips, switches e linhas não são vidro: contorno de 1dp em branco 10%.
3. Animação só em `graphicsLayer` e na fase de desenho, com as molas de `AuroMotion`. Nunca recompor por frame. Com `rememberReduceMotion()` verdadeiro: sem movimento e troca instantânea.
4. Não mexer no motor de áudio (Oboe, decoders, DSP do equalizador) nem na lógica do `PlaybackService`, só no que a UI precisa dele.
5. Texto novo vai em `strings.xml`, nunca literal no Compose.
6. Nenhuma dependência nova sem avisar. Já existem: Compose BOM 2024.09.00 (tem `SharedTransitionLayout` e `Modifier.animateItem()`), activity-compose 1.9.2 (tem `PredictiveBackHandler`), palette-ktx, Material3, icons-extended. Se `SharedTransitionLayout` não resolver o import, adicione `androidx.compose.animation:animation` (sem versão, o BOM resolve).
7. Antes de apagar algo, provar com `grep` que não é usado. Apagar em commit separado.
8. Ao fim de cada fase, atualizar `AI_CONTEXT.md` (o que existe, onde fica, regras novas).

## Decisões suas (o agente usa o padrão se você não responder)

| Decisão | Padrão |
|---|---|
| Paisagem e tablet | celulares travados em retrato; painel duplo só se a largura for ≥ 600dp (última fase, opcional) |
| Idiomas | pt-BR (padrão) e inglês |
| Reordenar a fila | implementação própria, sem biblioteca nova |
| Widget de tela inicial | opcional, depois de tudo |
| Excluir arquivos do aparelho | fora do escopo |

---

## Fase 0: limpeza (commit próprio)

Deixar só o que o app usa. O app roda o conjunto `Auro*`. Confirmar com `grep` que nada abaixo é referenciado, depois apagar:
- `ui/screens/DryScreens.kt`, `DryOverlays.kt`, `ui/components/DryComponents.kt`, `ui/theme/DryTheme.kt`
- `ui/screens/PlayerScreens.kt`, `PlayerOverlays.kt`
- `ui/components/LiquidComponents.kt`, `LiquidAgslShaders.kt`
- Layouts XML antigos em `res/layout/` (activity_main, dialog_equalizer, dialog_lyrics, item_track e similares) e os adapters e o `WaveformView` que só eles usavam
- `AuroWaveformSeekbar` está definida e sem uso: apagar.

Depois, dividir `AuroScreens.kt` em um arquivo por tela, sem mudar comportamento.

**Aceite:** `assembleDebug` passa, o app abre e toca igual, cerca de 7.600 linhas a menos.
