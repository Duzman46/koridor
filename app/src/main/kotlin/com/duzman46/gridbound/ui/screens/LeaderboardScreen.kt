package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.navigation.DockedBarSpace
import com.duzman46.gridbound.leaderboard.domain.LeaderboardEntry
import com.duzman46.gridbound.leaderboard.domain.LeaderboardScope
import com.duzman46.gridbound.leaderboard.domain.OwnStanding
import com.duzman46.gridbound.presentation.leaderboard.LeaderboardUiState
import com.duzman46.gridbound.presentation.leaderboard.LeaderboardViewModel
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.Palette
import com.duzman46.gridbound.ui.components.EmptyState
import com.duzman46.gridbound.ui.components.ErrorState
import com.duzman46.gridbound.ui.components.LoadingState
import com.duzman46.gridbound.ui.components.home.LinkAccountBanner
import com.duzman46.gridbound.ui.components.home.PodiumCard
import com.duzman46.gridbound.ui.components.home.PremiumHeader
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.ui.components.home.ScopeTabs
import com.duzman46.gridbound.ui.components.home.StandingRow
import com.duzman46.gridbound.ui.components.home.StandingsHeader
import com.duzman46.gridbound.ui.components.home.localeUpper
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.duzman46.gridbound.ui.components.home.FriendPerk
import com.duzman46.gridbound.ui.components.home.FriendsEmptyPanel

private val VISIBLE_SCOPES = listOf(
    LeaderboardScope.GLOBAL,
    LeaderboardScope.WEEKLY,
    LeaderboardScope.FRIENDS,
)

@Composable
fun LeaderboardRoute(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLinkAccount: () -> Unit,
    onFriends: () -> Unit,
    onProfile: () -> Unit,
    viewModel: LeaderboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LeaderboardScreen(
        state = state,
        onBack = onBack,
        onOpenProfile = onOpenProfile,
        onLinkAccount = onLinkAccount,
        onFriends = onFriends,
        onProfile = onProfile,
        onSelectScope = viewModel::selectScope,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
    )
}

@Composable
private fun LeaderboardScreen(
    state: LeaderboardUiState,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLinkAccount: () -> Unit,
    onFriends: () -> Unit,
    onProfile: () -> Unit,
    onSelectScope: (LeaderboardScope) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    // Home is where this screen was opened from, so leaving is the same gesture as going back.
    val onHome = onBack
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // No back arrow.
        //
        // This is one of the three places the docked bar switches between, and there is no
        // "back" from a place — there is only the place next to it, which is what the bar is
        // for. An arrow here claimed the screen was something the player had opened and would
        // return from, sitting where the title of a destination belongs. System back still
        // works and still pops, for whoever arrived by the card on the home screen.
        // A null onBack is exactly the decision above, now said in one word instead of a
        // hand-built row. The uppercase went with it: no other screen shouts its own name, and
        // a fifth treatment of one gold title is what made five headers feel like five apps.
        //
        // No season selector either. There is one season, the control offered no second choice,
        // and a dropdown with nothing in it is a promise the app cannot keep.
        PremiumHeader(
            title = stringResource(R.string.leaderboard_title),
            onBack = null,
            subtitle = stringResource(R.string.leaderboard_subtitle),
        )

        ScopeTabs(
            labels = VISIBLE_SCOPES.map { it.label() },
            icons = listOf(PremiumIcon.TROPHY, PremiumIcon.CALENDAR, PremiumIcon.PEOPLE),
            selected = VISIBLE_SCOPES.indexOf(state.selectedScope).coerceAtLeast(0),
            onSelect = { onSelectScope(VISIBLE_SCOPES[it]) },
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
        )

        val tab = state.current
        Box(Modifier.weight(1f)) {
            when {
                tab.isLoading -> LoadingState()
                tab.error != null && tab.entries.isEmpty() ->
                    ErrorState(tab.error.asString(), onRetry)

                // The friends board with nobody on it is not an error and not a shrug: it is
                // the same "you have no friends yet" the friends screen answers with a panel,
                // so it answers with that panel. A one-line EmptyState here left the player on
                // a blank board with no way off it.
                tab.isEmpty && state.selectedScope == LeaderboardScope.FRIENDS ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Dimens.ScreenPadding)
                            .padding(top = Dimens.SpaceSm, bottom = Dimens.SpaceLg),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        FriendsEmptyPanel(
                            title = stringResource(R.string.friends_empty_title),
                            body = stringResource(R.string.leaderboard_empty_friends),
                            perks = listOf(
                                FriendPerk(
                                    icon = PremiumIcon.PLUS,
                                    title = stringResource(R.string.friends_perk_add),
                                    body = stringResource(R.string.friends_perk_add_hint),
                                ),
                                FriendPerk(
                                    icon = PremiumIcon.BARS,
                                    title = stringResource(R.string.friends_perk_compare),
                                    body = stringResource(R.string.friends_perk_compare_hint),
                                ),
                            ),
                            // A guest is offered nothing here for the same reason the friends
                            // screen offers them nothing: the form behind it needs an account.
                            action = stringResource(R.string.friends_add_player)
                                .takeIf { !state.isGuest },
                            onAction = onFriends,
                        )
                    }

                tab.isEmpty -> EmptyState(state.selectedScope.emptyMessage())
                else -> LeaderboardList(state, onOpenProfile, onLoadMore)
            }
        }
        OwnStandingBar(state, onLinkAccount)
        // The docked bar is not here any more. It is drawn once, outside the navigation host,
        // above every place it switches between — so it stays put while they change under it.
    }
}

