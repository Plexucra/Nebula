# Nebula -- 05: Geldkreislauf und ökonomischer Stresstest

## Status

Dieses Dokument ergänzt die Handelskonzeption um das Geldsystem und
prüft das vorgeschlagene Modell auf Missbrauchsmöglichkeiten.
Ausgangspunkt ist bewusst kein klassisches Faucet-and-Sink-System:
Credits sollen im normalen Spiel nicht laufend erzeugt oder vernichtet
werden, sondern zwischen Spielern und Bevölkerungen zirkulieren.

## 1. Grundregeln

-   Bei Entstehung eines neuen Einwohners entsteht grundsätzlich 1
    Credit.
-   Der Credit gehört zunächst der Bevölkerung bzw. ihrer abstrahierten
    Kaufkraft.
-   Normale Transaktionen erzeugen kein neues Geld.
-   Steuern werden vorerst gestrichen.
-   Ein eigener Arbeitsmarkt wird nicht simuliert.
-   1 Arbeitseinheit kostet 1 Credit.
-   Mehr Bevölkerung ermöglicht mehr gleichzeitig nutzbare Arbeit und
    damit schnellere bzw. umfangreichere Produktion.
-   Produktionsanlagen begrenzen zusätzlich, wie viel technisch
    produziert werden kann.

## 2. Lohn und Konsum

Produktionslohn ist kein Money Sink.

Benötigt eine Produktion 100 Arbeitseinheiten, zahlt der Spieler 100
Credits. Diese werden der Bevölkerung als Kaufkraft zugerechnet:

**Spieler → Lohn → Bevölkerung**

Die Bevölkerung kauft mit ihrer Kaufkraft reale Konsumgüter zu den
Preisen des lokalen Handelspostens:

**Bevölkerung → Geld → Verkäufer**

Das Konsumgut wird beim Verbrauch vernichtet, der Credit nicht.

Nicht ausgegebene Kaufkraft bleibt bei der Bevölkerung erhalten und kann
später ausgegeben werden.

## 3. Selbstversorgungstest

Spieler A besitzt 100 Credits. Er produziert Konsumgüter und zahlt 30
Credits Arbeitskosten.

Danach besitzt der Spieler 70 Credits und die Bevölkerung 30 Credits
Kaufkraft.

Kauft die eigene Bevölkerung anschließend diese Güter für 30 Credits vom
selben Spieler, besitzt dieser wieder 100 Credits.

Es wurde kein Geld erzeugt. Der Spieler hat Rohstoffe,
Produktionskapazität und Zeit eingesetzt, um seine Bevölkerung zu
versorgen.

**Ergebnis:** Eigenversorgung ist keine Gelddruckmaschine.

## 4. Handelstest

Spieler A zahlt 30 Credits Lohn. Seine Bevölkerung besitzt dadurch 30
Credits Kaufkraft.

Spieler B ist auf Konsumgüter spezialisiert und verkauft diese an die
Bevölkerung von A. Die 30 Credits wandern zu B.

A muss seinerseits Waren oder Leistungen exportieren, um wieder Geld aus
anderen Wirtschaftsräumen anzuziehen.

Damit unterstützt das Geldsystem Spezialisierung und Handel.

Autarkie bleibt technisch möglich, soll aber wegen der langfristigen
Spezialisierungsvorteile wirtschaftlich zunehmend ineffizient werden.

## 5. Flottenunterhalt

Auch Flottenunterhalt vernichtet kein Geld.

Er repräsentiert Sold, Versorgung, Wartung und andere Ausgaben des
militärischen Apparates. Das Geld wird an Bevölkerung in der Region bzw.
im Cluster ausgeschüttet, in dem sich die Flotte aufhält.

Damit gilt:

**Spieler → Flottenunterhalt → regionale Bevölkerung → Konsumnachfrage**

Eine starke militärische Präsenz kann dadurch einen lokalen
Wirtschaftsboom erzeugen.

Die genaue räumliche Verteilung der Ausschüttung bleibt offen.

## 6. Gateway- und Handelsgebühren

Gateway-Gebühren sind Transfers zwischen Spielern.

Bezahlt A zehn Credits für die Durchreise durch ein von B kontrolliertes
Gateway, erhält B diese zehn Credits.

Auch Gebühren neutraler Institutionen wie der Handelsgilde sollten
möglichst nicht vernichtet werden. Denkbar wäre eine regionale
Rückverteilung oder ein abstrahierter Gildenhaushalt, der das Geld
wieder ausgibt.

## 7. Stresstest: Bevölkerungsfarmen

Da jeder neue Einwohner Geld erzeugt, könnte ein Spieler versuchen,
ausschließlich Bevölkerung zu erzeugen.

Das ist zunächst weniger problematisch, wenn Bevölkerungswachstum reale
Güter und Infrastruktur benötigt. Neue Geldbasis wäre dann an reale
wirtschaftliche Expansion gekoppelt.

Problematisch wird jedoch folgende Schleife:

1.  Bevölkerung stark wachsen lassen.
2.  Für jeden Einwohner neue Credits erzeugen.
3.  Diese Credits über Konsum in die Spielerwirtschaft ziehen.
4.  Bevölkerung kollabieren lassen.
5.  Erneut wachsen.
6.  Wieder Credits erzeugen.

Wenn Tod kein Geld vernichtet, wäre dies eine echte Gelddruckmaschine.

### Empfohlene Sicherung: historischer Bevölkerungshöchststand

Neue Credits entstehen nur für Bevölkerung **oberhalb des bisherigen
historischen Höchststandes**.

Beispiel:

Ein Planet wächst erstmals von 8 auf 10 Milliarden Einwohner. Für die
zusätzlichen 2 Milliarden entsteht neue Geldbasis.

Danach fällt er auf 7 Milliarden zurück.

Wächst er später wieder von 7 auf 10 Milliarden, entsteht kein neues
Geld.

Erst beim Wachstum von 10 auf 11 Milliarden entsteht wieder neue
Geldbasis.

Damit kann dieselbe Bevölkerung nicht wiederholt zur Geldschöpfung
verwendet werden.

Alternativ könnte die Geldbasis an eine dauerhaft erschlossene
Bevölkerungskapazität gekoppelt werden. Das wäre stabiler, aber
abstrakter.

## 8. Stresstest: Geldhortung

Ein Altspieler könnte enorme Creditbestände ansammeln und nicht
ausgeben.

Dadurch fehlt anderen Marktteilnehmern Liquidität.

Allerdings erzeugt gehortetes Geld selbst keine Produktion. Produktion
benötigt laufende Arbeitszahlungen, Flotten benötigen Unterhalt und
Handel benötigt Liquidität.

**Vorschlag:** Zunächst keine Vermögenssteuer, keinen Geldverfall und
keine Obergrenze einführen. Spätere Simulationen müssen zeigen, ob die
normalen Ausgaben ausreichend Umlauf erzeugen.

## 9. Stresstest: Eine Region zieht fast alle Credits an

Ein hochspezialisierter Cluster könnte dauerhaft begehrte Waren
exportieren und immer mehr Credits ansammeln.

Das besitzt jedoch eine natürliche Gegenwirkung: Werden die
Käuferregionen zu geldarm, können sie die Exporte irgendwann nicht mehr
zu den bisherigen Preisen kaufen.

Die reiche Exportregion verliert Absatz und bekommt einen Anreiz, selbst
Waren zu importieren, dort Leistungen zu beziehen oder ihre Preise zu
senken.

Damit kann Geldknappheit selbst Handelsbeziehungen verändern.

**Bewertung:** zunächst gewünschte emergente Dynamik, solange sie nicht
zur vollständigen wirtschaftlichen Lähmung führt.

## 10. Stresstest: Flotten als Geldverteilungsmaschine

Ein Spieler könnte eine riesige Flotte in einer Region stationieren,
dort Unterhalt ausschütten lassen und anschließend selbst Konsumgüter an
diese Bevölkerung verkaufen.

Dann fließt ein Teil seines Flottenunterhalts zu ihm zurück.

Das ist nicht automatisch ein Exploit: Er musste reale Schiffe bauen,
bindet militärisches Kapital und muss reale Konsumgüter bereitstellen.

Problematisch wäre es erst, wenn Flottenunterhalt dadurch praktisch
kostenlos wird.

**Vorschlag:** numerisch testen, aber zunächst keine künstliche Sperre
einführen.

## 11. Stresstest: Flotten zur Marktmanipulation

Große Flotten könnten gezielt zwischen Regionen verlegt werden, um dort
Kaufkraft und Nachfrage zu verändern.

Das kann eine interessante strategische Konsequenz sein.

Reisezeiten, Unterhalt und geografische Beschränkungen verhindern
bereits beliebig schnelle Manipulation.

**Vorschlag:** grundsätzlich zulassen und beobachten.

## 12. Stresstest: Aufgestaute Kaufkraft

Ein blockierter oder isolierter Planet kann über lange Zeit Kaufkraft
ansammeln, wenn dort weiterhin gearbeitet wird, aber Konsumgüter fehlen.

Nach Öffnung des Marktes kann diese Kaufkraft auf ein knappes
Warenangebot treffen und starke Preissteigerungen auslösen.

Das ist grundsätzlich erwünscht: Händler erkennen die Gelegenheit und
transportieren Konsumgüter in die Region.

Es entsteht ein Nachkriegs- oder Nachblockadeboom ohne geskripteten
Bonus.

Zu prüfen bleibt, ob extrem lange Isolation zu absurden Kaufkraftbergen
führen kann.

## 13. Stresstest: Neue gegen alte Spieler

Alte Spieler können nominal sehr viel mehr Credits besitzen als neue
Spieler.

Das ist nicht automatisch problematisch. Credits sind kein Levelwert.
Alte Spieler besitzen gleichzeitig mehr Bevölkerung, Infrastruktur und
Produktionsvermögen.

