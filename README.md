# Aurō Player for Android

Reprodutor de música offline de alta fidelidade para Android, equipado com motor de áudio nativo em C++ (Google Oboe), biblioteca privada via pastas autorizadas (Storage Access Framework), equalizador biquad de 10 bandas e interface minimalista **Editorial Dark OLED**.

![Android](https://img.shields.io/badge/Android-API%2026%2B%20(AGSL%2033%2B)-black?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-purple?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09.00-blue?logo=jetpackcompose)
![C++](https://img.shields.io/badge/C%2B%2B-17%20%7C%20Oboe%201.9.3-00599C?logo=cplusplus)
![License](https://img.shields.io/badge/License-MIT-success)
[![Download APK](https://img.shields.io/badge/⬇️%20Download-v1.1.1-00C853?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Lusqta/Player/releases/latest)
[![Releases](https://img.shields.io/badge/📦%20Ver-Releases-blue?style=for-the-badge&logo=github)](https://github.com/Lusqta/Player/releases)
[![Build Status](https://img.shields.io/github/actions/workflow/status/Lusqta/Player/build-apk.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/Lusqta/Player/actions)

---

## 📲 Download do APK

Baixe a versão mais recente diretamente da página de lançamentos:

* 🚀 **[⬇️ Baixar Última Versão (`AuroPlayer-latest.apk`)](https://github.com/Lusqta/Player/releases/latest)**
* 📦 **[Página com Todas as Releases](https://github.com/Lusqta/Player/releases)**

> **Como instalar:**
> 1. Baixe o APK em seu celular pelo link acima.
> 2. Toque no arquivo baixado na central de notificações.
> 3. Caso solicitado pelo sistema, autorize a instalação a partir do navegador ou gerenciador de arquivos.
> 4. Toque em **Instalar** (ou **Atualizar**).

---

## ✦ Principais Recursos

### 1. Motor de Áudio Nativo de Alta Performance (C++ & Oboe)
* **Baixa latência e zero alocações:** Callback de áudio em tempo real executando no thread de áudio prioritário sem alocação dinâmica de memória (`malloc`/`new`), sem locks e sem chamadas JNI.
* **SPSC Ring Buffer Lock-Free:** Fila de blocos estéreo com sincronização atômica (`std::atomic`) entre a thread de decodificação e o thread de saída do Oboe.
* **Formatos Nativos:**
  * **WAV / PCM:** Decodificador `dr_wav` com suporte a 8, 16, 24 e 32 bits ponto flutuante.
  * **FLAC:** Decodificador `dr_flac` sem perdas para arquivos de alta resolução.
  * **MP3:** Decodificador `dr_mp3` integrado.
  * **AAC, M4A, ALAC, OGG, Opus:** Decodificador de hardware via `AMediaCodec` / Android NDK Media.

### 2. Processamento Digital de Sinal (DSP)
* **Equalizador Paramétrico de 10 Bandas:** Filtros biquad IIR nas frequências ISO (31 Hz, 62 Hz, 125 Hz, 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz, 8 kHz e 16 kHz), com amplitude de ±12.0 dB e presets salvos no banco local SQLite.
* **Controles de Tom & Pré-amplificação:** Pré-amp geral, ajuste dedicado de Graves (Shelf 100 Hz) e Agudos (Shelf 10 kHz).
* **Balanço Estéreo & Modo Mono:** Ajuste fino de atenuação de canais e soma mono para acessibilidade ou audição com fone único.
* **Crossfade & ReplayGain:** Transição suave configurável entre faixas e nivelamento automático de volume com suporte a ganho de faixa e álbum.

### 3. Biblioteca Musical Privada via Storage Access Framework (SAF)
* **Acesso Restrito:** O app não requisita leitura global de arquivos (`READ_EXTERNAL_STORAGE`); acessa exclusivamente os diretórios autorizados pelo usuário.
* **Indexação com Cache SQLite:** Varredura rápida de metadados, contagem de reproduções, histórico e favoritos.
* **Múltiplos Artistas:** Suporte nativo à separação por `;` com exibição combinada e agrupamento individual nas telas de artistas.
* **Filtros e Limpeza:** Ignora mensagens de voz de mensageiros (áudios curtos ou pastas como WhatsApp) nos destaques da tela inicial.
* **Letras Sincronizadas (.LRC):** Carregamento de arquivos `.lrc` na mesma pasta da faixa ou embutidos nos metadados ID3, com acompanhamento em tempo real.

### 4. Interface Editorial Dark OLED
* **Preto Absoluto OLED (`#000000`):** Economia de bateria e contraste puro sem superfícies cinzas artificiais.
* **Tipografia Editorial:** Famílias tipográficas **Plus Jakarta Sans** (títulos, métricas e controles) e **Manrope** (subtítulos e metadados).
* **Ambient Mesh Glow:** Fundo atmosférico sutil de 2 cores derivado das tonalidades da capa do álbum via `androidx.palette` (com suporte a shaders AGSL no Android 13+).
* **Monogramas Determinísticos:** Fallback elegante de gradiente escuro e iniciais tipográficas para artistas sem capa.
* **Controles Gestuais:** Scrubber de áudio fluido, mini player flutuante integrado e fila reordenável por arrasto.

---

## 📂 Estrutura do Projeto

```
player/
├── .github/
│   └── workflows/
│       └── build-apk.yml           # CI/CD para compilação automática de release APK
├── archived_spatial_audio/         # Arquivamento isolado do antigo módulo de áudio espacial
├── app/
│   ├── build.gradle.kts            # Configurações de compilação, NDK e dependências
│   └── src/
│       ├── main/
│       │   ├── cpp/
│       │   │   ├── CMakeLists.txt              # Configuração do build nativo C++
│       │   │   ├── audio_engine.h / .cpp       # Orquestrador do motor de áudio e threads
│       │   │   ├── audio_output.h / .cpp       # Callback Oboe real-time e DSP
│       │   │   ├── biquad_equalizer.h          # Equalizador 10 bandas, pré-amp e tons
│       │   │   ├── decoder.h                   # Interface base para decodificadores
│       │   │   ├── ring_buffer.h               # SPSC Ring Buffer lock-free
│       │   │   ├── jni_bridge.cpp              # Pontes JNI C++ ↔ Kotlin
│       │   │   └── decoders/                   # Decodificadores WAV, FLAC, MP3 e AMedia
│       │   ├── java/com/lucas/player/
│       │   │   ├── MainActivity.kt             # Ponto de entrada e navegação
│       │   │   ├── AudioEngine.kt              # Wrapper JNI
│       │   │   ├── data/                       # LibraryRepository, CoverArtLoader, SQLite, LRC
│       │   │   ├── model/                      # Track, LibraryCategory, ArtistUtils
│       │   │   ├── queue/                      # QueueManager e histórico
│       │   │   ├── service/                    # PlaybackService e MediaSession
│       │   │   └── ui/
│       │   │       ├── components/             # AuroComponents, AuroOverlay, AuroFeedback
│       │   │       ├── screens/                # Home, NowPlaying, Drilldown, Equalizer, etc.
│       │   │       └── theme/                  # AuroDesignSystem (Cores, Tipos, AuroSquircle)
│       │   └── res/
│       │       ├── font/                       # Fontes TTF (Plus Jakarta Sans e Manrope)
│       │       ├── values/ e values-en/        # Strings multilíngues (pt-BR e en-US)
│       │       └── drawable/                   # Ícones de sistema e notificação
│       └── test/                               # Testes unitários JUnit (QueueManager, AuroTilt)
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🛠️ Como Compilar o Projeto

### Pré-requisitos
* **Java Development Kit (JDK):** Versão 17 (ex.: Eclipse Temurin 17).
* **Android SDK:** Compile SDK 35, Build Tools 34.0.0+.
* **Android NDK:** Versão `26.3.11579264` (configurada em `app/build.gradle.kts`).
* **CMake:** Versão `3.22.1`.

### Passos no Terminal

1. **Clonar o repositório:**
   ```bash
   git clone https://github.com/Lusqta/Player.git
   cd Player
   ```

2. **Executar os testes unitários:**
   ```bash
   ./gradlew testDebugUnitTest
   ```

3. **Compilar o APK de desenvolvimento (Debug):**
   ```bash
   ./gradlew assembleDebug
   ```
   O APK gerado estará em `app/build/outputs/apk/debug/app-debug.apk` (ou `AuroPlayer-v1.1.1.apk` na raiz).

4. **Compilar o APK otimizado de produção (Release):**
   ```bash
   ./gradlew assembleRelease
   ```

---

## 📄 Licença

Este projeto é software livre licenciado sob os termos da licença [MIT](LICENSE).
