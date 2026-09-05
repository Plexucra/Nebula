import { Id } from './common.model';

/**
 * Ingame-Nachrichtensystem – ausschließlich Spieler-zu-Spieler ("E-Mail"),
 * AUSDRÜCKLICH keine Gruppen-/Broadcast-Nachrichten. Vollständig neues
 * Feature ohne TS-Vorlage (siehe Umsetzungskonzept/14_...md), aber nach
 * demselben Muster wie {@link GameNotification} gehalten: eine flache Liste,
 * je Kommandant client-/serverseitig auf seine eigenen Nachrichten gefiltert.
 */
export interface Message {
  id: Id;
  fromPlayerId: Id;
  toPlayerId: Id;
  subject: string;
  body: string;
  sentAt: number;
  /** Nur vom Empfänger (`toPlayerId`) gesetzt/gesetzt werden. */
  read: boolean;
}
