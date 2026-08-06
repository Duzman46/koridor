package com.duzman46.gridbound.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.monetization.BillingState
import com.duzman46.gridbound.monetization.MonetizationState
import com.duzman46.gridbound.monetization.domain.Entitlement
import com.duzman46.gridbound.presentation.account.AccountEvent
import com.duzman46.gridbound.presentation.account.AccountViewModel
import com.duzman46.gridbound.presentation.auth.AuthEvent
import com.duzman46.gridbound.presentation.auth.AuthViewModel
import com.duzman46.gridbound.presentation.profile.ProfileViewModel
import com.duzman46.gridbound.presentation.settings.SettingsViewModel
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.session.SessionStatus
import com.duzman46.gridbound.ui.screens.AccountScreen
import com.duzman46.gridbound.ui.screens.DifficultySelectionScreen
import com.duzman46.gridbound.ui.screens.EditProfileScreen
import com.duzman46.gridbound.ui.screens.FriendsRoute
import com.duzman46.gridbound.ui.screens.GameRoute
import com.duzman46.gridbound.ui.screens.LeaderboardRoute
import com.duzman46.gridbound.ui.screens.LobbyTab
import com.duzman46.gridbound.ui.screens.MainMenuScreen
import com.duzman46.gridbound.ui.screens.ModeSelectionScreen
import com.duzman46.gridbound.ui.screens.OnlineLobbyRoute
import com.duzman46.gridbound.ui.screens.ProfileScreen
import com.duzman46.gridbound.ui.screens.SettingsScreen
import com.duzman46.gridbound.ui.screens.SplashScreen
import com.duzman46.gridbound.ui.screens.StatisticsScreen
import com.duzman46.gridbound.ui.screens.StoreScreen
import com.duzman46.gridbound.ui.screens.TutorialRoute
import com.duzman46.gridbound.ui.screens.UsernameScreen
import com.duzman46.gridbound.ui.screens.WinnerScreen
import com.duzman46.gridbound.ui.screens.auth.ForgotPasswordScreen
import com.duzman46.gridbound.ui.screens.auth.SignInScreen
import com.duzman46.gridbound.ui.screens.auth.SignUpScreen
import com.duzman46.gridbound.ui.screens.auth.WelcomeScreen
import com.duzman46.gridbound.ui.util.findActivity
import com.duzman46.gridbound.util.enumValueOrDefault

private object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val SIGN_IN = "signIn"
    const val SIGN_UP = "signUp"
    const val FORGOT_PASSWORD = "forgotPassword"
    const val USERNAME = "username"
    const val TUTORIAL = "tutorial"
    const val HOME = "home"
    const val MODE = "mode"
    const val DIFFICULTY = "difficulty"
    const val SETTINGS = "settings"
    const val STATISTICS = "statistics"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "editProfile"
    const val ACCOUNT = "account"
    const val LEADERBOARD = "leaderboard"
    const val FRIENDS = "friends"
    const val STORE = "store"
    const val ONLINE = "online?inviteCode={inviteCode}&tab={tab}"
    const val GAME = "game/{mode}/{difficulty}?roomCode={roomCode}&playerId={playerId}&userId={userId}"
    const val WINNER = "winner/{winner}/{mode}/{difficulty}"

    fun online(inviteCode: String = "", tab: LobbyTab = LobbyTab.PLAY): String =
        "online?inviteCode=$inviteCode&tab=${tab.name}"
    fun game(mode: GameMode, difficulty: Difficulty): String = "game/${mode.name}/${difficulty.name}"
    fun quickPlay(difficulty: Difficulty): String = game(GameMode.VS_AI, difficulty)
    fun onlineGame(roomCode: String, playerId: PlayerId, userId: String): String =
        "game/${GameMode.ONLINE.name}/${Difficulty.MEDIUM.name}?roomCode=$roomCode&playerId=${playerId.name}&userId=$userId"
    fun winner(winner: PlayerId, mode: GameMode, difficulty: Difficulty): String =
        "winner/${winner.name}/${mode.name}/${difficulty.name}"
}

