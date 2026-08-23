package com.duzman46.gridbound.ui.components.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.Palette

/**
 * The home screen's premium surfaces: the one control that is gold, the ones that are not, and
 * the press behaviour they share.
 *
 * The whole file follows one ration. Roughly a tenth of what the eye lands on is gold — the way
 * into a match, the active tab, two of seven icons, the dot on one letter. Everything else is
 * neutral on near-black, separated by a hairline rather than by colour. Spend gold on a second
 * card and the first one stops meaning "this is the way in"; spend it on all of them and the
 * screen is a casino.
 *
 * Press is a scale, not a sink. The rest of the app moves its controls two pixels down, which
 * is right for a chunky block and wrong for a large flat card — a card that drops looks like it
 * has come loose. 0.98 reads as pressure on glass, which is what these are.
 */

private const val PRESSED_SCALE = 0.98f

/**
 * How a card acknowledges a press: a hair smaller, quickly down and slowly back.
 *
 * **The scale must sit inside the click, never outside it.** Every surface in this file puts
 * `clickable` before `scale` in its modifier chain, which reads backwards and is the whole point:
 * a modifier listed earlier wraps the ones after it, so the touch target is the card at rest and
 * only the picture inside it shrinks.
 *
 * Written the natural way round — scale first, clickable after — the card that is being pressed
 * is also the card whose touch target is shrinking under the finger, by two percent, immediately,
 * in the same frame as the press. A finger anywhere near an edge ends up outside the target it
 * just landed on, Compose reads that as the pointer leaving, and the tap is cancelled with no
 * click. The player sees a card that flickered and did nothing, taps again more carefully, and
 * the second one works. It was every surface on the home screen, and it is why this comment is
 * longer than the function.
 */
@Composable
private fun pressScale(pressed: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 130, easing = LinearEasing),
        label = "pressScale",
    )
    return scale
}

/**
 * The press scale and the rounded corner, in one graphics layer instead of two.
 *
 * `Modifier.scale(…).clip(…)` reads well and costs double: each is a layer of its own, so every
 * card on the home screen was allocating and compositing two render targets where one would do.
 * Eight cards, sixteen layers, on a screen that also carries a full-screen photograph.
 *
 * A single `graphicsLayer` does both, because clipping to a shape is something a layer already
 * knows how to do.
 */
private fun Modifier.pressLayer(scale: Float, shape: Shape): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
    this.shape = shape
    clip = true
}

/** The play card's two fills. Built once each — they never change, and they are not cheap. */
private val PlayCardResting = Brush.linearGradient(
    colors = listOf(Palette.Card, Color(0xFF332816)),
    start = Offset.Zero,
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)
private val PlayCardPressed = Brush.linearGradient(
    colors = listOf(Palette.Ground, Color(0xFF291F11)),
    start = Offset.Zero,
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)

/** The leading play-mode card's fill, on the same terms. */
private val ModeCardLeading =
    Brush.horizontalGradient(listOf(Palette.Card, Palette.GoldBandEnd))

/**
 * The one way into a match.
 *
 * Everything about it is calibrated to be the loudest thing on the screen without being the
 * biggest or the brightest: it is the only surface carrying a warm fill, the only one with a
 * gold edge, and the only one whose label is set in caps. The fill is a very short sweep from
 * near-black to a dark brass — enough that the card is lit from one side like the scene above
 * it, far short of a gradient anyone would describe as a gradient.
 *
 * No glow. The depth comes from the border catching light along the top and the fill falling
 * away underneath it, which is how a real embossed panel behaves.
 */
@Composable
fun PrimaryPlayCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = pressScale(pressed)
    val shape = RoundedCornerShape(22.dp)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        modifier
            .fillMaxWidth()
            // Click outside the scale. See pressScale.
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$title. $subtitle" }
            .pressLayer(scale, shape)
            .background(if (pressed) PlayCardPressed else PlayCardResting)
            .border(BorderStroke(1.dp, Palette.Gold.copy(alpha = 0.6f)), shape)
            .heightIn(min = 92.dp)
            .padding(horizontal = 22.dp, vertical = Dimens.SpaceMd),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayTriangle(Modifier.size(30.dp), rtl)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = localeUpper(title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = localeUpper(subtitle),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.2.sp,
                    color = Palette.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A solid triangle. Drawn rather than imported so it carries no icon set's corner radius. */
@Composable
private fun PlayTriangle(modifier: Modifier = Modifier, rtl: Boolean) {
    // The same ink as the word beside it: the triangle and the title are one mark, and they
    // were two near-whites a hundredth of a ratio apart.
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f)) {
        val s = size.minDimension
        drawPath(
            Path().apply {
                moveTo(s * 0.16f, s * 0.06f)
                lineTo(s * 0.92f, s * 0.50f)
                lineTo(s * 0.16f, s * 0.94f)
                close()
            },
            ink,
        )
    }
}

