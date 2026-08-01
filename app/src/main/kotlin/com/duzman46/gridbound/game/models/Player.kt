package com.duzman46.gridbound.game.models

import com.duzman46.gridbound.core.Constants

enum class PlayerId {
    PLAYER_ONE,
    PLAYER_TWO;

    val opponent: PlayerId
        get() = if (this == PLAYER_ONE) PLAYER_TWO else PLAYER_ONE

    val startPosition: Position
        get() = when (this) {
            PLAYER_ONE -> Position(Constants.Board.PLAYER_ONE_START_ROW, Constants.Board.START_COLUMN)
            PLAYER_TWO -> Position(Constants.Board.PLAYER_TWO_START_ROW, Constants.Board.START_COLUMN)
        }

    val goalRow: Int
        get() = when (this) {
            PLAYER_ONE -> Constants.Board.PLAYER_ONE_GOAL_ROW
            PLAYER_TWO -> Constants.Board.PLAYER_TWO_GOAL_ROW
        }
}

data class Player(
    val id: PlayerId,
    val position: Position,
    val wallsRemaining: Int,
) {
    init {
        require(wallsRemaining in 0..Constants.Board.STARTING_WALLS)
    }

    fun moveTo(target: Position): Player = copy(position = target)

    fun useWall(): Player {
        require(wallsRemaining > 0)
        return copy(wallsRemaining = wallsRemaining - 1)
    }
}

