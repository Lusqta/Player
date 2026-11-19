#pragma once

#include <atomic>
#include <array>
#include <cmath>
#include <algorithm>
#include <cstring>

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
        updateStaticFilters(48000.0f);
        updateCrossoverCoeffs(120.0f, 48000.0f);
        updateInternalTargets();
    }

    void setSampleRate(int sampleRate) {
        int clampedSr = std::clamp(sampleRate, 8000, 192000);
        if (mSampleRate.exchange(clampedSr) != clampedSr) {
            resetState();
            updateStaticFilters(static_cast<float>(clampedSr));
            updateCrossoverCoeffs(mCurSubCutoffHz, static_cast<float>(clampedSr));
        }
    }

    void setMode(int mode) {
        mMode.store(std::clamp(mode, 0, 2), std::memory_order_relaxed);
        updateInternalTargets();
    }

    void setStandardPreset(int preset, float intensity) {
        mPreset.store(std::clamp(preset, 0, 2), std::memory_order_relaxed);
        mStandardIntensity.store(std::clamp(intensity, 0.0f, 1.0f), std::memory_order_relaxed);
        updateInternalTargets();
    }

    void setProParams(float subCutoffHz, float midGainDb, float sideWidth, float surroundAmbience, float speakerAngle) {
        mProSubCutoffHz.store(std::clamp(subCutoffHz, 60.0f, 250.0f), std::memory_order_relaxed);
        mProMidGainDb.store(std::clamp(midGainDb, -6.0f, 6.0f), std::memory_order_relaxed);
        mProSideWidth.store(std::clamp(sideWidth, 0.5f, 2.2f), std::memory_order_relaxed);
        mProSurround.store(std::clamp(surroundAmbience, 0.0f, 1.0f), std::memory_order_relaxed);
        mProAngle.store(std::clamp(speakerAngle, 0.0f, 1.0f), std::memory_order_relaxed);
        updateInternalTargets();
    }

    void resetState() {
        mErBufferL.fill(0.0f);
        mErBufferR.fill(0.0f);
        mCrossBufferL.fill(0.0f);
        mCrossBufferR.fill(0.0f);
        for (int b = 0; b < 4; ++b) {
            mFdnBuffer[b].fill(0.0f);
            mFdnLpState[b] = 0.0f;
            mFdnHpState[b] = 0.0f;
        }
        mWriteIdx = 0;

        mLp1L.reset(); mLp2L.reset();
        mLp1R.reset(); mLp2R.reset();
        mHp1L.reset(); mHp2L.reset();
        mHp1R.reset(); mHp2R.reset();

        mPinnaPeakL.reset(); mPinnaNotchL.reset();
        mPinnaPeakR.reset(); mPinnaNotchR.reset();

        mHeadShadowL = mHeadShadowR = 0.0f;
        mTorsoLpL = mTorsoLpR = 0.0f;
        mApDiffL.reset();
        mApDiffR.reset();
    }

    // Processa buffer float32 intercalado (L, R, L, R...)
    void process(float* buffer, int numFrames, int channels = 2) {
        const int mode = mMode.load(std::memory_order_relaxed);
        if (mode == MODE_OFF || channels != 2 || buffer == nullptr || numFrames <= 0) {
            return;
        }

        const float sr = static_cast<float>(mSampleRate.load(std::memory_order_relaxed));

        // Offsets acústicos para Early Reflections de sala de controle (em samples)
        const int erTap1 = std::clamp(static_cast<int>(sr * 0.0068f), 1, kBufferMask - 1); // 6.8ms parede lateral
        const int erTap2 = std::clamp(static_cast<int>(sr * 0.0119f), 1, kBufferMask - 1); // 11.9ms parede oposta
        const int erTap3 = std::clamp(static_cast<int>(sr * 0.0174f), 1, kBufferMask - 1); // 17.4ms teto/piso
        const int erTap4 = std::clamp(static_cast<int>(sr * 0.0231f), 1, kBufferMask - 1); // 23.1ms parede traseira
        const int torsoDelay = std::clamp(static_cast<int>(sr * 0.00115f), 1, kBufferMask - 1); // 1.15ms reflexão de ombro

        // Comprimentos mutuamente primos da matriz FDN 4x4 (sala acústica tratada)
        const int fdnDelays[4] = {
            std::clamp(static_cast<int>(sr * 0.0197f), 1, kBufferMask - 1),
            std::clamp(static_cast<int>(sr * 0.0273f), 1, kBufferMask - 1),
            std::clamp(static_cast<int>(sr * 0.0359f), 1, kBufferMask - 1),
            std::clamp(static_cast<int>(sr * 0.0461f), 1, kBufferMask - 1)
        };

        const float targetCutoff = mTargetSubCutoffHz.load(std::memory_order_relaxed);
        const float targetMidGain = mTargetMidGainLinear.load(std::memory_order_relaxed);
        const float targetSideWidth = mTargetSideWidth.load(std::memory_order_relaxed);
        const float targetSurround = mTargetSurround.load(std::memory_order_relaxed);
        const float targetAngle = mTargetAngle.load(std::memory_order_relaxed);

        const float smooth = 0.0025f;

        for (int i = 0; i < numFrames; ++i) {
            mCurSubCutoffHz += (targetCutoff - mCurSubCutoffHz) * smooth;
            mCurMidGain += (targetMidGain - mCurMidGain) * smooth;
            mCurSideWidth += (targetSideWidth - mCurSideWidth) * smooth;
            mCurSurround += (targetSurround - mCurSurround) * smooth;
            mCurAngle += (targetAngle - mCurAngle) * smooth;

            // Recalcula coeficientes do crossover LR4 apenas quando a frequência muda
            if (std::fabs(mCurSubCutoffHz - mLastCalcCutoffHz) > 0.2f) {
                updateCrossoverCoeffs(mCurSubCutoffHz, sr);
            }

            const float inL = buffer[i * 2];
            const float inR = buffer[i * 2 + 1];

            // 1. Crossover Linkwitz-Riley de 4ª Ordem (24 dB/oitava, fase coerente)
            const float lowL = mLp2L.process(mLp1L.process(inL, mLpCoeffs), mLpCoeffs);
            const float lowR = mLp2R.process(mLp1R.process(inR, mLpCoeffs), mLpCoeffs);
            const float highL = mHp2L.process(mHp1L.process(inL, mHpCoeffs), mHpCoeffs);
            const float highR = mHp2R.process(mHp1R.process(inR, mHpCoeffs), mHpCoeffs);

            // 2. Matriz Mid / Side com projeção de caixa central e abertura lateral
            const float rawMid = 0.5f * (highL + highR);
            const float rawSide = 0.5f * (highL - highR);

            const float mid = rawMid * mCurMidGain;
            const float side = rawSide * mCurSideWidth;

            // Sinais das caixas virtuais Front Left e Front Right
            const float spkL = mid + side;
            const float spkR = mid - side;

            // 3. Pinna HRTF (Projeção Frontal + Contorno da Concha Auricular)
            // Aplica pico frontal (3.2kHz) + notch anatômico da orelha externa (8.2kHz)
            // escalado pelo ângulo binaural para externalizar o som fora da cabeça
            const float pinnaMix = 0.35f + 0.55f * mCurAngle;
            const float hrtfL = mPinnaNotchL.process(mPinnaPeakL.process(spkL, mPinnaPeakCoeffs), mPinnaNotchCoeffs);
            const float hrtfR = mPinnaNotchR.process(mPinnaPeakR.process(spkR, mPinnaPeakCoeffs), mPinnaNotchCoeffs);

            const float directL = spkL * (1.0f - pinnaMix) + hrtfL * pinnaMix;
            const float directR = spkR * (1.0f - pinnaMix) + hrtfR * pinnaMix;

            // 4. Modelo Esférico Craniano de Woodworth (ITD + Head Shadow ILD + Reflexão de Torso)
            // Mapeia slider [0..1] para ângulo físico [15°..60°] (0.2618 a 1.0472 rad)
            const float theta = 0.2618f + mCurAngle * 0.7854f;
            // Equação de Woodworth: ITD = (r / c) * (theta + sin(theta)), r=0.0875m, c=343m/s
            const float woodworthSec = 0.0002551f * (theta + theta * (1.0f - theta * theta * 0.1666f));
            const int itdSamples = std::clamp(static_cast<int>(woodworthSec * sr), 1, kBufferMask - 1);

            // Sombra acústica da cabeça (Low-Pass dependente do ângulo: 950Hz a 550Hz)
            mHeadShadowL += mHeadShadowAlpha * (directL - mHeadShadowL);
            mHeadShadowR += mHeadShadowAlpha * (directR - mHeadShadowR);

            mCrossBufferL[mWriteIdx] = mHeadShadowL;
            mCrossBufferR[mWriteIdx] = mHeadShadowR;

            const int readItd = (mWriteIdx - itdSamples) & kBufferMask;
            const int readTorso = (mWriteIdx - torsoDelay) & kBufferMask;

            // Reflexão de ombro/torso (1.15ms) + Crossfeed Contralateral
            mTorsoLpL += mTorsoAlpha * (mCrossBufferL[readTorso] - mTorsoLpL);
            mTorsoLpR += mTorsoAlpha * (mCrossBufferR[readTorso] - mTorsoLpR);

            const float crossGain = 0.14f + 0.16f * mCurAngle;
            const float contralateralL = mCrossBufferR[readItd] * crossGain + mTorsoLpL * 0.08f;
            const float contralateralR = mCrossBufferL[readItd] * crossGain + mTorsoLpR * 0.08f;

            // 5. Acústica de Sala Realista: Early Reflections + Matriz FDN Hadamard 4x4
            const float roomFeedL = rawSide + 0.22f * rawMid;
            const float roomFeedR = -rawSide + 0.22f * rawMid;

            mErBufferL[mWriteIdx] = roomFeedL;
            mErBufferR[mWriteIdx] = roomFeedR;

            // Reflexões primárias cruzadas (paredes laterais e teto de estúdio)
            const float er1L = mErBufferL[(mWriteIdx - erTap1) & kBufferMask];
            const float er1R = mErBufferR[(mWriteIdx - erTap1) & kBufferMask];
            const float er2L = mErBufferR[(mWriteIdx - erTap2) & kBufferMask] * 0.78f;
            const float er2R = mErBufferL[(mWriteIdx - erTap2) & kBufferMask] * 0.78f;
            const float er3L = mErBufferL[(mWriteIdx - erTap3) & kBufferMask] * 0.62f;
            const float er3R = mErBufferR[(mWriteIdx - erTap3) & kBufferMask] * 0.62f;
            const float er4L = mErBufferR[(mWriteIdx - erTap4) & kBufferMask] * 0.50f;
            const float er4R = mErBufferL[(mWriteIdx - erTap4) & kBufferMask] * 0.50f;

            const float earlyL = (er1L + er2L + er3L + er4L) * 0.35f;
            const float earlyR = (er1R + er2R + er3R + er4R) * 0.35f;

            // Leitura das 4 linhas da FDN
            float fdnOut[4];
            for (int b = 0; b < 4; ++b) {
                const int rIdx = (mWriteIdx - fdnDelays[b]) & kBufferMask;
                float rawTap = mFdnBuffer[b][rIdx];
                // Absorção do ar (Low-Pass 3.8kHz) + Corte de lama (High-Pass 170Hz)
                mFdnLpState[b] += mFdnLpAlpha * (rawTap - mFdnLpState[b]);
                mFdnHpState[b] += mFdnHpAlpha * (mFdnLpState[b] - mFdnHpState[b]);
                fdnOut[b] = mFdnLpState[b] - mFdnHpState[b];
            }

            // Matriz Ortogonal de Hadamard 4x4 (difusão máxima sem coloração metálica)
            const float h0 = 0.5f * (fdnOut[0] + fdnOut[1] + fdnOut[2] + fdnOut[3]);
            const float h1 = 0.5f * (fdnOut[0] - fdnOut[1] + fdnOut[2] - fdnOut[3]);
            const float h2 = 0.5f * (fdnOut[0] + fdnOut[1] - fdnOut[2] - fdnOut[3]);
            const float h3 = 0.5f * (fdnOut[0] - fdnOut[1] - fdnOut[2] + fdnOut[3]);

            // Feedback curto de sala de controle tratada (T60 ~ 220ms, g = 0.36)
            const float fbGain = 0.36f;
            mFdnBuffer[0][mWriteIdx] = er1L * 0.5f + h0 * fbGain;
            mFdnBuffer[1][mWriteIdx] = er1R * 0.5f + h1 * fbGain;
            mFdnBuffer[2][mWriteIdx] = er2L * 0.5f + h2 * fbGain;
            mFdnBuffer[3][mWriteIdx] = er2R * 0.5f + h3 * fbGain;

            const float lateL = mApDiffL.process((fdnOut[0] + fdnOut[2]) * 0.5f);
            const float lateR = mApDiffR.process((fdnOut[1] + fdnOut[3]) * 0.5f);

            // Combina Reflexões Primárias (70% da percepção espacial) + Cauda FDN (30%)
            const float roomOutL = (earlyL * 0.72f + lateL * 0.45f) * mCurSurround;
            const float roomOutR = (earlyR * 0.72f + lateR * 0.45f) * mCurSurround;

            mWriteIdx = (mWriteIdx + 1) & kBufferMask;

            // 6. Recombinação + Compensação Automática de Volume Percebido
            const float energyComp = 1.0f / (1.0f + mCurSurround * 0.16f + mCurAngle * 0.09f + std::max(0.0f, mCurSideWidth - 1.0f) * 0.08f);

            const float outL = lowL + (directL + contralateralL + roomOutL) * energyComp;
            const float outR = lowR + (directR + contralateralR + roomOutR) * energyComp;

            // 7. Soft-Limiter Racional de Estúdio
            buffer[i * 2] = softLimit(outL);
            buffer[i * 2 + 1] = softLimit(outR);
        }
    }

