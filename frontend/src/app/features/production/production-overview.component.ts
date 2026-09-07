import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { GAME_API } from '../../core/sim/game-api.token';
import { ChainPlan, Id, ProductCategory, ProductType, ProductionQueueEntry } from '../../core/models';
import { iconForProduct } from '../../core/ui/product-icons';
import { PRODUCT_CATEGORY_LABELS } from '../../core/ui/product-category-labels';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';

const DEFAULT_MAX_STOCK = 50;
/** Rekursionsdeckel für `buildTreeNode` – der tiefste reale Rezeptbaum liegt bei Stufe 6, 12 ist reichlich Sicherheitsabstand gegen einen unerwarteten Zyklus im Katalog. */
const MAX_TREE_DEPTH = 12;

interface ModalRow {
  colonyId: Id;
  colonyName: string;
  industryLevel: number;
  stock: number;
  existingEntry: ProductionQueueEntry | undefined;
}

/** Ein Knoten im vollständig aufgeklappten Produktionsbaum eines Produkts, siehe `buildTreeNode`. */
interface TreeNode {
  productTypeId: Id;
  name: string;
  icon: string;
  tier: number;
  isRaw: boolean;
  /** Gesamtbedarf dieses Vorprodukts für EINE Einheit des Wurzelprodukts. */
  quantity: number;
  children: TreeNode[];
}

