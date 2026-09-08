package de.nebula.state;

import de.nebula.data.GroundUnitCatalog;
import de.nebula.data.ProductCatalog;
import de.nebula.engine.Clock;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.BattleOutcome;
import de.nebula.model.BattleStatus;
import de.nebula.model.Colony;
import de.nebula.model.DiplomaticStatus;
import de.nebula.model.GroundBattle;
import de.nebula.model.GroundBattlePhase;
import de.nebula.model.GroundBattleTickResult;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.GroundForceUnitStack;
import de.nebula.model.GroundUnitClass;
import de.nebula.model.GroundUnitTypeDef;
import de.nebula.model.NotificationType;
import de.nebula.model.PlanetStats;
import de.nebula.model.Player;
import de.nebula.model.Population;
import de.nebula.model.ProductType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bodenkampf (Mechanik/05_Bodentruppen_und_Bodenkrieg.md §2-4, §10-12, plus
 * die Belagerungs-Konkretisierung des Nutzers) – der Durchgang, den
 * {@code LandingCommands} ausdrücklich offengelassen hatte. Ein gelandeter
 * Verband greift EINE Kolonie auf demselben Planeten an.
 *
 * <p><b>Das Gefecht hat zwei Phasen</b> ({@link GroundBattlePhase}), und der
 * Fall der Garnison ist NICHT sein Ende:</p>
 *
 * <ol>
 *   <li><b>Kampfticks</b> wie beim Angriff auf eine Blockade
 *       ({@link BattleCommands}) – bewusst dieselben Kernformeln wie im Raum
 *       (Mechanik/04_..., §2-5: Produktionsaufwand als militärischer Wert,
 *       proportionale Schadensverteilung, Kontermatrix, Restschaden), denn die
 *       Konzeption beschreibt den Bodenkampf ausdrücklich als denselben
 *       Gefechtskern mit eigenem Kontersystem (§3). Eigen ist hier nur, was
 *       die Konzeption eigens nennt: nur AKTIVE Drohnen kämpfen und sind Ziel
 *       (§3), fallende Drohnen reißen ihre Soldaten mit (§4), freigewordene
 *       Soldaten aktivieren Reserven nach (§4). Soldaten sind in dieser Phase
 *       ausschließlich Bediener und kämpfen nicht selbst.</li>
 *   <li><b>Belagerungsticks</b>, sobald keine aktivierbaren Drohnen mehr in
 *       der Kolonie stehen (§10). Jetzt ist alles umgekehrt: es kämpfen nur
 *       noch die SOLDATEN des Angreifers gegen aufständische Zivilisten, kein
 *       Material zählt mehr, und über den Ausgang entscheidet allein die
 *       Loyalität. Erst wenn sie unter
 *       {@code Formulas.SIEGE_SURRENDER_LOYALTY_PCT} fällt, geht die Kolonie
 *       über ({@link ColonyConquest}).</li>
 * </ol>
 *
 * <p>Der Phasenwechsel geht in BEIDE Richtungen und wird bei jedem Tick neu
 * entschieden: trifft mitten in der Belagerung wieder eine kampffähige
 * Verteidigung ein, beginnen erneut reguläre Kampfticks; ist auch die
 * geschlagen, geht es zurück in die Belagerung.</p>
 */
public final class GroundBattleCommands {
  private GroundBattleCommands() {
  }

  private static final int NOTIFICATION_CODE_GROUND_BATTLE_STARTED = 405;
  private static final int NOTIFICATION_CODE_GROUND_BATTLE_ENDED = 406;
  private static final int NOTIFICATION_CODE_GROUND_SIEGE_BEGUN = 407;

  // --- Abfragen ------------------------------------------------------------

  public static List<GroundBattle> activeGroundBattles(GameState state, String playerId) {
    return state.groundBattles.stream()
        .filter(b -> b.status == BattleStatus.Active && (b.attackerId.equals(playerId) || b.defenderId.equals(playerId)))
        .toList();
  }

  public static List<GroundBattle> groundBattleHistory(GameState state, String playerId) {
    return state.groundBattles.stream()
        .filter(b -> b.status == BattleStatus.Ended && (b.attackerId.equals(playerId) || b.defenderId.equals(playerId)))
        .sorted((a, b) -> Long.compare(b.endedAt != null ? b.endedAt : 0, a.endedAt != null ? a.endedAt : 0))
        .toList();
  }

  public static GroundBattle groundBattle(GameState state, String id) {
    return state.groundBattles.stream().filter(b -> b.id.equals(id)).findFirst().orElse(null);
  }

