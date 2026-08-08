package com.duzman46.gridbound.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Outcome
import com.duzman46.gridbound.core.UiText
import com.duzman46.gridbound.di.ApplicationScope
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.online.model.OnlineLobbyResult
import com.duzman46.gridbound.online.model.OnlineSession
import com.duzman46.gridbound.session.SessionManager
import com.duzman46.gridbound.social.domain.RequestKind
import com.duzman46.gridbound.social.domain.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RematchStage {
    /** Nobody has been asked yet. */
    IDLE,

    /** The room is open, the question is out, and the answer is somebody else's to give. */
    WAITING,

    /** They said no. */
    DECLINED,
}

data class RematchUiState(
    val stage: RematchStage = RematchStage.IDLE,
    val isBusy: Boolean = false,
    /**
     * False for a guest. A rematch travels on the request channel, which needs a real account
     * at both ends, so there is no point offering one that could not be delivered.
     */
    val canAsk: Boolean = false,
    val message: UiText? = null,
)

/**
 * Asks the opponent of the match that has just ended for another one.
 *
 * The room is opened first and the question sent second, so accepting is a single tap into a
 * room already standing rather than a handshake that has to build one. Nothing about this
 * blocks the winner screen: the player can walk away to another game or to the menu at any
 * point, and the room they left open is torn down behind them.
 */
@HiltViewModel
class RematchViewModel @Inject constructor(
    private val onlineRepository: OnlineGameRepository,
    private val socialRepository: SocialRepository,
    private val sessionManager: SessionManager,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RematchUiState(canAsk = sessionManager.state.value.canUseSocialFeatures),
    )
    val uiState: StateFlow<RematchUiState> = _uiState.asStateFlow()

    private val _accepted = MutableSharedFlow<OnlineSession>(extraBufferCapacity = 1)

    /** Fires when the opponent walks into the room: the next match is on. */
    val accepted: SharedFlow<OnlineSession> = _accepted.asSharedFlow()

    private var pending: Pending? = null
    private var watchJob: Job? = null

    /**
     * @param playedRoomCode the match just finished. It is what licenses a request between two
     *   players who need not be friends, and the server checks it rather than taking our word.
     * @param seat the seat held in that match, which is the one given up here: blue opens, and
     *   a rematch that returned the first move to whoever asked for it would be a rematch on
     *   better terms than the match itself.
     */
    fun ask(playedRoomCode: String, opponentUserId: String, seat: PlayerId) {
        val account = sessionManager.state.value
        val ownId = account.user?.userId ?: return
        val state = _uiState.value
        if (state.stage != RematchStage.IDLE || state.isBusy || !state.canAsk) return
        if (playedRoomCode.isBlank() || opponentUserId.isBlank()) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            // One room per finished match, not one per tap: both players see this button and
            // both may press it at once, and a code apiece would put the two of them on
            // separate boards waiting for each other. Whichever device gets there first opens
            // it; the other walks straight into the seat it left.
            val created = onlineRepository.rematchRoom(playedRoomCode, opponentUserId, seat)
            if (created is OnlineLobbyResult.Failure) {
                _uiState.update { it.copy(isBusy = false, message = created.error.message) }
                return@launch
            }
            val session = (created as OnlineLobbyResult.Success).session
            val sent = socialRepository.sendRematch(
                fromUserId = ownId,
                fromUsername = account.profile?.username.orEmpty(),
                toUserId = opponentUserId,
                roomCode = session.roomCode,
                playedRoomCode = playedRoomCode,
            )
            if (sent is Outcome.Failure) {
                // A room nobody will ever be told about is worse than no room: it would sit
                // in the player's name until it expired.
                onlineRepository.leaveRoom(session)
                _uiState.update { it.copy(isBusy = false, message = sent.error.message) }
                return@launch
            }
            pending = Pending(session, opponentUserId)
            _uiState.update { it.copy(stage = RematchStage.WAITING, isBusy = false) }
            watch(ownId, session)
        }
    }

    /**
     * Two answers, from two directions. Acceptance shows up as the opponent appearing in the
     * room — there is no separate yes to send, because joining is the yes. A refusal comes back
     * along the request channel, which is the only thing the asker is allowed to read.
     */
    private fun watch(ownId: String, session: OnlineSession) {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            launch {
                onlineRepository.observeRoom(session.roomCode)
                    .catch { error -> AppLog.warn("observe-rematch-room", error) }
                    .collect { room ->
                        // Guarded on `pending` as well as cancelled below, because the room
                        // republishes itself on every move: an answer announced twice would
                        // be a second navigation queued behind the interstitial.
                        if (room?.status?.isPlayable != true || pending == null) return@collect
                        // Handed over: the room is in play and is no longer ours to close.
                        pending = null
                        _accepted.tryEmit(session)
                        stopWatching()
                    }
            }
            launch {
                socialRepository.observeRequests(ownId)
                    .mapNotNull { requests ->
                        requests.firstOrNull {
                            it.kind == RequestKind.REMATCH_DECLINED &&
                                it.roomCode == session.roomCode
                        }
                    }
                    .collect { refusal ->
                        pending = null
                        onlineRepository.leaveRoom(session)
                        // Consumed, so a refusal cannot be read a second time and answer a
                        // request that has not been made yet.
                        socialRepository.clearRequest(ownId, refusal.fromUserId)
                        _uiState.update { it.copy(stage = RematchStage.DECLINED) }
                        stopWatching()
                    }
            }
        }
    }

    /**
     * Ends both watches at once. Called from inside one of them, which is why nothing may
     * follow it: cancelling the parent cancels the coroutine doing the cancelling.
     */
    private fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    /**
     * Withdraws an unanswered request when the player leaves this screen.
     *
     * Every exit passes through here — another game, the main menu, the system back gesture —
     * so a room opened for an opponent who is no longer being waited on never outlives the
     * screen that opened it, and the bar on their phone stops offering a room that is gone.
     *
     * The work runs on the application scope because the navigation that clears this view
     * model is the same navigation that cancels its scope.
     */
    override fun onCleared() {
        val abandoned = pending ?: return
        pending = null
        val ownId = sessionManager.state.value.user?.userId ?: return
        applicationScope.launch {
            onlineRepository.leaveRoom(abandoned.session)
            socialRepository.clearRequest(abandoned.opponentUserId, ownId)
        }
    }

    /** A request that has been sent and not yet answered, and so is still ours to take back. */
    private data class Pending(val session: OnlineSession, val opponentUserId: String)
}
