package com.lucas.player.ui.screens

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.data.CoverArtLoader
import com.lucas.player.data.LibraryRepository
import com.lucas.player.data.MusicDatabase
import com.lucas.player.model.*
import com.lucas.player.model.formatArtistsDisplay
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TELA PRINCIPAL DE ARTISTA (Editorial Dark OLED - Estilo Apple Music)
 * - Fundo em degradê proporcional à altura rolável completa (cor viva -> #050505)
 * - Hero Full-Bleed (380dp) com blur gradiente incolor progressivo (API 31+) e fade sem bordas
 * - Identidade centralizada (fonte editorial selecionada ou logo PNG transparente)
 * - Menu •••: Alterar foto/GIF, Personalizar identidade, Cor da página e Remover mídia
 */
private data class ArtistProfileData(
    val top5Tracks: List<Track> = emptyList(),
    val releases: List<ArtistRelease> = emptyList(),
    val carouselReleases: List<ArtistRelease> = emptyList(),
    val primaryTracks: List<Track> = emptyList(),
    val featTracks: List<Track> = emptyList(),
    val fallbackTrack: Track? = null
)

private val ARTIST_FONT_IDS = listOf("default", "manrope", "serif", "mono_caps", "condensed")

private val ARTIST_PRESET_COLORS = listOf(
    0xFFD72638.toInt(),
    0xFFE84855.toInt(),
    0xFFD97706.toInt(),
    0xFF1D4ED8.toInt(),
    0xFF4F46E5.toInt(),
    0xFF7C3AED.toInt(),
    0xFF0F766E.toInt(),
    0xFF15803D.toInt(),
    0xFFBE185D.toInt(),
    0xFF78350F.toInt()
)

private fun artistIdentityTextStyle(fontId: String): TextStyle {
    return when (fontId) {
        "manrope" -> TextStyle(
            fontFamily = ManropeFontFamily,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            color = AuroColor.TextPrimary
        )
        "serif" -> TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 31.sp,
            lineHeight = 37.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            color = AuroColor.TextPrimary
        )
        "mono_caps" -> TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 23.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.6.sp,
            color = AuroColor.TextPrimary
        )
        "condensed" -> TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 28.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1.1).sp,
            color = AuroColor.TextPrimary
        )
        else -> TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            color = AuroColor.TextPrimary
        )
    }
}

private fun formatArtistIdentityText(artistName: String, fontId: String): String {
    val display = formatArtistsDisplay(artistName, artistName)
    return if (fontId == "mono_caps" || fontId == "condensed") {
        display.uppercase()
    } else {
        display
    }
}

