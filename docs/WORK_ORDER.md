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

---

# Round 4 — the join, verified end to end

Three engineers again worked the same tree at once: the empty leaderboard and the back button,
the home screen and the palette and the icon, and the guest's name and the account collision.
This round runs everything against the combined result, settles the one database question a
described-but-not-made instruction left outstanding, and audits the four things that only go
wrong once three branches sit together.

## Verified green, on the merged tree

- [x] `./gradlew.bat :app:test :app:lintRelease :app:assembleRelease` — **221 JVM tests** in 26
      classes, no failures, no skips. `lintRelease` reports **no errors**; the four warnings it
      does report are pre-existing and none is about this round's code: two `UnusedAttribute`
      notes on manifest attributes that only take effect above `minSdk`, one newer-AGP notice,
      and `ObsoleteSdkInt` on `mipmap-anydpi-v26`, which is redundant now that `minSdk` is 26 but
      is the folder every adaptive icon ships in.
- [x] `rules-tests` — **134 emulator tests**, up from 129: two for the rank scan's repinned read
      and the maintenance subtree, three new ones below.
- [x] `worker` — 9 unit, **46 end-to-end**, up from 42.

## The database change that was described rather than made

The guest engineer was asked to describe any `database.rules.json` change their work needed
instead of making it. Their answer was that it needs none, and the audit behind it holds — every
line of it was replayed against the emulator rather than taken on trust:

- `/users/$uid` `.write` ends in `(data.exists() && !newData.exists())` under `auth.uid == $uid`,
  and its `.validate` opens with `!newData.exists()`.
- `/usernames/$normalizedUsername` `.write` admits `data.exists() && data.val() == auth.uid`, and
  its `.validate` likewise begins `!newData.exists()`.
- `/usersPrivate/$uid` is owner-write outright.

So `database.rules.json` is unchanged by this round's account work. What was missing was anything
holding the rules to it, and the hand-over is the one flow where being wrong is unrecoverable:
the guest's rows are deleted *before* the other account signs in, because a moment later that uid
is unreachable from every device and whatever is left under it is left for good.

- [x] `handing a guest over to an account that already exists` — the three writes
      `deleteAccountData` makes, in the order it makes them, as the guest; that a *generated*
      `guest_######` name is inside what the username key will validate, so it can be given back
      at all; that the profile is still deletable once its name has already been released, which
      is the ordering the flow is forced into and the one a tidy-up of the create rule would
      silently break; and that the account being signed into cannot do any of it on the guest's
      behalf, which is why none of it can wait until afterwards.

## Fixed in the join

- [x] **The board-index back-fill could wedge without saying so.** The walk's cursor was the last
      entry of a parsed JSON object. Which keys a page holds is exact — the first page-full at or
      above the cursor — but the REST API answers a query with an object and promises no order
      once a parser has been through it, so "last entry" is not reliably "highest key", and a
      cursor that lands short re-reads the same page for ever: nothing indexed after the first
      lap, and a clean-looking run reported every minute. It now takes the highest key in the
      page, which cannot land short. That branch was also entirely untested — every profile in
      the end-to-end fitted in one page, so only the wrap-around arm ever ran, while a real
      database only ever takes the other one. Now covered: a tree larger than one page, walked to
      the end, asserting that every profile is taken on, that no lap left the cursor where the
      lap before it did, and that the last key in order is not stepped over.
- [x] **`docs/RELEASE.md` still asked the tester to confirm the behaviour this round removed** —
      "test back and Home-icon exit confirmation in AI, local two-player, and online modes". Back
      no longer confirms anything offline, so the checklist would have had the owner re-file the
      fix as a bug. It now names what each mode is expected to do, including the online room
      nobody has joined, which leaves at once and still has to release the seat.
- [x] **`README.md` named Cloud Functions as the server.** Rating, room upkeep and the queue
      backstop moved to the scheduled worker two rounds ago and the README was never told: it
      listed Cloud Functions in the stack, walked the reader through `firebase deploy --only
      functions` as the way to make ratings work at all, and left `worker/` out of the test
      commands entirely. It now says there are two implementations of one job, which of them is
      deployed, that deploying both is wrong, and which of the two picks up reports that were
      already waiting when it started.
- [x] **Two comments that had outlived what they describe.** `KoridorGlyphs` explained its
      monochrome rule by "keeping the amber inside the well" — there is no amber left anywhere in
      the app. `SeatColors` said both goal rows are the seat hue "lifted", which is true of red
      and not of blue, whose goal row is the pawn's own value.

## Audited clean, no change needed

