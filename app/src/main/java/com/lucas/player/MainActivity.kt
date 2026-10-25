package com.lucas.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.lucas.player.data.LibraryRepository
import com.lucas.player.data.LyricsManager
import com.lucas.player.data.MusicDatabase
import com.lucas.player.data.PreferencesManager
import com.lucas.player.model.*
import com.lucas.player.queue.RepeatMode
import com.lucas.player.queue.ShuffleMode
import com.lucas.player.service.PlaybackService
import com.lucas.player.ui.GroupItem
import com.lucas.player.ui.components.AuraLiquidRootBox
import com.lucas.player.ui.screens.*
import com.lucas.player.ui.theme.AuraPaletteExtractor

class MainActivity : ComponentActivity(), PlaybackService.PlaybackListener {

    private val libraryRepo = LibraryRepository()
    private val musicDb by lazy { MusicDatabase(this) }
    private val preferencesManager by lazy { PreferencesManager(this) }
    private var playbackService: PlaybackService? = null
    private var isBound = false

    // Reactive Compose States
    private val currentTrackState = mutableStateOf<Track?>(null)
    private val isPlayingState = mutableStateOf(false)
    private val progressMsState = mutableLongStateOf(0L)
    private val durationMsState = mutableLongStateOf(0L)
    private val waveformPeaksState = mutableStateOf<FloatArray?>(null)
    private val shuffleModeState = mutableStateOf(ShuffleMode.OFF)
    private val repeatModeState = mutableStateOf(RepeatMode.OFF)

    // Library State
    private val currentCategoryState = mutableStateOf(LibraryCategory.ALL_SONGS)
    private val currentSortModeState = mutableStateOf(SortMode.TITLE_ASC)
    private val searchQueryState = mutableStateOf("")
    private val displayedTracksState = mutableStateOf<List<Track>>(emptyList())
    private val displayedGroupsState = mutableStateOf<List<GroupItem>>(emptyList())
    private val isInsideGroupDrilldownState = mutableStateOf(false)
    private val drilldownTitleState = mutableStateOf("")

    // Screens & Dialogs
    private val isNowPlayingExpandedState = mutableStateOf(false)
    private val showEqualizerSheetState = mutableStateOf(false)
    private val showLyricsSheetState = mutableStateOf(false)
    private val trackContextMenuState = mutableStateOf<Track?>(null)
    private val trackInfoDialogState = mutableStateOf<Track?>(null)
    private val showPriorityQueueState = mutableStateOf(false)
    private val showSleepTimerState = mutableStateOf(false)
    private val showSortOrderState = mutableStateOf(false)
    private val activeLyricsState = mutableStateOf<LyricsManager.Lyrics?>(null)

    // Equalizer & Tone
    private val eqEnabledState = mutableStateOf(true)
    private val toneEnabledState = mutableStateOf(true)
    private val preampDbState = mutableFloatStateOf(0f)
    private val bassDbState = mutableFloatStateOf(0f)
    private val trebleDbState = mutableFloatStateOf(0f)
    private val balanceState = mutableFloatStateOf(0f)
    private val crossfadeMsState = mutableIntStateOf(0)
    private val isMonoState = mutableStateOf(false)
    private val replayGainModeState = mutableIntStateOf(0)
    private val bandGainsState = mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    private val activePresetNameState = mutableStateOf("Flat")
    private val presetsListState = mutableStateOf<List<MusicDatabase.EqPreset>>(emptyList())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlaybackService.LocalBinder
            val s = binder.getService()
            playbackService = s
            isBound = true
            s.addListener(this@MainActivity)

            shuffleModeState.value = s.queueManager.shuffleMode
            repeatModeState.value = s.queueManager.repeatMode
            isPlayingState.value = s.audioEngine.isPlaying()
            syncEqualizerState()

