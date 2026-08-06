package com.duzman46.gridbound.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.AdBanner
import com.duzman46.gridbound.ui.components.CenteredContent
import com.duzman46.gridbound.ui.components.KoridorMark
import com.duzman46.gridbound.ui.components.LanguagePickerDialog
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.ui.components.home.BoardShowcase
import com.duzman46.gridbound.ui.components.home.Destination
import com.duzman46.gridbound.ui.components.home.DestinationChips
import com.duzman46.gridbound.ui.components.home.GlyphKind
import com.duzman46.gridbound.ui.components.home.PlaySlab
import com.duzman46.gridbound.ui.components.home.RoomPair
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.SelectionCard
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(Constants.Animation.SPLASH_DURATION_MILLIS)
        onFinished()
    }
    val transition = rememberInfiniteTransition(label = "splash")
    val scale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "logoScale",
    )
    ScreenBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXl),
            ) {
                KoridorMark(Modifier.size(104.dp).scale(scale))
                Text(
                    stringResource(R.string.app_name).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

/**
 * The home screen.
 *
 * A real Koridor position fills the top of the screen — mid-game, walls placed, one pawn
 * forced the long way round. Below it: one loud action, the two room actions, and everything
 * else as a wrapping row of quiet chips. No paragraph explains any of it; the board does.
 */
@Composable
fun MainMenuScreen(
    session: SessionState,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onQuickPlay: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onModes: () -> Unit,
    onFriends: () -> Unit,
    onLeaderboard: () -> Unit,
    onTutorial: () -> Unit,
    onProfile: () -> Unit,
    onStatistics: () -> Unit,
    onSettings: () -> Unit,
    showAdBanner: Boolean,
) {
    var languagePickerOpen by remember { mutableStateOf(false) }
    if (languagePickerOpen) {
        LanguagePickerDialog(
            selected = language,
            onSelect = {
                languagePickerOpen = false
                onLanguage(it)
            },
            onDismiss = { languagePickerOpen = false },
        )
    }

    val destinations = listOf(
        Destination(stringResource(R.string.menu_modes), GlyphKind.MODES, onModes),
        Destination(stringResource(R.string.leaderboard_title), GlyphKind.LEADERBOARD, onLeaderboard),
        Destination(stringResource(R.string.friends_title), GlyphKind.FRIENDS, onFriends),
        Destination(stringResource(R.string.menu_tutorial), GlyphKind.TUTORIAL, onTutorial),
        Destination(stringResource(R.string.menu_statistics), GlyphKind.STATISTICS, onStatistics),
        Destination(stringResource(R.string.game_settings), GlyphKind.SETTINGS, onSettings),
    )

    Scaffold(
        // The banner is a bottom bar, not an overlay: the menu is laid out above it instead
        // of having its last row covered on short screens. Scaffold hands the content the
        // bar's height but not the system bar behind it, so the inset is added here.
        bottomBar = { if (showAdBanner) AdBanner(Modifier.navigationBarsPadding()) },
    ) { padding ->
        ScreenBackground {
            Column(
                Modifier
                    .fillMaxSize()
                    // Keeping the Scaffold's top inset is what stops the dark well running
                    // under the status bar, where light-theme status icons would vanish.
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = Dimens.MenuMaxWidth)
                        .padding(horizontal = Dimens.ScreenPadding),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Spacer(Modifier.height(Dimens.SpaceLg))
                    BoardShowcase(
                        session = session,
                        language = language,
                        onProfile = onProfile,
                        onLanguage = { languagePickerOpen = true },
                    )
                    Spacer(Modifier.height(20.dp))
                    PlaySlab(stringResource(R.string.menu_quick_play), onQuickPlay)
                    Spacer(Modifier.height(10.dp))
                    RoomPair(
                        createLabel = stringResource(R.string.menu_create_room),
                        joinLabel = stringResource(R.string.menu_join_room),
                        onCreate = onCreateRoom,
                        onJoin = onJoinRoom,
                    )
                    Spacer(Modifier.height(20.dp))
                    DestinationChips(destinations)
                    Spacer(Modifier.height(Dimens.SpaceXl))
                }
            }
        }
    }
}

@Composable
fun ModeSelectionScreen(onBack: () -> Unit, onAi: () -> Unit, onLocal: () -> Unit, onOnline: () -> Unit) {
    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.mode_title), onBack) }) { padding ->
        ScreenBackground {
            CenteredContent(Modifier.padding(padding)) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = Dimens.MenuMaxWidth),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                ) {
                    SelectionCard(stringResource(R.string.mode_vs_ai), stringResource(R.string.mode_vs_ai_description), Icons.Rounded.SmartToy, onAi)
                    SelectionCard(stringResource(R.string.mode_two_players), stringResource(R.string.mode_two_players_description), Icons.Rounded.SportsEsports, onLocal)
                    SelectionCard(stringResource(R.string.mode_online), stringResource(R.string.mode_online_description), Icons.Rounded.Wifi, onOnline)
                }
            }
        }
    }
}

@Composable
fun DifficultySelectionScreen(onBack: () -> Unit, onSelected: (Difficulty) -> Unit) {
    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.difficulty_title), onBack) }) { padding ->
        ScreenBackground {
            CenteredContent(Modifier.padding(padding)) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = Dimens.MenuMaxWidth),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                ) {
                    SelectionCard(stringResource(R.string.difficulty_easy), stringResource(R.string.difficulty_easy_description), Icons.Rounded.Bolt, onClick = { onSelected(Difficulty.EASY) })
                    SelectionCard(stringResource(R.string.difficulty_medium), stringResource(R.string.difficulty_medium_description), Icons.Rounded.SmartToy, onClick = { onSelected(Difficulty.MEDIUM) })
                    SelectionCard(stringResource(R.string.difficulty_hard), stringResource(R.string.difficulty_hard_description), Icons.Rounded.Psychology, onClick = { onSelected(Difficulty.HARD) })
                }
            }
        }
    }
}
