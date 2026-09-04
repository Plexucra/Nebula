import { Id } from './common.model';

/**
 * 'Fuel': Elerium-115-Kette (Antriebs- und Energieversorgungs-Treibstoff,
 * siehe `Umsetzungskonzept/01_...`, §3 "PowerUpkeepJob") – eigene Kategorie
 * statt BuildingMaterial, da sowohl einmalig verbaut (Antriebsmodul) als
 * auch laufend verbraucht (Energienetz-Unterhalt).
 * 'Facility': planetare Anlagen (Ebene 7 im Produktionsbaum, z. B.
 * Schiffswerft, Koloniehabitat) – aktuell als Katalogeintrag vorhanden,
 * aber noch nicht mit dem Gebäude-Ausbausystem verknüpft (siehe
 * product-catalog.ts Kopfkommentar).
 */
export type ProductCategory = 'Ship' | 'GroundUnit' | 'ConsumerGood' | 'BuildingMaterial' | 'RawResource' | 'Fuel' | 'Facility';

export interface RecipeInput {
  inputProductTypeId: Id;
  quantity: number;
}

export interface ResourceWeight {
  resourceTypeId: Id;
  weight: number;
}

/** Siehe Umsetzungskonzept/02_..., §1. tier 0 = Rohstoff, steigt Richtung Endprodukt. */
export interface ProductType {
  id: Id;
  name: string;
  category: ProductCategory;
  tier: number;
  recipe: RecipeInput[];
  resourceProfile: ResourceWeight[];
  /** Basis-Produktionszeit in Spielstunden für 1 Einheit. */
  baseProductionHours: number;
  /** Benötigte Arbeitskraft (Bevölkerung) pro paralleler Produktionseinheit. */
  baseWorkforceRequired: number;
  /** Masse pro Einheit in kg – künftig neben `volumeM3` begrenzender Faktor für Frachterladung. */
  massKg: number;
  /** Volumen pro Einheit in m³ – künftig neben `massKg` begrenzender Faktor für Frachterladung. */
  volumeM3: number;
  /**
   * Eleriumbedarf pro Charge in Gramm Rohäquivalent (Prozessenergie plus im
   * Produkt gebundenes Elerium), siehe `Nebula_Flache_Produktliste_...md`
   * §1.1. Reiner Datenwert – wird aktuell NICHT als Produktionsvoraussetzung
   * geprüft oder verbraucht (wie bei `category: 'Facility'` eine bewusste
   * Lücke für einen späteren Schritt; einzige aktive Elerium-Verbrauchsstelle
   * bleibt der PowerUpkeepJob des Energienetzes).
   */
  eleriumG: number;
  /** Kurzbeschreibung für UI-Tooltips. */
  description: string;
}
