package com.duzman46.gridbound.profile

import com.duzman46.gridbound.auth.domain.AccountType
import com.duzman46.gridbound.profile.domain.UserProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a profile carries a name its owner chose, which two separate things hang off.
 *
 * The entry gate sends an account that has not been named to the username screen, and
 * `RtdbUserProfileRepository.claimBoardRating` refuses to put one on the all-time board. The
 * second is the newer of the two and the reason it matters: linking a credential makes an
 * account real several seconds before its owner finishes naming it, and the board claim used
 * to travel with the link — so those seconds were spent listed publicly as `guest_######`, and
 * an app killed in them left the player there indefinitely.
 *
 * Deliberately a property of the profile rather than of the device: a preference only knows
 * about the install it was set on, and the answer has to survive a reinstall and follow the
 * player to a second handset.
 */
class ProfileNameTest {

    private fun profile(username: String) = UserProfile(
        userId = "alice-uid",
        username = username,
        normalizedUsername = username.lowercase(),
        avatarId = "avatar_01",
        accountType = AccountType.GOOGLE,
    )

    @Test
    fun `a name the app handed out is not one the player chose`() {
        assertFalse(profile("guest_483920").hasChosenName)
        // The scheme before it. A profile written by an older build still says the same thing.
        assertFalse(profile("player_a1b2c3").hasChosenName)
    }

    @Test
    fun `a name the player typed is`() {
        assertTrue(profile("alice").hasChosenName)
        assertTrue(profile("Koridor_Fan").hasChosenName)
    }

    @Test
    fun `casing is not a way round it`() {
        // The generated form is matched against the normalized name, so a profile whose name
        // reached the database in another case is still recognised as unnamed.
        assertFalse(profile("Guest_483920").hasChosenName)
    }
}
