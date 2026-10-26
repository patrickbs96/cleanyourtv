# Vier Welten – Design für den Clean-Your-TV-Launcher (Fork)

Stand 25.10.2026. Gerät: Philips 65OLED934 (TPM191E, `PH9M_EA_5599`, Android TV 11 / API 30, MediaTek, 2 GB RAM,
1920×1080, OLED). Launcher `com.gathin.tvhome` ist dort Home, 6er-Dock, `HOME_KEY_SERVICE=1` und `power_on_home=true`
(siehe `backup/README.md`); `key_filter` wird mit Task 0 `true` (§ 4.10d).

Mockups, an denen dieses Design gemessen wird: `.superpowers/brainstorm/651-1792953360/content/vier-welten-v3.html`
(Szenen und UI) und `vier-welten-v4-motion.html` (Dock-Wechsel und App-Start, mit Knöpfen zum Abspielen).

## 1. Absicht

Patrick und seine Partnerin mögen Harry Potter (Einladungen und Hochzeitsseite sind in diesem Stil), Marvel (MCU-Kino)
und Star Wars. Der Fernseher soll zwischen vier „Welten“ wechseln können – **Clean** (der heutige Launcher,
unverändert), **Hogwarts**, **Marvel**, **Star Wars** – und dabei trotzdem ein aufgeräumter, schneller Home-Screen
bleiben: nerdig in Licht, Farbe, Schrift und Bewegung, nicht in Logos.

## 2. Ziele, Nicht-Ziele, Anti-Ziele

**Ziele**

- Vier Welten, umschaltbar auf dem Home-Screen über die Farbtasten der Fernbedienung
  (Grün = Clean, Gelb = Hogwarts, Rot = Marvel, Blau = Star Wars) und über einen Eintrag „Welt“ in den
  Launcher-Einstellungen. Die Wahl bleibt über Neustarts gespeichert.
- Jede der drei neuen Welten ist eine komplette Haut: Szene (Hintergrund), Schrift, Begrüßung, Hero-Name, Buttons,
  Dock-Stil, Fokusfarbe, leichte Dauerbewegung, eigener Dock-Wechsel, eigener App-Start, eigener Übergang beim
  Hineinwechseln, Tag- und Nachtvariante der Szene.
- Clean bleibt bit-identisch zum heutigen Verhalten, solange Clean gewählt ist.
- Alles aus eigenem Code und frei lizenzierten Schriften gezeichnet; keine Logos, Figuren, Markenschriften,
  Filmbilder oder Zitate, die als Marke geschützt sind.
- Leichtgewichtig: keine Videos, keine Shader, Dauerbewegung mit wenigen Dutzend Partikeln; mit
  `reduced_motion` entfällt alle Bewegung außer dem Nötigsten. Netz nur für die Wetterzeile (§ 4.10c), sonst nichts.
- OLED-Schutz: Versatz der UI jede Minute (§ 4.10a), Uhr-Screensaver nach 10 min.

**Nicht-Ziele**

- Kein Pull Request ans Upstream-Repo; das ist ein privater Fork auf dem Branch `vier-welten`.
- Keine Änderung an Debloat, Tasten-Mapping oder dem Theme-Dateiformat (`config.json`) über das Nötige hinaus.
- Keine Welten-Umschaltung aus fremden Apps heraus (die Farbtasten wirken nur, während Home im Vordergrund ist).
- Keine weiteren Welten in dieser Runde (das Modell soll sie später erlauben, siehe § 4).

**Anti-Ziele** (das wäre ein Fehlschlag, auch wenn es läuft)

- Ein Home-Screen, der nach Fan-Shop aussieht: Logos, Figuren, grelle Vollflächen.
- Spürbares Ruckeln beim D-Pad, längere Kaltstarts, Einbrenn-Risiko durch helle statische Flächen auf dem OLED.
- Ein Clean, das sich anders anfühlt als vorher.

## 3. Rahmenbedingungen

- Fork: Branch `vier-welten` im lokalen Checkout `C:\Users\ltdor\Documents\GitHub\cleanyourtv` (Remote zeigt auf
  `Gathin23/cleanyourtv`; ein eigener GitHub-Fork zum Sichern ist Patricks Sache). `.superpowers/` kommt in die
  `.gitignore`.
- Build wie bisher: `launcher/build-and-install.sh` mit `JAVA_HOME` (Temurin 17) und `ANDROID_HOME=C:/Users/ltdor/android-sdk`,
  `TV_HOST=192.168.178.144`, `HOME_KEY_SERVICE=1`. minSdk 26, targetSdk 34, Java, AndroidX RecyclerView + DynamicAnimation.
- APK-Größe: die Upstream-CI hat ein Größen-Gate; für den Fork gilt nur „unter 10 MB“, drei zusätzliche Schriften
  (je eine Stärke, Latin-Subset) und sechs JPEG-Hintergründe à ≤ 300 KB passen.
