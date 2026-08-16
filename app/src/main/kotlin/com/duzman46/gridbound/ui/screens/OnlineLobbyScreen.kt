package com.duzman46.gridbound.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import com.duzman46.gridbound.game.audio.LocalHapticsManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.presentation.online.OnlineLobbyEvent
import com.duzman46.gridbound.presentation.online.OnlineLobbyUiState
import com.duzman46.gridbound.presentation.online.OnlineLobbyViewModel
import com.duzman46.gridbound.social.domain.ContentReportReason
import com.duzman46.gridbound.social.domain.Friend as OnlineFriend
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold
import com.duzman46.gridbound.ui.components.EmptyState
import com.duzman46.gridbound.ui.components.FormMessage
import com.duzman46.gridbound.ui.components.LoadingState
import com.duzman46.gridbound.ui.components.PlayerAvatar
import com.duzman46.gridbound.ui.components.ReportDialog
import com.duzman46.gridbound.game.board.SeatColors
import com.duzman46.gridbound.ui.components.home.DialogCrest
import com.duzman46.gridbound.ui.components.home.DurationChip
import com.duzman46.gridbound.ui.components.home.EmptyRoomsPanel
import com.duzman46.gridbound.ui.components.home.FieldControl
import com.duzman46.gridbound.ui.components.home.FieldLabel
import com.duzman46.gridbound.ui.components.home.FormHeading
import com.duzman46.gridbound.ui.components.home.GoldSubmit
import com.duzman46.gridbound.ui.components.home.HomeHero
import com.duzman46.gridbound.ui.components.home.LobbyActionCard
import com.duzman46.gridbound.ui.components.home.LobbyField
import com.duzman46.gridbound.ui.components.home.LobbySectionHeader
import com.duzman46.gridbound.ui.components.home.OpenRoomCard
import com.duzman46.gridbound.ui.components.home.OutlineAction
import com.duzman46.gridbound.ui.components.home.PanelFootnote
import com.duzman46.gridbound.ui.components.home.PremiumBackArrow
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.ui.components.home.RankedQueueCard
import com.duzman46.gridbound.ui.components.home.SearchingPanel
import com.duzman46.gridbound.ui.components.home.SeatCard
import com.duzman46.gridbound.ui.components.home.SheetGrip
import com.duzman46.gridbound.ui.components.home.drawEye
import com.duzman46.gridbound.ui.components.home.drawHash
import com.duzman46.gridbound.ui.components.home.drawLock
import com.duzman46.gridbound.ui.components.home.drawPairMark
import com.duzman46.gridbound.ui.components.home.drawPlusMark
import com.duzman46.gridbound.ui.components.home.drawRoomCrest
import kotlinx.coroutines.delay

/** Which of the lobby's two forms is open, if either. */
private enum class LobbyForm { JOIN, CREATE }

/**
 * How much of the window the scene keeps on this screen.
 *
 * Less than the third it takes on home and play, and one number for every state this screen has.
 *
 * It was briefly two numbers — a third while the room list was empty, a sixth once rooms
 * arrived — and that was worse than either. Rooms arrive a moment after the screen does, so
 * opening the lobby meant watching it settle and then jump: the photograph shrank and every
 * control slid up under the finger already reaching for one. A layout that moves when data
 * lands is a layout that cannot be aimed at.
 *
 * So the screen commits up front to the size its fullest state needs. The lobby carries more
 * than home does — a queue card, two cards, a header, and a list whose length nobody controls —
 * and the waiting panel that replaces it is one tall column that has to reach its own cancel
 * button on the shortest phone the app supports. The scene stays, because vanishing between two
 * screens is its own kind of jolt; it just stops being what the layout is built around.
 */
private const val LOBBY_SCENE_SHARE = 0.16f

/**
 * @param inviteCode pre-fills the join field when the player arrived from an invitation, so
 *   accepting one is a single tap rather than retyping a code.
 */
