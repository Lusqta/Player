#pragma once

#include "phonon.h"
#include <atomic>
#include <array>
#include <cmath>
#include <algorithm>
#include <cstring>

class SteamSpatialPro {
public:
    enum ProPreset : int {
        PRO_PRESET_STUDIO_NX = 0,   // Nearfield fiel (estilo Waves Nx)
        PRO_PRESET_ABBEY_ROAD = 1,  // Sala grande de estúdio
        PRO_PRESET_CINEMA_360 = 2,  // Cúpula Atmos 360°
        PRO_PRESET_ARENA_3D = 3,    // Palco máximo
        PRO_PRESET_CUSTOM = 4
    };

    SteamSpatialPro() {
        initSteam(48000);
        updateCrossoverCoeffs(115.0f, 48000.0f);
        updateRoomFilters(48000.0f);
    }

    ~SteamSpatialPro() {
        releaseSteam();
    }

    void setSampleRate(int sampleRate) {
        int clampedSr = std::clamp(sampleRate, 8000, 192000);
        if (mSampleRate.load(std::memory_order_relaxed) != clampedSr) {
            initSteam(clampedSr);
            updateCrossoverCoeffs(mCurSubCutoffHz, static_cast<float>(clampedSr));
            updateRoomFilters(static_cast<float>(clampedSr));
        }
    }

    void setParams(float subCutoffHz, float midGainDb, float sideWidth,
                   float roomAmbience, float speakerAngle, float elevationNorm) {
        mTargetSubCutoffHz.store(std::clamp(subCutoffHz, 60.0f, 250.0f), std::memory_order_relaxed);
        mTargetMidGainLinear.store(std::pow(10.0f, std::clamp(midGainDb, -6.0f, 6.0f) / 20.0f), std::memory_order_relaxed);
        mTargetSideWidth.store(std::clamp(sideWidth, 0.5f, 2.2f), std::memory_order_relaxed);
        mTargetRoom.store(std::clamp(roomAmbience, 0.0f, 1.0f), std::memory_order_relaxed);
        mTargetAngle.store(std::clamp(speakerAngle, 0.0f, 1.0f), std::memory_order_relaxed);
        mTargetElevation.store(std::clamp(elevationNorm, -1.0f, 1.0f), std::memory_order_relaxed);
    }

    void requestReset() {
        mResetRequested.store(true, std::memory_order_release);
    }

    void resetState() {
        mFifoCount = 0;
        mOutFifoL.fill(0.0f);
        mOutFifoR.fill(0.0f);
        mOutReadPos = 0;
        mDelayWriteIdx = 0;
        mSurDelayL.fill(0.0f);
        mSurDelayR.fill(0.0f);
        for (int b = 0; b < 4; ++b) {
            mFdnBuf[b].fill(0.0f);
            mFdnLp[b] = 0.0f;
            mFdnHp[b] = 0.0f;
        }
        mLp1L.reset(); mLp2L.reset(); mLp1R.reset(); mLp2R.reset();
        mHp1L.reset(); mHp2L.reset(); mHp1R.reset(); mHp2R.reset();
        mApSL.reset(); mApSR.reset();
    }

    // Processa buffer float32 intercalado (L, R) com bloqueio seguro de 256 frames para Steam Audio
    void process(float* buffer, int numFrames) {
        if (!mReady.load(std::memory_order_acquire) || buffer == nullptr || numFrames <= 0) {
            return;
        }

        if (mResetRequested.exchange(false, std::memory_order_acq_rel)) {
            resetState();
        }

        for (int i = 0; i < numFrames; ++i) {
            mInFifoL[mFifoCount] = buffer[i * 2];
            mInFifoR[mFifoCount] = buffer[i * 2 + 1];

            // Entrega saída do bloco anterior (latência fixa de 256 frames ~ 5.3ms a 48kHz)
            if (mOutReadPos < kBlockSize) {
                buffer[i * 2] = mOutFifoL[mOutReadPos];
                buffer[i * 2 + 1] = mOutFifoR[mOutReadPos];
                ++mOutReadPos;
            }

            ++mFifoCount;
            if (mFifoCount == kBlockSize) {
                processBlock256();
                mFifoCount = 0;
                mOutReadPos = 0;
            }
        }
    }

private:
    static constexpr int kBlockSize = 256;
    static constexpr int kDelaySize = 4096;
    static constexpr int kDelayMask = kDelaySize - 1;
    static constexpr float kPi = 3.14159265359f;

