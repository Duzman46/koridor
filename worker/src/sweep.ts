import { Rtdb } from "./db.js";
import {
  MatchScore,
  SCORE_DRAW,
  SCORE_LOSS,
  SCORE_WIN,
  STARTING_RATING,
  rateMatch,
  weekKey,
} from "./elo.js";

/**
 * Everything the server does, once a minute.
 *
 * Six jobs: rate the matches players have reported — and record every one of them in both
 * players' histories — clear away rooms nobody played out, pair whoever the phones left
 * waiting in the matchmaking list, keep the all-time board's index complete, collect the
 * request-channel entries no client can reach, and clear the weekly board of the rows and the
 * weeks nothing stands behind. The first two used to be Cloud Functions — one
 * triggered by a database write, one by a schedule — and both are now polled instead, because
 * a worker cannot subscribe to database events. Polling is the lesser evil here: it needs no
 * endpoint open to the internet, and a report that arrives while a run is already going is
 * simply picked up by the next one.
 */

/**
 * How many reports one run will take on.
 *
 * The ceiling is not the work, it is subrequests: the free plan allows fifty outbound requests
 * per invocation and one rated report costs ten — two to claim it, one to re-check the room,
 * four to read both records and both weekly rows, two to read both histories, and one for the
 * update that lands all of it. Three of those leaves twenty for the pending query, the room
 * sweep and the matchmaking backstop, and the worst those can want between them is seventeen.
 * At one run a minute that is still four thousand matches a day, which is far more than this
 * game will produce.
 *
 * Exported so the end-to-end test knows how many runs a batch of reports needs; a run that
 * silently left one behind would look exactly like a run that rated everything.
 */
export const MAX_REPORTS_PER_RUN = 3;

/** Rooms handled per run. One query and one update however many come back. */
const MAX_ROOMS_PER_RUN = 200;

/** Waiting-list entries read per run. One query and one update however many come back. */
const MAX_QUEUE_ENTRIES_PER_RUN = 60;

/**
 * Pairs made per run. Each costs one read — is that room code free? — on top of the shared
 * update, and the free plan's fifty subrequests are mostly spent on rating reports.
 *
 * Four a minute is far more than the phones ever leave behind: this only picks up players the
 * client pairing could not match to each other, which needs an odd number waiting or a device
 * that lost the race and has since gone quiet.
 */
const MAX_QUEUE_PAIRS_PER_RUN = 4;

/**
 * How long an entry is honoured.
 *
 * Almost nothing should ever reach this. A phone that closes gives its place up, and one that
 * dies has the removal run for it by the onDisconnect handler the database holds. This is the
 * backstop for neither happening — a connection lost at exactly the wrong moment. Clearing the
 * entry of a player who is in fact still waiting costs them nothing: their app notices the
 * place is gone and takes a new one. Constants.Online.MATCHMAKING_STALE_MILLIS is the same
 * number, and is what stops a phone claiming an entry this run is about to delete.
 */
const QUEUE_STALE_MILLIS = 5 * 60 * 1000;

/**
 * Profiles examined per backfill lap. One query and one update however many come back.
 *
 * Exported so the end-to-end test can seed past it: a walk that fits in one page never runs
 * the half of this job that moves the cursor.
 */
export const MAX_BACKFILL_PROFILES_PER_RUN = 40;

/**
 * How far through the profile tree the backfill walk has got.
 *
 * Nothing under `maintenance` is readable or writable by any client — database.rules.json
 * denies the whole subtree — so this is the worker talking to itself between runs.
 */
const BACKFILL_CURSOR_PATH = "maintenance/boardIndexBackfill/cursor";

/** invites/{recipient}/{sender} — the live request channel both players listen on. */
const INVITES_PATH = "invites";

/** How far through the request channel the collector has walked. See [BACKFILL_CURSOR_PATH]. */
const INVITE_CURSOR_PATH = "maintenance/inviteSweep/cursor";

/**
 * Recipients examined per lap. One query and one update however many come back.
 *
 * Exported so the end-to-end test can seed past it: a walk that fits in one page never runs
 * the half of this job that moves the cursor.
 */
export const MAX_INVITE_RECIPIENTS_PER_RUN = 50;

/**
 * How many senders one lap will look up the existence of.
 *
 * Only entries that have *not* expired cost anything here — the rest are already collected on
 * their stamp alone — so this is a cap on live entries whose sender might have deleted their
 * account since, which in a healthy database is a handful at a time. Ten keeps the whole job
 * inside thirteen subrequests: the cursor, the page, these, and the update.
 */
const MAX_INVITE_SENDER_CHECKS_PER_RUN = 10;

/** leaderboards/weekly/{week}/{uid} — the denormalised table the weekly board is read from. */
const WEEKLY_BOARD_PATH = "leaderboards/weekly";

/** How far through this week's board the prune has walked. See [BACKFILL_CURSOR_PATH]. */
const WEEKLY_CURSOR_PATH = "maintenance/weeklyBoardSweep/cursor";

/**
 * How many weekly rows one lap looks up the owner of, and how many finished weeks it counts
 * before dropping them.
 *
 * One read each, and both are small because this job shares its minute with the invite
 * collector, which is already the most expensive thing on it. Five and two put the pair at
 * about forty-nine of the free plan's fifty on the worst minute either can run at all — and a
 * board of any size is still covered in a few minutes of laps, while stale weeks arrive at
 * one a week.
 */
const MAX_BOARD_ROW_CHECKS_PER_RUN = 5;
const MAX_FINISHED_WEEKS_PER_RUN = 2;

const WEEK_MILLIS = 7 * 24 * 60 * 60 * 1000;

/** recentMatches/{uid}/{matchId} — the short history shown on a player's profile. */
const RECENT_MATCHES_PATH = "recentMatches";

/**
 * How many matches a profile's history holds before the oldest is dropped.
 *
 * The number is the whole reason the list is safe to read in one go: a profile page fetches
 * the node entire, so a bound here is a bound on every read of it forever. Ten because that is
 * what the profile offers to show — three at rest, all ten once the player asks — and keeping
 * more would be storing rows with nothing that can display them.
 */
const RECENT_MATCHES_KEPT = 10;

const DEFAULT_AVATAR = "avatar_01";

/**
 * The value `AccountType.GUEST` writes into a profile: an anonymous Firebase user, an account
 * that lives on one handset until it is uninstalled.
 *
 * Such a player is rated, because a match is rated without asking who played it, but they are
 * kept off both leaderboards — see [holdsBoardPlace]. A guest's match is meant to be casual and
 * so never reaches [applyRating] at all; the check is here because the flag that makes it casual
 * is set by a phone, and the boards are this worker's to protect.
 */
