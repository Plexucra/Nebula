# Planetentypen, Rohstoffprofile und Produktionsbaum

*Ergänzung zu `Konzeption/01_Produktion_und_Arbeitsteilung.md`,
`Konzeption/02_Ressourcen_und_Geografie.md`,
`Mechanik/01_Produktionskette_und_Spezialisierung.md` und
`Mechanik/02_Ressourcenprofile_und_Cluster.md`.*

## 1. Ziel und Status

Dieses Dokument definiert einen vollständigen Startkatalog für die
planetaren Rohstoffprofile und den siebenstufigen Produktionsbaum. Es ist als
konkreter Designvorschlag gedacht. Zahlenwerte sind erste Balancewerte und
sollten durch Simulationen überprüft werden.

Der Katalog verfolgt fünf Ziele:

- Die Rohstoffbasis bleibt geologisch und chemisch nachvollziehbar, ohne jedes
  reale Element einzeln abzubilden.
- Planetentypen erzeugen erkennbare wirtschaftliche Profile und regionale
  Handelsanreize.
- Alle sechs Schiffsklassen besitzen eigene Module und ein eigenes
  Ressourcenprofil.
- Chipfertigung und biologische Produktion bilden echte, mehrstufige Ketten.
- Ein geeigneter Heimatplanet kann Grundkonsumgüter selbst herstellen, bleibt
  für hochwertige Industrie aber auf Spezialisierung und Handel angewiesen.

Elerium-115 ist die einzige ausdrücklich fiktive Ausnahme vom Anspruch
physikalischer Plausibilität. Die Bezeichnung ist ein In-World-Name und nicht
mit dem realen, sehr kurzlebigen Element der Ordnungszahl 115 gleichzusetzen.

## 2. Bedeutung der Prozentwerte

Die Prozentwerte der Planetentabellen sind **Fördergüten**, keine chemischen
Massenanteile und keine Wahrscheinlichkeiten.

Eine Fördergüte beschreibt, wie wirtschaftlich eine Rohstoffgruppe mit der
verfügbaren Technik gewonnen werden kann. Die Werte einer Tabellenzeile sind
voneinander unabhängig und müssen sich deshalb nicht zu 100 Prozent addieren.

| Fördergüte | Bedeutung       | Wirtschaftliche Wirkung                         |
| ----------:| --------------- | ----------------------------------------------- |
| 1 bis 9    | Spuren          | Gewinnung möglich, aber nur im Notfall sinnvoll |
| 10 bis 24  | arm             | teuer und langsam                               |
| 25 bis 44  | mäßig           | brauchbare Eigenversorgung                      |
| 45 bis 64  | gut             | konkurrenzfähige Förderung                      |
| 65 bis 84  | reich           | regionaler Exportrohstoff                       |
| 85 bis 100 | außergewöhnlich | Grundlage eines überregionalen Clusters         |

Empfohlene erste Förderformel:

```text
Ausstoß = Anlagenkapazität × Arbeitsfaktor × (0,20 + 1,30 × Fördergüte / 100)
```

Damit bleibt jede Ressource grundsätzlich förderbar. Ein Vorkommen mit 100
Punkten liefert jedoch mehr als das Siebenfache eines Vorkommens mit einem
Punkt. Rohstoffe werden nicht erschöpft; Knappheit entsteht durch Fördergüte,
Anlagenkapazität, Arbeit, Energie und Transport.

## 3. Die sieben Produktionsebenen

| Ebene | Funktion               | Typische Erzeugnisse                                         |
| -----:| ---------------------- | ------------------------------------------------------------ |
| 1     | natürliche Rohstoffe   | Erze, Eis, Gase, Lagerstätten, Eleriumspuren                 |
| 2     | Aufbereitung           | Konzentrate, Raffinate, getrennte Fluide                     |
| 3     | Grundwerkstoffe        | Legierungen, Keramiken, Chemikalien, Nährlösungen            |
| 4     | Komponenten            | Platten, Leiter, Wafer, Kulturen, Reaktorkammern             |
| 5     | Baugruppen             | Chips, Triebwerkskerne, Sensorfelder, Nahrungsbasen          |
| 6     | Module und Einzelwaren | klassenspezifische Schiffsmodule, Gebäudemodule, Konsumwaren |
| 7     | Endprodukte            | Schiffe, Anlagen, Konsumpakete, Ausrüstungspakete            |

Jedes Rezept nennt nur die direkten Vorprodukte. Energie, Arbeitszeit und
Anlagenzeit sind zusätzliche Kosten und werden nicht als Warenbestandteile in
jede Rezeptzeile geschrieben.

## 4. Rohstoffkatalog der Ebene 1

Die Rohstoffe sind bewusst wirtschaftliche Sammelgruppen. Sie ersetzen eine
Simulation mit Dutzenden einzelner Elemente, behalten aber deren wichtigste
technische Unterschiede bei.

| Rohstoff               | Reale Entsprechung                                                | Hauptverwendung                                   |
| ---------------------- | ----------------------------------------------------------------- | ------------------------------------------------- |
| Ferrometallerz         | eisen-, nickel- und kobalthaltige Erze                            | Stahl, Struktur, Panzerung, Maschinen             |
| Leichtmetallerz        | aluminium- und magnesiumhaltige Erze                              | Leichtbau, Rahmen, schnelle Schiffe               |
| Refraktärmetallerz     | titan-, wolfram-, molybdän- und niobhaltige Erze                  | Hitze, Reaktoren, Düsen, schwere Panzerung        |
| Leitmetallerz          | vor allem kupfer-, zinn- und zinkhaltige Erze                     | Leitungen, Kontakte, Elektromotoren               |
| Edelmetallerz          | Gold, Silber und Platingruppenmetalle                             | Kontakte, Katalysatoren, Sensorik                 |
| Seltenerdenerz         | Lanthanoide, Yttrium und Scandium                                 | Magnete, Optik, Sensoren, Hochleistungselektronik |
| Technologiemetallerz   | Lithium, Gallium, Germanium, Indium und verwandte Spurenmetalle   | Halbleiter, Batterien, Spezialelektronik          |
| Silikatmineral         | Quarz, Feldspäte und silikatische Gesteine                        | Glas, Keramik, Silizium, Bauwerkstoffe            |
| Kohlenstoffmineral     | Graphit, Karbonate und kohlenstoffreiche Minerale                 | Verbundstoffe, Elektroden, Chemie, Biologie       |
| Salzmineral            | Chloride, Phosphate, Nitrate, Sulfate und Spurennährstoffe        | Chemie, Dünger, Medizin, Lebenserhaltung          |
| Radionukliderz         | Uran-, Thorium- und andere radioaktive Minerale                   | Strahlenquellen, Spezialenergie, Sensorik         |
| Wasser              | Eis, Grundwasser und wasserhaltige Minerale                       | Trinkwasser, Sauerstoff, Chemie, Reaktionsmasse   |
| Atmosphärenfluid       | Stickstoff, Sauerstoff, Kohlendioxid, Wasserstoff und Prozessgase | Atemluft, Chemie, Treibstoffe, Biologie           |
| Edelgaskonzentrat      | Helium, Neon, Argon, Krypton und Xenon                            | Kühlung, Ionentriebwerke, Fertigungsatmosphäre    |
| Kohlenwasserstofflager | Methan, höhere Kohlenwasserstoffe und organische Sedimente        | Polymere, Chemie, Textilien, Treibstoffe          |
| Isotopenträger         | deuterium-, helium-3- und lithiumreiche Trägerstoffe              | Fusionsisotope, Hochenergieantriebe               |
| Eleriumspuren          | fiktives Elerium-115 in stabilen Mineral- oder Fluidmatrizen      | Energie, Schiffsantrieb, Waffeninitiatoren        |

### 4.1 Bewusste Zusammenfassungen