    struct BiquadCoeffs { float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0; };
    struct BiquadState {
        float z1 = 0, z2 = 0;
        void reset() { z1 = z2 = 0; }
        inline float process(float x, const BiquadCoeffs& c) {
            float y = c.b0 * x + z1;
            z1 = c.b1 * x - c.a1 * y + z2;
            z2 = c.b2 * x - c.a2 * y;
            return y;
        }
    };
    struct AllPass1 {
        float a = 0, x1 = 0, y1 = 0;
        void reset() { x1 = y1 = 0; }
        void setFreq(float hz, float sr) {
            float t = std::tan(kPi * hz / sr);
            a = (t - 1.0f) / (t + 1.0f);
        }
        inline float process(float x) {
            float y = a * x + x1 - a * y1;
            x1 = x; y1 = y;
            return y;
        }
    };

    static inline IPLVector3 sphericalToDir(float azimuthDeg, float elevationDeg) {
        const float az = azimuthDeg * (kPi / 180.0f);
        const float el = elevationDeg * (kPi / 180.0f);
        const float cosEl = std::cos(el);
        // Sistema de coordenadas do Steam Audio: +X direita, +Y cima, -Z frente
        return IPLVector3{
            cosEl * std::sin(az),
            std::sin(el),
            -cosEl * std::cos(az)
        };
    }

    static inline float softLimit(float x) {
        const float ax = std::fabs(x);
        if (ax <= 0.92f) return x;
        const float s = (x >= 0.0f) ? 1.0f : -1.0f;
        const float o = ax - 0.92f;
        return s * (0.92f + 0.08f * (o / (o + 0.08f)));
    }

    void initSteam(int sr) {
        mReady.store(false, std::memory_order_release);
        releaseSteam();

        mSampleRate.store(sr, std::memory_order_relaxed);

        IPLContextSettings ctxSettings{};
        ctxSettings.version = STEAMAUDIO_VERSION;
        if (iplContextCreate(&ctxSettings, &mContext) != IPL_STATUS_SUCCESS) return;

        IPLAudioSettings audioSettings{};
        audioSettings.samplingRate = sr;
        audioSettings.frameSize = kBlockSize;

        IPLHRTFSettings hrtfSettings{};
        hrtfSettings.type = IPL_HRTFTYPE_DEFAULT;
        hrtfSettings.volume = 1.0f;
        hrtfSettings.normType = IPL_HRTFNORMTYPE_RMS;
        if (iplHRTFCreate(mContext, &audioSettings, &hrtfSettings, &mHrtf) != IPL_STATUS_SUCCESS) {
            releaseSteam();
            return;
        }

        IPLBinauralEffectSettings fxSettings{};
        fxSettings.hrtf = mHrtf;
        for (int s = 0; s < 5; ++s) {
            if (iplBinauralEffectCreate(mContext, &audioSettings, &fxSettings, &mSpeakerFx[s]) != IPL_STATUS_SUCCESS) {
                releaseSteam();
                return;
            }
        }

        // Buffers fixos deinterleaved para os 5 canais virtuais + saída binaural temporária
        for (int s = 0; s < 5; ++s) {
            mSpkPtrs[s] = mSpkData[s].data();
            mInBufDesc[s].numChannels = 1;
            mInBufDesc[s].numSamples = kBlockSize;
            mInBufDesc[s].data = &mSpkPtrs[s];
        }
        mTmpOutPtrs[0] = mTmpOutL.data();
        mTmpOutPtrs[1] = mTmpOutR.data();
        mOutBufDesc.numChannels = 2;
        mOutBufDesc.numSamples = kBlockSize;
        mOutBufDesc.data = mTmpOutPtrs;

        resetState();
        mReady.store(true, std::memory_order_release);
    }

    void releaseSteam() {
        for (int s = 0; s < 5; ++s) {
            if (mSpeakerFx[s]) {
                iplBinauralEffectRelease(&mSpeakerFx[s]);
                mSpeakerFx[s] = nullptr;
            }
        }
        if (mHrtf) { iplHRTFRelease(&mHrtf); mHrtf = nullptr; }
        if (mContext) { iplContextRelease(&mContext); mContext = nullptr; }
    }

