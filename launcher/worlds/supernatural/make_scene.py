#!/usr/bin/env python3
"""Draws the Supernatural world's two scenes from code alone (Pillow + NumPy, fixed seeds), spec § 4.1:

    world_supernatural_day.jpg    "Himmel": the golden hour, a low sun on the horizon and rays of light through the
                                  sky, a dusty country road, telegraph poles, the motel's sign off and its windows dark,
                                  the star on the road like chalk, the warding marks sprayed red
    world_supernatural_night.jpg  "Hölle": a dark sky with clouds, cracks glowing red in the ground on the left horizon,
                                  the sign's neon and some of the windows lit, the star glimmering like embers, the car's
                                  tail light glowing red with its reflection on the wet ground

    python launcher/worlds/supernatural/make_scene.py

Shared by both: a road from the bottom left into the distance on the right, telegraph poles, low hills, a one-storey
motel on the right with doors and windows and a sign on a pole ("MOTEL" / "VACANCY"), and in front of it at the
roadside a long, low black four-door in the style of the late sixties (side view, facing left, chrome belt line,
chrome bumpers, no badge), right of the app name directly above the dock. On the road ahead of it a pentagram in a
circle with short rays; on the motel's end wall a sprayed warding circle (double ring, hexagon, triangle, marks round
it) and small marks over the doors; over the motel the lettering "SUPERNATURAL" in Courier Prime (OFL).

All shapes are this script's own and generic (the mockup supernatural-mockup.html, painters SCENES.day/night, car,
motel, star, sigils, godRays, hellGlow, in the 192 x 108 units of common.py): no logo, no figures, no symbols of the
series, no badge on the car.

What moves is SupernaturalOverlay's, painted here at rest so the picture is whole under reduced motion: the neon
(lit steadily at night, its flicker and the failing letter are the overlay's), the embers of the star and the cracks
(their pulse), the tail light, rain, lightning, the shadow of the wings, the warding marks' glow in the flash, the
rays' breathing, dust and heat shimmer. Where the overlay (or WorldLaunchMotion's headlights, or the logo picture of
spec § 4.4) draws over a painted thing it uses the same numbers as here: they are named in capitals and mirrored in
SupernaturalScene.java (tests/test_supernatural_scene.py keeps the two equal).

Picture rule (spec § 3): what matters stands right of the app name, directly above the dock (its top edge at y 76.6
units), never behind it, never in the header on the top left (x below 76, y below 34) and never under the pills on the
top right (PILLS); both top corners and the bottom third stay dark (common.py), and at night no large area is bright
(OLED rule).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "seasons"))
import common as c  # noqa: E402
from occasions_a import line  # noqa: E402
from seasons import DOCK, XS, YS, corners, shade, under  # noqa: E402

TAU = 2 * np.pi
FONTS = Path(__file__).resolve().parent.parent.parent / "app" / "src" / "main" / "res" / "font"
LETTERING_FONT = FONTS / "courier_prime_bold.ttf"     # Courier Prime (OFL), the world's typeface
SIGN_FONT = FONTS / "oswald_bold.ttf"                 # Oswald (OFL), a narrow face for the motel's neon

# ---- named numbers, in units (192 x 108); SupernaturalScene.java holds the same -----------------------------------
PILLS = (128.0, 11.0, 192.0, 23.0)       # the pills' zone on the top right (PersonalMotions.PILLS): nothing under it
HORIZON = 56.2                           # the mockup's .52
HILLS = (0.02, 0.02, 0.7, 0.015, 2.3, 40.0)   # the hills' ridge (hills_line); the flash lights only the sky above it
# The lettering "SUPERNATURAL" over the motel: x, y, width, height. Its height is 6 % of the screen (spec § 4.4: the
# logo picture takes this place at that height); it lies below the pills and above the motel's roof.
LETTERING = (129.0, 35.6, 47.0, 6.48)
# The car, facing left: its front at x, its tyres standing on y, its length and its height over the ground.
CAR = (111.4, 75.9, 57.6, 9.6)
CAR_HEADLIGHT = (111.9, 71.0)            # the lamp in the front, where the headlights flare up at an app start
CAR_TAIL_LIGHT = (167.9, 70.3, 1.0, 1.9)  # the tail light at the back: x, y, width, height
CAR_ROOF_GLINT = (142.5, 66.6)           # the sun's glint on the roof by day
# The star on the road ahead of the car, a circle in perspective: centre, half width, half height (its rays reach
# 1.22 times further).
STAR = (90.2, 73.0, 10.6, 2.4)
STAR_RAYS = 1.22
# The motel: left, right, the roof's top, the eaves under it, the ground in front of the doors.
MOTEL = (119.0, 186.0, 43.4, 44.8, 58.6)
ROOF_OVERHANG = 1.6                      # how far the flat roof reaches past the walls on both sides
ROOM_X = (122.0, 130.6, 139.2, 147.8, 156.4, 165.0)   # each room's window; its door follows 4.8 to the right
WINDOW = (4.2, 3.6, 50.4)                # width, height, top
DOOR = (4.8, 2.6, 8.4, 50.2)             # offset from the window, width, height, top
LIT_ROOMS = (1, 3, 4)                    # the rooms with a warm window at night
# The warding circle on the end wall: centre and radius; the marks over the doors: centres and their radius.
WARD = (179.6, 51.4, 4.4)
DOOR_MARKS = ((128.1, 47.6), (136.7, 47.6), (145.3, 47.6), (153.9, 47.6), (162.5, 47.6), (171.1, 47.6))  # door middles
DOOR_MARK_R = 0.95
# The sign on its pole: the board (x, y, width, height), the pole's x and foot; the neon "MOTEL" and "VACANCY" as
# rectangles (x, y, width, height), MOTEL's letters' centres and half widths as their ink is painted (the overlay lets
# one of them fail; motel_letter_spans() measures them from the drawn mask, main() and the tests check these).
SIGN = (102.6, 10.4, 24.0, 11.2)
SIGN_POLE = (121.0, 59.6)
SIGN_POLE_HALF = 0.45                    # the pole's half width
NEON_MOTEL = (104.6, 11.6, 20.0, 5.4)
NEON_MOTEL_X = (108.4, 112.575, 115.875, 118.875, 121.775)
NEON_MOTEL_HALF = (1.9, 1.575, 1.375, 1.175, 1.175)
NEON_VACANCY = (106.6, 17.6, 16.0, 2.8)
# The sun on the horizon by day and the rays of light fanning up from it: angles in radians from straight up,
# negative to the left; each ray's half width in radians and its length in units.
SUN = (67.2, 54.0, 6.5)
RAYS = (-0.84, -0.56, -0.28, 0.0, 0.28, 0.56, 0.84)
RAY_HALF, RAY_LEN = 0.05, 75.6
# The cracks glowing in the ground on the left at night, each a polyline; and the area round them with their glow.
CRACKS = (
    ((6.0, 57.4), (9.2, 57.9), (11.0, 57.5), (14.6, 58.6), (17.1, 58.2), (19.4, 59.3), (23.8, 59.0), (26.0, 60.2),
     (30.5, 60.6), (33.2, 60.1), (36.8, 61.4)),
    ((26.0, 60.2), (27.4, 61.6), (26.6, 62.5), (28.3, 64.0)),
    ((33.0, 57.2), (36.4, 57.7), (38.1, 57.3), (41.9, 58.4), (44.7, 58.0), (47.0, 59.0), (51.6, 58.8), (54.2, 59.9),
     (58.9, 60.0), (62.0, 61.2)),
    ((44.7, 58.0), (45.5, 56.9), (47.9, 56.7)),
    ((51.6, 58.8), (52.8, 60.4), (51.9, 61.6), (53.6, 62.8), (52.9, 64.1)),
    ((58.9, 60.0), (61.8, 59.4), (64.4, 59.8), (67.6, 59.1), (70.8, 59.6)),
)
CRACK_W = 0.26                           # the cracks' width in the middle; they taper to their ends
CRACK_AREA = (4.0, 54.0, 70.0, 12.0)
# The sky behind the lettering at its four corners (top left, top right, bottom left, bottom right) as painted, by
# day and by night: the overlay paints this over the lettering when the logo picture takes its place (spec § 4.4).
LETTERING_SKY_DAY = ("#B77141", "#A7673C", "#CD864A", "#C38047")
LETTERING_SKY_NIGHT = ("#0E131B", "#0D1219", "#10151E", "#0F141D")

# Seeds: the shapes (clouds, sprayed marks) and the grain of each picture (common.export).
SEED = 1967
GRAIN_DAY, GRAIN_NIGHT = 81, 82


# ---- helpers ------------------------------------------------------------------------------------------------------
def text_mask(text, font_path, box, fit_width=True):
    """A mask with text in white, sized so its ink fills box's width (or height) and centred in box (units)."""
    x, y, bw, bh = box
    probe = ImageFont.truetype(str(font_path), 200)
    left, top, right, bottom = probe.getbbox(text)
    k = (bw * c.U / (right - left)) if fit_width else (bh * c.U / (bottom - top))
    font = ImageFont.truetype(str(font_path), max(1, round(200 * k)))
    left, top, right, bottom = font.getbbox(text)
    m = Image.new("L", (c.w, c.h), 0)
    ox = (x + bw / 2) * c.U - (left + right) / 2
    oy = (y + bh / 2) * c.U - (top + bottom) / 2
    ImageDraw.Draw(m).text((ox, oy), text, font=font, fill=255)
    return np.asarray(m, dtype=np.float32) / 255.0


