package de.nebula.model;

import java.util.List;

public class Gateway {
  public String id;
  public String systemId;
  public GatewayDiscoveryState state;
  public Long discoveredAt;
  public Long activatedAt;
  public Long activatingCompletesAt;
  public List<String> reachableSystemIds;
}
