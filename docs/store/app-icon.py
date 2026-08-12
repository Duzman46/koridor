# -*- coding: utf-8 -*-
"""Cuts every icon the app ships out of the one photograph the home screen already shows.

Run it after `reference/koridor.png` changes, or after the framing below is moved. Nothing here
is generated: it is one deterministic crop of one file, resampled, so the tile on the launcher,
the tile on the splash and the tile in the Play listing cannot drift apart from each other.

    python docs/store/app-icon.py

**The scene, not a piece of it.** An adaptive icon is authored on a 108-unit canvas and every
launcher throws away everything outside the middle 72 — a third of the width, all the way round.
The first version of this file took that as a cropping problem: which square of the photograph
survives the mask? Every answer was one pawn on its own, because the two pieces sit 848 pixels
apart on the picture's diagonal and no square that holds them both puts either inside the safe
circle. So the icon shipped as a single cream pawn, and it was wrong — it says chess, and this
game is the walls.

The question was the wrong one. The composition does not have to be cropped up until it fits;
it can be **scaled down into** the safe area. [SCENE] is a square of real photograph holding both
pawns and the three walls between them, centred on the pieces rather than on the frame, and it is
laid into the canvas at exactly the size a launcher keeps. Both pawns then clear a circle mask
with room to spare, and every pixel a player sees is photograph.

**The ring is not.** [SCENE] already fills the visible square, so the outer sixth — the part only
the mask ever touches — is the scene's own edge rows pushed outwards, blurred, and pulled down
towards black at the rim. Replication alone streaks; blur alone leaves the corner as bright as
the middle and the tile stops having an edge.

**Why the tone is touched.** The scene was lit for a six-inch screen. A launcher draws it twelve
millimetres wide, and the deepest part of the board — where the dark pawn and the near wall
live — goes to nothing. [shadow_gamma] opens that end and leaves everything above the knee where
the photographer put it, so the cream pawn's highlight and the specular on the wall caps are the
original pixels. A flat brightness lift was tried first and turned the board to charcoal, which
is a different app from the one the icon opens.
"""
import os

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SOURCE = os.path.join(ROOT, "reference", "koridor.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")
STORE = os.path.join(ROOT, "store-assets")

#: The square of photograph that becomes the icon: left, top, right, bottom in source pixels.
#: Square on purpose — a letterboxed scene would have to invent the bands above and below it,
#: and this crop reaches the real board instead. Centred on the two pieces (they span y 255..800,
#: midpoint 527) rather than on the frame.
SCENE = (200, 2, 1250, 1052)

#: The fraction of the 108-unit canvas a launcher mask keeps: 72/108.
VISIBLE = 72 / 108

#: Working resolution of the 108-unit canvas. A multiple of 108 so the visible square lands on a
#: whole number of pixels and the round trip below closes exactly.
CANVAS = 108 * 15

#: How far the canvas reaches past the visible square on each side. DERIVED, never chosen — a
#: mask that keeps 72 of 108 discards a sixth of the canvas per side. An earlier revision picked
#: this number by hand as a sixth of the *window* instead, which is the same fraction measured
#: against the wrong side, and every export came out 12% tighter than this file claimed. The
#: assertion in [visible_of] is what stops that happening again.
RING = round(CANVAS * (1 - VISIBLE) / 2)

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
    """The 108-unit square: [SCENE] filling the visible middle, a fabricated ring around it."""
    photo = Image.open(SOURCE).convert("RGB")
    side = CANVAS - 2 * RING
    scene = shadow_gamma(photo.crop(SCENE)).resize((side, side), Image.LANCZOS)

    # Edge-replicate outwards. Pillow will do it without numpy by stretching the one-pixel border
    # strips, which is the same result and one dependency fewer.
    plate = Image.new("RGB", (CANVAS, CANVAS))
    plate.paste(scene.crop((0, 0, 1, side)).resize((RING, side), Image.NEAREST), (0, RING))
    plate.paste(scene.crop((side - 1, 0, side, side)).resize((RING, side), Image.NEAREST), (CANVAS - RING, RING))
    plate.paste(scene, (RING, RING))
    whole = plate.crop((0, RING, CANVAS, RING + side))
    plate.paste(whole.crop((0, 0, CANVAS, 1)).resize((CANVAS, RING), Image.NEAREST), (0, 0))
    plate.paste(whole.crop((0, side - 1, CANVAS, side)).resize((CANVAS, RING), Image.NEAREST), (0, CANVAS - RING))

    blurred = plate.filter(ImageFilter.GaussianBlur(CANVAS * 0.035))
    band = Image.new("L", (CANVAS, CANVAS), 255)
    ImageDraw.Draw(band).rectangle((RING, RING, CANVAS - RING - 1, CANVAS - RING - 1), fill=0)
    plate = Image.composite(blurred, plate, band.filter(ImageFilter.GaussianBlur(CANVAS * 0.006)))

    # A vignette that bites only outside the scene, so the ring reaches board-black at the rim
    # and the tile has an edge even on a launcher whose mask is close to the full square.
    shade = Image.new("L", (CANVAS, CANVAS), 0)
    pen = ImageDraw.Draw(shade)
    steps = 40
    for step in range(steps):
        inset = int(CANVAS * 0.5 * step / steps)
        pen.rectangle((inset, inset, CANVAS - inset, CANVAS - inset), outline=int(120 * (1 - step / steps)))
    shade = Image.composite(shade.filter(ImageFilter.GaussianBlur(CANVAS * 0.02)),
                            Image.new("L", (CANVAS, CANVAS), 0), band)
    return Image.composite(Image.new("RGB", (CANVAS, CANVAS), (2, 3, 4)), plate, shade)


def visible_of(square):
    """What is left of the canvas after a launcher mask: the middle 72 of 108, i.e. [SCENE]."""
    kept = square.crop((RING, RING, square.width - RING, square.height - RING))
    # The round trip has to close, or the framing drifts silently the next time it is moved.
    expected = round(CANVAS * VISIBLE)
    assert kept.width == expected, "ring and visible square disagree: %d != %d" % (kept.width, expected)
    return kept


def save(image, path, **options):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, **options)
    print("  %-58s %6.1f KB" % (os.path.relpath(path, ROOT), os.path.getsize(path) / 1024))


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
