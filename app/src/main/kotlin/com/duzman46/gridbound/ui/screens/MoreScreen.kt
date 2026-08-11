package com.duzman46.gridbound.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.duzman46.gridbound.BuildConfig
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.home.MoreEntry
import com.duzman46.gridbound.ui.components.home.MoreGroup
import com.duzman46.gridbound.ui.components.home.PremiumHeader
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import com.duzman46.gridbound.ui.components.home.SupportCard

/**
 * Everything the app can do that is not starting a match.
 *
 * Two rules decide what is on it. Nothing appears twice — friends and settings have tiles on the
 * home screen and are therefore not here, because one destination reachable twice from one screen
 * teaches the player that neither route is real. And nothing appears that opens nothing: the
 * reference design has rows for a support address, a social account and a help centre, and this
 * app has none of the three configured, so they are not drawn. A row that does nothing is the
 * cheapest way to make an app feel broken.
 */
@Composable
fun MoreScreen(
    offersAdRemoval: Boolean,
    onBack: () -> Unit,
    onStatistics: () -> Unit,
    onAchievements: () -> Unit,
    onRemoveAds: () -> Unit,
    onRestorePurchases: () -> Unit,
    onOpenUrl: (String) -> Unit,
    isGuest: Boolean,
) {
    val context = LocalContext.current
    val inviteSubject = stringResource(R.string.app_name)
    val inviteText = stringResource(R.string.more_invite_message, storeUrl())
    val chooserTitle = stringResource(R.string.more_invite_friends)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        PremiumHeader(
            title = stringResource(R.string.menu_more),
            subtitle = stringResource(R.string.home_more_subtitle),
            onBack = onBack,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(top = Dimens.SpaceSm, bottom = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            if (offersAdRemoval) {
                SupportCard(
                    title = stringResource(R.string.more_support_title),
                    body = stringResource(R.string.more_support_body),
                    action = stringResource(R.string.store_remove_ads),
                    onAction = onRemoveAds,
                )
                if (isGuest) {
                    // Said before Play has taken the money, not after: a guest's purchase has no
                    // account to be attached to and would be stranded on this device.
                    Text(
                        text = stringResource(R.string.store_guest_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B9098),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(horizontal = Dimens.SpaceXs),
                    )
                }
            }

            MoreGroup(
                listOf(
                    MoreEntry(
                        icon = PremiumIcon.BARS,
                        title = stringResource(R.string.menu_statistics),
                        subtitle = stringResource(R.string.more_statistics_hint),
                        onClick = onStatistics,
                    ),
                    MoreEntry(
                        icon = PremiumIcon.STAR,
                        title = stringResource(R.string.achievements_title),
                        subtitle = stringResource(R.string.more_achievements_hint),
                        onClick = onAchievements,
                    ),
                    MoreEntry(
                        icon = PremiumIcon.PEOPLE,
                        title = chooserTitle,
                        subtitle = stringResource(R.string.more_invite_friends_hint),
                        onClick = { context.share(chooserTitle, inviteSubject, inviteText) },
                    ),
                ),
            )

            MoreGroup(
                buildList {
                    if (BuildConfig.MONETIZATION_CONFIGURED) {
                        add(
                            MoreEntry(
                                icon = PremiumIcon.RESTORE,
                                title = stringResource(R.string.store_restore),
                                subtitle = stringResource(R.string.more_restore_hint),
                                onClick = onRestorePurchases,
                            ),
                        )
                    }
                    // A legal link with no URL configured is not shown at all rather than
                    // opening nothing — Play requires the policy link to work, not to exist.
                    val privacy = BuildConfig.PRIVACY_POLICY_URL
                    if (privacy.isNotBlank()) {
                        add(
                            MoreEntry(
                                icon = PremiumIcon.SHIELD_STAR,
                                title = stringResource(R.string.account_privacy_policy),
                                subtitle = stringResource(R.string.more_privacy_hint),
                                onClick = { onOpenUrl(privacy) },
                            ),
                        )
                    }
                    val terms = BuildConfig.TERMS_URL
                    if (terms.isNotBlank()) {
                        add(
                            MoreEntry(
                                icon = PremiumIcon.DOCUMENT,
                                title = stringResource(R.string.account_terms_of_service),
                                subtitle = stringResource(R.string.more_terms_hint),
                                onClick = { onOpenUrl(terms) },
                            ),
                        )
                    }
                    add(
                        MoreEntry(
                            icon = PremiumIcon.INFO,
                            title = stringResource(R.string.more_version),
                            value = BuildConfig.VERSION_NAME,
                        ),
                    )
                },
            )
        }
    }
}

/**
 * Where this build lives on the store.
 *
 * Built from the application id rather than stored anywhere: it is the same string Play derives
 * the listing from, so there is nothing here to configure and nothing to fall out of date.
 */
private fun storeUrl(): String =
    "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"

/**
 * Hands the invitation to whatever the player uses to talk to people.
 *
 * A chooser rather than a named app: which one they use is theirs to say, and naming one would
 * mean shipping a list of them.
 */
private fun Context.share(chooserTitle: String, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        startActivity(
            Intent.createChooser(intent, chooserTitle)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (error: android.content.ActivityNotFoundException) {
        // A device with nothing at all that can send text. Nothing to recover, and nothing worth
        // interrupting the player over.
        AppLog.warn("share-invite", error)
    }
}
