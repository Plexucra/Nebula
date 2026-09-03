import { BuildingType } from '../../models';

/**
 * Gebäudekatalog. ProductionFacility-Typen sind bewusst grob nach
 * Warenkategorie geschnitten (Rohstoff/Fertigung, Werft, Ausbildung) statt
 * pro Einzelprodukt – siehe Umsetzungskonzept/02_..., §1 (Produktionsanlagen
 * begrenzen, was technisch produziert werden kann).
 */
export const BUILDING_CATALOG: BuildingType[] = [
  {
    id: 'b_habitat',
    name: 'Wohnkomplex',
    category: 'Infrastructure',
    description: 'Lebensraum für die Kolonialbevölkerung. Trägt die Bevölkerung, gibt selbst keinen Produktionsbonus.',
    maxLevel: 20,
    buildPointsPerLevel: 3,
    baseCostPerLevel: 120,
    baseHoursPerLevel: 1,
    upkeepPerLevel: 1.5,
    // Bewusst hoch angesetzt (nicht 60): Bei niedrigem Wert stößt die
    // Bevölkerung schon im vierstelligen Bereich an die Kapazitätsdecke,
    // weit unter den in der Konzeption beispielhaft genannten
    // Millionen-/Milliarden-Größenordnungen (Mechanik/11_..., §7).
    // Restliche Formeln (Workforce/Sicherheit/Konsum sind bereits
    // populationsproportional) bleiben unverändert – siehe
    // Mechanik/10_..., "Offene Zahlenfragen" zur weiterhin ungelösten
    // Geldschöpfungs-Skalierungsfrage.
    populationCapacityPerLevel: 6000,
  },
  {
    id: 'b_powergrid',
    name: 'Energienetz',
    category: 'Infrastructure',
    description: 'Energieversorgung für Kolonie und Industrie. Erhöht die planetare Infrastrukturkapazität – benötigt dafür laufend Elerium-Zellen aus dem Kolonielager, sonst Blackout-Abzug.',
    maxLevel: 20,
    buildPointsPerLevel: 2,
    baseCostPerLevel: 90,
    baseHoursPerLevel: 0.8,
    upkeepPerLevel: 1.5,
    populationCapacityPerLevel: 2500,
  },
  {
    id: 'b_industry',
    name: 'Industriekomplex',
    category: 'ProductionFacility',
    description: 'Extraktion und Fertigung von Rohstoffen, Bauteilen und Konsumgütern.',
    maxLevel: 20,
    buildPointsPerLevel: 4,
    baseCostPerLevel: 160,
    baseHoursPerLevel: 1.2,
    upkeepPerLevel: 3,
    productionSlotsPerLevel: 1,
  },
  {
    id: 'b_shipyard',
    name: 'Werft',
    category: 'ProductionFacility',
    description: 'Voraussetzung für den Bau von Schiffen jeder Klasse.',
    maxLevel: 15,
    buildPointsPerLevel: 6,
    baseCostPerLevel: 260,
    baseHoursPerLevel: 2,
    upkeepPerLevel: 5,
    productionSlotsPerLevel: 1,
  },
  {
    id: 'b_academy',
    name: 'Ausbildungszentrum',
    category: 'ProductionFacility',
    description: 'Voraussetzung für die Rekrutierung und Ausbildung von Bodentruppen.',
    maxLevel: 15,
    buildPointsPerLevel: 3,
    baseCostPerLevel: 140,
    baseHoursPerLevel: 1,
    upkeepPerLevel: 2,
    productionSlotsPerLevel: 1,
  },
  {
    id: 'b_defense',
    name: 'Planetare Abwehr',
    category: 'PlanetaryDefense',
    description: 'Autonome Abwehrsysteme gegen Landungsversuche. Muss nach Ausbau aktiviert werden (Anlaufzeit).',
    maxLevel: 10,
    buildPointsPerLevel: 6,
    baseCostPerLevel: 300,
    baseHoursPerLevel: 2,
    upkeepPerLevel: 6,
  },
];

export function findBuildingType(id: string): BuildingType {
  const found = BUILDING_CATALOG.find(b => b.id === id);
  if (!found) throw new Error(`Unbekannter BuildingType: ${id}`);
  return found;
}
