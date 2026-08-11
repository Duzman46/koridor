package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.presentation.social.FriendsEvent
import com.duzman46.gridbound.presentation.social.FriendsUiState
import com.duzman46.gridbound.presentation.social.FriendsViewModel
import com.duzman46.gridbound.presentation.social.HostedInvite
import com.duzman46.gridbound.social.domain.Friend
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.social.domain.PlayerRequest
import com.duzman46.gridbound.social.domain.RequestKind
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold
import com.duzman46.gridbound.ui.components.FormMessage
import com.duzman46.gridbound.ui.components.home.FieldLabel
import com.duzman46.gridbound.ui.components.home.FormHeading
import com.duzman46.gridbound.ui.components.home.FriendAction
import com.duzman46.gridbound.ui.components.home.FriendCard
import com.duzman46.gridbound.ui.components.home.FriendPerk
import com.duzman46.gridbound.ui.components.home.FriendsEmptyPanel
import com.duzman46.gridbound.ui.components.home.FriendsSectionHeader
import com.duzman46.gridbound.ui.components.home.GoldSubmit
import com.duzman46.gridbound.ui.components.home.InviteCard
import com.duzman46.gridbound.ui.components.home.LinkAccountBanner
import com.duzman46.gridbound.ui.components.home.LobbyField
import com.duzman46.gridbound.ui.components.home.OutlineAction
import com.duzman46.gridbound.ui.components.home.PremiumHeader
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.ui.components.home.SheetGrip
import com.duzman46.gridbound.ui.components.home.drawAddPerson
import com.duzman46.gridbound.ui.components.home.drawBlockMark
import com.duzman46.gridbound.ui.components.home.drawCross
import com.duzman46.gridbound.ui.components.home.drawEllipsisMark
import com.duzman46.gridbound.ui.components.home.drawHash
import com.duzman46.gridbound.ui.components.home.drawInviteMark
import com.duzman46.gridbound.ui.components.home.drawPairMark
import com.duzman46.gridbound.ui.components.home.drawPersonMark
import com.duzman46.gridbound.ui.components.home.drawTick
import com.duzman46.gridbound.ui.components.home.localeUpper

@Composable
fun FriendsRoute(
    onBack: () -> Unit,
    onJoinInvite: (String) -> Unit,
    onLinkAccount: () -> Unit,
    onOpenGame: (OnlineSession) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is FriendsEvent.OpenGame) onOpenGame(event.session)
        }
    }
    FriendsScreen(
        state = state,
        onBack = onBack,
        onJoinInvite = onJoinInvite,
        onLinkAccount = onLinkAccount,
        viewModel = viewModel,
    )
}

/**
 * The friends screen.
 *
 * No tab row. The reference has three tabs and two of them were dropped, and a row with one tab
 * in it is a heading pretending to be a control. Invitations did not go with the tab — they are a
 * section of this list while there are any, gold, at the top, because an open seat expires.
 *
 * Adding somebody is one control, in the header, present whether the list is full or empty. The
 * empty panel repeats it as its one gold button, which is the whole point of an empty panel: it
 * has one thing to say and it should end in the way to do it.
 */
@Composable
private fun FriendsScreen(
    state: FriendsUiState,
    onBack: () -> Unit,
    onJoinInvite: (String) -> Unit,
    onLinkAccount: () -> Unit,
    viewModel: FriendsViewModel,
) {
    var confirming by remember { mutableStateOf<PendingConfirmation?>(null) }
    var addOpen by rememberSaveable { mutableStateOf(false) }

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

    if (addOpen) {
        AddPlayerSheet(
            state = state,
            viewModel = viewModel,
            onBlock = { confirming = it },
            onDismiss = { addOpen = false },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        PremiumHeader(
            title = stringResource(R.string.friends_title),
            subtitle = stringResource(R.string.friends_subtitle),
            onBack = onBack,
            trailing = {
                // Not offered to a guest: finding a player needs an account, so a control that
                // opens a form nobody can submit would be a promise the screen cannot keep.
                if (!state.requiresAccount) {
                    FriendAction(
                        label = stringResource(R.string.friends_add_player),
                        onClick = { addOpen = true },
                        accented = true,
                    ) { drawAddPerson(KoridorGold) }
                }
            },
        )

        // Above the list rather than inside it: a message about the action just taken has to be
        // where the player is looking, and anything inside the list is wherever scrolling left it.
        state.message?.let { message ->
            FormMessage(
                message,
                Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXs),
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                state.hostedInvite != null -> HostedInvitePanel(
                    hosted = state.hostedInvite,
                    onCancel = viewModel::cancelHostedInvite,
                )

                state.isEmpty && state.invites.isEmpty() -> EmptyFriends(
                    canAdd = !state.requiresAccount,
                    onAdd = { addOpen = true },
                )

                else -> FriendsList(
                    state = state,
                    viewModel = viewModel,
                    onJoinInvite = onJoinInvite,
                    onConfirm = { confirming = it },
                )
            }
        }

        // Only a guest, and only at the foot: it is the answer to why this screen is empty, not
        // a second thing to read before the list.
        if (state.requiresAccount) {
            LinkAccountBanner(
                title = localeUpper(stringResource(R.string.friends_link_title)),
                hint = stringResource(R.string.friends_link_hint),
                action = stringResource(R.string.friends_link_action),
                onAction = onLinkAccount,
                modifier = Modifier
                    .padding(horizontal = Dimens.ScreenPadding)
                    .navigationBarsPadding()
                    .padding(bottom = Dimens.SpaceMd),
                mark = { drawPairMark(KoridorGold) },
            )
        }
    }
}

