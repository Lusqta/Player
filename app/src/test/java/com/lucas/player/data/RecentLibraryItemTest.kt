package com.lucas.player.data

import android.net.Uri
import com.lucas.player.model.RecentLibraryItem
import com.lucas.player.model.Track
import com.lucas.player.model.UserPlaylist
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

class RecentLibraryItemTest {

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)
    private val repo = LibraryRepository()

    private fun createTrack(
        id: Long,
        title: String = "Track $id",
        artist: String = "Artist",
        album: String = "",
        albumArtist: String = "",
        dateAddedSec: Long = 1000L,
        durationMs: Long = 180_000L,
        path: String = "/Music/track_$id.mp3",
        folder: String = "Music"
    ): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            durationMs = durationMs,
            uri = dummyUri,
            path = path,
            folder = folder,
            dateAddedSec = dateAddedSec
        )
    }

    @Test
    fun albumWithMultipleTracksGroupsIntoSingleAlbumItem() {
        val t1 = createTrack(id = 1, title = "Track 1", artist = "The Weeknd; Daft Punk", album = "After Hours", dateAddedSec = 1000L)
        val t2 = createTrack(id = 2, title = "Track 2", artist = "The Weeknd", album = "after hours", dateAddedSec = 2500L)

        val result = repo.buildRecentLibraryItems(tracks = listOf(t1, t2))

        assertEquals(1, result.size)
        val item = result.first()
        assertTrue(item is RecentLibraryItem.AlbumItem)
        val albumItem = item as RecentLibraryItem.AlbumItem
        assertEquals("After Hours", albumItem.albumName)
        assertEquals(2500L, albumItem.timestampSec)
        assertEquals(t1, albumItem.coverTrack)
        assertEquals("The Weeknd", albumItem.subtitle)
    }

    @Test
    fun singleTracksWithoutValidAlbumStayIndividualSingleTrackItems() {
        val t1 = createTrack(id = 10, title = "Freestyle 1", artist = "Artist A; Artist B", album = "", dateAddedSec = 3000L)
        val t2 = createTrack(id = 11, title = "Demo 2", artist = "Artist C", album = "<unknown>", dateAddedSec = 2000L)
        val t3 = createTrack(id = 12, title = "Leak 3", artist = "Artist D", album = "Álbum desconhecido", dateAddedSec = 1500L)

        val result = repo.buildRecentLibraryItems(tracks = listOf(t1, t2, t3))

        assertEquals(3, result.size)
        assertTrue(result.all { it is RecentLibraryItem.SingleTrackItem })
        val first = result[0] as RecentLibraryItem.SingleTrackItem
        assertEquals("Freestyle 1", first.title)
        assertEquals("Artist A, Artist B", first.subtitle)
        assertEquals(3000L, first.timestampSec)
    }

    @Test
    fun playlistsAreMixedAndSortedByTimestampDescendingWithLimit() {
        val t1 = createTrack(id = 1, title = "Song", artist = "Artist A", album = "Album One", dateAddedSec = 1000L)
        val t2 = createTrack(id = 2, title = "Loose", artist = "Artist B", album = "", dateAddedSec = 3000L)
        val pl = UserPlaylist(id = 5L, name = "Late Night", trackCount = 8, createdMs = 2_000_000L, coverPath = "")

        val result = repo.buildRecentLibraryItems(
            tracks = listOf(t1, t2),
            playlists = listOf(pl),
            playlistCoverTracks = mapOf(5L to t1),
            limit = 12
        )

        assertEquals(3, result.size)
        assertTrue(result[0] is RecentLibraryItem.SingleTrackItem)
        assertTrue(result[1] is RecentLibraryItem.PlaylistItem)
        assertTrue(result[2] is RecentLibraryItem.AlbumItem)

        val plItem = result[1] as RecentLibraryItem.PlaylistItem
        assertEquals("Late Night", plItem.title)
        assertEquals(2000L, plItem.timestampSec)
        assertEquals(t1, plItem.coverTrack)
        assertTrue(plItem.subtitle.startsWith("Playlist ·"))
    }

    @Test
    fun junkAudioIsIgnoredAndLimitRespected() {
        val junk = createTrack(id = 99, title = "WhatsApp Voice", artist = "Me", album = "WhatsApp", dateAddedSec = 9999L, durationMs = 5_000L, folder = "WhatsApp Audio")
        val tracks = (1..15).map { i ->
            createTrack(id = i.toLong(), title = "Single $i", artist = "Artist $i", album = "", dateAddedSec = i.toLong() * 100L)
        }

        val result = repo.buildRecentLibraryItems(tracks = listOf(junk) + tracks, limit = 12)

        assertEquals(12, result.size)
        assertFalse(result.any { it.title == "WhatsApp Voice" || it.title == "WhatsApp" })
        assertEquals("Single 15", result.first().title)
    }
}
