package com.lucas.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lucas.player.data.CoverArtLoader
import com.lucas.player.data.LibraryRepository
import com.lucas.player.data.LyricsManager
import com.lucas.player.data.MusicDatabase
import com.lucas.player.data.PreferencesManager
import com.lucas.player.model.*
import com.lucas.player.queue.QueueSection
import com.lucas.player.queue.RepeatMode
import com.lucas.player.queue.ShuffleMode
import com.lucas.player.service.PlaybackService
import com.lucas.player.ui.GroupItem
import com.lucas.player.ui.components.*
import com.lucas.player.ui.navigation.NavScreen
import com.lucas.player.ui.navigation.PlayerNavController
import com.lucas.player.ui.screens.*
import com.lucas.player.ui.screens.HomePlaylist
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class LibraryAccess {
    object Granted : LibraryAccess()
    object NeedsRequest : LibraryAccess()
    object Denied : LibraryAccess()
    object PermanentlyDenied : LibraryAccess()
}

private data class HomeSnapshot(
    val allTracks: List<Track> = emptyList(),
    val recentlyPlayed: List<Track> = emptyList(),
    val mostPlayed: List<Track> = emptyList(),
    val userPlaylists: List<UserPlaylist> = emptyList(),
    val playlists: List<HomePlaylist> = emptyList(),
    val recentLibraryItems: List<RecentLibraryItem> = emptyList(),
    val categoryCounts: Map<LibraryCategory, Int> = emptyMap(),
    val albumCoverMap: Map<String, Track?> = emptyMap()
)

class MainActivity : ComponentActivity(), PlaybackService.PlaybackListener {

    private val libraryRepo by lazy { LibraryRepository.getInstance(this) }
    private val musicDb by lazy { MusicDatabase(this) }
    private val preferencesManager by lazy { PreferencesManager(this) }
    private var playbackService: PlaybackService? = null
    private var isBound = false
    private val snackbarState = AuroSnackbarState()

    // Controlador de Navegação com Pilha de Histórico estilo Apple Music 7.0.2
    private val navController = PlayerNavController(NavScreen.Home)

    private fun screenDepth(screen: NavScreen): Int = when (screen) {
        is NavScreen.Home -> 0
        is NavScreen.LibraryRoot -> 1
        is NavScreen.CategoryList,
        is NavScreen.Search,
        is NavScreen.Equalizer,
        is NavScreen.Queue,
        is NavScreen.Settings -> 2
        is NavScreen.Drilldown,
        is NavScreen.ArtistProfile -> 3
        is NavScreen.ArtistTracks,
        is NavScreen.ArtistDiscography -> 4
        is NavScreen.NowPlaying -> 5
    }

    // Estados Reativos do Player
    private val currentTrackState = mutableStateOf<Track?>(null)
    private val isPlayingState = mutableStateOf(false)
    private val progressMsState = mutableLongStateOf(0L)
    private val durationMsState = mutableLongStateOf(0L)
    private val shuffleModeState = mutableStateOf(ShuffleMode.OFF)
    private val repeatModeState = mutableStateOf(RepeatMode.OFF)
    private val priorityQueueState = mutableStateOf<List<Track>>(emptyList())
    private val regularQueueState = mutableStateOf<List<Track>>(emptyList())
    private val upcomingPriorityQueueState = mutableStateOf<List<Track>>(emptyList())
    private val upcomingRegularQueueState = mutableStateOf<List<Track>>(emptyList())
    private val playbackHistoryState = mutableStateOf<List<Pair<Long, Track>>>(emptyList())

    private fun refreshQueueAndHistoryState() {
        val s = playbackService ?: return
        priorityQueueState.value = s.queueManager.getPriorityQueue()
        regularQueueState.value = s.queueManager.getTracks()
        upcomingPriorityQueueState.value = s.getUpcomingPriorityTracks()
        upcomingRegularQueueState.value = s.getUpcomingRegularTracks()
        playbackHistoryState.value = s.getPlaybackHistory(100)
    }

    // Estados da Biblioteca
    private val libraryVersionState = mutableIntStateOf(0)
    private val totalTracksCountState = mutableIntStateOf(0)
    private val libraryAccessState = mutableStateOf<LibraryAccess>(LibraryAccess.NeedsRequest)
    private val selectedFoldersState = mutableStateOf<List<String>>(emptyList())
    private val isLoadingLibraryState = mutableStateOf(false)
    private val loadingTracksCountState = mutableIntStateOf(0)
    private val errorTrackIdsState = mutableStateListOf<Long>()
    private var consecutivePlaybackErrors = 0
    private var skipJob: Job? = null
    private val currentCategoryState = mutableStateOf(LibraryCategory.ALL_SONGS)
    private val currentSortModeState = mutableStateOf(SortMode.TITLE_ASC)
    private val displayedTracksState = mutableStateOf<List<Track>>(emptyList())
    private val displayedGroupsState = mutableStateOf<List<GroupItem>>(emptyList())
    private val drilldownTracksState = mutableStateOf<List<Track>>(emptyList())

    // Busca Instantânea
    private val searchQueryState = mutableStateOf("")

    // Diálogos & Modais
    private val trackContextMenuState = mutableStateOf<Track?>(null)
    private val trackInfoDialogState = mutableStateOf<Track?>(null)
    private val showSortOrderState = mutableStateOf(false)
    private val showSleepTimerState = mutableStateOf(false)
    private val showCreatePlaylistDialogState = mutableStateOf(false)
    private val editingPlaylistState = mutableStateOf<UserPlaylist?>(null)
    private val showPlaylistTrackPickerState = mutableStateOf<UserPlaylist?>(null)
    private val deletingPlaylistState = mutableStateOf<UserPlaylist?>(null)
    private val playlistsVersionState = mutableIntStateOf(0)