- Speicher: ein Hintergrund wird als RGB_565 dekodiert (4 MB bei 1080p; `wallpaper_true_color` darf bei 2 GB an sein).
  Partikel-Overlays zeichnen auf einen Canvas ohne Allokation pro Frame (Muster: `PetalShowerView`).
- Schriften (alle SIL OFL): Cormorant Garamond (Hogwarts), Oswald (Marvel), Michroma (Star Wars). Outfit bleibt für Clean.
  Lizenztexte neben `FONT-LICENSE-OFL.txt` ablegen.
- Fernbedienung: die Philips-Fernbedienung hat vier Farbtasten. Mit `getevent -lt` am 25.10.2026 gemessen: Gerät
  `/dev/input/event1` (`TPV_MutilRC`) sendet `KEY_RED`, `KEY_GREEN`, `KEY_YELLOW`, `KEY_BLUE` (Linux 398–401) **ohne**
  `MSC_SCAN`. Die Vendor-Key-Layout-Datei `/vendor/usr/keylayout/TPV_MutilRC.kl` ist für die Shell nicht lesbar; die
  Android-Keycodes sind damit sehr wahrscheinlich `KEYCODE_PROG_RED/GREEN/YELLOW/BLUE` (183–186), aber nicht bewiesen.
  **Erster Schritt der Umsetzung:** die vier Tasten in `MainActivity.dispatchKeyEvent` mit `Log.i` (Keycode und
  `getScanCode()`) protokollieren, Patrick drücken lassen, `logcat` lesen; fällt ein anderer Keycode (etwa
  `KEYCODE_UNKNOWN` mit Scancode 398–401), wird nach Scancode gematcht. Die Tasten erreichen die Activity direkt,
  solange Home im Vordergrund ist – kein Accessibility-Dienst nötig, die Lautstärke-Wiederholung bleibt wie sie ist.
  **Gemessen am 26.10.2026** (echte Fernbedienung, Log `World`): Grün `key=184 scan=399`, Gelb `key=185 scan=400`,
  Blau `key=186 scan=401` – Android liefert die `KEYCODE_PROG_*`-Codes, der Scancode-Pfad ist nur Reserve.
  Blau öffnet zusätzlich die Philips-Hilfe (`org.droidtv.eum`): der Firmware-Dienst `GlobalKeyRcv` liest die
  Taste selbst mit (Scancode 401 → `org.droidtv.action.HELP`), unabhängig davon, ob Home sie verbraucht.
  **Deshalb (Patrick, 26.10.):** zusätzlich wechseln die Zifferntasten, 1 Clean, 2 Hogwarts, 3 Marvel, 4 Star Wars
  (die Firmware tut bei Ziffern auf Home nichts, am TV geprüft). Die Ziffer folgt der Reihenfolge im `World`-Enum,
  eine weitere Welt bekommt also die nächste freie Ziffer (bis 9). Ziffern 1 bis Anzahl der Welten sind in der
  Tastenbelegung nicht lernbar. Die Farbtasten bleiben.
- Vorhandene Bausteine, auf denen gebaut wird: `Festival` (Haut mit eigenen Pill-Icons, Begrüßung, Akzentfarbe),
  `GlassView.setAccent/setStyle` (Fokusrahmen), `DockDecoration` (Dock-Band), `HeroArt`/Hero-Titel, `UiFont` +
  `res/values/fonts.xml` (Schrift-Indirektion), `WallpaperPlayer` + `WallpaperMood` (Tag/Abend/Nacht nach Sonnenstand),
  `PetalShowerView` (Partikel-Overlay), `Motion` (reduced motion), `LauncherSettingsActivity` + `SharedPreferences
  Config.SETTINGS_NAME` (gespeicherte Wahl), `res/anim/app_open_*` (App-Start).

## 4. Architektur

### 4.1 Das Welt-Modell

Eine Welt ist ein unveränderliches Java-Objekt `World` (enum oder final class mit statischer Liste), kein Theme-Ordner:
alle Werte liegen im APK. Felder:

