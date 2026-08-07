import assert from "node:assert/strict";
import { restDatabase } from "./db.js";
import { MatchReport, QueueEntry, closestPairs, sweep, verifyReport } from "./sweep.js";

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

  const first = await sweep(db, now);
  console.log("first run :", JSON.stringify(first));

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
  // but neither board may take them. Seeded after the runs above because a run rates only
  // four reports and the choreography of the first one is worth leaving alone.
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
