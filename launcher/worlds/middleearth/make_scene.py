#!/usr/bin/env python3
"""Draws the Middle-earth world's three scenes from code alone (Pillow + NumPy, fixed seeds), spec § 4.1:

    world_middleearth_morning.jpg  "Hügelland" (green): hills with blades of grass, streaks of cloud, a patchwork of
                                   fields with hedges; on the right a round yellow door in the hillside with boards
                                   and a knob, round lit windows with glazing bars, a chimney; above them a great tree
                                   with bark and a shaded crown; a winding path with a fence and flowers
    world_middleearth_day.jpg      "Neblige Berge" (blue-grey): three ranges with rock strata and snowy peaks, two rows
                                   of conifers, gnarled old trees with bark in front; on the right a slim dark tower
                                   with storeys in the haze
    world_middleearth_night.jpg    "Dunkler Turm" (red): layers of ash in the sky, a volcano on the left with hatched
                                   flanks and three lava channels, a jagged black tower in stepped tiers with
                                   buttresses standing off it, glimmering windows and a crenellated crown with the
                                   eye set into its wall, on the right above the dock; an army of spears on rugged
                                   ground

    python launcher/worlds/middleearth/make_scene.py

Layered silhouettes with a film grain, in the spirit of a poster triptych without taking anything from one. All
shapes are this script's own (the mockup mittelerde-mockup.html, painters STATIC.shire/mountains/mordor, version 4,
in the 192 x 108 units of common.py): no film stills, no poster motifs, no faces, no logos, no inscription.

What moves is the overlay's (Task 4), painted here at rest so each picture is whole under reduced motion: the smoke
from the chimney and the morning mist, the mist in the valleys by day; at night the embers and sparks over the
crater, the falling ash, the eye on the tower (painted dim here) and its sweeping cone of light. The company walks on
the dock's top edge (Task 5) in each scene's darkest tone. Where the overlay draws over a painted thing it uses the
same numbers as here: they are named in capitals and mirrored in MiddleEarthScene.java
(tests/test_middleearth_scene.py keeps the two equal).

Picture rule (spec § 3, § 4.4): what matters stands right of the app name, directly above the dock (its top edge at
y 76.6 units); the logo area at the top in the middle (LOGO_FREE), the header on the top left (HEADER) and the pills
on the top right (PILLS) show only sky and clouds (check_free compares them with the bare sky); both top corners and
the bottom third stay dark (common.py), no large area is brighter than 60 % (OLED rule, check_bright), the night
darkest of all.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "seasons"))
import common as c  # noqa: E402
from occasions_a import line  # noqa: E402
from seasons import XS, YS, corners, crest, lcg, shade, under  # noqa: E402

TAU = 2 * np.pi

# ---- named numbers, in units (192 x 108); MiddleEarthScene.java holds the same ------------------------------------
DOCK = 76.6                              # the dock's top edge (y 766 of 1080): the company walks on it
PILLS = (128.0, 23.0, 192.0, 34.0)       # the pills' zone (PersonalMotions.PILLS): left, top, right, bottom
HEADER = (0.0, 0.0, 76.0, 34.0)          # greeting, clock, date and weather on the top left: left, top, right, bottom
                                         # (the crater's sparks stop below its bottom)
# The logo picture (spec § 4.4): centred at x 57 %, y 11 %, at most 18 % wide and 8 % high, as x, y, width, height;
# and the area kept free round it (x 48 to 66 %, y 4 to 18 %), sky and clouds only in all three scenes.
LOGO = (92.16, 7.56, 34.56, 8.64)
LOGO_FREE = (92.16, 4.32, 34.56, 15.12)
# Morning: the top of the chimney the smoke rises from, and the band (top, bottom) the morning mist drifts through.
CHIMNEY_TOP = (171.0, 45.6)
MIST_MORNING = (56.0, 71.0)
# Day: the band (top, bottom) the mist drifts through the valleys.
MIST_DAY = (57.0, 71.0)
# Night: the crater's rim (centre x, y, half width) the embers and sparks rise from, and the three lava channels down
# the flanks as polylines (the overlay lets them glow).
CRATER = (46.0, 40.0, 5.5)
LAVA = (
    ((44.0, 40.6), (42.6, 45.0), (40.4, 50.0), (37.0, 55.5), (33.6, 60.5), (29.4, 66.0)),
    ((48.2, 40.6), (50.0, 45.4), (53.4, 50.6), (56.4, 55.8), (60.6, 61.0), (64.4, 66.4)),
    ((46.0, 40.8), (45.6, 46.0), (46.8, 51.4), (45.8, 57.0), (47.0, 62.4)),
)
LAVA_W = 0.34                            # the channels' width in their middle; they taper to their ends
# Night: the dark tower's top (its crown's highest merlon), the eye set into the crown (centre, half width, half
# height; painted dim, the overlay lights it and lets it pulse) and the origin of its sweeping cone of light.
TOWER_TOP = (146.0, 35.0)
EYE = (146.0, 39.0, 2.9, 1.35)
CONE_ORIGIN = (146.0, 39.0)
# The darkest tone of each scene, the ground in front: the company is drawn in it (Task 5).
DARKEST_MORNING = "#141C10"
DARKEST_DAY = "#0E161C"
DARKEST_NIGHT = "#050101"

# Seeds: the shapes of each scene and its grain.
SEED = 1954
GRAIN_MORNING, GRAIN_DAY, GRAIN_NIGHT = 91, 92, 93
GRAIN_SIGMA = {"morning": 4.2, "day": 4.2, "night": 2.6}   # the film grain's strength at 1920 x 1080 (0-255)


# ---- helpers ------------------------------------------------------------------------------------------------------
class Layer:
    """An RGBA picture at the drawing size to paint many small coloured shapes on in units (grass, crowns, spears);
    put onto the picture with over()."""

    def __init__(self):
        self.im = Image.new("RGBA", (c.w, c.h), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    @staticmethod
    def _c(colour, alpha):
        s = colour.lstrip("#")
        return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (int(round(alpha * 255)),)

    def line(self, points, width, colour, alpha=1.0):
        self.d.line([(x * c.U, y * c.U) for x, y in points], fill=self._c(colour, alpha),
                    width=max(1, round(width * c.U)), joint="curve")

    def circle(self, cx, cy, r, colour, alpha=1.0):
        self.d.ellipse([(cx - r) * c.U, (cy - r) * c.U, (cx + r) * c.U, (cy + r) * c.U], fill=self._c(colour, alpha))

    def poly(self, points, colour, alpha=1.0):
        self.d.polygon([(x * c.U, y * c.U) for x, y in points], fill=self._c(colour, alpha))

    def rect(self, x, y, rw, rh, colour, alpha=1.0):
        self.d.rectangle([x * c.U, y * c.U, (x + rw) * c.U - 1, (y + rh) * c.U - 1], fill=self._c(colour, alpha))

    def over(self, img, mask=None, blur=0.0):
        """Paints the layer over img (in place), only where mask is (h x w, 0..1) if given."""
        im = self.im.filter(ImageFilter.GaussianBlur(blur * c.U)) if blur > 0 else self.im
        a = np.asarray(im, dtype=np.float32)
        alpha = a[:, :, 3] / 255.0
        if mask is not None:
            alpha = alpha * mask
        return c.blend(img, alpha, a[:, :, :3])


def column(nodes_x, nodes_y):
    """A line given at nodes, for every pixel column (units)."""
    return np.interp(XS, nodes_x, nodes_y).astype(np.float32)


def mountain_line(base, amp, seed, jag, top_limit=35.5, n=60):
    """The mockup's ridge(): base - amp (sin(i .21 + seed) .6 + sin(i .53 + 2 seed) .3 + (r - .5) jag) at 61 nodes
    across the width, joined straight (peaks stay sharp) and given for every pixel column; scaled down where it would
    rise above top_limit (the header, the logo area and the pills stay sky)."""
    r = lcg(seed)
    i = np.arange(n + 1)
    noise = np.array([r() for _ in i]) - 0.5
    k = np.sin(i * 0.21 + seed) * 0.6 + np.sin(i * 0.53 + seed * 2) * 0.3 + noise * jag
    y = base - k * amp
    top = column(i / n * 192, y)
    if top.min() < top_limit:
        top = base - (base - top) * (base - top_limit) / (base - top.min())
    return top.astype(np.float32)


def quad_line(p0, p1, p2):
    """A quadratic curve's y for every pixel column it spans (108 where it does not: no ground there)."""
    t = np.linspace(0, 1, 2000)
    x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
    y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
    out = np.interp(XS, x, y, left=108.0, right=float(y[-1]))
    return out.astype(np.float32)


