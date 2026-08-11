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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.duzman46.gridbound.R
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.home.GlyphKind
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import com.duzman46.gridbound.ui.components.home.HomeBrand
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.KoridorMark
import com.duzman46.gridbound.ui.components.LanguagePickerDialog
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.ui.components.home.BottomItem
import com.duzman46.gridbound.ui.components.home.HomeHero
import com.duzman46.gridbound.ui.components.home.HomeGridRow
import com.duzman46.gridbound.ui.components.home.HomeMenuCard
import com.duzman46.gridbound.ui.components.home.KoridorBottomBar
import com.duzman46.gridbound.ui.components.home.PremiumTopBar
import com.duzman46.gridbound.ui.components.home.PrimaryPlayCard
import com.duzman46.gridbound.ui.components.home.WideMenuCard
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.saveable.rememberSaveable
import com.duzman46.gridbound.BuildConfig
import com.duzman46.gridbound.ui.components.home.HomeSheet
import com.duzman46.gridbound.ui.components.home.SheetAction
import com.duzman46.gridbound.ui.components.home.SheetDivider
import com.duzman46.gridbound.ui.components.home.SheetLink
import com.duzman46.gridbound.ui.components.home.SheetVersion
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
                    // Explicit, and that is the whole fix: this Text is not inside a Surface,
                    // so it inherited LocalContentColor's default of black and was drawn in
                    // black on a near-black ground. The wordmark was there the whole time and
                    // could not be read.
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 0.18.em,
                )
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
    offersAdRemoval: Boolean,
    onLanguage: (AppLanguage) -> Unit,
    onPlay: () -> Unit,
    onFriends: () -> Unit,
    onLeaderboard: () -> Unit,
    onProfile: () -> Unit,
    onStatistics: () -> Unit,
    onTutorial: () -> Unit,
    onSettings: () -> Unit,
    onRemoveAds: () -> Unit,
    onRestorePurchases: () -> Unit,
    onOpenUrl: (String) -> Unit,
    showAdBanner: Boolean,
) {
    var openSheet by rememberSaveable { mutableStateOf(HomeMenu.NONE) }
    var languagePickerOpen by remember { mutableStateOf(false) }
    val dismiss = { openSheet = HomeMenu.NONE }

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

    if (openSheet == HomeMenu.MORE) {
        HomeSheet(stringResource(R.string.menu_more), dismiss) {
            // No friends entry here. "More" is where the things nobody looks for by name go,
            // and friends is a mark in the row at the top of the screen — one destination
            // reachable twice from one screen teaches the player that neither route is real.
            //
            // Hidden once bought: an upgrade you already own is not an offer.
            if (offersAdRemoval) {
                SheetAction(stringResource(R.string.store_remove_ads), GlyphKind.REMOVE_ADS, {
                    dismiss(); onRemoveAds()
                })
                // A guest's purchase would be stranded on this device, so the account comes
                // first. Said here rather than after Play has already taken the money.
                if (session.isGuest) {
                    SheetVersion(stringResource(R.string.store_guest_warning))
                }
            }
            SheetLink(stringResource(R.string.store_restore), onClick = { dismiss(); onRestorePurchases() })
            SheetLink(stringResource(R.string.menu_statistics), onClick = { dismiss(); onStatistics() })
            SheetDivider()
            val privacyUrl = BuildConfig.PRIVACY_POLICY_URL
            val termsUrl = BuildConfig.TERMS_URL
            // A legal link with no URL configured is not shown at all rather than opening
            // nothing — Play requires the policy link to work, not merely to exist.
            if (privacyUrl.isNotBlank()) {
                SheetLink(stringResource(R.string.account_privacy_policy), onClick = { onOpenUrl(privacyUrl) })
            }
            if (termsUrl.isNotBlank()) {
                SheetLink(stringResource(R.string.account_terms_of_service), onClick = { onOpenUrl(termsUrl) })
            }
            SheetVersion(stringResource(R.string.more_about_version, BuildConfig.VERSION_NAME))
        }
    }

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
                .padding(bottom = padding.calculateBottomPadding()),
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
                            // Gold: standings are where a player sees they are getting
                            // somewhere. The other three are maintenance.
                            accented = true,
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
                    onClick = { openSheet = HomeMenu.MORE },
                    accented = true,
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
            }
        }
    }
}

/** Which sheet is showing. Saved, so a rotation does not close it. */
private enum class HomeMenu { NONE, MORE }
