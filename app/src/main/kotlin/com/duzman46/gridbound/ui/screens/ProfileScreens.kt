package com.duzman46.gridbound.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.duzman46.gridbound.ui.components.ReportDialog
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.EmptyState
import com.duzman46.gridbound.ui.components.SecondarySubmitButton
import com.duzman46.gridbound.ui.components.LoadingState
import com.duzman46.gridbound.ui.components.PlayerAvatar
import com.duzman46.gridbound.ui.components.PremiumNotice
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.SubmitButton
import com.duzman46.gridbound.navigation.DockedBarSpace
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold
import com.duzman46.gridbound.ui.components.home.BadgeAccent
import com.duzman46.gridbound.ui.components.home.PremiumHeader
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.ui.components.home.PremiumIconButton
import com.duzman46.gridbound.ui.components.home.OptionEntry
import com.duzman46.gridbound.ui.components.home.OptionGroup
import com.duzman46.gridbound.ui.components.home.ProfileEmptyGames
import com.duzman46.gridbound.ui.components.home.ProfileFeatureCard
import com.duzman46.gridbound.ui.components.home.FriendActionButton
import com.duzman46.gridbound.ui.components.home.ProfileDetailCard
import com.duzman46.gridbound.ui.components.home.ProfileIdentity as PremiumProfileIdentity
import com.duzman46.gridbound.ui.components.home.ProfilePips
import com.duzman46.gridbound.ui.components.home.ProfileProgressBar
import com.duzman46.gridbound.ui.components.home.ProfileStatStrip
import com.duzman46.gridbound.ui.components.home.SectionLabel
import com.duzman46.gridbound.ui.components.home.PuzzleAccent
import com.duzman46.gridbound.ui.components.home.RecentGamesPremium
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
 * The player's own page: who they are, their record, and what they are collecting.
 *
 * This was the last Material screen among the three the docked bar switches between — a card of
 * statistics under four stacked buttons, next to a home screen and a leaderboard that had both
 * been redrawn. Nothing here is new information. The rating, the record, the win streak and the
 * recent matches were all on the old page; they are arranged so the numbers lead and the
 * navigation stops being a list of destinations the player has to read.
 *
 * The four buttons are gone rather than restyled. Leaderboard is a tab of its own, friends hang
 * off the home screen, and Account is a row inside Settings — three of the four were second ways
 * into places already one tap away, and the fourth, "Edit profile", is now the pencil on the
 * thing it edits.
 *
 * **What the reference asked for and this does not have.** The mock puts a daily-puzzle card and
 * a consecutive-days streak beside the badges. Neither exists: there is no puzzle, and nothing
 * anywhere records days opened — `ComeBackWorker` says in as many words that there is no streak
 * to protect. The puzzle card is therefore present but marked "soon" and opens a notice; the
 * third card counts the run of *wins*, which the app has kept all along and which already drives
 * two badges. Drawing a day counter that nothing increments would have been a lie in a number.
 */
