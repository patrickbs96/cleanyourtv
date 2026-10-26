# Jahreskreis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine fünfte Welt „Jahreskreis“ (EN „Seasons“, Ziffer 1), deren Szene und Bewegung sich aus dem Datum ergeben – Jahreszeiten, Anlässe, persönliche Tage –, plus persönliche Tage abgestuft in allen anderen Welten.

**Architecture:** Reine Datumslogik (`SeasonsCalendar`, `SeasonsToday`, `PersonalDay`) entscheidet je Tag einen `Look`. Die Welt `SEASONS` nutzt die vorhandene Szenen-/Overlay-Mechanik (`WorldScene`, `WorldOverlay`), deren Bild- und Bewegungswahl vom Look statt nur von der Welt abhängt; Dock-Wechsel und App-Start laufen wie in Clean. Persönliche Tage liegen in `SharedPreferences` auf dem TV und werden auf einer neuen Einstellungsseite gepflegt.

**Tech Stack:** Java 17, Android minSdk 26 / targetSdk 34, AndroidX, JUnit 4 (JVM), Python 3 + Pillow/numpy für Szenen (`launcher/worlds/`), Python-Repo-Tests in `tests/`.

**Spec:** `docs/superpowers/specs/2026-10-26-jahreskreis-design.md` (baut auf `docs/superpowers/specs/2026-10-25-vier-welten-launcher-design.md`). Malvorlage der Mockups (gitignored, enthält echte Namen – **nie** committen oder in Bilder übernehmen): `.superpowers/brainstorm/jahreskreis/jahreskreis-mockup.html` (Canvas-Painter je Look in `PAINT.*`, Koordinaten in Anteilen von Breite/Höhe).

## Global Constraints

- Branch: auf `vier-welten` aufsetzen, erst **nach** der Abnahme der Vier Welten (Task 12 jenes Plans); neuer Branch `jahreskreis` von dessen Stand. Remote `origin` = `patrickbs96/cleanyourtv`, kein PR ans Upstream.
- **Keine Namen, Geburts- oder Jahrestagsdaten im Repo** (öffentlich). Tests nutzen nur Platzhalter („Alex“, „Robin“). Alle Texte mit Namen sind Vorlagen mit Platzhaltern.
- **Clean bleibt bit-identisch** an Tagen ohne persönlichen Eintrag; `World.parse` liefert für fehlend/unbekannt weiter `CLEAN`.
- Ziffern: 0 Clean, 1 Jahreskreis, 2 Hogwarts, 3 Marvel, 4 Star Wars (= Enum-Reihenfolge). Farbtasten unverändert, Jahreskreis hat keine.
- Keine Logos, Figuren, Markenschriften, Filmbilder, geschützten Zitate. „May the 4th“ als eigenes Wortspiel erlaubt, das Filmzitat nicht.
- Bildregel: Wichtiges steht rechts vom App-Namen direkt über der App-Leiste (Dock-Oberkante y ≈ 766 von 1080), nie dahinter, nicht im Kopfbereich links.
- Szenen: 1920×1080 JPEG ≤ 300 KB, aus `make_scene.py` mit festem Seed, bestehen `launcher/worlds/common.py`-Prüfung (obere Ecken und unteres Drittel mittlere Luminanz ≤ 40).
- Overlays ≤ 30 fps, nur bewegte Regionen invalidieren, keine Allokation pro Frame, Pause in `onPause` und bei `reduced_motion`. Effekte ≤ 1,2 s – Ausnahmen: Vorspann ≤ 20 s (jede Taste beendet), Konfetti-Gruß in anderen Welten 3 s.
- Messgrenzen je Look am TV: gfxinfo 95th ≤ 16 ms, Janky ≤ 5 %; RSS ≤ +25 MB gegen Clean; Kaltstart ≤ +300 ms.
- Strings: `values` (EN), `values-de`, `values-iw`; neue Views auch in `layout-iw`. Python-Repo-Suite vor jedem Commit (siehe SDD-`common.md`).
- Commits Deutsch mit echten Umlauten, Trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` und `Claude-Session: …` laut Controller; nie `git add -A`/`git stash`.

## Review Focus

1. **Mitternacht, während Home offen ist:** Look wechselt mit dem nächsten Minuten-Tick nach 00:00 ohne Neustart (Advent → Nikolaus). Test: Task 4 `SeasonsSceneTest.lookChangesAtMidnight`.
2. **Grenzfälle der Einträge:** Geburtstag 29.02. im Nicht-Schaltjahr am 28.02.; Hochzeitstag im Hochzeitsjahr selbst zeigt nichts; leere Namen. Tests: Task 1.
3. **Kaputte oder alte Pref-Zeilen** (Hand-Edit, frühere Version): werden übersprungen, nie Absturz. Test: Task 3 `PersonalDayTest.badLinesAreSkipped`.
4. **Vorspann blockiert die Fernbedienung nicht und kommt nur einmal am Tag**, auch nach recreate/Weltwechsel. Test: Task 9 `OncePerDayTest`.
5. **Jahreswechsel:** Silvester/Neujahr über 31.12.→01.01., Winter über Jahresgrenze. Test: Task 1 `newYearSpansTheTurnOfTheYear`.

---

### Task 1: Datumslogik – Looks, Kalender, persönliche Tage

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/Look.java`, `SeasonsCalendar.java`, `PersonalDay.java`, `SeasonsToday.java`
- Test: `launcher/app/src/test/java/com/gathin/tvhome/SeasonsCalendarTest.java`, `SeasonsTodayTest.java`

