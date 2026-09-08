# 24. Kolonisationsschiffe und Startbevölkerung

## Ausgangslage

Kolonisieren war bis hierher ein reiner Knopfdruck: `colonizePlanet` prüfte
Besiedelbarkeit, zog 800 Credits ab und stellte die Kolonie im selben Moment
fertig hin – mit 25 Einwohnern aus dem Nichts. Damit war Expansion weder eine
logistische noch eine wirtschaftliche Entscheidung, sondern nur eine Frage von
Kleingeld.

Die Nutzervorgabe ersetzt das durch ein echtes Kolonisationsschiff: es kostet
Bevölkerung, Material und Credits, braucht eine feste Bauwoche, verlangt eine
loyale Heimatkolonie und muss zum Ziel gebracht werden. Kolonien entstehen
dadurch nur noch dort, wo jemand vorher etwas dafür aufgegeben hat.

## A. Startbevölkerung als gemeinsame Konstante

`startPopulation` steht neu in `shared/game-constants.json` (Wert **2000**) und
ist damit für Backend und Frontend dieselbe Zahl
(`GameConstants.START_POPULATION`, `shared-constants.ts`). Sie hat ab sofort
**drei** Bedeutungen, die bewusst identisch sind:

1. Bevölkerung einer frisch registrierten Heimatwelt (`WorldSeed`, vorher fest 120).
2. Kolonisten, die EIN Kolonisationsschiff seiner Bau-Kolonie entzieht.
3. Bevölkerung, mit der die neu gegründete Kolonie startet.

Das ist die eigentliche Aussage der Vorgabe „die StartBevölkerung ist die, die
auch Initial beim Start vorhanden ist": eine per Schiff gegründete Kolonie ist
exakt so groß wie eine Heimatwelt am Tag 1 – kein Zwergensiedlung-Sonderfall
mehr.

**Folgeanpassung der Startausstattung.** Die Startvorräte an Grundnahrung waren
fest auf die alten 120 Einwohner zugeschnitten (5 Stück je Dauerauftrag, 50
Stück Gesamtbestand, 20 Stück Verkaufsorder). Bei 2000 Einwohnern wäre der
Bedarf das 16,7-fache gewesen und die Kolonie sofort im Hunger-Lebensstandard.
Deshalb sind diese drei Werte in `WorldSeed` jetzt **pro Kopf** bemessen
(`STARTER_GOODS_PER_CAPITA_PRODUCTION/_STOCK/_SELL_ORDER`, abgeleitet aus genau
den alten Verhältnissen 5/120, 50/120, 20/120). Das Verhältnis von Produktion,
Vorrat und Verbrauch bleibt damit exakt so wie vorher – die Startkolonie ist
nur 16,7-mal größer, nicht wirtschaftlich anders.

## B. Was das Schiff kostet

| Posten | Wert | Herleitung |
|---|---|---|
| Kolonisten | 2000 | Startbevölkerung, siehe §A |
| Mindestbevölkerung zum Bau | 4000 | „mindestens 2× StartBevölkerung"; nach dem Auszug bleibt genau eine volle Startbevölkerung zurück |
| Kolonistenprämie | 16 000 Cr | `START_POPULATION × Formulas.CREDITS_PER_NEW_INHABITANT` (8 Cr je Kopf, die bereits existierende Konstante) |
| Mindestloyalität | 90 % | Nutzervorgabe – unter diesem Wert findet sich niemand, der auswandern will |
| Bauzeit | 168 Spielstunden | eine Spielwoche, **ohne jeden Bonus** (§D) |
| Material | siehe unten | „so viel wie ein Frachter + 20 Truppentransporter + Baustoffe für Infrastruktur 1 und Wohngebäude 1" |

**Material (Nutzerentscheidung: wertgleich in Rohstoffen, keine fertigen
Schiffe als Vorprodukte).** Die Rezepte von 1× `p_freighter` und 20×
`p_trooptransport` wurden vollständig bis auf Tier-0-Rohstoffe expandiert und
summiert – 16 Rohstoffe, angeführt von `p_kohlenstoff`, `p_silikat` und
`p_salz` (aktuelle Stückzahlen in der Tabelle unten). Dazu kommen die
namentlich genannten Baustoffe der beiden Startgebäude: Infrastruktur Stufe 1
verlangt 2 `p_stahl` + 1 `p_leitermetall`, Wohnkomplex Stufe 1 verlangt 2
`p_stahl` + 1 `p_glaswerkstoff` – zusammen **4 Stahl, 1 Leitermetall, 1
Glaswerkstoff**. Der Vorteil dieser Form: niemand muss erst 21 Schiffe bauen,
`autoProduceMissing` löst die Kette wie bei jedem anderen Auftrag selbst auf.

