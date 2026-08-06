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

/**
 * The entry screen. It always offers guest play alongside the account options, so a first
 * launch never traps the player behind a mandatory registration form.
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
        CenteredContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(28.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    stringResource(R.string.auth_welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.auth_welcome_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                if (state.isGoogleAvailable) {
                    SubmitButton(
                        text = stringResource(R.string.auth_continue_with_google),
                        onClick = onGoogle,
                        isSubmitting = state.isSubmitting,
                        leadingIcon = Icons.Rounded.AccountCircle,
                    )
                }
                SecondarySubmitButton(
                    text = stringResource(R.string.auth_sign_in_with_email),
                    onClick = onEmailSignIn,
                    enabled = !state.isSubmitting,
                    leadingIcon = Icons.Rounded.AlternateEmail,
                )
                SecondarySubmitButton(
                    text = stringResource(R.string.auth_create_account),
                    onClick = onCreateAccount,
                    enabled = !state.isSubmitting,
                    leadingIcon = Icons.Rounded.PersonAdd,
                )
                TextButton(
                    onClick = onGuest,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.auth_play_as_guest))
                }
                Text(
                    stringResource(R.string.auth_guest_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                state.error?.let { FormMessage(it) }
            }
        }
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
        EmailField(state.email, onEmail, imeAction = ImeAction.Done)
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

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction = ImeAction.Next,
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
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    visible: Boolean,
    label: String,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
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
