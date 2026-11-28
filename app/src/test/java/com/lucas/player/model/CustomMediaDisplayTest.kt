package com.lucas.player.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class CustomMediaDisplayTest {

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)

    private fun track(id: Long, album: String): Track = Track(
        id = id,
        title = "Track $id",
        artist = "Artist $id",
        album = album,
        durationMs = 180_000L,
        uri = dummyUri,
        path = "/storage/emulated/0/Music/$id.mp3",
        folder = "Music"
    )

    @Test
    fun `animated gif plays when neither pauseGifs nor reduceMotion is active`() {
        val mode = resolveCustomMediaMode(
            gifPath = "/data/user/0/app/files/hero.gif",
            staticImagePath = "/data/user/0/app/files/hero.jpg",
            pausedDisplayMode = "STATIC_IMAGE",
            pauseGifs = false,
            reduceMotion = false
        )
        assertEquals(ResolvedMediaMode.ANIMATED_GIF, mode)
    }

    @Test
    fun `paused gifs with dual media obeys GIF_FRAME or STATIC_IMAGE`() {
        val frameMode = resolveCustomMediaMode(
            gifPath = "/tmp/a.gif",
            staticImagePath = "/tmp/a.jpg",
            pausedDisplayMode = "GIF_FRAME",
            pauseGifs = true,
            reduceMotion = false
        )
        assertEquals(ResolvedMediaMode.FROZEN_GIF_FRAME, frameMode)

        val staticMode = resolveCustomMediaMode(
            gifPath = "/tmp/a.gif",
            staticImagePath = "/tmp/a.jpg",
            pausedDisplayMode = "STATIC_IMAGE",
            pauseGifs = true,
            reduceMotion = false
        )
        assertEquals(ResolvedMediaMode.STATIC_IMAGE, staticMode)
    }

    @Test
    fun `reduceMotion pauses gif and respects fallback order`() {
        val staticOnReduce = resolveCustomMediaMode(
            gifPath = "/tmp/a.gif",
            staticImagePath = "/tmp/a.jpg",
            pausedDisplayMode = "STATIC_IMAGE",
            pauseGifs = false,
            reduceMotion = true
        )
        assertEquals(ResolvedMediaMode.STATIC_IMAGE, staticOnReduce)

        val gifOnlyFrozen = resolveCustomMediaMode(
            gifPath = "/tmp/a.gif",
            staticImagePath = "",
            pausedDisplayMode = "STATIC_IMAGE",
            pauseGifs = true,
            reduceMotion = false
        )
        assertEquals(ResolvedMediaMode.FROZEN_GIF_FRAME, gifOnlyFrozen)
    }

    @Test
    fun `static image only and empty media resolve cleanly`() {
        val staticOnly = resolveCustomMediaMode(
            gifPath = "",
            staticImagePath = "/tmp/cover.jpg",
            pausedDisplayMode = "GIF_FRAME",
            pauseGifs = false,
            reduceMotion = false
        )
        assertEquals(ResolvedMediaMode.STATIC_IMAGE, staticOnly)

        val fallback = resolveCustomMediaMode(
            gifPath = "",
            staticImagePath = "",
            pausedDisplayMode = "GIF_FRAME",
            pauseGifs = false,
            reduceMotion = false
        )
        assertEquals(ResolvedMediaMode.FALLBACK, fallback)
    }

    @Test
    fun `selectPlaylistMosaicTracks requires 4 distinct albums in playlist order`() {
        assertTrue(selectPlaylistMosaicTracks(listOf(track(1, "A"), track(2, "B"), track(3, "C"))).isEmpty())

        val repeatedAlbums = listOf(
            track(1, "Alpha"),
            track(2, "alpha "),
            track(3, "Beta"),
            track(4, "Gamma")
        )
        assertTrue(selectPlaylistMosaicTracks(repeatedAlbums).isEmpty())

        val diverseTracks = listOf(
            track(1, "Alpha"),
            track(2, "Alpha"),
            track(3, "Beta"),
            track(4, "Gamma"),
            track(5, "Delta"),
            track(6, "Epsilon")
        )
        val mosaic = selectPlaylistMosaicTracks(diverseTracks)
        assertEquals(listOf(1L, 3L, 4L, 5L), mosaic.map { it.id })
    }
}