@Composable
fun AuroArtistScreen(
    artistName: String,
    onBack: () -> Unit,
    onNavigateToTracks: () -> Unit,
    onNavigateToDiscography: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onTrackMenuClick: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    currentPlayingId: Long,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onChangeMedia: () -> Unit = {},
    onRemoveMedia: () -> Unit = {},
    customMediaPath: String? = null,
    isGif: Boolean = false,
    artistMediaVersion: Int = 0,
    customMedia: MusicDatabase.CustomMedia? = null,
    customMediaMap: Map<String, MusicDatabase.CustomMedia> = emptyMap(),
    customMediaVersion: Int = 0,
    artistStyle: MusicDatabase.ArtistStyle? = null,
    artistStyleVersion: Int = 0,
    onUpdateArtistFont: (String) -> Unit = {},
    onPickArtistLogo: () -> Unit = {},
    onRemoveArtistLogo: () -> Unit = {},
    onUpdateArtistColorMode: (String, Int) -> Unit = { _, _ -> },
    libraryVersion: Int = 0,
    albumMetaMap: Map<String, MusicDatabase.AlbumMeta> = emptyMap(),
    albumMediaVersion: Int = 0,
    errorTrackIds: Set<Long> = emptySet(),
    showFileFormat: Boolean = true,
    libraryRepository: LibraryRepository = LibraryRepository.getInstance(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val bottomInset = LocalAuroBottomInset.current
    val listState = rememberLazyListState()

    val resolvedAutoMediaPath = remember(customMedia, customMediaPath) {
        customMedia?.staticImagePath?.ifBlank {
            customMedia.gifFrozenFramePath.ifBlank { customMedia.gifPath }
        }?.ifBlank { null } ?: customMediaPath
    }
    val mediaStamp = remember(customMediaPath, resolvedAutoMediaPath, customMedia, artistMediaVersion, customMediaVersion) {
        val fileMod = resolvedAutoMediaPath?.let { java.io.File(it).takeIf(java.io.File::exists)?.lastModified() } ?: 0L
        fileMod xor ((artistMediaVersion + customMediaVersion).toLong() shl 16)
    }

    val effectiveStyle = artistStyle ?: MusicDatabase.ArtistStyle(
        artistKey = artistName.trim().lowercase()
    )
    var showIdentitySheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }

    val logoPath = effectiveStyle.logoPath.takeIf { it.isNotBlank() }
    val logoStamp = remember(logoPath, artistStyleVersion) {
        logoPath?.let { java.io.File(it).takeIf(java.io.File::exists)?.lastModified() } ?: 0L
    }
    var artistLogoBitmap by remember(logoPath, logoStamp) {
        mutableStateOf(CoverArtLoader.getCachedArtistLogo(logoPath))
    }
    LaunchedEffect(logoPath, logoStamp) {
        if (logoPath != null) {
            CoverArtLoader.loadArtistLogo(logoPath, 800) { bmp ->
                artistLogoBitmap = bmp
            }
        } else {
            artistLogoBitmap = null
        }
    }

    val profileData by produceState(
        initialValue = ArtistProfileData(),
        key1 = artistName,
        key2 = libraryVersion
    ) {
        value = withContext(Dispatchers.IO) {
            ArtistProfileData(
                top5Tracks = libraryRepository.getTop5TracksForArtist(artistName),
                releases = libraryRepository.getArtistReleases(artistName),
                carouselReleases = libraryRepository.getTop3CarouselReleases(artistName),
                primaryTracks = libraryRepository.getPrimaryTracksForArtist(artistName),
                featTracks = libraryRepository.getFeatTracksForArtist(artistName),
                fallbackTrack = libraryRepository.resolveArtistFallbackCoverTrack(artistName)
            )
        }
    }
    val top5Tracks = profileData.top5Tracks
    val releases = profileData.releases
    val carouselReleases = profileData.carouselReleases
    val primaryTracks = profileData.primaryTracks
    val featTracks = profileData.featTracks
    val fallbackTrack = profileData.fallbackTrack

    // Cor automática viva extraída da foto/1º frame do GIF ou fallbackTrack
    var autoSourceBitmap by remember(resolvedAutoMediaPath, mediaStamp, fallbackTrack?.id) {
        mutableStateOf<Bitmap?>(null)
    }
    LaunchedEffect(resolvedAutoMediaPath, mediaStamp, fallbackTrack?.id, effectiveStyle.colorMode) {
        if (effectiveStyle.colorMode == "AUTO") {
            val mediaFile = resolvedAutoMediaPath?.takeIf { it.isNotBlank() }
                ?.let { java.io.File(it) }
                ?.takeIf { it.exists() }
            if (mediaFile != null) {
                CoverArtLoader.loadFileBitmap(mediaFile.absolutePath, 400) { bmp ->
                    autoSourceBitmap = bmp
                }
            } else if (fallbackTrack != null) {
                CoverArtLoader.loadCover(context, fallbackTrack, 300) { bmp ->
                    autoSourceBitmap = bmp
                }
            } else {
                autoSourceBitmap = null
            }
        }
    }

    val autoExtractedColor by produceState(
        initialValue = AuroColor.Canvas,
        key1 = autoSourceBitmap,
        key2 = effectiveStyle.colorMode
    ) {
        value = if (effectiveStyle.colorMode == "AUTO" && autoSourceBitmap != null) {
            CoverGlow.extractVibrantPageColor(autoSourceBitmap)
        } else {
            AuroColor.Canvas
        }
    }

    val targetTopColor = when (effectiveStyle.colorMode) {
        "OLED_BLACK" -> AuroColor.Canvas
        "MANUAL" -> if (effectiveStyle.customColorArgb != 0) {
            GlowTone.clampManualPageColor(effectiveStyle.customColorArgb)
        } else {
            AuroColor.Canvas
        }
        else -> autoExtractedColor
    }
    val animatedTopColor by animateColorAsState(
        targetValue = targetTopColor,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "artist_page_top_color"
    )

    // Cálculo proporcional do degradê ao longo de toda a altura rolável
    val heroHeightPx = with(density) { 380.dp.toPx() }
    val defaultRowPx = with(density) { 64.dp.toPx() }
    val measuredItemHeights = remember(artistName) { mutableStateMapOf<Int, Int>() }
    val scrollMetrics by remember(listState, heroHeightPx, defaultRowPx) {
        derivedStateOf {
            val info = listState.layoutInfo
            info.visibleItemsInfo.forEach { item ->
                measuredItemHeights[item.index] = item.size
            }
            val viewportH = info.viewportSize.height.toFloat().coerceAtLeast(1f)
            val totalItems = info.totalItemsCount
            if (totalItems == 0) {
                Pair(0f, viewportH)
            } else {
                var totalH = 0f
                var currentScroll = 0f
                val firstIdx = listState.firstVisibleItemIndex
                for (i in 0 until totalItems) {
                    val h = measuredItemHeights[i]?.toFloat()
                        ?: if (i == 0) heroHeightPx else defaultRowPx
                    if (i < firstIdx) currentScroll += h
                    totalH += h
                }
                currentScroll += listState.firstVisibleItemScrollOffset
                val docH = maxOf(viewportH, totalH)
                val maxScroll = (docH - viewportH).coerceAtLeast(0f)
                val clampedScroll = if (!listState.canScrollForward && listState.canScrollBackward) {
                    maxScroll
                } else {
                    currentScroll.coerceIn(0f, maxScroll)
                }
                Pair(clampedScroll, docH)
            }
        }
    }

    val totalTracksCount = primaryTracks.size + featTracks.size
    val albumCount = releases.count { it.isAlbum }
    val singleCount = releases.count { !it.isAlbum }
    val subtitleText = buildString {
        append(formatTracksCount(totalTracksCount))
        if (albumCount > 0) {
            append(" • ")
            append(pluralStringResource(R.plurals.albums_count, albumCount, albumCount))
        } else if (singleCount > 0) {
            append(" • ")
            append(pluralStringResource(R.plurals.singles_count, singleCount, singleCount))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
            .drawBehind {
                val (scrollY, docH) = scrollMetrics
                val effectiveDocH = maxOf(size.height, docH)
                val midColor = lerp(animatedTopColor, AuroColor.Canvas, 0.55f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0.00f to animatedTopColor,
                        0.55f to midColor,
                        1.00f to AuroColor.Canvas,
                        startY = -scrollY,
                        endY = effectiveDocH - scrollY
                    )
                )
                // Véu escuro gradual e suave atrás da área de listas para leitura nítida sem corte brusco
                val veilTop = heroHeightPx - with(density) { 48.dp.toPx() } - scrollY
                if (veilTop < size.height) {
                    val clampedTop = veilTop.coerceAtLeast(0f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.14f),
                            1.0f to Color.Black.copy(alpha = 0.24f),
                            startY = veilTop,
                            endY = veilTop + with(density) { 160.dp.toPx() }
                        ),
                        topLeft = Offset(0f, clampedTop),
                        size = Size(size.width, (size.height - clampedTop).coerceAtLeast(0f))
                    )
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 80.dp)
        ) {
            // Hero Full-Bleed Editorial (380dp) com blur gradiente incolor
            item(key = "artist_hero") {
                ArtistHeroSection(
                    artistName = artistName,
                    subtitleText = subtitleText,
                    fontId = effectiveStyle.fontId,
                    logoBitmap = artistLogoBitmap,
                    customMediaPath = customMediaPath,
                    isGif = isGif,
                    customMedia = customMedia,
                    fallbackTrack = fallbackTrack,
                    mediaStamp = mediaStamp,
                    onPlayAll = {
                        AuroHaptics.click(view)
                        onPlayAll()
                    }
                )
            }

            // Seção 1: Principais músicas (Top 5)
            if (top5Tracks.isNotEmpty()) {
                item(key = "top_tracks_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuroSpace.ScreenMargin, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(AuroSquircle)
                                .clickable {
                                    AuroHaptics.click(view)
                                    onNavigateToTracks()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.artist_top_tracks),
                                style = AuroType.SectionTitle,
                                color = AuroColor.TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = AuroColor.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(CircleShape)
                                .background(AuroColor.PillMuted)
                                .clickable {
                                    AuroHaptics.click(view)
                                    onShuffleAll()
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = stringResource(R.string.shuffle_all),
                                    style = AuroType.MicroLabel.copy(
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                )
                            }
                        }
                    }
                }

                itemsIndexed(top5Tracks, key = { _, track -> "top_${track.id}" }) { index, track ->
                    AuroTrackRow(
                        track = track,
                        trackIndex = index,
                        isPlayingThis = track.id == currentPlayingId,
                        hasError = errorTrackIds.contains(track.id),
                        showFormatTag = showFileFormat,
                        showAlbumInSubtitle = releases.size > 1,
                        onClick = { onTrackClick(track, index) },
                        onLongClick = { onTrackLongClick(track) },
                        onMenuClick = { onTrackMenuClick(track) }
                    )
                }
            }

            // Seção 2: Álbuns / Discografia (Carrossel Horizontal de até 3)
            if (carouselReleases.isNotEmpty()) {
                item(key = "albums_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AuroHaptics.click(view)
                                    onNavigateToDiscography()
                                }
                                .padding(horizontal = AuroSpace.ScreenMargin, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.artist_albums),
                                style = AuroType.SectionTitle,
                                color = AuroColor.TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = AuroColor.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = AuroSpace.ScreenMargin),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                        ) {
                            items(carouselReleases, key = { it.albumName }) { release ->
                                val albumMeta = albumMetaMap[release.albumName.trim().lowercase()]
                                val albumCustomMedia = customMediaMap[
                                    MusicDatabase.customMediaLookupKey(MusicDatabase.ENTITY_ALBUM, release.albumName)
                                ]
                                Column(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clip(AuroSquircle)
                                        .clickable {
                                            AuroHaptics.click(view)
                                            onAlbumClick(release.albumName)
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(140.dp)
                                            .clip(AuroSquircle)
                                            .background(AuroColor.SurfaceCard)
                                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), AuroSquircle)
                                    ) {
                                        AlbumArtworkImage(
                                            albumName = release.albumName,
                                            customMediaPath = albumMeta?.mediaPath?.takeIf { it.isNotBlank() },
                                            isGif = albumMeta?.isGif ?: false,
                                            fallbackTrack = release.coverTrack,
                                            shape = AuroSquircle,
                                            mediaStamp = (albumMediaVersion + customMediaVersion).toLong(),
                                            targetSizeDp = 300,
                                            customMedia = albumCustomMedia,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = release.albumName,
                                        style = AuroType.CardTitle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                        color = AuroColor.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val typeLabel = if (release.isAlbum) {
                                        stringResource(R.string.group_subtitle_album)
                                    } else {
                                        stringResource(R.string.artist_singles_eps)
                                    }
                                    val desc = if (release.year > 0) "${release.year} • $typeLabel" else typeLabel
                                    Text(
                                        text = desc,
                                        style = AuroType.Caption,
                                        color = AuroColor.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top Bar transparente com pílulas escuras discretas para contraste sobre capas claras
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.38f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .clickable {
                        AuroHaptics.click(view)
                        onBack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPinned) Color.White.copy(alpha = 0.22f)
                            else Color.Black.copy(alpha = 0.38f)
                        )
                        .border(
                            0.5.dp,
                            if (isPinned) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.14f),
                            CircleShape
                        )
                        .clickable {
                            AuroHaptics.click(view)
                            onTogglePin()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(if (isPinned) R.string.action_unpin else R.string.action_pin),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                var showMenu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.38f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                            .clickable {
                                AuroHaptics.click(view)
                                showMenu = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.cd_more_options),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(AuroColor.SurfaceGrouped)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.artist_change_media),
                                    style = AuroType.Body,
                                    color = AuroColor.TextPrimary
                                )
                            },
                            onClick = {
                                showMenu = false
                                onChangeMedia()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.artist_customize_identity),
                                    style = AuroType.Body,
                                    color = AuroColor.TextPrimary
                                )
                            },
                            onClick = {
                                showMenu = false
                                showIdentitySheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.artist_page_color),
                                    style = AuroType.Body,
                                    color = AuroColor.TextPrimary
                                )
                            },
                            onClick = {
                                showMenu = false
                                showColorSheet = true
                            }
                        )
                        if (customMediaPath != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.artist_remove_media),
                                        style = AuroType.Body,
                                        color = AuroColor.Danger
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onRemoveMedia()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showIdentitySheet) {
            ArtistIdentitySheet(
                artistName = artistName,
                currentFontId = effectiveStyle.fontId,
                logoPath = logoPath,
                logoBitmap = artistLogoBitmap,
                accentColor = animatedTopColor,
                onSelectFont = onUpdateArtistFont,
                onPickLogo = {
                    showIdentitySheet = false
                    onPickArtistLogo()
                },
                onRemoveLogo = onRemoveArtistLogo,
                onDismiss = { showIdentitySheet = false }
            )
        }

        if (showColorSheet) {
            ArtistPageColorSheet(
                currentMode = effectiveStyle.colorMode,
                currentCustomArgb = effectiveStyle.customColorArgb,
                onSelectMode = onUpdateArtistColorMode,
                onDismiss = { showColorSheet = false }
            )
        }
    }
}

