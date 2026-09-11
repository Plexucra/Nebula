# 15. Wirtschaftskreislauf, Aufbewahrungsfristen und EINE Regelquelle

Ergebnis eines konzeptionellen Reviews mit drei Aufträgen. Zwei Vorentscheidungen
des Nutzers gelten dabei als gesetzt: Aufbewahrungsfristen zählen in **Spielzeit**
(nicht Realzeit), und der `SimulatedGameApiService` wird **gelöscht** – das
Backend ist ab sofort die alleinige Wahrheit über Spielregeln.

## Auftrag 1: Startverkaufsorders für Grundkonsumgüter

> **Hinweis (Stand Dokument 17):** Preis und Bedarfssätze wurden mit dem
> Minimalstart in `17_...md`, Teil C neu hergeleitet (Preis 20 → 300, Bedarf
> je Kopf gesenkt). Die Herleitung unten beschreibt den damaligen Stand.

### Review-Befund

`EconomyTick.runConsumption` kauft ausschließlich aus `state.sellOrders`; die
Bevölkerung kann NICHT direkt aus dem Kolonielager essen. Weder `WorldSeed` noch
die Bots legten je eine Verkaufsorder an. Folge für jeden frischen Kommandanten
und alle 20 Bots: Versorgung 0 → Lebensstandard 0 % → `growthConditionFactor`
klemmt bei 0,15 → Loyalitätsverfall, und das Spieler-Wallet kannte ausschließlich
Abflüsse (Gebäudeunterhalt, Flottenunterhalt, Löhne).

### Umsetzung

`WorldSeed.starterSellOrders` legt beim Anlegen JEDER Heimatkolonie
wiederkehrende Verkaufsorders (`autoRelist = true`) am eigenen Depot an – für
beide Grundkonsumgüter, die die Kolonie ohnehin per Dauerauftrag
(`requeueOnComplete`) nachproduziert. Damit schließt sich der Kreislauf:
Produktion → Lager → Verkaufsorder → Bevölkerung kauft → Spieler verdient, und
`MarketCommands.replenishDormantSellOrders` füllt die Order jeden Tick aus dem
nachproduzierten Lagerbestand wieder auf.

**Warum nur zwei der drei Konsumgüter**: `GameConstants.CONSUMER_GOODS_ORDER`
enthält drei Güter. `p_grundnahrung` und `p_grundmedizin` sind billige
Tier-2-Güter und stehen jetzt beide im Start-Dauerauftrag. Das dritte,
`p_unterhaltungselektronik`, ist Tier 5 mit tiefer Vorkette (Steuerchip,
Kommchip, Leiterbündel, Polymergrundstoff …) und würde die SEQUENTIELLE
Produktionswarteschlange der Kolonie über lange Zeit blockieren – ausgerechnet
die Nahrungsproduktion würde dadurch aushungern. Diesen Zweig baut der
Kommandant selbst auf. Maximal erreichbarer Lebensstandard bleibt dadurch
(2 × Nahrung + 1 × Medizin) / 4 × 100 = 112,5 % – also ohne Nachteil.

### Gewählte Werte (nachgerechnet, nicht geraten)

- **Menge je Order: 20 Stück**, aus einem Gesamt-Startbestand von 50 je Gut
  (Rest 30 bleibt im Lager als Puffer fürs erste Auto-Relist; gleiche
  Buchführung wie `MarketCommands.createSellOrderCore`, das die Order-Menge aus
  dem Lager abzieht). `runConsumption` kauft je Tick höchstens `ceil(Bedarf)`,
  bei Startbevölkerung 420 also 1 Stück je Gut – 20 Stück puffern damit rund
  20 Ticks.
