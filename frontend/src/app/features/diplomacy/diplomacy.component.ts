import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { GAME_API } from '../../core/sim/game-api.token';

@Component({
  selector: 'app-diplomacy',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './diplomacy.component.html',
  styleUrl: './diplomacy.component.scss',
})
export class DiplomacyComponent {
  private readonly api = inject(GAME_API);
  private readonly homeSystemId = this.api.player()?.homeSystemId ?? '';
  protected readonly gateway = this.api.gateway(this.homeSystemId);
  protected readonly gatewayActive = () => this.gateway()?.state === 'Active';
}
