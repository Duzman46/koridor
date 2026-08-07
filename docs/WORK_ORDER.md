# Work order — round 2

Everything the owner asked for on 2026-08-07, with the four decisions they made when asked.
Kept in the repo so it survives a context reset and so every agent works from one list.

## Decisions taken (do not re-litigate)

1. **Blue always moves first.** Colour is no longer paint over a fixed seat — choosing red
   means playing second. The host↔seat binding that the security rules and the rating worker
   assume must change with it.
2. **Quick match is a queue, paired by the phones with the server as a backstop.** Pressing
   quick match must NOT create a room. It writes the player into a waiting list with their
   rating; a client pairs the two closest-rated waiters in a transaction and creates the room
   for both; the scheduled worker pairs anyone left waiting and clears stale entries.
3. **Display name is removed entirely.** One name: the username. Everywhere.
4. **Leaving forfeits online matches only.** Bot and hot-seat matches just confirm.

## The list

### Protocol / server
- [x] Blue moves first; colour picks the seat. Rules, worker, `OnlineRoom.playerFor/userFor`.
- [x] Quick match queue (see decision 2). Rating-proximity preferred.
- [x] Drop the total-match clock. Only a per-turn clock remains.
- [x] A turn clock running out ends the match immediately — no "claim the win" button.
- [x] Leaving an online match ends it: opponent wins and is told "your rival left".
- [x] Rematch and game invites travel on a live channel both players already listen to.

### Game screen
- [x] Last five seconds of a turn: the clock turns red and a warning sound plays.
- [x] Tap the opponent to open their profile and send a friend request from there.
- [x] Back in an online match warns that leaving loses it.
- [x] Only the username is shown — never a separate display name.

### After the match
- [x] "Play again" becomes **Rövanş** in a two-player match, and asks the opponent rather
      than restarting alone. The opponent sees a bar at the top: accept or decline.
- [x] If they decline (or while waiting) the loser/winner can take another game or go home.

### Menus
- [x] Friends reachable from the main menu, top-left.
- [x] From a friend: "invite to a game" — creates a room and sends them an invite.
- [x] An incoming invite shows as a bar at the top of whatever screen you are on:
      accept / decline. Same mechanism as the rematch request.
- [x] Leaderboard rows open a profile, and a friend request can be sent from there.
- [x] Create-room: the "who can join" control is removed entirely.
- [x] Open rooms refresh themselves on a timer; no manual pull.

## What was verified, and how

Every box above was checked against the code, not against a report. The whole list is ticked,
so what remains is the part no machine can sign off: `docs/MANUAL_TESTS.md` sections D0 and
D4–D7 cover the request channel, the rematch, the friends entry point and the two ways into
another player's profile, and none of them can be run without two real handsets.

Automated evidence behind the ticks:
`./gradlew.bat :app:test :app:lintRelease :app:assembleRelease` (186 JVM tests),
`rules-tests` (108 emulator tests against `database.rules.json`),
`worker` (9 unit, 30 end-to-end).

---

# Round 3 — merging three parallel branches

Three engineers worked the same tree at once (home screen, account flows, leaderboard
eligibility). This round is the join: run everything against the combined result, and fix what
only shows up once the three sit together.

## Verified green, on the merged tree

- [x] `./gradlew.bat :app:test :app:lintRelease :app:assembleRelease` — **202 JVM tests**, no
      failures, no skips; `lintRelease` clean of errors.
- [x] `rules-tests` — **129 emulator tests**, up from 120: nine new ones for account erasure.
- [x] `worker` — 9 unit, **36 end-to-end**.
- [x] `functions` — 9 unit plus its rating end-to-end. Not on the required list, but
      `functions/src/index.ts` is a live second writer of the same rows and was edited this
      round, so leaving it unrun would have meant shipping it unverified.

## Rules: no change, and now a test that says why

