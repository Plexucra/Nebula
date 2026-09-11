# 36 — Tageseinkauf und Bevölkerungsvorrat: der Kolonietag

> **Stand 11.9.2026, Umsetzungskonzept/38:** Tageseinkauf und Notkauf sind
> durch stehende **Kauforders der Bevölkerung** abgelöst; der Kolonietag,
> der Vorrat von sieben Tagesbedarfen und die Deckungsmessung gelten weiter.
> Was hier über `purchase`, `buyAtOwnPost` und `emergencyPurchase` steht,
> beschreibt den Zwischenstand.

**Vorgabe (Nutzer, 10.9.2026):** „Die Kolonie soll nur noch auf dem
planetaren Handelsposten kaufen … Die Alternative wäre, dass die Kolonie nur
einmal am Tag einen Kauf durchführt, am besten etwas mehr kauft, als sie
tatsächlich benötigt, und etwas im Lager behält. Das hilft gegen schwere
Zeiten und beim Phänomen, wenn die Bevölkerung innerhalb eines Tages wächst
und der Bedarf steigt." Vorratszeit: sieben Tage.

## A. Ausgangslage

Nach dem Ereignisplaner (Sitzung 10.9.2026) war alles Punktuelle ein
Ereignis. Übrig blieb ein galaxieweiter **Wirtschaftsschritt jede
Realsekunde** (`EconomyTick.economyStep`): Energie, Unterhalt, Konsum,
Lebensstandard, Kernwerte, Wachstum für alle Kolonien. Er ließ sich nicht
extrapolieren, weil die Bevölkerung jeden Schritt aus einem lebenden,
systemweiten Orderbuch kaufte – eine neue Order von Spieler A änderte die
Versorgung jeder Kolonie im System. Der Schritt lief über alle Gebäude,
Flotten und Orders der Galaxie, je Kolonie, je Sekunde.

## B. Das Modell

| Größe | Regel |
|---|---|
| Markt der Bevölkerung | die Verkaufsseite des Handelspostens ihres Planeten (seit Umsetzungskonzept/37 ein Orderbuch je Planet; ursprünglich: Orders mit `depotColonyId` = eigene Kolonie) |
| Takt | ein **Kolonietag** je Kolonie und Spieltag, Ereignis `COLONY_DAY`, Tageszeit = Gründungszeit + n Tage |
| Tagesbedarf | `Bevölkerung × Bedarf je Kopf und Stunde × 24` |
| Vorratsziel | `⌈Tagesbedarf × 7⌉` je Grundkonsumgut (`populationStockTargetDays`) |
| Einkauf | Ziel minus Vorrat, aus dem Bevölkerungs-Wallet zu gleichen Teilen je Gut (Rest fließt weiter), ganze Stücke, günstigste Order zuerst |
| Verbrauch | ein Tagesbedarf aus dem Vorrat, ganze Stücke über das Übertragskonto |
| Deckung | `gedeckt × (1 + 0,5 × min(1, Reichweite/Ziel))`, also 1,0 gedeckt ohne Reserve, 1,5 mit vollem Vorrat, 0 ohne Essen |
| Notkauf | Vorrat unter `populationEmergencyPurchaseBelowDays` (1 Tag) und eine Order erscheint am eigenen Posten → sofort nachkaufen |
| Gründung | sofort einkaufen, erster Kolonietag einen Spieltag später |

Reihenfolge im Kolonietag (`Economy.colonyDay`, tragend): Energie →
Unterhalt und Löhne → Einkauf → Verbrauch und Lebensstandard → Kernwerte →
Wachstum → Flankenmeldungen (Blackout, Schrumpfen, Konto).

Alle Raten bleiben **je Spielstunde** definiert und werden mit
`GAME_DAY_HOURS` multipliziert. Das Wachstum ist damit ein Euler-Schritt
von 24 Stunden; die logistische Formel bleibt stabil (maximaler Zuwachs
rund 11 % der Wohnkapazität je Tag).

## C. Was sich dadurch ändert

- **Rechenlast:** je Kolonie ein Lauf je Spieltag statt sechzig je Spieltag
  (Tempo 1) für alle. Die Kolonien verteilen sich über den Tag, weil ihre
  Gründungszeiten es tun. Der Sekundentakt (`GameTick`) bleibt als Wecker
  und rechnet nichts.
