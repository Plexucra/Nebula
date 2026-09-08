package de.nebula.npcbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Zwei Spuren je Bot: das menschenlesbare Log (jede Entscheidung mit
 * Begründung) und eine maschinenlesbare JSONL-Spur ({@code <bot>.jsonl}) mit
 * einem Kennzahlen-Datensatz je Entscheidungstakt plus einem Datensatz je
 * Ereignis (Strategiewechsel, Rollenwechsel, Angriff, Landung, Eroberung,
 * Gründung, Blackout ...). Die JSONL-Spur wertet {@code report.mjs} aus –
 * nur so lässt sich über 20 Bots und Stunden hinweg beurteilen, ob die
 * Spielmechaniken tragen (Nutzervorgabe: "das Verhalten sehr ausgiebig
 * monitoren").
 */
final class Monitor {
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String botName;
  private final PrintStream human;
  private final BufferedWriter jsonl;

  Monitor(String botName, Path logDir) {
    this.botName = botName;
    PrintStream h = System.out;
    BufferedWriter j = null;
    if (logDir != null) {
      try {
        Files.createDirectories(logDir);
        h = new PrintStream(Files.newOutputStream(logDir.resolve(botName + ".log"),
            StandardOpenOption.CREATE, StandardOpenOption.APPEND), true, StandardCharsets.UTF_8);
        j = Files.newBufferedWriter(logDir.resolve(botName + ".jsonl"), StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException e) {
        System.err.println("[" + botName + "] Logverzeichnis nicht beschreibbar: " + e.getMessage());
      }
    }
    this.human = h;
    this.jsonl = j;
  }

  void log(String message) {
    human.println("[" + LocalTime.now().format(TIME_FMT) + " " + botName + "] " + message);
  }

  /** Ereignis: menschenlesbar UND als JSONL-Zeile, damit der Report es zählen und zeitlich einordnen kann. */
  void event(String type, String message, Object... kv) {
    log(type + ": " + message);
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("event", type);
    fields.put("msg", message);
    for (int i = 0; i + 1 < kv.length; i += 2) fields.put(String.valueOf(kv[i]), kv[i + 1]);
    writeJson(fields);
  }

  /** Kennzahlen des Takts – eine Zeile je Takt. */
  void metrics(Map<String, Object> m) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("event", "tick");
    fields.putAll(m);
    writeJson(fields);
  }

  private synchronized void writeJson(Map<String, Object> fields) {
    if (jsonl == null) return;
    try {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("t", System.currentTimeMillis());
      node.put("bot", botName);
      for (Map.Entry<String, Object> e : fields.entrySet()) node.set(e.getKey(), MAPPER.valueToTree(e.getValue()));
      jsonl.write(MAPPER.writeValueAsString(node));
      jsonl.newLine();
      jsonl.flush();
    } catch (IOException e) {
      human.println("[" + botName + "] JSONL-Schreibfehler: " + e.getMessage());
    }
  }
}