def at(top, x):
    """A column line's y at x (units)."""
    return float(top[min(c.w - 1, max(0, int(x * c.U)))])


def bezier(p0, p1, p2, p3, n=40):
    t = np.linspace(0, 1, n)[:, None]
    pts = (1 - t) ** 3 * np.array(p0) + 3 * (1 - t) ** 2 * t * np.array(p1) + 3 * (1 - t) * t * t * np.array(p2) \
        + t ** 3 * np.array(p3)
    return [tuple(map(float, p)) for p in pts]


def hatch(mask, gap, slope, width, offset=0.0):
    """Diagonal hatching inside mask: lines x - slope y = k gap, width units wide (soft)."""
    u = (XS[None, :] - slope * YS[:, None] + offset) / gap
    d = np.abs(u - np.round(u)) * gap
    return (np.clip(1 - d / width, 0, 1) * mask).astype(np.float32)


def tapered(p, polylines, w0):
    """Polylines drawn widest in their middle, a hair at their ends."""
    for pts in polylines:
        n = len(pts) - 1
        for i in range(n):
            line(p, [pts[i], pts[i + 1]], w0 * (0.3 + 0.7 * np.sin(np.pi * (i + 0.5) / n)))


def crown(layer, x, y, rad, colours, rng, n=150, sx=1.15, sy=0.8, light_from=(-1, -1)):
    """A tree's crown as clumps of leaves: dark underneath and away from the light, mid and light on the lit side,
    the mockup's crown() with more clumps; the light ones last."""
    dark, mid, light = colours
    clumps = []
    for _ in range(n):
        a, d = rng.uniform(0, TAU), np.sqrt(rng.uniform()) * rad
        cx, cy = x + np.cos(a) * d * sx, y + np.sin(a) * d * sy
        rr = rad * rng.uniform(0.12, 0.26)
        lit = ((cx - x) * light_from[0] + (cy - y) * light_from[1]) / rad
        tone = 0 if cy > y + rad * 0.25 or lit < -0.35 else (2 if lit > 0.25 and rng.uniform() > 0.35 else 1)
        clumps.append((tone, cx, cy, rr))
    for tone in (0, 1, 2):
        for t, cx, cy, rr in clumps:
            if t == tone:
                layer.circle(cx, cy, rr, (dark, mid, light)[tone])


def finish(img, strength=1.0):
    """Darkens the bottom (towards the dock) and both top corners, as the UI over them needs (common.py)."""
    shade(img, 78, 104, 0.35)
    corners(img, 0.55 * strength)
    c.keep_dark(img, corners=0.6 * strength, bottom=0.6)
    return img


# ---- the morning: the hill country --------------------------------------------------------------------------------
HILL = ((57.6, 108.0), (111.4, 38.9), (201.6, 51.8))   # the big hill's quadratic curve (the mockup's)
TREE = (113.0, 36.0, 11.8)               # the great tree's crown: centre and radius; its trunk stands on the hill
DOOR = (150.0, 67.4, 4.5)                # the round door: centre and radius
WINDOWS = ((134.0, 66.6, 2.4), (166.0, 66.0, 2.4))


def soft_clouds(img, rng, n, y_range, colour, alpha, rx=(7, 20), ry=(0.6, 1.4)):
    """Long soft streaks of cloud."""
    blobs = [(float(rng.uniform(-10, 202)), float(rng.uniform(*y_range)), float(rng.uniform(*rx)),
              float(rng.uniform(*ry))) for _ in range(n)]
    body = c.shape(lambda p: [p.ellipse(x, y, a, b) for x, y, a, b in blobs], blur=0.7)
    c.blend(img, body * alpha, colour)
    return body


def fields(img, top, rng):
    """A patchwork of fields with hedges on the far slope, two rows following the ridge."""
    lay = Layer()
    hedges = []
    colours = ("#7A8C54", "#8A985C", "#6C8250", "#96985E", "#80915A", "#748A52")
    rows = ((0.8, 4.6), (4.6, 9.0), (9.0, 13.6))
    for row, (d0, d1) in enumerate(rows):
        x = -8.0 - row * 4.0
        k = row * 2
        while x < 200:
            wdt = float(rng.uniform(10.0, 17.0)) * (1 + row * 0.15)
            xa, xb = x, x + wdt
            # the field boundaries fan out from the middle, as on a slope seen from below
            sa, sb = (xa - 96) * 0.05, (xb - 96) * 0.05
            quad = [(xa, at(top, xa) + d0), (xb, at(top, xb) + d0), (xb + sb, at(top, xb + sb) + d1),
                    (xa + sa, at(top, xa + sa) + d1)]
            lay.poly(quad, colours[(k * 4 + row) % len(colours)])
            # furrows across some of them
            if rng.uniform() < 0.45:
                for t in np.linspace(0.2, 0.8, 4):
                    lay.line([(xa + sa * t, quad[0][1] + (quad[3][1] - quad[0][1]) * t),
                              (xb + sb * t, quad[1][1] + (quad[2][1] - quad[1][1]) * t)], 0.08, "#5E7044", 0.5)
            hedges.append([quad[0], quad[1]])
            hedges.append([quad[1], quad[2]])
            x, k = xb, k + 1
    lay.over(img, blur=0.05)
    # the hedges, dark and a little bumpy, with a bush or a small tree here and there
    bush = Layer()
    for (xa, ya), (xb, yb) in hedges:
        pts = [(xa + (xb - xa) * t, ya + (yb - ya) * t - 0.1 + 0.08 * np.sin(t * 17 + xa))
               for t in np.linspace(0, 1, 9)]
        bush.line(pts, 0.32, "#3C5030", 0.9)
        for t in np.linspace(0.1, 0.9, 5):
            if rng.uniform() < 0.10:
                bush.circle(xa + (xb - xa) * t, ya + (yb - ya) * t - 0.25, float(rng.uniform(0.3, 0.45)), "#34462A")
    bush.over(img, mask=under(top - 1.0), blur=0.04)


