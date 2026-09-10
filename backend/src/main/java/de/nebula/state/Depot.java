package de.nebula.state;

import de.nebula.model.DepotEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Unbegrenztes Depot je Kommandant und Handelsort – Handelsgilde-Station
 * ({@code planetId == null}, Umsetzungskonzept/22_...md) oder Planetarer
 * Handelsposten ({@code planetId} gesetzt, Umsetzungskonzept/37_...md).
 * Exaktes Gegenstück zu {@link Warehouse}, nur mit (systemId, planetId,
 * ownerId, productTypeId) statt (colonyId, productTypeId) als Schlüssel.
 */
public final class Depot {
  private Depot() {
  }

  static boolean at(DepotEntry e, String systemId, String planetId) {
    return e.systemId.equals(systemId) && Objects.equals(e.planetId, planetId);
  }

  public static double qty(GameState state, String systemId, String planetId, String ownerId, String productTypeId) {
    for (DepotEntry e : state.depot) {
      if (at(e, systemId, planetId) && e.ownerId.equals(ownerId) && e.productTypeId.equals(productTypeId)) return e.quantity;
    }
    return 0;
  }

  public static void add(GameState state, String systemId, String planetId, String ownerId, String productTypeId, double delta) {
    boolean found = false;
    for (DepotEntry e : state.depot) {
      if (at(e, systemId, planetId) && e.ownerId.equals(ownerId) && e.productTypeId.equals(productTypeId)) {
        e.quantity = Math.max(0, e.quantity + delta);
        found = true;
        break;
      }
    }
    if (delta <= 0) return;
    if (!found) {
      DepotEntry e = new DepotEntry();
      e.systemId = systemId;
      e.planetId = planetId;
      e.ownerId = ownerId;
      e.productTypeId = productTypeId;
      e.quantity = delta;
      state.depot.add(e);
    }
    // Ein Zugang weckt schlafende Dauerorders, die aus diesem Depot gespeist sind.
    MarketCommands.replenishDormantDepotOrders(state, systemId, planetId, ownerId, productTypeId);
  }

  /** Alle Positionen mit Bestand &gt; 0 eines Kommandanten an einem Ort, für die Depot-Ansicht. */
  public static List<DepotEntry> ownedBy(GameState state, String systemId, String planetId, String ownerId) {
    List<DepotEntry> out = new ArrayList<>();
    for (DepotEntry e : state.depot) {
      if (at(e, systemId, planetId) && e.ownerId.equals(ownerId) && e.quantity > 0) out.add(e);
    }
    return out;
  }
}
