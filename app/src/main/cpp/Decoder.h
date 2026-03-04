#pragma once

#include <memory>
#include <string>

class Decoder {
public:
  virtual ~Decoder() = default;

  virtual bool open(const std::string &path) = 0;
  virtual void close() = 0;
  virtual int readFrames(float *buffer, int numFrames) = 0;
  virtual void seek(long positionMs) = 0;
  virtual int getSampleRate() const = 0;
  virtual int getChannelCount() const = 0;
  virtual long getDurationMs() const = 0;
  virtual long getPositionMs() const = 0;

  // Factory method para instanciar FLAC ou MP3 baseado na extensao
  static std::unique_ptr<Decoder> create(const std::string &path);
};
