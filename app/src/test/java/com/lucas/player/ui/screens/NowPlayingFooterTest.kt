package com.lucas.player.ui.screens

import android.net.Uri
import com.lucas.player.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class NowPlayingFooterTest {

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)

    private fun sampleTrack(
        formatTag: String = "",
        bitrateKbps: Int = 0,
        sampleRateHz: Int = 0,
        year: Int = 0,
        replayGainTrackGain: Float = 0f
    ) = Track(
        id = 1L,
        title = "Sample",
        artist = "Artist",
        album = "Album",
        durationMs = 200_000L,
        uri = dummyUri,
        path = "/music/sample.flac",
        folder = "Music",
        formatTag = formatTag,
        bitrateKbps = bitrateKbps,
        sampleRateHz = sampleRateHz,
        year = year,
        replayGainTrackGain = replayGainTrackGain
    )

    @Test
    fun `includes all technical fields when present`() {
        val track = sampleTrack(
            formatTag = "flac",
            bitrateKbps = 980,
            sampleRateHz = 44100,
            year = 2020,
            replayGainTrackGain = -4.25f
        )
        assertEquals(
            "FLAC  ·  980 kbps  ·  44.1 kHz  ·  2020  ·  RG -4.25 dB",
            formatTrackTechnicalFooter(track)
        )
    }

    @Test
    fun `omits optional fields when zero or blank`() {
        val track = sampleTrack(
            formatTag = "MP3",
            bitrateKbps = 0,
            sampleRateHz = 48000,
            year = 0,
            replayGainTrackGain = 0f
        )
        assertEquals("MP3  ·  48 kHz", formatTrackTechnicalFooter(track))
    }
}
