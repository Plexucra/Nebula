package de.nebula.state;

import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.Building;
import de.nebula.model.Colony;
import de.nebula.model.FleetLocationType;
import de.nebula.model.GroundBattle;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.NotificationType;
import de.nebula.model.PlanetStats;
import de.nebula.model.Player;
import de.nebula.model.Population;
import de.nebula.model.ProductionQueueEntry;
import de.nebula.model.RecruitmentQueueEntry;
import de.nebula.model.TransactionReason;
import de.nebula.model.Wallet;
import de.nebula.model.WalletOwnerType;
import de.nebula.model.WarehouseEntry;

import java.util.ArrayList;
import java.util.Map;

/**
 * Was mit einer Kolonie geschieht, wenn ihre Bodenverteidigung gefallen ist
 * (Mechanik/05_Bodentruppen_und_Bodenkrieg.md §2). Zwei Schritte, in dieser
 * Reihenfolge:
 *
 * <ol>
 *   <li><b>Konfliktschäden.</b> Aus der im Gefecht aufgelaufenen
 *       Zivilverlustquote folgen deterministisch die Sachschäden – an
 *       Infrastruktur, Produktionsanlagen und Ressourcenbeständen
 *       ({@link Formulas#conquestMaterialLossFraction}), an
 *       Verteidigungsanlagen stärker
 *       ({@link Formulas#conquestDefenseLossFraction}). Kein Zufall.</li>
 *   <li><b>Übernahme.</b> Was überlebt hat, bleibt erhalten und geht an den
 *       Eroberer: entweder wird die Kolonie zusammengeführt mit seiner
 *       bereits vorhandenen Kolonie auf demselben Planeten, oder sie wechselt
 *       als Ganzes den Eigentümer ("sonst neue Kolonie").</li>
 * </ol>
 *
 * <p>Die Zusammenführung ist kein Sonderfall aus Bequemlichkeit, sondern die
 * Regel aus §1: pro Spieler höchstens eine Kolonie je Planet. Ohne sie hätte
 * ein Eroberer nach einem Sieg zwei.</p>
 */
final class ColonyConquest {
  private ColonyConquest() {
  }

  /**
   * Führt Schadensabrechnung und Übernahme aus und liefert den Meldungstext
   * für die Benachrichtigungen beider Seiten.
   */
  static String conquer(GameState state, IdGenerator ids, GroundBattle battle, GroundForceGroup victoriousGroup) {
    Colony colony = ColonyCommands.colony(state, battle.colonyId);
    if (colony == null) return null;
    String previousOwnerId = colony.ownerId;
    String previousOwnerName = GameQueries.ownerDisplayName(state, colony.ownerId);
    String attackerName = GameQueries.ownerDisplayName(state, battle.attackerId);

    applyConflictDamage(state, colony, battle.civilianLossRatio);

    // Aufträge des bisherigen Eigentümers laufen nicht für den Eroberer weiter.
    // Bewusst OHNE Erstattung: gebundene Baustoffe und Credits sind Kriegsbeute
    // bzw. im Chaos der Eroberung verloren (§2 behandelt Bestände ohnehin nur
    // als beschädigt oder erbeutet, nie als rückerstattet).
    state.productionQueue.removeIf(e -> e.colonyId.equals(colony.id));
    state.recruitmentQueue.removeIf(e -> e.colonyId.equals(colony.id));
    for (Building b : state.buildings) if (b.colonyId.equals(colony.id)) b.pendingOrder = null;
    // Die Gebote der Bevölkerung tragen den bisherigen Kommandanten als Eigentümer –
    // zurückziehen (Escrow zurück ins Bevölkerungs-Wallet), der nächste Kolonietag
    // stellt sie unter dem neuen Herrn neu (Umsetzungskonzept/38, Teil C).
    MarketCommands.cancelPopulationBids(state, colony.id);

    Colony ownColonyOnPlanet = state.colonies.stream()
        .filter(c -> c.ownerId.equals(battle.attackerId) && c.planetId.equals(colony.planetId))
        .findFirst().orElse(null);

    String summary;
    if (ownColonyOnPlanet != null) {
      mergeInto(state, ids, colony, ownColonyOnPlanet);
      summary = attackerName + " hat \"" + colony.name + "\" (" + previousOwnerName
          + ") erobert und in \"" + ownColonyOnPlanet.name + "\" eingegliedert.";
      moveGarrisonInto(state, victoriousGroup, ownColonyOnPlanet.id);
    } else {
      colony.ownerId = battle.attackerId;
      colony.isHomeworld = false; // eine eroberte Kolonie ist nie die Heimatwelt des Eroberers
      // Die Loyalität wird hier NICHT gesetzt: die Belagerung hat sie unter
      // SIEGE_SURRENDER_LOYALTY_PCT gedrückt, und genau dieser Wert ist der
      // Zustand, in dem der Eroberer die Kolonie übernimmt. Ein pauschaler
      // Startwert würde sie an dieser Stelle sogar wieder anheben.
      state.rawStandardOfLiving.remove(colony.id);
      summary = attackerName + " hat \"" + colony.name + "\" von " + previousOwnerName + " erobert.";
      moveGarrisonInto(state, victoriousGroup, colony.id);
    }
    releaseHomeworld(state, ids, previousOwnerId, battle.colonyId, colony.name);
    // Die Siegprüfung hängt am Besitzwechsel – hier und bei der Löschung eines
    // Kommandanten, nicht mehr an jedem Tick.
    VictoryCommands.evaluate(state, ids);
    return summary;
  }

