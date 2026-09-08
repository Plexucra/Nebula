package de.nebula.state;

import de.nebula.data.BuildingCatalog;
import de.nebula.data.ProductCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.BuildSlots;
import de.nebula.model.Building;
import de.nebula.model.BuildingType;
import de.nebula.model.Colonization;
import de.nebula.model.Colony;
import de.nebula.model.ColonySpeedBreakdown;
import de.nebula.model.Fleet;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.NotificationType;
import de.nebula.model.Planet;
import de.nebula.model.PlanetResourceConcentration;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import de.nebula.model.PopulationGrowthState;
import de.nebula.model.PopulationMoneySupplyState;
import de.nebula.model.ProductType;
import de.nebula.model.Specialization;
import de.nebula.model.SupplyInventoryEntry;
import de.nebula.model.WarehouseEntry;
import de.nebula.model.TransactionReason;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;

import java.util.ArrayList;
import java.util.Comparator;
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

  /** Alle Kolonien (aller Spieler) auf diesem Planeten – {@code LandingCommands.land} prüft jede einzeln auf Landungsabwehr. */
  public static List<Colony> coloniesOnPlanet(GameState state, String planetId) {
    return state.colonies.stream().filter(c -> c.planetId.equals(planetId)).toList();
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

  /**
   * Löst die Landung aus (Umsetzungskonzept/24_...md): verbraucht EIN
   * Kolonisationsschiff aus einer eigenen, im Orbit dieses Planeten liegenden
   * Flotte und startet die {@link Colonization}. Die Kolonie selbst entsteht
   * erst einen Spieltag später in {@link #processColonizations} – der Aufruf
   * liefert daher keine Kolonie mehr, sondern den laufenden Vorgang.
   *
   * <p>Die frühere reine Credit-Gründung (800 Cr, sofort) ist damit abgelöst:
   * bezahlt wird jetzt über das Schiff (Bau in der Werft, 2000 Kolonisten,
   * Kolonistenprämie, siehe {@code ShipyardCommands}).</p>
   */
  public static Colonization colonizePlanet(GameState state, IdGenerator ids, String playerId, String planetId) {
    var player = GameQueries.requirePlayer(state, playerId);
    Planet planet = planet(state, planetId);
    if (planet == null) throw new CommandException("Unbekannter Planet.");
    if (!planet.usable) throw new CommandException("Dieser Himmelskörper ist nicht besiedelbar.");
    boolean alreadyOwned = state.colonies.stream().anyMatch(c -> c.planetId.equals(planetId) && c.ownerId.equals(player.id));
    if (alreadyOwned) throw new CommandException("Auf diesem Planeten besteht bereits eine eigene Kolonie.");
    boolean alreadyRunning = state.colonizations.stream().anyMatch(c -> c.planetId.equals(planetId) && c.ownerId.equals(player.id));
    if (alreadyRunning) throw new CommandException("Auf diesem Planeten läuft bereits eine eigene Koloniegründung.");

    Fleet fleet = fleetWithColonyShipAt(state, player.id, planet);
    if (fleet == null) {
      throw new CommandException("Dafür muss eine eigene Flotte mit einem Kolonisationsschiff im Orbit dieses Planeten liegen.");
    }
    // Das Schiff wird bei der Landung verbraucht – es IST die neue Kolonie. War es das
    // letzte Schiff der Flotte, verschwindet auch die Flotte (siehe consumeShips): eine
    // reine Kolonisationsflotte hinterlässt sonst eine schiffslose Geisterflotte.
    FleetCommands.consumeShips(state, fleet, GameConstants.COLONY_SHIP_PRODUCT_ID, 1);

    long t = Clock.now();
    Colonization colonization = new Colonization();
    colonization.id = ids.next("cln");
    colonization.planetId = planetId;
    colonization.systemId = planet.systemId;
    colonization.ownerId = player.id;
    colonization.fleetId = fleet.id;
    colonization.colonyName = planet.name + "-Kolonie";
    colonization.startedAt = t;
    colonization.endsAt = t + (long) Clock.hoursToMs(GameConstants.COLONIZATION_HOURS);
    state.colonizations.add(colonization);
    return colonization;
  }

  /**
   * Versorgungsinventar der Kolonie (Umsetzungskonzept/25_...md): alles, was im
   * Lager liegt, angereichert um Verbrauch je Spielstunde und Reichweite. Nach
   * Produktkategorie und Name sortiert, damit die Liste stabil bleibt und nicht
   * bei jedem Poll umspringt.
   */
  public static List<SupplyInventoryEntry> supplyInventory(GameState state, String colonyId) {
    double population = 0;
    for (Population p : state.populations) if (p.colonyId.equals(colonyId)) population = p.currentCount;
    int infrastructureLevel = GameQueries.getBuildingLevel(state, colonyId, GameConstants.INFRASTRUCTURE_BUILDING_ID);
    // Der Pro-Kopf-Bedarf ist je TICK definiert; für die Anzeige rechnen wir ihn
    // auf die Spielstunde hoch, weil Spieler in Spielstunden denken.
    double ticksPerGameHour = 1 / GameConstants.TICK_GAME_HOURS;

    List<SupplyInventoryEntry> result = new ArrayList<>();
    for (WarehouseEntry w : state.warehouse) {
      if (!w.colonyId.equals(colonyId)) continue;
      ProductType product = ProductCatalog.find(w.productTypeId);
      SupplyInventoryEntry entry = new SupplyInventoryEntry();
      entry.productTypeId = w.productTypeId;
      entry.name = product.name;
      entry.category = product.category;
      entry.quantity = w.quantity;

      Double perCapita = GameConstants.CONSUMER_NEED_PER_CAPITA.get(w.productTypeId);
      if (perCapita != null) {
        entry.consumptionPerGameHour = population * perCapita * ticksPerGameHour;
        entry.pendingFraction = FractionPot.pending(state, FractionPot.key("consume", colonyId, w.productTypeId));
      } else if (GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID.equals(w.productTypeId)) {
        entry.consumptionPerGameHour = Formulas.infrastructureEleriumPerHour(infrastructureLevel);
        entry.pendingFraction = FractionPot.pending(state, FractionPot.key("power", colonyId));
      }
      entry.coverageGameHours = entry.consumptionPerGameHour > 0
          ? entry.quantity / entry.consumptionPerGameHour : null;
      result.add(entry);
    }
    result.sort(Comparator.comparing((SupplyInventoryEntry e) -> e.category.name()).thenComparing(e -> e.name));
    return result;
  }

  /** Laufende Koloniegründungen des Kommandanten – Fortschrittsanzeige im Client. */
  public static List<Colonization> colonizationsOf(GameState state, String playerId) {
    return state.colonizations.stream().filter(c -> c.ownerId.equals(playerId)).toList();
  }

  /** Eigene, im Orbit DIESES Planeten stationierte Flotte mit mindestens einem Kolonisationsschiff. */
  private static Fleet fleetWithColonyShipAt(GameState state, String playerId, Planet planet) {
    for (Fleet f : state.fleets) {
      if (!f.ownerId.equals(playerId) || f.status != FleetStatus.Stationed) continue;
      if (!planet.id.equals(f.locationPlanetId)) continue;
      for (FleetShipGroup g : f.ships) {
        if (GameConstants.COLONY_SHIP_PRODUCT_ID.equals(g.shipProductTypeId) && g.quantity >= 1) return f;
      }
    }
    return null;
  }

  /** Schließt fällige Koloniegründungen ab – aufgerufen aus {@code GameTick}. */
  public static void processColonizations(GameState state, IdGenerator ids, long t) {
    for (Colonization c : state.colonizations.stream().filter(x -> x.endsAt <= t).toList()) {
      state.colonizations.remove(c);
      Planet planet = planet(state, c.planetId);
      if (planet == null) continue;
      Colony colony = foundColony(state, ids, c, t);
      Notifications.notify(state, ids, NotificationType.Info, NOTIFICATION_CODE_COLONY_FOUNDED,
          "Kolonie \"" + colony.name + "\" gegründet – " + (long) GameConstants.START_POPULATION
              + " Kolonisten sind gelandet.", colony.id, null);
    }
  }

  private static final int NOTIFICATION_CODE_COLONY_FOUNDED = 120;

  private static Colony foundColony(GameState state, IdGenerator ids, Colonization request, long t) {
    Planet planet = planet(state, request.planetId);
    Colony colony = new Colony();
    colony.id = ids.next("col");
    colony.planetId = request.planetId;
    colony.systemId = request.systemId;
    colony.ownerId = request.ownerId;
    colony.name = request.colonyName;
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

    // Die Kolonisten des Schiffs SIND die Startbevölkerung – dieselbe Zahl, mit der
    // auch eine Heimatwelt beginnt (Umsetzungskonzept/24_...md).
    double startPopulation = GameConstants.START_POPULATION;
    Population population = new Population();
    population.colonyId = colony.id;
    population.currentCount = startPopulation;
    population.growthRatePerInterval = 0;
    state.populations.add(population);

    boolean moneySupplyExists = state.moneySupplyStates.stream().anyMatch(m -> m.planetId.equals(request.planetId));
    if (!moneySupplyExists) {
      PopulationMoneySupplyState money = new PopulationMoneySupplyState();
      money.planetId = request.planetId;
      money.historicalPeakPopulation = startPopulation;
      money.lastPopulation = startPopulation;
      state.moneySupplyStates.add(money);
    }

    Wallet popWallet = new Wallet();
    popWallet.id = ids.next("wal");
    popWallet.ownerType = WalletOwnerType.Population;
    popWallet.ownerId = colony.id;
    popWallet.balance = startPopulation * Formulas.CREDITS_PER_NEW_INHABITANT;
    state.wallets.add(popWallet);

    // Startbebauung wie die Heimatwelt: Wohnkomplex 1 + Industriekomplex 1 +
    // Infrastruktur 2 – genau die Stufen, deren Baustoffe das Schiff mitbringt.
    for (String[] start : new String[][]{{"b_habitat", "1"}, {"b_industry", "1"}, {GameConstants.INFRASTRUCTURE_BUILDING_ID, "2"}}) {
      Building b = new Building();
      b.id = ids.next("bld");
      b.colonyId = colony.id;
      b.typeId = start[0];
      b.level = Integer.parseInt(start[1]);
      state.buildings.add(b);
    }
    if (planet != null && colony.name == null) colony.name = planet.name + "-Kolonie";
    return colony;
  }
}
