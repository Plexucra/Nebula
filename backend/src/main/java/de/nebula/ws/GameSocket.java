package de.nebula.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.nebula.data.BuildingCatalog;
import de.nebula.data.GroundUnitCatalog;
import de.nebula.data.ProductCatalog;
import de.nebula.data.ShipCatalog;
import de.nebula.data.WorldSeed;
import de.nebula.model.Player;
import de.nebula.model.PlayerRole;
import de.nebula.state.BuildingCommands;
import de.nebula.state.ColonyCommands;
import de.nebula.state.CommandException;
import de.nebula.state.ConnectionRegistry;
import de.nebula.state.GameQueries;
import de.nebula.state.GameState;
import de.nebula.state.GameStateSeeder;
import de.nebula.state.IdGenerator;
import de.nebula.state.BattleCommands;
import de.nebula.state.BlockadeCommands;
import de.nebula.state.DiplomacyCommands;
import de.nebula.state.EconomyTick;
import de.nebula.state.FleetCommands;
import de.nebula.state.GatewayCommands;
import de.nebula.state.HubMarketCommands;
import de.nebula.state.MarketCommands;
import de.nebula.state.MessageCommands;
import de.nebula.state.NotificationCommands;
import de.nebula.state.ProductionCommands;
import de.nebula.state.RecruitmentCommands;
import de.nebula.state.ShipyardCommands;
import de.nebula.state.TreatyCommands;
import de.nebula.model.FleetSystemTarget;
import de.nebula.model.TreatyType;
import de.nebula.model.WalletOwnerType;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Einziger WebSocket-Endpunkt des Spiels (Umsetzungskonzept/13_...md, §"WebSocket-Gerüst").
 * Nimmt {@link ClientMessage} entgegen, verteilt anhand von {@code type} an
 * eine Befehlsmethode und antwortet mit {@link ServerMessage} ("Ack"/"Error").
 *
 * <p>Bewusst noch OHNE die meiste Spiel-Geschäftslogik (Kampf, Diplomatie,
 * Produktion, ...) – jeder noch nicht portierte Befehl antwortet ehrlich mit
 * "Error" statt mit einer erfundenen Ersatzlogik (Leitplanke "nichts von der
 * Spielmechanik darf verloren gehen", siehe Migrationsplan). Diese Klasse ist
 * Quarkus-connection-scoped: eine Instanz je offene WebSocket-Verbindung,
 * {@link WebSocketConnection} kann deshalb per Konstruktor injiziert werden.</p>
 */
@WebSocket(path = "/game")
public class GameSocket {

  private static final Logger LOG = Logger.getLogger(GameSocket.class);

  private final WebSocketConnection connection;
  private final GameState state;
  private final ConnectionRegistry connections;
  private final ObjectMapper objectMapper;
  private final IdGenerator ids;

  public GameSocket(WebSocketConnection connection, GameState state, ConnectionRegistry connections,
                     ObjectMapper objectMapper, IdGenerator ids) {
    this.connection = connection;
    this.state = state;
    this.connections = connections;
    this.objectMapper = objectMapper;
    this.ids = ids;
  }

  @OnOpen
  public void onOpen() {
    connection.sendTextAndAwait(ServerMessage.push("players", List.copyOf(state.players)));
  }

  @OnClose
  public void onClose() {
    connections.logout(connection);
  }

  @OnTextMessage
  public void onMessage(String raw) {
    ClientMessage message;
    try {
      message = objectMapper.readValue(raw, ClientMessage.class);
    } catch (Exception e) {
      LOG.warnf(e, "Konnte ClientMessage nicht parsen: %s", raw);
      connection.sendTextAndAwait(ServerMessage.error(null, "Ungültige Nachricht: " + e.getMessage()));
      return;
    }
    try {
      // Grobkörnige Sperre um jeden Befehl (siehe Migrationsplan §"Zustandshaltung"):
      // schützt vor Race Conditions zwischen mehreren WS-Verbindungen und dem Tick-Loop
      // (`GameTick`), auf Kosten von Durchsatz – für die Größenordnung dieses Prototyps
      // unproblematisch.
      Object result;
      synchronized (state) {
        result = dispatch(message.type, message.payload);
      }
      connection.sendTextAndAwait(ServerMessage.ack(message.requestId, result));
    } catch (CommandException e) {
      connection.sendTextAndAwait(ServerMessage.error(message.requestId, e.getMessage()));
    } catch (Exception e) {
      LOG.errorf(e, "Unerwarteter Fehler bei Befehl '%s'", message.type);
      connection.sendTextAndAwait(ServerMessage.error(message.requestId, "Interner Fehler: " + e.getMessage()));
    }
  }

  private String currentPlayerId() {
    return connections.playerIdOf(connection);
  }

  /**
   * Befehls-Dispatch anhand von {@code type} (1:1 zu einem {@code GameApi}-Methodennamen).
   * Wird mit jeder portierten Phase aus dem Migrationsplan (§"Reihenfolge der
   * Geschäftslogik-Portierung") um weitere {@code case}-Zweige ergänzt.
   */
  private Object dispatch(String type, JsonNode payload) {
    return switch (type) {
      // --- Konto/Anmeldung ---------------------------------------------------
      case "players" -> List.copyOf(state.players);
      case "login" -> handleLogin(payload);
      case "logout" -> handleLogout();
      case "registerPlayer" -> handleRegisterPlayer(payload);
      case "resetGame" -> handleResetGame();

      // --- Katalog (statisch) --------------------------------------------------
      case "productTypes" -> ProductCatalog.CATALOG;
      case "buildingTypes" -> BuildingCatalog.CATALOG;
      case "shipTypes" -> ShipCatalog.CATALOG;
      case "groundUnitTypes" -> GroundUnitCatalog.CATALOG;

      // --- Planeten/Kolonien ---------------------------------------------------
      case "colonies" -> ColonyCommands.coloniesOf(state, requirePlayerId());
      case "coloniesInSystem" -> ColonyCommands.coloniesInSystem(state, text(payload, "systemId"));
      case "colony" -> ColonyCommands.colony(state, text(payload, "id"));
      case "colonyStats" -> ColonyCommands.colonyStats(state, text(payload, "id"));
      case "population" -> ColonyCommands.population(state, text(payload, "id"));
      case "moneySupplyState" -> ColonyCommands.moneySupplyState(state, text(payload, "planetId"));
      case "populationWallet" -> GameQueries.findWallet(state, WalletOwnerType.Population, text(payload, "colonyId"));
      case "consumptionCoverage" -> ColonyCommands.consumptionCoverage(state, text(payload, "colonyId"));
      case "colonySpeedBreakdown" -> ColonyCommands.colonySpeedBreakdown(state, text(payload, "colonyId"));
      case "populationTrend" -> de.nebula.state.PopulationHistory.trend(state, text(payload, "colonyId"));
      case "transactions" -> GameQueries.transactionsForPlayer(state, requirePlayerId());
      case "transfer" -> throw new CommandException(
          "Noch kein anderer Kommandant \"" + text(payload, "toPlayerName") + "\" erreichbar – Mehrspieler folgt in einer späteren Ausbaustufe.");
      case "planet" -> ColonyCommands.planetForPlayer(state, text(payload, "id"), currentPlayerId());
      case "planetsInSystem" -> ColonyCommands.planetsInSystemForPlayer(state, text(payload, "systemId"), currentPlayerId());
      case "colonizePlanet" -> ColonyCommands.colonizePlanet(state, ids, requirePlayerId(), text(payload, "planetId"));

      // --- Bebauung --------------------------------------------------------------
      case "buildings" -> BuildingCommands.buildingsForColony(state, text(payload, "colonyId"));
      case "buildSlots" -> BuildingCommands.buildSlots(state, text(payload, "colonyId"));
      case "housingCapacity" -> de.nebula.state.PowerGrid.effectiveHousingCapacity(state, text(payload, "colonyId"));
      case "powerCoverage" -> de.nebula.state.PowerGrid.coverageRatio(state, text(payload, "colonyId"));
      case "isBlackout" -> de.nebula.state.PowerGrid.isBlackout(state, text(payload, "colonyId"));
      case "powerUpkeepPerHour" -> de.nebula.state.PowerGrid.powerUpkeepPerHour(state, text(payload, "colonyId"));
      case "queueBuilding" -> {
        BuildingCommands.queueBuilding(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "buildingTypeId"));
        yield null;
      }
      case "cancelBuildingOrder" -> {
        BuildingCommands.cancelBuildingOrder(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "buildingId"));
        yield null;
      }
      case "demolishBuilding" -> {
        BuildingCommands.demolishBuilding(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "buildingId"));
        yield null;
      }
      case "activateDefense" -> {
        BuildingCommands.activateDefense(state, requirePlayerId(), text(payload, "colonyId"), text(payload, "buildingId"));
        yield null;
      }
      case "deactivateDefense" -> {
        BuildingCommands.deactivateDefense(state, requirePlayerId(), text(payload, "colonyId"), text(payload, "buildingId"));
        yield null;
      }

      // --- Produktion (sequentielle Warteschlange) ------------------------------
      case "warehouse" -> ProductionCommands.warehouseFor(state, text(payload, "colonyId"));
      case "specializations" -> ProductionCommands.specializationsFor(state, text(payload, "colonyId"));
      case "productionQueue" -> ProductionCommands.productionQueueFor(state, text(payload, "colonyId"));
      case "previewProductionChain" -> ProductionCommands.previewProductionChain(state, text(payload, "colonyId"),
          text(payload, "productTypeId"), payload.path("quantity").asDouble());
      case "queueProduction" -> {
        ProductionCommands.queueProduction(state, ids, requirePlayerId(), text(payload, "colonyId"),
            text(payload, "productTypeId"), payload.path("quantity").asDouble(),
            payload.path("autoProduceMissing").asBoolean(false), payload.path("requeueOnComplete").asBoolean(false));
        yield null;
      }
      case "resumeProduction" -> {
        ProductionCommands.resumeProduction(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "entryId"));
        yield null;
      }
      case "cancelProduction" -> {
        ProductionCommands.cancelProduction(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "entryId"));
        yield null;
      }

      // --- Handel ------------------------------------------------------------
      case "wallet" -> GameQueries.findWallet(state, WalletOwnerType.Player, requirePlayerId());
      case "sellOrders" -> MarketCommands.sellOrdersInSystem(state, text(payload, "systemId"));
      case "createSellOrder" -> {
        MarketCommands.createSellOrder(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "productTypeId"),
            payload.path("quantity").asDouble(), payload.path("pricePerUnit").asDouble(), payload.path("autoRelist").asBoolean(false));
        yield null;
      }
      case "cancelSellOrder" -> {
        MarketCommands.cancelSellOrder(state, requirePlayerId(), text(payload, "orderId"));
        yield null;
      }
      case "buyFromOrder" -> {
        MarketCommands.buyFromOrder(state, ids, requirePlayerId(), text(payload, "orderId"),
            payload.path("quantity").asDouble(), text(payload, "deliverToColonyId"));
        yield null;
      }
      case "createSellOrderFromFleet" -> {
        MarketCommands.createSellOrderFromFleet(state, ids, requirePlayerId(), text(payload, "fleetId"), text(payload, "productTypeId"),
            payload.path("quantity").asDouble(), payload.path("pricePerUnit").asDouble(), payload.path("autoRelist").asBoolean(false));
        yield null;
      }

      // --- Handelsgilde-Station: Depot & Orderbuch (Umsetzungskonzept/22_...md) ---
      case "hubDepot" -> HubMarketCommands.hubDepotOf(state, text(payload, "systemId"), requirePlayerId());
      case "hubOrders" -> HubMarketCommands.ordersInSystem(state, text(payload, "systemId"));
      case "createHubSellOrder" -> {
        HubMarketCommands.createSellOrder(state, ids, requirePlayerId(), text(payload, "systemId"), text(payload, "productTypeId"),
            payload.path("quantity").asDouble(), payload.path("pricePerUnit").asDouble());
        yield null;
      }
      case "createHubBuyOrder" -> {
        HubMarketCommands.createBuyOrder(state, ids, requirePlayerId(), text(payload, "systemId"), text(payload, "productTypeId"),
            payload.path("quantity").asDouble(), payload.path("pricePerUnit").asDouble());
        yield null;
      }
      case "cancelHubOrder" -> {
        HubMarketCommands.cancelOrder(state, requirePlayerId(), text(payload, "orderId"));
        yield null;
      }
      case "unloadCargoToHubDepot" -> {
        FleetCommands.unloadCargoToHubDepot(state, requirePlayerId(), text(payload, "fleetId"), text(payload, "productTypeId"),
            payload.path("quantity").asDouble());
        yield null;
      }
      case "loadCargoFromHubDepot" -> {
        FleetCommands.loadCargoFromHubDepot(state, requirePlayerId(), text(payload, "fleetId"), text(payload, "productTypeId"),
            payload.path("quantity").asDouble());
        yield null;
      }

      // --- Universums-Statistik --------------------------------------------
      case "universeStats" -> EconomyTick.universeStats(state);

      // --- Benachrichtigungen ------------------------------------------------
      case "notifications" -> NotificationCommands.notifications(state, requirePlayerId());
      case "unreadNotificationCount" -> NotificationCommands.unreadNotificationCount(state, requirePlayerId());
      case "markNotificationRead" -> {
        NotificationCommands.markNotificationRead(state, text(payload, "id"));
        yield null;
      }
      case "markAllNotificationsRead" -> {
        NotificationCommands.markAllNotificationsRead(state, requirePlayerId());
        yield null;
      }
      case "setNotificationKeep" -> {
        NotificationCommands.setNotificationKeep(state, requirePlayerId(), text(payload, "id"), payload.path("keep").asBoolean(false));
        yield null;
      }

      // --- Nachrichten (ausschließlich Spieler-zu-Spieler) ----------------------
      case "inbox" -> MessageCommands.inbox(state, requirePlayerId());
      case "sentMessages" -> MessageCommands.sentMessages(state, requirePlayerId());
      case "unreadMessageCount" -> MessageCommands.unreadMessageCount(state, requirePlayerId());
      case "sendMessage" -> {
        String senderId = requirePlayerId();
        String toPlayerId = text(payload, "toPlayerId");
        MessageCommands.sendMessage(state, ids, senderId, toPlayerId, text(payload, "subject"), text(payload, "body"));
        pushMessages(senderId);
        pushMessages(toPlayerId);
        yield null;
      }
      case "markMessageRead" -> {
        String playerId = requirePlayerId();
        MessageCommands.markMessageRead(state, playerId, text(payload, "id"));
        pushMessages(playerId);
        yield null;
      }
      case "setMessageKeep" -> {
        String playerId = requirePlayerId();
        MessageCommands.setMessageKeep(state, playerId, text(payload, "id"), payload.path("keep").asBoolean(false));
        pushMessages(playerId);
        yield null;
      }

      // --- Flotten -------------------------------------------------------------
      case "fleets" -> FleetCommands.fleetsOf(state, requirePlayerId());
      case "allFleets" -> FleetCommands.allFleets(state);
      case "shipyardQueue" -> ShipyardCommands.shipyardQueueFor(state, text(payload, "colonyId"));
      case "queueShip" -> {
        ShipyardCommands.queueShip(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "shipProductTypeId"),
            payload.path("quantity").asDouble(), payload.path("autoProduceMissing").asBoolean(false),
            payload.path("requeueOnComplete").asBoolean(false));
        yield null;
      }
      case "resumeShipOrder" -> {
        ShipyardCommands.resumeShipOrder(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "entryId"));
        yield null;
      }
      case "cancelShipOrder" -> {
        ShipyardCommands.cancelShipOrder(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "entryId"));
        yield null;
      }
      case "transferShipsToFleet" -> {
        FleetCommands.transferShipsToFleet(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "shipProductTypeId"),
            payload.path("quantity").asDouble(), text(payload, "targetFleetId"));
        yield null;
      }
      case "loadCargo" -> {
        FleetCommands.loadCargo(state, requirePlayerId(), text(payload, "fleetId"), text(payload, "productTypeId"), payload.path("quantity").asDouble());
        yield null;
      }
      case "unloadCargo" -> {
        FleetCommands.unloadCargo(state, requirePlayerId(), text(payload, "fleetId"), text(payload, "productTypeId"), payload.path("quantity").asDouble());
        yield null;
      }
      case "moveFleet" -> {
        FleetCommands.moveFleet(state, requirePlayerId(), text(payload, "fleetId"), text(payload, "destinationSystemId"));
        yield null;
      }
      case "cancelFleetMove" -> {
        FleetCommands.cancelFleetMove(state, requirePlayerId(), text(payload, "fleetId"));
        yield null;
      }
      case "routePreview" -> FleetCommands.routePreview(state, text(payload, "fleetId"), text(payload, "destinationSystemId"));
      case "fleetCargoCapacity" -> FleetCommands.fleetCargoCapacity(state, text(payload, "fleetId"), text(payload, "productTypeId"));
      case "moveFleetWithinSystem" -> {
        FleetCommands.moveFleetWithinSystem(state, requirePlayerId(), text(payload, "fleetId"), parseFleetSystemTarget(payload.path("target")));
        yield null;
      }
      case "exploreSystem" -> {
        FleetCommands.exploreSystem(state, requirePlayerId(), text(payload, "fleetId"));
        yield null;
      }

      // --- Bodentruppen ----------------------------------------------------------
      case "groundForces" -> RecruitmentCommands.groundForces(state, text(payload, "colonyId"));
      case "recruitmentQueue" -> RecruitmentCommands.recruitmentQueueFor(state, text(payload, "colonyId"));
      case "queueRecruitment" -> {
        RecruitmentCommands.queueRecruitment(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "unitProductTypeId"),
            payload.path("quantity").asDouble(), payload.path("autoProduceMissing").asBoolean(false),
            payload.path("requeueOnComplete").asBoolean(false));
        yield null;
      }
      case "resumeRecruitment" -> {
        RecruitmentCommands.resumeRecruitment(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "entryId"));
        yield null;
      }
      case "cancelRecruitment" -> {
        RecruitmentCommands.cancelRecruitment(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "entryId"));
        yield null;
      }

      // --- Gateway/Galaxie -------------------------------------------------------
      case "gateway" -> GatewayCommands.gateway(state, text(payload, "systemId"));
      case "gatewayWeights" -> GatewayCommands.gatewayWeights(state, requirePlayerId(), text(payload, "systemId"));
      case "visibleSystems" -> GatewayCommands.visibleSystems(state);
      case "system" -> GatewayCommands.system(state, text(payload, "id"));
      case "galaxyRoutes" -> GatewayCommands.galaxyRoutes(state);
      case "hasVisitedSystem" -> GatewayCommands.hasVisitedSystem(state, requirePlayerId(), text(payload, "systemId"));
      case "hasExploredSystem" -> GatewayCommands.hasExploredSystem(state, requirePlayerId(), text(payload, "systemId"));

      // --- Blockaden ---------------------------------------------------------
      case "blockadesInSystem" -> BlockadeCommands.blockadesInSystem(state, text(payload, "systemId"));
      case "formBlockade" -> {
        BlockadeCommands.formBlockade(state, ids, requirePlayerId(), text(payload, "fleetId"), parseBlockadeAnchor(payload.path("anchor")));
        yield null;
      }
      case "liftBlockade" -> {
        BlockadeCommands.liftBlockade(state, requirePlayerId(), text(payload, "blockadeId"));
        yield null;
      }

      // --- Diplomatie --------------------------------------------------------
      case "diplomaticStatus" -> DiplomacyCommands.diplomaticStatus(state, currentPlayerId(), text(payload, "otherPlayerId"));
      case "activeWars" -> DiplomacyCommands.activeWars(state, requirePlayerId());
      case "incomingPeaceOffers" -> DiplomacyCommands.incomingPeaceOffers(state, requirePlayerId());
      case "outgoingPeaceOffers" -> DiplomacyCommands.outgoingPeaceOffers(state, requirePlayerId());
      case "declareWar" -> {
        DiplomacyCommands.declareWar(state, ids, requirePlayerId(), text(payload, "otherPlayerId"));
        yield null;
      }
      case "offerPeace" -> {
        DiplomacyCommands.offerPeace(state, ids, requirePlayerId(), text(payload, "otherPlayerId"));
        yield null;
      }
      case "respondToPeaceOffer" -> {
        DiplomacyCommands.respondToPeaceOffer(state, ids, requirePlayerId(), text(payload, "offerId"), payload.path("accept").asBoolean(false));
        yield null;
      }

      // --- Friedens-/Handelsverträge (Umsetzungskonzept/21_...md) ---------------
      case "treaties" -> TreatyCommands.treatiesOf(state, requirePlayerId());
      case "incomingTreatyOffers" -> TreatyCommands.incomingTreatyOffers(state, requirePlayerId());
      case "outgoingTreatyOffers" -> TreatyCommands.outgoingTreatyOffers(state, requirePlayerId());
      case "hasPeaceTreaty" -> TreatyCommands.hasPeaceTreaty(state, requirePlayerId(), text(payload, "otherPlayerId"));
      case "hasTradeAgreement" -> TreatyCommands.hasTradeAgreement(state, requirePlayerId(), text(payload, "otherPlayerId"));
      case "offerTreaty" -> {
        TreatyCommands.offerTreaty(state, ids, requirePlayerId(), text(payload, "otherPlayerId"), TreatyType.valueOf(text(payload, "type")));
        yield null;
      }
      case "respondToTreatyOffer" -> {
        TreatyCommands.respondToTreatyOffer(state, ids, requirePlayerId(), text(payload, "offerId"), payload.path("accept").asBoolean(false));
        yield null;
      }
      case "terminateTreaty" -> {
        TreatyCommands.terminateTreaty(state, ids, requirePlayerId(), text(payload, "otherPlayerId"), TreatyType.valueOf(text(payload, "type")));
        yield null;
      }

      // --- Raumgefechte --------------------------------------------------------
      case "activeBattles" -> BattleCommands.activeBattles(state, requirePlayerId());
      case "battle" -> BattleCommands.battle(state, text(payload, "id"));
      case "battleHistory" -> BattleCommands.battleHistory(state, requirePlayerId());
      case "battleByReportToken" -> BattleCommands.battleByReportToken(state, text(payload, "token"));
      case "attackableFleetsInSystem" -> BattleCommands.attackableFleetsInSystem(state, currentPlayerId(), text(payload, "systemId"));
      case "engageBattle" -> {
        BattleCommands.engageBattle(state, ids, requirePlayerId(), text(payload, "attackerFleetId"), text(payload, "defenderFleetId"));
        yield null;
      }
      case "retreatFromBattle" -> {
        BattleCommands.retreatFromBattle(state, ids, requirePlayerId(), text(payload, "battleId"));
        yield null;
      }

      default -> throw new CommandException("Unbekannter oder noch nicht portierter Befehl: " + type);
    };
  }

  private static String text(JsonNode payload, String field) {
    return payload.path(field).asText(null);
  }

  /** Parst {@code { kind: 'System' | 'PlanetOrbit' | 'ColonyOrbit', planetId?, colonyId? }} (siehe TS {@code FleetSystemTarget}). */
  private static FleetSystemTarget parseFleetSystemTarget(JsonNode node) {
    String kind = node.path("kind").asText(null);
    if (kind == null) throw new CommandException("moveFleetWithinSystem benötigt 'target.kind'.");
    return switch (kind) {
      case "System" -> new FleetSystemTarget.System();
      case "PlanetOrbit" -> new FleetSystemTarget.PlanetOrbit(text(node, "planetId"));
      case "ColonyOrbit" -> new FleetSystemTarget.ColonyOrbit(text(node, "colonyId"));
      default -> throw new CommandException("Unbekannter target.kind: " + kind);
    };
  }

  /** Parst {@code { kind: 'Gateway' | 'PlanetOrbit', planetId? }} (siehe TS {@code BlockadeAnchor}). */
  private static de.nebula.model.BlockadeAnchor parseBlockadeAnchor(JsonNode node) {
    String kind = node.path("kind").asText(null);
    if (kind == null) throw new CommandException("formBlockade benötigt 'anchor.kind'.");
    return switch (kind) {
      case "Gateway" -> new de.nebula.model.BlockadeAnchor.Gateway();
      case "PlanetOrbit" -> new de.nebula.model.BlockadeAnchor.PlanetOrbit(text(node, "planetId"));
      default -> throw new CommandException("Unbekannter anchor.kind: " + kind);
    };
  }

  private String requirePlayerId() {
    String playerId = currentPlayerId();
    if (playerId == null) throw new CommandException("Kein aktiver Kommandant.");
    return playerId;
  }

  /**
   * Sofort-Push (statt auf das nächste Client-Poll-Intervall zu warten, siehe
   * Migrationsplan §"Sync-Strategie") an ALLE offenen Verbindungen eines
   * Kommandanten, für den "E-Mail"-Charakter des Nachrichtensystems – auf
   * allen seinen Geräten gleichzeitig (siehe {@code ConnectionRegistry},
   * ursprünglich für genau diesen Mehrgeräte-Fall gebaut). Die Kanalnamen
   * entsprechen bewusst 1:1 den Query-Befehlsnamen (`inbox`/`sentMessages`/
   * `unreadMessageCount`), damit `WebSocketGameApiService` sie direkt auf
   * ihre gepollten Signale abbilden kann, ohne einen eigenen Kanal-Namen zu
   * benötigen.
   */
  private void pushMessages(String playerId) {
    for (WebSocketConnection conn : connections.connectionsOf(playerId)) {
      conn.sendTextAndAwait(ServerMessage.push("inbox", MessageCommands.inbox(state, playerId)));
      conn.sendTextAndAwait(ServerMessage.push("sentMessages", MessageCommands.sentMessages(state, playerId)));
      conn.sendTextAndAwait(ServerMessage.push("unreadMessageCount", MessageCommands.unreadMessageCount(state, playerId)));
    }
  }

  private Player handleLogin(JsonNode payload) {
    String playerId = text(payload, "playerId");
    if (playerId == null) {
      throw new CommandException("login benötigt 'playerId'.");
    }
    Optional<Player> player = state.players.stream().filter(p -> p.id.equals(playerId)).findFirst();
    if (player.isEmpty()) {
      throw new CommandException("Unbekannter Kommandant: " + playerId);
    }
    connections.login(playerId, connection);
    return player.get();
  }

  private Object handleLogout() {
    connections.logout(connection);
    return null;
  }

  /**
   * Registriert einen neuen Kommandanten – Java-Gegenstück zu
   * {@code SimulatedGameApiService.registerPlayer}. Bewusste Abweichung vom
   * TS-Original (siehe {@link GameStateSeeder}): im geteilten Server-Modell
   * gibt es keinen automatisch vorregistrierten Standard-Kommandanten. Der
   * ERSTE echte Registrierungsaufruf überhaupt erzeugt deshalb die komplette
   * Galaxie ({@code createWorldSeed}), jeder weitere fügt sich in die
   * bestehende ein ({@code createAdditionalPlayerSeed}) – exakt wie im
   * TS-Original ab dem zweiten Kommandanten.
   */
  private Player handleRegisterPlayer(JsonNode payload) {
    String commanderName = payload.path("commanderName").asText("").trim();
    String homeworldName = payload.path("homeworldName").asText("").trim();
    if (commanderName.isEmpty()) commanderName = "Unbekannter Kommandant";
    if (homeworldName.isEmpty()) homeworldName = "Heimatwelt";
    PlayerRole role;
    try {
      role = PlayerRole.valueOf(payload.path("role").asText(PlayerRole.Normal.name()));
    } catch (IllegalArgumentException e) {
      role = PlayerRole.Normal;
    }
    String campId = payload.hasNonNull("campId") ? payload.path("campId").asText().trim() : null;
    if (campId != null && campId.isEmpty()) campId = null;

    Player player;
    synchronized (state) {
      if (state.systems.isEmpty()) {
        WorldSeed.Seed seed = WorldSeed.createWorldSeed(commanderName, homeworldName, ids, role, campId);
        GameStateSeeder.bootstrap(state, seed, ids);
        player = seed.player;
      } else {
        WorldSeed.AdditionalSeed seed = WorldSeed.createAdditionalPlayerSeed(
            state.systems, state.players, commanderName, homeworldName, ids, role, campId);
        GameStateSeeder.appendPlayer(state, seed, ids);
        player = seed.player;
      }
    }
    connections.login(player.id, connection);
    connection.broadcast().sendTextAndAwait(ServerMessage.push("players", List.copyOf(state.players)));
    return player;
  }

  /** Kompletter Fabrik-Reset der GESAMTEN gemeinsamen Galaxie (alle Kommandanten!) – danach leere Galaxie, kein Auto-Kommandant (siehe {@link #handleRegisterPlayer}). */
  private Object handleResetGame() {
    synchronized (state) {
      state.players.clear();
      state.systems.clear();
      state.knownSystemIdsByPlayer.clear();
      state.exploredSystemIdsByPlayer.clear();
      state.planets.clear();
      state.colonies.clear();
      state.planetStats.clear();
      state.powerStates.clear();
      state.populations.clear();
      state.moneySupplyStates.clear();
      state.wallets.clear();
      state.transactions.clear();
      state.buildings.clear();
      state.specializations.clear();
      state.productionQueue.clear();
      state.warehouse.clear();
      state.gateways.clear();
      state.fleets.clear();
      state.shipyardQueue.clear();
      state.groundForceGroups.clear();
      state.recruitmentQueue.clear();
      state.sellOrders.clear();
      state.hubOrders.clear();
      state.hubDepot.clear();
      state.universeStats.clear();
      state.notifications.clear();
      state.diplomaticRelations.clear();
      state.peaceOffers.clear();
      state.battles.clear();
      state.blockades.clear();
      state.messages.clear();
      state.populationHistory.clear();
      state.consumptionBudget.clear();
      state.lastProducedAt.clear();
    }
    connections.logout(connection);
    connection.broadcast().sendTextAndAwait(ServerMessage.push("players", List.of()));
    return null;
  }
}