@Composable
private fun EmptyFriends(canAdd: Boolean, onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Dimens.ScreenPadding)
            .padding(top = Dimens.SpaceSm, bottom = Dimens.SpaceLg),
    ) {
        FriendsEmptyPanel(
            title = stringResource(R.string.friends_empty_title),
            body = stringResource(R.string.friends_empty_body),
            perks = listOf(
                FriendPerk(
                    icon = PremiumIcon.PLUS,
                    title = stringResource(R.string.friends_perk_add),
                    body = stringResource(R.string.friends_perk_add_hint),
                ),
                FriendPerk(
                    icon = PremiumIcon.GAMEPAD,
                    title = stringResource(R.string.friends_perk_play),
                    body = stringResource(R.string.friends_perk_play_hint),
                ),
                FriendPerk(
                    icon = PremiumIcon.BARS,
                    title = stringResource(R.string.friends_perk_compare),
                    body = stringResource(R.string.friends_perk_compare_hint),
                ),
            ),
            action = stringResource(R.string.friends_add_player).takeIf { canAdd },
            onAction = onAdd,
        )
    }
}

@Composable
private fun FriendsList(
    state: FriendsUiState,
    viewModel: FriendsViewModel,
    onJoinInvite: (String) -> Unit,
    onConfirm: (PendingConfirmation) -> Unit,
) {
    // Resolved out here: the list content block is not a composable scope, so stringResource
    // cannot be called inside it.
    val invitesLabel = stringResource(R.string.invites_title)
    val incomingLabel = stringResource(R.string.friends_section_incoming)
    val onlineLabel = stringResource(R.string.friends_section_online)
    val offlineLabel = stringResource(R.string.friends_section_offline)
    val outgoingLabel = stringResource(R.string.friends_section_outgoing)
    val blockedLabel = stringResource(R.string.friends_section_blocked)
    val onlineStatus = stringResource(R.string.friends_online)
    val offlineStatus = stringResource(R.string.friends_offline)
    val joinLabel = stringResource(R.string.invites_join)
    val dismissLabel = stringResource(R.string.invites_dismiss)
    val acceptLabel = stringResource(R.string.friends_accept)
    val declineLabel = stringResource(R.string.friends_decline)
    val cancelLabel = stringResource(R.string.friends_cancel_request)
    val unblockLabel = stringResource(R.string.friends_unblock)

    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = Dimens.ScreenPadding,
            end = Dimens.ScreenPadding,
            top = Dimens.SpaceSm,
            bottom = Dimens.SpaceXl,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        if (state.invites.isNotEmpty()) {
            section(invitesLabel, state.invites.size)
            items(state.invites, key = FriendsRowKey::invite) { invite ->
                InviteCard(
                    text = invite.summary(),
                    joinLabel = joinLabel,
                    dismissLabel = dismissLabel,
                    onJoin = { onJoinInvite(invite.roomCode) },
                    onDismiss = { viewModel.dismissInvite(invite.fromUserId) },
                )
            }
        }

        if (state.incomingRequests.isNotEmpty()) {
            section(incomingLabel, state.incomingRequests.size)
            items(state.incomingRequests, key = FriendsRowKey::friend) { friend ->
                PlayerRow(friend, online = false, status = offlineStatus) {
                    FriendAction(acceptLabel, { viewModel.accept(friend.userId) }, accented = true) {
                        drawTick(KoridorGold)
                    }
                    FriendAction(declineLabel, { viewModel.decline(friend.userId) }) {
                        drawCross(Color(0xFF8B9098))
                    }
                }
            }
        }

        if (state.onlineFriends.isNotEmpty()) {
            section(onlineLabel, state.onlineFriends.size)
            items(state.onlineFriends, key = FriendsRowKey::friend) { friend ->
                PlayerRow(friend, online = true, status = onlineStatus) {
                    FriendMenu(friend, viewModel, onConfirm)
                }
            }
        }

        if (state.offlineFriends.isNotEmpty()) {
            section(offlineLabel, state.offlineFriends.size)
            items(state.offlineFriends, key = FriendsRowKey::friend) { friend ->
                PlayerRow(friend, online = false, status = offlineStatus) {
                    FriendMenu(friend, viewModel, onConfirm)
                }
            }
        }

        if (state.outgoingRequests.isNotEmpty()) {
            section(outgoingLabel, state.outgoingRequests.size)
            items(state.outgoingRequests, key = FriendsRowKey::friend) { friend ->
                PlayerRow(friend, online = false, status = offlineStatus) {
                    FriendAction(cancelLabel, { viewModel.cancelRequest(friend.userId) }) {
                        drawCross(Color(0xFF8B9098))
                    }
                }
            }
        }

        if (state.blocked.isNotEmpty()) {
            section(blockedLabel, state.blocked.size)
            items(state.blocked, key = FriendsRowKey::friend) { friend ->
                PlayerRow(friend, online = false, status = offlineStatus) {
                    FriendAction(unblockLabel, { viewModel.unblock(friend.userId) }) {
                        drawTick(Color(0xFF8B9098))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(title: String, count: Int) {
    item(key = "head:$title") { FriendsSectionHeader(title, "$count") }
}

@Composable
private fun PlayerRow(
    friend: Friend,
    online: Boolean,
    status: String,
    actions: @Composable RowScope.() -> Unit,
) {
    FriendCard(
        initial = friend.username.take(1).uppercase(),
        name = friend.username,
        status = status,
        online = online,
        actions = actions,
    )
}

/**
 * What there is to do about a friend: play them, and — behind one more tap — stop being friends.
 *
 * Three round controls beside a name is about a hundred and twenty pixels of a row that has three
 * hundred and sixty, and the name is what gives way. Inviting is the thing anybody does here, so
 * it keeps its own control; removing and blocking are things done once, so they live behind the
 * ellipsis where a mis-tap costs nothing.
 */
@Composable
private fun RowScope.FriendMenu(
    friend: Friend,
    viewModel: FriendsViewModel,
    onConfirm: (PendingConfirmation) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val removeLabel = stringResource(R.string.friends_remove)
    val removeMessage = stringResource(R.string.friends_remove_confirm, friend.username)
    val blockLabel = stringResource(R.string.friends_block)
    val blockMessage = stringResource(R.string.friends_block_confirm, friend.username)

    FriendAction(
        label = stringResource(R.string.friends_invite),
        onClick = { viewModel.inviteToGame(friend) },
        accented = true,
    ) { drawInviteMark(KoridorGold) }

    Box {
        FriendAction(
            label = stringResource(R.string.friends_more_actions),
            onClick = { open = true },
        ) { drawEllipsisMark(Color(0xFF8B9098)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(removeLabel) },
                onClick = {
                    open = false
                    onConfirm(
                        PendingConfirmation(removeLabel, removeMessage, removeLabel) {
                            viewModel.removeFriend(friend.userId)
                        },
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(blockLabel) },
                onClick = {
                    open = false
                    onConfirm(
                        PendingConfirmation(blockLabel, blockMessage, blockLabel) {
                            viewModel.block(friend.userId)
                        },
                    )
                },
            )
        }
    }
}

/**
 * Finding a player, as a sheet rather than a card at the top of the list.
 *
 * A search box that is on screen all the time is a search box in the way all the time, on a screen
 * whose content is the people already found. It opens from the header control, keeps the same
 * view model, and therefore keeps a half-typed name if it is closed by accident.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlayerSheet(
    state: FriendsUiState,
    viewModel: FriendsViewModel,
    onBlock: (PendingConfirmation) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(top = Dimens.SpaceSm, bottom = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        ) {
            FormHeading(
                title = stringResource(R.string.friends_add_player),
                subtitle = stringResource(R.string.friends_add_player_hint),
                mark = { drawAddPerson(KoridorGold) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                FieldLabel(stringResource(R.string.friends_search_label))
                LobbyField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.friends_search_placeholder),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Search,
                    ),
                    leading = { drawPersonMark(KoridorGold) },
                    trailing = {
                        if (state.isSearching) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                )
            }
            GoldSubmit(
                label = stringResource(R.string.friends_search_action),
                onClick = viewModel::search,
                enabled = state.query.isNotBlank(),
                busy = state.isSearching,
                mark = { drawHash(Color(0xFF1A1206)) },
            )

            state.searchResult?.let { profile ->
                SearchResult(
                    name = profile.username,
                    rating = profile.rating.toString(),
                    status = state.searchStatus,
                    busy = state.isBusy,
                    onAdd = { viewModel.sendRequest(profile.userId) },
                    onAccept = { viewModel.accept(profile.userId) },
                    onUnblock = { viewModel.unblock(profile.userId) },
                    onBlock = {
                        onBlock(it)
                        onDismiss()
                    },
                    blockUsername = profile.username,
                    blockAction = { viewModel.block(profile.userId) },
                )
            }
            state.searchMessage?.let { FormMessage(it) }
        }
    }
}

@Composable
private fun SearchResult(
    name: String,
    rating: String,
    status: FriendshipStatus,
    busy: Boolean,
    onAdd: () -> Unit,
    onAccept: () -> Unit,
    onUnblock: () -> Unit,
    onBlock: (PendingConfirmation) -> Unit,
    blockUsername: String,
    blockAction: () -> Unit,
) {
    val blockLabel = stringResource(R.string.friends_block)
    val blockMessage = stringResource(R.string.friends_block_confirm, blockUsername)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        FriendCard(
            initial = name.take(1).uppercase(),
            name = name,
            status = stringResource(R.string.friends_rating, rating),
            online = false,
        ) {
            if (status == FriendshipStatus.NONE) {
                FriendAction(blockLabel, {
                    onBlock(
                        PendingConfirmation(blockLabel, blockMessage, blockLabel, blockAction),
                    )
                }) { drawBlockMark(Color(0xFF8B9098)) }
            }
        }
        when (status) {
            FriendshipStatus.NONE -> GoldSubmit(
                label = stringResource(R.string.friends_add),
                onClick = onAdd,
                busy = busy,
                mark = { drawAddPerson(Color(0xFF1A1206)) },
            )

            FriendshipStatus.REQUEST_RECEIVED -> GoldSubmit(
                label = stringResource(R.string.friends_accept),
                onClick = onAccept,
                busy = busy,
                mark = { drawTick(Color(0xFF1A1206)) },
            )

            FriendshipStatus.REQUEST_SENT -> ResultNote(
                stringResource(R.string.friends_request_pending),
            )

            FriendshipStatus.FRIENDS -> ResultNote(stringResource(R.string.friends_already_friends))

            FriendshipStatus.BLOCKED -> OutlineAction(
                label = stringResource(R.string.friends_unblock),
                onClick = onUnblock,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ResultNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceSm),
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF8B9098),
        textAlign = TextAlign.Center,
    )
}

/**
 * The room being held open for one invited friend.
 *
 * It takes the whole screen rather than sitting as a row in the list, because that is what is
 * actually true: a room is open, somebody is expected in it, and scrolling through the rest of
 * the friend list is not something to be doing meanwhile. The code is spelled out for the same
 * reason the lobby spells it out — a notification that never arrives is answered by reading six
 * characters down the phone.
 */
@Composable
private fun HostedInvitePanel(hosted: HostedInvite, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(color = KoridorGold)
        Text(
            text = stringResource(R.string.friends_invite_waiting, hosted.friendName),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = hosted.session.roomCode,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = KoridorGold,
        )
        Text(
            text = stringResource(R.string.room_code_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8B9098),
            textAlign = TextAlign.Center,
        )
        OutlineAction(
            label = stringResource(R.string.room_close),
            onClick = onCancel,
        )
    }
}

@Composable
private fun PlayerRequest.summary(): String = stringResource(
    if (kind == RequestKind.REMATCH) R.string.requests_rematch_from else R.string.invites_from,
    fromUsername.ifBlank { roomCode },
)

private data class PendingConfirmation(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

/**
 * What identifies a row in the friends list.
 *
 * A LazyColumn keys every one of its `items` blocks into one space, and an invitation and a
 * friendship are two different rows about the same player — necessarily so, because the rules
 * charge friendship for a game invitation, so an invitation cannot exist without one. Keying
 * both on the raw user id meant the second of the two was a duplicate key, and Compose answers
 * a duplicate key by throwing out of the measure pass: one friend inviting another took the
 * whole app down, on the very screen the invitation is answered from.
 *
 * The friend rows share a space of their own safely, because the sections that hold them are a
 * partition of one status field and nobody is in two of them.
 */
internal object FriendsRowKey {
    fun invite(request: PlayerRequest): String = "invite:${request.fromUserId}"

    fun friend(friend: Friend): String = "friend:${friend.userId}"
}
