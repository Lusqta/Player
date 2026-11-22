package com.lucas.player.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Clean-room ReplayGain parser replicating Poweramp's RG engine.
 * Scans ID3v2, Vorbis Comments, and APE tags for track and album loudness normalization data.
 */
object ReplayGainReader {

    data class ReplayGainInfo(
        val trackGainDb: Float = 0.0f,
        val trackPeak: Float = 1.0f,
        val albumGainDb: Float = 0.0f,
        val albumPeak: Float = 1.0f,
        val hasData: Boolean = false
    )

    private val TRACK_GAIN_REGEX = Pattern.compile("replaygain_track_gain[\\x00=\\s]+([+-]?\\d+(?:\\.\\d+)?)\\s*(?:db)?", Pattern.CASE_INSENSITIVE)
    private val TRACK_PEAK_REGEX = Pattern.compile("replaygain_track_peak[\\x00=\\s]+([+-]?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE)
    private val ALBUM_GAIN_REGEX = Pattern.compile("replaygain_album_gain[\\x00=\\s]+([+-]?\\d+(?:\\.\\d+)?)\\s*(?:db)?", Pattern.CASE_INSENSITIVE)
    private val ALBUM_PEAK_REGEX = Pattern.compile("replaygain_album_peak[\\x00=\\s]+([+-]?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE)

    fun readFromUri(context: Context, uri: Uri): ReplayGainInfo {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val maxBytes = 131072
                val buffer = ByteArray(maxBytes)
                var totalRead = 0
                while (totalRead < maxBytes) {
                    val read = stream.read(buffer, totalRead, maxBytes - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                if (totalRead < 128) return ReplayGainInfo()
                val headerText = String(buffer, 0, totalRead, StandardCharsets.ISO_8859_1)
                return parseFromText(headerText)
            }
        } catch (_: Exception) {}
        return ReplayGainInfo()
    }

    fun readFromFile(path: String): ReplayGainInfo {
        val file = File(path)
        if (!file.exists() || !file.canRead() || file.length() < 128) {
            return ReplayGainInfo()
        }

        try {
            RandomAccessFile(file, "r").use { raf ->
                // Read header tag region (up to 128 KB)
                val readLength = file.length().coerceAtMost(131072L).toInt()
                val buffer = ByteArray(readLength)
                raf.readFully(buffer)

                // Convert bytes with ISO-8859-1 to preserve binary byte values 0-255 without decoding errors
                val headerText = String(buffer, StandardCharsets.ISO_8859_1)
                return parseFromText(headerText)
            }
        } catch (_: Exception) {
            return ReplayGainInfo()
        }
    }

    private fun parseFromText(headerText: String): ReplayGainInfo {
        var trackGain = 0.0f
        var trackPeak = 1.0f
        var albumGain = 0.0f
        var albumPeak = 1.0f
        var found = false

        val tgMatcher = TRACK_GAIN_REGEX.matcher(headerText)
        if (tgMatcher.find()) {
            trackGain = tgMatcher.group(1)?.toFloatOrNull() ?: 0.0f
            found = true
        }

        val tpMatcher = TRACK_PEAK_REGEX.matcher(headerText)
        if (tpMatcher.find()) {
            trackPeak = tpMatcher.group(1)?.toFloatOrNull() ?: 1.0f
            found = true
        }

        val agMatcher = ALBUM_GAIN_REGEX.matcher(headerText)
        if (agMatcher.find()) {
            albumGain = agMatcher.group(1)?.toFloatOrNull() ?: 0.0f
            found = true
        }

        val apMatcher = ALBUM_PEAK_REGEX.matcher(headerText)
        if (apMatcher.find()) {
            albumPeak = apMatcher.group(1)?.toFloatOrNull() ?: 1.0f
            found = true
        }

        return ReplayGainInfo(
            trackGainDb = trackGain,
            trackPeak = trackPeak,
            albumGainDb = albumGain,
            albumPeak = albumPeak,
            hasData = found
        )
    }
}
