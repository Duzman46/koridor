package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.duzman46.gridbound.theme.Dimens

/**
 * The home screen's two-by-two block of destinations.
 *
 * A stacked column of four full-width rows reads as a ranking, and these four are not ranked —
 * whichever the player wants, they want it as much as the other three. Side by side they are
 * peers, and the pair of rows costs roughly half the height four rows did, which is the room
 * the second line of each tile is paid for with.
 *
 * This file held a tile component and a measured height token for it once. Both went with the
 * Material-era card family they were sized against, and nothing had called either for a
 * redesign: the tiles the grid actually draws are MenuScreens' own, and they size themselves
 * from their content rather than from a number two files had to agree on. What is left is the
 * row, which is the only part the grid ever needed from here — a pair of peers that measure the
 * same height.
 */

/** Two tiles that share a row and always measure the same height. */
@Composable
fun HomeGridRow(
    left: @Composable RowScope.() -> Unit,
    right: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        left()
        right()
    }
}
