package com.duzman46.gridbound.game.models

import com.duzman46.gridbound.core.Constants

data class BoardState(
    val players: Map<PlayerId, Player>,
    val walls: Set<Wall>,
    val currentPlayer: PlayerId,
    val status: GameStatus,
    val turnNumber: Int,
    val history: List<TurnRecord>,
) {
    init {
        require(players.keys == PlayerId.entries.toSet())
        require(walls.size <= Constants.Board.MAX_PLACED_WALLS)
        require(turnNumber >= 1)
    }

    fun player(id: PlayerId): Player = checkNotNull(players[id])

    fun pawn(id: PlayerId): Pawn = Pawn(id, player(id).position)

    companion object {
        fun initial(): BoardState = BoardState(
            players = PlayerId.entries.associateWith { id ->
                Player(
                    id = id,
                    position = id.startPosition,
                    wallsRemaining = Constants.Board.STARTING_WALLS,
                )
            },
            walls = emptySet(),
            currentPlayer = PlayerId.PLAYER_ONE,
            status = GameStatus.IN_PROGRESS,
            turnNumber = 1,
            history = emptyList(),
        )
    }
}