**Interfaces:**
- Produces:
  - `enum Look { WINTER, SPRING, SUMMER, AUTUMN, NEW_YEAR, CARNIVAL, VALENTINE, EASTER, MOTHERS_DAY, FATHERS_DAY, HALLOWEEN, ST_MARTIN, NIKOLAUS_EVE, NIKOLAUS, ADVENT, CHRISTMAS, BIRTHDAY, ANNIVERSARY, WEDDING }` mit `final String id` (snake_case, z. B. `"st_martin"`), `final boolean hasNight` (nur die vier Jahreszeiten), `final boolean bright` (SPRING, EASTER, MOTHERS_DAY, FATHERS_DAY, VALENTINE: nachts dimmen), `final int accent` (0xAARRGGBB, aus dem Mockup-`ring`).
  - `final class SeasonsCalendar`: `static LocalDate easter(int year)` (Gauß/Meeus gregorianisch), `static LocalDate firstAdvent(int year)`, `static Look season(LocalDate d)`, `static Look occasion(LocalDate d, boolean mothersDay, boolean fathersDay)` → kürzester aktiver Anlass oder `null`; `static int adventCandles(LocalDate d)` (1–4, 0 außerhalb).
  - `final class PersonalDay`: `enum Kind { BIRTHDAY("birthday"), ANNIVERSARY("anniversary"), WEDDING("wedding") }`; Felder `kind, month, day, startYear, names`; `boolean isOn(LocalDate d)` (29.02. → 28.02. im Nicht-Schaltjahr), `int years(LocalDate d)` (= Jahr − startYear), `boolean shows(LocalDate d)` (isOn und für WEDDING years ≥ 1, für ANNIVERSARY years ≥ 0), `String encode()`, `static PersonalDay decode(String line)` (null bei Fehler).
  - `final class SeasonsToday`: `static SeasonsToday resolve(LocalDate d, List<PersonalDay> days, boolean mothersDay, boolean fathersDay)`; Felder `Look look`, `List<PersonalDay> personal` (heute sichtbare, sortiert BIRTHDAY > ANNIVERSARY > WEDDING), `int adventCandles`; `String names()` (Namen aller heutigen Einträge der Art des ersten, verbunden mit „ & “).

Zeiträume (Spec § 4.1/4.2): Winter 21.12.–20.03., Frühling 21.03.–20.06., Sommer 21.06.–22.09., Herbst 23.09.–20.12.; Silvester/Neujahr 31.12.–01.01.; Karneval Ostern−52 … Ostern−48; Valentinstag 14.02.; Ostern Ostern−7 … Ostern+1; Muttertag 2. Sonntag im Mai; Vatertag Ostern+39; Halloween 24.–31.10.; St. Martin 01.–11.11.; Nikolaus-Vorabend 05.12.; Nikolaus 06.12.; Advent 1. Advent … 23.12.; Weihnachten 24.–26.12. Vorrang: persönlicher Eintrag (Art BIRTHDAY > ANNIVERSARY > WEDDING) vor dem kürzesten aktiven Anlass (Tage im Zeitraum; Gleichstand: Reihenfolge oben) vor der Jahreszeit.

- [ ] **Step 1: Write the failing tests**

