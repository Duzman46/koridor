package com.duzman46.gridbound.presentation

import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.AppError
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.presentation.leaderboard.LeaderboardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * What the bar at the foot of the leaderboard says when it has no row of the player's own.
 *
 * There are three reasons for that and they are not interchangeable, which is the point: the
 * bar is the only place the app ever tells somebody they are not signed in, so it has to be
 * right about it.
 */
class LeaderboardUiStateTest {

    @Test
    fun `a guest is told why they are unranked rather than told to sign in`() {
        // They already signed in — anonymously, which is exactly what they chose. "Sign in"
        // reads as an instruction they have followed and cannot follow again.
        val state = LeaderboardUiState(isGuest = true)

        assertEquals(UiText.Res(R.string.leaderboard_guest_not_ranked), state.noStandingMessage)
    }

    @Test
    fun `a player with no account is the one told to sign in`() {
        assertEquals(
            UiText.Res(R.string.leaderboard_sign_in_required),
            LeaderboardUiState().noStandingMessage,
        )
    }

    @Test
    fun `a signed-in player whose position did not load is not told to sign in`() {
        // The regression. Their standing is missing because the lookup failed, and the bar
        // used to read every missing standing as a missing account.
        val state = LeaderboardUiState(
            hasAccount = true,
            ownStandingError = AppError.NETWORK.message,
        )

        assertEquals(AppError.NETWORK.message, state.noStandingMessage)
        assertNotEquals(UiText.Res(R.string.leaderboard_sign_in_required), state.noStandingMessage)
    }

    @Test
    fun `a signed-in player is never told to sign in, even with no failure to report`() {
        // Nothing produces this state today, and if something ever does, the wrong answer to
        // fall back on is the one sentence on this screen that could be a lie.
        val state = LeaderboardUiState(hasAccount = true)

        assertEquals(UiText.Res(R.string.error_unknown), state.noStandingMessage)
    }
}
