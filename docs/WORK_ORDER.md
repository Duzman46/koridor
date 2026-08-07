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

---

# Round 5 — three features, one tree, and the paperwork that publishes it

Three engineers again, on the same working tree: the guest hand-over that announced a success it
had not had, the recent-games section on both profile pages, and canned messages during an online
match. Two of them needed `database.rules.json` and both made their own change to it, which is
the thing this round was most likely to have broken.

It had not. What the join actually left behind was documentation: the feature that added a new
public node did not reach the document the Play Data Safety form is filled in from, and the
feature that added messages wrote a sentence in it that the same document contradicts two
sections later. Both are fixed below, along with a third error in that file that predates all
three engineers and would have been transcribed into the form as it stands.

## Verified green, on the merged tree

- [x] `./gradlew.bat :app:test :app:lintRelease :app:assembleRelease` — **237 JVM tests** in 26
      classes, no failures, no skips. `lintRelease`: **0 errors, 4 warnings**, all four the same
      pre-existing ones round 4 recorded — `enableOnBackInvokedCallback` and `localeConfig` as
      `UnusedAttribute`, a newer-Gradle notice, and `ObsoleteSdkInt` on `mipmap-anydpi-v26`.
      `assembleRelease` completes through R8.
- [x] `rules-tests` — **156 emulator tests** in 22 suites, up from 134: sixteen for the message
      vocabulary, six for the match history.
- [x] `worker` — 9 unit, **55 end-to-end**, up from 46.

## Two engineers, one rules file

- [x] **The file is valid JSON and the two additions do not touch each other.** `recentMatches`
      is a new top-level node; the messages live at `rooms/$code/chat/$authorId`, inside a
      subtree neither of the other branches went near. Nothing was overwritten in the merge:
      every rule round 4 verified is byte-identical, and the diff against `HEAD` is exactly the
      two new branches.
- [x] **Both branches are tested from both sides.** `recentMatches`: a signed-in player may read
      anyone's, an anonymous one may read nobody's, the tree cannot be queried whole, and the
      owner can neither write nor delete their own row. Messages: allowed for a player in the
      room, refused for a word outside the vocabulary, for a message written under the rival's
      id, for a stranger, for a field beside the two a message has, for a stamp the phone chose,
      for a second message inside the gap, and in a room nobody is playing — plus allowed once
      the gap has passed and in a match that has just finished, and the move path from both
      ends: a move carries the rival's message through untouched, and a move that rewrites it or
      invents one is refused.
- [x] **No free text can reach the database through the messages, checked by trying.** The
      obvious hole in a rule that names two fields is a write *below* one of them: a parent's
      `.validate` is not evaluated for a write to its descendant, so `chat/{uid}/key/note` has
      only whatever rule sits at that depth standing in front of it — `$otherFields`, which
      matches at every depth. Eight such writes were replayed against the emulator: a string
      under `key`, under `at` and two levels under `key`; an object as the value of `key`; a new
      field written deep; the whole `chat` node at once; a slot opened under a third uid; and
      deleting the rival's message on its own. Every one is refused. The two that a future
      edit to these rules could plausibly reopen — free text below `key` or `at`, and a write
      aimed at the `chat` node instead of a slot in it — are now tests rather than a claim, and
      are the two the suite grew by.
- [x] **Every other way a player-typed string reaches the database is accounted for.** The app
      takes typing in eight places: e-mail, password, username, room code, room name, room
      password, the friend search box and the word that confirms account deletion. Of those,
      only the username and the room name are stored where another player can read them, both
      predate this round, and both are declared on the Play form. The messages add no ninth.

## Fixed in the join

- [x] **`docs/DATA_SAFETY.md` did not know the match history exists.** A new node holding the
      opponent's username, the result, the date and the rating change, readable by every
      signed-in player, was added to the database without reaching the one document that is
      explicitly the basis for the Play Data Safety form. It is now a row in §2.2. It also
      belongs in §4.2, and that is the sharper omission: the rules deliberately grant clients no
      write under `recentMatches` — deleting is a write, and the right to delete a loss is the
      whole thing a server-owned history exists to prevent — so a deleted account's rows survive
      it, and so does that player's name inside their opponents' rows. A deletion section that
      lists what remains and leaves those out is the kind of wrong answer a form is filled in
      from.
- [x] **The same document said the app collects no free text, on a page that declares room names
      as user-generated content.** The bullet added under §2.6 "never collected" read *serbest
      metin* — free text — and asserted that nothing the player writes leaves the device. True
      of the messages, which is what it was about, and false of the two fields §2.1 and §2.2
      already list and §5 already declares. It now says *in-match* free text and names the two
      exceptions, so the section can no longer be read as denying them.
