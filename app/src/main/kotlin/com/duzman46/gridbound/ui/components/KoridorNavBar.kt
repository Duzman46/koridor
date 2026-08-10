package com.duzman46.gridbound.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.home.GlyphKind
import com.duzman46.gridbound.ui.components.home.KoridorGlyph

/**
 * The three places the app always lets you get back to.
 *
 * Before this the only way home was the back arrow, which meant a player three screens deep in
 * settings had to guess how many times to press it. A bar fixes the depth problem, but only if
 * it is honest about what it contains: three destinations, each of which is a *place* rather
 * than an action. Starting a match is not here — it is the loudest control on the home screen
 * and does not need a second entrance — and neither is anything that opens a dialog.
 *
 * Three and not five. Every extra tab is a permanent strip of the screen spent on something
 * most sessions never touch, and this app has exactly three things a player returns to: where
 * they start from, what they have played, and who they are.
 */
enum class NavTab(val route: String) {
    /** Where a session starts. */
    HOME("home"),

    /** Matches: the modes, and the history of what was played. */
    GAMES("play"),

    /** The player's own things — profile, stats, friends, settings, everything else. */
    PROFILE("profile"),
}

private val BarHeight = 64.dp

/**
 * The bar itself.
 *
 * The selected tab is marked twice — filled glyph tint and a bolder label — because on a dark
 * ground a single tint change between two greens is not a difference anyone notices in
 * peripheral vision, which is the only vision a navigation bar ever gets.
 *
 * No ripple and no indicator pill. The app's controls acknowledge a press by moving or by
 * changing colour, and a pill sliding under a glyph is a third idiom that belongs to a
 * different design language than the one the cards are written in.
 */
@Composable
fun KoridorNavBar(
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Dimens.Hairline)
                .background(colors.outlineVariant),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = BarHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.forEach { tab ->
                NavBarItem(
                    tab = tab,
                    active = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    tab: NavTab,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        targetValue = if (active) colors.primary else colors.onSurfaceVariant,
        animationSpec = tween(durationMillis = 120),
        label = "navTint",
    )
    val label = stringResource(tab.labelRes())
    Column(
        modifier
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = Dimens.SpaceSm, horizontal = Dimens.SpaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        KoridorGlyph(tab.glyph(), Modifier.size(Dimens.IconSm), tint = tint)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun NavTab.glyph(): GlyphKind = when (this) {
    NavTab.HOME -> GlyphKind.HOME
    NavTab.GAMES -> GlyphKind.MODES
    NavTab.PROFILE -> GlyphKind.PROFILE
}

private fun NavTab.labelRes(): Int = when (this) {
    NavTab.HOME -> R.string.nav_home
    NavTab.GAMES -> R.string.nav_games
    NavTab.PROFILE -> R.string.nav_profile
}
