#pragma once

#include "Decoder.h"
#include <android/log.h>
#include <atomic>
#include <condition_variable>
#include <functional>
#include <memory>
#include <mutex>
#include <oboe/Oboe.h>
#include <thread>

#define LOG_TAG "AudioEngine_C"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class AudioEngine : public oboe::AudioStreamDataCallback {
public:
  AudioEngine();
  ~AudioEngine();

  bool load(const std::string &path);
  bool setNextAudio(const std::string &path); // Pre-Buffer pro Gapless

  bool start();
  void stop();
  void pause();
  void resume();
  void seek(long positionMs);

  long getPositionMs();

  void setVolume(float volume);

  void setOnPlaybackCompletedCallback(std::function<void()> callback);
  void setOnGaplessTransitionCallback(std::function<void()> callback);

  oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream,
                                        void *audioData,
                                        int32_t numFrames) override;

private:
  std::shared_ptr<oboe::AudioStream> mStream;
  std::unique_ptr<Decoder> mDecoder;
  std::unique_ptr<Decoder> mNextDecoder; // Fica espreitando em RAM pro Gapless
  std::mutex mLock;
  bool mIsPlaying = false;
  std::atomic<float> mVolume{1.0f};
  std::function<void()> mOnPlaybackCompleted;
  std::function<void()> mOnGaplessTransition;

  // Controle Seguro de Thread para Callbacks pós-áudio
  std::atomic<bool> mCompleted{false};
  std::atomic<bool> mGaplessTriggered{false};
  std::atomic<bool> mIsEngineDestroyed{false};
  std::thread mWatcherThread;
  std::condition_variable mWatcherCV;
  std::mutex mWatcherMutex;

  void openStream(int sampleRate = 0, int channelCount = 0);
  void watcherLoop();
};
