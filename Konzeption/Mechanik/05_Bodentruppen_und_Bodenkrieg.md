# Mechanik: Bodentruppen, Rekrutierung und Bodenkrieg

*Grundprinzipien: `Konzeption/03_Militaer_und_Eroberung.md`. Quelle:
`02/10_Planetare_Bebauung_Bodenkrieg_und_Gefechtsaufmarsch.md` §27–35
und `02/11_Kampfmechanik_Blockaden_und_Landungsabwehr.md` §10–21
(neuerer, teilweise widersprechender Stand – hier markiert). Die
generelle Kampf-Kernmechanik steht in `04_Kampfmechanik_Kern.md`.*

## 1. Planetare Bebauung und Überbebauung

- Jeder Planet besitzt eine feste **Bebauungskapazität** aus planetaren
  Eigenschaften und Größe.
- Infrastruktur und Produktionsanlagen verbrauchen **Bebauungspunkte**.
- Für bestehende Kolonien ist die Kapazität **kein hartes Limit**:
  Überschreitung ist möglich, aber zu steigenden Kosten.
- Eine **neue Kolonie** (per Siedlungsschiff) darf nur gegründet werden,
  solange die gesamte Bebauungskapazität des Planeten noch nicht
  erreicht ist. Pro Spieler höchstens eine Kolonie je Planet.

Überbebauungs-Kostenkomponenten:

1. **Planetweiter Malus** aus der gesamten Bebauung aller Spieler.
2. **Individueller Verursacheranteil**, der den Hauptverursacher
   überproportional belastet.

```text
Überbebauung erhöht Baukosten UND Unterhaltskosten.
Baukostenkurve steigt deutlich steiler als Unterhaltskostenkurve.
```

Werden Anlagen zerstört, werden Bebauungspunkte sofort freigegeben.
Freiwilliger Abbau: Bebauungspunkte vollständig frei,
**50 % der Basis-Ressourcenkosten** werden zurückerstattet (Basiskosten,
nicht historisch erhöhte Überbebauungskosten). Während eines aktiven
Bodengefechts an einer Kolonie: kein Bau/Ausbau/Abriss möglich.

## 2. Konfliktschäden bei Eroberung

Zentrale Bezugsgröße:

```text
Zivilverlustquote = verlorene Zivilbevölkerung / Zivilbevölkerung vor dem Kampf
```

Materielle Schäden steigen **überproportional** zur Zivilverlustquote.
Referenzpunkte:

```text
50 % Zivilbevölkerungsverlust → ~80 % Verlust bei Infrastruktur,
                                  Produktionsanlagen, Ressourcenbeständen
50 % Zivilbevölkerungsverlust → ~90 % Verlust bei Verteidigungsanlagen
```

Schadenskurven sind **deterministisch**, kein Zufall (exakte Kurven noch
offen). Zivile Verluste entstehen vor allem in Relation zu den
tatsächlichen militärischen Verlusten der Verteidiger. Überlebende
Anlagen/Ressourcen bleiben nach vollständiger Eroberung erhalten und
werden übernommen (Zusammenführung mit eigener Kolonie, falls
vorhanden, sonst neue Kolonie). Bereits verursachte Schäden bleiben auch
bei späterem Angriffsabbruch bestehen.

## 3. Bodentruppen-Bestandteile

```text
Soldaten
Leichte autonome Waffenträger
Mittlere autonome Waffenträger
Schwere autonome Waffenträger
```

**Wichtige Revision (Dokument 11 überschreibt Dokument 10):**

- Dokument 10: Soldaten können auch ohne Waffenträger kämpfen (im
  Nachteil gegen alle drei Waffenträgerklassen).
- Dokument 11 (aktueller Stand): **Soldaten besitzen keine eigene
  unmittelbare Kampfwirkung.** Ihre einzige Funktion ist, Waffenträger
  zu führen und dadurch kampffähig zu machen.

Drei relevante Bestände (aktueller Stand):

1. **Aktive Waffenträger** – ausreichend mit Soldaten besetzt, kampffähig.
2. **Reserve-Waffenträger** – wegen fehlender Soldaten nicht aktiv.
3. **Reserve-Soldaten** – wegen fehlender Waffenträger nicht gebunden.

Reserve-Bestände nehmen nicht am Kampf teil und können in normalen
Kampfticks keinen Schaden erhalten.

> **Entfällt (Revision):** Die Dokument-10-Regel, dass pro Kampftick
> 50 % der nicht aktivierbaren (inaktiven) Waffenträger zerstört werden,
> gilt nicht mehr.

