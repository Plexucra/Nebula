# Umsetzung: Kampf, Blockaden und Kriegszustand

*Grundlage: `Konzeption/03_Militaer_und_Eroberung.md`,
`Mechanik/04_Kampfmechanik_Kern.md`,
`Mechanik/06_Blockaden_Gefechtsablauf_Aufmarsch.md`.*

## 1. Datenmodell

```text
WarState
  partyAId, partyBId, declaredAt, peaceEligibleAt (declaredAt + 24h),
  status (AtWar|Peace), peaceRequestedBy: [partyId]
  // Frieden erst wenn beide Parteien zugestimmt haben UND kein
  // aktives Battle zwischen ihnen läuft (Mechanik/06_... §1)

BlockadePoint
  id, connectionType (GatewayTransit|OrbitToSurface|SurfaceToColony),
  refId (gatewayId | planetId | colonyId)

Blockade
  id, blockadePointId (unique – max. 1 Blockade je Punkt),
  memberFleetIds: [fleetId]   // beliebig viele blockierende Flotten

Battle
  id, blockadePointId, startedAt, currentTick,
  sideA: { partyIds, exposedUnits: [{unitTypeId, count}], restDamage: {unitTypeId: value} }
  sideB: { ... gleiche Struktur }
  deploymentTick (1|2|3+, steuert 25%/50%/100%-Grenze)
  status (Active|Resolved)

BattleParticipant
  battleId, fleetId | groundForceGroupId, side (A|B),
  joinedAtTick, retreatRequestedAtTick | null
```

## 2. API-Endpunkte

```text
POST /api/v1/wars
  body: { targetPartyId }
  → sofort AtWar, peaceEligibleAt = now + 24h
POST /api/v1/wars/{warId}/peace-requests
  → wenn beide Parteien angefragt haben UND kein aktives Battle
    zwischen ihnen: status=Peace, cooldown 24h vor erneuter
    Kriegserklärung (WarCooldownJob)

POST /api/v1/blockade-points/{pointId}/blockades
  body: { fleetId }
  → 409, wenn bereits eine Blockade an diesem Punkt existiert und der
    Beitritt nicht zur bestehenden blockierenden Seite gehört
  → 409, wenn Krieg mit einer bereits blockierenden Partei besteht
GET  /api/v1/blockade-points/{pointId}/blockade
POST /api/v1/blockades/{id}/join
  body: { fleetId }
DELETE /api/v1/blockades/{id}/members/{fleetId}
  → nur außerhalb eines aktiven Battle; sonst muss regulärer Rückzug
    (siehe unten) verwendet werden

POST /api/v1/battles/{battleId}/join
  body: { fleetId | groundForceGroupId, sideHint: "attack" }
  → Seite wird ausschließlich aus WarState-Beziehungen bestimmt
    (Mechanik/06_... §12), sideHint dient nur der Absichtserklärung
    "ich will passieren" vs. "expliziter Gefechtsbeitritt"
POST /api/v1/battles/{battleId}/retreat
  body: { fleetId | groundForceGroupId }
  → markiert retreatRequestedAtTick; Einheit erhält im aktuellen Tick
    noch einmal Schaden (Mechanik/06_... §4), danach Austritt
GET  /api/v1/battles/{battleId}
  → liefert nur die für den anfragenden Spieler EXPONIERTEN
    gegnerischen Gruppen (siehe CombatTickJob unten)
```

## 3. CombatTickJob (Kernalgorithmus, alle 8h je aktivem Battle)

```text
1. Exposition aktualisieren je Seite:
     maxExposure = 10 × Σ productionAspect(exponierte Gegnerseite)
     deploymentCap = 25% | 50% | 100% je nach deploymentTick
     neu zu exponierende Einheiten: proportional nach Typ, gewichtet
     zugunsten geringen productionAspect (Mechanik/06_... §6-7)

2. Für jede Seite: Gruppenschaden je Einheitentyp
     = count × productionAspect × globalDamageFactor

3. Schadensverteilung (pro angreifender Gruppe → alle exponierten
   gegnerischen Gruppen, proportional zu deren productionAspect-Anteil,
   danach Kontermultiplikator ×2/×1/×0,5 anwenden) – siehe
   Mechanik/04_... §5

4. Restschaden-Verrechnung je (Seite, EinheitenTyp):
     total = restDamage + effectiveDamage
     losses = floor(total / haltbarkeit)
     restDamage_neu = total - losses × haltbarkeit

5. Überkill-Weiterverteilung (§6 in Mechanik/04_...) iterativ, solange
   Feuerkraft übrig und Ziele vorhanden

6. Verluste proportional auf BattleParticipant-Flotten/-Gruppen
   zurückverteilen (deterministisch, Mechanik/04_... §8)

7. Rückzugsanfragen aus vorherigem Tick abschließend verarbeiten
   (ein letzter einseitiger Schaden, dann Austritt)

8. Battle-Ende prüfen: eine Seite ohne exponierbare Einheiten mehr
   → Resolved; bei Battle-Ende: gesamter restDamage verfällt
```

Für Bodengefechte gilt derselbe Algorithmus; zusätzlich: Waffenträger-
Reservebestände nehmen nicht teil (Schritt 1 überspringt sie), und bei
vollständiger Niederlage der Verteidiger-Kolonie werden verbleibende
Reserven entfernt statt erbeutet (Mechanik/05_... §10).

## 4. Menüführung

```text
Kriegsübersicht (Player-Dashboard)
├─ Aktive Kriege (Gegenpartei, seit wann, peaceEligibleAt-Countdown)
├─ Friedensangebote (ausstehend/erhalten)
└─ "Krieg erklären" (Spielersuche)

Blockadenkarte (pro System)
├─ Blockadestellen (Gateway/Orbit/Boden) mit aktueller Blockade-Seite
└─ Aktion "Blockade errichten" / "Beitreten" / "Gefechtsbeitritt"

Gefechtsansicht (bei aktivem Battle)
├─ Eigene exponierte + nicht-exponierte (nur eigene) Einheiten
├─ Gegnerisch NUR exponierte Einheiten sichtbar
├─ Tick-Countdown bis nächste Kampfrunde
├─ Aufmarschfortschritt (25%/50%/100%-Anzeige)
└─ "Rückzug"-Aktion je eigenem Verband (außer Verteidiger-Eigentümer
    bei Kolonie-Verteidigung, siehe Mechanik/05_... §11)
```
