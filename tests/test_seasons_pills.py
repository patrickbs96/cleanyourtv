"""Jahreskreis: nothing that matters in a look lies under the pills on the top right.

The round pills (inputs, Wi-Fi, settings; activity_main.xml topButtons) sit on the TV at x 1305 to 1810, y 125 to 215
of 1920 x 1080. On the TV they covered the top of the autumn and Halloween moons, the Christmas tree's star, the
Advent window with its paper star and the carnival garland with its streamers. personal.py PILLS and
PersonalMotions.PILLS hold that zone in the scenes' 192 x 108 units; this test keeps every named motif out of it: the
painted ones (launcher/worlds/seasons/*.py) and the overlay's that move over them (OccasionMotionsA/B.java), whose
numbers it reads from the Java and checks against the Python's, so the two cannot drift apart.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORLDS = ROOT / "launcher" / "worlds"
JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"

try:
    import numpy  # noqa: F401
    import PIL  # noqa: F401
    HAVE_SCENES = True
except ImportError:          # the scene modules draw with numpy and Pillow (tools/venv)
    HAVE_SCENES = False

if HAVE_SCENES:
    sys.path.insert(0, str(WORLDS))
    sys.path.insert(0, str(WORLDS / "seasons"))
    import occasions_a  # noqa: E402
    import occasions_b  # noqa: E402
    import personal  # noqa: E402
    import seasons  # noqa: E402


def class_body(source, name):
    """The text of the nested class `name` in a Java file: from its declaration to the next nested class."""
    start = source.index(f"static final class {name} ")
    end = source.find("static final class ", start + 1)
    return source[start:end if end >= 0 else len(source)]


def _number(expr):
    """A Java float literal or a product of them (e.g. `192 * 0.13f`) as a Python float."""
    value = 1.0
    for factor in expr.split("*"):
        m = re.fullmatch(r"\s*(-?\d+(?:\.\d+)?)[fF]?\s*", factor)
        if not m:
            raise ValueError(f"not a plain number: {expr!r}")
        value *= float(m.group(1))
    return value


def java_float(text, name):
    m = re.search(rf"\b{name}\s*=\s*([^,;{{}}]+?)\s*[,;]", text)
    if not m:
        raise AssertionError(f"{name} not found")
    return _number(m.group(1))


def java_floats(text, name):
    """A one- or two-dimensional float array constant, as a list (of lists)."""
    flat = re.search(rf"\b{name}\s*=\s*\{{([^{{}}]*)\}}\s*[,;]", text)
    if flat:
        return [_number(v) for v in flat.group(1).split(",")]
    nested = re.search(rf"\b{name}\s*=\s*\{{((?:\s*\{{[^{{}}]*\}}\s*,?)+)\s*\}}\s*;", text)
    if not nested:
        raise AssertionError(f"{name} not found")
    return [[_number(v) for v in row.split(",")] for row in re.findall(r"\{([^{}]*)\}", nested.group(1))]


def overlaps(box, zone):
    x0, y0, x1, y1 = box
    return x0 < zone[2] and x1 > zone[0] and y0 < zone[3] and y1 > zone[1]


def disc(x, y, r):
    return (x - r, y - r, x + r, y + r)


def _luma(hex_colour):
    s = hex_colour.lstrip("#")
    r, g, b = (int(s[i:i + 2], 16) for i in (0, 2, 4))
    return 0.299 * r + 0.587 * g + 0.114 * b


def halo_light(cx, cy, r, stops, zone):
    """The most light (Rec. 601 luminance, 0-255) a halo painted with c.radial(..., mode="add") adds anywhere under
    the pills: its alpha falls off with the distance, so the most is at the zone's point nearest its centre."""
    px, py = min(max(cx, zone[0]), zone[2]), min(max(cy, zone[1]), zone[3])
    d = ((px - cx) ** 2 + (py - cy) ** 2) ** 0.5 / r
    if d >= stops[-1][0]:
        return 0.0
    for (p0, c0, a0), (p1, c1, a1) in zip(stops, stops[1:]):
        if p0 <= d <= p1:
            t = (d - p0) / (p1 - p0)
            return (a0 + (a1 - a0) * t) * (_luma(c0) + (_luma(c1) - _luma(c0)) * t)
    return 0.0


# A soft halo may reach under the pills, but only as a faint brightening of the dark sky behind them, never as a
# visible light: at most HALO_MAX of 255 luminance added there. The values today are listed in
# test_halos_fade_out_under_the_pills.
HALO_MAX = 24.0


