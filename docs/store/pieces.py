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
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
SOURCE = os.path.join(ROOT, "reference", "pawn.png")
WALL_SOURCE = os.path.join(ROOT, "reference", "wall.png")

#: Seat colours, and they are SeatColors' own. Literals rather than parsed out of Kotlin: a
#: sprite that silently followed a token would be a sprite nobody remembered to re-render.
SEATS = {
    "blue": (0x3A, 0x7C, 0xFA),
    "red": (0xEC, 0x42, 0x38),
}

#: Height the sprite is mastered at, before the density downsample.
MASTER_HEIGHT = 512

#: Densities it ships at, as (folder, fraction of the master).
DENSITIES = (("xhdpi", 0.25), ("xxhdpi", 0.375), ("xxxhdpi", 0.5))

#: The wall sprite's proportions, which are `Constants.Board`'s and have to stay its. A wall is
#: two tiles and the channel between them long, and as thick as the channel times the overhang.
#: Getting this wrong does not distort a little — it distorts the gold caps, which is the one
#: part of the piece an eye reads as a manufactured object rather than a smear.
GAP_RATIO = 0.15590
WALL_THICKNESS_RATIO = 1.91
WALL_HEIGHT = 128

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
    """The ivory taken to [tint], the gold left alone, the speculars left white.

    A duotone and not a multiply. Multiplying the render's luminance by the seat colour was the
    obvious thing and it made two plastic beads: the ivory sits between 200 and 240, so dividing
    by a mid tone put the whole body at or above the tint's full value and every surface came out
    the same saturated hue. Marble does not do that — it goes pale where the light hits and keeps
    its hue only in the mid tones. So the ramp runs dark-tint → tint → nearly-white, and the piece
    keeps the modelling that made the render worth using.
    """
    pixels = np.asarray(image.convert("RGB")).astype(float)
    lum = pixels.mean(axis=2)
    goldness = pixels[:, :, 0] - pixels[:, :, 2]

    # 0 where the pixel is ivory, 1 where it is metal, ramped between the two so the boundary
    # does not become a hard line the recolour draws attention to.
    metal = np.clip((goldness - GOLD_GATE * 0.55) / (GOLD_GATE * 0.9), 0.0, 1.0)[:, :, None]

    hue = np.array(tint, dtype=float)
    shadow = hue * 0.13
    # Not white: a highlight that goes all the way to paper leaves two pale pieces that have to
    # be told apart by their mid tones alone, and the board is dark enough that the mid tones are
    # the smallest part of the piece.
    highlight = hue + (255.0 - hue) * 0.44

    #: Where the ramp turns from "gaining the hue" to "losing it to the light".
    #:
    #: High, and that is the whole trick. The render's ivory sits between 0.6 and 0.94 of full
    #: luminance — it is a *pale* material — so a knee at two thirds put almost the entire piece
    #: on the far side of it and produced a baby blue pawn and a pink one. At 0.86 only the top
    #: of the dome and the lit shoulder cross over, which is exactly where a real piece goes pale.
    KNEE = 0.86
    t = np.clip(lum / 255.0, 0.0, 1.0)
    low = t / KNEE
    high = (t - KNEE) / (1.0 - KNEE)
    ramped = np.where(
        (t < KNEE)[:, :, None],
        shadow[None, None, :] + (hue - shadow)[None, None, :] * np.clip(low, 0, 1)[:, :, None],
        hue[None, None, :] + (highlight - hue)[None, None, :] * np.clip(high, 0, 1)[:, :, None],
    )

    # A specular is white on any material. Without this the highlight takes the seat colour and
    # the piece stops looking wet.
    gloss = np.clip((lum - 243.0) / 12.0, 0.0, 1.0)[:, :, None]
    tinted = ramped * (1.0 - gloss) + 255.0 * gloss

    blended = tinted * (1.0 - metal) + pixels * metal
    return Image.fromarray(
        np.dstack([np.clip(blended, 0, 255), alpha * 255.0]).astype(np.uint8)
    )


def piece_mask(pixels):
    """The wall against its bloom: wood or metal, and the largest single thing of either."""
    r, g, b = pixels[:, :, 0], pixels[:, :, 1], pixels[:, :, 2]
    # Wood is red against green; the metal is yellow against blue; the glow behind them is
    # neither, which is what separates them from it — luminance alone does not, because the
    # bloom and the wood in shadow land on the same value.
    mask = ((r - g > 42) | (g - b > 58)) & (pixels.mean(axis=2) > 40)
    labels, count = ndimage.label(mask)
    areas = ndimage.sum(mask, labels, range(1, count + 1))
    return ndimage.binary_fill_holes(labels == (1 + int(np.argmax(areas))))