**`workHoursPerUnit` ist der Montageaufwand, nicht die Kettensumme.** Das Feld
beschreibt DIESEN Schritt – die Arbeitsstunden der Vorprodukte stecken bereits
in den Rohstoffen. Entscheidend ist das aus einem zweiten Grund:
`workHoursPerUnit × baseProductionHours` ist zugleich der Produktionsaufwand,
aus dem `BattleCommands` Schaden UND Haltbarkeit ableitet (Mechanik/04_...,
§2). Mit der Kettensumme wäre das zivile Kolonisationsschiff mit Abstand das
stärkste Kampfschiff des Spiels geworden. Der Wert wird deshalb bewusst so
gewählt, dass der Kampfwert **in der Größenordnung eines Frachters** liegt und
damit unauffällig bleibt.

**Nachgezogen mit der Massenskala (Umsetzungskonzept/27_...md).** Die Regel
oben ist unverändert, ihre Eingänge sind es nicht: ein Frachter wiegt jetzt
40 000 t, ein Mannschaftstransporter 60 000 t. Daraus folgt unmittelbar

| | vorher | jetzt |
|---|---:|---:|
| Masse (= 1 Frachter + 20 Transporter) | 13 573,8 t | **1 240 000 t** |
| Volumen (dieselbe Summe) | 86 438 m³ | **7 863 636 m³** |
| Rohstoffrezept | 20 298 Einheiten | **1 275 076 Einheiten** |
| `workHoursPerUnit` | 6 000 | **9 500** |
| Kampfwert | 1,008 Mio. | 1,596 Mio. (Frachter: 1,6 Mio.) |

Die feste Bauzeit von 168 Spielstunden bleibt unberührt. Das Rohstoffrezept ist
damit der teuerste Einzelposten des Katalogs; ob die Expansionsschwelle in
dieser Höhe bleiben soll, ist in 27_...md, §F als offener Punkt vermerkt.

## C. Bauen (`ShipyardCommands`)

Der Bau läuft über die normale, sequentielle Werft-Warteschlange. Zusätzlich
prüft `queueShip` für `p_colonyship` in `reserveColonists`: Loyalität,
Bevölkerung, Credits. Sind alle drei erfüllt, werden Kolonisten und Prämie
**sofort beim Einreihen** gebunden, nicht erst bei Fertigstellung – sonst stünde
am Ende der Bauwoche womöglich ein fertiges Schiff ohne Besatzung da, weil die
Bevölkerung zwischenzeitlich gesunken ist. `cancelShipOrder` macht beides über
`releaseColonists` rückgängig.

