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

## Fase 1: peças base (arquivo novo `ui/components/AuroFeedback.kt`)

**1.1 `AuroSnackbar` (aviso de vidro).** Substitui todos os `Toast.makeText`.
- API: `AuroSnackbarHost(state)` no `MainActivity`, acima da navbar e do mini player; `state.show(text, action?, onAction?)`.
- Visual: `AuroGlass(Card)`, altura 52dp, margem horizontal 16dp, 12dp acima do mini player. Texto Body 14sp branco; ação em Label/Accent A.
- Movimento: entra subindo 24dp com fade, `AuroMotion.liquid()`; sai com fade de 160 ms. Uma mensagem por vez (a nova substitui a atual). Dura 3 s (4 s se tiver ação).
- **Aceite:** `grep -rn "Toast\." app/src` vazio.

**1.2 Estados vazio, carregando e erro.**
- `AuroEmptyState(icon, title, message, actionLabel?, onAction?)`: coluna centralizada; ícone de 48dp em branco 60%; título Title (21/28); mensagem Body em TextSecondary; ação opcional como `AuroGlass(Card)` de 48dp de altura.
- `AuroLoadingBar`: linha de 2dp no topo da lista em Accent A, com um trecho de luz de 30% da largura deslizando (`rememberInfiniteTransition`, 1400 ms), desenhada em `drawBehind`. Sem `CircularProgressIndicator`.
- Textos:

| Situação | Título | Mensagem | Ação |
|---|---|---|---|
| Biblioteca vazia | Nenhuma música encontrada | Não achei arquivos de áudio neste aparelho. | Atualizar biblioteca |
| Fila vazia | A fila está vazia | Escolha uma música na biblioteca. | Abrir biblioteca |
| Playlist vazia | Playlist vazia | Adicione músicas pelo menu da faixa. | n/a |
| Busca sem resultado | Nada encontrado para “{q}” | Confira a ortografia ou tente outra palavra. | n/a |

**1.3 `AuroCoverPlaceholder(modifier, glass)`.** Hoje a faixa sem capa usa um quadrado cinza `#2D2D2D` com nota musical.
- Preto absoluto com a nota (`MusicNote`, 40% do lado) em Accent A a 60%.
- `glass = true` (Now Playing e mini player): contorno de vidro com `AuroGlass`. `glass = false` (linhas de lista): só contorno de 1dp em branco 10%.
- Trocar todo `.background(AuroColor.Track)` que seja placeholder de capa.

**1.4 `AuroHaptics` (object, com guardas de API).** Usar `View.performHapticFeedback`, que já respeita a configuração do sistema.

| Ação | Constante |
|---|---|
| toque em `AuroGlass` | `VIRTUAL_KEY` |
| play/pause, ★ | `CONFIRM` (API 30+, senão `VIRTUAL_KEY`) |
| erro | `REJECT` (API 30+, senão `LONG_PRESS`) |
| toque longo (menu, pegar item da fila) | `LONG_PRESS` |
| passo de slider (EQ, arrastar o progresso a cada 5%) | `SEGMENT_FREQUENT_TICK` (API 34+, senão `CLOCK_TICK`) |

**Aceite:** todo `onClick` de `AuroGlass` vibra uma vez, sem duplicar.

## Fase 2: movimento

Hoje as telas trocam seco (`when (currentScreen)` no `MainActivity`) e os overlays usam a animação padrão do `Dialog`.

**2.1 Entre telas.** Trocar o `when` por `AnimatedContent`.
- Profundidade: LibraryRoot 0; CategoryList, Search, Equalizer, Queue, Settings 1; Drilldown 2.
- Avançar: a nova tela entra com `slideInHorizontally { it / 10 }` mais fade, `spring(0,85; 300)`; a antiga sai com `slideOutHorizontally { -it / 14 }` mais fade de 160 ms. Voltar é o inverso.
- Troca na mesma profundidade (abas da navbar): só fade, 220 ms entra e 140 ms sai.
- Guardar o estado de rolagem de cada tela (`rememberSaveable` com `LazyListState`, ou no ViewModel) para voltar na mesma posição.
- **Aceite:** sem flash nem pulo, e a posição de rolagem volta ao retornar.

**2.2 Mini player ↔ Now Playing.** `SharedTransitionLayout` com `sharedBounds` no contêiner (`spring(0,85; 250)`) e `sharedElement` na capa. Se der problema, plano B: o Now Playing sobe de baixo (`slideInVertically { it }` com mola) e o mini player some em fade.

**2.3 Overlays.** Criar `AuroOverlay(visible, onDismiss, content)` e migrar todos os `Dialog`, `ModalBottomSheet` e `Popup` de `AuroOverlays.kt`.
- Scrim: preto a 72%, fade de 200 ms.
- Painel: entra de 0,94 para 1 de escala mais fade, `AuroMotion.liquid()`; sai com fade de 140 ms.
- Sheets: sobem com `spring(0,85; 300)` e fecham arrastando para baixo.

