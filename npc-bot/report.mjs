#!/usr/bin/env node
// Auswertung eines Testlaufs (Umsetzungskonzept/31): liest die JSONL-Spuren
// aller Bots (<logdir>/*.jsonl) und – falls vorhanden – die Beobachter-Zeitreihe
// (<logdir>/observer.jsonl) und druckt einen Bericht: Ereignis-Zeitlinie je
// Mechanik, Strategie-/Rollenwechsel, Wirtschaftskennzahlen, Koordination,
// offene Blocker. Markdown auf stdout.
//
//   node report.mjs [logdir=logs]
import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const dir = process.argv[2] ?? 'logs';
const files = readdirSync(dir).filter(f => f.endsWith('.jsonl') && f !== 'observer.jsonl');
const bots = {};
let t0 = Infinity, t1 = 0;
for (const f of files) {
  const rows = readFileSync(join(dir, f), 'utf8').split('\n').filter(Boolean).map(l => { try { return JSON.parse(l); } catch { return null; } }).filter(Boolean);
  if (!rows.length) continue;
  const name = rows[0].bot;
  bots[name] = { ticks: rows.filter(r => r.event === 'tick'), events: rows.filter(r => r.event !== 'tick') };
  t0 = Math.min(t0, rows[0].t); t1 = Math.max(t1, rows[rows.length - 1].t);
}
const names = Object.keys(bots).sort();
const rel = t => ((t - t0) / 60000).toFixed(1).padStart(6) + ' min';
const count = (arr, f) => arr.filter(f).length;
const out = [];
out.push(`# Testlauf-Bericht (${names.length} Bots, ${((t1 - t0) / 60000).toFixed(0)} Realminuten)\n`);

// --- 1. Ereignis-Zeitlinie je Mechanik ----------------------------------------
const MECHANICS = [
  ['Wirtschaft', ['STRATEGY', 'QUEUE_REORDERED', 'BUILD_ORDERED', 'COLONY_LOST', 'COLONY_GAINED']],
  ['Koordination', ['COORDINATOR', 'ASSIGN_ROUND', 'ASSIGNMENT', 'ROLE_RENEGOTIATED', 'MEMBER_LOST', 'COORDINATOR_TAKEOVER', 'COORDINATOR_STEPDOWN', 'TARGET_REASSIGNED', 'REPORT_RECEIVED']],
  ['Diplomatie', ['WAR_DECLARED', 'TREATY_OFFERED', 'TREATY_ACCEPTED', 'TREATY_REJECTED', 'PEACE_REJECTED']],
  ['Raumkampf', ['RAID_LAUNCHED', 'BATTLE_STARTED', 'BATTLE_ENDED', 'BATTLE_RETREAT', 'FLEET_REBUILT', 'FLEET_REINFORCED', 'WARSHIP_ORDERED']],
  ['Bodenkrieg', ['TRANSPORT_ORDERED', 'TRANSPORT_READY', 'RECRUIT_ORDERED', 'INVASION_LAUNCHED', 'LANDED', 'LANDING_WIPED', 'GROUND_BATTLE_STARTED', 'GROUND_BATTLE_REFUSED', 'GROUND_RETREAT', 'GROUND_BATTLE_LOST', 'CONQUERED', 'INVASION_ENDED', 'INVASION_ABORTED']],
  ['Expansion', ['COLONY_SHIP_ORDERED', 'COLONY_SHIP_READY', 'COLONY_SHIP_LOST', 'COLONIZATION_STARTED', 'COLONY_FOUNDED', 'COLONIZATION_FAILED', 'DELIVERY_REQUESTED', 'DELIVERY_STARTED', 'DELIVERY_DONE']],
];
out.push('## 1. Ereignisse je Mechanik (Anzahl über alle Bots, erstes Auftreten)\n');
out.push('| Mechanik | Ereignis | Anzahl | erstes Auftreten | Beispiel |');
out.push('|---|---|---:|---|---|');
for (const [mech, types] of MECHANICS) {
  for (const type of types) {
    const all = names.flatMap(n => bots[n].events.filter(e => e.event === type).map(e => ({ ...e, bot: n }))).sort((a, b) => a.t - b.t);
    if (!all.length) { out.push(`| ${mech} | ${type} | 0 | – | – |`); continue; }
    out.push(`| ${mech} | ${type} | ${all.length} | ${rel(all[0].t)} | ${all[0].bot}: ${String(all[0].msg).replace(/\|/g, '/').slice(0, 110)} |`);
  }
}

