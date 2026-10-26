"""The four seasons of Jahreskreis, each by day and by night (spec § 4.1), drawn from code alone:

    winter   snowy hills under a blue sky, a stand of firs on the right; by night a moon, stars and blue snow
    spring   hills in their first green, a cherry tree in blossom on the right; by night the same under a pale moon
    summer   the sea in the evening light, a low sun and its path on the water, a headland on the left;
             by night a moon over the sea and its silver path
    autumn   a forest of spires in amber and rust before a glowing sky; by night the same as black silhouettes

The shapes follow the mockup's painters (jahreskreis-mockup.html, PAINT.winter/spring/summer/autumn), whose
coordinates are fractions of the width and height, here in the 192 x 108 units of common.py. Hills are drawn per
column from the mockup's two sines, so they meet the right edge without a seam. What moves (snow, blossoms, fireflies
and the glitter on the water, leaves) is SeasonMotions.java's, not part of the pictures.

Picture rule (spec § 3): nothing that matters behind the dock (its top edge is at y 76.6 units) or in the header on
the top left; both top corners and the bottom third stay dark (common.py), and no large area is bright (OLED rule).
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import common as c  # noqa: E402

# Column and row centres in units, for shapes drawn per column (hills) or per row.
XS = ((np.arange(c.w, dtype=np.float32) + 0.5) / c.U)
YS = ((np.arange(c.h, dtype=np.float32) + 0.5) / c.U)
DOCK = 76.6                              # the dock's top edge in units (y 766 of 1080)


def sky_stars(rng, n, y_max, r_range=(0.18, 0.4)):
    """n stars between x 8 and 186 above y_max (units), none in the header on the top left (x < 76, y < 34)."""
    out = []
    while len(out) < n:
        x, y, r = c.random_stars(rng, 1, x_range=(8, 186), y_range=(4, y_max), r_range=r_range)[0]
        if not (x < 76 and y < 34):
            out.append((x, y, r))
    return out


def lcg(seed):
    """The mockup's seeded random (rng in jahreskreis-mockup.html), so a shape lands where it does there."""
    s = seed & 0xFFFFFFFF

    def r():
        nonlocal s
        s = (s * 1664525 + 1013904223) & 0xFFFFFFFF
        return s / 4294967296
    return r


def ridge(y, amp, ph):
    """The mockup's hill line: y + sin(x * 5 + ph) * amp + sin(x * 13 + ph) * amp * .3 with x as a fraction of the
    width, for every pixel column; y and amp in units."""
    fx = XS / 192
    return (y + np.sin(fx * 5 + ph) * amp + np.sin(fx * 13 + ph) * amp * 0.3).astype(np.float32)


def under(top):
    """The area below a line of column tops (units), with an anti-aliased edge."""
    return np.clip((YS[:, None] - top[None, :]) * c.U + 0.5, 0, 1).astype(np.float32)


def crest(top, depth):
    """A band just below a line, fading out over depth units: light on a hill's crest."""
    d = YS[:, None] - top[None, :]
    return np.where(d >= 0, np.exp(-np.maximum(d, 0) / depth), 0).astype(np.float32) * under(top)


def shade(img, start, end, floor):
    """Darkens downwards from row start to end (units), down to floor times the colour at the bottom."""
    k = np.clip((YS - start) / (end - start), 0, 1)
    v = (1 - (1 - floor) * k ** 1.3).astype(np.float32)
    img *= v[:, None, None]
    return img


def corners(img, strength, reach=0.34):
    """Darkens both top corners more than common.keep_dark: an ellipse of reach (fraction of width and height)."""
    fx = (XS / 192)[None, :]
    fy = (YS / 108)[:, None]
    v = np.ones((c.h, c.w), dtype=np.float32)
    for cx in (0.0, 1.0):
        d = np.sqrt(((fx - cx) / reach) ** 2 + (fy / reach) ** 2)
        v *= 1 - strength * np.exp(-d ** 2 * 1.8)
    img *= v[:, :, None]
    return img


def fill(img, alpha, top_colour, bottom_colour, top, bottom):
    """Paints alpha with a vertical gradient from top_colour at row top to bottom_colour at row bottom (units)."""
    return c.blend(img, alpha, c.vgradient([(0, top_colour), (1, bottom_colour)], top=top / 108, bottom=bottom / 108))


