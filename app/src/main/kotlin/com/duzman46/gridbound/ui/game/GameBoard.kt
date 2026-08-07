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
import com.duzman46.gridbound.game.board.boardPalette
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
) {
    val renderer = remember { CanvasRenderer() }
    val touchController = remember { TouchController() }
    // Whose way up the board is drawn. Wherever one person holds one seat — online or against
    // the bot — that is fixed to the seat they occupy, so their own goal is always the far
    // edge. A player who chose red sits opposite and reads the board the other way round.
    //
    // On a shared device it is fixed too, and deliberately: the two players sit on opposite
    // sides of the handset the way they would sit across a real board, so blue reads it from
    // the near edge and red from the far one. Turning the board on every hand-over was the
    // pass-the-phone gesture, which is not how anyone plays this game in the same room —
    // their own controls are mirrored to their own side instead.
    val flipped = when (state.mode) {
        GameMode.ONLINE, GameMode.VS_AI -> state.localPlayer == PlayerId.PLAYER_TWO
        GameMode.LOCAL_TWO_PLAYER -> false
    }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(Constants.Animation.PAWN_DURATION_MILLIS),
        label = "boardRotation",
    )
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
    val palette = remember(colors) { boardPalette(colors) }
    val boardDescription = stringResource(R.string.cd_board)

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = boardDescription }
            // `flipped` belongs in the key: without it the gesture lambda kept the
            // orientation from the turn it was created on, so on a shared device every tap
            // after the first hand-over landed on the mirrored square.
            .pointerInput(state.wallMode, state.wallOrientation, state.acceptsHumanInput, flipped) {
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
                // Turning the tray must not turn the pieces standing on it. Without this the
                // pawns were drawn head-down for whoever sat in the second seat, which is
                // what made the second pawn look wrong while blue looked fine.
                pieceRotation = -rotation,
            )
        }
        rotate(rotation, center, renderBoard)
    }
}