Die drei Waffenträgerklassen bilden ein geschlossenes Kontersystem
(keine lineare Hierarchie), analog zu den Schiffsklassen (siehe
`03_Schiffsklassen_und_Kontersystem.md` §2 und die generische
Kontermatrix in `04_Kampfmechanik_Kern.md` §4).

## 4. Aktivierung von Waffenträgern

- Verfügbare Soldaten werden **proportional** auf leicht/mittel/schwer
  verteilt.
- Für alle drei Klassen gilt dasselbe Soldaten-zu-Waffenträger-
  Besetzungsverhältnis (genaue Zahl noch offen).
- Treffen während eines laufenden Gefechts zusätzliche Soldaten ein,
  können sie Reserve-Waffenträger aktivieren; treffen zusätzliche
  Waffenträger ein und bestehen Reserve-Soldaten, werden diese
  entsprechend aktiviert.
- Neu aktivierte Waffenträger unterliegen den normalen
  Gefechts-/Aufmarsch-/Expositionsregeln.
- Wird ein aktiver Waffenträger zerstört, gehen die ihm proportional
  zugeordneten Soldaten automatisch mit verloren (keine nachträgliche
  separate Deaktivierungsberechnung).

## 5. Rekrutierung von Soldaten

- Entnimmt Personen aus der Zivilbevölkerung.
- Verbraucht definierte Ausrüstungsgüter.
- Benötigt Produktionszeit (die zentrale begrenzende Größe).
- **Nicht** durch Produktspezialisierung effizienter machbar.
- Nur möglich bei Kolonien mit **> 50 % Loyalität**.

```text
Rekrutierungsgeschwindigkeit bei 100 % Loyalität
  ≈ 10 × Rekrutierungsgeschwindigkeit bei 50 % Loyalität
```

(genaue Kurve dazwischen: Konfigurationswert, noch offen)

Fertige Soldaten werden nicht gelagert/transportiert, sondern
unmittelbar einem vorhandenen Bodentruppenverband der Kolonie
zugeordnet (bei mehreren geeigneten Verbänden: erstbester, keine
Spielerentscheidung; ohne geeigneten Verband: neuer wird automatisch
erzeugt).

## 6. Organisation, Auflösung, Transport

- Bodentruppenverbände: analog zu Raumflotten aufteilbar,
  zusammenlegbar, auflösbar.
- Auflösung nur, wenn der Verband gelandet bei einer eigenen Kolonie
  ist. Waffenträger/Drohnen zurück in den Lagerbestand, Soldaten zurück
  in die Zivilbevölkerung der Kolonie.
- Transport nur mit **Bodentruppentransportern**, gemeinsame Kapazität
  für Soldaten und alle Waffenträgerklassen:

```text
Relativer Platzbedarf:
Leichte Drohne   = 0,05
Soldat           = 1
Mittlere Drohne  = 1
Schwere Drohne   = 20

Verhältnis (bezogen auf leichte Drohne):
leicht : Soldat : mittel : schwer = 1 : 20 : 20 : 400
```

- Nach Dokument 11: **nur eine Transportergröße**; Ladung wird
  proportional über alle Transporter einer Flotte verteilt (keine
  spezialisierten Transporter nach Ladungstyp), sodass die individuelle
  Ladung eines einzelnen Transporters nicht gespeichert werden muss.

## 7. Landung und planetare Bewegung

```text
Landung → Ort „Planetenoberfläche“ → Bewegung zu Kolonie/Kampfgebiet
```

- Jede Bewegung auf der Planetenoberfläche benötigt **genau einen
  Kampftick**, unabhängig von Planetengröße/Entfernung.
- Transporter selbst nehmen nach Landung nicht am Bodengefecht teil,
  können jederzeit wieder starten, sind am Boden kein angreifbares
  Ziel.
