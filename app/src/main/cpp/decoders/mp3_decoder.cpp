#define DR_MP3_IMPLEMENTATION
#include "mp3_decoder.h"
#include <unistd.h>
#include <algorithm>

static size_t onMp3ReadFd(void* pUserData, void* pBufferOut, size_t bytesToRead) {
    int fd = *reinterpret_cast<int*>(pUserData);
    size_t total = 0;
    while (total < bytesToRead) {
        ssize_t n = ::read(fd, static_cast<char*>(pBufferOut) + total, bytesToRead - total);
        if (n <= 0) break;
        total += static_cast<size_t>(n);
    }
    return total;
}

static drmp3_bool32 onMp3SeekFd(void* pUserData, int offset, drmp3_seek_origin origin) {
    int fd = *reinterpret_cast<int*>(pUserData);
    int whence = SEEK_SET;
    if (origin == DRMP3_SEEK_CUR) whence = SEEK_CUR;
    else if (origin == DRMP3_SEEK_END) whence = SEEK_END;
    off_t res = ::lseek(fd, offset, whence);
    return (res != (off_t)-1) ? DRMP3_TRUE : DRMP3_FALSE;
}

static drmp3_bool32 onMp3TellFd(void* pUserData, drmp3_int64* pCursor) {
    int fd = *reinterpret_cast<int*>(pUserData);
    off_t res = ::lseek(fd, 0, SEEK_CUR);
    if (res == (off_t)-1) return DRMP3_FALSE;
    *pCursor = res;
    return DRMP3_TRUE;
}

Mp3Decoder::Mp3Decoder() = default;

Mp3Decoder::~Mp3Decoder() {
    close();
}

void Mp3Decoder::close() {
    if (isOpened_) {
        drmp3_uninit(&mp3_);
        isOpened_ = false;
    }
    if (fd_ >= 0) {
        ::close(fd_);
        fd_ = -1;
    }
    currentFrame_ = 0;
    totalFrames_ = 0;
}

bool Mp3Decoder::open(int fd) {
    close();
    if (fd < 0) return false;

    fd_ = ::dup(fd);
    if (fd_ < 0) return false;

    ::lseek(fd_, 0, SEEK_SET);

    if (!drmp3_init(&mp3_, onMp3ReadFd, onMp3SeekFd, onMp3TellFd, nullptr, &fd_, nullptr)) {
        close();
        return false;
    }

    isOpened_ = true;
    currentFrame_ = 0;
    totalFrames_ = static_cast<int64_t>(drmp3_get_pcm_frame_count(&mp3_));
    return true;
}

size_t Mp3Decoder::read(float* outBuffer, size_t framesToRead) {
    if (!isOpened_ || framesToRead == 0) return 0;

    drmp3_uint64 framesRead = drmp3_read_pcm_frames_f32(&mp3_, framesToRead, outBuffer);
    if (framesRead == 0) return 0;

    if (mp3_.channels == 1) {
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

bool Mp3Decoder::seek(int64_t frameIndex) {
    if (!isOpened_) return false;
    if (frameIndex < 0) frameIndex = 0;
    if (totalFrames_ > 0 && frameIndex > totalFrames_) {
        frameIndex = totalFrames_;
    }

    drmp3_bool32 ok = drmp3_seek_to_pcm_frame(&mp3_, static_cast<drmp3_uint64>(frameIndex));
    if (ok == DRMP3_TRUE) {
        currentFrame_ = frameIndex;
        return true;
    }
    return false;
}

int32_t Mp3Decoder::getSampleRate() const {
    return isOpened_ ? static_cast<int32_t>(mp3_.sampleRate) : 0;
}

int32_t Mp3Decoder::getChannelCount() const {
    // We always output stereo (2 channels)
    return isOpened_ ? 2 : 0;
}

int64_t Mp3Decoder::getTotalFrames() const {
    return isOpened_ ? totalFrames_ : 0;
}

int64_t Mp3Decoder::getCurrentFrame() const {
    return isOpened_ ? currentFrame_ : 0;
}
