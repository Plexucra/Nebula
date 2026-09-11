package de.nebula.model;

/** Sequentieller Werft-Auftrag, siehe {@link ProductionQueueEntry} und Umsetzungskonzept/10_...md. */
public class ShipyardQueueEntry {
  public String id;
  public String colonyId;
  public String shipProductTypeId;
  public double quantity;
  /**
   * Kein {@code autoProduceMissing} mehr: die Werft montiert nur, was im Lager liegt;
   * fehlende Vorprodukte reiht {@code ShipyardCommands.queueMissingShipInputs} in die
   * Produktionswarteschlange ein.
   */
  public boolean requeueOnComplete;
  public ProductionQueueStatus status;
  public Integer stoppedReasonCode;
  public ChainPlan plan;
  public Long startedAt;
  public Long endsAt;
}
