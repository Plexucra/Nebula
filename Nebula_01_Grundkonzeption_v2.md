# Nebula -- Konzeptdokument 01

## Grundkonzept, Wirtschaftsstruktur und strategische Spezialisierung

**Arbeitstitel:** Nebula\
**Dokument:** 01 -- Grundkonzeption\
**Status:** Frühe Konzeptfassung\
**Zielplattform:** Browser; perspektivisch mobile Clients\
**Zielgröße:** Hunderte bis potenziell Millionen Spieler

------------------------------------------------------------------------

## 1. Vision

*Nebula* ist ein persistentes Online-Strategiespiel, dessen zentrales
Element eine von Spielern getragene, dynamisch skalierende Wirtschaft
ist.

Das Spiel soll nicht primär darauf beruhen, dass jeder Spieler
wirtschaftlich autark wird. Stattdessen soll das System Spezialisierung,
Handel, Kooperation, regionale Abhängigkeiten und strategische Konflikte
erzeugen.

Die grundlegende Designidee lautet:

> Je größer die Spielerschaft und je stärker die wirtschaftliche
> Spezialisierung, desto tiefer kann sich die Produktionskette
> auffächern.

Jeder Spieler soll die Möglichkeit haben, eine wirtschaftliche Nische zu
finden, in der er für andere Spieler, Gruppen oder politische Parteien
einen messbaren ökonomischen Wert erzeugt.

------------------------------------------------------------------------

## 2. Strategische Grundeinheit: Planet und Flotte

Jeder Spieler verfügt über einen Planeten beziehungsweise eine planetare
wirtschaftliche Basis.

An einem Planeten kann eine große Blockade- bzw. Verteidigungsflotte
stationiert werden. Für diese Flotte stehen drei grundlegende
Schiffstypen zur Verfügung:

-   **Schiffstyp A**
-   **Schiffstyp B**
-   **Schiffstyp C**

Die genaue Zusammensetzung einer Flotte ist frei wählbar.

Schiffe können entweder selbst produziert oder von anderen Spielern
erworben werden. Damit sind militärische Stärke und wirtschaftliche
Leistungsfähigkeit unmittelbar miteinander verbunden.

------------------------------------------------------------------------

## 3. Drei Schiffstypen und das Kontersystem

Zwischen den drei Schiffstypen besteht ein bewusst asymmetrisches
Kontersystem.

Jeder Schiffstyp besitzt einen klaren Vorteil gegenüber einem anderen
Schiffstyp und gleichzeitig eine relevante Schwäche gegenüber dem
dritten Typ. Kein einzelner Schiffstyp soll gegen beide anderen Typen
gleichermaßen effizient sein.

Das System folgt damit grundsätzlich einer zyklischen Struktur:

**A schlägt B → B schlägt C → C schlägt A**

Eine Flotte, die nur aus einem Schiffstyp besteht, kann wirtschaftlich
besonders effizient herzustellen sein, besitzt jedoch eine ausgeprägte
militärische Schwachstelle.

Eine robuste Verteidigung erfordert deshalb grundsätzlich eine
ausgewogene Verfügbarkeit aller drei Schiffskategorien.

Damit entsteht eine Verbindung zwischen wirtschaftlicher Spezialisierung
und militärischer Verwundbarkeit.

------------------------------------------------------------------------

## 4. Hierarchische Produktion

Schiffe werden nicht unmittelbar aus Rohstoffen hergestellt. Ihre
Produktion erfolgt über eine hierarchische Modulstruktur.

In einer vereinfachten Darstellung gilt:

**1 Schiff → 10 Module → je 10 Submodule → je 10 weitere Submodule → ...
→ Rohstoffe**

Ein Schiff benötigt beispielsweise zehn Hauptmodule. Jedes dieser
Hauptmodule kann wiederum aus zehn Untermodulen bestehen. Jedes
Untermodul kann erneut in zehn Komponenten zerlegt werden.

Diese Produktionshierarchie ist konzeptionell nicht auf eine feste Tiefe
beschränkt.

------------------------------------------------------------------------

## 5. Dynamische Skalierung der Produktionsketten

Die Tiefe der Produktionskette soll sich an die Größe und
wirtschaftliche Reife der Spielwelt anpassen können.

