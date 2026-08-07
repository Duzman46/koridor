package com.duzman46.gridbound.online.data

import com.duzman46.gridbound.core.Constants
import java.security.MessageDigest
import java.util.Locale
import kotlin.random.Random

/**
 * Room code generation and password hashing.
 *
 * A room password is never stored or sent in the clear. The hash lives under a node the
 * database rules make unreadable to every client, and the join rule compares the hash the
 * joiner supplies against it — so the check happens on the server without the secret ever
 * being downloadable. Salting with the room code stops one rainbow table covering all rooms.
 */
object RoomCredentials {

    fun generateCode(random: Random): String = buildString(Constants.Online.ROOM_CODE_LENGTH) {
        repeat(Constants.Online.ROOM_CODE_LENGTH) {
            append(
                Constants.Online.ROOM_CODE_ALPHABET[
                    random.nextInt(Constants.Online.ROOM_CODE_ALPHABET.length),
                ],
            )
        }
    }

    fun normalizeCode(raw: String): String = raw.trim()
        .uppercase(Locale.ROOT)
        .filter(Constants.Online.ROOM_CODE_ALPHABET::contains)
        .take(Constants.Online.ROOM_CODE_LENGTH)

    fun isValidCode(code: String): Boolean =
        code.length == Constants.Online.ROOM_CODE_LENGTH &&
            code.all(Constants.Online.ROOM_CODE_ALPHABET::contains)

    /** Returns null when the room has no password. */
    fun hashPassword(roomCode: String, password: String): String? {
        if (password.isBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        val salted = "koridor:$roomCode:$password"
        return digest.digest(salted.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }
    }

    /**
     * The room a pairing with the player waiting as [userId] must land in.
     *
     * Derived rather than drawn, because it is the lock on claiming that player and a lock
     * only works where every claimer computes the same one. Nobody may write to anybody
     * else's queue entry, so there is nowhere else to put it: two devices that pick the same
     * opponent aim at this one node, and the transaction that creates it lets exactly one of
     * them through. The claimed player is told nothing and needs to be — the room names them,
     * and the query they are already watching turns it up.
     *
     * [queuedAt] is folded in so that queueing again never aims at the room a previous
     * attempt left behind.
     */
    fun meetingCode(userId: String, queuedAt: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("koridor:queue:$userId:$queuedAt".toByteArray(Charsets.UTF_8))
        val alphabet = Constants.Online.ROOM_CODE_ALPHABET
        return buildString(Constants.Online.ROOM_CODE_LENGTH) {
            repeat(Constants.Online.ROOM_CODE_LENGTH) { index ->
                // The alphabet has thirty-two letters and a byte has 256 values, so the fold
                // is even — no letter is likelier than another.
                append(alphabet[(digest[index].toInt() and 0xFF) % alphabet.length])
            }
        }
    }
}
