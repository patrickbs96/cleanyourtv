# Vier Welten – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Clean-Your-TV-Launcher bekommt drei umschaltbare Welten (Hogwarts, Marvel, Star Wars) neben dem unveränderten Clean, gewählt per Farbtaste oder Einstellungs-Zeile, jede mit eigener Szene, Typografie, Dock, Dauerbewegung, Dock-Wechsel-, App-Start- und Übergangs-Animation.

**Architecture:** Ein unveränderliches `World`-Objekt pro Welt bündelt alle Werte; `MainActivity` liest die gewählte Welt einmal in `onCreate` aus den `SharedPreferences` und baut die Haut dort auf (kein Live-Umbau). Ein Wechsel speichert den Key und ruft `recreate()` hinter einer zweigeteilten Übergangsanimation. Szenen sind per Python gezeichnete JPEGs im APK, Dauerbewegung und Effekte sind leichte Canvas-Views nach dem Muster von `PetalShowerView`. Clean nimmt an jeder Welt-Abfrage den „nichts tun“-Pfad.

**Tech Stack:** Java 17, Android minSdk 26 / targetSdk 34, AndroidX RecyclerView 1.3.2 + DynamicAnimation 1.0.0, JUnit 4 (JVM-Tests in `launcher/app/src/test`), Gradle 8.9, Python 3 + Pillow/NumPy für die Szenen-Skripte, `tools/tv.py` (adb-shell) für den TV.

**Spec:** `docs/superpowers/specs/2026-10-25-vier-welten-launcher-design.md`

## Global Constraints

- Branch `vier-welten` im Checkout `C:\Users\ltdor\Documents\GitHub\cleanyourtv`; Remote `origin` = `patrickbs96/cleanyourtv`. Kein PR ans Upstream.
- Build/Install immer: `cd launcher && JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot" ANDROID_HOME=C:/Users/ltdor/android-sdk TV_HOST=192.168.178.144 HOME_KEY_SERVICE=1 ./build-and-install.sh` (Git Bash). JVM-Tests: `cd launcher && JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot" ANDROID_HOME=C:/Users/ltdor/android-sdk ./gradlew testReleaseUnitTest -q --tests '<Klasse>'`.
- TV-Befehle: `TV_HOST=192.168.178.144 tools/venv/Scripts/python tools/tv.py '<shell>'`, Screenshots `tools/tv.py --screenshot shots/<name>.png [KEY …]`. Netflix/Live TV liefern 0-Byte-Screenshots; dann `dumpsys activity activities | grep topResumedActivity`.
- **Clean bleibt bit-identisch**: jeder Codepfad prüft `world.isClean()` zuerst und nimmt dann exakt den heutigen Weg.
- Keine Logos, Figuren, Markenschriften, Filmbilder, geschützten Zitate. Schriften nur SIL OFL: Cormorant Garamond, Oswald, Michroma (Lizenztexte neben `launcher/FONT-LICENSE-OFL.txt`).
- Alle Effekte ≤ 1,2 s; Übergang ≤ 900 ms; Overlays ≤ 30 fps, nur Teilflächen invalidieren, keine Allokation pro Frame, pausieren in `onPause` und bei `reduced_motion`.
- Szenenbilder: 1920×1080 JPEG ≤ 300 KB, dunkel in den oberen Ecken und im unteren Drittel; aus `make_scene.py` mit festem Seed reproduzierbar.
- Leistungsgrenzen (Task 12): Kaltstart Home ≤ +300 ms, RSS ≤ +25 MB gegenüber Clean, keine Fläche > 60 % Luminanz länger als 10 min statisch.
- Commits auf Deutsch, Umlaute echt (ä ö ü ß), Trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. Nie `git add -A`; nur die Dateien der Task.
- Deutsch in UI-Strings (`values-de`), Englisch als Default-String (`values`), wie im Repo üblich.

## Review Focus

1. **Farbtaste, während ein Popup offen ist** (Inputs-Menü, Tile-Menü, Reorder, App-Bibliothek, Settings-Menü): der Nutzer erwartet, dass nichts passiert und das Popup bleibt – kein `recreate()` unter einem offenen Dialog. → Test in Task 2 (`WorldSwitchGuardTest`) und TV-Prüfung Task 2 Schritt 9.
2. **Doppeltes OK während der App-Start-Animation**: kein zweiter `startActivity`, kein hängender Effekt. → Task 9 (`LaunchGuardTest`).
3. **Tag/Nacht-Grenze schlägt um, während Home sichtbar ist** (Minute-Tick): sanfte Überblendung, kein Flackern, Overlay-Parameter folgen. → Task 4 (`WorldSceneTest.moodChangeCrossfades`) + Testschalter Task 11.
4. **`reduced_motion` oder `animations=false` an**: keine Overlays, keine Effekte, Farbtasten wechseln trotzdem (mit Hart-Schnitt). → Task 5 (`OverlayPolicyTest`) und Task 10.
5. **Theme-Push, `--reset`, Force-Stop, Neustart**: Welt bleibt gewählt; Clean als Fallback bei unbekanntem Pref-Wert. → Task 1 (`WorldTest.unknownValueIsClean`), TV-Prüfung Task 12.

---

### Task 1: `World`-Modell und Persistenz

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/World.java`
- Test: `launcher/app/src/test/java/com/gathin/tvhome/WorldTest.java`

**Interfaces:**
- Produces:
  - `enum World { CLEAN, HOGWARTS, MARVEL, STARWARS }` mit
    - `static final String PREF_KEY = "world"`
    - `static World current(SharedPreferences prefs)` – liest `PREF_KEY`, unbekannt/fehlend → `CLEAN`
    - `void save(SharedPreferences prefs)` – schreibt `id` unter `PREF_KEY` (commit, nicht apply: `recreate()` folgt sofort)
    - `static World forKeyCode(int keyCode)` – `KEYCODE_PROG_GREEN`→CLEAN, `_YELLOW`→HOGWARTS, `_RED`→MARVEL, `_BLUE`→STARWARS, sonst `null`
    - `static World forScanCode(int scanCode)` – 399→CLEAN, 400→HOGWARTS, 398→MARVEL, 401→STARWARS, sonst `null`
    - `final String id` (`clean|hogwarts|marvel|starwars`), `final int labelRes`, `final int keyColor` (Grün `0xFF2ECC40`, Gelb `0xFFF1C40F`, Rot `0xFFE03131`, Blau `0xFF2F6FE0`)
    - `boolean isClean()`
    - `final int accent` (CLEAN 0, HOGWARTS `0xFFE8C87A`, MARVEL `0xFF9FDCFF`, STARWARS `0xFFCFE0FF`)
    - `final int ambientColor`, `final int ambientAlphaPercent` (Gold `0xFFD9A441`/35, Gold `0xFFE6B85A`/30, Blau `0xFF5B8CFF`/25; CLEAN 0/0)
    - `final int backgroundColor` (HOGWARTS `0xFF141020`, MARVEL `0xFF120306`, STARWARS `0xFF000000`, CLEAN 0 = Config-Wert)
    - `String greetingPrefix(boolean day)` – HOGWARTS `day ? "Lumos" : "Nox"`, MARVEL `"Suit up"`, sonst `""`; `String greetingSuffix()` – STARWARS `"Äußerer Rand"`, sonst `""`
    - `final int heroPrefixRes` (HOGWARTS → `R.string.world_hog_hero_prefix` = „Zauberspiegel zeigt“, sonst 0)
    - `boolean forcesIconOnlyPills()` – true außer CLEAN
    - `static boolean dockStyleSupported(Config cfg)` – `cfg.dockRow && !cfg.singleRow && !cfg.cinematicHome && cfg.rowsBelowDock == 0`
  - Strings: `world`, `world_clean`, `world_hogwarts`, `world_marvel`, `world_starwars`, `world_hint` („Farbtasten der Fernbedienung wechseln direkt“), `world_hog_hero_prefix` in `values/strings.xml` (englisch) und `values-de/strings.xml`.

- [ ] **Step 1: Test schreiben**

```java
public class WorldTest {
    @Test public void unknownValueIsClean() { assertSame(World.CLEAN, World.parse("")); assertSame(World.CLEAN, World.parse("narnia")); assertSame(World.CLEAN, World.parse(null)); }
    @Test public void idsRoundTrip() { for (World w : World.values()) assertSame(w, World.parse(w.id)); }
    @Test public void colourKeysMapAsSpecified() {
        assertSame(World.CLEAN, World.forKeyCode(KeyEvent.KEYCODE_PROG_GREEN));
        assertSame(World.HOGWARTS, World.forKeyCode(KeyEvent.KEYCODE_PROG_YELLOW));
        assertSame(World.MARVEL, World.forKeyCode(KeyEvent.KEYCODE_PROG_RED));
        assertSame(World.STARWARS, World.forKeyCode(KeyEvent.KEYCODE_PROG_BLUE));
        assertNull(World.forKeyCode(KeyEvent.KEYCODE_DPAD_CENTER));
    }
    @Test public void scanCodesMapLikeKeys() { assertSame(World.MARVEL, World.forScanCode(398)); assertSame(World.CLEAN, World.forScanCode(399)); assertSame(World.HOGWARTS, World.forScanCode(400)); assertSame(World.STARWARS, World.forScanCode(401)); assertNull(World.forScanCode(1)); }
    @Test public void hogwartsGreetsLumosByDayNoxByNight() { assertEquals("Lumos", World.HOGWARTS.greetingPrefix(true)); assertEquals("Nox", World.HOGWARTS.greetingPrefix(false)); assertEquals("", World.CLEAN.greetingPrefix(true)); assertEquals("Äußerer Rand", World.STARWARS.greetingSuffix()); }
    @Test public void onlyCleanKeepsTextPills() { assertFalse(World.CLEAN.forcesIconOnlyPills()); assertTrue(World.MARVEL.forcesIconOnlyPills()); }
}
```
`KeyEvent` ist in JVM-Tests ein Stub ohne Konstanten? Prüfen: `android.jar`-Stubs liefern die Konstanten (statische finals sind inline). Falls der Test mit „Stub!“ fällt, die Keycodes als `int` in `World` spiegeln (`KEY_RED = 183 …`) und im Test die Zahlen verwenden.

- [ ] **Step 2: Test laufen lassen, Fehlschlag sehen**

Run: `./gradlew testReleaseUnitTest -q --tests 'com.gathin.tvhome.WorldTest'`
Expected: Kompilierfehler „cannot find symbol World“.

- [ ] **Step 3: `World.java` schreiben** (Werte aus dem Interfaces-Block; `parse(String)` als Basis von `current`), Strings in `values/strings.xml` und `values-de/strings.xml` ergänzen.

- [ ] **Step 4: Test grün**

Run: wie Schritt 2. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/World.java launcher/app/src/test/java/com/gathin/tvhome/WorldTest.java launcher/app/src/main/res/values/strings.xml launcher/app/src/main/res/values-de/strings.xml
git commit -m "Welten: World-Modell mit Farbtasten, Gruß-Wörtern und Persistenz-Key"
```

