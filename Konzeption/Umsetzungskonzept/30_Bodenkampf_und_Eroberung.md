# 30. Bodenkampf, Belagerung und Eroberung

Setzt `Mechanik/05_Bodentruppen_und_Bodenkrieg.md` §2, §3-4 und §10-12 um –
genau den Durchgang, den `LandingCommands` (Umsetzungskonzept/04_...) mit dem
Kommentar „das bleibt ein eigener, späterer Durchgang" offengelassen hatte –
zuzüglich der **Belagerungsregel** (Nutzervorgabe), die §10 konkretisiert.
Landung und Landungsabwehr (§7-8) standen bereits; was fehlte, war alles
danach.

## Der Ablauf in einem Satz

Ein Angriff auf eine Kolonie ist zunächst dasselbe wie ein Angriff auf eine
Blockade – reguläre Kampfticks, so viele wie Gegenwehr da ist. **Sind alle
militärischen Gegner besiegt, endet das Gefecht aber nicht**, sondern geht in
Belagerungsticks über; erst wenn die Loyalität der Bevölkerung unter 2 %
gedrückt ist, wechselt die Kolonie den Eigentümer.

```text
Kampfticks  ──(keine aktivierbaren Drohnen mehr)──>  Belagerungsticks
     ^                                                      │
     └──────────(neue kampffähige Verteidigung)──────────────┘
```

Der Wechsel wird bei **jedem** Tick neu entschieden und geht in beide
Richtungen (`GroundBattlePhase`, `GroundBattleCommands.resolveTick`).

## A. Phase 1: derselbe Gefechtskern wie im Raum

Mechanik/05_..., §3 beschreibt die drei Drohnenklassen ausdrücklich als „ein
geschlossenes Kontersystem, **analog zu den Schiffsklassen**". Der Bodenkampf
verwendet deshalb dieselben Kernformeln wie `BattleCommands` (Mechanik/04_...,
§2-5) und keine zweite, eigene Kampfrechnung:

- militärischer Wert = Produktionsaufwand (`Formulas.productionAspect`),
- Gruppenschaden proportional zum Kostenanteil des Ziels verteilt,
- Kontermatrix ×2 / ×0,5 (leicht schlägt schwer, schwer schlägt mittel,
  mittel schlägt leicht – aus `ground-units.json`),
- Restschaden-Konto je Einheitentyp,
- Kampftick alle 8 Spielstunden (`Formulas.COMBAT_TICK_HOURS`).

Eigen ist am Boden nur, was die Konzeption eigens nennt:

| Regel | Quelle | Umsetzung |
| --- | --- | --- |
| Nur **aktive** Drohnen kämpfen und sind Ziel; Reserven nehmen nicht teil und können nicht getroffen werden | §3 | `computeSideDamage`/`applyDamage` lesen und schreiben ausschließlich `activeCount` |
| Soldaten haben **keine** eigene Kampfwirkung | §3 | Soldaten sind weder Schadensquelle noch Ziel |
| Fallende Drohnen reißen ihre Soldaten mit | §4 | `Formulas.soldiersLostWithDrones`, abgerundet |
| Nachrückende Soldaten/Drohnen aktivieren sich mitten im Gefecht | §4 | nach jedem Tick `recalcCrewing` auf **beiden** Seiten |
| Besiegt, sobald keine **aktivierbaren** Waffenträger mehr da sind | §10 | Übergang in die Belagerung, wenn `activeDroneCount == 0` nach dem Nachaktivieren |

### Eine Lücke, die dabei auffiel

`recalcCrewing` war auf die **Kolonie** definiert, nicht auf den Verband. Ein
frisch gelandeter Verband (`LandingCommands.land`) hatte deshalb **null aktive
Drohnen** – nach §10 sofort kampfunfähig, und das ohne dass es je aufgefallen
wäre, weil es bis jetzt nichts gab, wofür „aktiv" am Boden eine Rolle spielte.
`recalcCrewing(GroundForceGroup)` gilt jetzt für jeden Verband; `land` ruft es
auf.

