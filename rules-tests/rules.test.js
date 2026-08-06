/**
 * Security rule tests for database.rules.json.
 *
 * These are the guard on the claim that a modified client cannot help itself to rating,
 * wins or entitlements. They run against the Realtime Database emulator — never a real
 * project — and are driven by `npm test` in this directory, which starts and stops the
 * emulator around them.
 *
 * Prerequisites: `npm install` here, plus the Firebase CLI (`npm i -g firebase-tools`) and
 * a JDK on PATH, which the database emulator needs.
 */
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const { after, before, beforeEach, describe, it } = require("node:test");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const ALICE = "alice-uid";
const BOB = "bob-uid";

/** A profile shaped the way the rules demand a fresh one must be. */
function newProfile(username, overrides = {}) {
  return {
    username,
    normalizedUsername: username.toLowerCase(),
    displayName: username,
    avatarId: "avatar_01",
    accountType: "GUEST",
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
    ...overrides,
  };
}

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "koridor-rules-test",
    database: {
      host: "127.0.0.1",
      port: 9000,
      rules: readFileSync(join(__dirname, "..", "database.rules.json"), "utf8"),
    },
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearDatabase();
});

/** Claims a username and writes the matching profile, the way the app's sign-up does. */
async function seedProfile(uid, username) {
  const db = testEnv.authenticatedContext(uid).database();
  await db.ref(`usernames/${username.toLowerCase()}`).set(uid);
  await db.ref(`users/${uid}`).set(newProfile(username));
}

describe("profiles", () => {
  it("lets a signed-in player create their own profile at the starting rating", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(db.ref(`usernames/alice`).set(ALICE));
    await assertSucceeds(db.ref(`users/${ALICE}`).set(newProfile("alice")));
  });

  it("refuses a profile that starts above the starting rating", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await db.ref(`usernames/alice`).set(ALICE);
    await assertFails(db.ref(`users/${ALICE}`).set(newProfile("alice", { rating: 2400 })));
  });

  it("refuses a profile written under someone else's uid", async () => {
    const db = testEnv.authenticatedContext(BOB).database();
    await db.ref(`usernames/alice`).set(BOB);
    await assertFails(db.ref(`users/${ALICE}`).set(newProfile("alice")));
  });

  it("refuses a username already taken by another player", async () => {
    await seedProfile(ALICE, "alice");
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref(`usernames/alice`).set(BOB));
  });

  it("refuses an unauthenticated read of the profile tree", async () => {
    await seedProfile(ALICE, "alice");
    const anonymous = testEnv.unauthenticatedContext().database();
    await assertFails(anonymous.ref(`users/${ALICE}`).get());
  });
});

describe("competitive fields are server-owned", () => {
  it("refuses a player raising their own rating", async () => {
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref(`users/${ALICE}/rating`).set(2400));
  });

  it("refuses a player awarding themselves wins", async () => {
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref(`users/${ALICE}/wins`).set(99));
  });

  it("refuses a player granting themselves an entitlement", async () => {
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref(`users/${ALICE}/purchasedEntitlements/REMOVE_ADS`).set(true));
  });

  it("still lets a player change their own display name and language", async () => {
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(db.ref(`users/${ALICE}/displayName`).set("Alice"));
    await assertSucceeds(db.ref(`users/${ALICE}/preferredLanguage`).set("tr"));
  });

  it("refuses another player editing your profile", async () => {
    await seedProfile(ALICE, "alice");
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref(`users/${ALICE}/displayName`).set("hacked"));
  });
});

describe("private data", () => {
  it("keeps the email address readable only by its owner", async () => {
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref(`usersPrivate/${ALICE}/email`).set("alice@example.com"));

    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref(`usersPrivate/${ALICE}/email`).get());
  });

  it("keeps room password hashes unreadable by everyone", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("roomSecrets/ABC123").get());
  });

  it("keeps the purchase token index unreadable and unwritable", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("purchaseTokens/anything").get());
    await assertFails(db.ref("purchaseTokens/anything").set({ uid: ALICE }));
  });
});

