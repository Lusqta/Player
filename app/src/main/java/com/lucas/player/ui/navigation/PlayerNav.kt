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
    object Home : NavScreen()
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
 * Controlador de navegação com pilha de histórico (backstack) no padrão Apple Music 7.0.2:
 * 4 abas principais: Início, Biblioteca, Fila, Buscar.
 */
class PlayerNavController(initialScreen: NavScreen = NavScreen.Home) {
    private val backStack = mutableStateListOf<NavScreen>(initialScreen)

    val currentScreen: NavScreen
        get() = backStack.lastOrNull() ?: NavScreen.Home

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
     * Substitui a tela do topo da pilha de navegação.
     */
    fun replaceCurrent(screen: NavScreen) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.size - 1] = screen
        } else {
            backStack.add(screen)
        }
    }

    /**
     * Volta um nível na hierarquia de navegação.
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
     * Comportamento da aba "Início" na barra flutuante:
     */
    fun toggleHome() {
        if (currentScreen is NavScreen.Home) {
            // Já está no início
        } else {
            navigateTo(NavScreen.Home)
        }
    }

    /**
     * Comportamento da aba "Biblioteca" na barra flutuante:
     */
    fun toggleLibrary() {
        when (val curr = currentScreen) {
            is NavScreen.Drilldown -> {
                navigateTo(NavScreen.CategoryList(curr.category))
            }
            is NavScreen.CategoryList -> {
                navigateTo(NavScreen.LibraryRoot)
            }
            is NavScreen.LibraryRoot -> {
                // Já está na raiz da biblioteca
            }
            else -> {
                navigateTo(NavScreen.LibraryRoot)
            }
        }
    }

    /**
     * Comportamento da aba "Fila" na barra flutuante:
     */
    fun toggleQueue() {
        if (currentScreen is NavScreen.Queue) {
            navigateUp()
        } else {
            navigateTo(NavScreen.Queue)
        }
    }

    /**
     * Comportamento da aba "Buscar" na barra flutuante:
     */
    fun toggleSearch() {
        if (currentScreen is NavScreen.Search) {
            navigateUp()
        } else {
            navigateTo(NavScreen.Search)
        }
    }

    /**
     * Abre a tela do Equalizador a partir do NowPlaying ou Configurações.
     */
    fun toggleEqualizer() {
        if (currentScreen is NavScreen.Equalizer) {
            navigateUp()
        } else {
            navigateTo(NavScreen.Equalizer)
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

    /**
     * Reseta a navegação diretamente para a Tela Inicial (Início).
     */
    fun resetToHome() {
        backStack.clear()
        backStack.add(NavScreen.Home)
    }
}
