# World scenes

The themed worlds (Hogwarts, Marvel, Star Wars, Supernatural) each show a day and a night picture behind Home
(`WorldScene.java`; Clean has none); Middle-earth shows a morning, a day and a night picture. The pictures are drawn by the scripts here from code alone, with
Pillow and NumPy and a fixed seed, so anyone can redraw them byte for byte. No photos, artwork, logos,
characters or film imagery are used: silhouettes and gradients only.

| Script | Writes (to `app/src/main/res/drawable-nodpi/`) |
|---|---|
| `hogwarts/make_scene.py` | `world_hogwarts_night.jpg` (castle on a cliff, lake, moon), `world_hogwarts_day.jpg` (great hall, cloud ceiling, owls) |
| `marvel/make_scene.py` | `world_marvel_night.jpg` (crimson skyline, light streak, beacon tower), `world_marvel_day.jpg` (same skyline in warm backlight) |
| `starwars/make_scene.py` | `world_starwars_day.jpg` (twin suns over dunes, dome, vaporators), `world_starwars_night.jpg` (stars, ringed planet) |
| `supernatural/make_scene.py` | `world_supernatural_day.jpg` ("Himmel": golden hour, rays of light, a country road with telegraph poles, a motel with its sign off, a long black late-sixties four-door without a badge, a chalk star in a circle on the road, red-sprayed warding marks), `world_supernatural_night.jpg` ("Hölle": clouds, red-glowing cracks on the left horizon, the neon and some windows lit, the star glowing like embers, the tail light red). The word "SUPERNATURAL" over the motel is set in Courier Prime (OFL), no logo; the numbers the overlay draws over are shared with `SupernaturalScene.java` (`tests/test_supernatural_scene.py`) |
| `middleearth/make_scene.py` | Middle-earth's three moods, found by name (`WorldScene.drawableName`): `world_middleearth_morning.jpg` ("Hügelland": green hills with blades of grass, a patchwork of fields with hedges, a round yellow door in the hillside with round lit windows and a chimney, a great tree, a winding path with a fence and flowers), `world_middleearth_day.jpg` ("Neblige Berge": three ranges with rock strata and snow, two rows of conifers, gnarled old trees, a slim dark tower in the haze) and `world_middleearth_night.jpg` ("Dunkler Turm": layers of ash, a volcano with hatched flanks and three lava channels, a jagged black tower in stepped tiers with buttresses, glimmering windows and a crenellated crown with a dim eye set into it, an army of spears). Layered silhouettes with a baked-in film grain; the logo area at the top in the middle, the header and the pills show only sky. The numbers the overlay and the company draw over (chimney, mist bands, crater, lava, eye, cone, dock, header, logo area, darkest tones) are shared with `MiddleEarthScene.java` (`tests/test_middleearth_scene.py`) |
| `seasons/make_scene.py` | Jahreskreis' looks, found by name (`WorldScene.drawableName`): `world_seasons_<season>_day.jpg` and `_night.jpg` for winter (snowy hills, firs; by night moon and stars), spring (green hills, a cherry in blossom), summer (the sea in the evening light, a low sun; by night a moon and its path) and autumn (a forest of spires, trees in rust and amber); painted in `seasons/seasons.py`. One `world_seasons_<occasion>.jpg` each for `new_year` (skyline with lit windows over the dock), `carnival` (dark stage, spotlights, pennants, party hats and streamers), `valentine` (evening table at a window: roses, two glasses, a candle), `easter` (pastel morning, painted eggs above the dock), `mothers_day` (a bunch of flowers, a heart card "Mama") and `fathers_day` (meadow, a field path out of the picture on the right, a handcart with a picnic basket); painted in `seasons/occasions_a.py`. And for `halloween` (orange moon, bare tree, ribbed pumpkins with stems and carved faces), `st_martin` (children with paper lanterns at dusk, a town far off), `nikolaus_eve` and `nikolaus` (front door with wreath and lantern in the snow, two polished boots: empty, then filled with mandarins, nuts, chocolate and a candy cane), `advent` (wreath with fir, berries and ribbons on a wooden table, four unlit candles, cookies, a snowy window with a paper star, a string of lights) and `christmas` (decorated tree with baubles, tinsel, lights and a star, presents under it, snowy ground); painted in `seasons/occasions_b.py`. And for the personal days `birthday` (two pennant garlands "HAPPY" / "BIRTHDAY", a cake with four candles, three balloons whose strings run to the dock's top edge, confetti on the table), `anniversary` (desert at sunset, two suns, three rows of dunes, stars, a small projector in the sand) and `wedding` (garden evening, a rose arch with greenery, roses and lights, two interlocking golden rings); painted in `seasons/personal.py`, with no names and no numbers in them |

