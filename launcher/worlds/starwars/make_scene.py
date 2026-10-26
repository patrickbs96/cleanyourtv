#!/usr/bin/env python3
"""Draws the Star Wars world's two scenes from code alone (Pillow + NumPy, fixed seed):

    world_starwars_day.jpg    two suns setting over desert dunes in a heat haze, with a farm's dome, two moisture
                              vaporators and a lone figure on the left
    world_starwars_night.jpg  open space: a denser star field and a large ringed planet cut off at the bottom right

    python launcher/worlds/starwars/make_scene.py

Shapes use the mockup's 192 x 108 units (vier-welten-v3.html). Silhouettes only: no characters, ships, emblems or
lettering. The hyperspace streaks and the twinkling are the overlay's (drawn live).
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import common as c  # noqa: E402

SUN1 = (111.4, 70.2, 13.4)              # 58 %, 65 %, r 7 % of the width
SUN2 = (134.4, 72.4, 8.6)               # 70 %, 67 %, r 4.5 %
DAY_STARS = [(8, 8, .4), (22, 24, .35), (36, 5, .5), (50, 18, .35), (66, 10, .4), (80, 30, .35), (94, 6, .5),
             (110, 20, .4), (128, 36, .35), (146, 12, .5), (164, 28, .35), (182, 8, .4), (58, 42, .35),
             (174, 46, .4), (14, 48, .35), (120, 50, .35), (42, 56, .3)]


def bezier(p0, p1, p2, p3, n=40):
    return [((1 - t) ** 3 * p0[0] + 3 * (1 - t) ** 2 * t * p1[0] + 3 * (1 - t) * t ** 2 * p2[0] + t ** 3 * p3[0],
             (1 - t) ** 3 * p0[1] + 3 * (1 - t) ** 2 * t * p1[1] + 3 * (1 - t) * t ** 2 * p2[1] + t ** 3 * p3[1])
            for t in (i / n for i in range(n + 1))]


def dune_outline(start, c1, c2, mid, c4, end):
    """The mockup's 'M start C c1, c2, mid S c4, end': the S's first control mirrors c2 about mid."""
    c3 = (2 * mid[0] - c2[0], 2 * mid[1] - c2[1])
    return bezier(start, c1, c2, mid) + bezier(mid, c3, c4, end)[1:]


DUNE1 = dune_outline((0, 84), (30, 76), (60, 90), (96, 82), (160, 76), (192, 86))
DUNE2 = dune_outline((0, 94), (40, 86), (70, 100), (110, 92), (170, 90), (192, 98))


def farm(p):
    # the dome: half an ellipse on a low base
    p.d.pieslice([28 * c.U, 81 * c.U, 42 * c.U, 91 * c.U], 180, 360, fill=255)
    p.rect(30, 85, 10, 1.5)
    p.rect(33.2, 83.4, 3.6, 2.6)        # its doorway hood
    # the taller vaporator: a mast with three crossbars and a cap
    p.rect(54, 66, 1.4, 20)
    p.rect(51, 68, 7.4, 1)
    p.rect(51.5, 72, 6.4, 0.8)
    p.rect(52, 76, 5.4, 0.8)
    p.rect(53, 64, 3.4, 2.5)
    p.poly([(52.2, 86), (54.7, 82.5), (57.2, 86)])
    # the smaller one
    p.rect(62, 74, 1, 12)
    p.rect(60, 76, 5, 0.8)
    p.rect(60.5, 79, 4, 0.8)
    p.rect(61.6, 72.8, 1.8, 1.4)
    # a lone figure, a hooded robe a little wider at the hem
    p.poly([(44.45, 83), (45.45, 83), (45.8, 86.1), (44.1, 86.1)])
    p.circle(44.95, 82.6, 0.55)


def heat_shimmer(img, y0, y1, amplitude):
    """Moves each row between y0 and y1 (units) sideways a little, more towards the ground: the heat haze."""
    r0, r1 = int(y0 * c.U), int(y1 * c.U)
    for r in range(r0, r1):
        k = (r - r0) / max(1, r1 - r0)
        shift = int(round(amplitude * c.U * k * math.sin(r / (0.55 * c.U)) * (0.6 + 0.4 * math.sin(r / (2.3 * c.U)))))
        if shift:
            img[r] = np.roll(img[r], shift, axis=0)


def day():
    img = c.vgradient([(0, "#000000"), (.5, "#03040C"), (.66, "#2A1206"), (.72, "#6A2E0C"), (.76, "#1A0C04"),
                       (1, "#000000")])
    c.stars(img, DAY_STARS, colour="#DFE8FF")
    # the haze over the horizon, then the two suns
    c.radial(img, 120, 80, 72, [(0, "#FF9A3C", .42), (.5, "#FF9A3C", .16), (1, "#FF9A3C", 0)], ry=22, mode="add")
    x, y, r = SUN1
    c.radial(img, x, y, r * 2.2, [(0, "#FFB060", .22), (1, "#FF7A2A", 0)], mode="add")
    c.radial(img, x, y, r, [(0, "#FFF6DC", 1), (.55, "#FFCF7A", 1), (.75, "#FF9A3C", .95), (1, "#FF7A2A", 0)])
    x, y, r = SUN2
    c.radial(img, x, y, r * 2.2, [(0, "#FFA050", .18), (1, "#FF7A2A", 0)], mode="add")
    c.radial(img, x, y, r, [(0, "#FFE9C4", 1), (.55, "#FFB866", 1), (.78, "#FF8A3C", .95), (1, "#FF7A2A", 0)])
    heat_shimmer(img, 74, 86, 0.35)
    # a faint band of dust low over the dunes
    c.add(img, c.shape(lambda p: p.rect(0, 80.5, 192, 1.6), blur=1.2) * 0.12, "#FF9A3C")
    # the dunes: the far one lit at its crest, the near one nearly black
    far = c.shape(lambda p: p.poly(DUNE1 + [(192, 108), (0, 108)]))
    c.blend(img, far, c.vgradient([(0, "#2B1708"), (1, "#0A0502")], top=76 / 108, bottom=1.0))
    crest = np.clip(far - np.roll(far, int(0.5 * c.U), axis=0), 0, 1)
    c.add(img, crest * 0.55, "#7A4218")
    near = c.shape(lambda p: p.poly(DUNE2 + [(192, 108), (0, 108)]))
    c.blend(img, near, c.vgradient([(0, "#1A0D05"), (1, "#000000")], top=86 / 108, bottom=1.0))
    crest = np.clip(near - np.roll(near, int(0.4 * c.U), axis=0), 0, 1)
    c.add(img, crest * 0.30, "#5A3010")
    c.blend(img, c.shape(farm, blur=0.03), "#070301")
    c.keep_dark(img, corners=0.5, bottom=0.4)
    return img


def planet(img, cx, cy, r, ring_rx, ring_ry, tilt_deg):
    """A banded planet lit from the upper left, with a ring whose far half passes behind it."""
    x0, x1 = max(0, int((cx - ring_rx - 2) * c.U)), c.w
    y0, y1 = max(0, int((cy - r - ring_ry - 30) * c.U)), c.h
    yy, xx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
    dx, dy = xx / c.U - cx, yy / c.U - cy
    part = img[y0:y1, x0:x1]
    # the ring, in its own tilted frame
    t = math.radians(tilt_deg)
    u = dx * math.cos(t) + dy * math.sin(t)
    v = -dx * math.sin(t) + dy * math.cos(t)
    e = np.sqrt((u / ring_rx) ** 2 + (v / ring_ry) ** 2)
    ring = np.zeros_like(e)
    for lo, hi, a in ((0.66, 0.74, .30), (0.75, 0.86, .55), (0.875, 0.95, .42), (0.955, 1.0, .22)):
        ring += np.clip(np.minimum(e - lo, hi - e) * 60, 0, 1) * a
    ring *= 0.85 + 0.15 * np.sin(e * 140)
    ring_light = 0.6 + 0.4 * np.clip(-u / ring_rx, -1, 1)         # brighter towards the sun's side
    ring_col = c.rgb("#8C9CC8")
    # the planet
    d2 = (dx / r) ** 2 + (dy / r) ** 2
    disc = np.clip((1 - np.sqrt(d2)) * r * c.U / 1.5, 0, 1)          # anti-aliased edge
    nz = np.sqrt(np.clip(1 - d2, 0, 1))
    nx, ny = dx / r, dy / r
    light = np.array([-0.62, -0.55, 0.56])
    light /= np.linalg.norm(light)
    lambert = np.clip(nx * light[0] + ny * light[1] + nz * light[2], 0, 1)
    bands = 1 + 0.10 * np.sin(v / r * 26) + 0.05 * np.sin(v / r * 61 + 1.3)
    base = c.rgb("#2B3A6B")
    body = base[None, None, :] * (0.12 + 1.05 * lambert[:, :, None]) * bands[:, :, None]
    rim = np.clip(1 - nz, 0, 1) ** 3 * np.clip(lambert * 3, 0, 1)
    body += rim[:, :, None] * c.rgb("#6A86D0") * 0.8
    behind = (v < 0).astype(np.float32)
    a_back = ring * behind * (1 - disc)
    a_back = a_back[:, :, None]
    part *= 1 - a_back
    part += a_back * ring_col * ring_light[:, :, None]
    a = disc[:, :, None]
    part *= 1 - a
    part += a * body
    # the planet's shadow falls on the near ring just right of it
    shadow = np.clip(1 - np.sqrt(((dx - 10) / r) ** 2 + ((dy - 9) / r) ** 2), 0, 1) > 0
    a_front = ring * (1 - behind) * np.where(shadow, 0.35, 1.0)
    a_front = a_front[:, :, None]
    part *= 1 - a_front
    part += a_front * ring_col * ring_light[:, :, None]
    # a thin blue haze round the limb
    halo = np.exp(-((np.sqrt(d2) - 1) * r / 1.4) ** 2) * (np.sqrt(d2) > 1)
    part += (halo * 0.35)[:, :, None] * c.rgb("#4A64B0")
    return img


def night(rng):
    img = c.vgradient([(0, "#000000"), (.6, "#010208"), (1, "#02030A")])
    # a very faint band of the galaxy, from upper left to lower right
    yy, xx = np.mgrid[0:c.h, 0:c.w].astype(np.float32)
    dist = np.abs((yy / c.U - 20) - (xx / c.U) * 0.35) / 18
    c.add(img, (np.exp(-dist ** 2) * 0.06).astype(np.float32), "#3A4A80")
    del yy, xx, dist
    pts = c.random_stars(rng, 60, r_range=(0.22, 0.5))
    bright = [(x, y, r * 1.35) for x, y, r in pts[:8]]
    c.stars(img, pts[8:], colour="#DFE8FF")
    c.stars(img, bright, colour="#FFFFFF", glow=0.35)
    # the ringed planet: centre (92 %, 112 %), r 22 % of the width, ring 32 % x 7 %, tilted so it shows
    planet(img, 0.92 * 192, 1.12 * 108, 0.22 * 192, 0.32 * 192, 0.07 * 108, 24)
    c.keep_dark(img, corners=0.5, bottom=0.3)
    return img


if __name__ == "__main__":
    rng = np.random.default_rng(1977)
    ok = c.export(day(), "starwars_day", seed=31)
    ok = c.export(night(rng), "starwars_night", seed=32) and ok
    sys.exit(0 if ok else 1)
