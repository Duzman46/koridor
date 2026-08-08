package com.duzman46.gridbound.online.data

import com.duzman46.gridbound.core.Constants
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

    /**
     * The rating a queue entry must carry: whatever `users/{uid}/rating` holds, and nothing else.
     *
     * The rule compares the two for exact equality, so a guess is not a degraded answer — it is a
     * refused write, thrown as a database exception and shown to the player as "something went
     * wrong" the instant they press Quick match. This used to fall straight to the starting
     * rating whenever the profile read came back empty, which is right for exactly as long as
     * nobody has played a rated match: the first rating to move made every such press fail, and
     * intermittently, because whether it failed depended on whether that one read had succeeded.
     *
     * @param profileRating from the profile already in hand, or null when that read failed.
     * @param storedRating the same number read straight from its own node, or null when that
     *   read failed too. Only a player who has no rating anywhere reaches the fallback, and
     *   nobody with a profile is one.
     */
    fun queueRating(profileRating: Int?, storedRating: Int?): Int =
        profileRating ?: storedRating ?: Constants.Backend.STARTING_RATING

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
