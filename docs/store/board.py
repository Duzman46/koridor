# -*- coding: utf-8 -*-
"""Cuts the board's face out of the supplied whole-board render.

    python docs/store/board.py

**Why a whole board and not a tile.** A tile render was tried first and reverted the same day.
The reason it failed is worth keeping written down, because the mistake is an easy one to make
twice: a single tile carries its own frame, its own centre inlay and its own baked highlight,
and repeating it eighty-one times repeats all three. The frames read as eighty-one separate
objects rather than one board, the inlays read as a rash, and the highlights all fall the same
way — which is the one thing light on a real surface never does. `reference/tahta.png` is the
answer to that: one board, lit once, with the grid, the frame and both goal rows already in it.

**The picture is the source of truth for the geometry, not the other way round.** The renderer
draws pawns and walls on top of this image, so if the code's lattice and the picture's lattice
disagree by even a couple of percent the pieces stand on the grooves. So this script measures
the render's own grid and prints the two ratios `Constants.Board` has to carry; those constants
are copied from this script's output and nowhere else. Re-run it and re-copy if the artwork
changes.

**The grid is squared and the frame is not.** The render's grid is 909.5 px across and 930.2 px
down — 2.3 % out of square, which is invisible on its own but would show as pawns drifting off
centre by the eighth column. The fix is an anisotropic resample that makes the grid exactly
square; the frame around it is a plain moulding and does not care that it ends up 2 % thicker
one way than the other. What is left over on each side is the render's own near-black surround,
between 1 % and 3 % of the image, and the app draws the board on a dark background, so it is
the sliver that makes the padding uniform rather than a border anybody sees.
"""
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
SOURCE = os.path.join(ROOT, "reference", "tahta.png")

#: Where the 9x9 grid sits in the source, as (first tile's leading edge, step, tile side).
#: Measured off the render's own channels — the tiles are a calm plateau and the channels are a
#: narrow storm of shadow and gold, so a plain threshold across a scan band finds all nine runs.
#: Held as literals rather than re-measured on every run so a change to the artwork fails the
#: assertion below instead of silently re-cutting to a different grid.
GRID_X = (172.64, 102.700, 87.89)
GRID_Y = (157.49, 104.933, 90.78)

#: The frame's outer edge in the source, as (x0, y0, x1, y1). Everything beyond it is surround.
FRAME = (34, 39, 1215, 1211)

#: How much surround to leave past the widest frame margin. Small: it exists to make the four
#: paddings equal, not to be seen.
BLEED = 10.0

#: The master's side, and the densities it ships at.
MASTER = 1234
DENSITIES = (("xhdpi", 0.50), ("xxhdpi", 0.75), ("xxxhdpi", 1.00))


def span(grid):
    """The grid's extent: the first tile's leading edge to the last tile's trailing edge."""
    first, step, tile = grid
    return first, first + 8 * step + tile


def verify(image):
    """The render still has the grid this file says it has."""
    lum = np.asarray(image.convert("RGB")).astype(float).mean(axis=2)

    def runs(profile, lo, hi):
        found, start = [], None
        for i in range(lo, hi):
            if profile[i] > 30.0 and start is None:
                start = i
            elif profile[i] <= 30.0 and start is not None:
                if i - start > 50:
                    found.append(start)
                start = None
        return found

    for axis, profile, lo, hi, grid in (
        ("x", lum[600:640, :].mean(axis=0), 160, 1100, GRID_X),
        ("y", lum[:, 600:640].mean(axis=1), 140, 1100, GRID_Y),
    ):
        starts = runs(profile, lo, hi)
        assert len(starts) == 9, "%s: found %d tiles, not 9" % (axis, len(starts))
        first, step, _ = grid
        drift = max(abs(s - (first + k * step)) for k, s in enumerate(starts))
        assert drift < 3.0, "%s: grid moved by %.1f px — re-measure" % (axis, drift)
        print("  %s grid checked: 9 tiles, worst drift %.2f px" % (axis, drift))


def cut(image):
    """The board, its grid squared, cropped to a uniform padding."""
    ax0, ax1 = span(GRID_X)
    ay0, ay1 = span(GRID_Y)

    # Square the grid by stretching the shorter axis. The frame comes along and does not mind.
    scale = (ay1 - ay0) / (ax1 - ax0)
    wide = image.resize(
        (int(round(image.width * scale)), image.height), Image.LANCZOS
    )
    ax0, ax1 = ax0 * scale, ax1 * scale
    fx0, fx1 = FRAME[0] * scale, FRAME[2] * scale

    # The padding is set by the widest frame margin, so no side of the frame is ever clipped.
    pad = BLEED + max(ax0 - fx0, fx1 - ax1, ay0 - FRAME[1], FRAME[3] - ay1)
    box = (ax0 - pad, ay0 - pad, ax1 + pad, ay1 + pad)
    assert box[0] >= 0 and box[1] >= 0, "padding runs off the top left of the render"
    assert box[2] <= wide.width and box[3] <= image.height, "padding runs off the bottom right"

    cropped = wide.crop(tuple(int(round(v)) for v in box))
    return cropped.resize((MASTER, MASTER), Image.LANCZOS), pad, box[2] - box[0]


def ratios(pad, side):
    """The two numbers `Constants.Board` has to agree with, derived from the crop itself.

    The gap is measured on the y axis because that is the axis nothing was stretched on — x was
    resampled to match it, so after the cut the two are the same grid and y is the one that never
    passed through an interpolation.
    """
    tile = GRID_Y[2]
    gap = GRID_Y[1] - tile
    return pad / side, gap / tile


def save(image, folder, name):
    path = os.path.join(RES, folder, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, format="WEBP", quality=92, method=6)
    print("  %-48s %7.1f KB" % (os.path.relpath(path, ROOT), os.path.getsize(path) / 1024))


if __name__ == "__main__":
    master = Image.open(SOURCE).convert("RGB")
    print("source %dx%d" % master.size)
    verify(master)

    board, pad, side = cut(master)
    frame_ratio, gap_ratio = ratios(pad, side)
    print("\nboard master %dx%d" % board.size)
    for folder, fraction in DENSITIES:
        px = int(round(MASTER * fraction))
        save(board.resize((px, px), Image.LANCZOS), "drawable-" + folder, "board_surface.webp")

    print("\nConstants.Board must carry these, and this is the only place they come from:")
    print("    FRAME_RATIO = %.5ff" % frame_ratio)
    print("    GAP_RATIO   = %.5ff" % gap_ratio)
    print("\n  check: a %d px board gives padding %.2f, tile %.2f, gap %.2f, step %.2f"
          % (MASTER, MASTER * frame_ratio,
             (MASTER - 2 * MASTER * frame_ratio) / (9 + gap_ratio * 8),
             (MASTER - 2 * MASTER * frame_ratio) / (9 + gap_ratio * 8) * gap_ratio,
             (MASTER - 2 * MASTER * frame_ratio) / (9 + gap_ratio * 8) * (1 + gap_ratio)))
