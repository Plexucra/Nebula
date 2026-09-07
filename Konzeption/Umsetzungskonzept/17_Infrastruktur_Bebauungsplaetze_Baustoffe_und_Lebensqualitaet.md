# 17. Infrastruktur, Bebauungsplätze, Baustoffe und Lebensqualität

Redesign des Bebauungssystems und Neubalance der Bevölkerung nach drei
Korrekturrunden des Nutzers. Endgültiger Stand der Entscheidungen:

- Es gibt **ein** Gebäude "Infrastruktur" (`b_infrastructure`), das das
  Energienetz ersetzt. Jede Stufe liefert **einen** Bebauungsplatz; jede Stufe
  jedes anderen Gebäudes belegt genau einen. Infrastruktur selbst belegt keinen.
- **Alles kostet Baustoffe, auch Stufe 1.** Kein Baustoff-Startlager.
- **Minimalstart** einer Heimatkolonie: Wohnkomplex 1 + Industriekomplex 1 +
  Infrastruktur 2 – beide Plätze belegt, keiner frei. Der erste Zug ist
  zwangsläufig Infrastruktur → 3, deren Baustoffe der Industriekomplex 1
  selbst produzieren muss. Startflotte, Startwallet (6500 Cr) und
  Bodentruppen bleiben unverändert.
- Aufbewahrung, Kataloge und Konstanten bleiben in `/shared` (eine Quelle),
  Formeln nur im Backend.

## A. Infrastruktur und Bebauungsplätze

| Regel | Umsetzung |
|---|---|
| Gebäudekategorien | `Infrastructure` nur noch für `b_infrastructure`; Wohnkomplex ist `Housing` und liefert allein die Wohnkapazität (`PowerGrid.effectiveHousingCapacity` summiert nur `Housing`); der frühere 2500/Stufe-Wohnanteil des Energienetzes entfällt |
| Elerium-Bedarf | `Formulas.infrastructureEleriumPerHour(L) = 0,005 × L^1,25` je Spielstunde (Stufe 2: 0,0119/h, 3: 0,0198/h, 5: 0,0374/h, 10: 0,0889/h). Blackout-Mechanik unverändert (Deckung < Schwelle → Produktion ×0,1, Kernwerte halbiert), hängt jetzt an der Infrastruktur |
| Bebauungsplätze | `BuildingCommands.buildSlots`: `total = Infrastruktur-Stufe × 1`, `used = Σ Stufen aller anderen Gebäude` **inklusive laufender Ausbauten** (ein beauftragter Ausbau belegt seinen Platz sofort, sonst ließen sich mehr Aufträge einreihen als Plätze da sind). Harte Grenze in `queueBuilding`: "Kein freier Bebauungsplatz – Infrastruktur ausbauen." |
| Höchststufen | `maxLevel` ist für alle Gebäude entfallen (Feld gelöscht, UI "/ max" entfernt). Infrastruktur ist **planetweit** begrenzt: Σ Infrastruktur-Stufen aller Kolonien des Planeten ≤ `maxInfrastructureByPlanetSize` |
| Bebauungspunkte | `buildPointsPerLevel`, `Planet.buildCapacity`, `Formulas.overbuildFactor`/`buildPointsUsed`, die `overbuildFactor`-Query, der Überbebauungs-Malus im Gebäudeunterhalt und die UI-Anzeigen "Bebauungskapazität"/"Überbebauungsfaktor" sind ersatzlos gelöscht |
| Kosten der nächsten Infrastruktur-Stufe | `T` = Σ Infrastruktur-Stufen aller Kolonien des Planeten (inkl. laufender). Credits = `90 × (T+1) × (1 + 0,12·T)`, Stunden = `0,8 × (T+1)`, Baustoffmenge mit Stufe `T+1`. T=2 → 335 Cr/2,4 h; T=5 → 864 Cr; T=10 → 2 178 Cr; T=20 → 6 426 Cr – deutlich steiler als die Gebäudekurve (`1 + 0,08·Stufe`), dicht besiedelte Planeten werden für alle teurer (Mechanik/07 §2) |
| Rückbau | Infrastruktur nur, solange die bestehenden Gebäude danach noch Plätze haben |

