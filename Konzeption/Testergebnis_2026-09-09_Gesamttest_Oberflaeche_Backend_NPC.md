# Gesamttest 9.9.2026 — Oberfläche, Backend, NPC-Lager und Siegbedingung

**Auftrag:** alle Oberflächenbausteine und das gesamte Backend durchtesten,
Befunde festhalten bzw. sofort beheben; prüfen, ob die NPCs jede Mechanik
tatsächlich durchspielen, ob eine Partei am Ende **gewinnen** kann (Sieg =
alle feindlichen Kolonien vernichtet) und dass die Bots **nicht ins Internet
gehen**.

**Aufbau des Tests.** Das Live-Spiel auf Port 8080 blieb unangetastet. Getestet
wurde gegen eine rsync-Kopie des Repos in einem Scratch-Verzeichnis, gebaut mit
`gameSpeedMultiplier = 16` und `productionSpeedMultiplier = 900` (Testtempo,
NICHT der Live-Stand) und gestartet auf Port 8081: Backend + 20 Bots
(`BotArmy`, Lager NORD/SUED) + `observer.mjs`. Die Oberfläche lief im Browser
gegen dieselbe Instanz, angemeldet als zusätzlicher menschlicher Kommandant.
Sieben Läufe: Lauf 1 als Bestandsaufnahme, Lauf 2–7 jeweils nach dem nächsten
Fix. (Für Läufe, in denen ein Lager wirklich gewinnen soll, darf man sich NICHT
selbst registrieren – ein menschlicher Kommandant ist eine eigene Partei, und
solange seine Kolonie steht, ist der Krieg per Definition offen.)

---

## 1. Zusammenfassung

| Bereich | Ergebnis |
|---|---|
| Backend-Tests | 149 grün vor der Sitzung, 154 grün danach (5 neue für die Siegbedingung) |
| Befehlsoberfläche | 157 WS-Befehle; jeder Frontend-Aufruf hat sein Gegenstück, kein Frontend-Aufruf ins Leere |
| Oberfläche | alle 10 Hauptbereiche und alle 6 Kolonie-Tabs durchgespielt; 6 Fehler gefunden und behoben |
| NPC-Mechaniken | Landungsoperation und Kolonisierung waren **tot** – beide laufen jetzt |
| Siegbedingung | war **nicht vorhanden** – jetzt implementiert; im Testlauf hat **Lager NORD tatsächlich gewonnen** (20 : 0 Kolonien) |
| Internet | Bots verbinden sich ausschließlich mit dem Spielserver; jetzt zusätzlich erzwungen |

---

## 2. Behobene Fehler

### F1 (kritisch): Fremde Benachrichtigungen bei jedem Kommandanten

Ein frisch registrierter Spieler hatte nach 30 Sekunden **291 ungelesene
Benachrichtigungen**, überwiegend „Handelsflotte NPC-Sued-04-Heimat ist in Vela
Passage 7 angekommen". Ursache: `Notifications.notify(...)` mit `colonyId = null`
erzeugt eine Meldung OHNE Adresse, und die ist laut
`NotificationCommands.forPlayer` für **jeden** sichtbar. Betroffen waren drei
Auslöser: Flottenankunft (`FleetCommands.processFleetArrivals`) sowie
„Guthaben läuft leer"/„Guthaben aufgebraucht" (`EconomyTick`).

*Behoben:* alle drei melden jetzt über `Notifications.notifyPlayer(...)` an den
Eigentümer der Flotte bzw. den betroffenen Kommandanten. Gegenprobe: neuer
Spieler nach Registrierung — 4 statt 291 Meldungen.

### F2 (kritisch): Neuladen übernahm einen fremden Kommandanten