- Landungsverluste hängen von planetaren Verteidigungsanlagen und deren
  Abdeckung ab (Detailmechanik siehe §8 „Landungsabwehr").

## 8. Landungsabwehr (separates Ereignis, kein Kampftick)

Ausgelöst einmalig, wenn eine feindliche Flotte mit
Bodentruppentransportern landen will. Betrachtet nur die Transporter
selbst, nicht ihre Ladung.

```text
tatsächliche Abschusskapazität =
    Basis-Abschusskapazität × Zufallsfaktor zwischen 0,5 und 1,0

abgeschossene Transporter = floor(tatsächliche Abschusskapazität)
```

- Kann nie höher sein als Zahl der landenden Transporter.
- Nicht genutzte Kapazität verfällt für diese Landung.
- Wird **für jede landende feindliche Flotte separat** neu berechnet
  (keine gemeinsam verbrauchte Kapazität pro Zeitraum) → Anreiz für den
  Angreifer, gebündelt mit einer großen Transportflotte zu landen.

Bei mehreren feindlichen Kolonien auf demselben Planeten: Jede Kolonie
im Kriegszustand mit dem Landenden und mit aktiver Verteidigungsanlage
führt ihre eigene Landungsabwehr separat gegen die jeweils noch
vorhandenen Transporter aus; Reihenfolge spielerisch irrelevant.

**Ladungsverluste** (proportional aus Transporterverlust, aufgerundet):

```text
Verlust einer Ladungskategorie =
    ceil(vorhandene Menge × verlorene Transporter / gesamte Transporter)
```

Bei Totalverlust aller Transporter geht die gesamte Ladung verloren.
Überlebende Transporter besitzen keinen gespeicherten Schadenszustand
(entweder abgeschossen oder vollständig gelandet) und können sofort
wieder starten.

## 9. Ausbau und Aktivierung planetarer Verteidigung

```text
Abschusskapazität steigt LINEAR mit Ausbaustufe.
Kosten nächste Stufe = 2 × Kosten vorherige Stufe   (stark exponentiell)
Laufender Unterhalt: sehr leicht exponentiell mit Ausbaustufe.
```

Aktivierungszustand:

- **Deaktivieren:** sofort, Unterhalt endet sofort, Wirkung sofort null.
  Nicht zahlbarer Unterhalt löst automatische Deaktivierung aus; keine
  automatische Reaktivierung danach.
- **Aktivieren:** manuell, startet sofort vollen Unterhalt, benötigt
  **12 Stunden Anlaufzeit** ohne Wirkung während dieser Zeit. Abbruch
  jederzeit möglich (Unterhalt endet sofort). Ausbau während der
  Anlaufzeit möglich; eine währenddessen fertiggestellte Zusatzstufe
  setzt die Anlaufzeit **nicht** zurück – nach Ablauf der ursprünglichen
  12 Stunden wird die dann vorhandene Gesamtstufe aktiv.

Planetare Verteidigungsanlagen sind **keine Einheitengruppe** des
regulären Bodenkampfs, greifen nur bei der Landung ein, werden im
späteren Bodenkampf nicht direkt beschossen, können aber über die
allgemeine Konfliktschadensberechnung (§2) beschädigt/zerstört werden.

## 10. Niederlage im Bodenkampf

- Verteidigung gilt als besiegt, sobald keine aktivierbaren
  Waffenträger mehr vorhanden sind.
- Reserve-Bestände ohne passendes Gegenstück halten ein Gefecht nicht
  künstlich offen.
- Bei vollständiger Niederlage gehen alle verbleibenden
  Reserve-Soldaten/-Waffenträger verloren – **nicht** erbeutet.
- Eine Kolonie mit nur Soldaten und ohne aktivierbare Waffenträger hat
  keine wirksame Bodenverteidigung.

## 11. Rückzug im Bodenkampf

- Angreifer und unterstützende Verteidiger dürfen sich zurückziehen.
- Der **Eigentümer** der angegriffenen Kolonie kann sich aus der
  Verteidigung seiner eigenen Kolonie **nicht** zurückziehen.
- Erfolgreicher Rückzug führt die Bodenflotte zurück zu
  „Planetenoberfläche".
- Reserve-Bestände sind beim einseitigen Rückzugstick nicht angreifbar
  – nur aktive, exponierte Waffenträger.
- Mehrere Angreifer können separat den Rückzug erklären; das Gefecht
  läuft für übrige Beteiligte weiter.

## 12. Kolonieverhalten während Bodengefecht

- Produktion läuft normal weiter (kein pauschaler Kriegsmalus, nur
  reguläre Effekte aus Versorgung/Sicherheit/zerstörter Kapazität).
- Neue Soldaten/Waffenträger können fertiggestellt und in späteren
  Ticks eingesetzt werden.
- Keine neuen Handelsaktionen des Spielers an der betroffenen Kolonie;
  bereits bestehende Orders bleiben aktiv (Ware/Geld bereits gebunden).
- Bevölkerung darf weiterhin am planetaren Handelsdepot und an der
  System-Handelsstation kaufen.

## Offene Zahlenfragen

- Genaues Soldaten-zu-Waffenträger-Besetzungsverhältnis.
- Genaue Rekrutierungskurve zwischen 50 % und 100 % Loyalität.
- Exakte Schadenskurven für Infrastruktur/Produktion/Ressourcen/
  Verteidigungsanlagen zwischen den Referenzpunkten aus §2.
- Genaue lineare Abschusskapazität je Verteidigungsausbaustufe.
- Exakte, „sehr leicht exponentielle" Unterhaltskurve der Verteidigung.
