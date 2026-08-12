package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold

/**
 * The statistics screen's parts, in the premium language the rest of the app now speaks.
 *
 * The screen it replaces was Material — `StatCard`, `ListItem`, `SmartToy` — and was the last
 * place in the app still drawing a vendor icon.
 *
 * **What changed underneath is not cosmetic.** The three figures at the top used to be every
 * match this handset had ever seen: practice against the bot and two people passing one phone
 * back and forth, added together with ranked play into one win rate. That number answered no
 * question anybody has. The top of the screen is the *online* record now, and the bot and the
 * local games are further down, in the sections that are about them.
 */

private val CardFill = Color(0xFF12161B)
private val Muted = Color(0xFF8B9098)
private val Hairline = Color(0xFF20262D)

/** The four bot levels, warmest to hottest, so the row reads as a ladder. */
internal val DifficultyTones = listOf(
    Color(0xFF4CC38A),
    Color(0xFFD0A653),
    Color(0xFFDE8B4A),
    Color(0xFFE2776C),
)

/**
 * The page's title, with its last word in gold.
 *
 * Split on whitespace and coloured by position rather than by a marked-up string, so the ten
 * translations need no markup and a language whose phrase is one word simply gets no accent
 * instead of a broken one.
 */
@Composable
fun StatsTitle(title: String, note: String, modifier: Modifier = Modifier) {
    val words = title.trim().split(" ").filter { it.isNotBlank() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        Text(
            text = buildAnnotatedString {
                words.forEachIndexed { index, word ->
                    if (index > 0) append(" ")
                    if (words.size > 1 && index == words.lastIndex) {
                        withStyle(SpanStyle(color = KoridorGold)) { append(word) }
                    } else {
                        append(word)
                    }
                }
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
    }
}

/** One of the three figures across the top. */
@Composable
fun RowScope.StatsHeadlineCard(icon: PremiumIcon, value: String, label: String) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(CardFill)
            .border(1.dp, Hairline, RoundedCornerShape(Dimens.RadiusMd))
            .padding(vertical = Dimens.SpaceLg, horizontal = Dimens.SpaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        PremiumGlyph(icon, Modifier.size(Dimens.GlyphMd), KoridorGold)
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A titled card, which every block on this screen is. */
@Composable
fun StatsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(CardFill)
            .border(1.dp, Hairline, RoundedCornerShape(Dimens.RadiusMd))
            .padding(Dimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        content()
    }
}

/** A mark, a name and a number: the row every detail on this screen is made of. */
@Composable
fun StatsLine(icon: PremiumIcon, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PremiumGlyph(icon, Modifier.size(Dimens.IconSm), KoridorGold.copy(alpha = 0.85f))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
    }
}

/** The win-rate ring beside the details. */
@Composable
fun StatsDonut(fraction: Float, caption: String, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(112.dp)) {
                val stroke = size.minDimension * 0.11f
                val inset = stroke / 2f
                val box = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = Hairline,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = box,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                // Zero draws nothing rather than a dot: a rounded cap on a zero-length arc is a
                // pip at twelve o'clock, which reads as a sliver of progress that is not there.
                if (fraction > 0f) {
                    drawArc(
                        color = KoridorGold,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = box,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                text = caption,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** One bot level and what it has cost or paid. */
@Composable
fun RowScope.DifficultyChip(tone: Color, label: String, record: String) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(tone.copy(alpha = 0.10f))
            .border(1.dp, tone.copy(alpha = 0.30f), RoundedCornerShape(Dimens.RadiusSm))
            .padding(vertical = Dimens.SpaceMd, horizontal = Dimens.SpaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        PremiumGlyph(PremiumIcon.ROBOT, Modifier.size(Dimens.IconSm), tone)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = tone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = record,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = Muted,
            maxLines = 1,
        )
    }
}

/** The sentence at the foot of the screen that says what the figures above do and do not count. */
@Composable
fun StatsNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(Color(0xFF0E1216))
            .border(1.dp, Hairline, RoundedCornerShape(Dimens.RadiusSm))
            .padding(Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PremiumGlyph(PremiumIcon.INFO, Modifier.size(Dimens.IconSm), Muted)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A rule between rows inside a card. */
@Composable
fun StatsDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hairline))
}
