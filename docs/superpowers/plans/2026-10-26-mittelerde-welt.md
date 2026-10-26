# Mittelerde-Welt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine siebte Welt „Mittelerde“ (Ziffer 6) mit drei Tagesstimmungen, einer wandernden Gefolgschaft und eigener Haut nach dem Muster der Vier Welten.

**Architecture:** Enum-Konstante `MIDDLE_EARTH` hinter `SUPERNATURAL`. `WorldScene` lernt eine dritte Stimmung (`Mood { MORNING, DAY, NIGHT }`) für Welten, die sie nutzen; die übrigen Welten bleiben bei Tag/Nacht. Bewegung in `MiddleEarthOverlay`, die Gefolgschaft in `CompanyMotion` (vorgerenderte Silhouetten-Schrittbilder). Logo über `WorldLogo` aus dem Supernatural-Plan.

**Tech Stack:** Java 17, Android minSdk 26 / targetSdk 34, JUnit 4, Python 3 + Pillow/numpy.

**Spec:** `docs/superpowers/specs/2026-10-26-mittelerde-welt-design.md`; Muster: `docs/superpowers/plans/2026-10-25-vier-welten-launcher.md`. Mockup-Vorlage (gitignored): `.superpowers/brainstorm/mittelerde/mittelerde-mockup.html` (Controller legt sie vor Task 1 ab; Painter `STATIC.shire/mountains/mordor`, `DYNAMIC.*`, `figure`, `company`).

## Global Constraints

- Bau nach Jahreskreis **und** Supernatural, auf deren Stand (Branch `mittelerde`). Ziffer 6 aus der Enum-Reihenfolge. `WorldLogo` wird nur genutzt.
- Clean bleibt bit-identisch; keine Filmbilder, Plakatmotive, Gesichter, Logos, Zitate, Originalinschrift; Gefolgschaft nur als Silhouetten. Schrift Cinzel (SIL OFL); iw mit englischen Texten, Hebräisch per System-Fallback.
- Szenen 1920×1080 JPEG ≤ 300 KB, `common.py`-Prüfung, Logo-Fläche oben Mitte frei (x 48–66 %, y 4–18 %). Overlays ≤ 30 fps, keine Allokation pro Frame; Effekte ≤ 1,2 s; Übergang ≤ 900 ms.
- Bildregel: Tür/Baum, Turm, Turm mit Auge rechts vom App-Namen über der App-Leiste. Strings en/de/iw, `layout-iw`. Python-Suite vor jedem Commit. Commits Deutsch mit echten Umlauten.

## Review Focus

1. **Morgen-Grenze, wenn der Sonnenaufgang nach 11:00 läge:** kein Morgen, sofort Tag. Test: Task 2 `moodSkipsMorningAfterEleven`.
2. **Gruppe hinter einem sehr langen oder fehlenden App-Namen:** Dimmbereich folgt, ohne Hero voll sichtbar. Test: Task 5 `alphaWithoutHeroIsFull`, `alphaFollowsLongName`.
3. **forceMood „Morgen“ in einer Welt ohne Morgen** (Hogwarts …) wirkt wie Tag. Test: Task 2.
4. **Pause der Gruppe bei `onPause`/Screensaver:** keine Frames, beim Zurückkommen läuft sie weiter statt von vorn. Test: Task 5 `companyResumesWhereItStopped`.
5. **Auge und Lichtkegel bei `reduced_motion`:** statisch, kein Schweifen. Test: Task 4.

---

### Task 1: Welt im Modell, Strings, Schrift
**Files:** `World.java` (+ Tests), Strings en/de/iw (Name, drei Grüße), `res/font/cinzel_*.ttf`, Lizenz, `UiFont`.
**Produces:** `World.MIDDLE_EARTH("middleearth", R.string.world_middleearth, 0xFFD9B56A, 0xFFD9B56A, 0xFFD9B56A, 30, 0xFF0C0D0B, 0)`; `hasOwnMotion()` true; `boolean World.hasMorning()` (nur MIDDLE_EARTH).
- [ ] Tests: Ziffer 6, `parse("middleearth")`, nicht lernbar, `hasMorning`. Commit `Mittelerde: Welt auf Ziffer 6, Schrift Cinzel`.

### Task 2: Drei Stimmungen in WorldScene
**Files:** `WorldScene.java`, `LauncherSettingsActivity` (Testschalter: dritte Wahl „Morgen“), Strings, `WorldSceneTest`.
**Produces:** `enum Mood { MORNING, DAY, NIGHT }`; `static Mood WorldScene.mood(World w, String forced, int minuteOfDay, int sunrise, int sunset)` (Morgen = Sonnenaufgang bis 11:00 nur bei `hasMorning`, Tag bis Sonnenuntergang + 30 min); `static int resFor(World, Mood)` (`world_middleearth_{morning,day,night}`); Overlay-`setDay` bleibt für die anderen Welten, Mittelerde bekommt `setMood(Mood)`.
- [ ] Tests: `moodSkipsMorningAfterEleven` (sunrise 11:30 → DAY um 11:40), `morningUntilEleven`, `forcedMorningIsDayWithoutMorning`.
- [ ] Commit `Welten: dritte Stimmung Morgen für Mittelerde`.

