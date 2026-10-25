#include "amedia_decoder.h"
#include <cstring>
#include <algorithm>

AmediaDecoder::AmediaDecoder() = default;

AmediaDecoder::~AmediaDecoder() {
    close();
}

void AmediaDecoder::close() {
    if (codec_) {
        AMediaCodec_stop(codec_);
        AMediaCodec_delete(codec_);
        codec_ = nullptr;
    }
    if (format_) {
        AMediaFormat_delete(format_);
        format_ = nullptr;
    }
    if (extractor_) {
        AMediaExtractor_delete(extractor_);
        extractor_ = nullptr;
    }
    if (fd_ >= 0) {
        ::close(fd_);
        fd_ = -1;
    }
    sampleRate_ = 0;
    channels_ = 0;
    durationUs_ = 0;
    totalFrames_ = 0;
    currentFrame_ = 0;
    isEos_ = false;
    isOpened_ = false;
    leftoverBuffer_.clear();
    leftoverOffset_ = 0;
}

bool AmediaDecoder::open(int fd) {
    close();
    if (fd < 0) return false;

    fd_ = ::dup(fd);
    if (fd_ < 0) return false;

    off64_t fileSize = ::lseek64(fd_, 0, SEEK_END);
    ::lseek64(fd_, 0, SEEK_SET);

    extractor_ = AMediaExtractor_new();
    if (!extractor_) {
        close();
        return false;
    }

    if (AMediaExtractor_setDataSourceFd(extractor_, fd_, 0, fileSize) != AMEDIA_OK) {
        close();
        return false;
    }

    size_t numTracks = AMediaExtractor_getTrackCount(extractor_);
    ssize_t audioTrackIndex = -1;
    const char* selectedMime = nullptr;

    for (size_t i = 0; i < numTracks; ++i) {
        AMediaFormat* trackFormat = AMediaExtractor_getTrackFormat(extractor_, i);
        const char* mime = nullptr;
        if (AMediaFormat_getString(trackFormat, AMEDIAFORMAT_KEY_MIME, &mime) && mime) {
            if (std::strncmp(mime, "audio/", 6) == 0) {
                audioTrackIndex = static_cast<ssize_t>(i);
                selectedMime = mime;
                format_ = trackFormat;
                break;
            }
        }
        AMediaFormat_delete(trackFormat);
    }

    if (audioTrackIndex < 0 || !selectedMime) {
        close();
        return false;
    }

    AMediaExtractor_selectTrack(extractor_, static_cast<size_t>(audioTrackIndex));

    AMediaFormat_getInt32(format_, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sampleRate_);
    AMediaFormat_getInt32(format_, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channels_);
    AMediaFormat_getInt64(format_, AMEDIAFORMAT_KEY_DURATION, &durationUs_);
    AMediaFormat_getInt32(format_, "pcm-encoding", &pcmEncoding_);

    if (sampleRate_ <= 0) sampleRate_ = 44100;
    if (channels_ <= 0) channels_ = 2;
    if (durationUs_ > 0) {
        totalFrames_ = (durationUs_ * sampleRate_) / 1000000LL;
    }

    codec_ = AMediaCodec_createDecoderByType(selectedMime);
    if (!codec_) {
        close();
        return false;
    }

    if (AMediaCodec_configure(codec_, format_, nullptr, nullptr, 0) != AMEDIA_OK) {
        close();
        return false;
    }

    if (AMediaCodec_start(codec_) != AMEDIA_OK) {
        close();
        return false;
    }

    isOpened_ = true;
    currentFrame_ = 0;
    isEos_ = false;
    return true;
}

