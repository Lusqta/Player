package com.lucas.player.queue

import com.lucas.player.model.Track
import java.util.Collections

enum class RepeatMode(val value: Int, val displayName: String) {
    OFF(0, "Repetir: Desativado"),
    ONE(1, "Repetir: 1 Música"),
    CATEGORY(2, "Repetir: Álbum/Categoria"),
    ALL(3, "Repetir: Toda a Fila"),
    ADVANCE_CATEGORY(4, "Avançar Categoria");

    companion object {
        fun fromValue(value: Int): RepeatMode =
            values().firstOrNull { it.value == value } ?: OFF
    }
}

enum class ShuffleMode(val value: Int, val displayName: String) {
    OFF(0, "Aleatório: Desativado"),
    ALL_TRACKS(1, "Aleatório: Todas as Músicas"),
    WITHIN_CATEGORY(2, "Aleatório: Dentro da Categoria"),
    CATEGORIES(3, "Aleatório: Categorias"),
    SONGS_AND_CATEGORIES(4, "Aleatório: Músicas e Categorias");

    companion object {
        fun fromValue(value: Int): ShuffleMode =
            values().firstOrNull { it.value == value } ?: OFF
    }
}

enum class QueueSection {
    PRIORITY, // "A Seguir"
    QUEUE     // "Fila"
}

class QueueManager {

    data class PreQueueSnapshot(
        val tracks: List<Track>,
        val index: Int
    )

    private val originalTracks = mutableListOf<Track>()
    private val shuffleIndices = mutableListOf<Int>()
    private var currentIndex: Int = -1

    // Poweramp Priority Queue System
    private val priorityQueue = mutableListOf<Track>()
    private var preQueueSnapshot: PreQueueSnapshot? = null
    var isPlayingFromPriorityQueue: Boolean = false
        private set

    var shuffleMode: ShuffleMode = ShuffleMode.OFF
        private set

    val isShuffle: Boolean
        get() = shuffleMode != ShuffleMode.OFF

