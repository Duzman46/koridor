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
 * Four jobs: rate the matches players have reported, clear away rooms nobody played out, pair
 * whoever the phones left waiting in the matchmaking list, and keep the all-time board's index
 * complete. The first two used to be Cloud Functions — one triggered by a database write, one
 * by a schedule — and both are now polled instead, because a worker cannot subscribe to
 * database events. Polling is the lesser evil here: it needs no endpoint open to the internet,
 * and a report that arrives while a run is already going is simply picked up by the next one.
 */

/**
 * How many reports one run will take on.
 *
 * The ceiling is not the work, it is subrequests: the free plan allows fifty outbound
 * requests per invocation and a single report costs about eight. Four leaves room for the
 * query and the room sweep with margin to spare, and at one run a minute that is far more
 * throughput than this game will ever produce.
 */
const MAX_REPORTS_PER_RUN = 4;

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

const DEFAULT_AVATAR = "avatar_01";

/**
 * The value `AccountType.GUEST` writes into a profile: an anonymous Firebase user, an account
 * that lives on one handset until it is uninstalled.
 *
 * Such a player is rated, because a match is rated without asking who played it, but they are
 * kept off both leaderboards — see [weeklyUpdates] and [playerUpdates], and [backfillBoardIndex]
 * for the profiles that were already here before either of those wrote anything. A guest's match
 * is meant to be casual and so never reaches [applyRating] at all; the check is here because the
 * flag that makes it casual is set by a phone, and the boards are this worker's to protect.
 */
const GUEST_ACCOUNT_TYPE = "GUEST";
const EXPIRED_ROOM_GRACE_MILLIS = 60 * 60 * 1000;
const ROOM_EXPIRY_MILLIS = 24 * 60 * 60 * 1000;
const DEFAULT_TURN_SECONDS = 60;

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
  /** An anonymous account. Earns a rating like anyone else, but holds no place on a board. */
  isGuest: boolean;
}

interface WeeklyRecord {
  wins: number;
  totalGames: number;
}

/** One player waiting to be paired, as matchmaking/{uid} stores them. */
export interface QueueEntry {
  rating: number;
  ranked: boolean;
  queuedAt: number;
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
  };
  await rateReportedMatches(db, now, result);
  await expireRooms(db, now, result);
  await pairWaitingPlayers(db, now, result);
  // Last, and only on a run that rated nothing. Rating a match costs about eight of the fifty
  // subrequests a run gets, so a full four of them leaves no room for anything else — and of
  // everything here this is the one job with nobody waiting on it.
  if (result.rated === 0) await backfillBoardIndex(db, result);
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
        result.unranked += 1;
        continue;
      }
      await applyRating(db, report, now);
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
  // The room's own write rules already proved the outcome is legitimate: a normal win is
  // checked against the final board, a timeout against the server clock, and a resignation
  // can only ever be filed against oneself. Re-derive nothing; just insist they agree.
  if ((room.winnerUserId ?? "") !== report.winnerUid) return "winner does not match the room";
  if ((room.endReason ?? "") !== report.endReason) return "end reason does not match the room";
  if (report.endReason === "NORMAL") {
    // The board only ever names a seat, and the host is not always seat one: choosing red
    // puts them in seat two. A room written before seats existed carries no hostSeat, which
    // means the host opened — the only arrangement that protocol had.
    const hostInSeatTwo = room.hostSeat === "PLAYER_TWO";
    const seatOneUid = hostInSeatTwo ? report.guestUid : report.hostUid;
    const seatTwoUid = hostInSeatTwo ? report.hostUid : report.guestUid;
    const board = room.board as { status?: string } | undefined;
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

async function applyRating(db: Rtdb, report: MatchReport, now: number): Promise<void> {
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
  });
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

  for (const [[hostUid, host], [guestUid, guest]] of closestPairs(
    waiting,
    MAX_QUEUE_PAIRS_PER_RUN
  )) {
    const code = await freeRoomCode(db, guestUid, guest.queuedAt);
    // No code left after several tries means a room already sits where this pair would go,
    // which is a phone that paired them a moment ago. Leaving them is the right answer: the
    // entries they no longer need are cleared by their own clients or by staleness.
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
 * A room code nothing occupies, or null if the first few are all taken.
 *
 * The first candidate is deliberately the one a phone would have used for this player, so a
 * pairing the client had already half made is found rather than written over. The salt only
 * moves if that code is busy, which in practice means it is busy with exactly that pairing.
 */
async function freeRoomCode(
  db: Rtdb,
  guestUid: string,
  queuedAt: number
): Promise<string | null> {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const code = await roomCode(`koridor:queue:${guestUid}:${queuedAt}${"+".repeat(attempt)}`);
    if ((await db.get(`rooms/${code}`)) === null) return code;
  }
  return null;
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
    if (value.accountType === GUEST_ACCOUNT_TYPE) continue;
    if (typeof value.leaderboardRating === "number") continue;
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
 * A guest gets no row. Nothing under `leaderboards` is writable by a client, so this is the
 * only hand that ever writes there and refusing here is refusing outright: there is no row to
 * be filtered out of a query, cached by a phone, or found by a modified one.
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
    isGuest: value.accountType === GUEST_ACCOUNT_TYPE,
  };
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
