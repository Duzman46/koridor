package com.duzman46.gridbound.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.presentation.auth.AuthUiState
import com.duzman46.gridbound.ui.components.CenteredContent
import com.duzman46.gridbound.ui.components.FormMessage
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.SecondarySubmitButton
import com.duzman46.gridbound.ui.components.SubmitButton
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold
import com.duzman46.gridbound.ui.components.KoridorMark
import com.duzman46.gridbound.ui.components.home.GoogleMark
import com.duzman46.gridbound.ui.components.home.OptionChevron
import com.duzman46.gridbound.ui.components.home.PremiumGlyph
import com.duzman46.gridbound.ui.components.home.PremiumIcon

/**
 * The entry screen, and the first thing the app ever says.
 *
 * It always offers guest play alongside the account options, so a first launch never traps the
 * player behind a mandatory registration form — and guest play is offered *as an offer*, in a
 * card of its own under a divider, rather than as the small grey text button it used to be.
 *
 * The mark at the top is the launcher icon, at the size it is worth drawing. A player arrives
 * here having just tapped that tile, and meeting it again is what says they are in the right
 * place; the Material gamepad glyph that stood here said nothing about this game at all.
 */
@Composable
fun WelcomeScreen(
    state: AuthUiState,
    onGoogle: () -> Unit,
    onEmailSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onGuest: () -> Unit,
) {
    ScreenBackground {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceLg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            ) {
                KoridorMark(Modifier.size(132.dp))
                Spacer(Modifier.height(Dimens.SpaceSm))
                Text(
                    text = stringResource(R.string.auth_welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = KoridorGold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.auth_welcome_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Dimens.SpaceSm))

                if (state.isGoogleAvailable) {
                    // The one that takes a single tap is the only filled button on the screen.
                    // Google's mark keeps Google's colours; everything else here is gold.
                    AuthChoice(
                        mark = { GoogleMark(Modifier.size(22.dp)) },
                        label = stringResource(R.string.auth_continue_with_google),
                        filled = true,
                        enabled = !state.isSubmitting,
                        onClick = onGoogle,
                    )
                }
                AuthChoice(
                    mark = { PremiumGlyph(PremiumIcon.ENVELOPE, Modifier.size(20.dp), KoridorGold) },
                    label = stringResource(R.string.auth_sign_in_with_email),
                    filled = false,
                    enabled = !state.isSubmitting,
                    onClick = onEmailSignIn,
                )
                AuthChoice(
                    mark = { PremiumGlyph(PremiumIcon.PLUS, Modifier.size(20.dp), KoridorGold) },
                    label = stringResource(R.string.auth_create_account),
                    filled = false,
                    enabled = !state.isSubmitting,
                    onClick = onCreateAccount,
                )

                OrRule(stringResource(R.string.account_or).uppercase())

                // Guest play as an offer rather than an escape hatch. It used to be a grey text
                // button under three real ones, which is how you make the thing most first-time
                // players actually want look like the thing they are not supposed to pick.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.RadiusMd))
                        .background(Color(0xFF12161B))
                        .border(1.dp, Color(0xFF2A3038), RoundedCornerShape(Dimens.RadiusMd))
                        .clickable(enabled = !state.isSubmitting, role = Role.Button, onClick = onGuest)
                        .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PremiumGlyph(PremiumIcon.PERSON, Modifier.size(22.dp), Color(0xFF8B9098))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auth_play_as_guest),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = KoridorGold,
                        )
                        Text(
                            text = stringResource(R.string.auth_guest_card_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OptionChevron()
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = Dimens.SpaceSm),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PremiumGlyph(PremiumIcon.SHIELD_STAR, Modifier.size(18.dp), KoridorGold.copy(alpha = 0.7f))
                    Text(
                        text = stringResource(R.string.auth_guest_link_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                state.error?.let { FormMessage(it) }
            }
        }
    }
}

/** One way in: a mark and a name, filled for the recommended one and outlined for the rest. */
@Composable
private fun AuthChoice(
    mark: @Composable () -> Unit,
    label: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (filled) KoridorGold else Color(0xFF12161B))
            .border(1.dp, if (filled) KoridorGold else Color(0xFF2A3038), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = Dimens.SpaceLg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mark()
        Spacer(Modifier.width(Dimens.SpaceMd))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            // Near-black on the gold one: white on this yellow is under three to one.
            color = if (filled) Color(0xFF1A1206) else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
    }
}

