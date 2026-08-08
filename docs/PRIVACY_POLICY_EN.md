# Koridor Privacy Policy

Last updated: 8 August 2026

This is the text published at `public/privacy.html`, which is what the app links to from
**More → Privacy Policy** and from **Settings → Account**. The two must be changed together;
`docs/DATA_SAFETY.md` is where both are derived from.

Koridor is a strategy game. It can be played on its own, against a bot or on one handset, with
no account and no network at all. Playing online needs an account, and that is where everything
below applies.

## What is collected

**Your account.** Creating an account with an e-mail address stores that address with Firebase
Authentication and in a private record only you can read. Signing in with Google stores what
Google returns for your account instead. Either way you are asked to choose a **username**,
which is public: other players see it on the leaderboard, on your profile, on the board during
a match and in their own recent-games list. You also pick an **avatar**, which is drawn in the
app from a fixed set — no image is ever uploaded.

Playing as a guest creates an anonymous identifier for the device instead of an account. A
guest has a generated name, is never listed on a leaderboard, and cannot use friends,
invitations or rated matches.

**How you play.** Your rating, wins, losses, draws, win streak and number of games are stored
with your account and are public. Every online match writes a result record naming the two
players, and a row in each player's short history — the opponent's username, the outcome, the
date and the rating change — which any signed-in player may read.

**Rooms and matches.** A room holds its code, an optional room name you type, the settings, the
board position and the moves, for as long as the match lasts. During a match you may send
preset phrases and emoji chosen from a fixed list; nothing you type reaches another player.

**Friends.** Friendships, friend requests, blocks, game invitations and a coarse online/offline
flag are stored so the friends list works. A block is never shown to the person blocked.
Presence is only "online" or "offline" — never a location, an address or a last-seen time.

**Purchases.** "Remove ads" is a one-time in-app product handled by Google Play. Koridor never
sees card, address or billing details. It stores a hash of the purchase token, the order
identifier and the purchase time, to confirm ownership and to stop one purchase being reused on
another account.

**Ads.** The free version shows Google AdMob ads. Google may process your advertising
identifier, IP address, approximate location, ad interactions and device diagnostics under its
own policies. Where required, your consent is requested through the Google User Messaging
Platform before any ad is loaded, and your ad privacy choices can be reopened from **Settings →
Ad privacy options**. Buying "remove ads" stops ads loading at all.

**Diagnostics.** Firebase Crashlytics receives crashes and handled errors from release builds.
What goes with them is the exception type and a fixed label naming the operation — never your
username, your e-mail, a room code or anything you typed. Crashlytics itself also collects the
device model, the operating system version and an installation identifier. Google Analytics for
Firebase is present and collects its default events; the app sends no event of its own.

## What is never collected

Location of any kind, contacts, phone numbers, photos, video, audio, files, health, financial
or biometric data, SMS or call logs. No image you supply is uploaded — avatars are drawn from a
fixed set in the app itself.

## Content other players can see, and how to report it

Two things you type are shown to players who do not know you: your **username** and a **room
name**. Both are reportable from inside the app.

- Open a player's profile — from the leaderboard, from a recent-games row, or by tapping your
  opponent during a match — and use **Report**. Signed-in players can also **Block** from the
  same page, which stops that player inviting you or sending you requests.
- In the room browser, the flag beside a listed room reports the player who named it.

A report names the account and the category and is read only by the developer. The player being
reported is never shown it and cannot delete it. There is no free-text field anywhere in the
app that reaches another player.

## Permissions

Internet and network state, for online matches, ads and purchase checks. That is the whole
list. The app does not ask for camera, microphone, contacts, location or storage permission.

## Service providers

Firebase Authentication, Firebase Realtime Database, Firebase Crashlytics, Google Analytics for
Firebase, Google Play Billing, Google AdMob, the Google User Messaging Platform, Android
Credential Manager, and a scheduled Cloudflare Worker that calculates ratings and clears away
finished rooms. Data is processed under those providers' terms for gameplay, security, fraud
prevention, payments and advertising. Koridor does not sell user data.

## Keeping and deleting your data

You can delete your account from inside the app: **Settings → Account → Delete account**. You
are asked to type a confirmation word. This releases your username, deletes your private
record, your public profile, your friendships and blocks on both sides, your invitations, your
presence, your rows on the weekly leaderboard, and your Firebase Authentication account.

Some things survive it, and this is why:

- Match result records keep the two user identifiers so an opponent's history stays whole. They
  no longer point at any profile.
- Your opponents' own history rows keep the username you played under, because their record of
  a match they played is theirs.
- Your own history rows remain in the database, unreachable from the app once the profile is
  gone. No client is allowed to write there at all — the right to delete a loss is exactly what
  a server-owned history exists to prevent.
- A hash of a purchase token remains, so the same purchase cannot be moved to another account.
  It contains no personal data.
- Google Play keeps its own record of a purchase, and Google keeps its own advertising data.
  Both are governed by Google's policies and by your Google account settings.

Automatic clean-up: a room waiting for an opponent is deleted after 30 minutes, a played room
after 24 hours, and an invitation expires after 10 minutes. A profile's history keeps its last
ten matches. The weekly leaderboard keeps the current week and the one before it.

Uninstalling the app removes everything stored on the device. Clearing the app's storage from
Android settings does the same without removing your account.

## Children's privacy

Koridor asks for an e-mail address when you create an account that way, and for a username
before you can play online. It does not ask for a real name, a phone number or an address.
Online play puts you in touch with other players; share room codes only with people you know.

## Changes and contact

Changes are published on this page and noted on the store listing. Questions, data requests and
reports about another player can be sent to **furkanduzman46@gmail.com**.
