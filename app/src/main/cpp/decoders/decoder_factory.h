#pragma once

#include "decoder.h"
#include <memory>

class DecoderFactory {
public:
    static std::unique_ptr<IDecoder> createDecoder(int fd);
};
