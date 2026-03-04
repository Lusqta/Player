#pragma once

#include "Decoder.h"
#include <android/log.h>
#include <memory>
#include <mutex>
#include <oboe/Oboe.h>


#define LOG_TAG "AudioEngine_C"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class AudioEngine : public oboe::AudioStreamDataCallback {
public:
  AudioEngine();
  ~AudioEngine();

  bool load(const std::string &path);
  bool start();
  void stop();
  void pause();
  void resume();
  void seek(long positionMs);

  oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream,
                                        void *audioData,
                                        int32_t numFrames) override;

private:
  std::shared_ptr<oboe::AudioStream> mStream;
  std::unique_ptr<Decoder> mDecoder;
  std::mutex mLock;
  bool mIsPlaying = false;

  void openStream(int sampleRate = 0, int channelCount = 0);
};