const GUEST_ACCOUNT_TYPE = "GUEST";

/**
 * The shape of every name the app hands out by itself: `guest_483920` today, `player_a1b2c3`
 * from the scheme before it, either with a digit appended after a collision. `UsernameRules`
 * on the phone holds the same expression and matches it against the same trimmed, lower-cased
 * form.
 */
const GENERATED_NAME = /^(?:guest|player)_[a-z0-9]{1,10}$/;
const EXPIRED_ROOM_GRACE_MILLIS = 60 * 60 * 1000;
const ROOM_EXPIRY_MILLIS = 24 * 60 * 60 * 1000;
const DEFAULT_TURN_SECONDS = 60;

/** Squares a side, and walls each. Both halves of the arithmetic in [boardFlaw]. */
const BOARD_SIZE = 9;
const WALLS_PER_PLAYER = 10;

/**
 * The square each seat opens from, and the file both of them stand on. See [timeoutFlaw].
 *
 * Seat one starts on the far row and walks to row zero; seat two starts on row zero and walks
 * to the far row. Neither has spent a wall yet, so a seat found exactly here, still holding
 * ten, is a seat whose player has not taken a turn.
 */
const STARTING_ROW_SEAT_ONE = BOARD_SIZE - 1;
const STARTING_ROW_SEAT_TWO = 0;
const STARTING_COLUMN = (BOARD_SIZE - 1) / 2;

/** Excludes 0/O and 1/I, exactly as Constants.Online.ROOM_CODE_ALPHABET does. */
const ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const ROOM_CODE_LENGTH = 6;

export interface MatchReport {
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
  /** Whether a public board may carry a row for this account. See [holdsBoardPlace]. */
  onBoards: boolean;
}

interface WeeklyRecord {
  wins: number;
  totalGames: number;
}

/**
 * One line of a player's history, as `recentMatches/{uid}/{matchId}` holds it.
 *
 * The opponent's name is copied in rather than looked up, for the same reason the weekly board
 * copies it: reading ten names back out would cost ten profile reads to draw one card. It is
 * also the only way the row survives the opponent deleting their account, which is a match
 * that still happened.
 */
interface RecentMatch {
  opponentName: string;
  /**
   * Who the opponent was, beside what they were called.
   *
   * The name alone is a caption; this is what makes the row a way of reaching them — their
   * profile, and from there a friend request or a report. It is stored rather than looked up
   * for the same reason the name is: drawing ten rows would otherwise cost ten reads.
   *
   * Absent on a row written before this field existed, which is why every reader has to treat
   * it as optional rather than assume ten rows all carry one.
   */
  opponentUserId?: string;
  result: "WIN" | "LOSS" | "DRAW";
  playedAt: number;
  /**
   * Absent when nothing was at stake — an unranked match, or one against a guest, which the
   * phones mark unranked for the same reason.
   *
   * Absent rather than zero, because zero is a real answer: two evenly matched players who
   * draw move each other's rating by nothing at all, and a screen that cannot tell that from
   * a casual game would report the casual game as a rated one worth no points.
   */
  ratingChange?: number;
}

/** One player waiting to be paired, as matchmaking/{uid} stores them. */
export interface QueueEntry {
  rating: number;
  ranked: boolean;
  queuedAt: number;
}

/**
 * One entry on the request channel, as invites/{recipient}/{sender} holds it.
 *
 * Only the field [collectDeadInvites] judges an entry by. The rest — who is asking, which room,
 * what kind of question — is the recipient's business and none of this worker's.
 */
interface Invite {
  expiresAt?: number;
}

export interface SweepResult {
  rated: number;
  unranked: number;
  rejected: number;
  skipped: number;
  roomsRemoved: number;
  roomsExpired: number;
  queuePaired: number;
  queueDropped: number;
  boardIndexed: number;
  invitesCollected: number;
  boardRowsRemoved: number;
}

export async function sweep(db: Rtdb, now: number): Promise<SweepResult> {
  const result: SweepResult = {
    rated: 0,
    unranked: 0,
    rejected: 0,
    skipped: 0,
    roomsRemoved: 0,
    roomsExpired: 0,
    queuePaired: 0,
    queueDropped: 0,
    boardIndexed: 0,
    invitesCollected: 0,
    boardRowsRemoved: 0,
  };
  await rateReportedMatches(db, now, result);
  await expireRooms(db, now, result);
  await pairWaitingPlayers(db, now, result);
  // Last, and only on a run that rated nothing. A rated match costs ten of the fifty
  // subrequests a run gets, so a full [MAX_REPORTS_PER_RUN] of them leaves no room for
  // anything else — and of everything here this is the one job with nobody waiting on it.
  if (result.rated === 0) await backfillBoardIndex(db, result);
  // Tighter still, because these two are the only jobs that spend a read on a row they are
  // unsure about: they run on a minute that took on no report at all. An unranked report costs
  // eight subrequests without adding to `rated`, so three of those plus the rooms, the queue
  // and the backfill already stand at forty-four of the fifty, and neither would fit. They
  // share the minute they do get, which is why the second one's caps are as low as they are.
  // Nothing is waiting on either — every entry the first collects is one no client would show,
  // and every row the second takes down belongs to nobody at all.
  if (result.rated + result.unranked + result.rejected === 0) {
    await collectDeadInvites(db, now, result);
    await pruneWeeklyBoards(db, now, result);
  }
  return result;
}

async function rateReportedMatches(
  db: Rtdb,
  now: number,
  result: SweepResult
): Promise<void> {
  const pending = await db.get<Record<string, MatchReport>>("matchResults", {
    orderBy: '"state"',
    equalTo: '"PENDING"',
    limitToFirst: String(MAX_REPORTS_PER_RUN),
  });
  if (!pending) return;

  for (const [matchId, report] of Object.entries(pending)) {
    if (!report) continue;
    const statePath = `matchResults/${matchId}/state`;

    // Claim it before doing anything. Two overlapping runs must not both rate a match.
    if (!(await db.claim(statePath, "PENDING", "RATED"))) {
      result.skipped += 1;
      continue;
    }

    try {
      const verdict = await verifyReport(db, report);
      if (verdict !== "ok") {
        console.warn("rejecting match report", { matchId, reason: verdict });
        await db.set(statePath, "REJECTED");
        result.rejected += 1;
        continue;
      }
      if (!report.ranked) {
        await recordCasualMatch(db, matchId, report, now);
        result.unranked += 1;
        continue;
      }
      await applyRating(db, matchId, report, now);
      result.rated += 1;
    } catch (error) {
      // Hand the report back so the next run can pick it up rather than losing the match.
      await db.set(statePath, "PENDING").catch(() => undefined);
      throw error;
    }
  }
}

