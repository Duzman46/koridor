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
one way than the other.

**The surround is cut away and the frame is taken in.** Both came out of the same complaint —
the board looked small and sat in a visible grey box. The box was the render's own background,
which runs to about #141414 while the app's is #070A0D; it is gone now, dropped by an alpha cut
that finds the board's shape by flooding in from the four corners until the gold hairline round
the outside stops it. The smallness was the moulding: at 11.7 % a side it left the grid only
77 % of the picture, and a board can never be wider than the handset, so the grid had nowhere
else to grow from. [FRAME_SCALE] takes the moulding in without distorting any part of it.
"""
import os

import numpy as np
from PIL import Image
from scipy import ndimage

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

#: How thin to take the frame, as a fraction of the thickness the render gives it.
#:
#: The render's moulding is 11.7 % of the board on every side, so the grid it surrounds is only
#: 77 % of the picture — and on a 1080 px handset, where the board can never be wider than the
#: screen, that left a 71 px tile inside a 1001 px board. Shrinking the frame is the only place
#: the grid can grow from, and by now it is nearly spent: at 0.30 the moulding is under 4 % of
#: the board, the board is already the full width of the phone, and the whole of what is left to
#: win by deleting the frame outright is another eight per cent on a tile.
#:
#: **Nothing is distorted by this, and that is why it is a scale rather than a crop.** The frame
#: is taken in nine patches: each edge band is resampled only *across* itself, where a moulding
#: is a constant profile and cannot show it; each corner is resampled by the same factor on both
#: axes, so the gold ornament in it keeps its shape exactly and merely gets smaller. Cropping
#: instead would have cut the ornaments in half.
FRAME_SCALE = 0.30

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


def silhouette(image):
    """The board's own shape, so the render's surround can be dropped instead of shipped.

    It shipped once with the surround in it and it read as a grey box behind the board with
    square corners — the render's background is not black, it runs to about #141414 along the
    top, and the app's own is #070A0D. There is no threshold that separates them, because the
    frame's *wood* is as dark as the surround it sits on. What does separate them is that a gold
    hairline runs the whole way round the outside: flood the low ground in from the four corners
    and it stops there, and everything the flood could not reach is the board.
    """
    lum = np.asarray(image.convert("RGB")).astype(float).mean(axis=2)
    labels, _ = ndimage.label(lum <= 55.0)
    corners = {labels[2, 2], labels[2, -3], labels[-3, 2], labels[-3, -3]}
    corners.discard(0)
    assert corners, "the four corners are not background — the gate is too low"
    board = ndimage.binary_fill_holes(~np.isin(labels, sorted(corners)))
    return Image.fromarray((board * 255).astype(np.uint8)).convert("L")


def cut(image):
    """The board, its grid squared, its frame taken in, its surround dropped."""
    ax0, ax1 = span(GRID_X)
    ay0, ay1 = span(GRID_Y)

    # Square the grid by stretching the shorter axis. The frame comes along and does not mind.
    scale = (ay1 - ay0) / (ax1 - ax0)
    size = (int(round(image.width * scale)), image.height)
    wide = image.resize(size, Image.LANCZOS)
    alpha = silhouette(image).resize(size, Image.LANCZOS)
    ax0, ax1 = ax0 * scale, ax1 * scale
    fx0, fx1 = FRAME[0] * scale, FRAME[2] * scale

    # The four bands the frame occupies, and the grid between them. The render does not centre
    # its grid in its frame — the left margin is six pixels wider than the right and twenty wider
    # than the top — so these are four separate numbers and stay four.
    margins = (ax0 - fx0, ay0 - FRAME[1], fx1 - ax1, FRAME[3] - ay1)
    thin = [m * FRAME_SCALE for m in margins]
    grid = ax1 - ax0

    def band(box, width, height):
        return wide.crop(box).resize((max(1, int(round(width))), max(1, int(round(height)))),
                                     Image.LANCZOS), \
               alpha.crop(box).resize((max(1, int(round(width))), max(1, int(round(height)))),
                                      Image.LANCZOS)

    # Nine patches. Each edge band is resampled only across itself, where a moulding cannot show
    # it; each corner takes the same factor on both axes, so its ornament keeps its shape.
    xs = [fx0, ax0, ax1, fx1]
    ys = [FRAME[1], ay0, ay1, FRAME[3]]
    out_w = [thin[0], grid, thin[2]]
    out_h = [thin[1], grid, thin[3]]

    pad = max(thin)
    side = int(round(grid + pad * 2))
    face = Image.new("RGB", (side, side), (0, 0, 0))
    cover = Image.new("L", (side, side), 0)
    top = pad - thin[1]
    for row in range(3):
        left = pad - thin[0]
        for column in range(3):
            piece, mask = band((xs[column], ys[row], xs[column + 1], ys[row + 1]),
                               out_w[column], out_h[row])
            face.paste(piece, (int(round(left)), int(round(top))))
            cover.paste(mask, (int(round(left)), int(round(top))))
            left += out_w[column]
        top += out_h[row]

    face.putalpha(cover)
    print("  frame %s -> %s, grid %.1f, board %d px"
          % (tuple(round(m) for m in margins), tuple(round(t) for t in thin), grid, side))
    return face.resize((MASTER, MASTER), Image.LANCZOS), pad, float(side)


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
    image.save(path, format="WEBP", quality=92, method=6, exact=True)
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
