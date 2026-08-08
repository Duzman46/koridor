package com.duzman46.gridbound.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.di.ApplicationScope
import com.duzman46.gridbound.monetization.data.BillingOutcome
import com.duzman46.gridbound.monetization.data.EntitlementStore
import com.duzman46.gridbound.monetization.data.PurchaseMapper
import com.duzman46.gridbound.monetization.domain.BillingMessage
import com.duzman46.gridbound.monetization.domain.Entitlement
import com.duzman46.gridbound.monetization.domain.ProductCatalog
import com.duzman46.gridbound.monetization.domain.ProductKind
import com.duzman46.gridbound.monetization.domain.PurchaseRecord
import com.duzman46.gridbound.monetization.domain.PurchaseState
import com.duzman46.gridbound.monetization.domain.StoreOffer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BillingState(
    val isReady: Boolean = false,
    val offers: List<StoreOffer> = emptyList(),
    val entitlements: Set<Entitlement> = emptySet(),
    val pendingPurchases: List<PurchaseRecord> = emptyList(),
    val isRestoring: Boolean = false,
    val message: BillingMessage? = null,
) {
    fun owns(entitlement: Entitlement): Boolean = entitlement in entitlements

    val hasPending: Boolean get() = pendingPurchases.isNotEmpty()
}

/**
 * What handing [BillingManager] an account id has to set in motion.
 *
 * Extracted because getting it wrong is invisible: the entitlements the app acts on come from
 * one reader and nothing else feeds them, so a call that quietly starts none leaves a player
 * who has paid looking at ads for the rest of the process, with the purchase sitting correctly
 * on disk the whole time.
 */
internal data class AccountSwitch(
    val restartsReader: Boolean,
    val migratesGuestPurchases: Boolean,
) {
    companion object {
        /**
         * @param reading whether a reader is already running. This, rather than the id having
         *   moved, is what decides: the first call of every process carries null, and judging
         *   it against a field that also starts null read as "nothing changed" — so a launch
         *   that went straight into guest play never started a reader at all.
         */
        fun of(reading: Boolean, current: String?, next: String?): AccountSwitch = AccountSwitch(
            restartsReader = !reading || current != next,
            // A guest who has just linked keeps whatever they bought as a guest. Only on a
            // genuine change: the first call of a process has moved nothing to carry over.
            migratesGuestPurchases = reading && current == null && next != null,
        )
    }
}

/**
 * Owns the Play Billing connection and turns purchases into entitlements.
 *
 * What this deliberately does not do is treat a purchase token as proof on its own. Tokens
 * are handed to [PurchaseVerifier] for server-side verification against the Play Developer
 * API; the local grant that happens meanwhile comes from Play Services' own signed response,
 * and the server copy is what survives a reinstall or a new device.
 */