# ---- winter -------------------------------------------------------------------------------------------------------
def fir(p, x, base, height):
    """A fir as three stacked tiers and a short trunk; base is where it stands, in units."""
    wdt = height * 0.42
    for k, (t0, t1, s) in enumerate(((0.0, 0.45, 1.0), (0.3, 0.72, 0.78), (0.55, 1.0, 0.52))):
        y0, y1 = base - height * t0 - height * 0.08, base - height * t1
        p.poly([(x - wdt * s, y0), (x, y1), (x + wdt * s, y0)])
    p.rect(x - height * 0.035, base - height * 0.1, height * 0.07, height * 0.1)


def fir_snow(p, x, base, height):
    """Snow along each tier's upper left slope, where the light falls."""
    wdt = height * 0.42
    for t0, t1, s in ((0.0, 0.45, 1.0), (0.3, 0.72, 0.78), (0.55, 1.0, 0.52)):
        y0, y1 = base - height * t0 - height * 0.08, base - height * t1
        p.poly([(x - wdt * s, y0), (x, y1), (x - wdt * s * 0.35, y0 - (y0 - y1) * 0.25)])


FIRS = [(141, 8.5), (147, 12.5), (153.5, 10), (159, 15), (165.5, 11), (171, 13.5), (177, 9), (182.5, 11.5)]


def winter(day, rng):
    back, front = ridge(63.5, 3.0, 1), ridge(73.5, 3.6, 4)
    if day:
        img = c.vgradient([(0, "#0E1A36"), (.30, "#21385F"), (.52, "#45648F"), (.62, "#6A86AA")])
        # a pale low sun behind the hills, right of the middle
        c.radial(img, 120, 62, 60, [(0, "#C9D8EE", .20), (.45, "#9FB6D6", .08), (1, "#9FB6D6", 0)], ry=26,
                 mode="add")
    else:
        img = c.vgradient([(0, "#03060F"), (.45, "#08112A"), (.62, "#132447")])
        c.stars(img, sky_stars(rng, 40, 52, (0.18, 0.42)), "#DCE6FF",
                glow=0.15)
        c.radial(img, 150, 24, 16, [(0, "#C9D8EE", .14), (1, "#C9D8EE", 0)], mode="add")
        c.radial(img, 150, 24, 4.2, [(0, "#EEF3FF", .95), (.8, "#D5E0F5", .92), (1, "#D5E0F5", 0)])
    # the far hill, snow under the sky's light, with its lit crest
    hill = under(back)
    if day:
        fill(img, hill, "#8EA3C2", "#4E6384", 60, 80)
        c.add(img, crest(back, 1.6) * 0.18, "#E6EEFA")
    else:
        fill(img, hill, "#2C3D5E", "#141F36", 60, 80)
        c.add(img, crest(back, 1.4) * 0.10, "#9FB2D6")
    # the firs on its crest, right of the middle
    bases = [float(back[int(x * c.U)]) + 1.2 for x, _ in FIRS]
    trees = c.shape(lambda p: [fir(p, x, b, ht) for (x, ht), b in zip(FIRS, bases)], blur=0.06)
    c.blend(img, trees, "#16263A" if day else "#060B15")
    if day:
        snow = c.shape(lambda p: [fir_snow(p, x, b, ht) for (x, ht), b in zip(FIRS, bases)], blur=0.08)
        c.blend(img, snow * trees * 0.55, "#B7C7DE")
    # the near hill in front
    hill = under(front)
    if day:
        fill(img, hill, "#93A7C4", "#2A3854", 70, 92)
        c.add(img, crest(front, 2.0) * 0.16, "#F0F5FF")
    else:
        fill(img, hill, "#33466A", "#0E1628", 70, 100)
        c.add(img, crest(front, 1.6) * 0.08, "#AFC0E0")
    shade(img, 68, 96, 0.10)
    corners(img, 0.55 if day else 0.3)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# ---- spring -------------------------------------------------------------------------------------------------------
