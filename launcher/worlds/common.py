#!/usr/bin/env python3
"""Shared drawing helpers for the worlds' scenes (launcher/worlds/<id>/make_scene.py).

Every scene is drawn from code alone, at twice the size and scaled down for clean edges, in the mockup's
coordinates: 192 x 108 units for a 16:9 screen (vier-welten-v3.html draws its scenes in that viewBox), so a
shape can be copied from the mockup and drawn here with the same numbers.

    python launcher/worlds/common.py     # checks every generated picture (world_*_day/night, world_seasons_*)
                                         # and prints their numbers

A scene must stay under the launcher's UI: 1920 x 1080, a JPEG of at most 300 KB, and dark where the text and the
dock sit, that is in both top corners (15 % x 15 % each) and in the bottom third, each with a mean luminance of at
most 40 (Rec. 601, 0-255).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080
S = 2                                   # drawn at 2x, scaled down on export
w, h = W * S, H * S
U = w / 192                             # pixels per mockup unit at the drawing size
MAX_BYTES = 300 * 1024
MAX_LUMA = 40
OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "drawable-nodpi"
WORLDS = ("hogwarts", "marvel", "starwars")
SEASONS = ("winter", "spring", "summer", "autumn")   # Jahreskreis' seasons, a day and a night picture each


def rgb(hex_colour):
    """'#RRGGBB' as an (r, g, b) float array."""
    s = hex_colour.lstrip("#")
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def blank(colour="#000000"):
    img = np.empty((h, w, 3), dtype=np.float32)
    img[:] = rgb(colour)
    return img


def vgradient(stops, top=0.0, bottom=1.0):
    """A full-size picture with rows of colour from (position, '#hex') stops between the fractions top and bottom."""
    ys = (np.arange(h, dtype=np.float32) / (h - 1) - top) / max(bottom - top, 1e-6)
    pos = [p for p, _ in stops]
    cols = [rgb(c) for _, c in stops]
    rows = np.stack([np.interp(ys, pos, [c[i] for c in cols]) for i in range(3)], axis=1).astype(np.float32)
    return np.repeat(rows[:, None, :], w, axis=1)


def blend(img, alpha, colour):
    """Paints colour (a '#hex', an rgb array or a picture) over img with alpha (h x w, 0..1), in place."""
    a = alpha[:, :, None]
    if isinstance(colour, str):
        colour = rgb(colour)
    img *= 1 - a
    img += a * colour
    return img


def add(img, alpha, colour):
    """Adds light: colour times alpha, in place."""
    if isinstance(colour, str):
        colour = rgb(colour)
    img += alpha[:, :, None] * colour
    return img


def shape(draw_fn, blur=0.0):
    """An alpha mask (h x w floats) from a function that draws in white on an ImageDraw, in mockup units.

    draw_fn gets a Pen whose methods take mockup units; blur is in mockup units too.
    """
    m = Image.new("L", (w, h), 0)
    draw_fn(Pen(ImageDraw.Draw(m)))
    if blur > 0:
        m = m.filter(ImageFilter.GaussianBlur(blur * U))
    return np.asarray(m, dtype=np.float32) / 255.0


class Pen:
    """ImageDraw in mockup units (192 x 108)."""

    def __init__(self, draw):
        self.d = draw

    @staticmethod
    def _p(points):
        return [(x * U, y * U) for x, y in points]

    def rect(self, x, y, rw, rh, fill=255):
        self.d.rectangle([x * U, y * U, (x + rw) * U - 1, (y + rh) * U - 1], fill=fill)

    def poly(self, points, fill=255):
        self.d.polygon(self._p(points), fill=fill)

    def circle(self, cx, cy, r, fill=255):
        self.d.ellipse([(cx - r) * U, (cy - r) * U, (cx + r) * U, (cy + r) * U], fill=fill)

    def ellipse(self, cx, cy, rx, ry, fill=255):
        self.d.ellipse([(cx - rx) * U, (cy - ry) * U, (cx + rx) * U, (cy + ry) * U], fill=fill)


def radial(img, cx, cy, r, stops, ry=None, mode="over"):
    """A radial gradient at (cx, cy) with radius r (mockup units; ry for an ellipse), stops as
    (position 0..1, '#hex', alpha 0..1). mode 'over' paints, 'add' adds light. Only its bounding box is computed."""
    ry = r if ry is None else ry
    x0, x1 = max(0, int((cx - r) * U)), min(w, int((cx + r) * U) + 1)
    y0, y1 = max(0, int((cy - ry) * U)), min(h, int((cy + ry) * U) + 1)
    if x0 >= x1 or y0 >= y1:
        return img
    yy, xx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
    d = np.sqrt(((xx / U - cx) / r) ** 2 + ((yy / U - cy) / ry) ** 2)
    pos = [s[0] for s in stops]
    cols = [rgb(s[1]) for s in stops]
    alpha = np.interp(d, pos, [s[2] for s in stops]).astype(np.float32)
    alpha[d > pos[-1]] = 0
    colour = np.stack([np.interp(d, pos, [c[i] for c in cols]) for i in range(3)], axis=2).astype(np.float32)
    part = img[y0:y1, x0:x1]
    a = alpha[:, :, None]
    if mode == "add":
        part += colour * a
    else:
        part *= 1 - a
        part += colour * a
    return img


def stars(img, points, colour="#ffffff", glow=0.0):
    """Stars at (x, y, r) in mockup units, as soft discs; glow adds a faint halo."""
    def draw(p):
        for x, y, r in points:
            p.circle(x, y, r)
    core = shape(draw, blur=0.12)
    add(img, core, rgb(colour) * 0.95)
    if glow > 0:
        halo = shape(draw, blur=0.9)
        add(img, halo, rgb(colour) * glow)
    return img


def random_stars(rng, n, x_range=(0, 192), y_range=(0, 108), r_range=(0.25, 0.55)):
    return [(float(rng.uniform(*x_range)), float(rng.uniform(*y_range)), float(rng.uniform(*r_range)))
            for _ in range(n)]


def keep_dark(img, corners=1.0, bottom=1.0):
    """Darkens the top corners and the bottom third softly, so the UI over them stays readable.
    corners and bottom scale how strongly (0 = untouched)."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    fx, fy = xx / w, yy / h
    v = np.ones((h, w), dtype=np.float32)
    for cx in (0.0, 1.0):
        dist = np.sqrt(((fx - cx) / 0.30) ** 2 + (fy / 0.30) ** 2)
        v *= 1 - corners * 0.6 * np.exp(-dist ** 2 * 1.6)
    v *= 1 - bottom * 0.5 * np.clip((fy - 0.60) / 0.40, 0, 1) ** 1.2
    img *= v[:, :, None]
    return img


