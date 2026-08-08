package com.duzman46.gridbound.online.model

import androidx.annotation.StringRes
import com.duzman46.gridbound.R

/** Whether a message is carried by its words or by its glyph alone. */
enum class MatchMessageKind {
    /** Words, drawn with the glyph beside them. */
    PHRASE,

    /** The glyph on its own; the label exists so a screen reader has something to read. */
    REACTION,
}

/**
 * Every single thing one player is able to say to the other during an online match.
 *
 * The vocabulary is closed and has to stay closed. Free text would make the app a host of
 * user-generated content, which under Google Play's policy drags in moderation, blocking and
 * reporting duties — an entire product built to service one text field. A fixed list carries
 * none of that. It is also the only shape in which nobody can be got at: there is no way to
 * arrange these values into an insult, so the worst anybody can do is repeat one, and the rate
 * limit in the database rules, the mute on the board and the setting behind it answer that
 * between them.
 *
 * So: no free text, ever. Not a field "for friends only", not a list assembled at runtime, not
 * a username interpolated into a phrase. Anything one player types and another player reads is
 * the line, and this feature is on the safe side of it.
 *
 * [name] is the key stored in the database. database.rules.json holds the same names and
 * refuses every other value, so the two lists are one contract: change them together, and
 * never rename an entry — a match being played right now may already hold the old key.
 */
enum class MatchMessage(
    val kind: MatchMessageKind,
    val glyph: String,
    @param:StringRes val labelRes: Int,
) {
    GOOD_LUCK(MatchMessageKind.PHRASE, "🍀", R.string.chat_good_luck),
    NICE_MOVE(MatchMessageKind.PHRASE, "👏", R.string.chat_nice_move),
    WELL_PLAYED(MatchMessageKind.PHRASE, "👍", R.string.chat_well_played),
    GOOD_GAME(MatchMessageKind.PHRASE, "🤝", R.string.chat_good_game),
    THANKS(MatchMessageKind.PHRASE, "🙏", R.string.chat_thanks),
    OOPS(MatchMessageKind.PHRASE, "😅", R.string.chat_oops),
    SORRY(MatchMessageKind.PHRASE, "🙇", R.string.chat_sorry),
    GOOD_LUCK_NEXT_TIME(MatchMessageKind.PHRASE, "🌟", R.string.chat_good_luck_next_time),

    SMILE(MatchMessageKind.REACTION, "🙂", R.string.chat_reaction_smile),
    WOW(MatchMessageKind.REACTION, "😮", R.string.chat_reaction_wow),
    THINKING(MatchMessageKind.REACTION, "🤔", R.string.chat_reaction_thinking),
    BRILLIANT(MatchMessageKind.REACTION, "🔥", R.string.chat_reaction_brilliant),
    STRONG(MatchMessageKind.REACTION, "💪", R.string.chat_reaction_strong),
    SAD(MatchMessageKind.REACTION, "😢", R.string.chat_reaction_sad),
    ;

    companion object {
        /**
         * Null for a key this build has never heard of, which is how a phone reads a message
         * sent by a newer one: nothing appears, rather than an empty bubble.
         */
        fun forKey(key: String?): MatchMessage? = entries.firstOrNull { it.name == key }
    }
}

/**
 * The last thing one player said.
 *
 * A room keeps one of these per player and no history. The message is shown above the board for
 * a few seconds and then it is gone, so there is nothing a transcript would be of — and it makes
 * the cap structural rather than something to prune: two players, two slots, and the database
 * rules let a player write only under their own id.
 *
 * [sentAt] is stamped by the server, so it can be compared against a phone's own clock only as
 * loosely as the move clock is — which is all the fade needs.
 */
data class MatchChatEntry(
    val userId: String,
    val message: MatchMessage,
    val sentAt: Long,
)
