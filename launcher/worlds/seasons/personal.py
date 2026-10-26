"""Jahreskreis' personal days (spec § 4.3), one picture each, drawn from code alone:

    birthday     a party room in plum: two pennant garlands "HAPPY" and "BIRTHDAY" hanging deep right of the clock,
                 a cake with four lit candles on a plate left of the middle, a bunch of three balloons left of it,
                 their strings running down to the dock's top edge, confetti on the table
    anniversary  a desert at sunset: two suns close together, three rows of dunes with lit ridges, stars over it, a
                 small projector half in the sand on the right (the holo heart it throws is the overlay's)
    wedding      a garden in the evening: a rose arch with greenery, roses and a string of lights on a lawn, two
                 interlocking golden rings in it, trees and a hedge behind, petals on the grass

The pictures hold no names and no numbers: the name under the garland, the year count in the heart and the names over
the arch are drawn at runtime by PersonalMotions.java from the TV's entries (SeasonsToday.names(), years()), as are the
confetti, the candles' flicker, the heart, the twinkling stars and lights, the rings' sparkle and the petals in the air.
The bow that ties the balloons' strings to the dock is BalloonBowView.java's, drawn over the dock.

The shapes follow the mockup's painters (jahreskreis-mockup.html, PAINT.birthday, the anniversary painter and PAINT.wedding) in the 192 x 108
units of common.py; where the mockup draws from its seeded random (the anniversary's stars, the arch's leaves and
roses) the same generator (seasons.lcg) puts them where it does there. All three are evening looks with no night
picture of their own. Where PersonalMotions draws over a painted thing (the candles' flames, the stars, the projector's
lens, the arch's lights, the rings) it uses the same numbers as here; they are named in capitals.

Picture rule (spec § 3): what matters stands right of the app name, directly above the dock (its top edge at y 76.6
units), never behind it and never in the header on the top left (x below 76, y below 34).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import common as c  # noqa: E402
from occasions_a import bezier, line, turned_ellipse  # noqa: E402
from occasions_b import hshaded, ring, shaded  # noqa: E402
from seasons import DOCK, XS, YS, corners, lcg, shade, under  # noqa: E402

TAU = 2 * np.pi
# Outfit, the launcher's own font (OFL), at weight 700 for the pennants' letters.
FONT = Path(__file__).resolve().parent.parent.parent / "app" / "src" / "main" / "res" / "font" / "outfit.ttf"


# ---- drawing helpers ----------------------------------------------------------------------------------------------
def cubic(p0, p1, p2, p3, n=32):
    """A cubic Bézier from p0 over p1 and p2 to p3 as n + 1 points."""
    out = []
    for k in range(n + 1):
        t = k / n
        a, b, d, e = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t ** 3
        out.append((a * p0[0] + b * p1[0] + d * p2[0] + e * p3[0], a * p0[1] + b * p1[1] + d * p2[1] + e * p3[1]))
    return out


def letters(items):
    """A mask with letters in white: items as (letter, x, y, size, angle) in units, each centred on (x, y) and turned
    by angle (radians, clockwise on the screen as canvas rotate turns it)."""
    m = Image.new("L", (c.w, c.h), 0)
    for ch, x, y, size, angle in items:
        font = ImageFont.truetype(str(FONT), max(1, round(size * c.U)))
        font.set_variation_by_axes([700])
        side = int(size * c.U * 2.2)
        tile = Image.new("L", (side, side), 0)
        ImageDraw.Draw(tile).text((side / 2, side / 2), ch, font=font, fill=255, anchor="mm")
        tile = tile.rotate(-np.degrees(angle), resample=Image.BICUBIC)
        m.paste(255, (int(round(x * c.U - side / 2)), int(round(y * c.U - side / 2))), tile)
    return np.asarray(m, dtype=np.float32) / 255.0


def turn(x, y, angle, px, py):
    """The point (px, py) of a shape round (x, y), turned by angle."""
    ca, sa = np.cos(angle), np.sin(angle)
    return x + px * ca - py * sa, y + px * sa + py * ca


def muted(hex_colour, k):
    """A colour at k of its brightness, as an rgb array."""
    return c.rgb(hex_colour) * k


# ---- birthday -----------------------------------------------------------------------------------------------------
PENNANTS = ("#FF5C8A", "#FFD04A", "#5CC8FF", "#9B7BFF", "#5CF0A0")   # the mockup's five
# The two garlands (the mockup's swag()): word, x0, x1, y0 and sag in units, its colours start at this pennant.
GARLANDS = [("HAPPY", 115.2, 172.8, 4.32, 8.64, 0), ("BIRTHDAY", 96.0, 189.12, 18.36, 11.88, 2)]
PENNANT_MAX = 192 * 0.022                # half a pennant's width at most (the mockup's w * .022), its height 1.7 times
CAKE_X, CAKE_Y = 99.84, 69.12            # the mockup's .52 / .64: left of the middle, its plate just above the dock
CAKE_HW, CAKE_TOP, CAKE_BOTTOM = 15.36, CAKE_Y - 10.8, CAKE_Y + 4.32
PLATE_HW, PLATE_H = 18.24, 1.62
CANDLE_DX = (-9.6, -3.264, 3.264, 9.6)   # the mockup's four candles (dx .05 / .017 of the width)
CANDLE_COLOURS = ("#8FD3FF", "#FFE066", "#B9A3FF", "#7CFF9B")
CANDLE_W, CANDLE_TOP = 1.536, CAKE_Y - 18.36
FLAME_Y, FLAME_W, FLAME_H = CAKE_Y - 19.98, 0.768, 1.728   # PersonalMotions.Birthday flickers them here
KNOT_X = 69.12                           # where the strings meet the dock's top edge (BalloonBowView ties the bow)
# The balloons: centre, half width, half height, colour; below the header, left of the cake (the mockup's bunch,
# lower so the top one clears the header).
BALLOONS = [(62.4, 50.6, 4.03, 6.26, "#FF5C8A"), (75.84, 51.7, 3.84, 6.05, "#5CC8FF"),
            (69.12, 44.8, 4.22, 6.48, "#FFD04A")]
TABLE = 72.0                             # the table's back edge behind the cake


def garland_at(x0, x1, y0, sag, u):
    """A point of a garland's string at u (0..1): the mockup's at(u) = x0 + (x1 - x0) u, y0 + 4 sag u (1 - u)."""
    return x0 + (x1 - x0) * u, y0 + 4 * sag * u * (1 - u)


def pennants(word, x0, x1, y0, sag):
    """Each letter's pennant: (letter, x, y, half width, height, angle), hanging from the string and turned with it."""
    n = len(word)
    sw = min((x1 - x0) / n * 0.4, PENNANT_MAX)
    out = []
    for i, ch in enumerate(word):
        u = (i + 0.5) / n
        x, y = garland_at(x0, x1, y0, sag, u)
        out.append((ch, x, y, sw, sw * 1.7, float(np.arctan(4 * sag * (1 - 2 * u) / (x1 - x0)))))
    return out