@unittest.skipUnless(HAVE_SCENES, "the scene modules need numpy and Pillow (tools/venv)")
class PillsStayFreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.zone = personal.PILLS
        cls.personal_java = (JAVA / "PersonalMotions.java").read_text(encoding="utf-8")
        cls.a_java = (JAVA / "OccasionMotionsA.java").read_text(encoding="utf-8")
        cls.b_java = (JAVA / "OccasionMotionsB.java").read_text(encoding="utf-8")

    def assertFree(self, name, box):
        self.assertFalse(overlaps(box, self.zone), f"{name} {tuple(round(v, 2) for v in box)} lies under the pills "
                                                   f"{self.zone}")

    def test_the_zone_is_where_the_tv_shows_the_pills(self):
        # x 1305 to 1810 and y 125 to 215 of 1920 x 1080 are 130.5 to 181 and 12.5 to 21.5 units: inside, with a margin.
        x0, y0, x1, y1 = self.zone
        self.assertLessEqual(x0, 130.5)
        self.assertLessEqual(y0, 12.5)
        self.assertGreaterEqual(y1, 21.5)
        self.assertEqual(x1, 192.0)
        self.assertEqual(list(self.zone), java_floats(self.personal_java, "PILLS"))

    def halos(self):
        b = occasions_b
        out = [(f"{look} moon halo", m[0], m[1], *seasons.MOON_HALOS[look]) for look, m in seasons.MOONS.items()]
        out.append(("halloween moon halo", b.MOON_X, b.MOON_Y, *b.MOON_HALO))
        out.append(("st_martin moon halo", *b.MARTIN_MOON[:2], *b.MARTIN_MOON_HALO))
        out.append(("valentine moon halo", *occasions_a.VALENTINE_MOON[:2], *occasions_a.VALENTINE_MOON_HALO))
        out.append(("advent paper star halo", *b.PAPER_STAR, *b.PAPER_STAR_HALO))
        out.append(("christmas star halo", b.TREE_X, b.STAR_Y, *b.STAR_HALO))
        return out

    def test_halos_fade_out_under_the_pills(self):
        # Most light added under the pills, of 255: winter 16.9, spring 23.4, summer 17.7, autumn 13.0, Halloween
        # 20.8, the Christmas star 16.8 painted plus at most 8.4 from the overlay's twinkle; the St. Martin and
        # Valentine moons and the Advent paper star add none.
        for name, x, y, r, stops in self.halos():
            light = halo_light(x, y, r, stops, self.zone)
            self.assertLessEqual(light, HALO_MAX, f"{name} adds {light:.1f} under the pills")
        # The overlay's glow on the star: the paint's colour fading linearly to nothing at STAR_GLOW, at most .35.
        christmas = class_body(self.b_java, "Christmas")
        glow = java_float(christmas, "STAR_GLOW")
        b = occasions_b
        d = max(0.0, b.STAR_Y - self.zone[3]) / glow
        self.assertLessEqual(0.35 * max(0.0, 1 - d) * _luma("#FFE696"), HALO_MAX / 2)

    def test_nikolaus_door(self):
        b = occasions_b
        fr = b.DOOR_FRAME
        self.assertFree("nikolaus door", (b.DOOR_X - fr, b.DOOR_Y - fr, b.DOOR_X + b.DOOR_W + fr, b.WALL_BOTTOM))
        self.assertFree("nikolaus wreath", disc(b.WREATH_X, b.WREATH_Y, b.WREATH_R * 1.2))
        # The panels stay on the leaf, above the doorstep.
        for top, height in b.DOOR_PANELS:
            self.assertLess(b.DOOR_Y + top + height, b.WALL_BOTTOM - 2)

    def test_moons(self):
        for season, (x, y, r) in seasons.MOONS.items():
            self.assertFree(f"{season} moon", disc(x, y, r))
        self.assertFree("halloween moon", disc(occasions_b.MOON_X, occasions_b.MOON_Y, occasions_b.MOON_R))
        self.assertFree("st_martin moon", disc(*occasions_b.MARTIN_MOON))
        self.assertFree("valentine moon", disc(*occasions_a.VALENTINE_MOON))

    def test_christmas_tree_and_star(self):
        b = occasions_b
        self.assertFree("christmas star", disc(b.TREE_X, b.STAR_Y, 3.84))
        for tier in b.tree_tiers():
            xs, ys = [p[0] for p in tier], [p[1] for p in tier]
            self.assertFree("christmas tree", (min(xs), min(ys), max(xs), max(ys)))
        # Still standing above the dock with its presents whole.
        self.assertLess(b.TREE_BASE, seasons.DOCK)
        self.assertLess(b.GIFT_GROUND, seasons.DOCK)
        christmas = class_body(self.b_java, "Christmas")
        for name, value in (("TREE_X", b.TREE_X), ("TREE_TOP", b.TREE_TOP), ("TREE_BASE", b.TREE_BASE),
                            ("TREE_SPREAD", b.TREE_SPREAD)):
            self.assertAlmostEqual(java_float(christmas, name), value, places=4, msg=name)

    def test_advent_window_star_and_lights(self):
        b = occasions_b
        wx, wy, ww, wh = b.ADV_WIN_X, b.ADV_WIN_Y, b.ADV_WIN_W, b.ADV_WIN_H
        self.assertFree("advent window", (wx - 1.5, wy - 1.5, wx + ww + 1.5, wy + wh + 3.3))
        self.assertFree("advent paper star", disc(*b.PAPER_STAR, 3.84))
        self.assertLess(wy + wh + 3.3, b.ADV_TABLE)
        # The string of lights runs above the pills: bulbs and the overlay's glow round them.
        advent = class_body(self.b_java, "Advent")
        glow = java_float(advent, "LIGHT_GLOW")
        for k in range(b.LIGHTS_N):
            x = b.LIGHTS_FROM + k * b.LIGHTS_STEP
            y = float(b.lights_y(x)) + 1.0
            self.assertFree("advent light", (x - glow, y - 1.3, x + glow, y + glow))
        for name, value in (("WIN_X", wx), ("WIN_Y", wy), ("WIN_W", ww), ("WIN_H", wh),
                            ("LIGHTS_FROM", b.LIGHTS_FROM), ("LIGHTS_STEP", b.LIGHTS_STEP)):
            self.assertAlmostEqual(java_float(advent, name), value, places=4, msg=name)

    def test_carnival_garland_and_streamers(self):
        a = occasions_a
        for x, _ in a.garland_pennants():
            top = min(a.garland(x - a.PENNANT_HALF), a.garland(x + a.PENNANT_HALF))
            self.assertFree("carnival pennant", (x - a.PENNANT_HALF, top, x + a.PENNANT_HALF,
                                                 a.garland(x) + a.PENNANT_DROP))
        for k in range(0, 41):
            x = self.zone[0] + (a.GARLAND[1] - self.zone[0]) * k / 40
            self.assertGreaterEqual(a.garland(x), self.zone[3], f"the garland's string at x {x:.1f}")
        carnival = class_body(self.a_java, "Carnival")
        self.assertEqual(list(a.GARLAND), java_floats(carnival, "GARLAND"))
        sway = java_float(carnival, "SWAY") * 192 + 0.004 * 192
        lengths = java_floats(carnival, "STREAMER_LENGTH")
        for fx, length in zip(java_floats(carnival, "STREAMER_X"), lengths):
            x = fx * 192
            top = a.garland(x)
            self.assertFree("carnival streamer", (x - sway, top, x + sway, top + length * 108))
            self.assertLess(top + length * 108, seasons.DOCK)

    def test_fireworks_burst_below_the_pills(self):
        fireworks = class_body(self.a_java, "Fireworks")
        reach = (java_float(fireworks, "R_MAX") + java_float(fireworks, "SPARK")) * 192
        for fx, fy in zip(java_floats(fireworks, "X"), java_floats(fireworks, "Y")):
            self.assertFree("firework", disc(fx * 192, fy * 108, reach))

    def test_halloween_bats_fly_below_the_pills(self):
        halloween = class_body(self.b_java, "Halloween")
        s = java_float(halloween, "BAT_S") * 192
        top = (java_float(halloween, "BAT_TOP") - java_float(halloween, "BAT_BOB")) * 108 - s * 0.6
        bottom = (java_float(halloween, "BAT_TOP") + java_float(halloween, "BAT_ROWS")
                  + java_float(halloween, "BAT_BOB")) * 108 + s * 0.6
        self.assertFree("halloween bats", (java_float(halloween, "BAT_LEFT") * 192 - 2 * s, top, 192.0, bottom))

    def test_birthday_garlands(self):
        for word, x0, x1, y0, y1, sag, _ in personal.GARLANDS:
            for _, x, y, sw, sh, angle in personal.pennants(word, x0, x1, y0, y1, sag):
                corners = [personal.turn(x, y, angle, px, py) for px, py in ((-sw, 0), (sw, 0), (0, sh))]
                xs, ys = [p[0] for p in corners], [p[1] for p in corners]
                self.assertFree(f"{word} pennant", (min(xs), min(ys), max(xs), max(ys)))
        java = java_floats(class_body(self.personal_java, "Birthday"), "GARLANDS")
        self.assertEqual([list(g[1:6]) for g in personal.GARLANDS], java)
        wedding = class_body(self.personal_java, "Wedding")
        self.assertAlmostEqual(java_float(wedding, "ARCH_H"), personal.ARCH_H, places=4)


if __name__ == "__main__":
    unittest.main()
