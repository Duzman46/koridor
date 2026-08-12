package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.ThemeMode
import com.duzman46.gridbound.game.models.Difficulty
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.duzman46.gridbound.monetization.MonetizationState
import com.duzman46.gridbound.notifications.Notifications
import com.duzman46.gridbound.presentation.settings.SettingsUiState
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.LanguagePickerDialog
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.SectionCard
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.home.GroupNote
import com.duzman46.gridbound.ui.components.home.DifficultyChip
import com.duzman46.gridbound.ui.components.home.DifficultyTones
import com.duzman46.gridbound.ui.components.home.StatsCard
import com.duzman46.gridbound.ui.components.home.StatsDivider
import com.duzman46.gridbound.ui.components.home.StatsHeadlineCard
import com.duzman46.gridbound.ui.components.home.StatsLine
import com.duzman46.gridbound.ui.components.home.StatsNote
import com.duzman46.gridbound.ui.components.home.StatsTitle
import com.duzman46.gridbound.ui.components.home.OptionEntry
import com.duzman46.gridbound.ui.components.home.OptionGroup
import com.duzman46.gridbound.ui.components.home.PremiumHeader
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.ui.components.home.SectionLabel
import com.duzman46.gridbound.ui.components.home.SettingsTile
import com.duzman46.gridbound.ui.components.home.ThemePickerDialog
import kotlin.math.roundToInt