private:
    static constexpr int kBufferSize = 4096;
    static constexpr int kBufferMask = kBufferSize - 1;

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

    struct FirstOrderAllPass {
        float a = 0.0f, x1 = 0.0f, y1 = 0.0f;
        void reset() { x1 = y1 = 0.0f; }
        void setFreq(float freqHz, float sr) {
            float t = std::tan(3.14159265359f * freqHz / sr);
            a = (t - 1.0f) / (t + 1.0f);
        }
        inline float process(float x) {
            float y = a * x + x1 - a * y1;
            x1 = x; y1 = y;
            return y;
        }
    };

    void updateCrossoverCoeffs(float cutoffHz, float sr) {
        mLastCalcCutoffHz = cutoffHz;
        const float w0 = 6.28318530718f * std::clamp(cutoffHz, 40.0f, sr * 0.45f) / sr;
        const float cosW0 = std::cos(w0);
        const float sinW0 = std::sin(w0);
        // Butterworth Q = 1 / sqrt(2) -> 2 em cascata formam Linkwitz-Riley 4ª Ordem (LR4)
        const float alpha = sinW0 * 0.70710678f;
        const float a0Inv = 1.0f / (1.0f + alpha);

        // Low-Pass Butterworth 2nd Order
        const float lpB1 = 1.0f - cosW0;
        const float lpB0 = lpB1 * 0.5f;
        mLpCoeffs.b0 = lpB0 * a0Inv;
        mLpCoeffs.b1 = lpB1 * a0Inv;
        mLpCoeffs.b2 = mLpCoeffs.b0;
        mLpCoeffs.a1 = (-2.0f * cosW0) * a0Inv;
        mLpCoeffs.a2 = (1.0f - alpha) * a0Inv;

        // High-Pass Butterworth 2nd Order (Invertido para soma LR4 em fase 0dB flat)
        const float hpB1 = -(1.0f + cosW0);
        const float hpB0 = (1.0f + cosW0) * 0.5f;
        mHpCoeffs.b0 = hpB0 * a0Inv;
        mHpCoeffs.b1 = hpB1 * a0Inv;
        mHpCoeffs.b2 = mHpCoeffs.b0;
        mHpCoeffs.a1 = mLpCoeffs.a1;
        mHpCoeffs.a2 = mLpCoeffs.a2;
    }

    static BiquadCoeffs makePeakingEq(float freqHz, float q, float gainDb, float sr) {
        BiquadCoeffs c;
        const float A = std::pow(10.0f, gainDb / 40.0f);
        const float w0 = 6.28318530718f * std::clamp(freqHz, 20.0f, sr * 0.45f) / sr;
        const float cosW0 = std::cos(w0);
        const float alpha = std::sin(w0) / (2.0f * q);
        const float a0Inv = 1.0f / (1.0f + alpha / A);

        c.b0 = (1.0f + alpha * A) * a0Inv;
        c.b1 = (-2.0f * cosW0) * a0Inv;
        c.b2 = (1.0f - alpha * A) * a0Inv;
        c.a1 = c.b1;
        c.a2 = (1.0f - alpha / A) * a0Inv;
        return c;
    }

    void updateStaticFilters(float sr) {
        const float twoPiOverSr = 6.28318530718f / sr;
        // Pinna Frontal Cue (+2.2dB em 3.2kHz) + Concha Notch (-3.4dB em 8.2kHz)
        mPinnaPeakCoeffs = makePeakingEq(3200.0f, 1.25f, 2.2f, sr);
        mPinnaNotchCoeffs = makePeakingEq(8200.0f, 2.40f, -3.4f, sr);

        mHeadShadowAlpha = std::clamp(twoPiOverSr * 680.0f / (1.0f + twoPiOverSr * 680.0f), 0.005f, 0.99f);
        mTorsoAlpha = std::clamp(twoPiOverSr * 1100.0f / (1.0f + twoPiOverSr * 1100.0f), 0.005f, 0.99f);
        mFdnLpAlpha = std::clamp(twoPiOverSr * 3800.0f / (1.0f + twoPiOverSr * 3800.0f), 0.005f, 0.99f);
        mFdnHpAlpha = std::clamp(twoPiOverSr * 170.0f / (1.0f + twoPiOverSr * 170.0f), 0.001f, 0.50f);

        mApDiffL.setFreq(370.0f, sr);
        mApDiffR.setFreq(510.0f, sr);
    }

    static inline float softLimit(float x) {
        const float absX = std::fabs(x);
        if (absX <= 0.92f) return x;
        const float sign = (x >= 0.0f) ? 1.0f : -1.0f;
        const float over = absX - 0.92f;
        return sign * (0.92f + 0.08f * (over / (over + 0.08f)));
    }

    static inline float dbToLinear(float db) {
        return std::pow(10.0f, db / 20.0f);
    }

    void updateInternalTargets() {
        const int mode = mMode.load(std::memory_order_relaxed);
        if (mode == MODE_DOLBY_STANDARD) {
            const int preset = mPreset.load(std::memory_order_relaxed);
            const float k = mStandardIntensity.load(std::memory_order_relaxed);

            float cutoff = 120.0f;
            float midDb = 0.6f;
            float width = 1.32f;
            float sur = 0.35f;
            float angle = 0.40f;

            if (preset == PRESET_CINEMA) {
                cutoff = 135.0f;
                midDb = 1.6f;
                width = 1.60f;
                sur = 0.65f;
                angle = 0.55f;
            } else if (preset == PRESET_STAGE) {
                cutoff = 105.0f;
                midDb = -0.3f;
                width = 1.80f;
                sur = 0.78f;
                angle = 0.70f;
            }

            mTargetSubCutoffHz.store(cutoff, std::memory_order_relaxed);
            mTargetMidGainLinear.store(dbToLinear(midDb * k), std::memory_order_relaxed);
            mTargetSideWidth.store(1.0f + (width - 1.0f) * k, std::memory_order_relaxed);
            mTargetSurround.store(sur * k, std::memory_order_relaxed);
            mTargetAngle.store(angle * k, std::memory_order_relaxed);
        } else {
            mTargetSubCutoffHz.store(mProSubCutoffHz.load(std::memory_order_relaxed), std::memory_order_relaxed);
            mTargetMidGainLinear.store(dbToLinear(mProMidGainDb.load(std::memory_order_relaxed)), std::memory_order_relaxed);
            mTargetSideWidth.store(mProSideWidth.load(std::memory_order_relaxed), std::memory_order_relaxed);
            mTargetSurround.store(mProSurround.load(std::memory_order_relaxed), std::memory_order_relaxed);
            mTargetAngle.store(mProAngle.load(std::memory_order_relaxed), std::memory_order_relaxed);
        }
    }

    std::atomic<int> mSampleRate{48000};
    std::atomic<int> mMode{MODE_OFF};
    std::atomic<int> mPreset{PRESET_MUSIC};
    std::atomic<float> mStandardIntensity{0.75f};

    std::atomic<float> mProSubCutoffHz{120.0f};
    std::atomic<float> mProMidGainDb{0.0f};
    std::atomic<float> mProSideWidth{1.3f};
    std::atomic<float> mProSurround{0.35f};
    std::atomic<float> mProAngle{0.4f};

    std::atomic<float> mTargetSubCutoffHz{120.0f};
    std::atomic<float> mTargetMidGainLinear{1.0f};
    std::atomic<float> mTargetSideWidth{1.3f};
    std::atomic<float> mTargetSurround{0.35f};
    std::atomic<float> mTargetAngle{0.4f};

    float mCurSubCutoffHz = 120.0f;
    float mLastCalcCutoffHz = -1.0f;
    float mCurMidGain = 1.0f;
    float mCurSideWidth = 1.0f;
    float mCurSurround = 0.0f;
    float mCurAngle = 0.0f;

    BiquadCoeffs mLpCoeffs, mHpCoeffs;
    BiquadState mLp1L, mLp2L, mLp1R, mLp2R;
    BiquadState mHp1L, mHp2L, mHp1R, mHp2R;

    BiquadCoeffs mPinnaPeakCoeffs, mPinnaNotchCoeffs;
    BiquadState mPinnaPeakL, mPinnaNotchL, mPinnaPeakR, mPinnaNotchR;

    float mHeadShadowAlpha = 0.08f, mTorsoAlpha = 0.12f;
    float mHeadShadowL = 0.0f, mHeadShadowR = 0.0f;
    float mTorsoLpL = 0.0f, mTorsoLpR = 0.0f;

    float mFdnLpAlpha = 0.35f, mFdnHpAlpha = 0.02f;
    float mFdnLpState[4]{};
    float mFdnHpState[4]{};

    FirstOrderAllPass mApDiffL, mApDiffR;

    std::array<float, kBufferSize> mErBufferL{};
    std::array<float, kBufferSize> mErBufferR{};
    std::array<float, kBufferSize> mCrossBufferL{};
    std::array<float, kBufferSize> mCrossBufferR{};
    std::array<std::array<float, kBufferSize>, 4> mFdnBuffer{};
    int mWriteIdx = 0;
};