The account engineer was asked to describe rather than make the `database.rules.json` change
they needed. Their answer was that they need none, and their line-by-line audit of the erasure
paths holds — every claim in it was replayed against the emulator rather than taken on trust.

What was missing was any test at all pinning it. Account deletion is the one flow whose
correctness rests entirely on a rules fact that is invisible from the client: write permission
only ever flows downwards, so `friendships/$owner/$other` grants nothing at
`friendships/$owner`, and a multi-path update naming the parent is refused in full — silently,
taking the rows that *were* allowed down with it. That is exactly the bug that shipped, and
nothing would have caught it coming back.

- [x] `erasing an account` covers all eight leaf paths the client writes, both refused parent
      forms, the atomic-update shape that caused the original failure (asserting the allowed
      rows survive the refusal), the block that must outlive the blocked account, and four
      cross-player refusals.
- [x] `database.rules.json` parses; every branch new this round has a test on both sides —
      the query-shaped `/users` and `/rooms` reads, `leaderboardRating`, the invite `kind`
      and sender-retraction branches, `hostSeat`, `matchmaking`, and the match-report read.

## Fixed in the join

- [x] **The username gate was still open on the sign-in path.** `HandleEntryEvents` collects in
      a `LaunchedEffect` keyed on the view model, so it outlives recomposition — and read a
      session captured when the welcome screen was first drawn, before the player existed. On a
      device where anyone had finished the tutorial, the next account signing in was routed
      straight home, unnamed. The account work closed this hole at the tutorial hand-off; this
      is the same hole one screen earlier. Now read through `rememberUpdatedState`.
- [x] **Dead code from the home rework.** `SelectionCard`, `OfflineState`, `OfflineBanner`,
      `RoomPair`, `DestinationChips` and everything only they reached — `RoomButton`,
      `DestinationChip`, `Destination`, all of `PieceDepth.kt`, and five `Dimens` tokens.
- [x] **Orphaned strings.** `state_offline_title` and `state_offline_message` went with the
      offline composables, out of all ten locales.
- [x] **`KoridorBlock` recomposed on every frame of a press**, because the animated travel was
      read into the dp overload of `Modifier.offset` during composition. Read in the layout
      phase instead, which is what the composable's own KDoc already claimed.
- [x] The game screen's title was the only place the app named itself with a literal instead of
      `app_name` — harmless while the name holds, and one grep away from being missed if it
      ever changes.

## Audited clean, no change needed

- [x] Every `R.string` reference resolves; all ten locales carry the same 289 translatable
      entries; nothing is left orphaned. The handful of entries identical to English are
      cognates — *Avatar*, *Tutorial*, *Online* — not untranslated text.
- [x] No unused imports and no orphaned functions or composables remain anywhere in
      `app/src/main` or `app/src/test`. No `TODO`/`FIXME`.

## Still open — read before shipping

1. **A guest who links an account is put on the all-time board before being asked to name
   themselves.** Linking runs `ensureProfile`, which claims `leaderboardRating`, but nothing
   routes the player through the entry gate at that moment — `AccountEvent.Linked` deliberately
   navigates nowhere. Until the next cold start they are publicly listed under the generated
   `guest_######` name. It self-corrects on relaunch. Fixing it means either navigating away
   from the account screen on a state that may not have caught up yet, or moving when the board
   rating is claimed; both are behavioural changes that want a handset to sign off, so neither
   was made blind.
2. **Erasure misses invites sent to non-friends.** `deleteSocialData` walks the friend list to
   find the other players' nodes to clean, but a rematch goes to an opponent who need not be a
   friend, so `invites/<opponent>/<deleted uid>` survives. Invisible to players — the client
   filters expired entries and these carry a TTL — but the row is never collected.
3. **Not verifiable here:** no device is attached. `docs/MANUAL_TESTS.md` C7 covers the gate
   fix above, C1b the guest's view of the leaderboard; neither can be signed off from a build.