/**
 * The settings screen.
 *
 * Four sections, and what decides them is the *kind* of answer each setting has rather than what
 * it is about: two tiles for the settings whose answer is a named value, a card of switches for
 * the ones whose answer is yes or no, and cards of rows for the ones that open something.
 *
 * **What the reference had that this does not.** Its top row is four tiles and two are
 * duplicates — a "Ses / Açık" tile above a "Sesler" switch, and an "Arayüz / Sistem" tile beside
 * "Tema / Koyu". Two of its switches ("son hamleleri göster", "istatistikleri göster") control
 * settings this game does not have, and inventing a switch that changes nothing is worse than
 * leaving it out. Its support block — help, contact, social, policy — is the More screen's
 * content, and one destination reachable twice from one screen teaches the player that neither
 * route is real.
 *
 * **And what it has that the reference does not.** Every mark is gold. The reference gives each
 * icon its own hue — purple, blue, orange, green, pink, teal — and seven saturated chips in a
 * grid is a launcher, not a game. Here the colour says state, not identity: a switch that is on
 * is gold, a switch that is off is grey, and the eye can read the whole column at a glance.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onSound: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onMatchMessages: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
    onAccount: () -> Unit,
    monetization: MonetizationState,
    onPrivacyOptions: () -> Unit,
    // Read from the billing ledger rather than from [monetization], which mirrors it through a
    // collector: this is the screen a player lands on straight after paying, and the offer has
    // to be gone by the time they get here.
    offersAdRemoval: Boolean,
    isGuest: Boolean,
    onRemoveAds: () -> Unit,
) {
    var languageOpen by rememberSaveable { mutableStateOf(false) }
    var themeOpen by rememberSaveable { mutableStateOf(false) }
    val notificationsAllowed = rememberNotificationAccess()

    // Asked once, the first time this screen is opened while the switch is on and the system
    // has not been asked. Not on first launch: nobody has been offered anything then, that is
    // the dialog everybody denies, and Android allows exactly one more ask afterwards.
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.settings.notificationsEnabled, notificationsAllowed) {
        if (!asked && state.settings.notificationsEnabled && !notificationsAllowed) {
            asked = true
            onRequestNotifications()
        }
    }

    // The stored default is still "follow the device"; it is named here as whichever language
    // that actually produces, so the tile always says the language currently on screen.
    val deviceTag = LocalResources.current.configuration.locales[0].language
    val effective = AppLanguage.resolve(state.settings.language, deviceTag)

    if (languageOpen) {
        LanguagePickerDialog(
            selected = state.settings.language,
            onSelect = {
                languageOpen = false
                onLanguage(it)
            },
            onDismiss = { languageOpen = false },
        )
    }
    val themes = ThemeMode.entries
    if (themeOpen) {
        ThemePickerDialog(
            title = stringResource(R.string.settings_theme),
            options = themes.map { it.label() },
            selected = themes.indexOf(state.settings.themeMode),
            onSelect = {
                themeOpen = false
                onThemeMode(themes[it])
            },
            onDismiss = { themeOpen = false },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        PremiumHeader(
            title = stringResource(R.string.game_settings),
            subtitle = stringResource(R.string.settings_subtitle),
            onBack = onBack,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            SectionLabel(stringResource(R.string.settings_appearance))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                SettingsTile(
                    icon = PremiumIcon.LANGUAGE,
                    label = stringResource(R.string.settings_language),
                    value = effective.endonym,
                    onClick = { languageOpen = true },
                )
                SettingsTile(
                    icon = PremiumIcon.CONTRAST,
                    label = stringResource(R.string.settings_theme),
                    value = state.settings.themeMode.label(),
                    onClick = { themeOpen = true },
                )
            }

            SectionLabel(stringResource(R.string.settings_game_experience))
            OptionGroup(
                listOf(
                    OptionEntry(
                        icon = PremiumIcon.SPEAKER,
                        title = stringResource(R.string.settings_sounds),
                        subtitle = stringResource(R.string.settings_sounds_description),
                        checked = state.settings.soundEnabled,
                        onCheckedChange = onSound,
                    ),
                    OptionEntry(
                        icon = PremiumIcon.VIBRATE,
                        title = stringResource(R.string.settings_haptics),
                        subtitle = stringResource(R.string.settings_haptics_description),
                        checked = state.settings.hapticsEnabled,
                        onCheckedChange = onHaptics,
                    ),
                    // Beside sound and vibration because it is the same kind of choice — how
                    // much the app is allowed to say to you — and because this screen is two
                    // taps from the board, which is where somebody reaches for it while a rival
                    // is being tiresome.
                    OptionEntry(
                        icon = PremiumIcon.CHAT,
                        title = stringResource(R.string.settings_chat),
                        subtitle = stringResource(R.string.settings_chat_description),
                        checked = state.settings.matchMessagesEnabled,
                        onCheckedChange = onMatchMessages,
                    ),
                    // On from the start, and the switch shows the player's own answer rather
                    // than the system's. Showing the system's would mean a brand-new install
                    // reads "off" until somebody grants a permission they have not been asked
                    // for yet, which is the opposite of arriving switched on.
                    //
                    // The permission is asked for the first time this screen is opened with the
                    // switch on — see the effect below. That is the one moment the dialog makes
                    // sense: the row saying "Bildirimler: açık" is on screen behind it.
                    OptionEntry(
                        icon = PremiumIcon.BELL,
                        title = stringResource(R.string.settings_notifications),
                        subtitle = stringResource(R.string.settings_notifications_description),
                        checked = state.settings.notificationsEnabled,
                        onCheckedChange = { wanted ->
                            onNotifications(wanted)
                            if (wanted && !notificationsAllowed) onRequestNotifications()
                        },
                    ),
                ),
            )

            SectionLabel(stringResource(R.string.account_title))
            OptionGroup(
                buildList {
                    add(
                        OptionEntry(
                            icon = PremiumIcon.PERSON,
                            title = stringResource(R.string.account_title),
                            subtitle = stringResource(R.string.settings_account_hint),
                            onClick = onAccount,
                        ),
                    )
                    // Only where the consent framework says it is required; elsewhere it is a
                    // row that opens nothing.
                    if (monetization.privacyOptionsRequired) {
                        add(
                            OptionEntry(
                                icon = PremiumIcon.SHIELD_STAR,
                                title = stringResource(R.string.settings_ad_privacy),
                                subtitle = stringResource(R.string.settings_ad_privacy_hint),
                                onClick = onPrivacyOptions,
                            ),
                        )
                    }
                },
            )

            // No "restore purchases" row, and it is not a feature that went missing.
            //
            // Play is asked what this account owns every time billing connects — see
            // BillingManager.onBillingSetupFinished, which calls refresh() the moment the
            // client is ready, and queryPurchases() grants the entitlement from the answer. A
            // reinstall on the same account therefore restores itself before the player reaches
            // a menu. The button re-ran that same query and its only visible effect was a
            // "purchases restored" notice for something that had already happened, which is why
            // the owner read it as doing nothing: it was.
            //
            // What is left in this section is the offer, and only while there is one to make.
            if (offersAdRemoval) {
                SectionLabel(stringResource(R.string.settings_store))
                OptionGroup(
                    listOf(
                        OptionEntry(
                            icon = PremiumIcon.NO_ADS,
                            title = stringResource(R.string.store_remove_ads),
                            subtitle = stringResource(R.string.settings_remove_ads_hint),
                            onClick = onRemoveAds,
                        ),
                    ),
                )
            }
            if (offersAdRemoval && isGuest) {
                // A guest's purchase would be stranded on this device, so the account comes
                // first. Said before Play has taken the money, not after.
                GroupNote(stringResource(R.string.store_guest_warning))
            }
        }
    }
}

/**
 * Whether the system currently lets this app post anything, re-read every time the screen comes
 * back to the front.
 *
 * Re-read rather than remembered, because the answer is not the app's to keep: the player can
 * revoke it in system settings while this screen is sitting in the background, and they can grant
 * it in a dialog that puts this screen in the background to do so. Both of those end with the
 * activity resuming, which is exactly when this looks again.
 */
