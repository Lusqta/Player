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
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lucas.player.data.CoverArtLoader
import com.lucas.player.data.LibraryRepository
import com.lucas.player.data.LyricsManager
import com.lucas.player.data.MusicDatabase
import com.lucas.player.data.PreferencesManager
import com.lucas.player.model.*
import com.lucas.player.queue.RepeatMode
import com.lucas.player.queue.ShuffleMode
import com.lucas.player.service.PlaybackService
import com.lucas.player.ui.GroupItem
import com.lucas.player.ui.components.*
import com.lucas.player.ui.navigation.NavScreen
import com.lucas.player.ui.navigation.PlayerNavController
import com.lucas.player.ui.screens.*
import com.lucas.player.ui.theme.*

class MainActivity : ComponentActivity(), PlaybackService.PlaybackListener {

    private val libraryRepo = LibraryRepository()
    private val musicDb by lazy { MusicDatabase(this) }
    private val preferencesManager by lazy { PreferencesManager(this) }
    private var playbackService: PlaybackService? = null
    private var isBound = false
    private val snackbarState = AuroSnackbarState()

    // Controlador de Navegação com Pilha de Histórico estilo Poweramp
    private val navController = PlayerNavController(NavScreen.LibraryRoot)

    // Estados Reativos do Player
    private val currentTrackState = mutableStateOf<Track?>(null)
    private val isPlayingState = mutableStateOf(false)
    private val progressMsState = mutableLongStateOf(0L)
    private val durationMsState = mutableLongStateOf(0L)
    private val shuffleModeState = mutableStateOf(ShuffleMode.OFF)
    private val repeatModeState = mutableStateOf(RepeatMode.OFF)

    // Estados da Biblioteca
    private val totalTracksCountState = mutableIntStateOf(0)
    private val currentCategoryState = mutableStateOf(LibraryCategory.ALL_SONGS)
    private val currentSortModeState = mutableStateOf(SortMode.TITLE_ASC)
    private val displayedTracksState = mutableStateOf<List<Track>>(emptyList())
    private val displayedGroupsState = mutableStateOf<List<GroupItem>>(emptyList())
    private val drilldownTracksState = mutableStateOf<List<Track>>(emptyList())

    // Busca Instantânea
    private val searchQueryState = mutableStateOf("")
    private val searchResultsState = mutableStateOf<List<Track>>(emptyList())

    // Diálogos & Modais
    private val trackContextMenuState = mutableStateOf<Track?>(null)
    private val trackInfoDialogState = mutableStateOf<Track?>(null)
    private val showSortOrderState = mutableStateOf(false)
    private val showSleepTimerState = mutableStateOf(false)