    private val pickPlaylistCoverLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val pl = editingPlaylistState.value
            if (pl != null) {
                val savedPath = CoverArtLoader.savePlaylistCover(this, pl.id, uri)
                if (savedPath != null) {
                    libraryRepo.updatePlaylistCover(pl.id, savedPath)
                    editingPlaylistState.value = pl.copy(coverPath = savedPath)
                    playlistsVersionState.intValue++
                    libraryVersionState.intValue++
                    renderCurrentCategory()
                }
            }
        }
    }

    private val targetArtistForMediaState = mutableStateOf<String?>(null)
    private val artistMediaVersionState = mutableIntStateOf(0)

    private val pickArtistMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val artist = targetArtistForMediaState.value
        if (uri != null && !artist.isNullOrBlank()) {
            val result = CoverArtLoader.saveArtistMedia(this, artist, uri)
            if (result != null) {
                libraryRepo.setArtistMedia(artist, result.first, result.second)
                artistMediaVersionState.intValue++
                AuroHaptics.confirm(window.decorView)
                snackbarState.show(text = getString(R.string.artist_media_updated))
            }
        }
    }

    private fun removeArtistMedia(artistName: String) {
        val media = libraryRepo.getArtistMedia(artistName)
        if (media != null) {
            libraryRepo.removeArtistMedia(artistName)
            CoverArtLoader.deleteArtistMedia(media.first)
            artistMediaVersionState.intValue++
            AuroHaptics.confirm(window.decorView)
            snackbarState.show(text = getString(R.string.artist_media_removed))
        }
    }

    // Seleção Múltipla (Fase 4.3)
    private val isSelectionModeState = mutableStateOf(false)
    private val selectedTrackIdsState = mutableStateListOf<Long>()
    private val tracksToAddToPlaylistState = mutableStateOf<List<Track>>(emptyList())

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
            refreshQueueAndHistoryState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService?.removeListener(this@MainActivity)
            playbackService = null
            isBound = false
        }
    }

    private fun evaluateLibraryAccess(): LibraryAccess {
        val configuredUris = preferencesManager.getMusicFolderUris()
        if (configuredUris.isEmpty()) {
            return LibraryAccess.NeedsRequest
        }
        val persistedPermissions = contentResolver.persistedUriPermissions
        val hasValidPermission = configuredUris.any { uriStr ->
            val uri = try { Uri.parse(uriStr) } catch (_: Exception) { null }
            uri != null && persistedPermissions.any { perm ->
                perm.uri == uri && perm.isReadPermission
            }
        }
        return if (hasValidPermission) LibraryAccess.Granted else LibraryAccess.NeedsRequest
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val docId = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Exception) {
                uri.lastPathSegment ?: ""
            }

            val folderName = try {
                when {
                    docId.contains(":") -> {
                        val sub = docId.substringAfter(":")
                        if (sub.contains("/")) sub.substringAfterLast("/") else sub
                    }
                    else -> docId
                }.ifEmpty { "Música" }
            } catch (_: Exception) { "Música" }

            val folderPath = try {
                when {
                    docId.startsWith("primary:", ignoreCase = true) -> {
                        val rel = docId.substringAfter(":")
                        val base = Environment.getExternalStorageDirectory().absolutePath
                        if (rel.isEmpty()) base else "$base/$rel"
                    }
                    docId.contains(":") -> {
                        val parts = docId.split(":")
                        "/storage/${parts[0]}/${parts.getOrElse(1) { "" }}"
                    }
                    else -> docId
                }
            } catch (_: Exception) { docId }

            try {
                preferencesManager.addMusicFolderUri(uri.toString())
                preferencesManager.setSelectedMusicFolderUri(uri.toString())
                preferencesManager.setSelectedMusicFolderPath(folderPath)
                preferencesManager.setSelectedMusicFolderName(folderName)

                selectedFoldersState.value = preferencesManager.getMusicFolderUris()
                libraryAccessState.value = LibraryAccess.Granted
                loadLibrary()
                checkNotificationPermissionNonBlocking()
                snackbarState.show(getString(R.string.snackbar_folder_selected, folderName))
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Erro ao processar pasta selecionada", e)
            }
        }
    }

    private fun removeMusicFolder(uriString: String) {
        try {
            contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        preferencesManager.removeMusicFolderUri(uriString)
        selectedFoldersState.value = preferencesManager.getMusicFolderUris()
        libraryAccessState.value = evaluateLibraryAccess()
        loadLibrary()
        snackbarState.show(getString(R.string.snackbar_folder_removed))
    }

    private fun requestLibraryPermission() {
        folderPickerLauncher.launch(null)
    }

    private fun checkNotificationPermissionNonBlocking() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted && !preferencesManager.hasPromptedNotificationPermission()) {
                preferencesManager.setPromptedNotificationPermission(true)
                snackbarState.show(
                    text = getString(R.string.permission_notification_denied),
                    action = getString(R.string.permission_notification_action),
                    onAction = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        }
                        try {
                            startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        selectedFoldersState.value = preferencesManager.getMusicFolderUris()
        val access = evaluateLibraryAccess()
        if (access != libraryAccessState.value) {
            libraryAccessState.value = access
            if (access is LibraryAccess.Granted) {
                if (totalTracksCountState.intValue == 0) {
                    loadLibrary()
                }
                checkNotificationPermissionNonBlocking()
            }
        }
    }

    private fun enableHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val modes = currentDisplay?.supportedModes ?: emptyArray()
            if (modes.isNotEmpty()) {
                val maxResolution = modes.maxOfOrNull { it.physicalWidth * it.physicalHeight } ?: 0
                val bestMode = modes
                    .filter { (it.physicalWidth * it.physicalHeight) == maxResolution }
                    .maxByOrNull { it.refreshRate }
                bestMode?.let { mode ->
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = mode.modeId
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            preferredRefreshRate = mode.refreshRate
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        enableHighRefreshRate()
        AuroHaptics.init(this)
        LibraryRepository.init(this)
        libraryRepo.onStatsChangedListener = {
            runOnUiThread {
                libraryVersionState.intValue++
            }
        }

        selectedFoldersState.value = preferencesManager.getMusicFolderUris()
        libraryAccessState.value = evaluateLibraryAccess()
        if (libraryAccessState.value is LibraryAccess.Granted) {
            loadLibrary()
            checkNotificationPermissionNonBlocking()
        }

        startAndBindService()

        setContent {
            val backdropLayer = rememberGraphicsLayer()
            val isBackdropRecorded = remember { booleanArrayOf(false) }
            var contentRootOffset by remember { mutableStateOf(Offset.Zero) }
            val libraryAccess by libraryAccessState
            val isLoadingLibrary by isLoadingLibraryState
            val loadingTracksCount by loadingTracksCountState
            val errorTrackIds = remember { errorTrackIdsState }
            val currentScreen = navController.currentScreen
            val scrollStates = remember { mutableMapOf<String, LazyListState>() }
            fun getScrollState(key: String): LazyListState = scrollStates.getOrPut(key) { LazyListState() }
            val currentTrack by currentTrackState
            val isPlaying by isPlayingState
            val durationMs by durationMsState
            val shuffleMode by shuffleModeState
            val repeatMode by repeatModeState

            val libraryVersion by libraryVersionState
            val plVersion by playlistsVersionState
            val artistMediaVersion by artistMediaVersionState
            val homeSnapshot by produceState(
                initialValue = HomeSnapshot(),
                key1 = libraryVersion,
                key2 = plVersion
            ) {
                value = withContext(Dispatchers.IO) {
                    val all = libraryRepo.getAllTracks()
                    val recent = libraryRepo.getRecentlyPlayed().take(20)
                    val most = libraryRepo.getMostPlayed().take(60)
                    val userPls = libraryRepo.getUserPlaylists()
                    val homePls = userPls.map { pl ->
                        HomePlaylist(
                            id = pl.id,
                            name = pl.name,
                            countText = formatTracksCount(this@MainActivity, pl.trackCount),
                            coverTrack = libraryRepo.getTracksForUserPlaylist(pl.id).firstOrNull(),
                            coverPath = pl.coverPath
                        )
                    }
                    val recentItems = libraryRepo.getRecentLibraryItems(12)
                    val counts = mapOf(
                        LibraryCategory.ALL_SONGS to all.size,
                        LibraryCategory.ALBUMS to all.map { it.album }.filter { it.isNotBlank() }.distinct().size,
                        LibraryCategory.ARTISTS to all.map { it.artist }.filter { it.isNotBlank() }.distinct().size,
                        LibraryCategory.ALBUM_ARTISTS to all.map { it.albumArtist.ifBlank { it.artist } }.filter { it.isNotBlank() }.distinct().size,
                        LibraryCategory.GENRES to all.map { it.genre }.filter { it.isNotBlank() }.distinct().size,
                        LibraryCategory.YEARS to all.map { it.year }.filter { it > 0 }.distinct().size,
                        LibraryCategory.COMPOSERS to all.map { it.composer }.filter { it.isNotBlank() }.distinct().size,
                        LibraryCategory.FOLDERS to all.map { it.folder }.filter { it.isNotBlank() }.distinct().size,
                        LibraryCategory.PLAYLISTS to userPls.size
                    )
                    val albumCovers = all.groupBy { it.album.lowercase() }.mapValues { it.value.firstOrNull() }
                    HomeSnapshot(
                        allTracks = all,
                        recentlyPlayed = recent,
                        mostPlayed = most,
                        userPlaylists = userPls,
                        playlists = homePls,
                        recentLibraryItems = recentItems,
                        categoryCounts = counts,
                        albumCoverMap = albumCovers
                    )
                }
            }
            val artistMediaMap by produceState<Map<String, Pair<String, Boolean>>>(
                initialValue = emptyMap(),
                key1 = artistMediaVersion
            ) {
                value = withContext(Dispatchers.IO) {
                    libraryRepo.getAllArtistMedia()
                }
            }

            val totalTracksCount by totalTracksCountState
            val displayedTracks by displayedTracksState
            val displayedGroups by displayedGroupsState
            val drilldownTracks by drilldownTracksState

            val searchQuery by searchQueryState

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

            LaunchedEffect(currentScreen) {
                if (isSelectionModeState.value) {
                    isSelectionModeState.value = false
                    selectedTrackIdsState.clear()
                }
            }

            // Tratamento do Botão Físico/Gestual Voltar do Android
            BackHandler(
                enabled = isSelectionModeState.value || trackContextMenu != null || trackInfoDialog != null ||
                        showSortOrder || showSleepTimer || showMenuSheetState.value ||
                        showAudioInfoState.value || showAboutState.value ||
                        showLyricsSheetState.value || showSavePresetDialogState.value ||
                        deletePlaylistTargetState.value != null || deletePresetTargetState.value != null ||
                        showCreatePlaylistDialogState.value ||
                        editingPlaylistState.value != null || showPlaylistTrackPickerState.value != null ||
                        deletingPlaylistState.value != null ||
                        addToPlaylistDialogTrack.value != null || tracksToAddToPlaylistState.value.isNotEmpty() ||
                        navController.canNavigateUp
            ) {
                when {
                    isSelectionModeState.value -> {
                        isSelectionModeState.value = false
                        selectedTrackIdsState.clear()
                    }
                    showCreatePlaylistDialogState.value -> showCreatePlaylistDialogState.value = false
                    editingPlaylistState.value != null -> editingPlaylistState.value = null
                    showPlaylistTrackPickerState.value != null -> showPlaylistTrackPickerState.value = null
                    deletingPlaylistState.value != null -> deletingPlaylistState.value = null
                    tracksToAddToPlaylistState.value.isNotEmpty() -> tracksToAddToPlaylistState.value = emptyList()
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

            if (libraryAccess !is LibraryAccess.Granted) {
                AuroTheme(
                    accent = currentAccent,
                    playing = false
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AuroColor.Black)
                            .statusBarsPadding()
                    ) {
                        AuroEmptyState(
                            icon = Icons.Rounded.FolderOpen,
                            title = stringResource(R.string.permission_library_title),
                            message = stringResource(R.string.permission_library_message),
                            actionLabel = stringResource(R.string.permission_action_grant),
                            onAction = {
                                folderPickerLauncher.launch(null)
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        AuroSnackbarHost(
                            state = snackbarState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 24.dp)
                        )
                    }
                }
            } else {
                AuroTheme(
                    accent = currentAccent,
                    playing = isPlaying,
                    reduceMotionOverride = when (preferencesManager.getReduceMotionMode()) {
                        1 -> true
                        2 -> false
                        else -> null
                    }
                ) {
                    val reduceMotion = rememberReduceMotion()
                    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val barsHeight = 56.dp + (if (currentTrack != null) 60.dp else 0.dp)
                    val activeBottomInset = if (currentScreen is NavScreen.NowPlaying) 0.dp else (barsHeight + navBarBottomInset)

                    CompositionLocalProvider(
                        LocalAuroBottomInset provides activeBottomInset,
                        LocalAuroIsPlaying provides isPlaying
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AuroColor.Canvas)
                        ) {
                        // Barra Superior de Seleção Múltipla (Fase 4.3)
                        if (isSelectionModeState.value) {
                            val selectedCount = selectedTrackIdsState.size
                            Surface(
                                color = AuroColor.Black,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .zIndex(15f)
                                    .border(1.dp, AuroColor.Hairline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            isSelectionModeState.value = false
                                            selectedTrackIdsState.clear()
                                        }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.selection_exit),
                                                tint = AuroColor.TextPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (selectedCount == 1) {
                                                stringResource(R.string.selection_count_single)
                                            } else {
                                                stringResource(R.string.selection_count, selectedCount)
                                            },
                                            style = AuroType.Subtitle.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Tocar a seguir
                                        IconButton(
                                            onClick = {
                                                val all = libraryRepo.getAllTracks()
                                                val tracksToEnqueue = all.filter { selectedTrackIdsState.contains(it.id) }
                                                tracksToEnqueue.reversed().forEach { playbackService?.enqueueNext(it) }
                                                val count = tracksToEnqueue.size
                                                isSelectionModeState.value = false
                                                selectedTrackIdsState.clear()
                                                snackbarState.show(getString(R.string.selection_added_next, count))
                                            },
                                            enabled = selectedCount > 0
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                                contentDescription = stringResource(R.string.selection_play_next),
                                                tint = if (selectedCount > 0) currentAccent.a else AuroColor.TextTertiary
                                            )
                                        }

                                        // Adicionar à fila
                                        IconButton(
                                            onClick = {
                                                val all = libraryRepo.getAllTracks()
                                                val tracksToEnqueue = all.filter { selectedTrackIdsState.contains(it.id) }
                                                tracksToEnqueue.forEach { playbackService?.enqueueEnd(it) }
                                                val count = tracksToEnqueue.size
                                                isSelectionModeState.value = false
                                                selectedTrackIdsState.clear()
                                                snackbarState.show(getString(R.string.selection_added_queue, count))
                                            },
                                            enabled = selectedCount > 0
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                                contentDescription = stringResource(R.string.selection_add_queue),
                                                tint = if (selectedCount > 0) AuroColor.TextPrimary else AuroColor.TextTertiary
                                            )
                                        }

                                        // Adicionar à playlist
                                        IconButton(
                                            onClick = {
                                                val all = libraryRepo.getAllTracks()
                                                val tracksToPlaylist = all.filter { selectedTrackIdsState.contains(it.id) }
                                                if (tracksToPlaylist.isNotEmpty()) {
                                                    tracksToAddToPlaylistState.value = tracksToPlaylist
                                                }
                                            },
                                            enabled = selectedCount > 0
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                                contentDescription = stringResource(R.string.selection_add_playlist),
                                                tint = if (selectedCount > 0) AuroColor.TextPrimary else AuroColor.TextTertiary
                                            )
                                        }

                                        // Selecionar tudo
                                        IconButton(
                                            onClick = {
                                                val activeTracks = if (currentScreen is NavScreen.Drilldown) drilldownTracks else displayedTracks
                                                if (selectedTrackIdsState.size == activeTracks.size && activeTracks.isNotEmpty()) {
                                                    selectedTrackIdsState.clear()
                                                } else {
                                                    selectedTrackIdsState.clear()
                                                    selectedTrackIdsState.addAll(activeTracks.map { it.id })
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.SelectAll,
                                                contentDescription = stringResource(R.string.selection_select_all),
                                                tint = AuroColor.TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    // Conteúdo Principal de Acordo com a Tela Ativa (Fase 5.4: insets medidos via LocalAuroBottomInset)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { contentRootOffset = it.positionInRoot() }
                            .drawWithContent {
                                if (size.width > 0f && size.height > 0f) {
                                    backdropLayer.record {
                                        drawRect(AuroColor.Canvas)
                                        this@drawWithContent.drawContent()
                                    }
                                    isBackdropRecorded[0] = true
                                    drawLayer(backdropLayer)
                                } else {
                                    drawContent()
                                }
                            }
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                if (reduceMotion) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else if (targetState is NavScreen.NowPlaying) {
                                    (slideInVertically(spring(dampingRatio = 0.80f, stiffness = 280f)) { it } +
                                            scaleIn(initialScale = 0.92f, animationSpec = spring(0.80f, 280f)) +
                                            fadeIn(spring(0.80f, 280f)))
                                        .togetherWith(
                                            scaleOut(targetScale = 0.92f, animationSpec = spring(0.80f, 280f)) +
                                                    fadeOut(tween(180))
                                        )
                                } else if (initialState is NavScreen.NowPlaying) {
                                    (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                                            scaleIn(initialScale = 0.92f, animationSpec = spring(0.80f, 280f)))
                                        .togetherWith(
                                            slideOutVertically(spring(dampingRatio = 0.80f, stiffness = 280f)) { it } +
                                                    scaleOut(targetScale = 0.92f, animationSpec = spring(0.80f, 280f)) +
                                                    fadeOut(spring(0.80f, 280f))
                                        )
                                } else {
                                    val initialDepth = screenDepth(initialState)
                                    val targetDepth = screenDepth(targetState)
                                    if (targetDepth > initialDepth) {
                                        (slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = 320f)) { (it * 0.28f).toInt() } +
                                                fadeIn(tween(220, easing = FastOutSlowInEasing)))
                                            .togetherWith(
                                                slideOutHorizontally(spring(dampingRatio = 0.82f, stiffness = 320f)) { -(it * 0.12f).toInt() } +
                                                        scaleOut(targetScale = 0.96f, animationSpec = spring(0.82f, 320f)) +
                                                        fadeOut(tween(180))
                                            )
                                    } else if (targetDepth < initialDepth) {
                                        (slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = 320f)) { -(it * 0.12f).toInt() } +
                                                scaleIn(initialScale = 0.96f, animationSpec = spring(0.82f, 320f)) +
                                                fadeIn(tween(220, easing = FastOutSlowInEasing)))
                                            .togetherWith(
                                                slideOutHorizontally(spring(dampingRatio = 0.82f, stiffness = 320f)) { (it * 0.28f).toInt() } +
                                                        scaleOut(targetScale = 0.96f, animationSpec = spring(0.82f, 320f)) +
                                                        fadeOut(tween(180))
                                            )
                                    } else {
                                        fadeIn(tween(220)).togetherWith(fadeOut(tween(140)))
                                    }
                                }
                            },
                            label = "ScreenTransition",
                            modifier = Modifier.fillMaxSize()
                        ) { activeScreen ->
                            when (activeScreen) {
                            is NavScreen.Home -> {
                                AuroHomeScreen(
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaying,
                                    recentlyPlayed = homeSnapshot.recentlyPlayed,
                                    mostPlayed = homeSnapshot.mostPlayed,
                                    allTracks = homeSnapshot.allTracks,
                                    isLoading = isLoadingLibrary,
                                    loadingCount = loadingTracksCount,
                                    listState = getScrollState("home"),
                                    onTrackClick = { track ->
                                        val all = homeSnapshot.allTracks
                                        val idx = all.indexOfFirst { it.id == track.id }.takeIf { it != -1 } ?: 0
                                        playbackService?.playQueue(all, idx)
                                        navController.openNowPlaying()
                                    },
                                    onPlayAll = { tracks ->
                                        if (tracks.isNotEmpty()) {
                                            playbackService?.playQueue(tracks, 0)
                                            navController.openNowPlaying()
                                        }
                                    },
                                    onShuffleAll = { tracks ->
                                        if (tracks.isNotEmpty()) {
                                            playbackService?.queueManager?.setShuffleMode(ShuffleMode.ALL_TRACKS)
                                            shuffleModeState.value = ShuffleMode.ALL_TRACKS
                                            playbackService?.playQueue(tracks.shuffled(), 0)
                                            navController.openNowPlaying()
                                        }
                                    },
                                    onResumePlayback = {
                                        if (currentTrack != null) {
                                            if (!isPlaying) playbackService?.play()
                                        } else {
                                            val all = homeSnapshot.allTracks
                                            if (all.isNotEmpty()) playbackService?.playQueue(all, 0)
                                        }
                                    },
                                    onOpenNowPlaying = { navController.openNowPlaying() },
                                    onOpenSettings = { showMenuSheetState.value = true },
                                    onOpenLibrary = { navController.toggleLibrary() },
                                    onOpenMostPlayed = {
                                        val title = getString(R.string.home_mix_most_played)
                                        drillDownGroup(LibraryCategory.MOST_PLAYED, title)
                                        navController.navigateTo(
                                            NavScreen.Drilldown(
                                                category = LibraryCategory.MOST_PLAYED,
                                                title = title,
                                                subtitle = getString(R.string.group_subtitle_smart_playlist)
                                            )
                                        )
                                    },
                                    onOpenFavorites = {
                                        val title = getString(R.string.home_mix_favorites)
                                        drillDownGroup(LibraryCategory.TOP_RATED, title)
                                        navController.navigateTo(
                                            NavScreen.Drilldown(
                                                category = LibraryCategory.TOP_RATED,
                                                title = title,
                                                subtitle = getString(R.string.group_subtitle_smart_playlist)
                                            )
                                        )
                                    },
                                    onArtistClick = { artistName ->
                                        navController.navigateTo(NavScreen.ArtistProfile(artistName))
                                    },
                                    playlists = homeSnapshot.playlists,
                                    artistMediaMap = artistMediaMap,
                                    onAlbumClick = { albumName ->
                                        drillDownGroup(LibraryCategory.ALBUMS, albumName)
                                        navController.navigateTo(
                                            NavScreen.Drilldown(
                                                category = LibraryCategory.ALBUMS,
                                                title = albumName,
                                                subtitle = getString(R.string.group_subtitle_album)
                                            )
                                        )
                                    },
                                    onAlbumPlay = { album ->
                                        val tracks = libraryRepo.getTracksForAlbum(album, currentSortModeState.value)
                                        if (tracks.isNotEmpty()) {
                                            playbackService?.playQueue(tracks, 0)
                                            navController.openNowPlaying()
                                        }
                                    },
                                    onPlaylistClick = { name ->
                                        drillDownGroup(LibraryCategory.PLAYLISTS, name)
                                        navController.navigateTo(
                                            NavScreen.Drilldown(
                                                category = LibraryCategory.PLAYLISTS,
                                                title = name,
                                                subtitle = getString(R.string.group_subtitle_playlist)
                                            )
                                        )
                                    },
                                    onCreatePlaylist = { showCreatePlaylistDialogState.value = true },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.LibraryRoot -> {
                                val all = homeSnapshot.allTracks
                                val pinnedKeys = preferencesManager.getPinnedGroups()
                                val pinnedItems = displayedGroups.filter { pinnedKeys.contains(it.title) }
                                AuroLibraryRootScreen(
                                    totalTracksCount = totalTracksCount,
                                    isLoading = isLoadingLibrary,
                                    loadingCount = loadingTracksCount,
                                    categoryCounts = homeSnapshot.categoryCounts,
                                    listState = getScrollState("library_root"),
                                    pinnedGroups = pinnedKeys,
                                    pinnedItems = pinnedItems,
                                    recentLibraryItems = homeSnapshot.recentLibraryItems,
                                    coverTrackFinder = { group -> all.firstOrNull { it.album.equals(group.title, ignoreCase = true) || it.artist.equals(group.title, ignoreCase = true) } },
                                    onShuffleAll = {
                                        val tracks = homeSnapshot.allTracks
                                        if (tracks.isNotEmpty()) {
                                            shuffleModeState.value = ShuffleMode.ALL_TRACKS
                                            playbackService?.playQueue(tracks.shuffled(), 0)
                                        }
                                    },
                                    onResumePlayback = {
                                        if (currentTrack != null) {
                                            if (!isPlaying) playbackService?.play()
                                        } else {
                                            val tracks = homeSnapshot.allTracks
                                            if (tracks.isNotEmpty()) playbackService?.playQueue(tracks, 0)
                                        }
                                    },
                                    onSelectCategory = { cat ->
                                        selectCategory(cat)
                                        navController.navigateTo(NavScreen.CategoryList(cat))
                                    },
                                    onGroupClick = { group ->
                                        drillDownGroup(LibraryCategory.ALBUMS, group.title)
                                        navController.navigateTo(
                                            NavScreen.Drilldown(
                                                category = LibraryCategory.ALBUMS,
                                                title = group.title,
                                                subtitle = group.subtitle
                                            )
                                        )
                                    },
                                    onRecentItemClick = { item ->
                                        when (item) {
                                            is RecentLibraryItem.AlbumItem -> {
                                                drillDownGroup(LibraryCategory.ALBUMS, item.albumName)
                                                navController.navigateTo(
                                                    NavScreen.Drilldown(
                                                        category = LibraryCategory.ALBUMS,
                                                        title = item.albumName,
                                                        subtitle = item.subtitle.ifBlank { getString(R.string.group_subtitle_album) }
                                                    )
                                                )
                                            }
                                            is RecentLibraryItem.SingleTrackItem -> {
                                                val allTracksList = homeSnapshot.allTracks
                                                val idx = allTracksList.indexOfFirst { it.id == item.track.id }.takeIf { it != -1 } ?: 0
                                                playbackService?.playQueue(allTracksList, idx)
                                                navController.openNowPlaying()
                                            }
                                            is RecentLibraryItem.PlaylistItem -> {
                                                drillDownGroup(LibraryCategory.PLAYLISTS, item.playlist.name)
                                                navController.navigateTo(
                                                    NavScreen.Drilldown(
                                                        category = LibraryCategory.PLAYLISTS,
                                                        title = item.playlist.name,
                                                        subtitle = getString(R.string.group_subtitle_playlist)
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onTrackClick = { track ->
                                        val tracks = homeSnapshot.allTracks
                                        val idx = tracks.indexOfFirst { it.id == track.id }.takeIf { it != -1 } ?: 0
                                        playbackService?.playQueue(tracks, idx)
                                        navController.openNowPlaying()
                                    },
                                    onSortClick = { showSortOrderState.value = true },
                                    onRescanClick = { loadLibrary() },
                                    onSettingsClick = { showMenuSheetState.value = true },
                                    selectedFolderName = preferencesManager.getSelectedMusicFolderName(),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.CategoryList -> {
                                when (activeScreen.category) {
                                    LibraryCategory.QUEUE -> {
                                        AuroQueueScreen(
                                            priorityTracks = priorityQueueState.value,
                                            regularTracks = regularQueueState.value,
                                            currentTrackId = currentTrack?.id ?: -1L,
                                            errorTrackIds = errorTrackIds.toSet(),
                                            listState = getScrollState("queue"),
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
                                                playbackService?.let { s ->
                                                    priorityQueueState.value = s.queueManager.getPriorityQueue()
                                                    regularQueueState.value = s.queueManager.getTracks()
                                                }
                                                renderCurrentCategory()
                                            },
                                            onClearQueue = {
                                                playbackService?.clearPriorityQueue()
                                                playbackService?.let { s ->
                                                    priorityQueueState.value = s.queueManager.getPriorityQueue()
                                                    regularQueueState.value = s.queueManager.getTracks()
                                                }
                                                renderCurrentCategory()
                                                snackbarState.show(getString(R.string.snackbar_queue_cleared))
                                            },
                                            onShuffleQueue = {
                                                playbackService?.shuffleCurrentQueue()
                                                playbackService?.let { s ->
                                                    priorityQueueState.value = s.queueManager.getPriorityQueue()
                                                    regularQueueState.value = s.queueManager.getTracks()
                                                }
                                                renderCurrentCategory()
                                                snackbarState.show(getString(R.string.snackbar_queue_shuffled))
                                            },
                                            onMoveItem = { section, from, to ->
                                                playbackService?.moveQueueItem(section, from, to)
                                                playbackService?.let { s ->
                                                    priorityQueueState.value = s.queueManager.getPriorityQueue()
                                                    regularQueueState.value = s.queueManager.getTracks()
                                                }
                                                renderCurrentCategory()
                                            },
                                            showSnackbar = { msg, action, onAction ->
                                                snackbarState.show(msg, action, onAction)
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    LibraryCategory.YEARS -> {
                                        AuroYearsScreen(
                                            currentPlayingId = currentTrack?.id ?: -1L,
                                            sortMode = currentSortModeState.value,
                                            dataVersion = libraryVersion,
                                            onBackClick = { navController.navigateUp() },
                                            onSortClick = { showSortOrderState.value = true },
                                            onTrackClick = { tracks, position ->
                                                if (tracks.isNotEmpty()) {
                                                    playbackService?.playQueue(tracks, position)
                                                    navController.openNowPlaying()
                                                }
                                            },
                                            onTrackLongClick = { track ->
                                                trackContextMenuState.value = track
                                            },
                                            onTrackMenuClick = { track ->
                                                trackContextMenuState.value = track
                                            },
                                            onPlayAll = { tracks ->
                                                if (tracks.isNotEmpty()) {
                                                    playbackService?.playQueue(tracks, 0)
                                                    navController.openNowPlaying()
                                                }
                                            },
                                            onShuffleAll = { tracks ->
                                                if (tracks.isNotEmpty()) {
                                                    playbackService?.queueManager?.setShuffleMode(ShuffleMode.ALL_TRACKS)
                                                    shuffleModeState.value = ShuffleMode.ALL_TRACKS
                                                    playbackService?.playQueue(tracks.shuffled(), 0)
                                                    navController.openNowPlaying()
                                                }
                                            },
                                            errorTrackIds = errorTrackIds.toSet(),
                                            isSelectionMode = isSelectionModeState.value,
                                            selectedTrackIds = selectedTrackIdsState.toSet(),
                                            onToggleSelectTrack = { track ->
                                                if (selectedTrackIdsState.contains(track.id)) {
                                                    selectedTrackIdsState.remove(track.id)
                                                } else {
                                                    selectedTrackIdsState.add(track.id)
                                                }
                                            },
                                            showFileFormat = preferencesManager.isShowFileFormatEnabled(),
                                            listState = getScrollState("category_YEARS"),
                                            libraryRepository = libraryRepo,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    else -> {
                                        val albumCoverMap = homeSnapshot.albumCoverMap
                                        AuroCategoryListScreen(
                                            category = activeScreen.category,
                                            listState = getScrollState("category_${activeScreen.category.name}"),
                                            displayedTracks = displayedTracks,
                                            displayedGroups = displayedGroups,
                                            currentPlayingId = currentTrack?.id ?: -1L,
                                            errorTrackIds = errorTrackIds.toSet(),
                                            isSelectionMode = isSelectionModeState.value,
                                            selectedTrackIds = selectedTrackIdsState.toSet(),
                                            onToggleSelectTrack = { track ->
                                                if (selectedTrackIdsState.contains(track.id)) {
                                                    selectedTrackIdsState.remove(track.id)
                                                } else {
                                                    selectedTrackIdsState.add(track.id)
                                                }
                                            },
                                            showFileFormat = preferencesManager.isShowFileFormatEnabled(),
                                            coverTrackFinder = { group -> albumCoverMap[group.title.lowercase()] },
                                            onBackClick = { navController.navigateUp() },
                                            onTrackClick = { _, position ->
                                                playbackService?.playQueue(displayedTracks, position)
                                                navController.openNowPlaying()
                                            },
                                            onPlayTracksList = { tracks, position ->
                                                if (tracks.isNotEmpty()) {
                                                    playbackService?.playQueue(tracks, position)
                                                    navController.openNowPlaying()
                                                }
                                            },
                                            onShuffleTracksList = { tracks ->
                                                if (tracks.isNotEmpty()) {
                                                    playbackService?.queueManager?.setShuffleMode(ShuffleMode.ALL_TRACKS)
                                                    shuffleModeState.value = ShuffleMode.ALL_TRACKS
                                                    playbackService?.playQueue(tracks.shuffled(), 0)
                                                    navController.openNowPlaying()
                                                }
                                            },
                                            onTrackLongClick = { track ->
                                                trackContextMenuState.value = track
                                            },
                                            onTrackMenuClick = { track ->
                                                trackContextMenuState.value = track
                                            },
                                            onGroupClick = { group ->
                                                if (activeScreen.category == LibraryCategory.ARTISTS || activeScreen.category == LibraryCategory.ALBUM_ARTISTS) {
                                                    navController.navigateTo(NavScreen.ArtistProfile(group.title))
                                                } else {
                                                    drillDownGroup(activeScreen.category, group.title)
                                                    navController.navigateTo(
                                                        NavScreen.Drilldown(
                                                            category = activeScreen.category,
                                                            title = group.title,
                                                            subtitle = group.subtitle
                                                        )
                                                    )
                                                }
                                            },
                                            onGroupLongClick = { group ->
                                                if (activeScreen.category == LibraryCategory.PLAYLISTS) {
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
                                                showCreatePlaylistDialogState.value = true
                                            },
                                            onSortClick = { showSortOrderState.value = true },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            is NavScreen.Drilldown -> {
                                val plVersion = playlistsVersionState.intValue
                                val groupKey = "${activeScreen.category.name}_${activeScreen.title}"
                                val isPinned = preferencesManager.isGroupPinned(groupKey)
                                val currentPlaylist = if (activeScreen.category == LibraryCategory.PLAYLISTS) {
                                    remember(plVersion, activeScreen.title) {
                                        libraryRepo.getUserPlaylists().firstOrNull { it.name.equals(activeScreen.title, ignoreCase = true) }
                                    }
                                } else null
                                AuroDrilldownScreen(
                                    category = activeScreen.category,
                                    groupTitle = activeScreen.title,
                                    listState = getScrollState("drilldown_$groupKey"),
                                    tracks = drilldownTracks,
                                    currentPlayingId = currentTrack?.id ?: -1L,
                                    errorTrackIds = errorTrackIds.toSet(),
                                    isSelectionMode = isSelectionModeState.value,
                                    selectedTrackIds = selectedTrackIdsState.toSet(),
                                    isPinned = isPinned,
                                    playlistCoverPath = currentPlaylist?.coverPath,
                                    onAddTracksToPlaylist = if (activeScreen.category == LibraryCategory.PLAYLISTS && currentPlaylist != null) {
                                        { showPlaylistTrackPickerState.value = currentPlaylist }
                                    } else null,
                                    onEditPlaylist = if (activeScreen.category == LibraryCategory.PLAYLISTS && currentPlaylist != null) {
                                        { editingPlaylistState.value = currentPlaylist }
                                    } else null,
                                    onDeletePlaylist = if (activeScreen.category == LibraryCategory.PLAYLISTS && currentPlaylist != null) {
                                        { deletingPlaylistState.value = currentPlaylist }
                                    } else null,
                                    onTogglePin = {
                                        preferencesManager.togglePinnedGroup(groupKey)
                                    },
                                    onToggleSelectTrack = { track ->
                                        if (selectedTrackIdsState.contains(track.id)) {
                                            selectedTrackIdsState.remove(track.id)
                                        } else {
                                            selectedTrackIdsState.add(track.id)
                                        }
                                    },
                                    showFileFormat = preferencesManager.isShowFileFormatEnabled(),
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

                            is NavScreen.ArtistProfile -> {
                                val artistName = activeScreen.artistName
                                val isPinned = preferencesManager.isGroupPinned("ARTISTS_$artistName")
                                val artistMedia = artistMediaMap[artistName.trim().lowercase()]
                                AuroArtistScreen(
                                    artistName = artistName,
                                    onBack = { navController.navigateUp() },
                                    onNavigateToTracks = { navController.navigateTo(NavScreen.ArtistTracks(artistName)) },
                                    onNavigateToDiscography = { navController.navigateTo(NavScreen.ArtistDiscography(artistName)) },
                                    onAlbumClick = { albumName ->
                                        drillDownGroup(LibraryCategory.ALBUMS, albumName)
                                        navController.navigateTo(
                                            NavScreen.Drilldown(
                                                category = LibraryCategory.ALBUMS,
                                                title = albumName,
                                                subtitle = getString(R.string.group_subtitle_album)
                                            )
                                        )
                                    },
                                    onTrackClick = { _, position ->
                                        val topTracks = libraryRepo.getTop5TracksForArtist(artistName)
                                        if (topTracks.isNotEmpty()) {
                                            playbackService?.playQueue(topTracks, position)
                                            navController.openNowPlaying()
                                        }
                                    },
                                    onTrackLongClick = { track ->
                                        trackContextMenuState.value = track
                                    },
                                    onTrackMenuClick = { track ->
                                        trackContextMenuState.value = track
                                    },
                                    onPlayAll = {
                                        val topTracks = libraryRepo.getTop5TracksForArtist(artistName)
                                        if (topTracks.isNotEmpty()) {
                                            playbackService?.playQueue(topTracks, 0)
                                            navController.openNowPlaying()
                                        }
                                    },
                                    onShuffleAll = {
                                        val topTracks = libraryRepo.getTop5TracksForArtist(artistName)
                                        if (topTracks.isNotEmpty()) {
                                            playbackService?.queueManager?.setShuffleMode(ShuffleMode.ALL_TRACKS)
                                            shuffleModeState.value = ShuffleMode.ALL_TRACKS
                                            playbackService?.playQueue(topTracks.shuffled(), 0)
                                            navController.openNowPlaying()
                                        }
                                    },
                                    currentPlayingId = currentTrack?.id ?: -1L,
                                    isPinned = isPinned,
                                    onTogglePin = {
                                        preferencesManager.togglePinnedGroup("ARTISTS_$artistName")
                                    },
                                    onChangeMedia = {
                                        targetArtistForMediaState.value = artistName
                                        pickArtistMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    onRemoveMedia = {
                                        removeArtistMedia(artistName)
                                    },
                                    customMediaPath = artistMedia?.first,
                                    isGif = artistMedia?.second ?: false,
                                    artistMediaVersion = artistMediaVersion,
                                    libraryVersion = libraryVersion,
                                    errorTrackIds = errorTrackIds.toSet(),
                                    showFileFormat = preferencesManager.isShowFileFormatEnabled(),
                                    libraryRepository = libraryRepo,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.ArtistTracks -> {
                                val artistName = activeScreen.artistName
                                AuroArtistTracksScreen(
                                    artistName = artistName,
                                    onBack = { navController.navigateUp() },
                                    onTrackClick = { track, position ->
                                        val tracks = libraryRepo.getPrimaryTracksForArtist(artistName, SortMode.TITLE_ASC)
                                        val all = if (tracks.any { it.id == track.id }) tracks else (tracks + libraryRepo.getFeatTracksForArtist(artistName, SortMode.TITLE_ASC)).sortedBy { it.title.lowercase() }
                                        val idx = all.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                        playbackService?.playQueue(all, idx)
                                        navController.openNowPlaying()
                                    },
                                    onTrackLongClick = { track ->
                                        trackContextMenuState.value = track
                                    },
                                    onTrackMenuClick = { track ->
                                        trackContextMenuState.value = track
                                    },
                                    onPlayAll = { tracks ->
                                        if (tracks.isNotEmpty()) {
                                            playbackService?.playQueue(tracks, 0)
                                            navController.openNowPlaying()
                                        }
                                    },
                                    onShuffleAll = { tracks ->
                                        if (tracks.isNotEmpty()) {
                                            playbackService?.queueManager?.setShuffleMode(ShuffleMode.ALL_TRACKS)
                                            shuffleModeState.value = ShuffleMode.ALL_TRACKS
                                            playbackService?.playQueue(tracks.shuffled(), 0)
                                            navController.openNowPlaying()
                                        }
                                    },
                                    currentPlayingId = currentTrack?.id ?: -1L,
                                    libraryVersion = libraryVersion,
                                    errorTrackIds = errorTrackIds.toSet(),
                                    showFileFormat = preferencesManager.isShowFileFormatEnabled(),
                                    libraryRepository = libraryRepo,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.ArtistDiscography -> {
                                val artistName = activeScreen.artistName
                                AuroArtistDiscographyScreen(
                                    artistName = artistName,
                                    onBack = { navController.navigateUp() },
                                    onAlbumClick = { albumName ->
                                        drillDownGroup(LibraryCategory.ALBUMS, albumName)
                                        navController.navigateTo(
                                            NavScreen.Drilldown(
                                                category = LibraryCategory.ALBUMS,
                                                title = albumName,
                                                subtitle = getString(R.string.group_subtitle_album)
                                            )
                                        )
                                    },
                                    libraryVersion = libraryVersion,
                                    libraryRepository = libraryRepo,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.NowPlaying -> {
                                AuroNowPlayingScreen(
                                    track = currentTrack,
                                    isPlaying = isPlaying,
                                    progressMs = progressMsState.longValue,
                                    durationMs = durationMs,
                                    coverBitmap = currentCover,
                                    shuffleMode = shuffleMode,
                                    repeatMode = repeatMode,
                                    priorityTracks = upcomingPriorityQueueState.value,
                                    regularTracks = upcomingRegularQueueState.value,
                                    playbackHistory = playbackHistoryState.value,
                                    currentTrackId = currentTrack?.id ?: -1L,
                                    onQueueTrackClick = { _, idx, isPriority ->
                                        val section = if (isPriority) QueueSection.PRIORITY else QueueSection.QUEUE
                                        playbackService?.skipToUpcoming(section, idx)
                                        refreshQueueAndHistoryState()
                                    },
                                    onMoveUpcomingItem = { section, from, to ->
                                        playbackService?.moveUpcomingQueueItem(section, from, to)
                                        refreshQueueAndHistoryState()
                                    },
                                    onRemoveUpcomingItem = { section, index ->
                                        val removed = playbackService?.removeUpcomingQueueItem(section, index)
                                        refreshQueueAndHistoryState()
                                        removed
                                    },
                                    onInsertUpcomingItem = { section, index, trackToInsert ->
                                        playbackService?.insertUpcomingQueueItem(section, index, trackToInsert)
                                        refreshQueueAndHistoryState()
                                    },
                                    onClearManualQueue = {
                                        playbackService?.clearManualQueueKeepingCurrent()
                                        refreshQueueAndHistoryState()
                                    },
                                    onClearHistory = {
                                        playbackService?.clearPlaybackHistory()
                                        refreshQueueAndHistoryState()
                                    },
                                    onPlayTrack = { trackToPlay ->
                                        playbackService?.playTrack(trackToPlay)
                                        refreshQueueAndHistoryState()
                                    },
                                    onEnqueueNext = { trackToEnqueue ->
                                        playbackService?.enqueueNext(trackToEnqueue)
                                        refreshQueueAndHistoryState()
                                    },
                                    onRemoveQueueTrack = { trackToRemove ->
                                        playbackService?.queueManager?.removeTrackById(trackToRemove.id)
                                        refreshQueueAndHistoryState()
                                    },
                                    onClearQueue = {
                                        playbackService?.clearManualQueueKeepingCurrent()
                                        refreshQueueAndHistoryState()
                                    },
                                    onPlayPause = { playbackService?.togglePlayPause() },
                                    onNext = { playbackService?.playNext() },
                                    onPrevious = { playbackService?.playPrevious() },
                                    onSeek = { fraction ->
                                        val dur = durationMs.coerceAtLeast(1L)
                                        val target = (fraction * dur).toLong()
                                        playbackService?.seekTo(target)
                                        progressMsState.longValue = target
                                    },
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
                                    onOpenTrackInfo = {
                                        trackInfoDialogState.value = currentTrack
                                    },
                                    onAddToPlaylist = {
                                        addToPlaylistDialogTrack.value = currentTrack
                                    },
                                    onOpenSleepTimer = {
                                        showSleepTimerState.value = true
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.Equalizer -> {
                                AuroEqualizerScreen(
                                    listState = getScrollState("equalizer"),
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
                                    listState = getScrollState("search"),
                                    searchQuery = searchQuery,
                                    onQueryChange = { q -> onSearchQueryChange(q) },
                                    allTracks = homeSnapshot.allTracks,
                                    currentPlayingId = currentTrack?.id ?: -1L,
                                    preferencesManager = preferencesManager,
                                    errorTrackIds = errorTrackIds.toSet(),
                                    onTrackClick = { track, position ->
                                        val all = homeSnapshot.allTracks
                                        val idx = all.indexOfFirst { it.id == track.id }.takeIf { it != -1 } ?: position
                                        playbackService?.playQueue(all, idx)
                                        navController.openNowPlaying()
                                    },
                                    onTrackMenuClick = { track ->
                                        trackContextMenuState.value = track
                                    },
                                    onTrackLongClick = { track ->
                                        trackContextMenuState.value = track
                                    },
                                    onAlbumClick = { albumName ->
                                        drillDownGroup(LibraryCategory.ALBUMS, albumName)
                                        navController.navigateTo(
                                            NavScreen.Drilldown(
                                                category = LibraryCategory.ALBUMS,
                                                title = albumName,
                                                subtitle = getString(R.string.group_subtitle_album)
                                            )
                                        )
                                    },
                                    onArtistClick = { artistName ->
                                        navController.navigateTo(NavScreen.ArtistProfile(artistName))
                                    },
                                    onClose = { navController.navigateUp() },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.Queue -> {
                                AuroQueueScreen(
                                    priorityTracks = priorityQueueState.value,
                                    regularTracks = regularQueueState.value,
                                    currentTrackId = currentTrack?.id ?: -1L,
                                    errorTrackIds = errorTrackIds.toSet(),
                                    listState = getScrollState("queue"),
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
                                        playbackService?.let { s ->
                                            priorityQueueState.value = s.queueManager.getPriorityQueue()
                                            regularQueueState.value = s.queueManager.getTracks()
                                        }
                                        renderCurrentCategory()
                                    },
                                    onClearQueue = {
                                        playbackService?.clearPriorityQueue()
                                        playbackService?.let { s ->
                                            priorityQueueState.value = s.queueManager.getPriorityQueue()
                                            regularQueueState.value = s.queueManager.getTracks()
                                        }
                                        renderCurrentCategory()
                                        snackbarState.show(getString(R.string.snackbar_queue_cleared))
                                    },
                                    onShuffleQueue = {
                                        playbackService?.shuffleCurrentQueue()
                                        playbackService?.let { s ->
                                            priorityQueueState.value = s.queueManager.getPriorityQueue()
                                            regularQueueState.value = s.queueManager.getTracks()
                                        }
                                        renderCurrentCategory()
                                        snackbarState.show(getString(R.string.snackbar_queue_shuffled))
                                    },
                                    onMoveItem = { section, from, to ->
                                        playbackService?.moveQueueItem(section, from, to)
                                        playbackService?.let { s ->
                                            priorityQueueState.value = s.queueManager.getPriorityQueue()
                                            regularQueueState.value = s.queueManager.getTracks()
                                        }
                                        renderCurrentCategory()
                                    },
                                    showSnackbar = { msg, action, onAction ->
                                        snackbarState.show(msg, action, onAction)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            is NavScreen.Settings -> {
                                AuroSettingsScreen(
                                    preferencesManager = preferencesManager,
                                    onBackClick = { navController.navigateUp() },
                                    onRescanLibrary = {
                                        loadLibrary()
                                        snackbarState.show(getString(R.string.snackbar_library_updated))
                                    },
                                    selectedFolders = selectedFoldersState.value,
                                    onAddMusicFolder = {
                                        folderPickerLauncher.launch(null)
                                    },
                                    onRemoveMusicFolder = { uriStr ->
                                        removeMusicFolder(uriStr)
                                    },
                                    onSelectMusicFolder = {
                                        folderPickerLauncher.launch(null)
                                    },
                                    onClearMusicFolder = {
                                        preferencesManager.clearSelectedMusicFolder()
                                        selectedFoldersState.value = emptyList()
                                        libraryAccessState.value = evaluateLibraryAccess()
                                        loadLibrary()
                                        snackbarState.show(getString(R.string.snackbar_folder_cleared))
                                    },
                                    onSettingsChanged = {
                                        playbackService?.let { s -> s.queueManager.currentTrack?.let { s.applyEffectivePreamp(it) } }
                                        renderCurrentCategory()
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // AuroSnackbarHost posicionado acima do mini player / navbar (Fase 5.4: insets medidos)
                    val snackbarBottomPadding = if (currentScreen is NavScreen.NowPlaying) {
                        16.dp
                    } else {
                        activeBottomInset + 16.dp
                    }

                    AuroSnackbarHost(
                        state = snackbarState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = snackbarBottomPadding)
                    )

                    // Dock Inferior Colado na Borda (Editorial Dark OLED)
                    if (currentScreen !is NavScreen.NowPlaying) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            if (currentTrack != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color(0xCC050505), Color.Transparent)
                                            )
                                        )
                                        .padding(top = 8.dp, bottom = 8.dp)
                                ) {
                                    EditorialMiniPlayer(
                                        track = currentTrack,
                                        isPlaying = isPlaying,
                                        progressMs = progressMsState.longValue,
                                        durationMs = durationMs,
                                        coverBitmap = miniPlayerCover,
                                        onOpenNowPlaying = { navController.openNowPlaying() },
                                        onPlayPause = { playbackService?.togglePlayPause() },
                                        onNext = { playbackService?.playNext() },
                                        onPrevious = { playbackService?.playPrevious() }
                                    )
                                }
                            }

                            EditorialFloatingNavbar(
                                currentScreen = currentScreen,
                                onHomeClick = { navController.toggleHome() },
                                onSearchClick = { navController.toggleSearch() },
                                onLibraryClick = { navController.toggleLibrary() },
                                onTuneClick = { showMenuSheetState.value = true },
                                backdropLayer = backdropLayer,
                                backdropOrigin = contentRootOffset,
                                isBackdropReady = { isBackdropRecorded[0] && !backdropLayer.isReleased }
                            )
                        }
                    }

                    // Diálogos Modais Funcionais e Limpos
                    trackContextMenu?.let { track ->
                        val currentScreen = navController.currentScreen
                        val isInsidePlaylistDrilldown = currentScreen is NavScreen.Drilldown && currentScreen.category == LibraryCategory.PLAYLISTS
                        val playlistTitle = if (isInsidePlaylistDrilldown) (currentScreen as NavScreen.Drilldown).title else null
                        val playlist = if (playlistTitle != null) {
                            libraryRepo.getUserPlaylists().firstOrNull { it.name.equals(playlistTitle, ignoreCase = true) }
                        } else null

                        AuroTrackContextMenuDialog(
                            track = track,
                            onPlayNext = {
                                playbackService?.enqueueNext(track)
                                trackContextMenuState.value = null
                                snackbarState.show(getString(R.string.snackbar_enqueued_priority))
                            },
                            onEnqueueEnd = {
                                playbackService?.enqueueEnd(track)
                                trackContextMenuState.value = null
                                snackbarState.show(getString(R.string.snackbar_enqueued_end))
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
                            onStartSelection = {
                                trackContextMenuState.value = null
                                isSelectionModeState.value = true
                                selectedTrackIdsState.clear()
                                selectedTrackIdsState.add(track.id)
                            },
                            onRemoveFromPlaylist = if (playlist != null) {
                                {
                                    trackContextMenuState.value = null
                                    libraryRepo.removeTrackFromUserPlaylist(playlist.id, track.id)
                                    playlistsVersionState.intValue++
                                    libraryVersionState.intValue++
                                    drillDownGroup(LibraryCategory.PLAYLISTS, playlist.name)
                                    renderCurrentCategory()
                                }
                            } else null,
                            onDismiss = { trackContextMenuState.value = null }
                        )
                    }

                    if (tracksToAddToPlaylistState.value.isNotEmpty()) {
                        val tracks = tracksToAddToPlaylistState.value
                        AuroAddToPlaylistDialog(
                            track = tracks.first(),
                            playlists = homeSnapshot.userPlaylists,
                            onCreatePlaylist = { name ->
                                val plId = libraryRepo.createPlaylist(name)
                                if (plId > 0) {
                                    tracks.forEach { libraryRepo.addTrackToUserPlaylist(plId, it.id) }
                                    playlistsVersionState.intValue++
                                    libraryVersionState.intValue++
                                    renderCurrentCategory()
                                    snackbarState.show(getString(R.string.snackbar_added_multiple_to_playlist, name))
                                }
                                tracksToAddToPlaylistState.value = emptyList()
                                isSelectionModeState.value = false
                                selectedTrackIdsState.clear()
                            },
                            onSelectPlaylist = { pl ->
                                tracks.forEach { libraryRepo.addTrackToUserPlaylist(pl.id, it.id) }
                                playlistsVersionState.intValue++
                                libraryVersionState.intValue++
                                renderCurrentCategory()
                                snackbarState.show(getString(R.string.snackbar_added_multiple_to_playlist, pl.name))
                                tracksToAddToPlaylistState.value = emptyList()
                                isSelectionModeState.value = false
                                selectedTrackIdsState.clear()
                            },
                            onDismiss = { tracksToAddToPlaylistState.value = emptyList() }
                        )
                    }

                    if (showCreatePlaylistDialogState.value) {
                        AuroCreatePlaylistDialog(
                            onCreate = { name ->
                                val plId = libraryRepo.createPlaylist(name)
                                if (plId > 0) {
                                    playlistsVersionState.intValue++
                                    libraryVersionState.intValue++
                                    renderCurrentCategory()
                                    snackbarState.show(getString(R.string.snackbar_playlist_created))
                                    drillDownGroup(LibraryCategory.PLAYLISTS, name)
                                    navController.navigateTo(
                                        NavScreen.Drilldown(
                                            category = LibraryCategory.PLAYLISTS,
                                            title = name,
                                            subtitle = getString(R.string.group_subtitle_playlist)
                                        )
                                    )
                                }
                                showCreatePlaylistDialogState.value = false
                            },
                            onDismiss = { showCreatePlaylistDialogState.value = false }
                        )
                    }

                    // Diálogo de Edição de Playlist (Nome e Capa)
                    editingPlaylistState.value?.let { pl ->
                        val firstTrack = drilldownTracks.firstOrNull()
                        AuroEditPlaylistDialog(
                            playlist = pl,
                            fallbackTrack = firstTrack,
                            onPickCover = {
                                pickPlaylistCoverLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onRemoveCover = {
                                CoverArtLoader.deletePlaylistCover(pl.coverPath)
                                libraryRepo.updatePlaylistCover(pl.id, "")
                                editingPlaylistState.value = pl.copy(coverPath = "")
                                playlistsVersionState.intValue++
                                libraryVersionState.intValue++
                                renderCurrentCategory()
                            },
                            onSave = { newName ->
                                if (newName != pl.name) {
                                    libraryRepo.renamePlaylist(pl.id, newName)
                                    val cur = navController.currentScreen
                                    if (cur is NavScreen.Drilldown && cur.category == LibraryCategory.PLAYLISTS && cur.title == pl.name) {
                                        navController.replaceCurrent(cur.copy(title = newName))
                                        drillDownGroup(LibraryCategory.PLAYLISTS, newName)
                                    }
                                }
                                editingPlaylistState.value = null
                                playlistsVersionState.intValue++
                                libraryVersionState.intValue++
                                renderCurrentCategory()
                            },
                            onDismiss = { editingPlaylistState.value = null }
                        )
                    }

                    // Bottom Sheet para Adicionar Músicas em Lote
                    showPlaylistTrackPickerState.value?.let { pl ->
                        val existingIds by produceState(
                            initialValue = emptySet<Long>(),
                            key1 = pl.id,
                            key2 = drilldownTracks
                        ) {
                            value = withContext(Dispatchers.IO) {
                                libraryRepo.getTracksForUserPlaylist(pl.id).map { it.id }.toSet()
                            }
                        }
                        AuroPlaylistTrackPickerSheet(
                            allTracks = homeSnapshot.allTracks,
                            existingTrackIds = existingIds,
                            onAddTracks = { selectedIds ->
                                libraryRepo.addTracksToPlaylist(pl.id, selectedIds)
                                showPlaylistTrackPickerState.value = null
                                playlistsVersionState.intValue++
                                libraryVersionState.intValue++
                                drillDownGroup(LibraryCategory.PLAYLISTS, pl.name)
                                renderCurrentCategory()
                                snackbarState.show(getString(R.string.snackbar_added_multiple_to_playlist, pl.name))
                            },
                            onDismiss = { showPlaylistTrackPickerState.value = null }
                        )
                    }

                    // Confirmação para Excluir Playlist
                    deletingPlaylistState.value?.let { pl ->
                        AuroConfirmDeleteDialog(
                            title = stringResource(R.string.playlist_delete_action),
                            message = stringResource(R.string.playlist_delete_confirm),
                            onConfirm = {
                                libraryRepo.deletePlaylist(pl.id)
                                deletingPlaylistState.value = null
                                playlistsVersionState.intValue++
                                libraryVersionState.intValue++
                                val cur = navController.currentScreen
                                if (cur is NavScreen.Drilldown && cur.category == LibraryCategory.PLAYLISTS) {
                                    navController.navigateUp()
                                }
                                renderCurrentCategory()
                                snackbarState.show(getString(R.string.playlist_deleted))
                            },
                            onDismiss = { deletingPlaylistState.value = null }
                        )
                    }

                    addToPlaylistDialogTrack.value?.let { track ->
                        AuroAddToPlaylistDialog(
                            track = track,
                            playlists = homeSnapshot.userPlaylists,
                            onCreatePlaylist = { name ->
                                val plId = libraryRepo.createPlaylist(name)
                                if (plId > 0) {
                                    libraryRepo.addTrackToUserPlaylist(plId, track.id)
                                    playlistsVersionState.intValue++
                                    libraryVersionState.intValue++
                                    renderCurrentCategory()
                                    snackbarState.show(getString(R.string.snackbar_playlist_created_track_added))
                                }
                                addToPlaylistDialogTrack.value = null
                            },
                            onSelectPlaylist = { pl ->
                                libraryRepo.addTrackToUserPlaylist(pl.id, track.id)
                                playlistsVersionState.intValue++
                                libraryVersionState.intValue++
                                renderCurrentCategory()
                                snackbarState.show(getString(R.string.snackbar_added_to_playlist, pl.name))
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
                                val msg = if (stopAtEnd) getString(R.string.snackbar_timer_set_track_end, min) else getString(R.string.snackbar_timer_set, min)
                                snackbarState.show(msg)
                            },
                            onCancelTimer = {
                                playbackService?.cancelSleepTimer()
                                snackbarState.show(getString(R.string.snackbar_timer_off))
                            },
                            onDismiss = { showSleepTimerState.value = false }
                        )
                    }

                    if (showMenuSheetState.value) {
                        AuroPowerampMenuSheet(
                            onOpenQueue = {
                                selectCategory(LibraryCategory.QUEUE)
                                navController.navigateTo(NavScreen.CategoryList(LibraryCategory.QUEUE))
                            },
                            onOpenSettings = { navController.navigateTo(NavScreen.Settings) },
                            onOpenEqualizer = { navController.navigateTo(NavScreen.Equalizer) },
                            onOpenSleepTimer = { showSleepTimerState.value = true },
                            onOpenAudioInfo = { showAudioInfoState.value = true },
                            onRescanLibrary = {
                                loadLibrary()
                                snackbarState.show(getString(R.string.snackbar_library_updated))
                            },
                            onOpenAbout = { showAboutState.value = true },
                            onDismiss = { showMenuSheetState.value = false }
                        )
                    }

                    if (showAudioInfoState.value) {
                        val realSampleRate = playbackService?.audioEngine?.getSampleRate()?.takeIf { it > 0 }
                            ?: (currentTrack?.sampleRateHz ?: 48000)
                        AuroAudioEngineDialog(
                            sampleRate = realSampleRate,
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
                            currentProgressMs = progressMsState.longValue,
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
                            title = getString(R.string.confirm_delete_title),
                            message = getString(R.string.confirm_delete_playlist_message, group.title),
                            onConfirm = {
                                deleteUserPlaylist(group.title)
                                deletePlaylistTargetState.value = null
                            },
                            onDismiss = { deletePlaylistTargetState.value = null }
                        )
                    }

                    deletePresetTargetState.value?.let { preset ->
                        AuroConfirmDeleteDialog(
                            title = getString(R.string.confirm_delete_preset_title),
                            message = getString(R.string.confirm_delete_preset_message, preset.name),
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
            }
        }
    }
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

        snackbarState.show(getString(R.string.snackbar_eq_reset))
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
        snackbarState.show(getString(R.string.snackbar_preset_saved, name))
    }

    private fun deleteCustomPreset(preset: MusicDatabase.EqPreset) {
        if (preset.isBuiltin) return
        musicDb.deletePreset(preset.id)
        presetsListState.value = musicDb.getAllPresets()
        if (activePresetNameState.value == preset.name) {
            activePresetNameState.value = "Flat"
            preferencesManager.saveActivePresetName("Flat")
        }
        snackbarState.show(getString(R.string.snackbar_preset_deleted, preset.name))
    }

    private fun deleteUserPlaylist(playlistName: String) {
        val pl = libraryRepo.getUserPlaylists().firstOrNull { it.name == playlistName }
        if (pl != null) {
            libraryRepo.deletePlaylist(pl.id)
            playlistsVersionState.intValue++
            libraryVersionState.intValue++
            renderCurrentCategory()
            snackbarState.show(getString(R.string.snackbar_playlist_deleted, pl.name))
        }
    }

    private fun setTrackRating(track: Track?, rating: Int) {
        if (track == null) return
        libraryRepo.setTrackRating(track.id, rating)
        if (currentTrackState.value?.id == track.id) {
            currentTrackState.value = track.copy(rating = rating)
        }
        libraryVersionState.intValue++
        renderCurrentCategory()
        snackbarState.show(if (rating > 0) getString(R.string.snackbar_rating_set, rating) else getString(R.string.snackbar_rating_removed))
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
                displayedGroupsState.value = folders.map {
                    GroupItem(it.name, it.path, formatTracksCount(this, it.trackCount))
                }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.ALBUMS -> {
                val albums = libraryRepo.getAlbums()
                displayedGroupsState.value = albums.map {
                    val yearStr = if (it.year > 0) " • ${it.year}" else ""
                    GroupItem(it.name, "${it.artist}$yearStr", formatTracksCount(this, it.trackCount))
                }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.ARTISTS -> {
                val artists = libraryRepo.getArtists()
                displayedGroupsState.value = artists.map {
                    GroupItem(it.name, getString(R.string.group_subtitle_artist), formatTracksCount(this, it.trackCount))
                }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.ALBUM_ARTISTS -> {
                val artists = libraryRepo.getAlbumArtists()
                displayedGroupsState.value = artists.map {
                    GroupItem(it.name, getString(R.string.group_subtitle_album_artist), formatTracksCount(this, it.trackCount))
                }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.GENRES -> {
                val genres = libraryRepo.getGenres()
                displayedGroupsState.value = genres.map {
                    GroupItem(it.name, getString(R.string.group_subtitle_genre), formatTracksCount(this, it.trackCount))
                }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.YEARS -> {
                val years = libraryRepo.getYears()
                displayedGroupsState.value = years.map {
                    GroupItem(it.year.toString(), getString(R.string.group_subtitle_year), formatTracksCount(this, it.trackCount))
                }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.COMPOSERS -> {
                val comps = libraryRepo.getComposers()
                displayedGroupsState.value = comps.map {
                    GroupItem(it.name, getString(R.string.group_subtitle_composer), formatTracksCount(this, it.trackCount))
                }
                displayedTracksState.value = emptyList()
            }
            LibraryCategory.QUEUE -> {
                val queueTracks = playbackService?.queueManager?.getPriorityQueue() ?: emptyList()
                displayedTracksState.value = queueTracks
                displayedGroupsState.value = emptyList()
            }
            LibraryCategory.PLAYLISTS -> {
                val pls = libraryRepo.getUserPlaylists()
                displayedGroupsState.value = pls.map {
                    GroupItem(it.name, getString(R.string.group_subtitle_playlist), formatTracksCount(this, it.trackCount), it.coverPath)
                }
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
                val pl = libraryRepo.getUserPlaylists().firstOrNull { it.name.equals(groupName, ignoreCase = true) }
                if (pl != null) libraryRepo.getTracksForUserPlaylist(pl.id) else emptyList()
            }
            LibraryCategory.MOST_PLAYED -> libraryRepo.getTop50MostPlayed()
            LibraryCategory.TOP_RATED -> libraryRepo.getFavoriteTracks()
            else -> emptyList()
        }
        drilldownTracksState.value = tracks
    }

    private fun onSearchQueryChange(query: String) {
        searchQueryState.value = query
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
            libraryVersionState.intValue++
            refreshQueueAndHistoryState()
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            isPlayingState.value = isPlaying
            if (isPlaying) {
                consecutivePlaybackErrors = 0
            }
        }
    }

    override fun onPlaybackError(trackId: Long, trackTitle: String) {
        runOnUiThread {
            if (!errorTrackIdsState.contains(trackId)) {
                errorTrackIdsState.add(trackId)
            }
            consecutivePlaybackErrors++
            AuroHaptics.reject(window.decorView)

            if (consecutivePlaybackErrors < 3) {
                snackbarState.show(
                    getString(R.string.playback_error_skipping, trackTitle)
                )
                skipJob?.cancel()
                skipJob = lifecycleScope.launch {
                    delay(1200)
                    playbackService?.playNext()
                }
            } else {
                skipJob?.cancel()
                playbackService?.stop()
                snackbarState.show(
                    text = getString(R.string.playback_error_multiple),
                    action = getString(R.string.playback_retry),
                    onAction = {
                        consecutivePlaybackErrors = 0
                        playbackService?.playNext()
                    }
                )
            }
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
            regularQueueState.value = tracks
            refreshQueueAndHistoryState()
            if (currentCategoryState.value == LibraryCategory.QUEUE) {
                displayedTracksState.value = tracks
            }
        }
    }

    private fun loadLibrary() {
        isLoadingLibraryState.value = true
        errorTrackIdsState.clear()
        consecutivePlaybackErrors = 0
        try {
            libraryRepo.loadTracks(this) { list ->
                runOnUiThread {
                    try {
                        totalTracksCountState.intValue = list.size
                        loadingTracksCountState.intValue = list.size
                        isLoadingLibraryState.value = false
                        libraryVersionState.intValue++
                        selectCategory(LibraryCategory.ALL_SONGS)
                        playbackService?.restoreSavedQueue(list)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Erro ao atualizar UI com biblioteca", e)
                        isLoadingLibraryState.value = false
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Erro ao disparar carregamento de biblioteca", e)
            isLoadingLibraryState.value = false
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
        libraryRepo.onStatsChangedListener = null
        if (isBound) {
            playbackService?.removeListener(this)
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