---

### Task 2: Umschalten per Farbtaste und Einstellungs-Zeile (noch ohne Optik)

**Files:**
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java` (`onCreate` nach Zeile 379 `Config cfg = Config.load(this)`; `dispatchKeyEvent` ab Zeile 1234)
- Create: `launcher/app/src/main/java/com/gathin/tvhome/WorldSwitch.java`
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/LauncherSettingsActivity.java` (nach `openOnStartChoice`, Zeile 126–130; `refresh()` Zeile ~282)
- Modify: `launcher/app/src/main/res/layout/activity_launcher_settings.xml` (neue Zeile `worldChoice` direkt über `followThemeChoice`, gleicher Stil wie `openOnStartChoice`)
- Test: `launcher/app/src/test/java/com/gathin/tvhome/WorldSwitchTest.java`

**Interfaces:**
- Consumes: `World` (Task 1), `Config.SETTINGS_NAME`, `Config.SETTINGS_REVISION_KEY`, `LauncherSettingsActivity.changed()` (bumpt die Revision; Home recreatet in `onResume` Zeile 927).
- Produces:
  - `final class WorldSwitch` mit `static World target(KeyEvent event, World current, boolean popupOpen)` – gibt die Zielwelt zurück, wenn `event.getAction()==ACTION_UP`, kein Popup, `World.forKeyCode(keyCode)` oder (bei `KEYCODE_UNKNOWN`) `World.forScanCode(event.getScanCode())` eine andere Welt als `current` liefert; sonst `null`.
  - In `MainActivity`: Feld `World world`; in `onCreate` direkt nach `Config.load`: `world = World.current(getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE))`; Methode `void switchWorld(World target)` – `target.save(prefs); recreate();` (Task 10 hängt hier den Übergang davor).
  - In `dispatchKeyEvent` ganz vorn (vor dem `reordering`-Zweig): bei `KEYCODE_PROG_*` oder `KEYCODE_UNKNOWN` einmal `Log.i("World", "key=" + keyCode + " scan=" + event.getScanCode())`; dann `World t = WorldSwitch.target(event, world, openPopup != null || reordering || libraryOpen)`; wenn `t != null` → `switchWorld(t); return true;`.
  - Einstellungen: Zeile `worldChoice` mit Text `getString(R.string.world) + ": " + Name`; Klick öffnet `AlertDialog` mit `setSingleChoiceItems` über die vier Namen (vorangestellter farbiger Punkt per `SpannableString` + `ForegroundColorSpan(keyColor)` auf „●“); Auswahl → `World.save` + `changed()` + `refresh()`. Untertitel `world_hint`.

- [ ] **Step 1: Test schreiben**

```java
public class WorldSwitchTest {
    private static KeyEvent up(int code) { return new KeyEvent(KeyEvent.ACTION_UP, code); }
    @Test public void sameWorldIsNoop() { assertNull(WorldSwitch.target(up(KeyEvent.KEYCODE_PROG_RED), World.MARVEL, false)); }
    @Test public void otherColourSwitches() { assertSame(World.HOGWARTS, WorldSwitch.target(up(KeyEvent.KEYCODE_PROG_YELLOW), World.CLEAN, false)); }
    @Test public void popupBlocksSwitch() { assertNull(WorldSwitch.target(up(KeyEvent.KEYCODE_PROG_YELLOW), World.CLEAN, true)); }
    @Test public void downEventIsIgnored() { assertNull(WorldSwitch.target(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_BLUE), World.CLEAN, false)); }
    @Test public void dpadIsIgnored() { assertNull(WorldSwitch.target(up(KeyEvent.KEYCODE_DPAD_LEFT), World.CLEAN, false)); }
}
```
Falls `new KeyEvent(...)` im JVM-Stub „Stub!“ wirft: `WorldSwitch.target(int action, int keyCode, int scanCode, World current, boolean popupOpen)` als testbare Überladung anbieten und die `KeyEvent`-Variante darauf delegieren.

- [ ] **Step 2: Fehlschlag sehen** – Run: `./gradlew testReleaseUnitTest -q --tests 'com.gathin.tvhome.WorldSwitchTest'`. Expected: „cannot find symbol WorldSwitch“.

- [ ] **Step 3: `WorldSwitch` schreiben, `MainActivity` und `LauncherSettingsActivity` wie im Interfaces-Block anbinden.** `openPopup`, `reordering`, Bibliotheks-Flag heißen in `MainActivity` bereits so (Zeile 1586 `openPopup`, 1247 `reordering`; Bibliothek: `appLibrary != null && appLibrary.isShowing()` – Namen vor Ort prüfen).

- [ ] **Step 4: Test grün** – Run wie Schritt 2. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Bauen und installieren** – Run: `./build-and-install.sh` (Env s. Global Constraints). Expected: `Installed.`

- [ ] **Step 6: Keycodes der echten Fernbedienung lesen** – Run: `tools/tv.py 'logcat -c'`; Patrick bitten, Rot, Grün, Gelb, Blau zu drücken; dann `tools/tv.py 'logcat -d -s World'`. Expected: vier Zeilen `key=183|184|185|186 scan=398..401` (Rot 183, Grün 184, Gelb 185, Blau 186). Kommt `key=0`, greift der Scancode-Pfad; die Beobachtung in die Spec § 3 eintragen.

- [ ] **Step 7: Umschalten prüfen** – Run: `tools/tv.py 'cat /data/data/com.gathin.tvhome/shared_prefs/launcher_settings.xml' 2>/dev/null || tools/tv.py 'run-as com.gathin.tvhome cat shared_prefs/launcher_settings.xml'`. Expected: `<string name="world">hogwarts</string>` nach Gelb. Home kam nach dem Druck sichtbar neu (kurzes Schwarz) – das ist in dieser Task noch gewollt.

- [ ] **Step 8: Einstellungen prüfen** – `tools/tv.py --screenshot shots/t2-settings.png` nach Öffnen der Launcher-Einstellungen (Settings-Pill lang drücken → Launcher settings). Expected: Zeile „Welt: Hogwarts“ mit gelbem Punkt sichtbar.

- [ ] **Step 9: Review-Focus 1** – Inputs-Pill öffnen (`DPAD_UP`, `DPAD_CENTER` per `tools/tv.py`), dann Patrick Rot drücken lassen; `tools/tv.py --screenshot shots/t2-popup.png`. Expected: Inputs-Menü noch offen, `world` unverändert.

