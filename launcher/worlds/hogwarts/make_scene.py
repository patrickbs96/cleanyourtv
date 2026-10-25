#!/usr/bin/env python3
"""Draws the Hogwarts world's two scenes from code alone (Pillow + NumPy, fixed seed):

    world_hogwarts_night.jpg  a castle on a cliff on the right, towers with pointed roofs, lit windows and a viaduct
                              with two arches, above a lake that mirrors it; a large moon and twelve stars
    world_hogwarts_day.jpg    a great hall by day: an enchanted ceiling of drifting cloud bands, two shafts of light
                              from the tall windows, two owls in flight high on the left, long tables on a dark floor

    python launcher/worlds/hogwarts/make_scene.py

Shapes use the mockup's 192 x 108 units (vier-welten-v3.html). Silhouettes only: no characters, crests or film
imagery. The floating candles and the twinkling are the overlay's (drawn live), not part of the pictures.
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import common as c  # noqa: E402

LAKE = 82                               # lake line in units: 76 % of the height
WINDOW = "#FFD37A"


# ---- night: the castle on the cliff -------------------------------------------------------------------------------
def castle(p):
    # the cliff, a steep rock face on the left
    p.poly([(101, 82), (102, 76), (104.5, 70), (106, 66), (110, 63.5), (118, 61), (128, 60), (150, 58), (172, 62),
            (184, 66), (192, 70), (192, 82)])
    # the main keep with battlements
    p.rect(128, 36, 22, 26)
    for i in range(6):
        p.rect(128 + i * 4, 33, 2, 3)
    p.rect(150, 34, 1.4, 2)
    # the great tower and its spire
    p.rect(138, 20, 7, 18)
    p.poly([(137, 20), (141.5, 7), (146, 20)])
    for i in range(3):                  # battlements under the spire
        p.rect(137.5 + i * 2.6, 19, 1.4, 1.4)
    # six towers with pointed roofs: three on the left, three on the right
    for x, top, tw, roof in ((106, 54, 4, 8), (112, 46, 6, 10), (120, 42, 5, 8),
                             (152, 40, 6, 12), (161, 48, 5, 8), (168, 52, 4, 6)):
        p.rect(x, top, tw, 64 - top)
        p.poly([(x - 1, top), (x + tw / 2, top - roof), (x + tw + 1, top)])
    # a low hall between keep and right towers
    p.rect(150, 46, 12, 16)
    for i in range(3):
        p.rect(150.5 + i * 4, 44.6, 2, 1.4)
    # the viaduct: a deck on three piers with two arches, from a rock on the left into the cliff face
    p.poly([(74, 82), (75.5, 78), (78, 76.5), (81, 77), (82.5, 82)])
    p.rect(78.5, 71.6, 25, 10.4)
    p.rect(78.5, 70.8, 25, 0.8)
    for i in range(9):                  # a parapet along the deck
        p.rect(79 + i * 2.8, 70, 1.2, 0.9)


def viaduct_arches(p):
    for x0 in (81.6, 92.4):
        p.rect(x0, 76.6, 8.4, 5.4)
        p.ellipse(x0 + 4.2, 76.6, 4.2, 3.4)


WINDOWS = [(131, 42), (135, 46), (144, 50), (140, 26), (141.5, 31), (114, 52), (122, 48), (154, 46), (155, 54),
           (162.5, 53), (133, 54), (146, 40), (107.4, 58), (123, 55), (142, 44), (169.4, 56), (157, 50), (137, 58)]


def windows(p):
    for x, y in WINDOWS:
        p.rect(x, y, 1.2, 2)
        p.ellipse(x + 0.6, y, 0.6, 0.5)    # a pointed-ish top


NIGHT_STARS = [(12, 10, .5), (30, 22, .4), (52, 8, .6), (70, 18, .4), (88, 6, .5), (104, 26, .4), (126, 12, .5),
               (176, 22, .4), (160, 40, .4), (40, 38, .4), (96, 42, .4), (186, 8, .5)]


def night():
    img = c.vgradient([(0, "#06051A"), (.55, "#141020"), (1, "#241A3A")])
    c.stars(img, NIGHT_STARS, glow=0.25)
    # the moon at (78 %, 20 %), with a wide faint halo
    c.radial(img, 150, 21.6, 22, [(0, "#C9A84C", .16), (.5, "#C9A84C", .06), (1, "#C9A84C", 0)], mode="add")
    c.radial(img, 150, 21.6, 11.5, [(0, "#FFF8E0", .97), (.6, "#F3E4B0", .95), (.9, "#E0C98A", .9),
                                    (1, "#C9A84C", 0)])
    # a few soft maria on the disc
    maria = c.shape(lambda p: (p.circle(146.5, 19.5, 2.6), p.circle(152.5, 24.5, 2.1), p.circle(151, 17.4, 1.3)),
                    blur=0.9)
    c.blend(img, maria * 0.10, "#8C7A50")
    # the castle, its arches cut open again, and a faint moonlit rim on the left edges of the towers
    body = np.clip(c.shape(castle) - c.shape(viaduct_arches), 0, 1)
    rim = np.clip(body - np.roll(body, -int(0.35 * c.U), axis=1), 0, 1)
    rim[int(LAKE * c.U):] = 0
    c.blend(img, body, "#0A0818")
    c.add(img, rim * 0.5, "#2A2448")
    lit = c.shape(windows, blur=0.08)
    c.add(img, c.shape(windows, blur=0.9) * 0.55, "#FFB347")
    c.blend(img, lit, WINDOW)
    # the lake: a rippled, darkened mirror of everything above it
    hy = int(LAKE * c.U)
    lake = c.vgradient([(0, "#17132A"), (1, "#05040E")], top=LAKE / 108, bottom=1.0)[hy:]
    above = img[:hy][::-1]
    lake_h = c.h - hy
    refl = np.empty((lake_h, c.w, 3), dtype=np.float32)
    for i in range(lake_h):
        src = above[min(int(i * 0.94), hy - 1)]
        shift = int((math.sin(i * 0.05 / c.S) + 0.35 * math.sin(i * 0.17 / c.S)) * (1 + i / lake_h * 3) * c.S)
        refl[i] = np.roll(src, shift, axis=0)
    fade = np.linspace(0.40, 0.08, lake_h, dtype=np.float32)[:, None, None]
    water = lake * (1 - fade) + refl * fade
    for i in range(0, lake_h, 7 * c.S):  # soft horizontal glints
        water[i:i + c.S] *= 1.12
    img[hy:] = water
    # the shore line
    c.add(img, c.shape(lambda p: p.rect(0, LAKE - 0.15, 192, 0.3), blur=0.2) * 0.4, "#3A3058")
    c.keep_dark(img, corners=0.6, bottom=0.6)
    return img


# ---- day: the great hall -------------------------------------------------------------------------------------------
VP = (96, 58)                            # vanishing point of the hall


def walls(p):
    p.poly([(0, 14), (52, 42), (52, 64), (0, 98)])               # left wall
    p.poly([(192, 14), (140, 42), (140, 64), (192, 98)])         # right wall
    p.poly([(52, 42), (140, 42), (140, 64), (52, 64)])           # back wall


def floor(p):
    p.poly([(52, 64), (140, 64), (192, 98), (192, 108), (0, 108), (0, 98)])


def arch_window(p, x, top, ww, wh, bars=False):
    """A tall pointed window: a rectangle under a lancet top; with bars, only its stone mullion and transoms."""
    if bars:
        bw = max(0.18, ww * 0.09)
        p.rect(x + ww / 2 - bw / 2, top, bw, wh)
        for k in (0.38, 0.68):
            p.rect(x, top + wh * k, ww, bw)
        return
    p.rect(x, top + ww * 0.6, ww, wh - ww * 0.6)
    p.poly([(x, top + ww * 0.6), (x + ww / 2, top - ww * 0.25), (x + ww, top + ww * 0.6)])


def wall_windows(p, bars=False):
    window = lambda *a: arch_window(p, *a, bars=bars)  # noqa: E731
    # along each wall in perspective: nearer ones larger
    for i, t in enumerate((0.10, 0.30, 0.48, 0.64, 0.78)):
        for side in (-1, 1):
            xa = 0 + t * 52 if side < 0 else 192 - t * 52
            top = 14 + t * 28 + 6 * (1 - t)
            bottom = 98 - t * 34 - 20 * (1 - t)
            ww = 6.5 * (1 - t) + 1.6
            x = xa + (1.5 if side < 0 else -1.5 - ww)
            window(x, top, ww, bottom - top)
    # the great window in the back wall and two smaller ones beside it
    window(91, 45, 10, 16)
    window(76, 48, 6, 12)
    window(110, 48, 6, 12)


def tables(p):
    # four long tables running to the back, and the staff table across the far end
    for near_x in (26, 70, 122, 166):
        far = [(VP[0] + (near_x - VP[0]) * k, VP[1] + (108 - VP[1]) * k) for k in (0.14, 1.0)]
        (fx, fy), (nx, ny) = far
        half_far, half_near = 0.6, 4.6
        p.poly([(fx - half_far, fy), (fx + half_far, fy), (nx + half_near, ny + 1), (nx - half_near, ny + 1)])
    p.rect(66, 62.4, 60, 1.6)


def owl(p, cx, cy, span, lift):
    """An owl gliding, seen from below: a big round head without ears, a stout body, long broad wings whose
    leading edge bows forward, a smooth trailing edge, a few spread primaries at the tips and a short fan tail."""
    b = span / 12
    p.ellipse(cx, cy + 0.6 * b, 1.35 * b, 2.0 * b)                 # body
    p.circle(cx, cy - 1.3 * b, 1.45 * b)                           # head, as wide as the body
    p.poly([(cx - 1.2 * b, cy + 2.0 * b), (cx - 1.5 * b, cy + 3.4 * b), (cx, cy + 3.8 * b),
            (cx + 1.5 * b, cy + 3.4 * b), (cx + 1.2 * b, cy + 2.0 * b)])   # fan tail
    for side in (-1, 1):
        lead, trail = [], []
        for k in range(0, 13):
            t = k / 12
            x = cx + side * (0.8 * b + t * 5.0 * b)
            arch = lift * b * math.sin(t * math.pi * 0.9)
            lead.append((x, cy - 0.4 * b - arch - 0.9 * b * math.sin(t * math.pi)))      # bowed leading edge
            trail.append((x, cy + 1.5 * b - arch * 0.7 - 0.3 * b * t))                   # smooth trailing edge
        wing = lead + trail[::-1]
        p.poly(wing)
        # a rounded wing tip with short, blunt primaries
        tx, ty = lead[-1]
        p.ellipse(tx, ty + 0.9 * b, 0.9 * b, 1.0 * b)
        for f in range(3):
            p.ellipse(tx + side * 0.9 * b, ty + 0.4 * b + f * 0.6 * b, 0.6 * b, 0.28 * b)


def owl_eyes(p, cx, cy, span):
    """The two round eyes on the owl's face, the one thing that tells it from any other bird."""
    b = span / 12
    for side in (-1, 1):
        p.circle(cx + side * 0.6 * b, cy - 1.35 * b, 0.42 * b)


