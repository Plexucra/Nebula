# 37 — Planetarer Handelsposten als Orderbuch

**Vorgabe (Nutzer, 10.9.2026):** „Vom planetaren Handelsposten können auch
andere Kolonien auf dem Planeten ordern, ebenso alle anderen Spieler. Käufe
landen in einem Depot beim Posten. Alles analog, wie Handelsstationen
funktionieren." Entscheidungen dazu: Handelsvertrag bleibt Pflicht; keine
Handelsgilde-Orders am Posten; für Kolonien auf dem Planeten ist das Lager
das Depot.

## A. Ausgangslage

Bis dahin gab es zwei getrennte Handelsmodelle: reine Verkaufs-Orders je
Kolonie (`SellOrder`, `MarketCommands`) und das zweiseitige Orderbuch der
Handelsgilde-Stationen mit Depot je Kommandant (`HubOrder`, `HubDepot`,
`HubMarketCommands`, Umsetzungskonzept/22). Am Planetaren Handelsposten
konnte man nur aus einer fremden Order direkt ins eigene Lager kaufen –
auch von weit her, "buchhalterisch". Kauf-Orders gab es dort nicht.

## B. Das Modell

| Größe | Regel |
|---|---|
| Handelsort | Station (`planetId == null`) oder Posten eines Planeten (`planetId`); je Ort EIN Orderbuch (`MarketOrder`, `GameState.marketOrders`) |
| Posten existiert | auf jedem Planeten mit mindestens einer Kolonie |
| Zugang zum Posten | eigene Kolonie auf dem Planeten oder eigene Flotte, gelandet bei irgendeiner Kolonie des Planeten (der Posten ist neutral, Konzept 05 §6) |
| Depot | je Kommandant und Ort (`DepotEntry`, `Depot`), unbegrenzt; Flotte: „Ins Depot entladen" / „Aus Depot laden" auch bei fremder Kolonie |
| Lager = Depot | wer eine Kolonie auf dem Planeten hat: Verkaufs-Orders aus dem Lager (`sourceColonyId`), Käufe ins Lager; Depot bleibt ungenutzt |
| Matching | wie an der Station (Preis-Zeit-Priorität, Maker-Preis, Escrow), zusätzlich am Posten: Paare zweier Kommandanten ohne Handelsvertrag werden übersprungen |
| Bevölkerung | kauft am Kolonietag und beim Notkauf aus der Verkaufsseite des Postens ihres Planeten, günstigste zuerst, ohne Vertrag (Konzept 36) |
| Handelsgilde | Market-Maker nur an Stationen |
| Dauerorder | `autoRelist`: nach dem Leerkauf sofort aus der Quelle nachlegen (Lager oder Depot), sonst schlafend mit Restmenge 0 bis zum nächsten Zugang |
| Sofortkauf | „Kaufen" neben einer Verkaufs-Order (`buyFromOrder`): zum Orderpreis, mit Vertragsprüfung, Lieferung ins Lager der eigenen Kolonie auf dem Planeten, sonst ins Depot |
| Verkauf aus der Fracht | `createSellOrderFromFleet`: Fracht ins eigene Depot am Posten (bzw. Lager der eigenen Kolonie dort), dann Order – derselbe Weg wie an der Station in einem Schritt |

## C. Was sich dadurch ändert

- Ein Ordermodell statt zwei. `SellOrder`, `TradeLocationType`, `HubOrder`,
  `HubDepotEntry`, `HubDepot`, `HubMarketCommands` sind weg;
  `MarketCommands` bedient beide Orte. Die Socket-Befehle behalten ihre
  Namen (`sellOrders`, `createSellOrder`, `buyFromOrder`, `hubOrders`,
  `hubDepot`, `createHubSellOrder`, `createHubBuyOrder`, `cancelHubOrder`,
  `updateSellOrderPrice`), nehmen aber `planetId` an; `sellOrders` liefert
  die Verkaufsseite aller Posten eines Systems. `buyFromOrder` braucht kein
  `deliverToColonyId` mehr.
- Felder: `pricePerUnit` → `limitPrice`, `sellerId/sellerName` →
  `ownerId/ownerName`, `depotColonyId` → `sourceColonyId` plus `planetId`.
  Bot (`Economy.ensureLocalSellOrders`, `adjustPrices`) und e2e angepasst.
- Kolonie-Seite, Tab Handel: das Orderbuch des Postens (Briefe, Gebote,
  eigenes Depot, Kauf-Order aufgeben, Verkauf aus dem Lager gegen ein Gebot)
  und die Übersicht der anderen Posten im System.
- Flotten-Seite: bei einer fremden Kolonie gelandet wird ins Depot entladen
  und aus dem Depot geladen.
- Kein „buchhalterischer" Kauf mehr über Planetengrenzen: wer an einem
  fremden Posten kaufen will, muss dort eine Kolonie haben oder hinfliegen.
  Was er kauft, liegt in seinem Depot, bis eine Flotte es abholt.

## D. Nicht Teil dieses Konzepts

- Blockaden gegen Depot-Verkehr am Posten (Mechanik 09 §5 beschreibt es,
  umgesetzt ist es nicht).
- Eine Anzeige fremder Posten-Depots für den Kommandanten, dessen Kolonie
  auf dem Planeten liegt.
- Ein Preisvergleich über Posten hinweg wie für Stationen.

## E. Betroffene Stellen

Backend: `MarketOrder`, `MarketOrderSide`, `DepotEntry`, `Depot`,
`MarketCommands`, `Economy` (Einkauf über `MarketCommands.settleAsk`),
`FleetCommands` (Depot am Posten, Laden bei fremder Kolonie aus dem Depot),
`GameState`, `GameStateSeeder`, `WorldSeed` (Startorder am Posten),
`RetentionCleanup`, `FleetCompositionCommands`, `GameSocket`. Tests:
`MarketCommandsTest` (Station), neu `PlanetaryPostTest` (Zugang, Vertrag,
Lieferung ins Lager vs. Depot, Bevölkerung kauft bei Fremden, Abbruch).
Frontend: `trade.model.ts`, `game-api.ts`, `websocket-game-api.service.ts`,
`colony-detail` (Tab Handel), `trade-overview`, `fleets-overview`.
