import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { sparklineGeometry, trendDirection } from '../core/util/sparkline';

export type TileTone = 'accent' | 'good' | 'warn' | 'danger';

/**
 * Kompakte Kennzahl-Kachel mit Trendlinie (Sparkline), für die
 * Statistiken-Ansicht. Bewusst eine einzelne Kennzahl pro Kachel statt
 * eines mehrfarbigen Sammel-Charts – siehe dataviz-Leitfaden: eine
 * einzelne Größe über die Zeit ist meist besser als Stat-Kachel mit
 * Sparkline aufgehoben als in einem überladenen Mehrserien-Diagramm.
 */
@Component({
  selector: 'app-sparkline-tile',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './sparkline-tile.component.html',
  styleUrl: './sparkline-tile.component.scss',
})
export class SparklineTileComponent {
  title = input.required<string>();
  value = input.required<string>();
  unit = input<string>('');
  series = input<number[]>([]);
  tone = input<TileTone>('accent');
  hint = input<string>('');

  protected readonly geometry = computed(() => sparklineGeometry(this.series()));
  protected readonly trend = computed(() => trendDirection(this.series()));
}