def day(rng):
    # the enchanted ceiling: a pale violet-grey sky, darkest at the very top
    img = c.vgradient([(0, "#16122A"), (.12, "#2E2946"), (.30, "#57516F"), (.42, "#6A6482"), (.56, "#4E4866"),
                       (.66, "#2C2640")])
    # bands of cloud drifting across it
    yy, xx = np.mgrid[0:c.h, 0:c.w].astype(np.float32)
    fx, fy = xx / c.U, yy / c.U
    for k, (y0, thick, amp, alpha) in enumerate(((14, 3.2, 1.8, .26), (22, 4.0, 2.4, .30), (31, 4.6, 2.0, .28),
                                                 (39, 3.6, 1.6, .22), (8, 2.4, 1.2, .14))):
        phase = rng.uniform(0, 6.28)
        centre = y0 + amp * np.sin(fx * 0.045 + phase) + 0.8 * np.sin(fx * 0.13 + phase * 2.1)
        band = np.exp(-((fy - centre) / thick) ** 2)
        lumps = 0.55 + 0.45 * np.sin(fx * 0.09 + phase * 3.3) * np.sin(fx * 0.031 + k)
        c.blend(img, (band * lumps * alpha).astype(np.float32), "#A9A3BE")
    del yy, xx, fx, fy
    # the hall: stone walls, a little lighter where the daylight falls high up, and their tall windows
    wall = c.shape(walls)
    c.blend(img, wall, c.vgradient([(0, "#221C30"), (.4, "#18131F"), (1, "#0C0A12")]))
    glass = np.clip(c.shape(wall_windows, blur=0.12) - c.shape(lambda p: wall_windows(p, bars=True)), 0, 1)
    c.blend(img, glass * 0.9, c.vgradient([(0, "#6E6890"), (.5, "#4C4768"), (1, "#2A2640")]))
    c.add(img, c.shape(wall_windows, blur=1.4) * 0.12, "#8C86A8")
    c.blend(img, c.shape(floor), c.vgradient([(0, "#0D0A14"), (1, "#050409")], top=64 / 108, bottom=1.0))
    # two shafts of light from the right wall's nearest windows, across the hall to the floor
    fade = c.vgradient([(0, "#FFFFFF"), (.2, "#FFFFFF"), (1, "#383838")])[:, :, 0] / 255.0
    for quad, alpha in ((((178, 24), (184.5, 22), (128, 106), (100, 104)), .26),
                        (((169, 28), (174.5, 27), (100, 94), (80, 92)), .18)):
        shaft = c.shape(lambda p: p.poly(list(quad)), blur=1.6)
        c.add(img, shaft * fade * alpha, "#FFE2A8")
    del fade
    c.blend(img, c.shape(tables, blur=0.1), "#100B09")
    c.add(img, c.shape(lambda p: p.rect(66, 62.4, 60, 0.3), blur=0.1) * 0.5, "#4A3A2A")
    # two owls high on the left, clear of the clock
    c.blend(img, c.shape(lambda p: (owl(p, 66, 16, 12, 1.6), owl(p, 80, 11, 8.5, 2.6)), blur=0.05), "#120E1E")
    c.blend(img, c.shape(lambda p: (owl_eyes(p, 66, 16, 12), owl_eyes(p, 80, 11, 8.5)), blur=0.04) * 0.8,
            "#C9A84C")
    c.keep_dark(img, corners=1.0, bottom=0.6)
    return img


if __name__ == "__main__":
    rng = np.random.default_rng(1903)
    ok = c.export(night(), "hogwarts_night", seed=11)
    ok = c.export(day(rng), "hogwarts_day", seed=12) and ok
    sys.exit(0 if ok else 1)
