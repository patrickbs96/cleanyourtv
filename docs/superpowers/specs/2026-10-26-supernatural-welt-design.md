# Supernatural – sechste Welt für den Clean-Your-TV-Launcher (Fork)

Stand: 26.10.2026 · Auftraggeber: Patrick · Baut auf: `2026-10-25-vier-welten-launcher-design.md` (Welt-Modell,
Szenen, Overlays, Bewegungen) und `2026-10-26-jahreskreis-design.md` (Ziffern-Reihenfolge) · Mockup: Artifact „Welt
Supernatural“ (https://claude.ai/artifact/GUzrk3bhUCuWWPvFmmfW7m, Version 3).

## 1. Absicht

Eine Welt mit dem Gefühl der Serie Supernatural: Roadtrip bei Nacht, Motel, schwarzer Oldtimer, Himmel und Hölle,
Schutzzeichen – erkennbar, ohne Figuren, Logo oder Originalgrafiken der Serie. Wie Hogwarts, Marvel und Star Wars eine
Welt, die man ab und zu wählt.

## 2. Ziele, Nicht-Ziele, Anti-Ziele

**Ziele:** Welt `SUPERNATURAL` (Anzeigename „Supernatural“ in allen Sprachen), **Ziffer 5** (Reihenfolge nach Jahreskreis:
0 Clean, 1 Jahreskreis, 2 Hogwarts, 3 Marvel, 4 Star Wars, 5 Supernatural), keine Farbtaste. Komplette Haut wie die
Vier Welten: Szene Tag/Nacht, Dauerbewegung, Pills, Dock, Fokuswechsel, App-Start, Übergang, Gruß.
Dazu ein **eigenes Logo-Bild**, das Patrick nur auf den TV legt und das den gezeichneten Schriftzug ersetzt.

**Nicht-Ziele:** Keine Musik/Sounds. Keine Figuren oder Schauspieler (auch nicht als Silhouette mit Gesicht).

**Anti-Ziele:** Originallogo, Originalsymbole (Anti-Besessenheits-Tattoo, Teufelsfalle in Seriengrafik), Fotos oder
Szenenbilder im Repo. Ein Wagen mit Markenemblem. Ein Blitz, der den ganzen Schirm grell weiß macht (OLED, Blendung).

## 3. Rahmenbedingungen

Alle Regeln der Vier-Welten-Spec gelten (Clean bit-identisch, Szenen-JPEG ≤ 300 KB mit `common.py`-Prüfung, Overlays
≤ 30 fps, Effekte ≤ 1,2 s, Übergang ≤ 900 ms, Messgrenzen, Strings en/de/iw, `layout-iw`). Bildregel aus Jahreskreis:
Wichtiges rechts vom App-Namen direkt über der App-Leiste. Schriften nur SIL OFL: **Courier Prime** (Uhr, Gruß,
Datum, Hero), Lizenztext neben `FONT-LICENSE-OFL.txt`.

## 4. Was Supernatural zeigt

### 4.1 Szene (Tag „Himmel“, Nacht „Hölle“)

Gemeinsam: Landstraße von links unten in die Ferne nach rechts, Telegrafenmasten, flache Hügel, rechts ein einstöckiges
Motel mit Türen und Fenstern und einem Mast-Schild „MOTEL“ / „VACANCY“; davor am Straßenrand **ein langer, flacher
schwarzer Viertürer im Stil der späten 60er** (Seitenansicht, Chromleiste, Chromstoßstangen, kein Emblem), rechts vom
App-Namen über der App-Leiste; auf der Straße vor dem Wagen **ein Pentagramm im Kreis mit kurzen Strahlen** (eigene,
allgemeine Form); an der Stirnwand des Motels **ein gesprühter Bannkreis** (Doppelring, Sechseck, Dreieck, Zeichen
ringsum, eigene Form) und **kleine Schutzzeichen über den Türen**; über dem Motel der Schriftzug **„SUPERNATURAL“** in
Courier Prime (gezeichnet), solange kein eigenes Logo-Bild vorhanden ist (4.4).

| | Tag „Himmel“ | Nacht „Hölle“ |
|---|---|---|
| Himmel | goldene Stunde, Sonne tief, **Lichtstrahlen** | dunkel, Wolken |
| Boden | staubig-warm | links am Horizont **glühende Risse**, roter Schein |
| Motel | Schild aus, Fenster dunkel | **„MOTEL“ rot (ein Buchstabe setzt aus), „VACANCY“ blau flackernd**, einige Fenster warm erleuchtet |
| Stern | wie Kreide | **glimmt wie Glut** |
| Sigillen | rot gesprüht | rot, **leuchten beim Blitz auf** |
| Wagen | Sonnenreflex auf dem Dach | **Rücklichter glimmen rot**, Spiegelung auf nasser Straße |

### 4.2 Dauerbewegung (Overlay)

- Tag: treibender Staub im Gegenlicht, Hitzeflimmern über der Straße, Lichtstrahlen atmen leicht.
- Nacht: schräger Regen; Neon-Flackern; Glut des Sterns und der Risse pulsiert langsam; **etwa alle 9 s ein Blitz** in
  der Ferne (Himmel kurz aufgehellt, höchstens 40 % Deckkraft, Blitzlinie) und **für denselben Augenblick der Schatten
  riesiger Flügel** über dem Himmel; Sigillen leuchten dabei auf. Gesamtdauer des Blitzes ≤ 400 ms.

### 4.3 Bedienelemente

- **Pills:** eckig, Messingrand (Bernstein `#E0A24A`), dunkle Füllung; Icon-only; das Einstellungs-Icon ist der Stern
  im Kreis.
- **App-Leiste:** dunkel mit Bernsteinkante; auf ihrer Oberkante eine feine **weiße Salzlinie** (körnig).
- **Fokusrahmen:** Bernstein mit warmem Glow; **Fokuswechsel**: der Rahmen flackert beim Springen kurz wie eine unruhige
  Glühbirne (~150 ms), dann ruhig.
- **App-Start:** die **Scheinwerfer des Wagens blenden auf**, Lichtkegel füllt den Schirm, dann startet die App
  (≤ 1,2 s, Zurück bricht ab wie bei den anderen Welten).
- **Übergang in die Welt:** Blitz mit Flügelschatten, dann steht die Szene (≤ 900 ms).
- **Gruß:** Tag „Back on the road“, Nacht „Something's out there“ (eigene Sätze, keine Serienzitate) vor bzw. nach dem
  Tagesgruß wie bei den anderen Welten; Hero in Courier Prime.

### 4.4 Eigenes Logo-Bild (nur auf dem TV)

Liegt auf dem TV ein Bild `world_logo_supernatural.png` im Theme-Speicher des Launchers (gleicher Weg wie
Theme-Hintergründe, per `tools/theme.sh` oder ADB), ersetzt es den gezeichneten Schriftzug an derselben Stelle
(Höhe ≈ 6 % des Bildschirms, Seitenverhältnis bleibt, abgedunkelt auf ≤ 85 % Helligkeit). Fehlt es, bleibt der
Schriftzug. Das Bild kommt **nie ins Repo**. Der Mechanismus ist allgemein (`world_logo_<id>.png`), sodass später jede
Welt ein eigenes Logo bekommen kann.

### 4.5 Klarstellungen aus dem Builder-Check (26.10.)

- **Reihenfolge:** Supernatural wird **nach Jahreskreis** gebaut, auf dessen Branch (`jahreskreis`) bzw. nach dessen
  Merge. `SUPERNATURAL` steht im Enum direkt nach `STARWARS` und landet so auf Ziffer 5; keine festen Ziffern,
  kein Platzhalter.
- **Logo-Bild unabhängig vom Theme:** eigener Ordner `world_logos/` im App-Speicher, **nicht** im Theme-Ordner; ein
  Theme-Push und `--reset` lassen ihn unberührt. Neuer Schalter in `tools/theme.sh`: `--logo <welt-id> <datei.png>`
  legt `world_logo_<welt-id>.png` ab, `--logo <welt-id> --remove` entfernt es (ebenso in `theme.ps1`, falls vorhanden).
  Provider/`ThemeFiles` erlauben genau diesen Namen, PNG ≤ 2 MB. „Logo an/aus“ am TV = Datei vorhanden oder nicht.
- **Übergang je Tageszeit:** nachts Blitz mit Flügelschatten; tagsüber blenden die Lichtstrahlen auf (Himmel), dann
  steht die Szene. Beide ≤ 900 ms.

## 5. Was entschieden ist und von wem

- Patrick: Richtung „Highway bei Nacht“, Tag- und Nachtfassung, alle fünf Erkennungszeichen (Logo, Stern, Himmel und
  Hölle, Engelsflügel, Sigillen), Bedienelemente aus 4.3, Ziffer 5.
- Claude: Courier Prime statt der Mockup-Schrift (Lizenzregel), eigene allgemeine Formen statt Seriengrafik,
  Logo-Bild-Mechanismus allgemein für alle Welten, Blitz-Deckkraft ≤ 40 %; alle Punkte aus 4.5 (Bau nach Jahreskreis,
Logo-Ordner unabhängig vom Theme mit `--logo`, Tag-Übergang mit Lichtstrahlen).

## 6. Was der Umsetzende entscheidet

Partikelzahlen und genaue Zeiten innerhalb der Grenzen; ob Neon und Glut im Bild oder im Overlay leuchten; Form der
Flügel im Detail (zwei große, gefiederte Schwingen, keine Figur).

## 7. Prüfung

JVM: Ziffer 5 → SUPERNATURAL, Blitz-Taktung/-Dauer als reine Funktion, Logo-Fallback (kein Bild → Schriftzug).
Am TV: Screenshots Tag/Nacht und mitten im Blitz, gfxinfo, App-Start und Übergang, Logo-Bild an/aus.