def cherry_trunk(p, base):
    """The cherry's trunk and main branches, standing on the far hill at x 160; base lies below its crest."""
    p.poly([(157.9, base), (158.6, 58), (157.2, 50), (158.6, 50), (160.4, 57), (162.4, 49), (163.6, 49.6),
            (161.6, 58), (162.1, base)])
    for (x0, y0), (x1, y1), wdt in (((158, 52), (149, 44), 0.7), ((162.5, 50), (172, 42), 0.7),
                                    ((158.2, 50.5), (154, 39), 0.55), ((162.8, 49.8), (166, 37), 0.55),
                                    ((150.5, 45.5), (145, 46), 0.4), ((170.5, 43.5), (176, 45), 0.4)):
        p.poly([(x0 - wdt, y0), (x1, y1 - wdt * 0.5), (x1, y1 + wdt * 0.5), (x0 + wdt, y0)])


CROWN_SEED = 321


def blossom_clumps(rng):
    """Clumps of blossom round the branch ends: (x, y, r) in units, a crown from x 141 to 179, y 31 to 55."""
    out = []
    for cx, cy, rx, ry, n in ((160, 42, 16, 9, 150), (149, 45, 6.5, 4.5, 40), (172, 43, 6.5, 4.5, 40),
                              (155, 37, 6, 4, 30), (166, 36, 6, 4, 30)):
        for _ in range(n):
            a, d = rng.uniform(0, 6.283), np.sqrt(rng.uniform(0, 1))
            out.append((float(cx + np.cos(a) * rx * d), float(cy + np.sin(a) * ry * d),
                        float(rng.uniform(0.7, 1.7))))
    return out


def spring(day, rng):
    back, mid, front = ridge(67, 4.3, 1), ridge(77.8, 5.4, 3), ridge(90.7, 4.3, 5)
    if day:
        img = c.vgradient([(0, "#1D3452"), (.25, "#3E6489"), (.48, "#668AA6"), (.62, "#71898F")])
        c.radial(img, 70, 60, 80, [(0, "#C8D6C8", .08), (1, "#C8D6C8", 0)], ry=30, mode="add")
    else:
        # painted dark as a night already; the scene shows it undimmed (WorldScene.dimFor)
        img = c.vgradient([(0, "#060B16"), (.45, "#13243A"), (.64, "#24394A")])
        c.stars(img, sky_stars(rng, 26, 44, (0.18, 0.38)), "#E8EEFF",
                glow=0.12)
        c.radial(img, 124, 22, 18, [(0, "#DDE6F0", .16), (1, "#DDE6F0", 0)], mode="add")
        c.radial(img, 124, 22, 4.4, [(0, "#F4F6FA", .95), (.8, "#DCE3EE", .92), (1, "#DCE3EE", 0)])
    # the cherry tree first, so the far hill's crest covers its foot
    base = float(back[int(160 * c.U)]) + 3
    c.blend(img, c.shape(lambda p: cherry_trunk(p, base), blur=0.05), "#3A2A26" if day else "#141012")
    clumps = blossom_clumps(np.random.default_rng(CROWN_SEED))   # the same crown by day and by night
    crown = c.shape(lambda p: [p.circle(x, y, r) for x, y, r in clumps], blur=0.15)
    light = c.shape(lambda p: [p.circle(x - r * 0.25, y - r * 0.3, r * 0.6) for x, y, r in clumps if y < 44],
                    blur=0.3)
    c.blend(img, crown, "#9A5F76" if day else "#3A2532")
    c.blend(img, light * crown * (0.6 if day else 0.5), "#C98AA2" if day else "#583A4A")
    sky_light = "#B9D3A6" if day else "#5E7A62"
    for top, (hi, lo), (y0, y1) in ((back, ("#76995F", "#557F48") if day else ("#2E4630", "#1C2E20"), (62, 82)),
                                    (mid, ("#5F8E4E", "#3A6532") if day else ("#223823", "#13221A"), (72, 92)),
                                    (front, ("#3F6E36", "#1E3A1C") if day else ("#16281A", "#0A140D"), (86, 108))):
        fill(img, under(top), hi, lo, y0, y1)
        c.add(img, crest(top, 1.2) * (0.14 if day else 0.07), sky_light)
    shade(img, 68, 96, 0.10)
    corners(img, 0.75 if day else 0.4)
    c.keep_dark(img, corners=0.6, bottom=0.7)
    return img


