# Mechanik: Handelsabwicklung, Depots und Bevölkerungskonsum

*Grundprinzipien: `Konzeption/05_Handelsgilde_und_Warenprinzip.md`.
Quelle: `02/09_Handelsabwicklung_und_Bevoelkerungskonsum.md`
(vollständig, da dieses Dokument bereits Präzisierungs-/Mechanik-
charakter hat) sowie `02/05_Handelsgilde_und_Warenverkehr.md` §11
(Arbitrage-Formel).*

## 1. Physische Warenhaltung

Relevante Lagerorte: Kolonielager, Handelsstationen, planetare
Handelsdepots, Frachter. Ware in einer Verkaufsorder ist für diese
Order gebunden (nicht parallel nutzbar); bei Kauforders wird
entsprechend Geld gebunden.

## 2. Ein gemeinsamer Systemmarkt

Pro Sonnensystem: **ein** gemeinsamer Markt der Handelsgilde, keine
getrennte Preisbildung je Planet.

```text
Stationsverkauf / Stationskauf:  Ware an der System-Handelsstation
Depotverkauf / Depotkauf:        Ware in einem Handelsdepot auf einem
                                  konkreten Planeten
```

Depotorders werden nach Planet aufgetrennt angezeigt, gehören aber zum
gemeinsamen Systemmarkt (kein eigenes planetarisches Orderbuch).

> **Änderung (Konzeption/Spieldesign/05_...md, §14; Umsetzungskonzept/
> 21_...md):** Stationsverkauf/-kauf (siehe Kasten) ist in einem NORMALEN
> Sonnensystem nicht mehr möglich – nur noch an einer sektoralen
> Handelsstation der Handelsgilde. Depotverkauf/-kauf bleibt überall
> möglich, erfordert zwischen den beiden beteiligten Kommandanten aber
> einen gültigen Handelsvertrag (nicht für die Bevölkerung, siehe §4/§12).
> An einer Handelsstation gilt keine dieser beiden Einschränkungen.

## 3. Planetare Handelsdepots

- Reale Lagerorte der Handelsgilde, kein virtueller Verweis auf das
  Kolonielager.
- Für Depotverkauf muss der Verkäufer die Ware ins Depot übertragen.
- Ein fremder Spieler kann aus einem Depot nur kaufen, wenn eigene
  Frachter dort gelandet sind (Depotkauf ersetzt keinen interplanetaren
  Transport).
- Kolonien auf demselben Planeten wie das Depot handeln unmittelbar
  damit (kein eigener ausgespielter lokaler Frachtertransport nötig).
- Nach lokalem Kauf ist die Ware sofort wirtschaftlich verfügbar, auch
  wenn der interne Transport ins eigene Kolonielager technisch noch
  läuft.

## 4. Bevölkerung und Handelsorte

- Bevölkerung kauft zu denselben tatsächlichen Sell-Order-Preisen wie
  Spieler (kein künstlicher Bevölkerungspreis).
- Sie kauft **ausschließlich am eigenen Planetaren Handelsposten ihrer
  Kolonie** (Orders mit Depot = diese Kolonie: eigene Lagerorders und von
  Vertragspartnern dort abgesetzte Fracht). Nicht an der
  System-Handelsstation, nicht aus Depots anderer Kolonien – auch nicht auf
  demselben Planeten (Umsetzungskonzept/36, ersetzt die frühere
  Systemmarkt-Regel).
- Der Kauf ist sofort wirksam: die Ware wandert in den **Vorrat der
  Bevölkerung** (getrennt vom Kolonielager, vom Kommandanten nicht
  verkaufbar).

## 5. Blockaden und laufende Transporte

```text
Blockadebeginn → neue Transporte zu/von diesem Planeten: verboten
Bereits unterwegs befindliche Transporte: werden noch durchgelassen
```

Bevölkerungskäufe an der System-Handelsstation sind ab wirksamer
Blockade ebenfalls gesperrt; bereits vor Blockadebeginn ausgeführte
Orders gelten als abgeschlossen. Käufe aus bereits vorhandenen
planetaren Depots sind davon nicht automatisch betroffen.

An einer sektoralen Handelsstation der Handelsgilde ist grundsätzlich
KEINE Blockade möglich (Konzeption/Spieldesign/05_...md, §6/§13) – dieser
gesamte Abschnitt betrifft also ausschließlich den Handel in normalen
Sonnensystemen.

## 6. Tageseinkauf und Vorrat (ersetzt das geglättete Konsumbudget)

Seit Umsetzungskonzept/36 kauft die Bevölkerung **einmal je Spieltag**
(zur Tageszeit ihrer Kolonie = Gründungszeit + n Tage) und hält je
Grundkonsumgut einen **Vorrat**:

```text
Tagesbedarf(Gut)  = Bevölkerung × BedarfProKopfUndStunde(Gut) × 24
Vorratsziel(Gut)  = ⌈Tagesbedarf × populationStockTargetDays⌉   (7 Tage)
Einkauf(Gut)      = Vorratsziel − Vorrat, begrenzt durch Budget und Angebot
```

Budget ist das gesamte Bevölkerungs-Wallet, zu gleichen Teilen auf die
Grundgüter verteilt; was ein Gut nicht ausgibt, fließt den folgenden
Gütern zu. Es kann nie mehr ausgegeben werden als vorhanden. Gekauft wird
in ganzen Stücken, günstigste Order zuerst.

**Notkauf:** Liegt der Vorrat eines Guts unter
`populationEmergencyPurchaseBelowDays` (1 Tag) und am eigenen Posten
erscheint eine Order (neu, umgepreist, aus dem Lager nachgefüllt), kauft die
Bevölkerung sofort nach, statt auf den nächsten Tag zu warten.

Die frühere Glättung des Budgets (`N = 0,9·N + 0,1·Einkommen`) entfällt:
der Vorrat selbst glättet die Versorgung.

## 7. Konsumkategorien und Priorisierung

Mindestens zwei Prioritätsstufen: **Grundbedarf** vor **Luxus**
(weitere Zwischenstufen möglich). Budget wird in Kategorie-Reihenfolge
verarbeitet – erst wenn Grundbedarf nicht mehr sinnvoll ausgeben kann,
steht der Rest niedrigeren Stufen zur Verfügung. Keine feste
Preisgrenze: bei extremer Knappheit akzeptiert die Bevölkerung für
wenige Einheiten eines stark benötigten Grundbedarfsguts sehr hohe
Preise.

## 8. Diversifikation nach relativem ungedecktem Bedarf

```text
missingShare = max(0, (need - bought) / need)
```

Beispiel:

```text
Nahrung: Bedarf 10.000, davon 5.000 gedeckt → missingShare = 0,5
Wasser:  Bedarf 10,     davon 0 gedeckt     → missingShare = 1,0
```

Wasser erhält damit doppeltes Gewicht gegenüber Nahrung, trotz absolut
viel geringerer Stückzahl. Zahlungsbereitschaft entsteht aus Bedarf,
Knappheit und Budget – **kein** fester Referenzpreis.

## 9. Deterministische Budgetverteilung (ein Durchlauf)

Für jede Kategorie: feste Güterreihenfolge, Summe aller
`missingShare`-Werte zu Beginn berechnen. Pro Gut:

```text
goodBudget = budget × weight / remainingWeight
remainingWeight -= weight    (nach Verarbeitung des Guts)
```

Kann `goodBudget` nicht vollständig ausgegeben werden (fehlendes
Angebot, gedeckter Bedarf, günstige Preise), bleibt der Rest im
allgemeinen Budget für nachfolgende Güter verfügbar – **kein** zweiter
Verteilungsdurchlauf nötig.

Innerhalb eines Guts: Sell Orders vom niedrigsten zum höchsten Preis
abarbeiten, höchstens so viel kaufen wie Angebot, Restbedarf und
verfügbares Budget erlauben.

### Referenzimplementierung (Java, aus Ursprungsdokument)

```java
double budget = 0.9 * previousN + 0.1 * income;

for (int category = 0; category < 3 && budget > 0; category++) {
    List<Good> goods = getGoods(category);

    double remainingWeight = 0;
    for (Good good : goods)
        remainingWeight += good.missingShare();

    for (Good good : goods) {
        double weight = good.missingShare();
        if (weight <= 0)
            continue;

        double goodBudget = budget * weight / remainingWeight;
        remainingWeight -= weight;

        for (SellOrder order : good.getSellOrdersByPrice()) {
            double amount = Math.min(order.amount,
                Math.min(good.remainingNeed(), goodBudget / order.price));

            if (amount <= 0)
                break;

            buy(order, amount);

            double cost = amount * order.price;
            goodBudget -= cost;
            budget -= cost;

            if (goodBudget <= 0 || good.remainingNeed() <= 0)
                break;
        }
    }
}

double missingShare() {
    if (need <= 0)
        return 0;
    return Math.max(0, (need - bought) / need);
}
```

Güterreihenfolge innerhalb einer Kategorie muss **fest und
reproduzierbar** sein (deterministisches Kaufverhalten bei identischem
Ausgangszustand). Laufzeit: linear über Anzahl Konsumgüter, plus nur die
tatsächlich betrachteten Sell Orders.

## 10. Bedarf und geglättete Bedarfsdeckung

