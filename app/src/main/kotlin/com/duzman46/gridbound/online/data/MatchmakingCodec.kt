package com.duzman46.gridbound.online.data

import com.duzman46.gridbound.online.model.MatchmakingEntry
import com.google.firebase.database.DataSnapshot

/**
 * Maps between [MatchmakingEntry] and its Realtime Database shape.
 *
 * The field names are the contract shared with database.rules.json — which pins `rating`
 * against the player's own profile so it cannot be understated to farm easy opponents — and
 * with the worker that pairs whoever the phones leave behind. Renaming one means renaming it
 * in all three.
 */
object MatchmakingCodec {

    /** Null for an entry missing anything pairing needs, which no build of this app writes. */
    fun decode(snapshot: DataSnapshot): MatchmakingEntry? {
        val userId = snapshot.key?.takeIf(String::isNotBlank) ?: return null
        // Read as Long and narrowed, because the database has one number type and asking it
        // for an Int is asking it to convert on our behalf.
        val rating = snapshot.child(Keys.RATING).getValue(Long::class.java) ?: return null
        val queuedAt = snapshot.child(Keys.QUEUED_AT).getValue(Long::class.java) ?: return null
        return MatchmakingEntry(
            userId = userId,
            rating = rating.toInt(),
            ranked = snapshot.child(Keys.RANKED).getValue(Boolean::class.java) ?: false,
            queuedAt = queuedAt,
        )
    }

    object Keys {
        const val RATING = "rating"
        const val RANKED = "ranked"
        const val QUEUED_AT = "queuedAt"
    }
}