/**
 * A destination in the grid under the play card.
 *
 * Two of the five wear gold and three do not, and which two is not a matter of taste: the
 * leaderboard and the day's objectives are the two places that tell a player they are getting
 * somewhere. Learning the rules and changing a setting are maintenance, and maintenance is
 * neutral.
 *
 * There is no tinted square behind the icon. A colour block per destination was the previous
 * design's idea and it is what made the screen read as a template — six saturated chips in a
 * grid is a launcher, not a game.
 */
@Composable
fun RowScope.HomeMenuCard(
    title: String,
    subtitle: String,
    icon: PremiumIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.weight(1f)) {
        MenuSurface(
            title = title,
            subtitle = subtitle,
            icon = icon,
            onClick = onClick,
            trailing = null,
            minHeight = 80.dp,
        )
    }
}

/** The full-width card under the grid — same material, one line wider, and it navigates. */
@Composable
fun WideMenuCard(
    title: String,
    subtitle: String,
    icon: PremiumIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MenuSurface(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
        trailing = { Chevron() },
        minHeight = 76.dp,
        modifier = modifier,
    )
}

@Composable
private fun MenuSurface(
    title: String,
    subtitle: String,
    icon: PremiumIcon,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
    minHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = pressScale(pressed)
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier
            .fillMaxWidth()
            // Click outside the scale. See pressScale.
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$title. $subtitle" }
            .pressLayer(scale, shape)
            .background(if (pressed) colors.surfaceVariant else colors.surface)
            .border(BorderStroke(1.dp, colors.outlineVariant), shape)
            .heightIn(min = minHeight)
            .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Every mark on this screen is gold, at the owner's call, and the ration moved rather
        // than went: what is scarce here is the *fill*. Exactly one card is warm and bordered in
        // brass — the way into a match — and everything else is a grey card with a gold mark on
        // it. Two of five marks being grey never said "these matter less"; it said the icon set
        // was inconsistent, because the two that were gold were gold for reasons no player could
        // read off the screen.
        PremiumGlyph(icon, Modifier.size(26.dp), tint = Palette.Gold)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                // The player's locale, not the invariant one: `uppercase()` alone turns the
                // Turkish "i" into "I" and the home screen read LIDERLIK.
                text = localeUpper(title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

/** The mark on a card that opens a screen. Thin, neutral, and never gold. */
@Composable
private fun Chevron() {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        Modifier
            .size(18.dp)
            .scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f),
    ) {
        val s = size.minDimension
        drawPath(
            Path().apply {
                moveTo(s * 0.38f, s * 0.22f)
                lineTo(s * 0.66f, s * 0.50f)
                lineTo(s * 0.38f, s * 0.78f)
            },
            Palette.InkGlyph,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = s * 0.11f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

/**
 * A mode on the play screen.
 *
 * Bigger than a menu card, and it earns the height: this is the one screen where the player is
 * choosing *what kind of game to have*, and the difference between playing a stranger, a machine
 * and the person sitting next to them is not something a two-word label conveys. So the icon is
 * large enough to be read as a picture rather than a mark, and the description gets two lines to
 * say what the mode actually is.
 *
 * A hairline rule stands between the icon and the text. It is the only divider in the app and it
 * is here because the icon is doing real work — without it the glyph reads as decoration stuck to
 * the title; with it the card reads as two things, what this is and what it means.
 *
 * [leading] wears the gold: border, icon and title. Exactly one card on the screen may have it,
 * and it belongs to online play — that is the mode the game is built around and the one a
 * returning player wants most of the time. A second gold card would make neither of them the
 * answer.
 */
@Composable
fun PlayModeCard(
    title: String,
    subtitle: String,
    icon: PremiumIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = pressScale(pressed)
    val shape = RoundedCornerShape(20.dp)
    val ink = if (leading) Palette.Gold else Palette.InkGlyph

    Row(
        modifier
            .fillMaxWidth()
            // Click outside the scale. See pressScale.
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$title. $subtitle" }
            .pressLayer(scale, shape)
            .background(
                if (leading) {
                    ModeCardLeading
                } else {
                    SolidColor(if (pressed) colors.surfaceVariant else colors.surface)
                },
            )
            .border(
                BorderStroke(1.dp, if (leading) Palette.Gold.copy(alpha = 0.6f) else colors.outlineVariant),
                shape,
            )
            .heightIn(min = 126.dp)
            .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PremiumGlyph(icon, Modifier.size(44.dp), tint = ink)
        Box(
            Modifier
                .height(54.dp)
                .width(Dimens.Hairline)
                .background(colors.outlineVariant),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (leading) Palette.Gold else colors.onSurface,
                // Two lines, because one is not enough for the honest name of this mode.
                // "Aynı Cihazda İki Oyuncu" truncated to "Aynı Cihazda İki O…", and shortening
                // the string would have cost the only thing it says — that both players are
                // here, on this phone. The card grows instead.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Chevron()
    }
}

/**
 * The bar across the top: who you are on one side, what you can adjust on the other.
 *
 * The player's name and rating lead because this is the one screen where the app addresses the
 * person rather than the game. The two round controls are deliberately identical and
 * deliberately quiet — they are not destinations, they are switches, and a switch that competes
 * with the play button has been given the wrong weight.
 */
@Composable
fun PremiumTopBar(
    initial: String,
    name: String,
    rating: String?,
    onProfile: () -> Unit,
    onLanguage: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    profileLabel: String,
    languageLabel: String,
    settingsLabel: String,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onProfile,
                )
                .semantics(mergeDescendants = true) { contentDescription = profileLabel },
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Palette.Card)
                    .border(BorderStroke(1.dp, Palette.Gold.copy(alpha = 0.55f)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rating != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Crown(Modifier.size(13.dp))
                        Text(
                            text = rating,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.InkMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
            RoundControl(PremiumIcon.LANGUAGE, languageLabel, onLanguage)
            RoundControl(PremiumIcon.COG, settingsLabel, onSettings)
        }
    }
}

/** A small brass crown. Five points, no gems — the rating is a rank, not a prize. */
@Composable
private fun Crown(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawPath(
            Path().apply {
                moveTo(0f, h * 0.18f)
                lineTo(w * 0.25f, h * 0.62f)
                lineTo(w * 0.5f, h * 0.10f)
                lineTo(w * 0.75f, h * 0.62f)
                lineTo(w, h * 0.18f)
                lineTo(w * 0.86f, h)
                lineTo(w * 0.14f, h)
                close()
            },
            Palette.Gold,
        )
    }
}

/** One of the two switches at the trailing edge. */
@Composable
private fun RoundControl(icon: PremiumIcon, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(46.dp)
            // Click outside the scale. See pressScale.
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .pressLayer(pressScale(pressed), CircleShape)
            .background(Palette.Card)
            .border(BorderStroke(1.dp, Palette.Edge), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        PremiumGlyph(icon, Modifier.size(21.dp), tint = Palette.Gold)
    }
}

/**
 * The docked bar at the foot of the screen.
 *
 * A floating container rather than a full-width strip: the strip is what every Material app
 * ships with, and the point of this screen is that it does not look like every Material app.
 * The active tab is marked by a short gold rule above it and by its label, not by a filled
 * pill — a pill is a button, and a tab is a place.
 */
@Composable
fun KoridorBottomBar(
    items: List<BottomItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Card)
            .border(BorderStroke(1.dp, Palette.Edge), shape)
            .heightIn(min = 66.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            BottomTab(item, index == selectedIndex, Modifier.weight(1f))
        }
    }
}

/** What one tab of the docked bar is made of. */
data class BottomItem(val label: String, val icon: PremiumIcon, val onClick: () -> Unit)

/**
 * One tab of the docked bar.
 *
 * The inactive ones are gold too, at just under half strength, rather than grey. Every mark on
 * this screen is gold now, and a grey row at the foot of it would be the one place the rule
 * broke — but a tab still has to say whether it is the place you are standing in, so the
 * difference is carried three ways at once: the rule above it, the weight of its label, and the
 * strength of the same colour rather than a different one.
 */
@Composable
private fun BottomTab(item: BottomItem, active: Boolean, modifier: Modifier) {
    val tint = if (active) Palette.Gold else Palette.Gold.copy(alpha = 0.45f)
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = item.onClick,
            )
            .padding(vertical = Dimens.SpaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .height(2.dp)
                .size(width = 22.dp, height = 2.dp)
                .background(if (active) Palette.Gold else Color.Transparent, CircleShape),
        )
        PremiumGlyph(item.icon, Modifier.size(22.dp), tint = tint)
        Text(
            text = localeUpper(item.label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 0.6.sp,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
