import { Id } from './common.model';

export type NotificationType = 'Info' | 'Warnung' | 'Problem';

/**
 * Eigenständiges Benachrichtigungssystem, siehe Konzeption/Umsetzungskonzept/
 * 10_Sequentielle_Produktionsauftraege_und_Ereignissystem.md, §5. Jeder
 * Code ist eindeutig einem Sachverhalt zugeordnet; grobe Konvention nach
 * Typ gestaffelt (1xx Info, 4xx Warnung, 5xx Problem) – nicht technisch
 * erzwungen, nur Konvention.
 *
 * Bekannte Codes:
 * - 503 (Problem): Auftragswarteschlange einer Kolonie mangels Vorprodukten angehalten.
 * - 4xx (Warnung, reserviert, noch nicht ausgelöst): z. B. feindliche Flotte im System gesichtet – wartet auf Kampf-/Flottenbewegungslogik.
 */
export interface GameNotification {
  id: Id;
  code: number;
  type: NotificationType;
  message: string;
  /** Adressat KOLONIE – sichtbar für deren jeweiligen Eigentümer, wandert bei Eroberung mit. */
  colonyId: Id | null;
  /**
   * Adressat KOMMANDANT (Umsetzungskonzept/34_...md, §J 9): alles, was den
   * Spieler selbst betrifft – Diplomatie, Gefechte, Verlust der Heimatwelt.
   * Vorher hing das an seiner Heimatwelt; nach deren Fall bekam der Eroberer
   * die Post des Verlierers.
   */
  playerId: Id | null;
  createdAt: number;
  read: boolean;
  /**
   * "Beibehalten": schützt den Eintrag vor der automatischen Löschung nach
   * `NOTIFICATION_RETENTION_REAL_DAYS` ECHTEN Tagen (siehe `RetentionCleanup`
   * im Backend – REALZEIT-AUSNAHME).
   */
  keep: boolean;
  /** Interner Routen-Pfad (z. B. `/kampfbericht/<token>`) – wird im Benachrichtigungs-Panel als Link gerendert, wenn gesetzt. */
  link: string | null;
  /**
   * Beschriftung des Links, vom Server aus dem Code abgeleitet
   * (`Notifications.linkLabel`). Vorher stand im Panel fest "Kampfbericht
   * öffnen" – auch an einer Versorgungswarnung, die auf eine Kolonie zeigt.
   */
  linkLabel: string | null;
}
