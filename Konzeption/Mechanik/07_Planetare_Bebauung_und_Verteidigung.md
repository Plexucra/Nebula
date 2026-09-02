# Mechanik: Planetare Bebauung und Verteidigungsanlagen (Referenz)

*Diese Datei bündelt die reinen Bebauungs-/Verteidigungswerte aus
`05_Bodentruppen_und_Bodenkrieg.md` als eigenständige Kurzreferenz, da
sie sowohl für die zivile Planetenentwicklung als auch für die
militärische Mechanik relevant sind. Für den vollständigen Kontext
(Konfliktschäden, Landungsabwehr) siehe dort.*

## 1. Bebauungskapazität

```text
Bebauungskapazität = f(planetare Eigenschaften, Planetengröße)   [fest]
Infrastruktur + Produktionsanlagen verbrauchen Bebauungspunkte.
```

- Kein hartes Limit für bestehende Kolonien – Überschreitung möglich,
  aber zu steigenden Kosten (siehe §2).
- Neue Kolonie per Siedlungsschiff: nur solange Gesamtkapazität des
  Planeten nicht erreicht ist.
- Maximal eine Kolonie pro Spieler je Planet.

## 2. Überbebauungskosten

```text
Kostenkomponente 1: planetweiter Malus (Summe aller Spieler)
Kostenkomponente 2: individueller Verursacheranteil (Hauptverursacher
                     überproportional belastet)

Baukostenkurve:      steigt deutlich steiler
Unterhaltskostenkurve: steigt flacher
```

Bei Zerstörung: Bebauungspunkte sofort frei. Bei freiwilligem Abbau:
Bebauungspunkte vollständig frei + **50 % der Basis-Ressourcenkosten**
erstattet (Basiskosten, nicht historische Überbebauungskosten). Kein
Bau/Ausbau/Abriss während aktivem Bodengefecht an der Kolonie.

## 3. Planetare Verteidigungsanlagen

```text
Abschusskapazität:  linear mit Ausbaustufe
Baukosten nächste Stufe = 2 × Baukosten vorherige Stufe   (exponentiell)
Laufender Unterhalt:     sehr leicht exponentiell mit Ausbaustufe
```

Aktivierung/Deaktivierung:

```text
Deaktivieren: sofort, Unterhalt endet sofort, Wirkung = 0
Aktivieren:   manuell, Unterhalt startet sofort, 12 h Anlaufzeit ohne
              Wirkung, Abbruch jederzeit möglich (Unterhalt endet sofort)
```

- Nicht zahlbarer Unterhalt → automatische Deaktivierung, **keine**
  automatische Reaktivierung.
- Ausbau während der 12-h-Anlaufzeit möglich; setzt die Anlaufzeit
  **nicht** zurück – nach Ablauf wird die dann vorhandene Gesamtstufe
  aktiv.
- Design-Hintergrund: Anlaufzeit soll verhindern, dass permanente
  Online-Präsenz einen übermäßigen Vorteil erzeugt (siehe
  `Konzeption/00_Vision_und_Leitprinzipien.md` §7).

## Offene Zahlenfragen

- Exakte lineare Abschusskapazität je Ausbaustufe.
- Exakte „sehr leicht exponentielle" Unterhaltskurve.
- Exakte Baukostenkurve für Infrastruktur/Produktionsanlagen bei
  Überbebauung (Steilheit relativ zur Unterhaltskurve).
