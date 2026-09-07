package de.nebula.data;

import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
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
import de.nebula.model.PlayerRole;
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
import java.util.LinkedHashMap;
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
   * <p>Bewusst NUR Grundnahrung (Umsetzungskonzept/20_...md): Grundmedizin
   * und Unterhaltungselektronik lässt das Spiel jetzt komplett dem
   * Kommandanten. Zwei mitproduzierte Güter füllten die SEQUENTIELLE
   * Produktionswarteschlange der Kolonie dauerhaft, sodass nie Freiraum
   * blieb, um für Grundnahrung eigene Spezialisierungsstufen aufzubauen –
   * genau das soll die Startphase jetzt ermöglichen.</p>
   */
  private static final List<String> STARTER_CONSUMER_GOODS = List.of("p_grundnahrung");
  private static final double STARTER_CONSUMER_GOODS_QUANTITY = 5;
  /**
   * Anzahl Startaufträge, auf die {@link #STARTER_CONSUMER_GOODS_QUANTITY}
   * je Startkonsumgut aufgeteilt wird (Umsetzungskonzept/20_...md): zwei
   * Aufträge zu je der halben Menge statt einem großen – der
   * Gesamtdurchsatz der sequentiellen Warteschlange bleibt gleich, aber die
   * erste Charge liegt bereits nach der halben Zeit im Lager, statt dass der
   * Spieler auf den kompletten Auftrag warten muss.
   */
  private static final int STARTER_CONSUMER_GOODS_ORDER_SPLIT = 2;
  /**
   * Stabilisiertes Elerium je Warteschlangen-Umlauf. Seit dem Minimalstart
   * (Umsetzungskonzept/17_...md) dauerte ein Umlauf der Startaufträge bei
   * Industriekomplex 1 rund 134 Spielstunden (Grundnahrung 55 h + Grundmedizin
   * 53 h + 3 × 9 h Elerium); Infrastruktur 2 verbraucht in dieser Zeit
   * 0,0119 × 134 ≈ 1,6 Stück. Seit Umsetzungskonzept/20_...md entfällt der
   * Grundmedizin-Block, ein Umlauf ist also kürzer und der bisherige Puffer
   * reicht mit Reserve weiter: mit nur 1 Stück je Umlauf (früherer Wert vor
   * Konzept 17) lief die 25er-Reserve in ≈ 5 Realstunden leer, danach
   * Blackout-Todesspirale (Produktion ×0,1 kann kein Elerium mehr
   * nachliefern). 3 Stück je Umlauf decken den Bedarf bis Infrastruktur 3
   * (0,0198/h ≈ 2,7 je Umlauf) mit Puffer.
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
   * {@code Bevölkerung × 0,00008 × Preis} (seit Umsetzungskonzept/20_...md
   * wird NUR NOCH Grundnahrung geliefert, Grundmedizin baut der Spieler
   * selbst auf), Abfluss = Löhne {@code Bevölkerung × 0,008} +
   * Gebäudeunterhalt (Wohnkomplex 1,5 + Infrastruktur 2×1,5 + Industrie
   * 3,0 = 7,5/Spielstunde ⇒ 3,0) + Flottenunterhalt (0,2 je Schiff an der
   * Kolonie). Gleichgewichtspreis
   * {@code P* = (0,008·Bev + 3,0 + 0,2·Schiffe) / (0,00008·Bev)}: bei der
   * eingeschwungenen Bevölkerung (Wohnkomplex 1 ⇒ 200 Einwohner) und der
   * Startflotte (13 Schiffe) sind das <b>450 Credits/Stück</b> (vor Konzept
   * 20, mit zusätzlichem Grundmedizin-Erlös: 300).</p>
   *
   * <p>Während der Aufbauphase (120 → 200 Einwohner) liegt der
   * Gleichgewichtspreis höher, die Kolonie macht dort also ein kleines,
   * sich selbst korrigierendes Minus – gedeckt aus dem Startguthaben und dem
   * gleichzeitig geschöpften Wachstumsgeld (80 × 8 = 640 Cr).</p>
   *
   * <p><b>Bekannte Folgewirkung von Konzept 20:</b> {@code EconomyTick.
   * runConsumption} gewichtet Grundnahrung doppelt so hoch wie die übrigen
   * Konsumgüter bei der Lebensstandard-Berechnung. Ohne Grundmedizin-
   * Versorgung startet der Lebensstandard einer frischen Kolonie deshalb bei
   * rund 50 % statt vorher 75 % – gewollt, das ist der Anreiz, die
   * Grundmedizin-Kette selbst aufzubauen.</p>
   */
  private static final double STARTER_SELL_ORDER_PRICE = 450;

  private static final double SEALED_ELERIUM_RESERVE_HOME = 25;

  /**
   * Start-Vorrat an Eleriumkapseln ({@code GameConstants.JUMP_FUEL_PRODUCT_ID}), die jeder
   * Sprung einer Flotte verbraucht (siehe {@code FleetCommands.moveFleet}). Zehn Kapseln
   * reichen für rund 1000 Schiff-Sprünge (10 / 0,01) – ein neuer Kommandant mit wenigen
   * Schiffen kommt damit lange ohne eigene Kapselproduktion aus, während eine große Flotte
   * aus vielen Frachtern den Verbrauch schnell spürt.
   */
  private static final double STARTER_JUMP_FUEL_QUANTITY = 10;

  private static final List<String> PLANET_NAMES_HOME =
      List.of("Aurelia Prime", "Kessar", "Vantis", "Thal Minor", "Rho Cindra");

  /** Bahn-Suffixe für die Himmelskörper eines fremden Systems, siehe {@link #buildForeignSystemPlanets}. */
  private static final List<String> ORBIT_NUMERALS =
      List.of("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII");

  /**
   * Anzahl besiedelbarer Himmelskörper je System (Nutzervorgabe: "alles über 1,5 G wäre
   * unrealistisch" – mehr als eine Handvoll Planeten/Monde mit passender Masse pro System
   * gibt es deshalb nicht). Das Heimatsystem hat immer genau {@link #PLANET_NAMES_HOME}.size()
   * besiedelbare Himmelskörper, fremde Systeme eine zufällige Anzahl in diesem Bereich.
   */
  private static final int USABLE_BODIES_MIN = 3;
  private static final int USABLE_BODIES_MAX = 5;
  /** Anzahl NICHT besiedelbarer Himmelskörper je System (ungünstige Masse, Gasriesen, …), zusätzlich zu den besiedelbaren. */
  private static final int UNUSABLE_BODIES_MIN = 4;
  private static final int UNUSABLE_BODIES_MAX = 6;
  /**
   * Wahrscheinlichkeit, dass EIN besiedelbarer Himmelskörper eines Systems mit mindestens
   * einem Gasriesen/Eisriesen unter seinen unbesiedelbaren Körpern stattdessen ein
   * {@link PlanetType#Gasriesenmond} wird – ein Mond, der einen Teil der Fluide seines
   * Gasriesen mitnutzbar macht (Nutzervorgabe).
   */
  private static final double GAS_GIANT_MOON_CHANCE = 0.5;

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

  /**
   * Rohstoffe der Nahrungskette (Grundnahrung/Standardnahrung, siehe {@code products.json}),
   * die auf JEDEM Heimatplaneten zwischen {@link #HOMEWORLD_GOOD_MIN} und
   * {@link #HOMEWORLD_GOOD_MAX} Prozent liegen (Nutzervorgabe) – ein neuer Kommandant kann
   * seine Bevölkerung damit unabhängig vom gewürfelten Systemcluster immer selbst ernähren.
   */
  private static final List<String> HOMEWORLD_FOOD_RESOURCES =
      List.of("res_eis", "res_atmosphaere", "res_salz", "res_kohlenstoff");
  /** Eleriumspuren liegen wie die Nahrungsrohstoffe immer im "guten" Bereich (Nutzervorgabe). */
  private static final String HOMEWORLD_ELERIUM_RESOURCE = "res_elerium";
  private static final double HOMEWORLD_GOOD_MIN = 50.0;
  private static final double HOMEWORLD_GOOD_MAX = 60.0;
  /** Bereich für alle übrigen Heimatplanet-Rohstoffe außer der einen Zufalls-Ausnahme, siehe {@link #applyHomeworldProfile}. */
  private static final double HOMEWORLD_POOR_MIN = 1.0;
  private static final double HOMEWORLD_POOR_MAX = 9.0;

  private static List<ProductionQueueEntry> starterProductionQueue(String colonyId, IdGenerator ids) {
    record Entry(String productTypeId, double quantity) {
    }
    List<Entry> entries = new ArrayList<>();
    for (String productTypeId : STARTER_CONSUMER_GOODS) {
      double perOrder = STARTER_CONSUMER_GOODS_QUANTITY / STARTER_CONSUMER_GOODS_ORDER_SPLIT;
      for (int i = 0; i < STARTER_CONSUMER_GOODS_ORDER_SPLIT; i++) {
        entries.add(new Entry(productTypeId, perOrder));
      }
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

  private static WarehouseEntry jumpFuelReserveEntry(String colonyId, double quantity) {
    WarehouseEntry e = new WarehouseEntry();
    e.colonyId = colonyId;
    e.productTypeId = GameConstants.JUMP_FUEL_PRODUCT_ID;
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
   * §7.1. Der Clusterwert kommt NICHT mehr aus einem eigenen Wurf je Planet,
   * sondern aus {@code clusterValues} – dem regionalen Wert des Sternsystems an
   * seiner Kartenposition ({@link ResourceClusterField}), gemeinsam für alle
   * Planeten desselben Systems. Nur {@code localRandom} und
   * {@code localDeviation} bleiben je Planet unabhängig gewürfelt.
   */
  private static List<PlanetResourceConcentration> concentrationProfileForType(
      PlanetType type, Map<String, Double> clusterValues, Rng rnd) {
    List<PlanetTypeProfiles.ResourceRange> ranges = PlanetTypeProfiles.rangesForPlanetType(type);
    List<PlanetResourceConcentration> result = new ArrayList<>();
    for (PlanetTypeProfiles.ResourceRange rr : ranges) {
      double min = rr.range().min();
      double max = rr.range().max();
      double clusterValue = clusterValues.getOrDefault(rr.resourceTypeId(), 50.0);
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

  /**
   * Ersetzt für den Heimatplaneten ALLE Fördergüten durch die Nutzervorgabe: Nahrungsrohstoffe
   * ({@link #HOMEWORLD_FOOD_RESOURCES}) und {@link #HOMEWORLD_ELERIUM_RESOURCE} liegen immer
   * zwischen {@link #HOMEWORLD_GOOD_MIN} und {@link #HOMEWORLD_GOOD_MAX} Prozent, GENAU EIN
   * zufällig gewählter weiterer Rohstoff ebenfalls – alle übrigen liegen zwischen
   * {@link #HOMEWORLD_POOR_MIN} und {@link #HOMEWORLD_POOR_MAX} Prozent. Der zugrundeliegende
   * Planetentyp-Bereich ({@code conc}) bestimmt dabei nur noch die Menge der Rohstoff-Ids.
   */
  private static List<PlanetResourceConcentration> applyHomeworldProfile(List<PlanetResourceConcentration> conc, Rng rnd) {
    List<String> otherResourceIds = conc.stream()
        .map(c -> c.resourceTypeId)
        .filter(id -> !HOMEWORLD_FOOD_RESOURCES.contains(id) && !id.equals(HOMEWORLD_ELERIUM_RESOURCE))
        .toList();
    String bonusResourceId = otherResourceIds.isEmpty() ? null
        : otherResourceIds.get((int) Math.floor(rnd.next() * otherResourceIds.size()));

    List<PlanetResourceConcentration> result = new ArrayList<>();
    for (PlanetResourceConcentration c : conc) {
      boolean isGood = HOMEWORLD_FOOD_RESOURCES.contains(c.resourceTypeId)
          || c.resourceTypeId.equals(HOMEWORLD_ELERIUM_RESOURCE)
          || c.resourceTypeId.equals(bonusResourceId);
      PlanetResourceConcentration copy = new PlanetResourceConcentration();
      copy.resourceTypeId = c.resourceTypeId;
      copy.concentration = isGood
          ? Math.round(HOMEWORLD_GOOD_MIN + rnd.next() * (HOMEWORLD_GOOD_MAX - HOMEWORLD_GOOD_MIN))
          : Math.round(HOMEWORLD_POOR_MIN + rnd.next() * (HOMEWORLD_POOR_MAX - HOMEWORLD_POOR_MIN));
      result.add(copy);
    }
    return result;
  }

  private static boolean isGasGiant(PlanetType type) {
    return type == PlanetType.Gasriese || type == PlanetType.Eisriese;
  }

  /** Wie {@link #randomPlanetType}, aber ohne Gasriesen/Eisriesen – für Himmelskörper, die besiedelbar sein sollen. */
  private static PlanetType randomUsablePlanetType(Rng rnd) {
    PlanetType type;
    do {
      type = randomPlanetType(rnd);
    } while (isGasGiant(type));
    return type;
  }

  /** Regionaler Clusterwert je Rohstoff, unabhängig gewürfelt statt aus {@link ResourceClusterField} gelesen (siehe {@link #createAdditionalPlayerSeed}, dort ohne Zugriff auf das Feld der ursprünglichen Galaxie). */
  private static Map<String, Double> randomClusterValues(Rng rnd) {
    Map<String, Double> result = new LinkedHashMap<>();
    for (String resourceTypeId : PlanetTypeProfiles.RESOURCE_ORDER) {
      result.put(resourceTypeId, rnd.next() * 100);
    }
    return result;
  }

  /** Unbesiedelter Himmelskörper ohne Rohstoffanzeige (ungünstige Masse, Gasriese/Eisriese, …), siehe {@link #buildForeignSystemPlanets}. */
  private static Planet buildUnusablePlanet(String systemId, String systemName, int orbitIndex, PlanetType type, Rng rnd, IdGenerator ids) {
    Planet planet = new Planet();
    planet.id = ids.next("pla");
    planet.systemId = systemId;
    planet.name = systemName + " " + ORBIT_NUMERALS.get(Math.min(orbitIndex, ORBIT_NUMERALS.size() - 1));
    planet.size = PLANET_SIZES.get((int) Math.floor(rnd.next() * 4));
    planet.type = type;
    planet.resourceConcentration = List.of();
    planet.orbitIndex = orbitIndex;
    planet.usable = false;
    return planet;
  }

  /**
   * Himmelskörper eines NICHT-Heimatsystems (egal ob gewöhnliches System oder
   * Handelsgilde-Station – überall gibt es Planeten, siehe Konzeption-Rückfrage): eine
   * zufällige Anzahl besiedelbarer Körper ({@link #USABLE_BODIES_MIN}-{@link #USABLE_BODIES_MAX})
   * mit vollem Fördergüte-Profil, plus zusätzlich unbesiedelbare Körper
   * ({@link #UNUSABLE_BODIES_MIN}-{@link #UNUSABLE_BODIES_MAX}) ohne Rohstoffanzeige (Gasriesen,
   * Eisriesen oder Körper mit ungünstiger Masse). Enthält das System einen Gasriesen/Eisriesen,
   * wird mit {@link #GAS_GIANT_MOON_CHANCE} einer der besiedelbaren Körper stattdessen ein
   * {@link PlanetType#Gasriesenmond}, der einen Teil von dessen Fluiden mitnutzbar macht.
   */
  private static List<Planet> buildForeignSystemPlanets(String systemId, String systemName, Rng rnd, IdGenerator ids,
                                                          Map<String, Double> clusterValues) {
    List<Planet> planets = new ArrayList<>();
    int orbit = 0;

    int unusableCount = randInt(rnd, UNUSABLE_BODIES_MIN, UNUSABLE_BODIES_MAX);
    boolean gasGiantPresent = false;
    for (int i = 0; i < unusableCount; i++) {
      PlanetType type = randomPlanetType(rnd);
      if (isGasGiant(type)) gasGiantPresent = true;
      planets.add(buildUnusablePlanet(systemId, systemName, orbit++, type, rnd, ids));
    }

    int usableCount = randInt(rnd, USABLE_BODIES_MIN, USABLE_BODIES_MAX);
    boolean moonAvailable = gasGiantPresent;
    for (int i = 0; i < usableCount; i++) {
      PlanetType type;
      if (moonAvailable && rnd.next() < GAS_GIANT_MOON_CHANCE) {
        type = PlanetType.Gasriesenmond;
        moonAvailable = false; // höchstens ein Gasriesenmond pro System
      } else {
        type = randomUsablePlanetType(rnd);
      }
      List<PlanetResourceConcentration> conc = concentrationProfileForType(type, clusterValues, rnd);

      Planet planet = new Planet();
      planet.id = ids.next("pla");
      planet.systemId = systemId;
      planet.name = systemName + " " + ORBIT_NUMERALS.get(Math.min(orbit, ORBIT_NUMERALS.size() - 1));
      planet.size = PLANET_SIZES.get((int) Math.floor(rnd.next() * 4));
      planet.type = type;
      planet.resourceConcentration = conc;
      planet.orbitIndex = orbit++;
      planet.usable = true;
      planets.add(planet);
    }
    return planets;
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
                                                        Rng rnd, long t, IdGenerator ids, PlayerRole role, String campId,
                                                        Map<String, Double> clusterValues) {
    Player player = new Player();
    player.id = ids.next("ply");
    player.name = commanderName;
    player.homeSystemId = homeSystemId;
    player.homeworldColonyId = "";
    player.createdAt = t;
    player.role = role;
    player.campId = campId;

    List<Planet> planets = new ArrayList<>();
    for (int i = 0; i < PLANET_NAMES_HOME.size(); i++) {
      String name = PLANET_NAMES_HOME.get(i);
      // Der Spieler startet auf einem temperierten Biosphärenplaneten
      // (Nebula_Planetentypen_..., §8) – die übrigen Himmelskörper im
      // Heimatsystem sind zunächst unbesiedelt, aber (wie alle fünf
      // namentlichen Heimatplaneten) immer besiedelbar, deshalb kein Gasriese/Eisriese.
      PlanetType planetType = i == 0 ? PlanetType.TemperierterBiosphaerenplanet : randomUsablePlanetType(rnd);
      List<PlanetResourceConcentration> conc = concentrationProfileForType(planetType, clusterValues, rnd);
      if (i == 0) conc = applyHomeworldProfile(conc, rnd);

      Planet planet = new Planet();
      planet.id = ids.next("pla");
      planet.systemId = homeSystemId;
      planet.name = name;
      planet.size = PLANET_SIZES.get((int) Math.floor(rnd.next() * 4));
      planet.type = planetType;
      planet.resourceConcentration = conc;
      planet.orbitIndex = i;
      planet.usable = true;
      planets.add(planet);
    }
    // Zusätzlich unbesiedelbare Himmelskörper, damit auch das Heimatsystem der
    // galaxieweiten Zusammensetzung "wenige besiedelbare + einige unbesiedelbare
    // Körper" folgt (Nutzervorgabe), siehe {@link #buildForeignSystemPlanets}.
    int unusableCount = randInt(rnd, UNUSABLE_BODIES_MIN, UNUSABLE_BODIES_MAX);
    for (int i = 0; i < unusableCount; i++) {
      PlanetType type = randomPlanetType(rnd);
      int orbitIndex = PLANET_NAMES_HOME.size() + i;
      // Kein Systemname zur Hand (der Anzeigename des Heimatsystems entsteht erst in
      // createWorldSeed) – bewusst neutrale Bezeichnung statt Kolonienamens.
      planets.add(buildUnusablePlanet(homeSystemId, "Trümmerkörper", orbitIndex, type, rnd, ids));
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

    // Start (Nutzerentscheidung, abweichend von der ursprünglichen
    // Minimalstart-Herleitung in Umsetzungskonzept/17_...md): 120 Einwohner in
    // einem Wohnkomplex Stufe 1 (Kapazität 20.000 – der Wohnraum ist im
    // Frühspiel bewusst NICHT die Grenze; begrenzend ist die
    // Nahrungsversorgung, die Bevölkerung plateauiert rechnerisch bei ≈ 390),
    // Industriekomplex Stufe 5 und Infrastruktur Stufe 6 (beide
    // Bebauungsplätze belegt, keiner frei: total = 6 × SLOTS_PER_INFRASTRUCTURE_LEVEL
    // = 6, used = Habitat 1 + Industrie 5 = 6).
    double homePopulationCount = 120;
    int homeHabitatLevel = 1;
    int homeIndustryLevel = 5;
    int homeInfrastructureLevel = 6;

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
    warehouse.add(jumpFuelReserveEntry(colony.id, STARTER_JUMP_FUEL_QUANTITY));
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
    return createWorldSeed(commanderName, homeworldName, ids, PlayerRole.Normal, null);
  }

  public static Seed createWorldSeed(String commanderName, String homeworldName, IdGenerator ids, PlayerRole role,
                                      String campId) {
    Rng rnd = Rng.seeded(1337);
    long t = Clock.now();

    // --- Galaxie-Topologie ---------------------------------------------------
    GalaxyGenerator.GeneratedGalaxy galaxy = GalaxyGenerator.generateGalaxy(GALAXY_SYSTEM_COUNT, rnd);
    // Regionale Rohstoffstärken über die ganze Karte, siehe ResourceClusterField: Systeme in
    // derselben Gegend bekommen ähnliche Werte, weit entfernte Regionen unabhängige – dadurch
    // hat jede Gegend der Galaxie ein eigenes wirtschaftliches Profil und Fernhandel lohnt sich.
    ResourceClusterField.Field clusterField = ResourceClusterField.generate(PlanetTypeProfiles.RESOURCE_ORDER, rnd);
    List<String> names = Rng.shuffle(SYSTEM_NAME_POOL, rnd);
    List<String> systemIds = new ArrayList<>();
    List<String> gatewayIds = new ArrayList<>();
    for (int i = 0; i < galaxy.positions().size(); i++) {
      systemIds.add(ids.next("sys"));
      gatewayIds.add(ids.next("gw"));
    }
    Set<Integer> tradeHubSet = new LinkedHashSet<>(galaxy.tradeHubIndices());
    int homeIndex = galaxy.centralIndex();
    GalaxyGenerator.Point homePos = galaxy.positions().get(homeIndex);

    HomeworldBundle home = buildHomeworldBundle(commanderName, homeworldName, systemIds.get(homeIndex), rnd, t, ids,
        role, campId, clusterField.valuesAt(homePos.x(), homePos.y()));

    List<StarSystem> systems = new ArrayList<>();
    List<Planet> allPlanets = new ArrayList<>(home.planets());
    for (int i = 0; i < galaxy.positions().size(); i++) {
      boolean isHome = i == homeIndex;
      boolean isHub = tradeHubSet.contains(i);
      StarSystem s = new StarSystem();
      s.id = systemIds.get(i);
      s.name = isHome ? "Aurelia-System" : systemNameAt(names, i);
      s.x = galaxy.positions().get(i).x();
      s.y = galaxy.positions().get(i).y();
      if (isHome) {
        s.planetIds = home.planets().stream().map(p -> p.id).toList();
      } else {
        // Jedes System bekommt Himmelskörper, unabhängig davon, ob es eine
        // Handelsgilde-Station ist – siehe Konzeption-Rückfrage zur Systemansicht.
        Map<String, Double> clusterValues = clusterField.valuesAt(s.x, s.y);
        List<Planet> foreignPlanets = buildForeignSystemPlanets(s.id, s.name, rnd, ids, clusterValues);
        allPlanets.addAll(foreignPlanets);
        s.planetIds = foreignPlanets.stream().map(p -> p.id).toList();
      }
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
    seed.planets = allPlanets;
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
    return createAdditionalPlayerSeed(existingSystems, List.of(), commanderName, homeworldName, ids, PlayerRole.Normal, null);
  }

  /**
   * Wie {@link #createAdditionalPlayerSeed(List, String, String, IdGenerator)}, aber mit
   * Spielerrolle: bei {@code role == Npc} und vorhandenem {@code campId} wird das neue
   * Heimatsystem beim Schwerpunkt der bereits registrierten Lagerkollegen platziert
   * ({@link #pickCampPosition}) statt wie bisher maximal isoliert
   * ({@link #pickIsolatedPosition}) – Umsetzungskonzept/20_...md. Ohne Lagerkollegen (der
   * erste NPC eines Lagers) bleibt es bei der isolierten Platzierung, wodurch sich
   * verschiedene Lager im Schnitt weit auseinander ansiedeln.
   */
  public static AdditionalSeed createAdditionalPlayerSeed(List<StarSystem> existingSystems,
                                                            List<Player> existingPlayers, String commanderName,
                                                            String homeworldName, IdGenerator ids, PlayerRole role,
                                                            String campId) {
    Rng rnd = () -> Math.random();
    long t = Clock.now();

    List<StarSystem> campSystems = List.of();
    if (role == PlayerRole.Npc && campId != null) {
      Set<String> campHomeSystemIds = existingPlayers.stream()
          .filter(p -> campId.equals(p.campId))
          .map(p -> p.homeSystemId)
          .collect(java.util.stream.Collectors.toSet());
      campSystems = existingSystems.stream().filter(s -> campHomeSystemIds.contains(s.id)).toList();
    }
    GalaxyGenerator.Point position = campSystems.isEmpty()
        ? pickIsolatedPosition(existingSystems, rnd)
        : pickCampPosition(existingSystems, campSystems, rnd);
    StarSystem linkedSystem = nearestSystem(existingSystems, position);

    String systemId = ids.next("sys");
    String gatewayId = ids.next("gw");
    // Kein Zugriff auf das ResourceClusterField der ursprünglichen Galaxie (dieses System liegt
    // außerhalb davon) – stattdessen ein frisch gewürfelter, aber wie gewohnt über den ganzen
    // Heimatplaneten-Cluster gemeinsamer Clusterwert je Rohstoff.
    HomeworldBundle home = buildHomeworldBundle(commanderName, homeworldName, systemId, rnd, t, ids, role, campId,
        randomClusterValues(rnd));

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

  /**
   * Mindestabstand zu jedem bestehenden System, den ein Lager-Kandidat trotz Clusterung
   * einhalten muss – ungefähr eine Sprungweite zwischen benachbarten Systemen bei
   * {@code GALAXY_SYSTEM_COUNT} Systemen ({@code GalaxyGenerator.generateGalaxy}), damit
   * NPCs desselben Lagers nicht auf einem bestehenden System landen.
   */
  private static final double CAMP_MIN_SEPARATION = 0.05;

  /**
   * Gegenstück zu {@link #pickIsolatedPosition}: sucht unter zufälligen Kandidaten den mit
   * dem kleinsten Abstand zum Schwerpunkt der übergebenen {@code campSystems}, verwirft
   * dabei aber Kandidaten, die einem bestehenden System zu nahekommen
   * ({@link #CAMP_MIN_SEPARATION}). Findet sich in den Versuchen kein solcher Kandidat,
   * fällt die Methode auf isolierte Platzierung zurück, statt ein überlappendes System zu
   * erzeugen.
   */
  private static GalaxyGenerator.Point pickCampPosition(List<StarSystem> existingSystems,
                                                          List<StarSystem> campSystems, Rng rnd) {
    double campX = campSystems.stream().mapToDouble(s -> s.x).average().orElse(0.5);
    double campY = campSystems.stream().mapToDouble(s -> s.y).average().orElse(0.5);
    double margin = 0.08;
    GalaxyGenerator.Point best = null;
    double bestDistToCamp = Double.POSITIVE_INFINITY;
    for (int attempt = 0; attempt < 60; attempt++) {
      GalaxyGenerator.Point candidate = new GalaxyGenerator.Point(
          margin + rnd.next() * (1 - 2 * margin), margin + rnd.next() * (1 - 2 * margin));
      double minDistToExisting = Double.POSITIVE_INFINITY;
      for (StarSystem s : existingSystems) {
        minDistToExisting = Math.min(minDistToExisting, Math.hypot(candidate.x() - s.x, candidate.y() - s.y));
      }
      if (minDistToExisting < CAMP_MIN_SEPARATION) continue;
      double distToCamp = Math.hypot(candidate.x() - campX, candidate.y() - campY);
      if (distToCamp < bestDistToCamp) {
        bestDistToCamp = distToCamp;
        best = candidate;
      }
    }
    return best != null ? best : pickIsolatedPosition(existingSystems, rnd);
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
