package com.duzman46.gridbound.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.ui.components.ScreenBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import com.duzman46.gridbound.ui.components.home.HomeBrand
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.navigation.DockedBarSpace
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.KoridorMark
import com.duzman46.gridbound.ui.components.LanguagePickerDialog
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.ui.components.home.HomeHero
import com.duzman46.gridbound.ui.components.home.HomeGridRow
import com.duzman46.gridbound.ui.components.home.HomeMenuCard
import com.duzman46.gridbound.ui.components.home.PremiumTopBar
import com.duzman46.gridbound.ui.components.home.PrimaryPlayCard
import com.duzman46.gridbound.ui.components.home.WideMenuCard
import androidx.compose.material3.Scaffold
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
                // The home screen's own brand block, not a second setting of the same words.
                //
                // What stood here was a plain uppercased Text, and it was wrong twice. It called
                // String.uppercase() with no locale, which maps i to I in the root locale — so
                // the first thing a Turkish player saw was KORIDOR, the app's name misspelt, and
                // the home screen one screen later said KORİDOR. And it set the name in Black at
                // a tight track, where HomeWordmark sets it Light and widely tracked with a wall
                // piece standing in for the upright letter. Two different wordmarks a heartbeat
                // apart is not a splash, it is a mismatch.
                //
                // HomeBrand carries the tagline with it, which is the point: the promise belongs
                // on the screen that has nothing else to say.
                HomeBrand()
            }
        }
    }
}

/**
 * Everything under the scene, added up.
 *
 * The scene is the only element on this screen that can be any size, so it takes what is left
 * of the window after this — which is the one arrangement where the whole screen lands in one
 * frame on a phone rather than asking the player to scroll for the play button.
 *
 * Written as the rows it is made of rather than as one total, so a spacing token that moves
 * takes the stack with it. A row added to the screen has to be added here too, or the screen
 * starts scrolling again.
 */
/** The wordmark, its gap and the tagline, at default font scale. */
internal val HomeBrandBlock: Dp = Dimens.SpaceSm + 42.dp + Dimens.SpaceSm + 20.dp

/** What [com.duzman46.gridbound.ui.components.home.PrimaryPlayCard] measures at its minimum. */
internal val PlayCardHeight: Dp = 92.dp

/** One card of the two-up grid. */
internal val MenuCardHeight: Dp = 78.dp

/** The full-width card under the grid. */
internal val WideCardHeight: Dp = 72.dp

/** The docked bar and the padding it floats in, which content never sits behind. */
internal val DockedBarBlock: Dp = 70.dp + Dimens.SpaceSm * 2

internal val HomeStackUnderScene: Dp =
    HomeBrandBlock +
        Dimens.SpaceLg + PlayCardHeight +
        Dimens.SpaceMd + MenuCardHeight +
        Dimens.SpaceMd + MenuCardHeight +
        Dimens.SpaceMd + WideCardHeight +
        Dimens.SpaceMd

/**
 * The home screen.
 *
 * One way in and the four destinations a player asks for by name: learn it, see the table,
 * change something, everything else. Everything the app can do used to be a button here,
 * which made the first screen a directory; the rest now lives behind "More". Friends,
 * language and the player's own profile are small marks in a row of their own, not entries in
 * the list — they are people and preferences, not ways to start a game.
 *
 * Read top to bottom the screen is: who you are and who you know, the name of the game, the
 * game itself, then how to start one. The panel in the middle holds nothing but artwork, which
 * is the only arrangement in which all of the artwork can be seen.
 *
 * It still scrolls, and that is not a contradiction: at a doubled font scale, or in landscape,
 * five controls alone are taller than the window and something has to give. What the budget
 * buys is that nobody at default settings ever has to scroll to find the way in.
 */