| Feld | Bedeutung |
|---|---|
| `id` | `clean`, `hogwarts`, `marvel`, `starwars` |
| `key` | Farbtaste, die sie wählt |
| `label` | Anzeigename im Einstellungs-Eintrag „Welt“ |
| `fontBody/Light/Medium` | Schrift-Ressourcen für Uhr, Datum, Begrüßung, Hero, Pills |
| `greeting(timeOfDay)` | ersetzt den Tageszeit-Gruß (Hogwarts: „Lumos · Guten Morgen“ / „Nox · Guten Abend“; Marvel: „Suit up · …“; Star Wars: „… · Äußerer Rand“) |
| `heroPrefix` | Zeile über dem Hero-Namen (Hogwarts: „Zauberspiegel zeigt“; sonst leer) |
| `accent` | Fokus- und Glow-Farbe (Hogwarts Gold `#E8C87A`, Marvel Reaktor-Blau `#9FDCFF`, Star Wars Holo-Blau `#CFE0FF`) |
| `heroStyle` | Textstil des Hero-Namens (Hogwarts kursiv Creme; Marvel versal mit Gold-Rot-Verlauf; Star Wars versal Crawl-Gelb `#FFE81F`, 18° nach hinten gekippt) |
| `pills` | Icon-Satz und Form der drei Buttons (rund Gold-Medaillon / eckig HUD / eckig Holo) |
| `dockStyle` | siehe § 4.4 |
| `scene` | Tag- und Nachtbild (JPEG) plus Overlay-Klasse für die Dauerbewegung |
| `focusMotion` | Dock-Wechsel-Effekt (§ 4.6) |
| `launchMotion` | App-Start-Effekt (§ 4.7) |
| `enterMotion` | Übergang beim Hineinwechseln (§ 4.8) |
| `extras` | Hausfarben-Streifen (Hogwarts), Stein-Punkte (Marvel), Skala (Star Wars), HUD-Ecken (Marvel), Scanlinien (Star Wars) |

Clean ist `World.CLEAN` mit lauter „nichts tun“-Werten; jeder Codepfad, der eine Welt liest, muss bei Clean exakt
das heutige Verhalten ergeben. Das ist die wichtigste Regressionsbedingung (§ 7).

Die gewählte Welt steht in `SharedPreferences Config.SETTINGS_NAME` unter `world` (Default `clean`). `MainActivity`
liest sie **einmal in `onCreate`** und baut die Haut dort auf; ein Wechsel speichert den Key und startet die Activity
hinter dem Übergang neu (`recreate()`, § 4.2). Kein Live-Umbau: alles Welt-Relevante (DockDecoration, GlassView-Federn,
Schriften im Layout, Pill-Drawables) entsteht heute in `onCreate`, und ein zur Laufzeit rückstellbarer Umbau wäre das
größte Risiko für ein verändertes Clean.

Die Welten sind für die **heutige Konfiguration** dieses TVs gebaut: `dock_row=true`, 6 Spalten, `rows_below_dock=0`,
kein `cinematic_home`, kein `single_row`, kein Wallpaper-Theme. Ist einer dieser Schalter anders gesetzt, fällt der
Dock-Stil der Welt auf den Clean-Dock zurück (Szene, Schrift, Farben und Pills bleiben); diese Kombinationen werden
nicht getestet.

Ein Theme-Push (`tools/theme.sh`) oder `--reset` lässt die Welt-Wahl unangetastet; eine Welt liegt *über* dem
Theme/Config: wo beide etwas sagen (Hintergrund, Dock-Band, Glow-Farbe, Schrift), gewinnt die Welt, außer bei Clean.

### 4.2 Wechsel

- Farbtasten: in `MainActivity.dispatchKeyEvent` auf `ACTION_UP` von `KEYCODE_PROG_*`, nur wenn kein Dialog/Reorder
  aktiv ist. Gleiche Taste erneut = nichts. Ablauf eines Wechsels: erster Teil des `enterMotion` der Zielwelt
  (Abdunkeln bzw. Hyperraum-Anlauf, ≤ 400 ms) → `world` speichern → `recreate()` → die neue Instanz erkennt beim Start
  ein gesetztes „Übergang läuft“-Flag und spielt den zweiten Teil (Einblenden, Kerzen zünden, HUD-Boot) statt der
  normalen Kachel-Entrance. Der Warmstart liegt unter einer Sekunde und wird vom Übergang verdeckt.
- Einstellungen: neue Zeile „Welt“ in `LauncherSettingsActivity` mit Auswahl-Dialog (vier Einträge, die Farbtaste als
  farbiger Punkt vor dem Namen). Dort auch ein Hinweis-Text „Farbtasten der Fernbedienung wechseln direkt“. Dieser Weg
  nutzt den vorhandenen Revisions-Mechanismus (Home recreatet in `onResume`); der zweite Teil des Übergangs spielt
  ebenfalls.
- Kein Auto-Wechsel (keine Rotation zwischen Welten). Tag/Nacht wechselt nur das Bild *innerhalb* einer Welt (§ 4.3).

### 4.3 Szene: Hintergrund und Dauerbewegung

Jede Welt hat zwei statische Bilder, `day.jpg` und `night.jpg` (1920×1080, ≤ 300 KB, dunkel in den oberen Ecken und
im unteren Drittel, siehe `themes/README.md` Wallpapers), die wie beim `ruska`-Theme **per Python-Skript gezeichnet**
werden (Pillow + NumPy, Seed fest, reproduzierbar): `launcher/worlds/<id>/make_scene.py` schreibt beide Bilder nach
`launcher/app/src/main/res/drawable-nodpi/world_<id>_day.jpg` und `_night.jpg`. Tag/Nacht nach `WallpaperMood`
(morning+evening = Tag, night = Nacht) mit Sonnenstand aus `config.xml` `latitude/longitude`; das Bild wird beim
Moodwechsel weich überblendet (300 ms), nie hart.