- [ ] **Step 10: Commit**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/WorldSwitch.java launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java launcher/app/src/main/java/com/gathin/tvhome/LauncherSettingsActivity.java launcher/app/src/main/res/layout/activity_launcher_settings.xml launcher/app/src/test/java/com/gathin/tvhome/WorldSwitchTest.java docs/superpowers/specs/2026-10-25-vier-welten-launcher-design.md
git commit -m "Welten: Umschalten per Farbtaste und Einstellungs-Zeile, Keycodes der Philips-Fernbedienung geloggt"
```

---

### Task 3: Typografie, Farben, Gruß und Hero je Welt

**Files:**
- Create: `launcher/app/src/main/res/font/cormorant_garamond_light.ttf`, `cormorant_garamond_regular.ttf`, `cormorant_garamond_italic.ttf`, `oswald_light.ttf`, `oswald_medium.ttf`, `oswald_bold.ttf`, `michroma.ttf` (Latin-Subsets, von fonts.google.com; OFL-Texte als `launcher/FONT-LICENSE-OFL-cormorant.txt`, `-oswald.txt`, `-michroma.txt`)
- Modify: `launcher/app/src/main/res/values/fonts.xml` (neue `font_world_*`-Strings)
- Create: `launcher/app/src/main/java/com/gathin/tvhome/WorldTypography.java`
- Modify: `MainActivity.java`: `onCreate` nach dem Block Zeile 449–457 (Gruß/Uhr/Datum), `updateGreeting()` Zeile 2474–2482, `fillHero()` Zeile 1750, `root.setBackgroundColor` Zeile 414, Ambient in `setAmbient` Zeile 1887 (nur Farbe/Alpha-Quelle), `glass.setAccent` Zeile 424 und 1626–1631 (Welt-Akzent wie `festive` behandeln: `coloured` wird `false`, Akzent = `world.accent`)
- Test: `launcher/app/src/test/java/com/gathin/tvhome/WorldTypographyTest.java`

**Interfaces:**
- Consumes: `World`, `UiFont.get(Context,int)`, `liftTowardsWhite(int,float)` (MainActivity).
- Produces:
  - `final class WorldTypography` mit `static void apply(World w, TextView greeting, TextView clock, TextView date, View clockDivider, TextView heroName, TextView heroMeta)` – setzt Typeface, Farben, Letter-Spacing, Hero-Stil; bei `CLEAN` return ohne Änderung.
  - `static CharSequence heroText(World w, String label, Resources res)` – HOGWARTS: zweizeilig „ZAUBERSPIEGEL ZEIGT“ (klein, gesperrt, Gold) + Label kursiv; MARVEL/STARWARS: `label.toUpperCase(Locale)`; CLEAN: `label`.
  - `static String greetingText(World w, String base, boolean day)` – `"Nox · Guten Abend"`-Form: `prefix.isEmpty() ? base : prefix + " · " + base`, Suffix mit `" · "` dahinter.
  - `static int greetingColor(World w, int fallback)` – HOGWARTS `0xFFE8C87A`, MARVEL `0xFFE6B85A`, STARWARS `0xFF8FB3FF`, CLEAN `fallback`.
  - Marvel-Uhr: `LinearGradient`-Shader auf `clock.getPaint()` (Weiß→Gold→`0xFFA86D1F`), Star-Wars-Hero: `heroName.setRotationX(18f)` mit `setPivotY(height)` – im Spec § 4.4.

- [ ] **Step 1: Test schreiben**

```java
public class WorldTypographyTest {
    @Test public void greetingJoinsPrefixAndSuffixWithMiddleDot() {
        assertEquals("Nox · Guten Abend", WorldTypography.greetingText(World.HOGWARTS, "Guten Abend", false));
        assertEquals("Suit up · Guten Abend", WorldTypography.greetingText(World.MARVEL, "Guten Abend", true));
        assertEquals("Guten Abend · Äußerer Rand", WorldTypography.greetingText(World.STARWARS, "Guten Abend", true));
        assertEquals("Guten Abend", WorldTypography.greetingText(World.CLEAN, "Guten Abend", true));
    }
    @Test public void cleanKeepsFallbackColour() { assertEquals(0x12345678, WorldTypography.greetingColor(World.CLEAN, 0x12345678)); assertEquals(0xFF8FB3FF, WorldTypography.greetingColor(World.STARWARS, 0)); }
    @Test public void heroLabelCasing() { assertEquals("NETFLIX", WorldTypography.heroLabel(World.MARVEL, "Netflix")); assertEquals("Netflix", WorldTypography.heroLabel(World.CLEAN, "Netflix")); assertEquals("Netflix", WorldTypography.heroLabel(World.HOGWARTS, "Netflix")); }
}
```

- [ ] **Step 2: Fehlschlag sehen** – Run: `--tests 'com.gathin.tvhome.WorldTypographyTest'`. Expected: „cannot find symbol“.

- [ ] **Step 3: Schriften ablegen, `fonts.xml` erweitern, `WorldTypography` schreiben, in `MainActivity` an den genannten Stellen aufrufen.** `day` für den Gruß kommt aus Task 4 (`WorldScene.isDay()`); bis dahin `LocalTime.now().getHour() in 7..18`.

- [ ] **Step 4: Test grün** – Run wie Schritt 2.

- [ ] **Step 5: Bauen, installieren, je Welt Screenshot** – Patrick drückt Gelb/Rot/Blau/Grün, nach jedem `tools/tv.py --screenshot shots/t3-<welt>.png`. Expected: Schrift, Gruß-Wörter, Hero-Stil, Gruß-Farbe wie Spec § 4.4; Clean unverändert gegenüber `shots/02-home.png` (bis auf Uhr).

- [ ] **Step 6: Commit** – `git add launcher/app/src/main/res/font launcher/FONT-LICENSE-OFL-*.txt launcher/app/src/main/res/values/fonts.xml launcher/app/src/main/java/com/gathin/tvhome/WorldTypography.java launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java launcher/app/src/test/java/com/gathin/tvhome/WorldTypographyTest.java && git commit -m "Welten: Schrift, Farben, Gruß-Wörter und Hero-Stil je Welt"`

---

### Task 4: Szenen – Bilder zeichnen und Tag/Nacht anzeigen

**Files:**
- Create: `launcher/worlds/README.md`, `launcher/worlds/hogwarts/make_scene.py`, `launcher/worlds/marvel/make_scene.py`, `launcher/worlds/starwars/make_scene.py`, `launcher/worlds/common.py` (Gradient, Sterne, JPEG-Export ≤ 300 KB mit Qualitätsschleife)
- Create (generiert): `launcher/app/src/main/res/drawable-nodpi/world_hogwarts_day.jpg`, `world_hogwarts_night.jpg`, `world_marvel_day.jpg`, `world_marvel_night.jpg`, `world_starwars_day.jpg`, `world_starwars_night.jpg`
- Create: `launcher/app/src/main/java/com/gathin/tvhome/WorldScene.java`
- Modify: `MainActivity.java` `onCreate` nach Zeile 419 (`wallpaperPlayer`) und `minuteTick`/`onResume`/`onPause`
- Test: `launcher/app/src/test/java/com/gathin/tvhome/WorldSceneTest.java`

**Interfaces:**
- Consumes: `WallpaperMood` (Tag = morning|evening, Nacht = night; `WallpaperMood.current(...)`-Signatur vor Ort lesen, sonst `Sun` direkt mit `R.string.latitude/longitude`), `World`.
- Produces:
  - `final class WorldScene` mit `WorldScene(Context, FrameLayout root, World w)` – fügt bei `!w.isClean()` zwei `ImageView` (`CENTER_CROP`, RGB_565 via `BitmapFactory.Options.inPreferredConfig`) an Index 0/1 ein, zeigt das Bild der aktuellen Tageszeit; `void start()`, `void stop()`, `boolean isDay()`, `void update()` (vom Minute-Tick: bei Moodwechsel 300-ms-Crossfade über Alpha der oberen View, danach die untere entladen), `void forceMood(String auto|day|night)` (für Task 11), `void setListener(Runnable onMoodChanged)` (Overlay hört zu).
  - Reine Logik testbar: `static boolean isDay(int minuteOfDay, int sunrise, int sunset)` – `minuteOfDay >= sunrise && minuteOfDay < sunset + 30`.
  - `static int resFor(World w, boolean day)` → `R.drawable.world_<id>_<day|night>`, CLEAN → 0.
- Szenen-Inhalte (Spec § 4.3, Mockup v3 als Referenz): Hogwarts Nacht = Schloss auf Klippe rechts (Hauptturm + 6 Türme mit Spitzdächern, Zinnen, Viadukt mit 2 Bögen), Fenster `#FFD37A`, See mit Spiegelung ab 76 % Höhe, Mond bei (78 %, 20 %), 12 Sterne, Himmel `#06051A→#141020→#241A3A`; Tag = Große Halle: Decke in hellerem Violett-Grau mit Wolkenbändern, zwei Lichtschächte, zwei Eulen-Silhouetten oben links, Boden dunkel. Marvel Nacht = `radial #8E1222→#3A0810→#000`, Lichtkante bei 46 %, Skyline 16 Blöcke + Turm mit Leuchtfeuer bei (59 %, 28 %), Fensterraster; Tag = gleiche Skyline im warmen Gegenlicht `#FFB070` tief rechts, Himmel `#3A1010→#120306`. Star Wars Tag = Himmel `#000→#03040C→#2A1206→#6A2E0C→#1A0C04`, Sonnen (58 %, 65 %, r 7 %) und (70 %, 67 %, r 4,5 %), Dünen zwei Kurven, Kuppel bei 18 %, zwei Vaporatoren bei 28 %/32 %, Figur bei 23 %; Nacht = reiner Weltraum, 60 Sterne, Ringplanet rechts unten angeschnitten (Mittelpunkt 92 %, 112 %, r 22 %, Ring 32 % × 7 %, Farbe `#2B3A6B`).

- [ ] **Step 1: Test schreiben**

```java
public class WorldSceneTest {
    @Test public void dayRunsFromSunriseToHalfAnHourAfterSunset() {
        assertFalse(WorldScene.isDay(6 * 60 + 59, 7 * 60, 18 * 60 + 30));
        assertTrue(WorldScene.isDay(7 * 60, 7 * 60, 18 * 60 + 30));
        assertTrue(WorldScene.isDay(18 * 60 + 59, 7 * 60, 18 * 60 + 30));
        assertFalse(WorldScene.isDay(19 * 60, 7 * 60, 18 * 60 + 30));
    }
    @Test public void cleanHasNoScene() { assertEquals(0, WorldScene.resFor(World.CLEAN, true)); assertNotEquals(0, WorldScene.resFor(World.HOGWARTS, false)); }
}
```

- [ ] **Step 2: Fehlschlag sehen** – Run: `--tests 'com.gathin.tvhome.WorldSceneTest'`.

- [ ] **Step 3: Szenen-Skripte schreiben und Bilder erzeugen** – Run: `tools/venv/Scripts/pip install pillow numpy && for w in hogwarts marvel starwars; do tools/venv/Scripts/python launcher/worlds/$w/make_scene.py; done`. Expected: sechs JPEGs, jedes `ls -l` ≤ 300 KB; `python -c` Luminanz-Check aus `common.py` meldet obere Ecken (je 15 % × 15 %) und unteres Drittel mit mittlerer Luminanz ≤ 40.

- [ ] **Step 4: Bilder ansehen** – jedes JPEG mit dem Read-Tool öffnen; mit Mockup v3 vergleichen; nachbessern, bis Schloss/Reaktor-Skyline/Doppelsonnen klar erkennbar sind.

- [ ] **Step 5: `WorldScene` schreiben und in `MainActivity` anbinden** (`onCreate`: `worldScene = new WorldScene(this, root, world)`; `root.setBackgroundColor(world.isClean() ? cfg.backgroundColor : world.backgroundColor)`; `onResume`: `start()`, `onPause`: `stop()`, `minuteTick`: `update()`; `WorldTypography`-Gruß bekommt `worldScene.isDay()`).

- [ ] **Step 6: Test grün**, dann bauen und je Welt Screenshot `shots/t4-<welt>.png`. Expected: Szene sichtbar, UI darüber lesbar, Clean ohne Bild.

- [ ] **Step 7: Review-Focus 3 (Moodwechsel)** – vorerst über `tools/tv.py 'date'` planen: einen Screenshot 2 min vor und 2 min nach der berechneten Grenze (Sonnenuntergang Wohnort + 30 min; `python -c` mit `Sun`-Formel oder timeanddate) – oder nach Task 11 mit dem Testschalter wiederholen. Expected: anderes Bild, kein Schwarzbild dazwischen.

- [ ] **Step 8: Commit** – `git add launcher/worlds launcher/app/src/main/res/drawable-nodpi/world_*.jpg launcher/app/src/main/java/com/gathin/tvhome/WorldScene.java launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java launcher/app/src/test/java/com/gathin/tvhome/WorldSceneTest.java && git commit -m "Welten: gezeichnete Tag- und Nachtszenen mit Überblendung nach Sonnenstand"`

---