@Composable
private fun ArtistHeroSection(
    artistName: String,
    subtitleText: String,
    fontId: String,
    logoBitmap: Bitmap?,
    customMediaPath: String?,
    isGif: Boolean,
    customMedia: MusicDatabase.CustomMedia? = null,
    fallbackTrack: Track?,
    mediaStamp: Long,
    onPlayAll: () -> Unit
) {
    val density = LocalDensity.current
    val heroLayer = rememberGraphicsLayer()
    val isHeroRecorded = remember { booleanArrayOf(false) }
    val blurTopOffsetDp = 160.dp
    val blurTopOffsetPx = with(density) { blurTopOffsetDp.toPx() }
    val blurSoftPx = with(density) { 10.dp.toPx() }
    val blurMidPx = with(density) { 22.dp.toPx() }
    val blurStrongPx = with(density) { 36.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clipToBounds()
    ) {
        // Camada base: foto/GIF dissolvendo suavemente na metade inferior sem vinheta preta
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    if (!heroLayer.isReleased && size.width > 0f && size.height > 0f) {
                        val recorded = try {
                            heroLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            isHeroRecorded[0] = true
                            drawLayer(heroLayer)
                            true
                        } catch (_: Throwable) {
                            false
                        }
                        if (!recorded) {
                            drawContent()
                        }
                    } else {
                        drawContent()
                    }
                    // Máscara alfa na metade inferior para dissolver no degradê vivo da página
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.00f to Color.Black,
                            0.48f to Color.Black,
                            0.74f to Color.Black.copy(alpha = 0.56f),
                            0.90f to Color.Black.copy(alpha = 0.16f),
                            1.00f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            ArtistAvatarImage(
                artistName = artistName,
                customMediaPath = customMediaPath,
                isGif = isGif,
                fallbackTrack = fallbackTrack,
                shape = RectangleShape,
                mediaStamp = mediaStamp,
                customMedia = customMedia,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Blur gradiente incolor progressivo (API 31+) na metade inferior do Hero
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .align(Alignment.BottomCenter)
                    .clipToBounds()
            ) {
                HeroProgressiveBlurTier(
                    heroLayer = heroLayer,
                    isHeroReady = {
                        isHeroRecorded[0] &&
                            !heroLayer.isReleased &&
                            heroLayer.size.width > 0 &&
                            heroLayer.size.height > 0
                    },
                    topOffsetPx = blurTopOffsetPx,
                    blurRadiusPx = blurSoftPx,
                    maskBrush = remember {
                        Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.25f to Color.Black.copy(alpha = 0.70f),
                            0.65f to Color.Black.copy(alpha = 0.85f),
                            0.90f to Color.Black.copy(alpha = 0.25f),
                            1.00f to Color.Transparent
                        )
                    }
                )
                HeroProgressiveBlurTier(
                    heroLayer = heroLayer,
                    isHeroReady = {
                        isHeroRecorded[0] &&
                            !heroLayer.isReleased &&
                            heroLayer.size.width > 0 &&
                            heroLayer.size.height > 0
                    },
                    topOffsetPx = blurTopOffsetPx,
                    blurRadiusPx = blurMidPx,
                    maskBrush = remember {
                        Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.30f to Color.Black.copy(alpha = 0.45f),
                            0.70f to Color.Black.copy(alpha = 0.72f),
                            0.93f to Color.Black.copy(alpha = 0.18f),
                            1.00f to Color.Transparent
                        )
                    }
                )
                HeroProgressiveBlurTier(
                    heroLayer = heroLayer,
                    isHeroReady = {
                        isHeroRecorded[0] &&
                            !heroLayer.isReleased &&
                            heroLayer.size.width > 0 &&
                            heroLayer.size.height > 0
                    },
                    topOffsetPx = blurTopOffsetPx,
                    blurRadiusPx = blurStrongPx,
                    maskBrush = remember {
                        Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.75f to Color.Black.copy(alpha = 0.52f),
                            0.95f to Color.Black.copy(alpha = 0.12f),
                            1.00f to Color.Transparent
                        )
                    }
                )
            }
        }

        // Proteção sutil no topo para os ícones da barra de status
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.48f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Proteção sutil atrás do texto inferior para contraste garantido em capas claras
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.34f),
                            Color.Black.copy(alpha = 0.42f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Identidade centralizada no meio da área do blur gradiente + Play discreto à direita
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(185.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 76.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap.asImageBitmap(),
                        contentDescription = artistName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 72.dp)
                    )
                } else {
                    Text(
                        text = formatArtistIdentityText(artistName, fontId),
                        style = artistIdentityTextStyle(fontId).copy(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.65f),
                                offset = Offset(0f, 3f),
                                blurRadius = 10f
                            )
                        ),
                        color = AuroColor.TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitleText,
                    style = AuroType.Caption.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.65f),
                            offset = Offset(0f, 2f),
                            blurRadius = 6f
                        )
                    ),
                    color = Color.White.copy(alpha = 0.90f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = AuroSpace.ScreenMargin, bottom = 4.dp)
                    .border(0.75.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            ) {
                TranslucentPlayButton(
                    isPlaying = false,
                    size = 50.dp,
                    containerColor = Color.Black.copy(alpha = 0.52f),
                    onClick = onPlayAll
                )
            }
        }
    }
}