Bei einer kleinen Spielerschaft kann ein Spieler vergleichsweise
hochstufige Module direkt produzieren.

Bei einer großen Spielerschaft kann dieselbe Produktion in immer feinere
Teilprodukte zerlegt werden.

Dadurch kann die wirtschaftliche Komplexität mit der Population wachsen,
ohne dass bereits eine kleine Spielwelt zwingend Millionen
unterschiedlicher Produzenten benötigt.

Das System soll somit sowohl mit Hunderten als auch perspektivisch mit
sehr großen Spielerzahlen funktionieren.

------------------------------------------------------------------------

## 6. Produktionsspezialisierung

Ein zentrales ökonomisches Prinzip von *Nebula* ist die Spezialisierung.

Je länger ein Planet dasselbe konkrete Produkt produziert, desto
effizienter wird dessen Herstellung. Diese Effizienz kann sich
beispielsweise in kürzeren Produktionszeiten, höherem Output oder
geringerem Ressourceneinsatz ausdrücken; die genaue mathematische
Ausgestaltung wird in späteren Dokumenten definiert.

Ein Spieler steht damit vor einer strategischen Entscheidung:

-   häufig zwischen Produkten wechseln und flexibel bleiben,
-   oder sich langfristig auf ein bestimmtes Produkt spezialisieren und
    dadurch einen erheblichen Effizienzvorteil aufbauen.

------------------------------------------------------------------------

## 7. Arbeitsteilung als wirtschaftlicher Multiplikator

Besonders wichtig ist die Möglichkeit, einen Produktionsschritt nicht
selbst vollständig auszuführen.

Angenommen, ein Modul besteht aus zehn unterschiedlichen Submodulen.

Ein Spieler könnte das Hauptmodul direkt produzieren. Alternativ können
zehn spezialisierte Spieler jeweils eines der zehn benötigten Submodule
herstellen.

Wenn jeder dieser Spieler langfristig auf sein jeweiliges Submodul
spezialisiert ist, wirken die Spezialisierungsvorteile auf zehn
getrennte Produktionsprozesse.

Die gemeinsame, arbeitsteilige Produktion soll dadurch effizienter
werden als eine Struktur, in der dieselben Spieler unabhängig
voneinander jeweils das vollständige Hauptmodul produzieren.

Die wirtschaftliche Logik lautet:

> Arbeitsteilung + Spezialisierung soll einen systemischen
> Effizienzgewinn erzeugen.

Dadurch entsteht ein ökonomischer Druck zur Kooperation, ohne
Kooperation formal erzwingen zu müssen.

------------------------------------------------------------------------

## 8. Spieler als wirtschaftliche Nischenakteure

Mit zunehmender Tiefe der Produktionsketten entstehen immer mehr
spezialisierbare Produkte.

Ein Spieler muss deshalb nicht zwingend komplette Schiffe, Hauptmodule
oder andere Endprodukte herstellen. Er kann beispielsweise
ausschließlich ein bestimmtes Submodul produzieren und damit zu einem
unverzichtbaren Bestandteil einer wesentlich größeren Lieferkette
werden.

Das Ziel ist eine Wirtschaft, in der auch ein hoch spezialisierter
Produzent eines scheinbar kleinen Bauteils strategische Bedeutung
besitzen kann.

Große Organisationen und Regionen benötigen dadurch Netzwerke aus
Produzenten statt lediglich eine kleine Zahl vollständig autarker
Industrieplaneten.

------------------------------------------------------------------------

## 9. Rohstoffsystem

Die drei Schiffstypen benötigen grundsätzlich Rohstoffe aus mehreren
Ressourcenkategorien.

Die benötigten Mengen und Mischungsverhältnisse unterscheiden sich
jedoch stark.

Beispielsweise könnte gelten:

-   Schiffstyp A benötigt besonders viel Ressourcengruppe A,
-   Schiffstyp B besonders viel Ressourcengruppe B,
-   Schiffstyp C besonders viel Ressourcengruppe C.

Alle Ressourcengruppen können grundsätzlich für alle Schiffstypen
relevant bleiben. Entscheidend ist die unterschiedliche Gewichtung.

Damit besitzt jeder Schiffstyp ein eigenes wirtschaftliches
Ressourcenprofil.

------------------------------------------------------------------------

