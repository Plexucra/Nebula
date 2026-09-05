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
  public String colonyId;
  public long createdAt;
  public boolean read;
  /** Interner Routen-Pfad (z. B. {@code /kampfbericht/<token>}) – wird im Benachrichtigungs-Panel als Link gerendert, wenn gesetzt. */
  public String link;
}
