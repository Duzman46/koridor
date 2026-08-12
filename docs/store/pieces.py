# -*- coding: utf-8 -*-
"""Cuts the board pieces out of the supplied renders and colours them for the two seats.

    python docs/store/pieces.py

The pawn used to be computed here — a profile spun about an axis and shaded per pixel with a
real normal. That was the right answer while there was nothing better, and there now is:
`reference/pawn.png` is a rendered piece with real materials, marble against brushed gold, and
no amount of Blinn-Phong reaches that. The arithmetic is gone; what is left is a cut-out and a
recolour, both of which have to stay reproducible from the repository.

**Why the ivory render twice rather than the ivory and the dark one.** Two pawns were supplied.
Using them as the two seats would rename the seats: blue and red are written into the tutorial,
into the turn banner and into the colour a player is offered when a room is made, in ten
languages. Tinting one piece keeps every one of those sentences true — and it is also how a real
set works: one mould, two finishes, the same metal on both.

**The gold is not tinted.** A seat colour that swallowed the hardware would leave two pieces
differing in nothing but hue. Keeping the metal means they read as the same object in two
colours, which is exactly what they are.
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
SOURCE = os.path.join(ROOT, "reference", "pawn.png")
TILE_SOURCE = os.path.join(ROOT, "reference", "tile.png")

#: The tile's top face in the supplied render, as (top, left, right) in source pixels. It is a
#: solid seen from above at an angle, so that face is a rhombus; the fourth corner completes the
#: parallelogram. Measured off the render's own silhouette rather than guessed.
TILE_FACE = ((759, 54), (245, 497), (1287, 509))

#: Seat colours, and they are SeatColors' own. Literals rather than parsed out of Kotlin: a
#: sprite that silently followed a token would be a sprite nobody remembered to re-render.
SEATS = {
    "blue": (0x3A, 0x7C, 0xFA),
    "red": (0xEC, 0x42, 0x38),
}

#: Height the sprite is mastered at, before the density downsample.
MASTER_HEIGHT = 512

#: The flattened tile's master side.
TILE_SIZE = 512

#: Densities it ships at, as (folder, fraction of the master).
DENSITIES = (("xhdpi", 0.25), ("xxhdpi", 0.375), ("xxxhdpi", 0.5))

#: Luminance either side of the piece's edge.
#:
#: Set ABOVE the bloom rather than at the background. The render sits on black with a warm halo
#: around the piece, and a threshold that merely cleared the corners left that halo at partial
#: alpha — which the recolour below then dyed, giving each pawn a coloured aura on the board.
#: The piece is rim-lit all the way round its silhouette, so a gate this high still finds its
#: whole outline while the bloom falls entirely outside it.
EDGE_LOW, EDGE_HIGH = 104.0, 138.0

#: How gold a pixel has to be, as red minus blue, to be left alone by the recolour. The ivory
#: runs about 25 and the metal about 157, so the gate sits between them with room either side.
GOLD_GATE = 62.0


def cut_out(image):
    """Alpha for the piece: the bloom behind it discarded, its own dark parts kept."""
    pixels = np.asarray(image.convert("RGB")).astype(float)
    lum = pixels.mean(axis=2)

    # A soft edge rather than a hard one — at sprite size a binary cut reads as a sticker.
    alpha = np.clip((lum - EDGE_LOW) / (EDGE_HIGH - EDGE_LOW), 0.0, 1.0)

    # The piece has dark parts of its own: the shadowed underside, the grooves between the gold
    # rings. They are below the threshold and they are also *enclosed*, so they are found by
    # asking what the background can reach rather than by how bright anything is.
    reachable = Image.new("L", image.size)
    reachable.putdata((alpha.reshape(-1) * 255).astype(np.uint8).tolist())
    for corner in ((0, 0), (image.width - 1, 0), (0, image.height - 1),
                   (image.width - 1, image.height - 1)):
        ImageDraw.floodfill(reachable, corner, 255, thresh=40)
    outside = np.asarray(reachable).astype(float) >= 255

    return np.clip(np.where(outside, alpha, 1.0), 0.0, 1.0)


def recolour(image, alpha, tint):
    """The ivory taken to [tint], the gold left alone, the speculars left white."""
    pixels = np.asarray(image.convert("RGB")).astype(float)
    lum = pixels.mean(axis=2)
    goldness = pixels[:, :, 0] - pixels[:, :, 2]

    # 0 where the pixel is ivory, 1 where it is metal, ramped between the two so the boundary
    # does not become a hard line the recolour draws attention to.
    metal = np.clip((goldness - GOLD_GATE * 0.55) / (GOLD_GATE * 0.9), 0.0, 1.0)[:, :, None]

    # Dividing by the ivory's own mid tone keeps the piece's modelling instead of flattening
    # every surface to the tint.
    reference = 196.0
    scaled = (lum / reference)[:, :, None] * np.array(tint, dtype=float)[None, None, :]

    # A specular is white on any material. Without this the highlight takes the seat colour and
    # the piece stops looking wet.
    gloss = np.clip((lum - 232.0) / 23.0, 0.0, 1.0)[:, :, None]
    tinted = scaled * (1.0 - gloss) + 255.0 * gloss

    blended = tinted * (1.0 - metal) + pixels * metal
    return Image.fromarray(
        np.dstack([np.clip(blended, 0, 255), alpha * 255.0]).astype(np.uint8)
    )


def flatten_tile():
    """The tile's top face, taken off the rhombus and laid flat.

    An orthogonal grid needs a square, and the render is isometric. A perspective transform from
    the face's four corners is the whole of it — no repainting, so the wood grain, the gold
    inlay and the marble survive exactly as they were rendered.
    """
    top, left, right = TILE_FACE
    bottom = (left[0] + right[0] - top[0], left[1] + right[1] - top[1])
    quad = [top, right, bottom, left]
    square = [(0, 0), (TILE_SIZE, 0), (TILE_SIZE, TILE_SIZE), (0, TILE_SIZE)]

    rows = []
    for (x, y), (u, v) in zip(square, quad):
        rows.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        rows.append([0, 0, 0, x, y, 1, -v * x, -v * y])
    matrix = np.array(rows, dtype=float)
    target = np.array([c for point in quad for c in point], dtype=float)
    solved = np.linalg.solve(matrix.T @ matrix, matrix.T @ target)

    return Image.open(TILE_SOURCE).convert("RGB").transform(
        (TILE_SIZE, TILE_SIZE), Image.PERSPECTIVE, tuple(solved), Image.BICUBIC,
    )


def trim(image):
    """Cropped to the piece, so the renderer scales a pawn and not a box with a pawn in it."""
    box = image.getbbox()
    if box is None:
        return image
    margin = int(round(image.height * 0.006))
    return image.crop((
        max(0, box[0] - margin), max(0, box[1] - margin),
        min(image.width, box[2] + margin), min(image.height, box[3] + margin),
    ))


def save(image, folder, name):
    path = os.path.join(RES, folder, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, format="WEBP", quality=94, method=6, exact=True)
    print("  %-46s %6.1f KB" % (os.path.relpath(path, ROOT), os.path.getsize(path) / 1024))


if __name__ == "__main__":
    master = Image.open(SOURCE)
    mask = cut_out(master)
    for seat, tint in SEATS.items():
        piece = recolour(master, mask, tint)
        # Soften what the flood fill left blocky, before anything is scaled.
        piece.putalpha(piece.getchannel("A").filter(ImageFilter.GaussianBlur(1.2)))
        piece = trim(piece)
        print("pawn_%s  master %dx%d" % (seat, piece.width, piece.height))
        for folder, fraction in DENSITIES:
            height = max(1, int(round(MASTER_HEIGHT * fraction)))
            width = max(1, int(round(piece.width * height / piece.height)))
            save(piece.resize((width, height), Image.LANCZOS),
                 "drawable-" + folder, "pawn_%s.webp" % seat)

    tile = flatten_tile()
    print("board_tile  master %dx%d" % tile.size)
    for folder, fraction in DENSITIES:
        side = max(1, int(round(TILE_SIZE * fraction)))
        save(tile.resize((side, side), Image.LANCZOS), "drawable-" + folder, "board_tile.webp")
