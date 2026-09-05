# 11. Diplomatie und Raumgefechte

## 1. Ausgangslage und Motivation

Bis zu diesem Umsetzungsschritt gab es weder eine Beziehung zwischen
Kommandanten (jede Flotte konnte jede andere ungehindert im selben System
treffen, ohne Konsequenz) noch eine tatsächliche Kampfberechnung – die
Kernformeln aus `Mechanik/04_Kampfmechanik_Kern.md` waren spezifiziert,
aber nicht implementiert. Dieses Dokument beschreibt die erste
Umsetzung beider Systeme: **Diplomatie** (Krieg/Frieden zwischen genau
zwei Kommandanten) und **Raumgefechte** (Flotte-gegen-Flotte,
Kernformeln aus `04_...md` vollständig übernommen).

Neu registrierte Kommandanten erhalten ab sofort neben der bestehenden
Handelsflotte auch eine kleine Kampfflotte mit ALLEN drei Klassen
(Korvette/Zerstörer/Kreuzer) sowie eine Boden-Garnison (3 aktive
Soldaten, 5 Drohnen je Klasse) direkt im Startbestand
(`world-seed.ts`, `combatFleet`/`starterGroundForceGroup`) – damit lässt
sich der komplette Konterkreis ohne vorherigen Werftbau oder
Rekrutierung ausprobieren. Die Stückzahl je Klasse ist bewusst zufällig
und deutlich unterschiedlich (Korvette 2-8, Zerstörer 1-5, Kreuzer 1-3,
siehe `combatFleet`) statt symmetrisch 1:1:1 – erst bei asymmetrischen
Flottenzusammensetzungen lässt sich der Kontermultiplikator beim Testen
eines Gefechts tatsächlich beobachten.

## 2. Diplomatie

Modell: `DiplomaticRelation` (genau ein Eintrag je ungeordnetem
Kommandanten-Paar, kanonisch sortiert – siehe
`SimulatedGameApiService.relationKey`) und `PeaceOffer`. Fehlt ein
Beziehungseintrag, gilt impliziter Frieden.

- **Kriegserklärung** (`declareWar`): einseitig, tritt sofort in Kraft,
  keine Vorwarnzeit (Mechanik/06_..., §1). Löscht dabei automatisch ein
  eventuell zwischen beiden noch offenes Friedensangebot.
- **Friedensangebot** (`offerPeace`): einseitig gestellt, wirksam erst
  nach ausdrücklicher Annahme durch den Empfänger
  (`respondToPeaceOffer(accept: true)`) – Ablehnen löscht das Angebot
  ersatzlos, der Krieg läuft unverändert weiter. Gesperrt vor Ablauf
  von 24 Spielstunden seit Kriegsbeginn (`WAR_MIN_DURATION_HOURS`) und
  während eines laufenden Gefechts zwischen den beiden Parteien –
  entspricht exakt Mechanik/06_..., §1.
- Diplomatie ist ausschließlich zwischen Spieler-Kommandanten möglich;
  NPCs sind laut ihrem Modell nicht-kriegerisch und bleiben außen vor.

UI: neuer Bereich „Diplomatie / Krieg“ (`features/diplomacy`) – Liste
aller anderen Kommandanten mit Status-Badge und Aktion
(„Krieg erklären“ bzw. „Frieden anbieten“), Liste ein-/ausgehender
Friedensangebote mit Annehmen/Ablehnen, Liste laufender Gefechte sowie
ein Kampfprotokoll bereits beendeter Gefechte.

## 3. Raumgefechte

Modell: `Battle` – siehe `combat.model.ts`. Ein Gefecht ist **immer**
strikt eine Flotte gegen eine Flotte (kein Mehrparteien-Gefecht).

### 3.1 Auslösen

`engageBattle(attackerFleetId, defenderFleetId)`: nur möglich, wenn
beide Flotten stationiert sind, im selben System stehen, ihre
Besitzer im Krieg stehen, die VERTEIDIGENDE Flotte eine aktive
**Blockade** gebildet hat (siehe §4 – ohne Blockade ist eine Flotte
nicht angreifbar), beide mindestens ein Schiff besitzen und keine der
beiden Flotten bereits in einem anderen laufenden Gefecht steht. Beide
Seiten sind ab Gefechtsbeginn vollständig exponiert (kein
Expositionslimit, siehe §6).

### 3.2 Kampf-Tick