@Composable
fun OnlineLobbyRoute(
    onBack: () -> Unit,
    onOpenGame: (OnlineSession) -> Unit,
    inviteCode: String = "",
    viewModel: OnlineLobbyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is OnlineLobbyEvent.OpenGame) onOpenGame(event.session)
        }
    }
    LaunchedEffect(inviteCode) {
        if (inviteCode.isNotBlank()) viewModel.setRoomCode(inviteCode)
    }
    OnlineLobbyScreen(state = state, onBack = onBack, viewModel = viewModel, inviteCode = inviteCode)
}

@Composable
private fun OnlineLobbyScreen(
    state: OnlineLobbyUiState,
    onBack: () -> Unit,
    viewModel: OnlineLobbyViewModel,
    inviteCode: String,
) {
    // An invitation arrives with the code already typed, and the field holding it lives in the
    // join form — opening straight onto it is the difference between one tap and hunting for
    // where the code went.
    var openForm by rememberSaveable(inviteCode) {
        mutableStateOf(LobbyForm.JOIN.takeIf { inviteCode.isNotBlank() })
    }

    // A form has done its job the moment the lobby has a result of its own to show: a created
    // room turns the screen into the waiting panel, and a protected room raises the password
    // dialog. Either would otherwise be buried under the sheet that caused it.
    LaunchedEffect(state.waitingSession, state.passwordPromptCode) {
        if (state.waitingSession != null || state.passwordPromptCode != null) openForm = null
    }

    // A place in the matchmaking list is a promise to be there the moment a rival is found, and
    // an app that is not on screen cannot keep it: the opponent would be dropped into a match
    // against nobody. Giving the place up here is also what makes leaving the lobby enough —
    // there is no other exit to catch.
    //
    // A hosted room is the same promise with a code attached, and it was not being kept. Every
    // way out except the button — the back gesture, the home key, swiping the app away — left a
    // room sitting in the browser for half an hour with nobody behind it. A player who tapped
    // it waited for a host who had gone. So the room goes when the screen goes, by the same
    // rule and in the same place; `waitingSession` is already null by the time a match starts,
    // so this cannot close a room that has just found its rival.
    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            viewModel.leaveQueue()
            viewModel.cancelWaiting()
        }
    }

    // The list is a live listener rather than something re-fetched every fifteen seconds: a
    // room that opens shows up at once and one that fills leaves at once. It is still tied to
    // the screen being in front of somebody, and for a stronger reason than the poll was — a
    // listener is a query the database keeps synced, so one left attached goes on working
    // through the entire match the player walked into.
    LifecycleResumeEffect(Unit) {
        viewModel.watchOpenRooms()
        onPauseOrDispose { viewModel.stopWatchingRooms() }
    }

    state.passwordPromptCode?.let {
        PasswordPromptDialog(
            password = state.joinPassword,
            onPassword = viewModel::setJoinPassword,
            onConfirm = viewModel::confirmPasswordPrompt,
            onDismiss = viewModel::dismissPasswordPrompt,
        )
    }

    openForm?.let { form ->
        LobbyFormSheet(
            form = form,
            state = state,
            viewModel = viewModel,
            onDismiss = { openForm = null },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // The same scene as the two screens a player passed through to get here, at the size
        // this one can afford — see LOBBY_SCENE_SHARE. One number, whatever the screen is doing.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(LOBBY_SCENE_SHARE),
        ) {
            HomeHero(Modifier.fillMaxSize())
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PremiumBackArrow(onBack)
                Text(
                    text = stringResource(R.string.online_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = KoridorGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Everything under the picture, and the only part of the screen that scrolls.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f - LOBBY_SCENE_SHARE),
        ) {
            when {
                !state.isConfigured ->
                    EmptyState(stringResource(R.string.error_service_unavailable))

                state.isQueued -> WaitingPanel(
                    roomCode = null,
                    message = state.message,
                    connected = state.isConnected,
                    onCancel = viewModel::leaveQueue,
                )

                state.waitingSession != null -> WaitingPanel(
                    roomCode = state.waitingSession.roomCode,
                    // Nothing rendered state.message here, so an invite that failed just
                    // flipped the icon back with no explanation at all.
                    message = state.message,
                    connected = state.isConnected,
                    onCancel = viewModel::cancelWaiting,
                    friends = state.invitableFriends,
                    invitedUserIds = state.invitedUserIds,
                    onInvite = viewModel::inviteFriend,
                )

                else -> LobbyContent(
                    state = state,
                    viewModel = viewModel,
                    onOpenForm = { openForm = it },
                )
            }
        }
    }
}