Darüber liegt eine **Overlay-View** je Welt (eine Klasse, Muster `PetalShowerView`: kein Fokus, keine Allokation pro
Frame, kein Hardware-Layer – sie läuft dauerhaft, ein Vollbild-Layer kostete ~8 MB und würde je Tick ganz neu
gezeichnet), die die Dauerbewegung zeichnet. Sie läuft mit höchstens 30 fps, pausiert in `onPause`, bei
`reduced_motion` und solange der Bildschirmschoner läuft, und meldet nur ihre Teilflächen (`invalidate(rect)`; ab
API 21 ignoriert die Hardware-Pipeline das Rechteck, der Aufwand je Tick ist das Neuaufzeichnen der Display-List).

| Welt | Tagbild | Nachtbild | Overlay (Dauerbewegung) |
|---|---|---|---|
| Hogwarts | Große Halle bei Tag: verzauberte Decke mit Wolken, Lichtschächte, zwei Eulen-Silhouetten | Schloss auf der Klippe (Türme mit Spitzdächern, erleuchtete Fenster, Viadukt), See mit Spiegelung, großer Mond, Sternenhimmel | 10 schwebende Kerzen: Flackern 6–9 s/Zyklus, Drift ±2 px über 9 s, zeitversetzt; 12 Sterne funkeln 3–4 s; bei Tag nur die Kerzen, kleiner |
| Marvel | Skyline bei Tag unter tiefstehender Sonne, warmes Gegenlicht, der hohe Turm mit Leuchtfeuer | Skyline bei Nacht in Crimson-Schwarz, anamorphe Lichtkante bei 46 % Höhe, Fensterraster | Reaktor rechts (10 Segmente, 3 Ringe) dreht 1 U/18 s, Kern pulst 2 s; Lichtkante atmet 4 s; Leuchtfeuer pulst 2 s |
| Star Wars | Doppelsonnen-Untergang über Dünen mit Hitzeschleier, Farm-Kuppel, zwei Vaporatoren, einsame Figur | Reiner Weltraum: Sternenfeld dichter, ein großer Ringplanet rechts unten angeschnitten | Hyperraum-Streifen an beiden Rändern ziehen (1,1 s Schleife, nur äußere 16 % Breite); 15–20 Sterne funkeln; ~~Hologramm-Flackern der UI alle 6–7 s für 60 ms~~ (Nachtrag 26.10.: auf Patricks Wunsch entfernt, irritierend.) |

Clean: kein Bild, kein Overlay (wie heute: `background_color`, Ambient-Glow).

### 4.4 UI-Elemente je Welt

| Element | Hogwarts | Marvel | Star Wars |
|---|---|---|---|
| Schrift | Cormorant Garamond (300 Uhr, 400 Text, 400 kursiv Hero) | Oswald (700 Uhr/Hero, 500 Gruß, 300 Datum) | Michroma (eine Stärke), Uhr 10 % kleiner als Clean |
| Uhr | Creme `#F5E9C8` | Gold-Verlauf `#FFF2D0→#E6B85A→#A86D1F`, weicher Gold-Schatten | `#BCD3FF` mit blauem Schein |
| Datum/Gruß | Gold `#C9A84C`, Trennlinie Gold 40 % | Gold `#E6B85A` versal, Datum `#F0D9B0` | Holo-Blau `#8FB3FF`, versal, Scanlinien-Fläche hinter Uhr+Datum (3-px-Raster, 8 % Alpha) |
| Zierde unter Uhr | „⟡ ✦ ⟡“ in Gold | HUD-Ecken (dünne Linien in `#78C8FF` 35 %) um Uhr+Datum | – |
| Pills | **Icon-only** (die drei Welten erzwingen `icon_only_buttons` + `icon_only_inputs`; Clean behält die heutigen Text-Pills mit WLAN-Name). Hogwarts: runde Gold-Medaillons, Rand `#C9A84C`, Außenring 15 %, Icons Kerze / Eule / Stern | eckig (4 dp Radius), Rand `#78C8FF` 80 %, Innenschein, Icons wie Clean | eckig (3 dp), Rand `#8FB3FF` 70 %, blauer Außenschein, Icons wie Clean |
| Hero-Name | kursiv Creme, darüber Kleinzeile „ZAUBERSPIEGEL ZEIGT“ in Gold gesperrt | versal, Verlauf Weiß→Gold→Rot `#B4201F` | versal Crawl-Gelb, Perspektive 18° |
| Dock | Band `rgba(28,22,44,.85)→rgba(14,11,24,.9)`, Rand Gold 1 px, Außenrahmen 4 px Dunkel + 1 px Gold 45 %, „❖“ oben links/rechts; darunter 3-px-Streifen in den vier Hausfarben `#7A1B1B #C9A84C #1B4D2E #1E3A6B` | Metallleiste `#2A2A2E→#121214`, Rand `#4A4A50`, Oberkante 2 px Gold, zwei Nieten unten außen; über dem Dock links sechs Punkte in `#A855F7 #3B82F6 #EF4444 #F59E0B #22C55E #F97316` (Stein-Farben, nur Schmuck) | kein Band; 1-px-Linie `#8FB3FF` 60 % unter den Kacheln, darunter Zielcomputer-Skala (Striche alle 3,2 %), der Abschnitt unter der fokussierten Kachel leuchtet weiß |
| Fokusrahmen (`GlassView`) | Stil `rim`, Rand `#E8C87A`, Glow Gold | Stil `rim` + Zielklammern (Ecken 8 dp außerhalb, rasten auf 2 dp ein), Glow Blau | Stil `rim`, Rand `#CFE0FF`, Glow Holo-Blau |
| Ambient-Glow | Gold, 35 % | Gold, 30 % | Blau, 25 % |

