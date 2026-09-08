# 25. Ganze Stückzahlen und Übertragskonten

## Ausgangslage

Im Spiel bewegten sich Waren in beliebigen Bruchteilen. Sichtbar wurde das an
Lagerbeständen wie `358,76574737337415` Stabilisiertes Elerium oder Orders mit
`19,9904` Grundnahrung – Zahlen, die niemand lesen will und die jede
Mengenangabe in der Oberfläche unbrauchbar machen.

Die Nutzervorgabe: es soll durchgängig nur ganze Stücke geben – gekauft,
gelagert, verrechnet – und Bruchteile sollen im Zweifel verfallen.

## A. Warum naives Runden nicht funktioniert

Der entscheidende Punkt ist die Unterscheidung zwischen einem **Rest** und
einer **Rate**.

Ein Rest ist einmalig: die anteilige Gutschrift beim Abbruch eines Auftrags,
die ladbare Restmenge, eine Teilausführung. Ihn abzuschneiden ist harmlos, und
der Code tat das über `Math.floor` ohnehin schon.

Eine Rate ist etwas anderes. Der Nahrungsbedarf einer Kolonie mit 120
Einwohnern beträgt 0,0096 Stück je Tick – ein ganzes Stück also erst alle 104
Ticks. Der Elerium-Verbrauch der Infrastruktur liegt bei Stufe 6 bei 0,0188 je
Tick, ein Stück alle 53 Ticks. Ein Sprung kostet je Schiff 0,01 Kapseln.

| Verhalten | Folge |
|---|---|
| Abrunden | Der Vorgang findet **nie** statt: die Bevölkerung kauft für immer nichts und verhungert, die Infrastruktur verbraucht nie Elerium |
| Aufrunden | Explosion: aus 0,05 Stück Bedarf wird eine ganze Einheit je Sekunde. Genau das gab es hier schon einmal – der Grabstein steht als Kommentar in `EconomyTick.runConsumption` |

Und es lässt sich **nicht** über die Stückgröße lösen, weil die Rate mit der
Koloniegröße skaliert: dieselbe Nahrung ist bei 120 Einwohnern ein Stück alle
104 Ticks, bei 12.500 Einwohnern exakt eines pro Tick. Jede Stückgröße, die für
große Kolonien passt, lässt kleine verhungern.

## B. Übertragskonten (`FractionPot`)

Deshalb werden Bruchteile gesammelt statt gerundet. `FractionPot` bucht einen
Betrag auf ein Konto je Kolonie und Zweck und gibt zurück, wie viele GANZE
Stücke daraus fällig sind; der Rest bleibt liegen. Abgeschnitten wird Richtung
Null, damit negative Raten (schrumpfende Bevölkerung) symmetrisch funktionieren.

Nach außen bewegt sich dadurch immer nur Ganzes, während die langfristige Rate
exakt erhalten bleibt – **nichts geht verloren, es wird nur aufgeschoben**. Die
Bruchteile sind auf `GameState.fractionPots` isoliert und tauchen in Lager,
Orders und Fracht nicht mehr auf.

Vier Flüsse nutzen ein Konto:

| Zweck | Schlüssel | Rate |
|---|---|---|
| Bevölkerungskonsum | `consume:<kolonie>:<produkt>` | Einwohner × Pro-Kopf-Bedarf je Tick |
| Energieversorgung | `power:<kolonie>` | `infrastructureEleriumPerHour(Stufe)` × Tickdauer |
| Bevölkerungswachstum | `population:<kolonie>` | `populationGrowthDelta` × Tickdauer |
| Sprungtreibstoff | `jumpfuel:<kommandant>` | Schiffe × Sprünge × 0,01 Kapseln |

Beim Sprungtreibstoff wird bewusst erst GEPRÜFT und dann gebucht – sonst wäre
der Anspruch bereits abgebucht, wenn die Reise mangels Vorrat abgelehnt wird.

