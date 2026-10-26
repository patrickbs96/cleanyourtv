# Jahreskreis – fünfte Welt für den Clean-Your-TV-Launcher (Fork)

Stand: 26.10.2026 · Auftraggeber: Patrick · Baut auf: `2026-10-25-vier-welten-launcher-design.md` (Welt-Modell,
Szenen, Overlays, Tastenbelegung) · Mockups: Artifact „Welt Saison“ (https://claude.ai/artifact/Lf6gJrC5ANkRWMNAAQ4LQV),
Stand Version 14.

## 1. Absicht

Der Wohnzimmer-TV (Philips 65OLED934) soll eine Welt haben, die man **einmal wählt und die sich dann übers Jahr von
selbst verwandelt**: Jahreszeiten als Grund, Feste darüber, und die Tage des Haushalts (Geburtstage, Jahrestag,
Hochzeitstag) als Höhepunkte. Der Haushalt besteht aus zwei Personen; in ein paar Jahren kommen Kinder dazu, deshalb
zählen auch Feste, die vor allem Kindern Spaß machen (St. Martin, Nikolaus, Ostern, Karneval).

Jahreskreis wird die **Standardwelt** des Haushalts. Man ist meist nur kurz auf Home (App wählen und los), deshalb
darf sie sich bewegen; sie soll aber nie nerven und nie den OLED gefährden.

## 2. Ziele, Nicht-Ziele, Anti-Ziele

**Ziele**
- Eine fünfte Welt „Jahreskreis“ (Englisch „Seasons“), Ziffer **1**, die ihr Motiv aus dem Datum ableitet.
- Vier Jahreszeiten, je Tag- und Nachtfassung, plus Anlässe und persönliche Tage wie in Abschnitt 4.
- Persönliche Tage werden **auf dem TV** gepflegt, nicht im Repo.
- Persönliche Tage sind in **jeder** Welt sichtbar, abgestuft (4.6).
- Wandernde Termine (Ostern, Karneval, Mutter-/Vatertag) rechnet der TV selbst, ohne Netz.

**Nicht-Ziele**
- Keine Kalender- oder Kontakte-Anbindung, keine Cloud, keine weiteren Netzzugriffe.
- Keine Feiertage anderer Bundesländer oder Religionen in dieser Ausbaustufe.
- Supernatural und Herr der Ringe sind eigene Vorhaben (später, je eigene Spec).

**Anti-Ziele** (wäre ein Fehlschlag, auch wenn es technisch läuft)
- Ein Motiv, das wichtige Dinge hinter der App-Leiste versteckt (Eier, Kürbisse, Geschenke: am TV schon einmal passiert
  in den Mockups).
- Dauer-Effekte, die beim zehnten Home-Druck nerven, in den Themenwelten Hogwarts/Marvel/Star Wars.
- Namen oder Geburtsdaten im öffentlichen Repo `patrickbs96/cleanyourtv`.
- Logos, Figuren, Markenschriften, Filmbilder oder geschützte Zitate (gilt wie bei den Vier Welten; der 4. Mai darf
  ein eigenes „May the 4th“-Wortspiel nutzen, nicht das bekannte Filmzitat).

## 3. Rahmenbedingungen

- Alle Regeln der Vier-Welten-Spec gelten weiter, insbesondere: Clean bleibt bit-identisch; Szenen 1920×1080 JPEG
  ≤ 300 KB aus `make_scene.py` mit festem Seed; Overlays ≤ 30 fps, keine Allokation pro Frame, Pause in `onPause` und
  bei `reduced_motion`; Messgrenzen aus Task 12 (gfxinfo 95th ≤ 16 ms, Janky ≤ 5 %, RSS ≤ +25 MB gegen Clean,
  Kaltstart ≤ +300 ms, keine Fläche > 60 % Luminanz länger als 10 min statisch).
- **Ziffernbelegung neu:** 0 Clean, 1 Jahreskreis, 2 Hogwarts, 3 Marvel, 4 Star Wars. `World`-Enum-Reihenfolge
  entsprechend (Jahreskreis direkt nach CLEAN). Farbtasten bleiben wie sie sind (Jahreskreis bekommt keine).
- **Bildregel:** Was zählt (Eier, Kürbisse, Baum mit Geschenken, Kuchen, Laternen …), steht **rechts vom App-Namen
  direkt über der App-Leiste**, nie dahinter und nicht im Kopfbereich links (Gruß, Uhr, Datum, Wetter). Die Dock-
  Oberkante liegt am TV bei y ≈ 766 von 1080.
- Strings in `values` (Englisch), `values-de`, `values-iw`; neue Views auch in `layout-iw`.
- Hebräisch: Jahreskreis muss ohne Absturz laufen; Feste bleiben die deutschen (kein Kalender je Sprache).

## 4. Was Jahreskreis zeigt

### 4.1 Grund: Jahreszeiten (astronomisch, feste Grenzen)

| Jahreszeit | von – bis | Motiv (Mockup) |
|---|---|---|
| Winter | 21.12. – 20.03. | verschneite Hügel unter Nachtblau, leiser Schneefall |
| Frühling | 21.03. – 20.06. | Hügel im ersten Grün, Kirschblüten treiben |
| Sommer | 21.06. – 22.09. | Meer im Abendlicht, tiefe Sonne, Glühwürmchen |
| Herbst | 23.09. – 20.12. | Waldsilhouette in Bernstein und Rost, fallende Blätter |

Jede Jahreszeit hat eine **Tag- und eine Nachtfassung** (Wechsel wie in Vier-Welten § 4.5, Sonnenstand Wohnort).
Helle Himmel (Frühling, Ostern) werden in der Nachtfassung gedämpft (OLED-Regel).

### 4.2 Anlässe

| Anlass | Zeitraum | Motiv (Mockup) |
|---|---|---|
| Silvester & Neujahr | 31.12. – 01.01. | Skyline über der Leiste mit beleuchteten Fenstern, Feuerwerk in Abständen |
| Karneval | Weiberfastnacht – Rosenmontag | Luftschlangen und Konfetti vor dunkler Bühne |
| Valentinstag | 14.02. | Abendtisch am Fenster, rote Rosen, zwei Gläser, Kerze, schwebende Blütenblätter |
| Ostern | Palmsonntag – Ostermontag | Pastell-Morgen, bemalte Eier rechts über der Leiste, zwei Schmetterlinge |
| Muttertag | 2. Sonntag im Mai | bunter Strauß, selbst gemalte Herzkarte „Mama“ – **aus, bis eingeschaltet** |
| Vatertag | Christi Himmelfahrt | Feldweg, der rechts aus dem Bild läuft, Bollerwagen mit Picknickkorb – **aus, bis eingeschaltet** |
| Halloween | 24. – 31.10. | Orangemond, kahler Baum, gerippte Kürbisse mit flackernd leuchtenden Gesichtern, Fledermäuse |
| St. Martin | 01. – 11.11. | Laternenzug in der Dämmerung, Lichter schaukeln |
| Nikolaus | 05. – 06.12. | Haustür mit Kranz und Laterne, zwei geputzte Stiefel: am 5. leer, am 6. gefüllt (Mandarinen, Nüsse, Schokolade, Zuckerstange) |
| Advent | 1. Advent – 23.12. | Stube: Kranz mit Tannengrün, Beeren, Schleifen auf Holztisch, **so viele Kerzen brennen, wie Advent ist**; Plätzchenteller; Fenster mit Schnee und leuchtendem Papierstern; Lichterkette oben |
| Weihnachten | 24. – 26.12. | geschmückter Baum (Kugeln, Lametta-Girlanden, funkelnde Lichter, Stern), Geschenke darunter, Schneefall |

Wandernde Termine: Ostersonntag nach Gauß/Meeus (gregorianisch); Weiberfastnacht = Ostern − 52 Tage, Rosenmontag =
Ostern − 48, Palmsonntag = Ostern − 7, Ostermontag = Ostern + 1, Christi Himmelfahrt = Ostern + 39; 1. Advent = der
Sonntag zwischen 27.11. und 03.12.; Muttertag = 2. Sonntag im Mai.

### 4.3 Persönliche Tage

| Tag | Wann | Look in Jahreskreis |
|---|---|---|
| Geburtstag | eingetragenes Datum, ganztägig | zwei Wimpelgirlanden „HAPPY“ (oben) und „BIRTHDAY“ (darunter), tief durchhängend, rechts neben der Uhr; Buchstaben drehen sich mit ihrem Wimpel; **Name groß** unter der Girlande; Kuchen mit vier Kerzen und Ballon-Dreierbündel links davon, die Schnüre an der **Oberkante der App-Leiste** festgebunden (Schleife über der Leiste gezeichnet); Konfetti regnet durchgehend |
| Jahrestag („Zwei Sonnen“) | eingetragenes Datum und Startjahr | Wüste im Sonnenuntergang mit drei Dünenreihen, zwei Sonnen dicht beieinander, funkelnde Sterne; ein kleiner Projektor im Sand wirft ein flimmerndes Holo-Herz mit der **Zahl der Jahre** (Jahr − Startjahr), der Strahl endet an der Herzspitze; Gruß aus Vorlage, z. B. „Happy {Tag} · {Namen}“; **beim ersten Home des Tages einmal ein Vorspann** (gelber, nach hinten gekippter Lauftext, eigener Text, z. B. „Vor nicht allzu langer Zeit, in einer Stadt ganz in der Nähe … Episode {Jahre}: {Namen} …“), Zurück überspringt, danach ruhig |
| Hochzeitstag | eingetragenes Datum und Hochzeitsjahr | Gartenabend, Rosenbogen mit Grün, Rosen und Lichterkette, zwei ineinander greifende goldene Ringe mit wanderndem Funkeln, schwebende Blütenblätter; **Namen groß** oben rechts; Gruß „{n}. Hochzeitstag“ (n = Jahr − Hochzeitsjahr; erst ab n = 1, am Hochzeitstag selbst nichts) |

Zwei Geburtstage am selben Tag: beide Namen („{Name} & {Name}“).

### 4.4 Vorrang

Gilt an jedem Tag genau ein Look, in dieser Reihenfolge (erster Treffer gewinnt):
1. persönliche Tage (Geburtstag, Jahrestag, Hochzeitstag; fallen zwei zusammen, gewinnt der Geburtstag, die anderen
   erscheinen im Gruß),
2. eintägige Anlässe (Valentinstag, Muttertag, Vatertag, Nikolaus 6.12.),
3. mehrtägige Anlässe (Silvester/Neujahr, Karneval, Ostern, Halloween, St. Martin, Nikolaus-Vorabend 5.12., Advent,
   Weihnachten),
4. Jahreszeit.

Beispiel: Nikolaus erscheint mitten im Advent, am 7.12. ist wieder Advent.

### 4.5 Bewegung

Grundszenen ruhig (wenige, langsame Partikel), Anlässe etwas lebhafter, Geburtstag mit Dauer-Konfetti. Alles über die
bestehende Overlay-Mechanik (`WorldOverlay`), Grenzen aus § 3. Der Vorspann am Jahrestag ist die einzige Ausnahme von
„Effekte ≤ 1,2 s“: er läuft einmal am Tag, höchstens 20 s, Zurück oder jede Taste beendet ihn.

### 4.6 Persönliche Tage in den anderen Welten (Clean, Hogwarts, Marvel, Star Wars)

- Der Gruß wird zum Glückwunsch im Stil der Welt („Lumos · Alles Gute, {Name}“, „Happy Birthday, {Name}“ in Clean).
- Neben der Uhr den ganzen Tag ein kleines Icon: Kuchen (Geburtstag), Holo-Herz (Jahrestag), zwei Ringe (Hochzeitstag).
- Beim **ersten Home des Tages einmal** Konfetti in den Farben der Welt, wenige Sekunden. Kein Dauer-Konfetti, kein
  Vorspann.
- **Clean bleibt bit-identisch an allen anderen Tagen**; an persönlichen Tagen ist der Glückwunsch die bewusste
  Abweichung.

### 4.7 Pflege auf dem TV

Neue Seite in den Launcher-Einstellungen: **„Jahreskreis“** mit
- Liste persönlicher Tage: Art (Geburtstag, Jahrestag, Hochzeitstag), Name(n), Datum, Startjahr (für die Zählung);
  Hinzufügen, Ändern, Entfernen (Entfernen mit Rückfrage, Fokus auf Abbrechen),
- Vorspann-Text für den Jahrestag (Standardtext vorbelegt),
- Schalter Muttertag und Vatertag (Standard aus).

Gespeichert in `SharedPreferences` des Launchers, wie die Tastenbelegung. Eingabe per Fernbedienung (Android-Tastatur
für Namen, Datumsauswahl ohne Tastatur). Erstbefüllung nach dem Bau durch Patrick am TV oder durch den Controller per
ADB-Befehl, den Patrick freigibt; die Daten stehen nur auf dem TV und im Gerätebackup (`backup/`, gitignored).

### 4.8 Klarstellungen aus dem Builder-Check (26.10.)

- **„Standardwelt“** heißt: Auf Patricks TV ist Jahreskreis die gewählte Welt (einmal per Ziffer 1 oder Einstellungen
  gesetzt; die Wahl bleibt über Neuinstallationen erhalten). Im Code bleibt **Clean der Rückfall** für fehlende oder
  unbekannte Werte (`World.parse`), damit der öffentliche Fork und alle Tests unverändert bei Clean starten.
- **Persönliche Tage sind Einträge mit Art, nicht feste Daten im Code.** Arten: Geburtstag, Jahrestag, Hochzeitstag.
  Die Art bestimmt den Look: Jahrestag = Wüste mit zwei Sonnen, Holo-Herz und Vorspann (egal welches Datum),
  Hochzeitstag = Rosenbogen, Geburtstag = Girlande. Patricks konkrete Daten stehen nur am TV (und privat im Second Brain), nicht
  im Code. **Alle Texte mit Namen** (Gruß „Happy {Tag} · {Namen}“, Vorspann „Episode {Jahre}: {Namen}“, Girlanden-Name,
  „{n}. Hochzeitstag“) sind Vorlagen mit Platzhaltern; im Repo stehen keine Namen.
- **Tag/Nacht nur für die Jahreszeiten.** Anlässe und persönliche Tage haben je eine Fassung (abendlich gestaltet);
  helle Anlass-Looks (Ostern, Muttertag, Vatertag, Valentinstag-Fenster) werden nachts per Szenen-Dimmung abgesenkt
  wie die Nachtfassungen (OLED-Regel).
- **Theme-`Festival`** (config.json eines Themes) bleibt wie es ist. In Jahreskreis gewinnt Jahreskreis; an
  persönlichen Tagen gewinnt in allen Welten der Glückwunsch-Gruß gegen den Festival-Gruß. Ohne persönlichen Tag
  läuft Clean mit Festival exakt wie heute.
- **Effekte:** Jahreskreis nutzt für Dock-Wechsel und App-Start das **Clean-Verhalten** (keine eigenen Effekte
  je Look); eigen sind nur Szene, Overlay, Akzentfarbe und ein einfacher Überblend-Übergang beim Hineinwechseln.

## 5. Was entschieden ist und von wem

- Patrick: Name „Jahreskreis“, Standardwelt auf Ziffer 1, Clean auf 0, Geburtstag mit Girlande/Name/Kuchen/Ballons an
  der Leiste, abgestufte Anzeige in anderen Welten inkl. Kuchen-Icon, kein Dauer-Konfetti in anderen Welten, Jahrestag
  mit allen drei Ideen, Hochzeitstag mitzählend, alle Anlässe aus 4.2, Vorrang „kürzer gewinnt“.
- Claude (vorgeschlagen, nicht ausdrücklich bestätigt): englischer Name „Seasons“; Mutter-/Vatertag standardmäßig aus;
  astronomische Jahreszeiten-Grenzen; Pflege-Seite statt Kalender; 20-s-Grenze und Tastenabbruch für den Vorspann;
  Geburtstag schlägt Jahrestag/Hochzeitstag bei Gleichstand; alle Punkte aus 4.8 (Clean bleibt Code-Rückfall,
  Einträge mit Art und Namens-Vorlagen, Tag/Nacht nur Jahreszeiten, Vorrang vor Theme-Festival, Clean-Verhalten für
  Dock-Wechsel und App-Start).

## 6. Was der Umsetzende entscheidet

- Pills, Fokusrahmen, Dock-Stil, Schrift und Übergänge von Jahreskreis: zurückhaltend und vom Anlass gefärbt (Akzent
  je Look), auf Basis der bestehenden Bausteine (`WorldChrome`, `GlassView`, `WorldDockDecoration`,
  `WorldTransition`). Vorschlag: Clean-Schrift Outfit, Akzentfarbe aus dem Look, einfacher Überblend-Übergang.
- Ob Anlässe als eigene JPEGs oder als Overlay-Schicht über der Jahreszeit gebaut werden (Speicher, APK-Größe).
- Wie die Datumslogik gekapselt wird (rein funktional, JVM-testbar ist Pflicht).
- Reihenfolge der Umsetzung; sinnvoll: Datumslogik und Pflege-Seite zuerst, dann Jahreszeiten, dann Anlässe,
  persönliche Tage zuletzt.

## 7. Prüfung

- JVM-Tests: Datumsregeln (Ostern mehrerer Jahre, Advent-Grenzen, Vorrang, Schaltjahr 29.02. als Geburtstag → im
  Nicht-Schaltjahr am 28.02.), Jahreszählung.
- Testschalter wie `forceMood`: ein erzwungenes Datum, damit jeder Look am TV per Screenshot geprüft werden kann.
- Am TV: Screenshot je Look (Tag/Nacht), gfxinfo je Look mit Bewegung, Sichtprüfung der Bildregel (nichts hinter der
  Leiste), Vorspann einmal am Tag und per Taste abbrechbar.