Icons Kerze/Eule/Stern sind Vektor-Drawables (`res/drawable/ic_world_hog_*.xml`), 22 dp, einfarbig; keine Emoji.

### 4.5 Tag/Nacht

Nur die Szene (Bild + Overlay-Parameter) wechselt, nicht die UI-Farben. Hogwarts-Gruß wechselt „Lumos“/„Nox“ mit der
Tageszeit (Lumos von Sonnenaufgang bis Sonnenuntergang + 30 min). Umschaltzeiten aus `WallpaperMood` mit dem Sonnenstand
für Wohnort: `config.xml` `latitude=52.52`, `longitude=13.41` (bewusst grob, der Fork ist öffentlich). Für den Test
(§ 7) bekommt die Welt-Zeile in den Launcher-Einstellungen bei langem OK einen versteckten Schalter „Szene: Auto / Tag /
Nacht“, der nur bis zum nächsten Home-Start gilt.

### 4.6 Dock-Wechsel (Fokus bewegt sich zwischen Dock-Kacheln)

Clean: unverändert (Feder/`focus_overshoot`). Die drei Welten ersetzen die Bewegung des `GlassView`:

| Welt | Bewegung des Rahmens | Zusatz | Hero-Name |
|---|---|---|---|
| Hogwarts | gleitet 420 ms mit leichtem Überschwung | 10 Funken (5 px, Gold, weicher Schein) starten an der alten Kachel, fliegen 20–70 px nach oben-außen und verblassen in 700 ms | blendet 220 ms aus/ein |
| Marvel | Klammern öffnen (60 ms), Rahmen springt 360 ms, Klammern rasten ein (250 ms) | Scan-Streifen (18 % Kachelbreite, Blau 55 %) fährt 500 ms von links nach rechts über die neue Kachel; Reaktor-Kern pulst einmal 1,6× | blendet 220 ms, dabei kurz heller („Blitz“) |
| Star Wars | springt in **drei harten Schritten** (steps(3), 180 ms), dann Blip: Skalierung Y 0,2→1,05→1 mit Alpha 0,2→1 in 300 ms | Skalen-Abschnitt unter der neuen Kachel leuchtet, der alte erlischt | tippt sich zeichenweise neu (45 ms/Zeichen) |

Innerhalb des Rasters (nicht Dock) gilt dieselbe Rahmenbewegung ohne Zusatz-Effekt, damit das D-Pad ruhig bleibt.

### 4.7 App-Start

Heute: `res/anim/app_open_enter/exit`. Die Welten legen einen Effekt *vor* den `startActivity`-Aufruf (Dauer je
≤ 1,2 s; `startActivity` wird am Ende des Effekts ausgelöst, ein zweiter OK-Druck während des Effekts wird verschluckt):

| Welt | Effekt |
|---|---|
| Hogwarts | Kachel skaliert 1,07→1,25 (400 ms) und glüht weiß-gold; ein konischer Funkenwirbel (14 em, 1,5 Umdrehungen) dreht sich 1,1 s heraus; ab 60 % blendet Schwarz ein („Nox“); bei 100 % `startActivity` |
| Marvel | Kachel 1,07→1,35 (900 ms, cubic-out); drei Ringe laufen von der Kachelmitte nach außen (versetzt 0/150/300 ms, 900 ms); ab 400 ms wischt ein anamorpher Lichtstreifen (40 % Breite) 800 ms von links nach rechts; bei 1,0 s Schnitt auf Weiß (200 ms), `startActivity` bei 1,2 s |
| Star Wars | 70 Streaks aus der Bildmitte in zufälligen Winkeln, Start 150–500 ms versetzt, 900 ms auf 900 px Länge; die Szene zoomt 1,1 s auf 1,4×; die Kachel wird ab 100 ms 1 s lang auf 6×1,6 gezogen und verblasst; bei 1,0 s Weiß (200 ms), `startActivity` bei 1,2 s |

