#include "AudioEngine.h"

AudioEngine::AudioEngine() {
  LOGI("Criando AudioEngine...");
  mWatcherThread = std::thread(&AudioEngine::watcherLoop, this);
}

AudioEngine::~AudioEngine() {
  LOGI("Destruindo AudioEngine...");
  stop();
  mIsEngineDestroyed = true;
  mWatcherCV.notify_one();
  if (mWatcherThread.joinable()) {
    mWatcherThread.join();
  }
}

bool AudioEngine::load(const std::string &path) {
  // 1. Primeiro paramos o stream FORA do lock, porque stop() tranca chamadas
  // aguardando a thread do Oboe fechar. Se tivéssemos o lock aqui, a thread do
  // Oboe congelaria no try_lock e o stop() nunca mais retornaria (DEADLOCK)
  stop();

  int sampleRate = 0;
  int channelCount = 0;
  bool decoderOk = false;

  // 2. Protegemos as variáveis e o Decoder
  {
    std::lock_guard<std::mutex> lock(mLock);

    // Limpa a flag de finalização para não atirar callbacks errôneos
    mCompleted = false;
    mHasNextDecoder = false;

    mDecoder = Decoder::create(path);

    if (mDecoder) {
      sampleRate = mDecoder->getSampleRate();
      channelCount = mDecoder->getChannelCount();
      decoderOk = true;
      LOGI("Arquivo carregado [%d Hz, %d canais]", sampleRate, channelCount);
    }
  }

  // 3. Fix Bug 6: Abre o stream FORA do lock para evitar glitch
  // (se o Oboe disparar callback imediatamente, o try_lock não falha)
  if (decoderOk) {
    openStream(sampleRate, channelCount);
    return true;
  }
  return false;
}

bool AudioEngine::setNextAudio(const std::string &path) {
  std::lock_guard<std::mutex> lock(mLock);
  LOGI("Preparando Gapless NextAudio: %s", path.c_str());
  mNextDecoder = Decoder::create(path);
  // Fix Bug 8: Sinalizar atomically que temos próximo decoder
  mHasNextDecoder = (mNextDecoder != nullptr);
  return mNextDecoder != nullptr;
}

void AudioEngine::openStream(int sampleRate, int channelCount) {
  // Fix 7: Fecha stream anterior explicitamente antes de abrir um novo
  if (mStream) {
    mStream->requestStop();
    mStream->close();
    mStream.reset();
  }

  oboe::AudioStreamBuilder builder;

  // Configurações para máxima eficiência de bateria (uso prolongado de
  // música)
  builder.setDirection(oboe::Direction::Output)
      ->setPerformanceMode(oboe::PerformanceMode::PowerSaving)
      ->setSharingMode(oboe::SharingMode::Exclusive) // Acesso direto ao
                                                     // hardware se possível
      ->setFormat(oboe::AudioFormat::Float) // Processamento nativo via Float32
      ->setContentType(oboe::ContentType::Music)
      ->setDataCallback(this);

  if (sampleRate > 0)
    builder.setSampleRate(sampleRate);
  if (channelCount > 0)
    builder.setChannelCount(channelCount);
  else
    builder.setChannelCount(oboe::ChannelCount::Stereo);

  oboe::Result result = builder.openStream(mStream);
  if (result != oboe::Result::OK) {
    LOGE("Falha ao abrir o stream. Erro: %s", oboe::convertToText(result));
  } else {
    LOGI("Stream aberto com sucesso.");
  }
}

bool AudioEngine::start() {
  if (mStream && mStream->getState() != oboe::StreamState::Started) {
    oboe::Result result = mStream->requestStart();
    if (result != oboe::Result::OK) {
      LOGE("Erro ao iniciar o stream: %s", oboe::convertToText(result));
      return false;
    }
    mIsPlaying = true;
    LOGI("Stream iniciado.");
    return true;
  }
  return false;
}

