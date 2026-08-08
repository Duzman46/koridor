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
const firebase = require("firebase/compat/app").default;
require("firebase/compat/database");

const ALICE = "alice-uid";
const BOB = "bob-uid";

/**
 * The sentinel the server replaces with its own clock.
 *
 * Anything the rules time — how long ago the last message was sent — has to be stamped this
 * way, because a value a handset chose is a value a handset can choose again.
 */
const SERVER_TIME = firebase.database.ServerValue.TIMESTAMP;

/**
 * A profile shaped the way the rules demand a fresh one must be.
 *
 * This is exactly the payload ProfileCodec.encodeNewProfile writes, field for field. Seeding
 * anything else here means the rules are tested against a profile no handset ever sends, and
 * a required field the app has stopped writing passes every test and refuses every sign-up.
 */
function newProfile(username, overrides = {}) {
  return {
    username,
    normalizedUsername: username.toLowerCase(),
    avatarId: "avatar_01",
    accountType: "GUEST",
    createdAt: 1,
    lastLoginAt: 1,
    preferredLanguage: "",
    tutorialCompleted: false,
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
async function seedProfile(uid, username, overrides = {}) {
  const db = testEnv.authenticatedContext(uid).database();
  await db.ref(`usernames/${username.toLowerCase()}`).set(uid);
  await db.ref(`users/${uid}`).set(newProfile(username, overrides));
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

  it("still accepts a profile from a build that carries a display name", async () => {
    // The second name is gone from the app and from these rules, but a handset that has not
    // updated still sends one. A child no rule mentions has to cost nothing, or that phone
    // cannot create an account at all.
    const db = testEnv.authenticatedContext(ALICE).database();
    await db.ref(`usernames/alice`).set(ALICE);
    await assertSucceeds(
      db.ref(`users/${ALICE}`).set(newProfile("alice", { displayName: "Alice" })),
    );
  });

  it("refuses a profile carrying no name at all", async () => {
    // Tolerating the retired second name must not have loosened the one that replaced it:
    // the username is the only name there is, so a profile without one is not a profile.
    const db = testEnv.authenticatedContext(ALICE).database();
    await db.ref(`usernames/alice`).set(ALICE);
    const nameless = newProfile("alice");
    delete nameless.username;
    await assertFails(db.ref(`users/${ALICE}`).set(nameless));
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

  it("still lets a player change their own avatar and language", async () => {
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(db.ref(`users/${ALICE}/avatarId`).set("avatar_07"));
    await assertSucceeds(db.ref(`users/${ALICE}/preferredLanguage`).set("tr"));
  });

  it("refuses another player editing your profile", async () => {
    await seedProfile(ALICE, "alice");
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref(`users/${ALICE}/avatarId`).set("avatar_07"));
  });
});

/**
 * The all-time table is a query over /users, so a guest cannot be kept off it by not being
 * written — their profile has to exist. They are kept out of the *index* instead:
 * `leaderboardRating` is a copy of the rating that only a linked account may hold, the board
 * is the only query these rules allow over /users, and it can only be asked through that key.
 *
 * Both halves matter and both are checked here. Lose the validate and a guest writes their own
 * way onto the table; lose the read rule and they are back on it by ordering the same profiles
 * by the rating they were given, with no write needed at all.
 */
describe("the board index — who may be on the leaderboard", () => {
  const PAGE = 51;
  const RANK_SCAN = 200;

  /** A rating only the server can have put there, so it is put there the way the server does. */
  async function seedServerRating(uid, rating) {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref(`users/${uid}/rating`).set(rating);
    });
  }

  it("lets a linked account take the rating it holds onto the board", async () => {
    await seedProfile(ALICE, "alice", { accountType: "GOOGLE" });
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(db.ref(`users/${ALICE}/leaderboardRating`).set(1000));
  });

  it("refuses a guest a place in it", async () => {
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref(`users/${ALICE}/leaderboardRating`).set(1000));
  });

  it("refuses a board rating that does not match the rating it mirrors", async () => {
    // Otherwise the index is a second, writable rating, and the table is whatever a modified
    // client says it is.
    await seedProfile(ALICE, "alice", { accountType: "GOOGLE" });
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref(`users/${ALICE}/leaderboardRating`).set(2400));
  });

  it("refuses one player putting another on the board", async () => {
    await seedProfile(ALICE, "alice", { accountType: "GOOGLE" });
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref(`users/${ALICE}/leaderboardRating`).set(1000));
  });

  it("takes a guest who linked an account on at the rating they earned", async () => {
    // The one case that must not be stranded: they played as a guest, they are not a guest
    // any more, and the rating that follows them is the one the server recorded.
    await seedProfile(ALICE, "alice");
    await seedServerRating(ALICE, 1180);
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(db.ref(`users/${ALICE}/accountType`).set("GOOGLE"));
    await assertSucceeds(db.ref(`users/${ALICE}/leaderboardRating`).set(1180));
  });

  it("lets a player page the board through the index", async () => {
    await seedProfile(ALICE, "alice", { accountType: "GOOGLE" });
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      db.ref("users").orderByChild("leaderboardRating").startAt(0).limitToLast(PAGE).get(),
    );
  });

  it("still lets a guest read the board they are not on", async () => {
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      db.ref("users").orderByChild("leaderboardRating").startAt(0).limitToLast(PAGE).get(),
    );
  });

  it("refuses the same page without the floor that leaves guests out", async () => {
    // A rating is never negative, so starting at zero is the whole of "has one at all".
    // Drop it and the profiles carrying no board rating sort in ahead of everyone.
    await seedProfile(ALICE, "alice", { accountType: "GOOGLE" });
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      db.ref("users").orderByChild("leaderboardRating").limitToLast(PAGE).get(),
    );
  });

  it("refuses ranking every profile by the rating it was given", async () => {
    await seedProfile(ALICE, "alice", { accountType: "GOOGLE" });
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("users").orderByChild("rating").limitToLast(PAGE).get());
  });

  it("refuses reading the profile tree whole", async () => {
    await seedProfile(ALICE, "alice", { accountType: "GOOGLE" });
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("users").get());
  });

  it("lets a player count the board rows above their own for an exact position", async () => {
    await seedProfile(ALICE, "alice", { accountType: "GOOGLE" });
    const db = testEnv.authenticatedContext(ALICE).database();
    await db.ref(`users/${ALICE}/leaderboardRating`).set(1000);
    await assertSucceeds(
      db.ref("users").orderByChild("leaderboardRating").startAfter(1000).limitToFirst(RANK_SCAN).get(),
    );
  });

  it("refuses that scan from anywhere but the player's own rating", async () => {
    // Free choice of where to start turns the rank lookup into a window onto any slice of
    // the table, which is a different query than the one the app makes.
    await seedProfile(ALICE, "alice", { accountType: "GOOGLE" });
    const db = testEnv.authenticatedContext(ALICE).database();
    await db.ref(`users/${ALICE}/leaderboardRating`).set(1000);
    await assertFails(
      db.ref("users").orderByChild("leaderboardRating").startAfter(1400).limitToFirst(RANK_SCAN).get(),
    );
  });

  it("lets an account the index has not reached yet ask where it stands", async () => {
    // The scan is pinned to the rating rather than to the copy of it the board is sorted by,
    // and this is why: an account that predates the copy holds a rating and no copy at all,
    // and pinning to the copy compared their rating against nothing and refused them. The two
    // numbers are the same number — the validate rule above will not accept a copy that is
    // anything else — so nothing is opened by asking about the one that is always there.
    await seedProfile(ALICE, "alice", { accountType: "GOOGLE" });
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      db.ref("users").orderByChild("leaderboardRating").startAfter(1000).limitToFirst(RANK_SCAN).get(),
    );
  });
});

describe("server bookkeeping", () => {
  it("keeps the backfill cursor out of every client's reach", async () => {
    // The worker's note to itself about how far through the profile tree it has walked. A
    // client that could move it could stop accounts being taken onto the board at all.
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("maintenance/boardIndexBackfill/cursor").get());
    await assertFails(db.ref("maintenance/boardIndexBackfill/cursor").set(""));
  });

  it("and the invite collector's, along with anything else kept beside them", async () => {
    // The same treatment for the second walk, asked of the subtree rather than of the path,
    // so a third note added later is closed by this test before it is written.
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("maintenance/inviteSweep/cursor").get());
    await assertFails(db.ref("maintenance/inviteSweep/cursor").set(""));
    await assertFails(db.ref("maintenance").get());
    await assertFails(db.ref("maintenance").set({ anything: true }));
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

  it("lets a player who has been blocked block back", async () => {
    // Clearing the other player's row is exactly the write their own block refuses, and a
    // multi-path update is all-or-nothing — so sending both halves together meant being
    // blocked first was all it took to make somebody unblockable. The block that matters is
    // the one row on the blocker's own node, and that row has to land on its own.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref(`friendships/${BOB}/${ALICE}`).set({
        status: "BLOCKED",
        updatedAt: 1,
      });
    });
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`friendships/${BOB}/${ALICE}`).remove());
    await assertSucceeds(
      alice.ref().update({
        [`friendships/${ALICE}/${BOB}/status`]: "BLOCKED",
        [`friendships/${ALICE}/${BOB}/updatedAt`]: 2,
        [`invites/${ALICE}/${BOB}`]: null,
      }),
    );
  });
});