// --- 2. Endzustand je Bot ---------------------------------------------------------
out.push('\n## 2. Endzustand je Bot (letzter Takt)\n');
out.push('| Bot | Strategie | Rolle | Spezialität | Kolonien | Bev. | Loy. | Elerium h | Blackout | Credits | Flotte | Transp. | Sold./Drohnen | Werft/Akad. | Handelsfahrten | Erlös | Invasion | Expansion |');
out.push('|---|---|---|---|---:|---:|---:|---:|---|---:|---:|---:|---|---|---:|---:|---|---|');
for (const n of names) {
  const last = bots[n].ticks.at(-1);
  if (!last) continue;
  out.push(`| ${n} | ${last.strategy} | ${last.role} | ${last.specialty} | ${last.colonies} | ${last.pop} | ${last.loyalty} | ${last.eleriumHours ?? '∞'} | ${last.blackout ? 'JA' : '-'} | ${last.wallet} | ${last.fleet} | ${last.transports} | ${last.soldiers}/${last.drones} | ${last.shipyard}/${last.academy} | ${last.tradeTrips} | ${last.revenue} | ${last['mil.invasion']}${last['mil.blocked'] ? ' [' + last['mil.blocked'] + ']' : ''} | ${last.expansion}${last.expansionBlocked ? ' [' + last.expansionBlocked + ']' : ''} |`);
}

// --- 3. Verlauf: Bevölkerung, Blackout, Strategien ------------------------------------
out.push('\n## 3. Verlauf (alle Bots, Mittelwerte je 10-Minuten-Fenster)\n');
out.push('| Fenster | Bev. Ø | Loy. Ø | Elerium h Ø | Blackouts | Credits Ø | Flotte Ø | Soldaten Σ | Drohnen Σ | Transporter Σ | Strategien |');
out.push('|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|');
const windowMs = 10 * 60000;
for (let w = t0; w < t1; w += windowMs) {
  const rows = names.flatMap(n => { const inWin = bots[n].ticks.filter(r => r.t >= w && r.t < w + windowMs); return inWin.length ? [inWin.at(-1)] : []; });
  if (!rows.length) continue;
  const avg = k => (rows.reduce((s, r) => s + (Number(r[k]) || 0), 0) / rows.length).toFixed(0);
  const sum = k => rows.reduce((s, r) => s + (Number(r[k]) || 0), 0);
  const strategies = {};
  for (const r of rows) strategies[r.strategy] = (strategies[r.strategy] || 0) + 1;
  out.push(`| ${rel(w)} | ${avg('pop')} | ${avg('loyalty')} | ${avg('eleriumHours')} | ${count(rows, r => r.blackout)} | ${avg('wallet')} | ${avg('fleet')} | ${sum('soldiers')} | ${sum('drones')} | ${sum('transports')} | ${Object.entries(strategies).map(([k, v]) => k + '×' + v).join(', ')} |`);
}

// --- 4. Koordination --------------------------------------------------------------------
out.push('\n## 4. Koordination über das Nachrichtensystem\n');
for (const n of names) {
  const last = bots[n].ticks.at(-1);
  if (!last) continue;
  const assigns = bots[n].events.filter(e => e.event === 'ASSIGNMENT');
  const reneg = bots[n].events.filter(e => e.event === 'ROLE_RENEGOTIATED');
  out.push(`- **${n}** ${last['coord.coordinator'] ? '(Koordinator)' : ''}: ${last['coord.msgsSent']} gesendet, ${last['coord.msgsReceived']} empfangen, ${assigns.length} Zuteilungen erhalten` +
    (reneg.length ? `, ${reneg.length} Neuverhandlungen: ` + reneg.map(e => e.msg).join('; ') : '') + (last['coord.takeovers'] ? `, ${last['coord.takeovers']} Übernahmen` : ''));
}

// --- 5. Blocker (was hindert die Bots gerade) ------------------------------------------------
out.push('\n## 5. Aktuelle Blocker\n');
const blockers = {};
for (const n of names) {
  const last = bots[n].ticks.at(-1);
  if (!last) continue;
  for (const b of [last['mil.blocked'], last.expansionBlocked]) if (b) blockers[b] = (blockers[b] || []).concat(n);
}
for (const [b, ns] of Object.entries(blockers).sort((a, b) => b[1].length - a[1].length)) out.push(`- ${b} — ${ns.length}× (${ns.slice(0, 5).join(', ')}${ns.length > 5 ? ', …' : ''})`);
if (!Object.keys(blockers).length) out.push('- keine');

// --- 6. Beobachter-Ereignisse ----------------------------------------------------------------
const obs = join(dir, 'observer.jsonl');
if (existsSync(obs)) {
  const rows = readFileSync(obs, 'utf8').split('\n').filter(Boolean).map(l => JSON.parse(l));
  const events = rows.filter(r => r.kind === 'event');
  const snaps = rows.filter(r => r.kind === 'snapshot');
  out.push('\n## 6. Beobachter (unabhängige Serversicht)\n');
  if (snaps.length) {
    const s = snaps.at(-1);
    out.push(`Letzte Momentaufnahme ${rel(s.t)}: ${s.totals.colonies} Kolonien (gegründet ${s.totals.founded}, erobert ${s.totals.conquered}), Bevölkerung ${s.totals.pop}, Blackouts ${s.totals.blackout}, Verträge ${s.totals.treaties / 2}, Kriege ${s.totals.wars / 2}, NPC-Nachrichten in Postfächern ${s.totals.messages}.\n`);
  }
  for (const e of events.slice(0, 80)) out.push(`- ${rel(e.t)}: ${e.text}`);
}
console.log(out.join('\n'));
