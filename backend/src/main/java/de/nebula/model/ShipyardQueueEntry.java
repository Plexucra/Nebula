package de.nebula.model;

/** Sequentieller Werft-Auftrag, siehe {@link ProductionQueueEntry} und Umsetzungskonzept/10_...md. */
public class ShipyardQueueEntry {
  public String id;
  public String colonyId;
  public String shipProductTypeId;
  public double quantity;
  public boolean autoProduceMissing;
  public boolean requeueOnComplete;
  public ProductionQueueStatus status;
  public Integer stoppedReasonCode;
  public ChainPlan plan;
  public Long startedAt;
  public Long endsAt;
}