# ---- summer -------------------------------------------------------------------------------------------------------
HORIZON = 64.9                           # .601 of the height: the mockup's sea line
SUN_X, SUN_Y, SUN_R = 126.7, 63.2, 7.56  # .66 of the width, its disc just above the sea, radius .07 of the height


def headland(p):
    """A low headland on the left, falling into the sea; well below the header."""
    p.poly([(0, HORIZON + 0.2), (0, 57.5), (6, 56.2), (13, 57), (20, 58.4), (28, 60.6), (36, 62.6), (44, 64),
            (50, HORIZON + 0.2)])


def summer(day, rng):
    if day:
        img = c.vgradient([(0, "#16213E"), (.22, "#3A3656"), (.42, "#9A5545"), (.55, "#B9784A"), (.601, "#C08550")])
        c.radial(img, SUN_X, SUN_Y, 60, [(0, "#FFBE6E", .30), (.4, "#FFBE6E", .10), (1, "#FFBE6E", 0)], ry=36,
                 mode="add")
        sea = c.vgradient([(0, "#2A4A6A"), (.25, "#1B3552"), (1, "#081322")], top=HORIZON / 108, bottom=1.0)
    else:
        img = c.vgradient([(0, "#02040B"), (.40, "#070D1F"), (.601, "#16223C")])
        c.stars(img, sky_stars(rng, 36, 50), "#E8EEFF",
                glow=0.12)
        sea = c.vgradient([(0, "#0E1A30"), (.3, "#08111F"), (1, "#03070E")], top=HORIZON / 108, bottom=1.0)
    hy = int(HORIZON * c.U)
    img[hy:] = sea[hy:]
    if day:
        # the sun: a disc cut by the sea line, and its broad path of light on the water
        disc = c.shape(lambda p: p.circle(SUN_X, SUN_Y, SUN_R), blur=0.1)
        disc[hy:] = 0
        c.blend(img, disc, "#FFD38A")
        path_colour, path_alpha = "#FFC882", 0.22
    else:
        c.radial(img, SUN_X, 30, 16, [(0, "#DCE4F5", .14), (1, "#DCE4F5", 0)], mode="add")
        c.radial(img, SUN_X, 30, 4.0, [(0, "#F2F5FB", .95), (.8, "#D8DFEC", .92), (1, "#D8DFEC", 0)])
        path_colour, path_alpha = "#B9C8E6", 0.10
    # the still path of light on the water; the moving glitter over it is the overlay's
    rows = np.clip((YS - HORIZON) / (108 - HORIZON), 0, 1)
    half = 5 + rows * 26
    path = np.exp(-((XS[None, :] - SUN_X) / half[:, None]) ** 2) * (YS[:, None] > HORIZON)
    ripple = 0.55 + 0.45 * np.sin(YS * 9.0)[:, None] ** 2
    c.add(img, (path * ripple * path_alpha * (1 - rows[:, None]) ** 0.8).astype(np.float32), path_colour)
    # the headland on the left, and a soft line where sea meets sky
    c.blend(img, c.shape(headland, blur=0.08), "#1A1220" if day else "#04050A")
    c.add(img, c.shape(lambda p: p.rect(46, HORIZON - 0.1, 146, 0.2), blur=0.15) * (0.25 if day else 0.10),
          "#FFD8A0" if day else "#8FA0C0")
    shade(img, 70, 108, 0.30)
    corners(img, 0.55 if day else 0.3)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# ---- autumn -------------------------------------------------------------------------------------------------------
def spires(seed, n, scale=1.0, lift=0.0):
    """The mockup's forest: n spires from its seeded random, (x, half width, tip) in units, standing on the bottom.
    scale shrinks them and lift raises their tips (for a row further back)."""
    r = lcg(seed)
    out = []
    for _ in range(n):
        x, th, bw = r() * 192, 108 * (0.3 + r() * 0.25), 192 * (0.03 + r() * 0.03)
        out.append((x, bw * scale, 108 - (th + 27) * scale - lift))
    return out


