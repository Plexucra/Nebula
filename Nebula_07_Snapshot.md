# Nebula -- 07: Konzept-Snapshot

## Status des Dokuments

Dieses Dokument fasst den gesamten bisherigen Designstand von *Nebula*
in einer einzigen, in sich konsistenten Konzeptdatei zusammen. Es
enthält ausschließlich den aktuell gültigen Stand aller bisherigen
Festlegungen, gruppiert nach Themen statt nach Entstehungsdatum. Die
zugrunde liegenden Werte, Zahlen und Mechanismen sind weiterhin
größtenteils **vorläufig** und noch nicht numerisch ausbalanciert.

---

## 1. Vision

*Nebula* ist ein persistentes Online-Strategiespiel für Browser
(perspektivisch auch mobile Clients), dessen zentrales Element eine von
Spielern getragene, dynamisch skalierende Wirtschaft ist. Zielgröße
sind Hunderte bis potenziell Millionen Spieler.

Das Spiel soll nicht darauf beruhen, dass jeder Spieler wirtschaftlich
autark wird. Stattdessen erzeugt das System Spezialisierung, Handel,
Kooperation, regionale Abhängigkeiten und strategische Konflikte.

Grundidee:

> Je größer die Spielerschaft und je stärker die wirtschaftliche
> Spezialisierung, desto tiefer kann sich die Produktionskette
> auffächern.

Jeder Spieler soll eine wirtschaftliche Nische finden können, in der er
für andere Spieler, Gruppen oder politische Parteien einen messbaren
ökonomischen Wert erzeugt.

### Leitprinzipien

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

---

## 2. Meta-Progression: Vom Aufbauspiel zum Gesellschaftsspiel

Der Charakter des Spiels verändert sich mit der Entwicklung des
Spielers:

**Aufbauspiel → Wirtschaftsspiel → Gesellschaftsspiel → Diplomatie-,
Konflikt- und Intrigenspiel**

Zu Beginn steht der eigene Planet im Mittelpunkt: Aufbau, Optimierung,
unmittelbar nachvollziehbarer Fortschritt. Mit wachsender
wirtschaftlicher Entwicklung wird weiteres reines Wachstum zunehmend
weniger sinnvoll — nicht durch eine künstliche Stufenbegrenzung,
sondern weil die Struktur der Wirtschaft andere Spieler zunehmend
wichtig macht.

Der Übergang erfolgt organisch. Es gibt bewusst kein in-Game
Allianz-System. Die Spieler sollen sich selbst organisieren. Der Spieler entdeckt selbst, dass
Spezialisierung, Handel, Transport, Verteidigung und Zugriff auf
entfernte Ressourcen durch Kooperation erheblich effizienter werden. Im
späteren Spiel werden zunehmend Beziehungen zwischen Spielern — nicht
weitere Gebäudestufen — zum Gegenstand des Spiels: Vertrauen,
Abhängigkeiten, Handelsinteressen, territoriale Interessen, Konflikte
und politische Entscheidungen.

---

## 3. Planeten und Flotten

Jeder Spieler verfügt über einen Planeten bzw. eine planetare
wirtschaftliche Basis. An einem Planeten kann eine Blockade- bzw.
Verteidigungsflotte stationiert werden undabhäng davon ob man "im Besitz" des Planeten ist. Die Zusammensetzung einer
Flotte ist frei wählbar. Schiffe können selbst produziert oder von
anderen Spielern erworben werden — militärische Stärke und
wirtschaftliche Leistungsfähigkeit sind damit unmittelbar verbunden.

### Schiffskategorien

1. Kampfschiff A - Korvette
2. Kampfschiff B - Zerstörer
3. Kampfschiff C - Kreuzer
4. Frachter (nur für Waren)
5. Trägerschiff (nur zum Transport von Schiffen - hat Sprungantrieb und kann als einziges von System zu System springen ohne Gateway (aber sehr langsam))
6. Mannschaftstransporter

### Kontersystem der Kampfschiffe

Zwischen den drei Kampfschiffstypen besteht ein bewusst asymmetrisches,
zyklisches Kontersystem. Betrachtet man ein Vergleich auf Basis Baukosten ergibt sich:

- Korvetten schlagen Kreuzer

- Kreuzer schlagen Zerstörer

- Zerstörer schlagen Korvetten

Jeder Typ besitzt einen klaren Vorteil gegenüber einem anderen und eine
relevante Schwäche gegenüber dem dritten. Kein Typ ist gegen beide
anderen gleichermaßen effizient. Eine Flotte aus nur einem Schiffstyp
kann wirtschaftlich effizient sein, besitzt aber eine ausgeprägte
militärische Schwachstelle. Eine robuste Verteidigung erfordert eine
ausgewogene Verfügbarkeit aller drei Kategorien. Damit verbindet sich
wirtschaftliche Spezialisierung unmittelbar mit militärischer
Verwundbarkeit.

---

## 4. Produktionssystem

### Hierarchische Produktion

Schiffe entstehen nicht unmittelbar aus Rohstoffen, sondern über eine
hierarchische Modulstruktur:

**1 Schiff → 10 Module → je 10 Submodule → je 10 weitere Submodule →
... → Rohstoffe**

Diese Hierarchie ist konzeptionell nicht auf eine feste Tiefe
beschränkt.

### Dynamische Skalierung

Die Tiefe der Produktionskette passt sich an Größe und wirtschaftliche
Reife der Spielwelt an. Bei kleiner Spielerschaft kann ein Spieler
vergleichsweise hochstufige Module direkt produzieren; bei großer
Spielerschaft zerfällt dieselbe Produktion in immer feinere
Teilprodukte. Dadurch kann die wirtschaftliche Komplexität mit der
Population wachsen, ohne dass bereits eine kleine Spielwelt Millionen
unterschiedlicher Produzenten benötigt. Man kann problemlos alle Module auch direkt produzieren wenn man die Grundrohstoffe hat. Man produziert dann sozusagen die gesamte Unter-Modul-Ketten hierbei automatisch selbst mit und ist dementsprechend sehr langsam hierbei. Je größer/komplexer/näher das Produkt am Endprodukt ist desto größer ist der Zeitliche nachteil. Es selbst zu bauen. Wenn die 10 Untermodule aus dem ein Modul besteht von 10 Verschiedenen Produzenten hoch spezialisiert gebaut werden plus der Produktion der Endmontage ist die Produktion des Hauptmodul anteilig auf die verschiedenen Produzenten gerechnet deutlich günstiger / schneller als dasselbe Hauptmodul Spezialisert zeitgleich von 11 Spielern zu produzieren.

### Produktionsspezialisierung

Je länger ein Planet dasselbe konkrete Produkt herstellt, desto
effizienter wird die Produktion (kürzere Zeiten, höherer Output,
geringerer Ressourceneinsatz — genaue Mathematik offen). Der Spieler
entscheidet strategisch zwischen häufigem Produktwechsel (Flexibilität)
und langfristiger Spezialisierung (Effizienzvorteil). Nach längerer Zeit bildet sich sozusagen also die komplette Zulieferindustrie heraus. Stellt man später die Produktion auf etwas anderes um, so baut sich ebenso langsam die Wirtschaft wieder um und die Effizienzvorteile auf das vorige Produkt gehen Stück für Stück wieder zurück (dafür besser beim neuen Produkt.)

Neben Schiffen als Endprodukt, gibt es auch Konsumprodukte (für Bevölkerungserhalt/Wachstum) und Baustoffe (für Infrastruktur und Produktionsanlagen). 
Todo: Besseres Wording für Produktkategorie Baustoffe finden

### Arbeitsteilung als Multiplikator

Ein Produktionsschritt muss nicht vollständig von einem Spieler
ausgeführt werden. Zehn spezialisierte Spieler, die je ein Submodul
eines Hauptmoduls herstellen, wirken die Spezialisierungsvorteile auf
zehn getrennte Prozesse aus und sollen dadurch effizienter produzieren
als dieselben Spieler mit jeweils vollständiger Eigenproduktion.

> Arbeitsteilung + Spezialisierung soll einen systemischen
> Effizienzgewinn erzeugen.

Dadurch entsteht ökonomischer Druck zur Kooperation, ohne Kooperation
formal zu erzwingen.
Todo: Genauere Formel / Funktionsweise technisch beschreiben.

