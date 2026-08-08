package com.duzman46.gridbound.navigation

/**
 * Whether a back press that a player just made may take them off the screen they are on.
 *
 * A back arrow keeps drawing — and keeps taking touches, on top of the screen replacing it —
 * for the whole of its screen's exit transition; the same window `navigateFrom` guards on the
 * way in. The second of two fast taps therefore reaches a control whose own screen has already
 * been popped, and pops the screen underneath as well. The Friends screen is where that was
 * found, because its back arrow sits directly under the home button that opens it, but nothing
 * about it is Friends-specific: every back arrow in the graph had the same unguarded pop.
 *
 * Both questions have to be asked, and the first is the one that matters.
 *
 * [isResumed] is the fix proper. A screen that is on its way out is not the screen the player
 * believes they are pressing, so its controls stop meaning anything the moment it leaves. Ask
 * only the second question and the double tap still works — it simply stops one screen short
 * of emptying the graph, so Home to Profile to Friends lands the player on Home having never
 * seen Profile go by.
 *
 * [screenBeneath] is the backstop, and it is worth keeping for the case the first cannot cover:
 * a pop that arrives from somewhere other than a tap, where there is no live screen to ask
 * about. An emptied graph is not an error anything reports — the host composes nothing, the
 * window background is what is left visible (black, in the night palette), and the player
 * cannot press their way out, because NavController disables its own back callback once the
 * stack is empty.
 *
 * @param isResumed whether the screen whose control was pressed is still the resumed one.
 * @param screenBeneath the route the pop would return to, and null when there is none.
 *   NavController answers it with `previousBackStackEntry`, which skips the current entry and
 *   ignores the graph — so null is exactly the case where this screen is the only one there is.
 */
fun canLeaveScreen(isResumed: Boolean, screenBeneath: String?): Boolean =
    isResumed && screenBeneath != null

/**
 * The same question for a pop nobody pressed — one that arrives from a repository event or from
 * a save that has just landed.
 *
 * These legitimately run while their screen is no longer resumed, which is why they cannot be
 * held to [canLeaveScreen]: dropping one would strand the player on a screen that has finished
 * its work. Only the backstop applies.
 */
fun canLeaveScreenUnprompted(screenBeneath: String?): Boolean = screenBeneath != null
