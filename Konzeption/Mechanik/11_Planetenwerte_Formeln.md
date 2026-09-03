# Mechanik: Die vier Planetenwerte – Formeln und Beispiele

*Grundprinzipien: `Konzeption/07_Planeten_und_Bevoelkerung.md`. Quelle:
`02/07_Planeten_Bevoelkerung_und_politische_Integration.md` §5, §9.*

## 1. Das 100-%-Prinzip

```text
100 % = vollständige Deckung des Referenzbedarfs der AKTUELLEN
        Bevölkerungsgröße (nicht das Maximum).

Infrastruktur, Sicherheit, Lebensstandard: können deutlich > 100 % sein.
Loyalität: Sonderfall, sinnvoll bei 100 % gedeckelt.
```

## 2. Infrastruktur

```text
Stufe für 1 Mrd. Einwohner ausgelegt:
  bei 1,00 Mrd. Einwohnern   → ≈ 100 %
  bei 0,50 Mrd. Einwohnern   → überdimensioniert (> 100 %, kostet
                                 trotzdem Unterhalt)
  bei 1,25 Mrd. Einwohnern   → überlastet (< 100 %)
```

Kausalkette (kein zusätzlicher allgemeiner Produktionsbonus):

```text
Infrastruktur → ermöglicht Bevölkerung → Bevölkerung stellt Arbeit
bereit → Arbeit ermöglicht Produktion
```

## 3. Sicherheit

```text
Sicherheit ≈ f(stationierte Bodentruppen / Bevölkerungsgröße)
bestimmte Truppenstärke pro Bevölkerung ≈ 100 %
massive Überstationierung → weit über 100 % möglich
```

**Loyalitätssockel:** Eine wirklich loyale Bevölkerung hält notfalls
auch ohne stationierte Truppen selbst Ordnung (§8: "hohe Loyalität →
weniger notwendige Truppen"). Sicherheit ist deshalb das Maximum aus
Garnisonswert und einem festen Anteil der aktuellen Loyalität:

```text
Sicherheit = max(garnisonsbasierte Sicherheit, Loyalität × 0,3)
```

Bei 100 % Loyalität liegt der Sockel damit bei 30 % – eine Garnison
bleibt für höhere Werte weiterhin nötig, macht eine frisch gegründete,
noch unbewaffnete, aber loyale Kolonie aber nicht automatisch zu einem
0-%-Sicherheitsrisiko.

Wichtige Trennung:

```text
Loyalität: Will die Bevölkerung diese Herrschaft?
Sicherheit: Kann die Herrschaft ihre Ordnung durchsetzen?
```

Beispiel: eroberte Welt 10 % Loyalität + 200 % Sicherheit; alte
friedliche Kernwelt 98 % Loyalität + 50–60 % Sicherheit. Soldaten
erzeugen nicht automatisch Loyalität.

## 4. Lebensstandard

```text
Lebensstandard ≈ f(tatsächliche Konsumgüterversorgung / Bevölkerung)
vollständige Deckung normalen Konsumbedarfs ≈ 100 %
Unterversorgung → < 100 %; umfangreichere/hochwertigere Versorgung → > 100 %
```

Unmittelbar mit Handel/Blockaden verbunden, da Waren physisch
transportiert werden müssen (siehe
`09_Handelsabwicklung_und_Markt.md`).

## 5. Loyalität

```text
Loyalität ≈ f(Zeit unter aktueller Herrschaft, Lebensbedingungen)
```

- Wichtigster Faktor: **Zeit** unter stabiler Herrschaft.
- Gute Versorgung/Stabilität: beschleunigt Aufbau.
- Dauerhafte Unterversorgung/schwere Unruhen: bremst oder beschädigt
  Loyalität.

Startwerte:

```text
Heimatplanet:          starker struktureller Bonus + einmaliger,
                        nicht übertragbarer Sonderbonus für die
                        „Heimatregierung" (Startspieler)
Selbst gegr. Kolonie:  relativ hohe Grundloyalität
Eroberte Welt:         sehr niedrige Startloyalität
```

## 6. Politisches Gewicht (Gateway-Formel, Referenz)

```text
Gateway-Gewicht = Bevölkerung × Loyalität   (pro Planet)
```

Vollständig ausformuliert inkl. Aufsummierung über mehrere Planeten in
`08_Gateway_und_Zollmechanik.md` §2.

## 7. Durchgerechnete Beispiele

| Szenario | Bevölkerung | Infrastruktur | Sicherheit | Lebensstandard | Loyalität |
|---|---|---|---|---|---|
| Junge Wachstumskolonie | 40 Mio. | 190 % | 130 % | 155 % | 82 % |
| Frisch eroberte Welt | 6 Mrd. | 95 % | 210 % | 70 % | 12 % |
| Alte Kernwelt | 12 Mrd. | 110 % | 65 % | 140 % | 99 % |

Interpretation:

- **Junge Wachstumskolonie:** Infrastruktur/Versorgung für Wachstum
  vorbereitet, überdimensionierte Infrastruktur kostet aber bereits
  Unterhalt.
- **Frisch eroberte Welt:** militärisch fest unter Kontrolle, politisch
  kaum integriert – riesige Besatzungsarmee gebunden, trotz 6 Mrd.
  Einwohnern nur kleiner Teil des möglichen Gateway-Gewichts nutzbar.
- **Alte Kernwelt:** hohe Loyalität → keine riesige Garnison nötig;
  hoher Lebensstandard verschlingt dafür dauerhaft enorme Mengen an
  Konsumgütern.

## 8. Typische Wechselwirkungen (qualitativ, keine Formeln)

```text
zu wenig Infrastruktur      → schlechtere Lebensbedingungen
                             → geringeres Bevölkerungswachstum
fehlende Konsumgüter        → sinkender Lebensstandard
                             → langsamere Loyalitätsentwicklung
niedrige Loyalität + geringe Sicherheit → hohes Aufstandsrisiko
hohe Loyalität               → weniger notwendige Truppen
große Bevölkerung             → mehr benötigte Infrastruktur/Sicherheit
große Bevölkerung + hohe Loyalität → starkes Gateway-Gewicht
```

## Offene Zahlenfragen

- Genaue Berechnung der Prozentwerte für alle vier Planetenwerte
  (aktuell nur Referenzpunkt-Beispiele, keine vollständige Formel).
- Konkrete Infrastrukturstufen und deren Unterhaltskosten.
- Notwendige Truppenmenge pro Einwohner für 100 % Sicherheit.
- Genaue Konsumgütermengen für 100 % Lebensstandard, genaue Vorteile
  einer Überversorgung über 100 %.
- Geschwindigkeit der Loyalitätsentwicklung (Formel); exakter
  Loyalitätsstartwert selbst gegründeter Kolonien; exakte Höhe des
  Heimatplanet-Sonderbonus.
- Formel für Aufstandsrisiko und für Bevölkerungswachstum.
- Genaue Formel für Gateway-Gewicht bei mehreren exakt gleich starken
  konkurrierenden Spielern im selben System.
- Auswirkungen extremer Unterversorgung/Infrastrukturüberlastung
  (konkrete Schwellenwerte).
