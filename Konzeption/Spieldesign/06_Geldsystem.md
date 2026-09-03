# Geldsystem – Grundprinzipien

*Quelle: `02/06_Geldsystem.md`. Konkrete Formeln (Geldschöpfung pro
Einwohner, historischer Bevölkerungshöchststand) stehen in
`Mechanik/10_Geldkreislauf_Formeln.md`.*

Ausgangspunkt ist bewusst **kein klassisches Faucet-and-Sink-System**:
Credits werden im normalen Spiel nicht laufend erzeugt oder vernichtet,
sondern zirkulieren zwischen Spielern und Bevölkerungen.

## 1. Grundidee: Geld als Kreislauf statt als Ressource

- Bei Entstehung eines neuen Einwohners entsteht grundsätzlich neues
  Geld, zunächst der Bevölkerung bzw. ihrer abstrahierten Kaufkraft
  zugerechnet.
- Normale Transaktionen erzeugen kein neues Geld.
- Es gibt vorerst keine Steuern und keinen simulierten Arbeitsmarkt.
- Mehr Bevölkerung ermöglicht mehr gleichzeitig nutzbare Arbeit und
  damit schnellere bzw. umfangreichere Produktion; Produktionsanlagen
  begrenzen zusätzlich, wie viel technisch produziert werden kann
  (siehe `01_Produktion_und_Arbeitsteilung.md`).

## 2. Lohn, Konsum und Kreislauf

Produktionslohn ist kein Geldvernichter: Zahlt ein Spieler Löhne für
Arbeitseinheiten, werden diese der Bevölkerung als Kaufkraft
zugerechnet:

**Spieler → Lohn → Bevölkerung**

Die Bevölkerung kauft mit dieser Kaufkraft reale Konsumgüter zu
Marktpreisen:

**Bevölkerung → Geld → Verkäufer**

Das Konsumgut wird beim Verbrauch vernichtet, der Credit nicht. Nicht
ausgegebene Kaufkraft bleibt bei der Bevölkerung erhalten.

**Eigenversorgung ist keine Gelddruckmaschine:** Versorgt ein Spieler
seine eigene Bevölkerung, fließt das gezahlte Lohngeld über den
anschließenden Konsum wieder vollständig an ihn zurück – es wurde kein
Geld erzeugt, nur Rohstoffe, Produktionskapazität und Zeit eingesetzt.

**Handel unterstützt das Geldsystem:** Ist ein anderer Spieler auf
Konsumgüter spezialisiert und verkauft er diese an fremde Bevölkerung,
wandert das Lohngeld zu ihm ab. Der ursprüngliche Spieler muss
seinerseits Waren oder Leistungen exportieren, um wieder Geld aus
anderen Wirtschaftsräumen anzuziehen. Damit unterstützt das Geldsystem
Spezialisierung und Handel. Autarkie bleibt technisch möglich, soll aber
wegen der langfristigen Spezialisierungsvorteile wirtschaftlich
zunehmend ineffizient werden.

## 3. Flottenunterhalt und Gebühren als Transfers, nicht als Vernichtung

Auch Flottenunterhalt vernichtet kein Geld. Er repräsentiert Sold,
Versorgung, Wartung und andere Ausgaben des militärischen Apparates
(siehe `03_Militaer_und_Eroberung.md`). Das Geld wird an die
Bevölkerung in der Region bzw. im Cluster ausgeschüttet, in dem sich die
Flotte aufhält:

**Spieler → Flottenunterhalt → regionale Bevölkerung → Konsumnachfrage**

Eine starke militärische Präsenz kann dadurch einen lokalen
Wirtschaftsboom erzeugen. Gateway-Gebühren sind ebenfalls Transfers
zwischen Spielern: Bezahlt A Credits für die Durchreise durch ein von B
kontrolliertes Gateway, erhält B diese Credits. Auch Gebühren neutraler
Institutionen wie der Handelsgilde sollen möglichst nicht vernichtet
werden.

## 4. Geldschöpfung braucht eine Sicherung gegen Bevölkerungs-Exploits

Da jeder neue Einwohner Geld erzeugt, könnte ein Spieler versuchen,
Bevölkerung gezielt wachsen und wieder kollabieren zu lassen, um
wiederholt neue Credits zu erzeugen. Wäre Geldschöpfung an die aktuelle
Bevölkerungszahl gekoppelt und würde Tod kein Geld vernichten, entstünde
eine echte Gelddruckmaschine.

