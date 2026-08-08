# Release status — 8 August 2026

`com.duzman46.gridbound`, versionCode **5**, versionName **1.0.0**. minSdk 26, targetSdk 37.

This replaces the round-by-round log rounds 1–7 kept here. Everything below was re-run or
re-read on the tree as it stands; nothing is carried forward on the strength of an earlier
round's word. The history is in the git log.

**There is one thing that must happen before the upload, and it is not in this repository.**
See "Blocking" below.

---

## Verified green, on this tree

| Command | Result |
| --- | --- |
| `./gradlew.bat :app:test` | **281 tests in 35 classes**, 0 failures, 0 errors, 0 skipped |
| `./gradlew.bat :app:lintRelease` | **0 errors, 4 warnings** |
| `./gradlew.bat :app:assembleRelease` | completes through R8; APK 5.58 MB |
| `./gradlew.bat :app:bundleRelease` | AAB 12.72 MB — this is what Play takes |
| `cd rules-tests; npm test` | **201 tests in 28 suites**, all pass |
| `cd worker; npm test` | **9 unit tests**, all pass |
| `cd worker; npm run test:e2e` | **84 end-to-end checks**, all pass |

Counts are read out of `app/build/test-results/testDebugUnitTest/*.xml` and the TAP summaries,
not off the console. The four lint warnings are the same four rounds 4–7 recorded and none is
about code: `enableOnBackInvokedCallback` and `localeConfig` as `UnusedAttribute` (both take
effect above `minSdk`), a newer-Gradle notice, and `ObsoleteSdkInt` on `mipmap-anydpi-v26`,
which is redundant at `minSdk` 26 and is still the folder every adaptive icon ships in.

`:app:test` was forced with `--rerun-tasks`. It reported `UP-TO-DATE` on the first pass, and an
up-to-date task is not a green one.

The release APK verifies under **APK Signature Scheme v2**, one signer,
`CN=Koridor Upload, OU=Mobile, O=Duzman, L=Istanbul, ST=Istanbul, C=TR` — so `keystore.properties`
is wired to the real upload key rather than silently producing an unsigned artefact.

## Release configuration, checked field by field

- `isMinifyEnabled` and `isShrinkResources` are both **on** for release; `proguard-rules.pro` is
  minimal and every rule in it has a reason written beside it.
- The release `BuildConfig` carries the **real AdMob unit ids** (`ca-app-pub-8456650313142312/…`),
  not Google's test ids, and `MONETIZATION_CONFIGURED` is `true`. The test ids appear only in the
  debug build type and as the fallback for an unconfigured release, which this is not.
- Nothing debug-only reaches release: `isPseudoLocalesEnabled` is debug-only, the App Check debug
  provider is a `debugImplementation`, `APP_CHECK_ENABLED` is `false` by deliberate opt-in, and
  the merged release manifest carries no `debuggable` or `testOnly`.
- `PRIVACY_POLICY_URL` is `https://gridbound-duzman46.web.app/privacy`. The URL answers 200.
  **What it serves is the wrong document — see Blocking.**
- Permissions the app declares are now **`INTERNET` and `ACCESS_NETWORK_STATE`**, both of which
  the Firebase, Play Services Ads and Play Billing SDKs also declare for themselves. Everything
  else in the merged manifest arrives from a library.
- Ten languages, agreeing in three places: `localeFilters`, `res/xml/locales_config.xml` and the
  `AppLanguage` enum. Play's per-language split is off on purpose, so the in-app picker has
  something to switch to.

## Audited clean

- **Strings.** `values/strings.xml` holds **330** entries — 329 translatable, `app_name`
  correctly not — plus 3 plural sets. Each of the other nine locales carries **exactly** those
  329 and those 3: no missing key, no extra key, no plural set missing a form the base has.
  Every `R.string`, `R.plurals` and `@string/` reference under `app/src` resolves, and nothing
  is defined that nothing reads (330 of 330 referenced, 3 of 3). No format specifier differs
  between a locale and English except three Arabic plural forms — the `one` and `two` cases of
  `username_error_too_short`, `username_error_too_long` and `game_player_walls` — where Arabic
  spells the number as a word (*حرف واحد*, *جداران*) and so has no place to put `%1$d`. That is
  the correct Arabic and it cannot fail at runtime: an unused format argument is ignored.
  Checked separately, Russian, Arabic and Hindi carry no entry written in Latin script at all
  except Hindi's `%1$s AI`, which is the loanword that file uses throughout (`AI सोच रहा है…`).
  What still matches English elsewhere is format strings and cognates that are the real word in
  that language — *Avatar*, *Tutorial*, *Online*, *Optional*, *Global*, *Details*, *System*.
