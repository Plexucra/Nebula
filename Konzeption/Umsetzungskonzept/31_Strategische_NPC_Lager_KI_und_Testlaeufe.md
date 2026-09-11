# 31. Strategische NPC-Lager-KI, Monitoring und Testläufe

Nutzervorgabe: Die 20 NPC-Bots sollen nicht mehr nur handeln und sich
abstimmen, sondern **als Lager auf den langfristigen Sieg über das andere
Lager hinarbeiten** – eigene Kolonien gründen (vorzugsweise im eigenen
System), gegnerische Kolonien erobern, im eigenen Lager Friedens- und
Handelsverträge schließen, die Arbeitsteilung über das Nachrichtensystem
aushandeln und **neu aushandeln**, wenn sich die lokalen Verhältnisse ändern.
Die Intelligenz muss im Algorithmus liegen (keine externe KI), darf mehrere
Strategien für mehrere Szenarien vorhalten und soll **sehr ausgiebig
monitort** werden, um zu prüfen, ob die bisher umgesetzten Mechaniken
(Kampf, Bodenkampf/Eroberung aus Konzept 30, Koloniegründung aus Konzept 24)
tatsächlich tragen.

Dieses Dokument beschreibt die neue Bot-Architektur (`npc-bot`), das
Monitoring, den zweiten Test-Regler im Backend und – als eigentliches
Ergebnis – die Befunde aus den Testläufen.

## A. Ausgangslage: was das Live-Spiel gezeigt hat

Vor dem Umbau lief auf Port 8080 seit rund 4,5 Realstunden (≈ 1 000
Spieltage bei Tempo 4) ein Spiel mit den bisherigen 20 Bots. Eine
lesende Momentaufnahme über das reguläre Protokoll ergab:

| Befund | Zahl |
| --- | --- |
| Bot-Kolonien mit Bevölkerung 0–2 (Start: 2 000) | **20 von 20** |
| Bot-Kolonien im Blackout (Energiedeckung ≈ 0) | 20 von 20 |
| laufender Auftrag in jeder Warteschlange | `p_energienetzbaugruppe ×15` (Baustoff für Infrastruktur 8), Elerium dahinter wartend |
| Werften gebaut | 0 (die Bauliste wurde nie bis zur Werft abgearbeitet) |
| Kampfflotten mit 0 Schiffen (Geisterflotten nach verlorenem Gefecht) | 7 |
| zwei später registrierte Kolonien (ein Mensch, ein versehentlich doppelt gestarteter Bot) | Bevölkerung 11 000 – 12 000, gesund |

Die Todesursache ist exakt die aus `TODO.md` bekannte: die **eine**
sequentielle Warteschlange je Kolonie wurde von einem stundenlangen
Baustoffauftrag belegt, der Elerium-Dauerauftrag kam nie an die Reihe,
Infrastruktur 7 verbrauchte das 2,4-Fache der Auslegung, Blackout,
Todesspirale. Dazu exportierten die Bots ihre Grundnahrung bis auf 5 Stück
an die Handelsgilde – die eigene Bevölkerung hungerte.

Daraus folgt die erste Regel der neuen KI: **Energie vor allem anderen**,
und die Bevölkerungsreserve ist unantastbar.

## B. Machbarkeit unter der aktuellen Balance

Bevor die Bots erobern und gründen können, müssen sie Mannschaftstransporter
bzw. Kolonisationsschiffe bauen. `previewProductionChain` auf einer frischen
Heimatwelt (Industrie 5, 2 000 Einwohner, keine Spezialisierung, Tempo 4):

| Produkt | Spielstunden | Realzeit bei Tempo 4 |
| --- | ---: | ---: |
| 10 Soldaten | 1 084 | 11 min |
| 10 leichte Drohnen | 1 362 | 14 min |
| 1 Korvette | 38 778 | 6,7 h |
| 1 Frachter | 94 833 | 16 h |
| **1 Mannschaftstransporter** | **208 085** | **36 h** |
| 1 Zerstörer | 286 367 | 50 h |
| **1 Kolonisationsschiff** | **674 204** | **117 h (≈ 5 Tage)** |

Die Vorschau rechnet dabei noch mit dem Tempo des Industriekomplexes. Ein
echter Werftauftrag plant seine **gesamte** Vorkette mit dem Tempo der Werft
(`ShipyardCommands` → `ChainPlanner.planChain(…, "b_shipyard")`), also mit
Stufe 1 statt 5 – der Transporter dauerte im ersten Testlauf tatsächlich
**422 Spieltage** statt der vorausgeschauten 85. Dasselbe gilt für das
Ausbildungszentrum: eine Rekrutierungskette läuft komplett mit Akademie-Tempo.

**Kaufen statt bauen** ist keine Abkürzung: Schiffe und Bodeneinheiten sind
nicht Market-Maker-fähig (`HubMarketCommands.isMarketMakerEligible`), die
sechs Module eines Transporters kosten an der Station zusammen rund
**710 000 Cr**, ein Bot startet mit 6 500 Cr.

Damit stand fest: **Unter der aktuellen Balance kann kein Bot innerhalb
eines Testlaufs – und auch nicht innerhalb eines mehrtägigen LAN-Spiels –
eine Landung oder eine Koloniegründung erreichen.** Die in Konzept 27 §F
offengelassene Frage nach der Expansionsschwelle hat damit eine Zahl.

### Der zweite Test-Regler

Um die Mechaniken trotzdem durch die Bots prüfen zu können, gibt es in
`shared/game-constants.json` neben `gameSpeedMultiplier` einen zweiten,
davon unabhängigen Regler:

```json
"productionSpeedMultiplier": 1
```

