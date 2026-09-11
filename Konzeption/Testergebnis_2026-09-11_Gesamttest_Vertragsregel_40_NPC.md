# Gesamttest 11.9.2026 — Vertragsregel für die Bevölkerung, 40 NPCs, Oberfläche und Backend

**Auftrag:** (1) Die Bevölkerung darf am Handelsposten nur noch aus
Verkaufs-Orders ihres eigenen Kommandanten oder seiner
Handelsvertragspartner kaufen. (2) Die Zahl der NPCs verdoppeln. (3) Danach
ein möglichst umfangreicher Test des Gesamtspiels, auch der Oberfläche in
allen Verästelungen.

**Aufbau des Tests.** Das Live-Spiel auf Port 8080 (20 Bot-JVMs) blieb
unangetastet. Getestet wurde gegen rsync-Kopien des Repos im
Scratch-Verzeichnis: Instanz A auf 8081 (Backend + `BotArmy` mit 40 Bots +
`observer.mjs`, 24 Realminuten ≈ 96 Spieltage bei Tempo 4, Live-Balance mit
`productionSpeedMultiplier = 90`), Instanz B auf 8082 (Verifikationsbuild mit
allen Korrekturen, 4 Bots über das umgestellte `run-army.sh`). Die
Oberfläche lief im Chrome gegen beide Instanzen (nach jedem Deploy
`Strg+Umschalt+R`), angemeldet als zusätzlicher menschlicher Kommandant
„Tester" bzw. „Pruefer" und zur Prüfung des Kampfberichts als der vom
e2e-Skript angelegte „E2E-Angreifer".

---

## 1. Zusammenfassung

| Bereich | Ergebnis |
|---|---|
| Vertragsregel Bevölkerung | umgesetzt in `Economy.ownPostOrders`; 2 neue Unit-Tests (ohne Vertrag kauft sie nichts beim Fremden, mit Vertrag kauft sie die günstigere Fremd-Order) |
| NPC-Anzahl | 20 → 40 (20 je Lager); `run-army.sh` startet dafür EINEN `BotArmy`-Prozess statt 40 JVMs |
| Backend-Tests | 166 grün vor der Sitzung, 167 grün danach (alle Klassen, 0 Fehler) |
| e2e `full-playthrough.mjs` | alle Prüfungen bestanden (Bau, Produktionskette, 10 Gateway-Sprünge, Krieg, Gefechts-Tick formelgenau, Rückzug, Nachrichten, Beibehalten, Tempo-Aufschlüsselung) |
| Oberfläche | 10 Hauptbereiche, 6 Kolonie-Tabs, fremde Kolonie (Übersicht + Handel), Systemansicht, Galaxiekarte mit Flottenbewegung, Benachrichtigungen, Nachricht senden, Vertragsangebot, Kampfbericht, Abmelden/Anmelden, Flottendialoge (Laden, Ablegen, Aufteilen), Verkaufsorder anlegen/umpreisen/zurückziehen, „Fehlende Baustoffe produzieren" – 3 Fehler gefunden und behoben |
| Backend-Fehler | 1 NullPointerException (`energyStorage` bei frischer Kolonie) – behoben, Test ergänzt |
| 40-Bot-Lauf | stabil: 0 Serverfehler nach dem Fix, Server ~370 MB / 6 % CPU, BotArmy ~1 GB; 10 Raumgefechte, 11 Überfälle, 1520 Verträge, 400 Kriegserklärungen, 61 Transporter-Chargen; keine Invasion gestartet (siehe B2) |

---

## 2. Umgesetzt

### 2.1 Bevölkerung kauft nur bei Vertragspartnern

`Economy.ownPostOrders` siebt die Verkaufsseite des Postens: Order-Eigentümer
muss der Kommandant der Kolonie sein oder ein Kommandant, mit dem er einen
Handelsvertrag hat (`TreatyCommands.hasTradeAgreement`); Orders ohne
Eigentümer (Handelsgilde) gibt es am Posten nicht und zählen nicht. Weil
Versorgungswarnung, `populationSupply.orderAvailable` und die
Einnahmeschätzung `consumptionSpendPerHour` dieselbe Liste nutzen, sagen sie
jetzt „keine kaufbare Verkaufsorder", wenn nur ein Fremder ohne Vertrag
anbietet. Texte in Kolonie-Handel-Tab, Handelsübersicht, `trade.model.ts`,
Konzept 37, Mechanik 09 und Spieldesign 05 angepasst.

Tests: `PlanetaryPostTest.thePopulationIgnoresForeignSellOrdersWithoutATradeAgreement`
(Bert unterbietet Annas Startorder mit 30 statt 60 Cr – ohne Vertrag bleibt
seine Order unberührt, die Bevölkerung kauft Annas teurere) und
`…BuysFromAForeignSellOrderWithATradeAgreement`.

Im Browser: Tester bot NPC-Nord-09 einen Handelsvertrag an – der Bot lehnt
Angebote von außerhalb seines Lagers ab (Konzept 14, gewollt), die
Benachrichtigung „hat Ihr Angebot abgelehnt" kam nach einem Bot-Takt.

