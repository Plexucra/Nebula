package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.ChainPlan;
import de.nebula.model.ChainPlanStep;
import de.nebula.model.Specialization;

/**
 * 1:1-Portierung von {@code registerProduced}/{@code registerProducedChain}
 * aus {@code simulated-game-api.service.ts}. {@code hours} ist die
 * tatsächlich aufgewendete Produktionszeit (Spielstunden), NICHT die
 * produzierte Stückzahl – XP bemisst sich an investierter Zeit (siehe
 * {@code Formulas.specializationThresholdHours}), damit ein aufwendiges
 * Produkt nicht gegenüber einem schnellen Massenprodukt benachteiligt wird.
 */
public final class Specializations {
  private Specializations() {
  }

  public static void registerProduced(GameState state, String colonyId, String productTypeId, double hours) {
    // Soldaten sind laut Mechanik/05_..., §5 nicht spezialisierbar.
    if (productTypeId.equals("p_soldier") || hours <= 0) return;
    state.lastProducedAt.put(colonyId + ":" + productTypeId, Clock.now());

    Specialization existing = null;
    for (Specialization s : state.specializations) {
      if (s.colonyId.equals(colonyId) && s.productTypeId.equals(productTypeId)) {
        existing = s;
        break;
      }
    }
    int level = existing != null ? existing.currentLevel : 0;
    double experience = (existing != null ? existing.experience : 0) + hours;
    double threshold = existing != null ? existing.thresholdForNextLevel : Formulas.specializationThresholdHours(0);
    while (experience >= threshold) {
      level += 1;
      experience -= threshold;
      threshold = Formulas.specializationThresholdHours(level);
    }
    if (existing == null) {
      Specialization s = new Specialization();
      s.colonyId = colonyId;
      s.productTypeId = productTypeId;
      s.currentLevel = level;
      s.experience = experience;
      s.thresholdForNextLevel = threshold;
      state.specializations.add(s);
    } else {
      existing.currentLevel = level;
      existing.experience = experience;
      existing.thresholdForNextLevel = threshold;
    }
  }

  /**
   * Vergibt Spezialisierungs-XP für JEDEN Schritt der Kette, der tatsächlich
   * produziert wurde ({@code step.hours}) – nicht nur fürs Wurzelprodukt.
   * Sonst blieben automatisch mitproduzierte Vorprodukte (mangels
   * Lagerbestand gebaut) für immer auf Stufe 0.
   */
  public static void registerProducedChain(GameState state, String colonyId, ChainPlan plan) {
    for (ChainPlanStep step : plan.steps) registerProduced(state, colonyId, step.productTypeId, step.hours);
  }

  /** Ohne neue Produktion sinkt eine Spezialisierung nach {@code SPECIALIZATION_DECAY_GRACE_MS} um eine Stufe. */
  public static void decaySpecializations(GameState state, long t) {
    for (Specialization s : state.specializations) {
      if (s.currentLevel <= 0) continue;
      String key = s.colonyId + ":" + s.productTypeId;
      long last = state.lastProducedAt.getOrDefault(key, 0L);
      if (t - last > GameConstants.SPECIALIZATION_DECAY_GRACE_MS) {
        state.lastProducedAt.put(key, t);
        s.currentLevel -= 1;
      }
    }
  }
}
