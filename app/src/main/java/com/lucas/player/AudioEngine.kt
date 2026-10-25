package com.lucas.player

import android.os.ParcelFileDescriptor

/**
 * Kotlin wrapper for the native C++ Oboe Audio Engine.
 */
class AudioEngine {

    private var nativeHandle: Long = 0

    init {
        nativeHandle = nativeCreate()
        check(nativeHandle != 0L) { "Failed to create native AudioEngine" }
    }

    /**
     * Opens an audio file via ParcelFileDescriptor.
     * C++ makes an internal dup() of the fd, so the caller retains ownership
     * of [pfd] and should close it when done.
     */
    fun open(pfd: ParcelFileDescriptor): Boolean {
        if (nativeHandle == 0L) return false
        return nativeOpenFile(nativeHandle, pfd.fd)
    }

    fun play(): Boolean {
        if (nativeHandle == 0L) return false
        return nativePlay(nativeHandle)
    }

    fun pause(): Boolean {
        if (nativeHandle == 0L) return false
        return nativePause(nativeHandle)
    }

    fun stop(): Boolean {
        if (nativeHandle == 0L) return false
        return nativeStop(nativeHandle)
    }

    fun seekTo(frameIndex: Long): Boolean {
        if (nativeHandle == 0L) return false
        return nativeSeekTo(nativeHandle, frameIndex)
    }

    fun seekToMs(positionMs: Long): Boolean {
        val rate = getSampleRate()
        if (rate <= 0) return false
        val frame = (positionMs * rate) / 1000L
        return seekTo(frame)
    }

    fun setVolume(volume: Float) {
        if (nativeHandle == 0L) return
        nativeSetVolume(nativeHandle, volume.coerceIn(0.0f, 1.0f))
    }

    fun getVolume(): Float {
        if (nativeHandle == 0L) return 0.0f
        return nativeGetVolume(nativeHandle)
    }

    fun getPositionFrames(): Long {
        if (nativeHandle == 0L) return 0L
        return nativeGetPositionFrames(nativeHandle)
    }

    fun getDurationFrames(): Long {
        if (nativeHandle == 0L) return 0L
        return nativeGetDurationFrames(nativeHandle)
    }

    fun getPositionMs(): Long {
        val rate = getSampleRate()
        if (rate <= 0) return 0L
        return (getPositionFrames() * 1000L) / rate
    }

    fun getDurationMs(): Long {
        val rate = getSampleRate()
        if (rate <= 0) return 0L
        return (getDurationFrames() * 1000L) / rate
    }

    fun getSampleRate(): Int {
        if (nativeHandle == 0L) return 0
        return nativeGetSampleRate(nativeHandle)
    }

    fun getChannelCount(): Int {
        if (nativeHandle == 0L) return 0
        return nativeGetChannelCount(nativeHandle)
    }

    fun isPlaying(): Boolean {
        if (nativeHandle == 0L) return false
        return nativeIsPlaying(nativeHandle)
    }

    fun isPaused(): Boolean {
        if (nativeHandle == 0L) return false
        return nativeIsPaused(nativeHandle)
    }

    fun setEqualizerBand(band: Int, gainDb: Float) {
        if (nativeHandle == 0L) return
        nativeSetEqualizerBand(nativeHandle, band, gainDb)
    }

    fun getEqualizerBand(band: Int): Float {
        if (nativeHandle == 0L) return 0f
        return nativeGetEqualizerBand(nativeHandle, band)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        if (nativeHandle == 0L) return
        nativeSetEqualizerEnabled(nativeHandle, enabled)
    }

    fun isEqualizerEnabled(): Boolean {
        if (nativeHandle == 0L) return false
        return nativeIsEqualizerEnabled(nativeHandle)
    }

    fun setPreamp(gainDb: Float) {
        if (nativeHandle == 0L) return
        nativeSetPreamp(nativeHandle, gainDb)
    }

    fun getPreamp(): Float {
        if (nativeHandle == 0L) return 0f
        return nativeGetPreamp(nativeHandle)
    }