/**
 * Re-checks the claim against the room it was played in.
 *
 * The database rules enforce all of this too, but the authority for rating lives here and
 * must not depend on them: a rule loosened by accident should cost nothing.
 */
export async function verifyReport(db: Rtdb, report: MatchReport): Promise<string> {
  if (!report.hostUid || !report.guestUid || report.hostUid === report.guestUid) {
    return "invalid participants";
  }
  const room = await db.get<Record<string, unknown>>(`rooms/${report.roomCode}`);
  if (!room) return "room no longer exists";
  if (room.status !== "FINISHED") return "room is not finished";
  if (room.hostUserId !== report.hostUid || room.guestUserId !== report.guestUid) {
    return "participants do not match the room";
  }
  if (room.ranked !== report.ranked) return "ranked flag does not match the room";
  // The room's own write rules bound every step the outcome was reached by: how far a pawn
  // may travel in one turn, what a wall costs, when a clock has run out, and that a
  // resignation can only ever be filed against oneself. What they cannot do is count, so the
  // one part of the board no rule holds is checked here instead — see [boardFlaw].
  if ((room.winnerUserId ?? "") !== report.winnerUid) return "winner does not match the room";
  if ((room.endReason ?? "") !== report.endReason) return "end reason does not match the room";
  const board = room.board as Record<string, unknown> | undefined;
  const flaw = boardFlaw(board);
  if (flaw) return flaw;
  if (report.endReason === "TIMEOUT") {
    const clock = timeoutFlaw(room, board, report);
    if (clock) return clock;
  }
  if (report.endReason === "NORMAL") {
    // The board only ever names a seat, and the host is not always seat one: choosing red
    // puts them in seat two. A room written before seats existed carries no hostSeat, which
    // means the host opened — the only arrangement that protocol had.
    const hostInSeatTwo = room.hostSeat === "PLAYER_TWO";
    const seatOneUid = hostInSeatTwo ? report.guestUid : report.hostUid;
    const seatTwoUid = hostInSeatTwo ? report.hostUid : report.guestUid;
    const expected =
      board?.status === "PLAYER_ONE_WON"
        ? seatOneUid
        : board?.status === "PLAYER_TWO_WON"
          ? seatTwoUid
          : null;
    if (expected === null) return "board does not show a finished game";
    if (report.winnerUid !== expected) return "winner does not match the board";
  }
  return "ok";
}

/**
 * What is wrong with a match said to have been decided by a clock, or null when nothing is.
 *
 * A normal win is re-derived from the board above, because the board says who reached their
 * goal row. A clock says nothing of the sort: the room simply asserts that somebody ran out of
 * time, and the room is written by the players. So a report whose only evidence is that
 * assertion is checked against the one part of the position no opponent can author.
 *
 * The hole this closes was a whole ranked rating taken from a stranger who never played. The
 * queue is readable, so an attacker picks a name off it; a room may be opened naming anyone
 * who is in the queue; and until this ran, nothing between those two facts and a rated win
 * required the named player to touch the room at all. The room could be written with the
 * clock already spent, rewritten a moment later as a timeout, and reported — and every step
 * agreed with the one before it, because the same hand wrote all three.
 *
 * Three things are asked, and the third is the one that holds. A room still at version zero
 * has had no move written to it by anybody. A board still on its first two turns has not been
 * round both players. And a loser whose pawn stands on the square it started from, still
 * holding all ten walls, has not taken a turn — which is a fact about the board, not about
 * the room, and an attacker cannot forge it: the write rules let a player move their own pawn
 * and nobody else's, so the victim's pawn is the one thing in the position that only the
 * victim can have moved.
 *
 * It costs one thing, and knowingly: a match whose loser walked their pawn out and back to
 * exactly where it began, spending no wall, and then ran out of time, is refused as well.
 * That is a position no honest game reaches on purpose — the starting square is the furthest
 * point from the goal — and refusing it is the price of a test that needs nothing from the
 * rules to be true.
 */
function timeoutFlaw(
  room: Record<string, unknown>,
  board: Record<string, unknown> | undefined,
  report: MatchReport
): string | null {
  // A clock that ended in a draw takes nobody's rating, so there is nothing here to protect.
  if (!report.winnerUid) return null;
  if (numberOr(room.version, 0) === 0) return "a clock ran out on a room nobody had played";
  if (numberOr(board?.turnNumber, 0) < 3) {
    return "a clock ran out before both players had had a turn";
  }
  // See [verifyReport]: the board names seats, and the host is not always seat one.
  const hostInSeatTwo = room.hostSeat === "PLAYER_TWO";
  const seatOneUid = hostInSeatTwo ? report.guestUid : report.hostUid;
  const loserUid = report.winnerUid === report.hostUid ? report.guestUid : report.hostUid;
  const loserIsSeatOne = loserUid === seatOneUid;
  const seats = board?.players as Record<string, Record<string, unknown>> | undefined;
  const loser = loserIsSeatOne ? seats?.PLAYER_ONE : seats?.PLAYER_TWO;
  if (!loser) return "the board does not name both seats";
  const home = loserIsSeatOne ? STARTING_ROW_SEAT_ONE : STARTING_ROW_SEAT_TWO;
  if (
    loser.row === home &&
    loser.column === STARTING_COLUMN &&
    loser.wallsRemaining === WALLS_PER_PLAYER
  ) {
    return "the player said to have run out of time never took a turn";
  }
  return null;
}

/**
 * What is wrong with the board the match ended on, or null when nothing is.
 *
 * The write rules can compare a field against the value it held a moment ago, which is how
 * every pawn step and every wall paid for is bounded. What they cannot do is count children,
 * so `board/walls` is the one part of the board no rule can hold: a modified client may lay
 * walls it never paid for, or sweep the rival's off the board, and no rule will say a word.
 *
 * The arithmetic is what says it. Ten walls each and one spent per wall placed means the two
 * supplies and the walls standing on the board are a conserved twenty, in every position that
 * any sequence of legal turns can produce. A board where they are not is a board that was
 * never played, and a match played on one moves nobody's rating.
 */