### Spieler als wirtschaftliche Nischenakteure

Mit zunehmender Tiefe der Produktionsketten entstehen immer mehr
spezialisierbare Produkte. Ein Spieler muss keine kompletten Schiffe
oder Endprodukte herstellen — ein einzelnes Submodul kann strategische
Bedeutung besitzen. Große Organisationen benötigen dadurch Netzwerke
aus Produzenten statt weniger autarker Industrieplaneten.

---

## 5. Rohstoffe und Geografie

Die Kampfschiffstypen benötigen Rohstoffe aus mehreren
Ressourcenkategorien in unterschiedlicher Gewichtung (z. B. Typ A
benötigt vor allem Ressourcengruppe A) ebenso bei den anderen Produktkategorien - Strategisch wichtig ist dennoch dass die Schiffstypen auch speziell sind bei den Rohstoffen damit Schiffstypen primär in bestimmten Regionen gebaut werden. Alle Ressourcengruppen bleiben
grundsätzlich für alle Schiffstypen relevant; entscheidend ist die
unterschiedliche Gewichtung. Jeder Schiffstyp besitzt damit ein eigenes
wirtschaftliches Ressourcenprofil.

Rohstoffe sind ungleich verteilt: Es existieren großräumige
Ressourcencluster. Fehlende Ressourcen sind nicht regelmäßig auf einem
unmittelbar benachbarten Planeten verfügbar — die Cluster müssen groß
genug sein, um echte regionale Wirtschaftsstrukturen zu erzeugen. Eine
Region mit Überschuss einer Ressource wird ökonomisch zur intensiven
Nutzung dieser Ressource gedrängt. Es soll also jeder Rohstoff auf jedem Planeten existieren nur in sehr unterschiedlichen konzentrationen (unerschöpflich). Je nach region sind einzelne Rohstoffe deutlich stärker konzentriert doch je stärker das Pendel in die eine Richtung ausschlägt, desto stärker fehlt es an den anderen Resourcen.

### Zentrale Designschleife

**Ungleich verteilte Ressourcen → regionale Produktionsvorteile →
Spezialisierung auf bestimmte Produkte und Schiffstypen → höhere
wirtschaftliche Effizienz → größere Abhängigkeit von Handel und anderen
Produzenten → militärische Verwundbarkeit durch das Kontersystem →
Kooperation, Diplomatie, Handel oder Krieg → Veränderung von
Lieferketten und Spezialisierungen**

Die Wirtschaft ist damit kein bloßes Versorgungssystem für das
Militär, sondern selbst zentraler Teil des strategischen Gameplays.

---

## 6. Spezialisierung, Expansion und Konzentration

Die positive Rückkopplung der Spezialisierung ist ausdrücklich gewollt:
Ein Planet, der lange dasselbe Produkt herstellt, baut einen
erheblichen Effizienzvorteil auf. Dieser Vorteil darf aber nicht dazu
führen, dass ein erfolgreicher Spieler zwangsläufig sämtliche
Produktionszweige monopolisiert.

Grundprinzip: **Ein Planet kann jeweils nur eine wesentliche
Produktionsspezialisierung besitzen.** Wer einen weiteren
Produktionszweig mit demselben Spezialisierungsgrad kontrollieren will,
braucht einen weiteren Planeten. Breite vertikale Integration erfordert
somit die Kontrolle vieler Planeten.

Viele Planeten erzeugen eigene Probleme: militärischer Schutz jedes
einzelnen Planeten, gebundene Verteidigungsflotten, zusätzliche
Transportwege, schwierigere politische und administrative Stabilität.
Expansion soll nicht durch einen abstrakten Prozentmalus begrenzt
werden, sondern durch konkrete Sicherheits-, Verwaltungs-, Versorgungs-
und Kontrollaufgaben.

Drei Meta-Prinzipien:

> **Spezialisierung erzeugt zunehmende Effizienz.**
> **Expansion erzeugt zunehmende Komplexität.**
> **Konzentration erzeugt zunehmende Verwundbarkeit.**

---

## 7. Politische Designphilosophie

Für die politische Organisation der Spieler wird **kein klassisches
Allianzsystem als notwendige Kernmechanik** vorausgesetzt. Spieler
können sich außerhalb der vom Spiel vorgegebenen Strukturen frei
organisieren (Namen, Kommunikation, Absprachen, politische Blöcke),
ohne dass das Spiel diese Gruppen formal kennen muss.

Ziel ist nicht: „Spieler treten einer Allianz bei und erhalten deshalb
Vorteile." Ziel ist: „Spieler haben aufgrund ihrer geografischen und
wirtschaftlichen Situation gemeinsame Interessen und kooperieren
deshalb." Das wirkt der Entstehung vollständig homogener
Mega-Allianzen entgegen: Auch ein außerhalb des Spiels organisierter
großer Machtblock besitzt keine automatische wirtschaftliche
Homogenität, weil seine Mitglieder unterschiedliche geografische
Positionen und damit unterschiedliche lokale Interessen haben.

Die kleinste formalisierte politische Beziehung ist die **bilaterale
Vereinbarung zwischen zwei Spielern**. Größere politische Gebilde
entstehen aus vielen solchen Beziehungen sowie informeller Koordination
außerhalb des Spiels. Entscheidend sind die tatsächlichen Beziehungen:
Wer gewährt wem Durchfahrt? Wer erhebt welchen Zoll? Wer benötigt
wessen Handelsroute? Wer schützt welches Gateway?

> Nebula soll soziale und politische Strukturen möglichst nicht durch
> abstrakte Allianzmechaniken vorschreiben. Wirtschaft,
> Ressourcenverteilung, Entfernung, Gateways, bilaterale Zölle und
> militärische Verwundbarkeit sollen stattdessen Situationen erzeugen,
> in denen Kooperation zwischen bestimmten Spielern rational wird und
> zwischen anderen Spielern Interessenkonflikte entstehen.

---

## 8. Sonnensysteme und Gateways

Reisen zwischen Sonnensystemen erfolgen grundsätzlich über **Gateways**,
die Sonnensysteme miteinander verbinden:

**Sonnensystem → Gateway → Sonnensystem → Gateway → Sonnensystem**

Gateways bilden reale strategische Engstellen. Ein Gateway besitzt nur
eine begrenzte Reichweite bzw. eine begrenzte Menge erreichbarer
Systeme (erste Größenordnung: ca. 5–10 Systeme, noch kein
Balancewert). Die Galaxie ist damit ein Netzwerk aus begrenzten
Gateway-Verbindungen, kein vollständig verbundenes Schnellreisenetz.

### Kontrolle durch eine Alien-KI

Gateways gehören keinem Spieler und können nicht erobert oder gekauft werden. Jedes Gateway wird von einer
**Alien-KI** kontrolliert. Damit ein Spieler das Gateway eines Systems
politisch steuern kann, benötigt er das Wohlwollen bzw. die Anerkennung
dieser KI. Militärische Stärke allein reicht dafür nicht aus.

Wer die Gateway-Politik eines Systems bestimmen kann, entscheidet:

- welche Spieler bzw. Flotten das Gateway ansteuern oder durchqueren
  dürfen,
- welche Spieler ausgeschlossen werden,
- welcher Preis bzw. welche Gebühr verlangt wird,
- welche individuellen Konditionen für bestimmte Spieler gelten.

### Zölle als bilaterales Beziehungssystem

Zölle werden bilateral zwischen einzelnen Spielern geregelt, ohne dass
ein übergeordnetes Allianz- oder Handelsblocksystem notwendig ist. Der
Kontrolleur eines Gateways definiert einen **Standardzollsatz pro
Frachter**, der gegenüber allen Spielern ohne individuelle Vereinbarung
gilt. Beispiel:

- Spieler X: 0 %
- Spieler Y: 2 %
- Spieler Z: 5 %
- alle Spieler ohne Vertrag: 8 %

Verträge sind nicht automatisch übertragbar: Vereinbart A mit B 0 % und
B mit C ebenfalls 0 %, entsteht daraus kein Vertrag zwischen A und C.

### Warum Nachbarn natürliche Partner werden