## 10. Geografische Ressourcencluster

Rohstoffe sind im Universum nicht gleichmäßig verteilt.

Stattdessen existieren großräumige Ressourcencluster. Eine Region kann
beispielsweise besonders reich an Ressource A sein, während Ressourcen B
und C dort vergleichsweise selten vorkommen.

Wichtig ist, dass fehlende Ressourcen nicht regelmäßig auf einem
unmittelbar benachbarten Planeten in ausreichender Menge verfügbar sind.

Die Cluster müssen deshalb groß genug sein, um tatsächlich regionale
Wirtschaftsstrukturen zu erzeugen.

Eine Region mit einem starken Überschuss einer Ressource wird ökonomisch
dazu gedrängt, Produktionsketten aufzubauen, die diese Ressource
besonders intensiv nutzen.

------------------------------------------------------------------------

## 11. Verbindung von Geografie, Wirtschaft und Krieg

Ressourcencluster, Produktionsspezialisierung und das Kontersystem der
Schiffe greifen ineinander.

Eine Region mit großen Vorkommen der für Schiffstyp A wichtigen
Rohstoffe kann sich besonders effizient auf die Produktion von
A-Schiffen spezialisieren.

Dadurch entsteht jedoch eine strategische Verwundbarkeit: Wenn A gegen C
schwach ist, kann ein Gegner gezielt C-Flotten einsetzen.

Die Region benötigt deshalb entweder:

-   Handel mit Regionen, die andere Ressourcen besitzen,
-   eigene ineffizientere Produktionslinien für die fehlenden
    Schiffstypen,
-   militärische Bündnisse,
-   langfristige Lieferverträge,
-   oder andere Formen strategischer Absicherung.

Damit erzeugt die Geografie wirtschaftliche Spezialisierung, und die
wirtschaftliche Spezialisierung erzeugt wiederum militärische und
politische Abhängigkeiten.

------------------------------------------------------------------------

## 12. Zentrale Designschleife

Das bisherige Konzept lässt sich als folgende Kette zusammenfassen:

**Ungleich verteilte Ressourcen**\
→ **regionale Produktionsvorteile**\
→ **Spezialisierung auf bestimmte Produkte und Schiffstypen**\
→ **höhere wirtschaftliche Effizienz**\
→ **größere Abhängigkeit von Handel und anderen Produzenten**\
→ **militärische Verwundbarkeit durch das Kontersystem**\
→ **Kooperation, Diplomatie, Handel oder Krieg**\
→ **Veränderung von Lieferketten und Spezialisierungen**

Die Wirtschaft ist damit nicht lediglich ein Versorgungssystem für den
militärischen Teil des Spiels. Sie bildet selbst einen zentralen Teil
des strategischen Gameplays.

------------------------------------------------------------------------

## 13. Leitprinzipien für spätere Dokumente

Die folgenden Prinzipien gelten vorläufig als Kern des Konzepts:

1.  **Niemand soll vollständig autark sein müssen oder langfristig
    optimal autark sein können.**
2.  **Spezialisierung muss wirtschaftlich attraktiver sein als
    vollständige Eigenproduktion.**
3.  **Arbeitsteilung muss mit wachsender Spielerzahl zunehmend wertvoll
    werden.**
4.  **Produktionsketten müssen mit der Größe der Spielwelt skalieren
    können.**
5.  **Regionale Ressourcenunterschiede müssen groß genug sein, um echten
    Handel zu erzeugen.**
6.  **Ökonomische Spezialisierung soll strategische Verwundbarkeit
    erzeugen.**
7.  **Kein einzelner Schiffstyp darf eine universell optimale
    militärische Lösung darstellen.**
8.  **Kleine wirtschaftliche Nischen müssen für das Gesamtsystem
    relevant bleiben können.**
9.  **Wirtschaft, Geografie, Diplomatie und Krieg sollen keine
    getrennten Systeme sein, sondern sich gegenseitig beeinflussen.**

------------------------------------------------------------------------

## 14. Noch bewusst offene Fragen

Dieses erste Dokument definiert die Grundstruktur, aber noch keine
endgültigen Zahlen oder Balancingregeln. Insbesondere bleiben offen:

-   Wie genau Spezialisierungsboni wachsen und verfallen.
-   Wie schnell ein Planet seine Spezialisierung wechseln kann.
-   Wie viele Produktionsstufen bei welcher Spielerzahl sinnvoll sind.
-   Wie neue Produktionsstufen eingeführt werden, ohne bestehende
    Lieferketten zu zerstören.
-   Wie Transport, Entfernung und Logistik funktionieren.
-   Wie Märkte, Preise, Verträge und Handel technisch organisiert
    werden.
-   Wie stark die drei Schiffstypen einander kontern.
-   Wie Flottenverluste und Ersatzproduktion funktionieren.
-   Wie Ressourcen entstehen, erschöpft werden oder regenerieren.
-   Wie neue Spieler in bereits hoch spezialisierte Wirtschaftsräume
    eintreten können.
-   Wie Parteien, Allianzen und andere politische Organisationen
    strukturiert sind.
-   Wie verhindert wird, dass einzelne Großakteure komplette
    Lieferketten monopolisieren.

Diese Punkte sollten in späteren, nummerierten Konzeptdokumenten einzeln
konkretisiert und bei Bedarf auf Entscheidungen aus Dokument 01
zurückgeführt werden.

------------------------------------------------------------------------

## 15. Meta-Progression: Vom Aufbauspiel zum Gesellschaftsspiel

*Nebula* soll sich im Verlauf der Spielerentwicklung in seinem Charakter
verändern.

Zu Beginn steht der eigene Planet im Mittelpunkt. Die Motivation
entsteht zunächst vor allem aus Aufbau, Optimierung und unmittelbar
nachvollziehbarem Fortschritt: Produktionsmöglichkeiten werden
erweitert, erste Spezialisierungen aufgebaut, Schiffe verfügbar gemacht
und wirtschaftliche Engpässe überwunden.

Mit zunehmender wirtschaftlicher Entwicklung soll der Spieler jedoch an
einen Punkt gelangen, an dem weiteres Wachstum allein immer weniger
sinnvoll ist. Nicht eine künstliche Stufenbegrenzung, sondern die
Struktur der Wirtschaft soll dazu führen, dass andere Spieler zunehmend
wichtig werden.

Die angestrebte Entwicklung lautet:

**Aufbauspiel → Wirtschaftsspiel → Gesellschaftsspiel → Diplomatie-,
Konflikt- und Intrigenspiel**

Der Übergang soll organisch erfolgen. Das Spiel soll nicht ab einer
bestimmten Stufe verlangen, einer Allianz beizutreten. Stattdessen soll
der Spieler selbst feststellen, dass Spezialisierung, Handel, Transport,
Verteidigung und Zugriff auf entfernte Ressourcen durch Kooperation
erheblich effizienter werden.

Im späteren Spiel sollen daher zunehmend nicht weitere Gebäudestufen,
sondern Beziehungen zwischen Spielern zum Gegenstand des Spiels werden.
Vertrauen, Abhängigkeiten, Handelsinteressen, territoriale Interessen,
Konflikte und politische Entscheidungen sollen langfristig einen
größeren Teil der Motivation übernehmen.

------------------------------------------------------------------------

## 16. Spezialisierung, Expansion und Konzentration

Die positive Rückkopplung der Spezialisierung ist ausdrücklich gewollt.

Ein Planet, der über lange Zeit dasselbe Produkt herstellt, soll darin
einen erheblichen Effizienzvorteil aufbauen können. Dieser Vorteil ist
Teil der langfristigen Motivation und soll erfolgreiche Spezialisierung
spürbar belohnen.

Gleichzeitig darf dieser Mechanismus nicht zwangsläufig dazu führen,
dass ein erfolgreicher Spieler anschließend sämtliche Produktionszweige
monopolisiert.

Als vorläufiges Prinzip gilt deshalb:

> **Spezialisierung darf starke positive Rückkopplung erzeugen; die
> Ausweitung der Kontrolle auf immer mehr Spezialisierungen muss dagegen
> neue Kosten, Risiken und Verwundbarkeiten erzeugen.**

Eine mögliche grundlegende Regel ist, dass **ein Planet jeweils nur eine
wesentliche Produktionsspezialisierung besitzen kann**.

Wer einen weiteren Produktionszweig mit demselben Spezialisierungsgrad
kontrollieren möchte, benötigt dafür einen weiteren Planeten. Eine
breite vertikale Integration erfordert somit die Kontrolle vieler
Planeten.