@Composable
private fun BoxScope.HeroProgressiveBlurTier(
    heroLayer: GraphicsLayer,
    isHeroReady: () -> Boolean,
    topOffsetPx: Float,
    blurRadiusPx: Float,
    maskBrush: Brush
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    Box(
        modifier = Modifier
            .matchParentSize()
            .clipToBounds()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = maskBrush,
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    renderEffect = BlurEffect(
                        radiusX = blurRadiusPx,
                        radiusY = blurRadiusPx,
                        edgeTreatment = TileMode.Clamp
                    )
                }
                .drawBehind {
                    if (!isHeroReady()) return@drawBehind
                    try {
                        clipRect(0f, 0f, size.width, size.height) {
                            translate(left = 0f, top = -topOffsetPx) {
                                drawLayer(heroLayer)
                            }
                        }
                    } catch (_: Throwable) {}
                }
        )
    }
}

@Composable
private fun ArtistIdentitySheet(
    artistName: String,
    currentFontId: String,
    logoPath: String?,
    logoBitmap: Bitmap?,
    accentColor: Color = Color(0xFF101013),
    onSelectFont: (String) -> Unit,
    onPickLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.BottomSheet
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = AuroColor.SurfaceGrouped,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.85f)
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = AuroSpace.ScreenMargin)
                        .padding(top = 12.dp, bottom = 20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.24f))
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.artist_customize_identity),
                        style = AuroType.SectionTitle,
                        color = AuroColor.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Prévia ao vivo com a cor da página do artista
                    Text(
                        text = stringResource(R.string.artist_identity_preview),
                        style = AuroType.MicroLabel,
                        color = AuroColor.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        lerp(accentColor, Color(0xFF141418), 0.22f),
                                        Color(0xFF101013)
                                    )
                                )
                            )
                            .border(0.75.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoBitmap != null) {
                            Image(
                                bitmap = logoBitmap.asImageBitmap(),
                                contentDescription = artistName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .heightIn(max = 64.dp)
                            )
                        } else {
                            Text(
                                text = formatArtistIdentityText(artistName, currentFontId),
                                style = artistIdentityTextStyle(currentFontId),
                                color = AuroColor.TextPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Botões de Logo PNG
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .clickable {
                                        AuroHaptics.click(view)
                                        onPickLogo()
                                    }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.artist_logo_upload),
                                    style = AuroType.Label.copy(color = Color.White, fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!logoPath.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AuroColor.Danger.copy(alpha = 0.16f))
                                        .clickable {
                                            AuroHaptics.click(view)
                                            onRemoveLogo()
                                        }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = null,
                                        tint = AuroColor.Danger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.artist_logo_remove),
                                        style = AuroType.Label.copy(color = AuroColor.Danger, fontWeight = FontWeight.Medium)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = stringResource(R.string.artist_font_section),
                            style = AuroType.MicroLabel,
                            color = AuroColor.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        ARTIST_FONT_IDS.forEach { fontId ->
                            val isSelected = fontId == currentFontId
                            val label = when (fontId) {
                                "manrope" -> stringResource(R.string.artist_font_manrope)
                                "serif" -> stringResource(R.string.artist_font_serif)
                                "mono_caps" -> stringResource(R.string.artist_font_mono_caps)
                                "condensed" -> stringResource(R.string.artist_font_condensed)
                                else -> stringResource(R.string.artist_font_default)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable {
                                        AuroHaptics.click(view)
                                        onSelectFont(fontId)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        style = AuroType.Caption,
                                        color = AuroColor.TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formatArtistIdentityText(artistName, fontId),
                                        style = artistIdentityTextStyle(fontId).copy(fontSize = 18.sp, lineHeight = 22.sp),
                                        color = AuroColor.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                AuroHaptics.confirm(view)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.done),
                            style = AuroType.Label.copy(color = Color.Black, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistPageColorSheet(
    currentMode: String,
    currentCustomArgb: Int,
    onSelectMode: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val initialHsl = remember(currentCustomArgb) {
        val base = if (currentCustomArgb != 0) currentCustomArgb else 0xFFD72638.toInt()
        val r = ((base shr 16) and 0xFF) / 255f
        val g = ((base shr 8) and 0xFF) / 255f
        val b = (base and 0xFF) / 255f
        GlowTone.rgbToHsl(r, g, b)
    }
    var hue by remember(currentCustomArgb) { mutableFloatStateOf(initialHsl[0] * 360f) }
    var sat by remember(currentCustomArgb) { mutableFloatStateOf(initialHsl[1].coerceIn(0f, 1f)) }
    var light by remember(currentCustomArgb) { mutableFloatStateOf(initialHsl[2].coerceIn(0.08f, 0.42f)) }
    val previewArgb = GlowTone.hslToArgb((hue / 360f).coerceIn(0f, 1f), sat.coerceIn(0f, 1f), light.coerceIn(0.04f, 0.42f))
    val previewColor = GlowTone.clampManualPageColor(previewArgb)

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.BottomSheet
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = AuroColor.SurfaceGrouped,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.85f)
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AuroSpace.ScreenMargin, vertical = 20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.artist_page_color),
                        style = AuroType.SectionTitle,
                        color = AuroColor.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val modes = listOf(
                        "AUTO" to stringResource(R.string.artist_color_mode_auto),
                        "OLED_BLACK" to stringResource(R.string.artist_color_mode_oled),
                        "MANUAL" to stringResource(R.string.artist_color_mode_manual)
                    )
                    modes.forEach { (mode, label) ->
                        val isSelected = currentMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    AuroHaptics.click(view)
                                    val argb = if (mode == "MANUAL") previewColor.toArgb() else currentCustomArgb
                                    onSelectMode(mode, argb)
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = AuroType.Body.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = AuroColor.TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.artist_color_presets),
                        style = AuroType.MicroLabel,
                        color = AuroColor.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ARTIST_PRESET_COLORS.take(5).forEach { argb ->
                            val clamped = GlowTone.clampManualPageColor(argb)
                            val selected = currentMode == "MANUAL" && currentCustomArgb == clamped.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(clamped)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.20f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        AuroHaptics.click(view)
                                        val hsl = GlowTone.rgbToHsl(clamped.red, clamped.green, clamped.blue)
                                        hue = hsl[0] * 360f
                                        sat = hsl[1]
                                        light = hsl[2]
                                        onSelectMode("MANUAL", clamped.toArgb())
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ARTIST_PRESET_COLORS.drop(5).forEach { argb ->
                            val clamped = GlowTone.clampManualPageColor(argb)
                            val selected = currentMode == "MANUAL" && currentCustomArgb == clamped.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(clamped)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.20f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        AuroHaptics.click(view)
                                        val hsl = GlowTone.rgbToHsl(clamped.red, clamped.green, clamped.blue)
                                        hue = hsl[0] * 360f
                                        sat = hsl[1]
                                        light = hsl[2]
                                        onSelectMode("MANUAL", clamped.toArgb())
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Seletor HSL livre
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${stringResource(R.string.artist_color_hue)} (${hue.toInt()}°)",
                            style = AuroType.Caption,
                            color = AuroColor.TextSecondary
                        )
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(previewColor)
                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        )
                    }
                    Slider(
                        value = hue,
                        onValueChange = {
                            hue = it
                            val nextColor = GlowTone.clampManualPageColor(
                                GlowTone.hslToArgb((it / 360f).coerceIn(0f, 1f), sat, light)
                            )
                            onSelectMode("MANUAL", nextColor.toArgb())
                        },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = previewColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                        )
                    )

                    Text(
                        text = "${stringResource(R.string.artist_color_saturation)} (${(sat * 100).toInt()}%)",
                        style = AuroType.Caption,
                        color = AuroColor.TextSecondary
                    )
                    Slider(
                        value = sat,
                        onValueChange = {
                            sat = it
                            val nextColor = GlowTone.clampManualPageColor(
                                GlowTone.hslToArgb((hue / 360f).coerceIn(0f, 1f), it, light)
                            )
                            onSelectMode("MANUAL", nextColor.toArgb())
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = previewColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                        )
                    )

                    Text(
                        text = "${stringResource(R.string.artist_color_lightness)} (${(light * 100).toInt()}%)",
                        style = AuroType.Caption,
                        color = AuroColor.TextSecondary
                    )
                    Slider(
                        value = light,
                        onValueChange = {
                            light = it
                            val nextColor = GlowTone.clampManualPageColor(
                                GlowTone.hslToArgb((hue / 360f).coerceIn(0f, 1f), sat, it)
                            )
                            onSelectMode("MANUAL", nextColor.toArgb())
                        },
                        valueRange = 0.06f..0.42f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = previewColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                AuroHaptics.confirm(view)
                                onSelectMode("MANUAL", previewColor.toArgb())
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.artist_color_apply),
                            style = AuroType.Label.copy(color = Color.Black, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

/**
 * TELA COMPLETA DE MÚSICAS DO ARTISTA
 * - Top Bar com nome do artista, contagem, Tocar tudo e Aleatório
 * - Filtro opcional: "Mostrar feats"
 * - Fast scroller alfabético quando > 15 faixas
 */
private data class ArtistTracksData(
    val primaryTracks: List<Track> = emptyList(),
    val featTracks: List<Track> = emptyList(),
    val loaded: Boolean = false
)

@Composable
fun AuroArtistTracksScreen(
    artistName: String,
    onBack: () -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onTrackMenuClick: (Track) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    currentPlayingId: Long,
    libraryVersion: Int = 0,
    errorTrackIds: Set<Long> = emptySet(),
    showFileFormat: Boolean = true,
    libraryRepository: LibraryRepository = LibraryRepository.getInstance(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val bottomInset = LocalAuroBottomInset.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val tracksData by produceState(
        initialValue = ArtistTracksData(),
        key1 = artistName,
        key2 = libraryVersion
    ) {
        value = withContext(Dispatchers.IO) {
            ArtistTracksData(
                primaryTracks = libraryRepository.getPrimaryTracksForArtist(artistName, SortMode.TITLE_ASC),
                featTracks = libraryRepository.getFeatTracksForArtist(artistName, SortMode.TITLE_ASC),
                loaded = true
            )
        }
    }
    val primaryTracks = tracksData.primaryTracks
    val featTracks = tracksData.featTracks

    var showFeats by remember(artistName) { mutableStateOf(false) }
    LaunchedEffect(artistName, tracksData.loaded) {
        if (tracksData.loaded && primaryTracks.isEmpty() && featTracks.isNotEmpty()) {
            showFeats = true
        }
    }

    val displayedTracks = remember(primaryTracks, featTracks, showFeats) {
        if (showFeats && featTracks.isNotEmpty()) {
            (primaryTracks + featTracks).distinctBy { it.id }.sortedBy { it.title.lowercase() }
        } else {
            primaryTracks
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        onBack()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = formatArtistsDisplay(artistName, artistName),
                        style = AuroType.SectionTitle.copy(fontSize = 18.sp),
                        color = AuroColor.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatTracksCount(displayedTracks.size),
                        style = AuroType.Caption,
                        color = AuroColor.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        onPlayAll(displayedTracks)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.play_all),
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        AuroHaptics.click(view)
                        onShuffleAll(displayedTracks)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.shuffle_all),
                        tint = AuroColor.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Filtro de participações (Feats)
            if (featTracks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuroSpace.ScreenMargin, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(if (showFeats) Color.White.copy(alpha = 0.18f) else AuroColor.PillMuted)
                            .clickable {
                                AuroHaptics.click(view)
                                showFeats = !showFeats
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${stringResource(R.string.artist_show_feats)} (${featTracks.size})",
                            style = AuroType.MicroLabel.copy(
                                fontSize = 12.sp,
                                fontWeight = if (showFeats) FontWeight.Bold else FontWeight.Normal,
                                color = if (showFeats) Color.White else Color.White.copy(alpha = 0.70f)
                            )
                        )
                    }
                }
            }

            // Lista de Faixas
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset + 80.dp)
            ) {
                itemsIndexed(displayedTracks, key = { _, track -> track.id }) { index, track ->
                    AuroTrackRow(
                        track = track,
                        trackIndex = index,
                        isPlayingThis = track.id == currentPlayingId,
                        hasError = errorTrackIds.contains(track.id),
                        showFormatTag = showFileFormat,
                        onClick = { onTrackClick(track, index) },
                        onLongClick = { onTrackLongClick(track) },
                        onMenuClick = { onTrackMenuClick(track) }
                    )
                }
            }
        }

        // FastScroller translúcido na margem direita
        if (displayedTracks.size > 15) {
            AuroAlphabetFastScroller(
                onLetterSelected = { letter ->
                    val targetIndex = displayedTracks.indexOfFirst { matchesFirstLetter(it.title, letter) }
                    if (targetIndex >= 0) {
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, bottom = bottomInset + 30.dp, end = 2.dp)
            )
        }
    }
}