Benachbarte Systeme im selben Ressourcencluster besitzen tendenziell
ähnliche Rohstoffe und produzieren deshalb ähnliche Güter — sie sind
oft nicht die attraktivsten Abnehmer füreinander. Ein Produzent will
seinen Überschuss aus dem Cluster heraus in entferntere, knappere
Märkte transportieren, muss dafür aber die Gateways seiner unmittelbaren
Nachbarn passieren. Daraus entsteht ein wechselseitiger Anreiz, direkte
Nachbarn nicht oder nur gering zu besteuern: Der eigene Nachbar
benötigt meine Route für seine Exporte, ich benötige seine für meine
eigenen. Nachbarn können wirtschaftliche Konkurrenten sein und trotzdem
ein starkes gemeinsames Interesse an günstigem Transit besitzen.

Bei weiter entfernten Händlern, die ein Gateway nur als Transitstrecke
benötigen, besteht diese Gegenseitigkeit nicht in gleicher Weise —
entfernte Handelsreisende können deshalb rational stärker besteuert
werden.

### Lokale Interessengemeinschaften

Aus den bilateralen Zollbeziehungen bildet sich ohne formale
Allianzmechanik eine lokale politische Struktur heraus: Spieler einer
Region haben gemeinsame Interessen an niedrigen gegenseitigen Zöllen
und gemeinsamer Sicherung ihrer Handelswege. Mit zunehmender Entfernung
verändert sich die Interessenlage — an den Grenzen solcher
Interessengemeinschaften entstehen bewusst Spannungen: Ein Spieler am
Rand einer Region kann wirtschaftlich stark von einem angrenzenden
Spieler der Nachbarregion abhängig sein und deshalb andere Interessen
verfolgen als Spieler weiter innen. Die Begrenzung von Mega-Allianzen
erfolgt dadurch, dass politische und wirtschaftliche Interessen lokal
bleiben und sich mit der geografischen Position verändern — nicht durch
eine maximale Mitgliederzahl.

### Gateways als militärische Machtpositionen

Gateways können militärisch blockiert werden. Ein Krieg muss deshalb
nicht auf die Eroberung eines Produktionsplaneten abzielen — die
Kontrolle oder Abschneidung einer wichtigen Handelsverbindung kann
strategisch bereits entscheidend sein. Ein hoch spezialisierter Planet
kann extrem effizient produzieren, aber von wenigen Handelswegen
abhängig sein: wirtschaftliche Stärke erzeugt damit zugleich konkrete
Verwundbarkeit.

Erweiterte Systemkette:

**Ressourcencluster → regionale Spezialisierung →
Produktionsüberschüsse → Fernhandel → Gateway-Abhängigkeit →
bilaterale Zollverträge → lokale Interessengemeinschaften →
Grenzspannungen → Konflikte um Handelswege**

---

## 9. Trägerschiffe und alternative Fernreise

Neben Gateways existiert eine zweite, erheblich aufwendigere Möglichkeit
interstellarer Reise: **Trägerschiffe**. Sie besitzen selbst keine bzw.
praktisch keine Angriffs- und Kampfwerte; ihre Funktion besteht darin,
andere Schiffe (Frachter, Kampfschiffe A/B/C) aufzunehmen und den Raum
zwischen Sonnensystemen ohne Nutzung eines freigegebenen Gateways zu
überwinden.

Eine Reise per Trägerschiff dauert deutlich länger als über ein
Gateway (erste Größenordnung: Faktor 10, kein Balancewert) und ist
selbst sehr teuer. Prinzip:

> Gateways sind der normale und effiziente Verkehrsweg. Trägerschiffe
> sind die teure, langsame und riskante Alternative, wenn politische
> Zugangsrechte fehlen.

Dadurch bleibt Gateway-Kontrolle strategisch wertvoll, ohne absolute
Mauern zwischen Sonnensystemen zu erzeugen — kein politischer Akteur
kann ein System vollständig unerreichbar machen.

### Militärisches Risiko: gestrandete Flotten

Da Trägerschiffe kaum Kampfkraft besitzen, können Verteidiger sie
priorisiert angreifen. Werden die Trägerschiffe einer Angriffsflotte
zerstört, verlieren die verbliebenen Kampfschiffe möglicherweise jede
Möglichkeit, das fremde System ohne Gateway wieder zu verlassen — die
Flotte ist gestrandet. Ein taktischer Sieg kann dadurch zu einem
strategischen Problem werden. Der Angreifer müsste neue oder
zusätzliche Trägerschiffe produzieren oder kaufen, eine zweite
Transportflotte entsenden, Gatewayzugang aushandeln oder auf andere
Weise die gestrandete Flotte versorgen und zurückholen. Ein Angriff auf
ein abgeschottetes System bleibt dadurch grundsätzlich möglich, aber
teuer und riskant.

---

## 10. Flottenunterhalt

Flotten verursachen während ihres Einsatzes laufende Kosten (Unterhalt,
Sold, Einsatzvergütung). Eine Flotte kann nicht kostenlos unbegrenzt
weit entfernt von ihrer Heimat operieren. Je länger ein Einsatz dauert,
desto höher werden die Kosten — eine kurzfristige
Verteidigungsstationierung ist günstig, ein langer Einsatz weit
entfernt von der Heimat zunehmend teuer (ob ein Maximum existiert, ist
offen). Militärische Reichweite erhält dadurch einen wirtschaftlichen
Preis.

Diese Mechanik verstärkt das Problem gestrandeter Flotten: Eine Flotte
ohne funktionierende Trägerschiffe wird nicht nur militärisch gebunden,
sondern zunehmend zur wirtschaftlichen Belastung.

---

## 11. Heimatplaneten und Kolonien

### Status des Heimatplaneten

Der Heimatplanet eines Spielers unterscheidet sich grundsätzlich von
später erworbenen oder gegründeten Kolonien. Er ist der Ursprung des
Spielers, sein erster großer Vermögenswert, sein zentraler
Identifikationspunkt, normalerweise stark bevölkert, infrastrukturell
weit entwickelt und politisch besonders etabliert. Gegenüber der
lokalen Alien-KI besitzt der Heimatspieler eine besonders starke
Sympathie bzw. Legitimität.

### Kein mechanischer Sonderschutz — Schutz durch Bevölkerungsloyalität

Ein Heimatplanet besitzt keinen künstlichen Schutzmechanismus (kein
Schild, keine Unangreifbarkeit). Er kann grundsätzlich genauso erobert,
blockiert und besetzt werden wie jeder andere Planet.

Der tatsächliche Schutz entsteht aus der Bevölkerung selbst. Bevölkerung
besitzt neben ihrer Größe eine weitere Eigenschaft: **Loyalität**
gegenüber der bestehenden Herrschaft. Ein Heimatplanet unterscheidet
sich von einer gewöhnlichen Kolonie dadurch, dass er über lange Zeit
gewachsen ist und deshalb typischerweise eine sehr große und sehr
loyale Bevölkerung besitzt.

Nach einer militärischen Eroberung beginnt eine Besatzungsphase, deren
Dauer und Kosten von Bevölkerungsgröße und Loyalität abhängen: Eine
kleine, wenig loyale Kolonie lässt sich vergleichsweise schnell
befrieden; ein großer, hoch loyaler Heimatplanet erfordert eine sehr
lange Befriedungsphase. Während dieser Phase leistet die Bevölkerung
aktiven Widerstand: laufende reale Verluste beim Besatzer, vergleichbar
mit einer Aufstandsbekämpfung, deren Rate mit fortschreitender
Befriedung graduell sinkt, aber nicht sofort auf null fällt.

> Bevölkerungsgröße und Loyalität erzeugen Widerstandsfähigkeit.
> Widerstandsfähigkeit erzeugt Besatzungsdauer und Besatzungsverluste.
> Besatzungsverluste erzeugen ökonomische Kosten für den Eroberer.

Ein Heimatplanet ist dadurch kein unangreifbarer Sonderfall, sondern
der ökonomisch unattraktivste denkbare Eroberungsfall: hoher Aufwand,
hohe laufende Verluste, sehr lange Bindung von Bodentruppen bei
vergleichsweise geringem kurzfristigem Ertrag. Ein rational handelnder
Angreifer bevorzugt deshalb typischerweise lohnendere Ziele, ohne dass
das Spiel ihm die Eroberung formal verbietet. Heimatwelten begrenzen
dadurch strukturell auch das Wachstum großer Imperien: In fremden,
eroberten Systemen besitzt ein Spieler nicht automatisch dieselbe
Beziehung zur lokalen Gateway-KI wie ein dort beheimateter Spieler.

