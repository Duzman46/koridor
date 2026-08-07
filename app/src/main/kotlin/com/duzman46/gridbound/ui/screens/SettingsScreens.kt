package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.SectionCard
import com.duzman46.gridbound.ui.components.ScreenTopBar
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onSound: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onMatchMessages: (Boolean) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onAccount: () -> Unit,
    monetization: MonetizationState,
    onPrivacyOptions: () -> Unit,
) {
    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.game_settings), onBack) }) { padding ->
        ScreenBackground {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    SettingsCard(stringResource(R.string.settings_language)) {
                        // Wraps rather than scrolls: eleven options do not fit on one line,
                        // and a hidden language is a language nobody finds.
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // The stored default is still "follow the device"; it is shown as
                            // whichever language that actually produces here, so the ticked
                            // chip always names the language currently on screen.
                            val deviceTag = LocalResources.current.configuration.locales[0].language
                            val effective = AppLanguage.resolve(state.settings.language, deviceTag)
                            AppLanguage.selectable.forEach { language ->
                                FilterChip(
                                    selected = effective == language,
                                    onClick = { onLanguage(language) },
                                    label = {
                                        // Each language is labelled in itself, so it is
                                        // recognisable whatever the current language is.
                                        Text(language.endonym)
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    // Light, dark, follow the system. No accent picker: the app has one
                    // colour scheme on purpose, and letting the wallpaper repaint it was
                    // what made it look like a different app on every phone.
                    SettingsCard(stringResource(R.string.settings_appearance)) {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                val (label, icon) = when (mode) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system) to Icons.Rounded.PhoneAndroid
                                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light) to Icons.Rounded.LightMode
                                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark) to Icons.Rounded.DarkMode
                                }
                                FilterChip(
                                    selected = state.settings.themeMode == mode,
                                    onClick = { onThemeMode(mode) },
                                    label = { Text(label) },
                                    leadingIcon = { Icon(icon, contentDescription = null) },
                                )
                            }
                        }
                    }
                }
                item {
                    SettingsCard(stringResource(R.string.settings_game_experience)) {
                        SettingSwitch(stringResource(R.string.settings_sounds), stringResource(R.string.settings_sounds_description), Icons.AutoMirrored.Rounded.VolumeUp, state.settings.soundEnabled, onSound)
                        SettingSwitch(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_description), Icons.Rounded.TouchApp, state.settings.hapticsEnabled, onHaptics)
                        // Sits beside sound and vibration because it is the same kind of
                        // choice — how much the app is allowed to say to you — and because
                        // this screen is two taps from the board, which is where somebody
                        // reaches for it while a rival is being tiresome.
                        SettingSwitch(stringResource(R.string.settings_chat), stringResource(R.string.settings_chat_description), Icons.AutoMirrored.Rounded.Chat, state.settings.matchMessagesEnabled, onMatchMessages)
                    }
                }
                item {
                    SettingsCard(stringResource(R.string.settings_default_ai)) {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Difficulty.entries.forEach { difficulty ->
                                FilterChip(
                                    selected = state.settings.difficulty == difficulty,
                                    onClick = { onDifficulty(difficulty) },
                                    label = { Text(difficulty.label()) },
                                )
                            }
                        }
                    }
                }
                item {
                    SettingsCard(stringResource(R.string.account_title)) {
                        OutlinedButton(onClick = onAccount, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.ManageAccounts, contentDescription = null)
                            Text(stringResource(R.string.account_title), Modifier.padding(start = 8.dp))
                        }
                        // Only offered where the consent framework says it is required;
                        // elsewhere it would be a button that opens nothing.
                        if (monetization.privacyOptionsRequired) {
                            OutlinedButton(onClick = onPrivacyOptions, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.PrivacyTip, contentDescription = null)
                                Text(stringResource(R.string.settings_ad_privacy), Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

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
private fun SettingSwitch(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked, onCheckedChange) },
    )
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
}
