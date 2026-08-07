package com.duzman46.gridbound.ui.screens

import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.home.heroHeight
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Holds the home screen to one window.
 *
 * The first screen anyone opens has to show the way in without being scrolled to, and it is
 * one row away from not doing that: five controls, a wordmark, a utility row, an ad banner and
 * a board panel on a phone. Nothing about that is visible from reading the layout, and it is
 * only ever noticed on a device — so it is asserted here instead, against the same constants
 * the screen is built from.
 */
class HomeLayoutBudgetTest {

    /** The smallest content area a current phone gives an app, once the system bars are out. */
    private val contentHeight = 740.dp
    private val contentWidth = 360.dp
    private val column = contentWidth - Dimens.ScreenPadding * 2

    /**
     * Anchored adaptive banners are 15% of the display height, floored at 50 dp and capped at
     * 90, so both ends of that have to fit rather than the comfortable one.
     */
    private val bannerHeights = listOf(50.dp, 90.dp)

    @Test
    fun `the whole home screen fits a phone without scrolling`() {
        bannerHeights.forEach { banner ->
            val panel = heroHeight(contentHeight - banner - HomeChrome, column)
            val total = HomeChrome + panel + banner
            assertTrue(
                "the home screen needs $total behind a $banner banner and only has $contentHeight",
                total <= contentHeight,
            )
        }
    }

    @Test
    fun `the panel keeps a board worth looking at`() {
        bannerHeights.forEach { banner ->
            val panel = heroHeight(contentHeight - banner - HomeChrome, column)
            // The board fills all but a few percent of the panel's height, so the panel's
            // height is the board's side. Under 128 dp that is nine ranks of fourteen, which
            // is a diagram of a board rather than a board with two pawns on it.
            assertTrue(
                "a $panel panel behind a $banner banner leaves the board unreadable",
                panel >= column / 2.5f,
            )
        }
    }
}