def balloon_outline(x, y, rw, rh):
    """The mockup's balloon: two cubic curves from its bottom over its shoulders to its top, and back."""
    return (cubic((x, y + rh), (x - rw * 1.25, y + rh * 0.55), (x - rw * 1.1, y - rh), (x, y - rh))
            + cubic((x, y - rh), (x + rw * 1.1, y - rh), (x + rw * 1.25, y + rh * 0.55), (x, y + rh))[1:])


def balloon_string(x, y, rw, rh):
    """A balloon's string from its knot down to the dock's top edge at KNOT_X, curving a little."""
    return bezier((x, y + rh * 1.1), ((x + KNOT_X) / 2 + rw * 0.25, (y + rh + DOCK) / 2), (KNOT_X, DOCK), n=40)


def birthday(rng):
    img = c.vgradient([(0, "#1E0A28"), (.45, "#2E1236"), (TABLE / 108, "#3E1A3E")])
    c.radial(img, 112, 62, 90, [(0, "#FF8CBE", .18), (.5, "#FF8CBE", .06), (1, "#FF8CBE", 0)], ry=50, mode="add")
    # a soft light from above on the garlands, right of the header
    c.radial(img, 146, 18, 50, [(0, "#C890FF", .08), (1, "#C890FF", 0)], ry=22, mode="add")
    # party lights out of focus on the wall, right of the header and away from the name
    bokeh = [(float(rng.uniform(80, 190)), float(rng.uniform(36, 66)), float(rng.uniform(1.2, 2.6)),
              int(rng.integers(0, 5))) for _ in range(30)]
    for k, colour in enumerate(PENNANTS):
        c.add(img, c.shape(lambda p: [p.circle(x, y, r) for x, y, r, i in bokeh if i == k], blur=0.5) * 0.07, colour)
    # the table under the cake: its top seen from the front, darker towards the viewer
    table = c.vgradient([(0, "#4A2040"), (.25, "#341430"), (1, "#12060E")], top=TABLE / 108, bottom=1.0)
    ty = int(TABLE * c.U)
    img[ty:] = table[ty:]
    c.add(img, c.shape(lambda p: p.rect(0, TABLE, 192, 0.3), blur=0.15) * 0.12, "#FFA0C8")
    # the two garlands: the string, each pennant with its darker half and a soft shadow, the letter turned with it
    for word, x0, x1, y0, sag, first in GARLANDS:
        string = [garland_at(x0, x1, y0, sag, k / 40) for k in range(41)]
        c.blend(img, c.shape(lambda p: line(p, string, 0.16), blur=0.03) * 0.75, "#E8DCE8")
        flags = pennants(word, x0, x1, y0, sag)
        tri = [[turn(x, y, a, px, py) for px, py in ((-sw, 0), (sw, 0), (0, sh))] for _, x, y, sw, sh, a in flags]
        c.blend(img, c.shape(lambda p: [p.poly([(px + 0.5, py + 0.8) for px, py in t]) for t in tri], blur=0.6)
                * 0.35, "#000000")
        for i, t in enumerate(tri):
            colour = PENNANTS[(first + i) % len(PENNANTS)]
            c.blend(img, c.shape(lambda p: p.poly(t), blur=0.03), muted(colour, 0.9))
            c.blend(img, c.shape(lambda p: p.poly([t[0], t[2], (t[0][0] * 0.5 + t[1][0] * 0.5, t[0][1] * 0.5
                                                                 + t[1][1] * 0.5)][::-1]), blur=0.03) * 0.12,
                    "#FFFFFF")
            c.blend(img, c.shape(lambda p: p.poly([((t[0][0] + t[1][0]) / 2, (t[0][1] + t[1][1]) / 2), t[1], t[2]]),
                                 blur=0.03) * 0.16, "#000000")
        glyphs = [(ch, *turn(x, y, a, 0, sh * 0.34), sw * 0.95, a) for ch, x, y, sw, sh, a in flags]
        c.blend(img, letters(glyphs), "#FFF8F2")
    # the balloons' strings, then the balloons: shaded from the top left, with a knot and a highlight
    c.blend(img, c.shape(lambda p: [line(p, balloon_string(*b[:4]), 0.13) for b in BALLOONS], blur=0.03) * 0.6,
            "#F2E6F0")
    for x, y, rw, rh, colour in BALLOONS:
        body = c.shape(lambda p: p.poly(balloon_outline(x, y, rw, rh)), blur=0.04)
        box = (x - rw * 1.3, y - rh * 1.1, x + rw * 1.3, y + rh * 1.2)
        base = c.rgb(colour)
        light = "#%02X%02X%02X" % tuple(int(v) for v in np.minimum(255, base * 0.45 + 140))
        shaded(img, body, box, x - rw * 0.35, y - rh * 0.4, rh * 1.25,
               [(0, light), (.22, colour), (.75, "#%02X%02X%02X" % tuple(int(v) for v in base * 0.55)),
                (1, "#%02X%02X%02X" % tuple(int(v) for v in base * 0.35))])
        c.blend(img, c.shape(lambda p: p.poly(turned_ellipse(x - rw * 0.42, y - rh * 0.45, rw * 0.18, rh * 0.3, 0.45)),
                             blur=0.35) * 0.55, "#FFFFFF")
        c.blend(img, c.shape(lambda p: p.poly([(x, y + rh * 0.96), (x - rw * 0.16, y + rh * 1.13),
                                               (x + rw * 0.16, y + rh * 1.13)]), blur=0.03), base * 0.8)
    # the cake's shadow and plate
    c.blend(img, c.shape(lambda p: p.ellipse(CAKE_X + 1.5, CAKE_BOTTOM + PLATE_H + 0.3, PLATE_HW + 2, 1.0), blur=0.7)
            * 0.6, "#000000")
    plate = c.shape(lambda p: (p.rect(CAKE_X - PLATE_HW, CAKE_BOTTOM, PLATE_HW * 2, PLATE_H * 0.6),
                               p.ellipse(CAKE_X, CAKE_BOTTOM + PLATE_H * 0.6, PLATE_HW, PLATE_H * 0.4)), blur=0.04)
    c.blend(img, plate, muted("#E8DDB4", 0.82))
    c.add(img, c.shape(lambda p: p.rect(CAKE_X - PLATE_HW, CAKE_BOTTOM, PLATE_HW * 2, 0.25), blur=0.08) * 0.25,
          "#FFFFFF")
    # the cake: sponge in two layers with a cream filling, shaded round, under pink icing with drips
    body = c.shape(lambda p: p.rect(CAKE_X - CAKE_HW, CAKE_TOP, CAKE_HW * 2, CAKE_BOTTOM - CAKE_TOP), blur=0.04)
    box = (CAKE_X - CAKE_HW - 1, CAKE_TOP - 1, CAKE_X + CAKE_HW + 1, CAKE_BOTTOM + 1)
    hshaded(img, body, box, CAKE_X - CAKE_HW, CAKE_X + CAKE_HW,
            [(0, "#C8A0AE"), (.3, "#E2C2CC"), (.62, "#D6B2BE"), (1, "#9A7482")])
    c.blend(img, c.shape(lambda p: p.rect(CAKE_X - CAKE_HW, 66.2, CAKE_HW * 2, 1.1), blur=0.08) * 0.85, "#E86A92")
    c.blend(img, c.shape(lambda p: p.rect(CAKE_X - CAKE_HW, 67.3, CAKE_HW * 2, 0.4), blur=0.1) * 0.5, "#F6E6D2")
    drip = [(CAKE_X - CAKE_HW, CAKE_TOP - 0.3)]
    for i in range(17):
        x = CAKE_X - CAKE_HW + i * 192 * 0.01      # the mockup's zigzag, a step of .01 of the width
        drip.append((x, CAKE_TOP + 3.2 + (i % 2) * 1.6 + (1.4 if i % 4 == 1 else 0)))
    drip.append((CAKE_X + CAKE_HW, CAKE_TOP - 0.3))
    icing = c.shape(lambda p: (p.poly(drip), [p.circle(x, y, 0.75) for x, y in drip[1:-1]]), blur=0.05)
    hshaded(img, icing, box, CAKE_X - CAKE_HW, CAKE_X + CAKE_HW,
            [(0, "#D86A92"), (.3, "#FF8FB1"), (.65, "#F07CA2"), (1, "#B04A72")])
    c.add(img, c.shape(lambda p: p.rect(CAKE_X - CAKE_HW + 0.5, CAKE_TOP + 0.2, CAKE_HW * 2 - 1, 0.5), blur=0.25)
          * 0.3, "#FFFFFF")
    sprinkles = [(float(rng.uniform(CAKE_X - CAKE_HW + 1, CAKE_X + CAKE_HW - 1)),
                  float(rng.uniform(CAKE_TOP + 0.4, CAKE_TOP + 2.6)), float(rng.uniform(0, 3.1)),
                  int(rng.integers(0, 4))) for _ in range(46)]
    for k, colour in enumerate(CANDLE_COLOURS):
        c.blend(img, c.shape(lambda p: [p.poly(turned_ellipse(x, y, 0.42, 0.14, a, n=8)) for x, y, a, i in sprinkles
                                        if i == k], blur=0.03) * 0.9, colour)
    # four candles with a spiral stripe, their wicks and their flames at rest (the overlay flickers them)
    for dx, colour in zip(CANDLE_DX, CANDLE_COLOURS):
        x = CAKE_X + dx
        candle = c.shape(lambda p: p.rect(x - CANDLE_W / 2, CANDLE_TOP, CANDLE_W, CAKE_TOP + 1.2 - CANDLE_TOP),
                         blur=0.03)
        hshaded(img, candle, (x - 1.5, CANDLE_TOP - 0.5, x + 1.5, CAKE_TOP + 2), x - CANDLE_W / 2, x + CANDLE_W / 2,
                [(0, "#%02X%02X%02X" % tuple(int(v) for v in c.rgb(colour) * 0.75)), (.35, colour),
                 (1, "#%02X%02X%02X" % tuple(int(v) for v in c.rgb(colour) * 0.55))])
        stripes = c.shape(lambda p: [p.poly([(x - CANDLE_W / 2, CANDLE_TOP + 1.4 + k * 1.8),
                                             (x + CANDLE_W / 2, CANDLE_TOP + 0.6 + k * 1.8),
                                             (x + CANDLE_W / 2, CANDLE_TOP + 1.1 + k * 1.8),
                                             (x - CANDLE_W / 2, CANDLE_TOP + 1.9 + k * 1.8)]) for k in range(5)],
                          blur=0.03)
        c.blend(img, stripes * candle * 0.75, "#FFFFFF")
        c.blend(img, c.shape(lambda p: p.rect(x - 0.08, FLAME_Y + 0.4, 0.16, CANDLE_TOP - FLAME_Y - 0.3)), "#2A1A10")
        c.radial(img, x, FLAME_Y, 3.84, [(0, "#FFC85A", .35), (.5, "#FFC85A", .10), (1, "#FFC85A", 0)], mode="add")
        c.blend(img, c.shape(lambda p: p.ellipse(x, FLAME_Y, FLAME_W * 0.85, FLAME_H * 0.85), blur=0.1), "#FFD27A")
    # confetti lying on the table, right of the cake and under the name
    bits = [(float(rng.uniform(118, 190)), float(rng.uniform(TABLE + 1.0, DOCK - 0.8)), float(rng.uniform(0, 3.14)),
             int(rng.integers(0, 5))) for _ in range(90)]
    bits += [(float(rng.uniform(80, 118)), float(rng.uniform(CAKE_BOTTOM + PLATE_H + 0.6, DOCK - 0.6)),
              float(rng.uniform(0, 3.14)), int(rng.integers(0, 5))) for _ in range(14)]
    for k, colour in enumerate(PENNANTS):
        c.blend(img, c.shape(lambda p: [p.poly(turned_ellipse(x, y, 0.55, 0.22, a, n=4)) for x, y, a, i in bits
                                        if i == k], blur=0.03) * 0.8, muted(colour, 0.75))
    shade(img, 74, 100, 0.3)
    corners(img, 0.45)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# ---- anniversary ("two suns") -------------------------------------------------------------------------------------
