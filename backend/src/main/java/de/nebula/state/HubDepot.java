package de.nebula.state;

import de.nebula.model.HubDepotEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Unbegrenztes Depot je Kommandant und Handelsgilde-Station
 * (Umsetzungskonzept/22_...md) – exaktes Gegenstück zu {@link Warehouse},
 * nur mit (systemId, ownerId, productTypeId) statt (colonyId, productTypeId)
 * als Schlüssel, weil Handelsgilde-Stationen keine Kolonie haben.
 */
public final class HubDepot {
  private HubDepot() {
  }

  public static double qty(GameState state, String systemId, String ownerId, String productTypeId) {
    for (HubDepotEntry e : state.hubDepot) {
      if (e.systemId.equals(systemId) && e.ownerId.equals(ownerId) && e.productTypeId.equals(productTypeId)) return e.quantity;
    }
    return 0;
  }

  public static void add(GameState state, String systemId, String ownerId, String productTypeId, double delta) {
    for (HubDepotEntry e : state.hubDepot) {
      if (e.systemId.equals(systemId) && e.ownerId.equals(ownerId) && e.productTypeId.equals(productTypeId)) {
        e.quantity = Math.max(0, e.quantity + delta);
        return;
      }
    }
    if (delta <= 0) return;
    HubDepotEntry e = new HubDepotEntry();
    e.systemId = systemId;
    e.ownerId = ownerId;
    e.productTypeId = productTypeId;
    e.quantity = delta;
    state.hubDepot.add(e);
  }

  /** Alle Positionen mit Bestand &gt; 0 eines Kommandanten an einer Station, für die Depot-Ansicht. */
  public static List<HubDepotEntry> ownedBy(GameState state, String systemId, String ownerId) {
    List<HubDepotEntry> out = new ArrayList<>();
    for (HubDepotEntry e : state.hubDepot) {
      if (e.systemId.equals(systemId) && e.ownerId.equals(ownerId) && e.quantity > 0) out.add(e);
    }
    return out;
  }
}