- Aluminium und Magnesium werden als Leichtmetallerz geführt.
- Blei wird nicht als eigener Rohstoff geführt. Strahlenschutz entsteht als
  Werkstoff aus dichten Metallfraktionen und Verbundmaterialien.
- Gold, Silber und Platingruppenmetalle werden als Edelmetallerz geführt.
- Wasserstoff ist Bestandteil von Atmosphärenfluid und Wasser. Besonders
  geeignete Isotope werden über Isotopenträger abgebildet.
- Helium ist Teil des Edelgaskonzentrats. Helium-3 für Fusionsanwendungen
  erfordert zusätzlich die Isotopenaufbereitung.
- Biomasse ist kein abgebauter Rohstoff. Sie wird biologisch aus Wasser,
  Kohlenstoff, Nährsalzen, Atmosphärengasen und Energie aufgebaut.

## 5. Planetentypen

Die Klassifikation verbindet wissenschaftlich erkennbare Himmelskörper mit
leicht lesbaren Spielbezeichnungen. Ein Planetentyp beschreibt keine einzelne
exakte Astronomieklasse, sondern eine für Wirtschaft und Besiedlung relevante
Oberflächen- und Zusammensetzungsklasse.

| Planetentyp                   | Wissenschaftliche Nähe                                                  | Wirtschaftliche Rolle                                     |
| ----------------------------- | ----------------------------------------------------------------------- | --------------------------------------------------------- |
| Temperierter Biosphärenplanet | terrestrischer Planet mit flüssigem Wasser und dichter Atmosphäre       | vielseitiger Heimatplanet, Biologie und Grundversorgung   |
| Silikatplanet                 | trockener terrestrischer Gesteinsplanet                                 | Keramik, Glas, Metalle und Bauwirtschaft                  |
| Wüstenplanet                  | arider terrestrischer Planet                                            | Silikate, Salze, Leichtmetalle und Solarwirtschaft        |
| Ozeanplanet                   | terrestrischer Planet mit globalem oder fast globalem Ozean             | Wasser, Salze, Biomasse und Prozesschemie                 |
| Eisplanet                     | gefrorener terrestrischer Planet oder großer Eismond                    | Wasser, flüchtige Stoffe und Isotope                      |
| Vulkanplanet                  | geologisch extrem aktiver terrestrischer Planet                         | Refraktärmetalle, Radionuklide und Elerium                |
| Metallplanet                  | metallreicher Mantelrest oder eisenreicher terrestrischer Planet        | Strukturmetalle, Leiter und Edelmetalle                   |
| Kohlenstoffplanet             | kohlenstoffreicher terrestrischer Planet                                | Kohlenstoff, Kohlenwasserstoffe und Verbundstoffe         |
| Supererde                     | massereicher terrestrischer Planet                                      | schwere Metalle, Hochdruckminerale und Energieerze        |
| Planetoid                     | Asteroid, Zwergplanet oder kleiner Mond                                 | stark schwankende Spezialvorkommen                        |
| Gasriese                      | wasserstoff- und heliumreicher Riesenplanet                             | Atmosphärengase, Edelgase, Kohlenwasserstoffe und Isotope |
| Eisriese                      | methan-, ammoniak- und wasserreicher Riesenplanet                       | Wasser, Kohlenwasserstoffe, Isotope und Kryofluide        |
| Schwefelplanet                | schwefelreicher, vulkanisch oder chemisch aktiver terrestrischer Körper | Salze, Prozesschemie und hitzefeste Werkstoffe            |
| Gasriesenmond                 | Mond eines Gasriesen oder Eisriesen                                     | teilweiser Zugriff auf dessen Fluide, sonst unauffällig   |

Gasriesen und Eisriese haben keine feste Oberfläche und sind NICHT
besiedelbar (§7.4) – anders als in einer früheren Fassung dieses Dokuments
gibt es für sie keine Orbitalstationen-Kolonie mehr. Besiedelbar ist
stattdessen, mit einiger Wahrscheinlichkeit, ein **Gasriesenmond** im selben
System: ein gewöhnlicher kleiner Körper, der einen Teil der Fluide seines
Gasriesen mitnutzbar macht. Beim Planetoiden ersetzt eine verteilte
Habitatstruktur die klassische planetare Stadt.

## 6. Rohstoffprofile der Planetentypen

Alle Angaben sind mögliche Fördergütebereiche. Der konkrete Planet erhält je
Rohstoff einen Wert innerhalb des Bereichs. Ein hoher Maximalwert bedeutet
nicht, dass jeder Planet dieses Typs reich ist.

Balanceregel (Nutzervorgabe): jeder Planetentyp hat höchstens ZWEI
Signaturrohstoffe, die wirklich gut sein dürfen – ein Hauptsignaturrohstoff
zwischen 30 und 100 Prozent, höchstens ein zweiter zwischen 30 und 50 Prozent.
Alle übrigen Rohstoffe des Typs liegen zwischen 1 und 14 Prozent, also immer
unter der "arm"-Schwelle aus §2. Ein Planetentyp ist damit klar erkennbar
spezialisiert statt überall mittelmäßig gut, und kein einzelner Planetentyp
deckt seinen Bedarf an mehr als ein bis zwei Rohstoffen selbst.

### 6.1 Metallerze

| Planetentyp                   | Ferrometall | Leichtmetall | Refraktärmetall | Leitmetall | Edelmetall | Seltene Erden | Technologiemetall |
| ------------------------------ | ----------- | ------------ | --------------- | ---------- | ---------- | ------------- | ----------------- |
| Temperierter Biosphärenplanet  |        1–14 |         1–14 |            1–14 |       1–14 |       1–14 |          1–14 |              1–14 |
| Silikatplanet                  |       30–50 |         1–14 |            1–14 |       1–14 |       1–14 |          1–14 |              1–14 |
| Wüstenplanet                   |        1–14 |       30–100 |            1–14 |       1–14 |       1–14 |          1–14 |              1–14 |
| Ozeanplanet                    |        1–14 |         1–14 |            1–14 |       1–14 |       1–14 |          1–14 |              1–14 |
| Eisplanet                      |        1–14 |         1–14 |            1–14 |       1–14 |       1–14 |          1–14 |              1–14 |
| Vulkanplanet                   |        1–14 |         1–14 |          30–100 |       1–14 |       1–14 |          1–14 |              1–14 |
| Metallplanet                   |      30–100 |         1–14 |            1–14 |      30–50 |       1–14 |          1–14 |              1–14 |
| Kohlenstoffplanet              |        1–14 |         1–14 |            1–14 |       1–14 |       1–14 |          1–14 |              1–14 |
| Supererde                      |        1–14 |         1–14 |           30–50 |       1–14 |       1–14 |          1–14 |              1–14 |
| Planetoid                      |        1–14 |         1–14 |            1–14 |       1–14 |      30–50 |        30–100 |              1–14 |
| Gasriese                       |        1–14 |         1–14 |            1–14 |       1–14 |       1–14 |          1–14 |              1–14 |
| Eisriese                       |        1–14 |         1–14 |            1–14 |       1–14 |       1–14 |          1–14 |              1–14 |
| Schwefelplanet                 |        1–14 |         1–14 |            1–14 |       1–14 |       1–14 |          1–14 |             30–50 |
| Gasriesenmond                  |        1–14 |         1–14 |            1–14 |       1–14 |       1–14 |          1–14 |              1–14 |

### 6.2 Mineralische und energetische Rohstoffe

