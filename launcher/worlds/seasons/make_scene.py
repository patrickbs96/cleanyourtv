#!/usr/bin/env python3
"""Draws Jahreskreis' scenes from code alone (Pillow + NumPy, fixed seeds), one entry point for all its looks:

    python launcher/worlds/seasons/make_scene.py               # every look
    python launcher/worlds/seasons/make_scene.py winter autumn # only these

Writes world_seasons_<look>_day.jpg and world_seasons_<look>_night.jpg for the four seasons (seasons.py) and one
world_seasons_<look>.jpg for each occasion (occasions_a.py, New Year to Father's Day) to
app/src/main/res/drawable-nodpi/; WorldScene finds them by name (drawableName). Each picture is checked as it is
written (common.py: 1920 x 1080, at most 300 KB, dark top corners and bottom third); the script exits with 1 when one
fails, and with 2 for a look it does not know.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import common as c  # noqa: E402
import occasions_a  # noqa: E402
import seasons  # noqa: E402

# look id (Look.id in Look.java): the seasons as (painter(day, rng), shape seed, grain seed by day, grain seed by
# night), the occasions as (painter(rng), shape seed, grain seed)
LOOKS = {**seasons.SEASONS, **occasions_a.OCCASIONS}


def draw(look):
    # A fresh generator for each picture, so drawing one look alone writes the same bytes as drawing all.
    if look in seasons.SEASONS:
        painter, seed, day_grain, night_grain = LOOKS[look]
        ok = c.export(painter(True, np.random.default_rng(seed)), f"seasons_{look}_day", seed=day_grain)
        return c.export(painter(False, np.random.default_rng(seed)), f"seasons_{look}_night", seed=night_grain) and ok
    painter, seed, grain = LOOKS[look]
    return c.export(painter(np.random.default_rng(seed)), f"seasons_{look}", seed=grain)


def main(argv):
    looks = argv or list(LOOKS)
    unknown = [look for look in looks if look not in LOOKS]
    if unknown:
        print(f"unknown look: {', '.join(unknown)} (known: {', '.join(LOOKS)})")
        return 2
    ok = True
    for look in looks:
        ok = draw(look) and ok
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
