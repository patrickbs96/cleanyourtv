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
| `seasons/make_scene.py` | Jahreskreis' looks, found by name (`WorldScene.drawableName`): `world_seasons_<season>_day.jpg` and `_night.jpg` for winter (snowy hills, firs; by night moon and stars), spring (green hills, a cherry in blossom), summer (the sea in the evening light, a low sun; by night a moon and its path) and autumn (a forest of spires, trees in rust and amber); painted in `seasons/seasons.py`. One `world_seasons_<occasion>.jpg` each for `new_year` (skyline with lit windows over the dock), `carnival` (dark stage, spotlights, pennants, party hats and streamers), `valentine` (evening table at a window: roses, two glasses, a candle), `easter` (pastel morning, painted eggs above the dock), `mothers_day` (a bunch of flowers, a heart card "Mama") and `fathers_day` (meadow, a field path out of the picture on the right, a handcart with a picnic basket); painted in `seasons/occasions_a.py` |

```
tools/venv/Scripts/pip install pillow numpy
for w in hogwarts marvel starwars seasons; do tools/venv/Scripts/python launcher/worlds/$w/make_scene.py; done
tools/venv/Scripts/python launcher/worlds/seasons/make_scene.py winter autumn   # only some of Jahreskreis' looks
tools/venv/Scripts/python launcher/worlds/common.py    # checks them all again
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

Run the commands above from the repo root (the `for` loop draws everything; the line for some of Jahreskreis' looks
is only for redrawing those), then rebuild the APK (`launcher/build-and-install.sh`); the
pictures are resources, so a theme push does not change them. Each script uses fixed seeds, so an unchanged script
writes the same bytes again:

| World | Shapes (`np.random.default_rng`) | Grain in `common.export(seed=…)` |
|---|---|---|
| Hogwarts | 1903 | night 11, day 12 |
| Marvel | 1963 | night 21, day 22 |
| Star Wars | 1977 | day 31, night 32 |
| Jahreskreis: winter, spring, summer, autumn | 2112, 2103, 2106, 2309 (plus fixed seeds for the cherry's crown and the autumn trees) | day 41/43/45/47, night 42/44/46/48 |
| Jahreskreis: New Year, Carnival, Valentine's Day, Easter, Mother's Day, Father's Day | 3101 to 3106 (plus the mockup's seeded random for the skyline, the window's stars and the bouquet) | 51 to 56 |

Rules for a changed or new picture:

- 1920 x 1080, JPEG, **at most 300 KB**.
- **Dark in both top corners** (15 % x 15 %, clock and pills sit there) **and in the bottom third** (the dock),
  mean luminance at most 40 each. `common.py` checks it; a script that exits with 1 has a picture to fix.
- **No logos, no characters or figures, no film imagery, no trademarked lettering**: silhouettes, gradients and
  light only. The worlds are moods, not fan art.
- Moving things belong in the overlay (`HogwartsOverlay`, `MarvelOverlay`, `StarWarsOverlay`; Jahreskreis'
  `SeasonsOverlay` with one `LookMotion` per look, `SeasonMotions` for the seasons' snow, blossoms, fireflies and
  leaves, `OccasionMotionsA` for the fireworks, streamers and confetti, candle and petals, butterflies, motes and
  clouds of New Year to Father's Day), not in the picture; the picture is whole without them (reduced motion).
- **Jahreskreis** (spec § 3): what matters stands right of the app name, directly above the dock (its top edge at
  y 766 of 1080), never behind it and never in the header on the top left (greeting, clock, date, weather). Hills are
  drawn per pixel column, so they meet the right edge without a seam. Night versions are painted dark (OLED rule) and
  shown as they are; only bright looks without a night picture of their own (Easter, Mother's Day …) are dimmed at
  night by the scene (`WorldScene.NIGHT_DIM`).

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
