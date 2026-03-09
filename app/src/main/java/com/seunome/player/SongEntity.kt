package com.seunome.player

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val id: Long, // Usaremos o hashCode do realPath ou um Hash consistente para evitar duplicações
    
    val uriStr: String, // O "content://..." original ou realPath se possível tocar direto
    val title: String,
    val artist: String,
    val duration: Long,
    val realPath: String // Usado para o FileObserver e verificação de integridade
) {
    fun toSong(): Song {
        return Song(
            id = id,
            uri = realPath, // C++ precisa do absolute path nativo (não podemos mandar o SAF content URI)
            title = title,
            artist = artist,
            duration = duration
        )
    }
}