  /** Absichtlich UNGEFILTERT nach angemeldetem Kommandant – der Bericht ist über den unerratbaren Token teilbar. */
  public static GroundBattle groundBattleByReportToken(GameState state, String token) {
    return state.groundBattles.stream().filter(b -> token.equals(b.reportToken)).findFirst().orElse(null);
  }

  /** Läuft an dieser Kolonie gerade ein Bodengefecht? Sperrt Bau/Rückbau (§1) und neue Handelsaktionen (§12). */
  public static boolean isUnderGroundAttack(GameState state, String colonyId) {
    return state.groundBattles.stream().anyMatch(b -> b.status == BattleStatus.Active && b.colonyId.equals(colonyId));
  }

  public static GroundBattle activeBattleForGroup(GameState state, String groupId) {
    return state.groundBattles.stream()
        .filter(b -> b.status == BattleStatus.Active && groupId.equals(b.attackerGroupId)).findFirst().orElse(null);
  }

  /**
   * Kolonien auf demselben Planeten, die dieser gelandete Verband angreifen
   * darf: fremd, im Krieg, noch nicht von diesem Verband angegriffen. Damit
   * baut das Frontend die Regel nicht nach (Umsetzungskonzept/15_...md, Auftrag 3).
   */
  public static List<Colony> attackableColoniesForGroup(GameState state, String playerId, String groupId) {
    GroundForceGroup group = group(state, groupId);
    if (group == null || group.planetId == null || !playerId.equals(group.ownerId)) return List.of();
    if (activeBattleForGroup(state, groupId) != null) return List.of();
    return ColonyCommands.coloniesOnPlanet(state, group.planetId).stream()
        .filter(c -> !c.ownerId.equals(playerId))
        .filter(c -> DiplomacyCommands.diplomaticStatus(state, playerId, c.ownerId) == DiplomaticStatus.War)
        .toList();
  }

  // --- Angriff -------------------------------------------------------------