**Planetweite Obergrenzen (abweichend vom Vorschlag, begründet):** Der
Vorschlag 8/12/18/25 stammte aus der Annahme "10 Plätze je Stufe". Mit **1 Platz
je Stufe** wäre ein kleiner Planet mit 8 Stufen = 8 Gebäudestufen insgesamt
(z. B. Industrie 4 + Werft 3 + Wohnkomplex 1) ein hartes Ende noch vor jeder
Spezialisierung. Gewählt: **Klein 16, Mittel 24, Groß 36, Riesig 50**. Ein
kleiner Planet trägt damit z. B. Wohnkomplex 3 + Industrie 6 + Werft 4 +
Ausbildung 2 + Abwehr 1 – eine funktionsfähige, aber begrenzte Kolonie; die
Grenze ist der strategische Grund, weitere Planeten zu kolonisieren. Da die
Heimatwelt eine zufällige Größe hat, trifft das auch Startspieler –
gewollt (Planeten unterscheiden sich).

`colonizePlanet` legt neue Kolonien mit demselben Minimalstart an
(Wohnkomplex 1, Industrie 1, Infrastruktur 2).

## B. Baustoffe für Bebauung

`BuildingType.materials = [{ productTypeId, baseQuantity, fromLevel }]`,
Menge je Ausbau = `ceil(baseQuantity × Zielstufe^1,3)`
(`Formulas.buildingMaterialQuantity`; bei Infrastruktur ist die "Zielstufe"
`T+1`, die planetweite Summe). Baustoffe werden beim Einreihen aus dem
Kolonielager abgezogen; fehlende → Ablehnung mit Auflistung
("Fehlende Baustoffe: p_stahl (9 benötigt, 0 vorhanden), …"); Abbruch
erstattet Credits und Baustoffe, Rückbau nur Credits (50 %).

**Zuordnung (`shared/catalog/buildings.json`)** – die ersten Stufen verlangen
ausschließlich Tier-2-Grundwerkstoffe, die ein Industriekomplex 1 mit
`autoProduceMissing` aus Rohstoffen selbst herstellen kann:

| Gebäude | ab Stufe 1 (Basis) | später |
|---|---|---|
| Infrastruktur | Stahl 2, Leitermetall 1 | Leiterbündel 1 (ab 4), Energienetzbaugruppe 1 (ab 8) |
| Wohnkomplex | Stahl 2, Glaswerkstoff 1 | Druckhabitatsegment 1 (ab 5) |
| Industriekomplex | Stahl 2 | Strukturplatte 1 (ab 4), Fertigungsmaschinenbaugruppe 1 (ab 8) |
| Werft | Stahl 2, Leichtmetalllegierung 1 | Strukturplatte 1 + Leichtrahmen 1 (ab 4), Strukturzelle 1 (ab 7) |
| Ausbildungszentrum | Stahl 2, Keramikwerkstoff 1 | Druckhabitatsegment 1 (ab 5) |
| Planetare Abwehr | Stahl 2, Hochtemperaturlegierung 1 | Panzersegment 1 (ab 3), Panzerbaugruppe 1 (ab 6) |

Beispiele: Infrastruktur 3 (T=2 → Stufe 3): Stahl 9, Leitermetall 5.
Industrie 2: Stahl 5. Werft 1: Stahl 2, Leichtmetalllegierung 1. Industrie 5:
Stahl 17, Strukturplatte 9.

