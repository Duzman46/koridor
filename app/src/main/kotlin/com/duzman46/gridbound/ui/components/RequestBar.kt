package com.duzman46.gridbound.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.presentation.social.RequestBarEvent
import com.duzman46.gridbound.presentation.social.RequestChannelViewModel
import com.duzman46.gridbound.social.domain.PlayerRequest
import com.duzman46.gridbound.social.domain.RequestKind
import androidx.compose.foundation.BorderStroke
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.Palette
import com.duzman46.gridbound.ui.util.rememberMotionEnabled

/**
 * The live request channel, drawn over whatever the player happens to be looking at.
 *
 * An invitation from a friend and a rematch from the opponent you have just finished with are
 * the same event as far as the player is concerned — somebody is asking, and the answer is yes
 * or no — so they share one bar rather than each growing their own notification.
 *
 * **Why it is hung above the navigation graph.** A request arrives while the player is
 * somewhere else, and which screen that is cannot matter. Sitting beside the NavHost rather
 * than inside a screen means the channel is listened to once for the whole app, survives every
 * navigation, and cannot be missed because the only screen that was watching had just been
 * replaced.
 *
 * **Why it starts a top app bar below the window.** Screens that have a top app bar keep their
 * back button in its leading corner, and a bar pinned flush to the top would land on it —
 * trapping the player behind a notice they were entitled to ignore. Clearing the status bar
 * and one [Dimens.TopBarClearance] puts it under the back button where there is one, and reads
 * as a card hanging from the top edge where there is not.
 *
 * **Why it cannot steal a tap.** Everything around the card is empty layout that registers no
 * pointer input at all, so every touch outside the bar reaches the screen underneath
 * untouched. The card itself swallows the ones that land on it, because a tap on a bar is not
 * a tap on whatever it is covering.
 */
@Composable
fun RequestBar(
    onOpenGame: (OnlineSession) -> Unit,
    onOpenLobby: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequestChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RequestBarEvent.OpenGame -> onOpenGame(event.session)
                is RequestBarEvent.OpenLobby -> onOpenLobby(event.roomCode)
            }
        }
    }

    // The last question outlives the answer to it, so the bar leaves with the words it arrived
    // with rather than emptying itself and then sliding a blank card off the top.
    var answered by remember { mutableStateOf<PlayerRequest?>(null) }
    LaunchedEffect(state.request) { state.request?.let { answered = it } }
    val shown = state.request ?: answered

    val motionEnabled = rememberMotionEnabled()
    AnimatedVisibility(
        visible = state.request != null,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = Dimens.TopBarClearance),
        enter = if (motionEnabled) slideInVertically { -it } + fadeIn() else EnterTransition.None,
        exit = if (motionEnabled) slideOutVertically { -it } + fadeOut() else ExitTransition.None,
    ) {
        shown?.let { request ->
            RequestCard(
                request = request,
                message = state.message?.asString(),
                isAnswering = state.isAnswering,
                onAccept = viewModel::accept,
                onDecline = viewModel::decline,
            )
        }
    }
}

@Composable
private fun RequestCard(
    request: PlayerRequest,
    message: String?,
    isAnswering: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = Dimens.SpaceMd)
            .fillMaxWidth()
            // Consumes taps inside the card and nowhere else.
            .pointerInput(Unit) { detectTapGestures { } }
            // The bar appears without the player having asked for it, so a screen reader is
            // told about it the moment it does rather than when focus happens to reach it.
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(Dimens.RadiusMd),
        // The card is the app's card, and a failed answer is said in the border and the second
        // line rather than by repainting the whole bar.
        //
        // It used to take `primaryContainer` and `errorContainer`, neither of which this app
        // sets — so every match invitation, over every screen, arrived as Material's baseline
        // violet `#4F378B`, and a failure turned it crimson `#8C1D18`. Two hues from a palette
        // Koridor does not use, on the one surface that appears unbidden on top of whatever the
        // player was already looking at.
        color = Palette.Card,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            Dimens.Hairline,
            if (message == null) Palette.Gold.copy(alpha = 0.45f) else MaterialTheme.colorScheme.error,
        ),
        shadowElevation = 6.dp,
    ) {
        Row(
            // Tighter on the trailing edge: the two icon buttons carry their own padding.
            Modifier.padding(
                start = Dimens.SpaceLg,
                end = Dimens.SpaceSm,
                top = Dimens.SpaceSm,
                bottom = Dimens.SpaceSm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (request.kind == RequestKind.REMATCH) {
                            R.string.requests_rematch_from
                        } else {
                            R.string.invites_from
                        },
                        // A room that arrives before its sender's profile does still says
                        // something the player can act on.
                        request.fromUsername.ifBlank { request.roomCode },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (isAnswering) {
                CircularProgressIndicator(
                    Modifier.size(Dimens.IconSm),
                    color = Palette.Gold,
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onAccept) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.friends_accept),
                        tint = Palette.Gold,
                    )
                }
                IconButton(onClick = onDecline) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.friends_decline),
                        tint = Palette.InkGlyph,
                    )
                }
            }
        }
    }
}
