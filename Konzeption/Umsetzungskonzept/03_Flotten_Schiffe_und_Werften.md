# Umsetzung: Flotten, Schiffe und Werften

*Grundlage: `Konzeption/03_Militaer_und_Eroberung.md`,
`Mechanik/03_Schiffsklassen_und_Kontersystem.md`.*

## 1. Datenmodell

```text
ShipType (Spezialisierung von ProductType, category=Ship)
  id, name, class (Corvette|Destroyer|Cruiser|Freighter|Carrier),
  cargoCapacity (nur Freighter/Carrier), carrierSlotUsage
  (wie viel Trägerkapazität ein Schiff belegt, nur relevant wenn per
  Carrier transportiert)

Fleet
  id, ownerId, locationType (System|BlockadePoint|Colony-Orbit),
  locationId, moveOrder: { destinationId, departsAt, arrivesAt } | null
  cargo: [{ productTypeId, quantity }]
  (Summe der Mengen × 1 ≤ Σ cargoCapacity aller Freighter/Carrier der
  Fleet; nur be-/entladbar, während moveOrder == null, siehe §2 unten)
  // Vorgesehene Ablösung von "Menge × 1": ProductType trägt inzwischen
  // massKg/volumeM3 je Einheit (siehe 02_..., §1) – die eigentliche
  // Begrenzung soll künftig aus Masse UND Volumen des geladenen Frachts
  // relativ zu je einer Massen- und Volumenkapazität des Frachters
  // bestehen, nicht mehr aus einer abstrakten Stückzahl. Noch nicht
  // umgesetzt.

FleetShipGroup
  fleetId, shipTypeId, quantity
  (aggregiert – KEINE Einzelschiff-Objekte, siehe
  Mechanik/04_Kampfmechanik_Kern.md §1)

ShipyardQueue
  id, colonyId, shipTypeId, quantity, startedAt, completesAt
```

## 2. API-Endpunkte

```text
GET  /api/v1/players/{playerId}/fleets
GET  /api/v1/fleets/{fleetId}
POST /api/v1/colonies/{colonyId}/shipyard-queue
  body: { shipTypeId, quantity }
POST /api/v1/fleets
  body: { colonyId, shipGroups: [{shipTypeId, quantity}] }
  → gründet neue Flotte aus Werftbestand
POST /api/v1/fleets/{fleetId}/merge
  body: { otherFleetId }
POST /api/v1/fleets/{fleetId}/split
  body: { shipGroups: [{shipTypeId, quantity}] }
  → erzeugt neue Fleet mit abgespaltenen Gruppen
POST /api/v1/fleets/{fleetId}/move
  body: { destinationId, route: [gatewayId,...] | via: "carrier" }
  → Reisezeit = Anzahl Sprünge × 2 Spielstunden (Gatewayroute) bzw.
    × 20 Spielstunden (via carrier, ≈10× Gatewayzeit) – siehe
    Mechanik/08_... §4. Rein sprunganzahl-basiert, nicht abhängig von
    der geometrischen Kartendistanz. Via carrier: keine politische
    Kontrolle unterwegs.
  → bei Gatewayroute: prüft TariffAgreement je passiertem Gateway
    (siehe 06_...), bucht Gebühr sofort bei Abfahrt (Standardsatz ohne
    Vertrag: 8 % je Sprung, Mechanik/08_... §3)
POST /api/v1/fleets/{fleetId}/load
  body: { productTypeId, quantity }
  → verlädt Ware vom Kolonielager/Depot in den Frachterladeraum,
    begrenzt durch cargoCapacity; instantan (Mechanik/08_... §5)
POST /api/v1/fleets/{fleetId}/unload
  body: { productTypeId, quantity }
  → entlädt Ware aus dem Frachterladeraum ins Kolonielager/Depot am
    aktuellen Ort; instantan
POST /api/v1/fleets/{fleetId}/blockade
  body: { blockadePointId }
  → siehe 05_Kampf_Blockaden_und_Kriegszustand.md
```

## 3. Hintergrundjobs

```text
ShipyardCompletionJob – Werftauftrag fertigstellen, Schiffe der Fleet/
  Colony-Bestand zuordnen
FleetMovementJob – bei Erreichen von arrivesAt: Fleet an neuem Ort,
  moveOrder = null; bei Gatewaypassage: Kriegsbeziehungsprüfung
  gemäß Mechanik/06_... §11 (kann Bewegung in Gefechtseintritt
  umwandeln)
FleetUpkeepJob – periodische Abbuchung, Ausschüttung an regionale
  Population (siehe Mechanik/10_... §4)
StrandedFleetCheckJob – markiert Fleet als "gestrandet", wenn keine
  eigenen/verbündeten Carrier mehr am Ort und kein Gatewayzugang
```

## 4. Menüführung

```text
Flottenübersicht (galaxieweit, alle eigenen Flotten)
├─ Flottenliste: Ort, Zusammensetzung (Icons je Schiffsklasse + Menge),
│   Status (stationär/unterwegs/blockierend/gestrandet)
├─ Flotten-Detailansicht
│   ├─ Zusammensetzung bearbeiten (Split/Merge)
│   ├─ Bewegen (Zielwahl: Gateway-Route mit Gebührenvorschau vs.
│   │   Trägerschiff-Route mit Zeit-/Kostenvorschau)
│   └─ Blockade beitreten/verlassen (nur an Blockadestellen)
└─ Werft (je Kolonie mit Werft-Gebäude)
     └─ Baumenü: Schiffstyp wählen, Menge, Kosten-/Zeitvorschau
```