### Kolonisation

Unbewohnte oder freie Planeten können kolonisiert werden — das
unterscheidet sich grundsätzlich von der militärischen Übernahme eines
bereits bewohnten Planeten. Bei einer Kolonisation muss kein
bestehender Staat unterworfen werden; der Spieler errichtet eine neue
Siedlung und baut sie über Zeit aus. Eine junge Kolonie beginnt mit
sehr geringer Bevölkerung, geringer Infrastruktur, geringer
Produktionskapazität und starkem Entwicklungsbedarf.

Bevölkerungswachstum ist an reale Entwicklung gebunden — nicht an
einen abstrakten Timer. Es benötigt eine funktionierende
Lebensgrundlage: Wohnraum, Energie, Nahrung, Konsumgüter, medizinische
Versorgung, Verkehrs- und planetare Infrastruktur, Sicherheit,
wirtschaftliche Perspektiven. Je besser ein Planet entwickelt und
versorgt wird, desto stärker kann seine Bevölkerung wachsen. Bevölkerung
ist damit ein Ergebnis wirtschaftlicher Entwicklung.

### Kolonien in fremden Systemen erzeugen politische Abhängigkeit

Ein Planet in einem fremden System zu besitzen bedeutet nicht
automatisch, das dazugehörige Sonnensystem oder dessen Verkehrswege zu
kontrollieren. Um Waren einer Kolonie zu transportieren, benötigt der
Besitzer weiterhin Zugang zum lokalen Gateway und muss mit dem
jeweiligen lokalen Machthaber zurechtkommen — einem anderen Spieler
oder auch einem NPC. Kolonialexpansion soll deshalb keine automatische
Verkehrsherrschaft erzeugen: Große Reiche können wirtschaftlich und
militärisch mächtig sein, bleiben aber auf lokale Beziehungen, Verträge
und politische Stabilität angewiesen.

---

## 12. Herrschaft, Bevölkerung und politisches Gewicht

Der Spieler ist der souveräne Herrscher seiner planetaren Ordnung — ein
Planet soll sich tatsächlich wie sein Besitz und Herrschaftsraum
anfühlen. Gleichzeitig ist die Bevölkerung eine eigenständige Größe
innerhalb dieser Herrschaft:

> Der Spieler beherrscht den Planeten, aber die Bevölkerung ist eine
> eigenständige Größe innerhalb dieser Herrschaft.

Militärische Kontrolle über einen Planeten bedeutet nicht automatisch
vollständige politische Integration. Es lassen sich unterschiedliche
Zustände unterscheiden: eigener etablierter Planet, eigene junge
Kolonie, militärisch besetzter Planet, langfristig integrierter
eroberter Planet, Heimatplanet eines anderen Spielers unter Besatzung.

Die politische Anerkennung durch die Gateway-KI eines Sonnensystems
richtet sich nach der **Bevölkerungsgröße unter etablierter Herrschaft**
eines Spielers, nicht nach der bloßen Zahl kontrollierter Planeten. Ein
Spieler mit drei beinahe leeren Kolonien besitzt deshalb nicht
automatisch mehr politischen Einfluss als ein Spieler mit einer
einzigen hochentwickelten Heimatwelt. Das verhindert insbesondere, dass
ein mächtiger Spieler ein fremdes System einfach durch die schnelle
Gründung vieler Minimal-Kolonien politisch übernimmt.

Eine neue Kolonie besitzt anfangs nur wenige Einwohner und deshalb
wenig politisches Gewicht. Wer ein fremdes System politisch übernehmen
möchte, muss seine Kolonien ausbauen, versorgen und attraktiv machen,
bis dort genügend Bevölkerung lebt, um die bestehende Machtbalance zu
verändern — eine politische Übernahme ist dadurch möglich, aber
langfristig und teuer.

### Blockaden als indirektes politisches Werkzeug

Da Kolonien Versorgung benötigen, können militärische Blockaden eine
wichtige Rolle bei der politischen Kontrolle eines Systems spielen.
Blockiert werden können insbesondere Konsumgüter, Baumaterialien,
Versorgungsgüter, Verstärkungen und wirtschaftliche Importe. Eine
Blockade wirkt nicht über einen abstrakten politischen Malus, sondern
über die konkrete Grundlage des Bevölkerungswachstums:

> Warenströme ermöglichen Entwicklung.
> Entwicklung ermöglicht Bevölkerung.
> Bevölkerung erzeugt politisches Gewicht.
> Politisches Gewicht beeinflusst die Gateway-KI.

---

## 13. Militärische Auseinandersetzungen und Eroberung

### Kämpfe an konkreten strategischen Orten

Flotten verfolgen und fangen sich nicht beliebig im freien
interplanetaren oder interstellaren Raum ab — der Weltraum ist zu groß,
und zufälliges Abfangen wäre schwer nachvollziehbar. Militärische
Begegnungen entstehen grundsätzlich an konkreten strategischen Orten,
vor allem bei der **Blockade eines Planeten**: Eine Flotte kann einen
Planeten blockieren, eine andere Partei kann versuchen, diese Blockade
zu durchbrechen — in diesem Moment kommt es zur Schlacht. Dasselbe Prinzip
gilt für Handelsflotten: Wer einen Handelsstrom angreifen will, muss
einen relevanten Ort kontrollieren oder blockieren, den dieser
Handelsstrom passieren muss.

### Raumkampf und Eroberung als getrennte Phasen

Der Sieg einer Flotte über die Verteidigung eines Planeten tauscht
nicht unmittelbar den Eigentümer aus. Der Raumkampf entscheidet
zunächst über Zugang zum Planeten und Kontrolle des Orbits bzw. der
Blockade. Für die tatsächliche Übernahme sind **Bodentruppen bzw.
Besatzungskräfte** notwendig.

Bodentruppen müssen kein eigenständiges Strategiespiel mit zahlreichen
Einheitentypen werden. Ihre zentrale Funktion: einen Planeten nach
militärischer Öffnung besetzen, bestehende politische Strukturen
kontrollieren, Widerstand niederhalten, die neue Herrschaft
aufrechterhalten. Die Flotte gewinnt den Zugang, die Bodentruppen
halten die Besatzung.

### Mehrstufiger Eroberungsprozess

1. Planet blockieren.
2. Verteidigende Flotte besiegen oder vertreiben.
3. Bodentruppen und Versorgung heranführen.
4. Planetare Schlüsselpositionen besetzen.
5. Besatzungsmacht aufrechterhalten (inkl. Widerstand der Bevölkerung,
   siehe Abschnitt 11).
6. Bestehende Bevölkerung und Infrastruktur kontrollieren.
7. Langfristig eine neue politische Ordnung etablieren.
8. Erst später vollständige Integration erreichen.

> Einen Planeten militärisch zu schlagen soll erheblich leichter sein,
> als ihn dauerhaft zu besitzen.

Besatzung bindet dauerhaft Ressourcen: Bodentruppen, Geld, Versorgung,
Transportkapazität, Verwaltung, ggf. absichernde Flotten. Eine große
Zahl schnell eroberter Planeten kann so zur Belastung werden — eine
natürliche Grenze für aggressive Expansion, ohne einen abstrakten
„Imperiums-Malus".

Integration ist langsamer als Eroberung. Mögliche Einflussfaktoren:
Dauer der Besatzung, Qualität der Versorgung, Wiederaufbau,
wirtschaftliche Entwicklung, Sicherheit, Lebensstandard, Größe der
Besatzungskräfte, Verhalten des Eroberers gegenüber der Bevölkerung.

> Militärische Kontrolle entsteht schnell. Dauerhafte politische
> Herrschaft entsteht langsam.

### Beschlagnahmung im Handelskrieg

