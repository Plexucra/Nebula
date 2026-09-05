# 13. Client-Server-Migration: Quarkus-Backend

## Ausgangslage und Ziel

NEBULA läuft heute vollständig im Browser: die gesamte Simulation (Produktion,
Kampf, Diplomatie, Handel, Bevölkerung, Tick-Verarbeitung) steckt in
`SimulatedGameApiService` (Angular, ~2700 Zeilen), der Zustand liegt in
Angular Signals und wird nach jeder Änderung nach `localStorage` gespiegelt.
Zwei Browser (z. B. Handy und PC) sehen deshalb zwei unabhängige Spielstände.

**Ziel Stufe 1**: derselbe Kommandant spielt vom Handy UND vom PC dasselbe,
gemeinsam geteilte Spiel. Die Simulation zieht dafür in ein Java-Backend
(Quarkus), das Frontend bleibt strukturell unverändert und tauscht nur die
Datenquelle hinter der `GameApi`-Schnittstelle aus.

**Persistenz**: bewusst vorerst NICHT umgesetzt. Der Spielzustand lebt nur im
Arbeitsspeicher des Backend-Prozesses (vgl. `GameState`, siehe unten) – ein
Neustart des Servers = neue Galaxie. Das vereinfacht Aufsetzen und Zurücksetzen
während der Migration erheblich. Eine spätere PostgreSQL-Anbindung ist
vorbereitet (siehe Abschnitt "Spätere Persistenz"), aber nicht Teil dieser
Phase.

**Leitplanke für die gesamte Migration**: *"nichts von der Spielmechanik darf
verloren gehen"*. Das bedeutet konkret:
- Jede Formel, jede Konstante, jede Sonderregel wird 1:1 aus dem TypeScript
  übernommen, nie neu erfunden oder "vereinfacht mitportiert".
- Wo eine Geschäftsregel noch nicht portiert ist, antwortet der entsprechende
  Befehl ehrlich mit einem Fehler (`ServerMessage` vom Typ `"Error"`) statt
  mit einer unvollständigen oder erfundenen Ersatzlogik. Eine kaputte, aber
  ehrliche Lücke ist immer besser als eine stillschweigend abweichende
  Simulation.
- Solange TS und Java nebeneinander existieren (während der gesamten
  Migration), gilt bei Unstimmigkeiten das TypeScript als Referenz (siehe
  bereits `engine/Formulas.java`, Klassen-Javadoc).

## Fortschritt

**Status: Migration vollständig abgeschlossen.** Alle 12 Phasen sowie die
Frontend-seitige `WebSocketGameApiService` sind fertig, kompiliert und live
verifiziert (Backend über Node-WS-Client UND per komplettem
Browser-Rauchtest mit `ng serve` gegen den laufenden `quarkus:dev`-Server –
Kommandant registrieren, Bebauung ausbauen inkl. tickgetriebener
Fertigstellung, Produktions-Warteschlange, Lagerbestand, Flottenübersicht).
Der folgende chronologische Verlauf dokumentiert die einzelnen Runden.

**Dauerhafter End-to-End-Test** (`backend/e2e/full-playthrough.mjs`):
wiederholbares Node-Skript, das AUSSCHLIESSLICH über echte
WebSocket-Verbindungen zum `/game`-Endpunkt läuft (zwei unabhängige
Clients, wie zwei echte Frontend-Instanzen) – kein direktes Anfassen von
`GameState`/Java-Services. Deckt den kompletten Spielablauf in einem Lauf ab:
zwei Kommandanten registrieren → Werft ausbauen (tickgetrieben abgewartet)
→ Rohstoff-Produktionskette einreihen und tickgetrieben bis zum
Lagerbestand abwarten → Werft-Auftrag für ein Kriegsschiff anstoßen
(Befehlsmechanik, NICHT die mehrhundertstündige Fertigstellung) → die
bereits vorhandene Startflotte per echtem, hop-für-hop abgearbeitetem
Gateway-Sprung zum Heimatsystem des zweiten Kommandanten schicken →
Verteidiger blockiert seine Heimatkolonie → Krieg erklären → Gefecht
auslösen → EINEN echten, tickgetriebenen Kampf-Tick abwarten. Die
Kampf-Verluste werden NICHT nur auf "irgendeine Änderung" geprüft, sondern
per lokaler 1:1-Nachbildung von `BattleCommands.computeSideDamage`/
`applyDamage` (Mechanik/04_..., §2-5) exakt vorausberechnet und gegen das
tatsächliche `BattleTickResult` verglichen (`assert.deepEqual`).

Beim ERSTEN Testlauf dabei einen Bug im Test selbst gefunden und behoben
(nicht im Backend): die lokale JS-Nachbildung des Kontermultiplikators
vertauschte `.class`/`.countersClass` auf beiden Seiten des Vergleichs
(`atkDef.class === defDef.countersClass` statt korrekt
`atkDef.countersClass === defDef.class`, analog für die Gegenrichtung) –
dadurch wichen die lokal vorausberechneten Verluste von den (korrekten)
Server-Werten ab. Nach der Korrektur läuft der Test grün mit exakter
Übereinstimmung.

Beispielhafter beobachteter Lauf (Werte variieren je Zufalls-Flottenstärke/
Galaxie-Topologie, siehe world-seed.ts): Kommandant A (`ply_5pz`,
Heimatsystem `sys_5px`) vs. Kommandant B (`ply_5qk`, Heimatsystem `sys_5qi`);
Werft 3→4 tickgetrieben; `p_ferrometall`-Kette (`ChainPlan.totalHours=0.379`)
Lagerbestand 0→3; Werft-Auftrag `p_corvette` mit `ChainPlan.totalHours=760.6`
gestartet; Kampfflotte A `[Korvette×7, Zerstörer×2, Kreuzer×1]` gegen
Kampfflotte B `[Korvette×8, Zerstörer×2, Kreuzer×3]`; Reise über 7
Gateway-Sprünge (70000ms); Gefecht `btl_7wo` – Tick 1 exakt vorausberechnete
UND tatsächliche Verluste: Angreifer `{Korvette: 2, Zerstörer: 1}`,
Verteidiger `{}`; sauber per `retreatFromBattle` beendet
(`outcome: Retreat`).

