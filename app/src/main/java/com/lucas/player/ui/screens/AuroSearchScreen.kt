package com.lucas.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.R
import com.lucas.player.model.Track
import com.lucas.player.ui.components.AuroEmptyState
import com.lucas.player.ui.components.AuroTrackRow
import com.lucas.player.ui.theme.AuroColor
import com.lucas.player.ui.theme.AuroGlass
import com.lucas.player.ui.theme.AuroGlassKind

/**
 * AURŌ DESIGN SYSTEM v2 - TELA DE BUSCA (AURŌ SEARCH)
 */
@Composable
fun AuroSearchScreen(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    results: List<Track>,
    currentPlayingId: Long,
    onTrackClick: (Track, Int) -> Unit,
    onTrackMenuClick: (Track) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
            .statusBarsPadding()
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Voltar",
                    tint = AuroColor.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Campo de busca com vidro preto
            AuroGlass(
                kind = AuroGlassKind.Card,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = AuroColor.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Buscar faixas, artistas...", color = AuroColor.TextTertiary, fontSize = 13.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = AuroColor.TextPrimary,
                            unfocusedTextColor = AuroColor.TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Limpar",
                                tint = AuroColor.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = if (searchQuery.isEmpty()) "Digite para buscar" else "${results.size} resultados encontrados",
            color = AuroColor.TextTertiary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        if (searchQuery.isNotBlank() && results.isEmpty()) {
            AuroEmptyState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.empty_search_title, searchQuery),
                message = stringResource(R.string.empty_search_message),
                modifier = Modifier.padding(bottom = 120.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                itemsIndexed(results, key = { _, t -> t.id }) { index, track ->
                    AuroTrackRow(
                        track = track,
                        trackIndex = index,
                        isPlayingThis = track.id == currentPlayingId,
                        onClick = { onTrackClick(track, index) },
                        onLongClick = {},
                        onMenuClick = { onTrackMenuClick(track) }
                    )
                    HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
                }
            }
        }
    }
}
