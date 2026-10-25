package com.lucas.player

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lucas.player.data.CoverArtLoader
import com.lucas.player.data.LibraryRepository
import com.lucas.player.data.LyricsManager
import com.lucas.player.data.MusicDatabase
import com.lucas.player.data.PreferencesManager
import com.lucas.player.model.*
import com.lucas.player.queue.RepeatMode
import com.lucas.player.queue.ShuffleMode
import com.lucas.player.service.PlaybackService
import com.lucas.player.ui.GroupAdapter
import com.lucas.player.ui.GroupItem
import com.lucas.player.ui.TrackAdapter
import com.lucas.player.ui.WaveformView
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity(), PlaybackService.PlaybackListener {

    private val libraryRepo = LibraryRepository()
    private val musicDb by lazy { MusicDatabase(this) }
    private val preferencesManager by lazy { PreferencesManager(this) }
    private var playbackService: PlaybackService? = null
    private var isBound = false

    private var currentCategory = LibraryCategory.ALL_SONGS
    private var currentSortMode = SortMode.TITLE_ASC
    private var currentDrilldownKey = ""
    private var isNowPlayingExpanded = false
    private var isInsideGroupDrilldown = false

    private var currentDisplayedTracks = listOf<Track>()
    private var isUserTrackingSeekBar = false

    // Lyrics dialog tracking
    private var activeLyricsDialog: AlertDialog? = null
    private var currentLyrics: LyricsManager.Lyrics? = null
    private var currentLyricsLineViews: List<TextView> = emptyList()
    private var currentLyricsScrollView: ScrollView? = null
    private var lastActiveLyricIndex = -1

    // UI elements - Library Header & Categories
    private lateinit var layoutLibrary: View
    private lateinit var tvHeaderTitle: TextView
    private lateinit var etSearchTracks: EditText

    private lateinit var btnTabTracks: Button
    private lateinit var btnTabFolders: Button
    private lateinit var btnTabAlbums: Button
    private lateinit var btnTabArtists: Button
    private lateinit var btnTabGenres: Button
    private lateinit var btnTabYears: Button
    private lateinit var btnTabComposers: Button
    private lateinit var btnTabQueue: Button
    private lateinit var btnTabPlaylists: Button
    private lateinit var btnTabMostPlayed: Button
    private lateinit var btnTabRecentlyAdded: Button
    private lateinit var btnTabTopRated: Button
    private lateinit var btnSortOrder: Button

    private lateinit var layoutSubHeader: View
    private lateinit var btnBackFromGroup: Button
    private lateinit var tvSubHeaderTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyState: TextView

    // UI elements - Mini Player
    private lateinit var layoutMiniPlayer: View
    private lateinit var imgMiniCover: ImageView
    private lateinit var miniProgressBar: ProgressBar
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPlayPause: Button

    // UI elements - Now Playing Screen
    private lateinit var layoutNowPlaying: View
    private lateinit var btnCollapseNowPlaying: Button
    private lateinit var btnNpLyrics: Button
    private lateinit var btnNpSleepTimer: Button
    private lateinit var btnNpEqualizer: Button
    private lateinit var imgNpCover: ImageView
    private lateinit var tvNpTitle: TextView
    private lateinit var tvNpArtist: TextView
    private lateinit var tvNpAlbum: TextView
    private lateinit var seekBarNowPlaying: SeekBar
    private lateinit var tvNpCurrentTime: TextView
    private lateinit var tvNpTotalTime: TextView
    private lateinit var btnNpShuffle: Button
    private lateinit var btnNpPrevious: Button
    private lateinit var btnNpPlayPause: Button
    private lateinit var btnNpNext: Button
    private lateinit var btnNpRepeat: Button
    private lateinit var waveformNowPlaying: WaveformView

    // Adapters
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var groupAdapter: GroupAdapter

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlaybackService.LocalBinder
            val s = binder.getService()
            playbackService = s
            isBound = true
            s.addListener(this@MainActivity)

            updateShuffleButtonState(s.queueManager.shuffleMode)
            updateRepeatButtonState(s.queueManager.repeatMode)

            // Se o serviço já tiver uma faixa tocando/carregada, exibe o mini player
            s.queueManager.currentTrack?.let {
                showMiniPlayer(it, s.audioEngine.isPlaying())
                loadWaveformForTrack(it)
                CoverArtLoader.loadCover(this@MainActivity, it, 300, imgNpCover)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService?.removeListener(this@MainActivity)
            playbackService = null
            isBound = false
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] == true ||
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true

        if (audioGranted) {
            loadLibrary()
        } else {
            tvEmptyState.text = "Permissão necessária para acessar músicas locais.\nToque para permitir."
            tvEmptyState.visibility = View.VISIBLE
            tvEmptyState.setOnClickListener { checkAndRequestPermissions() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupAdapters()
        setupCategoryClickListeners()
        setupNowPlayingControls()
        setupBackNavigation()

        startAndBindService()
        checkAndRequestPermissions()
    }

    private fun initViews() {
        layoutLibrary = findViewById(R.id.layoutLibrary)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        etSearchTracks = findViewById(R.id.etSearchTracks)

        btnTabTracks = findViewById(R.id.btnTabTracks)
        btnTabFolders = findViewById(R.id.btnTabFolders)
        btnTabAlbums = findViewById(R.id.btnTabAlbums)
        btnTabArtists = findViewById(R.id.btnTabArtists)
        btnTabGenres = findViewById(R.id.btnTabGenres)
        btnTabYears = findViewById(R.id.btnTabYears)
        btnTabComposers = findViewById(R.id.btnTabComposers)
        btnTabQueue = findViewById(R.id.btnTabQueue)
        btnTabPlaylists = findViewById(R.id.btnTabPlaylists)
        btnTabMostPlayed = findViewById(R.id.btnTabMostPlayed)
        btnTabRecentlyAdded = findViewById(R.id.btnTabRecentlyAdded)
        btnTabTopRated = findViewById(R.id.btnTabTopRated)
        btnSortOrder = findViewById(R.id.btnSortOrder)

        layoutSubHeader = findViewById(R.id.layoutSubHeader)
        btnBackFromGroup = findViewById(R.id.btnBackFromGroup)
        tvSubHeaderTitle = findViewById(R.id.tvSubHeaderTitle)
        recyclerView = findViewById(R.id.recyclerViewLibrary)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        etSearchTracks.addTextChangedListener { text ->
            filterCurrentList(text?.toString()?.trim() ?: "")
        }

        btnSortOrder.setOnClickListener {
            showSortDialog()
        }

        layoutMiniPlayer = findViewById(R.id.layoutMiniPlayer)
        imgMiniCover = findViewById(R.id.imgMiniCover)
        miniProgressBar = findViewById(R.id.miniProgressBar)
        tvMiniTitle = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause)

        layoutNowPlaying = findViewById(R.id.layoutNowPlaying)
        btnCollapseNowPlaying = findViewById(R.id.btnCollapseNowPlaying)
        btnNpLyrics = findViewById(R.id.btnNpLyrics)
        btnNpSleepTimer = findViewById(R.id.btnNpSleepTimer)
        btnNpEqualizer = findViewById(R.id.btnNpEqualizer)
        imgNpCover = findViewById(R.id.imgNpCover)
        tvNpTitle = findViewById(R.id.tvNpTitle)
        tvNpArtist = findViewById(R.id.tvNpArtist)
        tvNpAlbum = findViewById(R.id.tvNpAlbum)
        seekBarNowPlaying = findViewById(R.id.seekBarNowPlaying)
        tvNpCurrentTime = findViewById(R.id.tvNpCurrentTime)
        tvNpTotalTime = findViewById(R.id.tvNpTotalTime)
        btnNpShuffle = findViewById(R.id.btnNpShuffle)
        btnNpPrevious = findViewById(R.id.btnNpPrevious)
        btnNpPlayPause = findViewById(R.id.btnNpPlayPause)
        btnNpNext = findViewById(R.id.btnNpNext)
        btnNpRepeat = findViewById(R.id.btnNpRepeat)
        waveformNowPlaying = findViewById(R.id.waveformNowPlaying)
    }

    private fun setupAdapters() {
        recyclerView.layoutManager = LinearLayoutManager(this)

        trackAdapter = TrackAdapter(
            onTrackClick = { track, position ->
                playbackService?.playQueue(currentDisplayedTracks, position)
                expandNowPlaying()
            },
            onTrackLongClick = { track, position ->
                showTrackContextMenu(track)
                true
            }
        )

        groupAdapter = GroupAdapter { groupItem ->
            drillDownGroup(groupItem.title)
        }

        recyclerView.adapter = trackAdapter
    }

    private fun setupCategoryClickListeners() {
        btnTabTracks.setOnClickListener { selectCategory(LibraryCategory.ALL_SONGS) }
        btnTabFolders.setOnClickListener { selectCategory(LibraryCategory.FOLDERS) }
        btnTabAlbums.setOnClickListener { selectCategory(LibraryCategory.ALBUMS) }
        btnTabArtists.setOnClickListener { selectCategory(LibraryCategory.ARTISTS) }
        btnTabGenres.setOnClickListener { selectCategory(LibraryCategory.GENRES) }
        btnTabYears.setOnClickListener { selectCategory(LibraryCategory.YEARS) }
        btnTabComposers.setOnClickListener { selectCategory(LibraryCategory.COMPOSERS) }
        btnTabQueue.setOnClickListener { selectCategory(LibraryCategory.QUEUE) }
        btnTabPlaylists.setOnClickListener { selectCategory(LibraryCategory.PLAYLISTS) }
        btnTabMostPlayed.setOnClickListener { selectCategory(LibraryCategory.MOST_PLAYED) }
        btnTabRecentlyAdded.setOnClickListener { selectCategory(LibraryCategory.RECENTLY_ADDED) }
        btnTabTopRated.setOnClickListener { selectCategory(LibraryCategory.TOP_RATED) }

        btnBackFromGroup.setOnClickListener {
            exitGroupDrilldown()
        }

        findViewById<View>(R.id.layoutMiniPlayerText).setOnClickListener {
            expandNowPlaying()
        }

        btnMiniPlayPause.setOnClickListener {
            playbackService?.togglePlayPause()
        }
    }

    private fun setupNowPlayingControls() {
        btnCollapseNowPlaying.setOnClickListener {
            collapseNowPlaying()
        }

        btnNpLyrics.setOnClickListener {
            showLyricsDialog()
        }

        btnNpSleepTimer.setOnClickListener {
            showSleepTimerDialog()
        }

        btnNpEqualizer.setOnClickListener {
            showEqualizerDialog()
        }

        btnNpPlayPause.setOnClickListener {
            playbackService?.togglePlayPause()
        }

        btnNpNext.setOnClickListener {
            playbackService?.playNext()
        }

        btnNpPrevious.setOnClickListener {
            playbackService?.playPrevious()
        }

        btnNpShuffle.setOnClickListener {
            val mode = playbackService?.toggleShuffleMode() ?: ShuffleMode.OFF
            updateShuffleButtonState(mode)
            Toast.makeText(this, mode.displayName, Toast.LENGTH_SHORT).show()
        }

        btnNpRepeat.setOnClickListener {
            val mode = playbackService?.toggleRepeat() ?: RepeatMode.OFF
            updateRepeatButtonState(mode)
            Toast.makeText(this, mode.displayName, Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.tvNpFormat).setOnClickListener {
            playbackService?.queueManager?.currentTrack?.let { showAudioInfoDialog(it) }
        }

        imgNpCover.setOnClickListener {
            playbackService?.queueManager?.currentTrack?.let { showAudioInfoDialog(it) }
        }

        waveformNowPlaying.onSeekListener = { progressFraction ->
            val totalDuration = playbackService?.audioEngine?.getDurationMs() ?: 0L
            if (totalDuration > 0) {
                val targetMs = (progressFraction * totalDuration).toLong()
                playbackService?.seekTo(targetMs)
                tvNpCurrentTime.text = formatTime(targetMs)
                seekBarNowPlaying.progress = targetMs.toInt()
            }
        }

        seekBarNowPlaying.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvNpCurrentTime.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingSeekBar = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingSeekBar = false
                val targetMs = seekBar?.progress?.toLong() ?: 0L
                playbackService?.seekTo(targetMs)
            }
        })
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isNowPlayingExpanded) {
                    collapseNowPlaying()
                } else if (isInsideGroupDrilldown) {
                    exitGroupDrilldown()
                } else if (currentCategory != LibraryCategory.ALL_SONGS) {
                    selectCategory(LibraryCategory.ALL_SONGS)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun selectCategory(category: LibraryCategory) {
        currentCategory = category
        isInsideGroupDrilldown = false
        currentDrilldownKey = ""
        layoutSubHeader.visibility = View.GONE
        etSearchTracks.setText("")

        // Highlight active category tab
        val activeColor = Color.parseColor("#00E676")
        val defaultColor = Color.parseColor("#333333")

        val allCategoryButtons = listOf(
            btnTabTracks to LibraryCategory.ALL_SONGS,
            btnTabFolders to LibraryCategory.FOLDERS,
            btnTabAlbums to LibraryCategory.ALBUMS,
            btnTabArtists to LibraryCategory.ARTISTS,
            btnTabGenres to LibraryCategory.GENRES,
            btnTabYears to LibraryCategory.YEARS,
            btnTabComposers to LibraryCategory.COMPOSERS,
            btnTabQueue to LibraryCategory.QUEUE,
            btnTabPlaylists to LibraryCategory.PLAYLISTS,
            btnTabMostPlayed to LibraryCategory.MOST_PLAYED,
            btnTabRecentlyAdded to LibraryCategory.RECENTLY_ADDED,
            btnTabTopRated to LibraryCategory.TOP_RATED
        )

        allCategoryButtons.forEach { (btn, cat) ->
            btn.setBackgroundColor(if (cat == category) activeColor else defaultColor)
        }

        renderCurrentCategory()
    }

    private fun renderCurrentCategory() {
        when (currentCategory) {
            LibraryCategory.ALL_SONGS -> {
                val list = libraryRepo.applySorting(libraryRepo.getAllTracks(), currentSortMode)
                displayTracks(list, "Nenhuma música encontrada no aparelho.")
            }
            LibraryCategory.FOLDERS -> {
                val folders = libraryRepo.getFolders()
                val items = folders.map { GroupItem(it.name, it.path, "${it.trackCount} faixas") }
                displayGroups(items, "Nenhuma pasta encontrada.")
            }
            LibraryCategory.ALBUMS -> {
                val albums = libraryRepo.getAlbums()
                val items = albums.map {
                    val yearStr = if (it.year > 0) " • ${it.year}" else ""
                    GroupItem(it.name, "${it.artist}$yearStr", "${it.trackCount} faixas")
                }
                displayGroups(items, "Nenhum álbum encontrado.")
            }
            LibraryCategory.ARTISTS -> {
                val artists = libraryRepo.getArtists()
                val items = artists.map { GroupItem(it.name, "Artista", "${it.trackCount} faixas") }
                displayGroups(items, "Nenhum artista encontrado.")
            }
            LibraryCategory.ALBUM_ARTISTS -> {
                val artists = libraryRepo.getAlbumArtists()
                val items = artists.map { GroupItem(it.name, "Artista do Álbum", "${it.trackCount} faixas") }
                displayGroups(items, "Nenhum artista encontrado.")
            }
            LibraryCategory.GENRES -> {
                val genres = libraryRepo.getGenres()
                val items = genres.map { GroupItem(it.name, "Gênero", "${it.trackCount} faixas") }
                displayGroups(items, "Nenhum gênero encontrado.")
            }
            LibraryCategory.YEARS -> {
                val years = libraryRepo.getYears()
                val items = years.map { GroupItem(it.year.toString(), "Ano de Lançamento", "${it.trackCount} faixas") }
                displayGroups(items, "Nenhum ano identificado nas tags.")
            }
            LibraryCategory.COMPOSERS -> {
                val comps = libraryRepo.getComposers()
                val items = comps.map { GroupItem(it.name, "Compositor", "${it.trackCount} faixas") }
                displayGroups(items, "Nenhum compositor identificado.")
            }
            LibraryCategory.QUEUE -> {
                val queueTracks = playbackService?.queueManager?.getPriorityQueue() ?: emptyList()
                displayTracks(queueTracks, "A Fila de Reprodução está vazia.\nToque e segure em qualquer música para enfileirar.")
            }
            LibraryCategory.PLAYLISTS -> {
                val pls = libraryRepo.getUserPlaylists()
                val items = pls.map { GroupItem(it.name, "Playlist", "${it.trackCount} faixas") }
                displayGroups(items, "Nenhuma playlist criada.\nToque e segure em uma música para criar.")
            }
            LibraryCategory.MOST_PLAYED -> {
                val list = libraryRepo.getMostPlayed()
                displayTracks(list, "Nenhuma música tocada recentemente.")
            }
            LibraryCategory.RECENTLY_ADDED -> {
                val list = libraryRepo.getRecentlyAdded()
                displayTracks(list, "Nenhuma música adicionada recentemente.")
            }
            LibraryCategory.RECENTLY_PLAYED -> {
                val list = libraryRepo.getRecentlyPlayed()
                displayTracks(list, "Nenhuma música no histórico de reprodução.")
            }
            LibraryCategory.TOP_RATED -> {
                val list = libraryRepo.getTopRated()
                displayTracks(list, "Nenhuma música favoritada ainda.\nToque e segure em uma música para favoritar.")
            }
        }
    }

    private fun displayTracks(list: List<Track>, emptyMessage: String) {
        currentDisplayedTracks = list
        recyclerView.adapter = trackAdapter
        trackAdapter.submitList(list)
        tvEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        tvEmptyState.text = emptyMessage
    }

    private fun displayGroups(items: List<GroupItem>, emptyMessage: String) {
        recyclerView.adapter = groupAdapter
        groupAdapter.submitList(items)
        tvEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        tvEmptyState.text = emptyMessage
    }

    private fun drillDownGroup(groupName: String) {
        currentDrilldownKey = groupName
        val tracks = when (currentCategory) {
            LibraryCategory.ALBUMS -> libraryRepo.getTracksForAlbum(groupName, currentSortMode)
            LibraryCategory.ARTISTS -> libraryRepo.getTracksForArtist(groupName, currentSortMode)
            LibraryCategory.ALBUM_ARTISTS -> libraryRepo.getTracksForAlbumArtist(groupName, currentSortMode)
            LibraryCategory.FOLDERS -> libraryRepo.getTracksForFolder(groupName, currentSortMode)
            LibraryCategory.GENRES -> libraryRepo.getTracksForGenre(groupName, currentSortMode)
            LibraryCategory.YEARS -> groupName.toIntOrNull()?.let { libraryRepo.getTracksForYear(it, currentSortMode) } ?: emptyList()
            LibraryCategory.COMPOSERS -> libraryRepo.getTracksForComposer(groupName, currentSortMode)
            LibraryCategory.PLAYLISTS -> {
                val pl = libraryRepo.getUserPlaylists().firstOrNull { it.name == groupName }
                if (pl != null) libraryRepo.getTracksForUserPlaylist(pl.id) else emptyList()
            }
            else -> emptyList()
        }

        if (tracks.isNotEmpty()) {
            isInsideGroupDrilldown = true
            tvSubHeaderTitle.text = groupName
            layoutSubHeader.visibility = View.VISIBLE
            displayTracks(tracks, "Nenhuma faixa neste agrupamento.")
        }
    }

    private fun exitGroupDrilldown() {
        isInsideGroupDrilldown = false
        currentDrilldownKey = ""
        layoutSubHeader.visibility = View.GONE
        renderCurrentCategory()
    }

    private fun filterCurrentList(query: String) {
        if (query.isEmpty()) {
            trackAdapter.submitList(currentDisplayedTracks)
            return
        }
        val filtered = currentDisplayedTracks.filter { track ->
            track.title.contains(query, ignoreCase = true) ||
            track.artist.contains(query, ignoreCase = true) ||
            track.album.contains(query, ignoreCase = true) ||
            track.genre.contains(query, ignoreCase = true) ||
            track.composer.contains(query, ignoreCase = true)
        }
        trackAdapter.submitList(filtered)
    }

    // --- Track Context Menu (Poweramp Quick Actions) ---

    private fun showTrackContextMenu(track: Track) {
        val options = arrayOf(
            "▶ Tocar a Seguir (Play Next)",
            "➕ Enfileirar no Fim (Enqueue)",
            "⭐ Favoritar (5 Estrelas)",
            "📋 Adicionar à Playlist",
            "ℹ Detalhes do Áudio e Tags"
        )

        AlertDialog.Builder(this)
            .setTitle(track.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        playbackService?.enqueueNext(track)
                        Toast.makeText(this, "Adicionada para tocar a seguir", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        playbackService?.enqueueEnd(track)
                        Toast.makeText(this, "Adicionada ao fim da fila", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        libraryRepo.setTrackRating(track.id, 5)
                        Toast.makeText(this, "Adicionada às Favoritas", Toast.LENGTH_SHORT).show()
                    }
                    3 -> showAddToPlaylistDialog(track)
                    4 -> showAudioInfoDialog(track)
                }
            }
            .show()
    }

    private fun showAddToPlaylistDialog(track: Track) {
        val playlists = libraryRepo.getUserPlaylists()
        val names = mutableListOf("➕ Criar Nova Playlist...")
        names.addAll(playlists.map { it.name })

        AlertDialog.Builder(this)
            .setTitle("Adicionar à Playlist")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    val input = EditText(this)
                    input.hint = "Nome da Playlist"
                    AlertDialog.Builder(this)
                        .setTitle("Nova Playlist")
                        .setView(input)
                        .setPositiveButton("Criar") { _, _ ->
                            val name = input.text.toString().trim()
                            if (name.isNotEmpty()) {
                                val plId = libraryRepo.createPlaylist(name)
                                if (plId > 0) {
                                    libraryRepo.addTrackToUserPlaylist(plId, track.id)
                                    Toast.makeText(this, "Adicionada a $name", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else {
                    val selectedPl = playlists[which - 1]
                    libraryRepo.addTrackToUserPlaylist(selectedPl.id, track.id)
                    Toast.makeText(this, "Adicionada a ${selectedPl.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showAudioInfoDialog(track: Track) {
        val msg = """
            Título: ${track.title}
            Artista: ${track.artist}
            Álbum: ${track.album}
            Artista do Álbum: ${if (track.albumArtist.isNotEmpty()) track.albumArtist else "N/A"}
            Compositor: ${if (track.composer.isNotEmpty()) track.composer else "N/A"}
            Gênero: ${if (track.genre.isNotEmpty()) track.genre else "N/A"}
            Ano: ${if (track.year > 0) track.year else "N/A"}
            Faixa: ${if (track.trackNumber > 0) track.trackNumber else "N/A"}
            Formato: ${track.formatTag}
            ReplayGain Faixa: ${if (track.replayGainTrackGain != 0f) "${track.replayGainTrackGain} dB (Pico: ${track.replayGainTrackPeak})" else "Sem tag RG"}
            ReplayGain Álbum: ${if (track.replayGainAlbumGain != 0f) "${track.replayGainAlbumGain} dB (Pico: ${track.replayGainAlbumPeak})" else "Sem tag RG"}
            Duração: ${formatTime(track.durationMs)}
            Tamanho: ${track.fileSize / (1024 * 1024)} MB
            Reproduções: ${track.playCount} vezes
            Classificação: ${if (track.rating > 0) "${track.rating} estrelas" else "Sem voto"}
            Caminho: ${track.path}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Informações da Faixa")
            .setMessage(msg)
            .setPositiveButton("Fechar", null)
            .show()
    }

    // --- Sort Dialog (Poweramp List Options) ---

    private fun showSortDialog() {
        val sortModes = SortMode.values()
        val names = sortModes.map { it.displayName }.toTypedArray()
        val currentIndex = sortModes.indexOf(currentSortMode)

        AlertDialog.Builder(this)
            .setTitle("Ordenar Lista")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                currentSortMode = sortModes[which]
                dialog.dismiss()
                if (isInsideGroupDrilldown) {
                    drillDownGroup(currentDrilldownKey)
                } else {
                    renderCurrentCategory()
                }
            }
            .show()
    }

    // --- Sleep Timer Dialog ---

    private fun showSleepTimerDialog() {
        val service = playbackService ?: return
        val currentRemainingSec = service.getSleepTimerRemainingSeconds()

        val view = layoutInflater.inflate(android.R.layout.select_dialog_singlechoice, null)
        val options = arrayOf(
            "Desativar Temporizador",
            "5 minutos",
            "10 minutos",
            "15 minutos",
            "30 minutos",
            "45 minutos",
            "60 minutos",
            "90 minutos"
        )
        val minutesArray = intArrayOf(0, 5, 10, 15, 30, 45, 60, 90)

        val title = if (currentRemainingSec > 0) {
            "Temporizador Ativo (${currentRemainingSec / 60}m ${currentRemainingSec % 60}s restantes)"
        } else {
            "Temporizador de Desligamento"
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options) { _, which ->
                val min = minutesArray[which]
                if (min == 0) {
                    service.cancelSleepTimer()
                    btnNpSleepTimer.text = "⏱ Sleep"
                    Toast.makeText(this, "Temporizador cancelado", Toast.LENGTH_SHORT).show()
                } else {
                    service.startSleepTimer(min)
                    btnNpSleepTimer.text = "⏱ ${min}m"
                    Toast.makeText(this, "Player desligará em $min minutos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun expandNowPlaying() {
        isNowPlayingExpanded = true
        layoutNowPlaying.visibility = View.VISIBLE
    }

    private fun collapseNowPlaying() {
        isNowPlayingExpanded = false
        layoutNowPlaying.visibility = View.GONE
    }

    private fun showMiniPlayer(track: Track, isPlaying: Boolean) {
        layoutMiniPlayer.visibility = View.VISIBLE
        tvMiniTitle.text = track.title
        tvMiniArtist.text = "${track.artist} • ${track.album}"
        btnMiniPlayPause.text = if (isPlaying) "⏸" else "▶"
        imgMiniCover.setImageResource(android.R.drawable.ic_media_play)
        CoverArtLoader.loadCover(this, track, 64, imgMiniCover)
    }

    private fun updateShuffleButtonState(mode: ShuffleMode) {
        btnNpShuffle.text = when (mode) {
            ShuffleMode.OFF -> "Shuffle: Off"
            ShuffleMode.ALL_TRACKS -> "Shuffle: Todas"
            ShuffleMode.WITHIN_CATEGORY -> "Shuffle: Cat"
            ShuffleMode.CATEGORIES -> "Shuffle: Álbuns"
            ShuffleMode.SONGS_AND_CATEGORIES -> "Shuffle: Tudo"
        }
        btnNpShuffle.setTextColor(if (mode != ShuffleMode.OFF) Color.parseColor("#00E676") else Color.WHITE)
    }

    private fun updateRepeatButtonState(mode: RepeatMode) {
        btnNpRepeat.text = when (mode) {
            RepeatMode.OFF -> "Repetir: Off"
            RepeatMode.ONE -> "Repetir: 1"
            RepeatMode.CATEGORY -> "Repetir: Cat"
            RepeatMode.ALL -> "Repetir: Fila"
            RepeatMode.ADVANCE_CATEGORY -> "Avançar Cat"
        }
        btnNpRepeat.setTextColor(if (mode != RepeatMode.OFF) Color.parseColor("#00E676") else Color.WHITE)
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    // PlaybackService.PlaybackListener callbacks
    override fun onTrackChanged(track: Track?) {
        runOnUiThread {
            if (track != null) {
                showMiniPlayer(track, playbackService?.audioEngine?.isPlaying() ?: false)
                tvNpTitle.text = track.title
                tvNpArtist.text = track.artist
                tvNpAlbum.text = track.album
                tvNpTotalTime.text = formatTime(track.durationMs)
                seekBarNowPlaying.max = track.durationMs.toInt().coerceAtLeast(1)
                miniProgressBar.max = track.durationMs.toInt().coerceAtLeast(1)
                trackAdapter.setCurrentPlayingId(track.id)
                imgNpCover.setImageResource(android.R.drawable.ic_media_play)
                CoverArtLoader.loadCover(this, track, 300, imgNpCover)
                loadWaveformForTrack(track)

                if (activeLyricsDialog?.isShowing == true) {
                    showLyricsDialog()
                }
            } else {
                layoutMiniPlayer.visibility = View.GONE
                trackAdapter.setCurrentPlayingId(-1L)
                waveformNowPlaying.setWaveform(null)
            }
        }
    }

    private fun loadWaveformForTrack(track: Track) {
        waveformNowPlaying.setWaveform(null)
        waveformNowPlaying.setProgress(0f)
        val uri = track.uri
        val service = playbackService ?: return
        Thread {
            try {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val peaks = service.audioEngine.scanWaveform(pfd, 70)
                    if (peaks != null && peaks.isNotEmpty()) {
                        runOnUiThread {
                            if (playbackService?.queueManager?.currentTrack?.id == track.id) {
                                waveformNowPlaying.setWaveform(peaks)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            btnMiniPlayPause.text = if (isPlaying) "⏸" else "▶"
            btnNpPlayPause.text = if (isPlaying) "⏸" else "▶"
        }
    }

    override fun onProgressUpdate(currentMs: Long, durationMs: Long) {
        runOnUiThread {
            if (!isUserTrackingSeekBar) {
                seekBarNowPlaying.progress = currentMs.toInt()
                tvNpCurrentTime.text = formatTime(currentMs)
            }
            if (durationMs > 0) {
                val progressFraction = currentMs.toFloat() / durationMs.toFloat()
                waveformNowPlaying.setProgress(progressFraction)
            }
            miniProgressBar.progress = currentMs.toInt()

            // Synchronize active lyrics line highlighting & smooth autoscroll
            activeLyricsDialog?.let { dialog ->
                val lyrics = currentLyrics
                if (dialog.isShowing && lyrics != null && lyrics.isSynced && currentLyricsLineViews.isNotEmpty()) {
                    val activeIdx = LyricsManager.findActiveLineIndex(lyrics, currentMs)
                    if (activeIdx != lastActiveLyricIndex && activeIdx in currentLyricsLineViews.indices) {
                        if (lastActiveLyricIndex in currentLyricsLineViews.indices) {
                            currentLyricsLineViews[lastActiveLyricIndex].apply {
                                setTextColor(Color.parseColor("#888888"))
                                textSize = 15f
                                setTypeface(null, Typeface.NORMAL)
                            }
                        }
                        val targetView = currentLyricsLineViews[activeIdx]
                        targetView.apply {
                            setTextColor(Color.parseColor("#00E676"))
                            textSize = 18f
                            setTypeface(null, Typeface.BOLD)
                        }
                        currentLyricsScrollView?.let { sv ->
                            val targetY = targetView.top - (sv.height / 2) + (targetView.height / 2)
                            sv.smoothScrollTo(0, targetY.coerceAtLeast(0))
                        }
                        lastActiveLyricIndex = activeIdx
                    }
                }
            }
        }
    }

    override fun onQueueUpdated(tracks: List<Track>, currentIndex: Int) {
        runOnUiThread {
            if (currentIndex in tracks.indices) {
                trackAdapter.setCurrentPlayingId(tracks[currentIndex].id)
            }
        }
    }

    // Permission and Service Handling
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        } else {
            loadLibrary()
        }
    }

    private fun loadLibrary() {
        tvEmptyState.text = "Lendo biblioteca do dispositivo..."
        tvEmptyState.visibility = View.VISIBLE

        libraryRepo.loadTracks(this) { _ ->
            runOnUiThread {
                selectCategory(LibraryCategory.ALL_SONGS)
            }
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, PlaybackService::class.java)
        try {
            startService(intent)
        } catch (_: Exception) {}
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun showEqualizerDialog() {
        val service = playbackService ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_equalizer, null)

        val switchEq = dialogView.findViewById<android.widget.Switch>(R.id.switchEqEnabled)
        val switchTone = dialogView.findViewById<android.widget.Switch>(R.id.switchToneEnabled)

        // Preamp & Tone Sliders
        val sbPreamp = dialogView.findViewById<SeekBar>(R.id.seekBarPreamp)
        val tvPreamp = dialogView.findViewById<TextView>(R.id.tvValPreamp)
        val sbBass = dialogView.findViewById<SeekBar>(R.id.seekBarBass)
        val tvBass = dialogView.findViewById<TextView>(R.id.tvValBass)
        val sbTreble = dialogView.findViewById<SeekBar>(R.id.seekBarTreble)
        val tvTreble = dialogView.findViewById<TextView>(R.id.tvValTreble)

        val btnPresetList = dialogView.findViewById<Button>(R.id.btnEqPresetList)
        val btnSavePreset = dialogView.findViewById<Button>(R.id.btnEqSavePreset)
        var activePresetName = preferencesManager.getActivePresetName()
        btnPresetList.text = "📋 Preset: $activePresetName"

        fun updateSlider(sb: SeekBar, tv: TextView, gainDb: Float) {
            sb.progress = (gainDb + 12f).toInt().coerceIn(0, 24)
            val prefix = if (gainDb > 0) "+" else ""
            tv.text = "${prefix}${gainDb.toInt()} dB"
        }

        // Init Preamp and Tone
        updateSlider(sbPreamp, tvPreamp, service.getPreamp())
        updateSlider(sbBass, tvBass, service.getBass())
        updateSlider(sbTreble, tvTreble, service.getTreble())

        switchTone.isChecked = service.isToneEnabled()
        switchTone.setOnCheckedChangeListener { _, isChecked ->
            service.setToneEnabled(isChecked)
            preferencesManager.saveToneEnabled(isChecked)
        }

        sbPreamp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val gainDb = (progress - 12).toFloat()
                    updateSlider(sbPreamp, tvPreamp, gainDb)
                    service.setPreamp(gainDb)
                    preferencesManager.savePreamp(gainDb)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        sbBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val gainDb = (progress - 12).toFloat()
                    updateSlider(sbBass, tvBass, gainDb)
                    service.setBass(gainDb)
                    preferencesManager.saveBass(gainDb)
                    switchTone.isChecked = true
                    service.setToneEnabled(true)
                    preferencesManager.saveToneEnabled(true)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        sbTreble.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val gainDb = (progress - 12).toFloat()
                    updateSlider(sbTreble, tvTreble, gainDb)
                    service.setTreble(gainDb)
                    preferencesManager.saveTreble(gainDb)
                    switchTone.isChecked = true
                    service.setToneEnabled(true)
                    preferencesManager.saveToneEnabled(true)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Stereo Balance Control
        val sbBalance = dialogView.findViewById<SeekBar>(R.id.seekBarBalance)
        val tvBalance = dialogView.findViewById<TextView>(R.id.tvValBalance)
        val currentBalance = service.getBalance()
        sbBalance.progress = ((currentBalance + 1.0f) * 100f).toInt().coerceIn(0, 200)
        fun updateBalanceText(bal: Float) {
            tvBalance.text = when {
                bal < -0.05f -> "E ${( -bal * 100).toInt()}%"
                bal > 0.05f -> "D ${( bal * 100).toInt()}%"
                else -> "Centro"
            }
        }
        updateBalanceText(currentBalance)
        sbBalance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val bal = (progress - 100) / 100.0f
                    updateBalanceText(bal)
                    service.setBalance(bal)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Crossfade Duration Slider
        val sbCrossfade = dialogView.findViewById<SeekBar>(R.id.seekBarCrossfade)
        val tvCrossfade = dialogView.findViewById<TextView>(R.id.tvValCrossfade)
        val currentCrossfadeSec = (service.getCrossfadeDurationMs() / 1000).coerceIn(0, 10)
        sbCrossfade.progress = currentCrossfadeSec
        fun updateCrossfadeText(sec: Int) {
            tvCrossfade.text = if (sec == 0) "Desativado" else "${sec}s"
        }
        updateCrossfadeText(currentCrossfadeSec)
        sbCrossfade.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateCrossfadeText(progress)
                    service.setCrossfadeDurationMs(progress * 1000)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Mono Audio Switch
        val switchMono = dialogView.findViewById<android.widget.Switch>(R.id.switchMono)
        switchMono.isChecked = service.isMono()
        switchMono.setOnCheckedChangeListener { _, isChecked ->
            service.setMono(isChecked)
        }

        // ReplayGain Mode Selector
        val btnRg = dialogView.findViewById<Button>(R.id.btnReplayGainMode)
        val rgModes = arrayOf("Desativado", "Ganho de Faixa (Padrão)", "Ganho de Álbum")
        fun updateRgButton() {
            btnRg.text = when (service.getReplayGainMode()) {
                1 -> "🔊 Faixa"
                2 -> "🔊 Álbum"
                else -> "Desativado"
            }
            btnRg.setTextColor(if (service.getReplayGainMode() != 0) Color.parseColor("#00E676") else Color.parseColor("#888888"))
        }
        updateRgButton()
        btnRg.setOnClickListener {
            val currentMode = service.getReplayGainMode()
            AlertDialog.Builder(this)
                .setTitle("Modo ReplayGain (RG)")
                .setSingleChoiceItems(rgModes, currentMode) { d, which ->
                    service.setReplayGainMode(which)
                    updateRgButton()
                    d.dismiss()
                    Toast.makeText(this, "ReplayGain: ${rgModes[which]}", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        // 10-Band Graphic Equalizer
        val seekBars = listOf(
            dialogView.findViewById<SeekBar>(R.id.seekBarBand0),
            dialogView.findViewById<SeekBar>(R.id.seekBarBand1),
            dialogView.findViewById<SeekBar>(R.id.seekBarBand2),
            dialogView.findViewById<SeekBar>(R.id.seekBarBand3),
            dialogView.findViewById<SeekBar>(R.id.seekBarBand4),
            dialogView.findViewById<SeekBar>(R.id.seekBarBand5),
            dialogView.findViewById<SeekBar>(R.id.seekBarBand6),
            dialogView.findViewById<SeekBar>(R.id.seekBarBand7),
            dialogView.findViewById<SeekBar>(R.id.seekBarBand8),
            dialogView.findViewById<SeekBar>(R.id.seekBarBand9)
        )
        val valTexts = listOf(
            dialogView.findViewById<TextView>(R.id.tvValBand0),
            dialogView.findViewById<TextView>(R.id.tvValBand1),
            dialogView.findViewById<TextView>(R.id.tvValBand2),
            dialogView.findViewById<TextView>(R.id.tvValBand3),
            dialogView.findViewById<TextView>(R.id.tvValBand4),
            dialogView.findViewById<TextView>(R.id.tvValBand5),
            dialogView.findViewById<TextView>(R.id.tvValBand6),
            dialogView.findViewById<TextView>(R.id.tvValBand7),
            dialogView.findViewById<TextView>(R.id.tvValBand8),
            dialogView.findViewById<TextView>(R.id.tvValBand9)
        )

        fun updateBandDisplay(band: Int, gainDb: Float) {
            val progress = (gainDb + 12f).toInt().coerceIn(0, 24)
            seekBars[band].progress = progress
            val prefix = if (gainDb > 0) "+" else ""
            valTexts[band].text = "${prefix}${gainDb.toInt()} dB"
        }

        switchEq.isChecked = service.isEqualizerEnabled()
        switchEq.setOnCheckedChangeListener { _, isChecked ->
            service.setEqualizerEnabled(isChecked)
            preferencesManager.saveEqualizerEnabled(isChecked)
        }

        for (i in 0 until 10) {
            val gain = service.getEqualizerBand(i)
            updateBandDisplay(i, gain)

            val bandIndex = i
            seekBars[i].setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val gainDb = (progress - 12).toFloat()
                        val prefix = if (gainDb > 0) "+" else ""
                        valTexts[bandIndex].text = "${prefix}${gainDb.toInt()} dB"
                        service.setEqualizerBand(bandIndex, gainDb)
                        preferencesManager.saveEqualizerBand(bandIndex, gainDb)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        fun applyFullPreset(preset: MusicDatabase.EqPreset) {
            activePresetName = preset.name
            preferencesManager.saveActivePresetName(preset.name)
            btnPresetList.text = "📋 Preset: ${preset.name}"

            switchEq.isChecked = true
            service.setEqualizerEnabled(true)
            preferencesManager.saveEqualizerEnabled(true)

            preset.bandGains.forEachIndexed { index, gainDb ->
                if (index < 10) {
                    updateBandDisplay(index, gainDb)
                    service.setEqualizerBand(index, gainDb)
                    preferencesManager.saveEqualizerBand(index, gainDb)
                }
            }
            updateSlider(sbBass, tvBass, preset.bassGain)
            service.setBass(preset.bassGain)
            preferencesManager.saveBass(preset.bassGain)

            updateSlider(sbTreble, tvTreble, preset.trebleGain)
            service.setTreble(preset.trebleGain)
            preferencesManager.saveTreble(preset.trebleGain)

            updateSlider(sbPreamp, tvPreamp, preset.preampGain)
            service.setPreamp(preset.preampGain)
            preferencesManager.savePreamp(preset.preampGain)

            if (preset.bassGain != 0f || preset.trebleGain != 0f) {
                switchTone.isChecked = true
                service.setToneEnabled(true)
                preferencesManager.saveToneEnabled(true)
            }
        }

        // All Presets Selector Dialog
        btnPresetList.setOnClickListener {
            val presets = musicDb.getAllPresets()
            val names = presets.map { p ->
                if (p.isBuiltin) "🔹 ${p.name}" else "⭐ ${p.name} (Custom)"
            }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Predefinições de Equalizador")
                .setItems(names) { _, which ->
                    val chosen = presets[which]
                    applyFullPreset(chosen)
                    Toast.makeText(this, "Preset '${chosen.name}' aplicado!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Fechar", null)
                .show()
        }

        // Save Current Settings as Custom Preset
        btnSavePreset.setOnClickListener {
            val input = EditText(this).apply {
                hint = "Nome do Preset (ex: Meus Fones)"
                setSingleLine()
            }
            AlertDialog.Builder(this)
                .setTitle("Salvar Predefinição")
                .setMessage("Deseja salvar o ajuste atual de 10 bandas e controles de Tone?")
                .setView(input)
                .setPositiveButton("Salvar") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        val gains = (0 until 10).map { service.getEqualizerBand(it) }
                        musicDb.savePreset(name, gains, service.getBass(), service.getTreble(), service.getPreamp())
                        preferencesManager.saveActivePresetName(name)
                        btnPresetList.text = "📋 Preset: $name"
                        Toast.makeText(this, "Preset '$name' salvo com sucesso!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // Quick scroll presets buttons
        dialogView.findViewById<Button>(R.id.btnPresetFlat).setOnClickListener {
            applyFullPreset(MusicDatabase.EqPreset(name = "Flat", bandGains = List(10) { 0f }))
        }
        dialogView.findViewById<Button>(R.id.btnPresetBass).setOnClickListener {
            applyFullPreset(MusicDatabase.EqPreset(name = "Bass Heavy", bandGains = listOf(8f, 7f, 5f, 2f, 0f, 0f, 0f, 1f, 2f, 3f), bassGain = 5f))
        }
        dialogView.findViewById<Button>(R.id.btnPresetRock).setOnClickListener {
            applyFullPreset(MusicDatabase.EqPreset(name = "Rock", bandGains = listOf(5f, 4f, 2f, -1f, -2f, 1f, 3f, 5f, 6f, 6f), bassGain = 3f, trebleGain = 3f))
        }
        dialogView.findViewById<Button>(R.id.btnPresetPop).setOnClickListener {
            applyFullPreset(MusicDatabase.EqPreset(name = "Pop", bandGains = listOf(-1f, 1f, 3f, 5f, 4f, 2f, -1f, 1f, 3f, 4f), bassGain = 2f, trebleGain = 2f))
        }
        dialogView.findViewById<Button>(R.id.btnPresetJazz).setOnClickListener {
            applyFullPreset(MusicDatabase.EqPreset(name = "Jazz", bandGains = listOf(3f, 3f, 2f, 1f, 1f, 2f, 2f, 3f, 4f, 4f), bassGain = 2f, trebleGain = 2f))
        }
        dialogView.findViewById<Button>(R.id.btnPresetVocal).setOnClickListener {
            applyFullPreset(MusicDatabase.EqPreset(name = "Vocal Boost", bandGains = listOf(-3f, -2f, 0f, 2f, 5f, 5f, 3f, 1f, 0f, -1f), bassGain = -1f, trebleGain = 1f))
        }
        dialogView.findViewById<Button>(R.id.btnPresetElectronic).setOnClickListener {
            applyFullPreset(MusicDatabase.EqPreset(name = "Electronic", bandGains = listOf(6f, 5f, 2f, 0f, -2f, 2f, 1f, 3f, 5f, 6f), bassGain = 5f, trebleGain = 4f))
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnEqClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showLyricsDialog() {
        val track = playbackService?.queueManager?.currentTrack
        if (track == null) {
            Toast.makeText(this, "Nenhuma faixa em reprodução", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_lyrics, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvLyricsTrackTitle)
        val tvSource = dialogView.findViewById<TextView>(R.id.tvLyricsSource)
        val scrollLyrics = dialogView.findViewById<ScrollView>(R.id.scrollLyrics)
        val layoutLines = dialogView.findViewById<LinearLayout>(R.id.layoutLyricsLines)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tvLyricsEmpty)
        val btnClose = dialogView.findViewById<Button>(R.id.btnLyricsClose)

        tvTitle.text = "${track.title} • ${track.artist}"
        tvSource.text = "Buscando letras..."

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        activeLyricsDialog = dialog
        currentLyricsScrollView = scrollLyrics
        lastActiveLyricIndex = -1

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            activeLyricsDialog = null
            currentLyrics = null
            currentLyricsLineViews = emptyList()
            currentLyricsScrollView = null
        }

        // Load lyrics in background thread
        Thread {
            val lyrics = LyricsManager.loadLyrics(this, track)
            runOnUiThread {
                currentLyrics = lyrics
                if (lyrics == null || lyrics.lines.isEmpty()) {
                    tvSource.text = "Sem letras encontradas"
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    tvEmpty.visibility = View.GONE
                    tvSource.text = "Letras: ${lyrics.source}"

                    val views = mutableListOf<TextView>()
                    for (line in lyrics.lines) {
                        val tvLine = TextView(this).apply {
                            text = line.text
                            textSize = 15f
                            setTextColor(Color.parseColor("#888888"))
                            gravity = Gravity.CENTER
                            setPadding(24, 20, 24, 20)
                            if (line.timeMs >= 0) {
                                setOnClickListener {
                                    playbackService?.seekTo(line.timeMs)
                                    Toast.makeText(this@MainActivity, "Saltando para ${formatTime(line.timeMs)}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        layoutLines.addView(tvLine)
                        views.add(tvLine)
                    }
                    currentLyricsLineViews = views
                }
            }
        }.start()

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            playbackService?.removeListener(this)
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