```java
// SeasonsCalendarTest
@Test public void easterMatchesKnownYears() {
    assertEquals(LocalDate.of(2026, 4, 5), SeasonsCalendar.easter(2026));
    assertEquals(LocalDate.of(2027, 3, 28), SeasonsCalendar.easter(2027));
    assertEquals(LocalDate.of(2028, 4, 16), SeasonsCalendar.easter(2028));
    assertEquals(LocalDate.of(2030, 4, 21), SeasonsCalendar.easter(2030));
}
@Test public void firstAdventIsTheFourthSundayBeforeChristmas() {
    assertEquals(LocalDate.of(2026, 11, 29), SeasonsCalendar.firstAdvent(2026));
    assertEquals(LocalDate.of(2027, 11, 28), SeasonsCalendar.firstAdvent(2027));
}
@Test public void adventCandlesCountTheSundays() {
    assertEquals(1, SeasonsCalendar.adventCandles(LocalDate.of(2026, 11, 29)));
    assertEquals(2, SeasonsCalendar.adventCandles(LocalDate.of(2026, 12, 7)));
    assertEquals(4, SeasonsCalendar.adventCandles(LocalDate.of(2026, 12, 23)));
    assertEquals(0, SeasonsCalendar.adventCandles(LocalDate.of(2026, 12, 24)));
}
@Test public void movingFeastsIn2027() {
    assertEquals(Look.CARNIVAL, SeasonsCalendar.occasion(LocalDate.of(2027, 2, 4), false, false));
    assertEquals(Look.CARNIVAL, SeasonsCalendar.occasion(LocalDate.of(2027, 2, 8), false, false));
    assertNull(SeasonsCalendar.occasion(LocalDate.of(2027, 2, 9), false, false));
    assertEquals(Look.FATHERS_DAY, SeasonsCalendar.occasion(LocalDate.of(2027, 5, 6), true, true));
    assertEquals(Look.MOTHERS_DAY, SeasonsCalendar.occasion(LocalDate.of(2027, 5, 9), true, true));
    assertNull(SeasonsCalendar.occasion(LocalDate.of(2027, 5, 9), false, false));   // off until switched on
}
@Test public void seasonsHaveAstronomicalBounds() {
    assertEquals(Look.WINTER, SeasonsCalendar.season(LocalDate.of(2027, 3, 20)));
    assertEquals(Look.SPRING, SeasonsCalendar.season(LocalDate.of(2027, 3, 21)));
    assertEquals(Look.SUMMER, SeasonsCalendar.season(LocalDate.of(2027, 6, 21)));
    assertEquals(Look.AUTUMN, SeasonsCalendar.season(LocalDate.of(2027, 9, 23)));
    assertEquals(Look.WINTER, SeasonsCalendar.season(LocalDate.of(2027, 12, 21)));
}

// SeasonsTodayTest (names are placeholders only)
static final PersonalDay ALEX = PersonalDay.decode("birthday;12;24;1990;Alex");
@Test public void shorterOccasionWins() {
    assertEquals(Look.NIKOLAUS_EVE, today(2026, 12, 5).look);
    assertEquals(Look.NIKOLAUS, today(2026, 12, 6).look);
    assertEquals(Look.ADVENT, today(2026, 12, 7).look);
    assertEquals(Look.EASTER, today(2027, 3, 21).look);    // Palm Sunday beats the first day of spring
    assertEquals(Look.ADVENT, today(2026, 12, 21).look);   // Advent beats winter
}
@Test public void newYearSpansTheTurnOfTheYear() {
    assertEquals(Look.NEW_YEAR, today(2026, 12, 31).look);
    assertEquals(Look.NEW_YEAR, today(2027, 1, 1).look);
    assertEquals(Look.WINTER, today(2027, 1, 2).look);
}
@Test public void birthdayBeatsChristmas() {
    SeasonsToday t = SeasonsToday.resolve(LocalDate.of(2026, 12, 24), List.of(ALEX), false, false);
    assertEquals(Look.BIRTHDAY, t.look); assertEquals("Alex", t.names());
}
@Test public void twoBirthdaysShowBothNames() {
    PersonalDay robin = PersonalDay.decode("birthday;12;24;1992;Robin");
    assertEquals("Alex & Robin", SeasonsToday.resolve(LocalDate.of(2026, 12, 24), List.of(ALEX, robin), false, false).names());
}
@Test public void anniversaryCountsYears() {
    PersonalDay a = PersonalDay.decode("anniversary;3;14;2019;Alex & Robin");
    SeasonsToday t = SeasonsToday.resolve(LocalDate.of(2027, 3, 14), List.of(a), false, false);
    assertEquals(Look.ANNIVERSARY, t.look); assertEquals(8, a.years(LocalDate.of(2027, 3, 14)));
}
@Test public void weddingShowsFromTheFirstAnniversary() {
    PersonalDay w = PersonalDay.decode("wedding;8;19;2027;Alex & Robin");
    assertEquals(Look.SUMMER, SeasonsToday.resolve(LocalDate.of(2027, 8, 19), List.of(w), false, false).look);
    assertEquals(Look.WEDDING, SeasonsToday.resolve(LocalDate.of(2028, 8, 19), List.of(w), false, false).look);
}
@Test public void leapDayBirthdayFallsOnTheTwentyEighth() {
    PersonalDay leap = PersonalDay.decode("birthday;2;29;2000;Alex");
    assertTrue(leap.isOn(LocalDate.of(2027, 2, 28)));
    assertTrue(leap.isOn(LocalDate.of(2028, 2, 29)));
    assertFalse(leap.isOn(LocalDate.of(2028, 2, 28)));
}
@Test public void birthdayLeadsWhenTwoKindsMeet() {
    PersonalDay a = PersonalDay.decode("anniversary;12;24;2019;Alex & Robin");
    SeasonsToday t = SeasonsToday.resolve(LocalDate.of(2026, 12, 24), List.of(a, ALEX), false, false);
    assertEquals(Look.BIRTHDAY, t.look); assertEquals(2, t.personal.size());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd launcher && ./gradlew testReleaseUnitTest -q --tests 'com.gathin.tvhome.Seasons*'` (JAVA_HOME/ANDROID_HOME wie in `common.md`)
