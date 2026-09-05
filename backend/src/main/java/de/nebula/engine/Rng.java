package de.nebula.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 1:1-Portierung von {@code frontend/src/app/core/sim/rng.ts}: deterministischer,
 * seed-basierter Zufallsgenerator für die Weltgenerierung (Galaxie-Topologie,
 * Planetentypen/-fördergüten, NPC-Auswahl). Dieselbe lineare Kongruenzformel wie
 * im TS-Original, inklusive dessen ECMAScript-{@code ToInt32}-Semantik bei der
 * {@code & 0x7fffffff}-Maskierung (siehe {@link #toInt32}) – damit erzeugt derselbe
 * Seed exakt dieselbe Zahlenfolge wie im Frontend, keine bloße "ähnliche" Portierung.
 */
@FunctionalInterface
public interface Rng {
  /** Nächste Pseudozufallszahl in [0, 1). */
  double next();

  /**
   * Reproduziert JS' {@code ToInt32}-Konvertierung (ECMA-262 §7.1.6) für die in
   * {@link #seeded} verwendete {@code double}-Arithmetik: {@code s * 1103515245 + 12345}
   * überschreitet für große {@code s} die 53-Bit-Ganzzahlgenauigkeit eines Double, sodass
   * eine naive {@code (long) wert & 0x7fffffff}-Umsetzung in Java ein anderes Ergebnis als
   * JS' {@code &}-Operator liefern würde. Diese Methode bildet exakt nach, was JS in diesem
   * Fall tut: zur Null hin abschneiden, dann modulo 2^32 in den vorzeichenbehafteten
   * Int32-Bereich falten.
   */
  static int toInt32(double d) {
    if (Double.isNaN(d) || Double.isInfinite(d)) return 0;
    double truncated = Math.signum(d) * Math.floor(Math.abs(d));
    double mod = truncated % 4294967296.0; // 2^32
    if (mod < 0) mod += 4294967296.0;
    return mod >= 2147483648.0 ? (int) (mod - 4294967296.0) : (int) mod;
  }

  static Rng seeded(long seed) {
    return new Rng() {
      double s = seed;

      @Override
      public double next() {
        s = toInt32(s * 1103515245.0 + 12345.0) & 0x7fffffff;
        return s / 0x7fffffff;
      }
    };
  }

  static <T> List<T> shuffle(List<T> list, Rng rng) {
    List<T> copy = new ArrayList<>(list);
    for (int i = copy.size() - 1; i > 0; i--) {
      int j = (int) Math.floor(rng.next() * (i + 1));
      T tmp = copy.get(i);
      copy.set(i, copy.get(j));
      copy.set(j, tmp);
    }
    return copy;
  }
}