- **Imports and dead code.** No unused import anywhere under `app/src`; the only imports with no
  textual use are `runtime.getValue`/`setValue`, which property delegation needs and never
  names. Of **437** top-level declarations, four are referenced only by a framework — the
  `Application` subclass named in the manifest and three Hilt modules found by annotation — and
  every other one is reached from source. No `TODO`, no `FIXME`.
- **`database.rules.json` parses** and the whole file is exercised: every top-level node now has
  at least one test on each side, which was not true when this round started (below).

---

## Found and fixed this round

1. **The store listing told the owner to paste a privacy-policy URL that answers 404.** Both
   `docs/store/PLAY_STORE_LISTING_EN.md` and `…_TR.md` gave the address with a trailing slash.
   Hosting serves the page through `cleanUrls`, so `/privacy` is 200 and `/privacy/` is **404** —
   verified by fetching both. The policy URL is the one link a reviewer always opens, and a 404
   there is a rejection with nothing else being read. Both files now carry the address without
   the slash, and say why, and say that it must match `app.properties`.
2. **The store listing described a different app.** It offered "Turkish and English languages"
   (there are ten), "dynamic color themes" (`ThemeMode` is `SYSTEM`/`LIGHT`/`DARK`; there is no
   dynamic colour anywhere in the tree), and "confirmation before leaving a match", which round 7
   changed. It named none of the online layer: no accounts, no username, no rating, no
   leaderboards, no friends, no quick match, no rematch, no in-match phrases, no reporting or
   blocking. A listing that hides the account requirement puts a reviewer in front of a sign-in
   gate they were not told about, and hides the user-generated content the content rating and
   Data safety answers turn on. Both files are brought level, each line checked against the
   strings or the code that implements it.
3. **`android.permission.VIBRATE` was declared and cannot be used.** The only haptic in the app
   is Compose's `LocalHapticFeedback`, which is `View.performHapticFeedback` — the system server
   performs the vibration under its own identity. The SDK's own annotation database attaches
   `RequiresPermission("android.permission.VIBRATE")` to `android.os.Vibrator` and to nothing
   under `android.view`, and the app never touches `Vibrator`. The manifest merge report also
   shows the permission arriving from this manifest alone, no library. It is removed, and the
   four documents that listed it as a permission the app asks for — `docs/PRIVACY_POLICY_EN.md`,
   `docs/PRIVACY_POLICY_TR.md`, `public/privacy.html`, `public/gizlilik.html` — no longer say so.
   This is the one change here whose premise a build cannot confirm, so `docs/MANUAL_TESTS.md`
   gains **H4**: feel for the wall-placement buzz once on a handset, and the four-file revert is
   written out in the step.
4. **Three nodes of `database.rules.json` had no test at all.** The suite was 192 and read as
   complete; it was not.
   - `leaderboards` — **zero** references in `rules.test.js`. This is the node whose being
     client-unwritable is the entire argument for the worker owning the weekly board, and for
     rounds 6 and 7 adding server jobs to take a deleted player's name off it. It is now six
     tests: the live week read with the exact ordered query `RtdbLeaderboardRepository` issues,
     the anonymous read refused, a player writing themselves on refused, a player deleting their
     own row refused, the week and the tree above it unwritable, and the weeks not enumerable.
   - `entitlementRevocations` — zero references. Now one test: unreadable and unwritable by the
     account it revokes.
   - `roomSecrets` — only the read refusal was covered. The `.write` rule, which is what stops a
     stranger replacing a room's password hash with one they know, had nothing. Now two tests:
     the host may set it for the room they opened, and nobody else may replace, overwrite a field
     of, or delete it while that room stands.

   192 → **201**, and all nine pass, which is what says the branches behave as claimed rather
   than that they merely exist.
5. **`docs/RELEASE.md` was wrong in two ways that matter to whoever runs it.** It said "Only
   Turkish and English resources are packaged", which has been false for several rounds, and its
   verification command was `:app:testDebugUnitTest :app:lintDebug :app:bundleRelease` — lint on
   the variant that does not ship. It now names the release-variant gate and the two server
   suites that are not part of any Gradle build, and records that the Firebase CLI needs a JDK 21
   or newer first on `PATH`.
