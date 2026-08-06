package com.duzman46.gridbound.rating

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These values are duplicated in functions/src/elo.test.ts. If one side drifts, both fail —
 * which is the point: the app and the rating function must agree on every match.
 */
class EloCalculatorTest {

    @Test
    fun `equal ratings expect an even game`() {
        assertEquals(0.5, EloCalculator.expectedScore(1000, 1000), 1e-9)
    }

    @Test
    fun `a 400 point lead is roughly a ten to one favourite`() {
        val expected = EloCalculator.expectedScore(1400, 1000)
        assertTrue("got $expected", abs(expected - 0.909) < 0.001)
    }

    @Test
    fun `expected scores of both sides sum to one`() {
        val a = EloCalculator.expectedScore(1234, 1567)
        val b = EloCalculator.expectedScore(1567, 1234)
        assertEquals(1.0, a + b, 1e-9)
    }

    @Test
    fun `k factor drops as a player establishes and climbs`() {
        assertEquals(40, EloCalculator.kFactor(1000, 0))
        assertEquals(40, EloCalculator.kFactor(1000, 29))
        assertEquals(20, EloCalculator.kFactor(1000, 30))
        assertEquals(10, EloCalculator.kFactor(2400, 100))
    }

    @Test
    fun `evenly matched win moves both by the same amount`() {
        val result = EloCalculator.rateMatch(1000, 50, 1000, 50, MatchScore.WIN)
        assertEquals(1010, result.playerOne.newRating)
        assertEquals(990, result.playerTwo.newRating)
        assertEquals(10, result.playerOne.delta)
        assertEquals(-10, result.playerTwo.delta)
    }

    @Test
    fun `a draw between equals changes nothing`() {
        val result = EloCalculator.rateMatch(1000, 50, 1000, 50, MatchScore.DRAW)
        assertEquals(1000, result.playerOne.newRating)
        assertEquals(1000, result.playerTwo.newRating)
    }

    @Test
    fun `beating a stronger player is worth more`() {
        val upset = EloCalculator.rateMatch(1000, 50, 1400, 50, MatchScore.WIN)
        val expected = EloCalculator.rateMatch(1400, 50, 1000, 50, MatchScore.WIN)
        assertTrue(upset.playerOne.delta > expected.playerOne.delta)
    }

    @Test
    fun `rating never falls below the floor`() {
        assertEquals(
            EloCalculator.MINIMUM_RATING,
            EloCalculator.newRating(EloCalculator.MINIMUM_RATING, 2400, MatchScore.LOSS, 100),
        )
    }

    @Test
    fun `a new player moves faster than an established one`() {
        val newcomer = EloCalculator.rateMatch(1000, 0, 1000, 0, MatchScore.WIN)
        val veteran = EloCalculator.rateMatch(1000, 100, 1000, 100, MatchScore.WIN)
        assertTrue(newcomer.playerOne.delta > veteran.playerOne.delta)
    }

    @Test
    fun `rating is zero sum for equally established players`() {
        val result = EloCalculator.rateMatch(1180, 50, 1320, 50, MatchScore.WIN)
        assertEquals(0, result.playerOne.delta + result.playerTwo.delta)
    }

    @Test
    fun `scores mirror correctly`() {
        assertEquals(MatchScore.LOSS, MatchScore.WIN.mirrored)
        assertEquals(MatchScore.WIN, MatchScore.LOSS.mirrored)
        assertEquals(MatchScore.DRAW, MatchScore.DRAW.mirrored)
    }

    @Test
    fun `starting rating is one thousand`() {
        assertEquals(1000, EloCalculator.STARTING_RATING)
    }
}
