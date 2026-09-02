# Umsetzung: Handelsgilde, Markt und Depots

*Grundlage: `Konzeption/05_Handelsgilde_und_Warenprinzip.md`,
`Mechanik/09_Handelsabwicklung_und_Markt.md`.*

## 1. Datenmodell

```text
TradePost
  id, type (LocalSystemMarket|SectoralStation), systemId | sectorId

Depot (planetares Handelsdepot, gehört zur Gilde)
  id, tradePostId, planetId, stock: [{ownerId, productTypeId, quantity}]

SellOrder
  id, tradePostId, locationType (Station|Depot), depotId | null,
  sellerId, productTypeId, quantity, remainingQuantity, pricePerUnit,
  createdAt   // keine Ablaufzeit

BuyOrder
  id, tradePostId, buyerId, productTypeId, quantity, pricePerUnit
  // NUR von Spielern erzeugbar – die Bevölkerung erzeugt keine
  // BuyOrders (Mechanik/09_... §12)

PersonalStorage (Spielerlager je Handelsort)
  playerId, tradePostId, productTypeId, quantity
  // Ziel eines Sell-Order-Abbruchs (Mechanik/09_... §13)

ConsumptionState (je Colony)
  colonyId, previousN, currentBudget,
  perGoodDemand: [{productTypeId, need, boughtSmoothed}]
```

## 2. API-Endpunkte

```text
GET  /api/v1/trade-posts/{id}/orders?productTypeId=&locationType=
POST /api/v1/trade-posts/{id}/sell-orders
  body: { locationType, depotId?, productTypeId, quantity, pricePerUnit }
  → Depotverkauf: bucht Ware vom Kolonielager ins Depot
POST /api/v1/trade-posts/{id}/sell-orders/{orderId}/cancel
  → verbleibende Menge zurück in PersonalStorage am selben Handelsort
POST /api/v1/trade-posts/{id}/buy-orders
  body: { productTypeId, quantity, pricePerUnit }
POST /api/v1/sell-orders/{orderId}/execute
  body: { quantity }
  → bei Depotverkauf: 403, wenn Käufer keine eigenen Frachter am Depot
    gelandet hat (Mechanik/09_... §3)
  → bei Stationsverkauf: sofort ausführbar, kein Frachter nötig

GET  /api/v1/colonies/{colonyId}/depot-access
  → listet Depots auf demselben Planeten (direkter Zugriff ohne
    Frachter, Mechanik/09_... §3)
```

## 3. ConsumptionTickJob (Bevölkerungskonsum, je Zeitintervall je Colony)

Implementiert exakt den Algorithmus aus `Mechanik/09_... §6-9`:

```text
1. budget = min(availableMoney, max(N, availableMoney / 10))
   N = 0.9 × previousN + 0.1 × incomeLastInterval

2. für jede Konsumkategorie (Reihenfolge: Grundbedarf, dann höhere
   Stufen) und solange budget > 0:
     remainingWeight = Σ missingShare(good) aller Güter der Kategorie
     für jedes Gut (feste Reihenfolge, siehe Sort-Spalte):
       goodBudget = budget × missingShare(good) / remainingWeight
       remainingWeight -= missingShare(good)
       kaufe aus erreichbaren SellOrders (siehe §4 unten) günstigste
       zuerst, bis goodBudget, remainingNeed oder Angebot erschöpft
       nicht ausgegebener Teil von goodBudget bleibt im budget

3. gekaufte Mengen fließen in die über 10 Intervalle geglättete
   Bedarfsdeckung → PlanetStatsRecalcJob (siehe 01_...) verwendet
   diesen geglätteten Wert für standardOfLivingPct
```

## 4. Erreichbare Sell Orders für Bevölkerungskauf

```text
population.reachableSellOrders(colony) =
    SellOrders WHERE locationType=Depot AND depot.planetId = colony.planetId
    UNION
    SellOrders WHERE locationType=Station AND tradePostId = colony.systemTradePostId
```

(entspricht `Mechanik/09_... §12`: kein Zugriff auf Depots anderer
Planeten)

## 5. Menüführung

```text
Kolonie-Detailansicht → Tab "Handel"
├─ Marktübersicht (Systemmarkt: Station + eigenes Planeten-Depot
│   nebeneinander)
├─ Meine offenen Orders (Sell/Buy), mit Abbrechen-Aktion
├─ Neue Order erstellen (Produkt, Menge, Preis, Ort: Station/Depot)
├─ Fremde Depots auf demselben Planeten (direkt handelbar)
└─ Fremde Depots auf anderen Planeten (nur mit gelandetem Frachter –
    Verweis auf Flottenübersicht, 03_...)

Bevölkerungs-Tab (siehe 08_...) zeigt zusätzlich:
└─ Konsumbudget N, Bedarfsdeckung je Kategorie/Gut (geglättet)
```
