package de.nebula.state;

import de.nebula.model.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Der GESAMTE geteilte Galaxie-Zustand, serverseitig im Speicher gehalten
 * (Umsetzungskonzept/13_...md, Phase 1 – "vorerst statisch im Java speichern,
 * keine Persistenz"). Direktes Java-Gegenstück zu den privaten Signals in
 * {@code SimulatedGameApiService} (Frontend) bzw. zum dortigen
 * {@code Snapshot}-Interface (localStorage-Struktur) – ein Feld je Collection,
 * 1:1 benannt, damit sich die spätere Geschäftslogik-Portierung mechanisch
 * an dieser Aufzählung orientieren kann.
 *
 * <p>Bewusst NUR Datenhaltung, keine Geschäftslogik – die Regeln (Produktion,
 * Kampf, Diplomatie, Handel, Tick-Verarbeitung, ...) wandern in eigene
 * Service-Klassen, sobald sie portiert werden (siehe Migrationsplan §6,
 * "Reihenfolge der Geschäftslogik-Portierung").</p>
 *
 * <p>Thread-Sicherheit: {@link CopyOnWriteArrayList}/{@link ConcurrentHashMap}
 * für phasenweise, unabhängige Lese-/Schreibzugriffe aus WebSocket-Handlern
 * (potenziell mehrere Verbindungen gleichzeitig) und dem einen
 * Tick-Scheduler-Thread. Das schützt NICHT vor Race Conditions bei
 * mehrschrittigen Invarianten (z. B. "lese Lagerbestand, dann schreibe
 * Abzug") – für die ist eine grobkörnige Sperre um ganze Kommando-Methoden
 * vorgesehen (siehe {@code de.nebula.service}-Paket, sobald es entsteht),
 * analog dazu, dass die TS-Simulation dank Single-Threaded-JS ohnehin nie
 * nebenläufig lief.</p>
 */
@ApplicationScoped
public class GameState {
  public final List<Player> players = new CopyOnWriteArrayList<>();
  public final List<StarSystem> systems = new CopyOnWriteArrayList<>();
  /** Bekannte Systeme PRO Kommandant (Fog of War) – playerId -> Set<systemId>. */
  public final Map<String, java.util.Set<String>> knownSystemIdsByPlayer = new ConcurrentHashMap<>();
  /**
   * Erforschte Systeme PRO Kommandant – playerId -> Set<systemId>. Getrennt von
   * {@link #knownSystemIdsByPlayer} ("schon mal dort gewesen"): ein besuchtes System zeigt
   * bereits seine Himmelskörper, aber erst das explizite Erforschen ({@code FleetCommands.exploreSystem})
   * deckt deren Rohstoffkonzentration auf. Das Heimatsystem bzw. das eigene neue Heimatsystem
   * gilt von Anfang an als erforscht (siehe {@code GameStateSeeder}), ebenso jedes System, in dem
   * ein Kommandant selbst kolonisiert hat ({@code ColonyCommands.colonizePlanet}).
   */
  public final Map<String, java.util.Set<String>> exploredSystemIdsByPlayer = new ConcurrentHashMap<>();
  public final List<Planet> planets = new CopyOnWriteArrayList<>();
  public final List<Colony> colonies = new CopyOnWriteArrayList<>();
  public final List<PlanetStats> planetStats = new CopyOnWriteArrayList<>();
  public final List<ColonyPowerState> powerStates = new CopyOnWriteArrayList<>();
  public final List<Population> populations = new CopyOnWriteArrayList<>();
  public final List<PopulationMoneySupplyState> moneySupplyStates = new CopyOnWriteArrayList<>();
  public final List<Wallet> wallets = new CopyOnWriteArrayList<>();
  public final List<Transaction> transactions = new CopyOnWriteArrayList<>();
  public final List<Building> buildings = new CopyOnWriteArrayList<>();
  public final List<Specialization> specializations = new CopyOnWriteArrayList<>();
  public final List<ProductionQueueEntry> productionQueue = new CopyOnWriteArrayList<>();
  public final List<WarehouseEntry> warehouse = new CopyOnWriteArrayList<>();
  public final List<Gateway> gateways = new CopyOnWriteArrayList<>();
  public final List<Fleet> fleets = new CopyOnWriteArrayList<>();
  public final List<ShipyardQueueEntry> shipyardQueue = new CopyOnWriteArrayList<>();
  public final List<GroundForceGroup> groundForceGroups = new CopyOnWriteArrayList<>();
  public final List<RecruitmentQueueEntry> recruitmentQueue = new CopyOnWriteArrayList<>();
  public final List<SellOrder> sellOrders = new CopyOnWriteArrayList<>();
  /** Orderbuch (Kauf UND Verkauf) der Handelsgilde-Stationen, siehe Umsetzungskonzept/22_...md. */
  public final List<HubOrder> hubOrders = new CopyOnWriteArrayList<>();
  /** Unbegrenztes Depot je Kommandant und Handelsgilde-Station, siehe {@code HubDepot}. */
  public final List<HubDepotEntry> hubDepot = new CopyOnWriteArrayList<>();
  /** Monotoner Zähler für Preis-Zeit-Priorität im Orderbuch (Millisekunden-Zeitstempel allein reichen bei zwei Orders im selben Tick nicht). */
  public final java.util.concurrent.atomic.AtomicLong hubOrderSeq = new java.util.concurrent.atomic.AtomicLong();
  public final List<UniverseStatSnapshot> universeStats = new CopyOnWriteArrayList<>();
  public final List<GameNotification> notifications = new CopyOnWriteArrayList<>();
  public final List<DiplomaticRelation> diplomaticRelations = new CopyOnWriteArrayList<>();
  public final List<PeaceOffer> peaceOffers = new CopyOnWriteArrayList<>();
  /** Förmliche Friedens-/Handelsverträge, siehe Umsetzungskonzept/21_...md. */
  public final List<Treaty> treaties = new CopyOnWriteArrayList<>();
  public final List<TreatyOffer> treatyOffers = new CopyOnWriteArrayList<>();
  public final List<Battle> battles = new CopyOnWriteArrayList<>();
  public final List<Blockade> blockades = new CopyOnWriteArrayList<>();
  /** Ingame-Nachrichtensystem (Umsetzungskonzept/14_...md) – ausschließlich Spieler-zu-Spieler. */
  public final List<Message> messages = new CopyOnWriteArrayList<>();
  /** Bevölkerungsverlauf je Kolonie als begrenzter Ringpuffer, siehe {@code PopulationHistory}. */
  public final Map<String, List<PopulationSample>> populationHistory = new ConcurrentHashMap<>();

  /** Analog zu {@code consumptionBudget}/{@code lastProducedAt}/{@code rawStandardOfLiving} in der TS-Simulation – rein interne Buchführung, kein Snapshot-Feld. */
  public final Map<String, Double> consumptionBudget = new ConcurrentHashMap<>();
  public final Map<String, Long> lastProducedAt = new ConcurrentHashMap<>();
  public final Map<String, Double> rawStandardOfLiving = new ConcurrentHashMap<>();
  /** Deckung (0..1,5) je Grundkonsumgut und Kolonie – Java-Gegenstück zu {@code _consumptionCoverage}. colonyId -> (productTypeId -> coverage). */
  public final Map<String, Map<String, Double>> consumptionCoverage = new ConcurrentHashMap<>();

  /** Tick-Intervall-Zeitstempel, siehe TS {@code lastWealthRedistributionAt}/{@code lastStatsSnapshotAt}. */
  public volatile long lastWealthRedistributionAt = 0;
  public volatile long lastStatsSnapshotAt = 0;
}
