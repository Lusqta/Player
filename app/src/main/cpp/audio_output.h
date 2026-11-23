#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <memory>
#include <cstdint>
#include "ring_buffer.h"
#include "biquad_equalizer.h"

class AudioOutput : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    explicit AudioOutput(SpscRingBuffer* ringBuffer);
    ~AudioOutput() override;

    bool openStream(int32_t sampleRate);
    void closeStream();

    bool start();
    bool pause();
    bool stop();

    void setVolume(float volume);
    float getVolume() const;

    void setBalance(float balance);
    float getBalance() const;

    void setMono(bool mono);
    bool isMono() const;

    int32_t getSampleRate() const;
    uint64_t getUnderrunCount() const;

    void resetPlayedFrames(int64_t startingFrame = 0);
    int64_t getPlayedFrames() const;

    void setEqualizerBand(size_t band, float gainDb) { equalizer_.setBandGain(band, gainDb); }
    float getEqualizerBand(size_t band) const { return equalizer_.getBandGain(band); }
    void setEqualizerEnabled(bool enabled) { equalizer_.setEnabled(enabled); }
    bool isEqualizerEnabled() const { return equalizer_.isEnabled(); }

    void setPreamp(float gainDb) { equalizer_.setPreamp(gainDb); }
    float getPreamp() const { return equalizer_.getPreamp(); }
    void setBass(float gainDb) { equalizer_.setBass(gainDb); }
    float getBass() const { return equalizer_.getBass(); }
    void setTreble(float gainDb) { equalizer_.setTreble(gainDb); }
    float getTreble() const { return equalizer_.getTreble(); }
    void setToneEnabled(bool enabled) { equalizer_.setToneEnabled(enabled); }
    bool isToneEnabled() const { return equalizer_.isToneEnabled(); }

    // Oboe AudioStreamDataCallback
    // STRICT: No allocations, no mutexes, no logs, no JNI
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames) override;

    // Oboe AudioStreamErrorCallback
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    SpscRingBuffer* ringBuffer_;
    std::shared_ptr<oboe::AudioStream> stream_;
    BiquadEqualizer equalizer_;

    std::atomic<float> volume_{1.0f};
    std::atomic<float> balance_{0.0f}; // -1.0 (Full Left) to +1.0 (Full Right)
    std::atomic<bool> mono_{false};
    std::atomic<uint64_t> underrunCount_{0};
    std::atomic<int64_t> playedFrames_{0};

    int32_t currentSampleRate_ = 0;
    std::atomic<bool> isPlaying_{false};
};
