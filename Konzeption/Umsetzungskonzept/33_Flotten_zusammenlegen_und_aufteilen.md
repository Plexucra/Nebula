# 33. Flotten zusammenlegen und aufteilen

Nutzervorgabe und zugleich der letzte offene Punkt aus Konzept 31 §J,
Nummer 11: die Zusammensetzung einer Flotte war bis hierher praktisch
unveränderlich. Schiffe kamen ausschließlich über
`transferShipsToFleet` aus dem Kolonielager hinzu; eine einmal gebildete
Flotte ließ sich nie wieder teilen und zwei Flotten nie vereinigen. Eine
Landungsoperation musste deshalb zwangsläufig als **drei** getrennte Flotten
reisen – Mannschaftstransporter, Drohnenfrachter, Eskorte –, jede mit eigenem
Tank, eigener Reisezeit und eigener Ankunft.

## A. Die beiden Befehle

```text
mergeFleets(targetFleetId, sourceFleetId)
    → Quellflotte wird aufgelöst, Zielflotte enthält alles

splitFleet(fleetId, ships, cargo, soldiers, name)
    → neue Flotte am selben Ort, Ursprungsflotte behält den Rest
```

**Zusammenlegen.** Man wählt eine andere eigene Flotte **am selben Ort**;
Schiffe, Fracht, Treibstoff und eingeschiffte Truppen wandern in die aktuelle
Flotte, die andere verschwindet. „Am selben Ort" heißt dasselbe System **und**
dieselbe Position darin (freier Raum, Planetenorbit oder Kolonieorbit) – ein
Ortswechsel innerhalb des Systems ist ein eigener Befehl
(`moveFleetWithinSystem`) und bleibt es.

**Aufteilen.** Man wählt je Schiffstyp und je Frachtposten eine Stückzahl,
dazu die Zahl der mitfahrenden Soldaten und den Namen der neuen Flotte. Die
neue Flotte entsteht am selben Ort, die Ursprungsflotte behält den Rest und
muss **mindestens ein Schiff behalten** – zum Umbenennen ist kein Aufteilen
nötig.

## B. Die eine Regel, die beides zusammenhält

> Eine Flotte darf nie mehr tragen, als ihre Schiffe fassen.

Beim Zusammenlegen ist das geschenkt: Kapazitäten und Ladung addieren sich
gemeinsam, der Tank ebenso (`JUMP_FUEL_TANK_PER_SHIP` hängt am Schiff). Beim
Aufteilen ist es die eigentliche Prüfung, und zwar **auf beiden Seiten**:

| Prüfung | abgespaltene Flotte | Ursprungsflotte |
| --- | --- | --- |
| Fracht: Masse und Volumen (`cargoMassKg`/`cargoVolumeM3`) | ✓ | ✓ |
| Soldaten: `troopCapacity` (nur der Mannschaftstransporter hat welche) | ✓ | ✓ |

Daraus folgt unmittelbar, was die Oberfläche nicht erklären muss: Wer alle
Frachter mitnimmt, kann die Ware nicht zurücklassen. Wer den einzigen
Mannschaftstransporter mitnimmt, muss auch die Soldaten mitnehmen. Und wer
Soldaten mitgibt, muss Transportplätze mitgeben – Soldaten reisen
ausschließlich im Mannschaftstransporter, Drohnen dagegen als gewöhnliche
Fracht im Frachter (Umsetzungskonzept/28_...md, §A).

