# Store asset notes

The launcher icon is an adaptive icon authored as vectors: `res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml` and the themed `ic_launcher_monochrome.xml`, on the standard 108-unit canvas. Everything raster is rendered from those same coordinates, so a launcher reading the legacy bitmap shows the artwork the adaptive icon shows. Launcher-density bitmaps carry a rounded alpha mask, because nothing masks them for us; `store-assets/play-icon-512.png` stays an opaque full-bleed square, as Google Play requires.

Files, and which of them are safe to upload:

- `store-assets/play-icon-512.png` — **the listing icon.** Use this one.
- `app/src/main/res/mipmap-*/ic_launcher.png` — Android launcher densities.
- `screenshots/phone-01-main-menu-tr.png` — main menu (1080×2160)
- `screenshots/phone-02-gameplay-tr.png` — a match against the Expert bot, two walls down (1080×2160)
- `screenshots/phone-03-difficulty-tr.png` — the four bots and the colour choice (1080×2160)
- `screenshots/phone-04-play-modes-tr.png` — the three ways to play (1080×2160)
- `feature-graphic-1024x500.png` — the listing's feature graphic, redrawn on 2026-08-08 from the palette the app actually ships: near-black board, blue and red pawns, mint walls, and the icon's own motif on the left. It replaces a version generated before the near-black palette, which showed green tiles, an orange pawn and gold walls — an app the player would not have recognised.

The four screenshots were taken on 2026-08-08 from the shipped release build on a Galaxy S24 at 1080×2340, cropped to remove the navigation bar. No interface element was generated or retouched — this is the app as it runs. The two earlier raster icons that used to sit in this folder are deleted rather than kept beside the current one, because the hazard was never that they were wrong, it was that they were named plausibly.

The feature graphic is drawn, not generated: `feature-graphic.py` renders it with Pillow at 4× and downsamples, so every colour in it is a literal from the same palette the screenshots show rather than an approximation of one. Re-run that script if the board or pawn colours change; there is no prompt to re-roll and no chance of the file drifting away from the app again.

```bash
python docs/store/feature-graphic.py
```