### Task 5: Dauerbewegung – Overlays je Welt

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/WorldOverlay.java` (abstrakte Basis), `HogwartsOverlay.java`, `MarvelOverlay.java`, `StarWarsOverlay.java`
- Modify: `MainActivity.java` (`onCreate` nach `WorldScene`; `onResume`/`onPause`; `reducedMotion`/`animations`)
- Test: `launcher/app/src/test/java/com/gathin/tvhome/OverlayPolicyTest.java`

**Interfaces:**
- Consumes: `World`, `WorldScene.isDay()`/Listener, `Motion`, Muster `PetalShowerView` (Felder-Arrays, `postInvalidateOnAnimation`, Hardware-Layer nur beim Spielen).
- Produces:
  - `abstract class WorldOverlay extends View`: `static final int REACTOR_KICK = 1`; `static WorldOverlay create(Context, World, boolean day)` (CLEAN → `null`), `void setDay(boolean)`, `void start()`, `void stop()`, `void pulse(int what)` (Task 8: Marvel `REACTOR_KICK`, andere Welten ignorieren es), Frame-Takt 33 ms über `postDelayed`, `invalidate(dirtyRect)` nur über den bewegten Bereichen.
  - `static boolean shouldRun(World w, boolean animations, boolean reducedMotion, boolean resumed, boolean screensaver)` – `!w.isClean() && animations && !reducedMotion && resumed && !screensaver`.
  - Hogwarts: 10 Kerzen (Positionen aus Mockup v3, Flacker-Zyklus 6–9 s per Kerze, Drift ±2 px / 9 s), 12 Sterne (Twinkle 3–4 s); `setDay(true)` zeichnet nur die Kerzen mit 60 % Größe. Marvel: Reaktor rechts bei (86 %, 50 %), Ø 10 % Bildhöhe, 10 Segmente, 3 Ringe, 1 U/18 s, Kern-Puls 2 s; Lichtkante atmet 4 s (Alpha 0,55↔0,9); Leuchtfeuer am Turm pulst 2 s. Star Wars: Hyperraum-Streifen in den äußeren 16 % Breite (Streifen alle 70 px, 100°, Geschwindigkeit 60 px/1,1 s), 15–20 Sterne twinkeln, Holo-Flackern-Signal alle 6–7 s (Callback `onHoloFlicker` für Task 6, 60 ms Alpha 0,6 auf Gruß/Uhr/Pills).

- [ ] **Step 1: Test schreiben**

```java
public class OverlayPolicyTest {
    @Test public void cleanNeverRuns() { assertFalse(WorldOverlay.shouldRun(World.CLEAN, true, false, true, false)); }
    @Test public void reducedMotionStops() { assertFalse(WorldOverlay.shouldRun(World.HOGWARTS, true, true, true, false)); }
    @Test public void animationsOffStops() { assertFalse(WorldOverlay.shouldRun(World.MARVEL, false, false, true, false)); }
    @Test public void pausedOrScreensaverStops() { assertFalse(WorldOverlay.shouldRun(World.STARWARS, true, false, false, false)); assertFalse(WorldOverlay.shouldRun(World.STARWARS, true, false, true, true)); }
    @Test public void normalCaseRuns() { assertTrue(WorldOverlay.shouldRun(World.STARWARS, true, false, true, false)); }
}
```

- [ ] **Step 2: Fehlschlag sehen** – Run: `--tests 'com.gathin.tvhome.OverlayPolicyTest'`.

- [ ] **Step 3: Basis und drei Overlays schreiben, in `MainActivity` einhängen** (View direkt über `WorldScene`, unter `heroArt`; Start/Stopp nach `shouldRun`; `ClockActivity`-Start = Screensaver-Flag).

- [ ] **Step 4: Test grün.**

- [ ] **Step 5: Bauen, je Welt zwei Screenshots im Abstand von 3 s** (`shots/t5-<welt>-a.png`, `-b.png`). Expected: sichtbarer Unterschied an Kerzen/Reaktor/Streifen; UI unverändert.

- [ ] **Step 6: Leistung** – Run: `tools/tv.py 'dumpsys gfxinfo com.gathin.tvhome | grep -E "Janky|90th|95th"'` nach 60 s Stillstand je Welt. Expected: 95th percentile ≤ 16 ms, Janky-Anteil ≤ 5 %. Sonst Frame-Takt auf 50 ms oder Partikelzahl senken.

- [ ] **Step 7: Commit** – `git add launcher/app/src/main/java/com/gathin/tvhome/WorldOverlay.java launcher/app/src/main/java/com/gathin/tvhome/HogwartsOverlay.java launcher/app/src/main/java/com/gathin/tvhome/MarvelOverlay.java launcher/app/src/main/java/com/gathin/tvhome/StarWarsOverlay.java launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java launcher/app/src/test/java/com/gathin/tvhome/OverlayPolicyTest.java && git commit -m "Welten: Dauerbewegung – Kerzen und Sterne, Reaktor und Lichtkante, Hyperraum und Holo-Flackern"`

---

### Task 6: Pills, Icons, HUD-Ecken, Scanlinien, Zierde

**Files:**
- Create: `launcher/app/src/main/res/drawable/ic_world_hog_candle.xml`, `ic_world_hog_owl.xml`, `ic_world_hog_star.xml` (22 dp Vektor, einfarbig, `?attr`-frei, Tint über `pill_text`), `pill_bg_hogwarts.xml`, `pill_bg_marvel.xml`, `pill_bg_starwars.xml` (Spec § 4.4: Hogwarts rund Rand `#C9A84C` + Außenring 15 %; Marvel 4 dp Ecken Rand `#78C8FF` 80 % + Innenschein; Star Wars 3 dp Ecken Rand `#8FB3FF` 70 %)
- Create: `launcher/app/src/main/java/com/gathin/tvhome/WorldChrome.java`
- Modify: `MainActivity.java` Zeilen 720–754 (Icon-only erzwingen: `iconOnlyButtons = cfg.iconOnlyButtons || world.forcesIconOnlyPills()`, ebenso `iconOnlyInputs`), nach Zeile 758 `WorldChrome.apply(...)`
- Modify: `launcher/app/src/main/res/layout/activity_main.xml`: unter `leftBlock` eine `TextView android:id="@+id/worldOrnament"` (GONE) und eine `View android:id="@+id/worldHud"` (GONE)

**Interfaces:**
- Consumes: `World`, `makeIconOnly(TextView)`, `setFestiveIcon(TextView, Drawable)`-Muster (Zeile 2707).
- Produces: `final class WorldChrome` mit `static void apply(World w, Activity a, TextView btnInputs, TextView btnWifi, TextView btnSettings, View worldHud, TextView worldOrnament, View scanlines)`: setzt Pill-Hintergründe und (Hogwarts) Icons, zeigt HUD-Ecken (Marvel, `HudCornersDrawable` als eigene `Drawable`-Klasse in derselben Datei), Ornament „⟡ ✦ ⟡“ (Hogwarts, Gold), Scanlinien-Fläche hinter Uhr/Datum (Star Wars, `repeating`-Shader 3 px, Alpha 8 %); CLEAN: return.

- [ ] **Step 1: Drawables und `WorldChrome` schreiben, `MainActivity` anbinden.** (Keine JVM-Testbarkeit; die Prüfung ist der Screenshot.)
- [ ] **Step 2: Bauen, je Welt Screenshot `shots/t6-<welt>.png` mit Fokus auf der Settings-Pill (`DPAD_UP` ×2, `DPAD_RIGHT` ×2).** Expected: Icon-only-Pills in den Welten, Text-Pills in Clean; Hogwarts-Medaillons mit Kerze/Eule/Stern; Marvel HUD-Ecken; Star-Wars-Scanlinien; Fokus auf Pill sichtbar.
- [ ] **Step 3: Commit** – `git add launcher/app/src/main/res/drawable/ic_world_* launcher/app/src/main/res/drawable/pill_bg_*.xml launcher/app/src/main/java/com/gathin/tvhome/WorldChrome.java launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java launcher/app/src/main/res/layout/activity_main.xml && git commit -m "Welten: Pills, Icons, HUD-Ecken, Scanlinien und Zierde je Welt"`

---

### Task 7: Dock-Stile und Fokusrahmen

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/WorldDockDecoration.java`
- Modify: `MainActivity.java` Zeile 574 (`new DockDecoration(...)` → `World.dockStyleSupported(cfg) && !world.isClean() ? new WorldDockDecoration(world, COLUMNS, density, cfg.dockOpacity) : new DockDecoration(...)`), Zeile 423 (`glass.setStyle(world.isClean() ? cfg.glassStyle : Config.GLASS_RIM)`), `onTileFocused` Zeile 1626–1631 (Welt-Akzent fest, kein App-Glow)
- Modify: `GlassView.java`: `void setBrackets(boolean on)` + `void setBracketsLocked(boolean locked)` (Marvel-Zielklammern: vier Eckwinkel 30 % Kantenlänge, 2 dp, Farbe = Rim; „offen“ 8 dp außerhalb, „gerastet“ 2 dp)
- Test: `launcher/app/src/test/java/com/gathin/tvhome/WorldDockGeometryTest.java`

**Interfaces:**
- Consumes: `DockDecoration.getItemOffsets`-Verhalten (6/12 dp), `AppAdapter.cornerRadius`, `AppAdapter.Holder.card`.
- Produces: `WorldDockDecoration extends RecyclerView.ItemDecoration` mit identischen `getItemOffsets` wie `DockDecoration` und `onDraw` je Welt: HOGWARTS Band-Verlauf `0xD91C162C→0xE60E0B18`, Rand 1 dp `#C9A84C`, Außenrahmen 4 dp `0xCC0A0814` + 1 dp `0x73C9A84C`, Eck-„❖“ als Pfad oben links/rechts, Streifen 3 px darunter in `#7A1B1B #C9A84C #1B4D2E #1E3A6B` (Viertel); MARVEL Metall `#2A2A2E→#121214`, Rand `#4A4A50`, Oberkante 2 dp `#E6B85A`, zwei Nieten Ø 5 dp außen unten, sechs Punkte Ø 6 dp links über dem Band in `#A855F7 #3B82F6 #EF4444 #F59E0B #22C55E #F97316`; STARWARS kein Band, Linie 1 dp `0x998FB3FF` unter den Kacheln, Skala Striche 1 dp alle 3,2 % der Dockbreite, Abschnitt unter der fokussierten Kachel weiß (`void setFocusedSlot(int)` aus `onTileFocused`). Statische Geometrie-Helfer: `static int stripeColorAt(float fraction)` (Hogwarts-Viertel), `static int tickCount(float dockWidthPx, float density)`.

- [ ] **Step 1: Test schreiben**

