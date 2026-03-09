package com.seunome.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaService : MediaBrowserServiceCompat() {

    // Constants State
    companion object {
        const val REPEAT_NONE = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
    }

    private lateinit var mediaSession: MediaSessionCompat
    private val engine = AudioEngine()
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    
    // Playback Variables
    private var isPlaying = false
    private var currentSongUri: String? = null
    
    // Fila Local (Queue) e Modos
    private var originalQueue = listOf<Song>()
    private var historyList = mutableListOf<Song>()
    private var currentSong: Song? = null
    private var upNextList = mutableListOf<Song>()
    
    // Controle da Fila, Aleatório e Repetição
    private var repeatMode = PlaybackStateCompat.REPEAT_MODE_NONE
    private var isShuffleEnabled = false
    
    // Controle de Foco Inteligente
    private var resumeOnFocusGain = false
    
    // Cache de Capas em RAM (Limita a ~15MB) para feedback instantâneo da Notificação
    private val albumArtCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // Usa um oitavo do heap livre (~15-30mb)
        
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                // Tamanho medido em KB no Cache
                return bitmap.byteCount / 1024
            }
        }
    }
    
    // Backup de Estado
    private var saveStateJob: kotlinx.coroutines.Job? = null

    private fun startStateSaver() {
        saveStateJob?.cancel()
        saveStateJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                saveCurrentState()
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    private fun stopStateSaver() {
        saveStateJob?.cancel()
        saveStateJob = null
        saveCurrentState()
    }

    private fun saveCurrentState() {
        val song = currentSong ?: return
        if (originalQueue.isEmpty()) return
        
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val originalIds = originalQueue.map { it.id }.joinToString(",")
        
        prefs.edit()
            .putLong("last_song_id", song.id)
            .putLong("last_position", engine.getPosition())
            .putString("last_original_queue", originalIds)
            .putInt("last_repeat_mode", repeatMode)
            .putBoolean("last_shuffle_mode", isShuffleEnabled)
            .apply()
    }

    override fun onCreate() {
        super.onCreate()
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupAudioFocus()
        
        engine.initEngine()
        engine.setCompletionListener(object : AudioEngine.OnCompletionListener {
            override fun onCompletion() {
                // Roda o callback na Thread principal pois JNI pode vir de background threads
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (isPlaying) {
                        try {
                            mediaSession.controller.transportControls.sendCustomAction("AUTO_NEXT", null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        })
        
        engine.setGaplessListener(object : AudioEngine.OnGaplessListener {
            override fun onGaplessTransition() {
                // O C++ fez a ponte Gapless sozinho! Apenas repassar visualmente pra UI
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (isPlaying) {
                        try {
                            mediaSession.controller.transportControls.sendCustomAction("AUTO_NEXT", null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        })

        mediaSession = MediaSessionCompat(this, "MediaService").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(MediaSessionCallback())
            setSessionToken(sessionToken)
            isActive = true
        }
        
        updatePlaybackState()
    }

    private fun setupAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Perda definitiva (Ex: O usuário deu play no Spotify/YouTube e esqueceu o nosso app)
                resumeOnFocusGain = false
                if (isPlaying) mediaSession.controller.transportControls.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Perda temporária (Ex: Ligação telefônica)
                resumeOnFocusGain = isPlaying // Só volta a tocar de depois se já estivesse tocando antes
                if (isPlaying) mediaSession.controller.transportControls.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Notificação passageira (Ex: Mensagem do WhatsApp)
                // Reduz o volume para 20% em vez de pausar a música toda.
                engine.setVolume(0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Foco recuperado
                engine.setVolume(1.0f) // Restaura volume 100%
                if (resumeOnFocusGain && currentSongUri != null && !isPlaying) {
                    resumeOnFocusGain = false
                    mediaSession.controller.transportControls.play()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                val res = audioManager.requestAudioFocus(it)
                return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } else {
            @Suppress("DEPRECATION")
            val res = audioManager.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return false
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Encaminha botões de mídia (fone, Bluetooth, Android Auto) para a MediaSession
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        
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
        
        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (action == "AUTO_NEXT") {
                handleSkipNext(isAuto = true)
                return
            }
            
            // Quando a Activity passar a lista
            if (action == "UPDATE_QUEUE" && extras != null) {
                val titles = extras.getStringArray("titles") ?: emptyArray()
                val artists = extras.getStringArray("artists") ?: emptyArray()
                val uris = extras.getStringArray("uris") ?: emptyArray()
                val durations = extras.getLongArray("durations") ?: LongArray(0)
                val ids = extras.getLongArray("ids") ?: LongArray(0)
                val startIndex = extras.getInt("start_index", -1)
                
                val isRestore = extras.getBoolean("is_restore", false)
                val startPosition = extras.getLong("start_position", 0L)
                val shuffleRestore = extras.getBoolean("shuffle_mode", false)
                val repeatModeRestore = extras.getInt("repeat_mode", REPEAT_NONE)

                val minSize = minOf(titles.size, artists.size, uris.size, durations.size, ids.size)
                
                val newOriginalQueue = mutableListOf<Song>()
                for (i in 0 until minSize) {
                    val s = Song(ids[i], uris[i], titles[i], artists[i], durations[i])
                    newOriginalQueue.add(s)
                }
                originalQueue = newOriginalQueue.toList()
                
                // Reinicia o estado das 3 listas
                historyList.clear()
                upNextList.clear()
                currentSong = null
                
                if (startIndex != -1 && startIndex < originalQueue.size) {
                    // Histórico (Tudo antes)
                    for (i in 0 until startIndex) historyList.add(originalQueue[i])
                    
                    // A atual
                    currentSong = originalQueue[startIndex]
                    
                    // Fila futura (tudo depois)
                    for (i in (startIndex + 1) until originalQueue.size) upNextList.add(originalQueue[i])
                    
                    // Empurra preload silencioso pra memória das proximas capas pro Gapless/Skip instantâneo
                    preloadNextArtworks()
                    
                    if (isRestore) {
                        isShuffleEnabled = shuffleRestore
                        repeatMode = repeatModeRestore
                        mediaSession.setShuffleMode(if (isShuffleEnabled) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)
                        mediaSession.setRepeatMode(repeatMode)
                    }
                    
                    if (isShuffleEnabled && !isRestore) applyShuffleAlgorithm()
                }
                syncQueueToSession()
                
                // Restauração Silenciosa Mágica (Gapless Visual App Loader)
                if (isRestore && currentSong != null) {
                    val uri = currentSong!!.uri
                    currentSongUri = uri
                    // Mostra as metadatas completas da música congelada
                    updateMetadata(uri, currentSong!!.title, currentSong!!.artist, currentSong!!.duration)
                    // Pede pro C++ carregar o Decoder sem dar Start/Play no som
                    engine.loadOnly(uri)
                    engine.seek(startPosition)
                    
                    isPlaying = false
                    updatePlaybackState()
                }
            }
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            if (!requestAudioFocus()) return
            
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
            if (!requestAudioFocus()) return
            
            if (currentSongUri != null) {
                // Se o currentSong tiver carregado apenas pelo LoadOnly, o resume() tocará a partir dlee
                engine.resume()
                isPlaying = true
                updatePlaybackState()
                showNotification()
                startStateSaver()
            }
        }

        override fun onPause() {
            engine.pause()
            isPlaying = false
            updatePlaybackState()
            showNotification()
            abandonAudioFocus()
            // Parar o foreground não significa matar o serviço, apenas tirar a prioridade
            stopForeground(false)
            stopStateSaver()
        }

        override fun onStop() {
            engine.stop()
            isPlaying = false
            updatePlaybackState()
            abandonAudioFocus()
            stopForeground(true)
            stopStateSaver()
            stopSelf()
        }

        override fun onSeekTo(pos: Long) {
            engine.seek(pos)
            updatePlaybackState()
        }
        
        // Lógica Principal de Pular (Comporta a diferença entre Click Manual vs Fim Automático do Arquivo)
        private fun handleSkipNext(isAuto: Boolean) {
            // Se está em repeat ONE e foi automático, repete a currentSong apenas
            if (isAuto && repeatMode == REPEAT_ONE && currentSong != null) {
                playCurrentSongFromQueue(isGaplessTransiting = isAuto)
                return
            }
            
            // Fila vazia, não há o que pular
            if (upNextList.isEmpty()) {
                if (repeatMode == REPEAT_ALL && originalQueue.isNotEmpty()) {
                    // Reiniciar a fila toda num ciclo completo
                    historyList.clear()
                    upNextList.addAll(originalQueue)
                    currentSong = upNextList.removeAt(0)
                    playCurrentSongFromQueue(isGaplessTransiting = isAuto)
                } else {
                    // Acabou tudo
                    isPlaying = false
                    engine.stop()
                    updatePlaybackState()
                    showNotification()
                }
                return
            }
            
            // Fluxo normal: empurra a atual pro histórico, pega a próxima
            currentSong?.let { historyList.add(it) }
            currentSong = upNextList.removeAt(0)
            
            // Reabastecer o cache na espreita
            preloadNextArtworks()
            
            playCurrentSongFromQueue(isGaplessTransiting = isAuto)
        }

        override fun onSkipToNext() {
            // Pulo acionado por botão é sempre manual (isAuto = false)
            handleSkipNext(false)
        }

        override fun onSkipToQueueItem(id: Long) {
            // 1. Tentar encontrar no UP NEXT
            val upNextIndex = upNextList.indexOfFirst { it.id == id }
            if (upNextIndex != -1) {
                // Apenas COMPRA a música, sem remover. Clona o ID para não conflitar com a visível na UI
                val clickedSong = upNextList[upNextIndex]
                val clonedSong = clickedSong.copy(id = System.nanoTime())
                
                // A música atual vai para o topo do histórico (se existir)
                currentSong?.let { historyList.add(it) }
                // A música clicada vira a atual
                currentSong = clonedSong
                playCurrentSongFromQueue(isGaplessTransiting = false)
                return
            }
            
            // 2. Tentar encontrar no HISTÓRICO
            val historyIndex = historyList.indexOfFirst { it.id == id }
            if (historyIndex != -1) {
                // Apenas pega a música, sem remover. Clona ID.
                val clickedSong = historyList[historyIndex]
                val clonedSong = clickedSong.copy(id = System.nanoTime())
                
                // Põe a atual como ÚLTIMA coisa que tocou
                currentSong?.let { historyList.add(it) }
                // A música clicada vira a atual
                currentSong = clonedSong
                playCurrentSongFromQueue(isGaplessTransiting = false)
                return
            }
        }

        override fun onSkipToPrevious() {
            // Se tocou mais de 3s, só volta a fita e cabô
            if (engine.getPosition() > 3000) {
                engine.seek(0)
                updatePlaybackState()
                return
            }

            // Voltar pro histórico
            if (historyList.isNotEmpty()) {
                val previousSong = historyList.removeAt(historyList.size - 1)
                
                // Manda o currentSong devolta pro começo das próximas
                currentSong?.let { upNextList.add(0, it) }
                
                currentSong = previousSong
                
                preloadNextArtworks()
                
                playCurrentSongFromQueue(isGaplessTransiting = false)
            } else {
                // Sem histórico, apenas reinicia
                engine.seek(0)
                updatePlaybackState()
            }
        }

        override fun onSetShuffleMode(shuffleMode: Int) {
            val shfEnabled = shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL
            if (isShuffleEnabled != shfEnabled) {
                isShuffleEnabled = shfEnabled
                if (isShuffleEnabled) {
                    applyShuffleAlgorithm()
                } else {
                    restoreOriginalQueue()
                }
            }
            mediaSession.setShuffleMode(shuffleMode)
            syncQueueToSession()
            updatePlaybackState()
        }

        override fun onSetRepeatMode(repeat: Int) {
            repeatMode = repeat
            mediaSession.setRepeatMode(repeat)
            updatePlaybackState()
        }
    }
    
    // Inicia a música atual com base no índice global
    private fun playCurrentSongFromQueue(isGaplessTransiting: Boolean = false) {
        val song = currentSong ?: return
        
        currentSongUri = song.uri
        updateMetadata(song.uri, song.title, song.artist, song.duration)
        
        // Se já estava tocando C++ num crossfade perfeito, o C++ JÁ ESTÁ NA MÚSICA CERTA.
        // Ignorar o PlayFile() que destruiria o Decoder pré-carregado.
        if (!isGaplessTransiting) {
            engine.playFile(song.uri)
        }
        
        isPlaying = true
        syncQueueToSession()
        updatePlaybackState()
        showNotification()
        
        // Informar ao C++ para pré-bufferizar a Próxima na RAM para o Gapless!
        if (upNextList.isNotEmpty()) {
            val nextSong = upNextList[0]
            engine.setNextAudio(nextSong.uri)
        }
    }
    
    private fun applyShuffleAlgorithm() {
        // Se a upNextList tá vazia não há o que embaralhar
        if (upNextList.isEmpty()) return
        
        // Embaralha apenas a upNextList
        upNextList.shuffle()
    }
    
    private fun restoreOriginalQueue() {
        // Sem lista original inteira não tem como restaurar
        if (originalQueue.isEmpty()) return
        
        val activeSong = currentSong ?: return
        
        // Vamos reconstruir historicamente a lista linear a partir de originalQueue
        // Encontra onde está o currentSong na original
        val originalIndex = originalQueue.indexOfFirst { it.id == activeSong.id }
        
        historyList.clear()
        upNextList.clear()
        
        if (originalIndex != -1) {
            for (i in 0 until originalIndex) historyList.add(originalQueue[i])
            for (i in (originalIndex + 1) until originalQueue.size) upNextList.add(originalQueue[i])
        } else {
            // Se falhar (bug/erro), joga tudo na upNext
            upNextList.addAll(originalQueue)
        }
    }

    private fun updatePlaybackState() {
        var state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        
        // Se a Engine declarou Fim de Arquivo mas nós ainda não trocamos de música, pode exibir PAUSE provisório
        // O Callback AUTO_NEXT pulará em milissegundos.
        
        // Pega o ID da música atual na fila para a UI poder grifar de verde
        val activeQueueId = currentSong?.id ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
            )
            .setState(state, engine.getPosition(), 1.0f)
            .setActiveQueueItemId(activeQueueId)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }
    
    private fun syncQueueToSession() {
        val flatQueue = mutableListOf<Song>()
        flatQueue.addAll(historyList)
        currentSong?.let { flatQueue.add(it) }
        flatQueue.addAll(upNextList)

        val queueItems = flatQueue.mapIndexed { index, song ->
            val description = android.support.v4.media.MediaDescriptionCompat.Builder()
                .setMediaId(song.uri)
                .setTitle(song.title)
                .setSubtitle(song.artist)
                .build()
            
            // Usando song.id como queueId vinculativo
            MediaSessionCompat.QueueItem(description, song.id)
        }
        mediaSession.setQueue(queueItems)
    }

    private fun updateMetadata(uri: String, title: String, artist: String, duration: Long) {
        val metadataBuilder = android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            
        // 1. Tentar sacar a imagem em Memória Lru Imediatamente (0 segundos de Latência)
        val cachedBitmap = albumArtCache.get(uri)
        if (cachedBitmap != null) {
            metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cachedBitmap)
            mediaSession.setMetadata(metadataBuilder.build())
            return // Matamos a Thread Pesada inteira!
        }
            
        // 2. Transmite Apenas-Texto provisório se foto nova
        mediaSession.setMetadata(metadataBuilder.build())
            
        // 3. Processador Secundário de Imagem (Tráfego Pesado) para o cache e view
        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = extractResizeBitmap(uri, 512)
            if (bitmap != null) {
                // Guarda de Segurança pro usuário não torrar CPU 
                albumArtCache.put(uri, bitmap)
                metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                
                withContext(Dispatchers.Main) {
                    // Impede o preenchimento fantasma de Notificações antigas se o usuário pular antes da leitura do HD terminar
                    if (currentSongUri == uri) {
                        mediaSession.setMetadata(metadataBuilder.build())
                    }
                }
            }
        }
    }
    
    // Extrator Background Padrão
    private fun extractResizeBitmap(path: String, maxSize: Int): Bitmap? {
        var extractedBitmap: Bitmap? = null
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture
            if (art != null) {
                val originalBitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                extractedBitmap = scaleBitmapToMax(originalBitmap, maxSize)
            }
            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return extractedBitmap
    }
    
    private fun preloadNextArtworks() {
        // Enche o LruCache silenciosamente pras 3 próximas músicas, assim o botão Pular se torna visualmente Instantâneo.
        val upcomingSongs = upNextList.take(3)
        if (upcomingSongs.isEmpty()) return
        
        CoroutineScope(Dispatchers.IO).launch {
            for (song in upcomingSongs) {
                if (albumArtCache.get(song.uri) == null) {
                   val bitmap = extractResizeBitmap(song.uri, 512)
                   if (bitmap != null) albumArtCache.put(song.uri, bitmap)
                }
            }
        }
    }
    
    private fun scaleBitmapToMax(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) return bitmap

        val bitmapRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        
        if (bitmapRatio > 1) {
            newWidth = maxSize
            newHeight = (maxSize / bitmapRatio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * bitmapRatio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
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

        val playPauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_PLAY_PAUSE)
        val prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        val nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        val playPauseAction = NotificationCompat.Action(playPauseIcon, "Play/Pause", playPauseIntent)
        val prevAction = NotificationCompat.Action(android.R.drawable.ic_media_previous, "Previous", prevIntent)
        val nextAction = NotificationCompat.Action(android.R.drawable.ic_media_next, "Next", nextIntent)

        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata?.description

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(description?.title ?: "Unknown Title")
            .setContentText(description?.subtitle ?: "Unknown Artist")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)) // Botões Exibidos no Resumo
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