def grass(layer, rng, n, x_range, y_range, mask_top, length, colour, alpha, width=0.11):
    """Blades of grass: short bent strokes rising from below each other, only under mask_top."""
    for _ in range(n):
        x = float(rng.uniform(*x_range))
        y = float(rng.uniform(*y_range))
        if y < at(mask_top, x) + 0.3:
            continue
        ln = length * float(rng.uniform(0.5, 1.5))
        bend = float(rng.uniform(-0.5, 0.5)) * ln
        layer.line([(x, y), (x + bend * 0.35, y - ln * 0.6), (x + bend, y - ln)], width, colour, alpha)


def great_tree(img, hill, rng):
    cx, cy, rad = TREE
    foot = at(hill, cx) + 0.8
    fork = cy + rad * 0.55
    outline = [(cx - 4.6, foot), (cx - 2.8, foot - 1.6), (cx - 2.2, foot - 5.0), (cx - 2.0, fork + 2.0),
               (cx - 2.6, fork), (cx - 6.0, fork - 3.4), (cx - 5.4, fork - 4.0), (cx - 1.4, fork - 1.2),
               (cx - 0.6, fork - 5.0), (cx + 0.6, fork - 5.0), (cx + 1.2, fork - 1.4), (cx + 5.6, fork - 4.4),
               (cx + 6.2, fork - 3.6), (cx + 2.4, fork + 0.2), (cx + 2.0, fork + 2.0), (cx + 2.3, foot - 5.0),
               (cx + 3.0, foot - 1.4), (cx + 4.8, foot)]
    trunk = c.shape(lambda p: p.poly(outline), blur=0.05)
    c.blend(img, trunk, c.vgradient([(0, "#2A2014"), (1, "#3C2E1E")], top=cy / 108, bottom=foot / 108))
    # the light from the left on the trunk, bark lines down it
    lit = c.shape(lambda p: p.poly([(cx - 4.6, foot), (cx - 2.8, foot - 1.6), (cx - 2.2, foot - 5.0),
                                    (cx - 2.0, fork + 2.0), (cx - 1.0, fork + 2.0), (cx - 1.2, foot - 5.0),
                                    (cx - 2.6, foot)]), blur=0.3)
    c.add(img, lit * trunk * 0.20, "#C8B88A")
    bark = c.shape(lambda p: [line(p, [(cx + k * 0.6 + 0.3 * np.sin(k * 1.7 + j), foot - 0.6 - j * 1.6)
                                       for j in range(int((foot - fork) / 1.6))], 0.11)
                              for k in (-3, -2, -1, 0, 1, 2, 3)], blur=0.03)
    c.blend(img, bark * trunk * 0.55, "#140E08")
    c.blend(img, c.shape(lambda p: p.ellipse(cx + 0.6, fork + 5.5, 0.4, 0.7), blur=0.05) * 0.7, "#120C06")
    lay = Layer()
    crown(lay, cx, cy, rad, ("#30442A", "#4A6436", "#6E8A4A"), rng, n=230, sx=1.1, sy=0.82)
    lay.over(img, blur=0.06)
    # a soft shade under the crown on the hill
    c.blend(img, c.shape(lambda p: p.ellipse(cx + 1.5, foot + 0.6, 9.0, 1.2), blur=0.8) * under(hill) * 0.35,
            "#1E2A14")


def hill_house(img, hill, rng):
    """The round door with its frame, boards and knob, the round windows lit warm, the chimney."""
    dx, dy, dr = DOOR
    # the hillside round the door sinks in a little: a darker, earthy ring and the stone frame
    c.blend(img, c.shape(lambda p: p.circle(dx, dy, dr * 1.55), blur=0.6) * 0.45, "#24301A")
    c.blend(img, c.shape(lambda p: p.circle(dx, dy, dr * 1.25), blur=0.06), "#3A3626")
    c.blend(img, c.shape(lambda p: [line(p, [(dx + np.cos(a) * dr * 1.12, dy + np.sin(a) * dr * 1.12),
                                             (dx + np.cos(a) * dr * 1.24, dy + np.sin(a) * dr * 1.24)], 0.14)
                                    for a in np.linspace(np.pi, TAU, 12)], blur=0.03) * 0.6, "#1C1A10")
    door = c.shape(lambda p: p.circle(dx, dy, dr), blur=0.04)
    c.blend(img, door, c.vgradient([(0, "#C9A23A"), (1, "#9C7A24")], top=(dy - dr) / 108, bottom=(dy + dr) / 108))
    boards = c.shape(lambda p: [p.rect(dx + k * 1.15 - 0.06, dy - dr, 0.12, dr * 2) for k in (-3, -2, -1, 0, 1, 2, 3)],
                     blur=0.03)
    c.blend(img, boards * door * 0.55, "#5A4312")
    c.add(img, c.shape(lambda p: p.circle(dx - 1.2, dy - 1.4, dr * 0.75), blur=1.2) * door * 0.12, "#FFE8A0")
    c.blend(img, c.shape(lambda p: p.circle(dx, dy + 0.3, 0.5), blur=0.04), "#4A3008")
    c.add(img, c.shape(lambda p: p.circle(dx - 0.15, dy + 0.15, 0.2), blur=0.05) * 0.5, "#FFE6A0")
    # the doorstep
    c.blend(img, c.shape(lambda p: p.rect(dx - dr * 0.9, dy + dr - 0.1, dr * 1.8, 0.7), blur=0.05), "#4A4434")
    for wx, wy, wr in WINDOWS:
        c.radial(img, wx, wy, 6.0, [(0, "#FFC870", .16), (1, "#FFC870", 0)], ry=4.5, mode="add")
        c.blend(img, c.shape(lambda p: p.circle(wx, wy, wr * 1.28), blur=0.05), "#2E3420")
        glass = c.shape(lambda p: p.circle(wx, wy, wr), blur=0.04)
        c.blend(img, glass, c.rgb("#FFD27E") * 0.86)
        c.blend(img, c.shape(lambda p: [p.rect(wx - wr, wy - 0.12, wr * 2, 0.24),
                                        p.rect(wx - 0.12, wy - wr, 0.24, wr * 2)], blur=0.03) * glass, "#3A3420")
    # the chimney on the hill, its top where the smoke starts
    hx, hy = CHIMNEY_TOP
    foot = at(hill, hx) + 1.5
    c.blend(img, c.shape(lambda p: p.rect(hx - 1.2, hy, 2.4, foot - hy), blur=0.04), "#58503E")
    c.blend(img, c.shape(lambda p: [p.rect(hx - 1.2, hy + k * 1.25, 2.4, 0.12) for k in (1, 2, 3, 4)], blur=0.03)
            * 0.6, "#2E2A20")
    c.blend(img, c.shape(lambda p: p.rect(hx - 1.6, hy, 3.2, 0.8), blur=0.04), "#3A3428")


