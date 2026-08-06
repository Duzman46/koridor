package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.theme.Dimens

/**
 * The sheet the four home buttons open onto.
 *
 * Everything that used to be a button on the home screen lives in one of these instead. A
 * home screen is a place to decide what to do, not a list of everything the app can do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            Text(
                title,
                Modifier.padding(bottom = Dimens.SpaceSm),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

/**
 * One choice inside a sheet.
 *
 * Full width, one line of label, a glyph, and nothing else — no supporting paragraph. The
 * label is the explanation; if a choice needs a paragraph, it is the wrong choice to offer.
 */
@Composable
fun SheetAction(
    label: String,
    glyph: GlyphKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.RoomHeight),
        shape = RoundedCornerShape(Dimens.RadiusXs),
        color = if (emphasised) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            Modifier.padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (emphasised) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            KoridorGlyph(glyph, Modifier.size(Dimens.GlyphMd), tint = tint)
            Text(
                label,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A plain text row for the low-traffic entries in "More". */
@Composable
fun SheetLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.ChipHeight),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(vertical = Dimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SheetDivider() {
    HorizontalDivider(
        Modifier.padding(vertical = Dimens.SpaceXs),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
    )
}

/** A labelled row of mutually exclusive choices, used by the custom-game sheet. */
@Composable
fun <T> SheetChoice(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(
        Modifier.padding(top = Dimens.SpaceSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Wraps: three translated difficulty labels do not fit on one line in German, and a
        // Row would squeeze the last until it broke a character per line.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}

/** A read-only line, for things like the version number that are information, not actions. */
@Composable
fun SheetVersion(label: String) {
    Text(
        label,
        Modifier.padding(vertical = Dimens.SpaceMd),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
