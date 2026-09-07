package de.nebula.data;

import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.Rng;
import de.nebula.model.Building;
import de.nebula.model.ChainPlan;
import de.nebula.model.Colony;
import de.nebula.model.Fleet;
import de.nebula.model.FleetLocationType;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.Gateway;
import de.nebula.model.GatewayDiscoveryState;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.GroundForceUnitStack;
import de.nebula.model.Planet;
import de.nebula.model.PlanetResourceConcentration;
import de.nebula.model.PlanetStats;
import de.nebula.model.PlanetSize;
import de.nebula.model.PlanetType;
import de.nebula.model.Player;
import de.nebula.model.Population;
import de.nebula.model.PopulationMoneySupplyState;
import de.nebula.model.ProductionQueueEntry;
import de.nebula.model.ProductionQueueStatus;
import de.nebula.model.SellOrder;
import de.nebula.model.StarSystem;
import de.nebula.model.TradeLocationType;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;
import de.nebula.model.WarehouseEntry;
import de.nebula.state.IdGenerator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1:1-Portierung von {@code frontend/src/app/core/sim/data/world-seed.ts}.
 * Baut eine komplette, frisch generierte Galaxie ({@link #createWorldSeed})
 * bzw. fügt einen weiteren Kommandanten in eine bestehende ein
 * ({@link #createAdditionalPlayerSeed}) – siehe Umsetzungskonzept/13_...md,
 * Phase 3.
 */
public final class WorldSeed {
  private WorldSeed() {
  }

  private static final ChainPlan EMPTY_CHAIN_PLAN = new ChainPlan(0, List.of(), true);

  /**
   * Grundkonsumgüter, die eine frische Heimatkolonie von Anfang an SELBST
   * produziert (Dauerauftrag mit {@code requeueOnComplete}) UND per
   * Auto-Relist-Verkaufsorder am eigenen Depot anbietet – siehe
   * {@link #starterSellOrders} und Umsetzungskonzept/15_...md, Auftrag 1.
   *
   * <p>Bewusst NUR die beiden billigen Tier-2-Güter aus
   * {@code GameConstants.CONSUMER_GOODS_ORDER}: das dritte
   * ({@code p_unterhaltungselektronik}) ist Tier 5 mit tiefer Vorkette
   * (Steuerchip/Kommchip/Leiterbündel/Polymergrundstoff …) und würde die
   * SEQUENTIELLE Produktionswarteschlange der Kolonie über lange Zeit
   * blockieren und damit ausgerechnet die Nahrungsproduktion aushungern.
   * Diesen Zweig baut der Kommandant selbst auf, sobald seine Industrie
   * es hergibt.</p>
   */
  private static final List<String> STARTER_CONSUMER_GOODS = List.of("p_grundnahrung", "p_grundmedizin");
  private static final double STARTER_CONSUMER_GOODS_QUANTITY = 5;
  /**
   * Stabilisiertes Elerium je Warteschlangen-Umlauf. Seit dem Minimalstart
   * (Umsetzungskonzept/17_...md) dauert ein Umlauf der Startaufträge bei
   * Industriekomplex 1 rund 134 Spielstunden (Grundnahrung 55 h + Grundmedizin
   * 53 h + 3 × 9 h Elerium); Infrastruktur 2 verbraucht in dieser Zeit
   * 0,0119 × 134 ≈ 1,6 Stück. Mit nur 1 Stück je Umlauf (früherer Wert) lief
   * die 25er-Reserve in ≈ 5 Realstunden leer, danach Blackout-Todesspirale
   * (Produktion ×0,1 kann kein Elerium mehr nachliefern). 3 Stück je Umlauf
   * decken den Bedarf bis Infrastruktur 3 (0,0198/h ≈ 2,7 je Umlauf) mit Puffer.
   */
  private static final double STARTER_ELERIUM_QUANTITY = 3;

  /** Gesamter Startbestand je Grundkonsumgut, aufgeteilt in Lager + sofort eingestellte Verkaufsorder. */
  private static final double STARTER_CONSUMER_GOODS_STOCK = 50;
  /**
   * Menge je Start-Verkaufsorder (aus {@link #STARTER_CONSUMER_GOODS_STOCK}
   * reserviert, Rest bleibt im Lager als Puffer für das erste Auto-Relist).
   * {@code EconomyTick.runConsumption} kauft je Tick höchstens
   * {@code ceil(Bedarf)} = 1 Stück je Gut (Startbevölkerung 420 ⇒ Bedarf
   * 0,168 bzw. 0,063 Stück/Tick), 20 Stück puffern also rund 20 Ticks, bevor
   * die Order schlafend wird und aus dem Lager nachgefüllt werden muss.
   */
  private static final double STARTER_SELL_ORDER_QUANTITY = 20;
  /**
   * Preis je Stück – mit Umsetzungskonzept/17_...md, Teil C strukturell
   * hergeleitet. Geld wird im Spiel nicht vernichtet, sondern kreist:
   * Löhne, Gebäude- und Flottenunterhalt fließen vom Spieler- ins
   * Bevölkerungs-Wallet, zurück kommt es NUR über den Konsum. Neues Geld
   * entsteht ausschließlich beim Bevölkerungswachstum über den bisherigen
   * Höchststand ({@code CREDITS_PER_NEW_INHABITANT}); am Wohnraum-Limit
   * versiegt diese Quelle. Im Gleichgewicht muss der Konsum die Abflüsse
   * deshalb EXAKT decken – ein dauerhafter Überschuss der einen Seite ist
   * zwangsläufig das Verarmen der anderen.
   *
   * <p>Bilanz je Tick ({@code TICK_GAME_HOURS} = 0,4): Einnahme =
   * {@code Bevölkerung × (0,00008 + 0,00004) × Preis} (nur Grundnahrung und
   * Grundmedizin werden geliefert), Abfluss = Löhne
   * {@code Bevölkerung × 0,008} + Gebäudeunterhalt (Wohnkomplex 1,5 +
   * Infrastruktur 2×1,5 + Industrie 3,0 = 7,5/Spielstunde ⇒ 3,0) +
   * Flottenunterhalt (0,2 je Schiff an der Kolonie). Gleichgewichtspreis
   * {@code P* = (0,008·Bev + 3,0 + 0,2·Schiffe) / (0,00012·Bev)}: bei der
   * eingeschwungenen Bevölkerung (Wohnkomplex 1 ⇒ 200 Einwohner) und der
   * Startflotte (13 Schiffe) sind das <b>300 Credits/Stück</b>.</p>
   *
   * <p>Während der Aufbauphase (120 → 200 Einwohner) liegt der
   * Gleichgewichtspreis höher (456 bei 120), die Kolonie macht dort also ein
   * kleines, sich selbst korrigierendes Minus von ≈ 600 Credits – gedeckt aus
   * dem Startguthaben und dem gleichzeitig geschöpften Wachstumsgeld
   * (80 × 8 = 640 Cr). Verworfene Werte: 20 (Spieler-Wallet nach ≈ 20
   * Realminuten leer) und 500 (Spieler +3,1/Tick, dafür Bevölkerungs-Wallet
   * 1865 → 263 in 10 Minuten – dieselbe Krankheit mit vertauschten Rollen).</p>
   */
  private static final double STARTER_SELL_ORDER_PRICE = 300;

  private static final double SEALED_ELERIUM_RESERVE_HOME = 25;

  private static final List<String> PLANET_NAMES_HOME =
      List.of("Aurelia Prime", "Kessar", "Vantis", "Thal Minor", "Rho Cindra");

  private static final int GALAXY_SYSTEM_COUNT = 200;

  private static final List<String> SYSTEM_NAME_POOL = List.of(
      "Aurelia", "Kepler's Reach", "Thessaly", "Drakon-Weite", "Vey Corva", "Halcyon Rand",
      "Praxis Gate", "Corvin Öde", "Nashira", "Talvex", "Ophir Rand", "Sirenum",
      "Kestrel-Feld", "Meridian Tor", "Borea Vor", "Xantha", "Rigel Außenposten",
      "Vantor Bogen", "Elyra Senke", "Cassiel", "Drift von Ilun", "Perath",
      "Solace Rand", "Nocturn Bucht", "Amaris", "Kroven Riff", "Vela Passage",
      "Tessark", "Orinth", "Fahrun Weite");

  private static final List<String> FACTION_FLAVORS = List.of(
      "unabhängige Kolonisten", "Grenzsiedlung", "unerforscht", "kleine Kolonie",
      "verlassenes System", "lokale Miliz");

  private static final List<PlanetType> PLANET_TYPES = List.of(
      PlanetType.TemperierterBiosphaerenplanet, PlanetType.Silikatplanet, PlanetType.Wuestenplanet,
      PlanetType.Ozeanplanet, PlanetType.Eisplanet, PlanetType.Vulkanplanet, PlanetType.Metallplanet,
      PlanetType.Kohlenstoffplanet, PlanetType.Supererde, PlanetType.Planetoid, PlanetType.Gasriese,
      PlanetType.Eisriese, PlanetType.Schwefelplanet);

  private static final List<PlanetSize> PLANET_SIZES =
      List.of(PlanetSize.Klein, PlanetSize.Mittel, PlanetSize.Groß, PlanetSize.Riesig);

  /** Heimatplanet-Mindestfördergüten (Nebula_Planetentypen_..., §8). */
  private static final Map<String, Double> HOMEWORLD_MINIMUMS = Map.ofEntries(
      Map.entry("res_eis", 80.0), Map.entry("res_atmosphaere", 80.0), Map.entry("res_salz", 60.0),
      Map.entry("res_kohlenstoff", 50.0), Map.entry("res_silikat", 45.0), Map.entry("res_leichtmetall", 30.0),
      Map.entry("res_kohlenwasserstoff", 30.0), Map.entry("res_ferrometall", 25.0), Map.entry("res_leitmetall", 15.0),
      Map.entry("res_technometall", 12.0), Map.entry("res_elerium", 15.0));

  private static List<ProductionQueueEntry> starterProductionQueue(String colonyId, IdGenerator ids) {
    record Entry(String productTypeId, double quantity) {
    }
    List<Entry> entries = new ArrayList<>();
    for (String productTypeId : STARTER_CONSUMER_GOODS) {
      entries.add(new Entry(productTypeId, STARTER_CONSUMER_GOODS_QUANTITY));
    }
    entries.add(new Entry("p_elerium_stabil", STARTER_ELERIUM_QUANTITY));

    List<ProductionQueueEntry> result = new ArrayList<>();
    for (Entry e : entries) {
      ProductionQueueEntry q = new ProductionQueueEntry();
      q.id = ids.next("pq");
      q.colonyId = colonyId;
      q.productTypeId = e.productTypeId();
      q.quantity = e.quantity();
      q.autoProduceMissing = true;
      q.requeueOnComplete = true;
      q.status = ProductionQueueStatus.queued;
      q.stoppedReasonCode = null;
      q.plan = EMPTY_CHAIN_PLAN;
      q.startedAt = null;
      q.endsAt = null;
      result.add(q);
    }
    return result;
  }

  private static WarehouseEntry eleriumReserveEntry(String colonyId, double quantity) {
    WarehouseEntry e = new WarehouseEntry();
    e.colonyId = colonyId;
    e.productTypeId = "p_elerium_stabil";
    e.quantity = quantity;
    return e;
  }

  /**
   * Wiederkehrende Verkaufsorders ({@code autoRelist}) für die
   * Grundkonsumgüter am Depot der eigenen Heimatkolonie. Ohne sie hat die
   * Bevölkerung NICHTS zu kaufen: {@code EconomyTick.runConsumption} kauft
   * ausschließlich aus {@code state.sellOrders} und kann NICHT direkt aus dem
   * Kolonielager essen – die Folge wäre Versorgung 0 ⇒ Lebensstandard 0 % ⇒
   * {@code growthConditionFactor} bei 0,15 ⇒ Loyalitätsverfall, und das
   * Spieler-Wallet kennte ausschließlich Abflüsse (Review-Befund, siehe
   * Umsetzungskonzept/15_...md, Auftrag 1). Mit ihnen schließt sich der
   * Kreislauf: Produktion → Lager → Verkaufsorder → Bevölkerung kauft →
   * Spieler verdient; das Auto-Relist füllt die Order jeden Tick aus dem
   * nachproduzierten Lagerbestand wieder auf
   * ({@code MarketCommands.replenishDormantSellOrders}).
   */
  private static List<SellOrder> starterSellOrders(String colonyId, String systemId, String sellerId,
                                                     String sellerName, long t, IdGenerator ids) {
    List<SellOrder> orders = new ArrayList<>();
    for (String productTypeId : STARTER_CONSUMER_GOODS) {
      SellOrder o = new SellOrder();
      o.id = ids.next("so");
      o.systemId = systemId;
      o.locationType = TradeLocationType.Depot;
      o.depotColonyId = colonyId;
      o.sellerId = sellerId;
      o.sellerName = sellerName;
      o.productTypeId = productTypeId;
      o.quantity = STARTER_SELL_ORDER_QUANTITY;
      o.remainingQuantity = STARTER_SELL_ORDER_QUANTITY;
      o.pricePerUnit = STARTER_SELL_ORDER_PRICE;
      o.createdAt = t;
      o.autoRelist = true;
      o.sourceFleetId = null;
      orders.add(o);
    }
    return orders;
  }

  /** Bei mehr Systemen als Namen im Pool hängt ein Zähler an, statt exakte Namensdopplungen zu erzeugen. */
  private static String systemNameAt(List<String> names, int i) {
    String base = names.get(i % names.size());
    int cycle = i / names.size();
    return cycle == 0 ? base : base + " " + (cycle + 1);
  }

  private static String pickAdditionalSystemName(Set<String> usedNames, Rng rnd) {
    List<String> strictlyAvailable = SYSTEM_NAME_POOL.stream().filter(n -> !usedNames.contains(n)).toList();
    if (!strictlyAvailable.isEmpty()) {
      return strictlyAvailable.get((int) Math.floor(rnd.next() * strictlyAvailable.size()));
    }
    String base = SYSTEM_NAME_POOL.get((int) Math.floor(rnd.next() * SYSTEM_NAME_POOL.size()));
    int cycle = 2;
    while (usedNames.contains(base + " " + cycle)) cycle++;
    return base + " " + cycle;
  }

  private static Building buildInstance(String colonyId, String typeId, int level, IdGenerator ids) {
    Building b = new Building();
    b.id = ids.next("bld");
    b.colonyId = colonyId;
    b.typeId = typeId;
    b.level = level;
    return b;
  }

  private static Fleet freighterFleet(String ownerId, String colonyId, String planetId, String systemId,
                                       String name, IdGenerator ids) {
    Fleet f = new Fleet();
    f.id = ids.next("flt");
    f.ownerId = ownerId;
    f.name = name;
    f.locationType = FleetLocationType.ColonyOrbit;
    f.locationColonyId = colonyId;
    f.locationPlanetId = planetId;
    f.systemId = systemId;
    f.status = FleetStatus.Stationed;
    f.ships = List.of(new FleetShipGroup("p_freighter", 1));
    f.cargo = List.of();
    f.destinationSystemId = null;
    f.pendingHops = List.of();
    f.departedAt = null;
    f.arrivesAt = null;
    return f;
  }

  private static int randInt(Rng rnd, int min, int max) {
    return min + (int) Math.floor(rnd.next() * (max - min + 1));
  }

  /**
   * Startflotte mit ALLEN drei Kampfklassen (Korvette/Zerstörer/Kreuzer, siehe
   * Mechanik/03_..., §2) – deckt den vollen Konterkreis ab. Stückzahlen je
   * Klasse bewusst zufällig und deutlich unterschiedlich (Korvette 2-8,
   * Zerstörer 1-5, Kreuzer 1-3), damit sich der Kontermultiplikator
   * (Mechanik/04_..., §4) beim Testen eines Gefechts beobachten lässt.
   */
  private static Fleet combatFleet(String ownerId, String colonyId, String planetId, String systemId,
                                    String name, Rng rnd, IdGenerator ids) {
    Fleet f = new Fleet();
    f.id = ids.next("flt");
    f.ownerId = ownerId;
    f.name = name;
    f.locationType = FleetLocationType.ColonyOrbit;
    f.locationColonyId = colonyId;
    f.locationPlanetId = planetId;
    f.systemId = systemId;
    f.status = FleetStatus.Stationed;
    f.ships = List.of(
        new FleetShipGroup("p_corvette", randInt(rnd, 2, 8)),
        new FleetShipGroup("p_destroyer", randInt(rnd, 1, 5)),
        new FleetShipGroup("p_cruiser", randInt(rnd, 1, 3)));
    f.cargo = List.of();
    f.destinationSystemId = null;
    f.pendingHops = List.of();
    f.departedAt = null;
    f.arrivesAt = null;
    return f;
  }

  /**
   * Start-Garnison (Umsetzungskonzept/19_...md): 2 Soldaten und 10 leichte
   * Drohnen – exakt die Kommandokapazität der beiden Soldaten
   * ({@code DRONES_PER_SOLDIER = 5}), alle zehn Drohnen sind also von Anfang
   * an geführt und einsatzbereit. Bewusst nur eine Drohnenklasse: der volle
   * Konterkreis ist Sache des Spielers, nicht der Startausstattung.
   */
  private static GroundForceGroup starterGroundForceGroup(String ownerId, String colonyId, IdGenerator ids) {
    GroundForceGroup g = new GroundForceGroup();
    g.id = ids.next("gfg");
    g.ownerId = ownerId;
    g.colonyId = colonyId;
    g.units = new ArrayList<>();
    g.units.add(stack("p_soldier", 2, 0));
    g.units.add(stack("p_drone_light", 10, 0));
    return g;
  }

  private static GroundForceUnitStack stack(String unitProductTypeId, int activeCount, int reserveCount) {
    GroundForceUnitStack s = new GroundForceUnitStack();
    s.unitProductTypeId = unitProductTypeId;
    s.activeCount = activeCount;
    s.reserveCount = reserveCount;
    return s;
  }

  private static PlanetType randomPlanetType(Rng rnd) {
    return PLANET_TYPES.get((int) Math.floor(rnd.next() * PLANET_TYPES.size()));
  }

  /**
   * Fördergüte-Profil (0-100 je Rohstoff) aus dem Fördergüte-Bereich des
   * Planetentyps, siehe Nebula_Planetentypen_Rohstoffprofile_Produktionsbaum.md,
   * §7.1. Vereinfachung ggü. Vorlage: der "Clusterwert" wird hier je Planet
   * statt je Sternsystem gezogen – die dort beschriebene Zwei-Phasen-Erzeugung
   * mit regionalem Cluster, Nachbarschaftsvalidierung und Signatur-/
   * Mangelrohstoffen (§7.2-7.3) ist noch nicht umgesetzt.
   */
  private static List<PlanetResourceConcentration> concentrationProfileForType(PlanetType type, Rng rnd) {
    List<PlanetTypeProfiles.ResourceRange> ranges = PlanetTypeProfiles.rangesForPlanetType(type);
    List<PlanetResourceConcentration> result = new ArrayList<>();
    for (PlanetTypeProfiles.ResourceRange rr : ranges) {
      double min = rr.range().min();
      double max = rr.range().max();
      double clusterValue = rnd.next() * 100;
      double localRandom = rnd.next();
      double localDeviation = rnd.next() * 10 - 5;
      double typwert = min + (max - min) * (0.65 * (clusterValue / 100) + 0.35 * localRandom);
      double foerdergute = Math.round(Math.max(min, Math.min(max, typwert + localDeviation)));
      PlanetResourceConcentration c = new PlanetResourceConcentration();
      c.resourceTypeId = rr.resourceTypeId();
      c.concentration = foerdergute;
      result.add(c);
    }
    return result;
  }

  private static List<PlanetResourceConcentration> applyHomeworldMinimums(List<PlanetResourceConcentration> conc) {
    List<PlanetResourceConcentration> result = new ArrayList<>();
    for (PlanetResourceConcentration c : conc) {
      PlanetResourceConcentration copy = new PlanetResourceConcentration();
      copy.resourceTypeId = c.resourceTypeId;
      copy.concentration = Math.max(c.concentration, HOMEWORLD_MINIMUMS.getOrDefault(c.resourceTypeId, 0.0));
      result.add(copy);
    }
    return result;
  }

  private record HomeworldBundle(Player player, List<Planet> planets, Colony colony, PlanetStats planetStats,
                                  Population population, PopulationMoneySupplyState moneySupplyState,
                                  List<Wallet> wallets, List<Building> buildings, List<WarehouseEntry> warehouse,
                                  List<ProductionQueueEntry> productionQueue, List<Fleet> fleets,
                                  GroundForceGroup groundForceGroup, List<SellOrder> sellOrders) {
  }

  /**
   * Baut EINEN Kommandanten samt Heimatplaneten-Cluster, Startkolonie,
   * Gebäuden, Wallets und Start-Auftragsliste – unabhängig davon, ob das
   * zugehörige Heimatsystem Teil einer brandneuen Galaxie ist
   * ({@link #createWorldSeed}) oder nachträglich in eine bestehende eingefügt
   * wird ({@link #createAdditionalPlayerSeed}).
   */
  private static HomeworldBundle buildHomeworldBundle(String commanderName, String homeworldName, String homeSystemId,
                                                        Rng rnd, long t, IdGenerator ids) {
    Player player = new Player();
    player.id = ids.next("ply");
    player.name = commanderName;
    player.homeSystemId = homeSystemId;
    player.homeworldColonyId = "";
    player.createdAt = t;

    List<Planet> planets = new ArrayList<>();
    for (int i = 0; i < PLANET_NAMES_HOME.size(); i++) {
      String name = PLANET_NAMES_HOME.get(i);
      // Der Spieler startet auf einem temperierten Biosphärenplaneten
      // (Nebula_Planetentypen_..., §8) – die übrigen Himmelskörper im
      // Heimatsystem sind zunächst unbesiedelt und dürfen beliebige Typen sein.
      PlanetType planetType = i == 0 ? PlanetType.TemperierterBiosphaerenplanet : randomPlanetType(rnd);
      List<PlanetResourceConcentration> conc = concentrationProfileForType(planetType, rnd);
      if (i == 0) conc = applyHomeworldMinimums(conc);

      Planet planet = new Planet();
      planet.id = ids.next("pla");
      planet.systemId = homeSystemId;
      planet.name = name;
      planet.size = PLANET_SIZES.get((int) Math.floor(rnd.next() * 4));
      planet.type = planetType;
      planet.resourceConcentration = conc;
      planet.orbitIndex = i;
      planets.add(planet);
    }

    Colony colony = new Colony();
    colony.id = ids.next("col");
    colony.planetId = planets.get(0).id;
    colony.systemId = homeSystemId;
    colony.ownerId = player.id;
    colony.name = homeworldName;
    colony.foundedAt = t;
    colony.isHomeworld = true;
    player.homeworldColonyId = colony.id;

    // Minimalstart (Umsetzungskonzept/17_...md): 120 Einwohner in einem
    // Wohnkomplex Stufe 1 (Kapazität 20.000 – der Wohnraum ist im Frühspiel
    // bewusst NICHT die Grenze; begrenzend ist die Nahrungsversorgung, die
    // Bevölkerung plateauiert rechnerisch bei ≈ 390), Industriekomplex
    // Stufe 1 und Infrastruktur Stufe 2 (beide Bebauungsplätze belegt, keiner
    // frei). Keine Baustoffe im Lager – jeder weitere Ausbau kostet Baustoffe,
    // die der Industriekomplex Stufe 1 selbst produzieren muss; der erste Zug
    // ist deshalb zwangsläufig Infrastruktur → 3.
    double homePopulationCount = 120;
    int homeHabitatLevel = 1;
    int homeIndustryLevel = 1;
    int homeInfrastructureLevel = 2;

    PlanetStats planetStats = new PlanetStats();
    planetStats.colonyId = colony.id;
    planetStats.infrastructurePct = 110;
    planetStats.securityPct = Formulas.MAX_SECURITY_PCT; // es gibt keine absolute Sicherheit (Umsetzungskonzept/19_...md)
    planetStats.standardOfLivingPct = 100;
    planetStats.loyaltyPct = 78;
    planetStats.lastRecalculatedAt = t;

    Population population = new Population();
    population.colonyId = colony.id;
    population.currentCount = homePopulationCount;
    population.growthRatePerInterval = 0;

    PopulationMoneySupplyState moneySupplyState = new PopulationMoneySupplyState();
    moneySupplyState.planetId = planets.get(0).id;
    moneySupplyState.historicalPeakPopulation = homePopulationCount;
    moneySupplyState.lastPopulation = homePopulationCount;

    Wallet playerWallet = new Wallet();
    playerWallet.id = ids.next("wal");
    playerWallet.ownerType = WalletOwnerType.Player;
    playerWallet.ownerId = player.id;
    playerWallet.balance = 6500;

    Wallet popWallet = new Wallet();
    popWallet.id = ids.next("wal");
    popWallet.ownerType = WalletOwnerType.Population;
    popWallet.ownerId = colony.id;
    popWallet.balance = 900;

    List<Building> buildings = new ArrayList<>();
    buildings.add(buildInstance(colony.id, "b_habitat", homeHabitatLevel, ids));
    buildings.add(buildInstance(colony.id, "b_infrastructure", homeInfrastructureLevel, ids));
    buildings.add(buildInstance(colony.id, "b_industry", homeIndustryLevel, ids));
    // Bewusst KEINE Werft und KEIN Ausbildungszentrum mehr (Nutzerentscheidung,
    // Umsetzungskonzept/17_...md). Der frühere Start mit Industrie 4/Werft 3
    // aus Dokument 12 gilt damit nicht mehr, siehe dort.

    List<WarehouseEntry> warehouse = new ArrayList<>();
    warehouse.add(eleriumReserveEntry(colony.id, SEALED_ELERIUM_RESERVE_HOME));
    // Startbestand je Grundkonsumgut: der in die Start-Verkaufsorder
    // reservierte Teil liegt NICHT mehr im Lager (gleiche Buchführung wie
    // MarketCommands.createSellOrderCore), der Rest bleibt als Puffer für das
    // erste Auto-Relist liegen.
    for (String productTypeId : STARTER_CONSUMER_GOODS) {
      WarehouseEntry w = new WarehouseEntry();
      w.colonyId = colony.id;
      w.productTypeId = productTypeId;
      w.quantity = STARTER_CONSUMER_GOODS_STOCK - STARTER_SELL_ORDER_QUANTITY;
      warehouse.add(w);
    }

    // Frachter ab Spielbeginn – ohne eigene Transportkapazität ist kein Handel
    // über die eigene Kolonie hinaus möglich (Konzeption/05_..., §9).
    Fleet freighter = freighterFleet(player.id, colony.id, colony.planetId, homeSystemId, "Handelsflotte " + colony.name, ids);
    // Kampfflotte mit allen drei Klassen, stark unterschiedliche Stückzahlen
    // je Klasse – ermöglicht sofortiges Ausprobieren des Kampfsystems inkl.
    // Kontermultiplikator ohne erst eine Werft hochziehen zu müssen.
    Fleet combat = combatFleet(player.id, colony.id, colony.planetId, homeSystemId, "Kampfflotte " + colony.name, rnd, ids);
    GroundForceGroup groundForceGroup = starterGroundForceGroup(player.id, colony.id, ids);

    return new HomeworldBundle(player, planets, colony, planetStats, population, moneySupplyState,
        List.of(playerWallet, popWallet), buildings, warehouse, starterProductionQueue(colony.id, ids),
        List.of(freighter, combat), groundForceGroup,
        starterSellOrders(colony.id, homeSystemId, player.id, player.name, t, ids));
  }

  /** Ergebnis von {@link #createWorldSeed}: eine komplett neu generierte Galaxie samt erstem Kommandanten. */
  public static class Seed {
    public Player player;
    public List<StarSystem> systems;
    public List<Planet> planets;
    public List<Colony> colonies;
    public List<PlanetStats> planetStats;
    public List<Population> populations;
    public List<PopulationMoneySupplyState> moneySupplyStates;
    public List<Wallet> wallets;
    public List<Building> buildings;
    public List<WarehouseEntry> warehouse;
    public List<ProductionQueueEntry> productionQueue;
    public List<Gateway> gateways;
    public List<Fleet> fleets;
    public List<GroundForceGroup> groundForceGroups;
    /** Start-Verkaufsorders für Grundkonsumgüter, siehe {@link #starterSellOrders}. */
    public List<SellOrder> sellOrders;
  }

  public static Seed createWorldSeed(String commanderName, String homeworldName, IdGenerator ids) {
    Rng rnd = Rng.seeded(1337);
    long t = Clock.now();

    // --- Galaxie-Topologie ---------------------------------------------------
    GalaxyGenerator.GeneratedGalaxy galaxy = GalaxyGenerator.generateGalaxy(GALAXY_SYSTEM_COUNT, rnd);
    List<String> names = Rng.shuffle(SYSTEM_NAME_POOL, rnd);
    List<String> systemIds = new ArrayList<>();
    List<String> gatewayIds = new ArrayList<>();
    for (int i = 0; i < galaxy.positions().size(); i++) {
      systemIds.add(ids.next("sys"));
      gatewayIds.add(ids.next("gw"));
    }
    Set<Integer> tradeHubSet = new LinkedHashSet<>(galaxy.tradeHubIndices());
    int homeIndex = galaxy.centralIndex();

    HomeworldBundle home = buildHomeworldBundle(commanderName, homeworldName, systemIds.get(homeIndex), rnd, t, ids);

    List<StarSystem> systems = new ArrayList<>();
    for (int i = 0; i < galaxy.positions().size(); i++) {
      boolean isHome = i == homeIndex;
      boolean isHub = tradeHubSet.contains(i);
      StarSystem s = new StarSystem();
      s.id = systemIds.get(i);
      s.name = isHome ? "Aurelia-System" : systemNameAt(names, i);
      s.x = galaxy.positions().get(i).x();
      s.y = galaxy.positions().get(i).y();
      s.planetIds = isHome ? home.planets().stream().map(p -> p.id).toList() : new ArrayList<>();
      s.gatewayId = gatewayIds.get(i);
      s.isHomeSystem = isHome;
      s.isTradeHub = isHub;
      s.factionFlavor = isHome ? "Heimatsystem"
          : isHub ? "Sektorale Handelsstation (Handelsgilde)"
          : FACTION_FLAVORS.get((int) Math.floor(rnd.next() * FACTION_FLAVORS.size()));
      systems.add(s);
    }

    // --- Gateways --------------------------------------------------------------
    // ALLE Gateways starten von Anfang an uneingeschränkt aktiv – kein
    // Erforschen/Entdecken/Aktivieren mehr nötig (bewusste Vereinfachung, siehe
    // TS-Original): Kommandanten sollen von Beginn an frei mit ihren Flotten
    // durchs gesamte bekannte Netz reisen können.
    List<Gateway> gateways = new ArrayList<>();
    for (int i = 0; i < galaxy.positions().size(); i++) {
      Gateway g = new Gateway();
      g.id = gatewayIds.get(i);
      g.systemId = systemIds.get(i);
      g.state = GatewayDiscoveryState.Active;
      g.discoveredAt = t;
      g.activatedAt = t;
      g.activatingCompletesAt = null;
      List<String> reachable = new ArrayList<>();
      for (int j : galaxy.neighbors().get(i)) reachable.add(systemIds.get(j));
      g.reachableSystemIds = reachable;
      gateways.add(g);
    }

    Seed seed = new Seed();
    seed.player = home.player();
    seed.systems = systems;
    seed.planets = home.planets();
    seed.colonies = List.of(home.colony());
    seed.planetStats = List.of(home.planetStats());
    seed.populations = List.of(home.population());
    seed.moneySupplyStates = List.of(home.moneySupplyState());
    seed.wallets = home.wallets();
    seed.buildings = home.buildings();
    seed.warehouse = home.warehouse();
    seed.productionQueue = home.productionQueue();
    seed.fleets = home.fleets();
    seed.groundForceGroups = List.of(home.groundForceGroup());
    seed.sellOrders = home.sellOrders();
    seed.gateways = gateways;
    return seed;
  }

  /** Ergebnis von {@link #createAdditionalPlayerSeed}: ein weiterer Kommandant in einer bestehenden Galaxie. */
  public static class AdditionalSeed {
    public Player player;
    public StarSystem newSystem;
    public Gateway newGateway;
    public String linkedSystemId;
    public List<Planet> planets;
    public Colony colony;
    public PlanetStats planetStats;
    public Population population;
    public PopulationMoneySupplyState moneySupplyState;
    public List<Wallet> wallets;
    public List<Building> buildings;
    public List<WarehouseEntry> warehouse;
    public List<ProductionQueueEntry> productionQueue;
    public List<Fleet> fleets;
    public GroundForceGroup groundForceGroup;
    /** Start-Verkaufsorders für Grundkonsumgüter, siehe {@link #starterSellOrders}. */
    public List<SellOrder> sellOrders;
  }

  /**
   * Fügt EINEN weiteren Kommandanten in eine bereits bestehende Galaxie ein
   * ("Registrieren"): bringt dabei ein komplett neues Heimatsystem samt
   * Heimatplaneten-Cluster mit, nach demselben Muster wie das allererste
   * Heimatsystem in {@link #createWorldSeed}. Andere Kommandanten, Systeme
   * und der Markt bleiben unangetastet.
   *
   * <p>Das neue System startet – wie jedes Heimatsystem – mit uneingeschränkt
   * aktivem Gateway und wird kartografisch mit dem nächstgelegenen
   * bestehenden System verbunden. Kein Versuch, es korrekt in das
   * ursprüngliche Nachbarschaftsnetz von {@code generateGalaxy} einzuflechten
   * – für den Prototyp reicht eine einzelne Verbindung.</p>
   */
  public static AdditionalSeed createAdditionalPlayerSeed(List<StarSystem> existingSystems, String commanderName,
                                                            String homeworldName, IdGenerator ids) {
    Rng rnd = () -> Math.random();
    long t = Clock.now();

    GalaxyGenerator.Point position = pickIsolatedPosition(existingSystems, rnd);
    StarSystem linkedSystem = nearestSystem(existingSystems, position);

    String systemId = ids.next("sys");
    String gatewayId = ids.next("gw");
    HomeworldBundle home = buildHomeworldBundle(commanderName, homeworldName, systemId, rnd, t, ids);

    Set<String> usedNames = new LinkedHashSet<>();
    for (StarSystem s : existingSystems) usedNames.add(s.name);
    String systemName = pickAdditionalSystemName(usedNames, rnd);

    StarSystem newSystem = new StarSystem();
    newSystem.id = systemId;
    newSystem.name = systemName;
    newSystem.x = position.x();
    newSystem.y = position.y();
    newSystem.planetIds = home.planets().stream().map(p -> p.id).toList();
    newSystem.gatewayId = gatewayId;
    newSystem.isHomeSystem = true;
    newSystem.isTradeHub = false;
    newSystem.factionFlavor = "Heimatsystem";

    Gateway newGateway = new Gateway();
    newGateway.id = gatewayId;
    newGateway.systemId = systemId;
    newGateway.state = GatewayDiscoveryState.Active; // siehe createWorldSeed: alle Gateways starten uneingeschränkt aktiv
    newGateway.discoveredAt = t;
    newGateway.activatedAt = t;
    newGateway.activatingCompletesAt = null;
    newGateway.reachableSystemIds = List.of(linkedSystem.id);

    AdditionalSeed seed = new AdditionalSeed();
    seed.player = home.player();
    seed.newSystem = newSystem;
    seed.newGateway = newGateway;
    seed.linkedSystemId = linkedSystem.id;
    seed.planets = home.planets();
    seed.colony = home.colony();
    seed.planetStats = home.planetStats();
    seed.population = home.population();
    seed.moneySupplyState = home.moneySupplyState();
    seed.wallets = home.wallets();
    seed.buildings = home.buildings();
    seed.warehouse = home.warehouse();
    seed.productionQueue = home.productionQueue();
    seed.fleets = home.fleets();
    seed.groundForceGroup = home.groundForceGroup();
    seed.sellOrders = home.sellOrders();
    return seed;
  }

  /**
   * Probiert mehrere zufällige Kandidatenpunkte und behält den mit dem
   * größten Mindestabstand zu allen bestehenden Systemen – einfache
   * räumliche Streuung ohne den Anspruch der Poisson-Disk-Platzierung aus
   * {@code GalaxyGenerator} (dort für eine feste Systemanzahl vorab
   * optimiert, hier für einen einzelnen, jederzeit nachträglich eingefügten
   * Punkt unnötig).
   */
  private static GalaxyGenerator.Point pickIsolatedPosition(List<StarSystem> existingSystems, Rng rnd) {
    double margin = 0.08;
    GalaxyGenerator.Point best = new GalaxyGenerator.Point(0.5, 0.5);
    double bestMinDist = -1;
    for (int attempt = 0; attempt < 40; attempt++) {
      GalaxyGenerator.Point candidate = new GalaxyGenerator.Point(
          margin + rnd.next() * (1 - 2 * margin), margin + rnd.next() * (1 - 2 * margin));
      double minDist = Double.POSITIVE_INFINITY;
      for (StarSystem s : existingSystems) {
        minDist = Math.min(minDist, Math.hypot(candidate.x() - s.x, candidate.y() - s.y));
      }
      if (minDist > bestMinDist) {
        bestMinDist = minDist;
        best = candidate;
      }
    }
    return best;
  }

  private static StarSystem nearestSystem(List<StarSystem> systems, GalaxyGenerator.Point pos) {
    StarSystem nearest = systems.get(0);
    for (StarSystem s : systems) {
      if (Math.hypot(pos.x() - s.x, pos.y() - s.y) < Math.hypot(pos.x() - nearest.x, pos.y() - nearest.y)) {
        nearest = s;
      }
    }
    return nearest;
  }
}
