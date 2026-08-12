# Store asset notes

The launcher icon is `reference/simge.png` — the owner's artwork: a gold pawn and a black one on a black board of gold-edged walls, inside a gold bezel. `docs/store/app-icon.py` cuts every size the app and the listing need from that one file, so the tile on the launcher, the tile on the splash screen, the tile in the Play listing and the mark on the feature graphic cannot drift apart from each other. Re-run it after the artwork or the framing changes; there is nothing to redraw by hand.

```bash
python docs/store/app-icon.py
python docs/store/feature-graphic.py
```

Two things about the cut are load-bearing:

- **The bezel is cropped off, and that took two tries.** Keeping it looked defensible on paper: the baked radius is 14.5%, inside a squircle's 30% and inside a circle, so the mask crosses the frame's *straight* runs rather than its corners and should come back as a gold rim following the mask. It shipped for one build and it was wrong on the handset — the moulding is lit from the top left, so the surviving rim is bright along two edges, dim along two, and pinched to nothing at the mask's narrowest points. A frame present on half its perimeter reads as damage. `ART` is now the opening *inside* the moulding: its inner edge runs from 81px at the top to 136px at the right (it is a three-dimensional bevel, not a stroke), and the crop clears the worst case on all four sides. Gold still reaches the edges, but it is the walls' own lit top faces — artwork, not frame. The launcher supplies the only frame there is.
- **The tile is scaled into the visible 72, not cropped to it.** A launcher keeps the middle 72 units of 108, so a full-bleed source loses a third of itself — here, the outer walls and most of the frame. The outer sixth, which nothing draws, is the tile's own edge pushed outwards, blurred and pulled down to the surround's near-black.

Three marks are drawn rather than cut from the artwork, each for a reason the platform imposes:

- **`ic_launcher_monochrome.xml`** — the Android 13 themed icon. The system keeps this layer's shape and recolours it to the wallpaper palette, so the artwork would arrive as one flat blob. It is the pawn between two walls.
- **`ic_notification.xml`** — the status bar keeps a small icon's alpha and throws away every pixel of colour it has. Same reason, at 24dp.
- **`ic_launcher_foreground.xml`** — one fully transparent path, which draws nothing and is deliberate twice over. A launcher parallaxes the foreground across the background, so a full-bleed picture has to be the *background* layer or it slides off its own edges; and the layer cannot be a genuinely empty `<vector>`, because `VectorDrawable` throws `no path defined in <vector>` and takes the whole icon down with it. That shipped for one build and every launcher fell back to Android's grid-and-robot placeholder.

The board's pawns are cut and coloured by `docs/store/pieces.py` from `reference/pawn.png`, a supplied render of a marble-and-gold piece.

```bash
python docs/store/pieces.py
```

Three things about that cut are load-bearing:

- **The threshold sits above the bloom, not above the background.** The render is on black with a warm halo around the piece. A gate that merely cleared the corners left the halo at partial alpha, and the recolour then dyed it — every pawn wore an aura on the board. The piece is rim-lit all the way round, so a high gate still finds its whole outline.
- **The gold is not tinted.** Only the ivory takes the seat colour, and speculars are forced back to white; a highlight in the seat colour stops the piece looking wet. Two pieces that differed in nothing but hue would be two hues, not two pieces.
- **The ivory render is used twice rather than using the dark one as the second seat.** Blue and red are written into the tutorial, the turn banner and the colour offered when a room is made, in ten languages. Tinting one piece keeps all of that true.

`reference/pawn-dark.png`, `reference/wall.png` and `reference/tile.png` were supplied with it and **nothing uses them yet.** The wall and the tile are drawn isometric — a rhombus seen from above — and this board is an orthogonal grid, where an isometric tile does not tessellate. Using them means either an isometric board, which is a rewrite of the geometry and every touch target, or pulling their materials out as textures. That is a decision, not a chore.

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

