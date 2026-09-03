# Umsetzung: Geldsystem, Bevölkerung und Planetenwerte

*Grundlage: `Konzeption/06_Geldsystem.md`,
`Konzeption/07_Planeten_und_Bevoelkerung.md`,
`Mechanik/10_Geldkreislauf_Formeln.md`,
`Mechanik/11_Planetenwerte_Formeln.md`.*

## 1. Datenmodell

```text
Wallet
  ownerType (Player|Population), ownerId, balance

Transaction (Audit-Log, append-only)
  id, fromWalletId | null (null = Geldschöpfung ODER Einzahlung in den
  Ausgleichsfonds, s. §7), toWalletId | null (null = Vernichtung ODER
  Abgabe in den Ausgleichsfonds), amount, reason
  (Wage|Consumption|FleetUpkeep|GatewayFee|Trade|MoneyCreation|Tax|Subsidy), at

PopulationMoneySupplyState (je Planet – Höchststand-Regel)
  planetId, historicalPeakPopulation, lastPopulation

Population (je Colony)
  colonyId, currentCount, growthRatePerInterval
```

## 2. API-Endpunkte

```text
GET  /api/v1/players/{playerId}/wallet
GET  /api/v1/players/{playerId}/transactions?reason=&since=
POST /api/v1/players/{playerId}/transfers
  body: { toPlayerId, amount }
  → direkte Spieler-zu-Spieler-Überweisung (Konzeption/05_... §12)

GET  /api/v1/colonies/{colonyId}/population
GET  /api/v1/planets/{planetId}/money-supply-state
```

Es gibt bewusst **keinen** Endpunkt, mit dem ein Spieler direkt Credits
"erzeugt" – Geldschöpfung läuft ausschließlich über den
`MoneySupplyJob` (s. u.), ausgelöst durch Bevölkerungswachstum.

## 3. MoneySupplyJob (Geldschöpfungsregel)

Läuft nach jedem `PopulationGrowthJob`-Tick je Planet:

```text
if currentPopulation > historicalPeakPopulation:
    delta = currentPopulation - historicalPeakPopulation
    createMoney(amount = delta × creditsPerNewInhabitant,
                toWallet = colonyPopulationWallet)
    historicalPeakPopulation = currentPopulation
    log Transaction(from=null, to=populationWallet, reason=MoneyCreation)
else:
    // kein Effekt, auch bei Wiederanstieg unterhalb des alten
    // Höchststands (Mechanik/10_... §5)
```

## 4. PopulationGrowthJob

```text
Wachstum ≈ f(infrastructurePct, standardOfLivingPct, securityPct,
             ggf. loyaltyPct)   // konkrete Formel offen, siehe
                                 // Mechanik/11_... "Offene Zahlenfragen"
→ liefert delta an currentPopulation der Colony
→ triggert danach PlanetStatsRecalcJob (Neubewertung der 4 Werte
  relativ zur neuen Bevölkerungsgröße) und MoneySupplyJob
```

## 5. PlanetStatsRecalcJob – Zusammenspiel der Werte

Konsolidierte Sicht auf die vier Werte aus `Mechanik/11_...` als
Berechnungspipeline je Zeitintervall:

```text
1. infrastructurePct  = builtCapacity(Colony) / referenceCapacity(population)
2. securityPct        = stationedTroopStrength(Colony) / referenceStrength(population)
3. standardOfLivingPct = smoothedDemandCoverage(Colony)  // aus ConsumptionTickJob
4. loyaltyPct         = loyaltyPrevious
                         + timeFactor(isHomeworld, isOwnColony)
                         + conditionModifier(standardOfLivingPct, securityPct)
                         (gedeckelt bei 100%)
5. gatewayWeightContribution = population × loyaltyPct
   → fließt in GatewayWeightSnapshot (siehe 06_...)
```

## 6. WealthTaxAndRedistributionJob (Ausgleichsfonds)

*Grundlage: `Konzeption/06_Geldsystem.md` §8, `Mechanik/10_...` §7.*

Läuft einmal pro Spieltag (24 Spielstunden), galaxieweit über Spieler
und alle NPCs. Sammelt zwei feste Abgaben ein und schüttet den
kompletten Betrag im selben Lauf wieder aus – es gibt keinen
persistenten Fondsstand zwischen zwei Läufen.

```text
WEALTH_TAX_THRESHOLD = 1000
WEALTH_TAX_RATE      = 0.001   // 0,1 % / Tag
COLONY_TAX_RATE      = 0.01    // 1 % / Tag

pot = 0
for wallet in Wallets where ownerType == Player:
    if wallet.balance > WEALTH_TAX_THRESHOLD:
        tax = wallet.balance × WEALTH_TAX_RATE
        wallet.balance -= tax
        pot += tax
        log Transaction(from=wallet, to=null, reason=Tax, note="Vermögenssteuer")

for colony in Colonies:
    popWallet = wallet(Population, colony.id)
    tax = popWallet.balance × COLONY_TAX_RATE
    popWallet.balance -= tax
    pot += tax
    log Transaction(from=popWallet, to=null, reason=Tax, note="Kolonialabgabe")

totalPopulation = sum(Population.currentCount)
if totalPopulation > 0 and pot > 0:
    perCapita = pot / totalPopulation
    for colony in Colonies:
        popWallet = wallet(Population, colony.id)
        n = Population(colony.id).currentCount
        share = n × perCapita
        popWallet.balance += share
        log Transaction(from=null, to=popWallet, reason=Subsidy, note="Ausgleichsfonds")
```

Wichtig: Die Ausschüttung geht ausschließlich an
Bevölkerungs-/Population-Wallets, nie an Spieler-/Kommandanten-Wallets
– sonst würde der Fonds Kommandanten-Hortung indirekt wieder belohnen
(siehe offene Frage in `Konzeption/06_Geldsystem.md`).

## 7. Menüführung

```text
Kolonie-Detailansicht → Tab "Bevölkerung"
├─ Einwohnerzahl + Wachstumsrate (Trendpfeil)
├─ Die vier Planetenwerte als Kennzahlen mit Referenzlinie bei 100%
│   (Infrastruktur/Sicherheit/Lebensstandard als Balken über 100%
│   möglich, Loyalität als bei 100% gedeckelter Ring)
├─ Historischer Bevölkerungshöchststand (Tooltip: erklärt
│   Geldschöpfungsregel)
└─ Politisches Gewicht im System (Verweis auf Gateway-Ansicht, 06_...)

Player-Dashboard
├─ Wallet-Saldo + Transaktionshistorie (filterbar nach reason)
└─ "Geld überweisen"-Aktion (Spielersuche)
```