/** A word between two rules, for the break between having an account and not wanting one. */
@Composable
private fun OrRule(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF2A3038)))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = KoridorGold.copy(alpha = 0.75f),
            letterSpacing = 2.sp,
        )
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF2A3038)))
    }
}

@Composable
fun SignInScreen(
    state: AuthUiState,
    onBack: () -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    AuthFormScaffold(title = stringResource(R.string.auth_sign_in_title), onBack = onBack) {
        EmailField(state.email, onEmail)
        PasswordField(
            value = state.password,
            visible = state.passwordVisible,
            label = stringResource(R.string.auth_password_label),
            imeAction = ImeAction.Done,
            onValueChange = onPassword,
            onToggleVisibility = onTogglePasswordVisibility,
            onDone = { if (!state.isSubmitting) onSubmit() },
        )
        SubmitButton(
            text = stringResource(R.string.auth_sign_in_title),
            onClick = onSubmit,
            isSubmitting = state.isSubmitting,
            // Auto-mirrored: the arrow points the other way in right-to-left languages.
            leadingIcon = Icons.AutoMirrored.Rounded.Login,
        )
        TextButton(onClick = onForgotPassword, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_forgot_password))
        }
        state.error?.let { FormMessage(it) }
    }
}

@Composable
fun SignUpScreen(
    state: AuthUiState,
    onBack: () -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirmPassword: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
) {
    AuthFormScaffold(title = stringResource(R.string.auth_sign_up_title), onBack = onBack) {
        EmailField(state.email, onEmail)
        PasswordField(
            value = state.password,
            visible = state.passwordVisible,
            label = stringResource(R.string.auth_password_label),
            imeAction = ImeAction.Next,
            onValueChange = onPassword,
            onToggleVisibility = onTogglePasswordVisibility,
        )
        PasswordField(
            value = state.confirmPassword,
            visible = state.passwordVisible,
            label = stringResource(R.string.auth_password_confirm_label),
            imeAction = ImeAction.Done,
            onValueChange = onConfirmPassword,
            onToggleVisibility = onTogglePasswordVisibility,
            onDone = { if (!state.isSubmitting) onSubmit() },
        )
        SubmitButton(
            text = stringResource(R.string.auth_create_account),
            onClick = onSubmit,
            isSubmitting = state.isSubmitting,
            leadingIcon = Icons.Rounded.PersonAdd,
        )
        state.error?.let { FormMessage(it) }
    }
}

@Composable
fun ForgotPasswordScreen(
    state: AuthUiState,
    onBack: () -> Unit,
    onEmail: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    AuthFormScaffold(title = stringResource(R.string.auth_reset_title), onBack = onBack) {
        Text(
            stringResource(R.string.auth_reset_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmailField(
            state.email,
            onEmail,
            imeAction = ImeAction.Done,
            onDone = { if (!state.isSubmitting) onSubmit() },
        )
        SubmitButton(
            text = stringResource(R.string.auth_reset_send),
            onClick = onSubmit,
            isSubmitting = state.isSubmitting,
        )
        state.error?.let { FormMessage(it) }
        state.info?.let { FormMessage(it, isError = false) }
    }
}

@Composable
private fun AuthFormScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(topBar = { ScreenTopBar(title, onBack) }) { padding ->
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
                    content()
                }
            }
        }
    }
}

/**
 * @param onDone what the tick in the corner of the keyboard does, where this field is the last
 *   one on the form. Declaring `ImeAction.Done` only draws the key; without this it closes the
 *   keyboard and nothing else happens, which on a two-field sign-in form reads as a dead button.
 */
@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.auth_email_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = onDone?.let { { it() } }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** @param onDone see [EmailField]. */
@Composable
private fun PasswordField(
    value: String,
    visible: Boolean,
    label: String,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onDone: (() -> Unit)? = null,
) {
    val toggleDescription = stringResource(
        if (visible) R.string.auth_hide_password else R.string.auth_show_password,
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = onDone?.let { { it() } }),
        trailingIcon = {
            IconButton(
                onClick = onToggleVisibility,
                modifier = Modifier.semantics { contentDescription = toggleDescription },
            ) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = null,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
