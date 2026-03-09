package com.seunome.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaController: MediaControllerCompat? = null
    private val songs = mutableListOf<Song>()
    private var isDraggingSeekBar = false
    
    private lateinit var listView: ListView
    private lateinit var miniPlayerContainer: android.widget.LinearLayout
    private lateinit var fullPlayerContainer: android.widget.LinearLayout
    private lateinit var btnClosePlayer: ImageButton
    private lateinit var btnSelectFolder: ImageButton
    private lateinit var loadingIndicator: ProgressBar
    
    // File Observer para Live Sync
    private var folderObserver: FileObserver? = null
    
    // ActivityResultLauncher para a API SAF
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            // Salvar no SharedPrefs
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("last_folder_uri", uri.toString()).apply()
            
            syncFolderToDatabase(uri)
        }
    }
    
    // Mini Player Views
    private lateinit var tvMiniSongTitle: TextView
    private lateinit var tvMiniSongArtist: TextView
    private lateinit var miniAlbumArt: android.widget.ImageView
    private lateinit var btnMiniPlayPause: ImageButton
    private lateinit var btnMiniNext: ImageButton
    
    // Full Player Views
    private lateinit var tvFullSongTitle: TextView
    private lateinit var tvFullSongArtist: TextView
    private lateinit var fullAlbumArt: android.widget.ImageView
    
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnQueue: ImageButton
    
    // Bottom Sheet Queue
    private lateinit var bottomSheetQueue: android.widget.LinearLayout
    private lateinit var btnCloseQueue: ImageButton
    private lateinit var queueListView: ListView

    private lateinit var seekBar: android.widget.SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.listView)
        
        miniPlayerContainer = findViewById(R.id.miniPlayerContainer)
        fullPlayerContainer = findViewById(R.id.fullPlayerContainer)
        btnClosePlayer = findViewById(R.id.btnClosePlayer)
        
        tvMiniSongTitle = findViewById(R.id.tvMiniSongTitle)
        tvMiniSongArtist = findViewById(R.id.tvMiniSongArtist)
        miniAlbumArt = findViewById(R.id.miniAlbumArt)
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause)
        btnMiniNext = findViewById(R.id.btnMiniNext)
        
        tvFullSongTitle = findViewById(R.id.tvFullSongTitle)
        tvFullSongArtist = findViewById(R.id.tvFullSongArtist)
        fullAlbumArt = findViewById(R.id.fullAlbumArt)
        
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnRepeat = findViewById(R.id.btnRepeat)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnQueue = findViewById(R.id.btnQueue)
        
        bottomSheetQueue = findViewById(R.id.bottomSheetQueue)
        btnCloseQueue = findViewById(R.id.btnCloseQueue)
        queueListView = findViewById(R.id.queueListView)
        
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MediaService::class.java),
            connectionCallback,
            null
        )

        checkPermissions()

        miniPlayerContainer.setOnClickListener {
            fullPlayerContainer.visibility = android.view.View.VISIBLE
            miniPlayerContainer.visibility = android.view.View.GONE
        }
        
        btnSelectFolder = findViewById(R.id.btnSelectFolder)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        
        btnSelectFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }
        
        btnClosePlayer.setOnClickListener {
            fullPlayerContainer.visibility = android.view.View.GONE
            miniPlayerContainer.visibility = android.view.View.VISIBLE
        }
        
        btnQueue.setOnClickListener {
            bottomSheetQueue.visibility = android.view.View.VISIBLE
            updateQueueUI()
        }
        
        btnCloseQueue.setOnClickListener {
            bottomSheetQueue.visibility = android.view.View.GONE
        }
        
        queueListView.setOnItemClickListener { parent, view, position, id ->
            val song = parent.getItemAtPosition(position) as Song
            mediaController?.transportControls?.skipToQueueItem(song.id)
            // Fecha a fila ao escolher (opcional, pode deixar aberta. Vamos deixar aberta e só atualizar)
            // bottomSheetQueue.visibility = android.view.View.GONE 
        }
        
        val playPauseListener = android.view.View.OnClickListener {
            val state = mediaController?.playbackState?.state
            if (state == PlaybackStateCompat.STATE_PLAYING) {
                mediaController?.transportControls?.pause()
            } else {
                mediaController?.transportControls?.play()
            }
        }
        btnPlayPause.setOnClickListener(playPauseListener)
        btnMiniPlayPause.setOnClickListener(playPauseListener)
        
        val nextListener = android.view.View.OnClickListener {
            mediaController?.transportControls?.skipToNext()
        }
        btnNext.setOnClickListener(nextListener)
        btnMiniNext.setOnClickListener(nextListener)

        btnPrev.setOnClickListener {
            mediaController?.transportControls?.skipToPrevious()
        }

        btnShuffle.setOnClickListener {
            val isShuffling = mediaController?.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL
            val newMode = if (isShuffling) PlaybackStateCompat.SHUFFLE_MODE_NONE else PlaybackStateCompat.SHUFFLE_MODE_ALL
            mediaController?.transportControls?.setShuffleMode(newMode)
            updateShuffleRepeatUI(newMode, mediaController?.repeatMode)
        }

        btnRepeat.setOnClickListener {
            val currentMode = mediaController?.repeatMode ?: PlaybackStateCompat.REPEAT_MODE_NONE
            val newMode = when (currentMode) {
                PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
                PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
                else -> PlaybackStateCompat.REPEAT_MODE_NONE
            }
            mediaController?.transportControls?.setRepeatMode(newMode)
            updateShuffleRepeatUI(mediaController?.shuffleMode, newMode)
        }
        
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                isDraggingSeekBar = true
            }
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                seekBar?.let { mediaController?.transportControls?.seekTo(it.progress.toLong()) }
                isDraggingSeekBar = false
            }
        })
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.startForegroundService(this, android.content.Intent(this, MediaService::class.java))
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
                updateShuffleRepeatUI(mediaController?.shuffleMode, mediaController?.repeatMode)
                progressHandler.post(updateProgressAction)
                
                // --- RESTAURAR ESTADO SALVO (FASE 9) ---
                val state = mediaController?.playbackState
                val isServiceActive = state != null && (state.state == PlaybackStateCompat.STATE_PLAYING || state.state == PlaybackStateCompat.STATE_PAUSED)
                
                if (!isServiceActive) {
                    val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val lastSongId = prefs.getLong("last_song_id", -1L)
                    val queueStr = prefs.getString("last_original_queue", "") ?: ""
                    
                    if (lastSongId != -1L && queueStr.isNotEmpty()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val ids = queueStr.split(",").mapNotNull { it.toLongOrNull() }
                            val dao = AppDatabase.getDatabase(this@MainActivity).songDao()
                            val allSongsRaw = dao.getAllSongs()
                            val allSongs = allSongsRaw.associateBy { it.id }
                            
                            val restoredQueue = ids.mapNotNull { allSongs[it]?.toSong() }
                            
                            if (restoredQueue.isNotEmpty()) {
                                val activeIndex = restoredQueue.indexOfFirst { it.id == lastSongId }
                                if (activeIndex != -1) {
                                    val titles = restoredQueue.map { it.title }.toTypedArray()
                                    val artists = restoredQueue.map { it.artist }.toTypedArray()
                                    val uris = restoredQueue.map { it.uri }.toTypedArray()
                                    val durations = restoredQueue.map { it.duration }.toLongArray()
                                    val outIds = restoredQueue.map { it.id }.toLongArray()
                                    
                                    val isShuffle = prefs.getBoolean("last_shuffle_mode", false)
                                    val isRepeat = prefs.getInt("last_repeat_mode", 0)
                                    val position = prefs.getLong("last_position", 0L)
                                    
                                    val bundle = Bundle().apply {
                                        putStringArray("titles", titles)
                                        putStringArray("artists", artists)
                                        putStringArray("uris", uris)
                                        putLongArray("durations", durations)
                                        putLongArray("ids", outIds)
                                        putInt("start_index", activeIndex)
                                        putBoolean("is_restore", true)
                                        putLong("start_position", position)
                                        putBoolean("shuffle_mode", isShuffle)
                                        putInt("repeat_mode", isRepeat)
                                    }
                                    
                                    withContext(Dispatchers.Main) {
                                        // Destrói as chaves pra só restaurar isso uma vez
                                        prefs.edit().remove("last_song_id").apply()
                                        mediaController?.transportControls?.sendCustomAction("UPDATE_QUEUE", bundle)
                                        miniPlayerContainer.visibility = android.view.View.VISIBLE
                                    }
                                }
                            }
                        }
                    } else if (mediaController?.metadata != null) {
                        miniPlayerContainer.visibility = android.view.View.VISIBLE
                    }
                } else if (mediaController?.metadata != null) {
                    miniPlayerContainer.visibility = android.view.View.VISIBLE
                }
                
                Unit // Previne o kotlin de tratar o if como retorno expression do .let
            }
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            updatePlayPauseState(state)
        }
        override fun onMetadataChanged(metadata: android.support.v4.media.MediaMetadataCompat?) {
            updateMetadataUI(metadata)
            // Se a música trocou e a fila estive aberta, reconstruir cores
            if (bottomSheetQueue.visibility == android.view.View.VISIBLE) {
                updateQueueUI() 
            }
        }
        override fun onShuffleModeChanged(shuffleMode: Int) {
            updateShuffleRepeatUI(shuffleMode, mediaController?.repeatMode)
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            updateShuffleRepeatUI(mediaController?.shuffleMode, repeatMode)
        }
        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            super.onQueueChanged(queue)
            if (bottomSheetQueue.visibility == android.view.View.VISIBLE) {
                updateQueueUI()
            }
        }
    }

    private fun updateQueueUI() {
        val queue = mediaController?.queue
        if (queue == null || queue.isEmpty()) return
        
        val activeQueueId = mediaController?.playbackState?.activeQueueItemId ?: -1L
        var activeIndex = -1
        
        val displaySongs = mutableListOf<Song>()
        queue.forEachIndexed { index, item -> 
            val desc = item.description
            if (item.queueId == activeQueueId) activeIndex = index
            
            displaySongs.add(
                Song(
                    id = item.queueId,
                    title = desc.title?.toString() ?: "Unknown",
                    artist = desc.subtitle?.toString() ?: "Unknown",
                    uri = "",
                    duration = 0L
                )
            )
        }
        
        val adapter = QueueAdapter(this, displaySongs, activeIndex)
        queueListView.adapter = adapter
        
        // Rolamos a lista pra mostrar a música atual, não o começo do histórico (a não ser que index = 0)
        if (activeIndex > 0) {
            queueListView.setSelection(activeIndex - 1) 
        }
    }

    private fun updateMetadataUI(metadata: android.support.v4.media.MediaMetadataCompat?) {
        metadata?.let {
            val title = it.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE) ?: "Desconhecido"
            val artist = it.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "Artista Desconhecido"
            
            tvMiniSongTitle.text = title
            tvFullSongTitle.text = title
            tvMiniSongArtist.text = artist
            tvFullSongArtist.text = artist
            
            val duration = it.getLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION)
            tvTotalTime.text = formatTime(duration)
            seekBar.max = duration.toInt()
            
            val bitmap = it.getBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            if (bitmap != null) {
                miniAlbumArt.setImageBitmap(bitmap)
                miniAlbumArt.setPadding(0, 0, 0, 0)
                fullAlbumArt.setImageBitmap(bitmap)
                fullAlbumArt.setPadding(0, 0, 0, 0)
            } else {
                miniAlbumArt.setImageResource(R.drawable.ic_music_note)
                miniAlbumArt.setPadding(8, 8, 8, 8)
                fullAlbumArt.setImageResource(R.drawable.ic_music_note)
                val pad = (40 * resources.displayMetrics.density).toInt()
                fullAlbumArt.setPadding(pad, pad, pad, pad)
            }
        }
    }
    
    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun updatePlayPauseState(state: PlaybackStateCompat?) {
        if (state?.state == PlaybackStateCompat.STATE_PLAYING) {
            btnPlayPause.setImageResource(R.drawable.ic_pause)
            btnMiniPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play)
            btnMiniPlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun updateShuffleRepeatUI(shuffleMode: Int?, repeatMode: Int?) {
        val shuffleTint = if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL) "#1DB954" else "#AAAAAA"
        btnShuffle.setColorFilter(android.graphics.Color.parseColor(shuffleTint))

        when (repeatMode) {
            PlaybackStateCompat.REPEAT_MODE_NONE -> {
                btnRepeat.setImageResource(R.drawable.ic_repeat)
                btnRepeat.setColorFilter(android.graphics.Color.parseColor("#AAAAAA"))
            }
            PlaybackStateCompat.REPEAT_MODE_ALL -> {
                btnRepeat.setImageResource(R.drawable.ic_repeat)
                btnRepeat.setColorFilter(android.graphics.Color.parseColor("#1DB954"))
            }
            PlaybackStateCompat.REPEAT_MODE_ONE -> {
                btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                btnRepeat.setColorFilter(android.graphics.Color.parseColor("#1DB954"))
            }
        }
    }

    private fun checkPermissions() {
        val permsNeeded = mutableListOf<String>()
        
        val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, audioPerm) != PackageManager.PERMISSION_GRANTED) {
            permsNeeded.add(audioPerm)
        }
        
        // Android 13+ exige permissão explícita para notificações
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permsNeeded.toTypedArray(), 101)
        } else {
            startupRoutines()
        }
    }
    
    private fun startupRoutines() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastUriStr = prefs.getString("last_folder_uri", null)
        
        // 1. Liga a UI ao Banco de Dados pra sempre
        observeDatabaseFlow()
        
        // 2. Tenta fazer um Quick Sync Se Tiver Pasta Salva
        if (lastUriStr != null) {
            try {
                val uri = Uri.parse(lastUriStr)
                syncFolderToDatabase(uri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun observeDatabaseFlow() {
        val dao = AppDatabase.getDatabase(this).songDao()
        lifecycleScope.launch {
            dao.getAllSongsFlow().collect { songEntities ->
                // Sempre que o DB sofre INSERT/DELETE, ele cai aqui
                songs.clear()
                songs.addAll(songEntities.map { it.toSong() })
                
                val adapter = MainSongAdapter(this@MainActivity, songs)
                listView.adapter = adapter
                setupListViewClicks()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Pode abrir a pasta direto ou vazio aguardando ação
        }
    }
    
    // Sincronizador Diferencial de Arquivos SAF -> DB Room
    private fun syncFolderToDatabase(treeUri: Uri) {
        // Liga o feedback Visual 
        loadingIndicator.visibility = android.view.View.VISIBLE
        btnSelectFolder.visibility = android.view.View.GONE
        
        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(this@MainActivity).songDao()
            
            withContext(Dispatchers.IO) {
                val rootFolder = DocumentFile.fromTreeUri(this@MainActivity, treeUri)
                if (rootFolder != null && rootFolder.isDirectory) {
                    val diskPaths = mutableSetOf<String>()
                    val existingSongsInDb = dao.getAllSongs() // Lista Snapshot
                    
                    // 1. Escanear e Inserir Novos
                    scanAndInsertNewAudio(rootFolder, diskPaths, dao, existingSongsInDb)
                    
                    // 2. Identificar os Arquivos Que Sumiram do Disco e Limpar do DB
                    val existingPathsInDb = existingSongsInDb.map { it.realPath }.toSet()
                    val ghostPaths = existingPathsInDb - diskPaths
                    
                    for (ghost in ghostPaths) {
                        dao.deleteSongByPath(ghost)
                    }
                }
            }
            
            // Fim do Processamento Pesado
            loadingIndicator.visibility = android.view.View.GONE
            btnSelectFolder.visibility = android.view.View.VISIBLE
            
            // Instala o Olheiro de Eventos para Sync em Tempo Real (Create ou Delete) se a Thread Pai Morrer
            val realPathRoot = getRealPathFromURI(this@MainActivity, treeUri)
            if (realPathRoot != null && folderObserver == null) {
                folderObserver = object : FileObserver(realPathRoot, FileObserver.CREATE or FileObserver.DELETE) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path != null && (path.endsWith(".mp3", true) || path.endsWith(".flac", true))) {
                            // Se uma música cair ou for deletada da pasta, manda o Quick Sync rodar de novo
                            syncFolderToDatabase(treeUri)
                        }
                    }
                }
                folderObserver?.startWatching()
            }
        }
    }

    private suspend fun scanAndInsertNewAudio(folder: DocumentFile, diskPaths: MutableSet<String>, dao: SongDao, cachedDbSongs: List<SongEntity>) {
        val files = folder.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanAndInsertNewAudio(file, diskPaths, dao, cachedDbSongs)
            } else {
                val name = file.name ?: continue
                if (name.endsWith(".mp3", true) || name.endsWith(".flac", true)) {
                    val realPath = getRealPathFromURI(this@MainActivity, file.uri) ?: file.uri.toString()
                    diskPaths.add(realPath)
                    
                    // Se não existir no Banco, Extrai Tags Pesadas e Insere
                    if (cachedDbSongs.none { it.realPath == realPath }) {
                        val uriStr = file.uri.toString()
                        var title = name.substringBeforeLast(".")
                        var duration = 0L
                        var artist = "Desconhecido"
                        
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(this@MainActivity, file.uri)
                            val metaTitle = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                            val metaArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            val metaDuration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            
                            if (!metaTitle.isNullOrEmpty()) title = metaTitle
                            if (!metaArtist.isNullOrEmpty()) artist = metaArtist
                            if (!metaDuration.isNullOrEmpty()) duration = metaDuration.toLongOrNull() ?: 0L
                            
                            retriever.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        // Hash pro ID pra evitar problemas
                        val idHash = realPath.hashCode().toLong()
                        val newSong = SongEntity(id = idHash, uriStr = uriStr, title = title, artist = artist, duration = duration, realPath = realPath)
                        dao.insertSong(newSong)
                    }
                }
            }
        }
    }

    private fun loadSongs() {
        // Se quisermos carregar uma varredura padrão do celular...
        // ... (Será substituído pelo loadSongsFromFolder)
    }
    
    private fun getRealPathFromURI(context: Context, uri: Uri): String? {
        if ("content" == uri.scheme) {
            if ("com.android.externalstorage.documents" == uri.authority) {
                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return android.os.Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
            } else {
                // Tenta resolver Uris diretas do MediaStore antigo
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                var cursor: android.database.Cursor? = null
                try {
                    cursor = context.contentResolver.query(uri, projection, null, null, null)
                    if (cursor != null && cursor.moveToFirst()) {
                        val column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        return cursor.getString(column_index)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    cursor?.close()
                }
            }
        } else if ("file" == uri.scheme) {
            return uri.path
        }
        return null
    }

    // Função separada pra reaproveitar o setup dos botões da lista e bundle Queue
    private fun setupListViewClicks() {
        listView.setOnItemClickListener { _, _, position, _ ->
            val song = songs[position]
            val bundle = Bundle().apply {
                putString("title", song.title)
                putString("artist", song.artist)
                putLong("duration", song.duration)
                
                // Manda o ID da playlist pra o player em Background se guiar
                putInt("queue_index", position)
                // Usando JSON GSON ou Parcellable é melhor no futuro, mas por enquanto enviaremos listas simples 
            }
            
            // Hack simples provisório: como não temos banco de dados ainda, vamos empurrar as URIs pra fila do MediaController
            // Atualizar Fila será feito via command action do Controller
            val queueTitles = songs.map { it.title }.toTypedArray()
            val queueArtists = songs.map { it.artist }.toTypedArray()
            val queueUris = songs.map { it.uri }.toTypedArray()
            val queueDurations = songs.map { it.duration }.toLongArray()
            val queueIds = songs.map { it.id }.toLongArray()
            
            val queueBundle = Bundle().apply {
                putStringArray("titles", queueTitles)
                putStringArray("artists", queueArtists)
                putStringArray("uris", queueUris)
                putLongArray("durations", queueDurations)
                putLongArray("ids", queueIds)
                putInt("start_index", position)
            }
            
            mediaController?.transportControls?.sendCustomAction("UPDATE_QUEUE", queueBundle)
            mediaController?.transportControls?.playFromMediaId(song.uri, bundle)
        }
    }

    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateProgressAction = object : Runnable {
        override fun run() {
            mediaController?.let { controller ->
                val state = controller.playbackState
                if (state != null && state.state == PlaybackStateCompat.STATE_PLAYING) {
                    if (!isDraggingSeekBar) {
                        val positionMs = state.position 
                        tvCurrentTime.text = formatTime(positionMs)
                        seekBar.progress = positionMs.toInt()
                    }
                    // Chama a si mesmo apenas quando continuar tocando
                    progressHandler.postDelayed(this, 1000)
                }
            }
        }
    }
}
