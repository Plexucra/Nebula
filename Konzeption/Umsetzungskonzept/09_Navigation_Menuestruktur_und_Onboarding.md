# Umsetzung: Navigation, Menüstruktur und Onboarding

*Grundlage: `Konzeption/08_Neue_Spieler_und_Einstieg.md`,
`Konzeption/00_Vision_und_Leitprinzipien.md` §4 (Meta-Progression).
Dieses Dokument fasst die in den vorherigen Umsetzungsdateien verteilte
Menüführung zu einer Gesamtnavigation zusammen und beschreibt, wie sich
das UI mit dem Spielerfortschritt freischaltet.*

## 1. Globale Navigationsstruktur

```text
Hauptnavigation (immer sichtbar)
├─ Planeten          → 01_Planeten_Kolonien_und_Bebauung.md
├─ Produktion        → 02_Produktion_Rohstoffe_und_Lager.md
├─ Flotten           → 03_Flotten_Schiffe_und_Werften.md
├─ Bodentruppen      → 04_Bodentruppen_und_Landung.md
├─ Diplomatie/Krieg  → 05_Kampf_Blockaden_und_Kriegszustand.md
├─ Galaxiekarte      → 06_Gateways_und_interstellare_Reisen.md
├─ Handel            → 07_Handelsgilde_Markt_und_Depots.md
├─ Konto (Wallet)    → 08_Geldsystem_Bevoelkerung_und_Planetenwerte.md
└─ Statistiken       → kein Spielinhalt, siehe §5 unten
```

## 2. Freischaltungslogik (kein Levelsystem, sondern Zustandsprüfung)

Gemäß `Konzeption/08_...` §5 sollen neue Mechaniken nicht per
Tutorial-Menü, sondern durch tatsächlichen Fortschritt sichtbar werden.
Technisch bedeutet das: Navigationspunkte werden nicht global versteckt,
sondern **kontextabhängig reduziert**, solange Voraussetzungen fehlen.

```text
FeatureVisibility(player):

  "Galaxiekarte" (voller Umfang inkl. fremder Systeme):
      sichtbar ab Gateway.activatedAt != null
      davor: nur Heimatsystem-Ansicht (Kolonisation/Erforschung)

  "Diplomatie/Krieg":
      sichtbar ab Gateway.activatedAt != null
      (vor Aktivierung gibt es keine erreichbaren fremden Parteien)

  "Handel" → Stationsmarkt/sektorale Ebene:
      sichtbar ab Gateway.activatedAt != null
      Depot-Handel INNERHALB des eigenen Systems bereits vorher nutzbar

  "Bodentruppen" → Rekrutierung:
      sichtbar sobald erste Colony existiert (kein Gateway nötig,
      Verteidigung ist auch isoliert relevant)

  "Flotten" → Trägerschiff-Bau:
      sichtbar sobald ShipType.class=Carrier erforscht/freigeschaltet
      (Forschungssystem selbst: siehe offene Meta-Frage in
      Konzeption/00_... §8 – hier nur die UI-Konsequenz beschrieben)
```

Diese Sichtbarkeitsregel ist rein UI-seitig (progressive disclosure) –
die zugrunde liegenden API-Endpunkte bleiben unverändert erreichbar,
sofern die serverseitige Vorbedingung (z. B. Gateway aktiviert) erfüllt
ist. Das vermeidet eine doppelte Business-Logik zwischen Client und
Server.

## 3. Onboarding-Flow (Heimatsystem-Phase)

