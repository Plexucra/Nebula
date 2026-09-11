#!/usr/bin/env bash
# Startet die komplette NPC-Bot-Armee: 20 Bots Lager NORD + 20 Bots Lager SUED
# (Umsetzungskonzept/14_...md, Teil 2; seit 11.9.2026 verdoppelt). Alle 40 Bots
# laufen in EINEM Java-Prozess (`BotArmy`, ein Thread und eine WebSocket-
# Verbindung je Bot, Umsetzungskonzept/31) – 40 einzelne JVMs à rund 600 MB
# hätten den Arbeitsspeicher eines LAN-Rechners gesprengt. Verhalten, Namen
# (`NPC-Nord-01` … `NPC-Nord-20`, `NPC-Sued-01` … `NPC-Sued-20`) und Protokoll
# sind identisch mit dem früheren Ein-Prozess-je-Bot-Start.
# Erwartet ein bereits gebautes target/npc-bot.jar (mvn clean package) und
# einen bereits laufenden Backend-Server (Standard: ws://localhost:8080/game,
# per erstem Argument überschreibbar). Bots je Lager per BOTS_PER_CAMP
# überschreibbar (Standard 20).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/target/npc-bot.jar"
SERVER="${1:-ws://localhost:8080/game}"
BOTS_PER_CAMP="${BOTS_PER_CAMP:-20}"
LOG_DIR="$SCRIPT_DIR/logs"
PID_FILE="$SCRIPT_DIR/army.pids"

if [ ! -f "$JAR" ]; then
  echo "Fehlt: $JAR – zuerst 'mvn -f $SCRIPT_DIR/pom.xml clean package' ausführen." >&2
  exit 1
fi

mkdir -p "$LOG_DIR"
: > "$PID_FILE"

logfile="$LOG_DIR/army.log"
# Heap-Grenze (11.9.2026): eine Bot-JVM ohne -Xmx wuchs im LAN-Betrieb auf
# 1,3 bis 3,8 GB RSS bei rund 130 MB lebenden Daten – die JVM darf ohne Grenze
# ein Viertel des Arbeitsspeichers belegen und sammelt erst dann. Die
# Bot-Armee braucht keinen Durchsatz-Kollektor: Serial-GC, 1 GB Deckel.
BOT_JAVA_OPTS="${BOT_JAVA_OPTS:--Xms64m -Xmx1g -XX:+UseSerialGC -Xss512k}"
# shellcheck disable=SC2086
java $BOT_JAVA_OPTS -cp "$JAR" de.nebula.npcbot.BotArmy --server="$SERVER" --count="$BOTS_PER_CAMP" --camps=NORD,SUED --logdir="$LOG_DIR" > "$logfile" 2>&1 &
echo $! >> "$PID_FILE"
echo "gestartet: BotArmy $((BOTS_PER_CAMP * 2)) Bots (je $BOTS_PER_CAMP NORD/SUED) pid=$! log=$logfile"

echo "Bot-Armee mit $((BOTS_PER_CAMP * 2)) Bots in einem Prozess gestartet (Server=$SERVER). Stoppen mit ./stop-army.sh"