def path_and_fence(img, rng):
    """The winding path up to the door, a fence along it and flowers at its sides."""
    dx, dy, dr = DOOR
    pts = bezier((92.0, 78.5), (112.0, 72.6), (130.0, 75.6), (dx, dy + dr + 0.4), n=60)

    def path(p):
        n = len(pts) - 1
        for i in range(n):
            line(p, [pts[i], pts[i + 1]], 2.6 - 1.6 * i / n)
    c.blend(img, c.shape(path, blur=0.15), c.vgradient([(0, "#A8966C"), (1, "#8A7A56")], top=0.65, bottom=0.73))
    c.blend(img, c.shape(lambda p: [line(p, [(x - 0.4, y), (x + 0.4, y)], 0.12) for x, y in pts[4:-4:3]], blur=0.05)
            * 0.3, "#5A4C34")
    # the fence above the path, posts and two rails
    posts = [(x, y - 2.2 + 0.6 * i / len(pts)) for i, (x, y) in enumerate(pts) if i % 6 == 3 and x < dx - 7]
    fence = c.shape(lambda p: [[line(p, [(x, y + 0.9), (x, y - 2.0)], 0.32) for x, y in posts],
                               line(p, [(x, y - 1.6) for x, y in posts], 0.18),
                               line(p, [(x, y - 0.4) for x, y in posts], 0.18)], blur=0.03)
    c.blend(img, fence, "#4A3A24")
    lay = Layer()
    for _ in range(140):
        i = int(rng.integers(0, len(pts)))
        x, y = pts[i]
        side = 1 if rng.uniform() < 0.5 else -1
        fx, fy = x + float(rng.uniform(-1.5, 1.5)), y + side * float(rng.uniform(1.2, 2.6))
        if fy > DOCK - 0.6:
            continue
        colour = ("#E8D36A", "#D9708A", "#F0EEDC", "#E89A50")[int(rng.integers(0, 4))]
        lay.circle(fx, fy, float(rng.uniform(0.18, 0.32)), colour, 0.9)
    lay.over(img, blur=0.03)


def morning_scene(rng):
    img = c.vgradient([(0, "#2E3826"), (.16, "#5E6A4C"), (.30, "#868A68"), (.40, "#9C8E6A"), (.47, "#A08A64"),
                       (1, "#A08A64")])
    # the morning light low on the left, warm
    c.radial(img, 52, 49, 80, [(0, "#C89A58", .05), (1, "#C89A58", 0)], ry=14, mode="add")
    soft_clouds(img, rng, 18, (12, 40), "#A8AC92", 0.35)
    soft_clouds(img, rng, 10, (20, 44), "#6E785E", 0.18, rx=(10, 24), ry=(0.5, 1.0))
    sky = img.copy()
    far = column(XS, 50.2 + np.sin(XS / 192 * 5 + 3) * 1.4 + np.sin(XS / 192 * 13 + 3) * 0.5)
    c.blend(img, under(far), "#7A8662")
    near = column(XS, 54.6 + np.sin(XS / 192 * 5 + 5) * 2.0 + np.sin(XS / 192 * 13 + 5) * 0.6)
    c.blend(img, under(near), "#6E8052")
    # copses of trees along the far ridge, a layer between it and the fields
    lay = Layer()
    for _ in range(26):
        x = float(rng.uniform(-4, 196))
        for j in range(int(rng.integers(2, 6))):
            tx = x + j * float(rng.uniform(0.9, 1.6))
            crown(lay, tx, at(near, tx) - 0.9, float(rng.uniform(0.9, 1.6)), ("#4E6440", "#5C7048", "#6C7E52"), rng,
                  n=10)
    lay.over(img, blur=0.05)
    fields(img, near, rng)
    # the low hill on the left, where the app name stands, a hedge with trees along its top
    left = column(XS, 64.8 + np.sin(XS / 192 * 6 + 1) * 1.6 + np.sin(XS / 192 * 17 + 1) * 0.4)
    lay = Layer()
    for _ in range(18):
        tx = float(rng.uniform(-2, 100))
        crown(lay, tx, at(left, tx) - 1.4, float(rng.uniform(1.3, 2.4)), ("#2E4024", "#3C522C", "#4E6636"), rng, n=16)
    lay.line([(x, at(left, x) - 0.5) for x in np.linspace(-2, 132, 70)], 1.1, "#34482A")
    lay.over(img, blur=0.05)
    c.blend(img, under(left), c.vgradient([(0, "#46603A"), (1, "#22301A")], top=0.58, bottom=0.76))
    c.add(img, crest(left, 1.2) * 0.12, "#D8E0A8")
    hill = quad_line(*HILL)
    c.blend(img, under(hill), c.vgradient([(0, "#6C8848"), (1, "#30482A")], top=0.46, bottom=0.78))
    c.add(img, crest(hill, 1.8) * 0.18, "#E0E8B0")
    # the hill's far side in shade, away from the morning light on the left
    c.blend(img, under(hill) * np.clip((XS - 120) / 90, 0, 1)[None, :] * 0.35, "#1E2C16")
    lay = Layer()
    grass(lay, rng, 2600, (0, 192), (50, 78), np.minimum(hill, left), 0.85, "#283C18", 0.45)
    grass(lay, rng, 1100, (0, 192), (50, 78), np.minimum(hill, left), 0.7, "#C8D49A", 0.28)
    lay.over(img, blur=0.02)
    great_tree(img, hill, rng)
    path_and_fence(img, rng)
    hill_house(img, hill, rng)
    # the ground in front, on which the company walks, in the scene's darkest tone
    front = column(XS, DOCK - 0.5 + np.sin(XS / 192 * 4 + 2) * 0.3)
    c.blend(img, under(front), DARKEST_MORNING)
    return img, sky


# ---- the day: the misty mountains ---------------------------------------------------------------------------------
DAY_TOWER = (160.0, 36.2, 62.0)          # the slim tower in the haze: its x, its top (spire) and its foot
# The gnarled old trees in front, right of the app name: x, height, lean (a fraction of the height to the right).
GNARLED = ((104.0, 21.5, 0.10), (119.0, 18.5, -0.06), (135.0, 23.0, 0.04), (149.5, 16.0, 0.12),
           (172.0, 21.0, -0.08), (187.0, 19.0, 0.06))


def strata(img, mask, top, base, amp, rng, colour, alpha):
    """Rock strata: wavy bands across a range under its ridge, closer together near the peaks, and short steep
    gullies."""
    k = np.zeros((c.h, c.w), dtype=np.float32)
    ph = float(rng.uniform(0, TAU))
    for j in range(14):
        y0 = base - amp * (1.3 - j * 0.11)
        yl = y0 + np.sin(XS / 192 * 40 * 0.4 * 0.6 + j + ph) * amp * 0.08 + np.sin(XS / 7.0 + j * 2.1) * 0.35
        d = np.abs(YS[:, None] - yl[None, :])
        k = np.maximum(k, np.clip(1 - d / 0.16, 0, 1))
    gullies = hatch(mask, 3.1, -0.55, 0.12, offset=ph) * np.clip((YS[:, None] - top[None, :]) / 6, 0, 1)
    c.blend(img, np.maximum(k * 0.8, gullies * 0.6) * mask * alpha, colour)