### Task 3: Szenen Hügelland, Berge, dunkler Turm
**Files:** `launcher/worlds/middleearth/make_scene.py`, `common.py`, Bilder `world_middleearth_{morning,day,night}.jpg`.
Inhalt nach Spec § 4.1 (Mockup-Version 4 als Vorlage: Grashalme, Felder, Baum mit Krone, Tür/Fenster, Gesteinsschichten, Nadelwald, knorrige Bäume, Turm mit Stockwerken, Vulkan mit Lavarinnen, Speer-Heer), Körnung, Logo-Fläche frei. Auge, Lichtkegel, Glut, Rauch, Nebel bleiben fürs Overlay.
- [ ] `make_scene.py` + `common.py` ok, Python-Suite. Commit `Mittelerde: drei Landschaften`.

### Task 4: Dauerbewegung (ohne Gefolgschaft)
**Files:** `MiddleEarthOverlay.java`, `WorldOverlay.create`, Test.
**Produces:** Morgen: Schornsteinrauch, Nebel; Tag: Talnebel; Nacht: Kraterglut + Funken, Asche, **Auge** pulsierend auf der Turmspitze, **Lichtkegel** schweift (Periode ~18 s, ±0,9 rad). Logo über `WorldLogo` in der freien Fläche. Bei `reduced_motion` statisches Auge, kein Kegel-Schweifen (Test `eyeStaysStillWithReducedMotion` über reine Winkel-Funktion).
- [ ] Commit `Mittelerde: Rauch, Nebel, Glut und das wachende Auge`.

### Task 5: Die Gefolgschaft
**Files:** `CompanyMotion.java` (+ `CompanyMotionTest`), Silhouetten nach Mockup `figure()` (Schrittbilder vorgerendert beim Layout, nicht pro Frame), Anbindung an `MiddleEarthOverlay`, Breite des Hero-Namens aus `MainActivity` (`heroName`-Bounds bei jedem Fokuswechsel).
**Produces:** `static float CompanyMotion.alphaAt(float x, float nameLeft, float nameRight, float width)` (0,25 im Namensbereich + 4 % Rand, weicher Übergang über 3 % Breite, sonst 1); `static long CompanyMotion.cycleMs(long crossMs, long pauseMs)`; Querung 6 min, Pause zufällig 2–5 min (fester Seed je Tag); Position bleibt über `onPause` erhalten.
- [ ] Tests: `alphaInsideNameIsQuarter`, `alphaWithoutHeroIsFull` (nameLeft = nameRight = -1), `alphaFollowsLongName`, `companyResumesWhereItStopped`.
- [ ] Commit `Mittelerde: die Gefolgschaft wandert über die App-Leiste`.

### Task 6: Pills, App-Leiste, Fokus
**Files:** `WorldChrome` (runde Pills, Goldrand, Ring-Icon `ic_world_me_settings.xml`), `WorldDockDecoration` (goldene Oberkante), `WorldFocusMotion` (Glanzlicht wandert einmal über den Rahmen, ~200 ms; Test Dauer ≤ 200 ms).
- [ ] Commit `Mittelerde: goldene Pills und glänzender Fokus`.

### Task 7: App-Start mit Feuerschrift
**Files:** `WorldLaunchMotion`, `LaunchFxView` (`case MIDDLE_EARTH`: erfundene Zeichenreihe aus Feuer läuft einmal um den Kachelrahmen, Weißblende 200 ms, gesamt ≤ 1200 ms; Glyphen als eigene Pfade, kein Font), `LaunchGuardTest`.
- [ ] Commit `Mittelerde: App-Start mit Feuerschrift um die Kachel`.

### Task 8: Übergang in die Welt
**Files:** `WorldTransition` (`case MIDDLE_EARTH`: Nebelschleier zieht über das Bild, dann Landschaft; ≤ 900 ms), `WorldTransitionTest`.
- [ ] Commit `Mittelerde: Übergang durch Nebel`.

### Task 9: Abnahme am TV
- [ ] Install, Screenshots je Stimmung (`forceMood` Morgen/Tag/Nacht), Gruppe vor und hinter dem App-Namen, gfxinfo je Stimmung **mit laufender Gruppe**, App-Start, Übergang, Logo an/aus (Testbild).
- [ ] Doku, Abnahme-Notiz, Push nach Patricks Go. Hinweis für die spätere Übernahme durch den Kollegen in `README.md` (Fork-Kasten): eigener Ort per Theme, eigenes Logo per `--logo`.
