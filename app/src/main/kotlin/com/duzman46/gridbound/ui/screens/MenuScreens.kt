package com.duzman46.gridbound.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.ui.components.CenteredContent
import com.duzman46.gridbound.ui.components.AdBanner
import com.duzman46.gridbound.ui.components.GradientBackground
import com.duzman46.gridbound.ui.components.MenuButton
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.SelectionCard
import com.duzman46.gridbound.ui.localization.localized
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(Constants.Animation.SPLASH_DURATION_MILLIS)
        onFinished()
    }
    val transition = rememberInfiniteTransition(label = "splash")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "logoScale",
    )
    GradientBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(
                    Modifier
                        .size(116.dp)
                        .scale(scale)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(34.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text("KORİDOR", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                Text(localized("Yolunu aç. Rakibinin yolunu değiştir.", "Open your path. Change your rival's path."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MainMenuScreen(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onStatistics: () -> Unit,
    showAdBanner: Boolean,
) {
    var languageMenuOpen by remember { mutableStateOf(false) }
    GradientBackground {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                OutlinedButton(onClick = { languageMenuOpen = true }) {
                    Icon(Icons.Rounded.Language, contentDescription = null)
                    Text(if (language == AppLanguage.TURKISH) "TR" else "EN", Modifier.padding(start = 8.dp))
                }
                DropdownMenu(expanded = languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Türkçe") },
                        onClick = {
                            languageMenuOpen = false
                            onLanguage(AppLanguage.TURKISH)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("English") },
                        onClick = {
                            languageMenuOpen = false
                            onLanguage(AppLanguage.ENGLISH)
                        },
                    )
                }
            }
            CenteredContent {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        Icons.Rounded.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("Koridor", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                    Text(
                        localized("Her hamle yeni bir yol.", "Every move opens a new path."),
                        modifier = Modifier.padding(bottom = 24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MenuButton(localized("Oyuna Başla", "Play"), Icons.Rounded.SportsEsports, onPlay)
                    MenuButton(localized("İstatistikler", "Statistics"), Icons.Rounded.BarChart, onStatistics)
                    MenuButton(localized("Ayarlar", "Settings"), Icons.Rounded.Settings, onSettings)
                }
            }
            if (showAdBanner) {
                AdBanner(Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
fun ModeSelectionScreen(onBack: () -> Unit, onAi: () -> Unit, onLocal: () -> Unit, onOnline: () -> Unit) {
    Scaffold(topBar = { ScreenTopBar(localized("Oyun Modu", "Game Mode"), onBack) }) { padding ->
        GradientBackground {
            CenteredContent(Modifier.padding(padding)) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 620.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text(localized("Nasıl oynamak istersin?", "How would you like to play?"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    SelectionCard(localized("Yapay Zekâya Karşı", "Against AI"), localized("Üç farklı zorluk seviyesinde stratejini dene.", "Test your strategy at three difficulty levels."), Icons.Rounded.SmartToy, onAi)
                    SelectionCard(localized("İki Oyuncu", "Two Players"), localized("Aynı cihazda sırayla oynayın.", "Take turns on the same device."), Icons.Rounded.Groups, onLocal)
                    SelectionCard(localized("Çevrimiçi", "Online"), localized("Oda koduyla internet üzerinden arkadaşına karşı oyna.", "Play a friend online with a room code."), Icons.Rounded.Wifi, onOnline)
                }
            }
        }
    }
}

@Composable
fun DifficultySelectionScreen(onBack: () -> Unit, onSelected: (Difficulty) -> Unit) {
    Scaffold(topBar = { ScreenTopBar(localized("Zorluk", "Difficulty"), onBack) }) { padding ->
        GradientBackground {
            CenteredContent(Modifier.padding(padding)) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 620.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(localized("Rakibini seç", "Choose your opponent"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    SelectionCard(localized("Kolay", "Easy"), localized("Rahat ve değişken hamleler.", "Relaxed and varied moves."), Icons.Rounded.Bolt, { onSelected(Difficulty.EASY) })
                    SelectionCard(localized("Orta", "Medium"), localized("En kısa yolu okur ve seni yavaşlatır.", "Reads the shortest path and slows you down."), Icons.Rounded.SmartToy, { onSelected(Difficulty.MEDIUM) })
                    SelectionCard(localized("Zor", "Hard"), localized("Minimax ve alpha-beta ile birkaç hamle sonrasını hesaplar.", "Calculates several moves ahead with minimax and alpha-beta."), Icons.Rounded.Psychology, { onSelected(Difficulty.HARD) })
                }
            }
        }
    }
}
