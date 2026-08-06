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
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.game.models.Difficulty
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
import com.duzman46.gridbound.ui.components.home.SheetAction
import com.duzman46.gridbound.ui.components.home.SheetChoice
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
                )
            }
        }
    }
}

/**
 * The home screen.
 *
 * Four choices and nothing else. Everything the app can do used to be a button here, which
 * made the first screen a directory rather than a way in; each of these opens a sheet holding
 * the handful of things that belong under it. Settings, language and the player's own profile
 * are small marks on the board panel, not entries in the list.
 */
@Composable
fun MainMenuScreen(
    session: SessionState,
    language: AppLanguage,
    adsRemoved: Boolean,
    onLanguage: (AppLanguage) -> Unit,
    onPlayBot: (Difficulty) -> Unit,
    onPlayLocal: () -> Unit,
    onQuickMatch: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
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
    defaultDifficulty: Difficulty,
) {
    var openSheet by rememberSaveable { mutableStateOf(HomeMenu.NONE) }
    var languagePickerOpen by remember { mutableStateOf(false) }
    var customDifficulty by rememberSaveable { mutableStateOf(defaultDifficulty) }
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

    when (openSheet) {
        HomeMenu.NONE -> Unit

        HomeMenu.PLAY -> HomeSheet(stringResource(R.string.menu_play), dismiss) {
            SheetAction(stringResource(R.string.play_vs_bot), GlyphKind.VS_BOT, {
                dismiss(); onPlayBot(defaultDifficulty)
            }, emphasised = true)
            SheetAction(stringResource(R.string.play_local), GlyphKind.FRIENDS, {
                dismiss(); onPlayLocal()
            })
            SheetDivider()
            // The custom game is the same bot match with the settings that used to be a
            // whole screen of their own, folded in where they are actually chosen.
            SheetChoice(
                label = stringResource(R.string.difficulty_title),
                options = Difficulty.entries,
                selected = customDifficulty,
                optionLabel = { it.label() },
                onSelect = { customDifficulty = it },
            )
            SheetAction(stringResource(R.string.play_custom_start), GlyphKind.SETTINGS, {
                dismiss(); onPlayBot(customDifficulty)
            })
        }

        HomeMenu.ONLINE -> HomeSheet(stringResource(R.string.menu_online), dismiss) {
            SheetAction(stringResource(R.string.online_quick_match), GlyphKind.QUICK_PLAY, {
                dismiss(); onQuickMatch()
            }, emphasised = true)
            SheetAction(stringResource(R.string.online_create_room), GlyphKind.CREATE_ROOM, {
                dismiss(); onCreateRoom()
            })
            SheetAction(stringResource(R.string.online_join_by_code), GlyphKind.JOIN_ROOM, {
                dismiss(); onJoinRoom()
            })
            SheetAction(stringResource(R.string.online_play_with_friend), GlyphKind.FRIENDS, {
                dismiss(); onFriends()
            })
            SheetDivider()
            SheetLink(stringResource(R.string.leaderboard_title), onClick = { dismiss(); onLeaderboard() })
        }

        HomeMenu.MORE -> HomeSheet(stringResource(R.string.menu_more), dismiss) {
            SheetAction(stringResource(R.string.menu_tutorial), GlyphKind.TUTORIAL, {
                dismiss(); onTutorial()
            })
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
            // Not a link: the version is here to be read, not tapped.
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
                    Spacer(Modifier.height(Dimens.SpaceLg))
                    BoardShowcase(
                        session = session,
                        language = language,
                        onProfile = onProfile,
                        onLanguage = { languagePickerOpen = true },
                        onSettings = onSettings,
                    )
                    Spacer(Modifier.height(Dimens.SpaceXl))
                    PlaySlab(stringResource(R.string.menu_play), onClick = { openSheet = HomeMenu.PLAY })
                    Spacer(Modifier.height(Dimens.SpaceMd))
                    HomeChoice(stringResource(R.string.menu_online), GlyphKind.ONLINE, onClick = { openSheet = HomeMenu.ONLINE })
                    Spacer(Modifier.height(Dimens.SpaceMd))
                    HomeChoice(stringResource(R.string.profile_title), GlyphKind.PROFILE, onClick = onProfile)
                    Spacer(Modifier.height(Dimens.SpaceMd))
                    HomeChoice(stringResource(R.string.menu_more), GlyphKind.MORE, onClick = { openSheet = HomeMenu.MORE })
                    Spacer(Modifier.height(Dimens.SpaceXl))
                }
            }
        }
    }
}

/** Which sheet is showing. Saved, so a rotation does not close it. */
private enum class HomeMenu { NONE, PLAY, ONLINE, MORE }

@Composable
private fun Difficulty.label(): String = when (this) {
    Difficulty.EASY -> stringResource(R.string.difficulty_easy)
    Difficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
    Difficulty.HARD -> stringResource(R.string.difficulty_hard)
}