| Planetentyp                   | Silikat | Kohlenstoff | Salz   | Radionuklide | Eleriumspuren |
| ------------------------------ | ------- | ----------- | ------ | ------------ | ------------- |
| Temperierter Biosphärenplanet  |    1–14 |        1–14 |   1–14 |         1–14 |          1–14 |
| Silikatplanet                  |  30–100 |        1–14 |   1–14 |         1–14 |          1–14 |
| Wüstenplanet                   |    1–14 |        1–14 |  30–50 |         1–14 |          1–14 |
| Ozeanplanet                    |    1–14 |        1–14 |  30–50 |         1–14 |          1–14 |
| Eisplanet                      |    1–14 |        1–14 |   1–14 |         1–14 |          1–14 |
| Vulkanplanet                   |    1–14 |        1–14 |   1–14 |         1–14 |         30–50 |
| Metallplanet                   |    1–14 |        1–14 |   1–14 |         1–14 |          1–14 |
| Kohlenstoffplanet              |    1–14 |      30–100 |   1–14 |         1–14 |          1–14 |
| Supererde                      |    1–14 |        1–14 |   1–14 |       30–100 |          1–14 |
| Planetoid                      |    1–14 |        1–14 |   1–14 |         1–14 |          1–14 |
| Gasriese                       |    1–14 |        1–14 |   1–14 |         1–14 |          1–14 |
| Eisriese                       |    1–14 |        1–14 |   1–14 |         1–14 |          1–14 |
| Schwefelplanet                 |    1–14 |        1–14 | 30–100 |         1–14 |          1–14 |
| Gasriesenmond                  |    1–14 |        1–14 |   1–14 |         1–14 |          1–14 |

### 6.3 Fluide und Isotopenträger

| Planetentyp                   | Wasser | Atmosphärenfluid | Edelgase | Kohlenwasserstoffe | Isotopenträger |
| ------------------------------ | ------ | ---------------- | -------- | ------------------- | -------------- |
| Temperierter Biosphärenplanet  | 30–100 |             30–50 |     1–14 |                1–14 |           1–14 |
| Silikatplanet                  |   1–14 |              1–14 |     1–14 |                1–14 |           1–14 |
| Wüstenplanet                   |   1–14 |              1–14 |     1–14 |                1–14 |           1–14 |
| Ozeanplanet                    | 30–100 |              1–14 |     1–14 |                1–14 |           1–14 |
| Eisplanet                      | 30–100 |              1–14 |     1–14 |                1–14 |          30–50 |
| Vulkanplanet                   |   1–14 |              1–14 |     1–14 |                1–14 |           1–14 |
| Metallplanet                   |   1–14 |              1–14 |     1–14 |                1–14 |           1–14 |
| Kohlenstoffplanet              |   1–14 |              1–14 |     1–14 |               30–50 |           1–14 |
| Supererde                      |   1–14 |              1–14 |     1–14 |                1–14 |           1–14 |
| Planetoid                      |   1–14 |              1–14 |     1–14 |                1–14 |           1–14 |
| Gasriese                       |   1–14 |            30–100 |    30–50 |                1–14 |           1–14 |
| Eisriese                       |   1–14 |              1–14 |     1–14 |               30–50 |         30–100 |
| Schwefelplanet                 |   1–14 |              1–14 |     1–14 |                1–14 |           1–14 |
| Gasriesenmond                  |   1–14 |             30–50 |   30–100 |                1–14 |           1–14 |

## 7. Generierung von Planet und Cluster

### 7.1 Zweistufige Erzeugung

Zuerst wird für jedes Sternsystem und jeden Rohstoff ein regionaler
Clusterwert zwischen 0 und 100 erzeugt. Danach wird dieser Wert durch den
Planetentyp in dessen erlaubten Fördergütebereich übersetzt.

```text
Typwert = Minimum + (Maximum - Minimum)
          × (0,65 × Clusterwert / 100 + 0,35 × lokale Zufallszahl)

Fördergüte = runden(begrenzen(Typwert + lokale Abweichung, Minimum, Maximum))
```

- Die lokale Zufallszahl liegt zwischen 0 und 1.
- Die lokale Abweichung liegt zwischen minus 5 und plus 5 Punkten.
- Der Clusterwert bestimmt überwiegend, ob ein Planet im unteren oder oberen
  Teil seines typischen Bereichs liegt.
- Der Planetentyp verhindert geologisch unpassende Ergebnisse. Ein Gasriese
  wird deshalb trotz eines Metallclusters nicht zum Metallplaneten.

### 7.2 Regionale Kohärenz statt Nachbarschafts-Validierung

Statt den Clusterwert je System unabhängig zu würfeln und Ausreißer in einer
nachträglichen Validierungsphase über alle Gateway-Nachbarn zu korrigieren,
erzeugt der Weltgenerator den Clusterwert direkt räumlich kohärent: je
Rohstoff entstehen einige wenige zufällige "Hotspots" mit zufälliger Stärke,
verteilt über die gesamte Galaxiekarte. Der Clusterwert eines Systems an
Position (x, y) ist der mit einem Gauß-Kern distanzgewichtete Mittelwert
dieser Hotspot-Stärken.

Ergebnis: benachbarte Systeme haben automatisch ähnliche Clusterwerte (glatter
Feldverlauf), weit entfernte Regionen der Galaxie streuen dagegen unabhängig.
Jede Gegend der Galaxie bekommt so ein eigenes wirtschaftliches Profil mit
eigenen Stärken und Schwächen, und Systeme mit ähnlichen Stärken liegen mit
hoher Wahrscheinlichkeit nebeneinander – ganz ohne Neuwürfeln einzelner
Planetentypen oder eine explizite Prüfung je Gateway-Kante.

### 7.3 Signatur- und Manglerohstoffe

Die frühere Idee, Signatur- und Manglerohstoffe je EINZELNEM Planeten neu zu
würfeln, entfällt: Signaturrohstoffe sind stattdessen fest im Fördergüte-
Bereich des Planetentyps verankert (§6) – ein Planetentyp hat höchstens zwei
Signaturrohstoffe, alle übrigen liegen für JEDEN Planeten dieses Typs unter 15
Prozent. Das ist einfacher als eine Zufallsauswahl je Planet, ergibt aber
denselben Effekt: kein einzelner Planet ist zufällig in allen Bereichen
gleichzeitig Spitzenklasse, und ein Planetentyp bleibt über die ganze Galaxie
hinweg wiedererkennbar spezialisiert.

### 7.4 Besiedelbare und unbesiedelbare Himmelskörper

Nicht jeder Himmelskörper eines Systems ist besiedelbar – realistischerweise
wäre eine Masse über 1,5 g Oberflächenschwerkraft für eine Kolonie ungünstig,
ebenso eine zu geringe Masse (kein Halt für Atmosphäre/Wasser) oder ein
Gasriese/Eisriese ohne feste Oberfläche (§5). Jedes System besteht deshalb aus:

- 3 bis 5 besiedelbaren Himmelskörpern mit vollem Fördergüte-Profil (§6);
- zusätzlich rund 4 bis 6 unbesiedelbaren Himmelskörpern OHNE
  Rohstoffanzeige – Gasriesen, Eisriesen oder Körper mit ungünstiger Masse.

Enthält ein System einen Gasriesen oder Eisriesen, kann einer der
besiedelbaren Himmelskörper stattdessen ein **Gasriesenmond** sein: ein Mond,
der einen Teil der Fluide seines Gasriesen (Atmosphärenfluid, Edelgase)
mitnutzbar macht und dadurch selbst besiedelbar ist, obwohl sein Mutterkörper
es nicht ist.

## 8. Heimatplanet und Grundversorgung

Ein neuer Spieler startet auf einem temperierten Biosphärenplaneten. Für den
konkreten Heimatplaneten gilt statt der üblichen Planetentyp-Fördergütebereiche
(§6) eine feste Regel (Nutzervorgabe), unabhängig vom regionalen Cluster des
Heimatsystems:

- Die vier Nahrungsrohstoffe **Wasser**, **Atmosphärenfluid**, **Salzmineral**
  und **Kohlenstoffmineral** sowie **Eleriumspuren** liegen IMMER zwischen 50
  und 60 Prozent.
- Genau EIN weiterer, zufällig gewählter Rohstoff liegt ebenfalls zwischen 50
  und 60 Prozent.
- Alle übrigen Rohstoffe liegen zwischen 1 und 9 Prozent.

