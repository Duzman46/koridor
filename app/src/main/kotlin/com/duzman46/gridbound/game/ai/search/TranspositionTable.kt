package com.duzman46.gridbound.game.ai.search

/**
 * A fixed-size, direct-mapped table of search results, keyed by the full Zobrist hash.
 *
 * Two parallel `LongArray`s rather than an array of objects: the table is probed once per node and
 * an object per entry would cost a pointer chase and a gigabyte-scale allocation churn over a
 * search. The **whole 64-bit key is stored and compared**, so an entry is returned only on a
 * genuine hash collision and never merely because two positions landed on the same index — the
 * fault that made the previous engine's cache unsound.
 *
 * Entry layout, packed into one `Long`:
 *
 * ```
 * score  bits  0..31   (Int, two's complement)
 * depth  bits 32..39
 * flag   bits 40..41   (EXACT, LOWER, UPPER)
 * move   bits 42..50
 * gen    bits 51..58
 * used   bit  63
 * ```
 *
 * The `used` bit exists because an all-zero word is otherwise a perfectly plausible entry (score 0,
 * depth 0, `EXACT`), which would make every empty slot answer as a draw-scored leaf.
 *
 * Entries survive across turns: the key covers pawns, walls, reserves and side to move, which is
 * the whole position as this project models it, so nothing about a later turn can invalidate one.
 * [newGeneration] only changes which entries lose a replacement contest.
 */
internal class TranspositionTable(sizeLog2: Int) {
    private val mask = (1 shl sizeLog2) - 1
    private val keys = LongArray(1 shl sizeLog2)
    private val data = LongArray(1 shl sizeLog2)

    private var generation = 0

    /** Ages every stored entry by one, so this search's entries outrank the previous search's. */
    fun newGeneration() {
        generation = (generation + 1) and GENERATION_WRAP
    }

    /** The index holding [hash], or -1. Every accessor below takes an index this returned. */
    fun find(hash: Long): Int {
        val index = hash.toInt() and mask
        val entry = data[index]
        return if (entry and USED_BIT != 0L && keys[index] == hash) index else -1
    }

    fun scoreAt(index: Int): Int = data[index].toInt()

    fun depthAt(index: Int): Int = ((data[index] ushr DEPTH_SHIFT) and DEPTH_MASK).toInt()

    fun flagAt(index: Int): Int = ((data[index] ushr FLAG_SHIFT) and FLAG_MASK).toInt()

    fun moveAt(index: Int): Int = ((data[index] ushr MOVE_SHIFT) and MOVE_MASK).toInt()

    /**
     * Files a result, preferring the deeper of two entries within one search but always yielding
     * to the current search over an older one — an entry from three turns ago is still *correct*,
     * yet it describes a line the search is no longer walking, so its depth should not be able to
     * hold the slot forever.
     */
    fun store(hash: Long, depth: Int, flag: Int, score: Int, move: Int) {
        val index = hash.toInt() and mask
        val existing = data[index]
        if (existing and USED_BIT != 0L) {
            val storedGeneration = (existing ushr GENERATION_SHIFT) and GENERATION_MASK
            val storedDepth = ((existing ushr DEPTH_SHIFT) and DEPTH_MASK).toInt()
            if (storedGeneration == generation.toLong() && depth < storedDepth) return
        }
        keys[index] = hash
        data[index] = (score.toLong() and SCORE_MASK) or
            (depth.toLong() shl DEPTH_SHIFT) or
            (flag.toLong() shl FLAG_SHIFT) or
            (move.toLong() shl MOVE_SHIFT) or
            (generation.toLong() shl GENERATION_SHIFT) or
            USED_BIT
    }

    companion object {
        const val FLAG_EXACT = 0
        const val FLAG_LOWER = 1
        const val FLAG_UPPER = 2

        private const val SCORE_MASK = 0xFFFF_FFFFL
        private const val DEPTH_SHIFT = 32
        private const val DEPTH_MASK = 0xFFL
        private const val FLAG_SHIFT = 40
        private const val FLAG_MASK = 0x3L
        private const val MOVE_SHIFT = 42
        private const val MOVE_MASK = 0x1FFL
        private const val GENERATION_SHIFT = 51
        private const val GENERATION_MASK = 0xFFL
        private const val GENERATION_WRAP = 0xFF
        private const val USED_BIT = 1L shl 63
    }
}
