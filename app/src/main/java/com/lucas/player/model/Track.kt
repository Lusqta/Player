package com.lucas.player.model

import android.net.Uri

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val path: String,
    val folder: String,
    val albumArtist: String = "",
    val composer: String = "",
    val genre: String = "",
    val year: Int = 0,
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val fileSize: Long = 0L,
    val dateAddedSec: Long = 0L,
    val dateModifiedSec: Long = 0L,
    var playCount: Int = 0,
    var lastPlayedTimestamp: Long = 0L,
    var rating: Int = 0,
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 44100,
    val formatTag: String = "",
    val replayGainTrackGain: Float = 0.0f,
    val replayGainTrackPeak: Float = 1.0f,
    val replayGainAlbumGain: Float = 0.0f,
    val replayGainAlbumPeak: Float = 1.0f
)

data class Album(
    val name: String,
    val artist: String,
    val trackCount: Int,
    val year: Int = 0
)

data class Artist(
    val name: String,
    val trackCount: Int
)

data class Folder(
    val name: String,
    val path: String,
    val trackCount: Int
)

data class Genre(
    val name: String,
    val trackCount: Int
)

data class Composer(
    val name: String,
    val trackCount: Int
)

data class YearGroup(
    val year: Int,
    val trackCount: Int
)

data class UserPlaylist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val createdMs: Long
)

enum class LibraryCategory(val title: String, val description: String) {
    ALL_SONGS("Todas as Músicas", "Todas as faixas da biblioteca"),
    FOLDERS("Pastas", "Diretórios de áudio"),
    ALBUMS("Álbuns", "Coleções de álbuns"),
    ARTISTS("Artistas", "Todos os artistas"),
    ALBUM_ARTISTS("Artistas do Álbum", "Artistas principais"),
    GENRES("Gêneros", "Estilos musicais"),
    YEARS("Anos", "Faixas por ano de lançamento"),
    COMPOSERS("Compositores", "Autores e compositores"),
    PLAYLISTS("Listas de Reprodução", "Playlists do usuário"),
    QUEUE("Fila de Reprodução", "Faixas prioritárias na fila"),
    RECENTLY_ADDED("Adicionadas Recentemente", "Últimas músicas adicionadas"),
    RECENTLY_PLAYED("Tocadas Recentemente", "Histórico de reprodução"),
    MOST_PLAYED("Mais Tocadas", "Faixas com maior número de reproduções"),
    TOP_RATED("Mais Votadas", "Faixas favoritas")
}

enum class SortMode(val displayName: String) {
    TITLE_ASC("Título (A-Z)"),
    TITLE_DESC("Título (Z-A)"),
    ARTIST_ASC("Artista (A-Z)"),
    ALBUM_ASC("Álbum (A-Z)"),
    YEAR_DESC("Ano (Mais recente)"),
    YEAR_ASC("Ano (Mais antigo)"),
    TRACK_NUMBER("Número da Faixa"),
    DURATION_DESC("Duração (Mais longa)"),
    DURATION_ASC("Duração (Mais curta)"),
    DATE_ADDED_DESC("Data de Adição (Recentes)"),
    PLAY_COUNT_DESC("Mais Tocadas"),
    RATING_DESC("Mais Votadas")
}