STARS_SEED, STARS, STARS_TOP = 61, 40, 0.4   # the mockup's rng(61): x of the width, y of .4 of the height
SUNS = [(115.2, 64.5, 7.56, "#F2D29C"), (128.64, 66.4, 5.4, "#F2A472")]   # .6 / .67 of the width, close together
# The three rows of dunes: base (units), phase, sand and lit ridge (the mockup's), from back to front.
DUNES = [(67.8, 1, "#7A3E22", "#D88A56"), (70.9, 3, "#5A2C18", "#B86A3E"), (73.8, 5, "#3A1C0E", "#8A4C28")]
DUNE_AMP = 3.0                          # the mockup's h * .018, deeper so the rows read as dunes
PROJ_X, PROJ_BASE = 157.44, 75.6        # the projector half in the sand on the right (the mockup's .82)
LENS_Y = PROJ_BASE - 2.85               # its lens, where the overlay's beam starts


def anniversary_stars():
    """The mockup's forty stars (rng(61), four values each: x, y, s, p) as (x, y, s) in units, none in the header."""
    r = lcg(STARS_SEED)
    out = []
    for _ in range(STARS):
        x, y, s, _p = r(), r(), r(), r()
        x, y = x * 192, y * 108 * STARS_TOP
        if not (x < 76 and y < 34):
            out.append((x, y, s))
    return out


