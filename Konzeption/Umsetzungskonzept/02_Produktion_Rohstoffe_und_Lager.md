# Umsetzung: Produktion, Rohstoffe und Lager

*Grundlage: `Konzeption/01_Produktion_und_Arbeitsteilung.md`,
`Konzeption/02_Ressourcen_und_Geografie.md`,
`Mechanik/01_Produktionskette_und_Spezialisierung.md`,
`Mechanik/02_Ressourcenprofile_und_Cluster.md`.*

## 1. Datenmodell

```text
ResourceType
  id, name, category

PlanetResourceConcentration
  planetId, resourceTypeId, concentration (0..∞, unerschöpflich)

ProductType
  id, name, category (Ship|GroundUnit|ConsumerGood|BuildingMaterial|
    RawResource|Fuel),
  tier (0 = Rohstoff, steigt Richtung Endprodukt),
  recipe: [{ inputProductTypeId, quantity }]   // leer bei Rohstoffen
  resourceProfile: [{ resourceTypeId, weight }]
  baseProductionTime, baseWorkforceRequired
    (→ Produktionsaufwand = baseProductionTime × baseWorkforceRequired,
    siehe Mechanik/04_... §2 – dieser Wert wird auch militärisch
    verwendet, sofern category=Ship)
  massKg, volumeM3
    (Masse/Volumen je Einheit – Platzhalterwerte für die künftige
    Frachtkapazität, siehe 03_Flotten_Schiffe_und_Werften.md §1; bislang
    nicht in die Frachtlogik eingebunden)

Specialization
  colonyId, productTypeId, currentLevel, currentThroughput,
  thresholdForCurrentLevel

ProductionQueue
  id, colonyId, productTypeId, quantity, startedAt, completesAt

Warehouse (Kolonielager)
  colonyId, productTypeId, quantity
```

## 2. API-Endpunkte

```text
GET  /api/v1/product-types
GET  /api/v1/product-types/{id}          // inkl. recipe, resourceProfile
GET  /api/v1/colonies/{colonyId}/warehouse
GET  /api/v1/colonies/{colonyId}/specializations
POST /api/v1/colonies/{colonyId}/production-queue
  body: { productTypeId, quantity }
  → prüft verfügbare Inputs im Warehouse (bzw. plant implizite
    Eigenproduktion der Unterkette, siehe Konzeption/01_... §1),
    reserviert Arbeitskapazität (Population, siehe 08_...),
    aktualisiert Specialization.currentThroughput
GET  /api/v1/colonies/{colonyId}/production-queue
DELETE /api/v1/colonies/{colonyId}/production-queue/{id}
```

## 3. Hintergrundjobs

```text
ProductionCompletionJob (je fälligem ProductionQueue-Eintrag):
  → Output ins Warehouse buchen, Inputs bereits bei Queue-Start
    abgebucht

SpecializationDecayJob (je Zeitintervall je Colony):
  → prüft currentThroughput je ProductType gegen thresholdForCurrentLevel
  → Überschuss erhöht Level, Unterschreitung senkt Level
    (konkrete Kurve: siehe offene Fragen in Mechanik/01_...; bis zur
    Festlegung: Platzhalter-Linearfunktion, konfigurierbar)
```

## 4. Menüführung

```text
Kolonie-Detailansicht → Tab "Produktion"
├─ Produktionswarteschlange (laufende + geplante Aufträge, Fortschrittsbalken)
├─ Neuer Auftrag: Produktsuche/-baum (visualisiert die Hierarchie
│   Endprodukt → Module → Submodule → Rohstoffe aus
│   Konzeption/01_... §1), pro Ebene sichtbar ob Zukauf oder
│   Eigenproduktion günstiger wäre (abgeleitet aus Marktpreisen, siehe
│   07_...)
├─ Spezialisierungsübersicht: aktuelle Stufe je Produkt, Fortschritt
│   zur nächsten Stufe, Warnindikator bei drohendem Stufenverlust
└─ Lagerbestand (Warehouse) je Produkt, Menge
```
