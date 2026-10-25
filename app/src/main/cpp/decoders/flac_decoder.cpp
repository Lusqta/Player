#define DR_FLAC_IMPLEMENTATION
#include "flac_decoder.h"
#include <unistd.h>
#include <algorithm>

static size_t onFlacReadFd(void* pUserData, void* pBufferOut, size_t bytesToRead) {
    int fd = *reinterpret_cast<int*>(pUserData);
    size_t total = 0;
    while (total < bytesToRead) {
        ssize_t n = ::read(fd, static_cast<char*>(pBufferOut) + total, bytesToRead - total);
        if (n <= 0) break;
        total += static_cast<size_t>(n);
    }
    return total;
}

static drflac_bool32 onFlacSeekFd(void* pUserData, int offset, drflac_seek_origin origin) {
    int fd = *reinterpret_cast<int*>(pUserData);
    int whence = SEEK_SET;
    if (origin == DRFLAC_SEEK_CUR) whence = SEEK_CUR;
    else if (origin == DRFLAC_SEEK_END) whence = SEEK_END;
    off_t res = ::lseek(fd, offset, whence);
    return (res != (off_t)-1) ? DRFLAC_TRUE : DRFLAC_FALSE;
}

static drflac_bool32 onFlacTellFd(void* pUserData, drflac_int64* pCursor) {
    int fd = *reinterpret_cast<int*>(pUserData);
    off_t res = ::lseek(fd, 0, SEEK_CUR);
    if (res == (off_t)-1) return DRFLAC_FALSE;
    *pCursor = res;
    return DRFLAC_TRUE;
}

FlacDecoder::FlacDecoder() = default;

FlacDecoder::~FlacDecoder() {
    close();
}

void FlacDecoder::close() {
    if (flac_) {
        drflac_close(flac_);
        flac_ = nullptr;
    }
    if (fd_ >= 0) {
        ::close(fd_);
        fd_ = -1;
    }
    isOpened_ = false;
    currentFrame_ = 0;
}

bool FlacDecoder::open(int fd) {
    close();
    if (fd < 0) return false;

    fd_ = ::dup(fd);
    if (fd_ < 0) return false;

    ::lseek(fd_, 0, SEEK_SET);

    flac_ = drflac_open(onFlacReadFd, onFlacSeekFd, onFlacTellFd, &fd_, nullptr);
    if (!flac_) {
        close();
        return false;
    }

    isOpened_ = true;
    currentFrame_ = 0;
    return true;
}

size_t FlacDecoder::read(float* outBuffer, size_t framesToRead) {
    if (!isOpened_ || !flac_ || framesToRead == 0) return 0;

    drflac_uint64 framesRead = drflac_read_pcm_frames_f32(flac_, framesToRead, outBuffer);
    if (framesRead == 0) return 0;

    if (flac_->channels == 1) {
        // Expand mono to interleaved stereo in-place from back to front
        for (int64_t i = static_cast<int64_t>(framesRead) - 1; i >= 0; --i) {
            float sample = outBuffer[i];
            outBuffer[i * 2] = sample;
            outBuffer[i * 2 + 1] = sample;
        }
    }

    currentFrame_ += static_cast<int64_t>(framesRead);
    return static_cast<size_t>(framesRead);
}

bool FlacDecoder::seek(int64_t frameIndex) {
    if (!isOpened_ || !flac_) return false;
    if (frameIndex < 0) frameIndex = 0;
    if (frameIndex > static_cast<int64_t>(flac_->totalPCMFrameCount)) {
        frameIndex = static_cast<int64_t>(flac_->totalPCMFrameCount);
    }

    drflac_bool32 ok = drflac_seek_to_pcm_frame(flac_, static_cast<drflac_uint64>(frameIndex));
    if (ok == DRFLAC_TRUE) {
        currentFrame_ = frameIndex;
        return true;
    }
    return false;
}

int32_t FlacDecoder::getSampleRate() const {
    return (isOpened_ && flac_) ? static_cast<int32_t>(flac_->sampleRate) : 0;
}

int32_t FlacDecoder::getChannelCount() const {
    return (isOpened_ && flac_) ? static_cast<int32_t>(flac_->channels) : 0;
}

int64_t FlacDecoder::getTotalFrames() const {
    return (isOpened_ && flac_) ? static_cast<int64_t>(flac_->totalPCMFrameCount) : 0;
}

int64_t FlacDecoder::getCurrentFrame() const {
    return currentFrame_;
}