void AudioEngine::stop() {
  // Fix Bug 1: Primeiro paramos o stream (espera callback terminar)
  // Depois limpamos o decoder com lock
  if (mStream) {
    mStream->requestStop();
    mStream->close();
    mStream.reset();
    LOGI("Stream parado e fechado.");
  }
  mIsPlaying = false;
}

void AudioEngine::pause() {
  if (mStream && mIsPlaying) {
    mStream->requestPause();
    mIsPlaying = false;
    LOGI("Stream pausado.");
  }
}

void AudioEngine::resume() {
  if (mStream && !mIsPlaying) {
    oboe::Result result = mStream->requestStart();
    if (result != oboe::Result::OK) {
      LOGE("Erro ao retomar stream: %s", oboe::convertToText(result));
    } else {
      mIsPlaying = true;
      LOGI("Stream retomado.");
    }
  }
}

void AudioEngine::seek(long positionMs) {
  std::lock_guard<std::mutex> lock(mLock);
  if (mDecoder) {
    mDecoder->seek(positionMs);
  }
}

long AudioEngine::getPositionMs() {
  // Fix 3: Proteger acesso ao decoder com lock para evitar use-after-free
  std::lock_guard<std::mutex> lock(mLock);
  if (mDecoder) {
    return mDecoder->getPositionMs();
  }
  return 0;
}

void AudioEngine::setOnPlaybackCompletedCallback(
    std::function<void()> callback) {
  mOnPlaybackCompleted = callback;
}

void AudioEngine::setOnGaplessTransitionCallback(
    std::function<void()> callback) {
  mOnGaplessTransition = callback;
}

void AudioEngine::setVolume(float volume) {
  // Limita o volume entre 0.0 (Mudo) e 1.0 (Maximum)
  if (volume < 0.0f)
    volume = 0.0f;
  if (volume > 1.0f)
    volume = 1.0f;
  mVolume.store(volume);
}

// Callback invocado pelo Oboe em alta frequência informando que o buffer de
// saída precisa de dados
oboe::DataCallbackResult
AudioEngine::onAudioReady(oboe::AudioStream *audioStream, void *audioData,
                          int32_t numFrames) {
  float *floatData = static_cast<float *>(audioData);

  // Se não estiver tocando, simplesmente preencha com zeros para evitar
  // chiados
  if (!mIsPlaying) {
    memset(floatData, 0,
           numFrames * audioStream->getChannelCount() * sizeof(float));
    return oboe::DataCallbackResult::Continue;
  }

  int framesRead = 0;
  bool lockAcquired = false;

  // Tenta pegar o lock do decodificador sem bloquear o stream de audio se
  // demorar
  if (mLock.try_lock()) {
    lockAcquired = true;
    if (mDecoder) {
      framesRead = mDecoder->readFrames(floatData, numFrames);
    }
    mLock.unlock();
  }

  // Se o decodificador chegou ao fim da música atual
  if (framesRead < numFrames) {
    if (mLock.try_lock()) {
      if (mNextDecoder) {
        // MAGIA GAPLESS OCORRENDO
        LOGI("Realizando Transição Atômica Gapless para os frames restantes");
        int missingFrames = numFrames - framesRead;
        // Esvazia Unique Pointer no lugar do atual (destruindo o Antigo
        // silenciosamente)
        mDecoder = std::move(mNextDecoder);
        mNextDecoder = nullptr;
        mHasNextDecoder = false;

        // Fix Bug 2: Verifica se sample rate/channels mudou
        int newSR = mDecoder->getSampleRate();
        int newCH = mDecoder->getChannelCount();
        int currentChannels = audioStream->getChannelCount();

        if (mStream && (mStream->getSampleRate() != newSR ||
            currentChannels != newCH)) {
          // Formato diferente — NÃO ler frames com formato incompatível
          // (causaria buffer overrun). Preencher com silêncio e deixar o
          // watcher reabrir o stream no formato correto.
          LOGI("Gapless: Formato mudou (%d ch -> %d ch), delegando ao watcher",
               currentChannels, newCH);
          mNeedsStreamReopen = true;
        } else {
          // Mesmo formato — safe to fill remaining buffer
          int newFramesRead = mDecoder->readFrames(
              floatData + (framesRead * currentChannels), missingFrames);
          framesRead += newFramesRead;
        }

        // Dispara aviso em background pro Kotlin girar a UI
        if (!mGaplessTriggered.exchange(true)) {
          std::lock_guard<std::mutex> watcherLock(mWatcherMutex);
          mWatcherCV.notify_one();
        }
      }
      mLock.unlock();
    }
  }

  // Fim de faixa autêntico: SÓ detecta quando nós DE FATO lemos do decoder
  // (lockAcquired == true) e ele retornou 0 frames. Se o try_lock falhou,
  // framesRead == 0 é FALSO POSITIVO — o decoder ainda tem dados.
  // Fix Bug 8: Se mHasNextDecoder == true, não declarar fim — o gapless
  // simplesmente não conseguiu o lock neste ciclo.
  if (framesRead < numFrames) {
    int channels = audioStream->getChannelCount();
    memset(floatData + framesRead * channels, 0,
           (numFrames - framesRead) * channels * sizeof(float));

    if (lockAcquired && mIsPlaying && framesRead == 0 &&
        !mHasNextDecoder.load()) {
      mIsPlaying = false;
      if (!mCompleted.exchange(true)) {
        std::lock_guard<std::mutex> lock(mWatcherMutex);
        mWatcherCV.notify_one();
      }
    }
  }

  // Aplica o Master Volume (Ganho Linear / Ducking) em todos os frames lidos
  // do decoder
  float currentVolume = mVolume.load();
  if (currentVolume != 1.0f) {
    int totalSamples = numFrames * audioStream->getChannelCount();
    for (int i = 0; i < totalSamples; ++i) {
      floatData[i] *= currentVolume;
    }
  }

  return oboe::DataCallbackResult::Continue;
}

