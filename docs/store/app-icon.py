# -*- coding: utf-8 -*-
"""Cuts every icon the app ships out of `reference/simge.png`.

Run it after that file changes, or after the framing below moves. Nothing here is generated: it
is one deterministic crop of one file, resampled, so the tile on the launcher, the tile on the
splash and the tile in the Play listing cannot drift apart from each other.

    python docs/store/app-icon.py

**The source already is an icon**, which is the whole problem this file has to solve. It arrives
with its own gold bezel and its own rounded corners, and an adaptive icon may carry neither: the
launcher applies a mask of its own, and a second frame underneath it does not line up with the
first.

Keeping the bezel was tried and shipped for one build. On paper it worked — the baked radius is
14.5%, inside a squircle's 30% and inside a circle, so the mask crosses the frame's straight runs
rather than its corners and comes back as a gold rim following the mask. On the handset it was
not even: the frame is lit from the top left, so the rim arrives bright along two edges, dim
along the other two, and cut to nothing where the mask pinches. A frame that is only a frame on
half of its perimeter reads as a mistake, which is what the owner called it.

So [ART] is the opening *inside* the bezel, with a margin: the frame's inner edge varies from
81px at the top to 136px at the right — it is a three-dimensional moulding, not a stroke — and
this crop clears the worst of those by a comfortable distance on every side. The gold that
remains at the edges belongs to the walls' own lit top faces, which is artwork rather than
frame. The launcher supplies the only frame there is now.

**The composition is scaled into the visible 72, not cropped to it.** A launcher keeps the
middle 72 units of 108 and throws away the rest, so a full-bleed source loses a third of itself
— here, the outer walls and most of the frame. Laying the whole tile into the visible square
instead means everything the artist framed survives, and the outer sixth (which nothing draws)
is the tile's own edge pushed outwards, blurred and pulled down to the surround's near-black.

**Nothing is regraded.** The photograph this replaced was lit for a six-inch screen and needed
its shadows opened; this file was authored as an icon at icon scale, and the gold already
carries it at 48 pixels.
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SOURCE = os.path.join(ROOT, "reference", "simge.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")
STORE = os.path.join(ROOT, "store-assets")

#: The artwork inside the bezel, as left/top/right/bottom in source pixels. The frame's inner
#: edge was measured across the middle 60% of every side and taken at its worst case — left 111,
#: right 1136, top 81, bottom 1126 — and this is the largest centred square that clears all four
#: with room to spare. Tightened past the minimum on purpose: the extra crop costs board nobody
#: looks at and buys the two pieces roughly a tenth more size at 48 pixels.
ART = (150, 130, 1098, 1078)

#: The corner a tile of this artwork is rounded to where the shape is ours to choose — the splash
#: mark and the feature graphic. Not used for the launcher, which supplies its own mask. Roughly
#: One UI's squircle, so the three agree.
TILE_RADIUS = 0.26

#: The fraction of the 108-unit canvas a launcher mask keeps: 72/108.
VISIBLE = 72 / 108

#: Working resolution of the 108-unit canvas. A multiple of 108 so the visible square lands on a
#: whole number of pixels and the round trip in [visible_of] closes exactly.
CANVAS = 108 * 15

#: How far the canvas reaches past the visible square on each side. DERIVED, never chosen — a
#: mask that keeps 72 of 108 discards a sixth of the canvas per side. An earlier revision picked
#: this by hand as a sixth of the *visible square* instead, which is the same fraction measured
#: against the wrong side, and every export came out 12% tighter than the file claimed.
RING = round(CANVAS * (1 - VISIBLE) / 2)

#: Launcher densities, as (folder suffix, scale). One dp is one pixel at mdpi.
DENSITIES = (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4))

#: Lossy, because this is a render with gradients in it — and at 92 the gold does not band.
QUALITY = 92


def rounded_mask(size, radius=0.22):
    """A legacy launcher shape, at eight times and back, because Pillow will not antialias one."""
    big = size * 8
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, big - 1, big - 1), radius=int(big * radius), fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def canvas():
    """The 108-unit square: the tile filling the visible middle, a fabricated ring around it."""
    side = CANVAS - 2 * RING
    tile = Image.open(SOURCE).convert("RGB").crop(ART).resize((side, side), Image.LANCZOS)

    grown = np.pad(np.asarray(tile), ((RING, RING), (RING, RING), (0, 0)), mode="edge")
    plate = Image.fromarray(grown)
    blurred = plate.filter(ImageFilter.GaussianBlur(CANVAS * 0.03))

    ring = Image.new("L", (CANVAS, CANVAS), 255)
    ImageDraw.Draw(ring).rectangle((RING, RING, CANVAS - RING - 1, CANVAS - RING - 1), fill=0)
    plate = Image.composite(blurred, plate, ring.filter(ImageFilter.GaussianBlur(CANVAS * 0.004)))

    # Pull the ring down to the surround's own near-black, so a launcher with an unusually wide
    # mask finds an edge there rather than a smear of gold.
    shade = Image.new("L", (CANVAS, CANVAS), 0)
    pen = ImageDraw.Draw(shade)
    steps = 40
    for step in range(steps):
        inset = int(CANVAS * 0.5 * step / steps)
        pen.rectangle((inset, inset, CANVAS - inset, CANVAS - inset), outline=int(210 * (1 - step / steps)))
    shade = Image.composite(shade.filter(ImageFilter.GaussianBlur(CANVAS * 0.018)),
                            Image.new("L", (CANVAS, CANVAS), 0), ring)
    return Image.composite(Image.new("RGB", (CANVAS, CANVAS), (9, 8, 7)), plate, shade)


def visible_of(square):
    """What is left of the canvas after a launcher mask: the middle 72 of 108, i.e. the tile."""
    kept = square.crop((RING, RING, square.width - RING, square.height - RING))
    # The round trip has to close, or the framing drifts silently the next time it is moved.
    expected = round(CANVAS * VISIBLE)
    assert kept.width == expected, "ring and visible square disagree: %d != %d" % (kept.width, expected)
    return kept


def save(image, path, **options):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, **options)
    print("  %-58s %6.1f KB" % (os.path.relpath(path, ROOT), os.path.getsize(path) / 1024))


if __name__ == "__main__":
    plate = canvas()
    visible = visible_of(plate)

    print("adaptive background — the whole 108-unit canvas, opaque, mask applied by the launcher")
    for suffix, scale in DENSITIES:
        size = int(round(108 * scale))
        save(
            plate.resize((size, size), Image.LANCZOS),
            os.path.join(RES, "mipmap-" + suffix, "ic_launcher_background.webp"),
            format="WEBP", quality=QUALITY, method=6,
        )

    print("legacy bitmap — nothing masks this one, so it carries its own rounded alpha")
    for suffix, scale in DENSITIES:
        size = int(round(48 * scale))
        tile = visible.resize((size, size), Image.LANCZOS).convert("RGBA")
        tile.putalpha(rounded_mask(size))
        save(
            tile,
            os.path.join(RES, "mipmap-" + suffix, "ic_launcher.webp"),
            format="WEBP", quality=QUALITY, method=6, exact=True,
        )

    print("splash mark — the tile, square; KoridorMark rounds it to TILE_RADIUS")
    save(
        visible.resize((432, 432), Image.LANCZOS),
        os.path.join(RES, "drawable-xxxhdpi", "app_mark.webp"),
        format="WEBP", quality=QUALITY, method=6,
    )

    print("Play listing — the tile again, opaque to the corners, as Google Play requires")
    save(
        visible.resize((512, 512), Image.LANCZOS),
        os.path.join(STORE, "play-icon-512.png"),
        format="PNG", optimize=True,
    )
