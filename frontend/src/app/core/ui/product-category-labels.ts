import { ProductCategory } from '../models';

/** Deutsche Anzeigenamen für die Kategorie-Auswahl im `ProductPickerDialogComponent`. */
export const PRODUCT_CATEGORY_LABELS: Record<ProductCategory, string> = {
  RawResource: 'Rohstoffe',
  ConsumerGood: 'Konsumgüter',
  BuildingMaterial: 'Baumaterial',
  Fuel: 'Treibstoff',
  Ship: 'Schiffe',
  GroundUnit: 'Bodeneinheiten',
};
