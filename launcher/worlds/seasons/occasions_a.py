"""Jahreskreis' first six occasions (spec § 4.2), one picture each, drawn from code alone:

    new_year     a city skyline over the dock with lit windows under a night sky (the fireworks are the overlay's)
    carnival     a dark stage with a curtain, coloured spotlights, a pennant garland and, on the boards above the
                 dock, party hats, curled streamers and confetti (the hanging streamers and falling confetti move)
    valentine    an evening table at a window: red roses in a glass vase, two glasses of wine, a candle, petals
    easter       a pastel morning over green hills, painted eggs in the grass right of the middle, above the dock
    mothers_day  a bright bunch of flowers in a blue vase and a hand-drawn heart card "Mama" on a table at a window
    fathers_day  a meadow under a spring sky, a field path running out of the picture on the right, a handcart with a
                 picnic basket on it

The shapes follow the mockup's painters (jahreskreis-mockup.html, PAINT.newyear/carnival/valentine/easter/mothers/
fathers) in the 192 x 108 units of common.py; where the mockup draws from its seeded random (the skyline, the
bouquet, the window's stars) the same generator (seasons.lcg) puts them where it does there. The occasions have no
night picture of their own: the bright ones (Valentine's window, Easter, Mother's and Father's Day) are painted as by
day, muted for the OLED rule, and dimmed at night by the scene (WorldScene.dimFor). What moves (fireworks, streamers
and confetti, the candle and petals, butterflies, motes of light, clouds and seeds) is OccasionMotionsA.java's.

Picture rule (spec § 3): what matters stands right of the middle, directly above the dock (its top edge at y 76.6
units), never behind it and never in the header on the top left; the skyline stands on the dock's edge, the eggs and
the cart are whole above it, and hills are drawn per pixel column (seasons.ridge). Nothing that matters lies under the
pills on the top right (personal.py: PILLS): the carnival garland and its streamers hang below them, Valentine's moon
too (tests/test_seasons_pills.py, with the fireworks' sites).
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import common as c  # noqa: E402
from seasons import DOCK, XS, YS, corners, crest, fill, lcg, ridge, shade, sky_stars, under  # noqa: E402

TAU = 2 * np.pi


# ---- drawing helpers ----------------------------------------------------------------------------------------------
def line(p, points, width, fill=255):
    """A polyline in units with round joints and ends, width in units."""
    p.d.line([(x * c.U, y * c.U) for x, y in points], fill=fill, width=max(1, round(width * c.U)), joint="curve")
    for x, y in (points[0], points[-1]):
        p.circle(x, y, width / 2, fill=fill)


def turned_ellipse(cx, cy, rx, ry, angle, n=28):
    """An ellipse's outline turned by angle (radians), as points in units."""
    ca, sa = np.cos(angle), np.sin(angle)
    out = []
    for k in range(n):
        t = k / n * TAU
        x, y = rx * np.cos(t), ry * np.sin(t)
        out.append((cx + x * ca - y * sa, cy + x * sa + y * ca))
    return out


def bezier(p0, p1, p2, n=48):
    """A quadratic Bézier from p0 over p1 to p2 as n + 1 points."""
    out = []
    for k in range(n + 1):
        t = k / n
        a, b, d = (1 - t) ** 2, 2 * (1 - t) * t, t * t
        out.append((a * p0[0] + b * p1[0] + d * p2[0], a * p0[1] + b * p1[1] + d * p2[1]))
    return out


def heart(k, wobble=0.0, n=60):
    """The mockup's heart (16 sin^3 u, -(13 cos u - 5 cos 2u - 2 cos 3u - cos 4u)) scaled by k, round the origin;
    wobble shakes the line a little, as a child's hand draws it."""
    out = []
    for i in range(n + 1):
        u = i / n * TAU
        x = 16 * np.sin(u) ** 3
        y = -(13 * np.cos(u) - 5 * np.cos(2 * u) - 2 * np.cos(3 * u) - np.cos(4 * u))
        out.append((x * k + np.sin(i) * wobble, y * k + np.cos(i * 1.7) * wobble))
    return out


# ---- new year -----------------------------------------------------------------------------------------------------
SKYLINE_GROUND = DOCK + 1.4             # the blocks stand on the dock's top edge, their feet hidden just behind it


def skyline():
    """The mockup's skyline from its rng(41): blocks (x, top, width) side by side over the whole width and their lit
    windows (x, y, width); the windows are drawn from the same sequence even where the dock would hide them, so the
    rest land where the mockup has them, and only those above the dock are kept."""
    r = lcg(41)
    blocks, windows = [], []
    x = 0.0
    while x < 192:
        bw, bh = 192 * (0.03 + r() * 0.05), 108 * (0.08 + r() * 0.22)
        top = SKYLINE_GROUND - bh
        blocks.append((x, top, bw))
        wy = top + 108 * 0.02
        while wy < SKYLINE_GROUND - 108 * 0.015:
            wx = x + bw * 0.15
            while wx < x + bw * 0.85:
                if r() > 0.72 and wy + 1.3 < DOCK - 0.8:
                    windows.append((wx, wy, bw * 0.1))
                wx += bw * 0.25
            wy += 108 * 0.03
        x += bw
    return blocks, windows


def new_year(rng):
    img = c.vgradient([(0, "#03040B"), (.40, "#080B1C"), (.62, "#121733"), (.72, "#1B1F3E"), (1, "#0A0C18")])
    # the city's glow on the low sky, right of the middle, and a faint purple of the fireworks above it
    c.radial(img, 128, 76, 100, [(0, "#4A3A6A", .32), (.6, "#2A2448", .12), (1, "#2A2448", 0)], ry=28, mode="add")
    c.radial(img, 128, 30, 60, [(0, "#2A1E48", .25), (1, "#2A1E48", 0)], ry=24, mode="add")
    c.stars(img, sky_stars(rng, 46, 44, (0.15, 0.36)), "#DCE2FF", glow=0.1)
    blocks, windows = skyline()
    shades = ("#05060E", "#080A17", "#0A0C1C")
    for k, colour in enumerate(shades):
        mask = c.shape(lambda p: [p.rect(x, top, bw + 0.05, SKYLINE_GROUND + 4 - top)
                                  for i, (x, top, bw) in enumerate(blocks) if i % 3 == k], blur=0.03)
        c.blend(img, mask, colour)
    # roof details: a few masts and stepped tops, from the shape seed
    tops = [(x, top, bw) for x, top, bw in blocks if rng.uniform() < 0.45]
    c.blend(img, c.shape(lambda p: [(p.rect(x + bw * 0.45, top - 3.2, 0.25, 3.2),
                                     p.rect(x + bw * 0.2, top - 1.0, bw * 0.6, 1.0)) for x, top, bw in tops],
                         blur=0.03), "#07081A")
    # a cool rim of the sky's light on the tops
    c.add(img, c.shape(lambda p: [p.rect(x, top, bw, 0.35) for x, top, bw in blocks], blur=0.2) * 0.25, "#6A5A9A")
    # the lit windows, most warm, some cool, with a faint glow
    warm = [wnd for wnd in windows if rng.uniform() < 0.8]
    cool = [wnd for wnd in windows if wnd not in warm]
    for group, colour in ((warm, "#FFD68C"), (cool, "#CFDDF6")):
        win = c.shape(lambda p: [p.rect(x, y, wd, 1.3) for x, y, wd in group], blur=0.04)
        c.add(img, c.shape(lambda p: [p.rect(x, y, wd, 1.3) for x, y, wd in group], blur=1.0) * 0.18, colour)
        c.blend(img, win * 0.75, colour)
    corners(img, 0.3)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# ---- carnival -----------------------------------------------------------------------------------------------------