Alle 8 Spielstunden (`COMBAT_TICK_HOURS`, über den Tick-Loop
ereignisbasiert geprüft wie die Flottenankunft) wird
`resolveBattleTick` ausgeführt:

1. Für jede Seite: Gruppenschaden gegen die andere Seite, proportional
   zum Produktionsaufwand-Anteil jedes gegnerischen Schiffstyps verteilt,
   Kontermultiplikator (×2/×1/×0,5) je Typenpaar danach angewendet
   (Mechanik/04_..., §3–4, exakt übernommen).
2. Restschaden-Formel je Schiffstyp (Mechanik/04_..., §5): `neu = alt +
   Schaden`, `Verluste = floor(neu / Haltbarkeit)`, `Rest = neu -
   Verluste × Haltbarkeit` – der Restschaden lebt nur innerhalb des
   Gefechts (`Battle.attackerResidualDamage`/`defenderResidualDamage`)
   und wird bei Gefechtsende verworfen.
3. Gefecht endet, sobald eine Seite (oder beide gleichzeitig)
   vollständig vernichtet ist. Bei gleichzeitiger beidseitiger
   Vernichtung gilt der Verteidiger als erfolgreich verteidigt
   (Gleichstand-Auflösung).

### 3.3 Rückzug

`retreatFromBattle`: die zurückziehende Seite feuert im letzten Tick
selbst nicht mehr, die Gegenseite aber noch ein letztes Mal – danach
endet das Gefecht sofort mit `outcome: 'Retreat'`, unabhängig vom
Zustand beider Flotten.

### 3.4 Konkrete Faktoren

`COMBAT_DAMAGE_FACTOR = 0,2`, `COMBAT_DURABILITY_FACTOR = 1,0` (erfüllt
die 20-%-Vorgabe aus Mechanik/04_..., §3), `COMBAT_TICK_HOURS = 8` – alle
drei in `engine/formulas.ts`, an dieser einen Stelle austauschbar.

UI: Auf der Flotten-Seite UND in der Systemansicht erscheint bei
stationierten Flotten mit blockierender, gegnerischer (im Krieg
stehender) Flotte im selben System ein „Angreifen“-Button; eine Flotte
in einem laufenden Gefecht zeigt stattdessen einen Kampfstatus-Block
(Tick-Zähler, Countdown bis zum nächsten Tick, letzte eigene Verluste,
„Zurückziehen“-Button) anstelle der regulären Flottenaktionen.

### 3.5 Kampfbericht

Jedes `Battle` trägt ein eigenes, unerratbares `reportToken`
(`randomToken()`, `crypto.randomUUID()` – bewusst NICHT die
fortlaufende, leicht erratbare `id`, siehe `id.ts`). Route
`/kampfbericht/:token` (`BattleReportComponent`) zeigt:

- Kopfzeile: Angreifer/Verteidiger, System, Status, bei laufendem
  Gefecht der Countdown bis zum nächsten Tick, bei beendetem Gefecht
  das Ergebnis.
- Kampfverlauf, Tick für Tick: welche Schiffe je Seite zu Tickbeginn
  noch kampffähig waren (`BattleTickResult.attackerShipsBefore`/
  `defenderShipsBefore` – Mechanik/04_..., §1: alle zu Tickbeginn
  kampffähigen Einheiten nehmen teil, auch wenn sie im selben Tick
  zerstört werden) sowie die daraus resultierenden Verluste GENAU
  dieses Ticks. Vor dem ersten aufgelösten Tick zeigt der Bericht den
  aktuellen (noch unveränderten) Flottenbestand.
- Am Ende (nur bei `status: 'Ended'`): Gesamtverluste je Seite, über
  alle Ticks aufsummiert.

Der Bericht ist AB KAMPFBEGINN abrufbar (nicht erst nach Kampfende) und
wird bei Kampfbeginn UND -ende per Benachrichtigung verlinkt
(`GameNotification.link`, im Benachrichtigungs-Panel als Link
gerendert). Einschränkung dieses Prototyps: die gesamte App liegt
hinter dem Login-Bildschirm (`app.component.html`) – der Link ist also
nur nutzbar, wenn man ohnehin als irgendein Kommandant angemeldet ist,
nicht wirklich anonym von außen abrufbar. Innerhalb dieser Grenze ist
er aber vom anzeigenden Kommandanten unabhängig (`battleByReportToken`
filtert NICHT auf den angemeldeten Kommandanten) und damit teilbar.

