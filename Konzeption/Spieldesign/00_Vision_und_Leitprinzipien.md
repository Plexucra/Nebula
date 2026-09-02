# Vision und Leitprinzipien

*Quelle: `02/00_Einfuehrung.md`. Diese Datei ist die oberste Ebene der
gesamten Konzeption – alle anderen Konzeptionsdateien konkretisieren
eines der hier genannten Prinzipien für ein einzelnes Themenfeld.
Konkrete Mechaniken, Zahlen und Formeln stehen nicht hier, sondern im
Ordner `Mechanik/`. Die konkrete Software-Umsetzung steht im Ordner
`Umsetzungskonzept/`.*

## 1. Vision

*Nebula* ist ein persistentes Online-Strategiespiel für Browser
(perspektivisch auch mobile Clients), dessen zentrales Element eine von
Spielern getragene, dynamisch skalierende Wirtschaft ist. Zielgröße sind
Hunderte bis potenziell Millionen Spieler.

Das Spiel soll nicht darauf beruhen, dass jeder Spieler wirtschaftlich
autark wird. Stattdessen erzeugt das System Spezialisierung, Handel,
Kooperation, regionale Abhängigkeiten und strategische Konflikte.

Grundidee:

> Je größer die Spielerschaft und je stärker die wirtschaftliche
> Spezialisierung, desto tiefer kann sich die Produktionskette
> auffächern.

Jeder Spieler soll eine wirtschaftliche Nische finden können, in der er
für andere Spieler, Gruppen oder politische Parteien einen messbaren
ökonomischen Wert erzeugt – auch als hoch spezialisierter Produzent
eines scheinbar kleinen Bauteils.

## 2. Leitprinzipien

Diese neun Prinzipien gelten als Kern des gesamten Konzepts und sollten
jede spätere Detailentscheidung leiten:

1. Niemand soll vollständig autark sein müssen oder langfristig optimal
   autark sein können.
2. Spezialisierung muss wirtschaftlich attraktiver sein als vollständige
   Eigenproduktion.
3. Arbeitsteilung muss mit wachsender Spielerzahl zunehmend wertvoll
   werden.
4. Produktionsketten müssen mit der Größe der Spielwelt skalieren
   können.
5. Regionale Ressourcenunterschiede müssen groß genug sein, um echten
   Handel zu erzeugen.
6. Ökonomische Spezialisierung soll strategische Verwundbarkeit
   erzeugen.
7. Kein einzelner Schiffstyp darf eine universell optimale militärische
   Lösung darstellen.
8. Kleine wirtschaftliche Nischen müssen für das Gesamtsystem relevant
   bleiben können.
9. Wirtschaft, Geografie, Diplomatie und Krieg sollen keine getrennten
   Systeme sein, sondern sich gegenseitig beeinflussen.

## 3. Die zentrale Designschleife

Die Grundmechanik von Nebula lässt sich als eine einzige Kette
zusammenfassen, die sich durch alle Themenfelder zieht:

**Ungleich verteilte Ressourcen**
→ **regionale Produktionsvorteile**
→ **Spezialisierung auf bestimmte Produkte und Schiffstypen**
→ **höhere wirtschaftliche Effizienz**
→ **größere Abhängigkeit von Handel und anderen Produzenten**
→ **militärische Verwundbarkeit durch das Kontersystem**
→ **Kooperation, Diplomatie, Handel oder Krieg**
→ **Veränderung von Lieferketten und Spezialisierungen**

Die Wirtschaft ist damit kein bloßes Versorgungssystem für das Militär,
sondern selbst zentraler Teil des strategischen Gameplays.

## 4. Meta-Progression: vom Aufbauspiel zum Gesellschaftsspiel

Der Charakter des Spiels soll sich mit der Entwicklung des Spielers
verändern:

**Aufbauspiel → Wirtschaftsspiel → Gesellschaftsspiel →
Diplomatie-, Konflikt- und Intrigenspiel**

Zu Beginn steht der eigene Planet im Mittelpunkt: Aufbau, Optimierung,
unmittelbar nachvollziehbarer Fortschritt. Mit wachsender
wirtschaftlicher Entwicklung wird weiteres reines Wachstum zunehmend
weniger sinnvoll – nicht durch eine künstliche Stufenbegrenzung,
sondern weil die Struktur der Wirtschaft andere Spieler zunehmend
wichtig macht.

