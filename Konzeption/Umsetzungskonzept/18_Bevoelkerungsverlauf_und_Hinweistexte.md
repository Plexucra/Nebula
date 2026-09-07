# 18. Bevölkerungs-Verlaufsgrafik und Hinweistexte

Zwei UI-/Transparenzthemen, kein Balancing.

## A. Bevölkerungs-Verlaufsgrafik

### Ausgangslage

Pro Kolonie gab es bisher **keine** Verlaufsdaten – nur `universeStats` mit
galaxieweiten Mittelwerten. Vorhanden waren `sparkline.ts` und
`SparklineTileComponent` als Bausteine.

### Backend: Ringpuffer und Einordnung

- Neue Modelle `PopulationSample` (Zeitpunkt, Bevölkerung, Lebensstandard,
  Wohnkapazität, Wachstumszustand) und `PopulationTrend` (Messpunkte, Phase,
  begrenzender Faktor, Fensterlänge in Spielstunden).
- `PopulationHistory.record` schreibt je Kolonie im Takt der
  Universums-Statistik (`STATS_SNAPSHOT_INTERVAL_MS` = 10 s Realzeit =
  4 Spielstunden) einen Messpunkt; `MAX_SAMPLES = 120` begrenzt den Ringpuffer.
  Das ergibt ein Fenster von **20 Realminuten bzw. rund 480 Spielstunden
  (20 Spieltagen)**; ältere Punkte fallen hinten heraus – "muss nicht unendlich
  zurückreichen" ist ausdrücklich erlaubt. Speicher: 120 Einträge × Kolonienzahl.
- Neue Query **`populationTrend(colonyId)`**.

**Die Phasen-Einordnung entsteht im Backend**, nicht in der Oberfläche: sie ist
eine Aussage über die Spielregeln (Totband, Wohnraumgrenze, Versorgung) und
gehört damit auf dieselbe Seite wie die Regeln selbst (Grundsatz aus
Dokument 15, Auftrag 3). Verfahren: der Zuwachs je Messpunkt wird in der
**jüngeren gegen die ältere Hälfte** des Fensters verglichen.

| Phase | Bedingung |
|---|---|
| `TooFewSamples` | weniger als 4 Messpunkte – es wird ehrlich gesagt, dass noch keine Aussage möglich ist |
| `Shrinking` | Zustand schrumpfend oder jüngerer Zuwachs deutlich negativ |
| `Plateau` | jüngerer Zuwachs praktisch null (< 0,2 % der Bevölkerung je Messpunkt) |
| `Accelerating` | jüngerer Zuwachs > 115 % des älteren |
| `Slowing` | jüngerer Zuwachs < 85 % des älteren |
| `Steady` | sonst |

Zusätzlich wird bei `Slowing`/`Plateau`/`Shrinking` der **begrenzende Faktor**
bestimmt: `Housing`, wenn der Wohnraum zu mindestens 85 % belegt ist, sonst
`Supply`, wenn ein Grundkonsumgut unter 95 % Deckung liegt. Damit beantwortet
die Grafik genau die Frage, um die es dem Nutzer geht: *hängt es an der
Versorgung oder am Wohnraum?*

### Frontend: `PopulationChartComponent`

Eigene, deutlich größere Grafik (560 × 180) im Tab „Bevölkerung" – kein
Kachel-Sparkline: Y-Achse mit drei beschrifteten Stützwerten, Flächenverlauf,
aktueller Wert mit Belegungsanteil, Zeitfenster in **Spieltagen** benannt.
Darunter die Phase in Worten mit farblicher Tönung, ein erklärender Satz und –
falls etwas bremst – der Hinweis, was zu tun ist.

**Skalierungsentscheidung (bewusst):** Die Wohnkapazität wird **nicht** als
Referenzlinie gezeichnet. Sie liegt mit 20.000 Plätzen auf Stufe 1 um
Größenordnungen über einer jungen Kolonie; im selben Maßstab wäre die
eigentliche Kurve eine flache Linie am unteren Rand und die Grafik wertlos.
Stattdessen skaliert die Y-Achse auf den **tatsächlichen Wertebereich des
Fensters** (mit 10 % Luft), und die Kapazität erscheint als Text mit
Belegungsanteil ("155 von 20.000 Plätzen (0,8 % belegt)"). Ob der Wohnraum
knapp wird, sagt ohnehin die Einordnung – dafür braucht es keine Linie.

### Verifikation

Protokollseitig gegen den laufenden Server, beide Pfade:

```
frische Kolonie: 0 Messpunkte, Phase=TooFewSamples → UI zeigt "noch zu wenig Verlauf"
 1 Punkt   | TooFewSamples |    –    | Bev 120,5
 4 Punkte  | Steady        |    –    | Bev 134,9 | Fenster 12 h (0,5 Spieltage)
 8 Punkte  | Steady        |    –    | Bev 155,6 | Fenster 28 h (1,2 Spieltage)
```

Gegenprobe mit gekappter Versorgung (Startaufträge und Verkaufsorders storniert):

```
 3 Punkte  | TooFewSamples |    –    | Bev 109,7 | Lebensstandard 0 %
 4 Punkte  | Shrinking     | Supply  | Bev 105,3 | Lebensstandard 0 %
10 Punkte  | Shrinking     | Supply  | Bev  82,8 | Lebensstandard 0 %
```

Die Einordnung erkennt den Rückgang und benennt die Versorgung als Ursache –
nicht den Wohnraum, der mit 20.000 Plätzen reichlich vorhanden ist.

## B. Hinweistexte in der gesamten Anwendung

**Geprüft: 80 Hinweis- und Tooltip-Texte** in allen Feature-Ansichten
(Kolonie-Detail mit allen sieben Tabs, Produktion, Flotten, Bodentruppen,
Diplomatie, Nachrichten, Galaxiekarte, Handel, Konto, Statistiken) plus
App-Shell, Kampfbericht, Startseite und Produkt-Auswahl. **Geändert: 20** –
davon **7 sachlich falsch**, 8 zu technisch im Ton, 5 fehlende Erklärungen.

### Sachlich falsch (Regeln hatten sich geändert)

| Ort | Vorher | Nachher |
|---|---|---|
| Kolonie, Bevölkerung | „Noch genug Platz … Kapazität kommt aus **Wohnkomplex + Energienetz**" | „Es ist reichlich Platz vorhanden – den Wohnraum stellt **allein der Wohnkomplex** (Tab „Bebauung"). Ob die Kolonie wächst, entscheidet derzeit die Versorgung." |
| Kolonie, Bevölkerung | „Wohnkapazität erschöpft – … bis **Wohnkomplex und/oder Energienetz** ausgebaut werden." | „Der Wohnraum ist voll – ohne zusätzliche Unterkünfte geht die Bevölkerung langsam zurück. Ein Ausbau des Wohnkomplexes verdoppelt den Platz." |
| Kolonie, Bevölkerung | „Wohnkapazität wird knapp – **Wohnkomplex und/oder Energienetz** bald ausbauen" | „Der Wohnraum wird knapp. Ein Ausbau des Wohnkomplexes **verdoppelt** die Kapazität …" |
| Kolonie, Bevölkerung | „**Energienetz**-Blackout: … mindert den **Kapazitätsbeitrag des Energienetzes** anteilig." | „Die **Infrastruktur** ist unterversorgt … arbeiten die Anlagen nur mit einem Bruchteil ihrer Leistung, und Lebensstandard wie Sicherheit brechen ein." |
| Kolonie, Produktion | „Blackout: … bis das **Energienetz** wieder … versorgt ist." | „Der **Infrastruktur** fehlt Stabilisiertes Elerium – bis wieder genug im Lager liegt, arbeiten alle Anlagen nur mit einem Bruchteil ihrer Leistung." |
| Statistiken | Tooltip „**Energienetz** nicht ausreichend versorgt – Produktion läuft nur mit 10 %, Sicherheit/Lebensstandard halbiert." | „Der **Infrastruktur** fehlt Stabilisiertes Elerium – die Produktion kriecht, Sicherheit und Lebensstandard brechen ein." |
| Produktion (2×) | „Masse/Volumen pro Einheit – **künftig** Frachterlimit" | „Masse/Volumen je Einheit – begrenzt zusammen … **wie viel eine Flotte laden kann**" (das Limit gilt längst, `loadCargo` setzt es durch) |
| Konto | „es gibt **keine klassische Steuer** und keinen automatischen Gelddrucker" | „… Einmal pro Spieltag zieht der galaktische **Ausgleichsfonds** einen kleinen Anteil großer Vermögen ein und verteilt ihn pro Kopf an alle Kolonien." (der Fonds existiert und zieht wirklich ab) |

### Ton: Zusammenhang statt Rechenvorschrift

