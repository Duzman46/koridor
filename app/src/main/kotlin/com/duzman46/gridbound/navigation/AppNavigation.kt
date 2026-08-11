package com.duzman46.gridbound.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
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
import com.duzman46.gridbound.online.model.RoomEndReason
import com.duzman46.gridbound.presentation.account.AccountEvent
import com.duzman46.gridbound.presentation.account.AccountViewModel
import com.duzman46.gridbound.presentation.auth.AuthEvent
import com.duzman46.gridbound.presentation.auth.AuthViewModel
import com.duzman46.gridbound.presentation.profile.ProfileViewModel
import com.duzman46.gridbound.presentation.profile.RecentGamesViewModel
import com.duzman46.gridbound.presentation.settings.SettingsViewModel
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.session.SessionStatus
import com.duzman46.gridbound.ui.components.BillingNotice
import com.duzman46.gridbound.ui.components.RequestBar
import com.duzman46.gridbound.ui.screens.AccountScreen
import com.duzman46.gridbound.ui.screens.DifficultyScreen
import com.duzman46.gridbound.ui.screens.EditProfileScreen
import com.duzman46.gridbound.ui.screens.FriendsRoute
import com.duzman46.gridbound.ui.screens.GameRoute
import com.duzman46.gridbound.ui.screens.LeaderboardRoute
import com.duzman46.gridbound.ui.screens.MainMenuScreen
import com.duzman46.gridbound.ui.screens.OnlineLobbyRoute
import com.duzman46.gridbound.ui.screens.PlayModeScreen
import com.duzman46.gridbound.ui.screens.PlayerProfileRoute
import com.duzman46.gridbound.ui.screens.ProfileScreen
import com.duzman46.gridbound.ui.screens.SettingsScreen
import com.duzman46.gridbound.ui.screens.SplashScreen
import com.duzman46.gridbound.ui.screens.StatisticsScreen
import com.duzman46.gridbound.ui.screens.TutorialRoute
import com.duzman46.gridbound.ui.screens.UsernameScreen
import com.duzman46.gridbound.ui.screens.WinnerRoute
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
    const val PLAY = "play"
    const val DIFFICULTY = "difficulty"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val STATISTICS = "statistics"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "editProfile"
    const val ACCOUNT = "account"
    const val LEADERBOARD = "leaderboard"
    const val FRIENDS = "friends"
    const val PLAYER_PROFILE = "player?userId={userId}"
    const val ONLINE = "online?inviteCode={inviteCode}"
    const val GAME = "game/{mode}/{difficulty}?roomCode={roomCode}&playerId={playerId}&userId={userId}&seat={seat}"
    const val WINNER = "winner/{winner}/{mode}/{difficulty}" +
        "?seat={seat}&endReason={endReason}&roomCode={roomCode}&opponentId={opponentId}"

    fun online(inviteCode: String = ""): String = "online?inviteCode=$inviteCode"

    fun entry(destination: EntryDestination): String = when (destination) {
        EntryDestination.WELCOME -> WELCOME
        EntryDestination.TUTORIAL -> TUTORIAL
        EntryDestination.USERNAME -> USERNAME
        EntryDestination.HOME -> HOME
    }

    /** Encoded: a user id is opaque, and one stray character would silently split the route. */
    fun playerProfile(userId: String): String = "player?userId=${Uri.encode(userId)}"
    fun game(mode: GameMode, difficulty: Difficulty, seat: PlayerId = PlayerId.PLAYER_ONE): String =
        "game/${mode.name}/${difficulty.name}?seat=${seat.name}"

    fun onlineGame(roomCode: String, playerId: PlayerId, userId: String): String =
        "game/${GameMode.ONLINE.name}/${Difficulty.MEDIUM.name}?roomCode=$roomCode&playerId=${playerId.name}&userId=$userId"
    /**
     * @param roomCode the finished online room, and [opponentId] who else was in it. The two
     *   of them are what the winner screen needs to offer a rematch; both are empty off line.
     */
    fun winner(
        winner: PlayerId,
        mode: GameMode,
        difficulty: Difficulty,
        seat: PlayerId = PlayerId.PLAYER_ONE,
        endReason: RoomEndReason = RoomEndReason.NORMAL,
        roomCode: String = "",
        opponentId: String = "",
    ): String =
        "winner/${winner.name}/${mode.name}/${difficulty.name}" +
            "?seat=${seat.name}&endReason=${endReason.name}" +
            "&roomCode=$roomCode&opponentId=${Uri.encode(opponentId)}"
}

