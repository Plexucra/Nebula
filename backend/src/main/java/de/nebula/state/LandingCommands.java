package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.data.ShipCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.engine.Rng;
import de.nebula.model.Building;
import de.nebula.model.Colony;
import de.nebula.model.DefenseActivationState;
import de.nebula.model.DiplomaticStatus;
import de.nebula.model.Fleet;
import de.nebula.model.FleetCargoEntry;
import de.nebula.model.FleetLocationType;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.GroundForceUnitStack;
import de.nebula.model.NotificationType;
import de.nebula.model.Player;
import de.nebula.model.ProductCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Landung (Umsetzungskonzept/04_Bodentruppen_und_Landung.md,
 * Mechanik/05_Bodentruppen_und_Bodenkrieg.md §7-8): Soldaten (an Bord) und
 * Drohnen (als Fracht) einer im Orbit eines Planeten liegenden eigenen Flotte
 * kommen auf die Planetenoberfläche – von dort per {@link #moveGroundForces}
 * weiter in eine eigene Kolonie auf demselben Planeten.
 *
 * <p>Bewusst NICHT Teil dieser Klasse: das Bodengefecht selbst – Angriff auf
 * fremde Kolonien, Rückzug, Niederlage, Eroberung (Mechanik/05_...md §2,
 * §10-12). Das steht in {@link GroundBattleCommands} und {@link ColonyConquest}.
 * Landungsabwehr (§8) gehört dagegen hierher: sie feuert schon beim bloßen
 * Landeversuch, unabhängig vom Bodengefecht.</p>
 */
public final class LandingCommands {
  private LandingCommands() {
  }


  /** Wie {@link #land(GameState, IdGenerator, String, String, String, Rng)}, mit echtem Zufall. */
  public static GroundForceGroup land(GameState state, IdGenerator ids, String playerId, String fleetId, String targetPlanetId) {
    return land(state, ids, playerId, fleetId, targetPlanetId, () -> Math.random());
  }

  /**
   * Wie oben, mit injizierbarem Zufallsgenerator (Tests: {@link Rng#seeded}) für die
   * Landungsabwehr. Läuft zuerst jede feindliche, kriegführende Kolonie mit aktiver
   * Verteidigung auf diesem Planeten ab (Mechanik/05_...md §8) – was das kostet, fehlt
   * entsprechend in der entstehenden Boden-Gruppe.
   */
  public static GroundForceGroup land(GameState state, IdGenerator ids, String playerId, String fleetId,
                                       String targetPlanetId, Rng rng) {
    Fleet fleet = FleetCommands.requireOwnFleet(state, playerId, fleetId);
    if (fleet.locationType != FleetLocationType.PlanetOrbit || !targetPlanetId.equals(fleet.locationPlanetId)) {
      throw new CommandException("Die Flotte muss dafür im Orbit dieses Planeten stehen.");
    }
    if (!hasLandableCargo(state, fleet)) {
      throw new CommandException("Diese Flotte hat weder Soldaten an Bord noch Drohnen geladen.");
    }
    // Eine Blockade sperrt auch die Landung (Umsetzungskonzept/34_...md, §J 7).
    // Der Fall tritt ein, wenn sich die Blockade erst gebildet hat, NACHDEM die
    // Flotte in den Orbit eingeflogen ist – wer landen will, muss sie zuerst
    // brechen, statt an ihr vorbei abzusetzen.
    var blocking = BlockadeCommands.orbitBlockadeAgainst(state, playerId, targetPlanetId);
    if (blocking != null) {
      BlockadeCommands.breakThroughOrbitBlockade(state, ids, playerId, fleetId, blocking);
      return state.groundForceGroups.stream()
          .filter(g -> playerId.equals(g.ownerId) && targetPlanetId.equals(g.planetId))
          .findFirst().orElse(null);
    }

    resolveLandingDefense(state, ids, playerId, fleet, targetPlanetId, rng);

    // Nach der Landungsabwehr neu abfragen: die Flotte kann inzwischen komplett
    // vernichtet sein (letzter Transporter verloren, siehe FleetCommands.consumeShips).
    fleet = state.fleets.stream().filter(f -> f.id.equals(fleetId)).findFirst().orElse(null);
    GroundForceGroup aboard = fleet == null ? null : TroopTransportCommands.embarkedForces(state, fleetId);
    double soldiers = aboard == null ? 0 : TroopTransportCommands.count(aboard, GameConstants.SOLDIER_PRODUCT_ID);
    List<FleetCargoEntry> droneCargo = fleet == null ? List.of() : droneCargoOf(fleet);

    GroundForceGroup surface = state.groundForceGroups.stream()
        .filter(g -> playerId.equals(g.ownerId) && targetPlanetId.equals(g.planetId))
        .findFirst().orElse(null);
    // Bei Totalverlust (Landungsabwehr hat alles vernichtet) bleibt nichts zu landen übrig –
    // dann keine leere Geister-Gruppe anlegen, sonst genau der Fehler aus TODO.md ("leere
    // Geisterflotte"), nur für Bodentruppen.
    if (soldiers <= 0 && droneCargo.isEmpty()) return surface;
    if (surface == null) {
      surface = new GroundForceGroup();
      surface.id = ids.next("gfg");
      surface.ownerId = playerId;
      surface.planetId = targetPlanetId;
      surface.units = new ArrayList<>();
      state.groundForceGroups.add(surface);
    }
    if (soldiers > 0) {
      TroopTransportCommands.remove(aboard, GameConstants.SOLDIER_PRODUCT_ID, soldiers);
      if (TroopTransportCommands.isEmpty(aboard)) state.groundForceGroups.remove(aboard);
      TroopTransportCommands.add(surface, GameConstants.SOLDIER_PRODUCT_ID, soldiers);
    }
    for (FleetCargoEntry c : droneCargo) {
      TroopTransportCommands.add(surface, c.productTypeId, c.quantity);
      FleetCargo.add(fleet, c.productTypeId, -c.quantity);
    }
    // Soldaten und Drohnen kommen beide als Reserve an Land (an Bord gab es
    // nichts zu kommandieren). Erst hier finden sie zueinander – ohne diesen
    // Aufruf stünde ein frisch gelandeter Verband mit null aktiven Drohnen da
    // und wäre nach Mechanik/05_..., §10 sofort kampfunfähig.
    RecruitmentCommands.recalcCrewing(surface);
    return surface;
  }

  private static boolean hasLandableCargo(GameState state, Fleet fleet) {
    GroundForceGroup aboard = TroopTransportCommands.embarkedForces(state, fleet.id);
    double soldiers = aboard == null ? 0 : TroopTransportCommands.count(aboard, GameConstants.SOLDIER_PRODUCT_ID);
    return soldiers > 0 || !droneCargoOf(fleet).isEmpty();
  }

  private static List<FleetCargoEntry> droneCargoOf(Fleet fleet) {
    List<FleetCargoEntry> result = new ArrayList<>();
    for (FleetCargoEntry c : fleet.cargo) {
      if (c.quantity > 0 && ProductCatalog.find(c.productTypeId).category == ProductCategory.GroundUnit) result.add(c);
    }
    return result;
  }

  /**
   * Mechanik/05_...md §8: einmalig, synchron, PRO landender Flotte gegen jede feindliche
   * Kolonie einzeln (Reihenfolge spielerisch irrelevant) – nicht periodisch, kein Tick-Job.
   * Restliche Transporter zählen jeweils für die nächste Kolonie weiter.
   */
  private static void resolveLandingDefense(GameState state, IdGenerator ids, String playerId, Fleet fleet,
                                             String targetPlanetId, Rng rng) {
    for (Colony colony : ColonyCommands.coloniesOnPlanet(state, targetPlanetId)) {
      if (!state.fleets.contains(fleet)) return; // Flotte bereits vollständig vernichtet
      if (DiplomacyCommands.diplomaticStatus(state, playerId, colony.ownerId) != DiplomaticStatus.War) continue;
      Building defense = activeDefenseBuilding(state, colony.id);
      if (defense == null) continue;

      int transportersBefore = transporterCount(fleet);
      if (transportersBefore <= 0) continue;

      double capacity = defense.level * Formulas.LANDING_DEFENSE_CAPACITY_PER_LEVEL;
      double actual = capacity * (0.5 + 0.5 * rng.next());
      int destroyed = (int) Math.min(Math.floor(actual), transportersBefore);
      if (destroyed <= 0) continue;

      destroyTransporters(state, fleet, destroyed);
      double lostFraction = (double) destroyed / transportersBefore;
      applyProportionalLosses(state, fleet, lostFraction);

      Player attacker = GameQueries.requirePlayer(state, playerId);
      Notifications.notifyPlayer(state, ids, NotificationType.Warnung, Notifications.CODE_LANDING_INTERCEPTED,
          "Landungsabwehr von \"" + colony.name + "\" hat " + destroyed + " Ihrer Transporter abgeschossen.",
          attacker.id, null);
      Notifications.notify(state, ids, NotificationType.Warnung, Notifications.CODE_LANDING_INTERCEPTED,
          attacker.name + " versucht bei \"" + colony.name + "\" zu landen – " + destroyed + " Transporter abgeschossen.",
          colony.id, null);
    }
  }

  private static Building activeDefenseBuilding(GameState state, String colonyId) {
    for (Building b : state.buildings) {
      if (b.colonyId.equals(colonyId) && b.typeId.equals(GameConstants.PLANETARY_DEFENSE_BUILDING_ID)
          && b.activationState == DefenseActivationState.Active) {
        return b;
      }
    }
    return null;
  }

  private static int transporterCount(Fleet fleet) {
    int total = 0;
    for (FleetShipGroup g : fleet.ships) {
      if (g.quantity > 0 && ShipCatalog.find(g.shipProductTypeId).troopCapacity > 0) total += (int) g.quantity;
    }
    return total;
  }

  private static void destroyTransporters(GameState state, Fleet fleet, int destroyed) {
    int remaining = destroyed;
    for (FleetShipGroup g : new ArrayList<>(fleet.ships)) {
      if (remaining <= 0) break;
      if (g.quantity <= 0 || ShipCatalog.find(g.shipProductTypeId).troopCapacity <= 0) continue;
      int fromGroup = (int) Math.min(g.quantity, remaining);
      FleetCommands.consumeShips(state, fleet, g.shipProductTypeId, fromGroup);
      remaining -= fromGroup;
    }
  }

  /**
   * Anteiliger, aufgerundeter Verlust der Ladung (Soldaten an Bord, Drohnenfracht) –
   * Mechanik/05_...md §8: {@code ceil(vorhandene Menge × verlorene / gesamte Transporter)}.
   * Bei Totalverlust (lostFraction = 1) ergibt das automatisch den kompletten Bestand.
   */
  private static void applyProportionalLosses(GameState state, Fleet fleet, double lostFraction) {
    GroundForceGroup aboard = TroopTransportCommands.embarkedForces(state, fleet.id);
    if (aboard != null) {
      double soldiers = TroopTransportCommands.count(aboard, GameConstants.SOLDIER_PRODUCT_ID);
      double lostSoldiers = Math.ceil(soldiers * lostFraction);
      if (lostSoldiers > 0) {
        TroopTransportCommands.remove(aboard, GameConstants.SOLDIER_PRODUCT_ID, lostSoldiers);
        if (TroopTransportCommands.isEmpty(aboard)) state.groundForceGroups.remove(aboard);
      }
    }
    for (FleetCargoEntry c : droneCargoOf(fleet)) {
      double lost = Math.ceil(c.quantity * lostFraction);
      if (lost > 0) FleetCargo.add(fleet, c.productTypeId, -lost);
    }
  }

  /**
   * Verlegt einen gelandeten Verband in eine eigene Kolonie auf demselben Planeten – genau
   * ein Kampftick Dauer, unabhängig von der Distanz (Mechanik/05_...md §7). Ausführung erst in
   * {@link #processGroundForceMovements}.
   */
  public static void moveGroundForces(GameState state, String playerId, String groupId, String targetColonyId) {
    GroundForceGroup group = state.groundForceGroups.stream().filter(g -> g.id.equals(groupId)).findFirst().orElse(null);
    if (group == null || !playerId.equals(group.ownerId)) throw new CommandException("Unbekannter Bodentruppenverband.");
    if (group.planetId == null) throw new CommandException("Der Verband muss auf einer Planetenoberfläche stehen.");
    if (group.pendingMoveColonyId != null) throw new CommandException("Der Verband ist bereits auf dem Weg.");
    if (GroundBattleCommands.activeBattleForGroup(state, groupId) != null) {
      throw new CommandException("Der Verband steht im Gefecht – erst zurückziehen, dann verlegen.");
    }

    Colony target = ColonyCommands.colony(state, targetColonyId);
    if (target == null || !playerId.equals(target.ownerId) || !group.planetId.equals(target.planetId)) {
      throw new CommandException("Die Zielkolonie muss eine eigene Kolonie auf demselben Planeten sein.");
    }

    group.pendingMoveColonyId = targetColonyId;
    group.moveCompletesAt = Clock.now() + (long) Clock.hoursToMs(Formulas.COMBAT_TICK_HOURS);
  }

  /** Aufgerufen aus {@code GameTick}: schließt fällige Verlegungen ab und löst die Boden-Gruppe dabei auf. */
  public static void processGroundForceMovements(GameState state, IdGenerator ids, long t) {
    List<GroundForceGroup> due = state.groundForceGroups.stream()
        .filter(g -> g.moveCompletesAt != null && g.moveCompletesAt <= t)
        .toList();
    for (GroundForceGroup group : due) {
      String targetColonyId = group.pendingMoveColonyId;
      for (GroundForceUnitStack u : group.units) {
        int total = u.activeCount + u.reserveCount;
        if (total <= 0) continue;
        if (GameConstants.SOLDIER_PRODUCT_ID.equals(u.unitProductTypeId)) {
          RecruitmentCommands.addSoldiersToGarrison(state, ids, targetColonyId, total);
        } else {
          RecruitmentCommands.addDronesToGarrison(state, ids, targetColonyId, u.unitProductTypeId, total);
        }
      }
      state.groundForceGroups.remove(group);
    }
  }
}