```java
public class WorldDockGeometryTest {
    @Test public void houseStripeQuarters() { assertEquals(0xFF7A1B1B, WorldDockDecoration.stripeColorAt(0.1f)); assertEquals(0xFFC9A84C, WorldDockDecoration.stripeColorAt(0.3f)); assertEquals(0xFF1B4D2E, WorldDockDecoration.stripeColorAt(0.6f)); assertEquals(0xFF1E3A6B, WorldDockDecoration.stripeColorAt(0.99f)); }
    @Test public void ticksEveryThreePointTwoPercent() { assertEquals(31, WorldDockDecoration.tickCount(1000f, 1f)); }
    @Test public void unsupportedConfigFallsBackToClean() { /* Config-Stub schwer: stattdessen World.dockStyleSupported mit den vier Bool/Int-Parametern überladen */ assertFalse(World.dockStyleSupported(true, true, false, 0)); assertFalse(World.dockStyleSupported(true, false, true, 0)); assertFalse(World.dockStyleSupported(true, false, false, 1)); assertTrue(World.dockStyleSupported(true, false, false, 0)); }
}
```

- [ ] **Step 2: Fehlschlag sehen**, **Step 3: implementieren**, **Step 4: grün** (`--tests 'com.gathin.tvhome.WorldDockGeometryTest'`).
- [ ] **Step 5: Bauen, je Welt Screenshot `shots/t7-<welt>.png` (Fokus Netflix) und `shots/t7-<welt>-plex.png` (`DPAD_RIGHT` ×3).** Expected: Dock wie Spec § 4.4; Marvel-Klammern gerastet; Star-Wars-Skala leuchtet unter Plex.
- [ ] **Step 6: Commit** – `git add launcher/app/src/main/java/com/gathin/tvhome/WorldDockDecoration.java launcher/app/src/main/java/com/gathin/tvhome/GlassView.java launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java launcher/app/src/main/java/com/gathin/tvhome/World.java launcher/app/src/test/java/com/gathin/tvhome/WorldDockGeometryTest.java && git commit -m "Welten: Dock-Stile (Goldrahmen, Metallleiste, Zielcomputer-Skala) und Fokusrahmen je Welt"`

---

### Task 8: Dock-Wechsel-Bewegung

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/WorldFocusMotion.java`, `SparkTrailView.java` (Hogwarts-Funken, Muster `PetalShowerView`), `ScanSweepView.java` (Marvel-Scan-Streifen)
- Modify: `MainActivity.java`: Spring-Setup Zeile 677–687 (Welt-Parameter), `onTileFocused` Zeile 1645–1648 (Star Wars: statt Spring drei Schritte à 60 ms via `ValueAnimator` mit `steps(3)`-Interpolator, dann Blip-Scale/Alpha 300 ms), `showHero` Zeile 1691 ff. (Hero-Übergang je Welt: Hogwarts 220-ms-Fade, Marvel Fade + Helligkeitsblitz, Star Wars Tipp-Effekt 45 ms/Zeichen)
- Test: `launcher/app/src/test/java/com/gathin/tvhome/WorldFocusMotionTest.java`

**Interfaces:**
- Consumes: `springX/springY`, `glass`, `GlassView.setBracketsLocked`, `WorldOverlay.pulse(REACTOR_KICK)`, `WorldDockDecoration.setFocusedSlot`.
- Produces: `final class WorldFocusMotion` mit `static SpringForce springFor(World w, boolean advanced)` (HOGWARTS Steifigkeit 260, Dämpfung 0,7; MARVEL 520/1,0; STARWARS `null` = Schrittmodus; CLEAN `null` = heutiger Code), `static float stepsInterpolate(float t, int steps)` (für `steps(3)`: `floor(t*steps)/steps`), `static long typingDelayMs(int charIndex)` = `45L * charIndex`, `void onDockMove(World w, int from, int to, Rect fromRect, Rect toRect)` – spielt Funken (10 Stück ab `fromRect`, 700 ms) bzw. Klammern-öffnen→springen→rasten (60/360/250 ms) + Scan 500 ms + `pulse` bzw. Skalen-Wechsel; nur für Dock-Positionen (`position < COLUMNS`), im Raster nur die Rahmenbewegung.

- [ ] **Step 1: Test schreiben**

```java
public class WorldFocusMotionTest {
    @Test public void stepsSnapToThirds() { assertEquals(0f, WorldFocusMotion.stepsInterpolate(0.2f, 3), 1e-6); assertEquals(1f/3, WorldFocusMotion.stepsInterpolate(0.4f, 3), 1e-6); assertEquals(1f, WorldFocusMotion.stepsInterpolate(1f, 3), 1e-6); }
    @Test public void typingIs45msPerChar() { assertEquals(0L, WorldFocusMotion.typingDelayMs(0)); assertEquals(315L, WorldFocusMotion.typingDelayMs(7)); }
    @Test public void cleanAndStarWarsHaveNoSpringOverride() { assertNull(WorldFocusMotion.springFor(World.CLEAN, false)); assertNull(WorldFocusMotion.springFor(World.STARWARS, false)); assertNotNull(WorldFocusMotion.springFor(World.HOGWARTS, false)); }
}
```

- [ ] **Step 2–4: Fehlschlag, implementieren, grün** (`--tests 'com.gathin.tvhome.WorldFocusMotionTest'`).
- [ ] **Step 5: Bauen; je Welt `tools/tv.py --screenshot shots/t8-<welt>-mid.png DPAD_RIGHT --wait 0.25` und `shots/t8-<welt>-end.png` (ohne Taste, 1 s später).** Expected: `mid` zeigt Funken / offene Klammern+Scan / Rahmen auf Zwischenschritt; `end` zeigt Ruhezustand auf Prime Video, Hero „Prime Video“ (Star Wars: fertig getippt).
- [ ] **Step 6: D-Pad-Gefühl mit Patrick** an der echten Fernbedienung (schnelles Rechts-Links ×10 je Welt). Expected: kein Hängen, kein Rahmen-Versatz; sonst Effekte bei Tastenwiederholung abbrechen (`cancel()` vor Neustart).
- [ ] **Step 7: Commit** – `git add launcher/app/src/main/java/com/gathin/tvhome/WorldFocusMotion.java launcher/app/src/main/java/com/gathin/tvhome/SparkTrailView.java launcher/app/src/main/java/com/gathin/tvhome/ScanSweepView.java launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java launcher/app/src/test/java/com/gathin/tvhome/WorldFocusMotionTest.java && git commit -m "Welten: Dock-Wechsel – Funkenspur, Zielerfassung, Hologramm-Blip und tippender Hero"`

---

### Task 9: App-Start-Effekte

**Files:**
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/AppAdapter.java` Zeile 483 (`launch(e)` → `listener.onTileLaunch(h.getBindingAdapterPosition(), h.card, () -> launch(e))`) und `interface TileListener` (+ `void onTileLaunch(int position, View card, Runnable start)`)
- Create: `launcher/app/src/main/java/com/gathin/tvhome/WorldLaunchMotion.java`, `LaunchFxView.java` (ein Overlay für Funkenwirbel / Ringe+Lichtstreifen / Hyperraum-Streaks + Weiß/Schwarz-Blende)
- Modify: `MainActivity.java` (implementiert `onTileLaunch`: CLEAN oder `reducedMotion` oder `!animations` → `start.run()` sofort; sonst `WorldLaunchMotion.play(world, card, root, start)`)
- Test: `launcher/app/src/test/java/com/gathin/tvhome/LaunchGuardTest.java`

**Interfaces:**
- Produces: `final class WorldLaunchMotion` mit `static long durationMs(World w)` (HOGWARTS 1200, MARVEL 1300 = 1000 + 300 Weiß, STARWARS 1250, CLEAN 0), `static boolean shouldPlay(World w, boolean animations, boolean reducedMotion)`, Instanz-Guard `boolean begin(World w, Runnable start)` (false, solange einer läuft) und `void finish()` (ruft `start` genau einmal und gibt frei), darauf aufbauend `void play(World w, View card, FrameLayout root, Runnable start)` mit Guard `playing` (zweiter Aufruf während `playing` wird verschluckt; Review-Focus 2) und Timeout-Sicherung: `start` läuft spätestens nach `durationMs + 200`, auch wenn eine Animation abgebrochen wurde; nach `start.run()` wird die Blende 400 ms später entfernt (die App liegt dann vorn).
- Zeitplan je Welt wörtlich aus Spec § 4.7.

- [ ] **Step 1: Test schreiben**

```java
public class LaunchGuardTest {
    @Test public void cleanAndReducedMotionStartImmediately() { assertFalse(WorldLaunchMotion.shouldPlay(World.CLEAN, true, false)); assertFalse(WorldLaunchMotion.shouldPlay(World.MARVEL, true, true)); assertFalse(WorldLaunchMotion.shouldPlay(World.MARVEL, false, false)); assertTrue(WorldLaunchMotion.shouldPlay(World.MARVEL, true, false)); }
    @Test public void durationsStayUnderLimit() { for (World w : World.values()) assertTrue(WorldLaunchMotion.durationMs(w) <= 1300); assertEquals(0, WorldLaunchMotion.durationMs(World.CLEAN)); }
    @Test public void secondPressWhilePlayingIsSwallowed() {
        WorldLaunchMotion m = new WorldLaunchMotion();
        int[] starts = {0};
        assertTrue(m.begin(World.HOGWARTS, () -> starts[0]++));   // begin(): nur der Guard, ohne Views
        assertFalse(m.begin(World.HOGWARTS, () -> starts[0]++));
        m.finish();
        assertEquals(1, starts[0]);
    }
}
```

