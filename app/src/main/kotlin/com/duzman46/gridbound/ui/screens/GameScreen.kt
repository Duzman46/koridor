package com.duzman46.gridbound.ui.screens

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.game.board.SeatColors
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.TurnRecord
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.online.model.MatchMessage
import com.duzman46.gridbound.online.model.MatchMessageKind
import com.duzman46.gridbound.presentation.game.GameEvent
import com.duzman46.gridbound.presentation.game.GameUiState
import com.duzman46.gridbound.presentation.game.GameViewModel
import com.duzman46.gridbound.presentation.game.MatchChatBubble
import com.duzman46.gridbound.ui.components.PlayerAvatar
import com.duzman46.gridbound.ui.game.GameBoard
import kotlinx.coroutines.delay

@Composable
fun GameRoute(
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onWinner: (PlayerId, GameUiState) -> Unit,
    onCompletedMatchExit: (onFinished: () -> Unit) -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is GameEvent.Feedback && event.hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
    // Leaving an online match forfeits it, so the result the player just caused arrives back
    // through the room a moment later. They asked for the door, not for a scoreboard on the
    // way to it, so once the exit is confirmed nothing else gets to route this screen.
    var leaving by remember { mutableStateOf(false) }
    val winner = state.winner?.takeIf { !leaving }
    LaunchedEffect(winner) {
        winner?.let { onWinner(it, state) }
    }
    GameScreen(
        state = state,
        onExit = {
            leaving = true
            viewModel.leaveGame(onHome)
        },
        onSettings = onSettings,
        onOpenProfile = onOpenProfile,
        // Restarting throws a match away and starts another — the same departure "play again"
        // makes from the victory screen, so it earns the same ad.
        //
        // Not on an untouched board, and that guard is load-bearing rather than tidy: with every
        // frequency gate gone this is the one ad trigger a player can press repeatedly without
        // leaving the screen, and ten taps would otherwise be ten ads. AdMob calls that invalid
        // traffic and closes accounts over it. A board with no moves on it is not a match anyone
        // is abandoning.
        onRestart = {
            if (state.boardState.history.isEmpty()) {
                viewModel.restart()
            } else {
                onCompletedMatchExit { viewModel.restart() }
            }
        },
        onUndo = viewModel::undo,
        onTileTap = viewModel::onTileTapped,
        onWallTap = viewModel::onWallTapped,
        onToggleWall = viewModel::toggleWallMode,
        onOrientation = viewModel::setWallOrientation,
        onConfirmWall = viewModel::confirmPendingWall,
        onCancelWall = viewModel::cancelPendingWall,
        onResign = viewModel::resign,
        onSendMessage = viewModel::sendMessage,
        onToggleMute = viewModel::toggleMatchMute,
    )
}

/**
 * Ticks once a second while an online match has a move clock, so the countdown stays live
 * without redrawing the board on every frame.
 */
