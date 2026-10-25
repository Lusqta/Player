package com.lucas.player.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.lucas.player.model.*
import java.io.File
import java.util.concurrent.Executors

class LibraryRepository {

    private val executor = Executors.newSingleThreadExecutor()
    private val tracks = mutableListOf<Track>()
    private var musicDb: MusicDatabase? = null

    fun loadTracks(context: Context, onComplete: (List<Track>) -> Unit) {
        executor.execute {
            if (musicDb == null) {
                musicDb = MusicDatabase(context.applicationContext)
            }
            val db = musicDb!!
            val statsMap = db.getAllStats()

            val list = queryMediaStore(context, statsMap)
            synchronized(tracks) {
                tracks.clear()
                tracks.addAll(list)
            }
            onComplete(list)
        }
    }

    fun getAllTracks(): List<Track> {
        return synchronized(tracks) { tracks.toList() }
    }

    fun recordTrackPlayed(trackId: Long) {
        executor.execute {
            musicDb?.incrementPlayCount(trackId)
            synchronized(tracks) {
                val index = tracks.indexOfFirst { it.id == trackId }
                if (index != -1) {
                    val t = tracks[index]
                    t.playCount += 1
                    t.lastPlayedTimestamp = System.currentTimeMillis()
                }
            }
        }
    }