def snow(img, top, base, amp, colour, rng):
    """Snow on the peaks: where a ridge rises high, down its slopes a little, with a ragged lower edge."""
    height = np.clip((base - top) / amp - 0.35, 0, None)          # how far above the range's middle
    depth = height * 3.4 + np.sin(XS * 1.9) * 0.35 + np.sin(XS * 4.3) * 0.2
    d = YS[:, None] - top[None, :]
    m = np.clip((depth[None, :] - d) * c.U * 0.25, 0, 1) * under(top)
    # streaks down the gullies
    streak = hatch(under(top), 1.4, -0.5, 0.18, offset=float(rng.uniform(0, 3))) * np.clip(
        1 - (d - depth[None, :]) / 2.4, 0, 1) * (height[None, :] > 0.05) * (d > 0)
    c.blend(img, np.maximum(m, streak * 0.6) * 0.92, colour)


def pines(layer, rng, n, base, size, colour, x_range=(0, 192)):
    """A row of conifers: narrow tiers on a short trunk, standing on base (a line or a number)."""
    for _ in range(n):
        x = float(rng.uniform(*x_range))
        b = at(base, x) if isinstance(base, np.ndarray) else base
        b += float(rng.uniform(-0.6, 0.6))
        hh = size * float(rng.uniform(0.6, 1.2))
        bw = hh * 0.26
        for t0, t1, s in ((0.0, 0.5, 1.0), (0.3, 0.78, 0.74), (0.58, 1.0, 0.48)):
            layer.poly([(x - bw * s, b - hh * t0 - hh * 0.06), (x, b - hh * t1), (x + bw * s, b - hh * t0 - hh * 0.06)],
                       colour)


def gnarled_tree(img, x, base, s, lean, rng):
    """An old gnarled tree: a twisting, leaning trunk with bark lines and a knot, long crooked branches reaching out
    and up with twigs, and sparse ragged clumps of leaves at their ends."""
    top = (x + lean * s, base - s)
    trunk = bezier((x, base), (x - 0.12 * s + lean * s * 0.2, base - s * 0.38),
                   (x + 0.16 * s + lean * s * 0.7, base - s * 0.66), top, n=40)

    def stem(p):
        n = len(trunk) - 1
        for i in range(n):
            line(p, [trunk[i], trunk[i + 1]], s * (0.13 - 0.085 * i / n))
        for d in (-1, 1):                     # roots
            line(p, [(x + d * 0.3, base - s * 0.1), (x + d * s * 0.09, base - s * 0.03),
                     (x + d * s * 0.16, base + 0.3)], s * 0.03)
    m = c.shape(stem, blur=0.04)
    c.blend(img, m, "#202E35")
    bark = c.shape(lambda p: [line(p, [(px + k * s * 0.016 * (1 - i / 44) + 0.12 * np.sin(i * 0.9 + k), py)
                                       for i, (px, py) in enumerate(trunk) if i % 2 == 0][:16], 0.06)
                              for k in (-2, -1, 0, 1, 2)], blur=0.02)
    c.blend(img, bark * m * 0.4, "#8EA4A8")
    kx, ky = trunk[11]
    c.blend(img, c.shape(lambda p: p.ellipse(kx + 0.15, ky, s * 0.018, s * 0.03), blur=0.05) * 0.7, "#101A1E")
    lay = Layer()
    tips = []
    for k in range(6):
        bx, by = trunk[min(16 + k * 4, 39)]
        d = (1 if k % 2 else -1) * (1 if rng.uniform() < 0.85 else -1)
        ln = s * float(rng.uniform(0.32, 0.5)) * (1 - k * 0.08)
        p1 = (bx + d * ln * 0.35, by - ln * 0.12 + float(rng.uniform(-0.3, 0.3)))
        p2 = (bx + d * ln * 0.7, by - ln * 0.38)
        p3 = (bx + d * ln, by - ln * 0.42 + float(rng.uniform(-0.6, 0.4)))
        lay.line([(bx, by), p1, p2, p3], max(0.16, s * (0.045 - k * 0.005)), "#202E35")
        lay.line([p1, (p1[0] + d * ln * 0.12, p1[1] - ln * 0.3), (p1[0] + d * ln * 0.2, p1[1] - ln * 0.36)], 0.14,
                 "#202E35")
        lay.line([p2, (p2[0] - d * ln * 0.05, p2[1] - ln * 0.25)], 0.12, "#202E35")
        tips += [p3, (p2[0] - d * ln * 0.05, p2[1] - ln * 0.25)]
    tips.append(top)
    lay.over(img, blur=0.03)
    cl = Layer()
    for tx, ty in tips:
        crown(cl, tx, ty - 0.3, s * float(rng.uniform(0.07, 0.11)), ("#1E2C33", "#2E424A", "#44585F"), rng, n=12,
              sx=1.4, sy=0.6)
    cl.over(img, blur=0.04)


