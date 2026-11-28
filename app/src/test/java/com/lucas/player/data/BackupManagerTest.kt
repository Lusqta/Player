package com.lucas.player.data

import android.net.Uri
import com.lucas.player.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class BackupManagerTest {

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)

    private fun track(
        id: Long,
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uri = dummyUri,
        path = "/music/$id.flac",
        folder = "Music"
    )

    @Test
    fun `json serialization and deserialization preserves full curation payload`() {
        val manifest = BackupManifest(
            backupVersion = BackupManager.BACKUP_VERSION,
            dbVersion = MusicDatabase.DATABASE_VERSION,
            exportedAtMs = 1_750_000_000_000L,
            appVersionName = "1.1.1"
        )
        val payload = BackupPayload(
            albumMeta = listOf(
                MusicDatabase.AlbumMeta(
                    albumKey = "discovery",
                    mediaPath = "album_covers/c_1.jpg",
                    isGif = false,
                    notes = "Classic 2001 house\n\"French Touch\""
                )
            ),
            artistStyles = listOf(
                MusicDatabase.ArtistStyle(
                    artistKey = "daft punk",
                    fontId = "editorial_serif",
                    logoPath = "artist_logos/dp.png",
                    colorMode = "MANUAL",
                    customColorArgb = -123456
                )
            ),
            customMedia = listOf(
                MusicDatabase.CustomMedia(
                    entityType = MusicDatabase.ENTITY_ARTIST,
                    entityKey = "daft punk",
                    gifPath = "artist_covers/dp.gif",
                    gifFrameIndex = 7,
                    gifFrozenFramePath = "frozen_frames/ff_1.jpg",
                    staticImagePath = "artist_covers/dp_static.jpg",
                    pausedDisplayMode = MusicDatabase.PAUSED_MODE_STATIC_IMAGE
                )
            ),
            albumCollections = listOf(
                BackupAlbumCollectionEntry(
                    id = 5L,
                    name = "Night Rotation",
                    createdMs = 1_700_000_000L,
                    albums = listOf("Discovery", "Homework")
                )
            ),
            playlists = listOf(
                BackupPlaylistEntry(
                    oldId = 10L,
                    name = "Favorites Mix",
                    createdMs = 1_700_000_100L,
                    coverPath = "playlist_covers/fav.jpg",
                    items = listOf(
                        BackupTrackRef(
                            trackId = 42L,
                            trackKey = "one more time|daft punk|discovery|320",
                            sortOrder = 0
                        )
                    )
                )
            ),
            trackStats = listOf(
                BackupTrackStatEntry(
                    trackId = 42L,
                    trackKey = "one more time|daft punk|discovery|320",
                    playCount = 19,
                    lastPlayedMs = 1_749_999_999L,
                    rating = 5
                )
            ),
            playEvents = listOf(
                BackupPlayEventEntry(
                    trackId = 42L,
                    trackKey = "one more time|daft punk|discovery|320",
                    playedAtMs = 1_749_999_000L
                )
            ),
            preferences = BackupPreferencesEntry(
                homeSectionOrder = listOf("TOP_ALBUMS", "QUICK_MIXES"),
                hiddenHomeSections = listOf("ARTISTS"),
                pauseGifs = true,
                pinnedGroups = listOf("ALBUMS_Discovery")
            )
        )

        val decodedManifest = BackupManager.deserializeManifest(BackupManager.serializeManifest(manifest))
        assertEquals(manifest, decodedManifest)

        val decodedPayload = BackupManager.deserializePayload(BackupManager.serializePayload(payload))
        assertEquals(payload, decodedPayload)
    }

    @Test
    fun `import remaps playlists stats and play events via trackKey when trackId changes`() {
        val sourceRoot = createTempDirectory("backup_src_").toFile()
        val targetRoot = createTempDirectory("backup_dst_").toFile()
        try {
            File(sourceRoot, "artist_covers").apply { mkdirs() }.let { dir ->
                File(dir, "dp.gif").writeBytes(byteArrayOf(71, 73, 70, 56, 57, 97))
            }
            File(sourceRoot, "frozen_frames").apply { mkdirs() }.let { dir ->
                File(dir, "ff_dp.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
            }

            val oldTrackA = track(101L, "Digital Love", "Daft Punk", "Discovery", 298_400L)
            val oldTrackMissing = track(102L, "Deleted Song", "Ghost", "Missing", 180_000L)
            val newTrackA = track(9001L, "  digital   love ", "DAFT PUNK", "discovery", 298_900L)

            val manifest = BackupManifest(
                backupVersion = BackupManager.BACKUP_VERSION,
                dbVersion = MusicDatabase.DATABASE_VERSION,
                exportedAtMs = 1_750_000_000_000L,
                appVersionName = "1.1.1"
            )
            val payload = BackupPayload(
                albumMeta = listOf(
                    MusicDatabase.AlbumMeta("discovery", "", false, "Vinyl edition notes")
                ),
                artistStyles = emptyList(),
                customMedia = listOf(
                    MusicDatabase.CustomMedia(
                        entityType = MusicDatabase.ENTITY_ARTIST,
                        entityKey = "daft punk",
                        gifPath = "artist_covers/dp.gif",
                        gifFrameIndex = 3,
                        gifFrozenFramePath = "frozen_frames/ff_dp.jpg",
                        staticImagePath = "",
                        pausedDisplayMode = MusicDatabase.PAUSED_MODE_GIF_FRAME
                    )
                ),
                albumCollections = listOf(
                    BackupAlbumCollectionEntry(1L, "Electronic", 100L, listOf("Discovery"))
                ),
                playlists = listOf(
                    BackupPlaylistEntry(
                        oldId = 3L,
                        name = "Late Night",
                        createdMs = 200L,
                        coverPath = "",
                        items = listOf(
                            BackupTrackRef(oldTrackA.id, BackupManager.buildTrackKey(oldTrackA), 0),
                            BackupTrackRef(oldTrackMissing.id, BackupManager.buildTrackKey(oldTrackMissing), 1)
                        )
                    )
                ),
                trackStats = listOf(
                    BackupTrackStatEntry(oldTrackA.id, BackupManager.buildTrackKey(oldTrackA), 12, 555_000L, 4),
                    BackupTrackStatEntry(oldTrackMissing.id, BackupManager.buildTrackKey(oldTrackMissing), 3, 111_000L, 2)
                ),
                playEvents = listOf(
                    BackupPlayEventEntry(oldTrackA.id, BackupManager.buildTrackKey(oldTrackA), 555_000L),
                    BackupPlayEventEntry(oldTrackMissing.id, BackupManager.buildTrackKey(oldTrackMissing), 111_000L)
                ),
                preferences = BackupPreferencesEntry(
                    homeSectionOrder = listOf("QUICK_MIXES"),
                    hiddenHomeSections = emptyList(),
                    pauseGifs = true,
                    pinnedGroups = listOf("ALBUMS_Discovery")
                )
            )

            val zipBuffer = ByteArrayOutputStream()
            val exportSummary = BackupManager.exportToStream(manifest, payload, sourceRoot, zipBuffer)
            assertEquals(2, exportSummary.mediaFilesCount)

            var restoredSnapshot: MusicDatabase.BackupRestoreSnapshot? = null
            var restoredPrefs: BackupPreferencesEntry? = null

            val importSummary = BackupManager.importFromStream(
                inputStream = ByteArrayInputStream(zipBuffer.toByteArray()),
                filesDir = targetRoot,
                currentTracks = listOf(newTrackA),
                applySnapshot = { restoredSnapshot = it },
                applyPreferences = { restoredPrefs = it }
            )

            assertEquals(1, importSummary.playlistsCount)
            assertEquals(1, importSummary.trackStatsCount)
            assertEquals(1, importSummary.playEventsCount)
            assertEquals(2, importSummary.mediaFilesCount)

            val snap = restoredSnapshot!!
            assertEquals(listOf(9001L), snap.playlists.single().trackIds)
            assertEquals(9001L, snap.trackStats.single().trackId)
            assertEquals(4, snap.trackStats.single().rating)
            assertEquals(9001L, snap.playEvents.single().trackId)
            assertEquals(
                File(targetRoot, "artist_covers/dp.gif").absolutePath,
                snap.customMedia.single().gifPath
            )
            assertTrue(File(targetRoot, "artist_covers/dp.gif").exists())
            assertTrue(File(targetRoot, "frozen_frames/ff_dp.jpg").exists())
            assertTrue(restoredPrefs!!.pauseGifs)
        } finally {
            sourceRoot.deleteRecursively()
            targetRoot.deleteRecursively()
        }
    }

    @Test
    fun `rejects zip slip traversal entries before touching target directory`() {
        val tempRoot = createTempDirectory("backup_zipslip_").toFile()
        val filesDir = File(tempRoot, "files").apply { mkdirs() }
        val outsideFile = File(tempRoot, "pwned.txt")
        try {
            var directBlocked = false
            try {
                BackupManager.resolveSafeZipDestination(filesDir, "../../pwned.txt")
            } catch (_: SecurityException) {
                directBlocked = true
            }
            assertTrue(directBlocked)

            val evilZip = ByteArrayOutputStream()
            ZipOutputStream(evilZip).use { zos ->
                zos.putNextEntry(ZipEntry("artist_covers/../../pwned.txt"))
                zos.write("malicious".toByteArray())
                zos.closeEntry()
            }

            var zipBlocked = false
            try {
                BackupManager.importFromStream(
                    inputStream = ByteArrayInputStream(evilZip.toByteArray()),
                    filesDir = filesDir,
                    currentTracks = emptyList(),
                    applySnapshot = {},
                    applyPreferences = {}
                )
            } catch (_: SecurityException) {
                zipBlocked = true
            }
            assertTrue(zipBlocked)
            assertFalse(outsideFile.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