Der Übergang soll organisch erfolgen. Das Spiel soll nicht ab einer
bestimmten Stufe verlangen, einer Allianz beizutreten. Der Spieler
entdeckt selbst, dass Spezialisierung, Handel, Transport, Verteidigung
und Zugriff auf entfernte Ressourcen durch Kooperation erheblich
effizienter werden. Im späteren Spiel werden zunehmend Beziehungen
zwischen Spielern – nicht weitere Gebäudestufen – zum Gegenstand des
Spiels: Vertrauen, Abhängigkeiten, Handelsinteressen, territoriale
Interessen, Konflikte und politische Entscheidungen.

Konkret durchläuft der Spieler dabei zwei Motivationsphasen:

Zunächst fragt er:

> Wie entwickle ich meine Welt?

Später fragt er:

> Mit wem handle ich? Wem vertraue ich? Wer kontrolliert meine Route?
> Wen lasse ich in mein System? Welche Region braucht meine Produkte?
> Welche politische Entwicklung bedroht meine Wirtschaft?

Der soziale Teil des Spiels wird dadurch nicht künstlich aktiviert,
sondern entdeckt (konkret ausgearbeitet in
`08_Neue_Spieler_und_Einstieg.md`).

## 5. Drei Meta-Prinzipien: Spezialisierung, Expansion, Konzentration

Die positive Rückkopplung der Spezialisierung ist ausdrücklich gewollt:
Ein Planet, der lange dasselbe Produkt herstellt, baut einen
erheblichen Effizienzvorteil auf (konkret in
`01_Produktion_und_Arbeitsteilung.md` und `Mechanik/01_Produktionskette_und_Spezialisierung.md`).
Dieser Vorteil darf aber nicht dazu führen, dass ein erfolgreicher
Spieler zwangsläufig sämtliche Produktionszweige monopolisiert.
Expansion soll nicht durch einen abstrakten Prozentmalus begrenzt
werden, sondern durch konkrete Sicherheits-, Verwaltungs-,
Versorgungs- und Kontrollaufgaben, die mit jedem weiteren Planeten
wachsen (konkret in `03_Militaer_und_Eroberung.md` und
`07_Planeten_und_Bevoelkerung.md`).

Daraus ergeben sich drei Meta-Prinzipien, die als Denkrahmen für alle
Balancing-Entscheidungen gelten:

> **Spezialisierung erzeugt zunehmende Effizienz.**
> **Expansion erzeugt zunehmende Komplexität.**
> **Konzentration erzeugt zunehmende Verwundbarkeit.**

## 6. Politische Designphilosophie: keine erzwungene Allianzmechanik

Für die politische Organisation der Spieler wird **kein klassisches
Allianzsystem als notwendige Kernmechanik** vorausgesetzt. Spieler
können sich außerhalb der vom Spiel vorgegebenen Strukturen frei
organisieren (Namen, Kommunikation, Absprachen, politische Blöcke),
ohne dass das Spiel diese Gruppen formal kennen muss.

Ziel ist nicht: „Spieler treten einer Allianz bei und erhalten deshalb
Vorteile."
Ziel ist: „Spieler haben aufgrund ihrer geografischen und
wirtschaftlichen Situation gemeinsame Interessen und kooperieren
deshalb."

Das wirkt der Entstehung vollständig homogener Mega-Allianzen entgegen:
Auch ein außerhalb des Spiels organisierter großer Machtblock besitzt
keine automatische wirtschaftliche Homogenität, weil seine Mitglieder
unterschiedliche geografische Positionen und damit unterschiedliche
lokale Interessen haben. Die Begrenzung von Mega-Allianzen erfolgt somit
nicht durch eine maximale Mitgliederzahl, sondern dadurch, dass
politische und wirtschaftliche Interessen lokal bleiben und sich mit der
geografischen Position verändern.

