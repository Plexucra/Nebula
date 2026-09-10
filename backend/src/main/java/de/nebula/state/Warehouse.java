package de.nebula.state;

import de.nebula.model.WarehouseEntry;

/**
 * 1:1-Portierung von {@code warehouseQty}/{@code addToWarehouse} aus
 * {@code simulated-game-api.service.ts}. Die TS-Version pflegt zusätzlich
 * einen {@code warehouseIndex} (`Map`) für O(1)-Lookups bei vielen
 * gleichzeitig aktiven Aufträgen (Performance-Fix aus einer früheren
 * Session) – im sequentiellen Warteschlangenmodell (max. 1 aktiver Auftrag
 * je Kolonie) ist die lineare Suche über {@code state.warehouse} für die
 * Größenordnung dieses Prototyps unproblematisch, der Index wird deshalb
 * bewusst NICHT mitportiert (YAGNI, bis ein echtes Performance-Problem
 * auftritt).
 */
public final class Warehouse {
  private Warehouse() {
  }

  public static double qty(GameState state, String colonyId, String productTypeId) {
    for (WarehouseEntry w : state.warehouse) {
      if (w.colonyId.equals(colonyId) && w.productTypeId.equals(productTypeId)) return w.quantity;
    }
    return 0;
  }

  /**
   * Bucht {@code delta} auf das Lager. Zufluss von Stabilisiertem Elerium füllt
   * zuerst den Energiespeicher der Kolonie bis zur Vorhaltemenge
   * ({@link EnergyStorageCommands#intake}) – gleich, ob er aus Produktion,
   * Entladung, Zukauf oder Eroberung stammt (Umsetzungskonzept/32_...md).
   */
  public static void add(GameState state, String colonyId, String productTypeId, double delta) {
    if (delta > 0 && de.nebula.engine.GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID.equals(productTypeId)) {
      delta = EnergyStorageCommands.intake(state, colonyId, delta);
      if (delta <= 0) return;
    }
    addRaw(state, colonyId, productTypeId, delta);
  }

  /**
   * Lagerbuchung OHNE Umweg über den Energiespeicher – für Entnahmen und für
   * Überschuss, der aus dem Speicher zurückfließt. Ein ZUGANG weckt schlafende
   * Auto-Relist-Orders dieses Produkts ({@code MarketCommands}): das ist die
   * Reaktion, die früher jeder Tick für alle Orders der Galaxie nachprüfte.
   * Der Relist entnimmt dem Lager wieder (negatives Delta), also keine Rekursion.
   */
  static void addRaw(GameState state, String colonyId, String productTypeId, double delta) {
    boolean found = false;
    for (WarehouseEntry w : state.warehouse) {
      if (w.colonyId.equals(colonyId) && w.productTypeId.equals(productTypeId)) {
        w.quantity = Math.max(0, w.quantity + delta);
        found = true;
        break;
      }
    }
    if (delta <= 0) return;
    if (!found) {
      WarehouseEntry w = new WarehouseEntry();
      w.colonyId = colonyId;
      w.productTypeId = productTypeId;
      w.quantity = delta;
      state.warehouse.add(w);
    }
    MarketCommands.replenishDormantSellOrders(state, colonyId, productTypeId);
  }
}
