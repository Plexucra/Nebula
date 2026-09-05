package de.nebula.state;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Portierung von {@code core/sim/id.ts}. {@link #next} liefert fortlaufende,
 * sortierbare IDs (Gegenstück zu {@code nextId}); {@link #randomToken}
 * liefert unerratbare Zufalls-Tokens für öffentlich teilbare Links (Gegenstück
 * zu {@code randomToken}, z. B. {@code Battle.reportToken}).
 */
@ApplicationScoped
public class IdGenerator {
  private final AtomicLong counter = new AtomicLong();

  public String next(String prefix) {
    return prefix + "_" + Long.toString(counter.incrementAndGet(), 36);
  }

  public String randomToken() {
    return UUID.randomUUID().toString();
  }
}