## B. Phase 2: die Belagerung (Nutzervorgabe)

Sobald die militärische Gegenwehr gebrochen ist, kehrt sich alles um. Es zählen
**ausschließlich die Soldaten** des Angreifers gegen aufständische Zivilisten –
keine Drohnen, kein Material. Und ausdrücklich: Soldaten sind hier **Akteure**,
nicht Drohnenbediener; gezählt werden deshalb alle Soldaten des Verbands,
aktive wie Reserve.

Das ist bewusst eine **reine Rechenoperation ohne Kampfwerte**. Soldaten haben
nach §3 keine eigene Kampfwirkung, und es sollen für sie auch keine erfunden
werden – die Belagerung wird stattdessen über drei Verhältniszahlen
entschieden.

### 1. Loyalitätsverlust

```text
Senkung je Belagerungstick (ABSOLUT, in Prozentpunkten)
    = 100 × Soldaten des Angreifers / Bevölkerung der Kolonie
```

1000 Soldaten gegen 10 000 Einwohner = 10 % ⇒ Loyalität 19 % wird zu 9 %.
Die Bevölkerung wird jeden Tick neu gelesen: fallen Rebellen, steigt das
Verhältnis, und die Belagerung beschleunigt sich von selbst.

### 2. Der Aufstand

```text
Rebellen je Tick     = 10 % der Bevölkerung
Kampfkraft Rebell    = 30 % eines Soldaten
Verluste einer Seite = floor(Kämpfer × Stärke / 12)
```

Zwölf Kämpfer voller Stärke töten je Tick einen Gegner. Weil Soldaten mit
voller und Rebellen nur mit 30 % Stärke rechnen, sterben entsprechend mehr
Rebellen als Soldaten. Am Beispiel oben: 1000 Soldaten gegen 1000 Rebellen ⇒
83 tote Rebellen, 25 tote Soldaten. Gefallene Rebellen sind Zivilverluste und
fließen in die Quote aus §2 ein – eine lange Belagerung macht die Beute
entsprechend kaputter.

### 3. Kapitulation

```text
Loyalität < 2 %  ⇒  die Kolonie geht an den Angreifer über
```

Vorher passiert nichts: der Fall der Garnison allein erobert gar nichts.
Umgekehrt kann auch der Angreifer die Belagerung verlieren – reiben die
Aufständischen seine Soldaten auf, ist sein Verband erledigt (ohne Soldaten
ist auch keine Drohne mehr kommandierbar).

Eine Folge, die bewusst so stehen bleibt: **eine zu kleine Belagerungstruppe
gewinnt nie.** Sinkt die Loyalität je Tick um weniger, als die reguläre
Loyalitätsentwicklung (`EconomyTick.recalcCoreStats`) sie wieder anhebt, läuft
die Belagerung ewig – der Angreifer muss dann Soldaten nachführen oder
abbrechen. Das ist keine Lücke, sondern genau die Aussage des Verhältnisses.

### Was daraus für die Vorbedingungen folgt

Ein Verband braucht jetzt **Soldaten** (sonst kann er weder Drohnen führen noch
belagern) und **Drohnen nur dann, wenn die Kolonie noch wehrhaft ist**. Gegen
eine Kolonie ohne aktivierbare Drohnen ist ein reiner Soldatenverband genau das
richtige Werkzeug – das Gefecht beginnt dann von der ersten Sekunde an als
Belagerung.

## C. Zivilverluste und Konfliktschäden (§2)