@Composable
private fun rememberClockTick(enabled: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(enabled) {
        while (enabled) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    return now
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameScreen(
    state: GameUiState,
    onExit: () -> Unit,
    onSettings: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onRestart: () -> Unit,
    onUndo: () -> Unit,
    onTileTap: (com.duzman46.gridbound.game.models.Position) -> Unit,
    onWallTap: (com.duzman46.gridbound.game.models.Wall) -> Unit,
    onToggleWall: () -> Unit,
    onOrientation: (WallOrientation) -> Unit,
    onConfirmWall: () -> Unit,
    onCancelWall: () -> Unit,
    onResign: () -> Unit,
    onSendMessage: (MatchMessage) -> Unit,
    onToggleMute: () -> Unit,
) {
    var showHistory by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showResignConfirmation by remember { mutableStateOf(false) }
    // Back always asks, in every mode. It used to ask only online, on the reasoning that a bot
    // game and a shared handset are the player's own board and theirs to close — but a board
    // is a board however it was started, and a game thrown away by a back press aimed at
    // something else is lost the same way whether or not anybody else was in it. Online is
    // still the one that says what it costs: there is a person on the other end and leaving
    // files a resignation in their favour, which is what [GameUiState.leavingForfeits] picks
    // the wording for.
    //
    // The gesture is swallowed rather than previewed, which is the point of reaching for the
    // predictive handler on a screen that means to stay. A plain BackHandler lets the system
    // animate the board peeling away while the app is about to answer with a dialog — a
    // departure drawn and then refused, which is the half-swipe that slides back. Collecting
    // the progress and drawing nothing holds the board still, and abandoning the gesture
    // cancels this coroutine before the dialog is ever reached, so a half-swipe costs nothing.
    PredictiveBackHandler(enabled = !showExitConfirmation) { progress ->
        progress.collect {}
        showExitConfirmation = true
    }

    val clockRunning = state.isOnline && state.turnDeadlineAt != null &&
        state.boardState.status == GameStatus.IN_PROGRESS
    val now = rememberClockTick(clockRunning)

    if (showResignConfirmation) {
        AlertDialog(
            onDismissRequest = { showResignConfirmation = false },
            title = { Text(stringResource(R.string.game_resign_confirm_title)) },
            text = { Text(stringResource(R.string.game_resign_confirm_message)) },
            dismissButton = {
                TextButton(onClick = { showResignConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                Button(onClick = {
                    showResignConfirmation = false
                    onResign()
                }) { Text(stringResource(R.string.game_resign)) }
            },
        )
    }
    if (showHistory) {
        HistoryDialog(state.boardState.history, onDismiss = { showHistory = false })
    }
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text(stringResource(R.string.game_exit_title)) },
            text = {
                // Against a bot or on a shared handset the board is simply thrown away.
                // Online there is someone on the other end of it, and the price is the match.
                Text(
                    stringResource(
                        if (state.leavingForfeits) {
                            R.string.game_exit_online_message
                        } else {
                            R.string.game_exit_message
                        },
                    ),
                )
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text(stringResource(R.string.game_exit_keep_playing))
                }
            },
            confirmButton = {
                Button(onClick = onExit) { Text(stringResource(R.string.game_exit_confirm)) }
            },
        )
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Black)
                        Text(
                            stringResource(R.string.game_turn, state.boardState.turnNumber),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showExitConfirmation = true }) {
                        Icon(Icons.Rounded.Home, contentDescription = stringResource(R.string.game_home))
                    }
                },
                actions = {
                    if (state.mode != GameMode.ONLINE) {
                        IconButton(onClick = onUndo, enabled = state.canUndo && !state.isAiThinking) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.game_undo))
                        }
                        // Held while the bot thinks, exactly as undo is. Restarting mid-search
                        // abandons a turn that is already running and starts another, and at the
                        // expert tier that is a second of work per press — enough that a player
                        // tapping an unresponsive-looking button decides the app has hung.
                        IconButton(onClick = onRestart, enabled = !state.isAiThinking) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.game_restart))
                        }
                    } else if (state.boardState.status == GameStatus.IN_PROGRESS) {
                        IconButton(onClick = { showResignConfirmation = true }) {
                            Icon(
                                Icons.Rounded.Flag,
                                contentDescription = stringResource(R.string.game_resign),
                            )
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.game_settings))
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val wide = maxWidth >= Constants.Ui.TABLET_BREAKPOINT_DP.dp
            // Two people on one handset sit on opposite sides of it, so the screen is split
            // rather than shared: each seat gets its own controls on its own edge, and the
            // far one is turned through half a circle to face its player.
            val shared = state.mode == GameMode.LOCAL_TWO_PLAYER
            Column(
                modifier = Modifier.fillMaxSize().widthIn(max = Constants.Ui.CONTENT_MAX_WIDTH_DP.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!shared) {
                    TurnSummary(
                        state = state,
                        onHistory = { showHistory = true },
                        onOpenProfile = onOpenProfile,
                    )
                    OnlineClockBar(state, now)
                    MatchChatBar(state, onSendMessage, onToggleMute)
                }
                if (shared) {
                    SeatPanel(
                        state = state,
                        seat = PlayerId.PLAYER_TWO,
                        onToggleWall = onToggleWall,
                        onOrientation = onOrientation,
                        onConfirmWall = onConfirmWall,
                        onCancelWall = onCancelWall,
                        onHistory = null,
                        modifier = Modifier.rotate(180f),
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        GameBoard(
                            state,
                            onTileTap,
                            onWallTap,
                            Modifier.fillMaxHeight().widthIn(max = Constants.Ui.BOARD_MAX_SIZE_DP.dp),
                        )
                    }
                    SeatPanel(
                        state = state,
                        seat = PlayerId.PLAYER_ONE,
                        onToggleWall = onToggleWall,
                        onOrientation = onOrientation,
                        onConfirmWall = onConfirmWall,
                        onCancelWall = onCancelWall,
                        onHistory = { showHistory = true },
                    )
                } else if (wide) {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GameBoard(
                            state,
                            onTileTap,
                            onWallTap,
                            Modifier.weight(1.25f).fillMaxHeight().widthIn(max = Constants.Ui.BOARD_MAX_SIZE_DP.dp),
                        )
                        CompactGameControls(
                            state,
                            onToggleWall,
                            onOrientation,
                            onConfirmWall,
                            onCancelWall,
                            Modifier.weight(0.75f).widthIn(max = 420.dp),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        GameBoard(
                            state,
                            onTileTap,
                            onWallTap,
                            Modifier.fillMaxHeight().widthIn(max = Constants.Ui.BOARD_MAX_SIZE_DP.dp),
                        )
                    }
                    CompactGameControls(
                        state,
                        onToggleWall,
                        onOrientation,
                        onConfirmWall,
                        onCancelWall,
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Move clock for online matches.
 *
 * There is nothing to press: the clock reaching zero ends the match by itself, on both
 * devices. All this has to do is make the last seconds impossible to miss, which is why the
 * whole bar goes red rather than only the digits.
 */
@Composable
private fun OnlineClockBar(state: GameUiState, now: Long) {
    val deadline = state.turnDeadlineAt
    if (!state.isOnline || deadline == null) return
    if (state.boardState.status != GameStatus.IN_PROGRESS) return

    val remaining = (deadline - now).coerceAtLeast(0L)
    val urgent = remaining <= Constants.Online.TURN_WARNING_MILLIS
    val yourTurn = state.boardState.currentPlayer == state.localPlayer

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (urgent) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(
                    if (yourTurn) R.string.game_your_turn else R.string.game_rival_turn,
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(
                    R.string.game_turn_timer,
                    remaining / 60_000L,
                    (remaining / 1_000L) % 60L,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/**
 * Everything the match has to say, and the one way to say something back.
 *
 * The row is reserved for the whole of an online match instead of appearing when a message
 * arrives. The board is measured out of what is left over, so a strip that came and went would
 * resize it mid-game — and a board that changes size under a thumb already on its way down is
 * how a tap meant for one square lands on the next.
 *
 * The way in sits at the end of this row rather than over the board or in the app bar, which
 * puts it where the messages are without putting it anywhere near a square. It is above the
 * board, and the board is square inside a box that is not: on a tall screen the board is as
 * wide as the screen allows and floats in the spare height, so there is dead space beneath
 * this button; on a squat one it is as tall as the screen allows and narrower than this row,
 * so the button overhangs the background instead. Either way a thumb that misses it misses the
 * board too.
 */
@Composable
private fun MatchChatBar(
    state: GameUiState,
    onSend: (MatchMessage) -> Unit,
    onToggleMute: () -> Unit,
) {
    if (!state.showsMatchMessages) return
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        MatchMessageSheet(
            canSend = state.canSendMessage,
            muted = state.matchMessagesMuted,
            onPick = {
                picking = false
                onSend(it)
            },
            onToggleMute = {
                picking = false
                onToggleMute()
            },
            onDismiss = { picking = false },
        )
    }
    MatchChatRow(
        modifier = Modifier.fillMaxWidth().heightIn(min = chatRowHeight()),
        rival = {
            ChatBubble(
                bubble = state.rivalBubble,
                container = MaterialTheme.colorScheme.secondaryContainer,
            )
        },
        own = {
            ChatBubble(
                bubble = state.ownBubble,
                container = MaterialTheme.colorScheme.surfaceVariant,
            )
        },
        action = {
            IconButton(onClick = { picking = true }, enabled = state.canOpenMessages) {
                Icon(
                    imageVector = if (state.matchMessagesMuted) {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    } else {
                        Icons.Rounded.Mood
                    },
                    // The way in is also the only sign that a mute is on, so it says which it is.
                    contentDescription = stringResource(
                        if (state.matchMessagesMuted) R.string.chat_muted else R.string.chat_open,
                    ),
                )
            }
        },
    )
}

/** Between the two bubbles, and between the near bubble and the way in. */
private val CHAT_ROW_GAP = 8.dp

/**
 * The two bubbles and the way in, on one line: the rival's at the start, this player's at the
 * end beside the button.
 *
 * The sides are the argument for laying it out by hand. What the rival said arrives on their
 * side and what this player said stays on theirs, so a glance tells them apart without reading
 * either — and that is the whole of what a [Row] with a weight each was buying. What it cost
 * was the width: half the row apiece however little is in either half, when eight of the
 * fourteen messages are phrases and the longest of them is "Bonne chance la prochaine fois".
 * Half of a 360 dp handset holds about a third of that, so the common case — one seat talking,
 * the other silent — ellipsized a phrase for want of space that was standing empty beside it.
 *
 * Each bubble is measured for what it asks for instead, and [chatBubbleWidths] decides what to
 * do when both asks together will not fit. A bubble held under its ask wraps rather than losing
 * its end, so this reports whatever height that takes; the caller's [chatRowHeight] floor is
 * what keeps the row the same height full as empty.
 */
@Composable
private fun MatchChatRow(
    modifier: Modifier,
    rival: @Composable () -> Unit,
    own: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    Layout(contents = listOf(rival, own, action), modifier = modifier) { slots, constraints ->
        val (rivalSlot, ownSlot, actionSlot) = slots
        // A bubble with nothing to say emits nothing at all, so a slot can be empty; the gaps
        // are counted from what is actually there rather than assumed.
        val rivalAsks = rivalSlot.firstOrNull()
        val ownAsks = ownSlot.firstOrNull()
        val gap = CHAT_ROW_GAP.roundToPx()
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val button = actionSlot.first().measure(loose)
        val gaps = gap * listOfNotNull(rivalAsks, ownAsks).size
        val (rivalWidth, ownWidth) = chatBubbleWidths(
            available = (width - button.width - gaps).coerceAtLeast(0),
            rivalAsk = rivalAsks?.maxIntrinsicWidth(constraints.maxHeight) ?: 0,
            ownAsk = ownAsks?.maxIntrinsicWidth(constraints.maxHeight) ?: 0,
        )
        val rivalBubble = rivalAsks?.measure(loose.copy(maxWidth = rivalWidth))
        val ownBubble = ownAsks?.measure(loose.copy(maxWidth = ownWidth))

        val height = maxOf(button.height, rivalBubble?.height ?: 0, ownBubble?.height ?: 0)
            .coerceAtLeast(constraints.minHeight)
        layout(width, height) {
            // placeRelative and not place: "their side" and "ours" are the start and the end of
            // the row, which trade places in Arabic along with everything else on the screen.
            rivalBubble?.placeRelative(0, (height - rivalBubble.height) / 2)
            if (ownBubble != null) {
                val start = width - button.width - gap - ownBubble.width
                ownBubble.placeRelative(start, (height - ownBubble.height) / 2)
            }
            button.placeRelative(width - button.width, (height - button.height) / 2)
        }
    }
}

/**
 * How wide each bubble is drawn, given the [available] width for the two of them and what each
 * one asks for.
 *
 * A row that can hold both asks gives each of them exactly what it asked for. Only a side asking
 * for more than its half can lose anything, and it loses it to a side that asked for less: a
 * face beside a phrase keeps its own width — squeezing it any further would clip a glyph that
 * has nowhere to wrap to — and the phrase takes the rest of the row. Two long phrases at once is
 * the one case with no room to find, and half each is the fair answer to it.
 *
 * Pulled out of the measure policy so the widths can be asserted in a unit test rather than
 * discovered on a phone, and against the one thing that is easy to get wrong here: the answer
 * has to depend on what is in the row, which a pair of static weights cannot.
 */
internal fun chatBubbleWidths(available: Int, rivalAsk: Int, ownAsk: Int): Pair<Int, Int> {
    if (available <= 0) return 0 to 0
    val rival = rivalAsk.coerceIn(0, available)
    val own = ownAsk.coerceIn(0, available)
    if (rival + own <= available) return rival to own
    val half = available / 2
    return when {
        rival <= half -> rival to available - rival
        own <= half -> available - own to own
        else -> half to available - half
    }
}

/** How many lines a phrase may wrap onto before its end is given up as unreadable anyway. */
private const val CHAT_BUBBLE_LINES = 2

/** [ChatBubble]'s own vertical padding, which the row has to hold open on top of the text. */
private val CHAT_BUBBLE_PADDING = 6.dp

/** The send button's touch target: the one thing in the row that does not scale with type. */
private val CHAT_ACTION_TOUCH_TARGET = 48.dp

/**
 * The height the message row holds open for the whole of a match, whatever is in it.
 *
 * The row is reserved rather than raised when a message arrives, and this is the number that
 * reserves it: worked out from the type the bubbles are drawn with, so it already covers the
 * tallest either seat can produce at the font scale in force. It was a flat 48 dp, which is the
 * button's touch target and has nothing to do with text — at the largest accessibility scale a
 * bubble is nearly twice that, and the parent clipped the words instead of showing them.
 *
 * A floor and not a measurement, in both directions. Upwards, because a reserve that turns out
 * to be short must grow rather than cut a phrase off — the arithmetic here is about the styles
 * this file draws with, and a floor is what survives being wrong about them. Downwards, because
 * the board is measured out of what this row leaves: a row that grew when the first message
 * landed would move every square under a thumb already on its way down.
 */
@Composable
private fun chatRowHeight(): Dp {
    val glyph = MaterialTheme.typography.titleMedium
    val label = MaterialTheme.typography.labelLarge
    return with(LocalDensity.current) {
        // A style may state a size without stating a line height; none of them state neither.
        chatRowHeight(
            glyphLine = glyph.lineHeight.takeOrElse { glyph.fontSize }.toDp(),
            labelLine = label.lineHeight.takeOrElse { label.fontSize }.toDp(),
        )
    }
}

/** Pulled out of the composable so the reserve can be asserted at font scales nobody tests on. */
internal fun chatRowHeight(glyphLine: Dp, labelLine: Dp): Dp = maxOf(
    CHAT_ACTION_TOUCH_TARGET,
    maxOf(glyphLine, labelLine * CHAT_BUBBLE_LINES) + CHAT_BUBBLE_PADDING * 2,
)

/**
 * One message, shown for a few seconds and then gone.
 *
 * There is nothing to dismiss. A bubble waiting to be tapped is one more thing to hit by
 * mistake beside a board, and a rival who says nothing further would otherwise leave their last
 * word sitting there for the rest of the match. A message that is already past its welcome when
 * it first arrives — which is what a player rejoining a match reads out of the room — is never
 * shown at all.
 */
@Composable
private fun ChatBubble(
    bubble: MatchChatBubble?,
    container: Color,
) {
    val entry = bubble?.entry
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(entry?.userId, entry?.sentAt) {
        val remaining = entry?.let {
            Constants.Online.CHAT_VISIBLE_MILLIS - (System.currentTimeMillis() - it.sentAt)
        } ?: 0L
        visible = remaining > 0L
        if (visible) {
            delay(remaining)
            visible = false
        }
    }
    // Faded rather than removed, so the message is still there to draw while it goes.
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "chatBubbleAlpha")
    if (bubble == null || entry == null || alpha == 0f) return

    val label = stringResource(entry.message.labelRes)
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        modifier = Modifier.alpha(alpha).semantics(mergeDescendants = true) {
            contentDescription = label
        },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = CHAT_BUBBLE_PADDING),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(entry.message.glyph, style = MaterialTheme.typography.titleMedium)
            if (bubble.showsWords) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    // Wrapping is the answer to a phrase that will not fit on one line, and
                    // ellipsis only to one that will not fit on [CHAT_BUBBLE_LINES] of them —
                    // which needs both seats talking at once on a narrow screen. The row's
                    // reserve is worked out from the same number; see [chatRowHeight].
                    maxLines = CHAT_BUBBLE_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The whole vocabulary, in one sheet.
 *
 * Faces and phrases share it rather than getting a control each: they are the same act — saying
 * something without typing it — and a second way in would be a second thing to hit on a screen
 * whose only target that matters is the board. Sorting them into "an emoji" or "a phrase"
 * before choosing what to say would be asking the player about the implementation.
 *
 * The mute lives at the bottom of it, next to the thing being muted, because that is where
 * somebody is standing when a rival has said the same thing eleven times. It is a mute for
 * this match and it is the same control that lifts it, which is why it reads as the state it
 * is in rather than as an instruction. The permanent answer is the settings switch, and it
 * stays there.
 *
 * [canSend] is false while the sheet has been opened for the mute alone — the connection has
 * dropped under a muted player, or the match has ended with the mute still on. The vocabulary
 * goes grey rather than away: what cannot be sent must not be offered, and a sheet that changes
 * shape depending on the connection is a worse answer than one that says which half is closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchMessageSheet(
    canSend: Boolean,
    muted: Boolean,
    onPick: (MatchMessage) -> Unit,
    onToggleMute: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.chat_open),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MatchMessage.entries
                    .filter { it.kind == MatchMessageKind.REACTION }
                    .forEach { message ->
                        val label = stringResource(message.labelRes)
                        IconButton(
                            onClick = { onPick(message) },
                            modifier = Modifier.semantics { contentDescription = label },
                            enabled = canSend,
                        ) {
                            Text(message.glyph, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MatchMessage.entries
                    .filter { it.kind == MatchMessageKind.PHRASE }
                    .forEach { message ->
                        SuggestionChip(
                            onClick = { onPick(message) },
                            label = { Text(stringResource(message.labelRes)) },
                            enabled = canSend,
                            icon = { Text(message.glyph) },
                        )
                    }
            }
            TextButton(onClick = onToggleMute, modifier = Modifier.align(Alignment.End)) {
                Icon(
                    imageVector = if (muted) {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(if (muted) R.string.chat_unmute else R.string.chat_mute),
                    Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CompactGameControls(
    state: GameUiState,
    onToggleWall: () -> Unit,
    onOrientation: (WallOrientation) -> Unit,
    onConfirmWall: () -> Unit,
    onCancelWall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.onlineMessage?.let { message ->
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
                Text(message.asString(), Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WallControls(
                    state = state,
                    enabled = state.acceptsHumanInput,
                    onToggleWall = onToggleWall,
                    onOrientation = onOrientation,
                    onConfirmWall = onConfirmWall,
                    onCancelWall = onCancelWall,
                )
            }
        }
    }
}

/**
 * One player's own side of a shared handset.
 *
 * Two of these frame the board, the far one turned through half a circle so it faces the
 * player sitting opposite. Only the seat on the clock carries controls; the other keeps its
 * name and wall count and nothing to press, which is what stops the wrong player moving.
 */
@Composable
private fun SeatPanel(
    state: GameUiState,
    seat: PlayerId,
    onToggleWall: () -> Unit,
    onOrientation: (WallOrientation) -> Unit,
    onConfirmWall: () -> Unit,
    onCancelWall: () -> Unit,
    onHistory: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val active = state.boardState.currentPlayer == seat &&
        state.boardState.status == GameStatus.IN_PROGRESS
    val seatColor = SeatColors.pawn(seat)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = if (active) seatColor else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            ),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (active) 3.dp else 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .alpha(if (active) 1f else 0.35f)
                        .background(seatColor, RoundedCornerShape(50)),
                )
                Text(
                    pluralStringResource(
                        R.plurals.game_player_walls,
                        state.boardState.player(seat).wallsRemaining,
                        playerName(seat),
                        state.boardState.player(seat).wallsRemaining,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(
                        if (active) R.string.game_your_turn else R.string.game_rival_turn,
                    ),
                    modifier = Modifier.weight(1f).alpha(if (active) 1f else 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onHistory != null) {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.game_history))
                    }
                }
            }
            if (active) {
                WallControls(
                    state = state,
                    enabled = state.acceptsHumanInput,
                    onToggleWall = onToggleWall,
                    onOrientation = onOrientation,
                    onConfirmWall = onConfirmWall,
                    onCancelWall = onCancelWall,
                )
            }
        }
    }
}

/** The pawn/wall action area, shared by the single control strip and by both seats. */
@Composable
private fun WallControls(
    state: GameUiState,
    enabled: Boolean,
    onToggleWall: () -> Unit,
    onOrientation: (WallOrientation) -> Unit,
    onConfirmWall: () -> Unit,
    onCancelWall: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            state.pendingWall != null -> {
                Text(
                    stringResource(R.string.game_wall_confirm_hint),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancelWall, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                        Text(stringResource(R.string.game_wall_cancel))
                    }
                    Button(onClick = onConfirmWall, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Text(stringResource(R.string.game_wall_confirm))
                    }
                }
            }

            state.wallMode -> {
                Text(
                    stringResource(R.string.game_wall_pick_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WallOrientation.entries.forEach { orientation ->
                        FilterChip(
                            selected = state.wallOrientation == orientation,
                            onClick = { onOrientation(orientation) },
                            label = {
                                Text(
                                    if (orientation == WallOrientation.HORIZONTAL) {
                                        stringResource(R.string.game_wall_horizontal)
                                    } else {
                                        stringResource(R.string.game_wall_vertical)
                                    },
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                        )
                    }
                    OutlinedButton(onClick = onToggleWall, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.game_history_pawn_label))
                    }
                }
            }

            else -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.game_move_hint),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onToggleWall, enabled = enabled) {
                        Text(stringResource(R.string.game_place_wall))
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnSummary(
    state: GameUiState,
    onHistory: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val current = state.boardState.currentPlayer
    val turnTitle = when {
        state.mode == GameMode.ONLINE && state.isOnlineSyncing -> stringResource(R.string.game_sending_move)
        state.mode == GameMode.ONLINE && !state.isOnlineConnected -> stringResource(R.string.game_connecting)
        state.mode == GameMode.ONLINE && current == state.localPlayer ->
            stringResource(R.string.game_turn_yours, playerName(current))
        state.mode == GameMode.ONLINE -> stringResource(R.string.game_turn_opponent)
        state.isAiThinking -> stringResource(R.string.game_ai_thinking)
        current == PlayerId.PLAYER_ONE -> stringResource(R.string.game_turn_blue)
        else -> stringResource(R.string.game_turn_red)
    }
    val activeColor = SeatColors.pawn(current)
    val shouldPulse = state.acceptsHumanInput || state.mode == GameMode.LOCAL_TWO_PLAYER
    val transition = rememberInfiniteTransition(label = "turnBeacon")
    val beaconAlpha by transition.animateFloat(
        initialValue = if (shouldPulse) 0.42f else 0.72f,
        targetValue = if (shouldPulse) 1f else 0.72f,
        animationSpec = infiniteRepeatable(tween(720), RepeatMode.Reverse),
        label = "turnBeaconAlpha",
    )
    Surface(
        modifier = Modifier.border(2.dp, activeColor.copy(alpha = beaconAlpha), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .alpha(beaconAlpha)
                                .background(activeColor, RoundedCornerShape(50)),
                        )
                        Text(turnTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        when (state.mode) {
                            GameMode.VS_AI ->
                                stringResource(R.string.game_vs_ai, state.difficulty.label())
                            GameMode.LOCAL_TWO_PLAYER -> stringResource(R.string.game_local_two_player)
                            GameMode.ONLINE -> stringResource(R.string.game_online_match)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isAiThinking || state.isOnlineSyncing || (state.mode == GameMode.ONLINE && !state.isOnlineConnected)) {
                    CircularProgressIndicator()
                }
                IconButton(onClick = onHistory) {
                    Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.game_history))
                }
            }
            // Half the row each at most, but only as much of it as they need: short labels
            // still sit apart on the two edges, while a sixteen-character username next to
            // "10 Mauern" is trimmed rather than shoving the other seat off the screen.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SeatWalls(state, PlayerId.PLAYER_ONE, onOpenProfile, Modifier.weight(1f, false))
                SeatWalls(state, PlayerId.PLAYER_TWO, onOpenProfile, Modifier.weight(1f, false))
            }
        }
    }
}

