# 27. Schiffsgrößen, Massenskala und Review der Produktionsketten

## Ausgangslage

Nutzervorgabe: **die Korvette soll von Masse und Volumen her einer Fregatte der
Deutschen Marine entsprechen, der Zerstörer etwa zehnmal, der Kreuzer etwa
hundertmal so groß sein.** Dazu ein Review aller Produktionsketten auf
Skalierung und Plausibilität.

Der Altbestand war in keiner Achse konsistent. Eine Korvette wog 278,5 t – etwa
so viel wie ein Küstenwachboot, nicht wie ein Kriegsschiff –, und die drei
Kampfklassen unterschieden sich in sechs verschiedenen Verhältnissen:

| | Masse | Volumen | Kettenstunden | Rohmasse | Kampfwert | Trägerslots |
|---|---:|---:|---:|---:|---:|---:|
| Korvette | 1,00 | 1,00 | 1,00 | 1,00 | 1,00 | 1 |
| Zerstörer | 1,63 | 1,38 | 1,44 | 1,43 | 2,70 | 2 |
| Kreuzer | 2,33 | 1,60 | 1,91 | 1,83 | 8,00 | 4 |

Die entscheidende Zeile ist die vorletzte gegen die vorvorletzte: **ein Kreuzer
kostete das 1,9-fache einer Korvette und hatte den 8-fachen Kampfwert.** Das
sind 4,2-mal mehr Kampfkraft je investierter Produktionsstunde. Der
Konterkreis aus `Mechanik/03_...`, §2 arbeitet mit ×2 im Vorteil und ×0,5 im
Nachteil und ist kostensymmetrisch – er kann einen Effizienzvorsprung von 4,2
nicht ausgleichen. Korvetten und Zerstörer waren damit strikt dominiert, und
das zentrale taktische Element des Spiels war wirkungslos.

Ursache ist die Definition des militärischen Werts. `Formulas.productionAspect`
ist `workHoursPerUnit × baseProductionHours` und beschreibt damit **nur die
Endmontage**. Die tatsächlichen Kosten stecken in der Unterkette, und die war
für alle drei Klassen nahezu gleich groß, weil sich die Schiffe nur in wenigen
Einzelstücken je Modul unterschieden.

## A. Die neue Massenskala

> **Nachgezogen am 9.9.2026 (Umsetzungskonzept/34, §A).** Die Skala ist seither
> einmal verschoben worden: die Kampfschiffe wurden gemeinsam versechsfacht,
> die Zivilschiffe nach Bauaufwand neu bemessen. Die zweite Tabelle unten ist
> der **geltende** Stand; die erste dokumentiert, wovon aus verschoben wurde.
> Unverändert geblieben sind dabei die Dichten je Klasse und das
> Massenverhältnis der Kampfschiffe 1 : 10 : 100 – nur der Anker ist nicht mehr
> die F125.

Anker war die Fregatte F125 *Baden-Württemberg* mit rund 7 200 t
Einsatzverdrängung. Von dort skalieren Zerstörer und Kreuzer nach Vorgabe mit
Faktor 10 und 100; die übrigen vier Schiffe sind rollengerecht in dieselbe
Skala eingeordnet.

| Schiff | Masse | Volumen | Dichte | Vergleich |
|---|---:|---:|---:|---|
| Korvette | 7 000 t | 31 818 m³ | 220 kg/m³ | Fregatte F125 |
| Frachter | 40 000 t | 363 636 m³ | 110 kg/m³ | Ladung 120 000 t |
| Mannschaftstransporter | 60 000 t | 375 000 m³ | 160 kg/m³ | |
| Zerstörer | 70 000 t | 269 231 m³ | 260 kg/m³ | 10 × Korvette |
| Trägerschiff | 250 000 t | 1 785 714 m³ | 140 kg/m³ | nimmt Kampfschiffe auf |
| Kreuzer | 700 000 t | 2 187 500 m³ | 320 kg/m³ | 100 × Korvette |
| Kolonisationsschiff | 1 240 000 t | 7 863 636 m³ | 158 kg/m³ | abgeleitet, siehe §D |

