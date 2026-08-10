package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import com.duzman46.gridbound.ui.components.SecondarySubmitButton
import com.duzman46.gridbound.ui.components.SubmitButton
import com.duzman46.gridbound.ui.components.home.EmptyRoomsPanel
import com.duzman46.gridbound.ui.components.home.HomeHero
import com.duzman46.gridbound.ui.components.home.HomeSceneShare
import com.duzman46.gridbound.ui.components.home.LobbyActionCard
import com.duzman46.gridbound.ui.components.home.LobbySectionHeader
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.ui.components.home.RankedQueueCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which of the lobby's two forms is open, if either. */
private enum class LobbyForm { JOIN, CREATE }

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

    // A place in the matchmaking list is a promise to be there the moment a rival is found,
    // and an app that is not on screen cannot keep it: the opponent would be dropped into a
    // match against nobody. Giving the place up here is also what makes leaving the lobby
    // enough — there is no other exit to catch.
    LifecycleStartEffect(Unit) { onStopOrDispose { viewModel.leaveQueue() } }

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
        // The same scene, at the same height, as the two screens a player passed through to get
        // here. Home, play and the lobby are one route taken three taps in a row, and a
        // photograph that changes size at each step is what makes one route read as three
        // designs. The share is the picture's own — see HomeSceneShare.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(HomeSceneShare),
        ) {
            HomeHero(Modifier.fillMaxSize())
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LobbyBackArrow(onBack)
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
                .weight(1f - HomeSceneShare),
        ) {
            when {
                !state.isConfigured ->
                    EmptyState(stringResource(R.string.error_service_unavailable))

                state.isQueued -> WaitingPanel(
                    roomCode = null,
                    message = state.message,
                    onCancel = viewModel::leaveQueue,
                )

                state.waitingSession != null -> WaitingPanel(
                    roomCode = state.waitingSession.roomCode,
                    // Nothing rendered state.message here, so an invite that failed just
                    // flipped the icon back with no explanation at all.
                    message = state.message,
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
 * The way back, on a screen whose top bar is a photograph.
 *
 * The same arrow the play screen uses, drawn again rather than shared: it is eleven lines of
 * canvas and pulling it into a component would mean a file whose only job is to hold one
 * private glyph two screens happen to agree on.
 */
@Composable
private fun LobbyBackArrow(onBack: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val label = stringResource(R.string.action_back)
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onBack,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(24.dp)
                .scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f),
        ) {
            val s = size.minDimension
            drawPath(
                Path().apply {
                    moveTo(s * 0.92f, s * 0.5f)
                    lineTo(s * 0.12f, s * 0.5f)
                    moveTo(s * 0.44f, s * 0.18f)
                    lineTo(s * 0.12f, s * 0.5f)
                    lineTo(s * 0.44f, s * 0.82f)
                },
                KoridorGold,
                style = Stroke(width = s * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
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
            refreshLabel = stringResource(R.string.action_retry),
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
                    items(state.visibleRooms, key = OnlineRoom::roomId) { room ->
                        OpenRoomRow(
                            room = room,
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
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(
                    when (form) {
                        LobbyForm.JOIN -> R.string.online_join_by_code
                        LobbyForm.CREATE -> R.string.online_create_room
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
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
    OutlinedTextField(
        value = state.roomCodeInput,
        onValueChange = viewModel::setRoomCode,
        label = { Text(stringResource(R.string.room_code_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    SubmitButton(
        text = stringResource(R.string.online_join_by_code),
        onClick = viewModel::joinByCode,
        enabled = state.canJoinByCode,
        isSubmitting = state.isBusy,
        leadingIcon = Icons.Rounded.Groups,
    )
}

@Composable
private fun CreateRoomForm(state: OnlineLobbyUiState, viewModel: OnlineLobbyViewModel) {
    val configuration = state.configuration

    OutlinedTextField(
        value = configuration.roomName,
        onValueChange = viewModel::setRoomName,
        label = { Text(stringResource(R.string.room_name_label)) },
        placeholder = { Text(stringResource(R.string.room_name_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    // The host picks a colour and with it a seat; the guest gets the other one. It arrives
    // already drawn, so the swatch showing as chosen is the seat the room will be written
    // with. It used to be drawn at write time and rendered here as blue meanwhile, which told
    // every host who left it alone that they had blue and gave them red half the time.
    SeatPicker(
        selected = configuration.hostSeat ?: PlayerId.PLAYER_ONE,
        onSelect = viewModel::setHostSeat,
    )

    Text(stringResource(R.string.room_turn_duration_label), fontWeight = FontWeight.SemiBold)
    ChipRow {
        OnlineLobbyViewModel.TURN_OPTIONS.forEach { seconds ->
            FilterChip(
                selected = configuration.timing.turnDurationSeconds == seconds,
                onClick = { viewModel.setTurnDuration(seconds) },
                label = { Text(durationLabel(seconds)) },
            )
        }
    }

    OutlinedTextField(
        value = configuration.password,
        onValueChange = viewModel::setRoomPassword,
        label = { Text(stringResource(R.string.room_password_label)) },
        placeholder = { Text(stringResource(R.string.room_password_optional)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    SubmitButton(
        text = stringResource(R.string.online_create_room),
        onClick = viewModel::createRoom,
        isSubmitting = state.isBusy,
        leadingIcon = Icons.Rounded.AddCircle,
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
    Card(
        onClick = onJoin,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayerAvatar(
                avatarId = Constants.Profile.DEFAULT_AVATAR_ID,
                name = room.hostName.ifBlank { room.roomCode },
                size = 40.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    room.roomName.ifBlank { room.roomCode },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.room_browser_host, room.hostName, room.hostRating),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    durationLabel(room.timing.turnDurationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.room_browser_players, room.playerCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (room.requiresPassword) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.room_locked),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { reporting = true }) {
                Icon(
                    Icons.Rounded.Flag,
                    contentDescription = stringResource(R.string.report_action),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
    onCancel: () -> Unit,
    friends: List<OnlineFriend> = emptyList(),
    invitedUserIds: Set<String> = emptySet(),
    onInvite: (String) -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(20.dp),
        ) {
            Column(
                Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(
                        if (roomCode == null) {
                            R.string.online_quick_match_searching
                        } else {
                            R.string.room_waiting_for_opponent
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (roomCode != null) {
                    SelectionContainer {
                        Text(
                            roomCode,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Text(
                        stringResource(R.string.room_code_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                message?.let { FormMessage(it) }

                // Inviting only makes sense here: this is the one moment there is a live
                // room code to send.
                if (roomCode != null && friends.isNotEmpty()) {
                    Text(
                        stringResource(R.string.friends_invite),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    friends.forEach { friend ->
                        val invited = friend.userId in invitedUserIds
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PlayerAvatar(friend.avatarId, friend.username, size = 32.dp)
                            Text(
                                friend.username,
                                Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (invited) {
                                Text(
                                    stringResource(R.string.friends_invite_sent),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                IconButton(onClick = { onInvite(friend.userId) }) {
                                    Icon(
                                        Icons.Rounded.PersonAdd,
                                        contentDescription = stringResource(R.string.friends_invite),
                                    )
                                }
                            }
                        }
                    }
                }

                SecondarySubmitButton(
                    text = stringResource(
                        if (roomCode == null) R.string.action_cancel else R.string.room_close,
                    ),
                    onClick = onCancel,
                )
            }
        }
    }
}

@Composable
private fun PasswordPromptDialog(
    password: String,
    onPassword: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.room_password_label)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = onPassword,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = password.isNotBlank()) {
                Text(stringResource(R.string.online_join_by_code))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ChipRow(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun durationLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.room_duration_unlimited)
    seconds < 60 -> stringResource(R.string.room_duration_seconds, seconds)
    else -> stringResource(R.string.room_duration_minutes, seconds / 60)
}