STAGE = 66.5                            # where the curtain meets the boards
CONFETTI = ("#FF3B5C", "#FFD23F", "#3EC6FF", "#7CFF6B", "#FFFFFF")
# The pennant garland: its string from x0 to x1, its ends at y0 and y1, sagging sag (units). It starts high right of
# the header and hangs below the pills on the top right (personal.py: PILLS); the streamers hang from it
# (OccasionMotionsA.Carnival.GARLAND).
GARLAND = (80.0, 188.0, 3.0, 30.0, 10.0)
PENNANT_HALF, PENNANT_DROP = 2.4, 4.6     # a pennant's half width and how far it hangs below the string


def garland(x):
    """The pennant garland's string at x (units): from (80, 3) down to (188, 30), sagging 10 units."""
    x0, x1, y0, y1, sag = GARLAND
    u = (x - x0) / (x1 - x0)
    return y0 + (y1 - y0) * u + sag * 4 * u * (1 - u)


def garland_pennants():
    """The garland's sixteen pennants as (x, colour index), right of the header."""
    return [(GARLAND[0] + 4 + k * 6.6, k) for k in range(16)]


def spiral(cx, cy, r0, r1, turns, squash=0.45, n=90):
    """A streamer's curl lying on the boards: a spiral from r0 to r1, squashed as seen from the front."""
    out = []
    for k in range(n + 1):
        t = k / n
        a, r = t * turns * TAU, r0 + (r1 - r0) * t
        out.append((cx + np.cos(a) * r, cy + np.sin(a) * r * squash))
    return out


def party_hat(p, x, base, wdt, height, lean=0.0):
    p.poly([(x - wdt / 2, base), (x + lean, base - height), (x + wdt / 2, base)])


def carnival(rng):
    img = c.vgradient([(0, "#1E0820"), (.45, "#30102E"), (STAGE / 108, "#40142E")])
    # the curtain's folds, per column
    folds = (0.5 + 0.5 * np.sin(XS * 1.25 + np.sin(XS * 0.21) * 1.5)).astype(np.float32)
    img *= (0.5 + 0.5 * folds ** 1.4)[None, :, None]
    # the boards
    floor = c.vgradient([(0, "#2A1018"), (.3, "#1C0A12"), (1, "#0C0508")], top=STAGE / 108, bottom=1.0)
    sy = int(STAGE * c.U)
    img[sy:] = floor[sy:]
    planks = np.zeros((c.h, c.w), dtype=np.float32)
    for y in (68.2, 70.4, 73.1, 76.4, 80.5):
        planks += c.shape(lambda p: p.rect(0, y, 192, 0.18), blur=0.05)
    c.blend(img, planks * 0.5, "#080306")
    c.add(img, c.shape(lambda p: p.rect(0, STAGE, 192, 0.35), blur=0.12) * 0.35, "#C06A8A")
    # coloured spotlights from above, their beams and pools on the boards
    for x, colour in ((88, "#FF3B5C"), (112, "#FFD23F"), (136, "#3EC6FF"), (158, "#7CFF6B"), (178, "#FF8AD8")):
        c.radial(img, x, 30, 13, [(0, colour, .16), (.7, colour, .06), (1, colour, 0)], ry=42, mode="add")
        c.radial(img, x, STAGE + 4, 14, [(0, colour, .20), (1, colour, 0)], ry=4.5, mode="add")
    c.radial(img, 132, 62, 80, [(0, "#FF6AA0", .10), (1, "#FF6AA0", 0)], ry=22, mode="add")
    # the pennant garland from high right of the header down under the pills
    xs = np.linspace(GARLAND[0], GARLAND[1], 60)
    c.blend(img, c.shape(lambda p: line(p, [(x, garland(x)) for x in xs], 0.25), blur=0.04), "#6A5030")
    pennants = garland_pennants()
    hw, drop = PENNANT_HALF, PENNANT_DROP
    for k, colour in enumerate(CONFETTI[:4]):
        mask = c.shape(lambda p: [p.poly([(x - hw, garland(x - hw)), (x + hw, garland(x + hw)),
                                          (x, garland(x) + drop)]) for x, i in pennants if i % 4 == k], blur=0.05)
        c.blend(img, mask, c.rgb(colour) * 0.55)
    # on the boards right of the middle: curled streamers, two party hats and confetti, all above the dock
    for (cx, cy, r1, turns), colour in (((121, 73.2, 3.2, 2.6), "#FF3B5C"), ((139, 74.6, 2.6, 2.2), "#3EC6FF"),
                                        ((183, 73.8, 2.8, 2.4), "#7CFF6B"), ((106, 74.8, 2.2, 2.0), "#FFD23F")):
        c.blend(img, c.shape(lambda p: line(p, spiral(cx, cy, 0.4, r1, turns), 0.42), blur=0.04),
                c.rgb(colour) * 0.8)
    c.blend(img, c.shape(lambda p: line(p, [(94 + k * 0.5, 72.6 + np.sin(k * 0.55) * 0.9) for k in range(40)], 0.4),
                         blur=0.04), c.rgb("#FF8AD8") * 0.8)
    # the hats' shadows, the hats, their stripes and pompoms
    c.blend(img, c.shape(lambda p: (p.ellipse(153, 75.6, 5.5, 0.9), p.ellipse(169.5, 75.6, 4.2, 0.7)), blur=0.5),
            "#050204")
    hat1 = c.shape(lambda p: party_hat(p, 152.5, 75.6, 8.0, 12.5, lean=0.6), blur=0.04)
    hat2 = c.shape(lambda p: party_hat(p, 169.5, 75.6, 6.0, 9.0, lean=-0.4), blur=0.04)
    c.blend(img, hat1, "#2A6FC0")
    c.blend(img, hat2, "#C8304C")
    stripes = c.shape(lambda p: [p.poly([(140, 75.6 - k * 3.2), (166, 75.6 - k * 3.2 - 5.5),
                                         (166, 75.6 - k * 3.2 - 4.3), (140, 75.6 - k * 3.2 + 1.2)])
                                 for k in range(5)], blur=0.04)
    c.blend(img, stripes * hat1, "#F2C63A")
    dots = c.shape(lambda p: [p.circle(x, y, 0.5) for x, y in ((168, 73.5), (171, 72.4), (169.3, 70), (170.6, 67.6),
                                                                (168.6, 74.8), (171.8, 74.6))], blur=0.04)
    c.blend(img, dots * hat2, "#F4E8EC")
    c.blend(img, c.shape(lambda p: (p.circle(153.1, 63.1, 1.2), p.circle(169.1, 66.6, 1.0)), blur=0.06), "#F8E6A0")
    # shade the hats' right side a little
    side = c.shape(lambda p: (p.poly([(153.1, 63.1), (156.5, 75.6), (154.5, 75.6)]),
                              p.poly([(169.1, 66.6), (172.5, 75.6), (171, 75.6)])), blur=0.6)
    c.blend(img, side * (hat1 + hat2) * 0.35, "#000000")
    # confetti scattered on the boards
    bits = [(float(rng.uniform(88, 190)), float(rng.uniform(STAGE + 1.0, DOCK - 0.6)), float(rng.uniform(0, 3.14)),
             int(rng.integers(0, 5))) for _ in range(140)]
    for k, colour in enumerate(CONFETTI):
        mask = c.shape(lambda p: [p.poly(turned_ellipse(x, y, 0.55, 0.22, a, n=4)) for x, y, a, i in bits if i == k],
                       blur=0.03)
        c.blend(img, mask * 0.85, c.rgb(colour) * 0.75)
    shade(img, 70, 100, 0.25)
    corners(img, 0.5)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# ---- valentine ----------------------------------------------------------------------------------------------------