/**
 * How long one screen takes to become another.
 *
 * Asymmetric on purpose. The screen being asked for should feel like it is settling into place,
 * so it takes its time; the one being left has already been dismissed and lingering over it is
 * how an interface starts to feel slow. Material's own guidance is the same shape, and the
 * difference is small enough that nobody could name it and large enough that everybody feels it.
 */
private const val NAV_ENTER_MILLIS = 280
private const val NAV_EXIT_MILLIS = 220

/**
 * The curve everything on this screen moves on: quick to leave, slow to arrive.
 *
 * Motion that starts fast and eases out reads as something being placed. A linear slide reads as
 * something being dragged, which is what the previous cross-fade avoided by not moving at all.
 */
private fun <T> navEnterSpec() = tween<T>(NAV_ENTER_MILLIS, easing = FastOutSlowInEasing)

private fun <T> navExitSpec() = tween<T>(NAV_EXIT_MILLIS, easing = FastOutSlowInEasing)

/** The fade runs shorter than the slide, so a screen is legible before it stops moving. */
private fun navEnterFade() = tween<Float>(NAV_ENTER_MILLIS - 80, easing = LinearOutSlowInEasing)

private fun navExitFade() = tween<Float>(NAV_EXIT_MILLIS - 60, easing = FastOutLinearInEasing)

