#pragma once

#include <cstdint>
#include <cstddef>

/**
 * Universal Audio Decoder interface.
 * All decoders normalize their output to interleaved 32-bit float stereo (2 channels).
 */
class IDecoder {
public:
    virtual ~IDecoder() = default;

    /**
     * Opens an audio stream from an existing file descriptor.
     * The decoder creates its own dup(fd) or owns the descriptor.
     */
    virtual bool open(int fd) = 0;

    /**
     * Reads up to 'framesToRead' frames into outBuffer.
     * outBuffer must have space for at least framesToRead * 2 floats.
     * Returns the actual number of frames read (0 on EOF or error).
     */
    virtual size_t read(float* outBuffer, size_t framesToRead) = 0;

    /**
     * Seeks to a specific PCM frame index.
     * Returns true on success, false on error.
     */
    virtual bool seek(int64_t frameIndex) = 0;

    /**
     * Original sample rate (e.g., 44100, 48000, 96000).
     */
    virtual int32_t getSampleRate() const = 0;

    /**
     * Source channel count (1 = mono, 2 = stereo).
     */
    virtual int32_t getChannelCount() const = 0;

    /**
     * Total PCM frames in the stream.
     */
    virtual int64_t getTotalFrames() const = 0;

    /**
     * Current frame position in the stream.
     */
    virtual int64_t getCurrentFrame() const = 0;
};
