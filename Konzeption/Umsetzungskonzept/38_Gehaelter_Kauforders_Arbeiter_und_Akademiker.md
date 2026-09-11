# 38 — Gehälter je Arbeitsstunde, Kauforders der Bevölkerung, Arbeiter und Akademiker

**Vorgabe (Nutzer, 11.9.2026, Designgespräch):** Der Geldkreislauf soll das
Übervorteilen der Bevölkerung nicht durch weitere Schutzregeln, sondern
durch eine Preisrückkopplung verhindern; zugleich braucht eine große
Bevölkerung einen Grund. Entscheidungen im Wortlaut:

- „Ich würde dann doch gerne Gehälter einführen. Also ein fixer Preis pro
  Arbeitsstunde." Keine Grundsicherung.
- „Dein Vorschlag bezüglich Kauforder ist genau richtig so. Die Bevölkerung
  weiß selbst am besten, was sie sich nachhaltig leisten kann und will. Sie
  legt den Preis auf Basis ihres Einkommens und Guthabens und Bedarfs (der
  gesamten Bevölkerung – nicht nur der arbeitenden)."
- Bevölkerung aufgeteilt in **Arbeiter** und **Akademiker**, die unabhängig
  zu- und abnehmen. Akademiker brauchen alle Konsumgüter in anderer
  Gewichtung, Arbeiter nur die Grundgüter. Ein Wallet je Kolonie. Grundgüter
  haben Vorrang; Akademiker schwinden bei Mangel an höheren Gütern zuerst und
  gehen zurück zu den Arbeitern. Akademiker zählen nicht als Arbeitskraft.
- **Forschungszentrum** als Bebauung: Stufe L bezahlt bis zu so viele
  Akademiker wie der Wohnkomplex Stufe L Einwohner fasst (20 000, je Stufe
  verdoppelt). Keine unbezahlten Akademiker. Die Zahl der bezahlten Akademiker
  über alle Kolonien ist das **Forschungsniveau** (ein Niveau, kein Vorrat);
  das Forschungsmodul selbst folgt später.
- **Güterstaffel:** je Wohnstufe kommt ein weiteres Pflichtgut hinzu, für die
  ersten 20 000 reicht Grundnahrung. Trinkwasserration und Standardnahrung
  fliegen aus dem Spiel, Grundkleidung rückt auf Stufe 3, Unterhaltungs-
  elektronik auf deren alten Platz. Infanterieausrüstung ist kein Konsumgut.
- Start bei null: keine Akademiker, kein Forschungszentrum, das
  Kolonisationsschiff bringt nur Arbeiter. Bots: Löhne und Gebote ja,
  Forschung nein. Soldatenrekrutierung aus der Bevölkerung bleibt
  ausgeklammert.

## A. Ausgangslage und Befund

Der Code setzte den Kreislauf aus Mechanik 10 nicht um: Produktion kostete
keine Credits (kein `Ledger`-Eintrag in `ProductionCommands`), der „Lohn" war
eine Kopfpauschale je Einwohner und Spielstunde (`Economy.payUpkeepAndWages`),
egal ob jemand arbeitete. Die Bevölkerung war reiner Preisnehmer
(`Economy.buyAtOwnPost` kaufte jede Order bis zum Budget). Ein zu hoher
Preis leerte ihr Wallet einmal, danach lag die Deckung dauerhaft bei
`Lohn / (Bedarf × Preis)` – der Lohn schrumpfte mit der Bevölkerung, die
Pro-Kopf-Deckung erholte sich bei festem Preis nie. Die einzige
Rückkopplung war ein Mensch mit Warnung oder der Bot mit Preisheuristik.

Mechanik 10 §1 sagt „1 Arbeitseinheit kostet 1 Credit" – dieses Konzept
kehrt dahin zurück.

## B. Löhne je Arbeitsstunde

