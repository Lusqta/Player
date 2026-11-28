package com.lucas.player.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.data.CoverArtLoader
import com.lucas.player.data.LibraryRepository
import com.lucas.player.data.MusicDatabase
import com.lucas.player.ui.components.AuroHaptics
import com.lucas.player.ui.components.AuroOverlay
import com.lucas.player.ui.components.AuroOverlayType
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroSpace
import com.lucas.player.ui.theme.AuroType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuroMediaEditorSheet(
    entityType: String,
    entityKey: String,
    entityTitle: String,
    initialMedia: MusicDatabase.CustomMedia?,
    libraryRepository: LibraryRepository = LibraryRepository.getInstance(),
    onSaved: (MusicDatabase.CustomMedia?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var draftGifPath by remember(entityType, entityKey, initialMedia) {
        mutableStateOf(initialMedia?.gifPath ?: "")
    }
    var draftFrameIndex by remember(entityType, entityKey, initialMedia) {
        mutableIntStateOf(initialMedia?.gifFrameIndex ?: 0)
    }
    var draftFrozenPath by remember(entityType, entityKey, initialMedia) {
        mutableStateOf(initialMedia?.gifFrozenFramePath ?: "")
    }
    var draftStaticPath by remember(entityType, entityKey, initialMedia) {
        mutableStateOf(initialMedia?.staticImagePath ?: "")
    }
    var draftPausedMode by remember(entityType, entityKey, initialMedia) {
        mutableStateOf(initialMedia?.pausedDisplayMode ?: MusicDatabase.PAUSED_MODE_GIF_FRAME)
    }
    var isSaving by remember { mutableStateOf(false) }

    val frameSamples by produceState<List<Bitmap>>(
        initialValue = emptyList(),
        key1 = draftGifPath
    ) {
        value = if (draftGifPath.isNotBlank()) {
            withContext(Dispatchers.IO) {
                CoverArtLoader.extractGifFrameSamples(draftGifPath, maxSamples = 20, thumbSizePx = 112)
            }
        } else {
            emptyList()
        }
    }

    var staticPreviewBitmap by remember(draftStaticPath) {
        mutableStateOf(CoverArtLoader.getCachedFileBitmap(draftStaticPath))
    }
    LaunchedEffect(draftStaticPath) {
        if (draftStaticPath.isNotBlank()) {
            CoverArtLoader.loadFileBitmap(draftStaticPath, 400) { bmp ->
                staticPreviewBitmap = bmp
            }
        } else {
            staticPreviewBitmap = null
        }
    }

    val gifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val savedPath = CoverArtLoader.saveCustomGif(context, entityType, entityKey, uri)
                if (savedPath != null) {
                    withContext(Dispatchers.Main) {
                        draftGifPath = savedPath
                        draftFrameIndex = 0
                        draftFrozenPath = ""
                        if (draftStaticPath.isBlank()) {
                            draftPausedMode = MusicDatabase.PAUSED_MODE_GIF_FRAME
                        }
                        AuroHaptics.confirm(view)
                    }
                }
            }
        }
    }

    val staticLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val savedPath = CoverArtLoader.saveCustomStaticImage(context, entityType, entityKey, uri)
                if (savedPath != null) {
                    withContext(Dispatchers.Main) {
                        draftStaticPath = savedPath
                        if (draftGifPath.isBlank()) {
                            draftPausedMode = MusicDatabase.PAUSED_MODE_STATIC_IMAGE
                        }
                        AuroHaptics.confirm(view)
                    }
                }
            }
        }
    }

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
                    .heightIn(max = screenHeight * 0.88f)
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
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
                        text = stringResource(R.string.media_editor_title),
                        style = AuroType.SectionTitle,
                        color = AuroColor.TextPrimary
                    )
                    Text(
                        text = entityTitle,
                        style = AuroType.Caption,
                        color = AuroColor.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Prévia quadrada 0.dp do GIF + Trilha arrastável de seleção de frame
                    if (draftGifPath.isNotBlank()) {
                        val clampedIndex = if (frameSamples.isNotEmpty()) {
                            draftFrameIndex.coerceIn(0, frameSamples.lastIndex)
                        } else 0
                        val previewFrame = frameSamples.getOrNull(clampedIndex)

                        Box(
                            modifier = Modifier
                                .size(156.dp)
                                .align(Alignment.CenterHorizontally)
                                .clip(RectangleShape)
                                .background(Color(0xFF101013))
                                .border(0.75.dp, Color.White.copy(alpha = 0.14f), RectangleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewFrame != null) {
                                Image(
                                    bitmap = previewFrame.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.GifBox,
                                    contentDescription = null,
                                    tint = AuroColor.TextTertiary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.media_editor_gif_frame_label, clampedIndex + 1, frameSamples.size.coerceAtLeast(1)),
                            style = AuroType.MicroLabel,
                            color = AuroColor.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (frameSamples.isNotEmpty()) {
                            var stripWidthPx by remember { mutableIntStateOf(1) }
                            val stripCount = frameSamples.size
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RectangleShape)
                                    .background(Color(0xFF101013))
                                    .border(0.75.dp, Color.White.copy(alpha = 0.12f), RectangleShape)
                                    .onSizeChanged { stripWidthPx = it.width.coerceAtLeast(1) }
                                    .pointerInput(stripCount, stripWidthPx) {
                                        detectTapGestures { offset ->
                                            val idx = ((offset.x / stripWidthPx.toFloat()) * stripCount)
                                                .toInt()
                                                .coerceIn(0, stripCount - 1)
                                            if (idx != draftFrameIndex) {
                                                AuroHaptics.tick(view)
                                                draftFrameIndex = idx
                                            }
                                        }
                                    }
                                    .pointerInput(stripCount, stripWidthPx) {
                                        detectHorizontalDragGestures { change, _ ->
                                            change.consume()
                                            val idx = ((change.position.x / stripWidthPx.toFloat()) * stripCount)
                                                .toInt()
                                                .coerceIn(0, stripCount - 1)
                                            if (idx != draftFrameIndex) {
                                                AuroHaptics.sliderTick(view)
                                                draftFrameIndex = idx
                                            }
                                        }
                                    }
                            ) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    frameSamples.forEachIndexed { idx, frameBmp ->
                                        val isCurrent = idx == clampedIndex
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .then(
                                                    if (isCurrent) {
                                                        Modifier.border(2.dp, Color.White, RectangleShape)
                                                    } else Modifier
                                                )
                                        ) {
                                            Image(
                                                bitmap = frameBmp.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    } else if (staticPreviewBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .align(Alignment.CenterHorizontally)
                                .clip(RectangleShape)
                                .background(Color(0xFF101013))
                                .border(0.75.dp, Color.White.copy(alpha = 0.14f), RectangleShape)
                        ) {
                            Image(
                                bitmap = staticPreviewBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Ações de GIF
                    MediaSlotRow(
                        icon = Icons.Outlined.GifBox,
                        label = if (draftGifPath.isBlank()) {
                            stringResource(R.string.media_editor_set_gif)
                        } else {
                            stringResource(R.string.media_editor_change_gif)
                        },
                        hasValue = draftGifPath.isNotBlank(),
                        onPick = {
                            AuroHaptics.click(view)
                            gifLauncher.launch("image/gif")
                        },
                        onRemove = {
                            AuroHaptics.click(view)
                            draftGifPath = ""
                            draftFrozenPath = ""
                            draftFrameIndex = 0
                            if (draftStaticPath.isNotBlank()) {
                                draftPausedMode = MusicDatabase.PAUSED_MODE_STATIC_IMAGE
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Ações de Imagem Estática
                    MediaSlotRow(
                        icon = Icons.Outlined.Image,
                        label = if (draftStaticPath.isBlank()) {
                            stringResource(R.string.media_editor_set_image)
                        } else {
                            stringResource(R.string.media_editor_change_image)
                        },
                        hasValue = draftStaticPath.isNotBlank(),
                        onPick = {
                            AuroHaptics.click(view)
                            staticLauncher.launch("image/*")
                        },
                        onRemove = {
                            AuroHaptics.click(view)
                            draftStaticPath = ""
                            draftPausedMode = MusicDatabase.PAUSED_MODE_GIF_FRAME
                        }
                    )

                    // Escolha de qual mídia exibir quando GIFs estiverem pausados
                    if (draftGifPath.isNotBlank() && draftStaticPath.isNotBlank()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.media_editor_paused_mode_title),
                            style = AuroType.MicroLabel,
                            color = AuroColor.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val options = listOf(
                            MusicDatabase.PAUSED_MODE_GIF_FRAME to stringResource(R.string.media_editor_mode_gif_frame),
                            MusicDatabase.PAUSED_MODE_STATIC_IMAGE to stringResource(R.string.media_editor_mode_static_image)
                        )
                        options.forEach { (mode, label) ->
                            val selected = draftPausedMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable {
                                        AuroHaptics.click(view)
                                        draftPausedMode = mode
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = AuroType.Body.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = AuroColor.TextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val hasChanges = draftGifPath != (initialMedia?.gifPath ?: "") ||
                        draftFrameIndex != (initialMedia?.gifFrameIndex ?: 0) ||
                        draftStaticPath != (initialMedia?.staticImagePath ?: "") ||
                        draftPausedMode != (initialMedia?.pausedDisplayMode ?: MusicDatabase.PAUSED_MODE_GIF_FRAME)
                    val canSave = !isSaving && hasChanges

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(CircleShape)
                            .background(if (canSave) Color.White else Color.White.copy(alpha = 0.28f))
                            .clickable(enabled = canSave) {
                                isSaving = true
                                AuroHaptics.confirm(view)
                                coroutineScope.launch(Dispatchers.IO) {
                                    val frozenPath = if (draftGifPath.isNotBlank()) {
                                        CoverArtLoader.saveFrozenGifFrame(
                                            context = context,
                                            entityType = entityType,
                                            entityKey = entityKey,
                                            gifPath = draftGifPath,
                                            frameIndex = draftFrameIndex,
                                            sampleCount = frameSamples.size.coerceAtLeast(1)
                                        ) ?: draftFrozenPath
                                    } else {
                                        CoverArtLoader.deleteCustomFile(draftFrozenPath)
                                        ""
                                    }

                                    // Limpeza de arquivos removidos pelo usuário
                                    val oldGif = initialMedia?.gifPath ?: ""
                                    if (oldGif.isNotBlank() && oldGif != draftGifPath) {
                                        CoverArtLoader.deleteCustomFile(oldGif)
                                    }
                                    val oldFrozen = initialMedia?.gifFrozenFramePath ?: ""
                                    if (oldFrozen.isNotBlank() && oldFrozen != frozenPath) {
                                        CoverArtLoader.deleteCustomFile(oldFrozen)
                                    }
                                    val oldStatic = initialMedia?.staticImagePath ?: ""
                                    if (oldStatic.isNotBlank() && oldStatic != draftStaticPath) {
                                        CoverArtLoader.deleteCustomFile(oldStatic)
                                    }

                                    val savedMedia: MusicDatabase.CustomMedia? =
                                        if (draftGifPath.isBlank() && draftStaticPath.isBlank()) {
                                            libraryRepository.deleteCustomMedia(entityType, entityKey)
                                            when (entityType) {
                                                MusicDatabase.ENTITY_ARTIST -> libraryRepository.removeArtistMedia(entityTitle)
                                                MusicDatabase.ENTITY_ALBUM -> libraryRepository.removeAlbumMedia(entityTitle)
                                                MusicDatabase.ENTITY_PLAYLIST -> {
                                                    entityKey.toLongOrNull()?.let { libraryRepository.updatePlaylistCover(it, "") }
                                                }
                                            }
                                            null
                                        } else {
                                            val item = MusicDatabase.CustomMedia(
                                                entityType = entityType,
                                                entityKey = entityKey,
                                                gifPath = draftGifPath,
                                                gifFrameIndex = draftFrameIndex,
                                                gifFrozenFramePath = frozenPath,
                                                staticImagePath = draftStaticPath,
                                                pausedDisplayMode = draftPausedMode
                                            )
                                            libraryRepository.upsertCustomMedia(item)
                                            // Sincroniza tabelas legadas para compatibilidade
                                            when (entityType) {
                                                MusicDatabase.ENTITY_ARTIST -> {
                                                    val primaryPath = draftGifPath.ifBlank { draftStaticPath }
                                                    libraryRepository.setArtistMedia(entityTitle, primaryPath, draftGifPath.isNotBlank())
                                                }
                                                MusicDatabase.ENTITY_ALBUM -> {
                                                    val primaryPath = draftGifPath.ifBlank { draftStaticPath }
                                                    libraryRepository.setAlbumMedia(entityTitle, primaryPath, draftGifPath.isNotBlank())
                                                }
                                                MusicDatabase.ENTITY_PLAYLIST -> {
                                                    val staticOrFrozen = draftStaticPath.ifBlank { frozenPath }
                                                    entityKey.toLongOrNull()?.let {
                                                        libraryRepository.updatePlaylistCover(it, staticOrFrozen)
                                                    }
                                                }
                                            }
                                            item
                                        }

                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        onSaved(savedMedia)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            style = AuroType.Label.copy(color = Color.Black, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaSlotRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    hasValue: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(onClick = onPick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = AuroType.Body.copy(color = Color.White, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (hasValue) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AuroColor.Danger.copy(alpha = 0.16f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.media_editor_remove),
                    tint = AuroColor.Danger,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