WIN_X, WIN_Y, WIN_W, WIN_H = 105.6, 10.8, 61.4, 45.4    # the mockup's window: .55 / .1 of the screen, .32 x .42
TABLE = 71.3                                            # the table's edge, .66 of the height
VASE_X, VASE_Y = 138.2, 74.5                           # .72 / .69: the vase stands on the table above the dock
CANDLE_X, CANDLE_TOP, FLAME_Y = 115.2, 64.8, 63.2       # .6 of the width, the candle from .6 to .69, its flame .585
ROSES = [(-0.03, -0.28), (0, -0.32), (0.03, -0.27), (-0.015, -0.23), (0.018, -0.22)]
# The crescent moon in the window's lower right pane, under the pills on the top right (personal.py: PILLS) and over
# the roofs.
VALENTINE_MOON = (156.0, 36.0, 3.0)
VALENTINE_MOON_HALO = (10, [(0, "#C9D4F0", .14), (1, "#C9D4F0", 0)])   # radius and c.radial stops, added light


def window_stars():
    """The mockup's twenty stars in the window (rng(103), four values each: x, y, s, p)."""
    r = lcg(103)
    out = []
    for _ in range(20):
        x, y, s, _p = r(), r(), r(), r()
        out.append((WIN_X + 2 + x * (WIN_W - 4), WIN_Y + 2 + y * (WIN_H * 0.72), 0.14 + s * 0.16))
    return out


def drapes(x0, x1, top, bottom, waist):
    """A curtain from x0 to x1, gathered at waist (y) to half its width, its inner edge (x1) curving in and out."""
    mid = (x0 + x1) / 2
    return ([(x0, top)] + bezier((x1, top), (x1 + 0.5, waist - 12), (mid, waist), n=24)
            + bezier((mid, waist), (x1 - 0.5, waist + 10), (x1, bottom), n=24) + [(x0, bottom)])


