# Umsetzung: Gateways und interstellare Reisen

*Grundlage: `Konzeption/04_Gateways_und_politische_Ordnung.md`,
`Mechanik/08_Gateway_und_Zollmechanik.md`.*

## 1. Datenmodell

```text
Gateway
  id, systemId, discoveredBy: playerId | null, discoveredAt,
  activatedBy: playerId | null, activatedAt,
  reachableSystemIds: [systemId]   // 3-6, siehe Mechanik/08_... §1

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

## 5. Galaxiegenerator (Frontend-Prototyp)

Der aktuelle Frontend-Prototyp (`frontend/`) enthält bereits einen
deterministischen, rein geometrischen Generator für die
Gateway-Topologie (`core/sim/data/galaxy-generator.ts`), der die
Vorgaben aus `Mechanik/08_...` §1 erfüllt:

```text
1. Systeme mit annähernd gleichmäßigem Mindestabstand zueinander
   platzieren (Poisson-Disk-artiges Sampling per Rejection-Verfahren) –
   vermeidet sowohl Ballungen als auch große Lücken.
2. EINE einzige, galaxieweit einheitliche Gateway-Reichweite bestimmen
   (Bisektion auf eine Ziel-Durchschnittsnachbarnzahl von 4,5). Zwei
   Systeme sind Nachbarn genau dann, wenn ihr Abstand ≤ dieser
   Reichweite ist – keine Verbindung nach Rang ("die 6 nächsten"),
   sondern eine echte, für jedes System gleiche Entfernungsgrenze. Die
   Nachbarnzahl 3-6 pro System ist damit FOLGE der Systemplatzierung
   aus Schritt 1, nicht das primäre Auswahlkriterium.
3. Nur als seltene Korrektur an Dichte-Ausreißern: Kappung bei Grad 6
   (nächste 6 behalten) bzw. Auffüllung bei Grad < 3 (nächste
   unverbundene Systeme ergänzen, auch leicht außerhalb der
   Reichweite).
4. Zusammenhang des Gesamtgraphen erzwingen (kürzeste Kante zwischen
   getrennten Komponenten).
5. Sektorale Handelsstationen per Greedy-k-Center auf dem Graphen
   platzieren, bis Ø ≤ 2 und Max ≤ 3 Sprünge zur nächsten Station
   erfüllt sind.
6. Zentralster Knoten (geometrisch nächster zur Kartenmitte) wird als
   Heimatsystem vorgeschlagen.
```

Über 10 Seeds × 4 Systemzahlen (16/20/24/32 = 40 Kombinationen)
stichprobenartig verifiziert: Gradgrenzen, Zusammenhang und
Handelsstations-Kriterien werden zuverlässig eingehalten, die
tatsächliche Durchschnittsnachbarnzahl liegt dabei fast immer sehr nah
an den angestrebten 4,5.

**Bekannte Prototyp-Vereinfachung gegenüber diesem Dokument:** Der
Prototyp kennt (noch) keine anderen Spieler. Die `isVisibleTo`-Logik
aus §3 ist deshalb vereinfacht: Die Aktivierung des **eigenen** Gateways
macht sofort die komplette bereits generierte Galaxiekarte (alle
Systeme + alle Routen) bekannt, nicht nur die unmittelbaren Nachbarn –
in einer echten Mehrspieler-Umsetzung müsste weiterhin gelten, dass ein
fremdes System erst nach Aktivierung *seines eigenen* Gateways
sichtbar wird.
