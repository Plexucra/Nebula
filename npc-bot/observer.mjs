#!/usr/bin/env node
// Beobachter für einen laufenden Server mit Bot-Armee (Umsetzungskonzept/31):
// nimmt in festem Realzeit-Abstand eine Momentaufnahme des GESAMTEN Spiels
// über das reguläre WebSocket-Protokoll und schreibt sie als JSONL
// (eine Zeile je Takt) plus eine lesbare Zusammenfassung auf die Konsole.
//
// Es gibt keinen Admin-/Beobachter-Zugriff im Protokoll; Kriege, Verträge,
// Gefechte und Postfächer sind strikt auf den eingeloggten Kommandanten
// gefiltert. Das Skript meldet sich deshalb – wie verify-army.mjs – der Reihe
// nach als jeder Bot an ("login" prüft im Prototyp kein Passwort) und liest
// dessen Sicht. Es setzt KEINEN verändernden Befehl ab.
//
//   node observer.mjs [ws://localhost:8081/game] [--interval=30] [--out=logs/observer.jsonl] [--once]
import { appendFileSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

const args = Object.fromEntries(process.argv.slice(2).filter(a => a.startsWith('--')).map(a => { const [k, v] = a.slice(2).split('='); return [k, v ?? 'true']; }));
const SERVER = process.argv.slice(2).find(a => !a.startsWith('--')) || 'ws://localhost:8081/game';
const INTERVAL_S = Number(args.interval ?? 30);
const OUT = args.out ?? 'logs/observer.jsonl';
const ONCE = args.once === 'true';
mkdirSync(dirname(OUT), { recursive: true });

function connect(url) {
  return new Promise((resolve, reject) => { const ws = new WebSocket(url); ws.addEventListener('open', () => resolve(ws)); ws.addEventListener('error', reject); });
}
function call(ws, type, payload = {}) {
  return new Promise((resolve, reject) => {
    const requestId = 'obs' + Math.random().toString(36).slice(2);
    const timer = setTimeout(() => { ws.removeEventListener('message', h); reject(new Error('timeout ' + type)); }, 20000);
    const h = (ev) => { const m = JSON.parse(ev.data); if (m.requestId !== requestId) return; clearTimeout(timer); ws.removeEventListener('message', h);
      m.type === 'Error' ? reject(new Error(m.payload?.message || 'Fehler')) : resolve(m.payload); };
    ws.addEventListener('message', h); ws.send(JSON.stringify({ type, requestId, payload }));
  });
}
const WEIGHT = { p_corvette: 1, p_destroyer: 10, p_cruiser: 100 };
const DRONE_VALUE = { p_drone_light: 17 * 2.29, p_drone_medium: 19 * 2.53, p_drone_heavy: 20 * 2.65 };
const strength = f => (f.ships ?? []).reduce((s, g) => s + (WEIGHT[g.shipProductTypeId] ?? 0) * g.quantity, 0);
const units = (g, id) => (g?.units ?? []).filter(u => u.unitProductTypeId === id).reduce((s, u) => s + u.activeCount + u.reserveCount, 0);
const droneValue = g => (g?.units ?? []).reduce((s, u) => s + (DRONE_VALUE[u.unitProductTypeId] ?? 0) * u.activeCount, 0);

const seenEvents = new Set();
function event(t, text) { const key = text; if (seenEvents.has(key)) return; seenEvents.add(key); console.log(`  !! ${text}`); appendFileSync(OUT, JSON.stringify({ t, kind: 'event', text }) + '\n'); }

async function snapshot(ws) {
  const t = Date.now();
  const players = await call(ws, 'players');
  const systems = await call(ws, 'visibleSystems');
  const bySys = Object.fromEntries(systems.map(s => [s.id, s]));
  const fleets = await call(ws, 'allFleets');
  const bots = players.filter(p => /^NPC-(Nord|Sued)-\d\d$/.test(p.name)).sort((a, b) => a.name.localeCompare(b.name));
  const snap = { t, kind: 'snapshot', players: players.length, bots: bots.length, fleets: fleets.length, byBot: {}, totals: {} };
  const totals = { colonies: 0, pop: 0, blackout: 0, battlesActive: 0, groundBattlesActive: 0, colonizations: 0, treaties: 0, wars: 0, conquered: 0, founded: 0, messages: 0 };
  const lines = [];
  for (const p of bots) {
    await call(ws, 'login', { playerId: p.id });
    const colonies = await call(ws, 'colonies');
    const wallet = await call(ws, 'wallet');
    const wars = await call(ws, 'activeWars');
    const treaties = await call(ws, 'treaties');
    const battles = await call(ws, 'activeBattles');
    const gbattles = await call(ws, 'activeGroundBattles');
    const bhist = await call(ws, 'battleHistory');
    const gbhist = await call(ws, 'groundBattleHistory');
    const colonizations = await call(ws, 'colonizations');
    const inbox = await call(ws, 'inbox');
    const landed = await call(ws, 'landedGroundForces');
    const cols = [];
    for (const c of colonies) {
      const st = await call(ws, 'colonyStats', { id: c.id });
      const pop = await call(ws, 'population', { id: c.id });
      const bo = await call(ws, 'isBlackout', { colonyId: c.id });
      const bld = await call(ws, 'buildings', { colonyId: c.id });
      const wh = await call(ws, 'warehouse', { colonyId: c.id });
      const g = await call(ws, 'groundForces', { colonyId: c.id });
      const yard = await call(ws, 'shipyardQueue', { colonyId: c.id });
      const rq = await call(ws, 'recruitmentQueue', { colonyId: c.id });
      const infra = bld.find(b => b.typeId === 'b_infrastructure')?.level ?? 0;
      const elerium = wh.find(w => w.productTypeId === 'p_elerium_stabil')?.quantity ?? 0;
      const perHour = infra > 0 ? 0.005 * Math.pow(infra, 1.25) : 0;
      const col = { id: c.id, name: c.name, home: c.isHomeworld, system: bySys[c.systemId]?.name, pop: Math.round(pop?.currentCount ?? 0),
        loyalty: Math.round(st?.loyaltyPct ?? 0), sol: Math.round(st?.standardOfLivingPct ?? 0), security: Math.round(st?.securityPct ?? 0), blackout: bo,
        eleriumHours: perHour > 0 ? Math.round(elerium / perHour) : null,
        buildings: Object.fromEntries(bld.map(b => [b.typeId.replace('b_', ''), b.level])),
        soldiers: units(g, 'p_soldier'), drones: ['p_drone_light', 'p_drone_medium', 'p_drone_heavy'].reduce((s, d) => s + units(g, d), 0), droneValue: Math.round(droneValue(g)),
        shipsInStock: Object.fromEntries(wh.filter(w => /^p_(corvette|destroyer|cruiser|freighter|trooptransport|colonyship)$/.test(w.productTypeId)).map(w => [w.productTypeId.slice(2), w.quantity])),
        yard: yard.map(q => q.shipProductTypeId.slice(2) + '/' + q.status + (q.endsAt ? '/' + Math.round((q.endsAt - t) / 60000) + 'min' : '')),
        recruiting: rq.map(q => q.unitProductTypeId.slice(2) + 'x' + q.quantity + '/' + q.status) };
      cols.push(col);
      totals.colonies++; totals.pop += col.pop; if (bo) totals.blackout++;
      if (!c.isHomeworld) { if (c.name.endsWith('-Kolonie')) totals.founded++; else totals.conquered++; }
    }
    const own = fleets.filter(f => f.ownerId === p.id);
    const fl = own.map(f => ({ id: f.id, name: f.name, status: f.status, system: bySys[f.systemId]?.name, strength: strength(f),
      ships: Object.fromEntries((f.ships ?? []).map(s => [s.shipProductTypeId.slice(2), s.quantity])), cargo: Object.fromEntries((f.cargo ?? []).map(c => [c.productTypeId.slice(2), Math.round(c.quantity)])) }));
    const msgsNpc = inbox.filter(m => (m.subject || '').startsWith('NPC:'));
    const bySubject = {};
    for (const m of msgsNpc) bySubject[m.subject] = (bySubject[m.subject] || 0) + 1;
    snap.byBot[p.name] = { id: p.id, camp: p.campId, home: bySys[p.homeSystemId]?.name, wallet: Math.round(wallet?.balance ?? 0), colonies: cols, fleets: fl,
      wars: wars.length, treaties: treaties.filter(x => !x.terminationEffectiveAt).length, battles: battles.length, groundBattles: gbattles.map(b => ({ id: b.id, colony: b.colonyId, phase: b.phase, ticks: b.ticksResolved, attacker: b.attackerId === p.id })),
      battleHistory: bhist.length, groundBattleHistory: gbhist.map(b => ({ id: b.id, outcome: b.outcome, ticks: b.ticksResolved, attacker: b.attackerId === p.id })),
      colonizations: colonizations.length, inboxNpc: bySubject, landed: landed.map(g => ({ planet: g.planetId, soldiers: units(g, 'p_soldier'), droneValue: Math.round(droneValue(g)) })) };
    totals.battlesActive += battles.filter(b => b.attackerId === p.id).length;
    totals.groundBattlesActive += gbattles.filter(b => b.attackerId === p.id).length;
    totals.colonizations += colonizations.length; totals.treaties += treaties.length; totals.wars += wars.length; totals.messages += msgsNpc.length;
    for (const b of gbhist) if (b.attackerId === p.id) event(t, `Bodengefecht ${b.id} von ${p.name}: ${b.outcome} nach ${b.ticksResolved} Ticks`);
    for (const b of bhist) if (b.attackerId === p.id) event(t, `Raumgefecht ${b.id} von ${p.name}: ${b.outcome} nach ${b.ticksResolved} Ticks`);
    for (const c of cols) { if (!c.home) event(t, `${p.name} besitzt ${c.name} (${c.system}) – ${c.name.endsWith('-Kolonie') ? 'gegründet' : 'erobert'}`); if (c.blackout) event(t, `${p.name}: ${c.name} im Blackout`); }
    for (const c of colonizations) event(t, `${p.name}: Koloniegründung läuft auf ${c.planetId}`);
    const home = cols.find(c => c.home);
    lines.push(`${p.name.padEnd(12)} ${(p.campId ?? '').padEnd(4)} col=${cols.length} pop=${String(home?.pop ?? 0).padStart(6)} loy=${String(home?.loyalty ?? 0).padStart(3)} sol=${String(home?.sol ?? 0).padStart(3)} bo=${home?.blackout ? 'Y' : '-'} elH=${String(home?.eleriumHours ?? '-').padStart(5)} $=${String(Math.round(wallet?.balance ?? 0)).padStart(6)} ` +
      `bld=${Object.entries(home?.buildings ?? {}).map(([k, v]) => k.slice(0, 4) + v).join(' ')} sol/dr=${home?.soldiers}/${home?.drones} fleet=${fl.map(f => Math.round(f.strength)).join('+')} yard=${home?.yard.join(',') || '-'} rq=${home?.recruiting.join(',') || '-'} wars=${wars.length} tr=${treaties.length} gb=${gbattles.length} msg=${msgsNpc.length}`);
  }
  await call(ws, 'logout').catch(() => {});
  snap.totals = totals;
  appendFileSync(OUT, JSON.stringify(snap) + '\n');
  console.log(`\n=== ${new Date(t).toLocaleTimeString()} | Spieler ${players.length}, Bots ${bots.length}, Kolonien ${totals.colonies} (gegründet ${totals.founded}, erobert ${totals.conquered}), Bevölkerung ${totals.pop}, Blackouts ${totals.blackout}, Raumgefechte ${totals.battlesActive}, Bodengefechte ${totals.groundBattlesActive}, Gründungen ${totals.colonizations}, Verträge ${totals.treaties / 2}, Kriege ${totals.wars / 2}, NPC-Nachrichten ${totals.messages}`);
  for (const l of lines) console.log(l);
}

const ws = await connect(SERVER);
console.log('[observer] verbunden mit', SERVER, '– Intervall', INTERVAL_S, 's, Ausgabe', OUT);
for (;;) {
  try { await snapshot(ws); } catch (e) { console.log('[observer] Fehler:', e.message); }
  if (ONCE) break;
  await new Promise(r => setTimeout(r, INTERVAL_S * 1000));
}
ws.close();