    void updateCrossoverCoeffs(float cutoffHz, float sr) {
        mLastCalcCutoffHz = cutoffHz;
        const float w0 = 2.0f * kPi * std::clamp(cutoffHz, 40.0f, sr * 0.45f) / sr;
        const float c = std::cos(w0);
        const float alpha = std::sin(w0) * 0.70710678f;
        const float a0Inv = 1.0f / (1.0f + alpha);

        mLpCoeffs.b0 = (1.0f - c) * 0.5f * a0Inv;
        mLpCoeffs.b1 = (1.0f - c) * a0Inv;
        mLpCoeffs.b2 = mLpCoeffs.b0;
        mLpCoeffs.a1 = (-2.0f * c) * a0Inv;
        mLpCoeffs.a2 = (1.0f - alpha) * a0Inv;

        mHpCoeffs.b0 = (1.0f + c) * 0.5f * a0Inv;
        mHpCoeffs.b1 = -(1.0f + c) * a0Inv;
        mHpCoeffs.b2 = mHpCoeffs.b0;
        mHpCoeffs.a1 = mLpCoeffs.a1;
        mHpCoeffs.a2 = mLpCoeffs.a2;
    }

    void updateRoomFilters(float sr) {
        const float w = 2.0f * kPi / sr;
        mFdnLpAlpha = std::clamp(w * 4200.0f / (1.0f + w * 4200.0f), 0.01f, 0.99f);
        mFdnHpAlpha = std::clamp(w * 190.0f / (1.0f + w * 190.0f), 0.001f, 0.5f);
        mApSL.setFreq(390.0f, sr);
        mApSR.setFreq(530.0f, sr);
    }

