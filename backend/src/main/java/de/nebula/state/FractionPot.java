package de.nebula.state;

/**
 * Übertragskonten ("Töpfe") für wiederkehrende Raten, die je Schritt UNTER einem
 * ganzen Stück liegen (Umsetzungskonzept/25_...md).
 *
 * <p>Alle Warenbewegungen im Spiel sollen in ganzen Stücken stattfinden. Bei
 * einmaligen Resten (anteilige Gutschrift beim Abbruch, ladbare Restmenge) ist
 * Abschneiden unproblematisch – der Rest verfällt und niemand vermisst ihn.
 * Bei wiederkehrenden RATEN ist das anders: der Bevölkerungsbedarf liegt bei
 * 120 Einwohnern bei 0,0096 Stück Grundnahrung je Sekundentakt (heute je Kolonietag: 0,58), der Elerium-Verbrauch
 * der Infrastruktur bei 0,0188. Würde man das je Schritt abschneiden, fände der
 * Vorgang NIE statt – die Bevölkerung kaufte für immer nichts. Würde man
 * aufrunden, explodierte der Verbrauch (das gab es hier schon einmal, siehe
 * Kommentar in {@code Economy.consumeFromStock}).</p>
 *
 * <p>Deshalb wird der Bruchteil hier gesammelt, bis ein ganzes Stück
 * zusammenkommt. Nach außen bewegt sich dadurch immer nur Ganzes, während die
 * langfristige Rate exakt erhalten bleibt: nichts geht verloren, es wird nur
 * aufgeschoben. Die Bruchteile sind damit auf diese eine Klasse isoliert und
 * tauchen in Lager, Orders und Anzeigen nicht mehr auf.</p>
 */
public final class FractionPot {
  private FractionPot() {
  }

  /** Schlüssel je Kolonie und Verwendungszweck – ein Topf darf nur EINEN Fluss abbilden. */
  public static String key(String purpose, String colonyId) {
    return purpose + ':' + colonyId;
  }

  public static String key(String purpose, String colonyId, String productTypeId) {
    return purpose + ':' + colonyId + ':' + productTypeId;
  }

  /**
   * Bucht {@code amount} in den Topf und gibt die daraus fälligen GANZEN Stücke
   * zurück; der Rest bleibt für den nächsten Aufruf liegen.
   *
   * <p>Abgeschnitten wird Richtung Null, damit negative Raten (schrumpfende
   * Bevölkerung) symmetrisch funktionieren: −0,3 ergibt 0 fällige Stücke und
   * behält −0,3 im Topf, −1,2 ergibt −1 und behält −0,2.</p>
   */
  public static double due(GameState state, String key, double amount) {
    double carry = state.fractionPots.getOrDefault(key, 0.0) + amount;
    double whole = (double) (long) carry;
    state.fractionPots.put(key, carry - whole);
    return whole;
  }

  /** Aktueller, noch nicht fälliger Rest – für die Anzeige im Versorgungsinventar. */
  public static double pending(GameState state, String key) {
    return state.fractionPots.getOrDefault(key, 0.0);
  }
}
