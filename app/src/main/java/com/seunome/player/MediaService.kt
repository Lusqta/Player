package com.seunome.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat

class MediaService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private val engine = AudioEngine()
    private var isPlaying = false
    private var currentSongUri: String? = null

    override fun onCreate() {
        super.onCreate()
        
        engine.initEngine()

        mediaSession = MediaSessionCompat(this, "MediaService").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(MediaSessionCallback())
            setSessionToken(sessionToken)
            isActive = true
        }
        
        updatePlaybackState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Exige o Foreground Imediato para não quebrar a regra de 5s do Android O+
        val channelId = "PlayerChannel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, "Media Playback", NotificationManager.IMPORTANCE_LOW)
                manager.createNotificationChannel(channel)
            }
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Iniciando Motor de Áudio")
            .setContentText("Aguarde...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }
        
        return START_STICKY
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(mutableListOf()) // Carga feita via MainActivity diretamente para simplicidade
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            mediaId?.let { uri ->
                if (currentSongUri != uri) {
                    currentSongUri = uri
                    val title = extras?.getString("title") ?: "Desconhecido"
                    val artist = extras?.getString("artist") ?: "Artista Desconhecido"
                    val duration = extras?.getLong("duration") ?: 0L
                    
                    updateMetadata(uri, title, artist, duration)
                    engine.playFile(uri)
                } else {
                    engine.resume()
                }
                isPlaying = true
                updatePlaybackState()
                showNotification()
            }
        }

        override fun onPlay() {
            if (currentSongUri != null) {
                engine.resume()
                isPlaying = true
                updatePlaybackState()
                showNotification()
            }
        }

        override fun onPause() {
            engine.pause()
            isPlaying = false
            updatePlaybackState()
            showNotification()
            // Parar o foreground não significa matar o serviço, apenas tirar a prioridade
            stopForeground(false)
        }

        override fun onStop() {
            engine.stop()
            isPlaying = false
            updatePlaybackState()
            stopForeground(true)
            stopSelf()
        }

        override fun onSeekTo(pos: Long) {
            engine.seek(pos)
            updatePlaybackState()
        }
    }

    private fun updatePlaybackState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val position = engine.getPosition()
        
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, position, 1.0f)
                .build()
        )
    }

    private fun updateMetadata(uri: String, title: String, artist: String, duration: Long) {
        val metadataBuilder = android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(uri)
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
                metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            }
            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
            
        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun showNotification() {
        val channelId = "PlayerChannel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, "Media Playback", NotificationManager.IMPORTANCE_LOW)
                manager.createNotificationChannel(channel)
            }
        }

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", null)
        } else {
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", null)
        }

        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata?.description

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(description?.title ?: "Unknown Title")
            .setContentText(description?.subtitle ?: "Unknown Artist")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0)) // O índice do botão play/pause (0) irá aparecer resumido
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.destroyEngine()
        mediaSession.isActive = false
        mediaSession.release()
    }
}
