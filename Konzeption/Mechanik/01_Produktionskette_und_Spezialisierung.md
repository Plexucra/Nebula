# Mechanik: Produktionskette und Spezialisierung

*Grundprinzipien: `Konzeption/01_Produktion_und_Arbeitsteilung.md`.
Quelle: `02/01_Produktion_und_Spezialisierung.md`.*

## 1. Hierarchische Produktionsstruktur

Grundformel der Modulhierarchie:

```text
1 Endprodukt → 10 Hauptmodule → je 10 Submodule → je 10 weitere
Submodule → … → Rohstoffe
```

- Faktor 10 pro Hierarchieebene ist der erste Arbeitswert, kein
  endgültiger Balancewert.
- Die Tiefe der Kette ist nicht fest, sondern skaliert mit
  Spielerzahl/wirtschaftlicher Reife der Spielwelt (mehr Spieler → mehr
  Ebenen).
- Eigenproduktion der kompletten Kette ist möglich, aber mit
  wachsendem zeitlichem Nachteil, je näher das Produkt am Endprodukt
  liegt.

## 2. Spezialisierung als Schwellenwertsystem

Kernmechanik (Wortlaut aus dem Ursprungsdokument):

- Eine Spezialisierungsstufe für ein bestimmtes Produkt benötigt einen
  bestimmten **Produktionswert pro Zeiteinheit**, um aufrechterhalten zu
  werden.
- Wird pro Zeiteinheit mehr an diesem Produkt produziert als für die
  aktuelle Stufe nötig, fließen die Mehrpunkte in eine höhere
  Spezialisierungsstufe für genau dieses Produkt.
- Eine höhere Spezialisierungsstufe benötigt wiederum einen höheren
  Produktionswert, um erhalten zu bleiben.

Damit entsteht ein natürliches Maximum an Spezialisierung/Effizienz nur
bei tatsächlicher Konzentration, ohne harte Beschränkung auf ein
Produkt pro Planet.

**Noch offen (keine Werte festgelegt):**

- Genaue mathematische Wachstums-/Zerfallskurve der
  Spezialisierungsstufen.
- Konkrete Effizienzgewinne pro Stufe (Zeit, Output, Ressourceneinsatz).
- Geschwindigkeit, mit der ein Planet die Spezialisierung wechseln kann.

## 3. Arbeitsteilungs-Effizienzgewinn

Qualitativ hergeleitet, aber noch **nicht in eine Formel gegossen**:

Werden die zehn Submodule eines Hauptmoduls von zehn unterschiedlichen,
hoch spezialisierten Produzenten hergestellt (zuzüglich Endmontage), ist
die Produktion des Hauptmoduls – anteilig auf alle Produzenten
gerechnet – deutlich günstiger und schneller, als wenn dasselbe
Hauptmodul zeitgleich von elf Spielern jeweils vollständig eigenständig
hergestellt würde (spezialisiert auf das Hauptmodul, aber mit
unspezialisierter Submodul-Fertigung).

**Todo:** genaue Formel/Funktionsweise dieses Effizienzgewinns technisch
beschreiben.

## 4. Mehrere Kolonien pro Planet – mechanische Konsequenz

Keine harte Grenze auf eine Kolonie pro Planet insgesamt, aber:

- Pro Spieler höchstens **eine Kolonie je Planet** (siehe auch
  `07_Planetare_Bebauung_und_Verteidigung.md` §1).
- Zusätzliche Kolonien anderer Spieler auf demselben Planeten erhöhen
  die Überbevölkerungswahrscheinlichkeit (Malus auf Infrastruktur,
  Lebensqualität, Sicherheit – siehe `11_Planetenwerte_Formeln.md`).

## Offene Zahlenfragen

- Wie genau wachsen und verfallen Spezialisierungsboni bzw. -stufen
  mathematisch?
- Wie schnell kann ein Planet seine Spezialisierung wechseln?
- Wie viele Produktionsstufen sind bei welcher Spielerzahl sinnvoll?
- Genaue technische Formel für den Arbeitsteilungs-Effizienzgewinn (§3).