def dune(base, ph):
    """A dune row's crest for every pixel column: the mockup's base + sin(x * 4 + ph) * h * .018 + sin(x * 11 + ph)
    * h * .006, with x as a fraction of the width."""
    fx = XS / 192
    return (base + np.sin(fx * 4 + ph) * DUNE_AMP + np.sin(fx * 11 + ph) * DUNE_AMP / 3).astype(np.float32)


def anniversary(rng):
    img = c.vgradient([(0, "#05081A"), (.30, "#151634"), (.45, "#36234C"), (.56, "#8A4A50"), (.62, "#C8664A"),
                       (.68, "#E6915E")])
    # the stars, faint; the overlay lets them twinkle
    stars = anniversary_stars()
    c.add(img, c.shape(lambda p: [p.circle(x, y, 0.14 + 0.1 * s) for x, y, s in stars], blur=0.08) * 0.75, "#F4F0FF")
    # the two suns low over the dunes, each in its glow, the second smaller and redder
    for x, y, r, colour in SUNS:
        c.radial(img, x, y, r * 5.2, [(0, colour, .22), (.35, colour, .08), (1, colour, 0)], ry=r * 3.8, mode="add")
    for x, y, r, colour in SUNS:
        disc = c.shape(lambda p: p.circle(x, y, r), blur=0.12)
        shaded(img, disc, (x - r - 1, y - r - 1, x + r + 1, y + r + 1), x, y, r,
               [(0, "#FFF4DA"), (.7, colour), (1, "#%02X%02X%02X" % tuple(int(v) for v in c.rgb(colour) * 0.9))])
    # the dunes, back to front: sand darkening downwards, a lit ridge facing the suns, soft ripples
    for k, (base, ph, sand, ridge) in enumerate(DUNES):
        top = dune(base, ph)
        mask = under(top)
        lit = np.clip((YS[:, None] - top[None, :]), 0, None)
        sand_c, deep = c.rgb(sand), c.rgb(sand) * 0.55
        colour = sand_c[None, None, :] * np.exp(-lit / 5.0)[:, :, None] + deep[None, None, :] * (
            1 - np.exp(-lit / 5.0))[:, :, None]
        c.blend(img, mask, colour.astype(np.float32))
        # the sunlit ridge, brighter near the suns (left of the middle) and fading to the right
        near = np.clip(1.25 - np.abs(XS - 122) / 90, 0.35, 1.0)[None, :]
        crest_line = np.where(lit >= 0, np.exp(-lit / 0.5), 0) * mask * near
        c.blend(img, (crest_line * 0.85).astype(np.float32), ridge)
        # the dune's lee side, away from the suns, in shadow
        slope = np.gradient(top)[None, :] * c.U
        c.blend(img, (mask * np.clip(-slope * 1.5, 0, 0.45) * np.exp(-lit / 6.0)).astype(np.float32), "#1A0A04")
        ripples = (0.5 + 0.5 * np.sin(YS[:, None] * 5.5 + XS[None, :] * 0.35 + k * 2)) ** 6
        c.blend(img, (ripples * mask * np.exp(-lit / 3.0) * 0.12).astype(np.float32), "#000000")
        if k == 1:
            # the projector stands in the second row, before the front one buries its foot
            body = c.shape(lambda p: p.poly([(PROJ_X - 3.07, PROJ_BASE), (PROJ_X - 1.92, PROJ_BASE - 2.7),
                                             (PROJ_X + 1.92, PROJ_BASE - 2.7), (PROJ_X + 3.07, PROJ_BASE)]),
                           blur=0.04)
            hshaded(img, body, (PROJ_X - 4, PROJ_BASE - 4, PROJ_X + 4, PROJ_BASE + 1), PROJ_X - 3.07,
                    PROJ_X + 3.07, [(0, "#4A5260"), (.4, "#2E3440"), (1, "#14181F")])
            c.blend(img, c.shape(lambda p: p.rect(PROJ_X - 1.92, PROJ_BASE - 2.75, 3.84, 0.3), blur=0.05) * 0.6,
                    "#8A94A8")
            c.blend(img, c.shape(lambda p: p.ellipse(PROJ_X, LENS_Y, 1.0, 0.35), blur=0.05), "#B8DCFF")
            c.radial(img, PROJ_X, LENS_Y, 2.4, [(0, "#A0D2FF", .55), (1, "#A0D2FF", 0)], mode="add")
    # a little sand heaped against the projector's foot
    c.blend(img, c.shape(lambda p: p.ellipse(PROJ_X + 0.3, PROJ_BASE + 0.15, 4.2, 0.9), blur=0.35) * 0.9,
            muted(DUNES[2][2], 0.95))
    shade(img, 74, 102, 0.25)
    corners(img, 0.35)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# ---- wedding ------------------------------------------------------------------------------------------------------
