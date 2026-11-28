package com.lucas.player.data

import android.content.Context
import android.net.Uri
import com.lucas.player.BuildConfig
import com.lucas.player.model.HomeSectionId
import com.lucas.player.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupManifest(
    val backupVersion: Int = BackupManager.BACKUP_VERSION,
    val dbVersion: Int = MusicDatabase.DATABASE_VERSION,
    val exportedAtMs: Long,
    val appVersionName: String
)

data class BackupPlaylistEntry(
    val oldId: Long,
    val name: String,
    val createdMs: Long,
    val coverPath: String,
    val items: List<BackupTrackRef>
)

data class BackupTrackRef(
    val trackId: Long,
    val trackKey: String,
    val sortOrder: Int
)

data class BackupAlbumCollectionEntry(
    val id: Long,
    val name: String,
    val createdMs: Long,
    val albums: List<String>
)

data class BackupTrackStatEntry(
    val trackId: Long,
    val trackKey: String,
    val playCount: Int,
    val lastPlayedMs: Long,
    val rating: Int
)

data class BackupPlayEventEntry(
    val trackId: Long,
    val trackKey: String,
    val playedAtMs: Long
)

data class BackupPreferencesEntry(
    val homeSectionOrder: List<String>,
    val hiddenHomeSections: List<String>,
    val pauseGifs: Boolean,
    val pinnedGroups: List<String>
)

data class BackupPayload(
    val albumMeta: List<MusicDatabase.AlbumMeta>,
    val artistStyles: List<MusicDatabase.ArtistStyle>,
    val customMedia: List<MusicDatabase.CustomMedia>,
    val albumCollections: List<BackupAlbumCollectionEntry>,
    val playlists: List<BackupPlaylistEntry>,
    val trackStats: List<BackupTrackStatEntry>,
    val playEvents: List<BackupPlayEventEntry>,
    val preferences: BackupPreferencesEntry
)

data class BackupSummary(
    val playlistsCount: Int,
    val albumCollectionsCount: Int,
    val albumNotesCount: Int,
    val artistStylesCount: Int,
    val customMediaCount: Int,
    val trackStatsCount: Int,
    val playEventsCount: Int,
    val mediaFilesCount: Int
)

object BackupManager {

    const val BACKUP_VERSION = 1
    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_DATA = "data.json"

    val MEDIA_DIRS = listOf(
        "artist_covers",
        "artist_logos",
        "album_covers",
        "playlist_covers",
        "frozen_frames"
    )

