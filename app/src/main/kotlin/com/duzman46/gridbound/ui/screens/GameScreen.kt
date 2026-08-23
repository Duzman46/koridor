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
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.LocalKoridorColors
import com.duzman46.gridbound.theme.Palette
import com.duzman46.gridbound.ui.components.home.PremiumTextAction
import com.duzman46.gridbound.ui.components.home.SheetGrip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import com.duzman46.gridbound.ui.components.drawPawnMark

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
    // Not LocalHapticFeedback. That is View.performHapticFeedback, which the platform silently
    // drops whenever the system-wide touch-feedback switch is off — and on the owner's handset
    // it is, so every move in the game was asking for a vibration that never happened. See
    // HapticsManager for why driving the vibrator directly is the correct fix and not a
    // workaround.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is GameEvent.Feedback && event.hapticsEnabled) {
                viewModel.vibrate(event.effect)
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
            // A TextButton in gold, not a filled Material Button. Dialogs are the one thing in
            // this app that stay Material — hand-rolling a window manager is not worth it — but
            // the two words inside them are ours, and PremiumNotice already draws its confirm
            // exactly this way. It also stops the destructive answer being the loud one.
            confirmButton = {
                TextButton(onClick = {
                    showResignConfirmation = false
                    onResign()
                }) { Text(stringResource(R.string.game_resign), color = Palette.Gold) }
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
                TextButton(onClick = onExit) {
                    Text(stringResource(R.string.game_exit_confirm), color = Palette.Gold)
                }
            },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GameTopBar(
                turn = state.boardState.turnNumber,
                onHome = { showExitConfirmation = true },
                onSettings = onSettings,
                trailing = {
                    if (state.mode != GameMode.ONLINE) {
                        GameBarButton(
                            icon = Icons.AutoMirrored.Rounded.Undo,
                            label = stringResource(R.string.game_undo),
                            enabled = state.canUndo && !state.isAiThinking,
                            onClick = onUndo,
                        )
                        // Held while the bot thinks, exactly as undo is. Restarting mid-search
                        // abandons a turn that is already running and starts another, and at the
                        // expert tier that is a second of work per press — enough that a player
                        // tapping an unresponsive-looking button decides the app has hung.
                        GameBarButton(
                            icon = Icons.Rounded.Refresh,
                            label = stringResource(R.string.game_restart),
                            enabled = !state.isAiThinking,
                            onClick = onRestart,
                        )
                    } else if (state.boardState.status == GameStatus.IN_PROGRESS) {
                        GameBarButton(
                            icon = Icons.Rounded.Flag,
                            label = stringResource(R.string.game_resign),
                            enabled = true,
                            onClick = { showResignConfirmation = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = SCREEN_INSET, vertical = 8.dp),
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
                        modifier = Modifier.fillMaxWidth().weight(1f).bleedHorizontally(SCREEN_INSET),
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
                        modifier = Modifier.fillMaxWidth().weight(1f).bleedHorizontally(SCREEN_INSET),
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
 * The warm wash behind an expiring move clock.
 *
 * A twelve per cent ember over [Palette.Card], dark enough that the bar is still a card and not
 * a slab. It is local to this file rather than a [Palette] token because it is the only place in
 * the app that earns a warm ground; promoting it would invite a second one.
 */
private val LiveWell = Color(0xFF2A1408)

/**
 * Move clock for online matches.
 *
 * There is nothing to press: the clock reaching zero ends the match by itself, on both
 * devices. All this has to do is make the last seconds impossible to miss.
 *
 * It used to do that with `errorContainer`, which this app never sets — so the last ten seconds
 * of a turn were announced in Material's baseline crimson `#8C1D18`, a hue that appears nowhere
 * else in Koridor, on a slab eight dp under a board the owner drew by hand. The quiet state was
 * no better: `surfaceVariant` sits 1.03:1 against [TurnSummary]'s ground, which is a card that
 * cannot be seen against the card above it.
 *
 * So the bar is the same card as everything else on this screen, and urgency is carried by the
 * border going from gold to ember and the digits going with it — the app's own `live`, which is
 * what it already means everywhere else: this is running out. The warm area shrinks from a
 * filled slab to a hairline and four characters, which is the opposite direction from where it
 * was and the reason a warm accent is defensible on a screen with a red seat on it.
 */
@Composable
private fun OnlineClockBar(state: GameUiState, now: Long) {
    val deadline = state.turnDeadlineAt
    if (!state.isOnline || deadline == null) return
    if (state.boardState.status != GameStatus.IN_PROGRESS) return

    val remaining = (deadline - now).coerceAtLeast(0L)
    val urgent = remaining <= Constants.Online.TURN_WARNING_MILLIS
    val yourTurn = state.boardState.currentPlayer == state.localPlayer
    val ember = LocalKoridorColors.current.live
    val shape = RoundedCornerShape(Dimens.RadiusMd)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (urgent) LiveWell else Palette.Card)
            .border(
                Dimens.Hairline,
                if (urgent) ember else Palette.Gold.copy(alpha = 0.45f),
                shape,
            ),
    ) {
        Row(
            Modifier.padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
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
                color = if (urgent) ember else Palette.Gold,
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
        // One bubble colour for both speakers. They were told apart by hue, and the rival's hue
        // was `secondaryContainer` — which this app never set, so it resolved to Material's
        // baseline grey-violet `#4A4458` and put a purple bubble eight dp above the board. The
        // sides already say who is talking: [MatchChatRow] places the rival at the start edge
        // and this player at the end, and does it with placeRelative so it survives Arabic. The
        // colour was doing a job the layout had already done, so it stopped being a colour.
        rival = { ChatBubble(bubble = state.rivalBubble) },
        own = { ChatBubble(bubble = state.ownBubble) },
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
private fun ChatBubble(bubble: MatchChatBubble?) {
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
    Box(
        modifier = Modifier
            .alpha(alpha)
            .clip(RoundedCornerShape(50))
            .background(Palette.Inset)
            .semantics(mergeDescendants = true) { contentDescription = label },
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
        // The same three lines LobbyFormSheet and the friends sheet already carry, and for the
        // same reason: left to itself this sheet takes `surfaceContainerLow`, which resolves to
        // Material's purple-tinted `#1D1B20` rather than the app's card, and Material's grey
        // drag pill. Two of the app's three sheets had already been fixed. This was the third,
        // and it is the one on the board screen.
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = Dimens.SpaceMd, bottom = Dimens.SpaceXs)) {
                SheetGrip(Modifier.align(Alignment.Center))
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // The vocabulary is fourteen entries and the phrases wrap onto as many rows as
                // the longest translation needs, so the sheet has to be able to give.
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            Text(
                stringResource(R.string.chat_open),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Palette.Gold,
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
                        // A box with a touch target, not an IconButton: Material's ripple is the
                        // only ripple that would have been left anywhere near the board screen,
                        // and the face is already the whole of the affordance. Dimmed rather
                        // than hidden when the connection is down — what cannot be sent must not
                        // be offered, and a sheet that changes shape mid-match is worse.
                        Box(
                            Modifier
                                .size(MIN_TOUCH_TARGET)
                                .clip(CircleShape)
                                .alpha(if (canSend) 1f else 0.4f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = canSend,
                                    role = Role.Button,
                                    onClick = { onPick(message) },
                                )
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(message.glyph, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            ) {
                MatchMessage.entries
                    .filter { it.kind == MatchMessageKind.PHRASE }
                    .forEach { message ->
                        // The board screen's own button, not Material's SuggestionChip — which
                        // brought its own outline, its own radius and its own label metrics to a
                        // sheet that opens over the board. The face rides in the label because
                        // this control draws its marks and an emoji is not one of them.
                        BoardActionButton(
                            label = "${message.glyph} ${stringResource(message.labelRes)}",
                            filled = false,
                            enabled = canSend,
                            onClick = { onPick(message) },
                        )
                    }
            }
            PremiumTextAction(
                label = stringResource(if (muted) R.string.chat_unmute else R.string.chat_mute),
                onClick = onToggleMute,
                modifier = Modifier.align(Alignment.End),
            )
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
            // Was `tertiaryContainer`, which this app never sets — so a strip that says "rival
            // disconnected" or "move sending" was painted in Material's baseline wine `#633B48`
            // with pink text on it, during a match, directly under the board. It is a status
            // line, and status lines in this app are quiet: the same card, the same radius and
            // the same gold hairline as everything else in this column.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.RadiusMd))
                    .background(Palette.Card)
                    .border(
                        Dimens.Hairline,
                        Palette.Gold.copy(alpha = 0.35f),
                        RoundedCornerShape(Dimens.RadiusMd),
                    ),
            ) {
                // A live region. This strip is how the match says the rival has dropped, that a
                // move is still being sent, or that the connection is back — and it appeared in
                // silence, which on a board screen is the same as not appearing at all.
                Text(
                    message.asString(),
                    Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.InkMuted,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.RadiusMd))
                .background(Palette.Card)
                .border(
                    Dimens.Hairline,
                    Palette.Gold.copy(alpha = 0.35f),
                    RoundedCornerShape(Dimens.RadiusMd),
                ),
        ) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
 *
 * The active seat lifts by [Palette.Inset] over [Palette.Card] — the same 1.19:1 step every
 * other raised thing in the app uses. It was `tonalElevation`, the only one in the codebase,
 * which blends `surfaceTint` over the surface; `darkColorScheme` defaults that to the primary,
 * so the active seat was being washed with gold at roughly eleven per cent on the one screen
 * where two seats have to be told apart at a glance. Nobody chose that colour and nobody could
 * have named it.
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
    val shape = RoundedCornerShape(Dimens.RadiusMd)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) Palette.Inset else Palette.Card)
            .border(
                width = if (active) 2.dp else Dimens.Hairline,
                color = if (active) seatColor else Palette.Edge,
                shape = shape,
            ),
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
                // Two inks rather than one ink at two opacities. `InkMuted` at 60% blends to
                // roughly #5F646B on this card, which is 3.0:1 — under the floor for text, and
                // invisibly so, because alpha is how a contrast budget gets spent without
                // anyone noticing. The waiting seat takes [Palette.InkGlyph] at full strength
                // instead: still a step quieter, still 4.50:1.
                Text(
                    stringResource(
                        if (active) R.string.game_your_turn else R.string.game_rival_turn,
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) Palette.InkMuted else Palette.InkGlyph,
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

/**
 * The height every state of the action bar occupies, whatever is in it.
 *
 * The bar sits under the board and the board takes what is left, so a bar that grows when wall
 * mode opens moves the board. On a handset the board is square and limited by the width, so it
 * does not resize when the bar grows — it slides up by half of whatever the bar took, and that
 * slide is the flicker on the first tap of "place a wall". Reserving the tallest state spends
 * slack that was doing nothing and buys a board that holds still.
 */
private val WallBarHeight = 88.dp

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
    Box(
        Modifier.fillMaxWidth().heightIn(min = WallBarHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.pendingWall != null -> {
                    WallHint(stringResource(R.string.game_wall_confirm_hint), strong = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoardActionButton(
                            label = stringResource(R.string.game_wall_cancel),
                            filled = false,
                            modifier = Modifier.weight(1f),
                            glyph = { drawCrossGlyph(it) },
                            onClick = onCancelWall,
                        )
                        BoardActionButton(
                            label = stringResource(R.string.game_wall_confirm),
                            filled = true,
                            modifier = Modifier.weight(1f),
                            glyph = { drawTickGlyph(it) },
                            onClick = onConfirmWall,
                        )
                    }
                }

                state.wallMode -> {
                    WallHint(stringResource(R.string.game_wall_pick_hint))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WallOrientation.entries.forEach { orientation ->
                            val lying = orientation == WallOrientation.HORIZONTAL
                            BoardActionButton(
                                label = stringResource(
                                    if (lying) {
                                        R.string.game_wall_horizontal
                                    } else {
                                        R.string.game_wall_vertical
                                    },
                                ),
                                // Filled is the orientation in force. A chip carried a tick to say
                                // the same thing in Material's voice; this bar has one voice.
                                filled = state.wallOrientation == orientation,
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                                glyph = { drawBarGlyph(it, lying) },
                                onClick = { onOrientation(orientation) },
                            )
                        }
                        BoardActionButton(
                            label = stringResource(R.string.game_history_pawn_label),
                            filled = false,
                            modifier = Modifier.weight(1f),
                            enabled = enabled,
                            glyph = {
                                drawPawnMark(Offset(size.width / 2f, size.height / 2f),
                                    size.minDimension, it)
                            },
                            onClick = onToggleWall,
                        )
                    }
                }

                else -> {
                    // A mark, the instruction, a rule, and the one thing there is to press. The
                    // button was outlined and grey beside grey text, which on a board screen makes
                    // the only action look like the caption next to it.
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Palette.Inset),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.size(20.dp)) {
                                drawPawnMark(
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    unit = size.minDimension,
                                    color = Palette.Gold,
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.game_move_hint),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.InkMuted,
                        )
                        Box(Modifier.width(Dimens.Hairline).height(34.dp).background(Palette.Edge))
                        WallButton(enabled = enabled, onClick = onToggleWall)
                    }
                }
            }
        }
    }
}

