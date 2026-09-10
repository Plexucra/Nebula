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
    long now = Clock.now();
    String key = colonyId + ":" + productTypeId;
    state.lastProducedAt.put(key, now);
    // Jede Produktion schiebt den Verfall um die Gnadenfrist hinaus – als EIN
    // Ereignis je Kolonie und Produkt, das die vorige Planung ersetzt.
    GameEvents.schedule(state, GameEventType.SPECIALIZATION_DECAY, key, now + graceMs());

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

  private static long graceMs() {
    return (long) Clock.hoursToMs(GameConstants.SPECIALIZATION_DECAY_GRACE_GAME_HOURS);
  }

  /**
   * Ereignis {@code SPECIALIZATION_DECAY}: ohne neue Produktion sinkt die
   * Spezialisierung nach {@code SPECIALIZATION_DECAY_GRACE_GAME_HOURS} um eine
   * Stufe, und die nächste Frist beginnt. Das Ziel ist {@code colonyId:productTypeId}.
   * Veraltet, wenn seither produziert wurde (dann liegt die letzte Produktion
   * nicht mehr genau eine Gnadenfrist zurück) oder die Spezialisierung mit
   * ihrer Kolonie verschwunden ist.
   */
  static void decay(GameState state, String key, long at) {
    Long last = state.lastProducedAt.get(key);
    if (last == null || last + graceMs() != at) return;
    int sep = key.indexOf(':');
    String colonyId = key.substring(0, sep);
    String productTypeId = key.substring(sep + 1);
    for (Specialization s : state.specializations) {
      if (!s.colonyId.equals(colonyId) || !s.productTypeId.equals(productTypeId)) continue;
      if (s.currentLevel <= 0) return;
      s.currentLevel -= 1;
      state.lastProducedAt.put(key, at);
      if (s.currentLevel > 0) GameEvents.schedule(state, GameEventType.SPECIALIZATION_DECAY, key, at + graceMs());
      return;
    }
  }
}
