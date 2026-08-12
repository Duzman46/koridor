# Store asset notes

The launcher icon is a photograph: one square cut out of `reference/koridor.png`, which is the same picture the main menu stands on. `docs/store/app-icon.py` cuts every size the app and the listing need from that one file, so the tile on the launcher, the tile on the splash screen and the tile in the Play listing cannot drift apart from each other or from the home screen. Re-run it after the photograph or the framing changes; there is nothing to redraw by hand.

```bash
python docs/store/app-icon.py
```

Three things are still drawn rather than photographed, and each for a reason the platform imposes:

- **`ic_launcher_monochrome.xml`** — the Android 13 themed icon. The system keeps this layer's shape and recolours it to the wallpaper palette, so a photograph would arrive as one flat blob. It is the pawn between two walls.
- **`ic_notification.xml`** — the status bar keeps a small icon's alpha and throws away every pixel of colour it has. Same reason, at 24dp.
- **`ic_launcher_foreground.xml`** — empty on purpose. A launcher parallaxes the foreground across the background, so a full-bleed picture has to be the *background* layer or it slides off its own edges.

Files, and which of them are safe to upload:

- `store-assets/play-icon-512.png` — **the listing icon.** Use this one. Opaque to the corners, no alpha, as Google Play requires — and it is the *masked* square, what a launcher leaves after it discards the outer sixth of the 108-unit canvas, so the listing shows what a phone shows rather than a zoomed-out version of it.
- `app/src/main/res/mipmap-*/ic_launcher_background.webp` — the adaptive icon's picture, five densities.
- `app/src/main/res/mipmap-*/ic_launcher.webp` — the legacy bitmap. Nothing masks this one, so it carries its own rounded alpha. Unreachable at `minSdk 26` — `mipmap-anydpi-v26` always wins — and kept because the cost is five kilobytes and the failure mode of not having it is an OEM launcher with a blank tile.
- `app/src/main/res/drawable-xxxhdpi/app_mark.webp` — the splash screen's tile, rounded by `KoridorMark`.
- `screenshots/phone-01-main-menu-tr.png` — main menu (1080×2160)
- `screenshots/phone-02-gameplay-tr.png` — a match against the Expert bot, two walls down (1080×2160)
- `screenshots/phone-03-difficulty-tr.png` — the four bots and the colour choice (1080×2160)
- `screenshots/phone-04-play-modes-tr.png` — the three ways to play (1080×2160)
- `feature-graphic-1024x500.png` — the listing's feature graphic, redrawn on 2026-08-08 from the palette the app actually ships: near-black board, blue and red pawns, mint walls, and the icon's own motif on the left. It replaces a version generated before the near-black palette, which showed green tiles, an orange pawn and gold walls — an app the player would not have recognised.

One stale icon is still on disk outside this folder: `../../store-assets/icon-512.png`, at the repository root, is the blue-and-orange emblem on green tiles from before the near-black palette. It is not `source/store-assets/play-icon-512.png` and uploading it by name is the hazard `WORK_ORDER.md` §9 describes.

The four screenshots were taken on 2026-08-08 from the shipped release build on a Galaxy S24 at 1080×2340, cropped to remove the navigation bar. No interface element was generated or retouched — this is the app as it runs. The two earlier raster icons that used to sit in this folder are deleted rather than kept beside the current one, because the hazard was never that they were wrong, it was that they were named plausibly.

The feature graphic is drawn, not generated: `feature-graphic.py` renders it with Pillow at 4× and downsamples, so every colour in it is a literal from the same palette the screenshots show rather than an approximation of one. Re-run that script if the board or pawn colours change; there is no prompt to re-roll and no chance of the file drifting away from the app again.

```bash
python docs/store/feature-graphic.py
```

