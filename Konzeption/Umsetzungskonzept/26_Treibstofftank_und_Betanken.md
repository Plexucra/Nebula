# 26. Treibstofftank und Betanken

## Ausgangslage

Sprungtreibstoff wurde bis hierher aus den Kolonielagern des Flottenbesitzers
gezogen – Heimatkolonie zuerst, dann die übrigen –, **unabhängig davon, wo die
Flotte gerade stand**. Eine Flotte am anderen Ende der Galaxie tankte also aus
einem Lager, das sie nie erreichen konnte. Damit war nirgends erkennbar, ob eine
Flotte tatsächlich fahrbereit ist: der Treibstoff existierte nur als abstrakter
Anspruch auf ein entferntes Lager.

## A. Der Tank

Jede Flotte hat jetzt einen eigenen Treibstofftank (`Fleet.fuelCapsules`) mit
einem Fassungsvermögen von `JUMP_FUEL_TANK_PER_SHIP` = **1.000 Eleriumkapseln
je Schiff** (`shared/game-constants.json`, also dieselbe Zahl für Backend und
Frontend). Eine Flotte aus sieben Schiffen fasst somit 7.000 Kapseln.

Der Tank ist **keine Fracht**:

- Er belegt weder Masse- noch Volumenkapazität. `fleetCargoCapacity` und
  `loadCargo` sehen ihn nicht.
- Er taucht nicht in `fleet.cargo` auf und lässt sich nicht per `unloadCargo`
  anrühren.

## B. Betanken ist ein eigener Befehl

`refuelFleet(fleetId, quantity)` verschiebt ganze Kapseln aus dem Lager der
Kolonie, bei der die Flotte **gelandet** ist, in ihren Tank. Voraussetzungen:
Flotte stationiert, an einer eigenen Kolonie angedockt, genug Kapseln im Lager,
genug Platz im Tank.

Bewusst ein eigener Befehl und kein Nebeneffekt des Reisens: nur so ist
eindeutig, welche Kapseln tatsächlich an Bord und damit für den Verbrauch
freigegeben sind. Vorher konnte man einer Flotte nicht ansehen, ob sie fliegen
kann.

## C. Warum es kein Ausladen gibt

Es gibt **kein Gegenstück** zum Betanken – das ist die zentrale Designregel
dieses Konzepts, nicht eine vergessene Funktion.

Der Tank fasst 1.000 Kapseln je Schiff und belegt keine Frachtkapazität. Könnte
man ihn am Ziel wieder leeren, wäre er ein zweiter, weit größerer Frachtraum an
jedem Schiff: ein einzelner Frachter würde 1.000 Kapseln kostenlos und ohne
Anrechnung auf Masse oder Volumen transportieren, und der eigentliche Frachtraum
wäre bedeutungslos. Treibstoff verlässt den Tank deshalb ausschließlich durch
Fliegen.

Wer Kapseln als Handelsware bewegen will, lädt sie ganz normal als Fracht – dann
zählen sie wie jede andere Ware gegen Masse und Volumen, sind aber nicht als
Treibstoff nutzbar, bis sie ausgeladen und getankt werden.

## D. Verbrauch

`consumeJumpFuel` zieht die Kosten (Schiffe × Sprünge × 0,01 Kapseln) jetzt
ausschließlich aus dem Tank der fliegenden Flotte. Reicht er nicht, wird der
Sprung abgelehnt, bevor die Flotte losfliegt – mit dem Hinweis, dass betankt
werden muss. Entfernte Kolonielager helfen nicht mehr aus.

Das Übertragskonto für Bruchteile (Umsetzungskonzept/25_...md) hängt seitdem an
der **Flotte** statt am Kommandanten (`jumpfuel:<fleetId>`), weil der Treibstoff
jetzt einer bestimmten Flotte gehört.

Verliert eine Flotte im Gefecht Schiffe, sinkt ihr Fassungsvermögen. Überzähliger
Treibstoff verfällt beim nächsten Flug – ein bewusst simpler Umgang mit einem
seltenen Randfall.

## E. Startausstattung und Bots

Startflotten laufen mit `STARTER_FLEET_FUEL` = 5 Kapseln betankt aus; ohne das
stünde ein frischer Kommandant vor einer Flotte, die sich erst nach einem
Betankungsbefehl bewegen kann. Das Kolonielager behält seine 10 Kapseln zum
Nachtanken.

Die NPC-Bots betanken vor jeder Abreise (`Bot.topUpFuel`), und zwar bevor sie zur
Handelsgilde-Station aufbrechen – an der Station gibt es keine eigene Kolonie,
die Hin- und Rückreise muss also aus einer Tankfüllung bestritten werden.
Fehler beim Betanken werden geschluckt: ein voller Tank oder ein leeres Lager
sind normale Zustände.

## F. Offener Balancing-Punkt

Bei 0,01 Kapseln je Schiff und Sprung reicht **eine** volle Tankfüllung für
100.000 Sprünge je Schiff. Die Tankgröße von 1.000 wird damit praktisch nie
binden – die eigentliche Grenze bleibt, wie viele Kapseln ein Kommandant
überhaupt besitzt (Startvorrat: 10). Wer will, dass der Tank als Reichweiten-
Begrenzung spürbar wird, müsste entweder den Verbrauch je Sprung deutlich
anheben oder die Tankgröße drastisch senken. Bewusst nicht selbständig
geändert – die Nutzervorgabe nennt 1.000 ausdrücklich.