Die Prämie geht vom Kommandanten-Wallet an die **Bevölkerung der Bau-Kolonie**,
wo die Kolonisten bis zum Auslaufen leben. Das ist eine bewusste Vereinfachung
gegenüber einer Prämie, die als Startkapital mitreist (dafür müsste der Betrag
am fungiblen Lagerbestand „Schiff" haften) – so bleibt das Geld im Kreislauf,
statt vernichtet zu werden.

## D. Bauzeit ohne jeden Bonus

`ChainPlanner.computeProductionHours` und die Variante ohne Arbeitskraft-Bremse
kehren für `p_colonyship` sofort mit `COLONY_SHIP_BUILD_HOURS` zurück – vor
jeder Bonusrechnung. Damit wirken weder Werftstufe noch Spezialisierung,
Fördergüte, Blackout noch verfügbare Arbeitskraft. Das folgt der bereits
vorhandenen Sonderregel für `p_soldier` (keine Spezialisierung), nur
konsequenter.

Nicht betroffen ist die **Vorkette**: wer `autoProduceMissing` nutzt, produziert
zusätzlich seine Rohstoffe, und das dauert so lange, wie es dauert. Fix ist die
Bauzeit des Schiffes selbst, nicht die Beschaffung seines Materials.

## E. Landen und gründen (`ColonyCommands`)

`colonizePlanet` verlangt jetzt eine eigene, **im Orbit genau dieses Planeten**
stationierte Flotte mit mindestens einem Kolonisationsschiff
(`fleetWithColonyShipAt`, `Fleet.locationPlanetId`). Das Schiff wird sofort
verbraucht – es IST die neue Kolonie – und es entsteht ein
`Colonization`-Vorgang mit `endsAt = jetzt + COLONIZATION_HOURS` (24
Spielstunden, ein Spieltag).

Bis dahin existiert **keine** Kolonie: der Vorgang ist bewusst ein eigener
Zustand in `state.colonizations` und kein Feld an der Kolonie. Eine halbfertige
Kolonie taucht damit in keiner Übersicht auf, produziert nicht, zählt in keine
Berechnung hinein und kann nicht angegriffen werden. `GameTick` ruft
`processColonizations` auf; bei Fälligkeit entsteht die Kolonie mit
Startbevölkerung, Startbebauung (Wohnkomplex 1 + Industriekomplex 1 +
Infrastruktur 2 – genau die Stufen, deren Baustoffe das Schiff mitgebracht hat)
und einer Benachrichtigung (Code 120).

Die frühere 800-Credit-Gründung entfällt ersatzlos; bezahlt wird jetzt
vollständig über das Schiff.

## F. Bewusst nicht umgesetzt

- **Kein Abbrechen einer laufenden Landung.** Wer `colonizePlanet` auslöst, hat
  das Schiff verbraucht; ein Rückzieher würde bedeuten, es wiederherzustellen.
- **Keine Kolonisten-Rückkehr.** Die 2000 Auswanderer sind für die Bau-Kolonie
  endgültig weg, auch wenn die Zielkolonie später verloren geht.
- **Kein Schutz der Landung.** Der Vorgang läuft unabhängig davon weiter, was im
  System passiert – Blockaden und Gefechte berühren ihn nicht. Eine
  „unterbrechbare Landung" wäre ein eigenes Vorhaben.
- **Keine Transportkapazität.** Das Schiff hat `cargoMassKg = 0`; es bringt
  seine Kolonisten und deren Startausrüstung mit, dient aber nicht als Frachter.
- **Kein eigener Modul-Produktbaum.** Die „speziellen Module" der Vorgabe sind
  in den Materialkosten aus §B abgebildet, statt als eigene Zwischenprodukte
  modelliert zu werden (ausdrücklich „für dich vereinfacht").

## G. Nachtrag: Kolonisieren in jedem System, keine Geisterflotte

Zwei Lücken, die beim Durchspielen der gesamten Kette auffielen (der Nachweis
über die echten Befehle liegt jetzt als `ColonizationJourneyTest` vor: Werft →
Lager → Flotte → Betanken → Gateway-Sprung → Orbit → Gründung).

**Die Aktion gab es nur im Heimatsystem.** `colonizePlanet` war serverseitig
von Anfang an ortsunabhängig – gefordert ist einzig eine eigene Flotte mit
Kolonisationsschiff im Orbit des Zielplaneten. Der einzige Knopf saß aber in
der Kolonienliste, die ausschließlich Planeten des eigenen Startsystems
auflistet. Genau der Zweck des Schiffs, ein ANDERES System zu besiedeln, war
damit über die Oberfläche unerreichbar. Der Knopf sitzt jetzt zusätzlich an
jedem unbesiedelten Planeten der Systemansicht, wo ohnehin schon die eigenen
Flotten und ihre Orte im System stehen. Ob ein Kolonisationsschiff im Orbit
liegt, entscheidet der Client anhand von `ShipTypeDef.class === 'ColonyShip'`
aus dem Schiffskatalog – keine zweite Produktliste im Frontend.

**Das verbrauchte Schiff ließ eine leere Flotte zurück.** Die Schiffsgruppe
wurde auf 0 gesetzt, die Flotte blieb bestehen: sichtbar in jeder Übersicht,
mit einem Tank, der rechnerisch nichts mehr fasst, und – weil `consumeJumpFuel`
bei 0 Schiffen ohne Verbrauch zurückkehrt – beliebig weit springfähig. Der
Verbrauch läuft jetzt über `FleetCommands.consumeShips`: leere Gruppen
verschwinden, und mit dem letzten Schiff verschwindet auch die Flotte (samt
ihrer Blockade, wie beim Ortswechsel). Eine gemischte Flotte verliert dagegen
nur das Kolonisationsschiff.