- **Vorrat statt Prozent:** „reicht noch 4,2 Tage" ist die Zahl, die der
  Kommandant sieht (Panel „Versorgung und Vorrat" im Tab Bevölkerung,
  Abfrage `populationSupply`). Der Vorrat gehört der Bevölkerung
  (`Population.stock`), nicht dem Kolonielager.
- **Zahltag:** Verkaufserlöse kommen in Tagesschüben statt tröpfelnd.
  Bevölkerung und Konten springen einmal am Tag.
- **Energie:** der Infrastrukturverbrauch wird je Tag gezogen. Was fehlt,
  bleibt als `shortfall` stehen und wird beim nächsten Elerium-Zugang
  sofort nachgeholt (`PowerGrid.settleShortfall`, aus `Warehouse.add`):
  der Blackout endet mit dem Nachschub, nicht erst am nächsten Tag.
- **Glättung:** die Sekunden-Zeitkonstanten (Energie 1,8 h, Budget 3,8 h,
  Lebensstandard 1,1 h) entfallen. Der Lebensstandard wird mit
  `tau = 24 h` je Tag geglättet, den Rest glättet der Vorrat selbst.
- **Heimatwelt-Startkasse:** 900 Cr stammten aus der Zeit der
  120-Einwohner-Kolonie; damit hätte der erste Einkauf eineinhalb Tage
  Nahrung bezahlt. Jetzt wie jede gegründete Kolonie: Einwohner ×
  `CREDITS_PER_NEW_INHABITANT` (16 000 Cr).
- **Lebensstandard-Spanne:** nur Grundnahrung versorgt ergibt 50 %
  (gedeckt, kein Vorrat) bis 75 % (voller Vorrat); die Wachstumsschwelle
  von 50 % bleibt.

## D. Notkauf – wo er hängt

`Economy.emergencyPurchase(state, ids, colonyId, gut)` wird gerufen aus:
`MarketCommands.createSellOrderCore`, `createSellOrderFromFleet` (am Posten
einer Kolonie), `updateSellOrderPrice` und
`ProductionCommands.completeProductionEntry` (das Einlagern hat eine
schlafende Dauerorder nachgefüllt). Das Nachfüllen aus `Warehouse.addRaw`
selbst löst ihn nicht aus – das Lager kennt keinen Id-Generator für die
Buchung.

## E. Nicht Teil dieses Konzepts

- Extrapolation von Kontostand und Bevölkerung in der Oberfläche zwischen
  zwei Kolonietagen (Basis plus Rate). Die Werte springen einmal am Tag;
  der Countdown zum nächsten Einkauf ist sichtbar.
- Ein Bevölkerungsvorrat je Gut mit unterschiedlicher Vorratszeit
  (Nahrung länger als Elektronik). Heute gilt ein Ziel für alle.
- Persistenz. Der Kolonietag ist dafür vorbereitet: der Zustand einer
  Kolonie ändert sich nur an Ereignissen.

## F. Betroffene Stellen

Backend: `Economy` (ersetzt `EconomyTick`), `GameEventType.COLONY_DAY`,
`GameEvents`, `PowerGrid`, `Warehouse`, `MarketCommands`,
`ProductionCommands`, `GameStateSeeder`, `ColonyCommands.foundColony`,
`ColonyConquest` (Vorrat zieht bei Eingliederung mit), `Population.stock`,
`ColonyPowerState.dueToday/shortfall`, `PopulationSupply`,
`GameConstants` (`TICK_MS`/`TICK_GAME_HOURS` entfallen),
`Formulas.smoothingAlpha(tau, step)`, `shared/game-constants.json`.
Frontend: `populationSupply`, Panel „Versorgung und Vorrat", Preisanhalt
beim Anbieten. Bot: Kommentare (die Preisbänder nach Deckung passen
weiter). Tests: `IntegerQuantitiesTest` (Kolonietag, Notkauf, Vorrat
ohne Orders), `GameEventsTest`, `GameSpeedTest`, `EnergyStorageTest`.
