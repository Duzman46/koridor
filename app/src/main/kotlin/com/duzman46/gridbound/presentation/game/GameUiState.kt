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
import com.duzman46.gridbound.online.model.MatchChatEntry
import com.duzman46.gridbound.online.model.MatchMessageKind
import com.duzman46.gridbound.online.model.RoomEndReason
import com.duzman46.gridbound.domain.models.DEFAULT_SOUND_VOLUME

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
    /**
     * The account this device is playing as, which is the only way to tell whose message is
     * whose. Blank outside an online match, where nobody is addressing anybody.
     */
    val localUserId: String = "",
    /** What each seat last said; see [MatchChatEntry]. At most one entry per player. */
    val chat: List<MatchChatEntry> = emptyList(),
    /** The player's own setting, which switches both halves off: nothing sent, nothing shown. */
    val matchMessagesEnabled: Boolean = true,
    /**
     * True while this player has silenced the rival for the rest of this match.
     *
     * Kept apart from [matchMessagesEnabled], and the two are different questions. The
     * setting is a standing answer to "do I want canned messages at all", given once and
     * kept; this is an answer to "not from this opponent, not now", given on the board with
     * a rival saying the same thing for the eleventh time. Reaching two screens into
     * settings to undo something done on the board is not an undo, so this dies with the
     * match and the control that set it is the control that clears it.
     */
    val matchMessagesMuted: Boolean = false,
    val soundVolume: Int = DEFAULT_SOUND_VOLUME,
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

    /**
     * True when this screen carries canned messages at all.
     *
     * Online only, and only while the player still wants them. A bot has nothing to say, and
     * the second player on a shared handset is close enough to say it out loud.
     */
    val showsMatchMessages: Boolean get() = isOnline && matchMessagesEnabled

    /**
     * True while what the rival says is shown.
     *
     * This is the whole of what a mute takes away. The row itself stays — it is where the
     * mute was reached from and it is the only way back — and so does the ability to speak,
     * which nobody asked to be relieved of.
     */
    val showsRivalMessages: Boolean get() = showsMatchMessages && !matchMessagesMuted

    /** True while a message would actually reach a rival who is still playing. */
    val canSendMessage: Boolean
        get() = showsMatchMessages && isOnlineConnected && winner == null &&
            boardState.status == GameStatus.IN_PROGRESS

    /**
     * True while the sheet the messages are chosen from may be opened at all.
     *
     * Wider than [canSendMessage], and the mute is the whole of the difference. Every condition
     * in [canSendMessage] is about a message reaching somebody; a mute is about this player not
     * having to read one, and the sheet holds the only control that lifts it. Gated on being
     * able to speak, a player who mutes and then hits a connection blip — or simply reaches the
     * end of the match — is left with the mute visibly on and nothing to press: a state that can
     * be set and not cleared. So the way in follows whichever of the two is true, and what is
     * offered once inside is still [canSendMessage]'s to decide.
     */
    val canOpenMessages: Boolean
        get() = showsMatchMessages && (canSendMessage || matchMessagesMuted)

    /** The last thing the rival said, whether or not it is still worth showing. */
    val rivalMessage: MatchChatEntry? get() = chat.firstOrNull { it.userId != localUserId }

    /** This player's own last message, echoed back so that sending one visibly did something. */
    val ownMessage: MatchChatEntry? get() = chat.firstOrNull { it.userId == localUserId }

    /** The rival's half of the message row; empty while muted, and while they have said nothing. */
    val rivalBubble: MatchChatBubble?
        get() = rivalMessage?.takeIf { showsRivalMessages }?.let(::MatchChatBubble)

    /** This player's half. A mute silences a rival, never the player who reached for it. */
    val ownBubble: MatchChatBubble? get() = ownMessage?.let(::MatchChatBubble)

    val acceptsHumanInput: Boolean
        get() = !isAiThinking && !isOnlineSyncing && when (mode) {
            GameMode.LOCAL_TWO_PLAYER -> true
            GameMode.VS_AI -> boardState.currentPlayer == localPlayer
            GameMode.ONLINE -> isOnlineConnected && boardState.currentPlayer == localPlayer
        }
}

/**
 * One side of the match message row.
 *
 * [showsWords] is worked out from the message rather than handed to each side, and that is the
 * point of the type. It was handed to each side, false for the sender's own bubble on the
 * theory that you already know what you just said — and eight of the fourteen messages are
 * phrases, so a player who picked "Good luck" watched a bare glyph while their opponent read
 * the words. What a message says is a property of the message, not of who is looking at it.
 */
data class MatchChatBubble(val entry: MatchChatEntry) {
    val showsWords: Boolean get() = entry.message.kind == MatchMessageKind.PHRASE
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
