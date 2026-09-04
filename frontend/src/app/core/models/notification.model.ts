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
  colonyId: Id | null;
  createdAt: number;
  read: boolean;
}
