# 19. Arbeitskräfte als Produktionsbremse, Sicherheitsdeckel und Start-Garnison

Drei zusammenhängende Balancing-/Transparenzänderungen, in einem Zug
umgesetzt: die Bevölkerung wirkt jetzt ausschließlich bremsend statt
beschleunigend auf die Produktion, Sicherheit ist auf 99 % gedeckelt, und die
Start-Garnison wurde deutlich verkleinert.

## A. Arbeitskräfte bremsen statt beschleunigen

### Ausgangslage

`workforceFactor` (`clamp(Bevölkerung / 400, 0.35, 5)`) war ein reiner
Tempo-Multiplikator: eine kleine Kolonie produzierte bis zu 65 % langsamer,
eine große bis zu 5× schneller – unabhängig davon, welches Produkt gebaut
wurde. Das war nicht plausibel erklärbar: warum sollte mehr Bevölkerung ein
einzelnes Schiff schneller bauen, wenn dessen Bauplatz in der Werft die
eigentliche Grenze ist? Zudem stand bei jedem Produkt bereits ein
Arbeitskräfte-Wert (`baseWorkforceRequired`), der aber nirgends benutzt
wurde.

### Neue Lesart

Der Katalogwert heißt jetzt **`workHoursPerUnit`** und bedeutet wörtlich
"Arbeitsstunden je Stück". Beispiel: ein Produkt mit 10 Spielstunden Bauzeit
(nach allen anderen Boni) und `workHoursPerUnit = 100` bindet
`100 / 10 = 10` Arbeitskräfte pro Stunde. Die verfügbaren Arbeitskräfte einer
Kolonie sind vereinfachend gleich ihrer Bevölkerung (1:1, kein weiterer
Umrechnungsfaktor).

Reicht die Bevölkerung nicht für den vollen Bedarf, verlängert sich die
Bauzeit proportional zum Fehlbetrag – **nie** umgekehrt:

```
hoursWithBonuses = Bauzeit NACH allen anderen Boni
                    (Anlagenstufe, Spezialisierung, Fördergüte, Blackout)
tatsächliche Dauer = max(hoursWithBonuses, workHoursPerUnit / verfügbare Arbeitskräfte)
```

`Formulas.productionHoursWithWorkforce` (Backend) /
`productionHoursWithWorkforce` (Frontend-Formelspiegel) implementieren genau
das; `Formulas.workersBoundPerHour` liefert den gebundenen Wert für die
Transparenzanzeige. Stehen z. B. nur 1 von 10 benötigten Arbeitskräften zur
Verfügung, läuft die Fertigung mit 10 % der eigentlich möglichen
Geschwindigkeit (Vorgabe des Auftrags, exakt in dieser Form abgebildet).

