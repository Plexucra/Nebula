# TODO

## Offen

- [ ] **Handelsgilde-Preisdrift** (Konzept 31 §J 6): 5er-Lose, ±10 % je
  Ausführung ohne Rückkehr, Konsumgüter zu 1,4 % des lokalen Preises. Offen:
  Driftrate zum Basispreis je Spieltag – und ob die Gilde überhaupt eine
  Geldquelle sein soll (Konzept 22 sagt: kleine Lose).
- [ ] **Kennwortschutz und Reset-Knopf** (F14/F15): die Anmeldung ist
  kennwortlos, der Reset-Knopf für jeden sichtbar. Für einen LAN-Abend mit
  mehreren Personen riskant. Eigenes Thema, bewusst nicht Teil von Konzept 34.
- [ ] **Persistenz des Weltzustands** (Konzept 31 §J 11): ein Neustart des
  Backends vernichtet ein laufendes LAN-Spiel. Eigenes Vorhaben mit eigenem
  Konzept; die Sitzung selbst übersteht das Neuladen inzwischen
  (Konzept 34 §L). Vorarbeit seit 10.9.2026: Spieluhr mit Versatz (`Clock`,
  `-Dnebula.game-clock.start`), Ereigniswarteschlange als reine Daten
  (`ScheduledEvent`). Empfohlener Weg: Schnappschuss des ganzen `GameState`
  als JSON (atomar, alle 30–60 s und beim Herunterfahren) plus `IdGenerator`-
  Zähler, Spieluhr und Ereignisse; danach optional Kommando-Journal. Vorher
  `Math.random` in `LandingCommands` durch einen Generator im Zustand ersetzen.
  Keine relationale Datenbank für den heißen Spielzustand (siehe Sitzung
  10.9.2026: Zeilen-Updates je Sekunde in jedem Wallet/Lager).
