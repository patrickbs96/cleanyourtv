# Supernatural-Welt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine sechste Welt „Supernatural“ (Ziffer 5) als komplette Haut nach dem Muster der Vier Welten, plus ein allgemeiner, vom Theme unabhängiger Logo-Bild-Mechanismus für alle Welten.

**Architecture:** Neue Enum-Konstante `SUPERNATURAL` hinter `STARWARS`; Szene über `WorldScene` (Tag/Nacht), Dauerbewegung als `SupernaturalOverlay extends WorldOverlay`, Haut-Bausteine in den bestehenden Klassen (`WorldTypography`, `WorldChrome`, `WorldDockDecoration`, `WorldFocusMotion`, `WorldLaunchMotion`/`LaunchFxView`, `WorldTransition`) als eigene `case SUPERNATURAL`-Zweige. Logo-Bilder liegen in `world_logos/` im App-Speicher und werden von `WorldLogo` geladen.

**Tech Stack:** Java 17, Android minSdk 26 / targetSdk 34, JUnit 4, Python 3 + Pillow/numpy (`launcher/worlds/`), Bash (`tools/theme.sh`).

**Spec:** `docs/superpowers/specs/2026-10-26-supernatural-welt-design.md`; Muster: `docs/superpowers/plans/2026-10-25-vier-welten-launcher.md`. Mockup-Vorlage (gitignored): `.superpowers/brainstorm/supernatural/supernatural-mockup.html` (Controller legt sie vor Task 1 ab; Painter `SCENES.day/night`, `car`, `motel`, `star`, `sigils`, `wings`, `godRays`, `hellGlow`).

## Global Constraints

- Bau **nach Jahreskreis**, auf dessen Stand (Branch `supernatural` von `jahreskreis` nach dessen Abnahme). Ziffer 5 aus der Enum-Reihenfolge.
- Clean bleibt bit-identisch; keine Figuren, Logos, Originalsymbole der Serie, kein Emblem am Wagen; Schrift nur Courier Prime (SIL OFL).
- Szenen 1920×1080 JPEG ≤ 300 KB, `common.py`-Prüfung; Overlays ≤ 30 fps, Regionen, keine Allokation pro Frame; Effekte ≤ 1,2 s, Übergang ≤ 900 ms; Blitz ≤ 400 ms, Himmelsaufhellung ≤ 40 % Deckkraft.
- Bildregel: Wagen rechts vom App-Namen direkt über der App-Leiste. Strings en/de/iw, `layout-iw`. Python-Suite vor jedem Commit. Commits Deutsch mit echten Umlauten, Trailer laut Controller.

## Review Focus

1. **Logo-Datei kaputt oder riesig:** wird ignoriert (Schriftzug bleibt), kein Absturz, kein OOM. Test: Task 2 `WorldLogoTest.badOrHugeFileFallsBack`.
2. **Theme-Push/-Reset lässt das Logo liegen.** Test: Task 2 Python `test_theme_push_keeps_world_logos`.
3. **Blitz bei `reduced_motion`:** kein Blitz, kein Flackern. Test: Task 4 `SupernaturalOverlayTest.noLightningWithReducedMotion`.
4. **Fokus-Flackern bei Tastenwiederholung** endet ruhig (wie Task 8 der Vier Welten). Test: Task 6 `WorldFocusMotionTest.supernaturalFlickerSettlesOnRepeat`.
5. **Scheinwerfer-Start + Zurück** bricht sauber ab, keine Reste. Test: Task 7 über `LaunchGuardTest`.

---

### Task 1: Welt im Modell, Strings, Schrift
**Files:** `World.java` (+ `WorldTest`, `WorldSwitchTest`, `KeyBindingsTest`), `res/values*/strings.xml`, `res/font/courier_prime_*.ttf`, Lizenztext, `UiFont`-Eintrag.
**Produces:** `World.SUPERNATURAL("supernatural", R.string.world_supernatural, 0xFFE0A24A, 0xFFE0A24A, 0xFFE0A24A, 30, 0xFF0B0D10, 0)`; `hasOwnMotion()` true; Gruß `greetingSuffix` Tag „Back on the road“ / Nacht „Something's out there“ (über `greetingPrefix(day)`-Muster, Methode um `day` erweitern falls nötig).
- [ ] Test: `assertSame(World.SUPERNATURAL, World.forKeyCode(KeyEvent.KEYCODE_5))`; `World.parse("supernatural")`; Ziffer 5 nicht lernbar.
- [ ] Fail prüfen, implementieren, Suiten grün. Commit `Supernatural: Welt auf Ziffer 5, Schrift Courier Prime`.

