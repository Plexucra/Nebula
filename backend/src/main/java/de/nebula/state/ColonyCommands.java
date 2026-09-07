package de.nebula.state;

import de.nebula.data.BuildingCatalog;
import de.nebula.data.ProductCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.BuildSlots;
import de.nebula.model.Building;
import de.nebula.model.BuildingType;
import de.nebula.model.Colony;
import de.nebula.model.ColonySpeedBreakdown;
import de.nebula.model.Planet;
import de.nebula.model.PlanetResourceConcentration;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import de.nebula.model.PopulationGrowthState;
import de.nebula.model.PopulationMoneySupplyState;
import de.nebula.model.ProductType;
import de.nebula.model.Specialization;
import de.nebula.model.TransactionReason;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1:1-Portierung der "Planeten/Kolonien"-Sektion aus
 * {@code simulated-game-api.service.ts} (Umsetzungskonzept/13_...md, Phase 5).
 */
public final class ColonyCommands {
  private ColonyCommands() {
  }

  public static List<Colony> coloniesOf(GameState state, String playerId) {
    return state.colonies.stream().filter(c -> c.ownerId.equals(playerId)).toList();
  }

  public static List<Colony> coloniesInSystem(GameState state, String systemId) {
    return state.colonies.stream().filter(c -> c.systemId.equals(systemId)).toList();
  }

