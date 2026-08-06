package com.duzman46.gridbound.rating

import com.duzman46.gridbound.core.Constants
import kotlin.math.pow
import kotlin.math.roundToInt

/** The score a player earned in a match, in Elo terms. */
enum class MatchScore(val value: Double) {
    LOSS(0.0),
    DRAW(0.5),
    WIN(1.0),
    ;

    val mirrored: MatchScore
        get() = when (this) {
            LOSS -> WIN
            DRAW -> DRAW
            WIN -> LOSS
        }
}

data class RatingChange(
    val previousRating: Int,
    val newRating: Int,
) {
    val delta: Int get() = newRating - previousRating
}

data class MatchRatingResult(
    val playerOne: RatingChange,
    val playerTwo: RatingChange,
)

/**
 * Standard Elo, with a provisional K-factor so new players converge quickly and established
 * ones move slowly.
 *
 * This is the single definition of the rating maths. The Cloud Function in
 * functions/src/index.ts mirrors it exactly; EloCalculatorTest pins the values both sides
 * must agree on.
 */
object EloCalculator {
    const val STARTING_RATING = Constants.Backend.STARTING_RATING

    /** Ratings never fall below this, so a losing streak cannot bury an account. */
    const val MINIMUM_RATING = 100

    private const val PROVISIONAL_GAMES = 30
    private const val PROVISIONAL_K = 40
    private const val STANDARD_K = 20
    private const val ELITE_K = 10
    private const val ELITE_RATING = 2_400

    /** Probability that a player of [rating] beats one of [opponentRating]. */
    fun expectedScore(rating: Int, opponentRating: Int): Double =
        1.0 / (1.0 + 10.0.pow((opponentRating - rating) / 400.0))

    fun kFactor(rating: Int, gamesPlayed: Int): Int = when {
        gamesPlayed < PROVISIONAL_GAMES -> PROVISIONAL_K
        rating >= ELITE_RATING -> ELITE_K
        else -> STANDARD_K
    }

    fun newRating(rating: Int, opponentRating: Int, score: MatchScore, gamesPlayed: Int): Int {
        val expected = expectedScore(rating, opponentRating)
        val k = kFactor(rating, gamesPlayed)
        val updated = rating + k * (score.value - expected)
        return updated.roundToInt().coerceAtLeast(MINIMUM_RATING)
    }

    /**
     * Rates both sides of a match from the ratings they held when it started, so the order
     * the two updates are written cannot change the outcome.
     */
    fun rateMatch(
        playerOneRating: Int,
        playerOneGames: Int,
        playerTwoRating: Int,
        playerTwoGames: Int,
        playerOneScore: MatchScore,
    ): MatchRatingResult = MatchRatingResult(
        playerOne = RatingChange(
            previousRating = playerOneRating,
            newRating = newRating(playerOneRating, playerTwoRating, playerOneScore, playerOneGames),
        ),
        playerTwo = RatingChange(
            previousRating = playerTwoRating,
            newRating = newRating(
                playerTwoRating,
                playerOneRating,
                playerOneScore.mirrored,
                playerTwoGames,
            ),
        ),
    )
}
