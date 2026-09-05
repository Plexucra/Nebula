package de.nebula.state;

import de.nebula.data.BuildingCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.model.Building;
import de.nebula.model.BuildingCategory;
import de.nebula.model.BuildingType;
import de.nebula.model.Colony;
import de.nebula.model.DefenseActivationState;
import de.nebula.model.PendingBuildingOrder;
import de.nebula.model.TransactionReason;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;

import java.util.List;

/**
 * 1:1-Portierung der "Bebauung"-Sektion aus {@code simulated-game-api.service.ts}
 * (Umsetzungskonzept/13_...md, Phase 5). Die Anwendung eines fertigen
 * Ausbauauftrags (TS {@code processBuildingCompletions}, tickgetrieben) ist
 * hier bewusst noch NICHT enthalten – das ist Teil des noch zu portierenden
 * Tick-Loops; bis dahin bleibt ein {@code pendingOrder} nach Ablauf seiner
 * Zeit ehrlich "noch nicht abgeschlossen" stehen, statt eine unvollständige
 * Ersatzlogik vorzutäuschen.
 */
public final class BuildingCommands {
  private BuildingCommands() {
  }

  public static List<Building> buildingsForColony(GameState state, String colonyId) {
    return state.buildings.stream().filter(b -> b.colonyId.equals(colonyId)).toList();
  }

  /** Bebauungspunkte-Überbelegungsfaktor für einen Himmelskörper (über alle seine Kolonien hinweg), siehe {@code Formulas.overbuildFactor}. */
  public static double overbuildFactor(GameState state, String planetId) {
    var planet = state.planets.stream().filter(p -> p.id.equals(planetId)).findFirst().orElse(null);
    if (planet == null) return 1;
    var colonyIds = state.colonies.stream().filter(c -> c.planetId.equals(planetId)).map(c -> c.id).toList();
    double used = 0;
    for (Building b : state.buildings) {
      if (colonyIds.contains(b.colonyId)) {
        used += Formulas.buildPointsUsed(BuildingCatalog.find(b.typeId).buildPointsPerLevel, b.level);
      }
    }
    return Formulas.overbuildFactor(used, planet.buildCapacity);
  }

  public static void queueBuilding(GameState state, IdGenerator ids, String playerId, String colonyId, String buildingTypeId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    queueBuildingCore(state, ids, colonyId, buildingTypeId);
  }

  /** Ungeprüfter Kern von {@link #queueBuilding} – für eine künftige NPC-KI gedacht (siehe TS-Original), die für ihre eigenen Kolonien keinen "eingeloggten Kommandanten" hat. */
  public static void queueBuildingCore(GameState state, IdGenerator ids, String colonyId, String buildingTypeId) {
    String colonyOwnerId = GameQueries.requireColonyOwner(state, colonyId);
    BuildingType type = BuildingCatalog.find(buildingTypeId);
    Building building = state.buildings.stream()
        .filter(b -> b.colonyId.equals(colonyId) && b.typeId.equals(buildingTypeId)).findFirst().orElse(null);
    int fromLevel = building != null ? building.level : 0;
    if (fromLevel >= type.maxLevel) throw new CommandException(type.name + " hat bereits die Höchststufe erreicht.");
    if (building != null && building.pendingOrder != null) throw new CommandException(type.name + " wird bereits ausgebaut.");

    double cost = Formulas.buildingUpgradeCost(type.baseCostPerLevel, fromLevel);
    double hours = Formulas.buildingUpgradeHours(type.baseHoursPerLevel, fromLevel);
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, colonyOwnerId);
    if (wallet == null || wallet.balance < cost) throw new CommandException("Nicht genug Credits für diesen Ausbau.");

    long t = Clock.now();
    PendingBuildingOrder pendingOrder = new PendingBuildingOrder();
    pendingOrder.targetLevel = fromLevel + 1;
    pendingOrder.startedAt = t;
    pendingOrder.completesAt = t + (long) Clock.hoursToMs(hours);