| Vorher | Nachher |
|---|---|
| „Aus Lebensstandard × Sicherheit – steuert direkt, wie schnell die Bevölkerung wächst (100 % = Referenzgeschwindigkeit)." | „Wie gut es den Menschen hier geht – gut versorgt und sicher, dann wächst die Kolonie zügig; fehlt es an Waren oder Schutz, kommt das Wachstum ins Stocken. Anhaltend schlechte Verhältnisse kosten außerdem Loyalität." |
| „Aus stationierten Bodentruppen relativ zur Bevölkerung, mindestens 30 % der Loyalität als Sockel." | „Wie gut die Kolonie geschützt ist – getragen von den hier stationierten Truppen im Verhältnis zur Einwohnerzahl; eine loyale Bevölkerung sorgt auch ohne Garnison für ein Mindestmaß an Ordnung." |
| „Politischer Langzeitwert, bei 100 % gedeckelt." | „Die Verbundenheit der Bevölkerung mit Ihnen – sie verändert sich nur langsam und wächst, solange die Menschen gut versorgt und sicher leben." |
| „Geglättete Deckung des Konsumbedarfs am Systemmarkt." | „Wie gut die Bevölkerung mit Konsumgütern versorgt ist, die sie am Systemmarkt kaufen konnte – Ausreißer einzelner Momente sind geglättet." |
| „(Arbeitskraft-Faktor, 0,35–5×, ab ~2000 Einwohnern gedeckelt)" | „(je mehr Menschen hier leben, desto mehr Hände arbeiten mit – ab einigen tausend Einwohnern ist der Vorteil ausgereizt)" |
| „(Gebäude-Tempofaktor, siehe Tab „Bebauung")" | „(jede Ausbaustufe des Industriekomplexes fertigt spürbar schneller, siehe Tab „Bebauung")" |
| Tooltip „Arbeitskraft pro paralleler Produktionseinheit" | „Wie viele Hände dieses Erzeugnis bindet – bei knapper Bevölkerung entsprechend langsamer" |
| Tooltip „Basiszeit pro Einheit" | „Fertigungsdauer je Einheit unter Idealbedingungen – Industrie, Bevölkerung und Spezialisierung verkürzen sie" |

### Ergänzt, weil bisher gar nicht erklärt

- **Bebauung**: „Jeder Ausbau kostet Credits und Baustoffe aus dem Kolonielager
  und belegt einen Bebauungsplatz. Plätze schafft allein die Infrastruktur –
  wie viele davon ein Himmelskörper insgesamt verträgt, hängt von seiner Größe
  ab und zählt über alle Kolonien darauf zusammen. Wer eng baut, verteuert
  damit auch die Ausbauten seiner Nachbarn."
- **Handel**: „… Grundnahrung, Grundmedizin und Unterhaltungselektronik kauft
  die Bevölkerung des Systems von selbst – das ist Ihre verlässlichste
  Einnahmequelle." (Der wichtigste Einnahmemechanismus war nirgends benannt.)
- **Statistiken**: Tooltips für „Gefährdete Kolonien" und „Geldmenge".
- **Bodentruppen**: Hinweis, dass nur eine ausreichend loyale Bevölkerung
  Soldaten stellt.
- **Bevölkerung**: „(die Infrastruktur zieht dafür laufend Stabilisiertes
  Elerium aus dem Lager)" und „(neue Credits entstehen nur, wenn die Kolonie
  über diesen Stand hinauswächst)".

### Nebenbefund: weitere eingefrorene Signalwerte

Im Zuge des Durchgangs fielen fünf Stellen derselben Art auf, wie sie zuvor
schon behoben wurden – hier allerdings mit den **Katalogen**, die nach dem
Verbindungsaufbau asynchron nachgeladen werden
(`this.send('productTypes')…then(...)`). Diese Komponenten lasen sie einmalig
im Feld-Initialisierer aus:

| Datei | Feld |
|---|---|
| `colony-detail.component.ts` | `buildingTypes`, `productTypes`, `groundUnitTypes` |
| `fleets-overview.component.ts` | `shipTypes` |
| `production-overview.component.ts` | `productTypes` |

Wird eine dieser Ansichten aufgebaut, bevor der Katalog eintrifft (Direktaufruf
per Link, Seiten-Neuladen), bleibt die Liste **dauerhaft leer** – im
Bebauungs-Tab hieße das: keine Gebäude zum Ausbauen. Behoben durch Umstellung
auf Getter, die bei jedem Zugriff frisch lesen.

## Verifikation

- `mvn clean test` (Backend), `tsc --noEmit` und `ng build` (Frontend): grün.
- Neue Query protokollseitig gegen den laufenden Server geprüft (beide Pfade,
  siehe Abschnitt A).
- **Nicht durchgeführt**: die Sichtprüfung im Browser. Die Chrome-Erweiterung
  war zum Zeitpunkt der Prüfung nicht verbunden ("Browser extension is not
  connected"), obwohl sie früher in dieser Sitzung funktioniert hatte. Die
  Grafik ist damit funktional (Daten, Einordnung, Build) belegt, aber nicht
  optisch abgenommen – das sollte beim nächsten Durchgang mit verbundener
  Erweiterung nachgeholt werden.
