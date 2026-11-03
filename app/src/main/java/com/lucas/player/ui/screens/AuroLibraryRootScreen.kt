package com.lucas.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucas.player.model.LibraryCategory
import com.lucas.player.ui.components.AuroBreadcrumbHeader
import com.lucas.player.ui.components.AuroBreadcrumbItem
import com.lucas.player.ui.theme.AuroColor

/**
 * AURŌ DESIGN SYSTEM v2 - TELA RAIZ DA BIBLIOTECA (AURŌ LIBRARY ROOT)
 */
@Composable
fun AuroLibraryRootScreen(
    totalTracksCount: Int,
    onSelectCategory: (LibraryCategory) -> Unit,
    onSortClick: () -> Unit,
    onRescanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = remember {
        listOf(
            LibraryCategory.ALL_SONGS to ("Todas as Músicas" to Icons.AutoMirrored.Rounded.PlaylistPlay),
            LibraryCategory.FOLDERS to ("Pastas" to Icons.Rounded.Folder),
            LibraryCategory.ALBUMS to ("Álbuns" to Icons.Rounded.Album),
            LibraryCategory.ARTISTS to ("Artistas" to Icons.Rounded.Person),
            LibraryCategory.ALBUM_ARTISTS to ("Artistas do Álbum" to Icons.Rounded.Group),
            LibraryCategory.GENRES to ("Gêneros" to Icons.Rounded.Category),
            LibraryCategory.PLAYLISTS to ("Listas de Reprodução" to Icons.Rounded.PlaylistAddCheck),
            LibraryCategory.QUEUE to ("Fila de Reprodução" to Icons.AutoMirrored.Rounded.QueueMusic),
            LibraryCategory.YEARS to ("Anos" to Icons.Rounded.DateRange),
            LibraryCategory.COMPOSERS to ("Compositores" to Icons.Rounded.Piano),
            LibraryCategory.MOST_PLAYED to ("Mais Tocadas" to Icons.Rounded.TrendingUp),
            LibraryCategory.RECENTLY_ADDED to ("Adicionadas Recentemente" to Icons.Rounded.History),
            LibraryCategory.TOP_RATED to ("Melhor Classificadas" to Icons.Rounded.Star)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuroColor.Black)
    ) {
        AuroBreadcrumbHeader(
            items = listOf(AuroBreadcrumbItem("Biblioteca")),
            onItemClick = {},
            canGoBack = false,
            rightActions = {
                IconButton(onClick = onSortClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Ordenar",
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onRescanClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Atualizar",
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        // Resumo
        Text(
            text = "$totalTracksCount faixas indexadas",
            color = AuroColor.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Linhas de categoria (sem vidro, texto e fio Hairline direto no preto)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(categories, key = { it.first.name }) { (cat, info) ->
                val (title, icon) = info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCategory(cat) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AuroColor.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = AuroColor.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = AuroColor.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                HorizontalDivider(color = AuroColor.Hairline, thickness = 1.dp)
            }
        }
    }
}