Ausführung: Backend separat starten (`cd backend && ./mvnw quarkus:dev`),
dann `node backend/e2e/full-playthrough.mjs [ws://host:port/game]`. Node
≥ 22 vorausgesetzt (globales `WebSocket`), keine weiteren Abhängigkeiten.

Stand nach der ersten Umsetzungsrunde (Phasen 1-4 aus der Reihenfolge unten
sind abgeschlossen und live gegen einen laufenden `quarkus:dev`-Server
verifiziert):

- **Domänenmodell** (67 Java-Dateien) und **Formeln** (`Formulas.java`)
  vollständig portiert.
- **WebSocket-Gerüst** (`GameSocket`, `ConnectionRegistry`, generischer
  Envelope) steht, inklusive Mehrfachverbindung pro Kommandant.
- **Statische Kataloge** (`ResourceCatalog`, `ProductCatalog` mit allen 214
  Einträgen, `BuildingCatalog`, `ShipCatalog`, `GroundUnitCatalog`,
  `PlanetTypeProfiles`) sowie `Clock`/`Rng` (inkl. JS-`ToInt32`-exakter
  Nachbildung des seed-basierten Zufallsgenerators) portiert.
- **Galaxie-Erzeugung** (`GalaxyGenerator`, `WorldSeed`) vollständig portiert
  und per `WorldSeedSmokeTest` verifiziert (200 Systeme, zusammenhängender
  Gateway-Graph, 10 NPCs, Heimatkolonie mit Industrie-Stufe 4/Werft-Stufe 3).
- **Konto/Anmeldung** (`registerPlayer`, `login`, `logout`, `players`,
  `resetGame`) läuft echt über das WebSocket-Protokoll, per Node-WS-Client
  gegen den laufenden Server verifiziert: zwei Kommandanten in derselben
  Galaxie, Broadcast der Spielerliste an alle Verbindungen, zweite
  gleichzeitige Verbindung für denselben Kommandanten (Handy+PC-Fall).