  /**
   * Eröffnet das Gefecht. Es geht unmittelbar in die Tickfolge – der erste
   * Tick fällt nach {@code COMBAT_TICK_HOURS}, exakt wie beim Angriff auf eine
   * Blockade ({@code engageBattle}) und wie §7 es für jede Bewegung auf der
   * Planetenoberfläche vorsieht: der Verband muss erst ins Kampfgebiet.
   * Wie viele Kampfticks es werden, entscheidet allein die Gegenwehr; steht
   * nichts Kampffähiges mehr, ist es von Anfang an eine Belagerung.
   */
  public static GroundBattle engageGroundBattle(GameState state, IdGenerator ids, String playerId,
                                                 String groupId, String targetColonyId) {
    Player me = GameQueries.requirePlayer(state, playerId);
    GroundForceGroup group = group(state, groupId);
    if (group == null || !playerId.equals(group.ownerId)) throw new CommandException("Unbekannter Bodentruppenverband.");
    if (group.planetId == null) throw new CommandException("Nur ein gelandeter Verband kann angreifen.");
    if (group.pendingMoveColonyId != null) throw new CommandException("Der Verband ist gerade auf dem Weg.");
    if (activeBattleForGroup(state, groupId) != null) throw new CommandException("Dieser Verband kämpft bereits.");

    Colony target = ColonyCommands.colony(state, targetColonyId);
    if (target == null || !group.planetId.equals(target.planetId)) {
      throw new CommandException("Die Zielkolonie liegt nicht auf diesem Planeten.");
    }
    if (target.ownerId.equals(playerId)) throw new CommandException("Ein Angriff auf die eigene Kolonie ist nicht möglich.");
    if (DiplomacyCommands.diplomaticStatus(state, playerId, target.ownerId) != DiplomaticStatus.War) {
      throw new CommandException("Ein Angriff ist nur im Krieg möglich – erklären Sie zuerst den Krieg (Diplomatie).");
    }
    RecruitmentCommands.recalcCrewing(group);
    GroundForceGroup garrison = RecruitmentCommands.groundForces(state, targetColonyId);
    RecruitmentCommands.recalcCrewing(garrison);
    // Ohne Soldaten kann der Verband weder Drohnen führen (§3) noch die
    // anschließende Belagerung bestreiten – er hätte auf diesem Planeten
    // nichts auszurichten.
    if (soldierCount(group) <= 0) {
      throw new CommandException("Dieser Verband hat keine Soldaten – ohne sie kämpft keine Drohne und belagert niemand.");
    }
    // Gegen eine WEHRHAFTE Kolonie braucht es Drohnen: Soldaten allein haben
    // keine Kampfwirkung (§3) und wären im ersten Kampftick verloren. Steht
    // dort dagegen nichts Kampffähiges mehr, ist ein reiner Soldatenverband
    // genau das Richtige – dann beginnt sofort die Belagerung.
    if (activeDroneCount(garrison) > 0 && activeDroneCount(group) <= 0) {
      throw new CommandException("Diese Kolonie wird noch von Drohnen verteidigt – Soldaten allein haben keine Kampfwirkung.");
    }

    long t = Clock.now();
    GroundBattle battle = new GroundBattle();
    battle.id = ids.next("gbt");
    battle.reportToken = ids.randomToken();
    battle.planetId = group.planetId;
    battle.colonyId = targetColonyId;
    battle.attackerId = playerId;
    battle.defenderId = target.ownerId;
    battle.attackerGroupId = groupId;
    battle.status = BattleStatus.Active;
    battle.phase = GroundBattlePhase.Combat;
    battle.startedAt = t;
    battle.nextTickAt = t + (long) Clock.hoursToMs(Formulas.COMBAT_TICK_HOURS);
    battle.ticksResolved = 0;
    battle.attackerResidualDamage = new HashMap<>();
    battle.defenderResidualDamage = new HashMap<>();
    battle.ticks = new ArrayList<>();
    battle.populationAtStart = populationOf(state, targetColonyId);
    battle.defenderStrengthAtStart = activeStrength(garrison);
    battle.civilianLossRatio = 0;
    battle.endedAt = null;
    battle.outcome = null;
    state.groundBattles.add(battle);

    String reportLink = "/bodenkampfbericht/" + battle.reportToken;
    Player defender = GameQueries.requirePlayer(state, target.ownerId);
    Notifications.notify(state, ids, NotificationType.Warnung, NOTIFICATION_CODE_GROUND_BATTLE_STARTED,
        me.name + " landet Bodentruppen bei \"" + target.name + "\" und greift an!", target.id, reportLink);
    Notifications.notify(state, ids, NotificationType.Warnung, NOTIFICATION_CODE_GROUND_BATTLE_STARTED,
        "Ihr Bodenangriff auf \"" + target.name + "\" (" + defender.name + ") hat begonnen.", me.homeworldColonyId, reportLink);

    // §10: eine Kolonie ohne aktivierbare Waffenträger hat keine wirksame
    // Bodenverteidigung. Gefallen ist sie damit aber NICHT – das Gefecht
    // beginnt dann eben sofort in der Belagerungsphase, und über den Ausgang
    // entscheidet allein die Loyalität ihrer Bevölkerung.
    if (activeDroneCount(garrison) <= 0) {
      battle.phase = GroundBattlePhase.Siege;
      notifySiegeBegun(state, ids, battle);
    }
    return battle;
  }

  /**
   * Rückzug (§11): die zurückziehende Seite feuert in diesem letzten Tick
   * nicht mehr, die Gegenseite noch einmal – wie im Raum. Der Eigentümer der
   * angegriffenen Kolonie kann sich NICHT zurückziehen.
   */
  public static void retreatFromGroundBattle(GameState state, IdGenerator ids, String playerId, String battleId) {
    GroundBattle battle = groundBattle(state, battleId);
    if (battle == null) throw new CommandException("Unbekanntes Bodengefecht.");
    if (battle.status != BattleStatus.Active) throw new CommandException("Dieses Gefecht ist bereits beendet.");
    if (battle.defenderId.equals(playerId)) {
      throw new CommandException("Aus der Verteidigung der eigenen Kolonie gibt es keinen Rückzug.");
    }
    if (!battle.attackerId.equals(playerId)) throw new CommandException("Dieses Gefecht betrifft Sie nicht.");
    resolveTick(state, ids, battle, battle.attackerId);
  }

  /** Aufgerufen aus {@code GameTick}. */
  public static void processGroundBattles(GameState state, IdGenerator ids, long t) {
    List<GroundBattle> due = state.groundBattles.stream()
        .filter(b -> b.status == BattleStatus.Active && b.nextTickAt <= t).toList();
    for (GroundBattle battle : due) resolveTick(state, ids, battle, null);
  }

  // --- Gefechtsauflösung ---------------------------------------------------

