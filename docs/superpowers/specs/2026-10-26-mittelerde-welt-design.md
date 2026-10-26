# Mittelerde – siebte Welt für den Clean-Your-TV-Launcher (Fork)

Stand: 26.10.2026 · Auftraggeber: Patrick (gedacht auch für einen Kollegen, der sie später übernehmen kann) · Baut
auf: `2026-10-25-vier-welten-launcher-design.md`, `2026-10-26-jahreskreis-design.md` (Ziffern),
`2026-10-26-supernatural-welt-design.md` (Logo-Bild-Mechanismus) · Mockup: Artifact „Welt Mittelerde“
(https://claude.ai/artifact/Apib24sQY2KaBGuZkzeeHp, Version 4).

## 1. Absicht

Eine Welt im Geist von Herr der Ringe als **gestufte Silhouetten-Landschaften mit Körnung** (Stil-Anregung: ein
dreiteiliges Fan-Poster, das nicht übernommen wird). Drei Stimmungen über den Tag erzählen eine Reise; eine
Gefolgschaft wandert als Silhouette über den vorderen Kamm. Feste Welt im Setup; ein Kollege kann sie später
übernehmen.

## 2. Ziele, Nicht-Ziele, Anti-Ziele

**Ziele:** Welt `MIDDLE_EARTH` (Anzeigename „Mittelerde“, EN „Middle-earth“, iw „Middle-earth“), **Ziffer 6**
(Enum-Reihenfolge nach Supernatural), keine Farbtaste. Komplette Haut wie die Vier Welten. **Drei Tagesstimmungen**
statt zwei.

**Nicht-Ziele:** Musik/Sounds; Karten oder Texte aus den Büchern; ein eigener Launcher-Build für den Kollegen (das
Übernehmen ist ein späteres Vorhaben).

**Anti-Ziele:** Filmbilder, Plakatmotive, Schauspieler-Ähnlichkeit, Gesichter, Filmlogos, Zitate aus Buch oder Film,
die Ring-Inschrift im Original. Die Gefolgschaft ist erkennbar **nur an der Zusammensetzung** der Silhouetten.

## 3. Rahmenbedingungen

Regeln der Vier-Welten-Spec (Clean bit-identisch, Szenen-JPEG ≤ 300 KB mit `common.py`-Prüfung, Overlays ≤ 30 fps,
Effekte ≤ 1,2 s, Übergang ≤ 900 ms, Messgrenzen, Strings en/de/iw, `layout-iw`) und die Bildregel (Wichtiges rechts
vom App-Namen direkt über der App-Leiste). Schrift **Cinzel** (SIL OFL) für Uhr, Gruß, Datum, Hero; Lizenztext
neben `FONT-LICENSE-OFL.txt`. Bau **nach Supernatural** (Ziffer 6 aus der Enum-Reihenfolge).

## 4. Was Mittelerde zeigt

### 4.1 Drei Stimmungen

Grenzen nach dem Sonnenstand am konfigurierten Ort (wie die anderen Welten, `Config.location`): **Morgen** von
Sonnenaufgang bis 11:00, **Tag** von 11:00 bis Sonnenuntergang + 30 min, **Abend/Nacht** danach bis
Sonnenaufgang. Liegt der Sonnenaufgang nach 11:00 (nur theoretisch), gibt es keinen Morgen. Wechsel mit 300 ms
Überblendung wie in `WorldScene`; `forceMood` bekommt die dritte Wahl „Morgen“.

| Stimmung | Szene (Mockup) | Bewegung |
|---|---|---|
| Morgen „Hügelland“ (Grundton Grün) | Hügel mit Grashalmen, Wolkenstreifen, Feld-Flickenteppich mit Hecken; rechts im Hang eine **runde gelbe Tür** mit Brettern und Knauf, runde Sprossenfenster warm erleuchtet, Schornstein; darüber ein **großer Baum** mit Rinde und schattierter Krone; gewundener Weg mit Zaun und Blumen | Rauch aus dem Schornstein, Morgennebel |
| Tag „Neblige Berge“ (Blaugrau) | drei Gebirgsketten mit Gesteinsschichten und Schneegipfeln, zwei Reihen Nadelwald, vorne **knorrige alte Bäume** mit Rindenmaserung; rechts oben ein **schlanker dunkler Turm** mit Stockwerken im Dunst | ziehender Talnebel |
| Abend/Nacht „Dunkler Turm“ (Rot) | Ascheschichten am Himmel, links ein **Vulkan** mit schraffierten Flanken und drei Lavarinnen, rechts über der App-Leiste ein **gezackter schwarzer Turm** mit Strebepfeilern, Stockwerken und glimmenden Fenstern; davor ein Speer-Heer auf zerklüftetem Grund | Glut und Funken am Krater, fallende Asche; auf der Turmspitze ein **feuriges Auge** (pulsierend) mit langsam übers Land schweifendem Lichtkegel |

### 4.2 Die Gefolgschaft

Zehn Silhouetten auf dem vorderen Kamm, **auf der Oberkante der App-Leiste**, wandern von links nach rechts, in
jeder Stimmung, Farbe = dunkelster Ton der Szene, weiche Kontur (Gegenlicht). Reihenfolge von vorn: großer Wanderer
mit breitem Spitzhut, langem Mantel und knorrigem Stab; ein Kleiner; der Schlanke mit Bogen und Köcher; der Zwerg
(stämmig, Bart, Doppelaxt über der Schulter); zwei Kleine; ein Krieger mit Schwert; ein Kleiner, der **ein Pferd mit
Packtaschen** führt; ein Krieger mit Rundschild auf dem Rücken. Kapuzenmäntel wehen nach hinten, Beine schreiten.
**Tempo:** einmal quer über den Bildschirm in etwa 6 min, dann Pause von zufällig 2–5 min, dann wieder von links.
**Hinter dem App-Namen** (dessen horizontaler Ausdehnung plus 4 % Rand) wird die Gruppe weich auf 25 % Deckkraft
gedimmt (Übergang über 3 % der Breite), außerhalb voll sichtbar; der Bereich folgt dem fokussierten App-Namen.

### 4.3 Bedienelemente

- **Pills:** rund, dünner Goldrand `#D9B56A`, dunkle Füllung; Icon-only; Einstellungs-Icon ein schlichter Ring.
- **App-Leiste:** dunkel mit feiner goldener Oberkante.
- **Fokusrahmen:** Gold mit warmem Glow; **Fokuswechsel:** kurzes goldenes Aufleuchten (Glanzlicht wandert einmal
  über den Rahmen, ~200 ms).
- **App-Start:** eine **Schrift aus Feuer läuft einmal um den Rahmen** der gewählten Kachel (eigene, erfundene
  Zeichenreihe, keine echte Inschrift), dann startet die App (≤ 1,2 s, Zurück bricht ab).
- **Übergang in die Welt:** Nebel zieht über das Bild, dann erscheint die Landschaft der Stimmung (≤ 900 ms).
- **Gruß:** Morgen „Ein neuer Tag im Hügelland“, Tag „Weit ist der Weg“, Abend/Nacht „Das Auge wacht“ (eigene
  Sätze; EN „A new day in the hill country“, „The road goes far“, „The eye is watching“), nach dem Tagesgruß wie in
  den anderen Welten.
- **Eigenes Logo-Bild:** `world_logo_middleearth.png` über den allgemeinen Mechanismus aus der Supernatural-Spec
  (`theme.sh --logo middleearth <datei>`), sonst kein Schriftzug.

### 4.4 Klarstellungen aus dem Builder-Check (26.10.)

- **Reihenfolge:** Mittelerde wird nach Jahreskreis **und** Supernatural gebaut, auf deren gemergtem Stand; den
  Logo-Mechanismus (`world_logos/`, `theme.sh --logo`, Freigabe in `ThemeFiles`) liefert Supernatural, Mittelerde
  nutzt ihn nur. Ziffer 6 ergibt sich dann aus der Enum-Reihenfolge.
- **Logo-Fläche:** Mittelerde hat keinen gezeichneten Schriftzug. Ein Logo-Bild steht **oben in der Mitte** zwischen
  Kopfbereich und Pills: Mittelpunkt x = 57 %, y = 11 % der Bildschirmhöhe, höchstens 8 % Höhe und 18 % Breite,
  Seitenverhältnis bleibt, ≤ 85 % Helligkeit; in allen drei Stimmungen an derselben Stelle. Alle drei Szenen halten
  diese Fläche frei (nur Himmel/Wolken).
- **Hebräisch:** iw übernimmt die englischen Texte (Name „Middle-earth“ und die drei Grüße). Cinzel bleibt gesetzt;
  hebräische Zeichen kommen aus der Systemschrift über Androids Schrift-Fallback, ohne eigene Ausweichschrift.

## 5. Was entschieden ist und von wem

- Patrick: Silhouetten-Stil nach dem Poster als Idee, drei Stimmungen über den Tag, Turm mit Auge, bergige
  Landschaft, Gefolgschaft (besser gezeichnet nach seinem Referenzbild), Verblassen hinter dem App-Namen, Bedien-
  elemente aus 4.3, Ziffer 6, fester Teil des Setups.
- Claude: Name „Mittelerde“, Morgen-Grenze 11:00, Tempo/Pausen der Gruppe, Cinzel, erfundene Feuerschrift statt
  Originalinschrift, Pferd statt Pony (nach dem Referenzbild); alle Punkte aus 4.4 (Bau nach Jahreskreis und
  Supernatural, Logo-Fläche oben Mitte, iw mit englischen Texten und System-Fallback).

## 6. Was der Umsetzende entscheidet

Partikelzahlen und Zeiten innerhalb der Grenzen; ob die Gruppe als Overlay-Pfade oder als vorgerenderte
Silhouetten-Bitmaps (je Figur wenige Schrittbilder) gezeichnet wird – Ziel: ruhige 30 fps, keine Allokation pro
Frame; Ermittlung der Breite des App-Namens.

## 7. Prüfung

JVM: Stimmungsgrenzen (Sonnenaufgang vor/nach 11:00, Abenddämmerung), Ziffer 6, Dimm-Kurve der Gruppe als reine
Funktion (`alphaAt(x, nameLeft, nameRight)`), Gruppen-Takt (Laufzeit + Pause). Am TV: Screenshots je Stimmung und mit
der Gruppe vor/hinter dem App-Namen, gfxinfo, App-Start, Übergang, Logo-Bild an/aus.