| Größe | Regel |
|---|---|
| Lohnsatz | `wagePerWorkHour` in `shared/game-constants.json` (Wert unverändert 0,0067 Cr, vorher `wagePerCapitaPerGameHour`) je **Einwohner-Arbeitsstunde** |
| Einwohner-Arbeitsstunde | Die Katalog-Arbeitsstunden (`workHoursPerUnit`) sind im Maßstab der ungestauchten Fertigung. `productionSpeedMultiplier` (90) ist die Produktivität: ein Einwohner leistet je Spielstunde 90 Katalog-Arbeitsstunden (die Stauchung greift NACH der Arbeitskraft-Bremse, siehe `_productionSpeedMultiplier`). Bezahlt werden deshalb `ChainPlan.totalWorkHours / productionSpeedMultiplier` Stunden. Eine voll beschäftigte Kolonie zahlt damit exakt die alte Pauschale: 2 000 Einwohner × 0,0067 = 13,4 Cr je Spielstunde |
| Lohn eines Auftrags | `ChainPlan.wageCredits = totalWorkHours / productionSpeedMultiplier × wagePerWorkHour`, in der Vorschau sichtbar |
| Buchung | beim **Start** eines Auftrags in Industrie, Werft und Ausbildungszentrum, Kommandanten-Wallet → Bevölkerungs-Wallet der Kolonie, `TransactionReason.Wage`; bei Daueraufträgen je Durchlauf |
| Ohne Guthaben | der Auftrag startet nicht: Status `stopped`, Code **509** `CODE_WAGES_UNPAID`, Benachrichtigung mit Betrag und Guthaben. Die Warteschlange hält wie bei fehlenden Vorprodukten; „Fortsetzen" prüft erneut. Vom System eingereihte Aufträge unterliegen derselben Regel |
| Abbruch | erstattet keine Löhne – die Arbeit ist geleistet; Baustoffe und Teilerfolg wie bisher |
| Kopfpauschale | entfällt. `payUpkeepAndWages` zahlt Gebäude- und Flottenunterhalt und die **Forschungsgehälter** (Teil E) |
| Preisanker | `ProductCosts` rechnet weiter `workHoursPerUnit × Lohnsatz` ohne Stauchung – ein reines Rechenmodell für die Handelsgilde-Orders und die Gewichtung der Gebote (Teil C), keine Geldbewegung. Bewusst unverändert |
| Kontostand-Tendenz | `treasuryFlowPerHour`: Unterhalt + Forschungsgehälter + Löhne laufender Aufträge (Lohn / Laufzeit) gegen die Konsumeinnahmen |

Startkasse 6 500 Cr gegen den Startauftrag Grundnahrung × 84: 84 × 60 h /
90 × 0,0067 ≈ 0,4 Cr. Die Löhne sind im Frühspiel kein Hindernis; sie werden
es bei Schiffsketten (Korvette ≈ 150 000 Fertigungsvorgänge) – gewollt.

## C. Kauforders der Bevölkerung

Die Bevölkerung stellt am eigenen Handelsposten **stehende Kauforders**
(`MarketOrder` mit `populationColonyId`). Tageseinkauf, Notkauf und
`buyAtOwnPost` aus Konzept 36 entfallen; das Orderbuch aus Konzept 37
bedient die Gebote.