  /**
   * EIN Tick des Gefechts. Welche Rechnung er ist, entscheidet allein, ob die
   * Kolonie in diesem Moment noch aktivierbare Drohnen hat: hat sie welche,
   * ist es ein regulärer Kampftick, sonst ein Belagerungstick. Der Wechsel
   * geht dadurch von selbst in beide Richtungen – trifft mitten in der
   * Belagerung wieder eine kampffähige Verteidigung ein, wird derselbe Tick
   * wieder ein Kampftick.
   */
  private static void resolveTick(GameState state, IdGenerator ids, GroundBattle battle, String retreatingPlayerId) {
    GroundForceGroup attacker = group(state, battle.attackerGroupId);
    GroundForceGroup defender = RecruitmentCommands.groundForces(state, battle.colonyId);
    Colony colony = ColonyCommands.colony(state, battle.colonyId);
    if (attacker == null || colony == null) {
      // Verband oder Kolonie sind zwischenzeitlich verschwunden – das Gefecht
      // hat kein Objekt mehr und endet ergebnislos (wie im Raum).
      endBattle(state, ids, battle, null, null);
      return;
    }
    RecruitmentCommands.recalcCrewing(defender);
    if (activeDroneCount(defender) > 0) resolveCombatTick(state, ids, battle, attacker, defender, retreatingPlayerId);
    else resolveSiegeTick(state, ids, battle, attacker, retreatingPlayerId);
  }

  /** Drohne gegen Drohne, Mechanik/04_..., §2-5 – Soldaten sind hier nur Bediener und kämpfen nicht selbst. */
  private static void resolveCombatTick(GameState state, IdGenerator ids, GroundBattle battle, GroundForceGroup attacker,
                                         GroundForceGroup defender, String retreatingPlayerId) {
    battle.phase = GroundBattlePhase.Combat;
    List<GroundForceUnitStack> attackerBefore = snapshot(attacker);
    List<GroundForceUnitStack> defenderBefore = snapshot(defender);
    double defenderStrengthBefore = activeStrength(defender);

    boolean attackerRetreats = battle.attackerId.equals(retreatingPlayerId);
    Map<String, Double> damageToDefender = attackerRetreats ? Map.of() : computeSideDamage(attackerBefore, defenderBefore);
    Map<String, Double> damageToAttacker = computeSideDamage(defenderBefore, attackerBefore);

    Map<String, Integer> defenderLosses = applyDamage(defender, damageToDefender, battle.defenderResidualDamage);
    Map<String, Integer> attackerLosses = applyDamage(attacker, damageToAttacker, battle.attackerResidualDamage);

    // §4: freigewordene Soldaten aktivieren Reserve-Drohnen nach – erst danach
    // steht fest, ob eine Seite noch aktivierbare Waffenträger hat (§10).
    RecruitmentCommands.recalcCrewing(attacker);
    RecruitmentCommands.recalcCrewing(defender);

    // §2: Zivilverluste in Relation zu den tatsächlichen militärischen
    // Verlusten der VERTEIDIGER, bezogen auf deren Ausgangsstärke.
    double defenderValueLost = defenderStrengthBefore - activeStrength(defender);
    double civilianFraction = Formulas.civilianLossFraction(defenderValueLost, battle.defenderStrengthAtStart);
    double civiliansLost = killCivilians(state, battle.colonyId, battle.populationAtStart * civilianFraction);
    battle.civilianLossRatio = Formulas.clamp(battle.civilianLossRatio + civilianFraction, 0, 1);

    GroundBattleTickResult tick = recordTick(battle, GroundBattlePhase.Combat, attackerBefore, defenderBefore,
        attackerLosses, defenderLosses, civiliansLost);
    PlanetStats stats = ColonyCommands.colonyStats(state, battle.colonyId);
    tick.loyaltyPctBefore = stats != null ? stats.loyaltyPct : 0;
    tick.loyaltyPctAfter = tick.loyaltyPctBefore;

    boolean defenderBeaten = activeDroneCount(defender) <= 0;
    boolean attackerBeaten = activeDroneCount(attacker) <= 0;

    if (attackerRetreats) {
      // §11: erfolgreicher Rückzug führt den Verband zurück auf die
      // Planetenoberfläche – dort steht er ohnehin, er löst sich nur vom Feind.
      endBattle(state, ids, battle, BattleOutcome.Retreat, null);
      return;
    }
    if (attackerBeaten) {
      // §10: bei vollständiger Niederlage gehen auch alle Reserven verloren –
      // nicht erbeutet. Gleichstand (beide aufgerieben) fällt zugunsten des
      // VERTEIDIGERS: ohne Soldaten kann der Angreifer die anschließende
      // Belagerung gar nicht führen, sein Sieg wäre folgenlos.
      state.groundForceGroups.remove(attacker);
      endBattle(state, ids, battle, BattleOutcome.DefenderVictory, null);
      return;
    }
    if (defenderBeaten) {
      // Die militärische Gegenwehr ist gebrochen – aber das Gefecht endet NICHT.
      // Ab jetzt entscheidet die Loyalität (Belagerung); die geschlagene
      // Garnison samt ihrer Reserven ist nach §10 verloren.
      if (defender != null) state.groundForceGroups.remove(defender);
      battle.phase = GroundBattlePhase.Siege;
      notifySiegeBegun(state, ids, battle);
    }
  }

