"""Supernatural's scene script and SupernaturalScene.java name the same numbers.

launcher/worlds/supernatural/make_scene.py paints the lettering, the car's lamps, the star, the warding marks, the
neon, the rays and the cracks at numbers named in capitals; SupernaturalScene.java holds the same numbers for the
overlay, the headlights at an app start and the logo picture (spec § 4.4), which draw over them. When one side
changes and the other does not, the light lands beside the thing it should light. This reads both files as text (no
Pillow or NumPy needed) and compares every shared name.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import ast
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "launcher" / "worlds" / "supernatural" / "make_scene.py"
JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "SupernaturalScene.java"

SHARED = ("HORIZON", "LETTERING", "LETTERING_SKY_DAY", "LETTERING_SKY_NIGHT", "CAR", "CAR_HEADLIGHT", "CAR_TAIL_LIGHT",
          "CAR_ROOF_GLINT", "STAR", "STAR_RAYS", "WARD", "DOOR_MARKS", "DOOR_MARK_R", "SIGN", "NEON_MOTEL",
          "NEON_VACANCY", "NEON_MOTEL_X", "SUN", "RAYS", "RAY_HALF", "RAY_LEN", "CRACKS", "CRACK_W", "CRACK_AREA")


def python_values():
    """The script's top-level NAME = literal (and A, B = literal, literal) assignments."""
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


def java_value(text, name):
    """NAME's value in the Java file as Python: {…} as lists, 1.5f as 1.5, 0xFFRRGGBB as '#RRGGBB'."""
    m = re.search(r"\b" + name + r"\s*=\s*", text)
    if not m:
        return None
    i, depth = m.end(), 0
    j = i
    while j < len(text):
        ch = text[j]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
        elif ch in ",;" and depth == 0:
            break
        j += 1
    raw = text[i:j]
    raw = re.sub(r"0xFF([0-9A-Fa-f]{6})", lambda h: '"#' + h.group(1).upper() + '"', raw)
    raw = re.sub(r"(\d)f\b", r"\1", raw)
    return ast.literal_eval(raw.replace("{", "[").replace("}", "]"))


def flat(value):
    """Nested tuples and lists as one flat list, for comparing a Python tuple of points to a Java flat array."""
    if isinstance(value, (list, tuple)):
        return [x for v in value for x in flat(v)]
    return [value]


class SupernaturalSceneNumbersTest(unittest.TestCase):
    def test_every_shared_number_is_the_same_on_both_sides(self):
        py = python_values()
        java = JAVA.read_text(encoding="utf-8")
        for name in SHARED:
            with self.subTest(name=name):
                self.assertIn(name, py, f"make_scene.py has no {name}")
                jv = java_value(java, name)
                self.assertIsNotNone(jv, f"SupernaturalScene.java has no {name}")
                a, b = flat(py[name]), flat(jv)
                self.assertEqual(len(a), len(b), name)
                for x, y in zip(a, b):
                    if isinstance(x, str):
                        self.assertEqual(x.upper(), y, name)
                    else:
                        self.assertAlmostEqual(float(x), float(y), places=4, msg=name)

    def test_cracks_keep_their_shape(self):
        """Python's cracks are point pairs, Java's flat arrays: the same number of cracks, the same points each."""
        py = python_values()["CRACKS"]
        java = java_value(JAVA.read_text(encoding="utf-8"), "CRACKS")
        self.assertEqual([len(c) * 2 for c in py], [len(c) for c in java])


if __name__ == "__main__":
    unittest.main()