## C. Lebensstandard bleibt feinfühlig

Würde man die Versorgungslage an der gestückelten Kaufmenge messen, spränge der
Lebensstandard nur alle 104 Ticks. Deshalb sind die beiden Dinge getrennt:

- **Versorgungslage** (`purchasableQuantity`) wird JEDEN Tick am bruchteiligen
  Bedarf gemessen und beantwortet: *hätte* die Bevölkerung kaufen können, was
  sie gerade braucht? Daraus entsteht wie bisher die Deckung je Gut und der
  geglättete Lebensstandard.
- **Warenbewegung** passiert ausschließlich in ganzen Stücken über das
  Übertragskonto.

Die Deckungszahl misst also den Markt, nicht die Stückelung.

## D. Ganzzahligkeit an den Rändern

Damit im Lager nie ein Bruchteil landet, werden die Mengeneingaben an den
Befehlsgrenzen auf ganze Stücke festgelegt: `queueProduction`,
`queueRecruitment`, `loadCargo`, `unloadCargo`, `loadCargoFromHubDepot` und
`unloadCargoToHubDepot`. Der Kettenplaner rechnet von dort mit ganzzahligen
Rezeptmengen weiter – alle Rezepte im Katalog sind bereits ganzzahlig, keines
hat Nachkommastellen. Hub-Orders und Verkaufsorders rundeten schon vorher ab.

Abgesichert ist das durch `IntegerQuantitiesTest`: er lässt 400 Wirtschafts-
Ticks laufen und prüft, dass Lagerbestände, Bevölkerung und Order-Restmengen
ganzzahlig bleiben, dass kein Topf je ein ganzes Stück hält, und – als
Gegenprobe – dass der Kreislauf trotzdem anläuft und der Lebensstandard steigt.
Zwei weitere Tests belegen, dass der langfristige Verbrauch von Elerium und
Sprungtreibstoff der Rate entspricht.

## E. Versorgungsinventar

Neu ist die Abfrage `supplyInventory(colonyId)` und ein gleichnamiges Panel in
der Koloniedetailansicht. Es zeigt bewusst mehr als einen rohen Lagerauszug,
weil die interessante Frage nicht „wie viel habe ich" ist, sondern „wie lange
komme ich damit hin": je Position Bestand, Verbrauch je Spielstunde,
Reichweite in Spielstunden und den offenen Rest im Übertragskonto. Letzteres
macht die Buchführung nachvollziehbar – man sieht, dass der fehlende Bruchteil
nicht verschwunden ist, sondern wartet.

Beispiel aus einem laufenden Spiel:

```
Grundnahrung              500   -0,434/h, reicht 1152,1 h, offen 0,339
Eleriumkapsel              10   kein laufender Verbrauch
Stabilisiertes Elerium     25   -0,047/h, reicht 532,5 h, offen 0,826
```

## F. Bewusst nicht umgesetzt

- **Kein globaler Ganzzahl-Typ.** Mengen bleiben `double`. Die Ganzzahligkeit
  ist eine Invariante, die an den Rändern durchgesetzt und per Test überwacht
  wird – ein Typwechsel hätte jede Signatur im Backend berührt, ohne mehr zu
  garantieren.
- **Keine Stückgrößen-Normierung.** Dass ein Stück Elerium 0,6 Gramm wiegt und
  ein Stück Atmosphäre 5.000 Tonnen, bleibt vorerst so. Das ist eine eigene
  Frage (siehe die offene Diskussion zu Einheiten) und unabhängig davon, ob
  Mengen ganzzahlig sind.
- **Keine Rückkehr abgeschnittener Reste.** Kann ein fälliges Stück nicht
  gekauft oder bezahlt werden, verfällt es – wie vom Nutzer vorgegeben. Der
  Topf wurde dabei bereits geleert, der Bedarf wird also nicht doppelt gestellt.