6. **CI only ever built the debug variant.** `.github/workflows/android.yml` ran
   `testDebugUnitTest lintDebug assembleDebug`, so nothing in the pipeline exercised R8 — and the
   one crash `proguard-rules.pro` exists for (`WorkDatabase_Impl` renamed, app dead before
   `onCreate`) is release-only by construction. It now runs `:app:test :app:lintRelease
   :app:assembleRelease`, which needs neither of the two git-ignored properties files.
7. `docs/MANUAL_TESTS.md` said 192 security-rule tests; it says 201.

---

## Blocking — do this before the upload

**The live privacy policy is not the one in this repository, and it describes a different app.**

`https://gridbound-duzman46.web.app/privacy` and `/gizlilik` were fetched. Both serve the version
dated **2 August 2026**: sections *Data collected and processed / Permissions / Service providers
/ Retention and deletion / Children's privacy / Changes and contact*. That page says the app does
not ask for a name or an e-mail address, names five service providers with **no Crashlytics and
no Analytics**, offers no way to delete an account, and closes with *"the developer contact
address will be added here"* — there is none. Round 7 rewrote all four policy files to match the
app; `public/privacy.html` and `public/gizlilik.html` on disk are dated **8 August 2026** and are
correct. **They were never deployed.**

Every one of those is a Play policy problem on its own, and the combination is worse than any of
them: the live page contradicts what the Data safety form will say.

    firebase deploy --only hosting

Then re-open both URLs and read them against `docs/DATA_SAFETY.md` §1, §2 and §5 before filling
in the form. This is step 5 of `docs/RELEASE.md`. It is not done here because publishing to the
owner's hosting is the owner's action, not an agent's.

---

## Round 8 — settings, the play screen, leaving a game, ads, and a fourth bot

### What the owner asked for, and what happened to it

- [x] **The default-AI picker is gone from Settings.** With it went `SettingsViewModel.setDifficulty`,
      which nothing else called, and `settings_default_ai` in all ten locales. The stored value
      still exists and is still written by `GameViewModel` when a bot match starts — it is what
      keys the per-difficulty statistics, and it was never a setting anyone needed to reach.
- [x] **The language chips are about half the area.** Their own control rather than a restyled
      `FilterChip`, because the height and the padding are the whole point and both are fixed
      inside one. Still 36 dp and still spaced; what went is the padding around short names, which
      is where a ten-entry list wastes its width.
- [x] **Remove ads and Restore purchases are in Settings as well as in More.** Both screens now ask
      one question — `BillingState.offersAdRemoval` — instead of writing the same negation twice.
      That is the fix for what the owner actually asked to have verified: a player who has paid and
      still finds the offer on the other screen reads it as their money having gone nowhere.
      `AdRemovalOfferTest` pins it, including that a purchase Play is still holding as pending does
      not withdraw the offer.
- [x] **The three play modes sit in the middle of the screen.** The column takes the measured
      viewport as a *minimum* height, so it centres when there is room and grows from the top when
      a large font makes the choices taller than the screen — centring unconditionally would put
      the first one out of reach above the top edge.
- [x] **Back asks in every mode.** It used to ask only online. Online still says what it costs;
      bot and hot-seat say the board is thrown away.
- [x] **A bot match opens on blue.** It was random, which is right against a stranger and wrong
      here: there is no opponent to be fair to, and a solo player who never touches the control
      should not have the opener silently change under them.
- [x] **A fourth tier, EXPERT.** See below.

### Advertising

- [x] A full-screen ad on **every** finished match in all three modes, and on leaving a match from
      the board. It was every third match. The match counter is gone rather than set to one: with
      the count at one it is a comparison that is always true and a stored integer nobody reads.
- [x] A **one-minute floor** between two ads is all that is left. It exists for exactly one
      sequence — match ends, ad on the way out, new match started and abandoned at once — which
      would otherwise put two ads five seconds apart. Shorter than any real game, so it never costs
      an ad anyone played for.
- [x] The banner was already on the home screen; verified rather than changed.
- [x] **`tools/app-ads.mjs`.** Without `app-ads.txt` a large part of programmatic demand does not
      bid, so the slots still fill at the price nobody competed for. Generated rather than
      committed because it carries the real publisher id, and gitignored. It only works once the
      Play listing's Website field names the same domain — that half is the owner's.
