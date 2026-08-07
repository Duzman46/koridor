import assert from "node:assert/strict";
import { restDatabase } from "./db.js";
import {
  MAX_BACKFILL_PROFILES_PER_RUN,
  MAX_REPORTS_PER_RUN,
  MatchReport,
  QueueEntry,
  SweepResult,
  closestPairs,
  sweep,
  verifyReport,
} from "./sweep.js";

/**
 * End-to-end check of the sweep, run against the database emulator with `npm run test:e2e`.
 *
 * The Elo unit tests cover the arithmetic. This covers everything around it — the indexed
 * query, the compare-and-set claim, the multi-path update and the room expiry — none of
 * which can be checked by reasoning about the code, and all of which fail silently in
 * production if they are wrong: a sweep that finds nothing looks exactly like a sweep with
 * nothing to do.
 *
 * The emulator accepts the literal bearer token `owner` for admin access, which stands in
 * for the service-account token without any key being involved.
 */
/**
 * The emulator attaches firebase.json's rules to the project's *default* namespace, and
 * silently serves any other namespace wide open with no indexes at all. Naming the wrong one
 * turns this into a test of nothing, so it is spelled out.
 */
const NAMESPACE = "demo-koridor-default-rtdb";
const HOST = "alice-uid";
const GUEST = "bob-uid";

// Four players for the matchmaking backstop, kept away from the two above so that rating a
// match and pairing a queue cannot quietly depend on each other.
const NEAR_ONE = "carol-uid";
const NEAR_TWO = "dave-uid";
const DISTANT = "erin-uid";
const GHOST = "frank-uid";

/** An anonymous player: rated like anyone else, and on neither leaderboard. */
const ANON = "grace-uid";

/** A linked account from before the all-time board had a sort key of its own. */
const LEGACY = "heidi-uid";

/** Two players who go on to play more matches than a history is allowed to keep. */
const REGULAR_ONE = "ivan-uid";
const REGULAR_TWO = "judy-uid";

const db = restDatabase("http://127.0.0.1:9000", async () => "owner", { ns: NAMESPACE });

/** The account type is load-bearing here, so these are the values a real profile carries. */
function profile(username: string, accountType: "GOOGLE" | "GUEST" = "GOOGLE") {
  return {
    username,
    normalizedUsername: username.toLowerCase(),
    avatarId: "avatar_01",
    accountType,
    createdAt: 1,
    rating: 1000,
    highestRating: 1000,
    totalGames: 0,
    wins: 0,
    losses: 0,
    draws: 0,
    currentWinStreak: 0,
    bestWinStreak: 0,
    accountStatus: "ACTIVE",
  };
}

function finishedRoom(overrides: Record<string, unknown> = {}) {
  return {
    hostUserId: HOST,
    guestUserId: GUEST,
    status: "FINISHED",
    currentTurnUserId: "",
    version: 12,
    winnerUserId: HOST,
    endReason: "NORMAL",
    createdAt: 1,
    lastMoveAt: 2,
    expiresAt: 4_000_000_000_000,
    ranked: true,
    visibility: "PUBLIC",
    turnDurationSeconds: 60,
    board: {
      currentPlayer: "PLAYER_ONE",
      status: "PLAYER_ONE_WON",
      turnNumber: 21,
      players: {
        PLAYER_ONE: { row: 0, column: 4, wallsRemaining: 6 },
        PLAYER_TWO: { row: 5, column: 4, wallsRemaining: 7 },
      },
    },
    ...overrides,
  };
}

/**
 * A room whose host chose red and so played seat two. The board still says which *seat* won,
 * and reading that as "seat one is the host" would pay every such match to the wrong player.
 */
function redHostRoom(winner: "PLAYER_ONE_WON" | "PLAYER_TWO_WON") {
  return finishedRoom({
    hostSeat: "PLAYER_TWO",
    winnerUserId: winner === "PLAYER_TWO_WON" ? HOST : GUEST,
    board: {
      currentPlayer: "PLAYER_TWO",
      status: winner,
      turnNumber: 21,
      players: {
        PLAYER_ONE: { row: winner === "PLAYER_ONE_WON" ? 0 : 5, column: 4, wallsRemaining: 6 },
        PLAYER_TWO: { row: winner === "PLAYER_TWO_WON" ? 8 : 5, column: 4, wallsRemaining: 7 },
      },
    },
  });
}