Das Grundprinzip der Gegenmaßnahme: Neue Credits sollen nur für
Bevölkerungswachstum entstehen, das tatsächlich neue historische Höchststände
erreicht – Wiederherstellung bereits einmal vorhandener Bevölkerung soll
keine zweite Geldbasis erzeugen (konkrete Formel siehe
`Mechanik/10_Geldkreislauf_Formeln.md`).

Bewusst offen bzw. bekannt ist eine Restlücke: Der historische
Bevölkerungshöchststand wird pro Planet geführt, nicht pro Spieler oder
galaxieweit. Ob das „Wegwerf-Kolonien"-Muster (Kolonie gründen, auf
neuen Höchststand wachsen lassen, Geld abschöpfen, aufgeben, nächste
Kolonie gründen) ein reales Problem darstellt, hängt von den noch nicht
festgelegten Kosten der Kolonialgründung und -entwicklung ab.

### Tod und Geldmenge

Beim Tod eines Einwohners wird kein Credit automatisch vernichtet: Der
ursprünglich geschaffene Credit kann längst einem Händler in einem
anderen Sektor gehören, eine nachträgliche Vernichtung wäre künstlich.
Die Geldmenge entspricht damit eher der historischen wirtschaftlichen
Expansion als der aktuellen Bevölkerung. Nach einer massiven
Bevölkerungskatastrophe kann relativ viel Geld auf weniger Bevölkerung
und Waren treffen – eine daraus entstehende Inflation ist eine mögliche
emergente Folge.

## 5. Was vorerst nicht eingeführt wird

Frei einstellbare oder progressive Steuersätze, ein simulierter
Arbeitsmarkt, frei verhandelbare Löhne, künstlicher Geldverfall,
automatische Geldvernichtung bei Tod, klassische Money Sinks allein zur
Inflationskontrolle.

Zwei feste, nicht spielerseitig einstellbare Abgaben existieren seit
dem Ausgleichsfonds (§8): eine Vermögenssteuer auf große
Spieler-/Kommandanten-Guthaben und eine Kolonialabgabe auf
Bevölkerungs-Wallets. Beide fließen vollständig in eine tägliche
galaxieweite Umverteilung zurück, sind also kein Money Sink im
klassischen Sinn (§8).

## 6. Stresstests: beobachtete Dynamiken (bewusst zunächst zugelassen)

Die folgenden Dynamiken wurden bereits durchdacht und sollen zunächst
**nicht** durch künstliche Gegenmaßnahmen unterbunden werden, sondern
beobachtet werden:

- **Eine Region zieht fast alle Credits an:** Eine hochspezialisierte
  Exportregion kann dauerhaft Credits ansammeln; wird die Käuferregion
  zu geldarm, verliert die Exportregion Absatz und erhält selbst einen
  Anreiz zu importieren oder Preise zu senken.
- **Geldhortung:** Altspieler können enorme Creditbestände ansammeln und
  nicht ausgeben. Gehortetes Geld erzeugt jedoch selbst keine Produktion
  – Produktion benötigt laufende Arbeitszahlungen, Flotten benötigen
  Unterhalt, Handel benötigt Liquidität. Seit dem Ausgleichsfonds (§8)
  wird sehr große Hortung zusätzlich aktiv gedämpft, statt nur indirekt
  unattraktiv zu sein – sowohl auf Spieler- als auch auf
  Kolonieebene.
- **Flotten als Geldverteilungsmaschine:** Ein Spieler könnte eine
  Flotte stationieren, dort Unterhalt ausschütten lassen und
  anschließend selbst Konsumgüter an diese Bevölkerung verkaufen – kein
  automatischer Exploit, da er reale Schiffe bauen und Konsumgüter
  bereitstellen musste. Problematisch wäre es erst, wenn Flottenunterhalt
  dadurch praktisch kostenlos wird.
- **Flotten zur Marktmanipulation:** Große Flotten könnten gezielt
  zwischen Regionen verlegt werden, um dort Kaufkraft und Nachfrage zu
  verändern. Reisezeiten, Unterhalt und geografische Beschränkungen
  verhindern bereits beliebig schnelle Manipulation.
- **Aufgestaute Kaufkraft:** Ein blockierter oder isolierter Planet kann
  über lange Zeit Kaufkraft ansammeln. Nach Öffnung des Marktes kann
  diese Kaufkraft auf ein knappes Warenangebot treffen und starke
  Preissteigerungen auslösen – ein Nachkriegs- bzw. Nachblockadeboom
  ohne geskripteten Bonus.
- **Neue gegen alte Spieler:** Alte Spieler können nominal sehr viel mehr
  Credits besitzen als neue. Das ist nicht automatisch problematisch:
  Credits sind kein Levelwert, Altspieler besitzen gleichzeitig mehr
  Bevölkerung, Infrastruktur und Produktionsvermögen.
