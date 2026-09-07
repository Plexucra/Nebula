import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { GAME_API } from '../../core/sim/game-api.token';
import { Blockade, Colony, Fleet, FleetSystemTarget, Id, PlanetType } from '../../core/models';
import { UiClockService, formatCountdown } from '../../core/ui/ui-clock.service';
import { planetTypeLabel } from '../../core/ui/planet-type-labels';

/** Eine Auswahlmöglichkeit im "Ziel"-Dropdown je Flotte, siehe `destinationOptions`. */
interface SystemLocationOption {
  label: string;
  target: FleetSystemTarget;
}

/**
 * Systemansicht: zeigt ALLE Himmelskörper eines Systems (auch unbesiedelte
 * Planeten, die auf der Galaxiekarte bisher gar nicht auftauchten) sowie den
 * Systemhandelsposten, und erlaubt eigenen, dort bereits `Stationed`en
 * Flotten die instante Bewegung zwischen diesen drei Ortsarten
 * (`GameApi.moveFleetWithinSystem`, siehe `FleetLocationType`) – Grundlage
 * für ein künftiges Landungs-/Invasionssystem (noch nicht umgesetzt: eine
 * Flotte im Orbit eines fremden, besiedelten Planeten kann hier zwar
 * positioniert, aber noch nicht zum Landungsangriff eingesetzt werden).
 */
