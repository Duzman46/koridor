package com.duzman46.gridbound.presentation.game

import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.game.audio.SoundEffect
import com.duzman46.gridbound.game.models.BoardState
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.GameMode
import com.duzman46.gridbound.game.models.GameStatus
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.game.models.Position
import com.duzman46.gridbound.game.models.Wall
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.online.model.RoomEndReason

data class GameUiState(
    val boardState: BoardState = BoardState.initial(),
    val mode: GameMode = GameMode.VS_AI,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /**
     * The seat this device plays. Blue is seat one and opens, so a player who picked red is
     * [PlayerId.PLAYER_TWO] and waits — against a bot as much as against a person. Meaningless
     * on a shared handset, where both seats are played from the same screen.
     */
    val localPlayer: PlayerId = PlayerId.PLAYER_ONE,
    val pawnSelected: Boolean = false,
    val validMoves: Set<Position> = emptySet(),
    val wallMode: Boolean = false,
    val wallOrientation: WallOrientation = WallOrientation.HORIZONTAL,
    val validWalls: Set<Wall> = emptySet(),
    val pendingWall: Wall? = null,
    val invalidWallPreview: Wall? = null,
    val recentlyPlacedWall: Wall? = null,
    val isAiThinking: Boolean = false,
    val isOnlineConnected: Boolean = false,
    val isOnlineSyncing: Boolean = false,
    val onlineMessage: UiText? = null,
    /**
     * The seat an online match was awarded to when the board itself did not decide it — a
     * resignation, a walk-out, a move clock running down. Those leave the position untouched,
     * so [boardState] cannot carry the result and the room does instead.
     */
    val onlineWinner: PlayerId? = null,
    /** How the match ended, so the loser is told what happened rather than only that it did. */
    val onlineEndReason: RoomEndReason? = null,
    /** Wall-clock instant the current player's move clock runs out; null when untimed. */
    val turnDeadlineAt: Long? = null,
    /**
     * The account holding the other seat, read straight off the room.
     *
     * Deliberately not folded into [onlineOpponent]: that one waits on a profile read because
     * it exists to show a rival, while this exists to address one. Offering a rematch must not
     * depend on a name having loaded.
     */
    val opponentUserId: String = "",
    /**
     * Who is sitting on the other side, once their profile has been read. Only ever set in an
     * online match: a bot has nothing to open, and the second player on a shared handset is
     * within arm's reach. Null until the read lands, so the screen never offers a tap that
     * would open a profile it cannot show.
     */
    val onlineOpponent: OnlineOpponent? = null,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val canUndo: Boolean = false,
) {
    val isOnline: Boolean get() = mode == GameMode.ONLINE

    /** Who won, whichever of the two ways the match found to end. */
    val winner: PlayerId? get() = boardState.status.winner ?: onlineWinner

    /**
     * True while walking out would cost the match. Only an online game already under way: a
     * room still waiting for an opponent is nobody's match to lose.
     */
    val leavingForfeits: Boolean
        get() = isOnline && isOnlineConnected && winner == null &&
            boardState.status == GameStatus.IN_PROGRESS

    val acceptsHumanInput: Boolean
        get() = !isAiThinking && !isOnlineSyncing && when (mode) {
            GameMode.LOCAL_TWO_PLAYER -> true
            GameMode.VS_AI -> boardState.currentPlayer == localPlayer
            GameMode.ONLINE -> isOnlineConnected && boardState.currentPlayer == localPlayer
        }
}

/**
 * The rival in an online match, as much of them as the board screen needs: enough to name and
 * picture them in the turn banner, plus the id that opens their profile.
 */
data class OnlineOpponent(
    val userId: String,
    val username: String,
    val avatarId: String,
)

sealed interface GameEvent {
    data class Feedback(val effect: SoundEffect, val hapticsEnabled: Boolean) : GameEvent
}
