# Store asset notes

The launcher icon is an adaptive icon authored as vectors: `res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` and the themed `ic_launcher_monochrome.xml`, on the standard 108-unit canvas. Everything raster is rendered from those same coordinates, so a launcher reading the legacy bitmap shows the artwork the adaptive icon shows. Launcher-density bitmaps carry a rounded alpha mask, because nothing masks them for us; `store-assets/play-icon-512.png` stays an opaque full-bleed square, as Google Play requires.

Files, and which of them are safe to upload:

- `store-assets/play-icon-512.png` — **the listing icon.** Use this one.
- `app/src/main/res/mipmap-*/ic_launcher.png` — Android launcher densities.
- `screenshots/phone-01-main-menu-tr.png` — main menu (1080×2160)
- `screenshots/phone-02-gameplay-tr.png` — a match against the Expert bot, two walls down (1080×2160)
- `screenshots/phone-03-difficulty-tr.png` — the four bots and the colour choice (1080×2160)
- `screenshots/phone-04-play-modes-tr.png` — the three ways to play (1080×2160)
- `feature-graphic-1024x500.png` — **stale.** Generated from the earlier raster emblem, before the near-black palette. It is the one asset here that still shows an app the player will not recognise. Play requires a feature graphic only for some placements; regenerate it before it is used anywhere prominent.

The four screenshots were taken on 2026-08-08 from the shipped release build on a Galaxy S24 at 1080×2340, cropped to remove the navigation bar. No interface element was generated or retouched — this is the app as it runs. The two earlier raster icons that used to sit in this folder are deleted rather than kept beside the current one, because the hazard was never that they were wrong, it was that they were named plausibly.

The feature graphic was generated with the built-in image generation mode, using the project icon as the visual reference, and then center-cropped to the exact Google Play size of 1024×500 pixels.

Feature graphic prompt:

> Create a polished 1024×500 landscape Google Play feature graphic for the Android strategy board game Koridor. Use the attached official app icon as the visual identity reference: deep charcoal-teal background, emerald rounded board tiles, one glossy cool-blue pawn, one glossy warm-orange pawn, and golden corridor walls. Show a cinematic three-quarter view of an abstract board with the two pawns facing across a strategic maze of gold walls. Premium modern 3D game art, dramatic rim lighting, clean negative space, high contrast, sharp silhouettes, balanced composition, no text, no letters, no logo, no watermark, no device mockup, no border, no unrelated objects.

