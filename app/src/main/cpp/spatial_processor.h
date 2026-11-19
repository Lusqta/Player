#pragma once

#include <atomic>
#include <array>
#include <cmath>
#include <algorithm>
#include <cstring>
#include <memory>
#include "jamesdsp/jdsp_shim.h"

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
        updateStdCrossoverCoeffs(110.0f, 48000.0f);
        updateInternalTargets();
        initJdsp(48000.0f);
    }

    void setSampleRate(int sampleRate) {
        int clampedSr = std::clamp(sampleRate, 8000, 192000);
        if (mSampleRate.exchange(clampedSr) != clampedSr) {
            resetState();
            const float sr = static_cast<float>(clampedSr);
            updateStaticFilters(sr);
            updateCrossoverCoeffs(mCurSubCutoffHz, sr);
            updateStdCrossoverCoeffs(110.0f, sr);
            initJdsp(sr);
        }
    }

    void setMode(int mode) {
        int clamped = std::clamp(mode, 0, 2);
        mMode.store(clamped, std::memory_order_relaxed);
        updateInternalTargets();
        if (clamped == MODE_DOLBY_STANDARD) {
            applyJdspPreset(mPreset.load(std::memory_order_relaxed));
        } else if (clamped == MODE_SPATIAL_PRO) {
            mCurSubCutoffHz = mTargetSubCutoffHz.load(std::memory_order_relaxed);
            mCurMidGain = mTargetMidGainLinear.load(std::memory_order_relaxed);
            mCurSideWidth = mTargetSideWidth.load(std::memory_order_relaxed);
            mCurSurround = mTargetSurround.load(std::memory_order_relaxed);
            mCurAngle = mTargetAngle.load(std::memory_order_relaxed);
            updateCrossoverCoeffs(mCurSubCutoffHz, static_cast<float>(mSampleRate.load(std::memory_order_relaxed)));
        }
    }

    void setStandardPreset(int preset, float intensity) {
        const int p = std::clamp(preset, 0, 2);
        mPreset.store(p, std::memory_order_relaxed);
        mStandardIntensity.store(std::clamp(intensity, 0.0f, 1.0f), std::memory_order_relaxed);
        applyJdspPreset(p);
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

        mStdLp1L.reset(); mStdLp2L.reset();
        mStdLp1R.reset(); mStdLp2R.reset();
        mStdHp1L.reset(); mStdHp2L.reset();
        mStdHp1R.reset(); mStdHp2R.reset();

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

        // Modo 1: Motor JamesDSP oficial (Stereo Widen + Progenitor 2 Reverb)
        if (mode == MODE_DOLBY_STANDARD) {
            processDolbyStandard(buffer, numFrames);
            return;
        }

        // Modo 2: Spatial Pro Binaural 3D (preservado inalterado)
        processSpatialPro(buffer, numFrames);
    }

