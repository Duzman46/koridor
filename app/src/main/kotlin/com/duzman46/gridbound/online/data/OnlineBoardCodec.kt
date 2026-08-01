package com.duzman46.gridbound.online.data

import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.Player
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.TurnRecord
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.online.model.OnlineRoom
import com.duzman46.gridbound.online.model.OnlineRoomStatus
import javax.inject.Inject

class OnlineBoardCodec @Inject constructor() {
    fun encodeBoard(state: BoardState): Map<String, Any?> = mapOf(
        "currentPlayer" to state.currentPlayer.name,
        "status" to state.status.name,
        "turnNumber" to state.turnNumber,
        "players" to state.players.mapKeys { it.key.name }.mapValues { (_, player) ->
            mapOf(
                "row" to player.position.row,
                "column" to player.position.column,
                "wallsRemaining" to player.wallsRemaining,
            )
        },
        "walls" to state.walls.sortedWith(
            compareBy<Wall>({ it.row }, { it.column }, { it.orientation.name }),
        ).map(::encodeWall),
        "history" to state.history.map(::encodeTurn),
    )

    fun decodeBoard(value: Any?): BoardState? = runCatching {
        val map = value.asStringMap()
        val playerValues = map["players"].asStringMap()
        val players = PlayerId.entries.associateWith { playerId ->
            val playerMap = playerValues[playerId.name].asStringMap()
            Player(
                id = playerId,
                position = Position(playerMap.int("row"), playerMap.int("column")),
                wallsRemaining = playerMap.int("wallsRemaining"),
            )
        }
        BoardState(
            players = players,
            walls = map["walls"].asList().mapNotNull(::decodeWall).toSet(),
            currentPlayer = PlayerId.valueOf(map.string("currentPlayer")),
            status = GameStatus.valueOf(map.string("status")),
            turnNumber = map.int("turnNumber"),
            history = map["history"].asList().mapNotNull(::decodeTurn),
        )
    }.getOrNull()

    fun decodeRoom(code: String, value: Any?): OnlineRoom? = runCatching {
        val map = value.asStringMap()
        OnlineRoom(
            code = code,
            hostUid = map.string("hostUid"),
            guestUid = map.optionalString("guestUid"),
            status = OnlineRoomStatus.valueOf(map.string("status")),
            revision = map.long("revision"),
            boardState = checkNotNull(decodeBoard(map["board"])),
            createdAt = map.long("createdAt"),
            updatedAt = map.long("updatedAt"),
        )
    }.getOrNull()

    private fun encodeWall(wall: Wall): Map<String, Any> = mapOf(
        "row" to wall.row,
        "column" to wall.column,
        "orientation" to wall.orientation.name,
    )

    private fun decodeWall(value: Any?): Wall? = runCatching {
        val map = value.asStringMap()
        Wall(map.int("row"), map.int("column"), WallOrientation.valueOf(map.string("orientation")))
    }.getOrNull()

    private fun encodeTurn(turn: TurnRecord): Map<String, Any> = buildMap {
        put("turnNumber", turn.turnNumber)
        put("player", turn.player.name)
        when (val action = turn.action) {
            is GameAction.MovePawn -> {
                put("action", "MOVE")
                put("row", action.target.row)
                put("column", action.target.column)
            }

            is GameAction.PlaceWall -> {
                put("action", "WALL")
                put("row", action.wall.row)
                put("column", action.wall.column)
                put("orientation", action.wall.orientation.name)
            }
        }
    }

    private fun decodeTurn(value: Any?): TurnRecord? = runCatching {
        val map = value.asStringMap()
        val action = when (map.string("action")) {
            "MOVE" -> GameAction.MovePawn(Position(map.int("row"), map.int("column")))
            "WALL" -> GameAction.PlaceWall(
                Wall(map.int("row"), map.int("column"), WallOrientation.valueOf(map.string("orientation"))),
            )
            else -> error("Unknown action")
        }
        TurnRecord(map.int("turnNumber"), PlayerId.valueOf(map.string("player")), action)
    }.getOrNull()

    private fun Any?.asStringMap(): Map<String, Any?> = (this as? Map<*, *>)
        ?.entries
        ?.associate { (key, value) -> key.toString() to value }
        ?: error("Expected map")

    private fun Any?.asList(): List<Any?> = when (this) {
        null -> emptyList()
        is List<*> -> this
        is Map<*, *> -> entries.sortedBy { it.key.toString().toIntOrNull() ?: Int.MAX_VALUE }.map { it.value }
        else -> error("Expected list")
    }

    private fun Map<String, Any?>.string(key: String): String = get(key)?.toString()?.takeIf(String::isNotBlank)
        ?: error("Missing $key")

    private fun Map<String, Any?>.optionalString(key: String): String? = get(key)?.toString()?.takeIf(String::isNotBlank)

    private fun Map<String, Any?>.int(key: String): Int = (get(key) as? Number)?.toInt() ?: error("Missing $key")

    private fun Map<String, Any?>.long(key: String): Long = (get(key) as? Number)?.toLong() ?: 0L
}

