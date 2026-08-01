package com.duzman46.gridbound.game

import androidx.compose.ui.geometry.Offset
import com.duzman46.gridbound.game.board.BoardGeometry
import com.duzman46.gridbound.game.board.TouchController
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchControllerTest {
    private val geometry = BoardGeometry(900f)
    private val controller = TouchController()

    @Test
    fun horizontalTapSnapsToNearestWallSlotFromWideArea() {
        val expected = Wall(3, 5, WallOrientation.HORIZONTAL)
        val center = geometry.wallRect(expected).center
        val tapAboveTheThinWall = Offset(center.x + geometry.tileSize * 0.45f, center.y - geometry.tileSize * 0.32f)

        assertEquals(expected, controller.wallAt(tapAboveTheThinWall, geometry, WallOrientation.HORIZONTAL))
    }

    @Test
    fun verticalTapSnapsToNearestWallSlotFromWideArea() {
        val expected = Wall(5, 2, WallOrientation.VERTICAL)
        val center = geometry.wallRect(expected).center
        val tapBesideTheThinWall = Offset(center.x + geometry.tileSize * 0.3f, center.y - geometry.tileSize * 0.4f)

        assertEquals(expected, controller.wallAt(tapBesideTheThinWall, geometry, WallOrientation.VERTICAL))
    }

    @Test
    fun wallTapOutsideBoardIsIgnored() {
        assertNull(controller.wallAt(Offset(-1f, 200f), geometry, WallOrientation.HORIZONTAL))
    }
}
