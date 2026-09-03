import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { GAME_API } from '../../core/sim/game-api.token';
import { Planet, System } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';
import { bfsHops } from '../../core/util/graph';

const ORBIT_ANGLE_STEP_DEG = 52;
const ORBIT_BASE_RADIUS = 28;
const ORBIT_RADIUS_STEP = 24;

@Component({
  selector: 'app-galaxy-map',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './galaxy-map.component.html',
  styleUrl: './galaxy-map.component.scss',
})
export class GalaxyMapComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);
  protected readonly countdown = formatCountdown;

  protected readonly player = this.api.player;
  private readonly homeSystemId = this.api.player()?.homeSystemId ?? '';
  protected readonly homeSystem = this.api.system(this.homeSystemId);
  protected readonly gateway = this.api.gateway(this.homeSystemId);
  protected readonly planets = this.api.planetsInSystem(this.homeSystemId);
  protected readonly weights = this.api.gatewayWeights(this.homeSystemId);
  protected readonly visibleSystems = this.api.visibleSystems();
  protected readonly routes = this.api.galaxyRoutes();

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly selectedSystem = signal<System | null>(null);

  protected select(system: System): void {
    this.selectedSystem.set(system);
  }

  protected systemById(id: string): System | undefined {
    return this.visibleSystems().find(s => s.id === id);
  }

  protected isDirectNeighbor(systemId: string): boolean {
    return (this.gateway()?.reachableSystemIds ?? []).includes(systemId);
  }

  /** BFS-Sprungdistanz vom Heimatsystem – nur über bereits bekannte Routen. */
  protected hopsFromHome(systemId: string): number | null {
    return bfsHops(this.routes(), this.homeSystemId).get(systemId) ?? null;
  }

  protected orbitRadius(planet: Planet): number {
    return ORBIT_BASE_RADIUS + planet.orbitIndex * ORBIT_RADIUS_STEP;
  }

  protected planetX(planet: Planet): number {
    const angle = (planet.orbitIndex * ORBIT_ANGLE_STEP_DEG * Math.PI) / 180;
    return 150 + this.orbitRadius(planet) * Math.cos(angle);
  }

  protected planetY(planet: Planet): number {
    const angle = (planet.orbitIndex * ORBIT_ANGLE_STEP_DEG * Math.PI) / 180;
    return 150 + this.orbitRadius(planet) * Math.sin(angle);
  }

  protected async activateGateway(): Promise<void> {
    this.error.set(null);
    this.busy.set(true);
    try {
      await this.api.activateGateway(this.homeSystemId);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Aktivierung fehlgeschlagen.');
    } finally {
      this.busy.set(false);
    }
  }
}
