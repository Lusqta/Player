#pragma once

#include <cmath>
#include <atomic>
#include <array>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

struct BiquadCoeffs {
    float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f;
    float a1 = 0.0f, a2 = 0.0f;
};

struct BiquadState {
    float x1 = 0.0f, x2 = 0.0f;
    float y1 = 0.0f, y2 = 0.0f;

    inline void reset() {
        x1 = x2 = y1 = y2 = 0.0f;
    }

    inline float process(float in, const BiquadCoeffs& c) {
        float out = c.b0 * in + c.b1 * x1 + c.b2 * x2 - c.a1 * y1 - c.a2 * y2;
        x2 = x1;
        x1 = in;
        y2 = y1;
        y1 = out;
        return out;
    }
};

/**
 * Studio-grade 10-Band Biquad Graphic Equalizer, Tone Controls (Bass/Treble),
 * Digital Preamp, and Analog-Warmth Soft Limiter (Poweramp DSP Architecture).
 */
class BiquadEqualizer {
public:
    static constexpr size_t kNumBands = 10;
    // 10 Standard ISO Octave Frequencies: 31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
    static constexpr std::array<float, kNumBands> kFrequencies = {
        31.0f, 62.0f, 125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f
    };
    static constexpr float kDefaultQ = 1.4142f; // ~1 octave bandwidth per band