describe("the request channel", () => {
  // invites/{recipient}/{sender} carries an invitation, a rematch and a refused rematch alike,
  // told apart by a kind. Friendship is still the price of an invitation. It is not the price
  // of a rematch: the two have just played each other, and the finished match is what the
  // rules look at instead — which is also why a rematch has to name it.

  const CAROL = "carol-uid";
  const PLAYED = "PLAYED";

  function request(overrides = {}) {
    return {
      kind: "GAME_INVITE",
      fromUserId: ALICE,
      fromUsername: "alice",
      roomCode: "NEWRM1",
      playedRoomCode: "",
      createdAt: 1,
      expiresAt: 2,
      ...overrides,
    };
  }

  /** A rematch ALICE is offering BOB off the back of the match they just played. */
  function rematch(overrides = {}) {
    return request({ kind: "REMATCH", playedRoomCode: PLAYED, ...overrides });
  }

  /**
   * A match between ALICE and BOB, seeded straight in: the point here is what the invite rule
   * makes of a room, not how that room came to be in that state.
   */
  async function seedMatch(overrides = {}) {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref(`rooms/${PLAYED}`).set({
        hostUserId: ALICE,
        guestUserId: BOB,
        status: "FINISHED",
        currentTurnUserId: "",
        version: 9,
        winnerUserId: ALICE,
        endReason: "NORMAL",
        createdAt: 1,
        lastMoveAt: 2,
        ranked: true,
        visibility: "PUBLIC",
        turnDurationSeconds: 60,
        browseKey: "PUBLIC_FINISHED",
        board: {
          currentPlayer: "PLAYER_ONE",
          status: "PLAYER_ONE_WON",
          turnNumber: 20,
          players: {
            PLAYER_ONE: { row: 0, column: 4, wallsRemaining: 7 },
            PLAYER_TWO: { row: 3, column: 4, wallsRemaining: 8 },
          },
        },
        ...overrides,
      });
    });
  }

  async function befriend(first, second) {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const entry = { status: "FRIENDS", updatedAt: 1 };
      await ctx.database().ref(`friendships/${first}/${second}`).set(entry);
      await ctx.database().ref(`friendships/${second}/${first}`).set(entry);
    });
  }

  it("lets a friend send a game invitation", async () => {
    await befriend(ALICE, BOB);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref(`invites/${BOB}/${ALICE}`).set(request()));
  });

  it("refuses a game invitation from someone who is not a friend", async () => {
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`invites/${BOB}/${ALICE}`).set(request()));
  });

  it("still accepts an invitation from a build that sends no kind", async () => {
    // The kind is new, and a handset that has not updated writes an entry without one. That
    // entry is an invitation — the only thing that build could send — and has to keep working.
    await befriend(ALICE, BOB);
    const alice = testEnv.authenticatedContext(ALICE).database();
    const legacy = request();
    delete legacy.kind;
    delete legacy.playedRoomCode;
    await assertSucceeds(alice.ref(`invites/${BOB}/${ALICE}`).set(legacy));
  });

  it("takes a rematch between two players who are not friends", async () => {
    await seedMatch();
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref(`invites/${BOB}/${ALICE}`).set(rematch()));
  });

  it("takes a rematch asked for by the guest as well as the host", async () => {
    await seedMatch();
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertSucceeds(
      bob.ref(`invites/${ALICE}/${BOB}`).set(rematch({ fromUserId: BOB, fromUsername: "bob" })),
    );
  });

  it("carries a refused rematch back to the player who asked", async () => {
    await seedMatch();
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertSucceeds(
      bob.ref(`invites/${ALICE}/${BOB}`).set(
        rematch({ kind: "REMATCH_DECLINED", fromUserId: BOB, fromUsername: "bob" }),
      ),
    );
  });

  it("refuses a rematch naming a match that is still being played", async () => {
    // Until the match is over there is nothing to have a rematch of, and an entry that could
    // be written mid-game would let one player interrupt the other with a bar.
    await seedMatch({ status: "IN_PROGRESS", currentTurnUserId: BOB, winnerUserId: "" });
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`invites/${BOB}/${ALICE}`).set(rematch()));
  });

  it("refuses a rematch naming a match neither player was in", async () => {
    await seedMatch({ hostUserId: CAROL, guestUserId: "dave-uid", winnerUserId: CAROL });
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`invites/${BOB}/${ALICE}`).set(rematch()));
  });

  it("refuses a rematch aimed at someone who was not in the match", async () => {
    await seedMatch();
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`invites/${CAROL}/${ALICE}`).set(rematch()));
  });

  it("refuses a rematch that names no match at all", async () => {
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`invites/${BOB}/${ALICE}`).set(rematch({ playedRoomCode: "" })));
  });

  it("refuses an entry whose kind is not one the app writes", async () => {
    await befriend(ALICE, BOB);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`invites/${BOB}/${ALICE}`).set(request({ kind: "SYSTEM" })));
  });

  it("refuses an entry naming a match with something that is not a room code", async () => {
    // An invitation reaches its own arm of the write rule on friendship alone, so this field
    // is checked nowhere else on that path — and it is the field a rematch is judged by.
    await befriend(ALICE, BOB);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`invites/${BOB}/${ALICE}`).set(request({ playedRoomCode: 7 })));
  });

  it("refuses an entry sent under someone else's name", async () => {
    await seedMatch();
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref(`invites/${BOB}/${ALICE}`).set(rematch({ fromUserId: CAROL })),
    );
  });

  it("lets the recipient clear an entry they have answered", async () => {
    await seedMatch();
    const alice = testEnv.authenticatedContext(ALICE).database();
    await alice.ref(`invites/${BOB}/${ALICE}`).set(rematch());
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertSucceeds(bob.ref(`invites/${BOB}/${ALICE}`).remove());
  });

  it("lets the sender withdraw a question nobody has answered", async () => {
    // A player who gives up waiting closes the room they opened, and the entry pointing at it
    // has to go with it — otherwise the other phone keeps offering a room that is not there.
    // Taking back only what you yourself wrote needs no further permission than having.
    await seedMatch();
    const alice = testEnv.authenticatedContext(ALICE).database();
    await alice.ref(`invites/${BOB}/${ALICE}`).set(rematch());
    await assertSucceeds(alice.ref(`invites/${BOB}/${ALICE}`).remove());
  });

  it("refuses clearing an entry between two other players", async () => {
    await seedMatch();
    const alice = testEnv.authenticatedContext(ALICE).database();
    await alice.ref(`invites/${BOB}/${ALICE}`).set(rematch());
    const stranger = testEnv.authenticatedContext(CAROL).database();
    await assertFails(stranger.ref(`invites/${BOB}/${ALICE}`).remove());
  });

  it("refuses reading someone else's channel", async () => {
    await seedMatch();
    const alice = testEnv.authenticatedContext(ALICE).database();
    await alice.ref(`invites/${BOB}/${ALICE}`).set(rematch());
    await assertFails(alice.ref(`invites/${BOB}`).get());
  });
});