Die Fehlermeldungen nennen jeweils die Seite und die konkreten Zahlen
(„Die Ursprungsflotte kann ihre Fracht nicht tragen: 100 t bei 0 t
Kapazität"), damit im Client keine zweite Kapazitätsrechnung nötig ist
(Umsetzungskonzept/15_...md, Auftrag 3).

## C. Was sonst noch mitwandert

- **Treibstoff** geht beim Aufteilen **anteilig** mit: der Tank hängt an den
  Schiffen, also folgt sein Inhalt dem Schiffsverhältnis (3 von 8 Schiffen →
  3/8 der Kapseln). Feinjustieren lässt er sich danach mit dem bereits
  vorhandenen `transferFuelBetweenFleets`.
- **Eingeschiffte Truppen** sind ein `GroundForceGroup` mit gesetzter
  `fleetId` (Konzept 28). Beim Zusammenlegen wird der Verband der Quellflotte
  umgehängt bzw. in den vorhandenen eingerechnet; beim Aufteilen entsteht ein
  zweiter Verband, und ein leer gewordener wird aufgelöst – nie bleibt eine
  leere Gruppe zurück. Soldaten stehen an Bord vollständig in der Reserve
  (an Bord kommandiert niemand Drohnen), genau wie beim Einschiffen.
- **Verkaufsorders aus Flottenfracht** (`SellOrder.sourceFleetId`) werden beim
  Zusammenlegen auf die Zielflotte umgehängt. Sonst verlöre ihr Auto-Relist
  seine Quelle: `MarketCommands.reserveForRelist` prüft Flotte, System und
  Landeort.
- **Blockaden**: die aufgelöste Flotte verliert ihre Blockade (dieselbe Regel
  wie beim Ortswechsel). Die Zielflotte behält ihre – sie wird nur stärker.

## D. Was nicht geht

- Flotten **unterwegs** (`InTransit`) oder **in einem laufenden Gefecht**
  lassen sich weder teilen noch zusammenlegen. Im Gefecht wäre es ein Rückzug
  durch die Hintertür (dieselbe Begründung wie in Konzept 30 §E für
  Bodentruppen), unterwegs gäbe es keinen gemeinsamen Ort.
- Fremde Flotten – beide müssen demselben Kommandanten gehören.
- Bruchteile: nur ganze Schiffe, ganze Frachtstücke, ganze Soldaten
  (Umsetzungskonzept/25_...md).

## E. Umsetzung

- `FleetCompositionCommands` (neu, `de.nebula.state`) mit `mergeFleets` und
  `splitFleet`. Bewusst eine eigene Klasse statt weiterer 200 Zeilen in
  `FleetCommands` (dort steht die Portierung des TS-Originals; dies ist ein
  neues Feature).
- `FleetCommands.cargoCapacityOf`/`cargoUsedOf` und
  `TroopTransportCommands.troopCapacity(List<FleetShipGroup>)` rechnen jetzt
  auf einer **Schiffsliste** statt nur auf einer bestehenden Flotte – nötig,
  weil beim Aufteilen die künftigen Zusammensetzungen geprüft werden, bevor es
  die zweite Flotte gibt. Die bisherigen Flotten-Überladungen delegieren
  dorthin, die Regel steht weiterhin an genau einer Stelle.
- WebSocket-Befehle `mergeFleets` und `splitFleet` (letzterer liefert die neue
  Flotte zurück), `GameApi` in beiden Frontend-Fassungen.
- Oberfläche: zwei zusätzliche Knöpfe je stationierter Flotte in der
  Flottenübersicht. „Zusammenlegen" erscheint nur, wenn es überhaupt eine
  eigene Flotte am selben Ort gibt; „Aufteilen" öffnet ein Formular mit einer
  Zeile je Schiffstyp, je Frachtposten und – wenn Soldaten an Bord sind – für
  die Soldaten, plus Namensfeld.
- `FleetCompositionTest`: Zusammenlegen überträgt Schiffe, Fracht, Tank und
  Truppen und löst die Quelle auf; verschiedener Ort und Flotte unterwegs
  werden abgelehnt; Aufteilen verschiebt genau das Gewählte samt anteiligem
  Treibstoff; beide Kapazitätsseiten werden geprüft (Fracht und Soldaten);
  nach einem Fehler ist nichts verschoben; Restflotte ohne Schiffe wird
  abgelehnt; leerer Name wird ersetzt.

## F. Folgemöglichkeit für die Bot-Armee

Die Landungsoperation der NPC-Bots (`Military`, Konzept 31 §E) führt heute
drei Flotten getrennt ans Ziel und wartet, bis alle drei angekommen sind – im
Testlauf blieb genau daran einmal ein voll beladener Transporter zurück, weil
sein Tank leer war. Mit `mergeFleets` ließe sich vor dem Abflug **eine**
Invasionsflotte bilden (Transporter + Frachter + Eskorte) und nach der Landung
per `splitFleet` wieder trennen. Bewusst noch nicht umgesetzt: die
Bot-Änderung gehört in einen eigenen Durchgang mit eigenem Testlauf.
