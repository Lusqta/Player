#include "AudioEngine.h"
#include <jni.h>
#include <string>

// Instância global simples
static AudioEngine *engine = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_seunome_player_AudioEngine_initEngine(JNIEnv *env, jobject thiz) {
  LOGI("Inicializando motor de áudio via Oboe...");
  if (!engine) {
    engine = new AudioEngine();
  }
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_seunome_player_AudioEngine_playFile(
    JNIEnv *env, jobject thiz, jstring path) {
  if (!engine)
    return JNI_FALSE;

  const char *filePath = env->GetStringUTFChars(path, nullptr);
  LOGI("Carregando arquivo (Fase 4 - minmp3/dr_flac): %s", filePath);

  bool result = engine->load(filePath);
  if (result) {
    engine->start();
  }

  env->ReleaseStringUTFChars(path, filePath);
  return result ? JNI_TRUE : JNI_FALSE;
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
}

} // extern "C"
