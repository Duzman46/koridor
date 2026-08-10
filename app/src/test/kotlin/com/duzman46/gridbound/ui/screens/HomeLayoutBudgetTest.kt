package com.duzman46.gridbound.ui.screens

import com.duzman46.gridbound.ui.components.home.PremiumIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What is left of the home screen's height contract.
 *
 * The arithmetic version of this test is gone, and deliberately. It added up every fixed row and
 * handed the picture what remained, and it was wrong on the device twice — because a card
 * measures what its own text and padding say it measures, not what a constant in another file
 * claims. The screen no longer has a budget: the scene is weighted and absorbs whatever is
 * left, so overflow is structurally impossible rather than arithmetically unlikely, and there
 * is nothing left to assert about heights.
 *
 * What is worth pinning is the icon set. Five destinations on this screen are told apart by
 * their marks before their labels are read, and two of them are gold while three are not — a
 * ration that only holds if nobody quietly adds a sixth.
 */
class HomeIconographyTest {

    @Test
    fun `every home destination has its own mark`() {
        val used = listOf(
            PremiumIcon.TROPHY,
            PremiumIcon.PEOPLE,
            PremiumIcon.MORTARBOARD,
            PremiumIcon.SLIDERS,
            PremiumIcon.SHIELD_STAR,
        )
        assertEquals("two destinations share a mark", used.size, used.toSet().size)
    }

    @Test
    fun `the bar and the top row do not borrow the cards' marks`() {
        val cards = setOf(
            PremiumIcon.TROPHY,
            PremiumIcon.PEOPLE,
            PremiumIcon.MORTARBOARD,
            PremiumIcon.SLIDERS,
            PremiumIcon.SHIELD_STAR,
        )
        val chrome = setOf(
            PremiumIcon.HOUSE,
            PremiumIcon.GAMEPAD,
            PremiumIcon.PERSON,
            PremiumIcon.BARS,
            PremiumIcon.COG,
        )
        assertTrue(
            "a mark means two things on one screen",
            cards.intersect(chrome).isEmpty(),
        )
    }
}