  public static Colony colony(GameState state, String id) {
    return state.colonies.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null);
  }

  /**
   * Fertig berechnete Aufschlüsselung aller Produktionstempo-Faktoren dieser
   * Kolonie für die Transparenz-Panels der Oberfläche (siehe
   * {@link ColonySpeedBreakdown}). Bewusst EIN Aufruf statt vieler
   * Einzelabfragen je Gebäudetyp/Produkt: die Panels zeigen alles gleichzeitig,
   * und jede Zeile einzeln nachzufragen wäre pro Kolonie-Ansicht ein Vielfaches
   * an WebSocket-Runden.
   */
  public static ColonySpeedBreakdown colonySpeedBreakdown(GameState state, String colonyId) {
    Colony colony = colony(state, colonyId);
    if (colony == null) throw new CommandException("Unbekannte Kolonie.");

    double population = 0;
    for (Population p : state.populations) if (p.colonyId.equals(colonyId)) population = p.currentCount;
    PlanetStats stats = colonyStats(state, colonyId);
    int industryLevel = GameQueries.getBuildingLevel(state, colonyId, "b_industry");

    ColonySpeedBreakdown result = new ColonySpeedBreakdown();
    result.population = population;
    result.availableWorkers = population;
    result.industryLevel = industryLevel;
    result.buildingSpeedFactor = Formulas.buildingLevelSpeedFactor(industryLevel);
    result.blackout = PowerGrid.isBlackout(state, colonyId);
    result.satisfactionPct = stats != null
        ? Formulas.growthConditionFactor(stats.standardOfLivingPct, stats.securityPct) * 100 : 0;

    double housingCapacity = PowerGrid.effectiveHousingCapacity(state, colonyId);
    result.housingCapacity = housingCapacity;
    result.growthState = stats != null
        ? Formulas.populationGrowthState(population, housingCapacity, stats.standardOfLivingPct) : PopulationGrowthState.Holding;
    result.growthPerHour = stats != null
        ? Formulas.populationGrowthDelta(population, housingCapacity, stats.standardOfLivingPct, stats.securityPct) : 0;
    result.shrinkBelowPct = Formulas.LIVING_STANDARD_SHRINK_BELOW_PCT;
    result.growthFromPct = Formulas.LIVING_STANDARD_GROWTH_FROM_PCT;
    BuildSlots slots = BuildingCommands.buildSlots(state, colonyId);
    result.buildSlots = slots;
    result.infrastructureEleriumPerHour = PowerGrid.powerUpkeepPerHour(state, colonyId);
    result.powerCoverage = PowerGrid.coverageRatio(state, colonyId);

    Wallet ownerWallet = GameQueries.findWallet(state, WalletOwnerType.Player, colony.ownerId);
    double balance = ownerWallet != null ? ownerWallet.balance : 0;
    List<ColonySpeedBreakdown.BuildingUpgradePreview> upgrades = new ArrayList<>();
    for (BuildingType type : BuildingCatalog.CATALOG) {
      BuildingCommands.UpgradePreview up = BuildingCommands.upgradePreview(state, colonyId, type);
      ColonySpeedBreakdown.BuildingUpgradePreview preview = new ColonySpeedBreakdown.BuildingUpgradePreview();
      preview.typeId = type.id;
      preview.currentLevel = up.currentLevel();
      preview.upgradeCost = up.credits();
      preview.upgradeHours = up.hours();
      preview.productionSpeedPct = Formulas.buildingLevelSpeedFactor(up.currentLevel()) * 100;
      preview.nextProductionSpeedPct = Formulas.buildingLevelSpeedFactor(up.currentLevel() + 1) * 100;
      preview.materials = up.materials();
      preview.needsSlot = up.needsSlot();
      String blocked = null;
      if (up.needsSlot() && slots.free <= 0) blocked = "Kein freier Bebauungsplatz – Infrastruktur ausbauen.";
      else if (!up.needsSlot() && slots.planetInfrastructureTotal >= slots.planetInfrastructureMax) {
        blocked = "Die Planetengröße erlaubt keine weitere Infrastruktur (planetweit "
            + slots.planetInfrastructureTotal + "/" + slots.planetInfrastructureMax + ").";
      } else if (balance < up.credits()) blocked = "Nicht genug Credits für diesen Ausbau.";
      else if (up.materials().stream().anyMatch(m -> m.available + 1e-9 < m.required)) blocked = "Fehlende Baustoffe.";
      preview.affordable = blocked == null;
      preview.blockedReason = blocked;
      upgrades.add(preview);
    }
    result.buildingUpgrades = upgrades;

    Map<String, Double> specBonus = new LinkedHashMap<>();
    for (Specialization s : state.specializations) {
      if (s.colonyId.equals(colonyId)) {
        specBonus.put(s.productTypeId, (Formulas.specializationSpeedFactor((int) s.currentLevel) - 1) * 100);
      }
    }
    result.specializationSpeedBonusPctByProduct = specBonus;

    // Fördergüte gilt nur für Rohstoffe (Tier 0 mit Rohstoffprofil) und hängt
    // am Planeten der Kolonie – für alle davon betroffenen Produkte vorab
    // berechnet, damit die Oberfläche je Produktionsschritt nur nachschlagen muss.
    Planet planet = null;
    for (Planet p : state.planets) if (p.id.equals(colony.planetId)) planet = p;
    Map<String, Double> concByProduct = new LinkedHashMap<>();
    if (planet != null) {
      for (ProductType product : ProductCatalog.CATALOG) {
        if (product.tier != 0 || product.resourceProfile.isEmpty()) continue;
        String resourceTypeId = product.resourceProfile.get(0).resourceTypeId;
        double concentration = 50;
        for (PlanetResourceConcentration c : planet.resourceConcentration) {
          if (c.resourceTypeId.equals(resourceTypeId)) {
            concentration = c.concentration;
            break;
          }
        }
        concByProduct.put(product.id, Formulas.resourceConcentrationFactor(concentration));
      }
    }
    result.concentrationFactorByProduct = concByProduct;
    return result;
  }

  public static PlanetStats colonyStats(GameState state, String colonyId) {
    return state.planetStats.stream().filter(s -> s.colonyId.equals(colonyId)).findFirst().orElse(null);
  }

  public static Population population(GameState state, String colonyId) {
    return state.populations.stream().filter(p -> p.colonyId.equals(colonyId)).findFirst().orElse(null);
  }

  public static PopulationMoneySupplyState moneySupplyState(GameState state, String planetId) {
    return state.moneySupplyStates.stream().filter(m -> m.planetId.equals(planetId)).findFirst().orElse(null);
  }

  /** Deckung (0..1,5) je Grundkonsumgut – Diagnosewert für die Statistik-Seite, siehe {@code EconomyTick.runConsumption}. */
  public static java.util.Map<String, Double> consumptionCoverage(GameState state, String colonyId) {
    return state.consumptionCoverage.getOrDefault(colonyId, java.util.Map.of());
  }

  public static Planet planet(GameState state, String id) {
    return state.planets.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null);
  }

  /**
   * Ob {@code playerId} die Rohstoffkonzentration von {@code planet} kennt: entweder durch
   * explizites Erforschen des Systems ({@code GatewayCommands.hasExploredSystem}, siehe
   * {@code FleetCommands.exploreSystem}) oder implizit, weil dort bereits eine eigene Kolonie
   * besteht (wer kolonisiert, kennt zwangsläufig die Fördergüte).
   */
  private static boolean knowsResourceConcentration(GameState state, Planet planet, String playerId) {
    if (playerId == null) return false;
    if (GatewayCommands.hasExploredSystem(state, playerId, planet.systemId)) return true;
    return state.colonies.stream().anyMatch(c -> c.planetId.equals(planet.id) && c.ownerId.equals(playerId));
  }

  /** Kopie von {@code planet} ohne Rohstoffkonzentration – für Kommandanten, die das System noch nicht erforscht haben. */
  private static Planet withoutResourceConcentration(Planet planet) {
    Planet copy = new Planet();
    copy.id = planet.id;
    copy.systemId = planet.systemId;
    copy.name = planet.name;
    copy.size = planet.size;
    copy.type = planet.type;
    copy.resourceConcentration = List.of();
    copy.orbitIndex = planet.orbitIndex;
    copy.usable = planet.usable;
    return copy;
  }

  /**
   * Client-facing Fassung EINES Planeten: blendet die Rohstoffkonzentration aus, solange
   * {@code playerId} das System noch nicht erforscht hat (siehe {@link #knowsResourceConcentration}).
   * Interne Geschäftslogik (Produktion, Kolonisierung, ...) liest weiterhin direkt
   * {@link #planet(GameState, String)} bzw. {@code state.planets} – die reale Fördergüte bleibt
   * davon unberührt, nur ihre Sichtbarkeit für die Oberfläche ist gegated.
   */
  public static Planet planetForPlayer(GameState state, String id, String playerId) {
    Planet planet = planet(state, id);
    if (planet == null) return null;
    return knowsResourceConcentration(state, planet, playerId) ? planet : withoutResourceConcentration(planet);
  }

  /** Client-facing Fassung ALLER Planeten eines Systems, siehe {@link #planetForPlayer}. */
  public static List<Planet> planetsInSystemForPlayer(GameState state, String systemId, String playerId) {
    return state.planets.stream()
        .filter(p -> p.systemId.equals(systemId))
        .map(p -> knowsResourceConcentration(state, p, playerId) ? p : withoutResourceConcentration(p))
        .toList();
  }

  private static final double COLONIZE_COST = 800;

  public static Colony colonizePlanet(GameState state, IdGenerator ids, String playerId, String planetId) {
    var player = GameQueries.requirePlayer(state, playerId);
    Planet planet = planet(state, planetId);
    if (planet == null) throw new CommandException("Unbekannter Planet.");
    if (!planet.usable) throw new CommandException("Dieser Himmelskörper ist nicht besiedelbar.");
    boolean alreadyOwned = state.colonies.stream().anyMatch(c -> c.planetId.equals(planetId) && c.ownerId.equals(player.id));
    if (alreadyOwned) throw new CommandException("Auf diesem Planeten besteht bereits eine eigene Kolonie.");

    Wallet wallet = GameQueries.findWallet(state, WalletOwnerType.Player, player.id);
    if (wallet == null || wallet.balance < COLONIZE_COST) throw new CommandException("Nicht genug Credits für eine Kolonialgründung.");

    long t = Clock.now();
    Colony colony = new Colony();
    colony.id = ids.next("col");
    colony.planetId = planetId;
    colony.systemId = planet.systemId;
    colony.ownerId = player.id;
    colony.name = planet.name + "-Kolonie";
    colony.foundedAt = t;
    colony.isHomeworld = false;
    state.colonies.add(colony);

    PlanetStats stats = new PlanetStats();
    stats.colonyId = colony.id;
    stats.infrastructurePct = 15;
    stats.securityPct = 5;
    stats.standardOfLivingPct = 30;
    stats.loyaltyPct = 55;
    stats.lastRecalculatedAt = t;
    state.planetStats.add(stats);

    Population population = new Population();
    population.colonyId = colony.id;
    population.currentCount = 25;
    population.growthRatePerInterval = 0;
    state.populations.add(population);

    boolean moneySupplyExists = state.moneySupplyStates.stream().anyMatch(m -> m.planetId.equals(planetId));
    if (!moneySupplyExists) {
      PopulationMoneySupplyState money = new PopulationMoneySupplyState();
      money.planetId = planetId;
      money.historicalPeakPopulation = 25;
      money.lastPopulation = 25;
      state.moneySupplyStates.add(money);
    }

    Wallet popWallet = new Wallet();
    popWallet.id = ids.next("wal");
    popWallet.ownerType = WalletOwnerType.Population;
    popWallet.ownerId = colony.id;
    popWallet.balance = 30;
    state.wallets.add(popWallet);

    // Minimalstart wie bei der Heimatwelt (Umsetzungskonzept/17_...md): Wohnkomplex 1
    // + Industriekomplex 1 + Infrastruktur 2 – beide Plätze belegt.
    for (String[] start : new String[][]{{"b_habitat", "1"}, {"b_industry", "1"}, {GameConstants.INFRASTRUCTURE_BUILDING_ID, "2"}}) {
      Building b = new Building();
      b.id = ids.next("bld");
      b.colonyId = colony.id;
      b.typeId = start[0];
      b.level = Integer.parseInt(start[1]);
      state.buildings.add(b);
    }

    Ledger.recordTx(state, ids, wallet.id, GameQueries.homeworldPopulationWalletId(state, player.id), COLONIZE_COST,
        TransactionReason.Construction, "Kolonialgründung " + colony.name);
    return colony;
  }
}
