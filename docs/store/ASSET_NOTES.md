# Store asset notes

The launcher icon is a photograph: one square cut out of `reference/koridor.png`. `docs/store/app-icon.py` cuts every size the app and the listing need from that one file, so the tile on the launcher, the tile on the splash screen and the tile in the Play listing cannot drift apart from each other. Re-run it after the photograph or the framing changes; there is nothing to redraw by hand.

`reference/koridor.png` is also the source of the main menu's backdrop, but **that one is not generated** — `res/drawable-{xh,xxh,xxxh}dpi/home_scene.webp` is a separate wide crop carrying its own top and bottom fade, and no script rebuilds it. Replacing the photograph therefore means re-running the script *and* re-cutting `home_scene` by hand, or the icon and the screen behind it stop being the same board.

```bash
python docs/store/app-icon.py
```

Three things are still drawn rather than photographed, and each for a reason the platform imposes:

- **`ic_launcher_monochrome.xml`** — the Android 13 themed icon. The system keeps this layer's shape and recolours it to the wallpaper palette, so a photograph would arrive as one flat blob. It is the pawn between two walls.
- **`ic_notification.xml`** — the status bar keeps a small icon's alpha and throws away every pixel of colour it has. Same reason, at 24dp.
- **`ic_launcher_foreground.xml`** — one fully transparent path, which draws nothing and is deliberate twice over. A launcher parallaxes the foreground across the background, so a full-bleed picture has to be the *background* layer or it slides off its own edges; and the layer cannot be a genuinely empty `<vector>`, because `VectorDrawable` throws `no path defined in <vector>` and takes the whole icon down with it. That shipped for one build and every launcher fell back to Android's grid-and-robot placeholder.

Files, and which of them are safe to upload:

- `store-assets/play-icon-512.png` — **the listing icon.** Use this one. Opaque to the corners, no alpha, as Google Play requires — and it is the *masked* square, what a launcher leaves after it discards the outer sixth of the 108-unit canvas, so the listing shows what a phone shows rather than a zoomed-out version of it.
- `app/src/main/res/mipmap-*/ic_launcher_background.webp` — the adaptive icon's picture, five densities.
- `app/src/main/res/mipmap-*/ic_launcher.webp` — the legacy bitmap. Nothing masks this one, so it carries its own rounded alpha. Unreachable at `minSdk 26` — `mipmap-anydpi-v26` always wins — and kept because the cost is five kilobytes and the failure mode of not having it is an OEM launcher with a blank tile.
- `app/src/main/res/drawable-xxxhdpi/app_mark.webp` — the splash screen's tile, rounded by `KoridorMark`.
- `screenshots/phone-0{1..4}-*-tr.png` — the Turkish set: main menu, a match against the Expert bot with two walls down, the four bots and the colour choice, and the three ways to play. All 1080×2160.
- `screenshots/phone-0{1..4}-*-en.png` — the same four in English, same dimensions. Eight files in the folder, not four; upload the set that matches the listing language.
- `feature-graphic-1024x500.png` — the listing's feature graphic, redrawn on 2026-08-08 from the palette the app actually ships: near-black board, blue and red pawns, mint walls, and the drawn pawn-between-walls mark on the left. **That mark is no longer the launcher icon** — see the note below.

Two stale files sit one directory above the repository, in `Koridor/store-assets/` — that is `../../../store-assets/` from here, outside `source/` and therefore outside git, which is why nothing has ever pruned them:

- `icon-512.png` — the blue-and-orange emblem on green tiles from before the near-black palette. Not `store-assets/play-icon-512.png`.
- `feature-graphic-1024x500.png` — **the same basename as the shipping one**, and the more dangerous of the two for exactly that reason. The one to upload is `docs/store/feature-graphic-1024x500.png`.

Uploading either by name is the hazard `WORK_ORDER.md` §9 describes.

The screenshots were taken on 2026-08-08 from the shipped release build on a Galaxy S24 at 1080×2340, cropped to remove the navigation bar. No interface element was generated or retouched — this is the app as it runs. The two earlier raster icons that used to sit in this folder are deleted rather than kept beside the current one, because the hazard was never that they were wrong, it was that they were named plausibly.

The feature graphic is drawn, not generated: `feature-graphic.py` renders it with Pillow at 4× and downsamples, so every colour in it is a literal from the same palette the screenshots show rather than an approximation of one. Re-run that script if the board or pawn colours change.

**It is out of date as of 2026-08-12 and was deliberately left that way.** Its left-hand motif is the jade bars and pawn that used to be the launcher icon, drawn at `feature-graphic.py:121–138` in `ICON_G1`/`ICON_G2`; the icon is a photograph now, so the graphic and the tile no longer read as the same product. Fixing it is a design decision about store artwork, not a mechanical consequence of the icon swap, so it waits for one.

```bash
python docs/store/feature-graphic.py
```