- **Preis: 20 Credits/Stück**, hergeleitet über den dominanten Geldabfluss
  (Stand damals; seit 11.9.2026 ist der Lohn `wagePerCapitaPerGameHour` in
  `shared/game-constants.json` auf ein Drittel gesenkt, 0,0067, und der
  Gleichgewichtspreis damit auf rund 17 Cr – der Startpreis 60 aus Konzept 36
  liegt bewusst darüber, das Wachstumsgeld trägt die Differenz, die Bots
  senken ihre Preise automatisch):
  Löhne je Tick = `Bevölkerung × 0,02 × TICK_GAME_HOURS` = Bevölkerung × 0,008;
  Konsumbedarf je Tick = Bevölkerung × (0,0004 + 0,00015) = Bevölkerung ×
  0,00055 Stück. Gleichsetzen ergibt 0,008 / 0,00055 ≈ **14,5 Credits/Stück**,
  bei denen die Konsum-Einnahmen die Löhne exakt ausgleichen. Aufgerundet auf
  glatte 20, damit zusätzlich ein Teil des Gebäude-/Flottenunterhalts getragen
  wird. Beide Seiten skalieren linear mit der Bevölkerung, der Ausgleich hält
  also auch beim Wachstum. Bezahlbarkeit unkritisch: das Konsumbudget beträgt
  mindestens ein Zehntel des Bevölkerungs-Wallets (Start: 900 ⇒ Budget 162,
  davon 1/3 = 54 je Gut).

Ein erster Versuch mit 10 Credits/Stück wurde live verworfen: der Kreislauf lief
zwar, das Spieler-Wallet sank aber weiter (6500 → 6148 in 30 s bei 740 Credits
Einnahmen), weil die Bevölkerung durch den nun hohen Lebensstandard rasant
wächst (420 → 5896 in wenigen Minuten) und die Löhne mitwachsen.

### Verifikation (live gegen laufenden Server)

| Messung | vor Auftrag 1 | nach Auftrag 1 (Preis 20) |
|---|---|---|
| Verkaufsorders im Heimatsystem | 0 | 2 (`p_grundnahrung`, `p_grundmedizin`, je 20× à 20 Cr, autoRelist) |
| Lebensstandard nach 30 s | 0 % | 102,3 % (Verlauf 110,4 → 111,8 → 98,0 → 108,3 → 110,1 → 102,3) |
| Versorgungsdeckung | 0 / 0 / 0 | 1,5 / 1,5 (Nahrung/Medizin), 0 (Elektronik, siehe oben) |
| Loyalität nach 30 s | fallend | 78,8 % → 82,8 % steigend |
| Spieler-Wallet nach 30 s | nur Abflüsse | 6500 → 6888 Cr, **60 Konsum-Transaktionen, 1480 Cr Einnahmen** |

**Beobachtete Grenze (ehrlich dokumentiert, nicht behoben)**: `runConsumption`
rundet den Bedarf je Tick auf ganze Stück auf (`ceil`), die Nachfrage liegt
dadurch bei kleiner Bevölkerung deutlich über dem rechnerischen Bedarf. Die
Startproduktion (≈ 5 Stück je ~13 Spielstunden) kann das auf Dauer nicht decken;
nach einigen Minuten läuft die Order zeitweise leer und die Deckung schwankt.
Das ist eine bestehende Eigenschaft der Konsum-/Produktionsbalance, keine Folge
dieses Auftrags – der Kreislauf als solcher ist geschlossen und nachgewiesen.

## Auftrag 2: "Beibehalten"-Kennzeichen mit automatischer Löschung

- `Message.keep` und `GameNotification.keep`, Standard `false`, beidseitig
  (Java + TypeScript).
- Neue Befehle `setMessageKeep` / `setNotificationKeep` (Dispatch in
  `GameSocket`, `GameApi` + `WebSocketGameApiService`). Beim Nachrichten-Flag
  dürfen **Absender UND Empfänger** setzen – beide sehen dieselbe Nachricht in
  Postausgang bzw. Posteingang.
- UI: "Beibehalten"-Checkbox je Nachricht (Posteingang und Postausgang) und je
  Benachrichtigung im Glocken-Panel, jeweils mit Erklärungs-Tooltip.
- Aufräumen im Tick: neue Klasse `RetentionCleanup.purgeExpired`, aufgerufen als
  letzter Schritt in `GameTick`.

**Fristen als benannte Konstanten in Spielstunden, an EINER Stelle**
(`shared/game-constants.json`, gelesen von `GameConstants` im Backend und von
`core/shared-constants.ts` im Frontend):