    fun defaultBackupFileName(nowMs: Long = System.currentTimeMillis()): String {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMs))
        return "auro-backup-$dateStr.zip"
    }

    fun normalizeTrackPart(value: String): String {
        return value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }

    fun buildTrackKey(title: String, artist: String, album: String, durationMs: Long): String {
        val durationSec = (durationMs / 1000L).coerceAtLeast(0L)
        return "${normalizeTrackPart(title)}|${normalizeTrackPart(artist)}|${normalizeTrackPart(album)}|$durationSec"
    }

    fun buildTrackKey(track: Track): String {
        return buildTrackKey(track.title, track.artist, track.album, track.durationMs)
    }

    fun buildTrackResolver(tracks: List<Track>): (savedTrackId: Long, savedTrackKey: String) -> Long? {
        val idSet = HashSet<Long>(tracks.size)
        val keyToId = HashMap<String, Long>(tracks.size)
        for (t in tracks) {
            idSet.add(t.id)
            val key = buildTrackKey(t)
            if (key.isNotEmpty() && !keyToId.containsKey(key)) {
                keyToId[key] = t.id
            }
        }
        return { savedId, savedKey ->
            when {
                savedId != 0L && savedId in idSet -> savedId
                savedKey.isNotBlank() -> keyToId[savedKey]
                else -> null
            }
        }
    }

    /**
     * Valida e resolve o caminho de uma entrada do .zip contra ataques Zip Slip,
     * garantindo que o arquivo de destino permaneça dentro do [rootDir].
     */
    @Throws(SecurityException::class)
    fun resolveSafeZipDestination(rootDir: File, entryName: String): File {
        val normalized = entryName.replace('\\', '/').trimStart('/')
        if (normalized.isBlank() || normalized.contains('\u0000')) {
            throw SecurityException("Nome de entrada inválido no backup: $entryName")
        }
        val segments = normalized.split('/')
        if (segments.any { it == ".." || it == "." || it.isEmpty() }) {
            throw SecurityException("Caminho inválido no backup (Zip Slip): $entryName")
        }
        val canonicalRoot = rootDir.canonicalFile
        val resolved = File(canonicalRoot, normalized).canonicalFile
        val rootPrefix = canonicalRoot.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
        if (!resolved.path.startsWith(rootPrefix)) {
            throw SecurityException("Entrada fora do diretório permitido (Zip Slip): $entryName")
        }
        return resolved
    }

    fun toRelativeMediaPath(filesDir: File, rawPath: String): String {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) return ""
        val asUnix = trimmed.replace('\\', '/')
        for (dir in MEDIA_DIRS) {
            if (asUnix.startsWith("$dir/")) return asUnix
        }
        return try {
            val rootPath = filesDir.canonicalPath.replace('\\', '/').trimEnd('/')
            val filePath = File(trimmed).canonicalPath.replace('\\', '/')
            if (filePath.startsWith("$rootPath/")) {
                val rel = filePath.removePrefix("$rootPath/")
                if (MEDIA_DIRS.any { rel.startsWith("$it/") }) rel else ""
            } else {
                for (dir in MEDIA_DIRS) {
                    val marker = "/$dir/"
                    val idx = filePath.lastIndexOf(marker)
                    if (idx >= 0) {
                        return filePath.substring(idx + 1)
                    }
                }
                ""
            }
        } catch (_: Throwable) {
            ""
        }
    }

    fun fromRelativeMediaPath(filesDir: File, relativeOrPath: String): String {
        val trimmed = relativeOrPath.trim()
        if (trimmed.isEmpty()) return ""
        val rel = toRelativeMediaPath(filesDir, trimmed)
        if (rel.isEmpty()) return ""
        return try {
            resolveSafeZipDestination(filesDir, rel).absolutePath
        } catch (_: Throwable) {
            ""
        }
    }

    suspend fun exportBackup(context: Context, targetUri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val appCtx = context.applicationContext
            val repo = LibraryRepository.getInstance(appCtx)
            val prefs = PreferencesManager(appCtx)
            val filesDir = appCtx.filesDir

            val manifest = BackupManifest(
                backupVersion = BACKUP_VERSION,
                dbVersion = MusicDatabase.DATABASE_VERSION,
                exportedAtMs = System.currentTimeMillis(),
                appVersionName = BuildConfig.VERSION_NAME
            )

            val trackKeysById = HashMap<Long, String>()
            for (cached in repo.getCachedTracksForBackup(appCtx)) {
                trackKeysById[cached.trackId] = buildTrackKey(cached.title, cached.artist, cached.album, cached.durationMs)
            }
            for (t in repo.getAllTracks()) {
                trackKeysById[t.id] = buildTrackKey(t)
            }

            val albumMetaList = repo.getAllAlbumMeta().values.map { meta ->
                meta.copy(mediaPath = toRelativeMediaPath(filesDir, meta.mediaPath))
            }
            val artistStyleList = repo.getAllArtistStyles().values.map { style ->
                style.copy(logoPath = toRelativeMediaPath(filesDir, style.logoPath))
            }
            val customMediaList = repo.getAllCustomMedia().values.map { cm ->
                cm.copy(
                    gifPath = toRelativeMediaPath(filesDir, cm.gifPath),
                    gifFrozenFramePath = toRelativeMediaPath(filesDir, cm.gifFrozenFramePath),
                    staticImagePath = toRelativeMediaPath(filesDir, cm.staticImagePath)
                )
            }
            val collectionsList = repo.getAlbumCollections().map { col ->
                BackupAlbumCollectionEntry(
                    id = col.id,
                    name = col.name,
                    createdMs = col.createdMs,
                    albums = repo.getAlbumsForCollection(col.id)
                )
            }
            val playlistsList = repo.getUserPlaylists().map { pl ->
                val rawIds = repo.getRawPlaylistTrackIds(appCtx, pl.id)
                BackupPlaylistEntry(
                    oldId = pl.id,
                    name = pl.name,
                    createdMs = pl.createdMs,
                    coverPath = toRelativeMediaPath(filesDir, pl.coverPath),
                    items = rawIds.mapIndexed { idx, tid ->
                        BackupTrackRef(
                            trackId = tid,
                            trackKey = trackKeysById[tid].orEmpty(),
                            sortOrder = idx
                        )
                    }
                )
            }
            val statsList = repo.getAllStatsForBackup(appCtx).values.map { st ->
                BackupTrackStatEntry(
                    trackId = st.trackId,
                    trackKey = trackKeysById[st.trackId].orEmpty(),
                    playCount = st.playCount,
                    lastPlayedMs = st.lastPlayedMs,
                    rating = st.rating
                )
            }
            val eventsList = repo.getAllPlayEventsForBackup(appCtx).map { ev ->
                BackupPlayEventEntry(
                    trackId = ev.trackId,
                    trackKey = trackKeysById[ev.trackId].orEmpty(),
                    playedAtMs = ev.playedAtMs
                )
            }
            val prefsEntry = BackupPreferencesEntry(
                homeSectionOrder = prefs.getHomeSectionOrder().map { it.name },
                hiddenHomeSections = prefs.getHiddenHomeSections().map { it.name },
                pauseGifs = prefs.isPauseGifsEnabled(),
                pinnedGroups = prefs.getPinnedGroups().toList().sorted()
            )

            val payload = BackupPayload(
                albumMeta = albumMetaList,
                artistStyles = artistStyleList,
                customMedia = customMediaList,
                albumCollections = collectionsList,
                playlists = playlistsList,
                trackStats = statsList,
                playEvents = eventsList,
                preferences = prefsEntry
            )

            val outStream = appCtx.contentResolver.openOutputStream(targetUri)
                ?: throw IllegalStateException("Não foi possível abrir o arquivo de destino")

            outStream.use { stream ->
                exportToStream(manifest, payload, filesDir, stream)
            }
        }
    }

    fun exportToStream(
        manifest: BackupManifest,
        payload: BackupPayload,
        filesDir: File,
        outputStream: OutputStream
    ): BackupSummary {
        var copiedMediaCount = 0
        ZipOutputStream(outputStream.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(serializeManifest(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ENTRY_DATA))
            zip.write(serializePayload(payload).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for (dirName in MEDIA_DIRS) {
                val dir = File(filesDir, dirName)
                if (!dir.exists() || !dir.isDirectory) continue
                val children = dir.walkTopDown().filter { it.isFile }.toList()
                for (file in children) {
                    val rel = file.relativeTo(filesDir).path.replace('\\', '/')
                    zip.putNextEntry(ZipEntry(rel))
                    file.inputStream().use { input ->
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                    copiedMediaCount++
                }
            }
        }
        return BackupSummary(
            playlistsCount = payload.playlists.size,
            albumCollectionsCount = payload.albumCollections.size,
            albumNotesCount = payload.albumMeta.count { it.notes.isNotBlank() },
            artistStylesCount = payload.artistStyles.size,
            customMediaCount = payload.customMedia.size,
            trackStatsCount = payload.trackStats.size,
            playEventsCount = payload.playEvents.size,
            mediaFilesCount = copiedMediaCount
        )
    }

    suspend fun importBackup(
        context: Context,
        sourceUri: Uri,
        currentTracks: List<Track>
    ): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val appCtx = context.applicationContext
            val repo = LibraryRepository.getInstance(appCtx)
            val prefs = PreferencesManager(appCtx)
            val filesDir = appCtx.filesDir

            val tracksForMatch = currentTracks.ifEmpty { repo.getAllTracks() }
            val inStream = appCtx.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Não foi possível abrir o arquivo de backup")

            inStream.use { stream ->
                importFromStream(
                    inputStream = stream,
                    filesDir = filesDir,
                    currentTracks = tracksForMatch,
                    applySnapshot = { snapshot -> repo.restoreBackupSnapshot(appCtx, snapshot) },
                    applyPreferences = { prefEntry -> restorePreferences(prefs, prefEntry) }
                )
            }
        }
    }

    fun importFromStream(
        inputStream: InputStream,
        filesDir: File,
        currentTracks: List<Track>,
        applySnapshot: (MusicDatabase.BackupRestoreSnapshot) -> Unit,
        applyPreferences: (BackupPreferencesEntry) -> Unit
    ): BackupSummary {
        val stagingDir = File(filesDir, ".backup_staging_${System.nanoTime()}")
        stagingDir.mkdirs()
        try {
            var manifestJson: String? = null
            var dataJson: String? = null
            val stagedMediaRelPaths = mutableListOf<String>()

            ZipInputStream(inputStream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val rawName = entry.name
                    val normalized = rawName.replace('\\', '/').trimStart('/')
                    if (!entry.isDirectory) {
                        when (normalized) {
                            ENTRY_MANIFEST -> manifestJson = readZipEntryText(zip)
                            ENTRY_DATA -> dataJson = readZipEntryText(zip)
                            else -> {
                                val topSegment = normalized.substringBefore('/')
                                if (topSegment in MEDIA_DIRS) {
                                    val stagedTarget = resolveSafeZipDestination(stagingDir, normalized)
                                    stagedTarget.parentFile?.mkdirs()
                                    stagedTarget.outputStream().use { out ->
                                        zip.copyTo(out)
                                    }
                                    stagedMediaRelPaths.add(normalized)
                                } else {
                                    // Ainda valida mesmo entradas desconhecidas contra Zip Slip
                                    resolveSafeZipDestination(filesDir, rawName)
                                }
                            }
                        }
                    } else {
                        resolveSafeZipDestination(filesDir, rawName)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val manifestRaw = manifestJson ?: throw IllegalArgumentException("manifest.json ausente no arquivo de backup")
            val manifest = deserializeManifest(manifestRaw)
            if (manifest.backupVersion < 1 || manifest.backupVersion > BACKUP_VERSION) {
                throw IllegalArgumentException("Versão de backup não suportada: ${manifest.backupVersion}")
            }

            val dataRaw = dataJson ?: throw IllegalArgumentException("data.json ausente no arquivo de backup")
            val payload = deserializePayload(dataRaw)

            // Limpa mídias anteriores nos diretórios gerenciados e move mídias validadas do staging
            for (dirName in MEDIA_DIRS) {
                val targetDir = File(filesDir, dirName)
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
            }
            for (rel in stagedMediaRelPaths) {
                val src = resolveSafeZipDestination(stagingDir, rel)
                val dest = resolveSafeZipDestination(filesDir, rel)
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
            }

            val resolver = buildTrackResolver(currentTracks)

            val restoredAlbumMeta = payload.albumMeta.map { meta ->
                meta.copy(mediaPath = fromRelativeMediaPath(filesDir, meta.mediaPath))
            }
            val restoredArtistStyles = payload.artistStyles.map { style ->
                style.copy(logoPath = fromRelativeMediaPath(filesDir, style.logoPath))
            }
            val restoredCustomMedia = payload.customMedia.map { cm ->
                cm.copy(
                    gifPath = fromRelativeMediaPath(filesDir, cm.gifPath),
                    gifFrozenFramePath = fromRelativeMediaPath(filesDir, cm.gifFrozenFramePath),
                    staticImagePath = fromRelativeMediaPath(filesDir, cm.staticImagePath)
                )
            }
            val restoredCollections = payload.albumCollections.map { col ->
                MusicDatabase.RestoredAlbumCollection(
                    name = col.name,
                    createdMs = col.createdMs,
                    albumNames = col.albums
                )
            }
            val restoredPlaylists = payload.playlists.map { pl ->
                val resolvedTrackIds = pl.items
                    .sortedBy { it.sortOrder }
                    .mapNotNull { ref -> resolver(ref.trackId, ref.trackKey) }
                MusicDatabase.RestoredPlaylist(
                    oldId = pl.oldId,
                    name = pl.name,
                    createdMs = pl.createdMs,
                    coverPath = fromRelativeMediaPath(filesDir, pl.coverPath),
                    trackIds = resolvedTrackIds
                )
            }
            val restoredStats = payload.trackStats.mapNotNull { stat ->
                val resolvedId = resolver(stat.trackId, stat.trackKey) ?: return@mapNotNull null
                MusicDatabase.TrackStat(
                    trackId = resolvedId,
                    playCount = stat.playCount,
                    lastPlayedMs = stat.lastPlayedMs,
                    rating = stat.rating
                )
            }
            val restoredEvents = payload.playEvents.mapNotNull { ev ->
                val resolvedId = resolver(ev.trackId, ev.trackKey) ?: return@mapNotNull null
                MusicDatabase.PlayEventRow(
                    trackId = resolvedId,
                    playedAtMs = ev.playedAtMs
                )
            }

            val snapshot = MusicDatabase.BackupRestoreSnapshot(
                albumMeta = restoredAlbumMeta,
                artistStyles = restoredArtistStyles,
                customMedia = restoredCustomMedia,
                albumCollections = restoredCollections,
                playlists = restoredPlaylists,
                trackStats = restoredStats,
                playEvents = restoredEvents
            )

            applySnapshot(snapshot)
            applyPreferences(payload.preferences)

            return BackupSummary(
                playlistsCount = restoredPlaylists.size,
                albumCollectionsCount = restoredCollections.size,
                albumNotesCount = restoredAlbumMeta.count { it.notes.isNotBlank() },
                artistStylesCount = restoredArtistStyles.size,
                customMediaCount = restoredCustomMedia.size,
                trackStatsCount = restoredStats.size,
                playEventsCount = restoredEvents.size,
                mediaFilesCount = stagedMediaRelPaths.size
            )
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun restorePreferences(prefs: PreferencesManager, entry: BackupPreferencesEntry) {
        val parsedOrder = entry.homeSectionOrder.mapNotNull { name ->
            HomeSectionId.entries.firstOrNull { it.name == name }
        }
        prefs.saveHomeSectionOrder(parsedOrder.ifEmpty { HomeSectionId.entries })

        val parsedHidden = entry.hiddenHomeSections.mapNotNull { name ->
            HomeSectionId.entries.firstOrNull { it.name == name }
        }.toSet()
        prefs.saveHiddenHomeSections(parsedHidden)

        prefs.setPauseGifsEnabled(entry.pauseGifs)
        prefs.setPinnedGroups(entry.pinnedGroups.toSet())
    }

    private fun readZipEntryText(zip: ZipInputStream): String {
        val buffer = ByteArrayOutputStream()
        zip.copyTo(buffer)
        return buffer.toString(Charsets.UTF_8.name())
    }

    // --- Serialização / Desserialização (org.json nativo no Android, com encoding compatível em testes JVM) ---

    fun serializeManifest(manifest: BackupManifest): String {
        return encodeJsonMap(manifestToMap(manifest))
    }

    fun deserializeManifest(json: String): BackupManifest {
        return mapToManifest(decodeJsonObject(json))
    }

    fun serializePayload(payload: BackupPayload): String {
        return encodeJsonMap(payloadToMap(payload))
    }

    fun deserializePayload(json: String): BackupPayload {
        return mapToPayload(decodeJsonObject(json))
    }

    private fun manifestToMap(m: BackupManifest): Map<String, Any?> = linkedMapOf(
        "backupVersion" to m.backupVersion,
        "dbVersion" to m.dbVersion,
        "exportedAtMs" to m.exportedAtMs,
        "appVersionName" to m.appVersionName
    )

    private fun mapToManifest(map: Map<String, Any?>): BackupManifest = BackupManifest(
        backupVersion = map.asInt("backupVersion", 0),
        dbVersion = map.asInt("dbVersion", MusicDatabase.DATABASE_VERSION),
        exportedAtMs = map.asLong("exportedAtMs", 0L),
        appVersionName = map.asString("appVersionName", "")
    )

    private fun payloadToMap(p: BackupPayload): Map<String, Any?> = linkedMapOf(
        "albumMeta" to p.albumMeta.map { m ->
            linkedMapOf(
                "albumKey" to m.albumKey,
                "mediaPath" to m.mediaPath,
                "isGif" to m.isGif,
                "notes" to m.notes
            )
        },
        "artistStyles" to p.artistStyles.map { s ->
            linkedMapOf(
                "artistKey" to s.artistKey,
                "fontId" to s.fontId,
                "logoPath" to s.logoPath,
                "colorMode" to s.colorMode,
                "customColorArgb" to s.customColorArgb
            )
        },
        "customMedia" to p.customMedia.map { c ->
            linkedMapOf(
                "entityType" to c.entityType,
                "entityKey" to c.entityKey,
                "gifPath" to c.gifPath,
                "gifFrameIndex" to c.gifFrameIndex,
                "gifFrozenFramePath" to c.gifFrozenFramePath,
                "staticImagePath" to c.staticImagePath,
                "pausedDisplayMode" to c.pausedDisplayMode
            )
        },
        "albumCollections" to p.albumCollections.map { col ->
            linkedMapOf(
                "id" to col.id,
                "name" to col.name,
                "createdMs" to col.createdMs,
                "albums" to col.albums
            )
        },
        "playlists" to p.playlists.map { pl ->
            linkedMapOf(
                "id" to pl.oldId,
                "name" to pl.name,
                "createdMs" to pl.createdMs,
                "coverPath" to pl.coverPath,
                "items" to pl.items.map { item ->
                    linkedMapOf(
                        "trackId" to item.trackId,
                        "trackKey" to item.trackKey,
                        "sortOrder" to item.sortOrder
                    )
                }
            )
        },
        "trackStats" to p.trackStats.map { st ->
            linkedMapOf(
                "trackId" to st.trackId,
                "trackKey" to st.trackKey,
                "playCount" to st.playCount,
                "lastPlayedMs" to st.lastPlayedMs,
                "rating" to st.rating
            )
        },
        "playEvents" to p.playEvents.map { ev ->
            linkedMapOf(
                "trackId" to ev.trackId,
                "trackKey" to ev.trackKey,
                "playedAtMs" to ev.playedAtMs
            )
        },
        "preferences" to linkedMapOf(
            "homeSectionOrder" to p.preferences.homeSectionOrder,
            "hiddenHomeSections" to p.preferences.hiddenHomeSections,
            "pauseGifs" to p.preferences.pauseGifs,
            "pinnedGroups" to p.preferences.pinnedGroups
        )
    )

    private fun mapToPayload(map: Map<String, Any?>): BackupPayload {
        val albumMeta = map.asObjectList("albumMeta").map { m ->
            MusicDatabase.AlbumMeta(
                albumKey = m.asString("albumKey"),
                mediaPath = m.asString("mediaPath"),
                isGif = m.asBoolean("isGif", false),
                notes = m.asString("notes")
            )
        }
        val artistStyles = map.asObjectList("artistStyles").map { s ->
            MusicDatabase.ArtistStyle(
                artistKey = s.asString("artistKey"),
                fontId = s.asString("fontId", "default"),
                logoPath = s.asString("logoPath"),
                colorMode = s.asString("colorMode", "AUTO"),
                customColorArgb = s.asInt("customColorArgb", 0)
            )
        }
        val customMedia = map.asObjectList("customMedia").map { c ->
            MusicDatabase.CustomMedia(
                entityType = c.asString("entityType"),
                entityKey = c.asString("entityKey"),
                gifPath = c.asString("gifPath"),
                gifFrameIndex = c.asInt("gifFrameIndex", 0),
                gifFrozenFramePath = c.asString("gifFrozenFramePath"),
                staticImagePath = c.asString("staticImagePath"),
                pausedDisplayMode = c.asString("pausedDisplayMode", MusicDatabase.PAUSED_MODE_GIF_FRAME)
            )
        }
        val albumCollections = map.asObjectList("albumCollections").map { col ->
            BackupAlbumCollectionEntry(
                id = col.asLong("id", 0L),
                name = col.asString("name"),
                createdMs = col.asLong("createdMs", 0L),
                albums = col.asStringList("albums")
            )
        }
        val playlists = map.asObjectList("playlists").map { pl ->
            BackupPlaylistEntry(
                oldId = pl.asLong("id", 0L),
                name = pl.asString("name"),
                createdMs = pl.asLong("createdMs", 0L),
                coverPath = pl.asString("coverPath"),
                items = pl.asObjectList("items").mapIndexed { idx, item ->
                    BackupTrackRef(
                        trackId = item.asLong("trackId", 0L),
                        trackKey = item.asString("trackKey"),
                        sortOrder = item.asInt("sortOrder", idx)
                    )
                }
            )
        }
        val trackStats = map.asObjectList("trackStats").map { st ->
            BackupTrackStatEntry(
                trackId = st.asLong("trackId", 0L),
                trackKey = st.asString("trackKey"),
                playCount = st.asInt("playCount", 0),
                lastPlayedMs = st.asLong("lastPlayedMs", 0L),
                rating = st.asInt("rating", 0)
            )
        }
        val playEvents = map.asObjectList("playEvents").map { ev ->
            BackupPlayEventEntry(
                trackId = ev.asLong("trackId", 0L),
                trackKey = ev.asString("trackKey"),
                playedAtMs = ev.asLong("playedAtMs", 0L)
            )
        }
        val prefsMap = map.asObject("preferences")
        val preferences = BackupPreferencesEntry(
            homeSectionOrder = prefsMap.asStringList("homeSectionOrder"),
            hiddenHomeSections = prefsMap.asStringList("hiddenHomeSections"),
            pauseGifs = prefsMap.asBoolean("pauseGifs", false),
            pinnedGroups = prefsMap.asStringList("pinnedGroups")
        )
        return BackupPayload(
            albumMeta = albumMeta,
            artistStyles = artistStyles,
            customMedia = customMedia,
            albumCollections = albumCollections,
            playlists = playlists,
            trackStats = trackStats,
            playEvents = playEvents,
            preferences = preferences
        )
    }

    private fun isOrgJsonWorking(): Boolean {
        return try {
            val obj = JSONObject()
            obj.put("ok", 1)
            obj.optInt("ok", 0) == 1 && obj.toString()?.contains("\"ok\"") == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun encodeJsonMap(map: Map<String, Any?>): String {
        if (isOrgJsonWorking()) {
            return (toOrgJsonValue(map) as JSONObject).toString()
        }
        return SimpleJson.encodeObject(map)
    }

    private fun decodeJsonObject(json: String): Map<String, Any?> {
        if (isOrgJsonWorking()) {
            val root = JSONObject(json)
            return orgJsonObjectToMap(root)
        }
        return SimpleJson.decodeObject(json)
    }

    private fun toOrgJsonValue(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> JSONObject().apply {
                for ((k, v) in value) {
                    if (k != null) put(k.toString(), toOrgJsonValue(v))
                }
            }
            is Iterable<*> -> JSONArray().apply {
                for (item in value) put(toOrgJsonValue(item))
            }
            else -> value
        }
    }

    private fun orgJsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = orgJsonValueToKotlin(obj.opt(k))
        }
        return out
    }

    private fun orgJsonValueToKotlin(v: Any?): Any? {
        return when (v) {
            null, JSONObject.NULL -> null
            is JSONObject -> orgJsonObjectToMap(v)
            is JSONArray -> List(v.length()) { idx -> orgJsonValueToKotlin(v.opt(idx)) }
            else -> v
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.asObject(key: String): Map<String, Any?> {
        return (this[key] as? Map<String, Any?>) ?: emptyMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.asObjectList(key: String): List<Map<String, Any?>> {
        val raw = this[key] as? List<*> ?: return emptyList()
        return raw.mapNotNull { it as? Map<String, Any?> }
    }

    private fun Map<String, Any?>.asStringList(key: String): List<String> {
        val raw = this[key] as? List<*> ?: return emptyList()
        return raw.mapNotNull { it?.toString() }
    }

    private fun Map<String, Any?>.asString(key: String, default: String = ""): String {
        return (this[key] as? String) ?: this[key]?.toString() ?: default
    }

    private fun Map<String, Any?>.asInt(key: String, default: Int = 0): Int {
        return when (val v = this[key]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }
    }

    private fun Map<String, Any?>.asLong(key: String, default: Long = 0L): Long {
        return when (val v = this[key]) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: default
            else -> default
        }
    }

    private fun Map<String, Any?>.asBoolean(key: String, default: Boolean = false): Boolean {
        return when (val v = this[key]) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            else -> default
        }
    }

    /** Fallback RFC-8259 minimal para ambientes JVM onde org.json do android.jar está stubbed. */
    private object SimpleJson {
        fun encodeObject(map: Map<String, Any?>): String {
            val sb = StringBuilder()
            appendValue(sb, map)
            return sb.toString()
        }

        private fun appendValue(sb: StringBuilder, value: Any?) {
            when (value) {
                null -> sb.append("null")
                is Boolean -> sb.append(if (value) "true" else "false")
                is Number -> sb.append(value.toString())
                is String -> appendString(sb, value)
                is Map<*, *> -> {
                    sb.append('{')
                    var first = true
                    for ((k, v) in value) {
                        if (k == null) continue
                        if (!first) sb.append(',')
                        first = false
                        appendString(sb, k.toString())
                        sb.append(':')
                        appendValue(sb, v)
                    }
                    sb.append('}')
                }
                is Iterable<*> -> {
                    sb.append('[')
                    var first = true
                    for (item in value) {
                        if (!first) sb.append(',')
                        first = false
                        appendValue(sb, item)
                    }
                    sb.append(']')
                }
                else -> appendString(sb, value.toString())
            }
        }

        private fun appendString(sb: StringBuilder, s: String) {
            sb.append('"')
            for (ch in s) {
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> if (ch.code < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
            sb.append('"')
        }

        @Suppress("UNCHECKED_CAST")
        fun decodeObject(src: String): Map<String, Any?> {
            val p = Parser(src)
            val root = p.readValue() as? Map<String, Any?> ?: throw IllegalArgumentException("JSON inválido")
            return root
        }

        private class Parser(private val src: String) {
            private var pos = 0

            fun readValue(): Any? {
                skipWs()
                if (pos >= src.length) throw IllegalArgumentException("JSON inesperado no fim")
                return when (val ch = src[pos]) {
                    '{' -> readObject()
                    '[' -> readArray()
                    '"' -> readString()
                    't' -> readLiteral("true", true)
                    'f' -> readLiteral("false", false)
                    'n' -> readLiteral("null", null)
                    '-', in '0'..'9' -> readNumber()
                    else -> throw IllegalArgumentException("Caractere inesperado em JSON: '$ch'")
                }
            }

            private fun readObject(): Map<String, Any?> {
                pos++ // '{'
                val map = LinkedHashMap<String, Any?>()
                skipWs()
                if (pos < src.length && src[pos] == '}') {
                    pos++
                    return map
                }
                while (true) {
                    skipWs()
                    val key = readString()
                    skipWs()
                    if (pos >= src.length || src[pos] != ':') throw IllegalArgumentException("Esperado ':' em JSON")
                    pos++
                    val value = readValue()
                    map[key] = value
                    skipWs()
                    if (pos >= src.length) throw IllegalArgumentException("Objeto JSON não fechado")
                    when (src[pos++]) {
                        ',' -> continue
                        '}' -> return map
                        else -> throw IllegalArgumentException("Esperado ',' ou '}' em JSON")
                    }
                }
            }

            private fun readArray(): List<Any?> {
                pos++ // '['
                val list = mutableListOf<Any?>()
                skipWs()
                if (pos < src.length && src[pos] == ']') {
                    pos++
                    return list
                }
                while (true) {
                    list.add(readValue())
                    skipWs()
                    if (pos >= src.length) throw IllegalArgumentException("Array JSON não fechado")
                    when (src[pos++]) {
                        ',' -> continue
                        ']' -> return list
                        else -> throw IllegalArgumentException("Esperado ',' ou ']' em JSON")
                    }
                }
            }

            private fun readString(): String {
                if (src[pos] != '"') throw IllegalArgumentException("Esperado '\"' em JSON")
                pos++
                val sb = StringBuilder()
                while (pos < src.length) {
                    val ch = src[pos++]
                    if (ch == '"') return sb.toString()
                    if (ch == '\\') {
                        if (pos >= src.length) throw IllegalArgumentException("Escape inválido")
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = src.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> sb.append(esc)
                        }
                    } else {
                        sb.append(ch)
                    }
                }
                throw IllegalArgumentException("String JSON não fechada")
            }

            private fun readLiteral(expected: String, result: Any?): Any? {
                if (!src.startsWith(expected, pos)) throw IllegalArgumentException("Literal JSON inválido")
                pos += expected.length
                return result
            }

            private fun readNumber(): Number {
                val start = pos
                while (pos < src.length && src[pos] in "-0123456789.eE+") pos++
                val raw = src.substring(start, pos)
                return if (raw.contains('.') || raw.contains('e') || raw.contains('E')) {
                    raw.toDouble()
                } else {
                    raw.toLong()
                }
            }

            private fun skipWs() {
                while (pos < src.length && src[pos].isWhitespace()) pos++
            }
        }
    }
}
