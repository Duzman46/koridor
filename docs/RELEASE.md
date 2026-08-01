# Release guide

## Verified artifacts

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Unsigned release bundle: `app/build/outputs/bundle/release/app-release.aab`

## Signing

Production signing credentials must never be committed. Create an upload key in Android Studio, keep it outside the repository, and configure signing through local environment variables or an untracked `keystore.properties` file before uploading the bundle to Google Play.

## Pre-release checklist

1. Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:bundleRelease`.
2. Confirm the version code and version name.
3. Test the signed build on at least one API 26 device and one API 37 device.
4. Verify phone, tablet, light theme, dark theme, sound-off and haptics-off flows.
5. Publish the privacy policy at a public HTTPS URL and add it to Play Console.
6. Complete Play Console content rating and data safety forms truthfully.