**Geltender Stand seit dem 9.9.2026** (`shared/catalog/products.json`;
Herleitung und Folgen in Umsetzungskonzept/34, §A):

| Schiff | Masse | Volumen | Dichte | Korvettenmassen |
|---|---:|---:|---:|---:|
| Mannschaftstransporter | 9 803 t | 62 284 m³ | 160 kg/m³ | 0,23 |
| Frachter | 9 451 t | 86 904 m³ | 110 kg/m³ | 0,23 |
| Korvette | 42 000 t | 190 909 m³ | 220 kg/m³ | 1,00 |
| Zerstörer | 420 000 t | 1 615 385 m³ | 260 kg/m³ | 10,00 |
| Kreuzer | 4 200 000 t | 13 125 000 m³ | 320 kg/m³ | 100,00 |
| Kolonisationsschiff | 7 440 000 t | 47 181 818 m³ | 158 kg/m³ | 177,14 |
| Trägerschiff | 12 600 193 t | 90 001 382 m³ | 140 kg/m³ | 300,00 |

Die Korvettenmasse ist damit die Recheneinheit gleich zweier Systeme: ein
Trägerslot (siehe §C) **und** eine Eleriumkapsel je Sprung
(Umsetzungskonzept/34, §K).

**Die Dichte je Klasse bleibt unverändert.** Sie war im Altkatalog bereits auf
glatte Werte gesetzt (Korvette 220, Zerstörer 260, Kreuzer 320, Frachter 110,
Träger 140, Transporter 160 kg/m³) und bildet Hohlräume, Leitungswege und
Wartungsraum ab. Das Volumen skaliert deshalb exakt mit der Masse, und die
Spalte „Masse-Volumen-Verhältnis" der flachen Produktliste geht wieder auf.
Für die Korvette bedeutet das 31 818 m³ – bei 150 m Länge und 19 m Breite
entspricht das rund 11 m mittlerer Bauhöhe, also einem Schiff mit Aufbauten.

## B. Wie die Masse zustande kommt: Stückzahlen statt schwererer Bauteile

Die Massenbilanz aus `Nebula_Flache_Produktliste_...md`, §1.3 bleibt
unangetastet:

```text
Modulmasse   = 1,08 × Summe der Unterprodukte     (Integrationsstruktur)
Schiffsmasse ≥ 1,03 × Summe der Module            (Endmontage)
```

Der einzige Weg, ein 7 000-t-Schiff aus einer 45-t-Strukturzelle zu bauen, ist
deshalb: **mehr Zellen, nicht schwerere.** Die Rezeptmengen der Schiffsmodule
wurden entsprechend hochskaliert – Korvette ≈ ×25, Zerstörer ≈ ×154, Kreuzer
≈ ×1 081. Die Massen aller Baugruppen, Komponenten, Werkstoffe und Rohstoffe
sind **unverändert**; nur die Stückzahlen in den 36 Schiffsmodulrezepten
wachsen.

```text
Korvettenrumpfmodul   25 × Strukturzelle, 25 × Panzerbaugruppe, 25 × Besatzungsbaugruppe
Zerstörerrumpfmodul  308 × Strukturzelle, 308 × Panzerbaugruppe, 152 × Besatzungsbaugruppe
Kreuzerrumpfmodul   3242 × Strukturzelle, 3242 × Panzerbaugruppe, 1083 × Strahlenschutzsegment
```

Das ist zugleich die plausiblere Lesart: eine Fregatte besteht aus vielen
Sektionen, nicht aus drei. Der Integrationszuschlag der Module trifft danach
exakt 8,00 %, der Endmontagezuschlag der Schiffe liegt zwischen 3,00 % und
3,30 % – die Schiffsmassen sind auf runde Zielwerte gesetzt, die Stückzahlen
als ganze Zahlen darunter gewählt.

