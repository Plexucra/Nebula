# Architektur- und Datenmodell-Übersicht

*Dieser Ordner bricht die Konzeption (`../Konzeption/`) und Mechanik
(`../Mechanik/`) auf konkrete Software-Artefakte herunter: Entitäten,
API-Endpunkte, Hintergrundprozesse und Menüführung. Er beschreibt eine
mögliche technische Umsetzung, keine aus den Ursprungsdokumenten
übernommene Vorgabe – die Ursprungsdokumente enthalten selbst keine
Angaben zu API oder UI.*

## 1. Grundannahmen der Architektur

Aus der Mechanik ergeben sich folgende technische Grundanforderungen:

- **Eventbasiert, nicht tickbasiert als Grundarchitektur.** Der Server
  reagiert grundsätzlich auf Ereignisse (Order ausgeführt, Bewegung
  angekommen, Landung versucht) statt die gesamte Simulation in einem
  globalen Welt-Tick durchzurechnen. Ein genereller "Welt-Tick" existiert
  nicht.
- **Geplante Jobs nur für die explizit dokumentierten periodischen
  Mechaniken.** Nur dort, wo die Mechanik selbst eine feste Wiederkehr
  vorschreibt, plant der Server einen zeitgesteuerten Folge-Job statt
  eines reinen Ereignisses: Kampfticks (`Mechanik/06_...` – 8 h
  Arbeitswert, ein Job pro laufendem `Battle`), planetare
  Verteidigungs-Anlaufzeiten (12 h, ein einmaliger Job pro
  Aktivierungsvorgang), Kriegs-/Friedens-Sperrfristen (24 h, ein
  einmaliger Job pro Friedensschluss) und Konsum-Glättung über
  Zeitintervalle (`Mechanik/09_...`, ein wiederkehrender Job pro
  Colony). Jeder dieser Jobs ist an eine konkrete Entität gebunden,
  nicht an einen globalen Takt.
- **Deterministische Kernberechnungen.** Kampfschaden, Restschaden,
  Verlustzuweisung und Konsumverteilung sind laut Mechanik explizit
  **deterministisch** (kein Zufall außer der dokumentierten Ausnahme in
  der Landungsabwehr, `Mechanik/05_...` §8). Das bedeutet: Diese
  Berechnungen müssen reproduzierbar und ohne versteckte
  Nebenläufigkeits-Effekte laufen (z. B. ein einzelner Tick-Job pro
  Gefecht, keine parallele Verrechnung derselben Gefechtsseite).
- **Gruppenbasierte statt objektbasierte Militär-Datenhaltung.**
  Einheiten werden nach Typ aggregiert gespeichert (`Menge` pro
  `EinheitenTyp` und `Flotte`/`Verband`), nicht als Einzelobjekte
  (`Mechanik/04_...` §1).
- **Physische Warenhaltung statt globalem Inventar.** Jede Wareneinheit
  gehört zu genau einem Lagerort (Kolonielager, Handelsdepot,
  Handelsstation, Frachterladeraum) – kein globales Spieler-Inventar
  (`Mechanik/09_...` §1).

## 2. Zuordnung Konzeption → Entität → Endpunkt-Datei