Expected: FAIL, Klassen fehlen.

- [ ] **Step 3: Implement `Look`, `SeasonsCalendar`, `PersonalDay`, `SeasonsToday`** – reines Java ohne Android-Klassen (JVM-testbar); `PersonalDay.encode` = `kind;month;day;startYear;names` mit `%`, `;` und Zeilenumbruch in `names` als `%25`, `%3B`, `%0A` kodiert.

- [ ] **Step 4: Run tests to verify they pass** (gleicher Befehl), danach die ganze JVM-Suite.

- [ ] **Step 5: Commit** `Jahreskreis: Datumslogik für Jahreszeiten, Anlässe und persönliche Tage`

---

### Task 2: Welt SEASONS im Modell, Ziffern, Clean-Pfade

**Files:**
- Modify: `World.java`, `WorldTest.java`, `WorldSwitchTest.java`, `KeyBindingsTest.java`, `res/values*/strings.xml`, alle Stellen mit `isClean()`-/`switch (world)`-Verzweigung (`WorldChrome`, `WorldTypography`, `WorldDockDecoration`, `WorldFocusMotion`, `WorldLaunchMotion`, `LaunchFxView`, `WorldTransition`, `WorldScene`, `WorldOverlay`, `MainActivity`)

**Interfaces:**
- Produces: `World.SEASONS("seasons", R.string.world_seasons, 0xFFE8A33D, 0, 0, 0, 0xFF0D0F14, 0)` direkt nach `CLEAN`; `boolean World.hasOwnMotion()` (true nur HOGWARTS, MARVEL, STARWARS); Strings `world_seasons` = „Seasons“ / de „Jahreskreis“ / iw „מעגל השנה“; `world_hint` „… 0 bis 4 …“.

- [ ] **Step 1: Tests anpassen/ergänzen**

```java
@Test public void digitsFollowTheEnumWithSeasonsOnOne() {
    assertSame(World.CLEAN, World.forKeyCode(KeyEvent.KEYCODE_0));
    assertSame(World.SEASONS, World.forKeyCode(KeyEvent.KEYCODE_1));
    assertSame(World.HOGWARTS, World.forKeyCode(KeyEvent.KEYCODE_2));
    assertSame(World.STARWARS, World.forKeyCode(KeyEvent.KEYCODE_4));
    assertNull(World.forKeyCode(KeyEvent.KEYCODE_5));
}
@Test public void seasonsHasNoColourKey() { for (int k = KeyEvent.KEYCODE_PROG_RED; k <= KeyEvent.KEYCODE_PROG_BLUE; k++) assertNotSame(World.SEASONS, World.forKeyCode(k)); }
@Test public void unknownIdStaysClean() { assertSame(World.CLEAN, World.parse("narnia")); assertSame(World.SEASONS, World.parse("seasons")); }
@Test public void onlyTheThemedWorldsHaveOwnMotion() { assertFalse(World.CLEAN.hasOwnMotion()); assertFalse(World.SEASONS.hasOwnMotion()); assertTrue(World.MARVEL.hasOwnMotion()); }
```
`KeyBindingsTest`: genommen sind 0–4, lernbar `KEYCODE_5`.

