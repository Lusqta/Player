#pragma once

#include "decoder.h"
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <vector>
#include <unistd.h>

class AmediaDecoder : public IDecoder {
public:
    AmediaDecoder();
    ~AmediaDecoder() override;

    bool open(int fd) override;
    size_t read(float* outBuffer, size_t framesToRead) override;
    bool seek(int64_t frameIndex) override;

    int32_t getSampleRate() const override { return sampleRate_; }
    int32_t getChannelCount() const override { return 2; }
    int64_t getTotalFrames() const override { return totalFrames_; }
    int64_t getCurrentFrame() const override { return currentFrame_; }

private:
    void close();

    int fd_ = -1;
    AMediaExtractor* extractor_ = nullptr;
    AMediaCodec* codec_ = nullptr;
    AMediaFormat* format_ = nullptr;

    int32_t sampleRate_ = 0;
    int32_t channels_ = 0;
    int32_t pcmEncoding_ = 2; // ENCODING_PCM_16BIT = 2, ENCODING_PCM_FLOAT = 4
    int64_t durationUs_ = 0;
    int64_t totalFrames_ = 0;
    int64_t currentFrame_ = 0;
    bool isEos_ = false;
    bool isOpened_ = false;

    std::vector<float> leftoverBuffer_;
    size_t leftoverOffset_ = 0;
};
