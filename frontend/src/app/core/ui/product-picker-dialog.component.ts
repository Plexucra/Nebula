import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { Id, ProductCategory, ProductType } from '../models';
import { PRODUCT_CATEGORY_LABELS } from './product-category-labels';

interface TierGroup {
  tier: number;
  products: ProductType[];
}

/**
 * Ersetzt ein flaches `<select>` über den kompletten (~200 Einträge)
 * Produktkatalog: erst Kategorie wählen, dann innerhalb der Kategorie nach
 * Stufe gruppiert und alphabetisch sortiert auswählen – analog zur
 * Stufen-Gruppierung auf der "Produktion"-Übersichtsseite
 * (`production-overview.component`).
 */
@Component({
  selector: 'app-product-picker-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-picker-dialog.component.html',
  styleUrl: './product-picker-dialog.component.scss',
})
export class ProductPickerDialogComponent {
  @Input({ required: true }) products: ProductType[] = [];
  @Input() open = false;
  @Output() readonly closed = new EventEmitter<void>();
  @Output() readonly picked = new EventEmitter<Id>();

  protected readonly categoryLabels = PRODUCT_CATEGORY_LABELS;
  protected readonly selectedCategory = signal<ProductCategory | null>(null);

  protected categoriesPresent(): ProductCategory[] {
    const seen = new Set<ProductCategory>();
    const order: ProductCategory[] = [];
    for (const p of this.products) {
      if (!seen.has(p.category)) { seen.add(p.category); order.push(p.category); }
    }
    return order;
  }

  protected countInCategory(cat: ProductCategory): number {
    return this.products.filter(p => p.category === cat).length;
  }

  protected tierGroupsFor(cat: ProductCategory): TierGroup[] {
    const inCategory = this.products.filter(p => p.category === cat);
    const tiers = [...new Set(inCategory.map(p => p.tier))].sort((a, b) => a - b);
    return tiers.map(tier => ({
      tier,
      products: inCategory.filter(p => p.tier === tier).sort((a, b) => a.name.localeCompare(b.name, 'de')),
    }));
  }

  protected chooseCategory(cat: ProductCategory): void {
    this.selectedCategory.set(cat);
  }

  protected back(): void {
    this.selectedCategory.set(null);
  }

  protected pick(id: Id): void {
    this.picked.emit(id);
    this.dismiss();
  }

  protected dismiss(): void {
    this.selectedCategory.set(null);
    this.closed.emit();
  }
}
