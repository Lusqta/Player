#pragma once

#include <atomic>
#include <array>
#include <cmath>
#include <algorithm>
#include <cstring>
#include <memory>
#include "jamesdsp/jdsp_shim.h"
#include "steam_spatial_pro.h"

class SpatialProcessor {
public:
    enum Mode : int {
        MODE_OFF = 0,
        MODE_DOLBY_STANDARD = 1,
        MODE_SPATIAL_PRO = 2
    };

    enum StandardPreset : int {
        PRESET_MUSIC = 0,
        PRESET_CINEMA = 1,
        PRESET_STAGE = 2
    };

    SpatialProcessor() {
        resetState();
        updateStdCrossoverCoeffs(110.0f, 48000.0f);
        initJdsp(48000.0f);
    }

    void setSampleRate(int sampleRate) {
        int clampedSr = std::clamp(sampleRate, 8000, 192000);
        if (mSampleRate.exchange(clampedSr) != clampedSr) {
            resetState();
            const float sr = static_cast<float>(clampedSr);
            updateStdCrossoverCoeffs(110.0f, sr);
            initJdsp(sr);
            mSteamPro.setSampleRate(clampedSr);
        }
    }

    void setMode(int mode) {
        int clamped = std::clamp(mode, 0, 2);
        mMode.store(clamped, std::memory_order_relaxed);
        if (clamped == MODE_SPATIAL_PRO) {
            mSteamPro.requestReset();
        }
    }

    void setStandardPreset(int preset, float intensity) {
        const int p = std::clamp(preset, 0, 2);
        mPreset.store(p, std::memory_order_relaxed);
        mStandardIntensity.store(std::clamp(intensity, 0.0f, 1.0f), std::memory_order_relaxed);
    }

    void setProParams(float subCutoffHz, float midGainDb, float sideWidth,
                      float surroundAmbience, float speakerAngle, float elevationNorm = 0.0f) {
        mSteamPro.setParams(subCutoffHz, midGainDb, sideWidth, surroundAmbience, speakerAngle, elevationNorm);
    }

    void resetState() {
        mStdLp1L.reset(); mStdLp2L.reset();
        mStdLp1R.reset(); mStdLp2R.reset();
        mStdHp1L.reset(); mStdHp2L.reset();
        mStdHp1R.reset(); mStdHp2R.reset();
        mSteamPro.requestReset();
    }

    // Processa buffer float32 intercalado (L, R, L, R...)
    void process(float* buffer, int numFrames, int channels = 2) {
        const int mode = mMode.load(std::memory_order_relaxed);
        if (mode == MODE_OFF || channels != 2 || buffer == nullptr || numFrames <= 0) {
            return;
        }

        // Modo 1: Motor JamesDSP oficial (Stereo Widen + Progenitor 2 Reverb)
        if (mode == MODE_DOLBY_STANDARD) {
            processDolbyStandard(buffer, numFrames);
            return;
        }

        // Modo 2: Motor Steam Audio Binaural 3D Real (libphonon.so)
        if (mode == MODE_SPATIAL_PRO) {
            mSteamPro.process(buffer, numFrames);
            return;
        }
    }

private:
    static constexpr int kMaxBlockFrames = 2048;

    struct BiquadCoeffs {
        float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f;
        float a1 = 0.0f, a2 = 0.0f;
    };

    struct BiquadState {
        float z1 = 0.0f, z2 = 0.0f;
        void reset() { z1 = z2 = 0.0f; }
        inline float process(float x, const BiquadCoeffs& c) {
            float y = c.b0 * x + z1;
            z1 = c.b1 * x - c.a1 * y + z2;
            z2 = c.b2 * x - c.a2 * y;
            return y;
        }
    };

    static void computeLR4Coeffs(float cutoffHz, float sr, BiquadCoeffs& lp, BiquadCoeffs& hp) {
        const float w0 = 6.28318530718f * std::clamp(cutoffHz, 40.0f, sr * 0.45f) / sr;
        const float cosW0 = std::cos(w0);
        const float sinW0 = std::sin(w0);
        const float alpha = sinW0 * 0.70710678f;
        const float a0Inv = 1.0f / (1.0f + alpha);

        // Low-Pass Butterworth 2nd Order
        const float lpB1 = 1.0f - cosW0;
        const float lpB0 = lpB1 * 0.5f;
        lp.b0 = lpB0 * a0Inv;
        lp.b1 = lpB1 * a0Inv;
        lp.b2 = lp.b0;
        lp.a1 = (-2.0f * cosW0) * a0Inv;
        lp.a2 = (1.0f - alpha) * a0Inv;

        // High-Pass Butterworth 2nd Order (Invertido para soma LR4 em fase 0dB flat)
        const float hpB1 = -(1.0f + cosW0);
        const float hpB0 = (1.0f + cosW0) * 0.5f;
        hp.b0 = hpB0 * a0Inv;
        hp.b1 = hpB1 * a0Inv;
        hp.b2 = hp.b0;
        hp.a1 = lp.a1;
        hp.a2 = lp.a2;
    }