Jeder Heimatplanet kann damit von Anfang an seine Bevölkerung ernähren und
seinen Eleriumbedarf lokal decken, ist bei allem anderen aber überwiegend arm
– genau wie jeder andere Planet auch auf Handel angewiesen. Welcher der
übrigen Rohstoffe die eine Ausnahme ist, unterscheidet sich von Heimatwelt zu
Heimatwelt und gibt jedem Start eine eigene, kleine Spezialisierung mit.

Zusätzlich beginnt die Kolonie mit einer versiegelten Eleriumreserve, einem
einfachen Eleriumgenerator, je einer primitiven Förderanlage für Wasser,
Atmosphärenfluid, Salz, Kohlenstoff und Elerium sowie einem kleinen Startvorrat
an Eleriumkapseln für die ersten Gateway-Sprünge (§9.4). Die Reserve verhindert
einen Startstillstand, ersetzt aber keine laufende Förderung.

Der Heimatplanet kann damit alle Grundkonsumgüter selbst herstellen. Für
leistungsfähige Chips, Großschiffe und hochspezialisierte Module bleiben Handel
und Arbeitsteilung deutlich effizienter.

## 9. Eleriumwirtschaft

### 9.1 Grundregel

Elerium-115 ist ein extrem energiedichter, fiktiver Energieträger. Es wird in
sehr kleinen physischen Mengen eingesetzt. Seine hohen Profilpunkte bei
bestimmten Produkten drücken strategische Abhängigkeit und Kosten aus, nicht
Massenanteil.

Elerium ersetzt nicht alle Betriebsstoffe:

- Es stellt Energie bereit.
- Ein Schiffsantrieb benötigt weiterhin Reaktionsmasse.
- Lebenserhaltung benötigt weiterhin Wasser und Atmosphärengase.
- Chemische und biologische Produktion benötigt weiterhin Materie.
- Eleriumwaffen benötigen einen Eleriuminitiator sowie ein konventionelles
  Träger-, Sicherungs- und Zielsystem.

### 9.2 Produktionskette

```text
Ebene 1: Eleriumspuren
Ebene 2: Eleriumkonzentrat
Ebene 3: Stabilisiertes Elerium
Ebene 4: Eleriumkapsel
Ebene 5: Eleriumenergiezelle, Eleriumtreibstoffkern oder Eleriuminitiator
Ebene 6: klassenspezifisches Energie-, Antriebs- oder Waffenmodul
Ebene 7: Schiff, Kraftwerk oder militärisches Ausrüstungspaket
```

### 9.3 Drei getrennte Einsatzformen

| Form                  | Verwendung                                      | Balancefunktion                                        |
| --------------------- | ----------------------------------------------- | ------------------------------------------------------ |
| Eleriumenergiezelle   | Kraftwerke, Netze, stationäre Maschinen         | erzeugt nutzbare Energie über längere Zeit             |
| Eleriumtreibstoffkern | Schiffsreaktor und Hochenergieantrieb           | hohe Leistung, benötigt weiterhin Reaktionsmasse       |
| Eleriuminitiator      | schwere Gefechtsköpfe und strategische Ladungen | sehr hoher Spitzenenergiebedarf, Verbrauch bei Einsatz |

### 9.4 Eleriumkapseln als Sprungtreibstoff

Jeder Gateway-Sprung einer Flotte verbraucht Eleriumkapseln: 0,01 Stück je
Schiff und Sprung, unabhängig von Schiffsklasse oder Fracht. Ein neuer
Kommandant startet mit 10 Kapseln im Heimatkolonielager – genug für viele
Sprünge einer kleinen Startflotte, ohne dass eigene Kapselproduktion sofort
nötig wäre. Reicht der gesamte Kapselvorrat über alle eigenen Kolonien
zusammen nicht für einen geplanten (auch mehrsprungigen) Flug, wird der
Sprung abgelehnt, bevor die Flotte losfliegt.

Damit bleibt Reisen für kleine Flotten praktisch kostenlos, während ein
Kommandant mit vielen Frachtern und großen Flotten die Sprungkosten zunehmend
einplanen und eigene Eleriumkapsel-Produktion aufbauen muss – eine weiche
Bremse gegen beliebig weite, beliebig große Flottenbewegungen, ohne neue
Spieler zu behindern.

## 10. Vollständiger Produktionsbaum

### 10.1 Ebene 2: aufbereitete Rohstoffe

| Produkt                     | Direkte Eingänge aus Ebene 1 |
| --------------------------- | ---------------------------- |
| Ferrometallkonzentrat       | Ferrometallerz               |
| Leichtmetallkonzentrat      | Leichtmetallerz              |
| Refraktärmetallkonzentrat   | Refraktärmetallerz           |
| Leitmetallkonzentrat        | Leitmetallerz                |
| Edelmetallkonzentrat        | Edelmetallerz                |
| Seltenerdkonzentrat         | Seltenerdenerz               |
| Technologiemetallkonzentrat | Technologiemetallerz         |
| Silikatraffinat             | Silikatmineral               |
| Kohlenstoffraffinat         | Kohlenstoffmineral           |
| Industriesalze              | Salzmineral                  |
| Radionuklidkonzentrat       | Radionukliderz               |
| Prozesswasser               | Wasser                    |
| Getrennte Atmosphärengase   | Atmosphärenfluid             |
| Edelgasfraktion             | Edelgaskonzentrat            |
| Kohlenwasserstofffraktion   | Kohlenwasserstofflager       |
| Isotopenkonzentrat          | Isotopenträger               |
| Eleriumkonzentrat           | Eleriumspuren                |

### 10.2 Ebene 3: Grundwerkstoffe und Betriebsmedien

| Produkt                  | Direkte Eingänge aus Ebene 2                                                  |
| ------------------------ | ----------------------------------------------------------------------------- |
| Stahllegierung           | Ferrometallkonzentrat, Kohlenstoffraffinat                                    |
| Leichtmetalllegierung    | Leichtmetallkonzentrat, Ferrometallkonzentrat                                 |
| Hochtemperaturlegierung  | Refraktärmetallkonzentrat, Ferrometallkonzentrat, Seltenerdkonzentrat         |
| Leitermetall             | Leitmetallkonzentrat, Edelmetallkonzentrat                                    |
| Katalysatormetall        | Edelmetallkonzentrat, Seltenerdkonzentrat                                     |
| Magnetwerkstoff          | Seltenerdkonzentrat, Ferrometallkonzentrat                                    |
| Halbleiterrohstoff       | Technologiemetallkonzentrat, Silikatraffinat, Kohlenstoffraffinat             |
| Keramikwerkstoff         | Silikatraffinat, Industriesalze                                               |
| Glaswerkstoff            | Silikatraffinat, Industriesalze                                               |
| Verbundwerkstoff         | Kohlenstoffraffinat, Leichtmetallkonzentrat, Silikatraffinat                  |
| Strahlenschutzwerkstoff  | Ferrometallkonzentrat, Refraktärmetallkonzentrat, Kohlenstoffraffinat         |
| Polymergrundstoff        | Kohlenwasserstofffraktion, Kohlenstoffraffinat                                |
| Industriechemikalien     | Industriesalze, Prozesswasser, Getrennte Atmosphärengase                      |
| Nährlösung               | Prozesswasser, Industriesalze, Getrennte Atmosphärengase, Kohlenstoffraffinat |
| Atemgasgemisch           | Getrennte Atmosphärengase, Prozesswasser                                      |
| Kryofluid                | Edelgasfraktion, Getrennte Atmosphärengase                                    |
| Reaktionsmasse           | Prozesswasser, Getrennte Atmosphärengase                                      |
| Fusionsisotope           | Isotopenkonzentrat, Prozesswasser, Edelgasfraktion                            |
| Radiologischer Werkstoff | Radionuklidkonzentrat, Keramikwerkstoff                                       |
| Stabilisiertes Elerium   | Eleriumkonzentrat, Hochtemperaturlegierung, Edelgasfraktion                   |

