package com.duzman46.gridbound.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.match.domain.MatchOutcome
import com.duzman46.gridbound.match.domain.RecentMatch
import com.duzman46.gridbound.presentation.profile.RecentGamesState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * How many games the section shows before the player asks for the rest.
 *
 * Three reads as a summary — "how has it been going lately?" — where the full ten reads as a
 * list, and a list is what you open once the summary has made you curious. Three is also short
 * enough to take in at a glance, which is what keeps this a part of a profile rather than
 * something that takes one over.
 */
private const val COLLAPSED_COUNT = 3

/**
 * The recent-games section, on the player's own profile and on anybody else's.
 *
 * Shared by both pages deliberately: whose history it is changes nothing about how a history
 * reads. Nothing here can be acted on, which is why it needs no owner-versus-stranger branch
 * at all — unlike the controls above it, which are the whole reason those pages are separate.
 *
 * A read that failed draws nothing rather than an error. This is one section of a page that
 * has already loaded, and a profile that reports its own network trouble twice is worse than
 * one that quietly leaves out the part it could not fetch.
 */
@Composable
fun RecentGamesCard(state: RecentGamesState, modifier: Modifier = Modifier) {
    if (state.isUnavailable) return
    val locale = LocalResources.current.configuration.locales[0]
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) state.matches else state.matches.take(COLLAPSED_COUNT)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.profile_recent_games),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            when {
                state.isLoading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }

                // Not a blank space under the heading. A profile with nothing here belongs to
                // somebody who has not played online yet, and that is a fact worth stating —
                // on a stranger's page as much as on your own.
                state.matches.isEmpty() -> Text(
                    stringResource(R.string.profile_recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> {
                    shown.forEachIndexed { index, match ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        RecentGameRow(match, locale)
                    }
                    if (state.matches.size > COLLAPSED_COUNT) {
                        ShowMoreRow(expanded, state.matches.size) { expanded = !expanded }
                    }
                }
            }
        }
    }
}

/**
 * The control that reveals the rest, as a row at the foot of the list rather than a tap on the
 * heading above it.
 *
 * A heading is a label, and making it secretly pressable is an affordance nobody can see: a
 * player who never tries it never learns there is more. It also could not say what it does —
 * the same words have to mean "expand" and then "collapse" — whereas a row names the action
 * and the number behind it, and changes its own label when the action changes. And it can be
 * absent: with three games or fewer there is nothing to reveal, and this simply is not drawn,
 * where a heading that is sometimes a button and sometimes not is a control that lies.
 *
 * Last, it appears exactly where the question arises — at the end of the third row, which is
 * the moment the reader wonders whether that is all of them.
 */
@Composable
private fun ShowMoreRow(expanded: Boolean, total: Int, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (expanded) {
                stringResource(R.string.profile_recent_show_fewer)
            } else {
                stringResource(R.string.profile_recent_show_all, total)
            },
        )
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * One game: who it was against and when on the reading edge, how it went and what it cost on
 * the other.
 *
 * The result is a word rather than a coloured pip, so the colour is emphasis and never the
 * only carrier of the meaning — which is what keeps the row legible to a player who cannot
 * tell the two colours apart.
 */
@Composable
private fun RecentGameRow(match: RecentMatch, locale: Locale) {
    Row(
        // One stop for a screen reader, announced as "alice, 5.08.2026, won, +12" instead of
        // four separate stops the listener has to assemble themselves.
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                // Empty only when the opponent's profile was already gone by the time the
                // server wrote the row. The match still happened; a blank line would be the
                // one thing that could not be true.
                match.opponentName.ifBlank {
                    stringResource(R.string.profile_recent_unknown_opponent)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(match.playedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(match.outcome.label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = match.outcome.tint(),
            )
            Text(
                // A match nobody was rated on says so. Printing the zero it did not move
                // would read as a rated game that earned nothing, which is a different — and
                // much more annoying — thing to have happened.
                match.ratingChange?.let { String.format(locale, "%+d", it) }
                    ?: stringResource(R.string.profile_recent_unranked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@get:StringRes
private val MatchOutcome.label: Int
    get() = when (this) {
        MatchOutcome.WIN -> R.string.profile_recent_result_win
        MatchOutcome.LOSS -> R.string.profile_recent_result_loss
        MatchOutcome.DRAW -> R.string.profile_recent_result_draw
    }

@Composable
private fun MatchOutcome.tint(): Color = when (this) {
    MatchOutcome.WIN -> MaterialTheme.colorScheme.primary
    MatchOutcome.LOSS -> MaterialTheme.colorScheme.error
    MatchOutcome.DRAW -> MaterialTheme.colorScheme.onSurfaceVariant
}
