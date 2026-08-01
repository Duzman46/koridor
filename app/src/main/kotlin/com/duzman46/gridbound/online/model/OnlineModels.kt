package com.duzman46.gridbound.online.model

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.PlayerId

enum class OnlineRoomStatus {
    WAITING,
    ACTIVE,
    FINISHED,
    ABANDONED,
}

data class OnlineSession(
    val roomCode: String,
    val userId: String,
    val playerId: PlayerId,
)

data class OnlineRoom(
    val code: String,
    val hostUid: String,
    val guestUid: String?,
    val status: OnlineRoomStatus,
    val revision: Long,
    val boardState: BoardState,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun playerFor(userId: String): PlayerId? = when (userId) {
        hostUid -> PlayerId.PLAYER_ONE
        guestUid -> PlayerId.PLAYER_TWO
        else -> null
    }

    fun userFor(playerId: PlayerId): String? = when (playerId) {
        PlayerId.PLAYER_ONE -> hostUid
        PlayerId.PLAYER_TWO -> guestUid
    }
}

sealed interface OnlineLobbyResult {
    data class Success(val session: OnlineSession) : OnlineLobbyResult
    data class Failure(val message: String) : OnlineLobbyResult
}