- [ ] **Step 2–4: Fehlschlag, implementieren, grün** (`--tests 'com.gathin.tvhome.LaunchGuardTest'`).
- [ ] **Step 5: Bauen; je Welt auf Prime Video (`DPAD_RIGHT`) `tools/tv.py --screenshot shots/t9-<welt>-mid.png DPAD_CENTER --wait 0.5`, dann `tools/tv.py 'dumpsys activity activities | grep topResumedActivity'`, dann `tools/tv.py 'input keyevent HOME'`.** Expected: `mid` zeigt Wirbel / Ringe+Lichtstreifen / Streaks; danach `topResumedActivity` = Prime Video; Home kehrt ohne Reste der Blende zurück.
- [ ] **Step 6: Review-Focus 2** – `tools/tv.py 'input keyevent DPAD_CENTER; input keyevent DPAD_CENTER'`, dann `dumpsys activity activities | grep -c "amazon"`. Expected: genau ein Prime-Video-Task; Home-Logcat ohne Exception.
- [ ] **Step 7: Commit** – `git add launcher/app/src/main/java/com/gathin/tvhome/AppAdapter.java launcher/app/src/main/java/com/gathin/tvhome/WorldLaunchMotion.java launcher/app/src/main/java/com/gathin/tvhome/LaunchFxView.java launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java launcher/app/src/test/java/com/gathin/tvhome/LaunchGuardTest.java && git commit -m "Welten: App-Start – Nox-Funkenwirbel, Reaktor-Ringe mit Lichtstreifen, Hyperraum-Sprung"`

---

### Task 10: Übergang beim Weltenwechsel (zwei Hälften um `recreate()`)

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/WorldTransition.java`
- Modify: `MainActivity.java`: `switchWorld` (Task 2) → `WorldTransition.leave(world, target, root, () -> { target.save(prefs); prefs.edit().putString(WorldTransition.PENDING_KEY, target.id).commit(); recreate(); })`; in `onCreate` nach dem Aufbau: `WorldTransition.enterIfPending(world, prefs, root, worldOverlay, sceneView, tilesReadyCallback)` – ersetzt die Kachel-Entrance (`tile_enter`) einmalig.
- Test: `launcher/app/src/test/java/com/gathin/tvhome/WorldTransitionTest.java`

**Interfaces:**
- Produces: `static final String PENDING_KEY = "world_transition_pending"`; `static long leaveMs(World target)` (CLEAN 250, HOGWARTS 300, MARVEL 250, STARWARS 400), `static long enterMs(World target)` (CLEAN 250, HOGWARTS 600 = Kerzen 10 × 60 ms, MARVEL 500, STARWARS 300), Summe ≤ 900; `static boolean consumePending(SharedPreferences prefs, World current)` – true genau dann, wenn `PENDING_KEY == current.id` (und entfernt den Key); bei `reducedMotion` oder `!animations`: `leave` ruft sofort den Runnable, `enter` tut nichts (Hart-Schnitt; Review-Focus 4).
- Inhalte wörtlich Spec § 4.8.

- [ ] **Step 1: Test schreiben**

```java
public class WorldTransitionTest {
    @Test public void totalStaysUnder900ms() { for (World w : World.values()) assertTrue(WorldTransition.leaveMs(w) + WorldTransition.enterMs(w) <= 900); }
    @Test public void pendingIsConsumedOnlyByTheTargetWorld() {
        FakePrefs p = new FakePrefs(); p.put(WorldTransition.PENDING_KEY, "marvel");       // kleine Map-basierte SharedPreferences-Attrappe im Test
        assertFalse(WorldTransition.consumePending(p, World.HOGWARTS));
        assertTrue(WorldTransition.consumePending(p, World.MARVEL));
        assertFalse(WorldTransition.consumePending(p, World.MARVEL));
    }
}
```
`FakePrefs`: minimale `SharedPreferences`-Implementierung über `HashMap` (nur `getString`, `edit().putString/remove/commit`), in `src/test` ablegen; wenn die Stub-Schnittstelle zu groß ist, `consumePending(String pendingValue, World current)` als reine Funktion testen und die Prefs-Variante darauf delegieren.

- [ ] **Step 2–4: Fehlschlag, implementieren, grün** (`--tests 'com.gathin.tvhome.WorldTransitionTest'`).
- [ ] **Step 5: Bauen; Patrick drückt Blau, währenddessen `tools/tv.py --screenshot shots/t10-sw-enter.png --wait 0.2`; dann Gelb/Rot/Grün ebenso.** Expected: kein hartes Schwarz zwischen den Welten außer bei Clean; Star Wars kommt per Hyperraum, Hogwarts zündet Kerzen, Marvel zeichnet HUD; nach ≤ 1 s steht die neue Haut.
- [ ] **Step 6: Review-Focus 4** – `tools/tv.py 'content write --uri content://com.gathin.tvhome.theme/config.json'` mit `{"reduced_motion": true}` (per `tools/theme.sh` mit Mini-Theme-Ordner im Scratchpad), Farbtaste drücken. Expected: Hart-Schnitt, Welt wechselt, keine Overlays. Danach `tools/theme.sh --reset`.
- [ ] **Step 7: Commit** – `git add launcher/app/src/main/java/com/gathin/tvhome/WorldTransition.java launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java launcher/app/src/test/java/com/gathin/tvhome/WorldTransitionTest.java launcher/app/src/test/java/com/gathin/tvhome/FakePrefs.java && git commit -m "Welten: Übergang beim Wechsel in zwei Hälften um den Neustart der Home-Activity"`

---

### Task 11: Testschalter Szene, Doku, Fork-README

**Files:**
- Modify: `LauncherSettingsActivity.java` (langes OK auf `worldChoice` → Dialog „Szene“: Auto / Tag / Nacht; schreibt `WorldScene.FORCE_KEY = "world_scene_force"` mit `auto|day|night`, `changed()`), `WorldScene.java` (liest den Key in `onCreate`-Pfad und löscht ihn beim nächsten Prozessstart: Flag in einer `static boolean` der Activity-Klasse, nicht in Prefs)
- Modify: `backup/README.md` (Abschnitt „Welten“), `launcher/worlds/README.md` (Bilder neu bauen, Seeds, Regeln), `README.md` des Forks: ganz oben ein kurzer Kasten „Dieser Fork: Vier Welten (Branch `vier-welten`)“ mit Link auf die Spec

- [ ] **Step 1: Testschalter einbauen; bauen; auf Hogwarts `Szene: Tag` wählen, Home öffnen, `shots/t11-hog-day.png`; `Szene: Nacht`, `shots/t11-hog-night.png`; ebenso Marvel und Star Wars.** Expected: sechs korrekte Bilder; Gruß „Lumos“ bei Tag, „Nox“ bei Nacht; Overlay-Variante passt (Kerzen kleiner am Tag).
- [ ] **Step 2: Review-Focus 3 wiederholen** – auf `Auto` zurück, dann mit `tools/tv.py 'date'` die Grenze abwarten oder per `tools/tv.py 'settings put global auto_time 0; date MMDDhhmmYYYY'` kurz vor Sonnenuntergang + 30 min setzen (danach `auto_time 1`). Expected: Überblendung sichtbar, kein Schwarzbild, Gruß wechselt mit dem nächsten Minute-Tick.
- [ ] **Step 3: Doku schreiben.**
- [ ] **Step 4: Commit** – `git add launcher/app/src/main/java/com/gathin/tvhome/LauncherSettingsActivity.java launcher/app/src/main/java/com/gathin/tvhome/WorldScene.java backup/README.md launcher/worlds/README.md README.md && git commit -m "Welten: Szenen-Testschalter, Doku für Fork und TV"` (Hinweis: `backup/` ist gitignored – prüfen; wenn ja, Abschnitt stattdessen nach `launcher/worlds/README.md` und `backup/README.md` nur lokal.)

---

### Task 12: Abnahme auf dem TV (läuft **zuletzt**, nach Task 15)

**Files:** keine Codeänderung außer Nachbesserungen; Ergebnisse in `docs/superpowers/plans/2026-10-25-vier-welten-abnahme.md`.

- [ ] **Step 1: Clean-Regression** – Grün drücken; `tools/tv.py 'am force-stop com.gathin.tvhome; am start -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity'`, innerhalb von 40 s (vor dem ersten Minuten-Tick, OLED-Versatz noch 0) `tools/tv.py --screenshot shots/t12-clean.png`; `tools/venv/Scripts/pip install -r tools/requirements-perf.txt`; `tools/venv/Scripts/python tools/screendiff.py shots/t14-clean-baseline.png shots/t12-clean.png --mask 90,40,620,260 --out shots/t12-clean-diff.png`. Expected: `identical`. (Baseline aus Task 14 Schritt 4, weil die Ambilight-Pill und die Wetterzeile Clean absichtlich verändern; Maske = Gruß/Uhr/Datum/Wetter; Netflix-Fokus.)
- [ ] **Step 2: Kaltstart** – je Welt: `tools/tv.py 'am force-stop com.gathin.tvhome; am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity | grep TotalTime'` dreimal, Mittel bilden. Expected: Welt − Clean ≤ 300 ms.
- [ ] **Step 3: Speicher** – nach 2 min Home je Welt `tools/tv.py 'dumpsys meminfo com.gathin.tvhome | grep "TOTAL PSS"'`. Expected: Welt − Clean ≤ 25 MB.
- [ ] **Step 4: Frames** – `tools/tv.py 'dumpsys gfxinfo com.gathin.tvhome reset'`, 20× `DPAD_RIGHT/LEFT` per `tools/tv.py`, dann `dumpsys gfxinfo com.gathin.tvhome | grep -E "Janky|95th"`. Expected: 95th ≤ 16 ms, Janky ≤ 5 %.
- [ ] **Step 5: Echte Fernbedienung** – Patrick: alle vier Farbtasten, Home, Lautstärke halten, Netflix-Taste, Standby/Ein (landet auf Home?). Expected: alles wie vor dem Fork; Welten wechseln.
- [ ] **Step 6: Persistenz** – `tools/theme.sh --reset`, `am force-stop`, `tools/tv.py --reboot`, warten, Screenshot. Expected: gewählte Welt steht noch.
- [ ] **Step 7: Logs** – `tools/tv.py 'logcat -d | grep -iE "FATAL|AndroidRuntime|OutOfMemory" | tail -5'`. Expected: leer.
- [ ] **Step 8: OLED-Check** – je Welt 10 min stehen lassen, dann Screenshot; die hellste statische Fläche (Sonnen, Mond, Reaktor-Kern) messen (`python -c` Luminanz > 153 = 60 %) – Fläche ≤ 2 % des Bildes. Expected: eingehalten; sonst Alpha der Quelle senken.
- [ ] **Step 9: Abnahme-Notiz schreiben, committen, Branch pushen** – `git add docs/superpowers/plans/2026-10-25-vier-welten-abnahme.md && git commit -m "Welten: Abnahme auf dem Philips 65OLED934" && git push origin vier-welten`.

---

### Task 13: OLED-Versatz und Uhr-Screensaver

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/OledShift.java`
- Modify: `launcher/app/src/main/res/values/config.xml` (neu `<bool name="oled_shift">true</bool>` mit Kommentar; `home_idle_clock_minutes` 0 → 10; `clock_glow` true → false), `Config.java` (Feld `oledShift`, Theme-Key `"oled_shift"`), `MainActivity.java` (`minuteTick` Zeile 359: `oledShift.next()`; `onCreate`: Instanz mit den Views; `onPause`: `reset()`), `themes/README.md` (Zeile für `oled_shift`)
- Test: `launcher/app/src/test/java/com/gathin/tvhome/OledShiftTest.java`