### 10.3 Ebene 4: Komponenten und Kulturen

| Produkt                | Direkte Eingänge aus Ebene 3                                              |
| ---------------------- | ------------------------------------------------------------------------- |
| Strukturplatte         | Stahllegierung                                                            |
| Leichtrahmen           | Leichtmetalllegierung, Verbundwerkstoff                                   |
| Panzersegment          | Stahllegierung, Hochtemperaturlegierung, Verbundwerkstoff                 |
| Hitzeschildsegment     | Hochtemperaturlegierung, Keramikwerkstoff                                 |
| Strahlenschutzsegment  | Strahlenschutzwerkstoff, Verbundwerkstoff                                 |
| Leiterbündel           | Leitermetall, Polymergrundstoff                                           |
| Magnetspule            | Magnetwerkstoff, Leitermetall, Kryofluid                                  |
| Optikkristall          | Glaswerkstoff, Seltenerdkonzentrat                                        |
| Halbleiterwafer        | Halbleiterrohstoff, Industriechemikalien, Edelgasfraktion                 |
| Keramiksubstrat        | Keramikwerkstoff, Leitermetall                                            |
| Energiespeicherzelle   | Technologiemetallkonzentrat, Leitermetall, Keramikwerkstoff               |
| Supraleitersegment     | Leitermetall, Seltenerdkonzentrat, Kryofluid                              |
| Dichtungssystem        | Polymergrundstoff, Leichtmetalllegierung                                  |
| Rohrleitungssystem     | Stahllegierung, Leichtmetalllegierung, Polymergrundstoff                  |
| Kühlmittelbehälter     | Kryofluid, Leichtmetalllegierung, Polymergrundstoff                       |
| Aktuatoreinheit        | Magnetwerkstoff, Leitermetall, Stahllegierung                             |
| Reaktorkammer          | Hochtemperaturlegierung, Keramikwerkstoff, Strahlenschutzwerkstoff        |
| Druckhabitatsegment    | Leichtmetalllegierung, Verbundwerkstoff, Glaswerkstoff, Polymergrundstoff |
| Lebenserhaltungszelle  | Atemgasgemisch, Prozesswasser, Industriechemikalien, Polymergrundstoff    |
| Biomassekultur         | Nährlösung, Prozesswasser, Atemgasgemisch                                 |
| Pharmakultur           | Nährlösung, Prozesswasser, Industriechemikalien, Atemgasgemisch           |
| Chemischer Energiesatz | Industriechemikalien, Kohlenstoffraffinat, Polymergrundstoff              |
| Fusionskapsel          | Fusionsisotope, Hochtemperaturlegierung, Keramikwerkstoff                 |
| Eleriumkapsel          | Stabilisiertes Elerium, Hochtemperaturlegierung, Strahlenschutzwerkstoff  |

### 10.4 Ebene 5: Elektronik, Baugruppen und biologische Grundprodukte

#### Chips

| Produkt            | Direkte Eingänge aus Ebene 4                                            |
| ------------------ | ----------------------------------------------------------------------- |
| Steuerchip         | Halbleiterwafer, Keramiksubstrat, Leiterbündel                          |
| Sensorchip         | Halbleiterwafer, Optikkristall, Leiterbündel                            |
| Navigationschip    | zwei Halbleiterwafer, Optikkristall, Leiterbündel, Energiespeicherzelle |
| Kommunikationschip | Halbleiterwafer, Optikkristall, Leiterbündel                            |
| Gefechtschip       | zwei Halbleiterwafer, Optikkristall, Leiterbündel, Keramiksubstrat      |
| Logistikchip       | Halbleiterwafer, Leiterbündel, Keramiksubstrat, Energiespeicherzelle    |
| Medizinchip        | Halbleiterwafer, Optikkristall, Keramiksubstrat, Leiterbündel           |
| Energiechip        | Halbleiterwafer, Keramiksubstrat, Supraleitersegment                    |

Die Chiptypen verwenden gemeinsame Wafer und Substrate, aber unterschiedliche
Strukturierung, Dotierung, Optik und Verschaltung. Dadurch bleibt die
Chipfertigung tief, ohne zyklische Abhängigkeiten zwischen Produkten derselben
Ebene zu erzeugen.

#### Schiffbaugruppen

| Produkt                        | Direkte Eingänge                                                     |
| ------------------------------ | -------------------------------------------------------------------- |
| Strukturzelle                  | Strukturplatte, Leichtrahmen, Dichtungssystem                        |
| Panzerbaugruppe                | Panzersegment, Hitzeschildsegment, Aktuatoreinheit                   |
| Energieverteiler               | Energiechip, Leiterbündel, Supraleitersegment, Energiespeicherzelle  |
| Fusionsreaktorkern             | Reaktorkammer, Fusionskapsel, Energiechip, Strahlenschutzsegment     |
| Eleriumenergiezelle            | Eleriumkapsel, Energiechip, Strahlenschutzsegment                    |
| Eleriumtreibstoffkern          | Eleriumkapsel, Reaktorkammer, Energiechip, Kryofluid                 |
| Eleriuminitiator               | Eleriumkapsel, Gefechtschip, Strahlenschutzsegment                   |
| Triebwerkskern                 | Reaktorkammer, Magnetspule, Hitzeschildsegment, Steuerchip           |
| Manövertriebwerk               | Aktuatoreinheit, Magnetspule, Reaktionsmasse, Steuerchip             |
| Reaktionsmassensystem          | Rohrleitungssystem, Kühlmittelbehälter, Reaktionsmasse, Steuerchip   |
| Sensorfeld                     | Sensorchip, Optikkristall, Leiterbündel, Energiespeicherzelle        |
| Kommunikationsfeld             | Kommunikationschip, Optikkristall, Leiterbündel                      |
| Navigationssystem              | Navigationschip, Sensorfeld, Kommunikationsfeld                      |
| Gefechtsleitsystem             | Gefechtschip, Sensorfeld, Kommunikationsfeld                         |
| Projektilwaffenbaugruppe       | Aktuatoreinheit, Panzersegment, Gefechtschip, Chemischer Energiesatz |
| Strahlenwaffenbaugruppe        | Optikkristall, Supraleitersegment, Gefechtschip, Energieverteiler    |
| Lenkflugkörperbaugruppe        | Gefechtschip, Sensorchip, Leichtrahmen, Chemischer Energiesatz       |
| Schwerer Gefechtskopf          | Eleriuminitiator, Panzersegment, Gefechtschip                        |
| Schildemitterbaugruppe         | Supraleitersegment, Magnetspule, Energiechip, Energiespeicherzelle   |
| Hangarbaugruppe                | Strukturzelle, Aktuatoreinheit, Druckhabitatsegment, Logistikchip    |
| Frachtumschlagbaugruppe        | Strukturzelle, Aktuatoreinheit, Logistikchip                         |
| Lebenserhaltungsbaugruppe      | Lebenserhaltungszelle, Medizinchip, Rohrleitungssystem               |
| Besatzungsbaugruppe            | Druckhabitatsegment, Lebenserhaltungsbaugruppe, Kommunikationschip   |
| Truppenunterbringungsbaugruppe | Druckhabitatsegment, Lebenserhaltungsbaugruppe, Panzerbaugruppe      |

#### Industrie und Biologie

