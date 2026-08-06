package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.presentation.tutorial.TutorialUiState
import com.duzman46.gridbound.presentation.tutorial.TutorialViewModel
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.game.GameBoard

@Composable
fun TutorialRoute(
    onFinished: () -> Unit,
    viewModel: TutorialViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onFinished()
    }
    TutorialScreen(
        state = state,
        onTileTap = viewModel::onTileTapped,
        onWallTap = viewModel::onWallTapped,
        onNext = viewModel::nextStep,
        onPrevious = viewModel::previousStep,
        onSkip = viewModel::skip,
    )
}

@Composable
private fun TutorialScreen(
    state: TutorialUiState,
    onTileTap: (com.duzman46.gridbound.game.models.Position) -> Unit,
    onWallTap: (com.duzman46.gridbound.game.models.Wall) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
) {
    var skipRequested by remember { mutableStateOf(false) }

    if (skipRequested) {
        AlertDialog(
            onDismissRequest = { skipRequested = false },
            title = { Text(stringResource(R.string.tutorial_skip_confirm_title)) },
            text = { Text(stringResource(R.string.tutorial_skip_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    skipRequested = false
                    onSkip()
                }) { Text(stringResource(R.string.action_skip)) }
            },
            dismissButton = {
                TextButton(onClick = { skipRequested = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold { padding ->
        ScreenBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            R.string.tutorial_progress,
                            state.stepIndex + 1,
                            state.stepCount,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { skipRequested = true }) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
                LinearProgressIndicator(
                    progress = { (state.stepIndex + 1f) / state.stepCount },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp),
                )

                InstructionCard(state)

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    GameBoard(
                        state = state.board,
                        onTileTap = onTileTap,
                        onWallTap = onWallTap,
                        modifier = Modifier.sizeIn(maxWidth = Constants.Ui.BOARD_MAX_SIZE_DP.dp),
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = !state.isFirstStep,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_previous))
                    }
                    Button(
                        onClick = onNext,
                        enabled = state.stepSolved,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(
                                if (state.isLastStep) R.string.tutorial_finish else R.string.action_next,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionCard(state: TutorialUiState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 620.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(state.step.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(state.step.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Announced by TalkBack as soon as it appears so the correction is not silent.
            state.hint?.let { hint ->
                Text(
                    hint.asString(),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.stepSolved && !state.isLastStep) {
                Text(
                    stringResource(R.string.tutorial_step_correct),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
