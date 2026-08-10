package com.duzman46.gridbound.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.AccentPalette
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.LocalKoridorColors
import com.duzman46.gridbound.ui.components.home.GlyphKind
import com.duzman46.gridbound.ui.components.home.KoridorGlyph

/**
 * The card system: a tinted glyph square, a title, and a line saying what the title means.
 *
 * [KoridorBlock] is still the app's button and nothing here replaces it — a button is one
 * label and one action, and adding a second line to it would have made every confirm dialog
 * two lines tall. A card is the other thing: a destination in a list of destinations, where
 * the player is choosing between options rather than agreeing to one.
 *
 * The second line is not decoration. A list of four bare labels makes the player open three of
 * them to find out what they are, and "Çevrim İçi" versus "Aynı Cihazda İki Oyuncu" is exactly
 * the distinction a first-time player cannot make from the name alone. Where there is nothing
 * true to say, pass no subtitle rather than inventing one — a card whose second line restates
 * its first is worse than a card with one line.
 */

/**
 * How tall a card's content is allowed to get down to, by how much it carries.
 *
 * [Compact] is the one that has to be derived rather than chosen. `heightIn` sits outside the
 * card's vertical padding, so a minimum below the content's own height never binds: a glyph
 * tile is [TileSize] and the padding adds `SpaceMd` twice, which is 68 dp before a single word
 * of the title is measured. Naming it 60 did not make the card 60 — it made the home screen's
 * budget wrong by eight. The other two sit above their content and bind normally.
 */
object CardHeight {
    /** Title only — the tile's own height plus its padding, which is the floor. */
    val Compact = TileSize + Dimens.SpaceMd * 2

    /** Title and subtitle — the default. */
    val Standard = 76.dp

    /** The one card a screen leads with. */
    val Feature = 92.dp
}

/** The tinted square a card is recognised by. Sized to hold [Dimens.GlyphMd] with room around it. */
internal val TileSize = 44.dp
private val FeatureTileSize = 52.dp

/**
 * A glyph on its destination's colour.
 *
 * Drawn as its own composable rather than inline in [KoridorCard] because the same square marks
 * the same destination in places that are not cards — a section header, a dialog, the bottom of
 * a settings row — and those have to agree with the cards or the colour stops being a name.
 */
@Composable
fun AccentTile(
    glyph: GlyphKind,
    tone: AccentPalette.Tone,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    inset: Boolean = false,
) {
    val side = if (large) FeatureTileSize else TileSize
    Box(
        // `modifier` first so a caller can fix the box's size — the home tiles run a smaller
        // square than a full-width card does, and a `size` written after this one would win
        // and undo them.
        modifier
            .then(if (inset) Modifier else Modifier.size(side))
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(tone.fill),
        contentAlignment = Alignment.Center,
    ) {
        KoridorGlyph(
            glyph,
            Modifier.size(
                when {
                    large -> Dimens.GlyphLg
                    inset -> Dimens.IconSm
                    else -> Dimens.GlyphMd
                },
            ),
            tint = tone.ink,
        )
    }
}

/**
 * A destination.
 *
 * Presses the same way [KoridorBlock] does — two pixels down, fast in and slow out — because a
 * card and a button on the same screen behaving differently reads as one of them being broken.
 * The travel moves placement rather than measurement, so a grid of these cannot reflow while
 * one is held.
 *
 * The whole row is one semantic node. Read as its parts a card announces a decorative square,
 * then a title, then a sentence, then a chevron, which is four stops on the way past something
 * that is one thing; [clearAndSetSemantics] collapses it to the sentence a screen reader user
 * actually needs.
 */
@Composable
fun KoridorCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    glyph: GlyphKind? = null,
    tone: AccentPalette.Tone? = null,
    feature: Boolean = false,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalKoridorColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sinks = pressed && enabled

    val travel by animateDpAsState(
        targetValue = if (sinks) Dimens.PressTravel else 0.dp,
        animationSpec = tween(durationMillis = if (pressed) 40 else 90, easing = LinearEasing),
        label = "cardTravel",
    )

    val shape = RoundedCornerShape(Dimens.RadiusMd)
    // A card is a surface, not an outline. The border is a hairline that only has to say where
    // the surface ends — at BorderStrong a grid of six cards becomes a grid of six frames.
    val fill = when {
        !enabled -> extra.disabledFill
        pressed -> colors.surfaceVariant
        else -> colors.surface
    }
    val ink = if (enabled) colors.onSurface else extra.disabledInk
    val minHeight = when {
        feature -> CardHeight.Feature
        subtitle != null -> CardHeight.Standard
        else -> CardHeight.Compact
    }
    val spoken = listOfNotNull(title, subtitle).joinToString(". ")

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .offset { IntOffset(x = 0, y = travel.roundToPx()) }
                .padding(bottom = Dimens.PressTravel)
                .fillMaxWidth()
                .clip(shape)
                .background(fill)
                // `outline`, not `outlineVariant`. The theme's own rule is that the quiet
                // token separates and the loud one identifies, and a card is identified by
                // nothing else: its fill is `surface`, which sits at 1.09:1 on the page in
                // dark and 1.11:1 in light. With the variant border the whole boundary
                // measured 2.02:1 and failed WCAG 1.4.11; `outline` puts it at 4.03:1. The
                // weight stays a hairline, so this is still a surface rather than a frame.
                .border(BorderStroke(Dimens.Hairline, colors.outline), shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                // Merged rather than cleared. `clearAndSetSemantics` would have taken the click
                // action out with the child text, leaving a card TalkBack can read and cannot
                // open; merging keeps the action and lets the description below replace the
                // four fragments the children would otherwise be announced as.
                .semantics(mergeDescendants = true) { contentDescription = spoken },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (glyph != null && tone != null) {
                    AccentTile(glyph, tone, large = feature)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = if (feature) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.Bold,
                        color = ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (enabled) colors.onSurfaceVariant else extra.disabledInk,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                trailing?.invoke()
            }
        }
    }
}

/**
 * The mark on a card that opens another screen.
 *
 * Drawn rather than imported, for the same reason the glyphs are: a stock Material chevron is a
 * thinner, rounder line than anything else on these screens and reads as pasted in. This one is
 * two wall pieces meeting at a point, in the wall's own cap.
 *
 * Present only on cards that *navigate*. A card that performs its action in place and stays
 * put must not carry one — a chevron promising a screen that never arrives is the cheapest way
 * to make an interface feel broken.
 */
@Composable
fun CardChevron(modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val mirror = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier
            .size(ChevronSize)
            .scale(scaleX = if (mirror) -1f else 1f, scaleY = 1f),
    ) {
        val s = size.minDimension
        val stroke = s * 0.14f
        val path = Path().apply {
            moveTo(s * 0.36f, s * 0.24f)
            lineTo(s * 0.64f, s * 0.50f)
            lineTo(s * 0.36f, s * 0.76f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private val ChevronSize = 20.dp

/**
 * The label above a group of cards.
 *
 * Small, wide-tracked and in the muted ink rather than the accent: a header that competes with
 * the cards under it is a header the eye has to get past. It exists to let the player skip a
 * whole group, which only works if it can be skipped.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(bottom = Dimens.SpaceSm),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One number and what it counts, as used in the profile's grid.
 *
 * The number leads and the label follows in the muted ink, because the player is scanning for
 * the figure and reads the word only when the figure surprises them.
 */
@Composable
fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasis: Color? = null,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = emphasis ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
