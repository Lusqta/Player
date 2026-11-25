package com.lucas.player.data

import android.net.Uri
import com.lucas.player.model.HomeSectionId
import com.lucas.player.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class Phase4BTest {

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)
    private val repo = LibraryRepository()

    private fun track(
        id: Long,
        title: String,
        album: String,
        trackNumber: Int,
        discNumber: Int = 1
    ): Track = Track(
        id = id,
        title = title,
        artist = "Artist",
        album = album,
        durationMs = 180_000L,
        uri = dummyUri,
        path = "/Music/$id.flac",
        folder = "Music",
        trackNumber = trackNumber,
        discNumber = discNumber
    )

    @Test
    fun albumCollectionTracksConcatenateInCollectionAlbumOrderSortedByTrackNumber() {
        val a2 = track(id = 1, title = "A Two", album = "Album Alpha", trackNumber = 2, discNumber = 1)
        val a1 = track(id = 2, title = "A One", album = "Album Alpha", trackNumber = 1, discNumber = 1)
        val aDisc2 = track(id = 3, title = "A Bonus", album = "Album Alpha", trackNumber = 1, discNumber = 2)

        val b3 = track(id = 4, title = "B Three", album = "Album Beta", trackNumber = 3)
        val b1 = track(id = 5, title = "B One", album = "album beta", trackNumber = 1)

        val other = track(id = 6, title = "Other", album = "Unlisted Album", trackNumber = 1)

        val allTracks = listOf(a2, a1, aDisc2, b3, b1, other)

        // Collection explicitly puts Album Beta before Album Alpha
        val tracksBetaFirst = repo.buildTracksForAlbumCollection(
            allTracks = allTracks,
            orderedAlbumNames = listOf("Album Beta", "Album Alpha", "Missing Album")
        )
        assertEquals(listOf(5L, 4L, 2L, 1L, 3L), tracksBetaFirst.map { it.id })

        // Reversed collection order puts Album Alpha before Album Beta
        val tracksAlphaFirst = repo.buildTracksForAlbumCollection(
            allTracks = allTracks,
            orderedAlbumNames = listOf("Album Alpha", "Album Beta")
        )
        assertEquals(listOf(2L, 1L, 3L, 5L, 4L), tracksAlphaFirst.map { it.id })
    }

    @Test
    fun homeSectionOrderNormalizationHandlesDefaultsInvalidAndMissingIds() {
        assertEquals(HomeSectionId.entries, PreferencesManager.normalizeHomeSectionOrder(null))
        assertEquals(HomeSectionId.entries, PreferencesManager.normalizeHomeSectionOrder("  "))

        val partial = PreferencesManager.normalizeHomeSectionOrder(
            "PLAYLISTS,UNKNOWN_OLD,ALBUM_COLLECTIONS,PLAYLISTS,ARTISTS"
        )
        assertEquals(
            listOf(
                HomeSectionId.PLAYLISTS,
                HomeSectionId.ALBUM_COLLECTIONS,
                HomeSectionId.ARTISTS,
                HomeSectionId.QUICK_MIXES,
                HomeSectionId.TOP_ALBUMS
            ),
            partial
        )

        val hidden = PreferencesManager.parseHiddenHomeSections(
            setOf("TOP_ALBUMS", "BOGUS_SECTION", "ARTISTS")
        )
        assertEquals(setOf(HomeSectionId.TOP_ALBUMS, HomeSectionId.ARTISTS), hidden)
    }
}
