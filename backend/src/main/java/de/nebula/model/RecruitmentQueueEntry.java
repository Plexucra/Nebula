package de.nebula.model;

/** Sequentieller Rekrutierungs-Auftrag, siehe {@link ProductionQueueEntry} und Umsetzungskonzept/10_...md. */
public class RecruitmentQueueEntry {
  public String id;
  public String colonyId;
  public String unitProductTypeId;
  public double quantity;
  public boolean autoProduceMissing;
  public boolean requeueOnComplete;
  public ProductionQueueStatus status;
  public Integer stoppedReasonCode;
  public ChainPlan plan;
  public Long startedAt;
  public Long endsAt;
}
