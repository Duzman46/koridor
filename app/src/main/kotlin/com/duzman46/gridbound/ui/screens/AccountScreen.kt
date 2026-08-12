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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.theme.KoridorGold
import com.duzman46.gridbound.ui.components.home.GroupNote
import com.duzman46.gridbound.ui.components.home.OptionEntry
import com.duzman46.gridbound.ui.components.home.OptionGroup
import com.duzman46.gridbound.ui.components.home.PremiumHeader
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import com.duzman46.gridbound.ui.components.home.GoogleMark
import com.duzman46.gridbound.ui.components.home.OptionChevron
import com.duzman46.gridbound.ui.components.home.PremiumGlyph

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
    var emailSheetOpen by rememberSaveable { mutableStateOf(false) }
    var deleteRequested by remember { mutableStateOf(false) }

    // Closed by the link succeeding, not by the button: onLinkEmail is several round trips
    // long and the answer arrives in state.error or state.info, so dismissing on tap would hide
    // the field the player has to correct.
    if (emailSheetOpen && !session.isGuest) emailSheetOpen = false
    if (emailSheetOpen) {
        EmailLinkDialog(
            state = state,
            onEmail = onEmail,
            onPassword = onPassword,
            onSubmit = onLinkEmail,
            onDismiss = { emailSheetOpen = false },
        )
    }
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

    ScreenBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            PremiumHeader(title = stringResource(R.string.account_title), onBack = onBack)
            // Outside the scroll on purpose. Every action here is a card away from the top of
            // the page and ends with a dialog closing over wherever the player had scrolled to;
            // an answer written at one end of a page the player is reading the other end of is
            // how a refused deletion came to look like a button that does nothing.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .padding(horizontal = Dimens.SpaceLg),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                // Every action on this page is several backend round trips long, and handing an
                // account over is the longest of them: data erased, an identity given up,
                // another signed in, and the session read back. The card whose button carries a
                // spinner is the first thing to disappear when that starts, so without this the
                // screen goes still at precisely the moment the player most needs telling that
                // it has not.
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
                    .padding(horizontal = Dimens.SpaceLg)
                    .navigationBarsPadding()
                    .padding(bottom = Dimens.SpaceXl),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            ) {
                if (session.isGuest) {
                    AccountHeadline(
                        title = stringResource(R.string.auth_link_title),
                        body = stringResource(R.string.auth_link_explainer),
                    )
                    LinkRows(
                        googleAvailable = session.isGoogleSignInAvailable,
                        enabled = !state.isSubmitting,
                        onLinkGoogle = onLinkGoogle,
                        onEmailRow = { emailSheetOpen = true },
                    )
                }

                OptionGroup(
                    listOf(
                        OptionEntry(
                            icon = PremiumIcon.PERSON,
                            title = session.user?.email
                                ?: stringResource(R.string.account_title),
                            subtitle = stringResource(R.string.account_manage_note),
                            onClick = { signOutRequested = true },
                        ),
                    ),
                )

                if (state.hasPrivacyPolicy || state.hasTerms) {
                    OptionGroup(
                        buildList {
                            if (state.hasPrivacyPolicy) {
                                add(
                                    OptionEntry(
                                        icon = PremiumIcon.SHIELD_STAR,
                                        title = stringResource(R.string.account_privacy_policy),
                                        subtitle = stringResource(R.string.account_privacy_note),
                                        onClick = { onOpenUrl(state.privacyPolicyUrl) },
                                    ),
                                )
                            }
                            if (state.hasTerms) {
                                add(
                                    OptionEntry(
                                        icon = PremiumIcon.DOCUMENT,
                                        title = stringResource(R.string.account_terms_of_service),
                                        subtitle = stringResource(R.string.account_terms_note),
                                        onClick = { onOpenUrl(state.termsUrl) },
                                    ),
                                )
                            }
                        },
                    )
                } else {
                    GroupNote(stringResource(R.string.account_legal_unavailable))
                }

                DangerRow(
                    title = stringResource(R.string.account_delete_title),
                    body = stringResource(R.string.account_delete_warning),
                    enabled = !state.isSubmitting,
                    onClick = { deleteRequested = true },
                )
            }
        }
    }
}

/** The gold line and the sentence under it, at the top of a page that is asking for something. */
@Composable
private fun AccountHeadline(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = KoridorGold,
            letterSpacing = 1.2.sp,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The one row on this page that destroys something, drawn so it cannot be mistaken for the ones
 * that do not.
 *
 * The warning is above the control rather than behind it. A red button with a title and no
 * consequence beside it is a button people press to find out what it does; this one says what
 * goes — profile, statistics, friends, rating — before the finger arrives, and the dialog then
 * asks for the word to be typed out.
 */
@Composable
private fun DangerRow(title: String, body: String, enabled: Boolean, onClick: () -> Unit) {
    val danger = MaterialTheme.colorScheme.error
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(danger.copy(alpha = 0.06f))
            .border(1.dp, danger.copy(alpha = 0.35f), RoundedCornerShape(Dimens.RadiusMd))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(Dimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(danger.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = danger,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = danger,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinkRows(
    googleAvailable: Boolean,
    enabled: Boolean,
    onLinkGoogle: () -> Unit,
    onEmailRow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        if (googleAvailable) {
            // Its own row, bordered in gold, because it is the one that takes a single tap and
            // the reference gives it that weight. Google's mark keeps Google's colours.
            LinkRow(
                mark = { GoogleMark(Modifier.size(22.dp)) },
                title = stringResource(R.string.auth_link_with_google),
                subtitle = null,
                accented = true,
                enabled = enabled,
                onClick = onLinkGoogle,
            )
            OrDivider()
        }
        LinkRow(
            mark = {
                PremiumGlyph(PremiumIcon.ENVELOPE, Modifier.size(20.dp), Color(0xFF9AA0A6))
            },
            title = stringResource(R.string.auth_link_with_email),
            subtitle = stringResource(R.string.account_link_email_note),
            accented = false,
            enabled = enabled,
            onClick = onEmailRow,
        )
    }
}

/** One way in: a mark, a name, a reason, and a chevron saying it leads somewhere. */
@Composable
private fun LinkRow(
    mark: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    accented: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.RadiusMd)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (accented) KoridorGold.copy(alpha = 0.07f) else Color(0xFF12161B))
            .border(
                1.dp,
                if (accented) KoridorGold.copy(alpha = 0.55f) else Color(0xFF2A3038),
                shape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF1B2027)),
            contentAlignment = Alignment.Center,
        ) { mark() }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        OptionChevron()
    }
}

/** The word between the one-tap way and the typed one. */
@Composable
private fun OrDivider() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF2A3038)))
        Text(
            text = stringResource(R.string.account_or),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF2A3038)))
    }
}

/**
 * The two fields, behind the row rather than laid out on the page.
 *
 * They were what made this a scrolling page: an address, a password and a button are most of a
 * phone's height, sitting under a heading, above three more cards. Nothing about the flow moved
 * -- the same lambdas, the same state, the same errors -- only where the fields are while the
 * player is not filling them in.
 */
@Composable
private fun EmailLinkDialog(
    state: AccountUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auth_link_with_email)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
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
                state.error?.let { FormMessage(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !state.isSubmitting) {
                Text(stringResource(R.string.auth_link_account))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
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