function boardFlaw(board: Record<string, unknown> | undefined): string | null {
  const seats = board?.players as Record<string, Record<string, unknown>> | undefined;
  const one = seats?.PLAYER_ONE;
  const two = seats?.PLAYER_TWO;
  if (!one || !two) return "the board does not name both seats";
  for (const seat of [one, two]) {
    if (!onBoard(seat.row) || !onBoard(seat.column)) return "a pawn stands off the board";
  }
  const held = [one.wallsRemaining, two.wallsRemaining];
  if (!held.every((walls) => onBoard(walls, WALLS_PER_PLAYER))) {
    return "a wall supply is not a number of walls";
  }
  // Absent means none were ever placed: an empty list is not stored at all.
  const placed = board?.walls;
  const standing = placed == null ? 0 : Object.keys(placed as object).length;
  if (Number(held[0]) + Number(held[1]) + standing !== 2 * WALLS_PER_PLAYER) {
    return "the walls on the board do not add up to the ones paid for";
  }
  return null;
}

function onBoard(value: unknown, limit: number = BOARD_SIZE - 1): boolean {
  return (
    typeof value === "number" && Number.isInteger(value) && value >= 0 && value <= limit
  );
}

async function applyRating(
  db: Rtdb,
  matchId: string,
  report: MatchReport,
  now: number
): Promise<void> {
  const week = weekKey(report.reportedAt || now);
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

  const playedAt = report.reportedAt || now;
  const [hostHistory, guestHistory] = await Promise.all([
    historyUpdates(
      db,
      report.hostUid,
      matchId,
      played(
        guest.username,
        report.guestUid,
        hostScore,
        playedAt,
        rated.playerOne.newRating - host.rating
      )
    ),
    historyUpdates(
      db,
      report.guestUid,
      matchId,
      played(
        host.username,
        report.hostUid,
        guestScore,
        playedAt,
        rated.playerTwo.newRating - guest.rating
      )
    ),
  ]);

  // A single multi-path update, so both players move together or not at all.
  await db.update({
    ...playerUpdates(report.hostUid, host, rated.playerOne.newRating, hostScore),
    ...playerUpdates(report.guestUid, guest, rated.playerTwo.newRating, guestScore),
    ...weeklyUpdates(week, report.hostUid, host, hostWeek, rated.playerOne.newRating, hostScore),
    ...weeklyUpdates(
      week,
      report.guestUid,
      guest,
      guestWeek,
      rated.playerTwo.newRating,
      guestScore
    ),
    ...hostHistory,
    ...guestHistory,
  });
}

/**
 * Files a match that moved nobody's rating in both players' histories anyway.
 *
 * An unranked game and a game against a guest are still games that were played, and a profile
 * that showed only the rated ones would be telling a player their evening did not happen. The
 * two profile reads here are for the names alone — there is no record to update, which is the
 * whole difference between this and [applyRating].
 */
async function recordCasualMatch(
  db: Rtdb,
  matchId: string,
  report: MatchReport,
  now: number
): Promise<void> {
  const [hostName, guestName] = await Promise.all([
    readUsername(db, report.hostUid),
    readUsername(db, report.guestUid),
  ]);
  const hostScore = scoreFor(report, report.hostUid);
  const playedAt = report.reportedAt || now;
  const [hostHistory, guestHistory] = await Promise.all([
    historyUpdates(db, report.hostUid, matchId, played(guestName, report.guestUid, hostScore, playedAt, null)),
    historyUpdates(
      db,
      report.guestUid,
      matchId,
      played(hostName, report.hostUid, mirror(hostScore), playedAt, null)
    ),
  ]);
  await db.update({ ...hostHistory, ...guestHistory });
}

/**
 * The paths that put [entry] at the head of one player's history and drop whatever that
 * pushes off the end.
 *
 * ## Why the server writes this and not the phones
 *
 * A history sits on a public profile, and a history a phone writes is a history a phone
 * edits: nothing in the database can tell a genuine report of a loss from a modified client
 * choosing not to file one, or filing it as a win against a name it invented. The only writer
 * that can be trusted with a record of who beat whom is the one holding a credential no player
 * has — and that writer is already here, reading and writing both players at the end of every
 * match. So the rules grant no client any write under `recentMatches`, and this is the hand
 * that fills it. Reading it is another matter entirely: the list *is* the public part, and any
 * signed-in player may read anyone's.
 *
 * Keyed by the match rather than by a push id, so a report that is somehow processed twice
 * overwrites its own row instead of appearing as two games. That mirrors the write-once rule
 * on the report itself: the same match is the same row wherever it is written.
 */
async function historyUpdates(
  db: Rtdb,
  uid: string,
  matchId: string,
  entry: RecentMatch
): Promise<Record<string, unknown>> {
  const base = `${RECENT_MATCHES_PATH}/${uid}`;
  const held = (await db.get<Record<string, RecentMatch | null>>(base)) ?? {};
  const updates: Record<string, unknown> = { [`${base}/${matchId}`]: entry };
  const older = Object.entries(held)
    // The row being written is not one of the ones it could displace.
    .filter((row): row is [string, RecentMatch] => !!row[1] && row[0] !== matchId)
    // Newest first, so what falls off the end is the oldest. The key breaks a tie because two
    // matches reported in the same millisecond still have to be dropped in a settled order:
    // leaving it to the order a JSON parser handed the members back would drop a different
    // one on every run, and the list would flicker rather than age.
    .sort((a, b) => b[1].playedAt - a[1].playedAt || (a[0] < b[0] ? -1 : 1));
  // The new row has already taken a place, so only one short of the cap survives beside it.
  for (const [id] of older.slice(RECENT_MATCHES_KEPT - 1)) updates[`${base}/${id}`] = null;
  return updates;
}

function played(
  opponentName: string,
  opponentUserId: string,
  score: MatchScore,
  playedAt: number,
  ratingChange: number | null
): RecentMatch {
  return {
    opponentName,
    // Omitted rather than written empty when the report does not name the opponent, so a
    // reader's "is there someone to open here" is one question about presence and not two.
    ...(opponentUserId ? { opponentUserId } : {}),
    result: score === SCORE_WIN ? "WIN" : score === SCORE_LOSS ? "LOSS" : "DRAW",
    playedAt,
    // See [RecentMatch.ratingChange]: a match nobody was rated on carries no number at all,
    // because zero is something else.
    ...(ratingChange === null ? {} : { ratingChange }),
  };
}

