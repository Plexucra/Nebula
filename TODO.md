# TODO

## Offen

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
- [ ] **F9, politisches Gewicht**: die Galaxiekarte zeigt je System ein Gewicht
  je Kommandant (Einwohner × Loyalität der eigenen Kolonien dort). Es hat keine
  Spielwirkung und wird nirgends erklärt – Rest eines früheren Konzepts zur
  Gateway-Kontrolle. Entweder Mechanik nachziehen oder Anzeige entfernen.
  *Empfehlung 11.9.2026: Anzeige entfernen.*

- [ ] **Anzeige zwischen zwei Kolonietagen** (Konzept 36 §E): Kontostand
  und Bevölkerung springen einmal je Spieltag. Die Oberfläche könnte mit
  Basis plus Rate weiterzählen, wie bei Countdowns; der Server liefert die
  Raten bereits (`treasuryFlowPerHour`, `growthRatePerInterval`).
  *Empfehlung (Review 11.9.2026 §3.3): nicht umsetzen und abhaken – die
  Werte springen im Spiel wirklich einmal am Tag, eine geglättete Anzeige
  zeigte einen Verlauf, den es nicht gibt.*

- [ ] **Invasionsdauer unter Live-Balance** (Gesamttest 11.9.2026 B2):
  2 000 Soldaten je Landung, 27 Plätze je Transporter, 6 Spieltage je
  Werftcharge – Eroberungen kommen erst nach Stunden Realzeit.
  Stellschrauben: Soldatenbedarf der Landung (`Military.sizeForce` im Bot,
  Siegeanteil) oder `troopCapacity` des Transporters im Schiffskatalog.
  Entscheidung, keine Reparatur.

## Erledigt

- [x] ~~Werft fertigte Vorprodukte am Industriekomplex vorbei~~ – 11.9.2026.
  „Vorprodukte automatisch mitproduzieren" im Werftauftrag rechnete die ganze
  Vorkette in dessen Laufzeit ein; in der Produktionswarteschlange stand kein
  Auftrag, nichts war blockiert. Jetzt montiert die Werft nur, was im Lager
  liegt: `queueShip` lehnt ohne Vorprodukte ab („Fehlende Vorprodukte: …",
  gleiches Format wie beim Ausbau, geprüft VOR dem Abmustern der Kolonisten),
  der neue Befehl `queueMissingShipInputs` reiht das Fehlende als EIN Bündel
  in die Produktion ein (Knopf auf der Flottenseite mit Bestand/Bedarf je
  Vorprodukt). Bots lesen die Ablehnung wie eine Baustoffmeldung. Test:
  `ShipyardInputsTest`. **Offen:** das Ausbildungszentrum
  (`RecruitmentCommands`) hat dasselbe Muster noch.

