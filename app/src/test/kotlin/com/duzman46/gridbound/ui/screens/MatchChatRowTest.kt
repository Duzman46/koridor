package com.duzman46.gridbound.ui.screens

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The message row's two measurements, and the guard that the row still uses them.
 *
 * Neither of them can be seen on the screen they belong to: this module has no instrumentation
 * on the JVM, and both faults were only ever going to be found on a handset in a language
 * nobody testing the app reads. So the arithmetic is asserted here against the numbers a 360 dp
 * phone actually produces, in the same spirit as HomeLayoutBudgetTest.
 *
 * The last test reads GameScreen.kt as text, which is unusual and deliberate: what broke both
 * times was a call site — half the row handed to each bubble whatever was in it, a flat 48 dp
 * asked to hold text that scales, and a way into the mute gated on being able to speak — and no
 * amount of testing a function can see a call site that stopped calling it. Compare
 * BackStackRuleTest, which guards the same class of regression the same way.
 */
class MatchChatRowTest {

    /**
     * What the row has for the two bubbles on the narrowest phone in support: 360 dp at density
     * 3, less the screen's 10 dp insets, the send button's 48 dp and the two 8 dp gaps.
     */
    private val space = (360 - 20) * 3 - 48 * 3 - 8 * 3 * 2

    /** A bubble holding the longest phrase in the ten languages, and one holding a face. */
    private val phrase = 735
    private val face = 135

    // --- How wide each bubble is drawn ---------------------------------------------------

    @Test
    fun `a phrase with the other side silent gets the whole row`() {
        // The reported fault. A weight each split the row down the middle however little was in
        // either half, and "Bonne chance la prochaine fois" does not fit in half of it — so the
        // commonest case of all, one seat talking, ellipsized against empty space.
        val (rival, own) = chatBubbleWidths(space, rivalAsk = 0, ownAsk = phrase)

        assertEquals(0, rival)
        assertEquals(phrase, own)
        assertTrue("a lone phrase got no more than the old half", own > space / 2)
    }

    @Test
    fun `a face beside a phrase keeps its own width`() {
        // A glyph has nowhere to wrap to, so shrinking that side would clip it outright. The
        // phrase is the only one of the two with anything to give.
        val (rival, own) = chatBubbleWidths(space, rivalAsk = face, ownAsk = phrase * 2)

        assertEquals(face, rival)
        assertEquals(space - face, own)
    }

    @Test
    fun `two phrases at once share what there is`() {
        // The one case with no room to find. Half each is the fair answer, and both wrap.
        val (rival, own) = chatBubbleWidths(space, rivalAsk = phrase * 2, ownAsk = phrase * 2)

        assertEquals(space / 2, rival)
        assertEquals(space - space / 2, own)
    }

    @Test
    fun `neither side is ever given more of the row than there is`() {
        val asks = listOf(0, face, phrase, phrase * 4)
        asks.forEach { rivalAsk ->
            asks.forEach { ownAsk ->
                val (rival, own) = chatBubbleWidths(space, rivalAsk, ownAsk)
                assertTrue(
                    "$rivalAsk and $ownAsk were given $rival and $own out of $space",
                    rival >= 0 && own >= 0 && rival + own <= space,
                )
            }
        }
    }

    @Test
    fun `a row with no width to give gives none`() {
        assertEquals(0 to 0, chatBubbleWidths(available = 0, rivalAsk = phrase, ownAsk = phrase))
        assertEquals(0 to 0, chatBubbleWidths(space, rivalAsk = 0, ownAsk = 0))
    }

    // --- How tall the row reserves itself ------------------------------------------------

    @Test
    fun `the reserve holds two lines of a phrase at the default font scale`() {
        // Material's own line heights: 24 sp for the glyph, 20 sp for the label.
        assertTrue(
            "${chatRowHeight(24.dp, 20.dp)} cannot hold two 20 dp lines and 12 dp of padding",
            chatRowHeight(glyphLine = 24.dp, labelLine = 20.dp) >= 52.dp,
        )
    }

    @Test
    fun `the reserve grows with the font scale`() {
        // The reported fault: at the largest accessibility scale the bubble is nearly twice the
        // flat 48 dp the row used to be, and the parent clipped the words rather than showing
        // them — which is the very thing the words were added for.
        val doubled = chatRowHeight(glyphLine = 48.dp, labelLine = 40.dp)

        assertTrue("$doubled is no taller than the 48 dp that clipped", doubled > 48.dp)
        assertTrue("$doubled cannot hold two 40 dp lines and 12 dp of padding", doubled >= 92.dp)
    }

    @Test
    fun `the reserve never drops below the send button`() {
        // Type can be scaled down as well as up, and 48 dp is a touch target rather than a
        // measurement of anything: it does not follow the text either way.
        assertEquals(48.dp, chatRowHeight(glyphLine = 8.dp, labelLine = 6.dp))
    }

    // --- And the row still asks -----------------------------------------------------------

    @Test
    fun `the row still takes its width, its height and its way in from the rules above`() {
        val source = File("src/main/kotlin/com/duzman46/gridbound/ui/screens/GameScreen.kt")
        assertTrue("GameScreen.kt not found from ${File(".").absolutePath}", source.exists())
        val text = source.readText()

        assertTrue(
            "the bubbles are no longer measured for what they ask for",
            "chatBubbleWidths(" in text,
        )
        assertTrue(
            "the row's height is no longer the computed floor",
            "heightIn(min = chatRowHeight())" in text,
        )
        assertTrue(
            "the way into the mute is gated on being able to speak again",
            "enabled = state.canOpenMessages" in text,
        )
    }
}
