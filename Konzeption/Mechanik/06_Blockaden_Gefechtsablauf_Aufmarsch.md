# Mechanik: Blockaden, Gefechtsbeitritt, Aufmarsch und Exposition

*Grundprinzipien: `Konzeption/03_Militaer_und_Eroberung.md` §3, §9.
Quelle: `02/10_Planetare_Bebauung_Bodenkrieg_und_Gefechtsaufmarsch.md`
§36–42 und `02/11_Kampfmechanik_Blockaden_und_Landungsabwehr.md`
§22–30 (neuerer Stand für Blockadedetails). Kampfschadensberechnung
selbst: `04_Kampfmechanik_Kern.md`.*

## 1. Kriegserklärung und Frieden

```text
Kriegserklärung → sofortige Angriffsmöglichkeit, keine Vorwarnzeit.
Frieden frühestens 24 Stunden nach Kriegserklärung.
Frieden erfordert Zustimmung BEIDER Parteien.
Kein Frieden, solange ein aktives Gefecht zwischen den Parteien läuft.
Nach Friedensschluss: 24-stündige Sperrfrist vor erneuter Kriegserklärung.
```

Ein Gefecht gilt als beendet, sobald die Parteien nicht mehr aktiv
gegeneinander kämpfen; gegnerische Bodentruppen dürfen danach
weiterhin gleichzeitig auf demselben Planeten existieren.

## 2. Mehrparteiengefechte

- Beitritt zu einem laufenden Gefecht nur, wenn Krieg mit **allen**
  Parteien der gegnerischen Seite besteht.
- Kein selektiver Angriff auf nur einen Teil der Gegenseite.
- Mehrere Parteien können gemeinsam auf derselben Seite kämpfen.

## 3. Kampfticks

```text
Erster Arbeitswert: 8 Stunden pro Kampftick (Konfigurationswert).
Kein Maximum an Kampfticks pro Gefecht.
```

Verstärkungen während eines laufenden Gefechts unterliegen denselben
allgemeinen Eintrittsregeln wie Erstteilnehmer.

## 4. Rückzug

```text
Rückzugsentscheidung → zusätzlicher Kampftick gegnerischen Schadens
                        für die zurückziehende Seite (ohne eigenen
                        Schaden in diesem Tick).
Gilt auch während der Aufmarschphase.
Nur bereits exponierte Einheiten sind in diesem letzten Tick angreifbar.
```

## 5. Aufmarschphase

Eigenschaft des **Gefechts**, nicht einzelner Verbände:

```text
Kampftick 1: max. 25 % Aufmarsch
Kampftick 2: max. 50 % Aufmarsch
ab Kampftick 3: 100 % Aufmarsch
```

- Werte sind Obergrenzen, kein Zwang zur vollen Ausschöpfung.
- Ein während Tick 1/2 eintretender Verband übernimmt die aktuelle
  Aufmarschstufe des gesamten Gefechts.
- Ein ab Tick 3 eintretender Verband hat **keinen eigenen** dreitägigen
  Aufmarsch – er kann sofort vollständig eingesetzt werden (soweit
  Expositionslimit dies zulässt).

## 6. Expositionslimit

Zusätzlich zur Aufmarschgrenze, in **jedem** Kampftick:

```text
maximale Exposition = 10 × exponierter Produktionsaufwand
                            der gesamten gegnerischen Seite

Produktionsaufwand = Basiszeit × Arbeitskräfte
```

- Gilt unabhängig davon, ob sich das Gefecht noch in der
  Aufmarschphase befindet.
- Während der ersten drei Ticks gelten **beide** Grenzen gleichzeitig
  (Minimum aus Prozentgrenze und 10×-Limit).
- Wird **für jede beteiligte Partei separat** angewendet – kämpfen
  mehrere Parteien gemeinsam gegen denselben Gegner, kann jede bis zum
  Zehnfachen der gesamten exponierten Gegenseite exponieren (die
  Gegenseite in Summe kann so von mehreren Parteien zusammen mehr
  offenlegen als eine einzelne Partei).
- Bereits exponierte Einheiten bleiben exponiert, bis zerstört oder
  Gefechtsaustritt – ein neu berechnetes Limit macht sie nicht wieder
  unsichtbar.

## 7. Auswahl der exponierten Einheiten

- Exposition erfolgt **proportional über die vorhandenen
  Einheitentypen**, verzerrt zugunsten kleinerer Einheiten.
- Die Bevorzugung leitet sich direkt aus dem **Produktionsaufwand** ab:
  geringerer Aufwand → tendenziell früherer Aufmarsch. Kein separater
  Mobilitäts-/Aufmarschwert.

## 8. Sichtbarkeit und Kampfteilnahme

- Nur **exponierte** Einheiten nehmen am Gefecht teil (verursachen
  Schaden, erhalten Schaden, sind für den Gegner sichtbar).
- Nicht exponierte Einheiten: kein Schaden, keine Verwundbarkeit, keine
  vollständige Sichtbarkeit.
- Funktion: gleichzeitig Aufmarsch- **und** Aufklärungsmechanik. Ein
  einzelner billiger Späher deckt bei riesiger gegnerischer Übermacht
  wegen des 10×-Limits nur einen kleinen Teil davon auf.