def day_scene(rng):
    img = c.vgradient([(0, "#202C34"), (.16, "#44545C"), (.32, "#74848A"), (.46, "#889496"), (1, "#8A9698")])
    soft_clouds(img, rng, 14, (10, 34), "#9AA6A8", 0.28, rx=(10, 26), ry=(0.6, 1.3))
    sky = img.copy()
    # the far range, the tower in the haze before it, then the nearer ranges, each darker and less hazy
    r1 = mountain_line(50.0, 15.0, 11, 0.9)
    m1 = under(r1)
    c.blend(img, m1, c.vgradient([(0, "#6C7C84"), (1, "#7C8A90")], top=0.33, bottom=0.6))
    strata(img, m1, r1, 50.0, 15.0, rng, "#4A5A62", 0.22)
    snow(img, r1, 50.0, 15.0, "#B8C2C4", rng)
    c.blend(img, c.shape(lambda p: p.rect(0, 50, 192, 10), blur=3.0) * 0.3, "#8E9A9C")
    tx, ttop, tfoot = DAY_TOWER
    outline = [(tx - 3.0, tfoot), (tx - 2.1, ttop + 12), (tx - 2.7, ttop + 9.5), (tx - 2.0, ttop + 7.0),
               (tx - 1.3, ttop + 7.6), (tx - 1.1, ttop + 3.0), (tx - 0.4, ttop + 4.6), (tx, ttop),
               (tx + 0.4, ttop + 4.6), (tx + 1.1, ttop + 3.0), (tx + 1.3, ttop + 7.6), (tx + 2.0, ttop + 7.0),
               (tx + 2.7, ttop + 9.5), (tx + 2.1, ttop + 12), (tx + 3.0, tfoot)]
    tower = c.shape(lambda p: p.poly(outline), blur=0.05)
    c.blend(img, tower * 0.85, "#222E36")
    tiers = c.shape(lambda p: [p.rect(tx - 2.4 - (y - ttop) * 0.03, y, 4.8 + (y - ttop) * 0.06, 0.28)
                               for y in np.arange(ttop + 12.0, tfoot, 3.0)], blur=0.03)
    c.blend(img, tiers * 0.7, "#1A242A")
    c.add(img, c.shape(lambda p: p.rect(tx - 2.6, ttop + 12, 0.7, tfoot - ttop - 12), blur=0.3) * tower * 0.10,
          "#C8D4D6")
    c.blend(img, c.shape(lambda p: [p.rect(tx - 0.25, y + 1.0, 0.5, 0.9)
                                    for y in np.arange(ttop + 12.0, tfoot - 4, 3.0)], blur=0.03), "#10181C")
    # the haze over its foot
    c.blend(img, c.shape(lambda p: p.rect(0, 54, 192, 10), blur=3.0) * 0.3, "#8E9A9C")
    r2 = mountain_line(57.0, 10.0, 17, 0.9)
    r2 = r2 + 7.0 * np.exp(-((XS - tx) / 9.0) ** 2)          # a pass in front of the tower, so it shows whole
    m2 = under(r2)
    c.blend(img, m2, c.vgradient([(0, "#52646C"), (1, "#5E6E76")], top=0.45, bottom=0.66))
    strata(img, m2, r2, 57.0, 10.0, rng, "#34444C", 0.25)
    snow(img, r2, 57.0, 10.0, "#A8B4B8", rng)
    c.blend(img, c.shape(lambda p: p.rect(0, 59, 192, 8), blur=2.5) * 0.2, "#8E9A9C")
    r3 = mountain_line(63.0, 6.0, 23, 0.6, top_limit=52.0)
    r3 = r3 + 3.0 * np.exp(-((XS - tx) / 8.0) ** 2)
    m3 = under(r3)
    c.blend(img, m3, c.vgradient([(0, "#3C4E56"), (1, "#32424A")], top=0.52, bottom=0.68))
    strata(img, m3, r3, 63.0, 6.0, rng, "#24343A", 0.25)
    lay = Layer()
    pines(lay, rng, 150, 67.2, 5.6, "#30444C")
    lay.over(img, blur=0.03)
    slope = column(XS, 67.6 + np.sin(XS / 192 * 6 + 2) * 0.8)
    c.blend(img, under(slope), c.vgradient([(0, "#2C3E46"), (1, "#1C2A30")], top=0.62, bottom=0.72))
    lay = Layer()
    pines(lay, rng, 95, 72.6, 7.6, "#22343C", x_range=(-2, 100))
    lay.over(img, blur=0.03)
    front = column(XS, DOCK - 1.4 + np.sin(XS / 192 * 4 + 1) * 0.5)
    c.blend(img, under(front), "#16222A")
    for x, ht, lean in GNARLED:
        gnarled_tree(img, x, DOCK + 1.0, ht, lean, rng)
    lay = Layer()
    grass(lay, rng, 900, (0, 192), (DOCK - 1.4, DOCK + 3), front, 1.4, "#16222A", 0.9, width=0.13)
    lay.over(img, blur=0.02)
    c.blend(img, under(column(XS, DOCK - 0.3 + np.sin(XS / 192 * 5) * 0.2)), DARKEST_DAY)
    return img, sky


# ---- the night: the dark tower ------------------------------------------------------------------------------------
# The tower in stepped tiers, each a little narrower than the one below, from its foot on the dock: bottom, top,
# half width at the bottom and at the top (units). Every tier's flank carries jagged teeth, every ledge a row of
# them; on top sits a wider crown with stepped merlons whose highest one ends at TOWER_TOP, the eye set into its wall.
TOWER_TIERS = ((77.4, 66.0, 7.0, 6.2), (65.5, 56.5, 5.4, 4.8), (56.0, 48.5, 4.2, 3.7), (48.0, 42.6, 3.2, 2.9))
TOWER_CROWN = (41.0, 37.0, 4.4)          # the crown's wall: its corbelled bottom, its top, its half width
# The merlons on the crown from its left edge to the middle: left, right (offsets from TOWER_TOP's x) and top; the
# right half mirrors them, the middle one spans both.
MERLONS = ((-4.4, -3.5, 36.2), (-2.7, -1.8, 35.6), (-1.0, 1.0, 35.0))
# The buttresses, standing off the tower and joined to it by a strut (left side, mirrored): the pier's outer and inner
# foot x, its top (x, y), and the strut's end on the tower (x, y).
BUTTRESSES = ((-13.2, -8.8, (-8.6, 56.4), (-5.0, 61.0)),)


def tower_left():
    """The tower's left outline from its foot up to the middle of the crown (units, x as offsets from TOWER_TOP's
    x): jagged teeth up each tier's flank, a row of teeth on every ledge, the corbelled crown and its merlons."""
    pts = []
    for k, (yb, yt, hb, ht) in enumerate(TOWER_TIERS):
        pts.append((-hb, yb))
        n = max(1, int((yb - yt) / 3.2))
        for j in range(1, n + 1):
            y = yb - (yb - yt) * j / (n + 1)
            h = hb + (ht - hb) * j / (n + 1)
            pts += [(-h, y + 0.6), (-h - 1.5, y - 1.0), (-h - 0.9, y - 0.2), (-h, y - 0.4)]
        pts.append((-ht, yt))
        inner = TOWER_TIERS[k + 1][2] if k + 1 < len(TOWER_TIERS) else None
        if inner is not None:
            # the ledge: a lip sticking out, a row of teeth across it back to the next tier
            pts += [(-ht - 0.8, yt), (-ht - 0.8, yt - 0.5)]
            x = -ht - 0.8
            while x + 0.75 < -inner:
                pts += [(x + 0.15, yt - 1.6), (x + 0.4, yt - 0.5)]
                x += 0.6
            pts.append((-inner, yt - 0.5))
    bottom, top, half = TOWER_CROWN
    ht = TOWER_TIERS[-1][3]
    pts += [(-ht - 0.4, TOWER_TIERS[-1][1] - 0.4), (-half, bottom)]
    edge = -half
    for left, right, y in MERLONS:
        if left > edge:
            pts += [(edge, top), (left, top)]
        right = min(right, 0.0)
        pts += [(left, y), (right, y)]
        edge = right
    return pts


def tower_outline():
    """The whole outline: tower_left() and its mirror, placed at TOWER_TOP's x."""
    tx = TOWER_TOP[0]
    left = tower_left()
    return [(tx + dx, y) for dx, y in left] + [(tx - dx, y) for dx, y in reversed(left)]


def tower_half(y):
    """The tower's half width at row y (units), its tiers' flanks without the teeth."""
    ys, hs = [], []
    for yb, yt, hb, ht in TOWER_TIERS:
        ys += [yb, yt]
        hs += [hb, ht]
    return float(np.interp(y, ys[::-1], hs[::-1]))


