#include "decoder_factory.h"
#include "wav_decoder.h"
#include "flac_decoder.h"
#include "mp3_decoder.h"
#include "amedia_decoder.h"
#include <unistd.h>
#include <cstring>

std::unique_ptr<IDecoder> DecoderFactory::createDecoder(int fd) {
    if (fd < 0) return nullptr;

    char header[12] = {0};
    ::lseek(fd, 0, SEEK_SET);
    ssize_t bytesRead = ::read(fd, header, sizeof(header));
    ::lseek(fd, 0, SEEK_SET);

    if (bytesRead >= 3) {
        // Check for MP3 ID3v2 header: "ID3"
        if (std::memcmp(header, "ID3", 3) == 0) {
            auto mp3 = std::make_unique<Mp3Decoder>();
            if (mp3->open(fd)) {
                return mp3;
            }
        }
    }

    if (bytesRead >= 4) {
        // Check for FLAC magic header: "fLaC"
        if (std::memcmp(header, "fLaC", 4) == 0) {
            auto decoder = std::make_unique<FlacDecoder>();
            if (decoder->open(fd)) {
                return decoder;
            }
        }

        // Check for WAV magic header: "RIFF" .... "WAVE"
        if (bytesRead >= 12 && std::memcmp(header, "RIFF", 4) == 0 && std::memcmp(header + 8, "WAVE", 4) == 0) {
            auto decoder = std::make_unique<WavDecoder>();
            if (decoder->open(fd)) {
                return decoder;
            }
        }

        // Check for MP3 frame sync word: 0xFF followed by 0xE0-0xFF
        auto u0 = static_cast<uint8_t>(header[0]);
        auto u1 = static_cast<uint8_t>(header[1]);
        if (u0 == 0xFF && (u1 & 0xE0) == 0xE0) {
            auto mp3 = std::make_unique<Mp3Decoder>();
            if (mp3->open(fd)) {
                return mp3;
            }
        }
    }

    // Fallbacks: Try MP3, then FLAC, then WAV
    {
        auto mp3 = std::make_unique<Mp3Decoder>();
        if (mp3->open(fd)) {
            return mp3;
        }
    }
    {
        auto flac = std::make_unique<FlacDecoder>();
        if (flac->open(fd)) {
            return flac;
        }
    }
    {
        auto wav = std::make_unique<WavDecoder>();
        if (wav->open(fd)) {
            return wav;
        }
    }

    // Universal NDK codec fallback for AAC, M4A, OGG, OPUS, ALAC, etc.
    {
        auto amedia = std::make_unique<AmediaDecoder>();
        if (amedia->open(fd)) {
            return amedia;
        }
    }

    return nullptr;
}