**Produktbaum aufgeräumt** (`shared/catalog/products.json`, 214 → 187):
gelöscht wurden die 14 `Facility`-Produkte (`p_foerderanlage`, `p_raffinerie`,
`p_werkstofffabrik`, `p_komponentenfabrik`, `p_elektronikfabrik`,
`p_biomasseanlage`, `p_nahrungsmittelfabrik`, `p_eleriumkraftwerk`,
`p_koloniehabitat`, `p_warenlager`, `p_modulwerft`, `p_schiffswerft`,
`p_planetenverteidigung`, `p_forschungskomplex`) und die 13 `*modul`-
Zwischenprodukte, die per Referenzsuche **ausschließlich** von diesen
verbraucht wurden (`p_foerdermodul`, `p_raffineriemodul`, `p_werkstoffmodul`,
`p_komponentenmodul`, `p_elektronikmodul`, `p_bioproduktionsmodul`,
`p_nahrungsmittelmodul`, `p_kraftwerksmodul`, `p_habitatmodul`, `p_lagermodul`,
`p_werftmodul`, `p_verteidigungsmodul`, `p_forschungsmodul`). Die Kategorie
`Facility` ist aus beiden Typsystemen entfernt. **Behalten, weil anderswo
Zutat:** alle Baustoff-Kandidaten (Stahl, Strukturplatte, Leichtrahmen,
Strukturzelle, Panzersegment/-baugruppe, Druckhabitatsegment,
Energienetz-/Fertigungsmaschinenbaugruppe …) sind Zutaten von Schiffen,
Bodeneinheiten oder Konsumgütern. Sechs `BuildingMaterial` ohne Verbraucher
(`p_katalysatormetall`, `p_radiowerkstoff`, `p_foerdermaschinenbaugruppe`,
`p_raffineriebaugruppe`, `p_reinraumbaugruppe`, `p_bioreaktorbaugruppe`)
bleiben bewusst stehen – sie sind Handelsware bzw. Spezialisierungsziele der
Bots, kein toter Ballast wie die Facilities.

**Grundnahrung/Grundmedizin abgeflacht** (Teil C): beide Tier 1 mit 2 h direkt
aus Rohstoffen (Nahrung: Wasser + Kohlenstoffmineral, Medizin: Wasser +
Salzmineral) statt Tier 2 über Prozesswasser/Kohlenstoffraffinat bzw.
Industriesalze.

## C. Lebensqualität, Wohnraum und Bevölkerungsdynamik

### Befund nachgerechnet

Der Review-Befund ging von 420 Einwohnern und Industrie 4 aus. Mit dem
Minimalstart gilt: 120 Einwohner, Industrie 1,
`workforceFactor(120) = clamp(120/400, 0,35, 5) = 0,35` → `speed = 0,35`.
Echte Server-Werte (`previewProductionChain`, leeres Lager):

| Startauftrag | `ChainPlan.totalHours` | je Stück |
|---|---|---|
| Grundnahrung × 5 | 55,4 h | 11,1 h |
| Grundmedizin × 5 | 50,5 h | 10,1 h |
| Stabilisiertes Elerium × 3 | 24,9 h | 8,3 h |

Mit den **alten** Bedarfssätzen (0,0004/Kopf/Tick) wäre allein Nahrung bei
131 % Auslastung gelandet – unmöglich. Dazu kam ein versteckter Faktor:
`runConsumption` rundete den Tick-Bedarf mit `ceil` auf ganze Stück auf, kaufte
also bei jedem Bedarf < 1 eine ganze Einheit je Sekunde. Der im Review
gemessene Start-Lebensstandard von 107 % stammte allein aus dem 50er-Vorrat.

### Hebel

1. **Bruchteil-Konsum**: `runConsumption` kauft exakt `need − bought` statt
   `ceil` davon; Deckung ist dadurch exakt 1,0 statt "1,5 oder 0".
2. **Flache Rezepte** für Grundnahrung/-medizin (Teil B): Kette 4 h statt 11 h.
3. **Bedarfssätze** (je Kopf und Tick): Nahrung 0,0004 → **0,00008**,
   Medizin 0,00015 → **0,00004**, Elektronik 0,0001 → **0,00004**.
4. **Elerium-Startauftrag 1 → 3 Stück** (siehe "Langzeit-Befund").

Warteschlangen-Auslastung im Startzustand (120 Einwohner, Industrie 1):
Nahrung 26,6 % + Medizin 12,1 % + Elerium 9,9 % = **48,6 %** – die geforderten
≤ 50 %, der Rest bleibt für eigene Aufträge des Spielers.

### Wohnraum: 20.000 je Stufe, Verdopplung, logistisches Wachstum

Wohnkomplex Stufe 1 fasst **20.000** Einwohner, jede weitere Stufe verdoppelt:
`capacity(L) = 20 000 × 2^(L−1)`; Stufe 20 ≈ **10,5 Milliarden**. Credits und
Baustoffe verdoppeln sich mit (`Formulas.housingUpgradeCost` =
`base × 2^Stufe`, `housingMaterialQuantity` = `ceil(base × 2^(Stufe−1))`) –
eine polynomiale Kurve wäre gegenüber verdoppelter Kapazität faktisch gratis.
Die **Bauzeit** bleibt bewusst polynomial: der Engpass soll die Investition
sein, nicht das Warten (Stufe 20 sonst 2^19 Stunden).

