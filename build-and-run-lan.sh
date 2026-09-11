#!/usr/bin/env bash
# Baut das Angular-Frontend als Production-Bundle, bettet es in das Quarkus-
# Backend ein, startet den Server auf allen Netzwerkschnittstellen und
# anschließend die komplette NPC-Bot-Armee (40 Bots in einem Prozess, siehe
# npc-bot/run-army.sh) gegen diesen Server (Umsetzungskonzept/14_...md,
# Teil 2+3 – "NPC-Bot-Armee" und "LAN-Betrieb"). Damit ist die Anwendung unter
# http://<LAN-IP-dieses-Rechners>:8080/ von jedem Gerät im selben WLAN
# erreichbar (z. B. einem Smartphone) – NICHT für Internet-Veröffentlichung
# gedacht. Der normale lokale Entwicklungsablauf (separates `ng serve` auf
# 4200 + `quarkus:dev` auf 8080) bleibt davon unberührt und funktioniert
# weiterhin unverändert.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/frontend"
BACKEND_DIR="$SCRIPT_DIR/backend"
NPC_BOT_DIR="$SCRIPT_DIR/npc-bot"
STATIC_TARGET="$BACKEND_DIR/src/main/resources/META-INF/resources"
RUN_JAR="$BACKEND_DIR/target/quarkus-app/quarkus-run.jar"
NPC_JAR="$NPC_BOT_DIR/target/npc-bot.jar"

# Läuft bereits ein Server aus einem früheren Aufruf dieses Skripts? Der neue
# Build würde sich sonst mit dem alten Prozess den Port 8080 teilen wollen.
# Gleiches Matching-Muster wie in stop-lan.sh.
EXISTING_PIDS="$(pgrep -f "java -jar $RUN_JAR" || true)"
if [ -n "$EXISTING_PIDS" ]; then
  echo "Fehler: Es läuft bereits ein Server aus diesem Checkout (PID(s): $EXISTING_PIDS)." >&2
  echo "        Erst beenden mit: ./stop-lan.sh" >&2
  exit 1
fi
if [ -f "$NPC_BOT_DIR/army.pids" ] && [ -s "$NPC_BOT_DIR/army.pids" ]; then
  echo "Fehler: Es sieht so aus, als liefe bereits eine Bot-Armee aus diesem Checkout." >&2
  echo "        Erst beenden mit: ./stop-lan.sh" >&2
  exit 1
fi

echo "==> Baue Angular-Frontend (Production) ..."
(cd "$FRONTEND_DIR" && npx ng build --configuration production)

BROWSER_DIST="$FRONTEND_DIR/dist/nebula-frontend/browser"
if [ ! -f "$BROWSER_DIST/index.html" ]; then
  echo "Fehler: $BROWSER_DIST/index.html nicht gefunden – Angular-Build-Ausgabestruktur hat sich vermutlich geändert." >&2
  exit 1
fi

echo "==> Kopiere Build-Ausgabe nach $STATIC_TARGET ..."
rm -rf "$STATIC_TARGET"
mkdir -p "$STATIC_TARGET"
cp -r "$BROWSER_DIST"/. "$STATIC_TARGET"/

echo "==> Baue und paketiere das Backend (inkl. eingebettetem Frontend) ..."
(cd "$BACKEND_DIR" && ./mvnw -B -ntp -DskipTests clean package)

if [ ! -f "$RUN_JAR" ]; then
  echo "Fehler: $RUN_JAR nicht gefunden – Quarkus-Package-Layout hat sich vermutlich geändert." >&2
  exit 1
fi

echo "==> Baue und paketiere den NPC-Bot (npc-bot/run-army.sh braucht $NPC_JAR) ..."
(cd "$NPC_BOT_DIR" && mvn -B -ntp -q -DskipTests clean package)

if [ ! -f "$NPC_JAR" ]; then
  echo "Fehler: $NPC_JAR nicht gefunden – npc-bot-Package-Layout hat sich vermutlich geändert." >&2
  exit 1
fi

LAN_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
echo ""
echo "==> Starte Backend auf 0.0.0.0:8080 (Produktionsmodus, kein Dev-Live-Reload) ..."
if [ -n "$LAN_IP" ]; then
  echo "    Auf einem Gerät im selben WLAN im Browser aufrufen: http://$LAN_IP:8080/"
else
  echo "    Konnte die LAN-IP nicht automatisch ermitteln – mit 'hostname -I' oder 'ip a' selbst nachsehen."
fi
echo "    Zum Beenden: Strg+C (in diesem Terminal) oder ./stop-lan.sh (von woanders)"
echo ""

# Läuft NICHT mehr per `exec` im Vordergrund: die Bot-Armee (siehe unten)
# muss NACH einem erfolgreichen Serverstart angestoßen werden, brauchen also
# ein Skript, das danach noch weiterläuft. `trap` sorgt dafür, dass sowohl
# Server als auch Bot-Armee bei Strg+C (SIGINT) oder SIGTERM sauber beendet
# werden – exakt das, was bisher `exec` + Strg+C implizit erledigte.
# Heap-Grenze (11.9.2026): ohne -Xmx nimmt sich die JVM bis zu einem Viertel des
# Arbeitsspeichers (auf einem 64-GB-Rechner 16 GB) und gibt einmal belegte
# Bereiche nicht zurück – der Live-Server stand nach 35 Stunden bei 5,8 GB RSS
# für rund 200 MB lebende Daten. 2 GB reichen für Galaxie, 40 Bots und die
# Aufbewahrungsfristen weit; die periodische GC gibt ungenutzten Heap frei.
SERVER_JAVA_OPTS="${SERVER_JAVA_OPTS:--Xms256m -Xmx2g -XX:+UseG1GC -XX:G1PeriodicGCInterval=60000 -XX:MaxHeapFreeRatio=30 -XX:MinHeapFreeRatio=10}"
# shellcheck disable=SC2086
java $SERVER_JAVA_OPTS -jar "$RUN_JAR" &
SERVER_PID=$!

cleanup() {
  echo ""
  echo "==> Beende Bot-Armee und Server ..."
  if [ -f "$NPC_BOT_DIR/army.pids" ]; then
    "$NPC_BOT_DIR/stop-army.sh" || true
  fi
  kill "$SERVER_PID" 2>/dev/null || true
  wait "$SERVER_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "==> Warte auf Serverstart, bevor die Bot-Armee losgeschickt wird ..."
SERVER_READY=0
for _ in $(seq 1 60); do
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "Fehler: Server-Prozess ist während des Starts beendet worden." >&2
    exit 1
  fi
  if curl -sf -o /dev/null "http://localhost:8080/"; then
    SERVER_READY=1
    break
  fi
  sleep 1
done

if [ "$SERVER_READY" -ne 1 ]; then
  echo "Fehler: Server antwortet nach 60s nicht auf http://localhost:8080/ – Bot-Armee wird nicht gestartet." >&2
  exit 1
fi

echo "==> Server läuft – starte Bot-Armee (40 Bots, ein Prozess) ..."
"$NPC_BOT_DIR/run-army.sh" "ws://localhost:8080/game"

wait "$SERVER_PID"
