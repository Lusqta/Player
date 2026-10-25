#include "audio_engine.h"
#include "decoders/decoder_factory.h"
#include <chrono>

AudioEngine::AudioEngine()
    : ringBuffer_(131072),
      audioOutput_(std::make_unique<AudioOutput>(&ringBuffer_)) {
    isRunning_.store(true, std::memory_order_relaxed);
    decodeThread_ = std::thread(&AudioEngine::decodeLoop, this);
}

AudioEngine::~AudioEngine() {
    stop();
    isRunning_.store(false, std::memory_order_relaxed);
    wakeDecoderThread();
    if (decodeThread_.joinable()) {
        decodeThread_.join();
    }
    audioOutput_->closeStream();
}

void AudioEngine::wakeDecoderThread() {
    std::unique_lock<std::mutex> lock(decodeMutex_);
    decodeCv_.notify_all();
}

void AudioEngine::stopInternal() {
    state_.store(PlaybackState::Stopped, std::memory_order_release);
    audioOutput_->stop();
    ringBuffer_.reset();
    audioOutput_->resetPlayedFrames(0);

    if (decoder_) {
        decoder_->seek(0);
    }
    isEos_.store(false, std::memory_order_relaxed);
    wakeDecoderThread();
}

bool AudioEngine::openFile(int fd) {
    std::lock_guard<std::recursive_mutex> lock(controlMutex_);

    // Stop current playback safely without deadlock
    stopInternal();

    auto newDecoder = DecoderFactory::createDecoder(fd);
    if (!newDecoder) {
        return false;
    }

    decoder_ = std::move(newDecoder);

    const int32_t sampleRate = decoder_->getSampleRate();
    if (!audioOutput_->openStream(sampleRate)) {
        decoder_.reset();
        return false;
    }

    ringBuffer_.reset();
    audioOutput_->resetPlayedFrames(0);
    isEos_.store(false, std::memory_order_relaxed);
    state_.store(PlaybackState::Stopped, std::memory_order_release);

    return true;
}

bool AudioEngine::play() {
    std::lock_guard<std::recursive_mutex> lock(controlMutex_);
    if (!decoder_) return false;

    PlaybackState current = state_.load(std::memory_order_acquire);
    if (current == PlaybackState::Playing) return true;

    // If stream was closed or at EOF, seek to start
    if (isEos_.load(std::memory_order_relaxed)) {
        decoder_->seek(0);
        ringBuffer_.reset();
        audioOutput_->resetPlayedFrames(0);
        isEos_.store(false, std::memory_order_relaxed);
    }

    // Prefill some frames into the buffer before starting stream to prevent initial underrun
    constexpr size_t kPrefillChunks = 8;
    constexpr size_t kChunkFrames = 2048;
    std::vector<float> prefill(kChunkFrames * 2);
    for (size_t i = 0; i < kPrefillChunks; ++i) {
        if (ringBuffer_.availableWrite() < kChunkFrames) break;
        size_t r = decoder_->read(prefill.data(), kChunkFrames);
        if (r == 0) {
            isEos_.store(true, std::memory_order_relaxed);
            break;
        }
        ringBuffer_.write(prefill.data(), r);
    }

    if (!audioOutput_->start()) {
        return false;
    }

    state_.store(PlaybackState::Playing, std::memory_order_release);
    wakeDecoderThread();
    return true;
}

bool AudioEngine::pause() {
    std::lock_guard<std::recursive_mutex> lock(controlMutex_);
    PlaybackState current = state_.load(std::memory_order_acquire);
    if (current != PlaybackState::Playing) return true;

    state_.store(PlaybackState::Paused, std::memory_order_release);
    audioOutput_->pause();
    wakeDecoderThread();
    return true;
}

bool AudioEngine::stop() {
    std::lock_guard<std::recursive_mutex> lock(controlMutex_);
    stopInternal();
    return true;
}

bool AudioEngine::seekTo(int64_t frameIndex) {
    std::lock_guard<std::recursive_mutex> lock(controlMutex_);
    if (!decoder_) return false;

    int64_t total = decoder_->getTotalFrames();
    if (frameIndex < 0) frameIndex = 0;
    if (total > 0 && frameIndex > total) frameIndex = total;

    seekTargetFrame_.store(frameIndex, std::memory_order_relaxed);
    seekRequested_.store(true, std::memory_order_release);
    wakeDecoderThread();
    return true;
}