describe("erasing an account", () => {
  // "Delete my account permanently" is a promise, and the rules are what decides whether the
  // client can keep it. Write permission in this database only ever flows downwards, so a rule
  // written at friendships/$owner/$other says nothing about friendships/$owner — and a client
  // that asks for the parent is refused at every leaf under it. These pin the exact set of
  // paths the erasure is allowed to name, so that shape cannot be quietly simplified away.

  /** Everything one account leaves behind, on its own node and on the other player's. */
  async function seedEntangledPlayers({ bobsView = "FRIENDS" } = {}) {
    await seedProfile(ALICE, "alice");
    await seedProfile(BOB, "bob");
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.database();
      await db.ref(`friendships/${ALICE}/${BOB}`).set({ status: "FRIENDS", updatedAt: 1 });
      await db.ref(`friendships/${BOB}/${ALICE}`).set({ status: bobsView, updatedAt: 1 });
      await db.ref(`invites/${ALICE}/${BOB}`).set({
        kind: "GAME_INVITE",
        fromUserId: BOB,
        roomCode: "ROOM01",
        createdAt: 1,
        expiresAt: 2,
      });
      await db.ref(`invites/${BOB}/${ALICE}`).set({
        kind: "GAME_INVITE",
        fromUserId: ALICE,
        roomCode: "ROOM02",
        createdAt: 1,
        expiresAt: 2,
      });
      await db.ref(`presence/${ALICE}`).set({ online: true, lastSeen: 1 });
      await db.ref(`usersPrivate/${ALICE}/email`).set("alice@example.com");
    });
  }

  it("lets a player erase every leaf their account left behind", async () => {
    await seedEntangledPlayers();
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref(`friendships/${ALICE}/${BOB}`).remove());
    await assertSucceeds(alice.ref(`friendships/${BOB}/${ALICE}`).remove());
    await assertSucceeds(alice.ref(`invites/${ALICE}/${BOB}`).remove());
    await assertSucceeds(alice.ref(`invites/${BOB}/${ALICE}`).remove());
    await assertSucceeds(alice.ref(`presence/${ALICE}`).remove());
    await assertSucceeds(alice.ref(`usersPrivate/${ALICE}`).remove());
    await assertSucceeds(alice.ref("usernames/alice").remove());
    await assertSucceeds(alice.ref(`users/${ALICE}`).remove());
  });

  it("refuses taking the friend list down as one node", async () => {
    await seedEntangledPlayers();
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`friendships/${ALICE}`).remove());
  });

  it("refuses taking the request channel down as one node", async () => {
    await seedEntangledPlayers();
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`invites/${ALICE}`).remove());
  });

  it("carries the erasure in one atomic update as long as it names only leaves", async () => {
    // The control for the test below. A multi-path update is written from the root, and the
    // root itself grants nothing — so this establishes that what refuses the next one is the
    // paths it names, not the fact that it is a root update at all.
    await seedEntangledPlayers();
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      alice.ref().update({
        [`friendships/${ALICE}/${BOB}`]: null,
        [`invites/${ALICE}/${BOB}`]: null,
        [`presence/${ALICE}`]: null,
      }),
    );
  });

  it("refuses an erasure that names those parents in one atomic update", async () => {
    // The whole deletion used to travel as a single root update, and a multi-path update is
    // all-or-nothing: the two refused parents took the rows that were allowed down with them,
    // so a player who asked to be forgotten kept their friends, their invites and their
    // presence. Nothing about that failure was visible from the client.
    await seedEntangledPlayers();
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref().update({
        [`friendships/${ALICE}`]: null,
        [`invites/${ALICE}`]: null,
        [`presence/${ALICE}`]: null,
      }),
    );
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const survived = await ctx.database().ref(`presence/${ALICE}`).get();
      if (!survived.exists()) throw new Error("presence should have been spared by the refusal");
    });
  });

  it("keeps a block alive when the blocked account erases itself", async () => {
    // A block has to outlive the account it was aimed at, or leaving and coming back is all it
    // takes to get past one. This is the row the erasure is expected to be refused, which is
    // why the mirrors are written one at a time rather than together.
    await seedEntangledPlayers({ bobsView: "BLOCKED" });
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`friendships/${BOB}/${ALICE}`).remove());
    await assertSucceeds(alice.ref(`friendships/${ALICE}/${BOB}`).remove());
  });

  it("refuses one player erasing another's profile", async () => {
    await seedEntangledPlayers();
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref(`users/${ALICE}`).remove());
    await assertFails(bob.ref(`usersPrivate/${ALICE}`).remove());
  });

  it("refuses releasing a name somebody else still holds", async () => {
    await seedEntangledPlayers();
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref("usernames/alice").remove());
  });

  it("refuses erasing somebody else's presence", async () => {
    await seedEntangledPlayers();
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref(`presence/${ALICE}`).remove());
  });

  // The entry the erasure above cannot reach, and why the worker collects it instead.
  //
  // Every game invitation this account sent is found by walking its friend list, because
  // friendship is exactly what the rules charge for one: the invitation and the friendship
  // always come as a pair. A rematch is charged for differently — the finished match is the
  // licence, and the two need never have been friends — so `invites/{opponent}/{me}` can
  // exist with nothing on this side of the database pointing at it.
  //
  // The three below are the whole problem. The write is permitted, the read that would find
  // it is not at any depth, and nobody else may do it on the account's behalf.

  const CAROL = "carol-uid";

  /** A rematch ALICE sent CAROL off a match they played, having never been friends. */
  async function seedRematchToAStranger() {
    await seedProfile(ALICE, "alice");
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref(`invites/${CAROL}/${ALICE}`).set({
        kind: "REMATCH",
        fromUserId: ALICE,
        fromUsername: "alice",
        roomCode: "NEWRM1",
        playedRoomCode: "PLAYED",
        createdAt: 1,
        expiresAt: 2,
      });
    });
  }

  it("lets the sender take back an entry sight unseen, friendship or none", async () => {
    await seedRematchToAStranger();
    const alice = testEnv.authenticatedContext(ALICE).database();
    // No friendship row exists in either direction, so the erasure's walk never names CAROL.
    await assertSucceeds(alice.ref(`invites/${CAROL}/${ALICE}`).remove());
  });

  it("but never lets them find out where they sent one", async () => {
    await seedRematchToAStranger();
    const alice = testEnv.authenticatedContext(ALICE).database();
    // Read permission is granted at invites/$recipient to that recipient alone, and nothing
    // below it opens a single entry to the player who wrote it. These three refusals together
    // are why no client can enumerate what it has to erase — so the collector is
    // collectDeadInvites in worker/src/sweep.ts, which holds a credential these rules do not
    // answer to.
    await assertFails(alice.ref("invites").get());
    await assertFails(alice.ref(`invites/${CAROL}`).get());
    await assertFails(alice.ref(`invites/${CAROL}/${ALICE}`).get());
  });

  it("refuses a third party collecting an entry between two other players", async () => {
    // The other half of why it cannot be a phone: the job cannot be handed to the opponent's
    // device either, and by the time anyone notices, the account that wrote it is gone.
    await seedRematchToAStranger();
    const stranger = testEnv.authenticatedContext(BOB).database();
    await assertFails(stranger.ref(`invites/${CAROL}/${ALICE}`).remove());
  });
});

