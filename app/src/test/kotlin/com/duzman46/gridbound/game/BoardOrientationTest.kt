package com.duzman46.gridbound.game

import androidx.compose.ui.geometry.Offset
import com.duzman46.gridbound.game.board.BoardOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardOrientationTest {
    @Test
    fun flippedBoardMapsTouchToOppositeCoordinates() {
        val mapped = BoardOrientation.toModelOffset(Offset(25f, 80f), 100f, 100f, flipped = true)

        assertEquals(Offset(75f, 20f), mapped)
    }

    @Test
    fun normalBoardKeepsTouchCoordinates() {
        val offset = Offset(25f, 80f)

        assertEquals(offset, BoardOrientation.toModelOffset(offset, 100f, 100f, flipped = false))
    }
}
