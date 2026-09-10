package de.nebula.model;

/**
 * Ein geplantes Spielereignis: ZU diesem Spielzeitpunkt ({@link #at}) passiert
 * DAS ({@link #type}) MIT diesem Objekt ({@link #targetId}). Der
 * Ereignisplaner ({@code GameEvents}) hält je Typ und Ziel höchstens ein
 * Ereignis – eine Neuplanung ersetzt die alte.
 *
 * <p>Bewusst Daten statt Verhalten (Typ als Aufzählung, Ziel als Id): so lässt
 * sich die Warteschlange serialisieren und später journalisieren, und die
 * Behandlung bleibt in den Befehlsklassen, wo auch das Anlegen des Ziels
 * steht.</p>
 */
public final class ScheduledEvent implements Comparable<ScheduledEvent> {
  /** Fälligkeit in SPIELZEIT ({@code Clock.now()}-Basis). */
  public final long at;
  /** Laufende Nummer – hält die Reihenfolge gleichzeitig fälliger Ereignisse stabil. */
  public final long seq;
  public final String type;
  /** Id des betroffenen Objekts; leer bei wiederkehrenden Aufgaben ohne Objektbezug. */
  public final String targetId;

  public ScheduledEvent(long at, long seq, String type, String targetId) {
    this.at = at;
    this.seq = seq;
    this.type = type;
    this.targetId = targetId;
  }

  @Override
  public int compareTo(ScheduledEvent o) {
    int byTime = Long.compare(at, o.at);
    return byTime != 0 ? byTime : Long.compare(seq, o.seq);
  }

  @Override
  public String toString() {
    return type + "(" + targetId + ")@" + at;
  }
}
