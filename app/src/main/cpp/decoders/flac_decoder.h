#pragma once

#include "decoder.h"
#include "dr_flac.h"
#include <unistd.h>

class FlacDecoder : public IDecoder {
public:
    FlacDecoder();
    ~FlacDecoder() override;

    bool open(int fd) override;
    size_t read(float* outBuffer, size_t framesToRead) override;
    bool seek(int64_t frameIndex) override;

    int32_t getSampleRate() const override;
    int32_t getChannelCount() const override;
    int64_t getTotalFrames() const override;
    int64_t getCurrentFrame() const override;

private:
    void close();

    int fd_ = -1;
    drflac* flac_ = nullptr;
    bool isOpened_ = false;
    int64_t currentFrame_ = 0;
};
