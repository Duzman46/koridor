import { initializeApp } from "firebase-admin/app";
import { Database, getDatabase } from "firebase-admin/database";
import { onValueCreated } from "firebase-functions/v2/database";
import { setGlobalOptions } from "firebase-functions/v2";
import { logger } from "firebase-functions";
import {
  MatchScore,
  SCORE_DRAW,
  SCORE_LOSS,
  SCORE_WIN,
  STARTING_RATING,
  rateMatch,
  weekKey,
} from "./elo";

/**
 * A ceiling on how far this can ever scale.
 *
 * Running on a paid plan means a bug or a flood of traffic bills real money, and the
 * default ceiling is a thousand instances. Ten is far more than this game's traffic will
 * ever need — a rating write takes milliseconds — and it turns a runaway loop from a
 * frightening invoice into a queue.
 */
setGlobalOptions({ maxInstances: 10 });

initializeApp();

export { sweepExpiredRooms } from "./rooms";
export { verifyPurchase, revokeEntitlement } from "./purchases";

interface MatchReport {
  roomCode: string;
  hostUid: string;
  guestUid: string;
  /** Empty string means a draw. */
  winnerUid: string;
  endReason: string;
  ranked: boolean;
  turnCount: number;
  reportedAt: number;
  reportedBy: string;
  state: string;
}

interface PlayerRecord {
  rating: number;
  highestRating: number;
  totalGames: number;
  wins: number;
  losses: number;
  draws: number;
  currentWinStreak: number;
  bestWinStreak: number;
  username: string;
  avatarId: string;
  /** An anonymous account. Earns a rating like anyone else, but holds no place on a board. */
  isGuest: boolean;
}

interface WeeklyRecord {
  wins: number;
  totalGames: number;
}

const DEFAULT_AVATAR = "avatar_01";

/** The value `AccountType.GUEST` writes into a profile. Mirrors worker/src/sweep.ts. */
const GUEST_ACCOUNT_TYPE = "GUEST";

/**
 * Applies rating to a reported match.
 *
 * Clients only ever file a report. Every number that affects competition is written here
 * with the Admin SDK, so a modified client cannot award itself points. The database rules
 * deny client writes to those fields, which makes an undeployed function safe by default:
 * ratings simply do not move.
 */
export const rateReportedMatch = onValueCreated(
  { ref: "/matchResults/{matchId}" },
  async (event) => {
    const matchId = event.params.matchId;
    const report = event.data.val() as MatchReport | null;
    if (!report) return;

    const db = getDatabase();
    const stateRef = db.ref(`matchResults/${matchId}/state`);

    // Claim the report first. A function can be delivered more than once, and only the
    // invocation that flips PENDING is allowed to apply rating.
    const claim = await stateRef.transaction((current) => {
      // A transaction's first run is handed the local cache, and for a node this process
      // has never read that is null rather than the stored value. Aborting there ends the
      // transaction without ever asking the server, which refused every first delivery —
      // no match was ever rated. Returning a value instead makes the SDK fetch the real
      // one and run this again, and that run sees PENDING or an already-claimed state.
      if (current === null) return "RATED";
      return current === "PENDING" ? "RATED" : undefined;
    });
    if (!claim.committed || claim.snapshot.val() !== "RATED") {
      logger.info("match already processed", { matchId });
      return;
    }

    try {
      const verdict = await verifyReport(db, report);
      if (verdict !== "ok") {
        logger.warn("rejecting match report", { matchId, reason: verdict });
        await stateRef.set("REJECTED");
        return;
      }
      if (!report.ranked) {
        logger.info("unranked match recorded without rating", { matchId });
        return;
      }
      await applyRating(db, report);
      logger.info("match rated", { matchId });
    } catch (error) {
      // Hand the report back so a retry can pick it up instead of losing the match.
      await stateRef.set("PENDING");
      throw error;
    }
  }
);

/**
 * Re-checks the claim against the room it was played in. The database rules enforce this
 * too, but the authority for rating lives here and must not depend on them.
 */
async function verifyReport(db: Database, report: MatchReport): Promise<string> {
  if (!report.hostUid || !report.guestUid || report.hostUid === report.guestUid) {
    return "invalid participants";
  }
  const room = (await db.ref(`rooms/${report.roomCode}`).get()).val();
  if (!room) return "room no longer exists";
  if (room.status !== "FINISHED") return "room is not finished";
  if (room.hostUserId !== report.hostUid || room.guestUserId !== report.guestUid) {
    return "participants do not match the room";
  }
  if (room.ranked !== report.ranked) return "ranked flag does not match the room";
  // The room's own write rules already proved the outcome is legitimate: a normal win is
  // checked against the final board, a timeout against the server clock, and a resignation
  // can only ever be filed against oneself. Re-derive nothing; just insist they agree.
  if ((room.winnerUserId ?? "") !== report.winnerUid) {
    return "winner does not match the room";
  }
  if ((room.endReason ?? "") !== report.endReason) {
    return "end reason does not match the room";
  }
  if (report.endReason === "NORMAL") {
    // The board only ever names a seat, and the host is not always seat one: choosing red
    // puts them in seat two. A room written before seats existed carries no hostSeat, which
    // means the host opened — the only arrangement that protocol had.
    const hostInSeatTwo = room.hostSeat === "PLAYER_TWO";
    const seatOneUid = hostInSeatTwo ? report.guestUid : report.hostUid;
    const seatTwoUid = hostInSeatTwo ? report.hostUid : report.guestUid;
    const boardStatus = room.board?.status;
    const expected =
      boardStatus === "PLAYER_ONE_WON"
        ? seatOneUid
        : boardStatus === "PLAYER_TWO_WON"
          ? seatTwoUid
          : null;
    if (expected === null) return "board does not show a finished game";
    if (report.winnerUid !== expected) return "winner does not match the board";
  }
  return "ok";
}