describe("handing a guest over to an account that already exists", () => {
  // Signing in as an account a guest's credential turned out to already belong to merges
  // nothing: Firebase has no join between two identities that both exist, so the guest's rows
  // are erased first, while the guest is still the identity making the request. A moment later
  // that uid is signed out on this handset and reachable from no other, and anything left under
  // it is left for good — a profile nobody can read and a name reserved against nobody.
  //
  // What that ordering rests on is a set of rules facts the client cannot see, so they are
  // pinned here. These are exactly the writes RtdbUserProfileRepository.deleteAccountData
  // makes, in the order it makes them.

  /** The shape of a name the app hands out, which is the only kind a guest ever carries. */
  const GENERATED = "guest_483920";

  async function seedGuest() {
    await seedProfile(ALICE, GENERATED, { accountType: "GUEST" });
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref(`usersPrivate/${ALICE}/email`).set("");
    });
  }

  it("lets a guest give up everything it owns", async () => {
    // A generated name has to be releasable by the account holding it, or every guest that
    // ever signs into another account burns one of them permanently. `guest_######` is inside
    // what the username key validates, and that is not obvious from either end on its own.
    await seedGuest();
    const guest = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(guest.ref(`usernames/${GENERATED}`).remove());
    await assertSucceeds(guest.ref(`usersPrivate/${ALICE}`).remove());
    await assertSucceeds(guest.ref(`users/${ALICE}`).remove());
  });

  it("still lets the profile go once its name has already been released", async () => {
    // The order is forced — the name is read off the profile, so it has to be released while
    // the profile is still there — and it is the order that could quietly stop working. The
    // profile rule reaches into `usernames` to authorise a *new* profile, and folding that
    // condition up into the rule as a whole would leave a guest here with their name already
    // given up and a profile they can no longer delete: half erased, and no way back.
    await seedGuest();
    const guest = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(guest.ref(`usernames/${GENERATED}`).remove());
    await assertSucceeds(guest.ref(`users/${ALICE}`).remove());
  });

  it("refuses the account being signed into tidying up after the guest", async () => {
    // Why the erasure cannot simply happen afterwards, when it would be easier to be sure the
    // sign-in succeeded first. Nobody but the guest may take the guest's rows down, and by
    // then the guest is gone.
    await seedGuest();
    await seedProfile(BOB, "bob", { accountType: "GOOGLE" });
    const other = testEnv.authenticatedContext(BOB).database();
    await assertFails(other.ref(`users/${ALICE}`).remove());
    await assertFails(other.ref(`usersPrivate/${ALICE}`).remove());
    await assertFails(other.ref(`usernames/${GENERATED}`).remove());
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

describe("the recent games on a profile", () => {
  const MATCH = "ABC123_1";

  /**
   * A row put there the way the only writer there is puts it there.
   *
   * `worker/src/sweep.ts` holds the service-account credential and so goes past the rules
   * entirely; disabling them here is what that looks like from inside a test.
   */
  async function seedHistory(uid, matchId = MATCH, entry = {}) {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref(`recentMatches/${uid}/${matchId}`).set({
        opponentName: "bob",
        result: "WIN",
        playedAt: 1,
        ratingChange: 12,
        ...entry,
      });
    });
  }

  it("lets any signed-in player read somebody else's", async () => {
    // The point of the list: it is on a public profile, and a profile is public to whoever
    // opened it — from the leaderboard, from a friend list, from a search result.
    await seedHistory(ALICE);
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertSucceeds(bob.ref(`recentMatches/${ALICE}`).get());
  });

  it("refuses a player writing their own", async () => {
    // The refusal that makes the list worth showing at all. A player who could write here
    // could invent the wins, and name an opponent who never existed.
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref(`recentMatches/${ALICE}/${MATCH}`).set({
        opponentName: "bob",
        result: "WIN",
        playedAt: 1,
        ratingChange: 400,
      }),
    );
  });

  it("refuses a player deleting a match out of their own", async () => {
    // Erasing a loss is a write too. Allowed, the list would be every match a player was
    // content to be seen losing.
    await seedHistory(ALICE, MATCH, { result: "LOSS", ratingChange: -12 });
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`recentMatches/${ALICE}/${MATCH}`).remove());
  });

  it("refuses a player writing into somebody else's", async () => {
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(
      bob.ref(`recentMatches/${ALICE}/${MATCH}`).set({
        opponentName: "bob",
        result: "LOSS",
        playedAt: 1,
        ratingChange: -400,
      }),
    );
  });

  it("refuses an unauthenticated read", async () => {
    await seedHistory(ALICE);
    const anonymous = testEnv.unauthenticatedContext().database();
    await assertFails(anonymous.ref(`recentMatches/${ALICE}`).get());
  });

  it("refuses reading everybody's at once", async () => {
    // A profile asks for one player's list. Nothing asks for the whole tree, and a tree that
    // could be read whole is a record of who played whom across the entire game.
    await seedHistory(ALICE);
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref("recentMatches").get());
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

describe("colour is the seat", () => {
  // Seat one is blue and always opens, so a host who picked red holds seat two and the room
  // they opened has nobody on the clock until someone arrives to take seat one. Every place
  // the rules used to read "the host is seat one" has to ask the room instead.
  //
  // A room carrying no hostSeat at all was written by a build that predates seats, where the
  // host was always seat one. That is what the absent field means, and phones running that
  // build have to keep playing.

  /** A fresh room, shaped the way the creation rule insists a new one must be. */
  function newRoom(overrides = {}) {
    return {
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
      ...overrides,
    };
  }

  /** The same room once ALICE has chosen red: seat two, and an empty clock. */
  function redHostRoom(overrides = {}) {
    return newRoom({ hostSeat: "PLAYER_TWO", currentTurnUserId: "", ...overrides });
  }

  /** BOB takes the free seat. The board is untouched; only the room changes. */
  function joined(room, onClock) {
    return {
      ...room,
      guestUserId: BOB,
      status: "IN_PROGRESS",
      currentTurnUserId: onClock,
      browseKey: "PUBLIC_IN_PROGRESS",
    };
  }

  /**
   * A match under way in a room the old protocol wrote: no seat, and the retired match clock
   * still on it. The host holds seat one, because that is the only shape that build produced.
   */
  function oldMatch(overrides = {}) {
    return {
      ...newRoom(),
      totalDurationSeconds: 900,
      guestUserId: BOB,
      status: "IN_PROGRESS",
      currentTurnUserId: ALICE,
      endReason: "",
      browseKey: "PUBLIC_IN_PROGRESS",
      ...overrides,
    };
  }

  /** A match under way in a red-host room: BOB holds seat one and is to move. */
  function redHostMatch(overrides = {}) {
    return {
      ...redHostRoom(),
      guestUserId: BOB,
      status: "IN_PROGRESS",
      currentTurnUserId: BOB,
      endReason: "",
      browseKey: "PUBLIC_IN_PROGRESS",
      ...overrides,
    };
  }

  /** Seat one steps forward and the turn passes to whoever `onClock` claims seat two is. */
  function advanced(match, onClock) {
    return {
      ...match,
      version: match.version + 1,
      lastMoveAt: Date.now(),
      currentTurnUserId: onClock,
      board: {
        ...match.board,
        currentPlayer: "PLAYER_TWO",
        turnNumber: match.board.turnNumber + 1,
        players: {
          ...match.board.players,
          PLAYER_ONE: { row: 7, column: 4, wallsRemaining: 10 },
        },
      },
    };
  }

  /** Seat two reaches row eight, which ends the game in its favour. */
  function won(match, winner) {
    return {
      ...match,
      status: "FINISHED",
      endReason: "NORMAL",
      currentTurnUserId: "",
      winnerUserId: winner,
      version: match.version + 1,
      lastMoveAt: Date.now(),
      browseKey: "PUBLIC_FINISHED",
      board: {
        ...match.board,
        status: "PLAYER_TWO_WON",
        players: {
          ...match.board.players,
          PLAYER_TWO: { row: 8, column: 4, wallsRemaining: 10 },
        },
      },
    };
  }

  /** Seat one reaches row zero, which ends the game in its favour. */
  function seatOneWon(match, winner) {
    return {
      ...match,
      status: "FINISHED",
      endReason: "NORMAL",
      currentTurnUserId: "",
      winnerUserId: winner,
      version: match.version + 1,
      lastMoveAt: Date.now(),
      browseKey: "PUBLIC_FINISHED",
      board: {
        ...match.board,
        status: "PLAYER_ONE_WON",
        players: {
          ...match.board.players,
          PLAYER_ONE: { row: 0, column: 4, wallsRemaining: 10 },
        },
      },
    };
  }

  /** An old-protocol match one move from seat one — its host — reaching its goal row. */
  function oldAlmostWon() {
    return oldMatch({
      version: 5,
      board: {
        currentPlayer: "PLAYER_ONE",
        status: "IN_PROGRESS",
        turnNumber: 6,
        players: {
          PLAYER_ONE: { row: 1, column: 4, wallsRemaining: 10 },
          PLAYER_TWO: { row: 3, column: 4, wallsRemaining: 10 },
        },
      },
    });
  }

  /** A red-host match one move from seat two reaching its goal row. */
  function almostWon() {
    return redHostMatch({
      currentTurnUserId: ALICE,
      version: 5,
      board: {
        currentPlayer: "PLAYER_TWO",
        status: "IN_PROGRESS",
        turnNumber: 6,
        players: {
          PLAYER_ONE: { row: 5, column: 4, wallsRemaining: 10 },
          PLAYER_TWO: { row: 7, column: 4, wallsRemaining: 10 },
        },
      },
    });
  }

  async function seed(code, room) {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref(`rooms/${code}`).set(room);
    });
  }

  it("opens a room whose host took seat two with nobody on the clock", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(db.ref("rooms/RED001").set(redHostRoom()));
  });

  it("refuses a host in seat two who puts themselves on the clock", async () => {
    // Seat one opens and it is empty, so there is nobody to be on the clock yet. Letting
    // this through would give the host the second colour and the first move at once.
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("rooms/RED001").set(redHostRoom({ currentTurnUserId: ALICE })));
  });

  it("still opens a room from a build that never heard of seats", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(db.ref("rooms/OLD001").set(newRoom()));
  });

  it("refuses an unknown seat", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("rooms/RED001").set(redHostRoom({ hostSeat: "PLAYER_THREE" })));
  });

  it("starts the clock on the guest who takes the opening seat", async () => {
    await seed("RED002", redHostRoom());
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertSucceeds(bob.ref("rooms/RED002").set(joined(redHostRoom(), BOB)));
  });

  it("refuses a joiner who starts the clock on the host instead", async () => {
    await seed("RED002", redHostRoom());
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref("rooms/RED002").set(joined(redHostRoom(), ALICE)));
  });

  it("refuses a joiner who moves the host out of seat two", async () => {
    // Rewriting the seat on the way in is how a guest would take the opening move from a
    // host who chose it. The seat is the host's to set, once, when they open the room.
    await seed("RED002", redHostRoom());
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(
      bob.ref("rooms/RED002").set(joined(redHostRoom({ hostSeat: "PLAYER_ONE" }), BOB)),
    );
  });

  it("still starts an old room's clock on its host", async () => {
    await seed("OLD002", newRoom());
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertSucceeds(bob.ref("rooms/OLD002").set(joined(newRoom(), ALICE)));
  });

  it("lets the guest holding seat one open the match", async () => {
    const match = redHostMatch();
    await seed("RED003", match);
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertSucceeds(bob.ref("rooms/RED003").set(advanced(match, ALICE)));
  });

  it("refuses a move that hands the turn back to the seat that made it", async () => {
    // Reading seat two as "the guest" — which is what it always used to mean — would leave
    // BOB on the clock for both seats and let one player play the whole game.
    const match = redHostMatch();
    await seed("RED003", match);
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref("rooms/RED003").set(advanced(match, BOB)));
  });

  it("refuses the host in seat two moving before seat one has", async () => {
    const match = redHostMatch();
    await seed("RED003", match);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/RED003").set(advanced(match, ALICE)));
  });

  it("credits a win by seat two to the host sitting in it", async () => {
    const match = almostWon();
    await seed("RED004", match);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref("rooms/RED004").set(won(match, ALICE)));
  });

  it("refuses that same win being paid to the guest", async () => {
    const match = almostWon();
    await seed("RED004", match);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/RED004").set(won(match, BOB)));
  });

  it("still lets an old room's host take their turn", async () => {
    // Opening such a room and joining it are covered above; this is the move itself, where
    // an absent seat has to fall through to "the host opened" every time the turn changes
    // hands. A match already under way when the phone updated is decided here.
    const match = oldMatch();
    await seed("OLD003", match);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref("rooms/OLD003").set(advanced(match, BOB)));
  });

  it("still credits an old room's win to its host in seat one", async () => {
    const match = oldAlmostWon();
    await seed("OLD004", match);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref("rooms/OLD004").set(seatOneWon(match, ALICE)));
  });

  it("refuses an old room's win being paid to the seat that did not reach its row", async () => {
    const match = oldAlmostWon();
    await seed("OLD004", match);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/OLD004").set(seatOneWon(match, BOB)));
  });
});

