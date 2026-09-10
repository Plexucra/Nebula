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
  /**
   * DIE Sperre der Galaxie. Abfragen halten das Leseschloss (beliebig viele
   * gleichzeitig), Befehle und der Ereignisplaner das Schreibschloss. Vorher
   * war es ein einziges {@code synchronized(state)} für alles – jede der
   * hunderte Abfragen je Sekunde stand hinter jedem Wirtschaftsschritt an.
   * Voraussetzung: Abfragen ändern nichts ({@code GameSocket.READ_ONLY}).
   */
  public final java.util.concurrent.locks.ReentrantReadWriteLock lock =
      new java.util.concurrent.locks.ReentrantReadWriteLock();

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
  /** Laufende Koloniegründungen – noch keine Kolonie (Umsetzungskonzept/24_...md). */
  public final List<de.nebula.model.Colonization> colonizations = new CopyOnWriteArrayList<>();
  public final List<PlanetStats> planetStats = new CopyOnWriteArrayList<>();
  public final List<ColonyPowerState> powerStates = new CopyOnWriteArrayList<>();
  /** Energiespeicher je Kolonie (Umsetzungskonzept/32_...md) – entsteht beim ersten Zugriff. */
  public final List<de.nebula.model.EnergyStorage> energyStorages = new CopyOnWriteArrayList<>();
  /** Letzte Versorgungswarnung je "colonyId:productTypeId" (Umsetzungskonzept/32_...md, Teil B). */
  public final Map<String, Long> lastSupplyWarningAt = new ConcurrentHashMap<>();
  /**
   * Zuletzt gemeldeter Zustand je beobachteter Dauerlage (z. B.
   * {@code "blackout:col_1"}), siehe {@code Notifications.edgeTriggered}.
   * Dauerlagen dürfen nur beim WECHSEL melden – sonst erzeugt jeder Tick eine
   * neue Benachrichtigung und die Glocke ist unbrauchbar.
   */
  public final Map<String, Boolean> notificationEdgeState = new ConcurrentHashMap<>();
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
  /** Bodengefechte um einzelne Kolonien (Mechanik/05_...md §10-12), siehe {@code GroundBattleCommands}. */
  public final List<GroundBattle> groundBattles = new CopyOnWriteArrayList<>();
  public final List<Blockade> blockades = new CopyOnWriteArrayList<>();
  /** Ingame-Nachrichtensystem (Umsetzungskonzept/14_...md) – ausschließlich Spieler-zu-Spieler. */
  public final List<Message> messages = new CopyOnWriteArrayList<>();
  /** Bevölkerungsverlauf je Kolonie als begrenzter Ringpuffer, siehe {@code PopulationHistory}. */
  public final Map<String, List<PopulationSample>> populationHistory = new ConcurrentHashMap<>();

  /** Analog zu {@code consumptionBudget}/{@code lastProducedAt}/{@code rawStandardOfLiving} in der TS-Simulation – rein interne Buchführung, kein Snapshot-Feld. */
  /**
   * Übertragskonten für Raten unter einem Stück je Tick (siehe {@link FractionPot}).
   * Die EINZIGE Stelle im Spielzustand, an der noch Bruchteile von Stückzahlen
   * liegen – Lager, Orders und Fracht bewegen sich ausschließlich in ganzen Stücken.
   */
  public final Map<String, Double> fractionPots = new ConcurrentHashMap<>();
  public final Map<String, Double> consumptionBudget = new ConcurrentHashMap<>();
  public final Map<String, Long> lastProducedAt = new ConcurrentHashMap<>();
  public final Map<String, Double> rawStandardOfLiving = new ConcurrentHashMap<>();
  /** Deckung (0..1,5) je Grundkonsumgut und Kolonie – Java-Gegenstück zu {@code _consumptionCoverage}. colonyId -> (productTypeId -> coverage). */
  public final Map<String, Map<String, Double>> consumptionCoverage = new ConcurrentHashMap<>();

  /**
   * Der entschiedene Krieg (siehe {@code VictoryCommands}) – {@code null},
   * solange mehr als eine Partei Kolonien besitzt.
   */
  public volatile de.nebula.model.GameVictory victory;
  /**
   * Parteien, die im Lauf dieser Galaxie jemals Kolonien besaßen. Nur mit
   * diesem Gedächtnis lässt sich "als Einzige übrig" von "als Erste da" unter-
   * scheiden – siehe {@code VictoryCommands.evaluate}.
   */
  public final java.util.Set<String> partiesEverWithColonies =
      java.util.concurrent.ConcurrentHashMap.newKeySet();

  /**
   * Die geplanten Ereignisse, nach Fälligkeit sortiert, plus Index je Typ und
   * Ziel – siehe {@link GameEvents}. Beide nur unter der Sperre auf
   * {@code this} anfassen; sie sind bewusst keine nebenläufigen Strukturen,
   * weil jede Änderung zwei Container konsistent halten muss.
   */
  public final java.util.TreeSet<de.nebula.model.ScheduledEvent> events = new java.util.TreeSet<>();
  public final Map<String, de.nebula.model.ScheduledEvent> eventByKey = new java.util.HashMap<>();
  public final java.util.concurrent.atomic.AtomicLong eventSeq = new java.util.concurrent.atomic.AtomicLong();

  /**
   * Räumt die schlüsselbasierten Nebenbücher einer Kolonie auf, die verschwindet
   * (Eingliederung nach Eroberung, Löschung ihres Kommandanten): Übertragskonten
   * ({@code "purpose:colonyId[:product]"}), Spezialisierungs-Zeitstempel
   * ({@code "colonyId:product"}), Versorgungswarnungen ({@code "colonyId:product"})
   * und Flankenzustände der Benachrichtigungen ({@code "lage:colonyId"}).
   *
   * <p>Vorher stand hier {@code k.contains(id)} – und weil die Ids fortlaufend
   * vergeben sind ({@code col_1}, {@code col_1a}, ...), räumte der Fall von
   * {@code col_1} auch die Übertragskonten von {@code col_1a} ab. Deshalb wird
   * hier exakt auf das Schlüsselsegment geprüft.</p>
   */
  public void forgetColonyBookkeeping(String colonyId) {
    fractionPots.keySet().removeIf(k -> {
      String[] parts = k.split(":");
      return parts.length >= 2 && parts[1].equals(colonyId);
    });
    lastProducedAt.keySet().removeIf(k -> k.startsWith(colonyId + ":"));
    lastSupplyWarningAt.keySet().removeIf(k -> k.startsWith(colonyId + ":"));
    notificationEdgeState.keySet().removeIf(k -> k.endsWith(":" + colonyId));
  }

  /**
   * Fabrik-Reset: leert JEDES Feld dieser Klasse. Bewusst hier und nicht im
   * {@code GameSocket}: Wer dort ein Feld vergaß, hinterließ Reste aus der
   * alten Galaxie – so blieben Verträge, Koloniegründungen, Energiespeicher,
   * Übertragskonten und die Flankenzustände der Benachrichtigungen über einen
   * Reset hinweg stehen (die Flanke "blackout:col_1" war dann für die nächste
   * Galaxie schon "gesetzt" und die erste Meldung blieb aus).
   * Aufrufer hält die Sperre auf {@code this}.
   */
  public void reset() {
    players.clear();
    systems.clear();
    knownSystemIdsByPlayer.clear();
    exploredSystemIdsByPlayer.clear();
    planets.clear();
    colonies.clear();
    colonizations.clear();
    planetStats.clear();
    powerStates.clear();
    energyStorages.clear();
    lastSupplyWarningAt.clear();
    notificationEdgeState.clear();
    populations.clear();
    moneySupplyStates.clear();
    wallets.clear();
    transactions.clear();
    buildings.clear();
    specializations.clear();
    productionQueue.clear();
    warehouse.clear();
    gateways.clear();
    fleets.clear();
    shipyardQueue.clear();
    groundForceGroups.clear();
    recruitmentQueue.clear();
    sellOrders.clear();
    hubOrders.clear();
    hubDepot.clear();
    universeStats.clear();
    notifications.clear();
    diplomaticRelations.clear();
    peaceOffers.clear();
    treaties.clear();
    treatyOffers.clear();
    battles.clear();
    groundBattles.clear();
    blockades.clear();
    messages.clear();
    populationHistory.clear();
    fractionPots.clear();
    consumptionBudget.clear();
    lastProducedAt.clear();
    rawStandardOfLiving.clear();
    consumptionCoverage.clear();
    victory = null;
    partiesEverWithColonies.clear();
    events.clear();
    eventByKey.clear();
  }
}
