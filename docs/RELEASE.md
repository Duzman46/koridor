# Play Store release guide

## Automated release protections

- Release builds use R8 code optimization and resource shrinking.
- All ten shipped languages are in the base APK. `localeFilters` strips every other language from
  the bundle, and Play's per-language split is switched off on purpose — the in-app language
  picker has to have something to switch to.
- Development builds always use Google’s official test banner and interstitial IDs.
- Release builds do not request ads unless real AdMob IDs are supplied.
- Premium is a one-time, non-consumable Play product with ID `remove_ads`.
- Purchases are granted only in the `PURCHASED` state, acknowledged, cached for offline use, and restored from Google Play.
- EEA/UK consent is requested through Google User Messaging Platform before ads can load.

## Required private configuration

1. Copy `monetization.properties.example` to `monetization.properties` and add the real AdMob app, banner, and interstitial IDs.
2. Create an active one-time Play Console product named `remove_ads`.
3. Create a private upload key, copy `keystore.properties.example` to `keystore.properties`, and fill in its path/passwords.
4. Never commit either private properties file or the `.jks` key. They are ignored by Git.

When `keystore.properties` is missing, `bundleRelease` intentionally creates an unsigned AAB suitable only for verification.

## Verification commands

The app, lint on the variant that actually ships, and the release binary:

`./gradlew :app:test :app:lintRelease :app:bundleRelease`

`lintRelease` rather than `lintDebug`: the shrinker, the manifest placeholders and the resource
set differ between the two, so a debug-only lint says nothing about what is uploaded.

The server halves are not part of that build and have to be run where they live:

`cd rules-tests && npm test` — the security rules, against the database emulator.
`cd worker && npm test && npm run test:e2e` — the scheduled worker, unit and end to end.

Both need a JDK 21 or newer first on `PATH`; the Firebase CLI refuses anything older.

## Play Console checklist

1. Confirm version code/name and upload the signed AAB.
2. Test through Play internal testing so Billing uses a licensed tester account.
3. Finish a complete online game from both player perspectives, including red-side rotation, wall placement, disconnect, and winning move.
4. Test leaving a game every way there is. Back and the Home icon both ask in every mode now; online is the one that says leaving loses the match, and bot and local two-player only say the board is thrown away. Confirming any of them shows a full-screen ad on the way out unless ad removal has been bought or one was shown in the last minute.
5. Run `node tools/app-ads.mjs`, then deploy both it and the privacy policy with `firebase deploy --only hosting`. `app-ads.txt` is what tells programmatic buyers that this AdMob account is allowed to sell the app's inventory; without it the slots still fill, at the price nobody bid up. It is generated rather than committed because it carries the real publisher id, and AdMob only finds it once the Play listing's Website field names the same domain. Then open the URL in `app.properties` and read the live page against `docs/DATA_SAFETY.md`. That page is the one artefact a reviewer fetches, and a contradiction between it and the Data safety form is on its own grounds for rejection.
6. Complete the Data safety form from `docs/DATA_SAFETY.md` §5, which covers account data, game and social data, the user-generated content the app shows (username and room name), messages, purchases, diagnostics and the advertising ID.
7. Complete target audience, ads, content rating, app access, and advertising ID declarations truthfully.
8. Add store screenshots for phone and tablet, feature graphic, descriptions, support email, and privacy URL.
9. Use Play pre-launch reports and resolve every crash, ANR, accessibility, and security warning before production rollout.

## Security note

The client verifies and acknowledges purchases through Google Play. Before a large commercial launch, add backend verification of purchase tokens through the Google Play Developer API for stronger fraud resistance.
