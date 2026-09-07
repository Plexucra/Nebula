# 12. Balancing-Ziel: Erster Frachter in Arbeitsteilung durch 10 Kommandanten

> **Hinweis (Stand Dokument 17):** Die hier hergeleiteten Start-Gebäudestufen
> (Industriekomplex 4, Werft 3) gelten seit dem Minimalstart aus
> `17_Infrastruktur_Bebauungsplaetze_Baustoffe_und_Lebensqualitaet.md` nicht
> mehr – eine Heimatkolonie startet nur noch mit Wohnkomplex 1,
> Industriekomplex 1 und Infrastruktur 2, jeder Ausbau kostet Baustoffe. Das Ziel "erster
> Frachter in einer Spielwoche" ist damit bewusst aufgegeben; den neuen,
> realistischen Zeitrahmen schätzt Dokument 17, Abschnitt D ab. Die Analyse
> unten bleibt als Herleitung der Kettenzeiten gültig.

## 1. Ziel

Es soll möglich sein, dass sich 10 Kommandanten zusammenschließen und – jeder
auf sein eigenes Teilstück spezialisiert, verbunden über den gemeinsamen
Markt (`createSellOrder`/`buyFromOrder`) – innerhalb einer Spielwoche (168
Spielstunden) ihren ersten Frachter fertigstellen. Dieses Dokument hält die
Analyse fest, die zu einer konkreten Anpassung der Start-Gebäudestufen
geführt hat, sowie die konkrete Gegenrechnung, mit der das Ziel überprüft
wurde.

## 2. Ausgangsbefund

Die komplette Rezeptkette für `p_freighter` ist neun Ebenen tief (Tier 6 bis
Tier 0) und verzweigt in sechs Tier-5-Baugruppen (Rumpf-, Antriebs-,
Energie-, Elektronik-, Ladungs- und Versorgungsmodul), die ihrerseits jeweils
mehrere Tier-4/3/2/1/0-Vorprodukte benötigen. Mit den ursprünglichen
Start-Gebäudestufen (Industriekomplex 2, Werft 1) und frischer
Kolonie-Bevölkerung (420, siehe `world-seed.ts`) hätte selbst eine EINZELNE
Kolonie ALLEIN (leeres Lager, keine Arbeitsteilung) laut
`SimulatedGameApiService.previewProductionChain` weit über 1000
Produktionsstunden benötigt – deutlich zu viel für das Ziel, selbst verteilt
auf mehrere Kolonien.

Zwei Effekte wurden bei der Analyse als entscheidend identifiziert:

1. **Bevölkerungswachstum ist der stärkste Hebel.** `workforceFactor`
   (`engine/formulas.ts`) skaliert die Produktionsgeschwindigkeit mit
   `clamp(Bevölkerung / 400, 0.35, 5)` – der Maximalwert 5 wird bei rund 2000
   Einwohnern erreicht. Mit der Standard-Wachstumsformel
   (`populationGrowthDelta`) erreicht eine frische Heimatkolonie (Kapazität
   16.000, Startbevölkerung 420, ungestörte Lebensbedingungen) diese Marke
   bereits nach **rund 2-3 Spielstunden** – praktisch sofort im Verhältnis zu
   einer ganzen Spielwoche. Wichtig dabei: Der Geschwindigkeitsfaktor eines
   Auftrags wird EINMALIG beim Einreihen berechnet (`computeProductionHours`,
   aufgerufen aus `planChain`) – wer seinen Auftrag erst nach diesen
   wenigen Stunden einreiht, produziert die GESAMTE Auftragsdauer über mit
   dem vollen Bevölkerungsbonus.
2. **Arbeitsteilung über den Markt funktioniert bereits vollständig** – live
   verifiziert (siehe §4): Eine Kolonie, die eine bereits fertige Baugruppe
   kauft (`buyFromOrder`, KEINE Entfernungs-/Logistikprüfung, die Ware
   erscheint sofort im Ziel-Lager), reduziert ihren eigenen
   `previewProductionChain`-Wert für den Frachter exakt um den Anteil, den
   diese Baugruppe samt ihrer kompletten Vorkette sonst gekostet hätte.

## 3. Balancing-Anpassung

Ausgehend von diesem Befund wurden die Start-Gebäudestufen der
Heimatkolonie in `world-seed.ts` (`buildHomeworldBundle`) angehoben:

| Gebäude | vorher | nachher |
|---|---|---|
| Industriekomplex (`b_industry`) | 2 | 4 |
| Werft (`b_shipyard`) | 1 | 3 |