## C. Kosten und Kampfwert folgen der Größe

`workHoursPerUnit` (Arbeitskräfte) und `baseProductionHours` (Bauzeit) sind so
gesetzt, dass der Arbeitsaufwand der drei Kampfklassen dasselbe Verhältnis
1 : 10 : 100 trägt wie die Masse. Die Bauzeit wächst dabei bewusst
unterlinear, die Arbeiterzahl überlinear – ein größeres Schiff braucht vor
allem mehr Hände, nicht vor allem mehr Kalenderzeit.

| Schiff | Arbeitskräfte | Bauzeit h | Arbeitsaufwand Ah | Ah je Tonne |
|---|---:|---:|---:|---:|
| Korvette | 2 500 | 240 | 600 000 | 85,7 |
| Zerstörer | 12 500 | 480 | 6 000 000 | 85,7 |
| Kreuzer | 50 000 | 1 200 | 60 000 000 | 85,7 |
| Frachter | 4 000 | 400 | 1 600 000 | 40,0 |
| Mannschaftstransporter | 7 500 | 480 | 3 600 000 | 60,0 |
| Trägerschiff | 24 000 | 900 | 21 600 000 | 86,4 |

Die Aufwandsdichte in der letzten Spalte ist der rollenabhängige Teil: ein
Frachter ist ein einfacher Rumpf mit viel Hohlraum (40 Ah/t), ein
Mannschaftstransporter gepanzert, aber unbewaffnet (60 Ah/t), Kampfschiffe und
das Trägerschiff mit Hangar und Sprungantrieb liegen bei rund 86 Ah/t.

Die Modulrezepte tragen ihren Anteil mit: `workHoursPerUnit` jedes
Schiffsmoduls wächst mit demselben Faktor wie seine Stückliste, die Bauzeit je
Modul bleibt gleich. Ein 25-mal größeres Modul entsteht also in derselben Zeit,
gebunden von 25-mal so vielen Arbeitern.

**Ergebnis.** Kosten und Kampfkraft sind wieder gekoppelt:

| | Masse | Kampfwert | Kettenstunden | Rohmasse | Kampfwert je Kettenstunde |
|---|---:|---:|---:|---:|---:|
| Korvette | 1,0 | 1,0 | 1,0 | 1,0 | 1,00 |
| Zerstörer | 10,0 | 10,0 | 7,5 | 7,5 | 1,33 |
| Kreuzer | 100,0 | 100,0 | 70,9 | 70,0 | 1,41 |

Der Effizienzvorsprung des Kreuzers ist von 4,18 auf 1,41 gefallen. Damit
entscheidet wieder der Konterkreis (×2 gegen ×0,5, also Faktor 4 zwischen
Vorteil und Nachteil) und nicht die Bauklasse. Der Restvorsprung von 1,41
stammt aus den unterschiedlichen Modulzusammensetzungen und ist gewollt: ein
Kreuzer soll sich lohnen, wenn er nicht auf seinen Konter trifft.

## D. Kolonisationsschiff

`24_Kolonisationsschiffe_und_Startbevoelkerung.md`, §B definiert die Kosten als
„wertgleich mit 1 Frachter + 20 Mannschaftstransportern, plus Baustoffe für
Infrastruktur 1 und Wohnkomplex 1", vollständig bis auf Tier-0-Rohstoffe
expandiert. Diese Regel ist unverändert angewandt worden – mit den neuen
Schiffsgrößen als Eingang.

- Masse und Volumen folgen derselben Summe: 40 000 t + 20 × 60 000 t =
  **1 240 000 t**, entsprechend 7 863 636 m³.
- Das Rohstoffrezept wächst von 20 298 auf **1 275 076 Einheiten** über 19
  Positionen.