    void processBlock256() {
        const float sr = static_cast<float>(mSampleRate.load(std::memory_order_relaxed));

        const float tCutoff = mTargetSubCutoffHz.load(std::memory_order_relaxed);
        const float tMid = mTargetMidGainLinear.load(std::memory_order_relaxed);
        const float tWidth = mTargetSideWidth.load(std::memory_order_relaxed);
        const float tRoom = mTargetRoom.load(std::memory_order_relaxed);
        const float tAngle = mTargetAngle.load(std::memory_order_relaxed);
        const float tElev = mTargetElevation.load(std::memory_order_relaxed);

        // Suavização por bloco (~256 samples) para zero estalos
        const float blockSmooth = 0.22f;
        mCurSubCutoffHz += (tCutoff - mCurSubCutoffHz) * blockSmooth;
        mCurMidGain += (tMid - mCurMidGain) * blockSmooth;
        mCurSideWidth += (tWidth - mCurSideWidth) * blockSmooth;
        mCurRoom += (tRoom - mCurRoom) * blockSmooth;
        mCurAngle += (tAngle - mCurAngle) * blockSmooth;
        mCurElev += (tElev - mCurElev) * blockSmooth;

        if (std::fabs(mCurSubCutoffHz - mLastCalcCutoffHz) > 0.5f) {
            updateCrossoverCoeffs(mCurSubCutoffHz, sr);
        }

        const int surDelayL = std::clamp(static_cast<int>(sr * 0.0137f), 1, kDelayMask - 1);
        const int surDelayR = std::clamp(static_cast<int>(sr * 0.0193f), 1, kDelayMask - 1);
        const int fdnDelays[4] = {
            std::clamp(static_cast<int>(sr * 0.0173f), 1, kDelayMask - 1),
            std::clamp(static_cast<int>(sr * 0.0241f), 1, kDelayMask - 1),
            std::clamp(static_cast<int>(sr * 0.0317f), 1, kDelayMask - 1),
            std::clamp(static_cast<int>(sr * 0.0419f), 1, kDelayMask - 1)
        };

        // 1. Crossover LR4 + Upmix 2.0 -> 5.0 (FL, FR, C, SL, SR)
        for (int i = 0; i < kBlockSize; ++i) {
            const float inL = mInFifoL[i];
            const float inR = mInFifoR[i];

            // Sub-grave 100% seco (mantém impacto físico)
            mDryLowL[i] = mLp2L.process(mLp1L.process(inL, mLpCoeffs), mLpCoeffs);
            mDryLowR[i] = mLp2R.process(mLp1R.process(inR, mLpCoeffs), mLpCoeffs);

            const float highL = mHp2L.process(mHp1L.process(inL, mHpCoeffs), mHpCoeffs);
            const float highR = mHp2R.process(mHp1R.process(inR, mHpCoeffs), mHpCoeffs);

            const float rawMid = 0.5f * (highL + highR);
            const float rawSide = 0.5f * (highL - highR);

            // Caixa Central (0°): vocal firme fora da cabeça
            const float centerSig = rawMid * 0.72f * mCurMidGain;

            // Caixas Frontais L/R: estéreo aberto sem duplicar energia do centro
            const float frontMid = rawMid * 0.36f;
            const float frontSide = rawSide * mCurSideWidth;
            const float flSig = frontMid + frontSide;
            const float frSig = frontMid - frontSide;

            // Caixas Surround Traseiras (SL / SR): Side descorrelacionado + Haas + All-Pass
            const float surGain = 0.35f + 0.60f * std::clamp((mCurSideWidth - 0.5f) / 1.7f, 0.0f, 1.0f);
            mSurDelayL[mDelayWriteIdx] = rawSide + 0.12f * rawMid;
            mSurDelayR[mDelayWriteIdx] = -rawSide + 0.12f * rawMid;

            float slSig = mApSL.process(mSurDelayL[(mDelayWriteIdx - surDelayL) & kDelayMask]) * surGain;
            float srSig = mApSR.process(mSurDelayR[(mDelayWriteIdx - surDelayR) & kDelayMask]) * surGain;

            // Sala Acústica FDN 4x4 alimentada nas caixas laterais e traseiras (dá realismo de sala física)
            float fdnOut[4];
            for (int b = 0; b < 4; ++b) {
                float tap = mFdnBuf[b][(mDelayWriteIdx - fdnDelays[b]) & kDelayMask];
                mFdnLp[b] += mFdnLpAlpha * (tap - mFdnLp[b]);
                mFdnHp[b] += mFdnHpAlpha * (mFdnLp[b] - mFdnHp[b]);
                fdnOut[b] = mFdnLp[b] - mFdnHp[b];
            }
            const float h0 = 0.5f * (fdnOut[0] + fdnOut[1] + fdnOut[2] + fdnOut[3]);
            const float h1 = 0.5f * (fdnOut[0] - fdnOut[1] + fdnOut[2] - fdnOut[3]);
            const float h2 = 0.5f * (fdnOut[0] + fdnOut[1] - fdnOut[2] - fdnOut[3]);
            const float h3 = 0.5f * (fdnOut[0] - fdnOut[1] - fdnOut[2] + fdnOut[3]);

            const float fb = 0.44f + 0.24f * mCurRoom; // RT60 dinâmico de estúdio seco até sala grande
            mFdnBuf[0][mDelayWriteIdx] = flSig * 0.45f + h0 * fb;
            mFdnBuf[1][mDelayWriteIdx] = frSig * 0.45f + h1 * fb;
            mFdnBuf[2][mDelayWriteIdx] = slSig * 0.50f + h2 * fb;
            mFdnBuf[3][mDelayWriteIdx] = srSig * 0.50f + h3 * fb;

            const float roomMix = mCurRoom * 0.65f;
            mSpkData[0][i] = flSig + fdnOut[0] * roomMix * 0.45f; // Front Left
            mSpkData[1][i] = frSig + fdnOut[1] * roomMix * 0.45f; // Front Right
            mSpkData[2][i] = centerSig;                           // Center (seco/focado)
            mSpkData[3][i] = slSig + fdnOut[2] * roomMix;         // Surround Left (traseira)
            mSpkData[4][i] = srSig + fdnOut[3] * roomMix;         // Surround Right (traseira)

            mDelayWriteIdx = (mDelayWriteIdx + 1) & kDelayMask;
        }

        // 2. Renderização Binaural 3D Real via Steam Audio HRTF para cada uma das 5 caixas
        const float frontAz = 15.0f + mCurAngle * 50.0f;         // 15° a 65°
        const float rearAz  = 110.0f + mCurAngle * 35.0f;        // 110° a 145°
        const float elevDeg = mCurElev * 35.0f;                  // -35° a +35°
        const float rearElevDeg = std::clamp(elevDeg + 10.0f, -35.0f, 45.0f);

        const IPLVector3 dirs[5] = {
            sphericalToDir(-frontAz, elevDeg),      // 0: Front Left
            sphericalToDir(+frontAz, elevDeg),      // 1: Front Right
            sphericalToDir(0.0f, elevDeg * 0.4f),   // 2: Center
            sphericalToDir(-rearAz, rearElevDeg),   // 3: Surround Left (trás/cima)
            sphericalToDir(+rearAz, rearElevDeg)    // 4: Surround Right (trás/cima)
        };

        std::fill(mAccumL.begin(), mAccumL.end(), 0.0f);
        std::fill(mAccumR.begin(), mAccumR.end(), 0.0f);

        for (int s = 0; s < 5; ++s) {
            IPLBinauralEffectParams params{};
            params.direction = dirs[s];
            params.interpolation = IPL_HRTFINTERPOLATION_BILINEAR;
            params.spatialBlend = 1.0f;
            params.hrtf = mHrtf;
            params.peakDelays = nullptr;

            iplBinauralEffectApply(mSpeakerFx[s], &params, &mInBufDesc[s], &mOutBufDesc);

            for (int i = 0; i < kBlockSize; ++i) {
                mAccumL[i] += mTmpOutL[i];
                mAccumR[i] += mTmpOutR[i];
            }
        }

        // 3. Recombina com Sub-Grave seco + Compensação de Loudness + Limiter
        const float gainComp = 0.88f / (1.0f + mCurRoom * 0.14f);
        for (int i = 0; i < kBlockSize; ++i) {
            mOutFifoL[i] = softLimit(mDryLowL[i] + mAccumL[i] * gainComp);
            mOutFifoR[i] = softLimit(mDryLowR[i] + mAccumR[i] * gainComp);
        }
    }

