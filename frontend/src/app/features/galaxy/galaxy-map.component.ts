import {
  AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, HostListener, ViewChild, computed, inject, signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { GAME_API } from '../../core/sim/game-api.token';
import { Colony, Fleet, Id, System } from '../../core/models';
import { bfsHops } from '../../core/util/graph';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';

interface ViewBox { x: number; y: number; w: number; h: number; }

type SystemColorClass = 'mine' | 'colony' | 'enemy' | null;

interface SystemMarker {
  myShips: number;
  enemyShips: number;
  colorClass: SystemColorClass;
}

const EMPTY_MARKER: SystemMarker = { myShips: 0, enemyShips: 0, colorClass: null };

/** Ausgangs-Kantenlänge des Ausschnitts (im 0..100-Galaxiekoordinatenraum), zentriert auf das Heimatsystem. */
const DEFAULT_VIEW_SIZE = 55;
const MIN_VIEW_SIZE = 6;
const MAX_VIEW_SIZE = 140;
/** Unterhalb dieser Ausschnittsbreite werden Systemnamen eingeblendet – bei 200 Systemen wären sie sonst dauerhaft unlesbar überlappt. */
const NAME_LABEL_ZOOM_THRESHOLD = 42;
/** Gesamtbewegung eines Zeigers (Pixel) unterhalb dieser Schwelle zählt noch als Klick, nicht als Ziehen. */
const CLICK_DRAG_THRESHOLD_PX = 6;
const WHEEL_ZOOM_STEP = 1.15;

@Component({
  selector: 'app-galaxy-map',
  standalone: true,
  imports: [RouterLink, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './galaxy-map.component.html',
  styleUrl: './galaxy-map.component.scss',
})
export class GalaxyMapComponent implements AfterViewInit {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);
  protected readonly countdown = formatCountdown;
  protected readonly nodeRadius = 0.95;

  @ViewChild('svgEl', { static: true }) private readonly svgRef!: ElementRef<SVGSVGElement>;

  protected readonly player = this.api.player;
  private readonly homeSystemId = this.api.player()?.homeSystemId ?? '';
  protected readonly homeSystem = this.api.system(this.homeSystemId);

  protected readonly systems = this.api.visibleSystems();
  protected readonly routes = this.api.galaxyRoutes();
  protected readonly allPlayers = this.api.players();
  protected readonly npcsAll = this.api.npcs();
  protected readonly myColonies = this.api.colonies();
  protected readonly myFleets = this.api.fleets();
  protected readonly allFleets = this.api.allFleets();

  private readonly systemsById = computed(() => new Map(this.systems().map(s => [s.id, s])));
  private readonly hopsFromHomeMap = computed(() => bfsHops(this.routes(), this.homeSystemId));

  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /** Vorberechnete Kanten in Bildschirm-/Galaxiekoordinaten (0..100) – hängt nur von Topologie/Systemen ab, nicht vom Kartenausschnitt. */
  protected readonly renderRoutes = computed(() => {
    const byId = this.systemsById();
    const out: { key: string; x1: number; y1: number; x2: number; y2: number }[] = [];
    for (const r of this.routes()) {
      const a = byId.get(r.a);
      const b = byId.get(r.b);
      if (!a || !b) continue;
      out.push({ key: r.a + '_' + r.b, x1: a.x * 100, y1: a.y * 100, x2: b.x * 100, y2: b.y * 100 });
    }
    return out;
  });

  /**
   * Marker je System: eigene Flotten (Farbe 1) < eigene Kolonie (Farbe 2, überschreibt) <
   * feindliche Flotten (Farbe 3/rot, überschreibt) – feindliche Präsenz nur für bereits
   * besuchte Systeme sichtbar (Fog of War, siehe `hasVisitedSystem`), eigene Flotten/Kolonien
   * immer, da man die ja selbst kennt.
   */
  protected readonly markers = computed(() => {
    const myId = this.api.player()?.id ?? null;
    const otherPlayerIds = new Set(this.allPlayers().map(p => p.id).filter(id => id !== myId));
    const myColonySystemIds = new Set(this.myColonies().map(c => c.systemId));

    const myShipsBySystem = new Map<Id, number>();
    for (const f of this.myFleets()) {
      if (f.status !== 'Stationed') continue;
      const ships = f.ships.reduce((sum, g) => sum + g.quantity, 0);
      myShipsBySystem.set(f.systemId, (myShipsBySystem.get(f.systemId) ?? 0) + ships);
    }

    const enemyShipsBySystem = new Map<Id, number>();
    for (const f of this.allFleets()) {
      if (f.status !== 'Stationed' || f.ownerId === myId || !otherPlayerIds.has(f.ownerId)) continue;
      const ships = f.ships.reduce((sum, g) => sum + g.quantity, 0);
      enemyShipsBySystem.set(f.systemId, (enemyShipsBySystem.get(f.systemId) ?? 0) + ships);
    }

    const map = new Map<Id, SystemMarker>();
    for (const sys of this.systems()) {
      const myShips = myShipsBySystem.get(sys.id) ?? 0;
      const visited = this.api.hasVisitedSystem(sys.id)();
      const enemyShips = visited ? (enemyShipsBySystem.get(sys.id) ?? 0) : 0;
      const hasColony = myColonySystemIds.has(sys.id);
      let colorClass: SystemColorClass = null;
      if (myShips > 0) colorClass = 'mine';
      if (hasColony) colorClass = 'colony';
      if (enemyShips > 0) colorClass = 'enemy';
      map.set(sys.id, { myShips, enemyShips, colorClass });
    }
    return map;
  });

  protected markerFor(systemId: Id): SystemMarker {
    return this.markers().get(systemId) ?? EMPTY_MARKER;
  }

  // ==========================================================================
  // Kartenausschnitt: Pan (Ziehen) + Zoom (Scrollrad / Pinch)
  // ==========================================================================

  protected readonly viewBox = signal<ViewBox>(this.initialViewBox());
  protected readonly showLabels = computed(() => this.viewBox().w < NAME_LABEL_ZOOM_THRESHOLD);
  protected readonly isDragging = signal(false);

  private initialViewBox(): ViewBox {
    const home = this.homeSystem();
    const cx = (home?.x ?? 0.5) * 100;
    const cy = (home?.y ?? 0.5) * 100;
    return { x: cx - DEFAULT_VIEW_SIZE / 2, y: cy - DEFAULT_VIEW_SIZE / 2, w: DEFAULT_VIEW_SIZE, h: DEFAULT_VIEW_SIZE };
  }

  protected viewBoxAttr(): string {
    const b = this.viewBox();
    return `${b.x} ${b.y} ${b.w} ${b.h}`;
  }

  ngAfterViewInit(): void {
    this.syncAspectToContainer();
  }

  @HostListener('window:resize')
  protected onWindowResize(): void {
    this.syncAspectToContainer();
  }

  /** Passt die Ausschnitts-Höhe einmalig an das tatsächliche Seitenverhältnis des Containers an, damit kein Letterboxing entsteht. */
  private syncAspectToContainer(): void {
    const rect = this.svgRef.nativeElement.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;
    const box = this.viewBox();
    const newH = box.w * (rect.height / rect.width);
    if (Math.abs(newH - box.h) < 0.01) return;
    const centerY = box.y + box.h / 2;
    this.viewBox.set({ ...box, h: newH, y: centerY - newH / 2 });
  }

  /** Verschiebt den Ausschnitt so, dass `system` mittig liegt – Zoomstufe bleibt unverändert. */
  private centerOn(system: System): void {
    const box = this.viewBox();
    this.viewBox.set({ ...box, x: system.x * 100 - box.w / 2, y: system.y * 100 - box.h / 2 });
  }

  private readonly activePointers = new Map<number, { x: number; y: number }>();
  private pinchLastDist: number | null = null;
  private gestureDragDistance = 0;
  private pendingBox: ViewBox | null = null;
  private rafScheduled = false;

  protected onPointerDown(ev: PointerEvent): void {
    (ev.target as Element).setPointerCapture?.(ev.pointerId);
    if (this.activePointers.size === 0) this.gestureDragDistance = 0;
    this.activePointers.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
    this.isDragging.set(true);
    if (this.activePointers.size === 2) this.pinchLastDist = this.currentPinchDistance();
  }

  protected onPointerMove(ev: PointerEvent): void {
    const prev = this.activePointers.get(ev.pointerId);
    if (!prev) return;
    const dx = ev.clientX - prev.x;
    const dy = ev.clientY - prev.y;
    this.activePointers.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
    this.gestureDragDistance += Math.hypot(dx, dy);

    if (this.activePointers.size >= 2) {
      const dist = this.currentPinchDistance();
      const mid = this.currentPinchMidpoint();
      if (this.pinchLastDist && dist) this.zoomAtClientPoint(this.pinchLastDist / dist, mid.x, mid.y);
      this.pinchLastDist = dist;
    } else if (this.activePointers.size === 1) {
      this.panByPixels(dx, dy);
    }
  }

  protected onPointerUp(ev: PointerEvent): void {
    this.activePointers.delete(ev.pointerId);
    if (this.activePointers.size < 2) this.pinchLastDist = null;
    if (this.activePointers.size === 0) this.isDragging.set(false);
  }

  protected onWheel(ev: WheelEvent): void {
    ev.preventDefault();
    const factor = ev.deltaY > 0 ? WHEEL_ZOOM_STEP : 1 / WHEEL_ZOOM_STEP;
    this.zoomAtClientPoint(factor, ev.clientX, ev.clientY);
  }

  private currentPinchDistance(): number {
    const pts = [...this.activePointers.values()];
    return pts.length < 2 ? 0 : Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y);
  }
  private currentPinchMidpoint(): { x: number; y: number } {
    const pts = [...this.activePointers.values()];
    return { x: (pts[0].x + pts[1].x) / 2, y: (pts[0].y + pts[1].y) / 2 };
  }

  private panByPixels(dxPx: number, dyPx: number): void {
    const rect = this.svgRef.nativeElement.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;
    const box = this.pendingBox ?? this.viewBox();
    const sx = box.w / rect.width;
    const sy = box.h / rect.height;
    this.pendingBox = { ...box, x: box.x - dxPx * sx, y: box.y - dyPx * sy };
    this.scheduleFlush();
  }

  private zoomAtClientPoint(factor: number, clientX: number, clientY: number): void {
    const rect = this.svgRef.nativeElement.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;
    const box = this.pendingBox ?? this.viewBox();
    const px = (clientX - rect.left) / rect.width;
    const py = (clientY - rect.top) / rect.height;
    const focusX = box.x + px * box.w;
    const focusY = box.y + py * box.h;
    const newW = Math.min(MAX_VIEW_SIZE, Math.max(MIN_VIEW_SIZE, box.w * factor));
    const actualFactor = newW / box.w;
    const newH = box.h * actualFactor;
    this.pendingBox = { x: focusX - px * newW, y: focusY - py * newH, w: newW, h: newH };
    this.scheduleFlush();
  }

  private scheduleFlush(): void {
    if (this.rafScheduled) return;
    this.rafScheduled = true;
    requestAnimationFrame(() => {
      this.rafScheduled = false;
      if (this.pendingBox) {
        this.viewBox.set(this.pendingBox);
        this.pendingBox = null;
      }
    });
  }

  // ==========================================================================
  // Auswahl / Detailpanel
  // ==========================================================================

  protected readonly selectedSystem = signal<System | null>(null);

  /** Klick auf einen System-Marker: wählt ihn für das Detailpanel aus – im Bewegen-Modus (siehe unten) zusätzlich als Flugziel. */
  protected onSystemClick(system: System): void {
    if (this.gestureDragDistance > CLICK_DRAG_THRESHOLD_PX) return;
    this.selectedSystem.set(system);
    if (this.moveModeFleetId()) this.moveTargetSystemId.set(system.id);
  }

  /** Wählt `system` aus und zentriert die Karte darauf – für die "Zum System"-Buttons in der Flottenliste. */
  protected focusSystem(system: System): void {
    this.selectedSystem.set(system);
    this.centerOn(system);
  }

  protected hasVisited(systemId: Id): boolean {
    return this.api.hasVisitedSystem(systemId)();
  }

  protected coloniesInSelectedSystem(): Colony[] {
    const s = this.selectedSystem();
    return s ? this.api.coloniesInSystem(s.id)() : [];
  }

  protected ownerDisplay(ownerId: Id): string {
    return this.allPlayers().find(p => p.id === ownerId)?.name
      ?? this.npcsAll().find(n => n.id === ownerId)?.name
      ?? 'Unbekannt';
  }

  protected hopsFromHome(systemId: Id): number | null {
    return this.hopsFromHomeMap().get(systemId) ?? null;
  }

  protected selectedGateway(): { reachableCount: number } | null {
    const s = this.selectedSystem();
    if (!s) return null;
    const gw = this.api.gateway(s.id)();
    return gw ? { reachableCount: gw.reachableSystemIds.length } : null;
  }

  protected selectedWeights() {
    const s = this.selectedSystem();
    return s ? this.api.gatewayWeights(s.id)() : [];
  }

  // ==========================================================================
  // Meine Flotten (rechte Leiste) + Bewegen-Modus
  // ==========================================================================

  protected fleetShipCount(fleet: Fleet): number {
    return fleet.ships.reduce((sum, g) => sum + g.quantity, 0);
  }

  protected fleetSystem(fleet: Fleet): System | undefined {
    return this.systemsById().get(fleet.systemId);
  }

  /** Öffnet das Detailpanel des Systems, in dem sich `fleet` gerade befindet, und zentriert die Karte darauf. */
  protected focusFleetSystem(fleet: Fleet): void {
    const sys = this.fleetSystem(fleet);
    if (sys) this.focusSystem(sys);
  }

  protected readonly moveModeFleetId = signal<Id | null>(null);
  protected readonly moveTargetSystemId = signal<Id | null>(null);

  protected moveModeFleetName(): string {
    return this.myFleets().find(f => f.id === this.moveModeFleetId())?.name ?? '';
  }

  protected startMove(fleet: Fleet): void {
    this.error.set(null);
    this.moveModeFleetId.set(fleet.id);
    this.moveTargetSystemId.set(null);
  }

  protected cancelMove(): void {
    this.moveModeFleetId.set(null);
    this.moveTargetSystemId.set(null);
  }

  protected movePreview(): { hops: number; ms: number } | null {
    const fleetId = this.moveModeFleetId();
    const targetId = this.moveTargetSystemId();
    if (!fleetId || !targetId) return null;
    return this.api.routePreview(fleetId, targetId)();
  }

  protected async confirmMove(): Promise<void> {
    const fleetId = this.moveModeFleetId();
    const targetId = this.moveTargetSystemId();
    if (!fleetId || !targetId) return;
    this.error.set(null);
    this.busy.set('move');
    try {
      await this.api.moveFleet(fleetId, targetId);
      this.cancelMove();
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Anflug fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }

  protected systemName(systemId: Id): string {
    return this.systemsById().get(systemId)?.name ?? '—';
  }

  /** Verbleibende Route einer unterwegs befindlichen Flotte als Namensliste – erster Eintrag ist der gerade laufende Sprung. */
  protected fleetRouteNames(fleet: Fleet): string[] {
    const names: string[] = [];
    if (fleet.destinationSystemId) names.push(this.systemName(fleet.destinationSystemId));
    for (const id of fleet.pendingHops) names.push(this.systemName(id));
    return names;
  }

  /** Bricht einen laufenden Flug jederzeit ab – der aktuelle Sprung wird noch zu Ende geflogen, siehe `GameApi.cancelFleetMove`. */
  protected async cancelFlight(fleet: Fleet): Promise<void> {
    this.error.set(null);
    this.busy.set('cancelFlight:' + fleet.id);
    try {
      await this.api.cancelFleetMove(fleet.id);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Abbruch fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }

  // ==========================================================================
  // Landen im Detailpanel: eigene, im gewählten System stationierte (nicht gelandete) Flotten
  // ==========================================================================

  protected eligibleLandingFleets(): Fleet[] {
    const s = this.selectedSystem();
    if (!s) return [];
    return this.myFleets().filter(f => f.status === 'Stationed' && f.locationType === 'System' && f.systemId === s.id);
  }

  protected landingFleetId: Id | null = null;

  protected async landAt(colonyId: Id): Promise<void> {
    const fleets = this.eligibleLandingFleets();
    const fleetId = this.landingFleetId ?? fleets[0]?.id;
    if (!fleetId) return;
    this.error.set(null);
    this.busy.set('land:' + colonyId);
    try {
      await this.api.landFleet(fleetId, colonyId);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Landung fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }
}