void AudioEngine::watcherLoop() {
  while (!mIsEngineDestroyed) {
    std::unique_lock<std::mutex> lock(mWatcherMutex);
    // Fix 6: Incluir mGaplessTriggered e mNeedsStreamReopen na condição
    mWatcherCV.wait_for(lock, std::chrono::milliseconds(50), [this] {
      return mCompleted.load() || mGaplessTriggered.load() || 
             mNeedsStreamReopen.load() || mIsEngineDestroyed.load();
    });

    if (mIsEngineDestroyed)
      break;

    // Fix Bug 4: Reabrir stream fora do callback de áudio (seguro)
    // Usa mLock para sincronizar com load()/stop() no acesso ao mStream
    if (mNeedsStreamReopen) {
      mNeedsStreamReopen = false;
      lock.unlock(); // Libera o watcher mutex antes de operar no stream

      int newSR = 0;
      int newCH = 0;

      {
        std::lock_guard<std::mutex> dataLock(mLock);
        LOGI("Watcher: Reabrindo stream para novo sample rate");
        if (mStream) {
          mStream->requestStop();
          mStream->close();
          mStream.reset();
        }
        if (mDecoder) {
          newSR = mDecoder->getSampleRate();
          newCH = mDecoder->getChannelCount();
        }
      }

      if (newSR > 0 && newCH > 0) {
        openStream(newSR, newCH);
        if (mStream) {
          mStream->requestStart();
          mIsPlaying = true;
        }
      }
      continue; // Volta pro loop sem processar outros eventos neste tick
    }

    if (mCompleted) {
      mCompleted = false; // reseta
      if (mOnPlaybackCompleted) {
        LOGI("Watcher disparando OnPlaybackCompleted");
        mOnPlaybackCompleted();
      }
    }

    if (mGaplessTriggered) {
      mGaplessTriggered = false; // reseta
      if (mOnGaplessTransition) {
        LOGI("Watcher disparando OnGaplessTransitionAcquired");
        mOnGaplessTransition();
      }
    }
  }
}