def blurred(mask, radius):
    """A mask softened by a Gaussian blur of radius units."""
    im = Image.fromarray((np.clip(mask, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius * c.U))
    return np.asarray(im, dtype=np.float32) / 255.0


def rect_mask(box, blur=0.0):
    x, y, bw, bh = box
    return c.shape(lambda p: p.rect(x, y, bw, bh), blur=blur)


def hills_line():
    """The mockup's distant hills per pixel column: HORIZON - (2 % + 2 % sin(i .7) + 1.5 % sin(i 2.3)) of the height,
    with i the column as a fortieth of the width (HILLS: base, first amplitude and rate, second amplitude and rate,
    the fraction of the width; SupernaturalScene.hillTop draws the same ridge)."""
    base, a1, f1, a2, f2, cols = HILLS
    i = XS / 192 * cols
    return (HORIZON - 108 * (base + a1 * np.sin(i * f1) + a2 * np.sin(i * f2))).astype(np.float32)


def motel_letter_spans():
    """MOTEL's letters as painted: each one's (left, right) ink in units, read off the drawn mask's columns."""
    mask = text_mask("MOTEL", SIGN_FONT, NEON_MOTEL, fit_width=False)
    cols = np.where(mask.max(axis=0) > 0.5)[0]
    runs = []
    for col in cols:
        if runs and col == runs[-1][1] + 1:
            runs[-1][1] = col
        else:
            runs.append([col, col])
    return [(a / c.U, (b + 1) / c.U) for a, b in runs]


def check_motel_letters():
    """NEON_MOTEL_X and NEON_MOTEL_HALF must be where the letters are painted (the overlay puts one out there)."""
    spans = motel_letter_spans()
    ok = len(spans) == len(NEON_MOTEL_X)
    for (left, right), x, half in zip(spans, NEON_MOTEL_X, NEON_MOTEL_HALF):
        ok = ok and abs((left + right) / 2 - x) < 0.03 and abs((right - left) / 2 - half) < 0.03
    if not ok:
        print("FAIL MOTEL's letters are painted at", [(round(a, 3), round(b, 3)) for a, b in spans],
              "- update NEON_MOTEL_X / NEON_MOTEL_HALF here and in SupernaturalScene.java")
    return ok


def road(p):
    """The road from the bottom left into the distance on the right (the mockup's ground())."""
    p.poly([(-9.6, 108), (96.0, 108), (121.0, HORIZON + 0.5), (115.2, HORIZON + 0.5)])


def road_edge(y, side):
    """The road's left (side 0) or right (side 1) edge at row y, in units."""
    t = (108 - y) / (108 - HORIZON - 0.5)
    return (-9.6 + t * 124.8) if side == 0 else (96.0 + t * 25.0)


def centre_dashes(p):
    """The dashed centre line, shorter and thinner into the distance."""
    x0, y0, x1, y1 = 44.2, 108.0, 118.1, HORIZON + 0.6
    u = 0.0
    while u < 0.98:
        d = 0.06 * (1 - u) ** 1.3 + 0.004
        a, b = u, min(u + d * 0.45, 1.0)
        wdt = 0.9 * (1 - u) + 0.08
        pa = (x0 + (x1 - x0) * a, y0 + (y1 - y0) * a)
        pb = (x0 + (x1 - x0) * b, y0 + (y1 - y0) * b)
        line(p, [pa, pb], wdt)
        u += d


POLES = (0.22, 0.38, 0.52, 0.64, 0.74, 0.82, 0.88)   # along the road's left side, from near to far


def pole_geometry():
    """Each telegraph pole's foot, height and cross-arm half width, in units."""
    out = []
    for u in POLES:
        y = 108 - (108 - HORIZON) * u * 0.98
        x = road_edge(y, 0) - 6.0 * (1 - u) - 1.0
        s = 1 - u * 0.85
        out.append((x, y, 108 * 0.34 * s, 192 * 0.016 * s, s))
    return out


def poles(p):
    for x, y, ht, arm, s in pole_geometry():
        wdt = max(0.18, 0.75 * s)
        p.rect(x - wdt / 2, y - ht, wdt, ht)
        p.rect(x - arm, y - ht * 0.93, arm * 2, max(0.15, 0.45 * s))


def wires(p):
    """Sagging wires between the cross-arms' tips."""
    g = pole_geometry()
    for side in (-1, 1):
        for (xa, ya, ha, aa, sa), (xb, yb, hb, ab, sb) in zip(g, g[1:]):
            pa = (xa + side * aa * 0.85, ya - ha * 0.93)
            pb = (xb + side * ab * 0.85, yb - hb * 0.93)
            mid = ((pa[0] + pb[0]) / 2, (pa[1] + pb[1]) / 2 + 1.2 * sa)
            pts = [((1 - t) ** 2 * pa[0] + 2 * (1 - t) * t * mid[0] + t * t * pb[0],
                    (1 - t) ** 2 * pa[1] + 2 * (1 - t) * t * mid[1] + t * t * pb[1]) for t in np.linspace(0, 1, 24)]
            line(p, pts, max(0.08, 0.16 * sa))


# ---- the motel ----------------------------------------------------------------------------------------------------
def motel(img, day, rng):
    x0, x1, roof, eaves, ground = MOTEL
    wall = c.shape(lambda p: p.rect(x0, eaves, x1 - x0, ground - eaves))
    if day:
        # the wall in the low sun from the left: warm, a little darker to the right
        grad = c.vgradient([(0, "#8A643E"), (1, "#5E4228")], top=eaves / 108, bottom=ground / 108)
        xs = np.clip((XS - x0) / (x1 - x0), 0, 1)[None, :, None]
        c.blend(img, wall, grad * (1 - 0.25 * xs))
    else:
        c.blend(img, wall, c.vgradient([(0, "#1C1E25"), (1, "#121318")], top=eaves / 108, bottom=ground / 108))
    # the end wall right of the last door, set off by a pilaster
    c.blend(img, rect_mask((173.2, eaves, 0.7, ground - eaves)), "#4A321E" if day else "#0E0F13")
    # the flat roof with its overhang, and the walkway in front
    c.blend(img, rect_mask((x0 - ROOF_OVERHANG, roof, x1 - x0 + 2 * ROOF_OVERHANG, eaves - roof + 0.2)),
            "#4E3520" if day else "#0F1014")
    c.blend(img, rect_mask((x0 - ROOF_OVERHANG, roof, x1 - x0 + 2 * ROOF_OVERHANG, 0.35)),
            "#C08A52" if day else "#262A33")
    c.blend(img, rect_mask((x0 - 1.0, ground - 0.3, x1 - x0 + 2.0, 1.1)), "#6E5034" if day else "#16171C")
    ww, wh, wy = WINDOW
    dx, dw, dh, dy = DOOR
    for i, x in enumerate(ROOM_X):
        lit = not day and i in LIT_ROOMS
        win = rect_mask((x, wy, ww, wh))
        if lit:
            c.blend(img, win, c.rgb("#FFC57C") * 0.86)
            # the curtain's fold and the glow on the wall round it
            c.blend(img, rect_mask((x + ww * 0.62, wy, 0.35, wh)) * 0.35, "#B07040")
            c.radial(img, x + ww / 2, wy + wh / 2, 6.0, [(0, "#FFB060", .16), (1, "#FFB060", 0)], ry=4.5, mode="add")
        else:
            c.blend(img, win, "#3A2616" if day else "#0B0C10")
            if day:   # the low sun in the glass
                c.blend(img, rect_mask((x + 0.3, wy + 0.3, ww * 0.3, wh - 0.6)) * 0.25, "#E0A060")
        c.blend(img, rect_mask((x - 0.25, wy - 0.25, ww + 0.5, 0.25)), "#2A1A10" if day else "#08090B")
        c.blend(img, rect_mask((x + dx, dy, dw, dh)), "#2A1A10" if day else "#0A0B0E")
        c.blend(img, c.shape(lambda p: p.circle(x + dx + dw * 0.8, dy + dh * 0.55, 0.16)), "#B08050" if day else "#3A3E48")
        # the room's number plate as a small light square over the door
        c.blend(img, rect_mask((x + dx + dw * 0.3, dy - 0.95, dw * 0.4, 0.5)), "#C8A070" if day else "#2C2F38")


def warding(img, day, rng):
    """The sprayed warding circle on the end wall and the small marks over the doors (own, generic shapes)."""
    cx, cy, r = WARD
    marks = []
    for k in range(12):
        a = k * TAU / 12 + 0.13
        x, y, s = cx + np.cos(a) * r * 0.89, cy + np.sin(a) * r * 0.89, r * 0.075
        marks.append([(x - s, y - s), (x + s * (rng.random() - 0.5) * 2, y + s)])
        marks.append([(x + s, y - s * rng.random()), (x - s * rng.random(), y)])

    def ring(rr):
        return [(cx + np.cos(t) * rr, cy + np.sin(t) * rr) for t in np.linspace(0, TAU, 73)]

    def polygon(n, rr):
        return [(cx + np.cos(-np.pi / 2 + k * TAU / n) * rr, cy + np.sin(-np.pi / 2 + k * TAU / n) * rr)
                for k in range(n + 1)]

    def draw(p):
        lw = 0.32
        line(p, ring(r), lw)
        line(p, ring(r * 0.78), lw)
        line(p, polygon(6, r * 0.74), lw)
        # a triangle standing on its point and a small ring in the middle (never a road sign's triangle and bar)
        line(p, [(cx + np.cos(np.pi / 2 + k * TAU / 3) * r * 0.5, cy + np.sin(np.pi / 2 + k * TAU / 3) * r * 0.5)
                 for k in range(4)], lw)
        line(p, ring(r * 0.12), lw * 0.8)
        for m in marks:
            line(p, m, lw * 0.75)
        for mx, my in DOOR_MARKS:
            s = DOOR_MARK_R
            line(p, [(mx - s, my), (mx + s, my)], 0.2)
            line(p, [(mx, my - s), (mx, my + s)], 0.2)
            line(p, [(mx + np.cos(t) * s * 0.9, my + np.sin(t) * s * 0.9) for t in np.linspace(0, TAU * 0.75, 20)],
                 0.2)

    paint = c.shape(draw, blur=0.05)
    spray = c.shape(draw, blur=0.35)          # the overspray round the lines
    if day:
        c.blend(img, spray * 0.18, "#7A1A14")
        c.blend(img, paint * 0.8, "#9A2219")
    else:
        c.blend(img, spray * 0.25, "#3A0A0A")
        c.blend(img, paint * 0.6, "#A42424")


def sign(img, day):
    """The motel's sign on its pole: off by day, its neon lit steadily at night (the overlay flickers it)."""
    sx, sy, sw, sh = SIGN
    px, foot = SIGN_POLE
    c.blend(img, rect_mask((px - SIGN_POLE_HALF, sy + sh - 0.2, 2 * SIGN_POLE_HALF, foot - sy - sh + 0.2)),
            "#2A1A10" if day else "#08090B")
    c.blend(img, rect_mask((sx, sy, sw, sh)), "#3A2A1A" if day else "#0E1014")
    frame = c.shape(lambda p: [p.rect(sx, sy, sw, 0.35), p.rect(sx, sy + sh - 0.35, sw, 0.35),
                               p.rect(sx, sy, 0.35, sh), p.rect(sx + sw - 0.35, sy, 0.35, sh)])
    c.blend(img, frame, "#6A4A2A" if day else "#1C2028")
    motel_text = text_mask("MOTEL", SIGN_FONT, NEON_MOTEL, fit_width=False)
    vacancy = text_mask("VACANCY", SIGN_FONT, NEON_VACANCY, fit_width=False)
    if day:
        c.blend(img, motel_text * 0.9, "#7C4A3E")
        c.blend(img, vacancy * 0.9, "#465A5F")
    else:
        mx, my, mw, mh = NEON_MOTEL
        c.radial(img, mx + mw / 2, my + mh / 2, 20, [(0, "#FF465A", .16), (1, "#FF465A", 0)], ry=12, mode="add")
        c.add(img, blurred(motel_text, 0.9) * 0.9, "#FF3B4A")
        c.blend(img, motel_text, "#FF6A76")
        c.add(img, blurred(vacancy, 0.6) * 0.6, "#3BC8E0")
        c.blend(img, vacancy * 0.85, "#7FE6F2")


def lettering(img, day):
    """"SUPERNATURAL" in Courier Prime over the motel (the logo picture of spec § 4.4 takes its place on the TV)."""
    mask = text_mask("SUPERNATURAL", LETTERING_FONT, LETTERING)
    if day:
        c.blend(img, mask * 0.78, "#28180E")
    else:
        c.add(img, blurred(mask, 0.8) * 0.35, "#FF7828")
        c.blend(img, mask * 0.78, "#E6D2B4")


def sky_corners(img):
    """The painted sky's colour at the lettering's four corners (just inside them), as '#RRGGBB'."""
    x, y, bw, bh = LETTERING
    out = []
    for px, py in ((x + 0.3, y + 0.3), (x + bw - 0.3, y + 0.3), (x + 0.3, y + bh - 0.3), (x + bw - 0.3, y + bh - 0.3)):
        v = img[int(py * c.U), int(px * c.U)]
        out.append("#%02X%02X%02X" % tuple(int(round(float(k))) for k in np.clip(v, 0, 255)))
    return tuple(out)


def check_sky(img, expected, mood):
    """The sky behind the lettering must be what SupernaturalScene.java says (the overlay covers the lettering with
    it); a change in the sky needs both updated."""
    got = sky_corners(img)
    for g, e in zip(got, expected):
        if np.abs(c.rgb(g) - c.rgb(e)).max() > 2:
            print(f"FAIL sky behind the lettering ({mood}): {got}, expected {expected}")
            return False
    return True


# ---- the car ------------------------------------------------------------------------------------------------------
def car_body_outline():
    """The body's side, facing left: a long hood, a low glasshouse set back, a long trunk deck, in units."""
    x, gy, L, H = CAR
    return [(x + 0.2, gy - 2.0), (x - 0.25, gy - 3.0), (x - 0.15, gy - 4.9), (x + 0.4, gy - 5.75),
            (x + L * 0.12, gy - 5.95), (x + L * 0.33, gy - 6.15), (x + L * 0.36, gy - 6.5), (x + L * 0.43, gy - 9.2),
            (x + L * 0.46, gy - H), (x + L * 0.66, gy - H), (x + L * 0.70, gy - 9.3), (x + L * 0.80, gy - 6.55),
            (x + L * 0.90, gy - 6.3), (x + L * 0.985, gy - 6.1), (x + L * 1.003, gy - 5.2),
            (x + L * 1.004, gy - 3.0), (x + L * 0.995, gy - 2.0)]


WHEELS = (0.18, 0.80)
WHEEL_R = 2.55


def car(img, day):
    x, gy, L, H = CAR
    # its shadow on the ground
    c.blend(img, c.shape(lambda p: p.ellipse(x + L * 0.5, gy - 0.1, L * 0.56, 1.3), blur=0.6) * 0.75, "#000000")
    body = c.shape(lambda p: p.poly(car_body_outline()), blur=0.04)
    top_col = "#4A4038" if day else "#2A2E36"
    c.blend(img, body, c.vgradient([(0, top_col), (0.32, "#0A0B0D"), (1, "#030304")],
                                    top=(gy - H) / 108, bottom=(gy - 2) / 108))
    # the shoulder's highlight along the top of the doors
    c.add(img, c.shape(lambda p: p.rect(x + 1.0, gy - 5.75, L - 2.0, 0.35), blur=0.15) * body * (0.35 if day else 0.25),
          "#FFD8A0" if day else "#7A8CA8")
    # the side windows, the B-pillar between them and chrome round them
    front_win = [(x + L * 0.375, gy - 6.3), (x + L * 0.435, gy - 9.0), (x + L * 0.535, gy - 9.15),
                 (x + L * 0.535, gy - 6.3)]
    rear_win = [(x + L * 0.555, gy - 9.15), (x + L * 0.655, gy - 9.15), (x + L * 0.685, gy - 9.0),
                (x + L * 0.765, gy - 6.3), (x + L * 0.555, gy - 6.3)]
    glass = c.shape(lambda p: [p.poly(front_win), p.poly(rear_win)], blur=0.04)
    c.blend(img, glass * (0.8 if day else 0.9), "#6E6252" if day else "#1E2630")
    if day:   # the sky in the glass: brighter at the top
        c.add(img, glass * c.shape(lambda p: p.rect(x, gy - 9.3, L, 1.2), blur=0.4) * 0.25, "#FFC880")
    chrome = "#F2D2A2" if day else "#9AAAC0"
    c.blend(img, c.shape(lambda p: [line(p, front_win + [front_win[0]], 0.16), line(p, rear_win + [rear_win[0]], 0.16)],
                         blur=0.02) * 0.65, chrome)
    # the belt line, door lines and handles (four doors), bumpers
    c.blend(img, c.shape(lambda p: line(p, [(x + 1.6, gy - 5.15), (x + L * 0.97, gy - 5.0)], 0.2), blur=0.03) * 0.85,
            chrome)
    c.blend(img, c.shape(lambda p: [line(p, [(x + L * fx, gy - 6.2), (x + L * fx, gy - 2.3)], 0.12)
                                    for fx in (0.355, 0.545, 0.77)], blur=0.03) * 0.8, "#000000")
    c.blend(img, c.shape(lambda p: [p.rect(x + L * fx, gy - 4.6, 1.0, 0.28) for fx in (0.49, 0.705)], blur=0.03) * 0.8,
            chrome)
    c.blend(img, c.shape(lambda p: [p.rect(x - 0.55, gy - 3.35, 4.0, 0.75), p.rect(x + L - 3.4, gy - 3.35, 3.95, 0.75)],
                         blur=0.05) * 0.9, chrome)
    # a thin chrome strip low along the sills
    c.blend(img, c.shape(lambda p: line(p, [(x + L * 0.26, gy - 2.45), (x + L * 0.72, gy - 2.45)], 0.12), blur=0.03)
            * 0.5, chrome)
    # the front lamp and the tail light
    hx, hy = CAR_HEADLIGHT
    c.blend(img, c.shape(lambda p: p.ellipse(hx, hy, 0.45, 0.6), blur=0.04), chrome)
    c.blend(img, c.shape(lambda p: p.ellipse(hx + 0.05, hy, 0.3, 0.42), blur=0.04),
            "#D8D0B8" if day else "#4A5060")
    tx, ty, tw, th = CAR_TAIL_LIGHT
    tail = rect_mask((tx, ty, tw, th), blur=0.04)
    if day:
        c.blend(img, tail, "#6A1410")
    else:
        c.radial(img, tx + tw / 2, ty + th / 2, 3.6, [(0, "#FF2828", .55), (.5, "#FF2828", .15), (1, "#FF2828", 0)],
                 mode="add")
        c.blend(img, tail, "#FF4A3A")
    # wheel arches, tyres, chrome trims
    for f in WHEELS:
        wx, wy = x + L * f, gy - WHEEL_R
        c.blend(img, c.shape(lambda p: p.ellipse(wx, wy - 0.2, WHEEL_R * 1.18, WHEEL_R * 1.1), blur=0.05) * body,
                "#020203")
        c.blend(img, c.shape(lambda p: p.circle(wx, wy, WHEEL_R), blur=0.04), "#050506")
        c.blend(img, c.shape(lambda p: line(p, [(wx + np.cos(t) * WHEEL_R * 0.56, wy + np.sin(t) * WHEEL_R * 0.56)
                                                for t in np.linspace(0, TAU, 40)], WHEEL_R * 0.17), blur=0.04) * 0.85,
                "#E6D2B4" if day else "#8C9AB0")
        c.blend(img, c.shape(lambda p: p.circle(wx, wy, WHEEL_R * 0.22), blur=0.04) * 0.7, chrome)
    if day:   # the low sun on the roof
        gx, gy2 = CAR_ROOF_GLINT
        c.radial(img, gx, gy2, 4.6, [(0, "#FFE0A8", .55), (.4, "#FFC880", .18), (1, "#FFC880", 0)], ry=1.2, mode="add")


# ---- the star on the road -----------------------------------------------------------------------------------------
def star_lines():
    cx, cy, rx, ry = STAR
    circle = [(cx + np.cos(t) * rx, cy + np.sin(t) * ry) for t in np.linspace(0, TAU, 97)]
    pent = [(cx + np.cos(-np.pi / 2 + k * 2 * TAU / 5) * rx * 0.92, cy + np.sin(-np.pi / 2 + k * 2 * TAU / 5) * ry * 0.92)
            for k in range(6)]
    rays = []
    for k in range(16):
        a = k * TAU / 16
        rays.append([(cx + np.cos(a) * rx * 1.05, cy + np.sin(a) * ry * 1.05),
                     (cx + np.cos(a) * rx * STAR_RAYS, cy + np.sin(a) * ry * STAR_RAYS)])
    return circle, pent, rays


def star(img, day):
    circle, pent, rays = star_lines()

    def draw(p):
        line(p, circle, 0.36)
        line(p, pent, 0.32)
        for r in rays:
            line(p, r, 0.3)

    if day:
        chalk = c.shape(draw, blur=0.08)
        c.blend(img, chalk * 0.55, "#E6E0CE")
    else:
        c.add(img, c.shape(draw, blur=0.9) * 0.45, "#FF5A14")
        c.blend(img, c.shape(draw, blur=0.06) * 0.85, "#FF8A38")


# ---- the scenes ---------------------------------------------------------------------------------------------------
def ground(img, day, rng):
    hz = HORIZON
    plain = c.shape(lambda p: p.rect(0, hz, 192, 108 - hz))
    if day:
        c.blend(img, plain, c.vgradient([(0, "#6A4A2A"), (1, "#2A1A10")], top=hz / 108, bottom=1))
    else:
        c.blend(img, plain, c.vgradient([(0, "#14161A"), (1, "#07080A")], top=hz / 108, bottom=1))
    hills = under(hills_line())
    c.blend(img, hills * (YS[:, None] < hz + 0.6), "#5A3C24" if day else "#0C0E12")
    if day:   # the sun's light on the hills' crests and the far plain
        c.radial(img, SUN[0], hz, 70, [(0, "#FFB060", .35), (1, "#FFB060", 0)], ry=8, mode="add")
    c.blend(img, c.shape(road, blur=0.05), "#3A3530" if day else "#16181C")
    c.blend(img, c.shape(centre_dashes, blur=0.03) * (0.75 if day else 0.4), "#E6C878")
    # the shoulder's dust along the road's right edge
    c.blend(img, c.shape(lambda p: p.poly([(96.0, 108), (101.0, 108), (121.8, hz + 0.5), (121.0, hz + 0.5)]), blur=0.3)
            * 0.35, "#8A6A44" if day else "#1A1C21")


def telegraph(img, day):
    """The poles and their wires, in front of the ground: at night drawn after the glowing cracks, which run behind
    the nearer poles."""
    c.blend(img, c.shape(wires, blur=0.02) * 0.8, "#2A1A10" if day else "#050608")
    c.blend(img, c.shape(poles, blur=0.03), "#24160C" if day else "#050608")


def god_rays(img):
    sx, sy, _ = SUN

    def draw(p):
        for a in RAYS:
            a0, a1 = a - RAY_HALF, a + RAY_HALF
            p.poly([(sx, sy), (sx + np.sin(a0) * RAY_LEN, sy - np.cos(a0) * RAY_LEN),
                    (sx + np.sin(a1) * RAY_LEN, sy - np.cos(a1) * RAY_LEN)])

    rays = c.shape(draw, blur=1.2)
    # fading out upwards, so they never light the header
    fade = np.clip((YS - 8) / 40, 0, 1)[:, None] ** 1.3
    c.add(img, rays * fade * 0.20, "#FFE6B4")


def clouds(img, rng):
    """Dark clouds across the night sky, their undersides faintly lit by the red glow on the left; none behind the
    lettering."""
    blobs = []
    for _ in range(60):
        x = float(rng.uniform(-10, 202))
        y = float(rng.uniform(4, 30))
        blobs.append((x, y, float(rng.uniform(5, 14)), float(rng.uniform(1.6, 3.6))))
    for _ in range(18):
        blobs.append((float(rng.uniform(-10, 104)), float(rng.uniform(36, 49)), float(rng.uniform(5, 12)),
                      float(rng.uniform(1.2, 2.6))))
    body = c.shape(lambda p: [p.ellipse(x, y, rx, ry) for x, y, rx, ry in blobs], blur=1.6)
    lx, ly, lw, lh = LETTERING
    keep = 1 - c.shape(lambda p: p.rect(lx - 6, ly - 4, lw + 12, lh + 8), blur=2.0)
    body *= keep
    c.blend(img, body * 0.7, "#1A1F2A")
    under_lit = np.clip(body - np.roll(body, int(-1.2 * c.U), axis=0), 0, 1)
    left = np.clip(1 - XS / 120, 0, 1)[None, :]
    c.add(img, under_lit * left * 0.35, "#5A1A14")


def hell_glow(img):
    hz = HORIZON
    c.radial(img, 38.4, hz + 2, 40, [(0, "#FF4614", .26), (.5, "#B02A0C", .10), (1, "#FF4614", 0)], ry=11, mode="add")
    def draw(p, k):
        # each crack tapers: widest in its middle, a hair at its ends
        for crack in CRACKS:
            n = len(crack) - 1
            for i in range(n):
                line(p, [crack[i], crack[i + 1]], CRACK_W * k * (0.3 + 0.7 * np.sin(np.pi * (i + 0.5) / n)))

    c.add(img, c.shape(lambda p: draw(p, 3.0), blur=1.1) * 0.45, "#E0400C")
    c.add(img, c.shape(lambda p: draw(p, 1.6), blur=0.25) * 0.5, "#FF5A10")
    c.blend(img, c.shape(lambda p: draw(p, 1.0), blur=0.04) * 0.9, "#FFA048")


def wet_reflections(img):
    """The wet ground at night: the neon's red smeared on the road, the tail light under the car."""
    c.radial(img, 116.5, 64.0, 6.0, [(0, "#FF3B4A", .10), (1, "#FF3B4A", 0)], ry=6.0, mode="add")
    tx, ty, tw, th = CAR_TAIL_LIGHT
    c.add(img, c.shape(lambda p: p.rect(tx - 0.2, CAR[1] - 0.2, tw + 0.4, 2.2), blur=0.5) * 0.25, "#FF2828")


def day_scene(rng):
    hz = HORIZON
    img = c.vgradient([(0, "#2A2030"), (.45, "#A8583A"), (1, "#F2B25A")], top=0, bottom=hz / 108)
    # the upper sky darker (the mockup's veil over its top quarter), softly
    c.blend(img, c.shape(lambda p: p.rect(0, 0, 192, 27), blur=6.0) * 0.45, "#000000")
    c.radial(img, SUN[0], SUN[1], 54, [(0, "#FFBE64", .55), (.5, "#FFA050", .16), (1, "#FFBE64", 0)], mode="add")
    god_rays(img)
    c.blend(img, c.shape(lambda p: p.circle(SUN[0], SUN[1], SUN[2]), blur=0.25), "#FFD890")
    c.radial(img, SUN[0], SUN[1], 12, [(0, "#FFF0C8", .45), (1, "#FFF0C8", 0)], mode="add")
    sky = finish(img.copy(), True)
    ground(img, True, rng)
    telegraph(img, True)
    motel(img, True, rng)
    warding(img, True, np.random.default_rng(SEED + 53))
    sign(img, True)
    lettering(img, True)
    star(img, True)
    car(img, True)
    # heat over the road, a few faint warm bands (the overlay makes them shimmer)
    for k in range(5):
        c.add(img, c.shape(lambda p: p.rect(96, hz + 0.6 + k * 0.65, 30, 0.15), blur=0.2) * (0.06 - k * 0.01),
              "#FFDCA0")
    return finish(img, True), sky


def night_scene(rng):
    img = c.vgradient([(0, "#05070C"), (1, "#141A24")], top=0, bottom=HORIZON / 108)
    clouds(img, rng)
    sky = finish(img.copy(), False)
    ground(img, False, rng)
    hell_glow(img)
    telegraph(img, False)
    motel(img, False, rng)
    warding(img, False, np.random.default_rng(SEED + 53))
    sign(img, False)
    lettering(img, False)
    star(img, False)
    wet_reflections(img)
    car(img, False)
    return finish(img, False), sky


def finish(img, day):
    """Darkens the bottom (towards the dock) and both top corners, as the UI over them needs (common.py)."""
    if day:
        shade(img, 78, 104, 0.35)
        corners(img, 0.45)
        c.keep_dark(img, corners=0.5, bottom=0.6)
    else:
        shade(img, 80, 104, 0.4)
        corners(img, 0.3)
        c.keep_dark(img, corners=0.4, bottom=0.5)
    return img


def main():
    """Draws both pictures; with --sky only prints the sky behind the lettering (for LETTERING_SKY_* here and in
    SupernaturalScene.java) without writing anything."""
    day, sky_day = day_scene(np.random.default_rng(SEED))
    night, sky_night = night_scene(np.random.default_rng(SEED + 1))
    if "--sky" in sys.argv:
        print("day", sky_corners(sky_day))
        print("night", sky_corners(sky_night))
        return 0
    ok = check_motel_letters()
    ok = check_sky(sky_day, LETTERING_SKY_DAY, "day") and ok
    ok = check_sky(sky_night, LETTERING_SKY_NIGHT, "night") and ok
    ok = c.export(day, "supernatural_day", seed=GRAIN_DAY) and ok
    ok = c.export(night, "supernatural_night", seed=GRAIN_NIGHT) and ok
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
