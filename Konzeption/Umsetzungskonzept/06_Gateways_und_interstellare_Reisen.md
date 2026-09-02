# Umsetzung: Gateways und interstellare Reisen

*Grundlage: `Konzeption/04_Gateways_und_politische_Ordnung.md`,
`Mechanik/08_Gateway_und_Zollmechanik.md`.*

## 1. Datenmodell

```text
Gateway
  id, systemId, discoveredBy: playerId | null, discoveredAt,
  activatedBy: playerId | null, activatedAt,
  reachableSystemIds: [systemId]   // ca. 5-10, siehe Mechanik/08_... §1

GatewayAccessRule
  gatewayId, targetPlayerId | null (null = Standardregel),
  tariffPercent, allowed: bool

GatewayWeightSnapshot
  gatewayId, playerId, weight (Σ population × loyalty über alle
  Colonies des Spielers im System), computedAt
  // wird bei jedem PlanetStatsRecalcJob-Lauf einer betroffenen Colony
  // neu berechnet; der Spieler mit dem höchsten weight gilt als
  // Gateway-Kontrolleur

CarrierTransit
  fleetId, originSystemId, destinationSystemId, departsAt, arrivesAt
  // arrivesAt - departsAt ≈ 10 × reguläre Gatewayreisezeit
```

## 2. API-Endpunkte

```text
GET  /api/v1/systems/{systemId}/gateway
POST /api/v1/systems/{systemId}/gateway/discover
  → nur intern auslösbar durch Research-/Kolonisationsfortschritt
    (siehe 09_Navigation_..._und_Onboarding.md), kein manueller
    Spieler-Endpunkt im klassischen Sinn
POST /api/v1/systems/{systemId}/gateway/activate
  → setzt activatedBy/activatedAt, macht System auf der Galaxiekarte
    aller Spieler sichtbar (siehe GalaxyMapService unten)
GET  /api/v1/systems/{systemId}/gateway/access-rules
PUT  /api/v1/systems/{systemId}/gateway/access-rules
  body: { targetPlayerId | null, tariffPercent, allowed }
  → nur durch aktuellen Gateway-Kontrolleur (höchstes
    GatewayWeightSnapshot) änderbar
GET  /api/v1/systems/{systemId}/gateway/weight
  → Rangliste aller Spieler mit Gateway-Gewicht in diesem System

GET  /api/v1/galaxy-map
  → liefert nur Systeme mit activatedAt != null, plus das eigene
    (noch nicht aktivierte) Heimatsystem
```

## 3. GalaxyMapService (Sichtbarkeitslogik)

```text
isVisibleTo(system, player):
  if system.gateway.activatedAt == null:
      return player == system.homeOwnerId  // nur der Besitzer selbst
  return true  // nach Aktivierung für alle sichtbar, unabhängig von
               // individuellen Zugangsregeln (Sichtbarkeit ≠ Zugang)
```

Zugang (Passage) wird getrennt davon über `GatewayAccessRule` bzw. die
Kriegsbeziehungsprüfung beim Bewegungsbefehl geprüft (siehe
`05_Kampf_Blockaden_und_Kriegszustand.md` und
`03_Flotten_Schiffe_und_Werften.md` → `POST /fleets/{id}/move`).

## 4. Menüführung

```text
Galaxiekarte (Hauptnavigationspunkt)
├─ Sichtbare Systeme (nur aktivierte + eigenes Heimatsystem)
├─ Systemdetail
│   ├─ Gateway-Status (entdeckt? aktiviert? Kontrolleur + Gewicht)
│   ├─ Zollregeln (Standardsatz + individuelle Verträge, nur
│   │   editierbar für Kontrolleur)
│   └─ "Route berechnen" (Gateway-Kette vs. Trägerschiff-Option mit
│       Zeit-/Kostenvergleich)
└─ Heimatsystem-Ansicht (vor Aktivierung: Sonderansicht ohne
    Galaxie-Kontext, Fokus auf Erforschung/Kolonisation, siehe
    09_Navigation_..._und_Onboarding.md)
```
