"""Regenerate the Play feature graphic from the palette the app actually ships.

The old file was drawn against the pre-launch palette: bright green tiles, an orange
pawn, gold walls. The shipped board is near-black with blue and red pawns and mint
walls, so the old graphic advertises an app the player will not recognise. Everything
here is drawn from the same colours the screenshots show.
"""
import os
from PIL import Image, ImageDraw, ImageFilter

S = 4  # supersample, downscaled at the end
W, H = 1024 * S, 500 * S

BG_TOP = (10, 14, 12)
BG_BOT = (4, 7, 6)
GLOW = (18, 120, 84)

TILE = (19, 24, 21)
TILE_EDGE = (30, 38, 33)
GOAL_BLUE = (38, 55, 92)
GOAL_RED = (86, 45, 41)
PAWN_BLUE = (74, 123, 232)
PAWN_BLUE_D = (44, 84, 180)
PAWN_RED = (196, 62, 52)
PAWN_RED_D = (150, 40, 33)
WALL = (99, 230, 168)

ICON_G1 = (43, 224, 143)
ICON_G2 = (16, 168, 110)
PAWN_W = (240, 250, 245)
PAWN_W_D = (198, 220, 210)


def vertical_gradient(size, top, bottom):
    w, h = size
    base = Image.new("RGB", (1, h))
    px = base.load()
    for y in range(h):
        t = y / max(1, h - 1)
        px[0, y] = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return base.resize((w, h), Image.BILINEAR)


def radial_glow(size, centre, radius, colour, strength=1.0):
    """A soft emerald bloom, painted as a blurred disc so it has no visible edge."""
    layer = Image.new("L", size, 0)
    d = ImageDraw.Draw(layer)
    cx, cy = centre
    d.ellipse((cx - radius, cy - radius, cx + radius, cy + radius),
              fill=round(255 * strength))
    layer = layer.filter(ImageFilter.GaussianBlur(radius * 0.55))
    tint = Image.new("RGB", size, colour)
    return tint, layer


def pawn(draw, cx, cy, r, light, dark):
    """The board pawn: head, neck, collar, base — the silhouette the app draws.

    cy is the centre of the head; everything else hangs below it, so callers can
    place the piece by its head the way the eye reads it.
    """
    base_w, base_h = r * 2.3, r * 0.86
    base_top = cy + r * 2.05
    draw.rounded_rectangle((cx - base_w / 2, base_top, cx + base_w / 2, base_top + base_h),
                           radius=base_h / 2, fill=dark)
    draw.rounded_rectangle((cx - r * 0.46, cy + r * 1.0, cx + r * 0.46, base_top + r * 0.12),
                           radius=r * 0.22, fill=dark)
    draw.rounded_rectangle((cx - r * 0.72, cy + r * 0.72, cx + r * 0.72, cy + r * 1.22),
                           radius=r * 0.24, fill=light)
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=light)
    draw.ellipse((cx - r * 0.58, cy - r * 0.74, cx + r * 0.08, cy - r * 0.08),
                 fill=tuple(min(255, c + 30) for c in light))


canvas = vertical_gradient((W, H), BG_TOP, BG_BOT)
tint, mask = radial_glow((W, H), (int(W * 0.66), int(H * 0.52)), int(H * 0.72), GLOW, 0.5)
canvas = Image.composite(Image.blend(canvas, tint, 0.42), canvas, mask)

board = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(board)

# A close-up of the board rather than the whole 9x9: at this aspect ratio a full board
# would sit as a small square in the middle with dead space either side.
pitch = 112 * S
gap = 11 * S
side = pitch - gap
cols, rows = 6, 4
x0 = int(W * 0.395)
y0 = int(H * 0.5 - rows * pitch / 2)

for row in range(rows):
    for col in range(cols):
        x = x0 + col * pitch
        y = y0 + row * pitch
        if x >= W:
            continue
        fill = TILE
        if row == 0:
            fill = GOAL_BLUE
        elif row == rows - 1:
            fill = GOAL_RED
        d.rounded_rectangle((x, y, x + side, y + side), radius=14 * S,
                            fill=fill, outline=TILE_EDGE, width=max(1, S // 2))

# Two mint walls, drawn on the tile seams exactly as the game places them.
wall_t = 13 * S
d.rounded_rectangle((x0 + pitch * 1 - gap // 2 - wall_t // 2, y0 + pitch * 1 + side * 0.12,
                     x0 + pitch * 1 - gap // 2 + wall_t // 2, y0 + pitch * 3 - gap - side * 0.12),
                    radius=wall_t // 2, fill=WALL)
d.rounded_rectangle((x0 + pitch * 2 + side * 0.10, y0 + pitch * 2 - gap // 2 - wall_t // 2,
                     x0 + pitch * 4 - gap - side * 0.10, y0 + pitch * 2 - gap // 2 + wall_t // 2),
                    radius=wall_t // 2, fill=WALL)

pawn(d, x0 + pitch * 2 + side / 2, y0 + pitch * 1 + side * 0.32, side * 0.19,
     PAWN_BLUE, PAWN_BLUE_D)
pawn(d, x0 + pitch * 3 + side / 2, y0 + pitch * 2 + side * 0.32, side * 0.19,
     PAWN_RED, PAWN_RED_D)

canvas = Image.alpha_composite(canvas.convert("RGBA"), board)

# The icon motif on the left: the two walls and the pawn between them, so the graphic
# and the launcher icon read as the same product.
mark = Image.new("RGBA", (W, H), (0, 0, 0, 0))
m = ImageDraw.Draw(mark)
mx, my = int(W * 0.185), int(H * 0.50)
bar_h = int(H * 0.52)
bar_w = int(bar_h * 0.132)
bar_dx = int(bar_h * 0.40)

bar_grad = vertical_gradient((bar_w, bar_h), ICON_G1, ICON_G2)
for sign in (-1, 1):
    shape = Image.new("L", (bar_w, bar_h), 0)
    ImageDraw.Draw(shape).rounded_rectangle((0, 0, bar_w - 1, bar_h - 1),
                                            radius=bar_w // 2, fill=255)
    canvas.paste(bar_grad, (mx + sign * bar_dx - bar_w // 2, my - bar_h // 2), shape)

pawn(m, mx, my - bar_h * 0.20, bar_h * 0.155, PAWN_W, PAWN_W_D)
canvas = Image.alpha_composite(canvas, mark)

# Vignette, so nothing important reads as sitting on the frame edge.
vig = Image.new("L", (W, H), 0)
ImageDraw.Draw(vig).rounded_rectangle((int(W * 0.04), int(H * 0.07),
                                       int(W * 0.96), int(H * 0.93)),
                                      radius=int(H * 0.2), fill=255)
vig = vig.filter(ImageFilter.GaussianBlur(int(H * 0.10)))
canvas = Image.composite(canvas, Image.new("RGBA", (W, H), (3, 5, 4, 255)), vig)

out = canvas.convert("RGB").resize((1024, 500), Image.LANCZOS)
dst = os.path.join(os.path.dirname(os.path.abspath(__file__)), "feature-graphic-1024x500.png")
out.save(dst, "PNG", optimize=True)
print(dst, out.size, round(os.path.getsize(dst) / 1024, 1), "KB")
