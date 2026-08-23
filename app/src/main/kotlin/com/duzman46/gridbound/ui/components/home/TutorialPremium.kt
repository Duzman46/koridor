package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.Palette

/**
 * The tutorial's parts, in the language the rest of the app now speaks.
 *
 * The screen they replace was Material — a `Surface` with tonal elevation, an `OutlinedButton` and
 * a `Button` — and it was one of the last two places still drawn that way. Nothing about the lesson
 * changed: the same seven steps, the same board, the same sentence per step. What changed is that
 * the card the lesson is delivered in now looks like the cards the rest of the app delivers
 * everything else in, which matters more here than anywhere: the tutorial is the first screen a new
 * player sees, and it was teaching them a visual vocabulary the app then stopped speaking.
 */



/**
 * The two things a step can say back to you, as colours.
 *
 * Gold is not available for either. It is the app's *action* colour — it is on the Next button and
 * on the card's own edge the moment a step is passed — so a gold sentence inside the card would be
 * the third gold thing appearing at once and none of the three would point anywhere. Red and green
 * are what the rest of the app already uses for a result that went against you and one that went
 * your way, on the profile's match list.
 */
internal val TutorialHintTone = Color(0xFFE2776C)
internal val TutorialSolvedTone = Color(0xFF4CC38A)

/**
 * Where you are in the lesson, as dots rather than a bar.
 *
 * Seven steps is few enough to count, and a dot per step says how many are left at a glance — a
 * progress bar only says "some".
 */
@Composable
fun TutorialStepDots(stepIndex: Int, stepCount: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(stepCount) { index ->
            Box(
                Modifier
                    .size(if (index == stepIndex) 10.dp else 7.dp)
                    .clip(CircleShape)
                    // A step still to come is InkGlyph, not the disabled ink: how many are
                    // left is the whole point of the row, so the dots have to be visible rather
                    // than merely present. 4.50:1 on the card, against a 3:1 floor for a mark.
                    .background(if (index <= stepIndex) Palette.Gold else Palette.InkGlyph),
            )
        }
    }
}

/**
 * The card the lesson is delivered in, floating over the board.
 *
 * [solved] lights the edge gold, so "you have done it" is visible from the edge of the card and not
 * only in the sentence inside it. It fires at the same moment the Next button goes live, which is
 * the point of doing it on the border rather than somewhere quieter: the edge and the button are
 * saying one thing.
 *
 * The shadow is the one piece of Material's card idiom worth keeping. Everywhere else in the app a
 * card sits on the page and the fill alone separates it; this one sits on the board, over squares
 * a player is being asked to tap, and without a cast shadow it reads as part of the board rather
 * than as something laid on top of it.
 */
@Composable
fun TutorialCard(
    solved: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.RadiusMd)
    Column(
        modifier
            .fillMaxWidth()
            .widthIn(max = TutorialCardMaxWidth)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(Palette.Card)
            .border(
                width = if (solved) Dimens.BorderStrong else Dimens.Hairline,
                color = if (solved) Palette.Gold else Palette.Edge,
                shape = shape,
            )
            .padding(Dimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        content = content,
    )
}

/** Tablets get a card, not a banner. */
private val TutorialCardMaxWidth = 620.dp

/** The step's count, its name and what it asks of you. */
@Composable
fun TutorialStepHeading(
    progress: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumGlyph(PremiumIcon.MORTARBOARD, Modifier.size(Dimens.IconSm), Palette.Gold)
            Text(
                text = progress,
                // Tabular figures: the count changes under the reader seven times and the title
                // below it should not shift sideways when "1" becomes "7".
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = Palette.Gold,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.InkMuted,
        )
    }
}

/**
 * A sentence the step adds while you are in it: the correction, or the confirmation.
 *
 * **The live region belongs to the component rather than to the caller.** Both of these appear
 * without anything being pressed, and a screen reader that is not told about them leaves a blind
 * player tapping a board that has already answered. Putting it here means the announcement cannot
 * be lost by a later caller who adds a third note and copies the wrong two lines.
 */
@Composable
fun TutorialNote(
    icon: PremiumIcon,
    tone: Color,
    text: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Dimens.RadiusSm)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tone.copy(alpha = 0.10f))
            .border(Dimens.Hairline, tone.copy(alpha = 0.30f), shape)
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PremiumGlyph(icon, Modifier.size(Dimens.IconSm), tone)
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = tone,
        )
    }
}
