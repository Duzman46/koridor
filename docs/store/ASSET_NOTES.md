# Store asset notes

The launcher icon is an adaptive icon authored as vectors: `res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` and the themed `ic_launcher_monochrome.xml`, on the standard 108-unit canvas. Everything raster is rendered from those same coordinates, so a launcher reading the legacy bitmap shows the artwork the adaptive icon shows. Launcher-density bitmaps carry a rounded alpha mask, because nothing masks them for us; `store-assets/play-icon-512.png` stays an opaque full-bleed square, as Google Play requires.

Files:

- `store-assets/play-icon-512.png`: Google Play listing icon
- `app/src/main/res/mipmap-*/ic_launcher.png`: Android launcher densities
- `feature-graphic-1024x500.png`: Google Play feature graphic
- `screenshots/phone-01-gameplay-tr.png`: Turkish gameplay screenshot (1080×2160)
- `screenshots/phone-02-main-menu-tr.png`: Turkish main menu screenshot (1080×2160)
- `screenshots/phone-03-exit-confirmation-tr.png`: Turkish exit confirmation screenshot (1080×2160)

**Not yet reshot for the current artwork**, and all three would ship a picture of an app the player will not recognise: `icon-master.png` and `icon-512.png` in this folder are the earlier raster emblem, the feature graphic below was generated from it, and the screenshots predate the near-black palette.

The feature graphic was generated with the built-in image generation mode, using the project icon as the visual reference, and then center-cropped to the exact Google Play size of 1024×500 pixels.

Feature graphic prompt:

> Create a polished 1024×500 landscape Google Play feature graphic for the Android strategy board game Koridor. Use the attached official app icon as the visual identity reference: deep charcoal-teal background, emerald rounded board tiles, one glossy cool-blue pawn, one glossy warm-orange pawn, and golden corridor walls. Show a cinematic three-quarter view of an abstract board with the two pawns facing across a strategic maze of gold walls. Premium modern 3D game art, dramatic rim lighting, clean negative space, high contrast, sharp silhouettes, balanced composition, no text, no letters, no logo, no watermark, no device mockup, no border, no unrelated objects.

The phone screenshots preserve the real tested app UI. They are cropped only to remove the Android navigation bar and meet Google Play's supported 2:1 aspect ratio; no interface elements were generated or retouched.