Zwingende Folge: die alte Wachstumsformel war proportional zum **freien**
Wohnraum (`Rest × 0,018 × Zustandsfaktor`) – bei Kapazität 20.000 und 120
Einwohnern wären das ≈ 358 Einwohner je Spielstunde gewesen. Ersetzt durch
**logistisches Wachstum**:

```
delta/h = 0,01 × Bevölkerung × (1 − Bevölkerung/Kapazität) × growthConditionFactor
```

mit dem unveränderten Totband darüber (< 30 % schrumpfen, 30–50 % halten,
≥ 50 % wachsen). Der Wohnraum ist damit wieder eine echte **Obergrenze**
statt eines Wachstumstreibers; begrenzend im Frühspiel ist die
Nahrungsversorgung. Der Sonderfall "Überbevölkerung" entfällt – oberhalb der
Kapazität wird der Klammerterm von selbst negativ. `BASE = 0,01` ist aus dem
Ziel "sichtbares Wachstum in wenigen Realminuten" hergeleitet:
`k = ln(Ziel/Start)/t`, mit Zustandsfaktor 0,75 ergibt 0,01 einen Anstieg von
120 auf über 1000 Einwohner in ≈ 15 Realminuten.

### Geldkreislauf: Verkaufspreis strukturell hergeleitet

Der E2E-Lauf scheiterte zunächst daran, dass der Testspieler nach ≈ 33 Minuten
die 335 Credits für Infrastruktur 3 nicht mehr aufbringen konnte. Gemessene
Ursache (Transaktionen je Tick, Preis 20): Einnahme 0,50, Abfluss 0,50
Gebäudeunterhalt + Löhne + Flottenunterhalt – das Spieler-Wallet war nach
≈ 20 Realminuten bei 0, während das Bevölkerungs-Wallet auf ≈ 8.000 anwuchs.

Geld wird im Spiel nicht vernichtet, sondern kreist: Spieler → Löhne/Unterhalt
→ Bevölkerung → Konsum → Spieler. Neues Geld entsteht **nur** beim Wachstum
über den bisherigen Höchststand (`CREDITS_PER_NEW_INHABITANT` = 8). Im
Gleichgewicht muss der Konsum die Abflüsse deshalb exakt decken – ein
dauerhafter Überschuss der einen Seite ist zwangsläufig das Verarmen der
anderen. Bilanz je Tick:

```
Einnahme = Bevölkerung × (0,00008 + 0,00004) × Preis
Abfluss  = Bevölkerung × 0,008 (Löhne) + 3,0 (Gebäude) + 0,2 × Schiffe (Flotte)
P*       = (0,008·Bev + 3,0 + 0,2·Schiffe) / (0,00012·Bev)
```

| Preis | Startzustand (Bev 120, 13 Schiffe) | gemessen |
|---|---|---|
| 20 | −6,27/Tick | Wallet nach ≈ 20 min leer ✗ |
| 300 | −2,24/Tick, bei Bev 200 ±0 | +0,37/Tick bei Bev 200 ✓ Modell bestätigt |
| 500 | +0,64/Tick | Spieler +3,1/Tick, **Bevölkerungs-Wallet 1865 → 263** in 10 min ✗ |

Gewählt: **300 Credits/Stück** – der Gleichgewichtspreis bei der
eingeschwungenen Bevölkerung. Die Aufbauphase läuft mit einem kleinen,
sich selbst korrigierenden Minus, gedeckt aus Startguthaben und dem
gleichzeitig geschöpften Wachstumsgeld. Das Modell wurde an zwei unabhängigen
Messungen bestätigt (Abweichung < 0,4/Tick).

### Messreihe (i): Startkolonie, eingeschwungen, 18 Minuten

Der 50er-Anfangsvorrat wurde in die Frachtflotte verladen und die Orders auf
0,5 Stück Rest neu angelegt, damit allein die laufende Produktion trägt.
`t; SoL %; Bev.; Deckung N/M; Zustand; Lager N/M/Elerium; Spieler-Wallet; Bev.-Wallet`:

```
   0; 100,0;  120; –    –;    Growing;   0,0  0,0 25,00;  6500;  900
  90;  25,0;  140; 0,00 1,00; Shrinking; 0,0  0,0 24,57;  6207; 1378   ← Vorrats-Entfernung (Messartefakt)
 180;  50,0;  116; 1,00 0,00; Holding;   4,5  0,0 24,14;  5743; 1835   ← erste Nahrungscharge
 270;  74,0;  120; 1,00 1,00; Growing;   3,5  4,5 23,72;  5425; 2128
 450;  74,3;  226; 1,00 1,00; Growing;   1,0  3,5 25,86;  5268; 2899
 631;  63,2;  423; 0,22 1,00; Growing;   6,5  6,5 28,00;  5819; 3861
 811;  62,0;  776; 0,20 1,00; Growing;   8,5 12,0 33,14;  7619; 4798
 991;  69,2; 1380; 1,00 1,00; Growing;  14,5 25,0 44,29; 11493; 5656
1051;  71,0; 1651; 0,82 1,00; Growing;  18,0 31,5 50,00; 13376; 5912
```

Ergebnis: **Lebensstandard-Plateau ≈ 75 % (≥ 50 %)**, Bevölkerung wächst
116 → 1651, **beide Wallets wachsen** (Spieler 5268 → 13.376 über 14,5 min,
Bevölkerung 1835 → 5912), Elerium netto positiv (24 → 50). Damit ist das
Abnahmekriterium "verarmt über ≥ 15 Realminuten nicht" erfüllt, und
Infrastruktur 3 (335 Cr) ist jederzeit bezahlbar.

### Messreihe (ii): Gegenprobe Schrumpfung und Totband

Startaufträge abgebrochen, Start-Verkaufsorders storniert; danach alle 5 s eine
Order über 0,02 Stück Grundnahrung (konstanter kleiner Zufluss):

```
Phase 1:   0; SoL 100,0; Bev 120,0; Growing;   +1,440/h
          15; SoL   0,5; Bev 116,0; Shrinking; −1,142/h
          90; SoL   0,0; Bev  85,9; Shrinking; −0,859/h
Phase 2: 135; SoL  20,9; Bev  79,9; Shrinking; −0,242/h
         315; SoL  22,5; Bev  76,8; Shrinking; −0,191/h
         495; SoL  23,9; Bev  74,3; Shrinking; −0,152/h
         676; SoL  25,1; Bev  72,3; Shrinking; −0,118/h
```

Ergebnis: **unter 30 % setzt Schrumpfung ein**, proportional zum Fehlbetrag;
bei konstantem Zufluss steigt der Lebensstandard mit sinkender Bevölkerung
(20,9 → 25,1 %) und die Schrumpfrate fällt monoton (−0,81 → −0,12/h) – das
System läuft **ohne Oszillation** auf das Totband zu.

### Messreihe (iii): Bevölkerung bis zum Nahrungs-Plateau

`t; Bev.; Deckung N; Deckung M; SoL %; Zustand; Wachstum/h; Wohnkapazität; Wallet`:

```
   0; 1866; 1,00; 1,00; 66,4; Growing;  +13,49; 20000; 14911
  60; 2206; 1,00; 1,00; 73,2; Growing;  +17,25; 20000; 17402
 180; 3016; 1,00; 1,00; 59,8; Growing;  +18,39; 20000; 23625
 300; 4062; 0,54; 1,00; 57,5; Growing;  +22,35; 20000; 32485
 420; 5092; 0,23; 1,00; 48,1; Holding;    0,00; 20000; 42142   ← Wachstum stoppt an der NAHRUNG
 540; 5426; 0,23; 0,30; 19,0; Shrinking; −19,86; 20000; 49987
 661; 4596; 0,27; 0,36; 23,3; Shrinking; −10,33; 20000; 50102
 901; 3378; 0,16; 0,50; 24,0; Shrinking;  −6,71; 20000; 50270
1141; 2572; 0,30; 0,39; 25,7; Shrinking;  −3,69; 20000; 50392
```