### 2.2 Vierzig NPCs

`run-army.sh` startete bisher 20 JVMs à rund 600 MB; das Live-Spiel belegt
damit 38 von 61 GB. Vierzig JVMs hätten nicht in den Speicher gepasst.
Deshalb startet `run-army.sh` jetzt EINEN `BotArmy`-Prozess
(`BOTS_PER_CAMP`, Standard 20; `BotArmy --count` Standard 20), schreibt die
PID wie bisher nach `army.pids`, `stop-army.sh` bleibt unverändert.
`build-and-run-lan.sh`, `stop-lan.sh`, `verify-army.mjs` (erwartet 20+20)
und Konzept 14 angepasst. Die Rollen- und Spezialitätenverteilung des
Koordinators arbeitet mit `rank % 5` bzw. `rank / 5`, deckt also auch 20
Mitglieder je Lager ab (12 Invasoren, 4 Raider, 2 Siedler, 2 Verteidiger).

Messwerte des 40-Bot-Laufs: Backend 373 MB RSS, 5,8 % CPU; BotArmy 1,0 GB,
3,3 % CPU; alle 40 Bots registriert, koordiniert (zwei Zuteilungsrunden je
Lager), 1520 Friedens-/Handelsverträge innerhalb der Lager, 400
Kriegserklärungen lagerübergreifend.

---

## 3. Behobene Fehler

### F1 (Backend): `energyStorage` warf NullPointerException bei frischer Kolonie

Jeder Bot löste beim ersten Takt `Unerwarteter Fehler bei Befehl
'energyStorage': NullPointerException: Cannot read field "stored" because "s"
is null` aus (40 Einträge im Serverlog). `EnergyStorageCommands.view`
rechnete `storedCoverageGameHours` mit `s.stored`, obwohl `s` bei einer
Kolonie ohne Speicher-Eintrag (vor dem ersten Kolonietag) null ist – die
Zeile davor hatte den Fall bereits mit `v.stored` abgefangen. *Behoben:*
`v.stored`; Test `EnergyStorageTest.viewWorksBeforeAnyStorageEntryExists`.
Auf der Verifikationsinstanz 8082: 0 Fehler im Serverlog.

### F2 (Oberfläche): Fremde Heimatsysteme hießen „Ihr Heimatsystem"

Die Galaxiekarte zeigte für das Heimatsystem von NPC-Nord-09 das Abzeichen
„Heimatsystem" und den Text „Ihr Heimatsystem.". `StarSystem.isHomeSystem`
ist ein Weltmerkmal (Heimat irgendeines Kommandanten). *Behoben:* Text und
Markierung vergleichen mit dem eigenen Heimatsystem (`homeSystem()?.id`);
die Beschriftung aller Heimatsysteme auf der Karte bleibt. Verifiziert auf
8082: fremdes Heimatsystem „2 · Corvin Öde" zeigt „4 Gateway-Sprung(e) von
204 · Orinth 7 entfernt", das eigene „Ihr Heimatsystem.".

### F3 (Oberfläche): „Feindliche Flotte" für jede fremde Flotte

Legende und Detailtext der Galaxiekarte nannten jede fremde Flotte
„feindlich" – auch die eines Kommandanten, mit dem Frieden herrscht
(`FleetCommands.fleetPresence` zählt alle fremden Schiffe in bereisten
Systemen, unabhängig vom Kriegszustand). *Behoben:* „Fremde Flotte" /
„Fremde Flotten hier".

### F4 (Backend, kosmetisch): Tank über Fassungsvermögen nach Verlusten

Nach dem e2e-Gefecht stand die Kampfflotte mit „12.600 / 10.700 Kapseln" da:
mit den vernichteten Schiffen schrumpft der Tank, der Treibstoff blieb.
`FleetCommands` klemmte erst beim nächsten Sprung. *Behoben:* nach jedem
Gefechts-Tick wird der Treibstoff beider Flotten auf die Tankkapazität
gekappt.

---

## 4. Balance-Befunde ohne Eingriff

### B1: Eine frische Kolonie verliert erst Guthaben, bevor die Einnahmen kommen

Tester: 10 569 Cr bei Registrierung, 3 120 Cr nach 30 Spieltagen, danach
steigend. Ursache ist kein Fehler: die Bevölkerung startet mit vollem
7-Tage-Vorrat und kauft die ersten Tage kaum, während Löhne
(0,02 Cr/Kopf/h) sofort ins Bevölkerungs-Wallet fließen. Selbst danach
deckt Grundnahrung allein (0,0002 × 60 Cr = 0,012 Cr/Kopf/h) die Löhne nicht;
erst Grundmedizin und Unterhaltungselektronik (je 0,0001 × Preis) bringen
den Kreislauf ins Plus. Die Anzeige „−108 Cr je Spielstunde" im Konto ist
rechnerisch korrekt (nachgerechnet gegen Bevölkerung, Bedarfe und Preise).
Das Bevölkerungs-Wallet des Testers hielt nach 30 Tagen 53 893 Cr. Wer das
ändern will: Löhne, Bedarfe oder Startpreise – eine Entscheidung, keine
Reparatur.