describe("the matchmaking list", () => {
  // Quick match writes a name into this list and nothing else. Everything about pairing
  // follows from what these rules allow: a player owns exactly one entry, everybody can read
  // the list because that is how two phones find each other, and the rating in an entry is
  // pinned to the profile so nobody can queue as a beginner to farm easy opponents.

  function entry(overrides = {}) {
    return { rating: 1000, ranked: true, queuedAt: Date.now(), ...overrides };
  }

  it("lets a player take their own place in the list", async () => {
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(db.ref(`matchmaking/${ALICE}`).set(entry()));
  });

  it("lets a player give their place up again", async () => {
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await db.ref(`matchmaking/${ALICE}`).set(entry());
    await assertSucceeds(db.ref(`matchmaking/${ALICE}`).remove());
  });

  it("lets anyone signed in read the whole list", async () => {
    // Pairing is done by the phones, and a phone that cannot see who is waiting cannot pair
    // with them. The list holds nothing private: a user id and a rating already public.
    await seedProfile(ALICE, "alice");
    await testEnv.authenticatedContext(ALICE).database().ref(`matchmaking/${ALICE}`).set(entry());
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertSucceeds(bob.ref("matchmaking").get());
  });

  it("refuses an unauthenticated read of the list", async () => {
    const anonymous = testEnv.unauthenticatedContext().database();
    await assertFails(anonymous.ref("matchmaking").get());
  });

  it("refuses a rating higher than the one on the profile", async () => {
    // Overstating a rating games the pairing one way and understating it farms beginners the
    // other. Either is a rating the profile does not carry, which is the whole check.
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref(`matchmaking/${ALICE}`).set(entry({ rating: 2400 })));
  });

  it("refuses a rating lower than the one on the profile", async () => {
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref(`matchmaking/${ALICE}`).set(entry({ rating: 100 })));
  });

  it("refuses rewriting the rating on an entry already in the list", async () => {
    // The pin has to hold on the field on its own, not only on the whole entry: a write to
    // one child never runs the parent's validation.
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await db.ref(`matchmaking/${ALICE}`).set(entry());
    await assertFails(db.ref(`matchmaking/${ALICE}/rating`).set(2400));
  });

  it("refuses queueing under another player's name", async () => {
    await seedProfile(ALICE, "alice");
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref(`matchmaking/${ALICE}`).set(entry()));
  });

  it("refuses taking another player out of the list", async () => {
    // Nobody may clear a rival out of the way, and nobody has to: a pairing is written as a
    // room, and each player takes their own name out once they are in one.
    await seedProfile(ALICE, "alice");
    await testEnv.authenticatedContext(ALICE).database().ref(`matchmaking/${ALICE}`).set(entry());
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref(`matchmaking/${ALICE}`).remove());
  });

  it("refuses an entry that is missing what pairing needs", async () => {
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref(`matchmaking/${ALICE}`).set({ rating: 1000 }));
  });

  it("refuses a place in the list dated into the future", async () => {
    // The stamp decides who is stale and which of a pair writes the room; a future one would
    // sit at the top of the list and never be swept out of it.
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      db.ref(`matchmaking/${ALICE}`).set(entry({ queuedAt: Date.now() + 600_000 })),
    );
  });

  it("refuses a field nothing reads", async () => {
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref(`matchmaking/${ALICE}`).set(entry({ skill: "grandmaster" })));
  });

  it("refuses an entry whose ranked flag is not a yes or a no", async () => {
    // Whether the pair plays for rating is read straight off this field by the other phone
    // and by the worker, so anything that is not a plain boolean is a rating decided by
    // whatever each of them makes of it.
    await seedProfile(ALICE, "alice");
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref(`matchmaking/${ALICE}`).set(entry({ ranked: "yes" })));
  });
});

describe("the room a pairing writes", () => {
  // A pairing is one write: a room naming both players, already under way. Nobody may write
  // to anybody else's queue entry, so the room is the whole of the handshake — which is why
  // these rules have to carry every condition that makes it an honest one.

  function entry() {
    return { rating: 1000, ranked: true, queuedAt: Date.now() };
  }

  function pairedRoom(overrides = {}) {
    return {
      roomName: "",
      hostUserId: ALICE,
      guestUserId: BOB,
      hostName: "alice",
      hostRating: 1000,
      visibility: "PRIVATE",
      status: "IN_PROGRESS",
      ranked: true,
      requiresPassword: false,
      createdAt: Date.now(),
      expiresAt: Date.now() + 86_400_000,
      turnDurationSeconds: 60,
      currentTurnUserId: ALICE,
      lastMoveAt: Date.now(),
      winnerUserId: "",
      endReason: "",
      version: 0,
      hostSeat: "PLAYER_ONE",
      browseKey: "PRIVATE_IN_PROGRESS",
      board: {
        currentPlayer: "PLAYER_ONE",
        status: "IN_PROGRESS",
        turnNumber: 1,
        players: {
          PLAYER_ONE: { row: 8, column: 4, wallsRemaining: 10 },
          PLAYER_TWO: { row: 0, column: 4, wallsRemaining: 10 },
        },
      },
      ...overrides,
    };
  }

  async function queue(...uids) {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      for (const uid of uids) {
        await ctx.database().ref(`matchmaking/${uid}`).set(entry());
      }
    });
  }

  it("lets a waiting player write the room for the rival they claimed", async () => {
    await queue(ALICE, BOB);
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(db.ref("rooms/MM0001").set(pairedRoom()));
  });

  it("puts the guest on the clock when the drawn seats came out that way", async () => {
    await queue(ALICE, BOB);
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      db.ref("rooms/MM0002").set(pairedRoom({ hostSeat: "PLAYER_TWO", currentTurnUserId: BOB })),
    );
  });

  it("refuses dragging in somebody who is not waiting", async () => {
    // Otherwise quick match is a way to start a match against any player at all, whether or
    // not they were looking for one.
    await queue(ALICE);
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("rooms/MM0003").set(pairedRoom()));
  });

  it("refuses a pairing written by somebody not in the list themselves", async () => {
    await queue(BOB);
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("rooms/MM0004").set(pairedRoom()));
  });

  it("refuses starting the clock on the seat that does not open", async () => {
    await queue(ALICE, BOB);
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("rooms/MM0005").set(pairedRoom({ currentTurnUserId: BOB })));
  });

  it("refuses locking a matchmade room with a password", async () => {
    await queue(ALICE, BOB);
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("rooms/MM0006").set(pairedRoom({ requiresPassword: true })));
  });

  it("refuses a matchmade room whose board has already been played", async () => {
    await queue(ALICE, BOB);
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      db.ref("rooms/MM0007").set(
        pairedRoom({
          board: {
            currentPlayer: "PLAYER_TWO",
            status: "IN_PROGRESS",
            turnNumber: 4,
            players: {
              PLAYER_ONE: { row: 2, column: 4, wallsRemaining: 10 },
              PLAYER_TWO: { row: 0, column: 4, wallsRemaining: 10 },
            },
          },
        }),
      ),
    );
  });

  it("refuses overwriting a room that is already there", async () => {
    // The room a pairing lands in is named after the player being claimed, so two phones
    // that pick the same rival aim at one node. This refusal is what makes the second of them
    // lose the race rather than take the first one's match apart.
    await queue(ALICE, BOB);
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref("rooms/MM0008").set(pairedRoom({ hostUserId: "mallory-uid" }));
    });
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("rooms/MM0008").set(pairedRoom()));
  });

  it("still refuses a matchmade room opened in someone else's name", async () => {
    await queue(ALICE, BOB);
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref("rooms/MM0009").set(pairedRoom()));
  });

  it("refuses a room that pairs a player with themselves", async () => {
    await queue(ALICE);
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("rooms/MM0010").set(pairedRoom({ guestUserId: ALICE })));
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

describe("room listing — the queries the app actually issues", () => {
  // These are the exact three queries FirebaseOnlineGameRepository runs. They were denied
  // by a `.read: false` on /rooms, which broke the room browser, quick match and rejoining
  // a match; nothing here covered them, which is why it shipped.
  it("lets a signed-in player browse public waiting rooms", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      db.ref("rooms").orderByChild("browseKey").equalTo("PUBLIC_WAITING").limitToLast(30).get(),
    );
  });

  it("lets a player find the room they are hosting", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(db.ref("rooms").orderByChild("hostUserId").equalTo(ALICE).get());
  });

  it("lets a player find the room they joined", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(db.ref("rooms").orderByChild("guestUserId").equalTo(ALICE).get());
  });

  it("refuses enumerating another player's rooms", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("rooms").orderByChild("hostUserId").equalTo(BOB).get());
  });

  it("refuses listing private rooms", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      db.ref("rooms").orderByChild("browseKey").equalTo("PRIVATE_WAITING").limitToLast(30).get(),
    );
  });

  it("refuses an unbounded read of every room", async () => {
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("rooms").get());
  });
});

describe("abandoned matches", () => {
  // The ten-minute idle forfeit replaced "return to your match". It must hold whether or
  // not the room has a turn clock of its own, and it must work in both directions: the
  // player still watching can end it, and so can the device of the player who walked off.

  /** An in-progress room with BOB on the clock and no turn timer at all. */
  function livingRoom(overrides = {}) {
    return {
      hostUserId: ALICE,
      guestUserId: BOB,
      status: "IN_PROGRESS",
      currentTurnUserId: BOB,
      version: 4,
      winnerUserId: "",
      endReason: "",
      createdAt: 1,
      lastMoveAt: 1,
      ranked: true,
      visibility: "PUBLIC",
      turnDurationSeconds: 0,
      browseKey: "PUBLIC_IN_PROGRESS",
      board: {
        currentPlayer: "PLAYER_TWO",
        status: "IN_PROGRESS",
        turnNumber: 5,
        players: {
          PLAYER_ONE: { row: 7, column: 4, wallsRemaining: 9 },
          PLAYER_TWO: { row: 1, column: 4, wallsRemaining: 9 },
        },
      },
      ...overrides,
    };
  }

  function finished(room, winner) {
    return {
      ...room,
      status: "FINISHED",
      winnerUserId: winner,
      endReason: "TIMEOUT",
      currentTurnUserId: "",
      version: room.version + 1,
      browseKey: "PUBLIC_FINISHED",
    };
  }

  async function seed(room) {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref("rooms/IDLE01").set(room);
    });
  }

  it("lets the waiting player end a match nobody has moved in for ten minutes", async () => {
    const room = livingRoom();
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref("rooms/IDLE01").set(finished(room, ALICE)));
  });

  it("lets the abandoning player's own device settle the match against itself", async () => {
    // Without this the loser's client can do nothing, and a match both sides walked away
    // from is only ever closed by whoever happens to open the app first.
    const room = livingRoom();
    await seed(room);
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertSucceeds(bob.ref("rooms/IDLE01").set(finished(room, ALICE)));
  });

  it("lets a player concede at any time", async () => {
    const room = livingRoom({ lastMoveAt: Date.now() });
    await seed(room);
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertSucceeds(
      bob.ref("rooms/IDLE01").set({ ...finished(room, ALICE), endReason: "RESIGNATION" }),
    );
  });

  it("refuses ending a match that was played a moment ago", async () => {
    const room = livingRoom({ lastMoveAt: Date.now() });
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/IDLE01").set(finished(room, ALICE)));
  });

  it("refuses a finishing write that also moves a pawn", async () => {
    // The rule that pins the board across a finish used to compare the two subtrees with
    // .val(), which is never equal to anything — so it refused every resignation and every
    // timeout claim instead of refusing this.
    const room = livingRoom();
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    const cheated = finished(room, ALICE);
    cheated.board.players.PLAYER_ONE = { row: 0, column: 4, wallsRemaining: 9 };
    await assertFails(alice.ref("rooms/IDLE01").set(cheated));
  });

  it("refuses handing the win to the player who stopped moving", async () => {
    const room = livingRoom();
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/IDLE01").set(finished(room, BOB)));
  });

  it("refuses a claim from someone who was never in the room", async () => {
    const room = livingRoom();
    await seed(room);
    const mallory = testEnv.authenticatedContext("mallory-uid").database();
    await assertFails(mallory.ref("rooms/IDLE01").set(finished(room, "mallory-uid")));
  });

  it("still refuses a turn-clock claim before that clock has run out", async () => {
    const room = livingRoom({ turnDurationSeconds: 60, lastMoveAt: Date.now() - 30_000 });
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/IDLE01").set(finished(room, ALICE)));
  });
});

