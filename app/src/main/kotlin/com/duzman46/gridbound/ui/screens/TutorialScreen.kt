package com.duzman46.gridbound.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
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

/**
 * The tutorial: a real board with a card of instructions floating over it.
 *
 * The instructions used to be a block stacked *above* the board, which cost twice: it pushed
 * the board down into whatever height was left, and it read as a caption rather than as
 * something talking to you about the thing under it. Now the board gets the screen and the
 * card sits on top of it near the thumb, the way a coach-mark does — including the Back and
 * Next buttons, so everything the step is asking of you is inside one object.
 */
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

    ScreenBackground {
        Box(Modifier.fillMaxSize()) {
            // The board is the whole screen and is not squeezed by the card: the card floats,
            // so a long translation makes the card taller without shrinking the squares
            // anyone is being asked to tap.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 64.dp, bottom = 210.dp),
                contentAlignment = Alignment.Center,
            ) {
                GameBoard(
                    state = state.board,
                    onTileTap = onTileTap,
                    onWallTap = onWallTap,
                    modifier = Modifier.widthIn(max = Constants.Ui.BOARD_MAX_SIZE_DP.dp),
                )
            }

            StepProgress(
                state = state,
                onSkip = { skipRequested = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
            )

            InstructionCard(
                state = state,
                onNext = onNext,
                onPrevious = onPrevious,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }
}

/**
 * Where you are in the lesson, as dots rather than a bar.
 *
 * Seven steps is few enough to count, and a dot per step says how many are left at a glance —
 * a progress bar only says "some".
 */
@Composable
private fun StepProgress(
    state: TutorialUiState,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(state.stepCount) { index ->
            val done = index <= state.stepIndex
            Box(
                Modifier
                    .size(if (index == state.stepIndex) 10.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (done) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            // How many steps are still to come is the whole point of the row,
                            // so those dots have to be visible rather than merely present.
                            MaterialTheme.colorScheme.outline
                        },
                    ),
            )
        }
        Box(Modifier.weight(1f))
        TextButton(onClick = onSkip) { Text(stringResource(R.string.action_skip)) }
    }
}

@Composable
private fun InstructionCard(
    state: TutorialUiState,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 620.dp)
            .border(
                width = 1.dp,
                // Lit while the step is still open, so "you have done it" is visible from the
                // edge of the card and not only in the sentence inside it.
                color = if (state.stepSolved) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(24.dp),
            ),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        // A real shadow: the card is over the board, so it has to read as being above it.
        shadowElevation = 12.dp,
        tonalElevation = 3.dp,
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.tutorial_progress, state.stepIndex + 1, state.stepCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(state.step.titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(state.step.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Announced by TalkBack as soon as it appears so the correction is not silent.
            AnimatedVisibility(state.hint != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    state.hint?.asString().orEmpty(),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            AnimatedVisibility(state.stepSolved, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    stringResource(R.string.tutorial_step_correct),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
