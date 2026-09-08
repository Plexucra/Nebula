# 28. Truppentransport: Soldaten und Drohnen getrennt

## Ausgangslage

Beim Nachrechnen der Schiffsgrößen (`27_Schiffsgroessen_und_Massenskala.md`)
fiel auf, dass der Mannschaftstransporter überhaupt keine Kapazität hatte:

- In `ships.json` standen für `p_trooptransport` **`cargoMassKg: 0` und
  `cargoVolumeM3: 0`** – nach `FleetCommands.remainingCapacity` konnte er
  buchstäblich nichts laden.
- `GroundForceGroup` hängt an einer **`colonyId`**, nie an einer Flotte. Es gab
  keinen Verladebefehl; Bodentruppen konnten das System nie verlassen.
- `GroundUnitTypeDef.transportSlotUsage` – das Feld, das die Kapazität hätte
  beschreiben sollen – war in Java und im Frontend-Modell deklariert und wurde
  **an keiner Stelle gelesen**.

Dazu war der Katalogwert falsch übertragen. `Mechanik/05_...`, §6 nannte
*leichte Drohne 0,05 / Soldat 1 / mittlere 1 / schwere 20*; in
`ground-units.json` standen Soldat und leichte Drohne **vertauscht** (Soldat
0,05, leichte Drohne 1). Daraus ergab sich pro Slot ein Kampfwert von 960 für
den Soldaten gegen 2,65 für die schwere Drohne – Faktor 362.

## A. Die Entscheidung: zwei Schiffe statt eines Slotmaßes

**Nutzervorgabe: der Mannschaftstransporter nimmt ausschließlich Soldaten auf,
Drohnen reisen als gewöhnliche Fracht im Frachter.** Ein gemeinsames Slotmaß
entfällt ersatzlos.

Der Grund ist die Bauart, nicht das Balancing. Soldaten brauchen über die
gesamte Reise Lebenserhaltung, Unterkunft, Nahrung und medizinische Versorgung
– der Transporter ist im Kern ein fliegendes Habitat. Drohnen sind Maschinen
und brauchen nur Laderaum. Beides in einen Rumpf zu zwingen hieße, für jede
Drohne Lebenserhaltung mitzuschleppen oder für jeden Soldaten einen
unbeheizten Frachtraum.

**Eine Landung braucht deshalb beide Schiffstypen.** Das ist gewollt: eine
Invasionsflotte ist ein zusammengesetztes Gebilde und hat zwei verwundbare
Stellen statt einer.

| | Soldaten | Drohnen |
|---|---|---|
| Schiff | Mannschaftstransporter | Frachter |
| Katalogfeld | `ShipTypeDef.troopCapacity` | `cargoMassKg` / `cargoVolumeM3` |
| Grenze | Kopfzahl | Masse **und** Volumen, wie jede Ware |

## B. Kapazität des Mannschaftstransporters: 1000 Soldaten

Aus der Stückliste ließen sich rund 2000 ableiten: das
`Mannschaftstransporttruppenmodul` enthält 277 Truppenunterbringungsbaugruppen
(je Druckhabitatsegment + Lebenserhaltungsbaugruppe + Panzerbaugruppe, also ein
gepanzerter, lebenserhaltener Unterkunftsblock).

**Der Wert ist auf Nutzervorgabe halbiert: `troopCapacity = 1000`.** Begründung
ist die Verwundbarkeit – bei 2000 hängt zu viel an einem einzelnen Rumpf, und
ein Transporter geht im Raumgefecht so leicht verloren wie jedes andere Schiff
(`BattleCommands` kennt keinen Sonderschutz). Wer eine ernsthafte Landung
plant, muss die Truppe jetzt über mehrere Transporter verteilen und riskiert
bei einem Verlust nur einen Teil davon.

Das passt auch zur Größenskala: 60 000 t Schiff auf 1000 Soldaten sind **60 t
je Soldat**. Reale Amphibienschiffe liegen bei 26 t (USS *America*, 45 000 t
für rund 1700 Marines) bis 31 t (San-Antonio-Klasse) – die tragen aber
zusätzlich Fahrzeuge, Landungsboote und Fluggerät. Ein Schiff, das nichts
außer Menschen transportiert, darf großzügiger ausfallen.

## C. Was ein vollständiger Landungsverband kostet

Ein Soldat kommandiert bis zu fünf Drohnen (`GameConstants.DRONES_PER_SOLDIER
= 5`, `Mechanik/05_...` §3-4). Zu einem vollen Transporter mit 1000 Soldaten
gehören also bis zu 5000 Drohnen. Bei 120 000 t / 250 000 m³ Frachterladung:

| Drohne | Masse | Volumen | je Frachter | Frachter für 5000 |
|---|---:|---:|---:|---:|
| Leicht | 31,5 t | 13,06 m³ | 3 808 | 1,3 |
| Mittel | 60,8 t | 23,00 m³ | 1 974 | 2,5 |
| Schwer | 97,2 t | 33,65 m³ | 1 234 | 4,1 |

Begrenzend ist in allen drei Fällen die **Masse**, nicht das Volumen – der
Frachter ist für Drohnen also nicht überdimensioniert. Ein Landungsverband
besteht damit aus 1 Transporter und 1 bis 4 Frachtern, je nach Drohnenklasse.
Wer schwer landet, braucht die vierfache Frachterflotte.

## D. Katalog und Modell

- `ShipTypeDef.troopCapacity` neu (Java und `fleet.model.ts`), in `ships.json`
  1000 beim Mannschaftstransporter, 0 bei allen anderen.
- `transportSlotUsage` entfernt aus `ground-units.json`,
  `GroundUnitTypeDef.java` und `ground-forces.model.ts`.
- `GroundForceGroup` bekommt neben `colonyId` eine **`fleetId`**. Ein Verband
  steht entweder in einer Kolonie oder an Bord – nie beides. Alle Zugriffe auf
  `colonyId` sind entsprechend nullsicher (`RecruitmentCommands.groundForces`,
  `EconomyTick.recalcCoreStats`); eingeschiffte Truppen zählen damit
  automatisch nicht mehr zur Sicherheit ihrer Herkunftskolonie.
- `Mechanik/05_...` §6 und `04_Bodentruppen_und_Landung.md` §1 auf die neue
  Aufteilung gezogen.

## E. Die Verladung

`TroopTransportCommands` bündelt beide Wege.

**Soldaten.** `embarkSoldiers`/`disembarkSoldiers` bewegen Soldaten zwischen
der Garnison der Kolonie, bei der die Flotte liegt, und dem Verband an Bord.
Grenze ist `troopCapacity`, summiert über alle Schiffe der Flotte. Entnommen
wird zuerst aus der Reserve, erst danach aus den aktiven Einheiten – eine
aktive Verteidigung bleibt so lange wie möglich stehen. Der letzte
ausgeschiffte Soldat löst den Verband an Bord auf, statt eine leere Gruppe
zurückzulassen.

**Drohnen.** `storeDrones`/`deployDrones` verschieben Drohnen zwischen
Garnison und Warenlager. Im Lager sind sie gewöhnliche Ware und damit über
`FleetCommands.loadCargo` verladbar; in der Garnison sind sie Einheiten und
zählen zur Sicherheit. Beides zugleich geht nicht – das ist die ganze
Buchführung, die es braucht. `Mechanik/05_...` §6 behandelt Drohnen beim
Auflösen eines Verbands ohnehin schon als Lagerbestand.

**Die Sperre.** `loadCargo`, `unloadCargo`, `loadCargoFromHubDepot` und
`unloadCargoToHubDepot` rufen alle vier `requireNotASoldier` auf. Ohne diese
Prüfung ließen sich Soldaten – sobald sie auf irgendeinem Weg ins Lager
gelangen – als Ware an `troopCapacity` vorbei verschiffen, und die ganze
Aufteilung wäre umgangen. `storeDrones` weist `p_soldier` ebenfalls ab, damit
sie erst gar nicht ins Lager kommen.

**Anzeige.** `fleetTroopCapacity(fleetId)` liefert Plätze, Belegung und die
tatsächlich einschiffbare Menge fertig berechnet – Gegenstück zu
`fleetCargoCapacity`, aus demselben Grund: die Kapazitätsregel darf nicht ein
zweites Mal im Client stehen (`15_...md`, Auftrag 3). In der Oberfläche sitzt
der Truppenblock direkt unter der Fracht in der Flottenübersicht, das
Ein-/Auslagern der Drohnen im Bodentruppen-Tab der Kolonie.

`TroopTransportTest` deckt die sechs Zusagen ab: nur der Transporter hat
Plätze, die Kapazitätsgrenze hält, eine Flotte ohne Transporter nimmt keine
Soldaten, eingeschiffte Soldaten verlassen die Kolonie und kommen vollständig
zurück, Drohnen fahren den Weg Garnison → Lager → Frachter → Lager → Garnison,
und Soldaten sind auf keinem Weg Fracht.

## F. Noch offen: die Landung

Verladen und verlegen funktioniert damit, **landen noch nicht**. Truppen lassen
sich derzeit nur zwischen eigenen Kolonien verschieben. Es fehlt der Ort
„Planetenoberfläche" samt `LandingDefenseJob` und Bodengefecht; `04_...md` §2
nennt die Endpunkte (`ground-transport-fleets/{id}/land`,
`ground-forces/{groupId}/move`), sie existieren nicht. Das ist der nächste
Schritt und ein eigenes Vorhaben.
