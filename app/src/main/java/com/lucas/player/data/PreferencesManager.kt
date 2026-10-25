package com.lucas.player.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    fun saveQueue(trackIds: List<Long>, currentIndex: Int, positionMs: Long) {
        val serializedIds = trackIds.joinToString(",")
        prefs.edit()
            .putString(KEY_QUEUE_IDS, serializedIds)
            .putInt(KEY_CURRENT_INDEX, currentIndex)
            .putLong(KEY_POSITION_MS, positionMs)
            .apply()
    }

    fun getSavedQueueIds(): List<Long> {
        val raw = prefs.getString(KEY_QUEUE_IDS, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    fun getSavedCurrentIndex(): Int {
        return prefs.getInt(KEY_CURRENT_INDEX, 0)
    }

    fun getSavedPositionMs(): Long {
        return prefs.getLong(KEY_POSITION_MS, 0L)
    }

    fun saveShuffle(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE, enabled).apply()
    }

    fun isShuffleEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHUFFLE, false)
    }

    fun saveRepeatMode(mode: Int) {
        prefs.edit().putInt(KEY_REPEAT_MODE, mode).apply()
    }

    fun getRepeatMode(): Int {
        return prefs.getInt(KEY_REPEAT_MODE, 0) // 0 = OFF, 1 = ALL, 2 = ONE
    }

    fun saveEqualizerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
    }

    fun isEqualizerEnabled(): Boolean {
        return prefs.getBoolean(KEY_EQ_ENABLED, false)
    }

    fun saveEqualizerBand(band: Int, gainDb: Float) {
        prefs.edit().putFloat("${KEY_EQ_BAND_PREFIX}$band", gainDb).apply()
    }

    fun getEqualizerBand(band: Int): Float {
        return prefs.getFloat("${KEY_EQ_BAND_PREFIX}$band", 0.0f)
    }

    fun savePreamp(gainDb: Float) {
        prefs.edit().putFloat(KEY_PREAMP, gainDb).apply()
    }

    fun getPreamp(): Float {
        return prefs.getFloat(KEY_PREAMP, 0.0f)
    }

    fun saveBass(gainDb: Float) {
        prefs.edit().putFloat(KEY_BASS, gainDb).apply()
    }

    fun getBass(): Float {
        return prefs.getFloat(KEY_BASS, 0.0f)
    }

    fun saveTreble(gainDb: Float) {
        prefs.edit().putFloat(KEY_TREBLE, gainDb).apply()
    }

    fun getTreble(): Float {
        return prefs.getFloat(KEY_TREBLE, 0.0f)
    }

    fun saveToneEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TONE_ENABLED, enabled).apply()
    }

    fun isToneEnabled(): Boolean {
        return prefs.getBoolean(KEY_TONE_ENABLED, false)
    }

    fun saveActivePresetName(name: String) {
        prefs.edit().putString(KEY_ACTIVE_PRESET, name).apply()
    }

    fun getActivePresetName(): String {
        return prefs.getString(KEY_ACTIVE_PRESET, "Flat") ?: "Flat"
    }

    fun saveCrossfadeDurationMs(ms: Int) {
        prefs.edit().putInt(KEY_CROSSFADE_MS, ms).apply()
    }

    fun getCrossfadeDurationMs(): Int {
        return prefs.getInt(KEY_CROSSFADE_MS, 0)
    }

    fun saveBalance(balance: Float) {
        prefs.edit().putFloat(KEY_BALANCE, balance).apply()
    }

    fun getBalance(): Float {
        return prefs.getFloat(KEY_BALANCE, 0.0f)
    }

    fun saveMono(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONO, enabled).apply()
    }

    fun isMonoEnabled(): Boolean {
        return prefs.getBoolean(KEY_MONO, false)
    }

    fun saveReplayGainMode(mode: Int) {
        prefs.edit().putInt(KEY_RG_MODE, mode).apply()
    }

    fun getReplayGainMode(): Int {
        return prefs.getInt(KEY_RG_MODE, 1) // 0 = OFF, 1 = TRACK, 2 = ALBUM
    }

    fun saveReplayGainPreampWithRg(gainDb: Float) {
        prefs.edit().putFloat(KEY_RG_PREAMP_WITH, gainDb).apply()
    }

    fun getReplayGainPreampWithRg(): Float {
        return prefs.getFloat(KEY_RG_PREAMP_WITH, 0.0f)
    }

    fun saveReplayGainPreampWithoutRg(gainDb: Float) {
        prefs.edit().putFloat(KEY_RG_PREAMP_WITHOUT, gainDb).apply()
    }

    fun getReplayGainPreampWithoutRg(): Float {
        return prefs.getFloat(KEY_RG_PREAMP_WITHOUT, 0.0f)
    }

    companion object {
        private const val KEY_QUEUE_IDS = "queue_ids"
        private const val KEY_CURRENT_INDEX = "current_index"
        private const val KEY_POSITION_MS = "position_ms"
        private const val KEY_SHUFFLE = "shuffle_enabled"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_BAND_PREFIX = "eq_band_"
        private const val KEY_PREAMP = "preamp"
        private const val KEY_BASS = "tone_bass"
        private const val KEY_TREBLE = "tone_treble"
        private const val KEY_TONE_ENABLED = "tone_enabled"
        private const val KEY_ACTIVE_PRESET = "active_preset_name"
        private const val KEY_CROSSFADE_MS = "crossfade_ms"
        private const val KEY_BALANCE = "stereo_balance"
        private const val KEY_MONO = "mono_audio"
        private const val KEY_RG_MODE = "replaygain_mode"
        private const val KEY_RG_PREAMP_WITH = "replaygain_preamp_with"
        private const val KEY_RG_PREAMP_WITHOUT = "replaygain_preamp_without"
    }
}
