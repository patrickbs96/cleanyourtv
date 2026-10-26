"""Jahreskreis' occasions from Halloween to Christmas (spec § 4.2), one picture each, drawn from code alone:

    halloween     an orange moon, a bare tree and three ribbed pumpkins with stems and carved faces on a dark hill
                  (the faces' flicker and the bats are the overlay's)
    st_martin     a procession of children with paper lanterns on sticks at dusk, a town far off (the lanterns swing)
    nikolaus_eve  a front door with a wreath and a lantern in the snow, two polished boots on the mat, still empty
    nikolaus      the same, the boots filled with mandarins, nuts, chocolate and a candy cane
    advent        a room: a wreath of fir with berries and ribbons on a wooden table, four unlit candles, a plate of
                  cookies, a window with snow and a glowing paper star, a string of lights at the top (the overlay
                  lights as many candles as Sundays of Advent have passed, twinkles the lights and lets it snow)
    christmas     a decorated tree (baubles, tinsel garlands, lights, a star) with presents under it on snowy ground
                  (the lights twinkle and snow falls in the overlay)

The shapes follow the mockup's painters (jahreskreis-mockup.html, PAINT.halloween/martin/nikolaus/advent/xmas) in the
192 x 108 units of common.py; where the mockup draws from its seeded random (the tree's baubles and lights) the same
generator (seasons.lcg) puts them where it does there. All six are dark looks with no night picture of their own.
Every picture is whole without its motion (reduced motion): the faces glow softly, the lanterns hang lit, the Advent
candles stand unlit. Where OccasionMotionsB.java draws over a painted thing (the faces, the lanterns, the candles'
wicks, the tree's lights) it uses the same numbers as here; they are named in capitals.

Picture rule (spec § 3): what matters stands right of the middle, directly above the dock (its top edge at y 76.6
units), never behind it and never in the header on the top left (x below 76, y below 34).
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import common as c  # noqa: E402
from occasions_a import bezier, line, turned_ellipse  # noqa: E402
from seasons import XS, corners, crest, fill, fir, fir_snow, lcg, ridge, shade, sky_stars, under  # noqa: E402

TAU = 2 * np.pi


# ---- drawing helpers ----------------------------------------------------------------------------------------------
def _box(box):
    x0, y0, x1, y1 = box
    return (max(0, int(x0 * c.U)), max(0, int(y0 * c.U)), min(c.w, int(x1 * c.U) + 1), min(c.h, int(y1 * c.U) + 1))


def _paint(img, mask, box, d, stops):
    """Paints mask inside box with colours from (position, '#hex') stops along d (positions per pixel of the box)."""
    x0, y0, x1, y1 = _box(box)
    if x0 >= x1 or y0 >= y1:
        return img
    d = d(*np.mgrid[y0:y1, x0:x1].astype(np.float32)[::-1] / c.U)
    pos = [p for p, _ in stops]
    cols = [c.rgb(col) for _, col in stops]
    colour = np.stack([np.interp(d, pos, [col[i] for col in cols]) for i in range(3)], axis=2).astype(np.float32)
    a = mask[y0:y1, x0:x1, None]
    part = img[y0:y1, x0:x1]
    part *= 1 - a
    part += a * colour
    return img


def shaded(img, mask, box, cx, cy, r, stops):
    """Paints mask with a radial gradient from (cx, cy) over radius r (units), computed only inside box."""
    return _paint(img, mask, box, lambda xx, yy: np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r, stops)


def hshaded(img, mask, box, x0, x1, stops):
    """Paints mask with a horizontal gradient from x0 to x1 (units), computed only inside box."""
    return _paint(img, mask, box, lambda xx, yy: (xx - x0) / (x1 - x0), stops)


def ring(p, cx, cy, rx, ry, width):
    """An ellipse's outline, width in units."""
    p.d.ellipse([(cx - rx) * c.U, (cy - ry) * c.U, (cx + rx) * c.U, (cy + ry) * c.U], outline=255,
                width=max(1, round(width * c.U)))


def frame(p, x, y, rw, rh, width):
    """A rectangle's outline, width in units."""
    p.d.rectangle([x * c.U, y * c.U, (x + rw) * c.U, (y + rh) * c.U], outline=255, width=max(1, round(width * c.U)))


def star_points(cx, cy, r_out, r_in, n, turn=-np.pi / 2):
    """A star of n points as 2n corners."""
    return [(cx + np.cos(turn + k * np.pi / n) * (r_out if k % 2 == 0 else r_in),
             cy + np.sin(turn + k * np.pi / n) * (r_out if k % 2 == 0 else r_in)) for k in range(2 * n)]


def fir_needles(rng, cx, cy, rx, ry, n, inner=0.72, outer=1.12, length=1.9):
    """Short strokes of fir round an ellipse (a wreath): (x0, y0, x1, y1) in units."""
    out = []
    for _ in range(n):
        a, rr = float(rng.uniform(0, TAU)), float(rng.uniform(inner, outer))
        x, y = cx + np.cos(a) * rx * rr, cy + np.sin(a) * ry * rr
        ang = a + np.pi / 2 + float(rng.uniform(-0.9, 0.9))
        ln = float(rng.uniform(0.5, 1.0)) * length
        out.append((x, y, x + np.cos(ang) * ln, y + np.sin(ang) * ln * 0.7))
    return out


def strokes(img, segs, width, colour, alpha=1.0, blur=0.03):
    c.blend(img, c.shape(lambda p: [line(p, [(x0, y0), (x1, y1)], width) for x0, y0, x1, y1 in segs], blur=blur)
            * alpha, colour)


# ---- halloween ----------------------------------------------------------------------------------------------------
MOON_X, MOON_Y, MOON_R = 146.0, 26.0, 11.0
PUMPKIN_GROUND = 75.8                   # where the pumpkins stand, just above the dock's top edge
# The mockup's three pumpkins (x .56 .7 .82 of the width, radius .03 .042 .026 of it), whole above the dock.
PUMPKINS = [(108.0, 6.4), (133.0, 8.8), (157.5, 5.6)]
# A carved face in fractions of the pumpkin's radius round its centre (x, ground - .82 r), the mockup's: two eyes,
# a nose and a jagged mouth closed by a curve through (0, .78) (OccasionMotionsB.Halloween draws the same).
FACE_EYES = [[(-0.48, -0.08), (-0.26, -0.42), (-0.10, -0.08)], [(0.10, -0.08), (0.26, -0.42), (0.48, -0.08)],
             [(-0.06, 0.02), (0.0, -0.12), (0.06, 0.02)]]
FACE_MOUTH = [(-0.55, 0.20), (-0.40, 0.45), (-0.30, 0.30), (-0.18, 0.50), (-0.05, 0.34), (0.08, 0.52), (0.20, 0.34),
              (0.32, 0.48), (0.44, 0.30), (0.55, 0.20)]
FACE_CURVE = (0.0, 0.78)


def face_polys(x, cy, r):
    """The face's four shapes in units: eyes, nose and the mouth with its curved lower lip."""
    polys = [[(x + fx * r, cy + fy * r) for fx, fy in eye] for eye in FACE_EYES]
    mouth = [(x + fx * r, cy + fy * r) for fx, fy in FACE_MOUTH]
    lip = bezier(mouth[-1], (x + FACE_CURVE[0] * r, cy + FACE_CURVE[1] * r), mouth[0], n=24)
    return polys + [mouth + lip[1:]]


