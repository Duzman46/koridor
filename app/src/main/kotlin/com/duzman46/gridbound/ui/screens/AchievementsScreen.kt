package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.achievements.domain.AchievementGroup
import com.duzman46.gridbound.achievements.domain.AchievementState
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.presentation.achievements.AchievementsUiState
import com.duzman46.gridbound.presentation.achievements.AchievementsViewModel
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.home.AchievementGroupHeader
import com.duzman46.gridbound.ui.components.home.AchievementRow
import com.duzman46.gridbound.ui.components.home.AchievementSummary
import com.duzman46.gridbound.ui.components.home.PremiumHeader
import com.duzman46.gridbound.ui.components.home.emblem

@Composable
fun AchievementsRoute(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AchievementsScreen(state, onBack)
}

/**
 * The badge shelf, in the order a player earns it.
 *
 * Groups rather than one long list, and the groups are the parts of the game rather than the
 * tiers: "against the machine" is somewhere to go, and "silver" is not. Inside a group the
 * ladder runs upwards, so the next rung is always the row under the one just earned.
 */
@Composable
private fun AchievementsScreen(state: AchievementsUiState, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        PremiumHeader(
            title = stringResource(R.string.achievements_title),
            subtitle = stringResource(R.string.achievements_subtitle),
            onBack = onBack,
        )
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
            item(key = "summary") {
                AchievementSummary(
                    unlocked = state.unlocked,
                    total = state.total,
                    fraction = state.fraction,
                    title = stringResource(
                        R.string.achievements_earned,
                        state.unlocked,
                        state.total,
                    ),
                    hint = stringResource(R.string.achievements_hint),
                )
            }

            AchievementGroup.entries.forEach { group ->
                val badges = state.of(group)
                if (badges.isEmpty()) return@forEach
                item(key = "head:${group.name}") {
                    AchievementGroupHeader(
                        title = stringResource(group.titleRes),
                        earned = "${state.unlockedIn(group)}/${badges.size}",
                    )
                }
                items(badges, key = { "badge:${it.achievement.name}" }) { badge ->
                    AchievementRow(
                        title = stringResource(badge.achievement.titleRes),
                        description = badge.description(),
                        tier = badge.achievement.tier,
                        emblem = badge.achievement.emblem(),
                        unlocked = badge.unlocked,
                        progress = badge.shown,
                        target = badge.target,
                        fraction = badge.fraction,
                    )
                }
            }
        }
    }
}

/**
 * What a badge asks of the player, in words.
 *
 * The number is a format argument rather than part of the sentence, so the three rungs of a
 * ladder share one translated string and cannot drift apart in nine languages. The bot badges
 * substitute the level's own name instead of a number, from the same strings the difficulty
 * screen uses — so "Hard" is spelled one way in the whole app.
 */
@Composable
private fun AchievementState.description(): String {
    val level = achievement.level
    return if (level == null) {
        stringResource(achievement.descriptionRes, achievement.quoted)
    } else {
        stringResource(achievement.descriptionRes, stringResource(level.label()))
    }
}

private fun Difficulty.label(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
    Difficulty.EXPERT -> R.string.difficulty_expert
}