## 4. Blockaden

Modell: `Blockade` (`blockade.model.ts`) – stark vereinfacht ggü.
`Mechanik/06_Blockaden_Gefechtsablauf_Aufmarsch.md` (siehe §5). Eine
Flotte ist **nur angreifbar, wenn sie eine Blockade gebildet hat** –
ein bloß "stationiertes" Herumstehen im System macht nicht mehr
angreifbar (Kernänderung ggü. der ursprünglichen `engageBattle`-Fassung
dieses Dokuments).

Zwei Ankerarten (`formBlockade(fleetId, anchor)`):

- **Gateway-Blockade**: Flotte muss am Systemhandelsposten stehen
  (`locationType: 'System'`) – Button „Gateway blockieren“.
- **Planeten-Orbit-Blockade**: Flotte muss im Orbit des jeweiligen
  Planeten stehen (`'PlanetOrbit'` ODER `'ColonyOrbit'` an diesem
  Planeten) – Button „Planet blockieren“, auch bei unbesiedelten
  Planeten möglich.

Höchstens EINE Blockade je Anker (je System für Gateway, je Planet für
Orbit) und höchstens eine Blockade je Flotte. Eine Blockade wird
automatisch aufgehoben, wenn die blockierende Flotte den Ort verlässt
(`moveFleetWithinSystem`) oder im Kampf vollständig vernichtet wird
(`pruneEmptyBlockades`); während eines laufenden Gefechts kann weder
die blockierende Flotte wegbewegt noch die Blockade freiwillig
aufgehoben werden (`liftBlockade`) – zuerst zurückziehen.

UI: Systemansicht (§ Systemansicht-Dokument entfällt, siehe
`SystemViewComponent`) zeigt je Ort (Handelsposten-Panel, jede
Planeten-Karte) eine aktive Blockade an und bietet bei eigenen, dort
stationierten Flotten die passenden Blockade-Buttons.

## 5. Bewusste Vereinfachung – Umfang und Grenzen dieses Schritts

Ggü. der vollständigen Spezifikation in
`Mechanik/06_Blockaden_Gefechtsablauf_Aufmarsch.md` (deutlich größer als
der hier umgesetzte Kern) fehlen weiterhin bewusst:

- Kein Blockade-ANKER-OBJEKT mit voller räumlicher Hierarchie (Gateway
  → Blockadestelle → System → Orbit → Blockadestelle →
  Planetenoberfläche → Blockadestelle → Kolonie) – nur die zwei
  Ankerarten aus §4, ohne verschachtelte Blockadestellen.
- Mobilmachungsrampe (25/50/100 % über mehrere Ticks).
- 10×-Expositionslimit je Tick und Partei.
- Mehrparteien-Gefechte/-Blockaden (nur strikt 1v1, kein "kein Krieg
  zwischen mit-blockierenden Parteien" o. Ä.).
- Schaden-Redistribution bei Overkill – überschüssiger Schaden am Ende
  eines vernichteten Schiffstyps verfällt einfach, statt auf andere
  Typen umverteilt zu werden.
- **Bodenkampf/Invasion**: Bodentruppen (`GroundForceGroup`) werden
  zwar bereits an neue Kommandanten ausgegeben (siehe oben) und eine
  Flotte kann jetzt sogar im Orbit eines fremden, besiedelten Planeten
  eine Blockade bilden, nehmen an einem `Battle` aber NICHT teil – es
  gibt in diesem Schritt keine Landungs-/Invasionsmechanik, nur
  Raumgefechte zwischen Flotten.

Diese Teile bleiben ein möglicher, klar abgegrenzter späterer
Ausbauschritt; die hier umgesetzten Kernformeln (Schaden, Haltbarkeit,
Kontersystem, Restschaden) sind dieselben, die auch das volle System
verwenden würde.

## 6. Persistenz

`DiplomaticRelation[]`, `PeaceOffer[]`, `Battle[]` und `Blockade[]` sind
Teil des `Snapshot` (localStorage-Autosave) wie jeder andere
Weltzustand, mit `?? []`-Fallback beim Laden alter Spielstände ohne
diese Felder (sowie Feld-für-Feld-Fallbacks für `Battle.reportToken`
und `BattleTickResult.attackerShipsBefore`/`defenderShipsBefore`, die
in noch älteren Spielständen ebenfalls fehlen können).
