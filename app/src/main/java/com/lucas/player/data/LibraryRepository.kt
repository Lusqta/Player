package com.lucas.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.lucas.player.model.*
import java.io.File
import java.util.concurrent.Executors

class LibraryRepository() {

    companion object {
        @Volatile
        private var instance: LibraryRepository? = null

        private val sharedTracks = mutableListOf<Track>()
        @Volatile
        private var sharedMusicDb: MusicDatabase? = null
        @Volatile
        private var appContext: Context? = null
        private val sharedExecutor = Executors.newSingleThreadExecutor()

        fun getInstance(context: Context? = null): LibraryRepository {
            if (context != null && appContext == null) {
                init(context)
            }
            return instance ?: synchronized(this) {
                instance ?: LibraryRepository().also { instance = it }
            }
        }

        fun init(context: Context) {
            if (appContext == null) {
                appContext = context.applicationContext
            }
            if (sharedMusicDb == null) {
                sharedMusicDb = MusicDatabase(context.applicationContext)
            }
        }

        private fun getOrCreateDb(context: Context? = null): MusicDatabase? {
            if (sharedMusicDb == null) {
                val ctx = context ?: appContext
                if (ctx != null) {
                    sharedMusicDb = MusicDatabase(ctx.applicationContext)
                }
            }
            return sharedMusicDb
        }
    }

    fun loadTracks(context: Context, onComplete: (List<Track>) -> Unit) {
        init(context)
        val prefs = PreferencesManager(context)
        val folderUris = prefs.getMusicFolderUris()

        val db = getOrCreateDb(context)
        val statsMap = db?.getAllStats() ?: emptyMap()
        val minSec = prefs.getIgnoreShorterThanSeconds()

        // 1. Emissão imediata do cache para as pastas configuradas (startup instantâneo estilo Poweramp)
        if (folderUris.isNotEmpty() && db != null) {
            val cachedTracks = db.getCachedTracks()
                .filter { cached -> folderUris.contains(cached.treeUri) }
            if (cachedTracks.isNotEmpty()) {
                val initialList = cachedTracks.mapNotNull { cached ->
                    val track = cachedToTrack(cached, statsMap)
                    if (minSec > 0 && track.durationMs > 0 && track.durationMs < minSec * 1000L) null else track
                }
                if (initialList.isNotEmpty()) {
                    synchronized(sharedTracks) {
                        sharedTracks.clear()
                        sharedTracks.addAll(initialList)
                    }
                    onComplete(initialList)
                }
            }
        }

        // 2. Varredura e sincronização em background via SAF puro (DocumentsContract)
        sharedExecutor.execute {
            try {
                val list = scanSafFolders(context, folderUris, statsMap, minSec)
                synchronized(sharedTracks) {
                    sharedTracks.clear()
                    sharedTracks.addAll(list)
                }
                onComplete(list)
            } catch (e: Throwable) {
                android.util.Log.e("LibraryRepository", "Erro na varredura de pastas SAF", e)
                onComplete(getAllTracks())
            }
        }
    }

    fun getAllTracks(): List<Track> {
        return synchronized(sharedTracks) { sharedTracks.toList() }
    }

