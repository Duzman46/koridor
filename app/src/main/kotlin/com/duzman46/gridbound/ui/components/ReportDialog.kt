package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.duzman46.gridbound.R
import com.duzman46.gridbound.social.domain.ContentReportReason
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.Palette
import com.duzman46.gridbound.theme.KoridorGold
import com.duzman46.gridbound.ui.components.home.ChoiceRow
import com.duzman46.gridbound.ui.components.home.DialogCrest
import com.duzman46.gridbound.ui.components.home.GoldSubmit
import com.duzman46.gridbound.ui.components.home.OutlineAction
import com.duzman46.gridbound.ui.components.home.drawEllipsisMark
import com.duzman46.gridbound.ui.components.home.drawFoulMark
import com.duzman46.gridbound.ui.components.home.drawPersonMark
import com.duzman46.gridbound.ui.components.home.drawRoomCrest
import com.duzman46.gridbound.ui.components.home.drawShieldBadge

/**
 * Why the player is reporting, chosen from a list rather than typed.
 *
 * A box to type in would be free text about another player travelling to a node nobody is
 * moderating, which is the very thing being reported. The operator does not need a paragraph:
 * they need the account and the category, and both are here.
 *
 * One dialog for both places content a player typed is shown to a stranger — the name on
 * another player's page, and the room name in the browser — so the two say the same thing and
 * offer the same list.
 *
 * The reason is now chosen and then sent, rather than sent by the act of choosing. Four buttons
 * that each fired immediately meant a mis-tap was a filed report, on a screen whose whole point
 * is that filing one is a serious thing to do.
 *
 * @param subject the name being reported, shown so the player can see they have the right one.
 */
@Composable
fun ReportDialog(
    subject: String,
    onDismiss: () -> Unit,
    onReport: (ContentReportReason) -> Unit,
) {
    var chosen by rememberSaveable { mutableStateOf(ContentReportReason.OFFENSIVE_NAME) }
    val shape = RoundedCornerShape(26.dp)
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(BorderStroke(1.dp, KoridorGold.copy(alpha = 0.35f)), shape)
                    .padding(Dimens.SpaceLg)
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            ) {
                Text(
                    text = stringResource(R.string.report_title, subject),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = KoridorGold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.report_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.InkMuted,
                    textAlign = TextAlign.Center,
                )
                Column(
                    Modifier.fillMaxWidth().padding(top = Dimens.SpaceXs),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                ) {
                    ContentReportReason.entries.forEach { reason ->
                        ChoiceRow(
                            label = stringResource(reason.label),
                            chosen = reason == chosen,
                            onClick = { chosen = reason },
                            mark = reason.mark,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = Dimens.SpaceXs),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                ) {
                    OutlineAction(
                        label = stringResource(R.string.action_cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    GoldSubmit(
                        label = stringResource(R.string.report_action),
                        onClick = { onReport(chosen) },
                        modifier = Modifier.weight(1f),
                    )
                }
                PanelNote(stringResource(R.string.report_note))
            }
            DialogCrest(
                mark = { drawShieldBadge() },
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-32).dp),
            )
        }
    }
}

/** The quiet line at the foot of a dialog. */
@Composable
private fun PanelNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Palette.InkMuted,
        textAlign = TextAlign.Center,
    )
}

private val ContentReportReason.label: Int
    get() = when (this) {
        ContentReportReason.OFFENSIVE_NAME -> R.string.report_reason_name
        ContentReportReason.OFFENSIVE_ROOM_NAME -> R.string.report_reason_room_name
        ContentReportReason.CHEATING -> R.string.report_reason_cheating
        ContentReportReason.OTHER -> R.string.report_reason_other
    }

/**
 * A mark per reason.
 *
 * Four rows of identical text is four rows nobody reads to the end. A figure, a room, a piece
 * that does not belong to this game, and an ellipsis — each says its row before the words do.
 */
private val ContentReportReason.mark: DrawScope.() -> Unit
    get() = when (this) {
        ContentReportReason.OFFENSIVE_NAME -> { { drawPersonMark(KoridorGold) } }
        ContentReportReason.OFFENSIVE_ROOM_NAME -> { { drawRoomCrest(KoridorGold) } }
        ContentReportReason.CHEATING -> { { drawFoulMark(KoridorGold) } }
        ContentReportReason.OTHER -> { { drawEllipsisMark(KoridorGold) } }
    }
