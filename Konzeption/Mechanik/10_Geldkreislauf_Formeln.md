# Mechanik: Geldkreislauf-Formeln und Stresstest-Szenarien

*Grundprinzipien: `Konzeption/06_Geldsystem.md`. Quelle:
`02/06_Geldsystem.md`.*

## 1. Grundregeln (Zahlenwerte)

```text
1 neuer Einwohner  → +1 Credit (der Bevölkerung/Kaufkraft zugerechnet)
1 Arbeitseinheit    → kostet 1 Credit
Normale Transaktionen erzeugen kein neues Geld.
Keine Steuern, kein simulierter Arbeitsmarkt (vorerst).
```

## 2. Selbstversorgungstest (Beispielrechnung)

```text
Spieler A: 100 Credits
→ zahlt 30 Credits Arbeitskosten für Konsumgüterproduktion
→ A: 70 Credits, Bevölkerung: 30 Credits Kaufkraft
→ Bevölkerung kauft die Güter für 30 Credits von A
→ A: wieder 100 Credits

Ergebnis: kein Geld erzeugt. Eigenversorgung ist keine Gelddruckmaschine.
```

## 3. Handelstest (Beispielrechnung)

```text
Spieler A zahlt 30 Credits Lohn → Bevölkerung A hat 30 Credits Kaufkraft
Spieler B verkauft Konsumgüter an Bevölkerung A → 30 Credits wandern zu B
→ A muss exportieren, um wieder Geld aus anderen Wirtschaftsräumen
  anzuziehen.
```

## 4. Flottenunterhalt und Gebühren (Transferrichtung)

```text
Spieler → Flottenunterhalt → regionale Bevölkerung → Konsumnachfrage
Spieler A → Gateway-Gebühr → Spieler B (Gateway-Kontrolleur)
```

## 5. Geldschöpfungsregel: historischer Bevölkerungshöchststand

**Zentrale Sicherungsregel gegen den Bevölkerungs-Wachstum/Kollaps-
Exploit:**

```text
Neue Credits entstehen NUR für Bevölkerung oberhalb des bisherigen
historischen Höchststandes (bezogen auf den einzelnen Planeten).
```

Beispielrechnung:

```text
Planet wächst 8 Mrd. → 10 Mrd.:  neue Geldbasis für +2 Mrd.
Planet fällt auf 7 Mrd. zurück:  kein Effekt
Planet wächst erneut 7 Mrd. → 10 Mrd.: KEIN neues Geld (alter
                                        Höchststand nicht überschritten)
Planet wächst weiter auf 11 Mrd.: neue Geldbasis für +1 Mrd.
                                   (neuer Höchststand)
```

Alternative (nicht gewählt, aber dokumentiert): Geldbasis an eine
dauerhaft erschlossene Bevölkerungs**kapazität** koppeln statt an den
Höchststand selbst – stabiler, aber abstrakter.

- Der Höchststand wird **pro Planet** geführt, nicht pro Spieler oder
  galaxieweit.
- Tod eines Einwohners vernichtet **keinen** Credit automatisch.

## 6. Bekannte Restlücke: Wegwerf-Kolonien

```text
neue Kolonie gründen → auf neuen lokalen Höchststand wachsen lassen
→ Geld abschöpfen → Kolonie aufgeben → nächste Kolonie gründen
```

Bleibt mechanisch möglich, da der Höchststand pro Planet (nicht pro
Spieler) geführt wird. Ob dies ein reales Problem ist, hängt von noch
offenen Kolonialgründungs-/Entwicklungskosten ab: Die Regel ist nur
sicher, solange Aufwand und Zeit für Gründung/Wachstum größer sind als
der abschöpfbare Betrag.

## 7. Zusammenfassung des vorläufigen Geldmodells

1. Neue dauerhaft zusätzliche Bevölkerung erzeugt neue Geldbasis.
2. Produktion überträgt Credits vom Spieler an die Bevölkerung.
3. Bevölkerung verwendet Credits zum Kauf realer Konsumgüter.
4. Konsum vernichtet Waren, nicht Geld.
5. Spielerhandel verteilt Credits zwischen Spielern und Regionen.
6. Gateway-Gebühren verteilen Credits zwischen Spielern.
7. Flottenunterhalt verteilt Credits zurück an regionale Bevölkerung.
8. Credits werden im normalen Spiel grundsätzlich nicht vernichtet.
9. Tod erzeugt keine automatische Geldvernichtung.
10. Wiederherstellung bereits früher vorhandener Bevölkerung erzeugt
    keine zweite Geldbasis.

## 8. Zu simulierende Testszenarien (Stresstest-Batterie)

Für die erste quantitative Prüfung des Geldmodells (siehe
`Konzeption/06_Geldsystem.md` §6 für die qualitative Einordnung der
einzelnen Dynamiken):

```text
- ein autarker Planet
- zwei spezialisierte Handelspartner
- Rohstoff- und Industriecluster
- eine reiche Transitregion
- eine Kriegsregion mit hoher Flottenpräsenz
- ein stark wachsender Kolonialraum
- eine stagnierende Altregion
- eine Region nach schwerer Bevölkerungskatastrophe
```

Zu beobachtende Messgrößen:

```text
- Gesamtgeldmenge
- Credits pro Einwohner
- Verteilung zwischen Spielern und Bevölkerung
- Kaufkraftstau
- Handelsvolumen
- Vermögenskonzentration
- Geldumlaufgeschwindigkeit
- Auswirkungen von Flottenstationierung
- Auswirkungen von Bevölkerungswachstum
```

## Offene Zahlenfragen

- Wie groß ist die tatsächliche Diskrepanz zwischen
  Bevölkerungs-Geldschöpfung (Größenordnung Milliarden Credits) und
  typischen Markttransaktionen (Größenordnung zehner/hunderter
  Credits)? Erfordert das eine grundsätzliche Skalierungsentscheidung
  (z. B. andere Einheit für Bevölkerungs-Geldschöpfung)?
- Wie wird die räumliche Verteilung der
  Flottenunterhalt-Ausschüttung auf die Bevölkerung genau bestimmt
  (Formel/Gewichtung)?