§2 nennt Referenzpunkte, keine Kurven („exakte Kurven noch offen"). Beide
Platzhalter stehen in `Formulas`, an einer einzigen Stelle austauschbar.

**Zivilverluste je Tick**, „in Relation zu den tatsächlichen militärischen
Verlusten der Verteidiger":

```text
Anteil = 0,5 × (Wertverlust der Verteidigung in diesem Tick
                / Ausgangsstärke der Verteidigung)
```

Der Faktor 0,5 ist kein Balancingwert, sondern genau der Referenzpunkt aus §2:
eine **restlos** aufgeriebene Verteidigung entspricht dort 50 % Zivilverlust.
Bezugsgrößen (`populationAtStart`, `defenderStrengthAtStart`) werden **einmal**
bei Kampfbeginn festgehalten, damit die Quote über alle Ticks dieselbe
Grundlage behält.

**Sachschaden bei der Eroberung**, „überproportional zur Zivilverlustquote,
deterministisch, kein Zufall":

```text
Materialschaden          = Quote^0,3219    (0,5 → 0,80, wie in §2)
Schaden Verteidigungsanl.= Quote^0,1520    (0,5 → 0,90, wie in §2)
```

`Quote^k` ist die einfachste Kurve, die alle in §2 genannten Punkte trifft –
die beiden Referenzwerte und die Ränder 0→0 und 1→1. Sie trifft Gebäudestufen
(Verteidigungsanlage stärker) und Lagerbestände, jeweils abgerundet: der
angebrochene Rest einer Stufe steht noch.

## D. Übernahme

§2 verlangt: was überlebt, bleibt erhalten und geht an den Eroberer –
„Zusammenführung mit eigener Kolonie, falls vorhanden, sonst neue Kolonie".
Beides ist umgesetzt (`ColonyConquest`):

- **Eigene Kolonie auf demselben Planeten vorhanden** → Bevölkerung, Bargeld
  der Bevölkerung und Lagerbestände wandern hinüber, die eroberte Kolonie wird
  restlos aufgelöst. Gebäudestufen werden **nicht** addiert – zwei halbe
  Industriekomplexe ergeben keinen doppelten. Das ist keine Bequemlichkeit,
  sondern §1: *pro Spieler höchstens eine Kolonie je Planet*.
- **Sonst** → die Kolonie wechselt als Ganzes den Eigentümer.

In beiden Fällen: Aufträge des bisherigen Eigentümers (Produktion,
Rekrutierung, laufende Ausbauten) verfallen ohne Erstattung, und der siegreiche
Verband bezieht die Kolonie als Garnison.

Die **Loyalität wird bei der Übernahme NICHT gesetzt**. Sie steht per
Definition unter 2 % – genau das war ja die Bedingung dafür, dass die Kolonie
überhaupt übergeht. Ein pauschaler Startwert („frisch erobert = 5 %") wäre hier
nicht nur überflüssig, er würde die Loyalität sogar wieder anheben. Der
Eroberer übernimmt also eine zutiefst feindselige Bevölkerung, und weil
Rekrutierung nach §5 erst über 50 % möglich ist, muss er sie erst befrieden.

Der **Geldschöpfungs-Höchststand** (`moneySupplyStates`) hängt am Planeten und
bleibt bewusst stehen; sonst ließe sich über Eroberung und Neubesiedlung
beliebig neues Geld schöpfen.

## E. Rückzug – und die stillen Wege drumherum (§11)

Der Angreifer darf abbrechen; die Verteidigung schlägt dabei noch einmal
einseitig zu, danach steht der Verband wieder auf der Planetenoberfläche.

„Der Eigentümer der angegriffenen Kolonie kann sich aus der Verteidigung seiner
eigenen Kolonie **nicht** zurückziehen" – das gilt nicht nur für den
Rückzugsbefehl. Soldaten einschiffen (`embarkSoldiers`) oder Drohnen einlagern
(`storeDrones`), während der Angreifer vor der Tür steht, wäre exakt derselbe
Rückzug durch die Hintertür; beides ist während eines Gefechts gesperrt. Die
**Gegenrichtung bleibt offen**: Nachschub darf jederzeit nachrücken, und neu
Rekrutiertes greift ab dem nächsten Tick mit ein (§4, §12).

Ebenso gesperrt, solange ein Gefecht läuft: Bau/Ausbau/Rückbau an der
betroffenen Kolonie (§1) und **neue** Handelsaufträge von ihr aus (§12).
Bestehende Orders laufen weiter (Ware und Geld sind bereits gebunden), und die
Bevölkerung kauft weiter ein – deshalb sitzt die Sperre in `createSellOrder`
und nicht im Kern.

## F. Bewusste Vereinfachungen

Dieselben wie im Raum, aus demselben Grund:

- **Strikt ein Angreifer gegen einen Verteidiger.** §11 kennt zusätzlich
  unterstützende Verteidiger und mehrere getrennt zurückziehbare Angreifer –
  dafür bräuchte es Mehrparteien-Gefechte, die es auch im Raumkampf nicht gibt.
  Mehrere Angreifer führen deshalb mehrere getrennte Gefechte gegen dieselbe
  Kolonie; jedes rechnet mit dem Bestand ab, der zu Beginn seines Ticks
  tatsächlich noch da ist.
- **Gleichstand im Kampftick fällt zugunsten des Verteidigers**, wie im Raum.
  Sind beide Seiten aufgerieben, hat der Angreifer keine Soldaten mehr und
  könnte die anschließende Belagerung gar nicht führen – sein „Sieg" wäre
  folgenlos, die Kolonie bliebe ohnehin beim Verteidiger.
- **Ein Rückzug aus der Belagerung kostet nichts.** Der einseitige Schlusstick
  aus §11 braucht ein Gegenüber; in der Belagerung steht keine Streitmacht mehr
  da, die einen Abzug bestrafen könnte.

## G. Oberfläche

- **Bodentruppen-Übersicht**: je gelandetem Verband entweder „Verlegen" (eigene
  Kolonie) oder „Angriff" (fremde Kolonie im Krieg, Zielliste kommt aus
  `attackableColoniesForGroup` – der Client baut die Regel nicht nach), im
  laufenden Gefecht stattdessen Countdown und „Angriff abbrechen". Ganz oben
  ein eigener Kasten für Angriffe auf **eigene** Kolonien.
