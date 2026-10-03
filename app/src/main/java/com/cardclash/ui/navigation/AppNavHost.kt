package com.cardclash.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.getString
import com.cardclash.di.AppContainer
import com.cardclash.ui.battle.BattleScreen
import com.cardclash.ui.collection.CollectionScreen
import com.cardclash.ui.deckbuilder.DeckBuilderScreen
import com.cardclash.ui.history.HistoryScreen
import com.cardclash.ui.menuhome.HomeScreen

/**
 * Grafo de navegacion de CardClash (Navigation Compose) con las rutas locales:
 * menu, coleccion, historial, combate (local demo) y constructor de mazos.
 */
@Composable
fun AppNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onOpenCollection = { navController.navigate(Routes.COLLECTION) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onPlayCombat = {
                    navController.navigate(Routes.BATTLE) {
                        launchSingleTop = true
                    }
                },
                onOpenDeckBuilder = { deckId ->
                    val route = if (deckId != null) {
                        "${Routes.DECK_BUILDER}/$deckId"
                    } else {
                        Routes.DECK_BUILDER
                    }
                    navController.navigate(route)
                },
            )
        }
        composable(Routes.COLLECTION) {
            CollectionScreen(container = container)
        }
        composable(Routes.HISTORY) {
            HistoryScreen(container = container)
        }
        composable(Routes.BATTLE) {
            BattleScreen(
                container = container,
                onBackToMenu = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.DECK_BUILDER}/{${Routes.ARG_DECK_ID}}",
            arguments = listOf(androidx.navigation.navArgument(Routes.ARG_DECK_ID) { type = androidx.navigation.NavType.StringType }),
        ) { backStackEntry ->
            val deckId = backStackEntry.getString(Routes.ARG_DECK_ID)
            DeckBuilderScreen(
                container = container,
                navController = navController,
            )
        }
        composable(Routes.DECK_BUILDER) {
            DeckBuilderScreen(
                container = container,
                navController = navController,
            )
        }
    }
}