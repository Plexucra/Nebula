# Testergebnis – End-to-End-Durchlauf der Oberfläche

**Datum:** 2026-09-09
**Umfang:** Alle Seiten, alle Tabs, alle erreichbaren Schaltflächen; Produktion,
Bebauung, Werft, Flotten (Zusammenlegen/Aufteilen/Betanken/Fracht/Truppen),
Gateway-Reise, Systemansicht, Erkundung, Blockade, Raumkampf, Landung, Handel
(planetar + Handelsgilde), Diplomatie, Nachrichten, Benachrichtigungen, Konto,
Statistiken, Registrierung/Anmeldung.

## Umsetzungsstand (Stand 9.9.2026, nach dem Testlauf)

**Alles unten Aufgeführte ist behoben**, mit zwei bewussten Ausnahmen:

- **Kennwortschutz / Authentifizierung** (Teil von K7) – auf Wunsch
  zurückgestellt, kommt als eigenes Thema. Der zweite Teil von K7, der
  Reset-Knopf, ist entschärft: Er verlangt jetzt die getippte Bestätigung
  `GALAXIE LOESCHEN`.
- **Die offenen Fragen in §7** sind Entwurfsfragen, keine Fehler. Beantwortet
  und umgesetzt wurden F1 (Realzeit-Fristen), F6/F7 (Träger, Treibstoffskala –
  als Anzeige) und F19 (fehlende Benachrichtigungen); die übrigen stehen
  weiterhin zur Entscheidung.

Zwei Dinge kamen über den Bericht hinaus dazu:

1. **Trägersprung ohne Gateway** ist jetzt implementiert
   (`CarrierTransitTest`, Konzept 06). Reichen die Träger einer Flotte nicht,
   um alle übrigen Schiffe aufzunehmen, wird der Sprung **abgebrochen** und die
   Meldung nennt, wie viele Träger fehlen.
2. **Laufende Gefechte** stehen dauerhaft rot oben rechts in der Kopfzeile und
   verlinken direkt auf den Kampfbericht.
3. **Kontolöschung nach Inaktivität**: Wer sich 30 echte Tage nicht anmeldet,
   verschwindet mitsamt Kolonien, Flotten, Lagern, Orders und Verträgen spurlos
   aus der Galaxie (`RetentionCleanup.deletePlayer`, `RetentionAndPlayerDeletionTest`).

### Zur Realzeit-Ausnahme (K1)

Aufbewahrungsfristen und Inaktivität rechnen ab sofort als **einzige**
Zeitangaben des Spiels in ECHTER Zeit, nicht in Spielzeit. Die Ausnahme ist an
jeder Stelle mit dem Wort `REALZEIT-AUSNAHME` markiert:
`shared/game-constants.json` (`_realTimeException`), `GameConstants`,
`SharedConstants`, `RetentionCleanup`, `EconomyTick.warnAboutSupplyGaps`,
`Player.lastSeenAt` und `core/shared-constants.ts`. Werte: Benachrichtigungen
14 Tage, Nachrichten 30 Tage, Kontolöschung 30 Tage, Versorgungswarnung
frühestens alle 60 Minuten. Die Kündigungsfristen für Verträge bleiben bewusst
in Spielzeit – das ist Spielmechanik.

---

## 0. Testaufbau und Grenzen des Laufs

Das Live-Spiel auf Port 8080 (20 NPC-Bots) wurde **nicht** angefasst. Der Lauf
fand gegen eine separate Instanz aus einer rsync-Kopie statt:

- `gameSpeedMultiplier: 20` (statt 4), `productionSpeedMultiplier: 4000` (statt 90)
- Backend auf Port 8081, Frontend aus derselben Kopie eingebettet
- Vier Testkommandanten: `Testalpha`, `Testbeta`, `Testgamma` und (unbeabsichtigt,
  siehe H10) zwei namenlose

**Was das für die Zahlen bedeutet:** Alle Realzeit-Angaben unten sind, wo es
darauf ankommt, auf das **Live-Tempo (`gameSpeedMultiplier: 4`, 625 ms je
Spielstunde)** umgerechnet – nicht auf das Testtempo. Wo ich gemessen habe,
steht die Messung dabei.

**Nicht erreicht:** Ein echter Bodenkampf (die Testkolonie brach vorher
wirtschaftlich zusammen, siehe K6, und ohne Credits/Bevölkerung ließen sich
keine Drohnen mehr rekrutieren). Landung, Anlandung der Truppen und die
Angriffsauswahl auf der Bodentruppen-Seite wurden geprüft, die
Bodengefechts-Auflösung selbst nicht. Ebenso ungeprüft: Kolonisierung eines
fremden Systems (Kolonisationsschiff verlangt 6.000 Einwohner, die Kolonie hatte
zu diesem Zeitpunkt 2.783).

---

## 1. Kritisch

### K1. Nachrichten und Benachrichtigungen löschen sich nach Sekunden selbst

`RetentionCleanup.purgeExpired` rechnet die Aufbewahrungsfristen in **Spielzeit**
um (`RetentionCleanup.java:23-26`). Mit `notificationRetentionGameHours: 48` und
`messageRetentionGameHours: 168` ergibt das bei Live-Tempo:

| Objekt | Frist | Realzeit bei `gameSpeedMultiplier: 4` |
|---|---|---|
| Benachrichtigung | 48 Spielstunden | **30 Sekunden** |
| Nachricht an Mitspieler | 168 Spielstunden | **105 Sekunden** |

**Gemessen** (bei Multiplikator 20): Eine Nachricht von Testbeta an Testalpha war
nach 15 s noch da, nach 30 s **gelöscht**. Die Nachricht, die ich vorher in der
Oberfläche von Testalpha an Testbeta geschickt hatte, stand in Testalphas Ordner
„Gesendet", Testbetas Posteingang meldete beim Anmelden „Posteingang ist leer" –
der Empfänger hat sie nie zu Gesicht bekommen.