`ChainPlanner.computeProductionHours` wendet die Bremse für **jeden**
Schritt einer Kette einzeln an (Produktion, Werft, Ausbildungszentrum nutzen
dieselbe Methode); `computeProductionHoursWithoutWorkforce` liefert dieselbe
Rechnung ohne die Bremse als Bezugsgröße dafür, ob und wie stark ein Schritt
gerade ausgebremst wird. `ChainPlanStep.workersBoundPerHour` /
`.workforceLimited` transportieren das Ergebnis bis in die aufklappbare
Auftragsvorschau (`colony-detail.component.html`, Produktions-Tab): jeder
Schritt, der tatsächlich durch fehlende Arbeitskräfte gebremst wird, zeigt
das in Worten ("Bremst hier: es fehlen Arbeitskräfte – dieser Schritt bindet
X Arbeitskräfte je Stunde, mehr als die Kolonie hat.") statt nur die
verlängerte Stundenzahl kommentarlos anzuzeigen.

Eine Ausnahme: `productionAspect` (die militärische Kampfkraft-Formel,
Schaden **und** Haltbarkeit einer Einheit) nutzt `workHoursPerUnit` weiter
als reinen Multiplikator (`workHoursPerUnit × baseProductionHours`) – dort
ist der Wert unverändert numerisch derselbe, nur die Bedeutung im
Produktionskontext hat sich geändert. Soldaten sind laut Mechanik/05_...,
§5 zusätzlich von Spezialisierung ausgenommen (unverändert).

### Bevölkerung als reale Projektbremse

Damit wird Bevölkerung erstmals zu einer echten Ressourcen-Obergrenze für
GLEICHZEITIGE Großprojekte: eine Kolonie mit wenig Einwohnern kann zwar
weiterhin jedes Produkt bauen, aber nicht beliebig viele Arbeitsstunden
gleichzeitig binden. Das macht Bevölkerungswachstum zu einer strategisch
sichtbaren Voraussetzung für Ausbaustufen mit hohem Arbeitsstunden-Bedarf,
statt (wie bisher) nur die globale Fertigungsgeschwindigkeit zu skalieren.

## B. Sicherheit auf 99 % gedeckelt

`Formulas.MAX_SECURITY_PCT = 99` ersetzt den bisherigen Deckel von 400 %:
"es gibt keine absolute Sicherheit" – eine Kolonie bleibt immer verwundbar,
egal wie stark die Garnison und wie hoch die Loyalität. Die Berechnung
selbst (garnisonsbasierter Anteil plus loyalitätsproportionaler Sockel,
max. 30 % bei 100 % Loyalität) ist unverändert, nur die obere Klammer der
`clamp`-Funktion wurde von 400 auf 99 gesenkt.

**Bekannter, noch nicht behobener Folgeeffekt:** der Referenzwert der
Formel (`reference = max(Bevölkerung × 0,05, 5)`) wurde nicht mit
angepasst. Mit der neuen Start-Garnison (Teil C) ergibt sich bei
Spielbeginn (Bevölkerung ≈ 120) eine Garnisonsstärke von rund 485
(`productionAspect` von 2 Soldaten + 10 leichten Drohnen), gegen eine
Referenz von nur 6 – die Sicherheit springt damit sofort auf den neuen
99 %-Deckel und bleibt dort bis die Bevölkerung auf etwa 9 700 gewachsen
ist. Der Deckel wirkt in dieser Phase also nicht als knappe Ressource,
sondern nur als Obergrenze, die dauerhaft anliegt. Ob die Referenzformel
(z. B. steilere Skalierung mit der Bevölkerung, oder ein von der
Garnisonsstärke abhängiger Bezugswert) angepasst werden soll, ist eine
offene Balancing-Entscheidung und wurde bewusst nicht selbständig
verändert.

## C. Start-Garnison verkleinert

Bisher: 3 aktive + 2 Reserve-Soldaten sowie je 5 leichte/mittlere/schwere
Drohnen. Neu: **2 Soldaten und 10 leichte Drohnen**, sonst nichts
(`WorldSeed.starterGroundForceGroup`). Die Zahl 10 ist bewusst exakt die
Kommandokapazität der beiden Soldaten (`DRONES_PER_SOLDIER = 5`) – alle
zehn Drohnen sind von Anfang an geführt und einsatzbereit, keine
"totes Kapital"-Drohne ohne Führung. Nur eine Drohnenklasse: der volle
Konterkreis (leicht/mittel/schwer) bleibt bewusst dem Spieler überlassen
und ist nicht Teil der Startausstattung.

## Verifikation

- `mvn -o compile` (Backend) und `npx tsc --noEmit -p tsconfig.app.json`
  (Frontend) fehlerfrei.
- `backend/e2e/full-playthrough.mjs` gegen den laufenden LAN-Server grün
  (Registrierung, Bau, Produktion, Flottenbewegung, Kampf, Nachrichten).
- Chain-Zeiten sanken durch den Wegfall der alten `workforceFactor`-Untergrenze
  spürbar (Beispiel Grundnahrungsmittel-Kette bei Spielbeginn: 55,4 h → 19,3 h;
  Arbeitskraft-Auslastung 48,6 % → 18,0 % der neuen Bremse) – erwartetes
  Verhalten, da die alte Formel kleine Kolonien pauschal auf 35 % Tempo
  gedeckelt hatte, unabhängig vom tatsächlichen Arbeitsstunden-Bedarf des
  jeweiligen Produkts.