/**
 * One seat's wall count under the turn banner.
 *
 * Online, the rival's half is a chip carrying their face and their username, and it is the way
 * to their profile — this is the only line on the board screen that says who is actually on
 * the other end of it, so it is the only honest place to put the tap. The other two modes keep
 * plain text: a bot has no profile, and the person across the handset is already in the room.
 */
@Composable
private fun SeatWalls(
    state: GameUiState,
    seat: PlayerId,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val walls = state.boardState.player(seat).wallsRemaining
    val opponent = state.onlineOpponent?.takeIf { state.isOnline && seat != state.localPlayer }
    if (opponent == null) {
        PlayerWalls(playerName(seat), walls, modifier)
        return
    }
    val openLabel = stringResource(R.string.cd_open_profile)
    Surface(
        onClick = { onOpenProfile(opponent.userId) },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${opponent.username}, $openLabel"
        },
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlayerAvatar(opponent.avatarId, opponent.username, size = 22.dp)
            PlayerWalls(opponent.username, walls)
        }
    }
}

/** A seat's name is its colour, and the two are the same fact: seat one is blue. */
@Composable
private fun playerName(player: PlayerId): String = stringResource(
    if (player == PlayerId.PLAYER_ONE) R.string.game_player_blue else R.string.game_player_red,
)