/**
 * Clears away rooms that were never played out.
 *
 * Rooms carry an `expiresAt` stamp: half an hour for one still waiting for an opponent, a day
 * once a match is under way. Without this the database accumulates abandoned rooms forever.
 * A match that ran past its window is closed rather than deleted, so both players still see
 * why it ended instead of the room vanishing from under them.
 */
async function expireRooms(db: Rtdb, now: number, result: SweepResult): Promise<void> {
  const expired = await db.get<Record<string, Record<string, unknown>>>("rooms", {
    orderBy: '"expiresAt"',
    endAt: String(now),
    limitToFirst: String(MAX_ROOMS_PER_RUN),
  });
  if (!expired) return;

  const updates: Record<string, unknown> = {};
  for (const [code, room] of Object.entries(expired)) {
    if (!room) continue;
    if (room.status === "IN_PROGRESS" || room.status === "STARTING") {
      updates[`rooms/${code}/status`] = "EXPIRED";
      updates[`rooms/${code}/currentTurnUserId`] = "";
      updates[`rooms/${code}/expiresAt`] = now + EXPIRED_ROOM_GRACE_MILLIS;
      updates[`rooms/${code}/browseKey`] = `${room.visibility ?? "PRIVATE"}_EXPIRED`;
      result.roomsExpired += 1;
    } else {
      updates[`rooms/${code}`] = null;
      updates[`roomSecrets/${code}`] = null;
      result.roomsRemoved += 1;
    }
  }
  await db.update(updates);
}

/**
 * Pairs whoever is still waiting, and clears out entries nothing is behind any more.
 *
 * The phones pair each other and normally get there first — this is what catches the cases
 * they cannot. An odd number waiting always leaves one over; a device that lost a race and
 * then went quiet leaves its partner unclaimed; a phone that never gets a stable connection
 * writes itself into the list and then does nothing at all. Without this the list would fill
 * with names nobody can play, and the last player to arrive would wait forever next to them.
 */
async function pairWaitingPlayers(
  db: Rtdb,
  now: number,
  result: SweepResult
): Promise<void> {
  const listed = await db.get<Record<string, QueueEntry>>("matchmaking", {
    orderBy: '"queuedAt"',
    limitToFirst: String(MAX_QUEUE_ENTRIES_PER_RUN),
  });
  if (!listed) return;

  const updates: Record<string, unknown> = {};
  const waiting: Array<[string, QueueEntry]> = [];
  for (const [uid, entry] of Object.entries(listed)) {
    if (isLive(entry, now)) {
      waiting.push([uid, entry]);
    } else {
      updates[`matchmaking/${uid}`] = null;
      result.queueDropped += 1;
    }
  }

  for (const pair of closestPairs(waiting, MAX_QUEUE_PAIRS_PER_RUN)) {
    const [[guestUid, guest], [hostUid, host]] = asThePhonesWouldHaveIt(pair);
    const code = await freeRoomCode(db, guestUid, guest.queuedAt);
    // No code means a room already sits where this pair would go, which is a phone that
    // paired them a moment ago. Leaving them is the right answer: the entries they no longer
    // need are cleared by their own clients or by staleness.
    if (!code) continue;
    updates[`rooms/${code}`] = pairedRoom(hostUid, host, guestUid, guest, now);
    updates[`matchmaking/${hostUid}`] = null;
    updates[`matchmaking/${guestUid}`] = null;
    result.queuePaired += 1;
  }

  await db.update(updates);
}

function isLive(entry: QueueEntry | null, now: number): boolean {
  return (
    !!entry &&
    typeof entry.rating === "number" &&
    typeof entry.queuedAt === "number" &&
    now - entry.queuedAt < QUEUE_STALE_MILLIS
  );
}

/**
 * The closest-rated pairs first, up to [limit].
 *
 * Sorting by rating puts every player next to whoever is nearest them, so the only pairs worth
 * considering are neighbours in that order; taking the smallest gaps first and skipping anyone
 * already spoken for is the greedy closest-first match. Exported because the ordering is the
 * whole point of pairing on rating at all, and it is worth being able to check directly.
 */
/**
 * The same two players, turned the way a phone would have arranged them: the one being
 * claimed first, the one doing the claiming second.
 *
 * The handsets settle this between themselves with no server in the loop, so the arrangement
 * has to be a fact about the entries: `MatchmakingRules.opens` gives the room to whoever
 * queued later — the device that has just looked — and names it after the other one, because
 * the room is the lock two devices going for the same person both aim at. [closestPairs]
 * hands its pairs back in rating order, which is unrelated, so about half of them arrive the
 * wrong way up. Deriving the room code from that half asks whether a room stands at a code no
 * phone would ever have used, and the check that exists to find a pairing the clients already
 * made quietly finds nothing and writes a second room for the same two people.
 *
 * Exported for the same reason [closestPairs] is: it is a claim about what the phones do, and
 * the only place it can be checked is against them.
 */
export function asThePhonesWouldHaveIt(
  pair: [[string, QueueEntry], [string, QueueEntry]]
): [[string, QueueEntry], [string, QueueEntry]] {
  const [one, two] = pair;
  const claimedFirst =
    one[1].queuedAt !== two[1].queuedAt
      ? one[1].queuedAt < two[1].queuedAt
      : one[0] < two[0];
  return claimedFirst ? [one, two] : [two, one];
}

export function closestPairs(
  waiting: Array<[string, QueueEntry]>,
  limit: number
): Array<[[string, QueueEntry], [string, QueueEntry]]> {
  const sorted = [...waiting].sort(
    (a, b) => a[1].rating - b[1].rating || (a[0] < b[0] ? -1 : 1)
  );
  const gaps = sorted
    .slice(0, -1)
    .map((entry, index) => ({ index, gap: sorted[index + 1][1].rating - entry[1].rating }))
    .sort((a, b) => a.gap - b.gap || a.index - b.index);

  const spokenFor = new Set<number>();
  const pairs: Array<[[string, QueueEntry], [string, QueueEntry]]> = [];
  for (const { index } of gaps) {
    if (pairs.length >= limit) break;
    if (spokenFor.has(index) || spokenFor.has(index + 1)) continue;
    spokenFor.add(index);
    spokenFor.add(index + 1);
    pairs.push([sorted[index], sorted[index + 1]]);
  }
  return pairs;
}

