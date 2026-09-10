package de.nebula.state;

import de.nebula.engine.Clock;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Der Realzeit-Takt der Galaxie. NICHT an einen eingeloggten Nutzer gebunden:
 * die gemeinsame Galaxie simuliert immer weiter, unabhängig davon, wer gerade
 * verbunden ist.
 *
 * <p>Der Takt selbst tut nur noch eines: er lässt den Ereignisplaner alles
 * abarbeiten, was bis zur aktuellen SPIELZEIT fällig ist ({@link GameEvents#runDue}).
 * Die frühere Schleife aus zwanzig {@code processXxx}-Schritten in fester
 * Reihenfolge ist aufgelöst: Fälligkeiten (Bau, Flug, Gefechtsrunde, Auftrag,
 * Vertrag, Spezialisierungsverfall) werden dort geplant, wo sie entstehen,
 * und feuern zu ihrer Zeit; Reaktionen (Order nachfüllen, Sieg prüfen) hängen
 * an der Zustandsänderung, die sie auslöst; nur die Ratenprozesse der
 * Wirtschaft laufen weiter als ein Block in fester Reihenfolge
 * ({@link EconomyTick#economyStep}), als wiederkehrendes Ereignis.</p>
 *
 * <p>Warum trotzdem ein Sekundentakt und kein schlafender Planer: Quarkus
 * liefert den Takt ohne eigenen Thread, die Auflösung von einer Sekunde ist
 * die kleinste sinnvolle für ein Spiel mit 2,5 s je Spielstunde, und der
 * Wirtschaftsschritt ist ohnehin jede Sekunde fällig.</p>
 */
@ApplicationScoped
public class GameTick {

  private final GameState state;
  private final IdGenerator ids;

  public GameTick(GameState state, IdGenerator ids) {
    this.state = state;
    this.ids = ids;
  }

  /**
   * Realzeit-Takt, BEWUSST unabhängig vom Tempo-Regler
   * ({@code Clock.GAME_SPEED_MULTIPLIER}): schnelleres Spiel heißt nicht mehr
   * Ticks je Sekunde, sondern mehr Spielstunden je Wirtschaftsschritt
   * ({@code GameConstants.TICK_GAME_HOURS}). Der Wert muss zu
   * {@code GameConstants.TICK_MS} passen – hier ein Textliteral, weil
   * Annotationswerte Konstanten sein müssen.
   */
  @Scheduled(every = "1s")
  void tick() {
    if (state.players.isEmpty()) return;
    synchronized (state) {
      GameEvents.runDue(state, ids, Clock.now());
    }
  }
}