@Composable
private fun rememberNotificationAccess(): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Notifications.permitted(context)) }
    LifecycleResumeEffect(context) {
        allowed = Notifications.permitted(context)
        onPauseOrDispose { }
    }
    return allowed
}

@Composable
private fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

/**
 * The career: the online record first, and everything that is not a ranked match after it.
 *
 * The three figures at the top used to be every match this handset had ever seen — practice
 * against the bot and two people passing one phone back and forth, added into the same win rate
 * as ranked play. That is a number which answers no question anybody has, and it flattered
 * itself: a player could beat the Easy bot ten times and read a 90% career.
 *
 * So the top is the **online** record, and it is taken from the account when there is one.
 * `UserProfile.totalGames`, `.wins` and `.losses` are server-owned, and only a reported online
 * match ever moves them — a bot game and a local game are never reported at all — so those
 * fields already *are* the online record, and they survive a reinstall, which the handset's
 * counters do not. A guest has no such row, so the device's own online tally stands in; a guest
 * cannot play ranked, so it stands in as zero, which is the true answer rather than a hidden one.
 *
 * Everything else is below, in the section it belongs to: the bot in the per-difficulty ladder,
 * the pass-and-play games as their own count. Nothing is hidden — it is separated, because
 * adding it together was the thing that made it meaningless.
 *
 * There is no draw anywhere on this screen. The game has no way to produce one.
 */