def pumpkin(img, x, r):
    cy = PUMPKIN_GROUND - r * 0.82
    box = (x - r * 1.5, cy - r * 1.3, x + r * 1.5, PUMPKIN_GROUND + 0.5)
    # its shadow on the hill and the warm light it throws round it
    c.blend(img, c.shape(lambda p: p.ellipse(x + r * 0.15, PUMPKIN_GROUND - 0.1, r * 1.35, r * 0.2), blur=0.5) * 0.6,
            "#000000")
    c.radial(img, x, cy, r * 2.2, [(0, "#FF8C1E", .16), (.5, "#FF8C1E", .05), (1, "#FF8C1E", 0)], mode="add")
    # the ribs, outer first so the inner ones overlap them, each round and shaded from the top left, with a dark seam
    for k in (3, -3, 2, -2, 1, -1, 0):
        sx, rx, ry = x + k * r * 0.27, r * (0.46 - abs(k) * 0.04), r * (0.86 - abs(k) * 0.05)
        rib = c.shape(lambda p: p.ellipse(sx, cy, rx, ry), blur=0.04)
        shaded(img, rib, box, sx - r * 0.1, cy - r * 0.32, r * 0.85,
               [(0, "#F0A040"), (.5, "#D2661A"), (.85, "#8A3208"), (1, "#5A2004")])
        c.blend(img, c.shape(lambda p: ring(p, sx, cy, rx, ry, max(0.12, r * 0.025)), blur=0.12) * 0.3, "#5A1E05")
    # the stem, bent, and a curl of vine
    stem = ([(x - r * 0.07, cy - r * 0.78)] + bezier((x - r * 0.07, cy - r * 0.78), (x - r * 0.02, cy - r * 1.12),
                                                     (x + r * 0.16, cy - r * 1.16), n=16)
            + [(x + r * 0.1, cy - r * 1.04)] + bezier((x + r * 0.1, cy - r * 1.04), (x + r * 0.05, cy - r * 0.9),
                                                      (x + r * 0.08, cy - r * 0.78), n=16))
    c.blend(img, c.shape(lambda p: p.poly(stem), blur=0.04), "#4A3A1A")
    c.blend(img, c.shape(lambda p: p.poly(stem[:12]), blur=0.2) * 0.35, "#8A7A4A")
    vine = [(x + r * 0.05 + np.sin(a) * r * 0.22 * (1 - a / 9) + a * r * 0.04, cy - r * 0.92 + np.cos(a) * r * 0.12)
            for a in np.linspace(0, 8, 30)]
    c.blend(img, c.shape(lambda p: line(p, vine, max(0.15, r * 0.04)), blur=0.03), "#3D6B2A")
    # the carved face: dark where it is cut, lit softly from the candle inside (the overlay flickers it)
    polys = face_polys(x, cy, r)
    cut = c.shape(lambda p: [p.poly(q) for q in polys], blur=0.04)
    c.blend(img, cut, "#3A1404")
    inner = c.shape(lambda p: [p.poly(q) for q in polys], blur=0.25)
    c.blend(img, cut * inner, "#F0A838")
    c.blend(img, cut * inner ** 3 * 0.8, "#FFD870")
    c.add(img, c.shape(lambda p: [p.poly(q) for q in polys], blur=r * 0.15) * 0.25, "#FF9A30")


def bare_tree(p):
    """A bare, gnarled tree on the right, standing on the hill."""
    p.poly([(176.4, 75.2), (177.2, 60), (176.6, 52), (177.6, 46), (178.4, 46.4), (178.0, 52), (178.8, 60),
            (179.6, 75.2)])
    for pts, wdt in (([(177.6, 60), (173, 55.5), (169.5, 54.2)], 0.55), ([(178.2, 56), (183, 51), (186.5, 50.2)], 0.5),
                     ([(177.6, 50), (175.2, 45.5), (175.8, 41.5)], 0.4), ([(178.0, 48), (181, 44), (184.5, 43)], 0.35),
                     ([(173, 55.5), (171.6, 51.8)], 0.3), ([(183, 51), (184.2, 47.5)], 0.3),
                     ([(175.6, 44), (173.2, 42.2)], 0.22), ([(181, 44), (181.4, 40.6)], 0.22)):
        line(p, pts, wdt)


def halloween(rng):
    img = c.vgradient([(0, "#0C0618"), (.35, "#180C2A"), (.62, "#2A1436"), (.72, "#2E1638")])
    c.stars(img, sky_stars(rng, 24, 46, (0.15, 0.32)), "#E8DCF0", glow=0.08)
    # the orange moon with its craters and glow, high on the right but clear of the corner
    c.radial(img, MOON_X, MOON_Y, 42, [(0, "#FF9628", .30), (.35, "#FF9628", .10), (1, "#FF9628", 0)], mode="add")
    c.blend(img, c.shape(lambda p: p.circle(MOON_X, MOON_Y, MOON_R), blur=0.1), "#F2BC66")
    moon = c.shape(lambda p: p.circle(MOON_X, MOON_Y, MOON_R))
    c.blend(img, c.shape(lambda p: [p.circle(MOON_X + dx * 192, MOON_Y + dy * 108, rr * 108)
                                    for dx, dy, rr in ((-.03, -.02, .03), (.04, .03, .02), (.01, .06, .015),
                                                       (-.015, .055, .01))], blur=0.4) * moon * 0.3, "#C07828")
    c.blend(img, moon * (1 - c.shape(lambda p: p.circle(MOON_X - 2.2, MOON_Y - 2.2, MOON_R * 0.95), blur=3)) * 0.35,
            "#A05A18")
    # a far hill, then the near one the pumpkins stand on
    fill(img, under(ridge(66.0, 2.6, 3)), "#1A0E26", "#120A1C", 62, 80)
    c.add(img, crest(ridge(66.0, 2.6, 3), 1.0) * 0.08, "#C08060")
    near = ridge(73.8, 1.0, 1)
    c.blend(img, under(near), "#0B0612")
    c.blend(img, c.shape(bare_tree, blur=0.05), "#0B0612")
    # dry grass along the near crest
    blades = [(float(x), float(near[min(c.w - 1, int(x * c.U))]) + 0.3, float(rng.uniform(0.6, 1.6)),
               float(rng.uniform(-0.6, 0.6))) for x in rng.uniform(78, 192, 140)]
    c.blend(img, c.shape(lambda p: [line(p, [(x, y), (x + lean, y - ht)], 0.18) for x, y, ht, lean in blades],
                         blur=0.03), "#0B0612")
    for x, r in PUMPKINS:
        pumpkin(img, x, r)
    shade(img, 76, 100, 0.25)
    corners(img, 0.45)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# ---- st. martin ---------------------------------------------------------------------------------------------------
MARTIN_GROUND = 75.9
# The children of the procession, walking right: x of their feet and their height (units). Each holds a stick
# forward; its tip is LANTERN_TIP from the feet, and the lantern hangs from it on a string of LANTERN_STRING, its
# centre LANTERN_STRING + LANTERN_R below the tip (OccasionMotionsB.Lanterns swings it about the tip).
CHILDREN = [(84.0, 11.5), (98.0, 13.0), (111.5, 10.5), (125.0, 12.5), (139.0, 11.0), (152.5, 13.5), (166.0, 12.0)]
LANTERN_TIP_DX, LANTERN_TIP_UP = 4.6, 2.6   # the tip: x + 4.6, ground - height - 2.6
LANTERN_STRING, LANTERN_R = 1.0, 2.3
LANTERN_COLOURS = ("#FFCF5A", "#FF8A5C", "#9FD8FF")   # the mockup's three
LANTERN_LIGHT = ("#FFF0C0", "#FFD0B0", "#E0F4FF")


def lantern_tip(x, ht):
    return x + LANTERN_TIP_DX, MARTIN_GROUND - ht - LANTERN_TIP_UP


def child(p, i, x, ht):
    """A child's silhouette walking right: legs mid-stride, a coat, a head with a cap, an arm holding the stick."""
    g, s = MARTIN_GROUND, ht / 12
    stride = 0.9 if i % 2 else -0.7
    line(p, [(x - 0.3, g - 4.2 * s), (x - 0.5 - stride, g - 0.2)], 1.0 * s)
    line(p, [(x + 0.3, g - 4.2 * s), (x + 0.5 + stride, g - 0.2)], 1.0 * s)
    p.poly([(x - 1.9 * s, g - 3.8 * s), (x - 1.2 * s, g - 9.0 * s), (x + 1.2 * s, g - 9.0 * s),
            (x + 1.9 * s, g - 3.8 * s)])
    p.circle(x + 0.1, g - 10.2 * s, 1.45 * s)
    if i % 3 == 0:
        p.circle(x + 0.1, g - 11.9 * s, 0.6 * s)             # a bobble hat
    else:
        p.poly([(x - 1.4 * s, g - 10.4 * s), (x + 0.1, g - 12.3 * s), (x + 1.6 * s, g - 10.4 * s)])
    tx, ty = lantern_tip(x, ht)
    hand = (x + 1.6 * s, g - 6.6 * s)
    line(p, [(x + 0.6 * s, g - 8.4 * s), hand], 0.75 * s)
    line(p, [hand, (tx, ty)], 0.32)


def town(p):
    """A town far off on the left of the middle: roofs and a church with its spire, low over the ground."""
    roofs = [(34, 71), (38, 67.5), (42, 71), (44, 68), (49, 66.5), (54, 68.5), (60, 65.8), (64, 69), (69, 67), (74, 70),
             (78, 68.4), (84, 71)]
    p.poly([(30, 73)] + roofs + [(86, 73)])
    p.rect(51, 58, 4.4, 10)
    p.poly([(50.6, 58.2), (53.2, 50.5), (55.8, 58.2)])
    p.rect(53.05, 49.2, 0.3, 1.6)


