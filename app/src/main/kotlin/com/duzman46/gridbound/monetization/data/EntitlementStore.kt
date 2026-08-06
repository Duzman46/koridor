package com.duzman46.gridbound.monetization.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.monetization.domain.Entitlement
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.entitlementDataStore by preferencesDataStore("koridor_entitlements")

/**
 * On-device record of what an account owns.
 *
 * Entitlements are stored per account id, never in one shared bucket, so signing into a
 * second account on the same phone cannot inherit the first account's purchases. The guest
 * bucket is kept separate too and is carried over when a guest links an account.
 */
@Singleton
class EntitlementStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Used before there is an identity, and for a guest who has not linked yet. */
    private val guestKey = "guest"

    fun observe(accountId: String?): Flow<Set<Entitlement>> =
        context.entitlementDataStore.data
            .catch { error ->
                if (error is IOException) {
                    AppLog.warn("entitlement-store-read", error)
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { preferences -> decode(preferences[keyFor(accountId)]) }

    suspend fun store(accountId: String?, entitlements: Set<Entitlement>) {
        runCatching {
            context.entitlementDataStore.edit { preferences ->
                preferences[keyFor(accountId)] = entitlements.joinToString(",") { it.name }
            }
        }.onFailure { AppLog.warn("entitlement-store-write", it) }
    }

    /**
     * Moves what the guest owned onto the account they just linked, so a purchase made
     * before signing up is not stranded.
     */
    suspend fun migrateGuestPurchases(accountId: String) {
        runCatching {
            context.entitlementDataStore.edit { preferences ->
                val guestOwned = decode(preferences[keyFor(null)])
                if (guestOwned.isEmpty()) return@edit
                val existing = decode(preferences[keyFor(accountId)])
                preferences[keyFor(accountId)] =
                    (existing + guestOwned).joinToString(",") { it.name }
                preferences.remove(keyFor(null))
            }
        }.onFailure { AppLog.warn("entitlement-migrate", it) }
    }

    suspend fun clear(accountId: String?) {
        runCatching {
            context.entitlementDataStore.edit { it.remove(keyFor(accountId)) }
        }.onFailure { AppLog.warn("entitlement-clear", it) }
    }

    private fun keyFor(accountId: String?) =
        stringPreferencesKey("owned_${accountId?.takeIf(String::isNotBlank) ?: guestKey}")

    private fun decode(raw: String?): Set<Entitlement> = raw
        ?.split(',')
        ?.mapNotNull { name -> Entitlement.entries.firstOrNull { it.name == name } }
        ?.toSet()
        .orEmpty()
}
