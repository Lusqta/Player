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

    // --- Configurações Gerais (Fase 4.2) ---

    fun isPauseOnHeadsetDisconnectEnabled(): Boolean {
        return prefs.getBoolean(KEY_PAUSE_ON_HEADSET_DISCONNECT, true)
    }

    fun setPauseOnHeadsetDisconnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PAUSE_ON_HEADSET_DISCONNECT, enabled).apply()
    }

    fun isLightReactionTiltEnabled(): Boolean {
        return prefs.getBoolean(KEY_TILT_ENABLED, true)
    }

    fun setLightReactionTiltEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TILT_ENABLED, enabled).apply()
    }

    fun getReflectionSoftness(): Float {
        return prefs.getFloat(KEY_REFLECTION_SOFTNESS, 0.6f)
    }

    fun setReflectionSoftness(softness: Float) {
        prefs.edit().putFloat(KEY_REFLECTION_SOFTNESS, softness.coerceIn(0f, 1f)).apply()
    }

    fun getReduceMotionMode(): Int {
        // 0 = Seguir sistema, 1 = Sempre reduzir, 2 = Manter animações
        return prefs.getInt(KEY_REDUCE_MOTION_MODE, 0)
    }

    fun setReduceMotionMode(mode: Int) {
        prefs.edit().putInt(KEY_REDUCE_MOTION_MODE, mode).apply()
    }

    fun isShowFileFormatEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHOW_FILE_FORMAT, true)
    }

    fun setShowFileFormatEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_FILE_FORMAT, enabled).apply()
    }

    fun getIgnoreShorterThanSeconds(): Int {
        return prefs.getInt(KEY_IGNORE_SHORTER_THAN_SEC, 0)
    }

    fun setIgnoreShorterThanSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_IGNORE_SHORTER_THAN_SEC, seconds.coerceAtLeast(0)).apply()
    }

    fun isGaplessEnabled(): Boolean {
        return prefs.getBoolean(KEY_GAPLESS_ENABLED, true)
    }

    fun setGaplessEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GAPLESS_ENABLED, enabled).apply()
    }

    fun isResumePlaybackEnabled(): Boolean {
        return prefs.getBoolean(KEY_RESUME_PLAYBACK, true)
    }

    fun setResumePlaybackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESUME_PLAYBACK, enabled).apply()
    }

    // --- Buscas Recentes (Fase 3.3) ---

    fun getRecentSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(SEARCH_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val current = getRecentSearches().toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val trimmedList = current.take(8)
        prefs.edit().putString(KEY_RECENT_SEARCHES, trimmedList.joinToString(SEARCH_DELIMITER)).apply()
    }

    fun removeRecentSearch(query: String) {
        val trimmed = query.trim()
        val current = getRecentSearches().toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        prefs.edit().putString(KEY_RECENT_SEARCHES, current.joinToString(SEARCH_DELIMITER)).apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    // --- Permissões (Fase 3.1) ---

    fun hasPromptedNotificationPermission(): Boolean {
        return prefs.getBoolean(KEY_PROMPTED_NOTIFICATION, false)
    }

    fun setPromptedNotificationPermission(prompted: Boolean) {
        prefs.edit().putBoolean(KEY_PROMPTED_NOTIFICATION, prompted).apply()
    }

    fun hasRequestedAudioPermission(): Boolean {
        return prefs.getBoolean(KEY_REQUESTED_AUDIO_PERM, false)
    }

    fun setHasRequestedAudioPermission(requested: Boolean) {
        prefs.edit().putBoolean(KEY_REQUESTED_AUDIO_PERM, requested).apply()
    }

    companion object {
        private const val SEARCH_DELIMITER = "\u001F"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val KEY_PROMPTED_NOTIFICATION = "prompted_notification"
        private const val KEY_REQUESTED_AUDIO_PERM = "requested_audio_perm"
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
        private const val KEY_PAUSE_ON_HEADSET_DISCONNECT = "pause_on_headset_disconnect"
        private const val KEY_TILT_ENABLED = "tilt_enabled"
        private const val KEY_REFLECTION_SOFTNESS = "reflection_softness"
        private const val KEY_REDUCE_MOTION_MODE = "reduce_motion_mode"
        private const val KEY_SHOW_FILE_FORMAT = "show_file_format"
        private const val KEY_IGNORE_SHORTER_THAN_SEC = "ignore_shorter_than_sec"
        private const val KEY_GAPLESS_ENABLED = "gapless_enabled"
        private const val KEY_RESUME_PLAYBACK = "resume_playback"
    }
}
