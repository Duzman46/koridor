package com.duzman46.gridbound.game.models

import com.duzman46.gridbound.core.Constants

enum class WallOrientation {
    HORIZONTAL,
    VERTICAL,
}

data class Wall(
    val row: Int,
    val column: Int,
    val orientation: WallOrientation,
) {
    init {
        require(row in 0 until Constants.Board.WALL_GRID_SIZE)
        require(column in 0 until Constants.Board.WALL_GRID_SIZE)
    }
}

