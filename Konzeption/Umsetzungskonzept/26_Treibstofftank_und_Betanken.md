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

Er darf aber ausdrücklich **auch als Lager dienen** (Nutzervorgabe): Treibstoff
fließt in beide Richtungen. Die 1.000 je Schiff sind bewusst großzügig bemessen,
weil die Sprungkosten künftig nicht mehr je Schiff, sondern **je Masse**
berechnet werden sollen – dann geht auch die geladene Fracht in den
Treibstoffbedarf ein (siehe §F).

## B. Umschlag in beide Richtungen

Drei eigene Befehle, alle nur mit ganzen Kapseln:

| Befehl | Von → nach | Voraussetzung |
|---|---|---|
| `refuelFleet` | Lager/Depot → Tank | Flotte stationiert an eigener Kolonie ODER an einer Handelsgilde-Station (eigenes Stationsdepot) |
| `drainFleetFuel` | Tank → Lager/Depot | dieselbe |
| `transferFuelBetweenFleets` | Tank → Tank | beide Flotten eigen, stationiert, im SELBEN System |

Bewusst eigene Befehle und kein Nebeneffekt des Reisens: nur so ist eindeutig,
welche Kapseln tatsächlich an Bord und damit für den Verbrauch freigegeben sind.
Vorher konnte man einer Flotte nicht ansehen, ob sie fliegen kann.

Der Flotte-zu-Flotte-Transfer ist der **Rettungsweg für gestrandete Flotten**:
wer ohne Treibstoff irgendwo im Nirgendwo steht, erreicht weder Kolonie noch
Station – eine andere eigene Flotte im selben System kann aber aushelfen.

## C. Die angebrochene Kapsel

Aus dem Tank lassen sich **nur ganze Kapseln** entnehmen. Der Bruchteil im Tank
IST die bereits angebrochene Kapsel: sie ist teilweise verflogen und geht nicht
mehr ins Lager zurück (`drainableFuel` = `floor(fuelCapsules)`).

Das vereinfacht die Buchführung erheblich. Vorher lief der Bruchteil des
Treibstoffverbrauchs über ein Übertragskonto (Umsetzungskonzept/25_...md), weil
ein Sprung weniger als eine ganze Kapsel kostet. Dieses Konto entfällt: der Tank
führt den Bruchteil selbst, und er hat dort sogar eine anschauliche Bedeutung.
Das Lager bleibt trotzdem ganzzahlig, weil Betanken und Abtanken nur ganze
Kapseln bewegen.

Beispiel: 3,4 Kapseln im Tank ⇒ 3 entnehmbar, 0,4 bleiben als angebrochene
Kapsel an Bord.

## D. Verbrauch

`consumeJumpFuel` zieht die Kosten (Schiffe × Sprünge × 0,01 Kapseln) jetzt
ausschließlich aus dem Tank der fliegenden Flotte, und zwar als exakten
Bruchteil. Reicht der Tank nicht, wird der Sprung abgelehnt, bevor die Flotte
losfliegt – mit dem Hinweis, dass betankt werden muss. Entfernte Kolonielager
helfen nicht mehr aus.

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

## F. Als Nächstes: Sprungkosten nach Masse

Die Tankgröße von 1.000 je Schiff ist auf einen noch ausstehenden Schritt hin
gewählt: die Sprungkosten sollen künftig **nicht mehr je Schiff, sondern je
Masse** berechnet werden – dann zählt auch die geladene Fracht mit, und ein
voll beladener Frachter kostet deutlich mehr Treibstoff als ein leerer.

Bis dahin gilt weiter `Schiffe × Sprünge × 0,01`. Bei dieser Rate reicht eine
volle Tankfüllung für 100.000 Sprünge je Schiff, die Tankgröße bindet also
praktisch nie – die eigentliche Grenze ist, wie viele Kapseln ein Kommandant
überhaupt besitzt (Startvorrat: 10). Mit der Massenformel bekommt der Tank seine
eigentliche Bedeutung.
