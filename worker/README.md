# Koridor's server

Rating, the weekly leaderboard, the match history on a profile, room expiry, the matchmaking
backstop and the request-channel entries no phone is allowed to find — everything that has to
be written by something no player controls, or that nobody's phone can be relied on to do.

This was originally three Cloud Functions. Cloud Functions require a Firebase project on the
Blaze plan, and Blaze requires a payment method, so the same work runs here instead: a
scheduled Cloudflare Worker on the free plan, which needs no card.

## What it does

Once a minute the worker:

1. finds match reports still marked `PENDING`, re-checks each against the room it claims to
   come from, and applies Elo to both players plus the current week's board;
2. writes every one of those matches — rated or not — into both players' histories under
   `recentMatches/{uid}`, keeping the last ten and dropping the oldest;
3. deletes rooms that expired without ever being played, and closes matches that ran past
   their window rather than deleting them mid-game;
4. pairs whoever is still in the matchmaking list, closest ratings first, and clears entries
   nothing is behind any more;
5. on a run that had no match to rate, walks a page of the profile tree and takes any account
   that belongs on the all-time board and is not yet in its index onto it;
6. on a run that took on no report at all, walks a page of the request channel and clears
   every entry that has expired, along with every live one whose sender no longer has a
   profile.

Step 2 is here rather than on the phones because the history sits on a public profile: a list
a device writes is a list a device edits, and nothing in the database could tell a real loss
from a modified client quietly not filing one. The rules therefore grant no client any write
under `recentMatches`, while letting every signed-in player read anyone's. An unranked match
and a match against a guest are recorded too — they were played — and carry no `ratingChange`
field at all rather than a zero, because zero is what an evenly matched draw actually pays.

The board's index is a copy of the rating written under `leaderboardRating`, and two profiles
never get one: a guest, which is what keeps anonymous players off the table, and an account
still wearing the name the app handed out. The second is what makes linking a credential and
being listed two separate moments — an account is real the instant the credential lands and
named a form later, and a name nobody chose is not one to publish. A profile carrying no copy
is not last in that ordering, it is absent from it, so before step 5 existed every account
written before the copy did was invisible on a board that reported no fault. It is the only
hand that can reach those profiles: their owners are signed in already and will not be signing
in again, and the rules let a phone write nobody's profile but its own.

Step 6 exists for the same reason from the other direction. `invites/{recipient}/{sender}`
carries a game invitation, a rematch and its refusal; a rematch is licensed by the finished
match rather than by friendship, so it can be sent to somebody who is not on the sender's
friend list. Deleting an account walks that friend list — it is the only enumeration of other
players a phone has — so an entry sent to a non-friend is one the departing account cannot
name, and the rules let nobody read `invites/{recipient}` but that recipient, at any depth. It
may be deleted by its sender, and never found by them. Nothing collected an expired entry
either, so the channel only ever grew.

Quick match is a waiting list, and the phones pair each other out of it — two devices reading
the same list reach the same answer, and the room a pairing lands in is named after the player
being claimed so that two devices going for the same person collide on one node. This is what
catches the cases that leaves behind: an odd number waiting, a device that lost a race and
then went quiet, a phone that wrote itself into the list and never managed anything else.

It has **no `fetch` handler**, so it has no public URL. The thing holding the database's
admin key cannot be reached from the internet at all — there is no endpoint to find or probe.

## Why polling

A worker cannot subscribe to Realtime Database events the way `onValueCreated` did, so the
trigger became a schedule. The cost is up to a minute of latency before a rating moves; the
benefit is that nothing has to be exposed to the network to receive an event, and a report
filed while a run is already going is simply picked up by the next one.

`MAX_REPORTS_PER_RUN` is three. The limit is not the work, it is subrequests: the free plan
allows fifty outbound requests per invocation and one rated report costs ten — two to claim
it, one to re-check the room, four to read both records and both weekly rows, two to read
both histories, and one for the update that lands all of it. Three of those leaves twenty for
the pending query, the room sweep and the matchmaking backstop, whose combined worst case is
seventeen — and still rates four thousand matches a day.

That is also why the last two steps are conditional, and why step 6 is the stricter of the
two. Nobody is waiting on either: an account is on the board within a minute of naming itself
either way, and every entry step 6 collects is one no client would have shown. Step 5 costs
three subrequests and runs whenever nothing was rated. Step 6 costs up to thirteen — one read
per live entry whose sender it is unsure about — and a run of three unranked reports stands at
forty-four of the fifty without ever adding to `rated`, so it waits for a minute that took on
no report at all.

## Layout

| file | what it is |
| --- | --- |
| `src/index.ts` | the `scheduled` entry point, and the only place secrets are read |
| `src/sweep.ts` | the actual work: rate reports, record histories, expire rooms, pair the waiting list, index the board, collect dead invites |
| `src/db.ts` | the Realtime Database REST client, including compare-and-set via ETags |
| `src/auth.ts` | service-account key → access token, signed with WebCrypto |
| `src/elo.ts` | the rating maths |

`src/elo.ts` is deliberately a sibling of the Kotlin `EloCalculator` and of
`functions/src/elo.ts`. They are kept in step by tests that pin the same reference values —
`EloCalculatorTest`, and `elo.test.ts` beside each copy. Change a constant in one and the
others fail.

## Setup

Steps 1–3 need your accounts and have to be done by you. After that everything is scripted.

### 1. A Cloudflare account

<https://dash.cloudflare.com/sign-up> — free, no card.

### 2. A service-account key

Firebase Console → **Project settings → Service accounts → Generate new private key**. This
downloads a JSON file.

**Save it outside this repository** — `Koridor/private/` next to the signing keystore is the
right place. It is a key to the whole database; it must never be committed, and it is not
needed again after step 4.

### 3. Sign wrangler in

```bash
npx wrangler login
```

### 4. Hand over the key

```bash
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT < ../../private/koridor-service-account.json
```

Reading it from the file rather than pasting keeps the key out of your shell history.
Cloudflare stores it encrypted and cannot read it back out, which is also why it must never
be written into `wrangler.toml`.

### 5. Deploy

```bash
npx wrangler deploy
```

Then watch a run go by:

```bash
npx wrangler tail
```

Every run logs a `sweep` line even when it finds nothing, so a cron that quietly stopped
firing looks different from a cron with nothing to do.

## Tests

```bash
npm test        # the rating maths
npm run test:e2e  # the whole sweep, against the database emulator
```

The end-to-end test is the one that matters. It starts the emulator with this project's real
`database.rules.json`, seeds an honest match, a false report, an unranked match, two dead
rooms, four players waiting to be paired, a pair who play eleven matches, an account that has
linked a credential but not yet named itself, and a request channel holding an erased
account's rematch — and checks what the sweep does with each, including that running it twice
does not rate the same match twice, that the eleventh match pushes the first out of a history
rather than the tenth, and that both walks advance their cursor over a tree larger than one
page. Every part of this failed silently in production when it was wrong: a sweep that finds
nothing looks exactly like a sweep with nothing to do.

## Not here

Play purchase verification (`functions/src/purchases.ts`) has not been ported. It needs a
second service account linked in the Play Console, and nothing depends on it: the "remove
ads" entitlement comes from Play Billing's own signed response on the device, and the receipt
the app files is an audit trail with no consumer for now.

`functions/` is left in place, dormant. If the project is ever moved to Blaze it can be
deployed as-is, and this worker deleted.