@Composable
fun AppNavigation(
    session: SessionState,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    quickPlayDifficulty: Difficulty,
    monetization: MonetizationState,
    billing: BillingState,
    onBuy: (Entitlement) -> Unit,
    onRestorePurchases: () -> Unit,
    onPrivacyOptions: () -> Unit,
    onCompletedMatchExit: (onFinished: () -> Unit) -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val openUrl: (String) -> Unit = { url ->
        if (url.isNotBlank()) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, url.toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (error: ActivityNotFoundException) {
                AppLog.warn("open-url", error)
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            // Hold on the splash until both the intro animation and the first auth state
            // have landed, so the player is never routed on a LOADING session.
            var introFinished by rememberSaveable { mutableStateOf(false) }
            SplashScreen { introFinished = true }
            LaunchedEffect(introFinished, session.status) {
                if (introFinished && session.status != SessionStatus.LOADING) {
                    navController.navigateToEntryPoint(session)
                }
            }
        }

        composable(Routes.WELCOME) {
            val viewModel: AuthViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            viewModel.HandleEntryEvents(navController, session)
            WelcomeScreen(
                state = state,
                onGoogle = {
                    context.findActivity()?.let(viewModel::signInWithGoogle)
                },
                onEmailSignIn = { navController.navigate(Routes.SIGN_IN) },
                onCreateAccount = { navController.navigate(Routes.SIGN_UP) },
                onGuest = viewModel::continueAsGuest,
            )
        }

        composable(Routes.SIGN_IN) {
            val viewModel: AuthViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            viewModel.HandleEntryEvents(navController, session)
            SignInScreen(
                state = state,
                onBack = navController::popBackStack,
                onEmail = viewModel::setEmail,
                onPassword = viewModel::setPassword,
                onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                onSubmit = viewModel::signInWithEmail,
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
            )
        }

        composable(Routes.SIGN_UP) {
            val viewModel: AuthViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            viewModel.HandleEntryEvents(navController, session)
            SignUpScreen(
                state = state,
                onBack = navController::popBackStack,
                onEmail = viewModel::setEmail,
                onPassword = viewModel::setPassword,
                onConfirmPassword = viewModel::setConfirmPassword,
                onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                onSubmit = viewModel::createAccount,
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            val viewModel: AuthViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ForgotPasswordScreen(
                state = state,
                onBack = navController::popBackStack,
                onEmail = viewModel::setEmail,
                onSubmit = viewModel::sendPasswordReset,
            )
        }

        composable(Routes.USERNAME) {
            val viewModel: ProfileViewModel = hiltViewModel()
            val state by viewModel.editState.collectAsStateWithLifecycle()
            UsernameScreen(
                state = state,
                onBack = navController::popBackStack,
                onUsername = viewModel::setUsername,
                onSubmit = {
                    viewModel.saveUsername {
                        navController.navigateAfterEntry(session, popUpToRoute = Routes.USERNAME)
                    }
                },
            )
        }

        composable(Routes.TUTORIAL) {
            TutorialRoute(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.TUTORIAL) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.HOME) {
            MainMenuScreen(
                session = session,
                language = language,
                onLanguage = onLanguage,
                // Straight into a match against the AI at the difficulty already chosen in
                // settings. Quick play must not ask a question — the online lobby's own
                // quick match is one tap further in, behind "create"/"join".
                onQuickPlay = { navController.navigate(Routes.quickPlay(quickPlayDifficulty)) },
                onCreateRoom = { navController.navigate(Routes.online(tab = LobbyTab.CREATE)) },
                onJoinRoom = { navController.navigate(Routes.online(tab = LobbyTab.PLAY)) },
                onModes = { navController.navigate(Routes.MODE) },
                onFriends = { navController.navigate(Routes.FRIENDS) },
                onLeaderboard = { navController.navigate(Routes.LEADERBOARD) },
                onTutorial = { navController.navigate(Routes.TUTORIAL) },
                onProfile = { navController.navigate(Routes.PROFILE) },
                onStatistics = { navController.navigate(Routes.STATISTICS) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                showAdBanner = monetization.adsAllowed,
            )
        }

        composable(Routes.LEADERBOARD) {
            LeaderboardRoute(onBack = navController::popBackStack)
        }

        composable(Routes.FRIENDS) {
            FriendsRoute(
                onBack = navController::popBackStack,
                // The lobby owns joining, so an invitation lands there with the code already
                // filled in rather than duplicating the join logic on this screen.
                onJoinInvite = { roomCode -> navController.navigate(Routes.online(roomCode)) },
            )
        }

        composable(Routes.PROFILE) {
            val viewModel: ProfileViewModel = hiltViewModel()
            val state by viewModel.session.collectAsStateWithLifecycle()
            ProfileScreen(
                profile = state.profile,
                onBack = navController::popBackStack,
                onEdit = { navController.navigate(Routes.EDIT_PROFILE) },
            )
        }

        composable(Routes.EDIT_PROFILE) {
            val viewModel: ProfileViewModel = hiltViewModel()
            val state by viewModel.editState.collectAsStateWithLifecycle()
            EditProfileScreen(
                state = state,
                onBack = navController::popBackStack,
                onUsername = viewModel::setUsername,
                onDisplayName = viewModel::setDisplayName,
                onAvatar = viewModel::setAvatar,
                onSubmit = { viewModel.saveProfile { navController.popBackStack() } },
            )
        }

        composable(Routes.ACCOUNT) {
            val viewModel: AccountViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val accountSession by viewModel.session.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) {
                viewModel.events.collect { event ->
                    when (event) {
                        AccountEvent.SignedOut, AccountEvent.AccountDeleted ->
                            navController.navigate(Routes.WELCOME) {
                                popUpTo(0) { inclusive = true }
                            }

                        AccountEvent.Linked -> Unit
                    }
                }
            }
            AccountScreen(
                state = state,
                session = accountSession,
                onBack = navController::popBackStack,
                onEmail = viewModel::setEmail,
                onPassword = viewModel::setPassword,
                onLinkGoogle = {
                    context.findActivity()?.let(viewModel::linkWithGoogle)
                },
                onLinkEmail = viewModel::linkWithEmail,
                onSignOut = viewModel::signOut,
                onDeleteAccount = viewModel::deleteAccount,
                onOpenUrl = openUrl,
            )
        }

        composable(Routes.MODE) {
            ModeSelectionScreen(
                onBack = navController::popBackStack,
                onAi = { navController.navigate(Routes.DIFFICULTY) },
                onLocal = { navController.navigate(Routes.game(GameMode.LOCAL_TWO_PLAYER, Difficulty.MEDIUM)) },
                onOnline = { navController.navigate(Routes.online()) },
            )
        }

        composable(
            route = Routes.ONLINE,
            arguments = listOf(
                navArgument("inviteCode") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = LobbyTab.PLAY.name
                },
            ),
        ) { entry ->
            OnlineLobbyRoute(
                inviteCode = entry.arguments?.getString("inviteCode").orEmpty(),
                initialTab = enumValueOrDefault(entry.arguments?.getString("tab"), LobbyTab.PLAY),
                onBack = navController::popBackStack,
                onOpenGame = { onlineSession ->
                    navController.navigate(
                        Routes.onlineGame(
                            onlineSession.roomCode,
                            onlineSession.playerId,
                            onlineSession.userId,
                        ),
                    ) {
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
                    onCompletedMatchExit {
                        if (mode == GameMode.ONLINE) {
                            navController.navigate(Routes.online()) {
                                popUpTo(Routes.WINNER) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Routes.game(mode, difficulty)) {
                                popUpTo(Routes.WINNER) { inclusive = true }
                            }
                        }
                    }
                },
                onHome = {
                    onCompletedMatchExit {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
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
                onLanguage = viewModel::setLanguage,
                onThemeMode = viewModel::setThemeMode,
                onDynamicColor = viewModel::setDynamicColor,
                onSound = viewModel::setSoundEnabled,
                onHaptics = viewModel::setHapticsEnabled,
                onDifficulty = viewModel::setDifficulty,
                onReplayTutorial = { navController.navigate(Routes.TUTORIAL) },
                onAccount = { navController.navigate(Routes.ACCOUNT) },
                onStore = { navController.navigate(Routes.STORE) },
                onBoardTheme = viewModel::setBoardTheme,
                ownedEntitlements = billing.entitlements,
                monetization = monetization,
                onRestorePurchases = onRestorePurchases,
                onPrivacyOptions = onPrivacyOptions,
            )
        }

        composable(Routes.STORE) {
            StoreScreen(
                state = billing,
                isGuest = session.isGuest,
                onBack = { navController.popBackStack() },
                onBuy = onBuy,
                onRestore = onRestorePurchases,
            )
        }

        composable(Routes.STATISTICS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            StatisticsScreen(state.statistics, navController::popBackStack)
        }
    }
}

/**
 * Sends the player from the splash screen to wherever they belong: the welcome screen when
 * there is no identity, the tutorial when they have not learned the game, otherwise home.
 */
private fun NavHostController.navigateToEntryPoint(session: SessionState) {
    val destination = when {
        !session.hasEntered -> Routes.WELCOME
        !session.tutorialCompleted -> Routes.TUTORIAL
        else -> Routes.HOME
    }
    navigate(destination) {
        popUpTo(Routes.SPLASH) { inclusive = true }
    }
}

/** Where to land once sign-in succeeds: the tutorial gate still applies. */
private fun NavHostController.navigateAfterEntry(session: SessionState, popUpToRoute: String) {
    val destination = if (session.tutorialCompleted) Routes.HOME else Routes.TUTORIAL
    navigate(destination) {
        popUpTo(popUpToRoute) { inclusive = true }
    }
}

@Composable
private fun AuthViewModel.HandleEntryEvents(
    navController: NavHostController,
    session: SessionState,
) {
    LaunchedEffect(this) {
        events.collect { event ->
            when (event) {
                AuthEvent.Entered -> navController.navigate(
                    if (session.tutorialCompleted) Routes.HOME else Routes.TUTORIAL,
                ) {
                    popUpTo(0) { inclusive = true }
                }

                AuthEvent.NeedsUsername -> navController.navigate(Routes.USERNAME) {
                    popUpTo(0) { inclusive = true }
                }

                AuthEvent.PasswordResetSent -> navController.popBackStack()
            }
        }
    }
}