    fun setBass(gainDb: Float) {
        if (nativeHandle == 0L) return
        nativeSetBass(nativeHandle, gainDb)
    }

    fun getBass(): Float {
        if (nativeHandle == 0L) return 0f
        return nativeGetBass(nativeHandle)
    }

    fun setTreble(gainDb: Float) {
        if (nativeHandle == 0L) return
        nativeSetTreble(nativeHandle, gainDb)
    }

    fun getTreble(): Float {
        if (nativeHandle == 0L) return 0f
        return nativeGetTreble(nativeHandle)
    }

    fun setToneEnabled(enabled: Boolean) {
        if (nativeHandle == 0L) return
        nativeSetToneEnabled(nativeHandle, enabled)
    }

    fun isToneEnabled(): Boolean {
        if (nativeHandle == 0L) return false
        return nativeIsToneEnabled(nativeHandle)
    }

    fun setBalance(balance: Float) {
        if (nativeHandle == 0L) return
        nativeSetBalance(nativeHandle, balance.coerceIn(-1.0f, 1.0f))
    }

    fun getBalance(): Float {
        if (nativeHandle == 0L) return 0f
        return nativeGetBalance(nativeHandle)
    }

    fun setMono(mono: Boolean) {
        if (nativeHandle == 0L) return
        nativeSetMono(nativeHandle, mono)
    }

    fun isMono(): Boolean {
        if (nativeHandle == 0L) return false
        return nativeIsMono(nativeHandle)
    }

    /**
     * Scans an audio file to produce normalized waveform amplitude peaks [0.0..1.0].
     * Similar to Poweramp's ScanDecode.
     */
    fun scanWaveform(pfd: ParcelFileDescriptor, numPoints: Int = 100): FloatArray? {
        return nativeScanWaveform(pfd.fd, numPoints)
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    protected fun finalize() {
        release()
    }

    companion object {
        init {
            System.loadLibrary("audio_engine")
        }
    }

    // Native JNI functions
    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeOpenFile(handle: Long, fd: Int): Boolean
    private external fun nativePlay(handle: Long): Boolean
    private external fun nativePause(handle: Long): Boolean
    private external fun nativeStop(handle: Long): Boolean
    private external fun nativeSeekTo(handle: Long, frameIndex: Long): Boolean
    private external fun nativeSetVolume(handle: Long, volume: Float)
    private external fun nativeGetVolume(handle: Long): Float
    private external fun nativeGetPositionFrames(handle: Long): Long
    private external fun nativeGetDurationFrames(handle: Long): Long
    private external fun nativeGetSampleRate(handle: Long): Int
    private external fun nativeGetChannelCount(handle: Long): Int
    private external fun nativeIsPlaying(handle: Long): Boolean
    private external fun nativeIsPaused(handle: Long): Boolean
    private external fun nativeSetEqualizerBand(handle: Long, band: Int, gainDb: Float)
    private external fun nativeGetEqualizerBand(handle: Long, band: Int): Float
    private external fun nativeSetEqualizerEnabled(handle: Long, enabled: Boolean)
    private external fun nativeIsEqualizerEnabled(handle: Long): Boolean
    private external fun nativeSetPreamp(handle: Long, gainDb: Float)
    private external fun nativeGetPreamp(handle: Long): Float
    private external fun nativeSetBass(handle: Long, gainDb: Float)
    private external fun nativeGetBass(handle: Long): Float
    private external fun nativeSetTreble(handle: Long, gainDb: Float)
    private external fun nativeGetTreble(handle: Long): Float
    private external fun nativeSetToneEnabled(handle: Long, enabled: Boolean)
    private external fun nativeIsToneEnabled(handle: Long): Boolean
    private external fun nativeSetBalance(handle: Long, balance: Float)
    private external fun nativeGetBalance(handle: Long): Float
    private external fun nativeSetMono(handle: Long, mono: Boolean)
    private external fun nativeIsMono(handle: Long): Boolean
    private external fun nativeScanWaveform(fd: Int, numPoints: Int): FloatArray?
}
