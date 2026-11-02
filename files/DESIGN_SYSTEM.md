# Aurō Design System v3 (player)

Spec para o agente que implementa o app. Código de referência: `AuroDesignSystem.kt`. Resultado esperado: `especimen_design_system.png`. Animação da luz: `inclinacao_luz.gif`.

## 1. A ideia

O mundo é vazio: fundo **#000000**, sem nada. Os objetos (botões, cards) são **de vidro preto**. Existe **uma única luz, que é o ouvinte**: ela vem de baixo, é **paralela** (a mesma direção em todos os objetos) e **acompanha a inclinação do celular**, como se o ouvinte estivesse segurando o aparelho. A luz não é desenhada: aparece só como reflexo nas bordas dos objetos.

Princípios:

1. Preto absoluto. Sem cinza de superfície, sem sombra.
2. Uma luz só, global e paralela. Nenhum objeto tem luz própria, e todos mostram o reflexo no mesmo lado.
3. Vidro preto: a maior parte de cada objeto é #000000. A luz vive só em reflexos nas bordas que encaram a luz, mais um fio fraco do lado oposto.
4. A cor da luz vem da capa da faixa que toca.
5. Vivo: a luz acompanha o celular com atraso de mola, respira devagar e tem ondas lentas. O vidro responde ao toque.
6. Pouco: só objetos interativos viram vidro. Texto e linhas de lista ficam direto no preto.

## 2. Cor

| Token | Valor | Uso |
|---|---|---|
| Black | #000000 | fundo de toda tela |
| TextPrimary | #FFFFFF (21:1) | títulos, ícones |
| TextSecondary | #9E9E9E (7,8:1) | artista, tempos |
| TextTertiary | #616161 (3,4:1) | só dica e desabilitado |
| Hairline | branco 8% | fio de 1dp entre linhas |
| Track | #2D2D2D | trilho do progresso |
| Accent A / B | da capa | luz, preenchimento do progresso |

Proibido: #121212, cinzas de superfície, sombras, mais de uma cor de luz ao mesmo tempo (exceto durante a transição de faixa).

## 3. Cor da capa → luz

1. `Palette` (androidx.palette) na capa: 16 cores, 96×96. Candidatos: vibrant, lightVibrant, darkVibrant, dominant.
2. Semente = candidato com maior `população × (0,25 + saturação)`.
3. `GlowTone.fit(semente)`:
   - saturação HSL < 0,10 (capa sem cor): luz prata quente, #D0CDC8 / #A69F96.
   - senão: S = clamp(S × 1,15; 0,62; 0,95), L = clamp(L; 0,50; 0,62) → **Accent A**. **Accent B** = matiz +28°, S − 0,05, L + 0,06.
4. Papéis: **A** colore o reflexo e o preenchimento do progresso. **B** entra no fio interno fraco.
5. Cache por id/URI da capa. Na troca de faixa, A e B animam por 900 ms (mistura em Oklab).

| Semente da capa | Accent A | Accent B |
|---|---|---|
| #D9531E laranja | #EF4F10 | #EBBD33 |
| #1F3FA8 azul escuro | #1B4AE4 | #623CE2 |
| #C2358F magenta | #D32C97 | #D34B62 |
| #1E9E6B verde | #1CE394 | #3DD6E1 |
| #B3202A vermelho | #E61927 | #E37E3B |
| #8A8A8A sem cor | #D0CDC8 | #A69F96 |

## 4. A luz do ouvinte

**Direção.** Vetor `(dx, dy)` no plano da tela (x pra direita, y pra baixo) apontando PARA a luz. Em pé: `(0, 1)`, a luz vem de baixo. Um único vetor para a tela toda, então todos os objetos têm o reflexo no mesmo lado (círculos no arco inferior, cards numa linha contínua na borda de baixo).