- **Wirtschaftliche Kriegsführung:** Ein reicher Spieler könnte sämtliche
  verfügbaren Konsumgüter eines gegnerischen Systems aufkaufen, um dort
  Versorgung zu verknappen. Das wäre grundsätzlich möglich, kostet ihn
  aber reales Geld und reale Lager-/Transportkapazität – keine kostenlose
  Sabotage.

## 7. Nächster sinnvoller Test

Die nächste Prüfung sollte quantitativ erfolgen (siehe
`Mechanik/10_Geldkreislauf_Formeln.md` für die konkreten zu simulierenden
Szenarien und Messgrößen). Erst anhand solcher Simulationen sollte
entschieden werden, ob das geschlossene Kreislaufmodell zusätzliche
Korrekturen benötigt.

## 8. Ausgleichsfonds: tägliche Vermögenssteuer und Umverteilung

Das reine Kreislaufmodell (§1–4) verhindert Geldvernichtung, aber nicht
Geldkonzentration: Ohne Gegenkraft können einzelne
Spieler-/Kommandanten-Guthaben und einzelne Kolonien über die
Spielzeit beliebig groß gegenüber dem Rest der Galaxie werden (§6,
"Geldhortung"). Der Ausgleichsfonds ist eine bewusste, aber milde
Gegenkraft dazu – kein Money Sink, da nichts vernichtet wird, sondern
eine Umverteilung.

**Abgaben (täglich, siehe `Mechanik/10_...` für die genauen Sätze):**

- Jedes Spieler-/Kommandanten-Wallet (Mensch wie NPC) oberhalb einer
  Guthabenschwelle zahlt einen kleinen Anteil seines Guthabens in den
  Fonds.
- Jede Kolonie zahlt zusätzlich unabhängig von ihrem Kontostand einen
  kleinen Anteil ihres Bevölkerungs-Wallets in den Fonds.

**Ausschüttung (täglich, vollständig):**

Der gesamte Fondsinhalt wird noch am selben Tag komplett wieder
ausgeschüttet – an die Bevölkerungs-Wallets **aller** Kolonien der
Galaxie, nicht an die Spieler-Wallets, und zwar proportional zur
jeweiligen Einwohnerzahl (pro Kopf). Der Fonds ist also nie dauerhaft
gefüllt, sondern nur ein täglicher Umlaufmechanismus.

**Warum pro Kopf und nicht pro Wallet-Guthaben:** Eine Ausschüttung
nach Guthaben würde bereits reiche Kolonien weiter bevorzugen und den
Ausgleichseffekt aufheben. Pro Kopf ausgeschüttet bekommen dagegen
gerade kleine, neue oder abgelegene Kolonien einen spürbaren,
regelmäßigen Kaufkraftschub relativ zu ihrer Größe.

**Erzählerische Einordnung:** Mechanisch ist das eine Steuer- und
Transferzahlung, erzählerisch lässt sie sich als galaxieweiter
Ausgleich zwischen Systemen lesen – Familien, die sich finanziell über
Systemgrenzen hinweg unterstützen, dazu grenzüberschreitender Konsum
bei Gütern und Dienstleistungen, die sich nicht an einen Ort binden
lassen (Unterhaltungselektronik, Versicherungen, Kommunikationsdienste
o. Ä.). Diese Rahmung ist bewusst vage gehalten, um spätere narrative
Ausgestaltung (z. B. eine sichtbare Institution, die den Fonds
verwaltet) nicht vorwegzunehmen.

**Abgrenzung zu §5:** Die beiden festen Sätze sind kein Ersatz für ein
allgemeines, spielerseitig einstellbares Steuersystem – sie sind fixe
Simulationskonstanten ohne Spielereinfluss.

## Offene konzeptionelle Fragen

- Bleibt die Wegwerf-Kolonie-Dynamik (§4) tatsächlich unproblematisch,
  sobald Kolonialkosten feststehen?
- Ausgleichsfonds (§8): Sollte die Ausschüttung langfristig auch
  Spieler-Wallets erreichen (z. B. anteilig), oder bleibt sie bewusst
  auf Bevölkerungs-Wallets beschränkt, um Kommandanten-Hortung nicht
  indirekt wieder zu belohnen?
- Ausgleichsfonds (§8): Sind 0,1 %/Tag (Spieler, Schwelle 1.000
  Credits) und 1 %/Tag (Kolonie, ohne Schwelle) auf lange Sicht die
  richtige Balance, oder verschieben sie zu viel/zu wenig Kaufkraft im
  Vergleich zu Löhnen und Unterhalt? Erfordert eine quantitative Prüfung
  analog §7.
