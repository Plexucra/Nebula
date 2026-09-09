# 32. Energiespeicher und Versorgungswarnung

Zwei Nachträge zu den Befunden aus Konzept 31 (§J, Vorschläge 3 und 4),
beide auf Nutzerentscheidung.

## A. Der Energiespeicher (Bebauung, Teil der Infrastruktur)

### Das Problem

Der Kettenplaner deckt jeden Zwischenschritt eines Auftrags zuerst aus dem
Kolonielager. Stabilisiertes Elerium ist Zutat der Sprungtreibstoffkette und
steckt damit in jedem Schiff: ein Mannschaftstransporter zieht 188 Stück,
ein Zerstörer 455 – beim Start des Auftrags, komplett. Danach belegt der
Auftrag die einzige Warteschlange für Tage, in denen kein Elerium nachkommt.
So starben im Live-Spiel alle zwanzig Bot-Kolonien (Konzept 31 §A), und im
Testlauf lief die Notfalllogik des Bots in einer Endlosschleife (§G,
Befund 9).

### Die Entscheidung

**Kein eigenes Gebäude.** Der Speicher kommt mit der Infrastruktur und wird
in der Bebauung bei ihr angezeigt und konfiguriert. Konfiguriert wird genau
eine Größe: die **Vorhaltemenge** – wie viel Stabilisiertes Elerium für das
Energienetz reserviert bleibt.

```text
Zufluss:   jedes Stabilisierte Elerium, das die Kolonie erreicht
           (Produktion, Entladung, Zukauf, Eroberung)
           → zuerst in den Speicher, bis die Vorhaltemenge erreicht ist
           → der Rest ins Lager
Abfluss:   nur die Infrastruktur, zuerst aus dem Speicher, dann aus dem Lager
Sichtbar:  Produktionsketten sehen ausschließlich das Lager
Senken:    Vorhaltemenge kleiner als Vorrat → Überschuss wandert ins Lager
```

Ohne Konfiguration ist die Vorhaltemenge **automatisch**: der Verbrauch der
aktuellen Infrastrukturstufe über `energyReserveDefaultGameHours`
(`shared/game-constants.json`, 240 Spielstunden = 10 Spieltage) – sie wächst
also mit jeder Stufe mit. Eine feste Einstellung bleibt, bis der Kommandant
wieder auf „automatisch" stellt.

### Umsetzung

- `EnergyStorage` (Modell: `colonyId`, `stored`, `reserveTarget` mit `null` =
  automatisch) in `GameState.energyStorages`, angelegt beim ersten Zugriff;
  `EnergyStorageView` als Anzeige-Fassung mit Reichweite und Lagerbestand.
- `EnergyStorageCommands`: `intake` (Zufluss), `drawForUpkeep` (Abfluss),
  `setReserve`, `view`, `defaultTarget`/`effectiveTarget`.
- **Der Haken sitzt in `Warehouse.add`**: jeder positive Zugang von
  `p_elerium_stabil` läuft durch `intake`, egal aus welcher Quelle. Entnahmen
  und der Rückfluss aus dem Speicher nutzen `addRaw`. `Warehouse.qty` liest
  weiterhin nur das Lager – genau deshalb sieht der Kettenplaner den Speicher
  nicht, ohne dass er selbst angefasst werden musste.
- `EconomyTick.consumePowerUpkeep` misst die Deckung an Speicher **plus**
  Lager und zieht die fällige Zelle zuerst aus dem Speicher.
- Eroberung (`ColonyConquest`): der Speicher erleidet denselben Materialschaden
  wie das Lager; bei der Eingliederung wandert sein Inhalt mit und füllt beim
  Ziel zuerst dessen Speicher.
- `supplyInventory` weist beim Elerium die vorgehaltene Menge gesondert aus
  (`reserved`) und rechnet sie in die Reichweite ein.
- WebSocket: `energyStorage(colonyId)`, `setEnergyReserve(colonyId,
  reserveTarget | null)`; Frontend: `GameApi` beidseitig, Anzeige samt
  Eingabe in der Infrastruktur-Zeile des Tabs „Bebauung".
- `EnergyStorageTest`: Zufluss füllt zuerst den Speicher, Ketten sehen nur das
  Lager, Abfluss zuerst aus dem Speicher, Senken gibt Überschuss frei, Automatik.

### Was das für die Bots heißt

Die Energie-Wache des Bots (Konzept 31 §G, Befund 9) muss den Kettenbedarf
nicht mehr vorrechnen: vor einem großen Auftrag setzt sie die Vorhaltemenge
auf den Infrastrukturverbrauch über die Laufzeit des Auftrags plus zehn Tage
und wartet, bis der Speicher voll ist (`ENERGY_RESERVE_SET`, `ENERGY_GUARD`).
Die Kette nimmt danach nur, was im Lager liegt, und produziert den Rest.

## B. Versorgungswarnung

Konzept 31 §J, Vorschlag 4 (Konsumpreis gegen Kaufkraft): Nutzerentscheidung
ist, dass die Preispolitik Sache des Kommandanten bleibt – wer seine
Bevölkerung ausnimmt, ist selbst schuld –, er aber gewarnt wird.

`EconomyTick.warnAboutSupplyGaps` (Code **505**, Typ Problem) meldet je
Kolonie und Grundbedarfsgut höchstens einmal je Spieltag, sobald die Deckung
unter 50 % fällt, und unterscheidet die zwei Ursachen:

- **keine Verkaufsorder im System** – „die Bevölkerung kauft nur aus Orders
  ihres Systems, der Lebensstandard bleibt ohne dieses Gut gedeckelt";
- **Order vorhanden, aber unbezahlbar** – „Preis der Verkaufsorder prüfen, das
  Bevölkerungs-Wallet gibt nicht mehr her".

Die Benachrichtigung verlinkt auf die Kolonie.

### Ist das ein konzeptionelles Grundproblem?

Kein grundsätzliches, aber ein struktureller Haken, den die Warnung nur
sichtbar macht: `runConsumption` teilt das Budget der Bevölkerung **vorab in
Drittel** auf die drei Güter. Fehlt ein Gut ganz (Elektronik ist für eine
Startkolonie weder herstellbar noch bezahlbar, Konzept 31 Befund 5), bleibt
sein Drittel dauerhaft im Bevölkerungs-Wallet liegen – ein Drittel der Löhne
kommt nie zum Kommandanten zurück, unabhängig von jeder Preispolitik. Im
Testlauf fielen so Kommandanten mit 20 000 Einwohnern von 150 000 auf unter
1 000 Cr, sobald das Wachstum an der Wohnkapazität endete. Zwei Auswege, beide
Designentscheidungen: das Budget nur auf **angebotene** Güter verteilen
(dann kauft die Bevölkerung mehr Nahrung und Medizin, Lebensstandard-Gewichte
unverändert), oder die Geldschöpfung nicht allein ans Wachstum binden. Die
Warnung deckt den Fall ab, sagt aber nichts über das Drittel.

## C. Zur Frage der Bauzeiten (Konzept 31 §J, Vorschlag 1)

Die Nachfrage lautete: warum nicht erst die Industrie maximal ausbauen und
dann mit voller Spezialisierung produzieren – und ist 20 000 Bevölkerung
nicht ohnehin ein Dorf? Die Zahlen (sequentielle Kette in Spielstunden,
`Formulas.buildingLevelSpeedFactor` linear, Spezialisierung +10 % je Stufe
auf **allen** Schritten unterstellt, was praktisch nie erreichbar ist, weil
sie je Produkt getrennt wächst und verfällt):

| Produkt | Industrie 5, ohne Spez. | Industrie 10, Spez. 10 | Industrie 30, Spez. 10 | Industrie 30, Spez. 50 | Arbeitsstunden | Untergrenze 20 000 Bev. | 200 000 | 2 Mio. |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Korvette | 53 793 | 13 460 | 4 503 | 1 501 | 5,4 Mio. | 268 h | 27 h | 3 h |
| Zerstörer | 403 815 | 100 978 | 33 691 | 11 230 | 41,0 Mio. | 2 048 h | 205 h | 20 h |
| Transporter | 261 950 | 65 512 | 21 869 | 7 290 | 26,9 Mio. | 1 346 h | 135 h | 13 h |
| Kolonisationsschiff | 255 072 | 63 776 | 21 270 | 7 090 | 127,5 Mio. | 6 376 h | 638 h | 64 h |

Drei Aussagen daraus:

1. **Industrie und Spezialisierung wirken linear und reichen nicht.** Selbst
   Industrie 30 (ein großer Planet lässt bis 36 Infrastrukturstufen zu, jede
   Industriestufe braucht eine) mit Spezialisierung 10 auf allen ~100
   Zwischenprodukten liefert eine Korvette in 4 500 Stunden = 188 Spieltage.
   Die Kette hat 98 verschiedene Produkte in 9 Ebenen; Spezialisierung Stufe 10
   verlangt 168 Stunden **Exklusiv**produktion je Produkt.
2. **Die Bevölkerung ist die zweite Schranke, und 20 000 sind tatsächlich ein
   Dorf.** Die Arbeitskraft-Bremse (Konzept 19) macht aus 5,4 Mio.
   Arbeitsstunden bei 20 000 Arbeitern mindestens 268 Stunden je Korvette –
   egal wie schnell die Anlagen sind. Mit 2 Mio. Einwohnern (Wohnkomplex 8;
   jede Stufe verdoppelt) sind es 3 Stunden. Die Wohnkapazität ist im Spiel
   also bereits das richtige Werkzeug für „Stadt statt Dorf"; die Bots haben
   sie bisher bei Stufe 2 belassen (jetzt bis Stufe 4 = 160 000).
3. **Beides zusammen entscheidet nicht der Bot, sondern der Katalog.** Die
   Kette einer Korvette kostet 2 000-mal so viel Arbeit wie ihre Endmontage.
   Wenn eine Millionenstadt mit Industrie 30 eine Korvette in Tagen bauen
   soll, müssen `baseProductionHours` und `workHoursPerUnit` der
   Zwischenprodukte um ein bis zwei Größenordnungen sinken (Konzept 31 §J,
   Vorschlag 1) – die Kampfwerte bleiben davon unberührt. Parallele
   Fertigungsslots (Vorschlag 2) würden die sequentielle Summe zusätzlich auf
   den kritischen Pfad drücken (Korvette: 445 statt 268 965 unskalierte
   Stunden – Faktor 600).

Kurz: Koordination und starke Industrie drücken die Zeit linear, die
Bevölkerung ebenfalls; die Größenordnung des Katalogs drückt keiner von
beiden weg.
