package com.duzman46.gridbound.game.ai.search

import com.duzman46.gridbound.core.Constants
import kotlin.random.Random

/**
 * The random keys the search hashes a position with.
 *
 * `(pawns, walls, reserves, side)` fully determines a Quoridor position as this project models it:
 * there is no repetition rule, and neither `RuleEngine` nor `VictoryChecker` reads `history` or
 * `turnNumber`, so hashing either would only make two identical positions look different.
 *
 * [reserve] is not optional. The same geometry with different wall stocks is a different position —
 * one side can still build and the other cannot — and omitting it would let the table answer a
 * question it was never asked.
 *
 * @param seed fixed per [SearchConfig] so a search is reproducible across runs and machines.
 */
internal class Zobrist(seed: Long) {
    val pawn = Array(2) { LongArray(WallGeometry.CELL_COUNT) }
    val wall = LongArray(WallGeometry.SLOT_COUNT)
    val reserve = Array(2) { LongArray(Constants.Board.STARTING_WALLS + 1) }
    val side: Long

    init {
        val random = Random(seed)
        for (player in 0..1) {
            for (cell in 0 until WallGeometry.CELL_COUNT) pawn[player][cell] = random.nextLong()
        }
        for (slot in 0 until WallGeometry.SLOT_COUNT) wall[slot] = random.nextLong()
        for (player in 0..1) {
            for (count in reserve[player].indices) reserve[player][count] = random.nextLong()
        }
        side = random.nextLong()
    }
}
