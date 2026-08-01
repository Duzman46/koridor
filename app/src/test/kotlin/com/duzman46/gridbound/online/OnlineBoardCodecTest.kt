package com.duzman46.gridbound.online

import com.duzman46.gridbound.game.TestFixtures
import com.duzman46.gridbound.game.models.ActionResult
import com.duzman46.gridbound.game.models.GameAction
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.online.data.OnlineBoardCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OnlineBoardCodecTest {
    private val codec = OnlineBoardCodec()

    @Test
    fun roundTripPreservesMovesWallsPlayersAndHistory() {
        val manager = TestFixtures.manager()
        manager.perform(GameAction.MovePawn(Position(7, 4)))
        val result = manager.perform(GameAction.PlaceWall(Wall(1, 2, WallOrientation.HORIZONTAL)))
        val state = (result as ActionResult.Success).state

        val decoded = codec.decodeBoard(codec.encodeBoard(state))

        assertNotNull(decoded)
        assertEquals(state, decoded)
    }
}

