package com.duzman46.gridbound.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.presentation.settings.SettingsViewModel
import com.duzman46.gridbound.ui.screens.DifficultySelectionScreen
import com.duzman46.gridbound.ui.screens.GameRoute
import com.duzman46.gridbound.ui.screens.MainMenuScreen
import com.duzman46.gridbound.ui.screens.ModeSelectionScreen
import com.duzman46.gridbound.ui.screens.OnlineLobbyRoute
import com.duzman46.gridbound.ui.screens.SettingsScreen
import com.duzman46.gridbound.ui.screens.SplashScreen
import com.duzman46.gridbound.ui.screens.StatisticsScreen
import com.duzman46.gridbound.ui.screens.WinnerScreen
import com.duzman46.gridbound.util.enumValueOrDefault

private object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val MODE = "mode"
    const val DIFFICULTY = "difficulty"
    const val SETTINGS = "settings"
    const val STATISTICS = "statistics"
    const val ONLINE = "online"
    const val GAME = "game/{mode}/{difficulty}?roomCode={roomCode}&playerId={playerId}&userId={userId}"
    const val WINNER = "winner/{winner}/{mode}/{difficulty}"

    fun game(mode: GameMode, difficulty: Difficulty): String = "game/${mode.name}/${difficulty.name}"
    fun onlineGame(roomCode: String, playerId: PlayerId, userId: String): String =
        "game/${GameMode.ONLINE.name}/${Difficulty.MEDIUM.name}?roomCode=$roomCode&playerId=${playerId.name}&userId=$userId"
    fun winner(winner: PlayerId, mode: GameMode, difficulty: Difficulty): String =
        "winner/${winner.name}/${mode.name}/${difficulty.name}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }
        composable(Routes.HOME) {
            MainMenuScreen(
                onPlay = { navController.navigate(Routes.MODE) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onStatistics = { navController.navigate(Routes.STATISTICS) },
            )
        }
        composable(Routes.MODE) {
            ModeSelectionScreen(
                onBack = navController::popBackStack,
                onAi = { navController.navigate(Routes.DIFFICULTY) },
                onLocal = { navController.navigate(Routes.game(GameMode.LOCAL_TWO_PLAYER, Difficulty.MEDIUM)) },
                onOnline = { navController.navigate(Routes.ONLINE) },
            )
        }
        composable(Routes.ONLINE) {
            OnlineLobbyRoute(
                onBack = navController::popBackStack,
                onOpenGame = { session ->
                    navController.navigate(Routes.onlineGame(session.roomCode, session.playerId, session.userId)) {
                        popUpTo(Routes.ONLINE) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.DIFFICULTY) {
            DifficultySelectionScreen(
                onBack = navController::popBackStack,
                onSelected = { navController.navigate(Routes.game(GameMode.VS_AI, it)) },
            )
        }
        composable(
            route = Routes.GAME,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("difficulty") { type = NavType.StringType },
                navArgument("roomCode") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("playerId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("userId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            GameRoute(
                onHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onWinner = { winner, state ->
                    navController.navigate(Routes.winner(winner, state.mode, state.difficulty)) {
                        popUpTo(Routes.GAME) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.WINNER,
            arguments = listOf(
                navArgument("winner") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType },
                navArgument("difficulty") { type = NavType.StringType },
            ),
        ) { entry ->
            val winner = enumValueOrDefault(entry.arguments?.getString("winner"), PlayerId.PLAYER_ONE)
            val mode = enumValueOrDefault(entry.arguments?.getString("mode"), GameMode.VS_AI)
            val difficulty = enumValueOrDefault(entry.arguments?.getString("difficulty"), Difficulty.MEDIUM)
            WinnerScreen(
                winner = winner,
                mode = mode,
                onReplay = {
                    if (mode == GameMode.ONLINE) {
                        navController.navigate(Routes.ONLINE) {
                            popUpTo(Routes.WINNER) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.game(mode, difficulty)) {
                            popUpTo(Routes.WINNER) { inclusive = true }
                        }
                    }
                },
                onHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onBack = navController::popBackStack,
                onThemeMode = viewModel::setThemeMode,
                onDynamicColor = viewModel::setDynamicColor,
                onSound = viewModel::setSoundEnabled,
                onHaptics = viewModel::setHapticsEnabled,
                onDifficulty = viewModel::setDifficulty,
            )
        }
        composable(Routes.STATISTICS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            StatisticsScreen(state.statistics, navController::popBackStack)
        }
    }
}
