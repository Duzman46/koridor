import assert from "node:assert/strict";
import { test } from "node:test";
import {
  MINIMUM_RATING,
  SCORE_DRAW,
  SCORE_LOSS,
  SCORE_WIN,
  STARTING_RATING,
  expectedScore,
  kFactor,
  newRating,
  rateMatch,
  weekKey,
} from "./elo";

/**
 * These values are duplicated in EloCalculatorTest.kt. If one side changes, both fail —
 * which is the point: the app and the server must rate a match identically.
 */

test("equal ratings expect an even game", () => {
  assert.equal(expectedScore(1000, 1000), 0.5);
});

test("a 400 point lead is roughly a ten to one favourite", () => {
  const expected = expectedScore(1400, 1000);
  assert.ok(Math.abs(expected - 0.909) < 0.001, `got ${expected}`);
});

test("k factor drops as a player establishes and climbs", () => {
  assert.equal(kFactor(1000, 0), 40);
  assert.equal(kFactor(1000, 29), 40);
  assert.equal(kFactor(1000, 30), 20);
  assert.equal(kFactor(2400, 100), 10);
});

test("evenly matched win moves both by the same amount", () => {
  const result = rateMatch(1000, 50, 1000, 50, SCORE_WIN);
  assert.equal(result.playerOne.newRating, 1010);
  assert.equal(result.playerTwo.newRating, 990);
});

test("a draw between equals changes nothing", () => {
  const result = rateMatch(1000, 50, 1000, 50, SCORE_DRAW);
  assert.equal(result.playerOne.newRating, 1000);
  assert.equal(result.playerTwo.newRating, 1000);
});

test("beating a stronger player is worth more", () => {
  const upset = rateMatch(1000, 50, 1400, 50, SCORE_WIN);
  const expected = rateMatch(1400, 50, 1000, 50, SCORE_WIN);
  assert.ok(
    upset.playerOne.newRating - 1000 > expected.playerOne.newRating - 1400,
    "an upset should be worth more than the expected result"
  );
});

test("rating never falls below the floor", () => {
  assert.equal(newRating(MINIMUM_RATING, 2400, SCORE_LOSS, 100), MINIMUM_RATING);
});

test("starting rating is one thousand", () => {
  assert.equal(STARTING_RATING, 1000);
});

test("week key follows ISO-8601", () => {
  // 4 January is always in week 1 by definition.
  assert.equal(weekKey(Date.UTC(2026, 0, 4)), "2026-W01");
  // 1 January 2027 is a Friday, so it belongs to week 53 of the 2026 week-year.
  assert.equal(weekKey(Date.UTC(2027, 0, 1)), "2026-W53");
  assert.equal(weekKey(Date.UTC(2026, 7, 6)), "2026-W32");
});
