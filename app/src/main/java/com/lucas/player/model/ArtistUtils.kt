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