@Composable
private fun LeaderboardList(
    state: LeaderboardUiState,
    onOpenProfile: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val tab = state.current
    val listState = rememberLazyListState()
    // Fetch the next page a few rows before the end so scrolling stays smooth.
    val shouldLoadMore by remember(tab.entries.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= tab.entries.size - 5
        }
    }
    LaunchedEffect(listState, tab.entries.size) {
        snapshotFlow { shouldLoadMore }.collect { if (it) onLoadMore() }
    }

    // The first three get the stage and everybody else gets a row. A table that renders its
    // winner the same way as its fortieth entry is a list, and nobody wants to be on a list.
    val podium = tab.entries.take(PODIUM_PLACES)
    val rest = tab.entries.drop(PODIUM_PLACES)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Dimens.ScreenPadding,
            end = Dimens.ScreenPadding,
            top = Dimens.SpaceMd,
            bottom = Dimens.SpaceMd,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        if (podium.isNotEmpty()) {
            item(key = "podium") {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceSm),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Second, first, third — left to right, the way a podium stands.
                    listOf(1, 0, 2).forEach { index ->
                        podium.getOrNull(index)?.let { entry ->
                            PodiumCard(
                                place = index + 1,
                                name = entry.username,
                                initial = entry.username.take(1).uppercase(),
                                rating = entry.rating.toString(),
                                wins = entry.wins.toString(),
                                // A letter, the way the reference sets it. "Galibiyet" beside
                                // "Mağlubiyet" in a third of a phone's width printed as
                                // "GalibiyetXMa" — two words with nowhere to go.
                                winsLabel = stringResource(R.string.leaderboard_wins_short),
                                losses = (entry.totalGames - entry.wins).coerceAtLeast(0).toString(),
                                lossesLabel = stringResource(R.string.leaderboard_losses_short),
                                onClick = { onOpenProfile(entry.userId) },
                            )
                        }
                    }
                }
            }
        }

        if (rest.isNotEmpty()) {
            item(key = "head") {
                StandingsHeader(
                    rankLabel = stringResource(R.string.leaderboard_column_rank),
                    playerLabel = stringResource(R.string.leaderboard_column_player),
                )
            }
        }

        items(rest, key = LeaderboardEntry::userId) { entry ->
            StandingRow(
                rank = entry.rank?.toString().orEmpty(),
                initial = entry.username.take(1).uppercase(),
                name = entry.username,
                wins = entry.wins.toString(),
                losses = (entry.totalGames - entry.wins).coerceAtLeast(0).toString(),
                rating = entry.rating.toString(),
                highlighted = entry.userId == state.ownStanding?.entry?.userId,
                onClick = { onOpenProfile(entry.userId) },
            )
        }
        if (tab.isAppending) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/**
 * The player's own row, pinned so it stays visible however far the list is scrolled.
 *
 * With no row to pin, the bar says why there is none, and
 * [LeaderboardUiState.noStandingMessage] is where that is decided — the reason belongs to the
 * player's situation, not to this layout.
 */
@Composable
private fun OwnStandingBar(state: LeaderboardUiState, onLinkAccount: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding)
            .navigationBarsPadding()
            .padding(bottom = DockedBarSpace),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        when {
            state.isOwnStandingLoading -> Box(
                Modifier.fillMaxWidth().padding(Dimens.SpaceMd),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }

            state.ownStanding != null -> OwnStandingRow(state.ownStanding)

            // Only a guest is offered an account, because only a guest is missing one. A player
            // who has linked and simply has not been ranked yet was being told to link again,
            // which is the app not knowing who it is talking to.
            state.isGuest -> LinkAccountBanner(
                title = localeUpper(stringResource(R.string.leaderboard_climb_title)),
                hint = state.noStandingMessage.asString(),
                action = stringResource(R.string.leaderboard_climb_action),
                onAction = onLinkAccount,
            )

            else -> Text(
                text = state.noStandingMessage.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.InkMuted,
            )
        }
        // No "the table updates live" line. It was a sentence about how the app works, sitting
        // where the standings should be, on the one screen whose whole content is a list.
    }
}

@Composable
private fun OwnStandingRow(standing: OwnStanding) {
    val entry = standing.entry
    // A capped scan can only prove "at least this far down", so the number is shown as N+.
    val rank = if (standing.isApproximate) {
        stringResource(R.string.leaderboard_rank_capped, entry.rank ?: 0)
    } else {
        entry.rank?.toString().orEmpty()
    }
    StandingRow(
        rank = rank,
        initial = entry.username.take(1).uppercase(),
        name = entry.username,
        wins = entry.wins.toString(),
        losses = (entry.totalGames - entry.wins).coerceAtLeast(0).toString(),
        rating = entry.rating.toString(),
        highlighted = true,
        onClick = null,
    )
}

/** How many places stand on the podium rather than in the table. */
private const val PODIUM_PLACES = 3

@Composable
private fun LeaderboardScope.label(): String = stringResource(
    when (this) {
        LeaderboardScope.GLOBAL -> R.string.leaderboard_tab_global
        LeaderboardScope.WEEKLY -> R.string.leaderboard_tab_weekly
        LeaderboardScope.FRIENDS -> R.string.leaderboard_tab_friends
    },
)

@Composable
private fun LeaderboardScope.emptyMessage(): String = stringResource(
    when (this) {
        LeaderboardScope.GLOBAL -> R.string.leaderboard_empty
        LeaderboardScope.WEEKLY -> R.string.leaderboard_empty_weekly
        LeaderboardScope.FRIENDS -> R.string.leaderboard_empty_friends
    },
)
