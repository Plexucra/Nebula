import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { PopulationTrend } from '../core/models';

/**
 * Verlaufsgrafik der Bevölkerung einer Kolonie (Umsetzungskonzept/18_...md).
 *
 * Zweck ist die EINORDNUNG, nicht die Linie: der Kommandant soll ablesen
 * können, in welcher Wachstumsphase seine Kolonie steckt und – wenn es
 * stockt – woran es liegt. Die Phasenaussage kommt fertig aus dem Backend
 * (`populationTrend`), hier wird sie nur dargestellt.
 *
 * Bewusste Darstellungsentscheidung: die Wohnkapazität wird NICHT als
 * Referenzlinie gezeichnet. Sie liegt mit 20.000 Plätzen auf Stufe 1 um
 * Größenordnungen über einer jungen Bevölkerung; im selben Maßstab wäre die
 * eigentliche Kurve eine flache Linie am unteren Rand. Stattdessen skaliert
 * die Grafik auf den tatsächlichen Wertebereich des Fensters, und die
 * Kapazität erscheint als Text mit Belegungsanteil.
 */
@Component({
  selector: 'app-population-chart',
  standalone: true,
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './population-chart.component.html',
  styleUrl: './population-chart.component.scss',
})
export class PopulationChartComponent {
  readonly trend = input<PopulationTrend | null>(null);

  private static readonly WIDTH = 560;
  private static readonly HEIGHT = 180;
  private static readonly PAD_LEFT = 52;
  private static readonly PAD = 14;

  protected readonly width = PopulationChartComponent.WIDTH;
  protected readonly height = PopulationChartComponent.HEIGHT;

  private readonly values = computed(() => this.trend()?.samples.map(s => s.population) ?? []);

  protected readonly hasCurve = computed(() => this.values().length >= 2);

  /** Wertebereich der Y-Achse: der tatsächliche Bereich des Fensters, mit etwas Luft. */
  protected readonly bounds = computed(() => {
    const v = this.values();
    if (v.length === 0) return { min: 0, max: 1 };
    const min = Math.min(...v);
    const max = Math.max(...v);
    if (max - min < 1e-9) return { min: Math.max(0, min - 1), max: max + 1 };
    const margin = (max - min) * 0.1;
    return { min: Math.max(0, min - margin), max: max + margin };
  });

  protected readonly path = computed(() => {
    const v = this.values();
    if (v.length < 2) return '';
    const { min, max } = this.bounds();
    const { WIDTH, HEIGHT, PAD, PAD_LEFT } = PopulationChartComponent;
    const step = (WIDTH - PAD_LEFT - PAD) / (v.length - 1);
    return v
      .map((value, i) => {
        const x = PAD_LEFT + i * step;
        const y = PAD + (1 - (value - min) / (max - min)) * (HEIGHT - PAD * 2);
        return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
      })
      .join(' ');
  });

  /** Fläche unter der Kurve – macht die Richtung auf einen Blick lesbar. */
  protected readonly areaPath = computed(() => {
    const p = this.path();
    if (!p) return '';
    const { WIDTH, HEIGHT, PAD, PAD_LEFT } = PopulationChartComponent;
    return `${p} L ${WIDTH - PAD} ${HEIGHT - PAD} L ${PAD_LEFT} ${HEIGHT - PAD} Z`;
  });

  protected readonly axisLabels = computed(() => {
    const { min, max } = this.bounds();
    const { HEIGHT, PAD } = PopulationChartComponent;
    return [
      { value: max, y: PAD + 4 },
      { value: (max + min) / 2, y: HEIGHT / 2 },
      { value: min, y: HEIGHT - PAD },
    ];
  });

  protected readonly current = computed(() => {
    const samples = this.trend()?.samples ?? [];
    return samples.length ? samples[samples.length - 1] : null;
  });

  protected readonly occupancyPct = computed(() => {
    const c = this.current();
    return c && c.housingCapacity > 0 ? (c.population / c.housingCapacity) * 100 : 0;
  });

  /** Zeitfenster in Spieltagen – die Achse ist in Spielzeit beschriftet, nicht in Realzeit. */
  protected readonly windowGameDays = computed(() => (this.trend()?.windowGameHours ?? 0) / 24);

  /** Die Phase in Worten – das eigentliche Ergebnis der Grafik. */
  protected readonly phaseLabel = computed(() => {
    switch (this.trend()?.phase) {
      case 'Accelerating': return 'Beschleunigtes Wachstum';
      case 'Steady': return 'Gleichmäßiges Wachstum';
      case 'Slowing': return 'Wachstum flacht ab';
      case 'Plateau': return 'Plateau erreicht';
      case 'Shrinking': return 'Bevölkerung geht zurück';
      default: return 'Noch zu wenig Verlauf für eine Einschätzung';
    }
  });

  protected readonly phaseTone = computed(() => {
    switch (this.trend()?.phase) {
      case 'Accelerating':
      case 'Steady': return 'good';
      case 'Slowing':
      case 'Plateau': return 'warn';
      case 'Shrinking': return 'bad';
      default: return 'neutral';
    }
  });

  /** Was bremst – nur gesetzt, wenn tatsächlich etwas bremst. */
  protected readonly limitText = computed(() => {
    const t = this.trend();
    if (!t || !t.limitingFactor) return null;
    return t.limitingFactor === 'Housing'
      ? 'Der Wohnraum ist nahezu ausgeschöpft – ein größerer Wohnkomplex schafft wieder Platz zum Wachsen.'
      : 'Die Versorgung hält nicht mit – solange Nahrung und Medizin knapp bleiben, wächst hier niemand mehr. Mehr davon produzieren oder am Markt zukaufen.';
  });

  protected readonly explanation = computed(() => {
    switch (this.trend()?.phase) {
      case 'Accelerating':
        return 'Je mehr Menschen hier leben, desto schneller wächst die Kolonie – solange Versorgung und Wohnraum mitkommen.';
      case 'Steady':
        return 'Die Kolonie wächst in gleichmäßigem Tempo. Vorräte und Wohnraum reichen derzeit aus.';
      case 'Slowing':
        return 'Die Zuwächse werden kleiner – die Kolonie läuft auf eine Grenze zu.';
      case 'Plateau':
        return 'Es kommt praktisch niemand mehr hinzu. Die Kolonie hat den Stand erreicht, den ihre Verhältnisse tragen.';
      case 'Shrinking':
        return 'Es ziehen mehr Menschen fort oder sterben, als hinzukommen.';
      default:
        return 'Sobald ein paar Messpunkte mehr vorliegen, lässt sich sagen, wohin die Kurve läuft.';
    }
  });
}