private:
    static constexpr int kBufferSize = 4096;
    static constexpr int kBufferMask = kBufferSize - 1;
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

    void updateCrossoverCoeffs(float cutoffHz, float sr) {
        mLastCalcCutoffHz = cutoffHz;
        computeLR4Coeffs(cutoffHz, sr, mLpCoeffs, mHpCoeffs);
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

    void processSpatialPro(float* buffer, int numFrames) {
        const float sr = static_cast<float>(mSampleRate.load(std::memory_order_relaxed));

        // Offsets acústicos para Early Reflections de sala de controle (em samples)
        const int erTap1 = std::clamp(static_cast<int>(sr * 0.0068f), 1, kBufferMask - 1);
        const int erTap2 = std::clamp(static_cast<int>(sr * 0.0119f), 1, kBufferMask - 1);
        const int erTap3 = std::clamp(static_cast<int>(sr * 0.0174f), 1, kBufferMask - 1);
        const int erTap4 = std::clamp(static_cast<int>(sr * 0.0231f), 1, kBufferMask - 1);
        const int torsoDelay = std::clamp(static_cast<int>(sr * 0.00115f), 1, kBufferMask - 1);

        // Comprimentos mutuamente primos da matriz FDN 4x4
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

        if (std::fabs(targetCutoff - mLastCalcCutoffHz) > 0.5f) {
            updateCrossoverCoeffs(targetCutoff, sr);
        }

        const float smooth = 0.005f;

        for (int i = 0; i < numFrames; ++i) {
            mCurSubCutoffHz += (targetCutoff - mCurSubCutoffHz) * smooth;
            mCurMidGain += (targetMidGain - mCurMidGain) * smooth;
            mCurSideWidth += (targetSideWidth - mCurSideWidth) * smooth;
            mCurSurround += (targetSurround - mCurSurround) * smooth;
            mCurAngle += (targetAngle - mCurAngle) * smooth;

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
            const float pinnaMix = 0.35f + 0.55f * mCurAngle;
            const float hrtfL = mPinnaNotchL.process(mPinnaPeakL.process(spkL, mPinnaPeakCoeffs), mPinnaNotchCoeffs);
            const float hrtfR = mPinnaNotchR.process(mPinnaPeakR.process(spkR, mPinnaPeakCoeffs), mPinnaNotchCoeffs);

            const float directL = spkL * (1.0f - pinnaMix) + hrtfL * pinnaMix;
            const float directR = spkR * (1.0f - pinnaMix) + hrtfR * pinnaMix;

            // 4. Modelo Esférico Craniano de Woodworth (ITD + Head Shadow ILD + Reflexão de Torso)
            const float theta = 0.2618f + mCurAngle * 0.7854f;
            const float woodworthSec = 0.0002551f * (theta + theta * (1.0f - theta * theta * 0.1666f));
            const int itdSamples = std::clamp(static_cast<int>(woodworthSec * sr), 1, kBufferMask - 1);

            // Sombra acústica da cabeça
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

            float fdnOut[4];
            for (int b = 0; b < 4; ++b) {
                const int rIdx = (mWriteIdx - fdnDelays[b]) & kBufferMask;
                float rawTap = mFdnBuffer[b][rIdx];
                mFdnLpState[b] += mFdnLpAlpha * (rawTap - mFdnLpState[b]);
                mFdnHpState[b] += mFdnHpAlpha * (mFdnLpState[b] - mFdnHpState[b]);
                fdnOut[b] = mFdnLpState[b] - mFdnHpState[b];
            }

            const float h0 = 0.5f * (fdnOut[0] + fdnOut[1] + fdnOut[2] + fdnOut[3]);
            const float h1 = 0.5f * (fdnOut[0] - fdnOut[1] + fdnOut[2] - fdnOut[3]);
            const float h2 = 0.5f * (fdnOut[0] + fdnOut[1] - fdnOut[2] - fdnOut[3]);
            const float h3 = 0.5f * (fdnOut[0] - fdnOut[1] - fdnOut[2] + fdnOut[3]);

            const float fbGain = 0.36f;
            mFdnBuffer[0][mWriteIdx] = er1L * 0.5f + h0 * fbGain;
            mFdnBuffer[1][mWriteIdx] = er1R * 0.5f + h1 * fbGain;
            mFdnBuffer[2][mWriteIdx] = er2L * 0.5f + h2 * fbGain;
            mFdnBuffer[3][mWriteIdx] = er2R * 0.5f + h3 * fbGain;

            const float lateL = mApDiffL.process((fdnOut[0] + fdnOut[2]) * 0.5f);
            const float lateR = mApDiffR.process((fdnOut[1] + fdnOut[3]) * 0.5f);

            const float roomOutL = (earlyL * 0.72f + lateL * 0.45f) * mCurSurround;
            const float roomOutR = (earlyR * 0.72f + lateR * 0.45f) * mCurSurround;

            mWriteIdx = (mWriteIdx + 1) & kBufferMask;

            // 6. Recombinação com preservação da energia direta
            const float directScale = 1.0f / (1.0f + mCurSurround * 0.05f);
            const float outL = lowL + directL * directScale + (contralateralL + roomOutL);
            const float outR = lowR + directR * directScale + (contralateralR + roomOutR);

            // 7. Soft-Limiter Racional de Estúdio
            buffer[i * 2] = softLimit(outL);
            buffer[i * 2 + 1] = softLimit(outR);
        }
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
        mTargetSubCutoffHz.store(mProSubCutoffHz.load(std::memory_order_relaxed), std::memory_order_relaxed);
        mTargetMidGainLinear.store(dbToLinear(mProMidGainDb.load(std::memory_order_relaxed)), std::memory_order_relaxed);
        mTargetSideWidth.store(mProSideWidth.load(std::memory_order_relaxed), std::memory_order_relaxed);
        mTargetSurround.store(mProSurround.load(std::memory_order_relaxed), std::memory_order_relaxed);
        mTargetAngle.store(mProAngle.load(std::memory_order_relaxed), std::memory_order_relaxed);
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

    // Filtros Linkwitz-Riley LR4 para Spatial Pro
    BiquadCoeffs mLpCoeffs, mHpCoeffs;
    BiquadState mLp1L, mLp2L, mLp1R, mLp2R;
    BiquadState mHp1L, mHp2L, mHp1R, mHp2R;

    // Filtros Linkwitz-Riley LR4 fixos em 110Hz para Dolby Standard
    BiquadCoeffs mStdLpCoeffs, mStdHpCoeffs;
    BiquadState mStdLp1L, mStdLp2L, mStdLp1R, mStdLp2R;
    BiquadState mStdHp1L, mStdHp2L, mStdHp1R, mStdHp2R;
    float mStdLastCalcCutoffHz = -1.0f;

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
};
