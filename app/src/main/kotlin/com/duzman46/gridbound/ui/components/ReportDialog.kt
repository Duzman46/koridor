package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.social.domain.ContentReportReason

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
 * @param subject the name being reported, shown so the player can see they have the right one.
 */
@Composable
fun ReportDialog(
    subject: String,
    onDismiss: () -> Unit,
    onReport: (ContentReportReason) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title, subject)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.report_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ContentReportReason.entries.forEach { reason ->
                    TextButton(
                        onClick = { onReport(reason) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(reason.label),
                            Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val ContentReportReason.label: Int
    get() = when (this) {
        ContentReportReason.OFFENSIVE_NAME -> R.string.report_reason_name
        ContentReportReason.OFFENSIVE_ROOM_NAME -> R.string.report_reason_room_name
        ContentReportReason.CHEATING -> R.string.report_reason_cheating
        ContentReportReason.OTHER -> R.string.report_reason_other
    }