@Component({
  selector: 'app-system-view',
  standalone: true,
  imports: [RouterLink, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './system-view.component.html',
  styleUrl: './system-view.component.scss',
})
export class SystemViewComponent {
  protected readonly api = inject(GAME_API);
  protected readonly clock = inject(UiClockService);
  protected readonly countdown = formatCountdown;
  private readonly route = inject(ActivatedRoute);

  protected readonly systemId: Id = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly system = this.api.system(this.systemId);
  protected readonly visited = this.api.hasVisitedSystem(this.systemId);
  protected readonly explored = this.api.hasExploredSystem(this.systemId);
  protected readonly planets = this.api.planetsInSystem(this.systemId);
  protected readonly colonies = this.api.coloniesInSystem(this.systemId);
  protected readonly allFleets = this.api.allFleets();
  protected readonly players = this.api.players();
  protected readonly blockades = this.api.blockadesInSystem(this.systemId);

  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private myId(): Id | undefined {
    return this.api.player()?.id;
  }

  protected colonyForPlanet(planetId: Id): Colony | undefined {
    return this.colonies().find(c => c.planetId === planetId);
  }

  protected ownerDisplay(ownerId: Id): string {
    return this.players().find(p => p.id === ownerId)?.name ?? 'Unbekannt';
  }

  protected fleetShipCount(fleet: Fleet): number {
    return fleet.ships.reduce((sum, g) => sum + g.quantity, 0);
  }

  protected planetTypeLabel(type: PlanetType): string {
    return planetTypeLabel(type);
  }

  private matchesTarget(f: Fleet, target: FleetSystemTarget): boolean {
    if (f.systemId !== this.systemId || f.status !== 'Stationed') return false;
    if (target.kind === 'System') return f.locationType === 'System';
    if (target.kind === 'PlanetOrbit') return f.locationType === 'PlanetOrbit' && f.locationPlanetId === target.planetId;
    return f.locationType === 'ColonyOrbit' && f.locationColonyId === target.colonyId;
  }

  /** ALLE (eigene + sichtbare fremde) stationierten Flotten an einem bestimmten Ort dieses Systems. */
  protected fleetsAt(target: FleetSystemTarget): Fleet[] {
    return this.allFleets().filter(f => this.matchesTarget(f, target));
  }

  /** Fasst Kolonie-Orbit UND bloßen Planeten-Orbit desselben Planeten zusammen – aus Flottensicht praktisch derselbe Ort. */
  protected fleetsAtPlanet(planetId: Id): Fleet[] {
    const colony = this.colonyForPlanet(planetId);
    const atOrbit = this.fleetsAt({ kind: 'PlanetOrbit', planetId });
    const atColony = colony ? this.fleetsAt({ kind: 'ColonyOrbit', colonyId: colony.id }) : [];
    return [...atOrbit, ...atColony];
  }

  /** Eigene, in DIESEM System bereits stationierte Flotten – Kandidaten für eine Bewegung zwischen den Orten des Systems. */
  protected myStationedFleetsHere(): Fleet[] {
    const myId = this.myId();
    return this.allFleets().filter(f => f.ownerId === myId && f.systemId === this.systemId && f.status === 'Stationed');
  }

  /**
   * Eigene, gerade unterwegs befindliche Flotten, die dieses System noch
   * nicht verlassen haben – ein Gateway-Sprung ist konzeptionell instant,
   * `Fleet.systemId` bleibt bis zur Ankunft das Ausgangssystem (siehe
   * `GalaxyMapComponent.markers`-Doku), die Flotte steht also bis dahin noch
   * sichtbar hier.
   */
  protected myInTransitFleetsHere(): Fleet[] {
    const myId = this.myId();
    return this.allFleets().filter(f => f.ownerId === myId && f.systemId === this.systemId && f.status === 'InTransit');
  }

  protected locationLabel(f: Fleet): string {
    if (f.locationType === 'System') return 'Systemhandelsposten';
    if (f.locationType === 'ColonyOrbit') return 'Kolonie ' + (this.colonies().find(c => c.id === f.locationColonyId)?.name ?? '—');
    return 'Orbit ' + (this.planets().find(p => p.id === f.locationPlanetId)?.name ?? '—');
  }

  /** Mögliche Bewegungsziele für `fleet` innerhalb des Systems – der aktuelle Ort wird ausgelassen. */
  protected destinationOptions(f: Fleet): SystemLocationOption[] {
    const opts: SystemLocationOption[] = [];
    if (f.locationType !== 'System') opts.push({ label: 'Systemhandelsposten', target: { kind: 'System' } });
    for (const p of this.planets()) {
      const colony = this.colonyForPlanet(p.id);
      if (colony) {
        if (f.locationType === 'ColonyOrbit' && f.locationColonyId === colony.id) continue;
        opts.push({ label: `Kolonie ${colony.name}`, target: { kind: 'ColonyOrbit', colonyId: colony.id } });
      } else {
        if (f.locationType === 'PlanetOrbit' && f.locationPlanetId === p.id) continue;
        opts.push({ label: `Orbit ${p.name} (unbesiedelt)`, target: { kind: 'PlanetOrbit', planetId: p.id } });
      }
    }
    return opts;
  }

  protected readonly destinationChoice: Partial<Record<Id, SystemLocationOption | null>> = {};

  protected async moveFleet(fleet: Fleet): Promise<void> {
    const choice = this.destinationChoice[fleet.id];
    if (!choice) return;
    this.error.set(null);
    this.busy.set('move:' + fleet.id);
    try {
      await this.api.moveFleetWithinSystem(fleet.id, choice.target);
      this.destinationChoice[fleet.id] = null;
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Bewegung fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }

  /** Erforschen ist mit jeder eigenen, HIER stationierten Flotte möglich – unabhängig vom Schiffstyp. */
  protected canExplore(fleet: Fleet): boolean {
    return !this.explored() && fleet.systemId === this.systemId && fleet.status === 'Stationed';
  }

  protected async exploreSystem(fleet: Fleet): Promise<void> {
    this.error.set(null);
    this.busy.set('explore:' + fleet.id);
    try {
      await this.api.exploreSystem(fleet.id);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Erforschen fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }

  // --- Blockaden (siehe `Blockade`, `GameApi.formBlockade`/`liftBlockade`) ---

  protected gatewayBlockade(): Blockade | undefined {
    return this.blockades().find(b => b.anchorKind === 'Gateway');
  }

  protected planetBlockade(planetId: Id): Blockade | undefined {
    return this.blockades().find(b => b.anchorKind === 'PlanetOrbit' && b.planetId === planetId);
  }

  protected blockadeForFleet(fleetId: Id): Blockade | undefined {
    return this.blockades().find(b => b.fleetId === fleetId);
  }

  protected canFormGatewayBlockade(fleet: Fleet): boolean {
    return fleet.locationType === 'System' && !this.blockadeForFleet(fleet.id) && !this.gatewayBlockade();
  }

  protected canFormPlanetBlockade(fleet: Fleet): boolean {
    if (fleet.locationType !== 'PlanetOrbit' && fleet.locationType !== 'ColonyOrbit') return false;
    if (this.blockadeForFleet(fleet.id)) return false;
    return !this.planetBlockade(fleet.locationPlanetId!);
  }

  protected async formGatewayBlockade(fleet: Fleet): Promise<void> {
    this.error.set(null);
    this.busy.set('blockade:' + fleet.id);
    try {
      await this.api.formBlockade(fleet.id, { kind: 'Gateway' });
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Blockade fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }

  protected async formPlanetBlockade(fleet: Fleet): Promise<void> {
    if (!fleet.locationPlanetId) return;
    this.error.set(null);
    this.busy.set('blockade:' + fleet.id);
    try {
      await this.api.formBlockade(fleet.id, { kind: 'PlanetOrbit', planetId: fleet.locationPlanetId });
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Blockade fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }

  protected async liftBlockade(blockade: Blockade): Promise<void> {
    this.error.set(null);
    this.busy.set('unblockade:' + blockade.id);
    try {
      await this.api.liftBlockade(blockade.id);
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : 'Aufheben fehlgeschlagen.');
    } finally {
      this.busy.set(null);
    }
  }
}
