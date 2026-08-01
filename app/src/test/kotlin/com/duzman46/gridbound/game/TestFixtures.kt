package com.duzman46.gridbound.game

import com.duzman46.gridbound.game.board.BoardGraph
import com.duzman46.gridbound.game.engine.GameEngine
import com.duzman46.gridbound.game.engine.GameManager
import com.duzman46.gridbound.game.engine.TurnManager
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.Player
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.pathfinding.AStarPathFinder
import com.duzman46.gridbound.game.pathfinding.BFSValidator
import com.duzman46.gridbound.game.rules.MoveValidator
import com.duzman46.gridbound.game.rules.RuleEngine
import com.duzman46.gridbound.game.rules.VictoryChecker
import com.duzman46.gridbound.game.rules.WallValidator

internal object TestFixtures {
    val graph = BoardGraph()
    val bfs = BFSValidator(graph)
    val aStar = AStarPathFinder(graph)
    val moves = MoveValidator(graph)
    val walls = WallValidator(bfs)
    val rules = RuleEngine(moves, walls)
    val engine = GameEngine(rules, VictoryChecker(), TurnManager())

    fun manager(): GameManager = GameManager(engine, moves, walls)

    fun state(
        playerOne: Position,
        playerTwo: Position,
        walls: Set<Wall> = emptySet(),
        current: PlayerId = PlayerId.PLAYER_ONE,
    ): BoardState = BoardState.initial().copy(
        players = mapOf(
            PlayerId.PLAYER_ONE to Player(PlayerId.PLAYER_ONE, playerOne, 10),
            PlayerId.PLAYER_TWO to Player(PlayerId.PLAYER_TWO, playerTwo, 10),
        ),
        walls = walls,
        currentPlayer = current,
    )
}