def valentine(rng):
    img = c.vgradient([(0, "#140710"), (.55, "#26101C"), (.66, "#2C0F1E")])
    # night in the window: a deep blue sky, the mockup's stars, a crescent moon and roofs far off
    pane = c.shape(lambda p: p.rect(WIN_X, WIN_Y, WIN_W, WIN_H))
    c.blend(img, pane, c.vgradient([(0, "#060A1E"), (.4, "#0C1430"), (.55, "#18224A")]))
    c.stars(img, window_stars(), "#EEF0FF", glow=0.08)
    mx, my, mr = VALENTINE_MOON
    c.radial(img, mx, my, *VALENTINE_MOON_HALO, mode="add")
    moon = c.shape(lambda p: p.circle(mx, my, mr), blur=0.08) * (1 - c.shape(lambda p: p.circle(mx + 1.6, my - 1.0, 2.7),
                                                                              blur=0.08))
    c.blend(img, moon, "#E8ECF6")
    roofs = [(WIN_X, 52), (112, 49.5), (118, 51), (124, 47.5), (131, 50.5), (139, 48.5), (146, 51.5),
             (152, 49), (159, 50.8), (WIN_X + WIN_W, 50)]
    c.blend(img, c.shape(lambda p: p.poly([(x, y) for x, y in roofs] + [(WIN_X + WIN_W, WIN_Y + WIN_H),
                                                                        (WIN_X, WIN_Y + WIN_H)])) * pane, "#0A0C1A")
    lights = [(float(rng.uniform(WIN_X + 1, WIN_X + WIN_W - 1)), float(rng.uniform(52.5, 55))) for _ in range(14)]
    c.blend(img, c.shape(lambda p: [p.rect(x, y, 0.5, 0.45) for x, y in lights]) * pane * 0.7, "#FFCF86")
    # the frame and its cross, the sill
    frame = c.shape(lambda p: (p.rect(WIN_X - 0.7, WIN_Y - 0.7, WIN_W + 1.4, 1.4),
                               p.rect(WIN_X - 0.7, WIN_Y + WIN_H - 0.7, WIN_W + 1.4, 1.4),
                               p.rect(WIN_X - 0.7, WIN_Y - 0.7, 1.4, WIN_H + 1.4),
                               p.rect(WIN_X + WIN_W - 0.7, WIN_Y - 0.7, 1.4, WIN_H + 1.4),
                               p.rect(WIN_X + WIN_W / 2 - 0.45, WIN_Y, 0.9, WIN_H),
                               p.rect(WIN_X, WIN_Y + WIN_H * 0.42 - 0.45, WIN_W, 0.9)), blur=0.05)
    c.blend(img, frame, "#3A1A24")
    c.blend(img, c.shape(lambda p: p.rect(WIN_X - 2.5, WIN_Y + WIN_H + 0.7, WIN_W + 5, 1.6), blur=0.06), "#3E1C26")
    # the curtains, gathered at the sides, with their folds
    curtains = c.shape(lambda p: (p.poly(drapes(97, 109, 4, TABLE, 40)),
                                  p.poly([(176 - (x - 97), y) for x, y in drapes(97, 109, 4, TABLE, 40)])), blur=0.1)
    cfolds = (0.55 + 0.45 * np.sin(XS * 2.2) ** 2).astype(np.float32)
    c.blend(img, curtains, c.vgradient([(0, "#2E0814"), (.5, "#5A1426"), (.66, "#3A0C1A")]) * cfolds[None, :, None])
    c.blend(img, c.shape(lambda p: (p.rect(96.5, 39.4, 7, 1.2), p.rect(169.5, 39.4, 7, 1.2)), blur=0.05), "#8A6A3A")
    # the table, its cloth's lit edge, and the warm light round the candle and the roses
    table = c.vgradient([(0, "#4A1E28"), (1, "#200A10")], top=TABLE / 108, bottom=1.0)
    ty = int(TABLE * c.U)
    img[ty:] = table[ty:]
    c.add(img, c.shape(lambda p: p.rect(0, TABLE, 192, 0.3), blur=0.1) * 0.25, "#FF9AA8")
    c.radial(img, 134.4, 64.8, 54, [(0, "#FF788C", .15), (1, "#FF788C", 0)], ry=40, mode="add")
    c.radial(img, CANDLE_X, FLAME_Y, 9, [(0, "#FFBE64", .40), (.4, "#FFBE64", .14), (1, "#FFBE64", 0)], mode="add")
    # the roses: stems from the vase's neck, leaves, then the glass vase over them
    neck = (VASE_X, VASE_Y - 108 * 0.12)
    heads = [(VASE_X + 192 * dx, VASE_Y + 108 * dy) for dx, dy in ROSES]
    c.blend(img, c.shape(lambda p: [line(p, [neck, (x, y)], 0.38) for x, y in heads], blur=0.04), "#2F6A35")
    leaves = [(neck[0] + (x - neck[0]) * 0.55, neck[1] + (y - neck[1]) * 0.55, x) for x, y in heads]
    c.blend(img, c.shape(lambda p: [p.poly(turned_ellipse(lx + (1.2 if x > VASE_X else -1.2), ly, 1.5, 0.6,
                                                          0.5 if x > VASE_X else -0.5)) for lx, ly, x in leaves],
                         blur=0.04), "#285A2E")
    vase = c.shape(lambda p: p.poly([(VASE_X - 3.84, VASE_Y)] + bezier((VASE_X - 3.84, VASE_Y),
                                                                         (VASE_X - 5.76, VASE_Y - 8.64),
                                                                         (VASE_X - 2.3, VASE_Y - 15.1))
                                    + bezier((VASE_X + 2.3, VASE_Y - 15.1), (VASE_X + 5.76, VASE_Y - 8.64),
                                             (VASE_X + 3.84, VASE_Y))), blur=0.04)
    c.blend(img, vase * 0.35, "#C8E6FF")
    c.add(img, c.shape(lambda p: p.ellipse(VASE_X - 2.4, VASE_Y - 7, 0.5, 4.5), blur=0.3) * vase * 0.35, "#FFFFFF")
    # the rose heads: a dark red cup, lighter petals, a spiral at the heart
    for x, y in heads:
        c.blend(img, c.shape(lambda p: p.circle(x, y, 2.5), blur=0.05), "#7A0C1E")
        c.blend(img, c.shape(lambda p: (p.ellipse(x - 0.6, y - 0.5, 1.8, 1.6), p.ellipse(x + 0.7, y - 0.2, 1.4, 1.5)),
                             blur=0.25) * 0.8, "#C42A3E")
        c.add(img, c.shape(lambda p: p.circle(x - 0.8, y - 0.9, 0.9), blur=0.4) * 0.25, "#FF8A96")
        c.blend(img, c.shape(lambda p: line(p, spiral(x, y, 0.15, 1.3, 1.7, squash=0.9, n=40), 0.18), blur=0.03)
                * 0.6, "#3C0008")
    # two glasses of red wine. The bowl's lowest point is y - 10.25 (halfway along its curve): the stem runs from
    # there down to the foot, and the wine is the same curve cut at its surface (y - 11.9), so it stays in the bowl.
    for gx in (157.4, 167.0):
        y = VASE_Y
        bowl = bezier((gx - 2.3, y - 14), (gx, y - 6.5), (gx + 2.3, y - 14), n=24)
        wine = bezier((gx - 1.53, y - 11.9), (gx, y - 8.6), (gx + 1.53, y - 11.9), n=24)
        c.blend(img, c.shape(lambda p: p.poly(wine), blur=0.04) * 0.75, "#A0142A")
        glass = c.shape(lambda p: (line(p, bowl, 0.22), line(p, [(gx, y - 10.3), (gx, y)], 0.22),
                                   line(p, [(gx - 1.9, y), (gx + 1.9, y)], 0.22)), blur=0.03)
        c.blend(img, glass * 0.6, "#E6F0FF")
        c.add(img, c.shape(lambda p: p.ellipse(gx - 1.2, y - 12, 0.25, 0.9), blur=0.15) * 0.3, "#FFFFFF")
    # the candle on a small holder, with its flame at rest (the overlay flickers it)
    c.blend(img, c.shape(lambda p: p.ellipse(CANDLE_X, VASE_Y - 0.3, 2.6, 0.6), blur=0.06), "#6A4A30")
    c.blend(img, c.shape(lambda p: p.rect(CANDLE_X - 1.34, CANDLE_TOP, 2.69, VASE_Y - CANDLE_TOP - 0.3), blur=0.04),
            "#E6D8C0")
    c.blend(img, c.shape(lambda p: p.rect(CANDLE_X + 0.3, CANDLE_TOP, 1.04, VASE_Y - CANDLE_TOP - 0.3), blur=0.3)
            * 0.3, "#000000")
    c.blend(img, c.shape(lambda p: p.rect(CANDLE_X - 0.08, FLAME_Y + 0.6, 0.16, 1.1)), "#2A1A10")
    c.blend(img, c.shape(lambda p: p.ellipse(CANDLE_X, FLAME_Y, 0.65, 1.65), blur=0.12), "#FFD27A")
    # a few petals on the cloth
    petals = [(float(rng.uniform(124, 176)), float(rng.uniform(TABLE + 0.6, DOCK - 0.8)), float(rng.uniform(0, 3)))
              for _ in range(9)]
    c.blend(img, c.shape(lambda p: [p.poly(turned_ellipse(x, y, 1.0, 0.45, a)) for x, y, a in petals], blur=0.05)
            * 0.85, "#B01830")
    shade(img, 72, 100, 0.3)
    corners(img, 0.35)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# ---- easter -------------------------------------------------------------------------------------------------------
