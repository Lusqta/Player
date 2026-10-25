#pragma once

#include <memory>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <cstdint>

#include "decoder.h"
#include "ring_buffer.h"
#include "audio_output.h"

enum class PlaybackState {
    Stopped = 0,
    Playing = 1,
    Paused = 2
};

class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    // Disallow copy/move
    AudioEngine(const AudioEngine&) = delete;
    AudioEngine& operator=(const AudioEngine&) = delete;

    bool openFile(int fd);
    bool play();
    bool pause();
    bool stop();
    bool seekTo(int64_t frameIndex);

    void setVolume(float volume);
    float getVolume() const;

    int64_t getPositionFrames() const;
    int64_t getDurationFrames() const;
    int32_t getSampleRate() const;
    int32_t getChannelCount() const;

    bool isPlaying() const;
    bool isPaused() const;

    void setEqualizerBand(size_t band, float gainDb);
    float getEqualizerBand(size_t band) const;
    void setEqualizerEnabled(bool enabled);
    bool isEqualizerEnabled() const;

    void setPreamp(float gainDb);
    float getPreamp() const;
    void setBass(float gainDb);
    float getBass() const;
    void setTreble(float gainDb);
    float getTreble() const;
    void setToneEnabled(bool enabled);
    bool isToneEnabled() const;

    void setBalance(float balance);
    float getBalance() const;
    void setMono(bool mono);
    bool isMono() const;

    static std::vector<float> scanWaveform(int fd, int numPoints = 100);

private:
    void decodeLoop();
    void wakeDecoderThread();
    void stopInternal();

    SpscRingBuffer ringBuffer_;
    std::unique_ptr<AudioOutput> audioOutput_;
    std::unique_ptr<IDecoder> decoder_;

    std::atomic<PlaybackState> state_{PlaybackState::Stopped};
    std::atomic<bool> isRunning_{false};
    std::atomic<bool> seekRequested_{false};
    std::atomic<int64_t> seekTargetFrame_{0};
    std::atomic<bool> isEos_{false};

    std::thread decodeThread_;
    std::mutex decodeMutex_;
    std::condition_variable decodeCv_;
    std::recursive_mutex controlMutex_; // Protects open/seek decoder swaps
};
