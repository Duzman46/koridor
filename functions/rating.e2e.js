/**
 * End-to-end check of the rating pipeline. Run with `npm run test:e2e`.
 *
 * Seeds a finished ranked room, files the match report the app would file, and waits for
 * rateReportedMatch to move both ratings, both records and the weekly board.
 *
 * The unit tests cover the Elo arithmetic; this covers the part that actually broke. The
 * claiming transaction aborted on its first delivery, so every report was logged as
 * "already processed" and no rating ever moved — and nothing failed, anywhere, to say so.
 * It runs against the emulator under a demo project id, never a real one.
 */
const { initializeApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");

const PROJECT = "demo-koridor";
const HOST = "alice-uid";
const GUEST = "bob-uid";
const CODE = "RATE01";
const MATCH = `${CODE}-1`;

initializeApp({ projectId: PROJECT, databaseURL: `http://127.0.0.1:9000/?ns=${PROJECT}` });
const db = getDatabase();

function profile(username) {
  return {
    username,
    normalizedUsername: username.toLowerCase(),
    avatarId: "avatar_01",
    accountType: "REGISTERED",
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

async function waitFor(check, label, timeoutMs = 25_000) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const value = await check();
    if (value !== null && value !== undefined) return value;
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${label}`);
    await new Promise((resolve) => setTimeout(resolve, 400));
  }
}

async function main() {
  await db.ref().set(null);
  await db.ref(`users/${HOST}`).set(profile("alice"));
  await db.ref(`users/${GUEST}`).set(profile("bob"));
  await db.ref(`rooms/${CODE}`).set({
    hostUserId: HOST,
    guestUserId: GUEST,
    status: "FINISHED",
    currentTurnUserId: "",
    version: 12,
    winnerUserId: HOST,
    endReason: "NORMAL",
    createdAt: 1,
    lastMoveAt: 2,
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
  });

  await db.ref(`matchResults/${MATCH}`).set({
    matchId: MATCH,
    roomCode: CODE,
    hostUid: HOST,
    guestUid: GUEST,
    winnerUid: HOST,
    endReason: "NORMAL",
    ranked: true,
    turnCount: 21,
    reportedAt: Date.now(),
    reportedBy: HOST,
    state: "PENDING",
  });

  // The state flips to RATED when the report is *claimed*, which is before any rating has
  // been written — waiting on it alone reads the players too early.
  const state = await waitFor(
    async () => {
      const value = (await db.ref(`matchResults/${MATCH}/state`).get()).val();
      if (value === "REJECTED") return value;
      const rating = (await db.ref(`users/${HOST}/rating`).get()).val();
      return rating !== 1000 ? value : null;
    },
    "the rating to be applied"
  );
  console.log("report state       :", state);

  const winner = (await db.ref(`users/${HOST}`).get()).val();
  const loser = (await db.ref(`users/${GUEST}`).get()).val();
  const week = (await db.ref("leaderboards/weekly").get()).val();
  const weekKey = week ? Object.keys(week)[0] : null;

  console.log("winner rating      :", winner.rating, "(was 1000)");
  console.log("loser rating       :", loser.rating, "(was 1000)");
  console.log("winner wins/streak :", winner.wins, "/", winner.currentWinStreak);
  console.log("loser losses       :", loser.losses);
  console.log("weekly board key   :", weekKey);
  console.log("weekly winner row  :", weekKey ? JSON.stringify(week[weekKey][HOST]) : "-");

  const problems = [];
  if (state !== "RATED") problems.push(`state is ${state}, expected RATED`);
  if (!(winner.rating > 1000)) problems.push("winner's rating did not rise");
  if (!(loser.rating < 1000)) problems.push("loser's rating did not fall");
  if (winner.rating - 1000 !== 1000 - loser.rating) problems.push("rating is not zero-sum");
  if (winner.wins !== 1 || winner.currentWinStreak !== 1) problems.push("win not recorded");
  if (loser.losses !== 1) problems.push("loss not recorded");
  if (!weekKey || !week[weekKey][HOST] || week[weekKey][HOST].wins !== 1) {
    problems.push("weekly leaderboard not updated");
  }

  if (problems.length) {
    console.error("\nFAILED:\n - " + problems.join("\n - "));
    process.exit(1);
  }
  console.log("\nOK: rating, records and the weekly board all moved server-side.");
}

main().then(
  () => process.exit(0),
  (error) => {
    console.error(error);
    process.exit(1);
  }
);