# The mockup's five eggs at .5 .6 .7 .79 .88 of the width, every other one a little lower; radii .016 x .021 of the
# width. Their bottoms stand on the grass just above the dock's top edge, close to it but whole.
EGG_RX, EGG_RY = 192 * 0.016, 192 * 0.021
EGG_BOTTOMS = (76.0, 74.6)
EGGS = [(192 * x, EGG_BOTTOMS[i % 2] - EGG_RY) for i, x in enumerate((0.5, 0.6, 0.7, 0.79, 0.88))]
EGG_COLOURS = ("#E89AAE", "#9EC2E6", "#E8DC96", "#B49EE6", "#9EE2BA")   # the mockup's pastels, a little deeper
EGG_PAINT = ("#B04A72", "#3A6AB0", "#C08A2A", "#6A4AB0", "#2E8A5A")


def egg(cx, cy, rx, ry, n=56):
    """An egg's outline, narrower at the top."""
    return [(cx + rx * np.sin(a) * (1 - 0.13 * np.cos(a)), cy - ry * np.cos(a))
            for a in np.linspace(0, TAU, n, endpoint=False)]


def egg_pattern(p, i, cx, cy, rx, ry):
    """Each egg's own painting: zigzags, dots, waves and bands, drawn over its whole box (cut to the egg later)."""
    if i in (0, 3):
        zig = [(cx - rx * 1.2 + k * rx * 0.3, cy + (0.9 if k % 2 else -0.9)) for k in range(9)]
        line(p, zig, 0.45)
        line(p, [(cx - rx * 1.2, cy - ry * 0.45), (cx + rx * 1.2, cy - ry * 0.45)], 0.35)
        line(p, [(cx - rx * 1.2, cy + ry * 0.45), (cx + rx * 1.2, cy + ry * 0.45)], 0.35)
    if i in (1, 3):
        for dx, dy in ((-0.5, -0.65), (0.5, -0.65), (0, -0.3), (-0.6, 0.7), (0.6, 0.7), (0, 0.85)):
            p.circle(cx + dx * rx, cy + dy * ry, 0.5)
    if i == 2:
        for row in (-0.5, 0.0, 0.5):
            line(p, [(cx - rx * 1.2 + k * 0.4, cy + row * ry + np.sin(k * 0.9) * 0.45) for k in range(17)], 0.35)
    if i == 4:
        p.rect(cx - rx * 1.2, cy - 0.8, rx * 2.4, 1.6)
        for dx in (-0.6, -0.2, 0.2, 0.6):
            p.circle(cx + dx * rx, cy - ry * 0.55, 0.45)
            p.circle(cx + dx * rx, cy + ry * 0.55, 0.45)


def easter(rng):
    # a pastel morning, muted so no large area is bright (OLED rule)
    img = c.vgradient([(0, "#1E2A48"), (.25, "#3E5480"), (.45, "#72769A"), (.58, "#8E7A8A"), (.62, "#927F86")])
    c.radial(img, 128, 56, 70, [(0, "#E8C0A0", .13), (.5, "#D8A8A0", .08), (1, "#D8A8A0", 0)], ry=26, mode="add")
    back, front = ridge(64.8, 3.2, 2), ridge(72.0, 2.2, 4)
    fill(img, under(back), "#6E9260", "#4C7442", 60, 80)
    c.add(img, crest(back, 1.2) * 0.12, "#E8F0C8")
    # small flowers in the far meadow
    flowers = [(float(x), float(back[min(c.w - 1, int(x * c.U))] + rng.uniform(1.0, 6.0)),
                ("#F4F0E0", "#F2D86A", "#E8A8C0")[int(rng.integers(0, 3))]) for x in rng.uniform(80, 190, 40)]
    for colour in ("#F4F0E0", "#F2D86A", "#E8A8C0"):
        c.blend(img, c.shape(lambda p: [p.circle(x, y, 0.35) for x, y, k in flowers if k == colour], blur=0.06)
                * 0.8, colour)
    fill(img, under(front), "#527E44", "#2A4C24", 68, 90)
    c.add(img, crest(front, 1.0) * 0.12, "#E0F0C0")
    shade(img, 70, 100, 0.12)
    # the eggs: a shadow in the grass, the egg, its painting, a white band (the mockup's line), shading and light
    c.blend(img, c.shape(lambda p: [p.ellipse(x + 0.8, y + EGG_RY - 0.1, EGG_RX * 1.25, 0.8) for x, y in EGGS],
                         blur=0.4) * 0.6, "#142410")
    for i, (x, y) in enumerate(EGGS):
        shell = c.shape(lambda p: p.poly(egg(x, y, EGG_RX, EGG_RY)), blur=0.04)
        c.blend(img, shell, EGG_COLOURS[i])
        c.blend(img, c.shape(lambda p: egg_pattern(p, i, x, y, EGG_RX, EGG_RY), blur=0.03) * shell * 0.85,
                EGG_PAINT[i])
        if i in (1, 2):
            c.blend(img, c.shape(lambda p: p.rect(x - EGG_RX, y - 0.3, EGG_RX * 2, 0.6), blur=0.03) * shell * 0.8,
                    "#FFFFFF")
        lit = c.shape(lambda p: p.poly(egg(x - 0.7, y - 0.8, EGG_RX, EGG_RY)), blur=0.9)
        c.blend(img, shell * (1 - lit) * 0.45, "#2A2030")
        c.add(img, c.shape(lambda p: p.ellipse(x - EGG_RX * 0.4, y - EGG_RY * 0.45, 0.7, 1.2), blur=0.4) * shell
              * 0.35, "#FFFFFF")
    # blades of grass in front of the eggs' feet and along the near crest
    blades = []
    for x, y in EGGS:
        for k in range(9):
            bx = x + (k - 4) * 0.75 + float(rng.uniform(-0.2, 0.2))
            blades.append((bx, y + EGG_RY + 0.2, float(rng.uniform(0.9, 2.0)), float(rng.uniform(-0.6, 0.6))))
    for x in rng.uniform(78, 192, 160):
        y = float(front[min(c.w - 1, int(x * c.U))]) + 0.4
        blades.append((float(x), y, float(rng.uniform(0.8, 1.8)), float(rng.uniform(-0.5, 0.5))))
    c.blend(img, c.shape(lambda p: [line(p, [(x, y), (x + lean, y - ht)], 0.22) for x, y, ht, lean in blades],
                         blur=0.03), "#4A7A3A")
    corners(img, 0.7)
    c.keep_dark(img, corners=0.6, bottom=0.7)
    return img


