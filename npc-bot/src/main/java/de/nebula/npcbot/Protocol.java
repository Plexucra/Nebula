package de.nebula.npcbot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Das Koordinationsprotokoll der Bots eines Lagers – ausschließlich über das
 * reguläre Ingame-Nachrichtensystem ({@code sendMessage}/{@code inbox}),
 * ohne jeden Seitenkanal. Betreff = Nachrichtentyp, Text = {@code key=value}
 * je Zeile; damit bleibt jede Nachricht auch für einen menschlichen
 * Mitspieler im Posteingang lesbar.
 *
 * <ul>
 *   <li>{@link #STATUS} Mitglied → Koordinator: Lagebericht (Kolonien,
 *       Blackout, Produktionsfähigkeit, Militär, Bedrohung).</li>
 *   <li>{@link #ASSIGN} Koordinator → Mitglied: Zuteilung von Wirtschafts-
 *       spezialisierung, Militärrolle und Ziel; zugleich Lebenszeichen des
 *       Koordinators (Failover, siehe {@code Coordination}).</li>
 *   <li>{@link #CLAIM}/{@link #CLAIM_OK}/{@link #CLAIM_DENY}: Anspruch auf
 *       einen Planeten (Besiedlung) bzw. eine Zielkolonie (Invasion), damit
 *       nicht zwei Bots dasselbe Ziel bearbeiten.</li>
 *   <li>{@link #REPORT} Mitglied → Koordinator: Ereignismeldung (Eroberung,
 *       Gründung, Verlust) – fließt in die nächste Zuteilung ein.</li>
 * </ul>
 */
final class Protocol {
  private Protocol() {
  }

  static final String PREFIX = "NPC:";
  static final String STATUS = "NPC:STATUS";
  static final String ASSIGN = "NPC:ASSIGN";
  static final String CLAIM = "NPC:CLAIM";
  static final String CLAIM_OK = "NPC:CLAIM-OK";
  static final String CLAIM_DENY = "NPC:CLAIM-DENY";
  static final String REPORT = "NPC:REPORT";

  static String encode(Map<String, String> fields) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : fields.entrySet()) {
      if (e.getValue() == null) continue;
      sb.append(e.getKey()).append('=').append(e.getValue().replace('\n', ' ')).append('\n');
    }
    return sb.length() == 0 ? "-" : sb.toString();
  }

  static Map<String, String> decode(String body) {
    Map<String, String> out = new LinkedHashMap<>();
    if (body == null) return out;
    for (String line : body.split("\n")) {
      int i = line.indexOf('=');
      if (i <= 0) continue;
      out.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
    }
    return out;
  }
}
