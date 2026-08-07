package com.duzman46.gridbound.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.AdBanner
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
 */
@Composable
fun MainMenuScreen(
    session: SessionState,
    language: AppLanguage,
    adsRemoved: Boolean,
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
            if (!adsRemoved) {
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
            Column(
                Modifier
                    .fillMaxSize()
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
                    // Tight to the panel, loose from the row above it: the wordmark belongs to
                    // the picture it introduces, and equal gaps on both sides would leave it
                    // floating between two things it has nothing to do with.
                    HomeWordmark()
                    Spacer(Modifier.height(Dimens.SpaceSm))
                    BoardShowcase()
                    Spacer(Modifier.height(Dimens.SpaceXl))
                    PlaySlab(stringResource(R.string.menu_play), onClick = onPlay)
                    Spacer(Modifier.height(Dimens.SpaceMd))
                    // Learning the game and seeing where you stand are the two things a
                    // player looks for by name. Buried in a sheet they were never found, and
                    // the screen had nothing under the slab but empty ground.
                    HomeChoice(stringResource(R.string.menu_tutorial), GlyphKind.TUTORIAL, onClick = onTutorial)
                    Spacer(Modifier.height(Dimens.SpaceMd))
                    HomeChoice(stringResource(R.string.leaderboard_title), GlyphKind.LEADERBOARD, onClick = onLeaderboard)
                    Spacer(Modifier.height(Dimens.SpaceMd))
                    HomeChoice(stringResource(R.string.game_settings), GlyphKind.SETTINGS, onClick = onSettings)
                    Spacer(Modifier.height(Dimens.SpaceMd))
                    HomeChoice(stringResource(R.string.menu_more), GlyphKind.MORE, onClick = { openSheet = HomeMenu.MORE })
                    Spacer(Modifier.height(Dimens.SpaceXl))
                }
            }
        }
    }
}

/** Which sheet is showing. Saved, so a rotation does not close it. */
private enum class HomeMenu { NONE, MORE }
