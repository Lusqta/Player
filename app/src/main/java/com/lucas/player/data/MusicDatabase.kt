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
        val trackCount: Int = 0
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
                $COL_PL_CREATED INTEGER NOT NULL
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createPresetsTable(db)
            seedBuiltinPresets(db)
        }
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
        db.execSQL(
            """
            INSERT INTO $TABLE_STATS ($COL_TRACK_ID, $COL_PLAY_COUNT, $COL_LAST_PLAYED, $COL_RATING)
            VALUES (?, 1, ?, 0)
            ON CONFLICT($COL_TRACK_ID) DO UPDATE SET
                $COL_PLAY_COUNT = $COL_PLAY_COUNT + 1,
                $COL_LAST_PLAYED = ?
            """.trimIndent(),
            arrayOf(trackId, System.currentTimeMillis(), System.currentTimeMillis())
        )
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
            SELECT p.$COL_PL_ID, p.$COL_PL_NAME, p.$COL_PL_CREATED, COUNT(i.$COL_ITEM_TRACK_ID) AS track_count
            FROM $TABLE_PLAYLISTS p
            LEFT JOIN $TABLE_PLAYLIST_ITEMS i ON p.$COL_PL_ID = i.$COL_ITEM_PL_ID
            GROUP BY p.$COL_PL_ID
            ORDER BY p.$COL_PL_NAME COLLATE NOCASE ASC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(COL_PL_ID)
            val nameCol = cursor.getColumnIndexOrThrow(COL_PL_NAME)
            val crCol = cursor.getColumnIndexOrThrow(COL_PL_CREATED)
            val cntCol = cursor.getColumnIndexOrThrow("track_count")

            while (cursor.moveToNext()) {
                list.add(
                    Playlist(
                        id = cursor.getLong(idCol),
                        name = cursor.getString(nameCol),
                        createdMs = cursor.getLong(crCol),
                        trackCount = cursor.getInt(cntCol)
                    )
                )
            }
        }
        return list
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        val db = writableDatabase
        val countQuery = "SELECT COUNT(*) FROM $TABLE_PLAYLIST_ITEMS WHERE $COL_ITEM_PL_ID = ?"
        var nextOrder = 0
        db.rawQuery(countQuery, arrayOf(playlistId.toString())).use {
            if (it.moveToNext()) {
                nextOrder = it.getInt(0)
            }
        }

        val values = ContentValues().apply {
            put(COL_ITEM_PL_ID, playlistId)
            put(COL_ITEM_TRACK_ID, trackId)
            put(COL_ITEM_ORDER, nextOrder)
        }
        db.insertWithOnConflict(TABLE_PLAYLIST_ITEMS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
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

    companion object {
        const val DATABASE_NAME = "poweramp_library.db"
        const val DATABASE_VERSION = 2

        const val TABLE_STATS = "track_stats"
        const val COL_TRACK_ID = "track_id"
        const val COL_PLAY_COUNT = "play_count"
        const val COL_LAST_PLAYED = "last_played_ms"
        const val COL_RATING = "rating"

        const val TABLE_PLAYLISTS = "playlists"
        const val COL_PL_ID = "id"
        const val COL_PL_NAME = "name"
        const val COL_PL_CREATED = "created_ms"

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
    }
}