LEAF_SEED = 923
# Broad-leaved trees in front of the forest, right of the middle where Home leaves room above the dock:
# x, the crown's centre height, its half width and half height, colour index (rust, amber, deep red).
LEAFY = [(104, 66, 6.5, 5.5, 0), (119, 63.5, 8, 6.5, 1), (135, 66.5, 6.5, 5.5, 2), (150, 62.5, 8.5, 7, 0),
         (167, 65.5, 7, 6, 1), (183, 63, 8, 6.5, 2)]


def leafy_blobs(rng):
    """The bumps along each crown's edge: (x, y, r, index into LEAFY)."""
    out = []
    for i, (x, y, rx, ry, _) in enumerate(LEAFY):
        for k in range(20):
            a = k / 20 * 6.283 + float(rng.uniform(-0.12, 0.12))
            out.append((x + float(np.cos(a)) * rx * 0.85, y + float(np.sin(a)) * ry * 0.85,
                        float(rng.uniform(1.3, 2.2)), i))
    return out


def autumn(day, rng):
    near, far = spires(11, 22), spires(29, 26, scale=0.72, lift=9)
    if day:
        img = c.vgradient([(0, "#24150D"), (.30, "#4E2C16"), (.52, "#9A5226"), (.62, "#A65A28"), (1, "#3A2010")])
        c.radial(img, 112, 58, 70, [(0, "#E39A4E", .30), (.5, "#C8742E", .10), (1, "#C8742E", 0)], ry=30,
                 mode="add")
        far_colour, near_colour = "#5E3018", "#20110A"
    else:
        img = c.vgradient([(0, "#040304"), (.40, "#0C0806"), (.58, "#26140B"), (1, "#0E0805")])
        c.stars(img, sky_stars(rng, 18, 19, (0.18, 0.38)), "#F2E6D8",
                glow=0.1)
        c.radial(img, 150, 18, 14, [(0, "#E8D6B8", .12), (1, "#E8D6B8", 0)], mode="add")
        c.radial(img, 150, 18, 3.6, [(0, "#F3E8D2", .92), (.8, "#E0CFAE", .9), (1, "#E0CFAE", 0)])
        far_colour, near_colour = "#160B06", "#050302"
    # a row of spires further back in the haze, then the mockup's forest in front
    c.blend(img, c.shape(lambda p: [p.poly([(x - b, 108), (x, t), (x + b, 108)]) for x, b, t in far], blur=0.1),
            far_colour)
    c.blend(img, c.shape(lambda p: [p.poly([(x - b, 108), (x, t), (x + b, 108)]) for x, b, t in near], blur=0.08),
            near_colour)
    # in front, broad-leaved trees in rust and amber on dark trunks, their crowns just above the dock
    c.blend(img, c.shape(lambda p: [p.poly([(x - 1.0, y), (x + 1.0, y), (x + 1.7, 108), (x - 1.7, 108)])
                                    for x, y, *_ in LEAFY], blur=0.06), near_colour)
    blobs = leafy_blobs(np.random.default_rng(LEAF_SEED))
    palette = ("#7E3416", "#9A5A1C", "#6A2212") if day else ("#22100A", "#2A170B", "#1C0B07")
    for i, (x, y, rx, ry, k) in enumerate(LEAFY):
        crown = c.shape(lambda p: (p.ellipse(x, y, rx, ry),
                                   [p.circle(bx, by, br) for bx, by, br, j in blobs if j == i]), blur=0.12)
        c.blend(img, crown, palette[k])
        lit = c.shape(lambda p: p.ellipse(x - rx * 0.3, y - ry * 0.35, rx * 0.75, ry * 0.6), blur=2.0)
        c.add(img, lit * crown * (0.22 if day else 0.05), "#F0A040")
    # the forest floor, the mockup's low hill at .86
    c.blend(img, under(ridge(92.9, 2.2, 2)), "#1A0D06" if day else "#040201")
    shade(img, 70, 108, 0.35)
    corners(img, 0.5 if day else 0.3)
    c.keep_dark(img, corners=0.5, bottom=0.7)
    return img


# name: (painter, shape seed, grain seed by day, grain seed by night)
SEASONS = {
    "winter": (winter, 2112, 41, 42),
    "spring": (spring, 2103, 43, 44),
    "summer": (summer, 2106, 45, 46),
    "autumn": (autumn, 2309, 47, 48),
}
