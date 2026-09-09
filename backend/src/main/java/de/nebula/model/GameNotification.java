package de.nebula.model;

/**
 * Eigenständiges Benachrichtigungssystem, siehe Umsetzungskonzept/10_...md,
 * §5. Code-Konvention nach Typ gestaffelt (1xx Info, 4xx Warnung, 5xx
 * Problem) – nicht technisch erzwungen. Bekannte Codes: 503 (Problem) =
 * Auftragswarteschlange mangels Vorprodukten angehalten; 101/102 (Info) =
 * Friedensangebot gestellt/angenommen; 401 (Warnung) = Kriegserklärung;
 * 402/403 (Warnung) = Gefecht begonnen/beendet.
 */
public class GameNotification {
  public String id;
  public int code;
  public NotificationType type;
  public String message;
  /**
   * Adressat KOLONIE – die Meldung gehört dem Eigentümer dieser Kolonie und
   * wandert mit ihr mit (eine eroberte Kolonie nimmt ihre Meldungen mit zum
   * Eroberer). {@code null}, wenn die Meldung an einen Kommandanten oder an
   * alle gerichtet ist.
   */
  public String colonyId;
  /**
   * Adressat KOMMANDANT (Umsetzungskonzept/34_...md, §J 9). Alles, was den
   * Spieler selbst betrifft – Diplomatie, Gefechte, Verlust der Heimatwelt –,
   * hing vorher an seiner {@code homeworldColonyId}: nach dem Fall der
   * Heimatwelt landeten diese Meldungen beim EROBERER, und ein Kommandant ohne
   * Kolonie bekam gar keine mehr. {@code null}, wenn die Meldung an eine
   * Kolonie oder an alle gerichtet ist.
   */
  public String playerId;
  public long createdAt;
  public boolean read;
  /**
   * "Beibehalten": schützt den Eintrag vor der automatischen Löschung nach
   * {@code GameConstants.NOTIFICATION_RETENTION_REAL_MS} (siehe
   * {@code RetentionCleanup}, REALZEIT-AUSNAHME). Standard {@code false}.
   */
  public boolean keep;
  /** Interner Routen-Pfad (z. B. {@code /kampfbericht/<token>}) – wird im Benachrichtigungs-Panel als Link gerendert, wenn gesetzt. */
  public String link;
  /**
   * Beschriftung des Links, abgeleitet aus dem Code
   * ({@code Notifications.linkLabel}). Kommt vom Server, weil sonst jede neue
   * Meldungsart das Frontend zwingt, ihren Code zu kennen – vorher stand dort
   * fest "Kampfbericht öffnen", auch an einer Versorgungswarnung.
   */
  public String linkLabel;
}