Die kleinste formalisierte politische Beziehung ist die **bilaterale
Vereinbarung zwischen zwei Spielern**. Größere politische Gebilde
entstehen aus vielen solchen Beziehungen sowie informeller Koordination
außerhalb des Spiels. Entscheidend sind die tatsächlichen Beziehungen:
Wer gewährt wem Durchfahrt? Wer erhebt welchen Zoll? Wer benötigt wessen
Handelsroute? Wer schützt welches Gateway?

> Nebula soll soziale und politische Strukturen möglichst nicht durch
> abstrakte Allianzmechaniken vorschreiben. Wirtschaft,
> Ressourcenverteilung, Entfernung, Gateways, bilaterale Zölle und
> militärische Verwundbarkeit sollen stattdessen Situationen erzeugen,
> in denen Kooperation zwischen bestimmten Spielern rational wird und
> zwischen anderen Spielern Interessenkonflikte entstehen.

Die konkrete Mechanik, die diese Philosophie trägt (bilaterale
Gateway-Zölle, lokale Interessengemeinschaften, Grenzspannungen), steht
in `04_Gateways_und_politische_Ordnung.md`.

## 7. Übergreifende Designmethodik

Aus der Arbeit an den Ursprungsdokumenten hat sich eine gemeinsame
Leitlinie herauskristallisiert, die für alle künftigen Detailregeln
gelten soll:

> Wo möglich, soll ein Schutz oder eine Begrenzung nicht durch eine
> harte Spielregel (Unverwundbarkeit, künstliche Sperre, künstlicher
> Timer) entstehen, sondern durch die wirtschaftlichen und
> militärischen Konsequenzen, die eine Handlung für den Angreifer bzw.
> Kontrolleur hat.

Beispiele für diese Linie: Der Schutz neuer Spieler entsteht aus
Unauffindbarkeit und lokalem Bevölkerungsübergewicht statt aus einem
Unverwundbarkeits-Timer (`08_Neue_Spieler_und_Einstieg.md`). Der
Schutz von Heimatplaneten entsteht aus Loyalität und Besatzungskosten
statt aus einem Schutzschild (`07_Planeten_und_Bevoelkerung.md`). Der
Zugang zu Handelsposten entsteht aus physischer Erreichbarkeit über
Trägerschiffe statt aus einer Sonderregel
(`05_Handelsgilde_und_Warenprinzip.md`). Die Öffnung des Gateways
entsteht aus wirtschaftlichem Eigeninteresse statt aus einem
Tutorial-Zwang (`04_Gateways_und_politische_Ordnung.md`).

Ein zweites, in den neueren Dokumenten hinzugekommenes Prinzip aus
demselben Geist:

> Mechaniken sollen möglichst keine starken Vorteile dafür erzeugen,
> rund um die Uhr auf kurzfristige Ereignisse reagieren zu können.
> Arbeitende Spieler sollen ebenso konkurrenzfähig bleiben können wie
> Spieler mit sehr viel verfügbarer Online-Zeit.

Konkrete Ausprägungen dieses Prinzips (Anlaufzeiten, Aufmarschphasen,
Kampfticks statt Echtzeitreaktion) stehen in `Mechanik/06_Blockaden_Gefechtsablauf_Aufmarsch.md`.

## 8. Offene Meta-Fragen

Diese Fragen betreffen keine einzelne Themendatei, sondern das Spiel als
Ganzes:

- Wie wird die grundlegende Zeitskala des Spiels definiert (Echtzeit vs.
  Ticks, typische Produktions- und Reisezeiten)? Für Kampfticks existiert
  inzwischen ein erster Arbeitswert (siehe
  `Mechanik/06_Blockaden_Gefechtsablauf_Aufmarsch.md`), für Produktions-
  und Reisezeiten allgemein nicht.
- Welche Rolle spielt Forschung als eigenständiges System (Techbaum,
  Forschungspunkte, Verhältnis zur Produktion)? Bisher taucht Forschung
  nur implizit auf (z. B. als Auslöser der Gateway-Entdeckung, vgl.
  `08_Neue_Spieler_und_Einstieg.md`).
- Wie werden Parteien, Allianzen und andere politische Organisationen
  technisch bzw. sozial strukturiert, wenn das Spiel sie formal nicht
  kennt? (vertieft in `04_Gateways_und_politische_Ordnung.md`)
