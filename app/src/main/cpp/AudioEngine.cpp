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

  // 2. Agora sim protegemos as variáveis e o Decoder
  std::lock_guard<std::mutex> lock(mLock);

  // Limpa a flag de finalização para não atirar callbacks errôneos
  mCompleted = false;

  mDecoder = Decoder::create(path);

  if (mDecoder) {
    LOGI("Arquivo carregado [%d Hz, %d canais]", mDecoder->getSampleRate(),
         mDecoder->getChannelCount());
    openStream(mDecoder->getSampleRate(), mDecoder->getChannelCount());
    return true;
  }
  return false;
}

bool AudioEngine::setNextAudio(const std::string &path) {
  std::lock_guard<std::mutex> lock(mLock);
  LOGI("Preparando Gapless NextAudio: %s", path.c_str());
  mNextDecoder = Decoder::create(path);
  return mNextDecoder != nullptr;
}

void AudioEngine::openStream(int sampleRate, int channelCount) {
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
    mStream->requestStart();
    mIsPlaying = true;
    LOGI("Stream retomado.");
  }
}

void AudioEngine::seek(long positionMs) {
  std::lock_guard<std::mutex> lock(mLock);
  if (mDecoder) {
    mDecoder->seek(positionMs);
  }
}

long AudioEngine::getPositionMs() {
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

        int channels = audioStream->getChannelCount();
        // Preenche o resto do buffer que sobrou daquele microsegundo com a
        // Nova Música!
        int newFramesRead = mDecoder->readFrames(
            floatData + (framesRead * channels), missingFrames);
        framesRead += newFramesRead;

        // Dispara aviso em background pro Kotlin girar a UI
        if (!mGaplessTriggered.exchange(true)) {
          std::lock_guard<std::mutex> watcherLock(mWatcherMutex);
          mWatcherCV.notify_one();
        }
      }
      mLock.unlock();
    }
  }

  // Se mesmo com(ou sem) Gapless nós não completamos o buffer do C++,
  // significa Fim de Playlist autêntico (silêncio).
  if (framesRead < numFrames) {
    int channels = audioStream->getChannelCount();
    memset(floatData + framesRead * channels, 0,
           (numFrames - framesRead) * channels * sizeof(float));

    if (mIsPlaying && framesRead == 0) {
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

  // Se esgotou sem gapless
  if (framesRead == 0 && lockAcquired && !mCompleted && !mGaplessTriggered) {
    LOGI("Fim do arquivo alcançado na thread de áudio. Levantando flag "
         "mCompleted.");
    mIsPlaying = false;
    mCompleted = true;
  }

  return oboe::DataCallbackResult::Continue;
}

void AudioEngine::watcherLoop() {
  while (!mIsEngineDestroyed) {
    std::unique_lock<std::mutex> lock(mWatcherMutex);
    mWatcherCV.wait_for(lock, std::chrono::milliseconds(50), [this] {
      return mCompleted.load() || mIsEngineDestroyed.load();
    });

    if (mIsEngineDestroyed)
      break;

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
