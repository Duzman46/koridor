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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold
import com.duzman46.gridbound.theme.Palette

/**
 * A setting whose answer is a value rather than yes or no.
 *
 * There is exactly one — language — and there used to be two. The reference this screen was
 * measured against has four tiles across the top and two of the four are duplicates: a tile
 * called "Ses / Açık" over a switch called "Sesler", and a tile called "Arayüz / Sistem" beside
 * one called "Tema / Koyu". The rule that killed those also killed this component's sibling:
 * a tile earns its place only when the answer is one of several named things. The app is
 * dark-only now, so theme is not one of those, and the tile that offered it is gone rather than
 * reduced to a control with a single answer.
 *
 * **Which is why this is full width and takes no [androidx.compose.foundation.layout.RowScope].**
 * It was `RowScope.SettingsTile` with `weight(1f)`, because there were two of them side by side.
 * With one left, a half-width card with dead space beside it would be a visible hole where a
 * setting used to be — the interface admitting it had been edited. It keeps its 108 dp minimum
 * height, its glyph well and its value line; only its width changed.
 *
 * The current answer is on the face of the tile. A control that opens a chooser without saying
 * what it is currently set to makes the player open it to find out.
 */
@Composable
fun SettingsTile(
    icon: PremiumIcon,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Card)
            // InkGlyph rather than the decorative Edge: this hairline is the whole boundary of a
            // tappable card, so it is the affordance and has to clear 3:1 (4.50:1 on Card).
            .border(BorderStroke(1.dp, Palette.InkGlyph), shape)
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
                color = Palette.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
