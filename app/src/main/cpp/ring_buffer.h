#pragma once

#include <vector>
#include <atomic>
#include <cstdint>
#include <cstddef>
#include <algorithm>
#include <cstring>

/**
 * Lock-free Single-Producer Single-Consumer (SPSC) Ring Buffer for audio frames.
 * Format: 32-bit Float, Stereo (2 channels interleaved).
 *
 * All operations on the audio thread (consumer) are completely non-blocking,
 * wait-free and allocation-free.
 */
class SpscRingBuffer {
public:
    explicit SpscRingBuffer(size_t capacityFramesPowerOfTwo = 131072) // ~2.73s at 48kHz
        : capacity_(roundUpPowerOfTwo(capacityFramesPowerOfTwo)),
          mask_(capacity_ - 1),
          buffer_(capacity_ * kChannels, 0.0f),
          writeIndex_(0),
          readIndex_(0) {}

    ~SpscRingBuffer() = default;

    // Disallow copy/move
    SpscRingBuffer(const SpscRingBuffer&) = delete;
    SpscRingBuffer& operator=(const SpscRingBuffer&) = delete;

    static constexpr size_t kChannels = 2;

    /**
     * Called by PRODUCER (decoder thread).
     * Writes up to 'frames' from source (interleaved stereo float).
     * Returns the actual number of frames written.
     */
    size_t write(const float* source, size_t frames) noexcept {
        const uint64_t currentWrite = writeIndex_.load(std::memory_order_relaxed);
        const uint64_t currentRead = readIndex_.load(std::memory_order_acquire);

        const size_t occupied = static_cast<size_t>(currentWrite - currentRead);
        const size_t freeSpace = capacity_ - occupied;
        const size_t framesToWrite = std::min(frames, freeSpace);

        if (framesToWrite == 0) {
            return 0;
        }

        const size_t startPos = static_cast<size_t>(currentWrite & mask_) * kChannels;
        const size_t totalSamples = framesToWrite * kChannels;
        const size_t bufferSamples = capacity_ * kChannels;

        if (startPos + totalSamples <= bufferSamples) {
            std::memcpy(&buffer_[startPos], source, totalSamples * sizeof(float));
        } else {
            const size_t firstChunkSamples = bufferSamples - startPos;
            const size_t secondChunkSamples = totalSamples - firstChunkSamples;
            std::memcpy(&buffer_[startPos], source, firstChunkSamples * sizeof(float));
            std::memcpy(&buffer_[0], source + firstChunkSamples, secondChunkSamples * sizeof(float));
        }

        writeIndex_.store(currentWrite + framesToWrite, std::memory_order_release);
        return framesToWrite;
    }

    /**
     * Called by CONSUMER (audio callback).
     * Reads up to 'frames' into destination (interleaved stereo float).
     * If fewer frames are available, fills remainder with silence (0.0f).
     * Returns actual frames read (excluding silence padding).
     */
    size_t read(float* destination, size_t frames) noexcept {
        const uint64_t currentRead = readIndex_.load(std::memory_order_relaxed);
        const uint64_t currentWrite = writeIndex_.load(std::memory_order_acquire);

        const size_t available = static_cast<size_t>(currentWrite - currentRead);
        const size_t framesToRead = std::min(frames, available);

        if (framesToRead > 0) {
            const size_t startPos = static_cast<size_t>(currentRead & mask_) * kChannels;
            const size_t totalSamples = framesToRead * kChannels;
            const size_t bufferSamples = capacity_ * kChannels;

            if (startPos + totalSamples <= bufferSamples) {
                std::memcpy(destination, &buffer_[startPos], totalSamples * sizeof(float));
            } else {
                const size_t firstChunkSamples = bufferSamples - startPos;
                const size_t secondChunkSamples = totalSamples - firstChunkSamples;
                std::memcpy(destination, &buffer_[startPos], firstChunkSamples * sizeof(float));
                std::memcpy(destination + firstChunkSamples, &buffer_[0], secondChunkSamples * sizeof(float));
            }

            readIndex_.store(currentRead + framesToRead, std::memory_order_release);
        }

        // Fill remaining with silence if buffer underrun occurred
        if (framesToRead < frames) {
            const size_t silenceSamples = (frames - framesToRead) * kChannels;
            std::memset(destination + (framesToRead * kChannels), 0, silenceSamples * sizeof(float));
        }

        return framesToRead;
    }

    /**
     * Available frames to read.
     */
    size_t availableRead() const noexcept {
        const uint64_t r = readIndex_.load(std::memory_order_relaxed);
        const uint64_t w = writeIndex_.load(std::memory_order_relaxed);
        return static_cast<size_t>(w - r);
    }

    /**
     * Available space in frames to write.
     */
    size_t availableWrite() const noexcept {
        const uint64_t r = readIndex_.load(std::memory_order_relaxed);
        const uint64_t w = writeIndex_.load(std::memory_order_relaxed);
        const size_t occupied = static_cast<size_t>(w - r);
        return (occupied < capacity_) ? (capacity_ - occupied) : 0;
    }

    /**
     * Flushes the buffer. Safe when producer and consumer are coordinated (e.g. stopped/seeking).
     */
    void reset() noexcept {
        const uint64_t currentWrite = writeIndex_.load(std::memory_order_relaxed);
        readIndex_.store(currentWrite, std::memory_order_release);
    }

    size_t capacity() const noexcept {
        return capacity_;
    }

private:
    static size_t roundUpPowerOfTwo(size_t v) {
        if (v == 0) return 1;
        uint64_t val = static_cast<uint64_t>(v) - 1;
        val |= val >> 1;
        val |= val >> 2;
        val |= val >> 4;
        val |= val >> 8;
        val |= val >> 16;
        val |= val >> 32;
        return static_cast<size_t>(val + 1);
    }

    const size_t capacity_;
    const size_t mask_;
    std::vector<float> buffer_;

    // Avoid false sharing by putting them on separate cache lines if possible
    alignas(64) std::atomic<uint64_t> writeIndex_;
    alignas(64) std::atomic<uint64_t> readIndex_;
};