void AudioEngine::setVolume(float volume) {
    audioOutput_->setVolume(volume);
}

float AudioEngine::getVolume() const {
    return audioOutput_->getVolume();
}

int64_t AudioEngine::getPositionFrames() const {
    PlaybackState s = state_.load(std::memory_order_acquire);
    if (s == PlaybackState::Stopped) return 0;
    int64_t pos = audioOutput_->getPlayedFrames();
    int64_t total = getDurationFrames();
    if (total > 0 && pos > total) pos = total;
    return pos;
}

int64_t AudioEngine::getDurationFrames() const {
    if (!decoder_) return 0;
    return decoder_->getTotalFrames();
}

int32_t AudioEngine::getSampleRate() const {
    if (!decoder_) return 0;
    return decoder_->getSampleRate();
}

int32_t AudioEngine::getChannelCount() const {
    if (!decoder_) return 0;
    return decoder_->getChannelCount();
}

bool AudioEngine::isPlaying() const {
    return state_.load(std::memory_order_acquire) == PlaybackState::Playing;
}

bool AudioEngine::isPaused() const {
    return state_.load(std::memory_order_acquire) == PlaybackState::Paused;
}

void AudioEngine::decodeLoop() {
    constexpr size_t kChunkFrames = 2048;
    std::vector<float> chunk(kChunkFrames * 2);

    while (isRunning_.load(std::memory_order_relaxed)) {
        // Handle pending seek request
        if (seekRequested_.load(std::memory_order_acquire)) {
            int64_t target = seekTargetFrame_.load(std::memory_order_relaxed);
            if (decoder_) {
                decoder_->seek(target);
            }
            ringBuffer_.reset();
            audioOutput_->resetPlayedFrames(target);
            isEos_.store(false, std::memory_order_relaxed);
            seekRequested_.store(false, std::memory_order_release);
        }

        PlaybackState currentState = state_.load(std::memory_order_acquire);

        // When paused or stopped: sleep on condition variable (0% CPU)
        if (currentState != PlaybackState::Playing) {
            std::unique_lock<std::mutex> lock(decodeMutex_);
            decodeCv_.wait(lock, [this]() {
                return !isRunning_.load(std::memory_order_relaxed) ||
                       seekRequested_.load(std::memory_order_relaxed) ||
                       state_.load(std::memory_order_relaxed) == PlaybackState::Playing;
            });
            continue;
        }

        // When reached End of Stream (EOF)
        if (isEos_.load(std::memory_order_relaxed)) {
            if (ringBuffer_.availableRead() == 0) {
                // All audio in ring buffer has been played out by Oboe callback
                state_.store(PlaybackState::Stopped, std::memory_order_release);
                audioOutput_->stop();
                ringBuffer_.reset();
                audioOutput_->resetPlayedFrames(0);
                if (decoder_) {
                    decoder_->seek(0);
                }
                isEos_.store(false, std::memory_order_relaxed);
            } else {
                // Wait for the consumer to finish playing
                std::unique_lock<std::mutex> lock(decodeMutex_);
                decodeCv_.wait_for(lock, std::chrono::milliseconds(50), [this]() {
                    return !isRunning_.load(std::memory_order_relaxed) ||
                           seekRequested_.load(std::memory_order_relaxed) ||
                           state_.load(std::memory_order_relaxed) != PlaybackState::Playing;
                });
            }
            continue;
        }

        // If ring buffer is nearly full, sleep briefly to avoid burning CPU
        if (ringBuffer_.availableWrite() < kChunkFrames) {
            std::unique_lock<std::mutex> lock(decodeMutex_);
            decodeCv_.wait_for(lock, std::chrono::milliseconds(20), [this]() {
                return !isRunning_.load(std::memory_order_relaxed) ||
                       seekRequested_.load(std::memory_order_relaxed) ||
                       state_.load(std::memory_order_relaxed) != PlaybackState::Playing ||
                       ringBuffer_.availableWrite() >= kChunkFrames;
            });
            continue;
        }

        if (!decoder_) {
            state_.store(PlaybackState::Stopped, std::memory_order_release);
            continue;
        }

        size_t framesRead = decoder_->read(chunk.data(), kChunkFrames);
        if (framesRead == 0) {
            isEos_.store(true, std::memory_order_relaxed);
            continue;
        }

        ringBuffer_.write(chunk.data(), framesRead);
    }
}