@Composable
fun StatisticsScreen(
    statistics: GameStatistics,
    profile: UserProfile?,
    onBack: () -> Unit,
) {
    // The account's figures when there is an account, the handset's online tally when there is
    // not. Resolved once here rather than at four call sites, so the two sources cannot get
    // mixed halfway down the screen.
    val games = profile?.totalGames ?: statistics.onlineGames
    val wins = profile?.wins ?: statistics.onlineWins
    val losses = profile?.losses ?: statistics.onlineLosses
    val rate = if (games == 0) 0f else wins.toFloat() / games
    val percent = stringResource(R.string.stats_percentage, (rate * 100).roundToInt())

    ScreenBackground {
        // The header is outside the scroll and the inset is on the container, which is what
        // every other premium screen does. With the header inside, scrolling ran the cards up
        // under the status bar and "Detaylar" ended up printed across the clock.
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            PremiumHeader(
                title = stringResource(R.string.menu_statistics),
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceLg)
                        .navigationBarsPadding()
                        .padding(bottom = Dimens.SpaceXl),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                ) {
                    StatsTitle(
                        title = stringResource(R.string.stats_career),
                        note = stringResource(R.string.stats_career_note),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                    ) {
                        StatsHeadlineCard(
                            icon = PremiumIcon.GLOBE,
                            value = games.toString(),
                            label = stringResource(R.string.stats_games),
                        )
                        StatsHeadlineCard(
                            icon = PremiumIcon.TROPHY,
                            value = wins.toString(),
                            label = stringResource(R.string.stats_wins),
                        )
                        StatsHeadlineCard(
                            icon = PremiumIcon.SHIELD_STAR,
                            value = losses.toString(),
                            label = stringResource(R.string.stats_losses),
                        )
                        StatsHeadlineCard(
                            icon = PremiumIcon.TARGET,
                            value = percent,
                            label = stringResource(R.string.stats_rate),
                        )
                    }

                    // No ring and no percentage down here. The rate is one number and it is
                    // in the row above; drawing it twice on one screen made the second one look
                    // like a different measurement.
                    StatsCard(stringResource(R.string.stats_details)) {
                        StatsLine(
                            icon = PremiumIcon.ROBOT,
                            label = stringResource(R.string.stats_bot_games),
                            value = statistics.botGames.toString(),
                        )
                        StatsDivider()
                        StatsLine(
                            icon = PremiumIcon.PEOPLE,
                            label = stringResource(R.string.stats_local_games),
                            value = statistics.localGames.toString(),
                        )
                        StatsDivider()
                        StatsLine(
                            icon = PremiumIcon.CLOCK,
                            label = stringResource(R.string.stats_total_turns),
                            value = statistics.totalTurns.toString(),
                        )
                    }

                    // The account's ranked standing. A guest gets no card rather than a card of
                    // zeroes, which would imply their play is recorded somewhere it is not.
                    profile?.let { account ->
                        StatsCard(stringResource(R.string.stats_ranked)) {
                            StatsLine(
                                icon = PremiumIcon.STAR,
                                label = stringResource(R.string.profile_rating),
                                value = account.rating.toString(),
                            )
                            StatsDivider()
                            StatsLine(
                                icon = PremiumIcon.BOLT,
                                label = stringResource(R.string.profile_highest_rating),
                                value = account.highestRating.toString(),
                            )
                            StatsDivider()
                            StatsLine(
                                icon = PremiumIcon.FLAME,
                                label = stringResource(R.string.profile_win_streak),
                                value = account.currentWinStreak.toString(),
                            )
                            StatsDivider()
                            StatsLine(
                                icon = PremiumIcon.TARGET,
                                label = stringResource(R.string.profile_best_streak),
                                value = account.bestWinStreak.toString(),
                            )
                        }
                    }

                    StatsCard(stringResource(R.string.stats_by_difficulty)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                        ) {
                            Difficulty.entries.forEachIndexed { index, difficulty ->
                                DifficultyChip(
                                    tone = DifficultyTones[index % DifficultyTones.size],
                                    label = difficulty.label(),
                                    record = stringResource(
                                        R.string.stats_record_short,
                                        statistics.winsByDifficulty[difficulty] ?: 0,
                                        statistics.lossesByDifficulty[difficulty] ?: 0,
                                    ),
                                    recordDescription = stringResource(
                                        R.string.stats_wins_losses,
                                        statistics.winsByDifficulty[difficulty] ?: 0,
                                        statistics.lossesByDifficulty[difficulty] ?: 0,
                                    ),
                                )
                            }
                        }
                    }

                    StatsNote(stringResource(R.string.stats_online_only))
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(title, Modifier.widthIn(max = Constants.Ui.FORM_MAX_WIDTH_DP.dp), content)
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatLine(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Difficulty.label(): String = when (this) {
    Difficulty.EASY -> stringResource(R.string.difficulty_easy)
    Difficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
    Difficulty.HARD -> stringResource(R.string.difficulty_hard)
    Difficulty.EXPERT -> stringResource(R.string.difficulty_expert)
}