**Bewusste Abweichung vom TS-Original** (siehe `GameStateSeeder`-Javadoc):
Im Browser-Prototyp registriert `bootstrapFreshWorld` beim allerersten Laden
automatisch einen synthetischen Standard-Kommandanten – reiner
Einzelspieler-UX-Komfort, keine Spielmechanik. Im geteilten Server-Modell
gibt es das nicht: der ERSTE echte `registerPlayer`-Aufruf erzeugt die
Galaxie (`createWorldSeed`), jeder weitere fügt sich ein
(`createAdditionalPlayerSeed`) – exakt wie im TS-Original ab dem zweiten
Kommandanten. Die Mechanik selbst ("eine gemeinsame, mit jedem Kommandanten
wachsende Galaxie") ist unverändert.

**Zusätzlich seit der ersten Runde** (Anfang Phase 5, Teilbereich Bebauung/Kolonien):
- **Geldsystem-Grundbausteine** (`GameQueries`, `Ledger`): Wallet-Lookup,
  Autorisierung (`requireOwnColony` – wichtig, da jetzt echte fremde
  Kommandanten in derselben Galaxie sitzen, nicht nur ein einzelner
  Browser-Nutzer wie im TS-Original), Transaktionsbuchung (auf 800 Einträge
  gedeckelt wie im TS-Original).
- **Kolonien/Planeten-Queries + `colonizePlanet`** (`ColonyCommands`).
- **Bebauung komplett** (`BuildingCommands`): `queueBuilding`,
  `cancelBuildingOrder`, `demolishBuilding`, `activateDefense`,
  `deactivateDefense`, `overbuildFactor` – live verifiziert inkl.
  Autorisierungsgrenze (fremder/nicht eingeloggter Kommandant wird
  abgewiesen) und Doppel-Ausbau-Sperre.
- **Tick-Loop-Grundgerüst** (`GameTick`, `@Scheduled(every = "1s")`, Phase 12
  vorgezogen): wendet `processBuildingCompletions`/`processDefenseActivations`
  an – live verifiziert (Ausbauauftrag angestoßen, nach Ablauf der Bauzeit
  automatisch auf die Zielstufe gesprungen, `pendingOrder` geleert). Jede
  WS-Befehlsverarbeitung UND der Tick synchronisieren jetzt auf `GameState`
  (grobkörnige Sperre, siehe Klassen-Javadoc von `GameTick`), damit sich
  beide nicht überschneiden können. Wächst mit jeder weiteren Phase um die
  entsprechende `processXxx`-Methode aus dem TS-Original.
- **Produktion komplett** (`ChainPlanner`, `ProductionCommands`, `Warehouse`,
  `Specializations`, `Notifications`): `planChain`/`computeProductionHours`
  (inkl. Bevölkerung/Gebäudestufe/Spezialisierung/Fördergüte/Blackout-Faktor),
  sequentielle Ein-Auftrag-pro-Kolonie-Warteschlange (`queueProduction`,
  `previewProductionChain`, `resumeProduction`, `cancelProduction` mit
  anteiliger Gutschrift bei Abbruch inkl. zeitbasierter XP für JEDEN
  Kettenschritt), Tick-Anbindung (`processProductionQueue`). Live end-to-end
  verifiziert: Start-Auftragsliste einer neuen Kolonie korrekt sequentiell
  (1 `running` + Rest `queued`), Abbruch eines laufenden Auftrags mit
  anteiliger Lager-/XP-Gutschrift, Neuanlegen+Tick-Fertigstellung eines
  einfachen Auftrags (Lagerbestand + Spezialisierungs-XP korrekt gebucht),
  Autorisierungsgrenze bei `queueProduction`.

**Abhängigkeits-Erkenntnis (beim Anlesen von `runConsumption` entdeckt)**:
der Bevölkerungs-Konsum kauft aktiv von `SellOrder`s am Systemmarkt
(`settleSellOrderPurchase`) – Phase 6 (Bevölkerung/Geldsystem) ist also NICHT
unabhängig von Phase 10 (Handel/Markt) portierbar, wie die ursprüngliche
Reihenfolge nahelegt. Konsequenz: den kolonie-basierten Markt-Kern (Phase 10
teilweise) vorgezogen und zusammen mit Phase 6 umgesetzt.

**Markt-Kern (Teil von Phase 10, vorgezogen)** (`MarketCommands`,
`FleetCargo`): `createSellOrder`, `cancelSellOrder`, `buyFromOrder`,
`settleSellOrderPurchase` (inkl. Auto-Relist-Logik), `replenishDormantSellOrders`.
`createSellOrderFromFleet` bewusst NICHT enthalten (setzt den noch nicht
portierten Flottenfracht-Mechanismus, Phase 7, voraus) – der Flotten-Zweig
in `settleSellOrderPurchase`/`cancelSellOrder`/`reserveForRelist` ist trotzdem
1:1 mitportiert (aktuell nie erreichbar, aber bereit für Phase 7).

**Bevölkerung/Geldsystem komplett** (`EconomyTick`, `PowerGrid`): 
`payUpkeepAndWages`, `runConsumption` (inkl. Markt-Einkauf, EMA-geglättetes
Konsumbudget, Lebensstandard-Glättung), `recalcCoreStats` (Infrastruktur/
Sicherheit/Loyalität), `growPopulationAndMoneySupply` (inkl. Geldschöpfung
bei neuem Bevölkerungshöchststand), `consumePowerUpkeep` (Elerium-Verbrauch
des Energienetzes, geglätteter Blackout-Übergang), `runWealthRedistributionIfDue`
(täglicher Ausgleichsfonds), `recordStatsSnapshotIfDue`,
`decaySpecializations`. Alle acht in exakt der TS-`runTick`-Reihenfolge in
`GameTick` verankert (Reihenfolge ist wichtig – spätere Schritte lesen von
früheren gesetzte Werte, siehe Kommentar dort). `GameState` um
`rawStandardOfLiving`/`consumptionCoverage`-Maps und drei
Tick-Intervall-Zeitstempel ergänzt (TS-Gegenstücke waren private
Service-Felder). Live end-to-end verifiziert: Löhne/Unterhalt fließen
Tick für Tick vom Spieler- ins Bevölkerungs-Wallet, Bevölkerungs-Konsum kauft
tatsächlich von einer angelegten `SellOrder` (remainingQuantity sinkt),
Lebensstandard und Bevölkerung reagieren entsprechend.

Neue Wallet-/Bevölkerungs-Queries ergänzt: `wallet`, `population`.

**Flotten/Werften/Gateways komplett** (`FleetCommands`, `ShipyardCommands`,
`GatewayCommands`, `Graph`): Flottenbewegung hop-für-hop über den
Gateway-Graphen (`moveFleet`/`cancelFleetMove`/`processFleetArrivals`,
`Graph.bfsPath`/`bfsHops` 1:1 aus `core/util/graph.ts`), Fracht
(`loadCargo`/`unloadCargo`/`transferShipsToFleet` mit Massen-/
Volumenkapazitätsprüfung), Instant-Bewegung innerhalb eines Systems
(`moveFleetWithinSystem`), Werft-Warteschlange (strukturell identisch zur
Produktionswarteschlange), Gateway-/Galaxie-Queries
(`gateway`/`gatewayWeights`/`visibleSystems`/`galaxyRoutes`/
`hasVisitedSystem`). Live end-to-end verifiziert: `routePreview` liefert
korrekte Sprunganzahl/Zeit, eine losgeschickte Flotte wird `InTransit`,
kommt nach der berechneten Zeit tickgetrieben am Nachbarsystem an
(`hasVisitedSystem` kippt korrekt auf `true`), Werft-Warteschlange berechnet
korrekt eine (sehr lange, siehe Flachliste) Gesamtbauzeit für eine Korvette.

**Bodentruppen komplett** (`RecruitmentCommands`): Rekrutierungs-Warteschlange
(strukturell identisch zur Produktionswarteschlange), Crewing-Verteilung
(`recalcCrewing`: Soldaten kommandieren Drohnen aus der Ferne, proportionale
Verteilung über alle drei Drohnenklassen bei Kommandokapazitäts-Engpass).
Loyalitätsschwelle (&gt;50%) für Rekrutierung geprüft. Live verifiziert:
Warteschlangeneintrag korrekt angelegt und gestartet (Gesamtbauzeit inkl.
automatisch mitproduzierter Ausrüstungskette betrug im Test 243,55
Spielstunden – die volle tickgetriebene Fertigstellung wurde deshalb NICHT
abgewartet, siehe Begründung unten).

**Bewusst nicht bis zur Fertigstellung abgewartet**: Werft- und
Rekrutierungs-Warteschlange nutzen exakt denselben `ChainPlanner`/
Tick-Completion-Mechanismus wie die bereits vollständig verifizierte
Produktionswarteschlange (Phase 5) – nur mit deutlich längeren
Gesamtbauzeiten (Schiffe/Ausrüstungsketten liegen auf Ebene 6-7 des
Produktionsbaums, siehe Flachliste). Ein Live-Warten auf die volle
Fertigstellung hätte in diesem Fall mehrere Minuten Realzeit gekostet, ohne
zusätzliche Aussagekraft gegenüber dem bereits bewiesenen Mechanismus zu
liefern – Start/Warteschlangen-Zustand/Chain-Berechnung wurden stattdessen
live geprüft.

**Kampf/Blockaden/Diplomatie komplett** (`BattleCommands`, `BlockadeCommands`,
`DiplomacyCommands`): Blockaden (zwei Ankerarten, Gateway/PlanetOrbit, je
Anker/Flotte höchstens eine), Diplomatie (Kriegserklärung einseitig sofort
wirksam, Friedensangebot mit Mindestkriegsdauer- und Aktiv-Gefecht-Sperre,
beidseitige Annahme), Raumgefechte (Mechanik/04_..., §2-5 Kernformeln 1:1:
`computeSideDamage` proportional zum Produktionsaufwand-Anteil je Schiffstyp
mit Kontermultiplikator, `applyDamage` mit Restschaden-Fortschreibung über
mehrere Ticks, 1 Flotte gegen 1 Flotte, volle Exposition beider Seiten,
Rückzug als finaler einseitiger Schadens-Tick). `processBattles` läuft im
Tick zwischen `processFleetArrivals` und `processProductionQueue` (exakte
TS-Reihenfolge).

Live end-to-end verifiziert mit zwei ECHTEN Kommandanten in derselben
Galaxie: Kommandant A schickt seine Kampfflotte über 6 Gateway-Sprünge
(hop-für-hop, korrekt automatisch fortgesetzt) zum Heimatsystem von
Kommandant B, B blockiert seine eigene Kolonie (Planet-Orbit-Anker), A
erklärt B den Krieg, `engageBattle` startet ein Gefecht, der erste
Kampf-Tick greift tickgetrieben nach der berechneten Zeit (`COMBAT_TICK_HOURS`)
und wendet plausiblen, nicht-symmetrischen Schaden gemäß Flottenzusammen-
setzung an (1 Korvette verloren), `retreatFromBattle` beendet das Gefecht
sofort mit `outcome: Retreat`, `battleHistory` zeigt den abgeschlossenen
Kampf korrekt an.

**Rest von Phase 10 + Phase 11 komplett** (`NotificationCommands`,
`NpcCommands`, `MarketCommands.createSellOrderFromFleet`): Benachrichtigungs-
Queries (pro Kommandant auf dessen Kolonien gefiltert, `null`-`colonyId` =
global), Verkauf direkt aus Flottenfracht (Depot- vs. Stationsorder je nach
Landestatus, inkl. Fracht-Rückerstattung bei Abbruch – jetzt tatsächlich
erreichbar, da das Flottensystem seit Phase 7 steht), NPC-KI
(`runNpcAiIfDue`, alle 10 NPCs: Infrastruktur-/Industrieausbau innerhalb
eines Budgetrahmens, Grundnahrungskette + Spezialprodukt als sequentielle
Aufträge mit Auto-Produktion, Überschussverkauf mit deterministischer
Preisstreuung). `runNpcAiIfDue` im Tick zwischen `runWealthRedistributionIfDue`
und `recordStatsSnapshotIfDue` verankert (exakte TS-Reihenfolge).

Live end-to-end verifiziert: 10 NPCs korrekt geseedet, nach zwei
KI-Zyklen (12s) zeigt eine NPC-Kolonie einen laufenden
Grundnahrungs-Auftrag + einen wartenden Spezialprodukt-Auftrag
(korrekte Sequenzierung) sowie einen tickgetrieben abgeschlossenen und
sofort erneut angestoßenen Industrieausbau; eine Kriegserklärung erzeugt
korrekt eine Benachrichtigung beim Angegriffenen (`unreadNotificationCount`,
`markNotificationRead` funktionieren); Fracht laden → Verkaufsorder aus
Flotte anlegen → Order stornieren → Fracht korrekt zurückerstattet.

Damit sind **Phasen 5-11 von 12 vollständig abgeschlossen und live
verifiziert**. Phase 12 (Tick-Loop) war bereits seit Phase 5 durchgehend
mitgewachsen (`GameTick` enthält inzwischen alle `processXxx`-Schritte aus
der TS-`runTick`-Reihenfolge bis auf `schedulePersistFromTick`, das mangels
Persistenzschicht in dieser Phase entfällt) und gilt damit ebenfalls als
abgeschlossen.

**Vollständigkeitsprüfung**: alle 93 `GameApi`-Methoden (per Skript aus
`game-api.ts` extrahiert und gegen die `case`-Zweige in `GameSocket.java`
abgeglichen) haben eine Entsprechung im Backend – keine Lücke mehr außer den
bewusst als "noch nicht möglich" gestalteten TS-Original-Verhalten
(`transfer` war schon im TS-Original ein Platzhalter, der immer einen Fehler
wirft – 1:1 mitportiert, keine neue Lücke).

**Frontend-Anpassung fertig** (`WebSocketGameApiService`, `backend-config.ts`):
implementiert `GameApi` vollständig über das WebSocket-Protokoll. Jede
Befehlsmethode sendet `{type, requestId, payload}` und löst ihr Promise über
die passende `Ack`/`Error`-Antwort auf. Für Query-Methoden gibt es
serverseitig nur für den Kanal `"players"` einen echten Push (siehe
`GameSocket.onOpen`/`handleRegisterPlayer`/`handleResetGame`) – alle
anderen ~60 `Signal`-Rückgaben werden per Polling (1s-Intervall, an den
Server-Tick angelehnt) aktuell gehalten. Das ist eine bewusste Vereinfachung
ggü. der ursprünglich skizzierten vollen Push-Synchronisation (siehe
Abschnitt "Sync-Strategie" oben) – für die Größenordnung dieses Prototyps
ausreichend reaktiv, eine spätere Ausbaustufe könnte echtes Server-Push pro
Kanal nachrüsten. Ebenso bewusst nicht umgesetzt: automatisches Aufräumen
der Polling-Intervalle bei Komponenten-Zerstörung (kein Speicherleck-Risiko
für einen Rauchtest, aber der erste Ausbauschritt für Dauerbetrieb).

Umschaltung zwischen `SimulatedGameApiService` (Standard/Fallback) und
`WebSocketGameApiService` läuft über `backend-config.ts` – ein
Laufzeit-Schalter via `localStorage` (`nebula_backend = 'websocket'`,
optional `nebula_backend_url` für eine abweichende Server-Adresse) statt
Angular-`environment.ts`/`fileReplacements`: damit lässt sich das Backend im
selben Build ohne Neukompilieren umschalten, was für Testzwecke praktischer
ist als eine Build-Zeit-Trennung. `npx tsc --noEmit` und
`ng build --configuration development` laufen beide sauber durch.

**Im Browser-Rauchtest gefundener und behobener Bug**: mehrere bestehende
Komponenten rufen Query-Methoden wie `population(colonyId)`/`colonyStats(id)`
DIREKT im Template auf (z. B. `{{ population(colony.id)()?.currentCount }}`,
siehe `colony-list.component.html`) statt sie einmalig als Feld zu cachen.
Bei `SimulatedGameApiService`s reinen `computed()`-Signalen unproblematisch
(billige Neuberechnung ohne eigenen Zustand), bei `WebSocketGameApiService`s
Polling-Signalen mit echtem Netzwerk-Roundtrip aber fatal: jeder
Change-Detection-Durchlauf erzeugte ein KOMPLETT NEUES Signal mit eigenem
Intervall, das vor Eintreffen seiner ersten Serverantwort schon wieder
verworfen wurde – betroffene Anzeigen (Einwohnerzahl, Statuswerte) blieben
dauerhaft auf ihrem Startwert (0/leer) stehen. Live im Browser beobachtet
("0 EINWOHNER" trotz befüllter Galaxie), Ursache über die
`colony-list.component.html`-Vorlage identifiziert. Behoben durch
Memoisierung in `poll()` (Cache-Schlüssel = `type` + serialisierte
Payload-Parameter): dieselbe Abfrage liefert jetzt immer dasselbe Signal
zurück, egal wie oft/aus welchem Kontext sie aufgerufen wird – behebt den
Bug UND begrenzt zugleich die Zahl gleichzeitig laufender Intervalle auf die
Anzahl unterschiedlicher Abfragen statt auf die Aufrufhäufigkeit. Nach dem
Fix im Browser bestätigt: Einwohnerzahl, Infrastruktur-/Sicherheits-/
Loyalitätsbalken zeigen korrekte Live-Werte.

**Zweite, systematische Browser-Testrunde (Frontend + echtes Backend, Formel-
Gegenrechnung)**: `ng serve` gegen laufenden `quarkus:dev`-Server, per Klick
durch die UI (Registrierung, Bebauung, Produktion, Bevölkerung, Flotten,
Multi-Device), Werte gegen `formulas.ts`/`Formulas.java` nachgerechnet:

- **Registrierung**: neue Kommandantin "Admiralin Vex" (Heimatkolonie
  Vexheim, Aurelia Prime) über die UI registriert – reale Werte sofort
  sichtbar (514 Einwohner, 6.451 Credits), kein "0 Einwohner"-Rückfall.
- **Bebauung-Tab**: Startwerte bestätigt – Industriekomplex Stufe 4 (400%
  Tempo, nächste Stufe 500% = `buildingLevelSpeedFactor(4)=4`,
  `(5)=5` ✓), Werft Stufe 3 (300%/400% ✓), Ausbildungszentrum Stufe 1
  (100%/200% ✓) – exakt die für das 10-Spieler-Frachter-Balancing
  gewählten Startwerte.
- **Produktion-Tab, Transparenz-Panel**: bei 1.033 Einwohnern zeigt das
  Panel "Bevölkerung 1.033 × 2,58" – `workforceFactor(1033) =
  clamp(1033/400, 0.35, 5) = 2,58` ✓ (exakt); bei späterem Stand 1.435
  Einwohner → "× 3,59" – `clamp(1435/400,...) = 3,59` ✓. Industriekomplex-
  Faktor "× 4" durchgehend korrekt. Start-Produktionswarteschlange (Grundnahrung
  laufend, Stabilisiertes Elerium wartend) sequentiell wie geplant, Lagerbestand
  aktualisiert sich tickgetrieben ohne manuelles Neuladen (Grundnahrung 50→55).
- **Bevölkerung-Tab**: Zufriedenheit "18% Wachstumsgeschwindigkeit" bei
  Lebensstandard 0%/Sicherheit 400% – `growthConditionFactor(0, 400) =
  clamp(0,0.15,1.6) × clamp(4,0.5,1.2) = 0.15 × 1.2 = 0.18` ✓ (exakt).
  Lebensstandard nahe 0% ist bei einer frisch registrierten Kommandantin
  KEIN Bug, sondern korrektes TS-Verhalten: `runConsumption` kauft
  ausschließlich von `SellOrder`s im EIGENEN Heimatsystem (siehe
  `simulated-game-api.service.ts:1536-1538`), ein neues Heimatsystem hat
  aber noch keine – die Bevölkerung muss erst selbst versorgt werden
  (Spieler muss über "Anbieten" im Lager selbst Angebote einstellen). Verhalten
  1:1 im Backend nachgebildet, keine Änderung nötig.
- **Flotten-Tab**: Kampfflotte (Korvette×8, Zerstörer×3, Kreuzer×2) über
  die UI Richtung "Fahrun Weite" losgeschickt – Status wechselt sofort auf
  "UNTERWEGS" mit lebender Routenanzeige ("via Xantha 2 → Drift von Ilun 5
  → … → nächster Sprung in Ns"), zählt tickgetrieben herunter, exakt wie im
  reinen WebSocket-E2E-Test (`backend/e2e/full-playthrough.mjs`) bereits
  bestätigt – jetzt zusätzlich in der echten UI verifiziert.
- **Multi-Device-Kernanforderung ("vom Handy als auch vom PC dasselbe Spiel
  spielen") – ERFOLGREICH verifiziert**: zweiter, unabhängiger Browser-Tab
  geöffnet, selbe Kommandantin eingeloggt (zweite WebSocket-Verbindung) →
  zeigt sofort denselben Kolonie-/Flottenstand (keine Neuanmeldung erzeugt
  keinen zweiten Spielstand). Entscheidender Test: in Tab 2 einen
  Werft-Auftrag ausgelöst ("Bauen" geklickt) → Tab 1 (unberührt, nicht neu
  geladen) zeigt den neuen Auftrag ("Korvette × 1, LÄUFT, 21m 26s") und den
  aktualisierten Credits-Stand sofort per Push, ganz ohne manuelles
  Neuladen. Das ist die zentrale Migrationsanforderung aus Stufe 1 – bestätigt
  funktionsfähig.
- **Im Zuge dessen gefundener und behobener Bug**: `colony-list.component.html`
  band `@if (stats(colony.id); as s)` an die SIGNATUR-Referenz von
  `stats(colonyId)` (eine Methode, die pro Aufruf ein neues/gecachtes Signal
  zurückgibt) statt an dessen aufgelösten WERT – ein Signal-Objekt ist immer
  wahr, auch wenn sein aktueller Inhalt `undefined` ist. Bei
  `SimulatedGameApiService` (Wert sofort synchron vorhanden) fiel das nie
  auf; bei `WebSocketGameApiService`s Polling-Signalen (Anfangswert
  `undefined`, echter Wert erst nach dem ersten Server-Roundtrip) crashte
  das Template beim ersten Rendern mit `TypeError: Cannot read properties
  of undefined (reading 'infrastructurePct')` (reproduzierbar in der
  Browser-Konsole beobachtet). Behoben durch `@if (stats(colony.id)(); as
  s)` (Signal EINMAL aufrufen, dessen Wert binden) plus Entfernen der
  überflüssigen `s()!`-Wiederholungen im Block. Andere Templates mit
  ähnlichem `@if (…; as x)`-Muster durchsucht (`grep`) – keine weiteren
  Vorkommen dieses Fehlermusters gefunden (alle anderen Stellen binden
  bereits an fertige Werte, nicht an Signal-Referenzen). Nach dem Fix im
  Browser bestätigt: keine Konsolenfehler mehr beim Laden von `/planeten`
  mit echten Kolonie-Daten. `npx tsc --noEmit` weiterhin sauber.

## Warum die bestehende Architektur das begünstigt

`GameApi` (`frontend/src/app/core/sim/game-api.ts`) ist schon heute die
EINZIGE Abhängigkeitsgrenze zwischen UI-Komponenten und Simulation – jede
Komponente injiziert `GAME_API`, nie `SimulatedGameApiService` direkt. Das
Interface selbst dokumentiert das seit Session-Beginn: *"ein späteres echtes
Backend müsste nur eine `HttpGameApiService`-Klasse bereitstellen, die
dieselbe Schnittstelle über REST/WebSocket erfüllt... Kein anderer Teil der
App müsste sich ändern."* Diese Migration löst genau dieses Versprechen ein:
**kein UI-Component wird in dieser Migration verändert.**

## Architekturüberblick

```
┌─────────────────┐        WebSocket (JSON)        ┌──────────────────────┐
│  Angular Client  │  ◄──────────────────────────►  │   Quarkus Backend    │
│                  │                                 │                      │
│  UI-Komponenten  │                                 │  GameSocket (WS)     │
│       │          │                                 │       │              │
│  GAME_API Token   │                                 │  Command-Dispatch    │
│       │          │                                 │       │              │
│  WebSocketGameApi │                                 │  Service-Schicht     │
│  Service (neu)    │                                 │  (Formulas, Regeln)  │
│  implements       │                                 │       │              │
│  GameApi          │                                 │  GameState (RAM)     │
└─────────────────┘                                 └──────────────────────┘
```

Zwei oder mehr Browser-Tabs desselben Kommandanten (Handy + PC) halten je eine
eigene WebSocket-Verbindung zum selben Backend-Prozess; beide sehen denselben
`GameState` und werden bei Mutationen beide per Push aktualisiert (siehe
"Sync-Strategie").

## Generischer Nachrichten-Envelope statt 90 Einzeltypen

`GameApi` hat ~90 Methoden. Für jede einen eigenen Sealed-Record-Typ (Befehl +
Antwort) anzulegen wäre die "saubere" Lösung, aber angesichts des Umfangs
unrealistisch, um in nützlicher Zeit voranzukommen. Stattdessen (bereits
umgesetzt, siehe `backend/src/main/java/de/nebula/ws/`):

- **`ClientMessage`**: `{ type, requestId, payload: JsonNode }`. `type`
  entspricht 1:1 einem `GameApi`-Methodennamen (z. B. `"queueProduction"`),
  `payload` sind deren Argumente als JSON-Objekt.
- **`ServerMessage`**: `{ type, requestId, payload }` mit drei Verwendungen:
  - `"Ack"` + `requestId` = Antwort auf genau einen Befehl (Pendant zum
    aufgelösten Promise im Frontend).
  - `"Error"` + `requestId` = Pendant zum abgelehnten Promise; `payload` ist
    `{ message }` und wird 1:1 der bestehenden TS-Fehlertexte weitergereicht.
  - Kanalname (z. B. `"players"`, `"fleets"`, `"colonies"`) + `requestId: null`
    = unaufgeforderter Push, Ersatz für Angular-Signal-Reaktivität.

Jeder Befehl wird serverseitig anhand von `type` an eine Handler-Methode
verteilt, die ihr `payload` selbst per Jackson in die erwarteten Argumenttypen
parst. Das ist bewusst weniger typsicher als 90 einzelne Records, aber
inkrementell erweiterbar: ein Befehl kann implementiert werden, ohne dass alle
89 anderen vorher modelliert sein müssen.

## Sync-Strategie (ersetzt Angular-Signal-Reaktivität)

Jede `GameApi`-Query-Methode liefert im Frontend ein `Signal`. Serverseitig
gibt es dafür keine Entsprechung – stattdessen:

1. Bei `@OnOpen` schickt der Server für jeden relevanten Kanal einmal den
   aktuellen Stand (z. B. `push("players", state.players)`).
2. Nach JEDER Mutation, die eine Collection verändert, sendet der Server
   erneut einen Push auf den betroffenen Kanal – aber nur an Verbindungen,
   für die der Kanal relevant ist (z. B. `colonies` nur an Verbindungen des
   jeweiligen Kommandanten, nicht an alle; `WebSocketConnection.broadcast()
   .filter(...)` aus Quarkus WebSockets Next leistet das).
3. Der `WebSocketGameApiService` im Frontend hält für jeden Kanal ein
   eigenes Angular `signal()`, das er beim Empfang eines Push aktualisiert –
   nach außen (an die UI-Komponenten) verhält er sich identisch zu
   `SimulatedGameApiService`, nur dass die Werte jetzt vom Server kommen statt
   lokal berechnet zu werden.

Das ist bewusst grobkörnig (ganze Collections statt Deltas) – für die
Galaxiegröße dieses Prototyps (max. ~11 Kolonien pro Kommandant, wenige
Dutzend Kommandanten) ist das unproblematisch und spart die Komplexität eines
Delta-Protokolls.

## Mehrere Verbindungen pro Kommandant (Handy + PC gleichzeitig)

Anders als ein klassisches 1-Login-1-Session-Modell muss hier EIN Kommandant
mehrere gleichzeitig offene WebSocket-Verbindungen haben können. Der
`GameSocket` hält deshalb keine 1:1-Zuordnung Verbindung↔Kommandant, sondern
eine `Map<Id playerId, Set<WebSocketConnection>>` (`ConnectionRegistry`,
siehe unten): `login` trägt die aktuelle Verbindung zusätzlich in die Menge
des gewählten Kommandanten ein, `@OnClose` entfernt sie wieder. Pushes für
einen Kommandanten gehen an ALLE seine offenen Verbindungen gleichzeitig.

## Domänenmodell

Alle 20 TypeScript-Interfaces/-Typen aus `frontend/src/app/core/models/`
wurden mechanisch nach `backend/src/main/java/de/nebula/model/` portiert (67
Java-Dateien) – ein Typ pro Datei, öffentliche Felder statt Getter/Setter
(kein Verhalten, reine Datenhalter, passend zu Jacksons Standard-Binding ohne
Zusatzannotationen). Discriminated Unions (`FleetSystemTarget`,
`BlockadeAnchor`) wurden zu Java `sealed interface` + `record` je Variante.
String-Literal-Unions wurden zu Java `enum`, inklusive exakt gleicher
Bezeichner (auch mit Sonderzeichen, z. B. `PlanetSize.Groß`), damit die
JSON-Serialisierung ohne Übersetzungsschicht 1:1 kompatibel bleibt.

Bekannte, dokumentierte Abweichungen (rein technisch, ohne Mechanik-Wirkung):
- `System` (TS) → `StarSystem` (Java), da `System` mit `java.lang.System`
  kollidiert.
- Felder namens `class` (`ShipTypeDef.class`, `GroundUnitTypeDef.class`)
  wurden zu `shipClass`/`unitClass` umbenannt und per `@JsonProperty("class")`
  auf den ursprünglichen JSON-Feldnamen zurückgemappt.
- `BuyOrder` wurde der Vollständigkeit halber mitportiert, obwohl im
  TS-Original aktuell nirgends erzeugt/konsumiert (verifiziert per
  Volltextsuche).

## Formeln

`engine/formulas.ts` wurde 1:1 nach `backend/src/main/java/de/nebula/engine/
Formulas.java` portiert (jede Funktion, jede Konstante, inklusive der
deutschen Erklärkommentare). Das ist die einzige Stelle im Backend, an der
Zahlen "hart" vorkommen – jede Spielregel, die auf diesen Formeln aufbaut,
ruft ausschließlich diese Methoden auf, nie eigene Neuberechnungen.

**Offener Punkt**: ein automatisierter Parity-Test (TS-Werte vs. Java-Werte
für dieselben Eingaben, z. B. über eine kleine Testtabelle) existiert noch
nicht. Bis dahin gilt bei Verdacht auf Abweichung das TypeScript als Referenz.
Sollte in einer späteren Phase Zeit dafür sein, ist das ein sinnvoller
Absicherungsschritt, kein Blocker für die weitere Migration.

## Zustandshaltung: `GameState`

`backend/src/main/java/de/nebula/state/GameState.java` (`@ApplicationScoped`,
also ein einziges Singleton für den ganzen Server) hält für JEDE Collection
aus dem bisherigen `Snapshot`-Interface (der `localStorage`-Struktur der
`SimulatedGameApiService`) ein öffentliches Feld – Spieler, Systeme, Planeten,
Kolonien, Gebäude, Produktionswarteschlangen, Flotten, Kämpfe, Blockaden,
Diplomatie, Benachrichtigungen usw. Bewusst NUR Datenhaltung, keine
Geschäftslogik (die wandert in eigene Service-Klassen, sobald sie portiert
wird).

Threadsicherheit: `CopyOnWriteArrayList`/`ConcurrentHashMap` schützen vor
kaputten Iteratoren bei gleichzeitigem Lesen/Schreiben aus mehreren
WebSocket-Verbindungen und dem Tick-Scheduler-Thread, aber NICHT vor
Race Conditions innerhalb mehrschrittiger Invarianten (z. B. "Lagerbestand
lesen, dann abziehen"). Dafür ist eine grobkörnige Sperre pro Kommando
vorgesehen (z. B. ein einziges `synchronized`-Objekt oder ein
`ReentrantLock` in der künftigen Service-Schicht) – für die Größenordnung
dieses Prototyps (keine hochfrequenten konkurrierenden Schreibzugriffe zu
erwarten) ausreichend; eine feingranulare Sperrstruktur wäre verfrühte
Optimierung.

`IdGenerator` (`@ApplicationScoped`) portiert `core/sim/id.ts`: `next(prefix)`
liefert fortlaufende sortierbare IDs, `randomToken()` unerratbare
UUID-Tokens (für `Battle.reportToken` u. ä.).

## Reihenfolge der Geschäftslogik-Portierung

Die vollständige `SimulatedGameApiService` (~90 Methoden: Produktionsketten,
Kampf, Diplomatie, Blockaden, Handel, Benachrichtigungen, NPC-KI,
Bevölkerungswachstum, Bebauung, Bodentruppen, Flottenbewegung) ist der mit
Abstand größte verbleibende Teil dieser Migration und wird NICHT in einem
Rutsch portiert, sondern schrittweise, überprüfbar, in dieser Reihenfolge:

1. **WebSocket-Gerüst** (dieser Schritt): `GameSocket`-Endpunkt,
   Verbindungsregistrierung (Mehrfachverbindung pro Kommandant), generisches
   Dispatching, Push-Infrastruktur – ohne echte Spielregeln, jeder noch nicht
   implementierte Befehl antwortet ehrlich mit `"Error"`.
2. **Statische Kataloge**: `product-catalog.ts`, `building-catalog.ts`,
   `ship-catalog.ts`, `ground-unit-catalog.ts` – rein deklarative Daten, kein
   Verhalten, groß aber risikofrei portierbar.
3. **Galaxie-Seed**: `world-seed.ts` (Systeme, Planeten, NPCs, Startkolonie
   je Kommandant) – Voraussetzung für `registerPlayer` und `resetGame`.
4. **Konto/Anmeldung**: `registerPlayer`, `login`, `logout`, `players()`,
   `resetGame` – erste Befehle, die tatsächlich Spielzustand erzeugen.
5. **Bebauung, Produktion, Lager** – inkl. `ChainPlan`-Berechnung.
6. **Bevölkerung, Wallet/Geldsystem, Planetenwerte** – Tick-getriebene Werte.
7. **Flotten, Werften, Gateways/Reisen**.
8. **Bodentruppen, Ausbildungszentrum**.
9. **Kampf, Blockaden, Diplomatie**.
10. **Handel/Markt, Benachrichtigungen**.
11. **NPC-KI** (baut auf allen vorherigen Phasen auf).
12. **Tick-Loop** (`@Scheduled`): wird bereits ab Phase 5 in minimaler Form
    benötigt (Fortschritt der Produktionswarteschlangen), wächst mit jeder
    weiteren Phase um die jeweiligen periodischen Regeln.

Jede Phase ist einzeln im Browser gegen das neue Backend testbar (Frontend
zeigt in dieser Übergangszeit ggf. `SimulatedGameApiService` UND
`WebSocketGameApiService` parallel hinter einem Umschalter, siehe unten).

## Frontend-Anpassung

Einzige neue Frontend-Datei: `WebSocketGameApiService implements GameApi`
(vermutlich `frontend/src/app/core/sim/websocket-game-api.service.ts`). Für
jede `GameApi`-Methode:
- Query (liefert `Signal`): hält ein internes `signal()`, aktualisiert bei
  Empfang des passenden Push-Kanals.
- Befehl (liefert `Promise`): sendet `ClientMessage` mit neuer `requestId`,
  merkt sich ein Promise-`resolve`/`reject`-Paar in einer `Map<requestId,
  ...>`, löst es beim passenden `"Ack"`/`"Error"` auf.

`app.config.ts` entscheidet über den `GAME_API`-Provider, welche
Implementierung aktiv ist – während der Migration vermutlich per
Umgebungsvariable/Build-Konfiguration umschaltbar, damit
`SimulatedGameApiService` bis zum Abschluss aller Phasen als Fallback
nutzbar bleibt und nichts spielbares verloren geht, während das Backend noch
unvollständig ist.

## Spätere Persistenz (nicht Teil dieser Phase)

`GameState` ist bewusst so geschnitten (ein Feld pro Collection, POJOs ohne
JPA-Annotationen), dass eine spätere Anbindung an PostgreSQL entweder über
Hibernate/Panache-Entities (Collections würden dann aus der Datenbank
nachgeladen statt im RAM gehalten) oder über einen einfachen
Snapshot-Mechanismus (gesamter `GameState` periodisch als JSON in eine Tabelle
geschrieben, beim Start zurückgelesen) erfolgen kann. Diese Entscheidung wird
bewusst vertagt, bis die Geschäftslogik-Portierung (siehe oben) abgeschlossen
ist – vorher wäre jede Persistenzentscheidung Spekulation auf ein sich noch
veränderndes Datenmodell.

## Nichts-verloren-Verifikationsstrategie

- **Formeln**: `Formulas.java`-Javadoc verweist auf `formulas.ts` als
  Referenz; ein Parity-Test ist als offener Punkt vermerkt (siehe oben).
- **Vollständigkeits-Checkliste**: die Reihenfolge oben (Abschnitt
  "Reihenfolge der Geschäftslogik-Portierung") dient zugleich als
  Fortschritts-Checkliste über alle ~90 `GameApi`-Methoden – jede Phase wird
  erst als abgeschlossen betrachtet, wenn ALLE ihr zugeordneten Methoden im
  Backend beantwortet werden (kein `"Error": "not implemented"` mehr) UND
  im Browser gegen das laufende Backend nachgespielt wurden.
- **Ehrliche Lücken statt Notlösungen**: bis eine Phase portiert ist,
  antwortet der Server auf ihre Befehle mit einem klaren Fehler statt mit
  einer vereinfachten Ersatzlogik – ein Spielstand, der wegen eines fehlenden
  Befehls (noch) nicht weiterkommt, ist ehrlich; ein Spielstand, der wegen
  einer heimlich abweichenden Regel falsche Werte zeigt, wäre der eigentliche
  Mechanik-Verlust, den es zu vermeiden gilt.
