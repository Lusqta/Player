# Aurō Player for Android

Um reprodutor de música offline de alta fidelidade para Android, combinando um motor de áudio nativo de baixa latência (C++ Oboe / AudioTrack), arquitetura de navegação inspirada no Poweramp e o **Aurō Design System v3** (vidro preto perceptual com iluminação paralela reativa à gravidade e ao giroscópio do aparelho).

![Android](https://img.shields.io/badge/Android-API%2026%2B%20(AGSL%2033%2B)-black?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024-blue?logo=jetpackcompose)
![License](https://img.shields.io/badge/License-Proprietary-gray)
[![Download APK](https://img.shields.io/badge/⬇️%20Download-AuroPlayer--Debug.apk-00C853?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Lusqta/Player/releases/download/latest/AuroPlayer-Debug.apk)
[![Releases](https://img.shields.io/badge/📦%20Ver-Releases-blue?style=for-the-badge&logo=github)](https://github.com/Lusqta/Player/releases)
[![Build Status](https://img.shields.io/github/actions/workflow/status/Lusqta/Player/build-apk.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/Lusqta/Player/actions)

---

## 📲 Download do APK

Para instalar o **Aurō Player** diretamente no seu dispositivo Android:

* **[⬇️ Baixar APK Direto (Última Versão Compilada)](https://github.com/Lusqta/Player/releases/download/latest/AuroPlayer-Debug.apk)**
* **[📦 Página de Releases no GitHub](https://github.com/Lusqta/Player/releases)**
* **[⚙️ Builds Automatizados (GitHub Actions Artifacts)](https://github.com/Lusqta/Player/actions)**

> 💡 **Como instalar:**
> 1. Baixe o arquivo `AuroPlayer-Debug.apk` no seu celular.
> 2. Abra a notificação de download ou o app de arquivos e toque no `.apk`.
> 3. Caso o Android solicite, habilite *"Permitir instalação a partir desta fonte"*.
> 4. Toque em **Instalar** e aproveite!

---

## ✦ Visão Geral do Aurō Design System v3

O visual do aplicativo adota o princípio de **mundo vazio e luz do ouvinte**:
1. **Preto Absoluto (`#000000`)**: O fundo de todas as telas, cards e diálogos é estritamente preto puro. Sem cinzas de superfície (`#121212`) e sem caixas de sombra difusa.
2. **Luz do Ouvinte Única e Paralela**: A luz não é desenhada como círculo ou gradiente no fundo — ela aparece exclusivamente como reflexo especular nas bordas dos objetos de vidro voltadas para ela. A direção é paralela em todos os objetos e vem de baixo `(dx = 0, dy = 1)`.
3. **Sensor de Inclinação (`AuroTilt` & `TiltSmoother`)**:
   - Conectado ao sensor de gravidade (`TYPE_GRAVITY`, com fallback para `TYPE_ACCELEROMETER`).
   - Suavizado por mola amortecida ($\omega = 7\text{ rad/s}, \zeta = 0{,}8$): a luz acompanha o movimento físico do celular como líquido, sem tremores e sem recompilar a UI desnecessariamente (leituras em fase de desenho via `State`/lambda).
   - Flutuações lentas de onda e respiração de $\pm 4^\circ$ enquanto a música toca.
4. **Vidro Preto Perceptual (`AuroGlass`)**:
   - Implementado com **AGSL `RuntimeShader`** no Android 13+ (API 33+).
   - O corpo do objeto é preto (`uBody = 0f`). A luz se concentra nas arestas com dois lóbulos (estreito e largo, `soft = 0.6f`), contorno com presença contínua (6% a 26%), dispersão cromática ($\pm 2^\circ$ por canal) e dither anti-banding.
   - Fallback gracioso para Android 12- com aro sutil de 10% de opacidade sobre o preto.
5. **Cores da Capa (`CoverGlow` & `GlowTone`)**:
   - Extrai automaticamente a cor vibrante da capa do álbum com `androidx.palette`.
   - Gera o par harmônico de brilho `Accent A` (reflexo principal e barra de progresso) e `Accent B` (halo interno secundário).
   - Transição suave de 900ms no espaço de cores Oklab entre faixas.

---

## ✦ Arquitetura do Projeto

```
app/src/main/java/com/lucas/player/
├── MainActivity.kt                 # Ponto de entrada, ciclo de vida, permissões e injeção do AuroTheme
├── audio/
│   ├── AudioEngine.kt              # Interface JNI para o motor nativo em C++
│   ├── EqualizerEngine.kt          # Equalizador paramétrico de 10 bandas + Pré-amplificador + Tone (Bass/Treble)
│   └── cpp/                        # Código nativo C++ (Oboe, AudioTrack, filtros biquad, balance)
├── data/
│   ├── LibraryRepository.kt        # Varredura do MediaStore local, pastas, gêneros e metadados
│   ├── MusicDatabase.kt            # Banco SQLite local (estatísticas, contadores de reprodução, presets de EQ)
│   ├── PreferencesManager.kt       # Persistência de preferências e configurações de áudio
│   ├── CoverArtLoader.kt           # Carregador assíncrono de capas de álbum com cache LRU
│   └── LyricsManager.kt            # Suporte e sincronização de letras LRC / embutidas
├── model/
│   ├── Track.kt                    # Entidade principal de faixa (id, título, artista, duração, bitrate, codec)
│   ├── LibraryCategory.kt          # Categorias: ALL_SONGS, FOLDERS, ALBUMS, ARTISTS, PLAYLISTS, QUEUE, etc.
│   ├── SortMode.kt                 # Modos de ordenação (A-Z, data, artista, ano, duração)
│   └── UserPlaylist.kt             # Playlists criadas pelo usuário
├── queue/
│   ├── QueueManager.kt             # Gestão de fila dupla (Fila Prioritária "A Seguir" + Fila Principal)
│   ├── ShuffleMode.kt              # Modos aleatórios (OFF, ALL_TRACKS, CATEGORY)
│   └── RepeatMode.kt               # Modos de repetição (OFF, ONE, ALL, CATEGORY)
├── service/
│   └── PlaybackService.kt          # Serviço de execução em primeiro plano (MediaSession, Notificação, Sleep Timer)
└── ui/
    ├── navigation/
    │   ├── NavScreen.kt            # Definição selada de telas do app
    │   └── PlayerNavController.kt  # Pilha de navegação hierárquica estilo Poweramp com BackHandler
    ├── theme/
    │   └── AuroDesignSystem.kt     # Tokens oficiais, AuroLight, AuroTilt, AuroTheme, AuroGlass e AGSL
    ├── components/
    │   └── AuroComponents.kt       # AuroProgressBar, AuroMiniPlayer, AuroNavbar, AuroTrackRow, etc.
    └── screens/
        ├── AuroScreens.kt          # Now Playing, LibraryRoot, CategoryList, Drilldown, Equalizer, Search, Queue
        └── AuroOverlays.kt         # Diálogos: Menu Contextual, Engine Info, Temporizador, Letras, Presets
```

---

## ✦ Telas e Funcionalidades

### 1. Now Playing (`AuroNowPlayingScreen`)
- Capa com raio de 22dp e gestos de arrasto (swipe down para recolher, swipe lateral para trocar de faixa).
- Título da faixa e artista alinhados à esquerda.
- Dois botões redondos de vidro preto de 40dp à direita: **`★`** (favoritar/classificar) e **`⋮`** (menu de contexto e info).
- Barra de progresso de 3dp com thumb de 10dp, tempo decorrido à esquerda e restante/total à direita.
- Controles de reprodução: anterior (56dp), play/pause (76dp) e próximo (56dp) em círculos de vidro preto com reflexo direcional; botões de shuffle e repeat simples.
- Cards largos de vidro preto: **`[ Fila ]`** e **`[ Letras ]`**.
- Pré-visualização da fila com a identificação da lista (ex: `VOX (7)`) e próximas faixas diretamente sobre o preto com divisores de 1dp Hairline.

### 2. Navegação Hierárquica da Biblioteca
- **Raiz da Biblioteca**: Lista de categorias principais (Todas as Músicas, Pastas, Álbuns, Artistas, Gêneros, Listas de Reprodução, Fila, Anos, Compositores, Mais Tocadas, etc.).
- **Navegação com Breadcrumbs**: Cabeçalho de migalhas de pão (`AuroBreadcrumbHeader`) com navegação de retorno fluida.
- **Drilldown**: Pastas -> Subpastas / Faixas; Álbuns -> Lista de Faixas do Álbum; Artistas -> Álbuns -> Faixas.
- **Fast Scroller Alfabético**: Barra lateral A-Z com bolha flutuante em vidro preto para busca rápida em listas com mais de 15 itens.

### 3. Fila de Reprodução Dupla (`QueueManager`)
- **Fila Prioritária ("A Seguir")**: Faixas adicionadas manualmente com "Tocar a seguir" são reproduzidas com prioridade máxima.
- **Fila Principal**: Mantém a ordem original da lista ou álbum que estava tocando.
- Funções para embaralhar, limpar fila prioritária e reordenar.

### 4. Motor de Áudio & Equalizador
- Equalizador gráfico/paramétrico de 10 bandas (31Hz a 16kHz) com ganho de -12dB a +12dB.
- Controles dedicados de **Tone** (Graves e Agudos) e **Pré-amplificador (Preamp)**.
- Alternância Mono/Estéreo e controle de balanço L/R.
- Gerenciamento de presets (salvar, carregar, redefinir para Flat, excluir customizados).

### 5. Mini Player & Navbar Inferior
- Mini player persistente em vidro preto de 72dp localizado sobre a luz do ouvinte na parte inferior.
- Barra de navegação inferior permanente com botões rápidos: **Equalizador**, **Biblioteca**, **Busca** e **Menu**.

---

## ✦ Como Compilar e Executar

### Pré-requisitos
- Android Studio Ladybug / Koala ou JDK 17+.
- Android SDK 34 (mínimo API 26; AGSL ativo em API 33+).
- NDK (Side by Side) e CMake para compilação C++.

### Comandos Gradle
```powershell
# Compilar apenas os fontes Kotlin
.\gradlew.bat compileDebugKotlin

# Montar o APK de depuração
.\gradlew.bat assembleDebug
```

O arquivo gerado estará localizado em:
`app/build/outputs/apk/debug/app-debug.apk`

---

## ✦ Especificações de Design Adicionais
Consulte o diretório [`files/`](files/) para a especificação original completa:
- [`DESIGN_SYSTEM.md`](files/DESIGN_SYSTEM.md): Especificação textual do Aurō Design System v3.
- [`AuroDesignSystem.kt`](files/AuroDesignSystem.kt): Implementação de referência pura.
- [`especimen_design_system.png`](files/especimen_design_system.png): Imagem do espécime visual e variantes de capa.