def st_martin(rng):
    img = c.vgradient([(0, "#0A1230"), (.32, "#18224A"), (.55, "#2E3462"), (.66, "#4A4268"), (.70, "#3A3456")])
    c.radial(img, 150, 70, 80, [(0, "#B06A6A", .22), (.5, "#7A4A6A", .08), (1, "#7A4A6A", 0)], ry=16, mode="add")
    c.stars(img, sky_stars(rng, 22, 40, (0.15, 0.32)), "#E8ECFF", glow=0.08)
    # a thin crescent moon, right of the middle
    moon = c.shape(lambda p: p.circle(118, 18, 2.6), blur=0.08) * (1 - c.shape(lambda p: p.circle(119.3, 17.2, 2.4),
                                                                                blur=0.08))
    c.radial(img, 118, 18, 9, [(0, "#C9D4F0", .10), (1, "#C9D4F0", 0)], mode="add")
    c.blend(img, moon, "#E6EAF6")
    # the town far off, with a few lit windows
    c.blend(img, c.shape(town, blur=0.08), "#141630")
    lit = [(float(rng.uniform(32, 84)), float(rng.uniform(69, 72))) for _ in range(12)] + [(52.6, 60.5), (52.6, 63)]
    c.blend(img, c.shape(lambda p: [p.rect(x, y, 0.45, 0.6) for x, y in lit], blur=0.05) * 0.7, "#FFC870")
    # the ground the procession walks on
    ground = ridge(73.4, 0.7, 1)
    c.blend(img, under(ground), "#0A0C18")
    c.add(img, crest(ground, 0.8) * 0.10, "#9A7AA0")
    # the lanterns' light on the ground and on the children, before them
    for i, (x, ht) in enumerate(CHILDREN):
        tx, ty = lantern_tip(x, ht)
        ly = ty + LANTERN_STRING + LANTERN_R
        c.radial(img, tx, ly, 9, [(0, LANTERN_COLOURS[i % 3], .22), (.5, LANTERN_COLOURS[i % 3], .07),
                                  (1, LANTERN_COLOURS[i % 3], 0)], mode="add")
        c.radial(img, tx, MARTIN_GROUND - 0.5, 7, [(0, LANTERN_COLOURS[i % 3], .10), (1, LANTERN_COLOURS[i % 3], 0)],
                 ry=1.6, mode="add")
    c.blend(img, c.shape(lambda p: [child(p, i, x, ht) for i, (x, ht) in enumerate(CHILDREN)], blur=0.05), "#07080F")
    # the lanterns: string, a round paper body lit from inside, its ribs, rims at top and bottom
    for i, (x, ht) in enumerate(CHILDREN):
        tx, ty = lantern_tip(x, ht)
        ly, r = ty + LANTERN_STRING + LANTERN_R, LANTERN_R
        c.blend(img, c.shape(lambda p: line(p, [(tx, ty), (tx, ly - r)], 0.14), blur=0.03), "#2A2A38")
        body = c.shape(lambda p: p.circle(tx, ly, r), blur=0.05)
        shaded(img, body, (tx - r - 1, ly - r - 1, tx + r + 1, ly + r + 1), tx - r * 0.15, ly + r * 0.1, r * 1.1,
               [(0, LANTERN_LIGHT[i % 3]), (.55, LANTERN_COLOURS[i % 3]), (1, "#7A4A2A")])
        c.blend(img, c.shape(lambda p: [ring(p, tx, ly, r * f, r, 0.1) for f in (0.35, 0.72)], blur=0.03)
                * body * 0.35, "#5A3A1A")
        c.blend(img, c.shape(lambda p: (p.rect(tx - r * 0.55, ly - r - 0.35, r * 1.1, 0.55),
                                        p.rect(tx - r * 0.5, ly + r - 0.25, r * 1.0, 0.5)), blur=0.03), "#2A1A10")
    shade(img, 76, 100, 0.3)
    corners(img, 0.45)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# ---- nikolaus -----------------------------------------------------------------------------------------------------
WALL_X, WALL_BOTTOM = 96.0, 73.6
DOOR_X, DOOR_Y, DOOR_W = 119.0, 21.6, 30.7          # the mockup's .62 / .2 of the screen, .16 of the width
WREATH_X, WREATH_Y, WREATH_R = DOOR_X + DOOR_W / 2, 34.5, 4.8
NIK_LANTERN_X, NIK_LANTERN_Y = 165.0, 33.0          # the lantern by the door (OccasionMotionsB.Nikolaus flickers it)
MAT_TOP, BOOT_GROUND = 74.0, 75.5
BOOT_S = 192 * 0.03 * 1.3                          # the mockup's boot size, larger: its shaft is 2.1 times this high
BOOTS = (123.0, 140.0)


def boot_outline(bx):
    """A boot standing at bx with its toe to the right, the mockup's shape: shaft, instep curve, toe."""
    s, g = BOOT_S, BOOT_GROUND
    return ([(bx - 0.45 * s, g - 2.1 * s), (bx + 0.45 * s, g - 2.1 * s), (bx + 0.45 * s, g - 0.5 * s)]
            + bezier((bx + 0.45 * s, g - 0.5 * s), (bx + 1.4 * s, g - 0.45 * s), (bx + 1.3 * s, g), n=20)[1:]
            + [(bx - 0.45 * s, g)])


def boot(img, bx, filled):
    s, g = BOOT_S, BOOT_GROUND
    c.blend(img, c.shape(lambda p: p.ellipse(bx + 0.35 * s, g - 0.05, 1.15 * s, 0.6), blur=0.4) * 0.6, "#000000")
    mask = c.shape(lambda p: p.poly(boot_outline(bx)), blur=0.04)
    hshaded(img, mask, (bx - s, g - 2.3 * s, bx + 1.5 * s, g + 0.2), bx - 0.5 * s, bx + 1.3 * s,
            [(0, "#121212"), (.25, "#3E3E44"), (.45, "#2A2A2E"), (1, "#0E0E10")])
    # sole and heel, the cuff's seam, and the polish: a long light down the shaft and one on the toe
    c.blend(img, c.shape(lambda p: (p.rect(bx - 0.47 * s, g - 0.32, 1.75 * s, 0.32),
                                    p.rect(bx - 0.47 * s, g - 0.75, 0.55 * s, 0.75)), blur=0.03), "#050505")
    c.blend(img, c.shape(lambda p: p.rect(bx - 0.45 * s, g - 1.85 * s, 0.9 * s, 0.18), blur=0.03) * mask * 0.6,
            "#000000")
    c.add(img, c.shape(lambda p: line(p, [(bx - 0.24 * s, g - 1.95 * s), (bx - 0.24 * s, g - 0.65 * s)], 0.32),
                       blur=0.12) * mask * 0.5, "#E8ECF4")
    c.add(img, c.shape(lambda p: p.ellipse(bx + 0.8 * s, g - 0.36 * s, 0.22 * s, 0.06 * s), blur=0.2) * mask * 0.25,
          "#E8ECF4")
    # its opening: dark inside, a lit rim
    top = g - 2.1 * s
    c.blend(img, c.shape(lambda p: p.ellipse(bx, top, 0.45 * s, 0.13 * s), blur=0.03), "#5A5A62")
    c.blend(img, c.shape(lambda p: p.ellipse(bx, top + 0.04, 0.38 * s, 0.09 * s), blur=0.03), "#030303")
    if filled:
        treats(img, bx, top, BOOTS.index(bx))


def mandarin(img, x, y, r):
    m = c.shape(lambda p: p.circle(x, y, r), blur=0.04)
    shaded(img, m, (x - r - 0.5, y - r - 0.5, x + r + 0.5, y + r + 0.5), x - r * 0.35, y - r * 0.35, r * 1.5,
           [(0, "#FFC060"), (.45, "#F08A20"), (1, "#8A3A08")])
    c.blend(img, c.shape(lambda p: [p.circle(x + dx * r, y + dy * r, 0.08) for dx, dy in
                                    ((-.3, .1), (.2, .3), (.4, -.1), (-.1, .45), (.1, -.05))]) * 0.4, "#B05010")
    c.blend(img, c.shape(lambda p: p.circle(x + r * 0.1, y - r * 0.85, 0.22), blur=0.03), "#3A5A1A")
    c.blend(img, c.shape(lambda p: p.poly(turned_ellipse(x + r * 0.45, y - r * 0.95, r * 0.45, r * 0.18, -0.4)),
                         blur=0.03), "#3E7A2A")