Die Weißschnitte waren im Motion-Mockup 300 bzw. 250 ms; sie sind auf 200 ms gekürzt, damit kein Effekt länger als
1,2 s läuft (Controller-Entscheidung 26.10., R12 revidiert).

Wenn die App bereits läuft (Task im Hintergrund), gleicher Effekt. Bei `reduced_motion`: sofort `startActivity`.

### 4.8 Übergang beim Hineinwechseln in eine Welt

Spielt beim Tastendruck/Einstellungswechsel, ≤ 900 ms, danach steht die neue Haut:

- **Clean**: 250 ms Überblendung zu Schwarz und zurück.
- **Hogwarts**: alte Szene wird 300 ms dunkler, aus der Mitte zünden nacheinander die Kerzen (je 60 ms versetzt), die neue Szene blendet unter ihnen ein.
- **Marvel**: HUD-Boot – Ecken, Pills, Dock-Leiste zeichnen sich 500 ms linienweise ein, der Reaktor fährt mit einem Puls hoch.
- **Star Wars**: Hyperraum-Sprung hinein (wie App-Start, aber 700 ms und ohne Weiß), die neue Szene steht nach dem Sprung.

### 4.9 Begrüßungen

Nur kurze, generische Worte, keine geschützten Zitate: Hogwarts „Lumos“/„Nox“, Marvel „Suit up“, Star Wars „Äußerer Rand“
als Ortsangabe hinter dem Gruß. Die vorhandene `greeting_name`-Logik bleibt und hängt hinten an.

### 4.10a OLED-Versatz (Nachtrag 25.10., aus cosy-home-tv übernommen)

Der heutige Launcher bewegt auf Home nichts; nur der Clock-Screensaver driftet. Neu: `oled_shift` (bool, `config.xml`
und Theme, auf diesem TV `true`): Uhrblock, Pills, Hero, Raster samt Dock-Band und Fokusrahmen bekommen bei jedem
Minuten-Tick denselben zufälligen Versatz von −3…+3 px in X und Y (reine Translation, 400 ms weich; Startwert 0, der
erste Versatz kommt mit dem ersten Tick). Gilt für **alle vier Welten, auch Clean** – darum wird die
Clean-Regression (§ 7) direkt nach dem Kaltstart gemessen, solange der Versatz 0 ist. Dazu `home_idle_clock_minutes=10`
und `clock_glow=false` in `config.xml`: nach 10 min Stillstand auf Home läuft der driftende Uhr-Screensaver auf Schwarz.

### 4.10b Ambilight-Pill (Nachtrag 25.10.)

`ambilight_action` (string, `config.xml`/Theme; hier `org.droidtv.action.AMBILIGHT_OSD`, am TV geprüft: löst zu
`org.droidtv.settings/.AmbilightOsd` auf). Nicht leer und auflösbar → vierte Pill „Ambilight“ zwischen Quellen und
Netz, eigenes Vektor-Icon (Bildschirm mit Lichtkranz), öffnet die Aktion; in `top_menu` eine Zeile im Gear-Menü.
In den Welten Icon-only wie die anderen Pills. Gilt auch in Clean (Konfiguration, keine Welt): die
Clean-Vergleichsaufnahme wird nach dieser Änderung neu genommen.
**Nachtrag 26.10. (Abnahme):** Mit beschrifteter vierter Pill blieb dem Uhrblock in Clean zu wenig Breite (Gruß
gekürzt, Uhr umgebrochen, Datum weg). Patricks Entscheidung: Clean zeigt alle Pills Icon-only wie die Welten
(`icon_only_buttons` und `icon_only_inputs` im Fork `true`, `wifi_label_width` 0); die Netz-Pill zeigt dann
keinen Netznamen und keine VPN-Kapsel mehr.

### 4.10c Wetterzeile (Nachtrag 25.10., bewusste Abweichung von „kein Netz“)

Patrick hat am 25.10.2026 zugestimmt, dass der Launcher dafür ins Netz darf: Berechtigung `INTERNET`, genau eine
Quelle, `api.open-meteo.com/v1/forecast` mit `latitude/longitude` aus `config.xml` (zwei Nachkommastellen, ≈ 1 km),
`current=temperature_2m,weather_code`, `timezone=auto`. Abruf alle 30 min, solange Home im Vordergrund ist, 5 min nach
einem Fehler, nie im Hintergrund; letzter Wert mit Zeitstempel in `SharedPreferences` und beim Start sofort gezeigt,
wenn jünger als 2 h. Anzeige: eine Zeile unter dem Datum, „12° · Bewölkt“, WMO-Code → deutsche Wörter aus eigenen
Strings, Typografie der Welt. `weather` (bool) schaltet sie ab; ohne Koordinaten ist sie aus und es gibt keinen
Netzzugriff. Keine weiteren Verbindungen, keine Telemetrie. Das Add-on `addons/weather` bleibt davon unberührt.