Nach `Strg+Umschalt+R` war die Oberfläche plötzlich als **NPC-Nord-01**
angemeldet, mit vollem Zugriff auf dessen Kolonie und Flotten. Ursache:
`restoreSession` merkte sich ausschließlich die `playerId`, und die IDs werden
je Serverlauf neu und fortlaufend vergeben (`p_1`, `p_2`, …). Nach einem
Neustart des Servers zeigte die gemerkte ID damit auf einen ganz anderen
Kommandanten — im LAN-Betrieb ein Übernahmerisiko, ganz ohne böse Absicht.

*Behoben:* die Sitzung merkt sich zusätzlich den **Namen** des Kommandanten und
verwirft sich selbst, wenn hinter der ID beim Neuladen ein anderer Name steht.
Ein Eintrag ohne gemerkten Namen (aus einer älteren Fassung) gilt ebenfalls als
ungültig. Das ersetzt keinen Kennwortschutz (bleibt offen, siehe TODO), schließt
aber die stille Übernahme.

### F3: Falsch ausgerichteter Seitenkopf „Nachrichten"

Als einzige Seite standen Überschrift und Knopf rechtsbündig. Ursache:
`.page-head` (global) setzt `flex-direction: column`, `.page-head-row`
(Komponente) setzte nur `align-items: flex-end` — in der Spaltenachse ist das
„rechts". *Behoben:* `flex-direction: row` in `.page-head-row`.

### F4: Tankanzeige zeigte mehr Inhalt als Fassungsvermögen

Ein voller Frachtertank stand als „11,3 / 11 Kapseln" da: Füllstand mit einer
Nachkommastelle, Fassungsvermögen gerundet. *Behoben:* beide Zahlen mit
derselben Genauigkeit.

### F5: „Infrastruktur" in den Statistiken war der Wohnraum

Die Kachel „Infrastruktur" zeigte `avgInfrastructurePct` — denselben Wert, den
die Kolonieansicht **Wohnraum** nennt (Wohnkapazität je Einwohner, deshalb auch
Werte über 100 %). Unter demselben Namen läuft aber auch das GEBÄUDE
Infrastruktur. *Behoben:* die Kachel heißt jetzt „Wohnraum".

### F6: Unbrauchbare Meldung beim Einschiffen von Soldaten

`embarkSoldiers` lehnte mit „Nicht genug Platz: 27 von 27 Plätzen frei" ab —
eine Meldung, die wie eine Zusage klingt, weil die angeforderte Menge fehlte.
*Behoben:* die Meldung nennt jetzt Anforderung, freien Platz und Kapazität und
sagt, was zu tun ist (mehr Mannschaftstransporter in die Flotte).

---

## 3. Die NPCs: zwei tote Mechaniken, jetzt lebendig

### 3.1 Landungsoperation stand endlos in der Verladung

Im Ausgangslauf gab es **null Invasionen**. Fünf Invasoren standen dauerhaft in
der Phase `LOADING`, im Bot-Log 1194-mal dieselbe Ablehnung:

> Landungsoperation: Befehl abgelehnt: Nicht genug Platz: 27 von 27 Plätzen frei.

Ursache war eine veraltete Konstante im Bot: `Catalog.TROOP_CAPACITY_PER_TRANSPORT`
stand auf **1000**, der Katalog (`shared/catalog/ships.json`) sagt seit der
Massenskala (Umsetzungskonzept/27) **27**. Der Bot forderte also 1000 Soldaten
für einen einzigen Transporter an — Faktor 37 daneben. Die Konstante wurde
zudem nirgends benutzt; der Bot verlud stumpf `neededSoldiers`.

*Behoben (npc-bot):*
* Die Kapazität kommt zur Laufzeit vom Server (`World.troopCapacityPerTransport()`),
  die Bot-Konstante ist gelöscht — eine Quelle statt zwei.
* `Strategy.Plan.wantTransport` (bisher `boolean`) ist `wantTransports` (Anzahl);
  `Military.prepare` rechnet sie aus dem Soldatenbedarf, die Werft baut sie in
  Losen zu zehn.
* Die Landungsflotte sammelt **alle** fertigen Transporter ein; erst wenn ihre
  Truppenkapazität den Bedarf deckt, beginnt die Verladung.