/**
 * The one room code this pairing may use, or null because something already occupies it.
 *
 * There is exactly one candidate, and no salt behind it. The code is the lock on claiming a
 * player — two devices going for the same person aim at one node and the transaction there
 * lets one of them through — and a lock only works where everybody computes the same one, so
 * a room already standing at it is not a collision to be worked around: it is the pairing,
 * written by a handset a moment ago, and the right answer is to leave the two of them in it.
 * Salting past it would write a second room for the same two players, which is the whole
 * thing this check exists to prevent. The phones have no salt either, for the same reason: a
 * device whose transaction finds the node taken simply keeps waiting.
 */
async function freeRoomCode(
  db: Rtdb,
  claimedUid: string,
  queuedAt: number
): Promise<string | null> {
  const code = await meetingCode(claimedUid, queuedAt);
  return (await db.get(`rooms/${code}`)) === null ? code : null;
}

/** The seed `RoomCredentials.meetingCode` folds on the phone, character for character. */
const QUEUE_SEED = (claimedUid: string, queuedAt: number): string =>
  `koridor:queue:${claimedUid}:${queuedAt}`;

/**
 * The room a phone would have opened for the pairing that claimed [claimedUid].
 *
 * Exported so the end-to-end can put a room exactly where a client's own pairing would have
 * left one, which is the only way to exercise the check [freeRoomCode] exists for.
 */
export async function meetingCode(claimedUid: string, queuedAt: number): Promise<string> {
  return roomCode(QUEUE_SEED(claimedUid, queuedAt));
}

/** Folds a seed into the room alphabet, the same way RoomCredentials.meetingCode does. */
async function roomCode(seed: string): Promise<string> {
  const digest = new Uint8Array(
    await crypto.subtle.digest("SHA-256", new TextEncoder().encode(seed))
  );
  let code = "";
  for (let index = 0; index < ROOM_CODE_LENGTH; index += 1) {
    code += ROOM_CODE_ALPHABET[digest[index] % ROOM_CODE_ALPHABET.length];
  }
  return code;
}

/**
 * The room a pair is dropped into: two players, already seated, already playing.
 *
 * The seats are drawn here because neither player chose a colour — quick match never offers
 * one — and blue opens whoever holds it. Unlisted, because the browser is for rooms with a
 * seat going spare and this has none.
 *
 * `createdAt` is server-stamped: the clients tell this room apart from a match they walked
 * out of earlier by asking whether it was made after they queued, and both stamps have to
 * come off the same clock.
 */
function pairedRoom(
  hostUid: string,
  host: QueueEntry,
  guestUid: string,
  guest: QueueEntry,
  now: number
): Record<string, unknown> {
  const hostSeat = Math.random() < 0.5 ? "PLAYER_ONE" : "PLAYER_TWO";
  return {
    roomName: "",
    hostUserId: hostUid,
    guestUserId: guestUid,
    hostName: "",
    hostRating: host.rating,
    visibility: "PRIVATE",
    status: "IN_PROGRESS",
    gameMode: "CLASSIC",
    // A guest's result cannot move a rating, so one guest makes the match casual.
    ranked: host.ranked === true && guest.ranked === true,
    requiresPassword: false,
    createdAt: { ".sv": "timestamp" },
    expiresAt: now + ROOM_EXPIRY_MILLIS,
    turnDurationSeconds: DEFAULT_TURN_SECONDS,
    currentTurnUserId: hostSeat === "PLAYER_ONE" ? hostUid : guestUid,
    board: {
      currentPlayer: "PLAYER_ONE",
      status: "IN_PROGRESS",
      turnNumber: 1,
      players: {
        PLAYER_ONE: { row: 8, column: 4, wallsRemaining: 10 },
        PLAYER_TWO: { row: 0, column: 4, wallsRemaining: 10 },
      },
    },
    lastMoveAt: { ".sv": "timestamp" },
    winnerUserId: "",
    endReason: "",
    version: 0,
    hostSeat,
    browseKey: "PRIVATE_IN_PROGRESS",
  };
}

/**
 * Puts profiles into the index the all-time board is ordered by, a page of the tree at a time.
 *
 * The board reads `/users` ordered by `leaderboardRating` — see [playerUpdates] for why that
 * is a second copy of the rating rather than the rating itself — and Realtime Database leaves
 * a child with no value for the sort key out of the answer entirely, without erroring. So a
 * profile that has never had the key written is not low on the table, it is absent from it,
 * and a board of nothing but absent profiles is an empty board that reports no fault.
 *
 * Every profile written before the key existed is in exactly that state, and the two hands
 * that write it cannot reach them: the client writes only its own profile and only when
 * somebody signs in or links an account, which a player with a session already on their phone
 * never does again, and [playerUpdates] writes it only for the two people in a rated match.
 * This is the third hand, and the only one that can touch a profile whose owner is not there:
 * it holds the admin credential, so it needs nothing from the player at all.
 *
 * The walk is by key with a stored cursor rather than by the sort key itself, because ordering
 * by the very key that is missing would put the guests — who correctly have none, and never
 * will — permanently at the front of every page, and the walk would never get past them.
 *
 * Reaching the end starts the walk again rather than stopping. It costs one query on a tree
 * that is by then fully indexed, and it repairs the one gap that can still open: a client that
 * creates a profile and dies before claiming its place on the board.
 */
async function backfillBoardIndex(db: Rtdb, result: SweepResult): Promise<void> {
  const cursor = (await db.get<string>(BACKFILL_CURSOR_PATH)) ?? "";
  const page = await db.get<Record<string, Record<string, unknown> | null>>("users", {
    orderBy: '"$key"',
    limitToFirst: String(MAX_BACKFILL_PROFILES_PER_RUN),
    // No cursor is the start of the tree, and no key names that: the database rejects the
    // empty string as a query bound, so the first lap of a walk is simply unbounded.
    ...(cursor ? { startAt: JSON.stringify(cursor) } : {}),
  });
  const rows = Object.entries(page ?? {});

  const updates: Record<string, unknown> = {};
  for (const [uid, value] of rows) {
    // `startAt` is inclusive, so the row the last lap finished on comes back again.
    if (!value || uid === cursor) continue;
    if (typeof value.leaderboardRating === "number") continue;
    // An account still wearing the name the app invented is not late to the board, it is not
    // due on it — see [holdsBoardPlace]. It is taken on by the lap after it has a name, which
    // is the whole reason this walk keeps going round rather than stopping when it runs out.
    if (!holdsBoardPlace(value)) continue;
    updates[`users/${uid}/leaderboardRating`] = numberOr(value.rating, STARTING_RATING);
    result.boardIndexed += 1;
  }

  // A page short of the limit is the end of the tree.
  const exhausted = rows.length < MAX_BACKFILL_PROFILES_PER_RUN;
  // The highest key that came back, not the last one to arrive. The REST API answers a query
  // with a JSON object and makes no promise that a parser hands the members back in the order
  // it sorted them, whereas *which* keys are in the page is exact: the first page-full at or
  // above the cursor. So the largest of them is where the next lap starts, and taking it that
  // way cannot land short — a cursor that went backwards would fetch the same page for ever,
  // indexing nothing and reporting nothing wrong.
  updates[BACKFILL_CURSOR_PATH] = exhausted
    ? ""
    : rows.reduce((highest, [uid]) => (uid > highest ? uid : highest), "");
  await db.update(updates);
}

