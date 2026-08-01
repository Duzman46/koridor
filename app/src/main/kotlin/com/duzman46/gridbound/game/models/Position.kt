package com.duzman46.gridbound.game.models

import com.duzman46.gridbound.core.Constants

data class Position(val row: Int, val column: Int) {
    init {
        require(row in 0 until Constants.Board.SIZE)
        require(column in 0 until Constants.Board.SIZE)
    }

    fun offsetOrNull(rowDelta: Int, columnDelta: Int): Position? {
        val nextRow = row + rowDelta
        val nextColumn = column + columnDelta
        return if (nextRow in 0 until Constants.Board.SIZE && nextColumn in 0 until Constants.Board.SIZE) {
            Position(nextRow, nextColumn)
        } else {
            null
        }
    }
}

enum class Direction(val rowDelta: Int, val columnDelta: Int) {
    UP(-1, 0),
    DOWN(1, 0),
    LEFT(0, -1),
    RIGHT(0, 1),
}

