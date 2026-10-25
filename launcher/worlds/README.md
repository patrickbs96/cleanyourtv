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
