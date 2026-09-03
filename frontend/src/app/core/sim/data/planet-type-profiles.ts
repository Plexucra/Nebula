import { PlanetType } from '../../models';

/** Fördergüte-Bereich [min, max] (0-100), siehe Nebula_Planetentypen_..., §6. */
export type ConcentrationRange = [min: number, max: number];

/**
 * Fördergüte-Bereiche je Planetentyp und Rohstoff, transkribiert aus
 * Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md, §6.1-6.3.
 * Reihenfolge je Zeile identisch zur Rohstoffreihenfolge in
 * `resource-catalog.ts`: Ferrometall, Leichtmetall, Refraktärmetall,
 * Leitmetall, Edelmetall, Seltenerden, Technologiemetall, Silikat,
 * Kohlenstoff, Salz, Radionuklid, Wassereis, Atmosphärenfluid, Edelgas,
 * Kohlenwasserstoff, Isotopenträger, Eleriumspuren.
 */
const RESOURCE_ORDER = [
  'res_ferrometall', 'res_leichtmetall', 'res_refraktaer', 'res_leitmetall', 'res_edelmetall',
  'res_seltenerden', 'res_technometall', 'res_silikat', 'res_kohlenstoff', 'res_salz',
  'res_radionuklid', 'res_eis', 'res_atmosphaere', 'res_edelgas', 'res_kohlenwasserstoff',
  'res_isotopentraeger', 'res_elerium',
] as const;

const RAW_RANGES: Record<PlanetType, ConcentrationRange[]> = {
  TemperierterBiosphaerenplanet: [[25, 55], [35, 65], [10, 30], [20, 45], [5, 20], [10, 30], [15, 35], [45, 75], [45, 75], [60, 90], [5, 20], [80, 100], [80, 100], [20, 50], [30, 60], [20, 45], [15, 30]],
  Silikatplanet: [[35, 70], [30, 65], [20, 50], [20, 50], [5, 25], [10, 40], [15, 45], [70, 100], [15, 40], [20, 50], [10, 35], [5, 35], [10, 40], [10, 35], [5, 25], [10, 35], [5, 25]],
  Wuestenplanet: [[25, 60], [40, 75], [15, 45], [15, 40], [5, 20], [15, 45], [10, 35], [65, 95], [20, 50], [55, 90], [10, 30], [5, 30], [20, 55], [10, 40], [15, 50], [10, 35], [5, 20]],
  Ozeanplanet: [[10, 35], [15, 40], [5, 20], [10, 30], [5, 20], [5, 20], [10, 30], [20, 50], [35, 65], [75, 100], [5, 20], [90, 100], [70, 95], [20, 50], [25, 55], [30, 60], [8, 25]],
  Eisplanet: [[10, 30], [10, 30], [5, 20], [5, 20], [2, 15], [5, 25], [5, 20], [15, 45], [25, 60], [25, 60], [5, 25], [85, 100], [15, 45], [25, 60], [35, 75], [45, 80], [10, 35]],
  Vulkanplanet: [[55, 90], [30, 60], [50, 85], [35, 70], [10, 35], [30, 65], [25, 60], [65, 95], [10, 35], [25, 60], [30, 70], [10, 40], [40, 80], [25, 60], [5, 25], [20, 55], [20, 55]],
  Metallplanet: [[75, 100], [30, 65], [50, 90], [55, 95], [20, 55], [25, 60], [25, 60], [20, 55], [10, 35], [10, 35], [25, 65], [1, 20], [1, 25], [5, 30], [1, 15], [15, 45], [15, 45]],
  Kohlenstoffplanet: [[15, 40], [10, 35], [10, 30], [10, 35], [5, 25], [10, 30], [15, 50], [25, 60], [75, 100], [20, 55], [10, 35], [10, 45], [20, 60], [10, 35], [70, 100], [20, 50], [10, 40]],
  Supererde: [[45, 85], [30, 60], [40, 75], [35, 70], [15, 40], [25, 60], [25, 60], [55, 90], [20, 50], [25, 60], [35, 75], [20, 60], [35, 75], [20, 55], [10, 40], [25, 60], [20, 60]],
  Planetoid: [[20, 90], [20, 85], [10, 80], [10, 80], [5, 65], [10, 70], [10, 70], [10, 90], [10, 90], [5, 70], [5, 80], [1, 70], [1, 65], [1, 70], [1, 80], [5, 85], [2, 65]],
  Gasriese: [[1, 8], [1, 8], [1, 6], [1, 6], [1, 5], [1, 6], [1, 8], [1, 10], [45, 85], [1, 20], [1, 8], [30, 75], [90, 100], [75, 100], [65, 100], [70, 100], [3, 25]],
  Eisriese: [[1, 12], [1, 12], [1, 10], [1, 10], [1, 8], [1, 12], [2, 15], [1, 15], [50, 90], [10, 35], [1, 12], [70, 100], [90, 100], [65, 95], [75, 100], [75, 100], [8, 35]],
  Schwefelplanet: [[25, 55], [15, 40], [15, 45], [20, 50], [5, 20], [10, 40], [15, 45], [55, 85], [10, 35], [60, 95], [15, 45], [5, 35], [45, 85], [15, 50], [10, 40], [15, 45], [10, 35]],
};

/** Alle Fördergüte-Bereiche für einen Planetentyp als `{ resourceTypeId, range }[]`. */
export function rangesForPlanetType(type: PlanetType): { resourceTypeId: string; range: ConcentrationRange }[] {
  return RESOURCE_ORDER.map((resourceTypeId, i) => ({ resourceTypeId, range: RAW_RANGES[type][i] }));
}

export const PLANET_TYPES: PlanetType[] = [
  'TemperierterBiosphaerenplanet', 'Silikatplanet', 'Wuestenplanet', 'Ozeanplanet', 'Eisplanet',
  'Vulkanplanet', 'Metallplanet', 'Kohlenstoffplanet', 'Supererde', 'Planetoid', 'Gasriese',
  'Eisriese', 'Schwefelplanet',
];

export const PLANET_TYPE_LABELS: Record<PlanetType, string> = {
  TemperierterBiosphaerenplanet: 'Temperierter Biosphärenplanet',
  Silikatplanet: 'Silikatplanet',
  Wuestenplanet: 'Wüstenplanet',
  Ozeanplanet: 'Ozeanplanet',
  Eisplanet: 'Eisplanet',
  Vulkanplanet: 'Vulkanplanet',
  Metallplanet: 'Metallplanet',
  Kohlenstoffplanet: 'Kohlenstoffplanet',
  Supererde: 'Supererde',
  Planetoid: 'Planetoid',
  Gasriese: 'Gasriese',
  Eisriese: 'Eisriese',
  Schwefelplanet: 'Schwefelplanet',
};
