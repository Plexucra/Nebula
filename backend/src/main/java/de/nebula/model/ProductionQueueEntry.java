package de.nebula.model;

import java.util.Map;

/**
 * Sequentieller Warteschlangeneintrag: pro Kolonie und Warteschlange
 * (Produktion/Werft/Rekrutierung, siehe {@link ShipyardQueueEntry}/
 * {@link RecruitmentQueueEntry}) ist höchstens ein Eintrag {@code running},
 * der Rest wartet ({@code queued}). Siehe Umsetzungskonzept/10_
 * Sequentielle_Produktionsauftraege_und_Ereignissystem.md.
 */
public class ProductionQueueEntry {
  public String id;
  public String colonyId;
  public String productTypeId;
  public double quantity;
  /**
   * {@code null} im normalen Einzelprodukt-Fall (unverändert: {@link #productTypeId}/
   * {@link #quantity} sind dann maßgeblich). Gesetzt für einen gebündelten Auftrag mit
   * MEHREREN direkt angeforderten Wurzelprodukten, die als EIN Auftrag ausgeführt werden
   * ({@link #productTypeId}/{@link #quantity} spiegeln dann nur das erste davon, für Anzeige
   * und Abwärtskompatibilität). Enthält in diesem Fall ALLE Wurzelprodukte samt Menge,
   * {@link #productTypeId} eingeschlossen. Siehe {@code ChainPlanner.planChain(..., Map, ...)}
   * und {@code ProductionCommands.queueProductionBundle} – der Fix für den Fall, dass ein
   * Bauauftrag mehrere Baustoffe direkt braucht, von denen einer Vorprodukt eines anderen ist
   * (z. B. {@code p_leitermetall} und {@code p_leiterbuendel}): getrennte Einzelaufträge dafür
   * würden sich gegenseitig den Lagerbestand wegnehmen, siehe TODO.md.
   */
  public Map<String, Double> bundledProducts;
  /** Checkbox "Nicht vorhandene Vorprodukte automatisch mitproduzieren". */
  public boolean autoProduceMissing;
  /** Checkbox "Nach Erfolg erneut einreihen". */
  public boolean requeueOnComplete;
  public ProductionQueueStatus status;
  /** Benachrichtigungscode, der zum Stopp geführt hat, nur bei {@code status: stopped}. */
  public Integer stoppedReasonCode;
  public ChainPlan plan;
  public Long startedAt;
  /** Ereignis-Zeitpunkt: einziger Trigger für den Fortschritt. */
  public Long endsAt;
}