Gewinnt eine blockierende Flotte gegen die Eskorte einer Handelsflotte,
können die transportierten Waren der gegnerischen Frachter
beschlagnahmt und dem Sieger einverleibt werden. Die Beute entspricht
den tatsächlich transportierten Gütern, nicht einem abstrakten
Zufallsbonus. Ein Sieger kann ein Interesse daran haben, Frachter und
Ladung möglichst unbeschädigt zu übernehmen — die genaue Mechanik
(Kapitulation, Entern, automatische Übergabe) ist offen. Blockierende
Flotten können unterschiedliche Einsatzregeln erhalten, z. B.: feindliche
Kampfschiffe werden bekämpft, Frachter nach Möglichkeit aufgebracht.

> Handelskrieg soll die Kontrolle realer Warenströme ermöglichen.

---

## 14. Einstieg neuer Spieler und Gateway-Entdeckung

Ein neuer Spieler ist nicht sofort Teil der galaktischen Politik. Sein
Heimatsystem ist zu Beginn nicht in das aktive Gateway-Netz eingebunden
— andere Spieler können es nicht regulär ansteuern. Der neue Spieler
entwickelt in diesem lokalen, isolierten Raum zunächst seinen
Heimatplaneten und lernt die Grundlagen der Spielmechanik kennen.

Das eigene Sonnensystem reicht für die frühe Selbstverwirklichung: Der
Spieler kann seinen Heimatplaneten ausbauen, weitere Planeten des
Systems erforschen und kolonisieren, neue Produktionsstandorte
entwickeln, innerplanetare und interplanetare Logistik aufbauen und
seine wirtschaftliche Spezialisierung entdecken — bevor er überhaupt
mit der gesamten Galaxie konfrontiert wird.

### Entdeckung statt Levelsprung

Der Eintritt in die galaktische Gemeinschaft ist nicht an eine
abstrakte Zivilisationsstufe gekoppelt. Das Gateway existiert bereits
im Heimatsystem (z. B. nahe der Sonne), ist aber zunächst unbekannt. Im
Verlauf der Erforschung des eigenen Systems, der Kolonisation weiterer
Planeten und wissenschaftlicher Arbeit stoßen Wissenschaftler auf
Hinweise, die zur Entdeckung des Gateways führen. Die Entdeckung ist
damit Folge tatsächlicher Erforschung, kein künstlicher Levelsprung.

### Aktivierung als Eintritt in die Galaxie

Nach Entdeckung muss das Gateway erforscht und aktiviert werden. Erst
mit der Aktivierung wird das Heimatsystem Teil des galaktischen
Gateway-Netzes: Der Spieler kann andere Systeme erreichen, andere
Spieler können sein System grundsätzlich erreichen, interstellare
Handelsbeziehungen, Gateway-Gebühren, politische Beziehungen und
regionale Märkte werden relevant.

Ein Sonnensystem erscheint auf der Galaxiekarte anderer Spieler und
wird für sie grundsätzlich ansteuerbar erst, nachdem ein Spieler
innerhalb dieses Systems dessen Gateway aktiviert hat — unabhängig
davon, ob er es danach für sich, für ausgewählte Spieler oder für alle
öffnet. Vor der Aktivierung ist ein System nicht nur politisch
isoliert, sondern für Außenstehende vollständig unbekannt: keine Route,
keine Kartenmarkierung, keine Ansteuerbarkeit — auch nicht per
Trägerschiff, da dafür bereits die Kenntnis der Zielkoordinaten
vorausgesetzt wäre.

Die schrittweise Entdeckung dient zugleich als natürliche
Tutorial-Struktur: Neue Mechaniken (Gateway-Politik, interstellarer
Handel, Zölle, fremde Spieler, Diplomatie, Blockaden, Trägerschiffe,
militärische Machtprojektion, regionale Märkte) werden nicht über ein
künstliches Tutorial-Menü eingeführt, sondern durch die Entwicklung der
eigenen Zivilisation freigeschaltet.

### Schutz neuer Spieler durch Weltlogik statt künstlicher Regel

Wenn ein neuer Spieler sein Gateway erstmals aktiviert, verfügt sein
Heimatsystem bereits über eine entwickelte Heimatwelt und ggf. eigene
Kolonien — seine politische Ordnung besitzt dadurch den überwiegenden
Teil der lokalen Bevölkerung, und die Gateway-KI erkennt ihn zunächst
als maßgebliche lokale Macht an. Er kann direkt nach der Aktivierung
entscheiden, wen er durch das Gateway lässt, und kann fremde Spieler
zunächst vollständig blockieren.

