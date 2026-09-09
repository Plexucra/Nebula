package de.nebula.npcbot;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Strategie-Ebene des Bots: aus der Lage ({@link Situation}) und der vom
 * Lager zugeteilten Rolle ({@link Assignment}) wird je Takt GENAU EINE
 * Strategie gewählt, aus der ein konkreter {@link Plan} (Bauprioritäten,
 * Exportfreigabe, Einkaufsliste, gewünschte Militärgüter) folgt. Die
 * ausführenden Module ({@code Economy}, {@code Trade}, {@code Military},
 * {@code Expansion}) kennen nur den Plan, nicht die Strategie – so lassen
 * sich Strategien ändern, ohne die Ausführung anzufassen.
 *
 * <p>Die Reihenfolge der Prüfungen in {@link #choose} ist die Prioritätsliste:
 * Überleben (Energie, Nahrung) vor Verteidigung vor Rollenaufgabe. Mit
 * Hysterese, damit ein Bot nicht in jedem Takt zwischen zwei Strategien
 * springt und dabei Aufträge immer wieder umstößt.</p>
 */
enum Strategy {
  /** Blackout oder Elerium fast leer: alles andere ruht, bis die Energie steht (Lehre aus dem Live-Spiel, TODO.md). */
  EMERGENCY_POWER,
  /** Bevölkerung unterversorgt: Nahrung/Medizin lokal produzieren und importieren, keine Exporte. */
  FAMINE,
  /** Eigene Kolonie unter Bodenangriff oder feindliche Flotte im Heimatsystem. */
  UNDER_ATTACK,
  /** Kampfflotte weitgehend verloren: Werft und Nachbau vor allem anderen. */
  RECOVER,
  /** Rolle INVADER: Transporter, Soldaten, Drohnen aufbauen. */
  PREPARE_INVASION,
  /** Rolle INVADER mit laufender Landungsoperation. */
  INVADE,
  /** Rolle RAIDER: gegnerische Blockadeflotten angreifen. */
  RAID,
  /** Rolle SETTLER: Kolonisationsschiff bauen und eigenes System besiedeln. */
  SETTLE,
  /** Rolle DEFENDER bzw. Standard: Wirtschaft und Verteidigung ausbauen. */
  BUILD_UP;

  enum MilitaryRole {DEFENDER, RAIDER, INVADER, SETTLER}

  /** Zuteilung des Lager-Koordinators (siehe {@code Coordination}). */
  static final class Assignment {
    String specialty;
    MilitaryRole role = MilitaryRole.DEFENDER;
    String targetColonyId;
    String targetSystemId;
    String targetPlanetId;
    String coordinatorId;
    long seq;

    boolean hasTarget() {
      return targetSystemId != null;
    }

    @Override
    public String toString() {
      return role + "/" + specialty + (targetSystemId != null ? " -> " + targetSystemId : "");
    }
  }

  /** Die Lage, wie sie der Bot in jedem Takt aus der Weltsicht zusammensetzt. */
  static final class Situation {
    boolean blackout;
    double minEleriumHours = Double.MAX_VALUE;
    double minFoodCoverage = 1;
    double minStandardOfLiving = 100;
    boolean underGroundAttack;
    boolean enemyFleetAtHome;
    boolean fleetInBattle;
    double fleetStrength;
    double initialFleetStrength;
    int transports;
    int colonyShips;
    int soldiers;
    int drones;
    int colonies;
    double wallet;
    double homeLoyalty;
    double homePopulation;
    int shipyardLevel;
    int academyLevel;
    boolean invasionActive;
    boolean settlingActive;
  }

  /** Was die Module in diesem Takt tun sollen. */
  static final class Plan {
    /** Ausbaustufen in Prioritätsreihenfolge; derselbe Gebäudetyp darf mehrfach mit steigender Obergrenze vorkommen (Stufenplan). */
    final List<String> buildPriority = new ArrayList<>();
    final List<Integer> buildCaps = new ArrayList<>();
    boolean exportAllowed = true;
    boolean allowInfrastructureGrowth = true;
    boolean wantTransport;
    boolean wantColonyShip;
    boolean wantWarships;
    int wantSoldiers;
    int wantDrones;
    String wantDroneType = Catalog.DRONE_MEDIUM;
    final List<String> hubImports = new ArrayList<>();

    Plan build(String type, int cap) {
      buildPriority.add(type);
      buildCaps.add(cap);
      return this;
    }
  }

  static Strategy choose(Situation s, Assignment a, Strategy previous) {
    boolean powerCritical = s.blackout || s.minEleriumHours < 24;
    boolean powerRecovered = !s.blackout && s.minEleriumHours > 72;
    if (powerCritical || (previous == EMERGENCY_POWER && !powerRecovered)) return EMERGENCY_POWER;

    // Lebensstandard: Nahrung zählt doppelt, Medizin und Elektronik einfach – ohne
    // (unbezahlbare) Elektronik liegt die Obergrenze bei 75 %, bei nur Nahrung bei 50 %.
    // Unter 30 % schrumpft die Bevölkerung (shared/game-constants.json) – das ist die Notlage.
    boolean famine = s.homePopulation > 20 && (s.minFoodCoverage < 0.5 || s.minStandardOfLiving < 32);
    boolean fed = s.minFoodCoverage >= 0.9 && s.minStandardOfLiving >= 45;
    if (famine || (previous == FAMINE && !fed)) return FAMINE;

    if (s.underGroundAttack || s.enemyFleetAtHome) return UNDER_ATTACK;

    boolean fleetGone = s.initialFleetStrength > 0 && s.fleetStrength < 0.3 * s.initialFleetStrength && !s.fleetInBattle;
    boolean fleetBack = s.fleetStrength >= 0.6 * s.initialFleetStrength;
    if (fleetGone || (previous == RECOVER && !fleetBack)) return RECOVER;

    return switch (a.role) {
      case INVADER -> s.invasionActive ? INVADE : PREPARE_INVASION;
      case RAIDER -> RAID;
      case SETTLER -> SETTLE;
      case DEFENDER -> BUILD_UP;
    };
  }

  /**
   * Der Plan je Strategie. Baustufen-Obergrenzen bewusst moderat: jede
   * Infrastrukturstufe frisst überlinear Elerium, und die Kolonie hat nur
   * EINE sequentielle Produktionswarteschlange (Umsetzungskonzept/17).
   */
  static Plan plan(Strategy strategy, Situation s) {
    Plan p = new Plan();
    switch (strategy) {
      case EMERGENCY_POWER -> {
        p.exportAllowed = false;
        p.allowInfrastructureGrowth = false;
        p.hubImports.add(Catalog.ELERIUM);
      }
      case FAMINE -> {
        // Keine Exporte, keine neue Infrastruktur – aber Werft und Ausbildungszentrum
        // laufen weiter (die Notlage ist meist eine Preisfrage, siehe Economy.adjustPrices).
        p.exportAllowed = false;
        p.allowInfrastructureGrowth = false;
        p.build(Catalog.SHIPYARD, 1).build(Catalog.ACADEMY, 1).build(Catalog.INDUSTRY, 6);
        p.hubImports.add(Catalog.FOOD);
        p.hubImports.add(Catalog.MEDICINE);
      }
      case UNDER_ATTACK -> {
        p.exportAllowed = false;
        p.build(Catalog.ACADEMY, 2).build(Catalog.DEFENSE, 1);
        p.wantSoldiers = 20;
        p.wantDrones = 40;
        p.wantDroneType = Catalog.DRONE_MEDIUM;
        p.wantWarships = true;
        p.hubImports.add(Catalog.ELERIUM);
      }
      case RECOVER -> {
        p.build(Catalog.SHIPYARD, 3).build(Catalog.INDUSTRY, 6);
        p.wantWarships = true;
        p.hubImports.add(Catalog.ELERIUM);
        p.hubImports.add(Catalog.STEEL);
      }
      case PREPARE_INVASION, INVADE -> {
        // Jedes neue Gebäude kostet eine Infrastrukturstufe (Bebauungsplätze) – Werft und
        // Ausbildungszentrum zuerst auf Stufe 1, erst dann die teureren Ausbauten.
        p.build(Catalog.SHIPYARD, 1).build(Catalog.ACADEMY, 1).build(Catalog.INDUSTRY, 8).build(Catalog.HABITAT, 3).build(Catalog.SHIPYARD, 3).build(Catalog.ACADEMY, 3).build(Catalog.INDUSTRY, 12).build(Catalog.HABITAT, 4);
        p.wantTransport = true;
        p.wantWarships = s.shipyardLevel >= 2;
        // Soldaten-/Drohnenbedarf setzt Military anhand des konkreten Ziels.
        p.hubImports.add(Catalog.ELERIUM);
        p.hubImports.add(Catalog.STEEL);
      }
      case RAID -> {
        p.build(Catalog.SHIPYARD, 1).build(Catalog.INDUSTRY, 8).build(Catalog.HABITAT, 3).build(Catalog.SHIPYARD, 3).build(Catalog.INDUSTRY, 12).build(Catalog.HABITAT, 4);
        p.wantWarships = true;
        p.hubImports.add(Catalog.ELERIUM);
        p.hubImports.add(Catalog.STEEL);
      }
      case SETTLE -> {
        p.build(Catalog.SHIPYARD, 1).build(Catalog.INDUSTRY, 8).build(Catalog.HABITAT, 3).build(Catalog.SHIPYARD, 2).build(Catalog.INDUSTRY, 12).build(Catalog.HABITAT, 4);
        p.wantColonyShip = true;
        p.hubImports.add(Catalog.ELERIUM);
      }
      case BUILD_UP -> {
        p.build(Catalog.ACADEMY, 1).build(Catalog.DEFENSE, 1).build(Catalog.INDUSTRY, 8).build(Catalog.HABITAT, 3).build(Catalog.ACADEMY, 2).build(Catalog.SHIPYARD, 2).build(Catalog.INDUSTRY, 12).build(Catalog.HABITAT, 4);
        p.wantSoldiers = 8;
        p.wantDrones = 30;
        p.wantDroneType = Catalog.DRONE_MEDIUM;
        p.wantWarships = s.shipyardLevel >= 2;
        p.hubImports.add(Catalog.ELERIUM);
        p.hubImports.add(Catalog.STEEL);
      }
    }
    return p;
  }
}
