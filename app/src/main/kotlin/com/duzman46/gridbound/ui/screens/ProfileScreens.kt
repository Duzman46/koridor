package com.duzman46.gridbound.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.presentation.profile.PlayerProfileUiState
import com.duzman46.gridbound.presentation.profile.PlayerProfileViewModel
import com.duzman46.gridbound.presentation.profile.ProfileEditState
import com.duzman46.gridbound.presentation.profile.RecentGamesState
import com.duzman46.gridbound.presentation.profile.RecentGamesViewModel
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.social.domain.ContentReportReason
import com.duzman46.gridbound.social.domain.FriendshipStatus
import com.duzman46.gridbound.ui.components.AvatarPalette
import com.duzman46.gridbound.ui.components.ErrorState
import com.duzman46.gridbound.ui.components.FormMessage
import com.duzman46.gridbound.ui.components.GateTopBar
import com.duzman46.gridbound.ui.components.RecentGamesCard
import com.duzman46.gridbound.ui.components.ReportDialog
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.EmptyState
import com.duzman46.gridbound.ui.components.SecondarySubmitButton
import com.duzman46.gridbound.ui.components.LoadingState
import com.duzman46.gridbound.ui.components.PlayerAvatar
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.SubmitButton
import java.text.DateFormat
import java.util.Date

/**
 * The gate every real account passes through once, before it ever reaches the game.
 *
 * It has no way out, and that is the whole design: there is no back arrow, and the system
 * back gesture is swallowed. A name is not a preference to be deferred — it is what every
 * other player will call you, and every screen past this one shows it to somebody. A skip,
 * a back arrow or a dismissable dialog would each hand out an unnamed account, and an
 * unnamed account is one the player has to be chased for later.
 */
@Composable
fun UsernameScreen(
    state: ProfileEditState,
    onUsername: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    BackHandler { }
    Scaffold(topBar = { GateTopBar(stringResource(R.string.username_title)) }) { padding ->
        ScreenBackground {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        stringResource(R.string.username_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    UsernameField(state, onUsername)
                    SubmitButton(
                        text = stringResource(R.string.action_continue),
                        onClick = onSubmit,
                        enabled = state.username.value.isNotBlank(),
                        isSubmitting = state.isSubmitting,
                    )
                    state.error?.let { FormMessage(it) }
                }
            }
        }
    }
}

/**
 * The player's own page: who they are, their record, and the screens that belong to them.
 *
 * Built to a hard budget — all of it has to be readable on one phone screen. That is why the
 * avatar sits beside the name instead of above it and why every statistic shares one card:
 * portrait-style headers and a stack of separate cards spend most of their height on padding.
 *
 * The scroll is a safety net for accessibility font scales, not part of the intended
 * experience. At default scale everything down to the last button is short enough that it
 * never moves — which is why the recent games sit *below* those buttons rather than under the
 * record where they read most naturally. Above them, three more rows would push the ways out
 * of this screen off the bottom of it; below, the section heading peeks over the edge and is
 * the one thing here worth scrolling for.
 */
@Composable
fun ProfileScreen(
    profile: UserProfile?,
    hasAccount: Boolean,
    recentGames: RecentGamesState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAccount: () -> Unit,
    onLeaderboard: () -> Unit,
    onFriends: () -> Unit,
    onOpenPlayer: (String) -> Unit,
) {
    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.profile_title), onBack) }) { padding ->
        ScreenBackground {
            if (profile == null) {
                // A null profile is only a loading state when there is an account behind it.
                // A guest playing locally has none and never will, so spinning forever here
                // was a dead end on the one screen that is supposed to explain who you are.
                if (hasAccount) {
                    LoadingState(Modifier.padding(padding))
                } else {
                    EmptyState(
                        message = stringResource(R.string.auth_guest_explainer),
                        modifier = Modifier.padding(padding),
                        title = stringResource(R.string.profile_title),
                        // The explainer ends on "link an account". Without a way to do it
                        // this screen told a guest what to do and then gave them nowhere to
                        // do it — the one screen they would go to in order to do it.
                        action = {
                            Button(onClick = onAccount) {
                                Text(stringResource(R.string.auth_link_account))
                            }
                        },
                    )
                }
                return@ScreenBackground
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileIdentity(profile)
                    ProfileStatsCard(profile)
                    SubmitButton(
                        text = stringResource(R.string.profile_edit_title),
                        onClick = onEdit,
                    )
                    // The leaderboard and the friend list used to be buttons on the home screen.
                    // They belong to the player, so they hang off the player's own screen.
                    SecondarySubmitButton(
                        text = stringResource(R.string.leaderboard_title),
                        onClick = onLeaderboard,
                    )
                    SecondarySubmitButton(
                        text = stringResource(R.string.friends_title),
                        onClick = onFriends,
                    )
                    SecondarySubmitButton(
                        text = stringResource(R.string.account_title),
                        onClick = onAccount,
                    )
                    RecentGamesCard(recentGames, onOpenPlayer)
                }
            }
        }
    }
}

