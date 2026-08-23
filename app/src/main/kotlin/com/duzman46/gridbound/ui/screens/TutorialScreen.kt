package com.duzman46.gridbound.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.presentation.tutorial.TutorialUiState
import com.duzman46.gridbound.presentation.tutorial.TutorialViewModel
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.home.PremiumActionButton
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.ui.components.home.PremiumTextAction
import com.duzman46.gridbound.ui.components.home.TutorialCard
import com.duzman46.gridbound.ui.components.home.TutorialHintTone
import com.duzman46.gridbound.ui.components.home.TutorialNote
import com.duzman46.gridbound.ui.components.home.TutorialSolvedTone
import com.duzman46.gridbound.ui.components.home.TutorialStepDots
import com.duzman46.gridbound.ui.components.home.TutorialStepHeading
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
 *
 * It is a pushed screen and not one of the places the docked bar switches between, so it takes
 * the system insets itself: the dots clear the status bar and the card clears the gesture bar.
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
        // Still a Material dialog. Dialogs are the one thing in this app that stay Material —
        // they own the scrim, the placement and the dismiss behaviour, and a hand-drawn one
        // would be re-implementing a window manager to change a corner radius.
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
                    .padding(horizontal = Dimens.SpaceLg)
                    .padding(top = TutorialTopInset, bottom = InstructionCardReserve),
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
                    .padding(horizontal = Dimens.SpaceLg),
            )

            InstructionCard(
                state = state,
                onNext = onNext,
                onPrevious = onPrevious,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(Dimens.SpaceLg),
            )
        }
    }
}

/** The dots, and the way out, on one line above the board. */
@Composable
private fun StepProgress(
    state: TutorialUiState,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TutorialStepDots(
            stepIndex = state.stepIndex,
            stepCount = state.stepCount,
            modifier = Modifier.weight(1f),
        )
        PremiumTextAction(label = stringResource(R.string.action_skip), onClick = onSkip)
    }
}

/**
 * What the board gives up at the top and the bottom of the lesson screen.
 *
 * [InstructionCardReserve] is the instruction card's own height plus the gap under it, and it is
 * written here rather than at the call site because it has to be *this* card's height: the card
 * floats over the board, and if the reserve is short the last row of squares sits under it — on
 * a lesson, under the very control asking the player to tap them. Named for the same reason
 * `WallBarHeight` is named on the board screen; the number is a dependency, not a taste.
 */
private val TutorialTopInset = 64.dp
private val InstructionCardReserve = 210.dp

@Composable
private fun InstructionCard(
    state: TutorialUiState,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TutorialCard(solved = state.stepSolved, modifier = modifier) {
        TutorialStepHeading(
            progress = stringResource(R.string.tutorial_progress, state.stepIndex + 1, state.stepCount),
            title = stringResource(state.step.titleRes),
            body = stringResource(state.step.messageRes),
        )

        // Both notes announce themselves to TalkBack as they appear — see TutorialNote, which
        // carries the live region so neither of these can be added without it.
        AnimatedVisibility(state.hint != null, enter = fadeIn(), exit = fadeOut()) {
            TutorialNote(
                icon = PremiumIcon.INFO,
                tone = TutorialHintTone,
                text = state.hint?.asString().orEmpty(),
            )
        }
        AnimatedVisibility(state.stepSolved, enter = fadeIn(), exit = fadeOut()) {
            TutorialNote(
                icon = PremiumIcon.TARGET,
                tone = TutorialSolvedTone,
                text = stringResource(R.string.tutorial_step_correct),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = Dimens.SpaceXs),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            PremiumActionButton(
                label = stringResource(R.string.action_previous),
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
                enabled = !state.isFirstStep,
            )
            // Gold only once the step is passed. A filled button that is dead is a button that
            // gets pressed, and on a lesson that is a player concluding the app is broken rather
            // than that they have not finished the move.
            PremiumActionButton(
                label = stringResource(
                    if (state.isLastStep) R.string.tutorial_finish else R.string.action_next,
                ),
                onClick = onNext,
                modifier = Modifier.weight(1f),
                filled = true,
                enabled = state.stepSolved,
            )
        }
    }
}
