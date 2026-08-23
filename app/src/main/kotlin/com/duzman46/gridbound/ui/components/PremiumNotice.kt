package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.duzman46.gridbound.R
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.Palette
import com.duzman46.gridbound.theme.KoridorGold
import com.duzman46.gridbound.ui.components.home.PremiumGlyph
import com.duzman46.gridbound.ui.components.home.PremiumIcon

/**
 * One thing to read and one way out of it.
 *
 * The app had five dialogs before this and no two of them agreed: three raw `Dialog`s with the
 * premium shell copied between them and two Material `AlertDialog`s that arrive in a different
 * shape, a different corner and a different colour from the screen underneath. This is the
 * premium shell, extracted, so the sixth one did not have to be a sixth copy.
 *
 * Deliberately not a confirmation. There is no second button and no destructive path through
 * it — a notice tells the player something and gets out of the way, and the moment it grows a
 * "cancel" it is a different component with different rules about which button is dangerous.
 */
@Composable
fun PremiumNotice(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: PremiumIcon = PremiumIcon.INFO,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, KoridorGold.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
                .padding(Dimens.SpaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(KoridorGold.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                PremiumGlyph(icon, Modifier.size(Dimens.GlyphLg), KoridorGold)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkMuted,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.action_close),
                    fontWeight = FontWeight.Bold,
                    color = KoridorGold,
                )
            }
        }
    }
}
