#!/usr/bin/env bash
# Beendet den mit build-and-run-lan.sh gestarteten Server UND die von ihm
# mitgestartete NPC-Bot-Armee (40 Bots in einem Prozess, siehe npc-bot/run-army.sh) wieder.
# Nötig, weil build-and-run-lan.sh Server und Bot-Armee im Hintergrund startet
# (kein Kindprozess, der beim Schließen des Terminals automatisch mitstirbt,
# und Strg+C wirkt nur im ursprünglichen Terminal) – wer im Hintergrund oder
# in einer anderen Sitzung gestartet hat, kann so ohne Zugriff auf das
# ursprüngliche Terminal beenden.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_JAR="$SCRIPT_DIR/backend/target/quarkus-app/quarkus-run.jar"
NPC_BOT_DIR="$SCRIPT_DIR/npc-bot"

if [ -f "$NPC_BOT_DIR/army.pids" ]; then
  echo "==> Beende Bot-Armee ..."
  "$NPC_BOT_DIR/stop-army.sh"
fi

# Gleiches Matching-Muster wie in build-and-run-lan.sh: Prozesse, deren
# Kommandozeile genau diesen (Checkout-spezifischen) Jar-Pfad enthält.
mapfile -t PIDS < <(pgrep -f "java -jar $RUN_JAR" || true)

if [ ${#PIDS[@]} -eq 0 ]; then
  echo "Kein laufender Server gefunden (erwarteter Prozess: java -jar $RUN_JAR)."
  exit 0
fi

echo "==> Beende Server (PID(s): ${PIDS[*]}) ..."
kill "${PIDS[@]}"

for _ in $(seq 1 20); do
  if ! kill -0 "${PIDS[@]}" 2>/dev/null; then
    echo "Server beendet."
    exit 0
  fi
  sleep 0.5
done

echo "Server reagiert nicht auf SIGTERM – erzwinge Beendigung (SIGKILL) ..."
kill -9 "${PIDS[@]}" 2>/dev/null || true
echo "Server beendet."