@Composable
fun MainMenuScreen(
    session: SessionState,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onPlay: () -> Unit,
    onFriends: () -> Unit,
    onLeaderboard: () -> Unit,
    onProfile: () -> Unit,
    onTutorial: () -> Unit,
    onSettings: () -> Unit,
    onMore: () -> Unit,
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

    // "More" used to open a bottom sheet from here. It is a screen now — see [MoreScreen] — so
    // the store, the statistics, the badges and the policy links all have an address a player
    // can arrive at and go back from, rather than living in a panel that only exists while a
    // finger is holding it open.

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // No ad banner on this screen. The docked bar is the last thing above the system's
        // navigation, and a banner beneath it pushed the bar up into the middle of the content
        // — the one place a navigation bar must never be. Interstitials still run; the home
        // screen simply is not where the app asks for money.
        // The docked bar is drawn once outside the navigation host now, above every place it
        // switches between, so it no longer animates with the screen under it.
    ) { padding ->
        // No scroll, and no height budget either.
        //
        // Both previous attempts added up the rows by hand and handed the scene what was left,
        // and both were wrong on the device — a card measures what its text and padding say it
        // measures, not what a constant in another file claims. So the arithmetic is gone: the
        // fixed rows take exactly the height they need, the scene is weighted, and it absorbs
        // whatever remains. Overflow is now impossible rather than merely unlikely, on every
        // screen size, at every font scale.
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(bottom = DockedBarSpace),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                HomeHero(Modifier.fillMaxSize())
                // The top bar rides on the scene rather than above it. A row of its own cost
                // sixty device-independent pixels of picture and bought nothing: the scene is
                // darkest along its top edge, which is exactly where this has to be legible.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceSm),
                ) {
                    val profile = session.profile
                    PremiumTopBar(
                        initial = profile?.username?.firstOrNull()?.uppercase() ?: "K",
                        name = profile?.username ?: stringResource(R.string.profile_title),
                        // Hidden rather than zeroed for a player who has none: a number beside
                        // a crown is a rank, and a guest is not ranked.
                        rating = profile?.rating?.takeIf { !session.isGuest }?.toString(),
                        onProfile = onProfile,
                        onLanguage = { languagePickerOpen = true },
                        onSettings = onSettings,
                        profileLabel = stringResource(R.string.profile_title),
                        languageLabel = stringResource(R.string.settings_language),
                        settingsLabel = stringResource(R.string.game_settings),
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = Dimens.MenuMaxWidth)
                    .padding(horizontal = Dimens.ScreenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The name sits under the picture, not over it: laid on the scene it needed a
                // scrim heavy enough to bury the pieces it was there to introduce.
                HomeBrand()
                Spacer(Modifier.height(Dimens.SpaceMd))
                PrimaryPlayCard(
                    title = stringResource(R.string.menu_play),
                    subtitle = stringResource(R.string.home_play_subtitle),
                    onClick = onPlay,
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
                // Two by two rather than four stacked. The four destinations under the play
                // card are peers — none is more likely than the others — and a column says the
                // opposite by putting one of them first.
                HomeGridRow(
                    left = {
                        HomeMenuCard(
                            title = stringResource(R.string.home_leaderboard_short),
                            subtitle = stringResource(R.string.home_leaderboard_subtitle),
                            icon = PremiumIcon.TROPHY,
                            onClick = onLeaderboard,
                        )
                    },
                    right = {
                        HomeMenuCard(
                            title = stringResource(R.string.friends_title),
                            subtitle = stringResource(R.string.home_friends_subtitle),
                            icon = PremiumIcon.PEOPLE,
                            onClick = onFriends,
                        )
                    },
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
                HomeGridRow(
                    left = {
                        HomeMenuCard(
                            title = stringResource(R.string.menu_tutorial),
                            subtitle = stringResource(R.string.home_tutorial_subtitle),
                            icon = PremiumIcon.MORTARBOARD,
                            onClick = onTutorial,
                        )
                    },
                    right = {
                        HomeMenuCard(
                            title = stringResource(R.string.game_settings),
                            subtitle = stringResource(R.string.home_settings_subtitle),
                            icon = PremiumIcon.SLIDERS,
                            onClick = onSettings,
                        )
                    },
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
                // The reference puts daily objectives in this slot. That feature does not exist
                // yet, and a card promising one that opens nothing is the cheapest way to make
                // an app feel broken — so the slot carries the one real destination without a
                // tile of its own until the objectives arrive.
                WideMenuCard(
                    title = stringResource(R.string.menu_more),
                    subtitle = stringResource(R.string.home_more_subtitle),
                    icon = PremiumIcon.SHIELD_STAR,
                    onClick = onMore,
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
            }
        }
    }
}
