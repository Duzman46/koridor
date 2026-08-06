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
}
