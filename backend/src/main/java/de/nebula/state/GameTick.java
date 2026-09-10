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
 * und feuern zu ihrer Zeit; Reaktionen (Order nachfüllen, Sieg prüfen,
 * Notkauf, Blackout beenden) hängen an der Zustandsänderung, die sie auslöst;
 * die Wirtschaft läuft je Kolonie einmal je Spieltag als Kolonietag
 * ({@link Economy#colonyDay}, Umsetzungskonzept/36).</p>
 *
 * <p>Warum trotzdem ein Sekundentakt und kein schlafender Planer: Quarkus
 * liefert den Takt ohne eigenen Thread, und die Auflösung von einer Sekunde
 * ist die kleinste sinnvolle für ein Spiel mit 2,5 s je Spielstunde. Der Takt
 * rechnet selbst nichts – ohne fällige Ereignisse ist er ein Blick auf die
 * Spitze der Warteschlange.</p>
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
   * ({@code Clock.GAME_SPEED_MULTIPLIER}): schnelleres Spiel heißt, dass in
   * einer Realsekunde mehr Spielzeit vergeht und mehr Ereignisse fällig werden,
   * nicht mehr Takte je Sekunde.
   */
  @Scheduled(every = "1s")
  void tick() {
    if (state.players.isEmpty()) return;
    state.lock.writeLock().lock();
    try {
      GameEvents.runDue(state, ids, Clock.now());
    } finally {
      state.lock.writeLock().unlock();
    }
  }
}