void AudioEngine::setEqualizerBand(size_t band, float gainDb) {
    if (audioOutput_) audioOutput_->setEqualizerBand(band, gainDb);
}

float AudioEngine::getEqualizerBand(size_t band) const {
    return audioOutput_ ? audioOutput_->getEqualizerBand(band) : 0.0f;
}

void AudioEngine::setEqualizerEnabled(bool enabled) {
    if (audioOutput_) audioOutput_->setEqualizerEnabled(enabled);
}

bool AudioEngine::isEqualizerEnabled() const {
    return audioOutput_ ? audioOutput_->isEqualizerEnabled() : false;
}

void AudioEngine::setPreamp(float gainDb) {
    if (audioOutput_) audioOutput_->setPreamp(gainDb);
}

float AudioEngine::getPreamp() const {
    return audioOutput_ ? audioOutput_->getPreamp() : 0.0f;
}

void AudioEngine::setBass(float gainDb) {
    if (audioOutput_) audioOutput_->setBass(gainDb);
}

float AudioEngine::getBass() const {
    return audioOutput_ ? audioOutput_->getBass() : 0.0f;
}

void AudioEngine::setTreble(float gainDb) {
    if (audioOutput_) audioOutput_->setTreble(gainDb);
}

float AudioEngine::getTreble() const {
    return audioOutput_ ? audioOutput_->getTreble() : 0.0f;
}

void AudioEngine::setToneEnabled(bool enabled) {
    if (audioOutput_) audioOutput_->setToneEnabled(enabled);
}

bool AudioEngine::isToneEnabled() const {
    return audioOutput_ ? audioOutput_->isToneEnabled() : false;
}

void AudioEngine::setBalance(float balance) {
    if (audioOutput_) audioOutput_->setBalance(balance);
}

float AudioEngine::getBalance() const {
    return audioOutput_ ? audioOutput_->getBalance() : 0.0f;
}

void AudioEngine::setMono(bool mono) {
    if (audioOutput_) audioOutput_->setMono(mono);
}

bool AudioEngine::isMono() const {
    return audioOutput_ ? audioOutput_->isMono() : false;
}

std::vector<float> AudioEngine::scanWaveform(int fd, int numPoints) {
    if (fd < 0 || numPoints <= 0) return {};
    auto decoder = DecoderFactory::createDecoder(fd);
    if (!decoder) return {};

    std::vector<float> peaks(numPoints, 0.05f);
    int64_t totalFrames = decoder->getTotalFrames();

    constexpr size_t kChunkSize = 2048;
    std::vector<float> buffer(kChunkSize * 2);

    if (totalFrames > 0) {
        int64_t framesPerPoint = totalFrames / numPoints;
        if (framesPerPoint <= 0) framesPerPoint = 1;

        for (int p = 0; p < numPoints; ++p) {
            int64_t targetFrame = p * framesPerPoint;
            decoder->seek(targetFrame);

            size_t framesToRead = std::min(static_cast<size_t>(framesPerPoint), kChunkSize);
            size_t read = decoder->read(buffer.data(), framesToRead);
            float maxPeak = 0.05f;
            for (size_t i = 0; i < read * 2; ++i) {
                float val = std::abs(buffer[i]);
                if (val > maxPeak) maxPeak = val;
            }
            peaks[p] = std::min(maxPeak, 1.0f);
        }
    } else {
        std::vector<float> allPeaks;
        size_t read = 0;
        while ((read = decoder->read(buffer.data(), kChunkSize)) > 0) {
            float maxPeak = 0.05f;
            for (size_t i = 0; i < read * 2; ++i) {
                float val = std::abs(buffer[i]);
                if (val > maxPeak) maxPeak = val;
            }
            allPeaks.push_back(std::min(maxPeak, 1.0f));
            if (allPeaks.size() > 5000) break;
        }
        if (!allPeaks.empty()) {
            for (int p = 0; p < numPoints; ++p) {
                size_t idx = (p * allPeaks.size()) / numPoints;
                peaks[p] = allPeaks[idx];
            }
        }
    }

    return peaks;
}