`ChainPlanner.computeProductionHours` und
`computeProductionHoursWithoutWorkforce` teilen **jede** Fertigungsdauer –
inklusive der Arbeitskraft-Bremse, sonst bliebe sie als Untergrenze stehen –
durch diesen Wert (`GameConstants.PRODUCTION_SPEED_MULTIPLIER`,
`SharedConstants.productionSpeedMultiplier()`, Frontend-Export
`PRODUCTION_SPEED_MULTIPLIER` nur zur Kennzeichnung). Die feste Bauwoche des
Kolonisationsschiffs bleibt unberührt. **Standard ist 1 – das Live-Spiel
ändert sich nicht.** Anders als der Tempo-Regler verschiebt dieser Wert das
Verhältnis von Fertigung zu Reisen, Kampfticks und Bevölkerung; er ist
ausschließlich ein Testwerkzeug, kein Balancing-Vorschlag.

## C. Architektur der neuen Bot-KI (`npc-bot`)

Der bisherige Ein-Klassen-Zustandsautomat (`Bot.java`, 900 Zeilen) ist durch
eine kleine strategische Schicht ersetzt. Jeder Bot bleibt ein eigener
Kommandant über das reguläre WebSocket-Protokoll, ohne Sonderrechte.

```text
World (Weltsicht, je Takt gecacht)
  └─> Situation (Lage: Energie, Versorgung, Bedrohung, Flotte, Truppen, Kolonien)
        └─> Coordination (Lagerabstimmung per Nachricht: Spezialität, Militärrolle, Ziel)
              └─> Strategy.choose  → EINE Strategie je Takt (mit Hysterese)
                    └─> Strategy.plan → Plan (Bauprioritäten, Export, Einkauf, Militärgüter)
                          └─> Diplomacy · Economy · Trade · Military · Expansion
                                └─> Monitor (Log + JSONL je Takt und Ereignis)
```

| Klasse | Aufgabe |
| --- | --- |
| `World` | Alle Serverabfragen, je Entscheidungstakt einmal gecacht; Gateway-Graph mit BFS-Sprungdistanz; Katalog-Rezepte (`productTypes`). |
| `Strategy` | Strategien `EMERGENCY_POWER`, `FAMINE`, `UNDER_ATTACK`, `RECOVER`, `PREPARE_INVASION`, `INVADE`, `RAID`, `SETTLE`, `BUILD_UP`; Militärrollen `INVADER`, `RAIDER`, `SETTLER`, `DEFENDER`. Reihenfolge = Priorität: Überleben vor Verteidigung vor Rollenaufgabe. |
| `Coordination` | Das Lagerprotokoll (siehe §D). |
| `Economy` | Jede eigene Kolonie: Energie zuerst (Dauerauftrag, Umsortieren der Warteschlange), Grundbedarf samt **eigener Verkaufsorders**, Spezialisierungscharge, Ausbau mit Baustoff-Bündeln, Werft- und Rekrutierungsaufträge hinter Energie- und Versorgungs-Wache (die Vorfertigung von Modulen im Industriekomplex aus den ersten Läufen ist seit §I überflüssig). |
| `Trade` | Der Startfrachter: Versorgungsfahrten zu jungen Kolonien, Leihgabe an das Militär als Drohnentransporter, sonst Handelsfahrt mit Kreditreserve. |
| `Diplomacy` | Krieg gegen jeden des anderen Lagers, Friedens- **und** Handelsvertrag mit jedem des eigenen Lagers, gegnerische Friedensangebote ablehnen, Menschen unbehelligt. |
| `Military` | Raum: Heimatblockade, Raids mit Stärkeschätzung, Rückzug, Wiederaufbau. Boden: die komplette Landungsoperation (§E). |
| `Expansion` | Kolonisationsschiff und Gründung im eigenen System, danach Versorgung. |
| `Monitor` | Menschenlesbares Log und JSONL-Spur je Bot. |
| `BotArmy` | Alle Bots in einem Prozess (ein Thread und eine Verbindung je Bot) statt einer JVM je Bot; seit 11.9.2026 der Weg von `run-army.sh` (40 Bots, `-Xmx1g`, Serial-GC). Die 600 MB je JVM waren nur der Anfang: ohne `-Xmx` wuchs jede Bot-JVM im LAN-Betrieb auf 1,3–3,8 GB RSS für rund 130 MB lebende Daten, davon der Großteil das ungelesene, 30 Tage aufbewahrte Postfach mit Statusnachrichten (siehe `RetentionCleanup.purgeNpcMail`). |

### Was die Wirtschaft anders macht als bisher

- **Elerium-Dauerauftrag** in jeder Kolonie, Charge für zehn Tage. Fällt die
  Reichweite unter 36 Stunden, wird die Warteschlange umsortiert: laufender
  Fremdauftrag abgebrochen (anteilige Gutschrift durch den Server), wartende
  Fremdaufträge verlustfrei hinter das Elerium gestellt. Infrastruktur wird
  nur ausgebaut, wenn die Reserve über 150 Stunden reicht.
- **Eigene Verkaufsorders für Medizin und Elektronik.** Die Bevölkerung kauft
  ausschließlich aus Orders ihres Systems; die Startausstattung legt seit
  Konzept 20 nur noch eine Nahrungsorder an (Konzept 23 §B ging noch von
  drei aus). Ohne Medizinorder blieb der Lebensstandard exakt bei 50 %
  (Nahrung zählt doppelt), die Bevölkerung im Totband, die Geldschöpfung bei
  Null. Mit der Order: 75 %, Wachstum, steigende Wallets (siehe §G).
- **Reserve statt Export.** Exportiert wird nur, was über der doppelten
  Bevölkerungsreserve liegt, und höchstens 30 Stück je Fahrt – mehr nimmt der
  Markt nicht ab (§G).
- **Vorfertigung.** Module von Schiffen und Zutaten von Einheiten entstehen
  im Industriekomplex (Stufe 5–6), Werft und Ausbildungszentrum montieren nur.

## D. Das Lagerprotokoll (`Protocol`, `Coordination`)

Nur das Ingame-Nachrichtensystem, Betreff = Typ, Text = `key=value` je Zeile
(für einen Menschen im Posteingang lesbar):