- `workHoursPerUnit` steigt von 6 000 auf **9 500**. Damit liegt der Kampfwert
  bei 1 596 000 und weiterhin auf Frachterniveau – die dortige Vorgabe „in der
  Größenordnung eines Frachters, und damit unauffällig" bleibt erfüllt.
- Die feste Bauzeit von 168 Spielstunden (`COLONY_SHIP_BUILD_HOURS`) ist nicht
  angefasst.

**Das ist der teuerste Einzelposten dieser Änderung und der wahrscheinlichste
Kandidat für eine Nachkalibrierung** – siehe §F.

## E. Nebenbefunde, die mit erledigt wurden

**Atmosphäre ist wieder verschiffbar.** `Atmosphärenfluid` (5 000 t je Charge)
lag bei 30 kg/m³ und belegte damit 166 667 m³, `Getrennte Atmosphärengase` bei
80 kg/m³ und 25 000 m³. Beide passten als **einzelnes Stück** in keinen
Frachter, und seit der Umstellung auf ganze Stückzahlen
(`25_Ganze_Stueckzahlen_und_Uebertragskonten.md`) damit überhaupt nicht mehr.
Eine Kolonie ohne eigenes Atmosphärenvorkommen konnte Atemgas nie importieren.
Beide werden jetzt **verflüssigt** gelagert und transportiert (800 kg/m³, in
der Größenordnung von Flüssigluft mit 870 kg/m³): 6 250 m³ und 2 500 m³. Die
Spalte Volumen ist laut §1.1 der flachen Produktliste ohnehin „das belegte
Transport- oder Lagervolumen" – Chargengröße und Wirtschaftskreislauf bleiben
unberührt.

**Trägerslots folgen der Masse.** `ShipTypeDef.carrierSlotUsage` stand auf
1 / 2 / 4 und damit quer zu jeder anderen Achse; jetzt 1 / 10 / 100, Frachter
6, Mannschaftstransporter 9, Kolonisationsschiff 177. Dasselbe Verhältnis
übernimmt `npc-bot/Catalog.SHIP_MILITARY_WEIGHT`, das laut eigenem Kommentar
1:1 aus diesem Feld stammt.

**Frachterladung.** 2 000 t / 3 000 m³ waren auf ein 564-t-Schiff bezogen;
jetzt 120 000 t / 250 000 m³ auf ein 40 000-t-Schiff. Die implizite Dichtegrenze
sinkt von 667 auf 480 kg/m³, wodurch auch sperrige Güter mitfahren.

## F. Offene Punkte

**1. Der Materialsockel der Kette ist unverändert – und jetzt sichtbar.**
Eine Korvette verbraucht **2 518 kg Rohmasse je Kilogramm Schiff**. Dieses
Verhältnis ist nicht neu (es lag vorher bei 1 544) und stammt aus den
Chargengrößen: Rohstoffe kommen in Losen von 10⁵ bis 10⁶ kg, Komponenten in
Losen von 10² bis 10⁴ kg. Jedes Rezept, das diese Grenze quert, verbrennt eine
volle Großcharge:

```text
1 Halbleiterwafer (100 kg)  ⟵  5 000 kg Halbleiterrohstoff
                             + 100 000 kg Industriechemikalien
                             +  30 000 kg Edelgasfraktion
```

Weil die Schiffe jetzt echte Größe haben, multipliziert sich dieser Sockel mit.
Bei Industriekomplex 8 und Spezialisierung 10 (Tempofaktor 16) und
`REAL_MS_PER_GAME_HOUR = 2500`:

| Schiff | Kette vorher | Kette jetzt | Realzeit vorher | Realzeit jetzt |
|---|---:|---:|---:|---:|
| Korvette | 11 306 h | 268 965 h | 0,5 h | 11,7 h |
| Zerstörer | 14 232 h | 2 019 076 h | 0,6 h | 3,7 Tage |
| Kreuzer | 19 317 h | 19 078 313 h | 0,8 h | 34,5 Tage |
| Kolonisationsschiff | 20 588 h | 1 275 360 h | 0,9 h | 2,3 Tage |

