package com.cardgames.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.cardgames.core.domain.game.GameId
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.theme.CardGamesTheme
import com.cardgames.feature.ginrummy.GinScreen
import com.cardgames.feature.ginrummy.setup.GinSetupScreen
import com.cardgames.feature.main.GameSettingsScreen
import com.cardgames.feature.main.HomeScreen
import com.cardgames.feature.main.SettingsScreen
import com.cardgames.feature.solitaire.SolitaireScreen
import com.cardgames.feature.stats.GameStatsScreen
import com.cardgames.feature.stats.StatsScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SolitaireRoute

/** [newMatch]: arriving from match setup — deal a fresh match rather than resume the saved one. */
@Serializable
data class GinRummyRoute(
    val newMatch: Boolean = false,
)

@Serializable
data object GinSetupRoute

@Serializable
data object SettingsRoute

/** One game's own settings page — from Settings, or straight from that game's top bar. */
@Serializable
data class GameSettingsRoute(
    val game: GameId,
)

@Serializable
data object StatsRoute

/** One game's stats page. */
@Serializable
data class GameStatsRoute(
    val game: GameId,
)

/**
 * Every navigation call goes through [open] (single-top, so a fast double tap on a game card or
 * Settings can't stack two copies of the same screen) and every back action is wrapped in
 * [dropUnlessResumed] (so a double tap on Back can't pop two screens — the second tap lands
 * while the first screen is already leaving and is dropped).
 *
 * Only the arriving screen moves, and it's drawn on top: going forward, it fades and slides in from
 * the right over the old one; going back, the screen being returned to fades and slides in from the
 * left over the one being left. On a tablet it only fades in, with no slide. The screen underneath
 * stays as it is — every screen is opaque felt, so fading both would show one through the other
 * mid-transition.
 */
@Composable
fun CardGamesNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val slides = CardGamesTheme.formFactor == FormFactor.PHONE

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
        enterTransition = {
            if (slides) {
                fadeIn(tween(TRANSITION_MILLIS)) +
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        tween(TRANSITION_MILLIS),
                    ) { it / SLIDE_FRACTION }
            } else {
                fadeIn(tween(TRANSITION_MILLIS))
            }
        },
        exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
        popEnterTransition = {
            if (slides) {
                fadeIn(tween(TRANSITION_MILLIS)) +
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(TRANSITION_MILLIS)) { it / SLIDE_FRACTION }
            } else {
                fadeIn(tween(TRANSITION_MILLIS))
            }
        },
        popExitTransition = { ExitTransition.KeepUntilTransitionsFinished },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onPlayGame = { gameId ->
                    when (gameId) {
                        GameId.KLONDIKE -> navController.open(SolitaireRoute)
                        GameId.GIN_RUMMY -> navController.open(GinRummyRoute())
                    }
                },
                onOpenGinSetup = { navController.open(GinSetupRoute) },
                onOpenSettings = { navController.open(SettingsRoute) },
                onOpenStats = { navController.open(StatsRoute) },
            )
        }
        composable<GinSetupRoute> {
            GinSetupScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
                onStartMatch = {
                    // The table replaces setup, so leaving the match comes back to Home.
                    navController.navigate(GinRummyRoute(newMatch = true)) {
                        popUpTo<GinSetupRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onContinueMatch = {
                    // Kept the match in progress: back to its table, in place of setup.
                    navController.navigate(GinRummyRoute()) {
                        popUpTo<GinSetupRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
                onOpenGame = { game -> navController.open(GameSettingsRoute(game)) },
            )
        }
        composable<GameSettingsRoute> { entry ->
            GameSettingsScreen(
                game = entry.toRoute<GameSettingsRoute>().game,
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable<StatsRoute> {
            StatsScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
                onOpenGame = { game -> navController.open(GameStatsRoute(game)) },
            )
        }
        composable<GameStatsRoute> { entry ->
            GameStatsScreen(
                game = entry.toRoute<GameStatsRoute>().game,
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
            )
        }
        composable<SolitaireRoute> {
            SolitaireScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
                onOpenSettings = { navController.open(GameSettingsRoute(GameId.KLONDIKE)) },
            )
        }
        composable<GinRummyRoute> {
            GinScreen(
                onNavigateBack = dropUnlessResumed { navController.popBackStack() },
                onOpenSettings = { navController.open(GameSettingsRoute(GameId.GIN_RUMMY)) },
                onNewMatch = {
                    // Setup takes the table's place; "Keep playing" there brings the match back.
                    navController.navigate(GinSetupRoute) {
                        popUpTo<GinRummyRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

private fun <T : Any> NavHostController.open(route: T) {
    navigate(route) { launchSingleTop = true }
}

private val TRANSITION_MILLIS = CardGamesMotion.Screen.millis

/** Screens slide a fraction of the width, not the whole way — a nudge of direction, not a page turn. */
private const val SLIDE_FRACTION = 6