    void updateStdCrossoverCoeffs(float cutoffHz, float sr) {
        mStdLastCalcCutoffHz = cutoffHz;
        computeLR4Coeffs(cutoffHz, sr, mStdLpCoeffs, mStdHpCoeffs);
    }

    void initJdsp(float sampleRate) {
        if (!mJdspLib) {
            mJdspLib = std::make_unique<JamesDSPLib>();
        }
        std::memset(mJdspLib.get(), 0, sizeof(JamesDSPLib));
        mJdspLib->fs = sampleRate;
        mJdspLib->tmpBuffer[0] = mJdspBlockL.data();
        mJdspLib->tmpBuffer[1] = mJdspBlockR.data();

        // Inicializa Stereo Enhancement (Stereo Widen)
        StereoEnhancementConstructor(mJdspLib.get());
        StereoEnhancementRefresh(mJdspLib.get());
        StereoEnhancementEnable(mJdspLib.get());

        mCurrentJdspPreset = -1;
        applyJdspPreset(mPreset.load(std::memory_order_relaxed));
        mJdspInitialized = true;
    }

    void applyJdspPreset(int preset) {
        if (!mJdspLib) return;
        const int sr = mSampleRate.load(std::memory_order_relaxed);

        float widenMix = 0.40f;
        float rt60 = 0.6f;
        float delay = 0.012f;
        float ertolate = 0.40f;
        float erefwet = -14.0f;
        float wet = -13.0f;
        float ereffactor = 0.9f;
        float erefwidth = 0.6f;
        float width = 0.85f;
        float wander = 0.20f;
        float bassb = 0.0f;
        float spin = 0.7f;
        float inputlpf = 18000.0f;
        float basslpf = 240.0f;
        float damplpf = 7500.0f;
        float outputlpf = 14000.0f;

        if (preset == PRESET_CINEMA) {
            widenMix = 0.70f;
            rt60 = 1.1f;
            delay = 0.020f;
            ertolate = 0.35f;
            erefwet = -11.0f;
            wet = -10.0f;
            ereffactor = 1.2f;
            erefwidth = 0.75f;
            width = 0.95f;
            wander = 0.25f;
            bassb = 0.15f;
            spin = 0.8f;
            inputlpf = 18000.0f;
            basslpf = 220.0f;
            damplpf = 6500.0f;
            outputlpf = 15000.0f;
        } else if (preset == PRESET_STAGE) {
            widenMix = 0.90f;
            rt60 = 1.5f;
            delay = 0.028f;
            ertolate = 0.30f;
            erefwet = -9.0f;
            wet = -8.0f;
            ereffactor = 1.4f;
            erefwidth = 0.85f;
            width = 1.0f;
            wander = 0.30f;
            bassb = 0.20f;
            spin = 1.0f;
            inputlpf = 18000.0f;
            basslpf = 200.0f;
            damplpf = 6000.0f;
            outputlpf = 16000.0f;
        }

        StereoEnhancementSetParam(mJdspLib.get(), widenMix);
        sf_advancereverb(&mJdspLib->reverb, sr, 1, ertolate, erefwet, 0.0f,
                         ereffactor, erefwidth, width, wet, wander, bassb,
                         spin, inputlpf, basslpf, damplpf, outputlpf, rt60, delay);
        mCurrentJdspPreset = preset;
    }

