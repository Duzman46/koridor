package com.duzman46.gridbound.game.models

enum class GameMode {
    LOCAL_TWO_PLAYER,
    VS_AI,
    ONLINE,
}

/**
 * How hard the bot plays. Declared weakest first — [Difficulty.entries] is the order they are
 * offered in and the order statistics are listed in.
 */
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT,
}

enum class GameStatus {
    IN_PROGRESS,
    PLAYER_ONE_WON,
    PLAYER_TWO_WON;

    val winner: PlayerId?
        get() = when (this) {
            IN_PROGRESS -> null
            PLAYER_ONE_WON -> PlayerId.PLAYER_ONE
            PLAYER_TWO_WON -> PlayerId.PLAYER_TWO
        }
}

sealed interface GameAction {
    data class MovePawn(val target: Position) : GameAction
    data class PlaceWall(val wall: Wall) : GameAction
}

enum class InvalidActionReason {
    GAME_FINISHED,
    INVALID_MOVE,
    INVALID_WALL,
    NO_WALLS_REMAINING,
}

sealed interface ActionResult {
    data class Success(val state: BoardState) : ActionResult
    data class Invalid(val reason: InvalidActionReason) : ActionResult
}

data class TurnRecord(
    val turnNumber: Int,
    val player: PlayerId,
    val action: GameAction,
)