function report(overrides: Partial<MatchReport> & { roomCode: string }): MatchReport {
  return {
    hostUid: HOST,
    guestUid: GUEST,
    winnerUid: HOST,
    endReason: "NORMAL",
    ranked: true,
    turnCount: 21,
    reportedAt: Date.UTC(2026, 7, 5),
    reportedBy: HOST,
    state: "PENDING",
    ...overrides,
  };
}

function queued(rating: number, queuedAt: number): QueueEntry {
  return { rating, ranked: true, queuedAt };
}

/** One player's history, exactly as a profile page reads it back. */
type History = Record<
  string,
  { opponentName: string; result: string; playedAt: number; ratingChange?: number }
>;

async function historyOf(uid: string): Promise<History> {
  return (await db.get<History>(`recentMatches/${uid}`)) ?? {};
}

/**
 * The sweep, run as many times as the reports waiting need.
 *
 * One run takes at most [MAX_REPORTS_PER_RUN] of them, so what the sweep made of a batch
 * larger than that is the sum over the runs it takes. Nothing else is counted twice: the
 * rooms, the waiting list and the board index are all dealt with by the first run and found
 * empty by the next, which is a claim worth having in the totals below.
 */
async function sweepReports(now: number, reports: number): Promise<SweepResult> {
  const total = await sweep(db, now);
  for (let taken = MAX_REPORTS_PER_RUN; taken < reports; taken += MAX_REPORTS_PER_RUN) {
    const run = await sweep(db, now);
    for (const key of Object.keys(total) as Array<keyof SweepResult>) total[key] += run[key];
  }
  return total;
}

const checks: Array<[string, boolean]> = [];
function check(label: string, passed: boolean) {
  checks.push([label, passed]);
  console.log(`${passed ? "  ok  " : "  FAIL"}  ${label}`);
}