/**
 * Another player's page: the same record, none of the controls that belong to its owner.
 *
 * Deliberately a second screen rather than [ProfileScreen] behind an `isOwner` flag. Only the
 * record is common; everything below it is the opposite of the other screen's — one offers the
 * ways into your own things, the other offers a relationship — so one screen would have been a
 * shared header over two bodies of conditionals. Splitting them also turns the rule that
 * matters into a structural fact: there is no branch here that can draw "Edit profile", so no
 * later change to a flag can put the owner's controls on a stranger's page. [ProfileIdentity]
 * and [ProfileStatsCard] are the part that is genuinely shared, and they are shared.
 */
@Composable
fun PlayerProfileRoute(
    onBack: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    viewModel: PlayerProfileViewModel = hiltViewModel(),
    recentGamesViewModel: RecentGamesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recentGames by recentGamesViewModel.state.collectAsStateWithLifecycle()
    PlayerProfileScreen(
        state = state,
        recentGames = recentGames,
        onBack = onBack,
        onOpenPlayer = onOpenPlayer,
        onSendRequest = viewModel::sendRequest,
        onAccept = viewModel::accept,
        onUnblock = viewModel::unblock,
        onBlock = viewModel::block,
        onReport = viewModel::report,
        onRetry = viewModel::retry,
    )
}

@Composable
private fun PlayerProfileScreen(
    state: PlayerProfileUiState,
    recentGames: RecentGamesState,
    onBack: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    onSendRequest: () -> Unit,
    onAccept: () -> Unit,
    onUnblock: () -> Unit,
    onBlock: () -> Unit,
    onReport: (ContentReportReason) -> Unit,
    onRetry: () -> Unit,
) {
    // The player's own name once it is known: a bar reading "Player profile" above a page with
    // their name on it says nothing the page does not already say.
    val title = state.profile?.username ?: stringResource(R.string.profile_player_title)
    Scaffold(topBar = { ScreenTopBar(title, onBack) }) { padding ->
        ScreenBackground {
            val profile = state.profile
            if (profile == null) {
                if (state.isLoading) {
                    LoadingState(Modifier.padding(padding))
                } else {
                    ErrorState(
                        message = (state.error ?: AppError.UNKNOWN.message).asString(),
                        onRetry = onRetry,
                        modifier = Modifier.padding(padding),
                    )
                }
                return@ScreenBackground
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileIdentity(profile)
                    ProfileStatsCard(profile)
                    FriendAction(state, onSendRequest, onAccept, onUnblock)
                    SafetyActions(state, onBlock, onReport)
                    RecentGamesCard(recentGames, onOpenPlayer)
                }
            }
        }
    }
}

/**
 * The one thing a player can do about someone else, saying what is actually true rather than
 * always offering to send.
 *
 * Absent on your own page and for a guest: a friendship needs two durable identities, and a
 * button that could only ever fail is worse than the sentence explaining why it is missing.
 * Declining a request and removing or blocking a friend stay on the friends screen — this page
 * moves a relationship forward, it does not administer it.
 */