| Größe | Regel |
|---|---|
| Eigentümer | `ownerId` = Kommandant der Kolonie (damit greift die Vertragsregel des Postens unverändert: eigener Kommandant oder Handelsvertragspartner), `populationColonyId` = die Kolonie. Selbsthandel-Sperre gilt für diese Gebote NICHT – der eigene Kommandant ist der Hauptlieferant |
| Escrow | aus dem Bevölkerungs-Wallet, wie bei jeder Kauforder; Rest zurück beim Zurückziehen |
| Ausführung | Ware in `Population.stock`, Geld an den Verkäufer, `TransactionReason.Consumption` |
| Rhythmus | am Kolonietag: alte Gebote zurückziehen (Escrow zurück), neue Gebote stellen, Matching. Zwischen zwei Tagen bleiben sie stehen; jede neue oder umgepreiste Verkaufsorder und jede Fertigstellung ruft das Matching |
| Menge je Gut | `Lücke = ⌈Tagesbedarf × 7⌉ − Vorrat` (Vorratsziel unverändert) |
| Einkommen | Zufluss ins Bevölkerungs-Wallet je Spieltag (Löhne, Unterhalt, Ausbau, Werftprämie, Wachstumsgeld, Ausgleichsfonds), gezählt im `Ledger`, geglättet über 7 Tage (`populationIncomeSmoothingDays`) |
| Tagesbudget | `B = min(Guthaben, Einkommen + Guthaben / 7)` |
| Verteilung | **1. Grundsicherung in Einkaufsreihenfolge:** je Gut mit Lücke, das eine kaufbare Verkaufsorder hat, wird `min(Rest, Briefkurs × Lücke)` reserviert; der Rest wandert zum nächsten Gut. **2. Rest im Verhältnis `Ankerpreis (ProductCosts) × Tagesbedarf`** auf alle Güter mit Lücke. `Limit = Grundsicherung / Lücke + Rest / max(Lücke, Wochenvorrat)` – die Grundsicherung gilt je Stück der Lücke (das Gebot trifft den Brief, die Tageslücke wird jeden Tag nachgekauft), der Überschuss wird auf den ganzen Wochenvorrat umgelegt, damit eine fast satte Kolonie für die letzten Stück nicht das Vielfache des Ankerpreises bietet (im 40-Bot-Lauf sonst 9 → 138 Cr für Grundmedizin); was die Lücke nicht braucht, bleibt im Wallet |
| Kommandant | verkauft ins Gebot („Verkaufen" am Gebot, Tab Handel) oder stellt eine Verkaufsorder zum oder unter dem Gebot; Ausführungspreis ist der der älteren Order |
| Nicht kaufbar | ohne Order bleibt ein Gebot stehen – das sichtbare Signal „die Bevölkerung zahlt X Cr für Y" |
| Zurückziehen, Preis ändern | für Bevölkerungsgebote gesperrt (`cancelOrder`, `updateOrderPrice`) |
| Eroberung, Auflösung | Gebote der Kolonie werden zurückgezogen (Escrow zurück ins Bevölkerungs-Wallet), danach wandert das Wallet wie bisher |
| Versorgungswarnung | unverändert (Code 505), Text nennt jetzt das Gebot |

Warum die zweistufige Verteilung: eine reine Verhältnisverteilung hätte
Grundnahrung und Luxuspaket bei knapper Kasse gleich unterversorgt. Die
Grundsicherung deckt zuerst, was die Grundgüter zum aktuellen Preis kosten;
erst der Rest hebt die Gebote der höheren Güter. Ist der Briefkurs eines
Grundguts höher als das ganze Budget, fällt das Gebot darunter, es wird
nichts gekauft, und der Kommandant sieht am Gebot, was die Bevölkerung
tragen kann. Steht kein Brief, entsteht trotzdem ein Gebot aus dem
Verhältnisanteil.

**Startpreis.** Erster Tag einer neuen Kolonie: Wallet 16 000 Cr, Einkommen
0, Budget 2 286 Cr, Lücke Grundnahrung 2 000 × 0,0002 × 24 × 7 ≈ 67 Stück,
Gebot ≈ 34 Cr. Die Startorder zu 60 Cr würde nie ausgeführt.
`WorldSeed.STARTER_SELL_ORDER_PRICE` und `DEFAULT_CONSUMER_PRICE` des Bots
gehen auf **20 Cr**; der offene TODO-Punkt „Startpreis 60" ist damit
entschieden. Dauerhaft: 2 000 Arbeiter, Startwarteschlange bindet rund 75 %
der Arbeitskraft → 10 Cr je Spielstunde Lohn → 240 Cr je Tag gegen 9,6
Stück Grundnahrung je Tag → rund 25 Cr je Stück tragbar.

## D. Arbeiter und Akademiker

`Population.currentCount` bleibt die **Gesamtbevölkerung** (Wohnraum,
Sicherheit, Gateway-Gewicht, Höchststand, Wachstumsgeld unverändert);
neu `Population.academics`. Arbeiter = Gesamt − Akademiker. Nur Arbeiter
zählen für die Arbeitskraft-Bremse (`ChainPlanner`,
`ColonySpeedBreakdown.availableWorkers`).

### Güterstaffel der Arbeiter

Die Tabelle `consumerNeedPerCapitaPerGameHour` behält Schlüssel und
Reihenfolge-Semantik, wird aber die **Staffel**: Eintrag i gehört zur
Wohnstufe i. Die Reihenfolge ist zugleich die Einkaufsreihenfolge (Vorrang).

| Stufe | Einwohner bis | Pflichtgut ab dieser Stufe | Bedarf je Kopf und Spielstunde |
|---:|---:|---|---:|
| 1 | 20 000 | Grundnahrung | 0,0002 |
| 2 | 40 000 | Grundmedizin | 0,0001 |
| 3 | 80 000 | Grundkleidung | 0,0001 |
| 4 | 160 000 | Hygienewaren | 0,0001 |
| 5 | 320 000 | Unterhaltungselektronik | 0,0001 |
| 6 | 640 000 | Haushaltswaren | 0,0001 |
| 7 | 1,3 Mio | Erweiterte Medizin | 0,00005 |
| 8 | 2,6 Mio | Grundversorgungspaket | 0,00005 |
| 9 | 5,2 Mio | Standardversorgungspaket | 0,00005 |
| 10 | 10,5 Mio | Komfortpaket | 0,00002 |
| 11 | 21 Mio | Luxuspaket | 0,00002 |
| 12+ | Wohnraum | kein weiteres Gut | |

- **Stufe** s einer Kolonie: kleinste Stufe, deren Grenze die Gesamtbevölkerung
  nicht überschreitet (`Formulas.consumerStage`); Grenzen aus
  `housingCapacityPerLevel` und `housingCapacityGrowthFactor`.
- **Pflichtgüter** = Einträge 1..s. Sie bilden den Lebensstandard der Arbeiter
  (Grundnahrung doppelt gewichtet wie bisher).
- **Wachstumsgut** = Eintrag s+1. Es wird mitgekauft und mitverbraucht (voller
  Pro-Kopf-Bedarf), zählt aber **nicht** in den Lebensstandard. Seine Deckung
  entscheidet den Deckel: unter 1,0 wächst die Kolonie nicht über die
  Stufengrenze hinaus (`PopulationGrowthState.GoodsLimited`), ab 1,0 gilt der
  Wohnraum. Ohne dieses Gut könnte die Bevölkerung das nächste Gut nie
  nachfragen und die Stufe nie freischalten.
- **Grenzverhalten:** an der Stufengrenze wird das Wachstumsgut zum Pflichtgut.
  Ist es dann schlecht versorgt, sinkt der Lebensstandard, die Kolonie
  schrumpft unter die Grenze, das Gut fällt wieder heraus – ein Pendeln um die
  Grenze. Bewusst zunächst zugelassen (die Deckelregel lässt nur Kolonien
  über die Grenze, deren Wachstumsgut voll gedeckt war); Hysterese bei Bedarf.

### Akademiker

| Größe | Regel |
|---|---|
| Bedarf | alle Pflicht- und Wachstumsgüter der Arbeiter zu deren Sätzen PLUS `academicNeedPerCapitaPerGameHour`: Stufe 1 des Zentrums Hygienewaren 0,0002, Grundkleidung 0,0002, Erweiterte Medizin 0,0001, Haushaltswaren 0,0002; Stufe 2 + Hochwertige Haushaltswaren 0,0001; Stufe 3 + Medizinpaket 0,00005; Stufe 4 + Komfortpaket 0,00005; Stufe 5 + Luxuspaket 0,00005 (`academicBaseGoodsCount` = 4, danach ein Gut je Stufe) |
| Lebensstandard der Akademiker | gewichtete Deckung über ALLE ihre Güter, `PlanetStats.academicStandardOfLivingPct`, gleiche Glättung wie bei den Arbeitern. Loyalität und Sicherheit bleiben gemeinsam und hängen wie bisher am Lebensstandard der Arbeiter |
| Deckel | `min(Zentrumskapazität, Kapazität der höchsten Zentrumsstufe L', deren Güter alle mit Deckung ≥ 1,0 versorgt sind)`; ohne Zentrum 0 |
| Nachfrage ohne Akademiker | steht ein Zentrum, fragt die Kolonie die Akademikergüter aller Zentrumsstufen für mindestens `max(Akademiker, 0,001 × Arbeiter)` Personen nach – sonst könnte nie ein Gut „versorgt" sein und nie ein Akademiker entstehen |
| Zuwachs je Spielstunde | `0,01 × (Akademiker + 0,001 × Arbeiter) × (1 − Akademiker / Deckel)` bei Akademiker-Lebensstandard ≥ 50 %; die Rekruten kommen aus den Arbeitern (Gesamtzahl unverändert) |
| Rückgang | Lebensstandard der Akademiker unter 30 %: `−Akademiker × 0,01 × (30 − LS) / 30` je Stunde wie bei den Arbeitern; über dem Deckel (Zentrum zurückgebaut, Gut fehlt): Überhang × 0,05 je Stunde; **unbezahlt** (Guthaben reicht am Kolonietag nicht): der unbezahlte Teil sofort. Alle Rückgänge gehen zurück zu den Arbeitern |
| Gehalt | `Akademiker × 24 × wagePerWorkHour` je Kolonietag, Kommandant → Bevölkerungs-Wallet, `TransactionReason.Wage` „Forschungsgehalt". Ein voll besetztes Zentrum Stufe 1 kostet damit so viel wie 20 000 voll beschäftigte Arbeiter |
| Ganze Menschen | Übertragskonto `academics:<Kolonie>` wie beim Wachstum |
| Verluste, Eroberung | Zivilverluste treffen die Gesamtzahl, Akademiker werden auf die Gesamtzahl gekappt; bei Eingliederung ziehen Akademiker mit |

### Forschungszentrum

`b_research` „Forschungszentrum", neue `BuildingCategory.Research`,
`researchCapacityPerLevel` 20 000 mit Verdopplung je Stufe (Kosten und
Baustoffe verdoppeln wie beim Wohnkomplex, `Formulas.housing*`), Unterhalt
4 Cr je Stufe und Spielstunde, Baustoffe Stahl 2 + Glaswerkstoff 1 +
Leitermetall 1, Druckhabitatsegment ab 4, Energienetzbaugruppe ab 6.
Belegt einen Bebauungsplatz je Stufe. Kein Produktionstempo.

### Forschungsniveau

Abfrage `researchLevel`: Summe der Akademiker über alle Kolonien des
Kommandanten. Sichtbar im Konto und je Kolonie. Gnadenfrist, Verfall und
Wirkung kommen mit dem Forschungsmodul (Konzept folgt).

## E. Der Kolonietag danach

`Economy.colonyDay`, Reihenfolge weiter tragend: Energie → Unterhalt und
Forschungsgehälter (unbezahlte Akademiker zurück) → **Gebote erneuern**
(zurückziehen, Einkommen glätten, Budget, stellen, Matching) → Verbrauch aus
dem Vorrat mit zwei Lebensstandards → Kernwerte → Wachstum der Arbeiter mit
Staffeldeckel, Wechsel der Akademiker → Flankenmeldungen. `startColonyRhythm`
stellt die ersten Gebote sofort.

## F. Katalog

- **Gelöscht:** `p_trinkwasserration`, `p_standardnahrung` (Nahrung enthält
  beides). In den Rezepten der Pakete ersetzt durch `p_grundnahrung` mit
  zusammengelegter Menge: Grundversorgungspaket Grundnahrung 4,
  Standardversorgungspaket Grundnahrung 2, Medizinpaket Grundnahrung 1,
  Komfortpaket Grundnahrung 1, Luxuspaket Grundnahrung 1, Infanteriepaket
  Grundnahrung 4, Schweres Truppenpaket Grundnahrung 2. Produkte 187 → 185.
- **Neue Kategorie `MilitaryEquipment`** („Militärausrüstung"):
  Infanterieausrüstung, Schwere Bodenausrüstung, Infanteriepaket, Schweres
  Truppenpaket. Konsumgüter damit 26 → 20 (Kategoriesummen aus Konzept 20
  ändern sich entsprechend).

## G. Oberfläche

- **Produktion:** Vorschau und Warteschlange zeigen die Löhne des Auftrags;
  Status „Löhne offen" (509) mit Fortsetzen.
- **Bevölkerung:** Arbeiter und Akademiker getrennt, Stufe und Grenze der
  Staffel mit dem Wachstumsgut und seiner Deckung, Deckel und Kapazität des
  Zentrums, Lebensstandard der Akademiker, Einkommen und Tagesbudget der
  Bevölkerung, je Gut Gebot, Brief, Vorrat, Reichweite, Deckung, Gruppe
  (Pflicht, Wachstum, Akademiker). Der Preisanhalt beim Anbieten nennt das
  Gebot.
- **Handel:** Gebote der Bevölkerung als „Bevölkerung von X", ohne
  Zurückziehen und Preisänderung, mit „Verkaufen" aus dem eigenen Lager.
- **Bebauung:** Karte Forschungszentrum (Plätze, Akademiker), Kategorie
  „Forschung", Grafik `b_research.svg`.
- **Konto:** Forschungsniveau.
- **Statistik:** Deckungs-Chips für alle Staffelgüter mit Katalognamen.

## H. Bot

- Preispolitik: verkauft **ins Gebot** – Verkaufsorders je Grundgut zum
  aktuellen Gebot der eigenen Bevölkerung (Neupreis, wenn das Gebot wandert),
  `DEFAULT_CONSUMER_PRICE` 20 als Rückfall ohne Gebot. Das Preisband nach
  Deckung entfällt.
- Löhne: gestoppte Aufträge mit Code 509 werden nicht verworfen, sondern
  fortgesetzt, sobald das Guthaben reicht. Kreditreserve unverändert (zwei
  Tage Vollbeschäftigung).
- Grundbedarf: Grundnahrung, Grundmedizin, Unterhaltungselektronik wie
  bisher; Grundkleidung und höher baut der Bot nicht – seine Kolonien
  deckeln damit bei Wohnstufe 2 (40 000). Kein Forschungszentrum.

## I. Nicht Teil dieses Konzepts

- Das Forschungsmodul (Wirkung, Gnadenfrist, Verfall des Niveaus).
- Soldaten aus der Bevölkerung.
- Hysterese an der Stufengrenze (Teil D).
- Matching nach einem Depot-Zugang ohne Id-Generator (`Depot.add` weckt
  schlafende Orders, kreuzt aber nicht sofort; das nächste Ereignis am
  Posten oder der nächste Kolonietag holt es nach).
- Getrennte Vorratsziele je Gut.

## J. Betroffene Stellen

Backend: `SharedConstants`, `GameConstants`, `Formulas` (Staffel, Akademiker,
Lohn), `ChainPlan.wageCredits`, `ChainPlanner`, `ProductionCommands`,
`ShipyardCommands`, `RecruitmentCommands` (Lohnbuchung, Code 509),
`Economy` (Gebote, zwei Lebensstandards, Wachstum, Gehälter),
`MarketCommands` (Bevölkerungsgebote im Matching, Abbruch, Erstattung),
`MarketOrder.populationColonyId`, `Population.academics`,
`PlanetStats.academicStandardOfLivingPct`, `PopulationSupply`,
`ColonySpeedBreakdown`, `PopulationGrowthState.GoodsLimited`, `Ledger`
(Einkommenszähler), `GameState`, `ColonyConquest`, `GroundBattleCommands`,
`BuildingCommands`/`BuildingCategory.Research`, `BuildingType.researchCapacityPerLevel`,
`ProductCategory.MilitaryEquipment`, `Notifications.CODE_WAGES_UNPAID`,
`WorldSeed` (Startpreis), `GameSocket` (`researchLevel`). Tests:
`WagesTest`, `PopulationBidsTest`, `PopulationClassesTest`;
`IntegerQuantitiesTest` und `PlanetaryPostTest` auf Gebote umgestellt.
Kataloge: `products.json`, `buildings.json`, `shared/game-constants.json`.
Frontend: Modelle, `colony-detail`, `statistics`, `account`,
`product-category-labels`, `shared-constants`. Bot: `Economy`, `Trade`,
`GameSpeed`. Konzepte 36, 37, 20, Spieldesign 06, Mechanik 10: Verweise.
