package com.lucas.player.model

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

class ArtistUtilsTest {

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)

    private fun createTrack(
        id: Long = 1L,
        artist: String = "Test Artist",
        albumArtist: String = "",
        album: String = "Test Album",
        durationMs: Long = 180_000L,
        path: String = "/storage/emulated/0/Music/song.mp3",
        folder: String = "Music"
    ): Track {
        return Track(
            id = id,
            title = "Song $id",
            artist = artist,
            album = album,
            durationMs = durationMs,
            path = path,
            folder = folder,
            uri = dummyUri,
            albumArtist = albumArtist
        )
    }

    @Test
    fun testSplitArtists() {
        val raw = "Kanye West; Lil Wayne; <unknown>;  ; Desconhecido; Jay-Z"
        val result = splitArtists(raw)
        assertEquals(listOf("Kanye West", "Lil Wayne", "Jay-Z"), result)
    }

    @Test
    fun testPrimaryArtistsOf() {
        // Quando albumArtist está preenchido, prioriza albumArtist
        val trackWithAlbumArtist = createTrack(
            artist = "Kendrick Lamar; Rihanna",
            albumArtist = "Kendrick Lamar"
        )
        assertEquals(listOf("Kendrick Lamar"), primaryArtistsOf(trackWithAlbumArtist))

        // Quando albumArtist é vazio, pega apenas o primeiro de splitArtists(artist)
        val trackWithoutAlbumArtist = createTrack(
            artist = "Travis Scott; Drake; Young Thug",
            albumArtist = ""
        )
        assertEquals(listOf("Travis Scott"), primaryArtistsOf(trackWithoutAlbumArtist))
    }

    @Test
    fun testIsPrimaryArtistTrack() {
        val track = createTrack(
            artist = "Daft Punk; Pharrell Williams",
            albumArtist = "Daft Punk"
        )
        assertTrue(isPrimaryArtistTrack(track, "Daft Punk"))
        assertTrue(isPrimaryArtistTrack(track, "daft punk"))
        assertFalse(isPrimaryArtistTrack(track, "Pharrell Williams"))
        assertFalse(isPrimaryArtistTrack(track, "Julian Casablancas"))
    }

    @Test
    fun testIsFeatArtistTrack() {
        val track = createTrack(
            artist = "Daft Punk; Pharrell Williams",
            albumArtist = "Daft Punk"
        )
        assertFalse(isFeatArtistTrack(track, "Daft Punk"))
        assertTrue(isFeatArtistTrack(track, "Pharrell Williams"))
        assertTrue(isFeatArtistTrack(track, "pharrell williams"))
        assertFalse(isFeatArtistTrack(track, "Julian Casablancas"))
    }

    @Test
    fun testClassifyIsAlbum() {
        // Nome com 'single' ou 'ep' -> sempre Single/EP (isAlbum = false)
        assertFalse(classifyIsAlbum("Starboy - Single", trackCount = 1, maxTrackNumber = 1))
        assertFalse(classifyIsAlbum("My Dear Melancholy, EP", trackCount = 7, maxTrackNumber = 7))
        assertFalse(classifyIsAlbum("Greatest Hits (EP)", trackCount = 10, maxTrackNumber = 10))

        // trackCount >= 7 ou maxTrackNumber >= 7 sem 'ep'/'single' -> Álbum (isAlbum = true)
        assertTrue(classifyIsAlbum("After Hours", trackCount = 14, maxTrackNumber = 14))
        assertTrue(classifyIsAlbum("Dawn FM", trackCount = 6, maxTrackNumber = 16)) // maxTrackNumber >= 7
        assertTrue(classifyIsAlbum("Compilation", trackCount = 7, maxTrackNumber = 3)) // trackCount >= 7

        // Poucas faixas (< 7) e maxTrackNumber (< 7) -> Single/EP
        assertFalse(classifyIsAlbum("Blinding Lights", trackCount = 2, maxTrackNumber = 2))
    }

    @Test
    fun testIsValidHighlightName() {
        assertTrue(isValidHighlightName("Taylor Swift"))
        assertTrue(isValidHighlightName("1989"))
        assertFalse(isValidHighlightName(""))
        assertFalse(isValidHighlightName("   "))
        assertFalse(isValidHighlightName("<unknown>"))
        assertFalse(isValidHighlightName("unknown"))
        assertFalse(isValidHighlightName("desconhecido"))
        assertFalse(isValidHighlightName("sem título"))
    }

    @Test
    fun testIsJunkAudio() {
        val normalTrack = createTrack(durationMs = 120_000L, path = "/storage/Music/song.mp3")
        assertFalse(isJunkAudio(normalTrack))

        val shortTrack = createTrack(durationMs = 15_000L, path = "/storage/Music/intro.mp3")
        assertTrue(isJunkAudio(shortTrack))

        val whatsappTrack = createTrack(durationMs = 45_000L, folder = "WhatsApp Audio")
        assertTrue(isJunkAudio(whatsappTrack))
    }
}