@Composable
private fun FriendAction(
    state: PlayerProfileUiState,
    onSendRequest: () -> Unit,
    onAccept: () -> Unit,
    onUnblock: () -> Unit,
) {
    if (state.isSelf) return
    if (state.requiresAccount) {
        RelationshipNote(stringResource(R.string.friends_requires_account))
        return
    }
    when (state.status) {
        FriendshipStatus.NONE -> SubmitButton(
            text = stringResource(R.string.friends_add),
            onClick = onSendRequest,
            isSubmitting = state.isBusy,
            leadingIcon = Icons.Rounded.PersonAdd,
        )

        FriendshipStatus.REQUEST_RECEIVED -> SubmitButton(
            text = stringResource(R.string.friends_accept),
            onClick = onAccept,
            isSubmitting = state.isBusy,
            leadingIcon = Icons.Rounded.Check,
        )

        FriendshipStatus.REQUEST_SENT ->
            RelationshipNote(stringResource(R.string.friends_request_pending))

        FriendshipStatus.FRIENDS -> RelationshipNote(
            text = stringResource(R.string.friends_already_friends),
            color = MaterialTheme.colorScheme.primary,
        )

        FriendshipStatus.BLOCKED -> SecondarySubmitButton(
            text = stringResource(R.string.friends_unblock),
            onClick = onUnblock,
            isSubmitting = state.isBusy,
        )
    }
    state.message?.let { FormMessage(it) }
}

/**
 * The two things a player can do about content somebody else typed.
 *
 * They live here because this is the page every route to another player ends on — the
 * opponent chip on the board, a leaderboard row, a line in a recent-games list — and until
 * now the page offered exactly one control, "Add friend", to somebody who had arrived at it
 * because of a name they wanted to do something about. Blocking existed only behind retyping
 * the exact username into the friends search, and reporting did not exist at all.
 *
 * Reporting is offered to a guest as well, because seeing something does not require an
 * account and Play's obligation is not to signed-in players only. Blocking is not: it is a
 * relationship, and a guest has no durable identity to hang one on.
 */
