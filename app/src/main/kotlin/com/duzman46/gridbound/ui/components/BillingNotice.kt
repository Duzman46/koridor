package com.duzman46.gridbound.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.monetization.domain.BillingMessage
import kotlinx.coroutines.delay

/** How long a notice stays up before taking itself down. */
private const val NOTICE_MILLIS = 4_000L

/**
 * What the store has to say, drawn over whatever the player is looking at.
 *
 * The billing layer has always produced these and nothing ever drew them, which made "restore
 * purchases" a button with no observable effect: a player whose purchase had genuinely been
 * restored and one whose tap did nothing at all saw exactly the same screen. It is hung beside
 * the navigation graph for the same reason [RequestBar] is — the answer arrives from Play
 * whenever Play feels like answering, and which screen the player is on by then cannot matter.
 *
 * Bottom of the window rather than top, so it never argues with the request bar for the same
 * strip of screen, and it dismisses itself: there is nothing here to act on.
 */
@Composable
fun BillingNotice(message: BillingMessage?, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        delay(NOTICE_MILLIS)
        onDismiss()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
            // Held after the message clears so the text does not blank out mid-fade.
            val shown = message ?: return@AnimatedVisibility
            Surface(
                shape = MaterialTheme.shapes.large,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
            ) {
                Text(
                    text = when (shown) {
                        is BillingMessage.Info -> shown.text
                        is BillingMessage.Error -> shown.text
                    }.asString(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = when (shown) {
                        is BillingMessage.Info -> MaterialTheme.colorScheme.onSurface
                        is BillingMessage.Error -> MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}
