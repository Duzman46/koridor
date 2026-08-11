package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.ThemeMode
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.monetization.MonetizationState
import com.duzman46.gridbound.presentation.settings.SettingsUiState
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.LanguagePickerDialog
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.SectionCard
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.home.GroupNote
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
    onAccount: () -> Unit,
    onReplayTutorial: () -> Unit,
    monetization: MonetizationState,
    onPrivacyOptions: () -> Unit,
    // Read from the billing ledger rather than from [monetization], which mirrors it through a
    // collector: this is the screen a player lands on straight after paying, and the offer has
    // to be gone by the time they get here.
    offersAdRemoval: Boolean,
    isGuest: Boolean,
    onRemoveAds: () -> Unit,
    onRestorePurchases: () -> Unit,
) {
    var languageOpen by rememberSaveable { mutableStateOf(false) }
    var themeOpen by rememberSaveable { mutableStateOf(false) }

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
                    OptionEntry(
                        icon = PremiumIcon.MORTARBOARD,
                        title = stringResource(R.string.settings_tutorial_replay),
                        subtitle = stringResource(R.string.settings_tutorial_replay_hint),
                        onClick = onReplayTutorial,
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

            SectionLabel(stringResource(R.string.settings_store))
            OptionGroup(
                buildList {
                    // Hidden once bought: an upgrade you already own is not an offer, and
                    // leaving it there is what makes a player wonder whether their money
                    // arrived.
                    if (offersAdRemoval) {
                        add(
                            OptionEntry(
                                icon = PremiumIcon.NO_ADS,
                                title = stringResource(R.string.store_remove_ads),
                                subtitle = stringResource(R.string.settings_remove_ads_hint),
                                onClick = onRemoveAds,
                            ),
                        )
                    }
                    // Restoring stays here rather than in More, beside the thing it restores:
                    // it is what somebody does on a new handset before they think to open a
                    // menu called More.
                    add(
                        OptionEntry(
                            icon = PremiumIcon.RESTORE,
                            title = stringResource(R.string.store_restore),
                            subtitle = stringResource(R.string.settings_restore_hint),
                            onClick = onRestorePurchases,
                        ),
                    )
                },
            )
            if (offersAdRemoval && isGuest) {
                // A guest's purchase would be stranded on this device, so the account comes
                // first. Said before Play has taken the money, not after.
                GroupNote(stringResource(R.string.store_guest_warning))
            }
        }
    }
}

@Composable
private fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

@Composable
fun StatisticsScreen(statistics: GameStatistics, onBack: () -> Unit) {
    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.menu_statistics), onBack) }) { padding ->
        ScreenBackground {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth().widthIn(max = 760.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.stats_career), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard(stringResource(R.string.stats_games), statistics.totalGames.toString(), Modifier.weight(1f))
                            StatCard(stringResource(R.string.stats_wins), statistics.totalWins.toString(), Modifier.weight(1f))
                            StatCard(
                                stringResource(R.string.stats_rate),
                                stringResource(
                                    R.string.stats_percentage,
                                    (statistics.winRate * 100).roundToInt(),
                                ),
                                Modifier.weight(1f),
                            )
                        }
                        SettingsCard(stringResource(R.string.stats_details)) {
                            StatLine(stringResource(R.string.stats_losses), statistics.totalLosses)
                            StatLine(stringResource(R.string.stats_local_games), statistics.localGames)
                            StatLine(stringResource(R.string.stats_total_turns), statistics.totalTurns)
                        }
                        SettingsCard(stringResource(R.string.stats_by_difficulty)) {
                            Difficulty.entries.forEach { difficulty ->
                                val wins = statistics.winsByDifficulty[difficulty] ?: 0
                                val losses = statistics.lossesByDifficulty[difficulty] ?: 0
                                ListItem(
                                    headlineContent = { Text(difficulty.label(), fontWeight = FontWeight.SemiBold) },
                                    supportingContent = {
                                        Text(stringResource(R.string.stats_wins_losses, wins, losses))
                                    },
                                    leadingContent = { Icon(Icons.Rounded.SmartToy, contentDescription = null) },
                                )
                            }
                        }
                    }
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