| Produkt                      | Direkte Eingänge                                                          |
| ---------------------------- | ------------------------------------------------------------------------- |
| Fördermaschinenbaugruppe     | Strukturplatte, Aktuatoreinheit, Steuerchip, Rohrleitungssystem           |
| Raffineriebaugruppe          | Reaktorkammer, Rohrleitungssystem, Steuerchip, Hitzeschildsegment         |
| Fertigungsmaschinenbaugruppe | Strukturplatte, Aktuatoreinheit, Steuerchip, Leiterbündel                 |
| Reinraumbaugruppe            | Druckhabitatsegment, Lebenserhaltungszelle, Medizinchip, Steuerchip       |
| Bioreaktorbaugruppe          | Biomassekultur, Lebenserhaltungszelle, Rohrleitungssystem, Medizinchip    |
| Energienetzbaugruppe         | Energieverteiler, Leiterbündel, Energiespeicherzelle, Steuerchip          |
| Nahrungsbasis                | Biomassekultur, Nährlösung, Prozesswasser                                 |
| Textilfaserbasis             | Biomassekultur oder Polymergrundstoff, Industriechemikalien               |
| Hygienebasis                 | Biomassekultur, Polymergrundstoff, Industriechemikalien, Prozesswasser    |
| Pharmabasis                  | Pharmakultur, Industriechemikalien, Medizinchip                           |
| Haushaltswarenbasis          | Polymergrundstoff, Leichtmetalllegierung, Glaswerkstoff, Keramikwerkstoff |
| Unterhaltungselektronikbasis | Steuerchip, Kommunikationschip, Leiterbündel, Polymergrundstoff           |

### 10.5 Ebene 6: klassenspezifische Schiffsmodule

Jede Schiffsklasse besitzt sechs eigene Module. Auch ähnlich klingende Module
sind keine austauschbaren Waren. Ein Korvettenantriebsmodul kann daher nicht in
einen Zerstörer eingebaut werden. Gemeinsame Baugruppen der Ebene 5 halten den
Baum trotzdem beherrschbar.

#### Korvette

| Modul                     | Direkte Eingänge                                                                     |
| ------------------------- | ------------------------------------------------------------------------------------ |
| Korvettenrumpfmodul       | Strukturzelle, Panzerbaugruppe, Besatzungsbaugruppe                                  |
| Korvettenantriebsmodul    | Triebwerkskern, zwei Manövertriebwerke, Reaktionsmassensystem, Eleriumtreibstoffkern |
| Korvettenenergiemodul     | Eleriumenergiezelle, Energieverteiler, Energiespeicherzelle                          |
| Korvettenelektronikmodul  | Navigationssystem, Sensorfeld, Kommunikationsfeld                                    |
| Korvettenwaffenmodul      | Strahlenwaffenbaugruppe, Lenkflugkörperbaugruppe, Gefechtsleitsystem                 |
| Korvettenversorgungsmodul | Lebenserhaltungsbaugruppe, Besatzungsbaugruppe, Frachtumschlagbaugruppe              |

#### Zerstörer

| Modul                     | Direkte Eingänge                                                                     |
| ------------------------- | ------------------------------------------------------------------------------------ |
| Zerstörerrumpfmodul       | zwei Strukturzellen, zwei Panzerbaugruppen, Besatzungsbaugruppe                      |
| Zerstörerantriebsmodul    | zwei Triebwerkskerne, Manövertriebwerk, Reaktionsmassensystem, Eleriumtreibstoffkern |
| Zerstörerenergiemodul     | Fusionsreaktorkern, Eleriumenergiezelle, Energieverteiler                            |
| Zerstörerelektronikmodul  | Navigationssystem, Gefechtsleitsystem, Kommunikationsfeld                            |
| Zerstörerwaffenmodul      | zwei Projektilwaffenbaugruppen, zwei Lenkflugkörperbaugruppen, Schwerer Gefechtskopf |
| Zerstörerversorgungsmodul | Lebenserhaltungsbaugruppe, Besatzungsbaugruppe, Frachtumschlagbaugruppe              |

#### Kreuzer

| Modul                   | Direkte Eingänge                                                                                               |
| ----------------------- | -------------------------------------------------------------------------------------------------------------- |
| Kreuzerrumpfmodul       | drei Strukturzellen, drei Panzerbaugruppen, Strahlenschutzsegment                                              |
| Kreuzerantriebsmodul    | drei Triebwerkskerne, zwei Manövertriebwerke, zwei Reaktionsmassensysteme, Eleriumtreibstoffkern               |
| Kreuzerenergiemodul     | zwei Eleriumenergiezellen, Fusionsreaktorkern, zwei Energieverteiler                                           |
| Kreuzerelektronikmodul  | Navigationssystem, zwei Sensorfelder, Gefechtsleitsystem, Kommunikationsfeld                                   |
| Kreuzerwaffenmodul      | zwei Strahlenwaffenbaugruppen, Projektilwaffenbaugruppe, Lenkflugkörperbaugruppe, zwei Schildemitterbaugruppen |
| Kreuzerversorgungsmodul | zwei Lebenserhaltungsbaugruppen, zwei Besatzungsbaugruppen, Frachtumschlagbaugruppe                            |

#### Frachter

| Modul                    | Direkte Eingänge                                                                     |
| ------------------------ | ------------------------------------------------------------------------------------ |
| Frachterrumpfmodul       | drei Strukturzellen, Leichtrahmen, Druckhabitatsegment                               |
| Frachterantriebsmodul    | Triebwerkskern, Manövertriebwerk, zwei Reaktionsmassensysteme, Eleriumtreibstoffkern |
| Frachterenergiemodul     | Fusionsreaktorkern, Energieverteiler, Energiespeicherzelle                           |
| Frachterelektronikmodul  | Navigationssystem, Logistikchip, Kommunikationsfeld                                  |
| Frachterladungsmodul     | drei Frachtumschlagbaugruppen, Strukturzelle, Logistikchip                           |
| Frachterversorgungsmodul | Lebenserhaltungsbaugruppe, Besatzungsbaugruppe, Fertigungsmaschinenbaugruppe         |

#### Trägerschiff

| Modul                        | Direkte Eingänge                                                                                 |
| ---------------------------- | ------------------------------------------------------------------------------------------------ |
| Trägerschiffrumpfmodul       | vier Strukturzellen, drei Panzerbaugruppen, zwei Druckhabitatsegmente                            |
| Trägerschiffantriebsmodul    | drei Triebwerkskerne, zwei Manövertriebwerke, drei Reaktionsmassensysteme, Eleriumtreibstoffkern |
| Trägerschiffenergiemodul     | drei Eleriumenergiezellen, Fusionsreaktorkern, zwei Energieverteiler                             |
| Trägerschiffelektronikmodul  | Navigationssystem, zwei Sensorfelder, zwei Kommunikationsfelder, Gefechtsleitsystem              |
| Trägerschiffhangarmodul      | vier Hangarbaugruppen, zwei Frachtumschlagbaugruppen, Logistikchip                               |
| Trägerschiffversorgungsmodul | drei Lebenserhaltungsbaugruppen, drei Besatzungsbaugruppen, Fertigungsmaschinenbaugruppe         |

#### Mannschaftstransporter

| Modul                                | Direkte Eingänge                                                                                 |
| ------------------------------------ | ------------------------------------------------------------------------------------------------ |
| Mannschaftstransportrumpfmodul       | drei Strukturzellen, zwei Panzerbaugruppen, zwei Druckhabitatsegmente                            |
| Mannschaftstransportantriebsmodul    | zwei Triebwerkskerne, zwei Manövertriebwerke, zwei Reaktionsmassensysteme, Eleriumtreibstoffkern |
| Mannschaftstransportenergiemodul     | Eleriumenergiezelle, Fusionsreaktorkern, Energieverteiler                                        |
| Mannschaftstransportelektronikmodul  | Navigationssystem, Kommunikationsfeld, Sensorfeld                                                |
| Mannschaftstransporttruppenmodul     | drei Truppenunterbringungsbaugruppen, Frachtumschlagbaugruppe, Medizinchip                       |
| Mannschaftstransportversorgungsmodul | drei Lebenserhaltungsbaugruppen, zwei Besatzungsbaugruppen, Pharmabasis                          |

### 10.6 Ebene 6: Gebäudemodule

