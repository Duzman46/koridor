# Store asset notes

The launcher icon is `reference/simge.png` — the owner's artwork: a gold pawn and a black one on a black board of gold-edged walls, inside a gold bezel. `docs/store/app-icon.py` cuts every size the app and the listing need from that one file, so the tile on the launcher, the tile on the splash screen, the tile in the Play listing and the mark on the feature graphic cannot drift apart from each other. Re-run it after the artwork or the framing changes; there is nothing to redraw by hand.

```bash
python docs/store/app-icon.py
python docs/store/feature-graphic.py
```

Two things about the cut are load-bearing:

- **The bezel is kept, against the guidance.** An adaptive icon is not supposed to carry its own shape — the launcher applies a mask, and a second rounded rectangle underneath it can show up as a gold ring clipped at four corners. Rendered under both masks that matter, that does not happen here: the baked radius is 14.5%, well inside a squircle's 30% and inside a circle, so the mask cuts across the frame's *straight* runs and comes out as a gold rim following the mask. On One UI it is indistinguishable from a frame drawn for that shape; on a Pixel it reads as a gold-rimmed disc. Both were looked at before the decision. `FRAME` is cut five pixels wide of the gold so the mask has dark to bite into first.
- **The tile is scaled into the visible 72, not cropped to it.** A launcher keeps the middle 72 units of 108, so a full-bleed source loses a third of itself — here, the outer walls and most of the frame. The outer sixth, which nothing draws, is the tile's own edge pushed outwards, blurred and pulled down to the surround's near-black.

Three marks are drawn rather than cut from the artwork, each for a reason the platform imposes:

- **`ic_launcher_monochrome.xml`** — the Android 13 themed icon. The system keeps this layer's shape and recolours it to the wallpaper palette, so the artwork would arrive as one flat blob. It is the pawn between two walls.
- **`ic_notification.xml`** — the status bar keeps a small icon's alpha and throws away every pixel of colour it has. Same reason, at 24dp.
- **`ic_launcher_foreground.xml`** — one fully transparent path, which draws nothing and is deliberate twice over. A launcher parallaxes the foreground across the background, so a full-bleed picture has to be the *background* layer or it slides off its own edges; and the layer cannot be a genuinely empty `<vector>`, because `VectorDrawable` throws `no path defined in <vector>` and takes the whole icon down with it. That shipped for one build and every launcher fell back to Android's grid-and-robot placeholder.

`reference/koridor.png` is the other source picture and **nothing generates from it.** It is the photograph behind the main menu, hand-cut into `res/drawable-{xh,xxh,xxxh}dpi/home_scene.webp` with its own top and bottom fade. It was briefly the icon too, for the length of one afternoon; the icon moved to `simge.png` and the backdrop stayed. Replacing it means re-cutting `home_scene` by hand.

Files, and which of them are safe to upload:

- `store-assets/play-icon-512.png` — **the listing icon.** Use this one. Opaque to the corners, no alpha, as Google Play requires — and it is the *masked* square, what a launcher leaves after it discards the outer sixth of the 108-unit canvas, so the listing shows what a phone shows rather than a zoomed-out version of it.
- `app/src/main/res/mipmap-*/ic_launcher_background.webp` — the adaptive icon's picture, five densities.
- `app/src/main/res/mipmap-*/ic_launcher.webp` — the legacy bitmap. Nothing masks this one, so it carries its own rounded alpha. Unreachable at `minSdk 26` — `mipmap-anydpi-v26` always wins — and kept because the cost is five kilobytes and the failure mode of not having it is an OEM launcher with a blank tile.
- `app/src/main/res/drawable-xxxhdpi/app_mark.webp` — the splash screen's tile, rounded by `KoridorMark`.
- `screenshots/phone-0{1..4}-*-tr.png` — the Turkish set: main menu, a match against the Expert bot with two walls down, the four bots and the colour choice, and the three ways to play. All 1080×2160.
- `screenshots/phone-0{1..4}-*-en.png` — the same four in English, same dimensions. Eight files in the folder, not four; upload the set that matches the listing language.
- `feature-graphic-1024x500.png` — the listing's feature graphic: near-black board, blue and red pawns, mint walls, and the launcher icon itself on the left. The mark used to be a drawing copied from the icon, which went stale the day the icon changed; `feature-graphic.py` now imports `app-icon.py` and cuts the tile at run time, so a stale copy is no longer possible.

Two stale files sit one directory above the repository, in `Koridor/store-assets/` — that is `../../../store-assets/` from here, outside `source/` and therefore outside git, which is why nothing has ever pruned them:

- `icon-512.png` — the blue-and-orange emblem on green tiles from before the near-black palette. Not `store-assets/play-icon-512.png`.
- `feature-graphic-1024x500.png` — **the same basename as the shipping one**, and the more dangerous of the two for exactly that reason. The one to upload is `docs/store/feature-graphic-1024x500.png`.

Uploading either by name is the hazard `WORK_ORDER.md` §9 describes.

The screenshots were taken on 2026-08-08 from the shipped release build on a Galaxy S24 at 1080×2340, cropped to remove the navigation bar. No interface element was generated or retouched — this is the app as it runs. The two earlier raster icons that used to sit in this folder are deleted rather than kept beside the current one, because the hazard was never that they were wrong, it was that they were named plausibly.

The board half of the feature graphic is drawn, not generated: `feature-graphic.py` renders it with Pillow at 4× and downsamples, so every colour in it is a literal from the same palette the screenshots show rather than an approximation of one. Re-run that script if the board or pawn colours change — and re-run it after `app-icon.py`, because the mark on the left is cut from the icon.

**The board is not recoloured to match the icon, on purpose.** Its tiles, its mint wall and its blue and red pawns are what a live match looks like, and a listing image that restyles the game to agree with its own icon is a listing image that misrepresents the app. The division is the app's own: gold is the interface accent, jade is a board object. What did move is the bloom behind everything, emerald to gold, because that is lighting rather than gameplay.

```bash
python docs/store/feature-graphic.py
```

