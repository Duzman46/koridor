package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.duzman46.gridbound.R
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.home.PremiumHeader
import java.util.Locale

/**
 * The language chooser: a screen of its own, filling the window.
 *
 * It was a bottom sheet, and a list inside a bottom sheet is a fight over the same gesture —
 * the sheet reads a drag as "move me", the list reads it as "scroll me", and the two trade
 * the gesture back and forth mid-flick. That is what made scrolling it lurch. A sheet earns
 * that risk when it is a short list you glance at; eleven languages is a list you read, so it
 * gets a full surface and ordinary scrolling with nothing underneath to argue with.
 *
 * Each name is written in its own language: a player who cannot read the current one can
 * still find theirs. Selection is a bar at the start edge rather than a checkmark — the same
 * wall shape the board uses, and it survives being read in either writing direction.
 */
@Composable
fun LanguagePickerDialog(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    // "Follow the device" is no longer a row, so the tick has to land on what following the
    // device actually produces here — Türkçe on a Turkish phone, not an option called
    // "device language" that says nothing about which one you are getting.
    val deviceTag = LocalResources.current.configuration.locales[0].language
    val effective = AppLanguage.resolve(selected, deviceTag)

    Dialog(
        onDismissRequest = onDismiss,
        // The default caps a dialog at a phone-dialog width, which would put an eleven-row
        // list in a narrow column with margins down both sides.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // The app's own header, not Material's TopAppBar. This dialog fills the window, so
            // what stood at the top of it was the last full-width Material bar a player could
            // reach — a different title weight and a different back arrow from the screen they
            // opened it from.
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                PremiumHeader(
                    title = stringResource(R.string.settings_language),
                    onBack = onDismiss,
                )
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                    items(AppLanguage.selectable, key = AppLanguage::name) { language ->
                        LanguageRow(
                            language = language,
                            isSelected = language == effective,
                            onClick = { onSelect(language) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    language: AppLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val code = language.tag.take(2).uppercase(Locale.ROOT)

    Box(
        Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            Modifier
                .heightIn(min = 64.dp)
                .padding(end = Dimens.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            // The selection mark: a wall bar at the start edge, in the accent.
            Box(
                Modifier
                    .width(Dimens.ScreenPadding)
                    .heightIn(min = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 26.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.secondary),
                    )
                }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusXs))
                    .background(if (isSelected) colors.primary else colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    code,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                )
            }
            Text(
                text = language.endonym,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(
                    // Mixed scripts: "العربية" must run right-to-left even while the app
                    // is English, and "English" must not flip while the app is Arabic.
                    textDirection = TextDirection.Content,
                ),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(
            Modifier.align(Alignment.BottomStart).padding(start = 80.dp),
            thickness = Dimens.Hairline,
            color = colors.outlineVariant.copy(alpha = 0.5f),
        )
    }
}
