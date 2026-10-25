#!/usr/bin/env python3
"""Draws the Marvel world's two scenes from code alone (Pillow + NumPy, fixed seed):

    world_marvel_night.jpg  a skyline of sixteen blocks and one tall tower with a beacon, black against a crimson glow,
                            an anamorphic streak of light across at 46 % of the height, a grid of lit windows
    world_marvel_day.jpg    the same skyline against a low sun on the right, warm backlight and rimmed edges

    python launcher/worlds/marvel/make_scene.py

Shapes use the mockup's 192 x 108 units (vier-welten-v3.html). A generic city only: no logos, lettering or film
imagery. The reactor, the breathing streak and the pulsing beacon are the overlay's (drawn live).
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import common as c  # noqa: E402

BEACON = (113.6, 30.2)                    # 59 %, 28 %
# sixteen blocks (x, top, width), the tall tower apart; from the mockup, with one more slim block by the tower
BLOCKS = [(0, 74, 10), (12, 66, 8), (22, 78, 12), (36, 60, 9), (47, 70, 6), (55, 56, 11), (68, 72, 7), (77, 64, 12),
          (91, 76, 8), (100, 80, 3.4), (124, 68, 9), (135, 58, 12), (149, 74, 6), (157, 62, 10), (169, 70, 8),
          (179, 78, 13)]
# window panels (x, top, width, height), as in the mockup
PANELS = [(13, 68, 6, 30), (37, 62, 7, 36), (56, 58, 9, 40), (78, 66, 10, 32), (105, 54, 14, 44), (136, 60, 10, 38),
          (158, 64, 8, 34), (125, 70, 7, 28), (1, 76, 8, 16), (170, 72, 6, 20)]


def skyline(p):
    for x, top, bw in BLOCKS:
        p.rect(x, top, bw, 108 - top)
    # a few roof details: stepped crowns and masts
    p.rect(57, 53.6, 7, 2.6)
    p.rect(59.4, 49, 0.6, 5)
    p.rect(137, 55.6, 8, 2.6)
    p.rect(140.6, 51, 0.6, 5)
    p.rect(38, 58, 5, 2.2)
    p.rect(159, 60, 6, 2.2)
    p.rect(80, 62, 6, 2.2)
    # the tall tower: a tapering crown on a broad shaft, and its mast
    p.poly([(104, 108), (104, 52), (112, 40), (118, 40), (120, 52), (120, 108)])
    p.rect(113, 30, 1.2, 10.4)


def window_grid(rng, density):
    """The lit windows: 0.7 x 1 units every 2 x 2.6 (the mockup's pattern) inside the panels, some dark."""
    yy, xx = np.mgrid[0:c.h, 0:c.w].astype(np.float32)
    fx, fy = xx / c.U, yy / c.U
    panel = c.shape(lambda p: [p.rect(*r) for r in PANELS])
    cells = (np.mod(fx, 2.0) < 0.7) & (np.mod(fy, 2.6) < 1.0)
    on = rng.random((int(108 / 2.6) + 2, int(192 / 2.0) + 2)) < density
    lit = on[(fy / 2.6).astype(int), (fx / 2.0).astype(int)]
    return (panel * cells * lit).astype(np.float32)


def edge_light(mask, dx_units):
    """The rim of a silhouette on the side facing the light: the mask minus itself moved by dx."""
    shift = int(round(dx_units * c.U))
    return np.clip(mask - np.roll(mask, shift, axis=1), 0, 1)


def beacon(img, strength):
    c.radial(img, *BEACON, 3.2, [(0, "#9FDCFF", .55 * strength), (.4, "#9FDCFF", .2 * strength),
                                 (1, "#9FDCFF", 0)], mode="add")
    c.radial(img, *BEACON, 1.1, [(0, "#FFFFFF", 1), (.6, "#BFEFFF", .9), (1, "#9FDCFF", 0)])


def night(rng):
    img = c.blank("#000000")
    c.radial(img, 96, 67, 153.6, [(0, "#8E1222", 1), (.45, "#3A0810", 1), (.85, "#0D0205", 1), (1, "#000000", 1)],
             ry=86.4)
    # the anamorphic streak at 46 %: a soft warm ellipse and a thin bright line across the whole width
    c.radial(img, 96, 49.7, 40, [(0, "#FFE6C0", .7), (.5, "#FF8C46", .18), (1, "#FF8C46", 0)], ry=7, mode="over")
    xs = np.linspace(0, 1, c.w, dtype=np.float32)
    line_alpha = np.clip(1 - np.abs(xs - 0.5) * 2, 0, 1) ** 1.4
    line = c.shape(lambda p: p.rect(0, 49.3, 192, 0.8), blur=0.15) * line_alpha[None, :]
    c.blend(img, line, "#FFF5E5")
    body = c.shape(skyline)
    c.blend(img, body, "#05000A")
    c.add(img, edge_light(body, 0.25) * 0.35, "#5A1018")      # a faint red rim from the glow behind
    c.blend(img, window_grid(rng, 0.72) * 0.55, "#FFD9A0")
    beacon(img, 1.0)
    c.blend(img, c.shape(lambda p: p.rect(0, 92, 192, 16), blur=1.5) * 0.85, "#000000")
    c.keep_dark(img, corners=0.6, bottom=0.5)
    return img


def day(rng):
    img = c.vgradient([(0, "#120306"), (.45, "#1E0709"), (.78, "#3A1010"), (1, "#3A1010")])
    # the low sun on the right, behind the blocks, and its wide warm backlight
    c.radial(img, 152, 72, 100, [(0, "#FFB070", .70), (.35, "#E07040", .30), (1, "#3A1010", 0)], ry=46, mode="add")
    c.radial(img, 152, 71, 28, [(0, "#FFE2B8", .75), (.4, "#FFB070", .38), (1, "#FFB070", 0)], ry=15, mode="add")
    c.radial(img, 152, 70.5, 5.2, [(0, "#FFF6E4", 1), (.7, "#FFD8A0", .95), (1, "#FFB070", 0)])
    # thin warm haze bands low over the city
    for y0, a in ((66, .10), (71, .08)):
        c.add(img, c.shape(lambda p: p.rect(0, y0, 192, 1.2), blur=1.4) * a, "#FFB070")
    body = c.shape(skyline)
    c.blend(img, body, "#0A0306")
    # backlit: the edges facing the sun glow, more so the nearer a block stands to it
    xs = np.linspace(0, 192, c.w, dtype=np.float32)
    near = np.clip(1 - np.abs(xs - 152) / 110, 0, 1)[None, :] ** 1.5
    left_of_sun = (xs < 152).astype(np.float32)[None, :]
    for width, alpha, colour in ((0.3, 0.9, "#FFB070"), (0.9, 0.25, "#C06030")):
        rim = edge_light(body, -width) * left_of_sun + edge_light(body, width) * (1 - left_of_sun)
        c.add(img, rim * near * alpha, colour)
    # a few windows catch the low sun
    c.blend(img, window_grid(rng, 0.25) * 0.22, "#FFB070")
    beacon(img, 0.6)
    c.blend(img, c.shape(lambda p: p.rect(0, 92, 192, 16), blur=1.5) * 0.85, "#000000")
    c.keep_dark(img, corners=0.7, bottom=0.5)
    return img


if __name__ == "__main__":
    rng = np.random.default_rng(1963)
    ok = c.export(night(rng), "marvel_night", seed=21)
    ok = c.export(day(rng), "marvel_day", seed=22) and ok
    sys.exit(0 if ok else 1)
