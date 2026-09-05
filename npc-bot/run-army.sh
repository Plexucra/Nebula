#!/usr/bin/env bash
# Startet die komplette NPC-Bot-Armee: 10 Prozesse Lager NORD + 10 Prozesse
# Lager SUED, je ein eigener Java-Prozess (Umsetzungskonzept/14_...md, Teil 2).
# Erwartet ein bereits gebautes target/npc-bot.jar (mvn clean package) und
# einen bereits laufenden Backend-Server (Standard: ws://localhost:8080/game,
# per erstem Argument überschreibbar).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/target/npc-bot.jar"
SERVER="${1:-ws://localhost:8080/game}"
LOG_DIR="$SCRIPT_DIR/logs"
PID_FILE="$SCRIPT_DIR/army.pids"

if [ ! -f "$JAR" ]; then
  echo "Fehlt: $JAR – zuerst 'mvn -f $SCRIPT_DIR/pom.xml clean package' ausführen." >&2
  exit 1
fi

mkdir -p "$LOG_DIR"
: > "$PID_FILE"

for camp in NORD SUED; do
  for i in $(seq 1 10); do
    idx=$(printf "%02d" "$i")
    logfile="$LOG_DIR/${camp}-${idx}.log"
    java -jar "$JAR" --index="$i" --camp="$camp" --server="$SERVER" > "$logfile" 2>&1 &
    echo $! >> "$PID_FILE"
    echo "gestartet: Lager=$camp index=$i pid=$! log=$logfile"
  done
done

echo "Alle 20 Bot-Prozesse gestartet (Server=$SERVER). Stoppen mit ./stop-army.sh"