    // Equalizador & Áudio
    private val eqEnabledState = mutableStateOf(true)
    private val toneEnabledState = mutableStateOf(true)
    private val preampDbState = mutableFloatStateOf(0f)
    private val bassDbState = mutableFloatStateOf(0f)
    private val trebleDbState = mutableFloatStateOf(0f)
    private val balanceState = mutableFloatStateOf(0f)
    private val isMonoState = mutableStateOf(false)
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
            }

            val all = libraryRepo.getAllTracks()
            if (all.isNotEmpty()) {
                s.restoreSavedQueue(all)
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
            snackbarState.show("Permissão necessária para ler músicas locais.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuroHaptics.init(this)
        LibraryRepository.init(this)

        setContent {
            val currentScreen = navController.currentScreen
            val currentTrack by currentTrackState
            val isPlaying by isPlayingState
            val progressMs by progressMsState
            val durationMs by durationMsState
            val shuffleMode by shuffleModeState
            val repeatMode by repeatModeState

            val totalTracksCount by totalTracksCountState
            val displayedTracks by displayedTracksState
            val displayedGroups by displayedGroupsState
            val drilldownTracks by drilldownTracksState

            val searchQuery by searchQueryState
            val searchResults by searchResultsState

            val trackContextMenu by trackContextMenuState
            val trackInfoDialog by trackInfoDialogState
            val showSortOrder by showSortOrderState
            val showSleepTimer by showSleepTimerState
            val showMenuSheetState = remember { mutableStateOf(false) }
            val showAudioInfoState = remember { mutableStateOf(false) }
            val showAboutState = remember { mutableStateOf(false) }
            val addToPlaylistDialogTrack = remember { mutableStateOf<Track?>(null) }
            val showLyricsSheetState = remember { mutableStateOf(false) }
            val showSavePresetDialogState = remember { mutableStateOf(false) }
            val deletePlaylistTargetState = remember { mutableStateOf<GroupItem?>(null) }
            val deletePresetTargetState = remember { mutableStateOf<MusicDatabase.EqPreset?>(null) }

            val eqEnabled by eqEnabledState
            val toneEnabled by toneEnabledState
            val preampDb by preampDbState
            val bassDb by bassDbState
            val trebleDb by trebleDbState
            val balance by balanceState
            val isMono by isMonoState
            val activePresetName by activePresetNameState
            val presetsList by presetsListState

            val coroutineScope = rememberCoroutineScope()
            DisposableEffect(coroutineScope) {
                snackbarState.bindScope(coroutineScope)
                onDispose { }
            }

            val currentCover = rememberTrackCover(currentTrack, 350)
            val miniPlayerCover = rememberTrackCover(currentTrack, 120)

            var currentAccent by remember { mutableStateOf(AuroAccent.Neutral) }
            LaunchedEffect(currentCover) {
                currentAccent = CoverGlow.from(currentCover)
            }

            // Tratamento do Botão Físico/Gestual Voltar do Android
            BackHandler(
                enabled = trackContextMenu != null || trackInfoDialog != null ||
                        showSortOrder || showSleepTimer || showMenuSheetState.value ||
                        showAudioInfoState.value || showAboutState.value ||
                        showLyricsSheetState.value || showSavePresetDialogState.value ||
                        deletePlaylistTargetState.value != null || deletePresetTargetState.value != null ||
                        addToPlaylistDialogTrack.value != null || navController.canNavigateUp
            ) {
                when {
                    showLyricsSheetState.value -> showLyricsSheetState.value = false
                    showSavePresetDialogState.value -> showSavePresetDialogState.value = false
                    deletePlaylistTargetState.value != null -> deletePlaylistTargetState.value = null
                    deletePresetTargetState.value != null -> deletePresetTargetState.value = null
                    addToPlaylistDialogTrack.value != null -> addToPlaylistDialogTrack.value = null
                    trackContextMenu != null -> trackContextMenuState.value = null
                    trackInfoDialog != null -> trackInfoDialogState.value = null
                    showSortOrder -> showSortOrderState.value = false
                    showSleepTimer -> showSleepTimerState.value = false
                    showMenuSheetState.value -> showMenuSheetState.value = false
                    showAudioInfoState.value -> showAudioInfoState.value = false
                    showAboutState.value -> showAboutState.value = false
                    navController.canNavigateUp -> navController.navigateUp()
                }
            }

            AuroTheme(
                accent = currentAccent,
                playing = isPlaying
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AuroColor.Black)
                ) {
                    // Conteúdo Principal de Acordo com a Tela Ativa
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (currentScreen is NavScreen.NowPlaying) 58.dp else if (currentTrack != null) 118.dp else 58.dp)
                    ) {
                        when (currentScreen) {
                            is NavScreen.LibraryRoot -> {
                                AuroLibraryRootScreen(
                                    totalTracksCount = totalTracksCount,
                                    onSelectCategory = { cat ->
                                        selectCategory(cat)
                                        navController.navigateTo(NavScreen.CategoryList(cat))
                                    },
                                    onSortClick = { showSortOrderState.value = true },
                                    onRescanClick = { loadLibrary() },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.CategoryList -> {
                                if (currentScreen.category == LibraryCategory.QUEUE) {
                                    val priorityTracks = playbackService?.queueManager?.getPriorityQueue() ?: emptyList()
                                    val regularTracks = playbackService?.queueManager?.getTracks() ?: emptyList()
                                    AuroQueueScreen(
                                        priorityTracks = priorityTracks,
                                        regularTracks = regularTracks,
                                        currentTrackId = currentTrack?.id ?: -1L,
                                        onBackClick = { navController.navigateUp() },
                                        onTrackClick = { _, index, isPriority ->
                                            if (isPriority) {
                                                playbackService?.skipToPriorityIndex(index)
                                            } else {
                                                playbackService?.skipToQueueIndex(index)
                                            }
                                            navController.openNowPlaying()
                                        },
                                        onRemoveTrack = { track ->
                                            playbackService?.removeTrackFromQueue(track.id)
                                            renderCurrentCategory()
                                        },
                                        onClearQueue = {
                                            playbackService?.clearPriorityQueue()
                                            renderCurrentCategory()
                                            snackbarState.show("Fila limpa")
                                        },
                                        onShuffleQueue = {
                                            playbackService?.shuffleCurrentQueue()
                                            renderCurrentCategory()
                                            snackbarState.show("Fila aleatória")
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    AuroCategoryListScreen(
                                        category = currentScreen.category,
                                        displayedTracks = displayedTracks,
                                        displayedGroups = displayedGroups,
                                        currentPlayingId = currentTrack?.id ?: -1L,
                                        onBackClick = { navController.navigateUp() },
                                        onTrackClick = { _, position ->
                                            playbackService?.playQueue(displayedTracks, position)
                                            navController.openNowPlaying()
                                        },
                                        onTrackLongClick = { track ->
                                            trackContextMenuState.value = track
                                        },
                                        onTrackMenuClick = { track ->
                                            trackContextMenuState.value = track
                                        },
                                        onGroupClick = { group ->
                                            drillDownGroup(currentScreen.category, group.title)
                                            navController.navigateTo(
                                                NavScreen.Drilldown(
                                                    category = currentScreen.category,
                                                    title = group.title,
                                                    subtitle = group.subtitle
                                                )
                                            )
                                        },
                                        onGroupLongClick = { group ->
                                            if (currentScreen.category == LibraryCategory.PLAYLISTS) {
                                                deletePlaylistTargetState.value = group
                                            }
                                        },
                                        onPlayAll = {
                                            if (displayedTracks.isNotEmpty()) {
                                                playbackService?.playQueue(displayedTracks, 0)
                                                navController.openNowPlaying()
                                            }
                                        },
                                        onShuffleAll = {
                                            if (displayedTracks.isNotEmpty()) {
                                                playbackService?.queueManager?.setShuffleMode(ShuffleMode.ALL_TRACKS)
                                                shuffleModeState.value = ShuffleMode.ALL_TRACKS
                                                playbackService?.playQueue(displayedTracks.shuffled(), 0)
                                                navController.openNowPlaying()
                                            }
                                        },
                                        onCreatePlaylist = {
                                            val dummy = currentTrack ?: displayedTracks.firstOrNull()
                                            if (dummy != null) {
                                                addToPlaylistDialogTrack.value = dummy
                                            }
                                        },
                                        onSortClick = { showSortOrderState.value = true },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            is NavScreen.Drilldown -> {
                                AuroDrilldownScreen(
                                    category = currentScreen.category,
                                    groupTitle = currentScreen.title,
                                    tracks = drilldownTracks,
                                    currentPlayingId = currentTrack?.id ?: -1L,
                                    onBackToCategoryList = { navController.navigateUp() },
                                    onBackToLibraryRoot = { navController.resetToLibraryRoot() },
                                    onPlayAll = {
                                        if (drilldownTracks.isNotEmpty()) {
                                            playbackService?.playQueue(drilldownTracks, 0)
                                            navController.openNowPlaying()
                                        }
                                    },
                                    onShuffleAll = {
                                        if (drilldownTracks.isNotEmpty()) {
                                            playbackService?.queueManager?.setShuffleMode(ShuffleMode.ALL_TRACKS)
                                            shuffleModeState.value = ShuffleMode.ALL_TRACKS
                                            playbackService?.playQueue(drilldownTracks.shuffled(), 0)
                                            navController.openNowPlaying()
                                        }
                                    },
                                    onTrackClick = { _, position ->
                                        playbackService?.playQueue(drilldownTracks, position)
                                        navController.openNowPlaying()
                                    },
                                    onTrackLongClick = { track ->
                                        trackContextMenuState.value = track
                                    },
                                    onTrackMenuClick = { track ->
                                        trackContextMenuState.value = track
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.NowPlaying -> {
                                val currentIndex = playbackService?.queueManager?.currentQueueIndex ?: 0
                                val totalQueue = playbackService?.queueManager?.queueSize ?: 0
                                val upcoming = playbackService?.queueManager?.getUpcomingTracks(2) ?: emptyList()
                                AuroNowPlayingScreen(
                                    track = currentTrack,
                                    isPlaying = isPlaying,
                                    progressMs = progressMs,
                                    durationMs = durationMs,
                                    coverBitmap = currentCover,
                                    shuffleMode = shuffleMode,
                                    repeatMode = repeatMode,
                                    currentTrackIndex = currentIndex,
                                    queueSize = totalQueue,
                                    categoryTitle = currentCategoryState.value.title,
                                    upcomingTracks = upcoming,
                                    onPlayPause = { playbackService?.togglePlayPause() },
                                    onNext = { playbackService?.playNext() },
                                    onPrevious = { playbackService?.playPrevious() },
                                    onSeek = { fraction ->
                                        val dur = durationMs.coerceAtLeast(1L)
                                        val target = (fraction * dur).toLong()
                                        playbackService?.seekTo(target)
                                        progressMsState.longValue = target
                                    },
                                    onSeekJumpForward = { playbackService?.seekJumpForward() },
                                    onSeekJumpBackward = { playbackService?.seekJumpBackward() },
                                    onBeginFastForward = { playbackService?.beginFastForward() },
                                    onEndFastForward = { playbackService?.endFastForward() },
                                    onBeginRewind = { playbackService?.beginRewind() },
                                    onEndRewind = { playbackService?.endRewind() },
                                    onRatingChanged = { r -> setTrackRating(currentTrack, r) },
                                    onToggleShuffle = {
                                        val newMode = playbackService?.toggleShuffleMode() ?: ShuffleMode.OFF
                                        shuffleModeState.value = newMode
                                        snackbarState.show(newMode.displayName)
                                    },
                                    onToggleRepeat = {
                                        val newMode = playbackService?.toggleRepeat() ?: RepeatMode.OFF
                                        repeatModeState.value = newMode
                                        snackbarState.show(newMode.displayName)
                                    },
                                    onCollapse = { navController.navigateUp() },
                                    onOpenEqualizer = { navController.navigateTo(NavScreen.Equalizer) },
                                    onOpenLyrics = { showLyricsSheetState.value = true },
                                    onOpenQueue = {
                                        selectCategory(LibraryCategory.QUEUE)
                                        navController.navigateTo(NavScreen.CategoryList(LibraryCategory.QUEUE))
                                    },
                                    onOpenTrackInfo = {
                                        trackInfoDialogState.value = currentTrack
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.Equalizer -> {
                                AuroEqualizerScreen(
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
                                    isMono = isMono,
                                    onToggleMono = { mono ->
                                        isMonoState.value = mono
                                        playbackService?.setMono(mono)
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
                                    onResetEq = { resetEqualizerToFlat() },
                                    onSavePreset = { showSavePresetDialogState.value = true },
                                    onDeletePreset = { preset -> deletePresetTargetState.value = preset },
                                    onClose = { navController.navigateUp() },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.Search -> {
                                AuroSearchScreen(
                                    searchQuery = searchQuery,
                                    onQueryChange = { q -> onSearchQueryChange(q) },
                                    results = searchResults,
                                    currentPlayingId = currentTrack?.id ?: -1L,
                                    onTrackClick = { _, position ->
                                        playbackService?.playQueue(searchResults, position)
                                        navController.openNowPlaying()
                                    },
                                    onTrackMenuClick = { track ->
                                        trackContextMenuState.value = track
                                    },
                                    onClose = { navController.navigateUp() },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.Queue -> {
                                val priorityTracks = playbackService?.queueManager?.getPriorityQueue() ?: emptyList()
                                val regularTracks = playbackService?.queueManager?.getTracks() ?: emptyList()
                                AuroQueueScreen(
                                    priorityTracks = priorityTracks,
                                    regularTracks = regularTracks,
                                    currentTrackId = currentTrack?.id ?: -1L,
                                    onBackClick = { navController.navigateUp() },
                                    onTrackClick = { _, index, isPriority ->
                                        if (isPriority) {
                                            playbackService?.skipToPriorityIndex(index)
                                        } else {
                                            playbackService?.skipToQueueIndex(index)
                                        }
                                        navController.openNowPlaying()
                                    },
                                    onRemoveTrack = { track ->
                                        playbackService?.removeTrackFromQueue(track.id)
                                        renderCurrentCategory()
                                    },
                                    onClearQueue = {
                                        playbackService?.clearPriorityQueue()
                                        renderCurrentCategory()
                                        snackbarState.show("Fila limpa")
                                    },
                                    onShuffleQueue = {
                                        playbackService?.shuffleCurrentQueue()
                                        renderCurrentCategory()
                                        snackbarState.show("Fila aleatória")
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // AuroSnackbarHost posicionado acima do mini player / navbar
                    val snackbarBottomPadding = if (currentScreen is NavScreen.NowPlaying) {
                        16.dp
                    } else if (currentTrack != null) {
                        142.dp
                    } else {
                        70.dp
                    }

                    AuroSnackbarHost(
                        state = snackbarState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = snackbarBottomPadding)
                    )

                    // Mini Player e Navbar Inferior Persistente (Estilo Poweramp presente em todas as telas)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        if (currentScreen !is NavScreen.NowPlaying && currentTrack != null) {
                            AuroMiniPlayer(
                                track = currentTrack,
                                isPlaying = isPlaying,
                                progressMs = progressMs,
                                durationMs = durationMs,
                                coverBitmap = miniPlayerCover,
                                onOpenNowPlaying = { navController.openNowPlaying() },
                                onPlayPause = { playbackService?.togglePlayPause() },
                                onNext = { playbackService?.playNext() },
                                onPrevious = { playbackService?.playPrevious() }
                            )
                        }

                        AuroNavbar(
                            currentScreen = currentScreen,
                            onEqClick = { navController.toggleEqualizer() },
                            onLibraryClick = { navController.toggleLibrary() },
                            onSearchClick = { navController.toggleSearch() },
                            onMenuClick = { showMenuSheetState.value = true }
                        )
                    }

                    // Diálogos Modais Funcionais e Limpos
                    trackContextMenu?.let { track ->
                        AuroTrackContextMenuDialog(
                            track = track,
                            onPlayNext = {
                                playbackService?.enqueueNext(track)
                                trackContextMenuState.value = null
                                snackbarState.show("Tocar a seguir (Fila prioritária)")
                            },
                            onEnqueueEnd = {
                                playbackService?.enqueueEnd(track)
                                trackContextMenuState.value = null
                                snackbarState.show("Adicionada ao fim da fila")
                            },
                            onAddToPlaylist = {
                                trackContextMenuState.value = null
                                addToPlaylistDialogTrack.value = track
                            },
                            onSetRating = { r ->
                                setTrackRating(track, r)
                                trackContextMenuState.value = null
                            },
                            onShowInfo = {
                                trackContextMenuState.value = null
                                trackInfoDialogState.value = track
                            },
                            onDismiss = { trackContextMenuState.value = null }
                        )
                    }

                    addToPlaylistDialogTrack.value?.let { track ->
                        AuroAddToPlaylistDialog(
                            track = track,
                            playlists = libraryRepo.getUserPlaylists(),
                            onCreatePlaylist = { name ->
                                val plId = libraryRepo.createPlaylist(name)
                                if (plId > 0) {
                                    libraryRepo.addTrackToUserPlaylist(plId, track.id)
                                    snackbarState.show("Playlist criada e faixa adicionada!")
                                }
                                addToPlaylistDialogTrack.value = null
                            },
                            onSelectPlaylist = { pl ->
                                libraryRepo.addTrackToUserPlaylist(pl.id, track.id)
                                snackbarState.show("Adicionada à playlist '${pl.name}'")
                                addToPlaylistDialogTrack.value = null
                            },
                            onDismiss = { addToPlaylistDialogTrack.value = null }
                        )
                    }

                    trackInfoDialog?.let { track ->
                        AuroTrackInfoDialog(
                            track = track,
                            onDismiss = { trackInfoDialogState.value = null }
                        )
                    }

                    if (showSortOrder) {
                        AuroSortDialog(
                            currentSortMode = currentSortModeState.value,
                            onSelectSort = { onSelectSortMode(it) },
                            onDismiss = { showSortOrderState.value = false }
                        )
                    }

                    if (showSleepTimer) {
                        AuroSleepTimerDialog(
                            remainingSeconds = playbackService?.getSleepTimerRemainingSeconds() ?: 0,
                            onSetTimerMinutes = { min, stopAtEnd ->
                                playbackService?.startSleepTimer(min, stopAtEnd)
                                val msg = if (stopAtEnd) "Desligará em $min min (ao fim da faixa)" else "Desligará em $min min"
                                snackbarState.show(msg)
                            },
                            onCancelTimer = {
                                playbackService?.cancelSleepTimer()
                                snackbarState.show("Temporizador desligado")
                            },
                            onDismiss = { showSleepTimerState.value = false }
                        )
                    }

                    if (showMenuSheetState.value) {
                        AuroPowerampMenuSheet(
                            onOpenEqualizer = { navController.navigateTo(NavScreen.Equalizer) },
                            onOpenSleepTimer = { showSleepTimerState.value = true },
                            onOpenAudioInfo = { showAudioInfoState.value = true },
                            onRescanLibrary = {
                                loadLibrary()
                                snackbarState.show("Biblioteca atualizada")
                            },
                            onOpenAbout = { showAboutState.value = true },
                            onDismiss = { showMenuSheetState.value = false }
                        )
                    }

                    if (showAudioInfoState.value) {
                        AuroAudioEngineDialog(
                            sampleRate = currentTrack?.sampleRateHz ?: 48000,
                            onDismiss = { showAudioInfoState.value = false }
                        )
                    }

                    if (showAboutState.value) {
                        AuroAboutDialog(
                            onDismiss = { showAboutState.value = false }
                        )
                    }

                    if (showLyricsSheetState.value && currentTrack != null) {
                        val lyrics = remember(currentTrack) {
                            LyricsManager.loadLyrics(this@MainActivity, currentTrack!!)
                        }
                        AuroLyricsSheet(
                            track = currentTrack!!,
                            lyrics = lyrics,
                            currentProgressMs = progressMs,
                            onSeekToMs = { ms ->
                                playbackService?.seekTo(ms)
                                progressMsState.longValue = ms
                            },
                            onDismiss = { showLyricsSheetState.value = false }
                        )
                    }

                    if (showSavePresetDialogState.value) {
                        AuroSavePresetDialog(
                            onSave = { name ->
                                saveCustomPreset(name)
                                showSavePresetDialogState.value = false
                            },
                            onDismiss = { showSavePresetDialogState.value = false }
                        )
                    }

                    deletePlaylistTargetState.value?.let { group ->
                        AuroConfirmDeleteDialog(
                            title = "Excluir Playlist",
                            message = "Deseja excluir a lista '${group.title}'?",
                            onConfirm = {
                                deleteUserPlaylist(group.title)
                                deletePlaylistTargetState.value = null
                            },
                            onDismiss = { deletePlaylistTargetState.value = null }
                        )
                    }

                    deletePresetTargetState.value?.let { preset ->
                        AuroConfirmDeleteDialog(
                            title = "Excluir Preset",
                            message = "Deseja excluir o preset '${preset.name}'?",
                            onConfirm = {
                                deleteCustomPreset(preset)
                                deletePresetTargetState.value = null
                            },
                            onDismiss = { deletePresetTargetState.value = null }
                        )
                    }
                }
            }
        }

        startAndBindService()
        checkAndRequestPermissions()
    }

    private fun resetEqualizerToFlat() {
        val service = playbackService ?: return
        activePresetNameState.value = "Flat"
        preferencesManager.saveActivePresetName("Flat")

        for (i in 0 until 10) {
            if (i < bandGainsState.size) bandGainsState[i] = 0f
            service.setEqualizerBand(i, 0f)
            preferencesManager.saveEqualizerBand(i, 0f)
        }
        bassDbState.floatValue = 0f
        service.setBass(0f)
        preferencesManager.saveBass(0f)

        trebleDbState.floatValue = 0f
        service.setTreble(0f)
        preferencesManager.saveTreble(0f)

        preampDbState.floatValue = 0f
        service.setPreamp(0f)
        preferencesManager.savePreamp(0f)

        snackbarState.show("Equalizador redefinido para Flat (0dB)")
    }

    private fun saveCustomPreset(name: String) {
        musicDb.savePreset(
            name = name,
            bandGains = bandGainsState.toList(),
            bass = bassDbState.floatValue,
            treble = trebleDbState.floatValue,
            preamp = preampDbState.floatValue
        )
        presetsListState.value = musicDb.getAllPresets()
        activePresetNameState.value = name
        preferencesManager.saveActivePresetName(name)
        snackbarState.show("Preset '$name' salvo")
    }

    private fun deleteCustomPreset(preset: MusicDatabase.EqPreset) {
        if (preset.isBuiltin) return
        musicDb.deletePreset(preset.id)
        presetsListState.value = musicDb.getAllPresets()
        if (activePresetNameState.value == preset.name) {
            activePresetNameState.value = "Flat"
            preferencesManager.saveActivePresetName("Flat")
        }
        snackbarState.show("Preset '${preset.name}' excluído")
    }

    private fun deleteUserPlaylist(playlistName: String) {
        val pl = libraryRepo.getUserPlaylists().firstOrNull { it.name == playlistName }
        if (pl != null) {
            libraryRepo.deletePlaylist(pl.id)
            renderCurrentCategory()
            snackbarState.show("Playlist '${pl.name}' excluída")
        }
    }

    private fun setTrackRating(track: Track?, rating: Int) {
        if (track == null) return
        track.rating = rating
        libraryRepo.setTrackRating(track.id, rating)
        if (currentTrackState.value?.id == track.id) {
            currentTrackState.value = track.copy(rating = rating)
        }
        renderCurrentCategory()
        snackbarState.show(if (rating > 0) "Classificada com $rating estrelas" else "Classificação removida")
    }

    private fun selectCategory(category: LibraryCategory) {
        currentCategoryState.value = category
        renderCurrentCategory()
    }

    private fun renderCurrentCategory() {
        val cat = currentCategoryState.value
        val sort = currentSortModeState.value

        when (cat) {
            LibraryCategory.ALL_SONGS -> {
                displayedTracksState.value = libraryRepo.applySorting(libraryRepo.getAllTracks(), sort)
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
                displayedGroupsState.value = years.map { GroupItem(it.year.toString(), "Ano", "${it.trackCount} faixas") }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.COMPOSERS -> {
                val comps = libraryRepo.getComposers()
                displayedGroupsState.value = comps.map { GroupItem(it.name, "Compositor", "${it.trackCount} faixas") }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.QUEUE -> {
                val queueTracks = playbackService?.queueManager?.getPriorityQueue() ?: emptyList()
                displayedTracksState.value = queueTracks
                displayedGroupsState.value = emptyList()
            }
            LibraryCategory.PLAYLISTS -> {
                val pls = libraryRepo.getUserPlaylists()
                displayedGroupsState.value = pls.map { GroupItem(it.name, "Playlist", "${it.trackCount} faixas") }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.MOST_PLAYED -> {
                displayedTracksState.value = libraryRepo.getMostPlayed()
                displayedGroupsState.value = emptyList()
            }
            LibraryCategory.RECENTLY_ADDED -> {
                displayedTracksState.value = libraryRepo.getRecentlyAdded()
                displayedGroupsState.value = emptyList()
            }
            LibraryCategory.RECENTLY_PLAYED -> {
                displayedTracksState.value = libraryRepo.getRecentlyPlayed()
                displayedGroupsState.value = emptyList()
            }
            LibraryCategory.TOP_RATED -> {
                displayedTracksState.value = libraryRepo.getTopRated()
                displayedGroupsState.value = emptyList()
            }
        }
    }

    private fun drillDownGroup(category: LibraryCategory, groupName: String) {
        val sort = currentSortModeState.value
        val tracks = when (category) {
            LibraryCategory.ALBUMS -> libraryRepo.getTracksForAlbum(groupName, sort)
            LibraryCategory.ARTISTS -> libraryRepo.getTracksForArtist(groupName, sort)
            LibraryCategory.ALBUM_ARTISTS -> libraryRepo.getTracksForAlbumArtist(groupName, sort)
            LibraryCategory.FOLDERS -> libraryRepo.getTracksForFolder(groupName, sort)
            LibraryCategory.GENRES -> libraryRepo.getTracksForGenre(groupName, sort)
            LibraryCategory.YEARS -> groupName.toIntOrNull()?.let { libraryRepo.getTracksForYear(it, sort) } ?: emptyList()
            LibraryCategory.COMPOSERS -> libraryRepo.getTracksForComposer(groupName, sort)
            LibraryCategory.PLAYLISTS -> {
                val pl = libraryRepo.getUserPlaylists().firstOrNull { it.name == groupName }
                if (pl != null) libraryRepo.getTracksForUserPlaylist(pl.id) else emptyList()
            }
            else -> emptyList()
        }
        drilldownTracksState.value = tracks
    }

    private fun onSearchQueryChange(query: String) {
        searchQueryState.value = query
        if (query.isBlank()) {
            searchResultsState.value = emptyList()
            return
        }
        val all = libraryRepo.getAllTracks()
        searchResultsState.value = all.filter { track ->
            track.title.contains(query, ignoreCase = true) ||
            track.artist.contains(query, ignoreCase = true) ||
            track.album.contains(query, ignoreCase = true) ||
            track.genre.contains(query, ignoreCase = true) ||
            track.composer.contains(query, ignoreCase = true)
        }
    }

    private fun onSelectSortMode(mode: SortMode) {
        currentSortModeState.value = mode
        renderCurrentCategory()
    }

    private fun syncEqualizerState() {
        val service = playbackService ?: return
        eqEnabledState.value = service.isEqualizerEnabled()
        toneEnabledState.value = service.isToneEnabled()
        preampDbState.floatValue = service.getPreamp()
        bassDbState.floatValue = service.getBass()
        trebleDbState.floatValue = service.getTreble()
        balanceState.floatValue = service.getBalance()
        isMonoState.value = service.isMono()
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

    // PlaybackService.PlaybackListener Callbacks
    override fun onTrackChanged(track: Track?) {
        runOnUiThread {
            currentTrackState.value = track
            isPlayingState.value = playbackService?.audioEngine?.isPlaying() ?: false
            if (track != null) {
                durationMsState.longValue = track.durationMs
            } else {
                durationMsState.longValue = 0L
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
        libraryRepo.loadTracks(this) { list ->
            runOnUiThread {
                totalTracksCountState.intValue = list.size
                selectCategory(LibraryCategory.ALL_SONGS)
                playbackService?.restoreSavedQueue(list)
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