**2.4 Voltar preditivo.** `PredictiveBackHandler` no Now Playing e nos overlays. Com o progresso `p` de 0 a 1: escala `1 − 0,08p`, raio do canto crescendo até 28dp, alpha `1 − 0,3p`. Ao confirmar, termina com mola; ao cancelar, volta com mola. Verificar que `android:enableOnBackInvokedCallback="true"` está no manifest.

## Fase 3: estados e fluxos

**3.1 Permissão e primeira abertura.** Modelar `LibraryAccess { Granted, NeedsRequest, Denied, PermanentlyDenied }`.
- Tela cheia com `AuroEmptyState`: "Precisamos de acesso às suas músicas" / "O Aurō lê os arquivos de áudio do aparelho. Nada sai daqui." Ação "Permitir" (dispara o pedido).
- Negada de forma permanente: ação "Abrir configurações" (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).
- Rechecar a permissão em `ON_RESUME`.
- Notificações (Android 13+): não bloqueiam. Se negadas, um snackbar uma vez só, com ação "Ativar" que abre as configurações de notificação do app.

**3.2 Carregando a biblioteca.** Mostrar `AuroLoadingBar` com "Carregando biblioteca… {n} faixas" enquanto `loadLibrary()` roda (só depois de 150 ms, para não piscar). Se vier vazia, estado de biblioteca vazia.

**3.3 Busca.**
- Campo vazio: "Buscas recentes" (últimas 8 no `PreferencesManager`, com ✕ por item e "Limpar").
- Digitando: debounce de 250 ms.
- Filtros: chips Todos | Faixas | Álbuns | Artistas (sem vidro).
- Resultados agrupados, com título da seção em Label/TextSecondary e no máximo 5 por grupo mais "Ver todos".
- Sem resultado: o estado de busca da tabela da Fase 1.
- `imePadding`, e esconder o teclado ao rolar.

**3.4 Erro de reprodução.** Expor um evento `PlaybackError(trackId)` a partir do `PlaybackService`. A UI mostra snackbar "Não foi possível tocar “{nome}”. Pulando…" (háptico de erro) e pula após 1,2 s. Depois de 3 falhas seguidas, parar e mostrar o estado de erro com "Tentar de novo". Marcar a linha da faixa com ícone de aviso até a próxima atualização da biblioteca.

## Fase 4: funções que faltam

**4.1 Reordenar a fila (`AuroQueueScreen`).** Hoje só remove e limpa.
- Alça (ícone de três barras, 24dp) no fim da linha; toque longo pega o item (háptico `LONG_PRESS`).
- Item arrastado: escala 1,03 e fundo branco a 6%. Os outros abrem espaço com `Modifier.animateItem()` e o item pousa com mola. Rolagem automática perto das bordas.
- Implementação própria com `detectDragGesturesAfterLongPress` e `LazyListState.layoutInfo`.
- Novo `moveQueueItem(from, to)` que mantém a faixa atual com o índice certo e **sem interromper a reprodução**.
- Snackbar "Faixa movida" com "Desfazer". Para leitor de tela: ações "Mover para cima" e "Mover para baixo".
- **Aceite:** arrastar a primeira faixa para o fim mantém a música tocando e o índice coerente.

**4.2 Configurações.** Novo `NavScreen.Settings`, primeiro item do menu (`AuroPowerampMenuSheet`).
- Layout: cabeçalho de seção em Label (Accent A, 12sp, maiúsculas, +0,8sp). Linhas de 56dp no mínimo: título Body, subtítulo Label/TextSecondary, controle à direita. Divisor Hairline.
- Switch: pílula 48×28dp preta com contorno de 1dp em branco 15% e bolinha branca de 20dp. Ligado: bolinha à direita e contorno em Accent A a 60%. Slider: igual à barra de progresso, com `semantics`. Escolha: abre `AuroOverlay` com lista.
- Itens (persistir no `PreferencesManager`; padrão entre parênteses). Mostrar só o que o motor já suporta, e anotar o resto no `AI_CONTEXT.md`:

| Seção | Itens |
|---|---|
| Biblioteca | pastas incluídas ou ignoradas (todas), ignorar faixas menores que N s (0), atualizar biblioteca |
| Reprodução | pausar ao desconectar fones (ligado), gapless (ligado), crossfade 0 a 12 s (0), ReplayGain desligado/faixa/álbum (desligado), retomar de onde parou (ligado) |
| Aparência | luz reage ao movimento (ligado; controla `tilt` do `AuroTheme`), suavidade do reflexo 0 a 1 (0,6; com um botão de vidro de exemplo ao vivo), reduzir animações (seguir sistema), mostrar formato do arquivo (ligado) |
| Sobre | versão, licenças, link do repositório |