@Composable
private fun SafetyActions(
    state: PlayerProfileUiState,
    onBlock: () -> Unit,
    onReport: (ContentReportReason) -> Unit,
) {
    if (state.isSelf) return
    var reporting by remember { mutableStateOf(false) }
    var confirmingBlock by remember { mutableStateOf(false) }
    val name = state.profile?.username.orEmpty()

    if (reporting) {
        ReportDialog(
            subject = name,
            onDismiss = { reporting = false },
            onReport = { reason ->
                reporting = false
                onReport(reason)
            },
        )
    }
    if (confirmingBlock) {
        AlertDialog(
            onDismissRequest = { confirmingBlock = false },
            title = { Text(stringResource(R.string.friends_block)) },
            text = { Text(stringResource(R.string.friends_block_confirm, name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingBlock = false
                    onBlock()
                }) { Text(stringResource(R.string.friends_block)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingBlock = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SecondarySubmitButton(
            text = stringResource(R.string.report_action),
            onClick = { reporting = true },
            modifier = Modifier.weight(1f),
        )
        if (!state.requiresAccount && state.status != FriendshipStatus.BLOCKED) {
            SecondarySubmitButton(
                text = stringResource(R.string.friends_block),
                onClick = { confirmingBlock = true },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A relationship with nothing to press says so in words, never as a disabled button. */
@Composable
private fun RelationshipNote(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Everything about a player that is theirs to set — with one exception that depends on who
 * they are.
 *
 * A guest gets [GuestUsernameNote] where the name field would be. Their name was handed out
 * and cannot be replaced, so a field there could only ever be typed into and refused; what
 * stands in its place is the thing that would genuinely give them a name, and it goes
 * straight there. The avatar stays: it is theirs, it costs nothing to keep, and taking the
 * whole screen away over one row would punish them for the row they cannot have.
 *
 * @param canChangeUsername false for a guest. See
 *   [com.duzman46.gridbound.session.SessionState.canChangeUsername] for why.
 */
@Composable
fun EditProfileScreen(
    state: ProfileEditState,
    canChangeUsername: Boolean,
    onBack: () -> Unit,
    onUsername: (String) -> Unit,
    onAvatar: (String) -> Unit,
    onLinkAccount: () -> Unit,
    onSubmit: () -> Unit,
) {
    Scaffold(
        topBar = { ScreenTopBar(stringResource(R.string.profile_edit_title), onBack) },
    ) { padding ->
        ScreenBackground {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (canChangeUsername) {
                        UsernameField(state, onUsername)
                    } else {
                        GuestUsernameNote(onLinkAccount)
                    }
                    Text(
                        stringResource(R.string.profile_avatar_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AvatarPicker(state.avatarId, state.username.value, onAvatar)
                    SubmitButton(
                        text = stringResource(R.string.action_save),
                        onClick = onSubmit,
                        isSubmitting = state.isSubmitting,
                    )
                    state.error?.let { FormMessage(it) }
                }
            }
        }
    }
}

@Composable
private fun AvatarPicker(
    selectedId: String,
    name: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarPalette.ids.forEach { avatarId ->
            val selected = avatarId == selectedId
            Box(
                Modifier.selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onSelect(avatarId) },
                ),
                contentAlignment = Alignment.BottomEnd,
            ) {
                PlayerAvatar(avatarId, name, size = 56.dp)
                if (selected) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * What a guest is shown where the name field would be.
 *
 * Not a disabled field with an explanation beside it: a control that can never be used is a
 * question the screen keeps asking and answering. The sentence says why the name is fixed,
 * and the button underneath is the only thing that changes that answer.
 */
@Composable
private fun GuestUsernameNote(onLinkAccount: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.profile_username_guest_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SubmitButton(
            text = stringResource(R.string.auth_link_account),
            onClick = onLinkAccount,
            leadingIcon = Icons.Rounded.PersonAdd,
        )
    }
}

@Composable
private fun UsernameField(state: ProfileEditState, onUsername: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.username.value,
            onValueChange = onUsername,
            label = { Text(stringResource(R.string.username_label)) },
            singleLine = true,
            isError = state.username.isError,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                when {
                    state.username.isChecking -> CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )

                    state.username.isAvailable -> Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    else -> Unit
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        state.username.message?.let {
            Text(
                it.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.username.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

/** Name and account kind on one line, so the record below starts near the top. */
@Composable
private fun ProfileIdentity(profile: UserProfile) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerAvatar(profile.avatarId, profile.username, size = 64.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                profile.username,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (profile.isGuest) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        stringResource(R.string.auth_guest_badge),
                        Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * The whole record in one card: the three headline numbers, then the rest as a two-column
 * table of label/value rows.
 *
 * Rows rather than a grid of small tiles because a row can wrap a long label — "Niederlagen",
 * "Победная серия" — onto a second line and stay readable, where a tile narrow enough to fit
 * eight of them would have to break the word. That is what keeps this legible at large font
 * scales instead of merely small.
 */
@Composable
private fun ProfileStatsCard(profile: UserProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatTile(
                    label = stringResource(R.string.profile_rating),
                    value = profile.rating.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.profile_games),
                    value = profile.totalGames.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.profile_wins),
                    value = profile.wins.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailRowPair(
                leftLabel = stringResource(R.string.profile_losses),
                leftValue = profile.losses.toString(),
                rightLabel = stringResource(R.string.profile_draws),
                rightValue = profile.draws.toString(),
            )
            DetailRowPair(
                leftLabel = stringResource(R.string.profile_win_streak),
                leftValue = profile.currentWinStreak.toString(),
                rightLabel = stringResource(R.string.profile_best_streak),
                rightValue = profile.bestWinStreak.toString(),
            )
            // Five statistics leave one without a partner. Letting it span both columns reads
            // as a summary line rather than a gap, and the peak rating earns that spot.
            DetailRow(
                stringResource(R.string.profile_highest_rating),
                profile.highestRating.toString(),
            )
            if (profile.createdAt > 0L) {
                Text(
                    stringResource(R.string.profile_member_since, formatDate(profile.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Two statistics side by side, each keeping its value pinned to the end of its own half. */
@Composable
private fun DetailRowPair(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DetailRow(leftLabel, leftValue, Modifier.weight(1f))
        DetailRow(rightLabel, rightValue, Modifier.weight(1f))
    }
}

@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label absorbs the slack so the value lands on the end edge whatever its width.
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

/** The three numbers a player looks for first, so they carry the weight the table does not. */
@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Uses the platform formatter so the date follows the active locale. */
private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