def walnut(img, x, y, rx, ry):
    m = c.shape(lambda p: p.ellipse(x, y, rx, ry), blur=0.04)
    shaded(img, m, (x - rx - 0.5, y - ry - 0.5, x + rx + 0.5, y + ry + 0.5), x - rx * 0.3, y - ry * 0.4, rx * 1.4,
           [(0, "#C89A60"), (.5, "#8A6232"), (1, "#4A3016")])
    c.blend(img, c.shape(lambda p: (line(p, [(x, y - ry), (x - 0.1, y), (x, y + ry)], 0.12),
                                    line(p, [(x - rx * 0.6, y - ry * 0.3), (x - rx * 0.2, y + ry * 0.1)], 0.08),
                                    line(p, [(x + rx * 0.3, y - ry * 0.5), (x + rx * 0.6, y + ry * 0.2)], 0.08)),
                         blur=0.03) * m * 0.7, "#3A2410")


def candy_cane(img, x0, y0, x1, y1):
    """A candy cane standing from (x0, y0) up to (x1, y1), hooked over to the left, white with red stripes."""
    r = 0.75
    hook = [(x1 - r + np.cos(a) * r, y1 - np.sin(a) * r) for a in np.linspace(0, np.pi * 0.95, 14)]
    pts = [(x0, y0), (x1, y1)] + hook
    cane = c.shape(lambda p: line(p, pts, 0.62), blur=0.03)
    c.blend(img, cane, "#F2F0EC")
    c.blend(img, c.shape(lambda p: [line(p, [(x0 - 2 + k * 0.55, y0 + 1), (x0 + k * 0.55, y0 - 9)], 0.2)
                                    for k in range(12)], blur=0.02) * cane * 0.9, "#D6312F")
    c.add(img, c.shape(lambda p: line(p, [(x0 - 0.15, y0), (x1 - 0.15, y1)], 0.15), blur=0.08) * cane * 0.3, "#FFFFFF")


def chocolate(img, x, y, wdt, ht, turn):
    """A bar of chocolate in red paper with gold foil at its top, standing tilted."""
    def pts(dx0, dy0, dx1, dy1):
        ca, sa = np.cos(turn), np.sin(turn)
        return [(x + dx * ca - dy * sa, y + dx * sa + dy * ca) for dx, dy in ((dx0, dy0), (dx1, dy0), (dx1, dy1),
                                                                               (dx0, dy1))]
    c.blend(img, c.shape(lambda p: p.poly(pts(-wdt / 2, -ht, wdt / 2, 0)), blur=0.03), "#B8202A")
    c.blend(img, c.shape(lambda p: p.poly(pts(-wdt / 2, -ht, wdt / 2, -ht * 0.72)), blur=0.03), "#D8B04A")
    c.add(img, c.shape(lambda p: p.poly(pts(-wdt / 2, -ht, -wdt * 0.2, -ht * 0.72)), blur=0.1) * 0.35, "#FFF0C0")
    c.blend(img, c.shape(lambda p: p.poly(pts(-wdt / 2, -ht * 0.55, wdt / 2, -ht * 0.47)), blur=0.03) * 0.8,
            "#F2E6C8")


def treats(img, bx, top, k):
    """What St. Nicholas left in the boot, peeking out over its rim (both boots differ)."""
    s = BOOT_S
    if k == 0:
        candy_cane(img, bx + 0.28 * s, top + 0.6, bx + 0.36 * s, top - 0.95 * s)
        walnut(img, bx + 0.2 * s, top - 0.22 * s, 0.27 * s, 0.22 * s)
        mandarin(img, bx - 0.18 * s, top - 0.28 * s, 0.36 * s)
    else:
        chocolate(img, bx + 0.05 * s, top + 0.4, 0.55 * s, 1.25 * s, 0.22)
        mandarin(img, bx + 0.22 * s, top - 0.25 * s, 0.34 * s)
        mandarin(img, bx - 0.24 * s, top - 0.2 * s, 0.3 * s)
        c.blend(img, c.shape(lambda p: p.circle(bx - 0.02 * s, top - 0.12 * s, 0.17 * s), blur=0.03), "#D8B04A")
        c.add(img, c.shape(lambda p: p.circle(bx - 0.06 * s, top - 0.16 * s, 0.08 * s), blur=0.1) * 0.4, "#FFF0C0")


def door_wreath(img, rng):
    segs = fir_needles(rng, WREATH_X, WREATH_Y, WREATH_R, WREATH_R, 220, inner=0.72, outer=1.18, length=1.3)
    c.blend(img, c.shape(lambda p: ring(p, WREATH_X, WREATH_Y, WREATH_R * 0.95, WREATH_R * 0.95, 1.6), blur=0.1),
            "#123A22")
    for k, colour in enumerate(("#1B4A2A", "#24603A", "#2F7445")):
        strokes(img, segs[k::3], 0.22, colour)
    berries = [(WREATH_X + np.cos(a) * WREATH_R * 0.95, WREATH_Y + np.sin(a) * WREATH_R * 0.95)
               for a in np.linspace(0.4, 0.4 + TAU, 11, endpoint=False)]
    c.blend(img, c.shape(lambda p: [p.circle(x, y, 0.42) for x, y in berries], blur=0.04), "#C8202A")
    # a red bow at the bottom with two tails
    bx, by = WREATH_X, WREATH_Y + WREATH_R
    c.blend(img, c.shape(lambda p: (p.poly(turned_ellipse(bx - 1.3, by - 0.2, 1.4, 0.75, -0.3)),
                                    p.poly(turned_ellipse(bx + 1.3, by - 0.2, 1.4, 0.75, 0.3)),
                                    p.poly([(bx - 0.4, by), (bx - 2.0, by + 3.6), (bx - 1.1, by + 3.2),
                                            (bx - 0.6, by + 3.9), (bx, by)]),
                                    p.poly([(bx + 0.4, by), (bx + 2.0, by + 3.6), (bx + 1.1, by + 3.2),
                                            (bx + 0.6, by + 3.9), (bx, by)])), blur=0.04), "#B8202A")
    c.blend(img, c.shape(lambda p: p.circle(bx, by - 0.1, 0.6), blur=0.04), "#8A1018")


def wall_lantern(img):
    x, y = NIK_LANTERN_X, NIK_LANTERN_Y
    c.radial(img, x, y, 18, [(0, "#FFBE5A", .30), (.4, "#FFBE5A", .10), (1, "#FFBE5A", 0)], mode="add")
    c.blend(img, c.shape(lambda p: (line(p, [(x + 4.2, y - 6.6), (x + 4.2, y - 3.6)], 0.5),
                                    line(p, [(x + 4.2, y - 5.4), (x, y - 5.4), (x, y - 4.6)], 0.4)), blur=0.03),
            "#141414")
    c.blend(img, c.shape(lambda p: (p.poly([(x - 3.0, y - 3.4), (x, y - 5.0), (x + 3.0, y - 3.4)]),
                                    p.rect(x - 2.4, y - 3.5, 4.8, 8.4), p.rect(x - 2.8, y + 4.7, 5.6, 0.9)),
                         blur=0.03), "#141414")
    glass = c.shape(lambda p: p.rect(x - 1.8, y - 2.8, 3.6, 7.2), blur=0.03)
    shaded(img, glass, (x - 2, y - 3, x + 2, y + 5), x, y + 1.6, 4.0, [(0, "#FFF0B0"), (.5, "#FFC460"), (1, "#C07A28")])
    c.blend(img, c.shape(lambda p: (p.rect(x - 0.15, y - 2.8, 0.3, 7.2), p.rect(x - 1.8, y + 0.6, 3.6, 0.3)),
                         blur=0.02), "#141414")
    # snow on its roof
    c.blend(img, c.shape(lambda p: p.ellipse(x, y - 4.2, 2.6, 0.5), blur=0.08) * 0.8, "#AAB6CA")


