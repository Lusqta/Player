package com.lucas.player.queue

import android.net.Uri
import com.lucas.player.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class QueueManagerTest {

    private lateinit var queueManager: QueueManager
    private lateinit var sampleTracks: List<Track>
    private val dummyUri: Uri = Mockito.mock(Uri::class.java)

    private fun createTrack(id: Long, title: String): Track {
        return Track(
            id = id,
            title = title,
            artist = "Artista $id",
            album = "Álbum $id",
            durationMs = 180000L,
            path = "/storage/emulated/0/Music/track_$id.flac",
            folder = "Music",
            uri = dummyUri
        )
    }

    @Before
    fun setUp() {
        queueManager = QueueManager()
        sampleTracks = listOf(
            createTrack(1L, "Faixa 1"),
            createTrack(2L, "Faixa 2"),
            createTrack(3L, "Faixa 3"),
            createTrack(4L, "Faixa 4"),
            createTrack(5L, "Faixa 5")
        )
    }

    @Test
    fun testMoveBeforeCurrentIndex_preservesCurrentTrackAndIndex() {
        // Inicia tocando a Faixa 3 no índice 2
        queueManager.setQueue(sampleTracks, startIndex = 2)
        assertEquals("Faixa 3", queueManager.currentTrack?.title)
        assertEquals(2, queueManager.currentQueueIndex)

        // Move Faixa 1 (índice 0) para o índice 1 (ambos antes do índice 2)
        val moved = queueManager.moveItem(QueueSection.QUEUE, from = 0, to = 1)
        assertTrue(moved)

        val tracks = queueManager.getTracks()
        assertEquals("Faixa 2", tracks[0].title)
        assertEquals("Faixa 1", tracks[1].title)
        assertEquals("Faixa 3", tracks[2].title)

        // Faixa atual continua a mesma e o índice deve continuar 2
        assertEquals("Faixa 3", queueManager.currentTrack?.title)
        assertEquals(2, queueManager.currentQueueIndex)
    }

    @Test
    fun testMoveAfterCurrentIndex_preservesCurrentTrackAndIndex() {
        // Inicia tocando a Faixa 2 no índice 1
        queueManager.setQueue(sampleTracks, startIndex = 1)
        assertEquals("Faixa 2", queueManager.currentTrack?.title)
        assertEquals(1, queueManager.currentQueueIndex)

        // Move Faixa 5 (índice 4) para índice 2 (ambos depois do índice 1)
        val moved = queueManager.moveItem(QueueSection.QUEUE, from = 4, to = 2)
        assertTrue(moved)

        val tracks = queueManager.getTracks()
        assertEquals("Faixa 2", tracks[1].title)
        assertEquals("Faixa 5", tracks[2].title)
        assertEquals("Faixa 3", tracks[3].title)
        assertEquals("Faixa 4", tracks[4].title)

        // Faixa atual e índice continuam inalterados
        assertEquals("Faixa 2", queueManager.currentTrack?.title)
        assertEquals(1, queueManager.currentQueueIndex)
    }

    @Test
    fun testMoveCrossingCurrentIndex_adjustsIndexAndPreservesTrack() {
        // Inicia tocando a Faixa 3 no índice 2
        queueManager.setQueue(sampleTracks, startIndex = 2)
        assertEquals("Faixa 3", queueManager.currentTrack?.title)

        // Move do início (0) para o fim (4) cruzando o currentIndex (2)
        val movedForward = queueManager.moveItem(QueueSection.QUEUE, from = 0, to = 4)
        assertTrue(movedForward)

        // A Faixa 3 deve ter sido deslocada para o índice 1
        assertEquals("Faixa 3", queueManager.currentTrack?.title)
        assertEquals(1, queueManager.currentQueueIndex)

        // Agora move do fim (4) para antes do currentIndex (0)
        val movedBack = queueManager.moveItem(QueueSection.QUEUE, from = 4, to = 0)
        assertTrue(movedBack)

        // A Faixa 3 deve ter sido deslocada para o índice 2
        assertEquals("Faixa 3", queueManager.currentTrack?.title)
        assertEquals(2, queueManager.currentQueueIndex)
    }

    @Test
    fun testMoveCurrentTrackItself_updatesCurrentIndexToDestination() {
        // Inicia tocando a Faixa 2 no índice 1
        queueManager.setQueue(sampleTracks, startIndex = 1)
        assertEquals("Faixa 2", queueManager.currentTrack?.title)
        assertEquals(1, queueManager.currentQueueIndex)

        // Move a própria faixa atual (índice 1) para o índice 4
        val moved = queueManager.moveItem(QueueSection.QUEUE, from = 1, to = 4)
        assertTrue(moved)

        val tracks = queueManager.getTracks()
        assertEquals("Faixa 1", tracks[0].title)
        assertEquals("Faixa 3", tracks[1].title)
        assertEquals("Faixa 4", tracks[2].title)
        assertEquals("Faixa 5", tracks[3].title)
        assertEquals("Faixa 2", tracks[4].title)

        // A faixa atual continua sendo Faixa 2, mas agora no índice 4
        assertEquals("Faixa 2", queueManager.currentTrack?.title)
        assertEquals(4, queueManager.currentQueueIndex)
    }

    @Test
    fun testMoveWithShuffleEnabled_movesInShuffleIndicesWithoutAffectingOriginalTracks() {
        queueManager.setQueue(sampleTracks, startIndex = 0)
        queueManager.setShuffleMode(ShuffleMode.ALL_TRACKS)

        // Define índices determinísticos para o teste: [0, 4, 2, 1, 3]
        queueManager.setShuffleIndicesForTest(listOf(0, 4, 2, 1, 3))
        val currentTrackBefore = queueManager.currentTrack?.title

        // Em shuffle, getTracks() retorna [Faixa 1, Faixa 5, Faixa 3, Faixa 2, Faixa 4]
        var tracks = queueManager.getTracks()
        assertEquals("Faixa 1", tracks[0].title)
        assertEquals("Faixa 5", tracks[1].title)
        assertEquals("Faixa 3", tracks[2].title)

        // Move o item 1 (Faixa 5) para a posição 3
        val moved = queueManager.moveItem(QueueSection.QUEUE, from = 1, to = 3)
        assertTrue(moved)

        tracks = queueManager.getTracks()
        assertEquals("Faixa 1", tracks[0].title)
        assertEquals("Faixa 3", tracks[1].title)
        assertEquals("Faixa 2", tracks[2].title)
        assertEquals("Faixa 5", tracks[3].title)
        assertEquals("Faixa 4", tracks[4].title)

        // Faixa atual permanece a mesma
        assertEquals(currentTrackBefore, queueManager.currentTrack?.title)

        // originalTracks permanece na ordem original intacta
        val original = queueManager.getOriginalTracks()
        assertEquals("Faixa 1", original[0].title)
        assertEquals("Faixa 2", original[1].title)
        assertEquals("Faixa 3", original[2].title)
        assertEquals("Faixa 4", original[3].title)
        assertEquals("Faixa 5", original[4].title)
    }

    @Test
    fun testMovePriorityQueue_reordersWithinPrioritySection() {
        val p1 = createTrack(101L, "Prioritária 1")
        val p2 = createTrack(102L, "Prioritária 2")
        val p3 = createTrack(103L, "Prioritária 3")

        queueManager.enqueueEnd(p1)
        queueManager.enqueueEnd(p2)
        queueManager.enqueueEnd(p3)

        assertEquals(3, queueManager.getPriorityQueue().size)
        assertEquals("Prioritária 1", queueManager.getPriorityQueue()[0].title)

        // Move a Prioritária 1 (índice 0) para o índice 2
        val moved = queueManager.moveItem(QueueSection.PRIORITY, from = 0, to = 2)
        assertTrue(moved)

        val pq = queueManager.getPriorityQueue()
        assertEquals("Prioritária 2", pq[0].title)
        assertEquals("Prioritária 3", pq[1].title)
        assertEquals("Prioritária 1", pq[2].title)
    }

    @Test
    fun testMoveItem_stringSectionAndInvalidBounds() {
        queueManager.setQueue(sampleTracks, startIndex = 0)

        // Usando string "Fila"
        assertTrue(queueManager.moveItem("Fila", from = 0, to = 2))

        // Usando string "A Seguir"
        queueManager.enqueueEnd(createTrack(99L, "P99"))
        queueManager.enqueueEnd(createTrack(100L, "P100"))
        assertTrue(queueManager.moveItem("A Seguir", from = 0, to = 1))

        // Limites inválidos
        assertFalse(queueManager.moveItem("Fila", from = -1, to = 2))
        assertFalse(queueManager.moveItem("Fila", from = 0, to = 99))
        assertFalse(queueManager.moveItem("Inexistente", from = 0, to = 1))
    }

    @Test
    fun testEnqueueNext_whenPlayingFromPriorityQueue_insertsAtPosition1() {
        queueManager.setQueue(sampleTracks, startIndex = 0)
        val p1 = createTrack(101L, "Prioritária 1")
        val p2 = createTrack(102L, "Prioritária 2")
        queueManager.enqueueEnd(p1)
        queueManager.enqueueEnd(p2)

        // Toca a primeira da prioridade
        val nextTrack = queueManager.next()
        assertEquals("Prioritária 1", nextTrack?.title)
        assertTrue(queueManager.isPlayingFromPriorityQueue)

        // Adiciona "A Seguir" enquanto toca prioritária: deve entrar no índice 1 (logo após a tocando agora)
        val p3 = createTrack(103L, "Prioritária 3")
        queueManager.enqueueNext(p3)

        val pq = queueManager.getPriorityQueue()
        assertEquals(3, pq.size)
        assertEquals("Prioritária 1", pq[0].title)
        assertEquals("Prioritária 3", pq[1].title)
        assertEquals("Prioritária 2", pq[2].title)

        // Upcoming priority deve descartar o item [0] que está tocando
        val upcomingPriority = queueManager.getUpcomingPriorityTracks()
        assertEquals(2, upcomingPriority.size)
        assertEquals("Prioritária 3", upcomingPriority[0].title)
        assertEquals("Prioritária 2", upcomingPriority[1].title)
    }

    @Test
    fun testClearManualQueueKeepingCurrent_preservesPlayingTrack() {
        queueManager.setQueue(sampleTracks, startIndex = 0)
        val p1 = createTrack(101L, "Prioritária 1")
        val p2 = createTrack(102L, "Prioritária 2")
        val p3 = createTrack(103L, "Prioritária 3")
        queueManager.enqueueEnd(p1)
        queueManager.enqueueEnd(p2)
        queueManager.enqueueEnd(p3)

        // Toca da prioridade
        queueManager.next()
        assertTrue(queueManager.isPlayingFromPriorityQueue)
        assertEquals("Prioritária 1", queueManager.currentTrack?.title)

        // Limpa a fila manual mantendo a atual
        queueManager.clearManualQueueKeepingCurrent()

        assertTrue(queueManager.isPlayingFromPriorityQueue)
        assertEquals(1, queueManager.getPriorityQueue().size)
        assertEquals("Prioritária 1", queueManager.currentTrack?.title)
        assertTrue(queueManager.getUpcomingPriorityTracks().isEmpty())
    }

    @Test
    fun testUpcomingRegularTracks_returnsStrictlyUpcoming() {
        queueManager.setQueue(sampleTracks, startIndex = 1) // Tocando Faixa 2
        val upcoming = queueManager.getUpcomingRegularTracks()
        assertEquals(3, upcoming.size)
        assertEquals("Faixa 3", upcoming[0].title)
        assertEquals("Faixa 4", upcoming[1].title)
        assertEquals("Faixa 5", upcoming[2].title)
    }

    @Test
    fun testMoveUpcomingItem_regularQueue() {
        queueManager.setQueue(sampleTracks, startIndex = 1) // Tocando Faixa 2 (índice 1)
        // Upcoming: [Faixa 3, Faixa 4, Faixa 5] (índices 0, 1, 2)
        // Move upcoming 0 (Faixa 3) para posição 2 (após Faixa 5)
        val moved = queueManager.moveUpcomingItem(QueueSection.QUEUE, fromUpcoming = 0, toUpcoming = 2)
        assertTrue(moved)

        val upcoming = queueManager.getUpcomingRegularTracks()
        assertEquals("Faixa 4", upcoming[0].title)
        assertEquals("Faixa 5", upcoming[1].title)
        assertEquals("Faixa 3", upcoming[2].title)

        // Faixa atual não foi alterada
        assertEquals("Faixa 2", queueManager.currentTrack?.title)
    }

    @Test
    fun testRemoveUpcomingItem_andUndoInsert() {
        queueManager.setQueue(sampleTracks, startIndex = 0) // Tocando Faixa 1
        // Upcoming: [Faixa 2, Faixa 3, Faixa 4, Faixa 5]

        // Remove upcoming índice 1 (Faixa 3)
        val removed = queueManager.removeUpcomingItem(QueueSection.QUEUE, upcomingIndex = 1)
        assertEquals("Faixa 3", removed?.title)

        val upcomingAfterRemove = queueManager.getUpcomingRegularTracks()
        assertEquals(3, upcomingAfterRemove.size)
        assertEquals("Faixa 2", upcomingAfterRemove[0].title)
        assertEquals("Faixa 4", upcomingAfterRemove[1].title)
        assertEquals("Faixa 5", upcomingAfterRemove[2].title)

        // Desfazer: reinserir no índice 1
        if (removed != null) {
            queueManager.insertUpcomingItem(QueueSection.QUEUE, upcomingIndex = 1, track = removed)
        }

        val restoredUpcoming = queueManager.getUpcomingRegularTracks()
        assertEquals(4, restoredUpcoming.size)
        assertEquals("Faixa 2", restoredUpcoming[0].title)
        assertEquals("Faixa 3", restoredUpcoming[1].title)
        assertEquals("Faixa 4", restoredUpcoming[2].title)
        assertEquals("Faixa 5", restoredUpcoming[3].title)
    }
}