| Nachricht | Richtung | Inhalt |
| --- | --- | --- |
| `NPC:STATUS` | Mitglied → Koordinator, alle 3 Takte | Kolonien, Bevölkerung, Blackout, `capable`, Spezialität, Rolle, Flotte, Transporter, Soldaten, Drohnen, Strategie, Invasionsphase, Bedrohung, Wallet |
| `NPC:ASSIGN` | Koordinator → jedes Mitglied, alle 6 Takte | Spezialität, Militärrolle, Zielkolonie/-system/-planet, Laufnummer – zugleich Lebenszeichen |
| `NPC:CLAIM` / `-OK` / `-DENY` | Mitglied ↔ Koordinator | Anspruch auf einen Planeten (Besiedlung) |
| `NPC:REPORT` | Mitglied → Koordinator | Eroberung, Gründung, Verlust |

**Koordinator** ist der lebende Bot mit dem niedrigsten Index. Bleibt sein
`ASSIGN` länger als 18 + 3·Index Takte aus, übernimmt der nächste
(`COORDINATOR_TAKEOVER`); erhält ein Koordinator ein `ASSIGN` eines
niedrigeren Index, tritt er zurück. Bis zur ersten Zuteilung arbeitet jeder
Bot mit einer vorläufigen Selbstzuteilung nach Index – kein Bot steht still.

**Arbeitsteilung.** Wirtschaftlich nach Index: Nahrung, Medizin im Wechsel,
jeder fünfte ein Tier-2-Baustoff. Militärisch nach Index: `INVADER`,
`RAIDER`, `SETTLER`, `DEFENDER` im Wechsel; ein bedrohtes Mitglied ist
vorübergehend `DEFENDER`.

**Neuverhandlung.** Meldet ein Mitglied dreimal `capable=false`
(anhaltender Blackout, Industrie verloren) oder verstummt es, wandert seine
Spezialität zum Mitglied mit der unkritischsten Rolle (Nahrung > Medizin >
Baustoff), beide erhalten eine neue Zuteilung (`ROLE_RENEGOTIATED`,
`MEMBER_LOST`). Nahrung und Medizin dürfen nie unbesetzt sein. Invasionsziele
werden eindeutig vergeben und neu vergeben, sobald eine Kolonie erobert ist
oder ein Angreifer ausfällt (`TARGET_REASSIGNED`).

**Zielwahl.** Für jeden `INVADER` aus seiner Sicht:
`Garnisonswert × 2 + Bevölkerung × 0,01 + Sprünge × 30 + gegnerische
Flottenstärke im System × 2` – kleinster Wert gewinnt. Garnisonen fremder
Kolonien sind öffentlich abfragbar (`groundForces`), Flotten ebenfalls
(`allFleets`) – Aufklärung ist im Prototyp gratis. `RAIDER` k eskortiert
`INVADER` k ins selbe System.

## E. Die Landungsoperation (`Military`, Phasen)

```text
BUILDING  Transporter (Werft), Soldaten + Drohnen (Akademie), Frachter reservieren
LOADING   Soldaten einschiffen, Drohnen einlagern → als Fracht in den Frachter, betanken, alle drei Flotten los
TRAVELING bis Transport- und Frachtflotte im Zielsystem stehen
ORBIT     Eskorte greift blockierende Feindflotte am Zielplaneten an (≥ 0,8× Stärke); Transporter+Frachter in den Orbit; land ×2
LANDED    Verband suchen, attackableColoniesForGroup, engageGroundBattle
FIGHTING  Kampfphase: Rückzug ohne aktive Drohnen; Belagerung: Rückzug, wenn die Loyalität 6 Ticks nicht sinkt
RETURNING nach Eroberung Versorgungsfahrt anfordern, Flotten heim, Frachter freigeben
```

Bedarfsrechnung aus der Garnison des Ziels: Drohnenklasse = Konter der
häufigsten Klasse dort (`ground-units.json`), Drohnen = Zielwert × 1,5 /
(eigener Wert × 2), Soldaten = max(12, Drohnen / 5, **5 % der
Zielbevölkerung**) – die 5 % sind der Belagerungsdruck aus Konzept 30 §B:
weniger als die Loyalitätsregeneration gewinnt nie. Jede Niederlage erhöht
den Bedarf der nächsten Welle um 100 %.

## F. Monitoring

- Je Bot `logs/<Name>.log` (jede Entscheidung mit Begründung, alle 12 Takte
  eine Zusammenfassungszeile) und `logs/<Name>.jsonl` – ein Datensatz je Takt
  (≈ 45 Kennzahlen) und je Ereignis (≈ 60 Ereignistypen von `STRATEGY` über
  `QUEUE_REORDERED`, `ROLE_RENEGOTIATED`, `RAID_LAUNCHED`, `LANDED`,
  `CONQUERED` bis `COLONY_FOUNDED`).
- `observer.mjs`: unabhängige Serversicht in festem Abstand, meldet sich
  nacheinander als jeder Bot an (kein Beobachterzugriff im Protokoll), schreibt
  `observer.jsonl` und druckt je Bot eine Zeile; setzt keinen verändernden
  Befehl ab.
- `report.mjs`: fasst beides zu einem Markdown-Bericht zusammen –
  Ereignisse je Mechanik mit erstem Auftreten, Endzustand je Bot, Verlauf in
  10-Minuten-Fenstern, Koordination, aktuelle Blocker.
- `verify-army.mjs` prüft weiterhin die Grundnachweise aus Konzept 14 (jetzt
  gegen `NPC:ASSIGN`).

## G. Befunde aus den Testläufen

Zwei Läufe mit je 20 Bots auf getrennten Instanzen (8081/8082), Tempo 4,
beide mit dem neuen Bot; Lauf A mit `productionSpeedMultiplier 1`
(unveränderte Balance), Lauf B mit 100. Ergebnisse und Zahlen: siehe die
Berichte `report.mjs` beider Läufe (Abschnitt H unten wird nach Abschluss
der Läufe ergänzt).