@Composable
private fun PlayerWalls(name: String, count: Int, modifier: Modifier = Modifier) {
    Text(
        pluralStringResource(R.plurals.game_player_walls, count, name, count),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HistoryDialog(history: List<TurnRecord>, onDismiss: () -> Unit) {
    val recent = history.takeLast(12).reversed()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_history)) },
        text = {
            if (recent.isEmpty()) {
                Text(stringResource(R.string.game_history_empty))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent) { record ->
                        Text(
                            "${record.turnNumber}. ${playerName(record.player)} · ${record.action.label()}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun GameAction.label(): String = when (this) {
    is GameAction.MovePawn ->
        stringResource(R.string.game_history_pawn, target.row + 1, target.column + 1)
    is GameAction.PlaceWall -> if (wall.orientation == WallOrientation.HORIZONTAL) {
        stringResource(R.string.game_wall_horizontal_full)
    } else {
        stringResource(R.string.game_wall_vertical_full)
    }
}

@Composable
private fun Difficulty.label(): String = when (this) {
    Difficulty.EASY -> stringResource(R.string.difficulty_easy)
    Difficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
    Difficulty.HARD -> stringResource(R.string.difficulty_hard)
    Difficulty.EXPERT -> stringResource(R.string.difficulty_expert)
}
