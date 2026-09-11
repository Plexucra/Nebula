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
import de.nebula.state.Economy;
import de.nebula.state.FleetCommands;
import de.nebula.state.FleetCompositionCommands;
import de.nebula.state.GatewayCommands;
import de.nebula.state.GroundBattleCommands;
import de.nebula.state.LandingCommands;
import de.nebula.state.MarketCommands;
import de.nebula.state.MessageCommands;
import de.nebula.state.NotificationCommands;
import de.nebula.state.ProductionCommands;
import de.nebula.state.RecruitmentCommands;
import de.nebula.state.TroopTransportCommands;
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
import java.util.Set;
import java.util.concurrent.locks.Lock;

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
      // Abfragen unter dem Leseschloss (beliebig viele gleichzeitig), Befehle unter dem
      // Schreibschloss – zusammen mit dem Ereignisplaner (GameTick). Was in READ_ONLY steht,
      // darf den Zustand nicht anfassen, sonst laufen zwei Leser in dieselbe Liste.
      Lock guard = READ_ONLY.contains(message.type) ? state.lock.readLock() : state.lock.writeLock();
      Object result;
      guard.lock();
      try {
        result = dispatch(message.type, message.payload);
      } finally {
        guard.unlock();
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
   * Die reinen Abfragen des Protokolls – sie laufen unter dem Leseschloss.
   * Jeder neue Befehl ist bis zur Aufnahme hier ein Schreibbefehl; das ist die
   * sichere Voreinstellung. Eine Abfrage gehört nur hierher, wenn ihr Pfad
   * nichts anlegt und nichts ändert (siehe {@code EnergyStorageCommands.view},
   * das früher beim Lesen einen Speicher anlegte).
   */
  private static final Set<String> READ_ONLY = Set.of(
      "players", "serverTime", "productTypes", "buildingTypes", "shipTypes", "groundUnitTypes",
      "colonies", "coloniesInSystem", "colony", "colonyStats", "population", "moneySupplyState", "populationWallet",
      "consumptionCoverage", "populationSupply", "colonySpeedBreakdown", "populationTrend", "transactions", "treasuryFlowPerHour",
      "planet", "planetsInSystem", "colonizations", "supplyInventory",
      "buildings", "buildSlots", "housingCapacity", "powerCoverage", "isBlackout", "powerUpkeepPerHour", "energyStorage",
      "warehouse", "specializations", "productionQueue", "previewProductionChain",
      "wallet", "sellOrders", "hubDepot", "hubOrders", "universeStats", "victory",
      "notifications", "unreadNotificationCount", "inbox", "sentMessages", "unreadMessageCount",
      "fleets", "allFleets", "fleetsInSystem", "fleet", "fleetPresence", "shipyardQueue", "fleetTroopCapacity",
      "groundForcesAtPlanet", "landedGroundForces", "carrierJumpPreview", "routePreview", "routePreviews", "fleetCargoCapacity",
      "groundForces", "recruitmentQueue",
      "gateway", "gatewayWeights", "visibleSystems", "system", "galaxyRoutes", "hasVisitedSystem", "hasExploredSystem",
      "blockadesInSystem", "diplomaticStatus", "activeWars", "incomingPeaceOffers", "outgoingPeaceOffers",
      "treaties", "incomingTreatyOffers", "outgoingTreatyOffers", "hasPeaceTreaty", "hasTradeAgreement",
      "activeBattles", "battle", "battleHistory", "battleByReportToken", "attackableFleetsInSystem",
      "activeGroundBattles", "groundBattle", "groundBattleHistory", "groundBattleByReportToken",
      "attackableColoniesForGroup", "isColonyUnderGroundAttack");

  /**
   * Befehls-Dispatch anhand von {@code type} (1:1 zu einem {@code GameApi}-Methodennamen).
   * Wird mit jeder portierten Phase aus dem Migrationsplan (§"Reihenfolge der
   * Geschäftslogik-Portierung") um weitere {@code case}-Zweige ergänzt.
   */
  private Object dispatch(String type, JsonNode payload) {
    return switch (type) {
      // --- Konto/Anmeldung ---------------------------------------------------
      case "players" -> List.copyOf(state.players);
      // Spieluhr, Realuhr und ihr Versatz (siehe Clock) – für Werkzeuge; die Oberfläche
      // bekommt die Spielzeit ohnehin mit jeder Nachricht (ServerMessage.gameNow).
      case "serverTime" -> java.util.Map.of("gameNow", de.nebula.engine.Clock.now(),
          "realNow", de.nebula.engine.Clock.realNow(), "offsetMs", de.nebula.engine.Clock.offsetMs());
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
      case "populationSupply" -> ColonyCommands.populationSupply(state, text(payload, "colonyId"));
      case "colonySpeedBreakdown" -> ColonyCommands.colonySpeedBreakdown(state, text(payload, "colonyId"));
      case "populationTrend" -> de.nebula.state.PopulationHistory.trend(state, text(payload, "colonyId"));
      case "transactions" -> GameQueries.transactionsForPlayer(state, requirePlayerId());
      case "treasuryFlowPerHour" -> Economy.treasuryFlowPerHour(state, requirePlayerId());
      case "transfer" -> throw new CommandException(
          "Noch kein anderer Kommandant \"" + text(payload, "toPlayerName") + "\" erreichbar – Mehrspieler folgt in einer späteren Ausbaustufe.");
      case "planet" -> ColonyCommands.planetForPlayer(state, text(payload, "id"), currentPlayerId());
      case "planetsInSystem" -> ColonyCommands.planetsInSystemForPlayer(state, text(payload, "systemId"), currentPlayerId());
      case "colonizePlanet" -> ColonyCommands.colonizePlanet(state, ids, requirePlayerId(), text(payload, "planetId"));
      case "colonizations" -> ColonyCommands.colonizationsOf(state, requirePlayerId());
      case "supplyInventory" -> ColonyCommands.supplyInventory(state, text(payload, "colonyId"));

      // --- Bebauung --------------------------------------------------------------
      case "buildings" -> BuildingCommands.buildingsForColony(state, text(payload, "colonyId"));
      case "buildSlots" -> BuildingCommands.buildSlots(state, text(payload, "colonyId"));
      case "housingCapacity" -> de.nebula.state.PowerGrid.effectiveHousingCapacity(state, text(payload, "colonyId"));
      case "powerCoverage" -> de.nebula.state.PowerGrid.coverageRatio(state, text(payload, "colonyId"));
      case "isBlackout" -> de.nebula.state.PowerGrid.isBlackout(state, text(payload, "colonyId"));
      case "powerUpkeepPerHour" -> de.nebula.state.PowerGrid.powerUpkeepPerHour(state, text(payload, "colonyId"));
      // Energiespeicher (Umsetzungskonzept/32_...md): reserveTarget null = automatisch
      case "energyStorage" -> de.nebula.state.EnergyStorageCommands.view(state, text(payload, "colonyId"));
      case "setEnergyReserve" -> {
        JsonNode target = payload.path("reserveTarget");
        de.nebula.state.EnergyStorageCommands.setReserve(state, requirePlayerId(), text(payload, "colonyId"),
            target.isMissingNode() || target.isNull() ? null : target.asDouble());
        yield null;
      }
      case "queueBuilding" -> {
        BuildingCommands.queueBuilding(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "buildingTypeId"));
        yield null;
      }
      case "queueMissingBuildingMaterials" -> BuildingCommands.queueMissingMaterials(state, ids, requirePlayerId(),
          text(payload, "colonyId"), text(payload, "buildingTypeId"));
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
      // body: { colonyId, products: { productTypeId: quantity, ... }, autoProduceMissing, requeueOnComplete } –
      // mehrere direkt benötigte Baustoffe als EIN Auftrag, siehe ProductionCommands.queueProductionBundle.
      case "queueProductionBundle" -> {
        ProductionCommands.queueProductionBundle(state, ids, requirePlayerId(), text(payload, "colonyId"),
            productMap(payload.path("products")),
            payload.path("autoProduceMissing").asBoolean(false), payload.path("requeueOnComplete").asBoolean(false));
        yield null;
      }
      case "moveProductionEntry" -> {
        ProductionCommands.moveProductionEntry(state, requirePlayerId(), text(payload, "colonyId"),
            text(payload, "entryId"), payload.path("direction").asInt());
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
      case "sellOrders" -> MarketCommands.postSellOrdersInSystem(state, text(payload, "systemId"));
      case "createSellOrder" -> {
        MarketCommands.createSellOrderFromColony(state, ids, requirePlayerId(), text(payload, "colonyId"), text(payload, "productTypeId"),
            payload.path("quantity").asDouble(), payload.path("pricePerUnit").asDouble(), payload.path("autoRelist").asBoolean(false));
        yield null;
      }
      case "updateSellOrderPrice" -> {
        MarketCommands.updateOrderPrice(state, ids, requirePlayerId(), text(payload, "orderId"), payload.path("pricePerUnit").asDouble());
        yield null;
      }
      case "cancelSellOrder", "cancelHubOrder" -> {
        MarketCommands.cancelOrder(state, requirePlayerId(), text(payload, "orderId"));
        yield null;
      }
      // deliverToColonyId wird nicht mehr gebraucht: geliefert wird ins Lager der eigenen Kolonie auf dem
      // Planeten des Postens, sonst ins Depot dort (Umsetzungskonzept/37).
      case "buyFromOrder" -> {
        MarketCommands.buyFromOrder(state, ids, requirePlayerId(), text(payload, "orderId"), payload.path("quantity").asDouble());
        yield null;
      }
      case "createSellOrderFromFleet" -> {
        MarketCommands.createSellOrderFromFleet(state, ids, requirePlayerId(), text(payload, "fleetId"), text(payload, "productTypeId"),
            payload.path("quantity").asDouble(), payload.path("pricePerUnit").asDouble(), payload.path("autoRelist").asBoolean(false));
        yield null;
      }

      // --- Orderbuch an Station (ohne planetId) oder Planetarem Handelsposten (Umsetzungskonzept/22 und 37) ---
      case "hubDepot" -> MarketCommands.depotOf(state, text(payload, "systemId"), text(payload, "planetId"), requirePlayerId());
      case "hubOrders" -> MarketCommands.ordersAt(state, text(payload, "systemId"), text(payload, "planetId"));
      case "createHubSellOrder" -> {
        MarketCommands.createSellOrder(state, ids, requirePlayerId(), text(payload, "systemId"), text(payload, "planetId"),
            text(payload, "productTypeId"), payload.path("quantity").asDouble(), payload.path("pricePerUnit").asDouble(),
            payload.path("autoRelist").asBoolean(false));
        yield null;
      }
      case "createHubBuyOrder" -> {
        MarketCommands.createBuyOrder(state, ids, requirePlayerId(), text(payload, "systemId"), text(payload, "planetId"),
            text(payload, "productTypeId"), payload.path("quantity").asDouble(), payload.path("pricePerUnit").asDouble());
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
      case "universeStats" -> Economy.universeStats(state);
      // Der entschiedene Krieg (VictoryCommands) – null, solange mehrere Parteien Kolonien haben.
      case "victory" -> state.victory;

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
      // Flotten gehen als FleetView hinaus: mit Tankgröße, Verbrauch je Sprung und Reichweite.
      case "fleets" -> FleetCommands.views(FleetCommands.fleetsOf(state, requirePlayerId()));
      case "allFleets" -> FleetCommands.views(FleetCommands.allFleets(state));
      case "fleetsInSystem" -> FleetCommands.views(FleetCommands.fleetsInSystem(state, text(payload, "systemId")));
      case "fleet" -> FleetCommands.view(FleetCommands.fleetById(state, text(payload, "id")));
      case "fleetPresence" -> FleetCommands.fleetPresence(state, requirePlayerId());
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
      case "refuelFleet" -> { FleetCommands.refuelFleet(state, requirePlayerId(), text(payload, "fleetId"), payload.path("quantity").asDouble()); yield null; }
      case "drainFleetFuel" -> { FleetCommands.drainFleetFuel(state, requirePlayerId(), text(payload, "fleetId"), payload.path("quantity").asDouble()); yield null; }
      case "transferFuelBetweenFleets" -> { FleetCommands.transferFuelBetweenFleets(state, requirePlayerId(), text(payload, "fromFleetId"), text(payload, "toFleetId"), payload.path("quantity").asDouble()); yield null; }
      case "loadCargo" -> {
        FleetCommands.loadCargo(state, requirePlayerId(), text(payload, "fleetId"), text(payload, "productTypeId"), payload.path("quantity").asDouble());
        yield null;
      }
      case "unloadCargo" -> {
        FleetCommands.unloadCargo(state, requirePlayerId(), text(payload, "fleetId"), text(payload, "productTypeId"), payload.path("quantity").asDouble());
        yield null;
      }
      // Bodentruppen-Verladung (Umsetzungskonzept/28_...md): Soldaten in den
      // Mannschaftstransporter, Drohnen über das Warenlager in den Frachter.
      case "embarkSoldiers" -> {
        TroopTransportCommands.embarkSoldiers(state, ids, requirePlayerId(), text(payload, "fleetId"), payload.path("quantity").asDouble());
        yield null;
      }
      case "disembarkSoldiers" -> {
        TroopTransportCommands.disembarkSoldiers(state, ids, requirePlayerId(), text(payload, "fleetId"), payload.path("quantity").asDouble());
        yield null;
      }
      case "storeDrones" -> {
        TroopTransportCommands.storeDrones(state, requirePlayerId(), text(payload, "colonyId"),
            text(payload, "unitProductTypeId"), payload.path("quantity").asDouble());
        yield null;
      }
      case "deployDrones" -> {
        TroopTransportCommands.deployDrones(state, ids, requirePlayerId(), text(payload, "colonyId"),
            text(payload, "unitProductTypeId"), payload.path("quantity").asDouble());
        yield null;
      }
      case "fleetTroopCapacity" -> TroopTransportCommands.fleetTroopCapacity(state, text(payload, "fleetId"));

      // Landung (Umsetzungskonzept/04_...md): von Bord auf die Planetenoberfläche, von dort
      // per moveGroundForces weiter in eine eigene Kolonie auf demselben Planeten.
      case "land" -> LandingCommands.land(state, ids, requirePlayerId(), text(payload, "fleetId"), text(payload, "targetPlanetId"));
      case "moveGroundForces" -> {
        LandingCommands.moveGroundForces(state, requirePlayerId(), text(payload, "groupId"), text(payload, "targetColonyId"));
        yield null;
      }
      case "groundForcesAtPlanet" -> {
        String playerId = requirePlayerId();
        yield state.groundForceGroups.stream()
            .filter(g -> playerId.equals(g.ownerId) && text(payload, "planetId").equals(g.planetId))
            .toList();
      }
      case "landedGroundForces" -> {
        String playerId = requirePlayerId();
        yield state.groundForceGroups.stream().filter(g -> playerId.equals(g.ownerId) && g.planetId != null).toList();
      }
      case "moveFleet" -> {
        FleetCommands.moveFleet(state, requirePlayerId(), text(payload, "fleetId"), text(payload, "destinationSystemId"),
            payload.path("viaCarrier").asBoolean(false));
        yield null;
      }
      case "carrierJumpPreview" -> FleetCommands.carrierJumpPreview(state, text(payload, "fleetId"),
          text(payload, "destinationSystemId"));
      case "renameFleet" -> {
        FleetCommands.renameFleet(state, requirePlayerId(), text(payload, "fleetId"), text(payload, "name"));
        yield null;
      }
      case "cancelFleetMove" -> {
        FleetCommands.cancelFleetMove(state, requirePlayerId(), text(payload, "fleetId"));
        yield null;
      }
      case "routePreview" -> FleetCommands.routePreview(state, text(payload, "fleetId"), text(payload, "destinationSystemId"));
      case "routePreviews" -> FleetCommands.routePreviewsFrom(state, text(payload, "fleetId"));
      case "fleetCargoCapacity" -> FleetCommands.fleetCargoCapacity(state, text(payload, "fleetId"), text(payload, "productTypeId"));
      // Flottenzusammenstellung (Umsetzungskonzept/33_...md)
      case "mergeFleets" -> {
        FleetCompositionCommands.mergeFleets(state, requirePlayerId(), text(payload, "targetFleetId"), text(payload, "sourceFleetId"));
        yield null;
      }
      // body: { fleetId, ships: { shipProductTypeId: quantity }, cargo: { productTypeId: quantity }, soldiers, name }
      case "splitFleet" -> FleetCompositionCommands.splitFleet(state, ids, requirePlayerId(), text(payload, "fleetId"),
          productMap(payload.path("ships")), productMap(payload.path("cargo")),
          payload.path("soldiers").asDouble(0), text(payload, "name"));

      case "moveFleetWithinSystem" -> {
        FleetCommands.moveFleetWithinSystem(state, ids, requirePlayerId(), text(payload, "fleetId"), parseFleetSystemTarget(payload.path("target")));
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

      // --- Bodengefechte (Mechanik/05_...md §2, §10-12) ------------------------
      case "activeGroundBattles" -> GroundBattleCommands.activeGroundBattles(state, requirePlayerId());
      case "groundBattle" -> GroundBattleCommands.groundBattle(state, text(payload, "id"));
      case "groundBattleHistory" -> GroundBattleCommands.groundBattleHistory(state, requirePlayerId());
      case "groundBattleByReportToken" -> GroundBattleCommands.groundBattleByReportToken(state, text(payload, "token"));
      case "attackableColoniesForGroup" ->
          GroundBattleCommands.attackableColoniesForGroup(state, requirePlayerId(), text(payload, "groupId"));
      case "isColonyUnderGroundAttack" -> GroundBattleCommands.isUnderGroundAttack(state, text(payload, "colonyId"));
      case "engageGroundBattle" -> GroundBattleCommands.engageGroundBattle(state, ids, requirePlayerId(),
          text(payload, "groupId"), text(payload, "targetColonyId"));
      case "retreatFromGroundBattle" -> {
        GroundBattleCommands.retreatFromGroundBattle(state, ids, requirePlayerId(), text(payload, "battleId"));
        yield null;
      }

      default -> throw new CommandException("Unbekannter oder noch nicht portierter Befehl: " + type);
    };
  }

  private static String text(JsonNode payload, String field) {
    return payload.path(field).asText(null);
  }

  /** Parst {@code { productTypeId: quantity, ... }} für {@code queueProductionBundle}. */
  private static java.util.Map<String, Double> productMap(JsonNode node) {
    java.util.LinkedHashMap<String, Double> result = new java.util.LinkedHashMap<>();
    node.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asDouble()));
    return result;
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
  /**
   * Die galaxieweiten, selten wechselnden Listen (Kommandanten, Systeme, Routen)
   * gehen als Push an ALLE Verbindungen, sobald sie sich ändern – bei einer
   * Registrierung und beim Reset. Die Oberfläche pollt sie deshalb nur noch als
   * Rückfallebene im Minutentakt statt sekündlich (bei tausend Systemen wären das
   * je Kartenansicht hunderte Kilobyte je Sekunde gewesen).
   */
  private void broadcastGalaxy() {
    connection.broadcast().sendTextAndAwait(ServerMessage.push("players", List.copyOf(state.players)));
    connection.broadcast().sendTextAndAwait(ServerMessage.push("visibleSystems", GatewayCommands.visibleSystems(state)));
    connection.broadcast().sendTextAndAwait(ServerMessage.push("galaxyRoutes", GatewayCommands.galaxyRoutes(state)));
  }

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
    // REALZEIT-AUSNAHME: echter Zeitstempel für die Inaktivitäts-Löschfrist
    // (siehe RetentionCleanup) – bewusst keine Spielzeit.
    player.get().lastSeenAt = de.nebula.engine.Clock.realNow();
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
    // Vorher wurden leere Eingaben still auf "Unbekannter Kommandant"/"Heimatwelt"
    // gesetzt. Weil der Name zugleich die Kennung in der Anmeldeliste und im
    // Empfängerfeld ist, entstanden so nicht unterscheidbare Kommandanten.
    if (commanderName.isEmpty()) {
      throw new CommandException("Bitte einen Namen für den Kommandanten angeben.");
    }
    if (homeworldName.isEmpty()) {
      throw new CommandException("Bitte einen Namen für die Heimatkolonie angeben.");
    }
    String candidate = commanderName;
    if (state.players.stream().anyMatch(p -> p.name.equalsIgnoreCase(candidate))) {
      throw new CommandException("Den Kommandanten \"" + commanderName + "\" gibt es bereits – bitte einen anderen Namen wählen.");
    }
    PlayerRole role;
    try {
      role = PlayerRole.valueOf(payload.path("role").asText(PlayerRole.Normal.name()));
    } catch (IllegalArgumentException e) {
      role = PlayerRole.Normal;
    }
    String campId = payload.hasNonNull("campId") ? payload.path("campId").asText().trim() : null;
    if (campId != null && campId.isEmpty()) campId = null;

    // Die Sperre auf state hält bereits onMessage – hier keine zweite.
    Player player;
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
    connections.login(player.id, connection);
    // REALZEIT-AUSNAHME, siehe handleLogin.
    player.lastSeenAt = de.nebula.engine.Clock.realNow();
    broadcastGalaxy();
    return player;
  }

  /**
   * Kompletter Fabrik-Reset der GESAMTEN gemeinsamen Galaxie (alle Kommandanten!) –
   * danach leere Galaxie, kein Auto-Kommandant (siehe {@link #handleRegisterPlayer}).
   * Die Sperre auf {@code state} hält bereits {@link #onMessage}.
   */
  private Object handleResetGame() {
    state.reset();
    connections.logout(connection);
    broadcastGalaxy();
    return null;
  }
}