describe("the move clock decides on its own", () => {
  // A clock reaching zero ends the match there and then — nobody presses anything, and both
  // devices try. That means the write has to be allowed from *either* side: from the player
  // who was waiting, and from the one who ran out, whose phone may be the only one awake.
  const IDLE = 600_000;

  /** BOB is on the clock with sixty seconds a move, and has just used them all. */
  function timedOut(overrides = {}) {
    return {
      hostUserId: ALICE,
      guestUserId: BOB,
      status: "IN_PROGRESS",
      currentTurnUserId: BOB,
      version: 4,
      winnerUserId: "",
      endReason: "",
      createdAt: 1,
      lastMoveAt: Date.now() - 61_000,
      ranked: true,
      visibility: "PUBLIC",
      turnDurationSeconds: 60,
      browseKey: "PUBLIC_IN_PROGRESS",
      board: {
        currentPlayer: "PLAYER_TWO",
        status: "IN_PROGRESS",
        turnNumber: 5,
        players: {
          PLAYER_ONE: { row: 7, column: 4, wallsRemaining: 9 },
          PLAYER_TWO: { row: 1, column: 4, wallsRemaining: 9 },
        },
      },
      ...overrides,
    };
  }

  function finished(room, winner) {
    return {
      ...room,
      status: "FINISHED",
      winnerUserId: winner,
      endReason: "TIMEOUT",
      currentTurnUserId: "",
      version: room.version + 1,
      browseKey: "PUBLIC_FINISHED",
    };
  }

  async function seed(room) {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref("rooms/CLK01").set(room);
    });
  }

  it("lets the waiting player end a match the move clock has run out on", async () => {
    const room = timedOut();
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref("rooms/CLK01").set(finished(room, ALICE)));
  });

  it("lets the player who ran out end it against themselves", async () => {
    // Otherwise the result waits on the winner's phone being awake, and a match played
    // against someone who then put their phone down hangs until the ten-minute sweep.
    const room = timedOut();
    await seed(room);
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertSucceeds(bob.ref("rooms/CLK01").set(finished(room, ALICE)));
  });

  it("refuses the player who ran out paying the win to themselves", async () => {
    const room = timedOut();
    await seed(room);
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref("rooms/CLK01").set(finished(room, BOB)));
  });

  it("refuses the player on the clock ending it while they still have time", async () => {
    const room = timedOut({ lastMoveAt: Date.now() - 30_000 });
    await seed(room);
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref("rooms/CLK01").set(finished(room, ALICE)));
  });

  it("still lets an untimed room be settled by its own idle span", async () => {
    // Turning the move clock off must not take the ten-minute backstop with it.
    const room = timedOut({ turnDurationSeconds: 0, lastMoveAt: Date.now() - IDLE - 1_000 });
    await seed(room);
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertSucceeds(bob.ref("rooms/CLK01").set(finished(room, ALICE)));
  });

  it("accepts a room from a build that still writes the retired match clock", async () => {
    // The field is no longer required, no longer validated and no longer read. An older
    // handset still sends it, and its rooms have to keep opening.
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      db.ref("rooms/CLK02").set({
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
        totalDurationSeconds: 900,
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

describe("the terms a room was opened under", () => {
  // The move clock and the ranked flag are what the room is played for, and every rule that
  // settles a match reads them back off the room as it now stands. So a write that could
  // restate either is a write that decides the match after the fact: shrink the rival's clock
  // in the same breath as a move and the timeout claim two seconds later is honest arithmetic;
  // clear the flag while conceding and the loss was never rated.

  /** BOB is on the clock in a ranked room with two minutes a move, and has just moved. */
  function playing(overrides = {}) {
    return {
      hostUserId: ALICE,
      guestUserId: BOB,
      status: "IN_PROGRESS",
      currentTurnUserId: ALICE,
      version: 4,
      winnerUserId: "",
      endReason: "",
      createdAt: 1,
      lastMoveAt: Date.now() - 1_000,
      ranked: true,
      visibility: "PUBLIC",
      turnDurationSeconds: 120,
      browseKey: "PUBLIC_IN_PROGRESS",
      board: {
        currentPlayer: "PLAYER_ONE",
        status: "IN_PROGRESS",
        turnNumber: 5,
        players: {
          PLAYER_ONE: { row: 7, column: 4, wallsRemaining: 10 },
          PLAYER_TWO: { row: 1, column: 4, wallsRemaining: 10 },
        },
      },
      ...overrides,
    };
  }

  /** ALICE steps forward and hands the turn to BOB. */
  function moved(room, overrides = {}) {
    return {
      ...room,
      version: room.version + 1,
      lastMoveAt: Date.now(),
      currentTurnUserId: BOB,
      board: {
        ...room.board,
        currentPlayer: "PLAYER_TWO",
        turnNumber: room.board.turnNumber + 1,
        players: {
          ...room.board.players,
          PLAYER_ONE: { row: 6, column: 4, wallsRemaining: 10 },
        },
      },
      ...overrides,
    };
  }

  function conceded(room, winner, reason, overrides = {}) {
    return {
      ...room,
      status: "FINISHED",
      winnerUserId: winner,
      endReason: reason,
      currentTurnUserId: "",
      version: room.version + 1,
      browseKey: "PUBLIC_FINISHED",
      ...overrides,
    };
  }

  async function seed(room) {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref("rooms/TRM001").set(room);
    });
  }

  it("takes a move that leaves the clock alone", async () => {
    const room = playing();
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref("rooms/TRM001").set(moved(room)));
  });

  it("refuses a move that shortens the rival's clock", async () => {
    // The whole match in one write: a legal move that also cuts two minutes to one second,
    // after which the mover's own timeout claim is arithmetic the rules agree with.
    const room = playing();
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/TRM001").set(moved(room, { turnDurationSeconds: 1 })));
  });

  it("refuses a joiner who shortens the clock on the way in", async () => {
    // The same trick one move earlier, and available to the guest before the host has played
    // at all.
    const waiting = playing({
      guestUserId: "",
      status: "WAITING",
      version: 0,
      lastMoveAt: 1,
      board: {
        currentPlayer: "PLAYER_ONE",
        status: "IN_PROGRESS",
        turnNumber: 1,
        players: {
          PLAYER_ONE: { row: 8, column: 4, wallsRemaining: 10 },
          PLAYER_TWO: { row: 0, column: 4, wallsRemaining: 10 },
        },
      },
    });
    await seed(waiting);
    const bob = testEnv.authenticatedContext(BOB).database();
    const join = {
      ...waiting,
      guestUserId: BOB,
      status: "IN_PROGRESS",
      lastMoveAt: Date.now(),
      browseKey: "PUBLIC_IN_PROGRESS",
    };
    await assertFails(bob.ref("rooms/TRM001").set({ ...join, turnDurationSeconds: 1 }));
    await assertSucceeds(bob.ref("rooms/TRM001").set(join));
  });

  it("refuses a timeout claim that also clears the ranked flag", async () => {
    // Written by the loser, which is the arm a modified client would use to stop ever losing
    // rating again: run the clock out, concede, and file the game as a casual one.
    const room = playing({ lastMoveAt: Date.now() - 121_000, currentTurnUserId: BOB });
    await seed(room);
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(
      bob.ref("rooms/TRM001").set(conceded(room, ALICE, "TIMEOUT", { ranked: false })),
    );
    await assertSucceeds(bob.ref("rooms/TRM001").set(conceded(room, ALICE, "TIMEOUT")));
  });

  it("refuses a resignation that also clears the ranked flag", async () => {
    const room = playing();
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/TRM001").set(conceded(room, BOB, "RESIGNATION", { ranked: false })),
    );
    await assertSucceeds(alice.ref("rooms/TRM001").set(conceded(room, BOB, "RESIGNATION")));
  });

  it("refuses a finish that promotes a casual match to a rated one", async () => {
    // The other direction, and the one that takes rating off a player who was told the game
    // did not count — which is every match with a guest in it.
    const room = playing({
      ranked: false,
      lastMoveAt: Date.now() - 121_000,
      currentTurnUserId: BOB,
    });
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/TRM001").set(conceded(room, ALICE, "TIMEOUT", { ranked: true })),
    );
    await assertSucceeds(alice.ref("rooms/TRM001").set(conceded(room, ALICE, "TIMEOUT")));
  });
});

