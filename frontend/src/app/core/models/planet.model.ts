import { Id } from './common.model';
import { PlanetResourceConcentration } from './resource.model';

export type PlanetSize = 'Klein' | 'Mittel' | 'Groß' | 'Riesig';

/**
 * Wirtschaftlich relevante Oberflächen-/Zusammensetzungsklasse, siehe
 * Konzeption/Umsetzungskonzept/Nebula_Planetentypen_Rohstoffprofile_
 * Produktionsbaum.md, §5. Bestimmt den Fördergüte-Bereich je Rohstoff
 * (§6) – keine exakte Astronomieklasse.
 */
export type PlanetType =
  | 'TemperierterBiosphaerenplanet'
  | 'Silikatplanet'
  | 'Wuestenplanet'
  | 'Ozeanplanet'
  | 'Eisplanet'
  | 'Vulkanplanet'
  | 'Metallplanet'
  | 'Kohlenstoffplanet'
  | 'Supererde'
  | 'Planetoid'
  | 'Gasriese'
  | 'Eisriese'
  | 'Schwefelplanet';

export interface Planet {
  id: Id;
  systemId: Id;
  name: string;
  size: PlanetSize;
  type: PlanetType;
  /** Gesamt-Bebauungskapazität des Himmelskörpers (über alle Kolonien hinweg). */
  buildCapacity: number;
  resourceConcentration: PlanetResourceConcentration[];
  /** Bahn-Index im System, rein fürs Layout der Systemkarte. */
  orbitIndex: number;
}