# ---- mother's day -------------------------------------------------------------------------------------------------
MVASE_X, MVASE_Y = 134.4, 74.5          # the mockup's .7 / .69
CARD_X, CARD_Y, CARD_TURN = 161.3, 64.8, 0.12
CARD_W, CARD_H = 192 * 0.08, 108 * 0.17
BLOOMS = ("#E86A92", "#E8BC40", "#B07AE8", "#E88A50", "#F2EEE6")   # the mockup's colours, a little muted
FLOWER_R = 192 * 0.012 * 1.3          # the mockup's .012 of the width, a little larger for a fuller bunch

# "Mama" in strokes, as a child writes it: x from 0, the baseline at y 0, the capital's top at -1.
GLYPHS = {
    "M": (0.82, [[(0, 0), (0.06, -1), (0.38, -0.36), (0.68, -1), (0.76, 0)]]),
    "a": (0.62, [[(0.52, -0.42), (0.42, -0.6), (0.22, -0.6), (0.04, -0.42), (0.02, -0.16), (0.16, 0.02),
                  (0.38, 0), (0.52, -0.2)], [(0.54, -0.6), (0.54, -0.1), (0.6, 0)]]),
    "m": (0.78, [[(0, 0), (0, -0.58)], [(0, -0.42), (0.1, -0.58), (0.26, -0.6), (0.36, -0.46), (0.36, 0)],
                 [(0.36, -0.46), (0.46, -0.6), (0.62, -0.6), (0.72, -0.46), (0.72, 0)]]),
}


def bouquet():
    """The mockup's eleven flowers from its rng(113): (x, y, colour index, petals)."""
    r = lcg(113)
    out = []
    for i in range(11):
        x = MVASE_X + (r() - 0.5) * 192 * 0.11
        y = MVASE_Y - 108 * (0.2 + r() * 0.15)
        out.append((x, y, i % 5, 5 if i % 2 else 6))
    return out


def card_point(x, y):
    """A point on the card (its centre at 0, 0, in units) where it lands in the scene: the card leans by CARD_TURN."""
    ca, sa = np.cos(CARD_TURN), np.sin(CARD_TURN)
    return CARD_X + x * ca - y * sa, CARD_Y + x * sa + y * ca


def mama_strokes(cap=3.0, baseline=6.2):
    """'Mama' as strokes on the card, centred, with a slight tremor."""
    width = sum(GLYPHS[ch][0] for ch in "Mama") + 0.14 * 3
    x0 = -width * cap / 2
    out = []
    for ch in "Mama":
        adv, strokes = GLYPHS[ch]
        for stroke in strokes:
            out.append([card_point(x0 + gx * cap + np.sin(k * 2.3 + gx * 7) * 0.08, baseline + gy * cap)
                        for k, (gx, gy) in enumerate(stroke)])
        x0 += (adv + 0.14) * cap
    return out