- [ ] **Step 2: Fail prüfen** (`--tests 'com.gathin.tvhome.World*' --tests 'com.gathin.tvhome.KeyBindingsTest'`).
- [ ] **Step 3: Implementieren.** Jede bestehende `isClean()`-Abfrage einordnen: Dock-Wechsel, App-Start, Fokusbewegung, Dock-Stil, Hero-Stil, Schrift → für SEASONS der Clean-Weg (über `!hasOwnMotion()` bzw. explizit); Szene, Overlay, Icon-Pills → Welt-Weg. Liste jeder geänderten Stelle mit Begründung in den Report. Bis Task 4 zeigt SEASONS nur `backgroundColor` (keine Szene).
- [ ] **Step 4: JVM-Suite, `assembleRelease`, Python-Suite grün.**
- [ ] **Step 5: Commit** `Jahreskreis: Welt im Modell auf Ziffer 1, Dock und App-Start wie Clean`

---

### Task 3: Persönliche Tage speichern und auf dem TV pflegen

**Files:**
- Create: `PersonalDays.java` (Laden/Speichern), `SeasonsActivity.java`, `res/layout/activity_seasons.xml` (+ `layout-iw`), Strings
- Modify: `AndroidManifest.xml`, `LauncherSettingsActivity.java` + `activity_launcher_settings.xml` (+ `-iw`): Zeile „Jahreskreis“ unter „Tastenbelegung“
- Test: `PersonalDayTest.java`, `PersonalDaysTest.java` (mit `FakePrefs`)

**Interfaces:**
- Consumes: `PersonalDay`, `PersonalDay.Kind` (Task 1).
- Produces: `PersonalDays` mit `static final String PREFS = "seasons"`, Schlüssel `days` (Zeilen getrennt durch `\n`), `mothers_day`, `fathers_day` (bool, default false), `crawl_text` (String, default `R.string.seasons_crawl_default`), `force_date` (prozessgebunden wie `WorldScene.FORCE_KEY`); `static List<PersonalDay> load(SharedPreferences)`, `static void save(SharedPreferences, List<PersonalDay>)`, `static LocalDate today(SharedPreferences, LocalDate realToday)` (erzwungenes Datum oder echtes), `static void saveForcedDate(SharedPreferences, String isoOrAuto)`.
- `SeasonsActivity`: Liste der Einträge (Art-Icon, Namen, Datum), „Eintrag hinzufügen“, Klick = bearbeiten, Entfernen mit Rückfrage (Fokus auf Abbrechen); Art per Auswahl, Namen per Tastatur, Tag/Monat/Jahr per `NumberPicker` (ohne Tastatur); Zeile „Vorspann-Text“ (Mehrzeilen-Eingabe, Platzhalter `{names}`, `{years}`, `{episode}`); Schalter Muttertag/Vatertag. Mit Intent-Extra `force_date` (`yyyy-MM-dd` oder `auto`) speichert sie nur das erzwungene Datum und beendet sich ohne Oberfläche (für `adb shell am start -n com.gathin.tvhome/.SeasonsActivity --es force_date 2026-12-06`).

- [ ] **Step 1: Failing tests**

```java
@Test public void roundTripKeepsSeparatorsInNames() { PersonalDay d = PersonalDay.decode("anniversary;3;14;2019;A%3BB & C"); assertEquals("A;B & C", d.names); assertEquals(d.encode(), PersonalDay.decode(d.encode()).encode()); }
@Test public void badLinesAreSkipped() {
    FakePrefs p = new FakePrefs(); p.edit().putString("days", "birthday;12;24;1990;Alex\nnonsense\nbirthday;13;1;1990;X\nwedding;8;19;2027;Alex & Robin").commit();
    assertEquals(2, PersonalDays.load(p).size());
}
@Test public void forcedDateHoldsOnlyForThisProcess() { /* wie WorldSceneTest für FORCE_KEY: nach resetProcessForTest() wieder echtes Datum */ }
@Test public void mothersAndFathersDayStartOff() { assertFalse(new FakePrefs().getBoolean("mothers_day", false)); }
```
- [ ] **Step 2: Fail prüfen.** - [ ] **Step 3: Implementieren** (Strings en/de/iw, Seite im Stil von `KeyBindingsActivity`). - [ ] **Step 4: Tests, Build, Python-Suite grün.**
- [ ] **Step 5: Commit** `Jahreskreis: persönliche Tage auf dem TV pflegen, Testdatum per ADB`

---

### Task 4: Szene, Overlay, Akzent und Übergang folgen dem Look

**Files:**
- Modify: `WorldScene.java`, `WorldOverlay.java`, `MainActivity.java`, `WorldTransition.java`, `WorldTypography.java`, `GlassView`-Aufrufer
- Create: `SeasonsOverlay.java` (Verteiler), `LookMotion.java` (Interface)
- Test: `SeasonsSceneTest.java`, `WorldTransitionTest.java` (ergänzt)

