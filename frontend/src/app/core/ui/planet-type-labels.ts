import { PlanetType } from '../models';

/** Anzeigenamen der Planetentypen für UI-Komponenten (siehe `PlanetType`-Model). */
export const PLANET_TYPE_LABEL: Record<PlanetType, string> = {
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

export function planetTypeLabel(type: PlanetType): string {
  return PLANET_TYPE_LABEL[type] ?? type;
}