@Composable
fun AppNavigation(
    session: SessionState,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    monetization: MonetizationState,
    billing: BillingState,
    onBuy: (Entitlement) -> Unit,
    onRestorePurchases: () -> Unit,
    onDismissBillingMessage: () -> Unit,
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

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            // Screens travel sideways, and the two of them travel together.
            //
            // The one arriving comes in from the leading edge under a fade; the one leaving
            // slides a shorter distance the same way and dims as it goes. Unequal distances are
            // what makes it read as depth rather than as a carousel — the outgoing screen is
            // being covered, not pushed off. Going back plays the same motion mirrored, so the
            // graph has a direction a player can feel.
            //
            // 280 ms in, 220 ms out, on the platform's own deceleration curve. That was 90 ms
            // for a while, which stopped being a transition and started being a cut; the app
            // was responsive and it looked broken. The responsiveness never depended on this
            // number anyway — see navigateFrom, where the taps were actually being dropped.
            enterTransition = {
                slideInHorizontally(navEnterSpec()) { width -> width / 6 } + fadeIn(navEnterFade())
            },
            exitTransition = {
                slideOutHorizontally(navExitSpec()) { width -> -width / 14 } + fadeOut(navExitFade())
            },
            popEnterTransition = {
                slideInHorizontally(navEnterSpec()) { width -> -width / 6 } + fadeIn(navEnterFade())
            },
            popExitTransition = {
                slideOutHorizontally(navExitSpec()) { width -> width / 14 } + fadeOut(navExitFade())
            },
        ) {
            composable(Routes.SPLASH) {
                // Hold on the splash until both the intro animation and the first auth state
                // have landed, so the player is never routed on a LOADING session.
                var introFinished by rememberSaveable { mutableStateOf(false) }
                SplashScreen { introFinished = true }
                LaunchedEffect(introFinished, session.status) {
                    if (introFinished && session.status != SessionStatus.LOADING) {
                        navController.navigateToEntry(session) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                }
            }

            composable(Routes.WELCOME) { entry ->
                val viewModel: AuthViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                viewModel.HandleEntryEvents(navController, session)
                WelcomeScreen(
                    state = state,
                    onGoogle = {
                        context.findActivity()?.let(viewModel::signInWithGoogle)
                    },
                    onEmailSignIn = { navController.navigateFrom(entry, Routes.SIGN_IN) },
                    onCreateAccount = { navController.navigateFrom(entry, Routes.SIGN_UP) },
                    onGuest = viewModel::continueAsGuest,
                )
            }

            composable(Routes.SIGN_IN) { entry ->
                val viewModel: AuthViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                viewModel.HandleEntryEvents(navController, session)
                SignInScreen(
                    state = state,
                    onBack = { navController.popFrom(entry) },
                    onEmail = viewModel::setEmail,
                    onPassword = viewModel::setPassword,
                    onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                    onSubmit = viewModel::signInWithEmail,
                    onForgotPassword = { navController.navigateFrom(entry, Routes.FORGOT_PASSWORD) },
                )
            }

            composable(Routes.SIGN_UP) { entry ->
                val viewModel: AuthViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                viewModel.HandleEntryEvents(navController, session)
                SignUpScreen(
                    state = state,
                    onBack = { navController.popFrom(entry) },
                    onEmail = viewModel::setEmail,
                    onPassword = viewModel::setPassword,
                    onConfirmPassword = viewModel::setConfirmPassword,
                    onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                    onSubmit = viewModel::createAccount,
                )
            }

            composable(Routes.FORGOT_PASSWORD) { entry ->
                val viewModel: AuthViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ForgotPasswordScreen(
                    state = state,
                    onBack = { navController.popFrom(entry) },
                    onEmail = viewModel::setEmail,
                    onSubmit = viewModel::sendPasswordReset,
                )
            }

            composable(Routes.USERNAME) {
                val viewModel: ProfileViewModel = hiltViewModel()
                val state by viewModel.editState.collectAsStateWithLifecycle()
                UsernameScreen(
                    state = state,
                    onUsername = viewModel::setUsername,
                    onSubmit = {
                        viewModel.saveUsername {
                            navController.navigateToEntry(session, EntryStep.USERNAME) {
                                popUpTo(Routes.USERNAME) { inclusive = true }
                            }
                        }
                    },
                )
            }

            composable(Routes.TUTORIAL) {
                TutorialRoute(
                    // Skipping and finishing land in the same place on purpose: what the
                    // skip button skips is the lesson, not the account it belongs to.
                    onFinished = {
                        navController.navigateToEntry(session, EntryStep.TUTORIAL) {
                            popUpTo(Routes.TUTORIAL) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.HOME) { entry ->
                MainMenuScreen(
                    session = session,
                    language = language,
                    offersAdRemoval = billing.offersAdRemoval,
                    onLanguage = onLanguage,
                    onPlay = { navController.navigateFrom(entry, Routes.PLAY) },
                    onFriends = { navController.navigateFrom(entry, Routes.FRIENDS) },
                    onLeaderboard = { navController.navigateFrom(entry, Routes.LEADERBOARD) },
                    onTutorial = { navController.navigateFrom(entry, Routes.TUTORIAL) },
                    onProfile = { navController.navigateFrom(entry, Routes.PROFILE) },
                    onStatistics = { navController.navigateFrom(entry, Routes.STATISTICS) },
                    onSettings = { navController.navigateFrom(entry, Routes.SETTINGS) },
                    onRemoveAds = {
                        // A guest has no account for Play to attach the purchase to, so it would
                        // not survive a reinstall or follow them to another device. Link first.
                        if (session.isGuest) {
                            navController.navigateFrom(entry, Routes.ACCOUNT)
                        } else {
                            onBuy(Entitlement.REMOVE_ADS)
                        }
                    },
                    onRestorePurchases = onRestorePurchases,
                    onOpenUrl = openUrl,
                    showAdBanner = monetization.adsAllowed,
                )
            }

            composable(Routes.PLAY) { entry ->
                PlayModeScreen(
                    onBack = { navController.popFrom(entry) },
                    onVsBot = { navController.navigateFrom(entry, Routes.DIFFICULTY) },
                    onLocal = {
                        navController.navigateFrom(
                            entry,
                            Routes.game(GameMode.LOCAL_TWO_PLAYER, Difficulty.MEDIUM),
                        )
                    },
                    onOnline = { navController.navigateFrom(entry, Routes.online()) },
                    showAdBanner = monetization.adsAllowed,
                )
            }

            composable(Routes.DIFFICULTY) { entry ->
                DifficultyScreen(
                    onBack = { navController.popFrom(entry) },
                    onSelected = { difficulty, seat ->
                        navController.navigateFrom(entry, Routes.game(GameMode.VS_AI, difficulty, seat))
                    },
                )
            }

            composable(Routes.LEADERBOARD) { entry ->
                LeaderboardRoute(
                    onBack = { navController.popFrom(entry) },
                    onLinkAccount = { navController.navigateFrom(entry, Routes.ACCOUNT) },
                    onOpenProfile = { userId ->
                        navController.navigateFrom(entry, Routes.playerProfile(userId))
                    },
                )
            }

            composable(Routes.FRIENDS) { entry ->
                FriendsRoute(
                    onBack = { navController.popFrom(entry) },
                    // The lobby owns joining, so an invitation lands there with the code already
                    // filled in rather than duplicating the join logic on this screen.
                    onJoinInvite = { roomCode ->
                        navController.navigateFrom(entry, Routes.online(roomCode))
                    },
                    onLinkAccount = { navController.navigateFrom(entry, Routes.ACCOUNT) },
                    // Popped, like the lobby's own hand-off: the room the friends screen was
                    // holding open has been taken, so there is nothing left there to go back to.
                    onOpenGame = { onlineSession ->
                        navController.navigate(
                            Routes.onlineGame(
                                onlineSession.roomCode,
                                onlineSession.playerId,
                                onlineSession.userId,
                            ),
                        ) {
                            popUpTo(Routes.FRIENDS) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = Routes.PLAYER_PROFILE,
                arguments = listOf(navArgument("userId") { type = NavType.StringType }),
            ) { entry ->
                PlayerProfileRoute(
                    onBack = { navController.popFrom(entry) },
                    // A history row on somebody else's page names a third player, and opening
                    // them is the same destination this screen already is — so it stacks rather
                    // than replaces, and back walks the chain the player actually followed.
                    onOpenPlayer = { userId ->
                        navController.navigateFrom(entry, Routes.playerProfile(userId))
                    },
                )
            }

            composable(Routes.PROFILE) { entry ->
                val viewModel: ProfileViewModel = hiltViewModel()
                val state by viewModel.session.collectAsStateWithLifecycle()
                // The same view model another player's page uses. This route carries no user
                // id, and that absence is what tells it to ask about the signed-in player.
                val recentGamesViewModel: RecentGamesViewModel = hiltViewModel()
                val recentGames by recentGamesViewModel.state.collectAsStateWithLifecycle()
                ProfileScreen(
                    profile = state.profile,
                    // Not "signed in": an ordinary guest signs in anonymously and is
                    // SIGNED_IN too, so testing that spun forever for exactly the players who
                    // have no profile. What decides it is whether a profile can exist at all.
                    hasAccount = state.canUseSocialFeatures,
                    recentGames = recentGames,
                    onBack = { navController.popFrom(entry) },
                    onEdit = { navController.navigateFrom(entry, Routes.EDIT_PROFILE) },
                    onAccount = { navController.navigateFrom(entry, Routes.ACCOUNT) },
                    onLeaderboard = { navController.navigateFrom(entry, Routes.LEADERBOARD) },
                    onFriends = { navController.navigateFrom(entry, Routes.FRIENDS) },
                    // A rival from a finished match, reached from the row that remembers them.
                    onOpenPlayer = { userId ->
                        navController.navigateFrom(entry, Routes.playerProfile(userId))
                    },
                )
            }

            composable(Routes.EDIT_PROFILE) { entry ->
                val viewModel: ProfileViewModel = hiltViewModel()
                val state by viewModel.editState.collectAsStateWithLifecycle()
                val profileSession by viewModel.session.collectAsStateWithLifecycle()
                EditProfileScreen(
                    state = state,
                    canChangeUsername = profileSession.canChangeUsername,
                    onBack = { navController.popFrom(entry) },
                    onUsername = viewModel::setUsername,
                    onAvatar = viewModel::setAvatar,
                    onLinkAccount = { navController.navigateFrom(entry, Routes.ACCOUNT) },
                    onSubmit = { viewModel.saveProfile { navController.popScreen() } },
                )
            }

            composable(Routes.ACCOUNT) { entry ->
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

                            // An account that just became real is still carrying the name the
                            // app handed its guest, and that name is about to be public. The
                            // gate every other account passes through has to be reached from
                            // here too; the event says so itself because the session behind
                            // this screen has not necessarily caught up yet.
                            is AccountEvent.Linked ->
                                if (event.needsUsername) {
                                    navController.navigate(Routes.USERNAME) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                        }
                    }
                }
                AccountScreen(
                    state = state,
                    session = accountSession,
                    onBack = { navController.popFrom(entry) },
                    onEmail = viewModel::setEmail,
                    onPassword = viewModel::setPassword,
                    onLinkGoogle = {
                        context.findActivity()?.let(viewModel::linkWithGoogle)
                    },
                    onLinkEmail = viewModel::linkWithEmail,
                    onSignInToExistingAccount = viewModel::signInToExistingAccount,
                    onDismissExistingAccount = viewModel::dismissExistingAccountWarning,
                    onSignOut = viewModel::signOut,
                    // Passed through even when it is null: an Activity that cannot be found
                    // is something the player needs told, not a tap that quietly does nothing.
                    onDeleteAccount = { viewModel.deleteAccount(context.findActivity()) },
                    onOpenUrl = openUrl,
                )
            }

            composable(
                route = Routes.ONLINE,
                arguments = listOf(
                    navArgument("inviteCode") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                OnlineLobbyRoute(
                    inviteCode = entry.arguments?.getString("inviteCode").orEmpty(),
                    onBack = { navController.popFrom(entry) },
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
                    navArgument("seat") {
                        type = NavType.StringType
                        defaultValue = PlayerId.PLAYER_ONE.name
                    },
                ),
            ) { entry ->
                GameRoute(
                    // Walking out of a board is a match ending, so it earns the same ad the
                    // victory screen's exits do. It runs after the game screen has already
                    // confirmed the departure and freed an online seat, never on the press
                    // itself: an ad in front of a dialog the player has not answered is the
                    // placement AdMob refuses and players uninstall over.
                    onHome = {
                        onCompletedMatchExit {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    },
                    onCompletedMatchExit = onCompletedMatchExit,
                    onSettings = { navController.navigateFrom(entry, Routes.SETTINGS) },
                    onOpenProfile = { userId ->
                        navController.navigateFrom(entry, Routes.playerProfile(userId))
                    },
                    onWinner = { winner, state ->
                        navController.navigate(
                            Routes.winner(
                                winner = winner,
                                mode = state.mode,
                                difficulty = state.difficulty,
                                seat = state.localPlayer,
                                endReason = state.onlineEndReason ?: RoomEndReason.NORMAL,
                                roomCode = entry.arguments?.getString("roomCode").orEmpty(),
                                opponentId = state.opponentUserId,
                            ),
                        ) {
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
                    navArgument("seat") {
                        type = NavType.StringType
                        defaultValue = PlayerId.PLAYER_ONE.name
                    },
                    navArgument("endReason") {
                        type = NavType.StringType
                        defaultValue = RoomEndReason.NORMAL.name
                    },
                    navArgument("roomCode") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("opponentId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val winner = enumValueOrDefault(entry.arguments?.getString("winner"), PlayerId.PLAYER_ONE)
                val seat = enumValueOrDefault(entry.arguments?.getString("seat"), PlayerId.PLAYER_ONE)
                val mode = enumValueOrDefault(entry.arguments?.getString("mode"), GameMode.VS_AI)
                val difficulty = enumValueOrDefault(entry.arguments?.getString("difficulty"), Difficulty.MEDIUM)
                val endReason =
                    enumValueOrDefault(entry.arguments?.getString("endReason"), RoomEndReason.NORMAL)
                WinnerRoute(
                    winner = winner,
                    mode = mode,
                    localPlayer = seat,
                    endReason = endReason,
                    playedRoomCode = entry.arguments?.getString("roomCode").orEmpty(),
                    opponentUserId = entry.arguments?.getString("opponentId").orEmpty(),
                    onRematchAccepted = { rematch ->
                        onCompletedMatchExit {
                            navController.navigate(
                                Routes.onlineGame(
                                    rematch.roomCode,
                                    rematch.playerId,
                                    rematch.userId,
                                ),
                            ) {
                                popUpTo(Routes.WINNER) { inclusive = true }
                            }
                        }
                    },
                    onReplay = {
                        onCompletedMatchExit {
                            if (mode == GameMode.ONLINE) {
                                navController.navigate(Routes.online()) {
                                    popUpTo(Routes.WINNER) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Routes.game(mode, difficulty, seat)) {
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

            composable(Routes.SETTINGS) { entry ->
                val viewModel: SettingsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    onBack = { navController.popFrom(entry) },
                    onLanguage = viewModel::setLanguage,
                    onThemeMode = viewModel::setThemeMode,
                    onSound = viewModel::setSoundEnabled,
                    onHaptics = viewModel::setHapticsEnabled,
                    onMatchMessages = viewModel::setMatchMessagesEnabled,
                    onAccount = { navController.navigateFrom(entry, Routes.ACCOUNT) },
                    monetization = monetization,
                    onPrivacyOptions = onPrivacyOptions,
                    offersAdRemoval = billing.offersAdRemoval,
                    isGuest = session.isGuest,
                    onRemoveAds = {
                        // Same rule as the home sheet: a guest has no account for Play to
                        // attach the purchase to, so it is the account screen they need first.
                        if (session.isGuest) {
                            navController.navigateFrom(entry, Routes.ACCOUNT)
                        } else {
                            onBuy(Entitlement.REMOVE_ADS)
                        }
                    },
                    onRestorePurchases = onRestorePurchases,
                )
            }

            composable(Routes.STATISTICS) { entry ->
                val viewModel: SettingsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                StatisticsScreen(state.statistics) { navController.popFrom(entry) }
            }
        }

        // Hung beside the graph rather than inside a screen, so a request reaches the
        // player wherever they are. See [RequestBar] for why it sits where it does.
        RequestBar(
            onOpenGame = { session ->
                val open = {
                    navController.navigate(
                        Routes.onlineGame(session.roomCode, session.playerId, session.userId),
                    ) {
                        // A match accepted from inside another one replaces it rather than
                        // stacking a second board on top of the first, and the same goes for the
                        // finished match's victory screen.
                        popUpTo(Routes.GAME) { inclusive = true }
                        popUpTo(Routes.WINNER) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                // Accepting a rematch is how the player who was *asked* leaves a finished match,
                // and it is the only way out of one that did not pass through the ad. It is the
                // same departure the winner screen's own buttons make, so it earns the same ad.
                //
                // Conditional, and that is the whole point of the check: the identical bar
                // accepts an invitation from the home screen or the friends list, where nothing
                // is being left and an ad would arrive in front of a player who has just asked
                // to start playing.
                val route = navController.currentDestination?.route
                if (route == Routes.GAME || route == Routes.WINNER) {
                    onCompletedMatchExit(open)
                } else {
                    open()
                }
            },
            onOpenLobby = { roomCode -> navController.navigate(Routes.online(roomCode)) },
        )

        BillingNotice(message = billing.message, onDismiss = onDismissBillingMessage)
    }
}

/**
 * A screen keeps drawing — and keeps taking touches, on top of the screen replacing it — for
 * the whole of its exit transition. A tap that lands in that window was aimed at the screen
 * the player is looking at, not at the one on its way out, so only the destination that is
 * still resumed is allowed to move them. Without this, backing out of the difficulty screen
 * and immediately picking a mode starts the bot match the difficulty screen was still
 * offering.
 *
 * This is for navigation a tap causes there and then. Navigation that arrives later — from a
 * repository event, or after another activity has been in front, as the interstitial is
 * between the winner screen and its exits — legitimately runs while the entry is not resumed
 * and must not be dropped.
 */
/**
 * Navigates, unless the screen that asked has already been left.
 *
 * The test is stack identity rather than lifecycle state. Both answer "is this still the screen
 * the player is pressing", and only one of them answers immediately: an entry does not reach
 * RESUMED until its arrival animation has finished, so guarding on that threw away every tap
 * made during a transition. See [canLeaveScreen] for the whole of it.
 */
private fun NavHostController.navigateFrom(
    entry: NavBackStackEntry,
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (isCurrent(entry)) navigate(route, builder)
}

/**
 * Whether [entry] is still the top of the stack.
 *
 * It stops being so the instant a navigation commits — which is the moment its controls should
 * stop working, rather than one animation later.
 */
private fun NavHostController.isCurrent(entry: NavBackStackEntry): Boolean =
    currentBackStackEntry?.id == entry.id

/**
 * Leaves a screen because the player pressed its back arrow. The mirror of [navigateFrom], and
 * for the same reason: see [canLeaveScreen].
 *
 * Every back arrow in the graph goes through here rather than through `popBackStack` itself,
 * because the tap that over-pops is never aimed at the screen it takes away — it is the second
 * half of a double tap on a control that is already on its way out.
 */
private fun NavHostController.popFrom(entry: NavBackStackEntry) {
    val beneath = previousBackStackEntry?.destination?.route
    if (canLeaveScreen(isCurrent(entry), beneath)) popBackStack()
}

/**
 * Leaves a screen because something other than a press said so — a save that has landed, or an
 * event from a repository. Held only to the backstop; see [canLeaveScreenUnprompted].
 */
private fun NavHostController.popScreen() {
    if (canLeaveScreenUnprompted(previousBackStackEntry?.destination?.route)) popBackStack()
}

/**
 * Moves the player on through the entry sequence, clearing the screen they are leaving.
 *
 * Every hand-off between the splash, the welcome screens, the tutorial and the username
 * picker goes through here, so there is exactly one answer to "where does this player
 * belong" and no screen can hold an opinion of its own. [entryDestinationFor] is that
 * answer; this only turns it into a route and decides what to erase behind it.
 */
private fun NavHostController.navigateToEntry(
    session: SessionState,
    justCompleted: EntryStep = EntryStep.NONE,
    clearBackStack: NavOptionsBuilder.() -> Unit,
) {
    navigate(Routes.entry(entryDestinationFor(session, justCompleted)), clearBackStack)
}

/**
 * Turns the auth screens' one outcome into a move through the entry sequence.
 *
 * The collector is keyed on the view model alone, so it outlives every recomposition and is
 * still running when the answer finally arrives. That makes the session it reads a matter of
 * timing rather than of taste: a plain capture would freeze the state as it was when the
 * welcome screen was drawn — before this player existed — and the gate would then be decided
 * on the previous occupant of the device. Somebody signing in after a guest had finished the
 * tutorial here would be waved through unnamed on that reading. [rememberUpdatedState] is
 * what keeps the long-lived collector reading the session as it is now.
 */
@Composable
private fun AuthViewModel.HandleEntryEvents(
    navController: NavHostController,
    session: SessionState,
) {
    val currentSession by rememberUpdatedState(session)
    LaunchedEffect(this) {
        events.collect { event ->
            when (event) {
                // The whole stack goes: none of the screens a player signed in from is
                // somewhere they can go back to now that they have.
                AuthEvent.Entered ->
                    navController.navigateToEntry(currentSession, EntryStep.ENTRY) {
                        popUpTo(0) { inclusive = true }
                    }

                AuthEvent.PasswordResetSent -> navController.popScreen()
            }
        }
    }
}