def nikolaus_scene(rng, filled):
    img = c.vgradient([(0, "#070C1C"), (.4, "#0E1630"), (.68, "#1A2442")])
    c.stars(img, [s for s in sky_stars(rng, 26, 60, (0.15, 0.34)) if s[0] < WALL_X - 1], "#E8EEFF", glow=0.08)
    # a snowy fir in the garden on the left of the house
    garden = [(84.0, 75.0, 22.0), (77.0, 75.0, 14.0)]
    trees = c.shape(lambda p: [fir(p, x, b, ht) for x, b, ht in garden], blur=0.05)
    c.blend(img, trees, "#0A1A1C")
    c.blend(img, c.shape(lambda p: [fir_snow(p, x, b, ht) for x, b, ht in garden], blur=0.08) * trees * 0.5,
            "#8A98B4")
    # the house wall with its siding, shaded towards its corner on the left
    wall = c.shape(lambda p: p.rect(WALL_X, 0, 192 - WALL_X, WALL_BOTTOM))
    c.blend(img, wall, c.vgradient([(0, "#24140F"), (.4, "#3A2420"), (.68, "#40281F")]))
    siding = c.shape(lambda p: [p.rect(WALL_X, y, 192 - WALL_X, 0.16) for y in np.arange(4.3, WALL_BOTTOM, 3.78)],
                     blur=0.04)
    c.blend(img, siding * wall * 0.3, "#000000")
    c.blend(img, c.shape(lambda p: p.rect(WALL_X, 0, 4, WALL_BOTTOM), blur=1.5) * wall * 0.5, "#000000")
    c.blend(img, c.shape(lambda p: p.rect(WALL_X - 0.6, 0, 1.2, WALL_BOTTOM), blur=0.05), "#1A0E0A")
    # a small lit window left of the door
    c.blend(img, c.shape(lambda p: p.rect(100.5, 27, 12, 17), blur=0.05), "#1A0E0A")
    pane = c.shape(lambda p: p.rect(101.5, 28, 10, 15))
    c.blend(img, pane, c.vgradient([(0, "#6A4A28"), (.4, "#8A6230"), (.42, "#7A5428")]))
    c.blend(img, c.shape(lambda p: (p.rect(106.2, 28, 0.6, 15), p.rect(101.5, 35.2, 10, 0.6)), blur=0.03), "#2A160C")
    c.blend(img, c.shape(lambda p: p.rect(99.8, 44, 13.4, 1.0), blur=0.05), "#B4BED0")
    # the door: its frame, the leaf with two panels, a knob and a letter slot
    dx, dy, dw = DOOR_X, DOOR_Y, DOOR_W
    c.blend(img, c.shape(lambda p: p.rect(dx - 1.4, dy - 1.4, dw + 2.8, WALL_BOTTOM - dy + 1.4), blur=0.04), "#1E0E08")
    leaf = c.shape(lambda p: p.rect(dx, dy, dw, WALL_BOTTOM - dy), blur=0.03)
    hshaded(img, leaf, (dx, dy, dx + dw, WALL_BOTTOM), dx, dx + dw, [(0, "#4A2214"), (.4, "#62301C"), (1, "#3E1C10")])
    for py, ph in ((dy + 4.0, 19.5), (dy + 27.5, 20.0)):
        c.blend(img, c.shape(lambda p: frame(p, dx + 3.0, py, dw - 6.0, ph, 0.5), blur=0.05) * 0.8, "#2A120A")
        c.add(img, c.shape(lambda p: p.rect(dx + 3.6, py + 0.6, dw - 7.2, 0.3), blur=0.1) * 0.12, "#FFC890")
    c.blend(img, c.shape(lambda p: p.rect(dx + 9.5, dy + 25.2, dw - 19, 1.3), blur=0.03), "#B08A3A")
    c.blend(img, c.shape(lambda p: p.circle(dx + dw * 0.86, dy + 28.0, 0.95), blur=0.04), "#D8B04A")
    c.add(img, c.shape(lambda p: p.circle(dx + dw * 0.86 - 0.3, dy + 27.7, 0.35), blur=0.1) * 0.5, "#FFF0C0")
    door_wreath(img, rng)
    wall_lantern(img)
    # the snowy ground, the doorstep and the mat (the snow kept dim, OLED rule)
    snow = under(ridge(73.2, 0.6, 2))
    fill(img, snow, "#4E5C7A", "#161C2C", 73, 94)
    c.add(img, crest(ridge(73.2, 0.6, 2), 0.8) * 0.16, "#C8D4EA")
    c.blend(img, c.shape(lambda p: p.rect(dx - 5, WALL_BOTTOM - 0.2, dw + 10, 2.4), blur=0.05), "#4A4C56")
    c.blend(img, c.shape(lambda p: p.rect(dx - 5, WALL_BOTTOM - 0.2, dw + 10, 0.45), blur=0.06), "#9AA6BA")
    c.blend(img, c.shape(lambda p: p.rect(dx + 1.5, MAT_TOP, dw - 3, BOOT_GROUND - MAT_TOP + 0.4), blur=0.04),
            "#7A5630")
    c.blend(img, c.shape(lambda p: [p.rect(dx + 2 + k * 0.7, MAT_TOP + 0.2, 0.18, 1.1) for k in range(38)], blur=0.02)
            * 0.45, "#4A3018")
    for bx in BOOTS:
        boot(img, bx, filled)
    if filled:
        # and two walnuts on the step beside the boots
        walnut(img, 151.6, BOOT_GROUND - 0.9, 1.1, 0.85)
        walnut(img, 153.9, BOOT_GROUND - 0.75, 0.95, 0.75)
    shade(img, 76, 100, 0.25)
    corners(img, 0.45)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


def nikolaus_eve(rng):
    return nikolaus_scene(rng, filled=False)


def nikolaus(rng):
    return nikolaus_scene(rng, filled=True)


# ---- advent -------------------------------------------------------------------------------------------------------
ADV_WIN_X, ADV_WIN_Y, ADV_WIN_W, ADV_WIN_H = 107.5, 10.8, 57.6, 43.2   # the mockup's .56 / .1, .3 x .4
PAPER_STAR = (ADV_WIN_X + ADV_WIN_W * 0.72, ADV_WIN_Y + ADV_WIN_H * 0.3)
ADV_TABLE = 64.0
WREATH = (130.0, 68.6, 23.0, 5.8)                                    # centre, half axes (the mockup's .68 / .67)
# The four candles on the wreath's back half (the mockup's (-.6, -.4), (-.2, -.75), (.2, -.75), (.6, -.4) of its
# half axes, y halved): x and the y of their foot (units), CANDLE_H high, CANDLE_W wide. Their flames
# (OccasionMotionsB.Advent) burn FLAME_UP above their tops; they are lit in this order, one more each Sunday.
CANDLES = [(116.2, 67.4), (125.4, 66.4), (134.6, 66.4), (143.8, 67.4)]
CANDLE_H, CANDLE_W, FLAME_UP = 16.0, 3.4, 2.6
# The string of lights along the top, right of the header: its line and the bulbs on it (OccasionMotionsB twinkles
# them), at x LIGHTS_FROM + k * LIGHTS_STEP.
LIGHTS_FROM, LIGHTS_STEP, LIGHTS_N = 82.0, 7.6, 15
LIGHT_COLOURS = ("#FFB46E", "#FFCD6E", "#FFE68C")


def lights_y(x):
    """The string of lights' sag at x (units): the mockup's h * .05 + sin(x / w * TAU * 3) * h * .02."""
    return 5.4 + np.sin(x / 192 * TAU * 3) * 2.16