```text
1. Account erstellen → Player + Homeworld-Colony automatisch angelegt
   (Population initial, PlanetStats initial: siehe
   08_Geldsystem_Bevoelkerung_und_Planetenwerte.md, Loyalität startet
   mit Heimatplanet-Sonderbonus, Konzeption/07_... §5)
2. Geführte erste Schritte (kontextuelle Hinweise, KEIN separates
   Tutorial-Menü):
   a. Erstes Gebäude bauen (01_...)
   b. Erste Produktion starten (02_...)
   c. Zweiten Planeten im Heimatsystem kolonisieren (01_...)
3. Das Gateway ist von Beginn an als "entdeckt" bekannt (kein
   künstlicher Fortschritts-/Wartewert) – die Aktivierung selbst bleibt
   eine bewusste, jederzeit mögliche Spielerentscheidung (Konzeption/08_...,
   §7: "Isolation bietet Sicherheit. Öffnung bietet Wohlstand.").
   *Korrektur gegenüber einer früheren Zwischenversion dieses Dokuments:*
   ein Prototyp-Platzhalter hatte die Entdeckung testweise an einen
   kumulierten Gebäudestufen-/Bevölkerungswert gekoppelt – das erzeugte
   eine unnötige Wartephase ohne Spielinhalt und wurde verworfen. Ein
   echtes Forschungssystem, das die Entdeckung ursächlich auslöst (statt
   sie einfach als Startzustand zu setzen), bleibt weiterhin offen (siehe
   Konzeption/04_... §6).
4. Aktivierungs-Aktion in 06_... ist damit von Anfang an verfügbar,
   erfordert aber weiterhin eine Anlaufzeit nach dem Auslösen.
5. Nach Aktivierung: Galaxiekarte, Diplomatie und interstellarer
   Handel erscheinen in der Hauptnavigation (siehe §2)
```

**Womit ist der Spieler vor der Aktivierung beschäftigt?** Nicht mit
Warten, sondern mit dem lokalen Produktions- und Handelskreislauf: Die
Heimatkolonie kann von der ersten Minute an Rohstoffe (z. B. Erz) oder
Konsumgüter (z. B. Nährstoffpaste) produzieren und über den lokalen
Systemmarkt verkaufen (02_... und 07_...) – dafür ist weder Gateway
noch eine zweite Kolonie nötig. Das ist absichtlich der erste konkrete
Gameplay-Loop, nicht ein Wartebildschirm.

## 4. Benachrichtigungs-/Push-Kanäle (Übersicht)

Diese Ereignisse sind für den Client zeitkritisch genug, um über einen
Push-Kanal statt reinem Polling ausgeliefert zu werden (Ergänzung zu
den einzelnen Endpunkt-Dateien):

```text
topic: player.{id}.notifications
  - Gateway entdeckt / aktivierbar
  - Werft-/Produktions-/Rekrutierungsauftrag fertig
  - Flotte am Ziel angekommen
  - Friedensangebot erhalten
  - Kriegserklärung erhalten

topic: battle.{id}.updates
  - neuer Kampftick berechnet (für alle Battle-Teilnehmer)

topic: colony.{id}.alerts
  - Blockade beginnt/endet
  - Loyalität/Sicherheit unter kritischen Schwellenwert
  - Überbebauungs-Warnschwelle erreicht
```

## 5. Statistiken (Entwickler-/Balancing-Werkzeug, kein Spielinhalt)

Der Frontend-Prototyp enthält zusätzlich eine Menüseite "Statistiken"
sowie 10 automatisch erzeugte NPC-Kolonien (`core/models/npc.model.ts`,
`SimulatedGameApiService.runNpcAiTick`). Das ist **kein** Vorschlag für
ein spielerseitiges KI-Gegner-System, sondern ein reines
Beobachtungs-/Stresstest-Werkzeug für die Konzeption selbst:

- Die NPCs verhalten sich wirtschaftlich wie Spieler (Bebauung,
  Produktion, lokaler Verkauf), bauen aber bewusst nie Kampfschiffe,
  Werft-Rüstung oder Bodentruppen – sie erzeugen dauerhafte
  Hintergrundlast auf Produktions-, Konsum- und Marktmechanik, ohne
  Kampfsystem-Abhängigkeiten vorwegzunehmen (das noch nicht
  implementiert ist).
- Die Statistikseite zeichnet alle 10 Sekunden einen Messpunkt über
  Gesamtbevölkerung, Geldmenge, die vier Planetenwerte im Schnitt sowie
  Markt-/Kolonie-Gesundheit auf (`UniverseStatSnapshot`) und zeigt sie
  als Zeitreihen – Grundlage, um Reglerentscheidungen wie
  Konsumbedarf/-tempo empirisch statt nur theoretisch zu treffen.