    fun setTrackRating(trackId: Long, rating: Int) {
        executor.execute {
            musicDb?.setRating(trackId, rating)
            synchronized(tracks) {
                val index = tracks.indexOfFirst { it.id == trackId }
                if (index != -1) {
                    tracks[index].rating = rating
                }
            }
        }
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
        return all.groupBy { it.artist }
            .map { (artistName, artistTracks) ->
                Artist(artistName, artistTracks.size)
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getTracksForArtist(artistName: String, sortMode: SortMode = SortMode.ALBUM_ASC): List<Track> {
        val filtered = getAllTracks().filter { it.artist.equals(artistName, ignoreCase = true) }
        return applySorting(filtered, sortMode)
    }

    fun getAlbumArtists(): List<Artist> {
        val all = getAllTracks()
        return all.groupBy { if (it.albumArtist.isNotEmpty()) it.albumArtist else it.artist }
            .map { (name, list) ->
                Artist(name, list.size)
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getTracksForAlbumArtist(artistName: String, sortMode: SortMode = SortMode.ALBUM_ASC): List<Track> {
        val filtered = getAllTracks().filter {
            it.albumArtist.equals(artistName, ignoreCase = true) ||
            (it.albumArtist.isEmpty() && it.artist.equals(artistName, ignoreCase = true))
        }
        return applySorting(filtered, sortMode)
    }

    fun getGenres(): List<Genre> {
        val all = getAllTracks()
        return all.filter { it.genre.isNotEmpty() && it.genre != "<unknown>" }
            .groupBy { it.genre }
            .map { (genreName, list) -> Genre(genreName, list.size) }
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
            .map { (year, list) -> YearGroup(year, list.size) }
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
            .map { (compName, list) -> Composer(compName, list.size) }
            .sortedBy { it.name.lowercase() }
    }

    fun getTracksForComposer(composerName: String, sortMode: SortMode = SortMode.TITLE_ASC): List<Track> {
        val filtered = getAllTracks().filter { it.composer.equals(composerName, ignoreCase = true) }
        return applySorting(filtered, sortMode)
    }

    fun getFolders(): List<Folder> {
        val all = getAllTracks()
        return all.groupBy { it.folder }
            .map { (folderName, folderTracks) ->
                val samplePath = folderTracks.firstOrNull()?.path?.let { File(it).parent ?: "" } ?: ""
                Folder(folderName, samplePath, folderTracks.size)
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getTracksForFolder(folderName: String, sortMode: SortMode = SortMode.TITLE_ASC): List<Track> {
        val filtered = getAllTracks().filter { it.folder.equals(folderName, ignoreCase = true) }
        return applySorting(filtered, sortMode)
    }

    fun getRecentlyAdded(limit: Int = 100): List<Track> {
        return getAllTracks()
            .sortedByDescending { it.dateAddedSec }
            .take(limit)
    }

    fun getRecentlyPlayed(limit: Int = 100): List<Track> {
        return getAllTracks()
            .filter { it.lastPlayedTimestamp > 0 }
            .sortedByDescending { it.lastPlayedTimestamp }
            .take(limit)
    }

    fun getMostPlayed(limit: Int = 100): List<Track> {
        return getAllTracks()
            .filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
            .take(limit)
    }

    fun getTopRated(): List<Track> {
        return getAllTracks()
            .filter { it.rating > 0 }
            .sortedByDescending { it.rating }
    }

    // --- Playlists ---

    fun getUserPlaylists(): List<UserPlaylist> {
        val db = musicDb ?: return emptyList()
        return db.getPlaylists().map {
            UserPlaylist(it.id, it.name, it.trackCount, it.createdMs)
        }
    }

    fun createPlaylist(name: String): Long {
        return musicDb?.createPlaylist(name) ?: -1L
    }

    fun getTracksForUserPlaylist(playlistId: Long): List<Track> {
        val db = musicDb ?: return emptyList()
        val trackIds = db.getTracksForPlaylist(playlistId)
        val all = getAllTracks().associateBy { it.id }
        return trackIds.mapNotNull { all[it] }
    }

    fun addTrackToUserPlaylist(playlistId: Long, trackId: Long) {
        musicDb?.addTrackToPlaylist(playlistId, trackId)
    }

    fun removeTrackFromUserPlaylist(playlistId: Long, trackId: Long) {
        musicDb?.removeTrackFromPlaylist(playlistId, trackId)
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

    // --- Query MediaStore ---

    private fun queryMediaStore(context: Context, statsMap: Map<Long, MusicDatabase.TrackStat>): List<Track> {
        val result = mutableListOf<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.COMPOSER,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.Audio.Media.ALBUM_ARTIST)
            projectionList.add(MediaStore.Audio.Media.GENRE)
        }

        val projection = projectionList.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val compCol = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
                val daCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dmCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)

                val albumArtistCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                } else -1

                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Sem título"
                    val artist = cursor.getString(artistCol) ?: "Artista desconhecido"
                    val album = cursor.getString(albumCol) ?: "Álbum desconhecido"
                    val durationMs = cursor.getLong(durCol)
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val folder = if (path.isNotEmpty()) {
                        File(path).parentFile?.name ?: "Música"
                    } else {
                        "Música"
                    }

                    val year = if (yearCol != -1) cursor.getInt(yearCol) else 0
                    val rawTrack = if (trackCol != -1) cursor.getInt(trackCol) else 0
                    val trackNumber = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack
                    val discNumber = if (rawTrack >= 1000) rawTrack / 1000 else 1
                    val composer = if (compCol != -1) cursor.getString(compCol) ?: "" else ""
                    val dateAdded = if (daCol != -1) cursor.getLong(daCol) else 0L
                    val dateModified = if (dmCol != -1) cursor.getLong(dmCol) else 0L
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L

                    val albumArtist = if (albumArtistCol != -1) cursor.getString(albumArtistCol) ?: "" else ""
                    val genre = if (genreCol != -1) cursor.getString(genreCol) ?: "" else ""

                    // Extension format tag
                    val ext = if (path.contains('.')) path.substringAfterLast('.').uppercase() else "AUDIO"
                    val formatTag = ext

                    // ReplayGain tags
                    val rgInfo = if (path.isNotEmpty()) ReplayGainReader.readFromFile(path) else ReplayGainReader.ReplayGainInfo()

                    // Merge stats
                    val stat = statsMap[id]
                    val playCount = stat?.playCount ?: 0
                    val lastPlayed = stat?.lastPlayedMs ?: 0L
                    val rating = stat?.rating ?: 0

                    val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    result.add(
                        Track(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            uri = trackUri,
                            path = path,
                            folder = folder,
                            albumArtist = albumArtist,
                            composer = composer,
                            genre = genre,
                            year = year,
                            trackNumber = trackNumber,
                            discNumber = discNumber,
                            fileSize = size,
                            dateAddedSec = dateAdded,
                            dateModifiedSec = dateModified,
                            playCount = playCount,
                            lastPlayedTimestamp = lastPlayed,
                            rating = rating,
                            formatTag = formatTag,
                            replayGainTrackGain = rgInfo.trackGainDb,
                            replayGainTrackPeak = rgInfo.trackPeak,
                            replayGainAlbumGain = rgInfo.albumGainDb,
                            replayGainAlbumPeak = rgInfo.albumPeak
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return result
    }
}