Die Kettenstunden sind hier topologisch korrekt aufgelöst; `planChain` selbst
meldet zurzeit 35 bis 40 Prozent weniger (Punkt 2).

Das ist für eine LAN-Sitzung zu viel. **Vor der nächsten Spielsitzung sollten
die Chargengrößen der Ebenen 2 bis 5 aneinander angeglichen werden** – das ist
der einzige Hebel, der die absoluten Kosten senkt, ohne die hier hergestellten
Verhältnisse 1 : 10 : 100 anzutasten. Alternativ ließen sich die Zielmassen
insgesamt eine Größenordnung tiefer ansetzen; die Fregatte als Anker wäre dann
allerdings aufgegeben.

**2. `ChainPlanner.planChain` zählt die Kette um 35 bis 40 Prozent zu niedrig.**
Unabhängig von dieser Änderung: `planChain` sortiert die erreichbaren Produkte
allein nach `tier` absteigend und läuft die Liste genau einmal durch. Es gibt
aber **46 Rezeptkanten innerhalb derselben Ebene** (`p_navsystem` ← `p_sensorfeld`,
`p_gefechtsleitsystem` ← `p_kommfeld`, `p_strahlenwaffe` ← `p_energieverteiler`
und weitere). Steht der Verbraucher in der Liste hinter seinem Vorprodukt, wird
dessen Bedarf nicht mehr aufgeschlagen. Gemessen fehlen bei der Korvette 35,0 %,
beim Kreuzer 39,6 %, beim Frachter 35,7 % der Kettenstunden und des Materials.
Die Reihenfolge muss topologisch sortiert werden, nicht nach Ebene.

**3. Masse hat für Kampfschiffe weiterhin keine Spielwirkung.** `massKg` und
`volumeM3` werden ausschließlich in `FleetCommands` für die Frachtkapazität
gelesen. Der Sprungtreibstoff ist `JUMP_FUEL_PER_SHIP_PER_HOP` – **je Schiff,
nicht je Masse**: ein 700 000-t-Kreuzer springt so teuer wie eine Korvette.
`carrierSlotUsage` wird in keiner Backend-Regel ausgewertet.

**4. Die größten Module passen in keinen Frachter.** Kreuzerrumpf- (297 653 t),
Kreuzerantriebs-, Kreuzerversorgungs-, Trägerrumpf- und Trägerhangarmodul
überschreiten jede Frachterladung als Einzelstück. Kreuzerbau ist damit
faktisch vertikal integriert – die Arbeitsteilung aus
`Spieldesign/01_Produktion_und_Arbeitsteilung.md` endet bei der Zerstörerklasse.

**5. Weitere Massenbilanz-Brüche, hier nicht angefasst.** Die Eleriumkapsel
entsteht mit 500 kg aus 0,0006 kg Eingang, weil im JSON-Rezept gegenüber der
flachen Produktliste die Massenträger (Hochtemperaturlegierung,
Strahlenschutzwerkstoff) und die Zwischenstufe `Eleriumkonzentrat` fehlen. Ein
Soldat wiegt 25,6 t bei 3 t Ausrüstung. Bodendrohnen kosten das 20-fache an
Transportslots für das 1,36-fache an Kampfwert.

**6. `workHoursPerUnit` trägt die falsche Spalte.**
`19_Arbeitskraefte_...md` definiert das Feld als „Arbeitsstunden je Stück", die
Katalogwerte stammen aber aus der Spalte **Arbeitskräfte** der flachen
Produktliste. Eine Korvette bindet deshalb `2 500 / 240 = 10,4` Arbeiter statt
2 500 – die Arbeitskraft-Bremse greift um den Faktor `baseProductionHours` zu
schwach, und zwar je Produkt unterschiedlich. Für den Kampfwert ist das Produkt
beider Felder zufällig richtig, für die Bremse nicht.