    var repeatMode: RepeatMode = RepeatMode.OFF
        private set

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        synchronized(this) {
            originalTracks.clear()
            originalTracks.addAll(tracks)

            if (originalTracks.isEmpty()) {
                currentIndex = -1
                shuffleIndices.clear()
                return
            }

            val safeIndex = startIndex.coerceIn(0, originalTracks.size - 1)
            rebuildShuffleIndices(preserveCurrentIndex = safeIndex)
            currentIndex = safeIndex
            isPlayingFromPriorityQueue = false
        }
    }

    val currentTrack: Track?
        get() = synchronized(this) {
            if (isPlayingFromPriorityQueue && priorityQueue.isNotEmpty()) {
                return priorityQueue.firstOrNull()
            }
            if (currentIndex in originalTracks.indices) {
                originalTracks[currentIndex]
            } else {
                null
            }
        }

    val currentQueueIndex: Int
        get() = synchronized(this) { currentIndex }

    val queueSize: Int
        get() = synchronized(this) { originalTracks.size }

    fun getTracks(): List<Track> = synchronized(this) {
        if (isShuffle && shuffleIndices.size == originalTracks.size) {
            shuffleIndices.mapNotNull { originalTracks.getOrNull(it) }
        } else {
            originalTracks.toList()
        }
    }

    fun getOriginalTracks(): List<Track> = synchronized(this) { originalTracks.toList() }

    fun getShuffleIndices(): List<Int> = synchronized(this) { shuffleIndices.toList() }

    internal fun setShuffleIndicesForTest(indices: List<Int>) {
        synchronized(this) {
            shuffleIndices.clear()
            shuffleIndices.addAll(indices)
        }
    }

    // --- Priority Queue (Poweramp Queue Engine) ---

    fun enqueueNext(track: Track) {
        synchronized(this) {
            if (priorityQueue.isEmpty()) {
                preQueueSnapshot = PreQueueSnapshot(originalTracks.toList(), currentIndex)
            }
            priorityQueue.add(0, track)
        }
    }

    fun enqueueEnd(track: Track) {
        synchronized(this) {
            if (priorityQueue.isEmpty()) {
                preQueueSnapshot = PreQueueSnapshot(originalTracks.toList(), currentIndex)
            }
            priorityQueue.add(track)
        }
    }

    fun enqueueList(tracks: List<Track>, playNext: Boolean) {
        synchronized(this) {
            if (priorityQueue.isEmpty()) {
                preQueueSnapshot = PreQueueSnapshot(originalTracks.toList(), currentIndex)
            }
            if (playNext) {
                priorityQueue.addAll(0, tracks)
            } else {
                priorityQueue.addAll(tracks)
            }
        }
    }

    fun getPriorityQueue(): List<Track> = synchronized(this) { priorityQueue.toList() }

    fun getUpcomingTracks(maxCount: Int = 2): List<Track> = synchronized(this) {
        val result = mutableListOf<Track>()
        if (priorityQueue.isNotEmpty()) {
            val startIdx = if (isPlayingFromPriorityQueue) 1 else 0
            for (i in startIdx until priorityQueue.size) {
                result.add(priorityQueue[i])
                if (result.size >= maxCount) return result
            }
        }
        if (originalTracks.isNotEmpty()) {
            val startIdx = currentIndex + 1
            for (i in startIdx until originalTracks.size) {
                val actualIndex = if (isShuffle && i in shuffleIndices.indices) shuffleIndices[i] else i
                if (actualIndex in originalTracks.indices) {
                    result.add(originalTracks[actualIndex])
                    if (result.size >= maxCount) return result
                }
            }
        }
        return result
    }

    fun clearPriorityQueue() {
        synchronized(this) {
            priorityQueue.clear()
            if (isPlayingFromPriorityQueue) {
                restorePreQueue()
            }
        }
    }

    fun skipToPriority(index: Int): Track? {
        synchronized(this) {
            if (index in priorityQueue.indices) {
                if (index > 0) {
                    priorityQueue.subList(0, index).clear()
                }
                isPlayingFromPriorityQueue = true
                return priorityQueue.firstOrNull()
            }
            return null
        }
    }

    private fun restorePreQueue() {
        preQueueSnapshot?.let { snapshot ->
            originalTracks.clear()
            originalTracks.addAll(snapshot.tracks)
            currentIndex = snapshot.index.coerceIn(0, (originalTracks.size - 1).coerceAtLeast(0))
            rebuildShuffleIndices(preserveCurrentIndex = currentIndex)
        }
        preQueueSnapshot = null
        isPlayingFromPriorityQueue = false
    }

    // --- Navigation Next / Previous ---

    fun peekNext(): Track? {
        synchronized(this) {
            if (repeatMode == RepeatMode.ONE) {
                return currentTrack
            }
            if (priorityQueue.isNotEmpty()) {
                if (isPlayingFromPriorityQueue) {
                    if (priorityQueue.size > 1) return priorityQueue[1]
                    return preQueueSnapshot?.let { snapshot ->
                        val nextIdx = snapshot.index + 1
                        if (nextIdx in snapshot.tracks.indices) snapshot.tracks[nextIdx]
                        else if (repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.CATEGORY) snapshot.tracks.firstOrNull()
                        else null
                    }
                } else {
                    return priorityQueue.firstOrNull()
                }
            }
            if (originalTracks.isEmpty()) return null
            if (isShuffle) {
                val currentShufflePos = shuffleIndices.indexOf(currentIndex)
                val nextShufflePos = currentShufflePos + 1
                if (nextShufflePos < shuffleIndices.size) {
                    return originalTracks[shuffleIndices[nextShufflePos]]
                } else if (repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.CATEGORY) {
                    return originalTracks[shuffleIndices.firstOrNull() ?: 0]
                }
                return null
            } else {
                val nextIndex = currentIndex + 1
                if (nextIndex < originalTracks.size) {
                    return originalTracks[nextIndex]
                } else if (repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.CATEGORY) {
                    return originalTracks.firstOrNull()
                }
                return null
            }
        }
    }

    fun next(): Track? {
        synchronized(this) {
            if (repeatMode == RepeatMode.ONE) {
                return currentTrack
            }

            // 1. Check if Priority Queue has items
            if (priorityQueue.isNotEmpty()) {
                // Remove the one that just played if we were already in priority queue
                if (isPlayingFromPriorityQueue) {
                    priorityQueue.removeAt(0)
                }

                if (priorityQueue.isNotEmpty()) {
                    isPlayingFromPriorityQueue = true
                    return priorityQueue[0]
                } else {
                    // Priority queue just emptied: seamless restore to original category!
                    restorePreQueue()
                }
            }

            if (originalTracks.isEmpty()) return null

            // 2. Standard Category Playback
            if (isShuffle) {
                val currentShufflePos = shuffleIndices.indexOf(currentIndex)
                val nextShufflePos = currentShufflePos + 1
                if (nextShufflePos < shuffleIndices.size) {
                    currentIndex = shuffleIndices[nextShufflePos]
                    return originalTracks[currentIndex]
                } else if (repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.CATEGORY) {
                    rebuildShuffleIndices(preserveCurrentIndex = -1)
                    currentIndex = shuffleIndices.firstOrNull() ?: 0
                    return originalTracks[currentIndex]
                } else {
                    return null // Reached end of shuffled queue
                }
            } else {
                val nextIndex = currentIndex + 1
                if (nextIndex < originalTracks.size) {
                    currentIndex = nextIndex
                    return originalTracks[currentIndex]
                } else if (repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.CATEGORY) {
                    currentIndex = 0
                    return originalTracks[currentIndex]
                } else {
                    return null // Reached end of linear queue
                }
            }
        }
    }

    fun previous(): Track? {
        synchronized(this) {
            if (repeatMode == RepeatMode.ONE) {
                return currentTrack
            }

            if (isPlayingFromPriorityQueue && priorityQueue.isNotEmpty()) {
                return priorityQueue.firstOrNull()
            }

            if (originalTracks.isEmpty()) return null

            if (isShuffle) {
                val currentShufflePos = shuffleIndices.indexOf(currentIndex)
                val prevShufflePos = currentShufflePos - 1
                if (prevShufflePos >= 0) {
                    currentIndex = shuffleIndices[prevShufflePos]
                    return originalTracks[currentIndex]
                } else if (repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.CATEGORY) {
                    currentIndex = shuffleIndices.lastOrNull() ?: 0
                    return originalTracks[currentIndex]
                } else {
                    return originalTracks[shuffleIndices[0]]
                }
            } else {
                val prevIndex = currentIndex - 1
                if (prevIndex >= 0) {
                    currentIndex = prevIndex
                    return originalTracks[currentIndex]
                } else if (repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.CATEGORY) {
                    currentIndex = originalTracks.size - 1
                    return originalTracks[currentIndex]
                } else {
                    return originalTracks[0]
                }
            }
        }
    }

    fun skipTo(index: Int): Track? {
        synchronized(this) {
            if (isShuffle && index in shuffleIndices.indices) {
                currentIndex = shuffleIndices[index]
                isPlayingFromPriorityQueue = false
                return originalTracks[currentIndex]
            }
            if (index in originalTracks.indices) {
                currentIndex = index
                isPlayingFromPriorityQueue = false
                return originalTracks[currentIndex]
            }
            return null
        }
    }

    // --- Reordenação da Fila (Fase 4.1) ---

    fun moveItem(section: QueueSection, from: Int, to: Int): Boolean {
        synchronized(this) {
            when (section) {
                QueueSection.PRIORITY -> {
                    if (from !in priorityQueue.indices || to !in priorityQueue.indices) return false
                    if (from == to) return true
                    val item = priorityQueue.removeAt(from)
                    priorityQueue.add(to, item)
                    return true
                }
                QueueSection.QUEUE -> {
                    if (isShuffle) {
                        if (from !in shuffleIndices.indices || to !in shuffleIndices.indices) return false
                        if (from == to) return true
                        val idx = shuffleIndices.removeAt(from)
                        shuffleIndices.add(to, idx)
                        return true
                    } else {
                        if (from !in originalTracks.indices || to !in originalTracks.indices) return false
                        if (from == to) return true
                        val item = originalTracks.removeAt(from)
                        originalTracks.add(to, item)

                        if (from == currentIndex) {
                            currentIndex = to
                        } else if (from < currentIndex && to >= currentIndex) {
                            currentIndex--
                        } else if (from > currentIndex && to <= currentIndex) {
                            currentIndex++
                        }

                        if (shuffleIndices.isNotEmpty()) {
                            rebuildShuffleIndices(preserveCurrentIndex = currentIndex)
                        }
                        return true
                    }
                }
            }
        }
    }

    fun moveItem(section: String, from: Int, to: Int): Boolean {
        val sec = when (section.trim().lowercase()) {
            "a seguir", "priority", "prioritária", "prioritaria" -> QueueSection.PRIORITY
            "fila", "queue", "normal", "regular" -> QueueSection.QUEUE
            else -> return false
        }
        return moveItem(sec, from, to)
    }

    // --- Shuffle Modes ---

    fun toggleShuffle(): ShuffleMode {
        synchronized(this) {
            shuffleMode = when (shuffleMode) {
                ShuffleMode.OFF -> ShuffleMode.ALL_TRACKS
                ShuffleMode.ALL_TRACKS -> ShuffleMode.WITHIN_CATEGORY
                ShuffleMode.WITHIN_CATEGORY -> ShuffleMode.OFF
                else -> ShuffleMode.OFF
            }
            if (isShuffle) {
                rebuildShuffleIndices(preserveCurrentIndex = currentIndex)
            }
            return shuffleMode
        }
    }

    fun setShuffleMode(mode: ShuffleMode) {
        synchronized(this) {
            shuffleMode = mode
            if (isShuffle) {
                rebuildShuffleIndices(preserveCurrentIndex = currentIndex)
            }
        }
    }

    // --- Repeat Modes ---

    fun toggleRepeat(): RepeatMode {
        synchronized(this) {
            repeatMode = when (repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.CATEGORY
                RepeatMode.CATEGORY -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.ADVANCE_CATEGORY
                RepeatMode.ADVANCE_CATEGORY -> RepeatMode.OFF
            }
            return repeatMode
        }
    }

    fun setRepeat(mode: RepeatMode) {
        synchronized(this) {
            repeatMode = mode
        }
    }

    fun removePriorityQueueItem(index: Int): Boolean {
        synchronized(this) {
            if (index in priorityQueue.indices) {
                priorityQueue.removeAt(index)
                if (priorityQueue.isEmpty() && isPlayingFromPriorityQueue) {
                    restorePreQueue()
                }
                return true
            }
            return false
        }
    }

    fun removeTrackById(trackId: Long): Boolean {
        synchronized(this) {
            val pqIdx = priorityQueue.indexOfFirst { it.id == trackId }
            if (pqIdx != -1) {
                return removePriorityQueueItem(pqIdx)
            }
            val origIdx = originalTracks.indexOfFirst { it.id == trackId }
            if (origIdx != -1) {
                originalTracks.removeAt(origIdx)
                if (currentIndex >= originalTracks.size) {
                    currentIndex = (originalTracks.size - 1).coerceAtLeast(0)
                }
                rebuildShuffleIndices(preserveCurrentIndex = currentIndex)
                return true
            }
            return false
        }
    }

    fun clearAllQueue() {
        synchronized(this) {
            priorityQueue.clear()
            originalTracks.clear()
            currentIndex = -1
            shuffleIndices.clear()
            preQueueSnapshot = null
            isPlayingFromPriorityQueue = false
        }
    }

    fun shuffleQueue() {
        synchronized(this) {
            if (originalTracks.isNotEmpty()) {
                rebuildShuffleIndices(preserveCurrentIndex = currentIndex)
            }
            if (priorityQueue.isNotEmpty()) {
                priorityQueue.shuffle()
            }
        }
    }

    private fun rebuildShuffleIndices(preserveCurrentIndex: Int) {
        shuffleIndices.clear()
        if (originalTracks.isEmpty()) return

        val indices = (originalTracks.indices).toMutableList()
        if (preserveCurrentIndex in indices) {
            indices.remove(preserveCurrentIndex)
            Collections.shuffle(indices)
            shuffleIndices.add(preserveCurrentIndex)
            shuffleIndices.addAll(indices)
        } else {
            Collections.shuffle(indices)
            shuffleIndices.addAll(indices)
        }
    }
}
