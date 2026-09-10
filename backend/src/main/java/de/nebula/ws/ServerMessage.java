package de.nebula.ws;

/**
 * Umschlag für JEDE Server→Client-Nachricht. Drei Verwendungen:
 * <ul>
 *   <li>{@code type: "Ack"}, {@code requestId} gesetzt, {@code payload} =
 *       Rückgabewert einer Befehlsmethode (z. B. das neu angelegte
 *       {@code Player}-Objekt bei {@code registerPlayer}) – Antwort auf genau
 *       EIN {@link ClientMessage}.</li>
 *   <li>{@code type: "Error"}, {@code requestId} gesetzt, {@code payload} =
 *       {@code {"message": "..."}} – Pendant zum abgelehnten Promise im
 *       Frontend (siehe {@code GameApi}-Fehlertexte, die 1:1 weitergereicht
 *       werden sollen).</li>
 *   <li>{@code type} = Name einer Signal-Datenquelle (z. B.
 *       {@code "players"}, {@code "fleets"}, {@code "colonies"}),
 *       {@code requestId: null}, {@code payload} = aktueller Stand dieser
 *       Datenquelle – unaufgeforderter Push nach jeder Mutation, die sie
 *       betrifft (Ersatz für die Angular-Signal-Reaktivität der
 *       {@code SimulatedGameApiService}, siehe Migrationsplan §5
 *       "Sync-Strategie").</li>
 * </ul>
 */
public class ServerMessage {
  public String type;
  public String requestId;
  public Object payload;
  /**
   * Die SPIELUHR des Servers beim Absenden ({@code Clock.now()}). Sie kann
   * gegen die Wanduhr der Empfänger verschoben sein (siehe {@code Clock});
   * Oberfläche und Bots stellen ihre eigene Spieluhr an jeder Nachricht
   * danach und rechnen Countdowns, Alter und Fortschritt damit – nie mit
   * {@code Date.now()} bzw. {@code System.currentTimeMillis()}.
   */
  public long gameNow;

  public ServerMessage() {
  }

  public ServerMessage(String type, String requestId, Object payload) {
    this.type = type;
    this.requestId = requestId;
    this.payload = payload;
    this.gameNow = de.nebula.engine.Clock.now();
  }

  public static ServerMessage ack(String requestId, Object payload) {
    return new ServerMessage("Ack", requestId, payload);
  }

  public static ServerMessage error(String requestId, String message) {
    return new ServerMessage("Error", requestId, new ErrorPayload(message));
  }

  public static ServerMessage push(String channel, Object payload) {
    return new ServerMessage(channel, null, payload);
  }

  public static class ErrorPayload {
    public String message;

    public ErrorPayload(String message) {
      this.message = message;
    }
  }
}
