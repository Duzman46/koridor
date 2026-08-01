package com.duzman46.gridbound.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.board.BoardGeometry
import com.duzman46.gridbound.game.board.BoardPalette
import com.duzman46.gridbound.game.board.CanvasRenderer
import com.duzman46.gridbound.game.board.TouchController
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.presentation.game.GameUiState

@Composable
fun GameBoard(
    state: GameUiState,
    onTileTap: (com.duzman46.gridbound.game.models.Position) -> Unit,
    onWallTap: (Wall) -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderer = remember { CanvasRenderer() }
    val touchController = remember { TouchController() }
    val playerOne = state.boardState.player(com.duzman46.gridbound.game.models.PlayerId.PLAYER_ONE)
    val playerTwo = state.boardState.player(com.duzman46.gridbound.game.models.PlayerId.PLAYER_TWO)
    val p1Row by animateFloatAsState(playerOne.position.row.toFloat(), tween(Constants.Animation.PAWN_DURATION_MILLIS), label = "p1Row")
    val p1Column by animateFloatAsState(playerOne.position.column.toFloat(), tween(Constants.Animation.PAWN_DURATION_MILLIS), label = "p1Column")
    val p2Row by animateFloatAsState(playerTwo.position.row.toFloat(), tween(Constants.Animation.PAWN_DURATION_MILLIS), label = "p2Row")
    val p2Column by animateFloatAsState(playerTwo.position.column.toFloat(), tween(Constants.Animation.PAWN_DURATION_MILLIS), label = "p2Column")
    val wallProgress = remember { Animatable(1f) }
    LaunchedEffect(state.recentlyPlacedWall) {
        if (state.recentlyPlacedWall != null) {
            wallProgress.snapTo(0f)
            wallProgress.animateTo(1f, tween(Constants.Animation.WALL_DURATION_MILLIS))
        }
    }
    val colors = MaterialTheme.colorScheme
    val palette = remember(colors) {
        BoardPalette(
            background = colors.surfaceVariant.copy(alpha = 0.58f),
            tile = colors.surface,
            tileAlternate = colors.surfaceVariant.copy(alpha = 0.72f),
            goalOne = Color(0xFF3F82FF),
            goalTwo = Color(0xFFFF9D3F),
            valid = Color(0xFF32D583),
            invalid = colors.error,
            wall = colors.secondary,
            playerOne = Color(0xFF3F82FF),
            playerTwo = Color(0xFFFF8A34),
            selection = colors.primary,
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = "Dokuz çarpı dokuz oyun tahtası" }
            .pointerInput(state.wallMode, state.wallOrientation, state.acceptsHumanInput) {
                detectTapGestures { offset ->
                    if (!state.acceptsHumanInput) return@detectTapGestures
                    val geometry = BoardGeometry(minOf(size.width, size.height).toFloat())
                    if (state.wallMode) {
                        touchController.wallAt(offset, geometry, state.wallOrientation)?.let(onWallTap)
                    } else {
                        touchController.tileAt(offset, geometry)?.let(onTileTap)
                    }
                }
            },
    ) {
        val geometry = BoardGeometry(size.minDimension)
        renderer.draw(
            scope = this,
            geometry = geometry,
            state = state.boardState,
            palette = palette,
            validMoves = state.validMoves,
            validWalls = state.validWalls,
            wallOrientation = state.wallOrientation,
            pendingWall = state.pendingWall,
            invalidWall = state.invalidWallPreview,
            recentWall = state.recentlyPlacedWall,
            recentWallProgress = wallProgress.value,
            playerOneRow = p1Row,
            playerOneColumn = p1Column,
            playerTwoRow = p2Row,
            playerTwoColumn = p2Column,
            selected = state.pawnSelected,
        )
    }
}