- [x] **Strings.** `values/strings.xml` holds 297 entries, 296 of them translatable — `app_name`
      correctly is not — and each of the other nine locales carries exactly those 296, no more
      and no fewer. Every `R.string`, `R.plurals` and `@string/` reference under `app/src`
      resolves, and nothing is defined that nothing reads. The seven keys the account work added
      are present and properly written in all ten. What matches English is format strings —
      `%1$d:%2$02d`, `%1$d/2`, `%1$d+`, `%1$s · %2$d`, `%1$d%%` — and cognates that are the real
      word in that language: *Avatar*, *Tutorial*, *Online*, *Optional*, *Horizontal*, *Version*.
- [x] **Imports and dead code.** No unused import anywhere in `app/src`: the only imports with no
      textual use are `runtime.getValue`/`setValue`, which property delegation needs and never
      names. Of 202 top-level declarations none is unreachable — every private composable,
      function and constant is reached from its own file, and every internal or public one from
      somewhere in the tree. No `TODO`/`FIXME`, and no KDoc `[link]` naming something that is not
      there.
- [x] **The home screen fits 360x740 dp and the board is still whole.** Read as arithmetic
      against the constants themselves rather than eyeballed. Fixed chrome sums to **506 dp**:
      12 + 48 utility row (44 dp of crest raised by Material's 48 dp touch floor, which `Surface`
      applies for us) + 16 + 36 wordmark (30 sp glyphs in the 1.2 line box it now sets explicitly)
      + 8 + 16 + 74 Play (72 plus 2 of press travel) + 4 x (12 + 56 + 2) + 16. The panel takes the
      remainder, so the column sums to exactly the window: 740 = 506 + 234 with no banner,
      506 + 184 + 50, and 506 + 144 + 90 at the adaptive banner's maximum. The panel can go
      neither negative nor unbounded — `heroHeight` clamps it to 133.3–255.4 dp — and it is the
      *floor* of that clamp which binds, so a row added to the screen without being added to the
      budget makes `HomeLayoutBudgetTest` fail rather than making the screen scroll quietly.
      The board survives the squeeze: in the 184 dp case the board's side is 173.0 dp with 5.5 dp
      of well above and below it, so all nine ranks — both goal rows included — are inside the
      panel, and both pawns stand on interior squares at (5,3) and (3,4). Board, gap and ten-slot
      rack span 209.8 dp of the 320 dp column, centred, and the rack's last bar ends exactly
      where the board does. Nothing is clipped by the panel's 28 dp corner radius at either end
      of the clamp. The two requirements do pull against each other, and what resolves them is
      structural rather than lucky: the tallest shape the panel may take is *derived* from the
      width the artwork needs, so the panel can never be taller than the board can fill.
- [x] **The launcher bitmaps are the vector.** Measured out of the PNGs rather than trusted: at
      every one of the five densities the wall bars land on canvas units 27–35 and 73–81 and the
      pawn's head on 43–65, which is the vector's own geometry to within a pixel of resampling,
      and the rounded alpha mask a legacy bitmap has to bring itself measures the 24-unit radius
      it is meant to. 48 / 72 / 96 / 144 / 192 px are all present.
      `store-assets/play-icon-512.png` is 512x512, RGB with no alpha channel at all, opaque in
      all four corners and full-bleed — which is what Google Play requires, and what a rounded or
      transparent icon is rejected for.

## Still open — read before shipping

Round 3's list is settled apart from one entry: item 1, the guest put on the board before being
asked to name themselves, is what `AccountEvent.Linked(needsUsername)` now routes. Item 2 stands,
repeated below.

1. **`functions/src/index.ts` has no board-index back-fill.** It is the Cloud Functions
   implementation of the same server and a live second writer of `leaderboardRating`, but it is
   not what is deployed — `README.md` now says so plainly, and says that deploying both is wrong.
   Deploy `worker/` and there is nothing here. Deploy `functions/` instead and every account
   written before the index existed stays off the all-time board with nothing to report it, which
   is the exact bug this round fixed on the other side.
2. **Erasure misses invites sent to non-friends.** Unchanged from round 3: `deleteSocialData`
   walks the friend list, and a rematch goes to an opponent who need not be on it, so
   `invites/<opponent>/<deleted uid>` survives. Invisible to players, never collected.
3. **`docs/store/` still holds the previous artwork.** `icon-master.png`, `icon-512.png`, the
   feature graphic generated from them and the three screenshots all show the old emblem and the
   palette from before the near-black one. `docs/store/ASSET_NOTES.md` says so at the top and no
   longer lists the two stale icons among the files to ship — the icon that is current and
   correct is `store-assets/play-icon-512.png`. They are still on disk, so the hazard is
   uploading one by name. Regenerating the screenshots needs a handset and the feature graphic
   needs the image workflow, so neither could be done here.
4. **Not verifiable here:** no device is attached. `docs/MANUAL_TESTS.md` C1b and C7 cover the
   guest's view of the board and the username gate, and the new back behaviour is step 4 of the
   `docs/RELEASE.md` checklist. None of the three can be signed off from a build.
