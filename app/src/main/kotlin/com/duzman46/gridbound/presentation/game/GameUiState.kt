package com.duzman46.gridbound.presentation.game

import com.duzman46.gridbound.game.audio.SoundEffect
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
    val pawnSelected: Boolean = false,
    val validMoves: Set<Position> = emptySet(),
    val wallMode: Boolean = false,
    val wallOrientation: WallOrientation = WallOrientation.HORIZONTAL,
    val validWalls: Set<Wall> = emptySet(),
    val invalidWallPreview: Wall? = null,
    val recentlyPlacedWall: Wall? = null,
    val isAiThinking: Boolean = false,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val canUndo: Boolean = false,
) {
    val acceptsHumanInput: Boolean
        get() = !isAiThinking &&
            (mode == GameMode.LOCAL_TWO_PLAYER || boardState.currentPlayer == PlayerId.PLAYER_ONE)
}

sealed interface GameEvent {
    data class Feedback(val effect: SoundEffect, val hapticsEnabled: Boolean) : GameEvent
}