Ergebnis und gewünschtes Spielerlebnis: die Bevölkerung wächst sichtbar von
120 auf über 5.000 und **plateauiert dann an der Nahrungsversorgung**
(Deckung 0,23 bei Kapazität 20.000 – der Wohnraum ist ausdrücklich NICHT die
Grenze). Danach überschwingt sie leicht und schrumpft mit monoton fallender
Rate (−19,9 → −3,7/h) auf das Totband zu. Die Oberfläche zeigt am Plateau
genau das: Zustand "Halten"/"Schrumpfung", die Deckungsgrade je Gut und den
Hinweis, dass mehr Nahrungsproduktion (oder Zukauf) nötig ist.

**Warum das Plateau erst bei mehreren tausend Einwohnern kommt** (bewusst so
belassen): die Produktionsgeschwindigkeit hängt über `workforceFactor` selbst
an der Bevölkerung (`clamp(Bev/400, 0,35, 5)`). Bis 2.000 Einwohner wachsen
Angebot UND Nachfrage proportional – die Deckung bleibt 1,0, die Kolonie
trägt sich selbst. Erst wenn der Workforce-Faktor bei 5 deckelt, wächst nur
noch die Nachfrage, und die Nahrung wird bindend. Das ist eine Eigenschaft
der bestehenden Balance (Dokument 12) und wurde nicht angetastet.

## D. Folgeanpassungen

- **UI-Label** des Planetenwerts `infrastructurePct` (Wohnraum/Bevölkerung)
  heißt jetzt "Wohnraum" (Kolonienliste, Kolonie-Detail); Feldname im
  Protokoll unverändert.
- **Bebauungs-Tab**: Bebauungsplätze `belegt/gesamt · frei` im Panel-Kopf;
  je Gebäude Credits, Stunden, "1 Platz", jede Baustoffzeile mit Bedarf und
  Lagerbestand (grün/rot) und der Ablehnungsgrund als Text – alles vom
  Backend (`ColonySpeedBreakdown.buildingUpgrades[].materials/affordable/
  blockedReason`), sichtbar VOR dem Klick (Transparenzregel). Die Himmels-
  körper-Karte zeigt Plätze und planetweite Infrastruktur-Grenze.
- **Neue Query** `buildSlots(colonyId)` → `{ total, used, free,
  infrastructureLevel, planetInfrastructureTotal, planetInfrastructureMax }`.
- **Startaufträge ohne Industriekomplex**: `tryStartNextProductionEntry` lässt
  wartende Aufträge als `queued` stehen (kein Fehler, kein `stopped`), und ein
  fertiger Ausbau weckt sie (`GameTick.processBuildingCompletions`). Mit dem
  endgültigen Start (Industrie 1 vorhanden) laufen sie sofort – geprüft im
  E2E-Test; die Wartelogik bleibt für Kolonien ohne Industriekomplex
  (z. B. nach Rückbau) korrekt.
- **npc-bot**: `BUILD_PRIORITY` = Industrie/Werft/Wohnkomplex, Infrastruktur
  wird gebaut, sobald ein Ausbau mangels Platz abgelehnt wird; "Fehlende
  Baustoffe: …" wird geparst und je Produkt genau die Fehlmenge mit
  `autoProduceMissing` eingereiht; Spezialisierung nur noch als 3er-Charge
  ohne Dauerauftrag (ein ×20-Dauerauftrag blockierte bei Industrie 1 die
  Warteschlange für Stunden); Elerium-Nachschub bei Lager < 10.
- **Dokument 12**: mit Hinweis auf dieses Dokument versehen. Neuer Zeitrahmen,
  im E2E-Lauf **gemessen** statt geschätzt: die Baustoffe für den ersten Zug
  (Infrastruktur 3: Stahl 9 + Leitermetall 5) brauchten bei Industriekomplex 1
  rund **25 Realminuten ≈ 600 Spielstunden ≈ 25 Spieltage**. Da jede weitere
  Stufe mehr Baustoffe verlangt, die Produktion aber mit der Bevölkerung
  schneller wird, kam ein Bot in ≈ 40 Realminuten auf Infrastruktur 7 /
  Industrie 6 – eine Werft (Stahl 2 + Leichtmetalllegierung 1) ist ab dann
  eine Sache von Minuten. Ein "erster Frachter in einer Spielwoche" ist
  dennoch bewusst aufgegeben: bis zur Werft vergehen mehrere Spielwochen, und
  die Frachterkette selbst kostet ≈ 750 Spielstunden. Realistisch liegt der
  erste Frachter aus dem Minimalstart bei **mehreren Spielmonaten**.