/**
 * The lobby proper: what you can do, then who is waiting to be played against.
 *
 * The room list is the page rather than a card near the bottom of it, because it is the only
 * thing here whose length nobody controls — with twenty rooms open, anything stacked below it
 * was unreachable in practice. Opening a room and joining by code are two buttons instead, and
 * the forms behind them live on their own surface.
 *
 * This is a lazy list, so it must remain the only vertical scroll on the page: nesting one
 * inside a scrolling parent gives it infinite height to measure against and it throws.
 */
@Composable
private fun LobbyContent(
    state: OnlineLobbyUiState,
    viewModel: OnlineLobbyViewModel,
    onOpenForm: (LobbyForm) -> Unit,
) {
    val gestureBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(top = Dimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        // The queue first, and loudest. Nine players in ten opened this screen to be put in
        // front of somebody, and it used to be a tonal card indistinguishable from the two
        // buttons under it.
        RankedQueueCard(
            title = stringResource(R.string.online_ranked),
            hint = stringResource(R.string.online_quick_match_hint),
            action = stringResource(R.string.online_quick_match),
            onClick = viewModel::quickMatch,
            busy = state.isBusy,
            modifier = Modifier.widthIn(max = Constants.Ui.FORM_MAX_WIDTH_DP.dp),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .widthIn(max = Constants.Ui.FORM_MAX_WIDTH_DP.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            LobbyActionCard(
                label = stringResource(R.string.online_join_by_code),
                icon = PremiumIcon.PEOPLE,
                onClick = { onOpenForm(LobbyForm.JOIN) },
            )
            LobbyActionCard(
                label = stringResource(R.string.online_create_room),
                icon = PremiumIcon.PLUS,
                onClick = { onOpenForm(LobbyForm.CREATE) },
            )
        }

        // Quick match and tapping a listed room both report failures through state.message, and
        // it belongs beside the controls that caused it rather than below an arbitrarily long
        // list where nobody would scroll to find it.
        state.message?.let { message -> FormMessage(message) }

        LobbySectionHeader(
            title = stringResource(R.string.online_browse_rooms),
            refreshLabel = stringResource(R.string.room_browser_refresh),
            onRefresh = viewModel::refreshOpenRooms,
        )

        // The page does not scroll. Everything above this point is fixed, and the rooms take
        // whatever is left and scroll inside it.
        //
        // That distinction is the whole layout. The list is the one thing here whose length
        // nobody controls — it can be empty, or it can be twenty rooms — and letting it push
        // the page meant the queue card, the card a player came for, slid off the top the
        // moment anyone looked at the list. Weighted instead: the queue never moves, the rooms
        // scroll under it, and on an empty lobby the panel simply sits in the space.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                state.isLoadingRooms && state.openRooms.isEmpty() -> LoadingState()

                state.visibleRooms.isEmpty() -> EmptyRoomsPanel(
                    title = stringResource(R.string.room_browser_empty),
                    hint = stringResource(R.string.room_browser_empty_hint),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = Dimens.SpaceLg + gestureBar),
                )

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Dimens.SpaceLg + gestureBar),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                ) {
                    itemsIndexed(state.visibleRooms, key = { _, room -> room.roomId }) { at, room ->
                        OpenRoomRow(
                            room = room,
                            index = at + 1,
                            enabled = !state.isBusy,
                            onJoin = { viewModel.joinListedRoom(room) },
                            onReport = { reason -> viewModel.reportRoom(room, reason) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The join and create forms, each on a surface of its own.
 *
 * A sheet rather than a navigation destination, because both forms hand their result straight
 * back to the lobby: creating a room turns the lobby into the waiting panel, joining a
 * protected one raises the lobby's password dialog, and a successful join is announced by a
 * single-shot event that only the lobby collects. A destination would take the lobby out of
 * the composition and that collector with it, so the form would submit into nothing. Staying
 * inside the lobby's composition also means the view model here is literally the same
 * instance — a half-typed code or a configured room survives closing the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LobbyFormSheet(
    form: LobbyForm,
    state: OnlineLobbyUiState,
    viewModel: OnlineLobbyViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // The sheet brings its own grip, drawn in gold like the rest of the screen. Material's
        // is a grey pill from a different design.
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = Dimens.SpaceMd, bottom = Dimens.SpaceXs)) {
                SheetGrip(Modifier.align(Alignment.Center))
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(top = Dimens.SpaceSm, bottom = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        ) {
            when (form) {
                LobbyForm.JOIN -> JoinByCodeForm(state, viewModel)
                LobbyForm.CREATE -> CreateRoomForm(state, viewModel)
            }
            state.message?.let { FormMessage(it) }
        }
    }
}

@Composable
private fun JoinByCodeForm(state: OnlineLobbyUiState, viewModel: OnlineLobbyViewModel) {
    FormHeading(
        title = stringResource(R.string.online_join_by_code),
        subtitle = stringResource(R.string.join_code_hint),
        mark = { drawHash() },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        FieldLabel(stringResource(R.string.room_code_label))
        LobbyField(
            value = state.roomCodeInput,
            onValueChange = viewModel::setRoomCode,
            placeholder = stringResource(R.string.room_code_placeholder),
            label = stringResource(R.string.room_code_label),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            // The sharpest of the six. A player is read a code down the phone, types it, presses
            // the tick — and until now the keyboard simply closed and the room did not open,
            // because `ImeAction.Done` draws the key and nothing else wires it to anything.
            keyboardActions = KeyboardActions(
                onDone = { if (state.canJoinByCode && !state.isBusy) viewModel.joinByCode() },
            ),
        )
    }
    PanelFootnote(stringResource(R.string.join_code_note))
    GoldSubmit(
        label = stringResource(R.string.online_join_by_code),
        onClick = viewModel::joinByCode,
        enabled = state.canJoinByCode,
        busy = state.isBusy,
        mark = { drawPairMark(Color(0xFF1A1206)) },
    )
}

@Composable
private fun CreateRoomForm(state: OnlineLobbyUiState, viewModel: OnlineLobbyViewModel) {
    val configuration = state.configuration
    var passwordShown by rememberSaveable { mutableStateOf(false) }

    FormHeading(
        title = stringResource(R.string.online_create_room),
        subtitle = stringResource(R.string.create_room_hint),
        mark = { drawRoomCrest() },
    )

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        FieldLabel(stringResource(R.string.room_name_label))
        LobbyField(
            value = configuration.roomName,
            onValueChange = viewModel::setRoomName,
            placeholder = stringResource(R.string.room_name_placeholder),
            label = stringResource(R.string.room_name_label),
            leading = { drawRoomCrest() },
        )
    }

    // The host picks a colour and with it a seat; the guest gets the other one. It arrives
    // already drawn, so the swatch showing as chosen is the seat the room will be written
    // with. It used to be drawn at write time and rendered here as blue meanwhile, which told
    // every host who left it alone that they had blue and gave them red half the time.
    val seat = configuration.hostSeat ?: PlayerId.PLAYER_ONE
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        FieldLabel(stringResource(R.string.paint_label))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
            PlayerId.entries.forEach { option ->
                val chosen = option == seat
                SeatCard(
                    swatch = SeatColors.pawn(option),
                    name = stringResource(
                        if (option == PlayerId.PLAYER_ONE) {
                            R.string.game_player_blue
                        } else {
                            R.string.game_player_red
                        },
                    ),
                    role = stringResource(
                        if (chosen) R.string.paint_yours else R.string.paint_rivals,
                    ),
                    chosen = chosen,
                    onClick = { viewModel.setHostSeat(option) },
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        FieldLabel(stringResource(R.string.room_turn_duration_label))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            OnlineLobbyViewModel.TURN_OPTIONS.forEach { seconds ->
                DurationChip(
                    label = durationLabel(seconds),
                    chosen = configuration.timing.turnDurationSeconds == seconds,
                    unlimited = seconds <= 0,
                    onClick = { viewModel.setTurnDuration(seconds) },
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        FieldLabel(
            stringResource(R.string.room_password_label),
            trailing = stringResource(R.string.room_password_optional),
        )
        LobbyField(
            value = configuration.password,
            onValueChange = viewModel::setRoomPassword,
            placeholder = stringResource(R.string.room_password_placeholder),
            label = stringResource(R.string.room_password_label),
            visualTransformation = if (passwordShown) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (!state.isBusy) viewModel.createRoom() },
            ),
            leading = { drawLock() },
            trailing = {
                // A password nobody can read back is a password typed wrong on a phone
                // keyboard, and this one is read out loud to a friend anyway.
                FieldControl(
                    label = stringResource(
                        if (passwordShown) R.string.room_password_hide else R.string.room_password_show,
                    ),
                    onClick = { passwordShown = !passwordShown },
                ) { drawEye(Color(0xFF8B9098), open = passwordShown) }
            },
        )
    }

    GoldSubmit(
        label = stringResource(R.string.online_create_room),
        onClick = viewModel::createRoom,
        busy = state.isBusy,
        mark = { drawPlusMark(Color(0xFF1A1206)) },
    )
}

/**
 * One listed room, and the two things there are to do about it.
 *
 * The report control is here because this is the only place the app shows a stranger something
 * another player typed — the room name — with no route to that player's page to report it
 * from. It is an icon rather than a button so it costs the row nothing when nobody needs it.
 */
@Composable
private fun OpenRoomRow(
    room: OnlineRoom,
    index: Int,
    enabled: Boolean,
    onJoin: () -> Unit,
    onReport: (ContentReportReason) -> Unit,
) {
    var reporting by remember { mutableStateOf(false) }
    if (reporting) {
        ReportDialog(
            subject = room.roomName.ifBlank { room.hostName.ifBlank { room.roomCode } },
            onDismiss = { reporting = false },
            onReport = { reason ->
                reporting = false
                onReport(reason)
            },
        )
    }
    OpenRoomCard(
        index = index,
        seat = SeatColors.pawn(room.hostSeat),
        title = room.roomName.ifBlank { room.roomCode },
        host = room.hostName.ifBlank { room.roomCode },
        rating = room.hostRating,
        durationLabel = durationLabel(room.timing.turnDurationSeconds),
        players = stringResource(R.string.room_browser_players, room.playerCount),
        locked = room.requiresPassword,
        lockedLabel = stringResource(R.string.room_locked),
        reportLabel = stringResource(R.string.report_action),
        enabled = enabled,
        onJoin = onJoin,
        onReport = { reporting = true },
    )
}

/**
 * The panel that replaces the lobby while there is nothing to do but wait.
 *
 * It covers both kinds of waiting, because to the player they are one thing. A hosted room has
 * a code to read out and friends to send it to; matchmaking has neither — nobody is meant to
 * join it, so [roomCode] is null and everything that only makes sense for an invitable room
 * disappears with it. Showing a code the player never asked for and cannot use read as a
 * mistake, which is why the whole block is tied to having one.
 *
 * @param roomCode null while waiting in the matchmaking list rather than in a room.
 */
@Composable
private fun WaitingPanel(
    roomCode: String?,
    message: UiText?,
    connected: Boolean,
    onCancel: () -> Unit,
    friends: List<OnlineFriend> = emptyList(),
    invitedUserIds: Set<String> = emptySet(),
    onInvite: (String) -> Unit = {},
) {
    // The clock lives here rather than in the view model. It ticks once a second, and a second
    // of state in a view model is a second of recomposition for the whole screen; here it is a
    // second of recomposition for one line of text.
    var seconds by remember(roomCode) { mutableIntStateOf(0) }
    LaunchedEffect(roomCode) {
        while (true) {
            delay(1_000)
            seconds++
        }
    }

    // Something to read while there is nothing to do. Rotating, because a single line held for
    // four minutes stops being advice and becomes wallpaper.
    val tips = listOf(
        stringResource(R.string.queue_tip_one),
        stringResource(R.string.queue_tip_two),
        stringResource(R.string.queue_tip_three),
        stringResource(R.string.queue_tip_four),
    )

    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(vertical = Dimens.SpaceMd)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        ) {
            SearchingPanel(
                badge = stringResource(
                    if (roomCode == null) R.string.online_ranked else R.string.room_yours,
                ),
                title = stringResource(
                    if (roomCode == null) {
                        R.string.online_quick_match_searching
                    } else {
                        R.string.room_waiting_for_opponent
                    },
                ),
                subtitle = stringResource(
                    if (roomCode == null) R.string.queue_searching_hint else R.string.room_code_hint,
                ),
                connectionLabel = stringResource(
                    if (connected) R.string.queue_connection_good else R.string.queue_connection_lost,
                ),
                stageLabel = stringResource(
                    if (roomCode == null) R.string.queue_stage else R.string.room_stage,
                ),
                elapsedLabel = stringResource(R.string.queue_elapsed),
                elapsedValue = "%02d:%02d".format(seconds / 60, seconds % 60),
                estimateLabel = stringResource(R.string.queue_estimate),
                estimateValue = stringResource(
                    R.string.queue_estimate_value,
                    Constants.Online.QUEUE_TYPICAL_WAIT_LOW_SECONDS,
                    Constants.Online.QUEUE_TYPICAL_WAIT_HIGH_SECONDS,
                ),
                tip = tips[(seconds / TIP_HOLD_SECONDS) % tips.size].takeIf { roomCode == null },
                connected = connected,
            ) {
                // A hosted room has something matchmaking does not: a code somebody else can
                // type. It goes inside the panel because it is the answer to "what now".
                if (roomCode != null) {
                    RoomCodePlate(roomCode)
                }
            }

            message?.let { FormMessage(it) }

            // Inviting only makes sense here: this is the one moment there is a live room
            // code to send.
            if (roomCode != null && friends.isNotEmpty()) {
                InviteList(friends, invitedUserIds, onInvite)
            }

            OutlineAction(
                label = stringResource(
                    if (roomCode == null) R.string.action_cancel else R.string.room_close,
                ),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
            PanelFootnote(
                text = stringResource(
                    if (roomCode == null) R.string.queue_cancel_note else R.string.room_close_note,
                ),
            )
        }
    }
}

/**
 * The code, big enough to read out over a phone and long-pressable to copy.
 *
 * It used to be a `SelectionContainer`, which is Android's answer to "let them copy it" and the
 * wrong one here: it asks the player to press, wait for handles, drag them to both ends of a
 * six-character word and then find "Copy" in a popup. The panel's own subtitle promised that
 * holding the code copies it. Now it does — one press, the whole code, and a buzz to say so.
 */
@Composable
private fun RoomCodePlate(roomCode: String) {
    val clipboard = LocalClipboardManager.current
    // The app's own vibrator rather than LocalHapticFeedback: the platform drops that one
    // wherever the phone-wide touch-feedback switch is off. See HapticsManager.
    val haptics = LocalHapticsManager.current
    val context = LocalContext.current
    val copied = stringResource(R.string.room_code_copied)
    val shape = RoundedCornerShape(16.dp)
    Text(
        text = roomCode,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KoridorGold.copy(alpha = 0.08f))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClickLabel = copied,
                onLongClick = {
                    clipboard.setText(AnnotatedString(roomCode))
                    haptics.tick()
                    // Android 13 and later show their own confirmation for anything put on the
                    // clipboard, and a second one on top of it is the app talking over the
                    // system. Older versions say nothing at all, so we do.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    }
                },
                onClick = {
                    clipboard.setText(AnnotatedString(roomCode))
                    haptics.tick()
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            .padding(vertical = Dimens.SpaceMd),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Black,
        letterSpacing = 4.sp,
        color = KoridorGold,
        textAlign = TextAlign.Center,
    )
}

/** Friends who could be sent the code, and whether they already have been. */
@Composable
private fun InviteList(
    friends: List<OnlineFriend>,
    invitedUserIds: Set<String>,
    onInvite: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        FieldLabel(stringResource(R.string.friends_invite))
        friends.forEach { friend ->
            val invited = friend.userId in invitedUserIds
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            ) {
                PlayerAvatar(friend.avatarId, friend.username, size = 32.dp)
                Text(
                    friend.username,
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (invited) {
                    Text(
                        stringResource(R.string.friends_invite_sent),
                        style = MaterialTheme.typography.bodySmall,
                        color = KoridorGold,
                    )
                } else {
                    FieldControl(
                        label = stringResource(R.string.friends_invite),
                        onClick = { onInvite(friend.userId) },
                    ) { drawPlusMark(KoridorGold) }
                }
            }
        }
    }
}