- [x] The AdMob ids in `monetization.properties` are the owner's own account, not Google's test
      publisher. Checked, not assumed.

### The EXPERT bot, and what happened to HARD

Designed twice independently, judged against each other and against an audit of the existing
engine; `docs/EXPERT_BOT_SPEC.md` is the result and the record. Three defects in the old `HardAI`
were confirmed from the source, and they are why the owner said the top of the ladder played badly:

1. **The move ordering was inverted.** Every wall sorted ahead of every non-winning pawn move, at
   every node, on both sides, with ties going to walls. The straight advance is the best move in
   most Quoridor positions; searching it eleventh is close to worst-case for alpha-beta.
2. **The transposition cache was both useless and unsound.** Keyed on `BoardState.hashCode()`,
   which covers the move history, so two identical positions reached by different orders never
   shared a key — a structurally zero hit rate, paid for at every node. Meanwhile a fail-high
   *bound* was filed and returned as an *exact* score, and the table was allocated inside the
   depth loop so nothing survived between iterations.
3. **The wall candidates were nearly all horizontal, and truncated in the wrong place.** A pawn
   walking straight up a column produced horizontal candidates only, and the cap of ten was applied
   after the opponent's path had been inserted first — so the bot's own defensive walls never
   reached the search at all.

- [x] **HARD and EXPERT are now one engine at two budgets**, and `HardAI.kt` is deleted. Two
      evaluations are two chances for the ladder to invert under maintenance, which is the
      complaint this started from. EASY and MEDIUM are untouched.
- [x] **The evaluation counts plies, not steps.** `race = 2*dYou - 2*dMe + 1` puts whose turn it is
      inside the number, so "a wall must cost the opponent two steps to be worth playing" is
      arithmetic rather than a weight. The old `IMMEDIATE_THREAT_WEIGHT` — a turn-blind cliff of
      2000 inside an evaluation whose other terms spanned 200 — is gone.
- [x] **`FastBoard` is proven equal to the rules engine, not asserted to be.** Seven differential
      tests over thousands of random positions against `MoveValidator`, `WallValidator` and
      `AStarPathFinder`, plus a reversibility property test and an incremental-hash differential.
      One review re-derived the wall geometry independently in Java and swept all 128x81x4 edges
      and all 128x128 slot pairs: zero mismatches.
- [x] **The chosen action is re-validated through `GameEngine`** with a pawn-move fallback, so a
      divergence could cost strength and never legality.
- [x] **Abandoning a search actually stops it.** `AIEngine.abandonSearch` sets a volatile flag
      polled beside the deadline. Cancelling the coroutine never did anything — a search is a CPU
      loop that never suspends — so restarting against a thinking bot used to wait out the search
      nobody wanted, and pressing again stacked them. The restart button is also held while the bot
      thinks, as undo already was.

### The measurements, including the one that did not come out the way it was meant to

- [x] **EXPERT beat the frozen old HARD 10-0**, zero adjudications, decided on the board.
- [x] **The mirror match is exactly 6.0/12.** An engine against a copy of itself must score exactly
      half; this is what proves the pairing is sound, and every other number is meaningless without
      it. Deliberately breaking the pairing turned it red at 7.0/12.
- [x] **Engines are driven by node count, not by a clock**, so the same seed gives the same result
      on a loaded machine. Verified across four runs whose wall-clock times differed 4.4 times.
- [x] **Significance is computed over openings, not games.** The two halves of a colour-swapped pair
      are the same position with the colours exchanged and are not independent — which is exactly
      why the mirror match is 6/12 by construction. Counting games overstated every p-value in the
      file.
- [ ] **At an equal node budget, EXPERT and HARD are not separable.** 57.8% over 32 openings,
      p = 0.133. They are one engine and HARD's depth cap is most of what limits it. The ladder is
      real at the *shipped* budgets — 3.5:1, where EXPERT scores 30 of 40 — and the test measures
      that pair rather than the configuration shape. Stated here because the design document
      predicted otherwise.
- [ ] **A known blind spot in that test**, found by mutating it: raising `HARD_MAX_DEPTH` to
      EXPERT's is caught, but swapping the wall-candidate constants between the tiers is not. Those
      are worth about one opening in twenty, and a bar tight enough to see them would leave no
      slack for a tuning pass.

## Still open

Round 7's list, re-checked against the code rather than assumed. One is new.

