package com.lucas.player.data

import android.net.Uri
import com.lucas.player.model.MostPlayedPeriod
import com.lucas.player.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class MostPlayedRankingTest {

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)
    private val repo = LibraryRepository()
    private val dayMs = 24L * 60L * 60L * 1000L

    private fun track(
        id: Long,
        title: String = "Track $id",
        artist: String = "Artist",
        album: String = "Album",
        albumArtist: String = "",
        playCount: Int = 0,
        lastPlayedTimestamp: Long = 0L,
        durationMs: Long = 180_000L,
        folder: String = "Music"
    ): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        durationMs = durationMs,
        uri = dummyUri,
        path = "/Music/t_$id.mp3",
        folder = folder,
        playCount = playCount,
        lastPlayedTimestamp = lastPlayedTimestamp
    )

    @Test
    fun periodAggregationFiltersEventsByWeekMonthYearAndAllUsesHistoricalPlayCount() {
        val nowMs = 1_000L * dayMs
        val t1 = track(id = 1, title = "Recent Hit", playCount = 3, lastPlayedTimestamp = nowMs - dayMs)
        val t2 = track(id = 2, title = "Month Hit", playCount = 10, lastPlayedTimestamp = nowMs - 15L * dayMs)
        val t3 = track(id = 3, title = "Year Hit", playCount = 50, lastPlayedTimestamp = nowMs - 120L * dayMs)
        val t4 = track(id = 4, title = "Legacy Hit", playCount = 120, lastPlayedTimestamp = nowMs - 400L * dayMs)
        val junk = track(id = 5, title = "Voice", playCount = 999, durationMs = 5_000L, folder = "WhatsApp Audio")

        val events = listOf(
            // t1: 3 plays within 7 days
            1L to (nowMs - 1L * dayMs),
            1L to (nowMs - 2L * dayMs),
            1L to (nowMs - 3L * dayMs),
            // t2: 4 plays 15 days ago (month & year, not week)
            2L to (nowMs - 15L * dayMs),
            2L to (nowMs - 15L * dayMs),
            2L to (nowMs - 16L * dayMs),
            2L to (nowMs - 16L * dayMs),
            // t3: 5 plays 120 days ago (year only)
            3L to (nowMs - 120L * dayMs),
            3L to (nowMs - 120L * dayMs),
            3L to (nowMs - 120L * dayMs),
            3L to (nowMs - 120L * dayMs),
            3L to (nowMs - 120L * dayMs),
            // t4: 1 play 400 days ago (outside year)
            4L to (nowMs - 400L * dayMs),
            // junk: recent plays should be filtered out
            5L to (nowMs - 1L * dayMs)
        )
        val allTracks = listOf(t1, t2, t3, t4, junk)

        val weekRanked = repo.buildMostPlayedTracks(
            allTracks,
            MostPlayedPeriod.WEEK,
            repo.aggregatePlayEvents(events, MostPlayedPeriod.WEEK, nowMs)
        )
        assertEquals(listOf(1L), weekRanked.map { it.track.id })
        assertEquals(listOf(3), weekRanked.map { it.plays })

        val monthRanked = repo.buildMostPlayedTracks(
            allTracks,
            MostPlayedPeriod.MONTH,
            repo.aggregatePlayEvents(events, MostPlayedPeriod.MONTH, nowMs)
        )
        assertEquals(listOf(2L, 1L), monthRanked.map { it.track.id })
        assertEquals(listOf(4, 3), monthRanked.map { it.plays })

        val yearRanked = repo.buildMostPlayedTracks(
            allTracks,
            MostPlayedPeriod.YEAR,
            repo.aggregatePlayEvents(events, MostPlayedPeriod.YEAR, nowMs)
        )
        assertEquals(listOf(3L, 2L, 1L), yearRanked.map { it.track.id })
        assertEquals(listOf(5, 4, 3), yearRanked.map { it.plays })

        val allTimeRanked = repo.buildMostPlayedTracks(allTracks, MostPlayedPeriod.ALL, emptyMap())
        assertEquals(listOf(4L, 3L, 2L, 1L), allTimeRanked.map { it.track.id })
        assertEquals(listOf(120, 50, 10, 3), allTimeRanked.map { it.plays })
    }

    @Test
    fun albumGroupingSumsPeriodPlaysIgnoresUnknownAlbumsAndPicksTopCoverTrack() {
        val a1 = track(id = 1, title = "Intro", artist = "Artist A", album = "Midnight")
        val a2 = track(id = 2, title = "Chorus", artist = "Artist A", album = "midnight")
        val b1 = track(id = 3, title = "Solo", artist = "Artist B", album = "Daylight")
        val unknown = track(id = 4, title = "Loose", artist = "Artist C", album = "<unknown>")

        val counts = mapOf(1L to 2, 2L to 6, 3L to 5, 4L to 99)
        val albums = repo.buildMostPlayedAlbums(listOf(a1, a2, b1, unknown), MostPlayedPeriod.MONTH, counts)

        assertEquals(2, albums.size)
        assertEquals("midnight", albums[0].albumName.lowercase())
        assertEquals(8, albums[0].plays)
        assertEquals(2L, albums[0].coverTrack.id)

        assertEquals("Daylight", albums[1].albumName)
        assertEquals(5, albums[1].plays)
    }

    @Test
    fun artistRankingCreditsPrimaryArtistsAndLeavesFeatOnlyFallbackNull() {
        val primaryTrack = track(
            id = 1,
            title = "Collab",
            artist = "Main Artist; Guest Star",
            albumArtist = "Main Artist",
            album = "Main LP",
            playCount = 10
        )
        val duoTrack = track(
            id = 2,
            title = "Duo Track",
            artist = "First Lead; Second Lead",
            albumArtist = "First Lead; Second Lead",
            album = "Duo EP",
            playCount = 4
        )
        val soloFeatOnlyArtist = track(
            id = 3,
            title = "Guest Verse",
            artist = "Headliner; Only Feat",
            albumArtist = "Headliner",
            album = "Other Album",
            playCount = 20
        )

        val tracks = listOf(primaryTrack, duoTrack, soloFeatOnlyArtist)
        val periodCounts = mapOf(1L to 7, 2L to 4, 3L to 9)

        val artists = repo.buildMostPlayedArtists(tracks, MostPlayedPeriod.WEEK, periodCounts)

        assertEquals(listOf("Headliner", "Main Artist", "First Lead", "Second Lead"), artists.map { it.artistName })
        assertEquals(listOf(9, 7, 4, 4), artists.map { it.plays })
        assertNotNull(artists.first { it.artistName == "Main Artist" }.fallbackTrack)

        // When albumArtist is absent on a track where someone is the sole credited artist (no albumArtist),
        // primaryArtistsOf attributes the track to artist.take(1). And if an artist appears only in feat position
        // on a track that has another primary artist, it gets no album cover fallback from that other artist:
        val featOnlyFallbackCounts = mapOf(3L to 9)
        val featOnlyTrackNoAlbumArtist = track(
            id = 4,
            title = "No AlbumArtist Track",
            artist = "Feat Only",
            albumArtist = "",
            album = "",
            playCount = 1
        )
        // Contrast: Headliner owns track 3; Main Artist owns track 1
        assertEquals("Main Artist", artists[1].artistName)
        assertEquals(primaryTrack.id, artists[1].fallbackTrack?.id)

        // Verify fallback is null when artist has only feat appearances in primary+feat pool (via empty primary match)
        val strictlyFeat = track(
            id = 5,
            title = "Song",
            artist = "; Hidden Feat",
            albumArtist = "Owner",
            album = "Owner Album"
        )
        val strictlyFeatArtists = repo.buildMostPlayedArtists(
            listOf(strictlyFeat, featOnlyTrackNoAlbumArtist),
            MostPlayedPeriod.MONTH,
            featOnlyFallbackCounts + mapOf(5L to 3, 4L to 2)
        )
        assertEquals("Owner", strictlyFeatArtists.first().artistName)
        assertEquals(null, strictlyFeatArtists.find { it.artistName == "Hidden Feat" })
        // And when artist IS credited via fallback splitArtists (e.g. empty albumArtist & empty primary)
        // or primary check finds zero primary tracks, fallbackTrack is null:
        assertNull(
            repo.buildMostPlayedArtists(
                listOf(strictlyFeat),
                MostPlayedPeriod.MONTH,
                mapOf(5L to 3)
            ).find { it.artistName == "Hidden Feat" }
        )
    }
}
