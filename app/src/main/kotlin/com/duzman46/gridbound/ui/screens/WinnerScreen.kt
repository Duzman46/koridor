package com.duzman46.gridbound.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.ui.components.ScreenBackground

@Composable
fun WinnerScreen(
    winner: PlayerId,
    mode: GameMode,
    onReplay: () -> Unit,
    onHome: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "winner")
    val glow by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
        label = "glow",
    )
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Constants.Animation.CONFETTI_CYCLE_MILLIS)),
        label = "confetti",
    )
    val humanLost = mode == GameMode.VS_AI && winner == PlayerId.PLAYER_TWO
    ScreenBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Confetti(progress)
            Card(
                Modifier.fillMaxWidth().widthIn(max = 500.dp).padding(20.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(92.dp).scale(glow),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        if (humanLost) stringResource(R.string.winner_ai_won) else stringResource(R.string.winner_victory),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (humanLost) {
                            stringResource(R.string.winner_retry_hint)
                        } else {
                            stringResource(
                                R.string.winner_reached_goal,
                                stringResource(
                                    if (winner == PlayerId.PLAYER_ONE) {
                                        R.string.game_player_blue
                                    } else {
                                        R.string.game_player_orange
                                    },
                                ),
                            )
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onReplay, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Replay, contentDescription = null)
                        Text(stringResource(R.string.winner_play_again), Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Home, contentDescription = null)
                        Text(stringResource(R.string.winner_main_menu), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Confetti(progress: Float) {
    val colors = listOf(
        Color(0xFF32D583),
        Color(0xFFFFC857),
        Color(0xFF4D8DFF),
        Color(0xFFFF6B6B),
        Color(0xFFB56CFF),
    )
    Canvas(Modifier.fillMaxSize()) {
        repeat(Constants.Animation.CONFETTI_PARTICLE_COUNT) { index ->
            val xFraction = ((index * 37) % 101) / 100f
            val phase = ((index * 19) % Constants.Animation.CONFETTI_PARTICLE_COUNT).toFloat() /
                Constants.Animation.CONFETTI_PARTICLE_COUNT
            val yFraction = (progress + phase) % 1f
            val drift = kotlin.math.sin((progress + phase) * Math.PI * 2).toFloat() * 18f
            drawCircle(
                color = colors[index % colors.size].copy(alpha = 0.78f),
                radius = 3f + (index % 4),
                center = androidx.compose.ui.geometry.Offset(xFraction * size.width + drift, yFraction * size.height),
            )
        }
    }
}
