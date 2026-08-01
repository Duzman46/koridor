package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.domain.models.ThemeMode
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.presentation.settings.SettingsUiState
import com.duzman46.gridbound.ui.components.GradientBackground
import com.duzman46.gridbound.ui.components.ScreenTopBar
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
) {
    Scaffold(topBar = { ScreenTopBar("Ayarlar", onBack) }) { padding ->
        GradientBackground {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    SettingsCard("Görünüm") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                val (label, icon) = when (mode) {
                                    ThemeMode.SYSTEM -> "Sistem" to Icons.Rounded.PhoneAndroid
                                    ThemeMode.LIGHT -> "Açık" to Icons.Rounded.LightMode
                                    ThemeMode.DARK -> "Koyu" to Icons.Rounded.DarkMode
                                }
                                FilterChip(
                                    selected = state.settings.themeMode == mode,
                                    onClick = { onThemeMode(mode) },
                                    label = { Text(label) },
                                    leadingIcon = { Icon(icon, contentDescription = null) },
                                )
                            }
                        }
                        SettingSwitch("Dinamik renk", "Cihazının renk paletini kullanır.", Icons.Rounded.ColorLens, state.settings.dynamicColor, onDynamicColor)
                    }
                }
                item {
                    SettingsCard("Oyun deneyimi") {
                        SettingSwitch("Sesler", "Hamle, duvar ve sonuç sesleri.", Icons.Rounded.VolumeUp, state.settings.soundEnabled, onSound)
                        SettingSwitch("Dokunsal geri bildirim", "Hamlelerde ve hatalarda titreşim.", Icons.Rounded.TouchApp, state.settings.hapticsEnabled, onHaptics)
                    }
                }
                item {
                    SettingsCard("Varsayılan AI") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Difficulty.entries.forEach { difficulty ->
                                FilterChip(
                                    selected = state.settings.difficulty == difficulty,
                                    onClick = { onDifficulty(difficulty) },
                                    label = { Text(difficulty.label()) },
                                    leadingIcon = { Icon(Icons.Rounded.SmartToy, contentDescription = null) },
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
fun StatisticsScreen(statistics: GameStatistics, onBack: () -> Unit) {
    Scaffold(topBar = { ScreenTopBar("İstatistikler", onBack) }) { padding ->
        GradientBackground {
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
                        Text("Kariyer özeti", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("Oyun", statistics.totalGames.toString(), Modifier.weight(1f))
                            StatCard("Galibiyet", statistics.totalWins.toString(), Modifier.weight(1f))
                            StatCard("Oran", "%${(statistics.winRate * 100).roundToInt()}", Modifier.weight(1f))
                        }
                        SettingsCard("Detaylar") {
                            StatLine("Mağlubiyet", statistics.totalLosses)
                            StatLine("Yerel oyun", statistics.localGames)
                            StatLine("Toplam tur", statistics.totalTurns)
                        }
                        SettingsCard("Zorluklara göre") {
                            Difficulty.entries.forEach { difficulty ->
                                val wins = statistics.winsByDifficulty[difficulty] ?: 0
                                val losses = statistics.lossesByDifficulty[difficulty] ?: 0
                                ListItem(
                                    headlineContent = { Text(difficulty.label(), fontWeight = FontWeight.SemiBold) },
                                    supportingContent = { Text("$wins galibiyet · $losses mağlubiyet") },
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
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
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

private fun Difficulty.label(): String = when (this) {
    Difficulty.EASY -> "Kolay"
    Difficulty.MEDIUM -> "Orta"
    Difficulty.HARD -> "Zor"
}
