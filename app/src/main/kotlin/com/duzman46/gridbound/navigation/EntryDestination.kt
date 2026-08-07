package com.duzman46.gridbound.navigation

import com.duzman46.gridbound.session.SessionState

/** The four places a player can be sent when the app has to decide where they belong. */
enum class EntryDestination {
    WELCOME,
    TUTORIAL,
    USERNAME,
    HOME,
}

/**
 * A step the player has just finished, which the session flow has not necessarily seen yet.
 *
 * Every one of these is recorded through a preference or a database write, and the session
 * state is rebuilt from those asynchronously. The screen the player just left, though, knows
 * for certain that it is done — and without saying so it would be sent straight back to
 * itself, which is a loop rather than a stale value.
 */
enum class EntryStep {
    NONE,

    /** An identity now exists: signed in, or accepted as a guest. */
    ENTRY,
    TUTORIAL,
    USERNAME,
}

/**
 * The one decision behind every hand-off between the entry screens.
 *
 * The order is the argument. The tutorial comes first because being asked to name yourself
 * before you have seen the game is being asked to commit to something you have no feel for
 * yet. The username comes second because it is what everyone else will call you, and there
 * is no point in the game at which a real account may be reached without one — skipping the
 * tutorial skips the tutorial and nothing else.
 */
fun entryDestinationFor(
    session: SessionState,
    justCompleted: EntryStep = EntryStep.NONE,
): EntryDestination {
    // Any of these steps is something only a player who is already in can have finished.
    val entered = session.hasEntered || justCompleted != EntryStep.NONE
    val tutorialDone = session.tutorialCompleted || justCompleted == EntryStep.TUTORIAL
    val named = !session.needsUsername || justCompleted == EntryStep.USERNAME
    return when {
        !entered -> EntryDestination.WELCOME
        !tutorialDone -> EntryDestination.TUTORIAL
        !named -> EntryDestination.USERNAME
        else -> EntryDestination.HOME
    }
}