* Eingeschifft wird nur, was an Bord passt UND in der Garnison steht.
* Neue Schiffe nimmt nur eine Flotte auf, die bei der Kolonie liegt — die Flotte
  wird vorher herangeholt (`dockAtHome`), sonst scheiterte jeder Takt an
  „Die Flotte muss bei dieser Kolonie stationiert sein" (101-mal in Lauf 2).

### 3.2 Treibstoff: Flotten blieben nach dem ersten Sprung stehen

`topUpFuel` tankte auf VOLL, das Lager hielt aber pauschal zehn Kapseln vor. Eine
Kampfflotte mit zwei Kreuzern fasst 12 600 Kapseln; im Log entsprechend:
„Nicht genug Treibstoff im Tank (benötigt 1610, im Tank 56)".

*Behoben:* getankt wird für die **Fahrt** (Fassungsvermögen ÷ 50 Sprünge ×
Sprünge × Sicherheitsaufschlag), und die Kolonie bevorratet Sprungtreibstoff
nach der größten eigenen Flotte statt nach einem Festwert.

### 3.3 Kolonisierung war wirtschaftlich unerreichbar

Kein einziges Kolonisationsschiff in irgendeinem Lauf; die Siedler meldeten
dauerhaft „Credits 630 < 16500". Die Messung an einem Bot zeigte, woran es lag:

| Bot NPC-Nord-03 | Wert |
|---|---|
| Guthaben Kommandant | 765 Cr, Saldo −322 Cr/h |
| Guthaben seiner **Bevölkerung** | **151 020 Cr** |
| Deckung Nahrung/Medizin/Elektronik | je 100 % |

Die Bevölkerung war also reich und vollversorgt, während der Kommandant an der
Grenze zur Pleite stand: Er verkaufte zu billig, und sein eigenes Preisprogramm
kannte nur ein Senken (und ein Anheben bis zum Startpreis 450 Cr).

*Behoben (npc-bot, `Economy.adjustPrices`):* Ist die Versorgung reichlich
(Deckung ≥ 105 %) und die Bevölkerung kaufkräftig (> 20 000 Cr im
Bevölkerungs-Wallet), hebt der Bot den Preis um 20 % an — bis maximal zum
Sechsfachen des Startpreises. Fällt die Deckung unter 90 %, senkt er wie bisher.
Damit fließt das gehortete Geld in die Staatskasse, ohne die eigene Versorgung
abzuwürgen.

**Wirkung:** Guthaben der Bots 600–1 200 Cr → 50 000–280 000 Cr; im selben Lauf
**12 Kolonisationsschiffe bestellt, 9 fertiggestellt, 8 Kolonien gegründet** —
die Mechanik war vorher schlicht nie erreichbar.

### 3.4 Zielaufklärung endete an der Lagergrenze

`Coordination.enemyColonies()` durchsuchte nur die **Heimatsysteme der Gegner**.
Gegründete Kolonien liegen aber im Heimatsystem ihres Gründers und eroberte im
Heimatsystem des Verlierers — also im eigenen Lager. Genau die Kolonien, die zum
Abschluss eines Krieges fehlen, waren unsichtbar. *Behoben:* die Aufklärung geht
über die Heimatsysteme **aller** Kommandanten.

### 3.5 Bodengefechte gingen verloren – die Landung war zu klein

Drei von vier, später fünf von sechs Bodengefechten endeten mit
`DefenderVictory` in der Kampfphase. Der Kampfbericht des Servers zeigt, woran
es lag (Gefecht `gbt_thk`, erster Tick):

| Seite | Soldaten | aktive Drohnen |
|---|---:|---|
| Angreifer | 980 | **48 schwere** |
| Verteidiger | 941 | 77 mittlere + 10 leichte |

Drei Ursachen, alle behoben:

1. **Der Bedarf war zu früh gerechnet.** Zwischen Bedarfsrechnung und Landung
   liegen Spieltage, in denen die Zielkolonie weiter rekrutiert und im Gefecht
   Reserven nachaktiviert (Mechanik/05 §4). Sicherheitsfaktor der Drohnenzahl
   von 1,5 auf **3,0**, Mindestmenge von 15 auf **120**.
2. **Der Frachtraum war die eigentliche Grenze.** Eine schwere Drohne wiegt
   583 t, ein Frachter trägt 28,4 kt – also **48 Stück**. Der Bot forderte mehr
   an, als je an Bord passte, und lief beim Beladen in „Massekapazität der
   Flotte reicht nicht aus". Jetzt rechnet er die Ladefähigkeit aus dem Katalog
   (Masse UND Volumen) vor und **bestellt zusätzliche Frachter**, bis die
   benötigten Drohnen hineinpassen.
3. **Fehlende Drohnen ließen die Operation hängen.** `storeDrones` wurde mit
   einer Menge aufgerufen, die die Garnison gar nicht hatte („Die Garnison hat
   nur N davon", 1860-mal im Lauf). Jetzt wird verladen, was da ist; fehlt
   etwas, fällt die Operation in den **Aufbau zurück** – nur dort schreibt
   `Military.prepare` den Bedarf wieder in den Plan, sonst hörte das
   Ausbildungszentrum auf zu rekrutieren und die Landung wartete ewig auf
   Soldaten, die niemand mehr ausbildete.

### 3.6 Rollenverteilung zielte nicht auf den Sieg

Je Lager war ein Viertel der Mitglieder Invasor (2–3 von 10). *Geändert:* bei
zehn Mitgliedern jetzt 6 Invasoren, 2 Raider, 1 Siedler, 1 Verteidiger. Wer
angegriffen wird, wird ohnehin vorübergehend Verteidiger. Damit bleiben alle
Rollen (und alle Mechaniken) besetzt, das Lager arbeitet aber auf den Sieg hin.

---

## 4. Die Siegbedingung (neu)

Es gab **keine**: Kolonien wechselten den Besitzer, ein Kommandant ohne Kolonie
blieb einfach im Spiel, und niemand hat je „gewonnen".

**Neu, nach der Vorgabe „gewonnen hat eine Partei, wenn sie alle feindlichen
Kolonien vernichtet hat":**

* `VictoryCommands.evaluate` läuft in jedem Tick nach allen Eroberungen.
* **Partei** = Lager (`Player.campId`) bei NPCs, der Kommandant selbst bei allen
  anderen. Zwei Lager, die gemeinsam kämpfen, gewinnen gemeinsam.
* Sieg, sobald **genau eine** Partei noch Kolonien besitzt — und nur, wenn
  vorher mindestens zwei Parteien welche hatten (sonst gewänne der erste
  Kommandant einer frischen Galaxie gegen niemanden).
* Der Ausgang wird EINMAL festgeschrieben (`GameState.victory`), alle
  Kommandanten bekommen eine Benachrichtigung (Code 121), und die Oberfläche
  zeigt ein Band über allen Seiten — golden für die eigene Partei.
* Abfragbar über den WS-Befehl `victory`; `resetGame` löscht ihn wieder.
* Die Galaxie läuft weiter: ein Kommandant ohne Kolonie behält Flotten und kann
  mit einem Kolonisationsschiff neu anfangen (Umsetzungskonzept/34 §J 9).
* Abgesichert durch `VictoryTest` (5 Fälle): kein Sieg bei zwei lebenden
  Parteien, kein Sieg für den Alleinstart, Sieg des letzten Kolonienbesitzers,
  gemeinsamer Lagersieg, und „einmal entschieden bleibt entschieden".

---

## 5. Kein Internet für die NPCs

Der Bot baut genau **eine** Netzverbindung auf: den WebSocket zum Spielserver
aus `--server`. Es gibt im ganzen Modul keinen weiteren HTTP-Aufruf. Gegenprobe
zur Laufzeit: die 20 Bots der Armee hielten exakt 20 Verbindungen, alle nach
`127.0.0.1:8081`.

Zusätzlich abgesichert: `GameConnection.connect` prüft die Zieladresse und
weist alles ab, was nicht Loopback, Link-Local oder privates Netz ist —

> Der Bot verbindet sich nur mit einem Server im eigenen Netz. Adresse 8.8.8.8
> liegt außerhalb – Verbindung abgelehnt.

Der LAN-Betrieb (Server unter seiner LAN-IP, Bots von anderen Rechnern) bleibt
davon unberührt.

---

## 6. Balance-Befunde ohne Eingriff

### B1: Eine frische Kolonie schrumpft, bis der Spieler den Preis senkt

Ein neu registrierter Kommandant steht binnen Minuten bei Lebensstandard 27 %
(Grenze zur Abwanderung: 30 %) und verliert Bevölkerung — obwohl Lager und
Verkaufsorder voll sind. Der Grund ist der Startpreis von 450 Cr je Stück: Er
ist in `WorldSeed` für eine Kolonie von rund 200 Einwohnern hergeleitet, die
Kolonie startet aber mit 2000. Die Bevölkerung kann sich davon nur einen Teil
ihres Bedarfs leisten (Deckung 54 %).

Gegenprobe in der Oberfläche: Preis von 450 auf 60 Cr gesenkt → Deckung 100 %,
Lebensstandard exakt 50 % (das Maximum ohne Medizin und Elektronik),
Bevölkerung wächst wieder (1 535 → 2 375). Der Saldo des Kommandanten kippt
dabei ins Minus — das ist die bekannte Kaufkraft-Lücke.

**Nicht geändert**, weil Konzept 34 §D die Konsumpreise ausdrücklich so
entschieden hat („die Kaufkraft-Lücke ist Sache des Spielers"). Umgesetzt wurde
nur der Hinweis: der Bevölkerungs-Tab nennt jetzt als dritten Hebel
ausdrücklich, den eigenen Verkaufspreis zu senken. Wenn der Startpreis
mitwachsen soll, gehört er in `WorldSeed` an `START_POPULATION` gekoppelt — das
ist eine Balance-Entscheidung, keine Fehlerbehebung.

### B2: Der Browser hält das alte Bundle fest

Nach einem Neubau der Instanz zeigte die Seite ohne `Strg+Umschalt+R` weiter den
Stand von vorher (alte Beschriftungen, fehlende neue Panels). Für den LAN-Abend
heißt das: Nach einem Neustart mit neuem Frontend müssen die Mitspieler hart
neu laden. Cache-Header für `index.html` wären der saubere Weg – eigenes Thema,
hier nur vermerkt.

### B3: NPC-Kommandanten stehen in der Anmeldeliste

Der Anmeldebildschirm listet alle 20 NPC-Kommandanten zur Auswahl; jeder kann
sie ohne Kennwort übernehmen. Gehört zum offenen TODO-Punkt „Kennwortschutz".

---

## 7. Was die Oberfläche sonst noch geprüft bekam

Durchgespielt und ohne Befund: Registrierung samt Pflichtfeldprüfung, Anmeldung,
Abmeldung, Kolonienliste, Kolonie-Tabs (Übersicht, Bebauung mit Energiespeicher
und Ausbauvorschau, Produktion mit Kettenvorschau und Produktauswahl-Dialog,
Bodentruppen, Bevölkerung mit Verlauf, Handel mit Preisänderung),
Produktionsübersicht, Flotten (Tank, Betanken, Bewegen mit Sprungvorschau,
Fracht, Zusammenlegen/Aufteilen), Bodentruppen-Übersicht, Diplomatie
(Kriegserklärung, Friedens- und Handelsvertragsangebote, Kampfprotokoll),
Nachrichten (Verfassen, Senden, Posteingang/Gesendet), Galaxiekarte samt
Fog-of-War („Noch nicht bereist"), Systemansicht (Bewegen, Blockade bilden und
aufheben, Kolonisieren-Hinweise je Planet), Handel (Systemhandelsposten,
Handelsgilde-Station mit Orderbuch und Depot), Konto (Saldo je Spielstunde,
Buchungen), Statistiken.

---

## 8. Testläufe im Überblick

| Lauf | Stand | Ergebnis |
|---|---|---|
| 1 | Ausgangsstand | **0** Invasionen, **0** Gründungen, **0** Eroberungen; alle Invasoren hingen in der Verladung |
| 2 | + Transporter-, Treibstoff-, Preis- und Aufklärungs-Fixes | 4 Invasionen, 4 Landungen, 4 Bodengefechte, **1 Eroberung**, **8 Koloniegründungen** |
| 3 | + engeres Preisband, mehr Drohnen | Invasionen laufen; Preisband zu eng → keine Rücklagen mehr für Kolonisationsschiffe |
| 4 | Preisband 0,9/1,05, 6 Invasoren je Lager | 6 Invasionen, 4 Landungen, 1 Gründung – Landungen zu klein, 5 von 6 Gefechten verloren |
| 5 | + größere Ausbildungslose, 120 Drohnen | 1 Eroberung, 1 Bot ausgeschaltet; Verladung blieb an fehlenden Drohnen hängen |
| 6 | + Rückfall in den Aufbau, Frachtraum-Deckelung | 8 Invasionen, 7 Landungen – aber auf 48 Drohnen gedeckelt und damit unterlegen |
| 7 | + zusätzliche **Frachter** für die Drohnen; im Lauf nachgezogen: Ausnahme-Schutz im Bot-Takt, gemeinsame Endziele, 10 % Belagerungssoldaten | **12 Eroberungen → Lager NORD gewinnt** (20 : 0 Kolonien) |

### Ergebnis: **Lager NORD hat gewonnen**

Nach rund drei Stunden Laufzeit stand im letzten Lauf die Entscheidung:

```
victory = {"partyId":"camp:NORD","partyName":"NORD","camp":true,
           "members":["NPC-Nord-01", ... ,"NPC-Nord-10"],
           "defeated":["NPC-Nord-06","NPC-Sued-01", ... ,"NPC-Sued-10"],
           "colonies":20}
```

Alle 20 Kolonien der Galaxie lagen in der Hand des Lagers NORD, elf
Kommandanten (die zehn von SUED plus NPC-Nord-06, dessen Heimatwelt SUED
zwischenzeitlich genommen hatte) standen ohne Kolonie da. In der Oberfläche
erschien das Siegband über allen Seiten („Lager NORD hat den Krieg entschieden
– alle gegnerischen Kolonien sind gefallen"), die Tafel auf der Statistikseite
nannte Siegerpartei, Mitglieder und Unterlegene, und jeder Kommandant bekam die
Benachrichtigung.

Der Weg dahin in Zahlen: 12 Eroberungen, jede über eine vollständige
Landungsoperation (Transporter bauen, Soldaten ausbilden, Drohnen verladen,
Gateway-Sprünge, Landung, Bodengefecht, Belagerung bis Loyalität < 2 %).

### Zwischenstand des letzten Laufs

Auf dem Weg dorthin stand es zwischenzeitlich **17 : 3 Kolonien für Lager NORD**:
neun Eroberungen, acht Kommandanten ohne eigene Kolonie (sieben davon SUED).
Vier NORD-Invasoren hatten Eroberungen auf dem Konto (einer allein vier) und
rüsteten für die nächste Welle – nach einer verlorenen Landung verdoppelt der
Bot seinen Bedarf, die zweite Welle verlangt also 2000 Soldaten und 74
Transporter. Zwei Rauheiten blieben dabei sichtbar und stehen als nächste
Schritte an:

* Eine Landungsflotte mit 74 Transportern bekommt ihren Tank nicht mehr in
  einem Zug voll („Landungsflotte kann nicht ablegen (Treibstoff?)") – die
  Kapselproduktion je Auftrag ist auf 1500 gedeckelt.
* Der Nachschub für eine zweite Welle dauert länger als der erste Aufbau; ein
  Lager sollte Reste einer geschlagenen Operation weiterverwenden, statt jedes
  Mal von vorn zu beginnen.

Drei weitere Befunde aus genau dieser Schlussphase sind noch im Lauf behoben
worden – ohne sie wäre der Krieg bei 19 : 1 stehen geblieben:

1. **Ein Bot fror ein.** Fragt der Takt die Truppenkapazität einer inzwischen
   verschwundenen Flotte ab, antwortet der Server mit „Unbekannte Flotte" – und
   weil diese Abfrage VOR den abgesicherten Modulen läuft, riss die Ausnahme
   den ganzen Takt mit. Ein Kommandant stand so über zwanzig Minuten still
   (im Log jede Sekunde „Takt: Befehl abgelehnt: Unbekannte Flotte").
   Behoben: `World.troopCapacity`/`cargoCapacity` liefern jetzt ein leeres
   Ergebnis statt einer Ausnahme.
2. **Fünf Invasoren standen untätig herum.** Der Koordinator vergibt jedes Ziel
   nur EINMAL – bei einer letzten feindlichen Kolonie bekam also genau ein
   Angreifer eine Aufgabe. Behoben: Sind weniger Ziele als Invasoren übrig,
   greifen mehrere dieselbe Kolonie an.
3. **Die Belagerung war zu knapp bemessen.** Mit 5 % der Zielbevölkerung als
   Soldaten sinkt die Loyalität zwar schneller, als sie sich erholt – aber
   10 % der Bevölkerung kämpfen als Aufständische zurück: Von 1000 Soldaten
   fielen 44 je Tick, und weil der Loyalitätsverlust an der Soldatenzahl hängt,
   wurde die Belagerung immer langsamer, während der Verband schmolz (bei
   Loyalität 35 war er aufgerieben). Behoben: 10 % der Zielbevölkerung.

**Der Krieg wird also tatsächlich geführt und ist entscheidbar** – im
letzten Lauf lief eine Landungsoperation nach der anderen, Bodengefechte gingen
in die Belagerungsphase über, Kolonien wechselten den Besitzer und drei
Kommandanten standen ohne Kolonie da. Ein vollständiger Lagersieg (alle 20
Kolonien in einer Hand) braucht bei diesem Tempo Stunden; die Siegbedingung
selbst wurde deshalb getrennt geprüft (Abschnitt 4 und die fünf Testfälle) und
in der Oberfläche mit einem echten Serverzustand gegengeprüft: Band, Tafel auf
der Statistikseite und Benachrichtigung erscheinen wie vorgesehen.

## 9. Testlauf nachstellen

```
rsync -a --exclude .git --exclude target --exclude node_modules --exclude dist <repo>/ /tmp/nebula-test/
# shared/game-constants.json: gameSpeedMultiplier 16, productionSpeedMultiplier 900
# frontend/src/app/core/sim/backend-config.ts: :8080 -> location.port
cd frontend && npx ng build --configuration production
cp -r dist/nebula-frontend/browser/. ../backend/src/main/resources/META-INF/resources/
cd ../backend && ./mvnw -DskipTests package
java -Dquarkus.http.port=8081 -jar target/quarkus-app/quarkus-run.jar &
java -cp ../npc-bot/target/npc-bot.jar de.nebula.npcbot.BotArmy \
     --server=ws://localhost:8081/game --count=10 --camps=NORD,SUED --logdir=logs &
node ../npc-bot/observer.mjs ws://localhost:8081/game --interval=60 --out=logs/observer.jsonl &
node ../npc-bot/report.mjs logs      # am Ende
```

Wer den Ausgang des Krieges sehen will, meldet sich dabei **nicht selbst an**:
ein menschlicher Kommandant ist eine eigene Partei, und solange seine Kolonie
steht, ist der Krieg definitionsgemäß offen.
