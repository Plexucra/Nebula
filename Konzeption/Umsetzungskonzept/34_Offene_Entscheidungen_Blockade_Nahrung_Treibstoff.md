# 34. Offene Entscheidungen: Katalogstand, Blockade, Nahrung, Treibstoff

Dreizehn Entscheidungen, die der Nutzer am **9. September 2026** getroffen hat.
Sie räumen §J von Konzept 31 („Vorschläge – Entscheidungen, die nicht im Code
getroffen werden sollten") und die offenen Fragen aus §7 des Testergebnisses
vom selben Tag ab. Dieses Dokument hält je Punkt fest, **was entschieden
wurde**, **warum**, und **was daraufhin im Code passiert ist** – auch dort, wo
die Entscheidung „so lassen" lautete: eine bewusst nicht geänderte Regel ist
kein offener Punkt mehr und soll nicht ein drittes Mal auf dem Tisch landen.

| # | Thema | Entscheidung | Code |
|---|---|---|---|
| §J 1 | Katalogstand der Schiffe | Arbeitsstand gilt, Konzepte 27/28 nachziehen | §A |
| §J 1 | Kettentiefe/Bauzeiten | so lassen | §B |
| §J 2 | Parallele Fertigungsslots | sequentiell lassen, totes Feld raus | §C |
| §J 4 | Konsumpreise/Kaufkraft | so lassen | §D |
| §J 5 | Nahrung gegen Wachstum | Wachstum an die Versorgung koppeln | §E |
| §J 7 | Blockade-Durchflug | Orbit sperren, Anflieger in den Kampf zwingen | §F |
| §J 8 | Zivilverluste | mit der Garnisonsstärke skalieren, Tick-Deckel | §G |
| §J 9 | Kommandant ohne Kolonie | Weiterleben, Neugründung, Post an den Spieler | §H |
| §J 10 | Bebauungsplätze | so lassen | §I |
| F2/F3 | Angriffsbedingung | so lassen: Kampf setzt eine Blockade voraus | §F |
| F4/F5 | Infrastruktur-Obergrenze | Werte bleiben, aber vorher vorrechnen und warnen | §J |
| F7 | Treibstoff | Verbrauch an Masse und Distanz koppeln | §K |
| §J 11/F16 | Sitzung und Persistenz | erst die Sitzung, Persistenz später | §L |

## A. Der Katalogstand der Schiffe gilt

`shared/catalog/products.json` und `ships.json` trugen am 9.9.2026 eine
Neuvermessung aller Schiffe, die in **keinem** Konzept stand – erkennbar
gewollt (`TroopTransportTest` nennt „die Verkleinerung des Transporters auf ein
Fünftel eines Korvetten-Bauaufwands"), aber nirgends festgehalten. Sie gilt.
Konzept 27 §A und Konzept 28 §B sind entsprechend nachgezogen; hier steht der
Stand vollständig, weil er die Bezugsgröße für Trägerslots UND für den neuen
Treibstoffverbrauch (§K) ist:

| Schiff | Masse | Volumen | Korvettenmassen | Kette (Arbeitsstunden) | Fracht | Truppen |
|---|---:|---:|---:|---:|---:|---:|
| Korvette | 42 000 t | 190 909 m³ | 1,00 | 16,2 Mio. | – | – |
| Frachter | 9 451 t | 86 904 m³ | 0,23 | 2,0 Mio. | 28 354 t | – |
| Mannschaftstransporter | 9 803 t | 62 284 m³ | 0,23 | 4,2 Mio. | – | 27 |
| Zerstörer | 420 000 t | 1 615 385 m³ | 10,00 | 101,2 Mio. | – | – |
| Kreuzer | 4 200 000 t | 13 125 000 m³ | 100,00 | 1 058,9 Mio. | – | – |
| Kolonisationsschiff | 7 440 000 t | 47 181 818 m³ | 177,14 | 127,5 Mio. | – | – |
| Trägerschiff | 12 600 193 t | 90 001 382 m³ | 300,00 | 2 480,9 Mio. | 400 Slots | – |

**Was daran unverändert ist:** die Dichte je Klasse (Korvette 220, Frachter
110, Transporter 160, Zerstörer 260, Kreuzer 320, Träger 140 kg/m³) und das
Massenverhältnis der Kampfschiffe 1 : 10 : 100. Der Anker hat sich verschoben –
die Korvette ist nicht mehr eine Fregatte F125 (7 000 t), sondern das
Sechsfache davon –, die Skala selbst steht.

**Was sich verschoben hat, und was das kostet:**

- **Zivilschiffe sind klein geworden.** Frachter und Transporter kosten jetzt
  ein Achtel bzw. ein Viertel des Bauaufwands einer Korvette. Ein Frachter
  trägt 28 354 t statt 120 000 t.
- **Eine Landungsoperation braucht viele Transporter.** 27 Soldaten je Schiff
  heißt: 1 000 Soldaten fahren in **37** Transportern. In Bauaufwand sind das
  rund 9,6 Korvetten (vorher: 5), in Trägerslots 8,6. Die Zusammenlegung von
  Flotten (Konzept 33) ist damit keine Bequemlichkeit mehr, sondern die
  Voraussetzung, eine Landung überhaupt zu befehligen.
- **Das Trägerschiff ist das mit Abstand teuerste Schiff** – seine Kette kostet
  das 2,3-fache eines Kreuzers. Das ist konsistent mit „ein Träger ist ein
  Spielziel", war aber nirgends genannt.
- **Unverändert kaputt:** die Massenbilanz des Kolonisationsschiffs. Die Summe
  seiner Modulmassen ergibt 1,27 Milliarden Tonnen gegen 7,44 Mio. t am Schiff
  selbst. Der Bruch stand schon im committeten Stand (Konzept 27 §D nennt ihn
  als „abgeleitet") und ist hier **nicht** angefasst worden – er wirkt sich
  nirgends aus außer in der flachen Produktliste, treibt aber über §K jetzt den
  Sprungpreis dieses Schiffs.

## B. Die Kettentiefe bleibt, wie sie ist

Eine Korvette kostet bei Industrie 5 ohne Spezialisierung rund **460
Spielstunden = 19 Spieltage** in EINER Kolonie. Das bleibt so.

Die Zahl kommt nicht aus der Endmontage, sondern aus der Form des Katalogs:
eine Korvette ist **150 483 Fertigungsvorgänge**, von denen die Werft genau
einen sieht (240 von 205 380 Fertigungsstunden). Deshalb ändert die Werftstufe
an der Gesamtdauer 0,1 % – kein Fehler des Kettenplaners, sondern die Kette
selbst:

| Tier | Stück je Korvette | Fertigungsstunden | Arbeitsstunden |
|---|---:|---:|---:|
| 0 Rohstoffe | 56 531 | 46 272 | 5,65 Mio. |
| 1 Raffinate | 56 531 | 23 674 | 4,87 Mio. |
| 2 Werkstoffe | 22 079 | 23 771 | 2,80 Mio. |
| 3 Komponenten | 11 875 | 18 578 | 1,88 Mio. |
| 4 Baugruppen | 3 460 | 92 412 | 0,87 Mio. |
| 5 Module (6 Stück) | 6 | 432 | 0,11 Mio. |
| 6 Endmontage | 1 | **240** | 0,003 Mio. |

Zwei Bremsen wirken je Stück, und es zählt die schärfere
(`ChainPlanner.computeProductionHours`):

```text
Dauer/Stück = max( baseProductionHours / (Anlagenstufe × Spezialisierung × Fördergüte),
                   workHoursPerUnit / Einwohner )  /  productionSpeedMultiplier
```

Im frühen Spiel bremst die **Anlage** (bei Industrie 5: 454 der 460 Stunden),
im ausgebauten Spiel die **Bevölkerung** (bei Industrie 12 / Spezialisierung
50: 85 der 93 Stunden). Ein Schiff ist damit bewusst ein Vorhaben mehrerer
spezialisierter Kolonien, kein Bauauftrag einer einzelnen – genau das
Versprechen aus Konzept 12.

Für den Fall, dass die Entscheidung später doch fällt: eine Kürzung gehört
**nicht** in `productionSpeedMultiplier` (der teilt auch Konsumgüter, Soldaten
und Drohnen mit), sondern in einen eigenen Faktor auf `baseProductionHours` UND
`workHoursPerUnit` der Nicht-Endprodukte. Vom heutigen Stand aus träfe Teiler 8
das Ziel „Korvette ≈ 2 Spieltage bei Industrie 5", Teiler 4 „≈ 5 Spieltage".

## C. Die Warteschlangen bleiben sequentiell

`buildings.json` kannte ein Feld `productionSlotsPerLevel` (1 je Stufe), das
nie ausgewertet wurde. Es ist **ersatzlos entfernt** – aus dem Katalog, aus
`BuildingType` und aus `building.model.ts`. Eine Kolonie fertigt weiter genau
einen Auftrag zur Zeit, und die Verdrängung des Elerium-Dauerauftrags durch
lange Bauaufträge bleibt ein Spielproblem, das der Kommandant selbst lösen
muss – der Energiespeicher (Konzept 32) ist dafür das Werkzeug.

## D. Konsumpreise und Kaufkraft bleiben unverändert

Startpreis 450 Cr gegen rund 50 Cr Kaufkraft je Kopf, und ein Drittel des
Bevölkerungsbudgets, das mangels Elektronik-Angebot liegen bleibt: beides
bleibt. Begründung des Nutzers: die eigentliche Ursache ist, dass ein
Kommandant seine eigene Bevölkerung übervorteilen und ihre Kaufkraft absaugen
kann – das lässt sich ohne unerwünschte Nebenwirkungen kaum abstellen, und wer
seine Kolonie ausnimmt, trägt die Folgen selbst. **Kein Fehler, keine
Änderung.**

## E. Nahrung deckelt das Wachstum

Bisher hing das Wachstum allein am Lebensstandard – einem geglätteten Mittel
über alle drei Grundbedarfsgüter. Der reagiert so träge, dass eine Kolonie in
die Hungersnot hineinwuchs: Industrie 5 ernährt rund 6 000 Einwohner, die
Wohnkapazität ließ 20 000 zu, und der Einbruch kam erst, als der
Lebensstandard nachgezogen hatte.

Neu: **Liegt die Nahrungsdeckung unter 100 %, wächst die Kolonie nicht.**
Sofort, ungeglättet, unabhängig von Wohnraum und Lebensstandard
(`Formulas.FOOD_COVERAGE_FOR_GROWTH`, gemessen an
`state.consumptionCoverage` aus `EconomyTick.runConsumption` – derselben
Deckung, die auch den Lebensstandard speist, und im selben Tick erhoben).

Der Deckel **hält** die Bevölkerung, er tötet sie nicht: Schrumpfung bleibt
Sache des Lebensstandards. Damit läuft eine Kolonie an ihre Versorgungsgrenze
und bleibt dort stehen, statt zu überschießen und zusammenzubrechen. Sichtbar
ist das als eigener Zustand `PopulationGrowthState.FoodLimited`
(„Nahrungsgrenze erreicht") mit eigenem Hinweistext in der Kolonieansicht –
der Spieler soll den Grund des Stillstands lesen können, statt ihn zu raten.
Regressionstest: `FormulasTest.growthStopsWhileFoodIsShortEvenAtFullLivingStandard`.

## F. Die Blockade sperrt den Orbit

Im Backend sperrte eine Blockade bis hierher **nichts**; sie machte nur die
blockierende Flotte angreifbar. Jetzt gilt
(`BlockadeCommands.orbitBlockadeAgainst`):

- **Gesperrt ist der Orbit**, nicht das System. Ankunft am Gateway und der
  Systemraum bleiben frei – eine einzelne Flotte soll kein ganzes System
  zusperren können, und niemand soll unterwegs stranden.
- **Passieren darf, wer einen Friedens- oder Handelsvertrag** mit dem
  Blockierer hat, und natürlich der Blockierer selbst. Gesperrt ist damit auch
  der Eigentümer der belagerten Kolonie – das ist der Zweck einer Blockade.
- **Frei ist der Weg, solange die blockierende Flotte in einem Gefecht
  gebunden ist.** Wer kämpft, kontrolliert den Orbit gerade nicht.
- Die Sperre gilt für den Einflug (`moveFleetWithinSystem` auf Planeten- oder
  Kolonieorbit) **und für die Landung** (`LandingCommands.land`) – letzteres
  für den Fall, dass sich die Blockade erst gebildet hat, nachdem die Flotte
  im Orbit stand.

**Ohne Vertrag wird niemand abgewiesen, sondern in den Kampf gezwungen**
(Nutzerentscheidung): die Flotte fliegt ein und steht anschließend im Gefecht
mit der Blockade (`BlockadeCommands.breakThroughOrbitBlockade`).

**Wechselwirkung mit F2/F3.** Die Angriffsbedingung bleibt unverändert: ein
Gefecht setzt eine Blockade und den Kriegszustand voraus (bestätigt als „so
lassen"). Beides zusammen ergibt drei Fälle, und der dritte ist der einzige
Punkt, an dem eine Entscheidung interpretiert werden musste:

| Lage | Ergebnis |
|---|---|
| Vertrag vorhanden | freie Durchfahrt |
| Krieg, kein Vertrag | Einflug **und** Gefecht |
| weder Krieg noch Vertrag | Orbit bleibt zu, mit der Meldung: Passage nur mit Vertrag, freikämpfen nur nach Kriegserklärung |

Der dritte Fall stoppt die Flotte – aber er ist kein stiller Rückwurf: der
Befehl wird abgelehnt, bevor die Flotte sich bewegt (die Prüfung steht
bewusst VOR jeder Zustandsänderung), und die Meldung sagt, was fehlt. Ein
automatischer Kriegseintritt durch bloßes Anfliegen wäre die Alternative
gewesen und ist bewusst **nicht** gewählt: eine Kriegserklärung ist eine
Entscheidung des Kommandanten, kein Nebeneffekt einer Flottenbewegung.
Regressionstest: `FleetLifecycleTest.eineBlockadeSperrtDenOrbitUndZwingtDenAnfliegerInsGefecht`.

## G. Zivilverluste skalieren mit der Garnison

Bisher galt die Quote aus Mechanik/05 §2 („restlos aufgeriebene Verteidigung =
50 % Zivilverlust") unabhängig davon, was dort stand: der Fall einer
Startgarnison aus zehn leichten Drohnen kostete dieselbe halbe Stadt wie eine
ausgekämpfte Belagerung. Der Eroberer übernahm regelmäßig eine Ruine.

Neu rechnet `Formulas.civilianLossFraction` mit **zwei** Faktoren:

```text
Quote je Tick = min( 0,5 × (in diesem Tick zerschlagene Verteidigung / Ausgangsstärke)
                         × Garnisonsdichte,
                     CIVILIAN_LOSS_MAX_PER_TICK )
```

Die **Garnisonsdichte** ist die Ausgangsstärke der Verteidigung gemessen an
`Formulas.garrisonReferenceStrength(Bevölkerung)` – derselben Bezugsgröße, an
der auch die Sicherheit 100 % erreicht (5 % der Bevölkerung, mindestens 5).
Eine Garnison in voller Stärke kostet beim Fall weiterhin die 50 % aus §2, ein
Wachdienst über 2 000 Einwohnern praktisch nichts. Der **Tick-Deckel** (10 %)
verteilt selbst den Fall einer Festung über mehrere Runden, statt in einer
einzigen Runde die halbe Bevölkerung zu töten.

Der Materialschaden bleibt unverändert an die aufgelaufene Quote gekoppelt
(`conquestMaterialLossFraction`) – er fällt damit automatisch mit.
Regressionstest: `GroundBattleTest.zivilverlusteSkalierenMitDerGarnison`.

## H. Ein Kommandant ohne Kolonie bleibt im Spiel

Nach dem Fall der Heimatwelt zeigte `Player.homeworldColonyId` auf fremden
Besitz. Das war nicht nur ein toter Verweis: **Benachrichtigungen wurden über
diese Kolonie adressiert**, der Eroberer bekam also die Post des Verlierers.

Drei Änderungen:

1. **Benachrichtigungen kennen jetzt zwei Adressaten.**
   `GameNotification.colonyId` (gehört dem Eigentümer der Kolonie, wandert bei
   Eroberung mit) und neu `GameNotification.playerId` – für alles, was den
   Kommandanten selbst betrifft. `Notifications.notifyPlayer` ist der Weg
   dorthin; sämtliche Diplomatie-, Vertrags-, Gefechts- und
   Landungsabwehr-Meldungen sind umgestellt.
   `NotificationCommands.forPlayer` filtert entsprechend.
2. **Der Heimatverweis wird gelöst.** `ColonyConquest.releaseHomeworld` leert
   `homeworldColonyId` und meldet dem Verlierer den Verlust
   (`CODE_HOMEWORLD_LOST`, Meldung 508). Flotten und übrige Kolonien bleiben
   ihm.
3. **Die nächste Gründung ist der Neuanfang.** Gründet ein Kommandant ohne
   Heimatwelt eine Kolonie, wird sie seine neue Heimatwelt
   (`ColonyCommands.foundColony`).

**Nicht umgesetzt:** eine Eliminierung. Wer weder Kolonie noch Flotte hat, hat
nichts mehr zu befehligen, bleibt aber angemeldet – das Aufräumen erledigt
weiterhin die Inaktivitätslöschung (`RetentionCleanup`, 30 Realtage). Der
NPC-Bot erkennt beide Lagen bereits selbst (`HOME_MOVED`, `ELIMINATED`).
Regressionstest: `GroundBattleTest.derVerlustDerHeimatweltSchaltetDenKommandantenNichtAus`.

## I. Bebauungsplätze bleiben bei einem je Infrastrukturstufe

Werft und Akademie treiben eine Startkolonie damit auf Infrastruktur 8, mit 15
Energienetzbaugruppen und doppeltem Eleriumverbrauch, bevor das erste Schiff
gebaut ist. Das bleibt: der Ausbau IST der Preis für eine Werft, und die
Bauplatzknappheit ist die Weiche zwischen Wirtschafts- und Militärkolonie.
`slotsPerInfrastructureLevel` bleibt 1.

## J. Die Infrastruktur-Obergrenze wird vorgerechnet, nicht entschärft

`eleriumUpkeepLevelExponent` bleibt bei 1,25, der Verbrauch wächst also weiter
überlinear, und bei Infrastruktur 20 übersteigt der Gebäudeunterhalt sämtliche
Konsumeinnahmen. Der Ruin bleibt möglich – er soll nur keine Überraschung mehr
sein.

Die Ausbauvorschau der Kolonieansicht nennt deshalb neu:

```text
Elerium 0,047/h → 0,061/h nach dem Ausbau · Vorrat reicht dann 180 Spielstunden
— zu wenig für die vorgehaltene Reserve: ohne mehr Eleriumnachschub endet der Ausbau im Blackout
```

Gerechnet wird mit dem, was **tatsächlich** da ist (Lager plus Energiespeicher,
`ColonySpeedBreakdown.eleriumStock`), nicht mit einer geschätzten
Nachlieferung; die Warnschwelle ist die Standard-Reichweite des
Energiespeichers (`energyReserveDefaultGameHours`, 10 Spieltage). Neue Felder:
`ColonySpeedBreakdown.eleriumStock` und
`BuildingUpgradePreview.eleriumPerHourAfterUpgrade` (nur beim
Infrastrukturgebäude gesetzt).

## K. Treibstoff hängt an Masse und Distanz

Vorher: 0,01 Kapseln je **Schiff** und Sprung gegen einen Tank von 1 000
Kapseln je Schiff. Eine Tankfüllung reichte für **100 000 Sprünge** –
Treibstoff war keine Ressource, sondern eine Hürde beim allerersten Flug.

Neu:

```text
Verbrauch je Sprung = Σ (Schiffsmasse / Korvettenmasse) × jumpFuelPerCorvetteMassPerHop
Tank je Schiff      = Verbrauch je Sprung × jumpFuelTankRangeHops
```

- **Eine Kapsel je Korvettenmasse und Sprung.** Dieselbe Bezugsgröße, in der
  schon die Trägerslots rechnen (Konzept 27 §A: ein Slot ist eine
  Korvettenmasse). Eine Korvette kostet 1 Kapsel je Sprung, ein Frachter 0,23,
  ein Kreuzer 100, ein Kolonisationsschiff 177.
- **Die Distanz steckt in der Zahl der Sprünge** – beim Trägersprung in
  Referenzsprüngen, weiter mit dem `carrierTransitFuelFactor` von 5.
- **Der Tank fasst 50 Sprünge, für jede Flotte gleich.** Die Reichweite ist
  damit eine Konstante des Spiels und keine Funktion der Flottengröße; teuer
  wird die Größe beim **Betanken**, nicht beim Fliegen. 50 Sprünge sind auf die
  Galaxie mit 200 Systemen kalibriert: einmal quer hindurch und zurück, oder
  ein weiter Trägersprung.
- **Abgeleitet, nicht gepflegt:** `ShipTypeDef.jumpFuelPerHop` und
  `fuelTankCapacity` rechnet `ShipCatalog` beim Laden aus der Produktmasse aus.
  Zwei gepflegte Zahlen für dieselbe Aussage liefen sonst auseinander, sobald
  ein Schiff neu vermessen wird – und die Oberfläche rechnet nichts nach,
  sondern liest die Werte (Konzept 15, „EINE Regelquelle").

Folgen, die bewusst in Kauf genommen sind: eine Kolonisationsfahrt kostet jetzt
echten Treibstoff (177 Kapseln je Sprung), weshalb der Startvorrat von 10 auf
**600 Kapseln** angehoben ist – bemessen auf rund drei Sprünge mit
Kolonisationsschiff plus Begleitfrachter. Startflotten laufen mit **vollem
Tank** aus (eine feste Kapselzahl wäre für den Startfrachter über dem
Fassungsvermögen und für die Kampfflotte ein Tropfen). Und der NPC-Bot tankt
nicht mehr nach Schiffszahl, sondern gegen das Fassungsvermögen
(`Trade.topUpFuel`, `World.fleetTankCapacity`).

Wer den Druck erhöhen will, dreht an `jumpFuelTankRangeHops` (Reichweite) oder
`jumpFuelPerCorvetteMassPerHop` (Preis je Sprung), beide in
`shared/game-constants.json`.

## L. Die Sitzung überlebt das Neuladen, die Welt noch nicht

Der `localStorage`-Eintrag mit der Spieler-ID ist umgesetzt
(`websocket-game-api.service.ts`, `nebula_player_id`): Neuladen, zweiter Tab
und Deep-Links (Kampfbericht!) funktionieren. Das ist **keine
Authentifizierung** – Kennwortschutz bleibt ein eigenes Thema (F14).

**Persistenz des Weltzustands bleibt offen.** Ein Neustart des Backends
vernichtet weiterhin ein laufendes LAN-Spiel. Das ist ein eigenes Vorhaben mit
eigenem Konzept, kein Nebenpunkt dieser Runde.

## M. Was offen bleibt

Bewusst nicht entschieden und weiterhin offen:

- **Handelsgilde-Preisdrift** (Konzept 31 §J 6): driften Market-Maker-Preise je
  Spieltag zum Basispreis zurück, und soll die Gilde überhaupt eine Geldquelle
  sein?
- **Fehlende Benachrichtigungen** (F19): „Bauauftrag fertig", „Flotte
  angekommen", „Order ausverkauft", „Blackout", „Bevölkerung schrumpft" – die
  Codes existieren teils schon, die Auslöser fehlen.
- **Kennwortschutz und Reset-Knopf** (F14/F15).
- **Anzeigefragen** F8 (Spezialisierungsschwellen), F9 (politisches Gewicht),
  F11 (mehrere Kommandanten auf einem Planeten), F12 (doppelte Planetennamen).
- **Persistenz des Weltzustands** (§L).
