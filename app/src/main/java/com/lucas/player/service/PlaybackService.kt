package com.lucas.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import com.lucas.player.AudioEngine
import com.lucas.player.MainActivity
import com.lucas.player.data.CoverArtLoader
import com.lucas.player.data.LibraryRepository
import com.lucas.player.data.PreferencesManager
import com.lucas.player.model.Track
import com.lucas.player.queue.QueueManager
import com.lucas.player.queue.RepeatMode
import com.lucas.player.queue.ShuffleMode

class PlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {

    interface PlaybackListener {
        fun onTrackChanged(track: Track?)
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onProgressUpdate(currentMs: Long, durationMs: Long)
        fun onQueueUpdated(tracks: List<Track>, currentIndex: Int)
    }

    private val binder = LocalBinder()
    private val listeners = mutableListOf<PlaybackListener>()
    private val playbackExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    val audioEngine = AudioEngine()
    val queueManager = QueueManager()
    private val libraryRepo = LibraryRepository()
    lateinit var preferencesManager: PreferencesManager
        private set

    private lateinit var mediaSession: MediaSession
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var hasAudioFocus = false
    private var pausedByTransientLoss = false
    private var wasPlaying = false
    private var hasRecordedPlayForCurrentTrack = false
    private var hasTriggeredAutoCrossfade = false
    private var currentCoverBitmap: Bitmap? = null

