package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.duzman46.gridbound.ui.components.EmptyState
import com.duzman46.gridbound.ui.components.ErrorState
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.LoadingState
import com.duzman46.gridbound.ui.components.PlayerAvatar
import com.duzman46.gridbound.ui.components.ScreenTopBar

private val VISIBLE_SCOPES = listOf(
    LeaderboardScope.GLOBAL,
    LeaderboardScope.WEEKLY,
    LeaderboardScope.FRIENDS,
)

@Composable
fun LeaderboardRoute(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: LeaderboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LeaderboardScreen(
        state = state,
        onBack = onBack,
        onOpenProfile = onOpenProfile,
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
    onSelectScope: (LeaderboardScope) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = { ScreenTopBar(stringResource(R.string.leaderboard_title), onBack) },
        bottomBar = { OwnStandingBar(state) },
    ) { padding ->
        ScreenBackground {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                TabRow(selectedTabIndex = VISIBLE_SCOPES.indexOf(state.selectedScope).coerceAtLeast(0)) {
                    VISIBLE_SCOPES.forEach { scope ->
                        Tab(
                            selected = state.selectedScope == scope,
                            onClick = { onSelectScope(scope) },
                            text = { Text(scope.label()) },
                        )
                    }
                }
                val tab = state.current
                when {
                    tab.isLoading -> LoadingState()
                    tab.error != null && tab.entries.isEmpty() ->
                        ErrorState(tab.error.asString(), onRetry)

                    tab.isEmpty -> EmptyState(state.selectedScope.emptyMessage())
                    else -> LeaderboardList(state, onOpenProfile, onLoadMore)
                }
            }
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(tab.entries, key = LeaderboardEntry::userId) { entry ->
            LeaderboardRow(
                entry = entry,
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
 * A standing on the table, and — in the list, where the name belongs to someone else — the way
 * to that player's page.
 *
 * The tap lives on the inner row rather than on the [Surface] so the ripple is clipped to the
 * rounded shape instead of washing over a rectangle behind it. A null [onClick] is the pinned
 * own-standing bar: reading your own record is what the profile screen is for, and a row that
 * led you to yourself would be the one row on the table that goes nowhere new.
 */
@Composable
private fun LeaderboardRow(
    entry: LeaderboardEntry,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val description = stringResource(
        R.string.cd_leaderboard_row,
        entry.rank ?: 0,
        entry.username,
        entry.rating,
    )
    val openLabel = stringResource(R.string.cd_open_profile)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = MaterialTheme.shapes.large,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        },
    ) {
        Row(
            Modifier
                .then(
                    if (onClick == null) {
                        Modifier
                    } else {
                        Modifier.clickable(onClickLabel = openLabel, onClick = onClick)
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = entry.rank?.toString().orEmpty(),
                modifier = Modifier.widthIn(min = 34.dp).clearAndSetSemantics { },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlayerAvatar(entry.avatarId, entry.username, size = 38.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    entry.username,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.leaderboard_record, entry.wins, entry.totalGames),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                entry.rating.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/**
 * The player's own row, pinned so it stays visible however far the list is scrolled.
 *
 * With no row to pin, the bar says why there is none. A guest is told the particular reason —
 * an anonymous account is rated but never ranked — because "sign in" reads as an instruction
 * they have already followed.
 */
@Composable
private fun OwnStandingBar(state: LeaderboardUiState) {
    Surface(tonalElevation = 4.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isOwnStandingLoading -> CircularProgressIndicator(
                    Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )

                state.ownStanding != null -> Column(
                    Modifier.fillMaxWidth().widthIn(max = 720.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.leaderboard_your_rank),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OwnStandingRow(state.ownStanding)
                }

                else -> Text(
                    stringResource(
                        if (state.isGuest) {
                            R.string.leaderboard_guest_not_ranked
                        } else {
                            R.string.leaderboard_sign_in_required
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OwnStandingRow(standing: OwnStanding) {
    val entry = standing.entry
    // A capped scan can only prove "at least this far down", so the number is shown as N+.
    val displayed = if (standing.isApproximate) {
        entry.copy(rank = null)
    } else {
        entry
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (standing.isApproximate) {
            Text(
                stringResource(R.string.leaderboard_rank_capped, entry.rank ?: 0),
                modifier = Modifier.widthIn(min = 34.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LeaderboardRow(displayed, highlighted = true, modifier = Modifier.weight(1f))
    }
}

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