- **`/bodenkampfbericht/:token`**: wie der Raum-Kampfbericht, über denselben
  unerratbaren Token teilbar. Jeder Tick ist als Kampf- oder Belagerungstick
  ausgewiesen und wird entsprechend anders gelesen: im Kampftick aktiver
  Bestand gegen Reserve (damit ablesbar bleibt, wie viel Material mangels
  Soldaten gar nicht zum Einsatz kam), im Belagerungstick Soldaten gegen
  Rebellen samt Loyalität vorher/nachher.

## H. Absicherung

`GroundBattleTest` fährt 16 Fälle über die echten Befehle.

Kampfphase: Angriff nur im Krieg; gegen eine wehrhafte Kolonie braucht es
Drohnen; ohne Soldaten geht gar nichts; Vernichtung des Angreifers samt seiner
Reserven; Rückzug mit einseitigem Schlusstick; Rückzugsverbot für den
Verteidiger; Reserven ohne Gefechtsteilnahme; Bau- und Handelssperre.

Belagerung: das Zahlenbeispiel der Vorgabe wird Stelle für Stelle nachgerechnet
(1000 Soldaten gegen 10 000 Einwohner ⇒ Loyalität 19 % → 9 %, 1000 Rebellen,
83 tote Rebellen, 25 tote Soldaten); die Kolonie geht erst unter 2 % über; eine
neue Verteidigung holt das Gefecht zurück in die Kampfphase und nach deren Fall
wieder in die Belagerung; eine zu kleine Belagerungstruppe wird von den
Aufständischen aufgerieben; der Rückzug aus der Belagerung bleibt verlustfrei.

Eroberung: Zivil- und Sachschaden, Loyalität unter der Kapitulationsschwelle,
Eingliederung in eine eigene Kolonie desselben Planeten.