  /**
   * Verlust der Heimatwelt (Umsetzungskonzept/34_...md, §J 9). Der Kommandant
   * bleibt im Spiel – mit seinen Flotten, seinen übrigen Kolonien und der
   * Möglichkeit, mit einem Kolonisationsschiff neu anzufangen (die nächste
   * Gründung wird dann wieder seine Heimatwelt, siehe
   * {@code ColonyCommands.colonizePlanet}). Was NICHT bleiben darf, ist der
   * Verweis: {@code homeworldColonyId} zeigte nach der Eroberung auf fremden
   * Besitz, und alles, was daran adressiert war, landete beim Eroberer.
   */
  private static void releaseHomeworld(GameState state, IdGenerator ids, String previousOwnerId,
                                       String conqueredColonyId, String colonyName) {
    Player loser = null;
    for (Player p : state.players) if (p.id.equals(previousOwnerId)) loser = p;
    if (loser == null || !conqueredColonyId.equals(loser.homeworldColonyId)) return;
    loser.homeworldColonyId = "";
    boolean hasOtherColony = state.colonies.stream().anyMatch(c -> c.ownerId.equals(previousOwnerId));
    Notifications.notifyPlayer(state, ids, NotificationType.Problem, Notifications.CODE_HOMEWORLD_LOST,
        "Ihre Heimatwelt \"" + colonyName + "\" ist verloren."
            + (hasOtherColony
                ? " Ihre übrigen Kolonien und Flotten bleiben Ihnen."
                : " Sie befehligen nur noch Ihre Flotten – ein Kolonisationsschiff gründet eine neue Heimat."),
        previousOwnerId, "/flotten");
  }

  /**
   * Mechanik/05_..., §2: Materialschaden steigt überproportional zur
   * Zivilverlustquote. Betroffen sind Gebäudestufen (Verteidigungsanlagen
   * stärker als der Rest) und die Lagerbestände; die Bevölkerungsverluste
   * selbst sind bereits tickweise im Gefecht angefallen.
   */
  private static void applyConflictDamage(GameState state, Colony colony, double civilianLossRatio) {
    double materialLoss = Formulas.conquestMaterialLossFraction(civilianLossRatio);
    double defenseLoss = Formulas.conquestDefenseLossFraction(civilianLossRatio);

    for (Building b : state.buildings) {
      if (!b.colonyId.equals(colony.id) || b.level <= 0) continue;
      boolean isDefense = b.typeId.equals(GameConstants.PLANETARY_DEFENSE_BUILDING_ID);
      double loss = isDefense ? defenseLoss : materialLoss;
      // Abgerundet: der angebrochene Rest einer Stufe steht noch.
      b.level = Math.max(0, b.level - (int) Math.floor(b.level * loss));
    }
    for (WarehouseEntry w : new ArrayList<>(state.warehouse)) {
      if (!w.colonyId.equals(colony.id) || w.quantity <= 0) continue;
      double lost = Math.floor(w.quantity * materialLoss);
      if (lost > 0) Warehouse.add(state, colony.id, w.productTypeId, -lost);
    }
    // Der Energiespeicher ist Lagerbestand im Sinne von §2 – derselbe Schaden.
    de.nebula.model.EnergyStorage storage = EnergyStorageCommands.storageOf(state, colony.id);
    storage.stored -= Math.floor(storage.stored * materialLoss);
  }