### Mechanik-Befunde (unabhängig vom Regler)

1. **Blockaden sperren nichts.** `moveFleet`, `moveFleetWithinSystem` und
   `land` prüfen keine Blockade; eine Blockade macht eine Flotte lediglich
   angreifbar. Die vom Nutzer genannte Regel „Blockaden nur mit Friedens-
   oder Handelsvertrag durchfliegen" existiert im Backend nicht – die Bots
   pflegen die Verträge trotzdem, damit sie bei Einführung sofort greift.
   Folge für die Bots: Transporter und Frachter fliegen unbehelligt in den
   Orbit einer blockierten Kolonie, weil nur blockierende Flotten angreifbar
   sind.
2. **Werft- und Akademieketten laufen mit Anlagentempo.** Siehe §B; Faktor 5
   gegenüber dem Industriekomplex. Die Bots umgehen das per Vorfertigung –
   ein menschlicher Spieler muss das wissen.
3. **Nur Nahrung hat eine Start-Verkaufsorder** (Konzept 20), Konzept 23 §B
   beschreibt drei. Ohne eigene Medizinorder bleibt jede Kolonie bei 50 %
   Lebensstandard und wächst nie.
4. **Handelsgilde als Geldquelle ist eine Sackgasse.** Der Market-Maker
   nimmt je Besuch ein 5er-Los und senkt danach sein Gebot um 10 %, ohne
   jemals zurückzukehren; eine Station absorbiert je Ware und Ewigkeit rund
   50 Stück Umsatz. Zugleich kostet Grundnahrung an der Station 6,24 Cr,
   während die Start-Verkaufsorder der eigenen Bevölkerung 450 Cr je Stück
   verlangt – Faktor 72. Geld entsteht praktisch nur über den lokalen Konsum
   und die Geldschöpfung beim Bevölkerungswachstum.
5. **Unterhaltungselektronik ist für eine Startkolonie unbezahlbar**
   (264 Cr je Stück an der Station, 4,8 Stück je Spieltag bei 2 000
   Einwohnern ≈ 1 270 Cr/Tag bei 6 500 Cr Startkapital) und lokal eine
   Tier-5-Kette; der Lebensstandard ist damit auf 75 % gedeckelt.
6. **Jedes zusätzliche Gebäude kostet eine Infrastrukturstufe**
   (`slotsPerInfrastructureLevel: 1`): Werft 1 + Akademie 1 verlangen
   Infrastruktur 8, ab Stufe 8 mit `p_energienetzbaugruppe ×15` – exakt der
   Auftrag, der im Live-Spiel jede Warteschlange blockiert hat. Der
   Elerium-Bedarf steigt dabei von 0,047/h (Stufe 6) auf 0,067/h (Stufe 8).
7. **Geisterflotten nach Gefechten.** Eine im Raumkampf vollständig vernichtete
   Flotte bleibt als Flotte mit 0 Schiffen bestehen (7 Stück im Live-Spiel);
   `consumeShips` räumt nur bei der Koloniegründung auf.
8. **Raumkampf funktioniert wie beschrieben**: Stärkeschätzung 1:10:100,
   Kampfticks alle 8 Spielstunden, Rückzug, Sieg/Niederlage – in beiden Läufen
   mehrfach beobachtet (Abschnitt H).

### Wirtschafts-Befunde (aus fünf Iterationen der Testläufe)

Jeder der folgenden Punkte hat in einem Zwischenlauf **alle oder viele
Bot-Kolonien** zum Kippen gebracht; die neue KI enthält für jeden eine
Gegenmaßnahme, die im Log als eigenes Ereignis sichtbar ist.

9. **Schiffsketten fressen die Energiereserve.** Stabilisiertes Elerium ist
   Zutat der Sprungtreibstoffkette, und die steckt in jedem Schiff:
   Mannschaftstransporter 188, Korvette 50, Zerstörer 455 Stück. Der
   Kettenplaner deckt jeden Zwischenschritt zuerst aus dem Lager – beim
   Start des Auftrags, vollständig – und belegt danach die einzige
   Warteschlange für Tage. Ergebnis im Zwischenlauf: Elerium 0, Blackout,
   und die Umsortier-Logik lief in einer Endlosschleife (123 Umsortierungen
   in vier Minuten), weil der neu gestartete Auftrag sich die frische Charge
   sofort wieder nahm. Gegenmaßnahme `ENERGY_GUARD`: ein großer Auftrag
   startet erst, wenn Kettenbedarf + Infrastrukturverbrauch über seine
   Laufzeit + zehn Tage Reserve im Lager liegen.
10. **Ein langer Auftrag lässt die Bevölkerung verhungern.** Hinter einem
    Transportermodul (bei Tempo 100 rund 600 Spielstunden Warteschlange)
    kamen Nahrungs- und Medizinchargen nicht mehr an die Reihe; der
    Nahrungs-Spezialist selbst fiel auf Lebensstandard 0, seine Bevölkerung
    halbierte sich binnen zwei Minuten, während sie 27 000 Cr unausgegeben
    hielt und der Kommandant mangels Einnahmen auf 0 Cr fiel (Löhne fließen
    weiter, Konsum nicht). Gegenmaßnahmen: `SUPPLY_GUARD` (dieselbe Rechnung
    wie beim Elerium für Nahrung und Medizin) und **Stückelung** – ein Modul,
    ein Rohstoff, höchstens 40 Einheiten-Zutaten je Auftrag, damit die
    Warteschlange zwischendurch frei wird.