**Interfaces:**
- Produces: `final class OledShift` mit `OledShift(List<View> views, Random rnd, boolean enabled)`, `static int[] nextOffset(Random rnd, int[] previous)` – zieht X und Y aus −3…+3 px (`density` wird außerhalb multipliziert), nie zweimal derselbe Punkt, `void next()` (animiert alle Views mit `animate().translationX/Y(...)`, 400 ms, `DecelerateInterpolator`; bei `!enabled` nichts), `void reset()` (Translation 0, ohne Animation), `int[] current()`. Views: `leftBlock`, `topButtons`, `hero`, `heroArt`, `ambient`, `grid`, `glass`, `worldHud`, `worldOrnament`, `stats`/`weather`. Der Fokusrahmen bekommt denselben Versatz wie `grid`, weil `onTileFocused` mit Layout-Koordinaten rechnet und Translationen nicht sieht: `glassScrollShift`-Mechanik vor Ort prüfen, sonst Versatz auf `glass` als zusätzliche Translation addieren (eigene `FloatProperty`, nicht die der Springs).
- Startwert 0; erster Versatz beim ersten Minuten-Tick (Clean-Regression in Task 12 misst vorher).

- [ ] **Step 1: Test schreiben**

```java
public class OledShiftTest {
    @Test public void offsetsStayWithinThreePixels() { Random r = new Random(7); int[] p = {0, 0}; for (int i = 0; i < 500; i++) { p = OledShift.nextOffset(r, p); assertTrue(Math.abs(p[0]) <= 3 && Math.abs(p[1]) <= 3); } }
    @Test public void neverRepeatsThePreviousPoint() { Random r = new Random(3); int[] p = {0, 0}; for (int i = 0; i < 500; i++) { int[] n = OledShift.nextOffset(r, p); assertFalse(n[0] == p[0] && n[1] == p[1]); p = n; } }
    @Test public void disabledNeverMoves() { OledShift s = new OledShift(java.util.Collections.emptyList(), new Random(1), false); s.next(); assertArrayEquals(new int[]{0, 0}, s.current()); }
}
```

- [ ] **Step 2–4: Fehlschlag, implementieren, grün** (`--tests 'com.gathin.tvhome.OledShiftTest'`).
- [ ] **Step 5: Bauen; `tools/tv.py --screenshot shots/t13-a.png`, 65 s warten, `shots/t13-b.png`; `tools/venv/Scripts/python tools/screendiff.py shots/t13-a.png shots/t13-b.png --mask 90,40,620,260`.** Expected: `different` mit kleinem Versatz überall (Dock, Pills), Fokusrahmen weiterhin exakt auf der Kachel (Ausschnitt mit dem Read-Tool prüfen).
- [ ] **Step 6: Screensaver** – Home 10 min unberührt lassen (oder vorübergehend `home_idle_clock_minutes` per Mini-Theme auf 1): `tools/tv.py 'dumpsys activity activities | grep mResumedActivity'`. Expected: `ClockActivity`; eine Taste → Home zurück. Danach Theme zurücksetzen.
- [ ] **Step 7: Commit** – `git add launcher/app/src/main/java/com/gathin/tvhome/OledShift.java launcher/app/src/main/java/com/gathin/tvhome/Config.java launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java launcher/app/src/main/res/values/config.xml themes/README.md launcher/app/src/test/java/com/gathin/tvhome/OledShiftTest.java && git commit -m "OLED: Versatz der Oberfläche jede Minute, Uhr-Screensaver nach 10 Minuten"`

---

### Task 14: Ambilight-Pill

**Files:**
- Create: `launcher/app/src/main/res/drawable/ic_ambilight.xml` (22 dp: Rechteck mit sechs kurzen Strahlen, Vektor, eigene Zeichnung), `ic_ambilight_pill.xml` falls die Pills eine eigene Größe nutzen (wie `ic_home_settings_pill`)
- Modify: `activity_main.xml` (neue `TextView android:id="@+id/btnAmbilight"` mit `style="@style/TopButton"` direkt nach `btnInputs`, `visibility="gone"`), `config.xml` (`<string name="ambilight_action" translatable="false">org.droidtv.action.AMBILIGHT_OSD</string>`), `Config.java` (Feld `ambilightAction`, Theme-Key), `MainActivity.java` (nach Zeile 691: sichtbar und verdrahtet, wenn `resolveActivity(new Intent(action))` nicht null; `pillKeys` und `advancedEffects`-Liste um die Pill ergänzen; `makeIconOnly` nach Welt-Regel; `showTopMenu` Zeile 3147: Zeile „Ambilight“), `WorldChrome.apply` (Pill-Hintergrund), `AndroidManifest.xml` `<queries>` (`<intent><action android:name="org.droidtv.action.AMBILIGHT_OSD"/></intent>`, sonst `resolveActivity` auf Android 11 null), Strings `ambilight` (en/de), `themes/README.md`
- Test: JVM-Test nur, wenn `Config` bereits JVM-testbar ist (prüfen: gibt es eine `ConfigTest`?); sonst ist die Prüfung der Screenshot.

- [ ] **Step 1: Implementieren wie oben.**
- [ ] **Step 2: Bauen; `tools/tv.py --screenshot shots/t14-pill.png DPAD_UP DPAD_UP DPAD_RIGHT`.** Expected: Pill „Ambilight“ zwischen Quellen und Netz, fokussiert.
- [ ] **Step 3: `tools/tv.py 'input keyevent DPAD_CENTER'`, 2 s warten, `tools/tv.py 'dumpsys activity activities | grep mResumedActivity'`.** Expected: `org.droidtv.settings/.AmbilightOsd`; `BACK` kehrt zu Home.
- [ ] **Step 4: Clean-Baseline neu nehmen** – Grün; `am force-stop` + Start wie Task 12 Schritt 1; innerhalb 40 s `tools/tv.py --screenshot shots/t14-clean-baseline.png`. (Diese Aufnahme ist die Referenz der Abnahme.)
- [ ] **Step 5: Commit** – `git add launcher/app/src/main/res/drawable/ic_ambilight*.xml launcher/app/src/main/res/layout/activity_main.xml launcher/app/src/main/res/values/config.xml launcher/app/src/main/res/values/strings.xml launcher/app/src/main/res/values-de/strings.xml launcher/app/src/main/java/com/gathin/tvhome/Config.java launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java launcher/app/src/main/java/com/gathin/tvhome/WorldChrome.java launcher/app/src/main/AndroidManifest.xml themes/README.md && git commit -m "Ambilight-Pill: öffnet das Philips-Ambilight-Menü, konfigurierbar über ambilight_action"`

---

### Task 15: Wetterzeile (Open-Meteo)

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/Weather.java`, `WeatherWords.java`
- Modify: `AndroidManifest.xml` (`<uses-permission android:name="android.permission.INTERNET" />` mit Kommentar „nur Wetterzeile, Spec § 4.10c“), `activity_main.xml` (`TextView android:id="@+id/weather"` unter `date`, Stil wie `stats` Zeile 297–310, `visibility="gone"`), `config.xml` (`<bool name="weather">true</bool>`), `Config.java` (Feld `weather`, Theme-Key), `MainActivity.java` (`onResume`: `weather.start()`, `onPause`: `weather.stop()`; Welt-Typografie auch auf `weather`; `OledShift`-Liste), Strings `weather_line` („%1$s° · %2$s“) und WMO-Wörter (de/en), `README.md` des Forks (Abschnitt „Was der Launcher ins Netz sendet“), `themes/README.md`
- Test: `launcher/app/src/test/java/com/gathin/tvhome/WeatherTest.java`

**Interfaces:**
- Produces: `final class Weather` mit `Weather(Context, TextView line, String latitude, String longitude, boolean enabled)`; `static String url(String lat, String lon)` = `https://api.open-meteo.com/v1/forecast?latitude=<lat>&longitude=<lon>&current=temperature_2m,weather_code&timezone=auto`; `static Reading parse(String json)` (`temperature_2m` → `int` gerundet, `weather_code` → `int`; `org.json` ist im JVM-Test nur ein Stub → `parse` mit minimalem handgeschriebenem Scanner für genau diese zwei Zahlenfelder, `null` bei allem anderen), `static boolean fresh(long savedAtMs, long nowMs)` = Alter < 2 h, `static long nextDelayMs(boolean lastFailed)` = 30 min bzw. 5 min; `void start()` (zeigt Cache wenn `fresh`, plant Abruf), `void stop()`; Abruf per `HttpURLConnection` auf `Executors.newSingleThreadExecutor()`, Timeouts 5 s, Ergebnis per `Handler` auf den Main-Thread; Cache in `SharedPreferences("weather")` (`temp`, `code`, `at`). Ohne Koordinaten oder `!enabled`: `start()` tut nichts, View bleibt `GONE`. Ein `Log.i("Weather", …)` je Abruf mit Dauer und Status, ohne Koordinaten.
- `final class WeatherWords`: `static String key(int wmoCode)` – 0 `wmo_clear`, 1–2 `wmo_fair`, 3 `wmo_cloudy`, 45/48 `wmo_fog`, 51–57 `wmo_drizzle`, 61–67 `wmo_rain`, 71–77 `wmo_snow`, 80–82 `wmo_showers`, 85–86 `wmo_snow_showers`, 95–99 `wmo_thunder`, sonst `wmo_unknown`; `static int stringRes(Resources, String key)` über `getIdentifier`. Deutsch: klar, heiter, bewölkt, Nebel, Niesel, Regen, Schnee, Schauer, Schneeschauer, Gewitter, „—“.

- [ ] **Step 1: Test schreiben**