describe("a win has to be walked to", () => {
  // A normal win used to be checked against one coordinate: the winning pawn standing on its
  // goal row. Nothing said where it had come from, so the first move of a match could put it
  // there — and the worker deliberately re-derives nothing, because it trusts these rules to
  // have proved it. The board a winning write leaves behind has to be one move away from the
  // board it found.

  /** ALICE hosts from seat one and is to move, with her pawn `row` squares from home. */
  function playing(row, overrides = {}) {
    return {
      hostUserId: ALICE,
      guestUserId: BOB,
      status: "IN_PROGRESS",
      currentTurnUserId: ALICE,
      version: 4,
      winnerUserId: "",
      endReason: "",
      createdAt: 1,
      lastMoveAt: Date.now() - 1_000,
      ranked: true,
      visibility: "PUBLIC",
      turnDurationSeconds: 60,
      browseKey: "PUBLIC_IN_PROGRESS",
      board: {
        currentPlayer: "PLAYER_ONE",
        status: "IN_PROGRESS",
        turnNumber: 5,
        players: {
          PLAYER_ONE: { row, column: 4, wallsRemaining: 8 },
          PLAYER_TWO: { row: 4, column: 4, wallsRemaining: 7 },
        },
      },
      ...overrides,
    };
  }

  /** ALICE's pawn lands on row zero, wherever it started. */
  function won(room, seatOne) {
    return {
      ...room,
      status: "FINISHED",
      endReason: "NORMAL",
      currentTurnUserId: "",
      winnerUserId: ALICE,
      version: room.version + 1,
      lastMoveAt: Date.now(),
      browseKey: "PUBLIC_FINISHED",
      board: {
        ...room.board,
        status: "PLAYER_ONE_WON",
        players: { ...room.board.players, PLAYER_ONE: { ...seatOne, row: 0 } },
      },
    };
  }

  /** ALICE takes an ordinary turn, leaving the board however `board` says. */
  function moved(room, board) {
    return {
      ...room,
      version: room.version + 1,
      lastMoveAt: Date.now(),
      currentTurnUserId: BOB,
      board: {
        ...room.board,
        currentPlayer: "PLAYER_TWO",
        turnNumber: room.board.turnNumber + 1,
        players: { ...room.board.players, ...board },
      },
    };
  }

  async function seed(room) {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref("rooms/WIN001").set(room);
    });
  }

  it("takes the last step onto the goal row", async () => {
    const room = playing(1);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      alice.ref("rooms/WIN001").set(won(room, { column: 4, wallsRemaining: 8 })),
    );
  });

  it("takes the jump over a rival standing in the way", async () => {
    const room = playing(2);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      alice.ref("rooms/WIN001").set(won(room, { column: 4, wallsRemaining: 8 })),
    );
  });

  it("takes the diagonal a blocked jump falls back to", async () => {
    const room = playing(1);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      alice.ref("rooms/WIN001").set(won(room, { column: 3, wallsRemaining: 8 })),
    );
  });

  it("refuses a pawn that teleports to the goal row", async () => {
    // The whole defect in one write: a pawn on its own back rank declaring the match won on
    // the first turn it gets.
    const room = playing(8);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/WIN001").set(won(room, { column: 4, wallsRemaining: 8 })),
    );
  });

  it("refuses a win from three squares out", async () => {
    // Two is the whole reach: a step, a straight jump, or the diagonal. Three is a teleport
    // with better manners.
    const room = playing(3);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/WIN001").set(won(room, { column: 4, wallsRemaining: 8 })),
    );
  });

  it("refuses a diagonal from two squares out", async () => {
    const room = playing(2);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/WIN001").set(won(room, { column: 3, wallsRemaining: 8 })),
    );
  });

  it("refuses a winning move that also refills the winner's walls", async () => {
    const room = playing(1);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/WIN001").set(won(room, { column: 4, wallsRemaining: 10 })),
    );
  });

  it("refuses a winning move that also moves the rival's pawn", async () => {
    const room = playing(1);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    const forged = won(room, { column: 4, wallsRemaining: 8 });
    forged.board.players.PLAYER_TWO = { row: 0, column: 0, wallsRemaining: 0 };
    await assertFails(alice.ref("rooms/WIN001").set(forged));
  });

  it("refuses the seat that is not on the clock winning", async () => {
    const room = playing(4, {
      currentTurnUserId: BOB,
      board: {
        currentPlayer: "PLAYER_TWO",
        status: "IN_PROGRESS",
        turnNumber: 5,
        players: {
          PLAYER_ONE: { row: 1, column: 4, wallsRemaining: 8 },
          PLAYER_TWO: { row: 4, column: 4, wallsRemaining: 7 },
        },
      },
    });
    await seed(room);
    const bob = testEnv.authenticatedContext(BOB).database();
    const forged = won(room, { column: 4, wallsRemaining: 8 });
    await assertFails(bob.ref("rooms/WIN001").set(forged));
  });

  it("takes an ordinary step and an ordinary wall", async () => {
    const room = playing(5);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      alice.ref("rooms/WIN001").set(moved(room, {
        PLAYER_ONE: { row: 4, column: 4, wallsRemaining: 8 },
      })),
    );
    await seed(room);
    await assertSucceeds(
      alice.ref("rooms/WIN001").set(moved(room, {
        PLAYER_ONE: { row: 5, column: 4, wallsRemaining: 7 },
      })),
    );
  });

  it("refuses a move that refills the mover's wall supply", async () => {
    const room = playing(5);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/WIN001").set(moved(room, {
        PLAYER_ONE: { row: 4, column: 4, wallsRemaining: 10 },
      })),
    );
  });

  it("refuses a move that empties the rival's wall supply", async () => {
    const room = playing(5);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/WIN001").set(moved(room, {
        PLAYER_ONE: { row: 4, column: 4, wallsRemaining: 8 },
        PLAYER_TWO: { row: 4, column: 4, wallsRemaining: 0 },
      })),
    );
  });

  it("refuses a move that walks the mover's pawn across the board", async () => {
    const room = playing(5);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/WIN001").set(moved(room, {
        PLAYER_ONE: { row: 1, column: 4, wallsRemaining: 8 },
      })),
    );
  });

  it("refuses a wall paid for with a step", async () => {
    // A turn is one thing or the other. Doing both is two turns for the price of one.
    const room = playing(5);
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/WIN001").set(moved(room, {
        PLAYER_ONE: { row: 4, column: 4, wallsRemaining: 7 },
      })),
    );
  });

  it("refuses a pawn stepped off the board", async () => {
    const room = playing(0, {
      board: {
        currentPlayer: "PLAYER_ONE",
        status: "IN_PROGRESS",
        turnNumber: 5,
        players: {
          PLAYER_ONE: { row: 0, column: 0, wallsRemaining: 8 },
          PLAYER_TWO: { row: 4, column: 4, wallsRemaining: 7 },
        },
      },
    });
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/WIN001").set(moved(room, {
        PLAYER_ONE: { row: 0, column: -1, wallsRemaining: 8 },
      })),
    );
  });
});

describe("reporting a player", () => {
  // The app shows two things one player typed to players who have never met them: the
  // username and the room name. Play requires a way to report either, and a report is worth
  // nothing if the account being reported can see it or delete it — so this node is written
  // by anybody signed in, about somebody else, and read by nobody at all.

  function report(overrides = {}) {
    return { reason: "OFFENSIVE_NAME", createdAt: SERVER_TIME, ...overrides };
  }

  it("lets a signed-in player report somebody else", async () => {
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref(`contentReports/${BOB}/${ALICE}`).set(report()));
  });

  it("takes a report about a room name, with the room it was seen in", async () => {
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(
      alice.ref(`contentReports/${BOB}/${ALICE}`).set(
        report({ reason: "OFFENSIVE_ROOM_NAME", roomCode: "AB3D5F" }),
      ),
    );
  });

  it("refuses a report filed under somebody else's name", async () => {
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`contentReports/${BOB}/${BOB}`).set(report()));
  });

  it("refuses reporting yourself", async () => {
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`contentReports/${ALICE}/${ALICE}`).set(report()));
  });

  it("refuses an anonymous report", async () => {
    const anonymous = testEnv.unauthenticatedContext().database();
    await assertFails(anonymous.ref(`contentReports/${BOB}/${ALICE}`).set(report()));
  });

  it("refuses a reason outside the vocabulary", async () => {
    // The same argument as the canned messages: a reason nobody chose from a list is free
    // text, and free text about another player is the thing this node exists to avoid.
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref(`contentReports/${BOB}/${ALICE}`).set(report({ reason: "he is a dog" })));
  });

  it("refuses a field beside the ones a report has", async () => {
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref(`contentReports/${BOB}/${ALICE}`).set(report({ note: "hello" })),
    );
    await assertFails(alice.ref(`contentReports/${BOB}/${ALICE}/note`).set("hello"));
  });

  it("refuses a stamp the phone chose", async () => {
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref(`contentReports/${BOB}/${ALICE}`).set(report({ createdAt: 1 })),
    );
  });

  it("refuses withdrawing a report once it is filed", async () => {
    const alice = testEnv.authenticatedContext(ALICE).database();
    await alice.ref(`contentReports/${BOB}/${ALICE}`).set(report());
    await assertFails(alice.ref(`contentReports/${BOB}/${ALICE}`).remove());
  });

  it("refuses everyone reading them, the reporter included", async () => {
    const alice = testEnv.authenticatedContext(ALICE).database();
    await alice.ref(`contentReports/${BOB}/${ALICE}`).set(report());
    await assertFails(alice.ref(`contentReports/${BOB}/${ALICE}`).get());
    const bob = testEnv.authenticatedContext(BOB).database();
    await assertFails(bob.ref(`contentReports/${BOB}`).get());
  });
});