Nach Öffnung des Gateways könnte ein reicher Altspieler versuchen, die
gesamte Produktion eines jungen Systems aufzukaufen.

Das muss nicht verhindert werden: Für den neuen Spieler kann der Verkauf
außerordentlich lukrativ sein. Sein Gateway gibt ihm außerdem Kontrolle
darüber, wen er überhaupt in sein System lässt.

## 14. Stresstest: Wirtschaftliche Kriegsführung

Ein reicher Spieler könnte sämtliche verfügbaren Konsumgüter eines
gegnerischen Systems aufkaufen, um dort Versorgung zu verknappen.

Das wäre grundsätzlich möglich, kostet ihn aber reales Geld. Die Waren
müssen anschließend tatsächlich gelagert oder transportiert werden.

Damit wäre dies keine kostenlose Sabotage, sondern wirtschaftliche
Kriegsführung mit realen Opportunitätskosten.

**Vorschlag:** nicht vorschnell verbieten.

## 15. Tod und Geldmenge

Beim Tod eines Einwohners sollte nicht automatisch ein Credit vernichtet
werden.

Der ursprünglich bei dessen Entstehung geschaffene Credit kann längst
einem Händler in einem völlig anderen Sektor gehören. Eine nachträgliche
Vernichtung wäre deshalb künstlich.

Die Geldmenge entspricht somit eher der historischen wirtschaftlichen
Expansion als exakt der aktuellen Bevölkerung.

Nach einer massiven Bevölkerungskatastrophe könnte relativ viel Geld auf
weniger Bevölkerung und Waren treffen. Eine daraus entstehende Inflation
wäre eine mögliche emergente Folge.

Der historische Bevölkerungshöchststand verhindert gleichzeitig, dass
der Wiederaufbau derselben Bevölkerung erneut Geld erzeugt.

## 16. Wichtigster Missbrauchspunkt

Der gefährlichste bislang erkennbare Exploit ist nicht Selbstversorgung,
sondern:

**Bevölkerung erzeugen → Geld abschöpfen → Bevölkerung verlieren →
Bevölkerung erneut erzeugen.**

Daher ist die stärkste Empfehlung dieses Stresstests:

> Neue Geldbasis entsteht nur für einen neuen historischen
> Bevölkerungshöchststand bzw. eine vergleichbare dauerhaft neu
> erschlossene Bevölkerungsbasis.

Diese eine Regel könnte ausreichen, um das ansonsten weitgehend
geschlossene Geldsystem ohne klassische Money Sinks stabil zu halten.

## 17. Vorläufiges Geldmodell

1.  Neue dauerhaft zusätzliche Bevölkerung erzeugt neue Geldbasis.
2.  Produktion überträgt Credits vom Spieler an die Bevölkerung.
3.  Bevölkerung verwendet Credits zum Kauf realer Konsumgüter.
4.  Konsum vernichtet Waren, nicht Geld.
5.  Spielerhandel verteilt Credits zwischen Spielern und Regionen.
6.  Gateway-Gebühren verteilen Credits zwischen Spielern.
7.  Flottenunterhalt verteilt Credits zurück an regionale Bevölkerung.
8.  Credits werden im normalen Spiel grundsätzlich nicht vernichtet.
9.  Tod erzeugt keine automatische Geldvernichtung.
10. Wiederherstellung bereits früher vorhandener Bevölkerung erzeugt
    keine zweite Geldbasis.

## 18. Was vorerst nicht eingeführt wird

-   Steuern
-   frei einstellbare Steuersätze
-   simulierter Arbeitsmarkt
-   frei verhandelbare Löhne
-   künstlicher Geldverfall
-   Vermögenssteuer
-   regelmäßige Zentralbankzahlungen
-   automatische Geldvernichtung bei Tod
-   klassische Money Sinks nur zur Inflationskontrolle

## 19. Nächster sinnvoller Test

Die nächste Prüfung sollte quantitativ erfolgen.

Zu simulieren wären mindestens:

-   ein autarker Planet,
-   zwei spezialisierte Handelspartner,
-   Rohstoff- und Industriecluster,
-   eine reiche Transitregion,
-   eine Kriegsregion mit hoher Flottenpräsenz,
-   ein stark wachsender Kolonialraum,
-   eine stagnierende Altregion,
-   eine Region nach schwerer Bevölkerungskatastrophe.

Beobachtet werden sollten:

-   Gesamtgeldmenge,
-   Credits pro Einwohner,
-   Verteilung zwischen Spielern und Bevölkerung,
-   Kaufkraftstau,
-   Handelsvolumen,
-   Vermögenskonzentration,
-   Geldumlaufgeschwindigkeit,
-   Auswirkungen von Flottenstationierung,
-   Auswirkungen von Bevölkerungswachstum.

Erst anhand solcher Simulationen sollte entschieden werden, ob das
geschlossene Kreislaufmodell zusätzliche Korrekturen benötigt.
