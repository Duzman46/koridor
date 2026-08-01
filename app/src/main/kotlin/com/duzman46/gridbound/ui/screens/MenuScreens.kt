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
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.ui.components.CenteredContent
import com.duzman46.gridbound.ui.components.GradientBackground
import com.duzman46.gridbound.ui.components.MenuButton
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.SelectionCard
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
                Text("Yolunu aç. Rakibinin yolunu değiştir.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MainMenuScreen(
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onStatistics: () -> Unit,
) {
    GradientBackground {
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
                    "Her hamle yeni bir yol.",
                    modifier = Modifier.padding(bottom = 24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MenuButton("Oyuna Başla", Icons.Rounded.SportsEsports, onPlay)
                MenuButton("İstatistikler", Icons.Rounded.BarChart, onStatistics)
                MenuButton("Ayarlar", Icons.Rounded.Settings, onSettings)
            }
        }
    }
}

@Composable
fun ModeSelectionScreen(onBack: () -> Unit, onAi: () -> Unit, onLocal: () -> Unit, onOnline: () -> Unit) {
    Scaffold(topBar = { ScreenTopBar("Oyun Modu", onBack) }) { padding ->
        GradientBackground {
            CenteredContent(Modifier.padding(padding)) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 620.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text("Nasıl oynamak istersin?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    SelectionCard("Yapay Zekâya Karşı", "Üç farklı zorluk seviyesinde stratejini dene.", Icons.Rounded.SmartToy, onAi)
                    SelectionCard("İki Oyuncu", "Aynı cihazda sırayla oynayın.", Icons.Rounded.Groups, onLocal)
                    SelectionCard("Çevrimiçi", "Oda koduyla internet üzerinden arkadaşına karşı oyna.", Icons.Rounded.Wifi, onOnline)
                }
            }
        }
    }
}

@Composable
fun DifficultySelectionScreen(onBack: () -> Unit, onSelected: (Difficulty) -> Unit) {
    Scaffold(topBar = { ScreenTopBar("Zorluk", onBack) }) { padding ->
        GradientBackground {
            CenteredContent(Modifier.padding(padding)) {
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 620.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Rakibini seç", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    SelectionCard("Kolay", "Rahat ve değişken hamleler.", Icons.Rounded.Bolt, { onSelected(Difficulty.EASY) })
                    SelectionCard("Orta", "En kısa yolu okur ve seni yavaşlatır.", Icons.Rounded.SmartToy, { onSelected(Difficulty.MEDIUM) })
                    SelectionCard("Zor", "Minimax ve alpha-beta ile birkaç hamle sonrasını hesaplar.", Icons.Rounded.Psychology, { onSelected(Difficulty.HARD) })
                }
            }
        }
    }
}