/**
 * Clears request-channel entries that no client will ever show, a page of the tree at a time.
 *
 * ## Why this cannot be the phones' job
 *
 * `invites/{recipient}/{sender}` is written by the sender and read by nobody else: the rules
 * grant `.read` at `invites/$recipient` to that recipient alone, and permission only ever flows
 * downwards, so the sender cannot list a channel, cannot read the single row they themselves
 * wrote, and cannot find out whether it is still there. They *may* delete it — the rule admits
 * `auth.uid == $sender && !newData.exists()` — but only by naming the recipient, and there is
 * nothing they are allowed to read that enumerates who that was.
 *
 * That is what leaves an erased account behind. An erasure walks the friend list and cleans the
 * other side of every relationship on it, which is every game invitation, because friendship is
 * what the rules charge for one. A rematch is charged for differently — the finished match is
 * the licence, and the two players need never have been friends — so `invites/{opponent}/{uid}`
 * is a row the deleting player cannot name and no other client is allowed to touch. "Delete
 * permanently" has to mean it, so the hand that keeps that promise is this one: it holds the
 * admin credential, so the read the rules refuse everybody costs it nothing.
 *
 * ## What counts as dead
 *
 * Two things, and the first is the ordinary one. An entry carries the moment it stops being
 * offered, and every client already filters on it — `PlayerRequest.isExpired` — so collecting
 * on the same stamp removes exactly what players have already stopped seeing. Nothing here has
 * to reason about clocks: the entry and the recipient are judged by the same number.
 *
 * The second is the promise. A live entry whose sender no longer has a profile belongs to an
 * account that has been erased, and it holds that account's uid and the name it played under.
 * It is the only case worth spending a read on, it is the only case a forged expiry could hide
 * behind for ever, and it is capped at [MAX_INVITE_SENDER_CHECKS_PER_RUN] a lap.
 *
 * The walk is by recipient key with a stored cursor, exactly as [backfillBoardIndex] walks the
 * profiles. It converges: an empty parent does not exist in this database, so once a lap has
 * been round, `invites` holds only channels with something live in them — which is a page.
 */
async function collectDeadInvites(
  db: Rtdb,
  now: number,
  result: SweepResult
): Promise<void> {
  const cursor = (await db.get<string>(INVITE_CURSOR_PATH)) ?? "";
  const page = await db.get<Record<string, Record<string, Invite | null> | null>>(INVITES_PATH, {
    orderBy: '"$key"',
    limitToFirst: String(MAX_INVITE_RECIPIENTS_PER_RUN),
    // See [backfillBoardIndex]: no cursor is the start of the tree, and no key names that.
    ...(cursor ? { startAt: JSON.stringify(cursor) } : {}),
  });
  const rows = Object.entries(page ?? {});

  const updates: Record<string, unknown> = {};
  const live: Array<{ recipient: string; sender: string; expiresAt: number }> = [];
  for (const [recipient, channel] of rows) {
    if (!channel) continue;
    for (const [sender, invite] of Object.entries(channel)) {
      if (!invite) continue;
      // An entry with no usable stamp is one no client can decide about either, and the rules
      // have required a numeric one since the channel existed. Treated as long dead.
      if (typeof invite.expiresAt !== "number" || invite.expiresAt <= now) {
        updates[`${INVITES_PATH}/${recipient}/${sender}`] = null;
        result.invitesCollected += 1;
      } else {
        live.push({ recipient, sender, expiresAt: invite.expiresAt });
      }
    }
  }

  // Furthest from expiring first, so that a cap which bites falls on the entries that were
  // about to be collected on their stamp anyway. Without an order it would fall on the same
  // ones every lap: once the tree fits a page the cursor stops moving, and whoever sorted
  // eleventh would never be asked about at all.
  const senders = [
    ...new Set(
      [...live].sort((a, b) => b.expiresAt - a.expiresAt).map((entry) => entry.sender)
    ),
  ].slice(0, MAX_INVITE_SENDER_CHECKS_PER_RUN);
  // The name is asked for rather than the node, because it is the field the entry copied and
  // every profile the rules will accept carries one. Missing means there is no profile left.
  const erased = new Set(
    (
      await Promise.all(
        senders.map(async (uid) =>
          (await db.get<unknown>(`users/${uid}/username`)) === null ? uid : null
        )
      )
    ).filter((uid): uid is string => uid !== null)
  );
  for (const { recipient, sender } of live) {
    if (!erased.has(sender)) continue;
    updates[`${INVITES_PATH}/${recipient}/${sender}`] = null;
    result.invitesCollected += 1;
  }

  // See [backfillBoardIndex] for both halves of this: a short page is the end of the tree, and
  // the cursor is the highest key that came back rather than the last one a parser handed over.
  const exhausted = rows.length < MAX_INVITE_RECIPIENTS_PER_RUN;
  updates[INVITE_CURSOR_PATH] = exhausted
    ? ""
    : rows.reduce((highest, [recipient]) => (recipient > highest ? recipient : highest), "");
  await db.update(updates);
}

/**
 * Clears the weekly board of the rows nothing stands behind and of the weeks nothing reads.
 *
 * Every row on that board is a copy of somebody's username — see [weeklyUpdates] for why it
 * is denormalised — and `leaderboards` is writable by no client at all. So an account being
 * deleted takes its profile, its name reservation and its friendships with it and leaves its
 * name standing on a table every signed-in player can read, with nothing anywhere able to
 * take it down. That is the same shape as the request channel, and it has the same answer:
 * the hand holding the admin credential is the only one that can keep the promise "delete
 * permanently" makes.
 *
 * Two things go, for two different reasons. A week the app has stopped asking for goes whole:
 * the client only ever reads the week it is in, so everything before last week is a public
 * copy of a name being kept for nobody. Last week is spared because a handset whose clock
 * lags an hour over a Sunday midnight is still asking for it. Inside the week that is live, a
 * row whose account no longer has a profile goes on its own — walked by key with a stored
 * cursor, exactly as [backfillBoardIndex] walks the profiles.
 *
 * Both halves are capped per lap, and low: this shares its minute with [collectDeadInvites],
 * which is already the most expensive job on it.
 */