Viele Planeten erzeugen wiederum Probleme, die ein konzentrierter
Spieler nicht in demselben Umfang besitzt:

-   Jeder zusätzliche Planet muss militärisch geschützt werden.
-   Verteidigungsflotten binden Schiffe und damit wirtschaftliche
    Ressourcen.
-   Eine über viele Planeten verteilte Produktion erzeugt zusätzliche
    Transportwege.
-   Mit zunehmender Zahl kontrollierter Kolonien kann politische und
    administrative Stabilität schwieriger werden.

Politische Stabilität ist hierbei zunächst ein konzeptioneller Ansatz
und noch kein ausdefiniertes System. Entscheidend ist, dass Expansion
nicht lediglich durch einen abstrakten Prozentmalus begrenzt werden
soll. Viele Kolonien sollen konkrete Aufgaben und Interessenkonflikte
erzeugen, beispielsweise durch Sicherheits-, Verwaltungs-, Versorgungs-
oder Kontrollaufwand.

Daraus ergeben sich drei vorläufige Meta-Prinzipien:

> **Spezialisierung erzeugt zunehmende Effizienz.**

> **Expansion erzeugt zunehmende Komplexität.**

> **Konzentration erzeugt zunehmende Verwundbarkeit.**

------------------------------------------------------------------------

## 17. Keine notwendige formale Allianzmechanik

Für die politische Organisation der Spieler wird zunächst ausdrücklich
**kein klassisches Allianzsystem als notwendige Kernmechanik
vorausgesetzt**.

Spieler können ihre Organisation außerhalb der vom Spiel vorgegebenen
Strukturen koordinieren. Sie können sich selbst Namen geben,
Kommunikationskanäle verwenden, Absprachen treffen und größere
politische Blöcke bilden. Das Spiel muss diese Gruppen nicht zwingend
als formale Allianz kennen.

Stattdessen soll die Spielmechanik Bedingungen schaffen, aus denen sich
lokale Interessengemeinschaften von selbst ergeben.

Das Ziel ist nicht: Spieler treten einer Allianz bei und erhalten
deshalb Vorteile.

Das Ziel ist: Spieler haben aufgrund ihrer geografischen und
wirtschaftlichen Situation gemeinsame Interessen und beginnen deshalb
miteinander zu kooperieren.

Dies soll gleichzeitig der Entstehung vollständig homogener
Mega-Allianzen entgegenwirken. Auch wenn sich außerhalb des Spiels ein
sehr großer Machtblock bildet, sollen seine Mitglieder aufgrund ihrer
unterschiedlichen geografischen Positionen nicht zwangsläufig identische
Interessen besitzen.

------------------------------------------------------------------------

## 18. Sonnensysteme und Gateways

Reisen zwischen Sonnensystemen erfolgen grundsätzlich nicht beliebig
durch den Raum, sondern über **Gateways**, die Sonnensysteme miteinander
verbinden.

Das Universum wird dadurch zu einem Netzwerk aus Sonnensystemen und
kontrollierbaren Verbindungen:

**Sonnensystem → Gateway → Sonnensystem → Gateway → Sonnensystem**

Gateways bilden reale strategische Engstellen. Wer ein Gateway
kontrolliert, kann Einfluss darauf nehmen, welche Schiffe und Frachter
diese Verbindung benutzen.

Ein Gateway kann insbesondere für den Verkehr geöffnet, mit einem Zoll
belegt oder militärisch blockiert werden.

Damit erhält die Lage eines Sonnensystems einen eigenständigen
strategischen Wert. Ein System kann wirtschaftlich oder politisch
wichtig sein, obwohl seine eigenen Rohstoffvorkommen durchschnittlich
sind, wenn über seine Gateways bedeutende Handelsströme laufen.

------------------------------------------------------------------------

## 19. Zölle ausschließlich als bilaterales Beziehungssystem

Zölle sollen grundsätzlich **bilateral zwischen einzelnen Spielern**
geregelt werden. Ein darüberliegendes formales Allianz- oder
Handelsblocksystem ist dafür nicht notwendig.

