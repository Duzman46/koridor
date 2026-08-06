package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.presentation.profile.ProfileEditState
import com.duzman46.gridbound.profile.domain.UserProfile
import com.duzman46.gridbound.ui.components.AvatarPalette
import com.duzman46.gridbound.ui.components.FormMessage
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.LoadingState
import com.duzman46.gridbound.ui.components.PlayerAvatar
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.SubmitButton
import java.text.DateFormat
import java.util.Date

/** First-run username picker, shown right after an account is created. */
@Composable
fun UsernameScreen(
    state: ProfileEditState,
    onBack: () -> Unit,
    onUsername: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.username_title), onBack) }) { padding ->
        ScreenBackground {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        stringResource(R.string.username_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    UsernameField(state, onUsername)
                    SubmitButton(
                        text = stringResource(R.string.action_continue),
                        onClick = onSubmit,
                        enabled = state.username.value.isNotBlank(),
                        isSubmitting = state.isSubmitting,
                    )
                    state.error?.let { FormMessage(it) }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.profile_title), onBack) }) { padding ->
        ScreenBackground {
            if (profile == null) {
                LoadingState(Modifier.padding(padding))
                return@ScreenBackground
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PlayerAvatar(profile.avatarId, profile.displayName, size = 96.dp)
                Text(
                    profile.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "@${profile.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (profile.isGuest) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            stringResource(R.string.auth_guest_badge),
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(stringResource(R.string.profile_rating), profile.rating.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.profile_games), profile.totalGames.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.profile_wins), profile.wins.toString(), Modifier.weight(1f))
                }

                ProfileDetailCard(profile)

                SubmitButton(
                    text = stringResource(R.string.profile_edit_title),
                    onClick = onEdit,
                    modifier = Modifier.widthIn(max = 620.dp),
                )
            }
        }
    }
}

@Composable
fun EditProfileScreen(
    state: ProfileEditState,
    onBack: () -> Unit,
    onUsername: (String) -> Unit,
    onDisplayName: (String) -> Unit,
    onAvatar: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Scaffold(
        topBar = { ScreenTopBar(stringResource(R.string.profile_edit_title), onBack) },
    ) { padding ->
        ScreenBackground {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    UsernameField(state, onUsername)
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = onDisplayName,
                        label = { Text(stringResource(R.string.profile_display_name_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.profile_avatar_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AvatarPicker(state.avatarId, state.displayName, onAvatar)
                    SubmitButton(
                        text = stringResource(R.string.action_save),
                        onClick = onSubmit,
                        isSubmitting = state.isSubmitting,
                    )
                    state.error?.let { FormMessage(it) }
                }
            }
        }
    }
}

@Composable
private fun AvatarPicker(
    selectedId: String,
    name: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarPalette.ids.forEach { avatarId ->
            val selected = avatarId == selectedId
            Box(
                Modifier.selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onSelect(avatarId) },
                ),
                contentAlignment = Alignment.BottomEnd,
            ) {
                PlayerAvatar(avatarId, name, size = 56.dp)
                if (selected) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsernameField(state: ProfileEditState, onUsername: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.username.value,
            onValueChange = onUsername,
            label = { Text(stringResource(R.string.username_label)) },
            singleLine = true,
            isError = state.username.isError,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                when {
                    state.username.isChecking -> CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )

                    state.username.isAvailable -> Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    else -> Unit
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        state.username.message?.let {
            Text(
                it.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.username.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun ProfileDetailCard(profile: UserProfile) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 620.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailRow(stringResource(R.string.profile_highest_rating), profile.highestRating.toString())
            DetailRow(stringResource(R.string.profile_losses), profile.losses.toString())
            DetailRow(stringResource(R.string.profile_draws), profile.draws.toString())
            DetailRow(stringResource(R.string.profile_win_streak), profile.currentWinStreak.toString())
            DetailRow(stringResource(R.string.profile_best_streak), profile.bestWinStreak.toString())
            if (profile.createdAt > 0L) {
                DetailRow(
                    label = stringResource(R.string.profile_member_since, formatDate(profile.createdAt)),
                    value = "",
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Uses the platform formatter so the date follows the active locale. */
private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