| Modul               | Direkte Eingänge                                                                  |
| ------------------- | --------------------------------------------------------------------------------- |
| Fördermodul         | Fördermaschinenbaugruppe, Energieverteiler, Strukturzelle                         |
| Raffineriemodul     | Raffineriebaugruppe, Energieverteiler, Strukturzelle                              |
| Werkstoffmodul      | Fertigungsmaschinenbaugruppe, Raffineriebaugruppe, Energieverteiler               |
| Komponentenmodul    | zwei Fertigungsmaschinenbaugruppen, Steuerchip, Energieverteiler                  |
| Elektronikmodul     | Reinraumbaugruppe, Fertigungsmaschinenbaugruppe, Energieverteiler                 |
| Bioproduktionsmodul | zwei Bioreaktorbaugruppen, Lebenserhaltungsbaugruppe, Energieverteiler            |
| Nahrungsmittelmodul | Bioreaktorbaugruppe, Fertigungsmaschinenbaugruppe, Logistikchip                   |
| Kraftwerksmodul     | Eleriumenergiezelle, Reaktorkammer, Energieverteiler, Strahlenschutzsegment       |
| Habitatmodul        | drei Druckhabitatsegmente, zwei Lebenserhaltungsbaugruppen, Energienetzbaugruppe  |
| Lagermodul          | zwei Strukturzellen, Frachtumschlagbaugruppe, Logistikchip                        |
| Werftmodul          | drei Fertigungsmaschinenbaugruppen, Frachtumschlagbaugruppe, Energienetzbaugruppe |
| Verteidigungsmodul  | zwei Panzerbaugruppen, Gefechtsleitsystem, Sensorfeld, Energieverteiler           |
| Forschungsmodul     | Reinraumbaugruppe, Sensorfeld, Medizinchip, Kommunikationsfeld                    |

### 10.7 Ebene 6: Konsumwaren und Bodenausrüstung

| Ware                       | Direkte Eingänge                                        |
| -------------------------- | ------------------------------------------------------- |
| Trinkwasserration          | Nahrungsbasis, Prozesswasser                            |
| Grundnahrung               | Nahrungsbasis                                           |
| Standardnahrung            | Nahrungsbasis, Hygienebasis                             |
| Hygienewaren               | Hygienebasis, Haushaltswarenbasis                       |
| Grundkleidung              | Textilfaserbasis                                        |
| Schutzkleidung             | Textilfaserbasis, Haushaltswarenbasis                   |
| Grundmedizin               | Pharmabasis, Hygienebasis                               |
| Erweiterte Medizin         | Pharmabasis, Medizinchip                                |
| Haushaltswaren             | Haushaltswarenbasis                                     |
| Hochwertige Haushaltswaren | Haushaltswarenbasis, Unterhaltungselektronikbasis       |
| Unterhaltungselektronik    | Unterhaltungselektronikbasis                            |
| Infanterieausrüstung       | Schutzkleidung, Kommunikationschip, Haushaltswarenbasis |
| Schwere Bodenausrüstung    | Panzerbaugruppe, Projektilwaffenbaugruppe, Gefechtschip |

### 10.8 Ebene 7: Endprodukte

#### Schiffe

| Endprodukt             | Benötigte Module aus Ebene 6                                                                                                                                                                                     |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Korvette               | Korvettenrumpfmodul, Korvettenantriebsmodul, Korvettenenergiemodul, Korvettenelektronikmodul, Korvettenwaffenmodul, Korvettenversorgungsmodul                                                                    |
| Zerstörer              | Zerstörerrumpfmodul, Zerstörerantriebsmodul, Zerstörerenergiemodul, Zerstörerelektronikmodul, Zerstörerwaffenmodul, Zerstörerversorgungsmodul                                                                    |
| Kreuzer                | Kreuzerrumpfmodul, Kreuzerantriebsmodul, Kreuzerenergiemodul, Kreuzerelektronikmodul, Kreuzerwaffenmodul, Kreuzerversorgungsmodul                                                                                |
| Frachter               | Frachterrumpfmodul, Frachterantriebsmodul, Frachterenergiemodul, Frachterelektronikmodul, Frachterladungsmodul, Frachterversorgungsmodul                                                                         |
| Trägerschiff           | Trägerschiffrumpfmodul, Trägerschiffantriebsmodul, Trägerschiffenergiemodul, Trägerschiffelektronikmodul, Trägerschiffhangarmodul, Trägerschiffversorgungsmodul                                                  |
| Mannschaftstransporter | Mannschaftstransportrumpfmodul, Mannschaftstransportantriebsmodul, Mannschaftstransportenergiemodul, Mannschaftstransportelektronikmodul, Mannschaftstransporttruppenmodul, Mannschaftstransportversorgungsmodul |

#### Planetare Anlagen

| Endprodukt           | Benötigte Module aus Ebene 6                               |
| -------------------- | ---------------------------------------------------------- |
| Rohstoffförderanlage | zwei Fördermodule, Kraftwerksmodul                         |
| Raffinerie           | zwei Raffineriemodule, Kraftwerksmodul, Lagermodul         |
| Werkstofffabrik      | zwei Werkstoffmodule, Kraftwerksmodul, Lagermodul          |
| Komponentenfabrik    | zwei Komponentenmodule, Kraftwerksmodul, Lagermodul        |
| Elektronikfabrik     | zwei Elektronikmodule, Kraftwerksmodul, Forschungsmodul    |
| Biomasseanlage       | zwei Bioproduktionsmodule, Kraftwerksmodul, Habitatmodul   |
| Nahrungsmittelfabrik | zwei Nahrungsmittelmodule, Bioproduktionsmodul, Lagermodul |
| Eleriumkraftwerk     | drei Kraftwerksmodule, Verteidigungsmodul                  |
| Koloniehabitat       | drei Habitatmodule, Kraftwerksmodul, Lagermodul            |
| Warenlager           | drei Lagermodule, Verteidigungsmodul                       |
| Modulwerft           | drei Werftmodule, Kraftwerksmodul, Lagermodul              |
| Schiffswerft         | fünf Werftmodule, zwei Kraftwerksmodule, zwei Lagermodule  |
| Planetenverteidigung | drei Verteidigungsmodule, Kraftwerksmodul, Forschungsmodul |
| Forschungskomplex    | drei Forschungsmodule, Elektronikmodul, Kraftwerksmodul    |

#### Konsumpakete und Ausrüstungspakete

| Endprodukt               | Benötigte Waren aus Ebene 6                                                              | Versorgungswirkung                         |
| ------------------------ | ---------------------------------------------------------------------------------------- | ------------------------------------------ |
| Grundversorgungspaket    | Trinkwasserration, Grundnahrung, Hygienewaren, Grundkleidung                             | verhindert akute Unterversorgung           |
| Standardversorgungspaket | Trinkwasserration, Standardnahrung, Hygienewaren, Grundkleidung, Haushaltswaren          | stabiler normaler Lebensstandard           |
| Medizinpaket             | Trinkwasserration, Grundmedizin, Erweiterte Medizin, Hygienewaren                        | Gesundheit, Epidemieschutz und Genesung    |
| Komfortpaket             | Standardnahrung, Schutzkleidung, Haushaltswaren, Unterhaltungselektronik                 | Zufriedenheit und Loyalität                |
| Luxuspaket               | Standardnahrung, Erweiterte Medizin, Unterhaltungselektronik, Hochwertige Haushaltswaren | Prestige, hohe Zufriedenheit und Geldsenke |
| Infanteriepaket          | Infanterieausrüstung, Grundmedizin, Trinkwasserration, Grundnahrung, Hygienewaren        | Ausrüstung einer leichten Bodeneinheit     |
|                          |                                                                                          |                                            |

## 11. Ressourcenprofile der Schiffsklassen

Die folgenden Werte sind **Ressourcenprofilpunkte**. Jede Schiffsspalte summiert
sich zu 100. Die Punkte messen den strategischen Einfluss eines Rohstoffs auf
Kosten und Lieferketten. Sie sind keine Massenanteile. Dadurch kann die physisch
kleine Eleriummenge trotzdem ein hoher Kosten- und Engpassfaktor sein.

