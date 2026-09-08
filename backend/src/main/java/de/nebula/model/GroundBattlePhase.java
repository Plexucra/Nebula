package de.nebula.model;

/**
 * Ein Bodengefecht läuft in ZWEI Phasen, und ein Gefecht kann beliebig oft
 * zwischen ihnen hin- und herwechseln (Nutzervorgabe, Konkretisierung zu
 * Mechanik/05_..., §10):
 *
 * <ul>
 *   <li>{@link #Combat} – reguläre Kampfticks wie beim Angriff auf eine
 *       Blockade: Drohne gegen Drohne, Soldaten sind dabei ausschließlich
 *       Bediener ihrer Drohnen und kämpfen nicht selbst.</li>
 *   <li>{@link #Siege} – sobald die militärische Gegenwehr gebrochen ist.
 *       Jetzt kämpfen ausschließlich die SOLDATEN des Angreifers gegen
 *       aufständische Zivilisten, und ausschließlich die Loyalität entscheidet
 *       über den Ausgang. Drohnen und alles andere zählen hier nicht.</li>
 * </ul>
 *
 * <p>Der Wechsel geht in beide Richtungen: trifft während der Belagerung
 * wieder eine kampffähige Verteidigung ein, beginnen erneut reguläre
 * Kampfticks; ist auch die geschlagen, geht es zurück in die Belagerung.</p>
 */
public enum GroundBattlePhase {
  Combat, Siege
}