```
tools/venv/Scripts/pip install pillow numpy
for w in hogwarts marvel starwars supernatural middleearth seasons; do tools/venv/Scripts/python launcher/worlds/$w/make_scene.py; done
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
| Supernatural | 1967 (day), 1968 (night); the warding marks 2020 | day 81, night 82 |
| Middle-earth | 1954 (morning), 1955 (day), 1956 (night); the volcano's outline 1961 | its own film grain (`make_scene.export`): morning 91, day 92, night 93 |
| Jahreskreis: winter, spring, summer, autumn | 2112, 2103, 2106, 2309 (plus fixed seeds for the cherry's crown and the autumn trees) | day 41/43/45/47, night 42/44/46/48 |
| Jahreskreis: New Year, Carnival, Valentine's Day, Easter, Mother's Day, Father's Day | 3101 to 3106 (plus the mockup's seeded random for the skyline, the window's stars and the bouquet) | 51 to 56 |
| Jahreskreis: Halloween, St. Martin, St. Nicholas' eve and day, Advent, Christmas | 3107 to 3111 (the eve and the day share 3109; plus the mockup's seeded random for the tree's baubles and lights) | 57 to 62 |
| Jahreskreis: birthday, anniversary, wedding day | 3201 to 3203 (plus the mockup's seeded random for the anniversary's stars and the arch's leaves and roses) | 71 to 73 |

Rules for a changed or new picture:

- 1920 x 1080, JPEG, **at most 300 KB**.
- **Dark in both top corners** (15 % x 15 %, clock and pills sit there) **and in the bottom third** (the dock),
  mean luminance at most 40 each. `common.py` checks it; a script that exits with 1 has a picture to fix.
- **No logos, no characters or figures, no film imagery, no trademarked lettering**: silhouettes, gradients and
  light only. The worlds are moods, not fan art.
- Moving things belong in the overlay (`HogwartsOverlay`, `MarvelOverlay`, `StarWarsOverlay`,
  `SupernaturalOverlay` for the dust, heat, rays, rain, neon flicker, embers and the lightning with its wing shadow,
  placed by `SupernaturalScene`'s constants; Jahreskreis'
  `SeasonsOverlay` with one `LookMotion` per look, `SeasonMotions` for the seasons' snow, blossoms, fireflies and
  leaves, `OccasionMotionsA` for the fireworks, streamers and confetti, candle and petals, butterflies, motes and
  clouds of New Year to Father's Day, `OccasionMotionsB` for the pumpkins' flicker and the bats, the swinging
  lanterns, snow, the Advent candles' flames and the twinkling lights of Halloween to Christmas, `PersonalMotions`
  for the personal days' candles, confetti, holo heart, stars, arch lights, ring sparkle and petals), not in the
  picture; the picture is whole without them (reduced motion), so the Advent candles are painted unlit. The personal
  days' names and year count are never painted: `PersonalMotions` writes them at runtime from the TV's entries, and
  `BalloonBowView` ties the birthday balloons' bow over the dock.
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
  `animations` off. Supernatural's own logo picture, if one is on the TV, still takes the lettering's place: it is
  still, so its overlay draws it once and nothing else.
