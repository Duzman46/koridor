package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.auth.domain.AccountType
import com.duzman46.gridbound.presentation.account.AccountUiState
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.ui.components.FormMessage
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.SecondarySubmitButton
import com.duzman46.gridbound.ui.components.SubmitButton

@Composable
fun AccountScreen(
    state: AccountUiState,
    session: SessionState,
    onBack: () -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onLinkGoogle: () -> Unit,
    onLinkEmail: () -> Unit,
    onSignInToExistingAccount: () -> Unit,
    onDismissExistingAccount: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var signOutRequested by remember { mutableStateOf(false) }
    var deleteRequested by remember { mutableStateOf(false) }

    if (state.existingAccountWarning) {
        ExistingAccountDialog(
            onConfirm = onSignInToExistingAccount,
            onDismiss = onDismissExistingAccount,
        )
    }
    if (signOutRequested) {
        ConfirmDialog(
            title = stringResource(R.string.auth_sign_out_confirm_title),
            message = stringResource(R.string.auth_sign_out_confirm_message),
            confirmLabel = stringResource(R.string.auth_sign_out),
            onConfirm = {
                signOutRequested = false
                onSignOut()
            },
            onDismiss = { signOutRequested = false },
        )
    }
    if (deleteRequested) {
        DeleteAccountDialog(
            // Only an email account has a secret the app can ask for. A Google account
            // proves itself through the account picker, and a guest has nothing to prove.
            password = state.password.takeIf { session.user?.accountType == AccountType.EMAIL },
            onPassword = onPassword,
            onConfirm = {
                deleteRequested = false
                onDeleteAccount()
            },
            onDismiss = { deleteRequested = false },
        )
    }

    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.account_title), onBack) }) { padding ->
        ScreenBackground {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Outside the scroll on purpose. Every action here is a card away from the
                // top of the page and ends with a dialog closing over wherever the player
                // had scrolled to; an answer written at one end of a page the player is
                // reading the other end of is how a refused deletion came to look like a
                // button that does nothing.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Every action on this page is several backend round trips long, and
                    // handing an account over is the longest of them: data erased, an
                    // identity given up, another signed in, and the session read back. The
                    // card whose button carries a spinner is the first thing to disappear
                    // when that starts, so without this the screen goes still at precisely
                    // the moment the player most needs telling that it has not.
                    if (state.isSubmitting) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    state.error?.let { FormMessage(it) }
                    state.info?.let { FormMessage(it, isError = false) }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (session.isGuest) {
                        LinkAccountCard(
                            state = state,
                            googleAvailable = session.isGoogleSignInAvailable,
                            onEmail = onEmail,
                            onPassword = onPassword,
                            onLinkGoogle = onLinkGoogle,
                            onLinkEmail = onLinkEmail,
                        )
                    }

                    AccountCard(stringResource(R.string.account_title)) {
                        session.user?.email?.let { email ->
                            Text(email, style = MaterialTheme.typography.bodyMedium)
                        }
                        SecondarySubmitButton(
                            text = stringResource(R.string.auth_sign_out),
                            onClick = { signOutRequested = true },
                            enabled = !state.isSubmitting,
                            leadingIcon = Icons.AutoMirrored.Rounded.Logout,
                        )
                    }

                    if (state.hasPrivacyPolicy || state.hasTerms) {
                        AccountCard(stringResource(R.string.account_privacy_policy)) {
                            if (state.hasPrivacyPolicy) {
                                SecondarySubmitButton(
                                    text = stringResource(R.string.account_privacy_policy),
                                    onClick = { onOpenUrl(state.privacyPolicyUrl) },
                                    leadingIcon = Icons.Rounded.PrivacyTip,
                                )
                            }
                            if (state.hasTerms) {
                                SecondarySubmitButton(
                                    text = stringResource(R.string.account_terms_of_service),
                                    onClick = { onOpenUrl(state.termsUrl) },
                                    leadingIcon = Icons.Rounded.Gavel,
                                )
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.account_legal_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    AccountCard(stringResource(R.string.account_delete_title)) {
                        Text(
                            stringResource(R.string.account_delete_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { deleteRequested = true },
                            enabled = !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                            Text(
                                stringResource(R.string.account_delete_action),
                                Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkAccountCard(
    state: AccountUiState,
    googleAvailable: Boolean,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onLinkGoogle: () -> Unit,
    onLinkEmail: () -> Unit,
) {
    AccountCard(stringResource(R.string.auth_link_title)) {
        Text(
            stringResource(R.string.auth_link_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (googleAvailable) {
            SubmitButton(
                text = stringResource(R.string.auth_link_with_google),
                onClick = onLinkGoogle,
                isSubmitting = state.isSubmitting,
                leadingIcon = Icons.Rounded.AccountCircle,
            )
        }
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmail,
            label = { Text(stringResource(R.string.auth_email_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPassword,
            label = { Text(stringResource(R.string.auth_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        SecondarySubmitButton(
            text = stringResource(R.string.auth_link_with_email),
            onClick = onLinkEmail,
            enabled = !state.isSubmitting,
            leadingIcon = Icons.Rounded.AlternateEmail,
        )
    }
}

/**
 * The one outcome of linking that cannot be undone by pressing back, put in front of the
 * player before it happens rather than reported once it has.
 *
 * Firebase offers no merge between two identities that both exist, so the account waiting on
 * the other side cannot absorb the guest playing now. The dialog says what goes and what
 * arrives in those words: a player who reads "linked" and then finds a different rating and
 * an empty friend list has been told something untrue. Declining is worded as staying a
 * guest, because that is exactly what it does — nothing has happened yet.
 */
@Composable
private fun ExistingAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auth_existing_account_title)) },
        text = { Text(stringResource(R.string.auth_existing_account_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.auth_existing_account_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.auth_existing_account_cancel))
            }
        },
    )
}

/**
 * Deletion asks the player to type a confirmation word, so an irreversible action cannot be
 * triggered by a stray tap.
 *
 * It also warns that the sign-in will be checked, because Firebase refuses to remove a
 * credential on a session that has been open for a while. That check happens before anything
 * is erased, and the account picker or password prompt it produces is a great deal less
 * alarming when the dialog has already said it is coming.
 *
 * @param password the current field value for an account that has one to give, null when the
 *   account proves itself some other way.
 */
@Composable
private fun DeleteAccountDialog(
    password: String?,
    onPassword: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val keyword = stringResource(R.string.action_delete).uppercase()
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.account_delete_warning))
                Text(
                    stringResource(R.string.account_delete_reauth_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.account_delete_confirm_label, keyword)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (password != null) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPassword,
                        label = { Text(stringResource(R.string.auth_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = typed.trim().equals(keyword, ignoreCase = true) &&
                    (password == null || password.isNotEmpty()),
            ) {
                Text(
                    stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AccountCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