### 4.10d Tastenbelegung im Launcher, Button Mapper abgelöst (Nachtrag 25.10., abends)

Messung am 25.10.: Mit abgeschaltetem Button Mapper (`flar2.homebutton`, ein Accessibility-Dienst, der Tasten
filtert) läuft die Lautstärke beim Halten wieder durch – **mit unserem `HomeKeyService`, auch mit
`key_filter=true`**. Der Verlust der Wiederholung kam allein vom Button Mapper, nicht vom Launcher-Dienst; die
Abwägung „Einschalten auf Home gegen Lautstärke“ vom Nachmittag war ein Irrtum, es gibt sie nicht.

Folgen: `key_filter` wird `true`, die Tastenbelegung zieht in den Launcher (`app_keys`), Button Mapper wird danach
deinstalliert. Patricks heutige Belegung: Rakuten-Taste → Disney+, Doppelklick Netflix-Taste → YouTube (Einzelklick
bleibt Netflix). `app_keys` kennt Tap und `hold:`; **Doppelklick ist neu** (Fork): Präfix `double:`, Fenster 400 ms;
ein Tap auf eine Taste mit `double:`-Eintrag wird 400 ms zurückgehalten und dann als Tap ausgeführt, kommt der zweite
Druck vorher, läuft der `double:`-Eintrag. Die Tasten melden sich am Kernel ohne Label (Linux-Codes `0x02b7`=695,
`0x030b`=779 und eine als `KEY_ONSCREEN_KEYBOARD`), die Android-Keycodes werden wie bei den Farbtasten per `Log.i`
im Dienst gemessen (Keycode + Scancode), gematcht wird per Scancode, wenn Android `KEYCODE_UNKNOWN` liefert.
Bis der Fork steht, bleibt Button Mapper an (Patricks Wahl, 25.10.).

**Nachtrag (25.10., spät):** Patrick belegt drei Tasten (Netflix, Philips, Rakuten) jeweils mit Einzel- und
Doppelklick. Die Belegung wird deshalb **auf dem TV** gepflegt: Seite „Tastenbelegung“ in den
Launcher-Einstellungen mit Lernmodus (Taste drücken → Code gemerkt → Name vergeben), je Taste Einzelklick /
Doppelklick / Halten mit App-Auswahl; gespeichert in `SharedPreferences`, überlagert `config.xml`/Theme, sofort
wirksam. Dazu „Alle Apps schließen“ (§ 4.10f).

**Nachtrag (26.10., Final-Review):** Nicht lernbar sind außer Pfeilen, OK, Zurück, Home, Power und Lautstärke auch
die Farbtasten (Weltwechsel) und `KEYCODE_SETTINGS` (öffnet in `HomeKeyService` vor jeder Belegung die
Einstellungen); der Lernversuch zeigt „Diese Taste ist schon belegt (Welten/Einstellungen)“ und wartet weiter. Eine
Taste mit Doppelklick oder Halten, aber leerem Einzelklick ist beim Einzelklick stumm (sie wird geschluckt): Die Seite
zeigt dann „Nichts (Taste stumm)“ und fragt beim Setzen von Doppelklick/Halten sofort „Einzelklick belegen?“ mit der
App-Auswahl (Abbrechen lässt sie stumm).

### 4.10f „Alle Apps schließen“ (Nachtrag 25.10., Ersatz für ein Premium-Feature des Button Mappers)

Doppelklick auf die Home-Taste (erster Druck geht sofort ans System, kein Zurückhalten), eine Zeile im
Settings-Pill-Menü und ein wählbares Ziel in der Tastenbelegung (`close_apps`) beenden die Hintergrund-Prozesse
aller Apps (`ActivityManager.killBackgroundProcesses`, Berechtigung `KILL_BACKGROUND_PROCESSES`, normal). Ausgenommen:
Launcher, Live TV, Play Services/Store, Assistant, Chromecast, Philips-Systemdienste. Grenze ohne Root: Apps mit
Vordergrund-Dienst (Spotify beim Abspielen) laufen weiter; eine hängende App wird durch den Home-Druck in den
Hintergrund gestellt und dann beendet – der Fall, um den es Patrick geht.

### 4.10e Schalter, die sofort an gehen (Task 0)

`tile_menu`, `app_library` + `library_button`, `vpn_app=de.mobileconcepts.cyberghost`, `wifi_status_color` +
`wifi_status_warnings`, `launcher_settings_button`, `key_filter=true`. Die in `hidden_apps` versteckten Philips-Kacheln
bleiben versteckt; Nicht-TV-Apps (CyberGhost, Button Mapper, Boosteroid) räumt Patrick danach selbst über die
Bibliothek von Home.

