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

**Umsetzungsstand im Prototyp** (`engine/formulas.ts`,
`SimulatedGameApiService.registerProduced`/`decaySpecializations`):

- XP-Gewinn = tatsächlich verbrauchte Produktionszeit (Spielstunden),
  nicht Stückzahl – wer ein Produkt exklusiv fertigt, sammelt XP
  proportional zur dafür aufgewendeten Zeit.
- Lineare Stufen-Schwelle (`specializationThresholdHours`), kalibriert
  auf **eine Spielwoche** (168 Spielstunden) ununterbrochener
  Exklusivproduktion = Stufe 10 = **+100 % Tempo**
  (`specializationSpeedFactor`, +10 %/Stufe). Bis Stufe 50 (+500 %)
  entsprechend länger (~23 Spielwochen kumuliert, da jede weitere Stufe
  selbst mehr kostet) – siehe Rechnung im Code-Kommentar dort.
- Verfall bei Inaktivität: eigene Kürzungsregel in
  `decaySpecializations` (siehe dort für Details).
- Industriekomplex/Werft/Ausbildungszentrum: linearer Tempo-Faktor zur
  Gebäudestufe (Stufe 2 = doppelt so schnell wie Stufe 1, Stufe 10 =
  zehnmal so schnell), siehe `buildingLevelSpeedFactor`.
- Arbeitsteilungs-Effizienzgewinn aus §3 bleibt weiterhin offen (keine
  gesonderte Formel jenseits der Spezialisierungsstufe selbst
  implementiert).

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

- Wachstum ist umgesetzt (siehe §2, „Umsetzungsstand im Prototyp“);
  Verfall bei Inaktivität ebenfalls (`decaySpecializations`,
  `SPECIALIZATION_DECAY_GRACE_MS`), aber ohne gesonderte Balancing-Prüfung.
- Wie viele Produktionsstufen sind bei welcher Spielerzahl sinnvoll?
- Genaue technische Formel für den Arbeitsteilungs-Effizienzgewinn (§3).