**Interfaces:**
- Consumes: `SeasonsToday`, `PersonalDays.today` (Tasks 1, 3).
- Produces:
  - `static String WorldScene.drawableName(Look look, boolean day)` → `world_seasons_<id>` bzw. `_day`/`_night` bei `hasNight`; `static int WorldScene.resFor(Context, World, boolean day, Look look)` (Ressource per Name; 0 wenn fehlt → Hintergrundfarbe bleibt).
  - `static boolean WorldScene.needsReload(LocalDate shownDate, Look shownLook, LocalDate now, Look nowLook, Boolean shownDay, boolean day)`; der Minuten-Tick lädt neu bei Look- oder Stimmungswechsel (Überblendung 300 ms wie heute).
  - Nachtdimmung: bei `look.bright && !day` liegt ein Farbfilter mit 55 % Abdunklung auf der Szene.
  - `interface LookMotion { void layout(SeasonsOverlay o, int w, int h); void frame(long now); void draw(Canvas c); }`; `SeasonsOverlay extends WorldOverlay` hält genau ein `LookMotion` für den heutigen Look (Fabrik `LookMotion.forLook(Look, SeasonsToday)`, bis Tasks 5–8 eine leere Bewegung).
  - Akzent: `int MainActivity.worldAccent()` = `today.look.accent` für SEASONS, sonst `world.accent`; überall, wo bisher `world.accent` gelesen wird.
  - Übergang: SEASONS leave 250 ms Abblende auf Schwarz, enter 350 ms Aufblende (`WorldTransition.leaveMs/enterMs`).
  - Schrift: SEASONS behält Clean-Schrift (Outfit), Grußfarbe = Akzent aufgehellt wie beim Festival (`liftTowardsWhite(accent, 0.25f)`).

- [ ] **Step 1: Failing tests**

```java
@Test public void lookChangesAtMidnight() {
    LocalDate d1 = LocalDate.of(2026, 12, 5), d2 = d1.plusDays(1);
    assertTrue(WorldScene.needsReload(d1, Look.NIKOLAUS_EVE, d2, Look.NIKOLAUS, true, true));
    assertFalse(WorldScene.needsReload(d1, Look.ADVENT, d1, Look.ADVENT, false, false));
}
@Test public void onlySeasonsHaveDayAndNightPictures() {
    assertEquals("world_seasons_winter_night", WorldScene.drawableName(Look.WINTER, false));
    assertEquals("world_seasons_advent", WorldScene.drawableName(Look.ADVENT, false));
}
@Test public void seasonsTransitionStaysShort() { assertTrue(WorldTransition.leaveMs(World.SEASONS) + WorldTransition.enterMs(World.SEASONS) <= 900); }
```
- [ ] **Step 2: Fail prüfen.** - [ ] **Step 3: Implementieren.** - [ ] **Step 4: Tests, Build, Python-Suite.**
- [ ] **Step 5: Commit** `Jahreskreis: Szene und Bewegung folgen dem Tages-Look, Akzent und Überblendung`

---

### Task 5: Jahreszeiten – acht Szenen und ihre Bewegung

**Files:**
- Create: `launcher/worlds/seasons/make_scene.py` (gemeinsamer Einstieg, `python launcher/worlds/seasons/make_scene.py [look …]`), `launcher/worlds/seasons/seasons.py`, `SeasonMotions.java`
- Modify: `launcher/worlds/common.py` (`check_all` prüft auch alle `world_seasons_*.jpg`), `launcher/worlds/README.md`
- Create (generiert): `res/drawable-nodpi/world_seasons_{winter,spring,summer,autumn}_{day,night}.jpg`

**Interfaces:**
- Consumes: `LookMotion`, `SeasonsOverlay` (Task 4); Malvorlage `PAINT.spring/summer/autumn/winter`.
- Produces: `LookMotion` für WINTER (Schneefall), SPRING (Kirschblüten), SUMMER (Glühwürmchen, Glitzern auf dem Wasser), AUTUMN (fallende, drehende Blätter); Tagfassungen heller, Nachtfassungen gedämpft.