/** The line above the buttons, held to two lines so the bar's height stays a known quantity. */
@Composable
private fun WallHint(text: String, strong: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        color = if (strong) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Palette.InkMuted
        },
        maxLines = 2,
    )
}

/**
 * The action bar's one button shape.
 *
 * [WallButton] had this look and nothing else on the bar did, so pressing it swapped a gold-edged
 * strip for Material's chips and buttons: another border, another radius, another height, on the
 * one screen where nothing is allowed to move. Filled is the action being offered, or the state
 * already in force; outlined is the alternative to it.
 */
@Composable
private fun BoardActionButton(
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glyph: (DrawScope.(Color) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val fill = when {
        !enabled -> Palette.Inset
        filled -> Palette.Gold
        else -> Palette.Inset
    }
    val edge = when {
        !enabled -> Palette.Edge
        filled -> Palette.Gold
        else -> Palette.Gold.copy(alpha = 0.45f)
    }
    val ink = when {
        !enabled -> Palette.InkDisabled
        filled -> Palette.GoldInk
        else -> Palette.Gold
    }
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Canvas(Modifier.size(16.dp)) { glyph(ink) }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = ink,
            // One line, and it gives up its end rather than its edge. The action bar's own
            // labels are short, but the message sheet now puts whole phrases in this shape in
            // ten languages — "Bonne chance la prochaine fois" is wider than a 360 dp handset,
            // and a clipped word reads as a rendering fault where an ellipsis reads as a word
            // that did not fit.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The piece this button places, lying the way it would lie. */
private fun DrawScope.drawBarGlyph(color: Color, lying: Boolean) {
    val long = size.minDimension * 0.82f
    val thick = size.minDimension * 0.24f
    val width = if (lying) long else thick
    val height = if (lying) thick else long
    drawRoundRect(
        color = color,
        topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(thick / 2f),
    )
}

private fun DrawScope.drawTickGlyph(color: Color) {
    val unit = size.minDimension
    val stroke = unit * 0.16f
    drawLine(color, Offset(unit * 0.16f, unit * 0.54f), Offset(unit * 0.40f, unit * 0.78f),
        strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(unit * 0.40f, unit * 0.78f), Offset(unit * 0.86f, unit * 0.24f),
        strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.drawCrossGlyph(color: Color) {
    val unit = size.minDimension
    val stroke = unit * 0.16f
    drawLine(color, Offset(unit * 0.24f, unit * 0.24f), Offset(unit * 0.76f, unit * 0.76f),
        strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(unit * 0.76f, unit * 0.24f), Offset(unit * 0.24f, unit * 0.76f),
        strokeWidth = stroke, cap = StrokeCap.Round)
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
    // Gold border, seat-coloured beacon. The whole card used to be outlined in the seat colour
    // and pulsed with it, which put a two-pixel red rectangle around the most important panel
    // on the screen for half of every match. The dot is what says whose turn it is; the frame
    // is the app's, and it stays the app's.
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(Palette.Card)
            .border(
                Dimens.Hairline,
                Palette.Gold.copy(alpha = 0.45f),
                RoundedCornerShape(Dimens.RadiusMd),
            ),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .alpha(beaconAlpha)
                                .background(activeColor, RoundedCornerShape(50)),
                        )
                        // The one line on the board screen that says whose move it is, and a
                        // live region because that is a fact which changes without the player
                        // touching anything. Without it a blind player is never told the turn
                        // has come round to them — the beacon beside this text pulses, and a
                        // pulse is not something a screen reader can pass on.
                        Text(
                            turnTitle,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        when (state.mode) {
                            GameMode.VS_AI ->
                                stringResource(R.string.game_vs_ai, state.difficulty.label())
                            GameMode.LOCAL_TWO_PLAYER -> stringResource(R.string.game_local_two_player)
                            GameMode.ONLINE -> stringResource(R.string.game_online_match)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.InkMuted,
                    )
                }
                if (state.isAiThinking || state.isOnlineSyncing || (state.mode == GameMode.ONLINE && !state.isOnlineConnected)) {
                    // Gold, said out loud. It drew in gold before this line existed, because
                    // Material falls back to `primary` — the right answer arrived at by
                    // accident, which is the kind that stops being right the moment somebody
                    // changes the scheme.
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = Palette.Gold,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(Dimens.SpaceSm))
                }
                GameBarButton(
                    icon = Icons.Rounded.Info,
                    label = stringResource(R.string.game_history),
                    enabled = true,
                    onClick = onHistory,
                    size = 36.dp,
                )
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
    // A pill, because a name is a pill-shaped thing — but on [Palette.Inset] rather than
    // `surfaceVariant`, which sat 1.03:1 against the banner behind it. This is the only route to
    // the opponent's profile in the whole match and it was, measured, invisible as a chip.
    //
    // Two boxes, and the outer is the point — the same arrangement [GameBarButton] uses. A
    // Material `Surface(onClick = …)` stood here and quietly enforced the platform's 48dp touch
    // target; the chip itself measures about thirty. Drawing it by hand means the target has to
    // be said out loud, or the one way to find out who you are playing gets smaller than the
    // fingertip aiming at it.
    Box(
        modifier = modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .clickable(role = Role.Button) { onOpenProfile(opponent.userId) }
            .semantics(mergeDescendants = true) {
                contentDescription = "${opponent.username}, $openLabel"
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Palette.Inset)
                .border(Dimens.Hairline, Palette.Edge, RoundedCornerShape(50))
                .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
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

/**
 * The board screen's own top bar: home, the turn number, and the actions.
 *
 * A CenterAlignedTopAppBar stood here and was the last Material chrome in the app: a flat
 * surface, a Black-weight title and four tint-less icon buttons. The board under it is the most
 * finished thing the app draws, and the row above it looked borrowed.
 *
 * **The app's name is not in it.** It stood here in `titleLarge` with a hand-drawn rule and a
 * diamond either side of it, and none of that was information: the player is in a match, on a
 * screen they reached from a launcher icon and a home screen that both say Koridor, and the only
 * line in this column that tells them something is the turn counter underneath. Every dp of
 * vertical space here comes off the board — this file already argues that case for horizontal
 * space where the board bleeds out of the screen inset — and the name with its rules was costing
 * about thirty-four of them to repeat a word.
 *
 * So the counter is promoted into the space the name left rather than a gap being closed around
 * it. It is the title of this screen because it is the one fact that changes.
 */
@Composable
private fun GameTopBar(
    turn: Int,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameBarButton(
            icon = Icons.Rounded.Home,
            label = stringResource(R.string.game_home),
            enabled = true,
            onClick = onHome,
        )
        Text(
            text = stringResource(R.string.game_turn, turn),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = Palette.InkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        trailing()
    }
}

/**
 * A gold-edged square control, which is what every button on this screen is.
 *
 * Two boxes rather than one, and the outer is the point: [size] is the chip that gets drawn, and
 * it is forty-four here and thirty-six on the turn banner — both under the forty-eight the
 * platform asks of anything a finger has to hit. The touch box is raised to that minimum while
 * the chip keeps the size the layout was built around, which is the arrangement
 * [com.duzman46.gridbound.ui.components.home.PremiumBackArrow] already uses: nothing moves, and
 * the target stops being smaller than the fingertip aiming at it.
 */
@Composable
private fun GameBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp = 44.dp,
) {
    val tint = if (enabled) Palette.Gold else Palette.InkDisabled
    Box(
        Modifier
            .size(size.coerceAtLeast(MIN_TOUCH_TARGET))
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.Card)
                .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.45f))
        }
    }
}