            s.queueManager.currentTrack?.let {
                currentTrackState.value = it
                durationMsState.longValue = it.durationMs
                loadWaveformForTrack(it)
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
            Toast.makeText(this, "Permissão necessária para reproduzir músicas locais.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val currentTrack by currentTrackState
            val isPlaying by isPlayingState
            val progressMs by progressMsState
            val durationMs by durationMsState
            val waveformPeaks by waveformPeaksState
            val shuffleMode by shuffleModeState
            val repeatMode by repeatModeState
            val currentCategory by currentCategoryState
            val currentSortMode by currentSortModeState
            val searchQuery by searchQueryState
            val displayedTracks by displayedTracksState
            val displayedGroups by displayedGroupsState
            val isInsideGroupDrilldown by isInsideGroupDrilldownState
            val drilldownTitle by drilldownTitleState

            val isNowPlayingExpanded by isNowPlayingExpandedState
            val showEqualizerSheet by showEqualizerSheetState
            val showLyricsSheet by showLyricsSheetState
            val trackContextMenu by trackContextMenuState
            val trackInfoDialog by trackInfoDialogState
            val showPriorityQueue by showPriorityQueueState
            val showSleepTimer by showSleepTimerState
            val showSortOrder by showSortOrderState
            val activeLyrics by activeLyricsState

            val eqEnabled by eqEnabledState
            val toneEnabled by toneEnabledState
            val preampDb by preampDbState
            val bassDb by bassDbState
            val trebleDb by trebleDbState
            val balance by balanceState
            val crossfadeMs by crossfadeMsState
            val isMono by isMonoState
            val replayGainMode by replayGainModeState
            val activePresetName by activePresetNameState
            val presetsList by presetsListState

            val currentCover = rememberTrackCover(currentTrack, 256)
            val auraPalette = remember(currentCover) {
                AuraPaletteExtractor.extractFromBitmap(currentCover)
            }

            val categories = remember {
                listOf(
                    LibraryCategory.ALL_SONGS,
                    LibraryCategory.FOLDERS,
                    LibraryCategory.ALBUMS,
                    LibraryCategory.ARTISTS,
                    LibraryCategory.GENRES,
                    LibraryCategory.YEARS,
                    LibraryCategory.COMPOSERS,
                    LibraryCategory.QUEUE,
                    LibraryCategory.PLAYLISTS,
                    LibraryCategory.MOST_PLAYED,
                    LibraryCategory.RECENTLY_ADDED,
                    LibraryCategory.TOP_RATED
                )
            }

            BackHandler(
                enabled = isNowPlayingExpanded || showEqualizerSheet || showLyricsSheet ||
                        trackContextMenu != null || trackInfoDialog != null ||
                        showPriorityQueue || showSleepTimer || showSortOrder ||
                        isInsideGroupDrilldown || currentCategory != LibraryCategory.ALL_SONGS
            ) {
                when {
                    showEqualizerSheet -> showEqualizerSheetState.value = false
                    showLyricsSheet -> showLyricsSheetState.value = false
                    trackContextMenu != null -> trackContextMenuState.value = null
                    trackInfoDialog != null -> trackInfoDialogState.value = null
                    showPriorityQueue -> showPriorityQueueState.value = false
                    showSleepTimer -> showSleepTimerState.value = false
                    showSortOrder -> showSortOrderState.value = false
                    isNowPlayingExpanded -> isNowPlayingExpandedState.value = false
                    isInsideGroupDrilldown -> exitGroupDrilldown()
                    currentCategory != LibraryCategory.ALL_SONGS -> selectCategory(LibraryCategory.ALL_SONGS)
                }
            }

            Surface(
                color = auraPalette.deepBase,
                modifier = Modifier.fillMaxSize()
            ) {
                AuraLiquidRootBox(
                    palette = auraPalette,
                    backgroundContent = {
                        // 1. Tela da Biblioteca (Fundo)
                        LibraryScreen(
                            currentCategory = currentCategory,
                            categories = categories,
                            onSelectCategory = { selectCategory(it) },
                            searchQuery = searchQuery,
                            onSearchQueryChange = { onSearchQueryChange(it) },
                            displayedTracks = displayedTracks,
                            displayedGroups = displayedGroups,
                            isInsideGroupDrilldown = isInsideGroupDrilldown,
                            drilldownTitle = drilldownTitle,
                            onBackFromDrilldown = { exitGroupDrilldown() },
                            onTrackClick = { _, position ->
                                playbackService?.playQueue(displayedTracks, position)
                                isNowPlayingExpandedState.value = true
                            },
                            onTrackLongClick = { track ->
                                trackContextMenuState.value = track
                            },
                            onGroupClick = { group ->
                                drillDownGroup(group.title)
                            },
                            currentPlayingId = currentTrack?.id ?: -1L,
                            onSortClick = { showSortOrderState.value = true },
                            modifier = Modifier.fillMaxSize()
                        )

                        // 2. Now Playing em Tela Cheia
                        AnimatedVisibility(
                            visible = isNowPlayingExpanded,
                            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(380)) + fadeIn(animationSpec = tween(300)),
                            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(350)) + fadeOut(animationSpec = tween(250)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            NowPlayingScreen(
                                track = currentTrack,
                                isPlaying = isPlaying,
                                progressMs = progressMs,
                                durationMs = durationMs,
                                waveformPeaks = waveformPeaks,
                                shuffleMode = shuffleMode,
                                repeatMode = repeatMode,
                                onPlayPause = { playbackService?.togglePlayPause() },
                                onNext = { playbackService?.playNext() },
                                onPrevious = { playbackService?.playPrevious() },
                                onSeek = { fraction ->
                                    val dur = durationMs.coerceAtLeast(1L)
                                    val target = (fraction * dur).toLong()
                                    playbackService?.seekTo(target)
                                    progressMsState.longValue = target
                                },
                                onToggleShuffle = {
                                    val newMode = playbackService?.toggleShuffleMode() ?: ShuffleMode.OFF
                                    shuffleModeState.value = newMode
                                    Toast.makeText(this@MainActivity, newMode.displayName, Toast.LENGTH_SHORT).show()
                                },
                                onToggleRepeat = {
                                    val newMode = playbackService?.toggleRepeat() ?: RepeatMode.OFF
                                    repeatModeState.value = newMode
                                    Toast.makeText(this@MainActivity, newMode.displayName, Toast.LENGTH_SHORT).show()
                                },
                                onCollapse = { isNowPlayingExpandedState.value = false },
                                onOpenLyrics = {
                                    loadLyricsForCurrentTrack(currentTrack)
                                    showLyricsSheetState.value = true
                                },
                                onOpenEqualizer = {
                                    syncEqualizerState()
                                    showEqualizerSheetState.value = true
                                },
                                onOpenQueue = { showPriorityQueueState.value = true },
                                onOpenSleepTimer = { showSleepTimerState.value = true },
                                onOpenTrackInfo = { track -> trackInfoDialogState.value = track },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    },
                    glassOverlays = {
                        // Mini Player Flutuante + Bottom Nav (Visível quando Now Playing estiver recolhido)
                        AnimatedVisibility(
                            visible = !isNowPlayingExpanded,
                            enter = fadeIn(animationSpec = tween(250)),
                            exit = fadeOut(animationSpec = tween(200)),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            FloatingMiniPlayerAndNav(
                                track = currentTrack,
                                isPlaying = isPlaying,
                                progressMs = progressMs,
                                durationMs = durationMs,
                                onMiniPlayerClick = { isNowPlayingExpandedState.value = true },
                                onPlayPauseClick = { playbackService?.togglePlayPause() },
                                onNextClick = { playbackService?.playNext() },
                                onEqualizerClick = {
                                    syncEqualizerState()
                                    showEqualizerSheetState.value = true
                                },
                                onLyricsClick = {
                                    loadLyricsForCurrentTrack(currentTrack)
                                    showLyricsSheetState.value = true
                                },
                                onQueueClick = { showPriorityQueueState.value = true },
                                onSleepTimerClick = { showSleepTimerState.value = true }
                            )
                        }

                        // Equalizador Studio em Sheet (Refrata o fundo com blur profundo)
                        AnimatedVisibility(
                            visible = showEqualizerSheet,
                            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(380)) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(350)) + fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            GlassEqualizerSheet(
                                eqEnabled = eqEnabled,
                                onToggleEq = { enabled ->
                                    eqEnabledState.value = enabled
                                    playbackService?.setEqualizerEnabled(enabled)
                                    preferencesManager.saveEqualizerEnabled(enabled)
                                },
                                toneEnabled = toneEnabled,
                                onToggleTone = { enabled ->
                                    toneEnabledState.value = enabled
                                    playbackService?.setToneEnabled(enabled)
                                    preferencesManager.saveToneEnabled(enabled)
                                },
                                preampDb = preampDb,
                                onPreampChange = { db ->
                                    preampDbState.floatValue = db
                                    playbackService?.setPreamp(db)
                                    preferencesManager.savePreamp(db)
                                },
                                bassDb = bassDb,
                                onBassChange = { db ->
                                    bassDbState.floatValue = db
                                    playbackService?.setBass(db)
                                    preferencesManager.saveBass(db)
                                    if (!toneEnabled) {
                                        toneEnabledState.value = true
                                        playbackService?.setToneEnabled(true)
                                        preferencesManager.saveToneEnabled(true)
                                    }
                                },
                                trebleDb = trebleDb,
                                onTrebleChange = { db ->
                                    trebleDbState.floatValue = db
                                    playbackService?.setTreble(db)
                                    preferencesManager.saveTreble(db)
                                    if (!toneEnabled) {
                                        toneEnabledState.value = true
                                        playbackService?.setToneEnabled(true)
                                        preferencesManager.saveToneEnabled(true)
                                    }
                                },
                                balance = balance,
                                onBalanceChange = { bal ->
                                    balanceState.floatValue = bal
                                    playbackService?.setBalance(bal)
                                },
                                crossfadeMs = crossfadeMs,
                                onCrossfadeChange = { ms ->
                                    crossfadeMsState.intValue = ms
                                    playbackService?.setCrossfadeDurationMs(ms)
                                },
                                isMono = isMono,
                                onToggleMono = { mono ->
                                    isMonoState.value = mono
                                    playbackService?.setMono(mono)
                                },
                                replayGainMode = replayGainMode,
                                onReplayGainModeChange = { mode ->
                                    replayGainModeState.intValue = mode
                                    playbackService?.setReplayGainMode(mode)
                                },
                                bandGains = bandGainsState,
                                onBandGainChange = { band, db ->
                                    if (band in bandGainsState.indices) {
                                        bandGainsState[band] = db
                                    }
                                    playbackService?.setEqualizerBand(band, db)
                                    preferencesManager.saveEqualizerBand(band, db)
                                },
                                activePresetName = activePresetName,
                                presets = presetsList,
                                onSelectPreset = { preset -> applyFullPreset(preset) },
                                onSavePreset = { name -> saveCustomPreset(name) },
                                onClose = { showEqualizerSheetState.value = false }
                            )
                        }

                        // Letras Sincronizadas
                        AnimatedVisibility(
                            visible = showLyricsSheet,
                            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(380)) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(350)) + fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            GlassLyricsScreen(
                                track = currentTrack,
                                lyrics = activeLyrics,
                                currentMs = progressMs,
                                onSeekToTime = { targetMs ->
                                    playbackService?.seekTo(targetMs)
                                    progressMsState.longValue = targetMs
                                },
                                onClose = { showLyricsSheetState.value = false }
                            )
                        }

                        // Modais com Vidro Real e Refração
                        trackContextMenu?.let { track ->
                            TrackContextMenuSheet(
                                track = track,
                                onPlayNext = {
                                    playbackService?.enqueueNext(track)
                                    trackContextMenuState.value = null
                                    Toast.makeText(this@MainActivity, "Adicionada para tocar a seguir", Toast.LENGTH_SHORT).show()
                                },
                                onEnqueueEnd = {
                                    playbackService?.enqueueEnd(track)
                                    trackContextMenuState.value = null
                                    Toast.makeText(this@MainActivity, "Adicionada ao fim da fila", Toast.LENGTH_SHORT).show()
                                },
                                onFavorite = {
                                    libraryRepo.setTrackRating(track.id, 5)
                                    trackContextMenuState.value = null
                                    Toast.makeText(this@MainActivity, "Adicionada às Favoritas", Toast.LENGTH_SHORT).show()
                                },
                                onShowInfo = {
                                    trackContextMenuState.value = null
                                    trackInfoDialogState.value = track
                                },
                                onDismiss = { trackContextMenuState.value = null }
                            )
                        }

                        trackInfoDialog?.let { track ->
                            TrackInfoDialog(
                                track = track,
                                onDismiss = { trackInfoDialogState.value = null }
                            )
                        }

                        if (showPriorityQueue) {
                            PriorityQueueDialog(
                                queueTracks = playbackService?.queueManager?.getPriorityQueue() ?: emptyList(),
                                currentTrackId = currentTrack?.id ?: -1L,
                                onTrackClick = { track, pos ->
                                    playbackService?.queueManager?.getPriorityQueue()?.let { q ->
                                        playbackService?.playQueue(q, pos)
                                    }
                                },
                                onClearQueue = {
                                    playbackService?.queueManager?.clearPriorityQueue()
                                    showPriorityQueueState.value = false
                                    Toast.makeText(this@MainActivity, "Fila prioritária limpa", Toast.LENGTH_SHORT).show()
                                },
                                onDismiss = { showPriorityQueueState.value = false }
                            )
                        }

                        if (showSleepTimer) {
                            SleepTimerDialog(
                                remainingSeconds = playbackService?.getSleepTimerRemainingSeconds() ?: 0,
                                onStartTimer = { min ->
                                    playbackService?.startSleepTimer(min)
                                    Toast.makeText(this@MainActivity, "Desligará em $min minutos", Toast.LENGTH_SHORT).show()
                                },
                                onCancelTimer = {
                                    playbackService?.cancelSleepTimer()
                                    Toast.makeText(this@MainActivity, "Temporizador cancelado", Toast.LENGTH_SHORT).show()
                                },
                                onDismiss = { showSleepTimerState.value = false }
                            )
                        }

                        if (showSortOrder) {
                            SortOrderDialog(
                                currentSortMode = currentSortMode,
                                onSelectSortMode = { onSelectSortMode(it) },
                                onDismiss = { showSortOrderState.value = false }
                            )
                        }
                    }
                )
            }
        }

        startAndBindService()
        checkAndRequestPermissions()
    }

    private fun selectCategory(category: LibraryCategory) {
        currentCategoryState.value = category
        isInsideGroupDrilldownState.value = false
        drilldownTitleState.value = ""
        searchQueryState.value = ""
        renderCurrentCategory()
    }

    private fun renderCurrentCategory() {
        when (currentCategoryState.value) {
            LibraryCategory.ALL_SONGS -> {
                val list = libraryRepo.applySorting(libraryRepo.getAllTracks(), currentSortModeState.value)
                displayedTracksState.value = filterTracks(list, searchQueryState.value)
                displayedGroupsState.value = emptyList()
            }
            LibraryCategory.FOLDERS -> {
                val folders = libraryRepo.getFolders()
                displayedGroupsState.value = folders.map { GroupItem(it.name, it.path, "${it.trackCount} faixas") }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.ALBUMS -> {
                val albums = libraryRepo.getAlbums()
                displayedGroupsState.value = albums.map {
                    val yearStr = if (it.year > 0) " • ${it.year}" else ""
                    GroupItem(it.name, "${it.artist}$yearStr", "${it.trackCount} faixas")
                }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.ARTISTS -> {
                val artists = libraryRepo.getArtists()
                displayedGroupsState.value = artists.map { GroupItem(it.name, "Artista", "${it.trackCount} faixas") }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.ALBUM_ARTISTS -> {
                val artists = libraryRepo.getAlbumArtists()
                displayedGroupsState.value = artists.map { GroupItem(it.name, "Artista do Álbum", "${it.trackCount} faixas") }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.GENRES -> {
                val genres = libraryRepo.getGenres()
                displayedGroupsState.value = genres.map { GroupItem(it.name, "Gênero", "${it.trackCount} faixas") }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.YEARS -> {
                val years = libraryRepo.getYears()
                displayedGroupsState.value = years.map { GroupItem(it.year.toString(), "Ano de Lançamento", "${it.trackCount} faixas") }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.COMPOSERS -> {
                val comps = libraryRepo.getComposers()
                displayedGroupsState.value = comps.map { GroupItem(it.name, "Compositor", "${it.trackCount} faixas") }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.QUEUE -> {
                val queueTracks = playbackService?.queueManager?.getPriorityQueue() ?: emptyList()
                displayedTracksState.value = filterTracks(queueTracks, searchQueryState.value)
                displayedGroupsState.value = emptyList()
            }
            LibraryCategory.PLAYLISTS -> {
                val pls = libraryRepo.getUserPlaylists()
                displayedGroupsState.value = pls.map { GroupItem(it.name, "Playlist", "${it.trackCount} faixas") }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.MOST_PLAYED -> {
                val list = libraryRepo.getMostPlayed()
                displayedTracksState.value = filterTracks(list, searchQueryState.value)
                displayedGroupsState.value = emptyList()
            }
            LibraryCategory.RECENTLY_ADDED -> {
                val list = libraryRepo.getRecentlyAdded()
                displayedTracksState.value = filterTracks(list, searchQueryState.value)
                displayedGroupsState.value = emptyList()
            }
            LibraryCategory.RECENTLY_PLAYED -> {
                val list = libraryRepo.getRecentlyPlayed()
                displayedTracksState.value = filterTracks(list, searchQueryState.value)
                displayedGroupsState.value = emptyList()
            }
            LibraryCategory.TOP_RATED -> {
                val list = libraryRepo.getTopRated()
                displayedTracksState.value = filterTracks(list, searchQueryState.value)
                displayedGroupsState.value = emptyList()
            }
        }
    }

    private fun drillDownGroup(groupName: String) {
        val tracks = when (currentCategoryState.value) {
            LibraryCategory.ALBUMS -> libraryRepo.getTracksForAlbum(groupName, currentSortModeState.value)
            LibraryCategory.ARTISTS -> libraryRepo.getTracksForArtist(groupName, currentSortModeState.value)
            LibraryCategory.ALBUM_ARTISTS -> libraryRepo.getTracksForAlbumArtist(groupName, currentSortModeState.value)
            LibraryCategory.FOLDERS -> libraryRepo.getTracksForFolder(groupName, currentSortModeState.value)
            LibraryCategory.GENRES -> libraryRepo.getTracksForGenre(groupName, currentSortModeState.value)
            LibraryCategory.YEARS -> groupName.toIntOrNull()?.let { libraryRepo.getTracksForYear(it, currentSortModeState.value) } ?: emptyList()
            LibraryCategory.COMPOSERS -> libraryRepo.getTracksForComposer(groupName, currentSortModeState.value)
            LibraryCategory.PLAYLISTS -> {
                val pl = libraryRepo.getUserPlaylists().firstOrNull { it.name == groupName }
                if (pl != null) libraryRepo.getTracksForUserPlaylist(pl.id) else emptyList()
            }
            else -> emptyList()
        }

        if (tracks.isNotEmpty()) {
            isInsideGroupDrilldownState.value = true
            drilldownTitleState.value = groupName
            displayedTracksState.value = filterTracks(tracks, searchQueryState.value)
            displayedGroupsState.value = emptyList()
        }
    }

    private fun exitGroupDrilldown() {
        isInsideGroupDrilldownState.value = false
        drilldownTitleState.value = ""
        renderCurrentCategory()
    }

    private fun filterTracks(list: List<Track>, query: String): List<Track> {
        if (query.isEmpty()) return list
        return list.filter { track ->
            track.title.contains(query, ignoreCase = true) ||
            track.artist.contains(query, ignoreCase = true) ||
            track.album.contains(query, ignoreCase = true) ||
            track.genre.contains(query, ignoreCase = true) ||
            track.composer.contains(query, ignoreCase = true)
        }
    }

    private fun onSearchQueryChange(query: String) {
        searchQueryState.value = query
        if (isInsideGroupDrilldownState.value) {
            drillDownGroup(drilldownTitleState.value)
        } else {
            renderCurrentCategory()
        }
    }

    private fun onSelectSortMode(mode: SortMode) {
        currentSortModeState.value = mode
        if (isInsideGroupDrilldownState.value) {
            drillDownGroup(drilldownTitleState.value)
        } else {
            renderCurrentCategory()
        }
    }

    private fun syncEqualizerState() {
        val service = playbackService ?: return
        eqEnabledState.value = service.isEqualizerEnabled()
        toneEnabledState.value = service.isToneEnabled()
        preampDbState.floatValue = service.getPreamp()
        bassDbState.floatValue = service.getBass()
        trebleDbState.floatValue = service.getTreble()
        balanceState.floatValue = service.getBalance()
        crossfadeMsState.intValue = service.getCrossfadeDurationMs()
        isMonoState.value = service.isMono()
        replayGainModeState.intValue = service.getReplayGainMode()
        activePresetNameState.value = preferencesManager.getActivePresetName()
        presetsListState.value = musicDb.getAllPresets()

        bandGainsState.clear()
        for (i in 0 until 10) {
            bandGainsState.add(service.getEqualizerBand(i))
        }
    }

    private fun applyFullPreset(preset: MusicDatabase.EqPreset) {
        val service = playbackService ?: return
        activePresetNameState.value = preset.name
        preferencesManager.saveActivePresetName(preset.name)

        eqEnabledState.value = true
        service.setEqualizerEnabled(true)
        preferencesManager.saveEqualizerEnabled(true)

        preset.bandGains.forEachIndexed { index, gainDb ->
            if (index < 10) {
                if (index < bandGainsState.size) bandGainsState[index] = gainDb
                service.setEqualizerBand(index, gainDb)
                preferencesManager.saveEqualizerBand(index, gainDb)
            }
        }

        bassDbState.floatValue = preset.bassGain
        service.setBass(preset.bassGain)
        preferencesManager.saveBass(preset.bassGain)

        trebleDbState.floatValue = preset.trebleGain
        service.setTreble(preset.trebleGain)
        preferencesManager.saveTreble(preset.trebleGain)

        preampDbState.floatValue = preset.preampGain
        service.setPreamp(preset.preampGain)
        preferencesManager.savePreamp(preset.preampGain)

        if (preset.bassGain != 0f || preset.trebleGain != 0f) {
            toneEnabledState.value = true
            service.setToneEnabled(true)
            preferencesManager.saveToneEnabled(true)
        }
    }

    private fun saveCustomPreset(name: String) {
        val service = playbackService ?: return
        val gains = (0 until 10).map { service.getEqualizerBand(it) }
        musicDb.savePreset(name, gains, service.getBass(), service.getTreble(), service.getPreamp())
        preferencesManager.saveActivePresetName(name)
        activePresetNameState.value = name
        presetsListState.value = musicDb.getAllPresets()
        Toast.makeText(this, "Preset '$name' salvo com sucesso!", Toast.LENGTH_SHORT).show()
    }

    private fun loadLyricsForCurrentTrack(track: Track?) {
        if (track == null) {
            activeLyricsState.value = null
            return
        }
        Thread {
            val lyrics = LyricsManager.loadLyrics(this, track)
            runOnUiThread {
                activeLyricsState.value = lyrics
            }
        }.start()
    }

    private fun loadWaveformForTrack(track: Track) {
        waveformPeaksState.value = null
        val uri = track.uri
        val service = playbackService ?: return
        Thread {
            try {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val peaks = service.audioEngine.scanWaveform(pfd, 70)
                    if (peaks != null && peaks.isNotEmpty()) {
                        runOnUiThread {
                            if (playbackService?.queueManager?.currentTrack?.id == track.id) {
                                waveformPeaksState.value = peaks
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    // PlaybackService.PlaybackListener Callbacks
    override fun onTrackChanged(track: Track?) {
        runOnUiThread {
            currentTrackState.value = track
            isPlayingState.value = playbackService?.audioEngine?.isPlaying() ?: false
            if (track != null) {
                durationMsState.longValue = track.durationMs
                loadWaveformForTrack(track)
                if (showLyricsSheetState.value) {
                    loadLyricsForCurrentTrack(track)
                }
            } else {
                durationMsState.longValue = 0L
                waveformPeaksState.value = null
                activeLyricsState.value = null
            }
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            isPlayingState.value = isPlaying
        }
    }

    override fun onProgressUpdate(currentMs: Long, durationMs: Long) {
        runOnUiThread {
            progressMsState.longValue = currentMs
            if (durationMs > 0) durationMsState.longValue = durationMs
        }
    }

    override fun onQueueUpdated(tracks: List<Track>, currentIndex: Int) {
        runOnUiThread {
            if (currentCategoryState.value == LibraryCategory.QUEUE) {
                displayedTracksState.value = tracks
            }
        }
    }

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

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            playbackService?.removeListener(this)
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
