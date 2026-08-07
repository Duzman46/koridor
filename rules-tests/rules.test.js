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