- [ ] **Step 1:** Szenen nach Malvorlage in Python malen (feste Seeds); Tag/Nacht je Jahreszeit; obere Ecken abdunkeln, damit `common.py` besteht.
- [ ] **Step 2:** `python launcher/worlds/seasons/make_scene.py && python launcher/worlds/common.py` → alle Prüfungen „ok“, je Datei ≤ 300 KB.
- [ ] **Step 3:** `SeasonMotions` (Partikelzahlen aus der Vorlage, Positionen im 192×108-Raster wie die anderen Overlays), Regionen nur um bewegte Bereiche.
- [ ] **Step 4:** Python-Repo-Suite (Bildtests!), JVM-Suite, Build.
- [ ] **Step 5: Commit** `Jahreskreis: vier Jahreszeiten mit Tag und Nacht, Schnee, Blüten, Glühwürmchen, Blätter`

### Task 6: Anlässe I – Neujahr, Karneval, Valentinstag, Ostern, Muttertag, Vatertag

Wie Task 5, Dateien `occasions_a.py`, `OccasionMotionsA.java`, Bilder `world_seasons_{new_year,carnival,valentine,easter,mothers_day,fathers_day}.jpg`. Motive und Bewegung nach Spec § 4.2 und Malvorlage (`PAINT.newyear` Skyline über der Leiste mit Fenstern + Feuerwerk alle ~3 s; `carnival` Luftschlangen + Konfetti; `valentine` Rosen/Gläser/Kerze + Blütenblätter; `easter` Eier rechts über der Leiste + zwei Schmetterlinge; `mothers` Strauß + Herzkarte „Mama“; `fathers` Weg läuft rechts aus dem Bild, Bollerwagen). Commit `Jahreskreis: Anlässe Neujahr bis Vatertag`.

### Task 7: Anlässe II – Halloween, St. Martin, Nikolaus, Advent, Weihnachten

Wie Task 5, `occasions_b.py`, `OccasionMotionsB.java`, Bilder `world_seasons_{halloween,st_martin,nikolaus_eve,nikolaus,advent,christmas}.jpg`. Halloween: gerippte Kürbisse mit Stiel, Gesichter als Overlay-Flackern; St. Martin: Laternen schaukeln (Overlay); Nikolaus-Vorabend leere Stiefel, Nikolaus gefüllte Stiefel; Advent: Kerzen im Bild unangezündet, **Flammen im Overlay für `today.adventCandles` Kerzen**; Weihnachten: geschmückter Baum, Lichter funkeln im Overlay, Geschenke darunter, Schnee. Test `OccasionMotionsBTest.adventLightsAsManyCandlesAsSundays` (Anzahl gezeichneter Flammen = `adventCandles`, über eine reine Hilfsfunktion `flamesFor(int candles)`). Commit `Jahreskreis: Anlässe Halloween bis Weihnachten`.

### Task 8: Persönliche Looks – Geburtstag, Jahrestag, Hochzeitstag

**Files:** `personal.py`, `PersonalMotions.java`, Bilder `world_seasons_{birthday,anniversary,wedding}.jpg`, Test `PersonalMotionsTest.java`.

- Geburtstag: Bild mit zwei Wimpelgirlanden „HAPPY“ (oben) / „BIRTHDAY“ (darunter), Buchstaben im Wimpelwinkel, Kuchen links-mittig, Ballonbündel links davon. Overlay: Kerzenflammen, Dauer-Konfetti, **Schleife der Ballonschnüre auf der Dock-Oberkante** (über der Leiste gezeichnet: eigene kleine View über dem Dock oder Overlay oberhalb des Docks in der Z-Reihenfolge), **Name(n) groß** unter der Girlande (`today.names()`, Schrift Bricolage-ähnlich nicht nötig: Outfit Bold, Größe ≥ 6 % der Bildbreite).
- Jahrestag: Wüste, drei Dünen, zwei Sonnen, Projektor; Overlay: flimmerndes Holo-Herz mit Zahl `years` mittig im Herz, Strahl endet an der Herzspitze, Sterne funkeln.
- Hochzeitstag: Rosenbogen mit Lichterkette; Overlay: Lichter, Ringe mit wanderndem Funkeln, Blütenblätter, **Namen groß** oben rechts.
- Tests: `heartNumberIsTheYearCount` (Text = `String.valueOf(years)`), `namesComeFromTheEntriesNotTheCode` (Overlay-Text = `today.names()`).
- Commit `Jahreskreis: Geburtstag, Jahrestag und Hochzeitstag`.

### Task 9: Vorspann am Jahrestag

**Files:** Create `CrawlView.java`, `OncePerDay.java`; Modify `MainActivity.java`, Strings (`seasons_crawl_default` = „Vor nicht allzu langer Zeit, in einer Stadt ganz in der Nähe …\n\nEpisode {episode}\n{names}\n\n{years} Jahre später …“ en/de/iw sinngemäß). Test `OncePerDayTest`, `CrawlTextTest`.

