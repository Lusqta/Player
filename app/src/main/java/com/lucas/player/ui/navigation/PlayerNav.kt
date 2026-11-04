package com.lucas.player.ui.navigation

import androidx.compose.runtime.*
import com.lucas.player.model.LibraryCategory

/**
 * Destinos de navegação do Player inspirados na arquitetura do Poweramp (build 1034):
 * - Level 0: Now Playing (cmd_nav_to_main)
 * - Level 1: Library Root (cmd_nav_to_lib)
 * - Level 2: Category List (Pastas, Álbuns, Artistas, Gêneros, etc.)
 * - Level 3: Category Drilldown (Faixas de um Álbum, Álbuns de um Artista, etc.)
 * - Telas de utilidade: Equalizador (cmd_nav_to_eq), Busca (cmd_nav_to_search), Fila
 */
sealed class NavScreen {
    object NowPlaying : NavScreen()
    object LibraryRoot : NavScreen()
    data class CategoryList(val category: LibraryCategory) : NavScreen()
    data class Drilldown(
        val category: LibraryCategory,
        val title: String,
        val subtitle: String = ""
    ) : NavScreen()
    object Equalizer : NavScreen()
    object Search : NavScreen()
    object Queue : NavScreen()
    object Settings : NavScreen()
}

/**
 * Controlador de navegação com pilha de histórico (backstack) e comportamentos
 * idênticos aos observados na engenharia reversa do Poweramp.
 */
class PlayerNavController(initialScreen: NavScreen = NavScreen.LibraryRoot) {
    private val backStack = mutableStateListOf<NavScreen>(initialScreen)

    val currentScreen: NavScreen
        get() = backStack.lastOrNull() ?: NavScreen.LibraryRoot

    val canNavigateUp: Boolean
        get() = backStack.size > 1

    /**
     * Navega para uma nova tela, empilhando no histórico.
     * Evita duplicar a mesma tela no topo.
     */
    fun navigateTo(screen: NavScreen) {
        if (backStack.lastOrNull() != screen) {
            backStack.add(screen)
        }
    }

    /**
     * Volta um nível na hierarquia de navegação (Equivalente ao cmd_list_up_nav do Poweramp).
     * @return true se desempilhou, false se estava na raiz.
     */
    fun navigateUp(): Boolean {
        return if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            true
        } else {
            false
        }
    }

    /**
     * Comportamento do botão "Biblioteca" na barra inferior do Poweramp:
     * - Se estiver no NowPlaying -> Volta para a última lista da biblioteca ou para a Raiz.
     * - Se estiver em um Drilldown -> Sobe para a lista da categoria.
     * - Se estiver na lista de categoria -> Sobe para a Raiz da biblioteca.
     * - Se já estiver na Raiz da biblioteca -> Alterna (toggle) de volta para o NowPlaying!
     */
    fun toggleLibrary() {
        when (val curr = currentScreen) {
            is NavScreen.NowPlaying -> {
                // Tenta achar a tela de biblioteca anterior no histórico
                val lastLibIndex = backStack.indexOfLast { it != NavScreen.NowPlaying && it != NavScreen.Equalizer && it != NavScreen.Search }
                if (lastLibIndex >= 0) {
                    // Mantém o histórico até lá
                    while (backStack.size > lastLibIndex + 1) {
                        backStack.removeAt(backStack.size - 1)
                    }
                } else {
                    navigateTo(NavScreen.LibraryRoot)
                }
            }
            is NavScreen.Drilldown -> {
                navigateTo(NavScreen.CategoryList(curr.category))
            }
            is NavScreen.CategoryList -> {
                navigateTo(NavScreen.LibraryRoot)
            }
            is NavScreen.LibraryRoot -> {
                navigateTo(NavScreen.NowPlaying)
            }
            else -> {
                navigateTo(NavScreen.LibraryRoot)
            }
        }
    }

    /**
     * Comportamento do botão "Equalizador" na barra inferior do Poweramp:
     * Toca no EQ -> Abre EQ; Toca de novo -> Retorna para a tela anterior.
     */
    fun toggleEqualizer() {
        if (currentScreen is NavScreen.Equalizer) {
            navigateUp()
        } else {
            navigateTo(NavScreen.Equalizer)
        }
    }

    /**
     * Comportamento do botão "Buscar" na barra inferior do Poweramp:
     */
    fun toggleSearch() {
        if (currentScreen is NavScreen.Search) {
            navigateUp()
        } else {
            navigateTo(NavScreen.Search)
        }
    }

    /**
     * Abre a tela do NowPlaying ao tocar em uma faixa ou no MiniPlayer.
     */
    fun openNowPlaying() {
        navigateTo(NavScreen.NowPlaying)
    }

    /**
     * Trata o botão Voltar físico/gestual do Android:
     * Retorna true se consumiu o evento de voltar, ou false se o app deve fechar.
     */
    fun handleBack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            return true
        }
        return false
    }

    /**
     * Reseta a navegação diretamente para a Raiz da Biblioteca.
     */
    fun resetToLibraryRoot() {
        backStack.clear()
        backStack.add(NavScreen.LibraryRoot)
    }
}