- [x] **And it said there is no crash reporting.** `§3` ended on "Crash reporting servisi
      (Crashlytics vb.) **kullanılmamaktadır**". `app/build.gradle.kts` has carried
      `firebase-crashlytics` and applied its plugin for several rounds, and `AppLog.report`
      sends every handled failure in a release build as a non-fatal. Nothing in this round
      caused it, and everything in this round would have shipped on top of it: this is the file
      the owner transcribes, and under-declaring diagnostics is a policy violation. §1 now lists
      Crashlytics and Analytics, §3 says what actually goes out — the exception, plus one custom
      key whose value is a fixed string chosen at the call site — and §5 carries the two rows
      Google's own guidance maps Crashlytics to. Analytics is a decision rather than a fact and
      is written up as one in §6: keep it and widen two rows, or drop the dependency.
- [x] **`docs/DATA_SAFETY.md` still named Cloud Functions as the server.** §1 listed them as
      what rates matches and cleans up rooms, and §4.3 credited the room sweep to
      `functions/src/rooms.ts`. Round 4 established that `worker/` is what is deployed and that
      deploying both is wrong; `README.md` was corrected then and this file was not. Both now
      name the worker, and the note records that Play purchase verification is the one job that
      moved nowhere and is therefore not running at all.
- [x] **A comment in `worker/src/sweep.ts` outlived the number beside it.** The backfill's
      guard explained itself with "rating a match costs about eight of the fifty subrequests"
      and "a full four of them" — the arithmetic from before histories were written, on a
      constant this round dropped to three. The count is now the one `MAX_REPORTS_PER_RUN`'s own
      documentation gives.
- [x] **Two of the three features had no device scenario.** `docs/MANUAL_TESTS.md` gained A6 for
      the messages during the round and nothing for the other two. C8 now covers the hand-over
      — including the five-minute wait, without which the guest session is fresh enough for
      Firebase to delete and the original bug never appears — and D8 covers the recent-games
      section, starting with the minute it can take for a server-written row to show up.

## Audited clean, no change needed

- [x] **Strings.** `values/strings.xml` holds 322 entries, 321 translatable — `app_name` is
      correctly not — and each of the other nine locales carries exactly those 321, plus the
      same three plural sets. Every `R.string`, `R.plurals` and `@string/` reference under
      `app/src` resolves, nothing is defined that nothing reads, and no format specifier differs
      between a locale and English. The twenty-eight keys this round added are present in all
      ten and read as somebody's language rather than a machine's: the Turkish messages are
      *Bol şans*, *Güzel hamle*, *İyi oynadın*, *İyi oyundu*, *Eyvah*, *Bir dahakine bol şans*,
      *Vay canına*. Nothing English is sitting in a non-English file; what still matches English
      is the pre-existing set of format strings and cognates round 4 listed.
- [x] **Imports and dead code.** No unused import anywhere in `app/src` — the only imports with
      no textual use remain `runtime.getValue`/`setValue`, which property delegation needs and
      never names. No private declaration, composable or otherwise, is unreferenced in its own
      file; `MatchChatBar`, `ChatBubble`, `MatchMessageSheet`, `ShowMoreRow` and `RecentGameRow`
      are all reached. No `TODO`/`FIXME`, no tabs, no trailing whitespace in anything this round
      touched.
- [x] **A move still carries the messages.** Every write the app makes to a room goes through
      `MutableData` and sets named children, so `chat` is rewritten as it stood rather than
      dropped — which is what the rules' "untouched" branch is there to accept. Checked on all
      five: the move, the finish, the join, the guest leaving a waiting room, and the host
      tearing one down. A client that dropped `chat` from a move *would* be allowed to, and it
      erases a pleasantry that had seconds left to live; not worth a rule that could refuse a
      real move.
- [x] **The worker still fits inside the free plan.** A rated report costs ten subrequests, three
      of them thirty; the pending query, the room sweep and the matchmaking backstop want at
      most seventeen more, and the board-index walk only runs on a run that rated nothing. The
      worst run is 47 of 50 — see below for the part of that worth watching.

## Still open — read before shipping

Round 3's list, re-checked rather than assumed:

- **Item 1, the guest listed under a generated name, is as closed as it is going to get, and it
  is not zero.** `AccountEvent.Linked(needsUsername)` reads the name off the profile the write
  returned — not off a session flow that may not have caught up — and routes an account still
  carrying `guest_######` to the username screen with the back stack cleared, so the app cannot
  be used past it. This round's hand-over also clears `usernameChosen`, so an arriving account
  is judged on its own name rather than on the answer the guest gave. What remains is that
  `claimBoardRating` runs inside `ensureProfile`, which is the write that makes the account
  real: from that instant until the player finishes typing, they are on the all-time board under
  the generated name. Round 3 weighed the alternative — moving when the board rating is claimed
  — and it is a behavioural change wanting a handset; the window is now the length of one form
  rather than until the next cold start, and it is not being narrowed further on the round
  before publishing. Not carried below, because there is no action left that this tree can take.
- **Item 2 is unchanged, verified still open, and is item 1 below.**

Round 4's list is unchanged except where noted.

1. **Erasure misses invites sent to non-friends.** Carried from round 3 and verified still open:
   `RtdbSocialRepository.deleteSocialData` reads `friendships/{me}` and cleans the other side of
   each relationship it finds, but a rematch invitation goes to an opponent who need not be a
   friend, so `invites/<opponent>/<deleted uid>` survives. Invisible to players — the client
   filters expired entries and these carry a TTL — but the row is never collected.
2. **`recentMatches` has no sweep.** A deleted account's history rows stay, and so does the
   deleted player's name inside their opponents' rows. Both are now stated in
   `docs/DATA_SAFETY.md` §4.2, which is what was actually missing; the rows themselves are
   unreachable from the app and hold only what was already public. The clean fix is a sixth
   worker job walking `recentMatches` for uids with no profile — a new job, not a rule change,
   and the rules must *not* be loosened to let a client do it.
3. **`RtdbUserProfileRepository.releaseUsername` can leak one row.** Its transaction is wrapped
   in `runCatching` and only logs. If it fails while the two `removeValue()` calls succeed, a
   guest's generated `guest_NNNNNN` name stays reserved against a uid that no longer exists.
   Harmless — generated names are never handed out twice — but it is the one thing on the
   hand-over path that can silently leave something behind. Shared with account deletion, so
   the fix touches more than the flow that surfaced it.
4. **A message never appears on a phone whose clock is fast.** `ChatBubble` fades on
   `System.currentTimeMillis() - sentAt`, and `sentAt` is the server's stamp. More than seven
   seconds of device clock ahead of the server computes every arriving message as already
   expired, and the feature is silently dead on that handset. The move clock has the same
   tolerance and is visibly wrong rather than absent, which is why this is not the same bug
   twice. The fix is `/.info/serverTimeOffset`, which Firebase already publishes on the
   connection the room is already read over — but it is a new listener feeding a new field
   through the room flow into the board screen, and that is not a change to make on the round
   before publishing over a bubble that fades.
5. **The worst worker run sits at 47 of the 50 subrequests the free plan allows.** Three rated
   reports at ten each, plus seventeen for the query, the room sweep and the pairing backstop.
   Anything added to a rated report costs three per run at that ceiling, so the next thing that
   needs a read per match has to come with `MAX_REPORTS_PER_RUN` going to two.
6. **Google Analytics is in the build and undeclared.** `firebase-analytics` is a dependency and
   `google-services.json` is present, so default event collection is on although the app sends
   no event of its own. `docs/DATA_SAFETY.md` §6 states the two options; one of them has to be
   chosen before the form is filled in. Crashlytics is not part of that choice — it is used, and
   §5 declares it either way.
7. **The privacy policy drafts are behind the app.** `docs/PRIVACY_POLICY_TR.md` and
   `docs/PRIVACY_POLICY_EN.md` describe an anonymous online game: no accounts, no usernames, no
   friends, no leaderboard, no match history, no messages, no crash reporting. "The app does not
   ask users for a name, email address, or phone number" is now false. They have to be rewritten
   from §1, §2 and §5 of `docs/DATA_SAFETY.md` and published at the URL `app.properties` is
   still missing.
8. **`functions/src/index.ts` has no board-index back-fill.** Unchanged from round 4. It is not
   what is deployed; deploying it instead of `worker/` reintroduces the bug round 4 fixed.
9. **`docs/store/` still holds the previous artwork.** Unchanged from round 4.
10. **Not verifiable here:** no device is attached. This round's three features are
    `docs/MANUAL_TESTS.md` A6 (messages), C8 (the hand-over) and D8 (recent games). C8 in
    particular cannot be replaced by anything a build can prove: the failure it exists for only
    appears once the guest session is old enough for Firebase to refuse to delete it.
