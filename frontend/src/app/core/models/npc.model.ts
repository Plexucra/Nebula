import { Id } from './common.model';

/**
 * Ein NPC verhält sich technisch wie ein Spieler (eigene Colony, Wallet,
 * Produktion, Marktorders – dieselben Engine-Pfade wie der Mensch), wird
 * aber von einer einfachen internen KI statt vom UI gesteuert. Bewusst
 * nicht kriegerisch (siehe `Npc.md`/Auftrag): baut nie Kampfschiffe,
 * Werft oder Bodentruppen, nur Infrastruktur, Rohstoff-/Konsumgüter-
 * produktion und lokalen Handel.
 */
export interface Npc {
  id: Id;
  name: string;
  homeColonyId: Id;
  homeSystemId: Id;
  /** Rohstoff, auf den sich der NPC spezialisiert (meist die höchste Konzentration seines Planeten). */
  specialtyProductId: Id;
}
