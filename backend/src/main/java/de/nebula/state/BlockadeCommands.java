package de.nebula.state;

import de.nebula.engine.Clock;
import de.nebula.model.Blockade;
import de.nebula.model.BlockadeAnchor;
import de.nebula.model.BlockadeAnchorKind;
import de.nebula.model.Fleet;
import de.nebula.model.FleetLocationType;
import de.nebula.model.FleetStatus;
import de.nebula.model.Player;
import de.nebula.model.StarSystem;

import java.util.List;

/**
 * 1:1-Portierung der "Blockaden"-Sektion aus {@code simulated-game-api.service.ts}
 * (Umsetzungskonzept/13_...md, Phase 9) – bewusst stark vereinfacht ggü.
 * Mechanik/06_...md (siehe dortige TS-Klassendoku): nur zwei Ankerarten,
 * kein Blockade-Anker-Objekt mit räumlicher Hierarchie, keine
 * Mobilmachungsrampe, kein Expositionslimit, keine Mehrparteien-Blockaden.
 *
 * <p>Neutrale Handelsgilde-Stationen ({@code StarSystem.isTradeHub}) sind
 * gemäß Umsetzungskonzept/21_...md von KEINER Blockade betroffen –
 * {@link #formBlockade} lehnt dort jeden Versuch ab (Konzeption/Spieldesign/
 * 05_...md, §6: garantierter physischer Zugang).</p>
 */
public final class BlockadeCommands {
  private BlockadeCommands() {
  }

  public static List<Blockade> blockadesInSystem(GameState state, String systemId) {
    return state.blockades.stream().filter(b -> b.systemId.equals(systemId)).toList();
  }

  /** Errichtet eine Blockade mit der eigenen, an diesem Ort bereits stationierten Flotte – macht sie angreifbar. */
  public static void formBlockade(GameState state, IdGenerator ids, String playerId, String fleetId, BlockadeAnchor anchor) {
    Player me = GameQueries.requirePlayer(state, playerId);
    Fleet fleet = FleetCommands.requireOwnFleet(state, playerId, fleetId);
    if (fleet.status != FleetStatus.Stationed) throw new CommandException("Die Flotte ist unterwegs.");
    if (fleet.ships.stream().noneMatch(s -> s.quantity > 0)) throw new CommandException("Eine Flotte ohne Schiffe kann keine Blockade bilden.");
    if (state.blockades.stream().anyMatch(b -> b.fleetId.equals(fleetId))) throw new CommandException("Diese Flotte blockiert bereits einen Ort.");
    StarSystem system = state.systems.stream().filter(s -> s.id.equals(fleet.systemId)).findFirst().orElse(null);
    if (system != null && system.isTradeHub) {
      throw new CommandException("Neutrale Handelsgilde-Stationen können nicht blockiert werden.");
    }

    String planetId;
    BlockadeAnchorKind kind;
    if (anchor instanceof BlockadeAnchor.Gateway) {
      kind = BlockadeAnchorKind.Gateway;
      planetId = null;
      if (fleet.locationType != FleetLocationType.System) throw new CommandException("Für eine Gateway-Blockade muss die Flotte am Systemhandelsposten stehen.");
      if (state.blockades.stream().anyMatch(b -> b.systemId.equals(fleet.systemId) && b.anchorKind == BlockadeAnchorKind.Gateway)) {
        throw new CommandException("Dieses Gateway wird bereits blockiert.");
      }
    } else {
      BlockadeAnchor.PlanetOrbit po = (BlockadeAnchor.PlanetOrbit) anchor;
      kind = BlockadeAnchorKind.PlanetOrbit;
      planetId = po.planetId();
      boolean atThisPlanet = planetId.equals(fleet.locationPlanetId)
          && (fleet.locationType == FleetLocationType.PlanetOrbit || fleet.locationType == FleetLocationType.ColonyOrbit);
      if (!atThisPlanet) throw new CommandException("Die Flotte muss im Orbit dieses Planeten stehen.");
      if (state.blockades.stream().anyMatch(b -> b.anchorKind == BlockadeAnchorKind.PlanetOrbit && planetId.equals(b.planetId))) {
        throw new CommandException("Dieser Planet wird bereits blockiert.");
      }
    }

    Blockade blockade = new Blockade();
    blockade.id = ids.next("blk");
    blockade.systemId = fleet.systemId;
    blockade.anchorKind = kind;
    blockade.planetId = planetId;
    blockade.fleetId = fleetId;
    blockade.ownerId = me.id;
    blockade.startedAt = Clock.now();
    state.blockades.add(blockade);
  }

  /** Hebt die eigene Blockade wieder auf – nicht möglich während eines laufenden Gefechts der blockierenden Flotte. */
  public static void liftBlockade(GameState state, String playerId, String blockadeId) {
    Player me = GameQueries.requirePlayer(state, playerId);
    Blockade blockade = find(state, blockadeId);
    if (blockade == null) throw new CommandException("Unbekannte Blockade.");
    if (!blockade.ownerId.equals(me.id)) throw new CommandException("Diese Blockade gehört einem anderen Kommandanten.");
    if (BattleCommands.activeBattleForFleet(state, blockade.fleetId) != null) {
      throw new CommandException("Während eines laufenden Gefechts kann die Blockade nicht aufgehoben werden.");
    }
    state.blockades.remove(blockade);
  }

  private static Blockade find(GameState state, String blockadeId) {
    for (Blockade b : state.blockades) if (b.id.equals(blockadeId)) return b;
    return null;
  }

  /** Eine Blockade ohne verbliebene Schiffe (Flotte im Kampf vollständig vernichtet) macht keinen Sinn mehr – wird automatisch entfernt. */
  public static void pruneEmptyBlockades(GameState state) {
    var emptyFleetIds = state.fleets.stream()
        .filter(f -> f.ships.stream().noneMatch(s -> s.quantity > 0))
        .map(f -> f.id)
        .collect(java.util.stream.Collectors.toSet());
    state.blockades.removeIf(b -> emptyFleetIds.contains(b.fleetId));
  }
}
