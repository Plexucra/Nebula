import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { GAME_API } from '../../core/sim/game-api.token';
import { Id, ProductType } from '../../core/models';

/** Explizite Icons für die bekanntesten/prominentesten Produkte (Rohstoffe, Schiffe, Bodeneinheiten). */
const PRODUCT_ICON: Record<string, string> = {
  p_ferrometall: '⛏️', p_leichtmetall: '⛏️', p_refraktaer: '⛏️', p_leitmetall: '⛏️', p_edelmetall: '⛏️',
  p_seltenerden: '⛏️', p_technometall: '⛏️', p_silikat: '🪨', p_kohlenstoff: '🪨', p_salz: '🧂',
  p_radionuklid: '☢️', p_eis: '🧊', p_atmosphaere: '💨', p_edelgas: '💨', p_kohlenwasserstoff: '🛢️',
  p_isotopentraeger: '⚛️', p_elerium: '☢️',
  p_corvette: '🛩️', p_destroyer: '🚢', p_cruiser: '🛳️', p_freighter: '📦', p_carrier: '🛸', p_trooptransport: '🚐',
  p_soldier: '💂', p_drone_light: '🤖', p_drone_medium: '🤖', p_drone_heavy: '🤖',
};
const DEFAULT_MAX_STOCK = 50;

/**
 * Schlüsselwort-Fallback für die übrigen ~190 Produkte des erweiterten
 * Produktionsbaums (Nebula_Planetentypen_..., §10): ein Icon pro Produkt
 * von Hand zu pflegen ist bei dieser Katalogtiefe nicht praktikabel –
 * grobe visuelle Wiedererkennung nach Wortbestandteil/Kategorie genügt.
 */
function keywordIcon(p: ProductType): string {
  const n = p.name.toLowerCase();
  if (n.includes('waffe') || n.includes('gefechtskopf') || n.includes('initiator')) return '⚔️';
  if (n.includes('schild')) return '🛡️';
  if (n.includes('triebwerk') || n.includes('manöver') || n.includes('antrieb')) return '🚀';
  if (n.includes('sensor') || n.includes('navigation') || n.includes('kommunikation')) return '📡';
  if (n.includes('chip') || n.includes('wafer') || n.includes('halbleiter')) return '🧩';
  if (n.includes('reaktor') || n.includes('kraftwerk') || n.includes('energie')) return '⚡';
  if (n.includes('elerium')) return '☢️';
  if (n.includes('rumpf') || n.includes('struktur') || n.includes('panzer') || n.includes('hitzeschild')) return '🔩';
  if (n.includes('habitat') || n.includes('besatzung') || n.includes('lebenserhaltung') || n.includes('truppenunterbring')) return '🫀';
  if (n.includes('hangar') || n.includes('fracht') || n.includes('lager')) return '📦';
  if (n.includes('werft')) return '🏗️';
  if (n.includes('medizin') || n.includes('pharma') || n.includes('hygiene')) return '💊';
  if (n.includes('nahrung') || n.includes('wasser') || n.includes('trinkwasser')) return '🥫';
  if (n.includes('kleidung') || n.includes('textil')) return '🧥';
  if (n.includes('elektronik') || n.includes('unterhaltung')) return '📺';
  if (n.includes('paket')) return '🎁';
  if (p.category === 'Ship') return '🛸';
  if (p.category === 'GroundUnit') return '🤖';
  if (p.category === 'Facility') return '🏭';
  if (p.category === 'RawResource') return '⛏️';
  if (p.category === 'Fuel') return '🔋';
  return '⚙️';
}

interface ModalRow {
  colonyId: Id;
  colonyName: string;
  industryLevel: number;
  stock: number;
  orderMaxStock: number | undefined;
  orderLocalPrice: number | undefined;
}

@Component({
  selector: 'app-production-overview',
  standalone: true,
  imports: [RouterLink, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './production-overview.component.html',
  styleUrl: './production-overview.component.scss',
})
export class ProductionOverviewComponent {
  protected readonly api = inject(GAME_API);
  protected readonly colonies = this.api.colonies();
  protected readonly productTypes = this.api.productTypes();

  protected readonly tiers = [0, 1, 2, 3, 4, 5, 6];

  protected readonly selectedProduct = signal<ProductType | null>(null);
  protected readonly draftMaxStock: Record<Id, number> = {};
  protected readonly draftLocalPrice: Record<Id, number> = {};
  protected readonly busy = signal<Id | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly modalRows = computed<ModalRow[]>(() => {
    const product = this.selectedProduct();
    if (!product) return [];
    return this.colonies().map(c => {
      const order = this.api.autoProductionOrders(c.id)().find(o => o.productTypeId === product.id);
      return {
        colonyId: c.id,
        colonyName: c.name,
        industryLevel: this.api.buildings(c.id)().find(b => b.typeId === 'b_industry')?.level ?? 0,
        stock: this.api.warehouse(c.id)().find(w => w.productTypeId === product.id)?.quantity ?? 0,
        orderMaxStock: order?.maxStock,
        orderLocalPrice: order?.localPrice,
      };
    });
  });

  protected queueLength(colonyId: string): number { return this.api.productionQueue(colonyId)().length; }
  protected warehouseCount(colonyId: string): number { return this.api.warehouse(colonyId)().length; }

  protected productsByTier(tier: number): ProductType[] {
    return this.productTypes.filter(p => p.tier === tier);
  }

  protected productName(id: string): string {
    return this.productTypes.find(p => p.id === id)?.name ?? id;
  }

  protected productIcon(id: string): string {
    if (PRODUCT_ICON[id]) return PRODUCT_ICON[id];
    const product = this.productTypes.find(p => p.id === id);
    return product ? keywordIcon(product) : '❔';
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
    return this.api.autoProductionOrdersForProduct(productTypeId)().length;
  }

  protected openModal(product: ProductType): void {
    this.error.set(null);
    for (const c of this.colonies()) {
      const existing = this.api.autoProductionOrders(c.id)().find(o => o.productTypeId === product.id);
      this.draftMaxStock[c.id] = existing?.maxStock ?? DEFAULT_MAX_STOCK;
      this.draftLocalPrice[c.id] = existing?.localPrice ?? 0;
    }
    this.selectedProduct.set(product);
  }

  protected closeModal(): void {
    this.selectedProduct.set(null);
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

  protected startOrUpdate(colonyId: Id): void {
    const product = this.selectedProduct();
    if (!product) return;
    const maxStock = this.draftMaxStock[colonyId];
    const localPrice = this.draftLocalPrice[colonyId] ?? 0;
    void this.run(colonyId, () => this.api.setAutoProductionTarget(colonyId, product.id, maxStock, localPrice));
  }

  protected stop(colonyId: Id): void {
    const product = this.selectedProduct();
    if (!product) return;
    void this.run(colonyId, () => this.api.cancelAutoProductionTarget(colonyId, product.id));
  }
}