  /**
   * Belagerungstick (Nutzervorgabe). Hier zählen AUSSCHLIESSLICH die Soldaten
   * des Angreifers und die aufständischen Zivilisten – keine Drohnen, kein
   * Material, keine Kampfwerte. Drei Rechnungen:
   *
   * <ol>
   *   <li>Die Loyalität sinkt um genau das Verhältnis Soldaten zu Bevölkerung
   *       (absolut, in Prozentpunkten).</li>
   *   <li>10 % der Bevölkerung erheben sich; beide Seiten töten nach
   *       {@code floor(Kämpfer × Stärke / 12)}, Rebellen mit 30 % Stärke.</li>
   *   <li>Unter {@code SIEGE_SURRENDER_LOYALTY_PCT} geht die Kolonie über.</li>
   * </ol>
   */
  private static void resolveSiegeTick(GameState state, IdGenerator ids, GroundBattle battle,
                                        GroundForceGroup attacker, String retreatingPlayerId) {
    battle.phase = GroundBattlePhase.Siege;
    List<GroundForceUnitStack> attackerBefore = snapshot(attacker);
    PlanetStats stats = ColonyCommands.colonyStats(state, battle.colonyId);
    double loyaltyBefore = stats != null ? stats.loyaltyPct : 0;

    if (battle.attackerId.equals(retreatingPlayerId)) {
      // Ein Rückzug aus der Belagerung kostet nichts weiter: es steht keine
      // Streitmacht mehr da, die einen Abzug bestrafen könnte – der einseitige
      // Schlusstick aus §11 hat nur im Kampftick ein Gegenüber.
      GroundBattleTickResult tick = recordTick(battle, GroundBattlePhase.Siege, attackerBefore, List.of(),
          Map.of(), Map.of(), 0);
      tick.loyaltyPctBefore = loyaltyBefore;
      tick.loyaltyPctAfter = loyaltyBefore;
      endBattle(state, ids, battle, BattleOutcome.Retreat, null);
      return;
    }

    int soldiers = soldierCount(attacker);
    double population = populationOf(state, battle.colonyId);
    int rebels = (int) Math.floor(population * Formulas.SIEGE_REBEL_POPULATION_SHARE);

    // Beide Seiten rechnen mit dem Bestand VOR dem Tick ab – gleichzeitig, wie
    // im Kampftick auch (Mechanik/04_..., §1).
    int rebelsLost = Math.min(rebels, Formulas.siegeKills(soldiers, 1.0));
    int soldiersLost = Math.min(soldiers, Formulas.siegeKills(rebels, Formulas.SIEGE_REBEL_COMBAT_STRENGTH));

    double civiliansLost = killCivilians(state, battle.colonyId, rebelsLost);
    battle.civilianLossRatio = Formulas.clamp(
        battle.civilianLossRatio + (battle.populationAtStart > 0 ? civiliansLost / battle.populationAtStart : 0), 0, 1);
    Map<String, Integer> attackerLosses = new HashMap<>();
    if (soldiersLost > 0) {
      removeSoldiers(attacker, soldiersLost);
      attackerLosses.put(GameConstants.SOLDIER_PRODUCT_ID, soldiersLost);
      // Weniger Soldaten heißt weniger kommandierte Drohnen – die Drohnen
      // selbst sind an der Belagerung unbeteiligt, ihre Bedienung nicht.
      RecruitmentCommands.recalcCrewing(attacker);
    }

    double loyaltyAfter = loyaltyBefore;
    if (stats != null) {
      loyaltyAfter = Formulas.clamp(loyaltyBefore - Formulas.siegeLoyaltyDropPct(soldiers, population), 0, 100);
      stats.loyaltyPct = loyaltyAfter;
    }

    GroundBattleTickResult tick = recordTick(battle, GroundBattlePhase.Siege, attackerBefore, List.of(),
        attackerLosses, Map.of(), civiliansLost);
    tick.attackerSoldiers = soldiers;
    tick.rebels = rebels;
    tick.loyaltyPctBefore = loyaltyBefore;
    tick.loyaltyPctAfter = loyaltyAfter;

    if (soldierCount(attacker) <= 0) {
      // Ohne Soldaten ist die Belagerung gebrochen – und mit ihnen ist auch
      // keine Drohne mehr kommandierbar, der Verband ist als Ganzes erledigt.
      state.groundForceGroups.remove(attacker);
      endBattle(state, ids, battle, BattleOutcome.DefenderVictory, null);
      return;
    }
    if (loyaltyAfter < Formulas.SIEGE_SURRENDER_LOYALTY_PCT) {
      String summary = ColonyConquest.conquer(state, ids, battle, attacker);
      endBattle(state, ids, battle, BattleOutcome.AttackerVictory, summary);
    }
  }