    fun recordTrackPlayed(trackId: Long) {
        sharedExecutor.execute {
            getOrCreateDb()?.incrementPlayCount(trackId)
            synchronized(sharedTracks) {
                val index = sharedTracks.indexOfFirst { it.id == trackId }
                if (index != -1) {
                    val t = sharedTracks[index]
                    sharedTracks[index] = t.copy(
                        playCount = t.playCount + 1,
                        lastPlayedTimestamp = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    fun setTrackRating(trackId: Long, rating: Int) {
        sharedExecutor.execute {
            getOrCreateDb()?.setRating(trackId, rating)
            synchronized(sharedTracks) {
                val index = sharedTracks.indexOfFirst { it.id == trackId }
                if (index != -1) {
                    val t = sharedTracks[index]
                    sharedTracks[index] = t.copy(rating = rating)
                }
            }
        }
    }

    fun ensureReplayGainLoaded(context: Context, track: Track): Track {
        if (track.replayGainTrackGain != 0.0f || track.replayGainTrackPeak != 1.0f) {
            return track
        }
        val rgInfo = if (track.uri.scheme == "content") {
            ReplayGainReader.readFromUri(context, track.uri)
        } else if (track.path.isNotEmpty()) {
            ReplayGainReader.readFromFile(track.path)
        } else {
            ReplayGainReader.readFromUri(context, track.uri)
        }
        if (!rgInfo.hasData) return track
        val updated = track.copy(
            replayGainTrackGain = rgInfo.trackGainDb,
            replayGainTrackPeak = rgInfo.trackPeak,
            replayGainAlbumGain = rgInfo.albumGainDb,
            replayGainAlbumPeak = rgInfo.albumPeak
        )
        synchronized(sharedTracks) {
            val idx = sharedTracks.indexOfFirst { it.id == track.id }
            if (idx != -1) {
                sharedTracks[idx] = updated
            }
        }
        return updated
    }

    fun ensureReplayGainLoaded(track: Track): Track {
        val ctx = appContext ?: return track
        return ensureReplayGainLoaded(ctx, track)
    }

    // --- Categorias e Agrupamentos ---

    fun getAlbums(): List<Album> {
        val all = getAllTracks()
        return all.groupBy { it.album }
            .map { (albumName, albumTracks) ->
                val artist = albumTracks.firstOrNull()?.artist ?: "<Desconhecido>"
                val year = albumTracks.map { it.year }.filter { it > 0 }.minOrNull() ?: 0
                Album(albumName, artist, albumTracks.size, year)
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getTracksForAlbum(albumName: String, sortMode: SortMode = SortMode.TRACK_NUMBER): List<Track> {
        val filtered = getAllTracks().filter { it.album.equals(albumName, ignoreCase = true) }
        return applySorting(filtered, sortMode)
    }

    fun getArtists(): List<Artist> {
        val all = getAllTracks()
        val artistTrackCount = mutableMapOf<String, Int>()
        val casingMap = mutableMapOf<String, MutableMap<String, Int>>()

        for (track in all) {
            val names = splitArtists(track.artist)
            for (name in names) {
                val key = name.lowercase()
                artistTrackCount[key] = (artistTrackCount[key] ?: 0) + 1
                val cases = casingMap.getOrPut(key) { mutableMapOf() }
                cases[name] = (cases[name] ?: 0) + 1
            }
        }

        return artistTrackCount.map { (key, count) ->
            val bestName = casingMap[key]?.maxByOrNull { it.value }?.key ?: key
            Artist(bestName, count)
        }.sortedBy { it.name.lowercase() }
    }

    fun getTracksForArtist(artistName: String, sortMode: SortMode = SortMode.ALBUM_ASC): List<Track> {
        val filtered = getAllTracks().filter { track ->
            splitArtists(track.artist).any { it.equals(artistName, ignoreCase = true) }
        }
        return applySorting(filtered, sortMode)
    }

    fun getPrimaryTracksForArtist(artistName: String, sortMode: SortMode = SortMode.ALBUM_ASC): List<Track> {
        val filtered = getAllTracks().filter { isPrimaryArtistTrack(it, artistName) }
        return applySorting(filtered, sortMode)
    }

    fun getFeatTracksForArtist(artistName: String, sortMode: SortMode = SortMode.ALBUM_ASC): List<Track> {
        val filtered = getAllTracks().filter { isFeatArtistTrack(it, artistName) }
        return applySorting(filtered, sortMode)
    }

    fun getTop5TracksForArtist(artistName: String): List<Track> {
        val primary = getPrimaryTracksForArtist(artistName).filterNot { isJunkAudio(it) }
        val base = if (primary.isNotEmpty()) {
            primary
        } else {
            getFeatTracksForArtist(artistName).filterNot { isJunkAudio(it) }
        }
        if (base.isEmpty()) return emptyList()

        val played = base.filter { it.playCount > 0 }
            .sortedWith(compareByDescending<Track> { it.playCount }.thenByDescending { it.lastPlayedTimestamp })
            .take(5)

        if (played.size >= 5) {
            return played
        }

        val playedIds = played.map { it.id }.toSet()
        val remaining = base.filterNot { it.id in playedIds }.shuffled()
        val needed = 5 - played.size
        return played + remaining.take(needed)
    }

    fun getArtistReleases(artistName: String): List<ArtistRelease> {
        val primary = getPrimaryTracksForArtist(artistName)
        val validTracks = primary.filter { isValidHighlightName(it.album) }
        if (validTracks.isEmpty()) return emptyList()

        return validTracks.groupBy { it.album.trim() }
            .map { (albumName, tracks) ->
                val year = tracks.maxOfOrNull { it.year } ?: 0
                val latestDateAddedSec = tracks.maxOfOrNull { it.dateAddedSec } ?: 0L
                val trackCount = tracks.size
                val maxTrackNumber = tracks.maxOfOrNull { it.trackNumber } ?: 0
                val totalPlays = tracks.sumOf { it.playCount }
                val coverTrack = tracks.maxByOrNull { it.playCount } ?: tracks.first()
                val isAlbum = classifyIsAlbum(albumName, trackCount, maxTrackNumber)

                ArtistRelease(
                    albumName = albumName,
                    year = year,
                    latestDateAddedSec = latestDateAddedSec,
                    trackCount = trackCount,
                    maxTrackNumber = maxTrackNumber,
                    totalPlays = totalPlays,
                    coverTrack = coverTrack,
                    isAlbum = isAlbum
                )
            }
            .sortedWith(
                compareByDescending<ArtistRelease> { it.year }
                    .thenByDescending { it.latestDateAddedSec }
                    .thenBy { it.albumName.lowercase() }
            )
    }

    fun getTop3CarouselReleases(artistName: String): List<ArtistRelease> {
        val allReleases = getArtistReleases(artistName)
        if (allReleases.isEmpty()) return emptyList()

        val albums = allReleases.filter { it.isAlbum }
        val candidates = if (albums.size >= 3) {
            albums
        } else {
            val singles = allReleases.filterNot { it.isAlbum }
            albums + singles
        }

        val played = candidates.filter { it.totalPlays > 0 }
            .sortedByDescending { it.totalPlays }
            .take(3)

        if (played.size >= 3) {
            return played
        }

        val playedNames = played.map { it.albumName.lowercase() }.toSet()
        val chronologicalRemaining = candidates.filterNot { it.albumName.lowercase() in playedNames }
            .sortedWith(
                compareByDescending<ArtistRelease> { it.year }
                    .thenByDescending { it.latestDateAddedSec }
                    .thenBy { it.albumName.lowercase() }
            )
        val needed = 3 - played.size
        return played + chronologicalRemaining.take(needed)
    }

    fun resolveArtistFallbackCoverTrack(artistName: String): Track? {
        val releases = getArtistReleases(artistName)
        if (releases.isNotEmpty()) {
            return releases.first().coverTrack
        }
        val primaryTracks = getPrimaryTracksForArtist(artistName)
        if (primaryTracks.isNotEmpty()) {
            return primaryTracks.first()
        }
        val featTracks = getFeatTracksForArtist(artistName)
        if (featTracks.isNotEmpty()) {
            return featTracks.maxByOrNull { it.playCount }
                ?: featTracks.maxByOrNull { it.dateAddedSec }
                ?: featTracks.first()
        }
        return null
    }

    fun getAlbumArtists(): List<Artist> {
        val all = getAllTracks()
        return all.groupBy { if (it.albumArtist.isNotEmpty()) it.albumArtist else it.artist }
            .map { (name, list) ->
                Artist(name, list.size)
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getTracksForAlbumArtist(albumArtist: String, sortMode: SortMode = SortMode.YEAR_DESC): List<Track> {
        val filtered = getAllTracks().filter {
            (if (it.albumArtist.isNotEmpty()) it.albumArtist else it.artist).equals(albumArtist, ignoreCase = true)
        }
        return applySorting(filtered, sortMode)
    }

    fun getFolders(): List<Folder> {
        val all = getAllTracks()
        return all.groupBy { it.folder }
            .map { (folderName, folderTracks) ->
                Folder(folderName, folderName, folderTracks.size)
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getTracksForFolder(folderName: String, sortMode: SortMode = SortMode.TITLE_ASC): List<Track> {
        val filtered = getAllTracks().filter { it.folder.equals(folderName, ignoreCase = true) }
        return applySorting(filtered, sortMode)
    }

    fun getGenres(): List<Genre> {
        val all = getAllTracks()
        return all.filter { it.genre.isNotEmpty() }
            .groupBy { it.genre }
            .map { (genreName, tracks) ->
                Genre(genreName, tracks.size)
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getTracksForGenre(genreName: String, sortMode: SortMode = SortMode.TITLE_ASC): List<Track> {
        val filtered = getAllTracks().filter { it.genre.equals(genreName, ignoreCase = true) }
        return applySorting(filtered, sortMode)
    }

    fun getYears(): List<YearGroup> {
        val all = getAllTracks()
        return all.filter { it.year > 0 }
            .groupBy { it.year }
            .map { (year, tracks) ->
                YearGroup(year, tracks.size)
            }
            .sortedByDescending { it.year }
    }

    fun getTracksForYear(year: Int, sortMode: SortMode = SortMode.TITLE_ASC): List<Track> {
        val filtered = getAllTracks().filter { it.year == year }
        return applySorting(filtered, sortMode)
    }

    fun getComposers(): List<Composer> {
        val all = getAllTracks()
        return all.filter { it.composer.isNotEmpty() }
            .groupBy { it.composer }
            .map { (name, tracks) ->
                Composer(name, tracks.size)
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getTracksForComposer(composerName: String, sortMode: SortMode = SortMode.TITLE_ASC): List<Track> {
        val filtered = getAllTracks().filter { it.composer.equals(composerName, ignoreCase = true) }
        return applySorting(filtered, sortMode)
    }

    fun getRecentlyAdded(): List<Track> {
        return getAllTracks().sortedByDescending { it.dateAddedSec }
    }

    fun getRecentlyPlayed(): List<Track> {
        return getAllTracks()
            .filter { it.lastPlayedTimestamp > 0 }
            .sortedByDescending { it.lastPlayedTimestamp }
    }

    fun getMostPlayed(): List<Track> {
        return getAllTracks()
            .filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
    }

    fun getTop50MostPlayed(): List<Track> {
        return getAllTracks()
            .filter { it.playCount > 0 && !isJunkAudio(it) }
            .sortedWith(
                compareByDescending<Track> { it.playCount }
                    .thenByDescending { it.lastPlayedTimestamp }
            )
            .take(50)
    }

    fun getTopRated(): List<Track> {
        return getAllTracks()
            .filter { it.rating > 0 }
            .sortedByDescending { it.rating }
    }

    fun getFavoriteTracks(): List<Track> {
        return getAllTracks()
            .filter { it.rating > 0 }
            .sortedWith(
                compareByDescending<Track> { it.rating }
                    .thenByDescending { it.lastPlayedTimestamp }
                    .thenBy { it.title.lowercase() }
            )
    }

    // --- Playlists ---

    fun getUserPlaylists(): List<UserPlaylist> {
        val db = getOrCreateDb() ?: return emptyList()
        return db.getPlaylists().map {
            UserPlaylist(it.id, it.name, it.trackCount, it.createdMs, it.coverPath)
        }
    }

    fun createPlaylist(name: String): Long {
        return getOrCreateDb()?.createPlaylist(name) ?: -1L
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        getOrCreateDb()?.renamePlaylist(playlistId, newName)
    }

    fun updatePlaylistCover(playlistId: Long, coverPath: String) {
        getOrCreateDb()?.updatePlaylistCover(playlistId, coverPath)
    }

    fun getTracksForUserPlaylist(playlistId: Long): List<Track> {
        val db = getOrCreateDb() ?: return emptyList()
        val trackIds = db.getTracksForPlaylist(playlistId)
        val all = getAllTracks().associateBy { it.id }
        return trackIds.mapNotNull { all[it] }
    }

    fun addTrackToUserPlaylist(playlistId: Long, trackId: Long) {
        getOrCreateDb()?.addTrackToPlaylist(playlistId, trackId)
    }

    fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) {
        getOrCreateDb()?.addTracksToPlaylist(playlistId, trackIds)
    }

    fun removeTrackFromUserPlaylist(playlistId: Long, trackId: Long) {
        getOrCreateDb()?.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun deletePlaylist(playlistId: Long) {
        val db = getOrCreateDb() ?: return
        val pl = db.getPlaylists().firstOrNull { it.id == playlistId }
        if (pl != null && pl.coverPath.isNotBlank()) {
            CoverArtLoader.deletePlaylistCover(pl.coverPath)
        }
        db.deletePlaylist(playlistId)
    }

    // --- Histórico Contínuo ---

    fun recordPlaybackHistory(trackId: Long) {
        getOrCreateDb()?.recordPlaybackHistory(trackId)
    }

    fun getPlaybackHistory(limit: Int = 100): List<Pair<Long, Track>> {
        val db = getOrCreateDb() ?: return emptyList()
        val historyRows = db.getPlaybackHistory(limit)
        if (historyRows.isEmpty()) return emptyList()
        val trackMap = getAllTracks().associateBy { it.id }
        return historyRows.mapNotNull { (rowId, trackId) ->
            val track = trackMap[trackId] ?: return@mapNotNull null
            rowId to track
        }
    }

    fun clearPlaybackHistory() {
        getOrCreateDb()?.clearPlaybackHistory()
    }

    // --- Mídia de Artista (Foto / GIF) ---

    fun setArtistMedia(artistName: String, path: String, isGif: Boolean) {
        getOrCreateDb()?.setArtistMedia(artistName, path, isGif)
    }

    fun getArtistMedia(artistName: String): Pair<String, Boolean>? {
        return getOrCreateDb()?.getArtistMedia(artistName)
    }

    fun getAllArtistMedia(): Map<String, Pair<String, Boolean>> {
        return getOrCreateDb()?.getAllArtistMedia() ?: emptyMap()
    }

    fun removeArtistMedia(artistName: String) {
        getOrCreateDb()?.removeArtistMedia(artistName)
    }

    // --- Ordenação Dinâmica ---

    fun applySorting(inputList: List<Track>, sortMode: SortMode): List<Track> {
        return when (sortMode) {
            SortMode.TITLE_ASC -> inputList.sortedBy { it.title.lowercase() }
            SortMode.TITLE_DESC -> inputList.sortedByDescending { it.title.lowercase() }
            SortMode.ARTIST_ASC -> inputList.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }))
            SortMode.ALBUM_ASC -> inputList.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
            SortMode.YEAR_DESC -> inputList.sortedByDescending { it.year }
            SortMode.YEAR_ASC -> inputList.sortedBy { it.year }
            SortMode.TRACK_NUMBER -> inputList.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.title.lowercase() }))
            SortMode.DURATION_DESC -> inputList.sortedByDescending { it.durationMs }
            SortMode.DURATION_ASC -> inputList.sortedBy { it.durationMs }
            SortMode.DATE_ADDED_DESC -> inputList.sortedByDescending { it.dateAddedSec }
            SortMode.PLAY_COUNT_DESC -> inputList.sortedByDescending { it.playCount }
            SortMode.RATING_DESC -> inputList.sortedByDescending { it.rating }
        }
    }

    // --- Scanner Recursivo SAF Puro (DocumentsContract) ---

    private fun fnv1a64(str: String): Long {
        var hash = -3750763034362895579L // 0xcbf29ce484222325L
        for (i in 0 until str.length) {
            hash = hash xor str[i].code.toLong()
            hash *= 1099511628211L // 0x100000001b3L
        }
        val positive = hash and Long.MAX_VALUE
        return if (positive == 0L) 1L else positive
    }

    private fun isAudioFile(name: String, mimeType: String): Boolean {
        if (mimeType.startsWith("audio/", ignoreCase = true)) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "aiff", "aif", "ape", "dsf", "dff", "alac" -> true
            else -> false
        }
    }

    private fun scanSafFolders(
        context: Context,
        treeUriStrings: List<String>,
        statsMap: Map<Long, MusicDatabase.TrackStat>,
        minSec: Int
    ): List<Track> {
        if (treeUriStrings.isEmpty()) return emptyList()

        val db = getOrCreateDb(context)
        val cachedMap = (db?.getCachedTracks() ?: emptyList()).associateBy { it.uri }
        val newOrUpdatedCached = mutableListOf<MusicDatabase.CachedTrack>()
        val foundUris = mutableSetOf<String>()
        val resultTracks = mutableListOf<Track>()

        val contentResolver = context.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        for (treeUriStr in treeUriStrings) {
            val treeUri = try {
                Uri.parse(treeUriStr)
            } catch (_: Exception) {
                continue
            }

            val rootDocId = try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (_: Exception) {
                continue
            }

            val folderNameRoot = PreferencesManager.formatFolderName(treeUriStr).substringAfterLast('/')

            val queue = ArrayDeque<Pair<String, String>>()
            queue.add(rootDocId to folderNameRoot)

            val lrcMap = mutableMapOf<String, String>()

            data class AudioCandidate(
                val docUri: Uri,
                val docId: String,
                val parentDocId: String,
                val folderName: String,
                val displayName: String,
                val size: Long,
                val lastModifiedSec: Long,
                val mimeType: String
            )
            val audioCandidates = mutableListOf<AudioCandidate>()

            while (queue.isNotEmpty()) {
                val (parentDocId, currentFolderName) = queue.removeFirst()
                val childrenUri = try {
                    DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                } catch (_: Exception) {
                    continue
                }

                var hasNomedia = false
                val folderEntries = mutableListOf<Triple<String, String, String>>()
                val entryDetails = mutableMapOf<String, Pair<Long, Long>>()

                try {
                    contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                        val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                        if (idCol != -1 && nameCol != -1 && mimeCol != -1) {
                            while (cursor.moveToNext()) {
                                val docId = cursor.getString(idCol) ?: continue
                                val displayName = cursor.getString(nameCol) ?: continue
                                val mimeType = cursor.getString(mimeCol) ?: ""
                                val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                                val modMs = if (modCol != -1) cursor.getLong(modCol) else 0L

                                if (displayName.equals(".nomedia", ignoreCase = true)) {
                                    hasNomedia = true
                                    break
                                }

                                if (displayName.startsWith(".")) {
                                    continue
                                }

                                folderEntries.add(Triple(docId, displayName, mimeType))
                                entryDetails[docId] = Pair(size, modMs / 1000L)
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (hasNomedia) {
                    continue
                }

                for ((docId, displayName, mimeType) in folderEntries) {
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val childFolderName = displayName
                        queue.add(docId to childFolderName)
                    } else if (displayName.endsWith(".lrc", ignoreCase = true)) {
                        val baseName = displayName.substringBeforeLast('.')
                        val lrcDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        lrcMap["$parentDocId/${baseName.lowercase()}"] = lrcDocUri.toString()
                    } else if (isAudioFile(displayName, mimeType)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val (size, lastModSec) = entryDetails[docId] ?: Pair(0L, 0L)
                        audioCandidates.add(
                            AudioCandidate(
                                docUri = docUri,
                                docId = docId,
                                parentDocId = parentDocId,
                                folderName = currentFolderName,
                                displayName = displayName,
                                size = size,
                                lastModifiedSec = lastModSec,
                                mimeType = mimeType
                            )
                        )
                    }
                }
            }

            for (candidate in audioCandidates) {
                val uriStr = candidate.docUri.toString()
                foundUris.add(uriStr)

                val baseName = candidate.displayName.substringBeforeLast('.')
                val lrcKey = "${candidate.parentDocId}/${baseName.lowercase()}"
                val lrcUri = lrcMap[lrcKey] ?: ""

                val cached = cachedMap[uriStr]
                if (cached != null && cached.fileSize == candidate.size && (cached.dateModifiedSec == candidate.lastModifiedSec || candidate.lastModifiedSec == 0L)) {
                    val effectiveLrc = if (lrcUri.isNotEmpty()) lrcUri else cached.lrcUri
                    val track = cachedToTrack(cached.copy(lrcUri = effectiveLrc, folder = candidate.folderName), statsMap)
                    if (minSec <= 0 || track.durationMs <= 0 || track.durationMs >= minSec * 1000L) {
                        resultTracks.add(track)
                    }
                } else {
                    val track = extractTrackMetadata(
                        context = context,
                        docUri = candidate.docUri,
                        treeUriStr = treeUriStr,
                        displayName = candidate.displayName,
                        folderName = candidate.folderName,
                        size = candidate.size,
                        lastModifiedSec = candidate.lastModifiedSec,
                        lrcUri = lrcUri,
                        statsMap = statsMap,
                        newOrUpdatedCached = newOrUpdatedCached
                    )
                    if (track != null) {
                        if (minSec <= 0 || track.durationMs <= 0 || track.durationMs >= minSec * 1000L) {
                            resultTracks.add(track)
                        }
                    }
                }
            }
        }

        if (newOrUpdatedCached.isNotEmpty()) {
            db?.upsertCachedTracks(newOrUpdatedCached)
        }

        if (foundUris.isNotEmpty()) {
            db?.deleteCachedTracksNotIn(foundUris)
        }

        return resultTracks
    }

    private fun extractTrackMetadata(
        context: Context,
        docUri: Uri,
        treeUriStr: String,
        displayName: String,
        folderName: String,
        size: Long,
        lastModifiedSec: Long,
        lrcUri: String,
        statsMap: Map<Long, MusicDatabase.TrackStat>,
        newOrUpdatedCached: MutableList<MusicDatabase.CachedTrack>
    ): Track? {
        val uriStr = docUri.toString()
        val trackId = fnv1a64(uriStr)
        val baseName = displayName.substringBeforeLast('.')
        val ext = if (displayName.contains('.')) displayName.substringAfterLast('.').uppercase() else "AUDIO"

        var title = baseName
        var artist = "Artista desconhecido"
        var album = "Álbum desconhecido"
        var albumArtist = ""
        var composer = ""
        var genre = ""
        var year = 0
        var trackNumber = 0
        var discNumber = 1
        var durationMs = 0L
        var bitrateKbps = 0

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, docUri)

            val t = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!t.isNullOrBlank()) title = t

            val a = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            if (!a.isNullOrBlank()) artist = a

            val al = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            if (!al.isNullOrBlank()) album = al

            val aa = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            if (!aa.isNullOrBlank()) albumArtist = aa

            val comp = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            if (!comp.isNullOrBlank()) composer = comp

            val gen = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            if (!gen.isNullOrBlank()) genre = gen

            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = dur?.toLongOrNull() ?: 0L

            val yStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            year = yStr?.filter { it.isDigit() }?.take(4)?.toIntOrNull() ?: 0

            val rawTrackStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val rawTrack = rawTrackStr?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
            trackNumber = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack
            val discStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            discNumber = discStr?.substringBefore('/')?.trim()?.toIntOrNull() ?: (if (rawTrack >= 1000) rawTrack / 1000 else 1)

            val brStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            bitrateKbps = (brStr?.toIntOrNull() ?: 0) / 1000
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        val cachedItem = MusicDatabase.CachedTrack(
            trackId = trackId,
            uri = uriStr,
            treeUri = treeUriStr,
            lrcUri = lrcUri,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            composer = composer,
            genre = genre,
            year = year,
            trackNumber = trackNumber,
            discNumber = discNumber,
            durationMs = durationMs,
            fileSize = size,
            dateModifiedSec = lastModifiedSec,
            dateAddedSec = lastModifiedSec,
            folder = folderName,
            formatTag = ext,
            bitrateKbps = bitrateKbps
        )
        newOrUpdatedCached.add(cachedItem)

        val stat = statsMap[trackId]
        return Track(
            id = trackId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            uri = docUri,
            path = "",
            folder = folderName,
            albumArtist = albumArtist,
            composer = composer,
            genre = genre,
            year = year,
            trackNumber = trackNumber,
            discNumber = discNumber,
            fileSize = size,
            dateAddedSec = lastModifiedSec,
            dateModifiedSec = lastModifiedSec,
            playCount = stat?.playCount ?: 0,
            lastPlayedTimestamp = stat?.lastPlayedMs ?: 0L,
            rating = stat?.rating ?: 0,
            formatTag = ext,
            replayGainTrackGain = 0.0f,
            replayGainTrackPeak = 1.0f,
            replayGainAlbumGain = 0.0f,
            replayGainAlbumPeak = 1.0f,
            lrcUri = lrcUri
        )
    }

    private fun cachedToTrack(cached: MusicDatabase.CachedTrack, statsMap: Map<Long, MusicDatabase.TrackStat>): Track {
        val stat = statsMap[cached.trackId]
        val parsedUri = try {
            Uri.parse(cached.uri)
        } catch (_: Exception) {
            Uri.EMPTY
        }
        return Track(
            id = cached.trackId,
            title = cached.title,
            artist = cached.artist,
            album = cached.album,
            durationMs = cached.durationMs,
            uri = parsedUri,
            path = "",
            folder = cached.folder,
            albumArtist = cached.albumArtist,
            composer = cached.composer,
            genre = cached.genre,
            year = cached.year,
            trackNumber = cached.trackNumber,
            discNumber = cached.discNumber,
            fileSize = cached.fileSize,
            dateAddedSec = cached.dateAddedSec,
            dateModifiedSec = cached.dateModifiedSec,
            playCount = stat?.playCount ?: 0,
            lastPlayedTimestamp = stat?.lastPlayedMs ?: 0L,
            rating = stat?.rating ?: 0,
            formatTag = cached.formatTag,
            replayGainTrackGain = 0.0f,
            replayGainTrackPeak = 1.0f,
            replayGainAlbumGain = 0.0f,
            replayGainAlbumPeak = 1.0f,
            lrcUri = cached.lrcUri
        )
    }
}