**Sensor** (`AuroTilt`, `TiltSmoother`): sensor de gravidade (`TYPE_GRAVITY`, com `TYPE_ACCELEROMETER` de reserva), `SENSOR_DELAY_GAME`.
- `x = −gx / 9,81 × 1,6` e `y = gy / 9,81 + 0,25`, normalizados. O "pra baixo do mundo" projetado na tela é a direção da luz, e o viés 0,25 deixa a luz embaixo quando o celular está deitado.
- Inclinar o lado direito pra baixo leva o reflexo pro lado direito; inclinar o esquerdo leva pro esquerdo. **Conferir o sinal no aparelho** e, se invertido, trocar o sinal de `gx`.
- Ganho de intensidade: 0,6 (deitado) a 1,0 (em pé).
- Suavização: mola amortecida (ω = 7 rad/s, ζ = 0,8). A luz segue o celular com um pouco de atraso e sem tremer.
- Escrever no `State` só se mudar mais que 0,002, e ler só na fase de desenho (nunca recompor por causa do sensor).

**Vida.** Enquanto toca: a direção respira ±4° em 16 s, a intensidade flutua ±5% e há ondas lentas (±12%) que correm pelo reflexo. Objetos mais embaixo na tela recebem um pouco mais de luz (0,6 em cima → 1,0 embaixo).

**Sem sensor ou com "remover animações":** luz fixa embaixo, sem respiração, troca de cor instantânea.

## 5. Objeto de vidro preto

Cada pixel é calculado assim (shader `AURO_GLASS_AGSL`):

1. **Corpo preto:** `uBody` = 0.
2. **Reflexo** nas bordas cuja normal encara a luz. Dois lóbulos (estreito e largo), controlados por `soft`. A espessura é medida em **dp**, não em fração do objeto, então a linha tem o mesmo peso em botões e cards: linha de ~1dp, faixa de ~3,5dp e brilho suave de ~8dp para dentro (com `soft = 0,6`).
3. **Fio especular** branco quente, no ponto mais forte.
4. **Dispersão:** a direção da luz gira ±2° por canal de cor, o que dá uma franja de cor sutil nas pontas do reflexo.
5. **Contorno com presença:** linha contínua de ~1dp em volta de todo o objeto: 6% de branco no lado escuro, até 26% no lado da luz. O objeto sempre se lê como vidro, mesmo longe do reflexo, e o reflexo some gradualmente ao redor, sem corte seco.
6. **Reflexo interno:** do lado oposto à luz, um fio fraco (7%).
7. **Dither** de 1,6/255 só onde há luz: sem banding no OLED, preto continua preto.

`soft`: 0 = reflexo agudo (a versão anterior), 1 = bem difuso. Padrão **0,6**: nem tão fino quanto antes, nem o brilho largo da primeira versão.

| Tipo | Forma | Reflexo (`uSpec`) |
|---|---|---|
| Button | círculo | 1,5 |
| Card | retângulo, raio 24dp | 1,3 |

Regras:
- Só objetos interativos viram vidro: botões, cards, mini player. Linhas de lista e textos ficam no preto.
- O shader não tem loops. Um `RuntimeShader` por objeto, criado uma vez com `remember`.
- A capa não é vidro: ela é a fonte da cor.

## 6. Tipografia, espaço e forma

| Estilo | Tamanho / linha | Peso |
|---|---|---|
| Display | 28 / 34 | Medium |
| Title | 21 / 28 | Medium |
| Body | 15 / 22 | Regular |
| Label | 12 / 16 (+0,2sp) | Medium |

- Fonte: sans geométrica (sugestão: Outfit ou Figtree em `res/font`), com o glifo "ō". Tempos com `fontFeatureSettings = "tnum"`.
- Espaço (grade de 4dp): 4, 8, 12, 16, 24, 32. Raios: card 24dp, capa 22dp.

## 7. Movimento

| Evento | Spec |
|---|---|
| Inclinar o celular | mola ω = 7 rad/s, ζ = 0,8, no sensor |
| Troca de faixa (cor) | `tween` 900 ms, easing (0,2; 0; 0; 1), Oklab |
| Toque num objeto | escala 0,96 e brilho ×1,6, `spring(0,75; 500)` |
| Movimento de UI | `spring(0,82; 260)` |
| Mini player → player cheio | shared bounds, `spring(0,85; 250)` |
| Respiração e ondas da luz | só tocando |

