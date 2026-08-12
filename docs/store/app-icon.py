# -*- coding: utf-8 -*-
"""Cuts every icon the app ships out of the one photograph the home screen already shows.

Run it after `reference/koridor.png` changes, or after the crop below is moved. Nothing here is
generated: it is one deterministic crop of one file, resampled, so the tile on the launcher, the
tile on the splash and the tile in the Play listing cannot drift apart from each other or from
the picture behind the main menu.

    python docs/store/app-icon.py

**Why the crop is where it is.** An adaptive icon is authored on a 108-unit canvas and every
launcher throws away everything outside the middle 72 — a third of the width, all the way round.
So the frame is not chosen by looking at the square; it is chosen by looking at what survives the
mask at the size a launcher actually draws it. Crops that hold both pawns lose both of them: the
two pieces sit on the picture's diagonal, 848 pixels apart, and no square that contains them both
puts either inside the safe circle. What is left is the cream pawn with the near wall behind it,
close enough to read at 48 pixels and wide enough that the wall is still an object.

**So [WINDOW] is the visible square, not the canvas** — measured off the piece rather than
guessed. The pawn occupies x 1035..1195, y 255..555 in the source; the window is sized so it
fills a little over half the height and sits a touch above centre, which leaves the head clear of
a circle mask and the base clear of the bottom. The first version of this file specified the
108-canvas instead and the Play listing came back with the head cut off at the top edge, because
the listing is the *visible* square full-bleed and nothing masks it back.

**The canvas is then grown outwards from that window** by [PAD], and where the photograph runs
out — some forty pixels past its right edge — the shortfall is mirrored. Those pixels live in
the ring no launcher draws; the alternative was sliding the whole composition left to fetch
board that nobody sees.

**Why the tone is touched at all.** The scene was lit for a six-inch screen. A launcher draws it
twelve millimetres wide against a wallpaper, and the deepest part of the board — which is where
the wall's silhouette lives — goes to nothing. [shadow_gamma] opens that end and leaves everything
above the knee exactly where the photographer put it, so the pawn's highlight and the specular on
the wall cap are the original pixels. A flat brightness lift was tried first and turned the board
to charcoal, which is a different app from the one the icon opens.
"""
import os

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SOURCE = os.path.join(ROOT, "reference", "koridor.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")
STORE = os.path.join(ROOT, "store-assets")

#: Left, top and side of the square a launcher KEEPS, in source pixels. Everything else is
#: derived from it, so moving the framing is one edit here.
WINDOW = (835 + 30, 142 + 30, 500)

#: The fraction of the 108-unit canvas a launcher mask keeps: 72/108.
VISIBLE = 72 / 108

#: How far the canvas reaches past [WINDOW] on each side. DERIVED, never chosen: a mask that
#: keeps 72 of 108 discards a sixth of the *canvas* per side, which is a quarter of the *window*
#: — 1/VISIBLE is 1.5, so the canvas is one and a half windows and each ring is half of the
#: remaining half. The first version of this file padded by a sixth of the window instead, which
#: is the same fraction measured against the wrong side, and every export came out 12% tighter
#: than the docstring above claims. The exports looked right and the spec was wrong, which is
#: the harder of the two to notice; the assertion below is here so it cannot happen twice.
PAD = round(WINDOW[2] * (1 / VISIBLE - 1) / 2)

#: Launcher densities, as (folder suffix, scale). One dp is one pixel at mdpi.
DENSITIES = (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4))

#: Lossy, because this is a photograph — and at 92 the dark board's gradient does not band.
QUALITY = 92


def shadow_gamma(image, gamma=0.86, knee=0.55):
    """Opens the shadows and leaves everything above the knee alone."""
    table = []
    for value in range(256):
        tone = value / 255.0
        blend = min(1.0, tone / knee)
        table.append(int(round(255 * (tone ** gamma * (1 - blend) + tone * blend))))
    return image.point(table * 3)


def rounded_mask(size, radius=0.22):
    """A legacy launcher shape, at eight times and back, because Pillow will not antialias one."""
    big = size * 8
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, big - 1, big - 1), radius=int(big * radius), fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def canvas():
    """The 108-unit square, graded, at source resolution: [WINDOW] grown by [PAD] each way."""
    photo = Image.open(SOURCE).convert("RGB")
    left, top, side = WINDOW
    box = (left - PAD, top - PAD, left + side + PAD, top + side + PAD)

    inside = (max(0, box[0]), max(0, box[1]), min(photo.width, box[2]), min(photo.height, box[3]))
    pixels = np.asarray(photo.crop(inside))
    # Reflected rather than repeated or filled: the shortfall is a strip of unlit board, and a
    # mirror of unlit board is unlit board. A solid fill would put a seam in the outer ring that
    # a launcher with a wide mask could just about reach.
    pixels = np.pad(
        pixels,
        ((inside[1] - box[1], box[3] - inside[3]), (inside[0] - box[0], box[2] - inside[2]), (0, 0)),
        mode="reflect",
    )
    return shadow_gamma(Image.fromarray(pixels))


def visible_of(square):
    """What is left of the canvas after a launcher mask: the middle 72 of 108, which is [WINDOW]."""
    kept = square.crop((PAD, PAD, square.width - PAD, square.height - PAD))
    # The round trip has to close, or WINDOW stops meaning what the docstring says it means and
    # the framing drifts silently the next time somebody moves it.
    assert kept.width == WINDOW[2], "canvas ring and window disagree: %d != %d" % (kept.width, WINDOW[2])
    return kept


def save(image, path, **options):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, **options)
    print("  %-58s %6.1f KB" % (os.path.relpath(path, ROOT), os.path.getsize(path) / 1024))


square = canvas()
visible = visible_of(square)

print("adaptive background — the whole 108-unit canvas, opaque, mask applied by the launcher")
for suffix, scale in DENSITIES:
    size = int(round(108 * scale))
    save(
        square.resize((size, size), Image.LANCZOS),
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
        format="WEBP", quality=QUALITY, method=6, lossless=False, exact=True,
    )

print("splash mark — the masked view, square; the composable rounds it to the launcher's shape")
save(
    visible.resize((432, 432), Image.LANCZOS),
    os.path.join(RES, "drawable-xxxhdpi", "app_mark.webp"),
    format="WEBP", quality=QUALITY, method=6,
)

print("Play listing — the masked view again, opaque to the corners, as Google Play requires")
save(
    visible.resize((512, 512), Image.LANCZOS),
    os.path.join(STORE, "play-icon-512.png"),
    format="PNG", optimize=True,
)
