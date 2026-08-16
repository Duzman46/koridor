package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold

/**
 * The end of a match, as one object standing on an empty field.
 *
 * Every other screen in the app is a list of cards and reads as a place. This one is a single
 * card in the middle of nothing, because it is not a place — it is a sentence, and three ways out
 * of it. So the whole treatment is spent on the one card: the wider corner the app keeps for the
 * pieces you look at rather than scroll past, and the only warm fill on the screen.
 *
 * **The warmth belongs to the win, and only to the win.** A gold-edged card under "the move clock
 * ran out" is the container congratulating a player the words are commiserating with — the same
 * mistake the confetti made before it was taken off the losing branch. On a defeat the card falls
 * back to the neutral fill every list card uses, and nothing on the screen is gold.
 */

/**
 * The hero card, and the only container this screen has.
 *
 * `SupportCard` on the More screen is the precedent for the warm one: a gradient that is barely
 * there, a gold edge, and everything around it left plain so the warmth means something.
 */
@Composable
fun WinnerPanel(
    lost: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.RadiusXl)
    Column(
        modifier
            .clip(shape)
            .background(if (lost) DefeatFill else VictoryFill)
            .border(
                BorderStroke(
                    if (lost) Dimens.Hairline else Dimens.BorderStrong,
                    if (lost) FieldEdge else KoridorGold.copy(alpha = 0.35f),
                ),
                shape,
            )
            .padding(Dimens.SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        content = content,
    )
}

/**
 * The mark at the top of the card: a trophy for the winner, a gamepad for everyone else.
 *
 * It sits on a disc rather than floating, which is the same medallion the badge shelf uses — a
 * drawn glyph with nothing behind it reads as a sticker at this size, and this one is 104 device
 * -independent pixels across.
 *
 * @param pulse what to scale the disc by. Pass 1f to hold it still; the caller owns the animation
 *   because the caller is the one that knows a defeat must not breathe.
 */
@Composable
fun WinnerMark(lost: Boolean, pulse: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(MarkSize)
            .scale(pulse)
            .clip(CircleShape)
            // The defeat disc is the app's recessed fill on purpose: the gamepad punches its pad
            // and its buttons out in near-black, and any lighter ground shows them as grey chips.
            .background(if (lost) Color(0xFF0E1216) else KoridorGold.copy(alpha = 0.12f))
            .border(
                BorderStroke(
                    if (lost) Dimens.Hairline else Dimens.BorderStrong,
                    if (lost) FieldEdge else KoridorGold.copy(alpha = 0.55f),
                ),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PremiumGlyph(
            if (lost) PremiumIcon.GAMEPAD else PremiumIcon.TROPHY,
            Modifier.size(MarkGlyphSize),
            tint = if (lost) Color(0xFF8B9098) else KoridorGold,
        )
    }
}

/**
 * What happened, and why.
 *
 * Two lines rather than one component each, because they are read as a single statement and the
 * gap between them is part of it: tighter than anything else on the card, so the reason reads as
 * belonging to the verdict above it rather than as the next item down.
 *
 * `headlineMedium`, not the display sizes. The verdict is a full sentence in ten languages and
 * two of them are compound German nouns; a size that fits "Kazandın!" and breaks "Gegner hat
 * gewonnen" over three lines is a size chosen for one locale.
 */
@Composable
fun WinnerHeadline(
    title: String,
    subtitle: String,
    lost: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (lost) Color(0xFFF2F3F5) else KoridorGold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8B9098),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A rematch that has been asked for, or refused, standing where its button stood.
 *
 * It keeps the button's height and the button's corner because it occupies the button's slot:
 * the offer goes out, the control it was made with becomes a line of text, and if that line were
 * a bare sentence the card would shrink under the player's finger and everything below it would
 * jump. The fill is the recessed one, which is the app's way of saying a thing is not pressable.
 *
 * @param busy whether the answer is still coming. The spinner is the whole difference between
 *   "waiting" and "declined" once the words have been read.
 */
@Composable
fun WinnerStatusStrip(
    label: String,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ActionRadius)
    Row(
        modifier
            .clip(shape)
            .background(Color(0xFF0E1216))
            .border(BorderStroke(Dimens.Hairline, Color(0xFF20262D)), shape)
            // A floor, not a height: "your rival declined the rematch" is two lines in several
            // of the languages the app ships in.
            .heightIn(min = ActionHeight)
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(SpinnerSize).clearAndSetSemantics { },
                color = KoridorGold,
                strokeWidth = 2.dp,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8B9098),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The celebration, and the one thing on this screen that is not in the app's palette.
 *
 * Five colours nobody chose from the theme, because confetti is not a component: paper thrown in
 * the air is not gold, and a shower of the brand colour reads as a rendering fault rather than as
 * a party. It stays deliberately multicoloured and is not theme-bound.
 *
 * The layout is arithmetic on the particle's index rather than a random source, so the same frame
 * of the same cycle draws the same picture on every recomposition — a `Random` here would make
 * the whole field jump each time the card above it changed a word.
 */
@Composable
fun WinnerConfetti(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        repeat(Constants.Animation.CONFETTI_PARTICLE_COUNT) { index ->
            val xFraction = ((index * 37) % 101) / 100f
            val phase = ((index * 19) % Constants.Animation.CONFETTI_PARTICLE_COUNT).toFloat() /
                Constants.Animation.CONFETTI_PARTICLE_COUNT
            val yFraction = (progress + phase) % 1f
            val drift = kotlin.math.sin((progress + phase) * Math.PI * 2).toFloat() * 18f
            drawCircle(
                color = ConfettiColours[index % ConfettiColours.size].copy(alpha = 0.78f),
                radius = 3f + (index % 4),
                center = Offset(xFraction * size.width + drift, yFraction * size.height),
            )
        }
    }
}

/** Barely a gradient. Enough for the card to catch a little light along its top edge. */
private val VictoryFill = Brush.verticalGradient(listOf(Color(0xFF1B1710), Color(0xFF12161B)))
private val DefeatFill = SolidColor(Color(0xFF12161B))

private val ConfettiColours = listOf(
    Color(0xFF32D583),
    Color(0xFFFFC857),
    Color(0xFF4D8DFF),
    Color(0xFFFF6B6B),
    Color(0xFFB56CFF),
)

private val MarkSize = 104.dp
private val MarkGlyphSize = 48.dp
private val SpinnerSize = 18.dp