- **Konkreter Fund aus einem ersten Testlauf:** Der ursprünglich
  angenommene Bedarfswert je Einwohner (`CONSUMER_NEED_PER_CAPITA` in
  `Mechanik/09_...`-Terminologie) war so hoch angesetzt, dass praktisch
  jede Kolonie – Spieler wie NPCs – dauerhaft bei nahe 0 % Lebensstandard
  hing, obwohl die Bevölkerungs-Wallets Tausende ungenutzte Credits
  anhäuften. Ursache war eine Kombination aus zu hohem Bedarfswert und
  einem KI-Fehler (Nahrung wurde als "Reserve" zurückgehalten, aber
  Bevölkerung kann ausschließlich über Marktorders kaufen, nie direkt
  aus dem Kolonielager – eine ungelistete Reserve ist für die eigene
  Bevölkerung unerreichbar). Nach Korrektur beider Ursachen stieg der
  durchschnittliche Lebensstandard über mehrere Minuten Beobachtung
  von ~0,3 % auf über 40 % bei sinkender Zahl gefährdeter Kolonien.
  Diese Regel (Bevölkerung konsumiert ausschließlich über den Markt,
  nie direkt aus dem eigenen Lager) ist damit ein zentraler, für
  weitere Spieldesign-Entscheidungen relevanter Fakt.
- **Platzierung als Nachbarschafts-Cluster:** Die 10 NPC-Systeme werden
  nicht mehr zufällig über die ganze Galaxie verstreut, sondern per
  Breitensuche (BFS) ab einem zufälligen Startsystem im Gateway-Graphen
  ausgewählt (`selectNpcClusterIndices`) – dadurch ist jeder NPC mit
  mindestens einem anderen NPC direkt über sein Gateway verbunden
  (stichprobenartig geprüft: 0 isolierte NPCs, 3-5 NPC-Nachbarn je
  Knoten). Erst dadurch kann überhaupt eine lokale, sich gegenseitig
  versorgende Wirtschaft entstehen.
- **Abgestimmte Spezialisierung unter Nachbarn:** Innerhalb des Clusters
  wählt jeder NPC (in BFS-Reihenfolge) die Rohstoffkonzentration seines
  Planeten mit der höchsten Priorität, die noch **kein** bereits
  zugewiesener direkter Gateway-Nachbar gewählt hat – erst wenn alle
  Optionen unter Nachbarn belegt sind, fällt die Wahl auf die insgesamt
  stärkste eigene Konzentration. Stichprobenartig geprüft: 0
  Spezialisierungs-Überschneidungen zwischen direkten Nachbarn.
- **Frachter ab Spielbeginn:** Sowohl der Spieler als auch jeder NPC
  startet mit einer "Handelsflotte" aus einem Frachter (`p_freighter`)
  in der eigenen Kolonie – Grundvoraussetzung für Warentransport über
  die eigene Kolonie hinaus (Konzeption/05_..., §9). Tatsächliche
  Frachterbewegung/-beladung zwischen Systemen ist weiterhin nicht
  implementiert (siehe `Bewegen`/`Blockade`-Buttons in der
  Flottenübersicht) – der Frachter ist aktuell reiner Bestand, keine
  aktive Logistik.
- Sollte ein echtes NPC-/KI-Spielersystem später als Spielinhalt
  gewünscht werden (z. B. um dünn besiedelte Regionen zu füllen), wäre
  das eine eigene Konzeptionsentscheidung – dieser Prototyp-Baustein
  wäre dafür allenfalls ein technischer Ausgangspunkt, kein Vorgriff auf
  das Design.

## Offene Umsetzungsfragen

- Wie wird das in `Konzeption/00_...` §8 als offen markierte
  Forschungssystem technisch modelliert (Techbaum vs. einfache
  Forschungspunkte)? Das entscheidet, wie `Gateway.discoveredBy`
  konkret ausgelöst wird.
- Wie werden informelle Spielerorganisationen (Konzeption/00_... §6,
  `04_...` §9) technisch unterstützt, ohne ein formales Allianzsystem
  einzuführen (z. B. rein clientseitige/externe Tools vs. optionale
  Gruppierungs-Metadaten wie gemeinsame Namensräume für
  TariffAgreements)?