11. **Der Startpreis von 450 Cr ist nur im Wachstum bezahlbar.** Einkommen
    der Bevölkerung sind Löhne (0,02 Cr je Kopf und Stunde) plus die
    Geldschöpfung beim Wachstum (8 Cr je neuem Einwohner); ihr Bedarf sind
    0,0004 Stück je Kopf und Stunde über alle drei Güter. Ohne Wachstum kann
    sie also rund 50 Cr je Stück zahlen – bei 450 Cr fiel jede Kolonie um
    10 000 Einwohner in die Hungersnot, während die Kommandanten 50 000 Cr
    hielten. Gegenmaßnahme `PRICE_ADJUSTED`: Versorgung unter 85 % senkt den
    Preis um 30 %, Versorgung am Anschlag hebt ihn; für einen
    budgetgebundenen Käufer ist der Erlös dabei derselbe. Beobachtete
    Gleichgewichtspreise: Nahrung ≈ 100 Cr, Medizin ≈ 75 Cr.
12. **Löhne skalieren mit der Bevölkerung, Einnahmen nicht.** Ein Kommandant
    mit 18 000 Einwohnern zahlt 8 600 Cr Löhne je Spieltag. Solange die
    Bevölkerung einkaufen kann, kommt das Geld zurück; sobald ein Gut fehlt,
    bleibt es im Bevölkerungs-Wallet liegen, und der Kommandant ist in
    Minuten zahlungsunfähig.
13. **Nahrungskapazität gegen Bevölkerungswachstum (Balance 1×).** Ein
    Industriekomplex 5 liefert – die ganze Warteschlange für Nahrung
    vorausgesetzt – rund 1,2 Grundnahrung je Stunde; das trägt etwa 6 000
    Einwohner. Der Geldkreislauf treibt die Bevölkerung aber ungebremst auf
    die Wohnkapazität von 20 000 (logistisches Wachstum, Konzept 17), sodass
    im Lauf A jede Kolonie bei 10 000–13 000 Einwohnern auf Lebensstandard
    25 fiel und die vier Invasoren, deren Warteschlange zusätzlich
    Einheiten-Zutaten fertigt, auf 300–2 500 Einwohner zusammenbrachen.
    Unter der aktuellen Balance kann eine Kolonie ihre eigene Bevölkerung
    nicht ernähren, sobald sie wächst – der Bot kann das nur mildern
    (Preise, Reserven), nicht beheben.
14. **Unterhalt eroberter/gegründeter Kolonien** ist in der KI vorgesehen
    (Versorgungsfahrt mit Elerium, Nahrung, Medizin; Verkaufsorders werden
    auch für Kolonien ohne Startorder angelegt), konnte aber nur so weit
    beobachtet werden, wie die Läufe kamen (Abschnitt H).

## H. Ergebnisse der Testläufe (8./9. September 2026)

Vollständige Berichte: `npc-bot/reports/testlauf-A-balance-1x.md` und
`npc-bot/reports/testlauf-B-fertigung-100x.md` (erzeugt mit `report.mjs`).
Beide Läufe: 20 Bots, Tempo 4, frische Galaxie, rund 70 Realminuten; Lauf A
mit unveränderter Balance, Lauf B mit `productionSpeedMultiplier 100`.

### Was in beiden Läufen funktioniert hat

| Mechanik | Beobachtung |
| --- | --- |
| Registrierung, Lagerbildung | 20 Bots, 100 Kriegserklärungen (jeder gegen jeden des anderen Lagers) |
| Verträge im eigenen Lager | 360 Angebote, 360 Annahmen = 90 Friedens- und 90 Handelsverträge je Lauf, alle binnen der ersten Minute |
| Koordination per Nachricht | Zuteilungsrunden alle 30 s, `STATUS` alle 15 s; Rollen und Ziele eindeutig vergeben, Neuverhandlung nach Blackout-Meldungen (`ROLE_RENEGOTIATED`), Zielwechsel nach Eroberung (`TARGET_REASSIGNED`); Koordinator-Übernahme nach Neustart der Prozesse |
| Raumkampf | 23 Raids, 22 Gefechte in Lauf B; Stärkeschätzung 1:10:100 traf den Ausgang jedes Mal (Angreifer mit ≥ 1,2× Stärke siegte in 1–4 Ticks, meist ohne Verlust) |
| Wirtschaft (mit den Gegenmaßnahmen aus §G) | keine Blackouts über die gesamte Laufzeit, Lebensstandard 75 % (Obergrenze ohne Elektronik), Bevölkerung von 2 000 auf die Wohnkapazität 20 000 |
| Neustartfähigkeit | Bot-Prozesse konnten während des Laufs neu gestartet werden; sie melden sich am bestehenden Kommandanten an statt einen zweiten zu registrieren |

### Lauf A (Balance 1×): die Wirtschaft trägt die Bevölkerung nicht

Kein Bot erreichte in 70 Minuten eine Werft oder ein Ausbildungszentrum:
die Baustoffketten (Infrastruktur 7 braucht Leiterbündel, Infrastruktur 8
`p_energienetzbaugruppe ×15`) dauern jeweils Spieltage, und davor verlangt
die Energie-Wache die Eleriumreserve für diese Dauer. Die Bevölkerung wuchs
derweil auf 10 000–13 000 und fiel dann auf Lebensstandard 25 (Befund 13):
Industrie 5 ernährt keine 10 000 Einwohner, egal wie die Warteschlange
sortiert wird. Die vier Invasoren, deren Warteschlange zusätzlich
Einheiten-Zutaten fertigt, brachen auf 35–2 500 Einwohner zusammen. Landung,
Eroberung und Gründung sind unter dieser Balance **nicht erreichbar** – mit
oder ohne KI.

### Lauf B (Fertigung 100×): die Kriegsmechanik trägt

Zeitlinie (Realminuten ab Start):

