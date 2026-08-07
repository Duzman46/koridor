/**
 * Elo maths, mirroring app/src/main/kotlin/com/duzman46/gridbound/rating/EloCalculator.kt.
 *
 * The implementations must stay in step, and the way they are held in step is that each one
 * has a test pinning the same reference values: EloCalculatorTest on the Android side, and
 * elo.test.ts beside each copy of this file. Change a constant here and the other tests fail.
 */

export const STARTING_RATING = 1000;
export const MINIMUM_RATING = 100;

const PROVISIONAL_GAMES = 30;
const PROVISIONAL_K = 40;
const STANDARD_K = 20;
const ELITE_K = 10;
const ELITE_RATING = 2400;

export type MatchScore = 0 | 0.5 | 1;

export const SCORE_LOSS: MatchScore = 0;
export const SCORE_DRAW: MatchScore = 0.5;
export const SCORE_WIN: MatchScore = 1;

export function expectedScore(rating: number, opponentRating: number): number {
  return 1 / (1 + Math.pow(10, (opponentRating - rating) / 400));
}

export function kFactor(rating: number, gamesPlayed: number): number {
  if (gamesPlayed < PROVISIONAL_GAMES) return PROVISIONAL_K;
  if (rating >= ELITE_RATING) return ELITE_K;
  return STANDARD_K;
}

/**
 * Rounds half away from zero to match Kotlin's roundToInt, which rounds .5 up. JavaScript's
 * Math.round rounds -0.5 to -0
 * , but ratings here are always positive so the two agree.
 */
function roundHalfUp(value: number): number {
  return Math.floor(value + 0.5);
}

export function newRating(
  rating: number,
  opponentRating: number,
  score: MatchScore,
  gamesPlayed: number
): number {
  const expected = expectedScore(rating, opponentRating);
  const k = kFactor(rating, gamesPlayed);
  const updated = rating + k * (score - expected);
  return Math.max(MINIMUM_RATING, roundHalfUp(updated));
}

export interface RatingChange {
  previousRating: number;
  newRating: number;
}

export interface MatchRatingResult {
  playerOne: RatingChange;
  playerTwo: RatingChange;
}

function mirror(score: MatchScore): MatchScore {
  if (score === SCORE_WIN) return SCORE_LOSS;
  if (score === SCORE_LOSS) return SCORE_WIN;
  return SCORE_DRAW;
}

/**
 * Rates both sides from the ratings held when the match started, so the order the two
 * updates are written cannot change the result.
 */
export function rateMatch(
  playerOneRating: number,
  playerOneGames: number,
  playerTwoRating: number,
  playerTwoGames: number,
  playerOneScore: MatchScore
): MatchRatingResult {
  return {
    playerOne: {
      previousRating: playerOneRating,
      newRating: newRating(playerOneRating, playerTwoRating, playerOneScore, playerOneGames),
    },
    playerTwo: {
      previousRating: playerTwoRating,
      newRating: newRating(
        playerTwoRating,
        playerOneRating,
        mirror(playerOneScore),
        playerTwoGames
      ),
    },
  };
}

/**
 * ISO-8601 week key such as `2026-W32`, computed in UTC.
 * Mirrors LeaderboardWeek.keyFor in the Android app.
 */
export function weekKey(epochMillis: number): string {
  const date = new Date(epochMillis);
  const target = new Date(
    Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate())
  );
  // ISO weeks run Monday-Sunday and belong to the year containing their Thursday.
  const dayNumber = (target.getUTCDay() + 6) % 7;
  target.setUTCDate(target.getUTCDate() - dayNumber + 3);
  const isoYear = target.getUTCFullYear();
  const firstThursday = new Date(Date.UTC(isoYear, 0, 4));
  const firstDayNumber = (firstThursday.getUTCDay() + 6) % 7;
  firstThursday.setUTCDate(firstThursday.getUTCDate() - firstDayNumber + 3);
  const week =
    1 + Math.round((target.getTime() - firstThursday.getTime()) / (7 * 24 * 3600 * 1000));
  return `${String(isoYear).padStart(4, "0")}-W${String(week).padStart(2, "0")}`;
}
