#include "AudioEngine.h"
#include <jni.h>
#include <string>

// Instância global simples
static AudioEngine *engine = nullptr;

// Cache do objeto Kotlin AudioEngine para podermos chamar o método dele
static JavaVM *g_jvm = nullptr;
static jobject g_audioEngineObj = nullptr;

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  g_jvm = vm;
  return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_seunome_player_AudioEngine_initEngine(JNIEnv *env, jobject thiz) {
  LOGI("Inicializando motor de áudio via Oboe...");
  if (!engine) {
    engine = new AudioEngine();
    // Guardamos a referência do Kotlin AudioEngine
    g_audioEngineObj = env->NewGlobalRef(thiz);

    // Configuramos no C++ uma lambda function que será atirada ao fim da música
    engine->setOnPlaybackCompletedCallback([]() {
      if (g_jvm && g_audioEngineObj) {
        JNIEnv *tempEnv;
        bool attached = false;

        // Pega a Thread Java atual ou ataço a Thread do Oboe nela
        int getEnvStat = g_jvm->GetEnv((void **)&tempEnv, JNI_VERSION_1_6);
        if (getEnvStat == JNI_EDETACHED) {
          if (g_jvm->AttachCurrentThread(&tempEnv, NULL) != 0) {
            LOGE("Falha ao anexar thread do AudioEngine à JVM");
            return;
          }
          attached = true;
        }

        jclass clazz = tempEnv->GetObjectClass(g_audioEngineObj);
        jmethodID methodId =
            tempEnv->GetMethodID(clazz, "onPlaybackCompleted", "()V");
        if (methodId) {
          tempEnv->CallVoidMethod(g_audioEngineObj, methodId);
        }

        if (attached) {
          g_jvm->DetachCurrentThread();
        }
      }
    });

    engine->setOnGaplessTransitionCallback([]() {
      if (g_jvm && g_audioEngineObj) {
        JNIEnv *tempEnv;
        bool attached = false;

        // Pega a Thread Java atual ou ataço a Thread do Oboe nela
        int getEnvStat = g_jvm->GetEnv((void **)&tempEnv, JNI_VERSION_1_6);
        if (getEnvStat == JNI_EDETACHED) {
          if (g_jvm->AttachCurrentThread(&tempEnv, NULL) != 0) {
            LOGE("Falha ao anexar thread do AudioEngine à JVM");
            return;
          }
          attached = true;
        }

        jclass clazz = tempEnv->GetObjectClass(g_audioEngineObj);
        jmethodID methodId =
            tempEnv->GetMethodID(clazz, "onGaplessTransition", "()V");
        if (methodId) {
          tempEnv->CallVoidMethod(g_audioEngineObj, methodId);
        }

        if (attached) {
          g_jvm->DetachCurrentThread();
        }
      }
    });
  }
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_seunome_player_AudioEngine_playFile(
    JNIEnv *env, jobject thiz, jstring path) {
  if (!engine)
    return JNI_FALSE;

  const char *cPath = env->GetStringUTFChars(path, nullptr);
  LOGI("Carregando arquivo (Fase 4 - minmp3/dr_flac): %s", cPath);

  bool success = engine->load(cPath);
  if (success) {
    engine->start();
  }

  env->ReleaseStringUTFChars(path, cPath);
  return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_seunome_player_AudioEngine_loadOnly(
    JNIEnv *env, jobject thiz, jstring path) {
  if (!engine)
    return JNI_FALSE;

  const char *cPath = env->GetStringUTFChars(path, nullptr);
  LOGI("Apenas carregando binários para a Ram via Restore State: %s", cPath);

  bool success = engine->load(cPath);
  // Pula a invocação do engine->start(), mantendo em silêncio.

  env->ReleaseStringUTFChars(path, cPath);
  return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_seunome_player_AudioEngine_setNextAudio(
    JNIEnv *env, jobject thiz, jstring path) {
  if (!path)
    return JNI_FALSE;
  const char *cPath = env->GetStringUTFChars(path, nullptr);

  if (engine) {
    bool success = engine->setNextAudio(cPath);
    env->ReleaseStringUTFChars(path, cPath);
    return success ? JNI_TRUE : JNI_FALSE;
  }

  env->ReleaseStringUTFChars(path, cPath);
  return JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_seunome_player_AudioEngine_pause(JNIEnv *env,
                                                                 jobject thiz) {
  LOGI("Pausando JNI...");
  if (engine)
    engine->pause();
}

JNIEXPORT void JNICALL
Java_com_seunome_player_AudioEngine_resume(JNIEnv *env, jobject thiz) {
  LOGI("Retomando JNI...");
  if (engine)
    engine->resume();
}

JNIEXPORT void JNICALL Java_com_seunome_player_AudioEngine_seek(
    JNIEnv *env, jobject thiz, jlong positionMs) {
  LOGI("Seek pendente: %lld ms", (long long)positionMs);
  if (engine)
    engine->seek(positionMs);
}

JNIEXPORT void JNICALL Java_com_seunome_player_AudioEngine_stop(JNIEnv *env,
                                                                jobject thiz) {
  LOGI("Parando JNI...");
  if (engine) {
    engine->stop();
  }
}

JNIEXPORT void JNICALL Java_com_seunome_player_AudioEngine_setVolume(
    JNIEnv *env, jobject thiz, jfloat volume) {
  if (engine) {
    engine->setVolume(volume);
  }
}

JNIEXPORT jlong JNICALL
Java_com_seunome_player_AudioEngine_getPosition(JNIEnv *env, jobject thiz) {
  if (engine) {
    return engine->getPositionMs();
  }
  return 0;
}

JNIEXPORT void JNICALL
Java_com_seunome_player_AudioEngine_destroyEngine(JNIEnv *env, jobject thiz) {
  LOGI("Destruindo motor de áudio via JNI...");
  if (engine) {
    delete engine;
    engine = nullptr;
  }
  if (g_audioEngineObj) {
    env->DeleteGlobalRef(g_audioEngineObj);
    g_audioEngineObj = nullptr;
  }
}

} // extern "C"