```text
Bedarf = Bevölkerung × BasisbedarfProPerson
```

Beispiel: `Deodorant: 0,1 kg pro Person und Monat`.

- Gegessen wird je Spieltag der Tagesbedarf aus dem Vorrat (ganze Stücke,
  Rest im Übertragskonto). Die **Deckung** eines Guts am Kolonietag ist
  `gedeckt × (1 + 0,5 × min(1, Vorratsreichweite / Vorratsziel))`: 1,0 wenn
  der Tag gedeckt war, bis 1,5 mit vollem Vorrat, 0 ohne Essen – ein leerer
  Vorrat bekommt keinen Bonus.
- Der Lebensstandard ist das gewichtete Mittel der Deckungen (Grundnahrung
  doppelt), geglättet mit einer Zeitkonstante von einem Spieltag.

## 11. Erweiterbare Konsumstufen

```text
Stufe 1: Grundversorgung
Stufe 2: einfache zusätzliche Konsumgüter
Stufe 3: höherwertiger Konsum
Stufe 4+: zunehmend luxuriöse Güter
```

- Höhere Stufen: deutlich geringerer mengenmäßiger Bedarf – erste
  Stufen ca. **Faktor 10** je Stufe (nicht dauerhaft fix, höhere
  Kategorien können kleinere Abstände wie Faktor 2 verwenden).
- Stufen bestimmen primäre Kaufreihenfolge; innerhalb einer Stufe: feste
  Sort-Spalte (nachrangig, für Determinismus).

## 12. Erreichbare Angebote für Bevölkerung

- Bevölkerung darf kaufen aus: Sell Orders am **eigenen** Planetaren
  Handelsposten ihrer Kolonie (siehe §4) – egal, wer der Verkäufer ist.
- **Nicht** an der System-Handelsstation, **nicht** aus Depots anderer
  Kolonien, auch nicht auf demselben Planeten.
- Bevölkerungen bleiben nach Kolonie getrennt (eigene Kaufkraft,
  Bedürfnisse, Vorrat und Konsumabwicklung je Kolonie, auch bei mehreren
  Kolonien auf demselben Planeten).
- Bevölkerung erzeugt **keine** Buy Orders – nur Käufer bestehender
  Sell Orders.
- Die Handelsvertrag-Pflicht (§2-Kasten) gilt NUR zwischen Spieler-
  Kommandanten. Bevölkerung ist keine Vertragspartei und kauft aus jeder
  Order an ihrem Posten.

## 13. Handelsdepots, Lager und Orders – Regeln

- Handelsdepots der Gilde: nicht militärisch angreif-/beschlagnahmbar.
- Lagerung für den Spieler kostenlos (Infrastrukturkosten erzählerisch
  indirekt durch die Gilde finanziert).
- Sell Orders: keine feste Laufzeit, bestehen bis vollständig
  ausgeführt oder abgebrochen; Teilausführungen möglich.
- Abbruch einer Sell Order: nicht verkaufte Ware zurück ins persönliche
  Lager am selben Handelsort (nicht automatisch weitertransportiert).

## 14. Arbitrage-Formel (Referenz)

```text
Handelsgewinn = Preisunterschied − Transportkosten − Gateway-Gebühren
                − sonstige Risiken und Kosten
```

## 15. Handelsgilde-Station: Depot und Orderbuch (Umsetzungskonzept/22_...md)

Präzisierung gegenüber §2/§13: An einer sektoralen Handelsstation ist
"Stationsverkauf/-kauf" (Kasten in §2) nicht mehr rein fracht- bzw.
kolonie-basiert, sondern läuft über ein eigenes, unbegrenztes Depot je
Kommandant (beliefert per Frachter-Flotte) mit einem echten zweiseitigen
Orderbuch (Kauf- UND Verkaufs-Orders, sofortige Ausführung auch in
Teilausführung beim Kreuzen). Ware einer Verkaufs-Order und Credits einer
Kauf-Order sind ab dem Einstellen gebunden (§1 traf diese Aussage für
Kauf-Orders bereits vorausschauend). Eine Handelsgilde-eigene Grundordnung
auf beiden Seiten stellt sicher, dass für jede Ware überhaupt ein Markt
existiert.

## Offene Zahlenfragen

- Wie lange können Waren an einem Handelsposten gelagert werden – kostet
  Lagerung Geld, kann Lagerkapazität knapp werden? (Laut §13 aktuell:
  kostenlos, keine Kapazitätsgrenze definiert.)
- Wie groß ist die Ladekapazität unterschiedlicher Frachter, wie
  funktioniert Verladen/Entladen im Detail?
- Können Handelsrouten automatisiert wiederholt werden (solange
  weiterhin reale Flotten fliegen)?