describe("purchases", () => {
  it("accepts a pending receipt from its owner", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      db.ref(`purchaseReceipts/${ALICE}/hash1`).set({
        purchaseToken: "token-1",
        productIds: ["remove_ads"],
        state: "PENDING",
        submittedAt: 1,
      }),
    );
  });

  it("refuses a receipt that claims to be already verified", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      db.ref(`purchaseReceipts/${ALICE}/hash1`).set({
        purchaseToken: "token-1",
        productIds: ["remove_ads"],
        state: "VERIFIED",
        submittedAt: 1,
      }),
    );
  });

  it("refuses overwriting a receipt that has already been filed", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    const receipt = {
      purchaseToken: "token-1",
      productIds: ["remove_ads"],
      state: "PENDING",
      submittedAt: 1,
    };
    await db.ref(`purchaseReceipts/${ALICE}/hash1`).set(receipt);
    await assertFails(db.ref(`purchaseReceipts/${ALICE}/hash1`).set(receipt));
  });

  it("refuses filing a receipt under another player", async () => {
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(
      bob.ref(`purchaseReceipts/${ALICE}/hash1`).set({
        purchaseToken: "token-1",
        productIds: ["remove_ads"],
        state: "PENDING",
        submittedAt: 1,
      }),
    );
  });
});

describe("friendships", () => {
  it("lets a player write their own side of a friendship", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      db.ref(`friendships/${ALICE}/${BOB}`).set({ status: "REQUEST_SENT", updatedAt: 1 }),
    );
  });

  it("refuses an unknown status value", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      db.ref(`friendships/${ALICE}/${BOB}`).set({ status: "ADMIN", updatedAt: 1 }),
    );
  });

  it("refuses a third party writing between two other players", async () => {
    const stranger = testEnv.authenticatedContext("mallory-uid").database();
    await assertFails(
      stranger.ref(`friendships/${ALICE}/${BOB}`).set({ status: "FRIENDS", updatedAt: 1 }),
    );
  });

  it("refuses reading someone else's friend list", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await db.ref(`friendships/${ALICE}/${BOB}`).set({ status: "FRIENDS", updatedAt: 1 });
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref(`friendships/${ALICE}`).get());
  });
});

describe("match results", () => {
  const REPORT = {
    roomCode: "ABC123",
    hostUid: ALICE,
    guestUid: BOB,
    winnerUid: ALICE,
    endReason: "NORMAL",
    ranked: true,
    turnCount: 20,
    reportedAt: 1,
    reportedBy: ALICE,
    state: "PENDING",
  };

  it("refuses a report with no finished room behind it", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("matchResults/m1").set(REPORT));
  });

  it("refuses a report filed by someone who did not play", async () => {
    const stranger = testEnv.authenticatedContext("mallory-uid").database();
    await assertFails(
      stranger.ref("matchResults/m1").set({ ...REPORT, reportedBy: "mallory-uid" }),
    );
  });

  it("refuses a report that claims to be already rated", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("matchResults/m1").set({ ...REPORT, state: "APPLIED" }));
  });
});

describe("rooms", () => {
  it("refuses a room created with more walls than the game allows", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      db.ref("rooms/ABC123").set({
        hostUserId: ALICE,
        guestUserId: "",
        status: "WAITING",
        currentTurnUserId: ALICE,
        version: 0,
        winnerUserId: "",
        createdAt: 1,
        lastMoveAt: 1,
        ranked: true,
        visibility: "PUBLIC",
        turnDurationSeconds: 60,
        board: {
          currentPlayer: "PLAYER_ONE",
          status: "IN_PROGRESS",
          turnNumber: 1,
          players: {
            PLAYER_ONE: { row: 8, column: 4, wallsRemaining: 99 },
            PLAYER_TWO: { row: 0, column: 4, wallsRemaining: 10 },
          },
        },
      }),
    );
  });

  it("refuses a room created in someone else's name", async () => {
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(
      bob.ref("rooms/ABC123").set({
        hostUserId: ALICE,
        guestUserId: "",
        status: "WAITING",
        currentTurnUserId: ALICE,
        version: 0,
        winnerUserId: "",
        createdAt: 1,
        lastMoveAt: 1,
        ranked: true,
        visibility: "PUBLIC",
        turnDurationSeconds: 60,
        board: {
          currentPlayer: "PLAYER_ONE",
          status: "IN_PROGRESS",
          turnNumber: 1,
          players: {
            PLAYER_ONE: { row: 8, column: 4, wallsRemaining: 10 },
            PLAYER_TWO: { row: 0, column: 4, wallsRemaining: 10 },
          },
        },
      }),
    );
  });
});

describe("the database is not open", () => {
  it("refuses a read at the root", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("/").get());
  });

  it("refuses a write to an undeclared path", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("somethingNew/x").set(1));
  });
});
