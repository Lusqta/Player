package com.seunome.player

class AudioEngine {

    companion object {
        init {
            System.loadLibrary("audio_engine")
        }
    }

    external fun initEngine(): Boolean
    external fun playFile(path: String): Boolean
    external fun pause()
    external fun resume()
    external fun seek(positionMs: Long)
    external fun getPosition(): Long
    external fun stop()
    external fun destroyEngine()
}
