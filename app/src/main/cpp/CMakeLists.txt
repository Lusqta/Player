cmake_minimum_required(VERSION 3.22.1)
project(player_audio_engine LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find prefab Oboe package
find_package(oboe REQUIRED CONFIG)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/decoders
)

add_library(audio_engine SHARED
    jni_bridge.cpp
    audio_engine.cpp
    audio_output.cpp
    decoders/wav_decoder.cpp
    decoders/flac_decoder.cpp
    decoders/mp3_decoder.cpp
    decoders/amedia_decoder.cpp
    decoders/decoder_factory.cpp
)

target_link_libraries(audio_engine PRIVATE
    oboe::oboe
    log
    mediandk
)

target_compile_options(audio_engine PRIVATE
    -Wall
    -Wextra
    -O3
    -fvisibility=hidden
)