def advent(rng):
    img = c.vgradient([(0, "#140C08"), (.3, "#1E130C"), (.59, "#2A1A10")])
    stripes = (0.88 + 0.12 * np.sin(XS * 3.0) ** 2).astype(np.float32)
    img *= stripes[None, :, None]
    c.radial(img, 130, 60, 70, [(0, "#FFAA5A", .14), (.5, "#FFAA5A", .05), (1, "#FFAA5A", 0)], ry=40, mode="add")
    # the window: a snowy night outside, roofs under snow, a lit window far off, snow heaped on the sill
    wx, wy, ww, wh = ADV_WIN_X, ADV_WIN_Y, ADV_WIN_W, ADV_WIN_H
    pane = c.shape(lambda p: p.rect(wx, wy, ww, wh))
    c.blend(img, pane, c.vgradient([(0, "#0A1430"), (.25, "#1A3058"), (.5, "#22386A")]))
    c.stars(img, [(x, y, r) for x, y, r in sky_stars(rng, 40, 40, (0.12, 0.25))
                  if wx + 1 < x < wx + ww - 1 and wy + 1 < y < wy + wh - 14], "#E8EEFF")
    # houses across the street: (left, width, eaves, ridge), gabled, snow on their roofs, a few windows lit
    houses = [(106.0, 9.5, 46.0, 41.5), (115.0, 12.0, 44.5, 39.0), (126.5, 8.5, 47.0, 43.0), (134.5, 13.0, 45.0, 39.5),
              (147.0, 9.0, 46.5, 42.0), (155.5, 11.0, 45.0, 40.5)]

    def gable(x, wd, eave, top, over=0.7):
        return [(x - over, eave), (x + wd / 2, top), (x + wd + over, eave)]
    block = c.shape(lambda p: [(p.rect(x, eave, wd, 60 - eave), p.poly(gable(x, wd, eave, top)))
                               for x, wd, eave, top in houses], blur=0.06) * pane
    c.blend(img, block, "#0C1428")
    c.blend(img, c.shape(lambda p: [p.rect(x + wd * 0.7, top + 0.6, 1.2, 3.0) for x, wd, eave, top in houses[1::2]],
                         blur=0.05) * pane, "#0C1428")
    c.blend(img, c.shape(lambda p: [line(p, gable(x, wd, eave - 0.35, top - 0.35), 0.9) for x, wd, eave, top in houses],
                         blur=0.12) * pane * 0.8, "#8A9AB8")
    lit = [(x + wd * f, eave + 2.2) for x, wd, eave, top in houses for f in (0.22, 0.62)][::2] + [(141.5, 49.5)]
    c.blend(img, c.shape(lambda p: [p.rect(x, y, 1.2, 1.4) for x, y in lit], blur=0.05) * pane * 0.75, "#FFC870")
    c.blend(img, c.shape(lambda p: p.poly([(wx, wy + wh)] + [(wx + k * ww / 30, wy + wh - 2.2 - 0.7 * np.sin(k * 0.9))
                                                            for k in range(31)] + [(wx + ww, wy + wh)]), blur=0.2)
            * pane, "#8A98B4")
    # the frame and its cross
    c.blend(img, c.shape(lambda p: (p.rect(wx - 1.5, wy - 1.5, ww + 3, 3), p.rect(wx - 1.5, wy + wh - 1.5, ww + 3, 3),
                                    p.rect(wx - 1.5, wy - 1.5, 3, wh + 3), p.rect(wx + ww - 1.5, wy - 1.5, 3, wh + 3),
                                    p.rect(wx + ww / 2 - 0.75, wy, 1.5, wh), p.rect(wx, wy + wh / 2 - 0.75, ww, 1.5)),
                         blur=0.05), "#5A3A22")
    c.add(img, c.shape(lambda p: (p.rect(wx - 1.5, wy - 1.5, ww + 3, 0.4), p.rect(wx - 1.5, wy - 1.5, 0.4, wh + 3)),
                       blur=0.1) * 0.15, "#FFD8A0")
    c.blend(img, c.shape(lambda p: p.rect(wx - 3, wy + wh + 1.5, ww + 6, 1.8), blur=0.05), "#4A2E1A")
    # the paper star hanging in the window, glowing
    sx, sy = PAPER_STAR
    c.blend(img, c.shape(lambda p: line(p, [(sx, wy + 1.5), (sx, sy - 3.8)], 0.12), blur=0.02), "#2A1A10")
    c.radial(img, sx, sy, 11, [(0, "#FFF0C8", .40), (.4, "#FFE0A0", .12), (1, "#FFE0A0", 0)], mode="add")
    star = c.shape(lambda p: p.poly(star_points(sx, sy, 3.84, 1.34, 8, turn=0.12)), blur=0.04)
    shaded(img, star, (sx - 4, sy - 4, sx + 4, sy + 4), sx, sy, 3.9, [(0, "#FFFCEC"), (.5, "#FFE6A8"), (1, "#E8B060")])
    c.blend(img, c.shape(lambda p: [line(p, [(sx, sy), pt], 0.08) for pt in star_points(sx, sy, 3.6, 1.2, 8, 0.12)
                                    [::2]], blur=0.02) * star * 0.3, "#C08040")
    # the string of lights along the top, right of the header
    xs = np.linspace(LIGHTS_FROM - 4, 192, 80)
    sprigs = []
    for _ in range(420):
        x = float(rng.uniform(LIGHTS_FROM - 4, 192))
        y = float(lights_y(x)) + float(rng.uniform(-0.6, 0.8))
        a = float(rng.uniform(0, TAU))
        sprigs.append((x, y, x + np.cos(a) * 1.4, y + np.sin(a) * 0.9))
    strokes(img, sprigs[::2], 0.2, "#12301C")
    strokes(img, sprigs[1::2], 0.2, "#1C4428")
    c.blend(img, c.shape(lambda p: line(p, [(x, lights_y(x)) for x in xs], 0.12), blur=0.02), "#3A2616")
    bulbs = [(LIGHTS_FROM + k * LIGHTS_STEP, float(lights_y(LIGHTS_FROM + k * LIGHTS_STEP)) + 1.0)
             for k in range(LIGHTS_N)]
    c.blend(img, c.shape(lambda p: [p.rect(x - 0.3, y - 1.3, 0.6, 0.6) for x, y in bulbs], blur=0.02), "#2A1A10")
    for k, colour in enumerate(LIGHT_COLOURS):
        group = [b for i, b in enumerate(bulbs) if i % 3 == k]
        c.add(img, c.shape(lambda p: [p.circle(x, y, 1.6) for x, y in group], blur=1.2) * 0.25, colour)
        c.blend(img, c.shape(lambda p: [p.ellipse(x, y, 0.45, 0.65) for x, y in group], blur=0.04) * 0.85, colour)
    # the table, its grain and its lit edge
    table = c.vgradient([(0, "#6A4528"), (.3, "#523420"), (1, "#2A1A0E")], top=ADV_TABLE / 108, bottom=1.0)
    ty = int(ADV_TABLE * c.U)
    img[ty:] = table[ty:]
    c.blend(img, c.shape(lambda p: [line(p, [(x, ADV_TABLE + 0.6 + j * 2.2 + np.sin(x * 0.07 + j) * 0.3)
                                             for x in np.linspace(0, 192, 40)], 0.12) for j in range(6)], blur=0.1)
            * 0.25, "#2A180C")
    c.add(img, c.shape(lambda p: p.rect(0, ADV_TABLE, 192, 0.35), blur=0.1) * 0.2, "#FFC890")
    c.radial(img, 130, 68, 40, [(0, "#FFAA5A", .12), (1, "#FFAA5A", 0)], ry=9, mode="add")
    # the wreath: a dark base, fir all round (the back half first, the front after the candles), berries, cones
    cx, cy, rx, ry = WREATH
    c.blend(img, c.shape(lambda p: p.ellipse(cx + 1, cy + 1.6, rx * 1.12, ry * 1.25), blur=0.8) * 0.6, "#120A04")
    c.blend(img, c.shape(lambda p: ring(p, cx, cy, rx * 0.86, ry * 0.86, 3.4), blur=0.25), "#0E2A18")
    segs = fir_needles(rng, cx, cy, rx, ry, 1700, inner=0.62, outer=1.1, length=2.4)
    back = [s for s in segs if s[1] < cy - 0.5]
    front = [s for s in segs if s[1] >= cy - 0.5]
    for k, colour in enumerate(("#1B4A2A", "#24603A", "#2F7445")):
        strokes(img, back[k::3], 0.3, colour)
    # the candles, unlit: red, with a light down their left side, a cupped top and a black wick
    for x, foot in CANDLES:
        top = foot - CANDLE_H
        body = c.shape(lambda p: p.rect(x - CANDLE_W / 2, top, CANDLE_W, CANDLE_H), blur=0.04)
        hshaded(img, body, (x - CANDLE_W, top - 1, x + CANDLE_W, foot), x - CANDLE_W / 2, x + CANDLE_W / 2,
                [(0, "#8A1C1C"), (.25, "#D8403A"), (.55, "#B8282A"), (1, "#6A1214")])
        c.blend(img, c.shape(lambda p: p.ellipse(x, top, CANDLE_W / 2, 0.45), blur=0.03), "#E05048")
        c.blend(img, c.shape(lambda p: p.ellipse(x, top + 0.05, CANDLE_W / 2 - 0.35, 0.28), blur=0.03), "#8A1C1C")
        c.blend(img, c.shape(lambda p: line(p, [(x, top), (x + 0.1, top - 1.0)], 0.2), blur=0.02), "#141010")
    for k, colour in enumerate(("#1B4A2A", "#24603A", "#2F7445")):
        strokes(img, front[k::3], 0.3, colour)
    c.add(img, c.shape(lambda p: p.ellipse(cx - 4, cy - 1.5, rx * 0.8, ry * 0.5), blur=2.0) * 0.06, "#C8E0A0")
    berries = []
    for a in np.linspace(0.2, 0.2 + TAU, 16, endpoint=False):
        for dx, dy in ((0, 0), (0.55, 0.3), (-0.4, 0.4)):
            berries.append((cx + np.cos(a) * rx * 0.98 + dx, cy + np.sin(a) * ry * 0.98 + dy))
    c.blend(img, c.shape(lambda p: [p.circle(x, y, 0.5) for x, y in berries], blur=0.04), "#C8202A")
    c.add(img, c.shape(lambda p: [p.circle(x - 0.15, y - 0.15, 0.15) for x, y in berries], blur=0.05) * 0.5,
          "#FFC0B0")
    cones = [(cx + np.cos(a) * rx * 0.9, cy + np.sin(a) * ry * 0.9) for a in (0.9, 2.3, 3.9, 5.5)]
    c.blend(img, c.shape(lambda p: [p.ellipse(x, y, 1.2, 0.8) for x, y in cones], blur=0.05), "#5A3418")
    c.blend(img, c.shape(lambda p: [line(p, [(x - 1, y - 0.2 + k * 0.4), (x + 1, y - 0.2 + k * 0.4)], 0.1)
                                    for x, y in cones for k in range(2)], blur=0.02) * 0.6, "#2A160A")
    # ribbons: a bow on either side, its tails hanging down to the table
    for side in (-1, 1):
        bx, by = cx + side * rx * 0.97, cy + 0.6
        c.blend(img, c.shape(lambda p: (p.poly(turned_ellipse(bx - 1.4, by - 0.4, 1.5, 0.8, -0.35)),
                                        p.poly(turned_ellipse(bx + 1.4, by - 0.4, 1.5, 0.8, 0.35)),
                                        p.poly([(bx - 0.4, by), (bx - 1.6 + side * 0.4, by + 5.2),
                                                (bx - 0.8, by + 4.7), (bx - 0.2, by + 5.4), (bx + 0.1, by)]),
                                        p.poly([(bx + 0.4, by), (bx + 1.8 + side * 0.4, by + 4.6),
                                                (bx + 1.0, by + 4.3), (bx + 0.5, by + 4.9), (bx, by)])), blur=0.04),
                "#B8282E")
        c.add(img, c.shape(lambda p: (p.poly(turned_ellipse(bx - 1.6, by - 0.7, 0.8, 0.3, -0.35)),
                                      p.poly(turned_ellipse(bx + 1.2, by - 0.7, 0.8, 0.3, 0.35))), blur=0.15) * 0.3,
              "#FFB0A0")
        c.blend(img, c.shape(lambda p: p.circle(bx, by - 0.3, 0.6), blur=0.03), "#8A1820")
    # the plate of cookies on the right: stars, crescents and round ones with jam
    px, py = 173.0, 72.4
    c.blend(img, c.shape(lambda p: p.ellipse(px + 0.8, py + 1.0, 10.4, 2.9), blur=0.6) * 0.6, "#120A04")
    c.blend(img, c.shape(lambda p: p.ellipse(px, py, 9.6, 2.7), blur=0.04), "#D8D2C4")
    c.blend(img, c.shape(lambda p: p.ellipse(px, py - 0.1, 7.4, 1.9), blur=0.2) * 0.4, "#A8A294")
    cookies = [(-4.2, -0.3, "star", "#C88A4A"), (2.4, -0.9, "star", "#A86A30"), (-0.8, 0.4, "round", "#D8A060"),
               (4.8, 0.3, "crescent", "#E8C890"), (-6.4, 0.6, "crescent", "#E8C890"), (0.9, -1.6, "round", "#C89050")]
    for dx, dy, kind, colour in cookies:
        x, y = px + dx, py + dy
        if kind == "star":
            m = c.shape(lambda p: p.poly([(xx, y + (yy - y) * 0.55) for xx, yy in star_points(x, y, 2.1, 1.0, 5)]),
                        blur=0.04)
        elif kind == "round":
            m = c.shape(lambda p: p.ellipse(x, y, 1.6, 0.9), blur=0.04)
        else:
            m = c.shape(lambda p: line(p, [(x - 1.6 + k * 0.32, y - np.sin(k / 10 * np.pi) * 0.9) for k in range(11)],
                                       0.7), blur=0.04)
        c.blend(img, m, colour)
        c.blend(img, m * (1 - c.shape(lambda p: p.ellipse(x - 0.3, y - 0.3, 1.8, 0.9), blur=0.6)) * 0.4, "#5A3418")
        if kind == "round":
            c.blend(img, c.shape(lambda p: p.ellipse(x, y - 0.1, 0.55, 0.32), blur=0.04), "#A01828")
        if kind == "crescent":
            c.add(img, m * 0.15, "#FFFFFF")
    # left of the wreath: a sprig of fir, cinnamon sticks, star anise, a walnut and two baubles on the table
    sprig = [(91 + k * 0.9, 73.4 - k * 0.25) for k in range(10)]
    strokes(img, [(x, y, x + 1.2 * s, y - 1.0) for x, y in sprig for s in (-1, 1)], 0.26, "#24603A")
    c.blend(img, c.shape(lambda p: line(p, [sprig[0], sprig[-1]], 0.25), blur=0.03), "#3A2A16")
    for y in (74.4, 75.2):
        c.blend(img, c.shape(lambda p: line(p, [(93.5, y), (101.0, y - 0.9)], 0.62), blur=0.04), "#8A4A22")
        c.add(img, c.shape(lambda p: line(p, [(93.5, y - 0.2), (101.0, y - 1.1)], 0.15), blur=0.08) * 0.2, "#FFC890")
    for x, y in ((88.0, 75.0), (103.0, 72.6)):
        anise = c.shape(lambda p: p.poly([(xx, y + (yy - y) * 0.6) for xx, yy in star_points(x, y, 1.3, 0.45, 8)]),
                        blur=0.04)
        c.blend(img, anise, "#6A3A1C")
        c.blend(img, c.shape(lambda p: [p.circle(x + np.cos(a) * 0.75, y + np.sin(a) * 0.45, 0.16)
                                        for a in np.linspace(0, TAU, 8, endpoint=False)], blur=0.03) * 0.8, "#C8904A")
    for x, y, colour in ((83.5, 74.0, "#B8282E"), (89.0, 72.4, "#D8B04A")):
        ball = c.shape(lambda p: p.circle(x, y, 1.3), blur=0.04)
        shaded(img, ball, (x - 2, y - 2, x + 2, y + 2), x - 0.45, y - 0.45, 1.8,
               [(0, "#FFFFFF"), (.25, colour), (.8, colour), (1, "#1A1010")])
        c.blend(img, c.shape(lambda p: p.rect(x - 0.3, y - 1.7, 0.6, 0.5), blur=0.02), "#B8A060")
    shade(img, 74, 100, 0.25)
    corners(img, 0.45)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# ---- christmas ----------------------------------------------------------------------------------------------------
