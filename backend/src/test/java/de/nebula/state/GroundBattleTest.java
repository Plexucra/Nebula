package de.nebula.state;

import de.nebula.data.WorldSeed;
import de.nebula.engine.Formulas;
import de.nebula.engine.GameConstants;
import de.nebula.model.BattleOutcome;
import de.nebula.model.BattleStatus;
import de.nebula.model.Building;
import de.nebula.model.Colony;
import de.nebula.model.GroundBattle;
import de.nebula.model.GroundBattlePhase;
import de.nebula.model.GroundForceGroup;
import de.nebula.model.GroundForceUnitStack;
import de.nebula.model.PlanetStats;
import de.nebula.model.Population;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bodenkampf und Eroberung (Mechanik/05_...md §2-4, §10-12) über die echten
 * Befehle. Die Gefechtsticks werden hier NICHT abgewartet, sondern über
 * {@code nextTickAt} fällig gestellt und mit
 * {@code GroundBattleCommands.processGroundBattles} ausgelöst – exakt das, was
 * {@code GameTick} sekündlich tut, nur ohne die Wartezeit.
 */
class GroundBattleTest {

  private record Arena(GameState state, IdGenerator ids, String attackerId, String defenderId,
                        String defenderColonyId, String defenderPlanetId) {
  }

  private static Arena newArena() {
    GameState state = new GameState();
    IdGenerator ids = new IdGenerator();
    GameStateSeeder.bootstrap(state, WorldSeed.createWorldSeed("Angreifer", "Angreiferheim", ids), ids);
    GameStateSeeder.appendPlayer(state,
        WorldSeed.createAdditionalPlayerSeed(state.systems, "Verteidiger", "Verteidigerheim", ids), ids);
    String attackerId = state.players.get(0).id;
    String defenderId = state.players.get(1).id;
    Colony defenderColony = ColonyCommands.colony(state, state.players.get(1).homeworldColonyId);
    // Die Startgarnison der Heimatwelt steht dem Test im Weg – jeder Test setzt
    // die Verteidigung selbst auf einen bekannten Bestand.
    state.groundForceGroups.removeIf(g -> defenderColony.id.equals(g.colonyId));
    return new Arena(state, ids, attackerId, defenderId, defenderColony.id, defenderColony.planetId);
  }

  /** Gelandeter Verband des Angreifers auf dem Planeten des Verteidigers. */
  private static GroundForceGroup landedGroup(Arena a, int soldiers, String droneId, int drones) {
    GroundForceGroup group = new GroundForceGroup();
    group.id = a.ids().next("gfg");
    group.ownerId = a.attackerId();
    group.planetId = a.defenderPlanetId();
    group.units = new ArrayList<>();
    if (soldiers > 0) group.units.add(stack(GameConstants.SOLDIER_PRODUCT_ID, soldiers));
    if (drones > 0) group.units.add(stack(droneId, drones));
    a.state().groundForceGroups.add(group);
    RecruitmentCommands.recalcCrewing(group);
    return group;
  }

  private static GroundForceGroup garrison(Arena a, int soldiers, String droneId, int drones) {
    GroundForceGroup group = new GroundForceGroup();
    group.id = a.ids().next("gfg");
    group.ownerId = a.defenderId();
    group.colonyId = a.defenderColonyId();
    group.units = new ArrayList<>();
    if (soldiers > 0) group.units.add(stack(GameConstants.SOLDIER_PRODUCT_ID, soldiers));
    if (drones > 0) group.units.add(stack(droneId, drones));
    a.state().groundForceGroups.add(group);
    RecruitmentCommands.recalcCrewing(group);
    return group;
  }

  private static GroundForceUnitStack stack(String productTypeId, int count) {
    GroundForceUnitStack s = new GroundForceUnitStack();
    s.unitProductTypeId = productTypeId;
    s.activeCount = 0;
    s.reserveCount = count;
    return s;
  }

  private static int total(GroundForceGroup group, String productTypeId) {
    if (group == null) return 0;
    return group.units.stream().filter(u -> u.unitProductTypeId.equals(productTypeId))
        .mapToInt(u -> u.activeCount + u.reserveCount).sum();
  }