## 9. Blockaden als Kampfanker

```text
Jedes reguläre Flottengefecht ist an eine Blockade gebunden.
Pro Blockadestelle existiert maximal EINE Blockade.
Beliebig viele Flotten/Parteien können der blockierenden Seite beitreten.
```

Räumliche Hierarchie (bewusst stark abstrahiert):

```text
Gateway
→ Blockadestelle
→ System
→ Orbit
→ Blockadestelle
→ Planetenoberfläche
→ Blockadestelle
→ Kolonie
```

- Kein zusätzlicher Ort „Atmosphäre/Landungsweg" – die planetare
  Landungsabwehr ist nur ein Ereignis beim Übergang Orbit →
  Planetenoberfläche (siehe `05_Bodentruppen_und_Bodenkrieg.md` §8).
- Eine Blockade kontrolliert die **Verbindung**, nicht den Zielort
  selbst; wirkt immer **beidseitig**.
- Bodenblockade kontrolliert konkret `Planetenoberfläche ↔ Kolonie` –
  nicht die gesamte Planetenoberfläche. Andere Bodenflotten können
  weiterhin frei auf der Oberfläche existieren/andere Kolonien
  ansteuern.

## 10. Beitritt zu einer Blockade

- Eine Flotte am Blockadeort kann ohne Zeitverlust beitreten/verlassen,
  solange kein laufendes Gefecht sie bindet.
- Beitritt zur blockierenden Seite nur, wenn **kein Krieg** mit einer
  bereits blockierenden Partei besteht.
- Solange zwei Parteien gemeinsam dieselbe Blockade halten, können sie
  einander **keinen Krieg erklären** (wird mit Fehlermeldung
  abgelehnt).
- Neutrale/freundliche Parteien passieren normal.

## 11. Entstehung eines Blockadegefechts

Beim Versuch der Durchquerung durch eine verfeindete Flotte:

```text
Krieg mit ALLEN Parteien einer Gefechtsseite → automatischer Eintritt
                                                 auf der Gegenseite
Krieg mit NUR EINEM TEIL einer Seite          → Passage verweigert,
                                                 aber kein automatischer
                                                 Gefechtseintritt
Keine relevante Feindschaft                    → Passage erlaubt
```

Im mittleren Fall bleibt die Flotte am strategischen Ort auf ihrer
Seite der Verbindung stehen.

## 12. Expliziter Gefechtsbeitritt

Zusätzlicher Befehl **„Gefechtsbeitritt"**, ohne die Verbindung passieren
zu wollen:

- Seite wird ausschließlich aus Kriegsbeziehungen bestimmt, nicht
  manuell wählbar.
- Nur möglich bei eindeutiger Zuordnung: Krieg mit allen Parteien einer
  Seite, kein Krieg mit der anderen Seite.
- Ein Beitritt zur blockierenden Seite macht die Partei automatisch
  selbst Teil der Blockade (Blockade verschwindet dadurch nicht mit dem
  Verlust der ursprünglich blockierenden Flotte, solange Unterstützer
  weiterkämpfen).

## 13. Bewegung und Gefechtsaustritt

- Wird eine Flotte beim Passageversuch in ein Gefecht gezogen, endet
  ihr ursprünglicher Bewegungsbefehl endgültig (kein automatisches
  Fortsetzen nach Rückzug/Gefechtsende).
- Nach Rückzug: Rückkehr immer auf die Seite der Blockade, von der aus
  das Gefecht betreten wurde – Rückzug bedeutet ausdrücklich **nicht**
  Passage.

## 14. Bestand und Ende einer Blockade

- Kann während eines laufenden Gefechts an ihr **nicht** aufgehoben
  werden.
- Einzelne Flotte kann während des Gefechts nicht per einfachem
  Zustandswechsel austreten – nur über regulären Rückzug (inkl.
  einseitigem Rückzugstick, siehe §4).
- Erfolgreicher Rückzug = automatischer Austritt aus der Blockade.
- Blockade bleibt bestehen, solange mindestens eine blockierende
  Einheit vorhanden ist – auch nach gewonnenem Gefecht („durchbrochen"
  ist keine Spielbegriff; Auflösung nur durch Vernichtung/Verlassen
  aller Blockierer).
- Ohne laufendes Gefecht: letzte blockierende Flotte kann ohne
  Zeitverlust verlassen → Blockade verschwindet sofort.
- Blockaden dürfen auch ohne aktuellen Krieg dauerhaft bestehen.

## 15. Verstärkung einer Blockade

Weitere Flotten können auch während eines laufenden Gefechts beitreten
(sofern Kriegsbeziehungen es erlauben), werden ab dem nächsten
Kampftick Teil des Gefechts und übernehmen die bestehende
Aufmarschstufe (keine eigene neue Aufmarschphase, da Aufmarsch
Gefechtseigenschaft ist).

## Offene Zahlenfragen

- Was geschieht bei technisch exakt gleichem Status in Randfällen
  (z. B. gleichzeitig eintreffende Flotten, exakt gleiches politisches
  Gewicht – siehe auch `08_Gateway_und_Zollmechanik.md`)?
- Zielauswahl-Detailfragen innerhalb der Exposition, sofern noch nicht
  vollständig durch §7 abgedeckt.