TREE_X, TREE_TOP, TREE_BASE = 138.0, 15.1, 70.2     # the mockup's .72 of the width, .14 and .65 of the height
TREE_H = TREE_BASE - TREE_TOP
TREE_SPREAD = 192 * 0.13                            # half the tree's width at its base
STAR_Y = TREE_TOP - 1.08
TREE_LIGHTS, LIGHTS_SEED = 30, 31                   # the mockup's lights, rng(31): OccasionMotionsB twinkles them
GIFT_GROUND = 75.6


def half_at(y):
    return (y - TREE_TOP) / TREE_H * TREE_SPREAD


def tree_lights():
    """The mockup's thirty lights from its rng(31), two values each: (x, y) in units on the tree."""
    r = lcg(LIGHTS_SEED)
    out = []
    for _ in range(TREE_LIGHTS):
        y = TREE_TOP + TREE_H * (0.1 + r() * 0.85)
        out.append((TREE_X + (r() * 2 - 1) * half_at(y) * 0.9, y))
    return out


def tree_tiers():
    """The mockup's four tiers: each a triangle from its tip down to a curved lower edge."""
    out = []
    for i in range(4):
        y0, hw = TREE_TOP + i * TREE_H * 0.2, 192 * (0.045 + i * 0.03)
        y1 = y0 + TREE_H * 0.4
        out.append([(TREE_X, y0), (TREE_X - hw, y1)] + bezier((TREE_X - hw, y1), (TREE_X, y1 + 108 * 0.03),
                                                              (TREE_X + hw, y1), n=24)[1:])
    return out


# The mockup's presents under the tree: x offset and size (fractions of the width and height), colour and ribbon;
# two more at the sides.
GIFTS = [(-.085, .045, .06, "#C8302F", "#FFD27A"), (-.035, .035, .045, "#2F6FC8", "#FFFFFF"),
         (.03, .05, .07, "#E8C06A", "#C8302F"), (.085, .032, .04, "#2D8A4E", "#FFD27A"),
         (-.122, .03, .035, "#8A3AA8", "#F2E6C8"), (.118, .028, .03, "#C8302F", "#F2F0EC")]


