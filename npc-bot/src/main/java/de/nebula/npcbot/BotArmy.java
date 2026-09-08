package de.nebula.npcbot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Die ganze Bot-Armee in EINEM Prozess (ein Thread und eine WebSocket-
 * Verbindung je Bot) – Alternative zu {@code run-army.sh} mit 20 JVMs, die im
 * Live-Spiel je rund 600 MB belegten. Verhalten und Protokoll sind identisch,
 * jeder Bot ist weiterhin ein eigener Kommandant mit eigener Verbindung.
 *
 * <pre>
 *   java -cp target/npc-bot.jar de.nebula.npcbot.BotArmy \
 *        --server=ws://localhost:8081/game --count=10 --camps=NORD,SUED --logdir=logs
 * </pre>
 */
public final class BotArmy {
  private BotArmy() {
  }

  public static void main(String[] args) throws Exception {
    Map<String, String> opts = Bot.parseArgs(args);
    String server = opts.getOrDefault("server", "ws://localhost:8080/game");
    int count = Integer.parseInt(opts.getOrDefault("count", "10"));
    int first = Integer.parseInt(opts.getOrDefault("first", "1"));
    Path logDir = Path.of(opts.getOrDefault("logdir", "logs"));
    List<Thread> threads = new ArrayList<>();
    for (String camp : opts.getOrDefault("camps", "NORD,SUED").split(",")) {
      for (int i = first; i < first + count; i++) {
        Bot bot = new Bot(i, camp.trim().toUpperCase(), server, logDir);
        Thread t = new Thread(() -> {
          try {
            bot.run();
          } catch (Exception e) {
            System.err.println("[" + bot.botName + "] beendet: " + e);
          }
        }, bot.botName);
        t.setDaemon(false);
        t.start();
        threads.add(t);
        Thread.sleep(400); // gestaffelter Start: Registrierung und erste Takte nicht alle in derselben Sekunde
      }
    }
    System.out.println("BotArmy: " + threads.size() + " Bots gegen " + server + " gestartet, Logs unter " + logDir.toAbsolutePath());
    for (Thread t : threads) t.join();
  }
}
