#!/usr/bin/env bash
# Beendet alle über run-army.sh gestarteten Bot-Prozesse sauber (SIGTERM).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$SCRIPT_DIR/army.pids"

if [ ! -f "$PID_FILE" ]; then
  echo "Keine $PID_FILE gefunden – nichts zu stoppen." >&2
  exit 0
fi

while read -r pid; do
  [ -z "$pid" ] && continue
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null && echo "gestoppt: pid=$pid" || echo "konnte pid=$pid nicht stoppen"
  fi
done < "$PID_FILE"

rm -f "$PID_FILE"
echo "Alle Bot-Prozesse gestoppt."
