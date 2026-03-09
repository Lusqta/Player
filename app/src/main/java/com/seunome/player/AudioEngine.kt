package com.seunome.player

class AudioEngine {

    companion object {
        init {
            System.loadLibrary("audio_engine")
        }
    }

    external fun initEngine(): Boolean
    external fun playFile(path: String): Boolean
    external fun loadOnly(path: String): Boolean
    external fun setNextAudio(path: String): Boolean
    external fun pause()
    external fun resume()
    external fun setVolume(volume: Float)
    external fun seek(positionMs: Long)
    external fun getPosition(): Long
    external fun stop()
    external fun destroyEngine()

    interface OnCompletionListener {
        fun onCompletion()
    }
    
    interface OnGaplessListener {
        fun onGaplessTransition()
    }

    private var completionListener: OnCompletionListener? = null
    private var gaplessListener: OnGaplessListener? = null

    fun setCompletionListener(listener: OnCompletionListener?) {
        this.completionListener = listener
    }
    
    fun setGaplessListener(listener: OnGaplessListener?) {
        this.gaplessListener = listener
    }

    // Chamado pelo C++ via JNI quando a música acaba (Com Gapless ou Não)
    @Suppress("unused")
    private fun onPlaybackCompleted() {
        completionListener?.onCompletion()
    }

    // Chamado pelo C++ via JNI EXATAMENTE no microssegundo que o aúdio transiciona sem pausa
    @Suppress("unused")
    private fun onGaplessTransition() {
        gaplessListener?.onGaplessTransition()
    }
}