@Singleton
class BillingManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val entitlementStore: EntitlementStore,
    private val verifier: PurchaseVerifier,
    @param:ApplicationScope private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    private val _state = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = _state.asStateFlow()

    private var productDetails: Map<String, ProductDetails> = emptyMap()
    private var connecting = false

    /** The account entitlements belong to. Null while signed out or playing as a guest. */
    private var accountId: String? = null

    /**
     * Reads the bucket [accountId] names into [BillingState.entitlements].
     *
     * Null means nothing is reading it, which is a different thing from "reading the guest
     * bucket" and is the whole of what went wrong: the field it used to be judged by starts
     * null too, so the first call — always null, from a session that is still loading — was
     * taken for "no change" and returned before starting anything. A process that then went
     * on to guest play never started a reader at all, and every purchase Play reported was
     * written to disk and read back by nobody. A player who had paid got the ads back.
     */
    private var entitlementJob: Job? = null

    /**
     * Tokens already turned into entitlements in this process. Play redelivers purchases on
     * every query, and this stops one being counted twice within a session; the durable
     * guarantee is the write-once receipt node the verifier uses.
     */
    private val redeemedTokens = mutableSetOf<String>()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .enableAutoServiceReconnection()
        .build()

    /** Called whenever the signed-in account changes, including on sign-out. */
    fun onAccountChanged(newAccountId: String?) {
        val switch = AccountSwitch.of(
            reading = entitlementJob != null,
            current = accountId,
            next = newAccountId,
        )
        if (!switch.restartsReader) return
        accountId = newAccountId
        redeemedTokens.clear()
        // One reader at a time. Left running, the reader for the account before this one would
        // keep writing its own bucket into the state on every write to the store — DataStore
        // hands every collector the whole of it — and after two account changes whichever of
        // the three woke last would decide what this player owns.
        entitlementJob?.cancel()
        entitlementJob = scope.launch {
            if (switch.migratesGuestPurchases && newAccountId != null) {
                entitlementStore.migrateGuestPurchases(newAccountId)
            }
            entitlementStore.observe(newAccountId).collect { owned ->
                _state.update { it.copy(entitlements = owned) }
            }
        }
        connect()
    }

    fun connect() {
        if (!ProductCatalog.hasAnythingToSell) return
        if (billingClient.isReady) {
            refresh()
            return
        }
        if (connecting) return
        connecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                val ready = PurchaseMapper.outcomeOf(result.responseCode) == BillingOutcome.OK
                _state.update { it.copy(isReady = ready) }
                if (ready) refresh()
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                _state.update { it.copy(isReady = false) }
            }
        })
    }

    fun purchase(activity: Activity, entitlement: Entitlement) {
        val product = ProductCatalog.forEntitlement(entitlement) ?: return
        val details = productDetails[product.productId] ?: run {
            report(BillingMessage.Error(UiText.Res(R.string.billing_error_unavailable)))
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        val outcome = PurchaseMapper.outcomeOf(result.responseCode)
        if (outcome.isWorthReporting) {
            AppLog.warn("launch-billing-flow-${outcome.name}")
            report(BillingMessage.Error(messageFor(outcome)))
        }
    }

    /** Re-reads what Play says this account owns. Backs the "restore purchases" button. */
    fun restorePurchases() {
        _state.update { it.copy(isRestoring = true, message = null) }
        if (!billingClient.isReady) {
            connect()
            return
        }
        refresh(announceRestore = true)
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (val outcome = PurchaseMapper.outcomeOf(result.responseCode)) {
            BillingOutcome.OK -> purchases?.let { handlePurchases(it, definitive = false) }
            // Backing out is a decision, not a failure: stay quiet.
            BillingOutcome.CANCELLED -> Unit
            BillingOutcome.ALREADY_OWNED -> restorePurchases()
            else -> {
                AppLog.warn("purchases-updated-${outcome.name}")
                report(BillingMessage.Error(messageFor(outcome)))
            }
        }
    }

    private fun refresh(announceRestore: Boolean = false) {
        queryOffers()
        queryPurchases(announceRestore)
    }

    private fun queryOffers() {
        if (ProductCatalog.inAppProductIds.isEmpty()) return
        val products = ProductCatalog.inAppProductIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build(),
        ) { result, queryResult ->
            if (PurchaseMapper.outcomeOf(result.responseCode) != BillingOutcome.OK) {
                AppLog.warn("query-product-details")
                return@queryProductDetailsAsync
            }
            productDetails = queryResult.productDetailsList.associateBy(ProductDetails::getProductId)
            rebuildOffers()
        }
    }

    private fun queryPurchases(announceRestore: Boolean) {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { result, purchases ->
            if (PurchaseMapper.outcomeOf(result.responseCode) != BillingOutcome.OK) {
                _state.update { it.copy(isRestoring = false) }
                return@queryPurchasesAsync
            }
            handlePurchases(purchases, definitive = true)
            if (announceRestore) {
                report(BillingMessage.Info(UiText.Res(R.string.billing_restore_complete)))
            }
            _state.update { it.copy(isRestoring = false) }
        }
    }

    /**
     * @param definitive true when the list came from a full query, which means an
     *   entitlement missing from it has genuinely been refunded or revoked.
     */
    private fun handlePurchases(purchases: List<Purchase>, definitive: Boolean) {
        val records = purchases.map(PurchaseMapper::toRecord)
        val owned = records.filter { it.state == PurchaseState.OWNED }
        val pending = records.filter { it.state == PurchaseState.PENDING }

        owned.forEach(::finalisePurchase)

        val grantedNow = owned.flatMap { it.entitlements }.toSet()
        scope.launch {
            val current = _state.value.entitlements
            // A definitive query is the whole truth, so a refund removes access. An update
            // callback only ever adds, because it carries just the purchase that changed.
            val next = if (definitive) grantedNow else current + grantedNow
            if (next != current) entitlementStore.store(accountId, next)
        }
        _state.update { it.copy(pendingPurchases = pending) }
        rebuildOffers()
    }

    /**
     * Acknowledges or consumes, and files the token for server-side verification. A
     * non-consumable left unacknowledged for three days is refunded by Play automatically.
     */
    private fun finalisePurchase(record: PurchaseRecord) {
        if (!redeemedTokens.add(record.purchaseToken)) return

        scope.launch { verifier.submit(accountId, record) }

        val consumable = record.productIds
            .mapNotNull(ProductCatalog::forProductId)
            .any { it.kind == ProductKind.CONSUMABLE }

        if (consumable) {
            billingClient.consumeAsync(
                ConsumeParams.newBuilder().setPurchaseToken(record.purchaseToken).build(),
            ) { result, _ ->
                if (PurchaseMapper.outcomeOf(result.responseCode) != BillingOutcome.OK) {
                    AppLog.warn("consume-purchase")
                    // Allow a later query to try again.
                    redeemedTokens.remove(record.purchaseToken)
                }
            }
        } else if (record.needsAcknowledgement) {
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(record.purchaseToken)
                    .build(),
            ) { result ->
                if (PurchaseMapper.outcomeOf(result.responseCode) != BillingOutcome.OK) {
                    AppLog.warn("acknowledge-purchase")
                    redeemedTokens.remove(record.purchaseToken)
                }
            }
        }
    }

    private fun rebuildOffers() {
        val owned = _state.value.entitlements
        val pendingProductIds = _state.value.pendingPurchases.flatMap { it.productIds }.toSet()
        _state.update { state ->
            state.copy(
                offers = ProductCatalog.configured.map { product ->
                    val details = productDetails[product.productId]
                    StoreOffer(
                        product = product,
                        title = details?.title.orEmpty(),
                        description = details?.description.orEmpty(),
                        formattedPrice = details?.oneTimePurchaseOfferDetails?.formattedPrice,
                        isOwned = product.entitlement in owned,
                        isPending = product.productId in pendingProductIds,
                    )
                },
            )
        }
    }

    private fun report(message: BillingMessage) = _state.update { it.copy(message = message) }

    private fun messageFor(outcome: BillingOutcome): UiText = when (outcome) {
        BillingOutcome.UNAVAILABLE -> UiText.Res(R.string.billing_error_unavailable)
        BillingOutcome.UNSUPPORTED -> UiText.Res(R.string.billing_error_unsupported)
        else -> UiText.Res(R.string.billing_error_failed)
    }
}
