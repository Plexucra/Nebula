# Flache Produktliste: Masse, Volumen, Arbeit und Elerium

*Zahlenkatalog zum Dokument
`Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md`.*

## 1. Status und Lesart

Dieses Dokument überführt den vollständigen siebenstufigen Produktionsbaum in
eine flache, sortierbare Produktliste. Es enthält **211 Produkte**.
Jede Zeile beschreibt genau eine Produktionscharge. Bei Modulen, Schiffen und
Anlagen entspricht eine Charge einem Stück; bei Rohstoffen, Werkstoffen und
Konsumwaren entspricht sie einem standardisierten Warenlos.

Die Zahlen sind konsistente Startwerte für Simulation und Balancing, keine
abschließend festgelegten Realwerte.

### 1.1 Spalten

- **Masse** ist die Nettoausgabemasse einer Charge in Kilogramm.
- **Volumen** ist das belegte Transport- oder Lagervolumen in Kubikmetern.
- **Masse-Volumen-Verhältnis** ist die effektive Schütt-, Pack- oder
  Systemdichte in Kilogramm pro Kubikmeter. Bei Schiffen und Modulen enthält
  sie Hohlräume, Leitungswege und Wartungsraum.
- **Eleriumbedarf** ist die Eleriummenge in Gramm Rohäquivalent, die für den
  konkreten Produktionsauftrag bereitstehen muss. Sie enthält Prozessenergie
  sowie das in eingebauten Eleriumkomponenten gebundene Material. Der Wert ist
  kein Massenanteil des Gesamtprodukts.
- **Arbeitskräfte** sind gleichzeitig gebundene Arbeiter.
- **Arbeitszeit** ist die reale Dauer der Charge bei voller Besetzung.
- **Arbeitsaufwand** ist Arbeitskräfte mal Arbeitszeit in Arbeiterstunden.
- **Direkte Unterprodukte** dienen der Rezeptzuordnung. Mengenwörter wie
  „zwei“ oder „drei“ bezeichnen diskrete Stückzahlen. Ohne Mengenwort gilt
  eine Einheit beziehungsweise ein chargenabhängiger Stoffanteil. Bei den
  Schiffsmodulen stehen seit der Größenskala aus `27_Schiffsgroessen_und_
  Massenskala.md` Zahlen statt Mengenwörter (`25 × Strukturzelle`) – dort
  liegen die Stückzahlen im drei- bis vierstelligen Bereich.

### 1.2 Rohstoffnormierung

Bei allen 17 Grundrohstoffen gelten exakt **100 Arbeiter und eine Stunde**.
Nur die gewinnbare Masse unterscheidet sich. Die Werte gelten bei einer
Fördergüte von 100. Für niedrigere Fördergüten wird der Ausstoß mit der im
Hauptdokument definierten Förderformel skaliert.

Eleriumspuren liefern unter diesen Idealbedingungen nur **0,001 kg**, also
**ein Gramm**, während häufige Massengüter im Tonnenbereich liegen.

### 1.3 Massenbilanz für Module

Die Masse jedes Schiffsmoduls und Gebäudemoduls wurde aus der Summe seiner
diskreten Unterprodukte zuzüglich acht Prozent Integrationsstruktur berechnet.
Schiffe, Anlagen und Pakete erhalten zusätzlich drei Prozent für Endmontage,
Verbindungen, Gehäuse oder Verpackung.

Geprüft wurden **76 zusammengesetzte Produkte**. Es gibt keine
Massenunterschreitung. Die kleinste rechnerische Reserve gegenüber der Summe
der Unterprodukte beträgt **3 Prozent**. Bei
Raffination, Chemie und biologischer Verarbeitung darf die Ausgabemasse
dagegen kleiner als die Einsatzmasse sein, weil Abraum, Abgas, Abwasser und
Nebenprodukte nicht Teil der Produktcharge sind.

Die acht Prozent Integrationsstruktur gelten für jedes Schiffsmodul **exakt**.
Der Endmontagezuschlag der Schiffe ist eine Untergrenze und liegt nach der
Größenskala aus `27_Schiffsgroessen_und_Massenskala.md` zwischen 3,00 und
3,30 Prozent: die Schiffsmassen sind dort auf runde Zielwerte gesetzt, die
Modulstückzahlen als ganze Zahlen darunter gewählt.

## 2. Flache Produktliste

