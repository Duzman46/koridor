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
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.duzman46.gridbound.theme.Palette
import com.duzman46.gridbound.ui.components.FormMessage
import com.duzman46.gridbound.ui.components.ScreenBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.duzman46.gridbound.theme.Dimens
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
    onRequestEmailSignIn: () -> Unit,
    onSignInWithEmail: () -> Unit,
    onDismissEmailSignInWarning: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var signOutRequested by remember { mutableStateOf(false) }
    var emailSheetOpen by rememberSaveable { mutableStateOf(false) }
    var signInSheetOpen by rememberSaveable { mutableStateOf(false) }
    var deleteRequested by remember { mutableStateOf(false) }

    // Closed by the link succeeding, not by the button: onLinkEmail is several round trips
    // long and the answer arrives in state.error or state.info, so dismissing on tap would hide
    // the field the player has to correct.
    if (emailSheetOpen && !session.isGuest) emailSheetOpen = false
    if (signInSheetOpen && !session.isGuest) signInSheetOpen = false
    if (signInSheetOpen) {
        EmailLinkDialog(
            state = state,
            title = stringResource(R.string.account_sign_in),
            action = stringResource(R.string.account_sign_in),
            onEmail = onEmail,
            onPassword = onPassword,
            // The warning first. onRequestEmailSignIn checks nothing and erases nothing; it
            // only puts the cost on screen.
            onSubmit = { signInSheetOpen = false; onRequestEmailSignIn() },
            onDismiss = { signInSheetOpen = false },
        )
    }
    if (state.emailSignInWarning) {
        AlertDialog(
            onDismissRequest = onDismissEmailSignInWarning,
            title = { Text(stringResource(R.string.account_sign_in_warning_title)) },
            text = { Text(stringResource(R.string.account_sign_in_warning)) },
            confirmButton = {
                TextButton(onClick = onSignInWithEmail) {
                    Text(stringResource(R.string.account_sign_in_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissEmailSignInWarning) {
                    Text(stringResource(R.string.auth_existing_account_cancel))
                }
            },
        )
    }
    if (emailSheetOpen) {
        EmailLinkDialog(
            state = state,
            title = stringResource(R.string.account_sign_up),
            action = stringResource(R.string.account_sign_up),
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
                    // One card rather than a heading floating above a group of rows. The title
                    // is what the card is for, so it belongs inside its border; loose above it,
                    // it read as a heading for the whole page and the page is about more.
                    LinkCard(
                        googleAvailable = session.isGoogleSignInAvailable,
                        enabled = !state.isSubmitting,
                        onLinkGoogle = onLinkGoogle,
                        onSignUp = { emailSheetOpen = true },
                        onSignIn = { signInSheetOpen = true },
                    )
                }

                // No "Account" section on the account page. What the row actually does is sign
                // out, so that is what it says; the address it signs you out of is the subtitle.
                OptionGroup(
                    listOf(
                        OptionEntry(
                            icon = PremiumIcon.PERSON,
                            title = stringResource(R.string.auth_sign_out),
                            subtitle = session.user?.email,
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

/**
 * Everything about becoming a real account, inside one border.
 *
 * The heading used to sit loose above the rows, where it read as a title for the whole page —
 * and the page also holds signing out, two legal documents and a deletion, none of which are
 * about protecting progress.
 */
@Composable
private fun LinkCard(
    googleAvailable: Boolean,
    enabled: Boolean,
    onLinkGoogle: () -> Unit,
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(Palette.Card)
            // InkGlyph rather than Edge: this hairline is the whole boundary of the card,
            // so it identifies it and has to clear 3:1 rather than merely decorate.
            .border(1.dp, Palette.InkGlyph, RoundedCornerShape(Dimens.RadiusMd))
            .padding(Dimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Text(
            text = stringResource(R.string.auth_link_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Palette.Gold,
        )
        Text(
            text = stringResource(R.string.auth_link_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (googleAvailable) {
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
        // "Sign up", not "link with e-mail". Linking is the mechanism; what the player is doing
        // is making an account. The subtitle says the thing they actually want to know, which is
        // that nothing they have played is lost — linkGuestWithEmail upgrades the anonymous user
        // in place and keeps the same user id, so rating, statistics and friends come with it.
        LinkRow(
            mark = {
                PremiumGlyph(PremiumIcon.ENVELOPE, Modifier.size(20.dp), Palette.InkGlyph)
            },
            title = stringResource(R.string.account_sign_up),
            subtitle = stringResource(R.string.account_sign_up_note),
            accented = false,
            enabled = enabled,
            onClick = onSignUp,
        )
        // Signing in to an account you already have is the opposite trade to the one above it,
        // and the subtitle says so rather than leaving the player to find out.
        LinkRow(
            mark = {
                PremiumGlyph(PremiumIcon.PERSON, Modifier.size(20.dp), Palette.InkGlyph)
            },
            title = stringResource(R.string.account_sign_in),
            subtitle = stringResource(R.string.account_sign_in_note),
            accented = false,
            enabled = enabled,
            onClick = onSignIn,
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
            .background(if (accented) Palette.Gold.copy(alpha = 0.07f) else Palette.Card)
            .border(
                1.dp,
                if (accented) Palette.Gold.copy(alpha = 0.55f) else Palette.InkGlyph,
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
                .background(Palette.Inset),
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
        Box(Modifier.weight(1f).height(Dimens.Hairline).background(Palette.Edge))
        Text(
            text = stringResource(R.string.account_or),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f).height(Dimens.Hairline).background(Palette.Edge))
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
    title: String,
    action: String,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
                Text(action)
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