def flatten_wall():
    """The wall render laid flat, then stretched to the proportions the board needs.

    The render is a bar seen from above at an angle. A box drawn that way has *vertical* edges
    that are still vertical on the page, so one shear along y — by the slope of the bar's own
    axis — lays it horizontal without touching anything else. No perspective solve, no corner
    hunting: the wood grain, the specular along the top edge and both gold caps come through as
    they were rendered.

    Then the proportions. The board's wall is about seven times longer than it is thick and the
    flat bar is about two, so the middle is stretched and **the caps are not** — stretched caps
    are the one part of this an eye reads immediately as wrong, because a cap is a manufactured
    object with a known shape and a plank is not. The wood between them takes the whole stretch,
    lengthwise, along its own grain, at a size where the grain is a warm blur anyway.
    """
    source = Image.open(WALL_SOURCE).convert("RGB")
    mask = piece_mask(np.asarray(source).astype(float))

    rows, columns = np.nonzero(mask)
    sampled = np.arange(columns.min() + 40, columns.max() - 40, 10)
    middles = np.array([rows[columns == x].mean() for x in sampled])
    slope = np.polyfit(sampled, middles, 1)[0]

    lift = int(np.ceil(slope * source.width))
    flat = source.transform((source.width, source.height + lift), Image.AFFINE,
                            (1, 0, 0, slope, 1, -lift), Image.BICUBIC)

    flat_mask = piece_mask(np.asarray(flat).astype(float))
    rows, columns = np.nonzero(flat_mask)
    box = (columns.min(), rows.min(), columns.max() + 1, rows.max() + 1)
    bar = flat.crop(box)
    alpha = Image.fromarray((flat_mask[box[1]:box[3], box[0]:box[2]] * 255).astype(np.uint8))
    bar.putalpha(alpha.filter(ImageFilter.GaussianBlur(1.0)))

    # Where the caps end: the middle of the bar is wood and carries no metal at all, so the last
    # column with any is the cap's inside edge.
    face = np.asarray(bar.convert("RGB")).astype(float)
    metal = (face[:, :, 1] - face[:, :, 2] > 58) & (face.mean(axis=2) > 70)
    inside = np.asarray(bar.getchannel("A")).astype(float) > 200
    share = np.array([metal[:, x].sum() / max(1.0, inside[:, x].sum()) for x in range(bar.width)])
    third = bar.width // 3
    left = int(np.nonzero(share[:third] > 0.03)[0].max()) + 1
    # The offset back onto the bar matters: `nonzero` on a slice indexes the slice, and dropping
    # that put the right cap's inside edge two hundred pixels into the cap itself — which cut a
    # third of the cap off and stretched the rest of it through the middle.
    right = (bar.width - third) + int(np.nonzero(share[bar.width - third:] > 0.03)[0].min())
    assert left < right, "the two caps overlap — the metal gate is finding the wood"

    length = int(round(WALL_HEIGHT * (2.0 + GAP_RATIO) / (GAP_RATIO * WALL_THICKNESS_RATIO)))
    scale = WALL_HEIGHT / float(bar.height)
    head = max(1, int(round(left * scale)))
    tail = max(1, int(round((bar.width - right) * scale)))
    middle = length - head - tail
    assert middle > max(head, tail), "the caps do not leave a middle to stretch"

    out = Image.new("RGBA", (length, WALL_HEIGHT), (0, 0, 0, 0))
    out.paste(bar.crop((0, 0, left, bar.height)).resize((head, WALL_HEIGHT), Image.LANCZOS), (0, 0))
    out.paste(bar.crop((left, 0, right, bar.height)).resize((middle, WALL_HEIGHT), Image.LANCZOS),
              (head, 0))
    out.paste(bar.crop((right, 0, bar.width, bar.height))
              .resize((tail, WALL_HEIGHT), Image.LANCZOS), (head + middle, 0))
    print("wall  flat %dx%d, caps %d and %d -> %d and %d, sprite %dx%d"
          % (bar.width, bar.height, left, bar.width - right, head, tail, length, WALL_HEIGHT))
    return out


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

    wall = flatten_wall()
    for folder, fraction in DENSITIES:
        height = max(1, int(round(WALL_HEIGHT * fraction * 2)))
        width = max(1, int(round(wall.width * height / wall.height)))
        save(wall.resize((width, height), Image.LANCZOS), "drawable-" + folder, "wall_piece.webp")