- [x] ~~Gehälter je Arbeitsstunde, Kauforders der Bevölkerung, Arbeiter und
  Akademiker, Forschungszentrum~~ – 11.9.2026, Konzept 38. Produktion kostet
  beim Start Löhne (`ChainPlan.wageCredits`, Code 509 ohne Guthaben), die
  Kopfpauschale entfällt; die Bevölkerung stellt stehende Kauforders aus
  ihrem Tagesbudget (`MarketOrder.populationColonyId`), Tageseinkauf und
  Notkauf sind weg; Güterstaffel je Wohnstufe mit Wachstumsgut
  (`GoodsLimited`); Akademiker (`Population.academics`) in bezahlten Plätzen
  des Forschungszentrums (`b_research`), Forschungsniveau als Abfrage;
  Trinkwasserration und Standardnahrung gestrichen, Militärausrüstung als
  eigene Kategorie; Startpreis 20 Cr (der frühere offene Punkt „Startpreis
  60" ist damit entschieden: die Bevölkerung setzt den Preis). Bots verkaufen
  ins Gebot. Tests: `WagesAndBidsTest`, `PopulationClassesTest`.

- [x] ~~Mindestdauer für Produktionsaufträge, Bebauung als Karten mit Bild~~ –
  11.9.2026.
  - **Mindestdauer 10 Spielminuten** (`minProductionOrderGameMinutes` in
    `shared/game-constants.json`, bei Tempo 4 rund 0,1 s Realzeit): kürzere
    Aufträge lehnt `queueProduction`/`queueProductionBundle` ab; die Meldung
    nennt Dauer, den Hinweis auf ineffiziente Kleinstlose und die
    Mindeststückzahl (bei Bündeln je Baustoff). Die Oberfläche zeigt das schon
    in der Vorschau („Los zu klein", Knopf „Auf N Stück erhöhen", Einreihen
    gesperrt; neue Abfrage `minimumProductionQuantity`). Ein wartender Auftrag,
    der beim Start darunter fällt (Industrie ausgebaut, Spezialisierung
    gestiegen), wird mit Code 504 gestoppt, samt Benachrichtigung mit
    Mindestmenge; die Warteschlange läuft mit dem nächsten weiter,
    „Fortsetzen" erklärt statt still neu zu stoppen. **Vom System angelegte
    Aufträge werden angehoben statt abgelehnt**, mit Puffer auf die doppelte
    Mindestdauer (genau auf die Mindestmenge angehoben, stoppten im Browsertest
    zwei wartende Startaufträge vor ihrem Start, weil die Kolonie inzwischen
    schneller fertigte): Startaufträge (vorher 3 Stück
    Elerium bzw. 42 Grundnahrung – bei Industrie 5 nach Sekunden fertig),
    „Fehlende Baustoffe produzieren" (im Verhältnis, Rest bleibt im Lager), das
    Neu-Einreihen eines Dauerauftrags nach Fertigstellung und Bot-Aufträge
    (`raiseToMinimum: true`). Werft und Ausbildungszentrum sind nicht
    betroffen. Test: `MinimumOrderDurationTest`; e2e prüft Ablehnung und
    Mindestmenge.
  - **Bebauung:** jede Bebauung ist eine eigene Karte mit Kopfzeile (Name,
    Zweck, Ausbau-/Blackout-Marke), Farbkante je Kategorie und vier Bereichen
    Bild / Stufe / Informationen / Ausbau; Baustoffe als Tabelle Bedarf/Lager,
    Ausbaufortschritt als Balken. Bilder: Fotos aus `/Bilder`, für das Web
    verkleinert (`frontend/public/buildings/<id>.jpg`, 1180 × 800, je unter
    300 KB), in einem einheitlichen Rahmen von 600 × 400 links, daneben Stufe
    und Informationen, darunter der Ausbau in drei Spalten (Eckdaten,
    Baustoffe, Hinweise/Knöpfe). Schmale Karten stapeln per Container-Abfrage,
    das Bild bleibt 400 px hoch. Seit 11.9. abends ist der Ausbau-Bereich
    standardmäßig eingeklappt (Kurzzeile Ziel · Kosten · Dauer · Hindernis,
    Knopf „Bauen/Ausbauen ▾", mehrere Karten gleichzeitig aufklappbar); ein
    laufender Ausbau zeigt immer Fortschritt und „Abbrechen". Im Tab
    Produktion steckt „Neuer Auftrag" (Formular, Los-zu-klein-Prüfung,
    Prognose) in einem Dialog über „Neuer Auftrag…" im Panelkopf; die
    Vorschau rechnet beim Öffnen, nicht mehr beim Tabwechsel. Planetare Abwehr hat noch kein Foto und zeigt
    die gezeichnete Grafik `b_defense.svg` im selben Rahmen. Ungebaute Gebäude
    gedämpft. Stil-Budget je Komponente auf 12 kB angehoben. Forschungszentrum
    seit 11.9. abends ebenfalls mit Foto. Schiffe: Korvette, Zerstörer, Kreuzer
    und Träger haben Fotos (`frontend/public/ships/<productTypeId>.jpg`, aus
    `/Bilder/Schiffe`), die Werft in der Flottenübersicht zeigt das Bild des
    gewählten Schiffstyps über dem Steckbrief; Frachter, Mannschaftstransporter
    und Kolonisationsschiff noch ohne Bild (dann kein Rahmen).

- [x] ~~Regelzahlen nach `/shared`, Treibstoffzahlen vom Server, Bot-Befunde,
  Oberflächen-Kleinigkeiten, `logs/` aus Git~~ – 11.9.2026, aus den
  Vorschlägen nach dem Review vom selben Tag.
  - **Regel für `/shared`:** Regelzahlen, die ein Mensch festlegt und mehr als
    ein Beteiligter kennt, stehen in `shared/game-constants.json`; Werte, die
    aus den Katalogen folgen, holen Bot und Oberfläche vom Server. Neu in der
    Datei: `consumerNeedPerCapitaPerGameHour` (Reihenfolge = Einkaufsreihenfolge,
    `GameConstants.CONSUMER_GOODS_ORDER` entsteht daraus), `creditsPerNewInhabitant`,
    `recruitMinLoyaltyPct` (vorher Literal 50 samt Text in `RecruitmentCommands`),
    `combatTickGameHours`, `dronesPerSoldier`, `siegeSurrenderLoyaltyPct`,
    `hoursPerGatewayHop` (auch im e2e-Skript).
  - **Bot:** neue `SharedConstants` im npc-bot; `Catalog` enthält keine
    abgetippten Zahlen mehr (Eleriumverbrauch, Startbevölkerung,
    Kolonisations-Loyalität, Kolonistenprämie, Bedarfe, Kampftakt …).
    Kampfwert je Schiff (`World.strength`, `carrierSlotUsage` der Schiffe mit
    Konterklasse) und je Drohne (`World.droneValue`) kommen aus `shipTypes`
    bzw. `productTypes` des Servers. Unbenutzte Kopien entfernt.
  - **Treibstoffzahlen vom Server:** `fleets`, `allFleets`, `fleetsInSystem`
    und `fleet` liefern `FleetCommands.FleetView` – die Flotte plus
    `fuelTankCapacity`, `jumpFuelPerHop`, `fuelRangeHops`. Oberfläche und Bot
    rechnen nicht mehr über den Schiffskatalog. Das Fassungsvermögen einer
    Flotte ist auf ganze Kapseln aufgerundet (vorher „11,3 Kapseln" beim
    Frachter). Test `FleetCommandsJumpFuelTest.fleetViewCarries…`.
  - **Bots bauen den Wohnkomplex aus**, sobald 80 % des Wohnraums belegt sind
    (Vorrang vor dem Ausbauplan, nicht in Blackout/Hunger). Vorher stand er
    in jedem Plan hinter Industrie 8, und alle 40 Bots blieben auf Stufe 1.
  - **Raider greifen keine Mini-Flotten mehr an** (Stärke unter 5
    Korvetten-Äquivalenten, `Military.MIN_RAID_TARGET_STRENGTH`, auch in der
    Zielwahl des Koordinators).
  - **Oberfläche:** Loyalitätshinweis in fremden Kolonien spricht vom
    „Kommandanten" statt von „Ihnen"; die Wohnraum-Kachel zeigt die Belegung
    („64 % belegt") statt „Wohnraum 400 %"; Konsumgüterlisten in Statistik und
    Preisanhalt kommen aus `CONSUMER_GOODS`; neun unbenutzte Exporte aus
    `core/shared-constants.ts` entfernt.
  - **`logs/` nicht mehr versioniert** (`git rm --cached`, `.gitignore` im
    Wurzelverzeichnis) – 40 Dateien, 800 MB, in die das Live-Spiel schreibt.

- [x] ~~Speicher, Löhne, Regelquelle~~ – 11.9.2026, Review in
  `Konzeption/Review_2026-09-11_Speicher_Loehne_Regelquelle.md`. Heap-Grenzen
  für Server (`-Xmx2g`) und Bot-Armee (`-Xmx1g`, Serial-GC) in den
  Startskripten; NPC-Post wird nach 30 echten Minuten weggeräumt
  (`RetentionCleanup.purgeNpcMail`); Lohn je Kopf auf ein Drittel (0,0067)
  und als `wagePerCapitaPerGameHour` an EINER Stelle in
  `shared/game-constants.json` für Backend, Preisanker und Bot.

- [x] ~~Bevölkerung kauft nur bei Vertragspartnern, 40 NPCs, Gesamttest~~ –
  11.9.2026. Die Bevölkerung einer Kolonie kauft am Posten nur noch Orders
  ihres eigenen Kommandanten oder seiner Handelsvertragspartner
  (`Economy.ownPostOrders`, Versorgungswarnung und `orderAvailable` folgen
  derselben Regel). Bot-Armee verdoppelt auf 20 je Lager (40 Bots), dafür
  `run-army.sh` auf den Ein-Prozess-Modus `BotArmy` umgestellt (40 JVMs
  passten nicht in den Speicher). Gesamttest mit Befunden und Fixes:
  `Konzeption/Testergebnis_2026-09-11_Gesamttest_Vertragsregel_40_NPC.md`.

- [x] ~~Planetarer Handelsposten als Orderbuch wie die Station~~ – 10.9.2026,
  Konzept 37. Ein Ordermodell für Station und Posten (`MarketOrder`,
  `MarketCommands`), Depot je Kommandant und Ort, Zugang über Kolonie oder
  gelandete Flotte, Handelsvertrag im Matching, keine Gilde-Orders am Posten,
  Lager = Depot für Kolonien auf dem Planeten. Kolonie-Handel-Tab zeigt das
  Orderbuch des Postens; Flotten entladen bei fremden Kolonien ins Depot.

- [x] ~~Tageseinkauf und Bevölkerungsvorrat, Kolonietag statt Sekundentakt~~ –
  10.9.2026, Konzept 36. Die Bevölkerung kauft nur noch am eigenen
  Handelsposten, einmal je Spieltag, mit sieben Tagesbedarfen Vorrat;
  Notkauf bei neuer Order unter einem Tag Vorrat. Die ganze Wirtschaft
  einer Kolonie ist EIN Ereignis je Spieltag (`Economy.colonyDay`), der
  galaxieweite Wirtschaftsschritt je Sekunde ist weg. Blackout endet mit
  dem Elerium-Nachschub. Panel „Versorgung und Vorrat" im Tab Bevölkerung.

- [x] ~~Startpreis 60 Cr, Handelsgilde als Notanker, F8/F11/F12, Systemnummern~~ –
  10.9.2026. Startorder auf 60 Cr (Bot rechnet mit demselben Wert); die
  Preisdrift der Handelsgilde ist entschieden gewollt (Gilde ist Notanker, kein
  Erwerb – kein Rücklauf zum Basispreis); Spezialisierung mit Skala (Stufe,
  Bonus, Stunden bis zur nächsten Stufe, Verfall); Systemansicht zeigt alle
  Kolonien eines Planeten mit Eigentümer und Landungsabwehr, Kolonisieren nur
  durch die eigene Kolonie gesperrt; Systeme tragen eine laufende Nummer
  („17 · Kessar“ überall, Zielwahl per Nummer beim Bewegen, Suche per Nummer);
  „Aurelia“ aus dem Namensvorrat, damit keine doppelten Planetennamen entstehen.

- [x] ~~Lastgrenzen 1 und 2: galaxieweite Abfragen, eine Sperre~~ – 10.9.2026.
  Keine Seite fragt mehr alle Flotten der Galaxie ab (`fleetsInSystem`,
  `fleet`, `fleetPresence` vom Server gezählt); Systeme und Routen werden bei
  Änderung gepusht und nur noch im Minutentakt gepollt. Abfragen laufen unter
  einem Leseschloss, Befehle und Ereignisplaner unter dem Schreibschloss
  (`GameState.lock`, `GameSocket.READ_ONLY`).

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
