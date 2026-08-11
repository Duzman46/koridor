package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold

/**
 * A setting whose answer is a value rather than yes or no.
 *
 * Two of them, side by side, and only two: the reference has four across the top and two of the
 * four are duplicates — a tile called "Ses / Açık" over a switch called "Sesler", and a tile
 * called "Arayüz / Sistem" beside one called "Tema / Koyu". Language and theme are the only
 * settings in this app whose answer is one of several named things, so they are the only tiles.
 *
 * The current answer is on the face of the tile. A control that opens a chooser without saying
 * what it is currently set to makes the player open it to find out.
 */
@Composable
fun RowScope.SettingsTile(
    icon: PremiumIcon,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .weight(1f)
            .clip(shape)
            .background(Color(0xFF12161B))
            .border(BorderStroke(1.dp, FieldEdge), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$label. $value" }
            .heightIn(min = 108.dp)
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(KoridorGold.copy(alpha = 0.10f))
                    .border(BorderStroke(1.dp, KoridorGold.copy(alpha = 0.45f)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PremiumGlyph(icon, Modifier.size(20.dp), tint = KoridorGold)
            }
            Box(Modifier.weight(1f))
            OptionChevron()
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7A7F86),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The theme chooser.
 *
 * A dialog rather than the full screen the language picker takes, and the difference is the list:
 * eleven languages is something a player reads, three themes is something they glance at.
 */
@Composable
fun ThemePickerDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xFF12161B))
                .border(BorderStroke(1.dp, FieldEdge), shape)
                .padding(Dimens.SpaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            DialogCrest(mark = { drawPremiumIcon(PremiumIcon.CONTRAST, KoridorGold) })
            Text(
                text = title,
                modifier = Modifier.padding(vertical = Dimens.SpaceXs),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // No mark beside the three. "Sistem", "Açık" and "Koyu" are one word each and the
            // ring at the trailing edge already says which is chosen; a sun and a moon here
            // would be decoration on a list that is three words long.
            options.forEachIndexed { index, label ->
                ChoiceRow(
                    label = label,
                    chosen = index == selected,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}