def luma(arr):
    a = np.asarray(arr, dtype=np.float32)
    return a[..., 0] * 0.299 + a[..., 1] * 0.587 + a[..., 2] * 0.114


def measure(path):
    """(bytes, top-left, top-right, bottom third) mean luminance of an exported picture."""
    im = Image.open(path).convert("RGB")
    assert im.size == (W, H), f"{path.name}: {im.size}, not {W}x{H}"
    y = luma(np.asarray(im))
    cw, ch = int(W * 0.15), int(H * 0.15)
    return (path.stat().st_size, float(y[:ch, :cw].mean()), float(y[:ch, W - cw:].mean()),
            float(y[H * 2 // 3:].mean()))


def check(path):
    size, tl, tr, bottom = measure(path)
    ok = size <= MAX_BYTES and max(tl, tr, bottom) <= MAX_LUMA
    print(f"{'OK  ' if ok else 'FAIL'} {path.name}: {size / 1024:.0f} KB, luminance top-left {tl:.1f}, "
          f"top-right {tr:.1f}, bottom third {bottom:.1f} (max {MAX_LUMA})")
    return ok


def export(img, name, seed=1):
    """Scales the drawing down, adds a little grain against banding and saves world_<name>.jpg at the best quality
    that stays within 300 KB; then checks it."""
    grain = np.random.default_rng(seed).normal(0, 1.2, img.shape).astype(np.float32)
    big = Image.fromarray(np.clip(img + grain, 0, 255).astype(np.uint8), "RGB")
    small = big.resize((W, H), Image.LANCZOS)
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"world_{name}.jpg"
    for quality in range(90, 40, -3):
        small.save(path, "JPEG", quality=quality, optimize=True, progressive=True)
        if path.stat().st_size <= MAX_BYTES:
            break
    print(f"{path} (quality {quality})")
    return check(path)


def check_all():
    """Checks the themed worlds' six pictures, the four seasons' eight (all required) and every other
    world_seasons_*.jpg that is there (the occasions' and personal days' looks)."""
    ok = True
    required = [OUT / f"world_{world}_{mood}.jpg" for world in WORLDS for mood in ("day", "night")]
    required += [OUT / f"world_seasons_{season}_{mood}.jpg" for season in SEASONS for mood in ("day", "night")]
    others = sorted(p for p in OUT.glob("world_seasons_*.jpg") if p not in required)
    for path in required + others:
        if not path.exists():
            print(f"FAIL {path.name}: missing")
            ok = False
        else:
            ok = check(path) and ok
    return ok


if __name__ == "__main__":
    sys.exit(0 if check_all() else 1)
