package com.duzman46.gridbound.achievements.domain

import androidx.annotation.StringRes
import com.duzman46.gridbound.R
import com.duzman46.gridbound.domain.models.GameStatistics
import com.duzman46.gridbound.game.models.Difficulty

/**
 * The badges a player can earn, and the arithmetic that says whether they have.
 *
 * Every one of them is a **question asked of the counters the app already keeps**, not a record
 * of its own. Nothing here is written when a badge is earned: a badge is earned exactly when the
 * statistics say so, and it is worked out again every time the screen is drawn. That is the whole
 * design, and it buys three things — a badge cannot be lost to a write that failed, a badge cannot
 * disagree with the statistics screen sitting next to it, and adding a badge later awards it
 * retroactively to the player who already did the thing.
 *
 * It costs one thing, and it is worth naming: an achievement can only exist if a counter can
 * answer it. "Win without ever being overtaken" is a lovely badge and there is no number in this
 * app that knows it, so it is not here. Two counters were added for the ones that were worth it —
 * the online tallies and the streak — and nothing was invented beyond that.
 *
 * The ladders are deliberate. Three tiers of the same thing, wearing the same mark in three
 * metals, say "keep going" in a way that eighteen unrelated one-offs cannot.
 */
enum class AchievementGroup(@StringRes val titleRes: Int) {
    FIRST_STEPS(R.string.achievement_group_first_steps),
    THE_MACHINE(R.string.achievement_group_machine),
    THE_NETWORK(R.string.achievement_group_network),
    CAREER(R.string.achievement_group_career),
    MASTERY(R.string.achievement_group_mastery),
}

/** Bronze, silver, gold — the tier of a badge, and the metal it is drawn in. */
enum class AchievementTier { BRONZE, SILVER, GOLD }

enum class Achievement(
    val group: AchievementGroup,
    val tier: AchievementTier,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    /** What the progress bar counts up to. Reaching it is what unlocks the badge. */
    val target: Int,
    /**
     * The number the description quotes, when it is not the target.
     *
     * Only [SWIFT] needs the distinction: its target is one match, and the number worth saying
     * out loud is how few turns that match may take.
     */
    val quoted: Int = target,
    /** The bot level a badge is about, for the three that are about one. */
    val level: Difficulty? = null,
) {
    // ---- First steps -------------------------------------------------------------------------
    FIRST_STEP(
        group = AchievementGroup.FIRST_STEPS,
        tier = AchievementTier.BRONZE,
        titleRes = R.string.achievement_first_step,
        descriptionRes = R.string.achievement_desc_play,
        target = 1,
    ),
    FIRST_WIN(
        group = AchievementGroup.FIRST_STEPS,
        tier = AchievementTier.BRONZE,
        titleRes = R.string.achievement_first_win,
        descriptionRes = R.string.achievement_desc_win,
        target = 1,
    ),
    SCHOLAR(
        group = AchievementGroup.FIRST_STEPS,
        tier = AchievementTier.BRONZE,
        titleRes = R.string.achievement_scholar,
        descriptionRes = R.string.achievement_desc_tutorial,
        target = 1,
    ),
    FACE_TO_FACE(
        group = AchievementGroup.FIRST_STEPS,
        tier = AchievementTier.BRONZE,
        titleRes = R.string.achievement_face_to_face,
        descriptionRes = R.string.achievement_desc_local,
        target = 1,
    ),

    // ---- Against the machine -----------------------------------------------------------------
    BOT_MEDIUM(
        group = AchievementGroup.THE_MACHINE,
        tier = AchievementTier.BRONZE,
        titleRes = R.string.achievement_bot_medium,
        descriptionRes = R.string.achievement_desc_bot,
        target = 1,
        level = Difficulty.MEDIUM,
    ),
    BOT_HARD(
        group = AchievementGroup.THE_MACHINE,
        tier = AchievementTier.SILVER,
        titleRes = R.string.achievement_bot_hard,
        descriptionRes = R.string.achievement_desc_bot,
        target = 1,
        level = Difficulty.HARD,
    ),
    BOT_EXPERT(
        group = AchievementGroup.THE_MACHINE,
        tier = AchievementTier.GOLD,
        titleRes = R.string.achievement_bot_expert,
        descriptionRes = R.string.achievement_desc_bot,
        target = 1,
        level = Difficulty.EXPERT,
    ),

    // ---- Against people ----------------------------------------------------------------------
    ONLINE_DEBUT(
        group = AchievementGroup.THE_NETWORK,
        tier = AchievementTier.BRONZE,
        titleRes = R.string.achievement_online_debut,
        descriptionRes = R.string.achievement_desc_online_play,
        target = 1,
    ),
    ONLINE_WIN(
        group = AchievementGroup.THE_NETWORK,
        tier = AchievementTier.SILVER,
        titleRes = R.string.achievement_online_win,
        descriptionRes = R.string.achievement_desc_online_win,
        target = 1,
    ),
    ONLINE_TEN(
        group = AchievementGroup.THE_NETWORK,
        tier = AchievementTier.GOLD,
        titleRes = R.string.achievement_online_ten,
        descriptionRes = R.string.achievement_desc_online_win,
        target = 10,
    ),

    // ---- A career ----------------------------------------------------------------------------
    REGULAR(
        group = AchievementGroup.CAREER,
        tier = AchievementTier.SILVER,
        titleRes = R.string.achievement_regular,
        descriptionRes = R.string.achievement_desc_play,
        target = 10,
    ),
    VETERAN(
        group = AchievementGroup.CAREER,
        tier = AchievementTier.GOLD,
        titleRes = R.string.achievement_veteran,
        descriptionRes = R.string.achievement_desc_play,
        target = 50,
    ),
    TEN_WINS(
        group = AchievementGroup.CAREER,
        tier = AchievementTier.SILVER,
        titleRes = R.string.achievement_ten_wins,
        descriptionRes = R.string.achievement_desc_win,
        target = 10,
    ),
    FIFTY_WINS(
        group = AchievementGroup.CAREER,
        tier = AchievementTier.GOLD,
        titleRes = R.string.achievement_fifty_wins,
        descriptionRes = R.string.achievement_desc_win,
        target = 50,
    ),
    MARATHON(
        group = AchievementGroup.CAREER,
        tier = AchievementTier.GOLD,
        titleRes = R.string.achievement_marathon,
        descriptionRes = R.string.achievement_desc_turns,
        target = 1_000,
    ),

    // ---- Mastery ------------------------------------------------------------------------------
    STREAK_THREE(
        group = AchievementGroup.MASTERY,
        tier = AchievementTier.SILVER,
        titleRes = R.string.achievement_streak_three,
        descriptionRes = R.string.achievement_desc_streak,
        target = 3,
    ),
    STREAK_FIVE(
        group = AchievementGroup.MASTERY,
        tier = AchievementTier.GOLD,
        titleRes = R.string.achievement_streak_five,
        descriptionRes = R.string.achievement_desc_streak,
        target = 5,
    ),
    SWIFT(
        group = AchievementGroup.MASTERY,
        tier = AchievementTier.GOLD,
        titleRes = R.string.achievement_swift,
        descriptionRes = R.string.achievement_desc_swift,
        target = 1,
        quoted = SWIFT_WIN_TURNS,
    ),
    ;
}

