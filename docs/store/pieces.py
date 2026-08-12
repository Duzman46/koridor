# -*- coding: utf-8 -*-
"""Renders the board's pawns as shaded objects, because vectors cannot be objects.

The board is drawn on a Compose Canvas out of rounded rectangles, circles and gradients, and
that is the right tool for tiles: a tile IS a rounded rectangle. A pawn is not. It is a turned
solid, and a circle with a lighter circle dropped on it reads as a symbol of one however the
gradient is tuned — the eye wants the highlight to travel round the form, the terminator to
curve, and the underside to pick the board back up.

So the pawn is computed rather than approximated. Each piece is a surface of revolution: a
profile r(y) spun about the vertical axis, shaded per pixel with a real normal.

    python docs/store/pieces.py

**Why this and not a modelling package.** The output has to be reproducible from the repository
by anybody who changes a seat colour, and it has to sit beside the code that decides those
colours. Sixty lines of arithmetic that anyone can read and re-run beats a binary somebody
exported once and cannot regenerate.
"""
import math
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")

#: Seat colours, and they are the ones SeatColors hands the renderer. Kept here as literals
#: rather than parsed out of Kotlin: a sprite that silently followed a token would be a sprite
#: nobody remembered to re-render.
SEATS = {
    "blue": (0x3A, 0x7C, 0xFA),
    "red": (0xEC, 0x42, 0x38),
}

#: Sprite side in pixels, before the density downsample. Four times the largest a pawn is ever
#: drawn (a tile on a tablet), so every screen samples down rather than up.
SIZE = 512

#: Supersampling. The silhouette is the whole read at small sizes and an aliased one looks
#: cheap however good the shading is.
SS = 3

#: Densities the sprite ships at, as (folder, fraction of SIZE).
DENSITIES = (("xhdpi", 0.25), ("xxhdpi", 0.375), ("xxxhdpi", 0.5))


def profile(y):
    """Radius of the piece at height [y], where 0 is the foot and 1 the crown.

    A real pawn, in five moves: a flared foot, a step in, a waisted stem, a collar and a dome.
    Written as one piecewise function so the silhouette is continuous — a stack of separate
    solids is what the vector version was, and its joins were visible.
    """
    if y < 0.05:                                     # the foot's rolled edge
        return 0.50
    if y < 0.20:                                     # the skirt: a quarter ellipse, concave
        t = (y - 0.05) / 0.15
        return 0.19 + 0.31 * math.sqrt(max(0.0, 1.0 - t * t))
    if y < 0.26:                                     # the step off the base
        t = (y - 0.20) / 0.06
        return 0.19 - 0.03 * t
    if y < 0.56:                                     # the stem, waisted
        t = (y - 0.26) / 0.30
        return 0.16 - 0.035 * math.sin(t * math.pi * 0.75)
    if y < 0.64:                                     # the collar
        t = (y - 0.56) / 0.08
        return 0.155 + 0.145 * math.sin(t * math.pi)
    if y < 0.68:                                     # the neck
        return 0.15
    t = (y - 0.68) / 0.32                            # the dome
    return 0.33 * math.sqrt(max(0.0, 1.0 - (t * 2 - 1) ** 2)) if t < 1 else 0.0


def render(rgb):
    """One pawn, shaded per pixel."""
    side = SIZE * SS
    # Light from the upper left and slightly in front, which is where every other highlight in
    # this app comes from.
    light = np.array([-0.42, 0.78, 0.46])
    light /= np.linalg.norm(light)
    view = np.array([0.0, 0.0, 1.0])
    base = np.array(rgb, dtype=float) / 255.0

    xs = (np.arange(side) + 0.5) / side * 2 - 1          # -1 .. 1, left to right
    ys = 1.0 - (np.arange(side) + 0.5) / side            # 1 at the top, 0 at the foot
    radii = np.array([profile(y) for y in ys])

    # Slope of the profile, for the vertical component of the normal.
    slope = np.gradient(radii, ys)

    rgba = np.zeros((side, side, 4), dtype=float)
    for row in range(side):
        r = radii[row]
        if r <= 0.0005:
            continue
        # How far across the body each pixel is, as a fraction of its radius.
        across = xs / r
        inside = np.abs(across) <= 1.0
        if not inside.any():
            continue
        sin_t = np.clip(across, -1.0, 1.0)
        cos_t = np.sqrt(np.maximum(0.0, 1.0 - sin_t ** 2))

        # Normal of a surface of revolution: outward in the horizontal plane, tilted by the
        # profile's slope. Normalised per pixel.
        nx = sin_t
        ny = np.full_like(sin_t, slope[row])
        nz = cos_t
        length = np.sqrt(nx ** 2 + ny ** 2 + nz ** 2)
        nx, ny, nz = nx / length, ny / length, nz / length

        lambert = np.clip(nx * light[0] + ny * light[1] + nz * light[2], 0.0, 1.0)
        # Blinn-Phong, with a tight lobe: this is enamel, not chalk.
        hx, hy, hz = light[0] + view[0], light[1] + view[1], light[2] + view[2]
        hlen = math.sqrt(hx * hx + hy * hy + hz * hz)
        spec = np.clip(nx * hx / hlen + ny * hy / hlen + nz * hz / hlen, 0.0, 1.0) ** 38

        # A cool bounce along the lower right, where a piece on a lit board picks the board up.
        bounce = np.clip(-(nx * light[0] + ny * light[1] + nz * light[2]), 0.0, 1.0) ** 2 * 0.13
        # And a rim, so the silhouette survives against a near-black tile.
        rim = (1.0 - cos_t) ** 3 * 0.17

        shade = 0.16 + 0.86 * lambert + bounce
        colour = base[None, :] * shade[:, None] + rim[:, None] * 0.55 + spec[:, None] * 1.15
        rgba[row, inside, :3] = np.clip(colour[inside], 0.0, 1.0)
        rgba[row, inside, 3] = 1.0

    image = Image.fromarray((rgba * 255).astype(np.uint8), mode="RGBA")
    return image.resize((SIZE, SIZE), Image.LANCZOS)


def save(image, folder, name):
    path = os.path.join(RES, folder, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, format="WEBP", quality=94, method=6, exact=True)
    print("  %-46s %6.1f KB" % (os.path.relpath(path, ROOT), os.path.getsize(path) / 1024))


if __name__ == "__main__":
    for seat, rgb in SEATS.items():
        piece = render(rgb)
        print("pawn_%s" % seat)
        for folder, fraction in DENSITIES:
            size = int(round(SIZE * fraction))
            save(piece.resize((size, size), Image.LANCZOS),
                 "drawable-" + folder, "pawn_%s.webp" % seat)
