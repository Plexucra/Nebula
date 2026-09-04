# 10. Sequentielle Produktionsaufträge und Ereignissystem

## 1. Ausgangslage und Motivation

Die ursprüngliche Dauerproduktion (siehe Historie in `02_Produktion_Rohstoffe_und_Lager.md`)
erlaubte beliebig viele gleichzeitig aktive Aufträge pro Kolonie, jeweils mit
eigenem Timer, plus eine automatische Kaskade, die für fehlende Rezept-Eingänge
rekursiv weitere Hilfsaufträge anlegte ("Abhängigkeiten automatisch
mitproduzieren"). In der Praxis führte das bei tiefen Produktketten
(sieben Ebenen, siehe `Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md`)
zu 150–250 gleichzeitig aktiven Aufträgen pro Kolonie, die jeden Tick einzeln
durchlaufen wurden – ein O(Aufträge)-Aufwand pro Tick, der mit wachsender
Kettentiefe und mehreren Kolonien nicht mehr sinnvoll skaliert.

Dieses Dokument beschreibt die Ablösung durch ein **sequentielles**
Warteschlangenmodell: pro Kolonie und Produktionsstätte (Industriekomplex,
Werft, Ausbildungszentrum) ist zu jedem Zeitpunkt **höchstens ein** Auftrag
aktiv. Die gesamte Vorprodukt-Kette eines Auftrags wird **einmalig im Voraus**
berechnet (Zeit- und Rohstoffbedarf aufsummiert) statt als Baum aus vielen
Einzelaufträgen abgebildet. Der Aufwand pro Tick sinkt dadurch auf
O(Kolonien × Produktionsstätten) – bei aktuell maximal 11 Kolonien × 3
Warteschlangen also konstant klein, unabhängig von der Kettentiefe.

## 2. ChainPlan: Vorausberechnung der gesamten Kette

Beim Anlegen eines Auftrags (Produkt, Stückzahl, Checkbox "Nicht vorhandene
Vorprodukte automatisch mitproduzieren") wird der komplette Rezeptbaum einmalig
aufgelöst:

- Für die Wurzel (angeforderte Stückzahl) und jede Rezeptzutat wird der
  Gesamtbedarf berechnet.
- Davon wird abgezogen, was **aktuell** im Kolonielager vorhanden ist – dieser
  Anteil wird nicht erneut produziert, sondern direkt verbraucht.
- Der Rest wird rekursiv in die Zutaten des jeweiligen Zwischenprodukts
  aufgelöst, bis nur noch Rohstoffe (leeres Rezept) übrig sind.
- Für jede tatsächlich zu produzierende Menge wird die Produktionszeit mit den
  zum Berechnungszeitpunkt gültigen Geschwindigkeitsfaktoren der Kolonie
  (Bevölkerung, Gebäudestufe, Spezialisierung, Fördergüte, Blackout)
  aufsummiert.

Das Ergebnis ist ein `ChainPlan`: eine Gesamtzeit (`totalHours`) und eine
Liste von Schritten (`steps`, vom Rohstoff bis zur Wurzel), die für die
aufklappbare Detailansicht im UI verwendet wird. **Nur die Gesamtsumme zählt
für die Ausführung** – intern entsteht dadurch ein einziger Warteschlangen-
Eintrag statt vieler.

Bewusste Vereinfachung: Die Geschwindigkeitsfaktoren werden einmalig zum
Berechnungszeitpunkt fixiert und während der Laufzeit des Auftrags nicht neu
bewertet (z. B. wenn die Bevölkerung währenddessen wächst). Das hält die
Berechnung einfach und nachvollziehbar; eine Neuberechnung würde die
Ereignisbasierung (feste `endsAt`-Zeitpunkte, siehe unten) unterlaufen.

## 3. Sequentielle Ausführung

Jede Kolonie hat pro Warteschlange (Produktion/Werft/Rekrutierung) eine
FIFO-Liste. Nur der erste Eintrag ist aktiv:

1. Auftrag anlegen → `ChainPlan` berechnen.
2. Ist die Warteschlange gerade leer/unbeschäftigt, startet der Auftrag
   sofort: die aggregiert zu produzierenden Rohstoffmengen werden aus dem
   Lager abgezogen, `startedAt`/`endsAt` gesetzt.
3. Reicht der Lagerbestand an Rohstoffen nicht und ist "automatisch
   mitproduzieren" **nicht** gesetzt, wird die Warteschlange **angehalten**
   (`stopped`) und eine Benachrichtigung erzeugt (siehe Abschnitt 5). Der
   Spieler muss manuell fortsetzen (Button "Fortsetzen"), sobald genug
   Vorprodukte vorhanden sind.
4. Ist "automatisch mitproduzieren" gesetzt, ist der Auftrag grundsätzlich
   immer ausführbar (jeder Rohstoff ist zu jeder Fördergüte > 0 gewinnbar,
   siehe `resourceConcentrationFactor`), auch wenn er dadurch sehr lange
   dauern kann.

Sobald `endsAt` erreicht ist (Prüfung: ein einziger Zeitvergleich pro Kolonie
und Warteschlange, kein Durchlaufen einzelner Produktionsschritte mehr), wird
der Auftrag abgeschlossen: die Zielmenge wandert ins Lager, der nächste
wartende Eintrag startet. Ist "nach Erfolg erneut einreihen" gesetzt, wird
derselbe Auftrag (Produkt, Stückzahl, Checkboxen) ans Ende der Warteschlange
neu angehängt statt entfernt – der `ChainPlan` wird dabei neu berechnet, da
sich der Lagerbestand zwischenzeitlich geändert haben kann.

## 4. Abbruch mit anteiliger Gutschrift

Wird ein laufender Auftrag abgebrochen, bestimmt der Anteil der bereits
verstrichenen Zeit an der Gesamtzeit (`elapsedFraction`), wie viel als
tatsächlich produziert gilt – pro Schritt der Kette einzeln, nicht als grobe
Pauschale auf das Endprodukt:

```
producedQty(step) = floor(step.quantityToProduce × elapsedFraction)
```

Die Abrundung ist bewusst: Ein einzelnes, großes Modul, das erst zu 90 %
fertig ist, zählt als **nicht** fertig (`floor(1 × 0.9) = 0`), nicht als 0,9
Module. Nur bei mehreren benötigten Einheiten eines Schritts (z. B. 10
Rohstoffeinheiten) ergibt sich eine sinnvolle Teilmenge (`floor(10 × 0.9) =
9`). Die so ermittelten Teilmengen werden dem Lager gutgeschrieben, nicht
verbrauchte Rohstoffanteile werden zurückerstattet. Ein abgebrochener Auftrag
wird nicht erneut eingereiht, selbst wenn "nach Erfolg erneut einreihen"
gesetzt war.

## 5. Benachrichtigungssystem

Neues, von der Produktion unabhängiges Modul: Benachrichtigungen vom Typ
**Info**, **Warnung** oder **Problem**, jeweils mit eindeutigem Nummerncode
(grobe Konvention: 1xx Info, 4xx Warnung, 5xx Problem), Freitext und
optionalem Kolonie-Bezug. Aktuell genutzt:

- **503 – Problem**: Auftragswarteschlange einer Kolonie musste mangels
  Vorprodukten angehalten werden (siehe Abschnitt 3, Schritt 3).

Vorbereitet, aber noch nicht produktiv ausgelöst (keine Kampf-/
Flottenbewegungslogik vorhanden): **4xx – Warnung**, z. B. "feindliche Flotte
im System gesichtet". Der Code-Bereich ist reserviert, die Auslösung folgt
mit der entsprechenden Spielmechanik.

## 6. Verkaufsorders: vom Lager statt von der Produktion

Verkaufsorders werden nicht mehr über die Produktion konfiguriert, sondern
direkt aus der Lagerbestand-Ansicht heraus ("Anbieten"-Button je Warenposten,
mit Mengen- und Preiseingabe). Eine so erzeugte Order trägt `autoRelist:
true`: Sobald sie durch einen Kauf (Spieler, NPC oder Bevölkerungskonsum)
vollständig verkauft ist (`remainingQuantity` erreicht 0), wird **im selben
Vorgang** – nicht erst beim nächsten Tick – eine neue Order mit identischer
Menge und Preis angelegt. Das ist bereits heute die Natur des Kaufvorgangs
(reagiert nur bei tatsächlichem Kauf, nicht periodisch) und wird hier
konsequent bis zum Neu-Listing durchgezogen.

Der bisherige Mechanismus, der Verkaufsorders an einen Dauerproduktionsauftrag
koppelte (Kappung bei 50 Einheiten, automatische Nachbefüllung aus dem
Produktions-Ziel-Lagerbestand), entfällt vollständig. Die Startkolonie legt
beim Spielbeginn keine automatischen Verkaufsorders für die Grundgüter mehr
an – das ist eine bewusste Verhaltensänderung; der Spieler richtet den
lokalen Verkauf jetzt selbst über "Anbieten" ein.

## 7. Ereignisbasierung: Umfang und Abgrenzung

Ereignisbasiert umgestellt sind:

- Die neue Produktions-/Werft-/Rekrutierungswarteschlange (feste `endsAt`-
  Zeitpunkte statt Pro-Einheit-Schleifen, siehe Abschnitt 3).
- Das Neu-Einstellen von Verkaufsorders (siehe Abschnitt 6).

Bewusst **nicht** umgestellt, weiterhin auf dem bestehenden periodischen Tick
(`runTick`, siehe `00_Architektur_und_Datenmodell_Uebersicht.md`):
Bevölkerungswachstum, Löhne/Gebäudeunterhalt, Bevölkerungskonsum,
NPC-KI-Taktung, Statistik-Snapshots, Energienetz/Blackout. Diese Systeme
laufen mit maximal 11 Kolonien pro Tick (O(Kolonien)) und waren nie der
identifizierte Engpass – eine vollständige Ereignisbasierung dieser Systeme
wäre ein eigenständiges, deutlich größeres Vorhaben ohne erkennbaren Nutzen
für das eigentliche Anliegen (die Produktionsauftragsliste) und wird hier
bewusst nicht verfolgt.

## 8. NPC-KI

Die bisherige rekursive Hilfsfunktion, die pro KI-Tick nur eine Ebene der
Kette nachlegte, entfällt: NPCs stellen jetzt – wie Spieler – einen einzigen
Auftrag mit "automatisch mitproduzieren" und "nach Erfolg erneut einreihen"
für ihr Zielprodukt (Grundnahrung bzw. Spezialisierungsprodukt). Der neue
`ChainPlan`-Mechanismus übernimmt die komplette Kette in einer Berechnung,
wodurch die bisherige schichtweise Eigenlogik der NPC-KI überflüssig wird.