describe("match reports", () => {
  it("lets the reporter read a match id that does not exist yet", async () => {
    // reportMatch runs in a transaction, and a transaction reads before it writes. Without
    // this the first read of a new match id was denied and no rating ever moved.
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(db.ref("matchResults/brand-new-id").get());
  });

  it("still refuses reading a stranger's finished match", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref("matchResults/m9").set({
        roomCode: "ABC123", hostUid: BOB, guestUid: "carol-uid", winnerUid: BOB,
        endReason: "NORMAL", ranked: true, turnCount: 10,
        reportedAt: 1, reportedBy: BOB, state: "PENDING",
      });
    });
    const db = testEnv.authenticatedContext(ALICE).database();
    await assertFails(db.ref("matchResults/m9").get());
  });
});

describe("what a player is allowed to say", () => {
  // Canned messages live inside the room, one slot per player, so they are swept away with
  // it and the cap is structural rather than pruned. Everything that keeps them harmless is
  // here: only the two players may write, only under their own id, only one of the fourteen
  // known keys, and not fifty a second.
  //
  // The move path matters as much as the message path. A move is written as the whole room
  // node, so every message in it is rewritten on every turn — which must keep working, and
  // must not become a way to put words in the rival's mouth.

  /** A match under way with ALICE hosting from seat one and to move. */
  function playing(overrides = {}) {
    return {
      hostUserId: ALICE,
      guestUserId: BOB,
      status: "IN_PROGRESS",
      currentTurnUserId: ALICE,
      version: 4,
      winnerUserId: "",
      endReason: "",
      createdAt: 1,
      lastMoveAt: 1,
      ranked: true,
      visibility: "PUBLIC",
      turnDurationSeconds: 0,
      browseKey: "PUBLIC_IN_PROGRESS",
      board: {
        currentPlayer: "PLAYER_ONE",
        status: "IN_PROGRESS",
        turnNumber: 5,
        players: {
          PLAYER_ONE: { row: 8, column: 4, wallsRemaining: 10 },
          PLAYER_TWO: { row: 0, column: 4, wallsRemaining: 10 },
        },
      },
      ...overrides,
    };
  }

  /** ALICE steps forward, which is the write that carries the whole room back up. */
  function moved(room) {
    return {
      ...room,
      version: room.version + 1,
      lastMoveAt: Date.now(),
      currentTurnUserId: BOB,
      board: {
        ...room.board,
        currentPlayer: "PLAYER_TWO",
        turnNumber: room.board.turnNumber + 1,
        players: {
          ...room.board.players,
          PLAYER_ONE: { row: 7, column: 4, wallsRemaining: 10 },
        },
      },
    };
  }

  function said(key, at = SERVER_TIME) {
    return { key, at };
  }

  async function seed(room) {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.database().ref("rooms/CHAT01").set(room);
    });
  }

  it("lets a player say one of the things there are to say", async () => {
    await seed(playing());
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref("rooms/CHAT01/chat/" + ALICE).set(said("GOOD_LUCK")));
  });

  it("refuses a word that is not in the vocabulary", async () => {
    // This is the whole reason the feature is not user-generated content: the set of things
    // that can be written is fixed here, not merely in the app that writes them.
    await seed(playing());
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/CHAT01/chat/" + ALICE).set(said("YOU_PLAY_LIKE_A_DOG")));
  });

  it("refuses a player speaking as their rival", async () => {
    await seed(playing());
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/CHAT01/chat/" + BOB).set(said("SORRY")));
  });

  it("refuses a message from somebody who is not in the room", async () => {
    await seed(playing());
    const mallory = testEnv.authenticatedContext("mallory-uid").database();
    await assertFails(
      mallory.ref("rooms/CHAT01/chat/mallory-uid").set(said("NICE_MOVE")),
    );
  });

  it("refuses anything but the two fields a message has", async () => {
    // Without this a modified client writes its own field beside the key and has the free
    // text the closed vocabulary exists to avoid.
    await seed(playing());
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/CHAT01/chat/" + ALICE).set({ ...said("THANKS"), text: "hello" }),
    );
    await assertFails(alice.ref("rooms/CHAT01/chat/" + ALICE + "/text").set("hello"));
  });

  it("refuses free text buried underneath one of those two fields", async () => {
    // The subtle way in, and the one a reading of the rules can talk itself out of: a
    // parent's `.validate` is never evaluated for a write to something below it, so the rule
    // on `key` does not see a write to `key/note`. What stands there is `$otherFields`, which
    // matches at every depth. Anything looser and the vocabulary is closed at the top and
    // open one level down, which is not closed at all.
    await seed(playing({ chat: { [ALICE]: said("GOOD_LUCK", Date.now() - 30_000) } }));
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/CHAT01/chat/" + ALICE + "/key/note").set("hello"));
    await assertFails(alice.ref("rooms/CHAT01/chat/" + ALICE + "/at/note").set("hello"));
    await assertFails(
      alice.ref("rooms/CHAT01/chat/" + ALICE).set({ key: { note: "hello" }, at: SERVER_TIME }),
    );
  });

  it("refuses a player writing the messages node rather than their slot in it", async () => {
    // Permission is granted at the slot and never flows upward, so this is refused even
    // though every value in it would have been accepted one level down. That is what keeps
    // the two slots two: a write here is a write to the rival's as well, and clearing what
    // they said is not this player's to do.
    await seed(playing());
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(
      alice.ref("rooms/CHAT01/chat").set({ [ALICE]: said("GOOD_LUCK") }),
    );
  });

  it("refuses a message a phone dated itself", async () => {
    // The gap between two messages is measured against the stamp on the last one, so a
    // device that could write its own could backdate it and send as many as it liked.
    await seed(playing());
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/CHAT01/chat/" + ALICE).set(said("OOPS", Date.now())));
  });

  it("refuses a second message sent straight after the first", async () => {
    await seed(playing());
    const alice = testEnv.authenticatedContext(ALICE).database();
    await alice.ref("rooms/CHAT01/chat/" + ALICE).set(said("GOOD_LUCK"));
    await assertFails(alice.ref("rooms/CHAT01/chat/" + ALICE).set(said("NICE_MOVE")));
  });

  it("takes the next one once the gap has passed", async () => {
    await seed(playing({ chat: { [ALICE]: said("GOOD_LUCK", Date.now() - 30_000) } }));
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref("rooms/CHAT01/chat/" + ALICE).set(said("NICE_MOVE")));
  });

  it("refuses swapping the key without a fresh stamp", async () => {
    // Writing the key on its own would otherwise slip past the gap, which is measured on the
    // stamp beside it.
    await seed(playing({ chat: { [ALICE]: said("GOOD_LUCK", Date.now() - 30_000) } }));
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/CHAT01/chat/" + ALICE + "/key").set("SORRY"));
  });

  it("refuses a message in a room nobody is playing", async () => {
    await seed(playing({ status: "WAITING", guestUserId: "", currentTurnUserId: ALICE }));
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertFails(alice.ref("rooms/CHAT01/chat/" + ALICE).set(said("GOOD_LUCK")));
  });

  it("still takes the last word from a match that has just ended", async () => {
    // "Good game" is said at the end, and the end may land between the sheet opening and the
    // tap. A message refused because the winning move arrived first would be the one message
    // players most want to send.
    await seed(playing({ status: "FINISHED", winnerUserId: BOB, currentTurnUserId: "" }));
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref("rooms/CHAT01/chat/" + ALICE).set(said("GOOD_GAME")));
  });

  it("carries the rival's message through a move untouched", async () => {
    // A move rewrites the whole room, the rival's message included. If that were refused,
    // one message would end the match: nobody could move again.
    const spoken = Date.now() - 30_000;
    const room = playing({ chat: { [BOB]: said("GOOD_LUCK", spoken) } });
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    await assertSucceeds(alice.ref("rooms/CHAT01").set(moved(room)));
  });

  it("refuses a move that rewrites what the rival said", async () => {
    // Same stamp, different words: the move is the one write a player is entitled to make
    // over the whole room, and it must not be a way to speak for the other seat.
    const spoken = Date.now() - 30_000;
    const room = playing({ chat: { [BOB]: said("GOOD_LUCK", spoken) } });
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    const forged = { ...moved(room), chat: { [BOB]: said("SORRY", spoken) } };
    await assertFails(alice.ref("rooms/CHAT01").set(forged));
  });

  it("refuses a move that puts words in a rival who has said nothing", async () => {
    const room = playing();
    await seed(room);
    const alice = testEnv.authenticatedContext(ALICE).database();
    const forged = { ...moved(room), chat: { [BOB]: said("OOPS", Date.now()) } };
    await assertFails(alice.ref("rooms/CHAT01").set(forged));
  });
});