@Composable
fun ProfileScreen(
    profile: UserProfile?,
    hasAccount: Boolean,
    recentGames: RecentGamesState,
    achievementsUnlocked: Int,
    achievementsTotal: Int,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAccount: () -> Unit,
    onFriends: () -> Unit,
    onSettings: () -> Unit,
    onAchievements: () -> Unit,
    onStatistics: () -> Unit,
    onOpenPlayer: (String) -> Unit,
) {
    var puzzleNotice by remember { mutableStateOf(false) }
    if (puzzleNotice) {
        PremiumNotice(
            title = stringResource(R.string.profile_puzzle_soon_title),
            message = stringResource(R.string.profile_puzzle_soon_body),
            icon = PremiumIcon.CALENDAR,
            onDismiss = { puzzleNotice = false },
        )
    }

    ScreenBackground {
        if (profile == null) {
            // A null profile is only a loading state when there is an account behind it.
            // A guest playing locally has none and never will, so spinning forever here
            // was a dead end on the one screen that is supposed to explain who you are.
            if (hasAccount) {
                LoadingState()
            } else {
                EmptyState(
                    message = stringResource(R.string.auth_guest_explainer),
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
        // No Scaffold and no banner. A band of board ran across the top for one build and it
        // cost 196dp of a 891dp window to say nothing the screen did not already say — with it
        // there, the recent matches ended up underneath the docked bar with 78 pixels of scroll
        // to reach them.
        //
        // The header is outside the scroll and the inset is on the container, which is what
        // every other premium screen does. Inside the scroll, the cards ran up under the status
        // bar and printed themselves across the clock.
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            PremiumHeader(
                title = stringResource(R.string.profile_title),
                onBack = onBack,
                trailing = {
                    PremiumIconButton(
                        icon = PremiumIcon.COG,
                        label = stringResource(R.string.game_settings),
                        onClick = onSettings,
                    )
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpaceLg)
                        // Both, in this order, and the order is the point. DockedBarSpace is the
                        // bar and the air it floats in and deliberately excludes the system's
                        // navigation inset, because the bar applies that itself — so a screen
                        // that clears the bar has to apply it too. Leaving it out is why the
                        // last card sat under the bar on a handset with three-button navigation,
                        // where the inset is 48dp rather than a gesture bar's 18.
                        .navigationBarsPadding()
                        .padding(bottom = DockedBarSpace),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                ) {
                    // Aliased at the import: this file already has a private ProfileIdentity,
                    // the Material one that a stranger's page still uses.
                    PremiumProfileIdentity(
                        username = profile.username,
                        avatarId = profile.avatarId,
                        isGuest = profile.isGuest,
                        onEdit = onEdit,
                    )
                    ProfileStatStrip(
                        rating = profile.rating,
                        games = profile.totalGames,
                        wins = profile.wins,
                        losses = profile.losses,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                    ) {
                        // Marked "soon" rather than given the reference's red dot. A dot means
                        // something is waiting for the player; nothing is, and the button opens
                        // a notice that says so.
                        ProfileFeatureCard(
                            icon = PremiumIcon.PUZZLE,
                            accent = PuzzleAccent,
                            title = stringResource(R.string.profile_puzzle_title),
                            headline = null,
                            action = stringResource(R.string.profile_puzzle_action),
                            onAction = { puzzleNotice = true },
                            badge = stringResource(R.string.profile_soon_badge),
                        )
                        ProfileFeatureCard(
                            icon = PremiumIcon.TARGET,
                            accent = BadgeAccent,
                            title = stringResource(R.string.achievements_title),
                            headline = "$achievementsUnlocked / $achievementsTotal",
                            action = stringResource(R.string.profile_badges_action),
                            onAction = onAchievements,
                            detail = {
                                ProfileProgressBar(
                                    fraction = if (achievementsTotal == 0) {
                                        0f
                                    } else {
                                        achievementsUnlocked.toFloat() / achievementsTotal
                                    },
                                    accent = BadgeAccent,
                                )
                            },
                        )
                        ProfileFeatureCard(
                            icon = PremiumIcon.FLAME,
                            accent = KoridorGold,
                            title = stringResource(R.string.profile_win_streak),
                            headline = profile.currentWinStreak.toString(),
                            action = stringResource(R.string.profile_streak_action),
                            onAction = onStatistics,
                            detail = {
                                ProfilePips(
                                    filled = profile.currentWinStreak,
                                    total = STREAK_PIPS,
                                    accent = KoridorGold,
                                )
                            },
                        )
                    }
                    // People, under the player's own numbers and above the people they have
                    // played. It was a button on this page before the redesign and dropping it
                    // was the one removal that cost something: the friend list is reachable
                    // from the home screen and nowhere else, and this is the page about who
                    // you are.
                    OptionGroup(
                        listOf(
                            OptionEntry(
                                icon = PremiumIcon.PEOPLE,
                                title = stringResource(R.string.friends_title),
                                onClick = onFriends,
                            ),
                        ),
                    )
                    // The heading lives inside the card now, with the clock and the expander,
                    // the way the reference draws it — so there is no floating label above an
                    // empty note when a player has not finished an online match yet.
                    when {
                        recentGames.isLoading -> LoadingState()
                        recentGames.matches.isEmpty() -> ProfileEmptyGames()
                        else -> RecentGamesPremium(
                            matches = recentGames.matches,
                            rating = profile.rating,
                            onOpenPlayer = onOpenPlayer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * How many pips the streak card draws.
 *
 * Five, because that is [com.duzman46.gridbound.achievements.domain.Achievement.STREAK_FIVE]'s
 * target — the longest run the game asks for — so a full row means the streak is as long as
 * anything rewards rather than as long as an arbitrary bar.
 */
private const val STREAK_PIPS = 5

/**
 * Another player's page: the same record, none of the controls that belong to its owner.
 *
 * Deliberately a second screen rather than [ProfileScreen] behind an `isOwner` flag. Only the
 * record is common; everything below it is the opposite of the other screen's — one offers the
 * ways into your own things, the other offers a relationship — so one screen would have been a
 * shared header over two bodies of conditionals. Splitting them also turns the rule that
 * matters into a structural fact: there is no branch here that can draw "Edit profile", so no
 * later change to a flag can put the owner's controls on a stranger's page. What is genuinely
 * shared is shared: both pages draw the same identity block, the same stat strip and the same
 * recent-match list out of ProfilePremium.kt, and the stranger's simply passes no onEdit.
 */
@Composable
fun PlayerProfileRoute(
    onBack: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    onLinkAccount: () -> Unit,
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
        onLinkAccount = onLinkAccount,
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
    onLinkAccount: () -> Unit,
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
                        .padding(horizontal = Dimens.SpaceLg)
                        .navigationBarsPadding()
                        .padding(bottom = Dimens.SpaceXl),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                ) {
                    // The owner's own components, with no onEdit. Everything about this page is
                    // the same record in the same shapes; what it must never grow is a way to
                    // change any of it, and the absent lambda is what guarantees that.
                    PremiumProfileIdentity(
                        username = profile.username,
                        avatarId = profile.avatarId,
                        isGuest = profile.isGuest,
                        trailing = {
                            FriendAction(
                                state = state,
                                onSendRequest = onSendRequest,
                                onAccept = onAccept,
                                onUnblock = onUnblock,
                                onLinkAccount = onLinkAccount,
                            )
                        },
                    )
                    ProfileStatStrip(
                        rating = profile.rating,
                        games = profile.totalGames,
                        wins = profile.wins,
                        losses = profile.losses,
                    )
                    // No streaks and no peak rating. Somebody else's run of form is theirs to
                    // know: the four public figures are what a stranger needs to size up an
                    // opponent, and how hot they are running right now is not one of them.
                    profile.createdAt.takeIf { it > 0L }?.let {
                        Text(
                            text = stringResource(
                                R.string.profile_member_since,
                                formatDate(it),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.message?.let { FormMessage(it) }
                    SafetyActions(state, onBlock, onReport)
                    SectionLabel(stringResource(R.string.profile_recent_games))
                    when {
                        recentGames.isLoading -> LoadingState()
                        recentGames.matches.isNotEmpty() -> RecentGamesPremium(
                            matches = recentGames.matches,
                            rating = profile.rating,
                            onOpenPlayer = onOpenPlayer,
                        )
                        else -> ProfileEmptyGames()
                    }
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
    onLinkAccount: () -> Unit,
) {
    if (state.isSelf) return
    // A guest gets the same button, pointed at the thing that makes it work. The page used to
    // print a sentence saying friendship needs an account, next to a Report button it had
    // nothing to do with.
    if (state.requiresAccount) {
        FriendActionButton(
            icon = PremiumIcon.PLUS,
            label = stringResource(R.string.friends_add),
            enabled = true,
            onClick = onLinkAccount,
        )
        return
    }
    when (state.status) {
        FriendshipStatus.NONE -> FriendActionButton(
            icon = PremiumIcon.PLUS,
            label = stringResource(R.string.friends_add),
            enabled = !state.isBusy,
            onClick = onSendRequest,
        )

        FriendshipStatus.REQUEST_RECEIVED -> FriendActionButton(
            icon = PremiumIcon.PEOPLE,
            label = stringResource(R.string.friends_accept),
            enabled = !state.isBusy,
            onClick = onAccept,
        )

        // Sent and already-friends are states, not offers, so neither is pressable.
        FriendshipStatus.REQUEST_SENT -> FriendActionButton(
            icon = PremiumIcon.CLOCK,
            label = stringResource(R.string.friends_request_pending),
            enabled = false,
            onClick = {},
        )

        FriendshipStatus.FRIENDS -> FriendActionButton(
            icon = PremiumIcon.PEOPLE,
            label = stringResource(R.string.friends_already_friends),
            enabled = false,
            onClick = {},
        )

        FriendshipStatus.BLOCKED -> FriendActionButton(
            icon = PremiumIcon.NO_ADS,
            label = stringResource(R.string.friends_unblock),
            enabled = !state.isBusy,
            onClick = onUnblock,
        )
    }
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

/** Uses the platform formatter so the date follows the active locale. */
private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