- [ ] **Startpreis der Konsumgüter wächst nicht mit der Startbevölkerung**
  (Gesamttest 9.9.2026, B1): `WorldSeed.STARTER_SELL_ORDER_PRICE` = 450 Cr ist
  für rund 200 Einwohner hergeleitet, die Kolonie startet aber mit 2000. Ein
  frischer Kommandant fällt deshalb sofort auf Lebensstandard 27 % und verliert
  Bevölkerung, bis er den Preis selbst senkt (mit 60 Cr: Deckung 100 %,
  Lebensstandard 50 %, Wachstum). Bewusst NICHT geändert – Konzept 34 §D hat
  die Konsumpreise entschieden („Kaufkraft-Lücke ist Sache des Spielers");
  wenn der Startpreis mitwachsen soll, gehört er an `START_POPULATION`
  gekoppelt. Der Hinweistext im Bevölkerungs-Tab nennt den Hebel inzwischen.
- [ ] **Anzeigefragen aus dem Testergebnis**: F8 (Spezialisierungsschwellen
  stehen nirgends), F9 (politisches Gewicht – woraus berechnet, wofür gut?),
  F11 (mehrere Kommandanten auf einem Planeten sind gewollt, aber in der
  Systemansicht nicht erkennbar), F12 (doppelte Planetennamen).

## Erledigt

- [x] ~~Tick-Schleife durch Ereignisplaner ersetzen, Spieluhr mit Versatz~~ –
  10.9.2026. `GameTick` arbeitet nur noch fällige Ereignisse ab
  (`GameEvents`): zwölf Fälligkeiten je Objekt, zwei Reaktionen (Orders
  nachfüllen, Sieg prüfen), vier wiederkehrende Aufgaben, der Wirtschaftsschritt
  als ein Block. `Clock.now()` ist eine Spieluhr; jede `ServerMessage` trägt
  `gameNow`, Oberfläche (`UiClockService`) und Bots rechnen damit. Geprüft mit
  160 Tests, e2e-Durchlauf und Browser gegen eine Instanz mit 8 h Versatz.

- [x] ~~Code-Review Gesamtprojekt~~ – 10.9.2026. Frontend pollt nur noch
  gelesene Signale (vorher hunderte Anfragen je Sekunde für zerstörte
  Ansichten), Routenvorschau als Sammelabfrage, Katalog-Indizes statt Streams,
  vollständiger Reset, Teilstring-Fehler beim Aufräumen der Übertragskonten,
  dreifach kopierte Abbruch-Gutschrift vereinigt, WebSocket-Port folgt der
  aufgerufenen Adresse.

- [x] ~~Gesamttest Oberfläche + Backend + NPCs, Siegbedingung~~ – durchgeführt am
  9.9.2026, siehe
  `Konzeption/Testergebnis_2026-09-09_Gesamttest_Oberflaeche_Backend_NPC.md`.
  Kurz: sechs Fehler behoben (globale Benachrichtigungen fremder Flotten,
  Sitzungsübernahme eines fremden Kommandanten nach dem Neuladen, rechtsbündiger
  Nachrichten-Kopf, Tankanzeige, „Infrastruktur"-Kachel = Wohnraum, unbrauchbare
  Einschiffungsmeldung); **Siegbedingung implementiert** (`VictoryCommands`,
  Band in der Oberfläche, `VictoryTest`); NPC-Landungsoperation und
  NPC-Kolonisierung waren tot und laufen jetzt (falsche Transporter-Kapazität,
  Treibstoff nach Fahrt statt Festwert, Preispolitik, Zielaufklärung über alle
  Systeme, größere Ausbildungslose, mehr Invasoren je Lager); Bots verbinden
  sich nachweislich nur mit dem Spielserver im eigenen Netz.

- [x] ~~Fehlende Benachrichtigungen (Testergebnis F19)~~ – beim Gesamttest
  9.9.2026 nachgeprüft: jeder Code in `Notifications` hat inzwischen einen
  Auslöser (Bauauftrag, Schiff, Flottenankunft, Order ausverkauft, Blackout und
  Wiederkehr, Bevölkerung schrumpft, Guthaben leer, Kolonie gegründet,
  Versorgungslücke, Landung abgefangen). Die Flottenankunft ging dabei bis
  zuletzt an ALLE Kommandanten – das ist mit demselben Test behoben.

- [x] ~~Dreizehn offene Entscheidungen aus Konzept 31 §J und Testergebnis §7~~ –
  entschieden am 9.9.2026 und umgesetzt, siehe
  `Konzeption/Umsetzungskonzept/34_Offene_Entscheidungen_Blockade_Nahrung_Treibstoff.md`
  (Entscheidung, Begründung und Umsetzungsstand je Punkt). Kurz:
  **Katalogstand der Schiffe gilt** (Konzepte 27 §A und 28 §B nachgezogen),
  **Kettentiefe bleibt** (19 Spieltage je Korvette bei Industrie 5 – Schiffe
  sind ein Vorhaben mehrerer Kolonien), **Warteschlangen bleiben sequentiell**
  (totes Feld `productionSlotsPerLevel` entfernt), **Konsumpreise bleiben**
  (Kaufkraft-Lücke ist Sache des Spielers), **Nahrung deckelt das Wachstum**
  (`PopulationGrowthState.FoodLimited`), **Blockade sperrt den Orbit und zwingt
  Anflieger in den Kampf**, **Zivilverluste skalieren mit der Garnisonsstärke**
  (plus Tick-Deckel), **Heimatverlust schaltet den Kommandanten nicht aus**
  (Benachrichtigungen adressieren jetzt den Spieler statt eine Kolonie),
  **Bebauungsplätze bleiben bei 1 je Stufe**, **Infrastruktur-Ausbau rechnet
  seinen Eleriumverbrauch vorher vor**, **Treibstoff hängt an Masse und
  Distanz** (Tank = 50 Sprünge für jede Flotte).

- [x] ~~Oberflächen-Testlauf 9.9.2026: 7 kritische, 11 hohe und 19 mittlere Befunde~~ –
  behoben, siehe `Konzeption/Testergebnis_2026-09-09_End-to-End_Oberflaeche.md`
  (das Dokument enthält den vollständigen Befund UND den Umsetzungsstand je
  Punkt). Kernpunkte: Aufbewahrungsfristen und Inaktivitäts-Löschung rechnen
  jetzt als EINZIGE Zeitangaben in Realzeit (überall mit `REALZEIT-AUSNAHME`
  markiert), der Trägersprung ohne Gateway ist implementiert
  (`CarrierTransitTest`), laufende Gefechte stehen rot in der Kopfzeile, der
  Kampfbericht ist aus dem Kampfprotokoll verlinkt, fehlende Baustoffe lassen
  sich als EIN Bündelauftrag einreihen, und der Gebäudeunterhalt steht am
  Ausbau. Die Entwurfsfragen aus §7 sind mit Konzept 34 abgeräumt, soweit sie
  entschieden wurden; der Rest steht oben unter „Offen".

- [x] ~~Balancing: Produktionsbäume der Schiffe (Konzept 31 §I/§J, Vorschlag 1)~~ –
  **entschieden: so lassen** (Konzept 34 §B). Die Kette einer Korvette bleibt
  bei 19 Spieltagen (Industrie 5, eine Kolonie); die Zahl steckt in 150 483
  Fertigungsvorgängen der Tier 0–4, nicht in der Endmontage. Wenn die
  Entscheidung später doch fällt, gehört der Faktor auf die Nicht-Endprodukte,
  nicht in `productionSpeedMultiplier` – Rechnung in Konzept 34 §B.

- [x] ~~Balancing: Elerium-Startreserve passt nicht mehr zur Startbebauung~~ –
  entschärft durch den **Energiespeicher** (Umsetzungskonzept/32): eintreffendes
  Elerium füllt zuerst eine für Ketten unsichtbare Vorhaltemenge (automatisch 10
  Tage Verbrauch der aktuellen Infrastrukturstufe), die Infrastruktur zieht zuerst
  daraus. Seit Konzept 34 §J rechnet die Ausbauvorschau zusätzlich vor, wie lange
  der Vorrat NACH dem Ausbau noch reicht, und warnt, bevor der Blackout kommt.

- [x] ~~Baustoffe verbrauchen einander als Vorprodukt – Reihenfolge ist eine Falle~~ –
  behoben, und zwar an der Ursache, nicht nur mit einer Einreihungsreihenfolge:
  `p_leiterbuendel` enthält `p_leitermetall` (1:1). Zwei GETRENNTE
  `queueProduction`-Aufträge für beide (wie sie ein Bauauftrag direkt zugleich
  braucht, z. B. `b_infrastructure` ab Stufe 4) konnten nie beide Sollmengen
  gleichzeitig im Lager haben, unabhängig von der Reihenfolge: der zweite
  Auftrag verbraucht per `autoProduceMissing` immer die Menge, die der erste
  gerade erst eingelagert hat. `ChainPlanner.planChain` kannte bislang nur EIN
  Wurzelprodukt je Aufruf. Jetzt gibt es eine Mehrfach-Wurzel-Variante
  (`planChain(state, colonyId, Map<String,Double> demand, facilityTypeId)`,
  `ChainPlanStep.isRoot`): mehrere direkt angeforderte Produkte werden als EIN
  Auftrag geplant, ihr gemeinsamer Bedarf (z. B. Leitermetall: 13 direkt + 13
  als Zutat des Leiterbündels = 26) wird in einem Rutsch produziert, aber bei
  Fertigstellung landet exakt die angeforderte Menge jedes Wurzelprodukts im
  Lager (`ProductionQueueEntry.bundledProducts`,
  `ProductionCommands.queueProductionBundle`, WS-Befehl
  `queueProductionBundle`). `Bot.queueMissingMaterials` (npc-bot) reiht die
  komplette Fehlliste jetzt als einen Bündelauftrag ein statt als mehrere
  Einzelaufträge; der e2e-Test tut für die Infrastruktur-Baustoffe dasselbe
  (die frühere Tier-absteigend-Reihenfolge war nur ein Workaround, kein Fix,
  und ist entfernt). Regressionstest: `ProductionBundleTest`.

- [x] ~~Kolonisieren war in der Oberfläche auf das Heimatsystem beschränkt~~ –
  behoben. Der „Kolonisieren"-Knopf sitzt jetzt zusätzlich an jedem
  unbesiedelten Planeten der **Systemansicht** (`system-view.component`), also
  in JEDEM erreichten System. Er erscheint genau dann, wenn eine eigene,
  stationierte Flotte mit Kolonisationsschiff im Orbit dieses Planeten liegt –
  dieselbe Bedingung wie `ColonyCommands.fleetWithColonyShipAt`; welche
  Produkte Kolonisationsschiffe sind, kommt aus `ShipTypeDef.class`
  (`'ColonyShip'` fehlte im TS-Typ und ist ergänzt), nicht aus einer zweiten
  Produktliste im Client. Liegt kein Schiff im Orbit, steht dort der Grund;
  läuft bereits eine Gründung, deren Restzeit. Die Kolonienliste behält ihre
  Heimatsystem-Sicht, nennt das System aber jetzt beim Namen (statt hart
  „Aurelia-System") und verweist für alles Weitere auf die Galaxiekarte.
  Der Weg dorthin (Werft → Lager → Flotte → Betanken → Gateway-Sprung → Orbit →
  Gründung) ist über die echten Befehle abgesichert: `ColonizationJourneyTest`.

- [x] ~~Nach der Landung blieb eine leere Geisterflotte stehen~~ – behoben.
  `colonizePlanet` verbucht das Schiff jetzt über
  `FleetCommands.consumeShips`: leergelaufene Schiffsgruppen verschwinden, und
  mit dem letzten Schiff verschwindet die Flotte selbst (samt ihrer Blockade,
  wie beim Ortswechsel). Eine gemischte Flotte verliert dagegen nur das
  Kolonisationsschiff und behält Schiffe wie Tankinhalt – beides ist in
  `ColonizationJourneyTest` und `ColonyCommandsUsabilityTest` festgehalten.

- [x] ~~Mehrere Kommandanten auf demselben Planeten~~ – **so gewollt**
  (Nutzerentscheidung). `colonizePlanet` prüft `alreadyOwned`/`alreadyRunning`
  bewusst nur gegen die eigenen Kolonien; zwei Kommandanten dürfen denselben
  Planeten besiedeln. Nicht ändern.

- [x] ~~Frisch gegründete Kolonie startet ohne Vorräte~~ – **kein Fehler**
  (Nutzerentscheidung): die Rohstoffe bringt der Kommandant selbst mit. Zu
  beachten ist dabei nur, dass das Kolonisationsschiff selbst keinen Frachtraum
  hat (`cargoMassKg = 0`, Umsetzungskonzept/24_...md, §F) – die Erstversorgung
  (vor allem Elerium für die Infrastruktur 2, sonst Blackout ab dem ersten
  Tick) muss also ein Frachter mitfliegen oder unmittelbar nachliefern.

- [x] ~~e2e (`backend/e2e/full-playthrough.mjs`) schlägt beim ersten Kampf-Tick fehl~~ –
  behoben. Die ursprüngliche Vermutung („Determinismus-Mismatch in
  `BattleCommands.java`") war falsch: die Datei war in Ordnung. Das Testskript
  rechnete mit `product.baseWorkforceRequired`, einem beim Workforce-Redesign in
  `workHoursPerUnit` umbenannten Feld. Ergebnis war `NaN`, das in
  `computeSideDamage`/`applyDamage` still als „keine Verluste" (`{}`) durchfiel.
  `shipMilitaryValue` prüft den Wert jetzt hart, damit ein künftiger Feldumbau
  laut scheitert statt still falsche Erwartungswerte zu liefern.
  Zusätzlich waren weitere Erwartungen des Skripts veraltet (Grundmedizin-
  Startorder, Minimalstart Industrie 1/Infrastruktur 2, `workforceFactor`), und
  der WS-Client hatte weder Befehls-Timeout noch Abbruchbehandlung – ein
  verlorener Ack ließ den Lauf unbegrenzt still stehen.
