# -*- coding: utf-8 -*-
"""Cuts the board pieces out of the supplied renders.

    python docs/store/pieces.py

The pawn used to be computed here — a profile spun about an axis and shaded per pixel with a
real normal. That was the right answer while there was nothing better; the renders are better.
What is left is a cut-out, and it has to stay reproducible from the repository.

**The two seats are now two renders, and nothing is tinted any more.** An ivory piece was
supplied first and used twice, dyed blue and dyed red, because blue and red are written into the
tutorial, into the turn banner and into the colour a host is offered when a room is made, in ten
languages — and a second render of a *black* piece could not be called "red". `pawn-blue.png` and
`pawn-red.png` settle that by being the same piece in the two named colours: one mould, gunmetal
and gold, a sapphire in one head and a ruby in the other. No duotone, no knee to tune, no gold
gate to keep the metal out of the dye. The materials arrive as they were rendered.

**They are also seen from much higher up, which is the real reason they replaced the ivory one.**
The board is drawn straight down — its tiles are square, its studs are circles, there is no
perspective in it at all — and a tall piece photographed from the side is the one object on the
table disagreeing with that. These are photographed from above, so they sit in the projection the
board is already in.

**The cut is by edge, not by threshold.** These pieces are gunmetal on a dark grey ground and the
body is *darker* than the ground behind it, so no luminance gate separates them — one that cleared
the background would take the body with it. What does separate them is that the ground is smooth
and the piece has an outline: flood the low-gradient country in from the four corners and it stops
at the silhouette, and everything it could not reach is the piece.
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
WALL_SOURCE = os.path.join(ROOT, "reference", "wall.png")

#: The two seats, as the two renders. Seat one is blue and seat two is red, everywhere and by
#: name — see SeatColors — so the files are named for the seats rather than for the stones.
PAWNS = {
    "blue": os.path.join(ROOT, "reference", "pawn-blue.png"),
    "red": os.path.join(ROOT, "reference", "pawn-red.png"),
}

#: Width the pawn is mastered at, before the density downsample. Width and not height: a piece
#: seen from above is sized on the board by how much of a tile its base covers.
MASTER_WIDTH = 512

#: Densities it ships at, as (folder, fraction of the master).
DENSITIES = (("xhdpi", 0.25), ("xxhdpi", 0.375), ("xxxhdpi", 0.5))

#: The wall sprite's proportions, which are `Constants.Board`'s and have to stay its. A wall is
#: two tiles and the channel between them long, and as thick as the channel times the overhang.
#: Getting this wrong does not distort a little — it distorts the gold caps, which is the one
#: part of the piece an eye reads as a manufactured object rather than a smear.
GAP_RATIO = 0.15590
WALL_THICKNESS_RATIO = 1.91
WALL_HEIGHT = 128

#: How steep a luminance slope has to be to count as the piece's outline.
#:
#: Chosen off a contact sheet rather than guessed. At 8 the ground's own texture leaks in and the
#: contact shadow comes with the piece; at 22 the outline breaks where the body is closest in
#: value to the ground and the flood pours into the piece, taking its top off. 14 closes all the
#: way round on both renders.
EDGE_GATE = 14.0

#: Colour is a barrier too, so the gold rings and the stone hold the outline where the gunmetal
#: is too close in value to the ground to hold it alone.
COLOUR_GATE = 22.0


def cut_piece(image):
    """Alpha for a pawn: the ground flooded away from the corners, the piece left behind.

    The blur first is not cosmetic — the render's ground is textured, and Sobel on the raw pixels
    finds that texture as readily as it finds the piece.
    """
    blurred = np.asarray(image.filter(ImageFilter.GaussianBlur(2.0))).astype(float)
    lum = blurred.mean(axis=2)
    saturation = blurred.max(axis=2) - blurred.min(axis=2)

    slope = np.hypot(ndimage.sobel(lum, axis=1), ndimage.sobel(lum, axis=0))
    barrier = (slope > EDGE_GATE) | (saturation > COLOUR_GATE)

    labels, _ = ndimage.label(~barrier)
    corners = {labels[3, 3], labels[3, -4], labels[-4, 3], labels[-4, -4]}
    corners.discard(0)
    assert corners, "the corners are on the barrier — the gates are too low"
    piece = ndimage.binary_fill_holes(~np.isin(labels, sorted(corners)))

    # The outline is a ridge two or three pixels wide, so closing it seals the pinholes the
    # flood would otherwise have crawled through, and the second fill takes the interior back.
    piece = ndimage.binary_fill_holes(ndimage.binary_closing(piece, np.ones((9, 9))))
    labels, count = ndimage.label(piece)
    assert count, "nothing survived the cut"
    areas = ndimage.sum(piece, labels, range(1, count + 1))
    return labels == (1 + int(np.argmax(areas)))


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


def base_anchor(image):
    """How far down the sprite the base's own centre sits, as a fraction of its height.

    A piece seen from above stands on its base, and its base is a disc: what belongs over the
    middle of a tile is the middle of that disc, not the middle of the picture. The disc is the
    widest thing in the sprite, so its centre is the middle of the widest run of rows.
    """
    solid = np.asarray(image.getchannel("A")).astype(float) > 128
    widths = solid.sum(axis=1)
    widest = float(np.nonzero(widths >= widths.max() * 0.985)[0].mean())
    # The widest row is the centre of the base's TOP ellipse; below it is the base's own side
    # wall, down to the sprite's last row, which is the front of the ellipse it actually stands
    # on. The contact ellipse's centre is between the two — which is the point that belongs over
    # the middle of a tile. Taking the widest row alone sat every piece a third of a base high.
    return (widest + image.height) / 2.0 / float(image.height)


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
    for seat, path in PAWNS.items():
        master = Image.open(path).convert("RGB")
        piece = master.copy()
        piece.putalpha(Image.fromarray((cut_piece(master) * 255).astype(np.uint8)))
        # Soften what the fill left blocky, before anything is scaled.
        piece.putalpha(piece.getchannel("A").filter(ImageFilter.GaussianBlur(1.2)))
        piece = trim(piece)
        base = base_anchor(piece)
        print("pawn_%-5s master %dx%d  base centre %.1f%% down" % (seat, piece.width, piece.height, 100 * base))
        for folder, fraction in DENSITIES:
            width = max(1, int(round(MASTER_WIDTH * fraction)))
            height = max(1, int(round(piece.height * width / piece.width)))
            save(piece.resize((width, height), Image.LANCZOS), "drawable-" + folder, "pawn_%s.webp" % seat)

    wall = flatten_wall()
    for folder, fraction in DENSITIES:
        height = max(1, int(round(WALL_HEIGHT * fraction * 2)))
        width = max(1, int(round(wall.width * height / wall.height)))
        save(wall.resize((width, height), Image.LANCZOS), "drawable-" + folder, "wall_piece.webp")
