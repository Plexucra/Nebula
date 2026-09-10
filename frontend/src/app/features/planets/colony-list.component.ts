import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { GAME_API } from '../../core/sim/game-api.token';
import { Colonization, Colony, Id, PlanetType } from '../../core/models';
import { planetTypeLabel } from '../../core/ui/planet-type-labels';
import { UiClockService } from '../../core/ui/ui-clock.service';
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
  private readonly clock = inject(UiClockService);
  protected readonly colonies = this.api.colonies();
  /** Reaktiv: `player()` ist beim echten Backend erst nach der ersten Server-Antwort gesetzt (siehe TradeOverviewComponent). */
  protected readonly homeSystemId = computed(() => this.api.player()?.homeSystemId ?? '');
  protected readonly planetsInSystem = computed(() => this.api.planetsInSystem(this.homeSystemId())());
  private readonly systems = this.api.visibleSystems();
  /** Name statt hartkodiertem „Aurelia" – das Heimatsystem heißt bei jedem Kommandanten anders. */
  protected readonly homeSystemName = computed(() =>
    this.systems().find(s => s.id === this.homeSystemId())?.name ?? 'Heimatsystem');

  protected readonly colonizations = this.api.colonizations();
  protected readonly colonizationHours = COLONIZATION_GAME_HOURS;
  protected readonly startPopulation = START_POPULATION;

  protected readonly busyPlanetId = signal<Id | null>(null);
  protected readonly error = signal<string | null>(null);

  protected stats(colonyId: string) { return this.api.colonyStats(colonyId); }
  protected population(colonyId: string) { return this.api.population(colonyId); }
  protected powerCoverage(colonyId: string) { return this.api.powerCoverage(colonyId); }

  /**
   * Zustand der Kolonie in einem Wort – die Karte zeigte vorher nur vier
   * unbeschriftete Balken. Wer mehrere Kolonien hat, sah damit NICHT, dass eine
   * davon im Blackout steckt und ihre Bevölkerung verliert.
   */
  protected colonyStatus(colonyId: string): { label: string; kind: 'bad' | 'warn' | 'good' } | null {
    if (this.powerCoverage(colonyId)() < 0.999) return { label: 'Energieausfall', kind: 'bad' };
    const growth = this.population(colonyId)()?.growthRatePerInterval ?? 0;
    if (growth < -0.001) return { label: 'Bevölkerung schrumpft', kind: 'bad' };
    if (growth > 0.001) return { label: 'wächst', kind: 'good' };
    return { label: 'hält', kind: 'warn' };
  }
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
    // Spieluhr, nicht Date.now(): `endsAt` ist Serverzeit (siehe UiClockService).
    return Math.max(0, Math.ceil((running.endsAt - this.clock.now()) / 60000));
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
