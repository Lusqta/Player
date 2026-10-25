#include <jni.h>
#include "audio_engine.h"

static inline AudioEngine* getEngine(jlong handle) {
    return reinterpret_cast<AudioEngine*>(handle);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_lucas_player_AudioEngine_nativeCreate(JNIEnv* /*env*/, jobject /*thiz*/) {
    try {
        auto* engine = new AudioEngine();
        return reinterpret_cast<jlong>(engine);
    } catch (...) {
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeDestroy(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        delete engine;
    } catch (...) {
        // Suppress any C++ exception crossing to JVM
    }
}

JNIEXPORT jboolean JNICALL
Java_com_lucas_player_AudioEngine_nativeOpenFile(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint fd) {
    try {
        auto* engine = getEngine(handle);
        if (!engine) return JNI_FALSE;
        return engine->openFile(fd) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_lucas_player_AudioEngine_nativePlay(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        if (!engine) return JNI_FALSE;
        return engine->play() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_lucas_player_AudioEngine_nativePause(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        if (!engine) return JNI_FALSE;
        return engine->pause() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_lucas_player_AudioEngine_nativeStop(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        if (!engine) return JNI_FALSE;
        return engine->stop() ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_lucas_player_AudioEngine_nativeSeekTo(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jlong frameIndex) {
    try {
        auto* engine = getEngine(handle);
        if (!engine) return JNI_FALSE;
        return engine->seekTo(frameIndex) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeSetVolume(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jfloat volume) {
    try {
        auto* engine = getEngine(handle);
        if (engine) engine->setVolume(volume);
    } catch (...) {
    }
}

JNIEXPORT jfloat JNICALL
Java_com_lucas_player_AudioEngine_nativeGetVolume(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return engine ? engine->getVolume() : 0.0f;
    } catch (...) {
        return 0.0f;
    }
}

JNIEXPORT jlong JNICALL
Java_com_lucas_player_AudioEngine_nativeGetPositionFrames(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return engine ? engine->getPositionFrames() : 0;
    } catch (...) {
        return 0;
    }
}

JNIEXPORT jlong JNICALL
Java_com_lucas_player_AudioEngine_nativeGetDurationFrames(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return engine ? engine->getDurationFrames() : 0;
    } catch (...) {
        return 0;
    }
}

JNIEXPORT jint JNICALL
Java_com_lucas_player_AudioEngine_nativeGetSampleRate(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return engine ? engine->getSampleRate() : 0;
    } catch (...) {
        return 0;
    }
}

JNIEXPORT jint JNICALL
Java_com_lucas_player_AudioEngine_nativeGetChannelCount(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return engine ? engine->getChannelCount() : 0;
    } catch (...) {
        return 0;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_lucas_player_AudioEngine_nativeIsPlaying(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return (engine && engine->isPlaying()) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_lucas_player_AudioEngine_nativeIsPaused(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return (engine && engine->isPaused()) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeSetEqualizerBand(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint band, jfloat gainDb) {
    try {
        auto* engine = getEngine(handle);
        if (engine && band >= 0) engine->setEqualizerBand(static_cast<size_t>(band), gainDb);
    } catch (...) {}
}

JNIEXPORT jfloat JNICALL
Java_com_lucas_player_AudioEngine_nativeGetEqualizerBand(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint band) {
    try {
        auto* engine = getEngine(handle);
        return (engine && band >= 0) ? engine->getEqualizerBand(static_cast<size_t>(band)) : 0.0f;
    } catch (...) {
        return 0.0f;
    }
}

JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeSetEqualizerEnabled(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jboolean enabled) {
    try {
        auto* engine = getEngine(handle);
        if (engine) engine->setEqualizerEnabled(enabled == JNI_TRUE);
    } catch (...) {}
}

JNIEXPORT jboolean JNICALL
Java_com_lucas_player_AudioEngine_nativeIsEqualizerEnabled(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return (engine && engine->isEqualizerEnabled()) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeSetPreamp(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jfloat gainDb) {
    try {
        auto* engine = getEngine(handle);
        if (engine) engine->setPreamp(gainDb);
    } catch (...) {}
}

JNIEXPORT jfloat JNICALL
Java_com_lucas_player_AudioEngine_nativeGetPreamp(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return engine ? engine->getPreamp() : 0.0f;
    } catch (...) {
        return 0.0f;
    }
}

JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeSetBass(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jfloat gainDb) {
    try {
        auto* engine = getEngine(handle);
        if (engine) engine->setBass(gainDb);
    } catch (...) {}
}

JNIEXPORT jfloat JNICALL
Java_com_lucas_player_AudioEngine_nativeGetBass(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return engine ? engine->getBass() : 0.0f;
    } catch (...) {
        return 0.0f;
    }
}

JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeSetTreble(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jfloat gainDb) {
    try {
        auto* engine = getEngine(handle);
        if (engine) engine->setTreble(gainDb);
    } catch (...) {}
}

JNIEXPORT jfloat JNICALL
Java_com_lucas_player_AudioEngine_nativeGetTreble(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return engine ? engine->getTreble() : 0.0f;
    } catch (...) {
        return 0.0f;
    }
}

JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeSetToneEnabled(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jboolean enabled) {
    try {
        auto* engine = getEngine(handle);
        if (engine) engine->setToneEnabled(enabled == JNI_TRUE);
    } catch (...) {}
}

JNIEXPORT jboolean JNICALL
Java_com_lucas_player_AudioEngine_nativeIsToneEnabled(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return (engine && engine->isToneEnabled()) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeSetBalance(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jfloat balance) {
    try {
        auto* engine = getEngine(handle);
        if (engine) engine->setBalance(balance);
    } catch (...) {}
}

JNIEXPORT jfloat JNICALL
Java_com_lucas_player_AudioEngine_nativeGetBalance(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return engine ? engine->getBalance() : 0.0f;
    } catch (...) {
        return 0.0f;
    }
}

JNIEXPORT void JNICALL
Java_com_lucas_player_AudioEngine_nativeSetMono(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jboolean mono) {
    try {
        auto* engine = getEngine(handle);
        if (engine) engine->setMono(mono == JNI_TRUE);
    } catch (...) {}
}

JNIEXPORT jboolean JNICALL
Java_com_lucas_player_AudioEngine_nativeIsMono(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        auto* engine = getEngine(handle);
        return (engine && engine->isMono()) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_lucas_player_AudioEngine_nativeScanWaveform(JNIEnv* env, jobject /*thiz*/, jint fd, jint numPoints) {
    try {
        std::vector<float> peaks = AudioEngine::scanWaveform(fd, numPoints);
        if (peaks.empty()) return nullptr;

        jfloatArray result = env->NewFloatArray(static_cast<jsize>(peaks.size()));
        if (!result) return nullptr;

        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(peaks.size()), peaks.data());
        return result;
    } catch (...) {
        return nullptr;
    }
}

} // extern "C"
