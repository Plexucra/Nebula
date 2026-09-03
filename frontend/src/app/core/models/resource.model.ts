import { Id } from './common.model';

/** Rohstoffkategorie – rein deskriptiv, siehe Konzeption/Umsetzungskonzept/Nebula_Planetentypen_..., §4. */
export type ResourceCategory = 'Metalle' | 'Mineralien' | 'Fluide' | 'Elerium';

export interface ResourceType {
  id: Id;
  name: string;
  category: ResourceCategory;
  description: string;
}

/** Konzentration eines Rohstoffs auf einem Planeten (0..∞, unerschöpflich). */
export interface PlanetResourceConcentration {
  resourceTypeId: Id;
  concentration: number;
}
