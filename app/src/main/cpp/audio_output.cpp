#include "audio_output.h"

AudioOutput::AudioOutput(SpscRingBuffer* ringBuffer)
    : ringBuffer_(ringBuffer) {}

AudioOutput::~AudioOutput() {
    closeStream();
}

bool AudioOutput::openStream(int32_t sampleRate) {
    if (stream_ && currentSampleRate_ == sampleRate) {
        return true;
    }

    closeStream();

    oboe::AudioStreamBuilder builder;
    builder.setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(oboe::ChannelCount::Stereo)
           ->setSampleRate(sampleRate)
           ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
           ->setPerformanceMode(oboe::PerformanceMode::None)
           ->setSharingMode(oboe::SharingMode::Shared)
           ->setDataCallback(this)
           ->setErrorCallback(this);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        stream_.reset();
        return false;
    }

    currentSampleRate_ = sampleRate;
    equalizer_.setSampleRate(sampleRate);
    return true;
}

void AudioOutput::closeStream() {
    isPlaying_.store(false, std::memory_order_relaxed);
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
    currentSampleRate_ = 0;
}

bool AudioOutput::start() {
    if (!stream_) return false;
    oboe::StreamState state = stream_->getState();
    if (state == oboe::StreamState::Closed) return false;

    if (state != oboe::StreamState::Started) {
        oboe::Result res = stream_->requestStart();
        if (res != oboe::Result::OK) {
            return false;
        }
    }
    isPlaying_.store(true, std::memory_order_relaxed);
    return true;
}

bool AudioOutput::pause() {
    isPlaying_.store(false, std::memory_order_relaxed);
    if (!stream_) return false;
    oboe::StreamState state = stream_->getState();
    if (state == oboe::StreamState::Started) {
        oboe::Result res = stream_->requestPause();
        return (res == oboe::Result::OK);
    }
    return true;
}

bool AudioOutput::stop() {
    isPlaying_.store(false, std::memory_order_relaxed);
    if (!stream_) return false;
    oboe::StreamState state = stream_->getState();
    if (state == oboe::StreamState::Started || state == oboe::StreamState::Paused) {
        oboe::Result res = stream_->requestStop();
        return (res == oboe::Result::OK);
    }
    return true;
}

void AudioOutput::setVolume(float volume) {
    if (volume < 0.0f) volume = 0.0f;
    if (volume > 1.0f) volume = 1.0f;
    volume_.store(volume, std::memory_order_relaxed);
}

float AudioOutput::getVolume() const {
    return volume_.load(std::memory_order_relaxed);
}

void AudioOutput::setBalance(float balance) {
    if (balance < -1.0f) balance = -1.0f;
    if (balance > 1.0f) balance = 1.0f;
    balance_.store(balance, std::memory_order_relaxed);
}

float AudioOutput::getBalance() const {
    return balance_.load(std::memory_order_relaxed);
}

void AudioOutput::setMono(bool mono) {
    mono_.store(mono, std::memory_order_relaxed);
}

bool AudioOutput::isMono() const {
    return mono_.load(std::memory_order_relaxed);
}

int32_t AudioOutput::getSampleRate() const {
    return currentSampleRate_;
}

uint64_t AudioOutput::getUnderrunCount() const {
    return underrunCount_.load(std::memory_order_relaxed);
}

void AudioOutput::resetPlayedFrames(int64_t startingFrame) {
    playedFrames_.store(startingFrame, std::memory_order_relaxed);
}

int64_t AudioOutput::getPlayedFrames() const {
    return playedFrames_.load(std::memory_order_relaxed);
}

// STRICT REAL-TIME CALLBACK:
// 1. No new/malloc
// 2. No mutex/lock
// 3. No log
// 4. No JNI
oboe::DataCallbackResult AudioOutput::onAudioReady(
    oboe::AudioStream * /*oboeStream*/,
    void *audioData,
    int32_t numFrames) {

    float* out = static_cast<float*>(audioData);

    const size_t framesRead = ringBuffer_->read(out, static_cast<size_t>(numFrames));

    if (framesRead < static_cast<size_t>(numFrames)) {
        underrunCount_.fetch_add(1, std::memory_order_relaxed);
    }

    // Process biquad equalizer DSP before volume and balance scaling
    equalizer_.process(out, static_cast<size_t>(numFrames));

    const bool mono = mono_.load(std::memory_order_relaxed);
    const float bal = balance_.load(std::memory_order_relaxed);
    const float vol = volume_.load(std::memory_order_relaxed);

    float leftGain = vol;
    float rightGain = vol;
    if (bal < -0.001f) {
        // Shift towards left: attenuate right channel
        rightGain *= std::max(0.0f, 1.0f + bal);
    } else if (bal > 0.001f) {
        // Shift towards right: attenuate left channel
        leftGain *= std::max(0.0f, 1.0f - bal);
    }

    if (mono || leftGain < 0.999f || leftGain > 1.001f || rightGain < 0.999f || rightGain > 1.001f) {
        for (size_t f = 0; f < static_cast<size_t>(numFrames); ++f) {
            float l = out[f * 2];
            float r = out[f * 2 + 1];
            if (mono) {
                float m = 0.5f * (l + r);
                l = m;
                r = m;
            }
            out[f * 2] = l * leftGain;
            out[f * 2 + 1] = r * rightGain;
        }
    }

    if (framesRead > 0) {
        playedFrames_.fetch_add(static_cast<int64_t>(framesRead), std::memory_order_relaxed);
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioOutput::onErrorAfterClose(oboe::AudioStream * /*oboeStream*/, oboe::Result error) {
    if (error == oboe::Result::ErrorDisconnected) {
        // Stream was disconnected (e.g. headphone unplugged)
        if (isPlaying_.load(std::memory_order_relaxed)) {
            // Re-open stream
            if (openStream(currentSampleRate_)) {
                start();
            }
        }
    }
}