/** What Android asks of anything a finger has to hit. */
private val MIN_TOUCH_TARGET: Dp = 48.dp

/**
 * The one thing there is to press on a board screen, drawn like it.
 *
 * Gold and filled, with near-black on it: white on this yellow is under three to one, and the
 * board behind it is the darkest surface in the app, so an outlined button here disappeared
 * into its own caption.
 */
@Composable
private fun WallButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val fill = if (enabled) Palette.Gold else Palette.Inset
    val ink = if (enabled) Palette.GoldInk else Palette.InkDisabled
    Row(
        Modifier
            .clip(shape)
            .background(fill)
            .border(Dimens.Hairline, if (enabled) Palette.Gold else Palette.Edge, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.SpaceLg, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Two bars on the diagonal: the piece the button places, not a generic glyph.
        Canvas(Modifier.size(18.dp)) {
            val bar = size.width * 0.62f
            val thick = size.height * 0.17f
            drawRoundRect(
                color = ink,
                topLeft = Offset(size.width * 0.06f, size.height * 0.24f),
                size = Size(bar, thick),
                cornerRadius = CornerRadius(thick / 2f),
            )
            drawRoundRect(
                color = ink.copy(alpha = 0.72f),
                topLeft = Offset(size.width * 0.32f, size.height * 0.58f),
                size = Size(bar, thick),
                cornerRadius = CornerRadius(thick / 2f),
            )
        }
        Text(
            text = stringResource(R.string.game_place_wall),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * The inset every card on this screen keeps, and the board gives back.
 */
private val SCREEN_INSET = 10.dp

/**
 * Lets the board step back out of the screen's own horizontal inset.
 *
 * The board is square, and on a handset it is always the width that limits it — the space above
 * and below it goes unused whatever happens. So every dp of side padding comes straight off the
 * grid, which is the game, while buying nothing. The cards above and below still want the inset
 * and still have it; the inset stays on the column and this takes it off one child.
 */
private fun Modifier.bleedHorizontally(inset: Dp) = layout { measurable, constraints ->
    val extra = inset.roundToPx() * 2
    val placeable = measurable.measure(constraints.offset(horizontal = extra))
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) {
        placeable.place(-inset.roundToPx(), 0)
    }
}