@Component({
  selector: 'app-production-overview',
  standalone: true,
  imports: [RouterLink, FormsModule, NgTemplateOutlet, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './production-overview.component.html',
  styleUrl: './production-overview.component.scss',
})
export class ProductionOverviewComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);
  protected readonly colonies = this.api.colonies();
  /** Katalog kommt asynchron – bei jedem Zugriff frisch lesen statt einmalig einzufrieren. */
  protected get productTypes() { return this.api.productTypes(); }

  protected readonly categoryLabels = PRODUCT_CATEGORY_LABELS;
  protected readonly countdown = formatCountdown;

  // --- Produktionskette: Kategorie → Stufe → Produkt --------------------
  protected readonly selectedCategory = signal<ProductCategory | null>(null);

  protected categoriesPresent(): ProductCategory[] {
    const seen = new Set<ProductCategory>();
    const order: ProductCategory[] = [];
    for (const p of this.productTypes) {
      if (!seen.has(p.category)) { seen.add(p.category); order.push(p.category); }
    }
    return order;
  }

  protected countInCategory(cat: ProductCategory): number {
    return this.productTypes.filter(p => p.category === cat).length;
  }

  protected tiersInCategory(cat: ProductCategory): number[] {
    return [...new Set(this.productTypes.filter(p => p.category === cat).map(p => p.tier))].sort((a, b) => a - b);
  }

  protected productsInCategoryTier(cat: ProductCategory, tier: number): ProductType[] {
    return this.productTypes
      .filter(p => p.category === cat && p.tier === tier)
      .sort((a, b) => a.name.localeCompare(b.name, 'de'));
  }

  protected chooseCategory(cat: ProductCategory): void {
    this.selectedCategory.set(cat);
  }

  protected backToCategories(): void {
    this.selectedCategory.set(null);
  }

  // --- Produkt-Detailansicht ---------------------------------------------
  protected readonly selectedProduct = signal<ProductType | null>(null);
  protected readonly produceOpen = signal(false);
  protected readonly draftQty: Record<Id, number> = {};
  protected readonly draftAutoMissing: Record<Id, boolean> = {};
  protected readonly draftRequeue: Record<Id, boolean> = {};
  protected readonly busy = signal<Id | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly chainPreview: Partial<Record<Id, ChainPlan>> = {};
  protected readonly chainPreviewLoading = signal<Id | null>(null);

  protected readonly modalRows = computed<ModalRow[]>(() => {
    const product = this.selectedProduct();
    if (!product) return [];
    return this.colonies().map(c => {
      const existingEntry = this.api.productionQueue(c.id)().find(e => e.productTypeId === product.id);
      return {
        colonyId: c.id,
        colonyName: c.name,
        industryLevel: this.api.buildings(c.id)().find(b => b.typeId === 'b_industry')?.level ?? 0,
        stock: this.api.warehouse(c.id)().find(w => w.productTypeId === product.id)?.quantity ?? 0,
        existingEntry,
      };
    });
  });

  protected openDetail(product: ProductType): void {
    this.error.set(null);
    this.produceOpen.set(false);
    for (const c of this.colonies()) {
      this.draftQty[c.id] = DEFAULT_MAX_STOCK;
      this.draftAutoMissing[c.id] = true;
      this.draftRequeue[c.id] = false;
      delete this.chainPreview[c.id];
    }
    this.selectedProduct.set(product);
  }

  protected closeDetail(): void {
    this.selectedProduct.set(null);
    this.produceOpen.set(false);
  }

  protected openProduce(): void {
    this.produceOpen.set(true);
  }

  /** Baut den kompletten Produktionsbaum (Rezept-Eingänge rekursiv bis zu den Rohstoffen) für EINE Einheit des angegebenen Produkts – rein aus dem statischen Katalog, unabhängig von einer Kolonie. */
  private buildTreeNode(productTypeId: Id, quantity: number, depth: number): TreeNode {
    const product = this.productTypes.find(p => p.id === productTypeId);
    const children = product && depth < MAX_TREE_DEPTH
      ? product.recipe.map(r => this.buildTreeNode(r.inputProductTypeId, quantity * r.quantity, depth + 1))
      : [];
    return {
      productTypeId,
      name: product?.name ?? productTypeId,
      icon: this.productIcon(productTypeId),
      tier: product?.tier ?? 0,
      isRaw: !product || product.recipe.length === 0,
      quantity,
      children,
    };
  }

  protected productionTree(): TreeNode | null {
    const p = this.selectedProduct();
    return p ? this.buildTreeNode(p.id, 1, 0) : null;
  }

  protected queueLength(colonyId: string): number { return this.api.productionQueue(colonyId)().length; }
  protected warehouseCount(colonyId: string): number { return this.api.warehouse(colonyId)().length; }

  /** Der gerade laufende Auftrag dieser Kolonie (höchstens einer je Kolonie), für die Kolonienübersicht oben. */
  protected runningEntry(colonyId: Id): ProductionQueueEntry | undefined {
    return this.api.productionQueue(colonyId)().find(e => e.status === 'running');
  }

  /** Der als Nächstes anstehende Auftrag (wartend oder angehalten) – erster Nicht-laufender Eintrag in Warteschlangenreihenfolge. */
  protected nextEntry(colonyId: Id): ProductionQueueEntry | undefined {
    return this.api.productionQueue(colonyId)().find(e => e.status !== 'running');
  }

  /** Weitere Aufträge über den laufenden und den nächsten hinaus, für den "+N weitere"-Hinweis. */
  protected remainingQueueCount(colonyId: Id): number {
    const total = this.queueLength(colonyId);
    const shown = (this.runningEntry(colonyId) ? 1 : 0) + (this.nextEntry(colonyId) ? 1 : 0);
    return Math.max(0, total - shown);
  }

  protected queueProgressPct(entry: { status: string; startedAt: number | null; endsAt: number | null }): number {
    if (entry.status !== 'running' || entry.startedAt === null || entry.endsAt === null) return 0;
    const total = entry.endsAt - entry.startedAt;
    if (total <= 0) return 100;
    return Math.min(100, Math.max(0, ((this.clock.now() - entry.startedAt) / total) * 100));
  }

  protected queueStatusLabel(entry: { status: string }): string {
    switch (entry.status) {
      case 'running': return 'läuft';
      case 'stopped': return 'gestoppt';
      case 'done': return 'fertig';
      default: return 'wartet';
    }
  }

  protected productName(id: string): string {
    return this.productTypes.find(p => p.id === id)?.name ?? id;
  }

  protected productIcon(id: string): string {
    const product = this.productTypes.find(p => p.id === id);
    return product ? iconForProduct(product) : '❔';
  }

  protected formatMass(kg: number): string {
    if (kg >= 1000) return `${(kg / 1000).toLocaleString('de-DE', { maximumFractionDigits: 1 })} t`;
    return `${kg.toLocaleString('de-DE')} kg`;
  }

  protected formatVolume(m3: number): string {
    if (m3 < 1) return `${Math.round(m3 * 1000)} l`;
    return `${m3.toLocaleString('de-DE', { maximumFractionDigits: 1 })} m³`;
  }

  /** Nur Waren aus dem Industriekomplex laufen als Dauerauftrag – Schiffe/Bodeneinheiten haben eigene Werft-/Ausbildungs-UI. */
  protected isIndustryProduct(p: ProductType): boolean {
    return p.category !== 'Ship' && p.category !== 'GroundUnit';
  }

  protected activeColonyCount(productTypeId: Id): number {
    return this.colonies().filter(c =>
      this.api.productionQueue(c.id)().some(e => e.productTypeId === productTypeId && e.status !== 'done')).length;
  }

  private async run(key: Id, action: () => Promise<unknown>): Promise<void> {
    this.error.set(null);
    this.busy.set(key);
    try {
      await action();
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Aktion fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }

  /** Reine Vorschau unter dem aktuellen Lagerbestand DIESER Kolonie – die vom Backend berechneten Realwerte für den Bau (Kettenschritte, Dauer, Arbeitskräfte). */
  protected previewFor(colonyId: Id): void {
    const product = this.selectedProduct();
    if (!product) return;
    const qty = this.draftQty[colonyId] ?? DEFAULT_MAX_STOCK;
    this.chainPreviewLoading.set(colonyId);
    this.api.previewProductionChain(colonyId, product.id, qty).then(plan => {
      this.chainPreview[colonyId] = plan;
      if (this.chainPreviewLoading() === colonyId) this.chainPreviewLoading.set(null);
    });
  }

  protected queueHere(colonyId: Id): void {
    const product = this.selectedProduct();
    if (!product) return;
    const qty = this.draftQty[colonyId] ?? DEFAULT_MAX_STOCK;
    const autoMissing = this.draftAutoMissing[colonyId] ?? true;
    const requeue = this.draftRequeue[colonyId] ?? false;
    void this.run(colonyId, () => this.api.queueProduction(colonyId, product.id, qty, autoMissing, requeue));
  }

  protected resumeHere(colonyId: Id, entryId: Id): void {
    void this.run(colonyId, () => this.api.resumeProduction(colonyId, entryId));
  }

  protected cancelHere(colonyId: Id, entryId: Id): void {
    void this.run(colonyId, () => this.api.cancelProduction(colonyId, entryId));
  }
}
