package com.seunome.player

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongsFlow(): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongs(): List<SongEntity>
    

    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    
    @Query("DELETE FROM songs WHERE realPath = :path")
    suspend fun deleteSongByPath(path: String)
    
}