**Interfaces:** `static boolean OncePerDay.due(String lastIso, LocalDate today)`, `static String OncePerDay.mark(LocalDate today)`; Pref `crawl_last` in `PersonalDays.PREFS`; `static String CrawlView.fill(String template, String names, int years)` (`{episode}` = römische Zahl von `years`).

- Nur in SEASONS, nur wenn `today.look == ANNIVERSARY`, beim ersten `onResume` des Tages; gelber, nach hinten gekippter Lauftext, höchstens 20 s; **jede Taste** (auch Zurück) beendet ihn und wird verbraucht; danach normal.
- Tests: `due(null, d)` true; nach `mark(d)` false für d, true für d+1; `fill("Episode {episode}: {names}", "Alex & Robin", 4)` = „Episode IV: Alex & Robin“.
- Commit `Jahreskreis: Vorspann am Jahrestag, einmal am Tag, jede Taste beendet`.

### Task 10: Persönliche Tage in allen Welten

**Files:** Modify `MainActivity.java` (Gruß), `layout/activity_main.xml` + `layout-iw` (ImageView `personalDayIcon` 28 dp in der Uhrzeile hinter `clock`, `gone`), Drawables `ic_day_cake.xml`, `ic_day_holo_heart.xml`, `ic_day_rings.xml`; Create `ConfettiBurstView.java`, `PersonalGreeting.java`; Test `PersonalGreetingTest.java`.

**Interfaces:**
- `static String PersonalGreeting.text(Resources r, World w, SeasonsToday t, int year)`: Geburtstag in Clean/SEASONS „Happy Birthday, {names}“, in Themenwelten `seasons_greeting_birthday` („Alles Gute, %s“ de / „Happy birthday, %s“ en) mit dem Weltwort davor (`greetingPrefix`); Jahrestag aus Vorlage `anniversary_greeting` (Standard „{years} Jahre · {names}“, auf dem TV änderbar); Hochzeitstag „{years}. Hochzeitstag“ (en „{years} years married“); weitere Einträge desselben Tages mit „ · “ angehängt; leerer String ohne Eintrag.
- Vorrang im Gruß: persönlicher Gruß vor Festival-Gruß vor Welt-Gruß. Ohne Eintrag exakt der heutige Weg (Clean bit-identisch).
- Icon neben der Uhr ganztägig je Art des ersten Eintrags.
- `ConfettiBurstView`: einmal am Tag (Pref `confetti_last`, `OncePerDay`) beim ersten `onResume`, 3 s, Farben aus `worldAccent()` und zwei Nachbartönen; nicht in SEASONS (dort Dauer-Konfetti bzw. eigener Look); pausiert bei `reduced_motion`.
- Tests: Texte je Art/Welt mit Platzhalternamen; `noEntryGivesEmptyText`; `festivalLosesToBirthday` (über eine reine Auswahlfunktion `PersonalGreeting.pick(String personal, String festival, String world)`).
- Commit `Jahreskreis: persönliche Tage in allen Welten – Gruß, Icon, einmal Konfetti`.

### Task 11: Abnahme am TV

- [ ] Installieren (`HOME_KEY_SERVICE=1 ./build-and-install.sh`), Accessibility-Eintrag vorher/nachher vergleichen.
- [ ] Je Look ein Screenshot über `force_date` (Daten aus `SeasonsTodayTest`; Tag und Nacht per `forceMood` für die Jahreszeiten) → `shots/jk-<look>.png`, mit dem Read-Tool prüfen: Bildregel (nichts hinter der Leiste), Lesbarkeit von Gruß/Uhr.
- [ ] gfxinfo 30 s je Look mit Bewegung; RSS und Kaltstart SEASONS gegen Clean.
- [ ] Persönliche Tage: Patricks Einträge (privat, Second Brain „Specs & Pläne“, Zeile 2026-10-26) **nur nach Patricks Ja** per Seite oder `am start`/Prefs eintragen; Prüfung mit erzwungenen Daten (Geburtstag, 04.05., Hochzeitstag ab 2028). Vorspann: einmal, Taste beendet.
- [ ] Andere Welten am Geburtstag: Gruß, Icon, einmal Konfetti; Clean an einem Normaltag pixelgleich zur Baseline.
- [ ] Welt auf Patricks TV auf Jahreskreis stellen (Ziffer 1), `force_date` auf `auto`.
- [ ] Doku: `README.md` Fork-Kasten, `launcher/worlds/README.md`, `docs/switches.md` falls neue Schalter; Abnahme-Notiz; Push `origin jahreskreis` nach Patricks Go.
