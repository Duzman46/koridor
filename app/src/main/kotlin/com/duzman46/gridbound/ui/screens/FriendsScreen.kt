package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.presentation.social.FriendsUiState
import com.duzman46.gridbound.presentation.social.FriendsViewModel
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.social.domain.GameInvite
import com.duzman46.gridbound.ui.components.EmptyState
import com.duzman46.gridbound.ui.components.FormMessage
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.PlayerAvatar
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.SubmitButton

@Composable
fun FriendsRoute(
    onBack: () -> Unit,
    onJoinInvite: (String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FriendsScreen(
        state = state,
        onBack = onBack,
        onJoinInvite = onJoinInvite,
        viewModel = viewModel,
    )
}

@Composable
private fun FriendsScreen(
    state: FriendsUiState,
    onBack: () -> Unit,
    onJoinInvite: (String) -> Unit,
    viewModel: FriendsViewModel,
) {
    var confirming by remember { mutableStateOf<PendingConfirmation?>(null) }

    confirming?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(pending.title) },
            text = { Text(pending.message) },
            confirmButton = {
                TextButton(onClick = {
                    pending.onConfirm()
                    confirming = null
                }) { Text(pending.confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.friends_title), onBack) }) { padding ->
        ScreenBackground {
            if (state.requiresAccount) {
                EmptyState(
                    stringResource(R.string.friends_requires_account),
                    modifier = Modifier.padding(padding),
                )
                return@ScreenBackground
            }
            // Section headings are resolved here: the LazyColumn content block is not a
            // composable scope, so stringResource cannot be called inside it.
            val invitesLabel = stringResource(R.string.invites_title)
            val incomingLabel = stringResource(R.string.friends_section_incoming)
            val onlineLabel = stringResource(R.string.friends_section_online)
            val offlineLabel = stringResource(R.string.friends_section_offline)
            val outgoingLabel = stringResource(R.string.friends_section_outgoing)
            val blockedLabel = stringResource(R.string.friends_section_blocked)
            val emptyLabel = stringResource(R.string.friends_empty)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { SearchCard(state, viewModel, onBlock = { confirming = it }) }

                if (state.invites.isNotEmpty()) {
                    section(invitesLabel)
                    items(state.invites, key = GameInvite::inviteId) { invite ->
                        InviteRow(
                            invite = invite,
                            onJoin = { onJoinInvite(invite.roomCode) },
                            onDismiss = { viewModel.dismissInvite(invite.inviteId) },
                        )
                    }
                }

                if (state.incomingRequests.isNotEmpty()) {
                    section(incomingLabel)
                    items(state.incomingRequests, key = Friend::userId) { friend ->
                        FriendRow(friend, online = false) {
                            ActionButton(stringResource(R.string.friends_accept), Icons.Rounded.Check) {
                                viewModel.accept(friend.userId)
                            }
                            ActionButton(stringResource(R.string.friends_decline), Icons.Rounded.Close) {
                                viewModel.decline(friend.userId)
                            }
                        }
                    }
                }

                if (state.onlineFriends.isNotEmpty()) {
                    section(onlineLabel)
                    items(state.onlineFriends, key = Friend::userId) { friend ->
                        FriendRow(friend, online = true) {
                            FriendMenu(friend, viewModel) { confirming = it }
                        }
                    }
                }

                if (state.offlineFriends.isNotEmpty()) {
                    section(offlineLabel)
                    items(state.offlineFriends, key = Friend::userId) { friend ->
                        FriendRow(friend, online = false) {
                            FriendMenu(friend, viewModel) { confirming = it }
                        }
                    }
                }

                if (state.outgoingRequests.isNotEmpty()) {
                    section(outgoingLabel)
                    items(state.outgoingRequests, key = Friend::userId) { friend ->
                        FriendRow(friend, online = false) {
                            ActionButton(
                                stringResource(R.string.friends_cancel_request),
                                Icons.Rounded.Close,
                            ) { viewModel.cancelRequest(friend.userId) }
                        }
                    }
                }

                if (state.blocked.isNotEmpty()) {
                    section(blockedLabel)
                    items(state.blocked, key = Friend::userId) { friend ->
                        FriendRow(friend, online = false) {
                            ActionButton(stringResource(R.string.friends_unblock), Icons.Rounded.Check) {
                                viewModel.unblock(friend.userId)
                            }
                        }
                    }
                }

                if (state.isEmpty && state.invites.isEmpty()) {
                    item {
                        Text(
                            emptyLabel,
                            Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.message?.let { message ->
                    item { FormMessage(message, isError = false) }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String) {
    item {
        Text(
            title,
            Modifier.padding(top = 10.dp, bottom = 2.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SearchCard(
    state: FriendsUiState,
    viewModel: FriendsViewModel,
    onBlock: (PendingConfirmation) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.friends_search_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Search,
                ),
                trailingIcon = {
                    IconButton(onClick = viewModel::search, enabled = !state.isSearching) {
                        if (state.isSearching) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.friends_search_label),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            state.searchResult?.let { profile ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PlayerAvatar(profile.avatarId, profile.username, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(profile.username, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            profile.rating.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val blockLabel = stringResource(R.string.friends_block)
                val blockMessage = stringResource(R.string.friends_block_confirm, profile.username)
                when (state.searchStatus) {
                    FriendshipStatus.NONE -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SubmitButton(
                            text = stringResource(R.string.friends_add),
                            onClick = { viewModel.sendRequest(profile.userId) },
                            isSubmitting = state.isBusy,
                            leadingIcon = Icons.Rounded.PersonAdd,
                            modifier = Modifier.weight(1f),
                        )
                        ActionButton(blockLabel, Icons.Rounded.Block) {
                            onBlock(
                                PendingConfirmation(
                                    title = blockLabel,
                                    message = blockMessage,
                                    confirmLabel = blockLabel,
                                    onConfirm = { viewModel.block(profile.userId) },
                                ),
                            )
                        }
                    }

                    FriendshipStatus.REQUEST_RECEIVED -> ActionButton(
                        stringResource(R.string.friends_accept),
                        Icons.Rounded.Check,
                    ) { viewModel.accept(profile.userId) }

                    FriendshipStatus.REQUEST_SENT -> Text(
                        stringResource(R.string.friends_section_outgoing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FriendshipStatus.FRIENDS -> Text(
                        stringResource(R.string.friends_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    FriendshipStatus.BLOCKED -> ActionButton(
                        stringResource(R.string.friends_unblock),
                        Icons.Rounded.Check,
                    ) { viewModel.unblock(profile.userId) }
                }
            }
            state.searchMessage?.let { FormMessage(it) }
        }
    }
}

@Composable
private fun FriendMenu(
    friend: Friend,
    viewModel: FriendsViewModel,
    onConfirm: (PendingConfirmation) -> Unit,
) {
    val removeLabel = stringResource(R.string.friends_remove)
    val removeMessage = stringResource(R.string.friends_remove_confirm, friend.username)
    val blockLabel = stringResource(R.string.friends_block)
    val blockMessage = stringResource(R.string.friends_block_confirm, friend.username)
    ActionButton(removeLabel, Icons.Rounded.Close) {
        onConfirm(
            PendingConfirmation(removeLabel, removeMessage, removeLabel) {
                viewModel.removeFriend(friend.userId)
            },
        )
    }
    ActionButton(blockLabel, Icons.Rounded.Block) {
        onConfirm(
            PendingConfirmation(blockLabel, blockMessage, blockLabel) {
                viewModel.block(friend.userId)
            },
        )
    }
}

@Composable
private fun FriendRow(
    friend: Friend,
    online: Boolean,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                PlayerAvatar(friend.avatarId, friend.username, size = 40.dp)
                if (online) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    friend.username,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        if (online) R.string.friends_online else R.string.friends_offline,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (online) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            actions()
        }
    }
}

@Composable
private fun InviteRow(invite: GameInvite, onJoin: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(
                    R.string.invites_from,
                    invite.fromUsername.ifBlank { invite.roomCode },
                ),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            ActionButton(stringResource(R.string.invites_join), Icons.Rounded.Check, onJoin)
            ActionButton(stringResource(R.string.invites_dismiss), Icons.Rounded.Close, onDismiss)
        }
    }
}

/**
 * Icon-only action with the label carried as its accessibility description, so the rows stay
 * compact on a small screen without becoming unreadable to a screen reader.
 */
@Composable
private fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class PendingConfirmation(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)
