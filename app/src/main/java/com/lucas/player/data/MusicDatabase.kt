package com.lucas.player.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Lightweight native SQLite Database managing track statistics, ratings, and playlists
 * replicating Poweramp's database persistence model.
 */
class MusicDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    data class TrackStat(
        val trackId: Long,
        val playCount: Int,
        val lastPlayedMs: Long,
        val rating: Int
    )

    data class Playlist(
        val id: Long,
        val name: String,
        val createdMs: Long,
        val trackCount: Int = 0,
        val coverPath: String = ""
    )

    data class EqPreset(
        val id: Long = 0,
        val name: String,
        val bandGains: List<Float>,
        val bassGain: Float = 0f,
        val trebleGain: Float = 0f,
        val preampGain: Float = 0f,
        val isBuiltin: Boolean = false
    )

    data class CachedTrack(
        val trackId: Long,
        val uri: String,
        val treeUri: String,
        val lrcUri: String = "",
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String = "",
        val composer: String = "",
        val genre: String = "",
        val year: Int = 0,
        val trackNumber: Int = 0,
        val discNumber: Int = 1,
        val durationMs: Long = 0L,
        val fileSize: Long = 0L,
        val dateModifiedSec: Long = 0L,
        val dateAddedSec: Long = 0L,
        val folder: String = "",
        val formatTag: String = "",
        val bitrateKbps: Int = 0,
        val sampleRateHz: Int = 44100
    )

    data class AlbumMeta(
        val albumKey: String,
        val mediaPath: String = "",
        val isGif: Boolean = false,
        val notes: String = ""
    )

    data class AlbumCollectionRow(
        val id: Long,
        val name: String,
        val createdMs: Long,
        val albumCount: Int = 0
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_STATS (
                $COL_TRACK_ID INTEGER PRIMARY KEY,
                $COL_PLAY_COUNT INTEGER DEFAULT 0,
                $COL_LAST_PLAYED INTEGER DEFAULT 0,
                $COL_RATING INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLISTS (
                $COL_PL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PL_NAME TEXT NOT NULL,
                $COL_PL_CREATED INTEGER NOT NULL,
                $COL_PL_COVER_PATH TEXT DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLIST_ITEMS (
                $COL_ITEM_PL_ID INTEGER NOT NULL,
                $COL_ITEM_TRACK_ID INTEGER NOT NULL,
                $COL_ITEM_ORDER INTEGER NOT NULL,
                PRIMARY KEY ($COL_ITEM_PL_ID, $COL_ITEM_TRACK_ID)
            )
            """.trimIndent()
        )

        createPresetsTable(db)
        seedBuiltinPresets(db)
        createCachedTracksTable(db)
        createPlaybackHistoryTable(db)
        createArtistMediaTable(db)
        createPlayEventsTable(db)
        createAlbumMetaTable(db)
        createAlbumCollectionsTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createPresetsTable(db)
            seedBuiltinPresets(db)
        }
        if (oldVersion < 3) {
            createCachedTracksTable(db)
        }
        if (oldVersion < 4) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_CACHED_TRACKS")
            createCachedTracksTable(db)
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE $TABLE_PLAYLISTS ADD COLUMN $COL_PL_COVER_PATH TEXT DEFAULT ''")
            } catch (_: Exception) {}
        }
        if (oldVersion < 6) {
            createPlaybackHistoryTable(db)
        }
        if (oldVersion < 7) {
            createArtistMediaTable(db)
        }
        if (oldVersion < 8) {
            createPlayEventsTable(db)
        }
        if (oldVersion < 9) {
            createAlbumMetaTable(db)
        }
        if (oldVersion < 10) {
            createAlbumCollectionsTables(db)
        }
    }

    private fun createAlbumCollectionsTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ALBUM_COLLECTIONS (
                $COL_AC_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_AC_NAME TEXT NOT NULL,
                $COL_AC_CREATED INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ALBUM_COLLECTION_ITEMS (
                $COL_ACI_COLLECTION_ID INTEGER NOT NULL,
                $COL_ACI_ALBUM_NAME TEXT NOT NULL,
                $COL_ACI_SORT_ORDER INTEGER NOT NULL,
                PRIMARY KEY ($COL_ACI_COLLECTION_ID, $COL_ACI_ALBUM_NAME)
            )
            """.trimIndent()
        )
    }

    private fun createAlbumMetaTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ALBUM_META (
                $COL_ALBUM_KEY TEXT PRIMARY KEY,
                $COL_ALBUM_MEDIA_PATH TEXT DEFAULT '',
                $COL_ALBUM_IS_GIF INTEGER DEFAULT 0,
                $COL_ALBUM_NOTES TEXT DEFAULT ''
            )
            """.trimIndent()
        )
    }

    private fun createPlayEventsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PLAY_EVENTS (
                $COL_PLAY_EVENT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PLAY_EVENT_TRACK_ID INTEGER NOT NULL,
                $COL_PLAY_EVENT_PLAYED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_play_events_time ON $TABLE_PLAY_EVENTS($COL_PLAY_EVENT_PLAYED_AT)")
    }

    private fun createArtistMediaTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ARTIST_MEDIA (
                $COL_ARTIST_KEY TEXT PRIMARY KEY,
                $COL_ARTIST_MEDIA_PATH TEXT NOT NULL,
                $COL_ARTIST_IS_GIF INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
    }

    private fun createPlaybackHistoryTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PLAYBACK_HISTORY (
                $COL_HISTORY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_HISTORY_TRACK_ID INTEGER NOT NULL,
                $COL_HISTORY_PLAYED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createCachedTracksTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CACHED_TRACKS (
                $COL_CACHE_TRACK_ID INTEGER PRIMARY KEY,
                $COL_CACHE_URI TEXT NOT NULL UNIQUE,
                $COL_CACHE_TREE_URI TEXT NOT NULL,
                $COL_CACHE_LRC_URI TEXT DEFAULT '',
                $COL_CACHE_TITLE TEXT,
                $COL_CACHE_ARTIST TEXT,
                $COL_CACHE_ALBUM TEXT,
                $COL_CACHE_ALBUM_ARTIST TEXT,
                $COL_CACHE_COMPOSER TEXT,
                $COL_CACHE_GENRE TEXT,
                $COL_CACHE_YEAR INTEGER,
                $COL_CACHE_TRACK_NUMBER INTEGER,
                $COL_CACHE_DISC_NUMBER INTEGER,
                $COL_CACHE_DURATION_MS INTEGER,
                $COL_CACHE_FILE_SIZE INTEGER,
                $COL_CACHE_DATE_MODIFIED INTEGER,
                $COL_CACHE_DATE_ADDED INTEGER,
                $COL_CACHE_FOLDER TEXT,
                $COL_CACHE_FORMAT_TAG TEXT,
                $COL_CACHE_BITRATE INTEGER,
                $COL_CACHE_SAMPLE_RATE INTEGER
            )
            """.trimIndent()
        )
    }

    private fun createPresetsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PRESETS (
                $COL_PRESET_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PRESET_NAME TEXT NOT NULL UNIQUE,
                $COL_PRESET_GAINS TEXT NOT NULL,
                $COL_PRESET_BASS REAL DEFAULT 0,
                $COL_PRESET_TREBLE REAL DEFAULT 0,
                $COL_PRESET_PREAMP REAL DEFAULT 0,
                $COL_PRESET_BUILTIN INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
    }

    private fun seedBuiltinPresets(db: SQLiteDatabase) {
        val builtins = listOf(
            EqPreset(name = "Flat", bandGains = List(10) { 0f }, bassGain = 0f, trebleGain = 0f, isBuiltin = true),
            EqPreset(name = "Bass Heavy", bandGains = listOf(8f, 7f, 5f, 2f, 0f, 0f, 0f, 1f, 2f, 3f), bassGain = 5f, trebleGain = 0f, isBuiltin = true),
            EqPreset(name = "Rock", bandGains = listOf(5f, 4f, 2f, -1f, -2f, 1f, 3f, 5f, 6f, 6f), bassGain = 3f, trebleGain = 3f, isBuiltin = true),
            EqPreset(name = "Pop", bandGains = listOf(-1f, 1f, 3f, 5f, 4f, 2f, -1f, 1f, 3f, 4f), bassGain = 2f, trebleGain = 2f, isBuiltin = true),
            EqPreset(name = "Jazz", bandGains = listOf(3f, 3f, 2f, 1f, 1f, 2f, 2f, 3f, 4f, 4f), bassGain = 2f, trebleGain = 2f, isBuiltin = true),
            EqPreset(name = "Vocal Boost", bandGains = listOf(-3f, -2f, 0f, 2f, 5f, 5f, 3f, 1f, 0f, -1f), bassGain = -1f, trebleGain = 1f, isBuiltin = true),
            EqPreset(name = "Electronic", bandGains = listOf(6f, 5f, 2f, 0f, -2f, 2f, 1f, 3f, 5f, 6f), bassGain = 5f, trebleGain = 4f, isBuiltin = true),
            EqPreset(name = "Classical", bandGains = listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 4f, 4f), bassGain = 1f, trebleGain = 2f, isBuiltin = true),
            EqPreset(name = "Dance", bandGains = listOf(6f, 6f, 3f, 0f, 1f, 2f, 4f, 5f, 4f, 0f), bassGain = 6f, trebleGain = 2f, isBuiltin = true),
            EqPreset(name = "Acoustic", bandGains = listOf(4f, 3f, 1f, 1f, 2f, 2f, 3f, 3f, 4f, 3f), bassGain = 2f, trebleGain = 3f, isBuiltin = true),
            EqPreset(name = "Deep Bass", bandGains = listOf(10f, 8f, 5f, 2f, 0f, 0f, 0f, 0f, 0f, 0f), bassGain = 7f, trebleGain = 0f, isBuiltin = true),
            EqPreset(name = "Techno", bandGains = listOf(5f, 4f, 0f, -2f, -1f, 1f, 4f, 5f, 5f, 3f), bassGain = 4f, trebleGain = 3f, isBuiltin = true)
        )

        for (p in builtins) {
            val cv = ContentValues().apply {
                put(COL_PRESET_NAME, p.name)
                put(COL_PRESET_GAINS, p.bandGains.joinToString(","))
                put(COL_PRESET_BASS, p.bassGain)
                put(COL_PRESET_TREBLE, p.trebleGain)
                put(COL_PRESET_PREAMP, p.preampGain)
                put(COL_PRESET_BUILTIN, 1)
            }
            db.insertWithOnConflict(TABLE_PRESETS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    // --- Statistics & Ratings ---

    fun incrementPlayCount(trackId: Long) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT INTO $TABLE_STATS ($COL_TRACK_ID, $COL_PLAY_COUNT, $COL_LAST_PLAYED, $COL_RATING)
            VALUES (?, 1, ?, 0)
            ON CONFLICT($COL_TRACK_ID) DO UPDATE SET
                $COL_PLAY_COUNT = $COL_PLAY_COUNT + 1,
                $COL_LAST_PLAYED = ?
            """.trimIndent(),
            arrayOf(trackId, now, now)
        )
        try {
            val eventValues = ContentValues().apply {
                put(COL_PLAY_EVENT_TRACK_ID, trackId)
                put(COL_PLAY_EVENT_PLAYED_AT, now)
            }
            db.insert(TABLE_PLAY_EVENTS, null, eventValues)
        } catch (_: Exception) {}
    }

    fun getPlayCountsSince(sinceMs: Long): Map<Long, Int> {
        val counts = LinkedHashMap<Long, Int>()
        val db = readableDatabase
        try {
            db.rawQuery(
                "SELECT $COL_PLAY_EVENT_TRACK_ID, COUNT(*) AS cnt FROM $TABLE_PLAY_EVENTS WHERE $COL_PLAY_EVENT_PLAYED_AT >= ? GROUP BY $COL_PLAY_EVENT_TRACK_ID ORDER BY cnt DESC",
                arrayOf(sinceMs.toString())
            ).use { cursor ->
                val trackIdCol = cursor.getColumnIndexOrThrow(COL_PLAY_EVENT_TRACK_ID)
                val cntCol = cursor.getColumnIndexOrThrow("cnt")
                while (cursor.moveToNext()) {
                    counts[cursor.getLong(trackIdCol)] = cursor.getInt(cntCol)
                }
            }
        } catch (_: Exception) {}
        return counts
    }

    fun setRating(trackId: Long, rating: Int) {
        val db = writableDatabase
        db.execSQL(
            """
            INSERT INTO $TABLE_STATS ($COL_TRACK_ID, $COL_PLAY_COUNT, $COL_LAST_PLAYED, $COL_RATING)
            VALUES (?, 0, 0, ?)
            ON CONFLICT($COL_TRACK_ID) DO UPDATE SET
                $COL_RATING = ?
            """.trimIndent(),
            arrayOf(trackId, rating, rating)
        )
    }

    fun getAllStats(): Map<Long, TrackStat> {
        val result = mutableMapOf<Long, TrackStat>()
        val db = readableDatabase
        db.rawQuery("SELECT $COL_TRACK_ID, $COL_PLAY_COUNT, $COL_LAST_PLAYED, $COL_RATING FROM $TABLE_STATS", null).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(COL_TRACK_ID)
            val pcCol = cursor.getColumnIndexOrThrow(COL_PLAY_COUNT)
            val lpCol = cursor.getColumnIndexOrThrow(COL_LAST_PLAYED)
            val ratCol = cursor.getColumnIndexOrThrow(COL_RATING)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val pc = cursor.getInt(pcCol)
                val lp = cursor.getLong(lpCol)
                val rat = cursor.getInt(ratCol)
                result[id] = TrackStat(id, pc, lp, rat)
            }
        }
        return result
    }

    // --- Playlists Management ---

    fun createPlaylist(name: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PL_NAME, name)
            put(COL_PL_CREATED, System.currentTimeMillis())
        }
        return db.insert(TABLE_PLAYLISTS, null, values)
    }

    fun getPlaylists(): List<Playlist> {
        val list = mutableListOf<Playlist>()
        val db = readableDatabase
        val query = """
            SELECT p.$COL_PL_ID, p.$COL_PL_NAME, p.$COL_PL_CREATED, p.$COL_PL_COVER_PATH, COUNT(i.$COL_ITEM_TRACK_ID) AS track_count
            FROM $TABLE_PLAYLISTS p
            LEFT JOIN $TABLE_PLAYLIST_ITEMS i ON p.$COL_PL_ID = i.$COL_ITEM_PL_ID
            GROUP BY p.$COL_PL_ID
            ORDER BY p.$COL_PL_NAME COLLATE NOCASE ASC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(COL_PL_ID)
            val nameCol = cursor.getColumnIndexOrThrow(COL_PL_NAME)
            val crCol = cursor.getColumnIndexOrThrow(COL_PL_CREATED)
            val coverCol = cursor.getColumnIndex(COL_PL_COVER_PATH)
            val cntCol = cursor.getColumnIndexOrThrow("track_count")

            while (cursor.moveToNext()) {
                val cover = if (coverCol != -1) cursor.getString(coverCol) ?: "" else ""
                list.add(
                    Playlist(
                        id = cursor.getLong(idCol),
                        name = cursor.getString(nameCol),
                        createdMs = cursor.getLong(crCol),
                        trackCount = cursor.getInt(cntCol),
                        coverPath = cover
                    )
                )
            }
        }
        return list
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PL_NAME, newName.trim())
        }
        db.update(TABLE_PLAYLISTS, values, "$COL_PL_ID = ?", arrayOf(playlistId.toString()))
    }

    fun updatePlaylistCover(playlistId: Long, coverPath: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PL_COVER_PATH, coverPath)
        }
        db.update(TABLE_PLAYLISTS, values, "$COL_PL_ID = ?", arrayOf(playlistId.toString()))
    }

    fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existingIds = mutableSetOf<Long>()
            var maxOrder = -1
            val query = "SELECT $COL_ITEM_TRACK_ID, $COL_ITEM_ORDER FROM $TABLE_PLAYLIST_ITEMS WHERE $COL_ITEM_PL_ID = ? ORDER BY $COL_ITEM_ORDER ASC"
            db.rawQuery(query, arrayOf(playlistId.toString())).use { cursor ->
                val trCol = cursor.getColumnIndexOrThrow(COL_ITEM_TRACK_ID)
                val ordCol = cursor.getColumnIndexOrThrow(COL_ITEM_ORDER)
                while (cursor.moveToNext()) {
                    existingIds.add(cursor.getLong(trCol))
                    val order = cursor.getInt(ordCol)
                    if (order > maxOrder) maxOrder = order
                }
            }
            var nextOrder = maxOrder + 1
            for (trackId in trackIds) {
                if (!existingIds.contains(trackId)) {
                    val values = ContentValues().apply {
                        put(COL_ITEM_PL_ID, playlistId)
                        put(COL_ITEM_TRACK_ID, trackId)
                        put(COL_ITEM_ORDER, nextOrder++)
                    }
                    db.insert(TABLE_PLAYLIST_ITEMS, null, values)
                    existingIds.add(trackId)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            try {
                db.endTransaction()
            } catch (_: Exception) {}
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        addTracksToPlaylist(playlistId, listOf(trackId))
    }

    fun getTracksForPlaylist(playlistId: Long): List<Long> {
        val list = mutableListOf<Long>()
        val db = readableDatabase
        val query = "SELECT $COL_ITEM_TRACK_ID FROM $TABLE_PLAYLIST_ITEMS WHERE $COL_ITEM_PL_ID = ? ORDER BY $COL_ITEM_ORDER ASC"
        db.rawQuery(query, arrayOf(playlistId.toString())).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(COL_ITEM_TRACK_ID)
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(idCol))
            }
        }
        return list
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        val db = writableDatabase
        db.delete(TABLE_PLAYLIST_ITEMS, "$COL_ITEM_PL_ID = ? AND $COL_ITEM_TRACK_ID = ?", arrayOf(playlistId.toString(), trackId.toString()))
    }

    fun deletePlaylist(playlistId: Long) {
        val db = writableDatabase
        db.delete(TABLE_PLAYLIST_ITEMS, "$COL_ITEM_PL_ID = ?", arrayOf(playlistId.toString()))
        db.delete(TABLE_PLAYLISTS, "$COL_PL_ID = ?", arrayOf(playlistId.toString()))
    }

    // --- Equalizer Presets ---

    fun savePreset(name: String, bandGains: List<Float>, bass: Float, treble: Float, preamp: Float): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_PRESET_NAME, name.trim())
            put(COL_PRESET_GAINS, bandGains.joinToString(","))
            put(COL_PRESET_BASS, bass)
            put(COL_PRESET_TREBLE, treble)
            put(COL_PRESET_PREAMP, preamp)
            put(COL_PRESET_BUILTIN, 0)
        }
        return db.insertWithOnConflict(TABLE_PRESETS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllPresets(): List<EqPreset> {
        val list = mutableListOf<EqPreset>()
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_PRESETS ORDER BY $COL_PRESET_BUILTIN DESC, $COL_PRESET_NAME ASC"
        db.rawQuery(query, null).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(COL_PRESET_ID)
            val nameCol = cursor.getColumnIndexOrThrow(COL_PRESET_NAME)
            val gainsCol = cursor.getColumnIndexOrThrow(COL_PRESET_GAINS)
            val bassCol = cursor.getColumnIndexOrThrow(COL_PRESET_BASS)
            val trebleCol = cursor.getColumnIndexOrThrow(COL_PRESET_TREBLE)
            val preampCol = cursor.getColumnIndexOrThrow(COL_PRESET_PREAMP)
            val builtinCol = cursor.getColumnIndexOrThrow(COL_PRESET_BUILTIN)

            while (cursor.moveToNext()) {
                val gainsStr = cursor.getString(gainsCol)
                val gains = gainsStr.split(",").mapNotNull { it.toFloatOrNull() }
                val validGains = if (gains.size == 10) gains else List(10) { 0f }
                list.add(
                    EqPreset(
                        id = cursor.getLong(idCol),
                        name = cursor.getString(nameCol),
                        bandGains = validGains,
                        bassGain = cursor.getFloat(bassCol),
                        trebleGain = cursor.getFloat(trebleCol),
                        preampGain = cursor.getFloat(preampCol),
                        isBuiltin = cursor.getInt(builtinCol) == 1
                    )
                )
            }
        }
        return list
    }

    fun deletePreset(presetId: Long): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_PRESETS, "$COL_PRESET_ID = ? AND $COL_PRESET_BUILTIN = 0", arrayOf(presetId.toString()))
        return rows > 0
    }

    // --- Cached Tracks (Incremental SAF Library Cache) ---

    fun getCachedTracks(): List<CachedTrack> {
        val list = mutableListOf<CachedTrack>()
        try {
            val db = readableDatabase
            val query = "SELECT * FROM $TABLE_CACHED_TRACKS"
            db.rawQuery(query, null).use { cursor ->
                val idCol = cursor.getColumnIndex(COL_CACHE_TRACK_ID)
                val uriCol = cursor.getColumnIndex(COL_CACHE_URI)
                val treeCol = cursor.getColumnIndex(COL_CACHE_TREE_URI)
                val lrcCol = cursor.getColumnIndex(COL_CACHE_LRC_URI)
                val titleCol = cursor.getColumnIndex(COL_CACHE_TITLE)
                val artistCol = cursor.getColumnIndex(COL_CACHE_ARTIST)
                val albumCol = cursor.getColumnIndex(COL_CACHE_ALBUM)
                val aaCol = cursor.getColumnIndex(COL_CACHE_ALBUM_ARTIST)
                val compCol = cursor.getColumnIndex(COL_CACHE_COMPOSER)
                val genreCol = cursor.getColumnIndex(COL_CACHE_GENRE)
                val yearCol = cursor.getColumnIndex(COL_CACHE_YEAR)
                val tnCol = cursor.getColumnIndex(COL_CACHE_TRACK_NUMBER)
                val dnCol = cursor.getColumnIndex(COL_CACHE_DISC_NUMBER)
                val durCol = cursor.getColumnIndex(COL_CACHE_DURATION_MS)
                val sizeCol = cursor.getColumnIndex(COL_CACHE_FILE_SIZE)
                val dmCol = cursor.getColumnIndex(COL_CACHE_DATE_MODIFIED)
                val daCol = cursor.getColumnIndex(COL_CACHE_DATE_ADDED)
                val folderCol = cursor.getColumnIndex(COL_CACHE_FOLDER)
                val fmtCol = cursor.getColumnIndex(COL_CACHE_FORMAT_TAG)
                val brCol = cursor.getColumnIndex(COL_CACHE_BITRATE)
                val srCol = cursor.getColumnIndex(COL_CACHE_SAMPLE_RATE)

                if (idCol == -1 || uriCol == -1 || treeCol == -1) return emptyList()

                while (cursor.moveToNext()) {
                    list.add(
                        CachedTrack(
                            trackId = cursor.getLong(idCol),
                            uri = cursor.getString(uriCol),
                            treeUri = cursor.getString(treeCol),
                            lrcUri = if (lrcCol != -1) cursor.getString(lrcCol) ?: "" else "",
                            title = if (titleCol != -1) cursor.getString(titleCol) ?: "Sem título" else "Sem título",
                            artist = if (artistCol != -1) cursor.getString(artistCol) ?: "Artista desconhecido" else "Artista desconhecido",
                            album = if (albumCol != -1) cursor.getString(albumCol) ?: "Álbum desconhecido" else "Álbum desconhecido",
                            albumArtist = if (aaCol != -1) cursor.getString(aaCol) ?: "" else "",
                            composer = if (compCol != -1) cursor.getString(compCol) ?: "" else "",
                            genre = if (genreCol != -1) cursor.getString(genreCol) ?: "" else "",
                            year = if (yearCol != -1) cursor.getInt(yearCol) else 0,
                            trackNumber = if (tnCol != -1) cursor.getInt(tnCol) else 0,
                            discNumber = if (dnCol != -1) cursor.getInt(dnCol) else 1,
                            durationMs = if (durCol != -1) cursor.getLong(durCol) else 0L,
                            fileSize = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L,
                            dateModifiedSec = if (dmCol != -1) cursor.getLong(dmCol) else 0L,
                            dateAddedSec = if (daCol != -1) cursor.getLong(daCol) else 0L,
                            folder = if (folderCol != -1) cursor.getString(folderCol) ?: "Músicas" else "Músicas",
                            formatTag = if (fmtCol != -1) cursor.getString(fmtCol) ?: "AUDIO" else "AUDIO",
                            bitrateKbps = if (brCol != -1) cursor.getInt(brCol) else 0,
                            sampleRateHz = if (srCol != -1) cursor.getInt(srCol) else 44100
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun upsertCachedTracks(tracks: List<CachedTrack>) {
        if (tracks.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                """
                INSERT OR REPLACE INTO $TABLE_CACHED_TRACKS (
                    $COL_CACHE_TRACK_ID, $COL_CACHE_URI, $COL_CACHE_TREE_URI, $COL_CACHE_LRC_URI,
                    $COL_CACHE_TITLE, $COL_CACHE_ARTIST, $COL_CACHE_ALBUM, $COL_CACHE_ALBUM_ARTIST,
                    $COL_CACHE_COMPOSER, $COL_CACHE_GENRE, $COL_CACHE_YEAR, $COL_CACHE_TRACK_NUMBER,
                    $COL_CACHE_DISC_NUMBER, $COL_CACHE_DURATION_MS, $COL_CACHE_FILE_SIZE,
                    $COL_CACHE_DATE_MODIFIED, $COL_CACHE_DATE_ADDED, $COL_CACHE_FOLDER,
                    $COL_CACHE_FORMAT_TAG, $COL_CACHE_BITRATE, $COL_CACHE_SAMPLE_RATE
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            for (t in tracks) {
                try {
                    stmt.clearBindings()
                    stmt.bindLong(1, t.trackId)
                    stmt.bindString(2, t.uri)
                    stmt.bindString(3, t.treeUri)
                    stmt.bindString(4, t.lrcUri)
                    stmt.bindString(5, t.title)
                    stmt.bindString(6, t.artist)
                    stmt.bindString(7, t.album)
                    stmt.bindString(8, t.albumArtist)
                    stmt.bindString(9, t.composer)
                    stmt.bindString(10, t.genre)
                    stmt.bindLong(11, t.year.toLong())
                    stmt.bindLong(12, t.trackNumber.toLong())
                    stmt.bindLong(13, t.discNumber.toLong())
                    stmt.bindLong(14, t.durationMs)
                    stmt.bindLong(15, t.fileSize)
                    stmt.bindLong(16, t.dateModifiedSec)
                    stmt.bindLong(17, t.dateAddedSec)
                    stmt.bindString(18, t.folder)
                    stmt.bindString(19, t.formatTag)
                    stmt.bindLong(20, t.bitrateKbps.toLong())
                    stmt.bindLong(21, t.sampleRateHz.toLong())
                    stmt.executeInsert()
                } catch (_: Exception) {}
            }
            db.setTransactionSuccessful()
        } catch (_: Exception) {
        } finally {
            try {
                db.endTransaction()
            } catch (_: Exception) {}
        }
    }

    fun deleteCachedTracksNotIn(validUris: Set<String>) {
        val db = writableDatabase
        if (validUris.isEmpty()) {
            try {
                db.delete(TABLE_CACHED_TRACKS, null, null)
            } catch (_: Exception) {}
            return
        }
        val cachedUris = mutableListOf<String>()
        try {
            db.rawQuery("SELECT $COL_CACHE_URI FROM $TABLE_CACHED_TRACKS", null).use { cursor ->
                val uriCol = cursor.getColumnIndex(COL_CACHE_URI)
                if (uriCol != -1) {
                    while (cursor.moveToNext()) {
                        cursor.getString(uriCol)?.let { cachedUris.add(it) }
                    }
                }
            }
            val toDelete = cachedUris.filter { it !in validUris }
            if (toDelete.isNotEmpty()) {
                db.beginTransaction()
                try {
                    toDelete.chunked(500).forEach { chunk ->
                        val placeholders = chunk.joinToString(",") { "?" }
                        db.delete(TABLE_CACHED_TRACKS, "$COL_CACHE_URI IN ($placeholders)", chunk.toTypedArray())
                    }
                    db.setTransactionSuccessful()
                } finally {
                    try {
                        db.endTransaction()
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    fun clearCachedTracks() {
        try {
            writableDatabase.delete(TABLE_CACHED_TRACKS, null, null)
        } catch (_: Exception) {}
    }

    // --- Playback History Management ---

    fun recordPlaybackHistory(trackId: Long) {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        var isDuplicate = false
        try {
            db.rawQuery(
                "SELECT $COL_HISTORY_TRACK_ID, $COL_HISTORY_PLAYED_AT FROM $TABLE_PLAYBACK_HISTORY ORDER BY $COL_HISTORY_ID DESC LIMIT 1",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val lastTrackId = cursor.getLong(0)
                    val lastPlayedAt = cursor.getLong(1)
                    if (lastTrackId == trackId && (now - lastPlayedAt) < 5000L) {
                        isDuplicate = true
                    }
                }
            }
        } catch (_: Exception) {}

        if (!isDuplicate) {
            val values = ContentValues().apply {
                put(COL_HISTORY_TRACK_ID, trackId)
                put(COL_HISTORY_PLAYED_AT, now)
            }
            db.insert(TABLE_PLAYBACK_HISTORY, null, values)
            try {
                db.execSQL(
                    """
                    DELETE FROM $TABLE_PLAYBACK_HISTORY
                    WHERE $COL_HISTORY_ID NOT IN (
                        SELECT $COL_HISTORY_ID FROM $TABLE_PLAYBACK_HISTORY
                        ORDER BY $COL_HISTORY_ID DESC
                        LIMIT 200
                    )
                    """.trimIndent()
                )
            } catch (_: Exception) {}
        }
    }

    fun getPlaybackHistory(limit: Int = 100): List<Pair<Long, Long>> {
        val list = mutableListOf<Pair<Long, Long>>()
        val db = readableDatabase
        try {
            db.rawQuery(
                "SELECT $COL_HISTORY_ID, $COL_HISTORY_TRACK_ID FROM $TABLE_PLAYBACK_HISTORY ORDER BY $COL_HISTORY_ID DESC LIMIT ?",
                arrayOf(limit.coerceAtLeast(1).toString())
            ).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(COL_HISTORY_ID)
                val trackIdCol = cursor.getColumnIndexOrThrow(COL_HISTORY_TRACK_ID)
                while (cursor.moveToNext()) {
                    list.add(cursor.getLong(idCol) to cursor.getLong(trackIdCol))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun clearPlaybackHistory() {
        try {
            writableDatabase.delete(TABLE_PLAYBACK_HISTORY, null, null)
        } catch (_: Exception) {}
    }

    fun setArtistMedia(artistName: String, path: String, isGif: Boolean) {
        val key = artistName.trim().lowercase()
        if (key.isEmpty()) return
        try {
            val cv = ContentValues().apply {
                put(COL_ARTIST_KEY, key)
                put(COL_ARTIST_MEDIA_PATH, path)
                put(COL_ARTIST_IS_GIF, if (isGif) 1 else 0)
            }
            writableDatabase.insertWithOnConflict(TABLE_ARTIST_MEDIA, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {}
    }

    fun getArtistMedia(artistName: String): Pair<String, Boolean>? {
        val key = artistName.trim().lowercase()
        if (key.isEmpty()) return null
        try {
            val cursor = readableDatabase.query(
                TABLE_ARTIST_MEDIA,
                arrayOf(COL_ARTIST_MEDIA_PATH, COL_ARTIST_IS_GIF),
                "$COL_ARTIST_KEY = ?",
                arrayOf(key),
                null, null, null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    val path = it.getString(0) ?: ""
                    val isGif = it.getInt(1) == 1
                    return Pair(path, isGif)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getAllArtistMedia(): Map<String, Pair<String, Boolean>> {
        val result = mutableMapOf<String, Pair<String, Boolean>>()
        try {
            val cursor = readableDatabase.query(
                TABLE_ARTIST_MEDIA,
                arrayOf(COL_ARTIST_KEY, COL_ARTIST_MEDIA_PATH, COL_ARTIST_IS_GIF),
                null, null, null, null, null
            )
            cursor.use {
                val keyCol = it.getColumnIndexOrThrow(COL_ARTIST_KEY)
                val pathCol = it.getColumnIndexOrThrow(COL_ARTIST_MEDIA_PATH)
                val gifCol = it.getColumnIndexOrThrow(COL_ARTIST_IS_GIF)
                while (it.moveToNext()) {
                    val key = it.getString(keyCol) ?: continue
                    val path = it.getString(pathCol) ?: ""
                    val isGif = it.getInt(gifCol) == 1
                    result[key] = Pair(path, isGif)
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun removeArtistMedia(artistName: String) {
        val key = artistName.trim().lowercase()
        if (key.isEmpty()) return
        try {
            writableDatabase.delete(TABLE_ARTIST_MEDIA, "$COL_ARTIST_KEY = ?", arrayOf(key))
        } catch (_: Exception) {}
    }

    // --- Metadados Customizados de Álbum (Capa / GIF + Anotações) ---

    fun setAlbumMedia(albumName: String, path: String, isGif: Boolean) {
        val key = albumName.trim().lowercase()
        if (key.isEmpty()) return
        try {
            writableDatabase.execSQL(
                """
                INSERT INTO $TABLE_ALBUM_META ($COL_ALBUM_KEY, $COL_ALBUM_MEDIA_PATH, $COL_ALBUM_IS_GIF, $COL_ALBUM_NOTES)
                VALUES (?, ?, ?, '')
                ON CONFLICT($COL_ALBUM_KEY) DO UPDATE SET
                    $COL_ALBUM_MEDIA_PATH = excluded.$COL_ALBUM_MEDIA_PATH,
                    $COL_ALBUM_IS_GIF = excluded.$COL_ALBUM_IS_GIF
                """.trimIndent(),
                arrayOf(key, path, if (isGif) 1 else 0)
            )
        } catch (_: Exception) {}
    }

    fun removeAlbumMedia(albumName: String) {
        val key = albumName.trim().lowercase()
        if (key.isEmpty()) return
        try {
            val cv = ContentValues().apply {
                put(COL_ALBUM_MEDIA_PATH, "")
                put(COL_ALBUM_IS_GIF, 0)
            }
            writableDatabase.update(TABLE_ALBUM_META, cv, "$COL_ALBUM_KEY = ?", arrayOf(key))
        } catch (_: Exception) {}
    }

    fun setAlbumNotes(albumName: String, notes: String) {
        val key = albumName.trim().lowercase()
        if (key.isEmpty()) return
        try {
            writableDatabase.execSQL(
                """
                INSERT INTO $TABLE_ALBUM_META ($COL_ALBUM_KEY, $COL_ALBUM_MEDIA_PATH, $COL_ALBUM_IS_GIF, $COL_ALBUM_NOTES)
                VALUES (?, '', 0, ?)
                ON CONFLICT($COL_ALBUM_KEY) DO UPDATE SET
                    $COL_ALBUM_NOTES = excluded.$COL_ALBUM_NOTES
                """.trimIndent(),
                arrayOf(key, notes)
            )
        } catch (_: Exception) {}
    }

    fun getAlbumMeta(albumName: String): AlbumMeta? {
        val key = albumName.trim().lowercase()
        if (key.isEmpty()) return null
        try {
            val cursor = readableDatabase.query(
                TABLE_ALBUM_META,
                arrayOf(COL_ALBUM_KEY, COL_ALBUM_MEDIA_PATH, COL_ALBUM_IS_GIF, COL_ALBUM_NOTES),
                "$COL_ALBUM_KEY = ?",
                arrayOf(key),
                null, null, null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    return AlbumMeta(
                        albumKey = it.getString(0) ?: key,
                        mediaPath = it.getString(1) ?: "",
                        isGif = it.getInt(2) == 1,
                        notes = it.getString(3) ?: ""
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getAllAlbumMeta(): Map<String, AlbumMeta> {
        val result = mutableMapOf<String, AlbumMeta>()
        try {
            val cursor = readableDatabase.query(
                TABLE_ALBUM_META,
                arrayOf(COL_ALBUM_KEY, COL_ALBUM_MEDIA_PATH, COL_ALBUM_IS_GIF, COL_ALBUM_NOTES),
                null, null, null, null, null
            )
            cursor.use {
                val keyCol = it.getColumnIndexOrThrow(COL_ALBUM_KEY)
                val pathCol = it.getColumnIndexOrThrow(COL_ALBUM_MEDIA_PATH)
                val gifCol = it.getColumnIndexOrThrow(COL_ALBUM_IS_GIF)
                val notesCol = it.getColumnIndexOrThrow(COL_ALBUM_NOTES)
                while (it.moveToNext()) {
                    val key = it.getString(keyCol) ?: continue
                    result[key] = AlbumMeta(
                        albumKey = key,
                        mediaPath = it.getString(pathCol) ?: "",
                        isGif = it.getInt(gifCol) == 1,
                        notes = it.getString(notesCol) ?: ""
                    )
                }
            }
        } catch (_: Exception) {}
        return result
    }

    // --- Coleções de Álbuns ---

    fun createAlbumCollection(name: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_AC_NAME, name.trim())
            put(COL_AC_CREATED, System.currentTimeMillis())
        }
        return db.insert(TABLE_ALBUM_COLLECTIONS, null, values)
    }

    fun getAlbumCollections(): List<AlbumCollectionRow> {
        val list = mutableListOf<AlbumCollectionRow>()
        val db = readableDatabase
        val query = """
            SELECT c.$COL_AC_ID, c.$COL_AC_NAME, c.$COL_AC_CREATED, COUNT(i.$COL_ACI_ALBUM_NAME) AS album_count
            FROM $TABLE_ALBUM_COLLECTIONS c
            LEFT JOIN $TABLE_ALBUM_COLLECTION_ITEMS i ON c.$COL_AC_ID = i.$COL_ACI_COLLECTION_ID
            GROUP BY c.$COL_AC_ID
            ORDER BY c.$COL_AC_NAME COLLATE NOCASE ASC
        """.trimIndent()

        try {
            db.rawQuery(query, null).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(COL_AC_ID)
                val nameCol = cursor.getColumnIndexOrThrow(COL_AC_NAME)
                val crCol = cursor.getColumnIndexOrThrow(COL_AC_CREATED)
                val cntCol = cursor.getColumnIndexOrThrow("album_count")
                while (cursor.moveToNext()) {
                    list.add(
                        AlbumCollectionRow(
                            id = cursor.getLong(idCol),
                            name = cursor.getString(nameCol),
                            createdMs = cursor.getLong(crCol),
                            albumCount = cursor.getInt(cntCol)
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun renameAlbumCollection(collectionId: Long, newName: String) {
        val values = ContentValues().apply {
            put(COL_AC_NAME, newName.trim())
        }
        try {
            writableDatabase.update(TABLE_ALBUM_COLLECTIONS, values, "$COL_AC_ID = ?", arrayOf(collectionId.toString()))
        } catch (_: Exception) {}
    }

    fun deleteAlbumCollection(collectionId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_ALBUM_COLLECTION_ITEMS, "$COL_ACI_COLLECTION_ID = ?", arrayOf(collectionId.toString()))
            db.delete(TABLE_ALBUM_COLLECTIONS, "$COL_AC_ID = ?", arrayOf(collectionId.toString()))
            db.setTransactionSuccessful()
        } finally {
            try {
                db.endTransaction()
            } catch (_: Exception) {}
        }
    }

    fun getAlbumsForCollection(collectionId: Long): List<String> {
        val list = mutableListOf<String>()
        val query = "SELECT $COL_ACI_ALBUM_NAME FROM $TABLE_ALBUM_COLLECTION_ITEMS WHERE $COL_ACI_COLLECTION_ID = ? ORDER BY $COL_ACI_SORT_ORDER ASC"
        try {
            readableDatabase.rawQuery(query, arrayOf(collectionId.toString())).use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(COL_ACI_ALBUM_NAME)
                while (cursor.moveToNext()) {
                    val albumName = cursor.getString(nameCol)
                    if (!albumName.isNullOrBlank()) {
                        list.add(albumName)
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun addAlbumsToCollection(collectionId: Long, albumNames: List<String>) {
        if (albumNames.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existingLower = mutableSetOf<String>()
            var maxOrder = -1
            val query = "SELECT $COL_ACI_ALBUM_NAME, $COL_ACI_SORT_ORDER FROM $TABLE_ALBUM_COLLECTION_ITEMS WHERE $COL_ACI_COLLECTION_ID = ? ORDER BY $COL_ACI_SORT_ORDER ASC"
            db.rawQuery(query, arrayOf(collectionId.toString())).use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(COL_ACI_ALBUM_NAME)
                val ordCol = cursor.getColumnIndexOrThrow(COL_ACI_SORT_ORDER)
                while (cursor.moveToNext()) {
                    val existingName = cursor.getString(nameCol)?.trim()?.lowercase()
                    if (!existingName.isNullOrEmpty()) existingLower.add(existingName)
                    val order = cursor.getInt(ordCol)
                    if (order > maxOrder) maxOrder = order
                }
            }
            var nextOrder = maxOrder + 1
            for (rawName in albumNames) {
                val trimmed = rawName.trim()
                val key = trimmed.lowercase()
                if (trimmed.isNotEmpty() && !existingLower.contains(key)) {
                    val values = ContentValues().apply {
                        put(COL_ACI_COLLECTION_ID, collectionId)
                        put(COL_ACI_ALBUM_NAME, trimmed)
                        put(COL_ACI_SORT_ORDER, nextOrder++)
                    }
                    db.insertWithOnConflict(TABLE_ALBUM_COLLECTION_ITEMS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
                    existingLower.add(key)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            try {
                db.endTransaction()
            } catch (_: Exception) {}
        }
    }

    fun removeAlbumFromCollection(collectionId: Long, albumName: String) {
        try {
            writableDatabase.delete(
                TABLE_ALBUM_COLLECTION_ITEMS,
                "$COL_ACI_COLLECTION_ID = ? AND LOWER($COL_ACI_ALBUM_NAME) = LOWER(?)",
                arrayOf(collectionId.toString(), albumName.trim())
            )
        } catch (_: Exception) {}
    }

    fun reorderCollectionAlbums(collectionId: Long, orderedAlbumNames: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_ALBUM_COLLECTION_ITEMS, "$COL_ACI_COLLECTION_ID = ?", arrayOf(collectionId.toString()))
            val seen = mutableSetOf<String>()
            var order = 0
            for (rawName in orderedAlbumNames) {
                val trimmed = rawName.trim()
                val key = trimmed.lowercase()
                if (trimmed.isNotEmpty() && seen.add(key)) {
                    val values = ContentValues().apply {
                        put(COL_ACI_COLLECTION_ID, collectionId)
                        put(COL_ACI_ALBUM_NAME, trimmed)
                        put(COL_ACI_SORT_ORDER, order++)
                    }
                    db.insert(TABLE_ALBUM_COLLECTION_ITEMS, null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            try {
                db.endTransaction()
            } catch (_: Exception) {}
        }
    }

    companion object {
        const val DATABASE_NAME = "poweramp_library.db"
        const val DATABASE_VERSION = 10

        const val TABLE_ALBUM_COLLECTIONS = "album_collections"
        const val COL_AC_ID = "id"
        const val COL_AC_NAME = "name"
        const val COL_AC_CREATED = "created_ms"

        const val TABLE_ALBUM_COLLECTION_ITEMS = "album_collection_items"
        const val COL_ACI_COLLECTION_ID = "collection_id"
        const val COL_ACI_ALBUM_NAME = "album_name"
        const val COL_ACI_SORT_ORDER = "sort_order"

        const val TABLE_ALBUM_META = "album_meta"
        const val COL_ALBUM_KEY = "album_key"
        const val COL_ALBUM_MEDIA_PATH = "media_path"
        const val COL_ALBUM_IS_GIF = "is_gif"
        const val COL_ALBUM_NOTES = "notes"

        const val TABLE_PLAY_EVENTS = "play_events"
        const val COL_PLAY_EVENT_ID = "id"
        const val COL_PLAY_EVENT_TRACK_ID = "track_id"
        const val COL_PLAY_EVENT_PLAYED_AT = "played_at_ms"

        const val TABLE_ARTIST_MEDIA = "artist_media"
        const val COL_ARTIST_KEY = "artist_key"
        const val COL_ARTIST_MEDIA_PATH = "media_path"
        const val COL_ARTIST_IS_GIF = "is_gif"

        const val TABLE_PLAYBACK_HISTORY = "playback_history"
        const val COL_HISTORY_ID = "id"
        const val COL_HISTORY_TRACK_ID = "track_id"
        const val COL_HISTORY_PLAYED_AT = "played_at_ms"

        const val TABLE_STATS = "track_stats"
        const val COL_TRACK_ID = "track_id"
        const val COL_PLAY_COUNT = "play_count"
        const val COL_LAST_PLAYED = "last_played_ms"
        const val COL_RATING = "rating"

        const val TABLE_PLAYLISTS = "playlists"
        const val COL_PL_ID = "id"
        const val COL_PL_NAME = "name"
        const val COL_PL_CREATED = "created_ms"
        const val COL_PL_COVER_PATH = "cover_path"

        const val TABLE_PLAYLIST_ITEMS = "playlist_items"
        const val COL_ITEM_PL_ID = "playlist_id"
        const val COL_ITEM_TRACK_ID = "track_id"
        const val COL_ITEM_ORDER = "sort_order"

        const val TABLE_PRESETS = "eq_presets"
        const val COL_PRESET_ID = "id"
        const val COL_PRESET_NAME = "name"
        const val COL_PRESET_GAINS = "gains"
        const val COL_PRESET_BASS = "bass"
        const val COL_PRESET_TREBLE = "treble"
        const val COL_PRESET_PREAMP = "preamp"
        const val COL_PRESET_BUILTIN = "is_builtin"

        const val TABLE_CACHED_TRACKS = "cached_tracks"
        const val COL_CACHE_TRACK_ID = "track_id"
        const val COL_CACHE_URI = "uri"
        const val COL_CACHE_TREE_URI = "tree_uri"
        const val COL_CACHE_LRC_URI = "lrc_uri"
        const val COL_CACHE_TITLE = "title"
        const val COL_CACHE_ARTIST = "artist"
        const val COL_CACHE_ALBUM = "album"
        const val COL_CACHE_ALBUM_ARTIST = "album_artist"
        const val COL_CACHE_COMPOSER = "composer"
        const val COL_CACHE_GENRE = "genre"
        const val COL_CACHE_YEAR = "year"
        const val COL_CACHE_TRACK_NUMBER = "track_number"
        const val COL_CACHE_DISC_NUMBER = "disc_number"
        const val COL_CACHE_DURATION_MS = "duration_ms"
        const val COL_CACHE_FILE_SIZE = "file_size"
        const val COL_CACHE_DATE_MODIFIED = "date_modified_sec"
        const val COL_CACHE_DATE_ADDED = "date_added_sec"
        const val COL_CACHE_FOLDER = "folder"
        const val COL_CACHE_FORMAT_TAG = "format_tag"
        const val COL_CACHE_BITRATE = "bitrate_kbps"
        const val COL_CACHE_SAMPLE_RATE = "sample_rate_hz"
    }
}
