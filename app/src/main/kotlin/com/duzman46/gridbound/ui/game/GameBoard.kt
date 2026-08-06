package com.duzman46.gridbound.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.game.board.BoardGeometry
import com.duzman46.gridbound.game.board.BoardOrientation
import com.duzman46.gridbound.game.board.BoardTheme
import com.duzman46.gridbound.game.board.CanvasRenderer
import com.duzman46.gridbound.game.board.TouchController
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.presentation.game.GameUiState

@Composable
fun GameBoard(
    state: GameUiState,
    onTileTap: (com.duzman46.gridbound.game.models.Position) -> Unit,
    onWallTap: (Wall) -> Unit,
    modifier: Modifier = Modifier,
    theme: BoardTheme = BoardTheme.CLASSIC,
) {
    val renderer = remember { CanvasRenderer() }
    val touchController = remember { TouchController() }
    val flipped = state.mode == GameMode.ONLINE && state.localPlayer == PlayerId.PLAYER_TWO
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
    val palette = remember(colors, theme) { theme.palette(colors) }
    val boardDescription = stringResource(R.string.cd_board)

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = boardDescription }
            .pointerInput(state.wallMode, state.wallOrientation, state.acceptsHumanInput) {
                detectTapGestures { offset ->
                    if (!state.acceptsHumanInput) return@detectTapGestures
                    val geometry = BoardGeometry(minOf(size.width, size.height).toFloat())
                    val modelOffset = BoardOrientation.toModelOffset(offset, size.width.toFloat(), size.height.toFloat(), flipped)
                    if (state.wallMode) {
                        touchController.wallAt(modelOffset, geometry, state.wallOrientation)?.let(onWallTap)
                    } else {
                        touchController.tileAt(modelOffset, geometry)?.let(onTileTap)
                    }
                }
            },
    ) {
        val geometry = BoardGeometry(size.minDimension)
        val renderBoard: DrawScope.() -> Unit = {
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
        if (flipped) rotate(180f, center, renderBoard) else renderBoard()
    }
}