  private static GroundBattleTickResult recordTick(GroundBattle battle, GroundBattlePhase phase,
                                                    List<GroundForceUnitStack> attackerBefore,
                                                    List<GroundForceUnitStack> defenderBefore,
                                                    Map<String, Integer> attackerLosses,
                                                    Map<String, Integer> defenderLosses, double civiliansLost) {
    long t = Clock.now();
    GroundBattleTickResult tick = new GroundBattleTickResult();
    tick.tick = battle.ticksResolved + 1;
    tick.atTime = t;
    tick.phase = phase;
    tick.attackerUnitsBefore = attackerBefore;
    tick.defenderUnitsBefore = defenderBefore;
    tick.attackerLosses = attackerLosses;
    tick.defenderLosses = defenderLosses;
    tick.civiliansLost = civiliansLost;
    battle.ticks.add(tick);
    battle.ticksResolved += 1;
    battle.nextTickAt = t + (long) Clock.hoursToMs(Formulas.COMBAT_TICK_HOURS);
    return tick;
  }

  private static void notifySiegeBegun(GameState state, IdGenerator ids, GroundBattle battle) {
    Colony colony = ColonyCommands.colony(state, battle.colonyId);
    String colonyName = colony != null ? colony.name : "?";
    String reportLink = "/bodenkampfbericht/" + battle.reportToken;
    String attackerName = GameQueries.ownerDisplayName(state, battle.attackerId);
    Player attacker = state.players.stream().filter(p -> p.id.equals(battle.attackerId)).findFirst().orElse(null);
    Player defender = state.players.stream().filter(p -> p.id.equals(battle.defenderId)).findFirst().orElse(null);
    if (attacker != null) {
      Notifications.notify(state, ids, NotificationType.Warnung, NOTIFICATION_CODE_GROUND_SIEGE_BEGUN,
          "Die Verteidigung von \"" + colonyName + "\" ist gebrochen – die Belagerung hat begonnen.",
          attacker.homeworldColonyId, reportLink);
    }
    if (defender != null) {
      Notifications.notify(state, ids, NotificationType.Warnung, NOTIFICATION_CODE_GROUND_SIEGE_BEGUN,
          attackerName + " belagert \"" + colonyName + "\" – ohne neue Bodentruppen fällt die Kolonie, sobald die Loyalität unter "
              + (long) Formulas.SIEGE_SURRENDER_LOYALTY_PCT + " % sinkt.", defender.homeworldColonyId, reportLink);
    }
  }

  /** Schadenswurf EINER Seite, exakt nach Mechanik/04_..., §3-4 – nur aktive Drohnen greifen an, nur aktive Drohnen sind Ziel. */
  private static Map<String, Double> computeSideDamage(List<GroundForceUnitStack> attackers, List<GroundForceUnitStack> defenders) {
    Map<String, Double> defenderCostByType = new HashMap<>();
    double totalDefenderCost = 0;
    for (GroundForceUnitStack d : defenders) {
      if (!isDrone(d.unitProductTypeId) || d.activeCount <= 0) continue;
      double cost = unitMilitaryValue(d.unitProductTypeId) * d.activeCount;
      defenderCostByType.put(d.unitProductTypeId, cost);
      totalDefenderCost += cost;
    }
    Map<String, Double> damageByType = new HashMap<>();
    if (totalDefenderCost <= 0) return damageByType;
    for (GroundForceUnitStack atk : attackers) {
      if (!isDrone(atk.unitProductTypeId) || atk.activeCount <= 0) continue;
      GroundUnitTypeDef atkDef = GroundUnitCatalog.find(atk.unitProductTypeId);
      double rawGroupDamage = atk.activeCount * unitMilitaryValue(atk.unitProductTypeId) * Formulas.COMBAT_DAMAGE_FACTOR;
      for (Map.Entry<String, Double> e : defenderCostByType.entrySet()) {
        GroundUnitTypeDef defDef = GroundUnitCatalog.find(e.getKey());
        double share = e.getValue() / totalDefenderCost;
        double mult = Formulas.counterMultiplier(atkDef.countersClass == defDef.unitClass, defDef.countersClass == atkDef.unitClass);
        damageByType.merge(e.getKey(), rawGroupDamage * share * mult, Double::sum);
      }
    }
    return damageByType;
  }