async function pruneWeeklyBoards(
  db: Rtdb,
  now: number,
  result: SweepResult
): Promise<void> {
  // Keys only: the weeks are asked for, not the tables under them, and a board with a
  // thousand rows on it would otherwise arrive whole just to be counted.
  const weeks = await db.get<Record<string, unknown>>(WEEKLY_BOARD_PATH, { shallow: "true" });
  if (!weeks) return;
  const live = weekKey(now);
  const previous = weekKey(now - WEEK_MILLIS);

  const updates: Record<string, unknown> = {};
  // Oldest first, so a cap that bites leaves the newest — the one closest to still being read.
  const finished = Object.keys(weeks)
    .filter((week) => week !== live && week !== previous)
    .sort()
    .slice(0, MAX_FINISHED_WEEKS_PER_RUN);
  for (const week of finished) {
    const rows = await db.get<Record<string, unknown>>(`${WEEKLY_BOARD_PATH}/${week}`, {
      shallow: "true",
    });
    updates[`${WEEKLY_BOARD_PATH}/${week}`] = null;
    result.boardRowsRemoved += Object.keys(rows ?? {}).length;
  }

  const cursor = (await db.get<string>(WEEKLY_CURSOR_PATH)) ?? "";
  const page = await db.get<Record<string, unknown>>(`${WEEKLY_BOARD_PATH}/${live}`, {
    orderBy: '"$key"',
    limitToFirst: String(MAX_BOARD_ROW_CHECKS_PER_RUN),
    // See [backfillBoardIndex]: no cursor is the start of the tree, and no key names that.
    ...(cursor ? { startAt: JSON.stringify(cursor) } : {}),
  });
  const listed = Object.keys(page ?? {});
  // The name is asked for rather than the node, for the same reason [collectDeadInvites] asks
  // for it: every profile the rules will accept carries one, so missing means none is left.
  const erased = await Promise.all(
    listed.map(async (uid) =>
      (await db.get<unknown>(`users/${uid}/username`)) === null ? uid : null
    )
  );
  for (const uid of erased) {
    if (uid === null) continue;
    updates[`${WEEKLY_BOARD_PATH}/${live}/${uid}`] = null;
    result.boardRowsRemoved += 1;
  }

  // See [backfillBoardIndex] for both halves of this: a short page is the end of the tree, and
  // the cursor is the highest key that came back rather than the last one a parser handed over.
  const exhausted = listed.length < MAX_BOARD_ROW_CHECKS_PER_RUN;
  updates[WEEKLY_CURSOR_PATH] = exhausted
    ? ""
    : listed.reduce((highest, uid) => (uid > highest ? uid : highest), "");
  await db.update(updates);
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

/**
 * Whether a public board may carry a row for the profile `value` describes.
 *
 * Two profiles are refused. An anonymous one, always: a table of the best players is meant to
 * list players, and an account that lasts until the handset is wiped is not one of them. And
 * one still wearing the name the app handed out — because a name nobody chose is a name nobody
 * meant to publish, and that is true however the account arrived at it. Linking a credential
 * makes an account real several seconds before its owner finishes the form that names it, and
 * those seconds used to be spent on the all-time board as `guest_######`.
 *
 * An account already carrying the board's sort key keeps it either way. Taking somebody off a
 * table they are on is a worse answer than a name that lags a rating by one match, and nothing
 * here is the right hand to be making that decision with: the phone is where a name is chosen.
 */
function holdsBoardPlace(value: Record<string, unknown>): boolean {
  if (value.accountType === GUEST_ACCOUNT_TYPE) return false;
  if (typeof value.leaderboardRating === "number") return true;
  const username = typeof value.username === "string" ? value.username.trim().toLowerCase() : "";
  return username !== "" && !GENERATED_NAME.test(username);
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
    // that a profile with no place on the board — see [holdsBoardPlace] — has no place in the
    // index to be read out of. It is written here, level with the rating, because the two are
    // shown as one row.
    ...(record.onBoards ? { [`users/${uid}/leaderboardRating`]: rating } : {}),
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
 * A profile with no place on a board — see [holdsBoardPlace] — gets no row. Nothing under
 * `leaderboards` is writable by a client, so this is the only hand that ever writes there and
 * refusing here is refusing outright: there is no row to be filtered out of a query, cached by
 * a phone, or found by a modified one. The name this table would carry is the very thing that
 * disqualifies half of them, which is the sharpest form the argument takes.
 */
function weeklyUpdates(
  week: string,
  uid: string,
  record: PlayerRecord,
  weekly: WeeklyRecord,
  rating: number,
  score: MatchScore
): Record<string, unknown> {
  if (!record.onBoards) return {};
  const base = `leaderboards/weekly/${week}/${uid}`;
  return {
    [`${base}/username`]: record.username,
    [`${base}/avatarId`]: record.avatarId,
    [`${base}/rating`]: rating,
    [`${base}/wins`]: weekly.wins + (score === SCORE_WIN ? 1 : 0),
    [`${base}/totalGames`]: weekly.totalGames + 1,
  };
}

async function readRecord(db: Rtdb, uid: string): Promise<PlayerRecord> {
  const value = (await db.get<Record<string, unknown>>(`users/${uid}`)) ?? {};
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
    onBoards: holdsBoardPlace(value),
  };
}

/** The one field a casual match needs off a profile. Empty when the account is already gone. */
async function readUsername(db: Rtdb, uid: string): Promise<string> {
  const value = await db.get<unknown>(`users/${uid}/username`);
  return typeof value === "string" ? value : "";
}

async function readWeekly(db: Rtdb, week: string, uid: string): Promise<WeeklyRecord> {
  const value =
    (await db.get<Record<string, unknown>>(`leaderboards/weekly/${week}/${uid}`)) ?? {};
  return {
    wins: numberOr(value.wins, 0),
    totalGames: numberOr(value.totalGames, 0),
  };
}

function numberOr(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}