def mothers_day(rng):
    # a warm room in the morning, its wall muted (OLED rule), a window with soft light behind the flowers
    img = c.vgradient([(0, "#241A18"), (.35, "#3C2E28"), (.62, "#56423A")])
    wx, wy, ww, wh = 100, 12, 52, 46
    pane = c.shape(lambda p: p.rect(wx, wy, ww, wh))
    c.blend(img, pane, c.vgradient([(0, "#526478"), (.3, "#6E7A86"), (.5, "#86796C")]))
    c.radial(img, 128, 30, 30, [(0, "#E8D8B8", .12), (1, "#E8D8B8", 0)], ry=24, mode="add")
    # far off through the window: a row of soft treetops
    tops = [(x, 50 + np.sin(x * 0.4) * 1.6 + np.sin(x * 1.3) * 0.6) for x in np.linspace(wx, wx + ww, 40)]
    c.blend(img, c.shape(lambda p: p.poly(tops + [(wx + ww, wy + wh), (wx, wy + wh)]), blur=0.3) * pane, "#5E7058")
    # the window's frame and cross, the sill, sheer curtains at its sides
    c.blend(img, c.shape(lambda p: (p.rect(wx - 0.8, wy - 0.8, ww + 1.6, 1.6), p.rect(wx - 0.8, wy + wh - 0.8,
                                                                                     ww + 1.6, 1.6),
                                    p.rect(wx - 0.8, wy - 0.8, 1.6, wh + 1.6), p.rect(wx + ww - 0.8, wy - 0.8, 1.6,
                                                                                      wh + 1.6),
                                    p.rect(wx + ww / 2 - 0.5, wy, 1.0, wh), p.rect(wx, wy + wh * 0.45 - 0.5, ww, 1.0)),
                         blur=0.05), "#B8AA96")
    c.blend(img, c.shape(lambda p: p.rect(wx - 3, wy + wh + 0.8, ww + 6, 1.8), blur=0.06), "#A8967E")
    sheer = c.shape(lambda p: (p.poly([(wx - 4, wy - 3), (wx + 7, wy - 3), (wx + 3, wy + wh), (wx - 4, wy + wh)]),
                               p.poly([(wx + ww - 7, wy - 3), (wx + ww + 4, wy - 3), (wx + ww + 4, wy + wh),
                                       (wx + ww - 3, wy + wh)])), blur=0.6)
    sfolds = (0.6 + 0.4 * np.sin(XS * 2.6) ** 2).astype(np.float32)
    c.blend(img, sheer * sfolds[None, :] * 0.45, "#C8BCAC")
    # the light falling in, across the wall onto the table
    beam = c.shape(lambda p: p.poly([(wx + 6, wy + 4), (wx + ww - 4, wy + 4), (wx + ww + 20, 73), (wx + 12, 73)]),
                   blur=5)
    c.add(img, beam * 0.07, "#FFE8C0")
    # the table
    table = c.vgradient([(0, "#8A6A4A"), (.4, "#5E4430"), (1, "#2A1C12")], top=TABLE / 108, bottom=1.0)
    ty = int(TABLE * c.U)
    img[ty:] = table[ty:]
    c.add(img, c.shape(lambda p: p.rect(0, TABLE, 192, 0.3), blur=0.1) * 0.2, "#FFE0B0")
    # the bouquet: stems from the vase's neck with a few leaves, then the vase, then the flowers
    neck = (MVASE_X, MVASE_Y - 108 * 0.11)
    flowers = bouquet()
    c.blend(img, c.shape(lambda p: [line(p, [neck, (x, y)], 0.3) for x, y, *_ in flowers], blur=0.03), "#4F8A3A")
    leaves = [(neck[0] + (x - neck[0]) * 0.5, neck[1] + (y - neck[1]) * 0.5, -0.6 if x < neck[0] else 0.6)
              for x, y, *_ in flowers[::2]]
    c.blend(img, c.shape(lambda p: [p.poly(turned_ellipse(lx + a * 1.5, ly, 1.8, 0.7, a)) for lx, ly, a in leaves],
                         blur=0.04), "#3E7A30")
    c.blend(img, c.shape(lambda p: p.ellipse(MVASE_X + 1, MVASE_Y + 0.2, 6.5, 0.9), blur=0.5) * 0.5, "#1A120C")
    vase = c.shape(lambda p: p.poly([(MVASE_X - 4.8, MVASE_Y), (MVASE_X - 3.84, MVASE_Y - 13),
                                     (MVASE_X + 3.84, MVASE_Y - 13), (MVASE_X + 4.8, MVASE_Y)]), blur=0.04)
    c.blend(img, vase, "#5F8FB3")
    c.blend(img, c.shape(lambda p: p.rect(MVASE_X + 1.2, MVASE_Y - 13, 4, 13), blur=0.8) * vase * 0.35, "#1A2A40")
    c.add(img, c.shape(lambda p: p.rect(MVASE_X - 3.2, MVASE_Y - 12, 0.8, 11), blur=0.3) * vase * 0.3, "#FFFFFF")
    c.blend(img, c.shape(lambda p: p.rect(MVASE_X - 3.9, MVASE_Y - 13.3, 7.8, 0.9), blur=0.04), "#4A7AA0")
    for k, colour in enumerate(BLOOMS):
        petals = c.shape(lambda p: [p.poly(turned_ellipse(x + np.cos(a) * FLOWER_R * 0.6, y + np.sin(a) * FLOWER_R
                                                          * 0.6, FLOWER_R * 0.55, FLOWER_R * 0.35, a, n=20))
                                    for x, y, i, n in flowers if i == k
                                    for a in (j * TAU / n for j in range(n))], blur=0.04)
        c.blend(img, petals, colour)
    c.blend(img, c.shape(lambda p: [p.circle(x, y, FLOWER_R * 0.35) for x, y, *_ in flowers], blur=0.04), "#F2D47A")
    c.blend(img, c.shape(lambda p: [p.circle(x + 0.25, y + 0.25, FLOWER_R * 0.2) for x, y, *_ in flowers], blur=0.1)
            * 0.4, "#A07020")
    # the heart card standing on the table: its shadow, the card, a crayon heart and "Mama"
    hw, hh = CARD_W / 2, CARD_H / 2
    box = [card_point(x, y) for x, y in ((-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh))]
    c.blend(img, c.shape(lambda p: p.poly([(x + 1.6, y + 0.6) for x, y in box]), blur=1.0) * 0.45, "#1A120C")
    c.blend(img, c.shape(lambda p: p.poly(box), blur=0.04), "#EEE6D6")
    k = 192 * 0.0011 * 1.1
    outline = [card_point(x, y - 3.4) for x, y in heart(k, wobble=0.12)]
    inside = c.shape(lambda p: p.poly([card_point(x, y - 3.4) for x, y in heart(k)]), blur=0.03)
    hatch = c.shape(lambda p: [line(p, [card_point(-6 + j * 0.7, 3), card_point(-1 + j * 0.7, -10)], 0.22)
                               for j in range(17)], blur=0.03)
    c.blend(img, hatch * inside * 0.55, "#F08AA4")
    c.blend(img, c.shape(lambda p: line(p, outline, 0.55), blur=0.04), "#E0405F")
    c.blend(img, c.shape(lambda p: [line(p, stroke, 0.42) for stroke in mama_strokes()], blur=0.04), "#4A7AD9")
    shade(img, 72, 98, 0.15)
    corners(img, 0.6)
    c.keep_dark(img, corners=0.6, bottom=0.7)
    return img


# ---- father's day -------------------------------------------------------------------------------------------------
# The field path: from the bottom left (behind the dock) it winds up to the right and runs out of the picture past
# the right edge, on the near hill; its upper and lower edges as quadratic Béziers in units.
PATH_TOP = ((40, 108), (96, 72.5), (198, 66.6))
PATH_BOTTOM = ((198, 71.6), (120, 79.5), (94, 108))
CART_X = 134.4                          # the mockup's .7


def path_outline():
    return bezier(*PATH_TOP, n=80) + bezier(*PATH_BOTTOM, n=80)


