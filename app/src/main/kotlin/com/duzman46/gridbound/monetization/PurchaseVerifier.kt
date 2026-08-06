package com.duzman46.gridbound.monetization

import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.data.firebase.runTransactionSuspend
import com.duzman46.gridbound.monetization.domain.PurchaseRecord
import com.duzman46.gridbound.online.data.FirebaseProvider
import com.google.firebase.database.Transaction
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Files a purchase token for server-side verification.
 *
 * The client never decides that a token is valid. It writes a receipt, and the Cloud Function
 * in functions/src/purchases.ts checks it against the Google Play Developer API before
 * granting anything durable. Two protections matter here:
 *
 *  - the token is stored hashed, so a database read cannot leak a usable purchase token;
 *  - the receipt node is write-once, so the same token can never be filed twice, which is
 *    what stops one purchase being redeemed on several accounts.
 */
@Singleton
class PurchaseVerifier @Inject constructor(
    private val firebase: FirebaseProvider,
) {
    suspend fun submit(accountId: String?, record: PurchaseRecord) {
        // A guest has no durable account to attach a purchase to. Play still honours it on
        // this device; the store screen explains why linking is needed to keep it.
        if (accountId.isNullOrBlank() || !firebase.isConfigured) return

        val tokenHash = hash(record.purchaseToken)
        runCatching {
            firebase.database
                .getReference(Constants.Billing.PURCHASE_RECEIPTS_PATH)
                .child(accountId)
                .child(tokenHash)
                .runTransactionSuspend { current ->
                    // Already filed: leave the earlier receipt and its verdict alone.
                    if (current.value != null) return@runTransactionSuspend Transaction.abort()
                    current.value = mapOf(
                        Keys.PURCHASE_TOKEN to record.purchaseToken,
                        Keys.PRODUCT_IDS to record.productIds,
                        Keys.ORDER_ID to record.orderId.orEmpty(),
                        Keys.PURCHASE_TIME to record.purchaseTimeMillis,
                        Keys.STATE to VerificationState.PENDING.name,
                        Keys.SUBMITTED_AT to System.currentTimeMillis(),
                    )
                    Transaction.success(current)
                }
        }.onFailure { AppLog.warn("submit-purchase-receipt", it) }
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }

    /** Where verification has got to. Written by the function, read by nobody but support. */
    enum class VerificationState {
        PENDING,
        VERIFIED,
        REJECTED,
        /** Refunded or charged back; the entitlement is withdrawn. */
        REVOKED,
    }

    object Keys {
        const val PURCHASE_TOKEN = "purchaseToken"
        const val PRODUCT_IDS = "productIds"
        const val ORDER_ID = "orderId"
        const val PURCHASE_TIME = "purchaseTime"
        const val STATE = "state"
        const val SUBMITTED_AT = "submittedAt"
    }
}