## Verifikation

- `mvn clean test` (Backend, `WorldSeedSmokeTest` auf Minimalstart
  umgestellt), `mvn clean compile`/`package` (npc-bot), `tsc --noEmit` und
  `ng build` (Frontend): grün.
- E2E-Test `full-playthrough.mjs` erweitert: Minimalstart (3 Gebäude, Plätze
  2/2/0), Startaufträge laufen sofort, Werft-Auftrag ohne Werft und Werft-Bau
  ohne Platz abgelehnt, Infrastruktur 3 ohne Baustoffe mit Auflistung
  abgelehnt (Vorschau 335 Cr, 2,4 h, Stahl 9, Leitermetall 5), Baustoffe mit
  echten Kettenzeiten produziert, Ausbau zieht Baustoffe ab, Plätze 3/2/1.
  Der frühere Werft-Auftrag im Test entfällt (keine Werft im Start).
- Ergebnisse der langen Läufe (E2E, 4-Bot-Lauf): siehe Abschnitt "Läufe".

## Läufe

**E2E-Test `full-playthrough.mjs` – grün.** Der Bebauungsteil im Wortlaut des Laufs:

```
Minimalstart bestätigt: b_habitat 1, b_infrastructure 2, b_industry 1
  · Plätze 2/2 (0 frei), Infrastruktur planetweit 2/36
Startaufträge laufen sofort an: 1 running, 2 queued.
Werft-Auftrag ohne Werft und Werft-Bau ohne freien Bebauungsplatz korrekt abgelehnt.
Infrastruktur 2→3 ohne Baustoffe korrekt abgelehnt; Vorschau: 335 Cr, 2,4 h,
  Baustoffe p_stahl 9 (Lager 0), p_leitermetall 5 (Lager 0).
Baustoffe produziert: p_stahl 9, p_leitermetall 5.
Infrastruktur 3 fertig, Baustoffe abgezogen, Plätze 2/3 (1 frei).
ALLE PRÜFUNGEN BESTANDEN.
```

Die Baustoffproduktion für Infrastruktur 3 dauerte bei Industriekomplex 1
(Workforce-Faktor 0,35) rund **25 Realminuten** – das ist der reale Zeitrahmen
des ersten Spielerzugs und die Grundlage der Zeitabschätzung in Abschnitt D.

**4-Bot-Lauf aus dem Minimalstart – bestanden.** Alle vier Bots kamen ohne
Eingriff hoch, jeder über den vorgesehenen Weg (Ausbau abgelehnt → "Kein
Bebauungsplatz" → Infrastruktur ausbauen → fehlende Baustoffe selbst
produzieren):

| Bot | Infrastruktur-Ausbauten | Baustoff-Aufträge | Stand nach ≈ 40 min |
|---|---|---|---|
| NPC-Nord-01 | 5 | 35 | Infrastruktur 7, Industrie 6 |
| NPC-Nord-02 | 5 | 29 | Industrie 5 |
| NPC-Sued-01 | 5 | 35 | Infrastruktur 7, Industrie 6 |
| NPC-Sued-02 | 5 | 28 | Industrie 5 |

Beispielzustand NPC-Nord-01: Bevölkerung 2.370, Gebäude Wohnkomplex 1 /
Infrastruktur 7 / Industrie 6, Lager 86 Stabilisiertes Elerium (der
Nachschub-Mechanismus trägt), Warteschlange gefüllt mit eigener
Baustoffproduktion bis hinauf zur `p_energienetzbaugruppe` (dem Baustoff für
Infrastruktur ab Stufe 8). Die Kriegs-/Angriffslogik lief unverändert weiter.

Angepasst wurden dafür drei Bot-Verhalten (siehe Abschnitt D): Auflösen der
beiden strukturellen Ablehnungen, exakte statt 1,5-facher Fehlmenge, und
Spezialisierung als 3er-Charge statt ×20-Dauerauftrag – letzterer hätte bei
Industriekomplex 1 die einzige sequentielle Warteschlange stundenlang
blockiert und die Kolonie über den Elerium-Mangel in den Blackout getrieben.
