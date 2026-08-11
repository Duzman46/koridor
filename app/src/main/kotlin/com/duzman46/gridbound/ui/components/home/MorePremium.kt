package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold

/**
 * "More", as a screen made of two cards.
 *
 * It was a bottom sheet, which is the wrong container for it: a sheet is for a decision taken in
 * passing, and this is a place — the drawer holding everything a player looks for by name rather
 * than reaches for by habit. A sheet also cannot be arrived at, which meant the version number
 * and the privacy policy lived somewhere with no address.
 *
 * Rows are grouped into cards rather than separated by headings. A heading over three entries is
 * a label for something that needs none; the card edge says the same thing without adding a word
 * to a screen that is already a list of words. The rows themselves are [OptionGroup], shared with
 * Settings — see there for why they are one component.
 */

/**
 * The one offer on the screen.
 *
 * It carries the only warm fill and the only gold control here, for the same reason the play card
 * does on the home screen: a screen where everything is emphasised has emphasised nothing. It is
 * not shown at all once the upgrade is owned — an offer you have already taken is not an offer.
 */
@Composable
fun SupportCard(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF1B1710), Color(0xFF12161B))))
            .border(BorderStroke(1.dp, Color(0xFF8D713B)), shape)
            .heightIn(min = 96.dp)
            .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(40.dp)) { drawRoomCrest() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8B9098),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // The button sits under the sentence rather than beside it. Beside it, on a phone,
            // "Reklamları Kaldır" and "Reklamları kaldırarak kesintisiz bir deneyim yaşa" were
            // sharing one line and clipping each other; the offer and the reason for it are two
            // lines of the same argument, not two columns.
            CompactOutline(
                label = action,
                onClick = onAction,
                modifier = Modifier.padding(top = Dimens.SpaceXs),
            )
        }
    }
}

/**
 * An outlined gold control at the size a card can spare.
 *
 * The form version of this is fifty-six device-independent pixels tall, which is right at the
 * bottom of a page and wrong inside a card that also has to hold a title and a sentence.
 */
@Composable
private fun CompactOutline(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier
            .clip(shape)
            .border(BorderStroke(1.dp, KoridorGold.copy(alpha = 0.6f)), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .heightIn(min = 42.dp)
            .padding(horizontal = Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = KoridorGold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OptionChevron(KoridorGold)
    }
}
