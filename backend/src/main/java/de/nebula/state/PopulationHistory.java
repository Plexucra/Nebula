package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.model.Colony;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import de.nebula.model.PopulationSample;
import de.nebula.model.PopulationTrend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bevölkerungsverlauf je Kolonie als Ringpuffer plus die Einordnung, in
 * welcher Wachstumsphase eine Kolonie steckt (Umsetzungskonzept/18_...md).
 *
 * <p>Aufgezeichnet wird im Takt der Universums-Statistik
 * ({@code STATS_SNAPSHOT_INTERVAL_GAME_HOURS} = 4 Spielstunden) mit
 * {@link #MAX_SAMPLES} Einträgen – das ergibt ein Fenster von 480
 * Spielstunden (20 Spieltagen), bei Tempo 1 also 20 Realminuten. Ältere
 * Messpunkte fallen hinten heraus: der Verlauf soll die aktuelle Phase
 * zeigen, nicht die Kolonialgeschichte.</p>
 */
public final class PopulationHistory {
  private PopulationHistory() {
  }

  /** 120 Messpunkte × 4 Spielstunden = 20 Spieltage Fenster – genug für eine Phasenaussage, klein genug für den Speicher. */
  public static final int MAX_SAMPLES = 120;

  /** Ab dieser Belegung gilt der Wohnraum als der begrenzende Faktor. */
  private static final double HOUSING_TIGHT_RATIO = 0.85;
  /** Unterhalb dieser Deckung gilt die Versorgung als bremsend. */
  private static final double SUPPLY_SHORT_COVERAGE = 0.95;
  /** Relativer Zuwachs je Messpunkt, unterhalb dessen von "kein nennenswerter Zuwachs" gesprochen wird. */
  private static final double PLATEAU_RELATIVE_DELTA = 0.002;

  /** Hängt einen Messpunkt je Kolonie an; wird im selben Takt wie die Universums-Statistik aufgerufen. */
  public static void record(GameState state, long t) {
    for (Colony colony : state.colonies) {
      Population population = null;
      for (Population p : state.populations) if (p.colonyId.equals(colony.id)) population = p;
      PlanetStats stats = null;
      for (PlanetStats s : state.planetStats) if (s.colonyId.equals(colony.id)) stats = s;
      if (population == null || stats == null) continue;

      double capacity = PowerGrid.effectiveHousingCapacity(state, colony.id);
      PopulationSample sample = new PopulationSample();
      sample.at = t;
      sample.population = population.currentCount;
      sample.standardOfLivingPct = stats.standardOfLivingPct;
      sample.housingCapacity = capacity;
      sample.growthState = Economy.growthState(state, colony); // samt Staffeldeckel (GoodsLimited)

      List<PopulationSample> history = state.populationHistory.computeIfAbsent(colony.id, id -> new ArrayList<>());
      synchronized (history) {
        history.add(sample);
        while (history.size() > MAX_SAMPLES) history.remove(0);
      }
    }
  }

  /** Verlauf samt Phasen-Einordnung für eine Kolonie. */
  public static PopulationTrend trend(GameState state, String colonyId) {
    List<PopulationSample> history = state.populationHistory.getOrDefault(colonyId, List.of());
    List<PopulationSample> samples;
    synchronized (history) {
      samples = List.copyOf(history);
    }

    PopulationTrend result = new PopulationTrend();
    result.samples = samples;
    result.windowGameHours = samples.size() < 2 ? 0
        : Clock.msToHours(samples.get(samples.size() - 1).at - samples.get(0).at);

    if (samples.size() < 4) {
      result.phase = PopulationTrend.Phase.TooFewSamples;
      return result;
    }

    PopulationSample latest = samples.get(samples.size() - 1);
    // Zuwachs je Messpunkt in der jüngeren gegen die ältere Hälfte des Fensters –
    // daraus ergibt sich, ob die Kurve steiler oder flacher wird.
    int mid = samples.size() / 2;
    double olderDelta = perSampleDelta(samples.subList(0, mid + 1));
    double youngerDelta = perSampleDelta(samples.subList(mid, samples.size()));
    double reference = Math.max(latest.population, 1);

    if (latest.growthState == de.nebula.model.PopulationGrowthState.Shrinking
        || youngerDelta < -reference * PLATEAU_RELATIVE_DELTA) {
      result.phase = PopulationTrend.Phase.Shrinking;
    } else if (Math.abs(youngerDelta) <= reference * PLATEAU_RELATIVE_DELTA) {
      result.phase = PopulationTrend.Phase.Plateau;
    } else if (youngerDelta > olderDelta * 1.15) {
      result.phase = PopulationTrend.Phase.Accelerating;
    } else if (youngerDelta < olderDelta * 0.85) {
      result.phase = PopulationTrend.Phase.Slowing;
    } else {
      result.phase = PopulationTrend.Phase.Steady;
    }

    // Bremst etwas? Wohnraum und Versorgung sind die beiden möglichen Ursachen.
    if (result.phase == PopulationTrend.Phase.Slowing || result.phase == PopulationTrend.Phase.Plateau
        || result.phase == PopulationTrend.Phase.Shrinking) {
      if (latest.housingCapacity > 0 && latest.population / latest.housingCapacity >= HOUSING_TIGHT_RATIO) {
        result.limitingFactor = PopulationTrend.LimitingFactor.Housing;
      } else {
        Map<String, Double> coverage = state.consumptionCoverage.getOrDefault(colonyId, Map.of());
        // Die Deckung führt nur die aktuell nachgefragten Güter (Güterstaffel,
        // Umsetzungskonzept/38); ein Gut, das die Stufe nicht verlangt, fehlt nicht.
        boolean short_ = coverage.isEmpty();
        for (double c : coverage.values()) {
          if (c < SUPPLY_SHORT_COVERAGE) short_ = true;
        }
        if (short_) result.limitingFactor = PopulationTrend.LimitingFactor.Supply;
      }
    }
    return result;
  }

  private static double perSampleDelta(List<PopulationSample> part) {
    if (part.size() < 2) return 0;
    return (part.get(part.size() - 1).population - part.get(0).population) / (part.size() - 1);
  }
}
