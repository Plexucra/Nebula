package de.nebula.state;

import de.nebula.data.ProductCatalog;
import de.nebula.data.ShipCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.model.Battle;
import de.nebula.model.BattleOutcome;
import de.nebula.model.BattleStatus;
import de.nebula.model.BattleTickResult;
import de.nebula.model.DiplomaticStatus;
import de.nebula.model.Fleet;
import de.nebula.model.FleetShipGroup;
import de.nebula.model.FleetStatus;
import de.nebula.model.NotificationType;
import de.nebula.model.Player;
import de.nebula.model.ProductType;
import de.nebula.model.ShipTypeDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1:1-Portierung der "Raumgefechte"-Sektion aus {@code simulated-game-api.service.ts}
 * (Mechanik/04_..., §2-5 – Kernformeln vollständig übernommen,
 * Umsetzungskonzept/13_...md, Phase 9).
 *
 * <p>BEWUSSTE VEREINFACHUNG ggü. Mechanik/06_Blockaden_Gefechtsablauf_
 * Aufmarsch.md (dort ein deutlich größeres System): kein Blockade-Anker-
 * Objekt, keine räumliche Hierarchie (Gateway/System/Orbit/Kolonie), keine
 * Mobilmachungsrampe (25/50/100% über mehrere Ticks), kein 10×-
 * Expositionslimit je Tick, keine Mehrparteien-Gefechte, keine
 * Schaden-Redistribution bei Overkill (siehe {@link #applyDamage}).
 * Stattdessen: ein {@link Battle} ist IMMER strikt 1 Flotte gegen 1 Flotte,
 * ausgelöst durch eine explizite "Angreifen"-Aktion zwischen zwei im
 * selben System stationierten, miteinander im Krieg stehenden Flotten –
 * beide Seiten vollständig exponiert. Bodentruppen nehmen NICHT teil.</p>
 */
public final class BattleCommands {
  private BattleCommands() {
  }


  public static Battle activeBattleForFleet(GameState state, String fleetId) {
    for (Battle b : state.battles) {
      if (b.status == BattleStatus.Active && (fleetId.equals(b.attackerFleetId) || fleetId.equals(b.defenderFleetId))) return b;
    }
    return null;
  }

  private static double shipMilitaryValue(String shipProductTypeId) {
    ProductType product = ProductCatalog.find(shipProductTypeId);
    return Formulas.productionAspect(product.workHoursPerUnit, product.baseProductionHours);
  }

  /**
   * Gruppenschaden EINER Seite gegen die andere, verteilt proportional zum
   * Produktionsaufwand-Anteil jedes gegnerischen Schiffstyps (Mechanik/04_...,
   * §3), Kontermultiplikator erst danach je Typenpaar angewendet (§4).
   */
  private static Map<String, Double> computeSideDamage(List<FleetShipGroup> attackerShips, List<FleetShipGroup> defenderShips) {
    Map<String, Double> defenderCostByType = new HashMap<>();
    double totalDefenderCost = 0;
    for (FleetShipGroup d : defenderShips) {
      if (d.quantity <= 0) continue;
      double cost = shipMilitaryValue(d.shipProductTypeId) * d.quantity;
      defenderCostByType.put(d.shipProductTypeId, cost);
      totalDefenderCost += cost;
    }
    Map<String, Double> damageByType = new HashMap<>();
    if (totalDefenderCost <= 0) return damageByType;
    for (FleetShipGroup atk : attackerShips) {
      if (atk.quantity <= 0) continue;
      ShipTypeDef atkDef = ShipCatalog.find(atk.shipProductTypeId);
      double rawGroupDamage = atk.quantity * shipMilitaryValue(atk.shipProductTypeId) * Formulas.COMBAT_DAMAGE_FACTOR;
      for (Map.Entry<String, Double> e : defenderCostByType.entrySet()) {
        ShipTypeDef defDef = ShipCatalog.find(e.getKey());
        double share = e.getValue() / totalDefenderCost;
        double mult = Formulas.counterMultiplier(atkDef.countersClass == defDef.shipClass, defDef.countersClass == atkDef.shipClass);
        damageByType.merge(e.getKey(), rawGroupDamage * share * mult, Double::sum);
      }
    }
    return damageByType;
  }

  private record DamageResult(List<FleetShipGroup> ships, Map<String, Double> residual, Map<String, Integer> losses) {
  }

  /**
   * Restschaden-Formel (Mechanik/04_..., §5): {@code neu = alt + Schaden},
   * {@code Verluste = floor(neu / Haltbarkeit)}, {@code Rest = neu - Verluste × Haltbarkeit}.
   * Verlustzahl auf den tatsächlichen Bestand gedeckelt – überschüssiger
   * Schaden verfällt dabei (keine Overkill-Redistribution auf andere Typen).
   */
  private static DamageResult applyDamage(List<FleetShipGroup> ships, Map<String, Double> damageByType, Map<String, Double> residual) {
    Map<String, Integer> losses = new HashMap<>();
    Map<String, Double> nextResidual = new HashMap<>(residual);
    List<FleetShipGroup> nextShips = new ArrayList<>();
    for (FleetShipGroup s : ships) {
      Double dmg = damageByType.get(s.shipProductTypeId);
      if (dmg == null || s.quantity <= 0) {
        nextShips.add(s);
        continue;
      }
      double durability = shipMilitaryValue(s.shipProductTypeId) * Formulas.COMBAT_DURABILITY_FACTOR;
      double total = nextResidual.getOrDefault(s.shipProductTypeId, 0.0) + dmg;
      int lostCount = (int) Math.min(Math.floor(total / durability), s.quantity);
      nextResidual.put(s.shipProductTypeId, lostCount >= s.quantity ? 0.0 : total - lostCount * durability);
      if (lostCount > 0) {
        losses.put(s.shipProductTypeId, lostCount);
        FleetShipGroup updated = new FleetShipGroup(s.shipProductTypeId, s.quantity - lostCount);
        nextShips.add(updated);
      } else {
        nextShips.add(s);
      }
    }
    return new DamageResult(nextShips, nextResidual, losses);
  }

  /** Alle noch laufenden Gefechte, die eine Flotte des angemeldeten Kommandanten betreffen (Angreifer ODER Verteidiger). */
  public static List<Battle> activeBattles(GameState state, String playerId) {
    return state.battles.stream()
        .filter(b -> b.status == BattleStatus.Active && (b.attackerId.equals(playerId) || b.defenderId.equals(playerId)))
        .toList();
  }

  public static Battle battle(GameState state, String id) {
    return state.battles.stream().filter(b -> b.id.equals(id)).findFirst().orElse(null);
  }

  /** Beendete Gefechte des angemeldeten Kommandanten, neueste zuerst – reines Kampfprotokoll für die UI. */
  public static List<Battle> battleHistory(GameState state, String playerId) {
    return state.battles.stream()
        .filter(b -> b.status == BattleStatus.Ended && (b.attackerId.equals(playerId) || b.defenderId.equals(playerId)))
        .sorted((a, b) -> Long.compare(b.endedAt != null ? b.endedAt : 0, a.endedAt != null ? a.endedAt : 0))
        .toList();
  }

  /** Absichtlich UNGEFILTERT nach angemeldetem Kommandant – der Kampfbericht ist über den unerratbaren Token teilbar. */
  public static Battle battleByReportToken(GameState state, String token) {
    return state.battles.stream().filter(b -> token.equals(b.reportToken)).findFirst().orElse(null);
  }

  /** Eigene, im System stationierte, gegnerische (im Krieg stehende) Flotten MIT AKTIVER BLOCKADE – Kandidaten für "Angreifen". */
  public static List<Fleet> attackableFleetsInSystem(GameState state, String playerId, String systemId) {
    if (playerId == null) return List.of();
    Set<String> atWarWith = new HashSet<>();
    for (var r : state.diplomaticRelations) {
      if (r.status == DiplomaticStatus.War && (r.playerAId.equals(playerId) || r.playerBId.equals(playerId))) {
        atWarWith.add(r.playerAId.equals(playerId) ? r.playerBId : r.playerAId);
      }
    }
    Set<String> blockadingFleetIds = new HashSet<>();
    for (var b : state.blockades) if (b.systemId.equals(systemId)) blockadingFleetIds.add(b.fleetId);
    return state.fleets.stream()
        .filter(f -> f.systemId.equals(systemId) && f.status == FleetStatus.Stationed && !f.ownerId.equals(playerId)
            && atWarWith.contains(f.ownerId) && f.ships.stream().anyMatch(s -> s.quantity > 0) && blockadingFleetIds.contains(f.id))
        .toList();
  }

  /** Startet ein Gefecht zwischen der eigenen {@code attackerFleetId} und einer gegnerischen, blockierenden Flotte im selben System. */
  public static void engageBattle(GameState state, IdGenerator ids, String playerId, String attackerFleetId, String defenderFleetId) {
    Player me = GameQueries.requirePlayer(state, playerId);
    Fleet attackerFleet = FleetCommands.requireOwnFleet(state, playerId, attackerFleetId);
    Fleet defenderFleet = findFleet(state, defenderFleetId);
    if (defenderFleet == null) throw new CommandException("Unbekannte Zielflotte.");
    if (defenderFleet.ownerId.equals(me.id)) throw new CommandException("Ein Angriff auf die eigene Flotte ist nicht möglich.");
    if (attackerFleet.status != FleetStatus.Stationed || defenderFleet.status != FleetStatus.Stationed) {
      throw new CommandException("Beide Flotten müssen stationiert sein, keine von ihnen darf unterwegs sein.");
    }
    if (!attackerFleet.systemId.equals(defenderFleet.systemId)) throw new CommandException("Die Flotten befinden sich nicht im selben System.");
    if (DiplomacyCommands.diplomaticStatus(state, me.id, defenderFleet.ownerId) != DiplomaticStatus.War) {
      throw new CommandException("Ein Angriff ist nur im Krieg möglich – erklären Sie zuerst den Krieg (Diplomatie).");
    }
    if (state.blockades.stream().noneMatch(b -> b.fleetId.equals(defenderFleetId))) {
      throw new CommandException("Diese Flotte hat keine Blockade gebildet und ist daher nicht angreifbar.");
    }
    if (attackerFleet.ships.stream().noneMatch(s -> s.quantity > 0) || defenderFleet.ships.stream().noneMatch(s -> s.quantity > 0)) {
      throw new CommandException("Eine der beiden Flotten hat keine Kampfschiffe.");
    }
    if (activeBattleForFleet(state, attackerFleetId) != null) throw new CommandException("Ihre Flotte befindet sich bereits in einem laufenden Gefecht.");
    if (activeBattleForFleet(state, defenderFleetId) != null) throw new CommandException("Die Zielflotte befindet sich bereits in einem laufenden Gefecht.");

    long t = Clock.now();
    Battle battle = new Battle();
    battle.id = ids.next("btl");
    battle.reportToken = ids.randomToken();
    battle.systemId = attackerFleet.systemId;
    battle.attackerId = me.id;
    battle.defenderId = defenderFleet.ownerId;
    battle.attackerFleetId = attackerFleetId;
    battle.defenderFleetId = defenderFleetId;
    battle.status = BattleStatus.Active;
    battle.startedAt = t;
    battle.nextTickAt = t + (long) Clock.hoursToMs(Formulas.COMBAT_TICK_HOURS);
    battle.ticksResolved = 0;
    battle.attackerResidualDamage = new HashMap<>();
    battle.defenderResidualDamage = new HashMap<>();
    battle.ticks = new ArrayList<>();
    battle.endedAt = null;
    battle.outcome = null;
    state.battles.add(battle);
    GameEvents.schedule(state, GameEventType.BATTLE_ROUND, battle.id, battle.nextTickAt);

    String reportLink = "/kampfbericht/" + battle.reportToken;
    Notifications.notifyPlayer(state, ids, NotificationType.Warnung, Notifications.CODE_BATTLE_STARTED,
        me.name + " greift Ihre Flotte \"" + defenderFleet.name + "\" an!", defenderFleet.ownerId, reportLink);
    Notifications.notifyPlayer(state, ids, NotificationType.Warnung, Notifications.CODE_BATTLE_STARTED,
        "Sie greifen die Flotte \"" + defenderFleet.name + "\" an!", me.id, reportLink);
  }

  private static Fleet findFleet(GameState state, String fleetId) {
    for (Fleet f : state.fleets) if (f.id.equals(fleetId)) return f;
    return null;
  }

  /**
   * Rückzug: die zurückziehende Seite feuert in diesem letzten Tick NICHT
   * mehr selbst, die Gegenseite aber noch einmal (ein finaler einseitiger
   * Schadens-Tick), danach endet das Gefecht sofort ({@code outcome: Retreat}).
   */
  public static void retreatFromBattle(GameState state, IdGenerator ids, String playerId, String battleId) {
    Player me = GameQueries.requirePlayer(state, playerId);
    Battle battle = battle(state, battleId);
    if (battle == null) throw new CommandException("Unbekanntes Gefecht.");
    if (battle.status != BattleStatus.Active) throw new CommandException("Dieses Gefecht ist bereits beendet.");
    if (!battle.attackerId.equals(me.id) && !battle.defenderId.equals(me.id)) throw new CommandException("Dieses Gefecht betrifft Sie nicht.");
    resolveBattleTick(state, ids, battle, battle.attackerId.equals(me.id) ? "attacker" : "defender", Clock.now());
  }

  /**
   * Ereignis {@code BATTLE_ROUND}: die nächste Gefechtsrunde ist fällig. Veraltet,
   * wenn das Gefecht beendet ist oder (nach einem Rückzug) eine andere Rundenzeit
   * trägt. Die Folgerunde wird in {@link #resolveBattleTick} ab {@code at} geplant.
   */
  static void round(GameState state, IdGenerator ids, String battleId, long at) {
    Battle battle = battle(state, battleId);
    if (battle == null || battle.status != BattleStatus.Active || battle.nextTickAt != at) return;
    resolveBattleTick(state, ids, battle, null, at);
  }

  /** {@code t} ist die Zeit dieser Runde – die Ereigniszeit, nicht die Abarbeitungszeit (siehe {@link GameEvents}). */
  private static void resolveBattleTick(GameState state, IdGenerator ids, Battle battle, String retreatingSide, long t) {
    Fleet attackerFleet = findFleet(state, battle.attackerFleetId);
    Fleet defenderFleet = findFleet(state, battle.defenderFleetId);
    if (attackerFleet == null || defenderFleet == null) {
      battle.status = BattleStatus.Ended;
      battle.endedAt = t;
      battle.outcome = null;
      battle.attackerResidualDamage = new HashMap<>();
      battle.defenderResidualDamage = new HashMap<>();
      GameEvents.cancel(state, GameEventType.BATTLE_ROUND, battle.id);
      return;
    }

    // Bestand VOR dem Schaden festhalten, bevor die Flotten überschrieben werden –
    // der Kampfbericht meldet die zu Tickbeginn kampffähigen, also an diesem Tick
    // teilnehmenden Schiffe (siehe BattleTickResult, Mechanik/04_..., §1).
    List<FleetShipGroup> attackerShipsBefore = attackerFleet.ships;
    List<FleetShipGroup> defenderShipsBefore = defenderFleet.ships;

    Map<String, Double> damageToDefender = "attacker".equals(retreatingSide) ? Map.of() : computeSideDamage(attackerShipsBefore, defenderShipsBefore);
    Map<String, Double> damageToAttacker = "defender".equals(retreatingSide) ? Map.of() : computeSideDamage(defenderShipsBefore, attackerShipsBefore);
    DamageResult defApplied = applyDamage(defenderShipsBefore, damageToDefender, battle.defenderResidualDamage);
    DamageResult atkApplied = applyDamage(attackerShipsBefore, damageToAttacker, battle.attackerResidualDamage);

    attackerFleet.ships = atkApplied.ships();
    defenderFleet.ships = defApplied.ships();

    BattleTickResult tickResult = new BattleTickResult();
    tickResult.tick = battle.ticksResolved + 1;
    tickResult.atTime = t;
    tickResult.attackerShipsBefore = attackerShipsBefore;
    tickResult.defenderShipsBefore = defenderShipsBefore;
    tickResult.attackerLosses = atkApplied.losses();
    tickResult.defenderLosses = defApplied.losses();

    boolean defenderDestroyed = defApplied.ships().stream().allMatch(s -> s.quantity <= 0);
    boolean attackerDestroyed = atkApplied.ships().stream().allMatch(s -> s.quantity <= 0);

    BattleOutcome outcome = null;
    BattleStatus status = BattleStatus.Active;
    if (retreatingSide != null) {
      outcome = BattleOutcome.Retreat;
      status = BattleStatus.Ended;
    } else if (attackerDestroyed && defenderDestroyed) {
      // Beide gleichzeitig vernichtet: Verteidiger gilt als erfolgreich verteidigt (Gleichstand-Auflösung).
      outcome = BattleOutcome.DefenderVictory;
      status = BattleStatus.Ended;
    } else if (defenderDestroyed) {
      outcome = BattleOutcome.AttackerVictory;
      status = BattleStatus.Ended;
    } else if (attackerDestroyed) {
      outcome = BattleOutcome.DefenderVictory;
      status = BattleStatus.Ended;
    }

    battle.status = status;
    battle.ticksResolved += 1;
    battle.nextTickAt = t + (long) Clock.hoursToMs(Formulas.COMBAT_TICK_HOURS);
    battle.attackerResidualDamage = status == BattleStatus.Ended ? new HashMap<>() : atkApplied.residual();
    battle.defenderResidualDamage = status == BattleStatus.Ended ? new HashMap<>() : defApplied.residual();
    battle.ticks.add(tickResult);
    battle.endedAt = status == BattleStatus.Ended ? t : null;
    battle.outcome = outcome;
    if (status == BattleStatus.Active) GameEvents.schedule(state, GameEventType.BATTLE_ROUND, battle.id, battle.nextTickAt);
    else GameEvents.cancel(state, GameEventType.BATTLE_ROUND, battle.id);

    if (status == BattleStatus.Ended) {
      String attackerName = state.players.stream().filter(p -> p.id.equals(battle.attackerId)).map(p -> p.name).findFirst().orElse("?");
      String defenderName = state.players.stream().filter(p -> p.id.equals(battle.defenderId)).map(p -> p.name).findFirst().orElse("?");
      String summary = outcome == BattleOutcome.AttackerVictory
          ? attackerName + " hat die Flotte von " + defenderName + " vernichtet."
          : outcome == BattleOutcome.DefenderVictory
          ? defenderName + " hat den Angriff von " + attackerName + " abgewehrt."
          : ("attacker".equals(retreatingSide) ? attackerName : defenderName) + " hat sich aus dem Gefecht zurückgezogen.";
      String reportLink = "/kampfbericht/" + battle.reportToken;
      Notifications.notifyPlayer(state, ids, NotificationType.Warnung, Notifications.CODE_BATTLE_ENDED, summary, battle.attackerId, reportLink);
      Notifications.notifyPlayer(state, ids, NotificationType.Warnung, Notifications.CODE_BATTLE_ENDED, summary, battle.defenderId, reportLink);
    }
    BlockadeCommands.pruneEmptyBlockades(state);
    FleetCommands.removeDestroyedFleets(state);
  }
}
