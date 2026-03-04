#include "Decoder.h"

#define DR_MP3_IMPLEMENTATION
#include "dr_mp3.h"

#define DR_FLAC_IMPLEMENTATION
#include "dr_flac.h"

#include <android/log.h>
#define LOG_TAG "AudioDecoder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class Mp3Decoder : public Decoder {
  drmp3 mp3;
  bool isOpen = false;

public:
  ~Mp3Decoder() override { close(); }

  bool open(const std::string &path) override {
    if (drmp3_init_file(&mp3, path.c_str(), nullptr)) {
      isOpen = true;
      return true;
    }
    return false;
  }

  void close() override {
    if (isOpen) {
      drmp3_uninit(&mp3);
      isOpen = false;
    }
  }

  int readFrames(float *buffer, int numFrames) override {
    if (!isOpen)
      return 0;
    return (int)drmp3_read_pcm_frames_f32(&mp3, numFrames, buffer);
  }

  void seek(long positionMs) override {
    if (!isOpen)
      return;
    uint64_t targetFrame = (positionMs * mp3.sampleRate) / 1000;
    drmp3_seek_to_pcm_frame(&mp3, targetFrame);
  }

  int getSampleRate() const override { return isOpen ? mp3.sampleRate : 0; }
  int getChannelCount() const override { return isOpen ? mp3.channels : 0; }
  long getDurationMs() const override {
    if (!isOpen)
      return 0;
    uint64_t frames = drmp3_get_pcm_frame_count(const_cast<drmp3 *>(&mp3));
    return (long)((frames * 1000) / mp3.sampleRate);
  }
};

class FlacDecoder : public Decoder {
  drflac *flac = nullptr;

public:
  ~FlacDecoder() override { close(); }

  bool open(const std::string &path) override {
    flac = drflac_open_file(path.c_str(), nullptr);
    return flac != nullptr;
  }

  void close() override {
    if (flac) {
      drflac_close(flac);
      flac = nullptr;
    }
  }

  int readFrames(float *buffer, int numFrames) override {
    if (!flac)
      return 0;
    return (int)drflac_read_pcm_frames_f32(flac, numFrames, buffer);
  }

  void seek(long positionMs) override {
    if (!flac)
      return;
    uint64_t targetFrame = (positionMs * flac->sampleRate) / 1000;
    drflac_seek_to_pcm_frame(flac, targetFrame);
  }

  int getSampleRate() const override { return flac ? flac->sampleRate : 0; }
  int getChannelCount() const override { return flac ? flac->channels : 0; }
  long getDurationMs() const override {
    if (!flac)
      return 0;
    return (long)((flac->totalPCMFrameCount * 1000) / flac->sampleRate);
  }
};

std::unique_ptr<Decoder> Decoder::create(const std::string &path) {
  if (path.empty())
    return nullptr;

  std::unique_ptr<Decoder> decoder;
  std::string ext = "";
  size_t dotPos = path.find_last_of('.');
  if (dotPos != std::string::npos) {
    ext = path.substr(dotPos);
  }

  // Converte a extensao para minusculo
  for (auto &c : ext)
    c = tolower(c);

  if (ext == ".mp3") {
    decoder = std::make_unique<Mp3Decoder>();
  } else if (ext == ".flac") {
    decoder = std::make_unique<FlacDecoder>();
  } else {
    LOGE("Formato não suportado: %s", path.c_str());
    return nullptr;
  }

  if (decoder->open(path)) {
    LOGI("Arquivo aberto com sucesso: %s", path.c_str());
    return decoder;
  } else {
    LOGE("Erro ao ler o arquivo: %s", path.c_str());
    return nullptr;
  }
}