1. **New: a room's password hash ends up readable by every signed-in player.** Joining a
   protected room writes `passwordAttempt` — the hash — into the room, because the rules compare
   it there, and nothing ever removes it. `rooms/$code` grants `.read` to anyone signed in when
   `visibility == 'PUBLIC'`, whatever the status, and the create form has had no visibility
   control since round 3, so every hand-made room is public. Bounded: the hash is salted with the
   room code (`SHA-256("koridor:$code:$password")`) so it is worth nothing anywhere else, and it
   only appears once the room is full and therefore unjoinable. Not fixed here — clearing the
   field afterwards needs a write shape `rooms/$code`'s `.write` rule does not currently admit,
   which is a rules change on the round before publishing.
2. **`board/walls` is guarded by arithmetic, not by rules.** Unchanged from round 7. Realtime
   Database rules cannot count children, so a modified client can still lay a wall it never paid
   for *during* a match. What it cannot do is have the match rated: `verifyReport` refuses a board
   where walls placed plus walls held is not twenty.
3. **`recentMatches` has no sweep.** Unchanged from round 7, re-checked: `worker/src/sweep.ts`
   writes those rows and no job walks them. A deleted account's history rows stay, and so does
   that player's name inside their opponents' rows. Both are declared as remaining in
   `docs/DATA_SAFETY.md` §4.2, and the rows hold only what was already public.
4. **`RtdbUserProfileRepository.releaseUsername` can leak one row.** Unchanged from round 5,
   re-read: the transaction is inside `runCatching { … }.onFailure { AppLog.warn }`. If it fails
   while the two `removeValue()` calls succeed, a generated `guest_NNNNNN` name stays reserved
   against a uid that no longer exists. Harmless — generated names are never handed out twice.
5. **A message never appears on a phone whose clock is fast.** Unchanged from round 5, re-read:
   `GameScreen.kt` fades the bubble on `System.currentTimeMillis() - sentAt`, and `sentAt` is the
   server's stamp. The room's own window no longer has this problem, and the same
   `/.info/serverTimeOffset` read would fix this too.
6. **The worst worker run sits at 47 of the 50 subrequests the free plan allows.** Unchanged.
   `MAX_REPORTS_PER_RUN` is 3 at ten subrequests each, plus seventeen for the pending query, the
   room sweep and the pairing backstop. The back-fill only runs on a minute that rated nothing;
   the invite collector and the weekly prune share a minute that took on no report at all.
   Anything added to a rated report costs three per run at that ceiling.
7. **Google Analytics is in the build and now declared.** The owner chose on 2026-08-08 to keep
   it: a game nobody has statistics for is a game whose problems cannot be found.
   `docs/DATA_SAFETY.md` §5 carries *Analitik* on the app-activity and device-identifier rows and
   §6 records the decision. The published privacy pages already described it. What is *not* done,
   and is written there rather than here as a blocker: Analytics is not wired to the UMP consent
   result, so an EEA player who refuses ad personalisation still has default Analytics collection.
   Correct declaration is what Play asks for and that is in place; consent mode is the next step
   if an EEA audience materialises.
8. **`functions/src/index.ts` is five jobs behind `worker/`.** Re-checked by grep: it has no
   board-index back-fill, does not keep an unnamed account off either board, collects no invites,
   prunes no weekly rows, does not check the board's wall arithmetic before rating — and writes
   no `recentMatches` row at all, which round 7's list did not mention. It is not what is
   deployed and `README.md` says so; deploying it instead of `worker/` reintroduces every one.
9. **`docs/store/` still holds the previous artwork.** Unchanged from round 4. `icon-master.png`,
   `icon-512.png`, the feature graphic generated from them and the three screenshots all show the
   old emblem and the pre-near-black palette. `ASSET_NOTES.md` says so and no longer lists the two
   stale icons among the files to ship — the current icon is `store-assets/play-icon-512.png`,
   512×512, RGB, no alpha, opaque to the corners. The hazard is uploading a stale file by name.
   Regenerating the screenshots needs a handset.
10. **Not verifiable here: no device is attached, and the emulator is not the deployed database.**
    `docs/MANUAL_TESTS.md` is the list; the ones this round bears on are **H4** (haptics without
    the VIBRATE permission, new) and **H2** (the release build after R8). Neither can be signed
    off from a build, and H4 is the only step here whose failure has a written revert.
