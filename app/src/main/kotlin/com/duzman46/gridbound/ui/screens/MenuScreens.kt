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
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.AdBanner
import com.duzman46.gridbound.ui.components.BlockHeight
import com.duzman46.gridbound.ui.components.KoridorMark
import com.duzman46.gridbound.ui.components.LanguagePickerDialog
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.ui.components.home.BoardShowcase
import com.duzman46.gridbound.ui.components.home.GlyphKind
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.saveable.rememberSaveable
import com.duzman46.gridbound.BuildConfig
import com.duzman46.gridbound.ui.components.home.HomeChoice
import com.duzman46.gridbound.ui.components.home.HomeSheet
import com.duzman46.gridbound.ui.components.home.HomeTopBar
import com.duzman46.gridbound.ui.components.home.HomeWordmark
import com.duzman46.gridbound.ui.components.home.SheetAction
import com.duzman46.gridbound.ui.components.home.SheetDivider
import com.duzman46.gridbound.ui.components.home.SheetLink
import com.duzman46.gridbound.ui.components.home.SheetVersion
import com.duzman46.gridbound.ui.components.home.PlaySlab
import com.duzman46.gridbound.ui.components.ScreenBackground
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

/** The secondary choices under the loud one. */
private const val HOME_CHOICES = 4

/**
 * What the round marks in the utility row measure once Material's 48 dp touch floor applies to
 * them, which is more than the 44 dp they are drawn at.
 */
private val HomeUtilityRowHeight = 48.dp

/** One line of the wordmark: the 30 dp size [HomeWordmark] fixes it to, in its 1.2 line box. */
private val HomeWordmarkHeight = 36.dp

/**
 * Every fixed row of the home screen added up, at default font scale.
 *
 * The board panel is the only element here that can be any size, so it gets what is left of
 * the window after this — which is the one arrangement in which the whole screen fits a phone
 * without scrolling. On a 360x740 dp content area that leaves 184 dp of panel behind a 50 dp
 * ad banner and 144 dp behind the 90 dp one a tall device asks for, against a floor of 133 dp;
 * `HomeLayoutBudgetTest` holds those numbers to it.
 *
 * Written as the rows it is made of rather than as one total, so a spacing or control token
 * that moves takes the budget with it, and declared under the pieces it is built from because
 * a file's properties are initialised in the order they are written. A row added to the screen
 * has to be added here as well, or the screen starts scrolling again.
 */
internal val HomeChrome: Dp =
    Dimens.SpaceMd + HomeUtilityRowHeight +
        Dimens.SpaceLg + HomeWordmarkHeight +
        Dimens.SpaceSm + // the wordmark's gap to the panel
        Dimens.SpaceLg + BlockHeight.Loud + Dimens.PressTravel +
        (Dimens.SpaceMd + BlockHeight.Wide + Dimens.PressTravel) * HOME_CHOICES +
        Dimens.SpaceLg

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
        bottomBar = { if (showAdBanner) AdBanner(Modifier.navigationBarsPadding()) },
    ) { padding ->
        ScreenBackground {
            // Measured outside the scroll, because inside one the height is unbounded and
            // there is nothing left to divide up. What arrives here is the window minus the
            // status bar and minus whatever the ad banner took, which is exactly the space
            // the screen has to fit into.
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                val hero = maxHeight - HomeChrome
                Column(
                    Modifier
                        .fillMaxSize()
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
                        Spacer(Modifier.height(Dimens.SpaceMd))
                        HomeTopBar(
                            session = session,
                            language = language,
                            onFriends = onFriends,
                            onSettings = onSettings,
                            onLanguage = { languagePickerOpen = true },
                            onProfile = onProfile,
                        )
                        Spacer(Modifier.height(Dimens.SpaceLg))
                        // Tight to the panel, loose from the row above it: the wordmark
                        // belongs to the picture it introduces, and equal gaps on both sides
                        // would leave it floating between two things it has nothing to do
                        // with.
                        HomeWordmark()
                        Spacer(Modifier.height(Dimens.SpaceSm))
                        BoardShowcase(hero)
                        Spacer(Modifier.height(Dimens.SpaceLg))
                        PlaySlab(stringResource(R.string.menu_play), onClick = onPlay)
                        Spacer(Modifier.height(Dimens.SpaceMd))
                        // Learning the game and seeing where you stand are the two things a
                        // player looks for by name. Buried in a sheet they were never found,
                        // and the screen had nothing under the slab but empty ground.
                        HomeChoice(
                            label = stringResource(R.string.menu_tutorial),
                            glyph = GlyphKind.TUTORIAL,
                            onClick = onTutorial,
                        )
                        Spacer(Modifier.height(Dimens.SpaceMd))
                        HomeChoice(
                            label = stringResource(R.string.leaderboard_title),
                            glyph = GlyphKind.LEADERBOARD,
                            onClick = onLeaderboard,
                        )
                        Spacer(Modifier.height(Dimens.SpaceMd))
                        HomeChoice(
                            label = stringResource(R.string.game_settings),
                            glyph = GlyphKind.SETTINGS,
                            onClick = onSettings,
                        )
                        Spacer(Modifier.height(Dimens.SpaceMd))
                        HomeChoice(
                            label = stringResource(R.string.menu_more),
                            glyph = GlyphKind.MORE,
                            onClick = { openSheet = HomeMenu.MORE },
                        )
                        Spacer(Modifier.height(Dimens.SpaceLg))
                    }
                }
            }
        }
    }
}

/** Which sheet is showing. Saved, so a rotation does not close it. */
private enum class HomeMenu { NONE, MORE }
