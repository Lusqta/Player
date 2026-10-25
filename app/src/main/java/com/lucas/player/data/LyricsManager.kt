package com.lucas.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import com.lucas.player.model.Track
import java.io.File
import java.util.regex.Pattern

/**
 * Synced (.LRC) and Embedded (ID3 USLT) Lyrics Engine replicating Poweramp's lyrics parser.
 */
object LyricsManager {

    data class LyricLine(
        val timeMs: Long,
        val text: String
    )

    data class Lyrics(
        val lines: List<LyricLine>,
        val isSynced: Boolean,
        val source: String
    )

    private val TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")

    /**
     * Finds and loads lyrics for the given track.
     * Searches for local .lrc sidecar file first, then checks embedded metadata.
     */
    fun loadLyrics(context: Context, track: Track): Lyrics? {
        // 1. Try external .lrc sidecar file next to audio
        val sidecarLrc = findSidecarLrcFile(track.path)
        if (sidecarLrc != null && sidecarLrc.exists() && sidecarLrc.canRead()) {
            val content = try { sidecarLrc.readText(Charsets.UTF_8) } catch (_: Exception) { null }
            if (!content.isNullOrBlank()) {
                val parsed = parseLrc(content, "Arquivo .LRC (${sidecarLrc.name})")
                if (parsed != null && parsed.lines.isNotEmpty()) return parsed
            }
        }

        // 2. Try embedded tags via MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(track.path)
            // Some devices expose lyrics in metadata or author tags
            val rawLyrics = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION)
            retriever.release()

            if (!rawLyrics.isNullOrBlank() && rawLyrics.length > 50 && (rawLyrics.contains("\n") || rawLyrics.contains("["))) {
                val parsed = parseLrc(rawLyrics, "Tags Embutidas (ID3)")
                if (parsed != null && parsed.lines.isNotEmpty()) return parsed
            }
        } catch (_: Exception) {
            // Ignore format reading errors
        }

        return null
    }

    /**
     * Parses LRC text formatted with [mm:ss.xx] timestamps or plain text.
     */
    fun parseLrc(rawText: String, source: String): Lyrics? {
        val lines = rawText.lines()
        val parsedLines = mutableListOf<LyricLine>()
        var hasTimestamps = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Check metadata tags e.g. [ti:Title], [ar:Artist], [al:Album]
            if (trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") ||
                trimmed.startsWith("[offset:")) {
                continue
            }

            val matcher = TIMESTAMP_PATTERN.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                hasTimestamps = true
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val fracStr = matcher.group(3)
                val ms = when {
                    fracStr == null -> 0L
                    fracStr.length == 1 -> fracStr.toLong() * 100L
                    fracStr.length == 2 -> fracStr.toLong() * 10L
                    else -> fracStr.substring(0, 3).toLong()
                }
                timestamps.add(min * 60_000L + sec * 1_000L + ms)
                lastMatchEnd = matcher.end()
            }

            val text = trimmed.substring(lastMatchEnd).trim()

            if (timestamps.isNotEmpty()) {
                for (ts in timestamps) {
                    parsedLines.add(LyricLine(timeMs = ts, text = text))
                }
            } else if (!hasTimestamps && trimmed.isNotEmpty()) {
                // Plain unsynced line
                parsedLines.add(LyricLine(timeMs = -1L, text = trimmed))
            }
        }

        if (parsedLines.isEmpty()) return null

        val sorted = if (hasTimestamps) {
            parsedLines.sortedBy { it.timeMs }
        } else {
            parsedLines
        }

        return Lyrics(
            lines = sorted,
            isSynced = hasTimestamps,
            source = source
        )
    }

    /**
     * Returns the active lyric line index for the current playback position in milliseconds.
     */
    fun findActiveLineIndex(lyrics: Lyrics, currentPositionMs: Long): Int {
        if (!lyrics.isSynced || lyrics.lines.isEmpty()) return -1

        var low = 0
        var high = lyrics.lines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lyrics.lines[mid].timeMs <= currentPositionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return result
    }

    private fun findSidecarLrcFile(trackPath: String): File? {
        val audioFile = File(trackPath)
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension

        // Exact matches
        val candidate1 = File(parent, "$baseName.lrc")
        if (candidate1.exists()) return candidate1

        val candidate2 = File(parent, "$baseName.LRC")
        if (candidate2.exists()) return candidate2

        // Subdirectory "Lyrics" or "lyrics"
        val lyricsDir = File(parent, "Lyrics")
        if (lyricsDir.isDirectory) {
            val candidateSub = File(lyricsDir, "$baseName.lrc")
            if (candidateSub.exists()) return candidateSub
        }

        return null
    }
}