async function main(): Promise<void> {
  const now = Date.now();

  await db.set("", {
    users: { [HOST]: profile("alice"), [GUEST]: profile("bob") },
    rooms: {
      // Rated normally.
      GOOD01: finishedRoom(),
      // Reported as a win the board does not show: must be refused.
      LIE001: finishedRoom({ winnerUserId: GUEST }),
      // Played but unranked: recorded, never rated.
      CASU01: finishedRoom({ ranked: false }),
      // Never played, past its window: deleted outright.
      DEAD01: { ...finishedRoom(), status: "WAITING", expiresAt: now - 1 },
      // A match that ran past its window: closed, not deleted.
      STAL01: { ...finishedRoom(), status: "IN_PROGRESS", expiresAt: now - 1 },
      // The host played red and won from seat two: rated like any other match.
      RED001: redHostRoom("PLAYER_TWO_WON"),
      // The same room the other way up. No report is filed for it; it is here so the seat
      // mapping can be asked directly, which is the only way to see it read seat one.
      RED002: redHostRoom("PLAYER_ONE_WON"),
    },
    roomSecrets: { DEAD01: { passwordHash: "x" } },
    matchResults: {
      "GOOD01-1": report({ roomCode: "GOOD01" }),
      "LIE001-1": report({ roomCode: "LIE001" }),
      "CASU01-1": report({ roomCode: "CASU01", ranked: false }),
      "RED001-1": report({ roomCode: "RED001" }),
    },
    matchmaking: {
      // Ten points apart, so they are each other's nearest and must be the pair made.
      [NEAR_ONE]: queued(1000, now - 30_000),
      [NEAR_TWO]: queued(1010, now - 20_000),
      // Four hundred points away: paired with neither, and left waiting rather than shoved
      // into a match with whoever happened to be left.
      [DISTANT]: queued(1400, now - 10_000),
      // Written by a phone that never came back. Nothing may be paired against it.
      [GHOST]: queued(1005, now - 10 * 60 * 1000),
    },
  });

  // The four reports seeded above, however many runs a run's report limit makes that.
  const first = await sweepReports(now, 4);
  console.log("first runs:", JSON.stringify(first));

  const winner = await db.get<Record<string, number>>(`users/${HOST}`);
  const loser = await db.get<Record<string, number>>(`users/${GUEST}`);
  const weekly = await db.get<Record<string, Record<string, Record<string, number>>>>(
    "leaderboards/weekly"
  );
  const week = weekly ? Object.keys(weekly)[0] : null;

  check("the honest matches were rated", first.rated === 2);
  check("the false report was refused", first.rejected === 1);
  check("the unranked match was recorded but not rated", first.unranked === 1);
  check("the winner's rating rose", (winner?.rating ?? 0) > 1000);
  check("the loser's rating fell", (loser?.rating ?? 0) < 1000);
  check(
    "rating is zero-sum",
    (winner?.rating ?? 0) - 1000 === 1000 - (loser?.rating ?? 0)
  );
  check("the wins were recorded", winner?.wins === 2 && winner?.currentWinStreak === 2);
  check("the losses were recorded", loser?.losses === 2);
  check("the weekly key is the ISO week of the report", week === "2026-W32");
  check("the weekly board carries the winner", (week && weekly?.[week]?.[HOST]?.wins) === 2);
  check(
    "both players are in the index the all-time board is ordered by",
    winner?.leaderboardRating === winner?.rating &&
      loser?.leaderboardRating === loser?.rating
  );

  // The two halves of the seat mapping. Read the board as "seat one is the host" — which is
  // what it meant before colour picked the seat — and each of these names the other player.
  check(
    "a win from seat two was paid to the host sitting in it",
    (await db.get<string>("matchResults/RED001-1/state")) === "RATED"
  );
  check(
    "and seat one in such a room belongs to the guest",
    (await verifyReport(db, report({ roomCode: "RED002", winnerUid: GUEST }))) === "ok"
  );
  check(
    "the weekly board is not padded with the refused or unranked match",
    week !== null && Object.keys(weekly?.[week] ?? {}).length === 2
  );

  // The history a profile page shows. Every part of it fails quietly if it is wrong: a list
  // written for one side only looks like an opponent who never plays, and a rating column
  // filled in for a casual game looks like a rated one worth nothing.
  const hostGames = await historyOf(HOST);
  const guestGames = await historyOf(GUEST);

  check(
    "a rated match is written into both players' histories",
    "GOOD01-1" in hostGames && "GOOD01-1" in guestGames
  );
  check(
    "each side sees the other's name and its own result",
    hostGames["GOOD01-1"]?.opponentName === "bob" &&
      hostGames["GOOD01-1"]?.result === "WIN" &&
      guestGames["GOOD01-1"]?.opponentName === "alice" &&
      guestGames["GOOD01-1"]?.result === "LOSS"
  );
  check(
    "and the rating each of them moved, adding up to the rating they now hold",
    Object.values(hostGames).reduce((sum, game) => sum + (game.ratingChange ?? 0), 0) ===
      (winner?.rating ?? 0) - 1000
  );
  check(
    "the unranked match is listed too, because it was still played",
    hostGames["CASU01-1"]?.result === "WIN"
  );
  check(
    "with no rating change at all rather than a change of zero",
    "CASU01-1" in hostGames && !("ratingChange" in hostGames["CASU01-1"])
  );
  check("the refused report reached neither history", !("LIE001-1" in hostGames));

  check("the abandoned room was deleted", (await db.get("rooms/DEAD01")) === null);
  check("its password secret went with it", (await db.get("roomSecrets/DEAD01")) === null);
  check(
    "the overrunning match was closed, not deleted",
    (await db.get<string>("rooms/STAL01/status")) === "EXPIRED"
  );
  check("a live room was left alone", (await db.get("rooms/GOOD01")) !== null);

  // The matchmaking backstop. The phones pair each other and normally get there first; this
  // is the run that catches whoever they could not, and clears out the names nothing is
  // behind. Both halves fail silently in production: an unpaired player just keeps waiting.
  const matched = await db.get<Record<string, Record<string, unknown>>>("rooms", {
    orderBy: '"guestUserId"',
    equalTo: `"${NEAR_TWO}"`,
  });
  const room = matched ? Object.values(matched)[0] : undefined;
  const seatOne = room?.hostSeat === "PLAYER_ONE" ? NEAR_ONE : NEAR_TWO;

  check("the two closest-rated waiters were paired", first.queuePaired === 1);
  check("the stale entry was cleared", (await db.get(`matchmaking/${GHOST}`)) === null);
  check("and counted as dropped, not paired", first.queueDropped === 1);
  check(
    "both of the paired players left the list",
    (await db.get(`matchmaking/${NEAR_ONE}`)) === null &&
      (await db.get(`matchmaking/${NEAR_TWO}`)) === null
  );
  check(
    "the distant rating was left waiting rather than paired with a leftover",
    (await db.get(`matchmaking/${DISTANT}`)) !== null
  );
  check("the room holds both of them, already playing", room?.hostUserId === NEAR_ONE &&
    room?.status === "IN_PROGRESS" && room?.version === 0);
  check("blue opens it, whichever of them drew blue", room?.currentTurnUserId === seatOne);
  check(
    "it is unlisted, unprotected and server-stamped",
    room?.visibility === "PRIVATE" &&
      room?.requiresPassword === false &&
      typeof room?.createdAt === "number"
  );
  check(
    "closest ratings are paired before distant ones",
    JSON.stringify(
      closestPairs(
        [
          ["a", queued(1000, 1)],
          ["b", queued(1400, 2)],
          ["c", queued(1010, 3)],
          ["d", queued(1390, 4)],
        ],
        4
      ).map(([one, two]) => [one[0], two[0]])
    ) === JSON.stringify([["a", "c"], ["d", "b"]])
  );

  // The claim is the guard against a match being rated twice. Running again must be inert.
  const ratingAfterFirst = winner?.rating;
  const second = await sweep(db, now);
  console.log("second run:", JSON.stringify(second));
  check("a second run finds nothing left to rate", second.rated === 0);
  check(
    "and does not move the rating again",
    (await db.get<Record<string, number>>(`users/${HOST}`))?.rating === ratingAfterFirst
  );
  check("nor anybody left to pair", second.queuePaired === 0);
  check(
    "and the one player still waiting is still waiting",
    (await db.get(`matchmaking/${DISTANT}`)) !== null
  );

  // A guest's match is supposed to be casual, and a phone marks it so. This one arrives
  // marked ranked anyway, which is what a modified client looks like — the rating may move,
  // but neither board may take them. Seeded after the runs above because a run takes only a
  // few reports and the choreography of the first ones is worth leaving alone.
  await db.update({
    [`users/${ANON}`]: profile("grace", "GUEST"),
    "rooms/ANON01": finishedRoom({
      hostUserId: ANON,
      guestUserId: HOST,
      winnerUserId: ANON,
    }),
    "matchResults/ANON01-1": report({
      roomCode: "ANON01",
      hostUid: ANON,
      guestUid: HOST,
      winnerUid: ANON,
      reportedBy: ANON,
    }),
  });
  const third = await sweep(db, now);
  console.log("third run :", JSON.stringify(third));
  const anon = await db.get<Record<string, number>>(`users/${ANON}`);

  check("the guest's match was rated", third.rated === 1);
  check("their rating moved with it", (anon?.rating ?? 0) > 1000);
  check(
    "but nothing put them in the all-time index",
    anon?.leaderboardRating === undefined
  );
  check(
    "and no weekly row was written for them",
    (await db.get(`leaderboards/weekly/${week}/${ANON}`)) === null
  );
  check(
    "while the opponent who is not a guest kept both",
    (await db.get<number>(`users/${HOST}/leaderboardRating`)) ===
      (await db.get<number>(`users/${HOST}/rating`)) &&
      (await db.get(`leaderboards/weekly/${week}/${HOST}`)) !== null
  );

  // The accounts that were already there the day the all-time board grew a sort key of its
  // own. Nothing about them ever writes it: their owners are signed in on a phone that will
  // not be signing in again, and they are not the two ends of a rated match. Ordered by a key
  // they do not carry, they are not last on the board — they are missing from it, silently,
  // which is a board with nobody on it and no error to explain why.
  await db.set(`users/${LEGACY}`, profile("heidi"));
  const fourth = await sweep(db, now);
  console.log("fourth run:", JSON.stringify(fourth));

  check(
    "the run with no match to rate backfills the board index instead",
    fourth.rated === 0 && fourth.boardIndexed === 1
  );
  check(
    "and takes the account on at the rating it already held",
    (await db.get<number>(`users/${LEGACY}/leaderboardRating`)) === 1000
  );
  check(
    "the guest is still left out of it",
    (await db.get(`users/${ANON}/leaderboardRating`)) === null
  );

  // The query the all-time board actually makes. This is the one that was returning nothing.
  const board = await db.get<Record<string, unknown>>("users", {
    orderBy: '"leaderboardRating"',
    startAt: "0",
    limitToLast: "51",
  });
  check(
    "the board query returns the accounts it used to skip, and still not the guest",
    board !== null && LEGACY in board && HOST in board && GUEST in board && !(ANON in board)
  );
  check(
    "the walk wraps round at the end rather than wedging on the last profile",
    (await db.get<string>("maintenance/boardIndexBackfill/cursor")) === ""
  );

  const fifth = await sweep(db, now);
  check("a later run finds nobody left to index", fifth.boardIndexed === 0);

  // A pair who keep playing. The cap is the only thing standing between a profile and a list
  // that grows for as long as the account exists, and it is invisible until it is missing:
  // eleven matches in a ten-match history look exactly like ten until somebody counts.
  const codes = Array.from({ length: 11 }, (_, index) => `RUN${String(index).padStart(3, "0")}`);
  const marathon: Record<string, unknown> = {
    [`users/${REGULAR_ONE}`]: profile("ivan"),
    [`users/${REGULAR_TWO}`]: profile("judy"),
  };
  for (const [index, code] of codes.entries()) {
    marathon[`rooms/${code}`] = finishedRoom({
      hostUserId: REGULAR_ONE,
      guestUserId: REGULAR_TWO,
      winnerUserId: REGULAR_ONE,
    });
    marathon[`matchResults/${code}-1`] = report({
      roomCode: code,
      hostUid: REGULAR_ONE,
      guestUid: REGULAR_TWO,
      winnerUid: REGULAR_ONE,
      reportedBy: REGULAR_ONE,
      // A minute apart, so which of them is the oldest is a fact and not a coin toss.
      reportedAt: Date.UTC(2026, 7, 5) + index * 60_000,
    });
  }
  await db.update(marathon);

  const marathonRuns = await sweepReports(now, codes.length);
  console.log("marathon  :", JSON.stringify(marathonRuns));
  const ivanGames = await historyOf(REGULAR_ONE);
  const judyGames = await historyOf(REGULAR_TWO);

  check("all eleven matches were rated", marathonRuns.rated === codes.length);
  check(
    "but a history stops at ten, on both sides of them",
    Object.keys(ivanGames).length === 10 && Object.keys(judyGames).length === 10
  );
  check(
    "and it is the oldest that fell off, not the newest",
    !(`${codes[0]}-1` in ivanGames) && `${codes[codes.length - 1]}-1` in ivanGames
  );

  // More profiles than one lap can carry. Every account above fitted in a single page, so the
  // cursor was only ever written back as "" and the branch that actually moves it never ran —
  // and that branch is the only one a real database ever takes. What it can get wrong is
  // silent: a cursor that does not advance re-reads the same page for ever, indexes nothing
  // after the first lap and reports a clean run every minute while the board stays half empty.
  const crowd = Array.from(
    { length: MAX_BACKFILL_PROFILES_PER_RUN + 5 },
    (_, index) => `legacy-${String(index).padStart(3, "0")}-uid`
  );
  for (const uid of crowd) {
    await db.set(`users/${uid}`, profile(uid.replace(/-/g, "")));
  }

  let indexed = 0;
  const trail: string[] = [];
  // A fixed number of laps rather than "until it finishes": a walk that stops advancing would
  // otherwise hang this script rather than fail it, and where it got to is the thing under
  // test. Four is one more than a tree this size needs, so the last one wraps.
  for (let lap = 0; lap < 4; lap += 1) {
    indexed += (await sweep(db, now)).boardIndexed;
    trail.push((await db.get<string>("maintenance/boardIndexBackfill/cursor")) ?? "");
  }
  console.log(`crowd walk: ${indexed} indexed, cursors ${JSON.stringify(trail)}`);

  check("a walk longer than one page takes every profile on it", indexed === crowd.length);
  check(
    "and never sat on the same cursor twice, which is what a wedged walk looks like",
    trail.every((at, index) => index === 0 || at !== trail[index - 1])
  );
  check("and reached the end of the tree, which is where it wraps", trail.includes(""));
  check(
    "the last profile in key order was not walked past",
    (await db.get<number>(`users/${crowd[crowd.length - 1]}/leaderboardRating`)) === 1000
  );

  const failed = checks.filter(([, passed]) => !passed);
  console.log(`\n${checks.length - failed.length}/${checks.length} passed`);
  assert.equal(failed.length, 0, `${failed.length} check(s) failed`);
  console.log("OK: the worker does everything the Cloud Functions did.");
}

main().then(
  () => process.exit(0),
  (error) => {
    console.error(error);
    process.exit(1);
  }
);