async function applyRating(db: Database, report: MatchReport): Promise<void> {
  const week = weekKey(report.reportedAt || Date.now());
  const [host, guest, hostWeek, guestWeek] = await Promise.all([
    readRecord(db, report.hostUid),
    readRecord(db, report.guestUid),
    readWeekly(db, week, report.hostUid),
    readWeekly(db, week, report.guestUid),
  ]);

  const hostScore = scoreFor(report, report.hostUid);
  const guestScore = mirror(hostScore);

  const rated = rateMatch(
    host.rating,
    host.totalGames,
    guest.rating,
    guest.totalGames,
    hostScore
  );

  const updates: Record<string, unknown> = {
    ...playerUpdates(report.hostUid, host, rated.playerOne.newRating, hostScore),
    ...playerUpdates(report.guestUid, guest, rated.playerTwo.newRating, guestScore),
    ...weeklyUpdates(week, report.hostUid, host, hostWeek, rated.playerOne.newRating, hostScore),
    ...weeklyUpdates(week, report.guestUid, guest, guestWeek, rated.playerTwo.newRating, guestScore),
  };

  // A single multi-path update, so both players move together or not at all.
  await db.ref().update(updates);
}

function scoreFor(report: MatchReport, uid: string): MatchScore {
  if (!report.winnerUid) return SCORE_DRAW;
  return report.winnerUid === uid ? SCORE_WIN : SCORE_LOSS;
}

function mirror(score: MatchScore): MatchScore {
  if (score === SCORE_WIN) return SCORE_LOSS;
  if (score === SCORE_LOSS) return SCORE_WIN;
  return SCORE_DRAW;
}

function playerUpdates(
  uid: string,
  record: PlayerRecord,
  rating: number,
  score: MatchScore
): Record<string, unknown> {
  const won = score === SCORE_WIN;
  const drew = score === SCORE_DRAW;
  const streak = won ? record.currentWinStreak + 1 : 0;
  return {
    [`users/${uid}/rating`]: rating,
    // The all-time board is ordered by this second copy rather than by the rating itself, so
    // that a guest — who never gets one written — has no place in the index to be read out of.
    // It is written here, level with the rating, because the two are shown as one row.
    ...(record.isGuest ? {} : { [`users/${uid}/leaderboardRating`]: rating }),
    [`users/${uid}/highestRating`]: Math.max(record.highestRating, rating),
    [`users/${uid}/totalGames`]: record.totalGames + 1,
    [`users/${uid}/wins`]: record.wins + (won ? 1 : 0),
    [`users/${uid}/losses`]: record.losses + (!won && !drew ? 1 : 0),
    [`users/${uid}/draws`]: record.draws + (drew ? 1 : 0),
    [`users/${uid}/currentWinStreak`]: streak,
    [`users/${uid}/bestWinStreak`]: Math.max(record.bestWinStreak, streak),
  };
}

/**
 * The weekly board is denormalised: it carries the name and avatar so a page of fifty rows
 * costs one query instead of fifty profile reads.
 *
 * A guest gets no row. Nothing under `leaderboards` is writable by a client, so the two
 * server-side writers — this and worker/src/sweep.ts — are the only hands that ever write
 * there, and refusing in both is refusing outright.
 */
function weeklyUpdates(
  week: string,
  uid: string,
  record: PlayerRecord,
  weekly: WeeklyRecord,
  rating: number,
  score: MatchScore
): Record<string, unknown> {
  if (record.isGuest) return {};
  const base = `leaderboards/weekly/${week}/${uid}`;
  return {
    [`${base}/username`]: record.username,
    [`${base}/avatarId`]: record.avatarId,
    [`${base}/rating`]: rating,
    [`${base}/wins`]: weekly.wins + (score === SCORE_WIN ? 1 : 0),
    [`${base}/totalGames`]: weekly.totalGames + 1,
  };
}

async function readRecord(db: Database, uid: string): Promise<PlayerRecord> {
  const value = (await db.ref(`users/${uid}`).get()).val() ?? {};
  return {
    rating: numberOr(value.rating, STARTING_RATING),
    highestRating: numberOr(value.highestRating, STARTING_RATING),
    totalGames: numberOr(value.totalGames, 0),
    wins: numberOr(value.wins, 0),
    losses: numberOr(value.losses, 0),
    draws: numberOr(value.draws, 0),
    currentWinStreak: numberOr(value.currentWinStreak, 0),
    bestWinStreak: numberOr(value.bestWinStreak, 0),
    username: typeof value.username === "string" ? value.username : "",
    avatarId: typeof value.avatarId === "string" ? value.avatarId : DEFAULT_AVATAR,
    isGuest: value.accountType === GUEST_ACCOUNT_TYPE,
  };
}

async function readWeekly(db: Database, week: string, uid: string): Promise<WeeklyRecord> {
  const value = (await db.ref(`leaderboards/weekly/${week}/${uid}`).get()).val() ?? {};
  return {
    wins: numberOr(value.wins, 0),
    totalGames: numberOr(value.totalGames, 0),
  };
}

function numberOr(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}