    std::atomic<bool> mReady{false};
    std::atomic<bool> mResetRequested{false};
    std::atomic<int> mSampleRate{48000};

    IPLContext mContext = nullptr;
    IPLHRTF mHrtf = nullptr;
    IPLBinauralEffect mSpeakerFx[5]{};

    std::atomic<float> mTargetSubCutoffHz{115.0f};
    std::atomic<float> mTargetMidGainLinear{1.0f};
    std::atomic<float> mTargetSideWidth{1.35f};
    std::atomic<float> mTargetRoom{0.35f};
    std::atomic<float> mTargetAngle{0.35f};
    std::atomic<float> mTargetElevation{0.0f};

    float mCurSubCutoffHz = 115.0f;
    float mLastCalcCutoffHz = -1.0f;
    float mCurMidGain = 1.0f;
    float mCurSideWidth = 1.35f;
    float mCurRoom = 0.35f;
    float mCurAngle = 0.35f;
    float mCurElev = 0.0f;

    BiquadCoeffs mLpCoeffs, mHpCoeffs;
    BiquadState mLp1L, mLp2L, mLp1R, mLp2R;
    BiquadState mHp1L, mHp2L, mHp1R, mHp2R;
    AllPass1 mApSL, mApSR;

    float mFdnLpAlpha = 0.35f, mFdnHpAlpha = 0.02f;
    float mFdnLp[4]{}, mFdnHp[4]{};

    std::array<float, kDelaySize> mSurDelayL{}, mSurDelayR{};
    std::array<std::array<float, kDelaySize>, 4> mFdnBuf{};
    int mDelayWriteIdx = 0;

    std::array<float, kBlockSize> mInFifoL{}, mInFifoR{};
    std::array<float, kBlockSize> mOutFifoL{}, mOutFifoR{};
    std::array<float, kBlockSize> mDryLowL{}, mDryLowR{};
    std::array<float, kBlockSize> mAccumL{}, mAccumR{};
    std::array<float, kBlockSize> mTmpOutL{}, mTmpOutR{};
    std::array<std::array<float, kBlockSize>, 5> mSpkData{};

    float* mSpkPtrs[5]{};
    float* mTmpOutPtrs[2]{};
    IPLAudioBuffer mInBufDesc[5]{};
    IPLAudioBuffer mOutBufDesc{};

    int mFifoCount = 0;
    int mOutReadPos = 0;
};
