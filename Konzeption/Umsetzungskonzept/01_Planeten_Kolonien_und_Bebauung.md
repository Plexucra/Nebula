# Umsetzung: Planeten, Kolonien und Bebauung

*Grundlage: `Konzeption/07_Planeten_und_Bevoelkerung.md`,
`Mechanik/07_Planetare_Bebauung_und_Verteidigung.md`,
`Mechanik/11_Planetenwerte_Formeln.md`.*

## 1. Datenmodell

```text
Planet
  id, systemId, name, size, buildCapacity, resourceConcentration[]

Colony
  id, planetId, ownerId, foundedAt, isHomeworld: bool

PlanetStats (1:1 mit Colony)
  colonyId, infrastructurePct, securityPct, standardOfLivingPct,
  loyaltyPct, lastRecalculatedAt

Building
  id, colonyId, type (Infrastructure|ProductionFacility|
  PlanetaryDefense), level, buildPointsUsed, upkeepCost,
  activationState (nur PlanetaryDefense: Inactive|Activating|Active),
  activationCompletesAt

Overbuild-Zustand wird nicht persistiert, sondern aus
  Σ buildPointsUsed(alle Colonies auf demselben Planet) je Tick berechnet.
```

## 2. API-Endpunkte

```text
GET    /api/v1/planets/{planetId}
GET    /api/v1/planets/{planetId}/colonies
GET    /api/v1/colonies/{colonyId}
GET    /api/v1/colonies/{colonyId}/stats
GET    /api/v1/colonies/{colonyId}/buildings
POST   /api/v1/colonies/{colonyId}/buildings
  body: { type, targetLevel }
  → prüft buildCapacity, berechnet Überbebauungskosten
    (Mechanik/07_... §2), gibt BuildOrder mit completesAt zurück
DELETE /api/v1/colonies/{colonyId}/buildings/{buildingId}
  → freiwilliger Abbau, erstattet 50% der Basiskosten
    (Mechanik/07_... §2), gibt Bebauungspunkte sofort frei
POST   /api/v1/colonies/{colonyId}/buildings/{buildingId}/activate
  → nur type=PlanetaryDefense; startet 12h-Anlaufzeit (Mechanik/07_... §3)
POST   /api/v1/colonies/{colonyId}/buildings/{buildingId}/deactivate
  → sofortige Wirkung, Unterhalt endet sofort
POST   /api/v1/planets/{planetId}/colonies
  body: { colonizationFleetId }
  → nur wenn Gesamtbebauungskapazität des Planeten noch nicht erreicht
    und Spieler dort noch keine Colony besitzt
```

**Sperrregel während Bodengefecht:** `POST/DELETE` auf `/buildings`
liefert `409 Conflict`, solange `Colony` Teil eines aktiven `Battle`
(type=Ground) ist (siehe `05_Kampf_Blockaden_und_Kriegszustand.md`).

## 3. Hintergrundjobs

```text
PlanetStatsRecalcJob (je Zeitintervall je Colony):
  liest Population, Buildings, Konsumdeckung (aus
  ConsumptionTickJob, siehe 08_...), stationierte GroundForceGroups
  → schreibt neue infrastructurePct/securityPct/standardOfLivingPct
  → loyaltyPct wird separat inkrementell fortgeschrieben (Zeitfaktor,
    siehe Mechanik/11_... §5)

OverbuildCostJob (bei jeder Building-Änderung auf einem Planeten):
  Σ buildPointsUsed aller Colonies auf dem Planet neu berechnen,
  planetweiten Malus + individuellen Verursacheranteil je Colony
  aktualisieren
```

## 4. Menüführung

```text
Planetenübersicht (Startbildschirm nach Login)
├─ Kolonieliste (eigene Colonies, sortiert nach System)
│   └─ Kolonie-Detailansicht
│        ├─ Tab "Übersicht"     – PlanetStats als 4 Balken/Ringe
│        │                        (Infrastruktur/Sicherheit/
│        │                         Lebensstandard/Loyalität)
│        ├─ Tab "Bebauung"      – Gebäudeliste, Ausbau-/Abrissbuttons,
│        │                        Bebauungskapazitäts-Anzeige,
│        │                        Überbebauungs-Warnindikator
│        ├─ Tab "Verteidigung"  – Planetare Verteidigungsanlage,
│        │                        Aktivieren/Deaktivieren-Schalter mit
│        │                        12h-Fortschrittsanzeige
│        ├─ Tab "Produktion"    – siehe 02_...
│        ├─ Tab "Bevölkerung"   – siehe 08_...
│        └─ Tab "Handel"        – siehe 07_...
└─ Systemkarte (alle Planeten des Heimatsystems)
     └─ "Kolonisieren"-Aktion auf unbesiedelten Planeten
