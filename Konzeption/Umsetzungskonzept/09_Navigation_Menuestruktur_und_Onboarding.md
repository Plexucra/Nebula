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
└─ Konto (Wallet)    → 08_Geldsystem_Bevoelkerung_und_Planetenwerte.md
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
3. Forschungsfortschritt akkumuliert im Hintergrund
   (Gateway-Entdeckung als Forschungsresultat, siehe offene Frage in
   Konzeption/04_... §6) → Gateway.discoveredBy gesetzt
4. Spieler erhält Benachrichtigung "Gateway entdeckt" →
   Aktivierungs-Aktion in 06_...  freigeschaltet
5. Nach Aktivierung: Galaxiekarte, Diplomatie und interstellarer
   Handel erscheinen in der Hauptnavigation (siehe §2)
```

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