def dark_tower(img, rng):
    tx = TOWER_TOP[0]
    body = c.shape(lambda p: p.poly(tower_outline()), blur=0.04)

    def piers(p):
        for ox, ix, (px, py), (sx, sy) in BUTTRESSES:
            for k in (1, -1):
                p.poly([(tx + k * ox, 77.4), (tx + k * ix, 77.4), (tx + k * (px + 1.0), py + 2.2),
                        (tx + k * (px + 0.3), py + 1.4), (tx + k * px, py), (tx + k * (px - 0.5), py + 1.2),
                        (tx + k * (px - 1.1), py + 2.0)])
                # a spike halfway up the pier, and the strut over to the tower
                mx, my = (ox + px) / 2, (77.4 + py) / 2
                p.poly([(tx + k * (mx - 0.1), my - 0.4), (tx + k * (mx - 1.3), my - 1.6),
                        (tx + k * (mx + 0.2), my + 0.6)])
                line(p, [(tx + k * px, py + 1.0), (tx + k * (px + sx) / 2, (py + sy) / 2 - 0.6), (tx + k * sx, sy)],
                     0.9)
    butt = c.shape(piers, blur=0.04)
    # the buttresses a little lighter than the tower, lit red from the volcano along their edges
    c.blend(img, butt, "#1E0907")
    c.add(img, np.clip(butt - np.roll(butt, int(0.3 * c.U), axis=1), 0, 1) * 0.35, "#A0341A")
    c.blend(img, body, "#070202")
    rim = np.clip(body - np.roll(body, int(0.3 * c.U), axis=1), 0, 1)
    c.add(img, rim * 0.28, "#8A2A14")
    # the storeys as dim lines at each tier's top
    floors = c.shape(lambda p: [p.rect(tx - tower_half(y) + 0.3, y + 0.6, 2 * tower_half(y) - 0.6, 0.14)
                                for _, y, _, _ in TOWER_TIERS[:-1]], blur=0.03)
    c.blend(img, floors * body * 0.45, "#5A1C0E")
    # glimmering windows: narrow slits in the tiers
    lay = Layer()
    for _ in range(24):
        y = float(rng.uniform(44.0, 75.0))
        half = tower_half(y)
        x = tx + float(rng.uniform(-0.7, 0.7)) * half
        lay.rect(x, y, 0.28, 0.7, "#FF9A3C", float(rng.uniform(0.5, 0.85)))
    glow = np.asarray(lay.im.filter(ImageFilter.GaussianBlur(0.6 * c.U)), dtype=np.float32)
    c.add(img, glow[:, :, 3] / 255.0 * 0.6, "#C84A14")
    lay.over(img, blur=0.02)
    # the eye at rest, set into the crown's wall: a dark recess, a dim fiery ring, a round dark core (the overlay
    # lights it and lets it pulse)
    ex, ey, rx, ry = EYE
    c.blend(img, c.shape(lambda p: p.ellipse(ex, ey, rx * 1.12, ry * 1.25), blur=0.06), "#020000")
    c.radial(img, ex, ey, rx * 1.6, [(0, "#E05A14", .20), (1, "#E05A14", 0)], ry=ry * 1.8, mode="add")
    c.blend(img, c.shape(lambda p: p.ellipse(ex, ey, rx, ry), blur=0.08) * 0.9, "#6A240A")
    c.blend(img, c.shape(lambda p: p.ellipse(ex, ey, rx * 0.66, ry * 0.62), blur=0.1) * 0.9, "#94380E")
    c.blend(img, c.shape(lambda p: p.circle(ex, ey, ry * 0.42), blur=0.06), "#160502")


def volcano_outline():
    """The volcano's outline: a ragged crater rim and concave flanks, steep at the top, running out to the plain."""
    cx, cy, half = CRATER
    r = np.random.default_rng(SEED + 7)
    left, right = [], []
    for t in np.linspace(0, 1, 26):
        prof = 1 - (1 - t) ** 1.7
        j = 0 if t in (0, 1) else float(r.uniform(-0.7, 0.7))
        left.append((cx - half - t * (cx - half + 12), cy + (75 - cy) * prof + j))
        j = 0 if t in (0, 1) else float(r.uniform(-0.7, 0.7))
        right.append((cx + half + t * (114 - cx - half), cy + (75 - cy) * prof + j))
    rim = [(cx - half * 0.55, cy + 0.6), (cx - half * 0.2, cy + 0.2), (cx + half * 0.15, cy + 0.7),
           (cx + half * 0.6, cy + 0.3)]
    return left[::-1] + rim + right


def volcano(img, rng):
    cx, cy, half = CRATER
    # the crater's glow over the rim, kept below the header
    c.radial(img, cx, cy, 12.0, [(0, "#FF5A14", .24), (.5, "#B02A0C", .07), (1, "#FF5A14", 0)], ry=4.6, mode="add")
    m = c.shape(lambda p: p.poly(volcano_outline()), blur=0.05)
    c.blend(img, m, c.vgradient([(0, "#2C0D08"), (1, "#140605")], top=cy / 108, bottom=72 / 108))
    # the flanks: the side away from the glow darker, hatched ridges down both
    away = np.clip((XS - cx) / 40, 0, 1)[None, :]
    c.blend(img, m * away * 0.45, "#0E0404")
    down = np.clip((YS[:, None] - cy) / 6, 0, 1)
    c.blend(img, hatch(m, 0.75, -0.62, 0.11) * down * (1 - away) * 0.7, "#0C0303")
    c.blend(img, hatch(m, 0.75, 0.62, 0.11, offset=0.3) * down * away * 0.7, "#0C0303")
    c.add(img, hatch(m, 2.3, -0.62, 0.09, offset=0.9) * down * (1 - away) * 0.18, "#C04A20")
    # the lava channels at rest (the overlay lets them glow and pulse, and adds the embers)
    c.add(img, c.shape(lambda p: tapered(p, LAVA, LAVA_W * 3.0), blur=1.0) * m * 0.40, "#E0400C")
    c.add(img, c.shape(lambda p: tapered(p, LAVA, LAVA_W * 1.6), blur=0.25) * m * 0.45, "#FF5A10")
    c.blend(img, c.shape(lambda p: tapered(p, LAVA, LAVA_W), blur=0.04) * m * 0.85, "#FF9A40")
    # the lit inner edge of the crater rim
    rim = volcano_outline()[25:31]
    c.add(img, c.shape(lambda p: line(p, rim, 0.25), blur=0.2) * 0.3, "#FF7A28")


def ash(img, rng):
    """Layers of ash across the sky: dark bands, some lit faintly red from below near the volcano."""
    blobs = [(float(rng.uniform(-10, 202)), float(rng.uniform(4, 40)), float(rng.uniform(10, 26)),
              float(rng.uniform(0.5, 1.5))) for _ in range(40)]
    body = c.shape(lambda p: [p.ellipse(x, y, a, b) for x, y, a, b in blobs], blur=0.9)
    c.blend(img, body * 0.55, "#120505")
    lit = np.clip(body - np.roll(body, int(-0.9 * c.U), axis=0), 0, 1)
    c.add(img, lit * np.clip(1 - np.abs(XS - CRATER[0]) / 90, 0, 1)[None, :] * 0.35, "#6A2010")


