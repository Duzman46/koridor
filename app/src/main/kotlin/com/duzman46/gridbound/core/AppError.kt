package com.duzman46.gridbound.core

import androidx.annotation.StringRes
import com.duzman46.gridbound.R

/**
 * The complete set of failures the UI is allowed to show.
 *
 * Backend exceptions are mapped onto one of these before they leave a repository, so a
 * Firebase error code or stack trace can never reach the screen. Technical detail is logged
 * through [AppLog] instead.
 */
enum class AppError(@param:StringRes val messageRes: Int) {
    NETWORK(R.string.error_network),
    SERVICE_UNAVAILABLE(R.string.error_service_unavailable),
    NOT_SIGNED_IN(R.string.error_not_signed_in),
    UNKNOWN(R.string.error_unknown),

    EMAIL_INVALID(R.string.auth_error_email_invalid),
    EMAIL_IN_USE(R.string.auth_error_email_in_use),
    PASSWORD_WEAK(R.string.auth_error_password_weak),
    PASSWORD_MISMATCH(R.string.auth_error_password_mismatch),
    INVALID_CREDENTIALS(R.string.auth_error_invalid_credentials),
    TOO_MANY_REQUESTS(R.string.auth_error_too_many_requests),
    GOOGLE_CANCELLED(R.string.auth_error_google_cancelled),
    GOOGLE_UNAVAILABLE(R.string.auth_error_google_unavailable),
    ACCOUNT_EXISTS_WITH_OTHER_METHOD(R.string.auth_error_account_exists_with_other_method),
    REQUIRES_RECENT_LOGIN(R.string.auth_error_requires_recent_login),
    CREDENTIAL_IN_USE(R.string.auth_error_credential_in_use),

    USERNAME_BLANK(R.string.username_error_blank),

    /** Length messages resolve through plurals; see [message]. */
    USERNAME_TOO_SHORT(R.string.username_error_blank),
    USERNAME_TOO_LONG(R.string.username_error_blank),
    USERNAME_INVALID_CHARACTERS(R.string.username_error_invalid_characters),
    USERNAME_TAKEN(R.string.username_error_taken),
    USERNAME_NOT_ALLOWED(R.string.username_error_not_allowed),

    ROOM_NOT_FOUND(R.string.room_error_not_found),
    ROOM_FULL(R.string.room_error_full),
    ROOM_CODE_INVALID(R.string.room_error_code_invalid),
    ROOM_CODE_UNAVAILABLE(R.string.room_error_code_unavailable),
    ROOM_PASSWORD_WRONG(R.string.room_error_password_wrong),
    ROOM_PASSWORD_TOO_SHORT(R.string.room_error_password_too_short),
    ROOM_NAME_TOO_LONG(R.string.room_error_name_too_long),
    ROOM_NO_OPPONENT_FOUND(R.string.room_error_no_opponent),
    RANKED_REQUIRES_ACCOUNT(R.string.room_error_ranked_requires_account),
    ;

    /**
     * True when the failure means the player genuinely cannot proceed, as opposed to the
     * backend merely being out of reach. Guest entry treats the latter as success so a
     * first launch offline — or against a project whose rules are not deployed yet — still
     * reaches the tutorial and local matches.
     */
    val blocksLocalPlay: Boolean
        get() = this != NETWORK && this != SERVICE_UNAVAILABLE && this != UNKNOWN

    /**
     * Length limits go through plurals so languages that inflect on the count read correctly.
     */
    val message: UiText
        get() = when (this) {
            USERNAME_TOO_SHORT -> UiText.plural(
                R.plurals.username_error_too_short,
                UsernameRules.MIN_LENGTH,
                UsernameRules.MIN_LENGTH,
            )

            USERNAME_TOO_LONG -> UiText.plural(
                R.plurals.username_error_too_long,
                UsernameRules.MAX_LENGTH,
                UsernameRules.MAX_LENGTH,
            )

            ROOM_CODE_INVALID -> UiText.of(messageRes, Constants.Online.ROOM_CODE_LENGTH)
            ROOM_PASSWORD_TOO_SHORT ->
                UiText.of(messageRes, Constants.Online.ROOM_PASSWORD_MIN_LENGTH)

            else -> UiText.Res(messageRes)
        }
}