Bewusst NICHT verändert: die lineare Formel `buildingLevelSpeedFactor`
selbst (Stufe 2 = doppelt so schnell wie Stufe 1 bleibt exakt erhalten,
siehe `Mechanik/01_...md`) und die Start-Gebäudestufen der NPC-Kolonien
(`spawnNpcs`, unverändert bei Industriekomplex 1) – diese Anpassung betrifft
ausschließlich neu registrierte Spieler-Kommandanten.

## 4. Gegenrechnung und Live-Verifikation

Mit den neuen Start-Werten UND Bevölkerung auf dem Maximalfaktor (5) ergibt
sich für die sechs Module (je eine Kolonie mit Industriekomplex 4, komplette
Vorkette, leeres Lager) und die finale Montage (Werft 3):

| Modul | Stunden (1 Kolonie, Industriekomplex 4) |
|---|---|
| Rumpfmodul | 43,2 |
| Antriebsmodul | 80,6 |
| Energiemodul | 48,2 |
| Elektronikmodul | 93,1 |
| Ladungsmodul (schwerstes) | 117,7 |
| Versorgungsmodul | 65,9 |
| **Finale Montage** (Werft 3) | **20,0** |

Die einfachste, naheliegendste Aufteilung – **je ein Kommandant pro
Baugruppe (6) + ein Kommandant, der alle sechs kauft und montiert (7
insgesamt)** – kommt danach auf rund 118 (schwerste Baugruppe) + 20
(Montage) ≈ **138 Spielstunden**, deutlich unter den 168 Stunden einer
Spielwoche. Die restlichen 3 der 10 Kommandanten stehen als Puffer zur
Verfügung (z. B. falls der eigene Heimatplanet einen benötigten Rohstoff
nicht führt – Fördergüte-Unterschiede zwischen Planetentypen erzwingen
ohnehin echten Handel zwischen unterschiedlich spezialisierten Kolonien,
siehe `Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md`).

**Live verifiziert** (nicht nur nachgerechnet) über die echte, laufende
Simulation, mit zwei frisch registrierten Testkommandanten:

1. Kommandant A reiht `queueProduction(colonyId, 'p_frachter_rumpf', 1,
   autoProduceMissing: true)` ein, die komplette Vorkette läuft automatisch
   durch (`processProductionQueue`), das fertige Modul landet im Lager.
2. Kommandant A bietet es an (`createSellOrder`).
3. Kommandant B kauft es (`buyFromOrder`) – erscheint sofort im eigenen
   Lager, ohne Flug/Lieferzeit.
4. `previewProductionChain(colonyId, 'p_freighter', 1)` bei Kommandant B
   sinkt dadurch NACHWEISLICH (gemessen: von 369,5 auf 326,0 Stunden, eine
   Ersparnis von 43,5 Stunden für genau dieses eine Modul) – der
   Arbeitsteilungs-Mechanismus funktioniert Ende-zu-Ende über die
   tatsächliche Engine, nicht nur auf dem Papier.

## 5. Praktischer Hinweis für Spieler (und künftige Doku/Tutorials)

Wer dieses Ziel tatsächlich verfolgt, sollte den eigenen
Grundnahrungs-Dauerauftrag NICHT einfach canceln, um die Warteschlange für
das gemeinsame Projekt freizuräumen – ohne Grundversorgung sinkt der
Lebensstandard, das bremst über `growthConditionFactor` das
Bevölkerungswachstum und damit den zentralen Geschwindigkeitshebel aus §2
spürbar ab (live beobachtet: Lebensstandard fiel nach dem Canceln
innerhalb weniger Ticks von 100 % auf 49 %). Der Auftrag lässt sich
stattdessen einfach hinter dem eigentlichen Bauauftrag einreihen und läuft
danach automatisch weiter.

## 6. Bekannte Grenzen dieser Analyse

- Die Gegenrechnung geht von einer sauberen 1-Modul-pro-Kolonie-Aufteilung
  aus; eine noch feinere Aufteilung (einzelne Vorprodukte statt ganzer
  Module) könnte die Zeit weiter drücken, wurde aber nicht Schritt für
  Schritt durchgerechnet – bei 138 von 168 verfügbaren Stunden besteht
  bereits ausreichend Spielraum, ohne das nötig zu machen.
- Handelslogistik ist in diesem Prototyp bewusst ohne Entfernungs- oder
  Sichtbarkeitsprüfung modelliert (`buyFromOrder` liefert unabhängig vom
  Standort sofort) – ein realistischeres Logistiksystem (Lieferzeit über
  Flotten) würde zusätzliche Pufferzeit in der Gegenrechnung erfordern.
