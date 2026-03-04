cmake_minimum_required(VERSION 3.22.1)

project(audio_engine)

# Importa o Oboe disponibilizado pelo Prefab (build.gradle)
find_package(oboe REQUIRED CONFIG)

add_library(
    ${CMAKE_PROJECT_NAME} 
    SHARED
    audio_bridge.cpp
    AudioEngine.cpp
    Decoder.cpp
)

target_link_libraries(
    ${CMAKE_PROJECT_NAME}
    oboe::oboe
    android
    log
)
