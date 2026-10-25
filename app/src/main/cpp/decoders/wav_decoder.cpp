#define DR_WAV_IMPLEMENTATION
#include "wav_decoder.h"
#include <unistd.h>
#include <algorithm>

static size_t onWavReadFd(void* pUserData, void* pBufferOut, size_t bytesToRead) {
    int fd = *reinterpret_cast<int*>(pUserData);
    size_t total = 0;
    while (total < bytesToRead) {
        ssize_t n = ::read(fd, static_cast<char*>(pBufferOut) + total, bytesToRead - total);
        if (n <= 0) break;
        total += static_cast<size_t>(n);
    }
    return total;
}

static drwav_bool32 onWavSeekFd(void* pUserData, int offset, drwav_seek_origin origin) {
    int fd = *reinterpret_cast<int*>(pUserData);
    int whence = SEEK_SET;
    if (origin == DRWAV_SEEK_CUR) whence = SEEK_CUR;
    else if (origin == DRWAV_SEEK_END) whence = SEEK_END;
    off_t res = ::lseek(fd, offset, whence);
    return (res != (off_t)-1) ? DRWAV_TRUE : DRWAV_FALSE;
}

static drwav_bool32 onWavTellFd(void* pUserData, drwav_int64* pCursor) {
    int fd = *reinterpret_cast<int*>(pUserData);
    off_t res = ::lseek(fd, 0, SEEK_CUR);
    if (res == (off_t)-1) return DRWAV_FALSE;
    *pCursor = res;
    return DRWAV_TRUE;
}

WavDecoder::WavDecoder() {
    wav_ = {};
}

WavDecoder::~WavDecoder() {
    close();
}

void WavDecoder::close() {
    if (isOpened_) {
        drwav_uninit(&wav_);
        isOpened_ = false;
    }
    if (fd_ >= 0) {
        ::close(fd_);
        fd_ = -1;
    }
    currentFrame_ = 0;
}

bool WavDecoder::open(int fd) {
    close();
    if (fd < 0) return false;

    fd_ = ::dup(fd);
    if (fd_ < 0) return false;

    ::lseek(fd_, 0, SEEK_SET);

    if (!drwav_init(&wav_, onWavReadFd, onWavSeekFd, onWavTellFd, &fd_, nullptr)) {
        close();
        return false;
    }

    isOpened_ = true;
    currentFrame_ = 0;
    return true;
}

size_t WavDecoder::read(float* outBuffer, size_t framesToRead) {
    if (!isOpened_ || framesToRead == 0) return 0;

    drwav_uint64 framesRead = drwav_read_pcm_frames_f32(&wav_, framesToRead, outBuffer);
    if (framesRead == 0) return 0;

    if (wav_.channels == 1) {
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

bool WavDecoder::seek(int64_t frameIndex) {
    if (!isOpened_) return false;
    if (frameIndex < 0) frameIndex = 0;
    if (frameIndex > static_cast<int64_t>(wav_.totalPCMFrameCount)) {
        frameIndex = static_cast<int64_t>(wav_.totalPCMFrameCount);
    }

    drwav_bool32 ok = drwav_seek_to_pcm_frame(&wav_, static_cast<drwav_uint64>(frameIndex));
    if (ok == DRWAV_TRUE) {
        currentFrame_ = frameIndex;
        return true;
    }
    return false;
}

int32_t WavDecoder::getSampleRate() const {
    return isOpened_ ? static_cast<int32_t>(wav_.sampleRate) : 0;
}

int32_t WavDecoder::getChannelCount() const {
    return isOpened_ ? static_cast<int32_t>(wav_.channels) : 0;
}

int64_t WavDecoder::getTotalFrames() const {
    return isOpened_ ? static_cast<int64_t>(wav_.totalPCMFrameCount) : 0;
}

int64_t WavDecoder::getCurrentFrame() const {
    return currentFrame_;
}
