import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { BuildingType, ChainPlan, Id, MarketOrder, MaterialRequirement, PlanetType, ProductionQueueEntry } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';
import { planetTypeLabel } from '../../core/ui/planet-type-labels';
import { ProductPickerDialogComponent } from '../../core/ui/product-picker-dialog.component';
import { PopulationChartComponent } from '../../shared/population-chart.component';
import { ENERGY_RESERVE_DEFAULT_GAME_HOURS } from '../../core/shared-constants';

/**
 * Der frühere eigene Tab "verteidigung" ist entfallen: Er enthielt EINE
 * Gebäudezeile, die nicht einmal den Gebäudenamen trug. Die Planetare Abwehr
 * steht jetzt in der Bebauungsliste – sie ist ein Gebäude mit Bebauungsplatz
 * wie die anderen. Alte Links mit `?tab=verteidigung` landen über
 * `parseTab` auf der Bebauung.
 */
type Tab = 'uebersicht' | 'bebauung' | 'produktion' | 'bodentruppen' | 'bevoelkerung' | 'handel';

/** Platzhalter, solange eine Vorschau noch nicht (neu) berechnet wurde – siehe `refreshNewOrderPreview`/`toggleQueueEntry`. */
const EMPTY_CHAIN_PLAN: ChainPlan = { totalHours: 0, steps: [], feasible: true, totalWorkHours: 0, workersBoundPerHour: 0 };