    BiquadEqualizer() {
        for (size_t i = 0; i < kNumBands; ++i) {
            gainsDb_[i].store(0.0f, std::memory_order_relaxed);
            coeffs_[i] = BiquadCoeffs{1.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        }
        preampDb_.store(0.0f, std::memory_order_relaxed);
        preampLinear_.store(1.0f, std::memory_order_relaxed);

        bassGainDb_.store(0.0f, std::memory_order_relaxed);
        trebleGainDb_.store(0.0f, std::memory_order_relaxed);

        bassCoeffs_ = BiquadCoeffs{1.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        trebleCoeffs_ = BiquadCoeffs{1.0f, 0.0f, 0.0f, 0.0f, 0.0f};

        enabled_.store(false, std::memory_order_relaxed);
        toneEnabled_.store(false, std::memory_order_relaxed);
    }

    void setSampleRate(int32_t sampleRate) {
        sampleRate_ = sampleRate > 0 ? sampleRate : 44100;
        recalculateAll();
        resetStates();
    }

    void setBandGain(size_t band, float gainDb) {
        if (band < kNumBands) {
            float clamped = std::clamp(gainDb, -12.0f, 12.0f);
            gainsDb_[band].store(clamped, std::memory_order_relaxed);
            recalculateBand(band, clamped);
        }
    }

    float getBandGain(size_t band) const {
        if (band < kNumBands) {
            return gainsDb_[band].load(std::memory_order_relaxed);
        }
        return 0.0f;
    }

    void setPreamp(float gainDb) {
        float clamped = std::clamp(gainDb, -12.0f, 12.0f);
        preampDb_.store(clamped, std::memory_order_relaxed);
        preampLinear_.store(std::pow(10.0f, clamped / 20.0f), std::memory_order_relaxed);
    }

    float getPreamp() const {
        return preampDb_.load(std::memory_order_relaxed);
    }

    void setBass(float gainDb) {
        float clamped = std::clamp(gainDb, -12.0f, 12.0f);
        bassGainDb_.store(clamped, std::memory_order_relaxed);
        recalculateLowShelf(clamped);
    }

    float getBass() const {
        return bassGainDb_.load(std::memory_order_relaxed);
    }

    void setTreble(float gainDb) {
        float clamped = std::clamp(gainDb, -12.0f, 12.0f);
        trebleGainDb_.store(clamped, std::memory_order_relaxed);
        recalculateHighShelf(clamped);
    }

    float getTreble() const {
        return trebleGainDb_.load(std::memory_order_relaxed);
    }

    void setToneEnabled(bool enabled) {
        toneEnabled_.store(enabled, std::memory_order_relaxed);
        if (!enabled) {
            stateBassL_.reset();
            stateBassR_.reset();
            stateTrebleL_.reset();
            stateTrebleR_.reset();
        }
    }

    bool isToneEnabled() const {
        return toneEnabled_.load(std::memory_order_relaxed);
    }

    void setEnabled(bool enabled) {
        enabled_.store(enabled, std::memory_order_relaxed);
        if (!enabled) {
            resetStates();
        }
    }

    bool isEnabled() const {
        return enabled_.load(std::memory_order_relaxed);
    }

    void resetStates() {
        for (size_t i = 0; i < kNumBands; ++i) {
            stateL_[i].reset();
            stateR_[i].reset();
        }
        stateBassL_.reset();
        stateBassR_.reset();
        stateTrebleL_.reset();
        stateTrebleR_.reset();
    }

    // Process interleaved stereo float buffer
    inline void process(float* buffer, size_t numFrames) {
        bool eqOn = enabled_.load(std::memory_order_relaxed);
        bool toneOn = toneEnabled_.load(std::memory_order_relaxed);
        float preamp = preampLinear_.load(std::memory_order_relaxed);

        if (!eqOn && !toneOn && std::abs(preamp - 1.0f) < 0.001f) {
            return;
        }

        for (size_t f = 0; f < numFrames; ++f) {
            float l = buffer[f * 2] * preamp;
            float r = buffer[f * 2 + 1] * preamp;

            // 1. 10-Band Graphic Equalizer
            if (eqOn) {
                for (size_t b = 0; b < kNumBands; ++b) {
                    const auto& c = coeffs_[b];
                    l = stateL_[b].process(l, c);
                    r = stateR_[b].process(r, c);
                }
            }

            // 2. Tone Controls (Bass Low-Shelf & Treble High-Shelf)
            if (toneOn) {
                l = stateBassL_.process(l, bassCoeffs_);
                r = stateBassR_.process(r, bassCoeffs_);
                l = stateTrebleL_.process(l, trebleCoeffs_);
                r = stateTrebleR_.process(r, trebleCoeffs_);
            }

            // 3. Analog-warmth soft limiter (preserves dynamics, eliminates harsh clipping)
            buffer[f * 2] = softLimit(l);
            buffer[f * 2 + 1] = softLimit(r);
        }
    }

private:
    // Soft knee limiter with smooth asymptotic saturation
    static inline float softLimit(float x) {
        constexpr float kThreshold = 0.70f;
        if (x > kThreshold) {
            float excess = x - kThreshold;
            return kThreshold + excess / (1.0f + excess * excess * 1.8f);
        } else if (x < -kThreshold) {
            float excess = -x - kThreshold;
            return -(kThreshold + excess / (1.0f + excess * excess * 1.8f));
        }
        return x;
    }

    void recalculateBand(size_t band, float gainDb) {
        if (sampleRate_ <= 0) return;

        float f0 = kFrequencies[band];
        if (f0 >= sampleRate_ * 0.48f) {
            f0 = sampleRate_ * 0.48f;
        }

        if (std::abs(gainDb) < 0.01f) {
            coeffs_[band] = BiquadCoeffs{1.0f, 0.0f, 0.0f, 0.0f, 0.0f};
            return;
        }

        // Robert Bristow-Johnson Peaking EQ
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * static_cast<float>(M_PI) * f0 / static_cast<float>(sampleRate_);
        float alpha = std::sin(w0) / (2.0f * kDefaultQ);
        float cosW = std::cos(w0);

        float b0 = 1.0f + alpha * A;
        float b1 = -2.0f * cosW;
        float b2 = 1.0f - alpha * A;
        float a0 = 1.0f + alpha / A;
        float a1 = -2.0f * cosW;
        float a2 = 1.0f - alpha / A;

        BiquadCoeffs c;
        c.b0 = b0 / a0;
        c.b1 = b1 / a0;
        c.b2 = b2 / a0;
        c.a1 = a1 / a0;
        c.a2 = a2 / a0;
        coeffs_[band] = c;
    }

    void recalculateLowShelf(float gainDb) {
        if (sampleRate_ <= 0) return;
        if (std::abs(gainDb) < 0.01f) {
            bassCoeffs_ = BiquadCoeffs{1.0f, 0.0f, 0.0f, 0.0f, 0.0f};
            return;
        }

        constexpr float f0 = 90.0f; // Bass center
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * static_cast<float>(M_PI) * f0 / static_cast<float>(sampleRate_);
        float cosW = std::cos(w0);
        float sinW = std::sin(w0);
        float alpha = (sinW / 2.0f) * std::sqrt(2.0f);
        float sqrtA = std::sqrt(A);

        float b0 =    A * ((A + 1.0f) - (A - 1.0f) * cosW + 2.0f * sqrtA * alpha);
        float b1 = 2.0f * A * ((A - 1.0f) - (A + 1.0f) * cosW);
        float b2 =    A * ((A + 1.0f) - (A - 1.0f) * cosW - 2.0f * sqrtA * alpha);
        float a0 =         (A + 1.0f) + (A - 1.0f) * cosW + 2.0f * sqrtA * alpha;
        float a1 = -2.0f * ((A - 1.0f) + (A + 1.0f) * cosW);
        float a2 =         (A + 1.0f) + (A - 1.0f) * cosW - 2.0f * sqrtA * alpha;

        bassCoeffs_ = BiquadCoeffs{b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0};
    }

    void recalculateHighShelf(float gainDb) {
        if (sampleRate_ <= 0) return;
        if (std::abs(gainDb) < 0.01f) {
            trebleCoeffs_ = BiquadCoeffs{1.0f, 0.0f, 0.0f, 0.0f, 0.0f};
            return;
        }

        float f0 = std::min(10000.0f, sampleRate_ * 0.45f); // Treble center
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * static_cast<float>(M_PI) * f0 / static_cast<float>(sampleRate_);
        float cosW = std::cos(w0);
        float sinW = std::sin(w0);
        float alpha = (sinW / 2.0f) * std::sqrt(2.0f);
        float sqrtA = std::sqrt(A);

        float b0 =    A * ((A + 1.0f) + (A - 1.0f) * cosW + 2.0f * sqrtA * alpha);
        float b1 = -2.0f * A * ((A - 1.0f) + (A + 1.0f) * cosW);
        float b2 =    A * ((A + 1.0f) + (A - 1.0f) * cosW - 2.0f * sqrtA * alpha);
        float a0 =         (A + 1.0f) - (A - 1.0f) * cosW + 2.0f * sqrtA * alpha;
        float a1 =  2.0f * ((A - 1.0f) - (A + 1.0f) * cosW);
        float a2 =         (A + 1.0f) - (A - 1.0f) * cosW - 2.0f * sqrtA * alpha;

        trebleCoeffs_ = BiquadCoeffs{b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0};
    }

    void recalculateAll() {
        for (size_t i = 0; i < kNumBands; ++i) {
            recalculateBand(i, gainsDb_[i].load(std::memory_order_relaxed));
        }
        recalculateLowShelf(bassGainDb_.load(std::memory_order_relaxed));
        recalculateHighShelf(trebleGainDb_.load(std::memory_order_relaxed));
    }

    int32_t sampleRate_ = 44100;
    std::atomic<bool> enabled_{false};
    std::atomic<bool> toneEnabled_{false};

    std::array<std::atomic<float>, kNumBands> gainsDb_;
    std::array<BiquadCoeffs, kNumBands> coeffs_;
    std::array<BiquadState, kNumBands> stateL_;
    std::array<BiquadState, kNumBands> stateR_;

    std::atomic<float> preampDb_{0.0f};
    std::atomic<float> preampLinear_{1.0f};

    std::atomic<float> bassGainDb_{0.0f};
    std::atomic<float> trebleGainDb_{0.0f};
    BiquadCoeffs bassCoeffs_;
    BiquadCoeffs trebleCoeffs_;
    BiquadState stateBassL_, stateBassR_;
    BiquadState stateTrebleL_, stateTrebleR_;
};
