package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.leaderboard.domain.LeaderboardEntry
import com.duzman46.gridbound.leaderboard.domain.LeaderboardScope
import com.duzman46.gridbound.leaderboard.domain.OwnStanding
import com.duzman46.gridbound.presentation.leaderboard.LeaderboardUiState
import com.duzman46.gridbound.presentation.leaderboard.LeaderboardViewModel
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold
import com.duzman46.gridbound.ui.components.EmptyState
import com.duzman46.gridbound.ui.components.ErrorState
import com.duzman46.gridbound.ui.components.LoadingState
import com.duzman46.gridbound.ui.components.home.BottomItem
import com.duzman46.gridbound.ui.components.home.ClimbBanner
import com.duzman46.gridbound.ui.components.home.KoridorBottomBar
import com.duzman46.gridbound.ui.components.home.PodiumCard
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.ui.components.home.ScopeTabs
import com.duzman46.gridbound.ui.components.home.StandingRow
import com.duzman46.gridbound.ui.components.home.StandingsHeader
import com.duzman46.gridbound.ui.components.home.localeUpper

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
    onProfile: () -> Unit,
    viewModel: LeaderboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LeaderboardScreen(
        state = state,
        onBack = onBack,
        onOpenProfile = onOpenProfile,
        onLinkAccount = onLinkAccount,
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
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeaderboardBackArrow(onBack)
            Column(Modifier.weight(1f)) {
                Text(
                    text = localeUpper(stringResource(R.string.leaderboard_title)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = KoridorGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.leaderboard_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8B9098),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // No season selector. There is one season, the control offered no second choice,
            // and a dropdown with nothing in it is a promise the app cannot keep.
        }

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

                tab.isEmpty -> EmptyState(state.selectedScope.emptyMessage())
                else -> LeaderboardList(state, onOpenProfile, onLoadMore)
            }
        }
        OwnStandingBar(state, onLinkAccount)
        // The same three items the home screen shows, in the same order and with the same
        // words — because it is the same bar. A row that renames itself between two of its own
        // places is telling the player they have left one thing and arrived at another.
        KoridorBottomBar(
            items = listOf(
                BottomItem(stringResource(R.string.nav_home), PremiumIcon.HOUSE, onHome),
                BottomItem(stringResource(R.string.leaderboard_title), PremiumIcon.TROPHY) {},
                BottomItem(stringResource(R.string.nav_profile), PremiumIcon.PERSON, onProfile),
            ),
            selectedIndex = 1,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceSm),
        )
    }
}

/** The way back, drawn like the rest of the app's rather than borrowed from Material. */
@Composable
private fun LeaderboardBackArrow(onBack: () -> Unit) {
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
            .padding(horizontal = Dimens.ScreenPadding),
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
            state.isGuest -> ClimbBanner(
                title = localeUpper(stringResource(R.string.leaderboard_climb_title)),
                hint = state.noStandingMessage.asString(),
                action = stringResource(R.string.leaderboard_climb_action),
                onAction = onLinkAccount,
            )

            else -> Text(
                text = state.noStandingMessage.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8B9098),
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
