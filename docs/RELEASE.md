# Play Store release guide

## Automated release protections

- Release builds use R8 code optimization and resource shrinking.
- Only Turkish and English resources are packaged; Play’s app bundle creates device-specific splits.
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

## Verification command

`./gradlew :app:testDebugUnitTest :app:lintDebug :app:bundleRelease`

## Play Console checklist

1. Confirm version code/name and upload the signed AAB.
2. Test through Play internal testing so Billing uses a licensed tester account.
3. Finish a complete online game from both player perspectives, including red-side rotation, wall placement, disconnect, and winning move.
4. Test leaving a game every way there is: back and the Home icon in AI and local two-player, where back returns to the previous screen and only the Home icon asks; back and the Home icon in an online match under way, where both must warn that leaving loses it; and back in an online room nobody has joined, which leaves at once because the seat still has to be released.
5. Publish both privacy policies at a public HTTPS URL and add the developer contact email.
6. Complete Data safety declarations for Firebase anonymous IDs/game state, AdMob advertising/diagnostics, and Play Billing purchase data.
7. Complete target audience, ads, content rating, app access, and advertising ID declarations truthfully.
8. Add store screenshots for phone and tablet, feature graphic, descriptions, support email, and privacy URL.
9. Use Play pre-launch reports and resolve every crash, ANR, accessibility, and security warning before production rollout.

## Security note

The client verifies and acknowledges purchases through Google Play. Before a large commercial launch, add backend verification of purchase tokens through the Google Play Developer API for stronger fraud resistance.
