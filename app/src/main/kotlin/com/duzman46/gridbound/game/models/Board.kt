package com.duzman46.gridbound.game.models

import com.duzman46.gridbound.core.Constants

data class Tile(val position: Position)

class Board {
    val size: Int = Constants.Board.SIZE
    val tiles: List<Tile> = List(size * size) { index ->
        Tile(Position(index / size, index % size))
    }

    fun contains(position: Position): Boolean =
        position.row in 0 until size && position.column in 0 until size
}

