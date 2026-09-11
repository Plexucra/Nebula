// Einmaliges Kontrollskript (kein Dauertest) gegen einen laufenden Server mit
// aktiver 20-Bot-Armee (siehe run-army.sh) – prüft anhand des regulären
// WebSocket-Protokolls (genau wie backend/e2e/full-playthrough.mjs) die in
// Umsetzungskonzept/14_...md, Teil 2 geforderten Nachweise:
//   1. 20 registrierte Spieler mit den erwarteten NPC-Namen
//   2. Kriegszustände zwischen den beiden Lagern
//   3. mindestens eine plausible Koordinationsnachricht (Zuteilung "NPC:ASSIGN" des Lager-Koordinators, Umsetzungskonzept/31)
//   4. mindestens ein reales Gefecht (aktiv oder bereits beendet)
// Da Kriege/Postfächer/Gefechte serverseitig strikt auf den EINGELOGGTEN
// Kommandanten gefiltert sind (kein Admin-/Observer-Zugriff im Protokoll),
// meldet sich das Skript nacheinander testweise als einzelne Bots an
// ("login" prüft im Prototyp bewusst kein Passwort) statt Log-Dateien zu
// parsen – das bleibt eine echte Protokollprüfung.
import assert from 'node:assert';

const SERVER = process.argv[2] || 'ws://localhost:8080/game';

function connect(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    ws.addEventListener('open', () => resolve(ws));
    ws.addEventListener('error', (e) => reject(e));
  });
}

function call(ws, type, payload = {}) {
  return new Promise((resolve, reject) => {
    const requestId = 'req' + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2);
    const handler = (event) => {
      const msg = JSON.parse(event.data);
      if (msg.requestId !== requestId) return;
      ws.removeEventListener('message', handler);
      if (msg.type === 'Error') reject(new Error(msg.payload?.message || 'Fehler'));
      else resolve(msg.payload);
    };
    ws.addEventListener('message', handler);
    ws.send(JSON.stringify({ type, requestId, payload }));
  });
}

const ws = await connect(SERVER);
console.log('[verify] Verbunden mit', SERVER);

const players = await call(ws, 'players');
const nordBots = players.filter(p => /^NPC-Nord-\d\d$/.test(p.name)).sort((a, b) => a.name.localeCompare(b.name));
const suedBots = players.filter(p => /^NPC-Sued-\d\d$/.test(p.name)).sort((a, b) => a.name.localeCompare(b.name));
console.log(`[verify] Registrierte Spieler gesamt: ${players.length} (NORD-Bots: ${nordBots.length}, SUED-Bots: ${suedBots.length})`);
assert.strictEqual(nordBots.length, 20, 'Erwartet 20 registrierte NORD-Bots');
assert.strictEqual(suedBots.length, 20, 'Erwartet 20 registrierte SUED-Bots');
console.log('[verify] BESTANDEN: 20 Bot-Kommandanten vollständig registriert (' + nordBots.map(p => p.name).join(', ') + ' / ' + suedBots.map(p => p.name).join(', ') + ')');

// --- 2. Kriegszustände zwischen den Lagern ---------------------------------
await call(ws, 'login', { playerId: nordBots[4].id }); // NPC-Nord-05
const activeWars = await call(ws, 'activeWars');
console.log(`[verify] ${nordBots[4].name} führt Krieg gegen ${activeWars.length} Kommandant(en).`);
assert.ok(activeWars.length >= 5, 'Erwartet mehrere aktive Kriege gegen das gegnerische Lager');
const warAgainstSued = activeWars.every(r => {
  const otherId = r.playerAId === nordBots[4].id ? r.playerBId : r.playerAId;
  return suedBots.some(s => s.id === otherId);
});
assert.ok(warAgainstSued, 'Alle Kriege sollten gegen das SUED-Lager geführt werden');
console.log('[verify] BESTANDEN: Kriegszustände zwischen den Lagern bestehen (' + nordBots[4].name + ' vs. SUED).');

// --- 3. Koordinationsnachricht (Zuteilung des Koordinators) ------------------
await call(ws, 'login', { playerId: nordBots[4].id }); // Empfänger einer Zuteilung (Index 05, nicht Koordinator)
const inbox = await call(ws, 'inbox');
const specMsg = inbox.find(m => (m.subject === 'NPC:ASSIGN' || m.subject === 'Spezialisierung') && m.fromPlayerId === nordBots[0].id);
assert.ok(specMsg, 'Erwartet eine Zuteilungs-Nachricht (NPC:ASSIGN) vom Koordinator (NPC-Nord-01) im Posteingang von NPC-Nord-05');
console.log(`[verify] BESTANDEN: Koordinationsnachricht gefunden – "${specMsg.subject}": "${specMsg.body}" (von ${nordBots[0].name} an ${nordBots[4].name}, gelesen=${specMsg.read}).`);

// --- 4. Mindestens ein reales Gefecht (aktiv oder beendet) -----------------
let battleFound = null;
for (const bot of [...nordBots, ...suedBots]) {
  await call(ws, 'login', { playerId: bot.id });
  const active = await call(ws, 'activeBattles');
  const history = await call(ws, 'battleHistory');
  if (active.length > 0) { battleFound = { kind: 'aktiv', battle: active[0], as: bot.name }; break; }
  if (history.length > 0) { battleFound = { kind: 'beendet', battle: history[0], as: bot.name }; break; }
}
assert.ok(battleFound, 'Erwartet mindestens ein aktives oder beendetes Gefecht bei irgendeinem der 40 Bots');
console.log(`[verify] BESTANDEN: Gefecht gefunden (${battleFound.kind}, geprüft als ${battleFound.as}): id=${battleFound.battle.id}, `
  + `Angreifer=${battleFound.battle.attackerId}, Verteidiger=${battleFound.battle.defenderId}, `
  + `outcome=${battleFound.battle.outcome ?? '(läuft noch)'}, Ticks=${battleFound.battle.ticksResolved}.`);

console.log('[verify] ALLE PRÜFUNGEN BESTANDEN.');
ws.close();