### B2: Unter Live-Balance startet in 24 Realminuten keine Invasion

Alle 40 Bots standen am Ende in `PREPARE_INVASION`, blockiert durch
„Transportraum 270/2000 Soldaten (Werft: noch 6 Tage)": eine Landung braucht
rund 2000 Soldaten, eine Werft-Charge liefert 10 Transporter (270 Plätze)
je ~6 Spieltage. Damit dauert der Aufbau je Bot etwa 45 Spieltage, also mehr
als die 96 Spieltage dieses Laufs für alle Stufen (Transporter, Anreise,
Landung). Der Gesamttest vom 9.9. sah Landungen nur mit
`productionSpeedMultiplier = 900`. Für einen LAN-Abend heißt das: Eroberungen
kommen erst nach Stunden. Keine Änderung vorgenommen.

### B3: Raider überfallen eine wiederaufgestellte Mini-Flotte immer wieder

NPC-Nord-14 griff „Flotte NPC-Sued-04-Heimat 2" (Stärke 1) dreimal in Folge
an; NPC-Sued-04 stellt sie nach jedem Verlust aus Lagerschiffen neu auf
(`FLEET_REBUILT`). Regelkonform, aber ein sinnloser Kreislauf, den eine
Mindeststärke des Ziels im Raider-Zielfilter beenden könnte.

### B4: Startorder-Preis unter dem Marktniveau der Bots

Die Bots haben ihre Grundnahrung nach 24 Minuten bei 51 Cr, Grundmedizin bei
149 Cr; die Startorder des Menschen steht bei 60 Cr. Hinweis, kein Fehler.

---

## 5. Was die Oberfläche geprüft bekam

- **Start/Konto:** Registrieren (Kommandant, Heimatkolonie, Spielerart),
  Abmelden, Anmelden per Kommandantenliste (auch als E2E-Angreifer), Konto mit
  Buchungsverlauf, Statistiken mit Sparklines.
- **Kolonie:** Übersicht (Planetenwerte, Fördergüte), Bebauung (Ausbau-Vorschau,
  Energiespeicher-Vorhaltemenge, „Fehlende Baustoffe produzieren" reiht die
  Kette ein und liefert), Produktion (Auftrag, Lager, „Anbieten" → Verkaufsorder),
  Bodentruppen (Rekrutierung, Garnison, Einlagern), Bevölkerung („Versorgung
  und Vorrat" mit Countdown zum Tageseinkauf, Wachstumsverlauf, Bevölkerungs-
  Wallet), Handel (Orderbuch, Preis ändern, Zurückziehen, Kauf-Order).
- **Fremde Kolonie:** nur Übersicht und Handel sichtbar, fremde Orders mit
  Link „Handelsvertrag erforderlich".
- **Galaxie:** Karte mit Suche, „Bewegen" → Zielsystem klicken → „Anfliegen"
  (5 Sprünge, 13 s), Ankunftsbenachrichtigung, Systemansicht mit Zielwahl
  (Kolonie/Orbit), „System erforschen" deckt Fördergüte auf, „Gateway
  blockieren" sichtbar.
- **Flotten:** Tank/Betanken, Laden, Ablegen (Kolonie → Systemhandelsposten),
  Aufteilen-Formular, Landen/Angreifen/Aufteilen bei der Kampfflotte.
- **Diplomatie:** 42 Kommandanten mit Status, Krieg erklären, Friedens-/
  Handelsvertrag anbieten (Angebot „wartet auf Antwort", vom Bot abgelehnt).
- **Nachrichten:** Neue Nachricht an NPC, Posteingang/Gesendet, Antworten,
  Beibehalten.
- **Benachrichtigungen:** Glocke mit Zähler, Texte der Versorgungswarnung mit
  neuem Wortlaut, Links „Kolonie öffnen", „Kampfbericht öffnen", „Flotte öffnen".
- **Kampfbericht:** Tick-Verlauf mit Teilnehmern und Verlusten, Gesamtverluste,
  Status „Beendet/Rückzug".
- Keine Konsolenfehler in Chrome während der gesamten Sitzung.

Nicht ausgelöst (bewusst): „Reset" (setzt die ganze Simulation zurück),
Krieg erklären als Mensch, Rückbau.

---

## 6. Testlauf nachstellen

```bash
# Instanz A: Backend + 40 Bots + Observer auf 8081 (Scratch-Kopie, Live-target unberührt)
scratchpad/stage2-run.sh
node backend/e2e/full-playthrough.mjs ws://localhost:8081/game
# Instanz B: Unit-Tests, Frontend-Build, Backend auf 8082, run-army.sh mit BOTS_PER_CAMP=2
scratchpad/stage3-verify.sh
# Bericht (observer.jsonl muss im Bot-Logverzeichnis liegen)
node npc-bot/report.mjs <logdir>
```