/**
 * How few turns a match has to take to count as a swift one.
 *
 * A turn here is one player's move, so both sides are moving inside this number. The shortest
 * legal game on a nine-by-nine board is eight moves each — sixteen turns with nobody laying a
 * single wall — so twenty is a genuinely direct game that still had room for a wall or two, and
 * not a figure anybody reaches by accident.
 */
const val SWIFT_WIN_TURNS = 20

/** One badge and how far along it the player is. */
data class AchievementState(
    val achievement: Achievement,
    val progress: Int,
) {
    val target: Int get() = achievement.target

    val unlocked: Boolean get() = progress >= target

    /** 0f to 1f, for the bar. Clamped, because a counter may run past a target it has passed. */
    val fraction: Float
        get() = if (target <= 0) 1f else (progress.toFloat() / target).coerceIn(0f, 1f)

    /** What the row prints: never more than the target, so "12/10" cannot happen. */
    val shown: Int get() = progress.coerceAtMost(target)
}

/**
 * Every badge, in catalogue order, answered against the numbers this player has actually put up.
 *
 * [tutorialCompleted] is passed rather than read here because it lives outside [GameStatistics] —
 * it is device-local progress through the lesson, not a match result — and this function stays a
 * pure one so it can be tested without a data store.
 */
fun achievementStates(
    statistics: GameStatistics,
    tutorialCompleted: Boolean,
): List<AchievementState> = Achievement.entries.map { achievement ->
    AchievementState(achievement, progressOf(achievement, statistics, tutorialCompleted))
}

private fun progressOf(
    achievement: Achievement,
    stats: GameStatistics,
    tutorialCompleted: Boolean,
): Int = when (achievement) {
    Achievement.FIRST_STEP, Achievement.REGULAR, Achievement.VETERAN -> stats.totalGames
    Achievement.FIRST_WIN, Achievement.TEN_WINS, Achievement.FIFTY_WINS -> stats.totalWins
    Achievement.SCHOLAR -> if (tutorialCompleted) 1 else 0
    Achievement.FACE_TO_FACE -> stats.localGames
    Achievement.BOT_MEDIUM, Achievement.BOT_HARD, Achievement.BOT_EXPERT ->
        achievement.level?.let { stats.winsByDifficulty[it] } ?: 0

    Achievement.ONLINE_DEBUT -> stats.onlineGames
    Achievement.ONLINE_WIN, Achievement.ONLINE_TEN -> stats.onlineWins
    Achievement.STREAK_THREE, Achievement.STREAK_FIVE -> stats.bestStreak
    // Zero is "has never won", not "won in no turns at all", so it can never satisfy this.
    Achievement.SWIFT -> if (stats.fastestWinTurns in 1..SWIFT_WIN_TURNS) 1 else 0
    Achievement.MARATHON -> stats.totalTurns
}