size_t AmediaDecoder::read(float* outBuffer, size_t framesToRead) {
    if (!isOpened_ || framesToRead == 0) return 0;

    size_t framesDelivered = 0;

    // 1. First drain any samples leftover from previous decode iteration
    while (leftoverOffset_ < leftoverBuffer_.size() && framesDelivered < framesToRead) {
        outBuffer[framesDelivered * 2] = leftoverBuffer_[leftoverOffset_];
        outBuffer[framesDelivered * 2 + 1] = leftoverBuffer_[leftoverOffset_ + 1];
        leftoverOffset_ += 2;
        framesDelivered++;
    }

    if (leftoverOffset_ >= leftoverBuffer_.size()) {
        leftoverBuffer_.clear();
        leftoverOffset_ = 0;
    }

    // 2. Pump codec until we deliver requested frames or hit EOS
    constexpr int kMaxAttempts = 32;
    int attempts = 0;

    while (framesDelivered < framesToRead && !isEos_ && attempts++ < kMaxAttempts) {
        // Enqueue input buffer
        ssize_t inIndex = AMediaCodec_dequeueInputBuffer(codec_, 2000 /* 2ms */);
        if (inIndex >= 0) {
            size_t inBufSize = 0;
            uint8_t* inBuf = AMediaCodec_getInputBuffer(codec_, static_cast<size_t>(inIndex), &inBufSize);
            ssize_t sampleSize = AMediaExtractor_readSampleData(extractor_, inBuf, inBufSize);
            if (sampleSize < 0) {
                AMediaCodec_queueInputBuffer(codec_, static_cast<size_t>(inIndex), 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
            } else {
                int64_t sampleTime = AMediaExtractor_getSampleTime(extractor_);
                AMediaCodec_queueInputBuffer(codec_, static_cast<size_t>(inIndex), 0, static_cast<size_t>(sampleSize), sampleTime, 0);
                AMediaExtractor_advance(extractor_);
            }
        }

        // Dequeue output buffer
        AMediaCodecBufferInfo info;
        ssize_t outIndex = AMediaCodec_dequeueOutputBuffer(codec_, &info, 5000 /* 5ms */);

        if (outIndex >= 0) {
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                isEos_ = true;
            }

            size_t outBufSize = 0;
            uint8_t* outBuf = AMediaCodec_getOutputBuffer(codec_, static_cast<size_t>(outIndex), &outBufSize);

            if (outBuf && info.size > 0) {
                const uint8_t* data = outBuf + info.offset;
                size_t numDecodedFrames = 0;

                // Most Android decoders output signed 16-bit PCM
                if (pcmEncoding_ == 4) { // PCM Float
                    const float* samples = reinterpret_cast<const float*>(data);
                    numDecodedFrames = info.size / (channels_ * sizeof(float));

                    for (size_t f = 0; f < numDecodedFrames; ++f) {
                        float l = samples[f * channels_];
                        float r = (channels_ > 1) ? samples[f * channels_ + 1] : l;

                        if (framesDelivered < framesToRead) {
                            outBuffer[framesDelivered * 2] = l;
                            outBuffer[framesDelivered * 2 + 1] = r;
                            framesDelivered++;
                        } else {
                            leftoverBuffer_.push_back(l);
                            leftoverBuffer_.push_back(r);
                        }
                    }
                } else { // Default: Signed 16-bit integer PCM
                    const int16_t* samples = reinterpret_cast<const int16_t*>(data);
                    numDecodedFrames = info.size / (channels_ * sizeof(int16_t));
                    constexpr float kScale = 1.0f / 32768.0f;

                    for (size_t f = 0; f < numDecodedFrames; ++f) {
                        float l = static_cast<float>(samples[f * channels_]) * kScale;
                        float r = (channels_ > 1) ? (static_cast<float>(samples[f * channels_ + 1]) * kScale) : l;

                        if (framesDelivered < framesToRead) {
                            outBuffer[framesDelivered * 2] = l;
                            outBuffer[framesDelivered * 2 + 1] = r;
                            framesDelivered++;
                        } else {
                            leftoverBuffer_.push_back(l);
                            leftoverBuffer_.push_back(r);
                        }
                    }
                }
            }

            AMediaCodec_releaseOutputBuffer(codec_, static_cast<size_t>(outIndex), false);
        } else if (outIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* newFormat = AMediaCodec_getOutputFormat(codec_);
            if (newFormat) {
                AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sampleRate_);
                AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channels_);
                AMediaFormat_getInt32(newFormat, "pcm-encoding", &pcmEncoding_);
                AMediaFormat_delete(newFormat);
            }
        }
    }

    currentFrame_ += static_cast<int64_t>(framesDelivered);
    return framesDelivered;
}

bool AmediaDecoder::seek(int64_t frameIndex) {
    if (!isOpened_ || !extractor_ || !codec_) return false;
    if (frameIndex < 0) frameIndex = 0;
    if (totalFrames_ > 0 && frameIndex > totalFrames_) frameIndex = totalFrames_;

    int64_t targetUs = (frameIndex * 1000000LL) / sampleRate_;
    AMediaExtractor_seekTo(extractor_, targetUs, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
    AMediaCodec_flush(codec_);

    leftoverBuffer_.clear();
    leftoverOffset_ = 0;
    currentFrame_ = frameIndex;
    isEos_ = false;
    return true;
}
