package com.lucas.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lucas.player.R
import com.lucas.player.data.LibraryRepository
import com.lucas.player.data.MusicDatabase
import com.lucas.player.model.Album
import com.lucas.player.model.AlbumCollection
import com.lucas.player.model.Track
import com.lucas.player.model.formatArtistsDisplay
import com.lucas.player.model.isValidHighlightName
import com.lucas.player.ui.components.*
import com.lucas.player.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class AlbumCollectionEntry(
    val albumName: String,
    val artist: String,
    val trackCount: Int,
    val coverTrack: Track?
)

private data class AlbumCollectionScreenState(
    val collection: AlbumCollection? = null,
    val orderedAlbums: List<AlbumCollectionEntry> = emptyList(),
    val allLibraryAlbums: List<Album> = emptyList(),
    val albumCoverMap: Map<String, Track?> = emptyMap(),
    val tracks: List<Track> = emptyList(),
    val isLoaded: Boolean = false
)

@Composable
fun AuroAlbumCollectionScreen(
    collectionId: Long,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onCollectionMutated: () -> Unit,
    onCollectionDeleted: () -> Unit,
    libraryVersion: Int = 0,
    albumCollectionsVersion: Int = 0,
    albumMetaMap: Map<String, MusicDatabase.AlbumMeta> = emptyMap(),
    albumMediaVersion: Int = 0,
    libraryRepository: LibraryRepository = LibraryRepository.getInstance(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val bottomInset = LocalAuroBottomInset.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val screenData by produceState(
        initialValue = AlbumCollectionScreenState(),
        collectionId,
        libraryVersion,
        albumCollectionsVersion
    ) {
        value = withContext(Dispatchers.IO) {
            val collection = libraryRepository.getAlbumCollections().firstOrNull { it.id == collectionId }
            val orderedNames = libraryRepository.getAlbumsForCollection(collectionId)
            val allTracks = libraryRepository.getAllTracks()
            val allAlbums = libraryRepository.getAlbums().filter { isValidHighlightName(it.name) }
            val albumByKey = allAlbums.associateBy { it.name.trim().lowercase() }
            val tracksByAlbum = allTracks.groupBy { it.album.trim().lowercase() }
            val coverMap = tracksByAlbum.mapValues { it.value.firstOrNull() }

            val entries = orderedNames.map { albumName ->
                val key = albumName.trim().lowercase()
                val libAlbum = albumByKey[key]
                val albumTracks = tracksByAlbum[key].orEmpty()
                AlbumCollectionEntry(
                    albumName = libAlbum?.name ?: albumName,
                    artist = libAlbum?.artist ?: albumTracks.firstOrNull()?.artist.orEmpty(),
                    trackCount = libAlbum?.trackCount ?: albumTracks.size,
                    coverTrack = albumTracks.firstOrNull()
                )
            }
            val tracks = libraryRepository.buildTracksForAlbumCollection(allTracks, orderedNames)
            AlbumCollectionScreenState(
                collection = collection,
                orderedAlbums = entries,
                allLibraryAlbums = allAlbums,
                albumCoverMap = coverMap,
                tracks = tracks,
                isLoaded = true
            )
        }
    }

    var localAlbums by remember(screenData.orderedAlbums) { mutableStateOf(screenData.orderedAlbums) }
    var showAddAlbumsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = with(density) { 72.dp.toPx() }

    LaunchedEffect(draggingIndex != null) {
        if (draggingIndex == null) return@LaunchedEffect
        while (isActive && draggingIndex != null) {
            val viewportH = listState.layoutInfo.viewportSize.height
            if (viewportH > 0) {
                val edgeThreshold = with(density) { 70.dp.toPx() }
                if (dragPointerY > 0 && dragPointerY < edgeThreshold) {
                    val factor = ((edgeThreshold - dragPointerY) / edgeThreshold).coerceIn(0.2f, 1f)
                    listState.scrollBy(-18f * factor)
                } else if (dragPointerY > viewportH - edgeThreshold) {
                    val factor = ((dragPointerY - (viewportH - edgeThreshold)) / edgeThreshold).coerceIn(0.2f, 1f)
                    listState.scrollBy(18f * factor)
                }
            }
            delay(16)
        }
    }

    val titleText = screenData.collection?.name ?: stringResource(R.string.album_collection_subtitle)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Canvas)
    ) {
        AuroTopBar(
            title = titleText,
            subtitle = formatAlbumsCount(localAlbums.size),
            canGoBack = true,
            onBackClick = onBack,
            rightActions = {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = {
                            AuroHaptics.click(view)
                            showMenu = true
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.cd_more_options),
                            tint = AuroColor.TextPrimary,
                            modifier = Modifier.size(22.dp)
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
                                    text = stringResource(R.string.album_collection_add_albums),
                                    style = AuroType.Body,
                                    color = AuroColor.TextPrimary
                                )
                            },
                            onClick = {
                                showMenu = false
                                showAddAlbumsSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.album_collection_rename),
                                    style = AuroType.Body,
                                    color = AuroColor.TextPrimary
                                )
                            },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.album_collection_delete),
                                    style = AuroType.Body,
                                    color = AuroColor.Danger
                                )
                            },
                            onClick = {
                                showMenu = false
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        )

        if (screenData.isLoaded && localAlbums.isEmpty()) {
            AuroEmptyState(
                icon = Icons.Outlined.Album,
                title = stringResource(R.string.album_collection_empty_title),
                message = stringResource(R.string.album_collection_empty_message),
                actionLabel = stringResource(R.string.album_collection_add_albums),
                onAction = { showAddAlbumsSheet = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomInset + 64.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset + 80.dp)
            ) {
                if (screenData.tracks.isNotEmpty()) {
                    item(key = "collection_actions") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AuroSpace.ScreenMargin, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable {
                                        AuroHaptics.click(view)
                                        onPlayAll(screenData.tracks)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFF050505),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.play_all),
                                    style = AuroType.Caption.copy(
                                        color = Color(0xFF050505),
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(CircleShape)
                                    .background(AuroColor.SurfaceCard)
                                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                    .clickable {
                                        AuroHaptics.click(view)
                                        onShuffleAll(screenData.tracks)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.shuffle_all),
                                    style = AuroType.Caption.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                itemsIndexed(
                    items = localAlbums,
                    key = { _, item -> item.albumName.trim().lowercase() }
                ) { index, entry ->
                    val itemKey = entry.albumName.trim().lowercase()
                    val albumMeta = albumMetaMap[itemKey]
                    val isDragging = draggingIndex == index

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .zIndex(if (isDragging) 2f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffsetY
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                }
                            }
                            .background(if (isDragging) Color.White.copy(alpha = 0.06f) else Color.Transparent)
                            .clickable {
                                AuroHaptics.click(view)
                                onAlbumClick(entry.albumName)
                            }
                            .padding(start = AuroSpace.ScreenMargin, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RectangleShape)
                                .background(AuroColor.SurfaceCard)
                        ) {
                            AlbumArtworkImage(
                                albumName = entry.albumName,
                                customMediaPath = albumMeta?.mediaPath?.takeIf { it.isNotBlank() },
                                isGif = albumMeta?.isGif ?: false,
                                fallbackTrack = entry.coverTrack,
                                shape = RectangleShape,
                                mediaStamp = albumMediaVersion.toLong(),
                                targetSizeDp = 180,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.albumName,
                                style = AuroType.TrackTitle.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = AuroColor.TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val artistDisplay = formatArtistsDisplay(entry.artist, stringResource(R.string.unknown_artist))
                            Text(
                                text = "$artistDisplay • ${formatTracksCount(entry.trackCount)}",
                                style = AuroType.Subtitle,
                                color = AuroColor.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                AuroHaptics.click(view)
                                localAlbums = localAlbums.filterNot {
                                    it.albumName.equals(entry.albumName, ignoreCase = true)
                                }
                                coroutineScope.launch(Dispatchers.IO) {
                                    libraryRepository.removeAlbumFromCollection(collectionId, entry.albumName)
                                    withContext(Dispatchers.Main) {
                                        onCollectionMutated()
                                    }
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_remove_item),
                                tint = AuroColor.TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .pointerInput(localAlbums.size) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            AuroHaptics.sliderTick(view)
                                            draggingIndex = index
                                            dragOffsetY = 0f
                                            dragPointerY = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.key == itemKey }
                                                ?.offset?.toFloat() ?: 0f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount
                                            dragPointerY += dragAmount

                                            val slotsShifted = (dragOffsetY / itemHeightPx).roundToInt()
                                            val currentIdx = draggingIndex ?: index
                                            val targetIdx = (currentIdx + slotsShifted).coerceIn(0, localAlbums.size - 1)
                                            if (targetIdx != currentIdx) {
                                                AuroHaptics.sliderTick(view)
                                                val list = localAlbums.toMutableList()
                                                val moved = list.removeAt(currentIdx)
                                                list.add(targetIdx, moved)
                                                localAlbums = list
                                                dragOffsetY -= (targetIdx - currentIdx) * itemHeightPx
                                                draggingIndex = targetIdx
                                            }
                                        },
                                        onDragEnd = {
                                            draggingIndex = null
                                            dragOffsetY = 0f
                                            val reorderedNames = localAlbums.map { it.albumName }
                                            coroutineScope.launch(Dispatchers.IO) {
                                                libraryRepository.reorderCollectionAlbums(collectionId, reorderedNames)
                                                withContext(Dispatchers.Main) {
                                                    onCollectionMutated()
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            dragOffsetY = 0f
                                            localAlbums = screenData.orderedAlbums
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Reorder,
                                contentDescription = stringResource(R.string.queue_reorder_handle),
                                tint = AuroColor.TextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddAlbumsSheet) {
        val existingAlbumKeys = remember(localAlbums) {
            localAlbums.map { it.albumName.trim().lowercase() }.toSet()
        }
        AlbumPickerSheet(
            albums = screenData.allLibraryAlbums,
            existingAlbumKeys = existingAlbumKeys,
            albumCoverMap = screenData.albumCoverMap,
            albumMetaMap = albumMetaMap,
            albumMediaVersion = albumMediaVersion,
            onAddAlbums = { selectedNames ->
                showAddAlbumsSheet = false
                if (selectedNames.isNotEmpty()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        libraryRepository.addAlbumsToCollection(collectionId, selectedNames)
                        withContext(Dispatchers.Main) {
                            onCollectionMutated()
                        }
                    }
                }
            },
            onDismiss = { showAddAlbumsSheet = false }
        )
    }

    if (showRenameDialog) {
        var newName by remember(titleText) { mutableStateOf(titleText) }
        AuroOverlay(
            visible = true,
            onDismiss = { showRenameDialog = false },
            type = AuroOverlayType.Dialog
        ) {
            AuroDialogContainer(
                title = stringResource(R.string.album_collection_rename_title),
                onDismiss = { showRenameDialog = false },
                actions = {
                    AuroDialogTextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showRenameDialog = false }
                    )
                    AuroDialogTextButton(
                        text = stringResource(R.string.save),
                        onClick = {
                            val trimmed = newName.trim()
                            if (trimmed.isNotEmpty()) {
                                showRenameDialog = false
                                coroutineScope.launch(Dispatchers.IO) {
                                    libraryRepository.renameAlbumCollection(collectionId, trimmed)
                                    withContext(Dispatchers.Main) {
                                        onCollectionMutated()
                                    }
                                }
                            }
                        }
                    )
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AuroHeight.DialogInputField)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (newName.isEmpty()) {
                        Text(
                            text = stringResource(R.string.album_collection_name_hint),
                            style = AuroType.Body.copy(color = AuroColor.TextTertiary)
                        )
                    }
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        textStyle = AuroType.Body.copy(color = AuroColor.TextPrimary),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AuroConfirmDeleteDialog(
            title = stringResource(R.string.album_collection_delete),
            message = stringResource(R.string.album_collection_delete_confirm),
            onConfirm = {
                showDeleteConfirm = false
                coroutineScope.launch(Dispatchers.IO) {
                    libraryRepository.deleteAlbumCollection(collectionId)
                    withContext(Dispatchers.Main) {
                        onCollectionDeleted()
                    }
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun AlbumPickerSheet(
    albums: List<Album>,
    existingAlbumKeys: Set<String>,
    albumCoverMap: Map<String, Track?>,
    albumMetaMap: Map<String, MusicDatabase.AlbumMeta>,
    albumMediaVersion: Int,
    onAddAlbums: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    var searchQuery by remember { mutableStateOf("") }
    val selectedAlbums = remember { mutableStateListOf<String>() }

    val filteredAlbums = remember(albums, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) albums
        else albums.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true)
        }
    }

    AuroOverlay(
        visible = true,
        onDismiss = onDismiss,
        type = AuroOverlayType.BottomSheet
    ) {
        AuroSheetContainer(
            onDismiss = onDismiss
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.album_collection_add_albums),
                    style = AuroType.Subtitle.copy(fontWeight = FontWeight.Bold),
                    color = AuroColor.TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selectedAlbums.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                AuroHaptics.confirm(view)
                                onAddAlbums(selectedAlbums.toList())
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.album_collection_add_selected, selectedAlbums.size),
                            style = AuroType.MicroLabel.copy(
                                fontSize = 12.sp,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = AuroColor.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.album_collection_search_albums),
                                style = AuroType.Body.copy(color = AuroColor.TextTertiary)
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = AuroType.Body.copy(color = AuroColor.TextPrimary),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = AuroColor.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(filteredAlbums, key = { it.name.trim().lowercase() }) { album ->
                    val key = album.name.trim().lowercase()
                    val isAlreadyIn = existingAlbumKeys.contains(key)
                    val isSelected = selectedAlbums.any { it.equals(album.name, ignoreCase = true) }
                    val albumMeta = albumMetaMap[key]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clickable(enabled = !isAlreadyIn) {
                                AuroHaptics.click(view)
                                if (isSelected) {
                                    selectedAlbums.removeAll { it.equals(album.name, ignoreCase = true) }
                                } else {
                                    selectedAlbums.add(album.name)
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isAlreadyIn) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else Color.Transparent)
                                    .border(
                                        1.5.dp,
                                        if (isSelected) Color.White else Color.White.copy(alpha = 0.35f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RectangleShape)
                                .background(AuroColor.SurfaceCard)
                        ) {
                            AlbumArtworkImage(
                                albumName = album.name,
                                customMediaPath = albumMeta?.mediaPath?.takeIf { it.isNotBlank() },
                                isGif = albumMeta?.isGif ?: false,
                                fallbackTrack = albumCoverMap[key],
                                shape = RectangleShape,
                                mediaStamp = albumMediaVersion.toLong(),
                                targetSizeDp = 120,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = album.name,
                                style = AuroType.Body.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = if (isAlreadyIn) AuroColor.TextTertiary else AuroColor.TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatArtistsDisplay(album.artist, stringResource(R.string.unknown_artist))} • ${formatTracksCount(album.trackCount)}",
                                style = AuroType.Caption.copy(color = AuroColor.TextTertiary),
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