### 4.10 Einstellungen und Persistenz

- `LauncherSettingsActivity`: Zeile „Welt“ (vier Optionen), gespeichert unter `world`.
- Die Welt überlebt Theme-Push, `--reset`, Neustart, `am force-stop`.
- `tools/manage.py` bekommt keinen neuen Schalter (Fork-Feature, nicht Theme-Key).

## 5. Dateien und Werkzeuge

- `launcher/app/src/main/java/com/gathin/tvhome/World.java` – Modell und die vier Welten.
- `…/world/HogwartsOverlay.java`, `MarvelOverlay.java`, `StarWarsOverlay.java` – Dauerbewegung (eine Basisklasse `WorldOverlay`).
- `…/world/WorldMotion.java` – Dock-Wechsel-, App-Start- und Übergangs-Effekte, als Strategien je Welt.
- `res/font/cormorant_garamond_*.ttf`, `oswald_*.ttf`, `michroma.ttf` + Einträge in `res/values/fonts.xml`.
- `res/drawable-nodpi/world_<id>_day.jpg`, `_night.jpg`; `res/drawable/ic_world_*.xml`; Dock-Zierde als Drawables.
- `launcher/worlds/<id>/make_scene.py` – zeichnet die Bilder; `launcher/worlds/README.md` – wie man sie neu baut.
- `backup/README.md` – Abschnitt „Welten“ (Tasten, Einstellungen, wie man Clean erzwingt).

## 6. Was der Umsetzende entscheidet

- Exakte Geometrie der Szenen (Turmzahl, Dünenverlauf, Skyline-Rhythmus), solange die Mockups v3 als Referenz erkennbar
  bleiben und die Bilder die Dunkelregeln einhalten.
- Ob Overlay-Partikel als eine View über dem Wallpaper oder in den bestehenden `SkyView`-Slot gezeichnet werden.
- Schriftstärken-Auswahl, solange Dateigröße und Lesbarkeit auf 3 m Abstand stimmen (Uhr ≥ 64 sp-Äquivalent).
- Wie die Welt-Haut auf Dialoge (Inputs-Menü, Einstellungen, Bibliothek) durchschlägt: mindestens Schrift und Akzentfarbe,
  nicht zwingend die Szene.
- Ob die Tag↔Nacht-Überblendung zwei volle 1080p-Bitmaps gleichzeitig hält oder über ein 480×270-Zwischenbild läuft
  (2 GB RAM; messen, nicht raten).

## 7. Prüfung

- **Clean-Regression**: Screenshot-Vergleich Home vor/nach dem Fork (`tools/screendiff.py`) bei gewählter Welt Clean; Diff muss leer sein bis auf die Uhr.
- **Unit (JVM, `tests/` ist Python – für Java: kleine JUnit-Tests im Launcher-Modul)**: `World`-Parsing/Defaults, Tag/Nacht-Zuordnung über `WallpaperMood`, Farbtasten-Mapping, Persistenz-Key.
- **Auf dem TV, je Welt**: Screenshot Home Tag und Nacht (Uhrzeit per `WallpaperMood`-Grenzen oder Testschalter), Dock-Wechsel per `tools/tv.py --screenshot … DPAD_RIGHT`, App-Start (Netflix gibt 0 Byte-Screenshots: `dumpsys activity activities | grep topResumedActivity`), Weltenwechsel mit allen vier Farbtasten **an der echten Fernbedienung** (injizierte Keys gelten nicht als Beweis für Tasten).
- **Leistung**: `tools/bench.py`/`trace.py` vor und nach: Kaltstart Home ≤ +300 ms, D-Pad-Frame-Zeit im Dock ≤ 16 ms im Mittel, RSS des Launchers ≤ +25 MB gegenüber Clean.
- **OLED**: keine Fläche heller als 60 % Luminanz, die länger als 10 min steht (Reaktor, Sonnen, Mond sind klein; Dauerbewegung läuft).
- **Abschaltbarkeit**: `reduced_motion` stoppt Overlays und Effekte; Welt Clean per Einstellung erreichbar, auch wenn eine Farbtaste fehlt.

## 8. Builder-Check (25.10.2026)

Ein unabhängiger Reviewer hat das Dokument gegen den Launcher-Code gelesen. Seine vier Fragen sind in § 4.1 (Neustart
statt Live-Umbau; nur heutige Konfiguration), § 4.4 (Icon-only-Pills in den Welten) und § 4.5 (Koordinaten Wohnort)
beantwortet. Geprüft und unkritisch: `theme.sh --reset` und `syncThemeChoices` lassen `world` stehen; mit
`key_filter=false` filtert `HomeKeyService` nichts, `app_keys` und `settings_key_keycodes` enthalten keine Farbtaste,
die Farbtasten erreichen `dispatchKeyEvent`; der Clock-Screensaver ist aus.