| Minute | Ereignis |
| ---: | --- |
| 0,5 | erster Raid, 0,8 erstes Raumgefecht |
| 18 | erste Kriegsschiffe aus Modul-Vorfertigung (Werft-Endmontage „Kettenvorschau 0 h") |
| 37 | erster Mannschaftstransporter in Dienst (Modul-Vorfertigung, Endmontage 1 h) |
| 48 | erste Landungsoperation gestartet (1 000 Soldaten, 15 mittlere Drohnen, Eskorte) |
| 63 | erste Landung: 1 000 Soldaten und 15 Drohnen kommen vollständig an (keine Landungsabwehr – der Verteidiger hatte keine Planetare Abwehr) |
| 63 | Bodengefecht `gbt_93b3`: Kampfphase 2 Ticks (15 mittlere gegen 10 leichte Drohnen – Konter ×2, Verteidiger nach Tick 2 ohne aktive Drohnen), dann 17 Belagerungsticks |
| 64,5 | **Eroberung**: NPC-Nord-02-Heimat geht an NPC-Sued-01 (`COLONY_GAINED`/`COLONY_LOST`), der Koordinator vergibt dem Angreifer sofort ein neues Ziel |
| 67 | zweite Landung (NPC-Nord-09 auf NPC-Sued-07-Heimat), Bodengefecht `gbt_9pjq` |
| 69 | **zweite Eroberung**: NPC-Sued-07-Heimat geht an NPC-Nord-09 – ebenfalls 2 Kampf- und 17 Belagerungsticks, 55,6 % Zivilverluste; diesmal vom Bot selbst bis zum `CONQUERED`-Ereignis begleitet, Versorgungsfahrt angefordert |

Das Bodengefecht Stelle für Stelle gegen Konzept 30:

| Tick | Phase | Soldaten | Rebellen | Loyalität | Verluste | Zivilverluste |
| ---: | --- | ---: | ---: | --- | --- | ---: |
| 1 | Kampf | 1 000 | – | 100 → 100 | Verteidiger 7 leichte Drohnen + 1 Soldat | 7 000 |
| 2 | Kampf | 1 000 | – | 100 → 100 | Verteidiger 3 Drohnen + 1 Soldat; Angreifer 1 mittlere Drohne | 3 000 |
| 3 | Belagerung | 1 000 | 1 028 | 100 → 90,3 | 25 Soldaten | 83 |
| 17 | Belagerung | 630 | 1 140 | 13,8 → 8,3 | 28 Soldaten | 52 |
| 19 | Belagerung | 574 | 1 159 | 6,3 → **1,3** | 28 Soldaten | 47 |

- Der Loyalitätsverlust je Tick entspricht exakt `100 × Soldaten / Bevölkerung`
  (1 000 / 10 300 ≈ 9,7 Punkte), die Rebellen exakt 10 % der Bevölkerung, die
  Verluste `floor(Kämpfer × Stärke / 12)` – Konzept 30 §B trägt.
- **Zivilverluste in der Kampfphase**: 50 % der Bevölkerung (10 000 von
  20 000) in zwei Ticks, weil die Garnison von 10 Drohnen restlos aufgerieben
  wurde – die Quote aus §2 („restlos aufgerieben = 50 %") wirkt unabhängig davon,
  wie klein die Garnison war. Eine Startgarnison von 10 Drohnen kostet damit
  die halbe Bevölkerung; das ist eine Balancing-Frage.
- Gesamt: 55,6 % Zivilverluste, Angreifer verliert 426 von 1 000 Soldaten,
  die Kolonie wechselt bei 1,3 % Loyalität mit rund 9 000 Einwohnern den Besitzer.
- Der Verlierer hat danach **keine Kolonie mehr**; das Spiel kennt keine
  Eliminierung, der Bot bleibt angemeldet (`ELIMINATED`) und die Lager-KI
  vergibt seine Rolle neu.

Nicht erreicht in Lauf B: die **Koloniegründung**. Die Siedler hatten alle
Voraussetzungen bis auf die Prämie – Loyalität 100 %, 20 000 Einwohner, die
Rohstoffe für das Kolonisationsschiff (1,27 Mio. Einheiten) waren in
Fertigung –, aber nach Erreichen der Wohnkapazität versiegt die Geldschöpfung,
und ohne Elektronik-Order bleibt ein Drittel der Löhne im Bevölkerungs-Wallet
(Befund 12): die Kommandanten fielen von 150 000 auf unter 1 000 Cr, die
16 000 Cr Kolonistenprämie blieben unerreichbar. Die lokale Elektronik-
Produktion ist als Gegenmaßnahme eingebaut (Kettenvorschau bei Tempo 100:
10 Stück in 11 Spielstunden, bei Balance 1× in 2,3 Spieltagen) und lief erst
in den letzten Minuten des Laufs. Dazu bestellte der Siedler die Rohstoffe
in falscher Reihenfolge nach – Kohlenstoff ist zugleich Zutat der
Grundnahrung und schwankt laufend, der Bot füllte in jedem Takt 84 Stück nach,
statt zum nächsten Rohstoff zu gehen – behoben, aber nicht mehr im Lauf
beobachtet.

### Offene Punkte für die Balance (Zusammenfassung)

1. Fertigungsdauern von Transporter (208 000 h) und Kolonisationsschiff
   (674 000 h) gegenüber Spielsitzungen von Stunden (§B).
2. Werft- und Akademieketten mit Anlagentempo statt Industrietempo (§B).
3. Blockaden ohne Sperrwirkung; `moveFleet` lässt die Blockade der
   abreisenden Flotte stehen (sie bleibt überall angreifbar, kann zu Hause
   keine neue bilden).
4. Handelsgilde: 5er-Lose, ±10 % ohne Rückkehr, Konsumgüter zu 1,4 % des
   lokalen Preises – keine Geldquelle, kein Marktausgleich.
5. Nahrungskapazität gegen logistisches Wachstum (Befund 13), Konsumpreis
   450 gegen Kaufkraft ≈ 50 (Befund 11), unspendbares Elektronik-Drittel
   (Befund 12).
6. 50 % Zivilverluste beim Fall einer Zehn-Drohnen-Garnison.
7. Nur eine Start-Verkaufsorder (Nahrung); Geisterflotten nach Gefechten;
   ein Kommandant ohne Kolonie bleibt bestehen.

## I. Nachtrag: Behebungen (9. September 2026)

Auf Nutzervorgabe wurden die klaren Fehler aus §G/§H behoben; alles, was
eine Balance- oder Designentscheidung ist, steht als Vorschlag in §J.

### 1. Werft und Ausbildungszentrum rechnen wie der Industriekomplex

`ChainPlanner.planChain` bestimmt die Anlage jetzt **je Kettenschritt** aus
der Produktkategorie (`facilityFor`): Schiffe → Werft, Bodeneinheiten →
Ausbildungszentrum, alles andere → Industriekomplex. Der Anlagenparameter
des Aufrufers spielt für die Vorkette keine Rolle mehr. Damit

- beschleunigt eine höhere Werftstufe genau die Endmontage der Schiffe, so
  wie eine höhere Industriestufe die Fertigung – linear mit der Stufe
  (`Formulas.buildingLevelSpeedFactor`);
- läuft die Vorkette eines Werft- oder Rekrutierungsauftrags mit dem
  Industrietempo, egal aus welcher Warteschlange der Auftrag kommt;
- stimmen Vorschau (Kolonieansicht) und echter Werftauftrag überein
  (`ChainPlannerFacilityTest`).

Die Bots brauchen die Vorfertigung von Modulen und Zutaten im
Industriekomplex deshalb nicht mehr; sie bestellen Schiffe und Einheiten
direkt in Werft bzw. Akademie, hinter Energie- und Versorgungs-Wache. Das
hält zugleich die Industrie-Warteschlange für Grundbedarf und Elerium frei.

**Was das an der Gesamtdauer ändert – und was nicht.** Die Endmontage ist
ein winziger Teil der Kette:

| Produkt | Kette bei Industrie 5 / Werft 1 | Industrie 5 / Werft 5 | Industrie 8 / Werft 5 | Endmontage selbst | Arbeitsstunden der Kette | Untergrenze bei 20 000 Arbeitern |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Korvette | 53 985 h | 53 793 h | 33 639 h | 240 h | 5,36 Mio. | 268 h |
| Zerstörer | 404 199 h | 403 815 h | 252 420 h | 480 h | 41,0 Mio. | 2 048 h |
| Frachter | 132 539 h | 132 219 h | 82 667 h | 400 h | 13,4 Mio. | 670 h |
| Mannschaftstransporter | 262 334 h | 261 950 h | 163 755 h | 480 h | 26,9 Mio. | 1 346 h |
| Kolonisationsschiff | 255 206 h | 255 072 h | 159 433 h | 168 h (fest) | 127,5 Mio. | 6 376 h |

Die Werftstufe von 1 auf 5 spart bei der Korvette 0,4 %. Die Dauer steckt
in den Zwischenprodukten (Tier 1–3: Leitermetall, Polymergrundstoff,
Raffinate, Konzentrate – je 4–20 Basisstunden, tausende Stück je Schiff),
nicht in Rohstoffen (7 %) und nicht in der Montage. Dazu kommt die
Arbeitskraft-Bremse: die Kette einer Korvette bindet 5,36 Mio.
Arbeitsstunden, bei 2 000 Einwohnern also mindestens 2 678 Stunden, egal wie
schnell die Anlagen sind. Das ist keine Fehlfunktion des Kettenplaners mehr,
sondern der Katalog selbst – siehe §J, Vorschlag 1.

### 2. Gateway-Absprung hebt die Blockade auf

`FleetCommands.moveFleet` entfernt die Blockade der abreisenden Flotte,
wie `moveFleetWithinSystem` es beim Ortswechsel im System schon tat. Vorher
war die Flotte mit dem verwaisten Eintrag überall angreifbar und konnte nach
der Rückkehr keine neue Blockade bilden (`FleetLifecycleTest`).

### 3. Keine Geisterflotten mehr

`FleetCommands.removeDestroyedFleets` (aufgerufen nach jedem Kampftick)
entfernt Flotten ohne Schiffe samt Blockade und eingeschifften Truppen –
dieselbe Regel, die bei der Koloniegründung schon galt (`consumeShips`).
Vorher blieben sieben schiffslose Flotten im Live-Spiel stehen, sichtbar
in jeder Übersicht und ohne Tank beliebig sprungfähig.

### 4. Bot-Härtung aus Lauf B

Wiederanmeldung am bestehenden Kommandanten statt Doppelregistrierung,
Betanken nach Tankstand statt fester Menge, Ausfall der Heimatwelt
(`HOME_MOVED`/`ELIMINATED`), lokale Elektronikproduktion, Rohstoffbestellung
des Siedlers ohne Endlosschleife.

## J. Vorschläge – Entscheidungen, die nicht im Code getroffen werden sollten

> **Abgeräumt am 9.9.2026.** Alle Punkte dieses Abschnitts sind entschieden –
> siehe `34_Offene_Entscheidungen_Blockade_Nahrung_Treibstoff.md`, das je Punkt
> Entscheidung, Begründung und Umsetzungsstand festhält. Offen geblieben sind
> nur Nummer 6 (Handelsgilde-Preisdrift) und die Persistenz aus Nummer 11; sie
> stehen in `TODO.md`. Der Text unten bleibt als Befund stehen, ist aber nicht
> mehr die Arbeitsgrundlage.


1. **Tiefe der Produktionsbäume (die eigentliche Ursache der Schiffsdauern).**
   Die Kette einer Korvette kostet 2 000-mal so viel Arbeit wie ihre
   Endmontage (5,36 Mio. gegen 2 500 Arbeitsstunden) und 225-mal so viele
   Fertigungsstunden. Wird die Balance auf „Korvette ≈ 2 Spieltage, Transporter
   ≈ 1 Spielwoche bei Industrie 5" gezogen, müssen `baseProductionHours` UND
   `workHoursPerUnit` der Zwischenprodukte (Tier 1–5, nicht Schiffe, nicht
   Einheiten) um etwa den Faktor 500–1 000 sinken. Die Kampfwerte bleiben
   dabei unberührt: `Formulas.productionAspect` nutzt nur die Werte des
   Schiffs selbst. Alternativ: ein globaler Faktor
   `intermediateProductionScale` in `game-constants.json`, der beide Größen
   für Nicht-Endprodukte teilt – reversibel und ohne Katalogumbau.
   Entscheidung: Zielwerte je Schiffsklasse und ob per Faktor oder per
   Katalog.
2. **Parallele Fertigung.** `buildings.json` kennt `productionSlotsPerLevel`
   (1 je Stufe), die Warteschlangen sind aber strikt sequentiell – das Feld
   ist tot. Vorschlag: so viele laufende Aufträge je Kolonie wie
   Anlagenstufe × Slots, und im Kettenplaner die Dauer als kritischer Pfad
   statt als Summe. Das würde die Warteschlangen-Verdrängung (Elerium,
   Nahrung hinter Baustoffen) strukturell lösen, die alle Wachen des Bots
   nur umschiffen. Aufwand: Warteschlangen in Produktion, Werft, Akademie
   plus Anzeige.
3. **Elerium als Kettenzutat.** Der Planer deckt Zwischenschritte zuerst aus
   dem Lager; ein Transporter zieht 188 Elerium, ein Zerstörer 455. Optionen:
   (a) Infrastruktur-Verbrauch bekommt einen reservierten Bestand
   (`eleriumReserveHours`), den Ketten nicht anfassen; (b) Ketten produzieren
   ihre Zutaten immer selbst und nehmen nur explizit freigegebene
   Lagermengen. Entscheidung: welche Regel, und ob sie für alle Zutaten oder
   nur für Betriebsstoffe gilt.
4. **Konsumpreise und Kaufkraft.** Startpreis 450 Cr gegen ≈ 50 Cr Kaufkraft
   ohne Wachstum (Befund 11). Entweder den Startpreis auf die Löhne
   beziehen (Preis ≈ Lohn je Kopf und Stunde / Bedarf je Kopf und Stunde,
   also ≈ 50 Cr bei drei Gütern) oder die Löhne anheben. Zusammenhängend:
   das Elektronik-Drittel des Bevölkerungsbudgets, das ohne Order liegen
   bleibt (Befund 12) – entweder Budget nur auf angebotene Güter verteilen
   oder Elektronik wieder in die Startausstattung nehmen (Konzept 20 hatte
   sie bewusst gestrichen).
5. **Nahrungskapazität gegen Wachstum.** Industrie 5 ernährt ≈ 6 000
   Einwohner, die Wohnkapazität lässt 20 000 zu (Befund 13). Optionen:
   Ertrag der Grundnahrung je Stück erhöhen, Pro-Kopf-Bedarf senken, oder
   Wachstum an die Versorgungslage koppeln (heute nur der Lebensstandard,
   der träge reagiert).
6. **Handelsgilde.** 5er-Lose, ±10 % je Ausführung ohne Rückkehr, Konsumgüter
   zu 1,4 % des lokalen Preises. Vorschlag: Market-Maker-Preise driften je
   Spieltag um x % zum Basispreis zurück, und der Basispreis von Konsumgütern
   orientiert sich am Bevölkerungspreis. Entscheidung: Driftrate und ob die
   Gilde überhaupt eine Geldquelle sein soll (Konzept 22 sagt: kleine Lose).
7. **Blockade-Durchflugregel.** Im Backend sperrt eine Blockade nichts. Für
   die vom Nutzer gemeinte Regel („nur mit Friedens- oder Handelsvertrag
   passieren") ist zu entscheiden: Gilt sie am Gateway (Ankunft im System
   wird verweigert, Flotte bleibt im Vorsystem) und/oder im Orbit (kein
   Eintritt in den Orbit, keine Landung, kein Andocken)? Was geschieht mit
   einer Flotte, die unterwegs auf eine neu gebildete Blockade trifft? Erst
   mit dieser Antwort lässt sich das in `moveFleet`/`processFleetArrivals`
   und `moveFleetWithinSystem`/`land` einbauen; die Bots pflegen die Verträge
   bereits.
8. **Zivilverluste in der Kampfphase.** Der Fall einer Zehn-Drohnen-
   Startgarnison kostet 50 % der Bevölkerung (Konzept 30 §C wendet die Quote
   „restlos aufgerieben = 50 %" unabhängig von der Garnisonsgröße an).
   Vorschlag: Quote zusätzlich mit dem Verhältnis Garnisonswert zu
   Bevölkerung skalieren, oder je Tick auf x % deckeln.
9. **Kommandant ohne Kolonie.** Nach dem Verlust der Heimatwelt bleibt der
   Spieler bestehen, `Player.homeworldColonyId` zeigt auf fremden Besitz und
   Benachrichtigungen, die an die Heimatwelt adressiert sind, landen beim
   Eroberer. Entscheidung: Eliminierung (Spieler verschwindet, Flotten
   verfallen), Neustart mit frischer Heimatwelt, oder Weiterleben mit
   Flotten; in jedem Fall Benachrichtigungen an den Spieler statt an eine
   Kolonie adressieren.
10. **Bebauungsplätze.** Jedes Gebäude kostet eine Infrastrukturstufe
    (`slotsPerInfrastructureLevel: 1`); Werft und Akademie treiben eine
    Startkolonie auf Infrastruktur 8 mit `p_energienetzbaugruppe ×15` und
    doppeltem Eleriumverbrauch. Vorschlag: 2 Plätze je Stufe oder die
    Startbebauung mit Werft 1 und Akademie 1.
11. **Weitere Implementierungspunkte.** Keine Persistenz (ein Neustart
    vernichtet ein LAN-Spiel); kein Beobachterzugriff im Protokoll
    (`observer.mjs` muss sich nacheinander als jeder Bot anmelden); kein
    Befehl, Schiffe zwischen Flotten zu verschieben (eine Landungsoperation
    fährt zwangsläufig als drei Flotten); `previewProductionChain` kennt keine
    Werft-/Akademieprodukte in der Kolonieansicht (jetzt rechnerisch korrekt,
    aber nirgends angezeigt).