| Rohstoff               | Korvette | Zerstörer | Kreuzer | Frachter | Trägerschiff | Mannschaftstransporter |
| ---------------------- | --------:| ---------:| -------:| --------:| ------------:| ----------------------:|
| Ferrometallerz         | 12       | 24        | 20      | 28       | 19           | 22                     |
| Leichtmetallerz        | 22       | 10        | 8       | 16       | 10           | 12                     |
| Refraktärmetallerz     | 4        | 7         | 12      | 3        | 7            | 4                      |
| Leitmetallerz          | 8        | 7         | 8       | 5        | 8            | 6                      |
| Edelmetallerz          | 2        | 2         | 3       | 1        | 3            | 1                      |
| Seltenerdenerz         | 7        | 4         | 5       | 2        | 5            | 2                      |
| Technologiemetallerz   | 12       | 8         | 8       | 5        | 10           | 6                      |
| Silikatmineral         | 3        | 4         | 4       | 6        | 4            | 6                      |
| Kohlenstoffmineral     | 8        | 6         | 5       | 8        | 5            | 7                      |
| Salzmineral            | 1        | 2         | 1       | 2        | 1            | 3                      |
| Radionukliderz         | 1        | 4         | 3       | 1        | 2            | 1                      |
| Wasser              | 2        | 2         | 1       | 3        | 2            | 5                      |
| Atmosphärenfluid       | 1        | 2         | 1       | 2        | 2            | 4                      |
| Edelgaskonzentrat      | 3        | 2         | 3       | 2        | 2            | 1                      |
| Kohlenwasserstofflager | 2        | 6         | 2       | 8        | 1            | 10                     |
| Isotopenträger         | 4        | 4         | 2       | 4        | 1            | 3                      |
| Eleriumspuren          | 8        | 6         | 14      | 4        | 18           | 7                      |
| **Summe**              | **100**  | **100**   | **100** | **100**  | **100**      | **100**                |

Die Profile erzeugen folgende wirtschaftliche Identitäten:

- Korvetten bevorzugen Leichtmetalle, Technologiemetalle, seltene Erden und
  Kohlenstoffverbunde.
- Zerstörer benötigen besonders viel Ferrometall, Munitionsträger,
  Radionuklide und Kohlenwasserstoffe.
- Kreuzer konzentrieren sich auf Refraktärmetalle, schwere Struktur und
  Elerium.
- Frachter benötigen vor allem preiswerte Struktur, Leichtbau, Polymere und
  Reaktionsmasse.
- Trägerschiffe sind elektronik-, energie- und eleriumintensive mobile Basen.
- Mannschaftstransporter benötigen viel Habitatstruktur, Wasser,
  Atmosphärengase, Polymere und Versorgungstechnik.

## 12. Zusammenhang zwischen Planet, Rohstoff und Produktion

| Planetarische Stärke               | Typische Vorprodukte                                    | Naheliegende Spezialisierung                       |
| ---------------------------------- | ------------------------------------------------------- | -------------------------------------------------- |
| Ferrometalle und Refraktärmetalle  | Stahllegierung, Hochtemperaturlegierung, Panzersegmente | Zerstörer, Kreuzer, Werften, Verteidigung          |
| Leichtmetalle und Kohlenstoff      | Leichtrahmen, Verbundwerkstoff, Polymergrundstoff       | Korvetten, Frachter, Habitate                      |
| Leitmetalle und seltene Erden      | Leiterbündel, Magnetwerkstoff, Sensorfelder             | Antriebe, Sensorik, Energienetze                   |
| Technologiemetalle und Silikate    | Halbleiterrohstoff, Wafer, Keramiksubstrate             | Chipfertigung, Elektronikfabriken                  |
| Wasser, Salze und Atmosphärengase  | Nährlösung, Atemgas, Biomassekulturen                   | Nahrung, Medizin, Bevölkerung                      |
| Edelgase und Isotope               | Kryofluide, Fusionskapseln, Magnetspulen                | Reaktoren, Hochenergieantriebe                     |
| Kohlenwasserstoffe und Kohlenstoff | Polymere, Textilfasern, Hygienebasen                    | Konsumgüter, Frachter, Mannschaftstransporter      |
| Radionuklide und dichte Metalle    | radiologische Werkstoffe, Strahlenschutzsegmente        | Reaktoren, Sensorik, schwere Schiffe               |
| Elerium                            | Energiezellen, Treibstoffkerne, Initiatoren             | Kraftwerke, Kreuzer, Trägerschiffe, schwere Waffen |

Kein Planetentyp ist als alleiniger Sieger gedacht. Ein Metallplanet kann eine
herausragende Schwerindustrie tragen, benötigt aber Wasser, Bioprodukte und
häufig Atmosphärengase. Ein Ozeanplanet kann Bevölkerung und Biologie versorgen,
braucht jedoch importierte Refraktärmetalle für Großschiffe. Ein Gasriese ist
für Isotope und Edelgase wichtig, bleibt aber von festen Strukturwerkstoffen
abhängig.

## 13. Vollständige Eigenproduktion und implizite Unterproduktion

Ein Spieler darf ein Endprodukt auch dann in Auftrag geben, wenn nicht alle
Vorprodukte auf Lager liegen. Das System kann fehlende Unterprodukte implizit
herstellen, sofern alle Rohstoffe verfügbar sind.

Dabei gelten drei Nachteile:

1. Jede implizit ausgeführte Unterstufe belegt zusätzliche Anlagenzeit.
2. Nur die ausdrücklich laufende Produktlinie erhält den vollen
   Spezialisierungsfortschritt.
3. Fehlende Spezialanlagen verursachen einen Umrüstmalus.

Damit bleibt Autarkie möglich, aber arbeitsteilige Produktion ist bei langen
Ketten strukturell schneller und günstiger.

## 14. Balanceleitlinien

- Grundversorgung darf auf einem Heimatplaneten nie an einem einzelnen
  seltenen Metall scheitern.
- Hochwertige Chips sollen mindestens fünf Rohstoffgruppen und vier
  Verarbeitungsschritte berühren.
- Jedes Schiff benötigt alle Rohstoffgruppen indirekt, aber kein Profil darf
  einem anderen entsprechen.
- Elerium muss für den Start vorhanden sein, darf aber ab mittlerem Spielstand
  einen relevanten Handels- und Konflikttreiber bilden.
- Planetentyp, regionaler Cluster und lokale Abweichung sollen gemeinsam
  wirken. Keiner der drei Faktoren darf die beiden anderen vollständig
  überschreiben.
- Ein Planet soll höchstens zwei klare Exportstärken und mindestens drei
  erkennbare Importbedarfe besitzen.
- Konsumgüterketten benötigen Energie, dürfen Elerium aber nicht als
  materiellen Rezeptbestandteil verbrauchen.
- Reaktionsmasse, Kühlmittel und Lebenserhaltung bleiben trotz Elerium als
  eigenständige logistische Güter relevant.

## 15. Noch zu simulierende Zahlenfragen

- Förderausstoß und Energieverbrauch je Fördergüte.
- Rezeptmengen und Produktionszeiten aller direkten Abhängigkeiten.
- Verbrauch einer Eleriumenergiezelle pro Koloniegröße und Zeiteinheit.
- Reichweite eines Eleriumtreibstoffkerns je Schiffsklasse.
- Verbrauch von Grund-, Standard-, Medizin-, Komfort- und Luxuspaketen je
  Bevölkerungseinheit.
- Benötigte Spezialisierungsdauer je Produktionsebene.
- Transportvolumen und Masse der aufbereiteten Stoffe, Module und Endprodukte.
- Häufigkeit der Planetentypen sowie der außergewöhnlichen Fördergüten.
- Wirtschaftliche Tragfähigkeit von Gasriesen- und Eisriesenkolonien ohne
  lokale Feststoffbasis.
