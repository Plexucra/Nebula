# 22. Handelsgilde-Stationen: Depot und Börsenhandel

Design-Vorgabe (Nutzerentscheidung): jeder Kommandant erhält an jeder
Handelsgilde-Station (`StarSystem.isTradeHub`) ein **unbegrenztes Depot**,
das per Flotte mit Frachtern beliefert wird. Neben Verkaufs-Orders gibt es
jetzt auch **Kauf-Orders** – wie an einer Börse. Kreuzt eine neue Order
sofort eine bestehende Gegen-Order, wird SOFORT ausgeführt, auch in
Teilausführung. Ein synthetischer Market-Maker ("Handelsgilde") stellt für
jede handelbare Ware eine kleine Kauf- und Verkaufs-Order, damit überhaupt
Handel entsteht, und verschiebt seinen eigenen Preis nach jeder Ausführung
weiter nach außen. Echtes Handeln der NPC-Bot-Prozesse (`npc-bot/`) ist
NICHT Teil dieses Vorhabens (Nutzerentscheidung: "das mit den NPC machen
wir später" – nachgereicht in Umsetzungskonzept/23_...md).

## A. Ausgangslage und abgelöste Vorgabe

Umsetzungskonzept/21_...md legte fest: an einer Handelsgilde-Station gibt es
KEINE Kolonie und damit KEIN Depot – verkauft wurde ausschließlich direkt aus
der Fracht einer dort stationierten Flotte (`MarketCommands.
createSellOrderFromFleet`, Zweig ohne Landung), gekauft wurde direkt in eine
eigene Kolonie geliefert (`buyFromOrder`). Es gab nur Verkaufs-Orders vom Typ
`SellOrder`/`TradeLocationType.Station`, keine Kauf-Orders, und keine
Gegenpartei außer einem zufällig gleichzeitig anbietenden Spieler – ohne
Market-Maker entstand praktisch nie Handel.

Diese Vorgabe wird mit diesem Dokument bewusst umgekehrt: die Station bekommt
ein echtes, persistentes Depot je Kommandant und ein zweiseitiges Orderbuch.
Der bisherige "ohne Landung verkaufen"-Zweig von `createSellOrderFromFleet`
ist dadurch überflüssig geworden und wirft jetzt an einer Handelsgilde-
Station bewusst einen Fehler, der auf den neuen Weg verweist (siehe §C) –
ein zweites, unabhängiges Orderbuch am selben Ort wäre nur verwirrend
gewesen. Alles andere aus Konzept 21 (Handelsverträge, `isTradeHub`,
Blockade-Freiheit) bleibt unverändert gültig und unberührt.

## B. Neues Modell: Depot, `HubOrder`, Orderbuch

- **`HubDepotEntry`** (`systemId`, `ownerId`, `productTypeId`, `quantity`),
  Liste `GameState.hubDepot`, Helfer `HubDepot` (exaktes Gegenstück zu
  `Warehouse`, nur ohne Kolonie-Bezug und ohne Kapazitätsgrenze). Beliefert
  wird es über zwei neue `FleetCommands`: `unloadCargoToHubDepot`/
  `loadCargoFromHubDepot`, analog zu `unloadCargo`/`loadCargo`, mit
  derselben Massen-/Volumenprüfung. Eine eigene "Frachter-Pflicht" braucht es
  dafür nicht: `p_freighter` ist das einzige Schiff mit `cargoMassKg > 0`
  (`shared/catalog/ships.json`) – eine Flotte ohne Frachter hat Kapazität 0
  und kann grundsätzlich nichts laden.
- **`HubOrder`** (`systemId`, `productTypeId`, `side` [`Buy`/`Sell`],
  `ownerId` [`null` = Handelsgilde], `ownerName`, `limitPrice`, `quantity`,
  `remainingQuantity`, `escrowedCredits`, `createdAt`, `seq`), Liste
  `GameState.hubOrders`. Ersetzt das alte, nie tatsächlich verwendete
  `BuyOrder`-Modell (kein `remainingQuantity`, kein Escrow-Feld – für ein
  echtes Orderbuch ungeeignet; wurde gelöscht).
- Beide Listen sind **bewusst getrennt** von `state.sellOrders`/
  `SellOrder` gehalten (statt die ~2.800 Handelsgilde-Orders dort
  einzumischen) – sonst würde jeder bestehende lineare Scan über
  `sellOrders` (u. a. `EconomyTick.runConsumption`, `sellOrdersInSystem`,
  `replenishDormantSellOrders`) unnötig durch Stationsdaten laufen, die für
  diese Stellen ohnehin nie relevant sind (Handelsgilde-Stationen haben keine
  Kolonie, `runConsumption` filtert nach `colony.systemId`).

## C. Escrow: Ware und Credits sind sofort gebunden

Wie beim bestehenden Kolonie-Handel (Ware einer `SellOrder` verlässt sofort
das Lager) gilt jetzt symmetrisch für beide Seiten des Stationshandels:

- **Verkaufs-Order**: bucht die Ware sofort aus dem Depot aus
  (`remainingQuantity` selbst IST das Escrow). Zurückziehen bucht den nicht
  ausgeführten Rest zurück ins Depot.
- **Kauf-Order**: bucht `Menge × Limitpreis` sofort aus dem Wallet aus
  (`HubOrder.escrowedCredits`, direkte `wallet.balance`-Mutation ohne
  Ledger-Eintrag – eine Reservierung ist kein abgeschlossenes Geschäft,
  genau wie beim Warenlager). Zurückziehen erstattet den verbleibenden
  Escrow-Saldo zurück ins Wallet.
- **Nur eine tatsächliche Ausführung** erzeugt einen `Ledger`-Eintrag
  (`TransactionReason.Trade`) – und zwar NUR in Richtung Verkäufer: der
  Käufer wurde bereits beim Einstellen der Order belastet, ein zweiter Abzug
  zum Ausführungszeitpunkt wäre eine Doppelbuchung (im Test
  `HubMarketCommandsTest` explizit geprüft).

`MarketCommands.cancelSellOrder` wurde dabei gehärtet: der bisherige
Kolonie-Handel kannte für eine Order ohne Flotten- UND ohne Kolonie-Ziel
keinen Erstattungspfad (Ware wäre ersatzlos verschwunden) – bislang
unerreichbar, aber genau die Form, die eine Stations-Order hätte, wenn sie
je (fälschlich) dort landen würde. Wirft jetzt statt stillschweigend zu
löschen. Zusätzlich prüft `reserveForRelist` beim Auto-Relist jetzt auch,
ob die Quell-Flotte noch AM SELBEN Ort steht wie beim Einstellen der Order
(vorher: nur "irgendwo stationiert") – sonst hätte eine seitdem
weitergezogene Flotte an ihrem neuen Standort für eine Order an ihrem alten
Standort weiter Fracht abgegeben.

## D. Matching

Ausführung folgt Preis-Zeit-Priorität: bestes Gebot trifft besten Brief,
bei Preisgleichheit die ältere Order zuerst (`seq`, ein monotoner Zähler
zusätzlich zum Millisekunden-Zeitstempel `createdAt`, falls zwei Orders in
derselben Millisekunde entstehen). **Ausführungspreis = Preis der ÄLTEREN
(ruhenden) der beiden Orders** – die "Maker"-Seite bekommt ihren eigenen
Preis, die aggressiv einstellende "Taker"-Seite die Differenz zu ihrem
eigenen Limit automatisch erstattet (weil ihr Escrow nur um den tatsächlichen
Ausführungspreis sinkt, nicht um ihr Limit).

Eine einzelne Order-Anlage matcht in einer Schleife, bis sich bestes Gebot
und bester Brief nicht mehr kreuzen (`HubMarketCommands.matchHubOrders`) –
auch mehrere passende Gegen-Orders und Teilausführungen in einem Rutsch.
Selbsthandel wird übersprungen (`Objects.equals(bid.ownerId, ask.ownerId)`,
NULL-sicher – das schließt insbesondere ein, dass die Handelsgilde nicht
gegen ihre eigene, gerade erst eingestellte Order auf der Gegenseite
handelt, siehe §E).

## E. Market-Maker ("Handelsgilde")

- Deckt alle Warenkategorien AUSSER `Ship` und `GroundUnit` ab (177 von 187
  Produkten) – beide lassen sich nicht produzieren und nie einlagern
  (`ProductionCommands.queueProductionCore` lehnt sie ab), gekaufte Stück
  wären tote Depot-Einträge.
- **Startpreis = Produktionskosten** (`ProductCosts`, neue Klasse in
  `de.nebula.data`): rekursiv `workHoursPerUnit × 0,02 Cr + Σ Rezeptmenge ×
  Kosten(Eingang)`, bis zu den Rohstoffen (leeres Rezept). 0,02 Cr ist
  exakt der Lohnsatz, den die Bevölkerung tatsächlich verdient
  (`EconomyTick.payUpkeepAndWages`) – eine reine Umrechnung von
  Arbeitsstunden in Credits, KEINE zusätzliche Geldbewegung im Spiel (das
  Spiel selbst kennt sonst keinen Credit-Preis für Rohstoffe/Baustoffe/
  Schiffsmodule, nur Arbeitsstunden). Beispiele: Ferrometallerz 2,00 Cr,
  Grundnahrung 5,20 Cr, ein Schiffsmodul ~870 Cr, ein Frachter ~3.953 Cr.
  Verkaufs-Startpreis = Kosten, Kauf-Startpreis = Kosten × 1,2 – der
  Aufschlag gibt Neulingen von Anfang an einen Grund, für den Handel zu
  produzieren.
- **Explizit NICHT auf `STARTER_SELL_ORDER_PRICE = 450`** (die
  Grundnahrung-Startorder am eigenen Heimatkolonie-Handelsposten)
  angeglichen: 450 Cr ist kein willkürlicher Wert, sondern der in
  `WorldSeed.java` hergeleitete Gleichgewichtspreis, mit dem sich der
  Geldkreislauf schließt (Löhne + Gebäude-/Flottenunterhalt der Kolonie
  müssen aus dem Verkauf an die eigene Bevölkerung gedeckt werden). Eine
  Angleichung hätte die Spieler-Einnahmen auf einen Bruchteil der Ausgaben
  zusammengestrichen. Für die 174 Waren ohne bisherigen Credit-Preis
  entsteht dadurch ohnehin kein Konflikt; nur bei den drei Konsumgütern ist
  es gewollt, dass die eigene Bevölkerung mehr zahlt als die Gildenstation –
  selbst versorgen oder billig importieren wird dadurch zu einer echten
  Entscheidung.
- **Nach jeder Ausführung einer Handelsgilde-Order wird auf DERSELBEN Seite
  eine neue Order 10 % weiter außen eingestellt** (Verkauf ×1,1, Kauf ×0,9,
  ausgehend vom zuletzt gehandelten Preis dieser Seite, nicht wieder von den
  Produktionskosten) – die Handelsgilde steckt den Preisrahmen dadurch
  selbsttätig ab. Das Nachstellen passiert ERST NACH der gesamten
  Match-Schleife, nicht sofort je Einzelausführung: sonst könnte eine sehr
  aggressive Order die frisch nachgestellte Order im selben Durchlauf gleich
  wieder treffen und eine Kaskade auslösen.
- **Feste, kleine Losgröße** (`MM_LOT = 5`) je Order, unabhängig vom
  Warenwert – "kleine Kauf-/Verkauforders" war die ausdrückliche Vorgabe.
- Die Handelsgilde hält weder ein echtes Wallet noch ein echtes Depot: ihre
  Ware entsteht beim Einstellen und vergeht beim Kauf, ihre Credits ebenso
  (`Ledger.recordTx` erlaubt bereits `from`/`to = null` für Geldschöpfung/
  -vernichtung, das bestehende Muster für z. B. Bevölkerungswachstum). Sie
  ist eine Preisrahmen-Abstraktion, kein Wirtschaftsteilnehmer mit eigener
  Bilanz – bewusste Vereinfachung, siehe §H.
- **Einmalige Erstbefüllung der GESAMTEN Galaxie** in
  `GameStateSeeder.bootstrap` (`HubMarketCommands.seedAllMarketMakers`, für
  jede Station × jede handelbare Ware), NICHT lazy beim ersten
  Stationsbesuch – sonst müsste jede Sekunden-Abfrage der Orderbuch-Seite
  (Client-Polling) 177 Waren auf Vollständigkeit prüfen.
  `ensureMarketMaker` läuft zusätzlich defensiv bei jeder Order-Anlage mit
  (billige Existenzprüfung), falls eine Station je ohne diese Erstbefüllung
  entstehen sollte.

## F. Wire-Protokoll und Frontend

- `GameSocket`: `hubDepot`, `hubOrders`, `createHubSellOrder`,
  `createHubBuyOrder`, `cancelHubOrder`, `unloadCargoToHubDepot`,
  `loadCargoFromHubDepot` – im bestehenden `// --- Handel ---`-Block, nach
  demselben `case "…" -> ...`-Muster wie der Kolonie-Handel.
- `TradeOverviewComponent`: das Panel "Handelsgilde-Station" ist jetzt der
  Börsenplatz – Stationswahl, eigenes Depot (mit Inline-Verkauf je
  Warenzeile), ein per `ProductPickerDialogComponent` gewähltes Orderbuch
  (Kauf-Gebote absteigend, Verkaufs-Briefe aufsteigend, eigene Orders
  zurückziehbar) und ein generisches "Order aufgeben"-Formular (Seite,
  Menge, Preis).
- `FleetsOverviewComponent`: eine an einer Handelsgilde-Station
  stationierte (nicht gelandete) Flotte zeigt statt "Verkaufen" jetzt
  "Aus Depot laden" bzw. beim Fracht-Entladen "Ins Depot entladen" –
  dieselben Bedienelemente wie beim Kolonie-Laden/-Entladen, nur gegen das
  Stations-Depot statt das Kolonielager (`isAtTradeHub`-Hilfsmethode
  unterscheidet die beiden Fälle).

## G. Bekannte, dokumentierte Vereinfachung

`WorldSeed.createWorldSeed` wählt Heimatsystem (`galaxy.centralIndex()`,
geometrisch zentral) und Handelsgilde-Stationen (`GalaxyGenerator.
placeTradeHubs`, graphzentral über Gateway-Distanz) nach UNABHÄNGIGEN
Kriterien – theoretisch könnten beide auf denselben Index fallen, ein
Heimatsystem wäre dann zugleich Handelsgilde-Station. Die strikte Trennung
von `state.hubOrders`/`state.hubDepot` (§B) verhindert, dass ein solcher
Kollisionsfall den Kolonie-Handel oder den Stationshandel inhaltlich
verfälscht (beide Systeme berühren sich nicht); es ist also KEIN Fehler in
diesem Vorhaben, nur eine vorgefundene Randbedingung der Galaxiegenerierung,
die hier nicht angetastet wurde.

## H. Bewusst nicht umgesetzt

- ~~**Echte NPC-Bot-Handelsteilnahme**~~ (Nutzerentscheidung: "das mit den
  NPC machen wir später") – nachgereicht in Umsetzungskonzept/23_...md: die
  20 laufenden `npc-bot`-Prozesse handeln jetzt selbstständig an
  Handelsgilde-Stationen.
- **Keine Preisrahmen-Rückkehr zur Mitte**: die ±10 %-Leiter läuft nur nach
  außen, nichts zieht Kauf-/Verkaufspreis der Handelsgilde nach starkem
  einseitigem Handel wieder in Richtung Produktionskosten zurück. Nicht
  Teil der Nutzervorgabe, ggf. spätere Ausbaustufe.
- **Kein eigenes Wallet/Depot der Handelsgilde**: siehe §E – sie
  konjuriert/vernichtet Ware und Credits an der Handelsgrenze zu echten
  Spielern, statt eine eigene, auditierbare Bilanz zu führen. Für die
  vorgesehene Rolle (Preisanker, keine echte Marktteilnehmerin) bewusst
  ausreichend.
- **Keine Kapazitätsgrenze für das Stations-Depot** – ausdrückliche
  Nutzervorgabe ("unbegrenztes Depot").
