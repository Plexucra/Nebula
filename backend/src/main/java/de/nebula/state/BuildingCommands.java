package de.nebula.state;

import de.nebula.data.BuildingCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.engine.SharedConstants;
import de.nebula.model.BuildSlots;
import de.nebula.model.Building;
import de.nebula.model.BuildingCategory;
import de.nebula.model.BuildingMaterial;
import de.nebula.model.BuildingType;
import de.nebula.model.Colony;
import de.nebula.model.DefenseActivationState;
import de.nebula.model.MaterialRequirement;
import de.nebula.model.PendingBuildingOrder;
import de.nebula.model.Planet;
import de.nebula.model.TransactionReason;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;
import de.nebula.model.WarehouseEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Bebauung (Umsetzungskonzept/17_...md): Bebauungsplätze statt
 * Bebauungspunkten, Baustoffe zusätzlich zu Credits, keine Höchststufe außer
 * der planetweiten Infrastruktur-Grenze. Alle Regeln leben ausschließlich
 * hier – die Oberfläche bekommt Kosten/Plätze fertig über
 * {@link #buildSlots} und {@link #upgradePreview}.
 */
public final class BuildingCommands {
  private BuildingCommands() {
  }

  public static List<Building> buildingsForColony(GameState state, String colonyId) {
    return state.buildings.stream().filter(b -> b.colonyId.equals(colonyId)).toList();
  }

  private static boolean isInfrastructure(BuildingType type) {
    return type.category == BuildingCategory.Infrastructure;
  }

  /** Gebäude, deren Kapazität sich je Stufe verdoppelt – Wohnkomplex und Forschungszentrum (Umsetzungskonzept/38): Kosten und Baustoffe verdoppeln mit. */
  static boolean isDoubling(BuildingType type) {
    return type.category == BuildingCategory.Housing || type.category == BuildingCategory.Research;
  }

  /** Stufe inklusive laufendem Ausbau – ein bereits beauftragter Ausbau belegt seinen Platz sofort. */
  private static int committedLevel(Building b) {
    return b.level + (b.pendingOrder != null ? 1 : 0);
  }

  /** Summe der Infrastruktur-Stufen (inkl. laufender Ausbauten) ALLER Kolonien auf dem Planeten einer Kolonie. */
  public static int planetInfrastructureTotal(GameState state, String planetId) {
    int total = 0;
    for (Colony c : state.colonies) {
      if (!c.planetId.equals(planetId)) continue;
      for (Building b : state.buildings) {
        if (b.colonyId.equals(c.id) && isInfrastructure(BuildingCatalog.find(b.typeId))) total += committedLevel(b);
      }
    }
    return total;
  }

  public static int planetInfrastructureMax(GameState state, String planetId) {
    Planet planet = ColonyCommands.planet(state, planetId);
    if (planet == null) throw new CommandException("Unbekannter Planet.");
    return SharedConstants.maxInfrastructureByPlanetSize(planet.size.name());
  }

  /** Bebauungsplätze der Kolonie, siehe {@link BuildSlots}. */
  public static BuildSlots buildSlots(GameState state, String colonyId) {
    Colony colony = ColonyCommands.colony(state, colonyId);
    if (colony == null) throw new CommandException("Unbekannte Kolonie.");
    int infrastructureLevel = 0;
    int used = 0;
    for (Building b : state.buildings) {
      if (!b.colonyId.equals(colonyId)) continue;
      if (isInfrastructure(BuildingCatalog.find(b.typeId))) infrastructureLevel = b.level;
      else used += committedLevel(b);
    }
    BuildSlots slots = new BuildSlots();
    slots.infrastructureLevel = infrastructureLevel;
    slots.total = infrastructureLevel * GameConstants.SLOTS_PER_INFRASTRUCTURE_LEVEL;
    slots.used = used;
    slots.free = slots.total - used;
    slots.planetInfrastructureTotal = planetInfrastructureTotal(state, colony.planetId);
    slots.planetInfrastructureMax = planetInfrastructureMax(state, colony.planetId);
    return slots;
  }

  /** Vorschau für EINEN Ausbauschritt: Zielstufe, Credits, Stunden, Baustoffe mit Lagerabgleich. */
  public record UpgradePreview(int currentLevel, int targetLevel, double credits, double hours,
                               List<MaterialRequirement> materials, boolean needsSlot) {
  }

  public static UpgradePreview upgradePreview(GameState state, String colonyId, BuildingType type) {
    Colony colony = ColonyCommands.colony(state, colonyId);
    if (colony == null) throw new CommandException("Unbekannte Kolonie.");
    int fromLevel = GameQueries.getBuildingLevel(state, colonyId, type.id);
    int planetTotal = isInfrastructure(type) ? planetInfrastructureTotal(state, colony.planetId) : 0;
    return preview(type, fromLevel, planetTotal, productTypeId -> Warehouse.qty(state, colonyId, productTypeId));
  }

  /**
   * Vorschauen für ALLE Gebäudetypen einer Kolonie in einem Rutsch – für die
   * Transparenz-Aufschlüsselung ({@code ColonyCommands.colonySpeedBreakdown}),
   * die jede Sekunde je offener Kolonieseite abgefragt wird. Gebäudestufen und
   * Lagerbestand werden EINMAL eingesammelt statt je Typ und Baustoff die
   * Listen abzulaufen; die Planetensumme kommt aus den bereits berechneten
   * {@link BuildSlots}.
   */
  public static Map<String, UpgradePreview> upgradePreviews(GameState state, Colony colony, BuildSlots slots) {
    Map<String, Integer> levels = new HashMap<>();
    for (Building b : state.buildings) if (b.colonyId.equals(colony.id)) levels.put(b.typeId, b.level);
    Map<String, Double> stock = new HashMap<>();
    for (WarehouseEntry w : state.warehouse) if (w.colonyId.equals(colony.id)) stock.put(w.productTypeId, w.quantity);
    Map<String, UpgradePreview> out = new LinkedHashMap<>();
    for (BuildingType type : BuildingCatalog.CATALOG) {
      out.put(type.id, preview(type, levels.getOrDefault(type.id, 0), slots.planetInfrastructureTotal,
          productTypeId -> stock.getOrDefault(productTypeId, 0.0)));
    }
    return out;
  }

  private static UpgradePreview preview(BuildingType type, int fromLevel, int planetTotal, Function<String, Double> stockOf) {
    int targetLevel = fromLevel + 1;
    double credits;
    double hours;
    int materialLevel;
    if (isInfrastructure(type)) {
      // Planetweite Summe T bestimmt Preis, Dauer UND Baustoffmenge – dicht besiedelte Planeten werden für alle teurer.
      credits = Formulas.infrastructureUpgradeCost(type.baseCostPerLevel, planetTotal);
      hours = Formulas.infrastructureUpgradeHours(type.baseHoursPerLevel, planetTotal);
      materialLevel = planetTotal + 1;
    } else {
      // Wohnkomplex und Forschungszentrum: Kapazität verdoppelt sich je Stufe, Credits/Baustoffe deshalb ebenfalls.
      credits = isDoubling(type)
          ? Formulas.housingUpgradeCost(type.baseCostPerLevel, fromLevel)
          : Formulas.buildingUpgradeCost(type.baseCostPerLevel, fromLevel);
      hours = Formulas.buildingUpgradeHours(type.baseHoursPerLevel, fromLevel);
      materialLevel = targetLevel;
    }
    List<MaterialRequirement> materials = new ArrayList<>();
    for (BuildingMaterial m : type.materials) {
      if (materialLevel < m.fromLevel) continue;
      MaterialRequirement req = new MaterialRequirement();
      req.productTypeId = m.productTypeId;
      req.required = isDoubling(type)
          ? Formulas.housingMaterialQuantity(m.baseQuantity, materialLevel)
          : Formulas.buildingMaterialQuantity(m.baseQuantity, materialLevel);
      req.available = stockOf.apply(m.productTypeId);
      materials.add(req);
    }
    return new UpgradePreview(fromLevel, targetLevel, credits, hours, materials, !isInfrastructure(type));
  }

  /**
   * Mechanik/05_...md §1, letzter Absatz: "Während eines aktiven Bodengefechts
   * an einer Kolonie: kein Bau/Ausbau/Abriss möglich." Gilt für JEDEN
   * Bauvorgang an dieser Kolonie, auch für den bereits laufenden Abbruch – wer
   * unter Beschuss steht, baut nicht.
   */
  static void requireNoGroundBattle(GameState state, String colonyId) {
    if (GroundBattleCommands.isUnderGroundAttack(state, colonyId)) {
      throw new CommandException("Während eines laufenden Bodengefechts kann an dieser Kolonie nicht gebaut werden.");
    }
  }

  /**
   * Reiht die Baustoffe, die dem nächsten Ausbau dieses Gebäudes noch fehlen,
   * als EINEN Bündelauftrag ein ({@code ProductionCommands.queueProductionBundle}).
   *
   * <p>Das ist der Ausweg aus der Falle, in die vorher jeder neue Kommandant
   * lief: Ein Ausbau braucht bis zu fünf Baustoffe GLEICHZEITIG, und mehrere
   * davon sind Vorprodukte voneinander (etwa Leitermetall im Leiterbündel).
   * Wer sie nacheinander einreiht, verliert den zuerst produzierten Baustoff
   * still an den zweiten Auftrag und kann den Ausbau nie bezahlen. Es gab genau
   * eine richtige Reihenfolge, und die Oberfläche verriet sie nirgends – der
   * Bündelauftrag macht die Reihenfolge irrelevant.</p>
   *
   * <p>Fehlt nur wenig, wäre der Auftrag kürzer als die Mindestdauer eines
   * Produktionsauftrags; dann werden alle Mengen im gleichen Verhältnis
   * angehoben – der Überschuss bleibt im Lager für den nächsten Ausbau.</p>
   *
   * @return die eingereihten Mengen je Baustoff (leer, wenn nichts fehlt)
   */
  public static Map<String, Double> queueMissingMaterials(GameState state, IdGenerator ids, String playerId,
                                                          String colonyId, String buildingTypeId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    BuildingType type = BuildingCatalog.find(buildingTypeId);
    UpgradePreview preview = upgradePreview(state, colonyId, type);

    Map<String, Double> demand = new java.util.LinkedHashMap<>();
    for (MaterialRequirement m : preview.materials()) {
      double missing = m.required - m.available;
      if (missing > 1e-9) demand.put(m.productTypeId, Math.ceil(missing));
    }
    if (demand.isEmpty()) {
      throw new CommandException("Für den nächsten Ausbau von " + type.name + " sind alle Baustoffe vorhanden.");
    }
    return ProductionCommands.queueProductionBundleCore(state, ids, colonyId, demand, true, false, true);
  }

  public static void queueBuilding(GameState state, IdGenerator ids, String playerId, String colonyId, String buildingTypeId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    queueBuildingCore(state, ids, colonyId, buildingTypeId);
  }

  /** Ungeprüfter Kern von {@link #queueBuilding} (ohne Besitzprüfung), für Aufrufer, die die Kolonie bereits verifiziert haben. */
  public static void queueBuildingCore(GameState state, IdGenerator ids, String colonyId, String buildingTypeId) {
    requireNoGroundBattle(state, colonyId);
    String colonyOwnerId = GameQueries.requireColonyOwner(state, colonyId);
    Colony colony = ColonyCommands.colony(state, colonyId);
    BuildingType type = BuildingCatalog.find(buildingTypeId);
    Building building = state.buildings.stream()
        .filter(b -> b.colonyId.equals(colonyId) && b.typeId.equals(buildingTypeId)).findFirst().orElse(null);
    if (building != null && building.pendingOrder != null) throw new CommandException(type.name + " wird bereits ausgebaut.");

    UpgradePreview preview = upgradePreview(state, colonyId, type);

    if (isInfrastructure(type)) {
      int planetTotal = planetInfrastructureTotal(state, colony.planetId);
      int max = planetInfrastructureMax(state, colony.planetId);
      if (planetTotal >= max) {
        throw new CommandException("Die Planetengröße erlaubt keine weitere Infrastruktur (planetweit " + planetTotal + "/" + max + ").");
      }
    } else {
      BuildSlots slots = buildSlots(state, colonyId);
      if (slots.free <= 0) throw new CommandException("Kein freier Bebauungsplatz – Infrastruktur ausbauen.");
    }

    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, colonyOwnerId);
    if (wallet == null || wallet.balance < preview.credits()) throw new CommandException("Nicht genug Credits für diesen Ausbau.");

    List<String> missing = new ArrayList<>();
    for (MaterialRequirement m : preview.materials()) {
      if (m.available + 1e-9 < m.required) {
        missing.add(m.productTypeId + " (" + (long) m.required + " benötigt, " + (long) Math.floor(m.available) + " vorhanden)");
      }
    }
    if (!missing.isEmpty()) throw new CommandException("Fehlende Baustoffe: " + String.join(", ", missing));

    for (MaterialRequirement m : preview.materials()) Warehouse.add(state, colonyId, m.productTypeId, -m.required);

    long t = Clock.now();
    PendingBuildingOrder pendingOrder = new PendingBuildingOrder();
    pendingOrder.targetLevel = preview.targetLevel();
    pendingOrder.startedAt = t;
    pendingOrder.completesAt = t + (long) Clock.hoursToMs(preview.hours());

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
    GameEvents.schedule(state, GameEventType.BUILDING_COMPLETED, building.id, pendingOrder.completesAt);
    Ledger.recordTx(state, ids, wallet.id, GameQueries.popWalletIdForColony(state, colonyId), preview.credits(),
        TransactionReason.Construction, "Ausbau " + type.name + " → Stufe " + preview.targetLevel());
  }

  /**
   * Ereignis {@code BUILDING_COMPLETED}: der Ausbau ist fertig. Veraltet, wenn
   * der Auftrag inzwischen abgebrochen oder ersetzt wurde (dann trägt das
   * Gebäude keine oder eine andere Fälligkeit).
   */
  static void completeBuilding(GameState state, IdGenerator ids, String buildingId, long at) {
    Building building = find(state, buildingId);
    if (building == null || building.pendingOrder == null || building.pendingOrder.completesAt != at) return;
    building.level = building.pendingOrder.targetLevel;
    building.pendingOrder = null;
    Colony colony = ColonyCommands.colony(state, building.colonyId);
    if (colony != null) {
      // Ein fertiges Gebäude war bisher nur an der veränderten Stufe zu erkennen –
      // wer nicht gerade auf dem Bebauungs-Tab stand, erfuhr nichts davon.
      BuildingType type = BuildingCatalog.find(building.typeId);
      Notifications.notify(state, ids, de.nebula.model.NotificationType.Info, Notifications.CODE_BUILDING_DONE,
          type.name + " in \"" + colony.name + "\" ist auf Stufe " + building.level + " fertig.",
          colony.id, Notifications.colonyLink(colony.id));
    }
    // Ein fertiger Industriekomplex weckt wartende Produktionsaufträge (Minimalstart, Umsetzungskonzept/17_...md).
    ProductionCommands.tryStartNextProductionEntry(state, ids, building.colonyId);
  }

  /** Ereignis {@code DEFENSE_ACTIVATED}: die Vorlaufzeit der Verteidigungsanlage ist um. */
  static void completeDefenseActivation(GameState state, String buildingId, long at) {
    Building building = find(state, buildingId);
    if (building == null || building.activationState != DefenseActivationState.Activating
        || building.activationCompletesAt == null || building.activationCompletesAt != at) return;
    building.activationState = DefenseActivationState.Active;
    building.activationCompletesAt = null;
  }

  private static Building find(GameState state, String buildingId) {
    for (Building b : state.buildings) if (b.id.equals(buildingId)) return b;
    return null;
  }

  /** Abbruch erstattet Credits UND Baustoffe vollständig – bezahlt wurde für die Zielstufe, die nicht entsteht. */
  public static void cancelBuildingOrder(GameState state, IdGenerator ids, String playerId, String colonyId, String buildingId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    Building building = state.buildings.stream()
        .filter(b -> b.id.equals(buildingId) && b.colonyId.equals(colonyId)).findFirst().orElse(null);
    if (building == null || building.pendingOrder == null) throw new CommandException("Kein laufender Ausbauauftrag.");
    BuildingType type = BuildingCatalog.find(building.typeId);
    building.pendingOrder = null;
    GameEvents.cancel(state, GameEventType.BUILDING_COMPLETED, building.id);
    // Die Vorschau beschreibt nach dem Zurücksetzen exakt den Schritt, der gerade lief.
    UpgradePreview preview = upgradePreview(state, colonyId, type);
    for (MaterialRequirement m : preview.materials()) Warehouse.add(state, colonyId, m.productTypeId, m.required);
    var player = GameQueries.requirePlayer(state, playerId);
    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, player.id);
    if (wallet != null) {
      Ledger.recordTx(state, ids, GameQueries.popWalletIdForColony(state, colonyId), wallet.id, preview.credits(),
          TransactionReason.Construction, "Abbruch Ausbau " + type.name);
    }
  }

  /**
   * Rückbau um eine Stufe: 50 % der Credits zurück, Baustoffe sind verbaut und
   * verloren. Infrastruktur kann nur zurückgebaut werden, solange danach noch
   * genug Bebauungsplätze für die bestehenden Gebäude bleiben.
   */
  public static void demolishBuilding(GameState state, IdGenerator ids, String playerId, String colonyId, String buildingId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    requireNoGroundBattle(state, colonyId);
    Building building = state.buildings.stream()
        .filter(b -> b.id.equals(buildingId) && b.colonyId.equals(colonyId)).findFirst().orElse(null);
    if (building == null || building.level <= 0) throw new CommandException("Kein rückbaubares Gebäude.");
    if (building.pendingOrder != null) throw new CommandException("Während eines laufenden Ausbaus kann nicht rückgebaut werden.");
    BuildingType type = BuildingCatalog.find(building.typeId);
    if (isInfrastructure(type)) {
      BuildSlots slots = buildSlots(state, colonyId);
      if (slots.used > slots.total - GameConstants.SLOTS_PER_INFRASTRUCTURE_LEVEL) {
        throw new CommandException("Rückbau nicht möglich: die bestehenden Gebäude brauchen diese Bebauungsplätze.");
      }
    }
    double refund = Math.round(0.5 * (isDoubling(type)
        ? Formulas.housingUpgradeCost(type.baseCostPerLevel, building.level - 1)
        : Formulas.buildingUpgradeCost(type.baseCostPerLevel, building.level - 1)));
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
    GameEvents.schedule(state, GameEventType.DEFENSE_ACTIVATED, building.id, building.activationCompletesAt);
  }

  private static final int DEFENSE_ACTIVATION_HOURS = 12;

  public static void deactivateDefense(GameState state, String playerId, String colonyId, String buildingId) {
    GameQueries.requireOwnColony(state, playerId, colonyId);
    for (Building b : state.buildings) {
      if (b.id.equals(buildingId) && b.colonyId.equals(colonyId)) {
        b.activationState = DefenseActivationState.Inactive;
        b.activationCompletesAt = null;
        GameEvents.cancel(state, GameEventType.DEFENSE_ACTIVATED, b.id);
      }
    }
  }
}
