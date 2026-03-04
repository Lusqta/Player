#include "AudioEngine.h"

AudioEngine::AudioEngine() { LOGI("Criando AudioEngine..."); }

AudioEngine::~AudioEngine() {
  LOGI("Destruindo AudioEngine...");
  stop();
}

bool AudioEngine::load(const std::string &path) {
  std::lock_guard<std::mutex> lock(mLock);

  // Para o stream atual antes de carregar um novo
  stop();
  mDecoder = Decoder::create(path);

  if (mDecoder) {
    LOGI("Arquivo carregado [%d Hz, %d canais]", mDecoder->getSampleRate(),
         mDecoder->getChannelCount());
    openStream(mDecoder->getSampleRate(), mDecoder->getChannelCount());
    return true;
  }
  return false;
}

void AudioEngine::openStream(int sampleRate, int channelCount) {
  oboe::AudioStreamBuilder builder;

  // Configurações para mínima latência e máxima eficiência de bateria
  // (performance C/C++)
  builder.setDirection(oboe::Direction::Output)
      ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
      ->setSharingMode(
          oboe::SharingMode::Exclusive) // Acesso direto ao hardware se possível
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
  if (mDecoder) {
    mDecoder->seek(positionMs);
  }
}

// Callback invocado pelo Oboe em alta frequência informando que o buffer de
// saída precisa de dados
oboe::DataCallbackResult
AudioEngine::onAudioReady(oboe::AudioStream *audioStream, void *audioData,
                          int32_t numFrames) {
  float *floatData = static_cast<float *>(audioData);

  // Se não estiver tocando, simplesmente preencha com zeros para evitar chiados
  if (!mIsPlaying) {
    memset(floatData, 0,
           numFrames * audioStream->getChannelCount() * sizeof(float));
    return oboe::DataCallbackResult::Continue;
  }

  int framesRead = 0;

  // Tenta pegar o lock do decodificador sem bloquear o stream de audio se
  // demorar
  if (mLock.try_lock()) {
    if (mDecoder) {
      framesRead = mDecoder->readFrames(floatData, numFrames);
    }
    mLock.unlock();
  }

  // Se o decodificador chegou ao fim (ou se não lemos nada), preenchemos o
  // resto do buffer com silence
  if (framesRead < numFrames) {
    int channels = audioStream->getChannelCount();
    memset(floatData + framesRead * channels, 0,
           (numFrames - framesRead) * channels * sizeof(float));
  }

  return oboe::DataCallbackResult::Continue;
}
