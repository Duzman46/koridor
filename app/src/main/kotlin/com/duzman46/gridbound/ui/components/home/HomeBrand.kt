package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold
import java.util.Locale

/**
 * The name and the promise, under the picture.
 *
 * Both are centred, both are widely tracked, and both are light rather than heavy. A wordmark
 * set in Black at a tight track is a sports app; the same letters opened up and thinned read as
 * a title plate, which is what a strategy game wants. The letterform does the work, so nothing
 * else has to — no lockup, no badge, no rule under it.
 */

/**
 * Uppercase in the **player's** language, not in the invariant one.
 *
 * `String.uppercase()` with no argument uses the root locale, where `i` becomes `I`. In Turkish
 * it must become `İ`, and the app ships in Turkish: the home screen was reading LIDERLIK and
 * HEMEN BIR MAÇ BAŞLAT, which is not a style choice, it is misspelling the player's language on
 * the first screen they see. The same trap catches Azerbaijani, and Lithuanian in the other
 * direction, so this takes the configuration's locale rather than special-casing Turkish.
 */
@Composable
fun localeUpper(text: String): String {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        @Suppress("DEPRECATION")
        configuration.locales.takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault()
    }
    return remember(text, locale) { text.uppercase(locale) }
}

/**
 * KORİDOR, with the upright stroke of the name drawn as what it is.
 *
 * One letter in the word is already the shape of a placed wall, so it is given the brand's gold
 * while the rest stay bone-white. It is coloured rather than replaced: the string is still the
 * app's own name, so a screen reader, a search index and a language that does not have that
 * letter in that position all still get KORİDOR.
 *
 * The size is set in dp and converted, so the plate keeps a fixed cap height and grows by at
 * most a quarter at large font scales — seven wide-tracked glyphs cannot be allowed to wrap.
 */
@Composable
fun HomeWordmark(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val size = with(density) { 34.dp.toSp() } * density.fontScale.coerceAtMost(1.2f)
    val name = localeUpper(stringResource(R.string.app_name))
    val ink = MaterialTheme.colorScheme.onBackground
    // The upright letter is not a letter. It is a wall.
    //
    // Colouring the glyph gold was the half-measure: at this weight the typeface still drew a
    // letter, dot and all, and it read as a lowercase i dropped into a line of capitals. So the
    // letter is removed from the drawn text entirely and a wall piece is set in the gap it
    // leaves — a full-height bar in the jade-gold the brand uses, with the tittle above it kept
    // as a separate disc so the word is still legible as the name.
    //
    // The string is untouched: `app_name` is still KORİDOR for the launcher, the store, a screen
    // reader and search. This is a drawing of the name, not a renaming.
    val index = remember(name) { name.indexOfFirst { it == 'İ' || it == 'I' } }
    val head = remember(name, index) { if (index < 0) name else name.substring(0, index) }
    val tail = remember(name, index) { if (index < 0) "" else name.substring(index + 1) }

    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = name },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WordmarkText(head, size, ink)
        if (index >= 0) {
            WallLetter(size)
            WordmarkText(tail, size, ink)
        }
    }
}

/** One run of the wordmark's letters. */
@Composable
private fun WordmarkText(text: String, size: TextUnit, ink: Color) {
    if (text.isEmpty()) return
    Text(
        text = text,
        fontSize = size,
        // Light, not Black. The weight is what separates a title plate from a scoreboard.
        fontWeight = FontWeight.Light,
        letterSpacing = 10.sp,
        color = ink,
        lineHeight = size * 1.25f,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The wall standing where the letter was.
 *
 * Drawn to the cap height of the letters beside it rather than to a fixed size, so it holds the
 * line at every font scale. Fully rounded on its short axis, which is how a placed wall is drawn
 * on the board — this is the same object, seen face on.
 */
@Composable
private fun WallLetter(size: TextUnit) {
    val density = LocalDensity.current
    val cap = with(density) { size.toDp() } * 0.72f
    val bar = cap * 0.13f
    Canvas(
        Modifier
            .padding(horizontal = 5.dp)
            .size(width = bar * 2.4f, height = cap * 1.34f),
    ) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.42f
        // The tittle, kept so the word still reads as the name rather than as KOR|DOR.
        drawCircle(KoridorGold, radius = stroke * 0.5f, center = Offset(w / 2f, stroke * 0.5f))
        drawRoundRect(
            color = KoridorGold,
            topLeft = Offset((w - stroke) / 2f, h - cap.toPx()),
            size = Size(stroke, cap.toPx()),
            cornerRadius = CornerRadius(stroke / 2f),
        )
    }
}

/**
 * PLANLA. ENGELLE. KAZAN.
 *
 * The middle verb carries the gold because it is the one that is about the other player: you
 * plan for yourself and you win for yourself, but you block *someone*. That is the whole game
 * in one word, and lighting it is cheaper than a sentence explaining it.
 *
 * Split on whitespace rather than at a fixed offset — the line is three words in every language
 * it ships in, but they are not the same lengths, and an index would cut a Russian word in two.
 */
@Composable
fun HomeTagline(modifier: Modifier = Modifier) {
    val line = localeUpper(stringResource(R.string.home_tagline))
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val styled = remember(line) {
        val words = line.split(" ").filter { it.isNotBlank() }
        buildAnnotatedString {
            words.forEachIndexed { index, word ->
                if (index > 0) append(" ")
                val accented = words.size >= 3 && index == 1
                if (accented) {
                    withStyle(SpanStyle(color = KoridorGold)) { append(word) }
                } else {
                    append(word)
                }
            }
        }
    }
    Text(
        text = styled,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Normal,
        letterSpacing = 2.6.sp,
        color = muted,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The name and the line as one block, with the gap that binds them. */
@Composable
fun HomeBrand(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        HomeWordmark()
        HomeTagline()
    }
}
