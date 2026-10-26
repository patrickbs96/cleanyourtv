"""Middle-earth's scene script and MiddleEarthScene.java name the same numbers.

launcher/worlds/middleearth/make_scene.py paints the chimney, the mist's valleys, the crater and its lava channels, the
tower with its eye, the ground the company walks on and the free logo area at numbers named in capitals;
MiddleEarthScene.java holds the same numbers for the overlay, the company and the logo picture (spec § 4.4), which
draw over them. When one side changes and the other does not, the smoke leaves the sky beside the chimney or the eye
glows beside the tower. This reads both files as text (no Pillow or NumPy needed) and compares every shared name;
with Pillow and NumPy it also checks that the scene keeps the logo area and the pills' zone inside the screen's free
places.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import unittest
from pathlib import Path

from test_supernatural_scene import flat, java_value

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "launcher" / "worlds" / "middleearth" / "make_scene.py"
JAVA_DIR = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
JAVA = JAVA_DIR / "MiddleEarthScene.java"
PERSONAL_MOTIONS = JAVA_DIR / "PersonalMotions.java"

SHARED = ("DOCK", "LOGO", "LOGO_FREE", "CHIMNEY_TOP", "MIST_MORNING", "MIST_DAY", "CRATER", "LAVA", "LAVA_W",
          "TOWER_TOP", "EYE", "CONE_ORIGIN", "DARKEST_MORNING", "DARKEST_DAY", "DARKEST_NIGHT")


def python_values():
    """The script's top-level NAME = literal (and A, B = literal, literal) assignments."""
    import ast
    out = {}
    for node in ast.parse(SCRIPT.read_text(encoding="utf-8")).body:
        if not isinstance(node, ast.Assign) or len(node.targets) != 1:
            continue
        target = node.targets[0]
        try:
            value = ast.literal_eval(node.value)
        except ValueError:
            continue
        if isinstance(target, ast.Name):
            out[target.id] = value
        elif isinstance(target, ast.Tuple) and all(isinstance(t, ast.Name) for t in target.elts):
            out.update(zip((t.id for t in target.elts), value))
    return out


class MiddleEarthSceneNumbersTest(unittest.TestCase):
    def assert_same(self, name, py_value, java_text, java_name=None):
        jv = java_value(java_text, java_name or name)
        self.assertIsNotNone(jv, f"no {java_name or name} on the Java side")
        a, b = flat(py_value), flat(jv)
        self.assertEqual(len(a), len(b), name)
        for x, y in zip(a, b):
            if isinstance(x, str):
                self.assertEqual(x.upper(), y, name)
            else:
                self.assertAlmostEqual(float(x), float(y), places=4, msg=name)

    def test_every_shared_number_is_the_same_on_both_sides(self):
        py = python_values()
        java = JAVA.read_text(encoding="utf-8")
        for name in SHARED:
            with self.subTest(name=name):
                self.assertIn(name, py, f"make_scene.py has no {name}")
                self.assert_same(name, py[name], java)

    def test_lava_keeps_its_shape(self):
        """Python's channels are point pairs, Java's flat arrays: the same number of channels, the same points each."""
        py = python_values()["LAVA"]
        java = java_value(JAVA.read_text(encoding="utf-8"), "LAVA")
        self.assertEqual([len(ch) * 2 for ch in py], [len(ch) for ch in java])

    def test_pills_are_where_personal_motions_keeps_them(self):
        self.assert_same("PILLS", python_values()["PILLS"], PERSONAL_MOTIONS.read_text(encoding="utf-8"))

    def test_logo_area_is_the_spec_s(self):
        """Spec § 4.4: centred at x 57 %, y 11 %, at most 18 % wide and 8 % high, inside the free area (x 48 to
        66 %, y 4 to 18 %)."""
        py = python_values()
        x, y, w, h = py["LOGO"]
        self.assertAlmostEqual((x + w / 2) / 192, 0.57, places=4)
        self.assertAlmostEqual((y + h / 2) / 108, 0.11, places=4)
        self.assertAlmostEqual(w / 192, 0.18, places=4)
        self.assertAlmostEqual(h / 108, 0.08, places=4)
        fx, fy, fw, fh = py["LOGO_FREE"]
        self.assertEqual([round(v, 4) for v in (fx / 192, fy / 108, (fx + fw) / 192, (fy + fh) / 108)],
                         [0.48, 0.04, 0.66, 0.18])
        self.assertTrue(fx <= x and fy <= y and x + w <= fx + fw and y + h <= fy + fh)

    def test_motifs_stay_out_of_the_free_places(self):
        """The tower's top and the chimney lie below the pills and the header, right of the app name; the crater
        below the header."""
        py = python_values()
        pills_bottom, header_bottom = py["PILLS"][3], py["HEADER"][3]
        self.assertGreater(py["TOWER_TOP"][1], pills_bottom)
        self.assertGreater(py["CHIMNEY_TOP"][1], pills_bottom)
        self.assertGreater(py["CRATER"][1], header_bottom)
        self.assertLess(py["EYE"][1] + py["EYE"][3], py["DOCK"])
        self.assertEqual(py["CONE_ORIGIN"], py["EYE"][:2])


def drawing_module():
    """make_scene.py itself (it needs Pillow and NumPy), or None without them."""
    try:
        import numpy  # noqa: F401
        import PIL  # noqa: F401
    except ImportError:
        return None
    import importlib.util
    import sys
    for extra in (SCRIPT.parent.parent, SCRIPT.parent.parent / "seasons"):
        if str(extra) not in sys.path:
            sys.path.insert(0, str(extra))
    spec = importlib.util.spec_from_file_location("middleearth_make_scene", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class MiddleEarthDrawingTest(unittest.TestCase):
    """The drawing's own shapes, without painting a whole scene."""

    @classmethod
    def setUpClass(cls):
        cls.scene = drawing_module()
        if cls.scene is None:
            raise unittest.SkipTest("needs Pillow and NumPy")

    def test_mountains_stay_below_the_free_places(self):
        for args in ((50.0, 15.0, 11, 0.9), (57.0, 10.0, 17, 0.9), (63.0, 6.0, 23, 0.6)):
            self.assertGreaterEqual(float(self.scene.mountain_line(*args).min()), 35.5 - 1e-3)

    def test_tower_flank_reaches_the_top(self):
        """The tower's horns end at TOWER_TOP and its foot stands on the dock."""
        ys = [y for _, y in self.scene.TOWER_FLANK]
        self.assertAlmostEqual(min(ys), self.scene.TOWER_TOP[1])
        self.assertGreater(max(ys), self.scene.DOCK)


if __name__ == "__main__":
    unittest.main()