```java
public class WeatherTest {
    @Test public void urlUsesTwoDecimalsAndCurrentFields() { assertEquals("https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,weather_code&timezone=auto", Weather.url("52.52", "13.41")); }
    @Test public void parseReadsTemperatureAndCode() { Weather.Reading r = Weather.parse("{\"current\":{\"time\":\"2026-10-25T19:15\",\"temperature_2m\":11.6,\"weather_code\":3}}"); assertEquals(12, r.temperature); assertEquals(3, r.code); }
    @Test public void parseRejectsGarbage() { assertNull(Weather.parse("<html>")); assertNull(Weather.parse("{\"current\":{}}")); }
    @Test public void freshnessIsTwoHours() { assertTrue(Weather.fresh(0, 2 * 3600_000L - 1)); assertFalse(Weather.fresh(0, 2 * 3600_000L)); }
    @Test public void retryIsFiveMinutesAfterFailure() { assertEquals(30 * 60_000L, Weather.nextDelayMs(false)); assertEquals(5 * 60_000L, Weather.nextDelayMs(true)); }
    @Test public void wmoWords() { assertEquals("wmo_clear", WeatherWords.key(0)); assertEquals("wmo_rain", WeatherWords.key(63)); assertEquals("wmo_thunder", WeatherWords.key(99)); assertEquals("wmo_unknown", WeatherWords.key(42)); }
}
```

- [ ] **Step 2–4: Fehlschlag, implementieren, grün** (`--tests 'com.gathin.tvhome.WeatherTest'`).
- [ ] **Step 5: Bauen; nach 10 s `tools/tv.py --screenshot shots/t15-weather.png`.** Expected: Zeile „<Temperatur>° · <Wort>“ unter dem Datum, in Welt-Schrift; `tools/tv.py 'dumpsys package com.gathin.tvhome | grep -c android.permission.INTERNET'` = 1 (nur diese Berechtigung neu).
- [ ] **Step 6: Netz-Nachweis** – `tools/tv.py 'logcat -d -s Weather'`. Expected: genau ein Abruf nach dem Start, kein weiterer innerhalb von 30 min; nach `am force-stop` + Start innerhalb 2 h: Zeile sofort da, Abruf erst nach Plan.
- [ ] **Step 7: Fehlerpfad** – kein WLAN-Trennen per adb möglich; der JVM-Test `parseRejectsGarbage` deckt den Parser, Logcat den nächsten echten Ausfall. Die Zeile bleibt bei Fehler auf dem letzten Wert, nie leer flackernd (Code-Review-Punkt).
- [ ] **Step 8: Commit** – `git add launcher/app/src/main/java/com/gathin/tvhome/Weather.java launcher/app/src/main/java/com/gathin/tvhome/WeatherWords.java launcher/app/src/main/AndroidManifest.xml launcher/app/src/main/res/layout/activity_main.xml launcher/app/src/main/res/values/config.xml launcher/app/src/main/res/values/strings.xml launcher/app/src/main/res/values-de/strings.xml launcher/app/src/main/java/com/gathin/tvhome/Config.java launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java README.md themes/README.md launcher/app/src/test/java/com/gathin/tvhome/WeatherTest.java && git commit -m "Wetterzeile: Temperatur und Wetterwort von Open-Meteo unter dem Datum, alle 30 Minuten"`

**Reihenfolge der Ausführung:** 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 13 → 14 → 15 → 12.

---

### Task 0: Schalter in `config.xml` (vor Task 1)

**Files:**
- Modify: `launcher/app/src/main/res/values/config.xml`: `tile_menu` → true, `app_library` → true, `library_button` → true, `vpn_app` → `de.mobileconcepts.cyberghost`, `wifi_status_color` → true, `wifi_status_warnings` → true, `launcher_settings_button` → true, `key_filter` → true.

- [ ] **Step 1: Werte setzen, bauen, installieren.**
- [ ] **Step 2: `tools/tv.py --screenshot shots/t0-home.png`; `shots/t0-library.png` nach `DPAD_UP DPAD_UP DPAD_RIGHT DPAD_RIGHT DPAD_CENTER`** (Bibliotheks-Button in der Top-Leiste). Expected: Netz-Pill grün, Bibliothek öffnet, Einstellungs-Pill zeigt das Launcher-Icon.
- [ ] **Step 3: Patrick: Lautstärke halten (Button Mapper noch an → Einzelschritt erwartet, das ist bekannt), Kachel lange OK → Tile-Menü.**
- [ ] **Step 4: Commit** – `git add launcher/app/src/main/res/values/config.xml && git commit -m "config.xml: Tile-Menü, App-Bibliothek, VPN-Pill, Netzstatus-Farben, Launcher-Einstellungen, Tastenfilter an"`

---

### Task 16: Tastenbelegung mit Doppelklick, Button Mapper ablösen (nach Task 15, vor Task 12)

**Files:**
- Modify: `launcher/app/src/main/java/com/gathin/tvhome/RemoteKeys.java` (Parser: Präfix `double:`; Maps `doubleKeycodes`, `doubleScancodes` analog zu den `hold:`-Maps), `HomeKeyService.java` und `InputKeyService.java` (Doppelklick-Erkennung dort, wo `hold:` ausgewertet wird), `config.xml` (`app_keys` mit den gemessenen Einträgen), `themes/README.md` (Zeile zu `double:`)
- Test: `launcher/app/src/test/java/com/gathin/tvhome/RemoteKeysDoubleTest.java` (es gibt bereits Tests zu `RemoteKeys`; daneben legen)

**Interfaces:**
- Produces in `RemoteKeys`: `static final long DOUBLE_MS = 400`; `static Map<Integer,String> parseDoubleKeys(List<String> entries)` und `parseDoubleScancodes(...)` – lesen nur Einträge mit Präfix `double:` (Rest wie `parseAppKeys`/`parseAppScancodes`), `static List<String> withoutDouble(List<String> entries)`; Zustandsklasse `DoubleTap` mit `DoubleTap(long windowMs)`, `int onTap(int key, long nowMs)` → `FIRST` (zurückhalten), `SECOND` (Doppelklick, Zurückhaltung verwerfen), `NONE` (Taste ohne `double:`-Eintrag: sofort ausführen); `Runnable pendingAfter(window)` als Timer-Hook im Dienst (`Handler.postDelayed`), bei Dienst-Unbind verworfen.
- Dienst: eine Taste mit `double:`-Eintrag wird beim ersten Tap **verschluckt** und nach 400 ms als Tap ausgeführt (Latenz ist der Preis, nur auf diesen Tasten); kommt der zweite Tap vorher, läuft das `double:`-Ziel. `hold:` bleibt davon unberührt (Halten wird vor dem Loslassen erkannt).

- [ ] **Step 1: Keycodes messen** – im Dienst ein `Log.i("Keys", "key=… scan=…")` für jeden Tastendruck (nur im Debug-Zweig, danach entfernen); bauen; `logcat -c`; Patrick drückt Netflix, Rakuten, YouTube, Prime Video; `tools/tv.py 'logcat -d -s Keys'`. Expected: vier Zeilen; Scancodes 695/779/… den Tasten zuordnen. In die Spec § 4.10d eintragen.
- [ ] **Step 2: Test schreiben**

```java
public class RemoteKeysDoubleTest {
    @Test public void doublePrefixParsesLikeAppKeys() { Map<Integer,String> m = RemoteKeys.parseDoubleScancodes(List.of("double:scan:695:com.google.android.youtube.tv", "scan:695:com.netflix.ninja")); assertEquals("com.google.android.youtube.tv", m.get(695)); assertEquals(1, m.size()); }
    @Test public void withoutDoubleDropsOnlyDoubleEntries() { assertEquals(List.of("scan:695:com.netflix.ninja"), RemoteKeys.withoutDouble(List.of("double:scan:695:x", "scan:695:com.netflix.ninja"))); }
    @Test public void secondTapInsideWindowIsDouble() { RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400); assertEquals(RemoteKeys.DoubleTap.FIRST, d.onTap(695, 1000)); assertEquals(RemoteKeys.DoubleTap.SECOND, d.onTap(695, 1300)); }
    @Test public void secondTapAfterWindowStartsOver() { RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400); d.onTap(695, 1000); assertEquals(RemoteKeys.DoubleTap.FIRST, d.onTap(695, 1500)); }
    @Test public void otherKeyInBetweenResets() { RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400); d.onTap(695, 1000); assertEquals(RemoteKeys.DoubleTap.FIRST, d.onTap(779, 1100)); }
}
```

- [ ] **Step 3–5: Fehlschlag, implementieren, grün** (`--tests 'com.gathin.tvhome.RemoteKeysDoubleTest'`), plus die vorhandenen `RemoteKeys`-Tests weiterhin grün.
- [ ] **Step 6: `config.xml` `app_keys`**: `scan:<rakuten>:com.disney.disneyplus`, `scan:<netflix>:com.netflix.ninja`, `double:scan:<netflix>:com.google.android.youtube.tv` (echte Scancodes aus Schritt 1; falls Android echte Keycodes liefert, `KEYCODE_…`-Form). Bauen, installieren.
- [ ] **Step 7: Button Mapper aus** – `tools/tv.py 'settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService'`; Patrick: Rakuten → Disney+, Netflix einmal → Netflix (mit ~0,4 s Verzögerung), Netflix doppelt → YouTube, Lautstärke halten → läuft durch. Expected: alles vier. Erst dann `tools/tv.py 'pm uninstall --user 0 flar2.homebutton'` – **nur nach Patricks Ja**.
- [ ] **Step 8: Commit** – `git add launcher/app/src/main/java/com/gathin/tvhome/RemoteKeys.java launcher/app/src/main/java/com/gathin/tvhome/HomeKeyService.java launcher/app/src/main/java/com/gathin/tvhome/InputKeyService.java launcher/app/src/main/res/values/config.xml themes/README.md launcher/app/src/test/java/com/gathin/tvhome/RemoteKeysDoubleTest.java docs/superpowers/specs/2026-10-25-vier-welten-launcher-design.md && git commit -m "Tastenbelegung: double:-Einträge für Doppelklick, Rakuten→Disney+, Doppel-Netflix→YouTube; Button Mapper abgelöst"`

**Reihenfolge der Ausführung (endgültig):** 0 → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 13 → 14 → 15 → 16 → 12.