    void processDolbyStandard(float* buffer, int numFrames) {
        if (!mJdspLib || !mJdspInitialized) {
            return;
        }

        const float sr = static_cast<float>(mSampleRate.load(std::memory_order_relaxed));
        const int targetPreset = mPreset.load(std::memory_order_relaxed);
        if (targetPreset != mCurrentJdspPreset) {
            applyJdspPreset(targetPreset);
        }

        if (std::fabs(110.0f - mStdLastCalcCutoffHz) > 0.5f) {
            updateStdCrossoverCoeffs(110.0f, sr);
        }

        const float wetMix = mStandardIntensity.load(std::memory_order_relaxed);

        int framesRemaining = numFrames;
        int frameOffset = 0;

        while (framesRemaining > 0) {
            const int blockSize = std::min(framesRemaining, kMaxBlockFrames);

            // 1. Isola o sub-grave abaixo de 110 Hz usando o crossover LR4 (100% seco)
            for (int j = 0; j < blockSize; ++j) {
                const float inL = buffer[(frameOffset + j) * 2];
                const float inR = buffer[(frameOffset + j) * 2 + 1];

                const float lowL = mStdLp2L.process(mStdLp1L.process(inL, mStdLpCoeffs), mStdLpCoeffs);
                const float lowR = mStdLp2R.process(mStdLp1R.process(inR, mStdLpCoeffs), mStdLpCoeffs);
                const float highL = mStdHp2L.process(mStdHp1L.process(inL, mStdHpCoeffs), mStdHpCoeffs);
                const float highR = mStdHp2R.process(mStdHp1R.process(inR, mStdHpCoeffs), mStdHpCoeffs);

                mBlockLowL[j] = lowL;
                mBlockLowR[j] = lowR;
                mBlockDryHighL[j] = highL;
                mBlockDryHighR[j] = highR;

                mJdspBlockL[j] = highL;
                mJdspBlockR[j] = highR;
            }

            // 2. Stereo Widen oficial do JamesDSP nos médios/agudos
            StereoEnhancementProcess(mJdspLib.get(), static_cast<size_t>(blockSize));

            // 3. Progenitor 2 Reverb oficial do JamesDSP
            for (int j = 0; j < blockSize; ++j) {
                float outL, outR;
                sf_reverb_process(&mJdspLib->reverb, mJdspBlockL[j], mJdspBlockR[j], &outL, &outR);
                mJdspBlockL[j] = outL;
                mJdspBlockR[j] = outR;
            }

            // 4. Recombina: out = low + dryHigh * (1 - wetMix) + processedHigh * wetMix
            // 5. Soft-Limiter para impedir clipping
            for (int j = 0; j < blockSize; ++j) {
                const float lowL = mBlockLowL[j];
                const float lowR = mBlockLowR[j];
                const float dryHighL = mBlockDryHighL[j];
                const float dryHighR = mBlockDryHighR[j];
                const float procL = mJdspBlockL[j];
                const float procR = mJdspBlockR[j];

                const float outL = lowL + dryHighL * (1.0f - wetMix) + procL * wetMix;
                const float outR = lowR + dryHighR * (1.0f - wetMix) + procR * wetMix;

                buffer[(frameOffset + j) * 2]     = softLimit(outL);
                buffer[(frameOffset + j) * 2 + 1] = softLimit(outR);
            }

            frameOffset += blockSize;
            framesRemaining -= blockSize;
        }
    }

    static inline float softLimit(float x) {
        const float absX = std::fabs(x);
        if (absX <= 0.92f) return x;
        const float sign = (x >= 0.0f) ? 1.0f : -1.0f;
        const float over = absX - 0.92f;
        return sign * (0.92f + 0.08f * (over / (over + 0.08f)));
    }

    std::atomic<int> mSampleRate{48000};
    std::atomic<int> mMode{MODE_OFF};
    std::atomic<int> mPreset{PRESET_MUSIC};
    std::atomic<float> mStandardIntensity{0.75f};

    // Filtros Linkwitz-Riley LR4 fixos em 110Hz para Dolby Standard
    BiquadCoeffs mStdLpCoeffs, mStdHpCoeffs;
    BiquadState mStdLp1L, mStdLp2L, mStdLp1R, mStdLp2R;
    BiquadState mStdHp1L, mStdHp2L, mStdHp1R, mStdHp2R;
    float mStdLastCalcCutoffHz = -1.0f;

    // JamesDSP Stereo Enhancement & Progenitor 2 Reverb (Modo 1)
    std::unique_ptr<JamesDSPLib> mJdspLib;
    bool mJdspInitialized = false;
    int mCurrentJdspPreset = -1;

    // Buffers pré-alocados para processamento sem malloc no áudio thread
    alignas(16) std::array<float, kMaxBlockFrames> mJdspBlockL{};
    alignas(16) std::array<float, kMaxBlockFrames> mJdspBlockR{};
    alignas(16) std::array<float, kMaxBlockFrames> mBlockLowL{};
    alignas(16) std::array<float, kMaxBlockFrames> mBlockLowR{};
    alignas(16) std::array<float, kMaxBlockFrames> mBlockDryHighL{};
    alignas(16) std::array<float, kMaxBlockFrames> mBlockDryHighR{};

    // Motor Steam Audio (Modo 2)
    SteamSpatialPro mSteamPro;
};
