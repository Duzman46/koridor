package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.core.asString

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
            CircularProgressIndicator()
            Text(
                stringResource(R.string.state_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Inbox,
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
        icon = Icons.Rounded.ErrorOutline,
        title = stringResource(R.string.state_error_title),
        message = message,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.error,
    ) {
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
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
 * A primary button that shows progress and refuses further taps while busy, which is how
 * every submit in the app avoids sending a duplicate request.
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

@Composable
private fun InfoState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: @Composable (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp), tint = tint)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            action?.invoke()
        }
    }
}