| Nr. | Ebene | Produkt | Kategorie | Masse kg | Volumen m³ | Masse-Volumen-Verhältnis kg/m³ | Eleriumbedarf g | Arbeitskräfte | Arbeitszeit h | Arbeitsaufwand Ah | Direkte Unterprodukte |
| ---: | ---: | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 001 | 1 | Ferrometallerz | Grundrohstoff | 800 000 | 363,64 | 2 200 | 0,001 | 100 | 1 | 100 | — |
| 002 | 1 | Leichtmetallerz | Grundrohstoff | 500 000 | 333,33 | 1 500 | 0,001 | 100 | 1 | 100 | — |
| 003 | 1 | Refraktärmetallerz | Grundrohstoff | 180 000 | 69,23 | 2 600 | 0,001 | 100 | 1 | 100 | — |
| 004 | 1 | Leitmetallerz | Grundrohstoff | 350 000 | 152,17 | 2 300 | 0,001 | 100 | 1 | 100 | — |
| 005 | 1 | Edelmetallerz | Grundrohstoff | 12 000 | 4 | 3 000 | 0,001 | 100 | 1 | 100 | — |
| 006 | 1 | Seltenerdenerz | Grundrohstoff | 40 000 | 20 | 2 000 | 0,001 | 100 | 1 | 100 | — |
| 007 | 1 | Technologiemetallerz | Grundrohstoff | 20 000 | 11,11 | 1 800 | 0,001 | 100 | 1 | 100 | — |
| 008 | 1 | Silikatmineral | Grundrohstoff | 1 500 000 | 937,5 | 1 600 | 0,001 | 100 | 1 | 100 | — |
| 009 | 1 | Kohlenstoffmineral | Grundrohstoff | 500 000 | 555,56 | 900 | 0,001 | 100 | 1 | 100 | — |
| 010 | 1 | Salzmineral | Grundrohstoff | 800 000 | 666,67 | 1 200 | 0,001 | 100 | 1 | 100 | — |
| 011 | 1 | Radionukliderz | Grundrohstoff | 15 000 | 6 | 2 500 | 0,001 | 100 | 1 | 100 | — |
| 012 | 1 | Wasser | Grundrohstoff | 2 000 000 | 2 105,26 | 950 | 0,001 | 100 | 1 | 100 | — |
| 013 | 1 | Atmosphärenfluid | Grundrohstoff | 5 000 000 | 6 250 | 800 | 0,001 | 100 | 1 | 100 | — |
| 014 | 1 | Edelgaskonzentrat | Grundrohstoff | 80 000 | 2 000 | 40 | 0,001 | 100 | 1 | 100 | — |
| 015 | 1 | Kohlenwasserstofflager | Grundrohstoff | 1 000 000 | 1 538,46 | 650 | 0,001 | 100 | 1 | 100 | — |
| 016 | 1 | Isotopenträger | Grundrohstoff | 10 000 | 33,33 | 300 | 0,001 | 100 | 1 | 100 | — |
| 017 | 1 | Eleriumspuren | Grundrohstoff | 0,001 | 0,00000025 | 4 000 | 0,001 | 100 | 1 | 100 | — |
| 018 | 2 | Ferrometallkonzentrat | Aufbereitung | 500 000 | 178,57 | 2 800 | 0,0032 | 80 | 4 | 320 | Ferrometallerz |
| 019 | 2 | Leichtmetallkonzentrat | Aufbereitung | 300 000 | 166,67 | 1 800 | 0,0032 | 80 | 4 | 320 | Leichtmetallerz |
| 020 | 2 | Refraktärmetallkonzentrat | Aufbereitung | 80 000 | 25 | 3 200 | 0,0032 | 80 | 4 | 320 | Refraktärmetallerz |
| 021 | 2 | Leitmetallkonzentrat | Aufbereitung | 180 000 | 69,23 | 2 600 | 0,0032 | 80 | 4 | 320 | Leitmetallerz |
| 022 | 2 | Edelmetallkonzentrat | Aufbereitung | 5 000 | 1,25 | 4 000 | 0,0072 | 120 | 6 | 720 | Edelmetallerz |
| 023 | 2 | Seltenerdkonzentrat | Aufbereitung | 15 000 | 6 | 2 500 | 0,0072 | 120 | 6 | 720 | Seltenerdenerz |
| 024 | 2 | Technologiemetallkonzentrat | Aufbereitung | 8 000 | 3,48 | 2 300 | 0,0072 | 120 | 6 | 720 | Technologiemetallerz |
| 025 | 2 | Silikatraffinat | Aufbereitung | 900 000 | 529,41 | 1 700 | 0,0032 | 80 | 4 | 320 | Silikatmineral |
| 026 | 2 | Kohlenstoffraffinat | Aufbereitung | 250 000 | 312,5 | 800 | 0,0032 | 80 | 4 | 320 | Kohlenstoffmineral |
| 027 | 2 | Industriesalze | Aufbereitung | 400 000 | 285,71 | 1 400 | 0,0032 | 80 | 4 | 320 | Salzmineral |
| 028 | 2 | Radionuklidkonzentrat | Aufbereitung | 5 000 | 1,56 | 3 200 | 0,0072 | 120 | 6 | 720 | Radionukliderz |
| 029 | 2 | Prozesswasser | Aufbereitung | 1 800 000 | 1 800 | 1 000 | 0,0032 | 80 | 4 | 320 | Wasser |
| 030 | 2 | Getrennte Atmosphärengase | Aufbereitung | 2 000 000 | 2 500 | 800 | 0,0018 | 60 | 3 | 180 | Atmosphärenfluid |
| 031 | 2 | Edelgasfraktion | Aufbereitung | 30 000 | 250 | 120 | 0,0018 | 60 | 3 | 180 | Edelgaskonzentrat |
| 032 | 2 | Kohlenwasserstofffraktion | Aufbereitung | 700 000 | 1 000 | 700 | 0,0032 | 80 | 4 | 320 | Kohlenwasserstofflager |
| 033 | 2 | Isotopenkonzentrat | Aufbereitung | 2 000 | 6,67 | 300 | 0,0072 | 120 | 6 | 720 | Isotopenträger |
| 034 | 2 | Eleriumkonzentrat | Aufbereitung | 0,00075 | 0,00000003 | 25 000 | 1,01 | 120 | 8 | 960 | Eleriumspuren |
| 035 | 3 | Stahllegierung | Grundwerkstoff | 100 000 | 15,38 | 6 500 | 0,012 | 120 | 10 | 1 200 | Ferrometallkonzentrat, Kohlenstoffraffinat |
| 036 | 3 | Leichtmetalllegierung | Grundwerkstoff | 70 000 | 28 | 2 500 | 0,012 | 120 | 10 | 1 200 | Leichtmetallkonzentrat, Ferrometallkonzentrat |
| 037 | 3 | Hochtemperaturlegierung | Grundwerkstoff | 20 000 | 2,86 | 7 000 | 0,029 | 180 | 16 | 2 880 | Refraktärmetallkonzentrat, Ferrometallkonzentrat, Seltenerdkonzentrat |
| 038 | 3 | Leitermetall | Grundwerkstoff | 20 000 | 2,67 | 7 500 | 0,012 | 120 | 10 | 1 200 | Leitmetallkonzentrat, Edelmetallkonzentrat |
| 039 | 3 | Katalysatormetall | Grundwerkstoff | 2 000 | 0,222 | 9 000 | 0,012 | 120 | 10 | 1 200 | Edelmetallkonzentrat, Seltenerdkonzentrat |
| 040 | 3 | Magnetwerkstoff | Grundwerkstoff | 8 000 | 1,33 | 6 000 | 0,012 | 120 | 10 | 1 200 | Seltenerdkonzentrat, Ferrometallkonzentrat |
| 041 | 3 | Halbleiterrohstoff | Grundwerkstoff | 5 000 | 2,27 | 2 200 | 0,029 | 180 | 16 | 2 880 | Technologiemetallkonzentrat, Silikatraffinat, Kohlenstoffraffinat |
| 042 | 3 | Keramikwerkstoff | Grundwerkstoff | 80 000 | 28,57 | 2 800 | 0,012 | 120 | 10 | 1 200 | Silikatraffinat, Industriesalze |
| 043 | 3 | Glaswerkstoff | Grundwerkstoff | 100 000 | 40 | 2 500 | 0,012 | 120 | 10 | 1 200 | Silikatraffinat, Industriesalze |
| 044 | 3 | Verbundwerkstoff | Grundwerkstoff | 50 000 | 31,25 | 1 600 | 0,012 | 120 | 10 | 1 200 | Kohlenstoffraffinat, Leichtmetallkonzentrat, Silikatraffinat |
| 045 | 3 | Strahlenschutzwerkstoff | Grundwerkstoff | 50 000 | 10 | 5 000 | 0,012 | 120 | 10 | 1 200 | Ferrometallkonzentrat, Refraktärmetallkonzentrat, Kohlenstoffraffinat |
| 046 | 3 | Polymergrundstoff | Grundwerkstoff | 80 000 | 88,89 | 900 | 0,012 | 120 | 10 | 1 200 | Kohlenwasserstofffraktion, Kohlenstoffraffinat |
| 047 | 3 | Industriechemikalien | Grundwerkstoff | 100 000 | 100 | 1 000 | 0,012 | 120 | 10 | 1 200 | Industriesalze, Prozesswasser, Getrennte Atmosphärengase |
| 048 | 3 | Nährlösung | Grundwerkstoff | 100 000 | 100 | 1 000 | 0,0064 | 80 | 8 | 640 | Prozesswasser, Industriesalze, Getrennte Atmosphärengase, Kohlenstoffraffinat |
| 049 | 3 | Atemgasgemisch | Grundwerkstoff | 50 000 | 625 | 80 | 0,0064 | 80 | 8 | 640 | Getrennte Atmosphärengase, Prozesswasser |
| 050 | 3 | Kryofluid | Grundwerkstoff | 20 000 | 25 | 800 | 0,0064 | 80 | 8 | 640 | Edelgasfraktion, Getrennte Atmosphärengase |
| 051 | 3 | Reaktionsmasse | Grundwerkstoff | 100 000 | 100 | 1 000 | 0,0064 | 80 | 8 | 640 | Prozesswasser, Getrennte Atmosphärengase |
| 052 | 3 | Fusionsisotope | Grundwerkstoff | 500 | 1 | 500 | 0,029 | 180 | 16 | 2 880 | Isotopenkonzentrat, Prozesswasser, Edelgasfraktion |
| 053 | 3 | Radiologischer Werkstoff | Grundwerkstoff | 3 000 | 0,857 | 3 500 | 0,029 | 180 | 16 | 2 880 | Radionuklidkonzentrat, Keramikwerkstoff |
| 054 | 3 | Stabilisiertes Elerium | Grundwerkstoff | 0,0006 | 0,000000024 | 25 000 | 0,793 | 180 | 24 | 4 320 | Eleriumkonzentrat, Hochtemperaturlegierung, Edelgasfraktion |
| 055 | 4 | Strukturplatte | Komponente | 20 000 | 11,11 | 1 800 | 0,021 | 150 | 14 | 2 100 | Stahllegierung |
| 056 | 4 | Leichtrahmen | Komponente | 12 000 | 6,67 | 1 800 | 0,021 | 150 | 14 | 2 100 | Leichtmetalllegierung, Verbundwerkstoff |
| 057 | 4 | Panzersegment | Komponente | 25 000 | 3,85 | 6 500 | 0,021 | 150 | 14 | 2 100 | Stahllegierung, Hochtemperaturlegierung, Verbundwerkstoff |
| 058 | 4 | Hitzeschildsegment | Komponente | 8 000 | 4,44 | 1 800 | 0,021 | 150 | 14 | 2 100 | Hochtemperaturlegierung, Keramikwerkstoff |
| 059 | 4 | Strahlenschutzsegment | Komponente | 15 000 | 5 | 3 000 | 0,053 | 220 | 24 | 5 280 | Strahlenschutzwerkstoff, Verbundwerkstoff |
| 060 | 4 | Leiterbündel | Komponente | 3 000 | 1,67 | 1 800 | 0,021 | 150 | 14 | 2 100 | Leitermetall, Polymergrundstoff |
| 061 | 4 | Magnetspule | Komponente | 5 000 | 0,769 | 6 500 | 0,021 | 150 | 14 | 2 100 | Magnetwerkstoff, Leitermetall, Kryofluid |
| 062 | 4 | Optikkristall | Komponente | 500 | 0,278 | 1 800 | 0,036 | 180 | 20 | 3 600 | Glaswerkstoff, Seltenerdkonzentrat |
| 063 | 4 | Halbleiterwafer | Komponente | 100 | 0,056 | 1 800 | 0,021 | 150 | 14 | 2 100 | Halbleiterrohstoff, Industriechemikalien, Edelgasfraktion |
| 064 | 4 | Keramiksubstrat | Komponente | 500 | 0,167 | 3 000 | 0,021 | 150 | 14 | 2 100 | Keramikwerkstoff, Leitermetall |
| 065 | 4 | Energiespeicherzelle | Komponente | 2 000 | 1,11 | 1 800 | 0,036 | 180 | 20 | 3 600 | Technologiemetallkonzentrat, Leitermetall, Keramikwerkstoff |
| 066 | 4 | Supraleitersegment | Komponente | 1 000 | 0,556 | 1 800 | 0,036 | 180 | 20 | 3 600 | Leitermetall, Seltenerdkonzentrat, Kryofluid |
| 067 | 4 | Dichtungssystem | Komponente | 500 | 0,278 | 1 800 | 0,021 | 150 | 14 | 2 100 | Polymergrundstoff, Leichtmetalllegierung |
| 068 | 4 | Rohrleitungssystem | Komponente | 3 000 | 1,67 | 1 800 | 0,021 | 150 | 14 | 2 100 | Stahllegierung, Leichtmetalllegierung, Polymergrundstoff |
| 069 | 4 | Kühlmittelbehälter | Komponente | 6 000 | 3,33 | 1 800 | 0,021 | 150 | 14 | 2 100 | Kryofluid, Leichtmetalllegierung, Polymergrundstoff |
| 070 | 4 | Aktuatoreinheit | Komponente | 4 000 | 2,22 | 1 800 | 0,021 | 150 | 14 | 2 100 | Magnetwerkstoff, Leitermetall, Stahllegierung |
| 071 | 4 | Reaktorkammer | Komponente | 20 000 | 3,08 | 6 500 | 0,053 | 220 | 24 | 5 280 | Hochtemperaturlegierung, Keramikwerkstoff, Strahlenschutzwerkstoff |
| 072 | 4 | Druckhabitatsegment | Komponente | 15 000 | 8,33 | 1 800 | 0,021 | 150 | 14 | 2 100 | Leichtmetalllegierung, Verbundwerkstoff, Glaswerkstoff, Polymergrundstoff |
| 073 | 4 | Lebenserhaltungszelle | Komponente | 5 000 | 2,78 | 1 800 | 0,012 | 100 | 12 | 1 200 | Atemgasgemisch, Prozesswasser, Industriechemikalien, Polymergrundstoff |
| 074 | 4 | Biomassekultur | Komponente | 20 000 | 25 | 800 | 0,012 | 100 | 12 | 1 200 | Nährlösung, Prozesswasser, Atemgasgemisch |
| 075 | 4 | Pharmakultur | Komponente | 2 000 | 2,5 | 800 | 0,012 | 100 | 12 | 1 200 | Nährlösung, Prozesswasser, Industriechemikalien, Atemgasgemisch |
| 076 | 4 | Chemischer Energiesatz | Komponente | 5 000 | 2,78 | 1 800 | 0,021 | 150 | 14 | 2 100 | Industriechemikalien, Kohlenstoffraffinat, Polymergrundstoff |
| 077 | 4 | Fusionskapsel | Komponente | 1 000 | 0,556 | 1 800 | 0,053 | 220 | 24 | 5 280 | Fusionsisotope, Hochtemperaturlegierung, Keramikwerkstoff |
| 078 | 4 | Eleriumkapsel | Komponente | 500 | 0,2 | 2 500 | 0,648 | 200 | 24 | 4 800 | Stabilisiertes Elerium, Hochtemperaturlegierung, Strahlenschutzwerkstoff |
| 079 | 5 | Steuerchip | Chip | 50 | 0,083 | 600 | 0,053 | 220 | 24 | 5 280 | Halbleiterwafer, Keramiksubstrat, Leiterbündel |
| 080 | 5 | Sensorchip | Chip | 60 | 0,1 | 600 | 0,053 | 220 | 24 | 5 280 | Halbleiterwafer, Optikkristall, Leiterbündel |
| 081 | 5 | Navigationschip | Chip | 80 | 0,133 | 600 | 0,053 | 220 | 24 | 5 280 | zwei Halbleiterwafer, Optikkristall, Leiterbündel, Energiespeicherzelle |
| 082 | 5 | Kommunikationschip | Chip | 60 | 0,1 | 600 | 0,053 | 220 | 24 | 5 280 | Halbleiterwafer, Optikkristall, Leiterbündel |
| 083 | 5 | Gefechtschip | Chip | 100 | 0,167 | 600 | 0,053 | 220 | 24 | 5 280 | zwei Halbleiterwafer, Optikkristall, Leiterbündel, Keramiksubstrat |
| 084 | 5 | Logistikchip | Chip | 70 | 0,117 | 600 | 0,053 | 220 | 24 | 5 280 | Halbleiterwafer, Leiterbündel, Keramiksubstrat, Energiespeicherzelle |
| 085 | 5 | Medizinchip | Chip | 70 | 0,117 | 600 | 0,053 | 220 | 24 | 5 280 | Halbleiterwafer, Optikkristall, Keramiksubstrat, Leiterbündel |
| 086 | 5 | Energiechip | Chip | 90 | 0,15 | 600 | 0,053 | 220 | 24 | 5 280 | Halbleiterwafer, Keramiksubstrat, Supraleitersegment |
| 087 | 5 | Strukturzelle | Schiffbaugruppe | 45 000 | 81,82 | 550 | 0,288 | 600 | 48 | 28 800 | Strukturplatte, Leichtrahmen, Dichtungssystem |
| 088 | 5 | Panzerbaugruppe | Schiffbaugruppe | 35 000 | 5,38 | 6 500 | 0,288 | 600 | 48 | 28 800 | Panzersegment, Hitzeschildsegment, Aktuatoreinheit |
| 089 | 5 | Energieverteiler | Schiffbaugruppe | 8 000 | 14,55 | 550 | 0,126 | 350 | 36 | 12 600 | Energiechip, Leiterbündel, Supraleitersegment, Energiespeicherzelle |
| 090 | 5 | Fusionsreaktorkern | Schiffbaugruppe | 35 000 | 5,38 | 6 500 | 0,288 | 600 | 48 | 28 800 | Reaktorkammer, Fusionskapsel, Energiechip, Strahlenschutzsegment |
| 091 | 5 | Eleriumenergiezelle | Schiffbaugruppe | 3 000 | 5,45 | 550 | 0,708 | 300 | 36 | 10 800 | Eleriumkapsel, Energiechip, Strahlenschutzsegment |
| 092 | 5 | Eleriumtreibstoffkern | Schiffbaugruppe | 5 000 | 9,09 | 550 | 0,708 | 300 | 36 | 10 800 | Eleriumkapsel, Reaktorkammer, Energiechip, Kryofluid |
| 093 | 5 | Eleriuminitiator | Schiffbaugruppe | 1 000 | 1,82 | 550 | 0,708 | 300 | 36 | 10 800 | Eleriumkapsel, Gefechtschip, Strahlenschutzsegment |
| 094 | 5 | Triebwerkskern | Schiffbaugruppe | 25 000 | 45,45 | 550 | 0,288 | 600 | 48 | 28 800 | Reaktorkammer, Magnetspule, Hitzeschildsegment, Steuerchip |
| 095 | 5 | Manövertriebwerk | Schiffbaugruppe | 5 000 | 9,09 | 550 | 0,126 | 350 | 36 | 12 600 | Aktuatoreinheit, Magnetspule, Reaktionsmasse, Steuerchip |
| 096 | 5 | Reaktionsmassensystem | Schiffbaugruppe | 15 000 | 15 | 1 000 | 0,126 | 350 | 36 | 12 600 | Rohrleitungssystem, Kühlmittelbehälter, Reaktionsmasse, Steuerchip |
| 097 | 5 | Sensorfeld | Schiffbaugruppe | 2 000 | 3,64 | 550 | 0,126 | 350 | 36 | 12 600 | Sensorchip, Optikkristall, Leiterbündel, Energiespeicherzelle |
| 098 | 5 | Kommunikationsfeld | Schiffbaugruppe | 1 500 | 2,73 | 550 | 0,126 | 350 | 36 | 12 600 | Kommunikationschip, Optikkristall, Leiterbündel |
| 099 | 5 | Navigationssystem | Schiffbaugruppe | 3 000 | 5,45 | 550 | 0,126 | 350 | 36 | 12 600 | Navigationschip, Sensorfeld, Kommunikationsfeld |
| 100 | 5 | Gefechtsleitsystem | Schiffbaugruppe | 2 500 | 4,55 | 550 | 0,126 | 350 | 36 | 12 600 | Gefechtschip, Sensorfeld, Kommunikationsfeld |
| 101 | 5 | Projektilwaffenbaugruppe | Schiffbaugruppe | 12 000 | 21,82 | 550 | 0,126 | 350 | 36 | 12 600 | Aktuatoreinheit, Panzersegment, Gefechtschip, Chemischer Energiesatz |
| 102 | 5 | Strahlenwaffenbaugruppe | Schiffbaugruppe | 8 000 | 14,55 | 550 | 0,126 | 350 | 36 | 12 600 | Optikkristall, Supraleitersegment, Gefechtschip, Energieverteiler |
| 103 | 5 | Lenkflugkörperbaugruppe | Schiffbaugruppe | 10 000 | 18,18 | 550 | 0,126 | 350 | 36 | 12 600 | Gefechtschip, Sensorchip, Leichtrahmen, Chemischer Energiesatz |
| 104 | 5 | Schwerer Gefechtskopf | Schiffbaugruppe | 2 000 | 3,64 | 550 | 0,126 | 350 | 36 | 12 600 | Eleriuminitiator, Panzersegment, Gefechtschip |
| 105 | 5 | Schildemitterbaugruppe | Schiffbaugruppe | 8 000 | 14,55 | 550 | 0,126 | 350 | 36 | 12 600 | Supraleitersegment, Magnetspule, Energiechip, Energiespeicherzelle |
| 106 | 5 | Hangarbaugruppe | Schiffbaugruppe | 50 000 | 90,91 | 550 | 0,288 | 600 | 48 | 28 800 | Strukturzelle, Aktuatoreinheit, Druckhabitatsegment, Logistikchip |
| 107 | 5 | Frachtumschlagbaugruppe | Schiffbaugruppe | 25 000 | 45,45 | 550 | 0,126 | 350 | 36 | 12 600 | Strukturzelle, Aktuatoreinheit, Logistikchip |
| 108 | 5 | Lebenserhaltungsbaugruppe | Schiffbaugruppe | 10 000 | 18,18 | 550 | 0,126 | 350 | 36 | 12 600 | Lebenserhaltungszelle, Medizinchip, Rohrleitungssystem |
| 109 | 5 | Besatzungsbaugruppe | Schiffbaugruppe | 20 000 | 36,36 | 550 | 0,126 | 350 | 36 | 12 600 | Druckhabitatsegment, Lebenserhaltungsbaugruppe, Kommunikationschip |
| 110 | 5 | Truppenunterbringungsbaugruppe | Schiffbaugruppe | 35 000 | 63,64 | 550 | 0,126 | 350 | 36 | 12 600 | Druckhabitatsegment, Lebenserhaltungsbaugruppe, Panzerbaugruppe |
| 111 | 5 | Fördermaschinenbaugruppe | Industrie und Biologie | 60 000 | 109,09 | 550 | 0,24 | 500 | 48 | 24 000 | Strukturplatte, Aktuatoreinheit, Steuerchip, Rohrleitungssystem |
| 112 | 5 | Raffineriebaugruppe | Industrie und Biologie | 70 000 | 127,27 | 550 | 0,24 | 500 | 48 | 24 000 | Reaktorkammer, Rohrleitungssystem, Steuerchip, Hitzeschildsegment |
| 113 | 5 | Fertigungsmaschinenbaugruppe | Industrie und Biologie | 80 000 | 145,45 | 550 | 0,24 | 500 | 48 | 24 000 | Strukturplatte, Aktuatoreinheit, Steuerchip, Leiterbündel |
| 114 | 5 | Reinraumbaugruppe | Industrie und Biologie | 30 000 | 54,55 | 550 | 0,24 | 500 | 48 | 24 000 | Druckhabitatsegment, Lebenserhaltungszelle, Medizinchip, Steuerchip |
| 115 | 5 | Bioreaktorbaugruppe | Industrie und Biologie | 30 000 | 4,62 | 6 500 | 0,24 | 500 | 48 | 24 000 | Biomassekultur, Lebenserhaltungszelle, Rohrleitungssystem, Medizinchip |
| 116 | 5 | Energienetzbaugruppe | Industrie und Biologie | 40 000 | 72,73 | 550 | 0,24 | 500 | 48 | 24 000 | Energieverteiler, Leiterbündel, Energiespeicherzelle, Steuerchip |
| 117 | 5 | Nahrungsbasis | Industrie und Biologie | 20 000 | 25 | 800 | 0,014 | 120 | 12 | 1 440 | Biomassekultur, Nährlösung, Prozesswasser |
| 118 | 5 | Textilfaserbasis | Industrie und Biologie | 10 000 | 12,5 | 800 | 0,014 | 120 | 12 | 1 440 | Biomassekultur oder Polymergrundstoff, Industriechemikalien |
| 119 | 5 | Hygienebasis | Industrie und Biologie | 10 000 | 12,5 | 800 | 0,014 | 120 | 12 | 1 440 | Biomassekultur, Polymergrundstoff, Industriechemikalien, Prozesswasser |
| 120 | 5 | Pharmabasis | Industrie und Biologie | 2 000 | 2,5 | 800 | 0,014 | 120 | 12 | 1 440 | Pharmakultur, Industriechemikalien, Medizinchip |
| 121 | 5 | Haushaltswarenbasis | Industrie und Biologie | 20 000 | 36,36 | 550 | 0,014 | 120 | 12 | 1 440 | Polymergrundstoff, Leichtmetalllegierung, Glaswerkstoff, Keramikwerkstoff |
| 122 | 5 | Unterhaltungselektronikbasis | Industrie und Biologie | 1 000 | 1,82 | 550 | 0,014 | 120 | 12 | 1 440 | Steuerchip, Kommunikationschip, Leiterbündel, Polymergrundstoff |
| 123 | 6 | Korvettenrumpfmodul | Schiffsmodul Korvette | 2 700 000 | 8 437,5 | 320 | 12,6 | 17 500 | 72 | 1 260 000 | 25 × Strukturzelle, 25 × Panzerbaugruppe, 25 × Besatzungsbaugruppe |
| 124 | 6 | Korvettenantriebsmodul | Schiffsmodul Korvette | 1 495 800 | 3 116,25 | 480 | 27,7 | 17 627 | 72 | 1 269 144 | 25 × Triebwerkskern, 52 × Manövertriebwerk, 25 × Reaktionsmassensystem, 25 × Eleriumtreibstoffkern |
| 125 | 6 | Korvettenenergiemodul | Schiffsmodul Korvette | 353 160 | 735,75 | 480 | 27,67 | 17 608 | 72 | 1 267 776 | 25 × Eleriumenergiezelle, 25 × Energieverteiler, 26 × Energiespeicherzelle |
| 126 | 6 | Korvettenelektronikmodul | Schiffsmodul Korvette | 175 500 | 675 | 260 | 12,6 | 17 500 | 72 | 1 260 000 | 25 × Navigationssystem, 25 × Sensorfeld, 25 × Kommunikationsfeld |
| 127 | 6 | Korvettenwaffenmodul | Schiffsmodul Korvette | 556 200 | 1 158,75 | 480 | 12,66 | 17 585 | 72 | 1 266 120 | 25 × Strahlenwaffenbaugruppe, 25 × Lenkflugkörperbaugruppe, 26 × Gefechtsleitsystem |
| 128 | 6 | Korvettenversorgungsmodul | Schiffsmodul Korvette | 1 495 800 | 8 310 | 180 | 12,69 | 17 627 | 72 | 1 269 144 | 26 × Lebenserhaltungsbaugruppe, 25 × Besatzungsbaugruppe, 25 × Frachtumschlagbaugruppe |
| 129 | 6 | Zerstörerrumpfmodul | Schiffsmodul Zerstörer | 29 894 400 | 93 420 | 320 | 221,44 | 184 533 | 120 | 22 143 960 | 308 × Strukturzelle, 308 × Panzerbaugruppe, 152 × Besatzungsbaugruppe |
| 130 | 6 | Zerstörerantriebsmodul | Schiffsmodul Zerstörer | 12 457 800 | 25 953,75 | 480 | 313,75 | 184 560 | 120 | 22 147 200 | 308 × Triebwerkskern, 151 × Manövertriebwerk, 154 × Reaktionsmassensystem, 154 × Eleriumtreibstoffkern |
| 131 | 6 | Zerstörerenergiemodul | Schiffsmodul Zerstörer | 7 641 000 | 15 918,75 | 480 | 313,76 | 184 565 | 120 | 22 147 800 | 154 × Fusionsreaktorkern, 151 × Eleriumenergiezelle, 154 × Energieverteiler |
| 132 | 6 | Zerstörerelektronikmodul | Schiffsmodul Zerstörer | 1 164 240 | 4 477,85 | 260 | 221,76 | 184 800 | 120 | 22 176 000 | 154 × Navigationssystem, 154 × Gefechtsleitsystem, 154 × Kommunikationsfeld |
| 133 | 6 | Zerstörerwaffenmodul | Schiffsmodul Zerstörer | 7 642 080 | 15 921 | 480 | 221,51 | 184 591 | 120 | 22 150 920 | 308 × Projektilwaffenbaugruppe, 308 × Lenkflugkörperbaugruppe, 150 × Schwerer Gefechtskopf |
| 134 | 6 | Zerstörerversorgungsmodul | Schiffsmodul Zerstörer | 9 147 600 | 50 820 | 180 | 221,76 | 184 800 | 120 | 22 176 000 | 154 × Lebenserhaltungsbaugruppe, 154 × Besatzungsbaugruppe, 154 × Frachtumschlagbaugruppe |
| 135 | 6 | Kreuzerrumpfmodul | Schiffsmodul Kreuzer | 297 653 400 | 930 166,88 | 320 | 3 890,89 | 2 161 608 | 180 | 389 089 440 | 3 242 × Strukturzelle, 3 242 × Panzerbaugruppe, 1 083 × Strahlenschutzsegment |
| 136 | 6 | Kreuzerantriebsmodul | Schiffsmodul Kreuzer | 140 178 600 | 292 038,75 | 480 | 4 542,82 | 2 163 250 | 180 | 389 385 000 | 3 245 × Triebwerkskern, 2 163 × Manövertriebwerk, 2 163 × Reaktionsmassensystem, 1 082 × Eleriumtreibstoffkern |
| 137 | 6 | Kreuzerenergiemodul | Schiffsmodul Kreuzer | 66 576 600 | 138 701,25 | 480 | 5 191,16 | 2 162 982 | 180 | 389 336 760 | 2 157 × Eleriumenergiezelle, 1 082 × Fusionsreaktorkern, 2 163 × Energieverteiler |
| 138 | 6 | Kreuzerelektronikmodul | Schiffsmodul Kreuzer | 12 860 640 | 49 464 | 260 | 3 897,16 | 2 165 091 | 180 | 389 716 380 | 1 083 × Navigationssystem, 2 165 × Sensorfeld, 1 083 × Gefechtsleitsystem, 1 081 × Kommunikationsfeld |
| 139 | 6 | Kreuzerwaffenmodul | Schiffsmodul Kreuzer | 63 110 880 | 131 481 | 480 | 3 895,73 | 2 164 296 | 180 | 389 573 280 | 2 164 × Strahlenwaffenbaugruppe, 1 082 × Projektilwaffenbaugruppe, 1 082 × Lenkflugkörperbaugruppe, 2 165 × Schildemitterbaugruppe |
| 140 | 6 | Kreuzerversorgungsmodul | Schiffsmodul Kreuzer | 99 214 200 | 551 190 | 180 | 3 890,75 | 2 161 529 | 180 | 389 075 220 | 2 160 × Lebenserhaltungsbaugruppe, 2 162 × Besatzungsbaugruppe, 1 081 × Frachtumschlagbaugruppe |
| 141 | 6 | Frachterrumpfmodul | Schiffsmodul Frachter | 12 409 200 | 38 778,75 | 320 | 102,13 | 85 111 | 120 | 10 213 320 | 213 × Strukturzelle, 70 × Leichtrahmen, 71 × Druckhabitatsegment |
| 142 | 6 | Frachterantriebsmodul | Schiffsmodul Frachter | 4 978 800 | 10 372,5 | 480 | 144,68 | 85 108 | 120 | 10 212 960 | 71 × Triebwerkskern, 70 × Manövertriebwerk, 142 × Reaktionsmassensystem, 71 × Eleriumtreibstoffkern |
| 143 | 6 | Frachterenergiemodul | Schiffsmodul Frachter | 3 446 280 | 7 179,75 | 480 | 102,11 | 85 093 | 120 | 10 211 160 | 71 × Fusionsreaktorkern, 71 × Energieverteiler, 69 × Energiespeicherzelle |
| 144 | 6 | Frachterelektronikmodul | Schiffsmodul Frachter | 350 503,2 | 1 348,09 | 260 | 102,26 | 85 218 | 120 | 10 226 160 | 71 × Navigationssystem, 72 × Logistikchip, 71 × Kommunikationsfeld |
| 145 | 6 | Frachterladungsmodul | Schiffsmodul Frachter | 9 202 431,6 | 51 124,62 | 180 | 102,19 | 85 158 | 120 | 10 218 960 | 213 × Frachtumschlagbaugruppe, 71 × Strukturzelle, 11 × Logistikchip |
| 146 | 6 | Frachterversorgungsmodul | Schiffsmodul Frachter | 8 434 800 | 46 860 | 180 | 102,24 | 85 200 | 120 | 10 224 000 | 71 × Lebenserhaltungsbaugruppe, 71 × Besatzungsbaugruppe, 71 × Fertigungsmaschinenbaugruppe |
| 147 | 6 | Trägerschiffrumpfmodul | Schiffsmodul Trägerschiff | 81 102 600 | 253 445,62 | 320 | 1 716,46 | 715 190 | 240 | 171 645 600 | 954 × Strukturzelle, 715 × Panzerbaugruppe, 476 × Druckhabitatsegment |
| 148 | 6 | Trägerschiffantriebsmodul | Schiffsmodul Trägerschiff | 34 754 400 | 72 405 | 480 | 1 859,29 | 715 111 | 240 | 171 626 640 | 715 × Triebwerkskern, 478 × Manövertriebwerk, 715 × Reaktionsmassensystem, 238 × Eleriumtreibstoffkern |
| 149 | 6 | Trägerschiffenergiemodul | Schiffsmodul Trägerschiff | 15 471 000 | 32 231,25 | 480 | 2 148,75 | 716 250 | 240 | 171 900 000 | 712 × Eleriumenergiezelle, 239 × Fusionsreaktorkern, 478 × Energieverteiler |
| 150 | 6 | Trägerschiffelektronikmodul | Schiffsmodul Trägerschiff | 3 218 400 | 12 378,46 | 260 | 1 716,48 | 715 200 | 240 | 171 648 000 | 238 × Navigationssystem, 477 × Sensorfeld, 478 × Kommunikationsfeld, 238 × Gefechtsleitsystem |
| 151 | 6 | Trägerschiffhangarmodul | Schiffsmodul Trägerschiff | 64 395 075,6 | 357 750,42 | 180 | 1 716,72 | 715 301 | 240 | 171 672 240 | 954 × Hangarbaugruppe, 477 × Frachtumschlagbaugruppe, 1 × Logistikchip |
| 152 | 6 | Trägerschiffversorgungsmodul | Schiffsmodul Trägerschiff | 43 761 600 | 243 120 | 180 | 1 716,14 | 715 059 | 240 | 171 614 160 | 718 × Lebenserhaltungsbaugruppe, 715 × Besatzungsbaugruppe, 238 × Fertigungsmaschinenbaugruppe |
| 153 | 6 | Mannschaftstransportrumpfmodul | Schiffsmodul Mannschaftstransporter | 23 403 600 | 73 136,25 | 320 | 238,83 | 165 983 | 144 | 23 901 552 | 277 × Strukturzelle, 185 × Panzerbaugruppe, 182 × Druckhabitatsegment |
| 154 | 6 | Mannschaftstransportantriebsmodul | Schiffsmodul Mannschaftstransporter | 9 460 800 | 19 710 | 480 | 294,15 | 165 979 | 144 | 23 900 976 | 184 × Triebwerkskern, 188 × Manövertriebwerk, 184 × Reaktionsmassensystem, 92 × Eleriumtreibstoffkern |
| 155 | 6 | Mannschaftstransportenergiemodul | Schiffsmodul Mannschaftstransporter | 4 583 520 | 9 549 | 480 | 294,31 | 166 070 | 144 | 23 914 080 | 96 × Eleriumenergiezelle, 92 × Fusionsreaktorkern, 92 × Energieverteiler |
| 156 | 6 | Mannschaftstransportelektronikmodul | Schiffsmodul Mannschaftstransporter | 647 460 | 2 490,23 | 260 | 238,88 | 166 015 | 144 | 23 906 160 | 92 × Navigationssystem, 93 × Kommunikationsfeld, 92 × Sensorfeld |
| 157 | 6 | Mannschaftstransporttruppenmodul | Schiffsmodul Mannschaftstransporter | 12 959 740,8 | 71 998,56 | 180 | 238,94 | 166 061 | 144 | 23 912 784 | 277 × Truppenunterbringungsbaugruppe, 92 × Frachtumschlagbaugruppe, 68 × Medizinchip |
| 158 | 6 | Mannschaftstransportversorgungsmodul | Schiffsmodul Mannschaftstransporter | 7 175 520 | 39 864 | 180 | 239 | 166 100 | 144 | 23 918 400 | 277 × Lebenserhaltungsbaugruppe, 185 × Besatzungsbaugruppe, 87 × Pharmabasis |
| 159 | 6 | Fördermodul | Gebäudemodul | 122 100 | 254,38 | 480 | 1,2 | 1 000 | 120 | 120 000 | Fördermaschinenbaugruppe, Energieverteiler, Strukturzelle |
| 160 | 6 | Raffineriemodul | Gebäudemodul | 132 900 | 276,88 | 480 | 1,2 | 1 000 | 120 | 120 000 | Raffineriebaugruppe, Energieverteiler, Strukturzelle |
| 161 | 6 | Werkstoffmodul | Gebäudemodul | 170 700 | 355,62 | 480 | 1,2 | 1 000 | 120 | 120 000 | Fertigungsmaschinenbaugruppe, Raffineriebaugruppe, Energieverteiler |
| 162 | 6 | Komponentenmodul | Gebäudemodul | 181 500 | 378,12 | 480 | 1,2 | 1 000 | 120 | 120 000 | zwei Fertigungsmaschinenbaugruppen, Steuerchip, Energieverteiler |
| 163 | 6 | Elektronikmodul | Gebäudemodul | 127 500 | 265,62 | 480 | 1,2 | 1 000 | 120 | 120 000 | Reinraumbaugruppe, Fertigungsmaschinenbaugruppe, Energieverteiler |
| 164 | 6 | Bioproduktionsmodul | Gebäudemodul | 84 300 | 175,62 | 480 | 1,2 | 1 000 | 120 | 120 000 | zwei Bioreaktorbaugruppen, Lebenserhaltungsbaugruppe, Energieverteiler |
| 165 | 6 | Nahrungsmittelmodul | Gebäudemodul | 118 900 | 247,71 | 480 | 1,2 | 1 000 | 120 | 120 000 | Bioreaktorbaugruppe, Fertigungsmaschinenbaugruppe, Logistikchip |
| 166 | 6 | Kraftwerksmodul | Gebäudemodul | 49 700 | 103,54 | 480 | 1,8 | 1 000 | 120 | 120 000 | Eleriumenergiezelle, Reaktorkammer, Energieverteiler, Strahlenschutzsegment |
| 167 | 6 | Habitatmodul | Gebäudemodul | 113 500 | 236,46 | 480 | 1,2 | 1 000 | 120 | 120 000 | drei Druckhabitatsegmente, zwei Lebenserhaltungsbaugruppen, Energienetzbaugruppe |
| 168 | 6 | Lagermodul | Gebäudemodul | 124 300 | 258,96 | 480 | 1,2 | 1 000 | 120 | 120 000 | zwei Strukturzellen, Frachtumschlagbaugruppe, Logistikchip |
| 169 | 6 | Werftmodul | Gebäudemodul | 329 400 | 686,25 | 480 | 1,2 | 1 000 | 120 | 120 000 | drei Fertigungsmaschinenbaugruppen, Frachtumschlagbaugruppe, Energienetzbaugruppe |
| 170 | 6 | Verteidigungsmodul | Gebäudemodul | 89 100 | 185,62 | 480 | 1,2 | 1 000 | 120 | 120 000 | zwei Panzerbaugruppen, Gefechtsleitsystem, Sensorfeld, Energieverteiler |
| 171 | 6 | Forschungsmodul | Gebäudemodul | 36 300 | 75,62 | 480 | 1,2 | 1 000 | 120 | 120 000 | Reinraumbaugruppe, Sensorfeld, Medizinchip, Kommunikationsfeld |
| 172 | 6 | Trinkwasserration | Konsumware | 1 000 | 1 | 1 000 | 0,0048 | 60 | 8 | 480 | Nahrungsbasis, Prozesswasser |
| 173 | 6 | Grundnahrung | Konsumware | 1 000 | 2 | 500 | 0,0048 | 60 | 8 | 480 | Nahrungsbasis |
| 174 | 6 | Standardnahrung | Konsumware | 1 000 | 2 | 500 | 0,0048 | 60 | 8 | 480 | Nahrungsbasis, Hygienebasis |
| 175 | 6 | Hygienewaren | Konsumware | 500 | 1 | 500 | 0,0048 | 60 | 8 | 480 | Hygienebasis, Haushaltswarenbasis |
| 176 | 6 | Grundkleidung | Konsumware | 500 | 1 | 500 | 0,0048 | 60 | 8 | 480 | Textilfaserbasis |
| 177 | 6 | Schutzkleidung | Konsumware | 500 | 1 | 500 | 0,0048 | 60 | 8 | 480 | Textilfaserbasis, Haushaltswarenbasis |
| 178 | 6 | Grundmedizin | Konsumware | 100 | 0,2 | 500 | 0,0048 | 60 | 8 | 480 | Pharmabasis, Hygienebasis |
| 179 | 6 | Erweiterte Medizin | Konsumware | 50 | 0,1 | 500 | 0,012 | 100 | 12 | 1 200 | Pharmabasis, Medizinchip |
| 180 | 6 | Haushaltswaren | Konsumware | 1 000 | 2 | 500 | 0,0048 | 60 | 8 | 480 | Haushaltswarenbasis |
| 181 | 6 | Hochwertige Haushaltswaren | Konsumware | 1 000 | 2 | 500 | 0,0048 | 60 | 8 | 480 | Haushaltswarenbasis, Unterhaltungselektronikbasis |
| 182 | 6 | Unterhaltungselektronik | Konsumware | 200 | 0,4 | 500 | 0,0048 | 60 | 8 | 480 | Unterhaltungselektronikbasis |
| 183 | 6 | Infanterieausrüstung | Konsumware | 3 000 | 6 | 500 | 0,0048 | 60 | 8 | 480 | Schutzkleidung, Kommunikationschip, Haushaltswarenbasis |
| 184 | 6 | Schwere Bodenausrüstung | Konsumware | 10 000 | 20 | 500 | 0,048 | 200 | 24 | 4 800 | Panzerbaugruppe, Projektilwaffenbaugruppe, Gefechtschip |
| 185 | 7 | Korvette | Schiff | 7 000 000 | 31 818,18 | 220 | 181 | 2 500 | 240 | 600 000 | Korvettenrumpfmodul, Korvettenantriebsmodul, Korvettenenergiemodul, Korvettenelektronikmodul, Korvettenwaffenmodul, Korvettenversorgungsmodul |
| 186 | 7 | Zerstörer | Schiff | 70 000 000 | 269 230,77 | 260 | 2 675,7 | 12 500 | 480 | 6 000 000 | Zerstörerrumpfmodul, Zerstörerantriebsmodul, Zerstörerenergiemodul, Zerstörerelektronikmodul, Zerstörerwaffenmodul, Zerstörerversorgungsmodul |
| 187 | 7 | Kreuzer | Schiff | 700 000 000 | 2 187 500 | 320 | 53 821,2 | 50 000 | 1 200 | 60 000 000 | Kreuzerrumpfmodul, Kreuzerantriebsmodul, Kreuzerenergiemodul, Kreuzerelektronikmodul, Kreuzerwaffenmodul, Kreuzerversorgungsmodul |
| 188 | 7 | Frachter | Schiff | 40 000 000 | 363 636,36 | 110 | 893,9 | 4 000 | 400 | 1 600 000 | Frachterrumpfmodul, Frachterantriebsmodul, Frachterenergiemodul, Frachterelektronikmodul, Frachterladungsmodul, Frachterversorgungsmodul |
| 189 | 7 | Trägerschiff | Schiff | 250 000 000 | 1 785 714,29 | 140 | 21 169,1 | 24 000 | 900 | 21 600 000 | Trägerschiffrumpfmodul, Trägerschiffantriebsmodul, Trägerschiffenergiemodul, Trägerschiffelektronikmodul, Trägerschiffhangarmodul, Trägerschiffversorgungsmodul |
| 190 | 7 | Mannschaftstransporter | Schiff | 60 000 000 | 375 000 | 160 | 2 435 | 7 500 | 480 | 3 600 000 | Mannschaftstransportrumpfmodul, Mannschaftstransportantriebsmodul, Mannschaftstransportenergiemodul, Mannschaftstransportelektronikmodul, Mannschaftstransporttruppenmodul, Mannschaftstransportversorgungsmodul |
| 191 | 7 | Rohstoffförderanlage | Planetare Anlage | 302 800 | 865,14 | 350 | 15 | 4 000 | 360 | 1 440 000 | zwei Fördermodule, Kraftwerksmodul |
| 192 | 7 | Raffinerie | Planetare Anlage | 453 000 | 1 294,29 | 350 | 15 | 4 000 | 360 | 1 440 000 | zwei Raffineriemodule, Kraftwerksmodul, Lagermodul |
| 193 | 7 | Werkstofffabrik | Planetare Anlage | 530 900 | 1 516,86 | 350 | 15 | 4 000 | 360 | 1 440 000 | zwei Werkstoffmodule, Kraftwerksmodul, Lagermodul |
| 194 | 7 | Komponentenfabrik | Planetare Anlage | 553 200 | 1 580,57 | 350 | 15 | 4 000 | 360 | 1 440 000 | zwei Komponentenmodule, Kraftwerksmodul, Lagermodul |
| 195 | 7 | Elektronikfabrik | Planetare Anlage | 351 300 | 1 003,71 | 350 | 15 | 4 000 | 360 | 1 440 000 | zwei Elektronikmodule, Kraftwerksmodul, Forschungsmodul |
| 196 | 7 | Biomasseanlage | Planetare Anlage | 341 800 | 976,57 | 350 | 15 | 4 000 | 360 | 1 440 000 | zwei Bioproduktionsmodule, Kraftwerksmodul, Habitatmodul |
| 197 | 7 | Nahrungsmittelfabrik | Planetare Anlage | 459 800 | 1 313,71 | 350 | 14,4 | 4 000 | 360 | 1 440 000 | zwei Nahrungsmittelmodule, Bioproduktionsmodul, Lagermodul |
| 198 | 7 | Eleriumkraftwerk | Planetare Anlage | 245 400 | 701,14 | 350 | 30,6 | 6 000 | 480 | 2 880 000 | drei Kraftwerksmodule, Verteidigungsmodul |
| 199 | 7 | Koloniehabitat | Planetare Anlage | 530 000 | 1 514,29 | 350 | 15 | 4 000 | 360 | 1 440 000 | drei Habitatmodule, Kraftwerksmodul, Lagermodul |
| 200 | 7 | Warenlager | Planetare Anlage | 475 900 | 1 359,71 | 350 | 14,4 | 4 000 | 360 | 1 440 000 | drei Lagermodule, Verteidigungsmodul |
| 201 | 7 | Modulwerft | Planetare Anlage | 1 197 100 | 3 420,29 | 350 | 15 | 4 000 | 360 | 1 440 000 | drei Werftmodule, Kraftwerksmodul, Lagermodul |
| 202 | 7 | Schiffswerft | Planetare Anlage | 2 054 900 | 5 871,14 | 350 | 73,2 | 10 000 | 720 | 7 200 000 | fünf Werftmodule, zwei Kraftwerksmodule, zwei Lagermodule |
| 203 | 7 | Planetenverteidigung | Planetare Anlage | 363 900 | 1 039,71 | 350 | 29,4 | 6 000 | 480 | 2 880 000 | drei Verteidigungsmodule, Kraftwerksmodul, Forschungsmodul |
| 204 | 7 | Forschungskomplex | Planetare Anlage | 294 700 | 842 | 350 | 29,4 | 6 000 | 480 | 2 880 000 | drei Forschungsmodule, Elektronikmodul, Kraftwerksmodul |
| 205 | 7 | Grundversorgungspaket | Paket | 3 090 | 7,36 | 420 | 0,0036 | 60 | 6 | 360 | Trinkwasserration, Grundnahrung, Hygienewaren, Grundkleidung |
| 206 | 7 | Standardversorgungspaket | Paket | 4 120 | 9,81 | 420 | 0,0036 | 60 | 6 | 360 | Trinkwasserration, Standardnahrung, Hygienewaren, Grundkleidung, Haushaltswaren |
| 207 | 7 | Medizinpaket | Paket | 1 700 | 4,05 | 420 | 0,0036 | 60 | 6 | 360 | Trinkwasserration, Grundmedizin, Erweiterte Medizin, Hygienewaren |
| 208 | 7 | Komfortpaket | Paket | 2 790 | 6,64 | 420 | 0,0036 | 60 | 6 | 360 | Standardnahrung, Schutzkleidung, Haushaltswaren, Unterhaltungselektronik |
| 209 | 7 | Luxuspaket | Paket | 2 320 | 5,52 | 420 | 0,0036 | 60 | 6 | 360 | Standardnahrung, Erweiterte Medizin, Unterhaltungselektronik, Hochwertige Haushaltswaren |
| 210 | 7 | Infanteriepaket | Paket | 5 770 | 13,74 | 420 | 0,014 | 120 | 12 | 1 440 | Infanterieausrüstung, Grundmedizin, Trinkwasserration, Grundnahrung, Hygienewaren |
| 211 | 7 | Schweres Truppenpaket | Paket | 13 000 | 30,95 | 420 | 0,014 | 120 | 12 | 1 440 | Schwere Bodenausrüstung, Schutzkleidung, Erweiterte Medizin, Trinkwasserration, Standardnahrung |

## 3. Balancehinweise

- Die Rohstoffausbeuten bilden technische Förderbarkeit ab, nicht den
  Marktpreis. Edelmetall, Isotopenträger und Elerium bleiben trotz kleiner
  Masse wirtschaftlich bedeutend.
- Volumen ist für Frachtraum und Lager entscheidend, Masse für Beschleunigung,
  Treibstoffbedarf und gegebenenfalls Gatewaykosten.
- Ein Kauf von Unterprodukten verlagert deren bereits geleistete Arbeit zum
  Zulieferer. Die Arbeitswerte der Endproduktzeile beschreiben nur Montage und
  Abnahme, nicht erneut die Arbeit der gesamten Unterkette.
- Dasselbe gilt für Prozessenergie. Der Eleriumbedarf einer Zeile nennt den
  Bedarf des konkreten Auftrags und das im Endprodukt gebundene Elerium; die
  schon bei Zulieferern verbrauchte Prozessenergie wird nicht doppelt gezählt.
- Die Werte sollten anschließend in Monatsläufen für Heimatkolonie,
  Industrieplanet, Blockade und vollständige Autarkie getestet werden.
