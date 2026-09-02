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

- Bevölkerung kauft über denselben Systemmarkt zu denselben tatsächlichen
  Sell-Order-Preisen (kein künstlicher Bevölkerungspreis).
- Kauf aus dem eigenen planetaren Depot: direkt, wenn passende Sell
  Order vorhanden.
- Kauf an der System-Handelsstation: letzter Transport zum Planeten ist
  **rein buchhalterisch** – kein simulierter Frachter, Ware gilt nach
  Kauf sofort als verfügbar für die Versorgung.

## 5. Blockaden und laufende Transporte

```text
Blockadebeginn → neue Transporte zu/von diesem Planeten: verboten
Bereits unterwegs befindliche Transporte: werden noch durchgelassen
```

Bevölkerungskäufe an der System-Handelsstation sind ab wirksamer
Blockade ebenfalls gesperrt; bereits vor Blockadebeginn ausgeführte
Orders gelten als abgeschlossen. Käufe aus bereits vorhandenen
planetaren Depots sind davon nicht automatisch betroffen.

## 6. Konsumbudget der Bevölkerung (geglättet)

```text
N = 0,9 × vorherigesN + 0,1 × EinkommenImLetztenZeitintervall
```

`N` ist das für das aktuelle Intervall vorgesehene Konsumbudget – nicht
die gesamte Kaufkraft. `income` = sämtliche Geldzuflüsse der
Koloniebevölkerung im letzten Intervall, unabhängig von der Herkunft.

Zusätzlich Berücksichtigung aufgestauten Geldes:

```text
budget = min(vorhandenesGeld, max(N, vorhandenesGeld / 10))
```

Es kann nie mehr ausgegeben werden als tatsächlich vorhanden.

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

- Ein Produkt kann höchstens zu 100 % seines Bedarfs gedeckt werden –
  **keine** Überversorgung desselben Produkts.
- Nicht das Einzelintervall, sondern die über **zehn Intervalle
  geglättete** Bedarfsdeckung beeinflusst Lebensstandard & Co.

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

- Bevölkerung darf kaufen aus: Handelsdepots **aller** Spieler auf
  demselben Planeten, Sell Orders an der System-Handelsstation.
- **Nicht** direkt aus Handelsdepots anderer Planeten.
- Bevölkerungen bleiben nach Kolonie getrennt (eigene Kaufkraft,
  Bedürfnisse, Konsumabwicklung je Kolonie, auch bei mehreren Kolonien
  auf demselben Planeten).
- Bevölkerung erzeugt **keine** Buy Orders – nur Käufer bestehender
  Sell Orders.

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

## Offene Zahlenfragen

- Wie lange können Waren an einem Handelsposten gelagert werden – kostet
  Lagerung Geld, kann Lagerkapazität knapp werden? (Laut §13 aktuell:
  kostenlos, keine Kapazitätsgrenze definiert.)
- Wie groß ist die Ladekapazität unterschiedlicher Frachter, wie
  funktioniert Verladen/Entladen im Detail?
- Können Handelsrouten automatisiert wiederholt werden (solange
  weiterhin reale Flotten fliegen)?