Der Kontrolleur eines Gateways definiert einen **Standardzollsatz pro
Frachter**. Dieser Standardsatz gilt gegenüber allen Spielern, mit denen
keine individuelle bilaterale Vereinbarung besteht.

Beispiel:

**Standardzoll: 8 % pro Frachter**

Davon abweichend kann der Spieler für einzelne andere Spieler
individuelle Zollsätze vereinbaren:

-   Spieler X: 0 %
-   Spieler Y: 2 %
-   Spieler Z: 5 %
-   alle Spieler ohne Vertrag: 8 %

Damit lautet die grundlegende politische Regel nicht „Mitglieder meiner
Allianz zahlen keinen Zoll", sondern beispielsweise „Mit Spieler X habe
ich bilateral 0 % vereinbart".

Verträge sind nicht automatisch übertragbar. Wenn A mit B 0 % Zoll
vereinbart und B mit C ebenfalls 0 %, entsteht daraus **kein** Vertrag
zwischen A und C.

Auf diese Weise kann sich ein dichtes Netz lokaler Beziehungen
herausbilden, ohne dass daraus automatisch ein homogener politischer
Block mit identischen Außenbeziehungen entsteht.

Die konkrete Berechnungsgrundlage des Zolls und die technische
Ausgestaltung von Vertragsabschluss, Vertragsdauer und Vertragsbruch
werden später definiert.

------------------------------------------------------------------------

## 20. Warum unmittelbare Nachbarn natürliche Partner werden

Die Kombination aus Ressourcenclustern, Spezialisierung und
Gateway-Zöllen soll einen konkreten Anreiz für lokale Kooperation
erzeugen.

Benachbarte Sonnensysteme innerhalb desselben Ressourcenclusters
verfügen tendenziell über ähnliche Rohstoffvorkommen. Dadurch
spezialisieren sich ihre Wirtschaften tendenziell auf ähnliche Produkte.

Gerade deshalb sind unmittelbare Nachbarn häufig **nicht die
attraktivsten Endabnehmer** der eigenen spezialisierten Waren. Sie
besitzen ähnliche Ressourcen und produzieren ähnliche Güter bereits
selbst oder im Überfluss.

Ein Produzent möchte seinen Überschuss vielmehr **aus dem lokalen
Ressourcencluster heraus transportieren**, um ihn in weiter entfernten
Regionen zu verkaufen, in denen diese Ressourcen und Produkte knapper
sind.

Dafür muss er jedoch die Gateways seiner unmittelbaren Nachbarn
passieren.

Dadurch entsteht ein wechselseitiger Anreiz, die direkten Nachbarn nicht
oder nur gering zu besteuern. Der eigene Nachbar benötigt meine Route,
um seine Waren aus dem Cluster zu exportieren; gleichzeitig benötige ich
seine Route für meine eigenen Exporte.

Die Nachbarn können somit wirtschaftlich Konkurrenten sein und trotzdem
ein starkes gemeinsames Interesse an günstigem gegenseitigem Transit
besitzen.

------------------------------------------------------------------------

## 21. Warum entfernte Spieler stärker besteuert werden können

Bei weiter entfernten Händlern verändert sich die Interessenlage.

Ein fremder Frachter aus einer entfernten Region, der ein Gateway
lediglich als Transitstrecke benötigt, ist für dessen Kontrolleur nicht
zwangsläufig in derselben Weise wichtig wie ein unmittelbarer Nachbar,
dessen Gateway der Kontrolleur selbst regelmäßig für seine Exporte
benötigt.

Dadurch entsteht ein Anreiz, entfernte Handelsreisende stärker mit
Zöllen zu belegen.

Vereinfacht:

**Direkter Nachbar:** niedriger oder kein Zoll, weil beide Seiten die
gegenseitigen Handelswege benötigen.

**Entfernter Spieler:** höherer Standardzoll, weil dessen Durchreise
Einnahmen erzeugt und keine gleich starke unmittelbare Gegenseitigkeit
bestehen muss.

Die wirtschaftlich rationale Beziehung hängt damit von der geografischen
Position ab.

------------------------------------------------------------------------

## 22. Lokale Interessengemeinschaften und Spannungen an ihren Grenzen

Aus den bilateralen Zollbeziehungen soll sich ohne formale
Allianzmechanik eine lokale politische Struktur herausbilden.

