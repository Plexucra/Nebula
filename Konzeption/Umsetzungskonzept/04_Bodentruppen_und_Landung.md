# Umsetzung: Bodentruppen, Transport und Landung

*Grundlage: `Konzeption/03_Militaer_und_Eroberung.md`,
`Mechanik/05_Bodentruppen_und_Bodenkrieg.md`.*

## 1. Datenmodell

```text
GroundUnitType
  id, class (Soldier|LightWalker|MediumWalker|HeavyWalker),
  productionAspect (Basiszeit × Arbeitskräfte, für Konter-/
  Haltbarkeitsberechnung, siehe Mechanik/04_...),
  transportSlotUsage (0,05 / 1 / 1 / 20, siehe Mechanik/05_... §6)

GroundForceGroup
  id, ownerId, locationType (PlanetSurface|Colony),
  locationId, colonyId (falls dort rekrutiert/stationiert)
  units: [{ unitTypeId, activeCount, reserveCount }]
  // reserveCount: Waffenträger ohne Soldaten ODER Soldaten ohne
  // Waffenträger, je nach unitTypeId (siehe Mechanik/05_... §3)

GroundTransportFleet (Spezialisierung von Fleet)
  fleetId, transporterCount, cargo: [{unitTypeId, count}]
  // Ladung proportional über alle Transporter verteilt, siehe
  // Mechanik/05_... §6

RecruitmentQueue
  id, colonyId, unitTypeId, count, startedAt, completesAt
  // nur startbar wenn Colony.loyaltyPct > 50 (Mechanik/05_... §5)
```

## 2. API-Endpunkte

```text
POST /api/v1/colonies/{colonyId}/recruitment-queue
  body: { unitTypeId, count }
  → 403, wenn loyaltyPct <= 50
  → Rekrutierungsgeschwindigkeit skaliert mit loyaltyPct
    (Mechanik/05_... §5)
GET  /api/v1/colonies/{colonyId}/ground-forces
POST /api/v1/ground-forces/{groupId}/split
POST /api/v1/ground-forces/{groupId}/merge
POST /api/v1/ground-forces/{groupId}/disband
  → nur wenn locationType=Colony und eigene Colony;
    Waffenträger → Lagerbestand, Soldaten → Population
POST /api/v1/ground-transport-fleets
  body: { fleetId, groupId }
  → verlädt GroundForceGroup proportional auf Fleet-Transporter
POST /api/v1/ground-transport-fleets/{id}/land
  body: { targetPlanetId }
  → löst LandingDefenseJob aus (siehe unten), danach neue
    GroundForceGroup am Ort "PlanetSurface"
POST /api/v1/ground-forces/{groupId}/move
  body: { targetColonyId }
  → 1 Kampftick Dauer, unabhängig von Distanz (Mechanik/05_... §7)
```

## 3. Landungsabwehr (Event-Endpunkt, kein Tick-Job)

```text
Intern ausgelöst durch POST .../land:

LandingDefenseResolver(transportFleet, targetPlanet):
  for each Colony on targetPlanet
      where Colony.warState(attacker) == AtWar
        and Colony.planetaryDefense.activationState == Active:
    capacity = baseCapacity(defenseLevel) × random(0.5, 1.0)
    destroyed = floor(min(capacity, remainingTransporters))
    → Ladungsverlust proportional + aufgerundet (Mechanik/05_... §8)
```

Dieser Resolver ist bewusst **kein** periodischer Job, sondern eine
synchron beim `land`-Aufruf ausgeführte Funktion (einmalig pro
landender Flotte).

## 4. Menüführung

```text
Kolonie-Detailansicht → Tab "Bodentruppen"
├─ Rekrutierung (nur sichtbar/aktiv wenn Loyalität > 50%)
│   └─ Auswahl Einheitentyp, Menge, Zeitvorschau (abhängig von
│       Loyalität)
├─ Verbandsübersicht (aktive/Reserve-Bestände getrennt dargestellt,
│   siehe Mechanik/05_... §3)
└─ Verladen auf Transporter → Übergabe an Flottenübersicht (03_...)

Planetenoberfläche-Ansicht (pro Planet, nicht pro Kolonie)
├─ gelandete GroundForceGroups aller Spieler (sichtbar je nach
│   Aufklärung/Exposition, siehe 05_Kampf_...)
└─ Bewegungsbefehl zu einer Kolonie auf demselben Planeten