  /**
   * Verbucht den Schaden IM Verband (Restschaden-Formel aus Mechanik/04_...,
   * §5) und nimmt anschließend die mit den Drohnen gefallenen Soldaten (§4).
   * Liefert die Verluste dieses Ticks je ProductType.
   */
  private static Map<String, Integer> applyDamage(GroundForceGroup group, Map<String, Double> damageByType,
                                                   Map<String, Double> residual) {
    Map<String, Integer> losses = new HashMap<>();
    if (group == null) return losses;
    int activeDronesBefore = activeDroneCount(group);
    int dronesLost = 0;
    for (GroundForceUnitStack u : group.units) {
      Double dmg = damageByType.get(u.unitProductTypeId);
      if (dmg == null || u.activeCount <= 0) continue;
      double durability = unitMilitaryValue(u.unitProductTypeId) * Formulas.COMBAT_DURABILITY_FACTOR;
      double total = residual.getOrDefault(u.unitProductTypeId, 0.0) + dmg;
      int lost = (int) Math.min(Math.floor(total / durability), u.activeCount);
      residual.put(u.unitProductTypeId, lost >= u.activeCount ? 0.0 : total - lost * durability);
      if (lost <= 0) continue;
      u.activeCount -= lost;
      losses.put(u.unitProductTypeId, lost);
      dronesLost += lost;
    }
    if (dronesLost > 0) {
      for (GroundForceUnitStack u : group.units) {
        if (!GameConstants.SOLDIER_PRODUCT_ID.equals(u.unitProductTypeId)) continue;
        int lostSoldiers = Formulas.soldiersLostWithDrones(u.activeCount, activeDronesBefore, dronesLost);
        if (lostSoldiers <= 0) continue;
        u.activeCount -= lostSoldiers;
        losses.merge(u.unitProductTypeId, lostSoldiers, Integer::sum);
      }
    }
    group.units.removeIf(u -> u.activeCount <= 0 && u.reserveCount <= 0);
    return losses;
  }

  /** Tötet Zivilbevölkerung und liefert die tatsächlich verlorene (ganze) Anzahl – Menschen sind ganze Stücke. */
  private static double killCivilians(GameState state, String colonyId, double amount) {
    if (amount <= 0) return 0;
    for (Population p : state.populations) {
      if (!p.colonyId.equals(colonyId)) continue;
      double lost = Math.min(p.currentCount, Math.floor(amount));
      p.currentCount -= lost;
      return lost;
    }
    return 0;
  }

  private static void endBattle(GameState state, IdGenerator ids, GroundBattle battle, BattleOutcome outcome, String conquestSummary) {
    long t = Clock.now();
    battle.status = BattleStatus.Ended;
    battle.endedAt = t;
    battle.outcome = outcome;
    battle.attackerResidualDamage = new HashMap<>();
    battle.defenderResidualDamage = new HashMap<>();

    String attackerName = GameQueries.ownerDisplayName(state, battle.attackerId);
    String defenderName = GameQueries.ownerDisplayName(state, battle.defenderId);
    Colony colony = ColonyCommands.colony(state, battle.colonyId);
    String colonyName = colony != null ? colony.name : "?";
    String summary = conquestSummary != null ? conquestSummary : switch (outcome == null ? BattleOutcome.Retreat : outcome) {
      case AttackerVictory -> attackerName + " hat \"" + colonyName + "\" erobert.";
      case DefenderVictory -> defenderName + " hat den Bodenangriff von " + attackerName + " auf \"" + colonyName + "\" abgewehrt.";
      case Retreat -> attackerName + " hat den Bodenangriff auf \"" + colonyName + "\" abgebrochen.";
    };
    String reportLink = "/bodenkampfbericht/" + battle.reportToken;
    Player attacker = state.players.stream().filter(p -> p.id.equals(battle.attackerId)).findFirst().orElse(null);
    Player defender = state.players.stream().filter(p -> p.id.equals(battle.defenderId)).findFirst().orElse(null);
    if (attacker != null) {
      Notifications.notify(state, ids, NotificationType.Warnung, NOTIFICATION_CODE_GROUND_BATTLE_ENDED, summary,
          attacker.homeworldColonyId, reportLink);
    }
    if (defender != null) {
      Notifications.notify(state, ids, NotificationType.Warnung, NOTIFICATION_CODE_GROUND_BATTLE_ENDED, summary,
          defender.homeworldColonyId, reportLink);
    }
  }

