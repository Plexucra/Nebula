package de.nebula.state;

import de.nebula.model.Fleet;
import de.nebula.model.FleetCargoEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Kleiner Hilfsbaustein für Flottenfracht-Manipulation (Muster aus mehreren
 * TS-Stellen wie {@code createSellOrderFromFleet}/{@code cancelSellOrder}/
 * {@code reserveForRelist}: {@code cargo.map(...).filter(quantity>0)}) –
 * eigenständig statt an das noch nicht portierte Flottensystem (Phase 7)
 * gebunden, damit {@code MarketCommands} den TS-seitigen Flotten-Zweig schon
 * jetzt 1:1 mitführen kann.
 */
public final class FleetCargo {
  private FleetCargo() {
  }

  public static double qty(Fleet fleet, String productTypeId) {
    for (FleetCargoEntry c : fleet.cargo) {
      if (c.productTypeId.equals(productTypeId)) return c.quantity;
    }
    return 0;
  }

  public static void add(Fleet fleet, String productTypeId, double delta) {
    List<FleetCargoEntry> cargo = new ArrayList<>(fleet.cargo);
    for (FleetCargoEntry c : cargo) {
      if (c.productTypeId.equals(productTypeId)) {
        c.quantity += delta;
        break;
      }
    }
    boolean exists = cargo.stream().anyMatch(c -> c.productTypeId.equals(productTypeId));
    if (!exists && delta > 0) {
      FleetCargoEntry entry = new FleetCargoEntry();
      entry.productTypeId = productTypeId;
      entry.quantity = delta;
      cargo.add(entry);
    }
    cargo.removeIf(c -> c.quantity <= 0);
    fleet.cargo = cargo;
  }
}