  /**
   * Führt die eroberte Kolonie in eine bestehende eigene Kolonie desselben
   * Planeten zusammen: Bevölkerung, Bargeld der Bevölkerung und Lagerbestände
   * wandern hinüber, alles Übrige (Gebäude, Kennzahlen, Verlauf, Wallet) wird
   * mit der Kolonie aufgelöst. Gebäudestufen werden NICHT addiert – zwei halbe
   * Industriekomplexe ergeben keinen doppelten; die Anlagen der eroberten
   * Kolonie fallen mit ihr.
   */
  private static void mergeInto(GameState state, IdGenerator ids, Colony conquered, Colony target) {
    double populationMoved = 0;
    double academicsMoved = 0;
    Map<String, Double> stockMoved = Map.of();
    for (Population p : new ArrayList<>(state.populations)) {
      if (!p.colonyId.equals(conquered.id)) continue;
      populationMoved = p.currentCount;
      academicsMoved = p.academics;
      stockMoved = p.stock;
      state.populations.remove(p);
    }
    if (populationMoved > 0) {
      for (Population p : state.populations) {
        if (!p.colonyId.equals(target.id)) continue;
        p.currentCount += populationMoved;
        p.academics += academicsMoved; // Akademiker ziehen mit (Umsetzungskonzept/38, Teil D)
        // Der Vorrat der Bevölkerung zieht mit ihr um (Umsetzungskonzept/36).
        stockMoved.forEach((good, qty) -> p.stock.merge(good, qty, Double::sum));
      }
    }

    Wallet conqueredWallet = GameQueries.findWallet(state, WalletOwnerType.Population, conquered.id);
    Wallet targetWallet = GameQueries.findWallet(state, WalletOwnerType.Population, target.id);
    if (conqueredWallet != null && targetWallet != null && conqueredWallet.balance > 0) {
      Ledger.recordTx(state, ids, conqueredWallet.id, targetWallet.id, conqueredWallet.balance,
          TransactionReason.Subsidy, "Eingliederung eroberter Kolonie");
    }

    for (WarehouseEntry w : new ArrayList<>(state.warehouse)) {
      if (!w.colonyId.equals(conquered.id) || w.quantity <= 0) continue;
      Warehouse.add(state, target.id, w.productTypeId, w.quantity);
    }
    // Vorgehaltenes Elerium wandert mit – und füllt beim Ziel zuerst dessen Speicher.
    double storedFuel = EnergyStorageCommands.stored(state, conquered.id);
    if (storedFuel > 0) Warehouse.add(state, target.id, GameConstants.INFRASTRUCTURE_FUEL_PRODUCT_ID, storedFuel);
    dissolve(state, conquered);
  }

  /** Löscht restlos alles, was an dieser Kolonie hängt – sie existiert nach der Eingliederung nicht mehr. */
  private static void dissolve(GameState state, Colony colony) {
    String id = colony.id;
    state.warehouse.removeIf(w -> w.colonyId.equals(id));
    EnergyStorageCommands.remove(state, id);
    state.buildings.removeIf(b -> b.colonyId.equals(id));
    state.specializations.removeIf(s -> s.colonyId.equals(id));
    state.productionQueue.removeIf((ProductionQueueEntry e) -> e.colonyId.equals(id));
    state.recruitmentQueue.removeIf((RecruitmentQueueEntry e) -> e.colonyId.equals(id));
    state.planetStats.removeIf(s -> s.colonyId.equals(id));
    state.powerStates.removeIf(p -> p.colonyId.equals(id));
    state.populations.removeIf(p -> p.colonyId.equals(id));
    state.groundForceGroups.removeIf(g -> id.equals(g.colonyId));
    state.wallets.removeIf(w -> w.ownerType == WalletOwnerType.Population && w.ownerId.equals(id));
    state.populationHistory.remove(id);
    state.rawStandardOfLiving.remove(id);
    state.consumptionCoverage.remove(id);
    state.forgetColonyBookkeeping(id);
    // Flotten des bisherigen Eigentümers verlieren ihren Liegeplatz und stehen
    // ab jetzt im Orbit des Planeten – sie gehen nicht mit der Kolonie unter.
    for (var f : state.fleets) {
      if (id.equals(f.locationColonyId)) {
        f.locationColonyId = null;
        f.locationType = FleetLocationType.PlanetOrbit;
        f.locationPlanetId = colony.planetId;
      }
    }
    state.colonies.removeIf(c -> c.id.equals(id));
    // Der Geldschöpfungs-Höchststand (moneySupplyStates) hängt am PLANETEN,
    // nicht an der Kolonie, und bleibt deshalb bewusst unangetastet stehen:
    // sonst ließe sich über Eroberung und Neubesiedlung beliebig neues Geld
    // schöpfen.
  }

  /**
   * Der siegreiche Verband bezieht die eroberte Kolonie als Garnison
   * (Mechanik/05_..., §7: Bewegung endet im Kampfgebiet). Damit steht er
   * anschließend unter denselben Regeln wie jede andere Garnison – nachrücken,
   * auflösen, wieder einschiffen.
   */
  private static void moveGarrisonInto(GameState state, GroundForceGroup group, String colonyId) {
    if (group == null) return;
    GroundForceGroup existing = RecruitmentCommands.groundForces(state, colonyId);
    if (existing != null && existing != group) {
      for (var u : group.units) {
        if (u.activeCount + u.reserveCount > 0) {
          TroopTransportCommands.add(existing, u.unitProductTypeId, u.activeCount + u.reserveCount);
        }
      }
      state.groundForceGroups.remove(group);
      RecruitmentCommands.recalcCrewing(existing);
      return;
    }
    group.planetId = null;
    group.colonyId = colonyId;
    group.pendingMoveColonyId = null;
    group.moveCompletesAt = null;
    RecruitmentCommands.recalcCrewing(group);
  }
}
