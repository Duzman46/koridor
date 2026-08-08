package com.duzman46.gridbound.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule behind the reported black screen, and the guard that it is still applied.
 *
 * The rule on its own is two lines and testing it proves little; what actually broke was a call
 * site, and a predicate test cannot see call sites. So the last test here reads the navigation
 * graph as text and insists every `popBackStack` in it goes through one of the two helpers.
 * That is unusual and it is deliberate: reverting any of the fifteen back arrows to a bare pop
 * is precisely the regression, and it is the only thing here that would notice.
 */
class BackStackRuleTest {

    @Test
    fun `a press on the screen the player is looking at leaves it`() {
        assertTrue(canLeaveScreen(isResumed = true, screenBeneath = "home"))
    }

    @Test
    fun `a press on a screen that is already leaving does nothing`() {
        // The case the first attempt at this fix missed, and the one that matters most. Two
        // fast taps on a back arrow: the second lands on a control whose screen has already
        // been popped and is merely still drawn. Guarding only on what is left underneath
        // stops the graph emptying and nothing else — so Home, Profile, Friends became Home,
        // with Profile passed through unseen.
        assertFalse(canLeaveScreen(isResumed = false, screenBeneath = "profile"))
    }

    @Test
    fun `the last screen stays even when the press is genuine`() {
        // Nothing reports an emptied graph. The host composes nothing, the window background
        // is what the player is left looking at — black, in the night palette — and system
        // back leaves the app rather than returning, because NavController switches off its
        // own callback once the stack is empty.
        assertFalse(canLeaveScreen(isResumed = true, screenBeneath = null))
    }

    @Test
    fun `a pop nobody pressed is held only to the backstop`() {
        // A save that has landed, or a password-reset event: these arrive after their screen
        // has stopped being the resumed one, and dropping them would strand the player on a
        // screen that has finished its work.
        assertTrue(canLeaveScreenUnprompted("home"))
        assertFalse(canLeaveScreenUnprompted(null))
    }

    @Test
    fun `no back arrow in the graph pops without going through the rule`() {
        val source = File("src/main/kotlin/com/duzman46/gridbound/navigation/AppNavigation.kt")
        assertTrue("AppNavigation.kt not found from ${File(".").absolutePath}", source.exists())

        val bare = source.readLines()
            .withIndex()
            .filter { (_, line) -> "popBackStack()" in line }
            // The two helpers are where the rule is applied, so they are the two places the
            // call is allowed to appear at all.
            .filterNot { (_, line) -> "canLeaveScreen(" in line || "canLeaveScreenUnprompted(" in line }
            .map { (index, line) -> "${index + 1}: ${line.trim()}" }

        assertEquals("popBackStack outside the rule", emptyList<String>(), bare)
    }
}
