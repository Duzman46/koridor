package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.online.model.RoomBrowserFilter
import com.duzman46.gridbound.online.model.RoomVisibility
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

private const val TAB_PLAY = 0
private const val TAB_CREATE = 1
private const val TAB_BROWSE = 2

/**
 * Which part of the lobby the caller wants. The main menu offers "create a room" and "join a
 * room" as separate actions, and both land here — this decides where.
 */
enum class LobbyTab { PLAY, CREATE, BROWSE }

private fun LobbyTab.index(): Int = when (this) {
    LobbyTab.PLAY -> TAB_PLAY
    LobbyTab.CREATE -> TAB_CREATE
    LobbyTab.BROWSE -> TAB_BROWSE
}

/**
 * @param inviteCode pre-fills the join field when the player arrived from an invitation, so
 *   accepting one is a single tap rather than retyping a code.
 * @param initialTab which tab opens first.
 */
@Composable
fun OnlineLobbyRoute(
    onBack: () -> Unit,
    onOpenGame: (OnlineSession) -> Unit,
    inviteCode: String = "",
    initialTab: LobbyTab = LobbyTab.PLAY,
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
    OnlineLobbyScreen(state = state, onBack = onBack, viewModel = viewModel, initialTab = initialTab)
}

@Composable
private fun OnlineLobbyScreen(
    state: OnlineLobbyUiState,
    onBack: () -> Unit,
    viewModel: OnlineLobbyViewModel,
    initialTab: LobbyTab,
) {
    var tab by remember(initialTab) { mutableIntStateOf(initialTab.index()) }

    state.passwordPromptRoom?.let {
        PasswordPromptDialog(
            password = state.joinPassword,
            onPassword = viewModel::setJoinPassword,
            onConfirm = viewModel::confirmPasswordPrompt,
            onDismiss = viewModel::dismissPasswordPrompt,
        )
    }

    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.online_title), onBack) }) { padding ->
        ScreenBackground {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (!state.isConfigured) {
                    EmptyState(stringResource(R.string.error_service_unavailable))
                    return@ScreenBackground
                }
                state.waitingSession?.let { session ->
                    WaitingRoomPanel(
                        roomCode = session.roomCode,
                        friends = state.invitableFriends,
                        invitedUserIds = state.invitedUserIds,
                        onInvite = viewModel::inviteFriend,
                        onCancel = viewModel::cancelWaiting,
                    )
                    return@ScreenBackground
                }

                TabRow(selectedTabIndex = tab) {
                    Tab(tab == TAB_PLAY, { tab = TAB_PLAY }, text = { Text(stringResource(R.string.online_quick_match)) })
                    Tab(tab == TAB_CREATE, { tab = TAB_CREATE }, text = { Text(stringResource(R.string.online_create_room)) })
                    Tab(tab == TAB_BROWSE, { tab = TAB_BROWSE }, text = { Text(stringResource(R.string.online_browse_rooms)) })
                }
                when (tab) {
                    TAB_CREATE -> CreateRoomTab(state, viewModel)
                    TAB_BROWSE -> BrowseRoomsTab(state, viewModel)
                    else -> PlayTab(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun PlayTab(state: OnlineLobbyUiState, viewModel: OnlineLobbyViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.resumableSession?.let {
            LobbyCard(stringResource(R.string.online_rejoin_match)) {
                Text(
                    stringResource(R.string.online_rejoin_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SubmitButton(
                    text = stringResource(R.string.online_rejoin_match),
                    onClick = viewModel::resumeMatch,
                    leadingIcon = Icons.Rounded.PlayArrow,
                )
            }
        }

        LobbyCard(stringResource(R.string.online_quick_match)) {
            Text(
                stringResource(
                    if (state.isSearching) {
                        R.string.online_quick_match_searching
                    } else {
                        R.string.room_ranked_description
                    },
                ),
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

        LobbyCard(stringResource(R.string.online_join_by_code)) {
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

        state.message?.let { FormMessage(it) }
    }
}

@Composable
private fun CreateRoomTab(state: OnlineLobbyUiState, viewModel: OnlineLobbyViewModel) {
    val configuration = state.configuration
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LobbyCard(stringResource(R.string.online_create_room)) {
            OutlinedTextField(
                value = configuration.roomName,
                onValueChange = viewModel::setRoomName,
                label = { Text(stringResource(R.string.room_name_label)) },
                placeholder = { Text(stringResource(R.string.room_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.room_visibility_label), fontWeight = FontWeight.SemiBold)
            ChipRow {
                RoomVisibility.entries.forEach { visibility ->
                    FilterChip(
                        selected = configuration.visibility == visibility,
                        onClick = { viewModel.setVisibility(visibility) },
                        label = { Text(visibility.label()) },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.room_ranked_label), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(
                            if (state.canPlayRanked) {
                                R.string.room_ranked_description
                            } else {
                                R.string.room_error_ranked_requires_account
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = configuration.ranked,
                    onCheckedChange = viewModel::setRanked,
                    enabled = state.canPlayRanked,
                )
            }

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

            Text(stringResource(R.string.room_total_duration_label), fontWeight = FontWeight.SemiBold)
            ChipRow {
                OnlineLobbyViewModel.TOTAL_OPTIONS.forEach { seconds ->
                    FilterChip(
                        selected = configuration.timing.totalDurationSeconds == seconds,
                        onClick = { viewModel.setTotalDuration(seconds) },
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
        state.message?.let { FormMessage(it) }
    }
}

@Composable
private fun BrowseRoomsTab(state: OnlineLobbyUiState, viewModel: OnlineLobbyViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChipRow(Modifier.weight(1f)) {
                FilterChip(
                    selected = state.filter.ranked == null && !state.filter.friendsOnly,
                    onClick = { viewModel.setFilter(RoomBrowserFilter()) },
                    label = { Text(stringResource(R.string.room_browser_filter_all)) },
                )
                FilterChip(
                    selected = state.filter.ranked == true,
                    onClick = { viewModel.setFilter(state.filter.copy(ranked = true, friendsOnly = false)) },
                    label = { Text(stringResource(R.string.room_browser_filter_ranked)) },
                )
                FilterChip(
                    selected = state.filter.ranked == false,
                    onClick = { viewModel.setFilter(state.filter.copy(ranked = false, friendsOnly = false)) },
                    label = { Text(stringResource(R.string.room_browser_filter_casual)) },
                )
                FilterChip(
                    selected = state.filter.maxTurnSeconds != null,
                    onClick = {
                        val current = state.filter.maxTurnSeconds
                        viewModel.setFilter(
                            state.filter.copy(maxTurnSeconds = if (current == null) 60 else null),
                        )
                    },
                    label = { Text(stringResource(R.string.room_browser_filter_fast)) },
                )
            }
            IconButton(onClick = viewModel::refreshOpenRooms) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_retry))
            }
        }

        when {
            state.isLoadingRooms && state.openRooms.isEmpty() -> LoadingState()
            state.visibleRooms.isEmpty() -> EmptyState(stringResource(R.string.room_browser_empty))
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.visibleRooms, key = OnlineRoom::roomId) { room ->
                    OpenRoomRow(room, enabled = !state.isBusy) { viewModel.joinListedRoom(room) }
                }
            }
        }
        state.message?.let { FormMessage(it, isError = true) }
    }
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
                    stringResource(
                        if (room.ranked) {
                            R.string.room_browser_filter_ranked
                        } else {
                            R.string.room_browser_filter_casual
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
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

@Composable
private fun WaitingRoomPanel(
    roomCode: String,
    friends: List<OnlineFriend>,
    invitedUserIds: Set<String>,
    onInvite: (String) -> Unit,
    onCancel: () -> Unit,
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
                    stringResource(R.string.room_waiting_for_opponent),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
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

                // Inviting only makes sense here: this is the one moment there is a live
                // room code to send.
                if (friends.isNotEmpty()) {
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
                    text = stringResource(R.string.room_close),
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
private fun LobbyCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    SectionCard(title, Modifier.widthIn(max = Constants.Ui.FORM_MAX_WIDTH_DP.dp), content)
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
private fun RoomVisibility.label(): String = stringResource(
    when (this) {
        RoomVisibility.PUBLIC -> R.string.room_visibility_public
        RoomVisibility.PRIVATE -> R.string.room_visibility_private
        RoomVisibility.FRIENDS -> R.string.room_visibility_friends
    },
)

@Composable
private fun durationLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.room_duration_unlimited)
    seconds < 60 -> stringResource(R.string.room_duration_seconds, seconds)
    else -> stringResource(R.string.room_duration_minutes, seconds / 60)
}