| Eintrag | Frist | Spieltage | Realzeit bei `REAL_MS_PER_GAME_HOUR = 2500` |
|---|---|---|---|
| Benachrichtigungen | 48 Spielstunden | 2 | ca. 2 Minuten |
| Nachrichten | 168 Spielstunden | 7 | ca. 7 Minuten |

### Verifikation (live, mit den echten Fristen)

Zwei Nachrichten und Benachrichtigungen angelegt, je eine markiert:

```
+30s …+390s  Nachrichten=2 (keep=true, drop=true)  | Benachrichtigungen=1 (keep=true)
+420s        Nachrichten=1 (keep=true, drop=FALSE) | Benachrichtigungen=1 (keep=true)
+450s        Nachrichten=1 (keep=true, drop=false) | Benachrichtigungen=1 (keep=true)
```

Ergebnis: **unmarkierte Einträge verschwinden nach Ablauf ihrer Frist (Nachricht
`msg_8m2` zwischen 390 s und 420 s, exakt im erwarteten 420-s-Fenster),
markierte bleiben erhalten** (`msg_8m1`, `ntf_8m4` nach 450 s unverändert da).
Zusätzlich prüft der dauerhafte E2E-Test die Befehlsmechanik (Setzen und wieder
Entfernen des Kennzeichens, Startwert `false`).

## Auftrag 3: Doppelte Regelimplementierung beseitigen

### Grundsatz

Regeln werden **immer im Backend implementiert und durchgesetzt**. Werte, die
beide Seiten brauchen, liegen als **JSON** in `/shared`. Ausnahmen sind erlaubt,
wo eine Verlagerung die Bedienbarkeit spürbar verschlechtern würde – sie sind
unten einzeln benannt und begründet.

### 3.1 `SimulatedGameApiService` gelöscht

Vorher per Volltextsuche geprüft, welcher Code ausschließlich für sie existierte.
Gelöscht wurden:

| Datei | Umfang |
|---|---|
| `core/sim/simulated-game-api.service.ts` | die komplette Zweit-Simulation (~2700 Zeilen) |
| `core/sim/engine/formulas.ts` | Zweitkopie aller Spielformeln |
| `core/sim/data/world-seed.ts`, `galaxy-generator.ts`, `planet-type-profiles.ts` | Galaxie-/Startwelt-Erzeugung |
| `core/sim/data/product-catalog.ts`, `building-catalog.ts`, `ship-catalog.ts`, `ground-unit-catalog.ts`, `resource-catalog.ts` | Katalog-Zweitkopien |
| `core/sim/clock.ts`, `id.ts`, `rng.ts` | nur von der Simulation genutzte Hilfen |

`app.config.ts` liefert jetzt immer `WebSocketGameApiService`. Der Umschalter in
`backend-config.ts` ist entfallen; die URL-Ableitung relativ zu
`location.hostname` bleibt (Voraussetzung für den LAN-Betrieb, siehe Dokument
14). Übrig bleiben in `core/sim/` nur noch vier Dateien: `game-api.ts`
(Vertrag), `game-api.token.ts`, `websocket-game-api.service.ts`,
`backend-config.ts`.

### 3.2 Transparenz-Anzeige erhalten – aber aus dem Backend

Ausdrückliche frühere Nutzeranforderung: *"es darf für die Produktion keine für
den Nutzer verborgenen Faktoren geben, er muss ja strategisch entscheiden."*
Die Transparenz-Panels rechneten ihre Faktoren bis dahin client-seitig mit
`formulas.ts`. Reihenfolge des Umbaus bewusst: **erst** die neue Query gebaut,
**dann** die Client-Berechnung entfernt.

Neu: `colonySpeedBreakdown(colonyId)` (`ColonyCommands` + Model
`ColonySpeedBreakdown`) liefert in EINEM Aufruf:
Bevölkerung, Workforce-Faktor, Industriestufe + Gebäude-Tempofaktor,
Blackout-Status, Zufriedenheit (= `growthConditionFactor` × 100), je Gebäudetyp
eine Ausbau-Vorschau (Kosten, Stunden, Tempo aktuell/nächste Stufe), je Produkt
den Spezialisierungs-Tempobonus und den Fördergüte-Ausbeutefaktor. Ein Aufruf
statt vieler Einzelabfragen, weil die Panels alles gleichzeitig anzeigen.

