# Mechanik: Schiffsklassen, Kontersystem und Flottenunterhalt

*Grundprinzipien: `Konzeption/03_Militaer_und_Eroberung.md`. Quelle:
`02/03_Schiffe_Militaer_und_Eroberung.md`. Die eigentliche
Schadensberechnung im Kampftick steht in `04_Kampfmechanik_Kern.md`.*

## 1. Schiffskategorien

| # | Kategorie | Funktion |
|---|---|---|
| 1 | Korvette (Kampfschiff A) | Kampf, kontert Kreuzer |
| 2 | Zerstörer (Kampfschiff B) | Kampf, kontert Korvette |
| 3 | Kreuzer (Kampfschiff C) | Kampf, kontert Zerstörer |
| 4 | Frachter | Nur Warentransport |
| 5 | Trägerschiff | Nur Schiffstransport, einziger Typ mit Sprungantrieb (gatewayunabhängig, sehr langsam) |
| 6 | Mannschaftstransporter / Bodentruppentransporter | Transport von Bodentruppen |

## 2. Kontersystem (zyklisch, auf Basis Baukosten)

```text
Korvette  schlägt  Kreuzer
Kreuzer   schlägt  Zerstörer
Zerstörer schlägt  Korvette
```

Jeder Typ: ein klarer Vorteil, eine relevante Schwäche, gegen den
dritten neutral. Die konkrete Umsetzung der Kontermultiplikatoren
(×2 / ×1 / ×0,5) steht in `04_Kampfmechanik_Kern.md` §4 – dieselbe
Kontermatrix gilt für Raum- und Bodenkampf.

## 3. Trägerschiff-Reisezeit

```text
Reisezeit per Trägerschiff ≈ 10 × Reisezeit per Gateway
```

Faktor 10 ist erster Arbeitswert, kein Balancewert.

## 4. Flottenunterhalt

Qualitativ beschrieben, noch **nicht in Formel gegossen**:

- Unterhalt steigt mit Einsatzdauer.
- Unterhalt steigt (implizit) mit Entfernung von der Heimat.
- Kurzfristige Heimatverteidigung: günstig. Langer, entfernter Einsatz:
  zunehmend teuer.
- Unklar, ob ein Maximum existiert.
- Geldwirtschaftlich: Unterhalt wird als Transfer an die regionale
  Bevölkerung ausgeschüttet, nicht vernichtet (siehe
  `10_Geldkreislauf_Formeln.md` §3).

## Offene Zahlenfragen

- Wie stark kontern sich die drei Kampfschiffstypen konkret (Zahlen)? –
  Für den eigentlichen Kampftick inzwischen beantwortet über die
  generische ×2/×1/×0,5-Matrix in `04_Kampfmechanik_Kern.md`, sofern
  diese auch für die Schiffsklassen (nicht nur Waffenträger) final gilt.
- Wie funktionieren Flottenverluste und Ersatzproduktion?
- Wie viele Schiffe kann ein Trägerschiff aufnehmen?
- Werden Trägerschiffe im Kampf automatisch oder nur auf ausdrücklichen
  Flottenbefehl priorisiert angegriffen?
- Wie genau skaliert Flottenunterhalt mit Einsatzdauer, Entfernung und
  Flottengröße, und besitzt er ein Maximum?
- Unter welchen Bedingungen werden Frachter gekapert statt zerstört?