/** How long one tip stays up before the next one takes its place. */
private const val TIP_HOLD_SECONDS = 8

@Composable
private fun PasswordPromptDialog(
    password: String,
    onPassword: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    LobbyDialog(
        onDismiss = onDismiss,
        crest = { drawLock() },
        title = stringResource(R.string.room_password_label),
        subtitle = stringResource(R.string.room_password_prompt_hint),
    ) {
        LobbyField(
            value = password,
            onValueChange = onPassword,
            placeholder = stringResource(R.string.room_password_label),
            label = stringResource(R.string.room_password_label),
            visualTransformation = if (shown) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (password.isNotBlank()) onConfirm() }),
            leading = { drawLock() },
            trailing = {
                FieldControl(
                    label = stringResource(
                        if (shown) R.string.room_password_hide else R.string.room_password_show,
                    ),
                    onClick = { shown = !shown },
                ) { drawEye(Color(0xFF8B9098), open = shown) }
            },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            OutlineAction(
                label = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            GoldSubmit(
                label = stringResource(R.string.room_join_action),
                onClick = onConfirm,
                enabled = password.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The shape every dialog on this screen takes: a crest hanging off the top edge, a title, a
 * sentence, and whatever the dialog is actually for.
 *
 * Not an `AlertDialog`. Material's is a rounded rectangle with a title row and a button row, and
 * every part of it — the tonal surface, the text button pair, the corner radius — belongs to a
 * different design than a gold hairline on near-black. What is kept is the only part that
 * matters: it is a `Dialog`, so it dims what is behind it and takes the back button.
 */
@Composable
private fun LobbyDialog(
    onDismiss: () -> Unit,
    crest: DrawScope.() -> Unit,
    title: String,
    subtitle: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(26.dp)
        // The crest sits half outside the panel, so the panel starts below its middle and the
        // Box lets it hang over the top edge rather than pushing the title down.
        Box(Modifier.padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(BorderStroke(1.dp, KoridorGold.copy(alpha = 0.35f)), shape)
                    .padding(Dimens.SpaceLg)
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8B9098),
                        textAlign = TextAlign.Center,
                    )
                }
                content()
            }
            DialogCrest(crest, Modifier.align(Alignment.TopCenter).offset(y = (-32).dp))
        }
    }
}

@Composable
private fun durationLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.room_duration_unlimited)
    seconds < 60 -> stringResource(R.string.room_duration_seconds, seconds)
    else -> stringResource(R.string.room_duration_minutes, seconds / 60)
}