  private static double population(Arena a, String colonyId) {
    for (Population p : a.state().populations) if (p.colonyId.equals(colonyId)) return p.currentCount;
    return 0;
  }

  private static void setPopulation(Arena a, String colonyId, double count) {
    for (Population p : a.state().populations) if (p.colonyId.equals(colonyId)) p.currentCount = count;
  }

  private static void setLoyalty(Arena a, String colonyId, double pct) {
    PlanetStats stats = ColonyCommands.colonyStats(a.state(), colonyId);
    if (stats != null) stats.loyaltyPct = pct;
  }

  /** Stellt den nächsten Kampftick sofort fällig und lässt ihn laufen – wie GameTick, nur ohne Warten. */
  private static void runTick(Arena a, GroundBattle battle) {
    battle.nextTickAt = 0;
    GroundBattleCommands.processGroundBattles(a.state(), a.ids(), System.currentTimeMillis());
  }

  private static void runUntilEnded(Arena a, GroundBattle battle, int maxTicks) {
    for (int i = 0; i < maxTicks && battle.status == BattleStatus.Active; i++) runTick(a, battle);
  }

  // --- Vorbedingungen des Angriffs -----------------------------------------

  @Test
  void angriffNurImKrieg() {
    Arena a = newArena();
    GroundForceGroup group = landedGroup(a, 100, "p_drone_light", 200);
    garrison(a, 10, "p_drone_light", 20);

    var e = assertThrows(CommandException.class, () -> GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId()));
    assertTrue(e.getMessage().contains("Krieg"), e.getMessage());
  }

  @Test
  void gegenEineWehrhafteKolonieBrauchtEsDrohnen() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 500, null, 0);
    garrison(a, 10, "p_drone_light", 20);

    var e = assertThrows(CommandException.class, () -> GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId()));
    assertTrue(e.getMessage().contains("Drohnen"), e.getMessage());
  }

  @Test
  void ohneSoldatenGehtWederKampfNochBelagerung() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 0, "p_drone_light", 200);

    var e = assertThrows(CommandException.class, () -> GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId()));
    assertTrue(e.getMessage().contains("Soldaten"), e.getMessage());
  }

  /**
   * §10 + Belagerungsregel: ohne aktivierbare Waffenträger hat die Kolonie keine
   * wirksame Verteidigung – gefallen ist sie damit aber NICHT. Das Gefecht
   * beginnt sofort in der Belagerungsphase, und weil dort nur Soldaten zählen,
   * darf ein reiner Soldatenverband das führen.
   */
  @Test
  void kolonieOhneAktivierbareDrohnenGehtSofortInDieBelagerung() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 500, null, 0); // nur Soldaten – reicht für eine Belagerung
    garrison(a, 400, null, 0); // Verteidiger hat nur Soldaten, also nichts Kampffähiges

    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());

    assertEquals(BattleStatus.Active, battle.status, "Der Fall der Verteidigung ist nicht das Ende des Gefechts");
    assertEquals(GroundBattlePhase.Siege, battle.phase);
    assertEquals(a.defenderId(), ColonyCommands.colony(a.state(), a.defenderColonyId()).ownerId,
        "Die Kolonie wechselt erst unter 2 % Loyalität den Eigentümer");
  }

  // --- Gefechtsverlauf ------------------------------------------------------

  @Test
  void uebermaechtigerAngreiferErobertDieKolonieMitZivilUndSachschaden() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 200, "p_drone_light", 1000);
    garrison(a, 4, "p_drone_heavy", 20);

    Building industry = a.state().buildings.stream()
        .filter(b -> b.colonyId.equals(a.defenderColonyId()) && b.typeId.equals("b_industry")).findFirst().orElseThrow();
    industry.level = 8;
    Warehouse.add(a.state(), a.defenderColonyId(), "p_stahl", 100);
    double populationBefore = population(a, a.defenderColonyId());

    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());
    assertEquals(BattleStatus.Active, battle.status, "Ein verteidigter Angriff beginnt nicht sofort entschieden");
    assertEquals(GroundBattlePhase.Combat, battle.phase);
    runUntilEnded(a, battle, 60);

    assertEquals(BattleStatus.Ended, battle.status);
    assertEquals(BattleOutcome.AttackerVictory, battle.outcome);
    assertTrue(battle.ticks.stream().anyMatch(t -> t.phase == GroundBattlePhase.Combat)
            && battle.ticks.stream().anyMatch(t -> t.phase == GroundBattlePhase.Siege),
        "Erst Kampfticks, dann Belagerungsticks – der Fall der Garnison beendet nichts");
    Colony colony = ColonyCommands.colony(a.state(), a.defenderColonyId());
    assertEquals(a.attackerId(), colony.ownerId, "Die Kolonie muss den Eigentümer wechseln");
    assertFalse(colony.isHomeworld, "Eine eroberte Kolonie ist nicht die Heimatwelt des Eroberers");

    PlanetStats stats = ColonyCommands.colonyStats(a.state(), a.defenderColonyId());
    assertTrue(stats.loyaltyPct < Formulas.SIEGE_SURRENDER_LOYALTY_PCT,
        "Übergeben wird erst unter der Kapitulationsschwelle – und dort bleibt die Loyalität auch");

    assertTrue(battle.civilianLossRatio > 0, "Eine aufgeriebene Verteidigung kostet Zivilbevölkerung (§2)");
    // Seit Umsetzungskonzept/34_...md skaliert die Quote mit der DICHTE der
    // Verteidigung: diese Garnison ist gegenüber der Bevölkerung schwach, ihr
    // Fall darf die Kolonie deshalb nicht halb entvölkern. Der Referenzpunkt
    // "50 % bei restlos aufgeriebener Verteidigung" gilt nur noch für eine
    // Garnison in voller Stärke (siehe zivilverlusteSkalierenMitDerGarnison).
    assertTrue(battle.civilianLossRatio < Formulas.CIVILIAN_LOSS_AT_TOTAL_DEFEAT,
        "Eine dünne Garnison darf keine 50 % Zivilverlust nach sich ziehen, war: " + battle.civilianLossRatio);
    assertTrue(population(a, a.defenderColonyId()) < populationBefore);
    assertTrue(industry.level < 8, "Materialschaden muss die Produktionsanlagen treffen (§2)");
    assertTrue(Warehouse.qty(a.state(), a.defenderColonyId(), "p_stahl") < 100,
        "Materialschaden muss die Ressourcenbestände treffen (§2)");

    // Der siegreiche Verband bezieht die Kolonie als Garnison.
    GroundForceGroup newGarrison = RecruitmentCommands.groundForces(a.state(), a.defenderColonyId());
    assertNotNull(newGarrison);
    assertEquals(a.attackerId(), newGarrison.ownerId);
    assertNull(newGarrison.planetId);
  }

  /**
   * Umsetzungskonzept/34_...md, §J 9: Der Verlust der Heimatwelt schaltet den
   * Kommandanten NICHT aus. Er behält Flotten und übrige Kolonien, sein
   * Heimatverweis wird gelöst (er zeigte sonst auf fremden Besitz), und seine
   * Post kommt weiterhin bei ihm an statt beim Eroberer.
   */
  @Test
  void derVerlustDerHeimatweltSchaltetDenKommandantenNichtAus() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 200, "p_drone_light", 1000);
    garrison(a, 4, "p_drone_heavy", 20);
    de.nebula.model.Player verlierer = a.state().players.stream()
        .filter(p -> p.id.equals(a.defenderId())).findFirst().orElseThrow();
    assertEquals(a.defenderColonyId(), verlierer.homeworldColonyId, "Vorbedingung: die angegriffene Kolonie IST die Heimatwelt");
    long flottenVorher = a.state().fleets.stream().filter(f -> f.ownerId.equals(a.defenderId())).count();
    assertTrue(flottenVorher > 0, "Vorbedingung: der Verteidiger hat Flotten");

    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());
    runUntilEnded(a, battle, 60);
    assertEquals(BattleOutcome.AttackerVictory, battle.outcome);

    assertTrue(a.state().players.stream().anyMatch(p -> p.id.equals(a.defenderId())),
        "Der Kommandant bleibt im Spiel");
    assertEquals("", verlierer.homeworldColonyId,
        "Der Heimatverweis muss gelöst werden – er zeigte sonst auf die Kolonie des Eroberers");
    assertEquals(flottenVorher, a.state().fleets.stream().filter(f -> f.ownerId.equals(a.defenderId())).count(),
        "Die Flotten bleiben dem Kommandanten");

    // Die Meldung geht an IHN, nicht an die verlorene Kolonie (und damit an den Eroberer).
    assertTrue(a.state().notifications.stream()
            .anyMatch(n -> n.code == Notifications.CODE_HOMEWORLD_LOST && a.defenderId().equals(n.playerId)),
        "Der Verlierer muss über den Verlust seiner Heimatwelt benachrichtigt werden");
    assertTrue(NotificationCommands.notifications(a.state(), a.defenderId()).stream()
            .anyMatch(n -> n.code == Notifications.CODE_HOMEWORLD_LOST),
        "…und die Meldung muss in SEINEM Postfach liegen");
    assertTrue(NotificationCommands.notifications(a.state(), a.attackerId()).stream()
            .noneMatch(n -> n.code == Notifications.CODE_HOMEWORLD_LOST),
        "…und nicht im Postfach des Eroberers");
  }

  /**
   * Umsetzungskonzept/34_...md, Entscheidung zu §J 8: die Zivilverlustquote
   * hängt nicht mehr allein daran, WIE VIEL der Verteidigung fällt, sondern
   * auch daran, wie viel Verteidigung überhaupt dastand. Dieselbe restlos
   * aufgeriebene Verteidigung kostet über einer kleinen Bevölkerung viel und
   * über einer großen wenig – und nie mehr als der Tick-Deckel zulässt.
   */
  @Test
  void zivilverlusteSkalierenMitDerGarnison() {
    double garnison = 100;
    double vollstaendigGefallen = garnison;

    // Dieselbe Garnison, zwei Bevölkerungsgrößen: bei 2 000 Einwohnern ist sie
    // die volle Referenzstärke (5 % = 100), bei 20 000 nur ein Zehntel davon.
    double dicht = Formulas.civilianLossFraction(vollstaendigGefallen, garnison, 2_000);
    double duenn = Formulas.civilianLossFraction(vollstaendigGefallen, garnison, 20_000);

    assertTrue(dicht > duenn, "Die dichtere Verteidigung kostet mehr Zivilisten");
    assertEquals(0.1 * Formulas.CIVILIAN_LOSS_AT_TOTAL_DEFEAT, duenn, 1e-9,
        "Ein Zehntel der Referenzstärke = ein Zehntel der Quote");
    assertTrue(dicht <= Formulas.CIVILIAN_LOSS_MAX_PER_TICK + 1e-9,
        "Kein Tick darf über den Deckel hinausgehen, war: " + dicht);

    // Die Zehn-Drohnen-Startgarnison aus dem Befund: praktisch bedeutungslos.
    double startgarnison = Formulas.civilianLossFraction(1, 1, 2_000);
    assertTrue(startgarnison < 0.01,
        "Der Fall eines Wachdienstes darf keine spürbaren Zivilverluste kosten, war: " + startgarnison);

    assertEquals(0, Formulas.civilianLossFraction(0, garnison, 2_000), 1e-9,
        "Ohne militärische Verluste keine Zivilverluste");
  }

  @Test
  void unterlegenerAngreiferWirdMitsamtSeinenReservenVernichtet() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    // 2 Soldaten kommandieren nur 10 Drohnen – die übrigen 90 sind Reserve und
    // gehen nach §10 mit der Niederlage ebenfalls verloren (nicht erbeutet).
    GroundForceGroup group = landedGroup(a, 2, "p_drone_light", 100);
    garrison(a, 200, "p_drone_medium", 800);

    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());
    runUntilEnded(a, battle, 20);

    assertEquals(BattleOutcome.DefenderVictory, battle.outcome);
    assertNull(GroundBattleCommands.group(a.state(), group.id), "Der geschlagene Verband darf nicht übrig bleiben");
    assertEquals(a.defenderId(), ColonyCommands.colony(a.state(), a.defenderColonyId()).ownerId);
  }

  @Test
  void rueckzugBeendetDasGefechtUndDerVerbandBleibtAufDerOberflaeche() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 40, "p_drone_light", 200);
    garrison(a, 40, "p_drone_medium", 200);

    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());
    runTick(a, battle);
    assertEquals(BattleStatus.Active, battle.status);
    int attackerDronesBefore = total(group, "p_drone_light");

    GroundBattleCommands.retreatFromGroundBattle(a.state(), a.ids(), a.attackerId(), battle.id);

    assertEquals(BattleOutcome.Retreat, battle.outcome);
    assertEquals(BattleStatus.Ended, battle.status);
    assertEquals(a.defenderPlanetId(), group.planetId, "Der Rückzug führt zurück auf die Planetenoberfläche (§11)");
    assertTrue(total(group, "p_drone_light") < attackerDronesBefore,
        "Im Rückzugstick feuert die Gegenseite noch einmal einseitig");
    assertEquals(a.defenderId(), ColonyCommands.colony(a.state(), a.defenderColonyId()).ownerId);
  }

  /** Mechanik/05_..., §11: der Eigentümer kann sich aus der Verteidigung seiner eigenen Kolonie nicht zurückziehen. */
  @Test
  void verteidigerKannSichNichtZurueckziehen() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 40, "p_drone_light", 200);
    garrison(a, 40, "p_drone_medium", 200);
    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());

    var e = assertThrows(CommandException.class, () -> GroundBattleCommands.retreatFromGroundBattle(
        a.state(), a.ids(), a.defenderId(), battle.id));
    assertTrue(e.getMessage().contains("Rückzug"), e.getMessage());
  }

  /** §3: Reserven nehmen nicht teil und können in einem normalen Kampftick keinen Schaden nehmen. */
  @Test
  void reservenNehmenNichtAmGefechtTeil() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 100, "p_drone_light", 500);
    // 1 Soldat kommandiert 5 Drohnen – 95 der 100 Drohnen sind Reserve.
    GroundForceGroup defense = garrison(a, 1, "p_drone_medium", 100);
    assertEquals(GameConstants.DRONES_PER_SOLDIER, GroundBattleCommands.activeDroneCount(defense));

    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());
    runTick(a, battle);

    // Der eine Soldat fällt mit seinen Drohnen; danach ist NICHTS mehr
    // aktivierbar, und die Reserve-Drohnen gehen als Ganzes verloren (§10).
    // Das Gefecht läuft trotzdem weiter – jetzt als Belagerung.
    assertEquals(BattleStatus.Active, battle.status);
    assertEquals(GroundBattlePhase.Siege, battle.phase);
    assertEquals(0, total(RecruitmentCommands.groundForces(a.state(), a.defenderColonyId()), "p_drone_medium"),
        "Reserve-Drohnen sind mit der Niederlage verloren, nicht erbeutet (§10)");
  }

  // --- Belagerung -----------------------------------------------------------

  /** Die Zahlen aus der Nutzervorgabe, direkt nachgerechnet: 1000 Soldaten gegen 10 000 Einwohner. */
  @Test
  void belagerungstickSenktLoyalitaetUmDasSoldatenVerhaeltnis() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 1000, null, 0);
    setPopulation(a, a.defenderColonyId(), 10_000);
    setLoyalty(a, a.defenderColonyId(), 19);

    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());
    assertEquals(GroundBattlePhase.Siege, battle.phase);
    runTick(a, battle);

    var tick = battle.ticks.get(battle.ticks.size() - 1);
    assertEquals(GroundBattlePhase.Siege, tick.phase);
    assertEquals(19, tick.loyaltyPctBefore, 0.001);
    assertEquals(9, tick.loyaltyPctAfter, 0.001, "1000/10000 = 10 % ⇒ 19 % wird zu 9 % (absolut)");

    // 10 % der Bevölkerung erheben sich: 1000 Rebellen.
    assertEquals(1000, tick.rebels);
    assertEquals(1000, tick.attackerSoldiers);
    // Soldaten mit voller Stärke: floor(1000 / 12) = 83 tote Rebellen.
    assertEquals(83, tick.civiliansLost, 0.001);
    // Rebellen mit 30 % Stärke: floor(1000 × 0,3 / 12) = 25 tote Soldaten.
    assertEquals(25, tick.attackerLosses.get(GameConstants.SOLDIER_PRODUCT_ID));
    assertEquals(975, total(group, GameConstants.SOLDIER_PRODUCT_ID));
    assertEquals(9917, population(a, a.defenderColonyId()), 0.001);
    assertEquals(BattleStatus.Active, battle.status, "Bei 9 % ist die Kapitulationsschwelle noch nicht erreicht");
  }

  @Test
  void kolonieGehtErstUnterZweiProzentLoyalitaetUeber() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 1000, null, 0);
    setPopulation(a, a.defenderColonyId(), 10_000);
    setLoyalty(a, a.defenderColonyId(), 19);

    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());
    runTick(a, battle);
    assertEquals(a.defenderId(), ColonyCommands.colony(a.state(), a.defenderColonyId()).ownerId,
        "Bei 9 % gehört die Kolonie noch dem Verteidiger");
    runTick(a, battle);

    assertEquals(BattleStatus.Ended, battle.status);
    assertEquals(BattleOutcome.AttackerVictory, battle.outcome);
    assertEquals(a.attackerId(), ColonyCommands.colony(a.state(), a.defenderColonyId()).ownerId);
  }

  /** Nutzervorgabe: trifft während der Belagerung wieder eine Verteidigung ein, gibt es wieder Kampfticks. */
  @Test
  void neueVerteidigungHoltDasGefechtAusDerBelagerungZurueckInDenKampf() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 2000, "p_drone_light", 2000);
    setPopulation(a, a.defenderColonyId(), 10_000);
    setLoyalty(a, a.defenderColonyId(), 90);

    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());
    runTick(a, battle);
    assertEquals(GroundBattlePhase.Siege, battle.phase);

    // Verstärkung trifft ein – ab jetzt wird wieder Drohne gegen Drohne
    // gerechnet. Groß genug, um den ersten Kampftick zu überstehen, zu klein,
    // um ihn zu gewinnen.
    garrison(a, 140, "p_drone_heavy", 700);
    runTick(a, battle);
    assertEquals(GroundBattlePhase.Combat, battle.phase,
        "Solange wieder etwas Kampffähiges dasteht, wird gekämpft und nicht belagert");
    var combatTick = battle.ticks.get(battle.ticks.size() - 1);
    assertEquals(GroundBattlePhase.Combat, combatTick.phase);
    assertTrue(combatTick.defenderLosses.containsKey("p_drone_heavy"), "Im Kampftick fallen Drohnen, nicht Rebellen");
    assertEquals(0, combatTick.rebels, "Im Kampftick erhebt sich niemand");

    // Ist auch die geschlagen, geht es zurück in die Belagerung.
    runUntilEnded(a, battle, 60);
    assertTrue(battle.ticks.stream().filter(t -> t.phase == GroundBattlePhase.Siege).count() >= 2,
        "Nach dem zweiten Sieg im Kampf muss wieder belagert werden");
  }

  /** Auch in der Belagerung kann der Angreifer verlieren: die Aufständischen reiben seine Soldaten auf. */
  @Test
  void zuKleineBelagerungstruppeWirdVonDenRebellenAufgerieben() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 30, null, 0);
    setPopulation(a, a.defenderColonyId(), 20_000);
    setLoyalty(a, a.defenderColonyId(), 90);

    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());
    runUntilEnded(a, battle, 20);

    assertEquals(BattleOutcome.DefenderVictory, battle.outcome);
    assertNull(GroundBattleCommands.group(a.state(), group.id));
    assertEquals(a.defenderId(), ColonyCommands.colony(a.state(), a.defenderColonyId()).ownerId);
  }

  @Test
  void rueckzugAusDerBelagerungLaesstDenVerbandUnversehrtStehen() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 1000, null, 0);
    setPopulation(a, a.defenderColonyId(), 10_000);
    setLoyalty(a, a.defenderColonyId(), 90);

    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());
    GroundBattleCommands.retreatFromGroundBattle(a.state(), a.ids(), a.attackerId(), battle.id);

    assertEquals(BattleOutcome.Retreat, battle.outcome);
    assertEquals(1000, total(group, GameConstants.SOLDIER_PRODUCT_ID),
        "In der Belagerung steht keine Streitmacht, die einen Abzug bestrafen könnte");
    assertEquals(a.defenderPlanetId(), group.planetId);
  }

  // --- Wirkung auf die Kolonie ---------------------------------------------

  @Test
  void waehrendDesGefechtsWirdNichtGebautUndNichtNeuGehandelt() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());
    GroundForceGroup group = landedGroup(a, 40, "p_drone_light", 200);
    garrison(a, 40, "p_drone_medium", 200);
    GroundBattleCommands.engageGroundBattle(a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());

    assertTrue(GroundBattleCommands.isUnderGroundAttack(a.state(), a.defenderColonyId()));
    var build = assertThrows(CommandException.class, () -> BuildingCommands.queueBuilding(
        a.state(), a.ids(), a.defenderId(), a.defenderColonyId(), "b_habitat"));
    assertTrue(build.getMessage().contains("Bodengefecht"), build.getMessage());

    Warehouse.add(a.state(), a.defenderColonyId(), "p_stahl", 10);
    var trade = assertThrows(CommandException.class, () -> MarketCommands.createSellOrder(
        a.state(), a.ids(), a.defenderId(), a.defenderColonyId(), "p_stahl", 5, 10, false));
    assertTrue(trade.getMessage().contains("Bodengefecht"), trade.getMessage());
  }

  /** §2: "Zusammenführung mit eigener Kolonie, falls vorhanden" – §1: höchstens eine Kolonie je Spieler und Planet. */
  @Test
  void eroberungWirdInEineEigeneKolonieAufDemselbenPlanetenEingegliedert() {
    Arena a = newArena();
    DiplomacyCommands.declareWar(a.state(), a.ids(), a.attackerId(), a.defenderId());

    Colony own = new Colony();
    own.id = a.ids().next("col");
    own.planetId = a.defenderPlanetId();
    own.systemId = ColonyCommands.colony(a.state(), a.defenderColonyId()).systemId;
    own.ownerId = a.attackerId();
    own.name = "Brückenkopf";
    a.state().colonies.add(own);
    Population ownPopulation = new Population();
    ownPopulation.colonyId = own.id;
    ownPopulation.currentCount = 100;
    a.state().populations.add(ownPopulation);
    Warehouse.add(a.state(), a.defenderColonyId(), "p_stahl", 1000);

    GroundForceGroup group = landedGroup(a, 200, "p_drone_light", 1000);
    garrison(a, 2, "p_drone_heavy", 10);

    GroundBattle battle = GroundBattleCommands.engageGroundBattle(
        a.state(), a.ids(), a.attackerId(), group.id, a.defenderColonyId());
    runUntilEnded(a, battle, 20);

    assertEquals(BattleOutcome.AttackerVictory, battle.outcome);
    assertNull(ColonyCommands.colony(a.state(), a.defenderColonyId()), "Die eroberte Kolonie geht in der eigenen auf");
    assertEquals(1, a.state().colonies.stream()
        .filter(c -> c.ownerId.equals(a.attackerId()) && c.planetId.equals(a.defenderPlanetId())).count(),
        "Pro Spieler höchstens eine Kolonie je Planet (§1)");
    assertTrue(population(a, own.id) > 100, "Die überlebende Bevölkerung wandert in die eigene Kolonie");
    assertTrue(Warehouse.qty(a.state(), own.id, "p_stahl") > 0, "Überlebende Bestände werden übernommen (§2)");
    assertNotNull(RecruitmentCommands.groundForces(a.state(), own.id), "Der Sieger bezieht die eigene Kolonie");
  }
}