LAWN = 74.2                              # where the lawn begins behind the arch
ARCH_X, ARCH_FOOT, ARCH_W, ARCH_H = 138.24, 75.6, 26.88, 45.36   # the mockup's .72, .14 and .42; feet above the dock
ARCH_POST = 0.55                         # the posts rise .55 of the height, the arch's top is a half ellipse
RING_Y, RING_R = 57.24, 5.76             # the rings in the arch (the mockup's .53 of the height, .03 of the width)
RING_DX = 0.55                           # each ring's centre RING_DX * RING_R either side of ARCH_X
LIGHTS = 22                              # bulbs along the arch (the overlay lets them twinkle)
ROSE_COLOURS = ("#F2B4C4", "#E06A88", "#F4E6E6")   # the mockup's three, a little muted


def arch_point(u):
    """A point of the arch's path at u (0..1): up the left post, over the half ellipse, down the right post (the
    mockup's path(u))."""
    if u < 0.3:
        return ARCH_X - ARCH_W, ARCH_FOOT - u / 0.3 * ARCH_H * ARCH_POST
    if u > 0.7:
        return ARCH_X + ARCH_W, ARCH_FOOT - (1 - u) / 0.3 * ARCH_H * ARCH_POST
    a = np.pi + (u - 0.3) / 0.4 * np.pi
    return (ARCH_X + np.cos(a) * ARCH_W, ARCH_FOOT - ARCH_H * ARCH_POST + np.sin(a) * ARCH_H * (1 - ARCH_POST))


