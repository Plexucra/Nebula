package de.nebula.model;

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
