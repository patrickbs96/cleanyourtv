# World scenes

The three worlds (Hogwarts, Marvel, Star Wars) each show a day and a night picture behind Home
(`WorldScene.java`; Clean has none). The pictures are drawn by the scripts here from code alone, with
Pillow and NumPy and a fixed seed, so anyone can redraw them byte for byte. No photos, artwork, logos,
characters or film imagery are used: silhouettes and gradients only.

| Script | Writes (to `app/src/main/res/drawable-nodpi/`) |
|---|---|
| `hogwarts/make_scene.py` | `world_hogwarts_night.jpg` (castle on a cliff, lake, moon), `world_hogwarts_day.jpg` (great hall, cloud ceiling, owls) |
| `marvel/make_scene.py` | `world_marvel_night.jpg` (crimson skyline, light streak, beacon tower), `world_marvel_day.jpg` (same skyline in warm backlight) |
| `starwars/make_scene.py` | `world_starwars_day.jpg` (twin suns over dunes, dome, vaporators), `world_starwars_night.jpg` (stars, ringed planet) |

```
tools/venv/Scripts/pip install pillow numpy
for w in hogwarts marvel starwars; do tools/venv/Scripts/python launcher/worlds/$w/make_scene.py; done
tools/venv/Scripts/python launcher/worlds/common.py    # checks all six again
```

`common.py` holds the shared helpers: shapes in the mockup's 192 x 108 units
(`.superpowers/brainstorm/651-1792953360/content/vier-welten-v3.html`), drawn at twice the size and scaled down,
gradients, stars, and the export. Every picture must stay under the launcher's UI: 1920 x 1080, a JPEG of at most
300 KB (the export lowers the quality until it fits), and dark in both top corners (15 % x 15 %) and in the bottom
third, each with a mean luminance of at most 40. Each script prints those numbers and exits with 1 when a picture
breaks them.

Day runs from sunrise until half an hour after sunset at config.xml's `latitude`/`longitude` (wallpaper_moods'
morning and evening), night the rest; the change fades over 300 ms. The moving parts (candles, the reactor,
hyperspace streaks, twinkling) are drawn live by the world's overlay, not baked into the pictures.

## Rebuilding the pictures

Run the three commands above from the repo root, then rebuild the APK (`launcher/build-and-install.sh`); the
pictures are resources, so a theme push does not change them. Each script uses fixed seeds, so an unchanged script
writes the same bytes again:

| World | Shapes (`np.random.default_rng`) | Grain in `common.export(seed=…)` |
|---|---|---|
| Hogwarts | 1903 | night 11, day 12 |
| Marvel | 1963 | night 21, day 22 |
| Star Wars | 1977 | day 31, night 32 |

Rules for a changed or new picture:

- 1920 x 1080, JPEG, **at most 300 KB**.
- **Dark in both top corners** (15 % x 15 %, clock and pills sit there) **and in the bottom third** (the dock),
  mean luminance at most 40 each. `common.py` checks it; a script that exits with 1 has a picture to fix.
- **No logos, no characters or figures, no film imagery, no trademarked lettering**: silhouettes, gradients and
  light only. The worlds are moods, not fan art.
- Moving things belong in the overlay (`HogwartsOverlay`, `MarvelOverlay`, `StarWarsOverlay`), not in the picture.

## Using the worlds on the TV

- **Colour keys** on Home switch at once, with the world's transition: **green Clean, yellow Hogwarts, red Marvel,
  blue Star Wars**. The same key again does nothing.
- **Launcher settings, row "World"** ("Welt"): the same four choices, each behind a dot in its key's colour. The
  world is saved in the launcher's settings and survives theme pushes, `--reset`, reboot and force-stop. Clean is
  the launcher exactly as without worlds.
- **Hidden scene switch**: a long OK on the "World" row opens "Scene: Auto / Day / Night" ("Szene: Auto / Tag /
  Nacht"). Day or Night shows that picture (and Hogwarts' Lumos or Nox, the overlay's day or night variant) at any
  hour, for testing. It is kept in the settings under `world_scene_force` and holds while the launcher's process
  lives: Home rebuilding (a settings change, a colour key) keeps it; the first Home of a new process (after
  `am force-stop`, a crash or a reboot) drops it and is back on Auto. In Clean it has nothing to show.
- **`reduced_motion`** (config.xml or theme): the scene's day/night pictures still change, but the overlays do not
  run, a world switch is a hard cut without its transition, an app starts at once without the world's launch
  effect, and the focus frame moves like Clean's with reduced motion, without sparks, scan, blip or typing. The same holds with
  `animations` off.