| Themenfeld                     | Konzeption                               | Mechanik                             | Umsetzungskonzept                                 |
| ------------------------------ | ---------------------------------------- | ------------------------------------ | ------------------------------------------------- |
| Planeten/Kolonien/Bebauung     | `Konzeption/07_...`, `Konzeption/01_...` | `Mechanik/07_...`, `Mechanik/11_...` | `01_Planeten_Kolonien_und_Bebauung.md`            |
| Produktion/Rohstoffe           | `Konzeption/01_...`, `Konzeption/02_...` | `Mechanik/01_...`, `Mechanik/02_...` | `02_Produktion_Rohstoffe_und_Lager.md`            |
| Flotten/Schiffe                | `Konzeption/03_...`                      | `Mechanik/03_...`                    | `03_Flotten_Schiffe_und_Werften.md`               |
| Bodentruppen                   | `Konzeption/03_...`                      | `Mechanik/05_...`                    | `04_Bodentruppen_und_Landung.md`                  |
| Kampf/Blockaden/Krieg          | `Konzeption/03_...`                      | `Mechanik/04_...`, `Mechanik/06_...` | `05_Kampf_Blockaden_und_Kriegszustand.md`         |
| Gateways/Reisen                | `Konzeption/04_...`                      | `Mechanik/08_...`                    | `06_Gateways_und_interstellare_Reisen.md`         |
| Handel/Markt                   | `Konzeption/05_...`                      | `Mechanik/09_...`                    | `07_Handelsgilde_Markt_und_Depots.md`             |
| Geld/Bevölkerung/Planetenwerte | `Konzeption/06_...`, `Konzeption/07_...` | `Mechanik/10_...`, `Mechanik/11_...` | `08_Geldsystem_Bevoelkerung_und_Planetenwerte.md` |
| Neue Spieler/Navigation        | `Konzeption/08_...`                      | –                                    | `09_Navigation_Menuestruktur_und_Onboarding.md`   |

## 3. Kern-Entitäten (galaxieweit)

```text
Player            – Spielkonto, gehört zu genau einem Heimatplaneten
System            – Sonnensystem, enthält Planeten + genau 1 Gateway
Planet            – Himmelskörper in einem System, Bebauungskapazität
Colony            – Ansiedlung eines Spielers auf einem Planeten
                    (max. 1 Colony pro Player pro Planet)
Population        – gehört zu genau einer Colony
PlanetStats       – Infrastruktur/Sicherheit/Lebensstandard/Loyalität
                    je Colony
Gateway           – 1:1 mit System, verwaltet von Alien-KI
Fleet             – Menge von Schiffsgruppen, Ort + Bewegungsbefehl
GroundForceGroup  – Menge von Bodentruppengruppen, Ort
Battle            – laufendes Gefecht an einer Blockadestelle
Blockade          – kontrolliert eine Verbindung zwischen zwei Orten
TradePost         – Handelsposten (lokal oder sektoral)
Depot             – planetares Warenlager der Handelsgilde
Order             – Kauf-/Verkaufsorder an einem TradePost
Wallet            – Credits-Bestand (Player oder Population)
WarState          – Kriegs-/Friedenszustand zwischen zwei Parteien
TariffAgreement   – bilaterale Zollvereinbarung an einem Gateway
```

Detaillierte Felder je Entität stehen in den jeweiligen Themendateien.

## 4. Hintergrundprozesse (Scheduler-Jobs)

```text
CombatTickJob          – alle 8h je aktivem Battle
LandingDefenseJob       – einmalig je Landungsversuch (Event, kein Tick)
ConsumptionTickJob      – je Zeitintervall je Colony (Konsumbudget N)
PlanetStatsRecalcJob    – je Zeitintervall je Colony
FleetUpkeepJob          – je Zeitintervall je Fleet
DefenseActivationJob    – einmalig je Aktivierungsvorgang (12h Timer)
WarCooldownJob          – einmalig je Friedensschluss (24h Sperrfrist)
PopulationGrowthJob     – je Zeitintervall je Colony
MoneySupplyJob          – bei Bevölkerungswachstum über Höchststand
```

## 5. API-Konventionen für die folgenden Dateien

- REST-artige Pfade unter `/api/v1/...`, JSON-Bodies.
- Schreibende Endpunkte, die eine spielinterne Wartezeit auslösen
  (Bewegungsbefehl, Aktivierung, Rekrutierung), geben ein
  `Order`/`Job`-Objekt mit `status` und `completesAt` zurück, statt
  synchron das Endergebnis zu liefern.
- Alle Listen-Endpunkte unterstützen Pagination (`?page=`, `?size=`).
- Es wird nicht zwischen REST und WebSocket/Push unterschieden – wo ein
  Ereignis für den Client in Echtzeit relevant ist (Gefechtsbeginn,
  Blockadeänderung, eingehende Nachricht), wird zusätzlich ein
  Push-Kanal (`topic`) angegeben.