Regras:
- Animar só em `graphicsLayer` e na fase de desenho. Nunca recompor a tela a cada frame (luz, relógio e cor chegam aos objetos como lambda/State).
- Nunca animar raio de blur.
- Pensar em 120 Hz.

## 8. Componentes

Layout de referência (do seu rascunho): capa; título e artista à esquerda com ★ e ⋮ à direita; progresso; linha de controles; Fila e Letras; lista.

- **Controles:** um círculo de vidro cada: anterior 56dp, play 76dp, próximo 56dp. Ícones brancos preenchidos. Aleatório e repetir ficam como ícones simples, sem vidro.
- **★ e ⋮:** círculos de vidro de 40dp ao lado do título.
- **Progresso:** trilho 3dp #2D2D2D, preenchimento Accent A, bolinha branca de 10dp. Tempos em Label/TextSecondary.
- **Cards:** Fila, Letras, mini player. Altura ~72dp, texto Body branco.
- **Linha de lista:** título 14sp TextPrimary, artista 11,5sp TextSecondary, fio Hairline. Sem vidro.
- **Capa:** quadrada, raio 22dp, sem efeito.

## 9. OLED, acessibilidade, performance

- Nenhum pixel acima de #000 além dos reflexos. O interior dos objetos é preto.
- A luz fica em linhas finas nas bordas (sem áreas grandes e claras: brilho e burn-in).
- A luz nunca carrega informação sozinha: estado sempre também por ícone ou texto.
- Contraste de texto essencial ≥ 7:1.
- Sensor: não precisa de permissão. Parar o sensor quando o app for pro segundo plano ainda não está implementado.
- Exige Android 13+ (API 33). Abaixo disso, cair para objeto preto com contorno branco de 10%.

## 10. Ordem de implementação

1. Adicionar `androidx.palette:palette-ktx` e copiar `AuroDesignSystem.kt` (ajustar o package).
2. Envolver as telas em `AuroTheme` (fundo preto, luz do ouvinte, relógio, cor animada).
3. Ligar `CoverGlow.from(capa)` à troca de faixa, com cache.
4. Trocar botões (incluindo ★ e ⋮) e cards por `AuroGlass`. Listas sem vidro.
5. Conferir o sinal da inclinação no aparelho.
6. Fonte e escala de texto.
7. Conferir num aparelho OLED: brilho baixo, 120 Hz, "remover animações".

## 11. Ajustes rápidos

| Quero | Mexer em |
|---|---|
| reflexo mais borrado ou mais fino | `soft` (0 a 1; padrão 0,6) |
| reflexo mais forte ou mais fraco | `uSpec` |
| linha de contorno mais ou menos presente | `0.06` e `0.20` no `rimCol` do shader |
| luz mais sensível à inclinação | `sideGain` em `AuroTilt.light` (1,6) |
| luz mais lenta ou mais rápida atrás do celular | `omega` em `TiltSmoother` (7) |
| luz mais forte embaixo do que em cima | `mix(0.6, 1.0, yN)` em `lightI` |

## 12. Status de teste

- **Testado:** o shader do vidro (compilado e renderizado no Skia, o mesmo motor do Android) e as contas de cor (`GlowTone`) e de inclinação (`AuroTilt`, `TiltSmoother`), compiladas com Kotlin numa JVM e idênticas à referência em Python (diferença de 5e-7).
- **Não testado:** Compose, leitura do sensor, Palette e animações no aparelho, e o sinal dos eixos. O espécime e a animação são simulações do shader feitas fora do Android.
- **Não implementado:** fusão "gooey" entre objetos (dois vidros que se juntam como líquido, marca registrada do liquid glass), parar o sensor em segundo plano, queda para API < 33, mini player.