def christmas(rng):
    img = c.vgradient([(0, "#050B1A"), (.35, "#0A1630"), (.62, "#14223F")])
    c.stars(img, sky_stars(rng, 34, 48, (0.15, 0.34)), "#E8EEFF", glow=0.1)
    # snowy ground, a far snowy rise and small firs at its edges (the snow dim, OLED rule)
    far = ridge(66.5, 2.0, 1)
    fill(img, under(far), "#3A4A68", "#26324C", 62, 80)
    c.add(img, crest(far, 1.0) * 0.12, "#C8D4EA")
    small = [(86, 9), (92, 12), (97.5, 8), (178, 10), (184, 13), (189.5, 9)]
    c.blend(img, c.shape(lambda p: [p.poly([(x - ht * 0.38, float(far[int(x * c.U)]) + 1.5),
                                             (x, float(far[int(x * c.U)]) + 1.5 - ht),
                                             (x + ht * 0.38, float(far[int(x * c.U)]) + 1.5)]) for x, ht in small],
                         blur=0.05), "#0A1A22")
    near = ridge(71.6, 1.0, 3)
    fill(img, under(near), "#56658A", "#161E30", 70, 90)
    c.add(img, crest(near, 1.2) * 0.2, "#DCE6F6")
    # the tree's glow, the trunk and the tiers with their needles
    c.radial(img, TREE_X, 46, 44, [(0, "#FFC070", .10), (.6, "#FFC070", .03), (1, "#FFC070", 0)], ry=40, mode="add")
    c.blend(img, c.shape(lambda p: p.ellipse(TREE_X + 2, TREE_BASE + 3.4, 26, 2.2), blur=1.0) * 0.5, "#0A1020")
    c.blend(img, c.shape(lambda p: p.rect(TREE_X - 1.54, TREE_BASE - 1.1, 3.08, 4.6), blur=0.04), "#3A2416")
    tree = c.shape(lambda p: [p.poly(t) for t in tree_tiers()], blur=0.04)
    for t in tree_tiers():
        tier = c.shape(lambda p: p.poly(t), blur=0.04)
        hw = abs(t[1][0] - TREE_X)
        hshaded(img, tier, (TREE_X - hw - 1, t[0][1], TREE_X + hw + 1, t[1][1] + 4), TREE_X - hw, TREE_X + hw,
                [(0, "#0B2C1B"), (.45, "#1A5A35"), (.6, "#174E2E"), (1, "#0A2616")])
        c.blend(img, c.shape(lambda p: line(p, t[1:], 0.5), blur=0.3) * tier * 0.5, "#06180E")
    needles = []
    for _ in range(700):
        y = float(rng.uniform(TREE_TOP + 3, TREE_BASE))
        x = TREE_X + float(rng.uniform(-1, 1)) * half_at(y)
        a = float(rng.uniform(0.3, 1.2)) * (1 if x > TREE_X else -1)
        needles.append((x, y, x + np.sin(a) * 1.2, y + np.cos(a) * 0.9))
    for group, colour in ((needles[::2], "#2A7A48"), (needles[1::2], "#06200F")):
        c.blend(img, c.shape(lambda p: [line(p, [(x0, y0), (x1, y1)], 0.16) for x0, y0, x1, y1 in group], blur=0.03)
                * tree * 0.6, colour)
    c.blend(img, c.shape(lambda p: [line(p, t[1:], 0.35) for t in tree_tiers()], blur=0.2) * tree * 0.3, "#A8C8B0")
    # tinsel garlands, gold, sagging across the tree
    for k, f in enumerate((.3, .52, .74)):
        y = TREE_TOP + TREE_H * f
        hw = half_at(y) * 0.95
        pts = bezier((TREE_X - hw, y - 2.16), (TREE_X, y + 4.86), (TREE_X + hw, y + 1.08), n=60)
        c.blend(img, c.shape(lambda p: line(p, pts, 0.42), blur=0.04) * tree * 0.85, "#D8B050")
        c.add(img, c.shape(lambda p: [p.circle(x, yy, 0.22) for i, (x, yy) in enumerate(pts) if i % 3 == 0],
                           blur=0.05) * tree * 0.5, "#FFF0C0")
    # the mockup's twenty-two baubles from its rng(37), and ten more on the lower branches, round and shiny
    r = lcg(37)
    colours = ("#D6312F", "#E8C06A", "#CFD8E6", "#2F6FC8", "#C9308A")
    baubles = []
    for _ in range(22):
        y = TREE_TOP + TREE_H * (0.18 + r() * 0.78)
        baubles.append((TREE_X + (r() * 2 - 1) * half_at(y) * 0.85, y, 192 * (0.006 + r() * 0.003)))
    for _ in range(10):
        y = TREE_TOP + TREE_H * float(rng.uniform(0.55, 0.95))
        baubles.append((TREE_X + float(rng.uniform(-1, 1)) * half_at(y) * 0.85, y, 192 * float(rng.uniform(0.006, 0.009))))
    for i, (x, y, br) in enumerate(baubles):
        ball = c.shape(lambda p: p.circle(x, y, br), blur=0.04)
        shaded(img, ball, (x - br - 0.5, y - br - 0.5, x + br + 0.5, y + br + 0.5), x - br * 0.35, y - br * 0.35,
               br * 1.35, [(0, "#FFFFFF"), (.25, colours[i % 5]), (.8, colours[i % 5]), (1, "#1A1010")])
        c.blend(img, c.shape(lambda p: p.rect(x - 0.3, y - br - 0.4, 0.6, 0.5), blur=0.02), "#B8A060")
    # the lights, lit softly (the overlay makes them twinkle)
    pts = tree_lights()
    for k, colour in enumerate(("#FFC882", "#FFDA82", "#FFEC96")):
        group = [pt for i, pt in enumerate(pts) if i % 3 == k]
        c.add(img, c.shape(lambda p: [p.circle(x, y, 1.4) for x, y in group], blur=0.9) * 0.3, colour)
        c.blend(img, c.shape(lambda p: [p.circle(x, y, 0.42) for x, y in group], blur=0.05) * 0.9, colour)
    # the star on top
    c.radial(img, TREE_X, STAR_Y, 9.6, [(0, "#FFE696", .45), (.4, "#FFE696", .14), (1, "#FFE696", 0)], mode="add")
    star = c.shape(lambda p: p.poly(star_points(TREE_X, STAR_Y, 3.84, 1.54, 5)), blur=0.04)
    shaded(img, star, (TREE_X - 4, STAR_Y - 4, TREE_X + 4, STAR_Y + 4), TREE_X - 0.6, STAR_Y - 0.8, 4.2,
           [(0, "#FFFBE8"), (.5, "#FFE7A3"), (1, "#D8A848")])
    # the presents: box, a darker side, the lid's light, ribbons both ways and a bow
    for dx, bw, bh, colour, rib in GIFTS:
        x, wd, ht = TREE_X + 192 * dx - 192 * bw / 2, 192 * bw, 108 * bh
        y = GIFT_GROUND - ht
        c.blend(img, c.shape(lambda p: p.ellipse(x + wd / 2 + 0.6, GIFT_GROUND, wd * 0.6, 0.6), blur=0.4) * 0.6,
                "#060A14")
        c.blend(img, c.shape(lambda p: p.rect(x, y, wd, ht), blur=0.03), colour)
        c.blend(img, c.shape(lambda p: p.rect(x + wd * 0.62, y, wd * 0.38, ht), blur=0.6)
                * c.shape(lambda p: p.rect(x, y, wd, ht)) * 0.35, "#000000")
        c.add(img, c.shape(lambda p: p.rect(x, y, wd, 0.45), blur=0.1) * 0.18, "#FFFFFF")
        c.blend(img, c.shape(lambda p: (p.rect(x + wd / 2 - 0.58, y, 1.15, ht), p.rect(x, y + ht * 0.35, wd, 0.86)),
                             blur=0.03), rib)
        c.blend(img, c.shape(lambda p: (p.poly(turned_ellipse(x + wd / 2 - 1.2, y - 0.5, 1.2, 0.85, -0.5)),
                                        p.poly(turned_ellipse(x + wd / 2 + 1.2, y - 0.5, 1.2, 0.85, 0.5))),
                             blur=0.04), rib)
        c.blend(img, c.shape(lambda p: p.circle(x + wd / 2, y - 0.2, 0.5), blur=0.03) * 0.6, "#000000")
    shade(img, 76, 100, 0.3)
    corners(img, 0.45)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# Look.id: (painter(rng), shape seed, grain seed); one picture each, world_seasons_<id>.jpg
OCCASIONS = {
    "halloween": (halloween, 3107, 57),
    "st_martin": (st_martin, 3108, 58),
    "nikolaus_eve": (nikolaus_eve, 3109, 59),
    "nikolaus": (nikolaus, 3109, 60),
    "advent": (advent, 3110, 61),
    "christmas": (christmas, 3111, 62),
}