@Component({
  selector: 'app-colony-detail',
  standalone: true,
  imports: [RouterLink, DecimalPipe, FormsModule, ProductPickerDialogComponent, PopulationChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './colony-detail.component.html',
  styleUrl: './colony-detail.component.scss',
})
export class ColonyDetailComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly colonyId: Id = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly colony = this.api.colony(this.colonyId);
  protected readonly stats = this.api.colonyStats(this.colonyId);
  protected readonly population = this.api.population(this.colonyId);
  protected readonly popWallet = this.api.populationWallet(this.colonyId);
  /**
   * ACHTUNG, hier lag ein Fehler: Diese vier Abfragen hingen an
   * `this.colony()?.planetId ?? ''`, ausgewertet EINMAL bei der Feldinitialisierung.
   * Zu diesem Zeitpunkt ist `colony()` noch leer, es ging also dauerhaft eine
   * leere Id an den Server – das Panel "Himmelskörper" blieb leer, die Brotkrume
   * zeigte "← Planeten · ·" und der Handel-Tab kannte keine Orders. Argumente
   * einer Abfrage müssen REAKTIV sein, deshalb `computed`.
   */
  protected readonly planet = computed(() => {
    const planetId = this.colony()?.planetId;
    return planetId ? this.api.planet(planetId)() : undefined;
  });
  protected readonly moneyState = computed(() => {
    const planetId = this.colony()?.planetId;
    return planetId ? this.api.moneySupplyState(planetId)() : undefined;
  });
  protected readonly buildings = this.api.buildings(this.colonyId);
  protected readonly warehouse = this.api.warehouse(this.colonyId);
  protected readonly supplyInventory = this.api.supplyInventory(this.colonyId);

  /**
   * EINE Lagertabelle statt zweier fast gleicher: "Versorgungsinventar" und
   * "Lagerbestand" listeten dieselben Bestände untereinander und unterschieden
   * sich nur darin, dass das eine Verbrauch/Reichweite und das andere den
   * "Anbieten"-Knopf hatte.
   */
  protected readonly stockRows = computed(() => {
    const supply = this.supplyInventory();
    const byId = new Map(supply.map(s => [s.productTypeId, s]));
    const rows = supply.map(s => ({ ...s, sellable: true }));
    for (const w of this.warehouse()) {
      if (byId.has(w.productTypeId)) continue;
      rows.push({
        productTypeId: w.productTypeId,
        name: this.productName(w.productTypeId),
        category: '',
        quantity: w.quantity,
        consumptionPerGameHour: 0,
        coverageGameHours: null,
        pendingFraction: 0,
        reserved: 0,
        sellable: true,
      });
    }
    return rows.sort((a, b) => a.name.localeCompare(b.name, 'de'));
  });

  /**
   * Reichweite in Tagen/Monaten statt in Spielstunden. "reicht 50.711,9 h" ist
   * keine Zahl, mit der man planen kann.
   */
  /**
   * Steckbrief einer Bodeneinheit für die Rekrutierungsauswahl. Vorher war das
   * ein reines Namens-Auswahlfeld mit vier Einträgen – ohne Aufwand,
   * Konterverhältnis oder den Hinweis, dass Soldaten selbst nicht kämpfen.
   */
  protected groundUnitFacts(productTypeId: Id): string {
    const def = this.api.groundUnitTypes().find(u => u.productTypeId === productTypeId);
    const product = this.api.productTypes().find(p => p.id === productTypeId);
    if (!def || !product) return '';
    const parts = [`${Math.round(product.workHoursPerUnit).toLocaleString('de-DE')} Ah`];
    if (def.class === 'Soldier') {
      parts.push(`kommandiert bis zu ${this.dronesPerSoldier} Drohnen`, 'ohne eigene Kampfwirkung');
    } else {
      parts.push('autonome Drohne – kämpft nur, wenn ein Soldat sie kommandiert');
      if (def.countersClass) parts.push(`kontert ${this.groundUnitClassLabel(def.countersClass)}`);
    }
    return parts.join(' · ');
  }

  protected readonly dronesPerSoldier = 5;

  protected groundUnitClassLabel(unitClass: string): string {
    const byClass: Record<string, string> = {
      Soldier: 'Soldaten', LightDrone: 'leichte Drohnen',
      MediumDrone: 'mittlere Drohnen', HeavyDrone: 'schwere Drohnen',
    };
    return byClass[unitClass] ?? unitClass;
  }

  /** Verschiebt einen wartenden Auftrag in der Warteschlange, siehe `GameApi.moveProductionEntry`. */
  protected moveQueueEntry(entryId: Id, direction: -1 | 1): void {
    void this.run(`move:${entryId}`, () => this.api.moveProductionEntry(this.colonyId, entryId, direction));
  }

  /**
   * Wachstumsrate je SPIELSTUNDE aus derselben Quelle, die auch der
   * Bebauungs-Tab und die Verlaufsgrafik nutzen (`colonySpeedBreakdown`).
   * Die Rate je Tick schwankte zu stark, um daneben eine Aussage wie
   * "Bevölkerung geht zurück" zu tragen.
   */
  protected readonly growthPerHour = computed(() => this.speedBreakdown()?.growthPerHour ?? 0);

  /** Reichweite des Bevölkerungsvorrats in Tagen, als Text. */
  protected supplyDays(days: number): string {
    if (days <= 0) return 'leer';
    if (days < 1) return `${Math.round(days * 24)} Spielstunden`;
    return `${days.toLocaleString('de-DE', { maximumFractionDigits: 1 })} Tage`;
  }

  protected formatRange(gameHours: number): string {
    if (gameHours < 48) return `${gameHours.toFixed(1)} h`;
    const days = gameHours / 24;
    if (days < 60) return `${days.toFixed(0)} Spieltage`;
    return `${(days / 30).toFixed(0)} Spielmonate`;
  }

  /**
   * Preisanhalt beim Anbieten: Was die Bevölkerung dieser Kolonie je Stück
   * überhaupt aufbringen kann. Vorher gab es dafür keinerlei Anhaltspunkt – ein
   * zu hoch gesetzter Preis führte nur zur Meldung "kann sich das nicht
   * leisten", ohne zu sagen, welcher Preis ginge.
   */
  protected priceHint(productTypeId: Id): string | null {
    const consumerGoods = ['p_grundnahrung', 'p_grundmedizin', 'p_unterhaltungselektronik'];
    if (!consumerGoods.includes(productTypeId)) return null;
    const wallet = this.popWallet()?.balance ?? 0;
    const population = this.population()?.currentCount ?? 0;
    if (population <= 0 || wallet <= 0) return null;
    // Der Tageseinkauf (Umsetzungskonzept/36) verteilt das Guthaben der
    // Bevölkerung zu gleichen Teilen auf die drei Grundgüter und füllt damit
    // den Vorrat auf das Ziel auf.
    const perGood = wallet / consumerGoods.length;
    const good = this.populationSupply()?.goods.find(g => g.productTypeId === productTypeId);
    const missing = good ? Math.max(0, Math.ceil(good.dailyNeed * (this.populationSupply()?.targetDays ?? 0)) - good.stock) : 0;
    const perUnit = missing > 0 ? ` Für die fehlenden ${missing} Stück Vorrat wären das bis zu ${Math.round(perGood / missing).toLocaleString('de-DE')} Cr je Stück.` : '';
    return `Kaufkraft der Bevölkerung: ${Math.round(wallet).toLocaleString('de-DE')} Cr insgesamt`
      + ` – für dieses Gut sind beim nächsten Tageseinkauf rund ${Math.round(perGood).toLocaleString('de-DE')} Cr verfügbar.${perUnit}`;
  }
  protected readonly specializations = this.api.specializations(this.colonyId);
  protected readonly productionQueue = this.api.productionQueue(this.colonyId);
  protected readonly groundForces = this.api.groundForces(this.colonyId);
  protected readonly recruitmentQueue = this.api.recruitmentQueue(this.colonyId);
  // --- Handelsposten des Planeten (Umsetzungskonzept/37): EIN Orderbuch je Planet ---
  /** Alle offenen Orders am Posten dieses Planeten – von allen Kolonien darauf und allen gelandeten Flotten. */
  protected readonly postOrders = computed(() => {
    const c = this.colony();
    return c ? this.api.hubOrders(c.systemId, c.planetId)() : [];
  });
  protected readonly postAsks = computed(() => this.postOrders().filter(o => o.side === 'Sell')
    .sort((a, b) => a.productTypeId.localeCompare(b.productTypeId) || a.limitPrice - b.limitPrice));
  protected readonly postBids = computed(() => this.postOrders().filter(o => o.side === 'Buy')
    .sort((a, b) => a.productTypeId.localeCompare(b.productTypeId) || b.limitPrice - a.limitPrice));
  /** Eigenes Depot am Posten – nur gefüllt, wenn man hier KEINE Kolonie hat (sonst ist das Lager das Depot). */
  protected readonly postDepot = computed(() => {
    const c = this.colony();
    return c ? this.api.hubDepot(c.systemId, c.planetId)() : [];
  });
  /** Verkaufs-Orders an den Posten der ANDEREN Planeten dieses Systems – reine Übersicht. */
  protected readonly otherPostOrders = computed(() => {
    const c = this.colony();
    return c ? this.api.sellOrders(c.systemId)().filter(o => o.planetId !== c.planetId) : [];
  });
  protected readonly system = computed(() => {
    const systemId = this.colony()?.systemId;
    return systemId ? this.api.system(systemId)() : undefined;
  });
  protected readonly housingCapacity = this.api.housingCapacity(this.colonyId);
  protected readonly powerCoverage = this.api.powerCoverage(this.colonyId);
  protected readonly powerUpkeepPerHour = this.api.powerUpkeepPerHour(this.colonyId);
  /** Energiespeicher der Kolonie (Umsetzungskonzept/32_...md) – Anzeige und Konfiguration im Tab "Bebauung". */
  protected readonly energyStorage = this.api.energyStorage(this.colonyId);
  /** Eingabe für die Vorhaltemenge; null = noch nichts eingegeben. */
  protected energyReserveDraft: number | null = null;
  /**
   * Alle Tempo-/Kostenfaktoren dieser Kolonie – fertig BERECHNET vom Backend
   * (siehe `GameApi.colonySpeedBreakdown`). Früher rechnete diese Komponente
   * sie aus einer zweiten Formelkopie (`engine/formulas.ts`) nach; die Regeln
   * leben jetzt ausschließlich im Backend, die Transparenz-Anzeige bleibt
   * dabei vollständig erhalten (Umsetzungskonzept/15_...md, Auftrag 3).
   */
  protected readonly speedBreakdown = this.api.colonySpeedBreakdown(this.colonyId);
  protected readonly consumptionCoverage = this.api.consumptionCoverage(this.colonyId);
  /** Vorrat und Tageseinkauf der Bevölkerung (Umsetzungskonzept/36), Panel im Tab "Bevölkerung". */
  protected readonly populationSupply = this.api.populationSupply(this.colonyId);
  protected readonly populationTrend = this.api.populationTrend(this.colonyId);

  /** Reaktiv: `player()` ist beim echten Backend erst nach der ersten Server-Antwort gesetzt (siehe TradeOverviewComponent). */
  protected readonly playerId = computed(() => this.api.player()?.id ?? '');
  protected readonly allPlayers = this.api.players();
  protected readonly tab = signal<Tab>(this.initialTab());
  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private static readonly OWNER_ONLY_TABS: Tab[] = ['bebauung', 'produktion', 'bodentruppen', 'bevoelkerung'];

  /** Nur die eigene Kolonie erlaubt Bau/Produktion/Truppen/Verwaltung – fremde Kolonien (siehe „System Handel") sind nur für Übersicht/Handel einsehbar. */
  protected isOwnColony(): boolean {
    return this.colony()?.ownerId === this.playerId();
  }

  /** Fällt für Besitzer-only-Tabs auf „Übersicht" zurück, sobald die Kolonie nicht (mehr) der eigenen gehört. */
  protected effectiveTab(): Tab {
    const t = this.tab();
    return (ColonyDetailComponent.OWNER_ONLY_TABS as readonly Tab[]).includes(t) && !this.isOwnColony() ? 'uebersicht' : t;
  }

  protected ownerDisplay(): string {
    const ownerId = this.colony()?.ownerId;
    if (!ownerId) return 'Unbekannt';
    return this.allPlayers().find(p => p.id === ownerId)?.name ?? 'Unbekannt';
  }

  /** Kataloge kommen asynchron nach dem Verbindungsaufbau – deshalb bei jedem Zugriff frisch lesen, nicht einmalig im Feld einfrieren. */
  protected get buildingTypes() { return this.api.buildingTypes(); }
  protected get productTypes() { return this.api.productTypes().filter(p => p.category !== 'Ship' && p.category !== 'GroundUnit'); }
  protected get groundUnitTypes() { return this.api.productTypes().filter(p => p.category === 'GroundUnit'); }

  /**
   * Handel am Posten ist nur zwischen Kommandanten mit Handelsvertrag möglich
   * (Konzept 05 §14) – die eigene Order ist davon unbenommen (dafür gibt es
   * „Zurückziehen"). Die Bevölkerung kauft ohne Vertrag.
   */
  protected canTradeWith(ownerId: Id | null): boolean {
    if (!ownerId || ownerId === this.playerId()) return true;
    return this.api.hasTradeAgreement(ownerId)();
  }

  protected newProductionProductId = this.productTypes[0]?.id ?? '';
  protected newProductionQty = 1;
  protected newProductionAutoMissing = true;
  protected newProductionRequeue = false;
  protected readonly productPickerOpen = signal(false);
  /** Reine Vorschau (keine Auftragsanlage) für das Neuer-Auftrag-Formular, siehe `refreshNewOrderPreview`. */
  protected readonly newOrderPreview = signal<ChainPlan | null>(null);
  protected readonly newOrderPreviewLoading = signal(false);

  protected newUnitProductId = this.groundUnitTypes[0]?.id ?? '';
  protected newUnitQty = 5;
  protected newUnitAutoMissing = true;
  protected newUnitRequeue = false;

  protected readonly expandedQueueEntry = signal<Id | null>(null);
  /** Vorschau-Pläne für noch nicht laufende (wartende/gestoppte) Warteschlangeneinträge – deren `entry.plan` ist bis zum Start ein Platzhalter, siehe `toggleQueueEntry`. */
  protected readonly queuePreview: Partial<Record<Id, ChainPlan>> = {};
  protected readonly queuePreviewLoading = signal<Id | null>(null);

  protected readonly buyDraftQty: Partial<Record<Id, number>> = {};

  /** Preisänderung einer eigenen Order – Entwurf je Order, `undefined` = Feld zu. */
  protected readonly priceDraft: Partial<Record<Id, number>> = {};

  protected startPriceEdit(orderId: Id, current: number): void {
    this.priceDraft[orderId] = current;
  }

  protected cancelPriceEdit(orderId: Id): void {
    this.priceDraft[orderId] = undefined;
  }

  protected async submitPrice(orderId: Id): Promise<void> {
    const price = this.priceDraft[orderId];
    if (!price || price <= 0) return;
    await this.run(`price:${orderId}`, async () => {
      await this.api.updateSellOrderPrice(orderId, price);
      this.priceDraft[orderId] = undefined;
    });
  }

  protected countdown = formatCountdown;

  private initialTab(): Tab {
    const t = this.route.snapshot.queryParamMap.get('tab');
    if (t === 'verteidigung') return 'bebauung'; // alter Tab, siehe Tab-Typ
    const valid: Tab[] = ['uebersicht', 'bebauung', 'produktion', 'bodentruppen', 'bevoelkerung', 'handel'];
    return (valid as string[]).includes(t ?? '') ? (t as Tab) : 'uebersicht';
  }

  /**
   * Hält die Adresszeile mit dem Tab in Einklang. Vorher blieb dort ein alter
   * `?tab=...` stehen, sodass Zurück-Taste, Neuladen und weitergegebene Links
   * auf dem falschen Tab landeten.
   */
  protected setTab(t: Tab): void {
    this.tab.set(t);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: t },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected buildingFor(typeId: Id) {
    return this.buildings().find(b => b.typeId === typeId);
  }

  protected buildingLevel(typeId: Id): number {
    return this.buildingFor(typeId)?.level ?? 0;
  }

  /**
   * Laufender Unterhalt dieses Gebäudes je Spielstunde (aktuelle Stufe) und was
   * die nächste Stufe zusätzlich kostet. Der Wert steckt seit jeher im Katalog
   * (`BuildingType.upkeepPerLevel`) und wird jeden Tick abgebucht, war aber
   * NIRGENDS in der Oberfläche zu sehen: Der Ausbau nannte nur die einmaligen
   * Kosten, während er die Dauerkosten erhöhte – im Testlauf der direkte Weg in
   * den Bankrott.
   */
  protected currentUpkeep(bt: BuildingType): number {
    return bt.upkeepPerLevel * this.buildingLevel(bt.id);
  }

  protected additionalUpkeep(bt: BuildingType): number {
    return bt.upkeepPerLevel;
  }

  /** Baustoffe, die dem nächsten Ausbau noch fehlen – Grundlage für den Bündelauftrag. */
  protected missingMaterials(bt: BuildingType): MaterialRequirement[] {
    return this.upgradeMaterials(bt).filter(m => m.available + 1e-9 < m.required);
  }

  /**
   * Reiht alle fehlenden Baustoffe als EINEN Auftrag ein.
   *
   * <p>Der Ausweg aus der Einstiegsfalle: Ein Ausbau braucht bis zu fünf
   * Baustoffe gleichzeitig, und mehrere davon sind Vorprodukte voneinander
   * (Leitermetall steckt im Leiterbündel). Wer sie nacheinander einreiht,
   * verliert den zuerst produzierten still an den zweiten Auftrag. Es gab genau
   * eine richtige Reihenfolge – und die stand nirgends. Der Bündelauftrag macht
   * die Reihenfolge irrelevant.</p>
   */
  protected produceMissingMaterials(typeId: Id): void {
    void this.run(`materials:${typeId}`, () => this.api.queueMissingBuildingMaterials(this.colonyId, typeId));
  }

  private upgradePreview(typeId: Id) {
    return this.speedBreakdown()?.buildingUpgrades.find(u => u.typeId === typeId);
  }

  protected upgradeCost(bt: BuildingType): number {
    return this.upgradePreview(bt.id)?.upgradeCost ?? 0;
  }

  /** Baustoffe des nächsten Ausbauschritts (Bedarf + Lagerbestand), vom Backend – sichtbar VOR dem Klick. */
  protected upgradeMaterials(bt: BuildingType): MaterialRequirement[] {
    return this.upgradePreview(bt.id)?.materials ?? [];
  }

  protected upgradeBlockedReason(bt: BuildingType): string | null {
    return this.upgradePreview(bt.id)?.blockedReason ?? null;
  }

  protected upgradeAffordable(bt: BuildingType): boolean {
    return this.upgradePreview(bt.id)?.affordable ?? false;
  }

  /** Bebauungsplätze – DIE strategische Größe der Bebauung (Umsetzungskonzept/17_...md). */
  protected slots() {
    return this.speedBreakdown()?.buildSlots ?? null;
  }

  /** Versorgungsdeckung je Grundkonsumgut – erklärt am Plateau, WARUM das Wachstum stockt. */
  protected coverageEntries(): { productTypeId: Id; coverage: number }[] {
    return Object.entries(this.consumptionCoverage()).map(([productTypeId, coverage]) => ({ productTypeId, coverage }));
  }

  protected growthStateLabel(): string {
    switch (this.speedBreakdown()?.growthState) {
      case 'Shrinking': return 'Schrumpfung';
      case 'Holding': return 'Halten';
      case 'Growing': return 'Wachstum';
      case 'Overcrowded': return 'Überbevölkert';
      case 'FoodLimited': return 'Nahrungsgrenze erreicht';
      default: return '–';
    }
  }

  /** Aktueller Elerium-Dauerverbrauch der Infrastruktur je Spielstunde. */
  protected eleriumPerHour(): number {
    return this.speedBreakdown()?.infrastructureEleriumPerHour ?? 0;
  }

  /**
   * Elerium-Dauerverbrauch der Infrastruktur nach dem nächsten Ausbau
   * (0 bei allen anderen Gebäuden) – Umsetzungskonzept/34_...md, F4/F5.
   */
  protected eleriumAfterUpgrade(bt: BuildingType): number {
    return this.upgradePreview(bt.id)?.eleriumPerHourAfterUpgrade ?? 0;
  }

  /**
   * Wie lange der vorhandene Eleriumvorrat nach dem Ausbau noch reicht, in
   * SPIELSTUNDEN. Die ehrlichste verfügbare Warnung: sie rechnet nicht mit
   * einer geschätzten Nachlieferung, sondern mit dem, was tatsächlich im Lager
   * und im Speicher liegt.
   */
  protected eleriumHoursLeftAfterUpgrade(bt: BuildingType): number {
    const perHour = this.eleriumAfterUpgrade(bt);
    if (perHour <= 0) return 0;
    return (this.speedBreakdown()?.eleriumStock ?? 0) / perHour;
  }

  /** Reicht der Vorrat nach dem Ausbau nicht einmal mehr für die vorgehaltene Reichweite, ist der Blackout absehbar. */
  protected eleriumUpgradeWarning(bt: BuildingType): boolean {
    return this.eleriumAfterUpgrade(bt) > 0
      && this.eleriumHoursLeftAfterUpgrade(bt) < ENERGY_RESERVE_DEFAULT_GAME_HOURS;
  }

  protected upgradeHours(bt: BuildingType): number {
    return this.upgradePreview(bt.id)?.upgradeHours ?? 0;
  }

  /** Produktionstempo einer Produktionsanlage (Industriekomplex/Werft/Ausbildungszentrum) als Prozentsatz – Stufe 1 = 100%, Stufe 4 = 400%. */
  protected productionSpeedPct(typeId: Id): number {
    return this.upgradePreview(typeId)?.productionSpeedPct ?? 0;
  }

  /** Produktionstempo nach dem nächsten Ausbauschritt in Prozent. */
  protected nextProductionSpeedPct(typeId: Id): number {
    return this.upgradePreview(typeId)?.nextProductionSpeedPct ?? 0;
  }

  protected capacityUsagePct(): number {
    const capacity = this.housingCapacity();
    const count = this.population()?.currentCount ?? 0;
    return capacity > 0 ? (count / capacity) * 100 : 0;
  }

  /**
   * Zufriedenheit = derselbe Faktor, der auch das tatsächliche
   * Bevölkerungswachstum steuert (siehe `F.growthConditionFactor`) –
   * 100% entspricht Referenzgeschwindigkeit, darüber/darunter schneller/
   * langsamer.
   */
  protected satisfactionPct(): number {
    return this.speedBreakdown()?.satisfactionPct ?? 0;
  }

  protected readonly productName = (id: Id): string => {
    return this.api.productTypes().find(p => p.id === id)?.name ?? id;
  };

  protected planetTypeLabel(type: PlanetType): string {
    return planetTypeLabel(type);
  }

  protected specLevel(productTypeId: Id): number {
    return this.specializations().find(s => s.productTypeId === productTypeId)?.currentLevel ?? 0;
  }

  /** Tempo-Bonus durch Spezialisierung in % (vom Backend berechnet): 100% = doppelte Geschwindigkeit = nur noch die halbe Zeit. */
  protected specSpeedBonusPct(productTypeId: Id): number {
    return Math.round(this.speedBreakdown()?.specializationSpeedBonusPctByProduct[productTypeId] ?? 0);
  }

  /**
   * Vollständige Aufschlüsselung ALLER Faktoren, die in `computeProductionHours`
   * (Backend) in die Produktionsgeschwindigkeit dieser Kolonie eingehen –
   * bewusst hier client-seitig aus denselben `engine/formulas.ts`-Funktionen
   * nachgerechnet (keine zweite, potenziell abweichende Formel), damit dem
   * Spieler KEIN Faktor verborgen bleibt, der seine strategische Entscheidung
   * (z. B. "erst Bevölkerung wachsen lassen" oder "Industriekomplex ausbauen")
   * beeinflussen könnte. Bevölkerung/Gebäudestufe/Blackout gelten koloniweit
   * für JEDEN Produktionsschritt gleichermaßen; Spezialisierung und
   * Fördergüte sind PRODUKTSPEZIFISCH, siehe `specLevel`/`concentrationFactorFor`.
   */
  protected colonySpeedFactors(): { population: number; availableWorkers: number; industryLevel: number; buildingSpeedFactor: number; blackout: boolean } {
    const b = this.speedBreakdown();
    return {
      population: b?.population ?? 0,
      availableWorkers: b?.availableWorkers ?? 0,
      industryLevel: b?.industryLevel ?? 0,
      buildingSpeedFactor: b?.buildingSpeedFactor ?? 0,
      blackout: b?.blackout ?? false,
    };
  }

  /** Fördergüte-Ausbeutefaktor (nur für Rohstoffe/Tier 0 – bei allem anderen `null`), vom Backend berechnet. */
  protected concentrationFactorFor(productTypeId: Id): number | null {
    return this.speedBreakdown()?.concentrationFactorByProduct[productTypeId] ?? null;
  }

  private async run(key: string, action: () => Promise<unknown>): Promise<void> {
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

  protected applyEnergyReserve(): void {
    const value = this.energyReserveDraft;
    if (value === null || !Number.isFinite(value) || value < 0) {
      this.error.set('Bitte eine Vorhaltemenge von 0 oder mehr eingeben.');
      return;
    }
    void this.run('energy-reserve', () => this.api.setEnergyReserve(this.colonyId, Math.floor(value)));
  }

  protected resetEnergyReserveToAutomatic(): void {
    this.energyReserveDraft = null;
    void this.run('energy-reserve', () => this.api.setEnergyReserve(this.colonyId, null));
  }

  protected upgradeBuilding(typeId: Id): void {
    void this.run(`build:${typeId}`, () => this.api.queueBuilding(this.colonyId, typeId));
  }
  protected cancelBuilding(buildingId: Id): void {
    void this.run(`cancel:${buildingId}`, () => this.api.cancelBuildingOrder(this.colonyId, buildingId));
  }
  protected demolish(buildingId: Id): void {
    void this.run(`demolish:${buildingId}`, () => this.api.demolishBuilding(this.colonyId, buildingId));
  }
  protected activateDefense(buildingId: Id): void {
    void this.run(`activate:${buildingId}`, () => this.api.activateDefense(this.colonyId, buildingId));
  }
  protected deactivateDefense(buildingId: Id): void {
    void this.run(`deactivate:${buildingId}`, () => this.api.deactivateDefense(this.colonyId, buildingId));
  }

  protected submitProduction(): void {
    void this.run('production', () => this.api.queueProduction(
      this.colonyId, this.newProductionProductId, this.newProductionQty,
      this.newProductionAutoMissing, this.newProductionRequeue));
  }
  protected resumeProduction(entryId: Id): void {
    void this.run(`resumeprod:${entryId}`, () => this.api.resumeProduction(this.colonyId, entryId));
  }
  protected cancelProduction(entryId: Id): void {
    void this.run(`cancelprod:${entryId}`, () => this.api.cancelProduction(this.colonyId, entryId));
  }

  protected readonly stockOf = (productTypeId: Id): number => {
    return this.warehouse().find(w => w.productTypeId === productTypeId)?.quantity ?? 0;
  };

  protected openProductPicker(): void {
    this.productPickerOpen.set(true);
  }
  protected onProductPicked(productTypeId: Id): void {
    this.productPickerOpen.set(false);
    this.newProductionProductId = productTypeId;
    this.refreshNewOrderPreview();
  }

  /** "Wird berechnet"-Prognose fürs Neuer-Auftrag-Formular – reine Vorschau, legt keinen Auftrag an (siehe `GameApi.previewProductionChain`). */
  protected refreshNewOrderPreview(): void {
    if (!this.newProductionProductId || this.newProductionQty <= 0) { this.newOrderPreview.set(null); return; }
    const productTypeId = this.newProductionProductId;
    const quantity = this.newProductionQty;
    this.newOrderPreviewLoading.set(true);
    this.api.previewProductionChain(this.colonyId, productTypeId, quantity).then(plan => {
      // Falls Produkt/Menge inzwischen weitergeklickt wurden, dieses veraltete Ergebnis verwerfen.
      if (productTypeId !== this.newProductionProductId || quantity !== this.newProductionQty) return;
      this.newOrderPreview.set(plan);
      this.newOrderPreviewLoading.set(false);
    });
  }

  protected toggleQueueEntry(entry: ProductionQueueEntry): void {
    if (this.expandedQueueEntry() === entry.id) {
      this.expandedQueueEntry.set(null);
      return;
    }
    this.expandedQueueEntry.set(entry.id);
    if (entry.status === 'running') return; // entry.plan ist bereits der verbindliche, beim Start berechnete Plan
    this.queuePreviewLoading.set(entry.id);
    this.api.previewProductionChain(entry.colonyId, entry.productTypeId, entry.quantity).then(plan => {
      this.queuePreview[entry.id] = plan;
      if (this.queuePreviewLoading() === entry.id) this.queuePreviewLoading.set(null);
    });
  }

  /** Für den aufgeklappten Eintrag anzuzeigender Plan: bei laufenden Aufträgen der verbindliche `entry.plan`, sonst die zuletzt berechnete Vorschau. */
  protected planFor(entry: ProductionQueueEntry): ChainPlan {
    return entry.status === 'running' ? entry.plan : (this.queuePreview[entry.id] ?? EMPTY_CHAIN_PLAN);
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

  protected readonly stockSaleDraftQty: Partial<Record<Id, number>> = {};
  protected readonly stockSaleDraftPrice: Partial<Record<Id, number>> = {};
  protected readonly stockSaleDraftAutoRelist: Partial<Record<Id, boolean>> = {};
  protected readonly stockSaleOpen = signal<Id | null>(null);

  protected toggleStockSale(productTypeId: Id): void {
    const opening = this.stockSaleOpen() !== productTypeId;
    this.stockSaleOpen.update(cur => cur === productTypeId ? null : productTypeId);
    // Menge bei JEDEM Öffnen frisch mit dem aktuellen Lagerbestand vorbelegen (nicht nur beim allerersten
    // Mal) – sonst zeigt ein erneutes Öffnen einen längst überholten Bestand von der letzten Anzeige an.
    if (opening) this.stockSaleDraftQty[productTypeId] = Math.floor(this.stockOf(productTypeId));
    if (this.stockSaleDraftPrice[productTypeId] === undefined) this.stockSaleDraftPrice[productTypeId] = 5;
    if (this.stockSaleDraftAutoRelist[productTypeId] === undefined) this.stockSaleDraftAutoRelist[productTypeId] = true;
  }

  protected submitStockSale(productTypeId: Id): void {
    const qty = this.stockSaleDraftQty[productTypeId] ?? 0;
    const price = this.stockSaleDraftPrice[productTypeId] ?? 0;
    const autoRelist = this.stockSaleDraftAutoRelist[productTypeId] ?? true;
    if (qty <= 0 || price <= 0) return;
    void this.run(`stocksale:${productTypeId}`, async () => {
      await this.api.createSellOrder(this.colonyId, productTypeId, qty, price, autoRelist);
      this.stockSaleOpen.set(null);
    });
  }

  protected submitRecruitment(): void {
    void this.run('recruit', () => this.api.queueRecruitment(
      this.colonyId, this.newUnitProductId, this.newUnitQty,
      this.newUnitAutoMissing, this.newUnitRequeue));
  }
  // --- Drohnen ein-/auslagern (Umsetzungskonzept/28_...md) ------------------
  // Drohnen sind Maschinen: im Lager sind sie Ware und damit Frachtgut, in der
  // Garnison sind sie Einheiten und zählen zur Sicherheit. Soldaten machen
  // diesen Weg NICHT mit – sie fahren ausschließlich im Mannschaftstransporter.
  protected readonly droneQty: Partial<Record<Id, number>> = {};

  /** Eingelagerte Drohnen dieser Kolonie – aus dem normalen Warenlager. */
  protected storedDrones(): { productTypeId: Id; quantity: number }[] {
    const droneIds = new Set(this.groundUnitTypes.filter(u => u.id !== 'p_soldier').map(u => u.id));
    return this.warehouse().filter(w => droneIds.has(w.productTypeId) && w.quantity > 0);
  }

  protected submitStoreDrones(unitProductTypeId: Id): void {
    const qty = this.droneQty[unitProductTypeId] ?? 0;
    if (qty <= 0) return;
    void this.run('drones', () => this.api.storeDrones(this.colonyId, unitProductTypeId, qty));
  }
  protected submitDeployDrones(unitProductTypeId: Id): void {
    const qty = this.droneQty[unitProductTypeId] ?? 0;
    if (qty <= 0) return;
    void this.run('drones', () => this.api.deployDrones(this.colonyId, unitProductTypeId, qty));
  }

  protected resumeRecruitment(entryId: Id): void {
    void this.run(`resumerecruit:${entryId}`, () => this.api.resumeRecruitment(this.colonyId, entryId));
  }
  protected cancelRecruitment(entryId: Id): void {
    void this.run(`cancelrecruit:${entryId}`, () => this.api.cancelRecruitment(this.colonyId, entryId));
  }

  protected cancelSellOrder(orderId: Id): void {
    void this.run(`cancelorder:${orderId}`, () => this.api.cancelSellOrder(orderId));
  }

  protected planetName(planetId: Id | null): string {
    if (!planetId) return '—';
    return this.api.planet(planetId)()?.name ?? '—';
  }

  protected buyQtyFor(orderId: Id, max: number): number {
    return Math.min(this.buyDraftQty[orderId] ?? max, max);
  }
  /** Sofortkauf gegen eine Verkaufs-Order: Lieferung ins Lager der eigenen Kolonie auf diesem Planeten, sonst ins Depot am Posten (Backend). */
  protected buyFromOrder(orderId: Id, remaining: number): void {
    const qty = this.buyQtyFor(orderId, remaining);
    if (qty <= 0) return;
    void this.run(`buy:${orderId}`, () => this.api.buyFromOrder(orderId, qty));
  }

  /** Verkauf aus dem Lager DIESER Kolonie gegen eine Kauf-Order am Posten – eine Verkaufs-Order zum Gebotspreis, die sofort kreuzt. */
  protected readonly sellDraftQty: Partial<Record<Id, number>> = {};
  protected sellQtyFor(bid: MarketOrder): number {
    const max = Math.min(bid.remainingQuantity, Math.floor(this.stockOf(bid.productTypeId)));
    return Math.max(0, Math.min(this.sellDraftQty[bid.id] ?? max, max));
  }
  protected sellIntoBid(bid: MarketOrder): void {
    const c = this.colony();
    const qty = this.sellQtyFor(bid);
    if (!c || qty <= 0) return;
    void this.run(`sell:${bid.id}`, () => this.api.createHubSellOrder(c.systemId, bid.productTypeId, qty, bid.limitPrice, c.planetId, false));
  }

  /** Verkauf aus dem eigenen Depot am Posten (nur ohne eigene Kolonie hier). */
  protected readonly depotSellQty: Partial<Record<Id, number>> = {};
  protected readonly depotSellPrice: Partial<Record<Id, number>> = {};
  protected sellFromPostDepot(productTypeId: Id, maxQty: number): void {
    const c = this.colony();
    const qty = Math.min(Math.floor(this.depotSellQty[productTypeId] ?? maxQty), maxQty);
    const price = this.depotSellPrice[productTypeId] ?? 0;
    if (!c || qty < 1 || price <= 0) return;
    void this.run(`sell-depot:${productTypeId}`, () => this.api.createHubSellOrder(c.systemId, productTypeId, qty, price, c.planetId, false));
  }

  /** Kauf-Order am Posten: Credits sofort im Escrow, Lieferung ins Lager der eigenen Kolonie hier bzw. ins Depot. */
  protected newBuyProductId: Id = '';
  protected newBuyQty = 1;
  protected newBuyPrice = 0;
  protected submitBuyOrder(): void {
    const c = this.colony();
    const qty = Math.floor(this.newBuyQty);
    if (!c || !this.newBuyProductId || qty < 1 || this.newBuyPrice <= 0) return;
    void this.run('buy-order', () => this.api.createHubBuyOrder(c.systemId, this.newBuyProductId, qty, this.newBuyPrice, c.planetId));
  }
}