Der Klassenkommentar nennt das ausdrücklich als Absicht („damit skaliert die
Aufbewahrung automatisch mit"). Genau das ist der Fehler: Eine Aufbewahrungsfrist
ist eine **Realzeit**-Größe, weil ein Mensch sie in Realzeit liest. Das
Nachrichten- und Benachrichtigungssystem ist im laufenden Spiel damit praktisch
funktionslos – es sei denn, man klickt „Beibehalten" innerhalb von 30 Sekunden,
was voraussetzt, dass man die Meldung überhaupt gesehen hat.

Betroffen sind mittelbar auch die Kündigungsfristen
(`peaceTreatyTerminationNoticeGameHours: 168` = 105 s real,
`tradeAgreementTerminationNoticeGameHours: 48` = 30 s real). Das ist eine
Spielmechanik und darf in Spielzeit rechnen – aber „7 Spieltage Kündigungsfrist"
klingt nach einer Bedenkzeit und sind knapp zwei Minuten.

### K2. Das Trägerschiff kann nichts – der Sprung ohne Gateway existiert nicht

Ich habe in der Werft ein Trägerschiff gebaut (funktioniert, 1 m 25 s Bauzeit,
Fortschrittsanzeige korrekt) und es in eine Flotte überführt. Danach: nichts.

- `CarrierTransit` aus `Konzeption/Umsetzungskonzept/06_…md` §1 ist **nicht
  implementiert** – im ganzen Backend kommt `Carrier` nur als Enum-Wert in
  `ShipClass.java:4` vor. Weder `FleetCommands` noch `GatewayCommands` kennen
  eine Sonderbehandlung.
- `carrierSlotCapacity: 400` und `carrierSlotUsage` stehen im Katalog
  (`shared/catalog/ships.json`) und im Modell (`ShipTypeDef.java:13-25`), werden
  aber **von keiner einzigen Codestelle gelesen**. Man kann keine Schiffe an Bord
  nehmen.
- `cargoMassKg: 0`, `troopCapacity: 0` – der Träger kann auch keine Fracht und
  keine Truppen tragen.
- In der Zielsystemauswahl gibt es keine Unterscheidung zwischen Gateway-Route
  und Trägersprung, und keine „Route berechnen"-Gegenüberstellung, wie sie
  Konzept 06 §4 vorsieht.

Ein gebautes Trägerschiff ist heute ein Schiff, das Unterhalt kostet und sonst
nichts tut. Der Testauftrag „Träger bauen und Sprünge ohne Gateway machen" ist
mit dem aktuellen Stand nicht erfüllbar.

### K3. Der Kampfbericht ist aus der Oberfläche nicht erreichbar

Nach dem gewonnenen Raumkampf steht auf der Diplomatie-Seite unter
„Kampfprotokoll" die Zeile `Testalpha ⚔ Testgamma – ANGREIFER SIEGT – 5 Ticks`.
**Diese Zeile ist kein Link.** Der einzige Weg zu `/kampfbericht/:token` führte
über die Benachrichtigung – und die ist nach 30 Sekunden gelöscht (K1). Ich habe
den Bericht nur öffnen können, indem ich den Token über die WebSocket-Schnittstelle
abgefragt und die Route von Hand gesetzt habe.

Das ist besonders ärgerlich, weil der Bericht selbst **sehr gut** ist: Tick für
Tick, je Seite die teilnehmenden Schiffe und die Verluste, dazu die
Gesamtverluste. Diese Arbeit sieht heute niemand.

### K4. Benachrichtigungs-Links zeigen ins Leere und tragen die falsche Beschriftung

- `EconomyTick.java:339` setzt als Link `"/kolonien/" + colony.id`. Die Route
  `kolonien` gibt es in `app.routes.ts` nicht – der Pfad ist `planeten/:id`. Der
  Klick landet über den `**`-Fallback stumpf in der Kolonienliste.
- `app-shell.component.html:39` beschriftet **jeden** Link fest mit
  „Kampfbericht öffnen →" – auch bei einer Versorgungswarnung (#505). Im Test
  standen vier Versorgungswarnungen untereinander, alle mit „Kampfbericht
  öffnen →".

### K5. Die Versorgungswarnung überflutet die Glocke

`SUPPLY_WARNING_COOLDOWN_GAME_HOURS = 24` (`EconomyTick.java:310`) – bei
Live-Tempo also **alle 15 Realsekunden je Kolonie und je fehlendem Gut**. Bei
zwei fehlenden Gütern sind das acht Meldungen pro Minute aus einer einzigen
Kolonie. Im Test war die Glocke durchgehend nur mit #505 gefüllt; die
Kampfmeldungen (#402/#403) waren nach wenigen Sekunden verdrängt bzw. abgelaufen.
Abfrage der Codes nach dem Gefecht: `{"505": 4}` – sonst nichts.

Zusammen mit K1 und K3 heißt das: Das Benachrichtigungssystem meldet ausschließlich
das, was am wenigsten dringend ist, und verliert alles andere.

### K6. Kein Alarm bei Blackout, Bevölkerungssturz und Bankrott

Der Testverlauf in Zahlen, alles innerhalb weniger Minuten und **ohne eine
einzige Meldung**:

| | Höchststand | Ende |
|---|---|---|
| Bevölkerung Alpha Prime | 7.791 | **115** |
| Guthaben Testalpha | 139.581 Cr | **0 Cr** |
| Energiedeckung | 100 % | **50,6 % (Blackout)** |

Im Einzelnen:

- **Blackout:** `isBlackout` lieferte `true`, `powerCoverage` 0,506. Angezeigt
  wird das ausschließlich durch eine Farbänderung (violett statt blau) und einen
  angehängten Halbsatz „– durch Blackout halbiert." am **Ende** eines
  dreizeiligen Fließtextes. Kein Banner, kein Warnsymbol, das Wort „Blackout"
  taucht in keiner Überschrift auf. Es gibt **keinen Benachrichtigungscode** dafür
  (vorhandene Codes: 101, 102, 401, 402, 403, 405, 406, 407, 503, 505).
- **Bankrott:** Ursache ist der Gebäudeunterhalt. `EconomyTick.java:54` bucht
  `upkeepPerLevel * level` je Tick – bei Infrastruktur 20 + Werft 6 + Akademie 4
  + Industrie 8 + Wohnkomplex 2 waren das rund 450 Cr/Tick gegen 450 Cr/Tick
  Konsumeinnahmen, zuzüglich Löhne und Flottenunterhalt.
- **Der Unterhalt wird nirgends angezeigt.** `upkeepPerLevel` existiert im
  Katalog und wird abgebucht, aber im gesamten Frontend gibt es keine einzige
  Ausgabe dafür. Die Bebauungszeile nennt nur die **einmaligen** Kosten
  („Stufe 8: 1.325 Cr · 6.4h"). Der Spieler baut aus, ohne die Folgekosten zu
  kennen, und geht daran pleite.
- **Die Kolonienliste zeigt davon nichts:** vier unbeschriftete Balken und die
  Einwohnerzahl. Kein Blackout-Zeichen, kein Trendpfeil, keine Warnfarbe. Wer
  zehn Kolonien hat, merkt nicht, dass eine stirbt.
- Die Statistikseite meldete die Kolonie in derselben Lage weiterhin als
  **„STABIL"**.

### K7. Kein Passwortschutz, und jeder darf die Galaxie aller löschen

- Die Anmeldung ist eine reine Namensliste ohne Passwort. Im LAN-Betrieb kann
  sich jeder als jeder anmelden und dessen Reich vollständig übernehmen.
- Der Knopf **„⟲ Reset"** sitzt dauerhaft in der Kopfzeile jeder Seite, direkt
  neben „Abmelden". Er löscht `state.players`, `state.systems`, `state.planets`,
  `state.colonies` … für **alle** Kommandanten (`GameSocket.java:656 ff.`). Es
  gibt einen `confirm()`-Dialog, aber keinerlei Berechtigungsprüfung im Backend.
  Ein Fehlklick plus Enter beendet ein stundenlang gewachsenes LAN-Spiel.

---

## 2. Hoch

### H1. Kolonie-Detailseite: vier Abfragen laufen dauerhaft mit leerer ID

`colony-detail.component.ts`:

```
35  planet        = this.api.planet(this.colony()?.planetId ?? '')
36  moneyState    = this.api.moneySupplyState(this.colony()?.planetId ?? '')
44  sellOrdersAll = this.api.sellOrders(this.colony()?.systemId ?? '')
45  system        = this.api.system(this.colony()?.systemId ?? '')
```

Diese Feldinitialisierungen laufen **einmal** beim Erzeugen der Komponente. Zu
diesem Zeitpunkt ist `colony()` noch leer, es geht also `''` an den Server, und
das Ergebnis bleibt für die Lebensdauer der Komponente leer – das Argument ist
nicht reaktiv.

Beobachtet: Beim ersten Öffnen der Kolonie stand in der Brotkrume
„← PLANETEN · ·" (zwei leere Trennpunkte) und das Panel **„Himmelskörper" war
komplett leer**. Nach einer späteren Navigation auf dieselbe Seite war es
gefüllt – dann lag `colony()` bereits im Poll-Cache. Der Fehler ist also
zeitabhängig und trifft ausgerechnet den ersten Eindruck.

### H2. Die Bebauungszeile kollabiert – auf dem Desktop und erst recht auf dem Handy

`colony-detail.component.scss:73-82`: `.building-row { grid-template-columns: 1fr auto auto; }`

Die mittlere Spalte (Stufe + Baustoffliste) ist `auto` und nimmt sich, was sie
braucht. Sobald die Baustoffliste von drei auf vier Einträge wächst (Infrastruktur
ab Stufe 8), quetscht sie die Beschreibungsspalte (`1fr`) auf rund 150 px
zusammen: Der Beschreibungstext steht dann als dünne, über 400 px hohe Säule da,
während rechts die halbe Zeile leer bleibt. Screenshot-belegt bei 1920 px
Fensterbreite.

Die 720-px-Regel (`Zeile 253-256`) ändert nur `.tab-btn` und `.grid-2` – die
`building-row` **stapelt auf dem Handy nicht**. Drei Spalten mit 4 Baustoffen und
2 Knöpfen auf 390 px sind unbenutzbar.

### H3. Schiffe und Truppen werden blind gekauft

- **Werft** (Flotten-Seite): ein Auswahlfeld mit sieben Namen. Kein Preis, keine
  Bauzeit, keine Angriffs-/Panzerungswerte, keine Ladekapazität, keine
  Truppenkapazität, kein Konterverhältnis. Erst nach dem Klick auf „Bauen"
  erscheint eine Restzeit.
- **Rekrutierung** (Kolonie → Bodentruppen): ein Auswahlfeld mit „Leichte Drohne
  / Mittlere Drohne / Schwere Drohne / Soldat". Ebenfalls ohne einen einzigen
  Wert.
- Das Kontersystem aus `Mechanik/03_…` ist damit für den Spieler unsichtbar.

Das sind genau die Zahlen, die man für eine strategische Entscheidung braucht.

### H4. Flottenbewegung ohne Routenvorschau

Der Knopf „Bewegen" öffnet ein Auswahlfeld mit **203 Systemen**, ohne Entfernung,
ohne Sprunganzahl, ohne Reisedauer, ohne Treibstoffbedarf, ohne Besitzer, ohne
Gruppierung. Nach der Auswahl passiert nichts, bis man „Losfliegen" drückt – erst
**danach** steht die Route da („Unterwegs via Halcyon Rand 7 → Talvex 4 → … →
Kestrel-Feld 8 – nächster Sprung in 1s").

`routePreview` (Sprünge + Dauer) ist im Backend vorhanden und wird auch benutzt –
aber nur in `galaxy-map.component.ts:375`, also **nicht dort, wo man den Flug
auslöst**.

### H5. Angreifen geht nur gegen blockierende Flotten – das steht nirgends

Meine 14-Schiffe-Kampfflotte stand im Krieg im Orbit von Testgammas
Heimatkolonie, mit eigener aktiver Blockade. **Es gab keinerlei Angriffsmöglichkeit
und keinen Hinweis warum.** Die Regel („Ohne Blockade nicht angreifbar") steht
ausschließlich als Kommentar in `game-api.ts:409`.

Verschärfend: Solange **ich** den Planeten blockierte, konnte der Verteidiger dort
keine Blockade legen („Dieser Planet wird bereits blockiert.") – und blieb damit
unangreifbar. Erst nachdem ich meine eigene Blockade aufgehoben und der Verteidiger
seine gelegt hatte, erschien „Angreifen". Wer zuerst blockiert, macht sich also
unangreifbar; das ist mindestens erklärungsbedürftig (siehe Frage F5).

Der anschließende Kampf lief gut: laufendes Gefecht mit Tickzähler,
Restzeit, Verlustanzeige und „Zurückziehen".

### H6. Baustoffe fressen einander – ohne jede Warnung, ohne Ausweg in der Oberfläche

Der in `TODO.md` beschriebene Vorprodukt-Konflikt trifft den menschlichen Spieler
in der Oberfläche direkt. Reproduziert:

1. Stahllegierung 60 produziert → Lager 60.
2. Leitermetall 40 produziert → Lager 40.
3. Leiterbündel 40 produziert → Leiterbündel 40, **Leitermetall 0**.

Infrastruktur Stufe 7 braucht Stahllegierung 26 **und** Leitermetall 13 **und**
Leiterbündel 13 gleichzeitig. Nacheinander eingereiht bekommt man sie nie
gleichzeitig ins Lager.

Im großen Maßstab noch drastischer: Ich habe 15 Baustoffe zu je 500 Stück
produziert; danach standen nur die fünf obersten Produkte im Lager, alle zehn
darunterliegenden waren auf 0 – von der jeweils späteren Bestellung still
aufgezehrt. Es gibt **genau eine** richtige Reihenfolge (topologisch, Zutaten
zuletzt), und die verrät die Oberfläche nirgends.

- Der Backend-Befehl `queueProductionBundle`, der genau das löst, kommt im
  Frontend **überhaupt nicht vor** (`grep queueProductionBundle frontend/src` → 0
  Treffer). Nur `npc-bot` und der e2e-Test nutzen ihn.
- Der Hinweis an einem blockierten Ausbau lautet nur „Fehlende Baustoffe." – ohne
  Weg zur Abhilfe. Es fehlt ein Knopf „Fehlende Baustoffe als Bündel einreihen".
- Die „Vorschau" zeigt zwar „aus Lager N" je Kettenschritt, aber nur, wenn man sie
  aktiv anfordert und liest.

### H7. Die Treibstoffanzeige führt in die Irre

Eine Kampfflotte mit 14 Schiffen zeigt „5 / 14.000 Kapseln" – ein praktisch
leerer Balken. Tatsächlich kostet ein Sprung
`JUMP_FUEL_PER_SHIP_PER_HOP = 0.01` je Schiff und Sprung
(`GameConstants.java:162`): Die 7-Sprung-Reise quer durch die Galaxie hat **0,98
Kapseln** verbraucht. Die 5 Kapseln reichen für 35 solcher Reisen.

Die Tankkapazität (`jumpFuelTankPerShip: 1000`) ist damit um den Faktor 100.000
über dem, was je gebraucht wird. Der Spieler sieht einen leeren Tank und traut
sich nicht loszufliegen. Nirgends steht, was ein Flug kostet – nur die
Fehlermeldung nennt es, und die ist gut („benötigt 0.2, im Tank 0.0").

### H8. Zahlenformat ist englisch, teils im selben Satz gemischt

Die Oberfläche ist durchgehend deutsch, die Zahlen sind es nicht (`DecimalPipe`
ohne Locale, also `en-US`):

- „Wohnraum 1,543 / 20,000" – englische Tausendertrennung
- „Bevölkerung: Halten (+0.00/h)", „Verbrauch 0.047 …/h", „reicht 10.6 Tage",
  „Stufe 7: 1,084 Cr · 5.6h"
- Im selben Kartentext: „Stufe 1 fasst **20.000** Einwohner … (Stufe 20 ≈ **10,5**
  Milliarden)" (hartkodiert, deutsch) direkt gefolgt von „Wohnkapazität
  **20,000**" (Pipe, englisch)
- Im Produktdialog wiederum deutsch: „92,3 m³ · 0,012 g Elerium"

Für deutsche Leser ist „1,543 Einwohner" schlicht falsch lesbar.

### H9. Irreversible Aktionen ohne Rückfrage

- **„Krieg erklären"** – ein Klick, sofort Krieg, keine Bestätigung. (Zum
  Vergleich: Der Friedensvertrag hat 7 Spieltage Kündigungsfrist, der Krieg
  beginnt in derselben Sekunde.)
- **„Zurückziehen"** bei einer Verkaufsorder – keine Bestätigung, und die Liste
  **sortiert sich unter dem Cursor um**: Ich habe zweimal an dieselbe Stelle
  geklickt und dabei zwei verschiedene Orders gelöscht.
- **„Kündigen"** bei einem Vertrag – keine Bestätigung, und bei gleichzeitigem
  Friedens- und Handelsvertrag steht nicht dabei, welcher gekündigt wird.
- **„Angreifen"** – der Zielknopf nennt nur „(3 Schiffstypen)", nicht die
  Schiffszahl oder Stärke. Man greift blind an, ohne Rückfrage.

### H10. Registrierung: leere Namen erlaubt, Namen nicht eindeutig

Ich habe das Registrierungsformular mit **beiden Feldern leer** abgeschickt – es
wurde ein Kommandant „Unbekannter Kommandant" mit Heimatwelt „Heimatwelt"
angelegt. Die `required`-Attribute (`new-game.component.html:15,19`) greifen
nicht, der Submit-Knopf ist nur über `busy()` gesperrt, und
`GameSocket.java:626-627` setzt stillschweigend Standardnamen.

Es gibt außerdem **keine Eindeutigkeitsprüfung**. Ergebnis im Test: Zwei
Einträge „UNBEKANNTER KOMMANDANT" in der Anmeldeliste und **zwei identische
Einträge im Empfängerfeld** der Nachrichtenseite – nicht unterscheidbar. Wer
einem der beiden schreiben will, rät.

### H11. Neuladen oder Deep-Link meldet ab

Die Anmeldung überlebt weder ein `F5` noch das direkte Aufrufen einer URL noch
einen neuen Tab. Der Aufruf von `http://…/produktion` landet immer auf dem
Startbildschirm. Damit ist auch jeder Link, den man weitergibt oder als Lesezeichen
speichert (etwa ein Kampfbericht), wertlos.

---

## 3. Mittel

### M1. Kolonienliste ohne Zahlen
Die Karte zeigt vier Balken mit den Beschriftungen „Wohnraum / Sicherheit /
Lebensstd. / Loyalität" – **ohne Werte**. Die Zahlen gibt es erst zwei Klicks
tiefer in der Übersicht. Genau hier bräuchte man sie, um zu erkennen, welche
Kolonie Aufmerksamkeit braucht.

### M2. Statistikseite
- Zwei Kacheln tragen **ganze Sätze als Überschrift** („ALLE CREDITS DER GALAXIE
  ZUSAMMEN – SIE WÄCHST NUR, WENN KOLONIEN ÜBER IHREN BISHERIGEN HÖCHSTSTAND
  HINAUSWACHSEN"). Diese Kacheln werden dadurch doppelt so hoch wie ihre Nachbarn,
  die Zahl rutscht nach unten, das Raster ist sichtbar zerbrochen. Die übrigen
  Kacheln machen es richtig vor: kurzer Titel, Erklärung als Untertitel.
- „DECKUNG GRUNDGÜTER: N 63 % · M 0 % · E 0 %" – Ein-Buchstaben-Kürzel ohne
  Legende.
- „STATUS: STABIL" bei 32 % Lebensstandard, laufendem Blackout und einer
  Bevölkerung, die sich halbiert hat.
- Die Sparklines haben keine Achse und keine Werte.

### M3. Konto
- „Konsum **p_grundnahrung**" – die interne Produkt-ID steht im
  Transaktionsverlauf.
- „Zuletzt ausgegeben / Zuletzt eingenommen" beziehen sich auf „Letzte 50
  Buchungen" – das ist keine Zeitgröße. Es fehlt die eigentlich wichtige Zahl:
  **Bilanz pro Stunde**. Ein „−60 Cr/h" neben dem Guthaben in der Kopfzeile hätte
  den Bankrott aus K6 verhindert.
- Zeitstempel sind Realzeit ohne Datum („12:41:47"), nicht Spielzeit.
- Verkaufserlöse an die eigene Bevölkerung laufen unter „Konsum"; ein eigener
  Posten für Handel mit anderen Kommandanten fehlt.

### M4. Zwei Panels für dasselbe
Im Produktions-Tab stehen „Versorgungsinventar" und „Lagerbestand" untereinander
und listen weitgehend dieselben Bestände (Stab. Elerium 348, Eleriumkapsel 10,
Grundnahrung 15.244, Stahllegierung 60, Leiterbündel 40). Unterschied: Das eine
zeigt Verbrauch/Reichweite, das andere den „Anbieten"-Knopf. Das gehört in **eine**
Tabelle mit zwei zusätzlichen Spalten.

### M5. Produktionswarteschlange
- Es gibt **keine Möglichkeit, die Reihenfolge zu ändern**, obwohl pro Kolonie nur
  ein Auftrag gleichzeitig läuft und die Reihenfolge damit spielentscheidend ist
  (siehe H6).
- Die Liste **sortiert sich sichtbar um**, während man sie ansieht
  (Grundnahrung/Elerium tauschen die Plätze). Keine Positionsnummern.
- Neben „LÄUFT" steht das Wort „bereit" am Ende des Fortschrittsbalkens –
  widersprüchlich.

### M6. „Benötigte Arbeitskräfte" ist die falsche Einheit
Die Vorschau meldet „benötigte Arbeitskräfte: 1.527.533", während die Kolonie
1.503 Einwohner hat. Gemeint sind **Arbeitsstunden** (60 Stück × 120 Ah allein für
die Endstufe), nicht Arbeitskräfte. Das Label suggeriert, man bräuchte eine
Million Einwohner. Außerdem fehlt der Vergleich zum Vorhandenen – die Zahl
„Verfügbare Arbeitskräfte" steht auf einer anderen Seite.

### M7. Verkaufsorders: kein Preisanhalt, kein Ändern
- Der „Anbieten"-Bereich hat **zwei unbeschriftete Eingabefelder** (Menge, Preis).
  Welches welches ist, erschließt sich nur aus den Vorbelegungen.
- Es gibt keinerlei Anhaltspunkt, welchen Preis die Bevölkerung zahlen kann. Ich
  habe 400 Cr gesetzt (analog zur Startorder Grundnahrung mit 450 Cr) und prompt
  die Meldung bekommen, die Bevölkerung könne es sich nicht leisten (Deckung
  33 %). Das Bevölkerungs-Wallet steht auf der Bevölkerungsseite, nicht hier.
- Ein bestehender Preis lässt sich **nicht ändern** – nur zurückziehen und im
  anderen Tab neu anlegen.
- Beim Anlegen verschwindet der komplette Bestand aus dem Lager, ohne dass an
  dieser Stelle sichtbar wird, wohin.

### M8. Handel ist über drei Orte verteilt
Anlegen im **Produktions**-Tab der Kolonie, Verwalten im **Handel**-Tab der
Kolonie, Ansehen auf der **Handel**-Seite. Der Hinweistext muss den Nutzer
zweimal weiterschicken („Neue Orders über ‚Anbieten' im Lagerbestand einer Kolonie
(Tab ‚Produktion')").

### M9. „Öffnen" bei einer Handelsorder springt an die falsche Stelle
Der Link neben einer Order auf der Handel-Seite führt zur **Übersicht** der
Kolonie, nicht zu deren Handel-Tab und nicht zur Order.

### M10. Galaxiekarte
- Von 204 Systemen sind **nur die beschriftet, in denen etwas von einem steht**.
  Der Rest sind namenlose Punkte.
- Keine Suche, kein Sprung zu einem System, kein Zoom-Bedienelement (nur Scrollrad
  bzw. Zwei-Finger).
- Die Legende erklärt „Eigene Flotte / Eigene Kolonie / Feindliche Flotte" – nicht
  die violetten Punkte und nicht den orange gestrichelten Kreis (Handelsgilde?).
- „POLITISCHES GEWICHT: Testalpha 2111" – und wenige Minuten später 831. Was das
  ist und woraus es entsteht, sagt die Oberfläche nicht.

### M11. Produktwähler
- Bei 102 Produkten gibt es **keine Suche** und keinen Filter.
- Es gibt **zwei verschiedene Wähler**: das Raster auf der Produktionsseite (mit
  Symbol, Arbeitsstunden, Dauer, Masse) und den Dialog im Kolonie-Tab (nur Namen,
  keine Werte). Dazu unterschiedliche Kategoriemengen (Produktionsseite: 7
  Kategorien inkl. Bodeneinheiten und Schiffe; Dialog: 5).
- Die Kürzel „AH / H / T" auf den Karten sind nirgends erklärt.

### M12. Systemansicht
- **Planetennamen sind nicht eindeutig:** In Kestrel-Feld 8 heißt Testgammas
  Heimatplanet „Aurelia Prime" – genauso wie mein eigener Heimatplanet im
  Aurelia-System. Beim Blick auf die Flottenliste („Im Orbit von Aurelia Prime")
  ist völlig unklar, welcher gemeint ist.
- Fünf Himmelskörper heißen „Trümmerkörper VI…X", sind aber als „Supererde ·
  Mittel", „Eisplanet · Klein", „Gasriese · Mittel" klassifiziert. Name und
  Klassifikation widersprechen sich.
- Jede der zehn Karten wiederholt denselben langen Erklärtext („Nicht erforscht –
  erst eine hier stationierte eigene Flotte kann die Fördergüten aufdecken.").
  Nach dem Erforschen wächst jede Karte auf 17 Rohstoffzeilen; die Seite wird sehr
  lang, ohne dass man einklappen kann.
- „System erforschen" wirkt sofort und ohne jede Rückmeldung.

### M13. Bevölkerungs-Tab widerspricht sich zwischen zwei Ticks
Im selben Panel stand im Screenshot „▼ −340,5 / Tick · Bevölkerung geht zurück ·
Es ziehen mehr Menschen fort oder sterben, als hinzukommen" und zwei Sekunden
später „▲ 0.0 / Tick · Beschleunigtes Wachstum". Die Momentaufnahme je Tick
schwankt zu stark für eine Aussage in dieser Deutlichkeit; hier gehört dieselbe
Glättung hin, die der Lebensstandard schon hat.
Nebenbei: Die Diagrammachse zeigt 8.339 / 5.053 / 1.767 statt gerundeter Werte.

### M14. Nachrichten
- Nach „Senden" schließt sich das Formular **ohne jede Bestätigung**, und die
  Ansicht bleibt auf dem (leeren) Posteingang. Ob die Nachricht raus ist, erfährt
  man nur durch manuellen Wechsel auf „Gesendet".
- Es gibt **keine Antwortfunktion** – auf eine erhaltene Nachricht muss man über
  „Neue Nachricht" und Empfängerauswahl reagieren.
- Der Seitenkopf ist als einziger in der ganzen Anwendung **zentriert**; alle
  anderen Seiten sind linksbündig.

### M15. Wortwahl bei den Flottenknöpfen
An derselben Flotte stehen je nach Ort: „ABLEGEN", „LADEN", „LANDEN", „TRUPPEN
LANDEN", „BEWEGEN", „VERKAUFEN". „Ablegen" heißt im Sprachgebrauch **auslaufen**,
gemeint ist aber Entladen. „Landen" (Fracht auf den Planeten) und „Truppen
landen" stehen unmittelbar nebeneinander und sind nicht auseinanderzuhalten.

### M16. Flotten lassen sich nicht benennen
Neue Flotten heißen automatisch „Flotte Alpha Prime 3", „… 4", „… 5", „… 6". Es
gibt keine Umbenennen-Funktion. Nach einer Stunde Spiel ist die Flottenliste nicht
mehr lesbar.

### M17. Benachrichtigungsfeld schließt nicht
Das aufgeklappte Glockenfeld überdeckt die rechte Seite der Seite und schließt
weder bei einem Klick daneben noch mit Escape – nur ein erneuter Klick auf die
Glocke schließt es. Ich habe mehrfach unter dem Feld weitergearbeitet, ohne es zu
merken.

### M18. Verteidigungs-Tab
Ein eigener Tab für **eine einzige Gebäudezeile** – und diese Zeile trägt nicht
einmal den Gebäudenamen (nur die Panel-Überschrift „Planetare Abwehr" nennt ihn).
Es fehlen Abwehrstärke, Aktivierungszustand und Anlaufzeit als Zahl.

### M19. Tabwechsel aktualisiert die URL nicht
Nach dem Wechsel vom Produktions- in den Bebauungs-Tab stand in der Adresszeile
weiterhin `?tab=produktion`. Vor/Zurück und Neuladen führen damit an die falsche
Stelle.

---

## 4. Niedrig / Kosmetik

- **Sehr viel ungenutzte Fläche.** Auf fast jeder Seite steht der Inhalt in der
  linken Hälfte, 40–70 % der Breite bleiben leer: Kolonienliste (eine schmale
  Karte, Rest leer), Produktionsseite (eine Kolonie-Karte), Verteidigungs-Tab,
  Bodentruppen-Seite, Nachrichten, Diplomatie („Laufende Gefechte" ist ein
  schmales Kästchen links, rechts daneben nichts).
- Massen in Kilogramm ohne Skalierung: „0 / 113.416.791 kg", „5.000.000 /
  28.354.198 kg". In Tonnen oder Kilotonnen wäre das lesbar.
- Reichweiten in Stunden ohne Umrechnung: „reicht 50.711,9 h" (≈ 5,8 Spieljahre).
- Verbrauchsangaben auf 0 gerundet: „Stabilisiertes Elerium −0/h" bei
  tatsächlichen 0,047/h.
- „offen 0.3" im Versorgungsinventar (Übertragskonto) – ohne Erklärung, was
  „offen" heißt.
- Die Mengenspalte bei „Unzugeordnete Schiffe im Lager" ist nicht ausgerichtet;
  die Zahlen springen je nach Stellenzahl seitlich.
- Der Kampfbericht hat keinen Zurück-Link und keinen Zeitstempel; die
  Seitenleiste markiert dabei weiterhin „Diplomatie / Krieg".
- Die Dialoge (Produkt-Details, Produktwähler) haben einen **horizontalen**
  Scrollbalken und einen inneren vertikalen; der Produktionsbaum wird in einer
  Box fester Höhe abgeschnitten.
- Die Vorschau im Produktdialog bleibt nach dem Einreihen als veraltete Tabelle
  stehen („aus Lager 0", obwohl inzwischen 60 im Lager sind).
- Die Vorschau-Tabelle bricht um: „zu produzieren 60" und „0.0 h" überlagern
  sich in derselben Zeile.
- Beim Anlegen einer neuen Flotte kann kein Name eingegeben werden, obwohl das
  Feld daneben („Menge") frei ist.
- Beim Beladen erscheint der Hinweis „max. 23354" erst **nach** dem Ladevorgang;
  vorher gibt es keinen Anhalt und keinen „Alles laden"-Knopf.
- Die Diplomatie-Karten brechen die vier Knöpfe unregelmäßig um (bei „Testbeta"
  drei in Zeile 1, einer in Zeile 2).
- „Angenommen"/„Abgelehnt" bei Vertragsangeboten stehen nicht nebeneinander:
  „ANNEHMEN" rechts vom Text, „ABLEHNEN" darunter links.
- Der Rekrutierungsblock zeigt „Soldat – 2 kommandieren Drohnen" ohne erkennbare
  Anzahl; erst auf der Bodentruppen-Seite steht „ein Soldat führt bis zu fünf".
- Garnisons-Chips werden auch mit Wert 0 angezeigt („Leichte Drohne × 0").

---

## 5. Handytauglichkeit

Der Testbrowser ließ sich nicht auf Handybreite verkleinern (`resize_window`
blieb wirkungslos, `innerWidth` blieb 1920). Die Bewertung stützt sich deshalb auf
die Stylesheets und die DOM-Struktur, nicht auf Screenshots.

**Gut gelöst:** Die Hülle (`app-shell.component.scss`) hat zwei Breakpoints, einen
Hamburger mit Off-Canvas-Navigation, 44-px-Touchziele, umbrechende Kopfzeile und
gekürzte Statuschips. Das entspricht `Umsetzungskonzept/16_…md`.

**Problematisch:**

| Komponente | `@media` | Risiko |
|---|---|---|
| `fleets-overview` | **0** | Die inhaltlich dichteste Seite überhaupt (Tank, Fracht, Truppen, 6 Knöpfe je Flotte). Rettet sich nur über `flex-wrap`. |
| `system-view` | **0** | `grid-template-columns: 90px 1fr 50px` in den Rohstoffzeilen; 17 Zeilen je Planet. |
| `ground-forces` | **0** | – |
| `messages` | **0** | – |
| `battle-report` | **0** | `min-width: 140px` auf dem Seitenlabel plus lange Schiffslisten in derselben Zeile. |
| `product-picker-dialog` | **0** | Dialog mit dreispaltigem Raster. |
| `colony-detail` | 2 | greift **nicht** auf `.building-row` (siehe H2) – der schlimmste Fall. |

Dazu kommt: Die Galaxiekarte braucht Zwei-Finger-Zoom auf 204 Punkten ohne
Beschriftung; die Systemansicht mit 10 Planeten × 17 Rohstoffzeilen ergibt auf dem
Handy eine sehr lange Scrollstrecke ohne Einklappmöglichkeit.

---

## 6. Vorschläge zur Aufteilung und Anordnung

1. **Verteidigung in Bebauung integrieren.** Ein Tab für eine Gebäudezeile
   lohnt nicht – die Planetare Abwehr ist ein Gebäude mit Bebauungsplatz wie die
   anderen fünf. Der Aktivierungszustand passt gut in dieselbe Zeile.
2. **Handel zusammenlegen.** Anlegen, Verwalten und Ansehen von Verkaufsorders
   gehören an **einen** Ort – naheliegend der Handel-Tab der Kolonie, mit einem
   „Anbieten"-Knopf je Lagerposten direkt dort. Der Lagerbestand kann als
   Auswahlliste in diesen Tab wandern.
3. **Versorgungsinventar und Lagerbestand verschmelzen** (M4) – eine Tabelle mit
   den Spalten Bestand · Verbrauch · Reichweite · Anbieten.
4. **Bilanz in die Kopfzeile.** Neben „Credits 42.913" gehört „−60 Cr/h" (bzw. ein
   rotes Warnzeichen). Das ist die eine Zahl, die den Bankrott aus K6 verhindert
   hätte.
5. **Kolonienliste als Tabelle** mit den Zahlen, die es heute nur als Balken gibt,
   plus einer Statusspalte (Blackout / schrumpft / wächst). Bei mehr als drei
   Kolonien ist die Kartenansicht nicht mehr überblickbar.
6. **Routenvorschau an den Ort der Entscheidung** (H4): Sprünge, Dauer und
   Treibstoffbedarf direkt unter dem Zielsystem-Auswahlfeld – dieselbe Anzeige,
   die die Galaxiekarte schon hat. Das Auswahlfeld selbst sollte nach Entfernung
   sortiert sein, nicht alphabetisch über alle 203 Systeme.
7. **Schiffs- und Truppenwerte in die Bauauswahl** (H3) – als Vergleichstabelle
   statt eines Auswahlfeldes.
8. **„Fehlende Baustoffe produzieren" als Knopf** an jedem blockierten Ausbau
   (H6), der intern `queueProductionBundle` benutzt. Damit wäre die größte
   Einstiegshürde des Spiels an genau der Stelle gelöst, an der sie auftritt.
9. **Bodentruppen-Seite und Bodentruppen-Tab** überschneiden sich fast
   vollständig. Entweder die Seite auf die Übersicht über *alle* Kolonien und
   gelandete Verbände beschränken (dann aber mit Werten) – oder auflösen.
10. **Kampfprotokoll verlinken** (K3) und um Datum, Ort und Verlustsumme
    erweitern; die Diplomatie-Seite ist der richtige Ort dafür.

---

## 7. Offene Fragen

**Zur Spielmechanik**

- **F1 – Aufbewahrung:** Sollen Nachrichten und Benachrichtigungen wirklich in
  Spielzeit verfallen? Mein Vorschlag: Fristen in **Realzeit** (z. B. 7 Tage),
  unabhängig vom Tempo-Regler. Oder gar nicht löschen, sondern nur ausblenden.
- **F2 – Blockade-Vorrang:** Warum macht die eigene Blockade den blockierten
  Planeten für den Verteidiger unblockierbar und damit den Verteidiger
  unangreifbar? Ist das gewollt („wer zuerst kommt")? Es fühlt sich wie eine
  Sackgasse an, weil der Angreifer sich damit selbst den Kampf verbaut.
- **F3 – Angriffsbedingung:** Warum ist eine Flotte ohne Blockade grundsätzlich
  nicht angreifbar? Eine Handelsflotte im Kriegsgebiet kann so nie abgefangen
  werden. Und: Kann man eine Kolonie überhaupt angreifen, oder nur Flotten?
- **F4 – Gebäudeunterhalt:** Ist es beabsichtigt, dass der Ausbau der
  Infrastruktur den Kommandanten wirtschaftlich ruiniert? Bei Infrastruktur 20
  war der Unterhalt größer als die gesamten Konsumeinnahmen. Wenn ja: Wo soll die
  Gegenfinanzierung herkommen – nur aus mehr Kolonien?
- **F5 – Elerium und Infrastruktur:** Der Verbrauch steigt überlinear
  (`eleriumUpkeepLevelExponent: 1.25`), die Nachlieferung hängt an **einer**
  sequentiellen Warteschlange, die von jedem längeren Bauauftrag verdrängt wird.
  Ist die Infrastruktur damit über Stufe ~12 überhaupt haltbar? (Das deckt sich
  mit dem bereits in `TODO.md` dokumentierten Befund, jetzt bei Stufe 20 erneut
  eingetreten: Deckung 50,6 %, Bevölkerung 7.086 → 115.)
- **F6 – Trägerschiff:** Soll `CarrierTransit` noch kommen, oder ist das Konzept
  aufgegeben? Falls es kommt: Zählt der Träger dann seine Fracht über
  `carrierSlotCapacity` (400) – ein Träger fasst also 400 Korvetten oder 4 Kreuzer?
  Und was kostet der Sprung an Treibstoff?
- **F7 – Treibstoffskala:** 1.000 Kapseln Tank je Schiff bei 0,01 Kapseln je
  Schiff und Sprung – ist eine der beiden Zahlen ein Tippfehler? So ist Treibstoff
  faktisch keine Ressource, sondern nur eine Hürde beim allerersten Flug.
- **F8 – Spezialisierung:** Alle 18 Einträge standen auf „Stufe 0 (+0 % Tempo)",
  die Fortschrittsbalken bewegten sich beim Produzieren. Wie viele Einheiten
  braucht Stufe 1, und warum steht das nicht dabei?
- **F9 – Politisches Gewicht:** Der Wert schwankte im Test von 2111 auf 831.
  Woraus berechnet er sich, und was tut er für den Spieler (Gateway-Kontrolle)?
- **F10 – Kaufkraft:** Wonach soll ein Spieler seinen Verkaufspreis bemessen? Das
  Bevölkerungs-Wallet lag bei 4.431 Cr für 1.500 Einwohner, die Startorder
  Grundnahrung bei 450 Cr/Stück. Gibt es einen „richtigen" Preis, oder ist das
  bewusst Ausprobieren?
- **F11 – Mehrere Kommandanten auf einem Planeten** ist laut `TODO.md` gewollt.
  Wie soll der Spieler das in der Systemansicht erkennen? Aktuell steht dort nur
  ein Kolonie-Name je Planet.
- **F12 – Doppelte Planetennamen** (M12): Ist das Absicht (Namen wiederholen sich
  in der Galaxie) oder ein Generator-Fehler? Wenn Absicht, sollte die Oberfläche
  immer „Planet (System)" schreiben.
- **F13 – Kolonisationsschiff:** Es verlangt 6.000 Einwohner (4.000 wandern aus,
  2.000 bleiben) und hat `cargoMassKg = 0`. Ist gewollt, dass eine neue Kolonie
  zwingend einen begleitenden Frachter braucht, sonst fällt sie ab dem ersten Tick
  in den Blackout?

**Zur Oberfläche**

- **F14:** Soll die Anmeldung dauerhaft ohne Passwort bleiben? Für den LAN-Betrieb
  mit mehreren Personen an einem Abend ist das riskant (K7).
- **F15:** Soll der Reset-Knopf für alle Spieler sichtbar sein, oder gehört er
  hinter eine Kennung / einen Startparameter?
- **F16:** Warum überlebt die Sitzung kein Neuladen (H11)? Ein `localStorage`-
  Eintrag mit der Spieler-ID würde genügen und macht Deep-Links (Kampfbericht!)
  erst nutzbar.
- **F17:** Ist „bereit" in der Produktionswarteschlange ein Status oder eine
  Restzeit? Es steht neben dem Etikett „LÄUFT".
- **F18:** Sollen Flotten benennbar sein? Ab etwa fünf Flotten ist die Liste sonst
  nicht mehr zu lesen.
- **F19:** Warum gibt es keine Benachrichtigung für „Bauauftrag fertig",
  „Schiff fertig", „Flotte angekommen", „Order ausverkauft", „Blackout",
  „Bevölkerung schrumpft"? Das sind genau die Ereignisse, wegen derer man in ein
  Aufbauspiel zurückkehrt. Aktuell gibt es nur Diplomatie, Kampf, angehaltene
  Warteschlange und Versorgungslücke.
- **F20:** Ist der Tempo-Regler `gameSpeedMultiplier` im Live-Betrieb als 4
  gedacht? Falls ja, sind alle Realzeit-Rechnungen in K1/K5 dieses Dokuments die
  maßgeblichen.

---

## 8. Was gut funktioniert hat

Damit der Bericht nicht schief steht – das lief im Test einwandfrei und ist
teilweise sehr sorgfältig gemacht:

- **Der Kampfbericht** (Tick für Tick, Teilnehmer und Verluste je Seite,
  Gesamtverluste) – inhaltlich das beste Stück der ganzen Oberfläche.
- **Das laufende Gefecht** in der Flottenliste: Tickzähler, Restzeit,
  Verlustmeldung des letzten Ticks, „Zurückziehen".
- **Flotten zusammenlegen und aufteilen** – klarer Hinweistext („Die gewählte
  Flotte wird aufgelöst; ihre Schiffe, Fracht, Soldaten und ihr Treibstoff gehen
  in ‚…' über."), funktioniert wie beschrieben.
- **Die Ausbau-Vorschau in der Bebauung**: Baustoffe grün/rot mit Lagerbestand
  in Klammern, dazu der genaue Grund, warum ein Ausbau nicht geht.
- **Die Erklärtexte zu den Planetenwerten** und zum Energiespeicher – fachlich
  präzise und ohne Marketing-Ton.
- **Die Fördergüte-Anzeige** im Himmelskörper-Panel und nach dem Erforschen in der
  Systemansicht.
- **Die Fehlermeldungen des Backends** sind durchweg konkret und nennen Zahlen
  („Zu wenig Bevölkerung: 6000 Einwohner nötig (4000 wandern aus, 2000 müssen
  bleiben), vorhanden sind 2783.", „Nicht genug Treibstoff im Tank (benötigt 0.2,
  im Tank 0.0)").
- **Der Versorgungs-Hinweis in der Bebauung** („Die Bevölkerung wächst nicht mehr,
  weil die Versorgung nicht mitkommt … Deckung: Unterhaltungselektronik 0 % ·
  Grundnahrung 80 % · Grundmedizin 0 %") – genau die richtige Information am
  richtigen Ort.
- **Die Diplomatie-Kette** Angebot → Annahme → Vertragsanzeige → Kündigen lief
  fehlerfrei über zwei Kommandanten hinweg.
- **Der Handelsgilde-Bereich** mit Orderbuch, günstigster Station und Depot-Hinweis
  ist verständlich aufgebaut.
