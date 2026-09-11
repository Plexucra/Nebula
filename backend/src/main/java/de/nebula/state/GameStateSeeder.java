package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.model.Gateway;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Überträgt einen {@link WorldSeed.Seed}/{@link WorldSeed.AdditionalSeed} in
 * den geteilten {@link GameState} – Java-Gegenstück zu {@code hydrate}/
 * {@code appendPlayer} in {@code SimulatedGameApiService}.
 *
 * <p>Bewusste Abweichung vom TS-Original: dort registriert
 * {@code bootstrapFreshWorld} beim allerersten Laden EINES BROWSERS
 * automatisch einen synthetischen Standard-Kommandanten (nur UX-Komfort für
 * den Einzelspieler-Prototyp, keine Spielmechanik). Im geteilten Server-Modell
 * gibt es dieses Konzept nicht: {@link #bootstrap} wird stattdessen einmalig
 * vom ERSTEN echten {@code registerPlayer}-Aufruf ausgelöst (siehe
 * {@code GameSocket}) – die eigentliche Mechanik ("eine gemeinsame Galaxie,
 * die mit jedem registrierten Kommandanten wächst") bleibt unverändert.</p>
 */
public final class GameStateSeeder {
  private GameStateSeeder() {
  }

  /** Erstbefüllung einer LEEREN Galaxie (Java-Gegenstück zu {@code hydrate}). */
  public static void bootstrap(GameState state, WorldSeed.Seed seed, IdGenerator ids) {
    state.players.add(seed.player);
    state.systems.addAll(seed.systems);
    state.knownSystemIdsByPlayer.put(seed.player.id, new LinkedHashSet<>(Set.of(seed.player.homeSystemId)));
    state.exploredSystemIdsByPlayer.put(seed.player.id, new LinkedHashSet<>(Set.of(seed.player.homeSystemId)));
    state.planets.addAll(seed.planets);
    state.colonies.addAll(seed.colonies);
    state.planetStats.addAll(seed.planetStats);
    state.populations.addAll(seed.populations);
    state.moneySupplyStates.addAll(seed.moneySupplyStates);
    state.wallets.addAll(seed.wallets);
    state.buildings.addAll(seed.buildings);
    state.warehouse.addAll(seed.warehouse);
    state.productionQueue.addAll(seed.productionQueue);
    state.gateways.addAll(seed.gateways);
    state.fleets.addAll(seed.fleets);
    state.groundForceGroups.addAll(seed.groundForceGroups);
    state.marketOrders.addAll(seed.sellOrders);
    // Start-Auftragsliste kommt direkt über addAll herein statt über
    // queueProduction, das sonst automatisch den nächsten wartenden Eintrag
    // anstößt – deshalb hier manuell nachholen (siehe TS `hydrate`). Dasselbe
    // gilt für die Mindestdauer: die Startmengen erst am fertigen Zustand anheben.
    for (var entry : seed.productionQueue) ProductionCommands.raiseToMinimum(state, entry);
    for (String colonyId : seed.productionQueue.stream().map(e -> e.colonyId).distinct().toList()) {
      ProductionCommands.tryStartNextProductionEntry(state, ids, colonyId);
    }
    // Einmalig für die GESAMTE (frisch erzeugte) Galaxie: Handelsgilde-Orderbuch an jeder Station
    // (Umsetzungskonzept/22_...md). Hier und nicht lazy beim ersten Stationsbesuch, siehe dortige Klassendoku.
    MarketCommands.seedAllMarketMakers(state, ids);
    // Die Bevölkerung kauft sofort aus den Startorders ein und beginnt ihren Tagesrhythmus (Umsetzungskonzept/36).
    for (var colony : seed.colonies) Economy.startColonyRhythm(state, ids, colony);
    VictoryCommands.evaluate(state, ids);
  }

  /**
   * Fügt einen neu registrierten Kommandanten samt neuem Heimatsystem in die
   * BESTEHENDE Galaxie ein (Java-Gegenstück zu {@code appendPlayer}) – andere
   * Kommandanten und der Systemmarkt bleiben unverändert bestehen.
   */
  public static void appendPlayer(GameState state, WorldSeed.AdditionalSeed seed, IdGenerator ids) {
    state.systems.add(seed.newSystem);
    state.gateways.add(seed.newGateway);
    // Bestehendes verlinktes Gateway bidirektional ergänzen – ohne das wäre
    // das neue System zwar VON SICH AUS erreichbar, aber nicht ANDERSHERUM.
    for (Gateway g : state.gateways) {
      if (g.systemId.equals(seed.linkedSystemId)) {
        var reachable = new java.util.ArrayList<>(g.reachableSystemIds);
        reachable.add(seed.newSystem.id);
        g.reachableSystemIds = reachable;
      }
    }
    state.players.add(seed.player);
    state.knownSystemIdsByPlayer.put(seed.player.id, new LinkedHashSet<>(Set.of(seed.newSystem.id)));
    state.exploredSystemIdsByPlayer.put(seed.player.id, new LinkedHashSet<>(Set.of(seed.newSystem.id)));
    state.planets.addAll(seed.planets);
    state.colonies.add(seed.colony);
    state.planetStats.add(seed.planetStats);
    state.populations.add(seed.population);
    state.moneySupplyStates.add(seed.moneySupplyState);
    state.wallets.addAll(seed.wallets);
    state.buildings.addAll(seed.buildings);
    state.warehouse.addAll(seed.warehouse);
    state.productionQueue.addAll(seed.productionQueue);
    state.fleets.addAll(seed.fleets);
    state.groundForceGroups.add(seed.groundForceGroup);
    state.marketOrders.addAll(seed.sellOrders);
    for (var entry : seed.productionQueue) ProductionCommands.raiseToMinimum(state, entry);
    ProductionCommands.tryStartNextProductionEntry(state, ids, seed.colony.id);
    Economy.startColonyRhythm(state, ids, seed.colony);
    // Eine neue Partei mit Kolonien – die Siegprüfung hängt an genau solchen Übergängen, nicht am Tick.
    VictoryCommands.evaluate(state, ids);
  }
}