  // --- kleine Helfer -------------------------------------------------------

  static GroundForceGroup group(GameState state, String groupId) {
    return state.groundForceGroups.stream().filter(g -> g.id.equals(groupId)).findFirst().orElse(null);
  }

  /** Militärischer Wert = Produktionsaufwand (Mechanik/04_..., §2) – identisch zur Schiffsbewertung. */
  private static double unitMilitaryValue(String unitProductTypeId) {
    ProductType product = ProductCatalog.find(unitProductTypeId);
    return Formulas.productionAspect(product.workHoursPerUnit, product.baseProductionHours);
  }

  private static boolean isDrone(String unitProductTypeId) {
    return GroundUnitCatalog.find(unitProductTypeId).unitClass != GroundUnitClass.Soldier;
  }

  /**
   * ALLE Soldaten des Verbands, aktive wie Reserve. In der Belagerung sind
   * Soldaten Akteure und keine Drohnenbediener mehr – die Unterscheidung
   * "kommandiert gerade eine Drohne" ist dort ohne Bedeutung.
   */
  static int soldierCount(GroundForceGroup group) {
    if (group == null) return 0;
    int total = 0;
    for (GroundForceUnitStack u : group.units) {
      if (GameConstants.SOLDIER_PRODUCT_ID.equals(u.unitProductTypeId)) total += u.activeCount + u.reserveCount;
    }
    return total;
  }

  /** Nimmt gefallene Soldaten zuerst aus der Reserve – aktive bleiben so lange wie möglich am Drohnenpult. */
  private static void removeSoldiers(GroundForceGroup group, int count) {
    int rest = count;
    for (GroundForceUnitStack u : group.units) {
      if (!GameConstants.SOLDIER_PRODUCT_ID.equals(u.unitProductTypeId)) continue;
      int fromReserve = Math.min(u.reserveCount, rest);
      u.reserveCount -= fromReserve;
      rest -= fromReserve;
      int fromActive = Math.min(u.activeCount, rest);
      u.activeCount -= fromActive;
      rest -= fromActive;
    }
    group.units.removeIf(u -> u.activeCount <= 0 && u.reserveCount <= 0);
  }

  static int activeDroneCount(GroundForceGroup group) {
    if (group == null) return 0;
    int total = 0;
    for (GroundForceUnitStack u : group.units) if (isDrone(u.unitProductTypeId)) total += u.activeCount;
    return total;
  }

  /** Summe des Produktionsaufwands aller AKTIVEN Drohnen – die einzige Größe, die am Boden kämpft. */
  private static double activeStrength(GroundForceGroup group) {
    if (group == null) return 0;
    double total = 0;
    for (GroundForceUnitStack u : group.units) {
      if (isDrone(u.unitProductTypeId)) total += u.activeCount * unitMilitaryValue(u.unitProductTypeId);
    }
    return total;
  }

  /** Kopie des Bestands für den Kampfbericht – der Verband selbst wird gleich verändert. */
  private static List<GroundForceUnitStack> snapshot(GroundForceGroup group) {
    List<GroundForceUnitStack> copy = new ArrayList<>();
    if (group == null) return copy;
    for (GroundForceUnitStack u : group.units) {
      GroundForceUnitStack s = new GroundForceUnitStack();
      s.unitProductTypeId = u.unitProductTypeId;
      s.activeCount = u.activeCount;
      s.reserveCount = u.reserveCount;
      copy.add(s);
    }
    return copy;
  }

  private static double populationOf(GameState state, String colonyId) {
    for (Population p : state.populations) if (p.colonyId.equals(colonyId)) return p.currentCount;
    return 0;
  }
}