def army(img, rng):
    """An army far off on the plain before the volcano: a dark mass of shields and helmets and a forest of spears."""
    lay = Layer()
    for _ in range(700):
        x = float(rng.uniform(1, 122))
        lay.circle(x, 68.9 + float(rng.uniform(-0.25, 0.25)), float(rng.uniform(0.35, 0.6)), "#0E0404")
    for _ in range(300):
        x = float(rng.uniform(2, 120))
        y = 68.6 + float(rng.uniform(-0.3, 0.3))
        hh = float(rng.uniform(2.4, 4.4))
        lean = float(rng.uniform(-0.2, 0.2))
        lay.line([(x, y), (x + lean, y - hh)], 0.11, "#100404")
        lay.poly([(x + lean - 0.15, y - hh + 0.15), (x + lean, y - hh - 0.55), (x + lean + 0.15, y - hh + 0.15)],
                 "#100404")
    lay.over(img, blur=0.02)
    c.blend(img, under(column(XS, 69.1 + np.sin(XS * 2.7) * 0.12 + np.sin(XS * 1.1) * 0.15))
            * np.clip((123 - XS) / 3, 0, 1)[None, :], "#0E0404")


def night_scene(rng):
    img = c.vgradient([(0, "#070302"), (.2, "#140605"), (.4, "#2C0E08"), (.6, "#44170D"), (1, "#48190E")])
    ash(img, rng)
    sky = img.copy()
    volcano(img, rng)
    rocks = mountain_line(69.6, 2.4, 61, 1.6, top_limit=60.0)
    m = under(rocks)
    c.blend(img, m, c.vgradient([(0, "#260C08"), (1, "#140605")], top=0.6, bottom=0.72))
    c.add(img, hatch(m, 0.9, -0.4, 0.1) * crest(rocks, 3.0) * 0.20, "#FF5A28")
    army(img, rng)
    plain = column(XS, 71.2 + np.sin(XS / 192 * 7 + 1) * 0.6 + np.sin(XS * 0.9) * 0.25)
    c.blend(img, under(plain), "#1A0705")
    c.add(img, hatch(under(plain), 1.1, -0.4, 0.1) * np.clip((YS[:, None] - 71) / 6, 0, 1) * 0.18, "#FF5A28")
    c.add(img, crest(plain, 0.8) * 0.10, "#C04A20")
    dark_tower(img, rng)
    c.blend(img, under(column(XS, DOCK - 0.4 + np.sin(XS / 192 * 5 + 2) * 0.25)), DARKEST_NIGHT)
    return img, sky


# ---- checks and export --------------------------------------------------------------------------------------------
def box_px(box, ltrb=False):
    if ltrb:
        x0, y0, x1, y1 = box
    else:
        x0, y0, bw, bh = box
        x1, y1 = x0 + bw, y0 + bh
    return int(y0 * c.U), int(np.ceil(y1 * c.U)), int(x0 * c.U), int(np.ceil(x1 * c.U))


def check_free(img, sky, mood):
    """The logo area, the header and the pills show only the sky (and its clouds): nothing painted over it there."""
    ok = True
    for name, box, ltrb in (("logo area", LOGO_FREE, False), ("header", HEADER, True), ("pills", PILLS, True)):
        y0, y1, x0, x1 = box_px(box, ltrb)
        diff = float(np.abs(img[y0:y1, x0:x1] - sky[y0:y1, x0:x1]).max())
        if diff > 3:
            print(f"FAIL {mood}: something is painted in the {name} (differs from the sky by {diff:.0f})")
            ok = False
    return ok


def check_darkest(img, darkest, mood):
    """The scene's darkest tone (the company's colour) is no lighter than the darkest of what it walks before."""
    y0, y1 = int(40 * c.U), int(DOCK * c.U) - 4
    lo = float(np.percentile(c.luma(img[y0:y1]), 0.5))
    tone = float(c.luma(c.rgb(darkest)))
    if tone > lo + 1.0:
        print(f"FAIL {mood}: DARKEST {darkest} (luminance {tone:.1f}) is lighter than the scene ({lo:.1f})")
        return False
    return True


def check_bright(path, mood):
    """OLED rule: no large area brighter than 60 % (at most 0.5 % of the picture); at night none over 40 %."""
    y = c.luma(np.asarray(Image.open(path).convert("RGB")))
    limit = 0.4 if mood == "night" else 0.6
    share = float((y > 255 * limit).mean())
    if share > 0.005:
        print(f"FAIL {path.name}: {share * 100:.2f} % of the picture brighter than {limit * 100:.0f} %")
        return False
    print(f"     {path.name}: {share * 100:.2f} % brighter than {limit * 100:.0f} % (OLED rule, max 0.5 %)")
    return True


def export(img, mood, seed):
    """Scales the drawing down, adds a fine film grain (luminance noise, softly clumped, strongest in the middle
    tones, fixed seed) and saves world_middleearth_<mood>.jpg at the best quality within 300 KB; then checks it."""
    big = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
    small = np.asarray(big.resize((c.W, c.H), Image.LANCZOS), dtype=np.float32)
    rng = np.random.default_rng(seed)
    noise = rng.normal(0, 1, (c.H, c.W)).astype(np.float32)
    clump = np.asarray(Image.fromarray(((noise * 40) + 128).clip(0, 255).astype(np.uint8))
                       .filter(ImageFilter.GaussianBlur(0.55)), dtype=np.float32) - 128
    noise = clump / max(1e-6, float(clump.std()))
    lum = c.luma(small) / 255.0
    weight = 0.35 + 0.65 * np.sin(np.pi * np.clip(lum, 0, 1)) ** 0.7
    small = small + (noise * GRAIN_SIGMA[mood] * weight)[:, :, None]
    out = Image.fromarray(np.clip(small, 0, 255).astype(np.uint8), "RGB")
    c.OUT.mkdir(parents=True, exist_ok=True)
    path = c.OUT / f"world_middleearth_{mood}.jpg"
    for quality in range(90, 40, -3):
        out.save(path, "JPEG", quality=quality, optimize=True, progressive=True)
        if path.stat().st_size <= c.MAX_BYTES:
            break
    print(f"{path} (quality {quality})")
    return c.check(path) and check_bright(path, mood)


SCENES = (("morning", morning_scene, DARKEST_MORNING, GRAIN_MORNING, 1.0),
          ("day", day_scene, DARKEST_DAY, GRAIN_DAY, 1.0),
          ("night", night_scene, DARKEST_NIGHT, GRAIN_NIGHT, 0.6))


def main():
    """Draws the three pictures, or only those named (morning, day, night)."""
    want = [a for a in sys.argv[1:] if not a.startswith("-")] or [s[0] for s in SCENES]
    ok = True
    for k, (mood, paint, darkest, grain_seed, strength) in enumerate(SCENES):
        if mood not in want:
            continue
        img, sky = paint(np.random.default_rng(SEED + k))
        ok = check_darkest(img, darkest, mood) and ok
        finish(img, strength)
        finish(sky, strength)
        ok = check_free(img, sky, mood) and ok
        ok = export(img, mood, grain_seed) and ok
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
