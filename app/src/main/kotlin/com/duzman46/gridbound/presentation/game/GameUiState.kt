package com.duzman46.gridbound.presentation.game

import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.game.audio.SoundEffect
import com.duzman46.gridbound.game.board.BoardTheme
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation

data class GameUiState(
    val boardState: BoardState = BoardState.initial(),
    val mode: GameMode = GameMode.VS_AI,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val localPlayer: PlayerId? = null,
    val pawnSelected: Boolean = false,
    val validMoves: Set<Position> = emptySet(),
    val wallMode: Boolean = false,
    val wallOrientation: WallOrientation = WallOrientation.HORIZONTAL,
    val validWalls: Set<Wall> = emptySet(),
    val pendingWall: Wall? = null,
    val invalidWallPreview: Wall? = null,
    val recentlyPlacedWall: Wall? = null,
    val isAiThinking: Boolean = false,
    val isOnlineConnected: Boolean = false,
    val isOnlineSyncing: Boolean = false,
    val onlineMessage: UiText? = null,
    val isRanked: Boolean = false,
    /** Wall-clock instant the current player's move clock runs out; null when untimed. */
    val turnDeadlineAt: Long? = null,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val canUndo: Boolean = false,
    val boardTheme: BoardTheme = BoardTheme.CLASSIC,
) {
    val isOnline: Boolean get() = mode == GameMode.ONLINE

    /** True once the rival's clock has run out and the win can be claimed. */
    fun canClaimTimeout(now: Long): Boolean =
        isOnline &&
            turnDeadlineAt != null &&
            now > turnDeadlineAt &&
            boardState.status == com.duzman46.gridbound.game.models.GameStatus.IN_PROGRESS &&
            boardState.currentPlayer != localPlayer

    val acceptsHumanInput: Boolean
        get() = !isAiThinking && !isOnlineSyncing && when (mode) {
            GameMode.LOCAL_TWO_PLAYER -> true
            GameMode.VS_AI -> boardState.currentPlayer == PlayerId.PLAYER_ONE
            GameMode.ONLINE -> isOnlineConnected && boardState.currentPlayer == localPlayer
        }
}

sealed interface GameEvent {
    data class Feedback(val effect: SoundEffect, val hapticsEnabled: Boolean) : GameEvent
}