**4.3 Seleção múltipla.** Toque longo numa linha entra no modo seleção (háptico).
- A barra do topo vira "N selecionadas" com: Tocar em seguida, Adicionar à fila, Adicionar à playlist, ✕ para sair, e "Selecionar tudo" no menu de overflow.
- Marca circular de 24dp à esquerda: contorno branco a 30%; selecionada = Accent A preenchido com tique preto.
- O botão voltar sai da seleção antes de navegar. Reutilizar a lógica existente de fila e playlist.

**4.4 Notificação (e widget, opcional).**
- Hoje usa `android.R.drawable.ic_media_*`. Trocar por vetores próprios: ícone pequeno monocromático `ic_stat_player.xml`, e ícones de anterior, play, pause e próximo em Material Rounded.
- `setColor` = Accent A da capa; ícone grande = capa (ou o placeholder preto); ações compactas 0, 1 e 2; visibilidade pública na tela de bloqueio.
- Widget 4×1 (Glance): fundo preto, capa de 48dp, título e artista, anterior/play/próximo. Só depois de tudo o resto.

## Fase 5: acessibilidade e adaptação

**5.1 Barra de progresso e sliders do EQ.** Hoje são gestos desenhados, sem `semantics`. Exemplo para o progresso:
```kotlin
Modifier.semantics {
    contentDescription = "Progresso da faixa"
    stateDescription = "$posTexto de $durTexto"
    progressBarRangeInfo = ProgressBarRangeInfo(pos.toFloat(), 0f..dur.toFloat())
    setProgress { alvo -> onSeek(alvo.toLong()); true }
    customActions = listOf(
        CustomAccessibilityAction("Voltar 10 segundos") { onSeek(pos - 10_000); true },
        CustomAccessibilityAction("Avançar 10 segundos") { onSeek(pos + 10_000); true },
    )
}
```
Banda do EQ: `contentDescription = "Banda 1 kHz"`, `stateDescription = "+3,5 dB"`, `progressBarRangeInfo` com a faixa real do equalizador, e `setProgress` em passos de 0,5 dB.

**5.2 Descrições e alvos de toque.** Todo ícone clicável com `contentDescription` vindo de `strings.xml`; decorativos `null`. Alvo mínimo de 48dp (os botões ★ e ⋮ de 40dp ganham área de toque de 48dp). Ordem de foco no Now Playing: capa, título, artista, progresso, controles, Fila e Letras.

**5.3 Textos.** `res/values/strings.xml` (pt-BR) e `res/values-en/strings.xml`. Usar `stringResource` e `pluralStringResource` para contagens ("7 faixas"). Uma tela por commit. **Aceite:** `grep -rnE 'Text\(\s*"' app/src/main/java` sem resultados relevantes.

**5.4 Insets medidos.** Remover os `58.dp` e `118.dp` fixos do `MainActivity`. Medir a altura da navbar e do mini player com `onSizeChanged` e expor `LocalAuroBottomInset`, que as listas, o snackbar e os overlays usam. Testar com navegação por gestos, por 3 botões, com e sem mini player, e com o teclado aberto na busca.

**5.5 Fonte grande.** Testar em 100%, 150% e 200%. Nada cortado, `maxLines` com reticências em título e artista, Now Playing rola, alvos continuam com 48dp ou mais.

**5.6 Orientação.** Padrão da tabela de decisões. Atenção: no Android 16 o travamento de orientação é ignorado em telas grandes (≥ 600dp), então só o painel duplo resolve de verdade. Exige a dependência `material3-window-size-class` (pedir antes).

**5.7 Android 8 a 12 (minSdk 26).** O vidro exige API 33. Confirmar o plano B: card preto com contorno de 1dp em branco 10%, sem shader. Testar em emulador API 26 e 31.

## Fase 6: qualidade

- **Desempenho:** no máximo 8 `AuroGlass` por tela. Em aparelho de 120 Hz, conferir o Now Playing com a luz do sensor e a rolagem da biblioteca. Se cair quadro, reduzir `soft` e as ondas, ou a taxa de atualização do sensor.
- **Sensor:** inclinar o lado direito para baixo deve levar o reflexo para o lado direito. Se estiver invertido, trocar o sinal de `gx` em `AuroTilt.light`.
- **Segundo plano:** parar o sensor em `ON_STOP` e religar em `ON_START`.
- **Roteiro manual:** primeira abertura sem permissão; negar e permitir; biblioteca vazia; biblioteca grande (1000+ faixas); busca com recentes e sem resultado; reordenar a fila; seleção múltipla; configurações persistem depois de matar o app; fonte 200%; TalkBack no player; desconectar fones; controles da notificação; matar e reabrir o app.

## Definição de pronto

- Toda lista tem estado vazio, carregando e erro.
- Nenhum `Toast`, nenhum texto literal em Compose, nenhum `.background(AuroColor.Track)` fora da barra de progresso.
- Toda navegação tem transição, e o voltar é preditivo.
- Dá para operar o player inteiro com TalkBack.
- Nenhum padding fixo de navbar ou mini player.
- Nenhum arquivo de UI sem uso.
- `AI_CONTEXT.md` e `DESIGN_SYSTEM.md` refletem o que existe.
