package de.nebula.model;

public class Player {
  public String id;
  public String name;
  public String homeworldColonyId;
  public String homeSystemId;
  public long createdAt;
  /**
   * REALZEIT-AUSNAHME (siehe {@code GameConstants.INACTIVE_PLAYER_DELETION_REAL_MS}):
   * echter Zeitstempel der letzten Anmeldung bzw. der Registrierung. Wer sich
   * {@code inactivePlayerDeletionRealDays} echte Tage nicht mehr anmeldet, wird
   * von {@code RetentionCleanup.deleteInactivePlayers} samt Kolonien, Flotten
   * und allem Übrigen spurlos entfernt. Bewusst KEINE Spielzeit: Ob jemand
   * zurückkommt, hängt nicht am Tempo-Regler.
   */
  public long lastSeenAt;
  public PlayerRole role;
  /** Lager-Kennzeichen für {@code role == Npc} (z. B. "NORD"/"SUED"), sonst null. */
  public String campId;
}
