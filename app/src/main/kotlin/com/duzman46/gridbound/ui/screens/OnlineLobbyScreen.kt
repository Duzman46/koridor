package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.duzman46.gridbound.social.domain.Friend as OnlineFriend
import com.duzman46.gridbound.ui.components.EmptyState
import com.duzman46.gridbound.ui.components.FormMessage
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.LoadingState
import com.duzman46.gridbound.ui.components.PlayerAvatar
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.SectionCard
import com.duzman46.gridbound.ui.components.SecondarySubmitButton
import com.duzman46.gridbound.ui.components.SubmitButton
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

    // The room list ages out from under the player: rooms are taken within a minute or two,
    // and asking them to pull for a fresh one is the app making its own staleness their
    // problem. Tied to the screen being resumed, so a lobby left open in the background is
    // not quietly issuing a query every fifteen seconds for the rest of the day.
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        val ticker = scope.launch {
            while (true) {
                delay(Constants.Online.ROOM_BROWSER_REFRESH_MILLIS)
                viewModel.refreshOpenRooms()
            }
        }
        onPauseOrDispose { ticker.cancel() }
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

    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.online_title), onBack) }) { padding ->
        ScreenBackground {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { QuickMatchCard(state, viewModel) }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = Constants.Ui.FORM_MAX_WIDTH_DP.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LobbyActionButton(
                    text = stringResource(R.string.online_join_by_code),
                    icon = Icons.Rounded.Groups,
                    onClick = { onOpenForm(LobbyForm.JOIN) },
                    modifier = Modifier.weight(1f),
                )
                LobbyActionButton(
                    text = stringResource(R.string.online_create_room),
                    icon = Icons.Rounded.AddCircle,
                    onClick = { onOpenForm(LobbyForm.CREATE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Quick match and tapping a listed room both report failures through state.message, and
        // it belongs beside the controls that caused it rather than below an arbitrarily long
        // list where nobody would scroll to find it.
        state.message?.let { message -> item { FormMessage(message) } }

        item { OpenRoomsHeader(onRefresh = viewModel::refreshOpenRooms) }

        when {
            state.isLoadingRooms && state.openRooms.isEmpty() -> item { LoadingState() }

            state.visibleRooms.isEmpty() -> item {
                Text(
                    stringResource(R.string.room_browser_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> items(state.visibleRooms, key = OnlineRoom::roomId) { room ->
                OpenRoomRow(room, enabled = !state.isBusy) {
                    viewModel.joinListedRoom(room)
                }
            }
        }
    }
}

/**
 * One of the two ways into a form.
 *
 * Deliberately not a [SubmitButton]: nothing is submitted by opening a form, so there is no
 * busy state to show, and the label needs room to wrap — "Rejoindre avec un code" does not fit
 * on one line at half the width of a phone.
 */
@Composable
private fun LobbyActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 60.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(
            text,
            Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OpenRoomsHeader(onRefresh: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = Constants.Ui.FORM_MAX_WIDTH_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.online_browse_rooms),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_retry))
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
private fun QuickMatchCard(state: OnlineLobbyUiState, viewModel: OnlineLobbyViewModel) {
    SectionCard(
        stringResource(R.string.online_quick_match),
        Modifier.widthIn(max = Constants.Ui.FORM_MAX_WIDTH_DP.dp),
    ) {
        Text(
            stringResource(R.string.online_quick_match_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SubmitButton(
            text = stringResource(R.string.online_quick_match),
            onClick = viewModel::quickMatch,
            isSubmitting = state.isBusy,
            leadingIcon = Icons.Rounded.Bolt,
        )
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

    // The host picks a colour and with it a seat; the guest gets the other one. Random until
    // touched, which is what the null means — a room opened without thinking about it does not
    // hand the host the first move every time.
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

@Composable
private fun OpenRoomRow(room: OnlineRoom, enabled: Boolean, onJoin: () -> Unit) {
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