/**
 * TELA DE DISCOGRAFIA DO ARTISTA
 * - Top Bar: "Discografia" • nome do artista
 * - Seção 1: Álbuns (Grid de 2 colunas, cronológico decrescente)
 * - Seção 2: Singles e EPs (Grid de 2 colunas, cronológico decrescente)
 */
@Composable
fun AuroArtistDiscographyScreen(
    artistName: String,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    libraryVersion: Int = 0,
    albumMetaMap: Map<String, MusicDatabase.AlbumMeta> = emptyMap(),
    albumMediaVersion: Int = 0,
    customMediaMap: Map<String, MusicDatabase.CustomMedia> = emptyMap(),
    customMediaVersion: Int = 0,
    libraryRepository: LibraryRepository = LibraryRepository.getInstance(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val bottomInset = LocalAuroBottomInset.current
    val listState = rememberLazyListState()

    val releases by produceState(
        initialValue = emptyList<ArtistRelease>(),
        key1 = artistName,
        key2 = libraryVersion
    ) {
        value = withContext(Dispatchers.IO) {
            libraryRepository.getArtistReleases(artistName)
        }
    }
    val albums = remember(releases) { releases.filter { it.isAlbum } }
    val singlesAndEps = remember(releases) { releases.filterNot { it.isAlbum } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    AuroHaptics.click(view)
                    onBack()
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = AuroColor.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.artist_discography),
                    style = AuroType.SectionTitle.copy(fontSize = 18.sp),
                    color = AuroColor.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatArtistsDisplay(artistName, artistName),
                    style = AuroType.Caption,
                    color = AuroColor.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AuroSpace.ScreenMargin,
                end = AuroSpace.ScreenMargin,
                top = 8.dp,
                bottom = bottomInset + 80.dp
            )
        ) {
            // Seção: Álbuns
            if (albums.isNotEmpty()) {
                item(key = "section_albums_header") {
                    Text(
                        text = "${stringResource(R.string.artist_albums)} (${albums.size})",
                        style = AuroType.SectionTitle,
                        color = AuroColor.TextPrimary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
                    )
                }

                val albumPairs = albums.chunked(2)
                items(albumPairs, key = { it.first().albumName }) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (album in pair) {
                            val albumMeta = albumMetaMap[album.albumName.trim().lowercase()]
                            val albumCustomMedia = customMediaMap[
                                MusicDatabase.customMediaLookupKey(MusicDatabase.ENTITY_ALBUM, album.albumName)
                            ]
                            Box(modifier = Modifier.weight(1f)) {
                                DiscographyCard(
                                    release = album,
                                    customMediaPath = albumMeta?.mediaPath?.takeIf { it.isNotBlank() },
                                    isGif = albumMeta?.isGif ?: false,
                                    albumMediaVersion = albumMediaVersion + customMediaVersion,
                                    customMedia = albumCustomMedia,
                                    onClick = {
                                        AuroHaptics.click(view)
                                        onAlbumClick(album.albumName)
                                    }
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Seção: Singles e EPs
            if (singlesAndEps.isNotEmpty()) {
                item(key = "section_singles_header") {
                    Text(
                        text = "${stringResource(R.string.artist_singles_eps)} (${singlesAndEps.size})",
                        style = AuroType.SectionTitle,
                        color = AuroColor.TextPrimary,
                        modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
                    )
                }

                val singlePairs = singlesAndEps.chunked(2)
                items(singlePairs, key = { it.first().albumName }) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (release in pair) {
                            val albumMeta = albumMetaMap[release.albumName.trim().lowercase()]
                            val albumCustomMedia = customMediaMap[
                                MusicDatabase.customMediaLookupKey(MusicDatabase.ENTITY_ALBUM, release.albumName)
                            ]
                            Box(modifier = Modifier.weight(1f)) {
                                DiscographyCard(
                                    release = release,
                                    customMediaPath = albumMeta?.mediaPath?.takeIf { it.isNotBlank() },
                                    isGif = albumMeta?.isGif ?: false,
                                    albumMediaVersion = albumMediaVersion + customMediaVersion,
                                    customMedia = albumCustomMedia,
                                    onClick = {
                                        AuroHaptics.click(view)
                                        onAlbumClick(release.albumName)
                                    }
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscographyCard(
    release: ArtistRelease,
    customMediaPath: String? = null,
    isGif: Boolean = false,
    albumMediaVersion: Int = 0,
    customMedia: MusicDatabase.CustomMedia? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AuroSquircle)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(AuroSquircle)
                .background(AuroColor.SurfaceCard)
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), AuroSquircle)
        ) {
            AlbumArtworkImage(
                albumName = release.albumName,
                customMediaPath = customMediaPath,
                isGif = isGif,
                fallbackTrack = release.coverTrack,
                shape = AuroSquircle,
                mediaStamp = albumMediaVersion.toLong(),
                targetSizeDp = 300,
                customMedia = customMedia,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = release.albumName,
            style = AuroType.CardTitle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = AuroColor.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        val desc = buildString {
            if (release.year > 0) append("${release.year} • ")
            append(formatTracksCount(release.trackCount))
        }

        Text(
            text = desc,
            style = AuroType.Caption,
            color = AuroColor.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
