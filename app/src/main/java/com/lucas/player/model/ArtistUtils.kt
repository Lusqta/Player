package com.lucas.player.model

fun splitArtists(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return raw.split(';')
        .map { it.trim() }
        .filter { candidate ->
            candidate.isNotEmpty() &&
            !candidate.equals("<unknown>", ignoreCase = true) &&
            !candidate.equals("unknown", ignoreCase = true) &&
            !candidate.equals("desconhecido", ignoreCase = true)
        }
}

fun formatArtistsDisplay(raw: String, fallback: String = ""): String {
    val artists = splitArtists(raw)
    return if (artists.isEmpty()) fallback else artists.joinToString(", ")
}

// Mantido para compatibilidade com chamadas existentes
fun formatDisplayArtist(raw: String, fallback: String = ""): String = formatArtistsDisplay(raw, fallback)

fun isJunkAudio(track: Track, minSecFloor: Int = 30): Boolean {
    val path = track.path.lowercase()
    val folder = track.folder.lowercase()
    if (path.contains("whatsapp") || folder.contains("whatsapp")) return true
    val floorMs = if (minSecFloor > 0) minSecFloor * 1000L else 30_000L
    return track.durationMs in 1L until floorMs
}

fun isValidHighlightName(raw: String): Boolean {
    val t = raw.trim()
    return t.isNotEmpty() &&
        !t.equals("<unknown>", ignoreCase = true) &&
        !t.equals("unknown", ignoreCase = true) &&
        !t.equals("desconhecido", ignoreCase = true) &&
        !t.equals("sem título", ignoreCase = true)
}

fun primaryArtistsOf(track: Track): List<String> {
    val albumArtists = splitArtists(track.albumArtist)
    if (albumArtists.isNotEmpty()) return albumArtists
    return splitArtists(track.artist).take(1)
}

fun isPrimaryArtistTrack(track: Track, artistName: String): Boolean {
    val clean = artistName.trim()
    if (clean.isEmpty()) return false
    return primaryArtistsOf(track).any { it.equals(clean, ignoreCase = true) }
}

fun isFeatArtistTrack(track: Track, artistName: String): Boolean {
    val clean = artistName.trim()
    if (clean.isEmpty()) return false
    return splitArtists(track.artist).any { it.equals(clean, ignoreCase = true) } &&
        !isPrimaryArtistTrack(track, clean)
}

private val EP_SINGLE_REGEX = Regex("\\b(ep|single)\\b", RegexOption.IGNORE_CASE)

fun classifyIsAlbum(albumName: String, trackCount: Int, maxTrackNumber: Int): Boolean {
    if (EP_SINGLE_REGEX.containsMatchIn(albumName)) return false
    return trackCount >= 7 || maxTrackNumber >= 7
}

data class ArtistRelease(
    val albumName: String,
    val year: Int,
    val latestDateAddedSec: Long,
    val trackCount: Int,
    val maxTrackNumber: Int,
    val totalPlays: Int,
    val coverTrack: Track,
    val isAlbum: Boolean
)