Beobachteter Beispielwert aus dem E2E-Lauf: `Bevölkerung 7014, Workforce ×5.00,
Industrie Stufe 4 (×4), Blackout=false, 6 Ausbau-Vorschauen, 17
Fördergüte-Faktoren`.

### 3.3 Geteilte Ausgangsdaten als JSON

**Kataloge** (`/shared/catalog/`): einmalig per Generator aus den bestehenden
Java-Katalogen serialisiert (statt abgetippt – dadurch garantiert wertgleich),
danach lädt `CatalogJson` sie zur Laufzeit aus dem Klassenpfad; der Maven-Build
kopiert `/shared` über eine zusätzliche Resource-Definition mit hinein.

| Datei | Einträge |
|---|---|
| `products.json` | 214 |
| `buildings.json` | 6 |
| `ships.json` | 6 |
| `ground-units.json` | 4 |
| `resources.json` | 17 |
| `planet-type-profiles.json` | 13 Planetentypen × 17 Rohstoffbereiche |

Die TS-Kataloge konnten **ersatzlos** entfallen: das Frontend bezieht Kataloge
ohnehin über die WebSocket-Befehle `productTypes`, `buildingTypes`, `shipTypes`,
`groundUnitTypes`. Die Java-Katalogklassen schrumpften von 295/56/45/41/47 auf
42/25/29/30/23 Zeilen und enthalten nur noch Lader plus `find`-Zugriffe.