def fathers_day(rng):
    # a spring sky, muted (OLED rule), the sun's glow high on the right, clear of the corner
    img = c.vgradient([(0, "#1C3050"), (.25, "#33587E"), (.45, "#6A8CA6"), (.56, "#869A8C")])
    c.radial(img, 146, 26, 40, [(0, "#FFE8A0", .34), (.4, "#FFDC90", .12), (1, "#FFDC90", 0)], mode="add")
    c.radial(img, 146, 26, 2.8, [(0, "#FFF6C8", .97), (.8, "#FFE7A0", .93), (1, "#FFE7A0", 0)])
    back, front = ridge(56.0, 2.2, 1), ridge(63.5, 2.4, 3)
    fill(img, under(back), "#7A9A66", "#5A7E4C", 54, 70)
    c.add(img, crest(back, 1.2) * 0.12, "#F0F4D0")
    # a stand of trees and a few bushes on the far hill, right of the middle
    crowns = [(171, -3.5, 4.2), (176.5, -5.0, 5.0), (182, -3.0, 3.8), (112, -1.6, 2.2), (118, -1.2, 1.8)]
    crowns = [(x, float(back[int(x * c.U)]) + dy, r) for x, dy, r in crowns]
    c.blend(img, c.shape(lambda p: [p.circle(x, y, r) for x, y, r in crowns], blur=0.15), "#3A5A34")
    c.add(img, c.shape(lambda p: [p.circle(x - r * 0.3, y - r * 0.35, r * 0.55) for x, y, r in crowns], blur=0.8)
          * 0.08, "#E8F0C0")
    fill(img, under(front), "#6A9450", "#365E2C", 60, 84)
    c.add(img, crest(front, 1.2) * 0.12, "#F0F4D0")
    # flowers in the meadow
    dots = [(float(x), float(front[min(c.w - 1, int(x * c.U))] + rng.uniform(0.8, 9)),
             int(rng.integers(0, 3))) for x in rng.uniform(78, 192, 70)]
    for k, colour in enumerate(("#F2EEE0", "#F2D25A", "#D88AB8")):
        c.blend(img, c.shape(lambda p: [p.circle(x, y, 0.33) for x, y, i in dots if i == k], blur=0.06) * 0.8, colour)
    # the field path, sand with two wheel ruts, out past the right edge
    path = c.shape(lambda p: p.poly(path_outline()), blur=0.12)
    c.blend(img, path, c.vgradient([(0, "#B8A47E"), (.4, "#9C8A68"), (1, "#5A4C38")], top=0.6, bottom=1.0))
    ruts = c.shape(lambda p: [line(p, bezier(*[(a[0] * (1 - f) + b[0] * f, a[1] * (1 - f) + b[1] * f)
                                               for a, b in zip(PATH_TOP, PATH_BOTTOM[::-1])], n=60), 0.3)
                              for f in (0.32, 0.68)], blur=0.25)
    c.blend(img, ruts * path * 0.3, "#5A4A34")
    shade(img, 70, 98, 0.08)
    # where the cart stands: the middle of the path at its x, but whole above the dock
    col = path[:, int(CART_X * c.U)]
    rows = np.nonzero(col > 0.5)[0]
    ground = min(float((rows[0] + rows[-1]) / 2 / c.U), DOCK - 1.6) if len(rows) else DOCK - 1.6
    wr = 192 * 0.016
    wheel_y = ground - wr
    box_bottom = wheel_y - 0.5
    box_top = box_bottom - 108 * 0.055
    # the shadow, the cart's bed with planks, its drawbar, wheels with spokes, the basket with a checked cloth
    c.blend(img, c.shape(lambda p: p.ellipse(CART_X + 1, ground - 0.1, 14, 1.1), blur=0.5) * 0.5, "#1E2414")
    c.blend(img, c.shape(lambda p: line(p, [(CART_X + 11.5, box_top + 2.2), (CART_X + 21, box_top - 4.5)], 0.6),
                         blur=0.04), "#3A2410")
    c.blend(img, c.shape(lambda p: line(p, [(CART_X + 20, box_top - 5.6), (CART_X + 22, box_top - 3.4)], 0.7),
                         blur=0.04), "#3A2410")
    c.blend(img, c.shape(lambda p: p.rect(CART_X - 11.5, box_top, 23, box_bottom - box_top), blur=0.04), "#8A5A2A")
    c.blend(img, c.shape(lambda p: [p.rect(CART_X - 11.5 + k * 5.76, box_top, 0.3, box_bottom - box_top)
                                    for k in range(1, 4)] + [p.rect(CART_X - 11.5, box_top + 2.9, 23, 0.25)],
                         blur=0.03) * 0.8, "#5A3A18")
    c.add(img, c.shape(lambda p: p.rect(CART_X - 11.5, box_top, 23, 0.5), blur=0.1) * 0.18, "#FFE0B0")
    # the basket on the bed, its handle and the red cloth
    bx0, bw, bh = CART_X - 6.7, 9.6, 108 * 0.045
    btop = box_top - bh + 0.8
    c.blend(img, c.shape(lambda p: line(p, [(bx0 + bw / 2 + np.cos(a) * 3.5, btop - np.sin(a) * 3.5)
                                            for a in np.linspace(0, np.pi, 30)], 0.55), blur=0.04), "#8A6A30")
    basket = c.shape(lambda p: p.rect(bx0, btop, bw, bh), blur=0.04)
    c.blend(img, basket, "#C8A060")
    weave = c.shape(lambda p: [p.rect(bx0, btop + 1.6 + k * 0.9, bw, 0.18) for k in range(3)]
                    + [p.rect(bx0 + 0.8 + k * 1.3, btop + 1.4, 0.18, bh - 1.4) for k in range(7)], blur=0.03)
    c.blend(img, weave * basket * 0.45, "#7A5A28")
    c.blend(img, c.shape(lambda p: p.rect(bx0, btop, bw, 1.1), blur=0.03), "#C8302F")
    c.blend(img, c.shape(lambda p: [p.rect(bx0 + k * 1.2, btop + (0.55 if k % 2 else 0), 0.6, 0.55)
                                    for k in range(8)], blur=0.02) * 0.85, "#F2EEE6")
    c.blend(img, c.shape(lambda p: p.poly([(bx0 + bw - 2.2, btop + 1.0), (bx0 + bw + 0.6, btop + 1.0),
                                           (bx0 + bw + 0.2, btop + 3.6), (bx0 + bw - 1.4, btop + 3.0)]), blur=0.04),
            "#C8302F")
    for dx in (-7.7, 7.7):
        x = CART_X + dx
        c.blend(img, c.shape(lambda p: p.circle(x, wheel_y, wr), blur=0.04), "#2A1A0E")
        c.blend(img, c.shape(lambda p: (p.circle(x, wheel_y, wr * 0.82),), blur=0.04)
                * (1 - c.shape(lambda p: p.circle(x, wheel_y, wr * 0.68), blur=0.04)), "#B08A5A")
        c.blend(img, c.shape(lambda p: [line(p, [(x, wheel_y), (x + np.cos(a) * wr * 0.75, wheel_y + np.sin(a) * wr
                                                                * 0.75)], 0.28)
                                        for a in np.linspace(0.3, 0.3 + TAU, 6, endpoint=False)], blur=0.03),
                "#B08A5A")
        c.blend(img, c.shape(lambda p: p.circle(x, wheel_y, 0.6), blur=0.03), "#5A3A18")
    # grass in front of the wheels and along the path's edges
    blades = [(float(x), float(ground + rng.uniform(-0.2, 0.3)), float(rng.uniform(0.6, 1.4)),
               float(rng.uniform(-0.4, 0.4))) for x in rng.uniform(CART_X - 12, CART_X + 12, 30)]
    c.blend(img, c.shape(lambda p: [line(p, [(x, y), (x + lean, y - ht)], 0.2) for x, y, ht, lean in blades],
                         blur=0.03), "#4E7A3A")
    corners(img, 0.55)
    c.keep_dark(img, corners=0.6, bottom=0.7)
    return img


# Look.id: (painter(rng), shape seed, grain seed); one picture each, world_seasons_<id>.jpg
OCCASIONS = {
    "new_year": (new_year, 3101, 51),
    "carnival": (carnival, 3102, 52),
    "valentine": (valentine, 3103, 53),
    "easter": (easter, 3104, 54),
    "mothers_day": (mothers_day, 3105, 55),
    "fathers_day": (fathers_day, 3106, 56),
}
