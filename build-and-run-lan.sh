#!/usr/bin/env bash
# Baut das Angular-Frontend als Production-Bundle, bettet es in das Quarkus-
# Backend ein und startet den Server auf allen Netzwerkschnittstellen
# (Umsetzungskonzept/14_...md, Teil 3 – "LAN-Betrieb"). Damit ist die Anwendung
# unter http://<LAN-IP-dieses-Rechners>:8080/ von jedem Gerät im selben WLAN
# erreichbar (z. B. einem Smartphone) – NICHT für Internet-Veröffentlichung
# gedacht. Der normale lokale Entwicklungsablauf (separates `ng serve` auf
# 4200 + `quarkus:dev` auf 8080) bleibt davon unberührt und funktioniert
# weiterhin unverändert.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/frontend"
BACKEND_DIR="$SCRIPT_DIR/backend"
STATIC_TARGET="$BACKEND_DIR/src/main/resources/META-INF/resources"

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

RUN_JAR="$BACKEND_DIR/target/quarkus-app/quarkus-run.jar"
if [ ! -f "$RUN_JAR" ]; then
  echo "Fehler: $RUN_JAR nicht gefunden – Quarkus-Package-Layout hat sich vermutlich geändert." >&2
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
echo "    Zum Beenden: Strg+C"
echo ""

exec java -jar "$RUN_JAR"