def tree(p, x, y, r):
    """A round garden tree's crown of puffs on its trunk."""
    p.rect(x - 0.5, y, 1.0, LAWN - y + 1)
    for dx, dy, k in ((0, 0, 1), (-0.6, 0.3, 0.75), (0.6, 0.35, 0.7), (0.15, -0.55, 0.65), (-0.3, -0.4, 0.6)):
        p.circle(x + dx * r, y - r * 0.6 + dy * r, r * k)


def wedding(rng):
    img = c.vgradient([(0, "#120A22"), (.30, "#2A1630"), (.50, "#5A3050"), (.64, "#8A4A5E"), (.70, "#A45E66")])
    c.radial(img, ARCH_X, 70, 70, [(0, "#FFBEC8", .16), (1, "#FFBEC8", 0)], ry=30, mode="add")
    c.stars(img, [(x, y, r) for x, y, r in c.random_stars(rng, 26, (84, 188), (3, 40), (0.14, 0.3))
                  if not (abs(x - ARCH_X) < 50 and 10 < y < 25)], "#FFE8F0", glow=0.08)
    # trees and a hedge far off, the lawn
    c.blend(img, c.shape(lambda p: [tree(p, x, y, r) for x, y, r in ((84, 62, 6.5), (97, 60, 8), (108, 64, 5.5),
                                                                    (172, 61, 7.5), (184, 63, 6.5))], blur=0.1),
            "#1A2018")
    hedge = 71.2 - np.abs(np.sin(XS * 0.55)) * 1.1 - np.abs(np.sin(XS * 1.7 + 1)) * 0.35
    c.blend(img, under(hedge.astype(np.float32)), "#1E2A1C")
    lawn = c.vgradient([(0, "#24341E"), (.3, "#162212"), (1, "#070B06")], top=LAWN / 108, bottom=1.0)
    ly = int(LAWN * c.U)
    img[ly:] = lawn[ly:]
    c.add(img, c.shape(lambda p: p.rect(0, LAWN, 192, 0.3), blur=0.2) * 0.12, "#FFC8B4")
    # the arch's shadow on the lawn, then the arch: two white rails with slats between them
    c.blend(img, c.shape(lambda p: p.ellipse(ARCH_X, ARCH_FOOT + 0.3, ARCH_W + 4, 1.0), blur=0.8) * 0.5, "#000000")
    outer = [arch_point(k / 120) for k in range(121)]
    inner = [(ARCH_X + (x - ARCH_X) * (ARCH_W - 1.3) / ARCH_W, ARCH_FOOT - (ARCH_FOOT - y) * (1 - 1.3 / ARCH_H))
             for x, y in outer]
    slats = [(outer[k], inner[k]) for k in range(4, 118, 6)]
    rails = c.shape(lambda p: (line(p, outer, 1.0), line(p, inner, 0.7), [line(p, s, 0.35) for s in slats]),
                    blur=0.04)
    c.blend(img, rails, muted("#E8E0D0", 0.78))
    c.blend(img, c.shape(lambda p: line(p, [(x + 0.25, y - 0.25) for x, y in outer], 0.3), blur=0.1) * rails * 0.35,
            "#FFFFFF")
    # the greenery climbing the arch (the mockup's rng(131) leaves, and more of them), then the roses
    r = lcg(131)
    leaves = []
    for i in range(90):
        x, y = arch_point(r())
        leaves.append((x + (r() - 0.5) * 2.3, y + (r() - 0.5) * 2.16, r() * 3, i % 2))
    roses = []
    for i in range(26):
        x, y = arch_point(r())
        roses.append((x + (r() - 0.5) * 1.92, y + (r() - 0.5) * 1.62, i % 3))
    for _ in range(260):
        x, y = arch_point(float(rng.uniform(0.02, 0.98)))
        leaves.append((x + float(rng.normal(0, 1.1)), y + float(rng.normal(0, 1.0)), float(rng.uniform(0, 3.1)),
                       int(rng.integers(0, 3))))
    for k, colour in enumerate(("#2F5A2A", "#3A6A32", "#24481F")):
        c.blend(img, c.shape(lambda p: [p.poly(turned_ellipse(x, y, 1.25, 0.6, a, n=10)) for x, y, a, i in leaves
                                        if i == k], blur=0.04), muted(colour, 0.9))
    c.add(img, c.shape(lambda p: [p.poly(turned_ellipse(x - 0.2, y - 0.2, 0.7, 0.25, a, n=8))
                                  for x, y, a, i in leaves[::3]], blur=0.1) * 0.12, "#B8E0A0")
    for _ in range(18):
        x, y = arch_point(float(rng.uniform(0.04, 0.96)))
        roses.append((x + float(rng.normal(0, 0.9)), y + float(rng.normal(0, 0.8)), int(rng.integers(0, 3))))
    for x, y, k in roses:
        colour = ROSE_COLOURS[k]
        c.blend(img, c.shape(lambda p: p.circle(x, y, 1.15), blur=0.05), muted(colour, 0.7))
        c.blend(img, c.shape(lambda p: (p.ellipse(x - 0.3, y - 0.25, 0.8, 0.7), p.ellipse(x + 0.35, y - 0.1, 0.6,
                                                                                          0.65)), blur=0.15) * 0.8,
                muted(colour, 0.9))
        c.blend(img, c.shape(lambda p: ring(p, x + 0.05, y - 0.05, 0.42, 0.38, 0.12), blur=0.05) * 0.5,
                muted(colour, 0.45))
    # the string of lights along the arch, at rest (the overlay lets them twinkle)
    bulbs = [arch_point((i + 0.5) / LIGHTS) for i in range(LIGHTS)]
    c.blend(img, c.shape(lambda p: line(p, [arch_point(k / 120) for k in range(121)], 0.12), blur=0.03) * 0.45,
            "#3A3020")
    for x, y in bulbs:
        c.radial(img, x, y, 2.3, [(0, "#FFE1A0", .30), (1, "#FFE1A0", 0)], mode="add")
    c.blend(img, c.shape(lambda p: [p.circle(x, y, 0.42) for x, y in bulbs], blur=0.06), "#FFEBC0")
    # two golden rings, interlocking: the right one passes over the left at the bottom crossing
    c.radial(img, ARCH_X, RING_Y, 16, [(0, "#FFE6A0", .12), (1, "#FFE6A0", 0)], mode="add")
    gold = [(0, "#FFF3C4"), (.45, "#E8C060"), (.8, "#B08428"), (1, "#806018")]
    for k, sign in enumerate((-1, 1)):
        x = ARCH_X + sign * RING_DX * RING_R
        band = c.shape(lambda p: ring(p, x, RING_Y, RING_R, RING_R * 0.9, 1.15), blur=0.04)
        shaded(img, band, (x - RING_R - 1, RING_Y - RING_R - 1, x + RING_R + 1, RING_Y + RING_R + 1),
               x - RING_R * 0.7, RING_Y - RING_R * 0.7, RING_R * 2.4, gold)
        if k == 0:
            left = band
    cross_y = RING_Y + RING_R * 0.9 * np.sqrt(1 - RING_DX ** 2)
    x = ARCH_X - RING_DX * RING_R
    # at the top crossing the left ring lies over the right one again
    top_cross = c.shape(lambda p: p.circle(ARCH_X, RING_Y - (cross_y - RING_Y), 1.6), blur=0.3)
    shaded(img, left * top_cross, (x - RING_R - 1, RING_Y - RING_R - 1, x + RING_R + 1, RING_Y + RING_R + 1),
           x - RING_R * 0.7, RING_Y - RING_R * 0.7, RING_R * 2.4, gold)
    c.add(img, c.shape(lambda p: [p.circle(ARCH_X + s * RING_DX * RING_R - RING_R * 0.55, RING_Y - RING_R * 0.6, 0.45)
                                  for s in (-1, 1)], blur=0.3) * 0.5, "#FFFFFF")
    # petals on the lawn
    petals = [(float(rng.uniform(96, 190)), float(rng.uniform(LAWN + 0.6, DOCK - 0.6)), float(rng.uniform(0, 3)))
              for _ in range(24)]
    c.blend(img, c.shape(lambda p: [p.poly(turned_ellipse(x, y, 0.85, 0.4, a)) for x, y, a in petals], blur=0.05)
            * 0.8, muted("#F2B4C4", 0.7))
    shade(img, 74, 100, 0.3)
    corners(img, 0.4)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# name: (painter, shape seed, grain seed)
PERSONAL = {
    "birthday": (birthday, 3201, 71),
    "anniversary": (anniversary, 3202, 72),
    "wedding": (wedding, 3203, 73),
}
