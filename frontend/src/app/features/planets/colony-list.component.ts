import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { Colonization, Colony, Id, PlanetType } from '../../core/models';
import { planetTypeLabel } from '../../core/ui/planet-type-labels';
import { COLONIZATION_GAME_HOURS, START_POPULATION } from '../../core/shared-constants';

@Component({
  selector: 'app-colony-list',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './colony-list.component.html',
  styleUrl: './colony-list.component.scss',
})
export class ColonyListComponent {
  protected readonly api = inject(GAME_API);
  protected readonly colonies = this.api.colonies();
  /** Reaktiv: `player()` ist beim echten Backend erst nach der ersten Server-Antwort gesetzt (siehe TradeOverviewComponent). */
  protected readonly homeSystemId = computed(() => this.api.player()?.homeSystemId ?? '');
  protected readonly planetsInSystem = computed(() => this.api.planetsInSystem(this.homeSystemId())());

  protected readonly colonizations = this.api.colonizations();
  protected readonly colonizationHours = COLONIZATION_GAME_HOURS;
  protected readonly startPopulation = START_POPULATION;

  protected readonly busyPlanetId = signal<Id | null>(null);
  protected readonly error = signal<string | null>(null);

  protected stats(colonyId: string) { return this.api.colonyStats(colonyId); }
  protected population(colonyId: string) { return this.api.population(colonyId); }
  protected planetName(colony: Colony): string {
    return this.api.planet(colony.planetId)()?.name ?? '—';
  }

  protected planetTypeLabel(type: PlanetType): string {
    return planetTypeLabel(type);
  }

  protected uncolonizedPlanets() {
    const colonizedIds = new Set(this.colonies().map(c => c.planetId));
    return this.planetsInSystem().filter(p => p.usable && !colonizedIds.has(p.id));
  }

  /** Laufende eigene Gründung auf diesem Planeten, falls es eine gibt. */
  protected colonizationOf(planetId: Id): Colonization | undefined {
    return this.colonizations().find(c => c.planetId === planetId);
  }

  protected remainingMinutes(running: Colonization): number {
    return Math.max(0, Math.ceil((running.endsAt - Date.now()) / 60000));
  }

  protected async colonize(planetId: Id): Promise<void> {
    this.error.set(null);
    this.busyPlanetId.set(planetId);
    try {
      await this.api.colonizePlanet(planetId);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Kolonisierung fehlgeschlagen.');
    } finally {
      this.busyPlanetId.set(null);
    }
  }
}