### Task 2: Logo-Bilder je Welt (allgemein)
**Files:** Create `WorldLogo.java` (+ `WorldLogoTest`); Modify `tools/theme.sh` (+ `theme.ps1` falls vorhanden), `ThemeFiles.java`/`ThemeProvider` (Freigabe genau `world_logos/world_logo_<id>.png`, ≤ 2 MB), `themes/README.md` (Abschnitt „Eigenes Logo“), Python-Test in `tests/`.
**Produces:** `static File WorldLogo.file(Context, World)`; `static Bitmap WorldLogo.load(Context, World, int maxW, int maxH)` (null bei fehlend/kaputt/> 2 MB; dekodiert verkleinert); `theme.sh --logo <id> <datei.png>` und `--logo <id> --remove`; Theme-Push und `--reset` lassen `world_logos/` unberührt.
- [ ] Tests: `badOrHugeFileFallsBack` (Inhalt kein PNG → null; > 2 MB → null, über reine Prüffunktion `WorldLogo.acceptable(long size, byte[] header)`); Python `test_theme_push_keeps_world_logos`, `test_logo_flag_puts_and_removes` (wie die bestehenden theme.sh-Tests mit Fake-ADB).
- [ ] Commit `Welten: eigenes Logo-Bild je Welt, nur auf dem TV, unabhängig vom Theme`.

### Task 3: Szenen Tag „Himmel“ und Nacht „Hölle“
**Files:** `launcher/worlds/supernatural/make_scene.py`, `common.py` (`WORLDS` += `supernatural`), Bilder `world_supernatural_{day,night}.jpg`, `WorldScene.resFor`.
Inhalt nach Spec § 4.1 (Straße, Masten, Motel mit Schild, Wagen ohne Emblem, Stern auf der Straße, Bannkreis und Schutzzeichen, gezeichneter Schriftzug „SUPERNATURAL“ in Courier Prime über dem Motel, Tag: Lichtstrahlen; Nacht: Wolken, glühende Risse). Dynamische Teile (Neon, Glut, Rücklichter, Regen) bleiben fürs Overlay.
- [ ] `make_scene.py` + `common.py` prüfen ok, Python-Suite. Commit `Supernatural: Szenen Himmel und Hölle`.

### Task 4: Dauerbewegung
**Files:** `SupernaturalOverlay.java`, `WorldOverlay.create`, Test `SupernaturalOverlayTest`.
**Produces:** Tag: Staub, Hitzeflimmern, atmende Strahlen; Nacht: Regen, Neon-Flackern („MOTEL“ ein Buchstabe setzt aus, „VACANCY“ flackert), Glut von Stern und Rissen, Rücklichter; **Blitz** alle 9 s ± 2 s, ≤ 400 ms, mit Flügelschatten und aufleuchtenden Sigillen; Logo-Bild (Task 2) ersetzt den Schriftzug: das Overlay deckt den gezeichneten Schriftzug mit dem Himmelsausschnitt ab und zeichnet das Logo dort (Spec § 4.4).
- [ ] Tests (reine Funktionen): `lightningTimesStayInWindow` (Abstand 7–11 s, Dauer ≤ 400 ms), `noLightningWithReducedMotion`.
- [ ] Commit `Supernatural: Regen, Neon, Glut, Blitz mit Flügelschatten`.

### Task 5: Pills, App-Leiste, Fokusrahmen
**Files:** `WorldChrome` (eckig, Messingrand, Stern-Icon `ic_world_sn_settings.xml`), `WorldDockDecoration` (Bernsteinkante + körnige Salzlinie auf der Oberkante), `GlassView`-Akzent.
- [ ] Commit `Supernatural: Messing-Pills, Salzlinie, Bernstein-Fokus`.

### Task 6: Fokuswechsel flackert
**Files:** `WorldFocusMotion` (`case SUPERNATURAL`: Rahmen springt, Alpha flackert 3× in ~150 ms, dann ruhig), Test in `WorldFocusMotionTest` (`supernaturalFlickerSettlesOnRepeat`, Dauer ≤ 150 ms).
- [ ] Commit `Supernatural: Fokus flackert wie eine unruhige Glühbirne`.

### Task 7: App-Start mit Scheinwerfern
**Files:** `WorldLaunchMotion`, `LaunchFxView` (`case SUPERNATURAL`: zwei Lichtkegel aus der Wagenfront wachsen, Weißblende 200 ms, gesamt ≤ 1200 ms), `LaunchGuardTest` (Dauer ≤ 1200, Zurück bricht ab).
- [ ] Commit `Supernatural: App-Start mit aufblendenden Scheinwerfern`.

### Task 8: Übergang in die Welt
**Files:** `WorldTransition` (`case SUPERNATURAL`: Nacht Blitz + Flügelschatten, Tag aufblendende Lichtstrahlen; je ≤ 900 ms), `WorldTransitionTest`.
- [ ] Commit `Supernatural: Übergang mit Blitz oder Lichtstrahlen`.

### Task 9: Abnahme am TV
- [ ] Install, Screenshots Tag/Nacht (`forceMood`) und mitten im Blitz, gfxinfo 30 s je Stimmung, App-Start, Übergang, Logo an/aus über `theme.sh --logo supernatural <datei>` (Testbild, kein echtes Logo im Repo), Theme-Push danach → Logo bleibt.
- [ ] Doku (`README.md` Fork-Kasten, `launcher/worlds/README.md`), Abnahme-Notiz, Push nach Patricks Go.
