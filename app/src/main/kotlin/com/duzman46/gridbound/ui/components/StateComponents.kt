package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.theme.Palette
import com.duzman46.gridbound.ui.components.home.PremiumActionButton
import com.duzman46.gridbound.ui.components.home.PremiumGlyph
import com.duzman46.gridbound.ui.components.home.PremiumIcon

/**
 * The loading, empty and error treatments every screen shares, so the states look and behave
 * the same everywhere instead of being reinvented per screen.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(color = Palette.Gold, strokeWidth = 2.dp)
            Text(
                stringResource(R.string.state_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkMuted,
            )
        }
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: PremiumIcon = PremiumIcon.INFO,
    title: String = stringResource(R.string.state_empty_title),
    /** Whatever the message tells the player to do, so the screen is not a dead end. */
    action: @Composable (() -> Unit)? = null,
) {
    InfoState(icon = icon, title = title, message = message, modifier = modifier, action = action)
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InfoState(
        icon = PremiumIcon.SHIELD_STAR,
        title = stringResource(R.string.state_error_title),
        message = message,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.error,
    ) {
        PremiumActionButton(
            label = stringResource(R.string.action_retry),
            onClick = onRetry,
            filled = true,
            icon = PremiumIcon.REPLAY,
        )
    }
}

/**
 * Inline validation or failure text under a form.
 *
 * Marked as a live region so TalkBack announces it the moment it appears. That matters more here
 * than anywhere else in the app: this is the only inline channel a refused password, a room that
 * would not open or a rematch that failed has, and without the live region a player who cannot
 * see the screen is told nothing at all — the text simply appears somewhere below the control
 * they are still standing on.
 *
 * Polite rather than assertive: it is an answer to something the player just did, not an
 * interruption, so it waits for whatever TalkBack is already saying to finish.
 */
@Composable
fun FormMessage(
    message: UiText,
    modifier: Modifier = Modifier,
    isError: Boolean = true,
) {
    Text(
        text = message.asString(),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

/**
 * The last Material button family in the app, and **neither of these has a call site.**
 *
 * Every submit in the app now draws
 * [com.duzman46.gridbound.ui.components.home.PremiumActionButton], which already had the
 * identical busy-spinner-replaces-the-mark behaviour and a better-documented reason for it — so
 * a form's button and the button on the screen either side of it stopped being two
 * near-identical things, which is exactly the seam an eye finds. [SecondarySubmitButton] never
 * had a call site at all: three screens imported it and none of them invoked it.
 *
 * They stay here for one reason and it is not a design one. A screen this change does not own
 * still carries dead `import` lines for both, and an import of a symbol that does not exist is
 * a compile error where an unused one is only a warning. Delete those imports in
 * `AccountScreen.kt` and both of these go, taking `Button` and `OutlinedButton` out of the
 * codebase with them.
 */
@Composable
fun SubmitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSubmitting: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled && !isSubmitting,
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(18.dp)
                    .clearAndSetSemantics { },
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null)
        }
        Text(text, Modifier.padding(start = if (isSubmitting || leadingIcon != null) 10.dp else 0.dp))
    }
}

@Composable
fun SecondarySubmitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSubmitting: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled && !isSubmitting,
    ) {
        if (leadingIcon != null) Icon(leadingIcon, contentDescription = null)
        Text(text, Modifier.padding(start = if (leadingIcon != null) 10.dp else 0.dp))
    }
}

/**
 * The shared body of [EmptyState] and [ErrorState].
 *
 * The mark is a [PremiumIcon] rather than a Material `ImageVector`, and that is the whole of
 * what changed here. These three states are where a player lands when a leaderboard is empty or
 * a lobby will not load — inside screens of gold-edged cards — and they were answering with
 * Material's inbox tray, Material's outlined error circle and a Material filled button. Three
 * borrowed shapes in the one place the app has nothing else to show.
 */
@Composable
private fun InfoState(
    icon: PremiumIcon,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tint: Color = Palette.InkGlyph,
    action: @Composable (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PremiumGlyph(icon, Modifier.size(44.dp), tint)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkMuted,
                textAlign = TextAlign.Center,
            )
            action?.invoke()
        }
    }
}