    // Sleep Timer
    private var sleepTimerEndTimestamp: Long = 0L
    private var sleepTimerStopAtTrackEnd: Boolean = false
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private val sleepTimerRunnable = object : Runnable {
        override fun run() {
            val remaining = sleepTimerEndTimestamp - System.currentTimeMillis()
            if (remaining <= 0) {
                pause()
                sleepTimerEndTimestamp = 0L
                audioEngine.setVolume(1.0f)
            } else {
                if (remaining <= 15000L) {
                    val fraction = remaining.toFloat() / 15000f
                    audioEngine.setVolume(fraction.coerceIn(0.0f, 1.0f))
                }
                sleepTimerHandler.postDelayed(this, 1000L)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (audioEngine.isPlaying()) {
                val current = audioEngine.getPositionMs()
                val total = audioEngine.getDurationMs()

                // Poweramp play count trigger: record when past 15s or 50%
                if (!hasRecordedPlayForCurrentTrack) {
                    val currentTrack = queueManager.currentTrack
                    if (currentTrack != null) {
                        if (current >= 15000L || (total > 0 && current >= total / 2)) {
                            hasRecordedPlayForCurrentTrack = true
                            libraryRepo.recordTrackPlayed(currentTrack.id)
                        }
                    }
                }

                notifyProgress(current, total)
                updateMediaSessionState(PlaybackState.STATE_PLAYING, current)

                // Poweramp Crossfade Engine: trigger seamless transition before track ends
                val crossfadeMs = preferencesManager.getCrossfadeDurationMs()
                if (crossfadeMs > 500 && total > 5000 && !hasTriggeredAutoCrossfade) {
                    if (current >= (total - crossfadeMs)) {
                        hasTriggeredAutoCrossfade = true
                        val nextTrack = queueManager.peekNext()
                        if (nextTrack != null && !sleepTimerStopAtTrackEnd) {
                            val fadeOutTime = (crossfadeMs / 2).coerceIn(200, 2000).toLong()
                            fadeOut(fadeOutTime) {
                                playNext(auto = true)
                            }
                        }
                    }
                }

                handler.postDelayed(this, 250)
            } else if (wasPlaying) {
                wasPlaying = false
                val current = audioEngine.getPositionMs()
                val total = audioEngine.getDurationMs()
                if (total > 0 && current >= (total - 500)) {
                    if (sleepTimerStopAtTrackEnd) {
                        sleepTimerStopAtTrackEnd = false
                        cancelSleepTimer()
                        pause()
                    } else {
                        playNext(auto = true)
                    }
                } else {
                    notifyState(false)
                    updateMediaSessionState(PlaybackState.STATE_PAUSED, current)
                    showNotification()
                }
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Restore saved shuffle and repeat
        val savedShuffle = preferencesManager.isShuffleEnabled()
        queueManager.setShuffleMode(if (savedShuffle) ShuffleMode.ALL_TRACKS else ShuffleMode.OFF)
        queueManager.setRepeat(RepeatMode.fromValue(preferencesManager.getRepeatMode()))

        // Restore saved Equalizer settings (10 Bands)
        val eqEnabled = preferencesManager.isEqualizerEnabled()
        audioEngine.setEqualizerEnabled(eqEnabled)
        for (i in 0 until 10) {
            val gain = preferencesManager.getEqualizerBand(i)
            audioEngine.setEqualizerBand(i, gain)
        }

        // Restore Preamp and Tone Controls (Bass/Treble)
        audioEngine.setPreamp(preferencesManager.getPreamp())
        audioEngine.setToneEnabled(preferencesManager.isToneEnabled())
        audioEngine.setBass(preferencesManager.getBass())
        audioEngine.setTreble(preferencesManager.getTreble())

        // Restore Stereo Balance and Mono Audio
        audioEngine.setBalance(preferencesManager.getBalance())
        audioEngine.setMono(preferencesManager.isMonoEnabled())

        initMediaSession()
        createNotificationChannel()

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_TOGGLE_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        saveCurrentState()
        handler.removeCallbacks(progressRunnable)
        cancelSleepTimer()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {}

        abandonAudioFocus()
        mediaSession.release()
        audioEngine.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun addListener(listener: PlaybackListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            listener.onTrackChanged(queueManager.currentTrack)
            listener.onPlaybackStateChanged(audioEngine.isPlaying())
            listener.onQueueUpdated(queueManager.getTracks(), queueManager.currentQueueIndex)
        }
    }

    fun removeListener(listener: PlaybackListener) {
        listeners.remove(listener)
    }

    fun playTrack(track: Track) {
        hasRecordedPlayForCurrentTrack = false
        hasTriggeredAutoCrossfade = false
        currentCoverBitmap = null

        playbackExecutor.execute {
            try {
                contentResolver.openFileDescriptor(track.uri, "r")?.use { pfd ->
                    if (audioEngine.open(pfd)) {
                        applyEffectivePreamp(track)
                        if (requestAudioFocus()) {
                            audioEngine.play()
                            wasPlaying = true
                            val crossfadeMs = preferencesManager.getCrossfadeDurationMs()
                            val fadeInTime = if (crossfadeMs > 0) (crossfadeMs / 2).coerceIn(150, 1500).toLong() else 120L
                            fadeIn(fadeInTime)
                            handler.post {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        startForeground(
                                            NOTIFICATION_ID,
                                            buildNotification(),
                                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                                        )
                                    } else {
                                        startForeground(NOTIFICATION_ID, buildNotification())
                                    }
                                } catch (_: Exception) {}
                                updateMediaSessionMetadata(track)
                                updateMediaSessionState(PlaybackState.STATE_PLAYING, 0)
                                notifyTrack(track)
                                notifyState(true)
                                startProgressUpdates()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        queueManager.setQueue(tracks, startIndex)
        notifyQueue()
        val track = queueManager.currentTrack
        if (track != null) {
            playTrack(track)
        }
    }

    fun play() {
        if (queueManager.currentTrack == null) return

        playbackExecutor.execute {
            if (!audioEngine.isPlaying()) {
                if (requestAudioFocus()) {
                    if (audioEngine.play()) {
                        wasPlaying = true
                        fadeIn(150L)
                        handler.post {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    startForeground(
                                        NOTIFICATION_ID,
                                        buildNotification(),
                                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                                    )
                                } else {
                                    startForeground(NOTIFICATION_ID, buildNotification())
                                }
                            } catch (_: Exception) {}
                            updateMediaSessionState(PlaybackState.STATE_PLAYING, audioEngine.getPositionMs())
                            notifyState(true)
                            startProgressUpdates()
                        }
                    }
                }
            }
        }
    }

    fun pause() {
        if (audioEngine.isPlaying()) {
            fadeOut(150L) {
                audioEngine.pause()
                wasPlaying = false
                updateMediaSessionState(PlaybackState.STATE_PAUSED, audioEngine.getPositionMs())
                notifyState(false)
                showNotification()
                saveCurrentState()
            }
        }
    }

    fun togglePlayPause() {
        if (audioEngine.isPlaying()) {
            pause()
        } else {
            play()
        }
    }

    fun stop() {
        fadeOut(100L) {
            audioEngine.stop()
            wasPlaying = false
            abandonAudioFocus()
            updateMediaSessionState(PlaybackState.STATE_STOPPED, 0)
            notifyState(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            saveCurrentState()
        }
    }

    fun playNext(auto: Boolean = false) {
        val nextTrack = queueManager.next()
        if (nextTrack != null) {
            notifyQueue()
            val crossfadeMs = preferencesManager.getCrossfadeDurationMs()
            if (!auto && crossfadeMs > 0 && audioEngine.isPlaying()) {
                val fadeTime = (crossfadeMs / 2).coerceIn(100, 1000).toLong()
                fadeOut(fadeTime) {
                    playTrack(nextTrack)
                }
            } else {
                playTrack(nextTrack)
            }
        } else {
            if (auto) {
                stop()
            }
        }
    }

    fun playPrevious() {
        if (audioEngine.getPositionMs() > 3000) {
            seekTo(0)
            return
        }

        val prevTrack = queueManager.previous()
        if (prevTrack != null) {
            notifyQueue()
            val crossfadeMs = preferencesManager.getCrossfadeDurationMs()
            if (crossfadeMs > 0 && audioEngine.isPlaying()) {
                val fadeTime = (crossfadeMs / 2).coerceIn(100, 1000).toLong()
                fadeOut(fadeTime) {
                    playTrack(prevTrack)
                }
            } else {
                playTrack(prevTrack)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        playbackExecutor.execute {
            audioEngine.seekToMs(positionMs)
            handler.post {
                updateMediaSessionState(
                    if (audioEngine.isPlaying()) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs
                )
                notifyProgress(positionMs, audioEngine.getDurationMs())
            }
        }
    }

    // --- Fades ---

    private fun fadeOut(durationMs: Long, onComplete: () -> Unit) {
        val steps = 12
        val stepDelay = (durationMs / steps).coerceAtLeast(5L)
        var currentStep = 0
        val fadeHandler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                currentStep++
                val vol = 1.0f - (currentStep.toFloat() / steps.toFloat())
                audioEngine.setVolume(vol.coerceIn(0.0f, 1.0f))
                if (currentStep < steps) {
                    fadeHandler.postDelayed(this, stepDelay)
                } else {
                    onComplete()
                    audioEngine.setVolume(1.0f)
                }
            }
        }
        fadeHandler.post(runnable)
    }

    private fun fadeIn(durationMs: Long) {
        audioEngine.setVolume(0.0f)
        val steps = 12
        val stepDelay = (durationMs / steps).coerceAtLeast(5L)
        var currentStep = 0
        val fadeHandler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                currentStep++
                val vol = currentStep.toFloat() / steps.toFloat()
                audioEngine.setVolume(vol.coerceIn(0.0f, 1.0f))
                if (currentStep < steps) {
                    fadeHandler.postDelayed(this, stepDelay)
                } else {
                    audioEngine.setVolume(1.0f)
                }
            }
        }
        fadeHandler.post(runnable)
    }

    // --- Sleep Timer ---

    fun startSleepTimer(minutes: Int, stopAtTrackEnd: Boolean = false) {
        sleepTimerStopAtTrackEnd = stopAtTrackEnd
        val totalMs = minutes * 60 * 1000L
        sleepTimerEndTimestamp = System.currentTimeMillis() + totalMs
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable)
        sleepTimerHandler.post(sleepTimerRunnable)
    }

    fun cancelSleepTimer() {
        sleepTimerEndTimestamp = 0L
        sleepTimerStopAtTrackEnd = false
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable)
        audioEngine.setVolume(1.0f)
    }

    fun getSleepTimerRemainingSeconds(): Int {
        if (sleepTimerEndTimestamp == 0L) return 0
        val remaining = (sleepTimerEndTimestamp - System.currentTimeMillis()) / 1000L
        return remaining.coerceAtLeast(0).toInt()
    }

    // --- Priority Queue Methods ---

    fun enqueueNext(track: Track) {
        queueManager.enqueueNext(track)
        notifyQueue()
    }

    fun enqueueEnd(track: Track) {
        queueManager.enqueueEnd(track)
        notifyQueue()
    }

    fun clearPriorityQueue() {
        queueManager.clearPriorityQueue()
        notifyQueue()
    }

    // --- Shuffle & Repeat Modes ---

    fun toggleShuffle(): Boolean {
        val mode = queueManager.toggleShuffle()
        preferencesManager.saveShuffle(mode != ShuffleMode.OFF)
        notifyQueue()
        return mode != ShuffleMode.OFF
    }

    fun toggleShuffleMode(): ShuffleMode {
        val mode = queueManager.toggleShuffle()
        preferencesManager.saveShuffle(mode != ShuffleMode.OFF)
        notifyQueue()
        return mode
    }

    fun toggleRepeat(): RepeatMode {
        val mode = queueManager.toggleRepeat()
        preferencesManager.saveRepeatMode(mode.value)
        return mode
    }

    // --- DSP Equalizer & Tone Controls ---

    fun setEqualizerEnabled(enabled: Boolean) {
        audioEngine.setEqualizerEnabled(enabled)
        preferencesManager.saveEqualizerEnabled(enabled)
    }

    fun isEqualizerEnabled(): Boolean = audioEngine.isEqualizerEnabled()

    fun setEqualizerBand(band: Int, gainDb: Float) {
        audioEngine.setEqualizerBand(band, gainDb)
        preferencesManager.saveEqualizerBand(band, gainDb)
    }

    fun getEqualizerBand(band: Int): Float = audioEngine.getEqualizerBand(band)

    fun setPreamp(gainDb: Float) {
        preferencesManager.savePreamp(gainDb)
        queueManager.currentTrack?.let { applyEffectivePreamp(it) } ?: audioEngine.setPreamp(gainDb)
    }

    fun getPreamp(): Float = preferencesManager.getPreamp()

    fun applyEffectivePreamp(track: Track) {
        val userPreamp = preferencesManager.getPreamp()
        val rgMode = preferencesManager.getReplayGainMode()
        val rgGain = when (rgMode) {
            1 -> { // Track Gain
                if (track.replayGainTrackGain != 0.0f) {
                    track.replayGainTrackGain + preferencesManager.getReplayGainPreampWithRg()
                } else {
                    preferencesManager.getReplayGainPreampWithoutRg()
                }
            }
            2 -> { // Album Gain
                if (track.replayGainAlbumGain != 0.0f) {
                    track.replayGainAlbumGain + preferencesManager.getReplayGainPreampWithRg()
                } else {
                    preferencesManager.getReplayGainPreampWithoutRg()
                }
            }
            else -> 0.0f
        }
        val effective = (userPreamp + rgGain).coerceIn(-12.0f, 12.0f)
        audioEngine.setPreamp(effective)
    }

    fun setReplayGainMode(mode: Int) {
        preferencesManager.saveReplayGainMode(mode)
        queueManager.currentTrack?.let { applyEffectivePreamp(it) }
    }

    fun getReplayGainMode(): Int = preferencesManager.getReplayGainMode()

    fun setBass(gainDb: Float) {
        audioEngine.setBass(gainDb)
        preferencesManager.saveBass(gainDb)
    }

    fun getBass(): Float = audioEngine.getBass()

    fun setTreble(gainDb: Float) {
        audioEngine.setTreble(gainDb)
        preferencesManager.saveTreble(gainDb)
    }

    fun getTreble(): Float = audioEngine.getTreble()

    fun setToneEnabled(enabled: Boolean) {
        audioEngine.setToneEnabled(enabled)
        preferencesManager.saveToneEnabled(enabled)
    }

    fun isToneEnabled(): Boolean = audioEngine.isToneEnabled()

    fun setBalance(balance: Float) {
        audioEngine.setBalance(balance)
        preferencesManager.saveBalance(balance)
    }

    fun getBalance(): Float = audioEngine.getBalance()

    fun setMono(mono: Boolean) {
        audioEngine.setMono(mono)
        preferencesManager.saveMono(mono)
    }

    fun isMono(): Boolean = audioEngine.isMono()

    fun setCrossfadeDurationMs(ms: Int) {
        preferencesManager.saveCrossfadeDurationMs(ms)
    }

    fun getCrossfadeDurationMs(): Int = preferencesManager.getCrossfadeDurationMs()

    private fun startProgressUpdates() {
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    private fun notifyTrack(track: Track?) {
        listeners.forEach { it.onTrackChanged(track) }
    }

    private fun notifyState(isPlaying: Boolean) {
        listeners.forEach { it.onPlaybackStateChanged(isPlaying) }
    }

    private fun notifyProgress(currentMs: Long, durationMs: Long) {
        listeners.forEach { it.onProgressUpdate(currentMs, durationMs) }
    }

    private fun notifyQueue() {
        val tracks = queueManager.getTracks()
        val index = queueManager.currentQueueIndex
        listeners.forEach { it.onQueueUpdated(tracks, index) }
    }

    private fun saveCurrentState() {
        val ids = queueManager.getTracks().map { it.id }
        preferencesManager.saveQueue(ids, queueManager.currentQueueIndex, audioEngine.getPositionMs())
    }

    // --- MediaSession setup ---

    private var mediaButtonClickCount = 0
    private val mediaButtonHandler = Handler(Looper.getMainLooper())
    private val mediaButtonRunnable = Runnable {
        when (mediaButtonClickCount) {
            1 -> togglePlayPause()
            2 -> playNext()
            3 -> playPrevious()
        }
        mediaButtonClickCount = 0
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "PowerampCleanSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = this@PlaybackService.play()
                override fun onPause() = this@PlaybackService.pause()
                override fun onSkipToNext() = this@PlaybackService.playNext()
                override fun onSkipToPrevious() = this@PlaybackService.playPrevious()
                override fun onSeekTo(pos: Long) = this@PlaybackService.seekTo(pos)
                override fun onStop() = this@PlaybackService.stop()

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }

                    if (event != null && event.action == KeyEvent.ACTION_DOWN) {
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_HEADSETHOOK,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                mediaButtonClickCount++
                                mediaButtonHandler.removeCallbacks(mediaButtonRunnable)
                                if (mediaButtonClickCount >= 3) {
                                    mediaButtonRunnable.run()
                                } else {
                                    mediaButtonHandler.postDelayed(mediaButtonRunnable, 320)
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                playNext()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                playPrevious()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                play()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                pause()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_STOP -> {
                                stop()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }
    }

    private fun updateMediaSessionMetadata(track: Track) {
        val baseMeta = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs)
            .build()
        mediaSession.setMetadata(baseMeta)

        // Asynchronously load artwork for Android 13/14/15/16 Quick Settings control center
        CoverArtLoader.loadCover(this, track, 512) { bitmap ->
            if (bitmap != null && queueManager.currentTrack?.id == track.id) {
                currentCoverBitmap = bitmap
                val fullMeta = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs)
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                    .putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
                    .build()
                mediaSession.setMetadata(fullMeta)
                showNotification()
            }
        }
    }

    private fun updateMediaSessionState(state: Int, positionMs: Long) {
        val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP

        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setState(state, positionMs, 1.0f)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    // --- Audio Focus Handling ---

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attr)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this, handler)
                .build()

            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }

        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
        hasAudioFocus = false
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                if (audioEngine.isPlaying()) {
                    pausedByTransientLoss = true
                    audioEngine.pause()
                    notifyState(false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                audioEngine.setVolume(0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                audioEngine.setVolume(1.0f)
                if (pausedByTransientLoss) {
                    pausedByTransientLoss = false
                    audioEngine.play()
                    notifyState(true)
                }
            }
        }
    }

    // --- Notifications & Control Center ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reprodução de Música",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reprodução do player"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val track = queueManager.currentTrack
        val isPlaying = audioEngine.isPlaying()

        val mainIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPending = PendingIntent.getService(
            this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_TOGGLE_PLAY_PAUSE }
        val togglePending = PendingIntent.getService(
            this, 2, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(
            this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track?.title ?: "Música")
            .setContentText(track?.artist ?: "Player")
            .setSubText(track?.album)
            .setContentIntent(contentPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Anterior", prevPending).build())
            .addAction(Notification.Action.Builder(playPauseIcon, if (isPlaying) "Pausar" else "Tocar", togglePending).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "Próxima", nextPending).build())
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        currentCoverBitmap?.let {
            builder.setLargeIcon(it)
        }

        return builder.build()
    }

    private fun showNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        const val CHANNEL_ID = "player_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.lucas.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.lucas.player.ACTION_PAUSE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.lucas.player.ACTION_TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT = "com.lucas.player.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.lucas.player.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.lucas.player.ACTION_STOP"
    }
}