Ein mächtiger Nachbar kann seine Hauptflotte deshalb nicht einfach
durchs Gateway schicken, sondern müsste auf die langsamere, teurere,
riskantere Trägerschiff-Variante ausweichen. Der Schutz ist nicht
absolut — ein Angriff bleibt physisch möglich —, aber unverhältnismäßig
aufwendiger. Kein künstlicher Zeitschutz („30 Tage unangreifbar") ist
dafür nötig; der Schutz ergibt sich aus:

1. Unauffindbarkeit des Heimatsystems vor Aktivierung.
2. Zeit zur Entwicklung von Heimatplanet und weiteren Systemplaneten
   vor Aktivierung.
3. Automatischem politischem Übergewicht durch lokale Bevölkerung bei
   Aktivierung.
4. Möglichkeit, das Gateway gegenüber Fremden zu sperren.
5. Notwendigkeit der teuren, langsamen Trägerschiff-Route für
   Angreifer.
6. Notwendigkeit von Blockade, Bodentruppen, Besatzung und langfristiger
   Integration für eine tatsächliche Übernahme.

### Wachstum der Galaxie

Auch wenn das eigene System für die frühe Entwicklung ausreicht, muss
die Galaxie mit der Spielerzahl wachsen können. Neue Regionen können
dynamisch entstehen, um genügend Heimatsysteme bereitzustellen, neue
lokale Wirtschafts- und Politikräume zu schaffen und eine vollständige
Verdichtung durch sehr alte Spieler zu verhindern. Wie neue Regionen an
das bestehende Gateway-Netz angebunden werden, ist offen.

---

## 15. Handelsgilde und Handelsposten

### Die Handelsgilde als neutrale Institution

Neben Spielern, lokalen Herrschern und der Alien-Gateway-KI existiert
die **Handelsgilde** als politisch neutrale, übergeordnete Institution.
Sie betreibt die Infrastruktur, über die ein erheblicher Teil des
organisierten galaktischen Handels abgewickelt wird. Ihre Einrichtungen
gehören keinem einzelnen Spieler und können nicht durch militärische
Eroberung privatisiert werden.

### Lokale Handelsposten

Sobald das Gateway eines Sonnensystems entdeckt und aktiviert wird,
richtet die Handelsgilde dort automatisch einen lokalen Handelsposten
ein — die Gateway-Aktivierung ist damit gleichzeitig der Eintritt des
Systems in die organisierte galaktische Handelswirtschaft. Der lokale
Handelsposten bildet vor allem die Wirtschaft seines Sonnensystems ab:
lokale Rohstoff- und industrielle Produktion, Konsum der Bevölkerung,
Nachfrage der dortigen Kolonien, Importe und Exporte. Dadurch bilden
sich von System zu System deutlich unterschiedliche Preise.

### Sektorale Handelsstationen

Zusätzlich existiert eine zweite Handelsebene: **sektorale
Handelsstationen** der Handelsgilde, die sich in regelmäßigen Abständen
in eigenen, meist unbedeutenden Sternsystemen ohne reguläre bewohnbare
Planeten befinden. Sie gehören der Handelsgilde und können nicht
kolonisiert oder dauerhaft von einem Spieler übernommen werden. Da sie
nur über eine begrenzte Zahl von Gateway-Verbindungen erreichbar sind,
entstehen natürliche Wirtschaftssektoren: Mehrere Sonnensysteme
orientieren sich an derselben sektoralen Handelsstation, wodurch eine
zweite Preisbildungsebene (lokal vs. sektoral) entsteht.

Bei der Erzeugung bzw. Erweiterung der Galaxie muss sichergestellt
werden, dass jedes relevante Gateway-Gebiet mindestens eine sektorale
Handelsstation sinnvoll erreichen kann — kein Spieler soll vollständig
vom organisierten galaktischen Handel abgeschnitten sein.

### Garantierter physischer Zugang

Ein neutraler Handelsposten — lokal wie sektoral — kann von keinem
Akteur beschlagnahmt, blockiert oder für einzelne Spieler gesperrt
werden, auch nicht vom Spieler, der das jeweilige Sonnensystem
politisch kontrolliert. Was ein Systemherrscher tatsächlich
kontrollieren kann, ist ausschließlich die Nutzung des Gateways: wer es
passieren darf und zu welchen Konditionen. Das Gateway ist damit der
einzige Hebel politischer Kontrolle, nicht der Handelsposten selbst.

Der langsame, teure Weg über Trägerschiffe bleibt unabhängig von jeder
Gateway-Politik nutzbar. Ein Spieler, dem das Gateway verweigert wird,
kann sein Ziel dennoch über Trägerschiffe erreichen — langsam und
riskant, aber ohne dass ihm dieser Weg verwehrt werden kann.
Zusammengefasst:

> **Gateway:** schnell, aber politisch kontrollierbar.
> **Trägerschiff:** langsam und teuer, aber politisch nicht
> kontrollierbar.
> **Der Handelsposten selbst:** immer erreichbar, sobald der Spieler auf
> irgendeinem der beiden Wege physisch im System angekommen ist.

Damit kann selbst eine vollständige Gateway-Blockade durch alle
Nachbarn und den Systemherrscher einen Spieler nicht vollständig
wirtschaftlich isolieren: Kein Kartell kann ihn vollständig aus dem
Handel drängen.

### Geografischer Wert und wichtige Verkehrsknoten

Nicht nur Rohstoffe und Planeten bestimmen den Wert eines
Sonnensystems, sondern auch seine Position im Gateway-Netz. Ein System
mit durchschnittlichen Rohstoffen kann extrem wertvoll sein, wenn es
auf einer wichtigen Route zwischen Handelszentren liegt. Besonders
wertvoll sind Systeme mit Zugang zu zwei oder drei sektoralen
Handelsstationen — sie können als Abkürzung zwischen unterschiedlichen
Handelsräumen fungieren. Solche geografischen Unterschiede müssen nicht
weggebalanciert werden; sie erzeugen bewusst politische und
wirtschaftliche Geschichten.

Ein Spieler, der ein strategisch wichtiges System kontrolliert, besitzt
dadurch nicht automatisch die Waren durchreisender Händler, kann aber
die Konditionen seines Gateways beeinflussen und dadurch erhebliche
Einnahmen aus Transitverkehr erzielen.

### Möglicher Schutz um sektorale Handelsstationen

Denkbar, aber noch nicht abschließend entschieden, ist ein direkter
militärischer Schutz des unmittelbaren Raums um sektorale
Handelsstationen durch die Handelsgilde selbst, damit ein Großspieler
keine zentrale Handelsstation dauerhaft blockieren und damit einen
ganzen regionalen Markt faktisch privatisieren kann.

---

## 16. Physischer Warentransport und Logistik

Grundprinzip:

> Eine Ware befindet sich immer an einem konkreten Ort. Handel
> teleportiert keine Güter.

Wird eine Ware gekauft, wechselt zunächst nur der Eigentümer — sie
bleibt physisch am Ort des Kaufs, bis sie tatsächlich transportiert
wird. Kauf und Transport sind bewusst getrennte Vorgänge. Nach dem Kauf
muss der Spieler geeignete Frachter bereitstellen, die Ware verladen,
eine Route bestimmen, notwendige Gateways passieren, ggf. Gebühren
zahlen, mögliche Blockaden berücksichtigen, den Zielmarkt erreichen und
die Ware dort ausladen und anbieten.

### Frachter und Handelsflotten

Frachter bilden neben den drei Kampfschiffstypen und den Trägerschiffen
eine eigenständige Schiffskategorie; ihre Aufgabe ist der Transport
physischer Güter. Ohne ausreichende Frachterkapazität kann ein Spieler
nicht beliebig große Warenmengen bewegen — Transportkapazität ist
selbst eine wirtschaftliche Ressource. Eine Handelsflotte kann Frachter
mit Kampfschiffen als Eskorte kombinieren: Mehr Frachter erhöhen die
transportierte Menge, mehr Kampfschiffe erhöhen die Sicherheit,
verbrauchen aber Produktionskapazität und verursachen Unterhalt, ohne
selbst Waren zu transportieren.

Es gibt keine automatische Marktverbindung, bei der Waren nach
Vertragsschluss unsichtbar zwischen zwei Handelsposten übertragen
werden — auch eine dauerhaft eingerichtete Handelsroute benötigt reale
Flotten. Automatisierung kann bedeuten, dass ein Spieler einer Flotte
wiederkehrende Befehle erteilt, aber nicht, dass der physische Transport
entfällt.

### Handelsrouten als wirtschaftliche Entscheidung

Da Waren tatsächlich transportiert werden, lassen sich Routen
vergleichen: Eine kurze Route kann hohe Gateway-Gebühren besitzen, eine
längere niedrigere, eine dritte politisch instabil oder militärisch
riskant sein. Relevant sind u. a. Transportzeit, Flottenunterhalt,
Gateway-Gebühren, Anzahl der Sprünge, politische Beziehungen,
Blockaden, militärisches Risiko, mögliche Umwege.

### Informationen über Transporte

Da reale Waren bewegt werden, können Informationen über diese
Transporte selbst wertvoll werden — Beobachtung von Handelsströmen,
Spionage, Gerüchte, Verrat, Weitergabe von Flotteninformationen,
Verschleierung wertvoller Transporte. Diese Systeme sind noch nicht
konkretisiert, ergeben sich aber natürlich aus dem physischen
Handelsmodell.

---

## 17. Preisbildung, Arbitrage und Spekulation

Jeder Handelsposten besitzt einen eigenen Markt; Preise bilden sich aus
Angebot und Nachfrage. Ein Gut, das in einem Rohstoffcluster im
Überfluss vorhanden ist, kann dort billig sein und in einem entfernten
Cluster teuer.

### Arbitrage

Ein Händler kann versuchen, eine Ware am günstigen Markt zu kaufen und
am teureren zu verkaufen. Der tatsächliche Gewinn hängt davon ab, ob
die Preisdifferenz größer ist als die Transportkosten:

> Handelsgewinn = Preisunterschied − Transportkosten − Gateway-Gebühren
> − sonstige Risiken und Kosten

### Spekulation

Spieler können Waren auch aufgrund erwarteter zukünftiger
Preisentwicklungen kaufen — etwa weil sie einen bevorstehenden Krieg
vermuten und deshalb steigende Nachfrage nach Kampfschiffen, Modulen,
Rohstoffen, Treibstoff, Frachtraum oder Konsumgütern erwarten.
Politische und militärische Informationen werden dadurch unmittelbar
wirtschaftlich relevant. Ein Spieler kann wirtschaftlich davon
profitieren, wenn er politische Entwicklungen früher erkennt als
andere, gute Beziehungen zu entfernten Regionen besitzt, bevorstehende
Konflikte richtig einschätzt, Produktionsausfälle oder neue
Handelsrouten erkennt oder Änderungen bei Gateway-Gebühren vorhersieht.

Preise sollen möglichst nicht durch künstliche Ereignismodifikatoren
beeinflusst werden, sondern durch tatsächliches Spielerverhalten: mehr
Bestellungen, mehr Verbrauch, verlorene Handelsrouten, blockierte
Produktionswelten, beschlagnahmte Waren, gebundene Frachterkapazität.

### Wirtschaftlicher Anreiz zur Gateway-Öffnung

Solange ein Spieler sein Gateway nicht aktiviert, ist sein System
weitgehend isoliert und sicher, kann seine Waren aber nicht zu
vertretbaren Kosten zu überregionalen Märkten bringen. Mit
zunehmender Spezialisierung produziert die lokale Wirtschaft irgendwann
mehr, als sie selbst sinnvoll verbrauchen kann — daraus entsteht aus
eigenem wirtschaftlichem Interesse der Wunsch zur Aktivierung:

> Isolation bietet Sicherheit. Öffnung bietet Wohlstand und Wachstum.

Kein künstlicher Aktivierungsanreiz (z. B. Tutorial-Timer) ist dafür
nötig.

---

## 18. Geldsystem

Ausgangspunkt ist bewusst kein klassisches Faucet-and-Sink-System:
Credits werden im normalen Spiel nicht laufend erzeugt oder vernichtet,
sondern zirkulieren zwischen Spielern und Bevölkerungen.

### Grundregeln

- Bei Entstehung eines neuen Einwohners entsteht grundsätzlich 1
  Credit, zunächst der Bevölkerung bzw. ihrer abstrahierten Kaufkraft
  zugerechnet.
- Normale Transaktionen erzeugen kein neues Geld.
- Es gibt vorerst keine Steuern und keinen simulierten Arbeitsmarkt.
- 1 Arbeitseinheit kostet 1 Credit.
- Mehr Bevölkerung ermöglicht mehr gleichzeitig nutzbare Arbeit und
  damit schnellere bzw. umfangreichere Produktion; Produktionsanlagen
  begrenzen zusätzlich, wie viel technisch produziert werden kann.

### Lohn, Konsum und Kreislauf

Produktionslohn ist kein Geldvernichter: Benötigt eine Produktion 100
Arbeitseinheiten, zahlt der Spieler 100 Credits, die der Bevölkerung
als Kaufkraft zugerechnet werden (**Spieler → Lohn → Bevölkerung**). Die
Bevölkerung kauft mit dieser Kaufkraft reale Konsumgüter zu
Marktpreisen (**Bevölkerung → Geld → Verkäufer**). Das Konsumgut wird
beim Verbrauch vernichtet, der Credit nicht. Nicht ausgegebene Kaufkraft
bleibt bei der Bevölkerung erhalten.

Eigenversorgung erzeugt dabei kein neues Geld: Zahlt ein Spieler Löhne
und verkauft seiner eigenen Bevölkerung anschließend Güter im selben
Wert, besitzt er am Ende denselben Betrag zurück — er hat lediglich
Rohstoffe, Produktionskapazität und Zeit eingesetzt, um seine
Bevölkerung zu versorgen.

Im Handel zwischen Spielern wandert Kaufkraft dagegen zwischen Akteuren:
Zahlt Spieler A Lohn, kann ein spezialisierter Spieler B Konsumgüter an
die Bevölkerung von A verkaufen und dadurch Credits von A abziehen. A
muss seinerseits exportieren, um Geld aus anderen Wirtschaftsräumen
anzuziehen. Autarkie bleibt technisch möglich, wird aber wegen der
langfristigen Spezialisierungsvorteile wirtschaftlich zunehmend
ineffizient.

### Flottenunterhalt, Gebühren und Transfers

Flottenunterhalt vernichtet kein Geld, sondern wird an die Bevölkerung
in der Region bzw. dem Cluster ausgeschüttet, in dem sich die Flotte
aufhält (**Spieler → Flottenunterhalt → regionale Bevölkerung →
Konsumnachfrage**). Eine starke militärische Präsenz kann dadurch einen
lokalen Wirtschaftsboom erzeugen.

Gateway-Gebühren sind Transfers zwischen Spielern: Zahlt A zehn Credits
für die Durchreise durch ein von B kontrolliertes Gateway, erhält B
diese zehn Credits. Auch Gebühren neutraler Institutionen wie der
Handelsgilde sollen möglichst nicht vernichtet werden — denkbar sind
regionale Rückverteilung oder ein abstrahierter Gildenhaushalt.

### Geldschöpfung: historischer Bevölkerungshöchststand

Damit Geldschöpfung nicht beliebig wiederholbar wird, entstehen neue
Credits nur für Bevölkerung **oberhalb des bisherigen historischen
Höchststandes eines Planeten**. Wächst ein Planet erstmals von 8 auf 10
Milliarden Einwohner, entsteht für die zusätzlichen 2 Milliarden neue
Geldbasis. Fällt er danach auf 7 Milliarden zurück und wächst erneut auf
10 Milliarden, entsteht kein neues Geld — erst beim Wachstum über den
alten Höchststand hinaus (z. B. auf 11 Milliarden) entsteht wieder neue
Geldbasis. Dieselbe Bevölkerung kann dadurch nicht wiederholt zur
Geldschöpfung verwendet werden.

Beim Tod eines Einwohners wird kein Credit automatisch vernichtet: Der
ursprünglich geschaffene Credit kann längst einem Händler in einem
anderen Sektor gehören, eine nachträgliche Vernichtung wäre künstlich.
Die Geldmenge entspricht damit eher der historischen wirtschaftlichen
Expansion als der aktuellen Bevölkerung. Nach einer massiven
Bevölkerungskatastrophe kann relativ viel Geld auf weniger Bevölkerung
und Waren treffen — eine daraus entstehende Inflation ist eine mögliche
emergente Folge. Der historische Höchststand verhindert dabei, dass der
bloße Wiederaufbau derselben Bevölkerung erneut Geld erzeugt.

### Vorläufiges Geldmodell (Zusammenfassung)

1. Neue dauerhaft zusätzliche Bevölkerung erzeugt neue Geldbasis.
2. Produktion überträgt Credits vom Spieler an die Bevölkerung.
3. Bevölkerung verwendet Credits zum Kauf realer Konsumgüter.
4. Konsum vernichtet Waren, nicht Geld.
5. Spielerhandel verteilt Credits zwischen Spielern und Regionen.
6. Gateway-Gebühren verteilen Credits zwischen Spielern.
7. Flottenunterhalt verteilt Credits zurück an regionale Bevölkerung.
8. Credits werden im normalen Spiel grundsätzlich nicht vernichtet.
9. Tod erzeugt keine automatische Geldvernichtung.
10. Wiederherstellung bereits früher vorhandener Bevölkerung erzeugt
    keine zweite Geldbasis.

### Was vorerst nicht eingeführt wird

Steuern, frei einstellbare Steuersätze, ein simulierter Arbeitsmarkt,
frei verhandelbare Löhne, künstlicher Geldverfall, Vermögenssteuer,
regelmäßige Zentralbankzahlungen, automatische Geldvernichtung bei Tod,
klassische Money Sinks allein zur Inflationskontrolle.

### Beobachtete wirtschaftliche Dynamiken (bewusst zunächst zugelassen)

- Eine hochspezialisierte Exportregion kann dauerhaft Credits
  ansammeln; wird die Käuferregion zu geldarm, verliert die Exportregion
  Absatz und erhält selbst einen Anreiz zu importieren oder Preise zu
  senken — Geldknappheit kann so Handelsbeziehungen verändern.
- Geldhortung durch Altspieler wird zunächst nicht durch
  Vermögenssteuer, Geldverfall oder Obergrenzen eingeschränkt; gehortetes
  Geld erzeugt selbst keine Produktion, da Produktion, Flottenunterhalt
  und Handel laufende Ausgaben bzw. Liquidität erfordern.
- Eine stationierte Flotte, deren Unterhalt teilweise über eigene
  Konsumgüterverkäufe an die versorgte Bevölkerung zurückfließt, ist
  kein automatischer Exploit, solange dafür reale Schiffe gebaut,
  militärisches Kapital gebunden und reale Konsumgüter bereitgestellt
  werden müssen.
- Große Flotten können gezielt zwischen Regionen verlegt werden, um
  dort Kaufkraft und Nachfrage zu beeinflussen — Reisezeiten, Unterhalt
  und geografische Beschränkungen verhindern bereits beliebig schnelle
  Manipulation.
- Ein blockierter oder isolierter Planet kann über lange Zeit
  Kaufkraft anstauen, wenn weiterhin gearbeitet wird, aber Konsumgüter
  fehlen. Nach Öffnung trifft diese Kaufkraft auf ein knappes
  Warenangebot und kann starke Preissteigerungen auslösen — ein
  Nachkriegs- bzw. Nachblockadeboom ohne geskripteten Bonus.
- Alte Spieler können nominal deutlich mehr Credits besitzen als neue;
  das ist nicht automatisch problematisch, da Credits kein Levelwert
  sind und Altspieler gleichzeitig mehr Bevölkerung, Infrastruktur und
  Produktionsvermögen besitzen. Ein reicher Altspieler kann nach
  Gateway-Öffnung versuchen, die Produktion eines jungen Systems
  aufzukaufen — für den jungen Spieler kann das lukrativ sein, und
  dessen Gatewaykontrolle bestimmt ohnehin, wen er überhaupt hereinlässt.
- Ein reicher Spieler könnte gezielt Konsumgüter eines gegnerischen
  Systems aufkaufen, um dort Versorgung zu verknappen — das ist
  wirtschaftliche Kriegsführung mit realen Kosten (Kapitaleinsatz,
  Lagerung, Transport), keine kostenlose Sabotage, und wird deshalb
  nicht vorschnell verboten.

---

## 19. Offene Punkte

Die folgenden Punkte sind bewusst noch nicht entschieden und sollten
in künftigen Dokumenten einzeln konkretisiert werden.

### Produktion und Wirtschaft

- Wie genau wachsen und verfallen Spezialisierungsboni?
- Wie schnell kann ein Planet seine Spezialisierung wechseln?
- Wie viele Produktionsstufen sind bei welcher Spielerzahl sinnvoll,
  und wie werden neue Stufen eingeführt, ohne bestehende Lieferketten
  zu zerstören?
- Wie werden Preise auf lokalen und sektoralen Märkten technisch
  gebildet? Gibt es Kauf-/Verkaufsaufträge bzw. Orderbücher?
- Wie lange können Waren an einem Handelsposten gelagert werden, kostet
  Lagerung Geld, kann Lagerkapazität knapp werden?
- Wie groß ist die Ladekapazität unterschiedlicher Frachter, wie
  funktioniert Verladen/Entladen?
- Welche Marktinformationen sind kostenlos sichtbar, welche müssen
  erst erworben oder durch eigene Präsenz gesammelt werden?

### Bevölkerung und Politik

- Wie wird politisches Gewicht gegenüber der Gateway-KI exakt
  berechnet? Zählt reine Bevölkerung oder später zusätzlich Loyalität
  bzw. Integration?
- Wie entsteht und verändert sich Loyalität konkret? Besitzt sie einen
  Zahlenwert? Wie unterscheidet sich die Basisloyalität einer neuen
  Kolonie von der eines langjährigen Heimatplaneten?
- Kann Loyalität durch gute Versorgung während einer Besatzung
  wiederhergestellt werden, oder ausschließlich durch Zeitdauer?
- Wie schnell wächst eine junge Kolonie? Wie viele Planeten enthält ein
  typisches Heimatsystem? Sind alle Planeten grundsätzlich bewohnbar
  oder müssen manche terraformt werden?
- Welche Rolle spielt Migration bzw. gezielte Umsiedlung von
  Bevölkerung zwischen Planeten?
- Wie wird die Alien-KI-Sympathie bzw. Legitimität eines Spielers exakt
  bewertet, und was geschieht, wenn mehrere Spieler gleich viel
  politisches Gewicht besitzen?

### Militär und Eroberung

- Wie stark kontern sich die drei Kampfschiffstypen konkret (Zahlen)?
- Wie funktionieren Flottenverluste und Ersatzproduktion?
- Wie viele Schiffe kann ein Trägerschiff aufnehmen, und werden
  Trägerschiffe im Kampf automatisch oder nur auf ausdrücklichen Befehl
  priorisiert angegriffen?
- Wie groß ist der tatsächliche Zeit- und Kostenunterschied zwischen
  Gateway- und Trägerreise?
- Wie genau skaliert Flottenunterhalt mit Einsatzdauer, Entfernung und
  Flottengröße, und besitzt er ein Maximum?
- Wie wird eine gestrandete Flotte versorgt bzw. zurückgeholt?
- Wie funktionieren Bodentruppen im Detail (Einheiten, Stärke,
  Logistik)?
- Wie lange dauert eine planetare Besatzung und Integration konkret,
  und wie stark reduziert Besatzung die wirtschaftliche Leistung eines
  Planeten?
- Wie genau wird eine militärische Schlacht aufgelöst (automatisch
  simuliert, taktische Eingabe, echtzeit- oder rundenbasiert)?
- Unter welchen Bedingungen werden Frachter gekapert statt zerstört,
  was geschieht mit Besatzungen gekaperter Schiffe, und können auch die
  Frachter selbst übernommen werden oder nur ihre Ladung?

### Galaxie, Gateways und Handel

- Wie groß ist die tatsächliche Reichweite eines Gateways, wie viele
  direkte Verbindungen besitzt ein typisches System?
- Wie weit darf die nächste sektorale Handelsstation maximal entfernt
  sein, und wie entstehen neue Handelsstationen, wenn die Galaxie
  wächst? Können mehrere sektorale Handelsstationen direkt miteinander
  verbunden sein?
- Wie stark darf geografisches Glück den Wert eines Heimatsystems
  beeinflussen?
- Wie werden neu entstehende Raumregionen an das bestehende
  Gateway-Netz angebunden?
- Wie funktioniert die konkrete Entdeckung des Gateways spielmechanisch
  (Forschung, Zufallsereignis, Bedingungen)? Kann ein Spieler die
  Aktivierung bewusst hinauszögern, und welche Vorteile erhält er durch
  frühe Aktivierung, damit vollständige Isolation nicht dauerhaft
  optimal ist?
- Wie werden neutrale Einrichtungen der Handelsgilde militärisch
  geschützt? Kann unmittelbar vor einem lokalen Handelsposten gekämpft
  oder blockiert werden?
- Welche Möglichkeiten gibt es für Schmuggel, und welche Rolle spielen
  Versicherungen für Handelsflotten?
- Können Handelsrouten automatisiert wiederholt werden, solange
  weiterhin reale Flotten fliegen? Welche Befehle können blockierende
  Flotten erhalten?
- Können Spieler existieren, deren wirtschaftliche Hauptrolle praktisch
  ausschließlich Handel, Logistik oder Spekulation ist?

### Geldsystem und Balancing

- Bleibt die Wegwerf-Kolonie-Dynamik (neue Kolonie gründen, auf einen
  neuen lokalen Bevölkerungshöchststand wachsen lassen, Geld
  abschöpfen, Kolonie aufgeben, wiederholen) unproblematisch? Das hängt
  davon ab, ob Aufwand und Zeit für Gründung und Wachstum einer Kolonie
  größer sind als der dabei abschöpfbare Geldbetrag — noch nicht
  quantitativ geprüft.
- Wie groß ist die tatsächliche Diskrepanz zwischen
  Bevölkerungs-Geldschöpfung (Größenordnung Milliarden Credits) und
  typischen Markttransaktionen (Größenordnung zehner/hunderter
  Credits)? Erfordert das eine grundsätzliche Skalierungsentscheidung?
- Wie wird die räumliche Verteilung der Flottenunterhalt-Ausschüttung
  auf die Bevölkerung genau bestimmt?
- Eine erste quantitative Simulation steht noch aus, u. a. für: einen
  autarken Planeten, zwei spezialisierte Handelspartner, ein
  Rohstoff-/Industriecluster, eine reiche Transitregion, eine
  Kriegsregion mit hoher Flottenpräsenz, einen stark wachsenden
  Kolonialraum, eine stagnierende Altregion, eine Region nach schwerer
  Bevölkerungskatastrophe. Zu beobachten wären u. a. Gesamtgeldmenge,
  Credits pro Einwohner, Verteilung zwischen Spielern und Bevölkerung,
  Kaufkraftstau, Handelsvolumen, Vermögenskonzentration,
  Geldumlaufgeschwindigkeit.

### Weitere Systemebenen

- Wie werden Parteien, Allianzen und andere politische Organisationen
  strukturiert, wenn das Spiel sie formal nicht kennt?
- Wie wird verhindert, dass einzelne Großakteure komplette
  Lieferketten monopolisieren?
- Welche Rolle spielen NPC-Machthaber in fremden Systemen (eigene
  Wirtschaft, Flotten, Diplomatiefähigkeit)?
- Sind bilaterale Zoll- und Handelsvereinbarungen technisch bindend
  (systemseitig festgelegte Werte) oder rein sozial und jederzeit
  einseitig brechbar? Wie werden Vertragsabschluss, Vertragsdauer und
  Vertragsbruch technisch abgebildet?
- Welche Rolle spielt Forschung als eigenständiges System (Techbaum,
  Forschungspunkte, Verhältnis zur Produktion)?
- Wie wird die grundlegende Zeitskala des Spiels definiert (Echtzeit
  vs. Ticks, typische Produktions- und Reisezeiten)?
- Wie werden neue Spieler technisch in bereits hoch spezialisierte
  Wirtschaftsräume eingeführt, sobald die Galaxie stark gewachsen ist?