    if (building != null) {
      building.pendingOrder = pendingOrder;
    } else {
      building = new Building();
      building.id = ids.next("bld");
      building.colonyId = colonyId;
      building.typeId = buildingTypeId;
      building.level = 0;
      building.pendingOrder = pendingOrder;
      building.activationState = type.category == BuildingCategory.PlanetaryDefense ? DefenseActivationState.Inactive : null;
      building.activationCompletesAt = null;
      state.buildings.add(building);
    }
    Ledger.recordTx(state, ids, wallet.id, GameQueries.popWalletIdForColony(state, colonyId), cost,
        TransactionReason.Construction, "Ausbau " + type.name + " → Stufe " + (fromLevel + 1));
  }

  public static void cancelBuildingOrder(GameState state, IdGenerator ids, String playerId, String colonyId, String buildingId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    Building building = state.buildings.stream()
        .filter(b -> b.id.equals(buildingId) && b.colonyId.equals(colonyId)).findFirst().orElse(null);
    if (building == null || building.pendingOrder == null) throw new CommandException("Kein laufender Ausbauauftrag.");
    BuildingType type = BuildingCatalog.find(building.typeId);
    double refund = Formulas.buildingUpgradeCost(type.baseCostPerLevel, building.level);
    building.pendingOrder = null;
    var player = GameQueries.requirePlayer(state, playerId);
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, player.id);
    if (wallet != null) {
      Ledger.recordTx(state, ids, GameQueries.popWalletIdForColony(state, colonyId), wallet.id, refund,
          TransactionReason.Construction, "Abbruch Ausbau " + type.name);
    }
  }

  public static void demolishBuilding(GameState state, IdGenerator ids, String playerId, String colonyId, String buildingId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    Building building = state.buildings.stream()
        .filter(b -> b.id.equals(buildingId) && b.colonyId.equals(colonyId)).findFirst().orElse(null);
    if (building == null || building.level <= 0) throw new CommandException("Kein rückbaubares Gebäude.");
    if (building.pendingOrder != null) throw new CommandException("Während eines laufenden Ausbaus kann nicht rückgebaut werden.");
    BuildingType type = BuildingCatalog.find(building.typeId);
    double refund = Math.round(Formulas.buildingUpgradeCost(type.baseCostPerLevel, building.level - 1) * 0.5);
    building.level -= 1;
    var player = GameQueries.requirePlayer(state, playerId);
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, player.id);
    if (wallet != null) {
      Ledger.recordTx(state, ids, GameQueries.popWalletIdForColony(state, colonyId), wallet.id, refund,
          TransactionReason.Construction, "Rückbau " + type.name);
    }
  }

  public static void activateDefense(GameState state, String playerId, String colonyId, String buildingId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    Building building = state.buildings.stream()
        .filter(b -> b.id.equals(buildingId) && b.colonyId.equals(colonyId)).findFirst().orElse(null);
    if (building == null || building.level < 1) throw new CommandException("Die Verteidigungsanlage muss zunächst gebaut werden.");
    if (building.activationState == DefenseActivationState.Active || building.activationState == DefenseActivationState.Activating) {
      throw new CommandException("Bereits aktiv bzw. in Aktivierung.");
    }
    building.activationState = DefenseActivationState.Activating;
    building.activationCompletesAt = Clock.now() + (long) Clock.hoursToMs(DEFENSE_ACTIVATION_HOURS);
  }

  private static final int DEFENSE_ACTIVATION_HOURS = 12;

  public static void deactivateDefense(GameState state, String playerId, String colonyId, String buildingId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    for (Building b : state.buildings) {
      if (b.id.equals(buildingId) && b.colonyId.equals(colonyId)) {
        b.activationState = DefenseActivationState.Inactive;
        b.activationCompletesAt = null;
      }
    }
  }
}