**Konstanten** (`/shared/game-constants.json`): `realMsPerGameHour` (2500),
`notificationRetentionGameHours` (48), `messageRetentionGameHours` (168).
Backend: `SharedConstants` (gelesen von `Clock.REAL_MS_PER_GAME_HOUR` und
`GameConstants`). Frontend: `core/shared-constants.ts` importiert dieselbe Datei
beim Build (`resolveJsonModule` + Pfad-Alias `@shared/*`) und leitet daraus die
Hinweistexte der "Beibehalten"-Schalter ab ("nach 7 Spieltagen, ca. 7 Minuten
Echtzeit") – die Zahlen stehen damit nirgends doppelt.

### 3.4 Drift-Audit: alle Fundstellen mit Entscheidung

| # | Fundstelle | Art der Doppelung | Entscheidung |
|---|---|---|---|
| 1 | `simulated-game-api.service.ts` | komplette zweite Regel-Engine | **aufgelöst** – gelöscht |
| 2 | `engine/formulas.ts` vs. `Formulas.java` | alle Spielformeln doppelt | **aufgelöst** – TS-Datei gelöscht, Werte kommen über `colonySpeedBreakdown` |
| 3 | `product/building/ship/ground-unit/resource-catalog.ts` vs. Java-Kataloge | 247 Datensätze doppelt | **aufgelöst** – eine JSON-Quelle, TS-Kopien gelöscht |
| 4 | `planet-type-profiles.ts` vs. `PlanetTypeProfiles.java` | 13 × 17 Wertebereiche doppelt | **aufgelöst** – JSON-Quelle, TS-Kopie gelöscht |
| 5 | `world-seed.ts`/`galaxy-generator.ts` vs. Java | Galaxie-/Startweltregeln doppelt | **aufgelöst** – TS-Dateien gelöscht |
| 6 | `clock.ts` `REAL_MS_PER_GAME_HOUR` vs. `Clock.java` | Zeitkompression doppelt | **aufgelöst** – `shared/game-constants.json` |
| 7 | Aufbewahrungsfristen (48/168 Spielstunden) | wären in UI-Texten + Backend doppelt | **aufgelöst** – dieselbe shared JSON, UI leitet Texte daraus ab |
| 8 | Frachtkapazität: `maxLoadable`/`fleetCapacity*` in `fleets-overview` vs. `FleetCommands.loadCargo` | Kapazitätsregel doppelt (Masse/Volumen) | **aufgelöst** – neue Query `fleetCargoCapacity` liefert Kapazität, Auslastung und maximal ladbare Menge |
| 9 | Blackout-Schwelle: `powerCoverage < 0.999` in `statistics` vs. `Formulas.BLACKOUT_THRESHOLD` | Schwellwert doppelt | **aufgelöst** – neue Query `isBlackout(colonyId)` |
| 10 | Planetentyp-Beschriftungen: `planet-type-labels.ts` vs. `PlanetTypeProfiles.LABELS` | 13 Anzeigenamen doppelt | **aufgelöst** – die Java-Seite war toter Code (nirgends aufgerufen) und wurde gelöscht; Beschriftungen sind reine Darstellung und bleiben im Frontend |
| 11 | `core/util/graph.ts` (`bfsHops`/`nearestByHops`) vs. `Graph.bfsPath` (Java) | zweite BFS-Implementierung | **bewusste Ausnahme (Performance)** – rechnet nur auf der bereits gelieferten Topologie (`galaxyRoutes`) und nur für die Darstellung (Sprungdistanz-Einfärbung der Galaxiekarte beim Pannen/Zoomen, nächstgelegener Handelsposten). Ein Server-Roundtrip pro Kartenframe wäre spürbar. Die spielentscheidende Route rechnet weiterhin ausschließlich das Backend (`moveFleet`/`routePreview`). |
| 12 | Fortschrittsbalken `((now - startedAt) / total) * 100` (Kolonie-Detail, Flotten) | Zeitanteil client-seitig | **bewusste Ausnahme (Performance)** – flüssige Balken/Countdowns brauchen ein lokales 500-ms-Ticken; die Eckwerte (`startedAt`/`endsAt`) kommen vom Server, das Ergebnis beeinflusst nichts am Spielstand |
| 13 | Eingabe-Vorbelegungen/-Begrenzungen (Mengenfelder, `DEFAULT_MAX_STOCK = 50`) | Komfortwerte | **bewusste Ausnahme (Bedienbarkeit)** – reine Formular-Vorbelegung, der Server validiert jede Eingabe erneut und ist die Autorität |
| 14 | `isStruggling` (< 30 % Lebensstandard, < 20 % Loyalität) in der Statistik | UI-Warnschwelle | **bewusste Ausnahme (reine Darstellung)** – existiert im Backend gar nicht und beeinflusst keine Spielmechanik; ausschließlich eine Hervorhebung in der Tabelle |
| 15 | Enum-/String-Literale in `core/models/*.ts` (`'Stationed'`, `'War'`, …) | Typdefinitionen | **bewusste Ausnahme (Protokollvertrag)** – TypeScript braucht die Typen des Wire-Formats; sie beschreiben die Schnittstelle, nicht eine Regel |
| 16 | Formatierung (Zeitalter-Texte, kg/t, m³/l, Prozentrundung, Sortierung/Filterung) | – | **keine Doppelung** – reine Darstellung über bereits gelieferte Werte |

### Nebenbefund

`GameQueries.transactionsForPlayer` liefert nur die letzten 200 Transaktionen.
Bei ~3 Buchungen je Tick (Gebäudeunterhalt, Flottenunterhalt, Löhne) rutschen
ältere Konsum-Einnahmen nach wenigen Minuten aus dem Fenster. Für die
Kontoansicht ist das gewollt; im E2E-Test wird der Einnahmen-Nachweis deshalb
direkt nach der Registrierung geführt, nicht am Testende.

## Verifikation gesamt

- `mvn clean compile` + `mvn clean test` (Backend): grün. `WorldSeedSmokeTest`
  um die neuen Start-Verkaufsorders erweitert (2 Orders, `autoRelist`, bestückt,
  bepreist) und an die 3 Start-Daueraufträge angepasst.
- `mvn clean compile` (npc-bot): grün.
- `tsc --noEmit` und `ng build` (Frontend): grün.
- Dauerhafter E2E-Test `backend/e2e/full-playthrough.mjs`: grün, erweitert um
  Start-Verkaufsorders, Lebensstandard > 0, Konsum-Einnahmen,
  "Beibehalten"-Befehle für Nachrichten und Benachrichtigungen sowie die
  Tempo-Aufschlüsselung aus dem Backend.
- Aufbewahrungsfristen zusätzlich in einem eigenen Langlauf über 450 s live
  verifiziert (siehe Auftrag 2).