Spieler einer Region haben gemeinsame Interessen, weil sie dieselben
oder aufeinanderfolgenden Gateways für ihre Exporte benötigen.
Gegenseitig niedrige Zölle und die gemeinsame Sicherung dieser
Handelswege können deshalb rational sein.

Mit zunehmender Entfernung verändert sich jedoch die Interessenlage.

Dadurch kann sich faktisch eine lokale Allianz herausbilden, obwohl das
Spiel sie nirgendwo als Allianz registriert.

Gerade an den Grenzen einer solchen Interessengemeinschaft sollen
Spannungen entstehen. Ein Spieler am Rand einer Region kann
wirtschaftlich stark vom unmittelbar angrenzenden Spieler der
Nachbarregion abhängig sein. Seine lokalen Interessen können deshalb von
den Interessen weiter innen liegender Spieler seines eigenen politischen
Umfelds abweichen.

Das ist ausdrücklich erwünscht.

Eine sehr große, außerhalb des Spiels organisierte Mega-Allianz kann
zwar existieren. Sie erhält durch das Spiel jedoch keine automatische
wirtschaftliche Homogenität. Ein Mitglied am einen Ende des Machtblocks
kann andere lokale Interessen besitzen als ein Mitglied am anderen Ende.

Die Begrenzung von Mega-Allianzen soll daher möglichst nicht durch eine
maximale Mitgliederzahl erfolgen, sondern dadurch, dass **politische und
wirtschaftliche Interessen lokal bleiben und sich mit der geografischen
Position verändern**.

------------------------------------------------------------------------

## 23. Gateways als wirtschaftliche und militärische Machtpositionen

Da Frachter auf Gateways angewiesen sind, kann die Kontrolle eines
Gateways wirtschaftliche Macht erzeugen. Ein Spieler kann Transitverkehr
besteuern, einzelnen Spielern Sonderkonditionen gewähren oder Durchfahrt
politisch einsetzen.

Gleichzeitig können Gateways militärisch blockiert werden.

Ein Krieg muss deshalb nicht ausschließlich auf die Eroberung eines
Produktionsplaneten abzielen. Es kann strategisch bereits entscheidend
sein, eine wichtige Handelsverbindung zu kontrollieren oder
abzuschneiden.

Ein hoch spezialisierter Planet kann extrem effizient produzieren, aber
von wenigen Handelswegen abhängig sein. Seine wirtschaftliche Stärke
erzeugt damit zugleich konkrete Verwundbarkeit.

Die Systemkette erweitert sich damit zu:

**Ressourcencluster → regionale Spezialisierung → Produktionsüberschüsse
→ Fernhandel → Gateway-Abhängigkeit → bilaterale Zollverträge → lokale
Interessengemeinschaften → Grenzspannungen → Konflikte um Handelswege**

------------------------------------------------------------------------

## 24. Vorläufige politische Designphilosophie

Aus den bisherigen Überlegungen ergibt sich folgende politische
Grundidee:

> **Nebula soll soziale und politische Strukturen möglichst nicht durch
> abstrakte Allianzmechaniken vorschreiben. Wirtschaft,
> Ressourcenverteilung, Entfernung, Gateways, bilaterale Zölle und
> militärische Verwundbarkeit sollen stattdessen Situationen erzeugen,
> in denen Kooperation zwischen bestimmten Spielern rational wird und
> zwischen anderen Spielern Interessenkonflikte entstehen.**

Die kleinste formalisierte politische Beziehung ist damit zunächst die
**bilaterale Vereinbarung zwischen zwei Spielern**.

Größere politische Gebilde entstehen aus vielen solchen Beziehungen und
aus informeller Koordination außerhalb des Spiels. Das Spiel muss nicht
wissen, ob 50 Spieler sich selbst als Allianz, Republik, Konzern oder
Imperium verstehen.

Entscheidend sind die tatsächlichen Beziehungen: Wer gewährt wem
Durchfahrt? Wer erhebt welchen Zoll? Wer benötigt wessen Handelsroute?
Wer schützt welches Gateway? Und wer kann den Handel eines anderen
Spielers blockieren?

Damit sollen politische Strukturen in *Nebula* möglichst als Folge
realer Interessen entstehen und nicht als Voraussetzung für diese
Interessen.
