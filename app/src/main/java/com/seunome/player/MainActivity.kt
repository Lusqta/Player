package com.seunome.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaController: MediaControllerCompat? = null
    private val songs = mutableListOf<Song>()
    
    private lateinit var listView: ListView
    private lateinit var btnPlayPause: ImageButton

    private lateinit var tvSongTitle: TextView
    private lateinit var tvSongArtist: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.listView)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        tvSongTitle = findViewById(R.id.tvSongTitle)
        tvSongArtist = findViewById(R.id.tvSongArtist)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MediaService::class.java),
            connectionCallback,
            null
        )

        checkPermissions()

        btnPlayPause.setOnClickListener {
            val state = mediaController?.playbackState?.state
            if (state == PlaybackStateCompat.STATE_PLAYING) {
                mediaController?.transportControls?.pause()
            } else {
                mediaController?.transportControls?.play()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    override fun onStop() {
        super.onStop()
        mediaController?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
        progressHandler.removeCallbacks(updateProgressAction)
    }

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaBrowser.sessionToken.let { token ->
                mediaController = MediaControllerCompat(this@MainActivity, token).apply {
                    registerCallback(controllerCallback)
                }
                updatePlayPauseState(mediaController?.playbackState)
                updateMetadataUI(mediaController?.metadata)
                progressHandler.post(updateProgressAction)
            }
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            updatePlayPauseState(state)
        }
        override fun onMetadataChanged(metadata: android.support.v4.media.MediaMetadataCompat?) {
            updateMetadataUI(metadata)
        }
    }

    private fun updateMetadataUI(metadata: android.support.v4.media.MediaMetadataCompat?) {
        metadata?.let {
            tvSongTitle.text = it.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE) ?: "Desconhecido"
            tvSongArtist.text = it.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "Artista Desconhecido"
        }
    }

    private fun updatePlayPauseState(state: PlaybackStateCompat?) {
        if (state?.state == PlaybackStateCompat.STATE_PLAYING) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun checkPermissions() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 101)
        } else {
            loadSongs()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        }
    }

    private fun loadSongs() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (${MediaStore.Audio.Media.DATA} LIKE '%.mp3' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac')"
        
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.TITLE + " ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol)
                // O decodificador em C lê através do caminho final do arquivo, não da URI do MediaStore diretamente
                songs.add(
                    Song(
                        id = cursor.getLong(idCol),
                        uri = path,
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown",
                        duration = cursor.getLong(durCol)
                    )
                )
            }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_2, android.R.id.text1, songs.map { it.title })
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val song = songs[position]
            val bundle = Bundle().apply {
                putString("title", song.title)
                putString("artist", song.artist)
                putLong("duration", song.duration)
            }
            mediaController?.transportControls?.playFromMediaId(song.uri, bundle)
        }
    }

    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateProgressAction = object : Runnable {
        override fun run() {
            mediaController?.let { controller ->
                val state = controller.playbackState
                if (state != null && state.state == PlaybackStateCompat.STATE_PLAYING) {
                    val positionMs = state.position // Retorna a última posição relatada, seria legal o Oboe atualizar isso lá embaixo em C.
                    // tvCurrentTime.text = formatTime(positionMs)
                    // seekBar.progress = positionMs.toInt()
                }
            }
            // Chama a si mesmo a cada 1 segundo (se estivesse tocando de fato do C++ pro Kotlin)
            progressHandler.postDelayed(this, 1000)
        }
    }
}
