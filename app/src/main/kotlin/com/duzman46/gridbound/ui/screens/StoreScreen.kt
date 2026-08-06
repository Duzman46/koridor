package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.asString
import com.duzman46.gridbound.monetization.BillingState
import com.duzman46.gridbound.monetization.domain.BillingMessage
import com.duzman46.gridbound.monetization.domain.Entitlement
import com.duzman46.gridbound.monetization.domain.StoreOffer
import com.duzman46.gridbound.ui.components.EmptyState
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.SecondarySubmitButton
import com.duzman46.gridbound.ui.components.SubmitButton

@Composable
fun StoreScreen(
    state: BillingState,
    isGuest: Boolean,
    onBack: () -> Unit,
    onBuy: (Entitlement) -> Unit,
    onRestore: () -> Unit,
) {
    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.store_title), onBack) }) { padding ->
        ScreenBackground {
            if (state.offers.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Box(Modifier.weight(1f)) {
                        EmptyState(stringResource(R.string.store_empty))
                    }
                    RestoreRow(state, onRestore)
                }
                return@ScreenBackground
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Stated up front, because a store in a competitive game invites the
                // question and the answer here is a design commitment.
                Text(
                    stringResource(R.string.store_no_pay_to_win),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isGuest) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 620.dp),
                    ) {
                        Text(
                            stringResource(R.string.store_guest_warning),
                            Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (state.hasPending) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 620.dp),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.HourglassTop, contentDescription = null)
                            Text(
                                stringResource(R.string.store_pending_explainer),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                state.offers.forEach { offer ->
                    OfferCard(offer, enabled = state.isReady) { onBuy(offer.product.entitlement) }
                }

                state.message?.let { message ->
                    Text(
                        when (message) {
                            is BillingMessage.Error -> message.text.asString()
                            is BillingMessage.Info -> message.text.asString()
                        },
                        color = when (message) {
                            is BillingMessage.Error -> MaterialTheme.colorScheme.error
                            is BillingMessage.Info -> MaterialTheme.colorScheme.primary
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                RestoreRow(state, onRestore)
            }
        }
    }
}

@Composable
private fun OfferCard(offer: StoreOffer, enabled: Boolean, onBuy: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 620.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                offer.title.ifBlank { offer.product.entitlement.label() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (offer.description.isNotBlank()) {
                Text(
                    offer.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                offer.isOwned -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.store_owned),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                offer.isPending -> Text(
                    stringResource(R.string.store_pending),
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold,
                )

                else -> SubmitButton(
                    text = offer.formattedPrice?.let { "${stringResource(R.string.store_buy)} · $it" }
                        ?: stringResource(R.string.store_buy),
                    onClick = onBuy,
                    enabled = enabled && offer.isPurchasable,
                )
            }
        }
    }
}

@Composable
private fun RestoreRow(state: BillingState, onRestore: () -> Unit) {
    SecondarySubmitButton(
        text = stringResource(R.string.store_restore),
        onClick = onRestore,
        enabled = !state.isRestoring,
        leadingIcon = Icons.Rounded.Restore,
        modifier = Modifier
            .padding(16.dp)
            .widthIn(max = 620.dp),
    )
}

@Composable
private fun Entitlement.label(): String = stringResource(
    when (this) {
        Entitlement.REMOVE_ADS -> R.string.store_title
        Entitlement.THEME_MIDNIGHT -> R.string.theme_board_midnight
        Entitlement.THEME_SUNSET -> R.string.theme_board_sunset
    },
)
