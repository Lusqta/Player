# Aurō Design System v3 (player)

Especificação técnica e visual do Aurō Player. Código de referência: [`AuroDesignSystem.kt`](file:///c:/Users/Lucas/Downloads/player/app/src/main/java/com/lucas/player/ui/theme/AuroDesignSystem.kt).

---

## 1. Princípios Fundamentais

1. **Preto Absoluto (`#000000`)**:
   - Todo o fundo de tela, modais, diálogos e gavetas utilizam `Color(0xFF000000)`.
   - Proibido o uso de tons de cinza de superfície comuns (ex: `#121212`, `#1E1E1E`) e sombras simuladas.
2. **Luz Única Global e Paralela (`AuroLight`)**:
   - Não existe luz própria por componente. Todos os botões, cards e controles leem a mesma direção da luz do ouvinte vinda de baixo (`dx, dy, gain`).
   - A luz não é desenhada como fundo: manifesta-se estritamente como reflexo nas bordas e arestas dos objetos de vidro.
3. **Vidro Líquido Preto (`AuroGlass`)**:
   - A maior parte do objeto é `#000000`. A luz vive no reflexo das bordas que encaram o ouvinte, com fio de contorno sutil no lado oposto.
   - Apenas objetos interativos tornam-se vidro (botões de transporte, cards de ação, mini player, controles de menu). Listas, títulos e textos sentam diretamente no preto com divisores `Hairline` (`0x14FFFFFF`).
4. **Cores da Capa (`CoverGlow` & `GlowTone`)**:
   - A cor do reflexo e do progresso deriva da arte do álbum ativo, interpolada suavemente em Oklab (900ms).
5. **Sensor e Vida Física**:
   - A luz acompanha o celular com mola amortecida ($\omega = 7\text{ rad/s}, \zeta = 0{,}8$).
   - O sensor desacopla em segundo plano (`ON_STOP`) e reativa ao retornar ao primeiro plano (`ON_START`).
6. **Desempenho e Acessibilidade**:
   - Limite estrito de no máximo 8 instâncias de `AuroGlass` ativas por tela.
   - Navegação 100% acessível via TalkBack com alvos mínimos de 48dp e semânticas completas.

---

## 2. Tokens de Cor e Espaçamento

### Cores (`AuroColor`)
| Token | Valor | Uso |
|---|---|---|
| `Black` | `#000000` | Fundo absoluto de todas as telas e diálogos |
| `TextPrimary` | `#FFFFFF` (21:1) | Títulos, ícones principais, botões ativos |
| `TextSecondary` | `#9E9E9E` (7,8:1) | Artista, tempos de faixa, descrições |
| `TextTertiary` | `#616161` (3,4:1) | Dicas, estados desabilitados e placeholders |
| `Hairline` | `0x14FFFFFF` (branco 8%) | Fio divisor de 1dp entre linhas |
| `Track` | `#2D2D2D` | Trilho das barras de progresso |
| `Accent A` | Dinâmico da Capa | Reflexo principal da luz, preenchimento do progresso |
| `Accent B` | Dinâmico da Capa | Halo secundário interno e detalhes análogos |

### Espaço e Raios (`AuroSpace` & `AuroRadius`)
- Grade de 4dp: `xxs=2.dp`, `xs=4.dp`, `sm=8.dp`, `md=12.dp`, `lg=16.dp`, `xl=24.dp`, `xxl=32.dp`.
- Raios: `Capa=22.dp`, `Card=24.dp`, `Pill=999.dp`, `Row=12.dp`.

---

## 3. Capa → Luz (`GlowTone` & `CoverGlow`)

1. Extração via `androidx.palette:palette-ktx`: 16 cores, amostra de 96×96 com proteção contra bitmaps `HARDWARE` (cópia prévia para `ARGB_8888`).
2. Semente selecionada pelo candidato com maior valor de $\text{população} \times (0{,}25 + \text{saturação})$.
3. `GlowTone.fit(semente)`:
   - Saturação HSL $< 0{,}10$ (capa acromática): Luz prata quente (`#D0CDC8` / `#A69F96`).
   - Saturação HSL $\ge 0{,}10$: Saturação e luminosidade calibradas para manter legibilidade e reflexos vívidos sobre o preto.
4. Animação de transição de cor: 900ms via `tween` com easing suave em espaço Oklab.

---

## 4. Luz do Ouvinte e Física do Sensor

### Vetor de Luz (`AuroLight`)
- Direção normalizada `(dx, dy)` no plano da tela apontando para a fonte de luz, com intensidade `gain` ($0{,}6 \dots 1{,}0$).
- Celular em pé em repouso: `(0, 1)`, luz vem da parte inferior (direção do ouvinte).

### Sensor (`AuroTilt` & `TiltSmoother`)
- Sensor `TYPE_GRAVITY` (com fallback para `TYPE_ACCELEROMETER`), taxa `SENSOR_DELAY_GAME`.
- Cálculo de orientação:
  $$x = \frac{g_x}{9{,}81} \times \text{sideGain}$$
  $$y = \frac{g_y}{9{,}81} + \text{bias}$$
  *Inclinar o lado direito para baixo ($g_x > 0$) desloca o reflexo para a direita ($x > 0$); inclinar para a esquerda desloca para a esquerda.*
- Suavização líquida: mola de segunda ordem ($\omega = 7\text{ rad/s}, \zeta = 0{,}8$).
- Economia de bateria:
  - O sensor é pausado no evento `ON_STOP` do ciclo de vida e religado no `ON_START` via `LifecycleEventObserver`.
  - A escrita no `State` só ocorre se a variação for superior a 0,002.
  - A leitura é feita exclusivamente na fase de desenho (`drawBehind` / shader uniforms), sem recompilação de Compose a cada frame.

---

## 5. Shaders e Renderização (`AuroGlass`)

- No Android 13+ (API 33+): Renderização em tempo real via AGSL (`RuntimeShader`).
  - Lóbulos especulares duplos controlados pelo parâmetro `soft` ($0{,}0 \dots 1{,}0$).
  - Franja de dispersão cromática sutil nas bordas ($\pm 2^\circ$).
  - Contorno de presença com 6% a 26% de branco para contraste perfeito em OLED.
  - Dither anti-banding nas regiões iluminadas.
- Fallback em Android 8 a 12 (API 26 a 32):
  - Card preto com contorno de 1dp em branco 10% (`AuroColor.Hairline`), preservando o fundo preto sem dependência de AGSL.

---

## 6. Movimento e Animações

- **Molas Líquidas**: `spring(dampingRatio = 0.85f, stiffness = 250f)` para expansão do player e sheets.
- **Transições de Tela**: `AnimatedContent` com transições baseadas em profundidade de navegação e efeito paralaxe sutil.
- **Respeito ao Usuário**: `rememberReduceMotion()` desativa instantaneamente transições elaboradas quando a escala de animação do sistema está desligada ou quando configurado pelo usuário no app.
- **Predictive Back**: Suporte completo a gestos de volta preditiva no Android 14+ com feedback elástico em todos os diálogos e na tela Now Playing.

---

## 7. Acessibilidade e Insets Medidos

- **Semânticas TalkBack**:
  - `AuroProgressBar` com range de progresso, `stateDescription` em tempo relativo e customActions ("Avançar 10s", "Voltar 10s").
  - `AuroSlider` com anúncio dinâmico em decibéis (dB) e ajuste contínuo.
  - `AuroQueueScreen` com ações de acessibilidade "Mover para cima" e "Mover para baixo".
- **Áreas de Toque**: Todos os controles interativos respeitam o padrão mínimo de 48dp (`minimumInteractiveComponentSize`).
- **Insets Dinâmicos**: `LocalAuroBottomInset` medido em tempo real pelo container da barra de navegação e mini player, garantindo espaçamento correto em gestos e navegação de 3 botões sem paddings estáticos.
- **Internacionalização**: 100% das strings externalizadas em `values/strings.xml` (Português) e `values-en/strings.xml` (Inglês) com paridade total.
