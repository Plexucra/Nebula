# Mechanik: Kern der Kampfberechnung (Raum- und Bodenkampf)

*Grundprinzipien: `Konzeption/03_Militaer_und_Eroberung.md`. Quelle:
`02/11_Kampfmechanik_Blockaden_und_Landungsabwehr.md` §1–10 (neuester
Stand, überschreibt ältere Annahmen aus
`02/10_Planetare_Bebauung_Bodenkrieg_und_Gefechtsaufmarsch.md`).
Blockaden/Aufmarsch/Exposition stehen in
`06_Blockaden_Gefechtsablauf_Aufmarsch.md`, Bodentruppen-Spezifika in
`05_Bodentruppen_und_Bodenkrieg.md`.*

## 1. Grundprinzip: gruppenbasierte, simultane Berechnung

Kämpfe werden **nicht auf Ebene einzelner Schiffe/Waffenträger**
gespeichert oder berechnet, sondern als aggregierte Gruppen nach
Einheitentyp:

```text
Beispiel: 12.000 Jäger, 800 Kreuzer, 40 Schlachtschiffe
```

Schaden innerhalb eines Kampfticks wird **simultan** berechnet: Alle zu
Tickbeginn kampffähigen und exponierten Einheiten verursachen ihren
Schaden auch dann noch, wenn sie im selben Tick zerstört werden.

## 2. Produktionsaufwand als militärischer Basiswert

```text
Produktionsaufwand = Basiszeit × Basisarbeitskräfte
```

- Spezialisierung, tatsächlicher Herstellungsort oder reale
  Produktionsdauer verändern den militärischen Wert einer Einheit
  **nicht**.
- Dient als gemeinsame Grundlage für: Expositionsgrenzen (siehe
  `06_Blockaden_Gefechtsablauf_Aufmarsch.md`), Basisschaden, Haltbarkeit,
  proportionale Schadensverteilung.
- Kein zusätzlicher abstrakter Kampfkraftwert.

## 3. Basisschaden und Haltbarkeit

```text
Gruppenschaden = Anzahl × Produktionsaufwand je Einheit × globaler Schadensfaktor

Haltbarkeit = Produktionsaufwand je Einheit × globaler Haltbarkeitsfaktor
```

- Dieselben globalen Faktoren für alle Einheitentypen; keine
  klassenindividuellen Basisboni auf Schaden/Haltbarkeit.
- Balancingwert: Schadens-/Haltbarkeitsfaktor so gewählt, dass zwei
  gleich starke, neutral gegeneinander kämpfende Seiten ca. **20 %**
  ihrer Kampfkraft pro Kampftick verlieren.

## 4. Kontersystem (drei Wirkungsstufen)

```text
Vorteil  = ×2
Neutral  = ×1
Nachteil = ×0,5
```

- Der Kontermultiplikator verändert **nur den verursachten Schaden**,
  nicht die Zielgruppenverteilung (siehe §5).
- Bei 20 % neutralem Referenzverlust:

```text
Vorteil  → 40 % Verlust
Neutral  → 20 % Verlust
Nachteil → 10 % Verlust
```

- Kein zusätzlicher Übermachtsbonus – zahlenmäßige/wirtschaftliche
  Überlegenheit wirkt bereits linear über Einheiten × Produktionsaufwand.

## 5. Schadensverteilung auf Zielgruppen

Eine angreifende Gruppe wählt keine Einzelziele. Ihr Basisschaden wird
**proportional zum Produktionsaufwand aller exponierten gegnerischen
Gruppen** verteilt; erst danach wird pro Zielgruppe der passende
Kontermultiplikator angewendet.

Konsequenzen:

- Große/wertvolle gegnerische Gruppen ziehen mehr Feuer auf sich.
- Viele billige Einheiten können nicht allein durch Stückzahl fast
  allen Schaden absorbieren.
- Keine Zielpriorisierungs-/Fokusfeuermechanik.

## 6. Überkill

Übersteigt der einer Zielgruppe zugewiesene Schaden deren vollständige
Vernichtung, verfällt der Überschuss nicht:

- Nicht benötigte **Basisfeuerkraft** wird erneut proportional auf die
  verbleibenden gegnerischen Gruppen verteilt.
- Der Kontermultiplikator des ursprünglichen Ziels wird **nicht**
  übertragen – für jedes neue Ziel wird der passende Multiplikator neu
  angewendet.
- Wiederholt sich innerhalb desselben Kampfticks, bis Feuerkraft
  verbraucht oder keine Zielgruppe mehr vorhanden ist.

## 7. Restschaden

Schaden, der nicht für eine weitere volle Einheit reicht, wird als
Restschaden gespeichert – ausschließlich auf Ebene:

```text
Gefechtsseite + Einheitentyp
```

**nicht** pro Flotte, **nicht** pro Einzelschiff.

```text
Gesamtschaden    = alter Restschaden + neuer effektiver Schaden
Verluste         = floor(Gesamtschaden / Haltbarkeit je Einheit)
neuer Restschaden = Gesamtschaden − Verluste × Haltbarkeit je Einheit
```

- Gespeicherter Restschaden ist immer < Haltbarkeit einer Einheit.
- Keine Mindestverluste – geringer Schaden akkumuliert über beliebig
  viele Ticks.
- Gespeichert wird der bereits kontermultiplizierte **effektive**
  Schaden.
- Restschaden mindert nicht die Kampfkraft verbleibender Einheiten
  (volle Leistung bis zur vollständigen Zerstörung).
- Endet das Gefecht, **verfällt der gesamte Restschaden** – keine
  Reparaturmechanik, keine dauerhaften Teilbeschädigungen außerhalb
  eines laufenden Gefechts.

## 8. Mehrere Flotten/Parteien in einem Gefecht

- Exponierte Einheiten einer gesamten Gefechtsseite werden nach
  Einheitentyp aggregiert berechnet (nicht Flotte gegen Flotte).
- Tatsächliche Verluste eines Typs werden **proportional zur
  vorhandenen Stückzahl** auf beteiligte Parteien/Flotten
  zurückverteilt.
- Rundung und Verlustzuweisung sind **deterministisch**, kein Zufall.
- Restschaden bleibt trotzdem nur am Gefecht + Gefechtsseite +
  Einheitentyp gespeichert.

## 9. Gemeinsame Kernmechanik Raum-/Bodenkampf

Beide Kampfarten teilen: gruppenbasierte Berechnung, Produktionsaufwand
als Basiswert, simultaner Schaden, proportionale Schadensverteilung,
Kontermultiplikatoren, Haltbarkeit aus Produktionsaufwand, Restschaden,
Überkill-Weiterverteilung, kein Übermachtsbonus. Bodengefechte besitzen
zusätzlich die Soldaten-/Waffenträger-Regeln aus
`05_Bodentruppen_und_Bodenkrieg.md`.

## Offene Zahlenfragen

- Konkrete Schiffsklassen- und Waffenträger-Kontermatrix (welche Klasse
  kontert welche, sofern noch nicht vollständig für alle Kombinationen
  festgelegt).
- Spätere Simulation/Balancing-Prüfung des neutralen 20-%-Schadenswerts.
- Zielauswahl-Detailfragen, sofern über die reine
  Produktionsaufwand-Proportionalität hinaus noch etwas benötigt wird.
