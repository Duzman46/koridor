package com.duzman46.gridbound.online.domain

import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineSession
import kotlinx.coroutines.flow.Flow

interface OnlineGameRepository {
    val isConfigured: Boolean

    suspend fun createRoom(): OnlineLobbyResult
    suspend fun joinRoom(roomCode: String): OnlineLobbyResult
    fun observeRoom(roomCode: String): Flow<OnlineRoom>
    suspend fun submitAction(session: OnlineSession, expectedRevision: Long, action: GameAction): Boolean
    suspend fun leaveRoom(session: OnlineSession)
}

